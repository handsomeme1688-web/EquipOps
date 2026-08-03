#!/usr/bin/env python3
"""Summarize JMeter CSV result files without third-party dependencies."""

from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path


def percentile(sorted_values: list[int], ratio: float) -> int:
    """Return the nearest-rank percentile used for this benchmark report."""
    rank = max(1, math.ceil(len(sorted_values) * ratio))
    return sorted_values[rank - 1]


def summarize(path: Path) -> dict[str, str | int | float]:
    elapsed_values: list[int] = []
    starts: list[int] = []
    finishes: list[int] = []
    errors = 0

    with path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            elapsed = int(row["elapsed"])
            started_at = int(row["timeStamp"])
            elapsed_values.append(elapsed)
            starts.append(started_at)
            finishes.append(started_at + elapsed)
            if row["success"].lower() != "true":
                errors += 1

    if not elapsed_values:
        raise ValueError(f"JTL contains no samples: {path}")

    elapsed_values.sort()
    samples = len(elapsed_values)
    duration_seconds = max((max(finishes) - min(starts)) / 1000, 0.001)

    return {
        "file": path.name,
        "samples": samples,
        "errors": errors,
        "error_rate_percent": round(errors / samples * 100, 4),
        "throughput_rps": round(samples / duration_seconds, 2),
        "p50_ms": percentile(elapsed_values, 0.50),
        "p95_ms": percentile(elapsed_values, 0.95),
        "p99_ms": percentile(elapsed_values, 0.99),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Summarize JMeter JTL CSV files")
    parser.add_argument("files", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    rows = [summarize(path) for path in args.files]
    args.output.parent.mkdir(parents=True, exist_ok=True)

    with args.output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    print(args.output)


if __name__ == "__main__":
    main()
