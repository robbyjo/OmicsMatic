# Handover: qualified Squidpy workflows; catalog admission remains open

**Start here. The full 250-paper implementation goal is unfinished.** The frozen
collection is complete: 250 papers, Nature71/Nature Genetics104/AJHG75, within
2023-10-23..2026-10-23. No extension was needed. The catalog still has **151 scoped
families (27 native Java +124 upstream;154 including three existing adapters),
1,679 reviewed/1,043 pending uses,446 labels and2,722 positive observations**.
The new Squidpy adapter is qualified within the scope below, but its separate
six-paper catalog transition is unfinished: **zero new family credit/reviews**.
Do not use the draft's projected152/1,685 totals as current results.

This supersedes the unfinished-work descriptions at the top of the
[previous handover](SQUIDPY_WORKFLOW_HANDOVER_2026-11-22.md), without modifying
its original failure evidence. The shared root checkout is an older dirty branch.
Work only from the owned checkout `D:/projects/JLinAlg/build/fu-1`, branch
`codex/squidpy-qualification-20261122`, or a new isolated checkout. Its publication
base is `ffe594faa3d69ccf388088c0facbb25738a95445`. Never bulk-stage/reset the root.

## Warnings and required actions

**SKIP3: dependent analysis is skipped.** Supply/install the exact permitted
runtime/input named in the diagnostic. If installation is prohibited or the
prerequisite is unavailable, retain the skip. Do not silently substitute a method
or edition. **FAIL2: no complete result is admitted.** Preserve original, partial,
undefined, failed and superseded evidence, fix the named cause, and retry in NEW
output. Check `analysis-status.json`, warnings and per-row statuses; require a
successful saved VERIFY. An upstream object returning is not sufficient.

**Sixteen earlier development MatrixMarket exports are invalidated for transport
qualification.** The retained SciPy float32 exports requested17 significant digits
and contain NUL padding; an independent original SciPy reader crashed with exit139.
The corrected exporter preserves the original array/dtype, uses9 significant
digits for float32/17 for float64, and rejects control bytes before parsing input.
Do not strip padding or repair old evidence in place. The old bytes, exact producer
contexts, diagnostic crashes and invalidation list are retained in the packet.
The separately qualified native prepared-CSR component is unaffected.

**GitHub is not a complete machine backup.** Transfer the private roots below
before retiring this machine. No runtime binaries, full paper texts, private
cohort/manuscript data or credentials are included in this publication.

**Historical export remains restricted.** Automatic approval review previously
rejected nine broad `codex/handover-20261122-ownerNN` snapshot branches
(NN01,04,29,30,31,41,43,45,59) and `codex/release-v0.3.1` because they may contain
sensitive material. Content-specific review and separate export approval remain
pending. Do not merge them, upload their private incremental bundle or export
them indirectly. All19 registered worktrees and the other18 owners' exact dirty
files/indexes remain preserved. Current publication covers the reviewed current
work; these historical/private exceptions remain local.

**Website validation remains open:** full `checkWebsite` has33 existing missing
scientific-citation panels, listed with file identities and remedies in
[the retained validation](fusion-catalog-checkpoint/VALIDATION.json).
Add actual mapped citations and rerun the full check; do not weaken it or invent
panels. Both unfinished literature reports remain withheld by the Pages workflow.
This handover does not publish a release or replace released JAR assets.

## What is qualified

The global `squidpy-tools` command now routes source-bound RUN, offline VERIFY
and actual REPLAY through original Squidpy1.1.2/1.2.2/1.8.4. Supported operations
are spatial neighbors, Moran/Geary supplied-graph autocorrelation, explicit
locally captured ligand-receptor pairs, and source-available Nanostring reading.
See the [commands, beginner examples and precise limits](SQUIDPY_WORKFLOWS.md).
Original Python computation was exercised on Linux; Windows21/25 and Linux21
packaged tests/offline verification have distinct recorded scope. This does not
qualify every Squidpy function, reproduce an author's full environment, interpret
opaque H5AD bodies offline, or reproduce a whole paper.

The current producer is
`build/squidpy-1/workflow-normal-build-145/project-build/cli/omicsmatic-0.4.0.jar`
under the owned checkout:198,261,646 bytes,
SHA256 `05c3c3d7c4a043fa5233a7a0a1c92d1dba4d27ba57d732e5dc353603fe345c9d`.
It is a retained local producer, not a GitHub release asset. Validation records:

- 763 normal-build tests;763 packaged tests on each Windows21/Windows25/Linux21 JVM.
- 73 packaged scenarios:60complete,6expected FAIL2,7prerequisite SKIP3;
  11 actual replays. Original edition-specific exceptions stay explicit.
- 66 independent original-API comparisons, backed by46 direct original calls
  (43 returned/3 raised), without importing the adapter operations or native kernel.
- 22 offline controls, including five resealed scientific contradictions checked
  by both Java and independent Python;44 saved-verification host checks.
- Four actual runtime/work/input/deadline controls,27 MatrixMarket transport
  controls,12 H5AD controls,12 work-estimate controls, and four example RUNs/one REPLAY.
- 661 original NumPy-generated primitive-array fixtures, including3D RGB,
  versions1/2/3,endianness/layout,integer widths and exact large integer payloads.
- 22,870 preceding JAR payloads remain byte-identical; the reviewed global
  dispatcher is the sole modified preceding payload. New payloads are Squidpy-only.

The immutable [qualification status](squidpy-workflow-qualification/STATUS.json)
pins39 sources plus a content-addressed12.5MB evidence ZIP/4,725 logical files.
The [handover packet](squidpy-workflow-qualified-handover-20261122/README.md)
adds fresh saved-checkpoint checks on Windows/Linux, four contradictory-checkpoint
controls per host, a fresh all250 primary audit and the855-entry catalog check.
These handover checks reread evidence; they do not rerun biological analyses.

## Exact work to resume

1. Verify the saved checkpoints and transfer private prerequisites. From the
   repository checkout, with Python3.11+ and assertions enabled:

   ```powershell
   python -B docs/literature/squidpy-workflow-qualified-handover-20261122/verify_checkpoint.py
   python -B tools/literature/verify_squidpy_workflow_checkpoint.py
   python -B tools/literature/verify_squidpy_native_checkpoint.py
   python -B tools/literature/close_fusion_catalog.py --verify --out build/NEW-catalog-check
   python -B tools/literature/audit_squidpy_sources.py --cache D:/projects/JLinAlg/build/literature-review/xml --out build/NEW-squidpy-source-audit
   ```

   The first two workflow readers require only the standard library. The separate
   capture reader uses NumPy. `--jar PATH` on the workflow/native readers also
   verifies the appropriate exact retained producer. Old native status accurately
   describes its historical component checkpoint; do not refresh its hashes just
   because a later full adapter exists.

2. Finish the guarded Squidpy catalog admission, **before awarding credit**.
   `tools/literature/squidpy_catalog/{primary,model}.py` and the audit wrapper are
   saved drafts. Their syntax and fresh primary audit pass; the model transition
   is not transaction-tested/admitted. Start from the exact eight canonical
   predecessor files pinned in the handover's `CATALOG-BASELINE.json`.
   Create the controller/wrapper, plan/inspect/apply/current-verify, source/model/
   rollback/concurrent-write tests, explicit admission guide and styled desktop/
   mobile preview/installed UI checks. Reuse the FUSION/seurat compare-and-swap
   pattern and retained predecessor ZIP; expect855→857 manifest members only after
   checking the actual coverage/proof additions. Preserve all1,679 prior reviews,
   445 unrelated inventory rows and2,716 unrelated observations. Canonical pages,
   ledger, summary, warnings and manifest have **not** been updated here.

   Before applying: make `primary.audit` accept saved predecessor `papers.json`
   bytes, and use those bytes for fresh audits after admission; otherwise updated
   review fields change its `original_observation` binding. Extend the existing
   report version renderer for Squidpy's broader provenance while preserving the
   original near-name unknown-version field. The draft currently does neither.
   `frozen_primary()` also expects an admission source packet not yet created.
   After real guarded admission the *proposed*, unclaimed totals are152 scoped
   families (27native+125upstream),1,685 reviewed/1,037 pending,21 method-overlap,
   258missing, with the same2,722 observations. Keep all paper/full-goal flags false.

3. Admit only the six source roles in the handover `ROLES.json`:
   P008 unspecified per-library Visium analysis; P009 per-sample Delaunay;
   P010 ligand-receptor inference with an unknown exact database snapshot/caller;
   P02430-neighbor per-sample neighborhoods; P094 Squidpy1.2.2 Moran statistics;
   P161 CosMx/AtoMx conversion and25/50/75/100-micrometre graphs.
   P009 broader paragraph118 reports Squidpy1.1.2/Python3.8.13; tested3.8.20 is
   compatible validation, not that exact reported full environment. P161's
   historical reader/edition remain unknown: a working `read.nanostring` interface
   is not proof of its use. GNN/manual images/temporal correlations/custom ranking
   stay separate. Preserve coordinate units, library/FOV/sample boundaries and
   unknown options;1.1.2 lacks the reader/library-key parameter.

4. Re-rank remaining high-impact gaps using actual current source roles and
   existing provider coverage. Candidates previously identified include nf-core,
   smartpca, MR-PRESSO, SCENIC/SCENIC+ and newest iDEP. The packet's mechanical
   `REMAINING-CANDIDATES.json` is a starting list, not validated priority: remove
   aliases, wrappers, comparators and already-covered operations before spending
   implementation effort. Use practical native Java methods and tested original
   adapters. Complete all1,043 pending semantic use reviews and missed-provider
   screening across all250 papers. Preserve FUSION P212's cohort-homonym exclusion
   and P108's isoTWAS method-baseline distinction. No reliable full-goal ETA exists.

Cell Ranger remains excluded. MEME/FIMO research/nonprofit and GATK3.8 academic
noncommercial permissions persist. Assess newest iDEP as requested; its terms do
not establish legacy iDEP.96 licensing. General permitted download/install
authorization already exists. Make prerequisite/error remedies prominent to users.

## Private transfer, process safety and cleanup

Transfer all existing private roots named in the preceding handovers, plus the
entire owned `build/squidpy-1` tree (current/failed captures, exact JARs and source
contexts, original wheels, locks, probes, installers, managed interpreters).
Keep root `build/` helpers/publication receipts, primary XML cache, all registered
worktrees/refs/indexes/dirty overlays and prior private transfer inventories.

**Also transfer the full WSL distro/base system** `JLinAlg-Omics-SAIGE-20261026`,
including `/var/tmp/omicsmatic-squidpy-workflow-20261122-134` with symlinks/managed
Python bases. Current1.8.4 uses system `/usr/bin/python3.12` and its standard
library/base OS as well as its private venv. Copying only that prefix, or only D:,
is insufficient. Other versions are1.2.2/Python3.10.22 and1.1.2/Python3.8.20.
The D-mounted legacy2 venv depends on legacy1/python;legacy4 depends on
legacy3/python. Preserve all linked roots. No WSL export/transfer was performed.

No current Squidpy science batch is left running: the latest build,73-case suite,
independent comparisons and controls have terminal receipts. Preserve unrelated
processes. Read the [process incident and remedy](HANDOVER_2026-11-22_PROCESS_SAFETY.md);
do not execute pre-fix external-worker producers. Use only captured birth-checked
`OwnedProcesses` handles, never later PID lookups. Resource checks monitor observed
usage; they are not an OS hard RAM quota or job-containment guarantee.

Cleanup is limited to exact, redundant, completed handover-reader control copies
after publication. Their original/altered bytes are recoverable from the current
source packet and `control-recovery.zip`; the packet records each mapping and
measured free-space change. All original/failed scientific evidence, runtimes,
producer contexts and unique work remain retained. Do not run old broad cleanup
helpers, delete a browser profile, or blanket-delete `build/`.
