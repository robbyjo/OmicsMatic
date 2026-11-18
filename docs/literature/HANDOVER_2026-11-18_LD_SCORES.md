# Current literature-toolbox handover — 18 November 2026

**Start here.** This is the latest handover, following the user's explicit
resume after [the earlier paused snapshot](HANDOVER_2026-11-18.md). That older
document remains a historical record. The current request is to document the
remaining work and push all project changes to GitHub. **The overall research
goal remains unfinished.** This checkpoint creates no release.

## What is complete

The frozen cohort contains **250 papers**, dated **2023-10-23 through
2026-10-23**, from Nature, Nature Genetics and AJHG. The current source-bound
catalog contains 446 named tool families, 485 effective definitions and 2,695
paper/tool observations. Of those observations, **1,261 positive uses have
individual reviews and 1,434 still need that review**. There are **128 new
scoped integrations: 24 native Java and 104 upstream adapters**, or 131 when
including three pre-existing adapters. There are 260 missing and 46
method-overlap families. A scoped integration does not establish whole-tool
parity or paper reproduction.

All earlier high-impact work is retained, including the prepared native LDSC
heritability, covariance/correlation and stratified APIs and captured commands.
The earlier handover documents those milestones and broader implementation
priorities. The additional qualified commit is:

| Commit | Result |
|---|---|
| `a39ac83c4720be26cf94a06f0e069770af7d3ff9` | Native streamed hard-call reference LD-score API, original PLINK BED source comparisons, synthetic SDK example and closed evidence bundle. |

Read the [API guide](../vignettes/ldsc-ld-scores.md),
[qualification](ldsc-scores-development/README.md) and
[warnings and required actions](ldsc-scores-development/WARNINGS.md).
The qualification records 37 original-core models and 75,226 reference fields,
nine focused tests on Windows and the same nine on Linux, 37 byte-identical
complete model payloads, and a 42-row comparison against the unchanged original
CLI. The native example matches full-precision core results; the original CLI
prints three decimal places. A 4,099-variant, 512-sample control exercises the
streamed cache. These are synthetic method controls, not biological validation.

The selected source is CBIIT LDSC 3.0.2, commit
`6c673952cee74bd5c57aef1555a03b1c015399a0`. The implementation preserves its
source chunk graph, including initial-clique/fallback behavior and correlations
beyond the nominal coordinate window. It preserves A2 allele orientation,
strict IEEE MAF boundaries, observed-mean missing-call imputation, population-SD
normalization and negative unbiased squared-correlation contributions. Do not
replace these with a superficially similar exact-distance implementation.

## Unfinished work saved with this checkpoint

The [reference-preparation draft](ldsc-reference-preparation-draft/README.md)
contains **all three unfinished source files**, both as an exact-byte archive
and a Git patch based on `a39ac83c`. It includes a standalone captured
`LdscReferenceCli`, a proposed public chunk-graph preflight API, a correction to
annotation/count/overlap memory estimates, and a proposed tenth SDK test.

**The draft has not been compiled, tested or qualified.** It is intentionally
stored as a development artifact, outside executable source. Production SDK
and test source remain exactly at the qualified `a39ac83c` snapshot. No work
was discarded and no old source hash or validation record was refreshed. The
saved patch was applied in an isolated repository and all three resulting
files were checked; the exact-byte archive was also read back and checked.
The draft adds no extra tool-family credit.

Known issues before even an initial successful draft command run:

- `model.json` is written twice with create-new semantics. Keep a single write
  after adding the print-policy fields; otherwise a valid analysis will fail.
- Help text does not yet document the complete 20-key request, including
  `print_variants` and `print_policy`.
- The new graph extraction and memory estimate change SDK source fingerprints.
  They require a new qualification against the preserved original controls.
  Never change historical guards to make an old qualification accept them.

## Next steps, in order

1. Read the draft README and its `DRAFT.json`, then apply the saved patch on a
   fresh `codex/` branch. Correct the known issues and compile before extending
   functionality. Preserve all failed attempts in fresh output directories.
2. Run all ten proposed SDK tests on Windows and Linux, including all 37
   original reference models. Compare every old/new model payload: the
   intentional estimated-byte change must be isolated; scores, filtering,
   counts, overlaps and chunk graphs must remain unchanged.
3. Test the actual packaged command boundary against the complete original
   controls. Cover sample and variant selection, binary/continuous/unpartitioned
   annotations, explicit coordinate units and source chunk behavior. Validate
   retained-SNP-index windows against an unchanged original SNP-window run.
   Print masks must affect printed rows only, while contribution populations,
   annotation counts and overlaps remain unchanged.
4. Test success and failure admission, missing prerequisites, malformed/unknown
   fields, strict UTF-8, reference order, duplicate/missing literal IDs, mixed
   chromosomes, changing inputs/runtime, reused output directories, byte and
   numerical limits, whole-reference refusal and incomplete runtime classes.
   Check prominent error actions, retained partials and absence of completion
   markers on failure. Qualify the memory-bound test before genotype reads.
5. Add complete copyable public examples and beginner documentation; execute
   the examples through the packaged JAR on both platforms. Close a new
   indexed, read-back evidence archive and source-bound verifier. Keep all
   older records and verifiers unchanged, with explicit snapshot relationships.
6. Continue higher-impact repeated analytical needs from
   [IMPLEMENTATION_PRIORITY.md](IMPLEMENTATION_PRIORITY.md): remaining LDSC
   source-specific preparation, munging/allele QC, liability conversion and
   distinct wrapper methods; review the LDAK six-role catalog preview; then
   remaining Monocle, nf-core, cytometry and common-statistical scope. Existing
   components do not supply complete Genomic SEM, gsMap, CELLECT or
   Covariate-LDSC pipelines.
7. Finish the 1,434 unreviewed observations and missing integrations. Preserve
   negative and comparison-only decisions, literal editions and source
   pointers. Distinguish reuse of estimates/reference resources from execution.

## Verification at this handover

The following checks were rerun against the restored qualified source:

- `tools/literature/ldsc_scores/verify.py`: current source bindings and all
  577 indexed LD-score qualification payloads pass.
- `tools/literature/ldsc_stratified/workflow_verify.py`: current workflow and
  retained earlier API evidence pass.
- `tools/literature/ldsc_hsq/catalog.py --verify`: all 250 primary-source XML
  hashes, source-specific admission decisions and the 794-entry current data
  manifest pass; no missing, extra or mismatched entries.

These are retained-evidence/source audits, not fresh executions of upstream
LDSC or the historical Windows/Linux JUnit suites. The draft is excluded from
those qualification claims. Machine-readable results are in
[HANDOVER_CHECKS_2026-11-18.json](HANDOVER_CHECKS_2026-11-18.json).

## Warnings and actions for the next developer

**Use qualified source and a complete current source-build JAR for analysis.**
The previously published 0.4.0 release predates these additions. The native
runtime needs Java 17+; the original reference-development runtime is private
and separate. A new numerical or source change needs fresh qualification.

For captured workflows, require exit 0, a verified `COMPLETED.json` and
`result_admitted: true`. **SKIP3 means dependent analysis was skipped:** supply
the named permitted prerequisite or leave that analysis skipped.
**FAIL2 means no complete result:** retain inputs, partial output and failure
evidence, correct the stated cause and retry with a new output directory.
Preflight filtering and a saved model are not a completed requested analysis.

Download/install permission is already granted within the operating environment.
Warn clearly and skip dependent analysis when a required installation is
unavailable or forbidden. Cell Ranger is excluded by the user. FIMO
educational/research/nonprofit use and the specific GATK 3.8 academic
noncommercial terms were accepted. Legacy iDEP.96 licensing is unresolved;
newer iDEP 2.4.4 work is retained separately. Do not ask again for those already
settled decisions.

Keep `site/literature-review.html` and `site/literature-warnings.html` withheld
from GitHub Pages as configured. Do not publish a release or infer biological,
whole-paper or whole-tool completion. Do not silently repair identifiers,
replace NaN/Inf, force positivity, remove failed rows/blocks/pairs, or substitute
a pseudoinverse to manufacture admission. Retain old, failed, skipped and
superseded scientific evidence and their original hashes.

## Workspace and evidence locations

The authoritative working checkout is
`D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/high-impact-stats-20261117`. The publication target is
[`robbyjo/OmicsMatic` main](https://github.com/robbyjo/OmicsMatic/tree/main).
The original checkout at `D:/projects/JLinAlg` contains an older dirty overlay;
do not reset, clean or broadly stage it. Its current historical files are
preserved rather than replacing newer integrated source with older copies.

Important paths relative to the authoritative checkout:

- `build/ldsc-scores-1/`: original source run, isolated Python runtime including
  bitarray, original CLI, native/package controls, examples and closure.
- `build/ldsc-scores-1/portable-1/jar/omicsmatic-0.4.0.jar`: the qualified API
  development JAR. Its bytes are pinned by the public qualification.
- `build/ldsc-stratified-cli-1/`: earlier captured workflow controls and runtime.
- `build/handover-20261118-current/`: this handover's three fresh source/evidence
  audits. Do not reuse these output directories for subsequent attempts.
- `docs/literature/ldsc-reference-preparation-draft/`: portable unfinished work
  committed to Git, including every original draft source byte.

Original source intake is under the authoritative checkout's
`build/ldsc-high-impact-1/upstream-1/`, including the exact CBIIT commit archive,
extracted tree and intake records. Verify them against the saved closure before
reusing the original reference runtime.
The 250 source XML files are under
`D:/projects/JLinAlg/build/literature-review/xml`.
Publication receipts are under
`D:/projects/JLinAlg/build/git-publish/20261118-handover-16/`.
Git does not include bulky private runtimes, generated JARs or every local
execution log; keep that workspace for a machine handover. Closed public
qualification archives and the exact unfinished source archive are committed.
