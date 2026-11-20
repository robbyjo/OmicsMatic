# Toolbox handover, 20 November 2026: start here

**The full 250-paper implementation and individual source-review goal is
unfinished. This file supersedes earlier handover task lists.** The collection
is complete: **250 papers** (71 Nature, 104 Nature Genetics, 75 AJHG) within
the frozen 2023-10-23 through 2026-10-23 window. No extension was needed.

Current admitted totals are **138 new scoped integrations: 25 native Java
and 113 upstream**; 141 include three existing adapters. There are 446 named
families, 485 effective definitions, 2,704 observations, **1,503 reviewed uses
and 1,201 pending**, 259 missing definitions and 37 method-overlap definitions.
A scoped integration is not a claim that an entire package or paper is reproduced.

## What was just finished

The normal source-built JAR now exposes original SuSiE through **`susie-tools`**.
Keep native `susie` separate. A REPLAY safety fix refuses output inside its
original capture, including junction/symlink aliases, before output creation.
Model-fitting code is unchanged. The retained standalone entry point still works.

There are 173 passing checks on each of Windows Java 21/25 and Linux Java 21;
five actual root-route original-model comparisons match with numeric difference
zero, including a refused nonconvergence and an actual replay. Three parent
protection controls pass. The normal JAR changes exactly the dispatcher and
replay-control class; the other 23,458 payloads remain identical. No Linux R
scientific deployment was qualified. See [saved proof and limits](susie-dispatch-checks/README.md).

The command-discovery metadata transition completed plan, inspect, apply and
fresh verify, with an **820-entry clean manifest**. It changes no paper identity,
review record, model role or family count. Historical sources and fingerprints
remain frozen. Current control code has separate qualification; older readers
must run in their producing historical checkouts. The three-edition workflow,
20 source-role reviews, P073/P203 additions and P235 proper-name correction
were already admitted at baseline `05a3b132447c60b93fd0c51401c4e17a79405f30`.
Do not redo those admissions or restart the completed adapters.

## Next owner: work in this order

1. Check `origin/main`, read this file, the [current command guide](../vignettes/susie-tools.md)
   and [source-role guide](SUSIE_CATALOG_INTEGRATION.md). Run the saved checks
   below in NEW output directories. Verify the separate-machine transfer if
   original runtimes or evidence are needed. Use a fresh isolated worktree;
   preserve the dirty original workspace and all historical checkouts.
2. Continue **higher-impact source roles**, using the current inventory before
   choosing a tool. Current method-overlap candidates by paper count include
   GSEA (19), GCTA (17), edgeR and limma (16 each), PEER (15), TwoSampleMR (12),
   QTLtools and GCTA-COJO (9 each). Counts overlap across papers. These names
   already have related code; inspect literal author operations, editions,
   native APIs and upstream development adapters before implementing anything.
   Method overlap does not establish source equivalence. Choose the most useful
   missing operation, qualify it against the actual original package, preserve
   refusals and then admit only the supported scope through a guarded transition.
3. Continue the **1,201 pending individual tool-use reviews**. Read Methods,
   Results and supplements; retain paragraph/source hashes, function/edition
   uncertainty, external-result reuse and hosted-wrapper distinctions. A name
   or path occurrence is not proof of execution. Preserve earlier reviews and
   all frozen paper identities; do not inflate counts from aliases or components.
4. Close useful remaining SuSiE roles only with exact evidence: 0.11.43,
   author inputs/settings/dependencies, PolyFun/coloc/FUSION/isoTWAS/tensorQTL
   hosts, allele/locus/LD/population preparation, lead selection, FINEMAP
   consensus, cross-validation and CLPP/downstream workflows. Linux R fitting
   needs its own deployment qualification. Three reviewed papers reuse external
   results; do not relabel them as new fits. Current standalone fits do not
   establish these broader workflows.
5. Reuse existing **iDEP 2.4.4** headless/Shiny development profiles rather than
   restarting legacy iDEP.96 or asking the same license question. Their remaining
   admission/deployment gaps are in the [preceding detailed handover](HANDOVER_2026-11-20.md).
   Continue other existing native/upstream components when they match the
   literal operation. Prefer practical native Java, tested upstream adapters
   for substantial tools, and higher-impact work over easy low-use additions.

## Warnings and required actions

**SKIP3 means the dependent analysis is skipped.** Name the exact missing
permitted source, edition, package, runtime or input and explain how to supply
it. Download/install is authorized where the operating environment permits;
if installation/access is forbidden, retain the skip. Do not replace an
unavailable exact edition with a nearby one. Cell Ranger remains excluded
by the user. FIMO nonprofit research and GATK 3.8 academic noncommercial use
were accepted; preserve their exact terms and access restrictions.

**FAIL2 means no complete result.** Preserve raw inputs, original model objects,
partials, warnings and errors; fix the named schema, integrity, numeric,
convergence, capacity or process cause and retry NEW output. Never silently
repair identifiers, alleles, thresholds, populations or nonfinite values.
REPLAY output must be outside the parent capture, normally a new sibling.

The published **0.4.0 binary predates recent adapters**; build current source.
No new release is requested. Two historical full-marker verifier mains still
need the exact original 0.3.7 JAR: 164,898,617 bytes, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
Without it, keep those checks skipped. A different published JAR is not a substitute.
Saved hashes show integrity, not biological validity, authentication or a signature.

## Verification commands

From the repository root with Python 3.11+; use NEW output names every time:

```text
python -B tools/literature/verify_susie_dispatch.py --out build/handover-susie-proof-NEW
python -B tools/literature/close_susie_dispatch.py --verify --out build/handover-command-metadata-NEW
```

The first is an R/Git-free saved-evidence readback; it does not refit models.
The second checks the current applied metadata and manifest and requires Git.
Do not run the completed `--apply` again. Older SuSiE readers correctly refuse
the changed current control source; run them in the exact historical checkout
when checking those historical checkpoints. Do not update their saved hashes.
Use the [command guide](../vignettes/susie-tools.md) for normal JAR RUN/VERIFY/REPLAY.

## Exact local state and transfer

Active checkout: `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/plink-relatedness-20261119`. Dirty original workspace:
`D:/projects/JLinAlg`, branch `codex/analysis-pipeline`. **Preserve all 25
registered worktrees**; do not reset, clean, delete or bulk-merge historical
overlays over main. Historical dirty variants are already preserved in Git
and `docs/literature/handover-20261119/`.

Exact previous catalog checkout: `D:/projects/JLinAlg/build/sd-1` at
`05a3b132447c60b93fd0c51401c4e17a79405f30`. Earlier unchanged owner checkouts:
`D:/projects/JLinAlg/build/sc-susie-1` at
`0e20bb5017303382a78e041ff8810139232e8336`,
`D:/projects/JLinAlg/build/sc-enrichr-1` at
`bd26330459c35c5ec6e5be904d349280bb353f6e`, and
`D:/projects/JLinAlg/build/high-impact-stats-1/build/enrichr-catalog-admission-1/coloc-predecessor-checkout`
at `29486e3f7bf83f534324f21c2d2bd917ad35b134`.

**GitHub contains source/docs and compact qualification packets. Large ignored
builds, installed runtimes and full original evidence require separate permitted
machine transfer. A Git clone alone does not recreate this operating environment.**
Preserve `D:/projects/JLinAlg/build/literature-review` (source caches), all
existing ignored evidence/runtimes, and these active-checkout directories:

- `build/susie-high-impact-1/`: exact R libraries, normal producer builds,
  independent 41-model references, source census and earlier attempts.
- `build/susie-catalog-admission-1/`: all admission revisions, failed/superseded
  notices and UI checks, exact-before metadata and original-owner results.
- `build/susie-dispatch-1/`: both normal builds, first skipped test run,
  supplied-reference run, final `tests-3/`, actual `original-global-2/` fits,
  copied-parent defect reproduction, alias controls, historical readbacks,
  formal metadata transition, portable controls and final UI evidence.
  The Linux final test roots were archived as `tests-3/linux21-test-root.tar`
  before WSL exited; do not assume `/tmp` directories still exist.
- `D:/projects/JLinAlg/build/*susie*20261120*`: private orchestration helpers,
  including the interrupted first compact-packet collection attempt, followed
  by successful bounded collection. No original input or result was changed.
- `D:/projects/JLinAlg/build/git-publish/20261120-susie-dispatch-55/`:
  commit/push receipts, worktree snapshots, protected-artifact pins and actual
  hosted Pages verification. The preceding publication receipt is
  `20261120-susie-catalog-54/` in the same parent directory.

Installed Windows JDK 21/25, R 4.6.1 and the existing WSL distribution are
environment dependencies, not Git assets. The local `.lwjgl/` directory is
an extracted native dependency cache, now ignored; it is preserved locally.
Never upload credentials or install proprietary dependencies to complete a count.
Both literature HTML reports remain excluded from GitHub Pages. A new source
push changing `site/` must be followed through actual Pages deployment, exact
live home comparison and 404 checks for both reports.
