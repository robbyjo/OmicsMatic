# Handover: historical TwoSampleMR, 21 November 2026

**The full goal is unfinished. The 250-paper corpus is complete; 1,104
paper/tool observations still need individual review. The historical adapter
is saved development with passing tests, not an admitted catalog integration.**

Start here when continuing. The earlier qualified modern workflows and local
clumping remain in [the preceding checkpoint](TWOSAMPLEMR_CLUMP_CHECKPOINT_2026-11-21.md).
The baseline before this handover is main
`00b29b144709d2bcda23f6edabdc69af68818adb`.

## Unchanged catalog

| Item | Current admitted count |
| --- | --- |
| Papers | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen publication interval | 2023-10-23 through 2026-10-23; no extension needed |
| Named / effective definitions | 446 /485 |
| Scoped families | **143: 26 native Java +117 upstream** |
| Including three existing integrations | 146 |
| Observations / reviewed / pending | 2,720 /1,616 /**1,104** |
| Missing / method-overlap definitions | 259 /30 |
| Canonical files / manifest members | 27 /835 |
| New TwoSampleMR catalog credit | **0** |

Fourteen source-role proposals remain proposals. P157/P177 attribution and
unreported versions remain unresolved. Scoped integrations do not mean
whole-tool parity or reproduction of all paper analyses.

## Work saved and evidence boundaries

Five new `TwoSampleMrHistorical*` Java classes and their R worker support
standalone development RUN/VERIFY/REPLAY for FORMAT, HARMONISE, MR,
HARMONISE_AND_MR and MV_HARMONISE. They preserve version-specific defaults,
original complete returns/errors/RNG and source body/formal guards without
rewriting the original namespace. They are compiled but have no global command
registration and add no catalog credit.

| Tagged version | Commit | Guarded functions | Source methods |
| --- | --- | ---: | ---: |
| 0.4.9 | `db55b97adc84c7c0302bf72834c762ab78db3e50` | 146 | 15 |
| 0.5.6 | `8db843c08084ee79476a8804735cc7e40d88d8b7` | 149 | 18 |
| 0.5.7 | `592ebe05538558b330c39ddeda0d11b1313ad819` | 149 | 18 |
| 0.6.8 | `806703f0693cdd1c01cbde1d8ec51b3ab0ae9d14` | 149 | 18 |

The two 0.5.6 tag aliases resolve to the same commit. These are release-tag
identities, **not proof of each paper's execution commit**. R is Windows 4.6.1
with explicitly declared present-day dependencies; historical dependency-era
and Linux original-R execution are not established.

The exact current normal build passes **769 focused tests**: 638 native
statistics, 29 modern workflow, 40 modern clumping and 62 historical tests.
The same 769 pass against the packaged JAR on Windows Java21/25 and Linux
Java21, with zero failures/errors/skips. Production class origins are checked
against that JAR. All **22,335 preceding JAR payloads** remain exact.

The quick-compiled Java pilot has 48 cases: 44 completed captures and four
expected original zero-rank errors. Fresh direct calls in unmodified namespaces
match **120 complete original returns and four error class/messages**, including
classes, attributes and RNG endpoints. Forty of those returns are source
parameter/method getters, not estimates. This comparison predates the final
non-ASCII audit correction and **has not been repeated on normal-JAR analyses**.
The fixture now contains 45 genuine successful captures, including that
correction. Saved verification and packaged tests do not rerun R.

The non-ASCII control exposed a Java audit bug: original 0.4.9 returns phenotype
columns in R's order (`éTrait`, `zTrait`), differing from Java sorting. The
correction preserves that order, checks the exact label set and requires the
same order across beta/P/SE matrices. The rejected control and corrected capture
remain retained. Version 0.4.9 MV preparation has six members and phenotype
columns; later versions have eight members and ID columns/name tables.

FORMAT preserves original coercion, row filtering and flags. Its Java audit
checks structure, intake, populations and retained originals; it does not
independently reimplement the complete upstream formatting algorithm.

## Continue in this order

1. Freeze these sources and use the normal JAR below. Run all 48 pilot cases
   plus the non-ASCII case through that JAR, expecting 45 successes and four
   original errors. Repeat fresh direct-original comparisons with
   `tools/literature/twosamplemr_historical/compare_original.R`, including the
   new non-ASCII case. Preserve all old attempts and source snapshots.
2. Exercise four real replays with parent-file/export checks, actual examples,
   missing/version/source mismatch, capacity and owned parent/child timeout
   controls. Verify representative completed captures on Linux without R.
   The existing unit controls do not substitute for these actual runtime checks.
3. Qualify original **0.6.8 local clumping** separately. Original 0.4.9/0.5.6/
   0.5.7 clump_data has no local bfile/plink arguments: keep its legacy/API
   requirements explicit; do not substitute the modern 0.7.14 implementation.
   Historical API/reference/authentication behavior remains open. Do not upload
   user data to a remote API without scope-specific authorization.
4. Resolve provider/dependency profiles and **P157/P177** source attribution.
   P224's separate MVMR and P177's TEMR are not interchangeable TwoSampleMR
   estimators. Newly downloaded official Europe PMC XML is link-discovery-only;
   retain frozen canonical XML and give no review credit yet.
5. Only after qualification, register the global command and perform guarded
   catalog admission from `peer_catalog.model.registry()`. Preserve all 1,616
   earlier reviews, 27 canonical owners, existing identities and alias rules.
   Then continue the remaining 1,104 reviews and highest-impact missing scopes.
6. Keep both literature reports off Pages. The inherited full website gate
   still has the missing-citation failure in `broad-gsea-tools.md`. No release
   or website change belongs to this handover.

## Prominent warnings and recovery

**0.4.9 has a documented build exception.** Two original full-source installs
failed while parsing old Rd documentation under R4.6.1, including --no-help.
The successful private build omits only `man/`. All other upstream bytes,
including every R file, NAMESPACE, DESCRIPTION, data and inst, are unchanged;
all 146 installed function bodies/formals match original source. It is a
documentation-only package build, not an unmodified complete archive. Preserve
the failed installs and `DOCUMENTATION-ONLY-BUILD.json`. Version 0.4.9 uses
Artistic-2.0; the later selected versions use MIT.

**Historical source is private.** The old startup source contains legacy
upstream OAuth application configuration. Do not redistribute it, dump global
R options, or include it in logs/public packets. Public resources contain
function hashes, method/default metadata and licenses, not the old source bodies.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Name the exact unavailable runtime,
package/profile or input and state how to restore it. Downloads/private installs
are authorized when allowed. Absence alone is not an installation prohibition.
If installation/execution is disallowed, warn prominently, retain the skip and
skip dependent analysis. Cell Ranger remains excluded at the user's request.

**FAIL2: NO COMPLETE RESULT.** Preserve invalid-data/design, numerical,
integrity, process, capacity and partial-result evidence. Fix the named cause
and retry a NEW output directory. Never silently change identifiers, order,
populations, thresholds, versions, estimators or nonfinite values. Modern
R4.6.1 path controls show failure before script execution when the temporary
directory contains a space; the historical path/control qualification remains
open. Optional installed provider packages are not automatically qualified.

Retained failed/superseded work includes the source-tag alias intake stop,
two original 0.4.9 installs, method-JSON shape setup, Java local-name compilation,
reference CRLF mismatch, the non-ASCII audit rejection, normal build 1 (before
that regression), and normal build 2 (fixture update path typo). Only normal
build **3** is the final current build. No scientific result was repaired by
the reference-line-ending or fixture corrections.

**GitHub is not a complete machine backup.** All **43 worktrees** remain local,
including unrelated dirty owners. The 42 preceding states were checked exact
against the last publication; their historical Git-visible changes are already
retained in the [worktree packet](handover-2026-11-21-worktrees/README.md).
New owned sources and compact evidence are saved by this handover. Transfer
ignored exact runtimes, JARs, full primary text, restricted source and complete
private evidence separately with SHA256 checksums before leaving the machine.
Do not reset old worktrees or treat their older catalog variants as current.

## Exact local paths and resume checks

All paths below are on this Windows machine, not repository-relative.

| Purpose | Path |
| --- | --- |
| Historical owner / branch | `D:/projects/JLinAlg/build/mr-5` / `codex/twosamplemr-historical-20261121` |
| Complete new evidence | `D:/projects/JLinAlg/build/mr-5/build/twosamplemr-historical-1` |
| Historical source intake | `D:/projects/JLinAlg/build/twosamplemr-historical-intake-20261121-1` |
| Historical extras and version libraries | `D:/projects/JLinAlg/build/twosamplemr-historical-runtime-20261121-1` |
| Protected preceding library | `D:/projects/JLinAlg/build/twosamplemr-runtime-intake-20261121-1/library` |
| Author link-discovery intake | `D:/projects/JLinAlg/build/twosamplemr-author-intake-20261121-1` |
| Normal producer JAR | `D:/projects/JLinAlg/build/mr-5/build/twosamplemr-historical-1/normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar` |
| Publication receipts | `D:/projects/JLinAlg/build/git-publish/20261121-historical-handover-87` |

Normal JAR SHA256:
`84ed28614a9527bb46312cdf69ea6c84f9e84bb322da3e07aab5dc3f46402df7`.
The three later version libraries are `versions/<version>/library` under the
historical runtime root. The 0.4.9 library is
`versions/0.4.9-documentation-only-attempt-3/library`. Runtime search order is
the selected version library, historical extras `library`, preceding protected
`library`, then system R's base library; user/site libraries are excluded.

Run the [saved reader](../../tools/literature/verify_twosamplemr_historical_handover.py)
with Python3.11+ (without -O) and a NEW output directory:

```text
python -B tools/literature/verify_twosamplemr_historical_handover.py --out build/historical-handover-readback-NEW
```

This checks saved source/catalog identities, archive payloads and declared test
evidence; it runs no Java or R analysis and supplies no new scientific credit.
The [packet](twosamplemr-historical-handover/README.md) records exclusions and
private transfer hashes. Copied development helpers use absolute historical
paths and fail-on-exist outputs; inspect/adapt them in fresh directories rather
than blindly rerunning them. Leave the full goal active and unfinished.
