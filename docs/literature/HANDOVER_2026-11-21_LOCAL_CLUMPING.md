# Handover: 21 November 2026, local clumping development

**The full goal is unfinished. Paper collection is complete; individual source
review and implementation remain open. The latest local-clumping Java adapter
is an unfinished draft, excluded from compilation and catalog credit.** Use this
handover first; older checkpoints retain their original evidence and scope.

## Published baseline and current counts

The qualified baseline before this handover is
`51a3115051832b26cb5b8036ca940d806db895cf` on GitHub main. It contains the
[native TwoSampleMR component](TWOSAMPLEMR_NATIVE_CHECKPOINT_2026-11-21.md) and
[original prepared-data workflows](TWOSAMPLEMR_WORKFLOW_CHECKPOINT_2026-11-21.md).
The latter supports standalone RUN/VERIFY/REPLAY for MR, harmonization, four
sensitivity stages and multivariable preparation. Its saved qualification has
667 tests per Windows Java21/25 and Linux Java21, 28 complete direct-original
return/RNG comparisons, seven normal-JAR cases, ten failed/skipped controls,
three replays, seven Linux R-free verifications and three examples. These are
prior qualification results; the handover readers do not rerun those analyses.

| Item | Current catalog, unchanged by this handover |
| --- | --- |
| Papers | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen publication interval | 2023-10-23 through 2026-10-23; no extension needed |
| Named / effective definitions | 446 / 485 |
| Admitted scoped families | 143: 26 native Java +117 upstream |
| Including three existing integrations | 146 |
| Observations | 2,720 |
| Individually reviewed / pending | 1,616 / **1,104** |
| Missing / method-overlap labels | 259 /30 |
| Canonical source files / manifest members | 27 /835 |
| New TwoSampleMR catalog credit | **0** |

Scoped families are bounded supported operations, not whole-tool parity.
Fourteen TwoSampleMR source-role proposals remain proposals. Do not count
modern 0.7.14 qualification as reproduction of reported historical versions.

## New work saved for continuation

- The original R local-clumping prototype uses TwoSampleMR **0.7.14** commit
  `c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6`, ieugwasr **1.2.1** commit
  `ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6`, R **4.6.1** Windows and exact
  **PLINK1.9, 27 Sep 2026**. PLINK SHA256 is
  `efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b`.
- All 152 TwoSampleMR source-defined functions are checked unchanged. Two
  ieugwasr functions temporarily use observed clones whose original bodies
  remain nested intact; both bindings are restored and all seven file-defined
  original function bodies/formals are checked. Do not claim that the entire
  ieugwasr namespace is unchanged during the observation.
- The observer copies raw PLINK association inputs, reports and logs before
  upstream cleanup, retains original arguments/returns/RNG/raised errors and
  exports row decisions plus typed internal tables. JWT promises are not forced
  or captured; the selected path uses a local reference panel.
- Latest **original-controls-5**: 25 actual R prototype cases, **21 successful
  and four expected failures**. Namespace restoration and observer checks pass
  for all 24 cases reaching the script. Earlier **original-controls-3** has 53
  complete original returns/classes/attributes and RNG endpoints identical to
  fresh unmodified source calls; three error controls match error classes,
  static headers and RNG. That independent comparison has **not** been repeated
  for the latest 25-case suite. It excludes the fatal temporary-path case.
- Two incomplete Java draft classes and their resources are saved under
  [development](../../tools/literature/twosamplemr_clump/development/README.md).
  They were moved unchanged out of `src/main` for this handover. They refer to
  three classes that do not exist yet: **TwoSampleMrClumpContracts,
  TwoSampleMrClumpFiles and TwoSampleMrClumpAudit**. There is no completed Java
  RUN/VERIFY/REPLAY, normal-JAR test, cross-host qualification or global command
  for local clumping. Do not expose or count the drafts as implemented.
- The [saved packet](twosamplemr-clump-handover/README.md) retains every current
  prototype attempt, synthetic input, raw failure, comparison, source intake
  and checksums. Earlier malformed-panel, observer-export and setup attempts
  are historical failed evidence, not successful results. The corrected panel
  is a separately generated paired BED/BIM reorder; never repair a user's panel
  silently. The packet contains no installed runtime binaries or private cohort.

## Ordered next actions

1. **Finish original local clumping first.** Review the two drafts against their
   actual generated code before use; then implement Contracts, Files and Audit.
   Require explicit thresholds, population declaration, source-default opt-ins,
   seed and exact runtime paths/hashes. Take before/after snapshots of all four
   inputs as one set. Validate SNP-major BED dimensions and BIM/FAM structure
   without silently sorting, subsetting, deduplicating or changing identifiers.
2. Audit typed batch/group/PLINK returns, raw copied logs and reports, retained
   input-row ordering and duplicates, singleton bypass and empty selections.
   Distinguish source-parsed keys from raw SNP tokens. Do not replace original
   LD computation with Pearson correlation or assume the existing native
   `ld-clump` command has source-equivalent semantics. Its existing haplotype
   likelihood implementation remains a separate compatible engine.
3. Repeat direct-original comparison for all latest cases, including numeric
   identifiers, formatting-created ties and empty input. Add meaningful Java
   semantic/tampering/capacity/path controls, real normal-JAR RUN/VERIFY/REPLAY,
   owned parent/child timeout cleanup, examples and host-specific qualification.
   Preserve the preceding 22,320 JAR payloads and old frozen verifier sources.
4. Qualify reported TwoSampleMR **0.4.9, 0.5.6, 0.5.7 and 0.6.8** in isolated
   libraries. Resolve optional/dependency profiles and source attribution,
   especially **P157/P177**. P224's separate MVMR and P177's TEMR are not
   interchangeable TwoSampleMR estimators. Keep unreported versions unknown.
5. Register the global command only after qualification, then make a guarded
   catalog transition from `peer_catalog.model.registry()`. Preserve all 1,616
   existing reviews, 27 canonical source owners and existing identities. Grant
   no extra family credit to aliases. Continue the 1,104 pending individual
   reviews and prioritize the remaining high-impact analysis gaps.
6. Retain the separate preprocessCore audit and six inherited descriptive-field
   differences. The full website gate has a known pre-existing missing-citation
   failure in `broad-gsea-tools.md`. Both literature HTML reports remain off
   Pages. No release or website change is part of this handover.

## Warnings and required recovery

**Do not run the Java drafts.** Complete the three missing classes and the
qualification above before promotion into `src/main`. Saved packet verification
establishes retained bytes and declared prototype outcomes; it does not rerun
R, Java or PLINK and does not prove whole-tool or biological validity.

**Windows R4.6.1 fails before the script when its temporary directory contains
a space.** The retained `space path` control demonstrates this. For this profile,
use a new capture output with a space-free owned temporary path; retain the
failed directory. Do not silently rename scientific inputs or claim arbitrary
output-path support. Input and runtime path handling still needs Java validation.

**Preserve upstream edge behavior.** Singleton groups bypass PLINK and may
retain a variant absent from the reference or above the threshold. A valid
empty selection is a completed selection, not an MR estimate. PLINK rejects
`r2=1`, `kb=0` and unsupported chromosome codes in retained controls. R's table
type inference can change numeric-looking SNP tokens; inspect the raw and
parsed keys rather than repairing leading zeros. R seed does not control
PLINK's wall-clock seed. Source defaults supply no extra thread/memory flags;
declare that policy instead of silently injecting resource flags.

**Missing prerequisites: SKIP3 with recovery and dependent analysis skipped.**
Use permitted private installs where possible. Absence does not mean installation
is prohibited. When installation or execution is actually disallowed, say which
tool is unavailable and how the user can supply it. Invalid inputs, integrity,
process, numerical or capacity failures are **FAIL2: NO COMPLETE RESULT**;
retain originals, raw errors and partial artifacts. Optional `mr.raps` remains
unqualified/missing in the existing private profile. Linux original R execution
is unqualified; Linux saved verification is a different claim.

**GitHub is not a machine backup.** Preserve all **41 existing worktrees**, their
unrelated dirty edits and all ignored raw/scientific/failed evidence. Transfer
the exact installed runtimes, producer JARs, full primary text and complete
private evidence separately with SHA256 checksums before leaving the machine.
No ignored runtime binaries, user libraries, credentials or unrelated cohort
outputs are uploaded by this handover. Historical local worktree heads may be
older than main; do not reset them or regenerate the catalog from those heads.

The [historical worktree snapshot packet](handover-2026-11-21-worktrees/README.md)
saves the 40 predecessor worktree states, including **2,177 Git-visible changed
or untracked files**: 1,828 reference exact baseline Git blobs and 349 retain
their distinct raw bytes in the delta archive. This includes older catalog/code
variants and exact line endings. They are retained history, not current build
inputs. The current worktree's new drafts and synthetic evidence are in the
separate clumping packet. Together these save the Git-visible handover changes;
ignored machine/scientific assets still require the separate transfer above.

## Exact local continuation paths

| Purpose | Path on this Windows machine |
| --- | --- |
| Main project / historical dirty owner | `D:/projects/JLinAlg` |
| Current isolated worktree | `D:/projects/JLinAlg/build/mr-3` |
| Current branch | `codex/twosamplemr-clump-20261121` |
| Complete new prototype evidence | `D:/projects/JLinAlg/build/mr-3/build/twosamplemr-clump-1` |
| Qualified preceding workflow owner/evidence | `D:/projects/JLinAlg/build/mr-2` / `build/twosamplemr-workflow-1` |
| Private R library, read-only to continuation | `D:/projects/JLinAlg/build/twosamplemr-runtime-intake-20261121-1/library` |
| ieugwasr source intake | `D:/projects/JLinAlg/build/twosamplemr-clump-intake-20261121-1` |
| Exact PLINK executable | `D:/projects/JLinAlg/build/omics-plink-official/plink1/plink.exe` |
| System Rscript | `C:/Program Files/R/R-4.6.1/bin/Rscript.exe` |
| Last qualified producer JAR | `D:/projects/JLinAlg/build/mr-2/build/twosamplemr-workflow-1/normal-gradle-4/project-build/cli/omicsmatic-0.4.0.jar` |
| Publication and preservation receipts | `D:/projects/JLinAlg/build/git-publish/20261121-handover-85` |

The last qualified JAR SHA256 is
`e234998589ed2d78effdd08091dbd2c8f50ab21e254a273ae06c24f548fd5aa6`.
The copied development generators use historical absolute paths and fail-on-exist
output directories. Inspect and adapt them in a fresh directory; do not blindly
rerun them over saved evidence or after the draft relocation.
