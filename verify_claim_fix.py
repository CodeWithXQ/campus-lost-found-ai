# -*- coding: utf-8 -*-
# 针对性验证：拾主能正确看到"待确认"的认领申请
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def call(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())


t1 = call("POST", "/api/auth/login", body={"username": "student1", "password": "123456"})["data"]["token"]
t2 = call("POST", "/api/auth/login", body={"username": "student2", "password": "123456"})["data"]["token"]

# student1 发布失物"蓝色书包"（应匹配 student2 的招领"蓝色双肩包"）
r = call("POST", "/api/post", token=t1, body={
    "type": "LOST", "title": "蓝色书包", "category": "书包/背包",
    "location": "图书馆", "lostTime": "2026-08-16 10:00:00",
    "description": "蓝色双肩书包", "imageUrls": "", "aiCategory": ""
})
pid = r["data"]["id"]
print("发布失物 postId=", pid)

matches = call("GET", f"/api/match/post/{pid}", token=t1)["data"]
top = matches[0] if matches else None
print("top 匹配:", top["otherPost"]["title"] if top else None,
      "matchId=", top["matchId"] if top else None,
      "score=", round(top["finalScore"], 3) if top else None)

assert top and top["matchId"], "未生成匹配记录"

# student1 申请认领
claim = call("POST", "/api/claim/apply", token=t1, body={"matchId": top["matchId"], "message": "包内有我的教材"})
print("申请认领 claimId=", claim["data"]["id"], "status=", claim["data"]["status"])

# 关键：student2（拾主）应能看到 PENDING 待确认申请
mine = call("GET", "/api/claim/my", token=t2)["data"]
pending = [c for c in mine if c["status"] == "PENDING"]
print("student2 视角待确认申请数:", len(pending))
for c in pending:
    print("  -", c["postTitle"], "申请人:", c["claimantName"], "状态:", c["status"])

assert len(pending) >= 1, "拾主看不到待确认申请（bug 未修复）"

# student2 确认
call("POST", f"/api/claim/{claim['data']['id']}/confirm", token=t2)
final = call("GET", f"/api/post/detail/{pid}")["data"]
print("确认后失物帖状态:", final["status"], "(应为1已下架)")
assert final["status"] == 1
print("\n===== 认领闭环修复验证: ALL PASS =====")
