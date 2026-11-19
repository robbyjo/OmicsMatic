# Toolbox handover, 19 November 2026

**Resume from GitHub main, with COLOC catalog checkpoint
`29486e3f7bf83f534324f21c2d2bd917ad35b134` included. The overall implementation
and individual source-review goal remains unfinished.**

The 250-paper cohort is collected: 71 Nature, 104 Nature Genetics and 75 AJHG
papers in the frozen 2023-10-23 to 2026-10-23 window. Do not silently move that
window. The current catalog has **135 new scoped integrations: 25 native Java
and 110 upstream**, or 138 including three pre-existing adapters. There are
2,700 paper/tool observations, **1,439 reviewed uses and 1,261 pending**,
446 named families and 485 effective definitions. Of those definitions,
259 remain missing and 40 have related methods whose source equivalence is
not established. Integration counts do not mean full package or paper reproduction.

## What was finished

The [COLOC catalog checkpoint](COLOC_CATALOG_CHECKPOINT_2026-11-19.md) is
admitted and pushed. Its [27-role crosswalk](COLOC_CATALOG_INTEGRATION.md),
[admission proof](omics-2023-2026/coloc-catalog-validation.json),
[checks](coloc-catalog-checks/README.md),
[ABF workflow guide](../vignettes/coloc-abf-workflow.md) and
[native BF guide](../vignettes/coloc-susie-native.md) describe exact scope.
All 1,413 earlier review objects were retained, including P190. The 809-entry
manifest passed with no mismatches, missing entries or extra entries.

The source-compatible ABF RUN/VERIFY/REPLAY workflow and the separately labelled
stable native BF method are already implemented. Do not restart them. The BF
method has documented differences from coloc 6.0.3; whole-package parity,
original multi-causal fitting, null/per-SNP-prior modes, historical editions,
FUSION-host execution and exact author settings are not established. Sixteen
of the 27 source roles do not specify an exact entry point.

Recent focused validation includes 62 ABF reference/workflow tests on each of
Windows Java 21/25 and Linux Java 21, 50 BF source/boundary/regression tests on
each of those JVMs, packaged commands and cross-host verification/replay.
Catalog admission separately passed 17 scope/preservation tests on each of
Windows and Linux, actual missing/changed dependency controls, fresh source
and predecessor audits and the cumulative manifest. These are component and
admission checks, not a fresh full repository suite or biological validation.

## What to do next, in order

1. Read this handover and the COLOC checkpoint. Verify the compact saved
   packets before rerunning scientific work. Use NEW output directories;
   preserve previous successful and failed attempts.
2. Check the current inventory, existing code and primary paper methods before
   choosing a missing scope. Prioritize broad impact and reuse. The table
   below records current candidate families; all six currently remain
   `method-overlap`, not source-qualified package integrations.
3. Start with a bounded Enrichr source-role audit and compare its reported
   operations with existing enrichment/GSEApy ORA support. It ties Scanpy in
   observed paper count and may reuse more existing native work. Determine
   exact library/background/score semantics from primary sources before
   deciding what Java can implement and what needs an upstream adapter.
4. Continue Scanpy and the other high-impact gaps using the same sequence:
   source census, existing-code comparison, bounded implementation, actual
   upstream reference checks, useful packaged workflows and error controls,
   then guarded cumulative admission. A family name is not sufficient
   evidence that the paper used a particular entry point or default.
5. Review the remaining 1,261 observations individually alongside development.
   Keep background mentions, adapted methods, unspecified settings and
   execution evidence distinct. Preserve all old reviews and paper identities.
6. For the next cumulative admission, use actual stage views through the
   published COLOC admission, its before ZIP, exact review inverse and
   809-entry manifest. Preserve frozen verifier/source bytes. Do not run old
   owners directly on modern metadata or refresh old hashes to pass a check.
7. Validate and push each concrete milestone to main without force. Preserve
   the two literature-report Pages exclusions. If `site/**` changes, follow
   the hosted deployment and check the home page and both report exclusions.

| Candidate | Catalog papers | Existing starting point |
| --- | ---: | --- |
| Enrichr | 22 | `src/main/java/org/jlinalg/enrichment/EnrichmentAnalysis.java`; existing GSEApy ORA support |
| Scanpy | 22 | `src/main/java/org/jlinalg/cli/SingleCellCli.java`; qualified Seurat/native preprocessing components |
| SuSiE | 19 | `src/main/java/org/jlinalg/susie/Susie.java`; retained original susieR/COLOC reference runtime |
| GSEA | 19 | `src/main/java/org/jlinalg/enrichment/RankedEnrichment.java` |
| GCTA | 17 | `src/main/java/org/jlinalg/genetics/ConditionalAssociation.java` |
| edgeR | 16 | `src/main/java/org/jlinalg/differential/EmpiricalBayesDifferential.java`; existing R adapter infrastructure |

Those frequencies are current catalog observations, not fresh package-wide
source censuses or promises of equivalence. No ETA for the entire remaining
goal is established; assess the next bounded scope before estimating it.

## Warnings and required actions

**SKIP3 means a required exact, permitted source/runtime/validation dependency
is missing, unreadable or cannot be installed. Skip its dependent analysis,
report the missing item and how to restore it, and retain the skip until it is
resolved. FAIL2 means invalid/nonfinite data, changed evidence, capacity,
process, schema or another failure: no complete result is admitted. Preserve
inputs, partials and errors, correct the cause and retry into NEW outputs.**

Two unchanged historical marker-verifier mains remain **SKIP3**. To run them,
restore the exact original `build/cli/omicsmatic-0.3.7.jar`, 164,898,617 bytes,
SHA-256 `545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
The different published release is not a replacement; do not alter saved hashes.
Current component checks passed separately and do not resolve that old gap.

For the native BF library, inspect the defined flag and warnings before using
conditional posterior output: an undefined all-zero marker is not a probability
vector. Its CLI refuses to produce a complete conditional result in that case.
Never silently repair IDs, alleles, P values, NaN/Inf, inequalities, populations
or algorithms. Retain known source discrepancies and no-result outcomes.

User decisions persist: implement practical methods in Java; otherwise use
tested upstream adapters. Download/install where the operating environment
allows. Research/nonprofit FIMO and academic noncommercial GATK 3.8 terms were
accepted. **Cell Ranger remains excluded at the user's request.** Warnings
must identify affected analyses and concrete recovery actions prominently.

Both literature HTML reports remain excluded from the public Pages deployment.
Their source and offline notices are saved; old full-page mobile layout and
interactivity remain unqualified. No release was created. Published 0.4.0
predates these workflows: use an appropriate source build or the retained
qualified local candidate rather than claiming released-binary availability.

## Worktrees, saved changes and transfer

The active checkout is `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. The original `D:/projects/JLinAlg` checkout
contains historical dirty overlays; do not reset, clean or broad-stage it.
All 21 worktree HEADs were already ancestors of main at the handover baseline.

The [historical overlay archive and inventory](handover-20261119/README.md)
preserve the nonignored dirty project files across all 21 worktrees. Of 2,248
files, 1,866 exactly match main and are referenced by Git object/hash. The 382
other byte variants are archived, including 288 newline-only variants and 94
substantively different variants. Sixty of those 94 match older main-history
blobs at the same path; 34 did not match that history lookup and are explicitly
retained as drafts. Do not merge these old overlays over the current code.
They add no scientific qualification, implementation credit or new backlog
claim. Their current originals remain unchanged on this machine.

**GitHub is not a full machine backup.** Ignored large builds, full-text XML,
PDFs, author archives, private evidence and installed/restricted runtimes remain
local and need a separate permitted transfer to reproduce full execution.
Preserve at least:

- `D:/projects/JLinAlg/build/literature-review/xml` (frozen primary XML cache).
- Active-checkout `build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf`
  and `build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip`.
- Active-checkout `build/coloc-catalog-admission-1/`, including final
  `verify-1/COMPLETED.json`, plan/apply outputs, controls and frozen snapshots.
- Active-checkout `build/coloc-high-impact-1/`, including all raw reference and
  failed-attempt directories, qualified candidate JARs, and **both**
  `runtime-coloc603-1/library` and `runtime-coloc603-2/library` R trees.
- `D:/projects/JLinAlg/build/git-publish/20261119-coloc-catalog-39/` and the
  subsequent `20261119-final-handover-40/` publication/preservation receipts.
- All earlier worktrees, evidence bundles and runtime stores identified by the
  linked historical checkpoints. Retain them until their approved retention
  or migration process explicitly permits removal.

For fresh catalog verification from the active root, follow the exact paths
and commands in [the guarded driver instructions](../../tools/literature/coloc_catalog/ADMISSION.md).
Use `--verify` and a NEW output directory; **do not reapply the completed
admission**. Missing private dependencies must remain visible as SKIP3.
