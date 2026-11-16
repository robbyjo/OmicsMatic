# Native PoPS development

**Available:** native Java PoPS v0.2 regression for finite, already aligned
gene/feature matrices. **Open:** the user-facing workflow, bounded feature-file
streaming, complete input capture/replay, original LassoCV/v0.1 adapters and
cumulative catalog admission. This checkpoint adds no catalog integration credit.
Build current source; the released v0.4.0 download lacks this API.

```powershell
.\gradlew.bat executableJar
java -cp build/cli/omicsmatic-0.4.0.jar examples/pops/PopsPreparedExample.java
```

The example uses synthetic finite matrices and the portable Java CPU backend.
The public API is `org.jlinalg.genetics.Pops.fit`. It requires target rows and
feature rows in the same gene order. Supply independent masks for target
covariate projection, marginal feature selection and training. The result contains
all feature marginals, selected ordinals, coefficients, scores, forward-selection
ranks and all 25 ridge cross-validation scores. `Result.predict` accepts the
original feature columns in their original order and adds no intercept or score
standardization. No ID matching, missing-value imputation or biological annotation
is inferred.

The implementation follows the exact selected [official v0.2 source](https://github.com/FinucaneLab/pops/tree/76eb86cba10254490003c8f4dc7ff5ce492d3667):

- Six MAGMA controls, optional target-score covariates, separate chromosome/HLA
  masks, strict marginal p-value filtering, controls, feature subsets/limits and
  forward stepwise selection with its original overriding behavior.
- Chromosome-block GLS whitening and the original population-MSE marginal
  estimates. The marginal-filter path removes NaN marginal features, including
  controls. Constant-feature NaNs remain visible in the marginal table.
- RidgeCV over the original 25 penalties from `10^-2` to `10^10`, with negative
  leave-one-out MSE and no intercept; minimum-norm OLS is separately selectable.
- Exact NumPy 1.19.5/Pandas 1.0.5 indirect quicksort behavior for the tested tied
  feature rankings. It does not introduce a different stable tie rule.

Call `PopsCovariance.regularizeMagma` explicitly for MAGMA covariance. Its shift is
`abs(min(0, minimum block eigenvalue)) + 0.05 + 0.9*max(0, populationVar(target)-1)`.
Custom target-score covariance is used as supplied. The eigenvalues are computed
in Java with a bounded Householder/QL solver adapted from the
[public-domain JAMA 1.0.3 reference](https://math.nist.gov/javanumerics/jama/).
The covariance stage uses Java CPU arithmetic; the remaining decompositions honor
the requested backend. HLA removal follows the source's exact chromosome string
`6` and inclusive TSS interval 20–40 Mb.

The exact reference environment was installed successfully: Python 3.8.20,
NumPy 1.19.5, SciPy 1.5.2, Pandas 1.0.5 and scikit-learn 0.24.2. The original
program was executed unchanged. Controls cover 11 successful complete CLI
analyses, two expected failures, 225 ridge cross-validation values, 50 indirect
sorting cases and 22 independent eigenvalue spectra, including singular and
indefinite matrices and scales from `10^-250` to `10^250`. All 17 Java tests pass
on Windows and Linux Java 21 without skips.

The complete official schizophrenia tutorial was also run without gene/feature
subsampling: 16,980 MAGMA target genes, 16,752 training genes, 18,383 prediction
genes, 508 features and 258 selected features. Original Python ran under a 16 GiB
address-space bound; native Java used an 8 GiB heap and the portable CPU backend.
Both selected alpha `31622.776601683792`. Maximum absolute differences were
`2.414e-14` for scores, `4.808e-14` for marginals and `2.360e-16` for coefficients.
Native processing took about 127 seconds on this workstation. These are software
controls on the supplied tutorial, not reproduction of a catalog paper's author
environment or biological findings.

**Retained failures and interruption:** the first tutorial binding stopped before
fitting because the validation reader initially rejected column-major NPY input.
The corrected reader consumes the declared storage order. A second benchmark
was deliberately stopped after profiling eigenvector accumulation; its original
sources, JAR, process identity and raw records remain retained. The qualified
revision uses the independently checked eigenvalue-only solver. The original
no-feature and unregularized-indefinite-covariance failures remain negative
controls; they supply no accepted model.

The first complete private-file readback encountered Windows error 1920 at a
Linux symbolic link inside the declared runtime exclusion. The revised consumer
prunes that exclusion before file inspection. Its failed record and original
consumer source remain retained alongside the later verification.

**Required actions:** use this finite prepared-matrix API only within its stated
scope. Version 0.1 uses distinct feature-selection and chromosome-held-out
prediction programs; do not substitute v0.2. The v0.1 source is audited and retained,
but its execution adapter is still open. Do not infer paper settings or turn a
downloaded PoPS score table into a claim that PoPS was fitted locally.

Native fitting requires no Python installation. Re-executing original controls
requires the exact recorded environment. Install it only where permitted. If it
is unavailable or installation is forbidden, skip the dependent original-runtime
analysis, prominently report `SKIP3`, name the missing interpreter/package/version,
and retain the saved controls. Never silently substitute newer packages.
Analysis, decomposition or verification failure admits no fitted result; retain
the error and inputs, resolve the named cause, and retry using a new output path.

The full 250-paper text audit establishes six software-use papers and rejects one
lipid-name match. P236 is a recovered observation pending catalog admission.

| Paper | Source role |
| --- | --- |
| [P088](https://doi.org/10.1038/s41588-024-01975-5) | PoPS v0.2 after MAGMA 1.10; gene ranks enter separately weighted cardiomyopathy prioritization. |
| [P090](https://doi.org/10.1038/s41588-024-01952-y) | PoPS v0.1; locus top-three candidates precede an eight-source prioritization score. |
| [P120](https://doi.org/10.1038/s41588-025-02410-z) | PoPS v0.2, explicitly listed in Code availability; top-decile scores support hypothyroidism gene mapping. |
| [P153](https://doi.org/10.1038/s41588-026-02695-8) | PoPS top-two gene ranks intersect model top-two predictions per credible set; a fresh fit/version is not established. |
| [P220](https://doi.org/10.1016/j.ajhg.2025.08.013) | Downloaded published BMI, WHRadjBMI and body-fat scores for downstream enrichment comparisons. |
| [P236](https://doi.org/10.1016/j.ajhg.2025.07.006) | Published PoPs comparator scores for Speos ulcerative-colitis analyses; recovered mixed-case name. |

P239's `POPS` denotes a membrane lipid in molecular dynamics. It is explicitly
excluded from PoPS software counts. Exact primary XML/paragraph identities and
six scoped reviews are retained with the development evidence. Exact author
commits and complete author options are not established by this checkpoint.

The existing catalog remains 250 papers, 2,691 observations and 124 scoped
integrations (22 native, 102 upstream), with 263 missing families. Its historical
verifiers retain their original source identities. Current PoPS additions require
their separate development source/evidence consumer; never refresh old verifier
hashes to suppress a refusal. The full toolbox objective remains open.

Saved source/record verification needs only Python's standard library and the
Git source checkout. It does not rerun an estimator or require the original
Python environment:

```powershell
python -B tools/literature/pops_native/records.py --out build/NEW-pops-record-check
python -B tools/literature/pops_native/records.py --rescan-primary --private-base build/pops --out build/NEW-pops-complete-check
```

The second command additionally requires the exact primary XML cache and the
named private development records. Missing optional records/rescan inputs produce
`SKIP3` with recovery instructions and no completion marker. An altered record or
source produces `FAIL2`. The complete named private artifact inventory excludes
the reinstallable environment/package cache; its exact lock, install/readiness
receipts and interpreter identity are retained. This is not adoption of an
original-runtime execution adapter.
