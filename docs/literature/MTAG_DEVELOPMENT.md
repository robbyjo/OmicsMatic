# MTAG: native estimator development and original-runtime controls

**Development component only. MTAG remains missing in the tool catalog.** This
checkpoint adds a native Java estimator and an unadjusted GMM covariance method.
The typed `mtag-tools` workflow, runtime profile, root command and project bundle
admission are still open. The catalog remains at **97 new scoped integrations**
(19 native methods and 78 adapters), or 100 including three existing adapters.
No released executable is rebuilt by this work.

## What is available

`org.jlinalg.genetics.MtagEstimator` implements the finite-input formulas in
original [MTAG1.0.8, commit9e17f3c](https://github.com/JonJala/mtag/tree/9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc):
`mtag_analysis` and `gmm_omega`. Algorithms are attributed to Omeed Maghzian,
Raymond Walters and Patrick Turley under GPL3. Normal tails use the project's
separate, pinned JDistlib0.10.2 dependency.

Supply complete, explicitly aligned Z scores, positive per-variant sample sizes,
ordered variant/trait IDs and symmetric positive-definite Omega and Sigma
matrices. The native estimator uses Cholesky solves instead of explicit matrix
inverses. It returns standardized beta, standard error, MTAG factor, Z and
two-sided P for every supplied variant and trait. It does not infer sample
overlap, select traits, harmonize alleles, filter inputs or estimate Sigma.

The working-byte preflight includes a conservative allowance for row objects
and identity storage. A pre-publication review found that the first estimate
omitted that allowance; the original evidence is preserved separately and a
budget-boundary regression test covers the corrected preflight. Numerical
formulas and previously checked result cells are unchanged.

The GMM method returns the **unadjusted** covariance, including negative
entries. It does not perform the original positive-definiteness adjustment.
Native estimation refuses singular/perfect-correlation covariance matrices,
nonfinite data, nonpositive sample sizes, invalid identities, incomplete
dimensions and unassessable arithmetic. No jitter, clipping, imputation or
identifier repair is performed. The caller declares logical capacities; these
are not operating-system memory quotas. The current ceilings are 32 traits and
1,000,000 variants.

```java
import java.util.List;
import org.jlinalg.genetics.MtagEstimator;

var result = MtagEstimator.estimate(
    List.of("rsA", "rsB"), List.of("traitA", "traitB"),
    new double[][] {{2, -3}, {1, 2}},       // rows: variants; columns: traits
    new double[][] {{10000, 12000}, {10000, 12000}},
    new double[][] {{.0001, .00003}, {.00003, .0002}}, // Omega
    new double[][] {{1.1, .1}, {.1, 1.2}},            // Sigma
    new MtagEstimator.Limits(1000, 8, 10_000_000));
double[][] standardizedEffects = result.standardizedBeta();
```

The isolated API JARs in `mtag-native-development` require Java17+ and the
separate `build/dependencies/jdistlib-all-0.10.2.jar`. The consumer JAR is a
development control probe, not a project analysis command. Do not use this
component as a replacement for a complete MTAG pipeline.

## Verification and its limits

Twenty-six predeclared matrix controls include 22 finite cases and four native
refusal cases. Every admitted beta, SE, factor, Z, P and unadjusted GMM entry was
compared with unchanged original Python2 functions. Windows Java21/25 and Linux
Java21 each checked 85,858 numeric cells: **257,574 comparisons** in total.
Maximum scaled error was approximately 2.78e-15. Eight JUnit tests passed on
each JVM, with no skips: 24 passes in total.

Separately, unchanged original MTAG completed a synthetic **20,000-variant,
three-trait** workflow, including its bundled LDSC estimation and maxFDR search.
The native component checked all 60,000 trait rows on each JVM, including
standardized beta/SE rescaling, Z, P and all nine global GMM covariance entries.
This added **720,000 numeric comparisons**; maximum scaled error was about
7.78e-16. This is one synthetic dataset reused across platforms. LDSC fitting
and the maxFDR formula were not independently reimplemented. The eight saved
maxFDR grid rows and logged maxima were checked for consistency; the coarse
grid is an explicit test choice, not a paper reconstruction.

**Original decimal parsing changes values.** Of 180,000 Z/N/frequency input
cells, original pandas0.24.2 parsed 31,497 differently from Java/Python3
round-to-nearest decimal conversion, by at most two binary64 ULPs. The first
strict raw-value audit failed and is retained. A separate call to unchanged
original parsing/merging/QC functions then captured all effective values as
hexadecimal binary64. Every source result preserves those effective values
exactly. The complete difference ledger is retained; no value is repaired.
Consumers must distinguish raw decimal text from the effective source values.

The public evidence and replay helper verify saved synthetic controls. They do
not authenticate an upstream binary, establish biological validity, reproduce
historical paper inputs or complete the 250-paper implementation goal.

```powershell
python tools/literature/verify_mtag_native_evidence.py
python tools/literature/verify_mtag_native_evidence.py --replay windows21 `
  --out build/literature-review/mtag-runtime/public-replay-windows21-1
```

Use `windows25` or `linux21` for the other recorded platforms. Linux replay uses
the existing `JLinAlg-Omics-SAIGE-20261026` WSL distribution. Saved replay needs
Java and the pinned JDistlib dependency, but does not need Python2 or rerun
LDSC, maxFDR or a scientific fit. Choose a new output directory for each attempt.

## Five primary-paper role drafts

Source hashes and paragraph pointers are saved in
`mtag-native-development/paper-use-reviews.json`. They are **not yet applied**
to the current catalog.

| Paper | MTAG use | Boundary |
| --- | --- | --- |
| P077 | MTAG1.0.8 combined valve area, mean gradient, peak velocity and aortic stenosis, with maxFDR. | METAL and PRScs are separate; literal flags and maxFDR grid are unknown. |
| P088 | MTAG1.0.8 combined DCM, Ecc and LVESVi after positive rg>0.7 screening; maxFDR0.03. | The initial correlation screen differs from the final three-trait input set. |
| P090 | MTAG1.0.8 combined primary GWAS with MRI traits selected by absolute correlation >0.7; maxFDR2.7%. | Do not infer the final input set from all ten screening candidates. |
| P112 | Joint EUR cannabis-use-disorder, alcohol-use-disorder and FTND nicotine-dependence analysis after duplicate, MAF and strand QC. | Exact version, commands, per-SNP N and reference settings are unreported. |
| P115 | Six MDD-related input selections and training-fold MTAG for held-out PRS evaluation; MTAG effective N used in LDSC. | Imputation/PRS are separate; effective-N definitions differ; complete input selections require the supplement. |

## Prominent runtime warnings and required recovery

**SKIP3 — skip dependent analysis when the exact permitted runtime is missing or
cannot be installed.** This reference uses Python2.7.15, NumPy1.16.4,
pandas0.24.2, SciPy1.2.1, bitarray0.8.3 and joblib0.14.1 from the archived
community `conda-forge/label/cf202003` channel. Python2 is the original tool's
requirement; it is isolated from shared Python environments. Setup success alone
admits no analysis.

The current-channel attempt could not solve the requested Python/NumPy versions.
The first archived installation failed on an NTFS package symlink. A `/tmp`
installation initially worked but disappeared before validation. These attempts
and the resulting failed validation are preserved. The successful private
reference is on the native Linux filesystem under a fresh `/var/tmp` directory.
Temporary directories can be cleared: preflight the recorded executable before
every dependent run. When unavailable, explicitly recreate the private runtime
where installation is permitted; otherwise leave the analysis skipped.

**FAIL2 — no result is admitted for invalid data, integrity failures, source
process errors, capacity failures or unsupported numerical results.** Preserve
all logs and partial outputs, read the named cause in `COMPLETED.json` and
`WARNINGS.md`, correct that cause and retry new paths. Do not change scientific
thresholds, source data or algorithms to hide a failure. Original MTAG explicitly
disables its beta/SE input mode because of known bugs; do not bypass that guard.

The typed workflow must still cover runtime admission, full input/QC and allele
contracts, supported original covariance/maxFDR choices, prominent diagnostics,
saved-result verification, root dispatch and paper-role application before
MTAG receives catalog integration credit.
