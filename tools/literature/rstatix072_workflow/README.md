# Original rstatix 0.7.2 script workflow

Run complete original rstatix calls through a trusted UTF-8 R script. The workflow uses the unchanged 0.7.2 namespace and locked private Linux/WSL R 4.5.3 provider from `../rstatix072/provider.py`. It checks all 126 exports and 339 source-defined functions before caller code starts. No package is installed by analysis.

```sh
python3 tools/literature/rstatix072_workflow/make_example.py --provider build/my-ready-rstatix-provider --out build/my-new-rstatix-example
python3 tools/literature/rstatix072_workflow/run.py --request build/my-new-rstatix-example/request.json
```

The generated request, script and 24 synthetic input rows are explicit examples, not recovered author data or settings. `input_directory` and `result_directory` point to owned captured files. The original engine handles R formulas, tidy evaluation, grouping and caller parameters. The script must write every result under `result_directory` and set the exact declared `analysis_complete` string only after the full population completes. Declare every expected result and its minimum byte size. A standalone source checkout or Java Levene JAR is not an original R runtime.

**SKIP3 skips dependent analysis** for missing/forbidden source, runtime, input, required export/package, installation or execution. Install only permitted prerequisites using [the provider instructions](../../../docs/literature/RSTATIX072_DEVELOPMENT.md). Required exports are literal: the paper's `wilcoxon_test` is not silently repaired to the package's `wilcox_test`. Retry with NEW paths after supplying or resolving the prerequisite.

**FAIL2 admits no complete result.** Preserve every original returned object, warning, error, input, complete/partial output, diagnostic and status. Correct the named cause and retry a NEW path. Do not drop failed cases or label a partial population complete. Read `WARNINGS.md`, `analysis-status.json`, the complete `MANIFEST.json`, `diagnostics/conditions.json`, full condition/run RDS objects and raw `process.stdout`/`process.stderr`. Warnings are counted prominently and are not muffled; `options(warn=2)` still turns warnings into errors. Exit zero without final diagnostics/marker or all declared results is unadmitted. Review and justify/correct every warning before using a returned object.

The controller verifies input/source/provider fingerprints before and after execution, preserves literal bytes, rejects symlinks and extra/missing result files, enforces declared workspace capacities and supervises its own new Linux process group. Caller code remains trusted account-level R code: this is not an OS sandbox, independent scientific validator or total CPU/RSS containment guarantee. Child processes that create separate sessions are outside that process-group supervision scope. Scripts must propagate scientific errors and check their own expected scientific results; generic file completion cannot infer the validity of arbitrary R objects, nonfinite values, designs or comparison families.

The independent native one-way Levene API remains separate. It does not supply the other original tests, arbitrary R center functions, formulas or grouped workflows. Original rstatix 0.7.2 retains its own p-value rounding and adjustment behavior. The modern R 4.5.3 dependency profile does not prove historical author-R equivalence or paper reproduction. Hashes establish integrity, not producer authentication or biological validity.

Read [the full workflow guide](../../../docs/literature/RSTATIX072_WORKFLOW_DEVELOPMENT.md) for source roles, request fields, scientific controls and recovery. The full 250-paper objective remains active.
