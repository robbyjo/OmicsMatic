# TwoSampleMR native-component checkpoint, 21 November 2026

**Eight source-bound Java statistical methods, median points and supplied-draw
median inference are implemented and tested in the normal JAR.** This is a
prepared-data component; full TwoSampleMR workflow/catalog admission remains
unfinished. The counted catalog stays at **143 scoped families, 1,616 reviewed
uses and 1,104 pending** (2,720 observations, 835 manifest entries).

Use the [API guide and warning recovery](TWOSAMPLEMR_NATIVE_COMPONENTS.md),
[runnable example](../../examples/twosamplemr-native/README.md),
[14 paper-role proposals](twosamplemr-source-review-proposal/README.md) and
[portable qualification packet](twosamplemr-native-checkpoint/README.md).
The released 0.4.0 predates the API. The existing generic MR API/commands remain
unchanged; the new API does not claim their rules are stock TwoSampleMR.

The target is advertised 0.7.14, commit c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6.
The original R definitions were executed unchanged for 63 synthetic cases and
630 method calls, including four raised rank-zero errors. Return/error captures,
inputs, warnings, RNG state and median draws are retained. All 638 tests pass
without skips in the normal build and separately per JVM on Windows Java 21/25
and Linux Java 21. All 22,297 earlier JAR payloads are byte-identical. The public
example runs on three JVMs and matches original IVW/median point results.

The first normal build had five exact-fit failures; correcting the rounded
Householder reciprocal and Reference BLAS norm accumulation resolved them with
the original fixtures and tolerances unchanged. Two preflight classpath failures
and two version-witness punctuation failures remain retained with their source
and logs. No raw scientific input or reference statistic was repaired.

Fresh Windows/Linux source audits check all 250 frozen XML/DOI identities, 14
positive software-use roles and sixteen semantic refusals per host. P157/P177
are proposed missing observations. P088 WM is explicitly expanded as weighted
median in Results; P211 reports 0.5.7, with citation 31 kept separate. Preserve
P112's trait-count discrepancy, P150's MAF wording, P211's Steiger threshold and
P224's harmonization-versus-MVMR provider boundary. Detailed function attribution
for the P157/P177 software declarations remains unresolved.

## Exact next work

1. Add tested original-runtime file workflows with explicit RUN/VERIFY/REPLAY,
   full original return/partial/error captures, integrity/input/population checks,
   capacities and owned process supervision. Retain the native methods as an
   explicit engine; do not replace or silently fall back to the legacy API.
2. Qualify reported historical editions 0.4.9, 0.5.6, 0.5.7 and 0.6.8 where their
   exact permitted sources/runtimes can be supplied. Preserve unreported editions
   as unknown. Current 0.7.14 source is not an author-edition substitution.
3. Cover package preparation actually reported by the papers: clump_data,
   harmonise_data and mv_harmonise_data, and sensitivity methods including modes.
   Keep PLINK, LDlinkR, MR-PRESSO, SIMEX, CAUSE, MVMR and TEMR provider boundaries.
   Do not add extra LD filters or change author thresholds/units silently.
4. Review the linked author code/supplementary preparation where permitted to
   resolve P157/P177 function attribution and other missing settings; current
   source intake does not establish paper-executed code commits.
5. Only after workflow qualification, register a global command and perform a
   guarded canonical admission starting from `peer_catalog.model.registry()`.
   Preserve all 1,616 old reviews, existing observations/identities and previous
   source owners. The fourteen current source proposals add zero ledger credit.
6. Continue the remaining individual reviews and higher-impact implementation
   gaps. Retain the separate preprocessCore audit, six inherited descriptive
   registry/inventory differences and pre-existing broad-gsea-tools website
   citation failure recorded in the preceding PEER checkpoint.

## Resume and warnings

Current checkout: `D:/projects/JLinAlg/build/mr-1`, branch
`codex/twosamplemr-20261121`. Private current evidence:
`build/twosamplemr-native-1`. Upstream full source/intake:
`D:/projects/JLinAlg/build/twosamplemr-intake-20261121-1` (241 file pins).
Normal JAR: `build/twosamplemr-native-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`.
Preserve all **39 worktrees**, including the clean PEER owner `build/pr-2` at
ac75e695. Publication receipts:
`D:/projects/JLinAlg/build/git-publish/20261121-twosamplemr-native-83`.

This is the requested handover stopping point. Resume from GitHub `main` in a
fresh checkout or isolated worktree, then follow the ordered work above. The
root checkout and older worktrees contain preserved historical dirty snapshots;
do not bulk-stage them over current main. Their committed ancestors and the
current completed changes are integrated on main; their exact local dirty
states are recorded in the publication preservation receipts.

With Python3.11+ without `-O`, use NEW output:

```powershell
python -B tools/literature/verify_twosamplemr_native_checkpoint.py --out build/NEW-saved-check
python -B tools/literature/check_twosamplemr_sources.py --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/NEW-source-check
```

**NO_COMPLETE_RESULT / FAIL2:** preserve inputs and raw unavailable/nonfinite/
partial results; inspect every kind, warning and original diagnostic. Resolve
the input-count, design, numerical, source/integrity, capacity or process cause
and retry NEW output. Exact-fit warnings retain their values; intentional NA SE
for sign and NA Q for medians must not be imputed. R RNG generation, scalar Wald
numeric(0), nonfinite/missing input and R model-container emulation are outside
this component's contract. Full workflow evidence directories remain next work.

**SKIP3:** if an exact external prerequisite cannot be supplied, name the
dependent analysis, cause and recovery, and skip it. Downloads/private installs
are allowed where the environment permits; missing software alone is not an
installation ban. If installation is prohibited, report the named prerequisite
and skip the affected analysis. Cell Ranger stays excluded.

**Before changing machines, separately transfer ignored exact JARs, full source
archives, primary XML/full paragraphs and complete private evidence with
checksums. GitHub is not a complete machine backup.** Both literature reports
remain off Pages. No release or complete toolbox/author/whole-paper claim is
made; the full goal remains active and unfinished.
