# Native FUMA GENE2FUNC gene-set module

**Checkpoint scope:** this guide retains its earlier candidate and validation. The current combined candidate adds optional raw gene-score annotations and typed orchestration; read [FUMA_TYPED_WORKFLOW.md](FUMA_TYPED_WORKFLOW.md), [FUMA_GENE_SCORE_ANNOTATIONS.md](FUMA_GENE_SCORE_ANNOTATIONS.md) and `fuma-workflow-validation.json`. Broader FUMA remains missing.

**Checkpoint scope:** this guide retains its earlier validated candidate and formats. The current combined command also supports supplied chromatin interactions; read [FUMA_CHROMATIN_WORKFLOWS.md](FUMA_CHROMATIN_WORKFLOWS.md) and `fuma-chromatin-validation.json`. Broader FUMA remains missing.

**Available in a development candidate; broader FUMA remains missing.**
`fuma-tools --method gene-set` implements the local supplied-annotation/GMT
overrepresentation method in Java. It needs no Python, FUMA account, network,
database installation or upstream runtime. Use the exact candidate and checksum
in [validation metadata](fuma-native-validation.json). This is not a public release.

Run the complete [synthetic example](../../../examples/fuma/README.md). Replace
`JAR` below with the candidate path, and choose a new output directory:

```text
java -jar JAR fuma-tools --method gene-set --annotations examples/fuma/genes.tsv --query examples/fuma/selected.txt --biotypes all --gmt-manifest examples/fuma/collections.tsv --annotation-id synthetic-20-gene-fixture-v1 --exclude-mhc false --correction fdr_tsbky --adjusted-cutoff 0.05 --min-overlap 2 --duplicates error --out build/fuma-example-NEW
```

Supply plain UTF-8 annotation TSV with `ensembl_gene_id`, `entrezID`,
`external_gene_name`, `gene_biotype`, `chromosome_name`, `start_position` and
`end_position`. Rows require complete positive Entrez IDs, nonempty identifiers
and valid coordinates. Query/background files are headerless, one ID per line.
Choose exactly one of `--background FILE` and `--biotypes TYPE:TYPE` (or `all`).
The manifest has one `path` column and lists every intended `.gmt` file, with paths
relative to the manifest; duplicate category basenames fail. GMT links/descriptions
are retained, except that the original backend blanks the link for singleton rows.

ID handling follows the inspected backend: uppercase input IDs, then choose one
global namespace for each list in symbol > Ensembl > Entrez order. Mixed lists
can therefore lose otherwise valid IDs. Repeated annotation mappings preserve
display-symbol order. Biotype filtering precedes MHC filtering; MHC exclusion
uses the first MOG and COL11A2 start positions in that filtered annotation, with
strict interval overlap. Missing/reversed anchors fail. At least two distinct
selected Entrez IDs must remain in the final background.

Every valid member-containing row belongs to its GMT file's correction family,
including zero-hit and zero-background rows. Short/no-member rows are ignored.
Correction precedes minimum-overlap and strict adjusted-P < cutoff filters.
Available corrections: bonferroni, sidak, holm-sidak, holm, simes-hochberg, hommel,
fdr_bh, fdr_by, fdr_tsbh and fdr_tsbky. Two-stage alpha is always 0.05, independent
of the display cutoff, including the original zero/all first-stage branches.
Do not substitute generic enrichment's filtered correction family for this method.

`--duplicates error` rejects repeated background memberships. Explicit
`original-count` preserves the backend's duplicate contribution to GMT set size,
while query overlaps remain unique. It warns and records every duplicate count;
an effective set larger than the background fails. Equal-P rows use stable GMT
row order; NumPy's original quicksort does not guarantee the same tie order.

## Warnings and required recovery

Read `WARNINGS.md`, `analysis-status.json`, `session.json`, `id-mapping.tsv` and
`all-tests.tsv` in every run. `GS.txt` contains retained results in the original
eight-column schema; `all-tests.tsv` preserves every tested row, duplicate counts
and natural-log raw/adjusted probabilities. `universe.tsv` and `selected.tsv`
record the exact denominator and query. The manifest retains input/output hashes.

Exit 0 means completed with review warnings. A hidden or empty retained table is
not an analysis failure: inspect the complete tested family and protocol. Exit 2
means failure; partial tables are quarantined and cannot be used as completed
results. Correct the named option, input, annotation, MHC anchor or resource limit
and rerun into a new writable output directory. Existing outputs are preserved.
Missing/NA Entrez annotation rows fail explicitly: create and retain an appropriate
derived resource with documented cleaning and universe, rather than assuming its
denominator equals the legacy backend's missing-value behavior.

Default caps are 256 MiB per input, one million gene rows, 100000 GMT terms,
five million memberships and 100 million quadratic Hommel operations. The help
lists explicit overrides. Select an appropriate declared correction or increase
the work budget when Hommel exceeds its cap; no scientific option is retuned.

Rounded zero probabilities remain accompanied by finite log values. Java's stable
arithmetic intentionally differs from some legacy SciPy/Sidak rounding; a strict
threshold on a rounding boundary needs review. Read the measured
[numerical findings](FUMA_NUMERICAL_FINDINGS.md).

The combined development candidate also provides a [supplied-reference locus workflow](FUMA_LOCUS_WORKFLOWS.md), with its own source findings and validation. The earlier gene-set candidate and its 192-test evidence remain retained; the combined candidate passes 224 focused/packaged tests.

## Validation and remaining work

The gene-set checkpoint candidate passed 192 focused tests in 18 suites, Java 17 compilation,
Javadoc and executable packaging; all 192 tests also passed against only the exact
packaged production classes on each of Java 21 and 25. Thirty-five unchanged
original GeneSet.py runs were compared with 70 packaged native workflows.
Counts, selected/background IDs, displayed genes/links and retained decisions
agree. Exact combinatorics independently checked all 1024 tested native rows;
legacy numeric discrepancies are retained separately. Forty-five probability
vectors across ten methods gave 450 correction families and 13920 compared fields
on Java 21/25, including independent 400-digit Sidak checks. Seven additional
public-example/Unicode workflows passed on Windows Java 21/25 and Linux Java 21.

The inspected original source is pinned to commit
`bdfbf06b5dd24037ec3c7497d773fad3c2656150` in the
[official FUMA repository](https://github.com/vufuma/FUMA-webapp).
All 598 source files match its Git tree and archive. Only private synthetic
reference execution used legacy Python 2.7.15, NumPy 1.16.6, pandas 0.24.2,
SciPy 1.2.1, statsmodels 0.10.0 and joblib 0.14.1. Those end-of-life dependencies
are not production prerequisites; unmodified upstream source remains private
under its own license. No upstream code was copied into the Java implementation.

Broader FUMA remains listed as missing: SNP2GENE clumping/locus construction,
variant/eQTL/chromatin annotation, full MAGMA orchestration, expression/DEG,
DrugBank and cell-type workflows still need implementation and their reference
resources. No historical FUMA release, hosted service or full paper is reproduced
by this module. If those future workflows require software/data whose installation
is unavailable or forbidden, skip the dependent analysis and state the dependency
and required recovery prominently; do not substitute this gene-set module.
