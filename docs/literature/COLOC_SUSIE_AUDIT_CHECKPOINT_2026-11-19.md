# COLOC SuSiE source audit and native fixes, 19 November 2026

**The existing native stable method now has source-bound qualification,
corrected boundaries and prominent semantic warnings. The full 250-paper
implementation/review goal and cumulative COLOC catalog admission are open.**

The [ABF workflow checkpoint](COLOC_ABF_WORKFLOW_CHECKPOINT_2026-11-19.md) was
pushed to GitHub main as `fdb9739f8d609ed02c88019eadc9e82a15a4fe9a` after
the goal resumed. Its 21 public files were verified; all 21 worktrees and 122
protected artifacts were preserved. Do not repeat its implementation or
refresh old source/hash guards.

## Result and evidence

The original coloc **6.0.3** package was actually executed for 26 supplied-BF
profiles and two original `runsusie`/`coloc.susie` fit pairs. Selected seven
installed function bodies **and formals** matched exact source commit
`27776b1bb14e19d1542798ac7477fa378fc2fc0d`. R 4.6.1, susieR 0.14.2 and
data.table 1.18.6.1 are recorded in the captured session. Namespace/DLL
authentication or complete dependency-source attestation is not claimed.

The audit preserves **12 fully matching profiles**, **four matching weighted
regional profiles with different conditional SNP posteriors**, one stable-H3
case, one invalid-simplex single-variant source case, four reserved-null
refusals, five other profiles without an admissible original posterior, and
one zero-weight refusal by both implementations. These distinct outcomes
must not be reported as 28 cases of literal source parity.

The raw original single-variant `coloc.bf_bf` hypotheses sum to
**2.0001003162171**, although all fields are finite. They remain retained and
unadmitted; no renormalization or repaired source success is claimed. Earlier
capture status `FINITE` means finite fields only. Native single-variant
statistics remain normalized and are explicitly labeled a method difference.

The first unchanged-native comparison had ten complete matches and exposed
the overlap-one defect and shared-prior restriction. The frozen original and
old-native outputs remain available. New qualification separates intended
stable-method differences from the corrected defects.

Changes:

- Complete shared populations retain all pairs at overlap exactly one.
- Scalar p12 may exceed p1/p2, preserving defaults and the (0,1) prior bounds.
- Exact variant IDs are retained by summary, labeled-LD and colocalization
  transport. Reserved R null columns, null IDs and malformed Unicode are
  explicitly refused rather than altered.
- Result warnings and `isSharedVariantPosteriorDefined` expose method scope
  and undefined legacy markers. The CLI displays/logs warnings and refuses a
  complete result when its conditional SNP posterior is undefined.

**50 tests per JVM profile**, Windows Java 21/25 and Linux Java 21, passed
with zero skips/failures: 150 executions. They cover all 28 original profiles,
new boundaries/identity/warning/refusal controls, ten earlier API scientific
tests and four earlier CLI tests. Actual packaged success and undefined-result
refusal also passed on all three. Both earlier ABF packet readbacks and an old
ABF bundle's native scientific verifier passed unchanged.

The [compact proof/readback](coloc-susie-audit-checks/README.md),
[API/CLI guide](../vignettes/coloc-susie-native.md) and
[example](../../examples/coloc-susie-native/README.md) give exact scope and
recovery. The candidate was compiled using Java 21 `--release 17`; ten existing
payloads were replaced and all 23,128 other parent payloads preserved. This is
not an all-project Gradle/full-suite success or a release.

## Next work

1. Finish the COLOC catalog coverage matrix for all
   [27 curated paper roles](coloc-abf-handover/source-roles.json). Distinguish
   the qualified source-compatible ABF workflow from this native stable
   supplied-BF method. Keep historical versions, FUSION hosts, unavailable
   supplementary settings and original multi-causal fitting gaps explicit.
2. Complete tested cumulative admission, compatibility/predecessor evidence,
   1,413-old-review preservation, metadata/report/manifest writing and fresh
   source audit. Do not increase family counts before admission passes.
3. Continue remaining source-mode/null/per-SNP-prior and upstream-fit scopes
   where paper use requires them, plus higher-impact remaining families and
   individual reviews. Enrichr/Scanpy (22 papers), GSEA/SuSiE (19), GCTA (17)
   and edgeR (16) remain priority candidates after checking existing methods.

Canonical totals remain **250 papers, 2,700 observations, 134 new scoped
integrations (24 native +110 upstream), 1,413 reviewed uses and 1,287 pending**.
This checkpoint awards zero new family credit. The frozen initial paper window
and 446 named families/485 effective definitions are unchanged. Preserve
source ambiguity and all original review objects; do not invent author settings
or whole-paper/full-tool completion.

## Recovery and transfer

**No undefined conditional SNP result is admitted by the CLI.** Preserve
inputs/logs, inspect shared BF support and retry fresh outputs only after
correcting the cause. API zero vectors for unsupported H4 are explicitly
undefined markers; check the flag and warnings before reporting probabilities.
Never silently alter IDs, alleles, P values, NaN/Inf, populations or algorithms.

**SKIP3:** exact prerequisite missing/unreadable or installation forbidden;
skip the affected original audit/analysis, name what to restore and continue
independent work. **FAIL2:** invalid/nonfinite data, changed evidence, schema,
capacity or other failure; no complete admitted result. Retain original inputs,
partials and diagnostics; correct the cause and retry NEW output paths. Do not
refresh historical hashes to conceal changes. Historical marker-verifier
SKIP3 gaps remain documented in earlier Seurat checkpoints.

Active checkout: `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`; preserve all 21 worktrees and the original
dirty root. Private COLOC root is its `build/coloc-high-impact-1/`.
Add `susie-source-audit-1`, `susie-source-compare-1`,
`susie-source-fixture-1`, `susie-fixes-build-1`, `susie-fixes-tests-1`,
`susie-fixes-qualification-1` and new packet/readback attempts to the intact
transfer set. Preserve older attempts, original full texts and both dependent
R libraries described in the earlier handover. Github is not a machine backup.

Private current JAR: `susie-fixes-build-1/omicsmatic-0.4.0.jar`, 194,518,876
bytes, SHA-256
`d53c6a21a4ca46839725af60c341582a665aed2bfed168555f080c00111b0896`.
The public 0.4.0 release predates this work. Older frozen bundles require their
matching selected producer classes; a different compiler/debug build may
change class hashes. Use the named preserved candidate for their readback or
create a new analysis with the new build; do not rehash an old bundle.

Publication receipts continue under
`D:/projects/JLinAlg/build/git-publish/20261119-coloc-susie-audit-38`.
P37's 122 protected artifacts and 12 canonical pins must remain unchanged.
Both literature HTML reports stay withheld from Pages; no release is created.
Prior download/install/research-use permissions persist and Cell Ranger
remains excluded.
