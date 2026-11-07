# Signac native normalization development

This development milestone implements `RunTFIDF` count arithmetic and
`FindTopFeatures` metadata and selection in Java. It is a native API foundation;
Signac remains **missing** in the implementation catalog until its other paper
roles and a public workflow are validated. The canonical executable and public
website are unchanged.

**Warnings and required actions**

| Condition | What was skipped or restricted | Required action |
| --- | --- | --- |
| **SKIP3: historical assay wrappers** | Signac 1.6.0, 1.7.0 and 1.9.0 `FindTopFeatures.Assay` wrappers cannot run on the installed SeuratObject 5.4.0: their `slot` argument is defunct. Their matrix arithmetic and metadata functions were tested separately. | Skip historical whole-assay execution on this runtime. If exact historical assay reproduction is needed, prepare an isolated, compatible historical SeuratObject installation and validate it. Preserve the original source and failed attempt; do not suppress the error or silently rewrite `slot` as `layer`. |
| **Installation index warnings** | The Bioconductor 3.23 annotation repository returned 404s for Windows binary indexes. Required software packages still installed and full Signac 1.17.1 loaded. No species genome/annotation analysis was attempted. | No repair is needed for these native normalization methods. Before a future annotation-dependent analysis, use a compatible, permitted annotation repository/package installation and verify that the required genome and annotation actually load. Otherwise report the missing package and skip that analysis. |
| **Native input restriction** | Fractional counts, nonpositive/nonfinite supplied IDF or scale, malformed CSC coordinates, and feature/cell totals above 2^53−1 are rejected. | Supply justified nonnegative integer counts with sorted unique coordinates and exact totals within the bound. Use an independently validated upstream route for a different input domain; never round or reinterpret counts silently. |
| **Empty counts / overflow** | Empty cells and computed empty-feature IDF produce named warnings. NaN stored results become zero as in the source. Infinity is retained with `NONFINITE_NORMALIZED_VALUES`. | Review empty-cell/feature QC. Skip downstream calculations requiring finite values when infinity is present; inspect scale and supplied IDF, correct the justified cause and rerun. Never silently clip infinity. |
| **Remaining workflow scope** | No LSI, fragment counting, nucleosome/TSS QC, gene activity, motifs, peak–gene linkage, differential accessibility, or coverage plotting is provided by this milestone. | Continue the typed upstream adapter and role-specific input/output validation before admitting these analyses. A working reference package is not workflow admission. |

The complete warnings, original errors, package versions and recovery instructions
are retained in [the evidence directory](signac-normalization-development/).

## What the methods do

Counts are an immutable compressed sparse column (CSC) matrix: features are rows,
cells are columns, and pointers delimit each column's stored entries. Sorted
unique row indices are mandatory. Explicitly stored zero entries remain stored;
implicit zeros do not become a dense matrix. IDs are preserved exactly within the
documented identity domain. The API checks configurable dimensions and entry
bounds and supports cancellation. It does not read or canonicalize Matrix Market
files automatically.

Computed IDF is **number of cells / total counts for each feature**. It does not
use the number of nonzero cells. For methods 1–3, TF uses counts multiplied by the
reciprocal of each cell's total. With `s` the scale:

| Java method | Source formula for stored values |
| --- | --- |
| `LOG_TF_IDF` (1) | `log1p(IDF × TF × s)` |
| `TF_LOG_IDF` (2) | `log(1 + IDF) × TF` |
| `LOG_TF_LOG_IDF` (3) | `log(1 + IDF) × log1p(TF × s)` |
| `RAW_COUNT_IDF` (4) | `IDF × raw counts` |

For supplied IDF, methods 2 and 3 use the supplied values directly: **no second
log transform** is applied. Scale affects methods 1 and 3 only. Arithmetic follows
the retained source expressions; tests allow a relative tolerance of 3e−13 or
16 ULP, whichever is larger, for finite R/Java math-library results.

`featureMetadata` returns every feature, ordered by decreasing total counts,
with original order preserved for ties. Percentile is the empirical cumulative
distribution at the count, including ties. The upstream matrix method ignores
`min.cutoff`; filtering belongs to its assay wrapper. `findTopFeatures` reproduces
that wrapper's selection rule without creating or mutating an assay:

- `Cutoff.count(5)` retains counts **strictly greater than 5**.
- `Cutoff.percentile(5)` corresponds to `q5` and retains ECDF **strictly greater than 0.05**.
- `Cutoff.all()` retains all features in metadata order.
- `Cutoff.keepExisting()` retains the supplied existing feature IDs and their order.

This uses the upstream source from [CRAN's Signac archive](https://cran.r-project.org/src/contrib/Archive/Signac/)
and the retained current source; see also the official
[RunTFIDF](https://stuartlab.org/signac/reference/runtfidf) and
[FindTopFeatures](https://stuartlab.org/signac/reference/findtopfeatures) references.
The original source and MIT notices are included in the fixtures and evidence.

## Run the small native example

From the repository root in PowerShell, with JDK 21 installed and the validated
development artifact still present:

```powershell
$signacJar = 'build/literature-review/signac-runtime/normalization-matrix-1/omicsmatic-0.3.7-signac-normalization-development.jar'
$signacClasses = 'build/signac-example-classes'
New-Item -ItemType Directory -Path $signacClasses -Force | Out-Null
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' --release 17 -encoding UTF-8 -cp $signacJar -d $signacClasses examples/signac-normalization/NormalizePeaks.java
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' -cp "$signacClasses;$signacJar" NormalizePeaks
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' -cp "$signacClasses;$signacJar" NormalizePeaks --empty-cell
```

The example prints normalized stored coordinates and selects `peak-C` using
`count > 5`. The second run also prints `WARNING ZERO_CELL_COUNTS` and a required
action to stderr. Including an empty column changes computed IDF because IDF uses
the number of columns, just as the source does. This example uses synthetic data;
it performs no biological analysis or external process execution.

The candidate is 164,371,746 bytes, SHA-256
`3f1a7d71ddd2678c9d62da8c42dfc16f64457b8a9d4f39a3102aac7de3601065`.
It adds 12 classes and preserves all 19,619 entries from the validated StringTie
parent artifact. The unchanged canonical executable does not contain these new
classes. A fresh checkout must build this development candidate using the
validation command below; it is not a released executable.

```powershell
# Use a NEW directory for every attempt; retain prior successes and failures.
python tools/literature/validate_signac_normalization.py --out build/literature-review/signac-runtime/normalization-matrix-NEW
python tools/literature/validate_signac_example.py --out build/literature-review/signac-runtime/normalization-example-NEW
```

These developer validators require the previously validated parent JAR, pinned
JUnit dependencies, Windows JDKs 21/25 and the named WSL Java 21 installation.
The example validator deliberately checks the frozen `normalization-matrix-1`
candidate. The matrix validator creates a new candidate without modifying the
canonical executable. Missing prerequisites are an error; do not call an
unexecuted validation a pass.

## Evidence and paper coverage

The full installed Signac **1.17.1** package was used for 1,152 normalization
cases, 36 metadata cases and 396 assay-selection cases. Five extracted source
editions—1.6.0, 1.7.0, 1.9.0, 1.14.0 and 1.17.1—produced identical normalization
and metadata results for these inputs. The current binary's three tested
function bodies matched retained CRAN source. Two extracted newer assay wrappers
also matched; the three older wrappers have the explicit skips above. These are
not five historical whole-package installations.

The native candidate passed **1,605 tests on each of Windows Java 21, Windows
Java 25 and Linux Java 21**, with no skipped native tests. This includes the
original controls, invalid-input and cancellation checks and a 50,000-by-50,000
sparse matrix with 50,000 stored entries under a 256 MiB Java heap. Javadoc and
Java 17 compilation with warnings as errors passed. Nine documented-example
runs/checks passed across the same platforms, including warnings and rejected
arguments. These tests do not establish biological accuracy or full workflow
equivalence.

All 250 primary XMLs were rescanned. Nine cataloged Signac software papers and
one additional actual software use were found, plus a separate institutional
`SignAC` name collision. The additional P134 use is retained for catalog curation;
the existing observation counts were not silently changed.

| Paper | Source role and remaining scope |
| --- | --- |
| P016 | Signac 1.6.0 / Seurat processing and integrated multiome workflow; full assay and integration validation remains. |
| P039 | Signac 1.9.0: explicit `RunTFIDF`/`RunSVD`, peak quantification, gene activity and motifs/chromVAR. The normalization foundation covers only the first arithmetic component. |
| P051 | Signac 1.14.0: explicit TF–IDF, numeric feature cutoff 5 and SVD, plus QC and common peak quantification. SVD and the other roles remain. |
| P094 | Signac 1.14.0 preprocessing and `LinkPeaks`; linkage remains. |
| P099 | Signac 1.7.0: explicit TF–IDF/SVD and LSI dimensions 2:30; SVD and other workflow steps remain. Monocle and chromVAR roles remain separately attributed. |
| P134 | Figure caption explicitly reports Signac logistic regression for differentially accessible peaks; newly found use, pending catalog curation and adapter implementation. |
| P153 | Standard processing, `FeatureMatrix`, `BinarizeCounts`, and a correlation-model benchmark; counting, binarization and model-specific reproduction remain. Custom Kendall correlation and Seurat RNA normalization are separate roles. |
| P158 | Signac/Seurat downstream processing, QC and gene-activity-based annotation; these complete roles remain. |
| P187 | Liver ATAC counts and TSS/fragment QC; these roles remain. |
| P225 | Signac 1.9.0 preprocessing, QC, motifs/chromVAR and coverage; full role validation remains. Harmony and SCENIC remain separate tools. |
| P007 | `SignAC` is a Kyoto research-core name in acknowledgements; it is excluded from software-use attribution. |

R installation was isolated to
`build/literature-review/signac-runtime/reference-setup-1/R-lib`; the existing
23,902-file SCTransform library was unchanged. Installer warnings, the initial
failed wrapper control, the initial name-collision audit failure, corrected
audits and successful controls are retained. No real study analysis, public CLI,
catalog promotion, release, deployment or GitHub push is claimed by this milestone.
The broader implementation goal remains active.
