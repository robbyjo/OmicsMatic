# Prepared-data limma workflows

The global `limma-tools` command and standalone `org.jlinalg.cli.LimmaWorkflowCli` run original limma/edgeR/statmod
methods on prepared matrices. The current normal-JAR execution controls cover
six operations, with original fitted objects retained. **The module still has
DEVELOPMENT_SCOPE: full author workflows and older
author editions remain unfinished.** Source-role and catalog status are tracked
in the [current handover](HANDOVER_CURRENT.md). See the
[execution checkpoint](LIMMA_WORKFLOW_QUALIFICATION_2026-11-21.md) for exact
evidence and remaining work. The separate
[native Java variance components](LIMMA_VARIANCE.md) require no R runtime.

## Run the examples

Build current source with `./gradlew.bat executableJar` on Windows (or
`./gradlew executableJar` on Linux). The ordinary output is
`build/cli/omicsmatic-0.4.0.jar`. The previously released 0.4.0 binary predates
`limma-tools`; this change has not created a release. Use that source-built JAR
and an explicitly supplied
**R 4.6.1 + limma 3.68.5 + edgeR 4.10.7 + statmod 1.5.2** environment. All three
packages are required by the current coupled profile, including non-count
operations. Other dependencies, such as jsonlite, must be available in the
declared libraries. Runtime packages are not embedded in the JAR. Existing
private installations can be reused; downloads/private installation are allowed
where the operating environment permits. A missing permitted prerequisite is
reported explicitly as SKIP3. There is no adjacent-version substitution.

Create a UTF-8 file containing one absolute R library-directory path per line.
Include the libraries holding the named packages and their dependencies. Keep
their exact versions; do not upgrade a shared environment merely to run an
example. The worker uses these directories plus R's base library, with site
libraries and startup profiles disabled. Original source bodies/formals and
package/DLL identities are checked and recorded; this is not full host or native
binary authentication.

From the repository root in PowerShell, replace these paths with your own:

```powershell
python -B examples/limma-workflows/run_examples.py `
  --jar 'D:/path/to/omicsmatic-0.4.0.jar' `
  --java 'C:/path/to/java.exe' `
  --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' `
  --r-libraries 'D:/path/to/exact-r-libraries.txt' `
  --out 'D:/path/to/NEW-limma-examples'
```

The [example runner](../../examples/limma-workflows/run_examples.py) fills the
runtime paths in six request templates, runs each case and verifies each
successful capture in a separate Java process. `--case LM` runs just that case.
Choose **new output** on every attempt. It writes the actual requests, process
logs and `COMPLETED.json` without modifying the example inputs.

Inspect `CASE/analysis/WARNINGS.md`, `analysis-status.json`, `manifest.json`,
`worker/report.json`, `worker/warnings.txt` and process logs. Exit zero means a
completed bounded result with retained warnings, not a warning-free or
whole-paper result. Numerical, design, source and process failures are refused.

## Supported operations

| Operation | Prepared inputs and original operation |
| --- | --- |
| `LM` | Finite dense expression/log/continuous/methylation values, numeric design and contrasts; original lmFit, contrasts.fit, eBayes or TREAT. Optional observational weights and fixed/estimated subject blocking. |
| `VOOM` | Declared raw integer or estimated fractional counts; original edgeR normalization, limma voom and lmFit. Estimated blocking requires explicit one/two-pass choice. |
| `EDGER_VOOM_LMFIT` | Original **edgeR::voomLmFit**, optional blocking/sample weights, then contrasts and moderation. Its provider is edgeR. |
| `BATCH` | Original removeBatchEffect with keep-design and explicit batch1/batch2/covariates; optional observational weights. |
| `NORMALIZE` | Original normalizeBetweenArrays: none, quantile, scale or cyclicloess on the declared dense matrix. |
| `CAMERAPR` | Original cameraPR from supplied ranked statistics and exact feature-set memberships, with declared direction/ranks/correlation. |

The [six templates](../../examples/limma-workflows/README.md) show executable
schema-version1 requests. RUN requires `mode`, `profile`, `operation`, `out`,
`rscript`, `r_libraries` and operation-specific fields. Unknown or unused fields
are refused. The profile identifier is
`limma-3.68.5-edgeR-4.10.7-statmod-1.5.2-R4.6.1`.

Dense matrices use `feature_id` followed by literal sample IDs; designs use
`sample_id` followed by explicit numeric coefficient columns in the same sample
order. Contrasts supply named vectors in that coefficient order. Names are
literal UTF-8, including `NA`, numeric-looking IDs and distinct Unicode forms.
There is no trimming, case folding, normalization, intersection or deduplication.
Missing/nonfinite numeric input is refused. Raw counts must be exact integers
before floating-point conversion; fractional estimated counts require that
explicit unit declaration. No automatic log transform or methylation conversion
is applied to LM inputs.

Fitted operations require an explicit adjustment scope (`per-contrast` or
`all-contrasts`) and support BH, BY, holm, hochberg, hommel, bonferroni or none.
Moderation selects EBAYES or TREAT, source modern/legacy behavior, declared trend
and robust options; TREAT requires an explicit nonnegative log2 fold-change
threshold. Invalid combinations and silently ignored source options are refused.
Supplied library sizes and normalization factors are used literally without
recentering. Set membership does not silently discard unknown features.

There are no offset matrices, quality-weighted voom, robust regression,
duplicate-array layouts, sparse/raw-read workflows or automatic design formula
construction in this interface. A method in this table does not establish an
older author edition or cover every operation in a paper.

## Verify and replay

```powershell
java -jar 'D:/path/to/omicsmatic-0.4.0.jar' limma-tools `
  --verify 'D:/path/to/complete-analysis'
```

VERIFY requires no R installation. It checks the saved inventory and exact
packaged source, literal input identities, exported structure and selected
variance, moderated-t/TREAT probability, adjustment, normalization and batch
arithmetic. It does not independently refit models, decode RDS/journal contents,
derive all F/B outputs or authenticate coordinated replacement of evidence.
Saved-byte verification is not a scientific refit or biological validation.

For a real original-runtime REPLAY, save this JSON with absolute paths:

```json
{
  "schema_version": 1,
  "mode": "REPLAY",
  "analysis": "D:/path/to/complete-parent",
  "out": "D:/path/to/NEW-replay-output"
}
```

Run it with `java -jar JAR limma-tools --request replay.json`.
The parent must verify first. Replay reuses its declared inputs and runtime
bindings, optionally with explicit `rscript`/`r_libraries` overrides. It must
write outside the parent into new output; parent files remain unchanged.

The worker was corrected after the initial handover. Earlier captures retain
their exact earlier worker bytes and require their corresponding verifier/JAR.
Use commit `f4cb923d` or its preserved `build/lm-3` owner for that historical
packet. Never rewrite old source hashes to make an old capture pass a new worker.

## Warnings and recovery

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Read the named missing exact runtime,
package, source or input. Restore the permitted prerequisite and retry into
**new output**, or retain the skip if it cannot be supplied. An unknown profile
can skip before an output directory exists; read stderr in that case. Missing
installation is not assumed to mean installation is forbidden.

**FAIL2: NO COMPLETE RESULT.** Preserve original inputs, fitted/null/partial
objects, warnings, logs and failed attempts. Correct the named invalid data,
design, source/integrity, numerical/convergence, capacity or process cause and
retry into **new output**. Failed/skipped captures cannot be admitted by VERIFY.
No silent identity/count/edition/threshold/population/estimator repair or generic
fallback occurs. Valid infinite prior df, source zero-df NA sigma and valid
negative unconstrained original components remain retained.

The adapter observes internal gamma fits through a read-only on.exit prefix,
with an exact AST inverse preserving the original statistical body. Incomplete
observer state or a journal failure cannot replace an original result/error,
and still forces the enclosing workflow to refuse completion. Journal failures
retain separate fallback object snapshots when storage permits. Journal close,
model-bundle and RNG finalization errors are reported separately while preserving
an existing original error. The exact
original convergence criterion is checked before treating iteration exhaustion
as failure. Full model objects, immutable snapshots and a binary internal-model
journal are retained, including valid/partial/failed states.

Resource limits cover logical dimensions/work, input/output bytes and elapsed
time, with owned-process cleanup. They are monitored limits, not OS-enforced
memory or disk quotas. SHA-256 establishes saved-byte integrity, not a signature.
