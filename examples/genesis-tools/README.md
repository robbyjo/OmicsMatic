# Complete synthetic GENESIS requests

Use [the integration guide](../../docs/literature/GENESIS_INTEGRATION.md) for exact prerequisites, copyable setup/run commands and prominent warnings. The source templates are complete JSON; `prepare_genesis_examples.py` copies them into a NEW workspace with all eleven byte-preserved synthetic input files and the caller's unchanged runtime profile. It performs no analysis.

The fixture has 64 synthetic participants, 512 autosomal biallelic variants, literal FID/IID pairs and explicit missing calls. Phenotype order deliberately reverses genotype order. Supplied PC-Relate PCs are literal synthetic inputs, not exported PC-AiR results. `manifest.json` records every source/input hash and the single deliberate one-component negative-control change. There are no participant data, biological findings or paper reconstructions.

| Request | Expected result with the full exact optional runtime |
|---|---|
| `pcair-10.json`, `pcair-20.json` | Original PC-AiR with ten/twenty components |
| `pcrelate.json` | PC-Relate with supplied PCs and explicit covariance settings |
| `null-gaussian.json` | Quantitative model with age/PRS and covariance |
| `null-binomial-mixed.json` | Positive-variance synthetic binary mixed model |
| `null-two-stage-group.json` | Gaussian within-group two-stage transform |
| `association-gaussian.json` | Quantitative single-variant Score |
| `association-binomial-score.json` | Binary mixed-model Score |
| `association-binomial-spa.json` | Binary mixed-model SPA; SPAtest 3.1.2 required |
| `association-binomial-binomirare.json` | Binary mixed-model BinomiRare; poibin 1.6 required |
| `association-gaussian-gxe.json` | One-covariate PRS interaction Wald tests |
| `known-cmp-refusal.json` | **FAIL2; no result.** Original true mixed CMP carrier-column error |
| `known-pcair-vector-refusal.json` | **FAIL2; no result.** Original projected one-component dimensions/identities are not repaired |

Unavailable or forbidden exact dependencies instead require **SKIP3** and skipped dependent analysis. Expected refusal recipes are diagnostics, never findings. Require exit0, `result_admitted=true` and successful complete `--verify` for every usable result. Preserve all output directories; use NEW paths for another attempt.
