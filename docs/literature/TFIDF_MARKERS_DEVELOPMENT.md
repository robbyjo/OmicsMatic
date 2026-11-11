# Native TF-IDF marker statistics

This Java component ranks genes within supplied cell clusters using expression frequency times inverse global expression frequency. It also returns hypergeometric upper-tail probabilities, Benjamini–Hochberg q values and complete gene/cluster statistics. The input supplies the complete axes, clusters and expression values; no clustering or normalization is inferred.

**SoupX integration remains unfinished.** This component covers marker statistics relevant to P008. It does not estimate or remove ambient RNA. SoupX remains missing in the credited catalog until its separate ambient RNA workflows are implemented and validated. The current catalog therefore remains at 76 new scoped integrations, with 255 missing tools. The 250-paper objective remains active.

## Warnings and required actions

**A selected marker need not pass FDR.** The original `quickMarkers` convention chooses the top N TF-IDF scores when at least N genes pass the strict q<FDR comparison. Those selected genes can themselves have q>=FDR. The workflow preserves this behavior and prominently reports the count of selected markers outside FDR. If fewer than N genes pass, it returns every passing gene in score order. `ALL` returns passing genes. Do not interpret every selected row as an FDR discovery.

**Floating-point cutoff boundaries require review.** Native calculations use jdistlib hypergeometric tails and ordinary double BH arithmetic. Original R and exact rational calculations can differ in their last bits. In the two-gene/two-cell control, the exact rational BH q is 1, while both captured R editions and Java return a value just below 1 for the enriched genes. The original strict FDR=1 call consequently returns markers. A second original call uses the actual 17-digit original q as its cutoff. All original/native results and the mathematical distinction are retained. Output flags q values within max(64 ulps, 3e-12 relative) of the selected cutoff; this band is a diagnostic, not a changed selection rule. Check the chosen original runtime if that boundary decision matters.

**Zero-expression genes retain nonfinite diagnostics.** Genes with no above-cutoff expression have Inf IDF and NaN TF-IDF under the formula. Their complete statistic rows remain present. The component refuses invalid or missing expression, duplicate coordinates, undeclared IDs, fewer than two genes/groups and groups with no above-cutoff expression. It does not omit missing values, merge duplicate entries, repair identifiers or substitute zero for nonfinite diagnostics.

**FAIL2 admits no result.** Read stderr, `WARNINGS.md` and `analysis-status.json`; preserve the complete failed attempt. Correct the named input, capacity, heap or storage cause and retry a new output path. If writing fails after the complete statistics table, that table remains under `unadmitted-partial-results` and is not admitted. Invalid request syntax can fail before a bundle exists; stderr still gives recovery instructions. Native execution requires Java and installs nothing. Separate original-reference setup requires a permitted exact R/package profile; unavailable installation reports SKIP3 and dependent original analysis must be skipped.

## Run the example

Use a JAR built from the current source containing `org.jlinalg.cli.TfidfMarkersCli`; released 0.3.7 JARs lack this new class. For the existing validated development workspace, the additive builder preserves the previous development JAR and canonical executable:

```powershell
python tools/literature/build_soupx_markers.py --out build/literature-review/soupx-runtime/marker-build-NEW
java -cp build/literature-review/soupx-runtime/marker-build-NEW/omicsmatic-0.3.7-tfidf-markers-development.jar org.jlinalg.cli.TfidfMarkersCli --request examples/tfidf-markers/request.json
```

The additive builder requires the previously validated normal-fit development artifact identified by its saved proof; it is a development checkpoint helper, not a fresh-clone installer. A normal full source build also compiles these Java classes. Require exit 0 and `result_admitted=true`. The example output is `examples/tfidf-markers/result`; choose a new `out` path before repeating. No R runtime is needed for the example. The Java API is `org.jlinalg.singlecell.TfidfMarkers.calculate`.

The closed JSON requires schema_version=1, mode=MARKERS, genes, cells, counts, out, expression_units, expression_cutoff, top_n and fdr. `top_n` is a positive integer or the literal string `ALL`. FDR is strictly positive and at most 1; expression_cutoff is finite and nonnegative. No version-dependent default cutoff is chosen: historical SoupX1.3.0 defaults to 0, while the captured1.6.2 defaults to 0.9. P008 reports neither threshold nor function/settings/version.

Use unquoted UTF-8 TSV. `genes.tsv` has the header `gene_id`, `cells.tsv` has `cell_id` and `cluster`, and sparse `counts.tsv` has `gene_id`, `cell_id` and `value`. Include all genes/cells, even zero-expression axes; absent coordinates mean zero. IDs are literal and unique within each axis; `NA` is a valid ID. Values must be complete, finite, nonnegative decimals. A value is expressing only when strictly greater than the declared cutoff. Output order uses unsigned UTF-8 bytes, matching the captured C collation, with stable gene ties and the first other cluster in that order for second-best ties. Unused R factor levels are outside this string-label interface.

Default logical bounds are128MiB total input,100,000 genes, one million cells/entries/gene-cluster pairs,100million operations and1GiB combined result tables. Optional `maximum_input_bytes`, `maximum_genes`, `maximum_cells`, `maximum_entries`, `maximum_gene_cluster_pairs`, `maximum_operations` and `maximum_output_table_bytes` adjust them within the parser limits. Bounds are not process RAM or disk quotas. Choose a permitted Java heap and storage suitable for the full gene/cluster table.

## Primary paper roles

Seven draft reviews are source-bound to the fixed cohort, with two additional named supporting paragraphs. They are preserved in the evidence and have not yet been applied to the credited catalog.

| Paper | Reported role | Limits retained |
|---|---|---|
| [P008](https://doi.org/10.1038/s41586-025-09875-2) | TF-IDF cluster-marker ranking | No function, settings or version; no ambient correction established |
| [P087](https://doi.org/10.1038/s41588-026-02774-w) | Manual human-liver annotations/ALB; separate mouse immune-cell annotations/Csf1r |1.6.2 is stated for mouse analysis, not the human analysis; non-expression masks/functions unknown |
| [P104](https://doi.org/10.1038/s41588-026-02733-5) | SoupX1.6.2 secondary ambient RNA validation | Does not establish corrected counts were used |
| [P145](https://doi.org/10.1038/s41588-026-02709-5) |1.6.2 autoEstCont then adjustCounts | Reported overrides tfidfMin=.95, soupQuantile=.6, priorRho=.1, contaminationRange=(.05,.8); subsequent DESeq2/covariate work is separate |
| [P182](https://doi.org/10.1016/j.ajhg.2024.10.019) |1.3.0 cell-free mRNA estimation/removal | Functions, settings and historical dependency environment unknown |
| [P225](https://doi.org/10.1016/j.ajhg.2024.07.002) | Sample-wise ambient RNA removal | scds and later manual cluster removal are separate |
| [P233](https://doi.org/10.1016/j.ajhg.2025.07.018) |1.6.2 ambient RNA removal | Later Seurat filters, annotation and RBC removal are separate |

## Source and evidence

The [official package](https://cran.r-project.org/web/packages/SoupX/index.html) and [maintainer repository](https://github.com/constantAmateur/SoupX) provide the original GPL-2 source. Source1.6.2 was captured from CRAN with archive/member checks. The first CRAN1.3.0 URL returned404; a complete official GitHub history candidate declaring1.3.0 was recovered at commit `1f6c0fb72944567f1903fe297233a4ea45c08638`. The commit and version labels do not identify the paper's exact source or dependencies. Original R4.6.1 references use Matrix1.7-5, Seurat5.6.0 and SeuratObject5.4.0; this is not a historical R3 environment replay. Initial constructor/storage-class failures and original warnings remain retained.

The Java mathematical implementation is independently written. Upstream GPL-2 source stays in the separate reference evidence; the separate R preparation/control programs carry GPL-2.0-or-later notices. No original SoupX source is copied into Java or bundled into the additive JAR.

The [sealed evidence](tfidf-markers-development/VALIDATION.json) records83 input cases per edition:79 original returns and four original errors. Each returned case includes a second diagnostic-only original `N=Inf,FDR=Inf` call exposing every gene/cluster statistic; this is not an admitted user FDR setting. Reference v2 fixtures retain17-significant-digit statistic strings and original RDS objects; initial display-rounded fixtures remain as historical evidence. The native suite passes171 checks on Windows Java21/25 and Linux Java21 with no skips. Twenty-eight packaged protocols independently audit exact combinatorial tails, every statistic, BH families, selected rows, capacities, late failure, identifiers and the executed example. Four extra protocols retain original/native boundary behavior. All parent JAR entries and canonical executable bytes remain unchanged.

```powershell
python tools/literature/soupx_markers_evidence.py
```

The checker needs Python and saved evidence only; it installs or executes no R package. Hashes establish integrity, not authenticity or biological validity. Ambient RNA estimation/correction adapters, exact paper reconstruction and whole-goal completion remain outstanding. Cell Ranger remains excluded.
