# Cicero supplied-prediction reuse: native Java development workflow

This workflow subsets supplied predictions using explicitly declared accessible intervals for each cell type. It preserves every selected original row byte for byte, including identifiers, scores, extra columns and line terminators. It uses no gene-expression or detected-gene rule and runs on Java without R or an upstream installation.

The primary Methods paragraph for P153 ([DOI](https://doi.org/10.1038/s41588-026-02695-8), PMC13447104, paragraph54, SHA-256 `7127ad8496e4253a67c941a3959d37f382490c354b00b13ed0ba61e663d1cf88`) distinguishes Cicero's accessibility-only prediction subset from the detected-gene filters used for other predictors. It does not establish a new Cicero fitting run. Prediction release, coordinate conventions, overlap thresholds and exact endpoint filtering remain unspecified. The caller declares those choices here; they are not attributed to the paper.

**Admission covers membership only.** Original `NA`, `NaN`, `Inf`, scores, target IDs and other payload strings remain unchanged. This operation does not validate their statistical meaning, predictor provenance, calibration or biology. No score threshold, target annotation, genome alias, gene-expression rule or silent identifier repair is introduced. An empty subset is a filtering disposition, not evidence of absent interactions.

## Build and run

Use an installed Python and JDK21 from the repository root:

```powershell
python tools/literature/build_cicero_prediction.py --out build/literature-review/monocle-runtime/my-cicero-prediction-build
$jar = 'build/literature-review/monocle-runtime/my-cicero-prediction-build/omicsmatic-0.3.7-cicero-prediction-development.jar'
java -Xmx256m -cp $jar org.jlinalg.cli.CiceroPredictionCli --request examples/cicero-prediction-subset/request.json
```

The builder requires the previously validated `cicero-workflow-build-8` development parent. It adds only the new Java classes, preserves every parent entry and leaves the canonical executable unchanged. Reconstruct the documented validated parents before building on another checkout. Runtime libraries and development JARs remain local; public evidence records exact entry identities. No root CLI route or release is added by this checkpoint.

The example retains p1 for type-A and p2/p3 for type-B. `empty-type` retains only the header. A touching half-open interval does not overlap source3. Target expression is irrelevant to the declared source-only policy, even when an opaque target ID has no interval metadata. Read `analysis-status.json`, `WARNINGS.md`, `membership.tsv`, `counts.tsv` and `provenance.json`. Require exit0 and `result_admitted=true`.

## Explicit contract

Use `--help` for all fields. The closed request requires schema_version1, mode `SUBSET`, predictions, peak_metadata, accessible_peaks, a **new** output path, coordinate_system, accessibility_scope, ordered unique cell_types and positive minimum_overlap_bp. Paths resolve against the request file. Cell-type IDs stay literal; ordinal output filenames avoid treating IDs as paths.

| File | Header and interpretation |
| --- | --- |
| Predictions | First columns `prediction_id`, `source_peak_id`, `target_id`, followed by any unique original columns. IDs are literal and prediction IDs unique. Scores and extra columns are opaque payload. Input order remains output order. |
| Peak metadata | Exactly `peak_id`, `chromosome`, `start`, `end`. Unique peak IDs. Each prediction source must have an interval. Target metadata is required only for both-endpoints scope. Unused declared metadata is allowed. |
| Accessible peaks | Exactly `cell_type`, `peak_id`, `chromosome`, `start`, `end`. Every cell type must be declared; each peak ID is unique within its cell type. Different peak IDs can have identical coordinates. A declared cell type can have no accessible intervals. |

Inputs are unquoted literal UTF-8 TSV. Quotes and whitespace remain characters; no trimming occurs. BOM, NUL, blank rows, bare CR, mixed line endings, duplicate headers, mismatched row widths, duplicate IDs and malformed intervals are refused. LF or CRLF is preserved in prediction outputs; the last input row can lack a terminal newline. `membership.tsv` is literal unquoted UTF-8 TSV with LF. `counts.tsv` follows the project's quoted-field TSV writer so quoted cell-type IDs remain representable.

Choose `one-based-closed` or `zero-based-half-open` explicitly. The former requires start≥1 and end≥start; the latter start≥0 and end>start. Internal overlap arithmetic normalizes to half-open coordinates, without changing captured inputs or prediction rows. Endpoints can use the full nonnegative signed64-bit domain. Chromosome names compare literally: `chr1` and `1` are distinct. No liftover, strand inference, aliases or adjacency expansion occurs.

The source interval is accessible when at least one declared peak on the same chromosome overlaps by at least minimum_overlap_bp bases. `source-only` tests only the source. `both-endpoints` also requires the declared target interval to qualify. This second policy is an explicit option, not an inferred P153 setting. The ledger records every prediction/cell-type decision, every endpoint's matching-peak count and its first qualifying witness in **accessible input order**. Source-only target fields say `not-tested`. Predictions are neither multiplied by overlap count nor reordered.

## Errors, capacities and evidence

**FAIL2: no result of a failed batch is admitted.** The entire cell-type family is atomic. A late failure moves even completed per-cell-type outputs to `unadmitted-partial-results`; do not interpret them. Invalid request syntax can fail before an output directory is created; stderr still gives recovery instructions. Otherwise preserve WARNINGS.md, status, captured inputs and partial ledgers. Correct the named cause and retry NEW paths. Do not drop rows, rename identifiers or change original scores to force success.

No external software is needed, so this native workflow has no installation skip. The separate [Cicero fitting adapter](CICERO_COACCESSIBILITY_DEVELOPMENT.md) still requires its exact permitted Windows/R profile and reports SKIP3 when unavailable. Subsetting supplied predictions does not fit or regenerate them.

Default capacities: 128MiB total inputs, one million rows per input, ten million prediction/cell-type decisions, 100million interval-query operations and 1GiB across generated membership/prediction tables. Parser maxima are 512MiB inputs, five million rows per input, 100million decisions, ten billion operations and 128GiB generated tables. Up to128 declared cell types are accepted. Captured inputs, counts, provenance, warnings and manifests require additional storage. Logical bounds are not OS quotas; use a permitted Java heap/storage size. Index construction is bounded by input rows; query work and generated table bytes have separate limits.

The native interval tree is checked against independent brute force, including strict boundaries, overlap lengths, literal chromosome names, duplicate coordinates, deterministic witnesses and signed64-bit endpoints. Actual JAR controls on Windows Java21/25 and Linux Java21 compare every membership row and selected output byte, retain nonfinite payload strings, test empty groups and reject malformed/capacity inputs. A late operation-budget failure preserves two completed earlier subsets but admits none.

The [sealed controls](cicero-prediction-development/VALIDATION.json) bind exact sources, parent/candidate entries, complete successful and failed outputs, an executed example and primary-source pointers. Verify without running an analysis or installing dependencies:

```powershell
python tools/literature/cicero_prediction_evidence.py
```

## Related source discovery still open

The verified scan of all 250 primary XML sources found one previously unregistered `fitdistrplus` use: P043, PMC10719105, Methods paragraph51 ([DOI](https://doi.org/10.1038/s41586-023-06824-9), SHA-256 `41db6565b9c28d56ef328ce411a63865d3f6a88dfa070f1c94457546ac40598e`). It fits a normal distribution to shuffled co-accessibility scores before the stated BH/FDR procedure. The article does not supply the function, estimation method, package version, shuffle implementation or an unambiguous retention direction. Its wording about filtering out scores significantly larger than background must remain unrepaired.

The first exploratory search missed the name because raw text appended citation94; the corrected scan checks citation-stripped and literal forms and retains the failed attempt. Official fitdistrplus 1.2-6 source was downloaded and checked against CRAN's repository MD5 plus complete SHA-256 member pins. This establishes source intake, not the paper version or a user fitting workflow. A preexisting private-library DESCRIPTION declares version 1.2-6; namespace loading and fitting remain unvalidated. This checkpoint installs nothing and does not claim a fitdistrplus analysis.

The canonical catalog still reports 389 named tools and 74 scoped integrations while fitdistrplus registration, normal-fit integration, Cicero paper-specific backgrounds and broader Monocle coverage remain unfinished. The discovered omission is explicit here; paper-wide semantic completeness and the full250-paper implementation goal remain open. Cell Ranger stays excluded and py-monocle's license decision remains pending.
