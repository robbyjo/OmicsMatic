# Handover: smartpca FST, current catalog and remaining full-goal work

**The full 250-paper goal remains unfinished.** Native source-specific PCA and
FST RUN/VERIFY/REPLAY now work through the global `smartpca-tools` command.
Qualification is complete for the documented profiles; **guarded six-role catalog
admission has not started, and no new family or review credit is claimed.**

Current admitted totals: **153 scoped families (28 native Java +125 upstream),
156 including three pre-existing adapters; 1,691 reviewed and 1,031 pending uses**.
The fixed cohort contains 250 papers (Nature 71, Nature Genetics 104, AJHG 75),
2,722 positive observations, 446 named labels and 485 provider definitions.
There are 258 missing labels and 20 method-overlap labels. Keep the original
2023-10-23 through 2026-10-23 search window; no extension was needed.

## Start here

Use the latest GitHub main in an isolated checkout, or continue the owned checkout
`D:/projects/JLinAlg/build/fu-1` on `codex/smartpca-fst-20261123` after checking its
status. The shared project root is an older dirty branch; do not bulk-stage or
reset it. This handover supersedes older pending-FST/dispatch instructions in
the lower historical handovers, while preserving their evidence and hashes.

Read [the copyable command guide](SMARTPCA_TOOLS.md),
[qualification status](smartpca-fst-qualification/STATUS.json),
[evidence explanation](smartpca-fst-qualification/README.md), and
[workspace inventory](smartpca-fst-qualification/WORKSPACE-HANDOVER.json).

```powershell
python -B tools/literature/verify_smartpca_fst_checkpoint.py
python -B tools/literature/verify_smartpca_workflow_checkpoint.py
java -jar PATH/TO/BUILT/omicsmatic-0.4.0.jar smartpca-tools --help
```

Use Python 3.11+ without `-O`. The saved readers verify evidence rather than
rerunning original programs. For scientific VERIFY/REPLAY, keep the exact JAR
recorded by each capture. A release containing this change has not been published.

## What is implemented and verified

The existing qualified PCA/outlier/perturbation-shrinkage profile is unchanged.
The new `eig-7.2.1-smartpca-16000-native-fst-v1` profile implements source-specific
pairwise FST, ordinary/inbreeding corrections, chromosome/genetic-position block
construction, global retained-marker weights and weighted-jackknife estimates/SE.
It streams all marker/pair contributions and supports sealed RUN, complete
recomputation VERIFY, and an actual new child calculation during REPLAY.
Only three dispatcher lines were added; old science was not silently replaced.

Final normal build: **1,906 tests, no failures/errors/skips**. Final JAR:
`D:/projects/JLinAlg/build/fu-1/build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar`
(198,869,501 bytes), SHA-256
`534da0fe4f10959a622c4d5f74fee35dc959c6d48e2730f9861dc83790973522`.
It preserves 22,972 previous payloads and changes only the old dispatcher class.
All 362 packaged protocol commands passed: Windows21 104, Windows25 129,
Linux21 129. Fourteen focused methods and six example commands passed per JVM.
The original-source comparison/refusal counts and their reuse are documented in
the packet README. Earlier attempts, including the original one-block inbreeding
abort and its incorrect checker expectation, remain retained.

## Exact next work

1. Finish guarded **smartpca six-role catalog admission** for P019, P041, P045,
   P071, P180 and P229. The canonical catalog is still byte-identical to base
   `eb167472e9e71cc51483e338ee3b8d32ca7bdcd8` (nine-file historical snapshot in
   the qualification archive). Use `tools/literature/mrpresso_catalog/` as the
   previous model/controller provider chain. Create a new package and reader;
   do not rewrite frozen predecessor helpers or hashes. Source roles are in
   `docs/literature/smartpca-native-development/ROLES.json`; fresh matching audits
   are retained under `build/smartpca-4/source-roles-1.json` and its Linux peer.
   The drafts grant zero reviews. Copy each original observation exactly, verify
   its identity, preserve the existing 1,691-entry review prefix and all 2,716
   unrelated positive observations, and explicitly mark applied roles only in
   the new admission records. Source-review status and whole-paper status differ.

2. Preserve reported contexts and versions: P019 MVP EIGENSOFT v6 differs from
   ROSMAP/1000G projection with unreported edition; P041 reports 7.2.1; P045
   reports LSQ/shrinkage and `r2thresh=.7` without establishing `killr2`; P071
   has separate present-day Eurasian/East Asian panels; P180 PCA reports 7.2.0
   but the separate `fstonly:YES` paragraph does not restate that edition or
   its PCA SNP threshold; P229 program 13050 is not a package version and is
   not the tested program 16000. Native available version 7.2.1 is separate
   from an author's version. This implementation does not establish historical
   v6/7.2.0/13050 equivalence or reproduce author environments.

3. Test model invariants, source identity, rollback/concurrent-write refusal,
   all 250 primary records, live catalog reconciliation and UI version contexts
   before applying. Use the new FST reader plus the frozen PCA reader and
   `smartpca_dispatch_compatibility.py`. The frozen MR-PRESSO catalog reader pins
   the old dispatcher and is expected to reject the new bytes; the explicit
   retained predecessor bridge proves its prior successful original context.
   Never refresh its hashes to bypass that distinction. The expected admitted
   transition is one family and six reviews: **154 scoped (29 native+125
   upstream), 157 including existing adapters; 1,697 reviewed/1,025 pending;
   method-overlap 19, missing 258**. These are predictions, not current credit.

4. Re-rank remaining high-impact gaps using actual source roles and existing
   coverage, then implement practical native methods or tested upstream adapters.
   Earlier candidates include nf-core (six preliminary roles), SCENIC/SCENIC+
   and newest iDEP; reassess aliases, wrappers and resource-only mentions first.
   Finish all 1,031 currently pending semantic reviews and missed-provider
   screening across body/tables/supplements/code for the entire 250-paper cohort.

5. Resolve the 33 existing missing website citation panels, run full website
   validation, and publish the unfinished literature reports only when the next
   build is ready, as previously requested. The first historical failure is
   `broad-gsea-tools.md`. Do not insert empty panels or weaken checks. Neither
   Pages workflow/exclusions nor reports are changed by this handover. No reliable
   ETA for completing every implementation and paper-use review is established.

## Warnings and required actions

**SKIP3 means analysis skipped.** If an upstream prerequisite cannot be installed
under environmental/license permissions, identify the affected analysis and exact
missing executable/package/version, install it in an allowed private prefix or
provide its licensed local path, then rerun. Cell Ranger remains excluded by the
user. Research/nonprofit MEME/FIMO and academic noncommercial GATK 3.8 permission
persist; legacy iDEP.96 terms are unresolved, so assess newest iDEP instead.
Native smartpca needs a suitable Java runtime, not an EIGENSOFT installation.

**FAIL2 means no complete result is admitted.** Retain raw/partial evidence and
read `analysis-status.json`, `WARNINGS.md` and process receipts. Fix the named input,
resource cap, timeout, numerical or integrity issue, then create a new output
directory. Restore the exact source/producer for a hash mismatch. Never reseal
altered scientific output or refresh historical hashes to turn failure into pass.
Unknown IDs, literal NA/NaN/Inf and negative FST remain unchanged. Undefined or
single-block ordinary diagnostics can be explicitly retained with false field
eligibility; original inbreeding with at most one block is refused even then.
The guide explains global block weights, source position-unit inference (including
excluded chromosomes), denominators and unclamped negative estimates.

**A GitHub clone is insufficient for machine handover.** Before retiring this
machine, privately transfer the full original primary-text cache, all unique
inputs/outputs/failed evidence, exact old/current producer JARs and private
runtimes. Preserve `D:/projects/JLinAlg/build/fu-1/build/smartpca-{1,2,3,4}/`,
the root helper scripts, `build/git-publish/`, all other historical evidence/runtime
roots named in [the broader handover](HANDOVER_2026-11-23.md), all 19 registered
worktrees with indexes/dirty overlays/refs, and the withheld private bundle.
Six explicitly listed redundant compiler-copy directories may be removed only
after verified public byte-exact recovery; see the cleanup plan/receipt.

Transfer the complete WSL distro/base OS `JLinAlg-Omics-SAIGE-20261026` rather
than copying only virtual environments. Required smartpca private roots include
`/var/tmp/omicsmatic-smartpca-reference-20261123-197` (GSL/OpenBLAS dependency
prefix), `...-198` (successful original source build),
`/var/tmp/omicsmatic-smartpca-outlier-reference-20261123-215`, and
`/var/tmp/omicsmatic-smartpca-fst-reference-20261123-241` and `...-242`.
Keep the dependency prefix even though its initial compilation failed: the
successful original executable links to it. Preserve any additional protocol and
JAR copies under `/var/tmp/omicsmatic-smartpca-*`. Existing R/SAIGE/Scanpy/Squidpy
installations remain untouched. Builds used Java21/25, Linux Java21, Gradle9.7.1
and pinned JDistlib0.10.2; Java source targets17 with strict lint.

**Restricted historical export remains unresolved.** Earlier automatic approval
review rejected nine `codex/handover-20261122-ownerNN` branches (01,04,29,30,31,
41,43,45,59) and `codex/release-v0.3.1` because their broad unpublished history
may contain private/local material. They and the private bundle must remain
local; do not merge/push them as a way to satisfy a general push request. A safe
selected-source publication does not grant clearance to those histories.
Other owners' dirty work is preserved, not incorporated into this validated
snapshot. Never stop unrelated processes or delete active/unique research data.
