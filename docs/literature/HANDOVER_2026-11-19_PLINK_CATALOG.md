# Toolbox handover: finish PLINK-family catalog admission

**The 250-paper collection is complete. Implementation and individual use review
remain unfinished. The immediate next task is cumulative PLINK/PLINK 2/KING
catalog admission, using the existing qualified components and checked source
census. No new scientific implementation was made during this handover.**

The source/workflow baseline is GitHub main commit
[`670833bd9953796810fd3d5f38545ed03897f9b4`](https://github.com/robbyjo/OmicsMatic/commit/670833bd9953796810fd3d5f38545ed03897f9b4).
This handover is a subsequent documentation commit; read its exact revision in
Git history. Use [HANDOVER_CURRENT.md](HANDOVER_CURRENT.md) as the entry point.

## Warnings and required actions

- **GitHub is not a machine backup.** Source, compact published evidence and
  proposal code are committed. Private full paper XML/PDF, author archives,
  restricted runtimes, large JARs, original runs and failed attempts remain in
  the local directories listed below. Transfer those separately, where their
  terms permit, to reproduce fresh source/runtime checks on another machine.
- **SKIP3 means the dependent audit or analysis is skipped.** Restore the exact
  named prerequisite and verify its hash, or leave the skip prominent. Download
  and install where the environment permits. Never replace a missing edition
  with a different release. Cell Ranger is excluded at the user's request.
- **FAIL2 means no complete result is admitted.** Retain raw inputs, partials,
  logs and errors. Correct the specific integrity, source, input, numerical or
  capacity cause and retry in a new output directory. Do not silently repair
  IDs, allele orientation, source inequalities, nonfinite values or statistics.
- **KING 1.4 and legacy PLINK 1.07 CNV operations remain unqualified scopes.**
  The new source census proves their paper use, not their implementation.
  Standalone KING 2.0 has a retained original/native joint-call discrepancy;
  affected analyses must still fail rather than be repaired or relabeled.
- **Two historical full marker-verifier mains remain SKIP3.** Their exact
  canonical JAR is absent: 164,898,617 bytes, SHA-256
  `545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
  Restore those bytes to rerun them, or retain the skip. Other retained-source
  and current-JAR marker checks passed separately; do not claim fresh full
  historical verification. See the [Seurat checkpoint](SEURAT_CATALOG_CHECKPOINT_2026-11-19.md).
- **Both literature HTML reports remain withheld from GitHub Pages.** Preserve
  the exclusions for `literature-review.html` and `literature-warnings.html`.
  Ordinary main-branch site changes can redeploy the rest of the site. This
  handover changes neither site files nor release state.

## Official counts and completed work

The cohort is frozen to **2023-10-23 through 2026-10-23**, the original request
window: 71 Nature, 104 Nature Genetics and 75 AJHG papers. Do not silently roll
the window forward or replace paper identities.

| Measure | Canonical catalog | Tested source proposal only |
|---|---:|---:|
| Papers | 250 | 250 |
| Paper/tool observations | 2,696 | 2,700 |
| Individually reviewed positive uses | 1,339 | 1,413 |
| Awaiting individual review | 1,357 | 1,287 |
| New scoped integrations | 131 | 131 |

The 131 integrations comprise **24 native Java and 107 upstream integrations**;
including three pre-existing adapters gives 134. These are bounded scopes,
not complete reimplementations of every tool or paper. There are 446 observed
named families and 485 effective definitions; 259 families remain missing and
44 have method overlap. PLINK, PLINK 2 and KING still have `method-overlap`
status. Do not count the proposal as an applied catalog or award family credit
before the cumulative evidence and transaction checks pass.

Already completed and pushed:

- [Seurat cumulative catalog admission](SEURAT_CATALOG_CHECKPOINT_2026-11-19.md),
  including its 50-role source crosswalk. Preserve the completed METAL/LDAK and
  earlier predecessor chain. Do not restart completed methods.
- [PLINK relatedness workflows](PLINK_RELATEDNESS_CHECKPOINT_2026-11-19.md):
  pinned original KING tables, triangular matrices and cutoff, plus PLINK 1
  GENOME with declared native pair checks; RUN/VERIFY/REPLAY, bounded resources
  and tested owned-process cleanup. Evidence covers 16 original pilot outcomes,
  54 packaged controls, four 257-sample original profiles, Windows/Linux JVM
  checks and public rebuild/readback. See the [guide](../vignettes/plink-relatedness-workflows.md)
  and [qualification packet](plink-relatedness-development/README.md).
- [PLINK source census](PLINK_SOURCE_CENSUS_2026-11-19.md): all 250 XML/DOI
  identities, 96 positive roles (53 PLINK, 26 PLINK 2, 17 KING), seven contextual
  exclusions and eight proper-name exclusions. Twenty tests, four actual
  missing/changed-source controls, Windows/Linux frozen proposal reproduction
  and Windows fresh-primary reproduction passed.
- The [frozen proposal](plink-source-review/README.md) appends 74 review records,
  preserves all 1,339 prior records and adds P088/PLINK, P130/PLINK 2,
  P212/KING and P229/KING. P114's legacy CNV role is bound to exact paper-linked
  author code read without execution. No author scripts or full text are
  redistributed. [Individual roles and unresolved scopes](plink-source-review/CROSSWALK.md)
  are already recorded; do not repeat the census from scratch.

## Next work, in order

1. **Finish existing-component evidence closure.** Inspect the actual saved
   qualifications for PLINK GLM, QC, pruning, PCA, scoring and LD, standalone
   KING 2.3.2/historical editions, native KING and the new relatedness workflow.
   Reuse existing methods. The latest inspection had just started reading
   `king232-development/VALIDATION.json`, `king-historical-development/VALIDATION.json`
   and `native-king-development/VALIDATION.json`, plus the PLINK verifiers.
   Inspect exact Firth/dosage/X/LD-matrix/ROH/meta/CNV/nonhuman support before
   declaring it implemented or missing. Existing QC already supports mid-P HWE.
2. **Add a separate cumulative admission layer.** Use completed
   `tools/literature/seurat_catalog/{catalog,evidence,model}.py` as the pattern.
   Reuse the frozen `plink_catalog.model.transition` and `primary.audit`.
   Implement new files such as `admission_model.py`, `evidence.py`, `catalog.py`
   and admission tests. Do not edit the eight guarded source-prototype files,
   the shared collector/cohort parser, or frozen qualification artifacts to
   make old guards pass. Their existing hashes intentionally bind earlier
   evidence.
3. **Verify the immutable predecessor correctly.** Bind Seurat's frozen
   before-state, predecessor evidence, qualifications and model transition to
   the actual eight current metadata/report documents. Do not run an older
   final-state verifier against a newer catalog and then weaken its guards.
   Preserve the two historical marker skips and restore P160's exact PDF if a
   fresh earlier source audit requires it. Keep the Methods-only lower parser
   unchanged; it does not independently reconstruct that supplementary use.
4. **Build the full metadata transaction.** Preserve the 1,339-review prefix,
   all unrelated observations, identities, order, old anchors and explicit
   source limitations. Add the four observations and 74 reviews from the
   checked proposal. Qualify only supported family scopes; source existence
   alone earns no implementation credit. Write a new role-coverage file,
   integration crosswalk, scoped registry metadata, proof, reversible
   `*-shared-source-compatibility.json`, both notices and the cumulative
   manifest. Reuse existing compatibility inversion helpers and retain every
   old manifest entry. The proposal currently covers only four core documents;
   the final transaction needs eight documents plus proof/manifest sidecars.
5. **Test plan/inspect/apply/verify and preservation.** Cover missing versus
   changed dependencies, rejected evidence, refused overwrite, partial writes,
   rollback and concurrent edits. Roll back only this transaction's unchanged
   owned bytes; preserve other work. Use new output paths for every attempt.
   Verify both frozen and fresh source-bound readback where prerequisites
   exist, plus meaningful Windows/Linux coverage. Keep unsupported roles and
   source ambiguities visible rather than promising complete author recipes.
6. **Review, then publish the qualified admission.** Check new notice sections
   at desktop/mobile widths with an isolated offline copy. The previous whole
   old-page layout/interactivity remains unqualified. Push to main without
   force after checking the selected snapshot, all worktrees and frozen
   evidence. If `site/**` changes trigger Pages, follow the deployment and
   verify both withheld report URLs remain absent. Do not create a release
   unless requested. Update actual counts only after admission succeeds.
7. **Continue the wider high-impact backlog and remaining reviews.** Current
   frequent candidates include coloc (27 papers), Enrichr/Scanpy (22 each),
   GSEA/SuSiE (19 each), GCTA (17) and edgeR (16). Inspect actual source roles,
   existing methods and licenses before choosing the next scope. Prefer useful
   native Java methods when tractable, with tested upstream adapters where
   appropriate. The overall 250-paper implementation goal is still open.

## Resume and transfer locations

On this machine use **`D:/projects/JLinAlg/build/high-impact-stats-1`**, branch
`codex/plink-relatedness-20261119`. The original `D:/projects/JLinAlg` checkout
is an older dirty worktree. It is not the current implementation. Preserve
all 21 worktrees, their existing overlays, frozen runs and restricted runtimes;
never broadly stage, reset or clean the original root.

From the active worktree, with Python 3.11+ and a new output path:

```powershell
python -B tools/literature/plink_catalog/reproduce_proposal.py --out build/plink-proposal-readback-HANDOVER-NEW
python -B tools/literature/plink_catalog/test_primary.py
python -B tools/literature/plink_catalog/test_model.py
python -B tools/literature/plink_catalog/primary.py `
  --source-cache D:/projects/JLinAlg/build/literature-review/xml `
  --author-archive build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip `
  --out build/plink-primary-audit-HANDOVER-NEW
```

The first command verifies a frozen, portable metadata proposal; it never
applies the catalog or runs scientific software. The last command performs
fresh primary-source checks and requires Git predecessor
`119326ac18b46a9a0c2e60441541f9199003e820`, the private XML cache and exact
author archive. Linux frozen proposal readback passed; fresh auditing on Linux
needs a native Git checkout, rather than assuming Linux Git understands a
Windows worktree's administrative paths.

| Location | Retain/transfer for |
|---|---|
| `D:/projects/JLinAlg/build/literature-review/xml` | Exact 250-paper XML cache; fresh source audits |
| Active worktree `build/plink-high-impact-1/` | Private source intake, original/failed audits, author archive, proposal/readback evidence |
| Active worktree `build/plink-relatedness-development-1/` | Exact runtime, builds, original runs, controls, lifecycle/scale evidence and public rebuild checks |
| Active worktree `build/seurat-workflow-1/` and related Seurat/METAL/LDAK evidence roots recorded in their checkpoints | Parent JARs, supplementary sources and immutable predecessor closure |
| `D:/projects/JLinAlg/build/literature-review/plink-relatedness-runtime/` | Older original reference runs and official source intake |
| `D:/projects/JLinAlg/build/git-publish/20261119-plink-source-review-32/` | Previous publication receipt, 21-worktree baseline and 74 protected artifact pins |
| `D:/projects/JLinAlg/build/git-publish/20261119-toolbox-final-handover-33/` | This handover's preservation, commit, push and public-readback receipts |

The qualified relatedness candidate is
`build/plink-relatedness-development-1/build-6/omicsmatic-0.4.0.jar`.
The [qualification JSON](plink-relatedness-development/QUALIFICATION.json)
binds its expected size of 194,474,943 bytes and SHA-256
`9faafae72255787ffc836207ae422a230ea63c8a1473d2b7e56908e409deb1d9`.
The exact runtime is under `runtime-2/`; do not substitute newer downloads for
the pinned PLINK 1.9.0 (27 Sep 2026) and 2.0.0-b.1.2 (24 Oct 2026) profile.
The public builder can reconstruct the qualified payloads from the exact parent;
container timestamps may differ. Follow the packet's existing rebuild checks.

The P114 author ZIP is 1,867,156 bytes, SHA-256
`4972138b4b934f6be9cee6c230f85e987407d28a483e035b452e78993ecf5783`.
Its [binding](../../tools/literature/plink_catalog/P114-AUTHOR-CODE.json) gives
the exact paper-linked download and selected member hashes. Do not execute
author code merely to redo a source audit.

The user has already authorized permitted downloads/installs, academic
noncommercial GATK 3.8 use, research/nonprofit FIMO use and qualified main-branch
pushes. Do not repeat those permission questions. Keep Cell Ranger excluded
and preserve edition-specific license restrictions. If the environment blocks
an indispensable action, report the actual refusal, affected analysis and exact
user recovery; continue unaffected work.

This handover records an unfinished goal. It awards no family credit and makes
no claim of complete paper reproduction, whole-tool parity or a new release.
The [handover check record](toolbox-handover-2026-11-19/CHECKPOINT.json) records
the checks performed for this documentation snapshot; it is not a new
scientific qualification or cumulative admission proof.
