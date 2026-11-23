# Toolbox handover: Squidpy draft and remaining work

**The 250-paper collection is complete. The implementation goal is unfinished.**
The admitted catalog contains **151 scoped families: 27 native and 124 upstream**
(154 including three existing adapters). There are **1,679 reviewed and 1,043
pending paper-tool uses**, 2,722 positive observations, 446 named labels and 485
provider definitions. Squidpy adds **zero credit and zero canonical reviews**
at this checkpoint. The frozen collection covers 2023-10-23 through 2026-10-23:
71 Nature, 104 Nature Genetics and 75 AJHG papers; extension was unnecessary.

The preceding published main is `65919926efbd5f828be9cbb147aabbc3b7c99a02`.
Continue in `D:/projects/JLinAlg/build/fu-1`, branch
`codex/squidpy-workflow-20261122`. The handover commit is discoverable in that
branch and main; its exact publication/readback receipts are retained under
`D:/projects/JLinAlg/build/git-publish/20261122-squidpy-handover-130/`.
The [preceding FUSION checkpoint](FUSION_CATALOG_CHECKPOINT_2026-11-22.md)
records the last admitted family and its qualification.

## Warnings and required actions

**Squidpy is a development snapshot, not an enabled command or qualified family.**
Its weighted-CSR Java Moran/Geary component is saved outside compiled sources.
Do not count it as implemented or add paper reviews until the normal build,
meaningful tests, original-edition comparisons, packaged execution, capture
verification/replay and guarded catalog admission succeed. No released JAR was
replaced. See the [restorable draft and receipts](squidpy-workflow-development/README.md).

**Squidpy 1.1.2 cannot import in the attempted Python 3.10.22 environment.**
The original source imports `Iterable` from `collections`. The complete import
error and failed environment are retained. Next obtain a privately installed
compatible historical interpreter and resolve its dependencies in a NEW prefix;
P009 reports Python 3.8.13. Do not patch the upstream source silently, monkeypatch
imports, overwrite the failed attempt, or claim the attempted environment
reproduces that paper. Squidpy 1.2.2 imported successfully in the second legacy
attempt; the containing installer still ended with failure because its later
1.1.2 import failed. Read each step's receipt, not just the overall return code.

**SKIP3 means the dependent analysis is skipped.** Install/provide the exact
permitted runtime, package, binary or input identified by the diagnostic. If
installation is prohibited or the prerequisite remains unavailable, retain the
skip with its affected operation and remedy. **FAIL2 means no complete result is
admitted.** Preserve raw, partial, failed, undefined and superseded evidence;
correct the cause and retry into a NEW output directory. Never turn NA/NaN/Inf,
rounded zero probabilities or an original failure into a successful estimate.

**Squidpy edition differences matter.** Original 1.1.2/1.2.2 reuse Moran's analytic
variance for Geary; 1.8.4 uses the corrected Geary formula. Keep named profiles.
An original analytic routine can leave an undefined feature's probability cell
indeterminate even if that cell appears finite. Retain the raw upstream output
but refuse probability admission for undefined point statistics/variance. The
native draft returns explicit undefined statuses; it does not reproduce
indeterminate memory as a numerical result. Source positive-tail CDF subtraction,
including cancellation to zero, is retained rather than silently replaced.

**Project-wide `checkWebsite` remains failing on 33 existing Markdown vignettes
missing scientific-citation panels.** The prior checkpoint's
[validation receipt](fusion-catalog-checkpoint/VALIDATION.json) lists them.
Add appropriate mapped citations and rerun the check; do not disable the rule or
insert empty panels. Scoped handover/catalog checks are separate. Both unfinished
literature report pages remain withheld from GitHub Pages.

**Read the [process incident handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md).**
Never execute superseded pre-fix external-worker producers. Use the existing
birth-bound `OwnedProcesses` pattern and captured handles; never resolve a PID
again to kill it, kill all descendants by PID, or stop an unrelated process.
No continuing Squidpy worker was found during this handover; installer terminal
receipts are saved. Failed attempts are not ongoing analyses.

## Saved work and actual validation

The development directory contains four byte-identical draft source/license
files, eight helpers (including the corrected standalone probe), dependency
locks, original intake hashes, selected install/import errors and synthetic
original/native numerical receipts. `CAPTURED-FILES.json`, `DRAFT-RESTORE.json`
and `FILES.json` record exact bytes and restore targets. The Java draft requires
a prepared square CSR graph with sorted unique columns; its explicit native
bounds are 100,000 observations and 1,000,000 stored entries. It does not build
graphs, choose genes, draw permutations, adjust multiple tests or implement a
complete Squidpy workflow.

Actual checks before handover:

- Original Squidpy **1.8.4** installed/imported in a new Linux Python 3.12.3 venv;
  **1.2.2** installed/imported in a separate Python 3.10.22 venv. Existing Scanpy,
  SAIGE/R and system prefixes were not modified. Locks and exact package inventories
  are retained. The historical environments are not the authors' full environments.
- The public original `sq.gr.spatial_autocorr` function ran on four artificial
  weighted graphs, with Moran/Geary, both row-normalization choices and both tail
  choices. It returned **64 finite rows and 32 undefined constant-feature rows**.
  Complete original row order, values, warnings and source hashes are saved.
- The native draft compiles using JDK21 `--release 17 -Xlint:all -Werror` against
  the preceding qualified project JAR. The corrected standalone probe produced
  288 rows across three profiles. Comparing the 1.8.4 profile's 64 finite rows
  gave no differences at relative 3e-12/absolute 3e-14 tolerances. Maximum absolute
  score/variance/probability errors were 2.23e-16/1.81e-16/6.67e-16 (upper rounded
  reporting bounds). Its 32 undefined-feature rows had explicit undefined status
  and no admitted probability. The older profiles have not yet been compared
  against actual historical runtimes.
- The original probe helper failed before producing numerical rows because it
  cast the strict JSON reader's `Numeric` token to `Number`. Its header-only
  output and reproduced exception are retained separately. Only the NEW corrected
  probe produced the reported native comparison. This was a helper correction,
  not a change to the native kernel or original input/results.

These are preliminary component checks. No full Squidpy normal-project build,
JUnit suite, workflow adapter, packaged RUN/VERIFY/REPLAY, whole-paper reproduction,
biological conclusion or catalog admission is claimed.

## Exact next work, in priority order

1. Read this handover and validate the saved snapshot:

   ```powershell
   python -B docs/literature/squidpy-workflow-development/verify_handover.py
   python -B tools/literature/close_fusion_catalog.py --verify --out build/NEW-catalog-verify
   python -B tools/literature/verify_fusion_workflow_checkpoint.py
   ```

   Use a fresh output for each controller execution. Keep original byte-pinned
   producers and readers with their source contexts; never refresh historical
   verifier hashes just to make a changed context pass.
2. Finish the practical native weighted-graph component: restore the draft only
   into a clean working branch, add meaningful numerical/undefined/input controls,
   run original 1.2.2 comparisons and obtain a compatible unmodified 1.1.2 runtime.
   Preserve edition-specific Geary conventions and document the native subset.
3. Implement a typed original Squidpy adapter for actual paper operations:
   spatial neighbors (KNN/radius/Delaunay and per-sample boundaries), spatial
   autocorrelation on supplied weighted spatial/expression graphs, ligand-receptor
   inference with explicitly supplied/captured CellPhoneDB/OmniPath interactions,
   and the source-verified CosMx/AtoMx reader. Do not permit hidden changing network
   resources or substitute the existing unweighted Java `SpatialStatistics` graph.
   Check old `seed` versus current `rng` behavior explicitly.
4. Add closed requests, prerequisite/version checks, original input/output/source
   capture, clear skip/failure diagnostics, resource/time limits, safe process
   ownership, offline VERIFY and real REPLAY. Qualify normal-built packaged commands
   with artificial originals and independent readers on relevant hosts. Preserve
   unchanged prior JAR payloads except the reviewed dispatcher extension.
5. Complete a guarded source-bound Squidpy catalog transaction only after
   qualification. The [250-XML audit](squidpy-workflow-development/SOURCE-AUDIT.json)
   found exactly six candidate papers: P008/P009/P010/P024/P094/P161. It contains
   paragraph locators/hashes and concise roles, not redistributed full paragraphs.
   Preserve all previous reviews, unknown editions and unrelated metadata. P009's
   broader software paragraph reports 1.1.2 even though the existing near-name
   extraction does not; record that additional provenance without inventing a
   change to the original extraction anchor. P008 leaves functions unspecified.
   Custom GNN, image, temporal-correlation and downstream ranking work is separate.
6. Re-rank the remaining **258 missing labels** and **22 method-overlap labels**
   by paper coverage, scientific impact and implementable scope. High-impact
   candidates identified include nf-core, smartpca, MR-PRESSO (six candidate papers
   each), and SCENIC/SCENIC+ (five each). Reconfirm current roles and existing
   coverage before implementing; label frequency is not blanket whole-tool credit.
   Evaluate newest iDEP as requested. Practical Java methods are welcome; substantial
   tools should use tested original adapters. Generic statistics do not replace
   GraphPad/FlowJo/FACSDiva applications.
7. Finish all **1,043 pending positive-use reviews**, inspect missed-provider
   extraction across all 250 papers and repair the 33 website citation panels.
   Preserve FUSION P212's cohort exclusion and P108's isoTWAS baseline distinction.
   Keep the reports withheld until their next ready build. No reliable full-goal
   ETA is established.

Cell Ranger stays excluded. MEME/FIMO research/nonprofit permission and GATK3.8
academic noncommercial permission persist. Newest iDEP terms do not establish
legacy iDEP.96 terms. Downloads and private installs are authorized where allowed;
do not request the same general authorization again.

## Transfer before retiring this machine

**GitHub is not a complete machine backup.** Transfer the following privately,
preserving file names, hashes, failed evidence and symlink/junction metadata:

- `D:/projects/JLinAlg/build/fu-1/build/squidpy-1/` in full: original wheels,
  installer, managed Python, all runtime prefixes/locks/caches, source excerpts,
  synthetic inputs, numerical outputs and every failed attempt. The 1.2.2 venv
  depends on the private interpreter under `runtime-legacy-1/python/`; keep both.
  The published snapshot is a selected receipt set, not these full runtimes.
- `D:/projects/JLinAlg/build/gp-1/build/fusion-1/` in full, including exact
  qualified producer `workflow-normal-build-9/project-build/cli/omicsmatic-0.4.0.jar`
  (SHA256 `f722167f292ca0a120e24fcabcf9d2cf344257059e8eef58c68642ea9e27a7dc`),
  runtimes, P108 private author intake and original/failed scientific captures.
- `D:/projects/JLinAlg/build/fu-1/build/fusion-catalog-1/`, the original
  `D:/projects/JLinAlg/build/literature-review/xml/` cache, prior tool runtimes and
  evidence referenced by their checkpoints, root build helpers and
  `D:/projects/JLinAlg/build/git-publish/` publication receipts.
- All **19 registered worktrees**, persistent refs, indexes, unique dirty edits
  and prior private overlays. The shared root is an older dirty branch; do not
  bulk-stage it, reset it or replace current main with its stale catalog.

The nine historical `codex/handover-20261122-ownerNN` exports (01,04,29,30,31,41,
43,45,59) and `codex/release-v0.3.1` remain withheld after possible-sensitive-material
review. Do not merge/export them indirectly. They require a content-specific
review and separate approval; current task publication does not bypass that block.
No private primary text, runtime, credential, session profile or blocked historical
export is included in this handover push.

Cleanup is limited to verified redundant handover scratch copies after publication.
Retain original source inputs, complete captures, runtimes and active/unique work.
The final local receipt records any removed paths and measured free-space change.
The prior entire-browser-profile deletion helper was rejected and must not be run;
its completed safe alternative removed only explicitly verified cache directories.
