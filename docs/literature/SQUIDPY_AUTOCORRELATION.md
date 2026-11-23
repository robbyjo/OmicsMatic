# Native Squidpy weighted-graph autocorrelation

The Java library computes Moran's I and Geary's C on a supplied weighted CSR
graph, with explicit original Squidpy edition conventions. It supports signed
and directed weights, optional row L1 normalization, self-edges and isolated
observations. It does not modify the supplied arrays.

**This is a native component, not a full Squidpy workflow.** Spatial graph
construction, permutations, gene selection, multiplicity correction,
ligand-receptor inference and CosMx input reading remain adapter work. It adds
no new catalog family credit. Read the [qualification and next work](SQUIDPY_NATIVE_CHECKPOINT_2026-11-22.md)
before using its scientific outputs. Original runtime comparisons use artificial
data, not a reproduced biological paper.

## Build and run an artificial example

Use current source; existing release downloads were not replaced. From the project
root with Java 17 or newer, build the executable JAR with the usual project build:

```powershell
.\gradlew.bat executableJar
java --class-path build/cli/omicsmatic-0.4.0.jar examples/squidpy-autocorrelation/WeightedAutocorrelation.java
```

The example uses a four-node path with unit edge weights. It reports Moran I
of 1/3 and Geary C of 0.3, together with their declared-edition normality
variance/probability and status. This library API is not a new global JAR command.
The artificial constant-feature example demonstrates the required no-result path:

```powershell
java --class-path build/cli/omicsmatic-0.4.0.jar examples/squidpy-autocorrelation/WeightedAutocorrelation.java constant
```

That example exits **2**, prints `UNDEFINED_STATISTIC` and a prominent `FAIL2`
remedy, and retains NaN as NaN. It does not replace an undefined estimate with zero.

## Call the Java API

```java
import org.jlinalg.squidpy.SquidpyAutocorrelation;

var result = SquidpyAutocorrelation.compute(
    SquidpyAutocorrelation.Edition.SQUIDPY_1_8_4,
    SquidpyAutocorrelation.Statistic.MORAN,
    new double[] {1, 2, 3, 4},
    new int[] {0, 1, 3, 5, 6},       // CSR row pointers
    new int[] {1, 0, 2, 1, 3, 2},    // zero-based column indexes
    new double[] {1, 1, 1, 1, 1, 1},
    false,                          // row L1 normalization
    false);                         // source two_tailed option

if (result.status() != SquidpyAutocorrelation.Status.COMPLETE) {
    // Report result.condition(); do not admit normalityPValue() as an estimate.
}
```

Rows must have sorted unique zero-based columns. Dimensions, indexes, finite
input values and weights are checked explicitly; no sorting, deduplication,
symmetrization, identifier reassignment or nonfinite-value repair is performed.
The native capacity is 3 through 100,000 observations and at most 1,000,000
stored entries. Supply graph/features in the same declared observation order.
An omitted edge means weight zero. Row normalization divides by the sum of
absolute weights and leaves an empty/zero row unchanged.

| Edition | Moran normality variance | Geary normality variance |
| --- | --- | --- |
| `SQUIDPY_1_1_2` | Original Moran formula | Original historical reuse of Moran formula |
| `SQUIDPY_1_2_2` | Original Moran formula | Original historical reuse of Moran formula |
| `SQUIDPY_1_8_4` | Original Moran formula | Original corrected Geary formula |

For a one-sided call the implementation retains the original source convention:
subtract the normal CDF from one for a positive standardized statistic, use the
CDF for a nonpositive statistic, and multiply by two only when `twoTailed` is
true. Cancellation to an exact zero probability is retained. This is an analytic
normality calculation, not an empirical permutation P value. Floating-point
summation across Java/NumPy/Numba may differ within the documented validated
tolerance; historical dependency builds are not asserted to be bitwise identical.

## Warnings and required actions

| Status | Meaning and action |
| --- | --- |
| `COMPLETE` | Finite point statistic and resolved positive normality variance. Keep the edition, normalization/tail flags, variance and graph provenance with the probability. |
| `UNDEFINED_STATISTIC` | The float64 point statistic is nonfinite, such as a constant feature or zero global graph weight. No complete statistic/probability is admitted. Inspect the feature and graph; preserve original values and retry with justified NEW inputs. |
| `UNDEFINED_ANALYTIC_VARIANCE` | The point is retained but analytic variance is nonpositive/nonfinite. No analytic probability is admitted. Inspect graph degeneracy, scale and the original source outcome; use a scientifically justified separately declared analysis. |
| `UNRESOLVED_ANALYTIC_VARIANCE` | Positive variance is within the native 64-ulp moment-cancellation bound, or its bound is nonfinite. Point and computed variance remain available; probability is NaN. Preserve any original upstream result separately and report the precision problem. |
| `IllegalArgumentException` | Input shape, capacity, CSR ordering or finite-value requirement failed. Report the error as a failed analysis; correct the declared input rather than repairing it silently. |

The roundoff bound is returned as `varianceRoundoffBound()`. It is a conservative
native admission diagnostic based on the magnitudes of the cancelling moment
terms, not a change to the original variance formula or a guarantee about every
upstream platform's rounding. The original runtime can return an apparently
finite cell from an uninitialized array for an undefined case. The fixture keeps
that raw evidence; the native API explicitly refuses to treat it as an estimate.
It also refuses cancellation-dominated graph variances even when an upstream
build reports a finite probability such as 0.5. These source/native admission
distinctions are deliberate and visible.

**SKIP3 for the future upstream adapter:** missing/prohibited original software
must skip the dependent operation with its exact prerequisite and remedy. The
native component has no Python dependency and must not be used as a silent
fallback for an unavailable full Squidpy operation. **FAIL2:** keep raw/partial,
undefined and failed evidence and use a new output after correcting the cause.

Original semantics and notices are traced to the
[official Squidpy source](https://github.com/scverse/squidpy), exact wheels and
all installed source hashes in the frozen reference fixture. The original BSD
notices are included under `META-INF/licenses/SQUIDPY-*-BSD-3-Clause.txt`.
