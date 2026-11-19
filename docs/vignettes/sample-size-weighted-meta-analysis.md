# Sample-size-weighted signed-Z meta-analysis in Java

Use `org.jlinalg.meta.SampleSizeWeightedMetaAnalysis` to combine **already
aligned statistics from independent cohorts**, with positive effective sample
sizes you explicitly supply. This native component needs Java 17+ and the
current source-build JAR. It does not invoke or install METAL. Existing
inverse-variance beta/SE methods remain in `org.jlinalg.meta.MetaAnalysis`.

**Before analysis:** verify that cohorts test the same hypothesis and effect
allele, that direction coding is correct, and that your effective sample sizes
and independence assumptions are appropriate. This API does not determine
those facts, harmonize alleles, correct overlap or apply genomic control.

## Quick start

Build the current source and run the complete synthetic example from the
repository root. In PowerShell:

```powershell
.\gradlew.bat --no-daemon --no-parallel :executableJar
$metaJar = (Resolve-Path 'build\cli\omicsmatic-0.4.0.jar').Path
# Use a NEW example output directory.
New-Item -ItemType Directory build\weighted-z-example
javac --release 17 -cp "$metaJar" -d build\weighted-z-example examples\meta\SampleSizeWeightedMetaExample.java
java -cp "$metaJar;build\weighted-z-example" SampleSizeWeightedMetaExample
```

On Linux, use `./gradlew`, forward slashes and `:` as the Java classpath
separator. The complete [example source](../../examples/meta/SampleSizeWeightedMetaExample.java)
prints the number of studies, combined Z, P, natural-log P, underflow indicator,
heterogeneity Q, degrees of freedom, heterogeneity P and I-squared. It uses
synthetic statistics, not biological data. A previously published `0.4.0`
release does not contain this addition; the version string alone is insufficient.
The exact qualified development JAR is pinned in
[VALIDATION.json](../literature/weighted-z-development/VALIDATION.json).

The example returns Z approximately `2.35937`, P `0.0183062` and heterogeneity
Q `11.9234`. These rounded guide values are for orientation; retain the
full-precision output for numerical comparisons.

The central call is:

```java
var result = SampleSizeWeightedMetaAnalysis.fitSignedZ(
    new double[]{2.5, -1.0, 3.2},
    new double[]{1000, 2000, 1500});
```

These weights mean declared effective sample sizes, not standard errors or
inverse variances. No effect estimate or standard error in original trait
units is inferred from the combined Z.

## Input choices and results

| Method | Inputs and restrictions |
|---|---|
| `fitSignedZ(z, effectiveN)` | Matching nonempty arrays; every Z finite and every weight positive and finite. |
| `fitPValues(p, directions, effectiveN)` | Two-sided P in `(0,1]`; each direction explicitly `-1` or `+1`. Exact input P=0 is refused. |
| `fitLogPValues(logP, directions, effectiveN)` | Finite **natural logarithms** of two-sided P, at most zero; explicit `-1` or `+1` directions. This is not negative log10 P. |

All methods have an overload accepting `Limits`. Arrays must remain immutable
while the call is in progress. The returned diagnostic arrays are defensive
copies. Callers should retain their original inputs and the declarations used
to obtain each statistic.

For each cohort, the coefficient is `sqrt(N_i) / sqrt(sum(N))`. The combined Z
is the sum of coefficient times cohort Z. This evaluates square roots before
division to avoid prematurely underflowing a small weight fraction. The result
retains cohort Z values, coefficients, Z contributions and heterogeneity
contributions, along with the total declared weight.

Heterogeneity Q is `sum((Z_i - coefficient_i * combinedZ)^2)` under the common
standardized-effect model, with `studies - 1` degrees of freedom. I-squared
uses its ordinary nonnegative convention. For a single cohort, Q is zero and
heterogeneity P, log P and I-squared are **null**; `heterogeneityDefined()` is
false. This avoids inventing an inferential test for one cohort.

## Underflow, refusal and recovery

**A displayed P of zero can mean numerical underflow.** If a computed result
P underflows, `pValueUnderflow()` is true and finite `logPValue()` is retained.
The equivalent heterogeneity fields behave similarly when that test is
defined. Use the log probability for downstream numerical work; do not replace
zero with a chosen positive floor.

For example, `fitLogPValues(new double[]{-10000}, new int[]{-1},
new double[]{100})` retains the extreme finite log probability. If an upstream
file contains only the literal P=0 and no trustworthy finite log probability,
the lost precision cannot be recovered: the ordinary-P method refuses it.
The method forms the two-sided-to-one-sided conversion in log space, including
for the smallest positive subnormal P.

**No complete result is returned for invalid or unrepresentable models.**
`IllegalArgumentException` identifies invalid/missing input or a declared
capacity refusal; correct the stated cause and submit the complete intended
population again. `ArithmeticException` identifies unrepresentable
intermediates or results; retain the input and failure and resolve the
numerical/model issue before a new attempt. Never drop cohorts, infer signs,
replace NaN/Inf or silently rescale inputs to manufacture a result.

Default capacity is 1,000,000 studies and an estimated 128 MiB budget. The
admission estimate is `8192 + 128 * studies` bytes; it is an explicit estimate,
not a JVM heap guarantee. You can declare different positive limits using
`new SampleSizeWeightedMetaAnalysis.Limits(maximumStudies, maximumBytes)`.
Capacity refusal covers the whole population and occurs before working-array
allocation. Sum-of-weights or statistic overflow also refuses the whole model.

If Java 17+ or the matching current JAR cannot be supplied, skip dependent
analysis and report that missing prerequisite and its required recovery step.
The library throws exceptions; it does not create CLI completion markers.

## Relationship to METAL and validation

METAL's [documented sample-size scheme](https://genome.sph.umich.edu/wiki/METAL_Documentation)
uses signed statistics and normalized square-root sample-size weights. This
component independently implements that mathematical calculation. It is not
the METAL interpreter or a complete replacement for source-specific workflows.

Qualification includes the same seven packaged tests on Windows and Linux,
69 finite independent R models with 1,844 fields, one expected unrepresentable
model with 20 raw R fields retained, and two further R tiny-Q controls with ten
fields. All complete model/diagnostic payloads agree across platforms. Against
unchanged original METAL, 62 ordinary models at each of two output modes agree
at 844 explicit printed-precision comparisons. Every one of the 23,031
pre-existing JAR members is unchanged; only four new component classes appear.

Source differences remain explicit. Original METAL outputs base-10 log P;
this API accepts and returns natural logs. Its `LOGPVALUE` option controls
output, not log-P input. Original METAL reports heterogeneity P=1 for a single
cohort or sufficiently small Q; the API retains null single-cohort inference
and the ordinary chi-square tail for small Q. An original boundary run emitted
nonfinite values despite exit 0 and remains **FAIL2, no complete result**.

Read the [qualification](../literature/weighted-z-development/README.md) and
[warnings and required actions](../literature/weighted-z-development/WARNINGS.md).
No METAL family credit, full paper replication or biological validation is
implied. The typed original METAL workflow and the 24 individual paper-role
reviews remain unfinished.
