# Native multivariate shrinkage foundation

> **Development foundation; mashr is still missing from the public toolbox.**
> `MashGaussian` evaluates a supplied Gaussian covariance mixture and
> `MashMixtureWeights` fits its proportions. There is no `mashr-tools` command,
> managed upstream analysis adapter, learned-covariance workflow or whole-paper
> reproduction. Do not count this foundation as another completed tool.

The Java implementation supports common positive-definite residual covariance,
singular positive-semidefinite priors, explicit effect/SE matrices, alpha scaling,
canonical covariance bases, posterior means/SDs/covariances, negative and zero
probabilities, local false-sign rates, component likelihoods and mixture weights.
The canonical basis follows source order and uses condition names for singleton
components. Its null component and scale grid must be supplied explicitly.

The native weight fitter uses plain EM for a fixed likelihood matrix and a
Dirichlet prior whose parameters are at least one. It reports the complete
iteration history when requested and a floating-point simplex dual-gap estimate.
In exact arithmetic, concavity bounds the remaining objective error by
`(numberOfRows + sum(prior - 1)) * relativeDualGap`. This is a convergence check
for the supplied fixed-covariance objective, not proof of a unique mixture or a
biological model. The original `mixEM` optimizer uses SQUAREM acceleration;
iteration identity and nonidentified weights are not asserted.

## Run the synthetic Java example

The example needs the current development JAR and Java 17 or newer. Native
calculations require no R installation. Run from the project root in PowerShell:

```powershell
java -cp build/literature-review/mashr-runtime/native-development-1/package-2/omicsmatic-0.3.7-mashr-development.jar examples/mashr-native/MashGaussianExample.java
```

The four synthetic rows are the complete training population for this example.
It fits proportions for null, independent and equal-effect covariance matrices,
then prints all eight feature/condition posterior rows. For a real project, the
training population, covariance-learning population and target population must
be declared separately. Fitting mixture proportions only on strong hits can
bias the prior. See the [original data-driven mash workflow](https://stephenslab.github.io/mashr/articles/intro_mash_dd.html).

Use `fit.requireConverged()` before admitting a fitted model. Catching its
exception and continuing as if the fit converged is invalid. Keep the returned
diagnostics when increasing iteration or work limits.

## Required warnings and recovery

| Condition | Behavior and required action |
| --- | --- |
| `NONCONVERGED` | No converged model is admitted. Retain the fit and history; increase permitted iteration/work capacity or use a separately validated optimizer. |
| Allocation, dimension, work or cancellation failure | The method throws without returning a row/component prefix. Correct the complete input or explicitly increase a permitted budget/heap. |
| Nonfinite effects, missing values or invalid SE | Rejected. Supply signed effects and positive finite standard errors with a documented conversion and missingness policy. Unsigned P values do not supply direction. |
| Asymmetric/non-PSD prior or non-SPD residual covariance | Rejected outside the explicit roundoff tolerance. No diagonal jitter is added. Any admitted symmetrization or small negative-eigenvalue clipping is recorded in `numericalNotes`. |
| `PRIOR_PRUNING` | Posterior summaries retain weights **strictly greater than** the explicit threshold; full-mixture likelihoods retain all supplied components. Keep the threshold and component order in provenance. |
| `COMPONENT_SCORE_RESOLUTION` | A component log density has coarse absolute floating-point resolution. Inspect the named component and retained reference boundary. Dominant full/retained mixture scores must still pass the resolution gate; do not claim component-level source equivalence. |
| Unavailable original-reference dependencies | The dependent reference analysis must be skipped. Provide the required R/package or JVM test closure and repeat into a **new** output directory. Preserve failed/partial evidence. The current private R installer is not a managed user-analysis runtime. |

Callers must display `numericalNotes` prominently and retain explicit exceptions
and recovery actions. LFSR includes zero-effect mass: a point-mass null has LFSR
one, and a mixture LFSR can exceed 0.5. Negative probability excludes zero mass.
These conventions follow [mashr0.2.79 posterior reporting](https://github.com/stephenslab/mashr/blob/0.2.79/R/posterior.R).

No missing values are invented. In the reviewed papers, explicitly supplied
`effect=0, SE=1e6` and `effect=0, SE=1e10` are different stated policies; neither
is inferred automatically. SE values above the model's explicit zero threshold
are accepted only when the full numerical calculation passes admission.

## Retained validation

The checksummed Gaussian archive contains 43 synthetic models and 346 complete
feature rows, with both original R and Rcpp algorithms from mashr0.2.79. There
are 83 completed original calls and three retained R failures: the R algorithm
rejects the explicitly supplied SE1e10 cases as computationally singular. The
successful Rcpp outputs for those cases remain available and are compared.

One extreme two-condition component likelihood is an explicit departure:
with prior `[[1,1],[1,1]]`, effects `[1,2]` and SE `[1e-6,2e-6]`, both original
algorithms differ from the independent 80-digit determinant/quadratic-form
oracle by about 8,274 log units. The native augmented-QR result is within 20 log
units of that oracle; the observed error is about 4.68. This is not a claim of
component-level source equality. That component's posterior weight underflows
to zero in both implementations. Its remaining posterior summaries are checked
against the original outputs and the coarse-score warning is required.

The separate weight archive contains 26 complete fit problems and 52 original
optimizer calls, spanning uniform and null-biased priors. Native fits agree
within the combined objective bounds from their independent dual-gap checks.
Full original sources and their notices accompany both archives.

The actual development JAR passes 181 checks on each of Windows Java21,
Windows Java25 and Linux Java21, with a 128MiB heap, zero skips and zero failures.
This is focused native-foundation validation; the entire project test suite was
not rerun for this addition. The canonical executable remains unchanged.
See [the saved validation record](MASHR_NATIVE_DEVELOPMENT_VALIDATION.json).

The original reference runtime is R4.6.1 with mashr0.2.79 and a checksummed
18-package private library. This does not prove a source-to-binary rebuild of
the upstream packages. The first interrupted download and all failed collector
and native-test attempts are retained in the private build directory.

## Remaining mashr integration

Still required: a managed original-R adapter for PCA/extreme-deconvolution
covariance learning and residual-correlation EM; complete input parsing and
admission; separate training/strong/target populations; typed CLI and complete
output bundles; paper-specific sharing/concordance definitions; and the final
eight-paper role review. Source omissions remain unknown rather than guessed.
The catalog remains at 61 new scoped integrations (13 native, 48 upstream),
with mashr among the 259 missing named tools.
