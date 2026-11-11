# Original mashr learning workflow: development checkpoint

> **Not yet a public toolbox integration.** This development driver runs original
> mashr0.2.79 covariance learning, representative-population fitting and complete
> target posteriors, followed by an independent Java numeric audit. Managed
> runtime admission, the public CLI, complete study bundles, integrated native
> singleton execution and final paper-role curation remain unfinished. Mashr is
> still marked **missing**; the catalog remains at 61 new scoped integrations.

The driver calls the original canonical-covariance builder, PCA, Bovy extreme
deconvolution, simple null-correlation estimator, residual-correlation EM,
mixture optimizer and posterior functions. Training, strong and target membership
are explicit. Strong and training populations may overlap, as may training and
target populations. Target values do not feed covariance or prior learning.
No signs, missing values, paper-specific settings or random-sampling guarantees
are inferred.

## Warnings, failures and required action

| Code or condition | Meaning and required action |
| --- | --- |
| `SOURCE_RCPP_SINGLE_FEATURE_UNAVAILABLE` | The inspected Rcpp profile aborts on a singleton posterior with multiple retained components. The driver reports FAIL2 before entering that path. Preserve the failure and use the native API example below; integrated native workflow execution is pending. No observations are padded or silently rerouted. |
| `SOURCE_R_SINGLE_FEATURE_DIMENSION` | The original R wrapper also fails on singleton likelihood/posterior dimensions in this profile. Selecting R is **not** a recovery for these cases. Preserve the source error and returned training model, when available. Use the native API until integration is complete. |
| `ED_TERMINATION_UNPROVEN` | The ED log reaches the declared iteration limit. Its wrapper exposes no convergence flag. Preserve the exact returned covariance candidates, increase the permitted limit, and repeat into a new directory. Reaching the limit is not a convergence certificate. |
| `RESIDUAL_EM_TERMINATION_UNPROVEN` | The source counter reaches the declared limit. Preserve the returned model, covariance, proportions and history; increase the limit and repeat. The original counter/history have legacy indexing and are not rewritten. |
| `MIXTURE_NONCONVERGED` | The independent fixed-covariance dual-gap check fails. Retain diagnostics and correct optimizer limits or the numerical discrepancy before admitting a fit. |
| `ED_LOCAL_OPTIMUM`, `ED_SOURCE_WARNING` | ED supplies a local covariance fit. Its six-decimal likelihood logs and any reported decrease are retained. Inspect these warnings; neither a monotonic path nor a global optimum is certified. |
| `SOURCE_RESIDUAL_EM_STOP` | Original residual EM can return the preceding model after a likelihood decrease. The returned history can omit the final accepted likelihood. The final model likelihood is recomputed and checked independently. This is the source stopping behavior, not a global convergence claim. |
| `SKIP3_DEPENDENCY_UNAVAILABLE` | A required exact package is unavailable. Install or supply the inspected profile only where permitted. If installation is unavailable or forbidden, keep the dependent analysis skipped. The analysis driver never installs packages. |
| Numeric, identity, capacity or cancellation failure | FAIL2; no sampled or partial result is admitted. Preserve original inputs and outputs, correct the named problem or explicitly increase a permitted budget/heap, and use a new output directory. |

Both original singleton failures are preserved, including the first Rcpp process
abort and the R dimension errors observed before failure guards were added.
This is evidence about the inspected R4.6.1/18-package profile, not a claim about
every upstream installation. The installed package sources were not patched.

`WARNINGS.md`, `source-status.tsv`, `source-progress.txt`, process stdout/stderr
and the returned models remain part of the evidence. R process exit0 means
`ORIGINAL_PROCESS_COMPLETE_UNAUDITED`, not successful study admission. Every
error includes recovery instructions. The independent Java gate checks numeric
consistency; process/runtime provenance and complete bundle integrity need
their own gates.

## Native recovery for one feature

The existing native Gaussian and mixture-weight APIs support one feature. This
complete synthetic example fits a two-condition fixed-covariance model without
padding, R or the failing original wrappers:

```powershell
java -Xmx128m -cp build/literature-review/mashr-runtime/workflow-development-1/package-4/omicsmatic-0.3.7-mashr-development.jar examples/mashr-native/MashSingletonExample.java
```

Use a validated JDK21 or JDK25 for this source-file command. The example verifies
its fitted proportions and posterior mean/SD against analytic values and was
also run as compiled Java17 bytecode on Windows Java21/25 and Linux Java21. It
is a native API demonstration; it does not reconstruct a paper or supply the
unfinished managed workflow. See [the native foundation guide](MASHR_NATIVE_DEVELOPMENT.md).

## Inspect or replay the development controls

The complete portable original corpus is
`src/test/resources/org/jlinalg/cli/mashr-workflow/original-controls.zip`.
It retains inputs, outputs, commands, logs, original RDS models, exact failed
ED/EM candidates, source notices and before/after Rhome/private-package file
fingerprints. It contains no upstream executable or patient data.

To rerun original synthetic controls in this workspace using the already
inspected private reference library, choose an absent output directory:

```powershell
python tools/literature/collect_mashr_workflow_controls.py `
  --runtime build/literature-review/mashr-runtime/reference-runtime-2 `
  --out build/literature-review/mashr-runtime/NEW-workflow-controls
```

This requires the existing Windows R4.6.1 reference setup. The controlled
missing-library case deliberately exercises SKIP3. The current reference
installer and collector are development helpers; a managed user-analysis
runtime is still required. Missing dependencies are not silently replaced.

Normalized inputs are UTF-8 tab-separated files: ordered feature-by-condition
`effects.tsv` and `standard-errors.tsv`, complete `populations.tsv` with literal
`train/strong/target` flags, and typed `settings.tsv`. A supplied residual matrix
also has explicit condition identities. Effects and SEs must have identical
ordered identities. Numeric-looking feature IDs retain leading zeroes. The
copied source inputs are checked again independently in Java.

Settings explicitly select alpha, canonical or canonical+PCA/ED covariances,
PC count, ED controls, residual mode/initialization/controls, prior, optimizer
and its controls, covariance normalization, point mass, positive scale grid,
prior-pruning threshold, original algorithm and fit dual-gap tolerance. The
inspected collector contains complete runnable settings for every tested branch;
unreported settings from a paper remain unknown rather than being filled from
these synthetic examples.

For retained-source numerical validation on all three JVMs, without rerunning R:

```powershell
python tools/literature/validate_mashr_workflow.py `
  --artifact build/literature-review/mashr-runtime/workflow-development-1/package-4/omicsmatic-0.3.7-mashr-development.jar `
  --out build/literature-review/mashr-runtime/NEW-workflow-JVM-validation
```

This requires the cached JUnit/JVM closure. It verifies class ownership from the
actual JAR, the embedded R driver and bound source/fixture/dependency bytes.

## Verified scope

The 26 original-process controls contain 17 completed original workflows,
eight expected FAIL2 outcomes and one dependency SKIP3. They cover both original
engines, alpha0/1, PCA→ED, simple and EM residual correlation, supplied residual
covariance, both mixture optimizers, absent point mass, disabled normalization,
overlapping populations, repeated component labels, numeric feature IDs and an
explicit SE1e10 target. Changing only target values leaves the learned model
byte-identical and changes the target posterior.

Across the 17 positive controls, Java checks all 1,700 training likelihood rows
and 440 target posterior rows, with 138,625 numeric comparisons. These are row
counts across controls, not distinct biological observations. It verifies
original effect/SE identities, population membership, every target mean/SD,
negative/zero probability, LFSR, covariance cell, component weight/likelihood,
and full mixture likelihood. Training objectives, likelihoods and dual gaps are
also checked. Repeated prior labels remain indexed rather than being merged.

The actual development JAR passes **220 focused tests on each** of Windows
Java21, Windows Java25 and Linux Java21, with a 128MiB heap, zero skips and zero
failures. The tests also reject altered posteriors, training likelihoods,
identities, population flags, input SEs, truncated/extra tables, missing
components and counterfeit success text. The entire project suite was not
rerun. The canonical executable remains unchanged.

The full Rhome and private package roots contain 5,637 fingerprinted files and
remain unchanged across the original controls. This is not an upstream
source-to-binary rebuild or a hermetic-host certificate. See
[the validation record](MASHR_WORKFLOW_DEVELOPMENT_VALIDATION.json) for exact
source, artifact and evidence pins. Public runtime/bundle admission, native
singleton orchestration, CLI integration and the eight-paper semantic review
remain required before promoting mashr in the catalog.
