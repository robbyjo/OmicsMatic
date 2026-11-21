# Native limma variance components

`org.jlinalg.differential.LimmaVarianceModeration` implements selected original
limma **3.68.5** variance routines in Java. No R process or installation is
required when using these native methods. This is a component of the ongoing
limma integration; it adds **no catalog family or paper-review credit** yet.

| Java method | Original source operation | Supported behavior |
| --- | --- | --- |
| `fitFDistribution` | `fitFDist(x, df1, covariate=NULL)` | Untrended prior scale and df, original informative-value filtering and median-relative offsets |
| `squeezeLegacy` | `squeezeVar(..., legacy=TRUE, robust=FALSE, covariate=NULL)` | Explicit legacy estimation, including the short-input return and vector zero-df handling |
| `posterior` | `.squeezeVar` | Supplied scalar/per-feature prior variances and df, finite and mixed/infinite-prior branches |
| `inverseTrigamma` | `trigammaInverse` | Original scalar branches and Newton iteration; nonconvergence fails explicitly |
| `logMinusDigamma` | statmod 1.5.2 `logmdigamma` | Real-input recurrence and original asymptotic polynomial |

The newer `squeezeVar` default selects a different estimator for unequal residual
df. **`squeezeLegacy` always requires that legacy choice by name.** It does not
implement or substitute for the modern unequal-df estimator. Trend/spline and
robust hyperparameter fitting, fitted models, moderated t/B/F statistics, TREAT,
voom, cameraPR, batch removal and dreamlet execution remain separate work.
Supplied posterior hyperparameters can be vectors, but that does not qualify
the algorithm that estimated them.

Inputs must have the declared scalar or per-feature lengths. Partial R vector
recycling is refused. The native API preserves the source early-return order
for zero/one inputs to `fitFDist` and fewer than three inputs to `squeezeVar`.
Arrays are copied; neither inputs nor returned immutable snapshots expose
internal mutable arrays. The legacy generic `EmpiricalBayesDifferential` and
existing `limma`/`voom` inference names are unchanged and remain method overlap.

## Warnings and result admission

**FAIL2: NO COMPLETE RESULT.** Check `Prior.finite()` or `Result.finite()` and
read `warnings()` before accepting the returned variance component. Original
NA/NaN/infinite or negative invalid outcomes are retained or explicitly refused;
they are not replaced with successful values. Infinite **prior df** is valid
and remains infinite; the method accepts its source-defined pooled-variance
limit. Raw special-function and `posterior` arrays require caller checks for
nonfinite outcomes; those primitive returns are not complete analysis bundles.

Original `fitFDist` filters nonfinite/negative variances and uninformative df
for prior estimation. `informative()`, `filtered()` and `offset()` expose that
behavior. Tiny accepted negative variances are treated as in the source. The
original median-relative offset and zero-variance warnings are preserved;
they are not the unrelated fixed floor used by the generic estimator.
`squeezeVar`'s original vector-df zeroing is also retained. Preserve the raw
input/df and diagnostics, investigate the stated cause and retry new output.
Do not silently substitute another edition or estimator to obtain a result.

**SKIP3: dependent validation skipped.** Running the native component needs no
R runtime. Regenerating the independent original controls requires exact
permitted R4.6.1, limma3.68.5/statmod1.5.2 and recovered matching sources. If these
are unavailable or installation is forbidden, skip that regeneration explicitly;
do not describe it as executed. Independent Linux source compilation remains
skipped because javac is absent on the current Linux host; transferred
Java17-target classes were executed on Linux Java21.

## Independent original controls

The official [limma archive](https://bioconductor.org/packages/3.23/bioc/src/contrib/limma_3.68.5.tar.gz)
is 2,842,081 bytes, SHA-256
`40b1d2059a76ad98db4bf80139760acf8e8bae92a8265aaaf16ac199831533f4`.
The official [statmod archive](https://cran.r-project.org/src/contrib/statmod_1.5.2.tar.gz)
is 93,227 bytes, SHA-256
`5f83d82043c5e26f1048fc0139580da1b7e40becf95fb4cb26ed3afcd46a581d`.
Archive/member pins are packaged in
`src/main/resources/org/jlinalg/omics/limma-variance/SOURCE-PROFILE.json`.
Recovered original R source, author attribution and edition are retained there.
limma is GPL >=2; statmod permits GPL-2 or GPL-3. Derived Java and selected
redistributed source use the project's GPL-3 terms; see the root LICENSE.

The [reference generator](../../tools/literature/limma_variance_reference/generate.R)
first checks exact original namespace function bodies/formals against recovered
source functions. It calls the original namespaces, retaining original objects,
warnings and errors. The test fixture contains 372 original controls, including
seven original errors and 36 warning-bearing cases. It covers empty/short inputs,
zeros, source filtering, unequal supplied df under explicit legacy selection,
extreme scales/df, supplied mixed priors and mathematical boundary values.

All **379 tests pass per JVM** on Windows Java21/25 and Linux Java21. Each host
compares **23,873 original numeric fields**; the largest relative difference
is `2.041495319926257e-15`. The normal JAR supplies the scientific classes; the
developer launcher checks their code origin. A runnable example also matches
all seven original scale/df/posterior values on all three hosts. All 23,548
predecessor JAR payloads are unchanged; additions are three native classes and
four resources plus their directory entry. These checks qualify the listed
variance operations, not author datasets or a whole limma workflow.

## Run the component

From a fresh clone/isolated checkout with the project prerequisites:

```text
gradlew.bat test --tests org.jlinalg.differential.LimmaVarianceModerationTest executableJar --no-daemon
```

See the [runnable Java example](../../examples/limma-variance/README.md) and
[saved qualification checkpoint](limma-variance-checkpoint/README.md).
The [paper-role proposal](limma-source-review-proposal/README.md) distinguishes
the actual operations, editions, providers and remaining gaps across 20
candidates; it changes no canonical observations or reviews.
