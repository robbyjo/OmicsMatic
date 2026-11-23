# Squidpy native component checkpoint and next work

Native Java now provides original-edition weighted-CSR Moran/Geary point
statistics and analytic normality conventions. The normal build passes **740
focused tests**; the actual executable JAR passes **728 component tests on each
of Windows Java21, Windows Java25 and Linux Java21**. All **22,863 preceding JAR
payloads are byte-identical**. Eight new payloads contain the component and
original BSD notices; the dispatcher is unchanged. Six actual copyable example
runs verify both a finite artificial path and a constant-feature FAIL2 condition
on all three JVMs. See the [beginner/API guide](SQUIDPY_AUTOCORRELATION.md).

**The full Squidpy adapter, catalog admission and full 250-paper goal remain
unfinished. Zero new family credit or canonical reviews are added.** Counts
remain **151 scoped families (27 native +124 upstream), 1,679 reviewed/1,043
pending uses**, 250 papers, 446 labels, 485 definitions and 2,722 positive
observations. This component is supplementary prepared-graph functionality,
not a replacement for every function in the upstream application.

## Actual source and runtime evidence

All three unchanged original Squidpy wheels were installed privately and their
installed Python source hashes checked against their official wheel members:
**1.1.2 on Python3.8.20, 1.2.2 on Python3.10.22 and 1.8.4 on Python3.12.3**.
Locks, complete package inventories, wheel hashes and source identities are
retained. P009's reported Python3.8.13 full environment was not reproduced;
3.8.20 is an explicitly different patch edition. Existing Scanpy/SAIGE/R and
system prefixes remain unchanged.

Each edition made 80 actual public `sq.gr.spatial_autocorr` calls on ten
artificial CSR graphs with Moran/Geary, both normalization choices and both
tail choices: **240 calls total, 192 returned frames and 48 original exceptions**.
Raw frames contain **576 feature rows**. Across all editions, 312 stable analytic
rows match the native score/variance/probability at relative3e-12/absolute3e-14
tolerances. Seventy-two additional finite point rows have undefined or
cancellation-dominated analytic variance and receive no native probability.
There are 192 undefined original point rows, plus 144 native no-result feature
outcomes corresponding to the 48 original failed calls. The 720 parameterized
tests cover those outcomes; eight further controls cover independent hand
values, source provenance, input integrity, edition differences and refusal
conditions. Twelve unchanged FUSION/spatial regression tests complete the normal
740-test run.

Graphs cover directed and signed weights, self-edges, unequal weights, isolates,
decimal float64 inputs, row normalization, an empty graph, globally cancelling
weights, moment underflow, zero analytic variance and a strong positive-tail
case that retains the original CDF subtraction to zero. Source expression
precision is asserted as float64 before every qualified call. AnnData0.8.0
needs an explicit dtype to avoid its default float32 conversion; AnnData0.13.4
removed that keyword and preserves the supplied float64 array. The retained
earlier implicit-dtype runs and rejected current-constructor attempt are not
substituted for these qualified references.

The original point algorithm and declared-edition variance formulas are retained.
The original old-edition Geary variance reuse is not silently corrected. Native
admission additionally refuses a positive variance within a documented 64-ulp
moment-cancellation bound, while retaining its computed variance and point.
This deliberate diagnostic differs from an upstream build's apparently finite
probability for a degenerate graph; the guide explains it. The original undefined
`numpy.empty` cells, numerical failures, exceptions, warnings and source row
order remain in the fixture. They are not accepted as probability estimates.

The [qualification packet](squidpy-native-qualification/README.md) stores exact
source/fixture/producer pins, normal-build and packaged reports, runtime attempts,
examples and restorable helpers. The producer is
`D:/projects/JLinAlg/build/fu-1/build/squidpy-1/native-normal-build-1/project-build/cli/omicsmatic-0.4.0.jar`,
SHA256 `395646c6d83a8d37905146a81bf555377d454c908bfdc804daf56d3c68a5a859`.
No existing released JAR was replaced. Read the saved checkpoint offline:

```powershell
python -B tools/literature/verify_squidpy_native_checkpoint.py
python -B tools/literature/verify_squidpy_native_checkpoint.py --jar PATH-TO-EXACT-PRODUCER
```

This reader verifies saved evidence and source context; it does not run a new
scientific analysis. Older handover verifiers deliberately need their old source
context after this component is restored to production source. Their snapshots,
source hashes and failed attempts stay unchanged.

The saved reader passes on both Windows and Linux with the exact producer hash.
Its [five controls](squidpy-native-qualification/READER-CONTROLS.json) admit a
valid copied checkpoint and refuse changed source, changed evidence, a resealed
false packaged-test count and a wrong producer. These are integrity/scope controls,
not a claim that unsigned receipts authenticate an external party's research.

## Warnings and required actions

**SKIP3: dependent upstream analysis skipped.** Provide/install the exact permitted
runtime/input named by the diagnostic, or keep the skip if installation is
prohibited/unavailable. A native method is never a silent fallback for a missing
Squidpy workflow. **FAIL2: no complete result.** Inspect the explicit condition,
preserve all original/partial/failed/undefined evidence, correct its cause and
use NEW output. Check each native result status before using a probability.

Earlier failures remain: legacy1 lacked `pkg_resources`; legacy2's 1.1.2 source
could not import `collections.Iterable` under Python3.10; legacy3's copied
Python3.8 venv lost its shared-library path. The successful **legacy4** environment
links to the unmodified private interpreter under **legacy3/python**. Keep both
directories. No upstream import or scientific function was monkeypatched. Do
not rerun old installers into existing directories or overwrite their errors.

Read the [process-safety incident](HANDOVER_2026-11-22_PROCESS_SAFETY.md); never
execute pre-fix worker JARs or kill unrelated processes. The future adapter must
use captured birth-bound `OwnedProcesses` handles. The component has no external
worker and adds no process termination code.

The full website check still has **33 existing Markdown citation-panel failures**
listed in [the prior validation receipt](fusion-catalog-checkpoint/VALIDATION.json).
No site/Pages workflow bytes changed here. Fix real mapped scientific citations
before claiming website readiness; both unfinished literature report pages stay
withheld. Cell Ranger stays excluded. MEME/FIMO research/nonprofit and GATK3.8
academic noncommercial use decisions persist; newest iDEP remains the requested
edition to evaluate.

## Continue the full implementation goal

1. Implement the complete typed Squidpy upstream adapter for actual paper roles:
   KNN/radius/Delaunay/grid spatial graphs, supplied-graph autocorrelation,
   explicit/captured ligand-receptor interactions and source-verified Nanostring
   CosMx/AtoMx reading. Preserve exact edition signatures, row/identifier order,
   original exports, settings and undefined-row diagnostics. Local source review
   confirms `read.nanostring` exists in1.2.2/1.8.4 but not1.1.2;1.1.2 has no
   `library_key` neighbor argument, and current `spatial_neighbors` is deprecated
   with changed option precedence. Do not drop options or silently upgrade a
   paper's edition. Current `rng` differs from historical `seed`.
2. Add closed requests and input/runtime/source fingerprints, a pre-call
   handshake, safe resource/time/process bounds, complete original export
   capture, offline VERIFY, real REPLAY and independent saved readers. Qualify
   actual packaged commands; neither an installed Python prefix nor this native
   component establishes that adapter.
3. Only then finish guarded source-bound admission for the six candidates
   P008/P009/P010/P024/P094/P161 from the
   [all-250 XML role audit](squidpy-workflow-development/SOURCE-AUDIT.json).
   Preserve P009's broader1.1.2 provenance and existing near-name unknown anchor;
   keep custom GNN/image/temporal/ranking steps separate. P008's exact functions
   remain unspecified. Do not award whole-paper or whole-tool reproduction.
4. Continue high-impact nf-core, smartpca, MR-PRESSO, SCENIC/SCENIC+, newest iDEP
   and other missing capabilities after checking current paper coverage. Complete
   all1,043 pending use reviews and missed-provider screening, preserving FUSION
   P212's cohort exclusion and P108's isoTWAS-baseline distinction. The overall
   objective is active; no reliable full-goal ETA is established.

## Private handover and cleanup

Transfer `D:/projects/JLinAlg/build/fu-1/build/squidpy-1/` in full, including exact
JARs, private environments/interpreter dependencies, wheels, complete raw failed
and successful evidence, source/reference generators and locks. Also retain
the FUSION and older evidence roots and all19 worktrees/private overlays listed
in the [machine-transfer handover](HANDOVER_2026-11-22_SQUIDPY.md). Its historical
export restrictions still apply. GitHub contains a selected source/evidence
checkpoint, not private runtimes, full papers, credentials or a complete machine
backup. The shared dirty root must not replace current main.

After publication, only the verified duplicate compiled test-runner file may be
removed from its temporary packaged-test directory: its exact bytes are retained
in the qualification ZIP with the source and producer. Preserve all scientific
producers, actual test classes/resources, failed evidence and runtime prefixes.
The local publication receipt records removed paths and measured space change.
