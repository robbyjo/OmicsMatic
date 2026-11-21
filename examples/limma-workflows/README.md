# Six prepared-data limma examples

Read the [workflow guide](../../docs/literature/LIMMA_WORKFLOWS.md) first.
These are synthetic prepared matrices with literal identifiers, not paper data.
The original R profile is external and explicitly supplied; templates contain
path placeholders that the runner replaces.

Run all six through [run_examples.py](run_examples.py), or select `--case`:

| Case | Template |
| --- | --- |
| LM expression model | [LM](LM/request-template.json) |
| Count voom model | [VOOM](VOOM/request-template.json) |
| edgeR voomLmFit | [EDGER_VOOM_LMFIT](EDGER_VOOM_LMFIT/request-template.json) |
| Batch removal | [BATCH](BATCH/request-template.json) |
| Matrix normalization | [NORMALIZE](NORMALIZE/request-template.json) |
| Pre-ranked camera | [CAMERAPR](CAMERAPR/request-template.json) |

Each folder contains its complete input TSV files. All six public examples were
actually executed using the normal JAR and the exact Windows original-R profile;
their exported files match the independent-original control exports exactly.
This does not establish original-R execution on Linux or reproduce a paper.

Read the printed warning/status paths after every run. Exit3 means a named
dependency was skipped; exit2 means no complete result. Keep every attempt and
retry into new output. Global CLI registration and catalog admission remain open.
