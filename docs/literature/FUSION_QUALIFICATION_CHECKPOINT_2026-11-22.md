# FUSION original-workflow qualification checkpoint

The original FUSION TRAIN/ASSOCIATE/POSTPROCESS adapter now has a source-bound
qualification bundle and independent read-only checker. The global command is
`fusion-tools`; read the [beginner workflow guide](FUSION_WORKFLOWS.md) and
[artificial examples](../../examples/fusion-original/README.md). This checkpoint
adds **zero family credit and zero canonical reviews** until the guarded FUSION
catalog transaction is completed. Canonical totals remain **150 scoped families
(27 native +123 upstream), 1,673 reviewed/1,050 pending uses and 250 papers**.
The full 250-paper implementation objective remains unfinished.

This supersedes the development status in the [handover](FUSION_WORKFLOW_CHECKPOINT_2026-11-22.md).
Both historical development snapshots and all earlier failed producers remain
unchanged. Original source is pinned to `9346c1222bffbb34499fa7a8e23c1b701b55cb05`;
the original three R scripts, PLINK utility and GPL-3.0 license remain byte-exact.

## Measured evidence

The normal JDK21/release17 build passed **75 tests, zero failures/errors/skips**.
Its private development JAR is 198136697 bytes, SHA-256
`f722167f292ca0a120e24fcabcf9d2cf344257059e8eef58c68642ea9e27a7dc`.
The packaged checks below all use that producer. No release asset is replaced.

| Check | Actual scope |
| --- | --- |
| 30 packaged cases | 28 completed and two correctly refused: Windows21/25 association; Linux21 association and training; Windows21 postprocessing, LDSC, omnibus/correlation, reports and plots; missing-Z/top1/COLOC/BETA-SE/permutation/SuSiE colocalization; main/MHC split; no-result policies; actual replays; GRM/model/reference-matched LD capacities. |
| Mixed result regression | Two finite associations and one original no-result retained with explicit policy; the same result refused without that policy. Original input files unchanged. |
| Rounding boundary regression | Raw source TWAS.P=0.013947978477149943, formatted export=0.0139, threshold=0.01392398923857497. The original missing COLOC posterior is preserved and correctly ineligible; all three associations remain finite. Source formatting is reconstructed exactly. |
| 17 offline verifier controls | 14 resealed contradictions refused, one valid copy and two cross-host valid reads. Includes missing source RDS, contradictory reconstruction, source/canonical mismatch, input/export closure, actual command, runtime fingerprints, counts and final manifest/marker byte capacity. |
| Six runtime/resource controls | Three SKIP3 prerequisites (R edition, package edition, missing GEMMA), plus input budget, output budget and actual timeout FAIL2. The timeout recorded and closed 13 owned identities; worker time was about 10.1s, total invocation about 18s. No OS containment claim. |
| Training CV/replay policy | All five original models with two-fold CV completed. Strict replay refused differing exports; explicit opt-in RUN/replay admitted and reported differences. Replay parents unchanged; external RNG defaults unchanged. |
| Public examples | Windows association and Linux all-model/two-fold training each completed, followed by separate successful VERIFY. Only input/runtime/output addresses were edited for the new checkout. |
| Independent unchanged-original comparison | All scientific fields in 15 association rows match five direct original-R reference cases (only declared FILE-prefix transport excluded). Three complete multi-panel exports match byte-for-byte. |
| Complete JAR payload comparison | 22,839 of 22,840 preceding payloads identical. Only the global dispatcher class changed; 23 new payloads. The optional checker argument verifies the entire actual producer and payload map. |
| Compatible figures | corrplot0.84 plus individual eQTL/scatter modes completed. Seven current rendered pages match their preceding renders byte-for-byte and were visually inspected. Original compact labels overlap in places; original figures are unchanged, not publication-ready artifacts. |
| Independent saved reader | Windows Python and permitted Linux Python read the same closed qualification bundle without R/Java/network. Cross-host saved reading is not a cross-host fit. |

The [public evidence index](fusion-workflow-qualification/EVIDENCE.json),
[status](fusion-workflow-qualification/STATUS.json) and
[license/scope notice](fusion-workflow-qualification/NOTICE.md) bind untransformed
artificial captures, normal-build source/test receipts, original comparisons and
refusal/runtime evidence. Evidence is deduplicated by object hash in evidence.zip;
the checker reads it without extraction. No actual JAR, installed runtime/binary,
paper XML/PDF or cookie-bearing HTTP capture is exported. Integrity is not a
detached signature or authentication against coordinated replacement.

```powershell
python -B tools/literature/verify_fusion_workflow_checkpoint.py
python -B tools/literature/verify_fusion_workflow_checkpoint.py --jar "PATH_TO_THE_EXACT_RECORDED_JAR"
```

The checker validates current scoped Java/resource source, normal-build binding,
the complete optional JAR payload map, closed saved input/result populations,
original/admitted export equality, runtime editions/unchanged closure, process
receipts, result/no-result counts parsed independently from TSV, raw-source
format reconstruction companions, replay policy and original reference exports.
Opaque RDS remains bound by hash, not independently decoded by Python. Original
R performed the source-object/option/matrix checks during the recorded runs.
No independent training refit, whole-tool qualification, paper reproduction or
biological calibration is claimed. Windows external-binary training and Linux
R plotting are not qualified by these checks.

## Remaining work and private evidence

Complete the guarded FUSION catalog update next. The six software-analysis or
comparator papers are P112/P120/P131/P181/P183/P238. P108's missing baseline
description needs author-code inspection before claiming original upstream
execution or SuSiE prediction. P212 is a study-cohort homonym. Preserve all
source extraction provenance, prior reviews and identities; derive new counts
from the transaction. Then prioritize remaining tools by actual paper coverage,
finish the 1,050 pending reviews and scan all 250 papers for missed providers.
The previously recorded 33 website citation-panel omissions remain open.

Owner: `D:/projects/JLinAlg/build/fu-1`, branch
`codex/fusion-qualification-20261122`. Complete private evidence and runtimes
remain under `D:/projects/JLinAlg/build/gp-1/build/fusion-1/`, especially
`workflow-normal-build-9`, `workflow-packaged-7`, `workflow-mixed-no-result-1`,
`workflow-rounded-gate-control-3`, `workflow-refusal-controls-5`,
`workflow-runtime-controls-5`, `workflow-training-cv-2`,
`workflow-training-replays-4`, `workflow-public-examples-2`,
`workflow-compatible-plots-4`, `plot-review-3` and `workflow-independent-checks-4`.
Keep old attempts and all libraries/JARs for private machine transfer.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the exact named permitted input,
runtime, package or binary; install privately where allowed or retain the skip
with its remedy. **FAIL2: NO COMPLETE RESULT.** Keep raw/partial/failed/null/
superseded evidence, fix the stated cause and retry NEW output. Read the guide's
original NA/NaN/Inf/zero-p, compatibility, process/resource and replay conditions.
Cell Ranger remains excluded; prior MEME/FIMO and GATK3.8 use permissions persist.

**Process safety and withheld exports:** retain the [incident handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md).
Do not execute pre-fix worker producers or stop/restart unrelated processes.
Historical owner01/04/29/30/31/41/43/45/59 handover branches and release-v0.3.1
remain withheld after automatic review flagged possible sensitive material;
content-specific review and separate export approval are still needed. Preserve
the other worktrees and their unique edits. GitHub is not the full private
machine transfer. The full objective remains active and unfinished.
