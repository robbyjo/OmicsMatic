# Original rstatix 0.7.2 workflow

**The project now has a supervised original rstatix 0.7.2 script workflow.** It executes the caller's exact trusted R code in the complete unchanged original namespace, including formulas, tidy evaluation, grouping and explicit caller arguments. It preserves complete result files, source/input identities, all original conditions and final admission status. The separate native Java Levene implementation remains available; it is not substituted for other rstatix functions. This component is a development checkpoint; catalog admission is separate and no whole-tool, whole-paper or biological qualification is claimed.

## Install and run an explicit example

Use the permitted private Linux/WSL R 4.5.3 provider described in [the native/provider guide](RSTATIX072_DEVELOPMENT.md). It contains the exact original 0.7.2 archive and all 166 source files, with a fixed dependency manifest/lock. Windows original execution is skipped; use native Linux Python/R through a permitted WSL environment.

```sh
python3 tools/literature/rstatix072_workflow/make_example.py --provider build/my-ready-rstatix-provider --out build/my-new-rstatix-example
python3 tools/literature/rstatix072_workflow/run.py --request build/my-new-rstatix-example/request.json
```

Use NEW directories. The example runs seven explicitly chosen operations on 24 synthetic rows: Shapiro normality, Levene variance, Kruskal–Wallis, pairwise Wilcoxon/BH, Welch two-sample t-test, Pearson correlation and Holm adjustment. These are demonstration choices, not reconstructed author settings. The original runtime/provider is not included in the Python entry point or native Levene JAR. Analysis installs no software. If installation/download is missing or forbidden, preserve the warning and skip the dependent original analysis.

Read `analysis/analysis-status.json`, `analysis/WARNINGS.md`, `analysis/result-files.json`, `analysis/MANIFEST.json`, `analysis/diagnostics/conditions.json` and complete raw `analysis/process.stdout`/`process.stderr`. Full original R condition objects, call stacks and run state are retained as RDS; no scalar-only summary replaces them. The example's `results/complete-original-results.rds` retains all seven complete original result objects, and its index lists every returned table's dimensions.

## Declare a complete trusted-script request

The generated `request.json` demonstrates every required field. Paths resolve relative to the request file and must be canonical without symbolic-link aliases. The version and mode are literal `0.7.2` and `SCRIPT`. Declare `ALLOW` or `FORBID` independently for `execution_policy` and `trusted_code_policy`.

`script` names the exact trusted UTF-8 R file. `provider_directory` names a READY private provider. `inputs` maps portable logical filenames to complete existing files; bytes and scientific identifiers within them remain unchanged. `expected_outputs` maps every portable relative result filename to its minimum byte size. Do not name an output prefix or omit empty-but-required results. `input_provenance` and `analysis_population` must describe the actual data and entire population. The controller cannot infer scientific completeness from arbitrary code.

`required_exports` lists literal original function names. A missing name skips the dependent analysis before caller code starts. It does not repair a paper's spelling or silently substitute a related function. `required_packages` maps any additional required package to its exact version, such as a known optional dependency. A failed dependency readiness check retains its original error and skips caller analysis. The provider's complete original namespace and 339 source-defined functions are checked before code starts.

The script receives `input_directory`, `result_directory`, `input_provenance` and `analysis_population`. Write every result under `result_directory`. Set the declared literal `analysis_complete` string only after every case and result has completed. Propagate original failures and verify the scientific objects before declaring completion. The original engine evaluates the script; the adapter does not emulate R syntax or translate rstatix calls into generic Java statistics.

`limits` declares `timeout_seconds`, `maximum_files`, `maximum_workspace_bytes` and `maximum_file_bytes`. They cover the entire owned workspace, including complete inputs, result files, logs and caches. Exceeding a capacity admits no prefix or partial run. The controller retains complete available partial files and diagnostics, then requires a NEW retry with an explicit corrected capacity or input. The process runs in a fresh home/tmp directory and new Linux process group; only that owned group is terminated on failure/completion. These controls do not establish total CPU/RSS containment or supervise child processes that create separate sessions.

**Trusted code runs with the user's account permissions.** This is not an OS sandbox, a validator of arbitrary scripts or independent statistical validation. A script can catch and hide errors or return scientifically inappropriate objects; the caller must propagate failures and justify model/data choices. Structural completion, file hashes and a final marker do not prove nonfinite-value eligibility, comparison families, pairing, biological correctness or paper reproduction.

## Original output and warning semantics

All 126 namespace exports remain available through the original engine. The installed original functions and source archive are unchanged. Caller row labels, factor levels, formulas, alternative directions, pairing order, comparison families, RNG handling and missing-value policies remain explicit original R inputs. The wrapper does not infer or normalize them. Selected native Levene behavior is a separate prepared-vector mathematical API.

rstatix 0.7.2 retains its edition-specific output shape and rounding. Several tests round p-values before subsequent adjustment; the adapter preserves the full original returned object rather than recalculating p-values with generic native methods. Levene's original wrapper returns car/broom values without that wrapper rounding. The selected R 4.5.3/car 3.1-5 dependency profile differs from historical author runtimes; exact/tie behavior and dependency semantics are not proven historically equivalent.

Warning/message handlers retain complete condition objects, messages, classes, calls and call stacks without muffling the original conditions. Original `options(warn=2)` still promotes a warning to an error. Warning counts and review instructions are prominent in the final status/warnings. Original logs remain intact. A zero exit after early `quit()` without complete worker diagnostics/return is unadmitted. Missing/extra/truncated results, modified captured inputs and undeclared root outputs also refuse admission.

`MANIFEST.json` inventories complete regular files and, for failed runs with forbidden links, link identities without following their targets. It excludes itself and the final `analysis-status.json`, which is written last. Complete original objects/results remain RDS; supplementary TSVs or JSON indexes do not replace R serialization. Opaque RDS byte parity and equality of formula execution-environment pointers across independent runs are not claimed.

## Paper roles and unresolved details

The [six individual source readings](../../tools/literature/rstatix072/paper-use-intake.json) cover all 12 named paragraphs in the exact 250-primary-source census. Five papers report 0.7.2; P241 does not report an edition. Native/provider and workflow checkpoints preserve those readings without silently changing earlier main-catalog records.

- P032 names adjusted p-values for MED12 CAR activation/GSVA scores, but the test, correction method and family remain unknown.
- P040 names two-sided t-tests for IMC differential abundances. Exact design, variance assumption and pairing remain unresolved. ggpubr, GeomxTools and GraphPad roles remain separate.
- P054 names Shapiro/Levene checks, Pearson correlation, Kruskal–Wallis, pairwise Mann–Whitney tests with Bonferroni/BH adjustment, paired t-tests and one-sided group-versus-all comparisons. The literal printed `wilcoxon_test` is not exported by selected 0.7.2, whose function is `wilcox_test`; the missing literal export is skipped and no spelling repair is inferred. Required author pairing, direction, families and group-versus-all data dependence remain explicit. Reported R 4.2.3 to 4.4.0 differs from this reference.
- P087 names `wilcox_test` with BH for OLR1 CPM group comparisons in ARLD/PBC. Dataset-specific groups/families and exact/tie options remain inputs; separate stats Spearman, ggpubr, MiloR and Seurat/MAST operations are separate.
- P098 names pairwise `t_test` on VST-normalized expression with Holm adjustment. The variance assumption and comparison family remain author inputs; DESeq2 transformations and other signature tests are separate.
- P241 names generic statistical analyses and a separate Web resources link. No test, edition, design or family is inferred; the link is not an additional execution.

The workflow supplies the original engine for explicit choices; it does not recover unknown scripts/designs or reproduce these papers.

## Qualification and recovery

The complete synthetic control covers 248 original calls across all seven paper-relevant operation families, including alternatives, pairing/variance options, comparisons, reference groups, custom Levene centers, grouped operations, exact/asymptotic Wilcoxon choices and eight p-adjustment names. Against an independent direct original-R execution, every complete result object, attribute, argument, condition, caller RNG state and all 250 scientific result files matched. The returned population contains 444 rows and 5,664 table cells. Both full original RDS objects are retained; formula execution environments are compared structurally without environment-pointer/opaque-byte claims. This is original-runtime integration evidence, not independent biological validation.

**SKIP3 skips dependent analysis** for missing/forbidden source, runtime, provider, input, required original export/package, installation or execution. Supply only permitted prerequisites, resolve the named requirement, and retry NEW paths. A READY provider is not an analysis result.

**FAIL2 admits no complete result.** Retain every completed/partial result, source/input copy, original object/error/warning, manifest and status. Correct the named cause and retry a NEW output. Do not remove failed cases or call a partial run complete. Review and justify/correct every original warning before using results. The first Windows-side harness converted Linux paths to backslash paths and was skipped before analysis; that failed request and diagnostics are retained. The corrected native POSIX request reused unchanged scientific inputs and completed the full population.

The first protocol harness also incorrectly expected an existing successful status to be overwritten by a refused second request. The controller correctly returned failure and preserved the original result. That harness failure and exact helper source are retained; the corrected assertion checks that the old status stays unchanged. No original algorithm, scientific input/result or analysis-controller behavior was changed for that correction.

The original package stays GPL-2 and runs separately in R. No original GPL-2 scientific code is translated into the GPL-3 Java library. Complete original source/license and native method evidence remain in [the earlier checkpoint](rstatix072-development-checkpoint/VALIDATION.json). Hashes establish integrity, not producer authentication, binary/source reproducibility or biological correctness. The canonical executable and all unrelated unfinished files remain unchanged. The full 250-paper semantic-review and implementation objective remains active.

```sh
python3 tools/literature/rstatix072_workflow/verify.py --out build/literature-review/monocle-runtime/my-new-rstatix-workflow-check
```

This checks complete saved controls and live scientific source bytes without new fitting or installation. The unchanged earlier native/provider verifier is also replayed with exactly its eight preserved catalog inputs. Only those metadata paths may be overlaid: original code, scientific inputs/results, source profiles and verifier definitions remain live and unchanged. The earlier guide's unfinished-adapter statement describes its retained pre-workflow checkpoint; this later checkpoint records the implemented original script workflow. Catalog admission remains a separate recorded step.
