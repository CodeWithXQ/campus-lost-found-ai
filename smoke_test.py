# -*- coding: utf-8 -*-
"""
端到端冒烟测试：登录 -> 发布 -> 智能匹配 -> 通知 -> 认领 -> 统计
运行前提：后端(8080)、MySQL、AI服务(8000) 均已启动
"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def call(method, path, token=None, body=None):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def check(name, cond, extra=""):
    print(("PASS" if cond else "FAIL"), "-", name, extra)
    return cond


ok = True

# 1. 登录
r1 = call("POST", "/api/auth/login", body={"username": "student1", "password": "123456"})
t1 = r1["data"]["token"]
r2 = call("POST", "/api/auth/login", body={"username": "student2", "password": "123456"})
t2 = r2["data"]["token"]
ok &= check("登录 student1/student2", r1["code"] == 200 and r2["code"] == 200)

# 2. 公共列表
r = call("GET", "/api/post/list?type=LOST&page=1&size=5")
ok &= check("公共帖子列表", r["code"] == 200 and r["data"]["total"] >= 1, f"total={r['data']['total']}")

# 3. 发布一条失物（应匹配 student2 的招领"黑色钱包"）
r = call("POST", "/api/post", token=t1, body={
    "type": "LOST", "title": "黑色钱包", "category": "钱包/包",
    "location": "一教302教室", "lostTime": "2026-08-17 12:00:00",
    "description": "黑色皮质钱包，内含身份证和校园卡", "imageUrls": "", "aiCategory": ""
})
new_id = r["data"]["id"]
ok &= check("发布失物", r["code"] == 200, f"postId={new_id}")

# 4. 匹配结果（含匹配依据）
r = call("GET", f"/api/match/post/{new_id}", token=t1)
matches = r["data"]
ok &= check("匹配到招领信息", len(matches) >= 1, f"matchCount={len(matches)}")
if matches:
    top = matches[0]
    print("    top匹配:", top["otherPost"]["title"], "综合分", round(top["finalScore"], 3),
          "| 名称", round(top["nameScore"], 3), "| 地点", round(top["locationScore"], 3),
          "| 类别", round(top["categoryScore"], 3), "| 时间", round(top["timeScore"], 3))

# 5. student2 收到匹配通知
r = call("GET", "/api/notification/list?page=1&size=10", token=t2)
ok &= check("拾主收到匹配通知", r["code"] == 200 and r["data"]["total"] >= 1,
            f"total={r['data']['total']}")

# 6. 认领闭环：student1(失主) 申请 -> student2(拾主) 确认
if matches and matches[0].get("matchId"):
    mid = matches[0]["matchId"]
    r = call("POST", "/api/claim/apply", token=t1, body={"matchId": mid, "message": "钱包内有我的身份证和校园卡"})
    claim_id = r["data"]["id"]
    ok &= check("失主发起认领申请", r["code"] == 200, f"claimId={claim_id}")

    r = call("GET", "/api/claim/my", token=t2)
    ok &= check("拾主可看到待确认申请", r["code"] == 200 and any(c["status"] == "PENDING" for c in r["data"]))

    r = call("POST", f"/api/claim/{claim_id}/confirm", token=t2)
    ok &= check("拾主确认认领", r["code"] == 200)

    r = call("GET", f"/api/post/detail/{new_id}")
    ok &= check("失物帖自动下架(status=1)", r["data"]["status"] == 1, f"status={r['data']['status']}")
else:
    print("SKIP - 无匹配记录，跳过认领流程")

# 7. 统计
r = call("GET", "/api/stats/overview", token=t1)
ok &= check("统计总览", r["code"] == 200 and "totalPosts" in r["data"], json.dumps(r["data"], ensure_ascii=False))
r = call("GET", "/api/stats/success-rate", token=t1)
print("    匹配成功率:", json.dumps(r["data"], ensure_ascii=False))

print("\n===== smoke test result:", "ALL PASS" if ok else "HAS FAILURE", "=====")
