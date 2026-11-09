# Native MR-MEGA regression development

**The complete MR-MEGA integration remains unfinished and uncredited.** The
catalog stays at 91 new integrations, 94 including existing adapters, and
250 missing tools. This checkpoint implements and tests the prepared-cohort
regression calculation in Java. It does not supply ancestry-axis inference,
complete GWAS input handling or a user-data file workflow. The broader
250-paper objective remains active.

## Implemented Java calculation

`org.jlinalg.meta.MrMegaRegression.fitWeighted(effects, weights, ancestry)` uses
every supplied cohort in its supplied order. The ancestry matrix contains one
row per cohort and one column per explicitly supplied ancestry coordinate.
The method adds an intercept. Arrays and returned value arrays are protected
against mutation; no allele, ID or cohort alignment is inferred.

The result retains coefficients, residual-scaled standard errors and covariance,
fitted values, fitted-minus-observed residuals, weighted mean, centered and
zero-centered total sums of squares, residual sum of squares, all three test
statistics and degrees of freedom, and the reported-formula log Bayes factor.
The association statistic uses the absolute difference between zero-centered
total and residual sums of squares. Ancestry heterogeneity uses the centered
total, and residual heterogeneity uses the residual sum. The log-Bayes-factor
penalty counts participating cohorts and regression coefficients, not samples.

**This covariance is scaled by RSS / residual degrees of freedom.** Substituting
ordinary known-variance fixed-effect meta-regression would give different
uncertainty. `fit(effects, standardErrors, ancestry)` explicitly converts
double-precision SEs to weights; it does not emulate the original file reader's
float32 storage and reciprocal arithmetic. `fitWeighted` allows those source
weights to be supplied separately when that reader has been implemented and
validated. Neither entry point installs or invokes external software.

```java
double[] effects = {1, 2, 3, 4};
double[] weights = {1, 1, 1, 1};
double[][] axes = {{}, {}, {}, {}}; // explicit intercept-only control
var fit = org.jlinalg.meta.MrMegaRegression.fitWeighted(effects, weights, axes);
// intercept = 2.5; RSS = 5; intercept covariance = 5/12
```

The native method uses the project's existing JDistlib dependency for stable
chi-square survival probabilities. The accompanying development jars are thin
components; their reference probes additionally need the exact recorded project
runtime on the Java classpath. They are not self-contained command-line analysis
applications or replacements for the canonical executable.

## Warnings and required actions

**Probability fields are explicitly named `stableAssociationP`,
`stableAncestryP` and `stableResidualP`.** They are not original MR-MEGA output
fields. The original custom long-double calculation produced `-inf` in three
extreme-tail control fields. Those exact original outputs remain retained.
Stable Java probabilities match a separately executed R4.6.1 chi-square survival
reference; they never overwrite or relabel original values. Four zero-statistic
test boundaries retain the original undefined `NaN` convention, with base-R's
different boundary values retained separately.

Invalid, nonfinite, inconsistent, singular or numerically unresolved inputs
throw an explicit error and return no result. No cohort is silently removed.
MR-MEGA's complete-analysis cohort condition requires at least the number of
ancestry axes plus three participating cohorts. A future file workflow must keep
`SmallCohortCount` variants with an explicit disposition; a caller must not treat
this kernel's exception as a zero statistic or a nonsignificant result. Explicit
workspace capacities also apply. Original unchecked inversion is not repaired
into an apparently successful native result.

The original reference requires a permitted Linux compiler, make and zlib.
Where download, installation or execution is unavailable or forbidden, **skip
dependent original validation**, retain its SKIP3/FAIL2 receipt and read its
required action. Native mathematical calculation itself requires no original
MR-MEGA installation. Failed attempts and inputs are preserved; correct the named
problem and retry NEW paths. No permission request is implied by a generic
reference build when the user has already authorized it.

## Evidence and source differences

Both JDK21/JDK25 builds target Java17 and pass 54 complete original C++ regression
controls on Windows JVM21, Windows JVM25 and Linux JVM21: 324 complete numerical
comparisons. Six contract suites cover the analytic intercept result, defensive
copies, zero-test behavior and eleven invalid/unsupported input refusals.
The largest observed scaled numerical difference is approximately
1.03e-14; the largest relative difference from the separate R probability
reference is approximately 3.77e-15. No actual JVM17 run is claimed.

The reference is the unchanged official
[MR-MEGA0.2 archive](https://tools.gi.ut.ee/tools/MR-MEGA_v0.2.zip), SHA-256
`e91eb5bdba0a398619e6cd98c351ea72d94e09d6f5a1d25bb7da8a66da938ffe`.
Its 100 archive members remain unchanged in the private reference build.
The test probe calls original public regression objects and retains their
full-precision coefficients, covariance and statistics. It does not patch the
original sources or claim original file-workflow execution. Upstream source and
binary bytes are not redistributed in this development evidence.

The [authors' documentation](https://genomics.ut.ee/en/tools) describes the small
P-value limitation and a separate R recalculation. Its stated default of four
ancestry axes differs from the supplied source's command-line default of three.
Do not resolve that discrepancy by guessing: supply the requested axis count
explicitly. The original source also stores frequencies/effects/SEs in float32,
uses a particular per-bin ancestry-marker selection, and uses the lower OR
confidence bound in its effect conversion. Those file/axis/GC behaviors are
remaining implementation and validation requirements.

The read-only full-cohort source audit drafts three primary MR-MEGA reviews
without changing the current 1,009-review catalog. P072 uses three axes, a
six-of-seven cohort requirement, no pre-meta-analysis GC and separate fine-mapping
from lnBF. P105 uses four axes and explicitly recalculates small P values.
P162 examines ancestry-related index-variant heterogeneity and leaves the
edition/axis settings unspecified; its SuSiEx fine-mapping is separate.

Frozen evidence verification checks stored integrity and numerical relationships.
It does not run C++, Java or R, independently refit current user data, authenticate
past execution, validate the missing file/ancestry workflow, or certify a paper
reproduction. The canonical executable and unrelated worktree changes stay
unchanged. Availability credit requires the remaining workflow and catalog gates.
