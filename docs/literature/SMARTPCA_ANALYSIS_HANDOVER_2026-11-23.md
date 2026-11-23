# smartpca analysis development handover

**The 250-paper objective remains unfinished.** The new Java outlier,
perturbation-shrinkage and iterative PCA components have original-source
comparisons. The six `SmartpcaWorkflow*` classes are an unfinished, package-private
workflow draft. There is no new smartpca CLI command, sealed RUN/VERIFY/REPLAY,
FST workflow or catalog admission. Do not present compilation as workflow
qualification. This stage grants **zero new tool-family credit and zero reviews**.

Start with [the saved status and evidence](smartpca-analysis-handover/README.md).
Its `STATUS.json` records the exact normal-build outcome, source hashes and
producer identity. The earlier [prepared-component checkpoint](SMARTPCA_NATIVE_DEVELOPMENT.md)
and its verifier remain frozen and unchanged.

The handover build passed **1,877 selected normal-build tests, with no failures,
errors or skips**. All **22,940 predecessor JAR payloads remain byte-identical**.
The exact new JAR also passed both numerical reference test entry points on
Windows Java 21/25 and Linux Java 21; ten implementation classes were checked to
load from that JAR. The two predecessor checkpoint readers still pass. These
checks cover the saved numerical scope, not the unfinished file workflow.

## Current catalog and scope

All **250 papers** are collected: Nature 71, Nature Genetics 104 and AJHG 75,
using the frozen 2023-10-23 through 2026-10-23 window. Do not move the window on
resumption. The admitted catalog still has **153 scoped families: 28 native Java
+125 upstream workflows**, or 156 including three pre-existing adapters.
There are **1,691 reviewed and 1,031 pending uses**, 2,722 positive observations,
446 labels, 485 provider definitions, 258 missing inventory rows and 20
method-overlap rows. Inventory rows are not independent tool counts.

The user prioritizes high-impact tools; practical native Java methods and tested
upstream adapters are both authorized. Download and private installation are
allowed where the operating environment permits. Cell Ranger remains excluded.
MEME/FIMO research/nonprofit and GATK 3.8 academic noncommercial permissions persist.
Assess newest iDEP; do not infer that its license covers legacy iDEP.96.

## What is saved and tested

- `SmartpcaOutliers`: original strict threshold and first qualifying axis,
  all-sample and leave-one-out modes, source variance denominator, bypasses,
  and explicit nonfinite diagnostics. Twenty-three calls to the unchanged
  original `ridoutlier` function were compared on Windows Java 21/25 and Linux
  Java 21.
- `SmartpcaShrinkage`: source `doshrinkp`/`doproj` perturbation shrinkmode,
  complete eigensystem, source normalization and all-individual output scaling.
  This is not autoshrink or fastshrink. One earlier original whole-program case
  is reused; new finite cases are included below. Singular/unidentified
  calculations refuse rather than reuse stale coefficients.
- `SmartpcaAnalysis`: iterative basis removal, permanent marker exclusions,
  ordinary/least-squares projection, source outlier records, shrinkage and
  explicit sample statuses. Sixteen finite whole-program cases match the
  original program's printed precision on all three JVMs. Two additional
  original cases retain undefined/stale-coefficient behavior and are explicitly
  refused by Java. Repeating a comparison on three JVMs is not three new
  original-runtime experiments.
- The six package-private workflow draft classes contain typed options, bounded
  EIGENSTRAT parsing/copying, source/producer identification, owned execution,
  partial diagnostics, coordinate chunks and streaming table comparison.
  **They lack workflow tests and the audit/controller needed for admission.**
  Their names and profile string are development details, not a supported CLI.

The pinned source is EIG v7.2.1, commit
`4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1`; the program reports `16000`.
Those two version identifiers are different. All 159 original source files stay
unchanged. Original binary SHA-256 is
`db36b911ef813604a871bfed6437211a51e0aea721cc364dc122c1121435a37d`.
No original runtime binary is distributed in this handover packet.

**Retained failed attempt:** the first 18-case analysis generator used `hiprec`,
which upstream ignored. Its metadata incorrectly claimed six-decimal coordinate
output. Native comparison failed on all three JVMs, as expected for that bad
reference metadata. Preserve `original-analysis-reference-1`,
`analysis-check-1` and `PRECISION-METADATA-INVALIDATED.json`. The corrected
generator uses the actual upstream key `hiprecision`; the fresh
`original-analysis-reference-2` and `analysis-check-2` pass. Do not overwrite or
silently relabel the first attempt.

## Resume in this order

1. Fetch main with full history into an isolated checkout. On this machine the
   owned checkout is `D:/projects/JLinAlg/build/fu-1`, branch
   `codex/smartpca-analysis-20261123`; inspect status first. The shared project
   root is an old dirty branch. Do not bulk-stage, reset or merge that root.
   Preserve all 19 registered worktrees, other owners' dirty bytes and indexes.
   Find this publication with
   `git log -1 -- docs/literature/SMARTPCA_ANALYSIS_HANDOVER_2026-11-23.md`.

2. Check the saved evidence using Python 3.11+ with assertions enabled:

   ```powershell
   python -B tools/literature/verify_smartpca_analysis_handover.py
   python -B tools/literature/verify_smartpca_component_checkpoint.py
   python -B tools/literature/verify_mrpresso_catalog_checkpoint.py
   ```

   These read saved evidence; they do not rerun original analyses. The new reader
   can optionally check the exact producer with `--jar PATH`. Keep old source
   hashes frozen; a new dispatcher later requires an explicit compatibility
   bridge rather than refreshing historical hashes.

3. Qualify the draft parser/options/tables with real source inputs and adversarial
   cases: literal IDs `NA`/`NaN`/`Inf`, duplicate IDs, unknown populations/options,
   malformed UTF-8, ragged/invalid genotypes, sorted marker metadata, nonautosomal
   rows, capacities and interruption. The draft explicitly refuses `Ignore`
   individuals and single-reference-population shrinkmode. Preserve those
   restrictions until the corresponding source branches are implemented and
   independently tested. Do not silently sort, merge, recode or drop input.

4. Implement `SmartpcaWorkflowAudit` and `SmartpcaToolsCli`. Require fresh output
   roots, complete raw input/request capture, source resources, exact producer
   binding, full namespaces/seals, consistent statuses/warnings and owned-process
   receipts. VERIFY must recompute all scientific results and check every
   coordinate chunk and TSV row without changing the capture. REPLAY must
   recalculate into a disjoint new root and retain portable parent provenance.
   Test resealed false results, IDs, options, extra/missing files/directories,
   cross-OS capture verification, deadlines, interruption and partial failures.
   An existing incomplete/corrupt capture is FAIL2, not a missing-prerequisite skip.
   Floating comparisons must retain exact integer/identity/zero semantics; do
   not add a generic absolute tolerance floor to conceal unresolved variation.

5. Implement and qualify the source-specific FST path with a tested original
   upstream adapter where appropriate. P180 explicitly reports `fstonly:YES`;
   a generic FST statistic is not an equivalent replacement. Retain negative or
   undefined source results, block-jackknife behavior, version uncertainty and
   input restrictions. No FST implementation was added in this stage.

6. Repeat the normal strict Java 17-target build and packaged numerical,
   RUN/VERIFY/REPLAY and integrity checks on Windows Java 21/25 and Linux Java 21.
   Preserve all prior producer payloads unless an explicitly reviewed dispatcher
   bridge is needed. Only after qualification, guard and test catalog admission
   and the six saved role reviews. Do not infer that v6, 7.2.0 or literal program
   13050 behaves identically to selected v7.2.1/program 16000.

7. Continue other high-impact gaps and all **1,031 pending** semantic reviews and
   missing-provider screening across the full cohort. The private candidate
   audit is `build/fu-1/build/high-impact-audit-152/`. Reassess nf-core,
   SCENIC/SCENIC+ and newest iDEP against current providers. P002 has separate
   nf-core 2.4.4 pathogen and 2.3.4 human-ancestry roles; do not lose the latter.
   P153's SCENIC+ benchmark mention and P094's near-name context require semantic
   review, not automatic execution credit. Finish the existing 33 website
   citation-panel errors before claiming full website readiness or deploying
   the unfinished literature reports.

## Warnings and required actions

**SKIP3: the dependent analysis was skipped.** Supply/install the exact permitted
runtime, input or producer named by the diagnostic. If unavailable or installation
is prohibited, retain the skip and clearly report the required action.

**FAIL2: no complete result is admitted.** Preserve raw/original/partial/failed/
undefined/superseded evidence. Fix the named numerical, input, capacity, timeout
or integrity cause and retry in a NEW directory. Require a completion receipt
and passing VERIFY before use. Retain literal identifiers, NA/NaN/Inf and
per-field admission flags. Never substitute a generic method, clamp source
probabilities or silently repair an undefined calculation.

The draft retains finite source shrinkage output for removed outliers under an
explicit status; it is not automatically eligible for inference. Source
nonfinite outlier diagnostics also remain explicit. The future controller must
enforce those policies consistently; draft output generation alone admits nothing.

The existing **33 missing website citation panels** remain open. See the exact
file list and remedies in `mrpresso-catalog-checkpoint/VALIDATION.json`; the first
reported file is `broad-gsea-tools.md`. Do not weaken the check or insert empty
panels. No release or public report deployment is claimed by this handover.

## Private transfer and cleanup

**GitHub is not a full machine backup.** Transfer these paths privately before
retiring this machine; this handover does not claim that transfer occurred:

- All of `D:/projects/JLinAlg/build/fu-1/build/smartpca-1/` and `smartpca-2/`,
  including exact producer JARs, original inputs/outputs, failed attempts,
  build/source contexts and the new normal-build receipts. Preserve root
  helper scripts and `D:/projects/JLinAlg/build/git-publish/`.
- The whole WSL distro `JLinAlg-Omics-SAIGE-20261026`, including the private
  `/var/tmp/omicsmatic-smartpca-reference-20261123-197` dependency prefix,
  `...-198` successful original build and
  `/var/tmp/omicsmatic-smartpca-outlier-reference-20261123-215` direct caller.
  The initial 197 build failure remains evidence; its dependency prefix is
  required by the successful 198 binary. Do not remove either.
- Earlier MR-PRESSO/Squidpy and other runtime/evidence roots, complete primary
  texts in `D:/projects/JLinAlg/build/literature-review/xml/`, all worktrees,
  refs/indexes/dirty overlays and the previously withheld private bundle. Follow
  [the broader handover](HANDOVER_2026-11-23.md) and its transfer links.

**Historical export restriction persists.** Nine
`codex/handover-20261122-ownerNN` branches (NN01,04,29,30,31,41,43,45,59) and
`codex/release-v0.3.1` were previously rejected by automatic approval review
because they may contain sensitive material. They remain local. Review specific
contents and obtain separate export approval before pushing/merging them; do not
include them indirectly in this publication.

No scientific job is intentionally left running. Confirm terminal build and
publication receipts before transfer; never stop unrelated processes. Clean only
verified duplicate temporary files with exact resolved paths and recovery.
Retain original and failed scientific evidence, runtimes and changing work.
The packet's cleanup receipt records any removals and measured space change.
