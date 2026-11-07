# Signac peak-to-gene links

This local development candidate runs original Signac LinkPeaks and independently audits its numerical results in Java. It extends the validated [TSS/gene activity candidate](SIGNAC_ANNOTATION_DEVELOPMENT.md). It accepts prepared peak counts and paired RNA expression; it does not create those measurements or infer their alignment. **Full Signac integration remains incomplete and receives no catalog credit yet.** Motifs/chromVAR, differential accessibility and their paper-specific workflows remain pending.

**SKIP3 — Missing or unavailable runtime dependencies skip fresh LinkPeaks analysis.** Supply exact R 4.6.1, Signac 1.17.1, Matrix 1.7.5, SeuratObject 5.4.0, GenomicRanges 1.64.0, GenomeInfoDb 1.48.0 and jsonlite 2.0.0 in explicit private/base libraries, with working native dependencies. These profiles require base R `sample.int`; optional `wrswoR` must be absent. Provide/install dependencies only where permitted, into a suitable separate runtime. If installation is forbidden or cannot succeed, retain the skip and **skip dependent analyses**. No analysis command downloads or installs software. Java-only captured verification remains available.

**FAIL2 — An invalid request, upstream failure, altered capture or failed numerical audit admits no result.** Read the named error, `WARNINGS.md`, `manifest.json` and worker logs. Preserve the attempt, correct its input/runtime/background/capacity cause and rerun into a **new output directory**. Never silently switch profiles, reorder or intersect cells, reduce background samples, discard genes or relax thresholds to obtain results. Existing output directories are refused.

**INFERENCE WARNING — Source p values are nominal `pnorm(-abs(z))`.** There is no factor of two and no automatic multiple-testing adjustment. They are not FDR values, and peak-to-gene associations do not establish regulatory causality. Document the inferential and multiplicity decisions appropriate to the study.

## Start with the checked examples

From the repository root:

```powershell
$linksJar = 'build/literature-review/signac-runtime/links-workflow-build-3/omicsmatic-0.3.7-signac-link-workflow-development.jar'
java -Xmx768m -cp $linksJar org.jlinalg.cli.SignacLinksRequestCli --request examples/signac-links/request.json
java -Xmx512m -cp $linksJar org.jlinalg.cli.SignacLinksRequestCli --request examples/signac-links/verify-request.json
```

The [checked example directory](../../examples/signac-links/README.md) contains all inputs, current and historical requests, a valid empty-result request and verification. Its current p cutoff 1 and small background sample are synthetic demonstration settings, not study recommendations. Relative paths resolve against the request's directory. Keep the request filename/path ASCII on the tested Windows launchers; exact Unicode data paths and IDs belong inside UTF-8 JSON/tables.

The candidate is **164636719 bytes**, SHA-256 `1679ec6d06a297d657ff06b3ddb1789fc4eee96164b0a5ec5d8c38d308f53a47`. It adds a standalone request CLI and guarded R worker to the native API candidate, preserving parent entries. Java compilation targets 17. Native tests and captured verification run on Windows Java 21, Windows Java 25 and Linux Java 21. Fresh R fits were executed on Windows R 4.6.1; a fresh fit launched from Windows Java 25 also preserves Unicode gene/cell IDs. Linux R execution and complete portable runtime/DLL admission are not claimed.

This is a local development artifact. It does not replace the canonical executable, add a root command or constitute a release, push or deployment.

## Explicit source profiles

| Profile | Executed implementation | Background behavior |
| --- | --- | --- |
| `1.17.1-package` | Actual installed `Signac::LinkPeaks`; twelve relevant function bodies checked against retained original source | Product of query/background KDE density ratios; excludes chromosome prefix such as `^chr1-` |
| `1.14.0-source` | Twelve literal original 1.14.0 functions in a private environment using the current pinned dependencies | Historical query-density-only weighting; excludes prefix `^chr1`, also affecting chr10/chr11 |

The historical profile is **not a historical whole-package installation**. Current dependencies and R are explicit in its evidence. Paper P094 uses Signac 1.14 LinkPeaks; its version must not be silently replaced with 1.17 behavior. Other paper-specific roles still require their own curation and validation. Retained source functions and the MIT notice are under `src/main/resources/org/jlinalg/omics/signac-links`.

Both profiles set R RNG kinds to Mersenne-Twister, Inversion and Rejection and use the requested seed. The worker captures pre/post RNG states, reruns the original background sampler from the pre-state, and requires the replay to reach the same post-state. Upstream optional sampler changes are refused rather than silently accepted.

## Inputs and provenance

Supply two sparse Matrix Market matrices with features in rows and cells in columns:

| Input field | Required content |
| --- | --- |
| `counts` | General coordinate **integer** peak-count matrix; nonnegative exact integers |
| `expression` | General coordinate real or integer RNA expression matrix; finite values, including signed values where justified |
| `peak_features`, `gene_features` | One unique feature ID per line; no header; exact matrix row order |
| `cells`, `expression_cells` | One unique cell ID per line; no header; exactly equal identities and order between matrices |
| `peak_ranges` | TSV header `chromosome`, `start`, `end` |
| `gene_coords` | TSV header `chromosome`, `start`, `end`, `strand`, `gene_id`, `gene_name` |
| `peak_metadata` | TSV header `peak_id`, `GC.percent`, `sequence.length`, `count` |

TSV headers use actual tabs; the checked files provide literal templates. Peak ranges and gene coordinates are **one-based closed**, within the supported 2^29 coordinate domain. Peak IDs must be exactly `chromosome-start-end`, matching matrix row order. Gene expression feature names use `gene_name`, or `gene_id` with `gene_id: true`. Gene strand is `+`, `-` or `*`; source `*` behavior is retained and warned. Export the study's actual annotation and normalized RNA assay with provenance. Gene activity is an accessibility proxy and must not silently substitute for measured RNA expression.

`GC.percent` must be finite from 0 to 100; `sequence.length` must be positive. The optional `count` column is either blank for every row or supplied for every row. Blank counts invoke the original count calculation. Supplied counts and sequence lengths are retained even when they differ from current matrix totals or genomic widths, with explicit warnings. They affect background matching. The workflow does not inspect a reference genome or compute RegionStats. Review GC/length/count preparation and retain reference/version provenance. The required `genome_build` label is a declaration, not assembly compatibility certification.

Integer peak duplicate coordinates are summed exactly and warned; explicit stored zero entries remain stored. Integer expression tokens must be exact signed integers within 2^53−1; real expression must be finite. Duplicate real expression coordinates are rejected: aggregate them explicitly with a documented rule before retrying. No implicit cell alignment, intersection, ID repair or sparse-zero removal occurs. Seurat name/storage changes are detected and rejected instead of silently accepting modified identities.

The UTF-8 importer keeps character columns and trailing empty fields without R's C-locale re-encoding. A retained earlier Unicode attempt failed safely before publication; the corrected worker passed actual Greek gene/cell tests. Tabs, newlines and invalid identifiers remain outside the format.

## Minimal FIT and request reference

Copy the checked request and set explicit study paths:

```json
{
  "schema_version": 1,
  "mode": "FIT",
  "profile": "1.17.1-package",
  "out": "links-run-NEW",
  "genome_build": "GRCh38-declared-by-investigator",
  "counts": "peak-counts.mtx",
  "peak_features": "peak-features.tsv",
  "cells": "cells.tsv",
  "expression": "rna-data.mtx",
  "gene_features": "gene-features.tsv",
  "expression_cells": "rna-cells.tsv",
  "peak_ranges": "peaks.tsv",
  "gene_coords": "genes.tsv",
  "peak_metadata": "metadata.tsv",
  "rscript": "C:/Program Files/R/R-4.6.1/bin/Rscript.exe",
  "r_library": "private-R-library",
  "r_base_library": "base-R-library",
  "method": "pearson",
  "distance": 500000,
  "min_cells": 10,
  "n_sample": 200,
  "pvalue_cutoff": 0.05,
  "score_cutoff": 0.05,
  "seed": 1
}
```

All fields above other than method/numerical options are required. Unknown fields are rejected. Choose exactly one of optional `genes_use` (array) or `genes_use_file` (one ID per line), or omit both. `gene_id` defaults to false. The CLI has only `FIT`, `VERIFY` and `--help`.

| Option | Default | Admission |
| --- | --- | --- |
| `method` | `pearson` | `pearson` or `spearman` |
| `distance` | 500000 | Integer 0..536870912 |
| `min_distance` | null | Integer −536870912..536870912 or null; negative values ignored by the source and warned |
| `min_cells` | 10 | Integer 0..2000000; eligibility uses strictly more positive cells |
| `n_sample` | 200 | Integer 2..1000000; background pool must support the original request |
| `pvalue_cutoff` | 0.05 | 0..1; strict comparison |
| `score_cutoff` | 0.05 | Finite nonnegative; strict absolute-correlation comparison |
| `seed` | 1 | Integer 0..2147483647 |
| `timeout` | 3600 seconds | Integer 1..86400; timeout stops the owned process tree |
| `maximum_pairs` | 100000 | 1..1000000 |
| `maximum_background_values` | 2000000 | 1..20000000 |
| `maximum_audit_work` | 2000000000 | 1..10000000000000 sparse-work units |

Expression intake defaults to at most two million features/cells, ten million stored entries, two billion bytes per file and 16384 characters per matrix/table line; ID limits are tighter. Peak-count intake inherits the [matrix input contract](SIGNAC_LSI_DEVELOPMENT.md). These are capacity limits, not feasibility guarantees. The upstream calculation can allocate memory before output audit caps are checked. Choose scientifically justified sizes and suitable Java/R memory resources; a capacity or memory failure admits no result. Do not change biological filters merely to fit the machine.

The private reference [setup helper](../../tools/literature/setup_signac_reference.py) supports explicit `--allow-install` into a new workspace directory when the expected base library and R environment already exist. It is not a general installer for every host or historical package version. Read setup logs, supply exact dependencies where permitted, and change the request's explicit runtime paths. Preserve shared and earlier runtime libraries.

## Source geometry and statistics

Eligibility uses `positiveCells > min.cells`. A gene needs at least two cis peaks before correlation filtering. TSS is gene start on plus strand and gene end on minus strand. Cis selection uses whole-peak overlap with the extended TSS window, rather than peak-midpoint distance. Reported links connect the source's integer-truncated peak midpoint to the TSS.

Minimum-distance filtering preserves the original binary distance-matrix subtraction. **If `min_distance > distance`, negative entries become true under R `as.logical`, selecting the outer difference.** The native audit reproduces this source behavior and warns. Review both thresholds; it is not an ordinary empty interval. Negative minimum distance is ignored by the source and warned.

Pearson uses the source sparse covariance calculation. Source sparse Spearman ranks stored entries, then adds `(nCells − nStored − 1)/2`; implicit zeros remain implicit. Signed expression and explicitly stored zeros can therefore differ from conventional dense Spearman. Matrix storage is part of the contract and remains captured.

Candidate pairs require `abs(correlation) > score_cutoff`. Backgrounds are sampled from the profile-specific trans pool, without replacement. Original MatchRegionStats can cap its own sample size for a small pool while LinkPeaks still indexes the requested count, leading to an upstream error. This workflow preserves that failure; it never lowers `n_sample` automatically. Undefined background variation and constant cis-expression cases can also fail. Preserve the failure and review coverage, populations, metadata and justified analysis settings.

For each pair, z is `(candidateCorrelation − mean(backgroundCorrelations)) / sampleSD(backgroundCorrelations)`. Nominal p is the original normal tail described above; retained links require `p < pvalue_cutoff`. Strict cutoffs can yield a valid header-only output.

## Outputs, verification and error recovery

A successful run writes `result-links.tsv` with chromosome/start/end/gene/peak/coefficient/zscore/pvalue and `diagnostics.json`. `WARNINGS.md` and `analysis-status.json` are mandatory reading even after exit 0. The capture includes source and canonical requests, original inputs, normalized sparse exchange, source-profile bytes, runtime path bindings, executable hash, installed-package/session records and worker logs. Worker tables retain all tested pairs, sampled background identities/correlations, ordered pools, effective settings and RNG states; `fit.rds` preserves the upstream object.

```json
{"schema_version":1,"mode":"VERIFY","analysis":"links-run-NEW"}
```

Run this request with the same CLI. Verification needs no R installation. It checks the complete captured file inventory, source bytes against the candidate JAR, request/settings/runtime-path bindings, cell/feature/storage equivalence between inputs and exchange, matching captured RNG replay states, allowed background pools and unique draws, native coefficients, sample SD/z/p values, ranges, retained sets, diagnostics and warnings. It detects tested semantic changes even after checksums are recomputed.

**Verification does not independently reconstruct R KDE weights or replay the R RNG.** Matching saved RNG states and the seeded upstream replay are separate provenance evidence. Captured hashes detect changes under these checks; they are not signatures or proof that a capture is authentic. Complete runtime DLL/host closure, biological interpretation and regulatory causality are not certified.

| Status | What to do |
| --- | --- |
| Exit 0, `completed-with-warnings` | Review warnings and diagnostics, verify the capture and assess study-specific inference |
| Exit 3, `skipped-dependency-unavailable` | Provide the named exact runtime only where permitted; otherwise retain the skip and skip dependent analyses |
| Exit 2, `failed` | Preserve attempt/logs; correct the named format, identity, background, numerical or capacity cause; rerun to a new directory |
| Request rejected before output creation | Read stderr; correct the request/input path or schema and use a new destination |
| VERIFY fails | Keep capture for investigation; do not use its table as an admitted result |

Timeout and Java-thread interruption were tested with an actual owned R process. Captured PIDs were checked directly with process creation times after termination, so the check does not depend on a surviving parent. No unrelated R process is targeted. An interrupted request preserves the thread interruption flag. Partial and failed evidence remains retained.

## Validation and remaining scope

The [frozen evidence](signac-link-development/VALIDATION.json) and its warnings distinguish each check:

* Sixty original controls across the two profiles: 44 successful calculations and 16 expected source failures, including strict filters, nominal cutoffs, empty results, supplied counts, signed/stored-zero expression and distance edge cases.
* 109 native tests on each of Windows Java 21, Windows Java 25 and Linux Java 21, with no skips. They include direct original audits, Matrix Market round trips, altered evidence, bounded intake, cancellation and numerical/source boundaries.
* All 60 fresh workflow cases against the final candidate: 44 original result/RNG matches, 16 retained failure gates and 132 successful captured verifications.
* An actual Unicode gene/cell fit launched from Windows Java 25 and three captured verifications; the earlier C-locale import failure remains retained.
* Fifty-four rehashed semantic-corruption rejections, three unlisted nested-file rejections, preserved existing destinations, two actual dependency skips and actual timeout/interruption cleanup checks.
* Three literal checked FIT examples compared with original numerical results and RNG states, nine platform verifications and execution of the literal current VERIFY request.

Numerical comparisons retain explicit tolerances: workflow coefficients/z use relative 2e−11 or absolute 2e−12; p uses relative 1e−9 or absolute 1e−300. Native semantic checks use their recorded coefficient/z/log-p tolerances. Feature IDs, link geometry, retained sets and RNG arrays are exact. These bounded synthetic and original-source controls are not a claim of validation on every study dataset.

Failed and superseded attempts are preserved. Parent milestone sources, both R-library inventories, the canonical executable and the catalog are rechecked unchanged. Counts remain **68 new integrations, 71 including existing tools, and 259 missing**. Signac remains missing in the full-role catalog until its remaining roles, public dispatch and paper-specific gates are complete. Cell Ranger remains excluded at the user's request. The 250-paper implementation goal remains active.

Primary references: [official LinkPeaks reference](https://stuartlab.org/signac/reference/linkpeaks), [official changelog](https://stuartlab.org/signac/news/) and [upstream releases/source](https://github.com/stuart-lab/signac/releases). Version-specific retained functions and their original full R source files are included with the evidence.
