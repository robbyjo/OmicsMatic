# Native Signac fragments, quantification and legacy nucleosome signal

This development candidate adds Java fragment counting, peak/region quantification, legacy nucleosome QC and sparse binarization. It builds on the validated [normalization](SIGNAC_NORMALIZATION_DEVELOPMENT.md) and [LSI](SIGNAC_LSI_DEVELOPMENT.md) candidates. **Full Signac remains missing in the catalog.** TSS enrichment, gene activity, motif/chromVAR workflows, peak links and differential accessibility still need their own implementation and original-source validation.

**WARNING — `keep_all_features: true` can discard counts in the original source when more than one feature chunk is used.** Original Signac 1.17.1 restores all feature rows inside every chunk, then selects the first matching row. Only the first chunk's counts survive for each source; cells discovered later can remain as zero columns. This Java implementation reproduces that observed behavior and emits `UPSTREAM_KEEP_ALL_CHUNK_BEHAVIOR`. In the original boundary control, `process_n` values 1, 4 and 2000 produce totals 1, 4 and 21. Review the intended analysis: keep the default `keep_all_features: false`, or explicitly choose a justified single chunk with `process_n` at least the number of features on chromosomes present in each source. Historical results are not silently corrected.

**FAIL2 — Failed analyses admit no successful result.** Read the terminal message, `WARNINGS.md`, `analysis-status.json` and logs. Retain the failed directory, correct the named cause and choose a new `out`. Partial tables in a failed directory are diagnostic only. Existing destinations are refused. Java or class-loading failures occur before this wrapper can write a bundle; use the named candidate JAR and a supported Java runtime.

**No R installation is needed for the four native operations.** The optional LSI step uses the existing exact-package R adapter. A missing R executable, pinned package/library or working native dependency produces **SKIP3** there. Install/provide that runtime where permitted and rerun to a new directory. If installation is unavailable or forbidden, retain the skip and skip dependent LSI analysis. Nothing is downloaded or installed during these native analyses.

## Run the checked examples

From the repository root, follow [the example commands](../../examples/signac-fragments/README.md). The exact input JSON files and synthetic data are checked in. The example outputs use new directories under `build/signac-fragments-*-demo-NEW`; use different names for another run and update dependent paths. Inputs resolve relative to the JSON file.

```powershell
$fragmentJar = 'build/literature-review/signac-runtime/fragment-workflow-build-3/omicsmatic-0.3.7-signac-fragment-workflow-development.jar'
java -Xmx512m -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/feature-request.json
java -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/verify-request.json
```

The local candidate is **164488830 bytes**, SHA-256 `4e822a4a4212d30d04a55c57249b13cad88fe34146aa9abbeac47088e53928e0`. It preserves every entry in the validated native API/LSI parent and adds seven request-workflow classes. It is a local standalone entry point. There is no root CLI route, release, canonical JAR replacement or deployment for this milestone. Compilation targets Java 17; execution is checked on Windows Java 21, Windows Java 25 and Linux Java 21.

Keep the request file's own path ASCII when using the tested Windows launchers. Put Unicode data paths and IDs inside UTF-8 JSON and UTF-8 tables. This avoids launcher argument conversion before Java receives the path. Use a `VERIFY` JSON request for a Unicode captured directory too.

## Request reference

Every request has `schema_version: 1`, an exact uppercase `mode` and `out`. The `VERIFY` mode instead has `analysis`. Unknown fields are rejected. Each analysis snapshots the request and every input file, checks hashes while copying, and writes into a new directory.

| Mode | Required fields | Optional fields and defaults | Main outputs |
| --- | --- | --- | --- |
| `COUNT_FRAGMENTS` | `files`: array of fragment paths | `cells` or `cells_file`; `max_lines: 0` means all records | `counts.tsv` |
| `FEATURE_MATRIX` | `sources`, `regions`, `genome_build` | `cells` or `cells_file`; `keep_all_features: false`; `process_n: 2000`; `sort_records: 100000` | `matrix.mtx`, `features.tsv`, `cells.tsv` |
| `NUCLEOSOME_SIGNAL` | `sources`; `object_cells` or `object_cells_file` | `max_lines`: 5000 times the number of object cells per file; explicitly set 0 for all records | `nucleosome.tsv` |
| `BINARIZE_CSC` | `matrix`, `features`, `cells_file` | None | `matrix.mtx`, `features.tsv`, `cells.tsv` |
| `VERIFY` | `analysis`: captured directory | None | Terminal verification result; analysis is not modified |

A source is `{"path":"fragments.tsv.gz"}` or includes `"map":[{"cell":"object-A","barcode":"A"}]`. For larger mappings use `"map_file":"cell-map.tsv"`, a UTF-8 TSV with header `cell<TAB>barcode`. Mapping is one-to-one within each source. Object cell IDs and file barcodes have distinct roles. Omitted maps retain source discovery behavior; the workflow does not invent an identity map.

`cells_file` and `object_cells_file` contain one ID per line, without a header. Choose the file or inline representation, not both. Inline lists appear as `"cells":["A","B"]`. Original CountFragments collapses duplicate whitelist IDs; matrix/object cell universes follow the stricter native contracts.

`regions` is a UTF-8 TSV with header `chromosome<TAB>start<TAB>end`. Coordinates are **one-based, closed** GRanges coordinates. Fragment input is **zero-based, half-open BED**. For example, a fragment `chr1<TAB>99<TAB>100<TAB>A<TAB>7` overlaps the one-base region `chr1,100,100`. Use matching assembly and chromosome names. `genome_build` is a required declared label, recorded in provenance; it does not validate a reference FASTA or reconcile assemblies.

Fragment input has five tab-separated columns: chromosome, start, end, barcode and read-support count. A consistent sixth strand column (`+`, `-` or `.`) is accepted. Plain files, ordinary gzip and BGZF are supported by content detection. Leading comment lines are supported; interleaved headers and malformed records fail. Native readers do not need a Tabix index. The original R comparisons used indexed BGZF inputs, and these different intake contracts are intentional and recorded.

For FeatureMatrix, records must have nondecreasing starts within each chromosome and contiguous chromosome blocks. CountFragments can stream unsorted records. Whole-file scans check gzip integrity. A `max_lines` prefix stops after that many records **per file**, including records excluded by the cell whitelist; unread tail/compression integrity is not certified. The `PREFIX_ONLY` warning requires choosing full-file scanning when full integrity is needed.

## Scientific behavior and warnings

FeatureMatrix counts one whole fragment line for each overlapping feature. Duplicate lines count separately. The fifth field describes read support and does **not** multiply feature counts. TSS endpoint counting is a separate, currently unimplemented operation. Feature/cell order and zero rows/columns follow the original controls, including per-source discovery order, mapped IDs, absent chromosomes and multi-source union behavior. Repeated exact region labels are rejected as ambiguous; overlapping distinct regions are allowed.

CountFragments reports `frequency_count` (lines), `reads_count` (sum of fifth fields), `nucleosome_free` (length below 147) and `mononucleosomal` (**strictly greater than 147 and strictly less than 294**). Lengths exactly 147 or 294 count in neither length bin. Multiple files pool matching raw barcodes; they are not automatically namespaced by sample. Review inputs before pooling samples that reuse barcode strings.

The legacy NucleosomeSignal ratio is mononucleosomal/nucleosome-free, followed by its source ECDF and R-compatible two-decimal percentile rounding. Signac 1.17.1 marks this legacy function deprecated in favor of ATACqc; this candidate implements the captured legacy metric and does not claim ATACqc. It is not TSS enrichment or a complete biological QC decision.

Explicit maps pre-seed unobserved barcodes, so 0/0 is `NaN`. Without a map, an unobserved object cell receives `NA`. Positive numerator/zero denominator produces `Inf`; those infinities participate in the ECDF. NaNs are excluded from the ECDF and remain `NaN`; missing metadata remain `NA`. An entirely undefined ECDF fails. Outputs retain separate `ratio_kind` and `percentile_kind` columns; do not replace these states by zero. Repeated object-cell QC rows across sources are rejected rather than silently pooled. Raw CountFragments pooling remains available separately.

Sparse BinarizeCounts changes **every stored CSC slot** to one, including explicitly stored zeros. It does not turn implicit zeros into ones. `STORED_ZERO_BINARIZED_TO_ONE` calls this out. If a positive-count indicator was intended, explicitly remove stored zeros first and record that transformation. Dense-matrix binarization is outside this candidate's scope.

Other actionable warnings include `SOURCE_MISSING_CHROMOSOMES`, `REQUESTED_CELLS_OUTSIDE_MAPPINGS`, `NO_OVERLAPPING_CELLS`, `NONFINITE_NUCLEOSOME_SIGNAL` and `UNOBSERVED_OR_UNMAPPED_OBJECT_CELLS`. Read their messages and required actions in each bundle. A zero-column feature matrix can be a valid quantification result; skip normalization/LSI until a scientifically justified cell/region correction supplies usable data.

## Outputs, limits and verification

Matrix outputs are Matrix Market integer coordinate files with one ID per line in the companion files. They feed the existing native normalization/LSI intake. The [optional LSI request](../../examples/signac-fragments/lsi-request.json) demonstrates this chain with two components; the exact R runtime is still required for SVD.

`operation.json` records mode, declared assembly, per-file scan counts, compression/prefix scope, effective sampling cap, chunk setting and warning codes. `manifest.json`, `analysis-status.json`, `WARNINGS.md`, original/canonical requests, captured inputs and `artifact-checksums.tsv` accompany results. All inputs are copied, so plan disk space for the captured copy and temporary sorting. A manifest marked failed is never a successful analysis.

Verification checks the complete file inventory and hashes, rejects symlinks, validates original/canonical option and manifest bindings, then **recomputes results, operation metadata, warnings and required actions** from the captured inputs. It needs neither original input locations nor R for the four native modes. It uses owned temporary sort files and removes them. Hashes provide integrity, not a signature or proof against coordinated replacement of an entire bundle.

Default API bounds are two million features, two million cells, twenty million nonzero values, one billion records, 250 million overlap contributions and 250 GB decoded bytes per scan. Fragment lines are limited to 4094 UTF-8 bytes; coordinates stay within the supported 2^29 range. Integer totals must be exactly representable up to 2^53-1. No overflow or malformed line is silently dropped. JSON requests are at most one million bytes; use map/cell files for larger lists. Region/map tables are bounded at two million rows and one GB. These are admission limits, not a claim that a 512 MB heap accommodates every maximum-size input.

FeatureMatrix uses bounded external sorting; `sort_records` controls the in-memory batch (1 through 1,000,000). Lower it to reduce sort memory; total feature/cell/nonzero structures still require memory. If capacity or memory fails, retain the failed run and allocate suitable resources or use a scientifically justified partition. Only owned spill files are removed. Input files, pre-existing output destinations and caller-owned paths are preserved.

## Evidence and remaining scope

The [frozen validation record](signac-fragments-development/VALIDATION.json), [evidence archive](signac-fragments-development/controls.zip) and [prominent warnings](signac-fragments-development/WARNINGS.md) preserve original outputs, exact source snapshots, failed attempts, process logs and checksums. Original package 1.17.1 was executed against 63 count, 64 quantification, 33 legacy nucleosome and 36 sparse binarization cases. An additional 20,100 original R rounding scalars cover every rank/n combination through n=200. Source from earlier catalog-relevant releases was inspected; historical package binaries were not executed for this milestone.

The native API passed **728 tests per JVM** on Windows Java 21, Windows Java 25 and Linux Java 21, without skips. They exercise the original results with plain/gzip/BGZF inputs, forced disk sorting, boundaries, mappings, prefix scans, nonfinite/missing states, sparse storage, malformed input, cancellation and preservation. Workflow and exact-example records additionally cover fresh native requests, captured replay, rehashed result/metadata rejection and actual native-matrix-to-R-LSI fits. Consult the machine-readable validation for exact counts and retained failures.

This is a scoped development milestone. The catalog remains 68 new integrations, 71 including existing tools, and 259 missing entries. No full Signac credit, complete runtime DLL/host certification, release, push or whole-goal completion is implied. See official [FeatureMatrix](https://stuartlab.org/signac/reference/featurematrix), [CountFragments](https://stuartlab.org/signac/reference/countfragments) and [NucleosomeSignal](https://stuartlab.org/signac/reference/nucleosomesignal) references alongside the retained implementation sources and original execution evidence.
