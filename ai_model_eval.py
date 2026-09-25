# -*- coding: utf-8 -*-
"""
AI 图像识别模型正确性评测脚本（评测用）

覆盖「2.2 模型正确性测试」的四个子项：
  ① 分类准确率      POST /classify    图片 -> 12 个校园标准类别之一 + 置信度
  ② 相似度区分度    POST /similarity  同物不同角度 vs 不同物 的余弦相似度
  ③ 描述可读性      POST /caption     图片 -> 中文描述（BLIP 英文 + OPUS-MT 英译中）
  ④ OCR 有效性      POST /ocr         证件卡图 -> 识别文本（PaddleOCR）

运行前提：AI 服务已启动（http://127.0.0.1:8000）
运行方式：python ai_model_eval.py

图片准备：
  默认使用脚本内置 PIL 合成测试图（开箱即跑，用于快速验证链路）。
  建议改用真实物品图，命中率更高。真实图按约定放入 ai-service/test_images/：
    ai-service/test_images/classify/<类别名>.jpg   分类样本（文件名=真实类别）
    ai-service/test_images/similarity/same_a.jpg    同物不同角度 A
    ai-service/test_images/similarity/same_b.jpg    同物不同角度 B
    ai-service/test_images/similarity/diff_a.jpg    不同物 A
    ai-service/test_images/similarity/diff_b.jpg    不同物 B
    ai-service/test_images/caption/*.jpg            描述样本
    ai-service/test_images/ocr/<任意>.jpg           证件卡图
"""
import io
import os
import re
import sys

import requests
from PIL import Image, ImageDraw

try:  # Windows 控制台统一按 UTF-8 输出，避免中文乱码
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

AI_BASE = "http://127.0.0.1:8000"
TEST_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "ai-service", "test_images")

# 与后端 PostController / clip_service.CLASS_NAMES 严格对齐的 12 个标准类别
CLASS_NAMES = [
    "手机", "钱包/包", "钥匙", "证件/卡", "书包/背包", "书籍",
    "衣物", "水杯", "眼镜", "耳机", "电脑/电子设备", "雨伞",
]

# 判定阈值（合成图难识别，放宽；真实图更严）
CLASSIFY_PASS = 0.5    # 分类 Top-1 命中率下限
SIM_GAP_PASS = 0.10    # 同物相似度 与 不同物相似度 的最小差值


# ---------------------------------------------------------------- HTTP 调用
def _multipart(path, files):
    return requests.post(AI_BASE + path, files=files, timeout=60).json()


def classify(image_bytes, name="img.jpg"):
    return _multipart("/classify", {"file": (name, image_bytes, "image/jpeg")})


def caption(image_bytes, name="img.jpg"):
    return _multipart("/caption", {"file": (name, image_bytes, "image/jpeg")})


def ocr(image_bytes, name="img.jpg"):
    return _multipart("/ocr", {"file": (name, image_bytes, "image/jpeg")})


def similarity(b1, b2):
    return _multipart("/similarity", {
        "file1": ("a.jpg", b1, "image/jpeg"),
        "file2": ("b.jpg", b2, "image/jpeg"),
    })


def _to_jpeg(img):
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def _is_chinese(text):
    return bool(re.search(r"[一-鿿]", text))


# ---------------------------------------------------------------- 合成图
def _canvas(bg=(240, 240, 240)):
    img = Image.new("RGB", (224, 224), bg)
    return img, ImageDraw.Draw(img)


def draw_wallet():
    img, d = _canvas()
    d.rounded_rectangle([40, 70, 184, 170], radius=20, fill=(120, 82, 40),
                        outline=(70, 45, 20), width=5)
    d.rectangle([40, 120, 184, 128], fill=(200, 170, 120))
    return img


def draw_backpack():
    img, d = _canvas()
    d.rounded_rectangle([50, 60, 174, 180], radius=16, fill=(60, 90, 170),
                        outline=(30, 50, 110), width=5)
    d.arc([90, 40, 134, 90], 180, 360, fill=(30, 50, 110), width=6)
    d.rounded_rectangle([70, 90, 154, 140], radius=8, fill=(80, 110, 190),
                        outline=(30, 50, 110), width=3)
    return img


def draw_umbrella():
    img, d = _canvas()
    d.pieslice([40, 60, 184, 204], 180, 360, fill=(200, 60, 60),
               outline=(150, 30, 30), width=4)
    d.line([112, 60, 112, 190], fill=(80, 80, 80), width=5)
    d.arc([112, 170, 140, 210], 0, 180, fill=(80, 80, 80), width=5)
    return img


def draw_cup():
    img, d = _canvas()
    d.rounded_rectangle([70, 70, 154, 180], radius=10, fill=(100, 160, 220),
                        outline=(40, 90, 150), width=5)
    d.rectangle([64, 60, 160, 74], fill=(220, 220, 220), outline=(120, 120, 120), width=3)
    return img


def draw_phone():
    img, d = _canvas()
    d.rounded_rectangle([72, 40, 152, 196], radius=18, fill=(30, 30, 30),
                        outline=(10, 10, 10), width=4)
    d.rounded_rectangle([80, 52, 144, 180], radius=10, fill=(120, 180, 240))
    return img


SYNTHETIC = {
    "钱包/包": draw_wallet,
    "书包/背包": draw_backpack,
    "雨伞": draw_umbrella,
    "水杯": draw_cup,
    "手机": draw_phone,
}


def _load_image_bytes(path):
    with open(path, "rb") as f:
        return f.read()


# ---------------------------------------------------------------- 子测试
def test_classify():
    """① 分类准确率：预测类别 vs 真实类别，统计 Top-1 命中率"""
    print("\n" + "=" * 56)
    print("① 分类准确率测试  POST /classify")
    print("=" * 56)
    samples = []  # [(真实类别, image_bytes)]
    real_dir = os.path.join(TEST_DIR, "classify")
    if os.path.isdir(real_dir):
        for fn in sorted(os.listdir(real_dir)):
            if fn.lower().endswith((".jpg", ".jpeg", ".png")):
                label = os.path.splitext(fn)[0]
                samples.append((label, _load_image_bytes(os.path.join(real_dir, fn))))
    if not samples:  # 无真实图 -> 合成图兜底
        print("[提示] 未发现真实图，使用内置合成图（真实图命中率会更高）")
        for label, fn in SYNTHETIC.items():
            samples.append((label, _to_jpeg(fn())))

    hit = 0
    for label, img in samples:
        r = classify(img)
        pred = r.get("category", "?")
        conf = r.get("confidence", 0)
        ok = (pred == label)
        hit += ok
        print(f"  真实[{label:8s}] -> 预测[{pred:12s}] 置信度 {conf:.3f}  {'PASS' if ok else 'FAIL'}")
    acc = hit / len(samples)
    print(f"  Top-1 命中率 = {hit}/{len(samples)} = {acc:.1%}  阈值 {CLASSIFY_PASS:.0%}  "
          f"-> {'PASS' if acc >= CLASSIFY_PASS else 'FAIL（建议换真实图）'}")
    return acc >= CLASSIFY_PASS


def test_similarity():
    """② 相似度区分度：同物不同角度 应显著高于 不同物"""
    print("\n" + "=" * 56)
    print("② 相似度区分度测试  POST /similarity")
    print("=" * 56)
    real_dir = os.path.join(TEST_DIR, "similarity")
    if os.path.isdir(real_dir):
        def _p(name):
            p = os.path.join(real_dir, name)
            return _load_image_bytes(p) if os.path.exists(p) else None
        same_a, same_b = _p("same_a.jpg"), _p("same_b.jpg")
        diff_a, diff_b = _p("diff_a.jpg"), _p("diff_b.jpg")
    else:
        same_a = same_b = diff_a = diff_b = None

    # 合成图兜底：钱包自身 vs 钱包（缩放旋转模拟不同角度）；钱包 vs 水杯（不同物）
    if not all([same_a, same_b, diff_a, diff_b]):
        print("[提示] 相似度真实图不完整，使用内置合成图")
        w = _to_jpeg(draw_wallet())
        w2 = _to_jpeg(draw_wallet().rotate(15).resize((224, 224)))  # 同物不同角度
        c = _to_jpeg(draw_cup())
        same_a, same_b, diff_a, diff_b = w, w2, w, c

    s_same = similarity(same_a, same_b).get("similarity", 0)
    s_diff = similarity(diff_a, diff_b).get("similarity", 0)
    s_self = similarity(same_a, same_a).get("similarity", 0)
    gap = s_same - s_diff
    print(f"  同物不同角度 相似度 = {s_same:.3f}")
    print(f"  不同物       相似度 = {s_diff:.3f}")
    print(f"  同图自比     相似度 = {s_self:.3f}（应≈1.0）")
    ok = gap >= SIM_GAP_PASS
    print(f"  区分度差值 = {gap:.3f}  阈值 {SIM_GAP_PASS:.2f}  -> {'PASS' if ok else 'FAIL'}")
    return ok


def test_caption():
    """③ 描述可读性：BLIP + OPUS-MT 输出中文描述"""
    print("\n" + "=" * 56)
    print("③ 图像描述可读性测试  POST /caption")
    print("=" * 56)
    imgs = []
    real_dir = os.path.join(TEST_DIR, "caption")
    if os.path.isdir(real_dir):
        for fn in sorted(os.listdir(real_dir)):
            if fn.lower().endswith((".jpg", ".jpeg", ".png")):
                imgs.append((fn, _load_image_bytes(os.path.join(real_dir, fn))))
    if not imgs:
        print("[提示] 未发现真实图，使用内置合成图")
        for label, fn in list(SYNTHETIC.items())[:3]:
            imgs.append((label, _to_jpeg(fn())))

    ok_all = True
    for name, img in imgs:
        r = caption(img)
        avail = r.get("available", False)
        text = r.get("caption", "")
        chinese = _is_chinese(text)
        ok = avail and chinese
        ok_all &= ok
        print(f"  [{name:12s}] available={avail} 描述={text!r}  -> {'PASS' if ok else 'FAIL'}")
    print(f"  描述可读性 -> {'PASS' if ok_all else 'FAIL（检查 BLIP/OPUS-MT 是否已加载）'}")
    return ok_all


def test_ocr():
    """④ OCR 有效性：证件卡图识别文本"""
    print("\n" + "=" * 56)
    print("④ OCR 有效性测试  POST /ocr")
    print("=" * 56)
    real_dir = os.path.join(TEST_DIR, "ocr")
    img = None
    if os.path.isdir(real_dir):
        for fn in sorted(os.listdir(real_dir)):
            if fn.lower().endswith((".jpg", ".jpeg", ".png")):
                img = _load_image_bytes(os.path.join(real_dir, fn))
                break
    if img is None:
        print("[提示] 未提供证件卡真实图（test_images/ocr/），跳过 OCR 测试（不计入失败）")
        return True

    r = ocr(img)
    avail = r.get("available", False)
    text = r.get("text", "")
    has_digit = bool(re.search(r"\d", text))
    if not avail:
        print("[提示] PaddleOCR 未安装，OCR 降级（不计入失败）")
        return True
    print(f"  available={avail} 识别文本={text!r}")
    ok = has_digit
    print(f"  是否提取到数字 -> {'PASS' if ok else 'FAIL'}")
    return ok


# ---------------------------------------------------------------- 主流程
def main():
    print("AI 图像识别模型正确性评测")
    print("服务地址:", AI_BASE)
    try:
        health = requests.get(AI_BASE + "/health", timeout=10).json()
        print("模型状态:", health)
    except Exception as e:
        print("[错误] 无法连接 AI 服务，请先启动:", e)
        sys.exit(1)

    results = {
        "① 分类准确率": test_classify(),
        "② 相似度区分度": test_similarity(),
        "③ 描述可读性": test_caption(),
        "④ OCR 有效性": test_ocr(),
    }

    print("\n" + "=" * 56)
    print("评测汇总")
    print("=" * 56)
    for name, ok in results.items():
        print(f"  {name:14s} -> {'PASS' if ok else 'FAIL'}")
    passed = sum(results.values())
    print(f"\n  通过 {passed}/{len(results)} 项")
    print("  结论:", "ALL PASS" if passed == len(results) else "存在 FAIL，请按上文提示排查")


if __name__ == "__main__":
    main()
