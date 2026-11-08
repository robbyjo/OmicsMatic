# LeafCutter model evaluation development

`org.jlinalg.sequence.LeafCutterModels` evaluates supplied parameters using the
multi-concentration and robust Dirichlet-multinomial models from LeafCutter
revision `2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb` (R package version 0.2.9).
It also computes the original two-condition effect definition and nested-model
chi-square tail. It uses existing JDistLib log-gamma and distribution functions.

**This API does not fit or optimize a model. Full LeafCutter remains unfinished
and uncounted.** The original R/Stan runtime supplies the estimates used in the
controls. A check of an objective at retained estimates does not independently
refit them, establish a global optimum, authenticate their origin or calibrate
the method for a biological cohort. The execution adapter and seven-paper use
review remain separate work. No release or canonical executable is replaced.

Native clustering and phenotype preprocessing are documented in
[LEAFCUTTER_DEVELOPMENT.md](LEAFCUTTER_DEVELOPMENT.md) and
[LEAFCUTTER_PHENOTYPES_DEVELOPMENT.md](LEAFCUTTER_PHENOTYPES_DEVELOPMENT.md).

## Parameter and matrix axes

Counts have sample rows and intron columns. The design has the same sample rows
and covariate columns. `Parameters.betaRaw` has covariate rows and intron columns;
each row is a strictly interior simplex. `betaScale` has one value per covariate
and `concentration` one positive value per intron. The real coefficient is
`betaScale[p] * (betaRaw[p][k] - 1/K)`. The robust model also requires an outlier
probability strictly between zero and one.

`logPosterior` follows Stan's constrained parameter objective. It includes the
concentration prior and, for the robust model, the outlier-probability prior and
sample mixture. It omits parameter-independent prior constants, the transformation
Jacobian and the multinomial coefficient, as the original source does. Treating
this as a generic Dirichlet-multinomial likelihood changes its meaning.

`effectSizes` requires intercept and group as the first two coefficient rows.
It follows the original exp/normalize, concentration-weight, normalize sequence,
holding other covariates at zero. The perturbed-minus-baseline values are the
reported delta PSI. They are not the softmax of group coefficients alone.

`anova` takes the retained null and full objective values, model column counts
and intron count. It uses `(fullColumns-nullColumns)*(introns-1)` degrees of
freedom and the upper chi-square tail of twice the objective difference. A
negative original difference remains negative and has upper-tail probability one.

## Warnings and required actions

**Preserve full numerical precision.** A 15-significant-digit JSON export changed
two near-null p-values by about `2.45e-10` when nearly equal objectives were
subtracted. The failed comparison and rounded files remain retained. The corrected
binding exports the original binary R fits with `digits=I(17)` and verifies a
double round trip. No model is refitted to fix serialization, and comparison
tolerances are not widened. Keep original RDS objects alongside readable outputs.

**Invalid or undefined model inputs return no value.** Preserve the original
count/design/parameter families, correct the named cause and repeat the entire
call. Never round counts, repair a simplex, replace an undefined value with zero
or drop a failing sample/intron. Counts and per-sample totals above `2^53` are
outside this evaluator's exact Stan-double profile. Native clustering can retain
larger exact int64 counts; that does not establish their eligibility for this
model profile. Source exp overflow in the effect calculation is rejected rather
than silently replaced by a different algorithm.

Logical matrix capacity bounds are not operating-system memory quotas. Original
cluster filtering, zero-coverage sample removal and constant-covariate removal
are retained in the R controls; this evaluator does not perform those operations.
Filtered clusters keep their source reason strings and undefined table cells.
R locale cluster ordering is retained. A zero optimizer return code is checked
for every retained fit, but does not establish a global optimum. The original
function may discard a null-refit attempt; its complete stdout/stderr and source
logic remain available, without a claim that it reports every discarded return
code.

For upstream stages, missing, different or forbidden required software keeps the
dependent analysis skipped (**SKIP3**). Install the exact permitted runtime
separately or supply an allowed matching profile. **FAIL2 admits no result from
an incomplete atomic batch**, including earlier successful stages. Preserve all
raw warnings/errors, failed attempts, original fits and process cleanup receipts;
correct the named cause and retry into new paths.

## Retained validation

The development controls include original fixed-parameter Stan evaluations,
smart/random initializations, ordinary/robust fits, a three-intron covariate fit,
and differential-splicing batches with successful and filtered clusters. Separate
Python mathematics and actual Java 21/25 on Windows plus Java 21 on Linux check
the original objectives, effects and tails. Complete source/runtime identities,
binary fits and failed local attempts are required evidence before this component
is sealed. Compilation or partial comparison alone grants no integration credit.

The completed comparison checks 56 evaluations on each of the three JVMs:
16 fixed-parameter objectives, 20 objectives at retained optimizer fits,
10 ANOVA families and 10 effect families covering 23 introns. The largest
observed numerical discrepancy is `1.2164491636212915e-11`. The original batches
retain 14 filtered-cluster dispositions and 56 undefined table fields. Fourteen
invalid-input cases are rejected, and 11 comparator tests exercise missing states,
complete families, ordering, degrees of freedom and the observed precision failure.

The sealed record is
[`leafcutter-models-development/VALIDATION.json`](leafcutter-models-development/VALIDATION.json).
Recheck every retained comparison and source identity without running R or Stan:

```bash
python -B tools/literature/leafcutter_models_evidence.py
```
