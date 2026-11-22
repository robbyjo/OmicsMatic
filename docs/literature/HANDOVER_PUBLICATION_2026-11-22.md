# Published checkpoint and work to resume

The qualified GenomicRanges implementation, catalog update, verification tools
and evidence packets are on GitHub `main` at
[`19c4612a90dc7fda3be9d297ecaa98d0709e41b1`](https://github.com/robbyjo/OmicsMatic/commit/19c4612a90dc7fda3be9d297ecaa98d0709e41b1),
also retained on `codex/genomicranges-qualified-20261122`. All 98 current-task
publication files were checked byte for byte against a narrow Git archive;
the public commit/tree, main reference and 12 representative source/document
files were independently read back. All 72 other worktree owners were preserved.
The publication checkout is `D:/projects/JLinAlg/build/gh-1/`; the shared root
checkout has its own dirty work and must not be reset to main.

The current scoped total is **149 families: 27 native Java and 122 upstream**,
or 152 including three pre-existing adapters. The 250-paper collection is
complete. **1,666 tool-use observations are reviewed and 1,057 remain pending.**
Neither every tool nor every paper workflow is implemented. The new provider
adds one upstream family, five reviews and two existing-review scope updates.
Its checks include 783 focused Java tests, 168 original-runtime cases across
three profiles and 27 actual replays. See the
[qualification checkpoint](GENOMICRANGES_CHECKPOINT_2026-11-22.md) for exact
scope, sources, hashes, commands and limitations.

## Work to resume

1. Prioritize the actual remaining high-impact gaps: FUSION and g:Profiler
   appear in seven papers each; Squidpy, smartpca and MR-PRESSO appear in six
   each. Recheck source roles, existing method overlap, licenses and feasible
   scope before assigning implementation credit. Prefer practical native Java
   methods and tested upstream adapters for substantial software.
2. Finish the remaining 1,057 observation reviews and screen the entire frozen
   corpus for missed providers. Keep paper-specific roles, versions, unknown
   settings and comparator-only uses distinct. Evaluate the newest iDEP for
   its remaining gap; do not assume terms for legacy iDEP.96.
3. Repair the **33 pre-existing website citation-panel omissions** listed in
   [the saved failure record](genomicranges-checkpoint-20261122/website-check.json)
   and rerun `checkWebsite` in a separately reviewed change. That check currently
   fails; the scoped implementation/catalog/UI checks pass. Preserve prior
   qualified source contexts instead of refreshing historical hashes.
4. Transfer private evidence and runtime prerequisites before retiring this
   machine. A GitHub clone alone cannot reproduce the local runtime checks.

## Prominent operational restrictions

**Use a current source build for external-worker execution.** A superseded
test may have terminated an unrelated older process after following a reused
Windows parent PID. Its application and recovery remain unknown. The corrected
source uses birth-checked immediate-child discovery and identity-bound handles.
Retain pre-fix JARs for historical evidence and known offline verification;
do not execute their external-worker paths. Existing public release assets
were not replaced or requalified. JDK 21 compilation is the qualified route;
a separate pre-existing JDK 25 compilation warning remains.

**Private and historical material remains local.** Automatic approval review
rejected export of nine broad unpublished snapshot branches
`codex/handover-20261122-ownerNN` (NN = 01, 04, 29, 30, 31, 41, 43, 45, 59)
and `codex/release-v0.3.1` because they may contain sensitive material.
Specific export approval remains pending. Do not upload the private incremental
bundle or merge those payloads as a workaround. A separate optional broad
verifier-closure archive was also rejected; the permitted narrow 98-file
publication check completed instead. No private runtime, original paper XML,
author notebook, manuscript/rendering output or historical bundle was exported
in this checkpoint.

Preserve all 73 worktrees, their indexes and dirty files; ignored runtimes;
primary inputs; and raw, null, failed, partial, superseded and invalidated
evidence. Transfer privately with junction/link targets accounted for:

- Current complete development evidence:
  `D:/projects/JLinAlg/build/gh-1/build/genomicranges-qualification-1/`.
- Actual qualified source-built producer:
  `D:/projects/JLinAlg/build/gh-1/build/genomicranges-handover-1/handover-8/project-build/cli/omicsmatic-0.4.0.jar`.
  SHA-256: `75394cca138d9dd5f9cfccd211f00aaf28924584d153370b5c6de66fba8de90c`.
- Private libraries:
  `D:/projects/JLinAlg/build/gr-2/build/genomicranges-2/private-profile-install-1/{313,320,321}/library/`.
- Portable R 4.1.0 and upstream source archives:
  `D:/projects/JLinAlg/build/gr-1/build/genomicranges-1/`.
- Publication receipts:
  `D:/projects/JLinAlg/build/git-publish/20261122-genomicranges-117/`.
- Current orchestration helpers: `D:/projects/JLinAlg/build/*20261122_117.*`.
  Review each producer/source context before rerunning historical scripts.

**SKIP3 means the dependent analysis was skipped:** supply the exact named
permitted input or runtime, installing privately where allowed; otherwise retain
the skip with the affected analysis and prerequisite stated. **FAIL2 means no
complete result was admitted:** retain logs and failed output, fix the named
cause and retry into a new output directory. Do not silently repair identifiers,
NA/NaN/Inf, original behavior or unknown settings. Cell Ranger remains excluded.
The confirmed MEME/FIMO research-use and accepted GATK 3.8 terms still apply.
Both unfinished literature reports remain excluded from Pages. This checkpoint
creates no release and does not finish the full toolbox goal.

## Hosted website publication

The implementation commit's original
[Pages run](https://github.com/robbyjo/OmicsMatic/actions/runs/45045169439)
failed to find its just-uploaded artifact. One retry successfully uploaded again
but then found two artifacts with the default name. Both attempts and artifacts
were retained. These match the two failure modes reported in the upstream
[artifact lookup issue](https://github.com/actions/deploy-pages/issues/451).

The follow-up Pages workflow separates build/upload and deployment into dependent
jobs and uses the same explicit artifact name, including run ID and attempt, in
both actions. This follows the separate-job pattern in GitHub's starter workflows
and prevents reruns from selecting duplicate artifact names. Deployment receives
the completed build's saved name, including when deployment alone is rerun.
Website content,
permissions and exclusion of the two unfinished literature reports are unchanged.
The corrected [fresh workflow run](https://github.com/robbyjo/OmicsMatic/actions/runs/45046642105)
**passed both build and deployment**, with exactly one artifact carrying the
expected run/attempt name. Public readback verified the workflow commit and
four changed publication files. Live checks verified the home page, CSS and
JavaScript against staged source bytes and both excluded reports returned
**HTTP 404**. See the [final verification receipt](genomicranges-checkpoint-20261122/pages-public-verification.json).
This hosted deployment success is separate from the outstanding local
`checkWebsite` failure described above. The later receipt-only documentation
commit changes no published website bytes and requires no new deployment.
