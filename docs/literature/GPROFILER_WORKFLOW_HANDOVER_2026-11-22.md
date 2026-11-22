# g:Profiler workflow development handover

**The full toolbox goal is unfinished.** The frozen collection contains 250 papers
(71 Nature, 104 Nature Genetics, 75 AJHG), dated 2023-10-23 through 2026-10-23.
Canonical counts remain **149 scoped families: 27 native Java + 122 upstream**,
or 152 including three pre-existing adapters. There are **1,666 reviewed and
1,057 pending tool-use observations**, 446 named labels and 485 provider definitions.

This checkpoint saves the new global `gprofiler-tools` RUN/VERIFY/REPLAY workflow,
its normal-build tests, original-service controls, privacy-safe evidence and an
**unapplied catalog model**. It adds **zero credited families or canonical reviews**.
The earlier [API component checkpoint](GPROFILER_API_COMPONENT_CHECKPOINT_2026-11-22.md)
and all original evidence remain unchanged. The draft's proposed 150-family,
1,673-reviewed/1,050-pending transition is **not installed or approved by a complete
catalog admission check**. Do not report those proposed totals as current.

## Saved implementation and verification

The global source-built JAR exposes `gprofiler-tools`; see the
[command guide](GPROFILER_WORKFLOWS.md). Native Java handles contracts, HTTPS,
exact full-build/organism preflight, recording, validation and offline replay.
Enrichment statistics, mapping and GO highlighting execute in the original
g:GOSt service. This is neither a native statistical reimplementation nor a
complete original R/Python client, paper reproduction or independent refit.

- Normal JDK 21 build: 28 focused tests, zero failures/errors/skips (13 API,
  15 file-workflow tests), and executable JAR build pass.
- Windows packaged JAR: eight enrichment cases plus metadata discovery; each
  completes RUN, VERIFY, offline REPLAY and replay VERIFY. They preserve 4,089
  returned rows and 1,647 original null evidence cells.
- Linux Java 21 on the existing permitted WSL environment: nine saved VERIFYs,
  three saved REPLAYs and three replay VERIFYs. No Linux live-service claim.
- Actual public CLI refuses 21 malformed/resealed saved captures and passes
  11 runtime/resource/replay refusal controls, including an owned junction alias.
- Original Python 1.0.0 source is exercised unchanged against eight recorded
  responses using an explicit `requests.post` stub. The installed requests
  runtime and original R clients were not qualified; presentation differences
  remain recorded.
- P052's exact required build `e111_eg58_p18_30541362` is unavailable at the named
  archive: it returns `e111_eg58_p18_28c0ce60`. **SKIP3 occurs after metadata only,
  before gene upload. No substituted historical analysis/result is admitted.**

The [evidence index](gprofiler-workflow-qualification/EVIDENCE.json),
[saved qualification receipts](gprofiler-workflow-qualification/QUALIFICATION.json)
and [source/data acknowledgements](gprofiler-workflow-qualification/NOTICE.md)
bind the packet. Its `FOR_ADMISSION` receipt describes the completed runtime
checks; catalog admission itself remains pending.

Run the read-only integrity/census check from the repository root:

```powershell
python -B tools/literature/verify_gprofiler_workflow_checkpoint.py
python -B tools/literature/verify_gprofiler_component_checkpoint.py
```

The new checker reads the closed ZIP without extraction, checks source hashes,
JUnit counts, publication lineage, raw/JSONL values, nulls and saved receipts.
It does not execute Java, contact the service, independently reproduce statistics
or replace the still-needed complete qualification/admission reader. Optional
`--jar PATH` additionally hashes the exact private producer without executing it.

## Resume in this order

1. Finish an independent qualification reader over the actual recorded protocol,
   all refusal causes, class provenance and replay/normalization semantics. The
   current handover checker verifies integrity and measured scope only. Preserve
   the original Python-client differences, combined `-1` absence sentinels,
   null evidence, literal zero p-values, server normalization and mapping warnings.
2. Finish `tools/literature/gprofiler_catalog/model.py` and add a guarded
   plan/inspect/apply/verify controller. It must preserve the previous 1,666 ledger
   records, all 2,716 unrelated observations and 445 other inventory rows. Complete
   model/transaction/concurrency/rollback/refusal tests. Bind the canonical base
   at `396062fb37479d84e95280a46ebc547db64ad78d`, or explicitly rebase/revalidate
   if main has advanced. Do not modify frozen historical readers or their hashes.
3. Independently re-audit the seven primary roles against the frozen all-250
   census/private XML; inspect styled desktop/mobile preview and then installed
   reports. Preserve R client 0.2.1/0.2.3 versus service/database build distinctions,
   and each paper's background, direction, ordering and unknown settings. P227's
   original extracted version stays null; recovered client context is separate.
4. Only after these checks, admit one scoped upstream family and seven reviews
   transactionally, update the complete checksum manifest (currently 851 members;
   the draft expects two new records), verify both source and installed states,
   and publish the reviewed transition. Neither a saved PASS receipt nor a draft
   model grants credit by itself.
5. Continue higher-impact gaps: FUSION appears in seven papers; Squidpy, smartpca
   and MR-PRESSO in six each. Recheck current primary roles, method overlap and
   permitted upstream terms before implementation. Finish all 1,057 pending
   use reviews and missed-provider screening across the complete frozen corpus.
   Evaluate newest iDEP; legacy iDEP.96 terms remain unresolved. Cell Ranger stays
   excluded by the user. Native Java remains preferred where practical.
6. Separately repair the 33 pre-existing website citation-panel omissions recorded
   in the [website failure receipt](genomicranges-checkpoint-20261122/website-check.json),
   then rerun `checkWebsite`. This remains a known project-wide failure; the scoped
   development checks do not establish release readiness. Both unfinished
   literature reports stay excluded from Pages. No release was created here.

## Transfer these local files before retiring the machine

GitHub contains source and declared publication copies, not a full runtime/evidence
backup. Preserve all **74 worktrees**, indexes and dirty files, including the shared
`D:/projects/JLinAlg` checkout. Do not reset it to main or replace its variants.

- Current owner: `D:/projects/JLinAlg/build/gp-1`, branch
  `codex/gprofiler-workflow-20261122`.
- Complete original current/failed/superseded evidence and private raw HTTP headers:
  `D:/projects/JLinAlg/build/gp-1/build/gprofiler-workflow-1/`.
- Exact current producer:
  `build/gprofiler-workflow-1/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar`
  within that owner; 198,006,052 bytes, SHA-256
  `cf2d785bb88c4df7478f230ffef6d0b404a0ea8f2ba2b8dba5f73fe5919b9560`.
- The predecessor `normal-build-1` and previous live/control attempts remain
  historical. Do not overwrite them or refresh their recorded hashes.
- Original component/reference evidence: `build/gp-1/build/gprofiler-1/` under
  the shared root; primary XML cache: `build/literature-review/xml/`.
- Current publication receipts/helpers: `build/git-publish/20261122-gprofiler-workflow-119/`
  and `build/*gprofiler*20261122_119.py` under the shared root. Scripts use fixed
  new destinations; inspect before rerunning. Prior receipt directories are retained.
- Previous qualified runtime/dependency transfer paths remain listed in the
  [previous published handover](HANDOVER_PUBLICATION_2026-11-22.md).
- Retained junction
  `build/gp-1/build/gprofiler-workflow-1/public-runtime-controls-2/owned-parent-junction`
  points to `live-packaged-3/highlight/run` in the same evidence root. Transfer
  link targets deliberately; do not blindly recurse/delete through it.

## Prominent warnings and required actions

**Raw HTTP traces contain Set-Cookie values. Keep originals private.** Public ZIP
copies replace only those values with hash-labeled placeholders, retaining header
presence/counts, original manifests and unchanged scientific payloads. Eighty-five
header files are transformed; 21 valid copies verify and 21 invalid controls are
refused by the actual JAR. Do not publish private originals or claim the derivative
hashes authenticate their origin. See the packet notice for GO/HPO attribution.

**SKIP3: analysis skipped.** Supply the exact permitted input/runtime/build named
in `WARNINGS.md`, installing privately if allowed; otherwise retain the explicit
skip. Changing to an available database build creates a different analysis and
must not be described as reproducing P052. **FAIL2: no complete result admitted.**
Keep the failed output/logs, correct the named cause and retry in a NEW directory.
Do not silently repair identifiers, NA/NaN/Inf, unknown settings or source behavior.

**Use current process-safe source for external-worker execution.** A superseded
test may have terminated an unrelated older Windows process; its application and
recovery remain unknown. Pre-fix external-worker JAR paths must not be executed.
Retain those producers for historical evidence/known offline checks. The corrected
birth-checked ownership code does not requalify every upstream workflow. No unknown
processes were stopped in preparing this checkpoint. Build with JDK 21 targeting
Java 17; an unrelated pre-existing JDK 25 compiler warning remains.

**Previously blocked historical exports remain local.** Automatic approval review
rejected nine broad `codex/handover-20261122-ownerNN` snapshots (NN 01, 04, 29, 30,
31, 41, 43, 45, 59) and `codex/release-v0.3.1` because they may contain sensitive
material. Specific export approval remains pending; do not merge their payloads
or upload the private incremental bundle as a workaround. Private paper XML,
author notebooks, manuscripts, runtimes and failed evidence require private
transfer. This publication preserves them locally.
