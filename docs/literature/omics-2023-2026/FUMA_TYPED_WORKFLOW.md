# Typed supplied-resource FUMA workflow

The development command connects the existing native locus, mapping and
GENE2FUNC modules, plus the original MAGMA adapter, using explicit local inputs.
It adds audited resource preparation and a stage journal. It does not reconstruct
the hosted FUMA service. **Broader FUMA and the full implementation goal remain
open; 271 tool-level integrations are still missing. Cell Ranger stays excluded.**

Use the candidate and exact proof hashes in
[fuma-workflow-validation.json](fuma-workflow-validation.json). From the repository
root, run the synthetic native example:

```text
java -jar build/literature-review/fuma-workflow-candidate-2/jlinalg-0.3.7.jar fuma-tools --method workflow --workflow-options-file examples/fuma/workflow/options.json --out build/fuma-workflow-example-NEW
```

The example maps two synthetic genes and tests the synthetic set containing both
against four background genes: hypergeometric probability `1/6`. Scores are copied
raw annotations, not biological estimates. The canonical executable and public
releases are unchanged.

## Recipe and resource choices

The UTF-8 JSON object requires numeric `schema_version: 1`, an explicit `build`
identity, and at least one selected stage: `loci`, `mapping`, `gene_set`,
`magma_annotate`, `magma_gene`, `magma_enrichment`. Stage values are existing CLI
options with string values. Unknown root fields, non-string stage options,
stage `out`, recursive options carriers, conflicting generated inputs and
inconsistent declared builds are rejected. The methods are fixed by stage;
annotation chooses `annotate` or `annotate-native`, enrichment chooses `sets`,
`properties` or `gsa`, and gene association requires an explicit model.

All recipe paths resolve relative to its directory. A CLI `--out` resolves
relative to the current working directory and overrides recipe `out`. Output
must be new. On Windows, some Java launchers replace non-ASCII arguments before
the application starts. **For Unicode paths, put `out` and resource paths inside
the UTF-8 JSON and pass an ASCII carrier path on the command line.** This route
passed all 17 native/control protocols on Java 25. A failed launcher argument
conversion is retained as a failure, not a successful Unicode analysis.

Connecting `loci` to `mapping` requires `variant_resources`:

```json
{
  "annotations": "variant-annotations.tsv",
  "candidate_score_source": "annotations",
  "extra_rows": "exclude-with-audit"
}
```

The annotations require `uid,chr,pos,CADD,RDB` as TSV columns. Optional state and
binary tracks pass through unchanged. The completed locus candidate's canonical
UID, chromosome and position must match exactly. `X` is accepted as chromosome
23; no prefix translation, liftover, allele reconstruction or inferred score is
performed. Duplicate resource UIDs are rejected even outside the selected scope.
Choose `candidate_score_source=file` plus `candidate_score_file` to preserve a
separate candidate score source with the same required columns. Candidate
positional filtering and annotation-based eQTL/chromatin filtering remain distinct.

`extra_rows=error` rejects resources outside the completed locus candidate set.
`exclude-with-audit` records their exclusion in `resource-disposition.tsv`.
Supplied consequences and eQTL rows are clipped under the same declared policy;
their upstream significance/alignment is never inferred. Generated
`candidates.tsv` preserves locus GWAS P, locus membership and all independent
rsID memberships from `ld-memberships.tsv`. It does not infer a nearest lead.
Inspect generated candidate, annotation and resource ledgers. Workflow
`max_bytes`/`max_rows` bound preparation reads; each analysis stage keeps its own
documented limits. Increase limits only after reviewing actual resource sizes.

Without a locus stage, mapping uses explicitly supplied existing candidate and
annotation tables. Optional [gene-score annotations](FUMA_GENE_SCORE_ANNOTATIONS.md)
use the same exact-ID policies as standalone mapping.

## Derived gene sets and the MAGMA branch

Selecting `gene_set` requires `gene_set_query_policy=actual-links|external`.
`actual-links` derives gene IDs only when positional, eQTL or chromatin evidence
counts are positive. Retained interaction declarations with zero actual links
are excluded and audited. It reuses GENE2FUNC's actual background, biotype, MHC
and global namespace preparation. An ambiguous switch to the symbol namespace
fails with an ID mapping audit. Fewer than two supported or background-retained
genes skips the dependent test; no genes are added. `external` requires an
explicit query and runs independently. Review `query-disposition.tsv` and
`query-preflight-id-mapping.tsv`, including quarantined copies after failure/skip.

**MAGMA uses separate genome-wide SNP/gene annotation, GWAS and reference inputs.**
Locus-selected candidates are not a valid implicit MAGMA input. Declare build,
ancestry, GWAS mode, sample-size contract, model and the permitted runtime as in
the existing [MAGMA guide](MAGMA_WORKFLOWS.md). A connected gene stage receives
the stable annotation bundle; connected enrichment receives its stable gene
bundle. External prebuilt bundles are also supported when their producing stage
is not selected. Existing bundle hash/identity checks remain enforced.

For example, add `magma_annotate` with `method=annotate-native`, full `genes` and
`snps`; `magma_gene` with `home`, `reference-panel`, `ancestry`, `input-mode=summary`,
full `pvalues`, `model=mean` and a declared seed; and `magma_enrichment` with
`method=sets`, `home`, `sets` and the seed. All values are strings. The workflow
does not inspect or infer whether a supplied GWAS is scientifically genome-wide;
that remains an explicit investigator responsibility. It rejects summary inputs
inside its generated workflow output directory.

**If MAGMA is missing, unsupported or forbidden, the association branch skips.**
Read that stage's `WARNINGS.md` for the exact supported installer/home and recovery.
Install only where permitted, or supply an existing permitted runtime on a
supported host, then rerun to a new directory. No automatic install or retry
occurs inside the analysis. Native annotation and the independent native gene-set
branch can still complete. Cell Ranger is not selected, installed or invoked.

## Completion, warnings and recovery

The root is a stable, new, journaled workspace. Individual analyses keep their
existing atomic publication and record their own staging commands. The workflow
does not rename the root after running stages, so admitted child bundles retain
usable paths. `workflow-journal.json` records every selected stage, dependencies,
status, time and exit code. `logs/` retains separate stdout/stderr per stage.
Final `manifest.json` records source/stage hashes and output hashes; require its
completed status **and** the matching final `analysis-status.json`.

- Exit **0**: all selected stages completed, with explicit review warnings.
- Exit **3**: at least one stage skipped and none failed. Dependent stages name
  the upstream cause; a partially skipped workflow is not complete.
- Exit **2**: a configuration, input, analysis or integrity failure occurred.
  Independent stages can still complete; the root remains failed.

**Required action:** read root and every stage's `WARNINGS.md`, logs, statuses,
resource/ID/evidence ledgers and source hashes. Correct the named cause or supply
permitted resources/software. Rerun to a new directory; existing directories are
never overwritten, resumed or automatically retried. A running/interrupted
journal is not a successful result. Retained independent completed stages require
their own warnings and hashes to be reviewed before separate use.

The combined candidate passed 365 focused tests in 30 suites with no skips,
strict Java 17 compilation, Javadoc and executable packaging. All 365 tests also
passed against exact packaged production classes on Java 21 and 25. Workflow
checks include native/skip/failure branches, raw scores, resource concordance,
background and namespace admission, stable bundle reuse and real MAGMA mean,
top and multi models. The original reference has 202 annotated gene outcomes,
201 tested genes and one empty annotation; no empty gene association is invented.
Earlier mapping/chromatin tables remain unchanged under 224 new portable replays.

Reproduce the native/control matrix with Python 3 and Java:

```text
python tools/literature/verify_fuma_workflow.py --jar JAR --out build/fuma-workflow-replay-NEW
```

Add `--unicode-paths` to exercise Unicode JSON resources/outputs, or add
`--magma-home PERMITTED_HOME` to select three original MAGMA models as well.
`--case NAME` selects one protocol. Missing Java skips with exit 3 and zero
completed protocols. A selected but unavailable original MAGMA runtime is also
reported as skipped, with recovery and no admitted association results.


## Current supplied expression and DEG module

The native resolved-ID expression/DEG subset now has a typed command and parent-workflow stage. Read [FUMA_EXPRESSION_WORKFLOWS.md](FUMA_EXPRESSION_WORKFLOWS.md) and [fuma-expression-validation.json](fuma-expression-validation.json) for its exact development candidate and scope. It selects supplied precomputed matrix values and tests supplied DEG memberships; it does not generate DEGs, normalize expression, cluster heatmaps or perform cell-type MAGMA. Both source gates, separate correction families, multiplicity/outside-background diagnostics, source numerical/dimension behavior and retained failures are explicit. Earlier milestones above retain their historical candidate and count descriptions. Current paper-role intake revalidated all14 FUMA uses against682 reviews and202 supporting pointers without changing any review. Broader FUMA remains missing;268 integrations and the full goal remain open. Cell Ranger stays excluded.
