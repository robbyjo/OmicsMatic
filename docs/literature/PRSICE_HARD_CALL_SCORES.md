# Native PRSice hard-call scoring

`org.jlinalg.genetics.PrsiceHardCallScores` implements the diploid scoring
arithmetic of original PRSice 2.3.5 in Java. It needs no PRSice installation.
It accepts a matrix of already selected variants and target samples, explicit
effect sizes, allele orientations and sample masks. It returns scores, raw
sums, allele denominators, per-variant inclusion and warnings.

The [original PRSice execution workflow](../PRSICE_WORKFLOW.md) adds typed file
handling, original clumping/association and streaming native score audits in a
separately qualified source build. This Java component alone performs no file
harmonization, LD estimation, clumping, threshold search, regression,
cross-validation or model selection. Neither checkpoint reproduces the five
papers or establishes historical author-edition equivalence. Catalog admission
remains separate: 160 scoped families and 1,765 reviewed uses; this component
adds no family or review credit.

## Use the Java API

Build current source with the project's documented JDK/Gradle setup, or use the
exact qualified source-built JAR listed in the checkpoint. These artificial
calls count copies of **target BIM A1**. Use `-1` for a missing hard call.
`flipped=true` means the effect allele is target BIM A2; this API does not infer
orientation from allele names or strand.

```java
import org.jlinalg.genetics.PrsiceHardCallScores;
import org.jlinalg.genetics.PrsiceHardCallScores.*;

int[][] calls = {{0, 2, -1}, {1, 2, 0}}; // variants by samples
double[] effects = {.5, -1};
boolean[] flipped = {false, false};
boolean[] founders = {true, true, false};
boolean[] eligible = {true, true, true};
boolean[] controls = {true, false, false};
var result = PrsiceHardCallScores.score(calls, effects, flipped, founders,
        eligible, controls, Model.ADDITIVE, Missing.MEAN_IMPUTE, Scaling.SUM);
if (result.status() != Status.COMPLETE)
    throw new IllegalStateException(result.status() + ": " + result.warnings());
double[] scores = result.scores(); // -1, -1, 0.5
```

The caller must supply the intended variant order, harmonization and sample
selection. Founder calls determine the source formula's frequency term.
`regressionEligible` selects the samples used for standardization; control-only
standardization also requires `controls=true`. The transform applies to every
supplied sample. These masks do not construct folds or interpret phenotypes.
Do not use held-out outcomes to select models, LD panels or tuning parameters.

The genetic models are additive, dominant, recessive and heterozygous-only.
The missing-call choices are mean imputation, set zero and centering. Scales
are sum, average, standardized and control-standardized. **Average divides by
the accumulated allele count: two per contributing diploid variant.** Set-zero
omits missing calls from that denominator. The other missing choices count
their alleles. Standardization operates on average scores and uses sample SD
with denominator `n-1`.

## Warnings and required actions

**Source-specific behavior:** non-additive missing/centering values use the
original model-dependent formula. A missing dominant, recessive or
heterozygous-only contribution can exceed the observed encoded genotype range.
The implementation reports this convention and preserves it. Do not replace
it silently with a mean of encoded genotypes or clip the result.

**No complete score result:** check `result.status()` and `result.warnings()`.
All-missing founder calls exclude that variant and leave its missing-model
value as `NaN`. If every variant is excluded, the result is unadmitted. Too few
selected standardization samples, zero/invalid SD and nonfinite scores retain
their raw values and return a failed status. Preserve those values, review the
named calls, effects or masks, and make an explicit justified correction before
retrying. The API does not repair inputs or claim calibrated individual risk.

**Unavailable verification prerequisites (SKIP3):** the native method works
without upstream software, but a packaged verification requires its exact
qualified JAR and a permitted JDK. Download/install prerequisites only where
allowed. If installation is prohibited or an exact prerequisite cannot be
supplied, skip that dependent verification and keep the warning. A changed
producer or evidence is FAIL2: no verified result. Restore the exact named
source/producer or qualify a new build; never refresh old expected hashes.

The [official 2.3.5 release](https://github.com/choishingwan/PRSice/releases/tag/2.3.5)
documents BGEN limitations, a Windows Eigen-build concern and changed R-squared
reporting. This component's hard-call scoring checks do not qualify BGEN,
regression, permutation tests, R plotting, historical patch releases or
whole-tool behavior. Its standardization reference fixture uses valid binary
phenotypes and explicit control/founder membership; broader phenotype/covariate
eligibility will need separate adapter validation.

## Evidence and source roles

The native implementation follows unmodified upstream source revision
`9dbf55e3f7f3ede2c1b1b6a334c676163e0cbe34`, especially
`BinaryPlink::read_score` and `Genotype::read_prs`, `standardize_prs` and
`calculate_score`. Original GPL-licensed Windows and Linux runtimes are private
validation dependencies. No upstream binary is bundled with this component.

There are 48 scoring combinations per operating system. The reference runs
cover all four models, all three missing choices and all four scales, with
allele reversal, missing calls, a nonfounder, negative effects and monomorphic
variants. Comparison respects the original output's nine significant digits;
it does not assert equality to inaccessible internal full-precision scores.
See the [checkpoint](prsice-scores-checkpoint/README.md) for the final test
counts, exact producer and repeatable verification instructions.

The [five source roles](prsice-source-audit/ROLES.json) distinguish:

- P076: PRSice-meta, with test-data selection of a population LD panel.
- P115: held-out and external-summary prediction, covariates and distinct metrics.
- P128: clumping/thresholding and scoring weights from separate Bayesian methods.
- P147: cross-validated target/nontarget prediction and PRS pleiotropy.
- P150: a supplementary PRSice comparison, separate from the main PRS-CS analysis.

All 250 XML identities were checked. Seventeen eligible prose anchors and seven
other structures were inventoried. Literal generic version-2 contexts and raw
near-name values remain preserved; no precise patch edition is inferred. Exact
author commands, input cohorts, unresolved supplements and paper reproduction
remain open. Read each role's settings and limits before selecting an analysis.
