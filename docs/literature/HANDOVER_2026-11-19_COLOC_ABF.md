# Handover: COLOC ABF and the remaining toolbox work

**Resume here. The 250-paper implementation/review goal is unfinished.**
All current task source, tests, compact evidence and this handover are intended
for GitHub main; private runtimes, original full texts and large builds remain
local. No release is created. Both literature HTML reports remain withheld
from Pages; preserve their exclusions in `.github/workflows/pages.yml`.

## Confirmed catalog baseline

The completed [PLINK catalog checkpoint](PLINK_CATALOG_CHECKPOINT_2026-11-19.md)
is on main at `2d820447e7e4b4aab414f3185916e83d5ea76a5c`.
The frozen cohort has **250 papers** (71 Nature, 104 Nature Genetics, 75 AJHG),
published within 2023-10-23..2026-10-23, **2,700 observations, 1,413 reviewed
positive uses and 1,287 pending**. There are **134 new scoped integrations
(24 native Java +110 upstream)**, or 137 including three existing adapters;
446 named families, 485 effective definitions, 259 missing and 41 method-overlap
definitions. These are bounded scopes, not whole-tool implementations.

The new COLOC component and source census award **zero additional family
credit**. Do not increase totals until cumulative catalog admission passes.
The current date does not silently move the frozen paper window.

## Work saved in this handoff

- Four new `org.jlinalg.coloc.ColocAbf*` classes accept regional regression or
  P-value data without R analysis. Existing SuSiE classes/CLI are unchanged.
- [API guide](../vignettes/coloc-native-abf.md), runnable
  [Java example](../../examples/coloc-abf/README.md), reference probe and 40
  reference/boundary tests, plus a 265-member synthetic original-reference ZIP.
- Actual pinned coloc 6.0.3 execution produced 30 original cases. The final
  candidate passed all 40 tests on Windows Java 21/25 and Linux Java 21, with
  no skipped/failed tests. Eight cases correctly refuse a native result.
- An earlier candidate comparison matched 72,738 numeric fields from 22
  successful cases, maximum absolute difference 3.552713678800501e-15.
  Final candidate build 2 separately passed every reference through JUnit.
- A manually curated [27-role source census](coloc-abf-handover/source-roles.json)
  distinguishes ABF, SuSiE, unspecified methods and FUSION-hosted analyses.
  All 250 XML identities and 51 named body paragraphs were checked. One old
  review is retained; 26 possible appended reviews are still unadmitted.
- [Compact evidence and offline readback](coloc-abf-handover/README.md) bind
  current source bytes to saved test reports and immutable upstream identity.

Final private candidate: `build/coloc-high-impact-1/native-build-2/omicsmatic-0.4.0.jar`
in the active checkout; 194,490,598 bytes, SHA-256
`914daff49577f53c32e93401daa3bb47ef373104acf43f14ee610840e10f26bf`.
Its 23,122 parent JAR payloads were preserved and eight ABF class payloads added.
It is a development candidate, not a newly published release.

## Ordered remaining work

1. Verify this compact handover and inspect current Git/main state. Preserve
   every existing worktree and immutable predecessor packet. Do not rerun or
   re-admit completed METAL, Seurat or PLINK milestones.
2. Finish a practical native ABF workflow for ordinary summary statistics:
   typed file inputs/options, visible warning/error reporting, output provenance
   and meaningful verification/replay/corruption/missing-prerequisite controls.
   The existing `coloc` CLI takes **SuSiE log-Bayes-factor rows**; it is not an
   ordinary beta/variance or P-value CLI. Keep the two method contracts distinct.
3. Review existing SuSiE code/qualification against actual paper use before
   awarding broader COLOC scope. Historical editions, author LD/fits, FUSION
   host behavior and unreviewed supplementary settings remain separate gaps.
4. Complete cumulative COLOC catalog admission: exact predecessor guards,
   source-role crosswalk, preservation of 1,413 old reviews, explicit coverage
   gaps, qualification/compatibility deltas, full metadata/report/manifest
   update, tested plan/apply/verify and fresh post-admission source audit.
   Keep all original hashes and failures; do not manufacture a full-tool claim.
5. Continue higher-impact gaps after checking implemented methods: Enrichr and
   Scanpy (22 papers each), GSEA/SuSiE (19), GCTA (17), edgeR (16). Native Java
   for tractable methods; tested upstream adapters where appropriate. Continue
   the 1,287 individual pending reviews alongside implementation.
6. Push qualified checkpoints to GitHub main without force. For `site/**`
   changes, follow hosted Pages deployment and live-page verification while
   retaining both literature-report exclusions. Do not create a release unless
   separately requested. The whole objective is not complete at this handoff.

## Warnings, errors and required recovery

**SKIP3: exact prerequisite missing/unavailable or installation forbidden.**
Print the missing file/runtime/version and required action prominently, skip
the dependent audit/analysis, retain logs and continue independent work. Restore
the original named bytes or permitted runtime and retry in a NEW directory.
Two historical marker-verifier mains still require their exact old canonical
JAR; see the Seurat checkpoint. Do not label these historical checks as passed.

**FAIL2: changed evidence, invalid scientific input, nonfinite posterior,
integrity/capacity error.** Return no completed scientific result. Retain
original inputs, partial outputs, warnings and error. Correct the cause before
retrying; never silently change IDs, alleles, P values, NaN/Inf, source versions,
populations or algorithms. The library currently throws on invalid/nonfinite
calculation; the future workflow must translate this into clear user-facing
failure evidence. Returned warnings must be displayed and retained.

Source-specific COLOC behavior is documented in the API guide: unweighted
conditional SNP posterior despite weighted regional priors, original-size
prior adjustment/scale estimation, position merge keys, H3 arithmetic limits,
ignored top-level upstream effect-prior override and strict native P<=1.
Do not silently replace these with more convenient semantics.

Retained development failures: isolated R install 1 lacked data.table;
reference capture 1 failed serialization; reference capture 2 used an ASCII
locale that rewrote Unicode IDs and failed native comparison; JVM setup 1 used
an absent Java 25 path; source audit 1 had an overstrict version regex.
Successful retries live in new directories. No old record was rewritten.
Reference 4 uses explicit UTF-8 character handling with C collation and
17-significant-digit TSV plus raw RDS output.

## Resume and machine transfer

Active checkout: `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. Original root `D:/projects/JLinAlg` is a
different, dirty worktree; do not reset, clean or broadly stage it. All 21
worktrees and their old overlays must be preserved. No subagents are required.

Private COLOC root (relative to active checkout): `build/coloc-high-impact-1/`.
Transfer it intact, including `upstream-coloc603-intake-1`,
`runtime-coloc603-1` **and** `runtime-coloc603-2` (the second library depends
on the first), `original-reference-1..4`, `native-reference-1..3`,
`native-build-1..2`, `junit-qualification-1..2`, `curated-source-audit-1..2`
and the handover packet preparation/readback attempts. Failed attempts are
scientific history, not disposable build cache.

Also transfer `D:/projects/JLinAlg/build/literature-review/xml`, all earlier
immutable scientific evidence/runtimes referenced by predecessor checkpoints,
and `D:/projects/JLinAlg/build/git-publish/`. Publication receipt P35 is in
`20261119-plink-catalog-35`; this handoff uses
`20261119-coloc-handover-36`. Preserve P35's 104 protected artifacts and
12 canonical pins unchanged. Helper scripts for original intake/install,
capture, comparison and test execution are in `D:/projects/JLinAlg/build/`
with `coloc` in their names. GitHub contains compact reproducible fixtures
and instructions, **not a complete machine backup**.

Tested runtimes: Adoptium Java 21.0.4.7 at
`C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin`, Java 25 at
`C:/Program Files/Java/jdk-25/bin`, R 4.6.1 at
`C:/Program Files/R/R-4.6.1/bin`, Linux Java 21 in WSL distro
`JLinAlg-Omics-SAIGE-20261026`. Native analysis needs Java, not R. R plus both
private libraries are needed only to repeat the original-package capture.

User decisions persist: prioritize high impact, native Java when practical,
downloads/installation permitted by the operating environment, research/nonprofit
FIMO and accepted academic-noncommercial GATK 3.8 terms. **Cell Ranger remains
excluded.** License/access gaps for other versions require explicit treatment;
never infer permission for legacy unlicensed code from a current release.
