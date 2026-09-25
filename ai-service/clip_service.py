# -*- coding: utf-8 -*-
"""
Chinese-CLIP 零样本物品分类

替换原「MobileNetV3 + ImageNet 标签映射」方案，直接输出 12 个校园标准类别。
原理：CLIP 把图片和文本映射到同一语义空间，用中文类别名与图片算余弦相似度做零样本分类，
因此能识别「钥匙 / 耳机 / 证件卡」等 ImageNet 中没有的类别。
"""
import os

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

import io

import torch
from PIL import Image

import cn_clip.clip as clip
from cn_clip.clip import load_from_name


class ClipClassifier:
    """Chinese-CLIP 零样本分类器"""

    # 与后端 PostController 的 12 个具体标准类别严格对齐（不含「其他」）
    CLASS_NAMES = [
        "手机", "钱包/包", "钥匙", "证件/卡", "书包/背包", "书籍",
        "衣物", "水杯", "眼镜", "耳机", "电脑/电子设备", "雨伞",
    ]

    # 中文 prompt 模板（零样本分类的关键，模板数量越多通常越稳）
    TEMPLATES = [
        "一张{}的照片",
        "这是一个{}",
        "{}",
    ]

    # 最高概率低于该阈值时判为「其他」（表示模型对任何已知类别都无把握）
    THRESHOLD = 0.30

    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.loaded = False
        try:
            self.model, self.preprocess = load_from_name("ViT-B-16", device=self.device)
            self.model.eval()
            self._build_zeroshot_weights()
            self.loaded = True
        except Exception as e:
            print("[ClipClassifier] 模型加载失败:", e)
            self.model = None

    def _build_zeroshot_weights(self):
        """把 12 个类别名 + 模板编码成文本向量，拼成 (512, 12) 的权重矩阵"""
        with torch.no_grad():
            weights = []
            for name in self.CLASS_NAMES:
                texts = [t.format(name) for t in self.TEMPLATES]
                tokenized = clip.tokenize(texts).to(self.device)
                emb = self.model.encode_text(tokenized)          # (n_templates, 512)
                emb = emb / emb.norm(dim=-1, keepdim=True)       # L2 归一化
                emb = emb.mean(dim=0)                            # 多模板求均值
                emb = emb / emb.norm()
                weights.append(emb)
            self.zeroshot_weights = torch.stack(weights, dim=1).to(self.device)

    def classify(self, image_bytes):
        """图片 → (中文标准类别, 置信度)；无把握时返回 ("其他", 概率)"""
        if not self.loaded:
            raise RuntimeError("模型未加载")
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        x = self.preprocess(img).unsqueeze(0).to(self.device)
        with torch.no_grad():
            feat = self.model.encode_image(x)
            feat = feat / feat.norm(dim=-1, keepdim=True)
            logits = 100.0 * feat @ self.zeroshot_weights   # (1, 12)
            probs = logits.softmax(dim=-1)[0]               # (12,)
        top_idx = int(probs.argmax().item())
        top_prob = float(probs[top_idx].item())
        if top_prob < self.THRESHOLD:
            return "其他", round(top_prob, 4)
        return self.CLASS_NAMES[top_idx], round(top_prob, 4)

    def embed(self, image_bytes):
        """图片 → L2 归一化的 512 维图像特征向量（图文同空间，用于图像匹配与文搜图）"""
        if not self.loaded:
            raise RuntimeError("模型未加载")
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        x = self.preprocess(img).unsqueeze(0).to(self.device)
        with torch.no_grad():
            feat = self.model.encode_image(x)
            feat = feat / feat.norm(dim=-1, keepdim=True)
        return feat.squeeze(0).cpu().numpy().tolist()

    def embed_text(self, text):
        """文本 → L2 归一化的 512 维文本特征向量（与图像同空间，用于文搜图）"""
        if not self.loaded:
            raise RuntimeError("模型未加载")
        tokenized = clip.tokenize([text]).to(self.device)
        with torch.no_grad():
            feat = self.model.encode_text(tokenized)
            feat = feat / feat.norm(dim=-1, keepdim=True)
        return feat.squeeze(0).cpu().numpy().tolist()
