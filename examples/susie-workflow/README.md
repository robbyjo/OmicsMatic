# Original SuSiE: runnable synthetic example

This example creates 60 samples and eight continuous predictors. It is a
software check, not biological fine-mapping. Use Python 3.11+ to prepare it,
Java 17+ to run the current source-built JAR, and the exact R 4.6.1/private
package profile described in the [workflow guide](../../docs/vignettes/susie-workflow.md).
Published 0.4.0 predates this new entry point.

From the repository root in PowerShell, with NEW output paths:

```powershell
python -B examples/susie-workflow/prepare_example.py --out build/susie-example-NEW --profile SuSiE-0.14.2-R4.6.1 --model RSS_Z --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --library 'build/susie-high-impact-1/runtime-install-1/0.14.2/library' --library 'build/susie-high-impact-1/runtime-profile-1/library' --library 'build/coloc-high-impact-1/runtime-coloc603-1/library'
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SusieWorkflowCli --request build/susie-example-NEW/request.json
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SusieWorkflowCli --verify build/susie-example-NEW/analysis
```

The three private-library paths above describe the retained development
machine. On another machine, provide NEW/private libraries containing the
exact listed packages; the guide explains requirements and installation.
Preparation writes no analysis result and installs nothing. RUN never
installs, changes package versions or falls back to the native `susie` command.

To exercise other inputs, prepare a separate NEW folder with `--model
INDIVIDUAL`, `SUFFICIENT` or `RSS_BETA_SE`. With profile
`SuSiE-0.11.92-R4.6.1`, use `SUFFICIENT_BETA_SE` for beta/SE data. The legacy
`RSS_Z` source interface has no sample-size argument; the preparer records
that explicit distinction. It does not translate it into the modern model.

Successful output is under `analysis/`. Read `WARNINGS.md` and
`analysis-status.json` first, then `worker/variant-results.tsv`, credible-set
tables and `worker/report.json`. `worker/original-fit.rds` is the unmodified
original model. VERIFY needs no R but checks structure/integrity rather than
independently refitting it. REPLAY requires the exact permitted runtime.

**SKIP3:** restore the named permitted missing input/runtime/package or keep
the dependent analysis skipped. **FAIL2:** no complete result; retain inputs,
original objects, partials and diagnostics, correct the named cause and retry
NEW output. Nonconverged models are retained and refused. No ID, allele,
threshold, population, nonfinite or model repair is performed.
