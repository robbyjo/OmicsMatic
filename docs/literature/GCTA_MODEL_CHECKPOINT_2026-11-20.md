# GCTA model-export qualification checkpoint

The implementation goal is active again. This checkpoint closes the open
average-information covariance follow-up, compares the available model exports
with separate original executions, and repairs a REML summary-verification gap.
**GCTA/COJO catalog admission, global command registration, mtCOJO and the
paper-specific older editions remain unfinished.** No family credit, release,
biological replication or whole-tool completion is claimed.

## What changed

`GctaWorkflowAudit` now requires the complete observed-scale univariate REML
summary schema for the selected single/multi-GRM profile. It checks each
variance ratio, the summed genetic ratio, total-variance SE from the full
component covariance, and single-GRM likelihood-test fields. Likelihood-ratio
checks respect the original full/null log-likelihood rounding and original
zero floor. The original one-sided P must be in 0..0.5; it is not replaced by
another P calculation. These checks validate internal exported relationships;
they do not refit original models or authenticate coordinated replacement.

The ratio checks use cross multiplication and decimal rounding intervals.
They retain valid negative or greater-than-one unconstrained estimates and
do not divide by printed-zero Vp. The frozen five-original corpus includes
positive, unconstrained, two-GRM, tiny-unit and large-unit fits. Nonzero internal
variance/SE can print as zero in the original six-decimal tables. Such exports
remain unchanged and prominently labeled; no hidden precision is invented.

The previous normal producer accepted all nine deliberately invalid summary
controls after their matching metadata and seals were updated. The corrected
producer refuses all nine with their specific schema, ratio, uncertainty or
likelihood-test cause. Original and historical failed attempts are retained.
The corpus is
`src/test/resources/org/jlinalg/cli/gcta1941-reml-captures.zip`; it contains only
synthetic inputs and original exports, not participant data or a GCTA runtime.

## Current evidence

| Check | Observed result |
|---|---|
| Normal build, Java 17 target | 18 tests passed; zero failures/errors/skips |
| Packaged JAR first on classpath | 18 tests passed per Windows21, Windows25, Linux21 |
| Existing complete captures | All 56 verify unchanged on each of those three JVMs |
| Producer payloads | 23,540 unchanged; only `GctaWorkflowAudit.class` changed |
| Source-export comparisons | 104 passing file/matrix/decoded-array comparisons across Windows/Linux |
| Independent remaining original runs | Six actual runs: tiny/large-unit REML and adjusted regional LD on both hosts |
| Average-information covariance | Six independent point checks consistent with printed-parameter perturbation bounds |
| Inverse/dependency/process controls | 21 per JVM: 16 mutated captures, one actual exhausted fit and four actual dependency/process cases |
| Timeout/output workers | All six owned workers observed terminal by independent process probes |

The 104 comparisons cover all available COJO joint/conditional/LD/given tables,
full PCA exports, REML HSQ/all BLP columns/full logged covariance, ordinary and
regional LD scores, sparse matrices, both GRM algorithms, the complete binary
fastGWA null model and every decoded array entry. Loaded and direct association
results match within each original runtime. Cross-platform calibration is not
forced to agree. Comparisons are scoped to the declared same-input synthetic
controls; they do not validate an author's data preparation or reproduce a paper.

The independent covariance calculation follows the
[frozen upstream REML source](https://raw.githubusercontent.com/jianyangqt/gcta/6da64e9d8be838d01c34efb974e69c72e861f4c2/main/est_hsq.cpp).
GCTA retains inverse average information from the previous iteration before
the final variance update. At the previous printed point, covariance spectral
differences are about 1.86e-7, 1.31e-6 and 6.24e-7 for positive, unconstrained
and multi-GRM fits. The corresponding bounds from previous-point decimal
uncertainty are 3.14e-4, 2.98e-4 and 6.08e-4. The earlier expected-Fisher
differences are outside these bounds and remain retained. This is a numerical
operator-norm perturbation calculation with floating-point padding, not a formal
interval-arithmetic proof or recovery of unexported full-precision state.

The actual `--reml-maxit 1` source control exits zero on both runtimes. Windows
writes finite but nonconverged exports; Linux writes a nonfinite h2 export on
this fixture. Both are **FAIL2: NO COMPLETE RESULT**. All available source
objects, logs and partials are retained. A process exit code or finite-looking
table alone does not establish a completed scientific result.

Independent Linux compilation remains **SKIP3 because Linux javac is absent**.
Windows JDK21 compiled to the project's Java17 target; actual execution was
checked on Windows Java21/25 and Linux Java21. Do not describe those runtime
checks as independent Linux compilation or actual Java17 execution.

## Warnings and recovery

- **SKIP3: dependent analysis skipped.** Supply the exact named permitted
  runtime/input in a new request or retain the skip if installation is forbidden.
  Never substitute editions, engines, hashes, populations, alleles or algorithms.
- **FAIL2: NO COMPLETE RESULT.** Read `WARNINGS.md`, `analysis-status.json`,
  `manifest.json`, available model objects, and all worker logs. Preserve raw
  inputs, null/partial/model exports and failed attempts. Correct the stated
  source/data/numeric/convergence/integrity/capacity/process cause and retry
  into a new directory. Do not interpret incomplete rows as a complete result.
- Prepared inputs must declare a complete literal FID/IID cohort. No automatic
  filtering, recoding, imputation or identifier/strand repair occurs. Binary
  fastGWA requires explicit 0/1 coding. mtCOJO is unimplemented, not a fake skip.
- Valid unconstrained variance/h2, COJO fractional effective n and adjusted
  negative LD values remain unchanged. Original-model VERIFY checks integrity,
  deterministic intake and declared export invariants; it does not independently
  refit models or prove authenticity. Resource limits are logical/monitored
  limits, not OS memory/file quotas.
- Historical reader-source pins remain frozen. Run the earlier workflow
  checkpoint reader in its exact owner `044b282c` / `build/gc-3`; do not refresh
  that reader's source hashes to accept this changed audit. This checkpoint
  separately demonstrates the unchanged captures verify with the new producer.
- **GitHub is not the complete workstation backup.** Transfer private runtimes,
  exact producer JARs, primary full text, complete evidence/recovery stores and
  all 26 worktrees separately with checksums. Preserve older owner checkouts.
  Both literature HTML reports remain withheld from Pages. No release is made.

## Exact continuation

1. Implement and qualify mtCOJO's own LD-score, conditioning and prevalence
   contract with an actual successful original fit. Resolve the reported older
   editions (1.92.4, 1.93.2, 1.93.0 and P140's 1.91.7beta/fastGWA statement),
   P171's wheat ploidy preparation and P248's literal formula/threshold ambiguity.
   The [source census](gcta-development/SOURCE-NOTES.md) preserves all 22 contexts.
2. Register global discovery with exact dispatcher compatibility evidence, then
   perform guarded source-role/catalog admission. Preserve all 1,548 existing
   reviews. P112's mtCOJO role and proposed P127 comparison-only removal need
   explicit before/after evidence; do not count GCTA/COJO aliases as separate
   packages. Continue actual replay/alias controls where a changed contract
   warrants them; do not silently refresh historical owners.
3. Continue the high-impact backlog, including limma, PEER and TwoSampleMR,
   and the 1,166 pending individual observations. Catalog totals remain
   **140 scoped integrations (26 native +114 upstream), 1,548 reviewed uses,
   1,166 pending, 826 manifest entries**. The 250-paper cohort remains frozen
   to 2023-10-23..2026-10-23. Full goal completion remains open.

Current checkout: `D:/projects/JLinAlg/build/gc-4`, branch
`codex/gcta-model-qualification-20261120`, baseline
`044b282c472124d9cf41a26dfcd7354d3f82a8fd`.
Private evidence: `D:/projects/JLinAlg/build/gc-4/build/gcta-qualification-1`.
`normal-gradle-2` is the current build/source owner; preserve the first failed
build and all baseline/harness attempts. Exact normal producer:
`normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`, 195,987,883 bytes,
SHA-256 `3346264a63e0333e104db90e47a3b8f9a988122469023feca8ec86eb2fe9596c`.

The [saved evidence README](gcta-model-development/README.md) explains the
lossless public archive and reader. The [previous handover](HANDOVER_2026-11-20_GCTA_WORKFLOWS.md)
retains runtime, full-text, exact missing historical-producer and private-transfer
paths. Its paused status describes that historical checkpoint; the user has
resumed this goal. No original input, old proof or failed run was retired here.
