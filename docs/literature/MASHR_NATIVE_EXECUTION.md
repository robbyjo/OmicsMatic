# Native mash fit and file execution: development checkpoint

**Development only.** Native Java now fits representative training effects and
predicts complete target posteriors from files, including a single training or
target feature. It also reloads an explicitly expanded model without refitting
or applying another scale grid. The catalog still treats mashr as missing:
managed upstream setup, public `tools`/study routes, bundle admission, remaining
paper-specific roles and scientific review are unfinished.

Validation is recorded in `MASHR_NATIVE_EXECUTION_VALIDATION.json`; complete
controls, source, tests, commands and retained failure evidence are in
`mashr-native-execution-controls.zip`. Earlier native/workflow development proofs
describe their saved artifacts. Their source hashes were not rewritten to cover
the revised Gaussian kernel.

## Run the one-feature example

Use the development JAR at
`build/literature-review/mashr-runtime/native-execution-1/package-5/omicsmatic-0.3.7-mashr-development.jar`.
The canonical executable in `build/cli` has not been replaced. These commands
run from the repository root with Java 21; Java 25 was also tested. No R package,
installer, download or background service is required for this native example.
All output and copied-request directories must be new. For another attempt,
choose new names and preserve the old diagnostics.

```powershell
$mashJar = (Resolve-Path 'build/literature-review/mashr-runtime/native-execution-1/package-5/omicsmatic-0.3.7-mashr-development.jar').Path
java -Xmx128m -cp $mashJar org.jlinalg.cli.MashNativeExecution --input examples/mashr-native-execution/fit-input --out build/mash-native-fit-demo
```

The example really contains one feature with effects `(10,10)`, standard errors
`(1,1)`, residual identity, a zero-effect component, and the supplied covariance
`100 * ones(2,2)`. The scale grid is explicitly `1`, normalization is explicitly
false, and the Dirichlet prior is `(10,1)`. The same feature is intentionally in
both the training and target populations. Fitted weights are approximately
`(0.9,0.1)`; each posterior mean is `2000/201`, and each posterior SD is
`sqrt(100/201)`. The implementation never duplicates this feature to work around
the original wrappers' dimension failures.

To predict a different feature using that model:

```powershell
Copy-Item -LiteralPath examples/mashr-native-execution/predict-input -Destination build/mash-native-predict-input -Recurse
Copy-Item -LiteralPath build/mash-native-fit-demo/model -Destination build/mash-native-predict-input/model -Recurse
java -Xmx128m -cp $mashJar org.jlinalg.cli.MashNativeExecution --input build/mash-native-predict-input --out build/mash-native-predict-demo
```

The new feature has effects `(2,-3)` and standard errors `(1,1)`. Its values do
not update the prior. Prediction settings contain only `schema_version=2` and
`operation=predict`; Gaussian settings and condition identities come from the
model. Its `representation.tsv` explicitly says `fully_expanded`. The model's
complete SHA-256 manifest is checked before use. This detects altered/missing
files; it is an integrity check, not a signature or scientific certification.

## Prepare a request

All input is UTF-8 TSV with literal tabs, finite numeric cells and explicit
identities. `effects.tsv` and `standard-errors.tsv` have a `feature` first column,
followed by the same unique condition names in the same order. Their feature
names and order must exactly match `populations.tsv`. Numeric-looking names such
as `0001` remain strings. Missing effects, missing SE, zero/nonpositive SE, and
unassigned rows fail; no P-value conversion, missingness inference, zero filling,
condition pooling or transcript-version removal occurs.

`populations.tsv` has exactly `feature`, `train`, `strong`, `target` columns.
Flags are literal `0` or `1`. Training and target overlap is allowed. A fit
requires at least one training and one target row. Only training rows enter
mixture fitting, null covariance estimation and residual EM. Strong membership
is recorded in `strong-ids.tsv` and prominently reported as unused for covariance
learning: this executor takes canonical or supplied bases and does not perform
PCA/extreme-deconvolution learning. Prediction requests must contain targets only.

`settings.tsv` begins with `key` and `value`. The complete fit settings are in
the runnable example. Schema 1 original-mashr requests, unknown/missing keys and
upstream optimizer names are rejected instead of being reinterpreted.

| Setting | Meaning |
| --- | --- |
| `operation` | `fit_predict` or `predict`; prediction loads the model described above. |
| `basis` | Fit only: `canonical` or `supplied`. Canonical uses the condition-labelled identity, singletons, equal-effect and three heterogeneity bases. |
| `grid` | Explicit comma-separated, strictly increasing positive scales. Covariances are multiplied by **squared** scales, in grid-major order. |
| `normalize` | Literal boolean; divide each nonzero basis by its largest diagonal before scale expansion. |
| `point_mass` | Literal boolean; prepend an explicit all-zero covariance if true. |
| `prior` | `uniform`, `nullbiased` (requires the prepended point mass), or `supplied` in `prior.tsv`, one parameter per expanded component. Parameters must be at least one. |
| `optimizer` | Must be `native_em`: plain EM with an independent concave-objective simplex dual-gap stopping certificate. |
| `weight_maxiter`, `weight_dual_gap` | Explicit iteration limit and relative dual-gap tolerance. The full optimization history is retained. |
| `alpha` | Effect/SE scaling exponent; posterior means/covariances return to the original effect scale. |
| `posterior_threshold` | Posterior summaries retain weights strictly above this threshold; full-mixture likelihoods retain every component. |
| `covariance_tolerance` | Explicit symmetry/PSD roundoff tolerance, not permission for arbitrary jitter. |
| `minimum_se`, `numeric_sensitivity` | Explicit SE-zero threshold and maximum tolerated log-score ULP diagnostic. |
| `residual` | `fixed`, `simple`, `em_correlation` or `em_covariance`. |
| `residual_maxiter`, `residual_tolerance` | Residual EM proposal limit and absolute unpenalized log-likelihood tolerance; retained in all fit requests. |

Supplied bases have `basis/component-names.tsv` and square numeric matrices
`basis/component-1.tsv`, `component-2.tsv`, etc. Matrix indices start at one;
duplicate component labels remain separate indexed components. The label count
defines the complete basis count. Basis files contain no headers. Expanded model
components are actual covariances, distinct from unexpanded bases. Exported
models are ready for `predict`; an RDS or raw original-mashr model directory is
not directly accepted as this typed model format.

For `fixed` or either EM mode, supply a square `residual.tsv`. For `simple`, omit
that file and add exactly `null_z_threshold` and `null_correlation` settings.
The estimator selects training rows with **every** absolute Z strictly below
the threshold, computes centered sample covariance with denominator `N-1`, and
optionally converts it to correlation. It records every selected feature. There
must be at least `max(number of conditions,2)` selected rows; undefined or
singular estimates are rejected rather than replaced by identity or jitter.
Unexpected files, including inputs unused by the chosen request, are rejected.

## Inspect results and failures

Successful exit 0 means this development execution completed. It does not admit
a public study bundle. `result.tsv` explicitly records that distinction.
`SHA256SUMS` covers every other regular output file. The output retains a complete
input snapshot, exact population identities, expanded model, optimizer histories,
residual proposals, accepted/rejected steps, warnings and all requested posterior
rows. Output paths cannot overlap input paths, and existing outputs are preserved.

Posterior numeric tables have **no header**. Rows follow `target-ids.tsv`;
condition columns follow `conditions.tsv`. `mean`, `sd`, `negative`, `zero`,
`lfsr` and `standardized-residual-mean` have one column per condition. `weights`
and `component-loglik` follow `model/component-names.tsv`. Covariances flatten
columns first (`i + j * number_of_conditions`). Each `loglik.tsv` row contains
original-scale log likelihood, scaled-data log likelihood, and retained prior
mass, in that order. Negative probabilities exclude zero-effect mass; LFSR
includes it and can exceed 0.5. No unreported significance cutoff is applied.

**Exit 2 / FAIL2 means incomplete.** Read `FAILURE.txt` and the prominent failure
section in `WARNINGS.md`. Convergence limits, invalid inputs, altered models,
cancelled work and capacity limits never become prefix success. Complete
candidate weight fits/proposals already written remain available, but no
`result.tsv` or final `SHA256SUMS` is admitted. Correct the named issue or raise
only a permitted budget, then retry into a **new** directory. The file API accepts
explicit row, input/output byte, storage and total-work limits; it also checks
planned allocations against half the current Java heap. The command uses
portable default limits and a 100,000-row maximum; raising the heap alone does
not override those limits. No automatic subsampling is performed.

Residual EM is the ad hoc source-style update, not a global residual optimizer.
Each proposed residual is fitted from uniform weights. A decrease in the full
unpenalized likelihood keeps the preceding accepted model and retains the
rejected candidate with an explicit `RESIDUAL_ROLLBACK` warning. Hitting the
iteration limit without a verified tolerance/rollback stop fails. Native
accounting recognizes a verified tolerance on the final allowed proposal;
upstream's legacy counter/history behavior differs. Native plain EM and upstream
mixSQP/SQUAREM need not give identical weights for nonidentified or approximately
optimized mixtures. Original-model prediction tests supply the actual original
fitted weights rather than claiming optimizer identity.

## Numerical evidence and remaining work

The actual JAR passed **295 focused tests on each of Windows Java 21, Windows
Java 25 and Linux Java 21**, with 128 MiB heaps and no skipped/aborted/failed
tests. This includes the previous 220 Gaussian, weight and original-workflow
checks, 17 native-fit tests and 58 file-execution/reference checks. Independent
processes on each JVM also ran fitting, model reuse and an expected invalid-SE
FAIL2, with analytic posterior checks and complete output-manifest verification.
This is a focused matrix; the whole project test suite was not rerun here.

Fresh calls to the installed original mashr 0.2.79 calculated standardized
conditional-noise means and second moments for every training row of 17 earlier
original workflows. The private R home and library were unchanged across those
calls. Original vector likelihoods, mixSQP and conditional primitives also
provide the true singleton reference without changing or padding the original
package. All 17 original models, including learned PCA/ED models, pass complete
native-file predictions against their original target tables. These validate
explicit model import and prediction; they do not implement native PCA/ED.

One numerical departure is deliberately retained. With covariance `1e200 * I`
and observed effects of order `1e100`, the original second-moment expression
cancels to zero. The native implementation computes the conditional standardized
noise mean through triangular solves and obtains the analytic identity noise
covariance. It does not subtract two indistinguishable large effect means.
Unresolvable prior scale disparities also fail explicitly instead of creating
false zero-effect mass. The original results and earlier failed attempts remain
in the evidence; no broad source-equivalence claim covers this boundary.

Next work remains public command/study integration and bundle gates, managed
upstream covariance-learning preparation, sharing/composite paper roles,
supplement review and the eight mashr paper-role reviews. No catalog promotion,
release, deployment or new GitHub push is part of this checkpoint. The 250-paper
catalog remains at 61 new scoped integrations (13 native, 48 upstream adapters),
with 259 named tools missing, 48 method-overlap entries and Cell Ranger excluded
at the user's request.
