# -*- coding: utf-8 -*-
"""
图像描述生成（captioning）：BLIP 生成英文描述 + OPUS-MT 英译中，输出中文描述。

用于"结合真实图片的匹配解释"：匹配详情悬浮时，展示双方图片各自的 AI 中文描述，
让用户看到 AI 到底从两张真实图片里"读出了什么"。

启动时加载模型（与 clip/dinov2 一致），加载失败时优雅降级（返回 None），
不影响主流程（与 /ocr 降级思路一致）。
"""
import io
import os

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

from PIL import Image


class CaptionService:
    """BLIP（图→英文句）+ MarianMT（英→中）的图片描述服务"""

    def __init__(self):
        self.loaded = False
        self.device = None
        self.processor = None
        self.caption_model = None
        self.trans_tokenizer = None
        self.trans_model = None
        try:
            import torch
            from transformers import BlipProcessor, BlipForConditionalGeneration
            from transformers import MarianMTModel, MarianTokenizer

            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

            # 图像描述（英文）
            self.processor = BlipProcessor.from_pretrained("Salesforce/blip-image-captioning-base")
            self.caption_model = BlipForConditionalGeneration.from_pretrained(
                "Salesforce/blip-image-captioning-base").to(self.device)
            self.caption_model.eval()

            # 英译中
            self.trans_tokenizer = MarianTokenizer.from_pretrained("Helsinki-NLP/opus-mt-en-zh")
            self.trans_model = MarianMTModel.from_pretrained(
                "Helsinki-NLP/opus-mt-en-zh").to(self.device)
            self.trans_model.eval()

            self.loaded = True
        except Exception as e:
            print("[CaptionService] 模型加载失败，图片描述降级:", e)
            self.loaded = False

    def caption(self, image_bytes):
        """图片 → 中文描述；未加载 / 出错返回 None"""
        if not self.loaded:
            return None
        try:
            import torch

            img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            inputs = self.processor(img, return_tensors="pt").to(self.device)
            with torch.no_grad():
                out = self.caption_model.generate(**inputs, max_new_tokens=48, num_beams=1)
            en = self.processor.decode(out[0], skip_special_tokens=True).strip()
            if not en:
                return None

            tok = self.trans_tokenizer([en], return_tensors="pt", padding=True, truncation=True).to(self.device)
            with torch.no_grad():
                zh_ids = self.trans_model.generate(**tok, max_new_tokens=80)
            zh = self.trans_tokenizer.batch_decode(zh_ids, skip_special_tokens=True)[0].strip()
            return zh or en
        except Exception as e:
            print("[CaptionService] 生成描述失败:", e)
            return None
