# Squidpy workflow handover: saved draft and remaining work

The 250-paper collection is complete for the frozen **2023-10-23 through
2026-10-23** cohort: Nature71, Nature Genetics104 and AJHG75. Implementation and
source-use review remain unfinished. Current admitted coverage is **151 scoped
families (27 native +124 upstream)**, or154 including three pre-existing adapters;
**1,679 reviewed/1,043 pending uses**, 2,722 positive observations, 446 labels and
485 provider definitions. There are258 missing labels and22 method-overlap labels.
Do not treat a scoped implementation as whole-tool or whole-paper reproduction.

The [native Squidpy component](SQUIDPY_NATIVE_CHECKPOINT_2026-11-22.md) remains
qualified and published. A larger original-runtime adapter is now saved in a
[restorable development packet](squidpy-workflow-handover-20261122/README.md).
Its **14 exact draft files,13 helpers, all prototype compile contexts, original
artificial probes and selected closed workflow captures** are retained. Draft
Java/resource/test files are stored outside normal compiled source paths for
handover; no global `squidpy-tools` dispatcher route is enabled. **Zero additional
family credit or canonical reviews.** The original native API remains available.

## Read the failures before resuming

**Unqualified adapter: do not use it for production analysis yet.** The prototype
compiles with Java21 `--release 17 -Xlint:all -Werror` and13 contract tests pass.
Direct operation probes on unchanged original Squidpy1.1.2/1.2.2/1.8.4 returned
10/13/15 successful exports and2/1/0 original exceptions. Those probes use the
draft operations wrapper and are not an independent direct-call oracle. A normal
project build and full packaged-adapter qualification have not been completed.

The first snapshot records36 closed cases and one unexpected FAIL2. The final
follow-up records **42 closed cases:40 expected outcomes and two unexpected
FAIL2 results**. All three batches ended without stopping a job for handover.
The1.1.2 batch completed18 cases, including actual KNN/Moran/ligand-receptor
REPLAY;1.2.2 and1.8.4 each stopped at case12, Nanostring. Later cases in those
two batches were not attempted. Read `FINAL-STATUS.json` and
`FINAL-BATCH-RECEIPTS.json`; the original snapshot is retained unchanged.
Both newer-edition Nanostring readers return successfully, but Java's reused
`PopsWorkflowNpy.inspect` rejects the original RGB image shape `[6,8,3]` with
**"Scalar, vector or two-dimensional NPY required"**. Its raw H5AD, images,
source outputs and failed admission remain saved. Add bounded, independently
verified multidimensional primitive-image handling in the Squidpy namespace;
do not weaken unrelated qualified NPY behavior or discard the image. Repeat the
actual reader captures in NEW output after fixing it. These are adapter failures,
not original-reader failures.

Expected original failures remain distinct:1.1.2/1.2.2 spectral transformation
raises `ValueError: inconsistent shapes` on the retained fixtures, and1.1.2
`use_raw=True` with a selected gene subset raises an original length mismatch.
Do not patch the original functions or silently upgrade editions. Earlier draft
capture attempts failed before the pre-call acknowledgment because a copied
runtime walker excluded Python's actual `html` package. That exclusion is fixed
in compile4/5, and original failed captures are retained. Compile1 has its own
syntax-error receipt. No historical result or verifier hash is refreshed.

**SKIP3 means the dependent analysis is skipped.** Install/provide the exact
permitted runtime/input named in the diagnostic; if installation is prohibited
or unavailable, keep the skip and do not substitute a native result silently.
**FAIL2 means no complete result is admitted.** Preserve original, partial,
undefined and failed evidence, fix the stated cause, and retry in NEW output.
`worker/report.json` records the original API-return phase; `analysis-status.json`
records final Java admission. A returned upstream object alone is not completion.
Undefined `numpy.empty` probability cells and adjusted families tainted by them
must not become estimates. Read each admitted-row status and all warnings.

## Exact work to resume, in priority order

1. Verify the saved packet and existing native/catalog checkpoints using the
   commands below. Inspect live private `workflow-cases-<version>-1/PROGRESS.json`,
   `RESULT.json` and exact `.PROCESS.json` receipts first. The final follow-up
   confirms all batches ended; two stopped on a failed case and have no success
   receipt. Do not blindly restart their helpers: each creates a
   new directory and stops on the first unexpected case. They use frozen
   `workflow-compile-5` classes/resources, retained with their exact source context.
2. Restore the14 files from `RESTORE.json` into a clean separate working branch,
   checking every SHA256. Keep this packet and old producer contexts unchanged.
   Fix the Nanostring image admission described above. Finish independent
   verification of replay-comparison maps/policies, the relationship between
   exported normality z, point statistics and standard error, ligand-receptor
   metadata-to-result axis coverage, and final process-deadline handling.
   Review resource/work estimates and all supported intake/array shapes.
3. Finish actual Java capture/VERIFY/REPLAY for all four operations and all three
   editions: KNN/radius/Delaunay/grid graphs, supplied spatial/expression-graph
   Moran/Geary, explicit local ligand-receptor pairs and Nanostring reading.
   Include seed-versus-rng, library/FOV boundaries, raw/layer/obs attributes,
   MatrixMarket/H5AD/prepared JSON, undefined/FDR cases, mutated evidence,
   resource/time/process failures, missing/wrong prerequisites and identifier
   transformations. Preserve source warnings, exact row order and NA/NaN/Inf.
   The1.1.2 reader and library-key neighbor parameter are unavailable upstream.
4. Generate an independent oracle using direct original public API calls,
   without importing the draft `operations.py` or native Java implementation.
   Compare complete scientifically defined outputs; retain original undefined
   cells separately. Add an independent saved-bundle reader with meaningful
   contradictory/resealed-evidence controls. The current development reader
   checks preservation and reporting; it does not independently refit models.
5. Run the normal project build and actual packaged cases on appropriate JVMs,
   keeping prior payloads byte-identical except an explicitly reviewed dispatcher
   extension. Only after successful qualification enable the global command,
   write beginner examples and qualify source-bound catalog admission. The new
   draft has **no packaged JAR or global command** yet. Keep all failed contexts.
6. Admit only verified roles from the
   [six-paper source audit](squidpy-workflow-development/SOURCE-AUDIT.json):
   P008/P009/P010/P024/P094/P161. P009's broader software paragraph reports1.1.2;
   preserve its near-name unknown-version anchor and record the broader evidence
   separately. P008's exact functions remain unspecified. Manual GNN, images,
   temporal correlation and custom ranking are separate methods. Coordinates
   retain caller units; sample/library/FOV partitioning must be explicit.
7. Re-rank high-impact missing families by current paper roles and useful scope.
   Candidates include nf-core, smartpca, MR-PRESSO, SCENIC/SCENIC+ and newest iDEP.
   Use native Java for practical methods and tested original adapters otherwise.
   Complete all1,043 pending use reviews and missed-provider screening across
   all250 papers. Preserve FUSION P212's cohort exclusion and P108's isoTWAS
   baseline distinction. The full objective is unfinished; no reliable full-goal
   ETA is established.

Run from the repository checkout; use a fresh catalog output each time:

```powershell
python -B docs/literature/squidpy-workflow-handover-20261122/verify_checkpoint.py
python -B tools/literature/verify_squidpy_native_checkpoint.py
python -B tools/literature/close_fusion_catalog.py --verify --out build/NEW-handover-catalog-check
```

The native reader was rerun successfully with the exact retained producer during
this handover. It checks the existing740 normal-build tests,728 packaged tests
per Windows21/Windows25/Linux21 JVM,240 original calls and22,863 unchanged prior
payloads. This is saved-checkpoint verification, not a new scientific run. The
855-entry cumulative catalog manifest also passes. The full website check still
has **33 existing missing citation panels**, listed in
[the validation receipt](fusion-catalog-checkpoint/VALIDATION.json). Repair them
with actual mapped scientific citations before claiming website readiness. No
site/Pages workflow is changed here; unfinished report pages remain withheld.

Cell Ranger stays excluded. MEME/FIMO research/nonprofit and GATK3.8 academic
noncommercial permissions persist. Evaluate newest iDEP as requested; its terms
do not establish legacy iDEP.96 licensing. General permitted download/install
authorization already exists; do not ask for it again.

## Private machine transfer and process ownership

**GitHub does not contain installed runtimes, full private evidence, full paper
texts, credentials or every historical worktree overlay.** Before retiring this
machine, transfer the roots in the
[machine-transfer handover](HANDOVER_2026-11-22_SQUIDPY.md), plus these additions:

- Entire `D:/projects/JLinAlg/build/fu-1/build/squidpy-1/`, including frozen
  compile1..5 contexts, exact native producer JAR, all successful and failed
  probes/captures, locks, wheels, installers, managed interpreters and runtimes.
  D-mounted1.1.2 legacy4 depends on legacy3/python;1.2.2 legacy2 depends on
  legacy1/python. Keep both linked interpreter roots and venvs.
- **New Linux-filesystem runtime root**
  `/var/tmp/omicsmatic-squidpy-workflow-20261122-134` inside WSL distro
  `JLinAlg-Omics-SAIGE-20261026`. Preserve this root and symlinks, including its
  managed `python/` bases. The three exact locked environments import unchanged
  Squidpy1.8.4/Python3.12.3,1.2.2/Python3.10.22 and1.1.2/Python3.8.20. Their
  interpreter bytes match retained originals. These are not the authors' full
  environments; P009 reports Python3.8.13. New prefixes do not modify old ones.
  `linux-runtime-install-1/RESULT.json` and `ENVIRONMENTS.json` record locations,
  full package probes and locks. **This WSL-only root is not covered by copying
  the D: project folder and has not been separately archived by this handover.**
- Complete root `build/` helpers/publication receipts and all19 registered
  worktrees, refs, indexes and unique dirty overlays. The shared root remains an
  older dirty branch; never bulk-stage it or overwrite main with its stale catalog.

The nine historical `codex/handover-20261122-ownerNN` exports (01,04,29,30,31,41,
43,45,59) and `codex/release-v0.3.1` remain withheld after possible-sensitive-material
review. They need content-specific review and separate approval. Do not merge or
export them indirectly. Current publication preserves other worktrees unchanged.

Read the [process-safety handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md). Never
execute pre-fix worker producers or stop unrelated processes. Use only captured,
birth-checked `OwnedProcesses` handles, never a later PID lookup. Existing private
prefixes and active scientific jobs must be preserved. Cleanup is limited to
verified redundant current draft source copies after exact capture; all frozen
compiled contexts, raw/failed evidence, runtimes and unique work remain retained.
The saved relocation receipt records14 redundant copies,258,678 logical bytes
removed and299,008 measured bytes recovered. The first receipt writer failed
after removal; every exact saved copy was restored, checked and removed again
with the corrected writer. Both attempts and recovery remain recorded. No raw
or failed scientific evidence, runtime or frozen producer was removed. Never run
the previously rejected entire-browser-profile deletion helper.
