# -*- coding: utf-8 -*-
# 验证 AI 图像链路：上传图片 -> AI 自动分类 -> 发布时特征提取（embed）
import io
import json
import urllib.request
from PIL import Image, ImageDraw

BASE = "http://127.0.0.1:8080"


def call(method, path, token=None, body=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = body if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


# 生成一张"钱包"风格的测试图（棕色矩形 + 圆角）
img = Image.new("RGB", (224, 224), (200, 180, 140))
d = ImageDraw.Draw(img)
d.rounded_rectangle([20, 40, 204, 184], radius=24, fill=(120, 82, 40), outline=(70, 45, 20), width=6)
d.rectangle([20, 100, 204, 106], fill=(200, 170, 120))
buf = io.BytesIO()
img.save(buf, format="JPEG")
img_bytes = buf.getvalue()
print("测试图生成完成, 大小", len(img_bytes), "bytes")

# 登录
t1 = call("POST", "/api/auth/login", body=json.dumps({"username": "student3", "password": "123456"}).encode())["data"]["token"]

import uuid
def multipart(img_bytes, filename="wallet.jpg"):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            "Content-Type: image/jpeg\r\n\r\n").encode() + img_bytes + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"

# 1. AI 自动分类（multipart）
b, ct = multipart(img_bytes)
r = call("POST", "/api/ai/classify", token=t1, body=b, content_type=ct)
print("AI 分类:", json.dumps(r, ensure_ascii=False))
assert r["code"] == 200 and r["data"].get("available"), "AI 分类失败"

# 2. 上传图片
b, ct = multipart(img_bytes)
up = call("POST", "/api/file/upload", token=t1, body=b, content_type=ct)
print("上传结果:", json.dumps(up, ensure_ascii=False))
path = up["data"]["path"]
assert up["code"] == 200

# 3. 发布带图失物（应触发后端 AI 特征提取 embed）
pub = call("POST", "/api/post", token=t1, body=json.dumps({
    "type": "LOST", "title": "棕色钱包", "category": "钱包/包",
    "location": "图书馆", "lostTime": "2026-08-17 09:00:00",
    "description": "棕色皮质钱包", "imageUrls": path, "aiCategory": ""
}, ensure_ascii=False).encode())
p = pub["data"]
print("发布带图帖子 postId=", p["id"], "| aiCategory=", p["aiCategory"])
fv = p.get("featureVector")
print("特征向量是否已提取:", bool(fv), "| 向量维度:", len(fv.split(",")) if fv else 0)
assert pub["code"] == 200 and fv, "发布时 AI 特征提取失败"

print("\n===== AI 图像链路验证: ALL PASS =====")
