# Toolbox handover: PLINK relatedness development

**Update:** the [workflow checkpoint](PLINK_RELATEDNESS_CHECKPOINT_2026-11-19.md)
supersedes this draft's pending qualification tasks for its stated scope.
Source-role review and catalog admission remain unfinished. The original
development packet and its source/artifact pins below are retained unchanged.

**Development checkpoint, not a qualified analysis integration. The 250-paper
collection is complete; implementation and individual source review remain
unfinished. Do not award PLINK, PLINK 2 or KING catalog credit from this snapshot.**

Start with [HANDOVER_CURRENT.md](HANDOVER_CURRENT.md). The last published
predecessor is `224e0f915ffabf78e3f7a76e0ca103b39e82206e`; the commit containing
this handover follows it on GitHub main. The authoritative local worktree is
`D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. Preserve the other worktrees and old dirty
overlays; do not broadly stage, reset, clean or merge the original root.

## Current totals and priorities

| Measure | Admitted catalog |
|---|---:|
| Papers | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen collection window | 2023-10-23 through 2026-10-23 |
| Named families / effective definitions | 446 / 485 |
| Paper/tool observations | 2,696 |
| New scoped integrations | 131: 24 native Java + 107 upstream |
| Including three existing adapters | 134 |
| Individually reviewed positive uses / pending | 1,339 / 1,357 |
| Missing / method-overlap definitions | 259 / 44 |

Seurat's [catalog admission](SEURAT_CATALOG_CHECKPOINT_2026-11-19.md) and
50-role crosswalk are complete. Preserve all predecessor/source guards.
Do not redo completed METAL, LDAK, native KING, PLINK QC/pruning/PCA/GLM,
Seurat preprocessing, markers or RNA workflows. Inspect their actual scope.
Continue high-impact analytical gaps first: PLINK (52 observations), coloc
(27), PLINK 2 (25), Enrichr/Scanpy (22 each), GSEA/SuSiE (19 each), GCTA (17)
and edgeR (16). Counts express observations, not distinct missing algorithms.

## Code and evidence saved here

Six new `src/main/java/org/jlinalg/cli/PlinkRelatedness*.java` classes implement
typed RUN/VERIFY/REPLAY for original Windows PLINK 2 KING tables, triangular
float64/float32 matrices and cutoff selection, plus PLINK 1.9 `--genome full`.
Native pair checks reuse `genetics.KingRobustKinship` and
`genetics.PlinkBedDataset`; these older implementations were not modified.
The new setup resources are under
`src/main/resources/org/jlinalg/omics/plink-relatedness-setup/`.
There is no root CLI registration, release or catalog admission.

The [checkpoint packet](plink-relatedness-handover/README.md) contains the exact
build metadata, source pins, both pilot summaries, all final synthetic captures,
synthetic inputs, installer outcome records and the machine-specific development
helpers. It contains no PLINK executables, runtime archives, large JARs or full
paper text. `checkpoint.zip` and every member are pinned in `MANIFEST.json`.
Successful captures can be relocated and verified without executing PLINK.
The portable checker itself is
`tools/literature/verify_plink_relatedness_handover.py`.

The final local candidate is:

```text
D:/projects/JLinAlg/build/high-impact-stats-1/build/plink-relatedness-development-1/build-4/omicsmatic-0.4.0.jar
194474566 bytes
SHA256 3cd3aa35592bd7159709969ea6ae481caba781abc1c142a99d3050eb17fe1197
```

Isolated JDK 21 compilation with `--release 17 -g -Xlint:all` succeeded with
empty stderr. All 23,095 prior parent-JAR members remained byte-identical;
27 class/resource payloads were added. **This is an isolated development build,
not a full Gradle build, release or qualification.** Parent JAR:

```text
build/seurat-workflow-1/qualification-1/omicsmatic-0.4.0.jar
194396019 bytes
SHA256 cca600c37abad95f428a1780291e40d55e423c2327c860cff1c36179d753e29e
```

Both build-1 and the exact build-4 candidate passed the 16 original-program
pilot expectations. On build-4, all 13 successful captures passed actual Java
VERIFY. Three expected FAIL2 attempts preserved their original reports:
nonfinite KING matrix, an unassessable pair in the proposed retained unrelated
set, and original PLINK 2 rejecting cutoff 0.5 (exit 8). No complete usable
result was admitted for those failures. Small synthetic fixtures include
16/17 participants, 129 SNPs, missing/monomorphic calls, a duplicate, unequal
heterozygosity, exact FID/IID tuples, repeated IID across families and a UTF-8 ID.
These pilots do not establish large-cohort performance or general robustness.

Native audits stream bounded pair batches. Each batch projects only the
participating samples into a scratch panel while reading all original SNPs.
This does not change the original program's full cohort or founder frequencies.
IBS/KING counts are independently recomputed; the IBD frequency model and Z
estimates, pedigree inference, PPC thinning and optimal sample selection are
not independently refit. Preserve source PPC constants `0.666666` and
`0.2222222`, source rounding, genuine nonfinite states and unbounded estimates.
The implementation repeatedly scans BED per batch; do not claim scalable
GWAS throughput without measuring it. Limits are logical/observed bounds,
not hard operating-system memory or process containment.

## Resume in this order

1. Read the six new classes and pinned build-4 source. Reproduce the retained
   checkpoint before editing. Use fresh attempt paths throughout. A standard
   project build may include the new classes, but has not been run for this
   tranche; do not substitute its artifact for the exact pinned checkpoint.
2. Add meaningful tests for closed schemas, duplicate JSON keys, invalid
   UTF-8/nonfinite/fractional limits, source/input/output bindings, capacity
   failures and bounded writers. Compare batch sizes 1, 7 and larger against
   the same original counts, coefficients and rows. Preserve the current pilots.
3. Exercise owned-process timeout, descendant cleanup and interruption in
   reality. Inspect retained process records, matching start times, unresolved
   children and output publication failures. There is no qualification of
   these failure paths yet. Keep failed staging directories and their paths.
4. Test genuine REPLAY into fresh output, then show the prior capture is
   byte-identical. Test relocated VERIFY on Linux Java; test Linux RUN and
   missing/corrupt *copies* of the Windows runtime returning SKIP3 before
   dependent analysis. Do not mutate the sealed successful runtime for tests.
5. Test malformed BED header/padding, duplicate exact FID/IID, unsorted BIM,
   all-unreportable inputs, legitimate empty filtered tables and failures at
   declared work/output/time capacities. Do not silently repair inputs.
6. Test resealed corruptions of raw counts/coefficient/pair population/order,
   genome IBS/PPC/rounded IBD relations, binary matrix/axes, selection partition,
   process command/status/profile, warnings, summary and extra files. VERIFY
   must refuse inconsistent captures. Hashes provide integrity, not producer
   authentication against coordinated fabricated data.
7. Finish source review before credit. Local intake checked all 250 XML
   identities and found 92 existing observations (52 PLINK, 25 PLINK 2, 15
   KING), with 22 previously reviewed. Regex role hints are not manual reviews.
   The first 16 of 23 PLINK/2 relatedness candidates were inspected; the full
   77 PLINK/2 and 15 KING role census remains unfinished. Preserve cohort,
   edition, host program, threshold inequality and other-package attribution.
8. Write beginner examples, the final scoped API/workflow guide, source-role
   crosswalk and self-contained qualification/auditor. Retain parent payload
   identities and old scientific guards. Only after passing qualification
   perform cumulative catalog admission and append supported reviews.
9. Continue other high-impact gaps and the remaining 1,357 individual reviews.
   Push qualified tranches to main without force. Keep both literature pages
   withheld from Pages and do not create an unrequested release.

Source-role cautions already found: P084 explicitly uses PLINK 2 for KING;
P123 has different duplicate/HLA thresholds; P175 names PLINK 1.9 IBD without
the exact flags/removal recipe. P071 relatedness belongs to lcMLkin, P108 to
SnpArrays.jl, P116 to KING `--ibdseg`, P176's adjusted GWAS to GEMMA, and P090
uses PLINK for score application with PRS-CS separately. These are intake
notes awaiting the full source-bound crosswalk, not admitted new reviews.

## Warnings and exact recovery

**SKIP3 means the dependent analysis is skipped.** The current admitted runtime
profile is Windows x86-64 only, PLINK 1.9.0 (27 Sep 2026) and PLINK 2.0.0-b.1.2
(24 Oct 2026), from the unchanged existing project lock. It is not the newest
upstream release. RUN never installs software. Export the setup helper to a
new directory, use Python 3.11+ with explicit `--home`, `--archives`, `--report`
and `--allow-install`; add `--allow-download` only where permitted. If exact
software is forbidden/unavailable, retain the warning and skip the analysis.
Do not reseal a modified home or silently substitute another edition.

The first local installer attempt passed both version probes but failed on
Windows directory publication with WinError 5. Its failure report was retained.
A second attempt into **new** runtime/report paths succeeded. Release the
named lock and retry new paths after this error; do not overwrite the failure.
The currently usable sealed home is local `build/plink-relatedness-development-1/runtime-2`.

**FAIL2 means no complete result is admitted.** Read `analysis-status.json`,
`diagnostics.json`, `WARNINGS.md`, worker stdout/stderr and `result.log`.
Retain inputs, raw reports and partials. Fix the named input, numerical,
integrity, original-process or capacity cause, then use new output paths.
Never change NaN/Inf/NA to zero, clip unbounded IBD values, equate PI_HAT and
KING kinship, certify a partition-only cutoff as unrelated, or apply exact
thresholds to rounded reports. Matrix nonfinite output is rejected; a mixed
table may retain explicitly unreportable rows. Read its per-row disposition.

**Historical Seurat warning:** two old full marker-verifier mains remain SKIP3
because their exact historical canonical JAR is missing. Follow the
[Seurat checkpoint](SEURAT_CATALOG_CHECKPOINT_2026-11-19.md) to restore the
named original hash or keep them skipped. Current RNA/native checks and
separately retained marker source/archive/payload checks passed; do not claim
a fresh historical full-verifier success or modify its guard.

## Machine transfer and retained local stores

GitHub contains the current source, handover and compact synthetic evidence.
**It is not a backup of the machine.** For continuation elsewhere, separately
copy, checksum and preserve permitted private stores before removing this host:

- Active `build/plink-relatedness-development-1/`: build attempts 1 through 4,
  both pilots, runtime/archive/setup attempts and preservation baseline.
- Root `build/literature-review/plink-relatedness-runtime/`: original-program
  controls, source intake and previous native qualification, including failed
  and superseded attempts. Do not replace any with this new checkpoint.
- Active `build/plink-high-impact-1/primary-intake-1/` and the frozen 250-paper
  XML/PDF cache. These private text records are not in the public packet.
- Active `build/seurat-workflow-1/qualification-1/` and the earlier scientific
  evidence/runtime stores referenced by the Seurat and older handovers.
- Root `build/git-publish/` receipts and all 21 worktrees, including original
  dirty overlays. Existing receipts cover 47 protected artifacts; this
  handover adds the exact current build and pilot pins without changing them.

Exact machine-specific build/pilot/intake helpers are retained inside the
packet under `helpers/`. They retain original absolute paths. Read them before
running, restore their prerequisite stores, and change only fresh attempt
destinations in a separately saved successor script. Do not rerun an existing
attempt. The local helpers at root `build/` remain in place as well.

User decisions persist: higher-impact tools first; practical native Java and
tested upstream adapters; permitted downloads/installs; prominent skip/failure
and recovery instructions; Cell Ranger excluded; FIMO research/nonprofit and
GATK 3.8 academic noncommercial terms accepted. Do not ask again for these
same decisions. Neither this handover nor its push completes the overall goal.
