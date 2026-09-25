# -*- coding: utf-8 -*-
"""
智能匹配算法离线评测脚本

评测指标：MRR / Precision@K / Recall@K / NDCG@K
评测方式：以 sql/init.sql 种子数据的已知正确匹配对作为标注集，调用后端 /api/match/post/{id} 接口。

运行前提：后端已启动（端口 8080），且数据库中存在对应的种子帖子。
运行方式：python evaluate_match.py

L1 权重分析：脚本末尾输出各维度分数均值，用于观察"哪个维度对正确匹配贡献大"，
            为人工调权（阶段七 L1 统计调权）提供数据依据。
"""
import math
import sys

import requests

BASE = "http://localhost:8080/api"
K = 3  # 取前 K 个匹配评估

# 已知正确匹配对（失物标题 -> 招领标题），来自 sql/init.sql 种子数据
GROUND_TRUTH = [
    ("蓝色书包", "蓝色双肩包"),
    ("黑色钱包", "黑色钱包"),
]


def get_posts(ptype):
    resp = requests.get(f"{BASE}/post/list", params={"type": ptype, "size": 100}, timeout=10)
    return resp.json()["data"]["records"]


def get_matches(post_id):
    resp = requests.get(f"{BASE}/match/post/{post_id}", timeout=10)
    return resp.json()["data"] or []


def dcg_at_k(rels, k):
    """DCG@k：rels[i] 为第 i 位的相关性（1 正确 / 0 错误）"""
    dcg = 0.0
    for i in range(min(k, len(rels))):
        dcg += (2 ** rels[i] - 1) / math.log2(i + 2)
    return dcg


def evaluate():
    try:
        lost_posts = get_posts("LOST")
        found_posts = get_posts("FOUND")
    except Exception as e:
        print(f"[错误] 无法连接后端，请确认后端已启动: {e}")
        sys.exit(1)

    found_by_title = {p["title"]: p["id"] for p in found_posts}

    mrr = []
    recall_k = []
    precision_k = []
    ndcg_k = []
    dimension_stats = {
        "nameScore": [], "categoryScore": [], "locationScore": [],
        "timeScore": [], "descriptionScore": [], "semanticScore": [], "imageScore": [],
    }

    for lost_title, found_title in GROUND_TRUTH:
        lost = next((p for p in lost_posts if p["title"] == lost_title), None)
        true_id = found_by_title.get(found_title)
        if not lost or true_id is None:
            print(f"[跳过] 缺少标注样本: {lost_title} -> {found_title}")
            continue

        matches = get_matches(lost["id"])
        ranked_ids = [m["otherPost"]["id"] for m in matches]

        # 记录各维度分数（用于 L1 权重分析）
        for m in matches:
            for dim in dimension_stats:
                v = m.get(dim)
                if v is not None:
                    dimension_stats[dim].append(v)

        if true_id in ranked_ids:
            rank = ranked_ids.index(true_id) + 1
            mrr.append(1.0 / rank)
            top_k = ranked_ids[:K]
            precision_k.append(1.0 / K)
            recall_k.append(1.0 if true_id in top_k else 0.0)
            rels = [1 if pid == true_id else 0 for pid in ranked_ids]
            idcg = dcg_at_k([1] + [0] * (K - 1), K)
            ndcg_k.append(dcg_at_k(rels, K) / idcg if idcg > 0 else 0.0)
            print(f"[命中] 失物《{lost_title}》 -> 正确匹配《{found_title}》排名第 {rank}")
        else:
            precision_k.append(0.0)
            recall_k.append(0.0)
            mrr.append(0.0)
            ndcg_k.append(0.0)
            print(f"[未命中] 失物《{lost_title}》 未匹配到《{found_title}》")

    if not mrr:
        print("无可评测样本")
        return

    print("\n===== 智能匹配评测结果 =====")
    print(f"样本数: {len(mrr)}")
    print(f"MRR (平均倒数排名): {sum(mrr) / len(mrr):.4f}")
    print(f"Precision@{K}: {sum(precision_k) / len(precision_k):.4f}")
    print(f"Recall@{K}: {sum(recall_k) / len(recall_k):.4f}")
    print(f"NDCG@{K}: {sum(ndcg_k) / len(ndcg_k):.4f}")

    print("\n===== 各维度分数均值（L1 权重分析参考） =====")
    for dim, vals in dimension_stats.items():
        if vals:
            print(f"{dim}: 均值 {sum(vals) / len(vals):.4f} (样本 {len(vals)})")


if __name__ == "__main__":
    evaluate()
