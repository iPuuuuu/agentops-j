# Benchmark Plan

No performance claims are published until measurements are reproducible.

## Planned experiments

1. Inject 5%, 10%, and 20% primary-provider failures; compare request success rate with fallback disabled and enabled.
2. Re-send the same tool request concurrently; verify exactly one execution and remaining callers receive a replay.
3. Run 10, 50, and 100 concurrent requests; record throughput, p50, p95, p99, error rate, and fallback rate.
4. Compare model and prompt versions against a fixed evaluation dataset.
5. Run the offline control-plane evaluator against `eval/dataset.jsonl` and archive its JSON output with the commit.

## Offline evaluation artifact

The repository contains 100 public synthetic JSONL samples and a standard-library-only Python evaluator. Run:

```bash
python3 eval/evaluate.py
```

See [evaluation.md](evaluation.md) for the input schema, output metrics, deterministic simulation schedule, illustrative pricing, and limitations. The evaluator is designed for offline regression and does not make real model-quality or production-latency claims.

## Required reporting

- Machine and JDK version
- Exact command and configuration
- Raw results
- Median of at least three runs
- Known limitations
