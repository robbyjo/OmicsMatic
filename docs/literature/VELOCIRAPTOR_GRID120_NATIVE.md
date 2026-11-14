# Native Java velocity-grid arithmetic

`org.jlinalg.singlecell.VelociraptorGrid120` implements the two-dimensional
arithmetic of velociraptor 1.20.0 `gridVectors`. It averages supplied coordinates
and projected velocity vectors in occupied grid bins. Calling this Java API
requires no R or Python runtime.

**This component does not fit RNA velocity, create an embedding or calculate
latent time. At this native component checkpoint (14 November 2026), the separate
R wrapper is missing/unqualified and this component adds zero family
implementation credit.** For current wrapper availability, read the
[current catalog](omics-2023-2026/README.md). P228 reports use
of the R wrapper for RNA velocity/latent-time analysis; it does not establish
use of this grid routine, author editions or the settings in these controls.

The implementation is available in this source checkout for a new library
build. The three project tests and isolated library JAR packaging passed;
packaged class-origin, complete example and guard checks passed as well. The
existing released/canonical CLI JAR has not been rebuilt for it.
There is no new standalone CLI command.

## Example

```java
import org.jlinalg.singlecell.VelociraptorGrid120;

double[][] coordinates = {
    {0, 0}, {.1, .1}, {1, 1}, {.1, .1}, {.9, .1}, {0, 0}
};
double[][] projectedVelocity = {
    {1, 2}, {3, 4}, {-1, 2}, {2, -3}, {0, 1}, {5, 2}
};

var grid = VelociraptorGrid120.grid(coordinates, projectedVelocity, 1, false);
for (int bin = 0; bin < grid.bins(); bin++) {
    System.out.printf("Group %d contains %d cells%n",
        grid.groupLabelOneBased(bin), grid.population(bin));
}
// Group 1 contains 5 cells
// Group 3 contains 1 cells

if (grid.requiresNonFiniteReview()) {
    throw new IllegalStateException(
        "Review the original vectors and median-length scaling before using this grid");
}
```

The labels are original first-matching-cell positions, starting at one; gaps
are retained. `binOfCell(cell)` gives the zero-based occupied-bin row for array
lookup. `groupOneBased(cell)` retains the original label. Cells and axes in all
accessors are zero-based, and every supplied row participates.

`start`, `end`, `vector`, `limit`, `delta` and `category` expose all arithmetic
intermediates. `population` supplies the original per-group count. Returned
values do not alias caller arrays. The two-argument `grid` overload selects the
original integer resolution 40 and median-length scaling.

## Supported inputs and required actions

Supply matching nonempty matrices with exactly two finite values per row and
a positive integer resolution. Both coordinate axes must have nondegenerate
finite ranges and representable grid spacing/categories. Group sums and
lengths required by scaling must not overflow.

**Unsupported native domains reject the complete calculation with an explicit
instruction to use the original R workflow.** Keep all original rows and values.
Use an appropriately qualified, permitted original route for unsupported
domains, or skip dependent work while that route is unavailable. Do not remove
cells, add pseudocounts, clip categories or repair values to obtain a native pass.

**Zero median vector length can produce NaN or infinity, as in the original
arithmetic.** These values remain in the result, and
`requiresNonFiniteReview()` becomes true. Keep them visible and review the
underlying vectors/scaling before plotting or reporting results. They are not
replaced with zero. Unscaled calculations do not impose scaled-length checks.

## Source comparison and tests

The native method was compared against 31 complete original R 4.5.3 controls
from the unchanged velociraptor 1.20.0 release. Cases include duplicate cells,
nonconsecutive group labels, negative coordinates, terminal categories,
cancellation, zero vectors/median, large unscaled vectors and 513-cell random
inputs at several explicit resolutions. All 279 returned arithmetic fields
were compared: 14,547 integer values, 5,736 finite FP64 values and 48 nonfinite
value classes. Finite values were bit-identical in this run; the declared
cross-runtime numeric tolerance is 1e-12 absolute/relative. Generated NaN bit
identity is not a universal claim. Thirty-one additional guard/input-immutability
checks passed.

The original full RDS objects, conditions, binary FP64/int32 fixtures, source
archive, R dependency lock and producer code are in
`src/test/resources/velociraptor-grid120-reference.zip`. Numeric execution and
acceptance use binary payloads. Human-readable JSON is not a rounded numeric
substitute. The fixed archive contains original R results, with no Java output
used as a golden value.

**The original R grid reference emitted a native stack-imbalance diagnostic;
its cause is unresolved.** Its full console and explicit review record remain
in the archive. The recorded zero delivered R-warning count covers handlers
around the grid calls only; raw-console diagnostic totals are unavailable and
review is required. Original source, warnings and failed exporter evidence were
retained. Matching numerical controls do not establish correctness of the
entire R runtime, biological interpretation, convergence or paper reproduction.
A fresh stage-marked probe localized the diagnostic to original namespace
loading and reproduced all31 original grid results with strict complete R-value
equality. It does not establish the underlying native cause or remove the
diagnostic from earlier evidence.

Run the project regression tests in a suitable source checkout:

```powershell
.\gradlew.bat test --tests org.jlinalg.singlecell.VelociraptorGrid120Test jar --no-daemon
```

The original MIT notice is packaged as
`META-INF/LICENSE-velociraptor120.txt`; the Java port follows the project's GPL
license. The complete 250-paper implementation/review objective remains open.
