# Native chromVAR expectations and deviations

This development command computes chromVAR deviations and deviation Z-scores in Java from a count matrix, motif peak annotations and an **explicit prepared background matrix**. It implements the retained `compute_expectations_core`, `compute_deviations_single` and `compute_deviations_core` formulas. The 1.16.0 and 1.34.1 source bodies are byte-identical. It does not generate GC/intensity-matched backgrounds or scan motifs; those are separate preparation steps. No R, download or installation is needed for native DEVIATIONS or VERIFY.

**WARNINGS / REQUIRED ACTIONS:** invalid inputs, source errors, exceeded capacities and failed verification produce **FAIL2** and admit no result. Preserve attempts, read `WARNINGS.md`, `diagnostics.json`, the captured request and inputs, correct the named cause, and retry into **NEW** output paths. If upstream background/motif preparation requires unavailable or forbidden software, report **SKIP3**, skip that dependent analysis and provide/install the exact permitted requirements separately. Do not replace missing upstream preparation with the synthetic backgrounds in this example.

## Run the complete example

From the repository root in PowerShell, using Java 21 or later:

```powershell
$chromvarJar = (Resolve-Path 'build/literature-review/signac-runtime/chromvar-workflow-build-1/omicsmatic-0.3.7-chromvar-workflow-development.jar').Path
$chromvarClass = 'org.jlinalg.cli.ChromVarRequestCli'
java -Xmx768m -cp $chromvarJar $chromvarClass --request examples/chromvar-deviations/request.json
java -Xmx768m -cp $chromvarJar $chromvarClass --request examples/chromvar-deviations/verify-request.json
```

The [example directory](../../examples/chromvar-deviations/README.md) includes all count, annotation, background and label inputs. There are 20 synthetic peaks, six cells, three annotations (including an empty annotation) and seven supplied background iterations. These are numerical controls, not a reference genome, motif database or GC/intensity-matched biological analysis. Output is `build/chromvar-deviations-demo-NEW`. If it already exists, preserve it and change the analysis and verification paths.

The [legacy request](../../examples/chromvar-deviations/legacy-request.json) selects the 1.16.0 source-formula profile. The [grouped request](../../examples/chromvar-deviations/grouped-request.json) gives equal weight to the fractions within two explicitly declared groups. Each request has a separate output directory. Relative paths resolve against the request file's directory.

The exact executable is 164869429 bytes, SHA-256:

```text
c74988bfe206cdc0e098b2ede00c0710a9db0d58f7d7b322631e530ab0484df9
```

This is a standalone development class, with no root CLI route or release. It preserves the validated numerical parent entries and leaves the canonical `build/cli/omicsmatic-0.3.7.jar` unchanged. The catalog remains at 68 new scoped integrations, 71 including existing adapters, and 259 missing tools; this milestone does not grant full Signac/chromVAR implementation credit.

## Prepare a study request

Copy the checked request, choose the declared source profile (`1.16.0-source` or `1.34.1-package`), point every input to the study's actual files and choose NEW output paths. The profile selects native source formulas; it does not assert that a historical R package was installed or executed. `genome_build`, `motif_collection` and `background_provenance` are required provenance declarations, not certification. Record the actual upstream database edition, reference, background algorithm, parameters, seed and peak order.

| Field | Input contract |
| --- | --- |
| `counts` | Plain UTF-8 Matrix Market **integer or real coordinate, general symmetry**, peaks by cells. Finite nonnegative counts; fractional values are retained. Duplicate coordinates must be explicitly aggregated with provenance before admission. |
| `peak_features`, `cells` | One unique exact ID per line, no header, in matrix order. At least one peak and one cell. Unicode IDs are supported. |
| `motif_labels` | TSV header `motif_id`, `motif_name`. IDs are unique; display names may repeat. Include declared motifs with no matched peaks. |
| `annotation_peaks` | TSV header `motif_id`, `peak_id`. Every membership is retained, including repeats and file order. A motif absent from this file is an empty annotation. |
| `motif_matrix` | Alternative to `annotation_peaks`: peaks-by-motifs Matrix Market binary presence, with row IDs from `peak_features` and column IDs/names from `motif_labels`. Choose exactly one annotation representation. Pattern duplicates use logical union. Nonbinary numeric values are refused; use explicit index-list membership for intended multiplicity. |
| `background` | TSV header `peak_id`, `iteration_1`, `iteration_2`, ... . One row for every peak, in count order. Each entry is a **one-based peak row index**. Do not use genomic coordinates or zero-based Java indices. |
| `norm` | JSON boolean, default `false`. Original `true` behavior sums per-cell fractions; it does **not** average across cells or renormalize to one. |
| `groups`, `group_levels` | Optional pair. Group TSV header `cell_id`, `group`, every cell in order; levels file has one unique declared factor level per line. Unused levels remain present. Blank/unmatched labels map to missing factor assignments with warnings. A literal `NA` string is an ordinary group if declared as a level. |
| `expectation` | Optional TSV header `peak_id`, `expectation`, every peak in order. This overrides computed expectations; norm/groups do not alter its values. Finite signed numbers and exact `NA`, `NaN`, `Inf`, `-Inf` tokens are retained without normalization or imputation. |

All TSV columns above are separated by literal tabs. Background values refer to **this exact count peak population**. Repeated background indices and repeated annotation members retain source multiplicity. Zero background iterations are representable with only the `peak_id` header/rows, but yield source nonfinite statistics and require review. These inputs do not establish that a background is appropriately GC/intensity matched.

For the [scanned motif workflow](SIGNAC_SCAN_DEVELOPMENT.md), reuse its peak count matrix, `motif-presence.mtx`, `motif-labels.tsv` and exact peak/cell IDs, then supply a separately prepared background for that same population. The original public chromVAR matrix interface was exercised on the captured synthetic scan matrix (80 peaks, 12 cells, three motifs), and its scores matched native scoring, including a deliberate Unicode cell-ID mapping.

## Understand source behavior

By default the expected fraction per peak is its pooled count divided by the total count. With groups and `norm=false`, fractions are calculated within each group and then averaged over declared levels. Original grouped `norm=false` can fail when a selected matrix axis has length one; that source failure is retained. An unused level can yield NaN. With `norm=true`, per-cell fractions are **summed** within each group before averaging groups. Missing group assignments do not become a new group.

For an annotation, the method compares observed counts to its expected count in each cell. Each supplied background iteration substitutes its peak indices, preserving multiplicity. The deviation is the observed fractional deviation minus the background mean; its Z-score divides by the **sample** standard deviation across background iterations (B−1 denominator). Full-core scoring uses the original expected-count threshold **one**. Cells below that threshold become source `NA`. The low-level Java `single` method also exposes its original explicit threshold and intermediate arrays.

**NA and NaN are different.** Empty annotations have NA scores/deviations and NA background overlap. Zero totals, zero iterations, invalid expectations and zero variance can produce NaN/Inf. The API and tables preserve these kinds. Source operand order affects which missing kind propagates. Original single-peak sparse-matrix column means accumulate doubles sequentially; with zero variance, a tiny residual can produce Inf/−Inf. These outputs are preserved, not interpreted as valid discoveries.

The full core requires a positive pooled count for **every** peak. It also retains the original one-cell/multiple-annotation output-dimension failure. No peak/cell filtering, threshold relaxation, imputation, clamping or corrected normalization is inferred. Repeated annotation members can produce fraction metadata above one; inspect multiplicities rather than treating the field name as a bound.

## Outputs, verification and capacities

Successful output contains:

- `deviations.tsv`: `motif_id`, `cell_id`, `deviation`, `z`, with a row for every annotation/cell pair.
- `expectations.tsv`: every peak's effective expected fraction/value.
- `motif-summary.tsv`: IDs/names, fraction matched and background overlap.
- `diagnostics.json`, `WARNINGS.md`, captured original/canonical requests and inputs, retained original function/notice resources, manifest/status, complete file checksums and `verify-request.json`.

VERIFY reads the capture and recomputes native expectations, deviations, metadata and warnings. It checks file population/hashes, input/request/source bindings, exact row order and generated verification request. It runs without R and preserves the existing capture. A failed capture cannot be promoted by VERIFY. Hashes prove integrity, not authenticity or full host provenance.

Default input capacity is 2 GB per file, one million characters per line and two million stored count/annotation coordinates. Numerical capacities include two million peaks/cells, 100000 annotations, 20 million supplied background indices, 10 million sampled memberships per annotation and 10 million output numeric values. Individual counts and each peak/cell total must be at most 2^53−1. These are admission bounds, not performance guarantees.

Request fields `maximum_entries`, `maximum_background_values`, `maximum_sample_memberships`, `maximum_output_values`, `maximum_input_bytes`, `maximum_output_bytes` and `maximum_work` expose bounded capacities. The input/output byte limits apply per file/result table. `maximum_work` applies to native phases, not a wall-clock timeout. Memory errors or exceeded capacities admit no result; use suitable hardware/heap and explicit capacities with NEW output paths. There is no automatic downsampling. Cancellation is checked during input and numerical loops.

## Validation and remaining work

The native numerical candidate passes **320 checks on each of Windows Java 21, Windows Java 25 and Linux Java 21**: 306 original method calls plus 14 admission/immutability properties. The calls span 51 synthetic populations and two source profiles, not independent biological datasets. All intermediates and NA/NaN/Inf kinds are checked. Finite comparisons use relative tolerance 2e−11 or absolute 2e−12. An earlier candidate's mismatches and direct original arithmetic probes remain retained.

Typed request validation matches 86 successful original full-core protocols, retains 16 source failures and passes 258 captured verifications. Historical controls use unchanged source functions on the recorded current R/Matrix dependencies, not historical whole packages. The motif-matrix integration additionally runs the original public `computeDeviations` interface. This milestone has no upstream GC/intensity sampling, historical chromVAR 1.5.0 execution, complete Signac workflow, release, push or deployment claim. Literature Pages remain withheld.

The [validation record](chromvar-deviations-development/VALIDATION.json), [retained controls](chromvar-deviations-development/controls.zip) and [required actions](chromvar-deviations-development/WARNINGS.md) bind the tested artifacts, source snapshots, literal examples, corruption/failure checks and unchanged parent/catalog. The evidence archive deduplicates content by SHA-256 and retains each logical path in `logical-files.json`; it does not discard failed attempts.
