# Typed original Seurat differential-analysis development

`org.jlinalg.cli.SeuratDifferentialCli` runs original current Seurat Wilcoxon or logistic-regression marker tests from a closed JSON request. It captures original inputs, explicit layer preparation, guarded source, runtime/process receipts, original result objects and public tables. Native Java independently audits preparation, fold summaries, detection fractions, feature selection, full-assay Bonferroni correction and result ordering. The [four copyable examples](../../examples/seurat-differential/README.md) provide the starting point.

**This is a standalone development interface.** Signac remains `missing` and Seurat remains `method-overlap` in the 250-paper catalog. The count remains **70 new scoped integrations, 73 including existing adapters, and 258 missing named tools**. Historical marker-summary profiles are separate from this current statistical runtime. Broader wrappers, plotting, paper-role review and public integration remain unfinished. Cell Ranger stays excluded.

## Warnings and required actions

| Condition | Required behavior and recovery |
| --- | --- |
| **SKIP3: requirement unavailable or forbidden** | Skip the dependent analysis. Read the named missing Rscript, private library or package in `WARNINGS.md` and `analysis-status.json`. Where permitted, provide the exact runtime below in private paths, update the request and retry into a **new** output directory. If installation/execution is forbidden, keep it skipped. Analysis never installs software. |
| **FAIL2: no result admitted** | Invalid input, incompatible source/version, original model failure, exhausted limits, timeout or failed publication verification admits no result. Retain all partial files, correct the named cause and retry into new paths. Do not interpret a retained table as an admitted result. |
| **PARTIAL SOURCE RESULT** | Original `FindAllMarkers` can catch a failed group and still return other rows. Its `only.pos` branch can emit no warning about the omitted group. The adapter observes original calls, retains each failure, prints a partial-result banner first and sets `complete_comparison_set: false`. Read every `SOURCE_COMPARISON_NOT_TESTED` entry; absence for an untested group is not a negative result. Correct group sizes/design and rerun. |
| Original logistic-regression warnings | Convergence and fitted-probability warnings remain in the worker report and `WARNINGS.md`. Inspect design, covariate distribution and biological replication. A successful return does not establish model adequacy. |
| Optional Presto installation message | This admitted base-R profile intentionally excludes Presto and limma. They are not missing requirements. Installing them can change the backend; use a separately validated profile before doing so. Do not modify frozen/shared runtime libraries. |
| **VERIFY scope** | It checks consistency, source bindings and native surrounding arithmetic. It does **not** refit Wilcoxon/LR p values, independently replay source sampling, authenticate execution/DLLs, validate biology or prove a full paper was reproduced. A coordinated probability forgery is demonstrably accepted after updating all consumed copies and hashes. |
| Completion file | `COMPLETED.json` finalizes the generic capture transaction. Also require `results_admitted: true`, a successful VERIFY and the needed comparison completeness in `analysis-status.json`. Failed/skipped captures can be finalized for retention and still refuse VERIFY. |

## Runtime and invocation

The explicit profile is `Seurat-5.6.0-R4.6.1-base`, with **R 4.6.1** and these loaded package versions:

| Package | Version |
| --- | --- |
| Seurat | 5.6.0 |
| SeuratObject | 5.4.0 |
| Matrix | 1.7.5 |
| jsonlite | 2.0.0 |
| future | 1.80.0 |
| future.apply | 1.21.0 |
| lmtest | 0.9.40 |

R's canonical `packageVersion` text for lmtest is `0.9.40`; its package DESCRIPTION uses `0.9-40`. The profile uses the former. Presto and limma must be absent from the declared library search path. Additional transitive dependencies must load successfully. The worker searches only declared `r_libraries` plus R's system `.Library`; it excludes user/site library defaults, clears R startup environment overrides, uses a C locale and fixes thread settings to one.

The existing permitted private Windows runtime was used for validation. A fresh clone does not include those libraries or a new general-purpose installer. Supply a separately prepared permitted exact runtime; do not assume repository setup histories are a portable installation kit. Do not substitute a newer edition to make a failed request run. Missing requirements remain SKIP3. No installation was needed for this milestone, and all 27,958 previously inventoried R-library files remained unchanged.

Build and run the separate development JAR using the commands in the example README. Supported entry points are `--request UTF8_JSON` and `--help`. The JSON mode is `PAIR`, `ALL` or `VERIFY`. All relative paths resolve against the request's directory. R fitting was tested on Windows Java 21/25. Java-only verification and packaged contracts also passed on Linux Java 21; this checkpoint does not establish Linux R fitting.

## Inputs and scientific choices

Counts use plain UTF-8 **Matrix Market coordinate integer general**, with one-based row/column indices and nonnegative integer counts. Features are rows; cells are columns. `features` and `cells` are separate ordered ID files, one exact ID per line and no header. Duplicate count coordinates are combined by exact integer sums with an explicit warning and the original bytes retained. Count totals must remain exactly representable in the permitted integer domain; estimates are never rounded into counts.

Prepared `data` and optional `scale_data` use coordinate real general matrices with the same complete dimensions and ordered IDs. Stored values must be finite decimal/scientific numbers; duplicate coordinates and Java-only hexadecimal numeric tokens are refused. Negative finite prepared values are retained, not repaired. Derived undefined source arithmetic is not clipped or imputed; a result outside the admitted identity/summary domain fails explicitly.

Metadata is unquoted tab-separated UTF-8. Its first header must be `cell`, followed by a declared `group_column` and any covariates. Rows and exact cell IDs must match count columns in order; no sorting, dropping, imputation or name repair occurs. Every declared group must be observed. Numeric covariates require finite values; factor covariates require an explicit ordered `levels` array. Literal Unicode identities and metadata names are retained in the mapping; only explicit ASCII f#/c#/g#/v#/l# transport codes cross R name handling. Public identities are restored exactly.

`PAIR` requires a `contrast` with a `numerator` array and optional disjoint `denominator`; an omitted denominator means all other declared groups. `ALL` compares every ordered group against the rest and refuses a pair contrast. Cell-level models do not infer donors, pseudobulk samples or independent biological replicates. Supplying a depth covariate does not solve all biological-design concerns.

| Setting | Meaning and default |
| --- | --- |
| `data_source` | `native_tfidf` (default), `provided` with a `data` file, or `counts`. Unused data/TF-IDF options are refused. |
| `tfidf_method`, `tfidf_scale` | Native Signac method 1–4 and scale (default 1 and 10000), only for native TF-IDF. Normalization uses the full captured count population before feature/cell test selection. |
| `test` | `wilcox` (default; base-R backend) or `LR`. Only LR accepts the typed `covariates` array; unused Wilcoxon covariates are refused. |
| `test_slot`, `fc_slot` | Independently select `counts`, `data` (default) or `scale.data`; the latter requires `scale_data`. The public effect is generic `fold_change`; source scale-data means are arithmetic differences (`avg_diff`). |
| `fc_normalization` | `unrecorded` (default), `LogNormalize` or `other`. ALL only accepts `unrecorded` because the prepared object's normalization history is empty. |
| `pseudocount`, `log_base` | Defaults 1 and 2. v5 adds the pseudocount to each group **sum before division by size**. |
| `test_features` | Optional ordered exact known feature-ID file. It restricts testing without shrinking normalization or the full-assay correction family. |
| `min_pct`, `min_diff_pct`, `logfc_threshold` | Defaults .01, string `"-Inf"` and .1. Detection is `value > 0`, rounded using source R behavior before filtering. |
| `only_positive` | Default false; applies the original pre/post-test positive-effect rules. Undefined identity outcomes are refused rather than silently dropped or renamed. |
| `max_cells_per_group`, `min_cells_group`, `seed` | Defaults string `"Inf"`, 3 and 1. Feature summaries/selection use full groups before original downsampling. |
| `return_threshold` | ALL only, default .01. The source keeps rows with **raw p strictly below this threshold**; it is not an adjusted-p threshold. |
| `densify`, `save_source_objects` | Defaults false and true. Explicit dense capacity is required when densifying or transporting scale data. |

Two source dispatch details are retained. First, current `FindMarkers.Assay` consumes the testing slot but does not forward it to the matrix method, whose feature-filter slot remains `data`. Second, the v5 data-slot fold mean applies `expm1` even to explicitly supplied TF-IDF values when normalization is unrecorded. The adapter warns about these choices instead of substituting another formula. Choose layer units/provenance deliberately.

Correction uses Bonferroni over **every original assay feature**, including features excluded from the test. Source p values then order the rows, with source detection-difference tie breaking. Missing/nonfinite output kinds remain explicit as `NA`, `NaN`, `Inf` and `-Inf`. The finite statistical controls contain no nonfinite output token; the separate original formatter probe covers those transport kinds.

## Capacity, evidence and verification

Required runtime declarations are `rscript` and a nonempty `r_libraries` array. `timeout_seconds` defaults to 3600 (1–86400). `maximum_input_bytes` defaults to 512,000,000 across captured inputs; `maximum_output_bytes` defaults to 2,000,000,000 of additional worker-file bytes. That worker-file limit is **not a process RSS limit**. The JVM heap, source library allocations and host resources still matter.

Input limits default to 2,000,000 features, 2,000,000 cells and 10,000,000 stored entries (maximum supported entries 20,000,000). `maximum_dense_entries` defaults to 10,000,000; `maximum_work` to 500,000,000; `maximum_result_rows` to 2,000,000 across raw group tables and the final table. Preparation checks complete contrast work and bounded protocol sizes before source fitting. Timeouts request cleanup of the owned R process tree; this does not authorize terminating unrelated jobs.

Inspect `source-request.json`, captured `inputs/`, normalized `request.json` / `effective-request.json`, `prepared-inputs.json`, retained `source/`, worker settings/layers, raw group and final tables, source RDS objects when enabled, versions/session info, RNG states, process receipts, `diagnostics.json` and `WARNINGS.md`. The source-rule sampled-cell preview checks membership/counts and retains the guarded rule's result; it does not independently prove which random cells the statistical function internally used.

The original source guard compares the bodies/formals of all **42 top-level functions defined in the retained official differential_expression.R file**. It does not authenticate the entire namespace, generic dispatch, dependencies, native DLLs or host. ALL uses a private observation binding around original FindMarkers calls while leaving the original FindAllMarkers body/formals unchanged. This records caught failures without simulating a test.

VERIFY requires no R runtime. It checks file populations/hashes, request/input/source/process bindings, prepared layers, complete group summaries, selected feature populations, corrections, ordering, published values, comparison-completeness flags and required warning text. Fourteen resealed corruption controls are refused. A test-only coordinated forgery halves one source p value, updates its correction plus all consumed table copies and reseals the files; VERIFY accepts it on three JVMs. The unchanged forensic RDS/source table is retained but not independently reconciled by VERIFY. **Hash consistency does not authenticate execution or statistical truth.**

## Validation checkpoint

The current development JAR passes 36 normal Gradle contract tests and the same 36 tests against the actual JAR on each of three JVMs. Its parent JAR entries are unchanged. The base matrix contains 12 real original requests, 36 portable verifications and 7,030 finite field comparisons at relative error 0. The extended matrix contains 20 real requests: 18 returned tables, including two explicitly partial comparison sets, and two expected original model failures. It adds 54 successful portable verifications, six failed-capture refusals and 8,240 finite comparisons at maximum relative error 4.160166989411576e-15. Original R warning counts agree.

Extended controls cover seeded sampling, LR covariates, count/scale-data effects, ordered selected features, raw-p return filtering, an undersized ALL group, a constant-factor LR failure, native TF-IDF and counts-as-data preparation. Eight real recovery controls cover missing R/library/package, dense/work/row/output limits and an actual worker timeout; failed/skipped captures refuse VERIFY and existing outputs remain unchanged. The timeout observer retained Windows handles and observed all four owned descendants exited. Listed-function body/formal mutations are refused before fitting. Two earlier negative-test fixture mistakes are retained with their explanations.

The [saved checkpoint](seurat-differential-development/VALIDATION.json) and [checkpoint warnings](seurat-differential-development/WARNINGS.md) record exact source/archive/artifact pins and example checks. The earlier artifact/version-notation attempts and superseded controls remain retained. This checkpoint grants no root-command/catalog credit, historical whole-package reproduction, Linux R fitting, biological validation, full-project test-suite claim, release, push, deployment or completion of the broader goal.
