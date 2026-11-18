# Stratified LDSC API: warnings and required actions

**Qualification covers a prepared-data Java API.** It supplies selected
observed-scale partitioned Hsq and explicit binary-overlap summaries. Read the
[API guide](../../vignettes/stratified-ldsc.md) for inputs, result axes, weighting,
limits and missing diagnostics. There is no automatic author-data preparation
or paper reproduction. The root command catalog retains its separately
qualified unpartitioned workflow scope.

**Unavailable or forbidden prerequisites:** skip dependent work and identify
the permitted input/resource the user must supply. The API installs nothing
and needs no Python or R runtime for analysis.

**Parameter, design, numerical or capacity failure:** admit no complete result.
Retain the exact input/provenance/model choices and the failed attempt. Correct
the named issue before a new attempt. Do not remove failed SNPs, annotations or
blocks, substitute a pseudoinverse or replace missing/nonfinite diagnostics to
obtain a successful result. Workflow wrappers must preserve this distinction
when mapping unavailable prerequisites to SKIP3 and invalid analyses to FAIL2.

**Signed/out-of-range estimates:** retain final coefficients and heritability
unchanged and review reference/population/model adequacy. The source clamps
only initial weighting heritability. Fixed initial partitioned weights and
unpartitioned iterative weights are different estimators.

**Overlapping annotations:** supply matching reference intersection counts and
the unique-universe count. Coefficient allocations and sum of annotation counts
do not describe unique overlapping-category heritability. Baseline/zero-contrast
enrichment P values remain missing; the log-P API sentinel is NaN. Continuous
overlap models, liability conversion, study-specific sidedness/corrections and
multivariate/spatial/wrapper workflows need explicit separate methods.

**Development history retained:** the first nine-test attempt had three failed
assertions despite passing all numerical field comparisons. The original
fixture lacked an above-one case; the coverage threshold and a parameter
exception expectation were also corrected. The first source/reference/test
snapshots remain in the evidence bundle. The final 48-model reference includes
the stronger extreme cases and all nine tests pass on both platforms.
