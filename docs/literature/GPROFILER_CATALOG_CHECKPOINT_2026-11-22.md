# g:Profiler catalog checkpoint

The catalog admits **one scoped upstream family and seven source reviews** for
the global `gprofiler-tools` workflow. Current counts are **150 scoped families
(27 native Java + 123 upstream)**, 153 including three pre-existing adapters;
**1,673 reviewed uses and 1,050 pending**. The frozen cohort remains 250 papers,
446 tool labels, 485 provider definitions and 2,723 observations. The full
implementation objective is unfinished.

All 1,666 prior reviews, 2,716 unrelated observations, 445 other inventory rows,
paper identities and original extraction/version anchors are preserved. Admission
uses an exact retained predecessor from main `744d366e3d2246bfe9831a3eec30a931fc864c35`,
a guarded plan/inspect/apply transaction and the complete 853-entry checksum
manifest. The [admission proof](omics-2023-2026/gprofiler-catalog-validation.json)
and [seven-role crosswalk](omics-2023-2026/gprofiler-source-role-coverage.json)
bind the before/after records; the [retained plan](gprofiler-catalog-admission/PLAN.json)
contains the full measured admission scope.

## Scientific scope and exact paper limitations

Java implements the request contract, HTTPS recording, exact full-build/organism
preflight, result/arithmetic validation, closed inventories and offline saved
VERIFY/REPLAY. Original g:GOSt executes enrichment, identifier mapping and GO
highlighting upstream. This is an upstream adapter, not a native enrichment
algorithm, complete R/Python package, independent statistical refit, biological
calibration or reproduction of every paper's analysis.

| Paper | Reviewed use and retained limitation |
| --- | --- |
| P052 | Selected RNA/protein gene enrichment and reported defaults. Exact required database build `e111_eg58_p18_30541362` is unavailable: the archive reports `e111_eg58_p18_28c0ce60`. **SKIP3 before gene upload; no substituted historical result.** Preserve the unusual protein minus-log-q selection wording. |
| P088 | One-sided/g:SCS enrichment of 63 prioritized genes with an Ensembl 110 protein-coding background. Keep the FUMA background separate. September 2023 dates do not establish a full service build. |
| P090 | gprofiler2 R client 0.2.3, pathway/GO driver analysis and Bonferroni-adjusted cumulative-hypergeometric probabilities. Client edition is distinct from the unreported database build. |
| P096 | NS-Forest marker g:GOSt, September 14 2023 web date, adjusted-P threshold and GO driver highlighting; supplemental g:SCS description. Complete snapshot/background settings remain unknown. |
| P208 | Ranked protein-altering de novo genes, ascending denovolyzeR gene p-values, 22,321 exome-capture targets as custom background and GO BP/CC/MF. Exact IDs, tie handling and correction remain unreported. |
| P227 | gProfiler2 R client 0.2.1, **HPO under-enrichment** and 3,092 inactive protein-coding genes as background. Separate regression Bonferroni wording does not establish this call's correction. Original extracted version remains null; recovered client context is displayed separately. |
| P228 | GO analysis after DESeq2 padj < 0.1 selection and removal of Y-chromosome genes. Service/client edition, background and correction remain unknown. |

The frozen all-250 primary census and each paragraph/DOI/XML identity were
rechecked. There are seven named roles, no unbound broader candidates and no
uninspected matching tables. These are reviewed method uses, not seven reproduced
biological analyses. The current service build `e114_eg62_p19_9dc92ac8` is never
substituted for an unreported or unavailable historical edition.

## Verification and how to rerun it

The unchanged actual source-built producer has 28 focused normal-build Java tests,
eight Windows enrichment cases plus metadata discovery, nine Windows offline
replays, nine Linux saved verifications and three Linux replays. Its eight
enrichment cases preserve 4,089 complete original rows and 1,647 null evidence cells.
Twenty-one malformed/resealed controls and 11 runtime/resource/replay controls
are retained. The unchanged official Python 1.0.0 source is compared using a
declared recorded-response stub; an installed requests runtime and R clients
were not executed/qualified.

The new qualification reader independently inspects all 21 valid RUN/replay
bundles: source requests, route/origin, exact builds/organisms, scientific echoes,
normalization, mapping, full JSONL values, original populations, ratios, combined
absence sentinels, driver flags and warning/count provenance. It checks 28 selected
class fingerprints against the optional exact JAR. A fresh actual packaged CLI
run accepts all 21 valid publication copies and refuses all 21 invalid copies;
input files remain unchanged. [Source-bound fresh runtime evidence](gprofiler-protocol-validation/STATUS.json)
is separate from the original saved qualification packet.

Twenty-three protocol/model/transaction tests pass on each Windows/Linux host.
Four actual source CLI controls refuse a missing/wrong JAR, changed plan source
pins and changed preview while retaining every canonical metadata/manifest byte.
Styled preview and installed desktop/mobile checks cover all seven role rows,
reported version types, family filters/counts and prominent recovery warnings.
Installed Windows/Linux readers recheck the full manifest and all 250 original
XML identities. The [verification receipt index](gprofiler-catalog-checkpoint/EVIDENCE.json)
retains the measured results, screenshots and failure/refusal receipts.

From the repository root, choose a NEW output directory:

```powershell
python -B tools/literature/close_gprofiler_catalog.py --verify --out build/gprofiler-check-new
python -B tools/literature/verify_gprofiler_workflow_checkpoint.py
```

The first command is the current installed catalog/source/protocol check. Add
`--cache PATH_TO_XML_CACHE --jar PATH_TO_EXACT_PRODUCER` to recheck the private
primary files and actual producer bytes/classes. Neither command re-executes
enrichment or contacts the service. Historical GenomicRanges/component readers
need their declared historical contexts; do not refresh old hashes to make them
accept later catalogs. Use the [workflow guide](GPROFILER_WORKFLOWS.md) for new
RUN requests, metadata discovery and offline replay.

## Warnings, recovery and remaining work

**SKIP3: dependent analysis skipped.** Restore the named exact permitted
input/runtime/full build, installing privately where allowed; otherwise retain
the skip. P052's exact build is unavailable and remains explicitly skipped.
**FAIL2: no complete result admitted.** Preserve logs and partial/provisional
outputs, fix the named input, protocol, capacity or timeout failure and retry NEW
output. Require a valid completion marker/manifest and successful VERIFY. Do not
silently repair IDs, NA/NaN/Inf, settings, zero p-values or original absence/nulls.

**Raw HTTP headers contain Set-Cookie values; originals stay private.** Public
copies explicitly redact only cookie values, preserve header presence/counts,
retain original manifest lineage and leave scientific payloads unchanged.
Read the [source/data notice](gprofiler-workflow-qualification/NOTICE.md) for GO,
HPO and original-client acknowledgements. Integrity hashes do not authenticate
coordinated replacement. Complete resolved custom-background mapping is not
returned by the service and remains unobserved.

Continue high-impact FUSION after the [homonym/source audit](FUSION_SOURCE_AUDIT_2026-11-22.md):
six software-analysis/comparator papers plus one additional method-baseline
description, with cohort/instrument mentions kept separate. Then review
Squidpy, smartpca and MR-PRESSO, other remaining tools, all 1,050 pending
use reviews and full-corpus missed-provider screening. Native Java remains
preferred where practical; use tested upstream adapters for substantial software.
Evaluate newest iDEP, without assuming legacy iDEP.96 terms. Cell Ranger remains
excluded. The confirmed MEME/FIMO research use and accepted GATK3.8 terms persist.

Preserve all **74 worktrees**, the shared dirty checkout, original inputs,
runtimes, failed/null/superseded evidence and pre-fix producers. Use current
process-safe source for external workers: a superseded test may have terminated
an unrelated older process, whose application/recovery remain unknown. Old
external-worker JAR paths remain unsafe; no release assets were replaced or
requalified. The [development handover](GPROFILER_WORKFLOW_HANDOVER_2026-11-22.md)
lists the exact private transfer paths, producer hash and retained junction.
New admission work/evidence is in the same owner under `build/gprofiler-catalog-1/`;
fresh runtime qualification is `build/gprofiler-workflow-1/admission-runtime-1/`.

The admission commit `caf2c4f03f0a16e8383c26b46e11b80fde3b5bb4` passed both build
and deployment in the [exact-commit Pages run](https://github.com/robbyjo/OmicsMatic/actions/runs/45059437333).
Live home/CSS/JavaScript match the source after line-ending normalization, and
both unfinished report URLs return 404. The [public deployment receipt](gprofiler-catalog-checkpoint/pages-public-verification.json)
records the run, artifact and observed boundary. The subsequent source-audit and
receipt documentation changes no published website bytes.

The 33 pre-existing website citation-panel omissions remain open; project-wide
`checkWebsite` is not claimed to pass. Both unfinished literature reports stay
excluded from Pages. No release or full-goal completion is claimed. Historical
exports previously rejected by automatic approval review for possible sensitive
material remain local and require specific approval, as recorded in the handover.
