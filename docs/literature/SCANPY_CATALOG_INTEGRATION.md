# Scanpy catalog integration: exact scope and source roles

This admission records **one bounded upstream workflow family**, plus three
explicit native Java component APIs. It appends 21 individual source reviews
for the 22 existing Scanpy observations, preserves P061 and all 1,462 earlier
review objects, and adds no observations or duplicate native family credit.
The catalog has **137 scoped integrations (25 native +112 upstream), 1,483
reviewed uses and 1,220 pending** across the same 250 frozen papers and 2,703
observations. The full implementation and semantic-review goal is unfinished.

## Available commands and APIs

Build current source with `./gradlew executableJar`; published 0.4.0 predates
these entry points. Use Java 17+ and an explicitly declared request:

```bash
java -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --help
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --request /absolute/path/request.json
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --verify /absolute/path/completed-capture
```

Follow the [60-cell runnable example](../../examples/scanpy-workflow/README.md)
and [main-command guide](../vignettes/scanpy-global-command.md) for full request
preparation. The [ordered-workflow guide](../vignettes/scanpy-workflow.md)
explains explicit input orientation, float64 MatrixMarket plus typed metadata,
H5AD intake, layers/raw, output, limits, runtime selection and REPLAY.

Original RUN/REPLAY uses profile `Scanpy-1.13.0-CPU-v1`: the qualified Linux CPU
Python 3.13+ stack, 14 exact dependency versions and 143 Scanpy source hashes.
The two qualified programs cover **15 distinct operations**, with 58 original
array/annotation comparisons. The global command passed RUN/VERIFY/REPLAY,
52 original-array comparisons and 62 existing checks per JVM. The broader
29-entry development allowlist is not 29 qualified operations. VERIFY is
Python-free captured-consistency/structure checking, not independent refitting
or proof against coordinated fabrication.

| Explicit native Java API | Source-bound scope | Saved original-reference checks |
| --- | --- | --- |
| [Preprocessing](../vignettes/scanpy-preprocessing.md) | Float64 dense/CSR filtering, normalize_total, log1p and scale; 1.9.3/1.13.0 | 426 checks/JVM, including 418 original cases |
| [Pearson residual matrices](../vignettes/scanpy-pearson-native.md) | Float64 dense/CSR analytic residuals; explicit 1.10.4/1.13.0 zero conventions; no SCT fit/HVG selection | 735 checks/JVM, including 720 original cases |
| [Dispersion HVGs](../vignettes/scanpy-hvg-native.md) | Seurat/Cell-Ranger flavors; float64 dense/CSR, optional explicit batches, ties/missing/export rules; 1.9.3/1.13.0 | Normal JAR and isolated library each pass 2,574 checks/JVM, including 2,560 original cases and 928 original errors |

These APIs require Java only and do not silently fall back to Python. Supported
profiles and data representations must be selected explicitly. A source-defined
missing annotation is not permission for generic NaN repair. Check `usable()`;
diagnostic masks are not admitted results when it is false. The original
upstream workflow remains a separate path. Native dispersion HVGs do not cover
Seurat-v3 LOESS or experimental Pearson HVG selection.

The three test JVMs are Windows Java 21/25 and Linux Java 21. Counts describe
retained actual executions; the catalog verifier reads saved proof and does
not rerun scientific models. See the [preprocessing](scanpy-preprocessing-checks/README.md),
[workflow](scanpy-workflow-checks/README.md), [Pearson](scanpy-pearson-checks/README.md),
[global-command](scanpy-dispatch-checks/README.md) and [HVG](scanpy-hvg-checks/README.md)
proof/restorer instructions. Original source, fixtures, failed attempts and
corrected candidates remain retained.

## What the 22 papers actually report

The [frozen source census](scanpy-preprocessing-checks/CENSUS.json) checks all
250 XML identities and records 53 named paragraphs. The
[machine-readable coverage](omics-2023-2026/scanpy-source-role-coverage.json)
retains every anchor/hash, literal edition/context, source operation,
component counterpart and gap. Current counterparts do not establish author
entry points, settings or historical package equivalence. **No row establishes
complete author execution, historical parity or a reproduced whole paper.**

### P008 — gene-signature-scoring

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893920/); DOI `10.1038/s41586-025-09875-2`. Reported Scanpy editions: not stated. Declared host: `direct`.

Scored a 15-gene fetal fallopian-tube signature in adult Visium epithelial spots using score_genes, then tested rostrocaudal trends separately.

**Required caution:** The Jonckheere decreasing/increasing trend tests and 2,000 permutations are separate. No Scanpy edition, control-gene pool, binning or random seed is reported.

Related explicit native scope: none assigned.

Remaining scope: Exact signature, control pool, bins and RNG; Visium preparation and separate trend/permutation tests. The Jonckheere decreasing/increasing trend tests and 2,000 permutations are separate. No Scanpy edition, control-gene pool, binning or random seed is reported. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P009 — spatial-clustering-and-normalization

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11798877/); DOI `10.1038/s41586-024-08334-8`. Reported Scanpy editions: 1.9.1. Declared host: `direct`.

Scaled log-normalized MERFISH expression with max_value=10, used Leiden and BBKNN/UMAP, and used 20 PCs/15 neighbors for the single-batch reprogramming branch. Trajectories first divided counts by segmentation volume, then used normalize_total defaults.

**Required caution:** Keep the BBKNN and single-batch neighbor branches separate. Volume correction, trajectory imputation, sklearn StandardScaler/k-means and spline smoothing are outside these Scanpy operations; defaults do not imply target_sum=10,000.

Related explicit native scope: preprocessing: normalize_total, scale.

Remaining scope: Historical 1.9.1 behavior, BBKNN, volume correction and separate batch/trajectory populations/settings. Keep the BBKNN and single-batch neighbor branches separate. Volume correction, trajectory imputation, sklearn StandardScaler/k-means and spline smoothing are outside these Scanpy operations; defaults do not imply target_sum=10,000. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P010 — spatial-preprocessing

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893904/); DOI `10.1038/s41586-025-09873-4`. Reported Scanpy editions: not stated. Declared host: `direct`.

Normalized and scaled STARmap profiles using stated standard Scanpy procedures before a hierarchical three-level marker-based k-means annotation.

**Required caution:** No Scanpy edition, normalization target/log base or exact functions are supplied. Do not assign the separate k-means annotation algorithm to a nonexistent Scanpy k-means entry point.

Related explicit native scope: preprocessing: normalize_total, scale.

Remaining scope: Normalization/scaling entry points and settings are unspecified; separate hierarchical k-means is not a Scanpy operation. No Scanpy edition, normalization target/log base or exact functions are supplied. Do not assign the separate k-means annotation algorithm to a nonexistent Scanpy k-means entry point. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P015 — single-cell-preprocessing-and-embedding

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11735409/); DOI `10.1038/s41586-024-08332-w`. Reported Scanpy editions: 1.7.0. Declared host: `direct`.

Processed chick and human neuruloid scRNA-seq with Scanpy 1.7.0: distinct QC/high-expression exclusions, target sum 10,000, default HVGs, Harmony integration and graph embeddings. Chick used 10 neighbors/40 PCs and Leiden resolution 3.5; human used 15 neighbors/30 PCs and UMAP-initialized force-directed drawing.

**Required caution:** Preserve the literal 1% versus 3% high-expression wording; it does not by itself distinguish a top-gene percentage from max_fraction. Cell Ranger remains excluded; DropEst, Scrublet, Harmony and Slingshot are separate tools.

Related explicit native scope: preprocessing: normalize_total.

Remaining scope: Historical 1.7.0, literal high-expression exclusion, unspecified HVG flavor, Harmony, force-directed graph and chick/human settings. Preserve the literal 1% versus 3% high-expression wording; it does not by itself distinguish a top-gene percentage from max_fraction. Cell Ranger remains excluded; DropEst, Scrublet, Harmony and Slingshot are separate tools. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P021 — multi-branch-nuclear-preprocessing

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600939/); DOI `10.1038/s41586-026-10271-7`. Reported Scanpy editions: 1.9.1, 1.9.3. Declared host: `direct-and-custom-wrapper`.

Used Scanpy 1.9.1 with Pegasus in the main cohort, including a custom cell_ranger-flavor batch-aware HVG wrapper with Source batches and 6,000 genes. A separate 1.9.3 replication branch filtered genes, downsampled to 1,000 UMIs, applied CPM/natural-log-plus-one, chose 6,000 HVGs, PCA explaining 50% variance and 100-neighbor UMAP.

**Required caution:** The custom wrapper declares both mean/dispersion cuts and n_top_genes; do not invent their precedence. Downsampling, chromosome/protein-coding restriction, Pegasus import and scVI label transfer remain separate author steps.

Related explicit native scope: preprocessing: normalize_total, log1p; dispersion-hvg: cell_ranger.

Remaining scope: cell_ranger-flavor batch HVGs/custom wrapper precedence, downsampling, Pegasus/scVI and variance-selected PC count. The custom wrapper declares both mean/dispersion cuts and n_top_genes; do not invent their precedence. Downsampling, chromosome/protein-coding restriction, Pegasus import and scVI label transfer remain separate author steps. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced. Native dispersion HVGs qualify explicit 1.9.3/1.13.0 profiles, not this historical/custom wrapper.

### P024 — downstream-processing-unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600946/); DOI `10.1038/s41586-025-09573-z`. Reported Scanpy editions: 1.9.1. Declared host: `shared-with-Pegasus`.

Reported Scanpy 1.9.1 alongside Pegasus 1.7.0 for downstream processing of an 8,898,978-cell by 60,605-gene snRNA-seq matrix.

**Required caution:** The paragraph does not allocate individual processing functions between Pegasus and Scanpy. Large author-cohort capacity and exact options are not established by a bounded synthetic workflow.

Related explicit native scope: none assigned.

Remaining scope: No functions allocated between Scanpy/Pegasus; reported 8.9-million-cell cohort capacity is not qualified. The paragraph does not allocate individual processing functions between Pegasus and Scanpy. Large author-cohort capacity and exact options are not established by a bounded synthetic workflow. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P031 — mouse-organoid-preprocessing

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872457/); DOI `10.1038/s41586-025-09829-8`. Reported Scanpy editions: not stated. Declared host: `direct`.

Processed mouse intestinal organoid cells in Python 3.11.9: mitochondrial filtering, per-cell normalization, log10 transformation, 2,000 most highly expressed nonmitochondrial/nonribosomal genes, scaling, 50-component PCA and 200-neighbor graphs.

**Required caution:** Python 3.11.9 is not a Scanpy edition. Preserve log10 and most highly expressed genes; do not substitute natural log or highly variable genes.

Related explicit native scope: preprocessing: normalize_total, scale.

Remaining scope: Literal log10 with unstated pseudocount and most highly expressed genes; do not substitute log1p or HVGs. Python 3.11.9 is not a Scanpy edition. Preserve log10 and most highly expressed genes; do not substitute natural log or highly variable genes. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P048 — human-mouse-single-cell-analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11779626/); DOI `10.1038/s41586-024-08290-3`. Reported Scanpy editions: not stated. Declared host: `direct`.

Processed human and mouse cells with QC, HVGs, regression/scaling, PCA/Harmony, Leiden/UMAP, cluster-marker ranking, embedding density and curated gene scores. Human sample scores used the median of cell scores; cross-species comparison used separate pseudobulk/z-score preparation.

**Required caution:** Retain the reported log2-plus-one then normalization order rather than silently rewriting it. No Scanpy edition or rank_genes_groups method is specified. Gene scoring, sample aggregation, CellBender and doublet detection are distinct stages.

Related explicit native scope: preprocessing: scale.

Remaining scope: Reported transformation/normalization order, regression, unspecified HVG/ranking choices, Harmony and cross-species/sample aggregation. Retain the reported log2-plus-one then normalization order rather than silently rewriting it. No Scanpy edition or rank_genes_groups method is specified. Gene scoring, sample aggregation, CellBender and doublet detection are distinct stages. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P055 — nuclear-PCA-graph-and-markers

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719111/); DOI `10.1038/s41586-023-06818-7`. Reported Scanpy editions: not stated. Declared host: `direct`.

Computed 250 PCs after per-cluster subsampling, used 20-neighbor connectivities for cell-type graph aggregation and obtained top-50 Wilcoxon markers per metacluster.

**Required caution:** OpenTSNE 1.0.0 performed the t-SNE, not Scanpy. Cell-type adjacency averaging and the Paris dendrogram are separate scikit-network/custom operations; Scanpy edition and tie-correction settings are unstated.

Related explicit native scope: none assigned.

Remaining scope: 250-PC/subsampling and custom graph aggregation/Paris; separate OpenTSNE t-SNE; unstated tie correction. OpenTSNE 1.0.0 performed the t-SNE, not Scanpy. Cell-type adjacency averaging and the Paris dendrogram are separate scikit-network/custom operations; Scanpy edition and tie-correction settings are unstated. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P060 — cross-modality-reference-integration

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719103/); DOI `10.1038/s41586-023-06808-9`. Reported Scanpy editions: not stated. Declared host: `direct-and-custom-SeuratIntegration`.

Normalized selected scRNA-seq/MERFISH genes to 1,000, applied log1p/scaling, computed 100 PCs and 15-neighbor graphs for integration partitions. The second round retained genes with log dispersions greater than zero from highly_variable_genes.

**Required caution:** Keep log dispersions >0 distinct from an inferred normalized-dispersion cutoff or HVG mask. METIS partitioning, custom CCA/anchor label transfer and confidence multiplication are not Scanpy package parity.

Related explicit native scope: preprocessing: normalize_total, log1p, scale.

Remaining scope: Select on literal log dispersion >0, not inferred HVG mask; separate custom CCA/anchors, METIS and transfer confidence. Keep log dispersions >0 distinct from an inferred normalized-dispersion cutoff or HVG mask. METIS partitioning, custom CCA/anchor label transfer and confidence multiplication are not Scanpy package parity. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P061 — derived-methylome-consensus-clustering

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719113/); DOI `10.1038/s41586-023-06805-y`. Reported Scanpy editions: not stated. Declared host: `ALLCools-derived`.

Used functions derived from ALLCools, Scanpy and scikit-learn during four methylome clustering rounds. The reported Leiden resolutions were 0.25 initially and 0.2–0.5 later within ALLCools ConsensusClustering.

**Required caution:** Preserve the existing review. This is methylome-fraction analysis with derived/hosted functions, not proof of a standalone RNA-count pipeline or the whole ALLCools consensus procedure.

Related explicit native scope: none assigned.

Remaining scope: ALLCools-hosted methylome consensus rounds/resolutions and population; standalone RNA clustering does not reproduce this host. Preserve the existing review. This is methylome-fraction analysis with derived/hosted functions, not proof of a standalone RNA-count pipeline or the whole ALLCools consensus procedure. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P062 — nuclear-Pearson-preprocessing-and-markers

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668712/); DOI `10.1038/s41586-026-10964-z`. Reported Scanpy editions: 1.10.4. Declared host: `direct`.

Used Scanpy 1.10.4 for nuclear QC/Scrublet, Pearson-residual normalization and experimental HVGs before SysVI, then Leiden/UMAP and Wilcoxon markers. Separately used square-rooted normalize_total output for plots and DEG analysis.

**Required caution:** Do not replace Pearson residuals or the square-root count branch with log normalization. SysVI, MAGIC, pyWGCNA and GSEApy are separate. Repeated Code availability text is context, not another execution role.

Related explicit native scope: preprocessing: normalize_total; pearson-matrix: analytic Pearson residual matrix.

Remaining scope: Experimental Pearson HVG selection, Scrublet, SysVI and square-root-normalized plotting/DE branch; native Pearson matrix alone is partial. Do not replace Pearson residuals or the square-root count branch with log normalization. SysVI, MAGIC, pyWGCNA and GSEApy are separate. Repeated Code availability text is context, not another execution role. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced. Native dispersion HVGs do not implement the reported Pearson/seurat_v3 HVG branch.

### P070 — spatial-single-cell-and-trajectory-analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC7616500/); DOI `10.1038/s41586-023-06806-x`. Reported Scanpy editions: 1.8.2. Declared host: `direct-with-external-wrappers`.

Used Scanpy 1.8.2 for per-batch 10,000-count normalization/log1p/HVG selection, scaling/cell-cycle regression, 100-PC/BBKNN integration, Leiden/PAGA/UMAP and diffusion-map/force-directed trajectories. Also reported Visium Wilcoxon marker tests and a custom Scrublet overclustering screen.

**Required caution:** Keep literal rank_gene_group, score_gene_cell_cycle and scanpt spellings unresolved; do not silently claim execution of corrected paths. BBKNN, custom doublet significance/manual curation and scVelo stochastic velocity are separate stages.

Related explicit native scope: preprocessing: log1p, scale.

Remaining scope: Historical normalize_per_cell and literal misspelled paths; regression/cell cycle, BBKNN, PAGA/diffmap/draw graph, custom Scrublet and scVelo. Keep literal rank_gene_group, score_gene_cell_cycle and scanpt spellings unresolved; do not silently claim execution of corrected paths. BBKNN, custom doublet significance/manual curation and scVelo stochastic velocity are separate stages. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P083 — hosted-query-reference-analysis-stack

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649598/); DOI `10.1038/s41588-026-02756-y`. Reported Scanpy editions: 1.9.1. Declared host: `ArchMap/scArches`.

Reported Scanpy 1.9.1 in the ArchMap query-to-reference mapping and downstream-analysis software stack, alongside scArches, XGBoost, scvi-tools and scIB.

**Required caution:** The package/version declaration does not establish exact standalone Scanpy functions or recreate the hosted mapping pipeline. The scArches branch and linked requirements need separate author-code checks.

Related explicit native scope: none assigned.

Remaining scope: No exact standalone Scanpy functions established; hosted ArchMap/scArches query/reference model and author requirements remain separate. The package/version declaration does not establish exact standalone Scanpy functions or recreate the hosted mapping pipeline. The scArches branch and linked requirements need separate author-code checks. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P085 — nuclear-processing-and-integration

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631771/); DOI `10.1038/s41588-024-01961-x`. Reported Scanpy editions: not stated. Declared host: `direct-with-scVI-and-Harmony`.

Normalized study counts and used scVI latent variables for Leiden/UMAP; reprocessed external cohorts with normalize_total/log1p, HVGs, scaling, PCA, Harmony, neighbor graphs and repeat Leiden/UMAP after QC.

**Required caution:** Retain the literal sc.ppl.log1p path, ln(CPM) wording and inconsistent cluster-number description without repair. normalize_total defaults do not establish target_sum=1e6. scVI fitting and author percentile/cluster curation are separate.

Related explicit native scope: preprocessing: normalize_total, scale.

Remaining scope: Unresolved sc.ppl.log1p, ln(CPM) target and cluster-count wording; HVGs, scVI/Harmony and manual curation. Retain the literal sc.ppl.log1p path, ln(CPM) wording and inconsistent cluster-number description without repair. normalize_total defaults do not establish target_sum=1e6. scVI fitting and author percentile/cluster curation are separate. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P093 — hosted-popV-preprocessing-and-evaluation

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631762/); DOI `10.1038/s41588-024-01993-3`. Reported Scanpy editions: 1.9.3. Declared host: `popV`.

Used Scanpy 1.9.3 in popV 0.2.2: seurat_v3 HVGs (default 4,000), 10K normalization, separate log1p layer, scaling/PCA, BBKNN/UMAP, default marker ranking and marker dotplots. UMAP min_dist was 0.3.

**Required caution:** Preserve raw-count and log layers, the seurat_v3 flavor and popV preprocessing/model context. BBKNN angular-distance settings and scikit-learn classification are separate; defaults do not imply Wilcoxon markers.

Related explicit native scope: preprocessing: normalize_total, log1p, scale.

Remaining scope: Explicit seurat_v3 HVGs, count/log layers, popV host, BBKNN angular metric, default ranking method and dotplots. Preserve raw-count and log layers, the seurat_v3 flavor and popV preprocessing/model context. BBKNN angular-distance settings and scikit-learn classification are separate; defaults do not imply Wilcoxon markers. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced. Native dispersion HVGs do not implement the reported Pearson/seurat_v3 HVG branch.

### P094 — multi-branch-RNA-spatial-metacell-analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649600/); DOI `10.1038/s41588-026-02748-y`. Reported Scanpy editions: 1.9.3, 1.8.2. Declared host: `direct`.

Used Scanpy 1.9.3 for main embryo RNA preprocessing/graphs, separate raw/denoised spatial branches and PCA embeddings with a Stereo-seq PC1 exclusion. A distinct SCENIC+ metacell RNA branch used Scanpy 1.8.2 normalize_total/log1p/scale.

**Required caution:** Do not assign 1.9.3 to the explicit 1.8.2 metacell branch. Denoised data supplied graph/clustering, normalized raw counts supplied GWAS scores. Harmony, denoising, transfer, Squidpy Moran tests, metacells and SCENIC+ are separate.

Related explicit native scope: preprocessing: normalize_total, log1p, scale.

Remaining scope: Historical 1.9.3 versus 1.8.2 branches; raw/denoised matrices, PC1 exclusion, regression, denoising, transfer and SCENIC+. Do not assign 1.9.3 to the explicit 1.8.2 metacell branch. Denoised data supplied graph/clustering, normalized raw counts supplied GWAS scores. Harmony, denoising, transfer, Squidpy Moran tests, metacells and SCENIC+ are separate. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P100 — RNA-and-MALDI-MSI-processing

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695644/); DOI `10.1038/s41588-025-02407-8`. Reported Scanpy editions: 1.10.3. Declared host: `direct-and-AnnData-workflow`.

Used Scanpy 1.10.3 for RNA QC, library normalization/log transformation, 6,000 HVGs, Harmony-adjusted 30-PC/10-neighbor Leiden/UMAP and Wilcoxon ranking with reported tie correction. MALDI-MSI AnnData preprocessing used separate pixel/metabolite filters and total-ion normalization.

**Required caution:** Keep the literal AND in the first RNA QC rule, tie_correct = TRUE as reported, and removal of metabolites expressed in more than 95% of pixels. Do not turn MSI into RNA counts or attribute Hotspot module fitting to Scanpy.

Related explicit native scope: preprocessing: normalize_total, log1p.

Remaining scope: Historical 1.10.3, literal RNA QC AND and >95% MSI pixel rule, exact HVGs/Harmony; RNA and MSI populations remain distinct. Keep the literal AND in the first RNA QC rule, tie_correct = TRUE as reported, and removal of metabolites expressed in more than 95% of pixels. Do not turn MSI into RNA counts or attribute Hotspot module fitting to Scanpy. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P104 — downstream-processing-unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649572/); DOI `10.1038/s41588-026-02733-5`. Reported Scanpy editions: 1.9.1. Declared host: `shared-with-Pegasus`.

Reported Scanpy 1.9.1 with Pegasus 1.7.0 for downstream processing of per-library nuclear count matrices.

**Required caution:** This paragraph does not name exact Scanpy functions or allocate steps between the two packages. STARsolo alignment and Vireo demultiplexing are separate.

Related explicit native scope: none assigned.

Remaining scope: No functions allocated between Scanpy/Pegasus; STARsolo and Vireo are separate. This paragraph does not name exact Scanpy functions or allocate steps between the two packages. STARsolo alignment and Vireo demultiplexing are separate. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P123 — PBMC-preprocessing-and-annotation

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553293/); DOI `10.1038/s41588-026-02741-5`. Reported Scanpy editions: 1.9.8. Declared host: `direct`.

Used Scanpy 1.9.8 for 10,000-count normalization/log1p, 3,000 shared HVGs, PCA, reported Harmony integration and UMAP based on 30 adjusted PCs before manual PBMC annotation.

**Required caution:** Preserve the stated scanpy.pp.harmony_integrate path as reported; its availability in that edition is not established by a newer root-level function. Shared-HVG batching details, graph settings and manual annotation remain unreported.

Related explicit native scope: preprocessing: normalize_total, log1p.

Remaining scope: Historical 1.9.8 and literal pp.harmony_integrate availability, shared-HVG batching and unstated graph/manual annotation settings. Preserve the stated scanpy.pp.harmony_integrate path as reported; its availability in that edition is not established by a newer root-level function. Shared-HVG batching details, graph settings and manual annotation remain unreported. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P161 — RNA-spatial-and-ligand-neighborhood-analysis

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447095/); DOI `10.1038/s41588-026-02665-0`. Reported Scanpy editions: 1.9.1. Declared host: `direct-with-integration-tools`.

Used Scanpy 1.9.1 for RNA HVGs with sample batches and explicit mean/dispersion cuts, then neighbor/Leiden/UMAP analysis. Separate CosMx and ligand-neighborhood branches used expression/latent representations and Wilcoxon rank-gene comparisons across cell types or neighborhoods.

**Required caution:** Do not collapse RNA, raw CosMx counts, imputed values, scVI/scANVI latent spaces and CytoSignal ligand matrices into one input population. Harmony/scVI/scANVI fitting, spatial neighborhoods and GAMs are separate tools.

Related explicit native scope: preprocessing: normalize_total, log1p.

Remaining scope: Batch/sample HVGs and literal cuts; separate RNA, CosMx, latent, imputed and ligand matrices; integration/neighborhood/GAM tools. Do not collapse RNA, raw CosMx counts, imputed values, scVI/scANVI latent spaces and CytoSignal ligand matrices into one input population. Harmony/scVI/scANVI fitting, spatial neighborhoods and GAMs are separate tools. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

### P182 — supplied-integrated-object-visualization

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639091/); DOI `10.1016/j.ajhg.2024.10.019`. Reported Scanpy editions: not stated. Declared host: `direct-on-reused-integrated-object`.

Used a supplied integrated cartilage scRNA-seq object, broad cell-type reannotation and QC, then displayed representative gene markers and UMAP representations created using Scanpy.

**Required caution:** This does not establish de novo normalization, HVG selection, PCA, neighbor graphs or clustering in Scanpy. Preserve the supplied-object provenance and avoid inventing a fit or package edition.

Related explicit native scope: none assigned.

Remaining scope: Supplied integrated-object provenance and plotting/reannotation; no de novo normalization/HVG/PCA/neighbor fit is established. This does not establish de novo normalization, HVG selection, PCA, neighbor graphs or clustering in Scanpy. Preserve the supplied-object provenance and avoid inventing a fit or package edition. Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.

## Admission, preservation and exact recovery

The [driver instructions](../../tools/literature/scanpy_catalog/ADMISSION.md)
give plan/inspect/apply/verify commands. Apply preserves the exact eight-document
before archive, original 812-entry manifest, source/qualification evidence,
registry and verifier source. It uses concurrent-edit guards and an exact
reviewed-use JSON inverse. The original unchanged Enrichr owner runs in an
exact clean historical checkout and invokes its original COLOC predecessor
in a separate exact checkout. No scientific source is overlaid, no old hash
is refreshed, and no completed earlier admission is reapplied. Current-source
Enrichr dispatch compatibility is separately checked by its explicit inverse.

The first long Windows checkout failed before admission or analysis and is
retained in the [preparation packet](handover-scanpy-20261119/README.md).
A NEW shorter checkout with command-local long-path support succeeds; the
failed attempt and all 22 preceding worktrees remain intact. The new exact
Enrichr checkout is a 23rd preserved worktree. Installed reference environments,
full inputs, captures and JARs remain separate machine-transfer items; GitHub
contains source and compact public evidence rather than those ignored files.

**SKIP3 — dependent analysis skipped:** restore the exact named permitted
runtime, source, input or evidence. Downloads/installs are allowed where the
environment permits. If an item cannot be installed or executed, keep the
analysis skipped, identify the affected step and explain what the user must
provide or install. Retry only into NEW output.

**FAIL2 — no complete result:** preserve inputs, warnings, diagnostics and
partials; correct changed evidence, invalid schema/data, undefined/nonfinite
arithmetic, lost positive model terms, capacity, timeout, interruption or
process failure. Do not silently repair IDs, dtype, alleles, P values,
inequalities, populations, NaN/Inf or algorithms. Read each workflow's
`WARNINGS.md`, `analysis-status.json`, worker reports/status and process logs.

Two historical full marker-verifier mains remain **SKIP3** until original
`build/cli/omicsmatic-0.3.7.jar` is restored: **164,898,617 bytes**, SHA-256
`545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2`.
A different published JAR is not a replacement. Component/current checks pass
separately and do not imply historical full-verifier success.

Cell Ranger remains excluded at the user's request; Scanpy's open-source
`cell_ranger` HVG flavor is separate. Both literature HTML reports remain
withheld from Pages. No release or whole-goal completion is claimed. Continue
the remaining individual reviews and prioritize other high-impact missing
scopes after this bounded admission.
