# Native exact decimal KING cutoff comparisons

`KingKinshipThreshold` compares exact integer hard-call ratios with an explicit
decimal cutoff. It distinguishes `>` and `>=` and keeps within-family and
between-family formulas separate. It requires no original KING runtime.

**Unassessable pairs cannot silently become unrelatedness decisions.** An
evaluation with no jointly called variants or a zero heterozygosity denominator
returns an explicit state. Calling `passes` on that state throws and tells the
caller to skip dependent relationship selection and inspect the counts.

This component evaluates caller-supplied complete counts. It does not repair
original KING reports, infer complete pair coverage, choose participants for
removal, reproduce an unspecified paper policy or replace SNPRelate/PLINK2
hosting. A historical KING count discrepancy still rejects that adapter's run.

```java
var counts = new KingRobustKinship.Counts(1200, 625, 200, 229, 0);
var evaluation = KingKinshipThreshold.evaluate(
    counts, KingKinshipThreshold.Formula.BETWEEN_MINIMUM, "0.0884");
// Exactly221/2500 = 0.0884: EQUAL
boolean strict = evaluation.passes(KingKinshipThreshold.Rule.GREATER_THAN); // false
boolean inclusive = evaluation.passes(
    KingKinshipThreshold.Rule.GREATER_THAN_OR_EQUAL); // true
```

The calculation uses no floating-point conversion or division. In this control,
the usual binary calculation is slightly below the decimal0.0884. A comparison
of that double with another binary double cannot establish the exact inclusive
decimal boundary. Rounded original table coefficients are not inputs to this
API; independently counted hard calls are required.

| Formula | Exact numerator | Exact denominator |
| --- | --- | --- |
| `BETWEEN_MINIMUM` | `2*min(HET1,HET2) - IBS1 - 4*IBS0` | `4*min(HET1,HET2)` |
| `WITHIN_AVERAGE` | `HET_HET - 2*IBS0` | `HET1 + HET2` |

All heterozygosities use only jointly called variants. `IBS1` is the sum of the
two asymmetric heterozygote/homozygote categories. Negative finite numerators
are preserved. The validated count type is the unchanged
[`KingRobustKinship.Counts`](NATIVE_KING_ROBUST.md) component.

The cutoff is an explicit decimal or scientific-notation string in0..0.5.
Leading/trailing whitespace, invalid/nonfinite strings, excessive text length
and excessive decimal precision/scale are rejected. The enum result is `BELOW`,
`EQUAL`, `ABOVE`, `NO_JOINT_CALLS` or `ZERO_HETEROZYGOSITY`. Choose the formula
and strict/inclusive rule explicitly; the API does not infer them from FID,
ancestry, a paper's threshold or a source-zero coefficient.

## Use the standalone Java API

The two Java17-targeted API artifacts include only this component and the
unchanged count kernel. They contain no upstream programs or dependencies.

```powershell
javac --release 17 -cp docs/literature/king-historical-development/king-exact-threshold-javac21.jar -d D:/work/king-threshold-example-new examples/native-king/KingThresholdExample.java
java -cp 'D:/work/king-threshold-example-new;docs/literature/king-historical-development/king-exact-threshold-javac21.jar' KingThresholdExample
```

On Linux, use `:` between classpath entries. The example prints:

```text
221/2500 EQUAL strict=false inclusive=true
```

The JDK21 and JDK25 builds are byte-identical. Fresh external consumers execute
on Windows Java21/25 and Linux Java21. The test suite runs19 methods on each of
those three hosts: ten new exact-boundary/formula/unassessable/capacity tests
and nine unchanged native count contracts,57 executions total. It also retains
the existing2880 original PLINK-reference pair checks. The first test-helper
attempt's copied27-execution metadata is explicitly superseded; its actual logs
remain retained. The corrected suite was rerun and reports57.

See [historical KING evidence](king-historical-development/VALIDATION.json) for
source/artifact hashes and test receipts. The [historical adapter guide](HISTORICAL_KING_INTEGRATION.md)
explains the2.0 discrepancy and admission protocol. This is additional scoped
Java functionality; it does not change the catalog's tool count or complete the
full250-paper goal.
