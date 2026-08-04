"""RAG 检索离线评测（对应手册 P2 · Day28/29【核心】）。

对评测集里的每条问题，用三种检索模式分别取 top-5：
  纯向量 / 纯 BM25 / 混合（RRF 融合）
统计 Hit@1、Hit@5、MRR，输出对比表 + 未命中的失败案例。

用法：
  conda activate repairmind
  cd repairmind-service && python eval/run_eval.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from app.services.retriever import retriever  # noqa: E402
from app.services.retriever import _chunk_by_id  # noqa: E402

QUESTIONS_FILE = PROJECT_ROOT / "eval" / "questions.json"
MODES = ["vector", "bm25", "hybrid", "hybrid_rerank"]  # hybrid_rerank 未下载模型时回退 hybrid
TOP_K = 5


def load_questions() -> list[dict]:
    with QUESTIONS_FILE.open("r", encoding="utf-8") as f:
        data = json.load(f)
    return data["questions"]


def verbatim_overlap(question: str, chunk_text: str, n: int = 10) -> bool:
    """检查问题是否与来源 chunk 存在 >=n 字的重合（手册 Day28：问题不能抄语料原句）。"""
    if not chunk_text or len(question) < n:
        return False
    grams = {chunk_text[i:i + n] for i in range(len(chunk_text) - n + 1)}
    return any(question[i:i + n] in grams for i in range(len(question) - n + 1))


def validate_questions(questions: list[dict]) -> None:
    """评测集自校验：字段齐全、chunk_id 存在、问题不与来源原句重合。"""
    by_id = _chunk_by_id()
    missing = []
    overlap = []
    for q in questions:
        assert q.get("question"), "问题不能为空"
        assert q.get("standard_points"), "标准要点不能为空"
        for cid in q["expected_chunk_ids"]:
            if cid not in by_id:
                missing.append((q["question"][:20], cid))
            elif verbatim_overlap(q["question"], by_id[cid]["text"]):
                overlap.append(q["question"][:24])
    if missing:
        print("⚠️ 评测集引用了不存在的 chunk_id:")
        for q, cid in missing:
            print("  ", q, "->", cid)
        sys.exit(1)
    if overlap:
        print("⚠️ 以下问题与来源 chunk 存在原句重合（应改写）：")
        for q in overlap:
            print("  ✗", q)
        sys.exit(1)
    print(f"✅ 评测集自校验通过：{len(questions)} 条问题，chunk_id 全部存在且无原句重合\n")


def split_key(i: int) -> str:
    """开发/测试集划分：每 5 条取 1 条进测试集（约 80/20），确定性可复现。"""
    return "test" if i % 5 == 4 else "dev"


def mrr_for(expected: list[str], ranked: list[str]) -> float:
    for i, cid in enumerate(ranked, start=1):
        if cid in expected:
            return 1.0 / i
    return 0.0


def run_mode(questions: list[dict], mode: str) -> dict:
    hits1 = hits5 = 0
    mrr_sum = 0.0
    failures = []
    for q in questions:
        ranked = [r["chunk_id"] for r in retriever.search(q["question"], top_k=TOP_K, mode=mode)]
        expected = q["expected_chunk_ids"]
        if ranked and ranked[0] in expected:
            hits1 += 1
        if any(c in ranked for c in expected):
            hits5 += 1
        else:
            failures.append(q["question"])
        mrr_sum += mrr_for(expected, ranked)

    n = len(questions)
    return {
        "mode": mode,
        "n": n,
        "hit@1": round(hits1 / n, 4),
        "hit@5": round(hits5 / n, 4),
        "mrr": round(mrr_sum / n, 4),
        "failures": failures,
    }


def main() -> None:
    questions = load_questions()
    validate_questions(questions)

    print(f"评测集：{len(questions)} 条问题，top-{TOP_K}，三种模式对比\n")
    print(f"{'模式':<10}{'Hit@1':>8}{'Hit@5':>8}{'MRR':>8}{'未命中':>8}")
    print("-" * 42)

    results = {}
    for mode in MODES:
        r = run_mode(questions, mode)
        results[mode] = r
        print(f"{mode:<10}{r['hit@1']:>8}{r['hit@5']:>8}{r['mrr']:>8}{len(r['failures']):>8}")

    best = max(MODES, key=lambda m: (results[m]["hit@5"], results[m]["mrr"]))
    print(f"\n🏆 最优模式：{best}（Hit@5 与 MRR 综合）")

    # 开发/测试集划分（确定性，约 80/20）
    dev = [q for i, q in enumerate(questions) if split_key(i) == "dev"]
    tst = [q for i, q in enumerate(questions) if split_key(i) == "test"]
    print("\n开发/测试集划分（hybrid，确定性 80/20）：")
    for name, subset in [("dev", dev), ("test", tst)]:
        r = run_mode(subset, "hybrid")
        print(f"  {name:<4} n={len(subset)}  Hit@5={r['hit@5']:.3f}  MRR={r['mrr']:.3f}")

    print("\n失败案例（混合模式未命中）：")
    for q in results["hybrid"]["failures"]:
        print("  ✗", q)


if __name__ == "__main__":
    main()
