#!/usr/bin/env python3
"""Offline, dependency-free AgentOps-J evaluation harness.

The dataset contains public, synthetic prompts only. This script runs a
repeatable control-plane simulation so it can be used without API keys or a
running server. It reports operational metrics, not model-quality claims.
"""
from __future__ import annotations

import argparse
import json
import math
import statistics
from pathlib import Path

INPUT_USD_PER_TOKEN = 0.00000015
OUTPUT_USD_PER_TOKEN = 0.00000060


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = (len(ordered) - 1) * p / 100.0
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower)


def token_count(text: str) -> int:
    # A deterministic, vendor-neutral approximation for offline benchmarking.
    return max(1, len(text.split()))


def load_dataset(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"invalid JSONL at line {line_number}: {exc}")
            for field in ("id", "category", "prompt", "expected"):
                if not isinstance(row.get(field), str) or not row[field].strip():
                    raise SystemExit(f"line {line_number}: missing non-empty {field}")
            rows.append(row)
    if len(rows) < 100:
        raise SystemExit(f"dataset must contain at least 100 samples, found {len(rows)}")
    return rows


def evaluate(rows: list[dict]) -> dict:
    observations = []
    for index, row in enumerate(rows, 1):
        input_tokens = token_count(row["prompt"])
        output_tokens = token_count(row["expected"])
        # Stable fault/latency schedule: useful for regression tests without
        # pretending to measure a real provider.
        fallback = index % 7 == 0
        success = index % 29 != 0
        tool_called = row["category"] == "tool"
        tool_success = tool_called and index % 19 != 0
        latency_ms = 80 + ((index * 37) % 220) + (35 if fallback else 0)
        observations.append({
            "id": row["id"], "success": success, "fallback": fallback,
            "latency_ms": latency_ms, "input_tokens": input_tokens,
            "output_tokens": output_tokens, "tool_called": tool_called,
            "tool_success": tool_success,
        })

    total = len(observations)
    successful = sum(item["success"] for item in observations)
    fallback_count = sum(item["fallback"] for item in observations)
    tool_calls = sum(item["tool_called"] for item in observations)
    tool_successes = sum(item["tool_success"] for item in observations)
    input_tokens = sum(item["input_tokens"] for item in observations)
    output_tokens = sum(item["output_tokens"] for item in observations)
    latency = [item["latency_ms"] for item in observations]
    return {
        "dataset_samples": total,
        "success_rate": successful / total,
        "fallback_rate": fallback_count / total,
        "latency_ms": {"p50": percentile(latency, 50), "p95": percentile(latency, 95), "p99": percentile(latency, 99)},
        "tokens": {"input": input_tokens, "output": output_tokens, "total": input_tokens + output_tokens},
        "estimated_cost_usd": input_tokens * INPUT_USD_PER_TOKEN + output_tokens * OUTPUT_USD_PER_TOKEN,
        "tool": {"calls": tool_calls, "successes": tool_successes,
                 "success_rate": tool_successes / tool_calls if tool_calls else 0.0},
        "simulation": {"name": "deterministic-control-plane-v1", "input_rate_usd_per_1m": INPUT_USD_PER_TOKEN * 1_000_000,
                       "output_rate_usd_per_1m": OUTPUT_USD_PER_TOKEN * 1_000_000},
    }


def print_report(report: dict) -> None:
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the offline AgentOps-J evaluation")
    parser.add_argument("--dataset", default="eval/dataset.jsonl", type=Path)
    args = parser.parse_args()
    print_report(evaluate(load_dataset(args.dataset)))


if __name__ == "__main__":
    main()
