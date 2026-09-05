# Offline evaluation

The repository includes a public, synthetic,脱敏 dataset at `eval/dataset.jsonl` with 100 samples across classification, extraction, summarization, and tool tasks. It contains no API keys, customer text, production traces, or personal data.

## Run

Requirements: Python 3.9+ standard library only. No network, service, model, or third-party package is required.

```bash
python3 eval/evaluate.py
# or use another JSONL file with at least 100 rows:
python3 eval/evaluate.py --dataset eval/dataset.jsonl
```

The script validates the JSONL schema (`id`, `category`, `prompt`, `expected`) and prints deterministic JSON metrics:

- `success_rate`: simulated request success rate
- `fallback_rate`: simulated provider fallback rate
- `latency_ms.p50/p95/p99`: deterministic latency percentiles
- `tokens.input/output/total`: whitespace-token approximation
- `estimated_cost_usd`: transparent illustrative input/output rate calculation
- `tool.success_rate`: success rate for rows in the `tool` category

This is a control-plane regression harness, not a claim about a real model's answer quality or production latency. The deterministic schedule makes CI and offline reproduction possible. For real provider measurements, replace the simulation observation loop with an adapter that records the same fields and keep the dataset unchanged.

## Reproducibility and privacy

The dataset is intentionally small and synthetic. Run from the repository root with the command above. The cost rates are constants in `eval/evaluate.py` and are illustrative only; update them to the configured provider's published pricing before using the output for planning. No external network calls are made.
