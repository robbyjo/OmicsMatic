# Toolbox handover and remaining work, 19 November 2026

**Resume from GitHub main. The latest scientific baseline is
`a638b0ae3610001332c46022aeeff5f47bdb706d`; this handover adds documentation
and a verification receipt. The 250-paper implementation and individual
source-review goal is unfinished.**

## Current position

The frozen cohort contains **250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG**, dated 2023-10-23 through 2026-10-23. No extension was needed. Retain
this established window rather than moving it when resuming.

The latest admitted catalog is [Enrichr](ENRICHR_CATALOG_CHECKPOINT_2026-11-19.md).
Its [admission proof](omics-2023-2026/enrichr-catalog-validation.json),
[summary](omics-2023-2026/summary.json) and 812-entry cumulative manifest record:

| Item | Current total |
| --- | ---: |
| New scoped integrations | **136** |
| Native Java / upstream integrations | 25 / 111 |
| Including three pre-existing adapters | 139 |
| Named families / effective definitions | 446 / 485 |
| Paper/tool observations | 2,703 |
| Individually reviewed / pending uses | **1,462 / 1,241** |
| Missing / method-overlap definitions | 259 / 39 |

These are bounded integrations, not whole-package equivalence or reproduced
papers. The recent Scanpy work has **no admitted family credit yet**. Do not
increment the catalog merely because its classes or dispatch entries exist.

## Work already completed

Three separate Scanpy milestones are committed and published. Their frozen
sources, original-reference fixtures, compact evidence, readers and examples
are retained. Do not restart their implementation or rewrite their saved pins.

| Milestone | Qualified scope and saved validation | Commit |
| --- | --- | --- |
| [Native preprocessing and source census](SCANPY_PREPROCESSING_CHECKPOINT_2026-11-19.md) | Float64 dense/CSR filtering, normalization, log1p and scaling, explicit 1.9.3/1.13.0 profiles; 426 checks per JVM, including 418 original-reference cases; 250 XML identities and 22 source roles | `e3211aa370993c7d10d73b32c44325d297469934` |
| [Ordered upstream file workflow](SCANPY_WORKFLOW_CHECKPOINT_2026-11-19.md) | Original 1.13.0 CPU RUN/VERIFY/REPLAY; 13-step MatrixMarket and four-step provided-H5AD programs covering 15 distinct operations; 58 original array/annotation comparisons agree; 20 Java checks per JVM and 21 worker controls | `33d94e818d3e03c7785563145980780c3e1860e4` |
| [Native Pearson normalization](SCANPY_PEARSON_CHECKPOINT_2026-11-19.md) | Float64 dense/CSR analytic Pearson matrix profiles for 1.10.4/1.13.0; source-specific zero conventions and strict arithmetic admission; 735 checks per JVM, including 720 original-package cases; standalone Java library/examples | `a638b0ae3610001332c46022aeeff5f47bdb706d` |

The three JVMs are Windows Java 21/25 and Linux Java 21. The Pearson kernel
does not fit SCT or select HVGs. The workflow's 29 allowlisted operation names
are broader than its 15 qualified operations. Historical author editions,
settings, complete author pipelines and large-cohort capacity remain separate
gaps. VERIFY checks captured structure and consistency; it does not
independently refit models or authenticate coordinated replacement.

## What to do next, in order

1. Read the three checkpoints and verify the saved packets using the commands
   below. Preserve all successful and failed attempts. Use NEW output paths.
2. Add the **global `scanpy-tools` dispatcher** in
   `src/main/java/org/jlinalg/cli/JLinAlgCli.java`. At handoff this route and its
   help line are **not implemented**; the standalone packaged class already
   works. The intended change is two routing lines and one help line. Review
   an explicit compatibility reader that proves the exact inverse back to the
   baseline dispatcher. Use `enrichr_workflow_transition.py` and
   `verify_enrichr_current.py` as precedents. Do not change old verifier hashes,
   overlay scientific source or imply that an unchanged old owner accepts the
   new dispatcher. Rebuild the normal JAR; qualify packaged help, refusal,
   VERIFY, RUN and REPLAY as appropriate, preserving unaffected class payloads.
3. Complete the **22-paper Scanpy coverage crosswalk** from the frozen
   [census](scanpy-preprocessing-checks/CENSUS.json) and
   `tools/literature/scanpy_source_roles.py`. The census already identifies
   all 22 existing observations, 53 named paragraphs and **21 new review
   candidates**; P061 already has a review and must remain unchanged. The
   crosswalk and cumulative admission are **not implemented**. Distinguish
   tested native APIs, tested current upstream operations, development-only
   operations, external tools and unresolved author settings for each role.
4. Qualify the highest-impact uncovered Scanpy branches needed by those roles.
   Prioritize other HVG flavors and batching, then useful regression,
   downsampling/Scrublet, scoring/cell-cycle and trajectory operations as
   justified by actual paper use. Reuse the native preprocessing/Pearson work.
   Implement practical Java methods directly; retain tested upstream adapters
   for substantial packages. Record historical-edition gaps explicitly rather
   than assigning modern defaults to old papers.
5. Finish **guarded cumulative Scanpy admission through the Enrichr
   predecessor**. Follow `tools/literature/enrichr_catalog/` and its
   [driver instructions](../../tools/literature/enrichr_catalog/ADMISSION.md).
   Preserve all 1,462 existing review objects, the frozen paper identities,
   original before ZIPs, exact review inverse and 812-entry predecessor
   manifest. Use a separate exact historical Enrichr checkout at
   `bd26330459c35c5ec6e5be904d349280bb353f6e` for the original unchanged owner;
   do not reset an existing worktree. Plan, inspect, apply and verify with
   concurrent-edit guards. Derive new counts from the actual reviewed proposal.
   Do not reapply the completed Enrichr admission.
6. Continue the **1,241 pending individual reviews** and other high-impact
   missing scopes. Recheck current code, catalog and primary methods before
   choosing among SuSiE/GSEA (19 observed papers each), GCTA (17), edgeR (16)
   and other candidates. Those frequencies come from the retained catalog,
   not fresh package-wide source censuses. Finish useful bounded milestones,
   validate, and push to main without force. There is no established ETA for
   the entire remaining goal.

No implementation edits for steps 2-5 were started after the Pearson baseline.
Do not mistake this proposed sequence for completed code or applied metadata.

## Source details that matter for the next Scanpy work

The present worker rejects nonfinite numeric annotations. Current Seurat-v3
HVG selection deliberately uses missing ranks for some unselected genes;
those source-defined annotations need an explicit audit and controlled
representation before this flavor can be admitted. Do not use a general
`nan_to_num` repair. Explicit `subset=True` changes the retained gene population
and is not a workaround for an author's different request. Normalizing before
or after HVG subsetting also changes library denominators; preserve the
declared layer/raw/order semantics.

The `cell_ranger` HVG flavor is separate from the excluded Cell Ranger program.
Its MAD calculation uses `statsmodels.robust.mad`; an unscaled generic median
absolute deviation is not equivalent. The existing 14-package workflow guard
does not separately bind statsmodels, so qualify that dependency before
claiming reproducible support. Percentile-bin duplicates, strict cutoffs,
top-gene ties, singleton bins, float32 exported dispersion annotations and
batch aggregation also require original-source controls. The source is saved
under active `build/scanpy-high-impact-1/upstream-intake-2/selected-source/`.

Keep literal paper ambiguities in the crosswalk: P031 says log10 and *most
highly expressed* genes; P048 states log2-plus-one then normalization; P055's
t-SNE is OpenTSNE; P060 uses log dispersions greater than zero; P062 separates
Pearson HVGs, SysVI and square-root-normalized plots; P061 is an ALLCools-derived
methylome workflow. P070/P085/P123 contain unresolved entry-point spellings;
do not silently correct them. The census contains the full per-paper cautions.

## Warnings and required actions

**SKIP3: an exact permitted dependency, source, runtime or input is missing,
unreadable, or cannot be installed/executed. Skip its dependent analysis,
identify the missing item prominently, restore it where permitted and retry
into NEW output. A skipped analysis has no admitted result.**

**FAIL2: changed evidence, invalid schema/data, undefined or nonfinite
arithmetic, lost positive model terms, capacity, timeout, interruption or
process failure. Preserve inputs, diagnostics and partials; correct the cause
and retry into NEW output. Do not report a complete result or silently repair
IDs, dtype, alleles, P values, thresholds, populations, NaN/Inf or algorithms.**

Two historical marker-verifier mains remain **SKIP3**. Their exact original
dependency is `build/cli/omicsmatic-0.3.7.jar`, **164,898,617 bytes**, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
Restore those original bytes to run them. The different published release is
not a substitute; do not refresh the saved hashes. The recent component
checks pass separately and do not resolve these historical skips.

User decisions persist: downloads/installs are authorized where allowed;
research/nonprofit FIMO use and academic noncommercial GATK 3.8 terms were
accepted. **Cell Ranger is excluded at the user's request.** Read each
workflow's `WARNINGS.md`, status/report files and logs before using output.

Both `literature-review.html` and `literature-warnings.html` remain withheld
from Pages by `.github/workflows/pages.yml`. No release was requested or
created. Published 0.4.0 predates these Scanpy entry points: use a current
source build, a retained qualified candidate, or the explicitly restored
native development component.

## Copyable saved-proof checks

From a current repository root, use Python 3.11+ and paths that do not exist:

```powershell
python -B docs/literature/handover-20261119/verify.py
python -B tools/literature/verify_scanpy_preprocessing.py --out build/handoff-preprocessing-NEW --restore-component
python -B tools/literature/verify_scanpy_workflow.py --out build/handoff-workflow-NEW --restore-capture
python -B tools/literature/verify_scanpy_pearson.py --out build/handoff-pearson-NEW --restore-component
```

The preprocessing reader can additionally repeat the frozen 250-paper source
census with `--rescan --primary-cache D:/projects/JLinAlg/build/literature-review/xml`
when that exact permitted private cache is available. These commands read
saved proof; they do not reinstall or rerun scientific models. Restored native
components require only Java. The workflow reader restores synthetic captures,
not the Python runtime or full normal JAR.

With a current source-built JAR, the standalone capture audits are:

```powershell
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.ScanpyWorkflowCli --verify build/handoff-workflow-NEW/capture
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.ScanpyWorkflowCli --verify build/handoff-workflow-NEW/provided-capture
```

The [handoff check receipt](HANDOVER_CHECKS_2026-11-19_SCANPY.json) records the
fresh saved-proof checks, 250-XML census, two Java capture audits, unchanged
catalog manifest and preservation checks. It is not a new full repository
test suite, fresh scientific refit or biological validation.

## Preserved work and separate transfer

The active checkout is `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. All **22 worktree HEADs are ancestors of
the main scientific baseline**. Every other worktree and its dirty files
remain intact; do not reset, clean or broad-stage the original root.

All **2,248 nonignored dirty files** across the older worktrees are already
preserved by the public [overlay inventory/archive](handover-20261119/README.md):
1,866 match main byte-for-byte; the other 382 variants are archived verbatim.
They include 34 drafts unmatched by the earlier same-path main-history lookup.
Preserve them as historical drafts; do not bulk-merge old catalogs or source
over current main. The one local branch outside main is the historical
`codex/release-v0.3.1`; its four commits are already public under the original
`v0.3.1` tag. Do not merge its obsolete release version metadata into main.

**GitHub is not a full machine backup.** Source, tests, compact public proofs
and historical nonignored overlays are published. Ignored full-text caches,
large builds, original captures, private evidence and installed runtimes
require a separate permitted transfer. Keep at least:

- The entire active `build/scanpy-high-impact-1/`, including all original and
  failed `native-*`, `workflow-*` and `pearson-*` attempts, source intakes,
  wheels/locks, runtime inventories and public-freeze copies.
- Current normal source candidate
  `build/scanpy-high-impact-1/pearson-gradle-1/project-build/cli/omicsmatic-0.4.0.jar`;
  small native library in `pearson-package-1/`; earlier workflow candidate in
  `workflow-gradle-4/project-build/cli/`. Check exact identities in their
  checkpoints rather than substituting another same-named JAR.
- WSL distribution `JLinAlg-Omics-SAIGE-20261026`, with exact prefixes
  `/var/tmp/omicsmatic-scanpy-current-20261119-1/environment`,
  `/var/tmp/omicsmatic-scanpy-1104-pearson-20261119-1/environment` and
  `/var/tmp/omicsmatic-scdrs-reference-2/environment`. Never install into these
  frozen prefixes; use a NEW environment for a changed reference stack.
- `D:/projects/JLinAlg/build/literature-review/xml`, active
  `build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf` and
  `build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip`.
- Active `build/enrichr-catalog-admission-1/`,
  `build/enrichr-catalog-admission-1/coloc-predecessor-checkout` and all earlier
  source/evidence/runtime stores identified by the linked checkpoints.
- Private new-output helper scripts under `D:/projects/JLinAlg/build/`,
  especially `gradle_scanpy_pearson_20261119.py`,
  `package_qualify_scanpy_pearson_20261119.py`,
  `drive_scanpy_pearson_reference_v2_20261119.py`,
  `drive_scanpy_pearson_extra_reference_20261119.py`,
  `qualify_scanpy_normal_workflow_v4_20261119.py` and
  `qualify_scanpy_replay_and_controls_v2_20261119.py`. Review configured output
  paths before use; these intentionally refuse reuse of existing directories.
- Publication and preservation receipts under
  `D:/projects/JLinAlg/build/git-publish/`, especially stages
  `20261119-scanpy-preprocessing-44`, `20261119-scanpy-workflow-45`,
  `20261119-scanpy-pearson-46` and `20261119-final-handover-47`.

Some private setup receipts contain full environment dictionaries. Transfer
them privately as permitted; do not publish them wholesale. Preserve the
73,525 previously pinned local artifacts and all earlier retention decisions.
No active scientific process needs to be awaited at this handoff.
