# Seurat: scoped workflows and 50 source-specific roles

This admission connects the qualified original Seurat 5.6.0 RNA workflow and
retained components to 50 individually reviewed paper roles. It adds one
upstream scoped family integration and one PDF-backed observation; it does not
implement every Seurat function or reproduce historical author workflows.
The machine-readable [admission proof](omics-2023-2026/seurat-catalog-validation.json)
and [role census](omics-2023-2026/seurat-source-role-coverage.json) establish
whether this transition has actually been applied. A plan alone awards no credit.

## Warning: historical full marker-verifier runs skipped

**SKIP3: the two historical full marker-verifier mains were skipped because their exact old canonical build/cli/omicsmatic-0.3.7.jar is unavailable here (164898617 bytes; SHA-256 545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2). Restore that exact original file to rerun them, or leave them skipped. The published v0.3.7 JAR has different bytes. Neither the old expected hash nor verifier source was changed. Current RNA/native audits, complete retained marker-archive/source checks and exact frozen marker-method payload identity in the current JAR are separately checked; no fresh historical full-verifier success or R refit is claimed.**

The initial unchanged historical mains returned FAIL2 for the missing file;
their failed consoles are retained locally at
`build/seurat-catalog-1/legacy-audit-intake-1/`. Subsequent bounded searches and
the official v0.3.7 release metadata did not find the expected bytes. The full
historical checks remain skipped, with no substituted JAR or changed old hash.
This does not skip or relabel the separately completed current RNA audit.
The current method bridge instead checks the exact originally tested marker
development JAR hashes and all 18 marker workflow payloads plus 11 native
summary classes against the current qualified JAR. No original marker R models
or historical test suites were rerun for admission.

## What is available

Use the source-build [RNA workflow guide](../vignettes/seurat-rna-workflow.md),
[copyable example](../../examples/seurat-rna-workflow/README.md) and
[original qualification](seurat-workflow-development/README.md).
`org.jlinalg.cli.SeuratWorkflowCli` has typed RUN, VERIFY and REPLAY requests for
RNA construction; LogNormalize/VST or explicit SCT v2; typed numeric/factor
regression and scaling; exact, Gram/fallback or explicit IRLBA PCA; Annoy/RANN
neighbors and SNN; source clustering; optional UMAP and t-SNE. Native SNN
construction/auditing is `org.jlinalg.sequence.SeuratSnn`. Original function
bodies/formals, IDs, feature dispositions, populations, RNG settings, warnings
and capture completeness remain explicit.

Six synthetic original-R profiles, seven complete captures including replay,
six tests on each Windows/Linux JVM and 26 controls are retained. The admission
rehashes the original public packets/current scientific sources, compares all
23,063 parent JAR payloads and actually re-verifies seven captures in Java.
Original R fitting ran on Windows during the preceding qualification, not this
admission. Java verification does not independently refit every R algorithm.

The independently qualified [native preprocessing](../vignettes/seurat-native-preprocessing.md),
[prepared-layer marker workflows](SEURAT_DIFFERENTIAL_DEVELOPMENT.md) and
[native marker summaries](SEURAT_MARKER_SUMMARY_DEVELOPMENT.md) retain their
original scope. Native summary callbacks are not native statistical P-value
tests. Current source and retained archive checks do not establish full legacy
package execution. The public 0.4.0 release predates these source-build additions.

## Unsupported source roles remain explicit

Anchor integration/transfer, WNN, Harmony, MAST, AddModuleScore, sketch/project,
plots, spatial/protein assay IO and the other listed author operations are not
performed by the qualified RNA workflow. Component overlap only identifies
related supported stages. It never means a complete paper recipe is runnable.
Do not round protein activities/intensities into integer RNA counts, apply a
raw-RNA workflow to externally integrated embeddings, or substitute Wilcoxon/LR
for MAST. Historical edition literals belong to their reported branches; they
are not certifications that those full editions ran here.

P047 and P061 describe adapted methodology, so direct package execution is not
established by those roles. P061's existing observation and review remain exact.
P093 is a comparative benchmark outside popV; P200 is DotPlot on existing atlas
annotations, not new annotation/DE. P083 is background and remains excluded.
P166's null DE result, P096's reported edition/API inconsistency, P063's WNN
ambiguity, P165's parameter-prose problem and other recorded cautions remain.

## Cohort, evidence and cumulative counts

The frozen 250-paper cohort is unchanged: 71 Nature, 104 Nature Genetics and 75
AJHG papers, dated 2023-10-23 through 2026-10-23. The source audit checks every
XML identity, 197 named paragraphs in 51 papers, 50 positive roles (48 direct
package roles and two adapted methods) and one excluded background context.
It appends 49 reviews while retaining the old 1,290 records exactly.

Only P160 is newly added. Its XML paragraph 59 links an author supplement and
names a table; that table label alone is insufficient. The exact 83-page author
PDF (52,222,963 bytes; SHA-256
`d3137b3a674deedd915f68664e777ac610be0a72c11a85a8163e66ca95fcb18b`)
supports the role through PDF pages 27-30, 34, 36 and 55. Printed page numbers
are one less. Pinned pypdfium2 5.13.0 text hashes and the prior Poppler visual
review are bound by [the compact metadata](../../tools/literature/seurat_catalog/P160-SUPPLEMENT.json).
The PDF/full paragraphs stay private. Retain the printed `min.pct.` spelling,
separate custom 150-cell/marker 300-cell limits, and external Harmony/Banksy/
ligand-receptor engines. Source roles do not certify complete author settings.

Legacy regex intake remains unchanged and cannot regenerate this PDF-backed
use from a Methods-only XML scan. The source-aware admission driver requires
the exact supplemental document. Missing evidence skips the dependent audit;
changed evidence fails it. PDF evidence is separate from XML supporting anchors.

After verified application: 2,696 observations, 1,339 reviewed positive uses,
1,357 pending, 131 new scoped integrations (24 native Java + 107 upstream),
134 including three existing adapters, 259 missing and 44 method-overlap
definitions. There remain 446 named families and 485 effective definitions.
These totals describe scoped integration, not 131 whole-tool replications.
Broader implementation and individual source review remain unfinished.

## Reproduce the admission checks

Use Python 3.11+ with pypdfium2 5.13.0, Git and the exact permitted local
sources. From `D:/projects/JLinAlg/build/high-impact-stats-1`:

```powershell
python -B tools/literature/seurat_catalog/test_admission.py
python -B tools/literature/seurat_catalog/catalog.py --plan `
  --primary-cache D:/projects/JLinAlg/build/literature-review/xml `
  --supplement-pdf build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf `
  --candidate-jar build/seurat-workflow-1/qualification-1/omicsmatic-0.4.0.jar `
  --parent-jar build/seurat-high-impact-1/build-1/jar/omicsmatic-0.4.0.jar `
  --java 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' `
  --marker-artifact-root D:/projects/JLinAlg `
  --out build/seurat-catalog-1/plan-NEW
```

PLAN requires the canonical predecessor to be untouched. Inspect its proposed
eight documents and source/qualification/predecessor reports. APPLY uses the
same arguments with `--apply` and another NEW output. It refuses repeated
admission and existing evidence destinations; it does not overwrite history.
It adds the sole exact review-JSON compatibility delta and preserves all
unrelated observations/inventory. The old METAL/LDAK logic runs on actual
retained stage bytes; no source overlays or historical fingerprint refreshes.

After application, rerun the source/evidence verifier:

```powershell
python -B tools/literature/seurat_catalog/catalog.py --verify `
  --primary-cache D:/projects/JLinAlg/build/literature-review/xml `
  --supplement-pdf build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf `
  --out build/seurat-catalog-1/verify-NEW
```

VERIFY without JAR/Java arguments reads retained reader evidence; it does not
rerun Java. Supply the three exact candidate/parent/Java arguments above to
actually re-verify seven captures and every parent payload again. It never
refits R models. Restore the two exact frozen marker development JARs under
`--marker-artifact-root` for a new plan/apply; their historical paths and hashes
are in the original qualification proofs. The old `test_catalog.py` remains a
frozen pre-admission handover test, whose live-state assertion is historical;
use `test_admission.py` for the new before/after stage.

## Warnings and required actions

**SKIP3: the dependent analysis/audit is skipped.** Download/install is allowed
where the environment permits. Supply the exact permitted runtime, source,
backend, reference or artifact at the named path, or leave the work skipped.
Never change requested algorithms, ignore missing original evidence or update
old expected hashes merely to pass a gate.

**FAIL2: no complete result/admission is accepted.** Keep inputs, partials,
failed/superseded evidence and diagnostics, correct the named integrity,
numerical, model, process or capacity cause, and retry into a NEW output path.
Require `results_admitted: true` and successful scientific VERIFY for analyses.
Do not silently repair IDs, NaN/Inf, P values, contributor populations or fits.

Tiny covariate differences (retained maximum `2.22e-16`) produced substantially
different t-SNE coordinates in an older independent fixture. Exact captured
inputs subsequently agreed with the original. Preserve both records and exact
inputs/seeds; do not promise equality across changed inputs/hosts. Hashes prove
integrity, not producer/host authentication or biological validity.

Both literature pages remain withheld from Pages. No release or website
deployment is part of catalog admission. Cell Ranger stays excluded; accepted
FIMO research/nonprofit and GATK 3.8 academic noncommercial terms persist.
Full source papers, private/large evidence and restricted runtimes stay local.

## Individual role crosswalk

Each entry below is a reviewed source role. XML paragraph indices/hashes and
the complete contextual edition records are in the machine-readable census.
All 50 entries explicitly leave historical runtime execution, exact author
settings, complete author-workflow qualification and whole-paper reproduction
false. Unreported editions stay unreported. This is not a paper-wide software
completeness claim.

### P008 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893920/); DOI `10.1038/s41586-025-09875-2`. Selected XML paragraphs: 78 [Methods / Analysis of scRNA-seq data / Cell–cell interaction analysis from scRNA-seq data], 79 [Methods / Analysis of scRNA-seq data / Cell–cell interaction analysis from scRNA-seq data].

FindAllMarkers supplies positive cell-type/sex markers, considering genes detected in at least 10% of cells, for sex-restricted CellPhoneDB interaction analyses.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: marker-workflow

Remaining author roles: unreported-author-test; CellPhoneDB-composition

Source caution: The selected paragraphs do not report a Seurat edition or test backend. CellPhoneDB interaction inference is separate from Seurat marker testing.

### P010 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893904/); DOI `10.1038/s41586-025-09873-4`. Selected XML paragraphs: 90 [Methods / scRNA-seq analysis of circulating T cells], 91 [Methods / scRNA-seq analysis of circulating T cells], 94 [Methods / scRNA-seq and V(D)J-seq analyses of B16-OVA TILs].

Processes blood T cells with normalization, VST features, scaling, PCA, Harmony-based neighbors/UMAP/clusters and FindAllMarkers; a separate TIL workflow uses Seurat RNA/ADT objects and CLR ADT normalization.

Reported editions: 5.3.0 (TIL RNA/ADT and V(D)J processing only)

Qualified component overlap: rna-workflow; native-preprocessing; marker-workflow

Remaining author roles: hashing; Harmony; ADT-assay; reference-projection; TCR-composition

Source caution: The 5.3.0 label belongs to the TIL branch, not automatically the blood analysis. Hashing, Harmony, ADT handling, ProjecTILs and TCR/clonotype analyses are distinct stages.

### P016 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10765522/); DOI `10.1038/s41586-023-06845-4`. Selected XML paragraphs: 32 [Methods / Multiome generation / Generation of aligned multiome data], 2 [GI accessibility changes with neuronal differentiation and genomic compartmentalization].

Analyzes paired olfactory RNA/accessibility data with Seurat and Signac; combines modalities by weighted nearest neighbors, visualizes UMAP and reports SCT-normalized RNA expression.

Reported editions: 4.1.0 (multiome analysis alongside Signac 1.6.0)

Qualified component overlap: sct-normalization; native-snn

Remaining author roles: WNN; multiome-assay; unreported-SCT-flavor

Source caution: WNN is a multimodal analysis, not the current RNA-only graph. The reported 4.1.0 package and author SCT flavor are not reproduced by the current 5.6.0/v2 profile.

### P018 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823447/); DOI `10.1038/s41586-025-09770-w`. Selected XML paragraphs: 52 [Methods / Single-nucleus RNA sequencing / Data preprocessing], 53 [Methods / Single-nucleus RNA sequencing / Data preprocessing], 54 [Methods / Single-nucleus RNA sequencing / Clustering using MapMyCells], 55 [Methods / Single-nucleus RNA sequencing / DEG analyses].

Uses SCTransform for clustering visualization and a separate NormalizeData RNA layer for DE/other analyses; integrates SCT layers with Harmony, attaches MapMyCells labels and runs Wilcoxon FindMarkers with min.pct 0.01 and logFC threshold zero.

Reported editions: 5.1.0 (snRNA-seq analysis)

Qualified component overlap: rna-workflow; sct-normalization; marker-workflow

Remaining author roles: IntegrateLayers; Harmony; external-annotation

Source caution: SCT and RNA layers serve different purposes. IntegrateLayers/Harmony and external MapMyCells labels are not supplied by the RNA-only workflow; do not test the SCT layer in place of the declared RNA layer.

### P031 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872457/); DOI `10.1038/s41586-025-09829-8`. Selected XML paragraphs: 52 [Methods / Sample preparation and scRNA-seq analysis of M cell organoids], 53 [Methods / Re-analysis of primary human intestinal scRNA-seq datasets], 54 [Methods / Re-analysis of primary human intestinal scRNA-seq datasets].

Builds and integrates organoid and published intestinal Seurat objects using anchor features, LogNormalize/VST, PCA/UMAP and SNN clusters; performs M-cell subclustering and Seurat expression plots.

Reported editions: 3.1.4 (M-cell organoid workflow)

Qualified component overlap: rna-workflow; native-snn

Remaining author roles: anchor-integration; existing-object-import; plotting

Source caution: Organoid and reference branches use different integration dimensions. Anchor integration, existing-object import and VlnPlot/DoHeatmap/DimPlot remain outside the current captured RNA recipe.

### P032 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11754113/); DOI `10.1038/s41586-024-08314-y`. Selected XML paragraphs: 36 [Methods / Perturb-seq analysis], 37 [Methods / Perturb-seq analysis], 38 [Methods / Perturb-seq analysis].

Demultiplexes HTOs, applies SCT with cell-cycle regression and CLR to ADTs, uses Harmony and multimodal neighbors, identifies activation genes with FindMarkers, and aggregates raw counts by perturbation/donor for DESeq2.

Reported editions: 4.3.0.1 (Perturb-seq processing)

Qualified component overlap: rna-workflow; sct-normalization; native-preprocessing; marker-workflow

Remaining author roles: hashing; WNN; ADT-assay; Harmony; cell-cycle-scoring; AggregateExpression

Source caution: DESeq2 performs the pseudobulk differential test. Hashing, WNN, ADT assays, Harmony, cell-cycle-score generation and AggregateExpression are separate from the current RNA workflow.

### P039 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10764288/); DOI `10.1038/s41586-023-06837-4`. Selected XML paragraphs: 45 [Methods / Mouse brain analysis / Quality control and cell type assignment], 53 [Methods / Mouse brain analysis / Comparison of Slide-tags snRNA-seq versus snRNA-seq data], 55 [Methods / Mouse brain analysis / Comparison of Slide-tags snRNA-seq with Slide-seqV2 and DBiT-seq], 56 [Methods / Mouse embryonic brain at E14 analysis], 57 [Methods / Human brain analysis / Quality control and cell type assignment], 63 [Methods / Tonsil analysis / Quality control and cell type assignment], 73 [Methods / Melanoma analysis / Quality control and cell type assignment / snRNA-seq data], 74 [Methods / Melanoma analysis / Quality control and cell type assignment / Multiome ATAC and snRNA-seq data], 75 [Methods / Melanoma analysis / Quality control and cell type assignment / Multiome ATAC and snRNA-seq data], 76 [Methods / Melanoma analysis / Inferring CNV], 78 [Methods / Melanoma analysis / ATAC analysis], 80 [Methods / Melanoma analysis / Differential gene expression, differential chromatin gene scores and gene set enrichment analysis], 81 [Methods / Melanoma analysis / Melanocytic-like and mesenchymal-like signatures].

Processes Slide-tags/spatial RNA with CP10K-log normalization, variable genes, PCA/SNN/Louvain/UMAP; transfers reference labels, integrates datasets, constructs RNA/ATAC WNN, tests MAST RNA and Wilcoxon chromatin scores, and computes gene-module scores.

Reported editions: 4.3.0 (multiple Slide-tags RNA analyses)

Qualified component overlap: rna-workflow; native-snn; marker-workflow

Remaining author roles: anchor-integration; label-transfer; WNN; MAST; AddModuleScore; plotting

Source caution: RNA preprocessing overlaps only part of this multi-tissue pipeline. Label transfer, integration, WNN, MAST and AddModuleScore are separate; Wilcoxon is not a replacement for the reported MAST contrasts.

### P040 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645638/); DOI `10.1038/s41586-026-10954-1`. Selected XML paragraphs: 42 [Methods / Single-cell RNA-seq and data analysis].

Log-normalizes counts, selects 3,000 variable genes, scales and computes PCA, uses Harmony and SNN/Louvain clustering (30 PCs, resolution 0.3, k 30), generates UMAP and Wilcoxon FindMarkers for cell-type annotation.

Reported editions: 5.3.0 (mouse tumor/lymph-node scRNA-seq)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: Harmony; study-specific-QC; manual-annotation

Source caution: Harmony correction and study-specific QC/manual annotation are not recreated by the current unsplit RNA recipe; published external datasets also retain their original processing context.

### P043 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719105/); DOI `10.1038/s41586-023-06824-9`. Selected XML paragraphs: 40 [Methods / Integration analysis with scRNA-seq data], 39 [Methods / Integration analysis with scRNA-seq data], 58 [Methods / Predicting GRNs for each cell subclass].

Integrates SnapATAC2-derived gene activity with RNA references using CCA/anchors; tests k.anchor values and selects 50 for label transfer, co-embeds UMAP, and selects variable RNA genes for CellOracle inputs.

Reported editions: v.5 (large-scale cross-modality integration; patch unspecified)

Qualified component overlap: rna-workflow

Remaining author roles: anchor-integration; label-transfer; cross-modality-input; CellOracle-composition

Source caution: This is reference/query cross-modality integration and label transfer, not an RNA-only graph. SnapATAC2 creates gene activities and CellOracle fits GRNs; Seurat is not credited with those computations.

### P046 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668669/); DOI `10.1038/s41586-026-10987-6`. Selected XML paragraphs: 47 [Methods / scRNA-seq analysis of oRG].

Applies 10,000-scale log normalization, scaling and S/G2M regression, then a 30-PC nearest-neighbor graph and Louvain clustering, with low-quality clusters removed before reclustering.

Reported editions: 5.4.0 (oRG scRNA-seq processing)

Qualified component overlap: rna-workflow

Remaining author roles: cell-cycle-scoring; study-specific-QC; manual-annotation

Source caution: CellBender and scDblFinder precede Seurat. Cell-cycle-score generation, low-quality-cluster decisions and biological cell-type annotation are not inferred by the project workflow.

### P047 — method-reimplementation

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719087/); DOI `10.1038/s41586-023-06823-w`. Selected XML paragraphs: 48 [Methods / Transfer of cell labels from one dataset to another with weighted k-nearest neighbours], 49 [Methods / Transfer of cell labels from one dataset to another with weighted k-nearest neighbours], 61 [Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq], 62 [Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq], 63 [Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq], 64 [Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq], 65 [Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq].

Adapts Seurat-v3-like anchor integration and distance-weighted label transfer in ALLCools, including reference subsampling and low-dimensional methylome/transcriptome alignment.

Reported editions: v3 (methodological reference, not a reported executed Seurat runtime)

Qualified component overlap: None in the qualified components.

Remaining author roles: adapted-ALLCools-method

Source caution: The text explicitly locates the adapted label-transfer implementation in ALLCools. Similar equations and anchor scoring do not establish direct Seurat package execution or qualify the adapted ALLCools method.

### P049 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668678/); DOI `10.1038/s41586-026-10981-y`. Selected XML paragraphs: 34 [Methods / Human tissue / Data preprocessing and quality control], 35 [Methods / Human tissue / Clustering and cell type annotation], 36 [Methods / Human tissue / Differential gene expression analysis], 47 [Methods / Animal models / Analysis of published EAE sequencing datasets].

Normalizes/scales RNA, selects variable features and PCA, integrates layers with RPCA, clusters/merges RGC populations using markers and cluster trees, aggregates counts for DESeq2, and runs Wilcoxon FindMarkers in mouse EAE reanalysis.

Reported editions: v.5 (human retinal ganglion cell processing; patch unspecified)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: IntegrateLayers; RPCA-integration; BuildClusterTree; AggregateExpression

Source caution: DESeq2 performs pseudobulk differential testing. IntegrateLayers/RPCA, cluster-tree merging and AggregateExpression are not implemented by the unsplit RNA recipe; author editions/settings remain branch-specific.

### P051 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823425/); DOI `10.1038/s41586-025-09807-0`. Selected XML paragraphs: 81 [Methods / Single-cell transcriptomics on mouse neutrophils / Construction of NeuMap and projection of external data], 82 [Methods / Single-cell transcriptomics on mouse neutrophils / Definition of hubs], 84 [Methods / Single-cell transcriptomics on mouse neutrophils / Signature projection], 85 [Methods / Single-cell transcriptomics on mouse neutrophils / Velocyto analysis], 91 [Methods / Data processing / Initial quality control and cell filtering], 92 [Methods / Data processing / ATAC data annotation], 94 [Methods / Data processing / Mapping onto NeuMap], 96 [Methods / Data processing / Merging of the datasets and peak calling], 105 [Methods / Single-cell transcriptomics on human neutrophils / Data processing and cell annotation], 106 [Methods / Single-cell transcriptomics on human neutrophils / Data integration], 107 [Methods / Single-cell transcriptomics on human neutrophils / Gene module scoring and hub identification], 108 [Methods / Single-cell transcriptomics on human neutrophils / Gene module scoring and hub identification], 110 [Methods / Single-cell transcriptomics on human neutrophils / Data pre-processing], 111 [Methods / Single-cell transcriptomics on human neutrophils / Quality control and data normalization], 116 [Methods / Single-cell transcriptomics on human neutrophils / Downstream analysis], 117 [Methods / Single-cell transcriptomics on human neutrophils / Downstream analysis], 118 [Methods / Single-cell transcriptomics on human neutrophils / Analyses of PDAC and myocardial infarction (MI) models], 126 [Methods / Single-cell transcriptomics on human neutrophils / Spatial analysis of human lung specimens using Visium HD], 127 [Methods / Single-cell transcriptomics on human neutrophils / Neighbourhood analysis].

Constructs NeuMap using Seurat integration/clusters/markers and reference projection; uses multiple Seurat editions for RNA/ATAC/spatial processing, RPCA, AddModuleScore, label transfer, LR markers and SCT/PCA/Leiden/UMAP analyses.

Reported editions: 4.0.5 (mouse reference construction / initial multiome QC); 4.3 (initial multiome QC and spatial downstream/neighborhood branches); 5.1.0 (ATAC/RNA annotation and PDAC/MI spatial analyses); 5.1 (short label for FindTransferAnchors; patch not supplied); 5.2.1 (module scores, object merging and human neutrophil integration); 4.4.0 (spatial input preprocessing)

Qualified component overlap: rna-workflow; sct-normalization; marker-workflow

Remaining author roles: anchor-integration; RPCA-integration; label-transfer; MapQuery; AddModuleScore; spatial-neighborhoods; reported-correction-boundary

Source caution: Keep each reported edition with its stage. The spatial LR paragraph reports BH significance; do not equate that statement with the current marker adapter's Bonferroni output. Integration, projection, module scoring and spatial-neighborhood code remain separate.

### P053 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719095/); DOI `10.1038/s41586-023-06819-6`. Selected XML paragraphs: 48 [Methods / 10x multiome sequence data processing and clustering], 86 [Methods / Analysis of paired-tag RNA and H3K27ac / Integration with human or mouse motor cortex 10x multiome RNA], 87 [Methods / Analysis of paired-tag RNA and H3K27ac / Integration with human or mouse motor cortex 10x multiome RNA].

Runs SCT/PCA, Harmony correction, Louvain/UMAP and reference annotation of multiome RNA; integrates orthologous genes across species by RPCA and maps paired-tag RNA to study references.

Reported editions: v.4 (multiome RNA workflow; patch unspecified)

Qualified component overlap: rna-workflow; sct-normalization

Remaining author roles: Harmony; RPCA-integration; label-transfer; orthology-composition; unreported-SCT-flavor

Source caution: Cross-species orthology, reference mapping, RPCA/Harmony and DoubletFinder are separate stages. The source does not establish the SCT flavor or an exact current-runtime author reproduction.

### P055 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719111/); DOI `10.1038/s41586-023-06818-7`. Selected XML paragraphs: 58 [Methods / Brain preparation / Clustering of snRNA-seq data / Quality control of clusters], 56 [Methods / Brain preparation / Clustering of snRNA-seq data / Visualization of cluster gene expression].

Uses Seurat SNN/Leiden (resolution 0.8) on cell-quality metrics to remove outlier groups, and separately LogNormalize at scale 10,000 for neighbor-averaged gene-expression visualization.

Reported editions: 4.2.0 (SNN/Leiden quality-metric network)

Qualified component overlap: native-snn; native-preprocessing

Remaining author roles: provided-metric-embedding; quality-network; plotting

Source caution: The quality graph is built from QC metrics, not a raw RNA count matrix. Native SNN/normalization overlap does not reproduce the quality-network construction, filtering rules or ComplexHeatmap plots.

### P058 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719099/); DOI `10.1038/s41586-023-06817-8`. Selected XML paragraphs: 45 [Methods / snRNA-seq / snRNA-seq preprocessing, quality control and removal of second-order nuclei], 47 [Methods / snRNA-seq / Clustering and type assignment], 48 [Methods / snRNA-seq / Clustering and type assignment], 49 [Methods / snRNA-seq / Joint clustering of 10x and SSv4 datasets], 53 [Methods / snRNA-seq / Module scores for feature expression programmes], 54 [Methods / snRNA-seq / Differential expression and Gene Ontology analysis], 55 [Methods / snRNA-seq / SCENIC].

Iteratively normalizes RNA, selects VST genes, scales, computes PCA and Louvain/UMAP across resolutions; uses MAST marker tests, SCT-based integration across technologies and AddModuleScore for gene programs.

Reported editions: 4.3.0 (downstream snRNA-seq analysis)

Qualified component overlap: rna-workflow; sct-normalization

Remaining author roles: MAST; SCT-integration; AddModuleScore; SCENIC-composition

Source caution: MAST is not replaced with Wilcoxon/LR. SCT integration, module scoring and downstream SCENIC are distinct scopes; extracting counts from a Seurat object does not make Seurat the GRN engine.

### P061 — method-reimplementation

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719113/); DOI `10.1038/s41586-023-06805-y`. Selected XML paragraphs: 68 [Methods / Atlas-level data integration and cluster annotation / Integration of methylome and transcriptome profiles], 66 [Methods / Atlas-level data integration and cluster annotation], 69 [Methods / Atlas-level data integration and cluster annotation / Integration of methylome and transcriptome profiles], 70 [Methods / Atlas-level data integration and cluster annotation / Integration of methylome and transcriptome profiles].

Adapted anchor finding/scoring for methylome–transcriptome integration.

Reported editions: v.3 (methodological reference, not an established package execution)

Qualified component overlap: None in the qualified components.

Remaining author roles: adapted-atlas-integration

Source caution: The paper describes adapting Seurat methodology; direct Seurat package execution is not established by this paragraph.

### P063 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872466/); DOI `10.1038/s41586-025-09932-w`. Selected XML paragraphs: 23 [Methods / Single-cell multiomics], 24 [Methods / Single-cell multiomics].

Uses HTO demultiplexing, selects 4,000 variable genes, log-normalizes/scales counts, clusters at resolution 0.35 and performs Wilcoxon FindMarkers; reports the clustering as WNN.

Reported editions: 5.3.0 (GEX/ADT/TCR multiomic analysis)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: hashing; reported-WNN-ambiguity; ADT-assay; TCR-composition

Source caution: The source names FindNeighbors/FindClusters while calling the analysis WNN; it does not specify a full FindMultiModalNeighbors configuration. Preserve that ambiguity, hashing and TCR/ADT handling separately.

### P064 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719112/); DOI `10.1038/s41586-023-06638-9`. Selected XML paragraphs: 38 [Methods / Computational analysis], 39 [Methods / Computational analysis / Segregation of major retinal cell classes], 41 [Methods / Computational analysis / Integration and clustering to identify species-specific types for photoreceptors, horizontal cells, bipolar cells and RGCs], 45 [Methods / Computational analysis / Visualization of cell classes], 47 [Methods / Computational analysis / Data integration and identification of orthotypes], 50 [Methods / Statistics and reproducibility].

Normalizes retinal RNA to 10,000, selects variable genes, computes PCA/neighbors and clusters/UMAP; integrates batches and shared orthologues across species to define retinal orthotypes.

Reported editions: 4.3.0 (cross-species retinal workflow)

Qualified component overlap: rna-workflow

Remaining author roles: anchor-integration; orthology-composition; orthotype-merging

Source caution: Cross-species anchor integration, downsampling and orthotype merging are separate. Harmony/Liger/scVI are alternative comparison methods, not silently selected Seurat backends.

### P069 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781646/); DOI `10.1038/s41586-023-06816-9`. Selected XML paragraphs: 27 [Methods / scRNA-seq data pre-processing], 49 [Methods / IREA analysis of human COVID-19 blood scRNA-seq data].

Demultiplexes matched expression/hashtag data with MULTIseqDemux, filters cells and normalizes expression to 10,000 with log transformation; reuses an annotated COVID-19 Seurat object for separate IREA analysis.

Reported editions: 4.1 (mouse expression/hashtag preprocessing; patch unspecified)

Qualified component overlap: native-preprocessing

Remaining author roles: MULTIseqDemux; existing-object-import; IREA-composition

Source caution: Hashtag assignment and existing-object import are not supplied by the RNA recipe. IREA, including homologue conversion and cytokine-response analysis, is not a Seurat calculation.

### P073 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631754/); DOI `10.1038/s41588-024-02019-8`. Selected XML paragraphs: 35 [Methods / scRNA-seq data processing and QC].

Uses Seurat with RCA for initial PBMC annotation/QC, then anchor-based reciprocal PCA integration and marker-guided subclustering of the AIDA cohort.

Reported editions: 4.1.1 (initial annotation/QC workflow)

Qualified component overlap: rna-workflow

Remaining author roles: RCA-composition; RPCA-integration; study-specific-QC

Source caution: Reference annotation, RPCA integration and cohort-specific QC are not inferred by the unsplit RNA recipe. DRAGEN, Cell Ranger and Freemuxlet retain separate preprocessing roles.

### P082 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631769/); DOI `10.1038/s41588-024-01999-x`. Selected XML paragraphs: 39 [Methods / CITE-seq data pre-processing and integration / Quality control of CITE-seq data], 40 [Methods / CITE-seq data pre-processing and integration / Pre-processing and dimensional reduction of CITE-seq data], 41 [Methods / CITE-seq data pre-processing and integration / Weighted nearest neighbor analysis of CITE-seq data].

Processes RNA with SCT/PCA and proteins with CLR/scaling/PCA, combines RNA (30 dimensions) and ADT (18 dimensions) through FindMultiModalNeighbors, and uses weighted graphs for UMAP/t-SNE/clustering.

Reported editions: 4.0.4 (CITE-seq QC)

Qualified component overlap: sct-normalization; native-preprocessing; native-snn

Remaining author roles: WNN; ADT-assay; multimodal-embeddings

Source caution: The 4.0.4 label is stated for QC. RNA and ADT populations/normalizations remain distinct; RNA-only neighbors are not the reported multimodal weighted graph.

### P085 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631771/); DOI `10.1038/s41588-024-01961-x`. Selected XML paragraphs: 49 [Methods / Spatial proteomics data analysis], 31 [Methods / Preprocessing gene expression data], 35 [Methods / ST clustering analysis].

Stores/exports Visium counts and images for BayesSpace analysis; separately log-normalizes real-valued IMC protein intensities, scales/PCA, applies Harmony, clusters and runs Wilcoxon protein-marker comparisons in Seurat.

Reported editions: 4.3.0 (IMC spatial proteomics only)

Qualified component overlap: native-preprocessing

Remaining author roles: real-protein-assay; Harmony; BayesSpace-composition; assay-conversion

Source caution: Protein intensities are not integer RNA counts and must not be rounded for the RNA adapter. BayesSpace performs spatial clustering in the transcriptomic branch; Harmony and assay/container conversion remain separate.

### P087 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649586/); DOI `10.1038/s41588-026-02774-w`. Selected XML paragraphs: 48 [Methods / scRNA-seq analysis / Human data processing, QC and integration], 52 [Methods / scRNA-seq analysis / Human data processing, QC and integration], 53 [Methods / scRNA-seq analysis / Human data processing, QC and integration], 57 [Methods / scRNA-seq analysis / Differential abundance and expression testing], 58 [Methods / scRNA-seq analysis / Cell–cell interaction analysis], 59 [Methods / scRNA-seq analysis / Human biopsy scRNA-seq analysis], 60 [Methods / scRNA-seq analysis / Mouse CCl4 scRNA-seq analysis], 61 [Methods / scRNA-seq analysis / Mouse BDL scRNA-seq analysis], 62 [Methods / scRNA-seq analysis / Mouse BDL scRNA-seq analysis], 66 [Methods / Deconvolution of bulk RNA-seq data], 68 [Methods / Human MDM microarray analysis / Spatial proteomic analysis].

Uses sketch-based atlas integration/projection, RNA preprocessing/clustering, Wilcoxon and MAST markers, reference label transfer and visualization; separately processes CLR-normalized spatial protein intensities with RPCA and reference mapping.

Reported editions: 5.0.0 (human atlas processing); 5.3.1.0000 (human biopsy and mouse BDL processing); 4.1.3 (mouse CCl4 processing); 5.2.1 (spatial protein-intensity analysis)

Qualified component overlap: rna-workflow; marker-workflow; native-preprocessing

Remaining author roles: SketchData; ProjectIntegration; ProjectData; Harmony; MAST; RPCA-integration; label-transfer; MapQuery; real-protein-assay

Source caution: Keep cohort/assay-specific editions. Sketch/project APIs, Harmony, MAST, integration/transfer and real-valued protein workflows are not established by the integer-RNA adapter; BayesPrism and liana/cell2cell are separate engines.

### P088 — direct-package-data-wrangling

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631763/); DOI `10.1038/s41588-024-01975-5`. Selected XML paragraphs: 40 [Methods / Single-cell expression and DE], 59 [Code availability].

Wrangles processed AnnData/Seurat study objects, restricts to nonfailing left-ventricular samples, harmonizes supplied cell labels, conditionally log-normalizes and exports expression/detection summaries weighted across datasets.

Reported editions: v.5.0 (code-availability statement for Seurat data wrangling)

Qualified component overlap: native-preprocessing

Remaining author roles: existing-object-import; cross-dataset-summaries; annotation-harmonization

Source caution: Code availability explicitly names Seurat 5.0, but not every listed summary is assigned a particular Seurat function. Existing annotations are reused; do not relabel this as de novo cell-type classification or a new marker test.

### P093 — comparative-benchmark

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631762/); DOI `10.1038/s41588-024-01993-3`. Selected XML paragraphs: 50 [Methods / Model parameters / Seurat label transfer], 11 [Results / PopV prediction score discriminates high- and low-quality annotations].

Runs Seurat as a popV comparison baseline: VST selection of 2,000 genes, CCA integration anchors, scaled/PCA embeddings, transfer anchors and TransferData label prediction.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: rna-workflow

Remaining author roles: anchor-integration; label-transfer; benchmark-only-role

Source caution: The paper explicitly states Seurat is not part of popV. Benchmark execution does not make Seurat a popV runtime dependency, and its edition is unreported in these selected paragraphs.

### P094 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649600/); DOI `10.1038/s41588-026-02748-y`. Selected XML paragraphs: 32 [Methods / Preprocessing and normalization of joint snRNA/ATAC–seq data], 35 [Methods / Downstream multi-omic analysis and visualizations].

Preprocesses joint RNA/ATAC data with Seurat/Signac and uses Wilcoxon FindAllMarkers/FindMarkers for accessibility contrasts, AverageExpression for heatmaps, and AddModuleScore for trait-associated peak-set activity.

Reported editions: 5.0.0 (joint RNA/ATAC preprocessing alongside Signac)

Qualified component overlap: marker-workflow

Remaining author roles: ATAC-layer-preparation; AverageExpression; AddModuleScore; Signac-composition

Source caution: Seurat objects also host Signac motif/link/coverage analyses; those are not reattributed to Seurat. Prepared ATAC layers, averaging and module scores are separate from the RNA-only workflow.

### P096 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631765/); DOI `10.1038/s41588-024-01990-6`. Selected XML paragraphs: 40 [Methods / snRNA-seq quality control], 41 [Methods / snRNA-seq data normalization, integration and clustering], 42 [Methods / snRNA-seq cluster annotation], 49 [Methods / Analysis of site-of-disease TBM markers], 50 [Methods / snRNA-seq data plots].

Reports Read10X/QC, per-sample SCT with mitochondrial regression, SCT-anchor integration, clustering, reference TransferData annotation and Seurat plots.

Reported editions: v.2.0 (literal reported Seurat edition)

Qualified component overlap: sct-normalization; rna-workflow

Remaining author roles: reported-edition-API-boundary; SCT-integration; label-transfer; plotting

Source caution: Retain the literal reported 2.0 label alongside the named SCT/anchor APIs; their historical edition/function consistency is not established. Do not silently correct the edition or replace integration/transfer with RNA-only steps.

### P099 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703679/); DOI `10.1038/s41588-023-01572-y`. Selected XML paragraphs: 36 [Methods / Cell clustering and annotation analysis], 44 [Methods / Cell filtering, clustering and annotation for EasySci-ATAC].

Co-embeds RNA data with published references for annotation; in the ATAC branch calls FindNeighbors/FindClusters on UMAP coordinates derived from scJoint embeddings to form Louvain annotation groups.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: native-snn

Remaining author roles: co-embedding; provided-embedding; scJoint-composition

Source caution: The ATAC graph input is an external UMAP embedding, not the current RNA PCA input. Monocle 2 performs the reported RNA differential test; Seurat is not credited with that inference.

### P117 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553300/); DOI `10.1038/s41588-026-02737-1`. Selected XML paragraphs: 68 [Methods / Analysis of high-resolution (Stereo-seq and Visium HD) spatial transcriptomics data], 50 [Methods / High-resolution spatial transcriptomics (Stereo-seq) of human adrenal glands], 60 [Methods / Cholesterol balance analysis in bulk scRNA-seq and Visium data], 67 [Methods / Analysis of low-resolution Visium spatial transcriptomics data], 72 [Methods / Tissue-pattern identification in high-resolution spatial transcriptomics data], 73 [Methods / Tissue-pattern identification in high-resolution spatial transcriptomics data], 74 [Methods / Tissue-pattern identification in high-resolution spatial transcriptomics data], 76 [Methods / Cycling cell identification in high-resolution spatial transcriptomics data].

Handles Visium/Stereo-seq objects, integrates spatial samples, normalizes RNA/estimated cell-type expression with SCT, scores cholesterol signatures and uses BANKSY/Harmony-derived assays for PCA/neighbors/clusters/UMAP and tissue patterns.

Reported editions: 5.1.0 (high-resolution spatial analysis); 5.0.1 (human bin SCT normalization)

Qualified component overlap: sct-normalization; rna-workflow

Remaining author roles: spatial-assay; anchor-integration; AddModuleScore; BANKSY-composition; Harmony; deconvolved-expression; cell-cycle-scoring

Source caution: BANKSY assays, deconvolved expression and spatial coordinates are not interchangeable with raw integer RNA counts. Preserve stage-specific editions; AddModuleScore, spatial processing and external RCTD/BANKSY/Harmony remain distinct.

### P124 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703699/); DOI `10.1038/s41588-023-01554-0`. Selected XML paragraphs: 52 [Methods / Processing and analyzing scRNA-seq], 54 [Methods / scRNA-seq analysis of CD19 CAR T cell infusion product for responders and nonresponders].

Processes perturbation RNA and tests gRNA-associated expression with MAST FindMarkers; separately log-normalizes/scales, selects variable genes and clusters CAR-T data before MAST responder/nonresponder comparisons.

Reported editions: 4.1.0 (CRISPRi/CRISPRa perturbation analyses); 4.2.0 (CAR-T product reanalysis)

Qualified component overlap: rna-workflow

Remaining author roles: MAST; gRNA-assignment; study-specific-QC

Source caution: MAST is the reported hurdle-model inference in both contexts. Current Wilcoxon/LR marker adapters are not substitutes; retain distinct editions and study-specific gRNA/sample groupings.

### P134 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553295/); DOI `10.1038/s41588-026-02714-8`. Selected XML paragraphs: 43 [Methods / Single-cell analysis], 22 [Results / Barcode lineage tracing reveals mutation-independent plasticity], 23 [Results / MAPK substates are epigenetically distinct].

Uses Seurat Louvain clustering on metaVIPER-derived protein-activity profiles and reports Seurat-predicted cell-cycle phases when examining pancreatic cancer cell states.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: native-snn

Remaining author roles: provided-protein-activity; cell-cycle-scoring; metaVIPER-composition

Source caution: Protein activities are not raw integer RNA counts. metaVIPER/ARACNe generate activities/networks and Monocle infers pseudotrajectories; neither is a Seurat calculation. The Seurat edition is unreported.

### P144 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597822/); DOI `10.1038/s41588-025-02374-0`. Selected XML paragraphs: 46 [Methods / Analysis of single-cell RNA sequencing and ATAC-seq and calculation of heteroplasmy].

Filters gene-expression cells, normalizes/scales and calls RunUMAP/FindClusters at resolution 0.1 alongside a separate mitochondrial genotyping/heteroplasmy analysis.

Reported editions: 4.3.0 (gene-expression processing)

Qualified component overlap: rna-workflow

Remaining author roles: unreported-author-settings; mgatk-composition

Source caution: mgatk performs genotyping and heteroplasmy estimation. The selected paragraph does not fully specify normalization, feature selection, graph settings or intermediate reduction commands.

### P145 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553309/); DOI `10.1038/s41588-026-02709-5`. Selected XML paragraphs: 50 [Methods / snRNA-seq of hNCs / snRNA-seq data preprocessing and annotation], 47 [Methods / Analysis of snRNA-seq datasets of human brains].

Uses SCT with mitochondrial regression and PCA/SNN/clustering for brain nuclei; in hNCs applies Harmony to SCT PCs, Louvain at resolution 0.5, UMAP and cluster-guided SoupX correction.

Reported editions: 5.2.1 (hNC multiplexed-library analysis)

Qualified component overlap: sct-normalization; rna-workflow

Remaining author roles: Harmony; SoupX-composition; manual-annotation

Source caution: The public-brain reanalysis does not independently report the hNC edition. Harmony and SoupX are separate; fixed-cluster/manual filtering and ambient correction are not inferred from raw counts.

### P153 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447104/); DOI `10.1038/s41588-026-02695-8`. Selected XML paragraphs: 45 [Methods / Cell-type annotation], 44 [Methods / Quality control for 10x Multiome data], 47 [Methods / Kendall correlation].

Uses Seurat/Signac workflows for multiome QC and RNA clustering/annotation; NormalizeData supplies RNA values for custom Kendall accessibility-expression correlations, with Harmony used in PBMC reference alignment.

Reported editions: 5.0.3 (PBMC/pancreatic RNA clustering and annotation)

Qualified component overlap: rna-workflow; native-preprocessing

Remaining author roles: Harmony; custom-Kendall-composition; Signac-composition

Source caution: Signac makes/binarizes the ATAC matrix and study code computes Kendall tau-b; those operations are not credited to Seurat. Dataset-specific QC and Harmony remain separate.

### P158 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597831/); DOI `10.1038/s41588-025-02368-y`. Selected XML paragraphs: 28 [Methods / scATAC-seq and scRNA-seq library preparation and analyses].

Uses Seurat/Signac for ATAC processing and Seurat RNA filtering, per-library SCT on 3,000 features, dataset integration and cell-type-specific Arc-versus-control FindMarkers tests, with MapMyCells annotations.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: sct-normalization; rna-workflow; marker-workflow

Remaining author roles: anchor-integration; external-annotation; unreported-SCT-flavor; unreported-author-test

Source caution: The edition, SCT flavor and marker-test backend are not reported here. The P <= 0.01 statement does not identify a raw versus adjusted field; integration and external annotation remain separate.

### P160 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597827/); DOI `10.1038/s41588-025-02352-6`. Selected XML paragraphs: 59 [Supplementary information].

Analysis evidence is the [author supplement](https://www.ebi.ac.uk/europepmc/webservices/rest/PMC12597827/supplementaryFiles), PDF pages 27-30, 34, 36 and 55. The XML anchor is a document link only.

Uses spatial SCT/PCA/SNN/UMAP and Wilcoxon FindAllMarkers; scales/regresses single-cell RNA, clusters Harmony embeddings, computes AverageExpression and AddModuleScore, and transfers labels to a comparison dataset.

Reported editions: 4.1.1 (spatial Visium analysis in the author supplement); 4.3.0.1 (single-cell RNA processing in the author supplement)

Qualified component overlap: rna-workflow; sct-normalization; marker-workflow

Remaining author roles: Harmony; spatial-assay; AverageExpression; AddModuleScore; label-transfer; author-downsampling

Source caution: Direct use is established by the author supplement, not the XML table label. Keep spatial and single-cell editions separate, retain the printed min.pct. spelling and distinguish custom 150-cell preselection from max.cells.per.ident 300. Harmony, spatial IO, averaging, module scores and label transfer remain separate scopes.

### P162 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447099/); DOI `10.1038/s41588-026-02704-w`. Selected XML paragraphs: 41 [Methods / Functional enrichment and tissue expression analysis].

Processes published thyroid scRNA-seq data and identifies Hashimoto-versus-control DE genes in Seurat; expression-derived cell-type gene sets then support a separate LDSC-SEG analysis.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: marker-workflow

Remaining author roles: unreported-author-settings; unreported-author-test; LDSC-SEG-composition

Source caution: LDSC-SEG supplies heritability enrichment, not Seurat. Seurat edition, preprocessing details and differential-test backend are not stated in the selected paragraph.

### P165 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447109/); DOI `10.1038/s41588-026-02673-0`. Selected XML paragraphs: 54 [Methods / Spatial transcriptomics], 55 [Methods / Transfer of cluster labels], 60 [Methods / Cluster correlation and matching for additional tumorigenesis models].

Processes spatial RNA with SCT and Harmony, identifies cluster markers, transfers reference cluster labels using SCT/FindTransferAnchors/TransferData, and computes cluster averages for cross-model matching.

Reported editions: v5 (cluster-average expression and marker comparison)

Qualified component overlap: sct-normalization; rna-workflow; marker-workflow

Remaining author roles: Harmony; label-transfer; AverageExpression; reported-setting-ambiguity

Source caution: Preserve the source's ambiguous phrase about FindNeighbors and a UMAP dimension of 0.5 rather than inventing a valid integer dimension. Harmony, label transfer and averaging are separate; filter prose and numeric cutoffs are not silently reconciled.

### P166 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525175/); DOI `10.1038/s41588-024-01902-8`. Selected XML paragraphs: 73 [Methods / Single-cell transcription analyses / Single-cell CEL-seq2 UMAP], 74 [Methods / Single-cell transcription analyses / Cbx7–Lap2β versus Lap2β differential expression].

Uses NormalizeData/ScaleData, variable features, PCA and UMAP for single-cell transcription profiles, then FindMarkers for condition contrasts at adjusted P < 0.05; reports no detected DE genes.

Reported editions: 4.1.0 (CEL-seq2 UMAP processing)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: unreported-author-test; null-finding-retention

Source caution: Retain the null DE finding; it is not a missing result. The test backend is not named and the explicit edition is attached to UMAP preprocessing, not separately documented for every contrast.

### P167 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597830/); DOI `10.1038/s41588-025-02353-5`. Selected XML paragraphs: 19 [Methods / CosMx data analysis / Data quality control and processing], 11 [Results / T cell dysfunction in EBV-positive DLBCL], 12 [Results / T cell infiltration in DLBCL of immune-privileged sites], 20 [Methods / CosMx data analysis / Cell type and state identification], 21 [Methods / CosMx data analysis / Cell type and state identification], 26 [Methods / CosMx data analysis / Niche-specific cell functional state analysis], 38 [Methods / Statistics and reproducibility].

Processes CosMx counts with SCT/PCA, Harmony, Louvain/UMAP, Seurat marker tests and plots; AddModuleScore supplies lineage/state signatures and figure legends identify Wilcoxon markers with Bonferroni correction.

Reported editions: 5.0.1 (CosMx transcriptomic workflow)

Qualified component overlap: sct-normalization; rna-workflow; marker-workflow

Remaining author roles: CosMx-input; Harmony; AddModuleScore; plotting

Source caution: CosMx input/coordinates, Harmony, plots and module scores are separate scopes. Preserve the reported Wilcoxon/Bonferroni gene tests instead of borrowing the FDR correction used for other score comparisons.

### P182 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639091/); DOI `10.1016/j.ajhg.2024.10.019`. Selected XML paragraphs: 17 [Methods / scRNA-seq of fetal tissues and preprocessing], 18 [Methods / scRNA-seq of fetal tissues and preprocessing], 19 [Methods / scRNA-seq of fetal tissues and preprocessing], 34 [Methods / Scoring the expression of osteoarthritis gene modules in fetal data and osteoarthritis adult cartilage].

Uses Seurat cell-cycle scoring, log normalization and scaling with cell-cycle/mitochondrial/UMI regression, selects 2,000 variable genes, clusters/UMAP, tests Wilcoxon markers and scores osteoarthritis modules with AddModuleScore.

Reported editions: v.3 (fetal/pediatric/organoid RNA preprocessing)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: cell-cycle-scoring; AddModuleScore; reported-default-boundary

Source caution: CellCycleScoring and AddModuleScore are separate. Retain the paper's stated 25 bins/100 controls in its module-score description without treating that statement as a verified default of the current runtime.

### P187 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12805840/); DOI `10.1016/j.ajhg.2025.11.009`. Selected XML paragraphs: 9 [Material and methods / Nuclei filtering and cell-type annotation], 10 [Material and methods / Nuclei filtering and cell-type annotation], 11 [Material and methods / Nuclei filtering and cell-type annotation], 21 [Material and methods / Mapping eQTL and power analysis].

Filters liver nuclei, log-normalizes RNA, builds PCA/SNN/Louvain/UMAP clusters, integrates corrected data through Harmony/IntegrateLayers and Azimuth labels, then sums donor/cell-type counts with AggregateExpression for eQTL preparation.

Reported editions: v.5 (nuclei QC and donor/cell-type pseudobulk aggregation)

Qualified component overlap: rna-workflow

Remaining author roles: DecontX-composition; IntegrateLayers; Harmony; Azimuth-composition; AggregateExpression

Source caution: DecontX, Harmony and Azimuth are distinct. AggregateExpression produces pseudobulk inputs; DESeq2 normalizes them and FastQTL performs association testing, rather than Seurat.

### P192 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568758/); DOI `10.1016/j.ajhg.2024.10.001`. Selected XML paragraphs: 16 [Methods / Processing of SAT snRNA-seq data in KOBS], 17 [Methods / Processing of SAT snRNA-seq data in RYSA], 18 [Methods / Processing of SAT snRNA-seq data in RYSA], 19 [Methods / Identification of unique cell-type marker genes in KOBS], 29 [Methods / Analysis of unique adipocyte marker gene expression based on the risk allele status of the TBX15 abdominal obesity variant rs10494217], 30 [Methods / Analysis of unique adipocyte marker gene expression based on the risk allele status of the TBX15 abdominal obesity variant rs10494217], 31 [Methods / Module score calculations with the trans genes using SAT snRNA-seq data in KOBS and RYSA].

Processes KOBS/RYSA adipose RNA with log normalization, variable genes, scaling/PCA and Louvain; applies Harmony in RYSA, identifies positive markers and Wilcoxon risk-allele contrasts, and computes trans-gene module scores.

Reported editions: 4.3.0 (KOBS preprocessing / risk-allele expression and module scoring); 4.3.0.1 (RYSA preprocessing and merged data); 4.0.3 (KOBS positive cell-type marker selection)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: Harmony; AddModuleScore; study-specific-grouping

Source caution: Keep three reported edition labels with their stages, and do not silently reinterpret the named FindVariableGenes call as current VST. Donor risk groups, Harmony, ambient/doublet tools and AddModuleScore are separate; Bonferroni rules remain source-specific.

### P200 — direct-package-visualization

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13393102/); DOI `10.1016/j.ajhg.2026.05.011`. Selected XML paragraphs: 20 [Subjects and methods / Human inner ear gene expression].

Imports processed inner-ear atlas matrices with existing cell-type annotations and uses Seurat DotPlot to display detection proportions and scaled mean expression of selected genes.

Reported editions: 5.3.1 (human inner-ear atlas DotPlot visualization)

Qualified component overlap: None in the qualified components.

Remaining author roles: DotPlot; existing-object-import

Source caution: This is visualization of an already annotated atlas, not new cell-type annotation, clustering or differential testing. DotPlot is not implemented by the current RNA workflow or marker-summary interface.

### P213 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13277697/); DOI `10.1016/j.ajhg.2026.04.003`. Selected XML paragraphs: 8 [Methods / OneK1K data and cell-type classification], 12 [Methods / Sex-differential expression analysis], 13 [Methods / Sex-chromosome gene-expression analysis], 14 [Methods / Cell-type gene-marker identification].

Uses SCT/batch correction and Azimuth annotations; tests sex contrasts with Wilcoxon and covariate-adjusted MAST, visualizes sex-chromosome features by UMAP/DimPlot and finds recurrent batch-wise marker genes.

Reported editions: 4.4.0 (OneK1K RNA analyses)

Qualified component overlap: sct-normalization; rna-workflow; marker-workflow

Remaining author roles: MAST; Azimuth-composition; custom-feature-set; batch-recurrence; plotting; MASH-composition

Source caution: Do not replace MAST with LR. MASH performs cross-cell-type shrinkage; authors' zero filling of untested logFCs is not a Seurat result-admission rule. Custom feature selection, batch recurrence/FDR and Azimuth remain separate.

### P223 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339655/); DOI `10.1016/j.ajhg.2024.06.018`. Selected XML paragraphs: 12 [Subjects, material, and methods / scRNA-seq].

Integrates and processes gene-expression and gRNA-presence count matrices in Seurat, using cells with no detectable gRNA as negative controls for the perturbation analysis.

Reported editions: 4.3.0 (joint gene-expression and gRNA-count processing)

Qualified component overlap: None in the qualified components.

Remaining author roles: gRNA-assignment; joint-count-processing; unreported-author-settings

Source caution: The paragraph reports joint processing, not a named anchor-integration algorithm or differential-test backend. Exact recipes are delegated to author code; do not invent them from generic Seurat defaults.

### P225 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339625/); DOI `10.1016/j.ajhg.2024.07.002`. Selected XML paragraphs: 9 [Material and methods / Bioinformatics analysis], 11 [Material and methods / Bioinformatics analysis], 12 [Material and methods / Bioinformatics analysis].

Processes/clusters snRNA-seq and tests Wilcoxon FindMarkers contrasts with logFC > 0.2 and adjusted P < 0.01; hosts Signac multiome assays and uses FindMarkers in the accessibility/motif context.

Reported editions: 4.3.0 (snRNA-seq processing and differential expression)

Qualified component overlap: rna-workflow; marker-workflow

Remaining author roles: ATAC-layer-preparation; Harmony; Signac-composition; motif-assignment-boundary

Source caution: SoupX/scds and Harmony/Signac/chromVAR retain separate roles. GO enrichment uses clusterProfiler and composition uses scCODA; neither is a Seurat calculation. Motif assignment differs from SCENIC and is not silently harmonized.

### P228 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696504/); DOI `10.1016/j.ajhg.2025.07.020`. Selected XML paragraphs: 11 [Material and methods / snRNA-seq and data analysis].

Filters RNA nuclei, normalizes with SCT, integrates samples with HarmonyIntegration, constructs 15-PC neighbors/clusters/UMAP and finds positive cluster markers at log2FC > 0.5 and adjusted P < 0.05.

Reported editions: Not reported in the selected role evidence.

Qualified component overlap: sct-normalization; rna-workflow; marker-workflow

Remaining author roles: Harmony; unreported-SCT-flavor; unreported-author-test; sccomp-composition

Source caution: Seurat edition, SCT flavor and marker backend are unreported here. HarmonyIntegration, scDblFinder and sccomp remain distinct; the stated compound QC criterion is not silently rewritten.

### P233 — direct-package-analysis

[Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461018/); DOI `10.1016/j.ajhg.2025.07.018`. Selected XML paragraphs: 8 [Methods / scRNA-seq data analysis], 9 [Methods / scRNA-seq data analysis], 10 [Methods / Single-cell GNAS genotyping].

Uses Seurat QC, RNA normalization/variable genes/scaling/PCA, Harmony/SNN clustering and PBMC-reference filtering; stromal subclustering uses Liger wrappers and Seurat wrappers export Monocle3 objects.

Reported editions: 5.0.2 (scRNA-seq QC)

Qualified component overlap: rna-workflow

Remaining author roles: Harmony; Liger-composition; Monocle3-conversion; reference-filtering

Source caution: The stated edition is attached to QC. Harmony, Liger iNMF and Monocle3 trajectories are separate engines; targeted GNAS genotyping is study-specific and not performed by Seurat.

