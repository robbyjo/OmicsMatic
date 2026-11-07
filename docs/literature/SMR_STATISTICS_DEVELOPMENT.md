# SMR native statistics: validated component and unfinished workflow

This document records the native-component checkpoint. Consult the [current catalog](omics-2023-2026/README.md) for later workflow availability and counts; this retained checkpoint does not override a later integration.

`org.jlinalg.mr.SmrStatistics` adds Java-only single-instrument SMR arithmetic and a supplied-instrument HEIDI covariance/deviation kernel. It includes both exposure and outcome uncertainty in the ratio variance, accepts an explicitly supplied estimate-error correlation, and retains a finite log tail when the displayed P underflows.

**COMPONENT ONLY — NO SMR/HEIDI EXECUTION ADAPTER IS ADMITTED BY THIS CHECKPOINT.** Both tool integrations remain missing at this checkpoint. This component does not select SNPs, align alleles, calculate reference-panel LD, prune instruments, fit multi-SNP SMR, estimate sample overlap, or return a HEIDI P value. Those original workflows have been executed as development controls; connecting and auditing them through a typed project adapter remains unfinished here. The full 250-paper goal remains active.

## Use the native Java API

Use Java 17+ and a current source build or the separately hash-bound development JAR in [the validation record](smr-statistics-development/VALIDATION.json). The canonical executable has not been replaced.

```java
import org.jlinalg.mr.SmrStatistics;

// Aligned effects and SEs; independent exposure/outcome estimate errors.
var ratio = SmrStatistics.ratio(0.5, 0.02, 0.1, 0.012);
System.out.println(ratio.effect());          // 0.2
System.out.println(ratio.standardError());   // about 0.0252982
System.out.println(ratio.pValue());
System.out.println(ratio.logPValue());

// A correlation must come from explicit caller provenance; this method does not estimate it.
var withOverlap = SmrStatistics.ratio(0.5, 0.02, 0.1, 0.012, 0.3);
```

The independent-error variance is the delta-method Jacobian variance of the ratio, including uncertainty in both effects. It differs from the existing outcome-only Wald SE convention. Supplying a correlation adds the covariance term; the caller must justify its sign and meaning. Neither this choice nor a significant association establishes causality.

`deviationKernel(exposureEffects, exposureSEs, outcomeEffects, outcomeSEs, ld, topIndex)` accepts 2–500 already selected, identically ordered instruments. The top index is explicit. It returns defensive copies of ratio estimates, the original no-overlap covariance convention, deviation covariance/correlation, squared standardized deviations and their sum. It preserves the source finite-strength correction and the literal **1e-8** addition to deviation-covariance diagonals. This fixed convention is not silently rescaled or replaced. A two-instrument kernel can illustrate arithmetic even though it does not satisfy the original workflow's default minimum for HEIDI testing.

**NATIVE INPUT/NUMERICAL ERRORS: NO RESULT.** Supply finite effects, positive finite SEs, nonzero exposure effects, complete aligned vectors and finite symmetric LD with a unit diagonal within the stated floating-point tolerance. Unrepresentable arithmetic and nonpositive deviation variances throw a named `IllegalArgumentException`. No variance clipping, ID repair, allele orientation change, covariance replacement or generic heterogeneity-test substitution occurs. Review the named cause before retrying; do not drop instruments simply to obtain a finite result. A displayed P of zero with finite `logPValue` means numerical underflow, not an exact zero probability.

## Original reference evidence

Two separate official source editions were captured:

| Source label | Official repository commit |
| --- | --- |
| 1.3.1 | `21587e8091b37183e0d7bda418723fafb36c28d0` |
| 1.4.3 | `eedb1a62ba4c66ab0b09acd0205876955a803f18` |

The commits identify captured source, not a historical paper binary or environment. Source 1.3.1 corresponds to the version label reported in P169; that does not establish its exact paper build. P150 reports 1.03, which is a distinct unresolved edition. The original source is separately licensed GPL-2.0 and stays outside the Java JAR. Preserve the original license files and notices. The Java implementation contains independently expressed mathematical arithmetic; the GPL-2-only reference harness is a separate executable, never linked into Java.

Each original program completed sparse and dense BESD preparation, single-SNP SMR with both HEIDI methods, HEIDI-disabled analysis, and multi-SNP SMR on the same declared synthetic population: 512 reference samples, 32 variants and five probes. Weak/insufficient-instrument results and original `NA` fields are retained. The original QTL reader stores beta/SE as binary32; the Java tests use those actual stored values when checking original result rows. Original `.smr`/`.msmr` output precision is compared as printed, without claiming 17-digit precision from rounded tables.

A separate thin harness links the original object files and calls the unchanged `est_cov_bxy`, `bxy_hetero3`, `max_abs_id` and chi-square tail functions. It captures 72 complete original covariance cases, 25,116 covariance entries and 924 original chi-square controls. Only the original CLI entry symbol is renamed so the harness can have its own entry point. The published header's unused float overload is not substituted for the actual original double overload. Harness ratio diagnostic arithmetic is owned reference scaffolding, not a separately callable original SMR estimator. Complete original HEIDI probabilities remain evidence; they are not a native HEIDI tail implementation.

**81 checks pass on each of Windows Java 21, Java 25 and Linux Java 21**, including full original covariance comparisons, independent matrix-projection checks, original chi-square tails, 40 ratio rows from ten complete original output tables, finite-difference covariance checks, units/sign invariance, immutability, underflow and invalid-domain refusals. This is numerical component validation, not biological validation or whole-paper reproduction.

## Dependency warnings and reproducible preparation

**OFFICIAL BINARY DOWNLOADS RETURNED HTTP 403.** The failed attempts from both current and legacy publisher hosts remain retained. They were not silently replaced or relabeled. Permitted official GitHub source downloads and private source compilation succeeded. No real biological analysis was admitted during this preparation.

The development preparation helpers target the validated Windows/WSL environment, with distro `JLinAlg-Omics-SAIGE-20261026`, GCC 13.3, private CMake 3.31.10, Eigen 3.4.0, zlib 1.3.1, fmt 12.1.0 and spdlog 1.15.3. The legacy source uses the existing system zlib; the current source uses the captured zlib. Current builds select the original supported Eigen backend and disable mimalloc. Existing source trees, dependency libraries and system installations remain unchanged; builds use owned copies.

**SKIP3: IF REQUIRED DOWNLOADS/COMPILATION ARE FORBIDDEN OR THE DECLARED ENVIRONMENT IS UNAVAILABLE, SKIP DEPENDENT ORIGINAL ANALYSIS.** Native Java methods require no SMR/CMake/Eigen runtime. The prototype helpers write warnings and preserve failed preparation attempts; inspect full logs and the named missing dependency. They do not create a WSL distro or install a compiler globally. Use NEW output directories.

From the repository root, this explicit preparation sequence captures the selected source and dependencies and builds privately:

```powershell
python tools/literature/intake_smr_sources.py `
  --commit 21587e8091b37183e0d7bda418723fafb36c28d0 `
  --out build/literature-review/smr-runtime/my-source

python tools/literature/setup_smr_cmake.py --allow-install `
  --out build/literature-review/smr-runtime/my-cmake

python tools/literature/prepare_smr_build_dependencies.py --allow-install `
  --out build/literature-review/smr-runtime/my-dependencies

python tools/literature/build_smr_reference.py --allow-build `
  --source-home build/literature-review/smr-runtime/my-source `
  --dependencies-home build/literature-review/smr-runtime/my-dependencies `
  --cmake-home build/literature-review/smr-runtime/my-cmake `
  --out build/literature-review/smr-runtime/my-build
```

**ORIGINAL HELP EXIT CODE 1 IS NOT MODEL SUCCESS.** Both captured programs print their version and option list for `--help` and exit 1. Earlier builders incorrectly expected zero; the failures and read-only recovery receipts remain. The corrected builder verifies this exact metadata behavior. Actual original preparation/model commands must still exit zero and produce complete expected files; no analysis failure is accepted merely because its banner appeared. Legacy compiler I/O warnings and current dependency deprecation warnings remain in full logs; no original function was patched to suppress them.

## Paper roles and remaining work

Twelve verified review drafts remain unapplied: seven SMR and five HEIDI observations, with five supporting pointers. P072 reuses prior multi-SNP results; P107 uses multi-SNP P and a strict reported HEIDI SNP-count filter; P150 and P169 have distinct version/correction/reference settings. P183's literal HEIDI eQTL threshold is **1.57e-8**, which must not be silently changed to the commonly documented **1.57e-3** default. OPERA multi-exposure HEIDI is separate. P189 is supplementary testing after primary protein MR; P203's input filtering, reported empirical-P wording and test-P versus LD-bound wording remain explicit.

The next required work is the full typed SMR/HEIDI adapter with captured inputs, exact source/runtime checks, native input/output audits, owned-process cleanup, atomic admission and tested SKIP3/FAIL2 actions, followed by catalog integration. At this checkpoint the catalog has 78 new scoped integrations and 253 missing tools. Run `python tools/literature/smr_statistics_evidence.py` to verify the committed component evidence without R, CMake or SMR.
