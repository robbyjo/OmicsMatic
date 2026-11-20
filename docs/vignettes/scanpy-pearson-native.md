# Native analytic Pearson normalization

`org.jlinalg.singlecell.ScanpyPearsonResiduals` implements complete float64
dense/CSR normalization profiles for **Scanpy 1.10.4 and 1.13.0** in Java.
The [example](../../examples/scanpy-pearson/README.md) runs without Python,
subprocesses or network access. The public 0.4.0 binary predates this API;
build current source or restore the small qualified development library.

This analytic negative-binomial offset model uses the cell and feature
marginal totals to form the expected count: `mu = cell_total * gene_total /
grand_total`. The unclipped residual is `(count-mu)/sqrt(mu+mu*mu/theta)`.
Theta is shared across genes, defaults to 100, and may be positive infinity
for the source's literal Poisson-limit arithmetic. The default symmetric
clipping bound is the square root of the number of cells. There is no fitted
gene-wise SCTransform model, variance floor, regression, HVG selection or PCA.

## Declared representations and version behavior

Use the immutable `SeuratPreprocessing.Matrix` container, features by cells in
sorted unique CSC storage. It is the transpose representation of source cells
by genes in CSR. Choose `Storage.DENSE` only when all coordinates, including
zeros, are stored; choose `Storage.CSR` explicitly for the sparse source path.
Inputs must be finite, nonnegative float64 counts, with exact unique identities
and order. Integer/float32 casting and AnnData layer/raw metadata are separate
scopes; an author's unknown dtype is not inferred. Noninteger values follow
the source's optional warning behavior without integer rounding.

The output is dense because unstored zero counts generally have **negative
residuals** when their expected count is positive. The method covers the full
cell-by-feature population, not only stored entries. Returned means/totals and
diagnostic values are defensive copies; output identities/order are unchanged.

The original [1.10.4 source](https://github.com/scverse/scanpy/blob/1.10.4/src/scanpy/experimental/pp/_normalization.py)
divides directly, so zero expected counts yield undefined residuals. The
original [1.13.0 source](https://github.com/scverse/scanpy/blob/3962176e93ba148bc0f61443c517877d736f2b3b/src/scanpy/experimental/pp/_normalization.py)
assigns zero residuals where mu is zero, including an entirely zero input.
Both behaviors are retained explicitly. The latter convention is labelled;
a zero residual is not evidence of positive expression. The 1.10.4 profile
refuses a complete matrix when its original residuals are nonfinite.

The source operation order is retained, including product before division and
square before division by theta. The existing scalar NB helper uses supplied
means and an explicit variance floor, and is not silently substituted. A
finite-looking clipped output does not validate nonfinite variance, unclipped
residuals, overflowing totals or lost positive expected counts. Such source
values remain diagnostics, with `usable=false` and no complete `matrix()`.

## Evidence and limits

All **735 checks pass on each of Windows Java 21/25 and Linux Java 21**,
including **720 actual original-package cases** and 15 admission/capacity/
cancellation/identity controls. References ran full isolated Scanpy 1.10.4
with declared legacy dependencies and the retained 1.13.0 runtime. All 37,676
runtime entries remained unchanged during each capture phase. This is not an
author-runtime or whole-paper reproduction claim. Case fixtures preserve raw
original parts and explicitly derived combined views; no scientific source or
original result was repaired. A prior capture-helper syntax failure occurred
before import/analysis and remains retained.

Cases cover dense input, implicit sparse zeros, explicitly stored zeros,
fractional counts, wide pairwise reductions, zero cells/genes, one-cell and
one-gene populations, theta/clip boundaries, underflow, overflow and finite
clipping that hides undefined model arithmetic. Finite source outputs are
checked with the larger of 64 ULPs and `8e-12 * max(1, abs(reference))`; NaN/Inf
classes are checked separately. Arbitrary ill-conditioned, cross-library
bitwise or full-package equivalence is not asserted.

The component's exact class payloads come from the strict normal Gradle JAR;
all 20,202 previous class payloads remain byte-identical. The small exported
library has only `java.base` dependencies and includes unchanged shared matrix
classes plus original GPL/BSD notices. Tests load native production classes
only from that component, with a JDK-only test JSON parser and no full-project
production fallback classpath. Three runnable example executions passed.
See the [saved proof/restorer](../literature/scanpy-pearson-checks/README.md).

Defaults bound one million features, ten million cells and stored entries,
20 million dense entries, one GiB estimated workspace and one billion work
visits. Complete dimensions and conservative workspace/work estimates are
checked before copies/output allocation. No truncation or implicit filtering
occurs. Cancellation and thread interruption refuse a complete result.
Callers need memory for defensive copies and output materialization as well.

## Warnings and required actions

**Inspect `usable()` and `warnings()` before downstream use.** Keep raw counts,
declared storage/dtype/edition/options, source diagnostics and failed attempts.
For a historical zero-count failure, use only preprocessing explicitly allowed
by the scientific protocol; do not silently remove genes/cells, replace NaN
with zero, switch to a newer edition or introduce a variance floor.

Zero clipping deliberately removes all finite residual variation and receives
an explicit warning. Noninteger count checks can be disabled only explicitly.
Nonfinite model terms, positive expected-count underflow and invalid input/
capacity/interruption produce library failures or an unusable diagnostic result;
they admit no complete matrix. Correct the named cause and retry a new analysis.

The saved reader uses SKIP3 for exact missing/unreadable permitted evidence
dependencies and FAIL2 for changed/invalid evidence. Restore the named item or
leave its dependent audit skipped; never refresh historical hashes. The Java
library itself needs no optional original runtime installation. Historical
reference environments and large normal candidates require separate transfer,
as recorded in the [checkpoint](../literature/SCANPY_PEARSON_CHECKPOINT_2026-11-19.md).
