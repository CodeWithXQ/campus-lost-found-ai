# -*- coding: utf-8 -*-
"""
失物招领 AI 图像服务（FastAPI）

启动：uvicorn app:app --host 0.0.0.0 --port 8000
或：  python app.py

接口：
- GET  /health       健康检查（返回模型加载状态）
- POST /embed        图片 → 960 维特征向量
- POST /classify     图片 → 中文物品类别 + 置信度
- POST /similarity   两张图片 → 余弦相似度
"""
import io
import os

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

import numpy as np
from PIL import Image
from fastapi import FastAPI, File, UploadFile, Request
from fastapi.middleware.cors import CORSMiddleware

from model_service import ModelService, Dinov2Service, image_quality

try:
    from clip_service import ClipClassifier
except Exception as _e:  # cn_clip 未安装时降级，不影响 /embed /similarity
    ClipClassifier = None
    print("[app] Chinese-CLIP 不可用，分类将降级为手动选择:", _e)

try:
    from caption_service import CaptionService
    caption_service = CaptionService()
except Exception as _e:
    caption_service = None
    print("[app] 图像描述服务不可用，图片描述降级:", _e)

# PaddleOCR 可选：用于证件卡类图片的文字识别（学号/卡号），未安装时降级
try:
    from paddleocr import PaddleOCR
    _ocr = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
    OCR_AVAILABLE = True
except Exception as _e:
    _ocr = None
    OCR_AVAILABLE = False
    print("[app] PaddleOCR 不可用，OCR 功能降级（仅靠描述文本提取号码）:", _e)

app = FastAPI(title="失物招领 AI 图像服务", version="1.0.0",
              description="MobileNetV3 图像特征提取 / 相似度匹配 / 物品自动分类")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

service = ModelService()
dinov2 = Dinov2Service()
clip = ClipClassifier() if ClipClassifier is not None else None


@app.get("/health")
def health():
    return {
        "status": "ok" if service.loaded else "model_not_loaded",
        "model": service.model_name,
        "device": str(service.device),
        "loaded": service.loaded,
        "dinov2_loaded": bool(dinov2 is not None and dinov2.loaded),
        "clip_loaded": bool(clip is not None and clip.loaded),
    }


@app.post("/embed")
async def embed(file: UploadFile = File(...)):
    data = await file.read()
    # 优先 Chinese-CLIP（512 维，图文同空间，支持文搜图）；未加载/失败时降级 DINOv2/MobileNetV3
    if clip is not None and clip.loaded:
        try:
            vec = clip.embed(data)
            return {"embedding": vec, "dim": len(vec), "model": "Chinese-CLIP"}
        except Exception:
            pass
    if dinov2 is not None and dinov2.loaded:
        try:
            vec = dinov2.embed(data)
            return {"embedding": vec, "dim": len(vec), "model": dinov2.model_name}
        except Exception:
            pass
    vec = service.embed(data)
    return {"embedding": vec, "dim": len(vec), "model": service.model_name}


@app.post("/embed-text")
async def embed_text(req: Request):
    """文本 → 512 维文本特征向量（与图像同空间，用于语义匹配与文搜图）"""
    body = await req.json()
    text = (body or {}).get("text", "")
    if not text:
        return {"embedding": [], "dim": 0, "model": ""}
    if clip is not None and clip.loaded:
        try:
            vec = clip.embed_text(text)
            return {"embedding": vec, "dim": len(vec), "model": "Chinese-CLIP"}
        except Exception:
            pass
    return {"embedding": [], "dim": 0, "model": ""}


@app.post("/classify")
async def classify(file: UploadFile = File(...)):
    data = await file.read()
    if clip is not None and clip.loaded:
        category, confidence = clip.classify(data)
    else:
        category, confidence = "其他", 0.0
    return {"category": category, "confidence": confidence}


@app.post("/quality")
async def quality(file: UploadFile = File(...)):
    """图片清晰度评估，返回 0~1 分数（越高越清晰）"""
    data = await file.read()
    score = image_quality(data)
    return {"quality": score}


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    """证件卡类图片文字识别，返回识别文本；PaddleOCR 未安装时降级返回空文本"""
    if not OCR_AVAILABLE or _ocr is None:
        return {"available": False, "text": ""}
    data = await file.read()
    try:
        img = Image.open(io.BytesIO(data)).convert("RGB")
        result = _ocr.ocr(np.array(img), cls=True)
        lines = []
        if result:
            for block in result:
                if not block:
                    continue
                for word in block:
                    if word and len(word) >= 2:
                        lines.append(word[1][0])
        return {"available": True, "text": "\n".join(lines)}
    except Exception as e:
        return {"available": False, "text": "", "error": str(e)}


@app.post("/similarity")
async def similarity(file1: UploadFile = File(...), file2: UploadFile = File(...)):
    data1 = await file1.read()
    data2 = await file2.read()
    sim = service.similarity(data1, data2)
    return {"similarity": sim}


@app.post("/caption")
async def caption(file: UploadFile = File(...)):
    """图片 → 中文描述（BLIP + 英译中）；模型未加载/失败时降级返回空描述"""
    if caption_service is None:
        return {"available": False, "caption": ""}
    data = await file.read()
    try:
        text = caption_service.caption(data)
        return {"available": bool(text), "caption": text or ""}
    except Exception as e:
        return {"available": False, "caption": "", "error": str(e)}


if __name__ == "__main__":
    import uvicorn

    print("失物招领 AI 图像服务启动: http://127.0.0.1:8000")
    uvicorn.run(app, host="0.0.0.0", port=8000)
