# Native Seurat matrix preprocessing

`org.jlinalg.sequence.SeuratPreprocessing` implements **LogNormalize, relative
counts (RC), CLR with either margin, and the unsplit, no-regression sparse
ScaleData path** in Java. It follows the pinned
[Seurat 5.6.0 source](https://github.com/satijalab/seurat/tree/75dfa8603befe31505e6287d32dc86c607732bd6),
not an inferred author edition or the newest online manual. No R or external
runtime is needed to use these native operations.

Start with the [copyable synthetic example](../../examples/seurat-preprocessing/README.md).
It compiles only the public API and example into a new private classes
directory, so it also works on a fresh clone without a released toolbox JAR.
Use Java 17+; qualification ran the packaged code and standalone example on
Windows and Linux Java 21. The public 0.4.0 release predates this API.

This is a matrix component, not the full Seurat workflow. SCTransform,
variable-feature selection, regression/split-aware scaling, assay construction,
PCA, integration, clustering and complete paper-role review remain open.
Seurat stays method-overlap in the catalog. Counts remain 130 scoped
integrations (24 native Java + 106 upstream), with 1,405 observations awaiting
individual review. No new family credit is taken for this component.

## Inputs and results

`Matrix` is immutable compressed sparse columns: **features by cells**. Supply
ordered feature/cell IDs, column pointers, sorted unique zero-based row indices
and finite real stored values. Implicit entries are measured zeros. Explicitly
stored zeros are retained. IDs are opaque, exact, unique and nonblank, without
control characters. No name repair, coordinate merging or sorting occurs.

Normalization accepts nonnegative counts or explicitly supplied nonnegative
abundances. It does not infer their scientific units or recover raw counts.
ScaleData also accepts signed finite prepared values. The complete supplied
feature/cell population defines denominators; no automatic QC or feature/cell
selection precedes normalization.

```java
var limits = SeuratPreprocessing.Limits.defaults();
var normalized = SeuratPreprocessing.logNormalize(input, 10000, limits, () -> false);
for (var warning : normalized.warnings()) {
    System.err.println(warning.code() + ": " + warning.message()
        + " ACTION: " + warning.requiredAction());
}
var data = normalized.matrix(); // throws if no usable complete matrix exists
var scaled = SeuratPreprocessing.scaleData(data,
    List.of("geneC", "geneA"),
    SeuratPreprocessing.ScaleOptions.defaults(), limits, () -> false);
var output = scaled.matrix();
```

Print and inspect warnings from both operations. Normalized usable matrices
retain the original sparse coordinates; CLR's numerical values are retained
sparsely although the original R CustomNormalize returns a dense matrix.
Scaled usable matrices are dense, column-major, with explicit ordered IDs and
`value(feature, cell)` / `valuesColumnMajor()` accessors. Output arrays and
metadata cannot mutate the captured input or result.

## Source behavior retained

| Operation | Declared behavior |
|---|---|
| `logNormalize(matrix, scale, limits, cancelled)` | Seurat 5.6.0's C++ LogNorm converts scale to a signed integer and computes `log1p(x * (integerScale / columnSum))`. The API retains declared/effective scales and warns on fractional truncation. Scale must be finite, nonnegative and within signed-int range. |
| `relativeCounts(matrix, scale, limits, cancelled)` | Uses a real finite nonnegative scale and `(x / columnSum) * scale`. A fractional RC scale is not coerced to an integer. |
| `clr(matrix, 1, limits, cancelled)` | Transforms each feature across cells. The denominator is `exp(sum(log1p(positive values)) / allCells)`, including implicit zeros in the population size. |
| `clr(matrix, 2, limits, cancelled)` | Transforms each cell across features, with all features in the population size. It does not mean the same direction as margin 1. |
| `scaleData(matrix, features, options, limits, cancelled)` | Features are explicit exact IDs in requested order; `null` means all. Center/scale are independent. Scaling without centering uses `sqrt(sum(x*x)/(cells-1))`; with centering it preserves the sparse source's sum-of-squares formula. |
| `ScaleOptions(center, scale, maximum)` | Source clipping is **upper-only**, even if both flags are false. Negative lower-tail values are not symmetrically clipped. Positive infinity disables the cap. NaN caps are refused. |

Unknown, duplicate or empty selected features are refused rather than silently
intersected away as in the R wrapper. The original singleton CLR shape errors
are retained as explicit refusals: margin 1 with one cell and multiple
features; margin 2 with a single feature. No dimension repair occurs.

Zero standard-deviation rows follow the named source rule and stay zero with
a warning. Undefined/overflowed scaling statistics are different: the original
may emit zero or nonfinite diagnostic values, but the native result refuses a
usable matrix. Source numerical cancellation is not silently replaced with a
different variance algorithm.

## Warnings, failures and recovery

**No complete matrix when `usable()` is false.** `matrix()` throws an
`ArithmeticException`; `diagnosticValues()` or
`diagnosticValuesColumnMajor()` still exposes the retained original arithmetic
for diagnosis. A finite-looking source output is not sufficient: an overflowed
total or undefined scaling statistic also refuses admission. Do not feed
diagnostic values into downstream analysis or label them a complete result.

**Zero-total columns are retained.** An empty column with only implicit zeros
can remain zero; a stored zero divided by zero can produce NaN. Inspect input
units and the supplied population. Do not erase coordinates, drop columns,
replace NaN/Inf or impute values merely to make an analysis pass. Correct the
identified scientific/input cause and retry; retain the original failed input
and diagnostics. Single-cell standard deviations are undefined; they are not
valid scaling results simply because the source returned zeros.

Malformed coordinates, duplicate identities, negative normalization inputs,
unknown selected features and exhausted capacities raise explicit exceptions.
Cancellation raises `CancellationException` and returns no partial result.
The SDK does not create analysis directories or issue workflow completion
receipts. A caller integrating it into a study must retain inputs/warnings and
use the complete workflow's admission contract.

`Limits` covers full feature, cell, stored-value, dense-output and work
populations. Defaults are 5,000,000 features, 10,000,000 cells, 100,000,000 stored
values, 20,000,000 dense entries and 1,000,000,000 work units. Copies and dense
output still need sufficient JVM heap. These are caller admission bounds, not
OS memory quotas or evidence of atlas-scale performance. No limit truncates
the requested population. Supply a cancellation callback when needed.

## Original references and verification

The closed reference has 13 synthetic input/prepared datasets and **298
original requests: 90 normalizations and 208 scalings**. Three requests retain
original singleton-shape errors. All 295 returned source matrix views were
compared, including invalid diagnostic views: 15,081 primary dense fields plus
920 normalization/subset-scaling pipeline fields. There are 247 usable native
matrices and 48 diagnostic-only refusals. The 13 packaged tests pass on each
qualified JVM. Kind/identity/coordinate/order checks are exact; finite values
use relative tolerance `3e-12` or 64 ULPs, whichever is larger. Tiny nonzero
values are not accepted merely under a large absolute floor.

The reference runtime was **Windows R 4.6.1, Seurat 5.6.0, SeuratObject 5.4.0,
Matrix 1.7.5**. Six installed R function bodies/formals matched the pinned
official source. This does not authenticate the whole namespace, DLLs or host.
Original R fitting/execution was not qualified on Linux; the Java computation
and example were. Hash checks establish integrity, not producer authenticity
or biological truth. No hypothesis test, historical author runtime or whole
paper is reproduced by these controls.

To inspect saved source/evidence without running R or Java tests:

```powershell
python -B tools/literature/seurat_preprocessing/verify.py --out build/seurat-preprocessing-audit-new
```

To recapture the original R controls, use Python 3.11+ and explicit permitted
runtime/library paths, for example on this workstation:

```powershell
python -B tools/literature/seurat_preprocessing/collect.py --execution allow --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe" --r-library D:/projects/JLinAlg/build/literature-review/signac-runtime/reference-setup-1/R-lib --r-library D:/projects/JLinAlg/build/omics-sctransform-r --out build/seurat-reference-new
```

**SKIP3 skips this dependent reference capture** if execution is forbidden or
the exact runtime/library/package is unavailable. Supply the named permitted
prerequisite or leave capture skipped. The collector never installs packages
or substitutes versions; native Java use needs none of these R prerequisites.
**FAIL2 admits no complete reference capture.** Read retained consoles and
`FAILURE.json`, preserve partials, correct the named cause and retry a new path.
Recapture alone does not perform native qualification. The tested collector
reproduced both JSON and original RDS bytes and exercised four genuine skips.

Read the [qualification](../literature/seurat-preprocessing-development-v2/README.md)
and [current checkpoint](../literature/SEURAT_PREPROCESSING_CHECKPOINT_2026-11-19.md).
The prior marker-summary and differential interfaces remain separately scoped.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Satija Lab (2026) — Seurat documentation and tutorials](../CITATIONS.md#seurat-api-documentation)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
