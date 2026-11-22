# GenomicRanges qualification and remaining toolbox work

One original upstream GenomicRanges provider is admitted. Current totals are
**149 scoped families (27 native Java + 122 upstream), 152 including three
pre-existing adapters; 1,666 reviewed uses and 1,057 pending**. The frozen corpus
still has 250 papers, 446 labels, 485 provider definitions and 2,723 observations.
There are 258 missing definitions, 24 method-overlap entries and three aliases
without additional family credit. The full implementation objective is unfinished.

**Unresolved website check:** `checkWebsite` fails because33 existing Markdown
vignettes lack its required scientific-citation panels, first reported at
`docs/vignettes/broad-gsea-tools.md`. All33 offending files and the check rule
are byte-identical to previous main `fe5bd98225520a866451e6aabcd7480a93efb753`.
The named list, logs and remedy are retained in
[the website failure record](genomicranges-checkpoint-20261122/website-check.json).
Add appropriate panels in a separately reviewed change that preserves previous
qualified contexts, then rerun `checkWebsite`. No project-wide site-check success
or release readiness is claimed. GenomicRanges' scoped checks below pass.

**Process safety remains relevant.** An earlier superseded test may have
terminated an unrelated older process; its application and recovery remain
unknown. Do not run pre-fix external-worker JARs. Use corrected source builds
with JDK21; existing release assets have not been replaced. Preserve old producers
for historical evidence and known offline verification. The current workflow
uses birth-checked immediate-child discovery and retained native handles.
Periodic observation is not OS job containment or proof of observing every child.

## Implemented scope and evidence

The global `genomicranges-tools` command provides RUN, offline VERIFY and fresh
REPLAY for explicitly prepared GRanges RDS objects. Operations are find/subset
overlaps, nearest, distance-to-nearest, promoters, internal boundary indices,
reduce and disjoin. Full original RDS returns preserve original names, metadata,
seqinfo, missing selections and intentional metadata loss. No paper pipeline or
complete GenomicRanges package is claimed. The existing Java scalar geometry
component remains separate and adds no additional native family credit.

Three declared Windows x86_64 reference profiles were exercised: GenomicRanges
1.44.0 with portable R4.1.0, and 1.58.0/1.60.0 with R4.6.1 and explicit private
dependency closures. Each checks 160 direct source function bodies/formals.
The legacy profile retains R4.1.1 binary-build warnings under its actual R4.1.0
engine. Read every capture's original conditions and WARNINGS.md.

Measured checks:

- 783 focused normal Java-build tests, zero failures/skips.
- 168 packaged cases across the three profiles: 159 full RDS comparisons and
  nine expected original error class/message comparisons.
- 27 actual replays; 186 full result/replay RDS comparisons in total. Operation
  warning classes/messages also match the direct original calls.
- 21 resealed invalid-capture refusals, nine dependency/source/capacity/timeout
  controls and seven intake/replay controls. Four public catalog refusal controls
  reject wrong/missing producers and changed plans/previews without changing
  canonical metadata. Old unsafe JARs were read for rejection, never executed.
- Exact UTC/nanosecond creation-time checks on all 195 saved RUN/REPLAY captures;
  no older or unclosed recorded candidate. Original archive bytes remain intact.
- 18 source/model/transaction/time-audit tests on each of Windows and Linux.
  Both hosts independently audit all 250 XML sources and produce identical census
  bytes. Linux checks are source/metadata checks, not Linux R operation claims.
- Installed verification on both hosts; the complete 851-entry catalog manifest
  has no mismatches, missing entries or extras. Styled preview and installed
  desktop/mobile checks cover all seven roles, counts and prominent remedies.
  Isolated browser fixtures use their own debugging endpoint for graceful close
  and a unique-profile process check; final matching process counts are zero.

Actual source-built producer, not a release asset:

`D:/projects/JLinAlg/build/gh-1/build/genomicranges-handover-1/handover-8/project-build/cli/omicsmatic-0.4.0.jar`

197,926,716 bytes; SHA-256
`75394cca138d9dd5f9cfccd211f00aaf28924584d153370b5c6de66fba8de90c`.

See [workflow guide and copyable examples](GENOMICRANGES_WORKFLOWS.md),
[qualification packet](genomicranges-qualification/QUALIFICATION.json) and
[catalog validation](omics-2023-2026/genomicranges-catalog-validation.json).
Saved integrity checks do not parse RDS, refit original models, authenticate
resealed captures or certify biological results.

## Source roles and review accounting

Five reviews are new. P094/P242 retain their previous review identities,
summaries and full-paragraph anchors; only their availability/scope notes change.
The other 1,659 prior review records, all 2,716 unrelated observations and all
445 other inventory rows remain unchanged. No observation is added or removed.

| Paper | Actual reported role | Important limit |
| --- | --- | --- |
| P013 | Array-midpoint GRanges, promoters and internal chromosome-boundary indices for motif windows | Edition and complete arguments unknown; sequence extraction and motif scanning remain separate. |
| P032 | GenomicRanges1.44.0 nearest-TSS gene annotation of CUT&RUN regions under R4.1.0 | TSS preparation, tie and strand settings unstated. |
| P051 | GenomicRanges1.58.0 with Signac for a common peak set before Seurat merging | No GenomicRanges function named; reduce/disjoin are not attributed to this paper. |
| P094 | subsetByOverlaps/findOverlaps for accessible/linked peaks and SNP/regulatory datasets | Preserve both reported editions and environment differences below. Existing summary preserved. |
| P137 | distanceToNearest for recurrent SCAA-loss peaks and predicted TF binding sites, distance <1000bp | Orientation, strand and ties unknown; not enumeration of every pair within1kb. |
| P158 | distanceToNearest from ChIP peaks to promoters defined as2kb upstream/200bp downstream | That definition does not establish a promoters() call; edition/settings unknown. |
| P242 | SNP-to-DHS interval mapping after separate CrossMap conversion | Functions/edition unstated; complete mask construction and FAME fitting remain separate. Existing summary preserved. |

A broader search found P039's unattributed subsetByOverlaps mention. It does not
establish another GenomicRanges provider observation and receives no extra credit.

A read-only audit of seven linked P094 author notebooks at commit
`ea1a5fcc81495d889c3d4252f62d658ce53df0b2` found recorded Part3 software-session
output for **Linux R4.4.2, GenomicRanges1.58.0 and IRanges2.40.0**. Other notebook
metadata differs. These records do not authenticate an executed paper commit or
establish a compatible GenomicRanges1.60.0 environment. The qualified Windows
reference profiles differ. The linked code also contains both single-base SNPs
and proxy ranges ending at POS+1; supplied endpoints are preserved. No author code
was executed, ported or redistributed. Published auxiliary evidence contains
pointers, hashes and interpreted metadata; original notebooks stay private.

## Verification and preserved contexts

For the current source checkout:

```powershell
python -B tools/literature/genomicranges_workflow_qualification/verify_checkpoint.py
python -B tools/literature/close_genomicranges_catalog.py --verify --out build/genomicranges-verify-NEW
```

The first reader uses `genomicranges-qualification/STATUS-v2.json`, including an
independent exact creation-time audit. Original STATUS.json, the original reader
snapshot and unchanged evidence ZIP are retained. They have a preceding source
context; do not refresh their hashes to hide a source change. The private original
project mirror remains under `packet-preparation-1/project`.

Catalog verification can additionally accept `--cache`, `--author-code-cache`
and the unchanged `--jar` to recheck available sources and native runtime bytes.
Plan/inspect/apply require those prerequisites; they create NEW outputs and do not
overwrite a previous admission. Source/model baselines refer to Git commit
`3c9cc8a71b8adc8eb9d1ff22f915e7bb1dfa999f`. Older process/SKAT/source-bound readers
must be used with their declared historical source contexts, not relabeled as
current checks. No old result or qualified hash has been silently repaired.

Earlier failed/superseded controls, matrices and test-harness attempts remain
retained. This includes the first Linux check's Windows `.git` path mismatch,
the UI selector for reported versions, missing preview assets, browser relaunch
observation limits and the short-lived browser-launcher check. The final styled
checks require loaded shared CSS, graceful owned CDP closure and zero uniquely
tagged fixture processes. The saved validation record distinguishes these phases.

## Next work and private transfer

Continue the full 250-paper objective: review the remaining1,057 uses, screen for
missed providers and implement remaining high-impact gaps. Current candidates:
**FUSION and g:Profiler, seven papers each**, followed by Squidpy, smartpca and
MR-PRESSO, six each. Recheck actual roles, overlap, licensing and feasibility
before selecting scope. Prefer practical native Java methods, with tested exact
upstream adapters for substantial software. Evaluate newest iDEP for its remaining
one-paper gap; do not assume uncertain legacy iDEP.96 licensing applies.

Preserve all73 worktrees, indexes, dirty files, ignored runtimes, primary XML/
PDF/XLSX, old JARs and raw/null/failed/partial/superseded/invalidated evidence.
**A GitHub clone is insufficient for machine handover.** Transfer privately before
retiring the machine, preserving junction/link targets explicitly. No private
transfer or cleanup is claimed here.

Current complete development evidence:

`D:/projects/JLinAlg/build/gh-1/build/genomicranges-qualification-1/`

Private GenomicRanges libraries:

`D:/projects/JLinAlg/build/gr-2/build/genomicranges-2/private-profile-install-1/{313,320,321}/library/`

Portable R4.1.0 and original source archives remain in
`D:/projects/JLinAlg/build/gr-1/build/genomicranges-1/`; older unsafe development
captures remain in `D:/projects/JLinAlg/build/gr-2/build/genomicranges-2/`.
Root `D:/projects/JLinAlg/build/*20261122_117.*` contains current orchestration
helpers; transfer them with their evidence. Review source/producer paths before
resuming old scripts. Do not run pre-fix producers. Publication receipts are in
`D:/projects/JLinAlg/build/git-publish/20261122-genomicranges-117/`.

**Historical export remains blocked:** automatic approval review previously
rejected nine broad unpublished snapshot branches and `codex/release-v0.3.1`
because they may contain sensitive material. Specific export approval remains
pending. Do not upload the private incremental bundle or merge those payloads
as a workaround. Private root manuscript/rendering outputs remain local.

**SKIP3:** dependent analysis skipped. Supply the named exact permitted input,
Rscript or private library, downloading/installing privately where allowed. If
installation is prohibited, retain the skip and explain the affected analysis
and exact prerequisite. **FAIL2:** no complete admitted result. Preserve the
attempt and logs, fix the named cause and retry NEW output. Never silently repair
IDs, NA/NaN/Inf, source behavior or unknown settings. Cell Ranger stays excluded;
MEME/FIMO research-use and accepted GATK3.8 terms remain unchanged. Both unfinished
literature reports remain excluded from Pages. This is not a release or completion
of the full implementation goal.
