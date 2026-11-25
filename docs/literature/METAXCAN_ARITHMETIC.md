# Native MetaXcan association arithmetic

The current source provides `org.jlinalg.genetics.MetaXcanMath`: Java arithmetic
for S-PrediXcan summary association, the release-tag v0.8.1 phi correction, and
S-MulTiXcan joint-tissue tests. Java runs these calculations without Python.
Runnable original-tool workflow integration and catalog admission are pending;
this component adds **zero** tool-family credit to the 250-paper catalog.

**Build the current source. Earlier 0.4.1 release downloads do not contain this
class.** Run `./gradlew executableJar` or `./gradlew.bat executableJar` and use
`build/cli/omicsmatic-0.4.1.jar`.

**Input order is the caller's responsibility.** Supply only the exact used SNP
intersection, with weights, GWAS z-scores, optional GWAS betas and raw-dosage
covariance in the same allele orientation and SNP order. For joint testing,
supply the model-product matrix in the same tissue order as the z-scores.
Incorrect ordering can produce plausible but incorrect results. This API does
not read PredictDB databases, resolve identifiers or alleles, train prediction
models, impute GWAS statistics, or select genes, samples and tissues.

For example, start `jshell --class-path /absolute/path/to/source-built.jar`:

```java
import org.jlinalg.genetics.MetaXcanMath;
var result = MetaXcanMath.summary(
    new double[]{1, 2},                 // raw-dosage prediction weights
    new double[]{2, -1},                // GWAS z-scores in model orientation
    new double[]{0.3, -0.2},            // GWAS betas; use null when unavailable
    new double[]{4, 1, 1, 9});          // row-major raw-dosage covariance
result.variance();                     // 44
result.zscore();                       // -2 / sqrt(44)
result.effectSize();                   // -2.4 / 44

var corrected = MetaXcanMath.calibrate(result, 0.002, 10000, 0.3);
corrected.denominator();               // 1 + phi * GWAS_N * GWAS_h2 = 7
corrected.uncalibrated();               // original fields remain available

var joint = MetaXcanMath.joint(
    new double[]{2, 1}, new double[]{1, 0.5, 0.5, 1},
    new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.CONDITION_NUMBER, 30),
    null);                             // null selects no diagonal regularization
joint.components();                    // 2
joint.statistic();                     // 4
```

The summary variance uses only the SNPs actually included in the calculation.
It is not the variance of the complete, unobserved prediction model. A missing
beta vector gives a null effect size; it does not create zero effects. Covariance
rows with zero variance are accepted when total predicted variance is positive.
The matrix is validated without changing it or normalizing it to correlation.

Calibration is an explicit separate operation. Original v0.8.1 replaces negative
phi with zero; the result retains the supplied phi, applied phi, replacement flag
and uncalibrated fields. It does not change the summary effect size or variance.
The original file workflow also drops genes whose phi is absent; that file/join
behavior is outside this arithmetic API. Historical releases are not silently
calibrated.

Joint testing supports the source's eigenvalue-ratio, trace-ratio, variance-threshold
and condition-number cutoffs. It calculates the cutoff before optional diagonal
regularization, uses an inclusive singular-value comparison, and always selects
the first component. The variance-threshold rule retains the original cumulative
comparison, rather than substituting a generic PCA rule. Computation uses the
portable Java CPU backend. Best/worst tissue ties keep the first input index.

Invalid dimensions, nonfinite inputs, indefinite covariance, nonpositive total
prediction variance or a selected zero singular component raise an exception.
No completed estimate is returned for those failures. Preserve the input and
original failure, correct the cause, and rerun explicitly; do not replace missing
values or repair covariance automatically. When the joint chi-square tail
underflows, the original p-value zero and source status `-4` are retained. A log
p-value is also returned where representable; zero denotes numerical underflow.

## Source identities and validation

The [original project](https://github.com/hakyimlab/MetaXcan) is MIT licensed; its
[notice](../../src/test/resources/org/jlinalg/genetics/metaxcan/LICENSE-MetaXcan.txt)
is retained with the entirely synthetic reference fixtures.

| Source selection | Commit | Internal package version |
| --- | --- | --- |
| Tag v0.7.3 | `72e8c94edf31a4b261d29a7acf9a659804510e46` | 0.7.3 |
| Explicit historical commit | `b133eb36039a557d1476ead50fc6147734c998de` | 0.7.4 |
| Tag v0.8.1 | `964f1fdb5bf9585585690e85bb0eca7b67663ddb` | 0.7.5 |

Tag identity and internal version are distinct. The 0.7.4 commit is a pinned
original source selection; the authors' exact commit was not reported. Unspecified
paper versions are not inferred. The v0.8.1 correction requires compatible model
phi values, GWAS sample size and heritability; it is not a replacement for an
uncalibrated historical run.

Unmodified upstream arithmetic produced 14 summary and 14 joint cases for each
of the three source selections, plus four phi reference cases. These include raw
variance, missing betas, rank deficiency, cutoff ties, forced first-component
selection, regularization and underflow. The source also drops two synthetic
rows lacking phi. Native tests compare those source results and check invalid
inputs. This establishes arithmetic qualification, not database parsing,
model training, original CLI execution, complete-tool equivalence or paper
reproduction. See the [native checkpoint](metaxcan-native/STATUS.json) for exact producer and evidence hashes.