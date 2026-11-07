# Native Signac motif enrichment

This local development candidate implements Signac FindMotifs statistics in Java and provides a captured `ENRICH`/`VERIFY` request workflow. It accepts a prepared peaks-by-motifs matrix and explicit query/background peak lists. It extends the validated [LinkPeaks candidate](SIGNAC_LINKS_DEVELOPMENT.md). **Full Signac integration remains incomplete and receives no catalog credit yet.**

**INPUT WARNING — Native enrichment does not perform sequence scanning, RegionStats, GC-matched background selection or chromVAR.** A FASTA, PFM or peaks-by-cells count matrix is not a substitute for the required peaks-by-motifs annotation matrix. Preserve the actual motif database edition, scan thresholds, assembly and selection provenance. `genome_build` and `motif_collection` are declarations, not certification.

**INFERENCE WARNING — The source uses the background alone as its hypergeometric population.** It does not pool query and background into a Fisher test. It can return zero p for query motifs absent from an independent background. This candidate retains that behavior. Review whether the original source inference and declared populations answer the study's scientific question. Enrichment does not establish per-cell TF activity or regulatory causality.

**NONFINITE SOURCE OUTPUTS — Empty populations, zero denominators or invalid hypergeometric parameters can produce `NaN`/`Inf`.** They remain explicit and trigger prominent warnings; they are not repaired, converted into discoveries or silently omitted from the table. Inspect affected rows and input meaning. Missing peak IDs, malformed inputs, excessive work and failed verification produce **FAIL2**, with no admitted result. Preserve attempts, correct the named cause and use new output directories.

## Run the checked examples

From the repository root:

```powershell
$motifJar = 'build/literature-review/signac-runtime/motif-workflow-build-1/omicsmatic-0.3.7-signac-motif-workflow-development.jar'
java -Xmx512m -cp $motifJar org.jlinalg.cli.SignacMotifsRequestCli --request examples/signac-motifs/request.json
java -Xmx512m -cp $motifJar org.jlinalg.cli.SignacMotifsRequestCli --request examples/signac-motifs/verify-request.json
```

The [example directory](../../examples/signac-motifs/README.md) contains all input files, current/legacy requests, an all-background request and verification. The candidate is **164699354 bytes**, SHA-256 `277ef4bab9d2f9a77316298afba9d0090ff32e8311dc56212b57e9d2fe0fe036`. It preserves the validated parent entries, targets Java 17 and is exercised on Windows Java 21, Windows Java 25 and Linux Java 21. These native commands need no R, download or installation.

Relative paths resolve against the request directory. Keep the request filename/path ASCII on the tested Windows launchers; exact Unicode data paths and labels belong inside UTF-8 JSON and tables. Existing output directories are refused. Change `out` and the corresponding verification path before rerunning, preserving the earlier capture.

## Inputs and minimal request

```json
{
  "schema_version": 1,
  "mode": "ENRICH",
  "profile": "1.17.1-package",
  "out": "motif-enrichment-NEW",
  "genome_build": "GRCh38-declared-by-investigator",
  "motif_collection": "exact-database-edition-and-scan-provenance",
  "motif_matrix": "motif-presence.mtx",
  "peak_features": "peak-ids.tsv",
  "motif_labels": "motif-labels.tsv",
  "query": "query-peak-ids.tsv",
  "background": "background-peak-ids.tsv",
  "p_adjust_method": "BH",
  "maximum_work": 100000000
}
```

Unknown fields are rejected. All displayed fields except `background`, `p_adjust_method` and `maximum_work` are required. Omitted or null `background` selects every matrix peak, including any query peaks; an empty background file means an empty population. These choices differ scientifically. Query/background files contain one exact peak ID per line, without a header or blank lines. Empty files are permitted to retain source boundary behavior. Duplicates in selections remain counted repeatedly, with a warning. No implicit membership intersection or deduplication occurs.

The matrix is general coordinate Matrix Market, with **peaks in rows and motifs in columns**. Integer, real and pattern fields are accepted; numeric values must be finite, nonnegative exact integers within 2^53−1. Normal motif occurrence input is binary. Supplying counts greater than one preserves raw source column sums and emits a warning because resulting hypergeometric parameters can be invalid. Explicit numeric zeros remain stored. Duplicate numeric coordinates are summed exactly; duplicate pattern coordinates use logical union, matching Matrix conversion. Excessive exact sums fail.

`peak_features` contains one unique matrix row ID per line, no header. `motif_labels` is a UTF-8 TSV with actual header `motif_id<TAB>motif_name`, one row per matrix column in order. Motif IDs must be unique; display names are the effective names from upstream preparation. Native enrichment does not rename motifs. Zero motif columns and one motif column are supported, including the correct empty output header.

Intake defaults allow up to two million peak rows, one million motif columns, ten million stored entries, two billion bytes per file and 16384 characters per matrix/table line. Selected lists allow at most two million entries; ID lines have tighter bounds. `maximum_work` defaults to 100 million counted selection/sparse/correction/sort operations and admits 1..10^13. Cancellation is checked during actual work. These are capacity bounds, not heap or feasibility guarantees; Hommel correction is quadratic. Use appropriate resources and a justified explicit work limit. Do not change biological selections merely to make an analysis pass.

## Source profiles and exact statistics

| Profile | Native statistical contract |
| --- | --- |
| `1.6.0-source` | Original FindMotifs counts, percentages, folds, p values and ordering; no `p.adjust` column; correction argument ignored as upstream |
| `1.9.0-source`, `1.14.0-source`, `1.17.1-package` | Same source statistics with complete-family R-style correction and a `p.adjust` column |

These profile names identify the source contract. The Java command does not execute R or a historical package. For source reference controls, the actual current Signac 1.17.1 package was executed. Historical controls executed unchanged literal functions on current pinned dependencies with a strict, recorded `GetAssayData(slot=...)` to `layer=...` access bridge. That bridge permits only metadata/counts/data access and performs no matrix, selection or numerical transformation. The earlier unbridged API failures remain retained. Historical whole-package execution is not claimed.

For each motif, source observed/background counts are column sums after ordered query/background subsetting, including duplicate rows. Percent observed is `observed/queryRows*100`; percent background is `backgroundCount/backgroundRows*100`; fold is their ratio. The source probability is:

```text
phyper(observed - 1,
       m = backgroundCount,
       n = backgroundRows - backgroundCount,
       k = queryRows,
       lower.tail = FALSE)
```

Invalid population parameters retain source `NaN`. All motifs are kept in the correction family before output ordering, including zero-occurrence motifs. Correction choices are `BH` (default), `BY`, `holm`, `hochberg`, `hommel`, `bonferroni`, `none` and `fdr` (BH alias). Source missing/NaN probabilities are excluded from the default finite correction denominator and remain NaN. The implementation reuses the project's validated direct-double R correction methods. Legacy 1.6 has no adjusted result and ignores the correction choice; do not label its p values as FDR.

Rows are ordered by source p ascending, then fold descending, with NaNs last and stable original motif order for ties. The independent native tests compare counts, number kinds, names, order, probabilities and every supported correction against original outputs. Finite comparisons use absolute tolerance 2e−12 or relative 2e−11; matrix IDs, retained duplicate multiplicities and source ordering are exact.

## Captures and verification

A successful run writes `enrichment.tsv`, `diagnostics.json`, `WARNINGS.md`, `analysis-status.json`, canonical/source requests, copied inputs and checksum/manifests. Legacy output has no `p.adjust` column. Number kinds remain explicit as numeric text, `NaN` or `Inf`. Read every warning even after exit 0 (`completed-with-warnings`). Exit 2 means failed request, analysis or verification. Failed captures retain diagnostics and do not publish an admitted enrichment table.

```json
{"schema_version":1,"mode":"VERIFY","analysis":"motif-enrichment-NEW"}
```

Run this JSON with the same CLI. Verification checks the complete inventory, request/source/profile/scope bindings, reconstructed matrix and selections, native statistics, correction family, source order, diagnostics and warnings. It rejects tested semantic changes even after checksums are recomputed. A failed verification means the table must not be used as an admitted result. Hashes are not signatures or authenticity certification.

Verification does not reconstruct the supplied motif database, scan a reference genome, reproduce KDE weights or replay upstream RNG. Explicit selections are inputs. Their original preparation, source version and seed evidence remain separate provenance. The native command does not certify assembly compatibility, a complete external runtime or biological interpretation.

## Upstream scanning and chromVAR work

The papers require more than enrichment: P094 describes AddMotifs and GC-matched FindMotifs; P039 and P099 report chromVAR 1.16.0, and P225 reports 1.5.0. Original versions must remain explicit. Signac 1.16 changed background matching to query/background KDE density ratios; older source profiles use query-density weighting. Native `ENRICH` does not silently choose either sampler.

A new isolated reference runtime installed motifmatchr 1.34.0 and chromVAR 1.34.1 without modifying the frozen base/private libraries. Fresh synthetic FASTA tests passed actual RegionStats, CreateMotifMatrix and AddMotifs, plus an unchanged Signac 1.9 chromVAR wrapper with the recorded access bridge and current chromVAR. Explicit serial BiocParallel execution avoids child-library ambiguity. The earlier child-R missing-`snow` attempt was stopped by its identity-verified owned process tree, checked gone and retained. The successful probe produced finite 2-by-12 chromVAR scores; it is not a validated public chromVAR workflow or historical package reproduction.

Official 1.16.0 and current motifmatchr/chromVAR source archives were acquired for comparison after an initial HTTP403 attempt was corrected with the established project User-Agent. Both attempts are preserved. These source downloads do not themselves admit historical execution. Remaining historical/profile, scanner, chromVAR and differential-accessibility workflows still need implementation and validation.

**SKIP3 / REQUIRED ACTION — For upstream operations, missing or forbidden dependencies require skipping the dependent analysis.** Provide/install the named exact runtime only where permitted, into suitable new private paths; otherwise retain the skip. Do not mutate frozen/shared libraries or silently substitute a current package for a paper's version. No native enrichment analysis installs packages. Its explicit prepared-input operation remains available without R.

## Evidence and project scope

The [frozen validation record](signac-motif-development/VALIDATION.json) distinguishes:

* 144 original enrichment controls across four editions: 111 successes and 33 retained failures with the explicit access bridge; the unbridged attempt is retained separately.
* 245 native tests per Java environment, all passing on Windows Java 21/25 and Linux Java 21, including original result/Matrix Market round trips, all corrections, nonfinite statistics, pattern/numeric duplicates, Unicode, bounds and cancellation.
* 111 actual native request comparisons, 333 captured verifications and eight retained missing-row failure gates.
* Thirty-six rehashed semantic-corruption rejections, three unlisted nested-file rejections, preserved existing outputs, three explicit failed-analysis gates and nine rejections of failed captures.
* Three literal checked enrichment requests, two additional fresh platform runs, five original statistical comparisons and sixteen captured/literal verifications.
* Successful isolated upstream setup and actual scanner/sequence-statistics/chromVAR reference probes, explicitly separate from native user workflow admission.

This is a local standalone CLI candidate. It does not replace the canonical executable or add root dispatch. No release, push or deployment occurred. Parent source pins, library inventories and the catalog are rechecked. Counts remain **68 new integrations, 71 including existing tools and 259 missing**. Full Signac remains missing in the catalog until its remaining roles and public gates pass. Cell Ranger remains excluded by user request. The 250-paper implementation goal remains active.

Primary references: [official FindMotifs reference](https://stuartlab.org/signac/reference/findmotifs), [Signac changelog](https://stuartlab.org/signac/news/), [motifmatchr package](https://bioconductor.org/packages/release/bioc/html/motifmatchr.html) and [chromVAR package](https://bioconductor.org/packages/release/bioc/html/chromVAR.html). Retained version-specific full source files and notices accompany the evidence.
