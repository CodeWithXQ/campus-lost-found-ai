# -*- coding: utf-8 -*-
"""
MobileNetV3 图像特征提取 / 相似度匹配 / 物品自动分类

技术说明：
- 特征提取：MobileNetV3-Large（ImageNet 预训练），去掉分类头取 960 维特征向量，L2 归一化
- 相似度匹配：两个特征向量的余弦相似度（0~1）
- 自动分类：复用分类头 top-5 预测，将 ImageNet 英文标签映射为中文校园物品类别
- 全程本地离线推理，无需 GPU，无需调用任何大模型 API（方案2 的"AI 升级版"核心）
"""
import io

import numpy as np
import torch
from PIL import Image
from torchvision import transforms
from torchvision.models import MobileNet_V3_Large_Weights, mobilenet_v3_large

from categories import IMAGENET_CATEGORY_MAP

# 本机可能存在多个 OpenMP 运行库冲突（Anaconda 常见），提前设置规避
import os
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

# 模型权重缓存到本服务目录内（避免写入用户目录权限受限，也便于离线复用）
_TORCH_HOME = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".torch_cache")
os.environ.setdefault("TORCH_HOME", _TORCH_HOME)

# DINOv2 权重托管在 huggingface.co，国内直连常超时，默认走国内镜像（已显式设置时尊重用户配置）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")


class ModelService:
    """封装 MobileNetV3 的图像能力"""

    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model_name = "MobileNetV3-Large"
        self.loaded = False
        self.labels = []
        try:
            weights = MobileNet_V3_Large_Weights.IMAGENET1K_V1
            self.model = mobilenet_v3_large(weights=weights)
            self.model.eval()
            self.model.to(self.device)
            self.labels = weights.meta["categories"]
            self.loaded = True
        except Exception as e:  # 首次运行需联网下载权重；失败时记录原因
            print("[ModelService] 模型加载失败:", e)
            self.model = None

        self.transform = transforms.Compose([
            transforms.Resize(256),
            transforms.CenterCrop(224),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ])

    # ---------- 内部工具 ----------

    def _load_image(self, image_bytes):
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        return img

    def _embed_tensor(self, x):
        """提取 960 维特征向量（不归一化）"""
        with torch.no_grad():
            x = self.model.features(x)
            x = self.model.avgpool(x)
            x = torch.flatten(x, 1)
            return x

    # ---------- 对外能力 ----------

    def embed(self, image_bytes):
        """图片 → L2 归一化的 960 维特征向量（list[float]）"""
        if not self.loaded:
            raise RuntimeError("模型未加载")
        img = self._load_image(image_bytes)
        x = self.transform(img).unsqueeze(0).to(self.device)
        v = self._embed_tensor(x)
        v = v / v.norm(dim=1, keepdim=True).clamp_min(1e-9)
        return v.squeeze(0).cpu().numpy().tolist()

    def classify(self, image_bytes):
        """图片 → (中文标准类别, 置信度)

        取 top-5 预测，返回第一个能映射到「标准类别」的标签；映射表只含 12 个具体类别，
        不再有「其他」值，因此不会因 top-1 命中某个"其他"标签而跳过正确的 top-2/top-3。
        top-5 全部无法映射时兜底返回 ("其他", 0.0)，表示"未识别出具体物品"。
        """
        if not self.loaded:
            raise RuntimeError("模型未加载")
        img = self._load_image(image_bytes)
        x = self.transform(img).unsqueeze(0).to(self.device)
        with torch.no_grad():
            emb = self._embed_tensor(x)
            logits = self.model.classifier(emb)
            probs = torch.softmax(logits, dim=1)[0]
        topk = torch.topk(probs, 5)
        for i in range(5):
            idx = int(topk.indices[i].item())
            label = self.labels[idx] if idx < len(self.labels) else ""
            category = IMAGENET_CATEGORY_MAP.get(label)
            if category:
                return category, round(float(topk.values[i].item()), 4)
        return "其他", 0.0

    def similarity(self, image_bytes_a, image_bytes_b):
        """两张图片的余弦相似度（0~1）"""
        va = np.array(self.embed(image_bytes_a), dtype=np.float64)
        vb = np.array(self.embed(image_bytes_b), dtype=np.float64)
        denom = np.linalg.norm(va) * np.linalg.norm(vb) + 1e-9
        cos = float(np.dot(va, vb) / denom)
        return max(0.0, min(1.0, cos))


class Dinov2Service:
    """DINOv2 自监督特征提取（实例级检索能力优于 ImageNet 分类特征）

    相比 MobileNetV3（ImageNet 分类监督，学的是"分门别类"），DINOv2 自监督训练
    保留了更多局部纹理与实例细节，对"同一物品不同角度/光照"的相似度计算更鲁棒，
    是失物招领图片匹配的更优底座。特征维度 384（DINOv2-Small）。

    特征提取采用 5-crop 多裁剪（中心 + 四角）取平均，缓解物品偏离中心 / 背景干扰。
    """

    def __init__(self):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model_name = "DINOv2-Small"
        self.dim = 384
        self.crop_size = 224
        self.resize_size = 256
        self.loaded = False
        try:
            import timm
            self.model = timm.create_model(
                "vit_small_patch14_dinov2.lvd142m", pretrained=True, num_classes=0)
            self.model.eval()
            self.model.to(self.device)
            data_config = timm.data.resolve_data_config(self.model.pretrained_cfg)
            self.mean = tuple(data_config.get("mean", (0.485, 0.456, 0.406)))
            self.std = tuple(data_config.get("std", (0.229, 0.224, 0.225)))
            # DINOv2 标准输入尺寸（lvd142m 为 518），5-crop 需按此尺寸裁剪
            input_size = data_config.get("input_size", (3, 224, 224))
            self.crop_size = input_size[-1]
            self.resize_size = self.crop_size + 32
            self.loaded = True
        except Exception as e:  # 首次运行需联网下载权重；失败时记录原因，由上层降级
            print("[Dinov2Service] 模型加载失败:", e)
            self.model = None
        # transform 只做 ToTensor + Normalize；resize/crop 由 _five_crops 手动控制
        self.transform = transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize(mean=self.mean, std=self.std),
        ])

    def _five_crops(self, img):
        """resize 到 256 后取 中心 + 四角 的 224×224，共 5 个裁剪"""
        img = img.resize((self.resize_size, self.resize_size), Image.BILINEAR)
        s = self.crop_size
        d = self.resize_size - self.crop_size  # 32
        return [
            img.crop((0, 0, s, s)),                                  # 左上
            img.crop((d, 0, d + s, s)),                              # 右上
            img.crop((0, d, s, d + s)),                              # 左下
            img.crop((d, d, d + s, d + s)),                          # 右下
            img.crop((d // 2, d // 2, d // 2 + s, d // 2 + s)),      # 中心
        ]

    def _embed_crop(self, crop):
        x = self.transform(crop).unsqueeze(0).to(self.device)
        with torch.no_grad():
            feat = self.model.forward_features(x)  # (1, 1+patches, 384)
            n_prefix = getattr(self.model, "num_prefix_tokens", 1)
            feat = feat[:, n_prefix:, :].mean(dim=1)
            feat = feat / feat.norm(dim=1, keepdim=True).clamp_min(1e-9)
        return feat

    def embed(self, image_bytes):
        """图片 → L2 归一化的 384 维特征向量（5-crop 平均，list[float]）"""
        if not self.loaded:
            raise RuntimeError("模型未加载")
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        feats = [self._embed_crop(c) for c in self._five_crops(img)]
        v = torch.stack(feats).mean(dim=0)
        v = v / v.norm().clamp_min(1e-9)
        return v.squeeze(0).cpu().numpy().tolist()


def image_quality(image_bytes):
    """拉普拉斯方差评估图像清晰度，返回 0~1 分数（越高越清晰）。

    用于后端在匹配时对模糊图降权、或前端提示用户重拍，不依赖任何深度学习模型。
    """
    from PIL import ImageFilter
    img = Image.open(io.BytesIO(image_bytes)).convert("L")
    lap = img.filter(ImageFilter.Kernel((3, 3), [0, 1, 0, 1, -4, 1, 0, 1, 0], scale=1))
    variance = float(np.asarray(lap, dtype=np.float64).var())
    return round(min(1.0, variance / 200.0), 4)
