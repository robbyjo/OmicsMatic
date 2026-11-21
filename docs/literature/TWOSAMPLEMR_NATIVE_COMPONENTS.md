# Native prepared TwoSampleMR statistics

The new `org.jlinalg.mr.TwoSampleMrStatistics` Java API supplies eight
deterministic statistical methods, weighted-median point estimation and
simple/weighted-median inference from supplied bootstrap effects. It targets
[TwoSampleMR 0.7.14 source c08ab8a6](https://github.com/MRCIEU/TwoSampleMR/tree/c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6).
The existing `MendelianRandomization` API and `mr-estimate` command retain their
previous behavior. This component has no new global command or catalog credit.

Build current source; the released 0.4.0 predates this API. Run the
[copyable synthetic example](../../examples/twosamplemr-native/README.md).
Native calculations require Java and the bundled JDistlib, with no R subprocess,
package installation or network access during evaluation.

| Method enum | Source statistical rule |
| --- | --- |
| `MR_WALD_RATIO` | One-SNP ratio; outcome SE divided by absolute exposure effect |
| `MR_META_FIXED_SIMPLE` | Direct first-order fixed estimate |
| `MR_IVW` | Multiplicative IVW, correcting underdispersion |
| `MR_IVW_FE` | Source fixed-effect SE/sigma division, retaining undefined results |
| `MR_IVW_MRE` | Multiplicative IVW allowing underdispersion |
| `MR_UWR` | Unweighted regression through the origin |
| `MR_EGGER_REGRESSION` | Positive-exposure orientation, slope/intercept, Student t with n-2 df |
| `MR_SIGN` | Exact sign concordance; at least six nonzero pairs; SE is intentionally NA |

The weighted median includes exposure uncertainty in its delta-method weights.
Those original-input weights remain fixed in `medianFromDraws`. The supplied
matrices contain sampled exposure/outcome effects, with bootstrap replicates as
rows and variants as columns. They are not standard-normal deviates. No Java
RNG is substituted for R's RNG. A point estimate alone carries no bootstrap SE.

Inputs are four aligned, complete finite, already harmonized independent-variant
effect/SE vectors. SEs must be positive. Zero exposure effects are retained;
identifiers, selection, allele alignment, LD and population preparation belong
to the caller. Missing values are refused without omission or imputation.
Regression weights must remain finite and positive. Capacity is one million
variants and twenty million cells per supplied bootstrap matrix.

Returned numerical fields use the source names (`b`, `se`, `pval`, `nsnp`, and
method-dependent Q/intercept fields). `Value.kind()` distinguishes finite, NA,
arithmetic NaN and either infinity. Source minimum-SNP guards return unavailable
fields. Scalar Wald rejects zero-length input, whose original numeric(0)
return is outside this scalar API. R model containers and attributes are not
emulated. Exact-fit and nonfinite values are retained, never replaced with an
analytic shortcut, clamped estimate or forced finite SE.

## Validation and remaining scope

The normal producer and all 638 tests pass per JVM on Windows Java 21/25 and
Linux Java 21, with JAR-origin checks. The reference harness runs 630 calls
across 63 synthetic cases through unchanged upstream R definitions under
Windows R4.6.1. Four rank-zero regressions raise original errors; each return or
raised-error capture, input, warning and RNG endpoint is retained. For median
methods, the actual R sampled effects, final RNG state and reconstructed
replicate SD are checked against the unmodified original result.

Numerical kinds and field populations match exactly; finite statistics use
2e-9 relative or 64-ULP tolerance, whichever is larger. The first normal build
had five exact-fit failures. Matching R's rounded reciprocal and Reference BLAS
norm accumulation resolved them without changing inputs, reference results or
tolerances. Earlier compiler/preflight harness failures remain retained.
All 22,297 pre-existing JAR payloads, including legacy MR and global dispatch,
remain byte-identical. Both upstream notices are in the normal JAR.

The [14 paper-role proposals](twosamplemr-source-review-proposal/README.md) are a
separate all-250 source audit. Reported historical editions are 0.4.9, 0.5.6,
0.5.7 and 0.6.8; others are unreported. None is silently mapped to this modern
profile. Full file workflows, original package execution, historical editions,
allele harmonization/clumping, modes, multivariable preparation, SIMEX,
provider-specific MR-PRESSO and full author analyses remain open.

## Warnings and recovery

**NO_COMPLETE_RESULT:** inspect the named unavailable/nonfinite core fields and
every warning. Preserve inputs and raw statistics; resolve the input count,
design or numerical cause before reporting an estimate. An essentially perfect
fit retains its numerical values and a precision warning. NA SE for sign
concordance and NA Q for median methods are intentional source outputs.

Invalid/missing values, dimensions or capacity raise an explicit Java exception.
A future analysis workflow must record these as **FAIL2: NO COMPLETE RESULT**,
retain its inputs/partials and retry corrected requests into NEW output. This
API does not create a workflow evidence directory itself.

External R is needed only to regenerate original reference captures. If an
exact permitted prerequisite cannot be supplied, mark the dependent reference
execution **SKIP3: DEPENDENT ANALYSIS SKIPPED**, state the cause and recovery,
and retain the skip. Downloads/private installs are authorized where permitted;
absence is not an installation prohibition. If installation is prohibited,
name the affected analysis and prerequisite, and skip it. No restricted runtime
or registration details are bypassed.

Primary XML/full text, exact producer JARs and complete private evidence need a
separate checksummed transfer. Both literature reports remain off Pages. The
full toolbox goal is unfinished; totals remain 143 scoped families, 1,616
reviewed uses and 1,104 pending. Cell Ranger remains excluded.
