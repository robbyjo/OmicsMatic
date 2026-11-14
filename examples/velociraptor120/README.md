# Complete velociraptor synthetic examples

Read [setup, requests, warnings and recovery](../../docs/literature/VELOCIRAPTOR120_WORKFLOWS.md)
before running. Use the exact permitted Linux/WSL runtime; missing or forbidden
prerequisites skip dependent analysis (**SKIP3**). **FAIL2** admits no complete result.
The original R namespace's native stack diagnostic remains unresolved and always
requires raw-console review.

`prepare.py` verifies `FILES.json` and every member of `synthetic-inputs.zip`, then
creates six requests in a NEW directory. It downloads, installs and executes nothing.
The archive retains original generator files, full RDS inputs, caller parameters,
the full saved synthetic fit and three complete original plot calls. All 160 cells
and 12 genes, literal `NA`, Greek identifiers and their order are retained.

The examples are:

| Request | Complete original operation |
| --- | --- |
| `stochastic-request.json` | RNA velocity through the original ANY-list R dispatch |
| `dynamical-request.json` | Dynamical/latent-time workflow through SingleCellExperiment dispatch |
| `project-grid-request.json` | Original velocity projection plus all grid intermediates |
| `plotVelocity-all-panels-request.json` | Complete six-panel plot over the selected two genes |
| `plotVelocityStream-plain-request.json` | Complete original PCA streamline figure |
| `plotVelocityStream-colored-request.json` | Complete original PCA colored streamline figure |

Read extracted `inputs/PROVENANCE.json` for each byte-exact source, the original
simulation parameters and the separately selected execution profile. This is not
author data or recovered author settings. A 15-iteration fit is not evidence of
convergence. The full graph RDS and complete SVG/PNG renderings are retained;
opaque RDS byte identity and graph environment/pointer identity are not claimed.

Check exit code, `analysis-status.json`, `WARNINGS.md` and `MANIFEST.json` before
using a result. If final status recording fails, preserve the full console and
`FINALIZATION_ERROR.json` where available; repair the named filesystem condition
and rerun into a NEW path. Never infer success from a figure or RDS left by a failed
attempt. No identifier, nonfinite value, parameter or coordinate label is repaired
automatically. Earlier failures remain retained.
