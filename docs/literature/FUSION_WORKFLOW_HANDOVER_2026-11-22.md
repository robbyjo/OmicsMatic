# Toolbox handover: current status and exact work to resume

The 250-paper collection is complete. The implementation/review objective is
**unfinished**. Current canonical totals remain **150 scoped tool families
(27 native Java + 123 upstream), 1,673 reviewed uses and 1,050 pending uses**;
250 papers, 446 labels, 485 provider definitions and 2,723 observations. The frozen
search window is 2023-10-23 through 2026-10-23: 71 Nature, 104 Nature Genetics and
75 AJHG papers. No extension was needed. Counts describe scoped capabilities,
not complete replacements for every upstream tool or reproduced papers.

Qualified main before this handover is
`833864a156d85578241f436e51128f16d0f97cf0`. It includes admitted g:Profiler and the
[native FUSION component](FUSION_NATIVE_COMPONENT_2026-11-22.md): original allele
flags and prepared-model association, 45 focused tests, 6,561 original allele
cases and 136 associations, checked on packaged Windows21/25 and Linux21 JVMs.
The component adds zero FUSION family credit or canonical reviews.

## Saved unfinished work

The [development snapshot](../../tools/development/fusion-workflow-20261122/README.md)
preserves **all 16 exact current draft files**, a restorable patch, four exact
machine-specific orchestration helpers and selected synthetic evidence excerpts.
It is outside the production build. Production `src/` remains byte-identical to
the qualified baseline; **the global `fusion-tools` command is not enabled**.
No partial adapter is presented as supported or added to the catalog. Keep the
snapshot's original source/profile hashes and failed attempts unchanged.

The draft proposes original-R TRAIN, ASSOCIATE and POSTPROCESS with typed
requests, captured inputs, declared runtime fingerprints, owned-process cleanup,
timeouts/capacity bounds, offline VERIFY and captured-input REPLAY. Original
scripts are unchanged, pinned to
[`fusion_twas` 9346c122](https://github.com/gusevlab/fusion_twas/tree/9346c1222bffbb34499fa7a8e23c1b701b55cb05).
The native component and upstream draft are separate scopes. Current source
does not establish the edition/settings used by a historical paper.

The latest two training processes have **terminated**; their RESULT.json exists.
Do not restart them because an earlier terminal tool handle is no longer live.
Neither original input set changed, including the `--rn` path, which uses working
copies because the original script can rewrite the phenotype file.

| Development probe | Observed outcome | Qualification boundary / next action |
| --- | --- | --- |
| Windows association, three synthetic features | Draft run completed | Compile-2 development context; not a packaged, qualified adapter. |
| Windows replay, joint post-processing and LDSC export | Each returned 0 | Retain compile-2 context; newer replay comparison/profile changes need fresh checks. |
| Windows correlation-only post-processing | FAIL2, missing original CORR export | Original ignores `omnibus_corr` without `omnibus`; draft now requires both, corrected path not yet qualified. |
| Linux all-model training | FAIL2; original R exited 1 reading absent `tmp/train.param.txt` in `weights.bslmm` | BLUP is first and uses that function too. GEMMA subprocess root cause unresolved; later models are not established as executed. No complete result admitted. |
| Linux top1 training, estimated h2, two-fold CV | Returned 0, one feature, top1 weights and hsq export | Explicit modern-GCTA startup compatibility enabled. One synthetic development result, not full training qualification. |

Early filename-regex failure, failed original AST walker, failed GCTA discovery
attempts and all superseded compiles are retained. Do not discard or relabel them.
Saved excerpts can be checked without running R:

```powershell
python -B tools/development/fusion-workflow-20261122/verify_snapshot.py
python -B tools/literature/verify_fusion_native_checkpoint.py
python -B tools/literature/verify_fusion_source_checkpoint.py
python -B tools/literature/close_gprofiler_catalog.py --verify --out build/NEW-gprofiler-check
```

These commands check source/saved evidence relationships, not a new scientific
refit. `NEW-gprofiler-check` must be a fresh path. The native checker optionally
takes `--jar` pointing to the exact retained native producer below.

## Work to do, in priority order

1. Resume FUSION in an isolated owner from the saved patch. Inspect the raw
   all-model logs and original GEMMA invocation/output conventions. Debug the
   missing `tmp/train.param.txt` in NEW output. Qualify BLUP, LASSO, top1, ENET and
   BSLMM separately, cross-validation, explicit/estimated h2, covariates,
   residualization and rank normalization. Do not fabricate missing source
   outputs, suppress errors, clip h2 or substitute another model.
2. Close the draft admission/audit gaps **before enabling the command**: typed
   offline canonical-request validation without old-host path resolution;
   complete captured-input and original-export inventories; actual command,
   seed, runtime/package/tool closure checks; sample-squared/kernel and
   weight/LD-squared limits before allocation; full output footprint after
   final manifests/markers and during VERIFY; operation-correct no-result
   bounds; UTF-8 diagnostics. Add prominent GCTA-compatibility and allowed-replay-
   difference warnings. Decide whether inherited loader variables must be
   cleared or explicitly included in runtime evidence. VERIFY is saved-bundle
   consistency, not RDS refitting or authentication.
3. Qualify the explicit modern-GCTA shim with exact startup-positive and
   startup-negative controls and analysis-argument/status forwarding. The
   bundled legacy `gcta_nr_robust` 1.21 segfaulted (139). Modern 1.94.1's no-argument
   status is 1 with its no-analysis diagnostic; the opt-in shim recognizes only
   that diagnostic/banner, retains the original logs/status and leaves actual
   analysis invocations unchanged. Do not silently substitute binaries or call
   this a reproduced paper runtime.
4. Add meaningful contracts/audit/refusal/process tests. Run fresh complete
   ASSOCIATE/COLOC (including SuSiE colocalization), permutation, all POSTPROCESS
   modes, omnibus/correlation, report/plot and no-result cases. Current prediction
   model choices are blup/lasso/top1/enet/bslmm, **not SuSiE prediction**. Test
   missing prerequisites (SKIP3), invalid protocols/results (FAIL2), file and
   matrix bounds, real timeout/owned cleanup, resealed contradictions, immutable
   inputs, physical output/replay separation and unchanged replay parents.
5. Build a NEW normal JDK21/release17 producer. Run focused native regressions
   and workflow tests, compare independent unchanged-original-R outputs and
   execute the actual packaged public command on Windows21/25 and Linux21.
   Record exact R/package/platform scope and new JAR/source identities; compare
   all preceding payloads. Cross-host offline reading is not cross-host R fit
   equivalence. Compile-3 is only standalone development compilation, not a
   normal packaged producer or full adapter qualification.
6. Finish the guarded FUSION source/catalog transition. P112/P120/P131/P181/P183/
   P238 are six software analysis/comparator papers. P108 adds a method-baseline
   description; inspect author code before claiming upstream execution or its
   SuSiE baseline. P212's pending FUSION-tool observation is a study-cohort
   homonym. Preserve extraction provenance and prior reviews; make corrections
   through the source-bound catalog transaction. Do not award credit in advance.
7. Continue higher-impact gaps, including Squidpy, smartpca and MR-PRESSO after
   checking current inventory/source roles. Evaluate newest iDEP and its actual
   license/dependencies rather than assuming legacy iDEP.96 terms. Complete all
   1,050 pending use reviews and scan all 250 papers for missed providers. Keep
   native Java where practical and tested original adapters where appropriate.
8. Repair the 33 existing website citation-panel omissions and rerun the complete
   website check before claiming it passes. Retain the unfinished-report Pages
   exclusions. Publish later scientific/catalog changes with their own checks;
   this handover does not change the deployed site or create a release.

## Private transfer and reproducibility

**GitHub is not a complete machine handover. Transfer ignored evidence/runtimes
privately before retiring this workstation.** Preserve all **74 worktrees**,
their dirty files/indexes, original scientific inputs, null/partial/failed/
superseded outputs, and existing producer JARs. Main checkout
`D:/projects/JLinAlg` has unrelated work; do not reset or stage it wholesale.
The current owner is `D:/projects/JLinAlg/build/gp-1`, branch
`codex/gprofiler-workflow-20261122`. New handover publication receipts are under
`D:/projects/JLinAlg/build/git-publish/20261122-final-handover-123/`.

Transfer `D:/projects/JLinAlg/build/gp-1/build/fusion-1/` in full, especially:

| Retained private location within fusion-1 | Purpose |
| --- | --- |
| normal-build-1/ | Qualified native producer and normal-build source/test receipts. JAR: `project-build/cli/omicsmatic-0.4.0.jar`, 198029624 bytes, SHA-256 `bb25c3a41b2d2464f69deea9218e1742d8a5d1ce0817281deaf6a9d27668d54c`. |
| native-reference-1/, native-reference-2/, packaged-reference-1/ | Failed/current original references and actual packaged native checks. |
| upstream-intake-1/, original-association-1/, intake-1/, source-windows-{1,2}/, source-linux-{1,2}/ | Original source/intake, synthetic data/direct R analyses and 250-paper source context. |
| workflow-compile-{1,2,3}/ | Distinct development source/classes/resources. Use the frozen compile-3 resources with its classes; never mix edited live resources into old evidence. |
| workflow-development-{1,2}/, workflow-post-replay-1/, workflow-linux-training-1/ | Complete successful/failed development captures and launcher/result receipts. Both latest training cases are terminal; `RESULT.json` records exits 2 and 0. |
| runtime-install-1/library/, workflow-windows-library-1/library/ | Private Windows R packages, exact PACKAGES.tsv in each parent. |
| workflow-linux-install-1/library/ | Private Linux added packages; preserve installer logs and PACKAGES.tsv. Existing SAIGE prefix was not changed. |
| binary-intake-1/, binary-probe-1/, gcta-modern-intake-{1,2,3}/ | Official PLINK/GEMMA/GCTA artifacts, failed legacy probe, modern binary and exact intake hashes/URLs. |

Windows Rscript is `C:/Program Files/R/R-4.6.1/bin/Rscript.exe` (R 4.6.1).
Permitted Linux distro is `JLinAlg-Omics-SAIGE-20261026`; its Rscript is
`/opt/jlinalg-saige-runtime-v1/source/.pixi/envs/default/bin/Rscript` (R 4.5.2).
Keep that existing prefix read-only; added packages are in the private owner
library. Linux optparse/Matrix emit retained built-under-R-4.5.3 warnings.
Read exact package versions from the saved PACKAGES.tsv rather than guessing.
Linux draft probes used Java21 and the frozen standalone classpath, not a new
executable JAR. Also transfer original primary XML/PDFs, older tool evidence and
g:Profiler original raw HTTP captures listed in preceding handovers. Raw headers
can contain Set-Cookie values; only explicitly redacted copies belong in public Git.
Handle retained junctions/symlinks as declared links, not blindly recursive copies.

## Prominent warnings and required actions

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** When a required permitted exact runtime,
package, input or tool is absent and cannot be installed, name it, skip dependent
analysis and explain how to provide it. Downloads/private installs are already
authorized where the environment permits them. Cell Ranger remains excluded.
MEME/FIMO research/nonprofit use and GATK3.8 academic noncommercial terms are
already accepted; do not ask for those same permissions again. P052's exact
historical g:Profiler build remains unavailable and skipped before submission.

**FAIL2: NO COMPLETE RESULT.** Preserve raw logs, partial exports and failed/null/
superseded evidence. Admit no result, fix the stated cause and retry NEW output.
Do not silently repair identifiers, NA/NaN/Inf, zero p-values, original omissions
or scientific settings. The saved draft verifier has known gaps listed above;
its development completion marker is not production qualification.

**Process incident:** a superseded test may have terminated an unrelated older
process; its application/recovery remain unknown. Read
[the incident handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md). Use current
birth-checked, identity-bound worker cleanup. Pre-fix external-worker JARs stay
historical and must not be executed; do not restart an unidentified application.

**Historical exports remain local.** Automatic approval review previously
rejected export of `codex/handover-20261122-owner01`, owner04, owner29, owner30,
owner31, owner41, owner43, owner45, owner59 and `codex/release-v0.3.1` because of
possible sensitive material. This handover push does not export or indirectly
merge those branches. A separate content review and specific export approval
are required for them. Preserve them locally/private-transfer them meanwhile.

The prior Pages deployment for `caf2c4f0` was verified; no site bytes change here.
The full goal remains active and unfinished. There is no reliable full-goal ETA
until the pending source/dependency/qualification work is sized.
