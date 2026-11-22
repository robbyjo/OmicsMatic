# Supplied FUMA gene-score annotations

The native mapping command can append **supplied annotations** to mapped gene
rows. It copies raw values by exact gene ID. It does not calculate pLI, constraint,
pathogenicity, or other scores. Broader FUMA remains missing.

The current development candidate is
`build/literature-review/fuma-workflow-candidate-2/jlinalg-0.3.7.jar`, SHA-256
`e590c788855e80a8e2161a3b5115a705bb91389b522cfddedaddea4080bc1151`.
Read [fuma-workflow-validation.json](fuma-workflow-validation.json) for evidence
and the [typed workflow guide](FUMA_TYPED_WORKFLOW.md) for a runnable example.

Add these string-valued options to an existing mapping carrier:

```json
{
  "gene-scores": "path/to/gene_scores.tsv",
  "gene-score-id": "reviewed-resource-release-and-assembly",
  "score-duplicates": "error",
  "score-missing": "retain",
  "max-score-cells": "1000000",
  "max-score-columns": "256"
}
```

The unquoted UTF-8 TSV needs at least five distinct nonempty headers and an exact
`ensg` key column. The key may be anywhere. Positions 1–4 are metadata;
positions 5 onward are copied payload, following the inspected original stage.
Payload headers become `score.<source-name>`. A key in the payload is also copied.
The column bound counts payload columns, excluding the four metadata columns.
The cell bound counts target rows times payload columns, including missing fills.
Existing byte/row limits also apply.

**Required action:** verify the resource assembly, release, gene namespace,
column definitions and units. IDs match case and version exactly. No case folding,
version stripping, trimming, numerical parsing, recalibration or ranking occurs.
Raw categorical strings, high precision decimals, subnormals, empty cells and
literal `NA` remain supplied text. Missing annotations are not zero and do not
imply absence of association.

Choose both admission policies explicitly:

- `score-duplicates=error` rejects duplicate valid resource IDs, including unused
  genes. `first` chooses the first data row, matching R's order-dependent `match`.
  Every ignored duplicate remains in the resource ledger.
- `score-missing=error` rejects a target with no exact match. `retain` appends a
  correctly shaped all-`NA` row. Blank, `NA`, and non-token resource keys are
  unmatchable and remain audited.

`gene-score-join.tsv` records target order, gene, source physical line and status.
`gene-score-resource.tsv` records every resource row, its selected/unused/ignored
status, first matching line and occurrence count. Source lines include the header.
`session.json` records the resource identity, payload columns and policies;
manifests retain hashes. Scores also annotate retained chromatin declarations,
but they do not turn a declaration into a supported association or bypass the
explicit empty-result policy. Omitting all score options preserves the existing
mapping result schema. Supplying score policies without a score table is an error.

**Failures exit 2 and withhold completed `genes.tsv`.** Read `WARNINGS.md`,
`session.json`, `analysis-status.json` and quarantined `partial/` audits. Correct
the named schema, identity, duplicate, coverage or budget cause; rerun to a new
directory. Review a bound before increasing it. No score resource is downloaded
or synthesized automatically.

The unmodified original `geneMap.R` ran 20 synthetic protocols: 19 completed and
one four-column resource failed. Seventeen valid schemas were compared to native
joins; missing-key and duplicate-header resources are deliberately rejected even
though R accepts them. R silently fills all missing values when `ensg` is absent.
For one payload column, R loses the declared header and writes a deparsed
expression. R also parses and rounds numerical text: the supplied
`0.123456789012345678901234567890` becomes `0.123456789012346`, and `1e-320`
becomes `9.99988867182683e-321`. Native output preserves both the supplied name
and raw text. These are documented differences, not raw-file equivalence.

The 29-case public fixture includes 17 valid joins and 12 controlled failures.
All 29 passed on Windows Java 21/25 and Linux Java 21. Nine result tables per
valid join agree across the three profiles. Reproduce using Python 3 and Java:

```text
python tools/literature/verify_fuma_gene_scores.py --jar JAR --out build/fuma-score-replay-NEW
```

Missing Java skips with exit 3 and zero completed protocols. Supply permitted
Java using `--java`, then rerun to a new directory. The verifier installs nothing.
The source commit, actual original outputs/errors, independent expected joins and
intentional differences are retained under
`tools/literature/fixtures/fuma-gene-scores/`. Original FUMA source was used only
as a private research reference; no original R code is bundled in production.
