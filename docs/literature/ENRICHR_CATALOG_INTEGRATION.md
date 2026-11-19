# Enrichr catalog integration and source crosswalk

This crosswalk supports guarded admission of one bounded current-service upstream adapter.
The admission proof and fresh verification establish the applied state; this guide alone
does not establish that admission has been applied.

250 papers, 446 named families, 2,703 observations, 1,462 individually reviewed positive uses and 1,241 awaiting individual review. There are 136 new scoped integrations (25 native Java and 111 upstream), 259 missing and 39 method-overlap definitions; 139 integrations include three existing adapters. Twenty-three Enrichr reviews and three source-supported observations are appended; all 1,439 earlier reviews, including P061/P163, are retained. The full implementation and semantic-review goal remains open.

Java file workflow for the current human/mouse Enrichr upstream service: prepared literal gene lists, server-default or explicit custom backgrounds, exact library download, raw/partial capture, all returned terms and numeric tokens, semantic warnings and offline VERIFY/REPLAY. Statistical computation is upstream.

Twenty-five reviewed source roles include 23 enrichment-analysis roles, one library-resource use and one unresolved function/package attribution. Historical clients/library snapshots, Julia local recalculation, FGSEA/intersections, original inputs/options and complete author workflows remain explicit gaps. The service does not reveal its final scoring population; custom normalization is unobserved. Use a current source build: the published 0.4.0 binary predates this command.

## Qualified scope and limitations

Read the [normal-build qualification](enrichr-current-checks/README.md),
[current-service guide](../vignettes/enrichr-current-service.md) and
[runnable synthetic examples](../../examples/enrichr-workflow/README.md).
The two-library standard/custom live captures compare all nine fields of 600 rows
independently, and preserve source positive infinite margins with warnings.
Normal API/workflow tests passed on three JVMs and documented commands passed.
No author cohort/runtime, historical client/library parity or biological result
is established by those synthetic tests. Statistics are computed upstream.

The 250-identity census identified 25 roles, including three missed observations
(P048/P051/P134). P048/P051 use embedded figure legends explicitly; no Methods
execution record is invented. The two prior P061/P163 reviews and observations
remain exact apart from a separate scope-qualification annotation.

## Source-role mapping

### P005 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823418/); DOI `10.1038/s41586-025-09887-y`. Declared host: unspecified. Reported editions: Not reported.

Tested GO enrichment for the eleven co-regulated astrocyte gene modules.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Keep the reported library spelling; no exact service/client edition, background or ontology snapshot is established.

### P008 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893920/); DOI `10.1038/s41586-025-09875-2`. Declared host: unspecified. Reported editions: Not reported.

Queried upregulated BPA/BBP-associated genes against Hallmark 2020 and displayed adjusted-P<0.05 pathways.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: EnrichR capitalization alone does not identify the R client; background and precise API/library identifier are unstated.

### P009 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11798877/); DOI `10.1038/s41586-024-08334-8`. Declared host: GSEApy. Reported editions: 1.0.4 (GSEApy).

Queried up- and downregulated TISSUE DEGs separately through GSEApy 1.0.4, with mouse organism and all imputed genes as background; displayed the top five sets per comparison.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: The GSEApy edition is not an Enrichr server edition; exact historical library bytes and author results are not recovered.

### P021 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600939/); DOI `10.1038/s41586-026-10271-7`. Declared host: enrichR-R. Reported editions: 3.2 (enrichR-R).

Used enrichR 3.2 for age/subclass DEGs, lifespan-trajectory clusters, shared protein-coding genes, pseudotime traDEG clusters and rhythmic gene groups; GO BP 2021 and the separate traDEG GO BP 2023 branch are retained.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Do not assign GO BP 2021 to the explicitly different 2023 traDEG branch or infer a library for the rhythmic branch; historical server bytes/backgrounds are not established.

### P031 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872457/); DOI `10.1038/s41586-025-09829-8`. Declared host: web. Reported editions: Not reported.

Uploaded human intestinal DEGs, mouse M-cell marker genes and bulk M-cell organoid DEGs for GO/cell-type or pathway annotation.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Keep the separate input lists and their stated DEG criteria; downstream clusterProfiler GSEA and its KEGG/GO v7.4 sets are separate from the Enrichr uploads.

### P039 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10764288/); DOI `10.1038/s41586-023-06837-4`. Declared host: enrichR-R. Reported editions: 3.1 (enrichR-R).

Applied R Enrichr 3.1 GO BP 2021 enrichment to the stated tumour-cluster-2 DEG selection, preserving the literal log2[FC]<-0.5 and adjusted-P<0.05 criteria.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Do not reverse the reported fold-change inequality to match the accompanying enriched-gene wording; exact inputs/background remain required.

### P048 — analysis-caption

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11779626/); DOI `10.1038/s41586-024-08290-3`. Declared host: unspecified. Reported editions: Not reported.

The embedded Figure 4a legend reports Enrichr Hallmark 2020 enrichment of human/mouse SPP1hi-TAM DEGs selected with adjusted-P<0.05 and absolute log2FC>0.5, with an adjusted-P=0.05 display line.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: This is an explicitly identified figure-legend use inside a body paragraph, not a Methods-derived execution record; host, background and exact settings are unestablished.

### P051 — analysis-caption

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823425/); DOI `10.1038/s41586-025-09807-0`. Declared host: unspecified. Reported editions: Not reported.

The embedded NeuMap figure legend reports retaining transcription factors identified by both EnrichR and chromatin-accessibility analysis for the trajectory network.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: No Enrichr library, host, threshold or full recipe is specified in this legend; an empty immuno-silent-path finding is not a general pathway-absence claim.

### P053 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719095/); DOI `10.1038/s41586-023-06819-6`. Declared host: GSEApy. Reported editions: Not reported.

Used the GSEApy Enrichr module with GO BP 2021 and distinct tested/expressed-gene or ABC-linked backgrounds for each species, cell type and ABC comparison.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Retain the several comparison-specific background definitions; do not substitute one universal background or transfer the nearby edgeR version to GSEApy.

### P055 — library-resource

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719111/); DOI `10.1038/s41586-023-06818-7`. Declared host: unspecified. Reported editions: Not reported.

Combined Enrichr transcription-factor target resources from ARCHS4, ENCODE and TRRUST, retaining human factors found in at least two resources with at least ten unique targets per resource and intersecting targets across pairs; downstream ranked testing used FGSEA 1.20.0.

Current mapping: current-human-mouse-library-download. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: This establishes library-resource use, not Enrichr enrichment-service execution. Exact database editions, identifiers and pairwise combination details require original resources; FGSEA options are a separate method.

### P058 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719099/); DOI `10.1038/s41586-023-06817-8`. Declared host: unspecified. Reported editions: Not reported.

Used enrichR and the three named GO 2021 libraries to annotate differential projection-target/SPP1 neuronal gene lists.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Do not infer an R-client edition or background from the enrichR spelling or from the preceding MAST/Seurat analysis.

### P061 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719113/); DOI `10.1038/s41586-023-06805-y`. Declared host: unspecified. Reported editions: Not reported.

Tested GO enrichment for genes overlapping selected chromatin bins and used randomly selected gene-length-matched background genes to address long-gene bias.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Keep the prior review object unchanged. Length matching and selection of the 300 bins are separate preparation steps; a generic custom-background API does not reconstruct that random sampling.

### P074 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807872/); DOI `10.1038/s41588-025-02417-6`. Declared host: web. Reported editions: Not reported.

Annotated prioritized causal-gene lists with Enrichr molecular-function ontologies, including the 261 prioritized genes and comparison with the AV-TWAS list.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Separate this additional Enrichr annotation from DEPICT/apcluster testing; exact ontology release, background and settings are unreported.

### P085 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631771/); DOI `10.1038/s41588-024-01961-x`. Declared host: enrichR-R. Reported editions: 3.1 (enrichR-R).

Used enrichR 3.1 GO enrichment for DEGs/shared DEGs, human meta-modules, mouse co-expression modules and amyloid-associated genes; a separate sex-comparison branch also names EnrichR.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Do not infer a GO year or exact historical server library. The sex-comparison paragraph does not separately declare its client version.

### P098 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649585/); DOI `10.1038/s41588-026-02745-1`. Declared host: web. Reported editions: Not reported.

Submitted Group 4 EnhBiv signature genes for GO BP 2025 enrichment and combined-score ranking; the Results separately report ChEA regulator enrichment of the 666-gene signature and clusters.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Preserve the paper's literal two-sided Fisher claim and BH statement. Do not silently substitute one-sided local ORA or infer its exact tested family, ChEA release or background.

### P121 — analysis-and-local-recalculation

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703691/); DOI `10.1038/s41588-023-01565-x`. Declared host: Enrichr.jl. Reported editions: Not reported.

Used Enrichr.jl API queries and downloaded term/gene definitions, then recalculated right-tail Fisher P values and odds ratios with HypothesisTesting.jl against all DE-tested genes and applied BH with MultipleTesting.jl.

Current mapping: current-human-mouse-remote-enrichment, current-human-mouse-library-download. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Current remote Enrichr statistics are not these locally recalculated results. Keep literal library names; exact Julia versions, odds-ratio estimator and tested-term family need author-code/source checks.

### P124 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703699/); DOI `10.1038/s41588-023-01554-0`. Declared host: unspecified. Reported editions: Not reported.

Annotated upregulated single-cell perturbation DEGs and the separate up/down bulk perturbation DEG lists with EnrichR GO BP 2021.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: No client edition, historical library snapshot or background is declared; upstream MAST/DESeq2 fitting is separate.

### P134 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553295/); DOI `10.1038/s41588-026-02714-8`. Declared host: unspecified. Reported editions: Not reported.

Compared differential-activity/core TF sets with KEA through EnrichR; the embedded Figure 5 legend describes Fisher testing with BH after the separate GSEA leading-edge selection.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Do not relabel KEA as ChEA or copy the neighboring two-sided permutation-GSEA specification onto Enrichr; exact KEA edition/background is unstated.

### P161 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447095/); DOI `10.1038/s41588-026-02665-0`. Declared host: GSEApy. Reported editions: 1.1.2 (GSEApy).

Used GSEApy 1.1.2 EnrichR on adjusted-P<0.05 genes, total measured expressed genes as background and the stated WikiPathway_2023_Human pathway list.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Preserve the paper's singular WikiPathway spelling; do not silently map it to a currently available differently spelled API identifier or infer original library bytes.

### P163 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525197/); DOI `10.1038/s41588-024-01911-7`. Declared host: unspecified. Reported editions: Not reported.

Performed additional Enrichr gene-list enrichment after separately described ranked FGSEA/MSigDB analyses.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Keep the prior review object unchanged. The FGSEA/MSigDB version and collections are not automatically Enrichr settings; exact Enrichr library, host and background remain unspecified.

### P169 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525171/); DOI `10.1038/s41588-024-01900-w`. Declared host: unspecified. Reported editions: 8 June 2023 (reported-release-date).

Reported pathway enrichment using Enrichr released 8 June 2023.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: A reported release date is retained as a date, not converted to a semantic server/client version or evidence that the present server reproduces that release.

### P182 — analysis-host-ambiguous

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639091/); DOI `10.1016/j.ajhg.2024.10.019`. Declared host: declared-ClusterProfiler. Reported editions: 4.2.2 (declared-ClusterProfiler).

Reports separate up/down ORA using functions named enrichr and enrichGO attributed to ClusterProfiler 4.2.2, 10-1500-gene sets, all QC-detected genes as background and FDR<0.05.

Current mapping: No execution mapping: unresolved attribution. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Retain the literal function/package attribution as unresolved rather than correcting it to enrichR. MSigDB 7.5.1 and ranked FGSEA are separately described; Enrichr service execution is not established.

### P228 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696504/); DOI `10.1016/j.ajhg.2025.07.020`. Declared host: unspecified. Reported editions: Not reported.

Used enrichR ORA for the snRNA-seq DEG analysis after the stated adjusted-P<0.2 selection; ranked FGSEA and GO redundancy reduction are separate analyses.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: The 0.2 threshold selects input DEGs, not an inferred enrichment significance cutoff; no Enrichr edition, library or background is established.

### P241 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11267527/); DOI `10.1016/j.ajhg.2024.05.023`. Declared host: enrichR-R. Reported editions: Not reported.

Used the enrichR R package/webserver to annotate snoRNA pull-down interactor lists, ranked functional categories by combined score and depicted the top five per condition.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Top-five display is not the multiple-testing family. Exact library year, background and R/server editions are unstated; do not recompute the combined score with a different method.

### P244 — analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461026/); DOI `10.1016/j.ajhg.2025.07.003`. Declared host: API. Reported editions: Not reported.

Queried canonical annotation libraries through the Enrichr API for GLEANR factors, with reported BH adjustment per gene-set library for each single factor.

Current mapping: current-human-mouse-remote-enrichment. Method/component availability does not establish author-edition or workflow reproduction.

Source boundary: Do not pool factors/libraries into a different correction family or invent library years, exact submitted genes or historical server settings.

## Warnings and required actions

**SKIP3: skip dependent analysis/audit when the exact permitted source, runtime, service or validation dependency is unavailable; name what to restore and leave the skip visible. FAIL2: no complete result; retain raw inputs, partials and errors, correct the cause and retry NEW outputs. Do not repair IDs, source inequalities, P values, nonfinite values, libraries or algorithms silently. Inspect WARNINGS.md and analysis-status.json. Positive source infinite scores and P=0 are explicit warnings, not capped values. Earlier fixture failures and two historical marker-verifier SKIP3 gaps remain retained. Both literature reports stay withheld from Pages; no release is created.**

Use the [guarded admission commands](../../tools/literature/enrichr_catalog/ADMISSION.md).
The unchanged original COLOC owner is run in its exact clean historical checkout,
with the complete before-stage metadata and existing source fingerprints.
The new current-service reader separately checks exact dispatcher/annotation
transitions and retained evidence. No old verifier, source lookup or hash is
rewritten. The earlier development-packet reader pins mutable historical
handover text; treat that old packet as an immutable historical checkpoint,
and use the current-service reader for the qualified current scope.
