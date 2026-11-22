# Handover: FUSION development and remaining toolbox work

The 250-paper collection is complete; implementation and source review remain
**unfinished**. Canonical totals are **150 scoped families (27 native Java + 123
upstream), 1,673 reviewed uses and 1,050 pending uses**: 250 papers, 446 labels,
485 provider definitions and 2,723 observations. The frozen search window is
2023-10-23 through 2026-10-23 (71 Nature, 104 Nature Genetics, 75 AJHG); it did not
need extension. Family counts describe bounded capabilities, not reproduced
papers or complete replacements for upstream packages.

This handover supersedes the current-state claims in the [earlier FUSION
handover](FUSION_WORKFLOW_HANDOVER_2026-11-22.md), which remains historical evidence.
The qualified production baseline remains `833864a156d85578241f436e51128f16d0f97cf0`.
The public handover parent is `f62e3205ca82854b3e391d18bed7bbcbca0168b4`.
**The latest adapter is preserved as development source outside the production
build. The global `fusion-tools` command is not enabled on main. No FUSION family
credit or additional canonical reviews are awarded by this handover.**

## What is saved

The [version 2 development snapshot](../../tools/development/fusion-workflow-20261122-v2/README.md)
contains all **39 exact current draft files**, including six Java workflow
classes, the dispatcher change, ten pinned source/resource files, 30 workflow
tests, artificial example data/requests and their two generators. It includes a
restorable binary-capable patch, 55 exact orchestration helpers and 87 selected
execution receipts/logs. A separate manifest and read-only checker bind the
inventory and source profile. The earlier 16-file snapshot remains unchanged.
The patch was actually applied in an isolated directory and all 39 restored
files matched their original lengths and SHA-256 values. Use the snapshot README
to restore it into a NEW clean development worktree; check its bytes before use.

The adapter implements original-R TRAIN, ASSOCIATE and POSTPROCESS with typed
requests, captured inputs, RUN/VERIFY/REPLAY, explicit runtime compatibility,
closed export inventories, package/runtime fingerprints, resource guards,
birth-checked worker ownership and prominent warnings. Training operates on
copies because original rank normalization can rewrite phenotype files. Original
FUSION source is pinned to `9346c1222bffbb34499fa7a8e23c1b701b55cb05` and remains
byte-identical. Prediction models are blup/lasso/top1/enet/bslmm; SuSiE
colocalization is a separate association option, not SuSiE prediction training.

## Validation actually completed

| Producer / private evidence folder | Measured scope |
| --- | --- |
| `workflow-normal-build-9/` | Normal JDK21/release17 build, executable JAR, **75 tests: zero failures, errors or skips** (45 native/xWAS + 30 workflow tests). Source inputs remained unchanged during the build. |
| `workflow-rounded-gate-control-3/` | Latest build: original R association/COLOC on all three artificial genes, followed by saved-capture checking, returned 0. A real rounding-boundary case preserves the original NA posterior while using the unrounded source p-value for eligibility; all three association rows are finite. |
| `workflow-packaged-6/` with build 8 | **29 cases, all expected**: association, missing-Z, top1, COLOC, BETA/SE, permutation, SuSiE, MHC split, Linux training, postprocessing/report/plots, no-result refusal/retention, replays and matrix bounds. Windows21/25 association and Linux21 association/training; postprocessing plots on Windows. These cases have NOT all been rerun on build 9. |
| `workflow-refusal-controls-2/` with build 6 | 15 offline checks: 12 resealed contradictions refused, one valid copy and Windows25/Linux21 saved-capture verification. Cross-host saved reading is not a cross-host R fit. |
| `workflow-runtime-controls-3/` with build 7 | Six expected outcomes: three missing-prerequisite SKIP3 and three invalid/resource/time FAIL2. The actual timeout closed its one recorded owned process identity. Its worker timer was approximately 10 seconds; total JVM/setup/cleanup time was longer. |
| `invocation-controls-1/` | Ten explicit GCTA/GEMMA startup and forwarding checks; some are stub protocol controls, not scientific fits. Real training also ran. |
| `workflow-training-cv-1/`, `workflow-training-replays-1/` with build 6 | All five models with two-fold CV completed. Strict all-model replay refused differing exports; explicit opt-in allowed and recorded differences. Replay parents were unchanged; external-tool RNG defaults were preserved. No independent training refit/equivalence claim. |
| `workflow-independent-checks-1/` with build 6 | 15 association rows matched unchanged original-R outputs; three complete multi-panel exports matched byte-for-byte. Of 22,840 preceding JAR payloads, 22,839 were identical; only the dispatcher class changed, with 23 added payloads. This proof still needs repeating for the final producer. |
| `workflow-public-examples-1/`, `example-data-regenerated-1/` with build 6 | Both draft example requests ran successfully; all 15 artificial data files regenerated byte-identically with Windows R4.6.1. Other R/platform serialization may differ. |
| `workflow-compatible-plots-1/`, `plot-review-2/` with build 6 | Explicit corrplot0.84 plus individual eQTL/scatter plots ran. Seven pages were visually inspected. Original compact labels overlap in places; figures are retained unchanged and are NOT claimed publication-ready. corrplot0.95 ignores the original cl.lim argument and emits retained warnings. |

Latest private development JAR:
`D:/projects/JLinAlg/build/gp-1/build/fusion-1/workflow-normal-build-9/project-build/cli/omicsmatic-0.4.0.jar`
is 198136697 bytes, SHA-256
`f722167f292ca0a120e24fcabcf9d2cf344257059e8eef58c68642ea9e27a7dc`.
It is a development producer, not an uploaded release or qualified full adapter.
Build 8's JAR is 198136687 bytes, SHA-256
`97c2568a5c1180bffc1ae24f6f99e02159ee5cfd4e219efbd1559de1143022bc`.

The last fix is important: the source emits space-padded NA fields. The earlier
reader could parse a whole numeric column as text and falsely count valid COLOC
rows as absent. Build 9 adds whitespace stripping during typed TSV parsing;
original exports and identifiers remain unchanged. The failing build-8 boundary
capture is retained as `workflow-rounded-gate-control-1/`; build 9's successful
replacement is `workflow-rounded-gate-control-3/`.

Source state is captured before the original final text formatting. Reconstructed
main/MHC tables must match original export bytes. This preserves unrounded
TWAS.P for admission gates; COLOC posteriors already rounded internally by the
original source retain that rounding. Saved VERIFY checks relationships and
hashes; it does not independently decode opaque RDS, refit models, authenticate
coordinated replacement, attest the host or impose an OS-wide memory quota.

## Exact work to resume, in priority order

1. Restore and verify the v2 snapshot in a NEW clean worktree. Preserve the old
   snapshots and all failed/superseded evidence. Keep current production intact
   until qualification and guarded admission are complete.
2. Add the still-missing mixed finite/no-result TSV regression: subset two weight
   files to the eight variants in missing.sumstats, leave the third at twelve,
   run max_impute=0 with explicit no-result retention, and require two finite
   association rows plus one original no-result row. Do not remove difficult rows
   to make a test pass. Repeat the full packaged case suite on build 9 or a NEW
   producer after further edits. Prepared v7 packaging helper targets a fresh
   `workflow-packaged-7/` but has NOT been run.
3. Repeat current-producer offline resealed controls, add missing source-RDS and
   contradictory reconstructed-table controls, then current runtime/time/process
   controls, all-model CV/replay policy and public example requests into NEW
   outputs. Existing `build/fusion-example-*` outputs must not be overwritten.
   Recheck matrix guards against actual GRM/model/reference-matched LD allocation.
4. Repeat independent original-R export comparison and the complete preceding
   JAR payload comparison on the final producer. Build a public, independent,
   read-only qualification reader/evidence bundle with actual producer/source
   identities and explicit platform/operation limits. Its current absence is a
   qualification gap; this snapshot checker does not replace it. Prepare beginner
   workflow documentation, test all links and capture current compatible plots
   if claiming them for that producer. Preserve originals and format limitations.
5. Finish the guarded FUSION catalog transaction. P112/P120/P131/P181/P183/P238
   are six software analysis/comparator papers. P108 adds a missing baseline
   description: inspect author code before claiming upstream execution or its
   SuSiE baseline. P212's pending tool observation is a FUSION study-cohort
   homonym. Preserve extraction provenance and prior reviews; derive new counts
   from the transaction rather than guessing. No correction is admitted yet.
6. Continue higher-impact gaps after checking the current inventory: Squidpy,
   smartpca, MR-PRESSO and newest iDEP with its actual license/dependencies.
   Implement practical Java methods natively and substantial tools via tested
   original adapters. Complete all 1,050 pending use reviews and missed-provider
   screening across all 250 papers; the full objective remains unfinished.
7. Repair the 33 previously recorded website citation-panel omissions and rerun
   the full website check. They are not claimed fixed or freshly recounted here.
   Keep unfinished-report Pages exclusions. No site change, deployment or release
   is part of this handover push.

## Private machine transfer and warnings

**TRANSFER BEFORE RETIRING THIS MACHINE:** GitHub does not contain the full
runtime/evidence environment. Copy `D:/projects/JLinAlg/build/gp-1/build/fusion-1/`
in full, all `D:/projects/JLinAlg/build/*fusion*20261122_12*.py`/`.R` helpers,
`build/git-publish/`, older tool evidence, paper XML/PDFs and the worktrees' unique
files/indexes/refs through an appropriate private channel. Retain original,
partial, failed, null and superseded outputs. Only selected synthetic receipts
are public. Actual JARs, installed libraries and upstream executable downloads
remain local. Handle symlinks/junctions as links, not blind recursive copies.

The fresh pre-push inventory found **18 registered worktrees**, with dirty files
in four; the previous 74-worktree count is historical. This handover preserves
the other 17 current worktrees and their changes. Owner checkout:
`D:/projects/JLinAlg/build/gp-1`, branch `codex/gprofiler-workflow-20261122`.
The root checkout has unrelated work and must not be reset or staged wholesale.
Fresh publication receipts: `D:/projects/JLinAlg/build/git-publish/20261122-fusion-handover-125/`.

Windows Rscript: `C:/Program Files/R/R-4.6.1/bin/Rscript.exe`.
Permitted Linux distro: `JLinAlg-Omics-SAIGE-20261026`, R4.5.2 at
`/opt/jlinalg-saige-runtime-v1/source/.pixi/envs/default/bin/Rscript`.
Keep that existing prefix read-only. Preserve the private libraries
`runtime-install-1/library`, `workflow-windows-library-1/library`,
`workflow-windows-corrplot084-2/library`, `workflow-linux-install-1/library`,
their package TSVs/intake/install logs, and `binary-intake-1/` plus
`gcta-modern-intake-3/`. Read exact request/package files rather than guessing.
Private paths in the draft examples must be edited on the receiving machine.

**SKIP3 — DEPENDENT ANALYSIS SKIPPED:** provide the named exact permitted runtime,
package, input or binary and retry NEW output. Downloads/private installs are
authorized where the environment permits; otherwise retain the skip and state
the missing prerequisite and remedy. Cell Ranger stays excluded. MEME/FIMO
research/nonprofit use and GATK3.8 academic noncommercial terms are already
accepted. P052's unavailable exact historical g:Profiler build remains skipped
before gene submission. Do not ask for the same permissions again.

**FAIL2 — NO COMPLETE RESULT:** preserve raw logs and partial exports, fix the
stated input/protocol/numeric/resource/time/process problem, and retry NEW output.
Do not silently repair identifiers, NA/NaN/Inf, zero p-values, omissions, source
parameters or stochastic differences. A development completion marker alone
does not qualify the adapter. Legacy GCTA1.21 segfaults; modern GCTA startup and
GEMMA output-directory compatibility require explicit recorded opt-in. GEMMA's
strict default path produced missing files despite an upstream zero exit; those
failures remain retained. Real source analyses are not replaced by stubs.

**PROCESS SAFETY:** a superseded test may have terminated an unrelated older
process; application/recovery remain unknown. Read the [incident handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md).
Use current birth-checked, identity-bound ownership. Do not execute pre-fix
worker producers, stop unrelated processes or restart an unidentified application.

**HISTORICAL EXPORTS STILL WITHHELD:** automatic approval review previously
rejected exports of owner01/04/29/30/31/41/43/45/59 under
`codex/handover-20261122-ownerNN` and `codex/release-v0.3.1` for possible sensitive
material. This push does not merge or export them indirectly. Preserve their
private copies; a content-specific review and separate export approval remain
necessary. Original g:Profiler HTTP headers may contain cookies: publish only
the explicitly redacted copies. The full goal has no reliable completion ETA
until the remaining review/dependency/qualification work is sized.
