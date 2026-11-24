# Native stratified LDSC workflow

Run prepared, observed-scale partitioned LDSC through
`org.jlinalg.cli.StratifiedLdscCli`. The command captures exact inputs and retains
joint fits, block deletes, covariance, row weights and optional binary-overlap
summaries. It uses the [validated Java API](stratified-ldsc.md) and needs no
Python, R or external LDSC runtime for analysis.

**Read warnings before interpreting results.** Success requires **exit 0**, a
valid `COMPLETED.json` and `analysis.json` with **`result_admitted: true`**.
`SKIP3` means the dependent analysis was skipped; supply the named permitted
input or complete runtime, or choose a separately qualified method. `FAIL2`
means no complete result was admitted; retain the attempt, read `FAILURE.txt`
and `WARNINGS.md`, correct the named issue and use a **new output directory**.

## Build and run a complete example

Use Java 17 or newer and a JAR built from the current source. The published
0.4.0 binary predates this command. From the repository root:

```powershell
.\gradlew.bat :executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.StratifiedLdscCli --help
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.StratifiedLdscCli --request examples/ldsc-stratified/fixed-overlap.json
```

The [examples](../../examples/ldsc-stratified/README.md) include full synthetic
reference inputs and requests for a fixed intercept, free intercept and
negative-heritability fit. Their relative paths resolve against the request.
Each writes to its own new `build/stratified-ldsc-example-...-1` directory.
Preserve an existing attempt and select a new `out` value before rerunning.
These synthetic models demonstrate numerical behavior; they are not study data.

## Declare the prepared model

Start from one of the example JSON files and supply every key explicitly:

| Key | Required value |
|---|---|
| `schema_version` | `1` |
| `analysis` | `"partitioned-hsq"` |
| `weighting` | `"source-fixed-initial"` |
| `input`, `out` | Prepared UTF-8 TSV and a new output directory. |
| `annotations` | Unique ordered column labels, distinct from reserved input headers. |
| `annotation_counts` | Matching positive reference annotation counts. These are not the regression row count. |
| `n_blocks` | Explicit consecutive-row block count, 2 through 10,000. |
| `intercept` | `"free"` or a finite number. |
| `overlap` | Binary-intersection TSV path, or `null`. |
| `reference_universe_variants` | Positive unique-universe count with overlap, otherwise `null`. |
| `data_provenance` | Source and QC/reference population of the prepared summaries. |
| `row_order_declaration` | How the intended genome order was established. |
| `annotation_provenance` | Annotation definitions, column identities, reference population and count/overlap construction. |

The input header is literal TSV:

```text
variant	chi_square	weight_ld_score	n	A0	A1
```

Replace `A0`, `A1` with the complete declared annotation sequence. Every row
needs a unique literal variant ID, finite chi-square at least zero, finite
weight-LD and annotation LD scores, and positive finite per-SNP N. Signed
annotation LD estimates remain regressors; the source floors only the summed
LD/weight-LD values used to construct weights. BOM presence is recorded.
Whitespace, quoted text and case are literal; no inferred joins, identifier
repair, deduplication, filtering or annotation reordering occurs.

For overlap, use `annotation` followed by the same header labels and one row
per annotation in the same order. Supply integer binary-intersection counts,
with a symmetric matrix, exact annotation-count diagonal and valid bounds for
the declared unique universe. Binary mode requires integer annotation and
universe counts. Do not round continuous annotation products into this mode.
With continuous-annotation components, set both overlap fields to `null` and
establish any additional interpretation/testing model separately.

## Read outputs and retained partials

| Output | Meaning |
|---|---|
| `source-request.json`, `source-input.tsv`, optional `source-overlap.tsv` | Exact captured bytes, rechecked against the original inputs before completion. |
| `model.json` | Full joint estimates, deletes, covariance, ratio jackknife, fixed weights, boundaries and optional overlap summary. |
| `coefficients.tsv` | Per-annotation coefficients, SEs and coefficient allocations `M_j * tau_j`. |
| `overlap-summary.tsv` | Overlap-adjusted proportions, enrichment, SEs, contrast P/log-P and distinct coefficient Z scores. |
| `row-audit.tsv` | Every supplied variant in order and its fixed initial precision weight. |
| `WARNINGS.md` | Scope, required actions and observed model warnings. |
| `analysis.json`, `manifest.json`, `COMPLETED.json` | Admission state and complete checksum bindings. |
| `component-model.json` | Retained fit component, explicitly marked as insufficient to admit a complete analysis. |

A failed overlap summary or missing Java dependency can leave a valid fit
component and captured inputs. Keep these for diagnosis; require the complete
success conditions before consuming the requested analysis. A late recording
failure retains `unadmitted-analysis.json`, records failure and removes completion.
An existing output directory is preserved in full and the new invocation fails.

## Scope and interpretation

The selected CBIIT 3.0.2 partitioned path uses **fixed initial weights**,
`old_weights=True`. It does not substitute the unpartitioned iterative or
two-step estimator. Caller preparation must explicitly establish raw-data
filters, allele/reference alignment, annotations, LD scores and row order.
Liability conversion and Genomic SEM/gsMap/CELLECT-specific behavior require
their separate methods and inputs.

For overlapping annotations, use the overlap summary and the unique reference
universe. Coefficient allocations and the sum of annotation counts double-count
shared variants. Enrichment P values use the source category-versus-complement
contrast, with a two-sided Student t tail and df equal to the block count.
An all-variants baseline/zero-SE contrast has **NA** P and log-P. One-sided
coefficient tests and outer multiple-testing corrections are separate choices.

Negative coefficients, proportions outside zero to one and out-of-range final
heritability remain visible. Review model/reference adequacy; never clamp them
or remove failed SNPs/annotations/blocks to obtain success. Full and delete
designs must support every parameter. The API's documented rank and numerical
capacity limits, plus a 64 MiB limit per input table, refuse the complete input
rather than truncating it.

The [workflow qualification and warnings](../literature/ldsc-stratified-workflow/README.md)
bind 48 original-core models and 22,596 reference fields at both API and file
boundaries, 17 focused tests on each platform, actual Java process controls and
all three public examples. This method extension adds no second LDSC family
credit and does not establish reproduction of the 42 reviewed papers.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Hilary K. Finucane et al. (2015) — Partitioning heritability by functional annotation using genome-wide association summary statistics](../CITATIONS.md#finucane-sldsc-2015) — [PMID: 26414678](https://pubmed.ncbi.nlm.nih.gov/26414678/) · [PMCID: PMC4626285](https://pmc.ncbi.nlm.nih.gov/articles/PMC4626285/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
