# Toolbox handover — 18 November 2026

**The 250-paper cohort is collected. The implementation and individual role
review goal remains unfinished. Next: finish METAL catalog admission, then
continue high-impact analytical gaps.** This is a handover checkpoint, with no
new family credit, canonical catalog mutation, release or website deployment.

This document supersedes the current-state instructions in the earlier
[METAL handover](HANDOVER_2026-11-18_METAL.md). Its weighted-Z and runtime-adapter
to-do items were subsequently completed. Preserve that document and all prior
failed, skipped, draft and superseded evidence as historical records.

## Current state

The authoritative worktree is `D:/projects/JLinAlg/build/high-impact-stats-1`,
branch `codex/metal-catalog-20261118`. Its predecessor is
`9006411abd568646dee25957a451380f9872ac8b`, already published to GitHub main.
The target repository is [robbyjo/OmicsMatic](https://github.com/robbyjo/OmicsMatic).

| Canonical catalog measure | Current value |
|---|---:|
| Papers, frozen 2023-10-23 through 2026-10-23 | 250 |
| Nature / Nature Genetics / AJHG | 71 / 104 / 75 |
| Named families / effective registry definitions | 446 / 485 |
| Paper/tool observations | 2,695 |
| New scoped integrations | 129: 24 native Java + 105 upstream |
| Integrations including three existing adapters | 132 |
| Individually reviewed positive uses | 1,266 |
| Observations awaiting individual review | 1,429 |
| Missing / method-overlap definitions | 259 / 46 |

Counts describe bounded scopes, not full-tool parity, whole-paper reproduction
or biological validation. The latest public `0.4.0` release predates these
additions. Its version string does not identify the matching source-build JAR.

Completed since the older handover:

- Native sample-size-weighted signed-Z SDK qualification was published in
  `34cd3f612dc14666175750434670624d50c3a7f2`; see the
  [checkpoint](WEIGHTED_Z_CHECKPOINT_2026-11-18.md).
- The original METAL 2020-05-05 typed workflow, private setup helper, examples,
  run/verify/replay paths and individual source-role preview were published in
  `9006411abd568646dee25957a451380f9872ac8b`; see its
  [checkpoint](METAL_WORKFLOW_CHECKPOINT_2026-11-18.md),
  [guide](../vignettes/metal-captured-workflow.md) and
  [qualification](metal-workflow-development/README.md).
- This checkpoint preserves the new executable developer audit
  `tools/literature/metal_catalog/primary.py` and its
  [complete census](metal-catalog-handover/PRIMARY-CENSUS.json): all 250 primary
  XML identities, 24 individual METAL roles and 47 named paragraphs passed.
  It audits sources only; it has no catalog plan/apply operation.

## Immediate work: admit METAL without changing historical evidence

No METAL catalog transition has been written or applied. There are no existing
METAL entries in the 1,266 reviewed-use records. The saved role preview and the
fresh census supply the source decisions; they are not admission by themselves.

1. Add `tools/literature/metal_catalog/model.py`, `catalog.py` and meaningful
   transition/admission tests. Inherit the effective 485-definition registry
   from `ldak_catalog.model.registry()`. It returns a list. Use the exact
   cumulative Git predecessor `9006411...`; preserve the existing LDAK and
   historical verifiers and their source guards.
2. Produce a reviewable, deterministic plan over the eight canonical metadata
   files: the five files `papers.json`, `tool-inventory.json`, `summary.json`,
   `README.md`, `WARNINGS.md` under `docs/literature/omics-2023-2026/`;
   `tools/literature/reviewed-uses.json`; and the two literature HTML pages in
   `site/`. Change only METAL's implementation/role metadata and the current
   notices/counts. Preserve unrelated records, order and every existing review.
3. Bind every new review to the saved XML identity and selected paragraph
   hashes. P077 must move from paragraph 42 to the specific method at 52,
   including all anchor fields and supporting evidence. Keep the paper cohort,
   registry population and observation membership unchanged.
4. Apply only after plan/invariant checks pass. Expected resulting totals are
   **130 new integrations = 24 native + 106 upstream; 133 including existing
   adapters; 1,290 reviewed uses; 1,405 awaiting review; 259 missing and 45
   method-overlap**. These are expected next totals, not current totals.
5. Add a separate admission evidence folder, proof, role coverage and verifier
   source archive. Preserve the exact eight predecessor files in a ZIP. Bind
   current qualification and unchanged scientific source to the actual Git
   predecessor; do not simulate old source with an overlay or refresh old
   hashes. Extend the strict reversible review-JSON delta chain through
   `catalog_source_integrity`; never use it to replace scientific code.
6. Refresh the current data manifest with the inherited manifest helper and
   audit for missing/extra/mismatched entries. Add negative controls for altered
   anchors/roles/editions, unrelated-record changes and false count credit.
   Verify the retained predecessor and the new transition, with explicit scope.
   The old catalog verifier expects the predecessor state and will need to be
   checked against that retained state after admission, not weakened.
7. Add a beginner-facing integration guide, prominent warnings/recovery and a
   new checkpoint; update `HANDOVER_CURRENT.md`. Verify the changed HTML locally,
   while keeping both pages withheld from Pages. Commit and push a qualified
   checkpoint to main without forcing or creating a release.

Important source decisions are already recorded individually in
`metal_workflows/reviews.py`, the frozen preview and the fresh census:

- P076 uses METAL for comparative benchmark data preparation. P212 includes
  pooled allele-frequency support; neither should be reinterpreted as a generic
  new GWAS result merely to simplify catalog wording.
- P123's overlap correction is Lin–Sullivan in RE2C; do not attribute it to
  METAL's overlap option. P120 specifies no additional genomic control.
- P114 is a gene-level CNV use: do not invent SNP alleles. P203's exact scheme
  remains unspecified. Unknown author settings and editions remain unknown.
- Seven records explicitly name historical 2011 editions: P076, P088, P105,
  P114, P123, P126 and P243. The qualified Linux 2020 profile does not reproduce
  those author binaries. P243 explicitly names Windows. A shared 2020 edition
  label also does not establish an identical author executable or command.

## Work after METAL admission

Prioritize analytical impact, reuse across papers and actual missing behavior.
Inspect existing Java methods and qualified components before adding duplicates.
Current broad families still marked method-overlap include PLINK (52 papers),
Seurat (49), coloc (27), PLINK 2 (25), Enrichr and Scanpy (22 each), GSEA and
SuSiE (19 each), GCTA (17), and edgeR and limma (16 each). Frequency is a starting
point; a family status does not mean all of its components are absent.
Use [the priority record](IMPLEMENTATION_PRIORITY.md) with the current inventory.
Do not treat proprietary GUI software's frequency as a reason to invent parity.

LDSC munging/allele QC, liability conversion and distinct wrapper methods remain
separate scopes. Existing LDSC components do not establish full Genomic SEM,
gsMap, CELLECT or Covariate-LDSC implementations. Relatedness methods and
inverse-variance meta-analysis already exist. Complete the remaining individual
source-role reviews alongside implementation. No defensible completion ETA has
been established for the full remaining scope.

## Fresh handover checks and repeat commands

The [checks packet](metal-catalog-handover/CHECKS.json) records:

- The current catalog verifier passed all 250 XML identities, 797 manifest
  entries and 2,424 qualification-packet payloads, with no manifest differences.
- METAL's source/evidence verifier passed 2,398 public payloads and 24 source
  decisions, rehashed all 813 private payloads, and ran the frozen Java reader
  against all 28 complete bundles. These retain 37 original execution stages.
  Original METAL itself was not re-executed; the historical Windows/Linux JUnit
  suites were not rerun for this source-audit/documentation checkpoint.
- The fresh primary census exactly matched the earlier successful census. An
  altered XML identity was rejected. All eight canonical metadata files remain
  byte-identical to the predecessor. The first new CLI wrapper attempt had a
  list/dictionary indexing error, corrected before the successful retry;
  [its failure is retained](metal-catalog-handover/HISTORICAL-PRIMARY-CLI-FAILURE.json).
- All other 20 worktrees and 33 protected artifacts matched the previous
  publication snapshot. The large LDAK physical store was not rehashed here.

From the authoritative worktree, use Python 3.11+ and **new** output directories:

```powershell
Set-Location D:/projects/JLinAlg/build/high-impact-stats-1
python -B tools/literature/metal_catalog/primary.py --cache D:/projects/JLinAlg/build/literature-review/xml --out build/resume-metal-primary-1
python -B tools/literature/ldak_catalog/catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/resume-catalog-before-metal-1
python -B tools/literature/metal_workflows/verify.py --out build/resume-metal-workflow-1 --closed-evidence build/metal-1/adapter-evidence-4 --verify-bundles --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe"
```

The catalog command above applies to this unchanged canonical predecessor.
The public-only METAL audit can omit `--closed-evidence`, `--verify-bundles` and
`--java`; it cannot establish private evidence closure. No command above
executes the original METAL binary or reproduces a biological analysis.

## Warnings and required actions

**SKIP3: dependent analysis was skipped.** Supply the named permitted runtime,
reference or platform, then retry into a new output path; otherwise leave the
analysis skipped and prominently state the cause. Download/install is already
authorized where allowed. **FAIL2: no complete result is admitted.** Keep inputs,
partials and diagnostics, correct the stated cause and retry into a new path.
Require the workflow completion marker and successful admission; an original
zero exit alone is insufficient. Never silently repair IDs, NaN/Inf, P values,
failed models or missing contributors.

**Original METAL source, archive, binary, notices and author examples remain
private because redistribution is restricted.** Public qualification packets
contain selected views, not standalone admitted execution bundles. Transfer
the named private stores separately for a machine handover where permitted.

Original METAL's overlap/frequency control produced Freq 1.2573 from all input
frequencies 0.3; preserve that source behavior without clipping or certifying
its uncertainty. Custom declared sample size is printed to six significant
digits. Declared N, reported Weight and overlap-adjusted N are distinct.
Post-meta GC reloads printed data and preserves the primary heterogeneity and
frequency fields. Original log-P output is base 10; the native SDK uses natural
logs and has different single-cohort conventions. Do not equate these scopes.

Cell Ranger remains excluded. Earlier accepted FIMO research/nonprofit and
GATK 3.8 academic noncommercial terms persist. Legacy iDEP.96 licensing is
unresolved; newer iDEP work is separate. Do not repeat settled permission
questions or redistribute restricted software.

Both literature HTML pages remain withheld by `.github/workflows/pages.yml`.
Full warnings-page mobile layout is not qualified. No release is authorized
or produced by this checkpoint. The full goal remains active and unfinished.

## Retain these local paths

- Qualified METAL reader:
  `D:/projects/JLinAlg/build/high-impact-stats-1/build/metal-1/adapter-evidence-4/readers/omicsmatic-0.4.0.jar`,
  194,151,916 bytes; SHA-256
  `3a2ccf9a80cf168b958dc6f16b1fc33dcdce8778a34921ffa671de28b905e833`.
- Complete METAL evidence: the parent `adapter-evidence-4/` directory, all 813
  indexed payloads and `INDEX.json` (SHA-256
  `d20b6899447d3ec639ce03b68515ec40ce3d1e4bdbb8e3f6f22010c424ba5a91`).
- Private original intake and binary: `build/metal-1/upstream-1/` and
  `build/metal-1/build-original-1/native-build/bin/metal` within the authoritative
  worktree. Binary SHA-256:
  `dc07f5b3718845c0e177a5bcd7c0f46f94b9a39b3f082e9f458fb2a8b7695a98`.
  Linux runtime distribution: `JLinAlg-Omics-SAIGE-20261026`.
- All earlier METAL readers, build attempts, closures and primary intake remain
  under `build/metal-1/`; current public qualification and historical snapshots
  remain under `docs/literature/metal-workflow-development/`.
- Primary XML cache: `D:/projects/JLinAlg/build/literature-review/xml/`.
- Full LDAK store:
  `D:/projects/JLinAlg/build/snakemake-workflow-1/build/ldak/qualification-evidence-1/`.
- Fresh handover audits:
  `D:/projects/JLinAlg/build/high-impact-stats-1/build/handover-20261118-catalog-1/`.
- Publication/preservation receipts:
  `D:/projects/JLinAlg/build/git-publish/20261118-handover-catalog-22/`.

The original `D:/projects/JLinAlg` and other worktrees contain unchanged older
dirty overlays. Do not broadly stage, reset or clean them or overwrite newer
integrated files with those versions. The current checkpoint's source, notes
and public audit evidence are committed; ignored build caches, private large
stores and restricted runtimes remain local. Preserve the workspace for a
machine handover. No scientific analysis was left intentionally running.
