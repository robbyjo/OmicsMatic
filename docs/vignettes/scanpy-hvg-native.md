# Native Scanpy dispersion-based highly variable genes

`org.jlinalg.singlecell.ScanpyHighlyVariableGenes` implements the float64
dispersion-based `seurat` and `cell_ranger` flavors, with explicit Scanpy
1.9.3 and 1.13.0 profiles. It supports dense/CSR storage, unbatched and
batch-aware selection, strict cutoffs or top-gene selection, and the original
DataFrame versus AnnData annotation precision. Computation needs Java only.

Start with the [runnable example](../../examples/scanpy-hvg/README.md) and
[saved component/restorer](../literature/scanpy-hvg-checks/README.md).
Published 0.4.0 predates this API; build current source or restore the exact
small development library. This API does not fit Seurat-v3 LOESS, select
Pearson-residual HVGs, or replace an AnnData host, plotter or author pipeline.

## Inputs and use

Use the immutable `SeuratPreprocessing.Matrix` container: unique ordered
feature/cell IDs, finite real values and sorted unique CSC entries in
features-by-cells orientation. This is transpose storage for the source's
cells-by-genes CSR. Declare `Storage.CSR`, or `DENSE` only when every entry,
including every zero, is explicitly stored. Do not silently sort, merge,
rename, round or coerce an input to satisfy the contract.

The source expects logarithmized expression for these dispersion flavors.
Seurat first multiplies by `log(base)` when that metadata is supplied, then
applies `expm1`. Cell Ranger uses the supplied expression values directly;
it does not receive an inferred inverse transformation. This API does no
automatic normalization or library-size correction. State the actual input
preparation and log base. The Cell Ranger *flavor* is separate from the
excluded Cell Ranger program.

For example, after creating the explicit input matrix:

```java
var options = new ScanpyHighlyVariableGenes.Options(
    ScanpyHighlyVariableGenes.Flavor.SEURAT, 2000, 20,
    0.0125, 3.0, 0.5, Double.POSITIVE_INFINITY,
    null, null, ScanpyHighlyVariableGenes.Export.ANNDATA);
var result = ScanpyHighlyVariableGenes.select(
    matrix, ScanpyHighlyVariableGenes.Storage.CSR,
    ScanpyHighlyVariableGenes.Profile.SCANPY_1_13_0,
    options, null, ScanpyHighlyVariableGenes.Limits.defaults(), () -> false);
if (!result.usable()) throw new ArithmeticException("Inspect retained HVG diagnostics/warnings");
boolean[] keep = result.selected();
```

`topGenes=null` selects on the four strict inequalities. A positive top-gene
count takes precedence over all cutoffs, as in the source; ignored custom
cutoffs are warned about. The API requires a positive count, positive bins
and a declared valid log base; invalid counts/bases are not repaired.
`Export.DATAFRAME` preserves float64 normalized dispersion.
`Export.ANNDATA` casts that annotation through float32 **after selection**.
The result is a typed immutable table/mask, not an AnnData object or automatic
subset. `selectedFeatures()` preserves input feature order.
An empty mask can be a valid source outcome. Check its size before downstream
models that require genes; do not silently relax thresholds to obtain input.

For batch-aware selection, supply `Batches(labels, categories)` with one
explicit label per cell and explicit unique category order. Unknown, missing
or unused empty categories are not silently dropped. Each batch applies the
source's unexpressed-gene filter; filtered rows receive the source zero fields
before aggregation. Current `filterUnexpressed=false` is overridden for batch
selection with a warning. The 1.9.3 profile refuses this newer optional
parameter. It must not silently become a current-edition request.

## Source distinctions retained

- Seurat mean bins use the source equal-width/end-extension rules and pandas
  mean/sample-SD reductions, including the singleton-bin substitution.
- Cell Ranger uses percentile bins, median centers and the scaled statsmodels
  MAD convention. Duplicate edges remain a failure; bins are not deduplicated.
- The legacy CSR profile uses centered sparse variance; current CSR uses the
  original second-moment calculation with one numerical thread. No variance
  floor replaces cancellation or negative/undefined variance.
- Unbatched top selection includes all ties at the threshold. Legacy normalized
  NaN-to-zero selection can include zero-variance genes with a nonpositive
  threshold; current top selection maps NaN to negative infinity instead.
  Cutoff-mode diagnostic annotation changes also retain their edition's rules.
- Final batch ranking prioritizes the number of selected batches, then average
  normalized dispersion. Ties follow legacy gene-name order or current gene
  position. Final batch cardinality is bounded by the requested count/full gene
  population; large requests can include genes absent from individual batches.
- The original 1.9.3 array/log-base path can mutate its supplied object. This
  Java API keeps its declared immutable input and warns about that object-API
  difference while retaining the original statistical operation order.

`batchDiagnostics()` retains full-precision per-batch values, bin edges/codes,
filter flags, diagnostic masks and explicit zero-variance missing flags.
Seurat's deliberate missing zero-variance annotations remain diagnostic NaNs
with a named warning. They are not a generic permission to admit nonfinite
model arithmetic. Negative/invalid model terms, lost positive terms, undefined
bin normalization and float32 range failure prevent complete selection, even
when later masking/aggregation looks finite. `diagnosticSelected()` is retained
for comparison; it is not an admitted downstream selection when `usable=false`.

## Warnings and required actions

**FAIL2/no complete selection:** preserve the original matrix, identities,
options, warnings and diagnostics. Correct the named schema, population,
arithmetic or capacity cause and retry a NEW analysis. The API refuses usable
selection through an exception or `usable=false`; callers must not consume a
diagnostic mask as a successful analysis. Do not fill NaN/Inf, add a variance
floor, relax a cutoff, change flavor/edition or drop categories silently.

Dimensions, complete work and workspace have explicit limits. Work estimates
and cancellation account for cell scans even in very sparse batches. Thread
interruption is preserved and no partial selection is admitted.

**SKIP3:** native computation requires an available Java 17+ runtime, not Python,
Scanpy or statsmodels. If a needed runtime/validation dependency cannot be
installed or executed where allowed, skip its dependent analysis/audit and
name the exact item to restore. Fresh original-reference capture separately
requires its exact permitted Python stacks. The saved proof reader requires
Python 3.11+ but runs no scientific model or installation.

The [checkpoint](../literature/SCANPY_HVG_CHECKPOINT_2026-11-19.md) records
original comparisons, retained failed attempts, transfer paths and remaining
qualification/admission work. These synthetic controls do not establish
whole-package parity, historical author execution or biological validity.
