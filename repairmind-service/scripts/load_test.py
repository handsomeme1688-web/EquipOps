"""并发压测（对应手册 Day34）。

- stub 模式压的是本地链路（排除第三方限流干扰）；真实模式压端到端；
- CPU 密集的 embedding 不能靠 asyncio.gather 假并行——本脚本压的是 HTTP 接口，
  服务端 embedding 默认跑在 CPU，测的是整体吞吐与延迟分布。

用法：
  uvicorn app.main:app --port 8001 &            # 先起服务
  python scripts/load_test.py --n 20 --concurrency 5
"""
from __future__ import annotations

import argparse
import asyncio
import statistics
import time

import httpx

QUESTIONS = [
    "怎么拆内存条",
    "更换电池前要做什么准备",
    "EQ-1001 的维修历史",
    "键盘进液了怎么清理",
    "怎么拆基座护盖",
]


async def one(client: httpx.AsyncClient, url: str, q: str) -> tuple[int, float]:
    t0 = time.perf_counter()
    r = await client.post(url + "/ask", json={"question": q, "tenant_id": "t1"})
    return r.status_code, (time.perf_counter() - t0) * 1000


async def run(url: str, n: int, concurrency: int) -> tuple[int, list[float]]:
    async with httpx.AsyncClient(timeout=60) as client:
        sem = asyncio.Semaphore(concurrency)

        async def worker(q: str):
            async with sem:
                return await one(client, url, q)

        tasks = [worker(QUESTIONS[i % len(QUESTIONS)]) for i in range(n)]
        latencies: list[float] = []
        ok = 0
        for coro in asyncio.as_completed(tasks):
            status, dt = await coro
            latencies.append(dt)
            if status == 200:
                ok += 1
        return ok, latencies


def main() -> None:
    parser = argparse.ArgumentParser(description="RepairMind /ask 并发压测")
    parser.add_argument("--url", default="http://localhost:8001")
    parser.add_argument("--n", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=5)
    args = parser.parse_args()

    ok, lat = asyncio.run(run(args.url, args.n, args.concurrency))
    lat.sort()

    def pct(p: float) -> float:
        return lat[min(len(lat) - 1, int(len(lat) * p))]

    total_s = sum(lat) / 1000
    print(f"请求数: {len(lat)}  成功: {ok}  失败: {len(lat) - ok}")
    print(f"延迟(ms) p50={pct(0.5):.0f}  p90={pct(0.9):.0f}  "
          f"p95={pct(0.95):.0f}  p99={pct(0.99):.0f}  max={lat[-1]:.0f}")
    if total_s > 0:
        print(f"平均 {statistics.mean(lat):.0f} ms   吞吐约 {len(lat) / total_s:.1f} req/s")


if __name__ == "__main__":
    main()
