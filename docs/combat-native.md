# Native continuous ComBat, sva 3.46.0 rules

`combat-native` adjusts a prepared continuous feature-by-sample matrix in Java.
It needs no R installation. It supports parametric/nonparametric empirical
Bayes, mean-only adjustment, singleton-batch mean-only selection, a reference
batch, an explicit numeric keep-design and original missing-cell behavior.
This is the continuous `ComBat` function. Count-based `ComBat_seq`, longitudinal
ComBat and surrogate-variable estimation require their separate models.

Build the executable JAR from current source. No released JAR is qualified by
this change. From the repository root, run the complete synthetic example:

```text
java -jar build/cli/omicsmatic-0.4.1.jar combat-native --request examples/combat-native/request.json
java -jar build/cli/omicsmatic-0.4.1.jar combat-native --verify build/combat-native-example-rev2-NEW --out build/combat-native-example-rev2-verified-NEW
```

Both output paths must be NEW and their parent directories must exist. Preserve
earlier outputs. The example is 64 synthetic features and 24 synthetic samples;
it is not biological evidence or a reproduction of the paper's reference panel.

## Input and request contract

All request keys in `examples/combat-native/request.json` are required. Unknown
or duplicated keys are refused. Paths resolve relative to the request file.
Use JSON `null` for absent `design` or `reference_batch`; use explicit Booleans
for `par_prior` and `mean_only`. The model selector is exactly `ComBat`, source
version `3.46.0`. A different requested model/version is skipped with SKIP3.

- `matrix`: UTF-8 TSV headed `feature_id`, then exact sample identifiers. Rows
  contain finite numeric literals or explicit `NA` missing cells. Literal
  numeric `NaN`/`Inf` inputs are refused. Feature and sample identifiers must be
  unique and nonblank; Unicode, surrounding spaces and the identifier `NA`
  remain literal. No trimming, matching, sorting, filtering or imputation occurs.
- `batch`: TSV headed `sample_id` and `batch`, with every sample in the exact
  matrix-column order. Factor levels follow first occurrence. This corresponds
  to explicitly supplying that factor-level order to the original R function.
- `design`: optional sample-by-covariate TSV headed `sample_id`, then unique
  numeric design-column names. Its row order must exactly match the matrix.
  Covariates must be finite. Supply an intercept explicitly if wanted; original
  ComBat drops all-one design columns except its reference-batch indicator.
- `units`: one of `continuous-values`, `log2-expression`, `VST-expression` or
  `methylation-M`. This records the caller's declaration, not a proof of units.
  This command does not create VST values, counts, normalization or z-scores.
- `population` and `preparation`: explicit descriptions of the actual supplied
  population and preparation. They do not authorize hidden preparation steps.
- `execution_policy`: `ALLOW` or `FORBID`; FORBID skips the dependent analysis.
- `max_iterations`, `max_input_bytes`, `max_entries`, `max_work`, `max_seconds`:
  explicit caller capacities. `max_entries` covers numeric matrix/design cells.
  `max_work` bounds complete input-dimension work: parametric
  `features*samples*((batches+design_columns)^2+max_iterations)`; nonparametric
  `features^2*samples`. These are conservative logical guards, not measured
  instructions or OS-enforced memory/time quotas. Cancellation and time checks
  run during intake and the fit. Hitting a guard fails the whole analysis.

Inputs are copied completely and pinned before fitting. Original input/request
identities are checked again afterward. No result is admitted from a changed,
incomplete or failed input population.

## Results, warnings and recovery

**Require exit 0 and `analysis-status.json` with `result_admitted=true`.** Read
`WARNINGS.md` even after success. A success message or an output matrix alone is
insufficient. Fresh verification uses the captured input bytes and recomputes
all five native result files; it checks exact output bytes and leaves the saved
bundle unchanged. Different numerical code/profile or changed bundle members
are refused. Hashes detect inconsistency; they do not authenticate coordinated
replacement of a producer and all its evidence.

Successful captures contain `adjusted.tsv`, `gamma.tsv`, `delta.tsv`,
`features.tsv`, `results.json`, literal input copies, request/parameter records,
source/producer identities, provenance, warnings and a complete file manifest.
Reference-batch and unadjusted rows retain their original data. Gamma/delta for
unadjusted features are `NOT_ESTIMATED`. They are not fitted zero/one effects.
Supplied missing cells remain `NA`. Original-rule undefined values at observed
cells are written as `NaN`/`Inf` in failed raw output and make the whole analysis
FAIL2. They are never stabilized into a successful answer. The native API
represents derived R NA and NaN with Java Double.NaN; derived undefined cells
export as `NaN`, while the supplied `NA` mask stays `NA`. This explicit encoding
difference does not change the full nonfinite mask or permit result admission.

**SKIP3 — dependent analysis skipped:** restore the named permitted input or
packaged Java prerequisite, select a separately qualified requested model, or
change an explicit FORBID policy if execution is intended. Retry NEW paths.
The command installs nothing automatically and needs no external R runtime.

**FAIL2 — no complete result:** retain the attempt and read its status, warning
and raw result files. Correct the named input/order/design, numerical
conditioning, undefined likelihood, convergence, cancellation or capacity
problem, then retry NEW paths. A failed capture cannot be verified as a result.
Do not refresh its seal, round/invert data, change identifiers or switch to the
legacy stabilized algorithm to conceal the failure.

## Source behavior and compatibility

The profile is bound to the original
[sva 3.46.0 archive](https://bioconductor.org/packages/3.16/bioc/src/contrib/sva_3.46.0.tar.gz),
[ComBat function](https://raw.githubusercontent.com/bioc/sva/RELEASE_3_16/R/ComBat.R)
and [helpers](https://raw.githubusercontent.com/bioc/sva/RELEASE_3_16/R/helper.R).
Their identities and Artistic-2.0 declaration are recorded in the packaged
`combat346/SOURCE.json`; original source/traces remain private. Reference
collection executes those unchanged R functions with actual BiocParallel and
matrixStats. It does not claim an installed/qualified full sva namespace.

The source-specific API is `ComBat.adjustSva346`, with an overload accepting a
caller cancellation/capacity check. Its tolerance is the original `1e-4`.
Existing `ComBat.adjust` and `batch-adjust` retain their earlier conventions.
They are not aliases for this source-specific profile. Relevant differences are:

- A retained missing cell selects original sample-residual-variance scaling for
  every feature, including otherwise complete rows.
- A missing cell prevents the original within-batch zero-variance screen from
  treating the observed values alone as a constant row.
- Original iteration stopping divides by signed prior values; replacing those
  denominators by absolute values can change the stopping iteration/result.
- Unavailable per-batch sample variances remain missing in likelihood sums.
  The full batch-design product preserves zero times NA/NaN propagation across
  batches; sparse missingness does not silently become a zero likelihood.
- Original nonparametric arithmetic can underflow to undefined likelihood
  ratios. This profile retains that outcome; the legacy log-space calculation
  can return a different, finite answer.

Java StrictMath evaluates the direct nonparametric formula. Agreement with R's
math library is numerical, not a claim of bitwise R/libm identity. The numeric
design uses Java rank/linear-solve conditioning guards. Ill-conditioned or
degenerate inputs may be refused even when an R implementation could fit them;
no generic fallback is applied. Fewer than two adjustable features are refused.
The full tool, arbitrary numerical boundary cases and original author runtime
stacks are not certified by the controlled reference comparisons.

For P108 this covers a selectable continuous ComBat operation from the named
sva 3.46.0 package. Exact author flags, input matrices and later HCP/covariate
adjustment remain unresolved/separate. Its `sva` provider label does not add a
second implementation. P086's ComBat_seq attribution/input-unit discrepancy and
P128's longitudinal model remain open. P185 does not identify its SVA entry
point or factor-count estimator. No paper is reproduced by this example.
