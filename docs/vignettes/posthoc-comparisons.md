# Native Tukey–Kramer multiple comparisons

For independent nonparametric pooled-rank comparisons, see the separate
[Dunn command and explicit comparison-family guide](dunn-comparisons.md).

`posthoc-tools tukey` fits an ordinary one-way model and compares every pair of
declared group means. It reports signed mean differences, standard errors,
Studentized-range statistics, multiplicity-adjusted p-values and simultaneous
confidence intervals. Unequal sample sizes are supported.

The analysis runs in Java using the bundled JDistlib distribution routines
and a native positive-integral calculation for small upper-tail probabilities.
R and GraphPad Prism are not required. Build current source; the released
OmicsMatic 0.4.0 JAR does not contain this command.

```bash
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar posthoc-tools tukey \
  --input examples/posthoc/groups.tsv \
  --confidence 0.95 --out build/tukey-example
```

On Windows use `./gradlew.bat executableJar`; the Java command is the same
when written on one line. Output must be a new directory.

## Input and results

Input is UTF-8 TSV with the exact header `group<TAB>value`. Each row is one
independent observation. An optional UTF-8 byte-order mark is supported and
recorded; the captured input bytes remain unchanged. Labels are literal and
retain first-appearance order. Numeric values must be finite decimals. Missing
values, empty rows and malformed records reject the complete analysis.

The example has three groups of five observations. The result includes:

| File | Meaning |
|---|---|
| `input.tsv` | Exact captured input |
| `groups.tsv` | Group order, labels, sample sizes and means |
| `comparisons.tsv` | Every pair, adjusted p-value and simultaneous interval |
| `analysis.json` | Confidence, residual SD/df, numerical method, tail-refinement count and limits |
| `manifest.json` | Input and output size/SHA-256 records |
| `WARNINGS.md` | Model scope and recovery instructions |
| `COMPLETED.json` | Written after successful complete analysis and output recording |

Differences and interval endpoints are **second group minus first group**, in
the declared group order. `studentized_range_q` is the Tukey range statistic.
The default confidence level is 0.95 and is always recorded.

Two-group probabilities use the exact two-sided Student-t identity. General
families use a positive-integrand tail calculation when needed, with explicit
quadrature convergence checks and independent pairwise probability bounds.
`probability_method` and `positive_tail_evaluations` record the route used.

Keep the full intended comparison family in the input. Removing other groups
changes the shared residual variance, degrees of freedom, multiplicity
adjustment and confidence intervals. Select contrasts for presentation from
the complete output without refitting a smaller family implicitly.

## Choose the model explicitly

The model assumes independent Gaussian observations with a common population
variance. It uses all groups to estimate residual variation. Normality and
equal variance are model assumptions; this command does not select a model
through preliminary tests. Raw observations and arithmetic group means define
the ordinary one-way fit.

Factorial marginal means, paired/repeated measurements, count-regression
contrasts and mixed models require their own model and covariance structure.
The one-way command does not infer these from group labels or from a paper's
mention of Tukey. Individual groups may be constant or contain one observation
when the full fit has positive residual variation and at least two residual
degrees of freedom. A fit with zero pooled variation is rejected explicitly.

The CLI accepts at most 64 MiB of input, two million observations and 256 groups.
These are refusal bounds: no rows, groups or comparisons are truncated.
Numerically unrepresentable results reject the full comparison family.
If adjusted p-values reach numeric zero, `NUMERIC-WARNING.txt` identifies the
floating-point limit; q and degrees of freedom remain available.

**SKIP3:** input or output access is unavailable or forbidden; skip dependent
analysis and supply a permitted accessible path. **FAIL2:** no complete result;
keep the input and partial files, read `FAILURE.txt` and `WARNINGS.md`, resolve
the named problem and retry new output. Require exit 0 and valid
`COMPLETED.json`; a partial table is not a completed family.

## Java API

```java
import org.jlinalg.stats.TukeyKramer;

double[][] groups = {{1, 2, 3, 4, 5}, {2, 3, 4, 5, 6}, {6, 8, 7, 9, 5}};
var result = TukeyKramer.compare(groups, 0.95);
for (var pair : result.comparisons()) {
    System.out.println(pair.difference() + "\t" + pair.adjustedPValue());
}
```

`compareMeans(means, sizes, sharedResidualSd, residualDf, confidence)` accepts
explicit summaries for independent mean estimates from the same justified
fit. The caller supplies the shared residual model and complete comparison
family. The API refuses families exceeding one million pairs and returns
immutable result lists.

## Validation and sources

The independent oracle fits `stats::aov` and calls `stats::TukeyHSD` in R 4.6.1,
using exact Student-t probabilities/intervals for the two two-group fits.
The checked-in fixtures cover 57 comparisons across ten models: balanced and
unbalanced groups, a singleton, constant individual groups, equal means,
two-group equivalence and several confidence levels. The generator and exact
fixture identities are recorded in
[PROVENANCE.json](../../src/test/resources/org/jlinalg/stats/tukey/PROVENANCE.json).

Ten additional tail checks use independently integrated normal-range and
chi-square distributions in R's QUADPACK integrator, exact two-group Student-t
probabilities and analytic three-group moment checks. They include positive
subnormal probabilities and true floating-point underflow. Relative tolerance
is `5e-8`, with at most eight smallest-double units for subnormal rounding.

**Numerical difference from legacy `ptukey`:** R 4.6.1 and its bundled Java
port return a tail near `9.37e-14` for two groups, four residual degrees of
freedom and `q = sqrt(3) * 1e6`. The exact Student-t result is
`2.66666666665e-24`. This implementation retains the latter. Legacy results
remain alongside the independent references in `tail-expected.tsv`; small
tails are neither floored nor forced to agree with that numerical artifact.
At two residual degrees of freedom the legacy routine also differs at ordinary
probabilities and confidence limits. The raw `TukeyHSD` table is retained;
`two-group-exact.tsv` supplies the separate exact-identity reference. Of the
57 comparisons, 55 use `TukeyHSD` directly and two use that exact identity.

To regenerate independently with an available permitted R installation:

```bash
python3 -B tools/literature/tukey_kramer/generate_reference.py \
  --rscript /path/to/Rscript --out build/tukey-reference-new
python3 -B tools/literature/tukey_kramer/generate_reference.py \
  --rscript /path/to/Rscript --tails --out build/tukey-tail-reference-new
```

<!-- SCIENTIFIC-CITATIONS:START -->

GraphPad documents its unequal-sample-size Tukey procedure as Tukey–Kramer.
The implementation follows that statistical method; its reference validation
uses R, without claiming a licensed Prism runtime comparison.
[GraphPad methods guide](https://www.graphpad.com/guides/prism/latest/statistics/stat_the_methods_of_tukey_and_dunne.htm),
[R TukeyHSD documentation](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/TukeyHSD.html).

<!-- SCIENTIFIC-CITATIONS:END -->

The source review distinguishes seven papers explicitly describing one-way
ANOVA/Tukey, other model-specific Tukey uses and two box-plot mentions.
See the [implementation priority record](../literature/IMPLEMENTATION_PRIORITY.md).
