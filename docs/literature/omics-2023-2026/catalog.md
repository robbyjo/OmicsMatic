# Omics and GWAS paper catalog

250 papers; 377 named tools; 2426 paper-tool observations.

Window: 2023-10-23 through 2026-10-23, inclusive. No extension was required.

This is a purposive open-access full-text sample, not a systematic or representative literature review. Methods-section extraction and context checks supply source-linked tool uses. The registry-bounded inventory can omit software or choose a partial description; semantic completeness review remains open. A related OmicsMatic method does not establish upstream package equivalence.

[Searchable offline view](../../../site/literature-review.html) · [Tool inventory](tool-inventory.json) · [Implementation queue](implementation-plan.md) · [Warnings and next actions](WARNINGS.md)

## P001: NAC controls nascent chain fate through tunnel sensing and chaperone action

Nature · 2025-12-22 · [DOI](https://doi.org/10.1038/s41586-025-10058-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13043293/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 1 | Used Bowtie 1.3.1 to exclude ribosomal-RNA reads and align retained reads against the study transcript reference based on WBcel235 gene isoforms. | Methods / Data analysis / Data processing |
| Cutadapt | Removed adapter sequence from demultiplexed ribosome-profiling reads using Cutadapt 1.4.2, before the separate fixed-base trimming step. | Methods / Data analysis / Data processing |
| FASTX-Toolkit | Removed the first 5-prime nucleotide after adapter trimming, using FASTX-Trimmer. | Methods / Data analysis / Data processing |
| riboWaltz | Estimated ribosome-profiling offsets; the analysis excluded fragment lengths without triplet periodicity. | Methods / Data analysis / Data processing |
| STAR | Aligned membrane-fractionation RNA-seq to the study transcript reference; counts supported translation-efficiency and membrane-bound RNA comparisons. | Methods / Data analysis / Membrane fractionation and RNA-seq |

XML SHA-256: `645d5c15b9ceea344d5be2d58a19ccbc7466093b40c8fdfef9d4ac9ebad1f076`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P002: Ancient genomes reveal a deep history of Treponema pallidum in the Americas

Nature · 2024-12-18 · [DOI](https://doi.org/10.1038/s41586-024-08515-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11964931/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Human ancestry analyses |
| BEAST | Bayesian phylogenetic inference and divergence analysis. | Methods / Molecular dating |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Virulence factor analysis |
| BWA | Mapped preprocessed ancient-DNA shotgun/capture reads to GRCh37 with BWA 0.7.12 and seed length 32 inside nf-core/eager. | Methods / Human ancestry analyses |
| Nextflow | Analysis pipeline orchestration. | Methods / Computational screening for T. pallidum DNA |
| nf-core | Reusable sequencing analysis pipelines. | Methods / Computational screening for T. pallidum DNA |
| nf-core/eager | Processed ancient-DNA shotgun and human-enriched capture data using nf-core/eager 2.3.4, coordinating preprocessing/mapping and ANGSD contamination estimation. | Methods / Human ancestry analyses |
| SAMtools | Generated textual pileup from merged ancient-DNA sequences with mapping and base-quality thresholds of 30 and BAQ disabled; a separate pileupCaller step sampled pseudo-haploid genotypes at captured SNPs. | Methods / Human ancestry analyses |
| SnpEff | Variant consequence annotation. | Methods / Virulence factor analysis |
| TreeAnnotator | Posterior tree summary and maximum clade credibility trees. | Methods / Molecular dating |

XML SHA-256: `7970057057eb527abb957f1fb9b21306dc3c3eeb89dcdc4aa3b874df7ee15965`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P003: An integrated view of the structure and function of the human 4D nucleome

Nature · 2025-12-17 · [DOI](https://doi.org/10.1038/s41586-025-09890-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12804090/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Avocado | Reused downloaded Avocado-imputed epigenomic P-value tracks from the 4D Nucleome portal for comparisons with SPIN states. | Methods / Processing other epigenomic data |
| BEDTools | Genomic interval manipulation and overlap. | Methods / GAM methods and data processing / GAM data window calling and sample quality control |
| Bowtie 2 | Mapped GAM-library reads to GRCh38/hg38 using Bowtie 2 2.3.4.3 defaults; nonunique reads, MAPQ below 20 and PCR duplicates were excluded afterward. | Methods / GAM methods and data processing / GAM data sequence alignment |
| cooltools | Chromosome conformation analysis. | Methods / Compartment analysis |
| CrossMap | Genome-assembly coordinate conversion with format-specific interval, alignment and variant handling. | Methods / Consensus chromatin-state annotations for H1 and HFFc6 cells |
| Cutadapt | Adapter trimming. | Methods / Chromatin dataset processing |
| deepTools | Computed CUT&Run coverage after Cutadapt, local Bowtie2 mapping and SAMtools quality/duplicate/ENCODE-blacklist filtering; HOMER supplied the later1Mb IZ-centered average profiles. | Methods / Chromatin dataset processing |
| FIMO | Scanned hg38 with FIMO for JASPAR POU2F1-SOX2 MA1962.1 and FOSL1-JUND MA1142.1 sites at P < 1e-5. Selected sites overlapping cell-type-specific Micro-C TAD boundaries with bedtools, then replaced motifs with random sequence for Akita in silico mutagenesis. | Methods / Methods for predicting Hi-C maps from sequence (Akita) |
| HISAT2 | Mapped H1-cell RNA-seq reads to hg38 with HISAT2 before SAMtools quality filtering and bedtools coverage/signal extraction at initiation zones and random regions. | Methods / IZ integration with chromatin and transcription |
| Integrative Genomics Viewer (IGV) | Plotted the CUT&Run chromatin profile with IGV and the WashU browser across the reported chromosome2 interval20404583–58108703. | Methods / Chromatin dataset processing |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / UMAP projection and clustering of chromatin loops |
| MACS | Generated CUT&RUN peaks and P-value tracks with MACS3 after hg38 Bowtie2 alignment. Compared SPIN-state signal with the genome-wide expectation using log2(mean state signal / mean genome signal). | Methods / Processing other epigenomic data |
| MAPS | PLAC-seq chromatin-interaction calling. | Methods / Identification of chromatin loops in different platforms |
| RSEM | Transcript abundance estimation. | Methods / Categorization of genes by expression levels |
| SAMtools | Filtered mapped CUT&Run alignments for quality, duplicates and ENCODE blacklisted regions before deepTools coverage calculation. | Methods / Chromatin dataset processing |
| t-SNE | Mapped six standardized chromatin-structure measurements into two dimensions with scikit-learn; perplexity 80, 300 iterations, seed 123. | Methods / Dimension reduction of structural features with t-SNE |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / UMAP projection and clustering of chromatin loops |

XML SHA-256: `ba8bdba91bac062550a30b47fa2f0dba8a5caef915fedf15ee341d4c5bda6370`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P004: Emergence of replication timing during early mammalian development

Nature · 2023-12-20 · [DOI](https://doi.org/10.1038/s41586-023-06872-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781638/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Single-cell Repli-seq read alignment and quality control filtering |
| Bowtie 2 | Mapped sequencing reads to mm10 with Bowtie 2 2.3.5 local alignment, before SAMtools duplicate marking/MAPQ selection and BEDTools genomic-bin counting. | Methods / Single-cell Repli-seq read alignment and quality control filtering |
| Cutadapt | Adapter trimming. | Methods / Analysis of public chromatin datasets |
| DESeq2 | Count-based differential expression analysis. | Methods / Analysis of public chromatin datasets |
| SAMtools | Marked duplicates using SAMtools 1.9 fixmate/sort/markdup, then retained properly paired, nonduplicate reads with mapping quality at least 20 before BEDTools genomic-bin counting and chromosome-imbalance QC. | Methods / Single-cell Repli-seq read alignment and quality control filtering |

XML SHA-256: `4ecc5a42095be0ab3723128d5bf6bf892f2171c86abec4835ea0aa7bf64b6bfb`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P005: Lesion-remote astrocytes govern microglia-mediated white matter repair

Nature · 2025-12-17 · [DOI](https://doi.org/10.1038/s41586-025-09887-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823418/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| cell2location | Spatial cell-type deconvolution. | Methods / Spatial transcriptomics analysis |
| Cell Ranger | Aligned sample snRNA-seq FASTQs with CellRanger against mm10-2020-A before independent cell/gene QC and analysis. | Methods / snRNA-seq data analysis |
| DIA-NN | Data-independent acquisition proteomic quantification. | Methods / CCN1 co-immunoprecipitation / Proteomic data analysis |
| edgeR | Count-based differential expression analysis. | Methods / Astrocyte ribosome-associated mRNA isolation, RNA-seq and analysis |
| Enrichr | Gene-set enrichment. | Methods / Astrocyte ribosome-associated mRNA isolation, RNA-seq and analysis |
| Harmony | Batch integration of cell embeddings. | Methods / snRNA-seq data analysis |
| MetaboAnalyst | Metabolomic statistics and pathway analysis. | Methods / CCN1 co-immunoprecipitation / Proteomic data analysis |
| NicheNet | Ligand-target communication modelling. | Methods / snRNA-seq data analysis |
| Scrublet | Removed predicted doublets independently per sample, after mitochondrial/count and gene-prevalence QC; concatenation, normalization to 10000, logarithm and Harmony followed. | Methods / snRNA-seq data analysis |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Spatial transcriptomics analysis |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Astrocyte ribosome-associated mRNA isolation, RNA-seq and analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Spatial transcriptomics analysis |

XML SHA-256: `e77488573a78e002932ba5dd67336204b01eed1e60235a00b4e57c442179e74e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P006: Lithocholic acid binds TULP3 to activate sirtuins and AMPK to slow down ageing

Nature · 2024-12-18 · [DOI](https://doi.org/10.1038/s41586-024-08348-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12222023/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Determination of the binding sites of TULP3 for LCA |
| AutoDock Vina | Molecular docking. | Methods / Determination of the binding sites of TULP3 for LCA |
| PyMOL | Illustrated AutoDock Vina docking results for LCA and AlphaFold-predicted TULP3 using PyMOL v2.5. | Methods / Determination of the binding sites of TULP3 for LCA |

XML SHA-256: `745aee7367f340c234f320ce930e9b9e3b7d53db9cb48086cf5efdc9e45850d1`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P007: The gene-regulatory evolution of the human skeleton

Nature · 2026-09-23 · [DOI](https://doi.org/10.1038/s41586-026-11053-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668659/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / MPRA design / Library design |
| BEDTools | Genomic interval manipulation and overlap. | Methods / MPRA design / Library design |
| Bowtie 2 | Aligned paired-end chondrocyte ATAC-seq/CUT&Tag reads to GRCh38 using Bowtie 2 2.5.1 very-sensitive alignment with dovetail allowed and insert bounds 0 to 700, before Picard duplicate removal and MACS3 peaks. | Methods / Chondrocyte ATAC-seq and CUT&Tag |
| DESeq2 | Count-based differential expression analysis. | Methods / Identification of gene expression differences |
| fastp | Removed adapters and quality-trimmed ATAC-seq/CUT&Tag reads using fastp 1.0.1 defaults before Bowtie2 mapping. | Methods / Chondrocyte ATAC-seq and CUT&Tag |
| FastQC | Assessed 80-base paired-end ATAC-seq/CUT&Tag sequencing quality with FastQC 0.12.1 before fastp trimming. | Methods / Chondrocyte ATAC-seq and CUT&Tag |
| FIMO | Generated reference and alternative allele FASTA sequences centred on each variant, with length set by the longest supplied motif, and ran fimo --text --thresh 1e-4 for TFs expressed in chondrocytes. To compare both alleles when one lacked a reported match, separately recomputed PWM scores for all sequences and calibrated P values empirically against background scores; differential binding was human minus ancestral log score. A separate coding-TF analysis reported FDR > 0.05 for ZFAT/ZFHX4. | Methods / TF-binding sites / Predicted-motif-based approach (FIMO) |
| Hornet | Ran the Hornet modified-WASP workflow linked by the paper to filter allele-swapped reads for stable remapping positions in hybrid human/chimpanzee or human/gorilla expression analysis. | Methods / Identification of gene expression differences |
| MACS | Called ATAC-seq and CUT&Tag peaks using MACS3 3.0.1 on GRCh38 paired-end alignments after fastp, Bowtie2 and Picard duplicate removal; reported BAMPE, human genome shortcut, nomodel and q-value 0.05. | Methods / Chondrocyte ATAC-seq and CUT&Tag |
| MEME | Motif discovery and scanning. | Methods / TF-binding sites / Predicted-motif-based approach (FIMO) |
| Picard | Removed PCR duplicates with Picard MarkDuplicates 3.0.0 after GRCh38 Bowtie2 alignment of paired-end chondrocyte ATAC-seq/CUT&Tag reads, before MACS3 peak calling. | Methods / Chondrocyte ATAC-seq and CUT&Tag |
| RepeatMasker | Repetitive sequence annotation. | Methods / MPRA design / Library design |
| STAR | Aligned hybrid-cell reads independently against the human/chimpanzee or human/gorilla references using STAR 2.7.11b, MD/NH tags, unique mapping, GTF junctions and sjdbOverhang 149. Repeated mapping with first-round splice junctions before separate duplicate removal and allele-swap mapping-bias controls. | Methods / Identification of gene expression differences |
| UCSC liftOver | Converted coordinates between genome assemblies during MPRA library design; GRCh37 was otherwise the stated reference. | Methods / MPRA design / Library design |
| WASP | Used a modified WASP pipeline to retain reads mapping to the same position after in silico allele swapping in cross-species hybrid-cell allele-specific expression analysis; indel-overlapping reads were discarded separately. | Methods / Identification of gene expression differences |

XML SHA-256: `43d758034dbfdfb6c544ff9c48b4fea6d41503aac53586732200e568af88c25c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P008: Spatiotemporal cellular map of the developing human reproductive tract

Nature · 2025-12-17 · [DOI](https://doi.org/10.1038/s41586-025-09875-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893920/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ArchR | Single-cell ATAC-seq analysis. | Methods / Analysis of scATAC–seq data / Per-library analyses |
| cell2location | Spatial cell-type deconvolution. | Methods / Analysis of 10x Visium data / Estimation of cell-type abundances per Visium spot using matched scRNA-seq data |
| CellBender | Ambient count background removal. | Methods / Analysis of scRNA-seq data / Per-library analyses |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Methods / Analysis of scRNA-seq data / Cell–cell interaction analysis from scRNA-seq data |
| Cell Ranger | Processed scATAC-seq libraries with Cell Ranger ATAC v2 for read filtering, alignment, barcode counting and cell calling against the 10x GRCh38 reference v3.1.0. | Methods / Analysis of scATAC–seq data / Per-library analyses |
| CellTypist | Reference-based cell-type classification. | Methods / Analysis of scRNA-seq data / In vivo–in vitro comparison |
| Enrichr | Gene-set enrichment. | Methods / Analysis of scRNA-seq data / In vivo–in vitro comparison |
| Harmony | Batch integration of cell embeddings. | Methods / Analysis of scRNA-seq data / Per-organ analyses |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Analysis of scRNA-seq data / Per-library analyses |
| MACS | Called cell-type pseudobulk ATAC peaks with MACS2 through pycisTopic, then constructed consensus regions for topic modelling, donor correction and motif analyses. | Methods / Analysis of scATAC–seq data / Integrative analysis of scRNA-seq and scATAC–seq data |
| Milo | Single-cell neighborhood differential abundance. | Methods / Analysis of scRNA-seq data / Human–mouse comparison of external genitalia |
| Nextflow | Analysis pipeline orchestration. | Methods / Analysis of ISS data / Preprocessing |
| pycisTarget | Searched transcription-factor motifs in the union of differential-accessibility regions and binarized topic regions. | Methods / Analysis of scATAC–seq data / Integrative analysis of scRNA-seq and scATAC–seq data |
| pycisTopic | Generated cell-type pseudobulk MACS2/consensus ATAC peaks, fit LDA topic models, selected models by pycisTopic metrics and obtained regions for motif analysis. | Methods / Analysis of scATAC–seq data / Integrative analysis of scRNA-seq and scATAC–seq data |
| Scanpy | Single-cell expression processing and analysis. | Methods / Analysis of 10x Visium data / Scoring of the fetal fallopian tube decreasing signature in adult data |
| SCENIC+ | Multiomic regulatory-network tools and motif reference resources. | Methods / Analysis of scATAC–seq data / Integrative analysis of scRNA-seq and scATAC–seq data |
| Scrublet | Called doublets per library using Scrublet with a cited two-step diffusion procedure; the source reports a greater-than-40-percent Scrublet-score filter alongside gene and mitochondrial QC. | Methods / Analysis of scRNA-seq data / Per-library analyses |
| scVI | Variational single-cell integration or expression modelling. | Methods / Analysis of scRNA-seq data / Integration of scRNA-seq libraries |
| Seurat | Used for cell clustering; marker or differential-expression analysis; cell annotation. | Methods / Analysis of scRNA-seq data / Cell–cell interaction analysis from scRNA-seq data |
| Slingshot | Lineage and pseudotime inference. | Methods / Analysis of scRNA-seq data / Trajectory inference and differential expression along trajectories |
| souporcell | Genotype-based cell demultiplexing. | Methods / Analysis of scRNA-seq data / Per-library analyses |
| SoupX | Ambient RNA contamination correction. | Methods / Analysis of scRNA-seq data / Per-library analyses |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Analysis of 10x Visium data / Per-library analyses |
| Squidpy | Spatial neighborhood and expression analysis. | Methods / Analysis of 10x Visium data / Per-library analyses |
| STARsolo | Aligned each single-cell RNA-seq library to human GRCh38 2020-A and generated initial mRNA quantification/QC with default STARsolo, before CellBender ambient-RNA subtraction, Souporcell donor assignment and Scrublet doublet calling. | Methods / Analysis of scRNA-seq data / Per-library analyses |
| tradeSeq | Trajectory-dependent differential expression. | Methods / Analysis of scRNA-seq data / Trajectory inference and differential expression along trajectories |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Analysis of scRNA-seq data / Per-library analyses |

XML SHA-256: `792ff0b362991b7300468688b3d8e83c611b8c0848de179549300764e829fd0a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P009: Spatial transcriptomic clocks reveal cell proximity effects in brain ageing

Nature · 2024-12-18 · [DOI](https://doi.org/10.1038/s41586-024-08334-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11798877/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Enrichr | Gene-set enrichment. | Methods / TISSUE differential gene expression analysis and pathway enrichment |
| gseapy | Python gene-set enrichment analysis. | Methods / TISSUE differential gene expression analysis and pathway enrichment |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Cell-type clustering and identification |
| Scanpy | Used for expression normalization; cell clustering. | Methods / Cell-type clustering and identification |
| Scrublet | Applied Scrublet separately per MERFISH experiment with a score cutoff of 0.18, before segmentation-volume and expression filters and volume/total-count normalization. | Methods / Cell segmentation and MERFISH data preprocessing |
| Squidpy | Spatial neighborhood and expression analysis. | Methods / Cell proximity effects of ageing and rejuvenation |
| Tangram | Mapping single-cell expression to spatial measurements. | Methods / TISSUE imputation |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Cell-type clustering and identification |

XML SHA-256: `2941b45e600f691aa74a50fd21aab7fdaedc8334713e21b88e5762361df94686`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P010: Transient hepatic reconstitution of trophic factors enhances aged immunity

Nature · 2025-12-17 · [DOI](https://doi.org/10.1038/s41586-025-09873-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893904/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Methods / Receptor–ligand analysis |
| Cell Ranger | Processed and aligned 5-prime single-cell sequencing reads to mouse GRCm39/mm39 using CellRanger v7.1.0 in the dissociated-cell and B16-OVA TIL experiments. | Methods / Dissociated scRNA-seq and data preprocessing |
| Harmony | Batch integration of cell embeddings. | Methods / scRNA-seq analysis of circulating T cells |
| Scanpy | Used for expression normalization. | Methods / STARmap data analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / scRNA-seq analysis of circulating T cells |
| Squidpy | Spatial neighborhood and expression analysis. | Methods / Receptor–ligand analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / scRNA-seq analysis of circulating T cells |

XML SHA-256: `0a03a6d37c2754eeac3b5b2b5f04972b65fff7a274e54f3648db9ea255942a5e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P011: MMEJ repair of breaks at TA repeats maintains ecDNA and cancer fitness

Nature · 2026-09-23 · [DOI](https://doi.org/10.1038/s41586-026-11048-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645644/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BAMscale | Generated reads-per-million-normalized coverage tracks for END-seq visualization/downstream analysis using BAMscale. | Methods / END-seq analysis |
| BEDTools | Genomic interval manipulation and overlap. | Methods / END-seq analysis |
| Bowtie 2 | Short-read alignment. | Methods / END-seq analysis |
| BWA | Aligned DLP+ single-cell whole-genome reads with BWA-MEM as part of Mondrian processing within Isabl, before joint DeStruct structural-variant analysis. | Methods / Direct library preparation |
| DeStruct | Called structural variants jointly across four DLP+ populations with DeStruct, then retained sample-specific events and required at least three supporting reads outside specified amplicon chromosomes. | Methods / Direct library preparation |
| ICE Analysis | Estimated CRISPR-Cas9 FANCM knockout efficiency using ICE Analysis version 3 on Sanger sequencing of the targeted cut site, with weekly monitoring. | Methods / Direct library preparation |
| Isabl | Used the Isabl platform to host/orchestrate the Mondrian single-cell DNA analysis workflow. | Methods / Direct library preparation |
| MACS | Called END-seq peaks with MACS2 2.2.7.1 after hg19 alignment and BAM-to-BED conversion: keep all duplicates, no model, shift -50, extension 100, q-value 0.001 and fold-enrichment cutoff 5. | Methods / END-seq analysis |
| Mondrian | Processed DLP+ single-cell whole-genome sequencing with the publicly available Mondrian pipeline, including BWA-MEM alignment and joint DeStruct calling within Isabl. | Methods / Direct library preparation |
| RepeatMasker | Repetitive sequence annotation. | Methods / Sequence analysis of the breakpoint of rearrangement junctions in human tumours |
| Trimmomatic | Trimmed END-seq reads with Trimmomatic0.39 before Bowtie2 alignment to hg19. The paragraph does not specify trimming steps or thresholds. | Methods / END-seq analysis |

XML SHA-256: `d726c83286905d60fbca3f0c218db7c67a575dc6b8922a05c04ff2fda2b3886e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P012: The PfRCR complex bridges malaria parasite and erythrocyte during invasion

Nature · 2023-12-20 · [DOI](https://doi.org/10.1038/s41586-023-06856-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10794152/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Model building and refinement |
| ChimeraX | Molecular structure visualization and model building. | Methods / Model building and refinement |

XML SHA-256: `b9d8f60a2730819b0468fc9db5395368cd55566e731b211338ab87120ccff876`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P013: Nucleosome fibre topology guides transcription factor binding to enhancers

Nature · 2024-12-18 · [DOI](https://doi.org/10.1038/s41586-024-08333-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11798873/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Bioinformatics / Protein structure visualization |
| APBS | Calculated protein electrostatic surface charge with the APBS plugin in PyMOL after separate structure prediction and alignment steps. | Methods / Bioinformatics / Protein structure visualization |
| BEDOPS | Converted signal from WIG to BED with wig2bed and used bedmap to quantify RPGC-normalized tag density in 301-base windows around MACS2 summits. | Methods / Bioinformatics / Read density analysis |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Bioinformatics / ChIP–seq peak calling |
| Bowtie 2 | Short-read alignment. | Methods / Bioinformatics / Sequencing data processing and alignment |
| Cutadapt | Adapter trimming. | Methods / Bioinformatics / Sequencing data processing and alignment |
| deepTools | Generated RPGC1x tracks with bamCoverage,10bp bins and extendReads, excluding chromosomeX from normalization. Used center-referenced10bp matrix bins over±1kb, original region order, missing-as-zero and per-bin sums; plotHeatmap/plotProfile rendered matrices. bigwigCompare subtracted RPGC input from H1 signal. | Methods / Bioinformatics / Gene expression analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / Bioinformatics / Gene expression analysis |
| FastQC | Sequencing read quality assessment. | Methods / Bioinformatics / Sequencing data processing and alignment |
| featureCounts | Used the R featureCounts interface in RsubRead to quantify mouse RNA-seq against UCSC mm9 GTF, combining single- and paired-end count tables. | Methods / Bioinformatics / Gene expression analysis |
| FitHiChIP | Peak-anchored chromatin interaction calling. | Methods / Bioinformatics / Micro-C loop calling and cis-interactions between ChIP–seq peaks |
| GenomicRanges | Genomic-range containers and overlap operations in R. | Methods / Bioinformatics / Motif analysis on TF-bound nucleosome arrays |
| GREAT | Regulatory-region functional enrichment. | Methods / Bioinformatics / Genomic intervals |
| Integrative Genomics Viewer (IGV) | Generated genome-track screenshots in Integrative Genomics Viewer from genome-coverage-normalized(RPGC) data. | Methods / Bioinformatics / Genome tracks visualizations |
| IRanges | Integer-range containers and overlap operations in R. | Methods / Bioinformatics / Motif position analysis on TF-bound nucleosomes |
| MACS | Called ChIP-seq narrow peaks/summits against input DNA at q-value 0.01 and broad TF peaks with bedGraph output, broad cutoff 0.1, no model and extension 200. Used MACS2 summits for subsequent fixed-width signal aggregation. | Methods / Bioinformatics / ChIP–seq peak calling |
| MEME | Motif discovery and scanning. | Methods / Bioinformatics / Motif discovery |
| Nextflow | Analysis pipeline orchestration. | Methods / Bioinformatics / Sequencing data processing and alignment |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Bioinformatics / Sequencing data processing and alignment |
| PyMOL | Visualized a mononucleosome based on PDB5NL0 with PyMOL v3.0; separately aligned the predicted MYC/MAX-TFAP2C complex to PDB8J0K with align and calculated electrostatic surface charge using the APBS plugin. | Methods / Bioinformatics / Protein structure visualization |
| Rsubread | Used RsubRead featureCounts to quantify mouse RNA-seq against mm9 GTF. | Methods / Bioinformatics / Gene expression analysis |
| SAMtools | Alignment manipulation and QC. | Methods / Bioinformatics / Sequencing data processing and alignment |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Bioinformatics / Sequencing data processing and alignment |
| UCSC bedGraphToBigWig | Conversion of genomic signal tracks to indexed bigWig. | Methods / Bioinformatics / Read density analysis |
| UCSC bigWigToWig | Conversion of indexed bigWig signal to WIG. | Methods / Bioinformatics / Read density analysis |

XML SHA-256: `4787955e5434581fd5843e30b9ebacc89fb4cac5b4021de23d36ddfdd0d91eda`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P014: Palaeometabolomes yield biological and ecological profiles at early human sites

Nature · 2025-12-17 · [DOI](https://doi.org/10.1038/s41586-025-09843-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12851940/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| MaxQuant | Mass-spectrometry proteomic identification and quantification. | Methods / Proteomics analysis |

XML SHA-256: `3113c865c0d28488d22c790f0a412262f0f14d1492d46e5c9a5bbf898eb64b76`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P015: Timely TGFβ signalling inhibition induces notochord

Nature · 2024-12-18 · [DOI](https://doi.org/10.1038/s41586-024-08332-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11735409/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Aligned chick reads to a custom GRCg6a.101 10x reference and posterior-neuruloid reads to human GRCh38-3.0.0 using CellRanger v4.0.0. | Methods / Chick scRNA-seq analysis |
| DropEst | Generated chick droplet RNA counts using DropEst 0.8.6 in the preprocessing workflow. | Methods / Chick scRNA-seq analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Chick scRNA-seq analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Chick scRNA-seq analysis |
| Louvain | Grouped observations into communities using a neighborhood graph. | Single-cell analysis of developing trunk |
| Scanpy | Single-cell expression processing and analysis. | Methods / Chick scRNA-seq analysis |
| Scrublet | Removed predicted chick-cell doublets by developmental stage with Scrublet thresholds between 0.2 and 0.3 after CellRanger or DropEst count generation. | Methods / Chick scRNA-seq analysis |
| Slingshot | Lineage and pseudotime inference. | Methods / Chick scRNA-seq analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Chick scRNA-seq analysis |

XML SHA-256: `a7b157b15a421557819879f6e5b98d8157313bf114b4ce05a7674c83b812ba30`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P016: RNA-mediated symmetry breaking enables singular olfactory receptor choice

Nature · 2023-12-20 · [DOI](https://doi.org/10.1038/s41586-023-06845-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10765522/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / ATAC-seq / ATAC-seq data processing |
| BWA | DNA read alignment. | Methods / Dip-C generation / Dip-C and data preprocessing |
| Cell Ranger | Demultiplexed multiome data with cellranger-arc mkfastq, built an mm10 reference with extended olfactory-receptor GTF annotations using mkref, and aligned with count. | Methods / Multiome generation / Generation of aligned multiome data |
| Cutadapt | Adapter trimming. | Methods / ATAC-seq / ATAC-seq data processing |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq / RNA-seq data processing and analysis |
| HOMER | Motif and regulatory enrichment analysis. | Methods / ATAC-seq / ATAC-seq data processing |
| Nextflow | Analysis pipeline orchestration. | Methods / In situ Hi-C, liquid Hi-C and H3K27ac HiChIP / In situ Hi-C, liquid Hi-C and HiChIP alignment and data preprocessing |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / ATAC-seq / ATAC-seq data processing |
| PyMOL | Reported three-dimensional reconstruction of Dip-C models in PyMOL v2.5.3, following a cited prior procedure. | Methods / Dip-C generation / Dip-C and data preprocessing |
| SAMtools | Alignment manipulation and QC. | Methods / RNA-seq / RNA-seq data processing and analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Multiome generation / Generation of aligned multiome data |
| Signac | Single-cell chromatin accessibility analysis. | Methods / Multiome generation / Generation of aligned multiome data |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA-seq / RNA-seq data processing and analysis |

XML SHA-256: `1d7aea455ed0d0bc55bf1eae2a7f006b8f0d1f980be46c5673479114bff121ee`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P017: Functional and evolutionary significance of unknown genes from uncultivated taxa

Nature · 2023-12-18 · [DOI](https://doi.org/10.1038/s41586-023-06955-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10849945/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DIAMOND | Protein sequence alignment. | Methods / Detection of protein clusters specifically from uncultivated taxa |
| t-SNE | Visualized log-transformed gene-family abundances with scikit-learn after DIAMOND-based homologue mapping and prevalence-based family/sample selection. | Methods / Ecological distribution of gene families |

XML SHA-256: `30ec0e5d53a74c8542bbad741265fdc9b5cce616a96020c80a8c67d81b29bf1c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P018: Astrocyte CCN1 stabilizes neural circuits in the adult brain

Nature · 2025-12-17 · [DOI](https://doi.org/10.1038/s41586-025-09770-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823447/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Azimuth | Reference mapping and cell annotation. | Methods / In vivo two-photon imaging / Binocular zone confirmation |
| Cell Ranger | Mapped snRNA-seq reads to mm10 with CellRanger count v8.0.1 and separately aggregated biological replicates with CellRanger aggr v8.0.1 using reported default normalization. | Methods / Single-nucleus RNA sequencing / Data preprocessing |
| clusterProfiler | Performed bulk GSEA on genes ranked by descending log2 fold change against GO, Reactome and KEGG; bulk ORA used up/down DEG lists and genes with mean FPKM above one in at least one group. Applied BH-adjusted P below 0.05 and q below 0.01 for bulk ORA; separate nuclear enrichGO used 4.12.6. | Methods / Bulk RNA sequencing / Processing and analysis / Pathway analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / Bulk RNA sequencing / Processing and analysis / RNA-sequencing mapping, analysis and statistics |
| GSEA | Ranked gene-set enrichment. | Methods / Bulk RNA sequencing / Processing and analysis / Pathway analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Single-nucleus RNA sequencing / Data preprocessing |
| MapMyCells | Reference-based cell annotation. | Methods / Single-nucleus RNA sequencing / Clustering using MapMyCells |
| sctransform | Used Seurat 5.1 SCTransform defaults for clustering and visualization after CellRanger 8.0.1 aggregation; differential expression and other analyses used a separately NormalizeData-processed RNA layer. | Methods / Single-nucleus RNA sequencing / Data preprocessing |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Single-nucleus RNA sequencing / Data preprocessing |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Bulk RNA sequencing / Processing and analysis / RNA-sequencing mapping, analysis and statistics |
| UCell | Rank-based single-cell gene-set scoring. | Methods / Single-nucleus RNA sequencing / Gene signatures |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Single-nucleus RNA sequencing / Data preprocessing |

XML SHA-256: `481dd1887424526f042a1651da067eb836e52d1a90ac370277b468a13acc6e9b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P019: Single-nucleus transcriptome-wide association study of human brain disorders

Nature · 2026-09-23 · [DOI](https://doi.org/10.1038/s41586-026-10836-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600940/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| FastQTL | Permutation-based molecular QTL mapping. | Methods / Training of snTIMs / Genotype and gene expression quality control for TIM training / Gene expression quality control |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Defined previously known gene-trait associations using FUMA SNP2GENE/MAGMA and bulk TWAS, with FDR across traits; gene type all, custom MHC coordinates and symmetric50kb windows. | Methods / Enrichment of novel genes |
| KING | Genetic kinship and relatedness estimation. | Methods / I-TWAS and snGReX-PheWAS in MVP / MVP genotype quality control |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Comparison of MG AD snTWAS with differential gene expression signatures associated with AD pathology |
| MAGMA | Defined previously known gene-trait associations using FUMA SNP2GENE/MAGMA and bulk TWAS, with FDR across traits; gene type all, custom MHC coordinates and symmetric50kb windows. | Methods / Enrichment of novel genes |
| mashr | Multivariate adaptive shrinkage. | Methods / mash Analysis |
| Minimac | Genotype imputation. | Methods / I-TWAS and snGReX-PheWAS in MVP / MVP genotype quality control |
| MungeSumstats | Used liftover within MungeSumstats to convert GWAS summaries to GRCh37 as required by FUMA/MAGMA before defining novel gene-trait associations. | Methods / Enrichment of novel genes |
| PEER | Latent confounder estimation. | Methods / Training of snTIMs / Genotype and gene expression quality control for TIM training / Gene expression quality control |
| PLINK | Used PLINK for several separate genotype workflows: an ancestry-specific 1000 Genomes LD panel for GWAS-summary imputation; MVP genotype QC and sex checks; ROSMAP sample/variant QC and IBD relatedness; and staged FACS-MG QC, LD pruning and genetic PCs. The LD panel reports --ld-window-kb 1000000, --ld-window 1000, --maf 0.01 and --ld-window-r2 0. FACS-MG reports --indep-pairwise 1000 10 0.02, followed by 50 5 0.02. | Methods / I-TWAS and snGReX-PheWAS in MVP / MVP genotype quality control |
| PrediXcan | Genetically predicted expression association. | Methods / Training of snTIMs / Genotype and gene expression quality control for TIM training / Training of cell-type-specific PrediXcan models |
| scVI | Variational single-cell integration or expression modelling. | Methods / Out-of-sample validation of PsychAD snTIMs / Validation of class-level PsychAD snTIMs in ROSMAP |
| SHAPEIT | Genotype haplotype phasing. | Methods / I-TWAS and snGReX-PheWAS in MVP / MVP genotype quality control |
| smartpca | Genetic ancestry principal components. | Methods / I-TWAS and snGReX-PheWAS in MVP / MVP genotype quality control |

XML SHA-256: `67a35ac2cd25950973379382addff05cb689b1a4b1424c71ec1e54b7e8b01209`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P020: Fasting boosts breast cancer therapy efficacy via glucocorticoid activation

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09869-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823405/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Methods / ChIP–seq |
| ChIP_Zwart | Processed ChIP-seq through ChIP_Zwart 0.1.2, including alignment, MAPQ/duplicates, input-controlled MACS2 peaks and downstream signal/differential/annotation analyses. | Methods / ChIP–seq |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Methods / ChIP–seq |
| clusterProfiler | Ran GSEA on xenograft DEGs ranked by log2 fold change using Hallmark, GR and PR signatures, with pvalueCutoff 0.05 and BH correction. The clinical post/pre analysis instead ranked by absolute log10 P times the fold-change sign and used permutation inference. | Methods / RNA-seq analyses |
| deepTools | Used deepTools2.5.3 within ChIP_Zwart0.1.2 for reported FRiP/normalized ChIP-seq signal and tornado plots; bigwigCompare averaged RPGC1x replicate tracks within each condition. | Methods / ChIP–seq |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq analyses |
| DIA-NN | Data-independent acquisition proteomic quantification. | Methods / Proteomics |
| DiffBind | Differential binding or accessibility across peak-count libraries. | Methods / ChIP–seq |
| GSEA | Ranked gene-set enrichment. | Methods / RNA-seq analyses |
| GSVA | Per-sample gene-set activity scoring. | Methods / RNA-seq analyses |
| HISAT2 | Mapped paired 51-bp MCF7 xenograft RNA-seq reads to hg38/GRCh38 using HISAT2 2.1.0 before HTSeq count 0.5.3 and DESeq2 1.22.2 differential expression. | Methods / RNA-seq analyses |
| HTSeq | Read counting and sequence processing. | Methods / RNA-seq analyses |
| MACS | Used MACS2 2.1.2 in the ChIP_Zwart 0.1.2 workflow to call peaks over input ChIP-seq controls and retain q-values below 0.01, before differential binding and annotation. | Methods / ChIP–seq |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / ChIP–seq |

XML SHA-256: `a49fb2508352ef4d9c68d4f91f317ce500811d59680d824c9ce5b602219becc4`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P021: Lifespan single-cell transcriptomic atlas of the human prefrontal cortex

Nature · 2026-09-23 · [DOI](https://doi.org/10.1038/s41586-026-10271-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600939/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Enrichr | Gene-set enrichment. | Methods / Age-associated transcriptomic changes |
| Harmony | Batch integration of cell embeddings. | Methods / Preprocessing of the snRNA-seq dataset / Data integration |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Preprocessing of the snRNA-seq dataset / Data integration |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Age-associated transcriptomic changes |
| MAGMA | Used MAGMA gene z-scores to weight disease genes for scDRS1.0.1; scDRS used200 matched control sets and gene-specific technical-noise weighting. | Methods / Single-cell polygenic disease risk score |
| mashr | Multivariate adaptive shrinkage. | Methods / Degree of sharing across the EN, IN and glial subclasses |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Pseudotime analysis and dynamically expressed gene identification and downstream analysis |
| Scanpy | Single-cell expression processing and analysis. | Methods / Preprocessing of the snRNA-seq dataset / Rigorous quality-control pipeline |
| scDRS | Single-cell disease relevance scoring. | Methods / Single-cell polygenic disease risk score |
| SCENIC+ | Multiomic regulatory-network tools and motif reference resources. | Methods / Inference of transcriptional regulators of pseudotime gene modules |
| Scrublet | Used Scrublet 0.2.3 with 10 principal components independently per batch; subsequent downsampling to 1000 UMIs, CPM and log transformation were separate. | Methods / Integrated analysis of lifespan replication dataset / Integration and annotation |
| scVI | Variational single-cell integration or expression modelling. | Methods / Integrated analysis of lifespan replication dataset / Integration and annotation |
| STARsolo | Aligned pooled multiplexed single-nucleus RNA-seq reads to hg38 using STARsolo 2.7.9a before genotype-based donor demultiplexing and concordance checks. | Methods / Preprocessing of the snRNA-seq dataset / Computational processing and demultiplexing |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Preprocessing of the snRNA-seq dataset / UMAP |
| variancePartition | Expression variance partitioning. | Methods / Quantification of variance in age |
| voom | Mean-variance weights for expression linear models. | Methods / Pseudobulk data aggregation and covariate selection |

XML SHA-256: `d6e691fbafe8e1c42b7a5dbfab7d06ad9e456f833141e7d1b54c2cba9107b1cd`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P022: Determining structures of RNA conformers using AFM and deep neural networks

Nature · 2024-12-18 · [DOI](https://doi.org/10.1038/s41586-024-07559-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11779638/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| PyMOL | Linked two Pep-Fold ARM structural models in parallel using PyMOL v2.0 bond building before coarse-grained simulation. | Methods / Peptide design, synthesis of P46 and modelling |

XML SHA-256: `5ccba4c1d2d0484d1d2333343940709f098753424084a66bf821e1f6d514c220`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P023: Causal modelling of gene effects from regulators to programs to traits

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09866-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12893915/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Pathway enrichment analysis of GWAS and LoF top hits / Clumping of GWAS top variants. |
| Bowtie 2 | Short-read alignment. | Methods / Linkage disequilibrium score regression |
| clusterProfiler | Used enrichGO and enricher to compare GO and Hallmark enrichment among MCH GWAS and loss-of-function hits, with GO sets of 20–2,000 genes. Also tested the top 200 Perturb-seq genes using genes expressed in K562 cells as background. | Methods / Pathway enrichment analysis of GWAS and LoF top hits / Pathway enrichment analysis. |
| FlashPCA | Genetic principal components. | Methods / LoF burden test in the All of Us cohort |
| HMMRATAC | Used MACS3 3.0.3 hmmratac to call ATAC narrow peaks for RPE1/Jurkat after preprocessing and duplicate filtering. | Methods / Linkage disequilibrium score regression |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Methods / Linkage disequilibrium score regression |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Estimation of gene-regulatory effects from Perturb-seq |
| LOFTEE | Loss-of-function variant annotation. | Methods / LoF burden test in the All of Us cohort |
| MACS | Filtered ATAC duplicates with MACS3 3.0.3 and used its hmmratac program to call narrow peaks for RPE1 and Jurkat. Separately reused MACS2 -log10(q) peak strengths in distance-decayed transcription-factor-to-gene regulation scores. | Methods / Linkage disequilibrium score regression |
| PLINK | Used PLINK 1.90b5.3 --clump to select independent GWAS variants with P=5e-8, r2=0.01 and a 10-Mb window. Subsequently merged lead SNPs within 100 kb, retained the smallest-P lead, annotated nearby protein-coding genes using bedtools and excluded HLA. | Methods / Pathway enrichment analysis of GWAS and LoF top hits / Clumping of GWAS top variants. |
| REGENIE | Ran REGENIE LoF gene-burden tests in All of Us using HapMap SNPs from ACAF for step 1, inverse-normal phenotypes in both steps, age/sex/interactions, 16 common-variant and 20 rare-variant PCs. Masks used MAC > 5 and MAF < 1%. | Methods / LoF burden test in the All of Us cohort |
| Trim Galore | Removed adapters from downloaded RPE1 and Jurkat ATAC-seq SRA reads with TrimGalore 0.5.0 before Bowtie2 alignment to hg19 and MACS3 hmmratac peak calling. Other cell types used supplied ENCODE peaks. | Methods / Linkage disequilibrium score regression |
| UCSC liftOver | Converted 830 ENCODE K562 transcription-factor ChIP-seq narrowPeak coordinate sets to hg19 for annotating gene programs. | Methods / Datasets / ChIP–seq data. |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Estimation of gene-regulatory effects from Perturb-seq / Co-expression with marker genes. |
| Ensembl VEP | Variant consequence annotation. | Methods / LoF burden test in the All of Us cohort |

XML SHA-256: `6e73911d0eb7e9d54023ecc3c0d216106778b58c3bc62c8248c9f3232abb4c70`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P024: Single-cell atlas of transcriptomic vulnerability across brain disorders

Nature · 2026-09-23 · [DOI](https://doi.org/10.1038/s41586-025-09573-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600946/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Processing of genotypes |
| Beagle | Estimated haplotypes across the selected MAPT/17q21.31 genotypes after PCA and three-cluster haplotype grouping; the source reports one burn-in and 23 phasing iterations. | Methods / MAPT locus haplotyping |
| CellBender | Ambient count background removal. | Methods / Processing of snRNA-seq data / QC |
| Harmony | Batch integration of cell embeddings. | Methods / Processing of snRNA-seq data / Batch correction |
| KING | Genetic kinship and relatedness estimation. | Methods / Processing of genotypes |
| LDSC | Used for cross-trait genetic correlation. | Methods / Co-heritability analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Processing of snRNA-seq data / Doublet detection |
| MAGMA | Mapped GWAS signals using35kb upstream/10kb downstream gene-body windows, then selected the top1000 MAGMA z-score weights for scDRS across multiple traits. The source names g1000_eur.bim and NCBI38.gene.loc. | Methods / Genetic heritability analysis of polygenic risk |
| mashr | Multivariate adaptive shrinkage. | Methods / Evaluation of shared disease signatures |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Trajectory analysis using neural network models / Comparison to traditional trajectory inference methods |
| PLINK 2 | Used explicitly named PLINK (v.2.0) to calculate ancestry PCs after MAF>=0.01, HWE P>=1e-10, variant missingness<=0.01, high-LD-region exclusion and LD pruning with a 1000-kb window, step 10 and r2=0.2. The later EUR set uses autosomal biallelic variants, imputation R2>0.8, HWE P>=1e-6, missingness<=0.02 and ancestry-specific ALFA MAF>=0.01. PLINK 2 also computes AD PRS from separately estimated PRS-CS-auto weights, including and excluding APOE. | Methods / Processing of genotypes |
| PRS-CS | Inferred Alzheimer disease SNP weights with PRS-CS-auto using a developers' 1000 Genomes LD panel and reported a=1,b=0.5,1000 iterations,500 burn-in and thinning5; PLINK2 then computed individual scores. | Methods / PRS calculation |
| Scanpy | Single-cell expression processing and analysis. | Methods / Processing of snRNA-seq data / Alignment |
| scDRS | Single-cell disease relevance scoring. | Methods / Genetic heritability analysis of polygenic risk |
| Scrublet | Applied Scrublet to infer further doublets after initial quality filtering, normalization/log transformation and an initial Leiden clustering. | Methods / Processing of snRNA-seq data / Doublet detection |
| scVI | Variational single-cell integration or expression modelling. | Methods / Spatial validation of cellular taxonomy / Class label prediction |
| Squidpy | Spatial neighborhood and expression analysis. | Methods / Spatial validation of cellular taxonomy / Definition of rough laminar layers within tissue |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Processing of snRNA-seq data / Alignment |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Defining cellular taxonomy using iterative clustering / Hierarchical cellular taxonomy |
| Vireo | Genotype-based cell demultiplexing. | Methods / Processing of snRNA-seq data / Alignment |

XML SHA-256: `95f294a27bf20b44f42ea8b780e347b1cd16849df0e48ee9745ac43600cd7c07`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P025: Earliest modern human genomes constrain timing of Neanderthal admixture

Nature · 2024-12-12 · [DOI](https://doi.org/10.1038/s41586-024-08420-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11839475/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Neanderthal ancestry |
| Bowtie 2 | Short-read alignment. | Methods / Y-chromosome and phenotypes |
| BWA | DNA read alignment. | Methods / DNA extraction and sequencing |
| GATK | Realigned indels with GATK 1.3-14 before error-profile-aware genotype calling of high-coverage ancient genomes with snpAD; subsequent site/depth/coverage and repeat filters were separate. | Methods / Data processing, general filters and genotyping |
| SAMtools | Alignment manipulation and QC. | Methods / Data processing, general filters and genotyping |
| snpAD | Estimated ancient-DNA substitution-error profiles and genotype frequencies, then called the most likely genotype per site/chromosome in the Zlaty kun and Ranis13 high-coverage genomes. | Methods / Data processing, general filters and genotyping |

XML SHA-256: `3dabca1b006902b4626ef32f25970a049a0a8db449693cab72232e96e8f80bdd`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P026: Structure and mechanism of the Zorya anti-phage defence system

Nature · 2024-12-11 · [DOI](https://doi.org/10.1038/s41586-024-08493-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11946911/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Model building and validation |
| APBS | Generated electrostatic maps with the APBS electrostatics plugin in PyMOL for structural figure preparation. | Methods / Figure preparation |
| ChimeraX | Molecular structure visualization and model building. | Methods / Model building and validation |
| MaxQuant | Mass-spectrometry proteomic identification and quantification. | Methods / MS data analysis |
| PyMOL | Manually adjusted ZorA-tail conformations in PyMOL v2.5, inspected predicted structures with v2.5.4 to identify tail starts, prepared figures with v2.5.4/v3.0.2 and calculated electrostatic maps with the APBS plugin. | Methods / Model building and validation |

XML SHA-256: `a187abeddf33a0f8370df96cf901492dd9a4d099d3a940545d5ebdc873432daf`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P027: An RNA splicing system that excises DNA transposons from animal mRNAs

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09853-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12779559/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / AlphaFold3 multimer prediction |
| BCFtools | Identified variants together with SAMtools after BWA alignment of pooled C. elegans mutant-lineage whole-genome reads; mapping SNPs were subsequently genotyped with GATK. | Methods / EMS screening and whole-genome sequencing |
| Bowtie 2 | Short-read alignment. | Methods / RIP–seq |
| BWA | Aligned pooled C. elegans mutant-lineage whole-genome reads to WBcel235/ce11 with BWA-MEM 0.7.17-r1188 before SAMtools/BCFtools variant discovery and GATK mapping-SNP genotyping. | Methods / EMS screening and whole-genome sequencing |
| CLAPAnalysis | Processed paired RIP-seq through a Snakemake workflow based on CLAPAnalysis with modified alignment parameters: Trim Galore/FastQC, Bowtie2 repetitive-RNA subtraction, STAR genomic alignment and Picard duplicate removal. | Methods / RIP–seq |
| FastQC | Sequencing read quality assessment. | Methods / RIP–seq |
| GATK | Genotyped mapping SNPs in pooled C. elegans mutant lineages after BWA-MEM alignment and SAMtools/BCFtools variant identification. | Methods / EMS screening and whole-genome sequencing |
| IsoQuant | Long-read transcript model identification, correction and quantification. | Methods / Long-read nanopore sequencing and SOS splicing analysis |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / RIP–seq |
| minimap2 | Aligned ONT reads for splicing analyses using minimap2 2.22--r1109dirty: -ax splice -C 0 for SOS, and default -ax splice for hybrid/SOS/F19B2.5.2 UTR analyses. | Methods / Long-read nanopore sequencing and SOS splicing analysis |
| Nextflow | Analysis pipeline orchestration. | Methods / Illumina sequencing and alternative splicing analysis |
| nf-core | Reusable sequencing analysis pipelines. | Methods / Illumina sequencing and alternative splicing analysis |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / RIP–seq |
| PyMOL | Illustrated the selected highest-scoring AlphaFold3 multimer/docking prediction using PyMOL v2.5.8. | Methods / AlphaFold3 multimer prediction |
| rMATS | Differential alternative splicing. | Methods / Illumina sequencing and alternative splicing analysis |
| SAMtools | Used SAMtools 1.3.1 alongside BCFtools 1.13 to identify variants in BWA-aligned whole-genome data from pooled C. elegans EMS-mutant lineages. Exact calling subcommands were not specified in this paragraph. | Methods / EMS screening and whole-genome sequencing |
| Snakemake | Analysis workflow execution. | Methods / RIP–seq |
| STAR | Aligned paired RIP-seq reads remaining after Bowtie2 repetitive-RNA removal to C. elegans ce11 using STAR 2.7.9a, local ends, minimum matched bases/score 100, unique mapping, noncanonical-junction removal, BySJout filtering and retained unmapped reads. | Methods / RIP–seq |
| Trim Galore | Trimmed paired RIP-seq adapters with TrimGalore 0.6.2 before CLAPAnalysis. A distinct nf-core/rnasplice 1.0.4 workflow used TrimGalore 0.6.7 for adapter/low-quality RNA-seq filtering, followed by STAR and splicing analysis; FastQC versions differ between these workflows. | Methods / RIP–seq |

XML SHA-256: `5c7736fa8079efb345b09556dc4290f29b89ffc1ea02f7c7026616798b8c6fa1`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P028: The structure of apolipoprotein B100 from human low-density lipoprotein

Nature · 2024-12-11 · [DOI](https://doi.org/10.1038/s41586-024-08467-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11839476/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ChimeraX | Molecular structure visualization and model building. | Methods / Cryo-electron microscopy / Data processing |
| Jalview | Alignment visualization and manual inspection. | Methods / Sequence analysis |

XML SHA-256: `6b3c7c80d635ca977fcf37f0707536890bcbb3498345dca7c16d332002d5a6be`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P029: A direct role for a mitochondrial targeting sequence in signaling stress

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09834-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC7618714/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCLConvert | Sequencer BCL demultiplexing and FASTQ conversion. | Methods / Gene expression analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / Gene expression analysis |
| DIA-NN | Data-independent acquisition proteomic quantification. | Methods / Sample processing for Mass Spectrometry (MS) analysis |
| DRAGEN | Sequencing alignment, variant or RNA processing platform; named workflow must be identified. | Methods / Gene expression analysis |
| featureCounts | Quantified yeast RNA-seq against R64-1-1 using Rsubread featureCounts after BCLConvert demultiplexing and DRAGEN RNA processing. The paragraph reports Rsubread 2.23.2 with R 4.4.1. | Methods / Gene expression analysis |
| Jalview | Alignment visualization and manual inspection. | Methods / Evolutionary analysis |
| MAFFT | Multiple sequence alignment. | Methods / Evolutionary analysis |
| Rsubread | Used the Rsubread featureCounts interface for yeast RNA-seq counts; reported version 2.23.2 with R 4.4.1. | Methods / Gene expression analysis |

XML SHA-256: `e4a97689207af74f04de28e08e53fd28f02f5589e0be6aa1fc53d12589ae281d`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P030: Template and target-site recognition by human LINE-1 in retrotransposition

Nature · 2023-12-14 · [DOI](https://doi.org/10.1038/s41586-023-06933-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10830416/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Model building and refinement |
| ChimeraX | Molecular structure visualization and model building. | Methods / Model building and refinement |

XML SHA-256: `520c2fca114e60be3cab04225f3fcd0d843f29ad217fe67ea696541939aab4cc`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P031: Human gut M cells resemble dendritic cells and present gluten antigen

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09829-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872457/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| clusterProfiler | Performed mature-M-cell versus enterocyte GSEA using log2 fold changes and KEGG/all-GO gene sets version 7.4. | Methods / Bulk RNA sequencing of M cell organoids |
| DESeq2 | Count-based differential expression analysis. | Methods / Re-analysis of primary human intestinal scRNA-seq datasets |
| Enrichr | Gene-set enrichment. | Methods / Re-analysis of primary human intestinal scRNA-seq datasets |
| GSEA | Ranked gene-set enrichment. | Methods / Bulk RNA sequencing of M cell organoids |
| Scanpy | Single-cell expression processing and analysis. | Methods / Re-analysis of scRNA-seq datasets of mouse intestinal tissue- or organoid-derived M cells |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Sample preparation and scRNA-seq analysis of M cell organoids |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Sample preparation and scRNA-seq analysis of M cell organoids |

XML SHA-256: `5b4d5ab5efcec7e09d836e2cd6e45388588aef1e37683e10fa5f6e242fcb415e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P032: Central control of dynamic gene circuits governs T cell rest and activation

Nature · 2024-12-11 · [DOI](https://doi.org/10.1038/s41586-024-08314-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11754113/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / CUT&RUN analysis |
| BEDTools | Genomic interval manipulation and overlap. | Methods / CUT&RUN analysis |
| Bowtie 2 | Short-read alignment. | Methods / CUT&RUN analysis |
| BWA | DNA read alignment. | Methods / ChIP–seq analysis |
| Cell Ranger | Counted Perturb-seq GEX, guide and antibody libraries using cellranger count v7.1.0, expect-cells=45000 and chemistry=SC5P-R2; aggr combined counts across wells. | Methods / Perturb-seq analysis |
| clusterProfiler | Ran GSEA with all human gene sets obtained through msigdbr 7.5.1 in the bulk RNA-seq analysis. | Methods / Bulk RNA-seq analysis |
| Cutadapt | Adapter trimming. | Methods / Bulk RNA-seq analysis |
| Cytoscape | Network visualization and analysis. | Methods / Bulk RNA-seq analysis |
| deepTools | Used deepTools3.5.5 computeMatrix scale-regions with3kb upstream/downstream,5kb scaled gene bodies and skipZeros, followed by plotProfile perGroup, on scaled STAT5A ChIP-seq bigWigs and expressed-gene BED regions. | Methods / CUT&RUN and ChIP–seq visualization |
| DESeq2 | Count-based differential expression analysis. | Methods / Perturb-seq analysis |
| FastQC | Sequencing read quality assessment. | Methods / Bulk RNA-seq analysis |
| featureCounts | Counted RNA-seq gene alignments with featureCounts in Subread 2.0.1 against GENCODE v41 basic, after Cutadapt/seqtk/STAR and UMI-tools 1.0.1 deduplication. | Methods / Bulk RNA-seq analysis |
| GenomicRanges | Genomic-range containers and overlap operations in R. | Methods / CUT&RUN analysis |
| GSVA | Per-sample gene-set activity scoring. | Methods / MED12 CAR activation scoring |
| Harmony | Batch integration of cell embeddings. | Methods / Perturb-seq analysis |
| MACS | Called CXXC1 and MED12 ChIP-seq peaks with MACS2 2.2.6 callpeak at q-value 0.05 on pooled AAVS1-knockout donor BAMs; used input samples for CXXC1 background and MED12-knockout samples for high-confidence MED12 background. | Methods / ChIP–seq analysis |
| MaxQuant | Mass-spectrometry proteomic identification and quantification. | Methods / Mass spectrometry |
| MSstats | Statistical proteomic differential analysis. | Methods / Mass spectrometry analysis |
| MultiQC | Aggregation of QC reports. | Methods / Bulk RNA-seq analysis |
| nf-core | Reusable sequencing analysis pipelines. | Methods / ChIP–seq analysis |
| RSeQC | Generated per-sample RNA-seq quality-control metrics alongside FastQC and MultiQC. | Methods / Bulk RNA-seq analysis |
| rtracklayer | R genomic-track import/export and chain-based GRanges coordinate mapping. | Methods / Polymerase pausing analysis |
| SAMtools | Alignment manipulation and QC. | Methods / CUT&RUN analysis |
| sctransform | Applied Seurat 4.3.0.1 SCTransform while regressing S.Score and G2M.Score. ADT CLR normalization, HTODemux, Harmony and WNN were separate. | Methods / Perturb-seq analysis |
| seqtk | Trimmed low-quality bases from bulk RNA-seq reads after Cutadapt adapter removal and before STAR GRCh38 alignment. The paper reports seqtk v0.5.0 without a trimming command or quality threshold. | Methods / Bulk RNA-seq analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Perturb-seq analysis |
| souporcell | Genotype-based cell demultiplexing. | Methods / Perturb-seq analysis |
| STAR | Aligned adapter/quality-trimmed bulk RNA-seq reads to GRCh38 with STAR 2.7.10a before UMI-tools counting/deduplication and featureCounts quantification using GENCODE v41. | Methods / Bulk RNA-seq analysis |
| UMI-tools | Counted unique molecular identifiers and deduplicated bulk RNA-seq reads before featureCounts gene quantification. | Methods / Bulk RNA-seq analysis |

XML SHA-256: `b69e9f3d24233ed51dabe954b63664dcbf8e15e7af5d419babb0018173e1ecb6`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P033: The landscape of genomic structural variation in Indigenous Australians

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06842-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10733147/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Analysis of STRs |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Analysis of large CNVs |
| BWA-MEM2 | Mapped matched Illumina short-read libraries to hg38 and T2T-chm13 with BWA-MEM2 2.2.1 and -Y, using sex-specific reference genomes while comparing long-read mappings separately. | Methods / Alignment to reference genome |
| Guppy | Nanopore basecalling; distinguish the fish name. | Methods / ONT data processing |
| minimap2 | Mapped ONT reads with minimap2 2.22 using -x map-ont -a --secondary=no --MD to sex-specific XY/XO hg38 or T2T-CHM13 references. | Methods / Alignment to reference genome |
| RepeatMasker | Repetitive sequence annotation. | Methods / Structural variation repeat classification |
| Sniffles | Long-read structural variant discovery. | Methods / Analysis of STRs |
| UCSC liftOver | Mapped structural-variant coordinates from T2T-CHM13 to hg38 before comparisons with gnomAD, deCODE and HGSVC SV sets using reciprocal-overlap categories. | Methods / Comparison to annotations |

XML SHA-256: `8c03b8a89cf60855e270dd75a5c5cfa6fe4d9ca7cbcf0e64cfe471e50db97249`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P034: Neutralizing GDF-15 can overcome anti-PD-1 and anti-PD-L1 resistance in solid tumours

Nature · 2024-12-11 · [DOI](https://doi.org/10.1038/s41586-024-08305-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11779642/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Gene expression analysis of GDFATHER patient biopsies |

XML SHA-256: `2fa4797d9f3f49c8fa17085e25076eb7bc0e73722a979a63e1c9afb15f28a34f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P035: Mapping the genetic landscape across 14 psychiatric disorders

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09820-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12779569/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DEPICT | Gene-set and tissue enrichment of GWAS loci. | Methods / Genomic SEM / Stratified genomic SEM |
| EWCE | Compared EWCE and competitive MAGMA enrichment and averaged their FDR-adjusted P-values for the reported results. | Methods / Functional annotation |
| Genomic SEM | Genetic covariance and structural equation modelling. | Methods / Genomic SEM / Genome-wide models |
| LAVA | Local genetic correlation. | Methods / LAVA |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / Genomic SEM / Genome-wide models |
| MAGMA | Used MAGMA.Celltyping in R for competitive genome-wide gene-set regression with 1000Genomes European LD,GRCh37 and35kb upstream/10kb downstream windows; averaged MAGMA and EWCE FDR-adjusted P-values for reported results. | Methods / Functional annotation |
| MAGMA.Celltyping | Used the MAGMA.Celltyping R package for competitive gene-set enrichment with European LD and strand/window-specific gene mapping. | Methods / Functional annotation |
| MiXeR | Polygenic mixture modelling and genetic overlap. | Methods / MiXeR |
| PLINK | Used for LD-based variant selection. | Methods / Locus definition and cross-locus overlap |
| Popcorn | Cross-population genetic correlation. | Methods / Cross-ancestry analyses |

XML SHA-256: `7fcb5520e426b53ab60f09e79907f5f2368afcc3d99989eea98fe39c00fbc52e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P036: Somatic evolution following cancer treatment in normal tissue

Nature · 2025-12-10 · [DOI](https://doi.org/10.1038/s41586-025-09792-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13190248/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaMissense | Missense variant pathogenicity prediction. | Methods / Duplex sequencing bioinformatic analyses |
| BWA | DNA read alignment. | Methods / Duplex sequencing bioinformatic analyses |
| dndscv | Somatic mutation selection analysis. | Methods / Positive selection analyses |
| NanoSeq | Low-error somatic mutation sequencing analysis. | Methods / NanoSeq libraries |
| Nextflow | Analysis pipeline orchestration. | Methods / Duplex sequencing bioinformatic analyses |
| SAMtools | Alignment manipulation and QC. | Methods / Duplex sequencing bioinformatic analyses |

XML SHA-256: `31cdaf15cf7c4bbe58add2c4a6e8b0c2edecf1135a9a5c21e667c390351bbd8f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P037: Bioactive glycans in a microbiome-directed food for children with malnutrition

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06838-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10764277/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / MAGs / Short-read shotgun sequencing |
| Bowtie 2 | Short-read alignment. | Methods / MAGs / Short-read shotgun sequencing |
| DESeq2 | Count-based differential expression analysis. | Methods / Defining the relationship between MAG abundances and WLZ |
| DIAMOND | Protein sequence alignment. | Methods / Subsystem-based annotation and prediction of functional capabilities (inferred metabolic phenotypes) of MAGs and cultured P. copri strains |
| edgeR | Count-based differential expression analysis. | Methods / Microbial RNA-seq analysis of MAG gene expression |
| FastQC | Sequencing read quality assessment. | Methods / Microbial RNA-seq analysis of MAG gene expression |
| fgsea | Ranked WLZ-associated MAG transcripts by fold-change direction times negative log10 P and tested metabolic-pathway transcript sets of at least ten genes across treatment, response-quartile, time and interaction contrasts; used BH q below 0.1. | Methods / Microbial RNA-seq analysis of MAG gene expression |
| ggtree | Phylogenetic tree visualization. | Methods / Constructing a marker-gene-based phylogeny for P. copri MAGs and cultured P. copri isolates |
| GSEA | Ranked gene-set enrichment. | Methods / Determining the effects of MDCF-2 supplementation on the abundances of WLZ-associated MAGs |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / MAGs / MAG taxonomy |
| kallisto | Used kallisto0.43.0 for metagenomic contig/MAG abundance and metatranscriptomic transcript quantification. DNA TPM prevalence/abundance filters selected837MAGs; later RNA counts excluded rRNA and low-DNA-abundance MAGs. | Methods / Defining the relationship between MAG abundances and WLZ |
| MAFFT | Multiple sequence alignment. | Methods / MAGs / MAG taxonomy |
| MetaBAT2 | Metagenomic assembly binning and read-depth processing. | Methods / MAGs / Short-read shotgun sequencing |
| Trim Galore | Removed adapters and low-quality sequence using TrimGalore 0.6.4 for shotgun microbial DNA and RNA. Discarded the entire paired observation if either mate was shorter than 50 bases for DNA or 100 bases for RNA; used separate pre/post FastQC checks. | Methods / MAGs / Short-read shotgun sequencing |
| variancePartition | Expression variance partitioning. | Methods / Determining the effects of MDCF-2 supplementation on the abundances of WLZ-associated MAGs |

XML SHA-256: `812e36432230a2cb9fdad7c98df03a2ab9131acdee41ba0847b8aa9c9aa312f7`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P038: TM184C is a GPCR-like regulator of intercellular exchange and autophagy

Nature · 2026-09-09 · [DOI](https://doi.org/10.1038/s41586-026-10993-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13581589/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Downloading the AF2 v.4 dataset |
| Cytoscape | Network visualization and analysis. | Methods / Software used |
| PyMOL | Listed PyMOL v3.4.19 among the software used in the study. | Methods / Software used |

XML SHA-256: `ccafee9b75be7412b12beb07f4a3ac0883682a5b40f0389d3c96f6f09d5e909a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P039: Slide-tags enables single-nucleus barcoding for multimodal spatial genomics

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06837-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10764288/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellBender | Ambient count background removal. | Methods / Slide-tags data preprocessing / snRNA-seq data |
| Cell Ranger | Used GEX v6.1.2 mkfastq/count with include-introns against GRCh38 or mm10, and ARC v2.0.2 mkfastq/count for human multiome data. GEX aggr adjusted sequencing-depth differences; its cell calls/barcodes also supported spatial matching and reproducibility denominators. | Methods / Slide-tags data preprocessing / snRNA-seq data |
| chromVAR | Chromatin motif accessibility deviation scores. | Methods / Melanoma analysis / ATAC analysis |
| clusterProfiler | Applied enrichGO with default parameters to spatially varying genes with Z above seven, using org.Hs.eg.db 3.16.0 biological-process annotations; selected displayed terms with adjusted P below 1e-8 in at least one cell type. | Methods / Human brain analysis / Identification of layers and layer-dependent gene expression |
| Enrichr | Gene-set enrichment. | Methods / Melanoma analysis / Differential gene expression, differential chromatin gene scores and gene set enrichment analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Human brain analysis / Quality control and cell type assignment |
| inferCNV | Expression-derived copy-number inference. | Methods / Melanoma analysis / Inferring CNV |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Mouse brain analysis / Quality control and cell type assignment |
| MACS | Recalled ATAC peaks across all cells with the Signac CallPeaks wrapper using MACS2 2.2.7.1, then mapped fragments to peaks/nuclei and removed nonstandard-chromosome and blacklist regions. | Methods / Melanoma analysis / Quality control and cell type assignment / Multiome ATAC and snRNA-seq data |
| RSEM | Transcript abundance estimation. | Methods / Mouse brain analysis / Comparison of Slide-tags snRNA-seq versus bulk RNA-seq |
| sctransform | Used SCTransform-normalized counts in spatial receptor-ligand expression correlations with a 100-micrometre adjacency definition. | Methods / Tonsil analysis / Spatial receptor–ligand prediction |
| seqtk | Downsampled spatial-barcode FASTQ reads to 25 million records for computational efficiency and consistency, before matching candidate cell barcodes to Cell Ranger/CellBender outputs and spatial barcode coordinates. | Methods / Slide-tags data preprocessing / Spatial barcode data |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Mouse brain analysis / Quality control and cell type assignment |
| Signac | Single-cell chromatin accessibility analysis. | Methods / Melanoma analysis / Quality control and cell type assignment / Multiome ATAC and snRNA-seq data |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Mouse brain analysis / Comparison of Slide-tags snRNA-seq versus bulk RNA-seq |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Mouse brain analysis / Quality control and cell type assignment |

XML SHA-256: `63233a1092cf7fe1c862864ab76af7f4c3ac6c2b5f8005998bc9c35f16eead1d`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P040: PLA2G2D in tumour-draining lymph nodes regulates anti-tumour immunity

Nature · 2026-09-09 · [DOI](https://doi.org/10.1038/s41586-026-10954-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645638/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Aligned and quantified GEM-X Universal 3-prime v4 on-chip-multiplexed mouse libraries against GRCm39-2024-A with CellRanger, reported as v9.01. | Methods / Single-cell RNA-seq and data analysis |
| clusterProfiler | Used GSEA with default settings against custom dendritic-cell, macrophage and CD8 T-cell signatures and applied BH correction. | Methods / GSEA |
| DESeq2 | Count-based differential expression analysis. | Methods / Bulk RNA-seq of stimulated T cells |
| GSEA | Ranked gene-set enrichment. | Methods / GSEA |
| Harmony | Batch integration of cell embeddings. | Methods / Single-cell RNA-seq and data analysis |
| HOMER | Motif and regulatory enrichment analysis. | Methods / Bulk RNA-seq of stimulated T cells |
| RSEM | Transcript abundance estimation. | Methods / Analyses of publicly available transcriptome data |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Single-cell RNA-seq and data analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Single-cell RNA-seq and data analysis |

XML SHA-256: `613a8a837a2bbebd1501da55e1f7167296630c8a94477fe415cdea0c273c53ad`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P041: Indigenous Australian genomes show deep structure and rich novel variation

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06831-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10733150/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Ancestry inference |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Runs of homozygosity |
| BEAST | Inferred mitochondrial phylogenies using BEAST before summarizing maximum clade credibility trees. | Methods / Mitochondrial genetic structure and diversity |
| GATK | Called mitochondrial variants with GATK 3.8-0 HaplotypeCaller using haploid ploidy and checked maternal parent-offspring concordance before phylogenetic analyses. | Methods / Mitochondrial genetic structure and diversity |
| KING | Genetic kinship and relatedness estimation. | Methods / Kinship inference |
| PLINK | Used for genotype quality control. | Methods / Genomic variation |
| PolyPhen-2 | Missense variant effect annotation. | Methods / Genomic variation |
| RFMix | Local ancestry inference. | Methods / Ancestry inference |
| SHAPEIT | Genotype haplotype phasing. | Methods / Haplotype inference |
| SIFT | Missense variant effect annotation. | Methods / Genomic variation |
| smartpca | Genetic ancestry principal components. | Methods / Ancestry inference |
| TreeAnnotator | Produced maximum clade credibility tree summaries from the BEAST mitochondrial phylogenetic analysis. | Methods / Mitochondrial genetic structure and diversity |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Population structure |
| Ensembl VEP | Variant consequence annotation. | Methods / Genomic variation |

XML SHA-256: `ae9b254ed9a0725706c14c83dc1242fc7281e48d89adfaa79e2b9032e1ed199d`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P042: Whole-genome landscapes of 1,364 breast cancers

Nature · 2025-12-03 · [DOI](https://doi.org/10.1038/s41586-025-09812-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12851938/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Genomic analysis and interpretation |
| BWA | DNA read alignment. | Methods / Genomic analysis and interpretation |
| CancerVision | Ran whole-genome cancer analysis and interpretation through the CancerVision platform, including mapping, preprocessing and somatic calling. | Methods / Genomic analysis and interpretation |
| DELLY | Structural variant discovery. | Methods / Genomic analysis and interpretation |
| dndscv | Somatic mutation selection analysis. | Methods / Identification of protein-coding driver genes |
| GATK | Used Mutect2 within CancerVision, alongside Strelka2, to call somatic SNVs and short indels from whole-genome data, then applied tumour/normal read-support, mapping-quality, VAF and panel-of-normals criteria. | Methods / Genomic analysis and interpretation |
| Mutect2 | Called somatic SNVs and short indels with Mutect2 in GATK 4.0 as part of the CancerVision tumour/normal workflow. | Methods / Genomic analysis and interpretation |
| Strelka2 | Called somatic SNVs and short indels with Strelka2 alongside Mutect2 in the whole-genome cancer analysis. | Methods / Genomic analysis and interpretation |
| Ensembl VEP | Variant consequence annotation. | Methods / Genomic analysis and interpretation |

XML SHA-256: `0b77101ea70ebffaaff42dbf7f46f28824a2ea9bf42e71af721cd20d561ef992`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P043: Single-cell analysis of chromatin accessibility in the adult mouse brain

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06824-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719105/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Identification of reproducible peak sets in each cell cluster |
| BWA | DNA read alignment. | Methods / Processing and alignment of sequencing reads |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Methods / TE analysis |
| clusterProfiler | Performed GO enrichment with analysis-specific background genes, Fisher exact P values and BH multiple-comparison correction. | Methods / GO enrichment |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Iterative cell clustering / Graph-based clustering |
| MACS | Called peaks from Tn5-corrected cell-cluster pseudobulks, biological replicates, half-read pseudoreplicates and their pool using MACS2 with shift -75, extension 150, no model, summits, SPMR and q-value 0.01. Extended summits to 501 bases and filtered reproducible peaks with score per million at least 5. | Methods / Identification of reproducible peak sets in each cell cluster |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Inference of cis-co-accessible cCREs |
| RepeatMasker | Repetitive sequence annotation. | Methods / TE analysis |
| Scrublet | Called doublets with the modified Scrublet workflow in SnapATAC2 on selected 500-bp accessibility tiles, with expected rate 0.08 and score cutoff 0.5. | Methods / Doublet removal |
| Seurat | Used for dataset integration. | Methods / Integration analysis with scRNA-seq data |
| SnapATAC2 | Used SnapATAC2 modified Scrublet on selected 500-bp accessibility tiles, expected doublet rate 0.08 and score cutoff 0.5. | Methods / Doublet removal |
| UCSC liftOver | Used reciprocal mouse-human liftOver searches to identify orthologous cCREs, with more than50% of bases mapped; accessibility conservation was then assessed against human cCREs. | Methods / Sequence conserved, chromatin accessibility conserved and mouse-specific cCREs |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Iterative cell clustering / Graph-based clustering |

XML SHA-256: `f8770e7b1232faedccba9e6bc4ddb1fb4db75a536adeff8473458534dab4d5a1`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P044: Fetal hepatocytes protect the HSPC genome via fetuin-A

Nature · 2024-12-04 · [DOI](https://doi.org/10.1038/s41586-024-08307-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11711094/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / Cut&Tag data processing |
| BWA | DNA read alignment. | Methods / ATAC-seq data processing |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Methods / Cut&Tag data processing |
| clusterProfiler | Performed Gene Ontology enrichment in the R-loop Cut&Tag analysis after alignment, signal generation, peak calling and peak annotation. | Methods / R-loop Cut&Tag data processing |
| deepTools | Used deepTools3.5.1 bamCoverage for ATAC/CUT&Tag tracks and separate correlation analysis. R-loop CUT&Tag tracks were normalized as reads per million before metaplots; the ATAC pipeline separately applied strand-specificTn5 shifts. | Methods / ATAC-seq data processing |
| DESeq2 | Count-based differential expression analysis. | Methods / ATAC-seq data processing |
| FastQC | Sequencing read quality assessment. | Methods / RNA-seq and analysis |
| HISAT2 | Aligned quality-trimmed paired 150-bp HSPC RNA-seq to mouse mm10 using HISAT2 2.2.1 before GENCODE M25/HTSeq-count 0.13.5 assignment. | Methods / RNA-seq and analysis |
| HOMER | Motif and regulatory enrichment analysis. | Methods / ATAC-seq data processing |
| HTSeq | Read counting and sequence processing. | Methods / RNA-seq and analysis |
| Integrative Genomics Viewer (IGV) | Visualized deepTools bamCoverage-normalized ATAC-seq signal in IGV v2.7.0 after mm10 alignment,filtering and strand-specific Tn5 shifts. | Methods / ATAC-seq data processing |
| MACS | Called mouse ATAC peaks with MACS2 2.2.6 after Tn5 offsets using BAM, mouse genome shortcut, no model, shift -100 and extension 200; also used MACS2 2.2.6 for separate CUT&Tag analyses. | Methods / ATAC-seq data processing |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Cut&Tag data processing |
| Spectronaut | Data-independent acquisition proteomic quantification. | Methods / Mass spectrometry analysis |
| Trim Galore | Used TrimGalore 0.6.7 for ATAC-seq adapter removal before BWA. A separate RNA-seq workflow explicitly used quality 20, FastQC, minimum length 20 and stringency 1 before HISAT2 and HTSeq quantification. | Methods / ATAC-seq data processing |

XML SHA-256: `7cfc75f5377145a3602c71ac043dc0370798e2104cb290f87cc20a67bd36e46b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P045: Homo sapiens-specific evolution unveiled by ancient southern African genomes

Nature · 2025-12-03 · [DOI](https://doi.org/10.1038/s41586-025-09811-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872451/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Analysis of pseudohaploidized data |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Analysis of complete genomes |
| BWA | DNA read alignment. | Methods / Data processing and authentication |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Analysis of complete genomes |
| PLINK | Used for genotype quality control; LD-based variant selection. | Methods / Analysis of pseudohaploidized data |
| SAMtools | Alignment manipulation and QC. | Methods / Data processing and authentication |
| smartpca | Genetic ancestry principal components. | Methods / Analysis of pseudohaploidized data |
| snpAD | Ancient-DNA error-aware genotype calling. | Methods / Analysis of complete genomes |
| SnpEff | Variant consequence annotation. | Methods / Analysis of complete genomes |

XML SHA-256: `7c8702baac1241ce1024443b7ab56c6377a83c2d6469598e3ce05a1d01224609`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P046: 3D epigenome of glial cell types in developing human cortex

Nature · 2026-09-02 · [DOI](https://doi.org/10.1038/s41586-026-10987-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668669/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / PLAC-seq library creation and analysis |
| Bismark | Bisulfite sequencing alignment and methylation calling. | Methods / WGBS library creation and analysis |
| Bowtie 2 | Short-read alignment. | Methods / WGBS library creation and analysis |
| BWA | DNA read alignment. | Methods / PLAC-seq library creation and analysis |
| CellBender | Ambient count background removal. | Methods / scRNA-seq analysis of oRG |
| Cell Ranger | Used Cell Ranger v9.0.1 multi for cell-barcode calling, read alignment and quality assessment with a custom GRCh38 GENCODE32/Ensembl98 reference containing eGFP. | Methods / scRNA-seq analysis of oRG |
| CIBERSORT | Cell-type deconvolution. | Methods / Characterizing bulk RNA-seq with scRNA-seq |
| Cytoscape | Network visualization and analysis. | Methods / TOBIAS footprinting analysis and network analysis |
| deepTools | Used deepTools3.5.1 multiBamSummary to summarize reads in a merged peak universe across four ATAC-seq cell types, then clustered samples using Spearman correlations. | Methods / ATAC-seq library creation and analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq library creation and analysis |
| DiffBind | Differential binding or accessibility across peak-count libraries. | Methods / ATAC-seq library creation and analysis |
| edgeR | Count-based differential expression analysis. | Methods / RNA-seq library creation and analysis |
| fastp | Read filtering and adapter trimming. | Methods / RNA-seq library creation and analysis |
| FastQC | Sequencing read quality assessment. | Methods / WGBS library creation and analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Immunocytochemistry |
| HOMER | Motif and regulatory enrichment analysis. | Methods / TF motif enrichment analysis |
| HPRep | Assessed biological-replicate concordance of chromatin-interaction data with HPRep after roughly ten-million-read sample matching. | Methods / PLAC-seq library creation and analysis |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / LDSC regression |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / RNA-seq library creation and analysis |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / scRNA-seq analysis of oRG |
| LS-GKM | Trained a gapped-kmer SVM with LS-GKM gkmtrain and wgkm kernel t=4, held chromosome 2 out for evaluation, retrained and used gkmpredict on all 11-mers for deltaSVM scoring. | Methods / Training the GKM model |
| MACS | Identified cell-type H3K4me3 anchor peaks for the MAPS PLAC-seq pipeline using MACS2 with human genome shortcut, broad mode, disabled local lambda and broad cutoff 0.01 on short-distance interaction reads; merged anchors and evaluated replicates before contact modelling. | Methods / PLAC-seq library creation and analysis |
| MAPS | Called H3K4me3 PLAC-seq interactions with the MAPS pipeline using MACS2-derived anchors, AND/XOR/NOT interaction classes and Poisson-regression modelling. | Methods / PLAC-seq library creation and analysis |
| RSEM | Transcript abundance estimation. | Methods / RNA-seq library creation and analysis |
| scCODA | Used scCODA for differential cell-composition analysis. | Methods / scRNA-seq analysis of oRG |
| scDblFinder | Single-cell doublet detection. | Methods / scRNA-seq analysis of oRG |
| scDist | Applied scDist 1.1.5 following SCTransform normalization/scaling. | Methods / scRNA-seq analysis of oRG |
| sctransform | Reported SCTransform 0.4.3 normalization/scaling before scDist 1.1.5 differential-state analysis. | Methods / scRNA-seq analysis of oRG |
| Seurat | Used for expression normalization; marker or differential-expression analysis. | Methods / scRNA-seq analysis of oRG |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA-seq library creation and analysis |
| Trim Galore | Trimmed paired WGBS reads using TrimGalore 0.6.6 with Illumina adapter removal, plus 15-base fixed removal from the 5-prime end of read 2 and 3-prime end of read 1; assessed reads with FastQC before and after trimming. | Methods / WGBS library creation and analysis |
| tximport | Imported bulk vRG,oRG,OPC and MG RSEM estimates with tximport for CIBERSORTx cell-composition analysis against a matched single-cell reference. | Methods / Characterizing bulk RNA-seq with scRNA-seq |

XML SHA-256: `06059f0f86607700494cb0b2477f8a9c03605b8c2089576e3b913baac4036be2`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P047: Brain-wide correspondence of neuronal epigenomics and distant projections

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06823-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719087/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ALLCools | Methylome datasets, clustering, integration and differential methylation. | Methods / Mapping and preprocessing |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Mapping and preprocessing |
| cisTarget | Recovery-curve motif enrichment and regulatory target analysis. | Methods / TF motif enrichment |
| Cluster-Buster | Clustered DNA motif scanning. | Methods / TF motif enrichment |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq |
| SCENIC+ | Multiomic regulatory-network tools and motif reference resources. | Methods / TF motif enrichment |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Transfer of cell labels from one dataset to another with weighted k-nearest neighbours |
| t-SNE | Visualized integrated principal components from three neuronal epigenomic/transcriptomic datasets; graph construction and Leiden clustering were separate steps. | Methods / Integration between snmC-seq, epi-retro-seq and scRNA-seq |

XML SHA-256: `894cd1e454d83639da755f2847f9dbf66d198fa33e25e2c1903c71a04f8de01e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P048: Evolution of myeloid-mediated immunotherapy resistance in prostate cancer

Nature · 2024-12-04 · [DOI](https://doi.org/10.1038/s41586-024-08290-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11779626/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellBender | Ambient count background removal. | Methods / Human scRNA-seq analysis |
| Cell Ranger | Processed human scRNA-seq with Cell Ranger v3 against GRCh38 and mouse scRNA-seq with v5 against GRCm38 before separate matrix analysis. | Methods / Human scRNA-seq analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Human scRNA-seq analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Human scRNA-seq analysis |
| Scanpy | Single-cell expression processing and analysis. | Methods / Human scRNA-seq analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Human scRNA-seq analysis |

XML SHA-256: `4d3d89d8709e715934244d9fae65e42acfeb379072ced500b5a1ed110b8660e6`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P049: Intracellular complement factor H protects neurons during CNS inflammation

Nature · 2026-09-02 · [DOI](https://doi.org/10.1038/s41586-026-10981-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668678/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Human tissue / Analysis of resilient and susceptible RGC subtypes |
| biomaRt | Annotated gene lists with biomaRt2.60.1 in neuronal Cfh-cKO culture RNA-seq analysis. | Methods / Cell culture / RNA-seq analysis of neuronal Cfh-cKO cultures |
| CellBender | Ambient count background removal. | Methods / Human tissue / Data preprocessing and quality control |
| Cell Ranger | Generated sample count matrices with Cell Ranger v7.0.1 against human GRCh38-2020-A, including exonic and intronic reads. | Methods / Human tissue / Data preprocessing and quality control |
| clusterProfiler | Used GSEA of ranked healthy-control cluster markers against top-50 marker sets from three reference retinal datasets for cell annotation. Separately used 4.12.6 for biological-theme enrichment of pseudobulk DEGs and GO analysis of six resilience/susceptibility expression modules. | Methods / Human tissue / Clustering and cell type annotation |
| DESeq2 | Count-based differential expression analysis. | Methods / Human tissue / Differential gene expression analysis |
| featureCounts | Generated mouse mm10 gene-overlap counts using featureCounts 1.5.1 after STAR 2.5.2b alignment. | Methods / Cell culture / RNA-seq analysis of neuronal Cfh-cKO cultures |
| GSEA | Ranked gene-set enrichment. | Methods / Human tissue / Clustering and cell type annotation |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Human tissue / Data preprocessing and quality control |
| scDblFinder | Single-cell doublet detection. | Methods / Human tissue / Data preprocessing and quality control |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Human tissue / Data preprocessing and quality control |
| STAR | Aligned paired 150-bp neuronal culture RNA-seq reads to mouse mm10 2020A with STAR 2.5.2b default parameters before featureCounts and differential-expression/signature analyses. | Methods / Cell culture / RNA-seq analysis of neuronal Cfh-cKO cultures |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Human tissue / Clustering and cell type annotation |

XML SHA-256: `732746565cfe445be1f1e2f1ce3b459964fc9f5ea28f7147c0b3a26f4f8d18c3`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P050: In vitro production of cat-restricted Toxoplasma pre-sexual stages

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06821-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781626/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BAMTools | Converted and sorted Bowtie-mapped Toxoplasma ChIP alignments with BAMTools before MACS2 peak calling. | Methods / ChIP coupled with Illumina sequencing / Library preparation, sequencing and data analysis (Arraystar) |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / ATAC–seq |
| deepTools | Used computeMatrix reference-point with minThreshold2,10bp bins and averageTypeBins sum for T.gondii ChIP/ATAC gene-centered analysis, followed by profiles/heatmaps and optional k-means. ATAC BAMs were converted with bamCoverage using5bp bins. | Methods / ChIP coupled with Illumina sequencing / Library preparation, sequencing and data analysis (Arraystar) |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq and sequence alignment |
| FastQC | Sequencing read quality assessment. | Methods / RNA-seq and sequence alignment |
| featureCounts | Counted Toxoplasma gondii ME49 gene-aligned reads with featureCounts after Subread 2.0.1 alignment to ToxoDB release 49. | Methods / RNA-seq and sequence alignment |
| Guppy | Nanopore basecalling; distinguish the fish name. | Methods / Nanopore direct RNA-seq |
| HOMER | Motif and regulatory enrichment analysis. | Methods / ChIP coupled with Illumina sequencing / Library preparation, sequencing and data analysis (Arraystar) |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / MS-based quantitative analyses of parasite proteomes and AP2 interactomes / Statistical analyses |
| MACS | Called Toxoplasma ChIP-enriched peaks with MACS v2.2 at raw P-value 1e-4 and used generated bedGraph coverage for visualization. A separate nf-core ATAC analysis used broad MACS2 peaks before counts and differential analysis. | Methods / ChIP coupled with Illumina sequencing / Library preparation, sequencing and data analysis (Arraystar) |
| minimap2 | Mapped ONT direct-RNA reads using minimap2 before SAMtools conversion/sorting in the Toxoplasma study. | Methods / Nanopore direct RNA-seq |
| MultiQC | Aggregation of QC reports. | Methods / RNA-seq and sequence alignment |
| nf-core | Reusable sequencing analysis pipelines. | Methods / ChIP coupled with Illumina sequencing / Library preparation, sequencing and data analysis (Arraystar) |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / ATAC–seq |
| SAMtools | Alignment manipulation and QC. | Methods / Nanopore direct RNA-seq |
| Subread suite | Aligned Toxoplasma RNA-seq to ME49/ToxoDB49 with Subread 2.0.1 using -d 50 -D 600 --sortReadsByCoordinates before featureCounts. | Methods / RNA-seq and sequence alignment |
| TPMCalculator | Expression-count and TPM normalization workflow. | Methods / RNA-seq and sequence alignment |
| UCSC bedGraphToBigWig | Conversion of genomic signal tracks to indexed bigWig. | Methods / ChIP coupled with Illumina sequencing / Library preparation, sequencing and data analysis (Arraystar) |

XML SHA-256: `2fd4b3123b1b5930fcd5d6e8d5b6bc21edd6f3bcb572a76cbb50e802b77d2cef`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P051: Architecture of the neutrophil compartment

Nature · 2025-12-03 · [DOI](https://doi.org/10.1038/s41586-025-09807-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823425/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Single-cell transcriptomics on human neutrophils / Spatial analysis of human lung specimens using Visium HD |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| BCLConvert | Sequencer BCL demultiplexing and FASTQ conversion. | Methods / Single-cell transcriptomics on mouse neutrophils / scRNA-seq of sorted tissue neutrophils |
| chromVAR | Chromatin motif accessibility deviation scores. | Methods / Data processing / Transcription factor activity |
| Cutadapt | Adapter trimming. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| DESeq2 | Count-based differential expression analysis. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| edgeR | Count-based differential expression analysis. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| FastQC | Sequencing read quality assessment. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| GenomicRanges | Genomic-range containers and overlap operations in R. | Methods / Data processing / Merging of the datasets and peak calling |
| GSEA | Ranked gene-set enrichment. | Methods / Single-cell transcriptomics on mouse neutrophils / Signature projection |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Single-cell transcriptomics on human neutrophils / Gene module scoring and hub identification |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Single-cell transcriptomics on human neutrophils / Spatial analysis of human lung specimens using Visium HD |
| MACS | Called MACS2 2.2.9.1 peaks through Signac 1.14.0 CallPeaks separately for annotated hubs, retaining cells with prediction score at least 0.7 and excluding blacklist/nonstandard-chromosome peaks. | Methods / Data processing / Merging of the datasets and peak calling |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Single-cell transcriptomics on mouse neutrophils / Velocyto analysis |
| RSEM | Transcript abundance estimation. | Methods / Bulk RNA sequencing of mice and human-derived neutrophils |
| SAMtools | Alignment manipulation and QC. | Methods / Single-cell transcriptomics on mouse neutrophils / Velocyto analysis |
| scater | Single-cell QC and expression summaries. | Methods / Single-cell transcriptomics on human neutrophils / Spatial analysis of human lung specimens using Visium HD |
| scran | Single-cell normalization and variance modelling. | Methods / Single-cell transcriptomics on human neutrophils / Spatial analysis of human lung specimens using Visium HD |
| sctransform | Used Seurat 5.1 SCTransform for spatial PDAC and myocardial-infarction profiles, followed by a top-ten-percent variable-gene choice, scaling, 20-PC analysis and Leiden clustering. | Methods / Single-cell transcriptomics on human neutrophils / Analyses of PDAC and myocardial infarction (MI) models |
| scVelo | RNA velocity analysis. | Methods / Single-cell transcriptomics on mouse neutrophils / Velocyto analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Single-cell transcriptomics on mouse neutrophils / Construction of NeuMap and projection of external data |
| Signac | Single-cell chromatin accessibility analysis. | Methods / Data processing / Initial quality control and cell filtering |
| SingleR | Reference-based cell annotation. | Methods / Single-cell transcriptomics on mouse neutrophils / Construction of NeuMap and projection of external data |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Single-cell transcriptomics on human neutrophils / Data pre-processing |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / In vitro mouse neutrophil culture and analysis |
| velocyto | Spliced/unspliced counting and RNA velocity. | Methods / Single-cell transcriptomics on mouse neutrophils / Velocyto analysis |

XML SHA-256: `9182a4004117036e7be71b48cabb34755f397746ef0fe821dfad01f64fa2c129`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P052: Creating bottom-up RNA transfer vehicles from synthetic protein assemblies

Nature · 2026-09-02 · [DOI](https://doi.org/10.1038/s41586-026-10952-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668671/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Sequence and structural alignments of the structure-mined membrane-binding domains |
| DESeq2 | Count-based differential expression analysis. | Methods / Characterization of STV-C8 RNA content |
| g:Profiler | Functional gene-set enrichment. | Methods / RNA and protein gene set enrichment analysis |
| MAFFT | Multiple sequence alignment. | Methods / Sequence and structural alignments of the structure-mined membrane-binding domains |
| MaxQuant | Mass-spectrometry proteomic identification and quantification. | Methods / Characterization of STV-C8 protein content |
| PyMOL | Aligned extracted predicted membrane-binding-domain structures to the rat PHPLCdelta crystal structure PDB1MAI and calculated RMSD with the PyMOL super alignment tool. | Methods / Sequence and structural alignments of the structure-mined membrane-binding domains |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Characterization of STV-C8 RNA content |

XML SHA-256: `6ef0e991748f47a416f8324e742a7c81bbfc2c42bdbefaf6a6ebfc61ebcf5392`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P053: Conserved and divergent gene regulatory programs of the mammalian neocortex

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06819-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719095/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ALLCools | Methylome datasets, clustering, integration and differential methylation. | Methods / Methylome clustering |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Droplet paired-tag / Identification of human level 1 (tissue conserved) and level 2 (cell type conserved) CREs |
| Bismark | Bisulfite sequencing alignment and methylation calling. | Methods / Data preprocessing / Mapping and quality control of snm3C-seq data |
| Bowtie 2 | Short-read alignment. | Methods / Data preprocessing / Mapping and quality control of snm3C-seq data |
| Cell Ranger | Used cellranger-arc for multiome processing and sense intronic/exonic RNA UMI matrices. Separately, paired-tag libraries used ARC v2.0.0 mkfastq, ATAC v2.0.0 for DNA and GEX v6.1.2 for RNA, followed by manual barcode pairing. | Methods / 10x multiome sequence data processing and clustering |
| cemba-data / YAP | Single-cell methylome and chromatin-contact read mapping workflow. | Methods / Data preprocessing / Mapping and quality control of snm3C-seq data |
| cooltools | Chromosome conformation analysis. | Methods / Domain boundary analysis |
| Cutadapt | Adapter trimming. | Methods / Data preprocessing / Mapping and quality control of snm3C-seq data |
| DoubletFinder | Single-cell doublet detection. | Methods / 10x multiome sequence data processing and clustering |
| edgeR | Count-based differential expression analysis. | Methods / Droplet paired-tag / GO enrichment analysis |
| Enrichr | Gene-set enrichment. | Methods / Droplet paired-tag / GO enrichment analysis |
| FIMO | Used FIMO 5.5.3 with JASPAR CORE vertebrate motifs to scan hg38 sequences of cCREs and differentially methylated regions. Averaged conservation, divergence or PhastCons scores at motif occurrences and used TFClass annotations. Later averaged motif PhastCons within each peak and weighted peak conservation by cell-type peak activity. | Methods / TF motif scanning |
| gseapy | Python gene-set enrichment analysis. | Methods / Droplet paired-tag / GO enrichment analysis |
| Harmony | Batch integration of cell embeddings. | Methods / 10x multiome sequence data processing and clustering |
| HOMER | Motif and regulatory enrichment analysis. | Methods / Droplet paired-tag / Annotation of TEs and TSS proximity |
| LDSC | Used for annotation-stratified genetic enrichment. | Methods / GWAS variant enrichment |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / 10x multiome sequence data processing and clustering |
| MACS | Called cross-species pseudobulk ATAC peaks with MACS2, reporting BAMPE, shift -75, extension 150, q-value 0.1, bedGraph/SPMR and summits. Extended summits to 500 bases, applied score-per-million at least 2 and excluded blacklist/low-mappability regions before comparative analysis. | Methods / ATAC–seq peak calling and filtering |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Data preprocessing / Mapping and quality control of snm3C-seq data |
| SAMtools | Alignment manipulation and QC. | Methods / Data preprocessing / Mapping and quality control of snm3C-seq data |
| scHiCluster | Single-cell chromosome-conformation imputation and analysis. | Methods / Cell- and cluster-level 3D genome analysis / Generating the chromatin contact matrix and imputation |
| sctransform | Used Seurat 4 SCTransform with 3000 highly variable genes; DoubletFinder removed the top ten percent per sample and Harmony/orthologue/RPCA integration followed. | Methods / 10x multiome sequence data processing and clustering |
| Seurat | Used for cell clustering. | Methods / 10x multiome sequence data processing and clustering |
| t-SNE | Used openTSNE to visualize principal components from selected cluster-enriched methylome features, alongside a UMAP view. | Methods / Methylome clustering |
| UCSC liftOver | Mapped human ATAC peaks and DMRs across species with minMatch0.5, and longer10-kb loop anchors/boundaries with minMatch0.3. ATAC targets had to be at most1kb and map back to the same original peak identity. | Methods / Droplet paired-tag / Identification of orthologous sequence elements across species (level 0) |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / 10x multiome sequence data processing and clustering |

XML SHA-256: `09ff276fa0ecc0e114a8e0f9af929fbb974a3124b01ef8ef205ed117198b41fe`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P054: The Microflora Danica atlas of Danish environmental microbiomes

Nature · 2025-12-03 · [DOI](https://doi.org/10.1038/s41586-025-09794-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823411/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cutadapt | Adapter trimming. | Methods / Bacterial and eukaryotic rRNA gene operon library preparation, sequencing and processing |
| DIAMOND | Protein sequence alignment. | Methods / Gene-centric investigation of short-read metagenomes |
| fastp | Read filtering and adapter trimming. | Methods / Short-read metagenomic library preparation, sequencing and processing |
| Guppy | Nanopore basecalling; distinguish the fish name. | Methods / Nearly full-length bacterial 16S rRNA gene amplicon library preparation, sequencing and processing |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Gene-centric investigation of short-read metagenomes |
| MAFFT | Multiple sequence alignment. | Methods / Gene-centric investigation of short-read metagenomes |
| MetaBAT2 | Metagenomic assembly binning and read-depth processing. | Methods / Metagenomic assembly and binning |
| minimap2 | Mapped selected metagenomic short reads to assemblies using minimap2 2.24 -ax sr, followed by SAMtools 1.16.1 view -Sb -F 2308 and sorting. | Methods / Metagenomic assembly and binning |
| SAMtools | Alignment manipulation and QC. | Methods / Metagenomic assembly and binning |
| SingleM | Metagenomic microbial community marker profiling. | Methods / Metagenomic assembly and binning |

XML SHA-256: `36f1b5bf78bf819751b8224a4d682ab83afc0e2e87a1f6a821a53d0a531e80b2`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P055: The molecular cytoarchitecture of the adult mouse brain

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06818-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719111/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellBender | Ambient count background removal. | Methods / Brain preparation / Construction of the brain-wide snRNA-seq dataset / snRNA-seq reads pre-processing |
| Cell Ranger | Demultiplexed and aligned brain-wide snRNA-seq against GRCm39.103 using CellRanger v5.0.1 with an additional include-introns setting. | Methods / Brain preparation / Construction of the brain-wide snRNA-seq dataset / snRNA-seq reads pre-processing |
| Enrichr | Gene-set enrichment. | Methods / Brain preparation / Analyses of activity-dependent gene expression / Transcription factor enrichment |
| fgsea | Tested curated transcription-factor target sets using genes ranked by average Fos correlation in excitatory cells relative to inhibitory background, reversing the inhibitory ranking. Restricted sets to 15–500 genes and used positive one-tailed tests with FDR correction. | Methods / Brain preparation / Analyses of activity-dependent gene expression / Transcription factor enrichment |
| GSEA | Ranked gene-set enrichment. | Methods / Brain preparation / Analyses of activity-dependent gene expression / Transcription factor enrichment |
| Hail | Distributed genotype QC and association. | Methods / Brain preparation / Cell type mapping into the Slide-seq dataset with RCTD / Changes to RCTD for parallelizable throughput |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Brain preparation / Clustering of snRNA-seq data / Leiden clustering |
| MAGMA | Used MAGMA1.10 with a10kb annotation window and GRCh37/1000Genomes to produce gene z-scores, then selected top1000 genes for scDRS after human-to-mouse orthologue conversion; reused precomputed scores for many traits. | Methods / Brain preparation / Heritability enrichment with scDRS |
| RCTD | Spatial cell-type deconvolution. | Methods / Brain preparation / Cell type mapping into the Slide-seq dataset with RCTD |
| Scanpy | Used for exploratory representation. | Methods / Brain preparation / Clustering of snRNA-seq data / Visualization of clusters |
| scDRS | Single-cell disease relevance scoring. | Methods / Brain preparation / Heritability enrichment with scDRS |
| Seurat | Used for cell clustering. | Methods / Brain preparation / Clustering of snRNA-seq data / Quality control of clusters |
| t-SNE | Visualized 250 principal components of cluster-subsampled nuclei using openTSNE 1.0.0, perplexity 350 and exaggeration four. | Methods / Brain preparation / Clustering of snRNA-seq data / Visualization of clusters |

XML SHA-256: `d8fcd4d6b75d84050aeb06ece62bd18c715d5a71b2e5cd291bb5e75afcd05fa6`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P056: Decay of driver mutations shapes the landscape of intestinal transformation

Nature · 2025-12-03 · [DOI](https://doi.org/10.1038/s41586-025-09762-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12804087/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / INCISE adenoma tissue processing and DNA sequencing |
| biobambam2 | Used biobambam2v2.0.79 for adapter clipping and PCR-duplicate marking in hybrid-capture tumor/normal sequencing. | Methods / Hybridization capture panel sequencing |
| BWA | DNA read alignment. | Methods / Hybridization capture panel sequencing |
| dndscv | Somatic mutation selection analysis. | Methods / Identification of driver genes |
| UCSC liftOver | Mapped capture-bait coordinates to mouse GRCm38 before assessing target-gene coverage and downstream tumor/normal variant analysis. | Methods / Hybridization capture panel sequencing |
| Ensembl VEP | Variant consequence annotation. | Methods / Hybridization capture sequencing variant calling and filtering |

XML SHA-256: `43adc653ca98d1d5fb7e866c3e0a89012be1b89f976d820602daa093783ac737`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P057: Structure and operating principles of a monkeypox virus replisome

Nature · 2026-09-02 · [DOI](https://doi.org/10.1038/s41586-026-10937-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645618/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| PyMOL | Generated replisome structural figures with PyMOL v2.5.4 alongside UCSF ChimeraX. | Methods / Model building, refinement and figure generation |

XML SHA-256: `675976b3e545a787dc4b1e4fc4680056a08534b9a45ab494b7fa826dc26bac6a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P058: A transcriptomic taxonomy of mouse brain-wide spinal projecting neurons

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06817-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719099/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / snRNA-seq / SCENIC |
| CellBender | Ambient count background removal. | Methods / snRNA-seq / snRNA-seq preprocessing, quality control and removal of second-order nuclei |
| Cell Ranger | Aligned snRNA-seq to mm10-2020-A with CellRanger v6.1.2. Built separate GFP or mScarlet custom references with v5.0.1 mkref and used v6.1.2 in the reported 10x cloud workflow to detect reporter transcripts. | Methods / snRNA-seq / cDNA amplification and library construction |
| cisTarget | Recovery-curve motif enrichment and regulatory target analysis. | Methods / snRNA-seq / SCENIC |
| Enrichr | Gene-set enrichment. | Methods / snRNA-seq / Differential expression and Gene Ontology analysis |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / snRNA-seq / snRNA-seq preprocessing, quality control and removal of second-order nuclei |
| SCENIC | Single-cell regulatory network inference. | Methods / snRNA-seq / SCENIC |
| sctransform | Applied SCTransform separately to 10x and SSv4 profiles, using 25 versus 30 principal components and an SCT integration workflow with 3000 anchors. | Methods / snRNA-seq / Joint clustering of 10x and SSv4 datasets |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / snRNA-seq / snRNA-seq preprocessing, quality control and removal of second-order nuclei |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / snRNA-seq / cDNA amplification and library construction |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / snRNA-seq / snRNA-seq preprocessing, quality control and removal of second-order nuclei |

XML SHA-256: `75c2262af9c034eff4f733e4f60dd326625ff33c95cd402bd80b7bcf9f8f0608`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P059: A high-resolution transcriptomic and spatial atlas of cell types in the whole mouse brain

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06812-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719114/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Aligned scRNA-seq to M21/GRCm38.p6 using CellRanger v6.1.1 and processed separate 10x Multiome libraries with ARC v2.0 using GRCm38 v98 FASTA and GENCODE M23 GTF references. | Methods / scRNA-seq / Sequencing data processing and QC |
| DoubletFinder | Single-cell doublet detection. | Methods / scRNA-seq / Sequencing data processing and QC |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / scRNA-seq / Joint clustering 10xv2 and 10xv3 datasets |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / scRNA-seq / Differential gene expression analysis |
| scVI | Variational single-cell integration or expression modelling. | Methods / scRNA-seq / Imputation |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / scRNA-seq / Imputation |
| WGCNA | Weighted coexpression networks and modules. | Methods / scRNA-seq / Defining transcription factor co-expression gene modules |

XML SHA-256: `48771a4387270374add8ea5c2127306e44260dc2dc2d82b0ceb358a060ca1b94`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P060: Molecularly defined and spatially resolved cell atlas of the whole mouse brain

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06808-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719103/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ALLCools | Methylome datasets, clustering, integration and differential methylation. | Methods / Integration of MERFISH data with scRNA-seq data |
| CellChat | Ligand-receptor cell communication inference. | Methods / Ligand–receptor analysis and analysis of other genes upregulating in interacting cell pairs |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Spatial module analysis |
| Scanpy | Used for expression normalization. | Methods / Integration of MERFISH data with scRNA-seq data |
| Scrublet | Applied Scrublet to MERFISH profiles after volume correction and normalization to 250, using a score cutoff greater than 0.25 and removing about four percent. | Methods / Preprocessing of MERFISH data |

XML SHA-256: `4755512c20301eedc928e213114e8280d3d6b216d1dcd7a6c1bb830aa4c4ade6`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P061: Single-cell DNA methylome and 3D multi-omic atlas of the adult mouse brain

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06805-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719113/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ALLCools | Merged ALLC profiles, built BaseDS tensors and called differential methylation. | Methods / Cluster-level DNA methylome analysis |
| BEDTools | Merged adjacent/overlapping DMR calls into a non-overlapping catalogue. | Methods / Cluster-level DNA methylome analysis |
| Bismark | Mapped bisulfite reads in single-cell methylome/chromatin-contact workflows. | Methods / Mapping and primary QC |
| Bowtie 2 | Provided short-read alignment within the bisulfite-mapping pipeline. | Methods / Mapping and primary QC |
| cemba-data / YAP | Ran the YAP single-cell methylome/chromatin-contact mapping pipeline. | Methods / Mapping and primary QC |
| cisTarget | Enriched TF motifs in correlated DMR sets using NES thresholds. | Methods / GRN analysis / DMR motif scan and TF motif enrichment analysis |
| Cluster-Buster | Scanned grouped motif PWMs over DMRs with hidden Markov models. | Methods / GRN analysis / DMR motif scan and TF motif enrichment analysis |
| Cutadapt | Demultiplexed single-cell FASTQ reads before mapping. | Methods / Mapping and primary QC |
| deepTools | Used deepTools3.5.1 to profile chromosome-domain boundary probabilities at transcripts over±2Mb with25kb bins. | Methods / Cell and cluster-level chromatin conformation analysis / Domain boundary probability and transcript body mC fraction correlation |
| Enrichr | Tested GO enrichment for genes overlapping selected chromatin bins. | Methods / Cell and cluster-level chromatin conformation analysis / Compartment score and mC fraction correlation |
| kallisto | Quantified SMART-seq at isoform level with kallisto using the same GTF-backed transcript models as the transcriptome/methylome analysis,then calculated cell-group TPM and exon PSI from transcript counts. | Methods / Intragenic epigenetic and transcriptomic isoform analysis / Integration and isoform quantification of the SMART-seq dataset |
| Leiden | Biclustered methylome/RNA clusters from a weighted confusion-matrix graph. | Methods / Atlas-level data integration and cluster annotation / Integration between snmC-seq and scRNA-seq or SMART-seq datasets |
| Picard | Processed and quality-checked alignment files within the mapping pipeline. | Methods / Mapping and primary QC |
| qnorm | Used qnorm0.8.0 to quantile-normalize transcript-body methylation fractions and25kb boundary probabilities along cell subclasses before differential/Pearson analyses. | Methods / Cell and cluster-level chromatin conformation analysis / Domain boundary probability and transcript body mC fraction correlation |
| SAMtools | Processed mapped BAM files and assessed alignment quality. | Methods / Mapping and primary QC |
| Scanpy | Supported iterative Leiden clustering at reported low resolutions. | Methods / Methylome clustering analysis / Methylome clustering |
| SCENIC+ | Supplied motif PWMs and a bundled motif-scanning implementation. | Methods / GRN analysis / DMR motif scan and TF motif enrichment analysis |
| scHiCluster | Imputed contact matrices using convolution and random-walk restart. | Methods / Cell and cluster-level chromatin conformation analysis / Generation of the chromatin contact matrix and imputation |
| Seurat | Adapted anchor finding/scoring for methylome–transcriptome integration. | Methods / Atlas-level data integration and cluster annotation / Integration of methylome and transcriptome profiles |
| SkyPilot | Provisioned cloud analysis environments for large tensor workflows. | Methods / Analysis infrastructures |
| Snakemake | Orchestrated the large-scale analysis workflows. | Methods / Analysis infrastructures |
| STAR | Produced gene-level counts for SMART-seq cross-modality integration. | Methods / Intragenic epigenetic and transcriptomic isoform analysis / Integration and isoform quantification of the SMART-seq dataset |

XML SHA-256: `f95adca75114b3a845747d11afb578d29e9272a860b2b5161493c841a0f19107`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P062: Aberrant excitatory neuronal ERBB4 promotes Alzheimer’s disease pathology

Nature · 2026-08-26 · [DOI](https://doi.org/10.1038/s41586-026-10964-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668712/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Used Cell Ranger v8.0.1 mkfastq to demultiplex BCL files, count against mm10-2020-A for barcode/UMI expression processing and reported clustering/differential expression, then aggr for multiple sequencing runs. | Methods / snRNA-seq analysis |
| GSEA | Ranked gene-set enrichment. | Methods / snRNA-seq analysis |
| gseapy | Used gseapy for gene-set enrichment downstream of single-cell processing. | Methods / snRNA-seq analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / snRNA-seq analysis |
| MAGIC | Used MAGIC-imputed counts to visualize violin plots in the single-cell workflow. | Methods / snRNA-seq analysis |
| PyWGCNA | Used PyWGCNA for coexpression-network analysis in the single-cell workflow. | Methods / snRNA-seq analysis |
| Scanpy | Single-cell expression processing and analysis. | Methods / Python packages for data analysis |
| Scrublet | Called doublets using scanpy.pp.scrublet with defaults in Scanpy 1.10.4; later Pearson residuals and SysVI analyses were separate. | Methods / snRNA-seq analysis |
| scVI | Variational single-cell integration or expression modelling. | Methods / Python packages for data analysis |
| SysVI | Used SysVI on Pearson-normalized highly variable genes, with 100-200 training epochs for total clusters and 5-30 for individual cell types. | Methods / snRNA-seq analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / snRNA-seq analysis |
| WGCNA | Weighted coexpression networks and modules. | Methods / snRNA-seq analysis |

XML SHA-256: `bcbebc68b9d568732820eb94dd23b42e575f6d5d037d28d9eabf64e4ad83d61f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P063: CD8+ T cell stemness precedes post-intervention control of HIV viraemia

Nature · 2025-12-01 · [DOI](https://doi.org/10.1038/s41586-025-09932-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12872466/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Single-cell multiomics |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / Single-cell multiomics |
| Cell Ranger | Used Cell Ranger multi v9.0.0 with GEX GRCh38-2020-A and VDJ GRCh38-alts-ensembl-5.0.0 references for GEX, antibody capture and TCR libraries; surface/hashtag barcodes were Antibody Capture features. | Methods / Single-cell multiomics |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Single-cell multiomics |

XML SHA-256: `ec0dcdc2560ab96a668871d969a4d02fc07a641b5d6b9bf9116358f9ed1b5e51`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P064: Evolution of neuronal cell classes and types in the vertebrate retina

Nature · 2023-12-13 · [DOI](https://doi.org/10.1038/s41586-023-06638-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719112/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Reported Cell Ranger v4.0.0 demultiplexing/alignment for nuclei libraries and v7.0 alignment of scRNA/snRNA datasets to species-specific transcriptomes to obtain BAMs. | Methods / snRNA-seq / Library preparation |
| Harmony | Batch integration of cell embeddings. | Methods / Statistics and reproducibility |
| scVI | Variational single-cell integration or expression modelling. | Methods / Statistics and reproducibility |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Computational analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Computational analysis / Integration and clustering to identify species-specific types for photoreceptors, horizontal cells, bipolar cells and RGCs |
| velocyto | Spliced/unspliced counting and RNA velocity. | Methods / Preprocessing of transcriptomic data |

XML SHA-256: `fb21e2378bd808a5120b419e96366a7a2b28e111149e04b18d6c4eeb89d52daa`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P065: Cell-type-directed design of synthetic enhancers

Nature · 2023-12-12 · [DOI](https://doi.org/10.1038/s41586-023-06936-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10830415/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / OmniATAC-seq |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Motif annotation |
| BWA-MEM2 | Mapped trimmed reads to custom hg38 augmented with integrated sequences as extra chromosomes using BWA-MEM2 2.2.1, before SAMtools cleanup and deepTools RPGC tracks. | Methods / OmniATAC-seq |
| cisTarget | Recovery-curve motif enrichment and regulatory target analysis. | Methods / Motif annotation |
| Cluster-Buster | Clustered DNA motif scanning. | Methods / Motif annotation |
| deepTools | Generated RPGC-normalized ATAC and ChIP bigWigs with deepTools3.5.0 bamCoverage after BWA-MEM2 alignment to ordinary/custom hg38 and SAMtools cleanup. A different paragraph uses the pyBigWig stats library for fly-window means. | Methods / OmniATAC-seq |
| MACS | Called peaks with MACS2 2.1.2.1 callpeak after bwa-mem2 alignment, SAMtools sorting/deduplication and blacklist removal; generated separate RPGC-normalized bigWig tracks with deepTools. | Methods / ChIP–seq |
| pyBigWig | Used pyBigWig stats to obtain mean fly-genome accessibility for500bp windows at50bp stride when comparing DeepFlyBrain predictions with measured accessibility. | Methods / Scoring the fly genome |
| SAMtools | Alignment manipulation and QC. | Methods / OmniATAC-seq |
| SHAP | Model feature attribution. | Methods / Nucleotide contribution scores |
| Trim Galore | Removed adapters with TrimGalore 0.6.7 in separate Omni-ATAC-seq and ChIP-seq workflows before bwa-mem2 mapping and subsequent assay-specific processing. | Methods / OmniATAC-seq |

XML SHA-256: `644c378b160cecd1c5249fcf19e7cab7ce35e80822d0182a54998f83e8084a83`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P066: Insights into longevity and virus-driven adaptation from Myotis bat genomes

Nature · 2026-08-26 · [DOI](https://doi.org/10.1038/s41586-026-10932-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13626957/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Phylogenetics |
| MEME | Motif discovery and scanning. | Methods / Selection scans and evolutionary rates / BUSTED |
| minimap2 | Aligned masked bat genomes at the assembly level with minimap2 before SyRI structural comparison and plotsr visualization. | Methods / Structural variation |
| plotsr | Synteny and structural-rearrangement visualization. | Methods / Structural variation |
| SyRI | Assembly-to-assembly structural rearrangement analysis. | Methods / Structural variation |

XML SHA-256: `b688f442bff972b85dc77f7c9b9899dac740bfc5bac0f7382dddbcd6994e5bb0`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P067: Targeted design of synthetic enhancers for selected tissues in the Drosophila embryo

Nature · 2023-12-12 · [DOI](https://doi.org/10.1038/s41586-023-06905-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10830412/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Deep learning sequence-to-accessibility models / Prediction on full Drosophila genome |
| DeepSTARR | Sequence-based neural prediction of regulatory activity. | Methods / Deep learning sequence-to-accessibility models / Model architecture and training |
| MACS | Called tissue pseudobulk Drosophila sci-ATAC peaks with MACS2 using no model, keep all duplicates, extension 200, shift -100, fly effective-genome shortcut and bedGraph output. | Methods / Processing of pseudo-bulk DNA accessibility data |

XML SHA-256: `535a141b0d00e993c45d6476f4d80ef675e0155b45778a2bba9c0f732ea0aecf`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P068: Long-read sequencing reveals pre-meiotic gene conversion in sperm

Nature · 2026-08-26 · [DOI](https://doi.org/10.1038/s41586-026-10901-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600919/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| FIMO | Detected PRDM9 motifs using FIMO and the PRDM9 A/A PWM supplied by Hinch et al. Scanned recombinant reads and a separate control set of 100,000 randomly sampled CCS reads; retained matches with q < 0.01. | Methods / PRDM9 motif detection in recombinant reads |
| HiFiAdapterFilt | Adapter-contamination filtering for PacBio HiFi reads. | Methods / Read alignment |
| hifiasm | Haplotype-resolved long-read assembly. | Methods / De novo haplotype-resolved assembly |
| minimap2 | Mapped filtered HiFi reads to both haplotypes using minimap2 2.26-r1175 -ax map-hifi --cs=short --eqx --MD. | Methods / Read alignment |
| SAMtools | Alignment manipulation and QC. | Methods / Read alignment |

XML SHA-256: `067d2aafd6add9be9d1fc8abda780d58cbe34a6fedd2efa34376d31c483e5159`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P069: Dictionary of immune responses to cytokines at single-cell resolution

Nature · 2023-12-06 · [DOI](https://doi.org/10.1038/s41586-023-06816-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781646/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Processed raw BCL data with the CellRanger v3.0 Gene Expression pipeline for demultiplexing, mm10 alignment and transcriptomic count matrices. | Methods / scRNA-seq data pre-processing |
| clusterProfiler | Assessed Hallmark enrichment of the 30 highest-weight genes in cytokine-responsive NMF programs. Separately ran GO biological-process GSEA on IL-18-minus-PBS mean-expression differences in NK cells and displayed terms with FDR-adjusted P below 0.1. | Methods / Identification of GPs per cytokine treatment and per cell type |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / scRNA-seq data pre-processing |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / scRNA-seq data pre-processing |
| t-SNE | Visualized single-cell cytokine-response profiles using 75 principal components selected after variable-gene filtering; Louvain supplied the global clusters. | Methods / scRNA-seq data pre-processing |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Identification of cellular polarization states |

XML SHA-256: `1f5dc9f2d83bde84f2d970e9c3af4b6586ef50309493d74c8848a55e4a61b3c5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P070: A human embryonic limb cell atlas resolved in space and time

Nature · 2023-12-06 · [DOI](https://doi.org/10.1038/s41586-023-06806-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC7616500/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Regulon analysis of transcription factors |
| cell2location | Spatial cell-type deconvolution. | Methods / Deconvolution of human Visium data using cell2location |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Methods / Cell–cell communication analysis of human scRNA-seq data |
| Cell Ranger | Aligned and quantified droplet scRNA-seq using Cell Ranger v3.0.2 with human GRCh38-3.0.0 or mouse mm10-2020-A references. | Methods / Alignment, quantification and quality control of scRNA-seq data |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Clustering and annotation of scRNA-seq data |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Digit region analysis of Visium data |
| Scanpy | Used for marker or differential-expression analysis. | Methods / Digit region analysis of Visium data |
| SCENIC | Single-cell regulatory network inference. | Methods / Regulon analysis of transcription factors |
| Scrublet | Computed Scrublet scores per 10x lane, then replaced scores by medians within Scanpy clusters/reclusters and used a right-tail normal approximation, MAD-derived spread and BH adjustment; marker review removed further clusters. | Methods / Doublet detection of scRNA-seq data |
| scVelo | RNA velocity analysis. | Methods / RNA velocity calculations for mesenchymal compartment |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Alignment and quantification of human Visium data |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Alignment and quantification of human Visium data |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Data preprocessing and integration of scRNA-seq data |

XML SHA-256: `95a862241ab6a0d94e137e32fdddf4cf5e54469a860271c361cca3ce01a0578f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P071: Ancient DNA from Shimao city records kinship practices in Neolithic China

Nature · 2025-11-26 · [DOI](https://doi.org/10.1038/s41586-025-09799-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12711557/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Population structure analysis |
| ancIBD | Identity-by-descent detection in ancient genomes. | Methods / Relatedness analyses |
| BWA | DNA read alignment. | Methods / Read alignment and variant calling |
| GLIMPSE | Low-coverage sequence genotype imputation. | Methods / Relatedness analyses |
| PLINK | Used for genotype quality control; LD-based variant selection. | Methods / Population structure analysis |
| smartpca | Genetic ancestry principal components. | Methods / Population structure analysis |

XML SHA-256: `0706316643483beae85d52dd959c6615e3f3f1ece9778a7d5b712e6126db9772`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P072: Multi-ancestry genome-wide association meta-analysis of Parkinson’s disease

Nature Genetics · 2023-12-28 · [DOI](https://doi.org/10.1038/s41588-023-01584-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786718/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Methods / Functional annotation and GSEA |
| CADD | Variant deleteriousness annotation. | Fine-mapping identifies six credible sets with single variants |
| CrossMap | Genome-assembly coordinate conversion with format-specific interval, alignment and variant handling. | Methods / MAMA |
| Eagle | Genotype phasing. | Methods / 23andMe diverse ancestry data |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Used MAGMA gene,competitive gene-set and gene-property analyses with an ancestry-proportioned custom1000Genomes panel,MSigDB7,GTEx8 and single-cell expression. Applied BH-FDR and conditional reanalysis of significant terms. | Methods / Functional annotation and GSEA |
| LDSC | Used for SNP heritability estimation. | Methods / Estimation of population-specific or shared causal variants at associated loci |
| MAGMA | Used MAGMA gene,competitive gene-set and gene-property analyses with an ancestry-proportioned custom1000Genomes panel,MSigDB7,GTEx8 and single-cell expression. Applied BH-FDR and conditional reanalysis of significant terms. | Methods / Functional annotation and GSEA |
| Minimac | Genotype imputation. | Methods / 23andMe diverse ancestry data |
| MR-MEGA | Trans-ancestry meta-regression. | Methods / MAMA |
| PLINK | Used for genetic association analysis. | Methods / MAMA |
| SMR | Summary-data molecular association or Mendelian randomization. | Methods / Functional annotation and GSEA |

XML SHA-256: `0f0a5a3bcb3a832a58367b749f5899e7dccab771e3a51cb2ead7cf8170e2e138`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P073: Single-cell RNA sequencing of peripheral blood links cell-type-specific regulation of splicing to autoimmune and inflammatory diseases

Nature Genetics · 2024-12-03 · [DOI](https://doi.org/10.1038/s41588-024-02019-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631754/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Genotype data processing and QC |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Quantification of mRNA AS |
| Cell Ranger | Reported Cell Ranger v7.0.1 in the initial pooled 5-prime RNA-seq processing/QC workflow with DRAGEN and Freemuxlet, using GRCh38/GENCODE32. A Cell Ranger barcode whitelist was reused for separate STARsolo splicing alignment. | Methods / scRNA-seq data processing and QC |
| coloc | Shared-signal colocalization analysis. | Methods / Complex trait associations |
| deepTools | Used deepTools3.5.1 to visualize read distribution after STARsolo two-pass/WASP alignment, barcode/UMI-aware duplicate handling and selection of unique proper pairs passing WASP. | Methods / Quantification of mRNA AS |
| DRAGEN | Sequencing alignment, variant or RNA processing platform; named workflow must be identified. | Methods / scRNA-seq data processing and QC |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Methods / Complex trait associations |
| LeafCutter | Intron excision quantification and splicing analysis. | Methods / Quantification of mRNA AS |
| mashr | Multivariate adaptive shrinkage. | Methods / cis-sQTL mapping |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Quantification of mRNA AS |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Methods / Genotype data processing and QC |
| QTLtools | Molecular QTL mapping and QC. | Methods / cis-sQTL mapping |
| SAMtools | Alignment manipulation and QC. | Methods / Quantification of mRNA AS |
| Seurat | Used for dataset integration; exploratory representation. | Methods / scRNA-seq data processing and QC |
| Slingshot | Lineage and pseudotime inference. | Methods / Dynamic intron use and dynamic sQTL mapping in B cell development |
| STARsolo | Aligned RNA-seq to GRCh38/GENCODE v32 with STARsolo 2.7.10a, 1MM barcode-whitelist matching, directional UMI-tools deduplication and two-pass splice discovery. Used per-sample post-QC variants for WASP mapping-bias filtering before separate Picard deduplication and unique/proper-pair selection. | Methods / Quantification of mRNA AS |
| WASP | Applied STARsolo waspOutputMode using each sample's post-QC VCF to reduce allelic mapping bias before retaining unique proper pairs that passed WASP filtering. | Methods / Quantification of mRNA AS |

XML SHA-256: `1c5a9bc6f69280e434474ef1cb19729d0fb6bcbb8977c0daf6a68765553bdfe4`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P074: Genomic and transcriptomic analyses of aortic stenosis enhance therapeutic target discovery and disease prediction

Nature Genetics · 2025-12-19 · [DOI](https://doi.org/10.1038/s41588-025-02417-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807872/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Methods / Transcriptome-wide association analysis |
| DEPICT | Gene-set and tissue enrichment of GWAS loci. | Methods / Gene-set enrichment |
| Enrichr | Gene-set enrichment. | Methods / Gene-set enrichment |
| GWAMA | GWAS meta-analysis. | Methods / Meta-analysis |
| LDpred | LD-aware Bayesian polygenic score construction. | Methods / Development of an AS PRS |
| LDSC | Used for association inflation assessment. | Methods / Meta-analysis |
| MetaXcan | Predicted expression association. | Methods / Transcriptome-wide association analysis |
| PolyPhen-2 | Missense variant effect annotation. | Methods / Causal gene prioritization |
| PrediXcan | Genetically predicted expression association. | Methods / Transcriptome-wide association analysis |
| PRS-CS | Inferred ankylosing-spondylitis PRS weights from UKB/MGBB-excluded GWAS with matched-population 1000 Genomes LD panels; the multi-ancestry GWAS used a European panel. PRS-CS evaluated phi=1e-6,1e-4,1e-2,1 with a=1,b=0.5 before separate MGBB tuning. | Methods / Development of an AS PRS |
| QTLtools | Molecular QTL mapping and QC. | Methods / Transcriptome-wide association analysis |
| REGENIE | Participating aortic-stenosis studies ran ancestry-stratified autosomal GWAS with either SAIGE or REGENIE; sex-stratified analyses also included the X chromosome after cohort-specific QC/imputation. | Methods / Study populations and phenotyping |
| SAIGE | Participating aortic-stenosis consortium studies used SAIGE or REGENIE for ancestry-stratified autosomal GWAS and sex-stratified autosomal/X-chromosome analyses. | Methods / Study populations and phenotyping |
| SIFT | Missense variant effect annotation. | Methods / Causal gene prioritization |
| UCSC liftOver | Converted cohort GWAS summary-statistic coordinates from hg19 to hg38 using reported LiftOverv1.04.00 before consortium meta-analysis. | Methods / Meta-analysis |

XML SHA-256: `d4b68aa51027857906d62e3b78dd5a1668cfc73303794a8f813059da6613cabb`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P075: Single-allele nanoscale mapping of regulatory variants

Nature Genetics · 2026-10-02 · [DOI](https://doi.org/10.1038/s41588-026-02776-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649568/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Allele-specific RNA-seq analysis |
| Bowtie 2 | Short-read alignment. | Methods / Single-allele MCC analysis |
| FLASH | After adapter removal with Trim Galore 0.3.1, reconstructed full paired reads using FLASH 1.2.11 in single-allele MCC analysis. Subsequent stages used BLAT 35 on capture-site mini-chromosomes, custom splitting and Bowtie2 alignment, allele assignment and ligation-junction analysis. | Methods / Single-allele MCC analysis |
| Trim Galore | Removed sequencing adapters with Trim Galore 0.3.1 before FLASH 1.2.11 paired-read reconstruction in single-allele MCC analysis; BLAT and custom SNP/ligation-junction analysis followed. | Methods / Single-allele MCC analysis |

XML SHA-256: `51f441979443877ef800499f784778a4450928a334b6f10eec6c1b41230ac93e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P076: BridgePRS leverages shared genetic effects across ancestries to increase polygenic risk score portability

Nature Genetics · 2023-12-20 · [DOI](https://doi.org/10.1038/s41588-023-01583-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786716/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BridgePRS | Cross-ancestry polygenic score transfer using shared genetic effects. | Results / Overview of BridgePRS method |
| IMPUTE | Genotype imputation. | Methods / Application to BioMe |
| LDSC | Used for SNP heritability estimation. | Methods / Trait selection |
| METAL | GWAS meta-analysis. | Methods / Application of PRS-CSx |
| PLINK | Used for LD-based variant selection; genetic association analysis. | Methods / The BridgePRS model / Definition of loci |
| PLINK 2 | Used PLINK v.2.0 --glm for ancestry-specific UKB quantitative-trait GWAS with sex, age, genotyping batch, assessment center and 40 PCs; blood biochemistry also includes fasting time and dilution factor. Converted imputed variants to hard calls with biallelic, MAF, missingness and MACH-R2 filters. Separate training/test/validation GWASs and additional European-test GWASs supplied the PRS portability comparisons. | Methods / Trait selection |
| PRS-CS | Benchmarked PRS-CS-mult by fitting separate single-population PRS-CS models and learning a linear combination of their scores using training and evaluation partitions. | Results / Benchmarking methods via simulation |
| PRS-CSx | Cross-population Bayesian polygenic scoring. | Results / Benchmarking methods via simulation |
| PRSice | Clumping and thresholding polygenic score construction. | Results / Benchmarking methods via simulation |

XML SHA-256: `b656785a4313898b532cf0f8f66f83244a2eab91d35667e240999b2b68138619`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P077: Multitrait analyses identify genetic variants associated with aortic valve function and aortic stenosis risk

Nature Genetics · 2025-12-19 · [DOI](https://doi.org/10.1038/s41588-025-02397-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807864/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| GSEA | Ranked gene-set enrichment. | Methods / Tissue and cell-type enrichment with MAGMA |
| MAGMA | Used MAGMA1.09b defaults with GRCh37 annotations and a precomputed European1000Genomes reference; genes and gene sets were tested with per-trait BH-FDR below0.05. | Methods / MAGMA gene-set analysis |
| METAL | GWAS meta-analysis. | Methods / Study overview |
| MTAG | Multi-trait GWAS analysis. | Methods / Study overview |
| REGENIE | Ran phenotype GWAS with REGENIE 2.2.4, adjusting for sex, age, age squared at cMRI, array, scanner identifier and ten genetic ancestry PCs. | Methods / GWAS |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Mendelian randomization for aortic valve function and aortic stenosis |

XML SHA-256: `bf145faa08ab089977ae3be06d6c1927a49fef4a65527e52b28eadfddd351803`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P078: Accurate detection of identity-by-descent segments in human ancient DNA

Nature Genetics · 2023-12-20 · [DOI](https://doi.org/10.1038/s41588-023-01582-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786714/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ancIBD | Identity-by-descent detection in ancient genomes. | Results / Evaluating ancIBD / Comparing to other methods |
| GLIMPSE | Low-coverage sequence genotype imputation. | Methods / The HMM |

XML SHA-256: `3761b24b51f04ecdc046d46240880739507e092ac4d54fd813ea0b50fbe9e0bc`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P079: Characterizing the evolutionary dynamics of cancer proliferation in single-cell clones with SPRINTER

Nature Genetics · 2024-11-29 · [DOI](https://doi.org/10.1038/s41588-024-01989-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11735394/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DESeq2 | Count-based differential expression analysis. | Methods / Identifying clone-specific ART |
| gseapy | Python gene-set enrichment analysis. | Methods / Identifying clone-specific ART |
| SPRINTER | Single-cell replication and copy-number analysis. | Methods / The SPRINTER algorithm |

XML SHA-256: `7caba2fdd0455dc9c05e509ae46a0a19b3f1db1d22b2cab66bd75ebe3c8312a5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P080: Reactivation of the embryonic ζ-globin gene ameliorates severe forms of α-thalassemia

Nature Genetics · 2026-10-01 · [DOI](https://doi.org/10.1038/s41588-026-02770-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649559/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / Next-generation sequencing (NGS) and data analysis |
| deepTools | Normalized sorted/indexed ATAC-seq and ChIPmentation BAMs as RPKM and converted them to bigWig with deepTools3.5.6 for UCSC browser views. | Methods / Next-generation sequencing (NGS) and data analysis |
| FastQC | Sequencing read quality assessment. | Methods / Next-generation sequencing (NGS) and data analysis |
| SAMtools | Alignment manipulation and QC. | Methods / Next-generation sequencing (NGS) and data analysis |

XML SHA-256: `8e3555478014fd895189c529a597b248c887305b63ff1c6d00f3335f04bfa234`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P081: The BAF chromatin remodeler synergizes with RNA polymerase II and transcription factors to evict nucleosomes

Nature Genetics · 2023-12-04 · [DOI](https://doi.org/10.1038/s41588-023-01603-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786724/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Sequencing, data processing, data analysis and data visualization |
| Bowtie 2 | Short-read alignment. | Methods / Sequencing, data processing, data analysis and data visualization |
| deepTools | Used deepTools3.5.1 for profiles,heatmaps and correlation matrices; supplied scores were averaged in nonoverlapping50bp reference-point bins and plotted as means. | Methods / Sequencing, data processing, data analysis and data visualization |

XML SHA-256: `49acd066c651ad9ccf081aa7344fbbd5d93769ed5dfac7d8854cda90ef8f7f63`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P082: Single-cell multiomics analysis reveals dynamic clonal evolution and targetable phenotypes in acute myeloid leukemia with complex karyotype

Nature Genetics · 2024-11-25 · [DOI](https://doi.org/10.1038/s41588-024-01999-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631769/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Arriba | RNA-seq gene-fusion detection. | Methods / Fusion transcript detection from bulk RNA-seq |
| AUCell | Rank-based gene-set activity scoring. | Methods / scNOVA–CITE workflow / Molecular phenotype analysis in gene sets |
| Cell Ranger | Aligned CITE-seq reads to GRCh38, distinguished cells from background and generated a unified gene-expression/cell-surface-protein feature-barcode matrix with Cell Ranger v6.0. | Methods / CITE-seq data pre-processing and integration |
| clusterProfiler | Used enricher hypergeometric overrepresentation to test Hallmark overlap with genes differentially expressed between subclones, controlling FDR at 0.05. | Methods / scNOVA–CITE workflow / Molecular phenotype analysis in gene sets |
| scran | Single-cell normalization and variance modelling. | Methods / scNOVA–CITE workflow / Finding differentially expressed features between subclones |
| sctransform | Used Seurat SCTransform regularized negative-binomial normalization of RNA before highly variable-gene PCA; antibody-derived tags used CLR separately. | Methods / CITE-seq data pre-processing and integration / Pre-processing and dimensional reduction of CITE-seq data |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / CITE-seq data pre-processing and integration / Quality control of CITE-seq data |
| SingleR | Reference-based cell annotation. | Methods / scNOVA–CITE workflow / ‘Reference-based’ annotation of leukemic cells |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Fusion transcript detection from bulk RNA-seq |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / CITE-seq data pre-processing and integration / Weighted nearest neighbor analysis of CITE-seq data |

XML SHA-256: `c46b71277955179cdc573c3bf2131f9e385491f06ebfc680ddfce7143dc1077e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P083: ArchMap is a web-based platform for reference-based analysis of single-cell datasets

Nature Genetics · 2026-09-30 · [DOI](https://doi.org/10.1038/s41588-026-02756-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649598/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ArchMap | Reference-based single-cell analysis. | Methods / Atlas preprocessing |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Performance metrics |
| Scanpy | Single-cell expression processing and analysis. | Methods / Framework architecture / Software tools for analysis |
| scArches | Single-cell reference transfer. | Methods / Label transfer and uncertainty quantification |
| scVI | Variational single-cell integration or expression modelling. | Methods / Mapping procedure |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Number of epochs for mapping |

XML SHA-256: `f02f643499d1a41663176713673b15200b1b4478947d7f7f0d3ad1785ff804f0`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P084: Perturbational phenotyping of human blood cells reveals genetically determined latent traits associated with subsets of common diseases

Nature Genetics · 2023-12-04 · [DOI](https://doi.org/10.1038/s41588-023-01600-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10786715/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Eagle | Genotype phasing. | Methods / Genotyping, quality control and imputation in screening cohort |
| IMPUTE | Genotype imputation. | Methods / Genotyping, quality control and imputation in UKBB cohort |
| KING | Used the KING-robust kinship estimator through PLINK2 and removed five individuals with kinship greater than 0.177 from the screening cohort. | Methods / Genotyping, quality control and imputation in screening cohort |
| Minimac | Genotype imputation. | Methods / Genotyping, quality control and imputation in screening cohort |
| PLINK | Processed screening-cohort genotype data using both PLINK1.9 and PLINK2. The shared workflow excludes sample/variant missingness above 10%, sex mismatches and variants with HWE P<1e-50, followed by a separate Michigan-server imputation workflow. | Methods / Genotyping, quality control and imputation in screening cohort |
| PLINK 2 | Used PLINK2 in the shared genotype-processing workflow, then estimated ten genetic PCs and KING-robust kinship in PLINK2. Removed five individuals with kinship greater than 0.177. PLINK1.9 is separately named in the same paragraph. | Methods / Genotyping, quality control and imputation in screening cohort |
| SHAPEIT | Genotype haplotype phasing. | Methods / Genotyping, quality control and imputation in UKBB cohort |

XML SHA-256: `b3f14dd0f3ad7e6c7ddd94237f00324876e5f04afce55ee61158cb6905927334`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P085: Spatial and single-nucleus transcriptomic analysis of genetic and sporadic forms of Alzheimer’s disease

Nature Genetics · 2024-11-22 · [DOI](https://doi.org/10.1038/s41588-024-01961-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631771/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellBender | Ambient count background removal. | Methods / Preprocessing gene expression data |
| CellChat | Ligand-receptor cell communication inference. | Methods / Cell–cell signaling analysis |
| Enrichr | Gene-set enrichment. | Methods / Differential gene expression analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Reprocessing publicly available single-nucleus gene expression datasets |
| kallisto | Used the count workflow from kallisto bustools to pseudoalign published Alzheimer snRNA-seq libraries against GRCh38 and obtain gene/UMI matrices. | Methods / Reprocessing publicly available single-nucleus gene expression datasets |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Initial snRNA-seq data analysis |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Spatial proteomics data analysis |
| Milo | Single-cell neighborhood differential abundance. | Methods / Differential cell state abundance testing in the snRNA-seq datasets |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Identifying amyloid-associated gene expression signatures in human and mouse |
| Scanpy | Single-cell expression processing and analysis. | Methods / Initial snRNA-seq data analysis |
| scArches | Single-cell reference transfer. | Methods / Reference-based integration of snRNA-seq datasets |
| scDRS | Single-cell disease relevance scoring. | Methods / Polygenic disease enrichment analysis in ST and snRNA-seq |
| Scrublet | Applied Scrublet 0.2.3 with defaults per experiment after splitpipe and CellBender 0.2.0; percentile-based doublet/count/mitochondrial QC followed. | Methods / Preprocessing gene expression data |
| scVI | Variational single-cell integration or expression modelling. | Methods / Initial snRNA-seq data analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / ST clustering analysis |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Preprocessing gene expression data |
| splitpipe | Processed single-cell reads with splitpipe before CellBender and per-experiment doublet detection. | Methods / Preprocessing gene expression data |
| UCell | Rank-based single-cell gene-set scoring. | Methods / Co-expression network analysis in mouse ST |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Initial snRNA-seq data analysis |

XML SHA-256: `059ce76dfa2d2ef1d510a2364803cd1b61962a88aa5a5a0b66fe67de32ccd8e5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P086: Investigation of a global mouse methylome atlas reveals subtype-specific copy number alterations in pediatric cancer models

Nature Genetics · 2025-12-11 · [DOI](https://doi.org/10.1038/s41588-025-02419-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807868/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DESeq2 | Count-based differential expression analysis. | Methods / Transcriptome comparison |
| Integrative Genomics Viewer (IGV) | Visualized human regions syntenic to mouse chromosomes with custom SEG files in IGV and generated human summary plots from combined segmentation files;Affinity Designer prepared the final artwork. | Methods / CNA synteny comparison |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Data analysis |
| minfi | Methylation-array preprocessing. | Methods / Human–mouse comparison |
| SeSAMe | Methylation-array preprocessing. | Methods / Data analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Data analysis |

XML SHA-256: `128b74558684f7bd74506858cfb8d15baa0ff9db51ac0debef173a96732c5d49`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P087: A human single-cell atlas identifies OLR1+ scar-associated macrophages as a potential therapeutic target for chronic liver disease

Nature Genetics · 2026-09-28 · [DOI](https://doi.org/10.1038/s41588-026-02774-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649586/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Generated human feature-barcode matrices with Cell Ranger v7.0.0/GRCh38-2020-A, including liver-biopsy processing. Mouse CCl4 data used v3.0.2/mm10-3.0.0, whereas mouse BDL data used v7.0.0/mm10-2020-A. | Methods / scRNA-seq analysis / Human data processing, QC and integration |
| edgeR | Count-based differential expression analysis. | Methods / Human liver bulk RNA-seq analysis |
| Harmony | Batch integration of cell embeddings. | Methods / scRNA-seq analysis / Human data processing, QC and integration |
| scran | Single-cell normalization and variance modelling. | Methods / scRNA-seq analysis / Clustering, annotation and atlas construction |
| sctransform | Applied standalone sctransform 0.4.2 vst() to bulk liver counts for PCA-based outlier detection. edgeR TMM/CPM and rank-based testing were separate. | Methods / Human liver bulk RNA-seq analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / scRNA-seq analysis / Human data processing, QC and integration |
| SoupX | Ambient RNA contamination correction. | Methods / scRNA-seq analysis / Human biopsy scRNA-seq analysis |

XML SHA-256: `d647fee1d2623c8d4c58ec1d36b7116037e311add277e5f5a258a1ed2a502593`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P088: Genome-wide association study reveals mechanisms underlying dilated cardiomyopathy and myocardial resilience

Nature Genetics · 2024-11-21 · [DOI](https://doi.org/10.1038/s41588-024-01975-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631763/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Methods / Locus definitions, variant annotation and gene prioritization / Functional mapping and annotation processing and annotation |
| CAUSE | Causal inference with correlated pleiotropy. | Methods / MR for DCM |
| CellBender | Ambient count background removal. | Methods / Single-cell expression and DE |
| coloc | Shared-signal colocalization analysis. | Methods / Locus definitions, variant annotation and gene prioritization / MR and colocalization for eQTLs and pQTLs |
| FUMA | Functional mapping, annotation and gene prioritization. | Methods / Locus definitions, variant annotation and gene prioritization / Functional mapping and annotation processing and annotation |
| g:Profiler | Functional gene-set enrichment. | Methods / Gene set enrichment analyses |
| LDSC | Used for SNP heritability estimation. | Methods / Heritability and genetic correlations |
| MAGMA | Computed gene-level associations with MAGMA1.10 using European1000Genomes Phase3 LD, then supplied those results to PoPS0.2 feature-based prioritization for18383 genes. | Methods / Locus definitions, variant annotation and gene prioritization / Polygenic priority score |
| METAL | GWAS meta-analysis. | Methods / GWAS meta-analyses |
| MTAG | Multi-trait GWAS analysis. | Methods / Multitrait analyses |
| PLINK 2 | Used for LD-based variant selection. | Methods / Locus definitions, variant annotation and gene prioritization / MR and colocalization for eQTLs and pQTLs |
| PoPS | Effector-gene prioritization using polygenic features. | Methods / Locus definitions, variant annotation and gene prioritization / Polygenic priority score |
| REGENIE | Ran NICM and stricter NI-DCM case-control GWAS with REGENIE in FinnGen, UKB and MGB after biobank-specific ICD definitions and heart-failure exclusions from controls. | Methods / GWAS for dilated cardiomyopathy |
| Seurat | Used for expression normalization. | Methods / Single-cell expression and DE |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Locus definitions, variant annotation and gene prioritization / MR and colocalization for eQTLs and pQTLs |
| UCSC liftOver | Converted GWAS summary-statistic coordinates to GRCh38 where needed before METAL fixed-effect meta-analysis of DCM/NICM datasets. | Methods / GWAS meta-analyses |

XML SHA-256: `9a809b741a416b28a354287c263b3662ef5137e6e616b1855b999b9355534623`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P089: Noncoding mutations cause super-enhancer retargeting resulting in protein synthesis dysregulation during B cell lymphoma progression

Nature Genetics · 2023-12-04 · [DOI](https://doi.org/10.1038/s41588-023-01561-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703697/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Main / DHL-specific mutations occur within SE-embedded promoters / PAX5/ZCCHC7 alterations affect pre-rRNA processing |
| Arriba | RNA-seq gene-fusion detection. | Methods / Additional software used |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Additional software used |
| BWA | DNA read alignment. | Methods / Preprocessing of WGS data |
| clusterProfiler | Used clusterProfiler for gene set enrichment analysis in the study’s additional-software workflow. | Methods / Additional software used |
| fastp | Read filtering and adapter trimming. | Methods / Preprocessing of WGS data |
| featureCounts | Reported FeatureCounts 2.0.0 for read quantification alongside STAR 2.7.3a in the study software methods. | Methods / Additional software used |
| Integrative Genomics Viewer (IGV) | Manually screened default-filtered Manta structural-variant calls involving BCL2 and MYC using Integrative Genomics Viewer v2.7.2. | Methods / Structural variant (SV) identification |
| Manta | Structural variant detection. | Methods / Structural variant (SV) identification |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Preprocessing of WGS data |
| ProteinPaint | Genomic result and molecular-data visualization. | Methods / Additional software used |
| SAMtools | Alignment manipulation and QC. | Methods / Preprocessing of WGS data |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Additional software used |
| WGCNA | Weighted coexpression networks and modules. | Methods / Identification of potential ER gene pairs in the B cell lymphoma genome |

XML SHA-256: `797f72ef972809efe0e2165df2e1b17c3e883e9ddbc7451c15e9573fa77fadd9`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P090: Genome-wide association analysis provides insights into the molecular etiology of dilated cardiomyopathy

Nature Genetics · 2024-11-21 · [DOI](https://doi.org/10.1038/s41588-024-01952-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631752/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Methods / Functionally informed fine-mapping of genomic loci |
| CellChat | Ligand-receptor cell communication inference. | Methods / Pathway enrichment analysis of effector genes, DEGs and intercellular communication in DCM single-nucleus transcriptomics |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization with gene expression |
| edgeR | Count-based differential expression analysis. | Methods / Tissue, cell type and cell state heritability enrichment |
| GCTA | Used for genetic association analysis; conditional or joint association analysis. | Methods / ABC model / Conditional GWAS analysis |
| g:Profiler | Functional gene-set enrichment. | Methods / Pathway enrichment analysis of effector genes, DEGs and intercellular communication in DCM single-nucleus transcriptomics |
| LDAK | SNP heritability and polygenic modelling. | Methods / SNP-based heritability estimation |
| LDSC | Used for cross-trait genetic correlation. | Methods / Discovery GWAS and multitrait analysis of GWAS |
| METAL | GWAS meta-analysis. | Methods / Discovery GWAS and multitrait analysis of GWAS |
| MTAG | Multi-trait GWAS analysis. | Methods / Discovery GWAS and multitrait analysis of GWAS |
| MultiXcan | Joint tissue predicted expression association. | Methods / Transcriptome-wide association study |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Methods / Polygenic risk score generation and testing |
| PolyFun | Functional-prior fine-mapping. | Methods / Functionally informed fine-mapping of genomic loci |
| PoPS | Effector-gene prioritization using polygenic features. | Methods / Functionally informed fine-mapping of genomic loci / Prioritization of effector genes at DCM loci |
| PRS-CS | Inferred dilated-cardiomyopathy PRS weights with PRS-CS-auto and a European 1000 Genomes phase3 LD panel after UKB-excluded GWAS/MTAG analysis; PLINK1.9 applied the resulting SNP weights. | Methods / Polygenic risk score generation and testing |
| REGENIE | Used REGENIE 3.2.4 whole-genome predictors and conditional PTV burden tests for binary cardiomyopathy/heart-failure and quantitative CMR outcomes. PTV MAF < 0.001; UKB required MAC >=20 for binary and >=3 for quantitative traits, GeL >=3; binary tests used Firth correction. | Methods / ABC model / Rare variant gene-based association testing |
| SKAT | Rare-variant sequence-kernel association. | Methods / ABC model / Rare variant gene-based association testing |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Methods / ABC model / Rare variant gene-based association testing |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Functionally informed fine-mapping of genomic loci |

XML SHA-256: `9134de1e47ed25c8ca3033729d6b60846a95603b762996d6aac9d2ef4926c3ca`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P091: Saturation mutagenesis identifies activating and resistance-inducing FGFR kinase domain mutations

Nature Genetics · 2025-12-08 · [DOI](https://doi.org/10.1038/s41588-025-02431-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807871/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Methods / NGS analysis |
| Cutadapt | Adapter trimming. | Methods / NGS analysis |
| FastQC | Sequencing read quality assessment. | Methods / NGS analysis |
| FLASH | In the Galaxy NGS workflow, applied Trimmomatic HEADCROP 10 to the forward read only, then merged that trimmed forward read and the reverse read using FLASH with minimum overlap 10, maximum overlap 200 and maximum mismatch density 0.25. Cutadapt demultiplexing, BWA-MEM mapping, Samtools formatting and custom R/Excel variant-frequency/enrichment calculations followed. | Methods / NGS analysis |
| SAMtools | Alignment manipulation and QC. | Methods / NGS analysis |
| Trimmomatic | In Galaxy, removed ten bases from the start of forward reads with HEADCROP10, then merged the trimmed forward and original reverse reads using FLASH: overlap10-200bases and maximum mismatch density0.25. | Methods / NGS analysis |

XML SHA-256: `7cf39be32673b003d9402dafbb2b43027233f9b4fb31c07518b564b0e1f682cb`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P092: Single-cell analysis of chromatin accessibility in the human intestine identifies regulatory programs and clarifies genetic associations in Crohn’s disease

Nature Genetics · 2026-09-25 · [DOI](https://doi.org/10.1038/s41588-026-02755-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649587/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ArchR | Single-cell ATAC-seq analysis. | Methods / ScATAC data preprocessing and data analysis |
| Cell Ranger | Aligned scATAC-seq reads with Cell Ranger ATAC; the paragraph points to a Supplementary Note for preprocessing details. | Methods / ScATAC data preprocessing and data analysis |
| chromVAR | Chromatin motif accessibility deviation scores. | Methods / TF motif enrichment |
| clusterProfiler | Called GSEA on topic-specific gene scores built from distance-weighted chromatin accessibility and normalized by the weights’ L2 norm, using MSigDB Hallmark, curated C2 and ontology C5 collections retrieved with msigdbr 7.5.1. | Methods / Topic modeling and pathway enrichment |
| DESeq2 | Count-based differential expression analysis. | Methods / CaQTL calling |
| Eagle | Genotype phasing. | Methods / Genotyping and quality control |
| GLIMPSE | Low-coverage sequence genotype imputation. | Methods / Genotyping and quality control |
| GSEA | Ranked gene-set enrichment. | Methods / Topic modeling and pathway enrichment |
| MACS | Called MACS2 peaks through ArchR pseudobulk/reproducible-peak functions, merged peaks iteratively within each cell type and then across cell types, and annotated categories from summit positions. | Methods / Accessible chromatin peak calling |
| mashr | Multivariate adaptive shrinkage. | Methods / MashR |
| PLINK | Used for LD-based variant selection; genetic association analysis. | Methods / Functional annotation and prioritization of fine-mapped SNPs |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Statistical fine-mapping |
| UCSC liftOver | Mapped the union of chromatin-accessibility peaks from hg38 to hg19, then extended peak regions1000bp on both sides for SNP annotations used in stratified LDSC. | Methods / Heritability enrichment |

XML SHA-256: `d0274e977260abcc52aecd4abe6f0e8b8901a5948beae068b28661a402dd5126`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P093: Consensus prediction of cell type labels in single-cell data with popV

Nature Genetics · 2024-11-20 · [DOI](https://doi.org/10.1038/s41588-024-01993-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631762/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellTypist | Reference-based cell-type classification. | Methods |
| Harmony | Batch integration of cell embeddings. | Methods |
| popV | Consensus single-cell type annotation. | Methods |
| Scanpy | Single-cell expression processing and analysis. | Methods |
| scArches | Single-cell reference transfer. | Methods / Model parameters / scVI |
| scVI | Variational single-cell integration or expression modelling. | Methods |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Model parameters / Seurat label transfer |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Model parameters |

XML SHA-256: `0a78b34e5fe4c639ecae089b6c3b4b31a82b993a03d669827ce1c1651ef6a4b9`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P094: Atlas of cell types and regulatory elements underlying human facial diversity

Nature Genetics · 2026-09-24 · [DOI](https://doi.org/10.1038/s41588-026-02748-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649600/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Used Cell Ranger ARC with reported default settings to align joint snRNA/ATAC data to human hg38 and obtain modality count matrices and fragment files. | Methods / Preprocessing and normalization of joint snRNA/ATAC–seq data |
| CellTypist | Reference-based cell-type classification. | Methods / Visium and Stereo-seq sample preparation and data analysis |
| cisTarget | Recovery-curve motif enrichment and regulatory target analysis. | Methods / SCENIC+ |
| GenomicRanges | Used GenomicRanges subsetByOverlaps() and findOverlaps() to intersect accessible or linked peaks with SNPs and other regulatory datasets after hg38 coordinate harmonization. | Methods / Downstream multi-omic analysis and visualizations |
| Harmony | Batch integration of cell embeddings. | Methods / Processing of scRNA-seq |
| IRanges | Used IRangesv2.40.0 with GenomicRanges overlap functions for regulatory-interval comparisons after lifting datasets to hg38. | Methods / Downstream multi-omic analysis and visualizations |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Processing of scRNA-seq |
| pycisTopic | Single-cell ATAC pseudobulk peaks and latent-Dirichlet-allocation topic modelling. | Methods / SCENIC+ |
| rtracklayer | Used the rtracklayer liftOver interface to harmonize heterogeneous regulatory/epigenomic data coordinates to hg38 before overlap and peak-to-gene analyses. | Methods / Downstream multi-omic analysis and visualizations |
| Scanpy | Single-cell expression processing and analysis. | Methods / Processing of scRNA-seq |
| SCENIC+ | Multiomic regulatory-network tools and motif reference resources. | Methods / SCENIC+ |
| Scrublet | Called doublets independently per sample with Scrublet score threshold 0.15 before joining 295141 cells and subsequent normalization, regression and Harmony. | Methods / Processing of scRNA-seq |
| sctransform | Applied SCTransform and PCA in the merged Seurat RNA workflow, which also describes NormalizeData/ScaleData and a literal nFeature_RNA less-than-1000 AND less-than-5000 filter. | Methods / Preprocessing and normalization of joint snRNA/ATAC–seq data |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Preprocessing and normalization of joint snRNA/ATAC–seq data |
| Signac | Single-cell chromatin accessibility analysis. | Methods / Preprocessing and normalization of joint snRNA/ATAC–seq data |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Visium and Stereo-seq sample preparation and data analysis |
| Squidpy | Spatial neighborhood and expression analysis. | Methods / Spatial codes analysis |
| Tangram | Mapping single-cell expression to spatial measurements. | Methods / Visium and Stereo-seq sample preparation and data analysis |
| tradeSeq | Trajectory-dependent differential expression. | Methods / Visium and Stereo-seq sample preparation and data analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Developmental trajectory inference from scRNA-seq data |

XML SHA-256: `ebd1e6a9559b288607109df25a9ef952fa78b93f938a81cb3f954c7678a8af35`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P095: Personal transcriptome variation is poorly explained by current genomic deep learning models

Nature Genetics · 2023-11-30 · [DOI](https://doi.org/10.1038/s41588-023-01574-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703684/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Constructing personalized input sequences |
| SAMtools | Alignment manipulation and QC. | Methods / Constructing personalized input sequences |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / QTL effect direction classification |

XML SHA-256: `bc4b0bc8bde6e9a1ab0b70d6e4befeb1e91943bfe86a03c342b69a0ced96e2f4`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P096: A temporal cortex cell atlas highlights gene expression dynamics during human brain maturation

Nature Genetics · 2024-11-20 · [DOI](https://doi.org/10.1038/s41588-024-01990-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631765/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| cell2location | Spatial cell-type deconvolution. | Methods / cell2location analysis of Visium datasets |
| Cell Ranger | Used Cell Ranger count v6.1.1 to align snRNA FASTQs to GRCh38, quantify gene expression with introns included and automatically filter very low-UMI background barcodes. | Methods / snRNA-seq read alignment and gene expression quantification |
| DESeq2 | Count-based differential expression analysis. | Methods / DESeq2 age-dependent differential gene expression analysis of snRNA-seq datasets |
| DoubletFinder | Single-cell doublet detection. | Methods / snRNA-seq quality control |
| g:Profiler | Functional gene-set enrichment. | Methods / Pathway enrichment analysis of snRNA-seq datasets |
| GSEA | Ranked gene-set enrichment. | Methods / Pathway enrichment analysis of snRNA-seq datasets |
| NS-Forest | Selected NS-Forest marker genes for anchor-based integration comparisons against random anchor choices. | Methods / NS-Forest machine learning marker analysis of snRNA-seq datasets |
| Scrublet | Combined dataset-tuned Scrublet 0.0.2, DoubletDecon 1.1.5 and DoubletFinder 3 calls: removed all DoubletFinder calls or the intersection of DoubletDecon and Scrublet calls. | Methods / snRNA-seq quality control |
| sctransform | Repeated SCTransform and integration using NS-Forest-selected marker anchors versus random anchors for comparison. | Methods / NS-Forest machine learning marker analysis of snRNA-seq datasets |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / snRNA-seq quality control |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Visium read alignment and gene expression quantification |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / snRNA-seq data normalization, integration and clustering |

XML SHA-256: `bdf8a7d606e062789c6bd24ca84dcc7c8bc02b0271255e367f45d220d57369f9`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P097: A genome-wide association study of mass spectrometry proteomics using a nanoparticle enrichment platform

Nature Genetics · 2025-11-27 · [DOI](https://doi.org/10.1038/s41588-025-02413-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695657/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Genotyping |
| coloc | Shared-signal colocalization analysis. | Methods / Replication |
| DIA-NN | Data-independent acquisition proteomic quantification. | Methods / Proteomics |
| Minimac | Genotype imputation. | Methods / Genotyping |
| PLINK | Used PLINK v.1.90b7.1 --linear to associate residualized, inverse-normal protein levels with genotypes in 1260 samples, excluding missing data points. The source separately describes lead selection, LD clumping and iterative conditional association. | Methods / Genome-wide association |
| PLINK 2 | Filtered Tarkin genotypes using PLINK2 v.2.00a5LM with --geno 0.1, --mac 10, --maf 0.05 and --hwe 1E-15. The 1260-sample proteomics subset was further filtered with --maf 0.05, --hwe 1E-6 and --geno 0.02; the first ten PCs were computed with --pca. | Methods / Genotyping |
| SHAPEIT | Genotype haplotype phasing. | Methods / Genotyping |

XML SHA-256: `c2fc5b55fb192193520c907287ad57fec48a06a6d0e5aca760cb5f25c25f7e79`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P098: Divergent medulloblastoma chromatin states disclose KDM2B as a selective dependency

Nature Genetics · 2026-09-24 · [DOI](https://doi.org/10.1038/s41588-026-02745-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649585/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Chromatin segmentation |
| Cytoscape | Network visualization and analysis. | Methods / Functional annotation and pathway enrichment |
| DAVID | Gene functional enrichment. | Methods / Functional annotation and pathway enrichment |
| deepTools | Used deepTools3.5.0 bamCoverage for browser tracks and reference-point promoter profiles overTSS±5kb with skipZeros disabled. State bedGraphs were divided by sample number to represent chromatin-state percentages. | Methods / Genomic coordinates and gene annotation |
| DESeq2 | Count-based differential expression analysis. | Methods / Gene expression analysis |
| Enrichr | Gene-set enrichment. | Methods / Functional annotation and pathway enrichment |
| GSEA | Ranked gene-set enrichment. | Methods / Identification of Group 4 EnhBiv and Group 3 ReprPC signature genes |
| gseapy | Python gene-set enrichment analysis. | Methods / Identification of Group 4 EnhBiv and Group 3 ReprPC signature genes |

XML SHA-256: `49f9874fb22a1e420c6cfbf330d7623514fe91d1d983d9dcc8940967836b7214`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P099: A global view of aging and Alzheimer’s pathogenesis-associated cell population dynamics and molecular signatures in human and mouse brains

Nature Genetics · 2023-11-30 · [DOI](https://doi.org/10.1038/s41588-023-01572-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703679/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / Data processing for EasySci-ATAC |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Differential accessible peak analysis |
| cell2location | Spatial cell-type deconvolution. | Methods / Integration analysis with external datasets and to locate the spatial distributions of main cell types and subtypes |
| Cell Ranger | Processed subsampled commercial 10x Chromium data with Cell Ranger as the comparator in an EasySci-RNA detection-depth benchmark. | Methods / Computational procedures for processing EasySci-RNA libraries |
| chromVAR | Chromatin motif accessibility deviation scores. | Methods / Transcription factor motif analysis |
| HTSeq | Read counting and sequence processing. | Methods / Data processing for EasySci-ATAC |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / LDSC analysis |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Cell subclustering analysis |
| MACS | Called per-cell-type pseudobulk ATAC peaks with MACS2 2.1.1 using no model, extension 200, shift -100 and q-value 0.05. Applied a score-per-million cutoff of 1.3, extended summits by 250 bases, merged peaks and constructed an HTSeq accessibility matrix. | Methods / Differential accessible peak analysis |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Cell clustering and annotation analysis |
| Scrublet | Used Scrublet for doublet removal after RT-primer-aware cell merging and quality filtering in a large single-cell dataset. | Methods / Cell clustering and annotation analysis |
| Seurat | Used for cell clustering; cell annotation. | Methods / Cell clustering and annotation analysis |
| Signac | Single-cell chromatin accessibility analysis. | Methods / Differential accessible peak analysis |
| SnapATAC2 | ATAC processing, modified doublet detection and embedding. | Methods / Cell filtering, clustering and annotation for EasySci-ATAC |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Computational procedures for processing EasySci-RNA libraries |
| Trim Galore | After custom EasySci barcode and adapter extraction, applied an additional default TrimGalore step described as removing poly-A sequence and low-quality bases before STAR mapping, duplicate handling and cell assignment. | Methods / Computational procedures for processing EasySci-RNA libraries |
| UCSC liftOver | Mapped human GWAS SNP coordinates to the mouse genome before annotating overlap with cell-type and microglia-subcluster differential-accessibility peaks for LDSC. | Methods / LDSC analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Cell subclustering analysis |

XML SHA-256: `eb2af1feea6cbbccd75e0e99e1a39b2be97ea787209a11a517023a41e02429d5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P100: Spatially resolved multi-omics of human metabolic dysfunction-associated steatotic liver disease

Nature Genetics · 2025-11-24 · [DOI](https://doi.org/10.1038/s41588-025-02407-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695644/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Computational analysis / Analysis of GRNs |
| cell2location | Spatial cell-type deconvolution. | Methods / Computational analysis / Spatial mapping of cell types in Visium ST using cell2location |
| CellChat | Ligand-receptor cell communication inference. | Methods / Computational analysis / Identifying differentially flowing signal variables |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Methods / Computational analysis / Cell–cell interaction analysis |
| cisTarget | Recovery-curve motif enrichment and regulatory target analysis. | Methods / Computational analysis / Analysis of GRNs |
| Harmony | Batch integration of cell embeddings. | Methods / Computational analysis / scRNA-seq data processing |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Computational analysis / scRNA-seq data processing |
| Milo | Single-cell neighborhood differential abundance. | Methods / Computational analysis / Differential abundance analysis |
| Scanpy | Single-cell expression processing and analysis. | Methods / Computational analysis / scRNA-seq data processing |
| SCENIC | Single-cell regulatory network inference. | Methods / Computational analysis / Analysis of GRNs |
| Scrublet | Used default Scrublet during Scanpy 1.10.3 preprocessing and also manually removed clusters expressing mixed-lineage markers. | Methods / Computational analysis / scRNA-seq data processing |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Computational analysis / Mapping of the gene expression libraries |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Computational analysis / scRNA-seq data processing |

XML SHA-256: `26ab3e5a8e6387851333b9f42f11e590cd4d7f0e73ba15689752650febdd414e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P101: Pangenome analysis reveals genomic variations associated with domestication traits in broomcorn millet

Nature Genetics · 2023-11-30 · [DOI](https://doi.org/10.1038/s41588-023-01571-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703678/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Phylogeny and population structure |
| ANNOVAR | Variant functional annotation. | Methods / Phylogeny and population structure |
| Beagle | Imputed missing SNP calls before the millet SNP GWAS on 43 phenotypes; SNP QC used MAF at least 0.05 and missingness at most 0.1. | Methods / SNP-based and PAV-based GWAS |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Identification of selective sweeps |
| BlastKOALA | KEGG orthology and pathway functional annotation. | Methods / RNA-seq and differential gene expression analysis |
| BWA | DNA read alignment. | Methods / Short-read sequencing, Hi-C sequencing and data processing |
| clusterProfiler | Applied hypergeometric enrichment to GO and KEGG annotations of wild-versus-domesticated millet population DEGs, with P at most 0.05. | Methods / RNA-seq and differential gene expression analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq and differential gene expression analysis |
| featureCounts | Used featureCounts 2.0.1 after Subjunc 2.0.1 mapping to the Longmi4 millet genome; the source describes read counts and FPKM expression. | Methods / RNA-seq and differential gene expression analysis |
| GATK | Variant discovery and recalibration. | Methods / Short-read sequencing, Hi-C sequencing and data processing |
| hifiasm | Haplotype-resolved long-read assembly. | Methods / Long-read sequencing, assembly and quality assessment |
| HISAT2 | Mapped plant RNA-seq evidence to millet genomes with HISAT2 2.1.0 using --min-intronlen 20 --max-intronlen 15000, followed by StringTie2 2.1.7 transcript assembly. | Methods / Gene and transposable element annotation |
| Integrative Genomics Viewer (IGV) | Manually inspected Minimap2-aligned HiFi reads at structural-variant breakpoints with IGV v2.9.2 while evaluating32 accession assemblies at304 loci. | Methods / Long-read sequencing, assembly and quality assessment |
| InterProScan | Protein domain and functional annotation. | Methods / Gene and transposable element annotation |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Phylogeny and population structure |
| Juicer | Hi-C read processing and contact maps. | Methods / Short-read sequencing, Hi-C sequencing and data processing |
| KofamKOALA | Profile-HMM KEGG orthology annotation. | Methods / RNA-seq and differential gene expression analysis |
| MAFFT | Multiple sequence alignment. | Methods / Gene-based pangenome analyses |
| Merqury | Assembly k-mer completeness assessment. | Methods / Long-read sequencing, assembly and quality assessment |
| minimap2 | Used minimap2 2.24-r1122 HiFi alignments to inspect millet structural-variant breakpoints, including manual IGV assessment of 304 loci spanning 3-17 kb. | Methods / Long-read sequencing, assembly and quality assessment |
| PLINK | Used for genetic principal components. | Methods / Phylogeny and population structure |
| RepeatMasker | Repetitive sequence annotation. | Methods / Gene and transposable element annotation |
| StringTie | Transcript assembly and quantification. | Methods / Gene and transposable element annotation |
| Subjunc | Mapped millet RNA-seq reads to Longmi4 with Subjunc 2.0.1 before featureCounts 2.0.1 quantification. | Methods / RNA-seq and differential gene expression analysis |
| Trimmomatic | Used Trimmomatic0.39 to remove adapters and low-quality sequence from150-base paired-end WGS of516millet accessions before BWA-MEM alignment in SpeedSeq0.0.2. | Methods / Short-read sequencing, Hi-C sequencing and data processing |

XML SHA-256: `ce1d3127ae8ceee02365eba3cae0c946505b733d66ae78d41eaa565177a6a98e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P102: Structural variation reshapes population gene expression and trait variation in 2,105 Brassica napus accessions

Nature Genetics · 2024-11-05 · [DOI](https://doi.org/10.1038/s41588-024-01957-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549052/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Beagle | Imputed and phased variants selected at MAF above 0.05 within each of two Brassica napus subpopulations before GEMMA GWAS. | Methods / GWASs |
| BEDTools | Genomic interval manipulation and overlap. | Methods / SV annotation (regulatory effect annotation of eSVs) |
| BWA | DNA read alignment. | Methods / Genome assembling |
| clusterProfiler | Tested GO enrichment of Brassica napus genes regulated by 495 trans-eQTL hotspots using Arabidopsis ortholog-derived GO annotations and FDR 0.01. | Methods / Gene set enrichment analysis on trans-eQTL hotspots |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization analysis of GWASs and eQTLs |
| GATK | Variant discovery and recalibration. | Methods / SNP/indel calling for B. napus accessions |
| GEMMA | Mixed-model association or variance components. | Methods / GWASs |
| HISAT2 | Mapped cleaned RNA-seq reads to ZS11 with reported HISAT2 2.1.2 defaults before SAMtools filtering of MAPQ<10, nonunique and unmapped reads. | Methods / RNA-seq data analysis |
| Juicer | Hi-C read processing and contact maps. | Methods / Anchoring and validation of B. napus cv. ZY821 assembly |
| MACS | Called Brassica ATAC and histone ChIP peaks with MACS2 2.1.3.3 against accession-specific genomes at effective size 9.6e8. Reported ATAC shift -100, extension 200, literal --board and bedGraph output; ChIP used bedGraph output and the same declared effective size. | Methods / Analyses of ChIP–seq and ATAC–seq |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Analyses of ChIP–seq and ATAC–seq |
| SAMtools | Alignment manipulation and QC. | Methods / SNP/indel calling for B. napus accessions |
| Sniffles | Long-read structural variant discovery. | Methods / SV identification and pan-SV library construction |
| StringTie | Transcript assembly and quantification. | Methods / RNA-seq data analysis |
| Trimmomatic | Used Trimmomatic0.36 for adapter/low-quality RNA-seq filtering before HISAT2 alignment to ZS11; SAMtools mapping filters preceded StringTie TPM calculation. | Methods / RNA-seq data analysis |

XML SHA-256: `425ec8beaa23c8950a7cdbc6ecc8f51be44208b47b20acfb8e7affecc0bd0a4e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P103: Proteome-wide model for human disease genetics

Nature Genetics · 2025-11-24 · [DOI](https://doi.org/10.1038/s41588-025-02400-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695638/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaMissense | Missense variant pathogenicity prediction. | Results / Method development / popEVE shows limited to no population bias |
| DAVID | Gene functional enrichment. | Methods / Structural and functional analysis of deleterious mutations / Functional similarity of novel and known DD genes |
| popEVE | Proteome-scale variant effect modelling. | Methods / Structural and functional analysis of deleterious mutations / Functional network |
| PyMOL | Calculated listed distances with the PyMOL distance function during manual inspection of resolved experimental structures for highly deleterious popEVE mutations. | Methods / Structural and functional analysis of deleterious mutations / Manual structure analysis |
| REVEL | Missense variant effect prediction. | Results / Method development / popEVE shows limited to no population bias |
| SPARK | Spatially variable gene testing. | Methods / Statistics and reproducibility |
| Ensembl VEP | Variant consequence annotation. | Methods / Human variation data |

XML SHA-256: `5995c0b15ea643adfbc7123beb9a9017aca518e4052c92088a5b5ae26e7781be`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P104: Single-nucleus atlas of cell-type specific genetic regulation in the human brain

Nature Genetics · 2026-09-23 · [DOI](https://doi.org/10.1038/s41588-026-02733-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649572/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CAVIAR | Fine-mapping credible variants. | Methods / Sample selection and preprocessing / Fine-mapping of cis-eQTLs |
| CellBender | Ambient count background removal. | Methods / Sample selection and preprocessing |
| coloc | Shared-signal colocalization analysis. | Methods / Sample selection and preprocessing / Colocalization of genetic signals from regulatory and disease risk variants |
| edgeR | Count-based differential expression analysis. | Methods / Sample selection and preprocessing / Analysis of genetic regulatory variants at the pseudobulk level |
| Harmony | Batch integration of cell embeddings. | Methods / Sample selection and preprocessing / Cell annotation |
| KING | Genetic kinship and relatedness estimation. | Methods / Sample selection and preprocessing |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Methods / Sample selection and preprocessing / Partitioning heritability based on statistical fine-mapping |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Sample selection and preprocessing / Cell annotation |
| mashr | Multivariate adaptive shrinkage. | Methods / Sample selection and preprocessing / Identifying shared and cell type-specific genetic regulatory effects |
| PEER | Latent confounder estimation. | Methods / Sample selection and preprocessing / Normalization of gene expression |
| QTLtools | Molecular QTL mapping and QC. | Methods / Sample selection and preprocessing / Enrichment of OCRs around detected regulatory variants |
| Scanpy | Single-cell expression processing and analysis. | Methods / Sample selection and preprocessing |
| SoupX | Ambient RNA contamination correction. | Methods / Sample selection and preprocessing |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Sample selection and preprocessing |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Sample selection and preprocessing / Cell annotation |
| Vireo | Genotype-based cell demultiplexing. | Methods / Sample selection and preprocessing |

XML SHA-256: `d92b674fe5e2053354f2693a2409628e5e729bd59cd613a8f76558e48860c248`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P105: East Asian-specific and cross-ancestry genome-wide meta-analyses provide mechanistic insights into peptic ulcer disease

Nature Genetics · 2023-11-30 · [DOI](https://doi.org/10.1038/s41588-023-01569-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703676/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Methods / Fine-mapping and variant annotation |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / Conditional analysis by COJO |
| coloc | Shared-signal colocalization analysis. | Methods / HP-stratified analysis |
| Eagle | Genotype phasing. | Methods / Genotyping and imputation |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Used MAGMA1.08 through FUMA1.3.8 with a1000Genomes EastAsian LD panel,19033 protein-coding genes and15485 MSigDB7 gene sets; compared analyses across annotation-window choices. | Methods / Gene-based analysis and pathway analyses |
| GCTA | Used for conditional or joint association analysis. | Methods / Conditional analysis by COJO |
| GWAMA | GWAS meta-analysis. | Methods / Replication of significant associations and EAS-specific meta-analysis |
| KING | Genetic kinship and relatedness estimation. | Methods / Blood group and secretor status interaction analysis |
| LDSC | Used for annotation-stratified genetic enrichment; association inflation assessment. | Methods / LDSC |
| LDstore | Linkage disequilibrium matrix calculation. | Methods / Fine-mapping and variant annotation |
| MAGMA | Used MAGMA1.08 through FUMA1.3.8 with a1000Genomes EastAsian LD panel,19033 protein-coding genes and15485 MSigDB7 gene sets; compared analyses across annotation-window choices. | Methods / Gene-based analysis and pathway analyses |
| METAL | GWAS meta-analysis. | Methods / Genome-wide association analysis |
| Minimac | Genotype imputation. | Methods / Genotyping and imputation |
| MR-MEGA | Trans-ancestry meta-regression. | Methods / Cross-ancestry meta-analysis |
| MR-PRESSO | MR pleiotropy/outlier sensitivity. | Methods / Two-sample MR analysis |
| PLINK | Used for genotype quality control; LD-based variant selection. | Methods / Genome-wide association analysis |
| Popcorn | Cross-population genetic correlation. | Methods / Genetic correlation estimation |
| PRS-CS | Inferred SNP weights with PRS-CS-auto and an East Asian 1000 Genomes HapMap3 reference,using TMM-50K GWAS for scores applied to BioBank Japan. | Methods / PRS construction and evaluation |
| SAIGE | Ran SAIGE0.44 single-variant and sex-stratified association tests,adjusting for age,sex and ten PCs;fit LOCO null models from LD-pruned array variants with MAF>1% and excluded test variants with MAC<20. | Methods / Genome-wide association analysis |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Fine-mapping and variant annotation |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Two-sample MR analysis |
| UCSC liftOver | Converted GRCh38 GWAS summary-statistic coordinates to GRCh37 with the UCSC tool before cross-ancestry meta-analysis. | Methods / Cross-ancestry meta-analysis |

XML SHA-256: `e62de8f4b3c797ec66020a59b641c28d173b3129e3bc6c26f9d731f7daff017f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P106: Scalable and accurate rare variant meta-analysis with Meta-SAIGE

Nature Genetics · 2025-11-20 · [DOI](https://doi.org/10.1038/s41588-025-02403-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695641/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| LOFTEE | Loss-of-function variant annotation. | Methods / UKB and All of Us WES data meta-analysis |
| Meta-SAIGE | Combined cohort summaries with Meta-SAIGE,compared results against individual-level SAIGE-GENE+,and analyzed ancestry-specific UKB/All of Us rare-variant data. | Methods / Comparison of Meta-SAIGE and SAIGE-GENE+ |
| MetaSTAAR | Summary-statistic gene-based rare-variant meta-analysis. | Methods / Computational cost evaluation |
| SAIGE | Generated per-cohort SAIGE variant score statistics,variances and P values,plus phenotype-independent sparse genotype cross-product LD matrices,for Meta-SAIGE analyses of binary and continuous traits. | Methods / Summary statistics from each cohort |
| SAIGE-GENE | Used SAIGE-GENE+ on all individual-level UKB exome data as the comparator to Meta-SAIGE across three binary and three continuous traits,with age,sex,batch,ten PCs and a sparse GRM cutoff0.05. | Methods / Comparison of Meta-SAIGE and SAIGE-GENE+ |
| SKAT | Rare-variant sequence-kernel association. | Methods / Gene-based test with the superset |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Methods / Gene-based test with the superset |

XML SHA-256: `65de4199ba9bdee9391fb80d66296f02e599761874ef0d71d3d4c7ebac6c4077`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P107: APOE-stratified genome-wide association analyses provide insights into the genetic etiology of Alzheimers’s disease

Nature Genetics · 2026-09-14 · [DOI](https://doi.org/10.1038/s41588-026-02713-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649558/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Used MAGMA1.08 through FUMA1.6.1 separately per meta-analysis stratum with0kb and35kb-upstream/10kb-downstream windows, after VEP rsID assignment and Picard build37 liftover. | Methods / Pathway analysis |
| HEIDI | Heterogeneity test distinguishing pleiotropy from linkage. | Methods / SMR |
| MAGMA | Used MAGMA1.08 through FUMA1.6.1 separately per meta-analysis stratum with0kb and35kb-upstream/10kb-downstream windows, after VEP rsID assignment and Picard build37 liftover. | Methods / Pathway analysis |
| METAL | GWAS meta-analysis. | Methods / GWAS analysis |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Pathway analysis |
| PLINK 2 | Used for genetic principal components. | Methods / GWAS analysis |
| REGENIE | Used REGENIE with genotype dosages and Firth regression for cohort-specific additive Alzheimer case-control GWAS, alongside other cohorts using SNPTEST or PLINK2. Applied effect/SE/P, absolute-effect, INFO and imbalance-aware MAC-info QC before METAL meta-analysis. | Methods / GWAS analysis |
| SMR | Summary-data molecular association or Mendelian randomization. | Methods / SMR |
| SNPTEST | Genotype association testing. | Methods / GWAS analysis |
| Ensembl VEP | Variant consequence annotation. | Methods / Pathway analysis |

XML SHA-256: `9d4761baeb66f428c6b6b9a4f017a8e431a327dbc7b4dc1178a9d84b88baeb7e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P108: Isoform-level transcriptome-wide association uncovers genetic risk mechanisms for neuropsychiatric disorders in the human brain

Nature Genetics · 2023-11-30 · [DOI](https://doi.org/10.1038/s41588-023-01560-2) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703692/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Methods / Trait association and stepwise hypothesis testing |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Adult brain reference panel processing and model training |
| ComBat | Batch-effect adjustment. | Methods / Adult brain reference panel processing and model training |
| CrossMap | Genome-assembly coordinate conversion with format-specific interval, alignment and variant handling. | Methods / Adult brain reference panel processing and model training |
| DESeq2 | Count-based differential expression analysis. | Methods / GTEx processing and model training |
| Eagle | Genotype phasing. | Methods / Adult brain reference panel processing and model training |
| edgeR | Count-based differential expression analysis. | Methods / GTEx processing and model training |
| isotwas | Isoform-level transcriptome-wide association. | Methods / GTEx processing and model training |
| LeafCutter | Calculated developmental-brain intron usage using LeafCutter for an executed splicing-event comparison, transformed usage percentages to M-values and supplied those events to the isoTWAS multivariate trait-mapping framework. | Results / Improved trait mapping across 15 neuropsychiatric GWAS |
| Minimac | Genotype imputation. | Methods / Developmental brain reference panel processing and model training |
| PEER | Latent confounder estimation. | Methods / GTEx processing and model training |
| PLINK | Used for genotype quality control. | Methods / Adult brain reference panel processing and model training |
| QTLtools | Molecular QTL mapping and QC. | Methods / Adult brain reference panel processing and model training |
| Salmon | Transcript abundance quantification. | Methods / GTEx processing and model training |
| SAMtools | Alignment manipulation and QC. | Methods / Adult brain reference panel processing and model training |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Training predictive models of isoform expression / Estimating SNP effects on isoform expression |
| sva | Latent confounder adjustment. | Methods / Adult brain reference panel processing and model training |
| WGCNA | Weighted coexpression networks and modules. | Methods / Adult brain reference panel processing and model training |

XML SHA-256: `2b6379547edec29576319cbf8354701af40c7480514415c36d8657075449c182`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P109: Grapevine pangenome facilitates trait genetics and genomic breeding

Nature Genetics · 2024-11-04 · [DOI](https://doi.org/10.1038/s41588-024-01967-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631756/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Population genetic analyses |
| clusterProfiler | Performed GO and GSEA enrichment at FDR below 0.05 to assess GWAS/divergent-selection candidates using fruit transcriptomes, with whole-genome proteins supplying the GO background. | Methods / Transcriptome and GO enrichment analyses |
| featureCounts | Reported featureCounts 2.0.2 expression quantification as transcripts per kilobase of exon model per million mapped reads after STAR 2.4.2 mapping to PNT2T grape assemblies. | Methods / Transcriptome and GO enrichment analyses |
| GSEA | Ranked gene-set enrichment. | Methods / Transcriptome and GO enrichment analyses |
| hifiasm | Haplotype-resolved long-read assembly. | Methods / De novo genome assembly |
| HISAT2 | Mapped RNA-seq evidence to repeat-masked grape assemblies with the reported Hisat2 2.10.2 before StringTie and combined BRAKER/PASA/MAKER gene annotation. | Methods / The annotation of genome assemblies |
| Integrative Genomics Viewer (IGV) | Used Integrative Genomics Viewer visualization with Minimap2-aligned ONT reads to identify split reads and estimate assembly gaps before gap closure. | Methods / De novo genome assembly |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Population genetic analyses |
| Juicer | Hi-C read processing and contact maps. | Methods / De novo genome assembly |
| LDAK | SNP heritability and polygenic modelling. | Methods / Heritability estimation |
| LDpred | LD-aware Bayesian polygenic score construction. | Methods / Polygenic scores |
| minimap2 | Aligned ONT reads to grape assemblies with minimap2 2.26 to assess split-read gaps and assembly continuity in IGV. | Methods / De novo genome assembly |
| muscat | Sample-aware single-cell differential state. | Methods / Samples and genome sequencing |
| PLINK | Used for LD-based variant selection. | Methods / Pangenome variation maps |
| plotsr | Synteny and structural-rearrangement visualization. | Methods / Comparative genomics |
| RepeatMasker | Repetitive sequence annotation. | Methods / The annotation of genome assemblies |
| selscan | Haplotype-based selection scans. | Methods / Population genetic analyses |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Transcriptome and GO enrichment analyses |
| StringTie | Transcript assembly and quantification. | Methods / The annotation of genome assemblies |
| SyRI | Assembly-to-assembly structural rearrangement analysis. | Methods / Comparative genomics |
| UMAP | Used a two-component UMAP representation to explore 29 grapevine agronomic traits and group differences, then mapped normalized trait values onto that representation. | Results / GWAS of complex agronomic traits and the importance of SVs |

XML SHA-256: `7aaea692a224ee167d84331b0b2ed24c870a03734bd568252deba2a156e78a4e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P110: Adenine DNA methylation associated with transcriptionally permissive chromatin is widespread across eukaryotes

Nature Genetics · 2025-11-18 · [DOI](https://doi.org/10.1038/s41588-025-02409-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695648/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Nanopore sequencing, base calling and methylation analysis |
| Bowtie 2 | Short-read alignment. | Methods / ChIP–seq library preparation and analysis |
| deepTools | Used DeepTools2 version3.5.0 to visualize average methylation around genes,TSS and transposable elements from bigWig tracks stratified by dinucleotide context, after Guppy/Rerio modified-base calling and modbam2bed. | Methods / Nanopore sequencing, base calling and methylation analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq preparation and analysis |
| fastp | Read filtering and adapter trimming. | Methods / ChIP–seq library preparation and analysis |
| Guppy | Nanopore basecalling; distinguish the fish name. | Methods / Nanopore sequencing, base calling and methylation analysis |
| HISAT2 | Mapped publicly sourced multispecies RNA-seq with HISAT2 2.2.1, restricting intron length to 40,000 bp. | Methods / RNA-seq preparation and analysis |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Sequence search and phylogenetic analysis |
| kallisto | Used kallisto0.46 transcriptome-based estimated counts (est_counts) for cross-species differential-expression analysis with DESeq2. | Methods / RNA-seq preparation and analysis |
| MACS | Called ChIP peaks with MACS3 3.0.0a7 at q-value 0.01 with species-adjusted effective genome sizes, including 4e7, and used broad mode; classified broad regions further using a width threshold above 1200 bases. | Methods / ChIP–seq library preparation and analysis |
| MAFFT | Multiple sequence alignment. | Methods / Sequence search and phylogenetic analysis |
| RepeatMasker | Repetitive sequence annotation. | Methods / Genome assembly and re-annotation |
| Sambamba | Removed duplicate ChIP-seq alignments with Sambamba 0.6.6 before MACS3 broad/narrow peak analyses. | Methods / ChIP–seq library preparation and analysis |
| SAMtools | Alignment manipulation and QC. | Methods / Nanopore sequencing, base calling and methylation analysis |
| StringTie | Transcript assembly and quantification. | Methods / RNA-seq preparation and analysis |
| UCSC bedGraphToBigWig | Conversion of genomic signal tracks to indexed bigWig. | Methods / Nanopore sequencing, base calling and methylation analysis |

XML SHA-256: `60ec7e72b2a2ec2bef4fe2a9695ce9668fb05548951d3f624c9865080509232b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P111: Cancer treatment alters mutant selection in normal esophagus

Nature Genetics · 2026-09-11 · [DOI](https://doi.org/10.1038/s41588-026-02738-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649571/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ASCAT | Allele-specific tumor copy-number analysis. | Methods / DNA sequencing / Copy number calling |
| BEDTools | Genomic interval manipulation and overlap. | Methods / DNA sequencing / Targeted sequencing |
| dndscv | Somatic mutation selection analysis. | Methods / DNA sequencing / Calculations of percentage mutant epithelia |
| NanoSeq | Low-error somatic mutation sequencing analysis. | Methods / DNA sequencing / NanoSeq |
| SAMtools | Alignment manipulation and QC. | Methods / DNA sequencing / Targeted sequencing |

XML SHA-256: `6f15acd2c2b4e39ed605d4f8a146f80c34601ad1ceeadd3931babb55174e310e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P112: Multi-ancestry genome-wide association study of cannabis use disorder yields insight into disease biology and public health implications

Nature Genetics · 2023-11-20 · [DOI](https://doi.org/10.1038/s41588-023-01563-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703690/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| FUSION | Transcriptome-wide association and conditional follow-up. | Methods / Transcriptome-wide association study |
| LDSC | Used for SNP heritability estimation. | Methods / LDSC and SNP-based heritability |
| METAL | GWAS meta-analysis. | Methods / MVP genotyping, imputation, quality control, and GWAS and meta-analysis |
| Minimac | Genotype imputation. | Methods / MVP genotyping, imputation, quality control, and GWAS and meta-analysis |
| MTAG | Multi-trait GWAS analysis. | Methods / Multi-trait analysis of GWAS |
| PLINK | Used PLINK 1.9 to analyze a new Yale-Penn case-control tranche of unrelated individuals not included in earlier GWAS or meta-analyses. | Methods / MVP genotyping, imputation, quality control, and GWAS and meta-analysis |
| PLINK 2 | Used PLINK 2.0 logistic regression for MVP cannabis-use-disorder GWAS, with sex, age and ten covariates called positive controls in the source. Applied ancestry-specific allele-frequency and best-guess genotype missingness rules. | Methods / MVP genotyping, imputation, quality control, and GWAS and meta-analysis |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Mendelian randomization |

XML SHA-256: `2ce4288e884bff3b58799766776b155aa6864290922f0419feec437793ddab61`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P113: Disentangling the architectural and non-architectural functions of CTCF and cohesin in gene regulation

Nature Genetics · 2025-11-18 · [DOI](https://doi.org/10.1038/s41588-025-02404-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695661/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Processing of RNA-seq data |
| BWA | DNA read alignment. | Methods / Processing of EU-seq data |
| Cutadapt | Adapter trimming. | Methods / Processing of RNA-seq data |
| deepTools | Used deepTools with UCSC bigWigMerge to generate gene-track bigWigs using scaling factors obtained from sense-transcript DESeq2 analysis. | Methods / Processing of EU-seq data |
| DESeq2 | Count-based differential expression analysis. | Methods / Processing of EU-seq data |
| Harmony | Batch integration of cell embeddings. | Methods / Microscopy and image-based cytometry |
| HTSeq | Read counting and sequence processing. | Methods / Processing of RNA-seq data |
| SAMtools | Alignment manipulation and QC. | Methods / Processing of EU-seq data |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Processing of RNA-seq data |
| UCSC bigWigMerge | Used UCSC bigWigMerge together with deepTools for gene-track bigWigs using scaling factors derived from sense-transcript DESeq2. | Methods / Processing of EU-seq data |

XML SHA-256: `2d4b5622d8bbbac3a8f7eab9653c9f66a3c6e393b0d7b912870ad4650873a4e0`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P114: Contribution of copy number variants to schizophrenia in East Asian populations

Nature Genetics · 2026-09-11 · [DOI](https://doi.org/10.1038/s41588-026-02732-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649597/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Validation against PGC study pipeline |
| GATK | Variant discovery and recalibration. | Methods / CNV calling using WES data |
| METAL | GWAS meta-analysis. | Methods / Meta-analysis of gene-level associations |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Methods / Rare CNV calling and QC in EAS samples |
| PLINK 2 | Used for polygenic score calculation. | Methods / PRS calculation |
| PRS-CS | Inferred schizophrenia PRS weights from East Asian GWAS with an East Asian 1000 Genomes phase3 LD panel and phi=0.01 after excluding MHC and strand-ambiguous variants; PLINK2 computed chromosome scores. | Methods / PRS calculation |
| REGENIE | Tested gene-level rare-CNV carrier indicators with REGENIE: step 1 used LD-pruned common variants, step 2 Firth logistic association for schizophrenia with a polygenic offset. Gene indicators were 0/1 by coding-region overlap; genes needed at least eight affected participants. | Methods / Gene-level rare CNV association analysis |
| UCSC liftOver | Harmonized previously reported schizophrenia CNV-locus coordinates to hg19 for comparing known loci. | Methods / Previously reported schizophrenia-associated loci |

XML SHA-256: `6704363b099275be7b6f109d24813c766ff0dde981047406aef138f7a1f4fc2a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P115: Phenotype integration improves power and preserves specificity in biobank-based genetic studies of major depressive disorder

Nature Genetics · 2023-11-20 · [DOI](https://doi.org/10.1038/s41588-023-01559-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703686/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AutoComplete | Deep learning-based phenotype imputation. | Methods / GWASs on observed or imputed phenotypes |
| FlashPCA | Genetic principal components. | Methods / GWASs on observed or imputed phenotypes |
| LDSC | Used for SNP heritability estimation; cross-trait genetic correlation. | Methods / SNP-based heritability and genetic correlation |
| METASOFT | Meta-analysis and heterogeneity assessment. | Methods / GWASs on observed or imputed phenotypes |
| MTAG | Multi-trait GWAS analysis. | Methods / SNP-based heritability and genetic correlation |
| PLINK 2 | Used for genetic association analysis. | Methods / GWASs on observed or imputed phenotypes |
| PRSice | Clumping and thresholding polygenic score construction. | Methods / In-sample PRS prediction of phenotypes in UK Biobank with ten-fold cross-validation |

XML SHA-256: `c6056f0d3f5891d77895c2aa10063553b1735f4ddc7c8d1103eb0c4e81744fe8`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P116: African-ancestry-specific variant IKKβ p.Glu502Lys confers high lupus risk

Nature Genetics · 2025-11-17 · [DOI](https://doi.org/10.1038/s41588-025-02398-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695626/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Cohort description |
| BOLT-LMM | Linear mixed-model GWAS. | Methods / Association analysis |
| Graphtyper | Graph-based genotyping. | Methods / Whole-genome sequencing / Sample preparation |
| KING | Genetic kinship and relatedness estimation. | Methods / Association analysis |
| PLINK | Used for genotype quality control; LD-based variant selection. | Methods / Association analysis |
| Ensembl VEP | Variant consequence annotation. | Methods / Whole-genome sequencing / Sample preparation |

XML SHA-256: `e8c125ce9ba6f45079f1a57aeb5b245d9ad390b7ca78116c800a7f28cc286b76`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P117: Human and mouse adrenal glands are characterized by species-specific steroidogenic states and tissue turnover

Nature Genetics · 2026-09-08 · [DOI](https://doi.org/10.1038/s41588-026-02737-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553300/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Cycling cell identification in high-resolution spatial transcriptomics data |
| biomaRt | Used biomaRt when excluding mitochondrial,ribosomal and X/Y chromosome genes from the sexual-dimorphism pathway analysis. | Methods / Sexual dimorphism analysis |
| clusterProfiler | Extended sexual-dimorphism GSEA to KEGG and WikiPathways with clusterProfiler 4.6.2 and DOSE 3.24.2, excluding mitochondrial, ribosomal and X/Y chromosome genes. | Methods / Sexual dimorphism analysis |
| DOSE | Used DOSE together with clusterProfiler to extend sexual-dimorphism GSEA to KEGG and WikiPathways after excluding mitochondrial, ribosomal and X/Y chromosome genes. | Methods / Sexual dimorphism analysis |
| GSEA | Ranked gene-set enrichment. | Methods / Sexual dimorphism analysis |
| GSVA | Per-sample gene-set activity scoring. | Methods / Cholesterol balance analysis in bulk scRNA-seq and Visium data |
| Harmony | Batch integration of cell embeddings. | Methods / Tissue-pattern identification in high-resolution spatial transcriptomics data |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Transition and velocity analysis |
| Numbat | Single-cell copy-number inference. | Methods / Somatic CNV analysis |
| RCTD | Spatial cell-type deconvolution. | Methods / Analysis of low-resolution Visium spatial transcriptomics data |
| sctransform | Applied SCTransform to RCTD-derived cell-type expression estimates, including summed adrenocortical/mesenchymal estimates and a spot-total filter of at least 1500. | Methods / Analysis of low-resolution Visium spatial transcriptomics data |
| scVelo | RNA velocity analysis. | Methods / Transition and velocity analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / High-resolution spatial transcriptomics (Stereo-seq) of human adrenal glands |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Low-resolution spatial transcriptomics (10x Visium) of human adrenal glands |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Cholesterol balance analysis in bulk scRNA-seq and Visium data |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Somatic CNV analysis |
| velocyto | Spliced/unspliced counting and RNA velocity. | Methods / Transition and velocity analysis |

XML SHA-256: `e4853ce47676f59ccb1964304954bffadbc36e267dfb8fcb54030f8de728b91a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P118: Deep learning-based phenotype imputation on population-scale biobank data increases genetic discoveries

Nature Genetics · 2023-11-20 · [DOI](https://doi.org/10.1038/s41588-023-01558-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703681/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AutoComplete | Deep learning-based phenotype imputation. | Results / Methods overview |
| FlashPCA | Genetic principal components. | Methods / Details of GWAS analysis |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / Additional analysis of imputed phenotypes |
| PLINK 2 | Used PLINK v.2 for GWAS of observed UKBB phenotypes with 5776313 imputed SNPs after MAF>5% and INFO>0.9 selection. Chose logistic or linear regression according to the observed phenotype type; GWAS of imputed phenotypes used linear regression. | Methods / GWAS on AutoComplete-imputed phenotypes |

XML SHA-256: `4ee3ecc695ec0c2f48cfc01eb20fbd2840f16a40ef8edaf51e4c0f30b6112b66`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P119: Base editing screens define the genetic landscape of cancer drug resistance mechanisms

Nature Genetics · 2024-10-18 · [DOI](https://doi.org/10.1038/s41588-024-01948-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549056/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Amplicon sequencing |
| PyMOL | Visualized crystal structures using PyMOL v2.4.1 in the PARP-trapping study. | Methods / PARP trapping assays |

XML SHA-256: `42eeeb05178996bac2de1340c1e8d100b224532c45bd62dd96daaa2fc852dca6`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P120: Genome-wide association study and polygenic risk prediction of hypothyroidism

Nature Genetics · 2025-11-14 · [DOI](https://doi.org/10.1038/s41588-025-02410-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695664/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| clusterProfiler | Tested Gene Ontology enrichment among genes supported by at least two lines of gene-mapping evidence. | Methods / Gene mapping |
| coloc | Shared-signal colocalization analysis. | Methods / Gene mapping |
| FUSION | Transcriptome-wide association and conditional follow-up. | Methods / Gene mapping |
| LDAK | SNP heritability and polygenic modelling. | Methods / Heritability |
| LDSC | Used for association inflation assessment. | Methods / Cohorts, association testing and meta-analysis |
| MAGMA | Used MAGMA with1000Genomes European LD to generate gene-level associations/correlations and gene-feature enrichments as inputs to PoPS joint prioritization. | Methods / Gene mapping |
| MendelVar | Used MendelVar on lead-variant regions plus/minus1Mb with European1000Genomes,matching thyroid/immune keywords in OMIM,HPO and Disease Ontology. | Methods / Gene mapping |
| METAL | GWAS meta-analysis. | Methods / Cohorts, association testing and meta-analysis |
| MungeSumstats | Used the R package MungeSumstats for between-build harmonization of cohort GWAS summary statistics in thyroid-trait meta-analyses. | Methods / Cohorts, association testing and meta-analysis |
| PLINK | Used for LD-based variant selection. | Methods / Risk locus definition |
| PoPS | Effector-gene prioritization using polygenic features. | Methods / Gene mapping |
| PRS-CS | Inferred hypothyroidism PRS weights from the GWAS meta-analysis using PRS-CS with a UK Biobank LD reference panel before UKB/GESUS phenotype evaluation. | Methods / PRS derivation and validation |
| Ensembl VEP | Variant consequence annotation. | Methods / Gene mapping |

XML SHA-256: `d81649b4a54ce2a23ed5d31137ef22c872736b5e5001859e62f81446cfe68afe`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P121: Primate-specific ZNF808 is essential for pancreatic development in humans

Nature Genetics · 2023-11-16 · [DOI](https://doi.org/10.1038/s41588-023-01565-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703691/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Chromatin immunoprecipitation / ChIP–seq analysis |
| Bowtie 2 | Short-read alignment. | Methods / Chromatin immunoprecipitation / ChIP–seq analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / Gene expression analysis by RNA-seq |
| Enrichr | Gene-set enrichment. | Methods / Gene expression analysis by RNA-seq |
| GATK | Variant discovery and recalibration. | Methods / Genetic analysis |
| HTSeq | Read counting and sequence processing. | Methods / Chromatin immunoprecipitation / ChIP–seq analysis |
| MACS | Called MACS2 2.2.7.1 peaks with matched input datasets as background for MER11 chromatin-state comparisons, before peak MAPQ selection, read counting, overlap-based WT/KO selection and signal clustering. | Methods / Chromatin immunoprecipitation / ChIP–seq analysis |
| RSEM | Transcript abundance estimation. | Methods / Gene expression analysis by RNA-seq |
| SAMtools | Alignment manipulation and QC. | Methods / Chromatin immunoprecipitation / ChIP–seq analysis |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Gene expression analysis by RNA-seq |

XML SHA-256: `2b5c9891a379642aacfce6ee39013edc58e16aaa0a788151acd7864c742fd680`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P122: Uniform dynamics of cohesin-mediated loop extrusion in living human cells

Nature Genetics · 2025-11-14 · [DOI](https://doi.org/10.1038/s41588-025-02406-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695666/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / Experimental methods / Capture Micro-C |
| Cutadapt | The Capture Micro-C methods describe trimming raw reads to 50 bases with the wording "TrimGalore (Cutadapt, v0.6.10)" before HiC-Pro mapping. | Methods / Experimental methods / Capture Micro-C |
| FastQC | Sequencing read quality assessment. | Methods / Experimental methods / Capture Micro-C |
| HiC-Pro | Hi-C read processing. | Methods / Experimental methods / Capture Micro-C |
| Trim Galore | Before Capture Micro-C mapping with HiC-Pro, reports trimming all raw reads to 50 bases with the wording "TrimGalore (Cutadapt, v0.6.10)". | Methods / Experimental methods / Capture Micro-C |

XML SHA-256: `ab323676dd7fcfcf2f0357f2459890db40da8c00f21de3975ba4f47307e84b29`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P123: Multiancestry genome-wide association and multiomics analyses elucidate spatiocellular features of multiple sclerosis genetics

Nature Genetics · 2026-09-07 · [DOI](https://doi.org/10.1038/s41588-026-02741-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553293/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Azimuth | Reference mapping and cell annotation. | Methods / Manual annotation of PBMC scRNA-seq data |
| CellBender | Ambient count background removal. | Methods / Alignment, quantification and QC of PBMC scRNA-seq data |
| Cell Ranger | Processed droplet scRNA-seq libraries with Cell Ranger v7, aligned to GRCh38 using its STAR alignment and created raw count matrices. | Methods / Alignment, quantification and QC of PBMC scRNA-seq data |
| FUMA | Functional mapping, annotation and gene prioritization. | Methods / GWAS meta-analysis |
| gsMap | Spatial mapping of genetic trait associations. | Methods / Spatially resolved mapping of regions associated with MS |
| gwas-sumstats-harmoniser | Followed the GWAS Catalog harmonizer’s reference-strand consensus-rate procedure over nonpalindromic variants after build conversion. The paragraph does not specify direct command execution. | Methods / GWAS meta-analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Manual annotation of PBMC scRNA-seq data |
| KING | Genetic kinship and relatedness estimation. | Methods / QC and imputation in the Japanese datasets |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / Spatially resolved mapping of regions associated with MS |
| MAGMA | Calculated MAGMA gene P-values/z-scores from cross-population MS GWAS and selected the top1000 genes outside the chromosome6:25-34Mb MHC region for scDRS1.0.2. | Methods / Detection of cell types associated with MS |
| METAL | GWAS meta-analysis. | Methods / GWAS meta-analysis |
| Minimac | Genotype imputation. | Methods / QC and imputation in the Japanese datasets |
| PLINK 2 | Genotype QC, association and score calculation. | Methods / QC and imputation in the Japanese datasets |
| RE2C | Used the Lin-Sullivan method in RE2Cv1.0.6 for GWAS meta-analysis of studies with overlapping samples. | Methods / GWAS meta-analysis |
| REGENIE | Ran REGENIE 3.3 MS case-control association in the selected UKB white-British cohort with age, sex and ten PCs after stated sample/variant and imputation QC. | Methods / UK Biobank |
| SAIGE | Used SAIGE0.45 GLMMs for Japanese multiple-sclerosis GWAS and SAIGE1.0.7 for Genomics England European-ancestry analysis,adjusting for age,sex and ten PCs. | Methods / GWAS of the Japanese datasets |
| Scanpy | Used for cell clustering. | Methods / Manual annotation of PBMC scRNA-seq data |
| scDRS | Single-cell disease relevance scoring. | Methods / Detection of cell types associated with MS |
| scds | Ran scds 1.18 alongside Scrublet per sample after CellRanger and CellBender. | Methods / Alignment, quantification and QC of PBMC scRNA-seq data |
| Scrublet | Ran Scrublet 0.2.3 and scds 1.18 per sample after CellRanger 7 and CellBender 0.1.0 processing. | Methods / Alignment, quantification and QC of PBMC scRNA-seq data |
| SHAPEIT | Genotype haplotype phasing. | Methods / QC and imputation in the Japanese datasets |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Alignment, quantification and QC of PBMC scRNA-seq data |
| UCSC liftOver | Converted hg38 GWAS summary statistics to hg19 with UCSC liftOver before reference-strand harmonization and multi-cohort meta-analysis. | Methods / GWAS meta-analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Manual annotation of PBMC scRNA-seq data |

XML SHA-256: `ec8569f654d7cd6b12e65cecd9140cada5e47a5b02dc2d32d9cf29e89ec407c5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P124: Transcriptional and epigenetic regulators of human CD8+ T cell function identified through orthogonal CRISPR screens

Nature Genetics · 2023-11-09 · [DOI](https://doi.org/10.1038/s41588-023-01554-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703699/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / Processing gRNA sequencing and gRNA analysis for FACS-based screens |
| Cell Ranger | Processed/demultiplexed scRNA-seq and generated transcript and guide-RNA UMI counts per cell barcode using CellRanger v6.0.1. | Methods / Processing and analyzing scRNA-seq |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Methods / ATAC-seq |
| deepTools | Generated CPM-normalized ATAC-seq bigWigs with deepTools3.0.1 bamCoverage after Bowtie1,blacklist and duplicate filtering. | Methods / ATAC-seq |
| DESeq2 | Count-based differential expression analysis. | Methods / Processing gRNA sequencing and gRNA analysis for FACS-based screens |
| DoubletFinder | Single-cell doublet detection. | Methods / Processing and analyzing scRNA-seq |
| Enrichr | Gene-set enrichment. | Methods / Processing and analyzing scRNA-seq |
| FastQC | Sequencing read quality assessment. | Methods / ATAC-seq |
| featureCounts | Generated gene counts with featureCounts in Subread 1.4.6-p4 against GENCODE v22 comprehensive GRCh38 after Trimmomatic and STAR 2.4.1a. | Methods / RNA sequencing |
| HOMER | Motif and regulatory enrichment analysis. | Methods / ATAC-seq |
| MACS | Called ATAC narrow peaks with MACS2 and retained reported adjusted-P values at most 0.001, merged calls across samples, and used peak counts for DESeq2 differential analysis and annotation. | Methods / ATAC-seq |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / ATAC-seq |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Processing and analyzing scRNA-seq |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA sequencing |
| Trimmomatic | Removed RNA-seq adapters with Trimmomatic0.32 before STAR GRCh38 alignment, featureCounts and DESeq2. A separate ATAC-seq paragraph also reports Trimmomatic adapter trimming before Bowtie1 and peak analysis. | Methods / RNA sequencing |

XML SHA-256: `28ca67729ea914e96b6e7973efa90054a63834a5f18772413ad3708a2e1b172c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P125: Genome-wide association analyses identify distinct genetic architectures for early-onset and late-onset depression

Nature Genetics · 2025-11-13 · [DOI](https://doi.org/10.1038/s41588-025-02396-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695632/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Genomic SEM | Genetic covariance and structural equation modelling. | Methods / Genomic SEM |
| LDpred | LD-aware Bayesian polygenic score construction. | Methods / Polygenic risk scores |
| LDSC | Used for SNP heritability estimation. | Methods / GWAS and meta-analysis |
| MAGMA | Used MAGMA1.08 with35kb-upstream/10kb-downstream expanded gene boundaries and a European subset of the high-coverage1000Genomes reference. | Methods / Gene-based tests |
| METAL | GWAS meta-analysis. | Methods / GWAS and meta-analysis |
| MR-PRESSO | MR pleiotropy/outlier sensitivity. | Methods / Mendelian randomization |
| PLINK | Used for LD-based variant selection. | Methods / GWAS and meta-analysis |
| REGENIE | Ran cohort-specific REGENIE GWAS for broad, early-onset, late-onset and case-case MDD comparisons using age, sex and ten PCs; then combined cohort statistics with METAL inverse-variance meta-analysis. | Methods / GWAS and meta-analysis |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Mendelian randomization |

XML SHA-256: `f35977fb92d9c713b8f0f887bbc688a2475a5ea42207a0edbf260b0ba815cc95`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P126: Rare variant analyses in 51,256 type 2 diabetes cases and 370,487 controls reveal the pathogenicity spectrum of monogenic diabetes genes

Nature Genetics · 2024-10-08 · [DOI](https://doi.org/10.1038/s41588-024-01947-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549050/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / Post-association analysis / Identification of the independent set of T2D-associated markers |
| GCTA | Used for genetic association analysis; conditional or joint association analysis. | Methods / Post-association analysis / Identification of the independent set of T2D-associated markers |
| HOMER | Motif and regulatory enrichment analysis. | Methods / Functional analysis of noncoding variants / In silico interpretation of new noncoding variants |
| METAL | GWAS meta-analysis. | Methods / Association analysis |
| PRS-CS | Inferred type2-diabetes PRS weights with PRS-CS-auto from a European GWAS meta-analysis excluding UKB,using a European UK Biobank LD panel,for later rare-gene carrier stratification. | Methods / Analysis of variants in monogenic diabetes genes / PRSs to stratify carriers of monogenic diabetes variants |
| REGENIE | Ran two-step binary T2D association using REGENIE v2 in UKB/GERA/MGBB and v3 in AoU, with step block sizes 1000/400 and age/sex/BMI/PC/batch covariates. Used approximate Firth plus Firth SE; pThresh 0.999 for UKB/GERA/MGBB and 0.05 for AoU. | Methods / Association analysis |

XML SHA-256: `52c69604a05c9f2c48debb522594ea3f39de3a7ccf0e701fed27530c99bc7bf5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P127: Computationally efficient meta-analysis of gene-based tests using summary statistics in large-scale genetic studies

Nature Genetics · 2025-11-12 · [DOI](https://doi.org/10.1038/s41588-025-02390-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695653/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Methods / Computing gene-based tests from summary statistics |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / Conditional analysis |
| MetaSTAAR | Summary-statistic gene-based rare-variant meta-analysis. | Methods / Computing gene-based tests from summary statistics |
| PolyPhen-2 | Missense variant effect annotation. | Methods / Variant annotations and gene sets |
| REGENIE | Fit REGENIE step 1 on array genotypes and step 2 on WES variants for multiple traits, retaining all polymorphic variants without a MAC exclusion and emitting HTP statistics for downstream REMETA gene tests. | Results / Methods overview / Single-variant association testing |
| SIFT | Missense variant effect annotation. | Methods / Variant annotations and gene sets |
| SuSiE | Bayesian fine-mapping and credible sets. | Results / Conditional analysis |
| Ensembl VEP | Variant consequence annotation. | Methods / Variant annotations and gene sets |

XML SHA-256: `77606c71ec8c2a524b62e37c7a59014631d5196648c30bd11737ba55d7ac15e5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P128: A multiancestry polygenic risk score for Alzheimer’s disease is associated with cognitive decline and neuropathological hallmarks in diverse populations

Nature Genetics · 2026-08-27 · [DOI](https://doi.org/10.1038/s41588-026-02722-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553296/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ComBat | Batch-effect adjustment. | Methods / AD-related traits / Neuroimaging data |
| GWAMA | GWAS meta-analysis. | Methods / PRS development |
| LDpred | LD-aware Bayesian polygenic score construction. | Methods / PRS evaluation in the ADSP dataset |
| PRS-CS | Inferred separate population-specific Alzheimer disease PRS weights using ancestry-matched 1000 Genomes HapMap3 LD panels; PRSice2 applied scores without clumping before ADGC combination-weight learning and ADSP evaluation. | Methods / PRS development / PRS-CS |
| PRS-CSx | Cross-population Bayesian polygenic scoring. | Methods / PRS development / PRS-CSx |
| PRSice | Clumping and thresholding polygenic score construction. | Methods / PRS development / PRSice2 |

XML SHA-256: `b318d7cad15920ee6ff59627209e75e46087bc52a41a6a87ceae355cca4be752`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P129: Most large structural variants in cancer genomes can be detected without long reads

Nature Genetics · 2023-11-09 · [DOI](https://doi.org/10.1038/s41588-023-01540-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703688/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ASCAT | Allele-specific tumor copy-number analysis. | Methods / Short-read whole-genome sequencing |
| BWA | DNA read alignment. | Methods / Nominating NAHR junctions |
| minimap2 | Mapped ONT tumor/normal reads to GRCh37 using minimap2 2.17 before multiple structural-variant callers and consensus selection. | Methods / Long-read whole-genome sequencing |
| Sniffles | Long-read structural variant discovery. | Methods / Long-read whole-genome sequencing |

XML SHA-256: `1f2e71d123a957c43821e96ac278e379a344577931404eb2d4f7c57fb4f2a7b8`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P130: GWAS of multiple neuropathology endophenotypes identifies new risk loci and provides insights into the genetic risk of dementia

Nature Genetics · 2024-10-08 · [DOI](https://doi.org/10.1038/s41588-024-01939-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549054/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Genotype data and quality control |
| coloc | Shared-signal colocalization analysis. | Methods / Statistical analyses / Colocalization analyses |
| FAVOR | Used FAVOR alongside FUMA for variant/gene mapping and functional-enrichment analyses. | Methods / Statistical analyses / FUMA and FAVOR annotation, gene-prioritization and functional enrichment pipeline |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Used MAGMA1.10 for NPE gene/gene-set analyses in FUMA/FAVOR pipelines; the source describes99.9percent variant-PC variance and an F test,with gene significance2.5e-6 and per-NPE Bonferroni pathway correction. | Methods / Statistical analyses / FUMA and FAVOR annotation, gene-prioritization and functional enrichment pipeline |
| KING | Genetic kinship and relatedness estimation. | Methods / Participants / ROSMAP |
| MAGMA | Used MAGMA1.10 for NPE gene/gene-set analyses in FUMA/FAVOR pipelines; the source describes99.9percent variant-PC variance and an F test,with gene significance2.5e-6 and per-NPE Bonferroni pathway correction. | Methods / Statistical analyses / FUMA and FAVOR annotation, gene-prioritization and functional enrichment pipeline |
| METAL | GWAS meta-analysis. | Methods / Statistical analyses / Single-variant GWAS |
| Minimac | Genotype imputation. | Methods / Genotype data and quality control |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Methods / Genotype data and quality control |
| SAIGE | Analyzed binary neuropathology phenotypes with SAIGE logistic mixed models and additive variant score tests;SAIGE1.1.3 is listed among null-model packages used in the two-stage analysis. | Methods / Statistical analyses / Single-variant GWAS |
| SAMtools | Alignment manipulation and QC. | Methods / Genotype data and quality control |

XML SHA-256: `42855fc8ef19d7ccdf79bee7cc0514baccf199463be2d5b85dfffed42f0fb4ee`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P131: Dissecting pleiotropy between major depressive disorder and physical disease comorbidities

Nature Genetics · 2026-08-26 · [DOI](https://doi.org/10.1038/s41588-026-02735-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553304/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Methods / Biological annotations / Gene expression of second-order factors |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Multivariate GWAS / Independent loci and QSNP heterogeneity |
| CAUSE | Causal inference with correlated pleiotropy. | Methods / Gastrointestinal factor follow-up analysis / CAUSE |
| coloc | Shared-signal colocalization analysis. | Methods / Biological annotations / Gene expression of second-order factors |
| FUMA | Functional mapping, annotation and gene prioritization. | Methods / Multivariate GWAS / Independent loci and QSNP heterogeneity |
| FUSION | Transcriptome-wide association and conditional follow-up. | Methods / Biological annotations / Gene expression of second-order factors |
| Genomic SEM | Genetic covariance and structural equation modelling. | Methods / Data selection and processing |
| GSA-MiXeR | Used GSA-MiXeR2.1.1 for exploratory gene-set heritability/enrichment,retaining AIC-positive sets with enrichment above1 and optionally filtering by MAGMA Bonferroni significance. | Methods / Biological annotations / Gene set enrichment |
| gsMap | Spatial mapping of genetic trait associations. | Methods / Biological annotations / gsMAP |
| LCV | Latent causal-variable genetic analysis. | Methods / Gastrointestinal factor follow-up analysis / LCV |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / LD score regression |
| MAGMA | Used MAGMA gene-set P-values with Bonferroni correction to conservatively prefilter exploratory GSA-MiXeR2.1.1 enrichment results,alongside an unfiltered exploratory report. | Methods / Biological annotations / Gene set enrichment |
| PLINK | Used for LD-based variant selection. | Methods / Multivariate GWAS / Independent loci and QSNP heterogeneity |

XML SHA-256: `d8f120704e4700fbb26e76704600acdde4e7ea6822c8c8dabe7a04d1e5b47353`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P132: Genetics and epidemiology of mutational barcode-defined clonal hematopoiesis

Nature Genetics · 2023-11-06 · [DOI](https://doi.org/10.1038/s41588-023-01555-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703693/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BOLT-LMM | Linear mixed-model GWAS. | Methods / CH GWAS / CH GWAS, association testing and meta-analysis |
| BWA | DNA read alignment. | Methods / WGS for CH case definition / UKB |
| coloc | Shared-signal colocalization analysis. | Methods / CH GWAS / RNA eQTL and sQTL analysis |
| GATK | Variant discovery and recalibration. | Methods / WGS for CH case definition / UKB |
| Graphtyper | Graph-based genotyping. | Methods / CH GWAS / Genotyping, WGS and imputation |
| kallisto | Estimated transcript abundances with kallisto after the study's personalized-genome RNA-seq workflow,then used BOLT-LMM for additive cis-eQTL tests with artifact/demographic/blood-cell/100LOCO-PC adjustment. | Methods / CH GWAS / RNA eQTL and sQTL analysis |
| LeafCutter | Intron excision quantification and splicing analysis. | Methods / CH GWAS / RNA eQTL and sQTL analysis |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / WGS for CH case definition / UKB |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Methods / Somatic genetics / Gene-based somatic mutation burden testing |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / CH GWAS / RNA eQTL and sQTL analysis |
| Strelka2 | Somatic SNV and short-indel calling. | Methods / WGS for CH case definition / Definition of CPLD-CH |
| Ensembl VEP | Variant consequence annotation. | Methods / WGS for CH case definition / Definition of CPLD-CH |

XML SHA-256: `3c71952c0b5cda7ed9b9aa4b719e22be08396c5943cafef6973884971326911f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P133: Genome-scale CRISPR screens identify PTGES3 as a direct modulator of androgen receptor function in advanced prostate cancer

Nature Genetics · 2025-11-05 · [DOI](https://doi.org/10.1038/s41588-025-02388-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695660/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Structural modeling of AR–PTGES3 complexes |
| BEDTools | Genomic interval manipulation and overlap. | Methods / ChIP–seq data analyses |
| Bowtie 2 | Short-read alignment. | Methods / ATAC-seq data processing |
| BWA | DNA read alignment. | Methods / ChIP–seq data analyses |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Methods / ATAC-seq data processing |
| Cutadapt | Adapter trimming. | Methods / RNA sequencing |
| deepTools | Used deepTools3.5.4 bamCoverage with RPGC for ChIP-seq and computeMatrix/plotHeatmap around genomic features. Separate ATAC preprocessing used alignmentSieve ATACshift in deepTools3.3.2, applying+4/-5 read-start shifts. | Methods / ChIP–seq data analyses |
| DESeq2 | Count-based differential expression analysis. | Methods / STRING and gene set enrichment analysis |
| FastQC | Sequencing read quality assessment. | Methods / RNA sequencing |
| fgsea | Ran preranked fgsea on DESeq2 Wald statistics against downloaded MSigDB Hallmark gene sets. | Methods / STRING and gene set enrichment analysis |
| HOMER | Motif and regulatory enrichment analysis. | Methods / ChIP–seq data analyses |
| Integrative Genomics Viewer (IGV) | Obtained ChIP-seq visualization snapshots with IGV v2.19.1 from RPGC-normalized BigWig tracks generated by deepTools bamCoverage. | Methods / ChIP–seq data analyses |
| MACS | Called ChIP peaks with MACS3 3.0.0 at Q-value 0.01. In a separate ATAC pipeline, called MACS2 2.2.5 BAMPE narrow peaks at q-value 0.05 with no model, bedGraph, keep-all duplicates and summits after Tn5 correction. | Methods / ChIP–seq data analyses |
| MEME | Motif discovery and scanning. | Methods / ATAC-seq data processing / Over-representation analysis / TF binding analysis |
| MetaboAnalyst | Metabolomic statistics and pathway analysis. | Methods / Mass spectrometry |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / ChIP–seq data analyses |
| PyMOL | Performed structural alignments and figure rendering of experimental/AlphaFold3 AR-PTGES3 complex models with PyMOL. | Methods / Structural modeling of AR–PTGES3 complexes |
| Rsubread | R interface for read mapping, feature/junction counting and variant analysis. | Methods / RNA sequencing |
| SAMtools | Alignment manipulation and QC. | Methods / ChIP–seq data analyses |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA sequencing |
| Trimmomatic | Trimmed ChIP-seq FASTQ reads with Trimmomatic0.39 before BWA-MEM hg38 alignment, MAPQ30/blacklist/duplicate filtering and MACS3 peaks; deepTools later supplied RPGC tracks and heatmaps. | Methods / ChIP–seq data analyses |

XML SHA-256: `f70438623e8599bd86e6c1b747ee584bd36e8afbc84d715badca1641edf29f25`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P134: Developmental and MAPK-responsive transcription factors regulate distinct malignant cell states and associated genetic dependencies in pancreatic cancer

Nature Genetics · 2026-08-26 · [DOI](https://doi.org/10.1038/s41588-026-02714-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553295/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ARACNe | Inferred the PDA regulatory networks used for metaVIPER protein-activity analysis from PDA samples with ARACNe. | Methods / Single-cell analysis |
| aREA | Used analytic rank-based enrichment analysis to assess conservation of Elyada-derived cell-state signatures across independent malignant-cell cohorts, cell lines and a PDX model. Also compared the 50 most activated and 50 most inactivated patient-signature proteins with cell-line protein-activity ranks using a two-sided aREA test. | Methods / Single-cell analysis |
| biomaRt | Used biomaRt together with kallisto0.44.0 and tximport for GRCh38 mRNA/ncRNA gene-expression quantification from PLATE-seq drug-perturbation libraries. | Methods / Drug perturbation assays |
| Cell Ranger | Processed lineage-tracing cell-line scRNA-seq with Cell Ranger v3.0.2, aligning FASTQs to human GRCh38-2020-A. | Methods / Single-cell lineage tracing |
| GSEA | Ranked gene-set enrichment. | Methods / Pooled TF overexpression assay (scRNA-seq) / CRISPR KO library design (in vivo) |
| kallisto | Used kallisto0.44.0 with sequence-specific bias correction for single-end PLATE-seq ORF perturbations,then aggregated transcript counts by Entrez IDs. A related drug PLATE-seq experiment used kallisto with tximport/biomaRt. | Methods / TF overexpression assay (PLATE-seq) |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Single-cell analysis |
| metaVIPER | Transformed gene-expression profiles into protein activities with metaVIPER using four bulk PDA regulatory networks and one single-cell PDA network,then analyzed malignant-cell states. | Methods / Single-cell analysis |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / Single-cell analysis |
| Seurat | Used for cell clustering. | Methods / Single-cell analysis |
| ShortRead | Assessed each PLATE-seq sample's sequencing-data quality with the ShortRead package. | Methods / Drug perturbation assays |
| tximport | Used tximport with kallisto0.44.0 and biomaRt to quantify GRCh38 mRNA/ncRNA gene expression from drug-perturbed pancreatic-cell PLATE-seq libraries. | Methods / Drug perturbation assays |
| UMI-tools | UMI counting and duplicate handling. | Methods / Single-cell lineage tracing |
| VIPER | Used Student’s t-test implemented in the VIPER package on normalized unperturbed GLS-versus-MOS cell-line expression profiles to construct a differential-expression signature; metaVIPER then converted that signature to protein activities. | Methods / TF overexpression assay (PLATE-seq) |

XML SHA-256: `6d2d8ed42a631cd6643f540253101197fc76f8b489be13e2a9e6c32a2ee99c8c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P135: An atlas of genetic determinants of forearm fracture

Nature Genetics · 2023-11-02 · [DOI](https://doi.org/10.1038/s41588-023-01527-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10632131/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Methods / Functional annotation |
| CADD | Variant deleteriousness annotation. | Methods / Functional annotation |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / Multi-SNP-based conditional and joint association analysis |
| FUMA | Functional mapping, annotation and gene prioritization. | Methods / Functional annotation |
| GCTA | Used for LD-based variant selection; genetic association analysis; conditional or joint association analysis. | Methods / Multi-SNP-based conditional and joint association analysis |
| LDSC | Used for cross-trait genetic correlation. | Methods / Genetic correlation |
| METAL | GWAS meta-analysis. | Methods / GWAS and meta-analysis |
| MR-PRESSO | MR pleiotropy/outlier sensitivity. | Methods / MR analysis of risk factors for fractures |
| PLINK | Used for genetic association analysis. | Methods / GWAS and meta-analysis |
| SAIGE | Contributing forearm-fracture cohorts used SAIGE or PLINK logistic models for additive per-allele SNP effects,with sex,age/age-squared,PCs,study site and family structure as applicable. | Methods / GWAS and meta-analysis |

XML SHA-256: `0c44b9e6179811684c6fb08bc74539310819e478131a5ec14a6b2ef51c150098`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P136: Population-specific putative causal variants shape quantitative traits

Nature Genetics · 2024-10-03 · [DOI](https://doi.org/10.1038/s41588-024-01913-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525193/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BOLT-LMM | Linear mixed-model GWAS. | Methods / Quantitative phenotype curation, QC and normalization |
| FINEMAP | Bayesian fine-mapping of association loci. | Methods / Statistical fine-mapping |
| GATK | Variant discovery and recalibration. | Methods / WGS and creation of the imputation reference panel |
| IMPUTE | Genotype imputation. | Methods / WGS and creation of the imputation reference panel |
| LDSC | Used for association inflation assessment. | Methods / LD Score regression |
| LDstore | Linkage disequilibrium matrix calculation. | Methods / Statistical fine-mapping |
| METAL | GWAS meta-analysis. | Methods / Quantitative and case-control association analysis and meta-analysis |
| Minimac | Genotype imputation. | Methods / Haplotype phasing and imputation |
| PLINK 2 | Used for genetic association analysis. | Methods / Quantitative and case-control association analysis and meta-analysis |
| PyMOL | Visualized protein structures from PDB6KN8,5LGD and5DO7 with PyMOL for Troponin-T,CD36 and ABCG5/ABCG8. | Methods / Protein visualization |
| SHAPEIT | Genotype haplotype phasing. | Methods / Haplotype phasing and imputation |
| UCSC liftOver | Mapped ENCODE DHS and conserved-footprint positions to hg19 before variant-overlap and regulatory-element/tissue enrichment analyses. | Methods / Regulatory element and tissue enrichment analysis |
| Ensembl VEP | Variant consequence annotation. | Methods / Variant annotation |

XML SHA-256: `e6075d2ebd3679fe39ea74b8d4c15e1c5e8d7d1a206c4cd4ba5227d13fb7880f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P137: Epigenetically driven and early immune evasion in colorectal cancer evolution

Nature Genetics · 2025-11-05 · [DOI](https://doi.org/10.1038/s41588-025-02349-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695633/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Methods / Processing of sequencing data |
| DESeq2 | Count-based differential expression analysis. | Methods / Processing of sequencing data |
| GenomicRanges | Genomic-range containers and overlap operations in R. | Methods / SCAA and SCAA loss analysis / TF analysis |
| Mutect2 | Somatic SNV and short-indel calling. | Methods / Processing of sequencing data |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Processing of sequencing data |
| Strelka2 | Somatic SNV and short-indel calling. | Methods / Immune escape prediction / Mutations in HLA |

XML SHA-256: `d88f004ec6aaa0a27733fb9485e793fa9dc4672b5af4d3154b970485b90de81a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P138: Predictive design of tissue-specific mammalian enhancers that function in the mouse embryo

Nature genetics · 2026-08-25 · [DOI](https://doi.org/10.1038/s41588-026-02729-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553301/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / Single-cell RNA-seq analyses |
| DeepSTARR | Predicted log2(mean ATAC treatment-pileup signal+1) from 1001-base one-hot sequences using a modified DeepSTARR CNN, duplicate runs and three held-out folds. | Methods / Sequence-to-accessibility models / Model architecture and training. |
| MACS | Called mouse embryo ATAC peaks per biological replicate and on merged reads using MACS2 2.2.5 with BAM, mouse genome shortcut, no model, shift -100, extension 200 and keep-all duplicates. Retained peaks supported in both replicates and merged data; used treatment pileup for region-mean accessibility targets. | Methods / Processing of ATAC-seq chromatin accessibility data / Peak calling. |
| SAMtools | Alignment manipulation and QC. | Methods / Processing of ATAC-seq chromatin accessibility data / Peak calling. |
| UCSC bedGraphToBigWig | Conversion of genomic signal tracks to indexed bigWig. | Methods / Processing of ATAC-seq chromatin accessibility data / Peak calling. |

XML SHA-256: `6d21824b8b7b9eb63f2b3c49f6452b00ec2a3c5f1cf17b9cbe5dd5b6ee6cc5bf`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P139: Context transcription factors establish cooperative environments and mediate enhancer communication

Nature Genetics · 2024-10-03 · [DOI](https://doi.org/10.1038/s41588-024-01892-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525195/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Methods / GM12878 ATAC–seq processing and peak scoring |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Methods / Defining enhancer categories |
| deepTools | Converted sorted/indexed GM12878 ATAC BAMs to bigWig with deepTools3.5.0 bamCoverage; downstream enhancer accessibility summed counts in±350bp around peak centers. | Methods / GM12878 ATAC–seq processing and peak scoring |
| DESeq2 | Count-based differential expression analysis. | Methods / Assessing BETi sensitivity in CMs |
| MEME | Motif discovery and scanning. | Methods / Analysis of TF BS syntax |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / ASA in GM12878 |
| Rsubread | R interface for read mapping, feature/junction counting and variant analysis. | Methods / Assessing BETi sensitivity in CMs |
| SAMtools | Alignment manipulation and QC. | Methods / GM12878 ATAC–seq processing and peak scoring |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Assessing BETi sensitivity in CMs |

XML SHA-256: `3e30aea6621a602bcd402a2e1ced0d8f4d08f298749bb7a586c813a85872cae0`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P140: Genetic associations with educational fields

Nature Genetics · 2025-11-04 · [DOI](https://doi.org/10.1038/s41588-025-02391-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695639/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| fastGWA | Sparse-relationship mixed-model GWAS. | Methods / Analyses / GWA meta-analyses |
| FUMA | Functional mapping, annotation and gene prioritization. | Methods / Analyses / GWA meta-analyses |
| GCTA | Used for genetic association analysis. | Methods / Analyses / GWA meta-analyses |
| Genomic SEM | Genetic covariance and structural equation modelling. | Methods / Analyses / GWAS-by-subtraction analyses controlling for educational attainment |
| IMPUTE | Genotype imputation. | Methods / Genetic data QC / MoBa |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / Analyses / SNP-based heritability analyses |
| METAL | GWAS meta-analysis. | Methods / Analyses / GWA meta-analyses |
| REGENIE | Used REGENIE 2.2.4 binary association with Firth correction for educational-field phenotypes in FinnGen. MoBa used the separate GCTA FastGWA-GLMM procedure. | Methods / Analyses / GWA meta-analyses |
| SBayesR | Bayesian mixture polygenic score estimation. | Methods / Measures / PGIs in Dutch Lifelines |

XML SHA-256: `0fe7de7050ee813d37932b53c07cb52cc920edf4270ad1eff0ae121aaa0d71cc`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P141: Rare variant effect estimation and polygenic risk prediction

Nature Genetics · 2026-08-17 · [DOI](https://doi.org/10.1038/s41588-026-02705-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553284/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaMissense | Missense variant pathogenicity prediction. | Methods / Rare variant PRS calculation |
| DeepRVAT | Deep-learning rare-variant association. | Methods / Rare variant PRS calculation |
| PRS-CS | Used PRS-CS as an alternative method for the common-variant PRS component when comparing and combining it with separately estimated rare-variant scores. | Methods / Rare variant PRS calculation |
| SAIGE | Used the SAIGE step1 average-information null-GLMM fitting approach and SAIGE two-sided single-variant score-test P values in the RareEffect analyses. | Methods / Fitting the null generalized mixed model (step 1) |
| SAIGE-GENE | Used SAIGE-GENE+ gene-level P values for burden/SKAT-O comparisons and selection of genes for rare-variant PRS;the training-set selection used P<2.5e-6,or the top ten genes when fewer passed. | Methods / Rare variant PRS calculation |
| SBayesR | Bayesian mixture polygenic score estimation. | Methods / Rare variant PRS calculation |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Results / UKB WES data analysis |

XML SHA-256: `a9307238319b75e931cf3fd7e78604889ea61ea9d2848c78339092c955e4b8a3`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P142: Mosaic chromosomal alterations in blood across ancestries using whole-genome sequencing

Nature Genetics · 2023-10-30 · [DOI](https://doi.org/10.1038/s41588-023-01553-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10632132/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Methods / Association analyses |
| LOFTEE | Loss-of-function variant annotation. | Methods / Association analyses |
| RFMix | Local ancestry inference. | Methods / Estimation of genetic ancestry proportions |
| Ensembl VEP | Variant consequence annotation. | Methods / Association analyses |

XML SHA-256: `03028ba5e150ca461688b1c933746b720067aef46a9353621c4d7b38262d53e4`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P143: MHC Hammer reveals genetic and non-genetic HLA disruption in cancer evolution

Nature Genetics · 2024-10-02 · [DOI](https://doi.org/10.1038/s41588-024-01883-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525181/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ASCAT | Allele-specific tumor copy-number analysis. | Methods / The TCGA cohort |
| GATK | Variant discovery and recalibration. | Methods / The MHC Hammer pipeline / Filters implemented for somatic HLA mutations |
| Nextflow | Analysis pipeline orchestration. | Results / A pipeline to evaluate HLA disruption |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Calling alternative splicing in all protein-coding genes |
| Ensembl VEP | Variant consequence annotation. | Methods / The MHC Hammer pipeline / Filters implemented for somatic HLA mutations |

XML SHA-256: `e453ab0b8a0631ac65243f10fc5e52a1d246024f8236a9a15322eaceda27c987`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P144: Functionally dominant hotspot mutations of mitochondrial ribosomal RNA genes in cancer

Nature Genetics · 2025-11-03 · [DOI](https://doi.org/10.1038/s41588-025-02374-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597822/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Aligned joint single-cell RNA and ATAC reads to human GRCh38 using CellRanger-Arc v2.0.2. | Methods / Analysis of single-cell RNA sequencing and ATAC-seq and calculation of heteroplasmy |
| fgsea | Ranked genes by decreasing Spearman correlation between expression and m.1227G>A heteroplasmy, then ran fgsea with the C5 GO collection to identify enriched or downregulated pathways. | Methods / Gene set enrichment analysis |
| GSEA | Ranked gene-set enrichment. | Methods / Correlation of gene expression and heteroplasmy |
| MaxQuant | Mass-spectrometry proteomic identification and quantification. | Methods / Analysis of proteomic data |
| Mutect2 | Somatic SNV and short-indel calling. | Methods / Somatic mitochondrial mutation calling |
| SAMtools | Alignment manipulation and QC. | Methods / Somatic mitochondrial mutation calling |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Analysis of single-cell RNA sequencing and ATAC-seq and calculation of heteroplasmy |

XML SHA-256: `880d3a8bb266dbd6172236f4d27161feaf2e49a239b502d71822657893588723`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P145: PLCG2 downregulation impairs synaptic function and increases Alzheimer’s disease hallmarks in neuronal cultures

Nature Genetics · 2026-08-14 · [DOI](https://doi.org/10.1038/s41588-026-02709-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553309/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AUCell | Rank-based gene-set activity scoring. | Methods / snRNA-seq of hNCs / Regulon analysis |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / WES data |
| CellChat | Ligand-receptor cell communication inference. | Methods / snRNA-seq of hNCs / Cell–cell communication |
| Cell Ranger | Processed four multiplexed nuclei libraries using Cell Ranger multi in Multiplex Flex mode with GRCh38-2024-A and the v1.1.0_GRCh38-2024-A human probe set. | Methods / snRNA-seq of hNCs / snRNA-seq data preprocessing and annotation |
| DESeq2 | Count-based differential expression analysis. | Methods / snRNA-seq of hNCs / Differential expression and pathway enrichment analysis |
| Harmony | Batch integration of cell embeddings. | Methods / snRNA-seq of hNCs / snRNA-seq data preprocessing and annotation |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / snRNA-seq of hNCs / snRNA-seq data preprocessing and annotation |
| SCENIC | Single-cell regulatory network inference. | Methods / snRNA-seq of hNCs / Regulon analysis |
| sctransform | Used Seurat 5.2.1 SCTransform with mitochondrial-percent regression, then top-3000 residual features, 20 principal components and Harmony. | Methods / snRNA-seq of hNCs / snRNA-seq data preprocessing and annotation |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Analysis of snRNA-seq datasets of human brains |
| SoupX | Ambient RNA contamination correction. | Methods / snRNA-seq of hNCs / snRNA-seq data preprocessing and annotation |
| UCell | Rank-based single-cell gene-set scoring. | Methods / snRNA-seq of hNCs / Neuronal maturation score calculation |
| Ensembl VEP | Variant consequence annotation. | Methods / WES data |

XML SHA-256: `9fdbbf6616ee60cf8d1b808afb0ab48d16a2e156eb5d9dbdd8602fd405fc3b46`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P146: Effects of psoriasis and psoralen exposure on the somatic mutation landscape of the skin

Nature Genetics · 2023-10-26 · [DOI](https://doi.org/10.1038/s41588-023-01545-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10632143/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ASCAT | Allele-specific tumor copy-number analysis. | Methods / Somatic mutation calling / Structural variant calling in the exome data with allele-specific copy number analysis of tumors |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Mutational signatures / Characterization of the psoralen signature |
| BWA | DNA read alignment. | Methods / DNA sequencing / Whole-exome sequencing |
| dndscv | Somatic mutation selection analysis. | Methods / Selection and driver analyses / Exome-wide driver discovery |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / DNA sequencing / Whole-exome sequencing |
| SAMtools | Alignment manipulation and QC. | Methods / DNA sequencing / Whole-exome sequencing |

XML SHA-256: `bbf17164639a6bbbb9d2437e32807e743c69abae1be5d1e39a9d8d83dcbdf46d`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P147: Liability threshold model-based disease risk prediction based on electronic health record phenotypes

Nature Genetics · 2025-11-03 · [DOI](https://doi.org/10.1038/s41588-025-02370-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597826/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AutoComplete | Deep learning-based phenotype imputation. | Methods / Missing value handling in EHR data |
| LDSC | Used for SNP heritability estimation. | Methods / Liability threshold model / Extension to multiple traits |
| LTPI | Liability-threshold phenotype imputation. | Methods / Estimation of posterior mean genetic liability |
| PLINK | Used for genotype quality control. | Methods / GWAS association tests |
| PRSice | Clumping and thresholding polygenic score construction. | Methods / Estimate PRSpleiotropy |
| REGENIE | Ran two-step REGENIE on a single CPU thread for LTPI, LTFH and baseline GWAS, explicitly omitting binary, Firth and SPA flags. Models included five PCs, genotyping batch, gender and age after autosomal European-sample QC and HLA-region exclusion. | Methods / GWAS association tests |
| SAIGE | Reused PheWeb SAIGE GWAS summaries for1,402PheCodes to obtain covariance estimates for chronic-kidney-disease LTPI risk analysis. | Results / CKD risk estimation |

XML SHA-256: `7047f4267081204079ed5ccd68a3ca7743fefb589b316ec4a51ed3a26622863d`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P148: Prevalence and chronology of colibactin-associated mutational processes and their microbiome spectra in Japanese colorectal cancer

Nature Genetics · 2026-08-10 · [DOI](https://doi.org/10.1038/s41588-026-02692-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553305/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / Metagenomic sequencing quality control and taxonomic profiling |
| BWA | DNA read alignment. | Methods / Mutation calling |
| CIBERSORT | Cell-type deconvolution. | Methods / Decomposition of tumor immune environments |
| Cutadapt | Adapter trimming. | Methods / Metagenomic sequencing quality control and taxonomic profiling |
| dndscv | Somatic mutation selection analysis. | Methods / Significantly mutated gene analysis |
| HTSeq | Read counting and sequence processing. | Methods / Transcriptome sequencing (RNA-seq) |
| SAMtools | Alignment manipulation and QC. | Methods / Mutation calling |
| SHAP | Model feature attribution. | Methods / Identification of CRC subtypes based on metagenome-derived SHAP values / SHAP analysis and subtype identification |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Transcriptome sequencing (RNA-seq) |

XML SHA-256: `3e088144a4ff911be8ee86605fa795c24a7aa68fc0c04533d55ea445706bc287`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P149: Rare variants with large effects provide functional insights into the pathology of migraine subtypes, with and without aura

Nature Genetics · 2023-10-26 · [DOI](https://doi.org/10.1038/s41588-023-01538-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10632135/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Genetic ancestry filtering and principal components |
| Beagle | Imputed FinnGen Release 6 array genotypes with the Finnish SISu v3 whole-genome reference panel. | Methods / Genotyping and whole-genome sequencing / Finland |
| Eagle | Genotype phasing. | Methods / Genotyping and whole-genome sequencing / Denmark |
| FUMA | Functional mapping, annotation and gene prioritization. | Methods / Functional data and colocalization analysis |
| GCTA | Used for conditional or joint association analysis. | Methods / Association testing and meta-analysis |
| Graphtyper | Graph-based genotyping. | Methods / Genotyping and whole-genome sequencing / Iceland |
| IMPUTE | Genotype imputation. | Methods / Genotyping and whole-genome sequencing / Denmark |
| kallisto | Computed blood/adipose gene expression from personalized transcript abundances with kallisto; the software table reports0.46. cis-eQTL regression used normal-quantile expression and chromosome-specific LOCO expression PCs. | Methods / Functional data and colocalization analysis |
| LDSC | Used for annotation-stratified genetic enrichment; association inflation assessment. | Methods / Association testing and meta-analysis |
| SHAPEIT | Genotype haplotype phasing. | Methods / Genotyping and whole-genome sequencing / US |
| Ensembl VEP | Variant consequence annotation. | Methods / Genotyping and whole-genome sequencing / Iceland |

XML SHA-256: `1a0fc0dd6a23b6f6035edaf86021ad90a1024a40a474cf1f62c49500ff3a97b1`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P150: Multivariate genomic analysis of 5 million people elucidates the genetic architecture of shared components of the metabolic syndrome

Nature Genetics · 2024-09-30 · [DOI](https://doi.org/10.1038/s41588-024-01933-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549047/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / MetS multivariate GWAS with Genomic SEM |
| EasyQC | Used EasyQCv23.8 to QC GWAS summaries against European1000G phase3, excluding invalid/reference-mismatched alleles, monomorphic/rare/duplicate/multiallelic variants, INFO<0.9 when available and variants absent from the reference. | Methods / Quality control of GWAS |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Used MAGMA1.08 through FUMA for SNP-wise-mean gene aggregation with European1000Genomes Phase3 LD and17674 protein-coding genes from Ensembl85; Bonferroni gene threshold2.83e-6. | Methods / MAGMA gene-based, gene set and gene property analyses |
| GCTA | Used for genetic association analysis; conditional or joint association analysis. | Methods / MetS multivariate GWAS with Genomic SEM |
| Genomic SEM | Genetic covariance and structural equation modelling. | Methods / Parallel analysis |
| HEIDI | Heterogeneity test distinguishing pleiotropy from linkage. | Methods / Gene prioritization |
| LDSC | Used for cross-trait genetic correlation. | Methods / Parallel analysis |
| MAGMA | Used MAGMA1.08 through FUMA for SNP-wise-mean gene aggregation with European1000Genomes Phase3 LD and17674 protein-coding genes from Ensembl85; Bonferroni gene threshold2.83e-6. | Methods / MAGMA gene-based, gene set and gene property analyses |
| METAL | GWAS meta-analysis. | Methods / Selection of GWAS on MetS components and meta-analysis |
| MR-PRESSO | MR pleiotropy/outlier sensitivity. | Methods / Two-sample Mendelian randomization |
| PLINK 2 | Used PLINK v.2.0 logistic regression with firth-fallback for the KoGES dichotomized MetS GWAS, adjusting for sex, age and ten genetic PCs. Separately computed UKB PRS by summing effect-allele genotypes weighted by posterior mean effects generated with PRS-CS. | Methods / MetS GWAS in the East Asian population |
| Popcorn | Cross-population genetic correlation. | Methods / Crosspopulation genetic correlation |
| PRS-CS | Inferred PRS weights for metabolic-syndrome components and latent factors from UKB-excluded GWAS using phi=0.01,a European 1000 Genomes phase3 LD panel and effective N for binary phenotypes; reported a=1,b=0.5,1000/500/5 sampling settings preceded PLINK2 scoring. | Methods / PRS analysis |
| PRSice | Computed an additional PRSice-2 score analysis when evaluating transferability of European MetS GWAS scores to KoGES; detailed results are referred to the supplement. | Results / MetS GWAS and PRS transferability in East Asians |
| SAIGE | Reused UKB-SAIGE,PanUKB and Neale-laboratory outcome GWAS summaries for two-sample MR of metabolic syndrome;325outcomes were available from350PRS-PheWAS associations. | Methods / Two-sample Mendelian randomization |
| SMR | Summary-data molecular association or Mendelian randomization. | Methods / Gene prioritization |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Two-sample Mendelian randomization |
| UCSC liftOver | Converted FinnGen GWAS summary-statistic coordinates from GRCh38 to GRCh37 before harmonizing all datasets to GRCh37. | Methods / Quality control of GWAS |

XML SHA-256: `7c6932a914734569a95e3cbdc6976d0e9bdc128a6b4fdaf51de76407469c2c4f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P151: High-resolution promoter interaction analysis implicates genes involved in activation of type 3 innate lymphoid cells in immune disease risk

Nature Genetics · 2026-08-04 · [DOI](https://doi.org/10.1038/s41588-026-02681-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447110/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / PCHi-C data preprocessing and detection of significant interactions |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq data analysis |
| LDSC | Used for annotation-stratified genetic enrichment. | Methods / RELI analysis |
| Salmon | Transcript abundance quantification. | Methods / RNA-seq data analysis |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Summary-level GWAS imputation and multivariate fine-mapping |
| tximport | Imported Salmon transcript counts and collapsed them to gene counts for DESeq2 analysis of human hg38 and mouse mm10 RNA-seq, with separate CRISPRi/CRISPRa condition contrasts. | Methods / RNA-seq data analysis |
| UCSC liftOver | Converted regulatory-element/PIR intersections from hg38 to hg19 with UCSC liftOverv377, then sorted and merged them for RELI trait enrichment. | Methods / RELI analysis |
| Ensembl VEP | Variant consequence annotation. | Methods / multiCOGS |

XML SHA-256: `1104a9bc5cfeac5ade7332ce7d0f1ab2680e780cdf1d736a3db6a047aa2e9a4c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P152: Yield of genetic association signals from genomes, exomes and imputation in the UK Biobank

Nature Genetics · 2024-09-25 · [DOI](https://doi.org/10.1038/s41588-024-01930-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549045/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Methods / Association analyses / Gene-based associations |
| GATK | Variant discovery and recalibration. | Methods / UKB data preparation / Genome sequencing |
| Graphtyper | Graph-based genotyping. | Methods / UKB data preparation / Genome sequencing |
| PolyPhen-2 | Missense variant effect annotation. | Methods / Gene and variant annotation |
| REGENIE | Ran REGENIE 3.1.1 for 100 traits: step 1 used QCed observed array genotypes; step 2 added age, age squared, sex, age-by-sex and ten PCs with the step-1 predictions. Analyses included autosomes and chromosome X. | Methods / Association analyses |
| SIFT | Missense variant effect annotation. | Methods / Gene and variant annotation |
| SKAT | Rare-variant sequence-kernel association. | Methods / Association analyses / Gene-based associations |
| Ensembl VEP | Variant consequence annotation. | Methods / Gene and variant annotation |

XML SHA-256: `9ec1cfa7238a892b8bae50169f30e8e9062215f392cb02a7914cbad6a8ea956b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P153: Mapping enhancer–gene regulatory interactions from single-cell data

Nature Genetics · 2026-08-03 · [DOI](https://doi.org/10.1038/s41588-026-02695-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447104/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Processed genomes/annotations with cellranger-arc mkref v2.0.2 and ran standard ARC pipelines to obtain multiome count matrices and fragments for public and newly generated datasets. | Methods / Collecting and preprocessing of 10x Multiome data |
| FIMO | Quantified the effect of a variant on a transcription-factor motif using JASPAR motifs and FIMO 5.5.9 match scores. The results show TP73 MA0861.1 aligned to reference and alternative rs7696969 sequences and report log-odds-based motif-match P values. | Methods / Applying scE2G to prioritize causal genes and cell types for GWAS loci |
| Harmony | Batch integration of cell embeddings. | Methods / Cell-type annotation |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Methods / Applying scE2G to prioritize causal genes and cell types for GWAS loci |
| MACS | Selected MACS2 2.2.9 peak regions overlapping ABC candidate elements, built/binarized ATAC count matrices and related accessibility to expression; merged cell-type peak resources for comparative predictor inputs. | Methods / Kendall correlation |
| PoPS | Effector-gene prioritization using polygenic features. | Methods / GWAS benchmark |
| scE2G | Single-cell enhancer-to-gene prediction. | Methods / Training and applying scE2G |
| Seurat | Used for cell annotation. | Methods / Quality control for 10x Multiome data |
| Signac | Single-cell chromatin accessibility analysis. | Methods / Quality control for 10x Multiome data |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / eQTL benchmark |

XML SHA-256: `58d7366dacc144c8821f5657fa302461b72ca21490d647dbd8fef420ae138aa5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P154: Integration of variant annotations using deep set networks boosts rare variant association testing

Nature Genetics · 2024-09-25 · [DOI](https://doi.org/10.1038/s41588-024-01919-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525182/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / UKBB WES data / Variant data and quality control (QC) |
| CADD | Variant deleteriousness annotation. | Methods / Variant annotations / MAF thresholds |
| DeepRVAT | Deep-learning rare-variant association. | Methods / DeepRVAT model / Hyperparameters and software |
| PLINK | Used for genotype quality control; LD-based variant selection; genetic association analysis. | Methods / Conditional association tests |
| PolyPhen-2 | Missense variant effect annotation. | Methods / Alternative RVAT methods / STAAR |
| REGENIE | Compared REGENIE burden and SKAT tests using missense and pLoF masks. Burden used the maximum alternate-allele count across sites; approximate Firth applied below P=0.01 and SKAT used Beta(MAF;1,25) weights. | Methods / Alternative RVAT methods / REGENIE |
| SIFT | Missense variant effect annotation. | Methods / Phenotype prediction using DeepRVAT and alternative rare variant scores / Burden and gene impairment scores |
| SKAT | Rare-variant sequence-kernel association. | Methods / DeepRVAT model / Ensembling and CV scheme |
| SpliceAI | Variant splice-impact prediction. | Methods / Variant annotations / SpliceAI |
| STAAR | Annotation-informed rare-variant set tests. | Methods / Alternative RVAT methods / STAAR |
| Ensembl VEP | Variant consequence annotation. | Methods / Alternative RVAT methods / Burden tests and SKAT |

XML SHA-256: `fc8f4f7a20372c8d7efbb1645cda3008e9a3472b0768fcae0105772b0472351d`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P155: Transcription factor switching drives subtype-specific pancreatic cancer

Nature Genetics · 2025-10-30 · [DOI](https://doi.org/10.1038/s41588-025-02389-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695649/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / ChIP–seq analyses |
| Cytoscape | Network visualization and analysis. | Methods / ChIP–seq enrichment near regulated genes |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq analyses |
| DiffBind | Differential binding or accessibility across peak-count libraries. | Methods / ChIP–seq analyses |
| HiC-Pro | Hi-C read processing. | Methods / Hi-ChIP data processing |
| MACS | Called MACS2 2.1.1.2016 peaks after combining replicate ChIP alignments into one library and using input sequences as background; selected tag-enriched peaks for motif, coverage and differential-binding analyses. | Methods / ChIP–seq analyses |
| MEME | Motif discovery and scanning. | Methods / ChIP–seq analyses |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA-seq analyses |

XML SHA-256: `4fa8d434acb43c12473250318a50001d3ff308f37ec3ecf8d13507b30f51d08b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P156: Cumulative transcription factor binding and p300-mediated histone acetylation drive enhancer activation frequency

Nature Genetics · 2026-07-31 · [DOI](https://doi.org/10.1038/s41588-026-02703-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447113/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Nextflow | Analysis pipeline orchestration. | Methods / Statistics, data analysis and high-performance computing |

XML SHA-256: `569106d214f7b1e81e2f535242163acf16331596e461971361d4f70525de44ac`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P157: Statistically and functionally fine-mapped blood eQTLs and pQTLs from 1,405 humans reveal distinct regulation patterns and disease relevance

Nature Genetics · 2024-09-24 · [DOI](https://doi.org/10.1038/s41588-024-01896-3) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525184/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Methods / Defining shared and specific QTLs |
| FastQTL | Permutation-based molecular QTL mapping. | Methods / eQTL and sQTL fine-mapping |
| FINEMAP | Bayesian fine-mapping of association loci. | Methods / eQTL and sQTL fine-mapping |
| GATK | Variant discovery and recalibration. | Methods / Genotyping, RNA-seq and protein expression measurement |
| LeafCutter | Intron excision quantification and splicing analysis. | Methods / eQTL and sQTL fine-mapping |
| PEER | Latent confounder estimation. | Methods / eQTL and sQTL fine-mapping |
| RSEM | Transcript abundance estimation. | Methods / eQTL and sQTL fine-mapping |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / eQTL and sQTL fine-mapping |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / eQTL and sQTL fine-mapping |
| tensorQTL | Molecular QTL mapping. | Methods / Targeted trans-eQTL and trans-pQTL calling |
| Ensembl VEP | Variant consequence annotation. | Methods / Functional enrichment |
| WASP | Allele-swapped read remapping for allelic mapping-bias filtering. | Methods / eQTL and sQTL fine-mapping |

XML SHA-256: `98edf86475be2175e390999f6b7b6cd2ec2d2aefd39c79956a91384df78b59d1`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P158: Cell-type- and locus-specific epigenetic editing of memory expression

Nature Genetics · 2025-10-29 · [DOI](https://doi.org/10.1038/s41588-025-02368-y) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597831/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCLConvert | Sequencer BCL demultiplexing and FASTQ conversion. | Methods / ChIP–seq library preparation and analysis |
| Bowtie 2 | Short-read alignment. | Methods / ChIP–seq library preparation and analysis |
| Cell Ranger | Aligned scATAC-seq reads and counted features with cellranger-atac count using reported default parameters. | Methods / scATAC-seq and scRNA-seq library preparation and analyses |
| edgeR | Count-based differential expression analysis. | Methods / ChIP–seq library preparation and analysis |
| GenomicRanges | Genomic-range containers and overlap operations in R. | Methods / ChIP–seq library preparation and analysis |
| Integrative Genomics Viewer (IGV) | Visualized ChIP-seq data with Integrative Genomics Viewer v2.16.0 after mm10 alignment and the stated peak/normalization analysis. | Methods / ChIP–seq library preparation and analysis |
| MapMyCells | Used MapMyCells for reference cell annotation in the Parse single-cell workflow. | Methods / scATAC-seq and scRNA-seq library preparation and analyses |
| sctransform | Used SCTransform independently per library with 3000 highly variable genes after Parse Trailmaker 1.4.1 processing against an augmented mm10 reference. | Methods / scATAC-seq and scRNA-seq library preparation and analyses |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / scATAC-seq and scRNA-seq library preparation and analyses |
| Signac | Single-cell chromatin accessibility analysis. | Methods / scATAC-seq and scRNA-seq library preparation and analyses |
| Trailmaker | Used Parse Trailmaker 1.4.1 with an augmented mm10 reference before per-library SCTransform. | Methods / scATAC-seq and scRNA-seq library preparation and analyses |

XML SHA-256: `43ad08ecc1cfa9a6ddc89d82679dcf2b5e69d29c23bdcea15009587bddf2b53a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P159: Federated analysis of autosomal recessive coding variants in 29,745 developmental disorder patients from diverse populations

Nature Genetics · 2024-09-23 · [DOI](https://doi.org/10.1038/s41588-024-01910-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525179/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Runs of homozygosity |
| BWA | DNA read alignment. | Methods / Cohorts, sequencing, alignment and variant calling / DDD |
| GATK | Variant discovery and recalibration. | Methods / Cohorts, sequencing, alignment and variant calling / DDD |
| GCTA | Used for genetic principal components. | Methods / Ancestry assignment / Assigning broad-scale GIA groups |
| LOFTEE | Loss-of-function variant annotation. | Methods / Variant filtering and annotation / Variant annotation |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Cohorts, sequencing, alignment and variant calling / DDD |
| PLINK | Used for LD-based variant selection. | Methods / Ancestry assignment / Assigning broad-scale GIA groups |
| SAMtools | Alignment manipulation and QC. | Methods / Cohorts, sequencing, alignment and variant calling / GeneDx |
| SpliceAI | Variant splice-impact prediction. | Methods / Variant filtering and annotation / Variant annotation |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Ancestry assignment / Assigning broad-scale GIA groups |
| Ensembl VEP | Variant consequence annotation. | Methods / Variant filtering and annotation / Variant annotation |

XML SHA-256: `008c4b0ea8d309b4ba19c19cdd9e58124b44510ab62af2216e95ea2633ec7940`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P160: Spatiotemporal gene expression and cellular dynamics of the developing human heart

Nature Genetics · 2025-10-29 · [DOI](https://doi.org/10.1038/s41588-025-02352-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597827/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Results / Spatially informed analysis of developmental cardiac niches |
| Cell Ranger | Demultiplexed scRNA-seq libraries with cellranger mkfastq v4.0.0 before a separate index-hopping filter. | Methods / ScRNA-seq |

XML SHA-256: `b373f46e2eb1785dfbd4b67a21d9c35eb483dee2cfae618e8ad90d0aa8affe3a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P161: Single-cell spatial mapping of human kidney development implicates the microenvironment in guiding cell fate decisions

Nature Genetics · 2026-07-30 · [DOI](https://doi.org/10.1038/s41588-026-02665-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447095/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellBender | Ambient count background removal. | Methods / Bioinformatic analysis: scRNA-seq |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Methods / Histologic neighborhood annotation |
| Cell Ranger | Aligned scRNA-seq samples with CellRanger v7.2.0 to generate the expression matrix used for annotation and connectivity. | Methods / Bioinformatic analysis: scRNA-seq |
| Enrichr | Gene-set enrichment. | Methods / Pathway analysis |
| gseapy | Python gene-set enrichment analysis. | Methods / Pathway analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / Bioinformatic analysis: scRNA-seq |
| Scanpy | Single-cell expression processing and analysis. | Methods / Bioinformatic analysis: scRNA-seq |
| Scrublet | Used Scrublet 0.2.3 to remove predicted doublets in the single-cell preprocessing workflow. | Methods / Bioinformatic analysis: scRNA-seq |
| scVelo | RNA velocity analysis. | Methods / Bioinformatic analysis: scRNA-seq |
| scVI | Variational single-cell integration or expression modelling. | Methods / Bioinformatic analysis: spatial single-cell transcriptomics |
| Squidpy | Spatial neighborhood and expression analysis. | Methods / Bioinformatic analysis: spatial single-cell transcriptomics |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Bioinformatic analysis: scRNA-seq |

XML SHA-256: `c7641339cc2001905b83a8902fe7fbe7ec934ac6a288b435c2c829ccbb09772f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P162: Multi-ancestry genome-wide association analyses provide insights into the genetic basis of Hashimoto’s thyroiditis

Nature Genetics · 2026-07-29 · [DOI](https://doi.org/10.1038/s41588-026-02704-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447099/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| biomaRt | Filtered hypothyroid/cis-eQTL colocalization results to protein-coding genes according to gene IDs using the biomaRt R package. | Methods / Colocalization of hypothyroid and cis-eQTL associations |
| DEPICT | Gene-set and tissue enrichment of GWAS loci. | Methods / Functional enrichment and tissue expression analysis |
| EasyQC | GWAS summary-statistic QC against reference variants. | Methods / Meta-analyses of individual cohort results |
| FUMA | Used the FUMA web workflow around the reported MAGMA analysis. Used MAGMA within FUMA1.3.7 for gene-set and tissue-expression enrichment across54GTEx8 tissues; independent-variant selection used FUMA r-squared0.05. | Methods / Functional enrichment and tissue expression analysis |
| LDSC | Used for association inflation assessment. | Methods / Meta-analyses of individual cohort results |
| MAGMA | Used MAGMA within FUMA1.3.7 for gene-set and tissue-expression enrichment across54GTEx8 tissues; independent-variant selection used FUMA r-squared0.05. | Methods / Functional enrichment and tissue expression analysis |
| METAL | GWAS meta-analysis. | Methods / Meta-analyses of individual cohort results |
| MR-MEGA | Trans-ancestry meta-regression. | Results / Fine-mapping results and ancestry-related heterogeneity |
| PLINK | Used for LD-based variant selection; genetic association analysis. | Methods / Meta-analyses of individual cohort results |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Functional enrichment and tissue expression analysis |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Mendelian randomization |

XML SHA-256: `2bafca9ee99bacd727b0e5e5e0dad28ab4681e32ffb8073a95172638c310be0f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P163: Pharmacological restriction of genomic binding sites redirects PU.1 pioneer transcription factor activity

Nature Genetics · 2024-09-18 · [DOI](https://doi.org/10.1038/s41588-024-01911-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525197/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Generated CUT&Tag bedGraph coverage with BEDTools genomecov and a scaling factor based on one million divided by total human reads before supplied-track threshold peak calling. | Methods / Data processing |
| Bowtie 2 | Mapped CUT&Tag to hg38 using Bowtie2 2.2.3, end-to-end/very-sensitive, no-mixed/no-discordant, Phred33 and insert bounds 10–700. Classical ChIP used 2.2.3 against hg38/dm3; PRO-seq separately used 2.5.1. | Methods / Data processing |
| Cutadapt | Trimmed PRO-seq adapters with Cutadapt 1.18 before Bowtie2 2.5.1 alignment and NRSA gene-body analysis. | Methods / PRO–seq |
| DESeq2 | Normalized RNA-seq raw counts and tested differential expression with DESeq2. Separately, DiffBind used DESeq2 analyses when reported n > 2. | Methods / Data processing |
| DiffBind | Used DiffBind 3.12.0 with DESeq2 when n > 2 and FDR cutoff 0.1; displayed technical variability in PCA/heatmaps and randomized vehicle/DB2115 pairs as a null comparison. Used a custom script when n = 1. | Methods / Data processing |
| Enrichr | Performed additional gene-list enrichment using Enrichr after separate ranked FGSEA analyses. | Methods / Data processing |
| FastQC | Assessed CUT&Tag read quality with FastQC 0.11.4 after reported Picard FASTQ generation and TrimGalore adapter trimming. | Methods / CUT&Tag |
| fgsea | Ranked genes by negative logarithm of adjusted P-value times the sign of fold change, then ran FGSEA against MSigDB 7.4 c1–8 and hallmark gene lists. | Methods / Data processing |
| GoodpeakAnalysis | Used the custom GoodpeaksScript/GoodpeakAnalysis route when n = 1, with average peak intensity >7.5, fold change >4 and summit >3. Classical ChIP differential analysis separately used GoodpeakAnalysis after spike-in normalization. | Methods / Data processing |
| HOMER | Performed motif analysis and peak annotation with the Homer package 4.11.1 after CUT&Tag peak generation. | Methods / Data processing |
| Integrative Genomics Viewer (IGV) | Visualized CUT&Tag bigWig tracks in IGV 2.4.15 and separately used IGV for classical ChIP bigWig visualization. | Methods / Data processing |
| MACS | Called peaks from already normalized CUT&Tag bedGraph with MACS2 2.1.0 bdgpeakcall using minimum length 100 and raw signal cutoff 2. Separately compared replicate MACS2 peak scores to input for CLICK scores and used normalized ChIP alignments for another peak-calling workflow. | Methods / Data processing |
| Nascent RNA Sequencing Analysis (NRSA) | Determined PRO-seq gene-body changes with the Nascent RNA Sequencing Analysis pipeline after Cutadapt, Bowtie2 and SAMtools format conversion. | Methods / PRO–seq |
| Picard | Generated CUT&Tag FASTQ files with Picard tools 2.17.1. Separately removed classical ChIP duplicates with Picard before Drosophila-ratio BAM subsampling. | Methods / CUT&Tag |
| SAMtools | Converted PRO-seq alignment file formats using SAMtools 1.9 before the NRSA nascent-transcription pipeline. | Methods / PRO–seq |
| STAR | Mapped RNA-seq reads to the hg38 transcriptome using STAR before raw-count normalization and differential expression with DESeq2. | Methods / Data processing |
| Trim Galore | Used TrimGalore 0.3.7 for adapter removal in CUT&Tag, ChIP-seq and Omni-ATAC-seq workflows after FASTQ generation, with separate FastQC assessment before downstream mapping and peak analysis. | Methods / CUT&Tag |
| UCSC bedGraphToBigWig | Converted normalized CUT&Tag bedGraph to bigWig with bedGraphToBigWig for IGV visualization. | Methods / Data processing |

XML SHA-256: `952a7f7cfb1744af6e4fc1a89ae6f063d4a0bab8d0ecb358d199f722e37b9ee6`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P164: Complete genome assemblies of two mouse subspecies reveal structural diversity of telomeres and centromeres

Nature Genetics · 2025-10-21 · [DOI](https://doi.org/10.1038/s41588-025-02367-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597820/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Assembly polishing |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Repetitive sequence annotation / Centromere accuracy |
| hifiasm | Haplotype-resolved long-read assembly. | Methods / Genome assembly |
| Integrative Genomics Viewer (IGV) | Visualized Minimap2/SAMtools-processed PacBio HiFi seed alignments in IGV v2.8.13 during iterative pseudoautosomal-region assembly walking and manually selected reads matching the seed over4kb. | Methods / PAR assembly using PacBio walking method |
| MashMap | Mapped unplaced telomere/centromere-bearing contigs to chromosome scaffolds with --pi 95 -f one-to-one and compared cumulative alignment lengths for assignment. | Methods / Assembly curation |
| Merqury | Assessed k-mer completeness of candidate mouse genome assemblies. | Methods / Assembly evaluation |
| minimap2 | Mapped F1 ONT reads to a combined two-T2T reference using minimap2 2.17-r941, converting PAF to BED and bedtools genomecov for centromeric coverage assessment. | Methods / Repetitive sequence annotation / Centromere accuracy |
| PretextMap | Generated Hi-C contact maps for manual chromosome assembly curation. | Methods / Assembly curation |
| PretextView | Visualized the Hi-C contact maps used for manual chromosome assembly curation. | Methods / Assembly curation |
| RepeatMasker | Annotated centromeric repeats in candidate unplaced telomeric contigs to support their placement in missing chromosome regions. | Methods / Assembly curation |
| SAMtools | Alignment manipulation and QC. | Methods / PAR assembly using PacBio walking method |
| seqtk | Detected canonical terminal telomeric repeats with telo to assess assembly ends and locate candidate unplaced telomere-bearing contigs. Also used seqtk in manually supported assembly curation to incorporate missing terminal sequence and introduce 100-bp model gaps. | Methods / Assembly evaluation |
| Sniffles | Long-read structural variant discovery. | Methods / Assembly evaluation |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Gene prediction and annotation |
| SyRI | Assembly-to-assembly structural rearrangement analysis. | Methods / Inversions methods |
| Verkko | Generated the selected base haplotype-separated assemblies before manual chromosome curation. | Methods / Assembly evaluation |
| Winnowmap | Compared chromosome assemblies and required HiFi/ONT reads spanning manual sequence integration boundaries to support assembly curation. | Methods / Assembly evaluation |
| Yak | Ran trioeval against parental Illumina k-mer spectra to estimate haplotype switch and Hamming errors. | Methods / Assembly evaluation |

XML SHA-256: `0a78a7fb735b5d918f9351cd3f354dacb9cdc0e3a00dde991721c385fe79fb97`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P165: Paired mutation calling and spatial transcriptomics identify cellular neighborhoods associated with the neoplastic outcome of mouse colitis

Nature Genetics · 2026-07-28 · [DOI](https://doi.org/10.1038/s41588-026-02673-0) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447109/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DESeq2 | Count-based differential expression analysis. | Methods / Bulk RNA sequencing |
| dndscv | Somatic mutation selection analysis. | Methods / Quantifying selection using dN/dS |
| GSVA | Per-sample gene-set activity scoring. | Methods / GSVA |
| Harmony | Batch integration of cell embeddings. | Methods / Spatial transcriptomics |
| sctransform | Applied SCTransform per Visium FFPE slide after SpaceRanger 3 processing, before Harmony and downstream clustering/markers. | Methods / Spatial transcriptomics |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Spatial transcriptomics |
| Space Ranger | Spatial transcriptomics read processing. | Methods / Spatial transcriptomics |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Spatial transcriptomics |

XML SHA-256: `386edeb2a738e47e95aac498040d22bc7ce1b5ae2c0b6e3f151d81aede695a1b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P166: Antagonism between H3K27me3 and genome–lamina association drives atypical spatial genome organization in the totipotent embryo

Nature Genetics · 2024-09-16 · [DOI](https://doi.org/10.1038/s41588-024-01902-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525175/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Seurat | Used for exploratory representation. | Methods / Single-cell transcription analyses / Single-cell CEL-seq2 UMAP |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Single-cell DamID analyses / Single-cell DamID UMAP |

XML SHA-256: `f36540742dc0ead9e214c87de531fcece84ff1542e3bf6797b8c72146e6bd393`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P167: Multi-modal spatial characterization of tumor immune microenvironments identifies targetable inflammatory niches in diffuse large B cell lymphoma

Nature Genetics · 2025-10-21 · [DOI](https://doi.org/10.1038/s41588-025-02353-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597830/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CellChat | Ligand-receptor cell communication inference. | Methods / CosMx data analysis / Neighborhood-based cell–cell communication analysis |
| CellPhoneDB | Ligand-receptor cell communication analysis. | Methods / CosMx data analysis / Neighborhood-based cell–cell communication analysis |
| Harmony | Batch integration of cell embeddings. | Methods / CosMx data analysis / Data quality control and processing |
| Leiden | Grouped observations into communities using a neighborhood graph. | Methods / CODEX data analysis / Cell typing |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / CosMx data analysis / Data quality control and processing |
| sctransform | Used Seurat 5.0.1 SCTransform and PCA on CosMx data before Harmony and Louvain; the dataset reports very sparse profiles. | Methods / CosMx data analysis / Data quality control and processing |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / CosMx data analysis / Data quality control and processing |

XML SHA-256: `a82e0b2515908450887efb66565192b57b0c193f2ccde7217ade8d11e6f8e904`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P168: Nanoparticle-enriched mass spectrometry proteomics in British South Asians identifies links between genetic variants, plasma protein levels and disease risk

Nature Genetics · 2026-07-24 · [DOI](https://doi.org/10.1038/s41588-026-02697-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447103/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Methods / Whole-exome sequencing (WES) |
| DIA-NN | Data-independent acquisition proteomic quantification. | Methods / Proteomic profiling / Seer Proteograph |
| Exome Germline Single Sample | Used the Exome Germline Single Sample 3.0.0 pipeline to preprocess reads and produce per-individual exome gVCFs/CRAMs with Picard, GATK HaplotypeCaller and SAMtools. | Methods / Whole-exome sequencing (WES) |
| GATK | Generated single-individual exome gVCFs with GATK 4.2.2.0 HaplotypeCaller in Exome Germline Single Sample 3.0.0. The pipeline omitted chrY/MT and called chrX diploid in both sexes. | Methods / Whole-exome sequencing (WES) |
| Hail | Distributed genotype QC and association. | Methods / Whole-exome sequencing (WES) |
| KING | Genetic kinship and relatedness estimation. | Methods / Genotyping and imputation |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Proteomic profiling / Seer Proteograph |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Whole-exome sequencing (WES) |
| REGENIE | Ran REGENIE 4.0 pQTL GWAS for 5768 protein groups using separate imputed/WES step-2 sets. Step 1 used common high-coverage genotyped or sequenced markers with stated LD pruning, and sex/age/ancestry/20-PC covariates. | Methods / Genome-wide association analysis |
| SAMtools | Alignment manipulation and QC. | Methods / Whole-exome sequencing (WES) |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Integrated statistical fine-mapping and novelty assignment |
| Ensembl VEP | Variant consequence annotation. | Methods / Statistical analysis |

XML SHA-256: `e68251d7b700b83479ee28f992b07c396ca42e800acfa35436035248b1ffc149`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P169: Systematic prioritization of functional variants and effector genes underlying colorectal cancer risk

Nature Genetics · 2024-09-16 · [DOI](https://doi.org/10.1038/s41588-024-01900-w) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525171/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Methods / MPRAs / Data analyses |
| cooltools | Chromosome conformation analysis. | Methods / Micro-C |
| Enrichr | Gene-set enrichment. | Methods / Gene evidence |
| MAGMA | Used MAGMA1.10 to define the top1000 CRC disease-associated genes from GWAS summary statistics for scDRS1.0.1 analyses of Tabula Sapiens and Gut Cell Atlas profiles. | Methods / Cell-type specificity of risk variants |
| Matrix eQTL | Matrix-based QTL association. | Methods / eQTLs and SMR |
| Nextflow | Analysis pipeline orchestration. | Methods / ChIPmentation |
| nf-core | Reusable sequencing analysis pipelines. | Methods / ChIPmentation |
| PolyFun | Functional-prior fine-mapping. | Methods / Fine-mapping of risk loci |
| Presto | Fast single-cell marker testing. | Methods / Gene evidence |
| SMR | Summary-data molecular association or Mendelian randomization. | Methods / eQTLs and SMR |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Fine-mapping of risk loci |

XML SHA-256: `35e6eef968caa63cd301d6867f6353d88441df9bd960be9d32288a4e86561782`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P170: Modeling heterogeneity in single-cell perturbation states enhances detection of response eQTLs

Nature Genetics · 2025-10-20 · [DOI](https://doi.org/10.1038/s41588-025-02344-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597823/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization analysis |
| FastQTL | Permutation-based molecular QTL mapping. | Methods / Prioritizing eQTLs in PBMCs bulk expression profiles |
| fgsea | Computed gene–perturbation-score Pearson correlations among 3,000 variable genes, retained genes with correlation Q below 0.05, and ran fgsea on their correlation ranks with set sizes 15–500 and 100,000 permutations using Hallmark and C7 resources. | Methods / Gene pathway enrichment analysis |
| Harmony | Batch integration of cell embeddings. | Methods / Single-cell mRNA quality control |
| PEER | Latent confounder estimation. | Methods / Prioritizing eQTLs in PBMCs bulk expression profiles |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Genotype quality control |
| PLINK | Used for genetic principal components. | Methods / Genotype quality control |

XML SHA-256: `ea3565b3d487b40dbbd57675e2825b39fda6b485cba722cd505701adc18d3739`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P171: A pangenome of tetraploid wheat reveals the genetic architecture underlying domestication and genomic diversity for breeding

Nature Genetics · 2026-07-22 · [DOI](https://doi.org/10.1038/s41588-026-02678-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447107/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Phylogenetic tree and population structure analysis |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Graph-based pangenome construction |
| Beagle | Imputed the filtered wheat VCFs using the reported Beagle 5.4 defaults after retaining biallelic SVs and excluding variants with more than 20 percent missing calls. | Methods / Variant calling and filtering |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Genomic selective-sweeping analysis |
| Bowtie 2 | Short-read alignment. | Methods / Centromeric region identification |
| BWA-MEM2 | Mapped cleaned whole-genome reads from new and published accessions to the Kronos genome using BWA-MEM2 2.3 defaults before LUSH-DNASeq SNP/short-indel genotyping and biallelic quality filtering. | Methods / Variant calling and filtering |
| deepTools | Converted filtered CENH3 ChIP-seq BAMs to normalized bigWig with deepTools3.5.5 defaults for visualization. | Methods / Centromeric region identification |
| DIAMOND | Protein sequence alignment. | Methods / Genome annotation |
| GCTA | Used for genetic principal components. | Methods / Phylogenetic tree and population structure analysis |
| GEMMA | Mixed-model association or variance components. | Methods / Genome-wide association studies |
| hifiasm | Haplotype-resolved long-read assembly. | Methods / Genome assembly / Step 1. Contig construction |
| InterProScan | Protein domain and functional annotation. | Methods / Genome annotation |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Pangenome analysis |
| LDAK | SNP heritability and polygenic modelling. | Methods / Heritability estimation |
| LUSH-DNASeq-pipeline | Genotyped SNPs and indels shorter than 50 bp with LUSH-DNASeq-pipeline 2.2.0 after BWA-MEM2 mapping, then kept biallelic variants and applied separate SNP/indel annotation thresholds. | Methods / Variant calling and filtering |
| MACS | Called CENH3 ChIP peaks with MACS3 3.0.1 using input controls and BAMPE after accession-specific alignment/filters; merged adjacent enriched regions within the reported 1000-kb distance to identify potential centromeres. | Methods / Centromeric region identification |
| MAFFT | Multiple sequence alignment. | Methods / Phylogenetic tree construction and divergence time estimation |
| Merqury | Assembly k-mer completeness assessment. | Methods / Assembled genome evaluation |
| minimap2 | Mapped ONT reads to 14 wheat chromosomes using minimap2 2.24 for chromosome-wise NextDenovo assembly, and aligned contigs to organelle references with -cx asm20. | Methods / Genome assembly / Step 1. Contig construction |
| pbmm2 | PacBio read alignment. | Methods / Genome annotation |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Methods / Graph-based pangenome construction |
| RepeatMasker | Repetitive sequence annotation. | Methods / Repeat annotation |
| SAMtools | Alignment manipulation and QC. | Methods / Centromeric region identification |
| SnpEff | Variant consequence annotation. | Methods / Variant calling and filtering |
| StainedGlass | Confirmed tandem-repeat structure in candidate centromere regions using StainedGlass 0.6 with window size 10000. | Methods / Centromeric region identification |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Genome annotation |
| StringTie | Transcript assembly and quantification. | Methods / Genome annotation |
| Trimmomatic | Used Trimmomatic0.39 on CENH3 ChIP-seq in the literal reported order ILLUMINACLIP:adapter.fa:2:30:10, MINLEN36, SLIDINGWINDOW4:20, before Bowtie2 and MACS3 BAMPE peaks. | Methods / Centromeric region identification |
| Verkko | Genome assembly. | Methods / Genome assembly / Step 1. Contig construction |

XML SHA-256: `ff3ca782d6de1cb39d389e91226f83dec87f7b14265fc75e264318f3724193fa`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P172: A recurrent de novo MAX p.Arg60Gln variant causes a syndromic overgrowth disorder through differential expression of c-Myc target genes

American Journal of Human Genetics · 2023-12-22 · [DOI](https://doi.org/10.1016/j.ajhg.2023.11.010) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10806738/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cutadapt | Adapter trimming. | Subjects and methods / mRNA sequencing |
| DESeq2 | Count-based differential expression analysis. | Subjects and methods / mRNA sequencing |
| FastQC | Sequencing read quality assessment. | Subjects and methods / mRNA sequencing |
| PyMOL | Used explicitly open-source PyMOL v1.7.6.0 for molecular modelling/rendering of a c-Myc/Max heterodimer,choosing a Gln60 rotamer and manually setting K392 chi1-chi4 to gauche+,trans,gauche-,trans. | Subjects and methods / Molecular modeling |
| Rsubread | R interface for read mapping, feature/junction counting and variant analysis. | Subjects and methods / mRNA sequencing |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Subjects and methods / mRNA sequencing |

XML SHA-256: `7b28cbb7dd78fa4950da9634c7c42ba8548c7580c229037c06f752c9b0b1431c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P173: DNA methylation-based predictors of metabolic traits in Scottish and Singaporean cohorts

American Journal of Human Genetics · 2024-12-19 · [DOI](https://doi.org/10.1016/j.ajhg.2024.11.012) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11739919/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| minfi | Methylation-array preprocessing. | Methods / DNAm in the LBC1936 |

XML SHA-256: `d3ad7541e92e9533104ca41da682f538b83ed875da92748104b80845c6dd6650`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P174: De novo variants in KDM2A cause a syndromic neurodevelopmental disorder

American Journal of Human Genetics · 2025-12-29 · [DOI](https://doi.org/10.1016/j.ajhg.2025.12.004) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12824617/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Subjects and methods / Methylome analysis and episignature |
| CADD | Variant deleteriousness annotation. | Subjects and methods / In silico prediction |
| Cutadapt | Adapter trimming. | Subjects and methods / Methylome analysis and episignature |
| FastQC | Sequencing read quality assessment. | Subjects and methods / Methylome analysis and episignature |
| Picard | Alignment QC, duplicates and sequence processing. | Subjects and methods / Methylome analysis and episignature |
| REVEL | Missense variant effect prediction. | Subjects and methods / In silico prediction |

XML SHA-256: `3297efcf4433f0becf763eb048427d5928cc3c7d18aef51e55d7d9c5318f3a67`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P175: Allele frequency trajectories across age groups reveal ongoing natural selection shaping disease susceptibility

American Journal of Human Genetics · 2026-07-24 · [DOI](https://doi.org/10.1016/j.ajhg.2026.07.002) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13504339/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CADD | Variant deleteriousness annotation. | Material and methods / Annotation of candidate variants and identification of causal variants through LD analyses, VEP, and GTEx |
| PLINK | Used for genotype quality control. | Material and methods / Quality controls for the whole-genome genotyping data |
| Ensembl VEP | Variant consequence annotation. | Material and methods / Annotation of candidate variants and identification of causal variants through LD analyses, VEP, and GTEx |

XML SHA-256: `cccf77ab19ccef0bb718ca6f955d485a32047edf9a44dfcf5142029406b992bf`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P176: MSH3 is a genetic modifier of somatic repeat instability in X-linked dystonia parkinsonism

American Journal of Human Genetics · 2025-12-23 · [DOI](https://doi.org/10.1016/j.ajhg.2025.12.002) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12824618/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DESeq2 | Count-based differential expression analysis. | Subjects, material, and methods / RNA-sequencing analysis |
| GATK | Variant discovery and recalibration. | Subjects, material, and methods / Whole-exome sequencing |
| GEMMA | Mixed-model association or variance components. | Subjects, material, and methods / Single-variant analysis |
| Picard | Alignment QC, duplicates and sequence processing. | Subjects, material, and methods / Whole-exome sequencing |
| PLINK | Used Plink v.1.9.0 to QC previously generated genome-wide SNV genotypes in 163 samples: --geno<=0.02, --mind<=0.02, MAF>=0.01 and a heterozygosity deviation rule. The retained variants underwent a separate GEMMA mixed-model GWAS. | Subjects, material, and methods / Genome-wide association analysis |
| PLINK 2 | Used Plink v.2.0.0 to subset and QC exome SNVs. Common-variant QC reports --mind<=0.1, --geno<=0.05, HWE=1e-6, MAF>=0.01 and a heterozygosity deviation rule. Rare-SNV QC uses the same missingness limits and MAF<0.01 before separate gene-based analysis. | Subjects, material, and methods / Whole-exome sequencing |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Subjects, material, and methods / Gene-based association analysis |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Subjects, material, and methods / RNA-sequencing analysis |
| sva | Latent confounder adjustment. | Subjects, material, and methods / RNA-sequencing analysis |
| Ensembl VEP | Variant consequence annotation. | Subjects, material, and methods / Whole-exome sequencing |

XML SHA-256: `3377c3c179d057758dcc338f4b16cd7a5828a4ba477f6ce4abc75b05cfed0485`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P177: TEMR: Trans-ethnic mendelian randomization method using large-scale GWAS summary datasets

American Journal of Human Genetics · 2024-12-16 · [DOI](https://doi.org/10.1016/j.ajhg.2024.11.006) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11739928/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| TEMR | Trans-ethnic Mendelian randomization. | Methods / TEMR model based on two ancestries |

XML SHA-256: `b0d9680e5ed48669d81de7e87bb898612252ae743d41a26daa2d437120541c4a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P178: Likelihood-based calibration improves the clinical utility of JAG1 functional data for variant classification

American Journal of Human Genetics · 2026-07-13 · [DOI](https://doi.org/10.1016/j.ajhg.2026.06.016) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13439524/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Material and methods / Structural and stability analysis |
| PyMOL | Aligned AlphaFold2 wild-type/mutant JAG1 residues31-333 in PyMOL v2.5.4 and quantified local C-alpha RMSD within plus/minus15 residues of each variant. | Material and methods / Structural and stability analysis |

XML SHA-256: `9c850af192a4673f8089b27292690d3c650c0dd1d8318f7e619d70d4b2f11894`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P179: Exonic trinucleotide repeat expansions in ZFHX3 cause spinocerebellar ataxia type 4: A poly-glycine disease

American Journal of Human Genetics · 2023-11-29 · [DOI](https://doi.org/10.1016/j.ajhg.2023.11.008) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10806739/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Beagle | Phased the two family VCF datasets with the 1000 Genomes reference panel before a separate hap-ibd IBD analysis. | Subjects and methods / Genetic analysis |
| ExpansionHunter | Repeat expansion genotyping. | Subjects and methods / Screening of larger datasets |
| GATK | Variant discovery and recalibration. | Subjects and methods / Genetic analysis |
| hap-ibd | Identity-by-descent segment detection. | Subjects and methods / Genetic analysis |
| Integrative Genomics Viewer (IGV) | Manually curated CNVs in Integrative Genomics Viewer v2.12.2 after GATK calling and comparison with genomic segments shared by affected relatives. | Subjects and methods / Genetic analysis |
| Ensembl VEP | Variant consequence annotation. | Subjects and methods / Genetic analysis |

XML SHA-256: `4a20c1a75c582c62907aac8dfb24fc725ae94d099bbae2ecb5461b450e27487e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P180: Demographic history and genetic variation of the Armenian population

American Journal of Human Genetics · 2024-11-25 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.022) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11739871/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Material and methods / Merging with ancient and modern reference datasets |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Material and methods / Variant annotation |
| BWA | DNA read alignment. | Material and methods / Data processing for population genomics analyses with WGS samples |
| GATK | Variant discovery and recalibration. | Material and methods / Data processing for population genomics analyses with WGS samples |
| IMPUTE | Genotype imputation. | Material and methods / Imputation |
| KING | Genetic kinship and relatedness estimation. | Material and methods / Data processing for population genomics analyses with WGS samples |
| Picard | Alignment QC, duplicates and sequence processing. | Material and methods / Data processing for population genomics analyses with WGS samples |
| PLINK | Used for genotype quality control. | Material and methods / Population genomic analyses |
| SAMtools | Alignment manipulation and QC. | Material and methods / Data processing for population genomics analyses with WGS samples |
| SHAPEIT | Genotype haplotype phasing. | Material and methods / Population genomic analyses |
| smartpca | Genetic ancestry principal components. | Material and methods / Population genomic analyses |
| Ensembl VEP | Variant consequence annotation. | Material and methods / Variant annotation |

XML SHA-256: `da52d87762688261114ecbab2ed96ddd482f7cf2c6ec4b1360e0df9f2051eb8a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P181: Data-driven RNA phenotyping captures genetically regulated dimensions of the transcriptome

American Journal of Human Genetics · 2026-07-01 · [DOI](https://doi.org/10.1016/j.ajhg.2026.06.005) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13361010/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Methods / TWAS |
| deepTools | Converted STAR2.7.11b Geuvadis RNA-seq BAMs to bigWig with deepTools bamCoverage using binSize1; other cohorts reused downloaded recount3 bigWigs. | Methods / RNA-seq datasets |
| eCAVIAR | Fine-mapping and colocalization. | Methods / Colocalization between xQTLs and FinnGen GWAS |
| FUSION | Transcriptome-wide association and conditional follow-up. | Methods / TWAS |
| LaDDR | Ran LaDDR again on each truncated/subsampled read dataset before mapping cis-QTLs with tensorQTL, using the same main-pipeline procedure. | Methods / Read-length and -depth simulations |
| MAJIQ | Splicing quantification and differential analysis. | Methods / Intron retention quantification using MAJIQ |
| pyBigWig | Summarized base-level coverage into per-bin sample means with pyBigWig, then depth-scaled relative to the median sample, added pseudocount8 by default and log2-transformed for LaDDR phenotypes. | Methods / LaDDR phenotyping |
| seqtk | Used trimfq to truncate paired 75-bp RNA-seq reads to 50 bp, then sample to create 50% and 25% depth datasets with a fixed seed shared across mates before rerunning alignment, coverage analysis, LaDDR and tensorQTL. | Methods / Read-length and -depth simulations |
| Snakemake | Analysis workflow execution. | Methods / Method and parameter comparisons |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA-seq datasets |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Functional enrichment analysis of fine-mapped xQTLs |
| tensorQTL | Molecular QTL mapping. | Methods / xQTL mapping |

XML SHA-256: `1bf57e8fc7856dd285a55fcae868bd9496d7940ada31f88b80a86897fe0af37b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P182: Primary cartilage transcriptional signatures reflect cell-type-specific molecular pathways underpinning osteoarthritis

American Journal of Human Genetics · 2024-11-22 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.019) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639091/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Processed fetal, pediatric and organoid FASTQs with CellRanger v3.0.2 using the human reference transcriptome reported as GRCh38-1.2.0. | Methods / scRNA-seq of fetal tissues and preprocessing |
| clusterProfiler | Performed separate up/down DEG overrepresentation analyses, naming enrichr and enrichGO from ClusterProfiler, with all QC-detected genes as background, set sizes 10–1,500 and FDR-adjusted P below 0.05. | Methods / Enrichment analyses |
| dupRadar | RNA-seq duplication and expression-dependent duplicate QC. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| edgeR | Count-based differential expression analysis. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| Enrichr | Gene-set enrichment. | Methods / Enrichment analyses |
| featureCounts | Counted genes/biotypes using featureCounts in Subread 2.0.3 after STAR 2.7.9a alignment with the stated GRCh38/Ensembl 105 reference. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| fgsea | Performed fgsea on differential-expression genes ranked by log2 fold-change effect size, using Reactome/Hallmark resources, set sizes 10–1,500 and FDR-adjusted P below 0.05. | Methods / Enrichment analyses |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Methods / Functional characterization of co-expression modules |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Power estimation of differential gene expression between osteoarthritis cartilage tissue grades |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / scRNA-seq of fetal tissues and preprocessing |
| MAGMA | Used MAGMA1.10 gene-set enrichment of coexpression modules against osteoarthritis GWAS,with GRCh37 and35kb-upstream/10kb-downstream windows; applied FDR across modules and traits. | Methods / Functional characterization of co-expression modules |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| RSEM | Transcript abundance estimation. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| RSeQC | RNA-seq quality metrics. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| Scanpy | Used for marker or differential-expression analysis; cell annotation; exploratory representation. | Methods / scRNA-seq data from osteoarthritis cartilage |
| Scrublet | Ran Scrublet separately per sequencing run after CellRanger 3.0.2 and SoupX 1.3, before Seurat 3 analysis. | Methods / scRNA-seq of fetal tissues and preprocessing |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / scRNA-seq of fetal tissues and preprocessing |
| Snakemake | Analysis workflow execution. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| SoupX | Ambient RNA contamination correction. | Methods / scRNA-seq of fetal tissues and preprocessing |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| Subread suite | Named Subread suite or explicit Subread alignment/index program; distinguish counting-library attribution. | Methods / Bulk RNA-seq of osteoarthritis cartilage samples and preprocessing |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Causal inference analysis |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / scRNA-seq data from osteoarthritis cartilage |
| voom | Mean-variance weights for expression linear models. | Methods / Power estimation of differential gene expression between osteoarthritis cartilage tissue grades |
| WGCNA | Weighted coexpression networks and modules. | Methods / Construction of weighted gene co-expression network and module identification in osteoarthritis cartilage |

XML SHA-256: `a341dce0f9805f735191516ad0178a8a44ec98248dba13a9941f0a2a8178224e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P183: Genetic control of non-coding RNAs in the human brain and their implications for complex traits

American Journal of Human Genetics · 2025-12-16 · [DOI](https://doi.org/10.1016/j.ajhg.2025.11.012) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12824624/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Material and methods / Identification and quantification of circRNAs in the 10 primary brain RNA-seq datasets |
| BWA | DNA read alignment. | Material and methods / Identification and quantification of circRNAs in the 10 primary brain RNA-seq datasets |
| CIRI2 | Used CIRI2-reported back-splice junction counts for circRNA quantification in the single-end MSBB cohort. | Material and methods / Identification and quantification of circRNAs in the 10 primary brain RNA-seq datasets |
| CIRIquant | Constructed pseudo-circular circRNA references and realigned RNA-seq through HISAT2 for CIRIquant circRNA expression and circular-to-linear ratios. | Material and methods / Identification and quantification of circRNAs in the 10 primary brain RNA-seq datasets |
| coloc | Shared-signal colocalization analysis. | Material and methods / Integrative analysis of GWAS and eQTL data |
| Cutadapt | Adapter trimming. | Material and methods / Identification and quantification of lncRNAs in the 10 primary brain RNA-seq datasets |
| DESeq2 | Count-based differential expression analysis. | Material and methods / Differential gene expression analysis |
| edgeR | Count-based differential expression analysis. | Material and methods / eQTL mapping analysis |
| FastQC | Sequencing read quality assessment. | Material and methods / Identification and quantification of lncRNAs in the 10 primary brain RNA-seq datasets |
| FUSION | Transcriptome-wide association and conditional follow-up. | Material and methods / Integrative analysis of GWAS and eQTL data |
| HEIDI | Heterogeneity test distinguishing pleiotropy from linkage. | Material and methods / Integrative analysis of GWAS and eQTL data |
| HISAT2 | Used HISAT2 through CIRIquant to align RNA-seq to pseudo-circular circRNA references for junction-based circRNA quantification. | Material and methods / Identification and quantification of circRNAs in the 10 primary brain RNA-seq datasets |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Material and methods / Heritability enrichment analysis and mediated heritability estimation for lncRNAs and circRNAs |
| PEER | Latent confounder estimation. | Material and methods / eQTL mapping analysis |
| PLINK | Used for LD-based variant selection; genetic association analysis. | Results / Functional and heritability enrichment of lncRNA and circRNA-eQTLs |
| SMR | Summary-data molecular association or Mendelian randomization. | Material and methods / Integrative analysis of GWAS and eQTL data |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Material and methods / Identification and quantification of lncRNAs in the 10 primary brain RNA-seq datasets |
| StringTie | Transcript assembly and quantification. | Material and methods / Identification and quantification of lncRNAs in the 10 primary brain RNA-seq datasets |
| tensorQTL | Molecular QTL mapping. | Material and methods / eQTL mapping analysis |
| voom | Mean-variance weights for expression linear models. | Material and methods / eQTL mapping analysis |

XML SHA-256: `f712738d2996e5a02826e4d939365bf1889c12ba08f9f65621cecc07798abedc`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P184: A transparent and generalizable deep-learning framework for genomic ancestry prediction

American Journal of Human Genetics · 2026-07-01 · [DOI](https://doi.org/10.1016/j.ajhg.2026.06.003) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13504326/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| PLINK | Used for genotype quality control. | Methods / Data processing / Construction of a harmonized SNP set |
| RFMix | Local ancestry inference. | Methods / Data processing / MHIB dataset |
| SHAPEIT | Genotype haplotype phasing. | Methods / Data processing / Construction of a harmonized SNP set |

XML SHA-256: `d39ae77884b8c3c248835c92978263f3e618aef2396e982a6595447a57b75998`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P185: The methylomic landscape of human articular cartilage development contains epigenetic signatures of osteoarthritis risk

American Journal of Human Genetics · 2024-11-22 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.017) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639090/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Differential methylation and differential methylated region analyses |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization analysis |
| limma | Linear modelling and empirical-Bayes differential analysis. | Methods / Differential methylation and differential methylated region analyses |
| Matrix eQTL | Matrix-based QTL association. | Methods / mQTL analysis |
| minfi | Methylation-array preprocessing. | Methods / Generation of genotype and methylation data |
| sva | Latent confounder adjustment. | Methods / Differential methylation and differential methylated region analyses |

XML SHA-256: `48dbc40e346a8d65e1a814b418469c3c669ec7721c44428623df05ba82d85848`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P186: Inherited infertility: Mapping loci associated with impaired female reproduction

American Journal of Human Genetics · 2024-11-19 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.018) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639076/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Beagle | Imputed Eagle 2.3.5-prephased genotypes with the SISu 4.2 reference panel using the explicitly reported Beagle 08Jun17.d8b build. | Subjects and methods / Genotyping and imputation |
| Eagle | Genotype phasing. | Subjects and methods / Genotyping and imputation |
| GATK | Variant discovery and recalibration. | Subjects and methods / Genotyping and imputation |
| Hail | Distributed genotype QC and association. | Subjects and methods / Genotyping and imputation |
| LDSC | Used for SNP heritability estimation; cross-trait genetic correlation. | Subjects and methods / Genetic correlations and disease enrichment |
| REGENIE | Ran REGENIE 2.2.4 additive and recessive GWAS on imputed genotypes in FinnGen women, adjusting for follow-up/death age, birth year, batch and ten PCs. Recessive tests required at least two minor-allele homozygotes. | Subjects and methods / GWAS |
| SuSiE | Bayesian fine-mapping and credible sets. | Subjects and methods / GWAS |

XML SHA-256: `0d895551794d073cddbc9ea40cac2c5e1778ca28df63e5272f3ef06651c3ea64`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P187: Liver single-nucleus multiome profiling reveals cell-type mechanisms for cardiometabolic traits

American Journal of Human Genetics · 2025-12-02 · [DOI](https://doi.org/10.1016/j.ajhg.2025.11.009) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12805840/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ataqv | Computed pseudobulk ATAC quality metrics including TSS enrichment and the percentage of reads overlapping peaks using ataqv 1.0.0. | Material and methods / Cell-type accessible chromatin regions |
| Azimuth | Reference mapping and cell annotation. | Material and methods / Nuclei filtering and cell-type annotation |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Material and methods / Genotyping and imputation |
| BEDTools | Genomic interval manipulation and overlap. | Material and methods / Identification of singlet droplets and donor deconvolution |
| BWA | DNA read alignment. | Material and methods / Mapping caQTL |
| Cell Ranger | Aligned multiome reads with CellRanger-ARC count v2 against GRCh38-2020-A with filtered GENCODE32 annotations, supplying BAMs, barcodes and reference annotations for later donor/cell-type analyses. | Material and methods / 10× multiome assays |
| demuxlet | Genotype-based cell assignment and doublet identification. | Material and methods / Identification of singlet droplets and donor deconvolution |
| DESeq2 | Count-based differential expression analysis. | Material and methods / Cell-type marker ATAC-seq peaks |
| FastQTL | Permutation-based molecular QTL mapping. | Material and methods / Mapping caQTL |
| featureCounts | Counted ATAC pseudobulk BAM overlaps with merged cell-type peaks using featureCounts, before EDASeq GC correction and DESeq2 size-factor/marker analyses. | Material and methods / Cell-type marker ATAC-seq peaks |
| GREAT | Regulatory-region functional enrichment. | Material and methods / Cell-type marker ATAC-seq peaks |
| Harmony | Batch integration of cell embeddings. | Material and methods / Nuclei filtering and cell-type annotation |
| HOMER | Motif and regulatory enrichment analysis. | Material and methods / Cell-type marker ATAC-seq peaks |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Material and methods / Heritability enrichment in cell-type peaks |
| Louvain | Grouped observations into communities using a neighborhood graph. | Material and methods / Nuclei filtering and cell-type annotation |
| MACS | Called cell-type ATAC peaks with the dated MACS2 2016-02-15 release on merged donor pseudobulk BED reads using no model, shift -100, extension 200, q-value 0.05 and keep-all duplicates. The paper explicitly retained same-location reads from different nuclei after prior nucleus-level deduplication. | Material and methods / Cell-type accessible chromatin regions |
| Picard | Alignment QC, duplicates and sequence processing. | Material and methods / Identification of singlet droplets and donor deconvolution |
| SAMtools | Alignment manipulation and QC. | Material and methods / Identification of singlet droplets and donor deconvolution |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Material and methods / Nuclei filtering and cell-type annotation |
| Signac | Single-cell chromatin accessibility analysis. | Material and methods / Nuclei filtering and cell-type annotation |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Material and methods / Identification of singlet droplets and donor deconvolution |
| UCSC liftOver | Converted cell-type ATAC-peak coordinates to GRCh37 before a seven-annotation LDSC analysis with GRCh37 1000G/HapMap references. | Material and methods / Heritability enrichment in cell-type peaks |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Material and methods / Nuclei filtering and cell-type annotation |
| WASP | Allele-swapped read remapping for allelic mapping-bias filtering. | Material and methods / Mapping caQTL |

XML SHA-256: `7dbc798c9834346d9395498d85b3c5d4885248e7f15eb34d3c77ccc41fe234e5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P188: Bi-allelic PRMT9 loss-of-function variants cause a syndromic form of intellectual disability

American Journal of Human Genetics · 2025-11-18 · [DOI](https://doi.org/10.1016/j.ajhg.2025.10.014) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808985/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| PyMOL | Prepared images of the determined apo-PRMT9 structure in PyMOL. | Subjects and methods / PRMT9 production, purification, crystallization, and structural determination |

XML SHA-256: `9a8ca2fb7bc8e9d675bb1200f1ed5e2c00a6c3101e4b5ce094b6fab522b9ac51`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P189: Proteome-wide Mendelian randomization and functional studies uncover therapeutic targets for polycystic ovarian syndrome

American Journal of Human Genetics · 2024-11-13 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.008) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639085/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| clusterProfiler | Tested GO and KEGG functional enrichment of primary-MR PCOS therapeutic targets with nominal MR P below 0.05, using Fisher exact tests and FDR below 0.05. | Material and methods / Functional enrichment analysis for therapeutic targets for PCOS |
| coloc | Shared-signal colocalization analysis. | Material and methods / Bayesian colocalization analysis |
| HEIDI | Heterogeneity test distinguishing pleiotropy from linkage. | Material and methods / SMR and heterogeneity in dependent instruments tests |
| MR-PRESSO | MR pleiotropy/outlier sensitivity. | Material and methods / Genetic instrument selection and primary mendelian randomization analysis |
| MungeSumstats | GWAS summary-statistic formatting, QC and genome-build harmonization. | Material and methods / Genetic instrument selection and primary mendelian randomization analysis |
| SeSAMe | Methylation-array preprocessing. | Material and methods / Experimental animal care and ethics |
| SMR | Summary-data molecular association or Mendelian randomization. | Material and methods / SMR and heterogeneity in dependent instruments tests |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Material and methods / Genetic instrument selection and primary mendelian randomization analysis |

XML SHA-256: `6fe50057926dacbc242d400e391db7d721751ba8d706e5c00a5e9cb668bdb723`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P190: A deep dive into statistical modeling of RNA splicing QTLs reveals variants that explain neurodegenerative disease

American Journal of Human Genetics · 2025-11-12 · [DOI](https://doi.org/10.1016/j.ajhg.2025.10.012) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12626415/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Results / Analysis of GTEx tissues points to potential improvement for sQTL modeling |
| coloc | Used COLOC to compare MAJIQTL-derived splicing QTLs with Alzheimer and Parkinson GWAS summary statistics and retained colocalized signals at PPH4>0.5. | Results / MAJIQTL improves sQTL enrichment in functional annotation and identifies sQTLs that explain the GWAS signal for neurodegenerative disorders |
| LeafCutter | Intron excision quantification and splicing analysis. | Results / Analysis of GTEx tissues points to potential improvement for sQTL modeling |
| MAJIQ | Splicing quantification and differential analysis. | Results / Analysis of GTEx tissues points to potential improvement for sQTL modeling |
| QTLtools | Molecular QTL mapping and QC. | Results / Analysis of GTEx tissues points to potential improvement for sQTL modeling |
| rMATS | Differential alternative splicing. | Results / MAJIQTL offers a robust and powerful statistical framework for sQTL discovery and analysis |
| Salmon | Transcript abundance quantification. | Results / Analysis of GTEx tissues points to potential improvement for sQTL modeling |
| SpliceAI | Defined splice-site-disrupting sQTL annotations using SpliceAI delta scores>0.2 at the relevant 5-prime or 3-prime junction site, then compared their enrichment among discovered sQTLs. | Results / MAJIQTL improves sQTL enrichment in functional annotation and identifies sQTLs that explain the GWAS signal for neurodegenerative disorders |

XML SHA-256: `44f6ab09c1f38e8f26fa9d8f37c3c88fde3a61efa85113a67259d2bb9927fb71`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P191: Integrating social determinants of health and genetic risk in disease risk models

American Journal of Human Genetics · 2026-06-22 · [DOI](https://doi.org/10.1016/j.ajhg.2026.05.014) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13384252/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Hail | Distributed genotype QC and association. | Methods / Genetic data analysis and genome-wide association study / PGS calculation |
| KING | Genetic kinship and relatedness estimation. | Methods / Genetic data analysis and genome-wide association study / PCA and genetic relationship matrix generation |
| METAL | GWAS meta-analysis. | Methods / Genetic data analysis and genome-wide association study / GWAS |
| PLINK | Used for genotype quality control; LD-based variant selection. | Methods / Genetic data analysis and genome-wide association study / PCA and genetic relationship matrix generation |
| PLINK 2 | Used for genetic principal components. | Methods / Genetic data analysis and genome-wide association study / PCA and genetic relationship matrix generation |
| SAIGE | Used SAIGE1.3.6 to build ancestry-specific sparse GRMs and run two-stage binary GWAS on BGEN srWGS data;single-variant tests required MAC>=200 and Firth effect correction at P<=0.05. | Methods / Genetic data analysis and genome-wide association study / GWAS |
| SHAP | Model feature attribution. | Methods / Risk prediction / Shapley Additive Explanations values |

XML SHA-256: `bbebe3e62f8b9224f40e8de19d2bd1d63234fcaabf742de36a379b05a0750db2`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P192: An abdominal obesity missense variant in the adipocyte thermogenesis gene TBX15 is implicated in adaptation to cold in Finns

American Journal of Human Genetics · 2024-11-01 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.001) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568758/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CADD | Reported a database-derived value of 25.7 for rs10494217, labeled CADD-SV in the source, as one component of TBX15 variant interpretation. | Results / Identification of an abdominal obesity GWAS missense variant within a unique adipocyte marker gene |
| CELLECT | Cell-type enrichment of GWAS. | Methods / Prioritizing adipose cell types based on enriched heritability of abdominal obesity |
| CELLEX | Cell expression specificity estimation. | Methods / Prioritizing adipose cell types based on enriched heritability of abdominal obesity |
| demuxlet | Genotype-based cell assignment and doublet identification. | Methods / Processing of SAT snRNA-seq data in KOBS |
| DoubletFinder | Single-cell doublet detection. | Methods / Processing of SAT snRNA-seq data in RYSA |
| Eagle | Genotype phasing. | Methods / Genotype quality control and imputation in KOBS, RYSA, and METSIM |
| FastQC | Sequencing read quality assessment. | Methods / Processing of SAT snRNA-seq data in RYSA |
| featureCounts | Generated gene counts using featureCounts for paired 75-bp SAT RNA-seq from BMI-discordant monozygotic twins after STAR two-pass mapping to hg38/Ensembl 104. | Methods / SAT bulk RNA-seq data from the BMI-discordant MZ twin cohort |
| Harmony | Batch integration of cell embeddings. | Methods / Processing of SAT snRNA-seq data in RYSA |
| KING | Genetic kinship and relatedness estimation. | Methods / Study cohorts / Metabolic syndrome in men cohort |
| LDSC | Used for SNP heritability estimation; annotation-stratified genetic enrichment. | Methods / Prioritizing adipose cell types based on enriched heritability of abdominal obesity |
| Louvain | Grouped observations into communities using a neighborhood graph. | Methods / Processing of SAT snRNA-seq data in KOBS |
| Minimac | Genotype imputation. | Methods / Genotype quality control and imputation in KOBS, RYSA, and METSIM |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / SAT bulk RNA-seq data from the BMI-discordant MZ twin cohort |
| PLINK | Used for genotype quality control. | Methods / Genotype quality control and imputation in KOBS, RYSA, and METSIM |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / Processing of SAT snRNA-seq data in KOBS |
| SIFT | Read SIFT deleteriousness predictions for rs10494217 through the VEP-based transcript interpretation, including a low-confidence prediction for one transcript. | Results / Identification of an abdominal obesity GWAS missense variant within a unique adipocyte marker gene |
| SingleR | Reference-based cell annotation. | Methods / Processing of SAT snRNA-seq data in KOBS |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Processing of SAT snRNA-seq data in KOBS |
| Ensembl VEP | Used VEP to inspect predicted consequences of TBX15 missense variant rs10494217 across two transcripts, including the SIFT annotations reported through that interpretation. | Results / Identification of an abdominal obesity GWAS missense variant within a unique adipocyte marker gene |

XML SHA-256: `6d8f96c90682633e9bbb02269e6cf2d94690dfe31e6f72d9c68770825e1ef658`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P193: Origins and implications of intron retention quantitative trait loci in human tissues

American Journal of Human Genetics · 2025-11-04 · [DOI](https://doi.org/10.1016/j.ajhg.2025.10.002) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808957/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization analysis |
| GCTA | Heritability, mixed-model association or conditional analysis. | Methods / Narrow-sense heritability estimates of irQTL |
| PEER | Latent confounder estimation. | Methods / irQTL analysis |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / Perturbation experiments |

XML SHA-256: `8611f79305c31cde3c28ee50d799c8652e494c8d089878a3403ea284c2b12703`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P194: Individuals who deviate from polygenic expectation are enriched for damaging variants in genes linked to rare disease

American Journal of Human Genetics · 2026-06-22 · [DOI](https://doi.org/10.1016/j.ajhg.2026.05.013) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13504349/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Variant consequence annotation |
| CADD | Variant deleteriousness annotation. | Methods / Variant consequence annotation |
| LOFTEE | Loss-of-function variant annotation. | Methods / Variant consequence annotation |
| PLINK | Used for genotype quality control. | Methods / Burden testing on phenotype misalignment |
| REGENIE | Used REGENIE 4.1 for MAF <0.1% pLoF and damaging-missense gene tests of phenotype misalignment. Step 1 used QCed array genotypes and sequencing tranche; misaligned-status tests used binary mode, while original continuous-trait comparisons used apply-rint. | Methods / Burden testing on phenotype misalignment |
| REVEL | Missense variant effect prediction. | Methods / Variant consequence annotation |
| SAIGE | Used a SAIGE array-genotype GRM as input to phenotype-specific greedy maximal-unrelated-set selection,excluding third-degree-or-closer relationships with G>2^-3.5. | Methods / Misaligned phenotype classification / Filtering to unrelated individuals |
| SpliceAI | Variant splice-impact prediction. | Methods / Variant consequence annotation |
| Ensembl VEP | Variant consequence annotation. | Methods / Variant consequence annotation |

XML SHA-256: `c2a5cbd8fc6f6234e930a0c736a47423455b1c2e40dba96e20ffeb306737a2fd`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P195: 3D genome topology distinguishes molecular subgroups of medulloblastoma

American Journal of Human Genetics · 2024-10-30 · [DOI](https://doi.org/10.1016/j.ajhg.2024.10.003) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639096/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Material and methods / Transcriptome analysis / Differential expression |
| BWA | DNA read alignment. | Material and methods / Hi-C contact maps |
| ChIPseeker | Peak annotation relative to genes and genomic features. | Material and methods / Hi-C contact maps |
| HISAT2 | Mapped RNA-seq to the stated no-alt-plus-hs38d1 hg38 reference with HISAT2 2.1.0 --rna-strandness RF --downstream-transcriptome-assembly. | Material and methods / Transcriptome analysis / Differential expression |
| HTSeq | Read counting and sequence processing. | Material and methods / Transcriptome analysis / Differential expression |
| Integrative Genomics Viewer (IGV) | Reported IGV v2.12.3 among the tools used to visualize Hi-C contacts and annotated features,together with Juicebox,HiCExplorer and pyGenomeTracks. | Material and methods / General statistical analysis and plotting |
| Juicer | Hi-C read processing and contact maps. | Material and methods / Hi-C contact maps |
| limma | Linear modelling and empirical-Bayes differential analysis. | Material and methods / Transcriptome analysis / Differential expression |
| SAMtools | Alignment manipulation and QC. | Material and methods / Transcriptome analysis / Differential expression |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Material and methods / SNF clustering |
| voom | Mean-variance weights for expression linear models. | Material and methods / Transcriptome analysis / Differential expression |

XML SHA-256: `4fccad7f6d187f3b1195ea7b65ab9b044a80d33440a548954e6787ac13b95da8`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P196: De novo variants in LDB1 are linked to distinct neurodevelopmental phenotypes determined by variant location and differing pathomechanisms

American Journal of Human Genetics · 2026-06-19 · [DOI](https://doi.org/10.1016/j.ajhg.2026.05.012) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13504347/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaMissense | Missense variant pathogenicity prediction. | Subjects and methods / In silico analysis |
| CADD | Variant deleteriousness annotation. | Subjects and methods / In silico analysis |
| REVEL | Missense variant effect prediction. | Subjects and methods / In silico analysis |
| SIFT | Missense variant effect annotation. | Subjects and methods / In silico analysis |
| SpliceAI | Variant splice-impact prediction. | Subjects and methods / In silico analysis |

XML SHA-256: `95faa1a0d1c00735514e9c2c97c83e61f5a6b83865b373e6f129842ccd286299`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P197: MARK2 variants cause autism spectrum disorder via the downregulation of WNT/β-catenin signaling pathway

American Journal of Human Genetics · 2024-10-16 · [DOI](https://doi.org/10.1016/j.ajhg.2024.09.006) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568763/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Material and methods / Model construction and calculation |
| CADD | Variant deleteriousness annotation. | Material and methods / Detection of MARK2 variants |
| clusterProfiler | Used clusterProfiler GO analysis and GSEA for genes meeting the RNA-seq differential-expression criteria P below 0.05 and absolute log2 fold change above 1.5. | Material and methods / RNA sequencing |
| DESeq2 | Count-based differential expression analysis. | Material and methods / RNA sequencing |
| GSEA | Ranked gene-set enrichment. | Material and methods / RNA sequencing |
| PolyPhen-2 | Missense variant effect annotation. | Material and methods / Detection of MARK2 variants |
| REVEL | Missense variant effect prediction. | Material and methods / Detection of MARK2 variants |
| Salmon | Transcript abundance quantification. | Material and methods / RNA sequencing |
| SIFT | Missense variant effect annotation. | Material and methods / Detection of MARK2 variants |

XML SHA-256: `88e5e37c9ddcd71c7aaa2429af134c25a7260edde6981956f685345e73b87082`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P198: Where is the boundary of the human pseudoautosomal region?

American Journal of Human Genetics · 2024-10-14 · [DOI](https://doi.org/10.1016/j.ajhg.2024.09.005) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568759/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Bowtie 2 | Short-read alignment. | Methods / Single-sperm sequencing |
| RepeatMasker | Repetitive sequence annotation. | Methods / Reanalysis of Y chromosomes sequenced by Hallast and colleagues |
| Sambamba | Alignment processing and duplicate removal. | Methods / Single-sperm sequencing |
| SAMtools | Alignment manipulation and QC. | Methods / Single-sperm sequencing |

XML SHA-256: `c00fe16d9e42a3dd58df478207b86b0fbe70bfee6a4d0609b637c4c22080e1af`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P199: Logica: A likelihood framework for cross-ancestry local genetic correlation estimation using summary statistics

American Journal of Human Genetics · 2025-10-23 · [DOI](https://doi.org/10.1016/j.ajhg.2025.10.001) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12739644/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| METAL | GWAS meta-analysis. | Material and methods / Real data applications |
| Popcorn | Cross-population genetic correlation. | Material and methods / Simulations |
| Ensembl VEP | Variant consequence annotation. | Material and methods / Real data applications |

XML SHA-256: `1f93b12ec761a3159a6d77d4cab15112418959284039e1ed4018dd815acc1fed`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P200: Genome-wide analysis implicates inner ear development in Ménière disease

American Journal of Human Genetics · 2026-06-16 · [DOI](https://doi.org/10.1016/j.ajhg.2026.05.011) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13393102/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| LDSC | Used for SNP heritability estimation. | Subjects and methods / Heritability estimate |
| MAGMA | Mapped meta-analysis SNP P-values to build38 NCBI protein-coding genes with symmetric10kb windows,European1000Genomes Phase3 LD and uniform GWAS sample size; used Bonferroni over annotated genes. | Subjects and methods / MAGMA gene association |
| METAL | GWAS meta-analysis. | Subjects and methods / GWAS meta-analysis |
| PLINK | Used Plink 1.9 LD clumping with r2=0.05 and a European 1000 Genomes reference to select index variants. Applied P<5e-8 first, then P<5e-7 for regions without an earlier clump, and retained unsupported singleton loci separately. | Subjects and methods / Index SNP selection |
| PLINK 2 | Used for genotype quality control. | Subjects and methods / Contributing biobanks and GWASs / AoU Research Program |
| REGENIE | Ran All of Us Meniere disease GWAS with REGENIE using age, sex, age squared, age-squared-by-sex and ten PCs after CDR v8 sequencing/phenotype selection and PLINK2 QC. | Subjects and methods / Contributing biobanks and GWASs / AoU Research Program |
| SAIGE | Reused SAIGE-generated Ménière-disease GWAS summaries from MVP,Pan-UKB and BBJ in a multibiobank analysis. | Subjects and methods / Contributing biobanks and GWASs / MVP |
| Seurat | Used for cell annotation. | Subjects and methods / Human inner ear gene expression |
| SuSiE | Bayesian fine-mapping and credible sets. | Subjects and methods / Fine-mapping |
| UCSC liftOver | Converted Pan-UKB and BBJ GWAS summary-statistic coordinates from GRCh37 to GRCh38 before METAL meta-analysis. | Subjects and methods / GWAS meta-analysis |
| Ensembl VEP | Variant consequence annotation. | Subjects and methods / Variant consequences |

XML SHA-256: `56c38e0cf1ead752938f859157f6b57f053b897937df0573ddc492f8cdb8fa61`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P201: Profiling genetically driven alternative splicing across the Indonesian archipelago

American Journal of Human Genetics · 2024-10-08 · [DOI](https://doi.org/10.1016/j.ajhg.2024.09.004) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568790/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| clusterProfiler | Tested GO and KEGG overrepresentation with all tested genes as universe and FDR 0.05, then simplified GO results with REVIGO using similarity 0.9 and the SimRel metric. | Subjects, material, and methods / Gene ontology overrepresentation analysis |
| coloc | Shared-signal colocalization analysis. | Subjects, material, and methods / Colocalization analysis with genome-wide association study hematological traits |
| DEXSeq | Differential exon usage. | Subjects, material, and methods / Isoform switching analyses |
| FastQC | Sequencing read quality assessment. | Subjects, material, and methods / RNA-seq processing |
| LeafCutter | Intron excision quantification and splicing analysis. | Subjects, material, and methods / Differential alternative splicing analysis |
| liftOver R workflow (rtracklayer interface) | Converted BLUEPRINT sQTL coordinates from hg19 to hg38 with the reported R liftOver packagev1.26.0 before cross-population comparison using mashr. | Subjects, material, and methods / Identifying shared cis-sQTLs and differences in effect sizes |
| limma | Linear modelling and empirical-Bayes differential analysis. | Subjects, material, and methods / Isoform switching analyses |
| mashr | Multivariate adaptive shrinkage. | Subjects, material, and methods / Identifying shared cis-sQTLs and differences in effect sizes |
| PEER | Latent confounder estimation. | Subjects, material, and methods / cis-sQTL mapping |
| QTLtools | Molecular QTL mapping and QC. | Subjects, material, and methods / cis-sQTL mapping |
| Salmon | Transcript abundance quantification. | Subjects, material, and methods / Differential alternative splicing analysis |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Subjects, material, and methods / RNA-seq processing |
| SUPPA2 | Alternative splicing quantification. | Subjects, material, and methods / Differential alternative splicing analysis |
| Trimmomatic | Used Trimmomatic0.36 to remove leading/trailing RNA-seq bases below Phred20, then STAR with EndToEnd and sjdbOverhang100 against GRCh38 Ensembl110. | Subjects, material, and methods / RNA-seq processing |

XML SHA-256: `405d53aae833dabbdca2d4dcf82fd0ce5d250102cd27c27f0e707acbb3b4163f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P202: Maternal age and genome-wide failure of meiotic recombination are associated with triploid conceptions in humans

American Journal of Human Genetics · 2025-10-14 · [DOI](https://doi.org/10.1016/j.ajhg.2025.09.014) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808983/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Subjects, material, and methods / Sequencing data analysis |

XML SHA-256: `b7f481c36d061c0258e1e1741805793fb19acb9077119e713e4b14f508c63a69`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P203: Cross-ancestry analysis of brain QTLs enhances interpretation of schizophrenia genome-wide association studies

American Journal of Human Genetics · 2024-10-02 · [DOI](https://doi.org/10.1016/j.ajhg.2024.09.001) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568756/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Subjects and methods / Variant identification |
| coloc | Shared-signal colocalization analysis. | Subjects and methods / Colocalization |
| FastQC | Sequencing read quality assessment. | Subjects and methods / Data quality control |
| GATK | Variant discovery and recalibration. | Subjects and methods / Variant identification |
| HEIDI | Heterogeneity test distinguishing pleiotropy from linkage. | Subjects and methods / Summary-data-based mendelian randomization |
| LDSC | Used for annotation-stratified genetic enrichment. | Subjects and methods / Partitioned LDSR |
| METAL | GWAS meta-analysis. | Subjects and methods / eQTL fine-mapping |
| MetaXcan | Predicted expression association. | Subjects and methods / Prioritizing genes underlying GWAS hits using MetaXcan |
| PEER | Latent confounder estimation. | Subjects and methods / Covariate selection |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Subjects and methods / Population validation, imputation, and filtering |
| QTLtools | Molecular QTL mapping and QC. | Subjects and methods / cis-eQTL mapping |
| RSEM | Transcript abundance estimation. | Subjects and methods / Gene expression quantification and quality control |
| SAMtools | Alignment manipulation and QC. | Subjects and methods / Variant identification |
| SMR | Summary-data molecular association or Mendelian randomization. | Subjects and methods / Summary-data-based mendelian randomization |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Subjects and methods / Gene expression quantification and quality control |
| voom | Mean-variance weights for expression linear models. | Subjects and methods / Gene expression quantification and quality control |

XML SHA-256: `2d035da198a42fbfd1067cedca09c0fc77b108e5269d02af3481cebd8bd1d644`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P204: A scalable framework for identifying allelic series from summary statistics

American Journal of Human Genetics · 2025-10-13 · [DOI](https://doi.org/10.1016/j.ajhg.2025.09.012) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808971/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Hail | Distributed genotype QC and association. | Material and methods / Preparation of UKB data / Quality control |
| LOFTEE | Loss-of-function variant annotation. | Material and methods / Preparation of UKB data / Whole-exome sequencing data |
| PolyPhen-2 | Missense variant effect annotation. | Material and methods / Preparation of UKB data / Whole-exome sequencing data |
| SAIGE | Calculated per-variant lipid-trait effects and standard errors with SAIGE1.3.0 among390,865UKB participants,using inverse-normalized traits and a sparse GRM. | Material and methods / Preparation of UKB data / Association analysis |
| SIFT | Missense variant effect annotation. | Material and methods / Preparation of UKB data / Whole-exome sequencing data |
| SKAT | Rare-variant sequence-kernel association. | Material and methods / COAST from summary statistics |
| Ensembl VEP | Variant consequence annotation. | Material and methods / Preparation of UKB data / Whole-exome sequencing data |

XML SHA-256: `19095a8cf795209c6a2cfb83096a9dddd3cecfe3915390f841d2ff5f7ed6d4cf`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P205: DiscoDivas: Leveraging genetic-ancestry continuum information to interpolate PRS for admixed populations

American journal of human genetics · 2026-06-03 · [DOI](https://doi.org/10.1016/j.ajhg.2026.05.006) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13504350/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DiscoDivas | Polygenic scoring across a genetic ancestry continuum. | Methods / Methodological details of DiscoDivas |
| LDpred | Used LDpred2 to adjust SNP effects for real GWAS data in the conventional multi-population PRS comparator, then selected predictive scores and trained their linear combination in fine-tuning data. | Results / Overview of multi-population GWAS PRS model |
| PLINK 2 | Genotype QC, association and score calculation. | Methods / Data / Simulated data |
| PRS-CS | Used PRS-CS to adjust SNP effects for simulated GWAS data in the conventional multi-population PRS comparator pipeline, followed by independent tuning and linear combination of single-GWAS scores. | Results / Overview of multi-population GWAS PRS model |

XML SHA-256: `2d74f341fbaacb04f31bae9c0f28e72ed1c23c0e3cd10aff3a3d14062a77db32`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P206: Large-scale application of ClinGen-InSiGHT APC-specific ACMG/AMP variant classification criteria leads to substantial reduction in VUS

American Journal of Human Genetics · 2024-10-01 · [DOI](https://doi.org/10.1016/j.ajhg.2024.09.002) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568752/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| SpliceAI | Variant splice-impact prediction. | Methods / Predictive data (PVS1, PP3, BP1, BP4, and BP7) |
| sva | Latent confounder adjustment. | Methods / ClinVar and LOVD variant extraction and annotation |
| Ensembl VEP | Variant consequence annotation. | Methods / ClinVar and LOVD variant extraction and annotation |

XML SHA-256: `cf61e571df9618218aa676f6ddce6af371a26e3a66aa0bdab433ef4cd516db91`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P207: Joint testing of rare variant burden scores using non-negative least squares

American Journal of Human Genetics · 2024-10-01 · [DOI](https://doi.org/10.1016/j.ajhg.2024.08.021) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11480795/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Methods / Sparse burden association test |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / Analysis of UK Biobank data |
| GCTA | Used for LD-based variant selection; conditional or joint association analysis. | Methods / Analysis of UK Biobank data |
| REGENIE | Used REGENIE for common-variant GWAS/step-1 predictors and exome gene tests in quantitative UKB traits. Compared SBAT, SKAT-O, ACAT-V and multiple-mask BURDEN-ACAT, then combined them into GENE_P; variants were grouped by four annotation classes and multiple AAF bins. | Methods / Analysis of UK Biobank data |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Methods / Combining different gene-based tests into a single p value per gene |
| SnpEff | Variant consequence annotation. | Methods / Annotations and AAF cutoffs for burden scores |

XML SHA-256: `fc9fc558adee3f0801b7367540e3bcae265c7016ef2abe8e02a190680a2a45dd`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P208: Distinguishing syndromic and nonsyndromic cleft palate through analysis of protein-altering de novo variants in 818 trios

American journal of human genetics · 2025-10-06 · [DOI](https://doi.org/10.1016/j.ajhg.2025.09.009) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808955/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ANNOVAR | Variant functional annotation. | Subjects and methods / Variant annotation |
| DRAGEN | Sequencing alignment, variant or RNA processing platform; named workflow must be identified. | Subjects and methods / WGS |
| GATK | Variant discovery and recalibration. | Subjects and methods / WGS |
| g:Profiler | Functional gene-set enrichment. | Subjects and methods / Enrichment analyses and creation of gene sets |

XML SHA-256: `89d54610e6bd493a525acc90eada392bf2a98f090e991e08652c33963f7b38e2`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P209: Multiple-testing corrections in selection scans using identity-by-descent segments

American Journal of Human Genetics · 2025-09-26 · [DOI](https://doi.org/10.1016/j.ajhg.2025.09.004) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12668784/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Material and methods / Pre-processing genetic data / TOPMed |
| Beagle | Separately phased the Indian British and Black British UKBB subsets with Beagle 5.4; other TOPMed and White British datasets reused earlier Beagle 5.2 phasing. | Material and methods / Pre-processing genetic data / UKBB |
| hap-ibd | Identity-by-descent segment detection. | Material and methods / Pre-processing genetic data / UKBB |

XML SHA-256: `04c2a90b6f70a8b838619f096ddfd1c8c25ce3187c1dbca3cc16bf5e57dd18cd`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P210: Meta-analysis across six global biobanks identifies recessive coding associations with complex traits and diseases

American Journal of Human Genetics · 2026-05-01 · [DOI](https://doi.org/10.1016/j.ajhg.2026.04.005) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13277689/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BWA | DNA read alignment. | Methods / Variant calling |
| CADD | Variant deleteriousness annotation. | Methods / Variant annotation |
| GATK | Variant discovery and recalibration. | Methods / Variant calling |
| Hail | Distributed genotype QC and association. | Methods / Variant calling |
| LOFTEE | Loss-of-function variant annotation. | Methods / Variant annotation |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / Variant calling |
| REGENIE | Used SAIGE or REGENIE to test ancestry-stratified recessive gene burdens with age/sex/interactions/PC covariates, RINT quantitative traits and Firth correction below P=0.01 for binary traits. Gene-phenotype tests required at least five bi-allelic carriers; matching additive tests used the same available gene universe. | Methods / Association analysis |
| REVEL | Missense variant effect prediction. | Methods / Variant annotation |
| SAIGE | Tested supplied recessive burdens with SAIGE or REGENIE per ancestry/subcohort,and subsequently tested additive burdens for genes with available recessive summaries. | Methods / Association analysis |
| SHAPEIT | Genotype haplotype phasing. | Methods / Statistical phasing and recessive burden calculation |
| Snakemake | Analysis workflow execution. | Methods / Statistical phasing and recessive burden calculation |
| SpliceAI | Variant splice-impact prediction. | Methods / Variant annotation |
| Ensembl VEP | Variant consequence annotation. | Methods / Variant annotation |
| WhatsHap | Read-based haplotype phasing. | Methods / Statistical phasing and recessive burden calculation |

XML SHA-256: `3f6fcfe1b9243e9970791a2f6799682576114a1b5d8f05b9572c6e4b12b9b31b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P211: Disentangling mechanisms behind the pleiotropic effects of proximal 16p11.2 BP4-5 CNVs

American Journal of Human Genetics · 2024-09-26 · [DOI](https://doi.org/10.1016/j.ajhg.2024.08.014) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568757/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| PLINK | Genotype QC, association, relatedness, LD or score calculations. | Material and methods / Study material / Software versions |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Material and methods / Study material / Software versions |

XML SHA-256: `32880fd62b705a534f47ad5333c87bed94a1292433f919865023a1be0eda5a64`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P212: Skeletal muscle eQTL meta-analysis implicates genes in the genetic architecture of muscular and cardiometabolic traits

American Journal of Human Genetics · 2025-09-23 · [DOI](https://doi.org/10.1016/j.ajhg.2025.09.003) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12614742/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| APEX | Molecular QTL analysis and meta-analysis. | Methods / Study-level eQTL analyses |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Methods / GWAS data for colocalization |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization with GWAS signals |
| edgeR | Count-based differential expression analysis. | Methods / Study-level eQTL analyses |
| FastQTL | Permutation-based molecular QTL mapping. | Methods / T2D colocalizations across tissues |
| FUSION | Transcriptome-wide association and conditional follow-up. | Results / Skeletal muscle eQTL colocalization with GWAS signals |
| GATK | Variant discovery and recalibration. | Methods / Study cohorts / FUSION |
| GCTA | Used for LD-based variant selection; genetic association analysis; conditional or joint association analysis. | Methods / GWAS data for colocalization |
| METAL | GWAS meta-analysis. | Methods / eQTL signal characterization |
| PEER | Latent confounder estimation. | Methods / Study-level eQTL analyses |
| PLINK | Used for genotype quality control; LD-based variant selection; genetic association analysis. | Methods / Colocalization with GWAS signals |
| SAMtools | Alignment manipulation and QC. | Methods / Study cohorts / FUSION |
| tximport | Used tximport to calculate what the paper calls length-scaled TPM for SGBS RNA-seq, then used median expression across replicates to measure INHBB expression. | Methods / Transcriptional reporter assays |

XML SHA-256: `92008590eafb6a68abea130c2562bd521c2a12802c66d58289643b773eed00aa`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P213: The impact of sex on the immune system explored at the single-cell level

American Journal of Human Genetics · 2026-05-01 · [DOI](https://doi.org/10.1016/j.ajhg.2026.04.003) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13277697/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Azimuth | Reference mapping and cell annotation. | Methods / OneK1K data and cell-type classification |
| Cell Ranger | Reported OneK1K preprocessing with Cell Ranger v2.2.0 for pool demultiplexing and STAR-based mapping to GRCh37/hg19 release84. | Methods / OneK1K data and cell-type classification |
| mashr | Multivariate adaptive shrinkage. | Methods / Sex-differential expression analysis |
| Matrix eQTL | Matrix-based QTL association. | Methods / Sex-specific eQTL and sex-interacting eQTL analysis / Sex-specific eQTL discovery (stratified approach) / Step 2: Filtering |
| Minimac | Genotype imputation. | Methods / OneK1K data and cell-type classification |
| PEER | Latent confounder estimation. | Methods / Sex-specific eQTL and sex-interacting eQTL analysis / Joint eQTLs |
| sctransform | Used Seurat 4.4 batch processing and SCTransform on OneK1K counts aligned with CellRanger 2.2 to hg19 before Azimuth reference annotation. | Methods / OneK1K data and cell-type classification |
| Seurat | Used for expression normalization; dataset integration. | Methods / OneK1K data and cell-type classification |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / OneK1K data and cell-type classification |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Methods / Sex-chromosome gene-expression analysis |

XML SHA-256: `959d396766e52593b434ed2bdb8b14f926e2213e84c92615dcd3a13e5069d952`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P214: Sensitivity of HiFi long-read genome sequencing for difficult-to-detect pathogenic variants when applied to real-world clinical laboratory samples

American Journal of Human Genetics · 2026-04-27 · [DOI](https://doi.org/10.1016/j.ajhg.2026.04.001) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13277683/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Material and methods / HiFi lrGS (HiFi sequencing) |
| Integrative Genomics Viewer (IGV) | Independently reviewed methylation-containing HiFi BAMs in IGV with two scientists,inspecting relevant CpG islands in positive and control samples;the Results also note a mosaic SCN1A deletion visible in IGV despite a missed automatic call. | Material and methods / Detection of imprinting-related methylation signatures using HiFi |
| pbmm2 | PacBio read alignment. | Material and methods / HiFi lrGS (HiFi sequencing) |
| WhatsHap | Read-based haplotype phasing. | Material and methods / HiFi lrGS (HiFi sequencing) |

XML SHA-256: `ef45a614ac3c79fc40bdd8c8b9b72fea4de882a2ad8340dc4c65ad43c94d0e53`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P215: ARID1A-BAF coordinates ZIC2 genomic occupancy for epithelial-to-mesenchymal transition in cranial neural crest specification

American Journal of Human Genetics · 2024-09-02 · [DOI](https://doi.org/10.1016/j.ajhg.2024.07.022) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11480806/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Material and methods / RNA-sequencing analysis |
| deepTools | Listed deepTools among statistical-analysis software after Kallisto quantification and DESeq2; the paragraph does not identify a deepTools command or settings. | Material and methods / RNA-sequencing analysis |
| DESeq2 | Count-based differential expression analysis. | Material and methods / RNA-sequencing analysis |
| FastQC | Sequencing read quality assessment. | Material and methods / RNA-sequencing analysis |
| FIMO | Quantified transcription-factor motif presence with FIMO on the background of ARID1A-bound regions lost in CSS cells, including the ZIC-family motif in lost enhancers. A separate HOMER de novo motif-enrichment analysis used shuffled input sequences and E < 0.001. | Material and methods / Motif analysis |
| HOMER | Motif and regulatory enrichment analysis. | Material and methods / Motif analysis |
| Integrative Genomics Viewer (IGV) | Visualized ChIP/ATAC peaks in IGV on hg19;the supporting figure descriptions show retained/lost ARID1A peaks and lost/gained ZIC2 binding in the compared cell lines. | Material and methods / ChIP- and ATAC-seq analysis |
| kallisto | Quantified gene-mapping RNA-seq reads with kallisto after Trim Galore/FastQC; estimated count-scale matrices fed DESeq2 and TPMs were used in gene violin plots. | Material and methods / RNA-sequencing analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Material and methods / Human iPSC collection, reprogramming, and culture |
| MACS | Called ATAC peaks with MACS2 at reported 5% FDR after hg19 BWA-MEM alignment, MAPQ/duplicate filtering and strand-specific Tn5 offsets; visualized peak tracks separately. | Material and methods / ChIP- and ATAC-seq analysis |
| MEME | Motif discovery and scanning. | Material and methods / Motif analysis |
| Trim Galore | Trimmed RNA-seq adapters with Trim Galore before Kallisto quantification. Separate ChIP-seq/ATAC-seq methods also report Trim Galore adapter trimming and FastQC assessment before alignment and assay-specific processing. | Material and methods / RNA-sequencing analysis |

XML SHA-256: `2c2eadf4d1372a82a1c0a402917e9eb2434b4d634407b33cf24117b39a3abd6f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P216: MosCoverY: A method to estimate mosaic loss of Y chromosome from sequencing coverage data

American Journal of Human Genetics · 2025-09-11 · [DOI](https://doi.org/10.1016/j.ajhg.2025.08.016) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696490/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Subjects and methods / Genetic validation: GWAS |
| GATK | Variant discovery and recalibration. | Subjects and methods / Other mLOY estimation methods in TCGA |
| GCTA | Used for LD-based variant selection; genetic association analysis; conditional or joint association analysis. | Subjects and methods / Genetic validation: GWAS |
| Picard | Alignment QC, duplicates and sequence processing. | Subjects and methods / Exome coverage downsampling |
| PLINK 2 | Used for LD-based variant selection. | Subjects and methods / Genetic validation: GWAS |
| REGENIE | Ran REGENIE in two steps: ridge whole-genome predictors on 611713 variants, then association on 9607831 imputed variants with age, ever-smoking, batch, WES release and ten PCs. | Subjects and methods / Genetic validation: GWAS |

XML SHA-256: `b4f22a1a3ee0ce4ac55290d2a0ded9d2b62f193e2e5e1363198f2068676a1967`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P217: Substitution of a single non-coding nucleotide upstream of TMEM216 causes non-syndromic retinitis pigmentosa and is associated with reduced TMEM216 expression

American Journal of Human Genetics · 2024-08-26 · [DOI](https://doi.org/10.1016/j.ajhg.2024.07.020) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11393691/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| FIMO | Used FIMO from MEME Suite 5.5.4 with JASPAR and HOCOMOCO PWMs to scan supplied sequences and retain motif occurrences at P < 0.001. Compared predicted transcription-factor binding in wild-type and TMEM216 promoter-variant sequence contexts, alongside separate expression and reporter experiments. | Subjects and methods / Impact of TMEM216 c.−69 G>A and G>T variants on transcription factor binding |
| Integrative Genomics Viewer (IGV) | Used IGV read-depth analysis of blood-derived ONT TMEM216 transcripts to assess allele imbalance in a carrier,reporting23,255 versus8,184 reads(74%/26%) at the linked coding SNV. Supporting Results describe inspection of apparent homozygotes and the cDNA-read display. | Results / Skewed allele balance in a c.−69G>T carrier |
| MEME | Motif discovery and scanning. | Subjects and methods / Impact of TMEM216 c.−69 G>A and G>T variants on transcription factor binding |
| minimap2 | Mapped 800-1,800-bp ONT TMEM216 transcript reads to GRCh38 with minimap2 2.22 after Porechop/NanoFilt filtering, then inspected alleles in IGV. | Subjects and methods / Sequencing of TMEM216 transcript from blood samples of individuals with TMEM216 c.−69G>T |
| SAMtools | Alignment manipulation and QC. | Subjects and methods / Sequencing of TMEM216 transcript from blood samples of individuals with TMEM216 c.−69G>T |

XML SHA-256: `9860f0d9499a3fd0e9a4fabda90c1a207535b40ba08dd29e5873f3809dbddeab`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P218: Bi-allelic deleterious variants in SNAPIN, which encodes a retrograde dynein adaptor, cause a prenatal-onset neurodevelopmental disorder

American Journal of Human Genetics · 2025-09-09 · [DOI](https://doi.org/10.1016/j.ajhg.2025.08.015) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12500174/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Integrative Genomics Viewer (IGV) | Visualized fibroblast RNA-seq alignments in IGV after STAR alignment to GRCh38/hg38 and aggregation into a Picard BAM file,including cultures with and without cycloheximide treatment. | Material and methods / RNA sequencing of human fibroblasts (family B) |
| Leiden | Grouped observations into communities using a neighborhood graph. | Material and methods / Ascertainment, ES or GS, and bioinformatic analysis |
| Picard | Alignment QC, duplicates and sequence processing. | Material and methods / RNA sequencing of human fibroblasts (family B) |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Material and methods / RNA sequencing of human fibroblasts (family B) |

XML SHA-256: `64d88796d3092074bbe981603650af6fe01a645bbd7dadd282c7e13b8600aeb2`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P219: Genetic effects on the skin methylome in healthy older twins

American Journal of Human Genetics · 2024-08-12 · [DOI](https://doi.org/10.1016/j.ajhg.2024.07.010) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11393713/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BEDTools | Genomic interval manipulation and overlap. | Material and methods / Genomic and functional enrichment of meQTL-CpG pairs |
| GCTA-COJO | LD-adjusted conditional or joint GWAS analysis. | Material and methods / Identification of skin meQTLs |
| coloc | Shared-signal colocalization analysis. | Material and methods / Sharing of skin meQTLs and skin eQTLs |
| Eagle | Genotype phasing. | Material and methods / Genotypes |
| GCTA | Used for genetic association analysis; conditional or joint association analysis. | Material and methods / Identification of skin meQTLs |
| Matrix eQTL | Matrix-based QTL association. | Material and methods / Identification of skin meQTLs |
| PEER | Latent confounder estimation. | Material and methods / Identification of local skin eQTLs |
| QTLtools | Molecular QTL mapping and QC. | Material and methods / Skin gene expression profiles |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Material and methods / Skin gene expression profiles |
| SuSiE | Bayesian fine-mapping and credible sets. | Material and methods / Sharing of skin meQTLs and skin eQTLs |

XML SHA-256: `e4fadb062adf4cc95728affb2ee19c5e59af94b50cce138a693a6dbb5f073112`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P220: Combining evidence from human genetic and functional screens to identify pathways altering obesity and fat distribution

American Journal of Human Genetics · 2025-09-04 · [DOI](https://doi.org/10.1016/j.ajhg.2025.08.013) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696503/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Material and methods / Variant consequence annotation |
| CADD | Variant deleteriousness annotation. | Material and methods / Variant consequence annotation |
| DESeq2 | Count-based differential expression analysis. | Material and methods / Wet lab methods / RNA-seq of human white adipose tissue cells |
| FINEMAP | Bayesian fine-mapping of association loci. | Material and methods / Common-variant association testing in imputed data / Fine-mapping |
| GATK | Variant discovery and recalibration. | Material and methods / ES data QC / ES data QC summary |
| GSEA | Ranked gene-set enrichment. | Material and methods / Wet lab methods / Pathway enrichment in CRISPR RNA-seq data |
| Hail | Distributed genotype QC and association. | Material and methods / ES data QC / Variant and sample-level QC |
| Harmony | Batch integration of cell embeddings. | Material and methods / Wet lab methods / Lipid staining with BODIPY |
| LDSC | Used for cross-trait genetic correlation. | Material and methods / Genetic correlation estimates |
| LDstore | Linkage disequilibrium matrix calculation. | Material and methods / Common-variant association testing in imputed data / Fine-mapping |
| LOFTEE | Loss-of-function variant annotation. | Material and methods / Variant consequence annotation |
| PLINK | Used for LD-based variant selection. | Material and methods / Genetic association testing |
| PoPS | Effector-gene prioritization using polygenic features. | Material and methods / Common-variant gene prioritization scores |
| REVEL | Missense variant effect prediction. | Material and methods / Variant consequence annotation |
| RSEM | Transcript abundance estimation. | Material and methods / Wet lab methods / RNA-seq of human white adipose tissue cells |
| SAIGE | Ran SAIGE common-variant association with1.1.6.3 and exome variant/gene tests with container1.1.9,using sparse-GRM null models,categorical variance ratios and quantitative inverse-normal transformation. | Material and methods / Genetic association testing |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Material and methods / Rare-variant and gene-level testing in ES data / Obesity and fat distribution trait-gene-level association tests |
| SpliceAI | Variant splice-impact prediction. | Material and methods / Variant consequence annotation |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Material and methods / Wet lab methods / RNA-seq of human white adipose tissue cells |
| Ensembl VEP | Variant consequence annotation. | Material and methods / Variant consequence annotation |

XML SHA-256: `d8be6d2b664a13ccf501559a92f41fc70280248d93b67c5872cd6d70a7d767f3`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P221: Multi-ancestry transcriptome prediction with functionally informed variants in TOPMed MESA improves performance of transcriptome-wide association studies

American Journal of Human Genetics · 2026-04-01 · [DOI](https://doi.org/10.1016/j.ajhg.2026.03.008) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13078710/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ACAT | Cauchy aggregation of association evidence. | Material and methods / Omnibus approach of FIV-based methods |
| MetaXcan | Predicted expression association. | Material and methods / TWAS |
| PEER | Latent confounder estimation. | Material and methods / Prediction accuracy validation analysis / JHS |
| PrediXcan | Genetically predicted expression association. | Material and methods / TWAS |
| PUMICE | Ran PUMICE with default settings using epigenomic annotations to distinguish essential/nonessential predictors and3D-genome regions to define cis-regulatory candidate sets for transcriptome prediction. | Material and methods / Transcriptome prediction methods / PUMICE |
| SuSiE | Bayesian fine-mapping and credible sets. | Material and methods / Transcriptome prediction methods / EN-FM |
| UCSC liftOver | Mapped hg19 epigenomic tracks and3D-genome regulatory regions to hg38 to align functional annotations with TOPMed MESA genotypes for PUMICE prediction. | Material and methods / Transcriptome prediction methods / PUMICE |

XML SHA-256: `927bc7bc410e96e1682ea57155b66dc3b7e436caeedabc7e9819a6e254d8bdda`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P222: Rare variants in PRKCI cause Van der Woude syndrome and other features of peridermopathy

American Journal of Human Genetics · 2025-09-02 · [DOI](https://doi.org/10.1016/j.ajhg.2025.08.008) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12510259/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaMissense | Missense variant pathogenicity prediction. | Material and methods / Sample preparation and whole-genome sequencing / Variant filtering and annotation |
| ANNOVAR | Variant functional annotation. | Material and methods / Sample preparation and whole-genome sequencing / Variant filtering and annotation |
| DRAGEN | Sequencing alignment, variant or RNA processing platform; named workflow must be identified. | Material and methods / Sample preparation and whole-genome sequencing / VWS and CPSeq cohorts |
| GATK | Variant discovery and recalibration. | Material and methods / Sample preparation and whole-genome sequencing / GMKF |
| PolyPhen-2 | Missense variant effect annotation. | Material and methods / Sample preparation and whole-genome sequencing / Variant filtering and annotation |
| PyMOL | Modelled PRKCI variants from PDB1ZRZ with a backbone-independent rotamer in PyMOL and measured them with the Wizard Measurement tool. | Material and methods / Protein 3D structure modeling |
| SIFT | Missense variant effect annotation. | Material and methods / Sample preparation and whole-genome sequencing / Variant filtering and annotation |

XML SHA-256: `61ee16460af7c0e99cc2cb7c0a5c008929f32efa3863efb62d910f55bf8e648f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P223: RNA variant assessment using transactivation and transdifferentiation

American Journal of Human Genetics · 2024-07-30 · [DOI](https://doi.org/10.1016/j.ajhg.2024.06.018) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339655/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Ballgown | Transcript expression analysis with assembled RNA transcripts. | Subjects, material, and methods / Short-read RNA-seq |
| BEDTools | Genomic interval manipulation and overlap. | Subjects, material, and methods / Assay for transposase-accessible chromatin with high-throughput sequencing (ATAC-seq) |
| biomaRt | Assigned Ensembl identifiers to gene symbols with biomaRt for short-read RNA-seq differential-expression analysis using edgeR. | Subjects, material, and methods / Short-read RNA-seq |
| Bowtie 2 | Short-read alignment. | Subjects, material, and methods / Assay for transposase-accessible chromatin with high-throughput sequencing (ATAC-seq) |
| Cell Ranger | Processed targeted scRNA-seq and guide-RNA sequencing files with cellranger v7.1.0 before integrating their count matrices in Seurat. | Subjects, material, and methods / scRNA-seq |
| edgeR | Count-based differential expression analysis. | Subjects, material, and methods / Short-read RNA-seq |
| fastp | Read filtering and adapter trimming. | Subjects, material, and methods / Assay for transposase-accessible chromatin with high-throughput sequencing (ATAC-seq) |
| featureCounts | Used Rsubread/seqinr featureCounts and juncCounts with custom GENCODE transcript annotations to extract splice-junction read counts. | Subjects, material, and methods / Short-read RNA-seq |
| Guppy | Nanopore basecalling; distinguish the fish name. | Subjects, material, and methods / Oxford nanopore amplicon sequencing |
| HISAT2 | Used the HISAT2/StringTie/Ballgown short-read RNA-seq workflow to map paired 150-bp reads to GRCh38/hg38. | Subjects, material, and methods / Short-read RNA-seq |
| Integrative Genomics Viewer (IGV) | Visualized GRCh38-mapped Oxford Nanopore amplicon reads in IGV;the supporting data-analysis paragraph also reports genome visualization with IGV and UCSC Genome Browser. | Subjects, material, and methods / Oxford nanopore amplicon sequencing |
| MACS | Called MACS2 ATAC peaks after hg38 mapping, mitochondrial/exclusion-list removal and SAMtools markdup using no model, extension 150, shift -75, human genome shortcut and keep-all duplicates; intersected promoter windows and normalized promoter counts. | Subjects, material, and methods / Assay for transposase-accessible chromatin with high-throughput sequencing (ATAC-seq) |
| MAJIQ | Splicing quantification and differential analysis. | Subjects, material, and methods / Data resources and analysis |
| Rsubread | Used Rsubread featureCounts/juncCounts with custom GENCODE transcript annotations for junction-read extraction. | Subjects, material, and methods / Short-read RNA-seq |
| Salmon | Transcript abundance quantification. | Subjects, material, and methods / Short-read RNA-seq |
| SAMtools | Alignment manipulation and QC. | Subjects, material, and methods / Assay for transposase-accessible chromatin with high-throughput sequencing (ATAC-seq) |
| seqinr | Used seqinr for biological sequence retrieval/analysis in a custom Rsubread junction-read extraction workflow. | Subjects, material, and methods / Short-read RNA-seq |
| Seurat | Used for dataset integration. | Subjects, material, and methods / scRNA-seq |
| StringTie | Transcript assembly and quantification. | Subjects, material, and methods / Short-read RNA-seq |

XML SHA-256: `12a39771e280e2ac90433d9881faae4b4b3d11bde9e35070997f405b2409a342`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P224: A semi-empirical Bayes approach for calibrating weak instrumental bias in sex-specific Mendelian randomization studies

American Journal of Human Genetics · 2025-09-01 · [DOI](https://doi.org/10.1016/j.ajhg.2025.07.015) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12416758/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| KING | Estimated kinship using KING v.2.0 and removed related individuals at coefficients>=0.0884 before sex-specific sleep GWAS, including relatives across the sex strata. | Appendix A / Sex-specific sleep GWASs in MVP |
| MR-PRESSO | MR pleiotropy/outlier sensitivity. | Results / Results from secondary analyses |
| PLINK 2 | Performed separate sex-stratum MVP GWASs for four sleep phenotypes using PLINK v.2.00a3LM. Analyses adjusted for age and ten PCs; each trait had models with and without BMI, with linear and squared BMI terms in the adjusted model. | Appendix A / Sex-specific sleep GWASs in MVP |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Appendix A / MVMR analysis |

XML SHA-256: `d4736392299f52787aaccdcc55081d1632f433b08e3468c73e9b4b647c9a5535`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P225: Cell-type-specific effects of autism-associated 15q duplication syndrome in the human brain

American Journal of Human Genetics · 2024-07-29 · [DOI](https://doi.org/10.1016/j.ajhg.2024.07.002) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339625/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Reported Cell Ranger v6.1.1 in the snRNA-seq processing workflow against GRCh38-2020-A, and separately used CellRanger ARC v2.0.0 for multiome mapping to a reference labelled CRCh38-2020-A-2.0.0. | Material and methods / Bioinformatics analysis |
| chromVAR | Chromatin motif accessibility deviation scores. | Material and methods / Bioinformatics analysis |
| clusterProfiler | Performed GO enrichment after per-cluster single-nucleus differential-expression analysis and treated pathways with BH-adjusted P below 0.05 as enriched. | Material and methods / Bioinformatics analysis |
| Harmony | Batch integration of cell embeddings. | Material and methods / Bioinformatics analysis |
| inferCNV | Expression-derived copy-number inference. | Material and methods / Bioinformatics analysis |
| scCODA | Compositional single-cell abundance analysis. | Material and methods / Bioinformatics analysis |
| scds | Single-cell doublet detection. | Material and methods / Bioinformatics analysis |
| SCENIC | Single-cell regulatory network inference. | Material and methods / Bioinformatics analysis |
| Seurat | Used for cell clustering. | Material and methods / Bioinformatics analysis |
| Signac | Single-cell chromatin accessibility analysis. | Material and methods / Bioinformatics analysis |
| SoupX | Ambient RNA contamination correction. | Material and methods / Bioinformatics analysis |

XML SHA-256: `f4c7cb7cfd0297a8b747f96b56b034512cdcfc8203327f8da0cf4653c2f3c347`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P226: Pathogenic variants in TMEM184B cause a neurodevelopmental syndrome associated with alteration of metabolic signaling

American Journal of Human Genetics · 2025-08-29 · [DOI](https://doi.org/10.1016/j.ajhg.2025.08.004) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12440709/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Subjects, material, and methods / Structure prediction |
| AlphaMissense | Missense variant pathogenicity prediction. | Subjects, material, and methods / Genome sequencing and analysis |
| CADD | Variant deleteriousness annotation. | Subjects, material, and methods / Genome sequencing and analysis |
| DESeq2 | Count-based differential expression analysis. | Subjects, material, and methods / RNA and transcriptomic analysis |
| FastQC | Sequencing read quality assessment. | Subjects, material, and methods / RNA and transcriptomic analysis |
| PyMOL | Created native/variant TMEM184B structure visualizations and overlays using PyMOL v2.6. | Subjects, material, and methods / Structure prediction |
| REVEL | Missense variant effect prediction. | Subjects, material, and methods / Genome sequencing and analysis |
| Salmon | Transcript abundance quantification. | Subjects, material, and methods / RNA and transcriptomic analysis |
| Trimmomatic | Removed adapters with Trimmomatic0.39 after FastQC, then quantified against a human v38 transcriptome using Salmon1.8.0. The source describes TPM-form transcript counts analyzed with DESeq2 in Galaxy. | Subjects, material, and methods / RNA and transcriptomic analysis |

XML SHA-256: `917b552cde86031ade53ca222a1a96b494ecbf0a2fee979cb0bb83bea2c43377`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P227: Misexpression of inactive genes in whole blood is associated with nearby rare structural variants

American Journal of Human Genetics · 2024-07-24 · [DOI](https://doi.org/10.1016/j.ajhg.2024.06.017) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339615/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AnnotationHub | Used AnnotationHub2.18.0 with tximport1.14.2,BiocFileCache and BiocGenerics to obtain transcript- or gene-level count matrices after Salmon1.1.0 quantification. | Subjects and methods / Salmon transcript quantification |
| BEDTools | Genomic interval manipulation and overlap. | Subjects and methods / Selection and characterization of transcriptional readthrough candidate SVs |
| BiocFileCache | Listed BiocFileCache1.10.2 among the R packages applied with tximport to obtain count matrices from Salmon1.1.0 quantifications. | Subjects and methods / Salmon transcript quantification |
| BiocGenerics | Listed BiocGenerics0.32.0 among supporting R packages applied with tximport,AnnotationHub and BiocFileCache for transcript/gene count matrices. | Subjects and methods / Salmon transcript quantification |
| BWA | DNA read alignment. | Subjects and methods / WGS |
| CADD | Variant deleteriousness annotation. | Subjects and methods / SV properties |
| FastQC | Sequencing read quality assessment. | Subjects and methods / RNA-seq alignment |
| featureCounts | Generated exon-based raw gene counts with featureCounts 2.0.0 for 60,617 genes and 4,778 samples after a Nextflow/STAR 2.7.3.a two-pass RNA-seq workflow. | Subjects and methods / RNA-seq alignment |
| GATK | Variant discovery and recalibration. | Subjects and methods / WGS |
| g:Profiler | Functional gene-set enrichment. | Subjects and methods / Gene-level features |
| Nextflow | Analysis pipeline orchestration. | Subjects and methods / RNA-seq alignment |
| QTLtools | Molecular QTL mapping and QC. | Subjects and methods / Quality control of RNA-seq samples |
| Salmon | Transcript abundance quantification. | Subjects and methods / RNA-seq alignment |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Subjects and methods / RNA-seq alignment |
| tximport | Used tximport1.14.2 with AnnotationHub,BiocFileCache and BiocGenerics to obtain transcript- and gene-level count matrices from Salmon1.1.0 GRCh38-cDNA quantifications. | Subjects and methods / Salmon transcript quantification |
| Ensembl VEP | Variant consequence annotation. | Subjects and methods / Genetic variant enrichment calculations |

XML SHA-256: `13a02fb8730969fc33caee388001ecaad9df350a92c96439ca7db88ca49636f8`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P228: Molecular and developmental deficits in Smith-Magenis syndrome human stem cell-derived cortical neural models

American Journal of Human Genetics · 2025-08-28 · [DOI](https://doi.org/10.1016/j.ajhg.2025.07.020) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696504/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| bcl2fastq | Base-call conversion and sample demultiplexing. | Material and methods / Bulk RNA-seq and RT-qPCR |
| Bowtie 2 | Short-read alignment. | Material and methods / Hi-C and data analysis |
| Cell Ranger | Used Cell Ranger count to align droplet reads to human GRCh38 and quantify annotated-gene transcripts per cell; its outputs also supplied later velocity counting. | Material and methods / snRNA-seq and data analysis |
| cooltools | Chromosome conformation analysis. | Material and methods / Hi-C and data analysis |
| DESeq2 | Count-based differential expression analysis. | Material and methods / Bulk RNA-seq and RT-qPCR |
| edgeR | Count-based differential expression analysis. | Material and methods / Hi-C and data analysis |
| Enrichr | Gene-set enrichment. | Material and methods / snRNA-seq and data analysis |
| fgsea | Ran fgsea on genes preranked by t statistics from batch-adjusted pseudobulk limma analyses. | Material and methods / snRNA-seq and data analysis |
| g:Profiler | Functional gene-set enrichment. | Material and methods / Bulk RNA-seq and RT-qPCR |
| HiC-Pro | Hi-C read processing. | Material and methods / Hi-C and data analysis |
| limma | Linear modelling and empirical-Bayes differential analysis. | Material and methods / snRNA-seq and data analysis |
| nf-core | Reusable sequencing analysis pipelines. | Material and methods / Hi-C and data analysis |
| sccomp | Used sccomp to compare cell composition after single-cell preprocessing and annotation. | Material and methods / snRNA-seq and data analysis |
| scDblFinder | Single-cell doublet detection. | Material and methods / snRNA-seq and data analysis |
| sctransform | Applied SCTransform and HarmonyIntegration after scDblFinder doublet handling, followed by 15-PC analysis, clustering and marker testing. | Material and methods / snRNA-seq and data analysis |
| scVelo | RNA velocity analysis. | Material and methods / snRNA-seq and data analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Material and methods / snRNA-seq and data analysis |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Material and methods / Bulk RNA-seq and RT-qPCR |
| Subread suite | Named Subread suite or explicit Subread alignment/index program; distinguish counting-library attribution. | Material and methods / Bulk RNA-seq and RT-qPCR |
| UMAP | Produced an exploratory low-dimensional representation of the measured profiles. | Material and methods / snRNA-seq and data analysis |
| velocyto | Spliced/unspliced counting and RNA velocity. | Material and methods / snRNA-seq and data analysis |

XML SHA-256: `c548343d1317fbd6fcf857ffcd429bb3b2cc4bdc89d0f77206c0589eb4c35ee2`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P229: Adaptive admixture at ACKR1, the Duffy blood group locus, may have shaped Plasmodium vivax prevalence in Oman

American Journal of Human Genetics · 2026-03-24 · [DOI](https://doi.org/10.1016/j.ajhg.2026.02.023) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13078719/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Subjects and methods / Global ancestry inference and principal-component analysis |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Subjects and methods / Global ancestry inference and principal-component analysis |
| BEDTools | Genomic interval manipulation and overlap. | Subjects and methods / Read mapping and variant calling |
| BWA | DNA read alignment. | Subjects and methods / Read mapping and variant calling |
| Eagle | Genotype phasing. | Subjects and methods / Local ancestry inference with RFMix v.2 |
| GATK | Variant discovery and recalibration. | Subjects and methods / Read mapping and variant calling |
| muscat | Sample-aware single-cell differential state. | Subjects and methods / Sample collection |
| PLINK 2 | Used for genetic principal components. | Subjects and methods / Global ancestry inference and principal-component analysis |
| RFMix | Local ancestry inference. | Subjects and methods / Local ancestry inference with RFMix v.2 |
| SAMtools | Alignment manipulation and QC. | Subjects and methods / Read mapping and variant calling |
| smartpca | Genetic ancestry principal components. | Subjects and methods / Global ancestry inference and principal-component analysis |

XML SHA-256: `9fd25a19989d361f91180bd6a64ce9e9c9506acabca080bef5ba1db23eb1877b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P230: De novo missense variants in HDAC3 leading to epigenetic machinery dysfunction are associated with a variable neurodevelopmental disorder

American Journal of Human Genetics · 2024-07-23 · [DOI](https://doi.org/10.1016/j.ajhg.2024.06.015) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339613/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Exome Germline Single Sample | Single-sample exome preprocessing and germline gVCF workflow. | Subjects and methods / Sequencing analysis |
| MaxQuant | Mass-spectrometry proteomic identification and quantification. | Subjects and methods / Mass-spectrometry (MS) analysis |
| PyMOL | Adjusted and visualized the PDB4A69 NCoR2 DAD-domain/HDAC3 structure in PyMOL v2.5.5. | Subjects and methods / Protein modeling |

XML SHA-256: `01e3a3b8b419e5da5cddad02253ade46a72fc64d9a083ced768aa0fc02c9986c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P231: Genome-wide detection of human 5′ UTR variants that impact protein translation

American Journal of Human Genetics · 2026-03-24 · [DOI](https://doi.org/10.1016/j.ajhg.2026.02.020) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087467/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CADD | Variant deleteriousness annotation. | Material and methods / 5ULTRA score training and test datasets |
| SpliceAI | Variant splice-impact prediction. | Material and methods / 5ULTRA development and annotation |
| Ensembl VEP | Variant consequence annotation. | Material and methods / 5ULTRA score training and test datasets |

XML SHA-256: `8b616234e1c89b18dea50c509bd64f903979330d006912b1374914b7a41f5146`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P232: Functional characterization of 2,832 JAG1 variants supports reclassification for Alagille syndrome and improves guidance for clinical variant interpretation

American Journal of Human Genetics · 2024-07-22 · [DOI](https://doi.org/10.1016/j.ajhg.2024.06.011) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339624/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Material and methods / Structural modeling |
| AlphaMissense | Missense variant pathogenicity prediction. | Material and methods / AlphaMissense and CADD analysis |
| CADD | Variant deleteriousness annotation. | Material and methods / Bioinformatic analysis |
| GATK | Variant discovery and recalibration. | Material and methods / Bioinformatic analysis |
| Leiden | Grouped observations into communities using a neighborhood graph. | Material and methods / Curation of benign and pathogenic JAG1 variant control set |
| pbmm2 | PacBio read alignment. | Material and methods / Bioinformatic analysis |
| PyMOL | Generated structural figures using PyMOL v2.5.4 for the human JAG1/NOTCH2 modelling analysis. | Material and methods / Structural modeling |
| SpliceAI | Variant splice-impact prediction. | Material and methods / Curation of benign and pathogenic JAG1 variant control set |
| Ensembl VEP | Variant consequence annotation. | Material and methods / Bioinformatic analysis |

XML SHA-256: `b93bbcabe1199d22864bdf378570a032a491d755a97060012d67dd98791c8ef7`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P233: Single-cell analysis of human fibrous dysplasia bone reveals a fibrotic transcriptome and GNAS variants in endothelial, perivascular, and stromal cells

American Journal of Human Genetics · 2025-08-22 · [DOI](https://doi.org/10.1016/j.ajhg.2025.07.018) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461018/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Cell Ranger | Processed scRNA-seq files with Cell Ranger v3.1.0 to generate expression matrices, and also ran Cell Ranger on targeted GNAS sequencing files before barcode intersection and genotype assignment. | Methods / scRNA-seq data analysis |
| Harmony | Batch integration of cell embeddings. | Methods / scRNA-seq data analysis |
| Monocle | Single-cell trajectory or pseudotime inference. | Methods / scRNA-seq data analysis |
| Seurat | Single-cell QC, normalization, integration, clustering or annotation. | Methods / scRNA-seq data analysis |
| SingleR | Reference-based cell annotation. | Methods / scRNA-seq data analysis |
| SoupX | Ambient RNA contamination correction. | Methods / scRNA-seq data analysis |

XML SHA-256: `bbb6dfe0c3463e42e5fec1b8f58ca3b48fcb6cda5a5d776fe21a98a4d3eeb901`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P234: Focus on single-gene effects limits discovery and interpretation of complex-trait-associated variants

American Journal of Human Genetics · 2026-03-23 · [DOI](https://doi.org/10.1016/j.ajhg.2026.02.022) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087463/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Methods / Colocalization of pcQTL, eQTL, and GWAS hits |
| PEER | Latent confounder estimation. | Methods / Processing of GTEx RNA-seq data |
| PLINK | Used for LD-based variant selection; genetic association analysis. | Methods / Colocalization of pcQTL, eQTL, and GWAS hits |
| SuSiE | Bayesian fine-mapping and credible sets. | Methods / Discovery and fine-mapping of pcQTLs and eQTLs |
| tensorQTL | Molecular QTL mapping. | Methods / Phenotype-level permutation-based FDR |
| Ensembl VEP | Variant consequence annotation. | Methods / PIP-weighted variant annotations |

XML SHA-256: `e9f518b47ea11fda0d58555e269e27a83b3a27a62a1491b07f1c17646ebf4d2f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P235: Structural and genetic diversity in the secreted mucins MUC5AC and MUC5B

American Journal of Human Genetics · 2024-07-10 · [DOI](https://doi.org/10.1016/j.ajhg.2024.06.007) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11344006/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ggtree | Constructed plotted phylogenetic trees in R from the inferred alignments/trees. | Material and methods / Sequence extractions and phylogenetic analyses |
| hifiasm | Haplotype-resolved long-read assembly. | Material and methods / Long-read sequence assembly and QC |
| IQ-TREE | Inferred maximum-likelihood trees with automatic model selection and 1000 bootstraps; also used iqtree2 to estimate coalescence times with 6.4-million-year chimpanzee divergence calibration. | Material and methods / Sequence extractions and phylogenetic analyses |
| Jalview | Visually inspected multiple sequence alignment quality before manually removing misaligned segments. | Material and methods / Sequence extractions and phylogenetic analyses |
| Locityper | Read recruitment and probabilistic WGS locus/haplotype genotyping. | Material and methods / Genotyping of MUC5AC haplogroups in 1KG populations using Locityper |
| MAFFT | Aligned selected intronic/intergenic sequences using global pairwise alignment and 100 iterations before phylogenetic inference. | Material and methods / Sequence extractions and phylogenetic analyses |
| MEME | Motif discovery and scanning. | Material and methods / NHP allele alignments and intronic VNTR analysis |
| minimap2 | Aligned phased HPRC/HGSVC/NHP assemblies to CHM13 using minimap2 2.24 with CIGAR inclusion, secondary alignments suppressed, stated divergence <10% and minimum peak alignment score 25,000. | Material and methods / Sequence extractions and phylogenetic analyses |
| PLINK | Used for LD-based variant selection. | Material and methods / LD block structure and selection detection analyses |
| rustybam | Identified specific-locus coordinates in individual haplotype assemblies after genome alignment. | Material and methods / Sequence extractions and phylogenetic analyses |
| SAMtools | Alignment manipulation and QC. | Material and methods / Genotyping of MUC5AC haplogroups in 1KG populations using Locityper |
| seqtk | Extracted locus sequences from phased genome assemblies after minimap2 alignment to CHM13 and rustybam coordinate identification, for subsequent multiple alignment and phylogenetic analyses. | Material and methods / Sequence extractions and phylogenetic analyses |
| Strobealign | Short-read DNA mapping. | Material and methods / Genotyping of MUC5AC haplogroups in 1KG populations using Locityper |
| SuSiE | Bayesian fine-mapping and credible sets. | Material and methods / Long-read sequence assembly and QC |
| Verkko | Genome assembly. | Material and methods / Long-read sequence assembly and QC |
| Yak | K-mer-based haplotype separation error evaluation. | Material and methods / Long-read sequence assembly and QC |

XML SHA-256: `20c83444997cdaf2df0fa3c4774c3ff01f898ece45ec04463618a0ec5268ffe1`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P236: Exploring the omnigenic architecture of selected complex traits

American Journal of Human Genetics · 2025-08-04 · [DOI](https://doi.org/10.1016/j.ajhg.2025.07.006) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461020/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| MAGMA | Aggregated GWAS summary statistics into MAGMA gene-level z-scores as graph-node features,combined with GTEx7 and blood-atlas expression features; dropped nodes missing any required feature. | Methods / Input data |

XML SHA-256: `49c50282cf2897c9f2968a9aeb392f7f900921855258bd4e73f7f7bab8662b0e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P237: Selective chr21 homolog silencing reveals polymorphisms influence the epigenetic silencing and functional dosage of RWDD2B

American Journal of Human Genetics · 2026-03-13 · [DOI](https://doi.org/10.1016/j.ajhg.2026.02.014) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087474/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| edgeR | Count-based differential expression analysis. | Material and methods / Gene expression analysis across RNA-seq datasets |
| featureCounts | Counted GRCh38 iPSC/differentiated-cell RNA-seq alignments with featureCounts after HISAT2, excluding multi-mapped reads. | Material and methods / Brief description of previous sequencing studies using this iPSC panel and differentiated derivatives |
| HISAT2 | Aligned directional-library RNA-seq from an iPSC panel/differentiated derivatives to GRCh38 with HISAT2 before featureCounts quantification excluding multi-mapped reads. | Material and methods / Brief description of previous sequencing studies using this iPSC panel and differentiated derivatives |

XML SHA-256: `632943002d2be4f3ac5c25410d55d8084c5479c6bee4011804d95a1aef9e08e3`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P238: Splicing-specific transcriptome-wide association uncovers genetic mechanisms for schizophrenia

American Journal of Human Genetics · 2024-06-25 · [DOI](https://doi.org/10.1016/j.ajhg.2024.06.001) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11339621/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| biomaRt | Downloaded Ensembl gene-ontology terms for each query and control gene through biomaRt. | Material and methods / GO analysis |
| FUSION | Transcriptome-wide association and conditional follow-up. | Material and methods / Alternative splicing imputation and association testing with summary statistics |
| GCTA | Heritability, mixed-model association or conditional analysis. | Material and methods / Predictive models of alternative splicing |
| LeafCutter | Intron excision quantification and splicing analysis. | Results / SpliTWAS uncovered more biologically plausible associations than TWAS |
| PLINK | Used for genotype quality control. | Material and methods / Datasets |

XML SHA-256: `9fd7fe14cbd31d5549507415ad91c6c1770f726c6c753ed8f8e32c4afe467ba9`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P239: Genomics-informed drug-repurposing strategy identifies two therapeutic targets for preventing liver disease associated with metabolic dysfunction

American Journal of Human Genetics · 2025-08-01 · [DOI](https://doi.org/10.1016/j.ajhg.2025.06.014) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12335671/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| AlphaFold | Used predicted protein structures in the study's structural analysis. | Methods / Protein preparation for molecular docking |
| PrediXcan | Genetically predicted expression association. | Methods / Predicting gene-expression profiles of MASLD genes |
| TwoSampleMR | Instrument harmonization and two-sample Mendelian randomization. | Methods / Evaluation of drug candidates |

XML SHA-256: `3f91ce277adbee318777e429e6c70c6365f3f0e4b1d6f0d182d12fa534548c0c`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P240: Inclusion bias affects common variant discovery and replication in a health-system linked biobank

American Journal of Human Genetics · 2026-03-10 · [DOI](https://doi.org/10.1016/j.ajhg.2026.02.011) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087405/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| FlashPCA | Genetic principal components. | Subjects and methods / Genetic and phenotypic data collection and preprocessing |
| PLINK | Used for genetic association analysis. | Subjects and methods / Replication of variant-level associations with select phenotypes |
| SBayesR | Bayesian mixture polygenic score estimation. | Subjects and methods / Phenome-wide association studies using PGSs |

XML SHA-256: `3cd64ce1cee66f9120aed378f77ea739c9e019906cc4c4cadaff26f22ca9e52e`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P241: Truncated variants of MAGEL2 are involved in the etiologies of the Schaaf-Yang and Prader-Willi syndromes

American Journal of Human Genetics · 2024-06-21 · [DOI](https://doi.org/10.1016/j.ajhg.2024.05.023) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11267527/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Enrichr | Gene-set enrichment. | Material and methods / Computational analyses |

XML SHA-256: `a117b4d635a4a6109167099c7872c18198e39b3bce412647df3e889760e0d8ee`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P242: Sparse modeling of interactions enables fast detection of genome-wide epistasis in biobank-scale studies

American Journal of Human Genetics · 2025-07-29 · [DOI](https://doi.org/10.1016/j.ajhg.2025.07.004) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461027/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| CrossMap | Used CrossMap together with GenomicRanges to map UKB SNPs to DHS genomic intervals for functionally informed marginal-epistasis analysis. | Material and methods / Software tools and data sources |
| FAME | Ran FAME as a marginal-epistasis comparator on the same simulated SNPs/traits, with100 stochastic trace draws; software was archived at commit prefixcfdd03f dated2024-05-07. | Material and methods / Simulation studies |
| GenomicRanges | Used GenomicRanges alongside CrossMap to map UKB SNPs to DHS intervals when constructing epistasis masks. | Material and methods / Software tools and data sources |
| mvMAPIT | Used the R mvMAPIT implementation of MAPIT as a comparator in marginal-epistasis analyses; defaults were used unless otherwise stated. | Material and methods / Software tools and data sources |
| PLINK | Used the generically named PLINK --hwe 0.000001 step to remove HWE-deviating SNPs during cohort-specific UKB QC. The surrounding MAF and complete-call filters explicitly name PLINK2. | Material and methods / Preprocessing the UK Biobank |
| PLINK 2 | Used PLINK 2.0 --maf 0.01 and --geno 0.00 in cohort-specific UKB variant QC, after removing structural and AT/CG variants. The intervening --hwe 0.000001 step is called PLINK without restating its version. | Material and methods / Preprocessing the UK Biobank |
| Sparse marginal epistasis (SME) | Applied sparse marginal epistasis: each focal SNP’s interactions were restricted by external functional masks, using a variance-component model and stochastic trace computations. Reported simulation settings used100 random vectors,100-SNP blocks and shared draws across25 focal SNPs. | Material and methods / The SME test |

XML SHA-256: `35ab6372dcbd6d8b49da5fd2ad4cf3a9d81dcc12d4d86f2dee7fcda17bfa519a`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P243: Genome-wide analyses reveal a potential role for the MAPT, MOBP, and APOE loci in sporadic frontotemporal dementia

American Journal of Human Genetics · 2024-06-17 · [DOI](https://doi.org/10.1016/j.ajhg.2024.05.017) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11267522/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| Eagle | Genotype phasing. | Subjects and methods / Imputation |
| FUMA | Functional mapping, annotation and gene prioritization. | Subjects and methods / Functional analysis |
| LDSC | Used for SNP heritability estimation. | Subjects and methods / Heritability and genetic correlation analyses |
| METAL | GWAS meta-analysis. | Subjects and methods / Association analyses |
| Minimac | Genotype imputation. | Subjects and methods / Imputation |
| PLINK | Used for genotype quality control; genetic principal components; genetic association analysis. | Subjects and methods / Association analyses |
| UCSC liftOver | Converted cohortI genotype coordinates from hg18 to hg19 using the reported LIFTOVER tool before Michigan-server Minimac4/HRC imputation. | Subjects and methods / Imputation |
| Ensembl VEP | Variant consequence annotation. | Subjects and methods / Association analyses |

XML SHA-256: `05908fb2da8a957ed2e08dd47a39928b89b803dd503c551eefc7d84fa5782752`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P244: Sparse matrix factorization robust to sample sharing across GWASs reveals interpretable genetic components

American journal of human genetics · 2025-07-28 · [DOI](https://doi.org/10.1016/j.ajhg.2025.07.003) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461026/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ashr | Adaptive effect-size shrinkage. | Methods / UKBB analysis across 137 diverse GWASs / GLEANR factor evaluation / SNP gene mapping and gene set enrichment analysis. |
| Enrichr | Gene-set enrichment. | Methods / UKBB analysis across 137 diverse GWASs / GLEANR factor evaluation / SNP gene mapping and gene set enrichment analysis. |
| LDSC | SNP heritability, genetic correlation or functional partitioning. | Methods / GLEANR model |
| PLINK | Used for LD-based variant selection; genetic association analysis. | Methods / UKBB analysis across 137 diverse GWASs / SNP selection |
| PLINK 2 | Used for genotype quality control; LD-based variant selection; genetic association analysis. | Methods / FinnGen and UKBB cross-cohort analysis / Study selection |
| SAIGE | Reused SAIGE-generated FinnGen/PheWeb and Pan-UKBB GWAS summaries for GLEANR cross-cohort factorization and137-phenotype UKBB analysis. | Methods / FinnGen and UKBB cross-cohort analysis / Study selection |

XML SHA-256: `ce8b5567a13d32a8b07666f10843f116c6f0a1d530456cfc74c40df2a1e0f2ec`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P245: A scalable and robust variance components method reveals insights into the architecture of gene-environment interactions underlying complex traits

American Journal of Human Genetics · 2024-06-11 · [DOI](https://doi.org/10.1016/j.ajhg.2024.05.015) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11267529/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| GCTA | Used for genotype quality control; LD-based variant selection. | Material and methods / Estimating GxE in the UK Biobank |
| LDAK | SNP heritability and polygenic modelling. | Results / Estimating GxE heritability from imputed SNPs |
| LDSC | Used for annotation-stratified genetic enrichment. | Results / Partitioning GxE heritability across tissue-specific genes |

XML SHA-256: `b9fa462033adb20912de4ac0ef5c2225048ffc2606b045c11524f8b629347f64`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P246: A calcium-sensing receptor allelic series and underdiagnosis of genetically driven hypocalcemia

American Journal of Human Genetics · 2025-07-14 · [DOI](https://doi.org/10.1016/j.ajhg.2025.06.013) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12414667/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| coloc | Shared-signal colocalization analysis. | Subjects, material, and methods / In vitro and computational analyses |
| Hail | Distributed genotype QC and association. | Subjects, material, and methods / UKB |
| KING | Genetic kinship and relatedness estimation. | Subjects, material, and methods / MGB Biobank |
| PLINK 2 | Used explicitly named PLINK v.2.00a3.1LM alongside HAIL v.0.2.78 for QC of qualifying CASR variants in the MANE transcript within UKB. | Subjects, material, and methods / UKB |
| SKAT | Rare-variant sequence-kernel association. | Subjects, material, and methods / In vitro and computational analyses |
| SKAT-O | Adaptive burden/kernel rare-variant testing. | Subjects, material, and methods / In vitro and computational analyses |
| SuSiE | Bayesian fine-mapping and credible sets. | Subjects, material, and methods / In vitro and computational analyses |

XML SHA-256: `ee8566722fcb23f987013f28182d693347f6013ae3df653212feb758f564a5ea`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P247: Locityper enables targeted genotyping of complex polymorphic genes

Nature Genetics · 2025-10-17 · [DOI](https://doi.org/10.1038/s41588-025-02362-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597825/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| BCFtools | Variant manipulation, calling, normalization and filtering. | Methods / Benchmarking Locityper |
| Locityper | Read recruitment and probabilistic WGS locus/haplotype genotyping. | Results / Locityper accurately genotypes challenging loci / Locityper outperforms variant calling pipelines |
| minimap2 | Used minimap2 for long-read mapping within Locityper WGS preprocessing against a predefined background region for insert-size, error-profile and read-depth modeling. | Methods / WGS dataset preprocessing |
| SAMtools | Alignment manipulation and QC. | Methods / Benchmarking Locityper |
| Sniffles | Long-read structural variant discovery. | Results / Locityper accurately genotypes challenging loci / Locityper outperforms variant calling pipelines |
| Strobealign | Short-read DNA mapping. | Methods / WGS dataset preprocessing |
| WhatsHap | Read-based haplotype phasing. | Methods / Benchmarking Locityper |

XML SHA-256: `3424ff42c2a02975a58df7a566c74b39db78eecd4b194f35ce75c4ed4c3a450b`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P248: Pangenome-resolved structural variation drives adaptation and trait evolution in cucumber

Nature Genetics · 2026-07-21 · [DOI](https://doi.org/10.1038/s41588-026-02682-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447089/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| ADMIXTURE | Population ancestry mixture inference. | Methods / Phylogeny and population structure |
| Beagle | Imputed missing SNP genotypes before cucumber GEMMA GWAS with the explicitly reported 21Jan17.6cc Beagle build. | Methods / SNP- and SV-based GWAS |
| BEDTools | Genomic interval manipulation and overlap. | Methods / Centromere and telomere identification |
| clusterProfiler | Performed GO enrichment following the RNA-seq/eQTL/TWAS analysis and differential-gene selection with adjusted P below 0.05 and absolute log2 fold change at least one. | Methods / RNA-seq, eQTL and TWAS analysis |
| coloc | Shared-signal colocalization analysis. | Methods / RNA-seq, eQTL and TWAS analysis |
| DESeq2 | Count-based differential expression analysis. | Methods / RNA-seq, eQTL and TWAS analysis |
| DIAMOND | Protein sequence alignment. | Methods / Repeat and protein-coding gene annotations |
| fastp | Read filtering and adapter trimming. | Methods / SNP and InDel calling of 422 accessions |
| GATK | Called SNPs and small indels with HaplotypeCaller, then used separate SNP QD/FS/MQ/MQRankSum/ReadPosRankSum and indel QD/FS/ReadPosRankSum thresholds. Non-biallelic variants and variants below reported depth 5 were excluded afterward. | Methods / SNP and InDel calling of 422 accessions |
| GCTA | Used for genetic principal components. | Methods / Phylogeny and population structure |
| GEMMA | Mixed-model association or variance components. | Methods / SNP- and SV-based GWAS |
| HiC-Pro | Hi-C read processing. | Methods / Chromatin interaction analysis |
| hifiasm | Haplotype-resolved long-read assembly. | Methods / Long-read sequencing, assembly and quality assessment |
| HISAT2 | Aligned strand-specific RNA-seq from 29 reference-cultivar tissues to each of 125 cucumber assemblies with HISAT2 2.2.1. | Methods / Repeat and protein-coding gene annotations |
| InterProScan | Protein domain and functional annotation. | Methods / Reannotation and classification of nucleotide-binding resistance genes |
| IQ-TREE | Maximum-likelihood phylogenetic tree and divergence analyses. | Methods / Phylogeny and population structure |
| Merqury | Assembly k-mer completeness assessment. | Methods / Long-read sequencing, assembly and quality assessment |
| minimap2 | Aligned high-quality long reads to cucumber genome assemblies with minimap2 2.17-r941 as an assembly-integrity assessment. | Methods / Long-read sequencing, assembly and quality assessment |
| pbmm2 | PacBio read alignment. | Methods / SV-based graph pangenome construction and SV genotyping |
| Picard | Alignment QC, duplicates and sequence processing. | Methods / SNP and InDel calling of 422 accessions |
| PLINK | Used for LD-based variant selection. | Methods / RNA-seq, eQTL and TWAS analysis |
| RepeatMasker | Repetitive sequence annotation. | Methods / Repeat and protein-coding gene annotations |
| Sniffles | Long-read structural variant discovery. | Methods / SV-based graph pangenome construction and SV genotyping |
| STAR | Splice-aware bulk RNA read alignment and optional raw gene counts. | Methods / RNA-seq, eQTL and TWAS analysis |
| StringTie | Transcript assembly and quantification. | Methods / Repeat and protein-coding gene annotations |

XML SHA-256: `6d73b939291dc5f2f13f1da8a89a7835c4195e4b38553c6c08410107d61400e5`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P249: Genotyping sequence-resolved copy number variation using pangenomes reveals paralog-specific global diversity and expression divergence of duplicated genes

Nature Genetics · 2025-10-17 · [DOI](https://doi.org/10.1038/s41588-025-02346-4) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597824/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| DESeq2 | Count-based differential expression analysis. | Methods / Expression analysis of pangenome-derived alleles / Expression correction |
| Locityper | Read recruitment and probabilistic WGS locus/haplotype genotyping. | Methods / Benchmarking of genotyping / Comparison of genotyping results to pangenome assemblies |
| PEER | Latent confounder estimation. | Methods / Expression analysis of pangenome-derived alleles / Expression correction |
| PLINK 2 | Computed genetic PCs for GEUVADIS samples using PLINK version 2 with default settings on chromosome-1 genotypes, then used those PCs in expression correction. | Methods / Expression analysis of pangenome-derived alleles / Expression correction |

XML SHA-256: `1a24a8d9791bc802b8b8d4a99d547489493d8a2cf041582c5f8b37a9ebe3ac9f`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).

## P250: Disease prediction with multi-omics and biomarkers empowers case–control genetic discoveries in the UK Biobank

Nature Genetics · 2024-09-11 · [DOI](https://doi.org/10.1038/s41588-024-01898-1) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11390475/)

| Tool | How used | Evidence location |
| --- | --- | --- |
| biomaRt | Annotated the closest gene for GWAS results using Ensembl GRCh37 BioMart through the biomaRt package. | Methods / GWAS analysis on case–control cohorts |
| PLINK 2 | Used for genotype quality control; LD-based variant selection. | Methods / GWAS analysis on case–control cohorts |
| REGENIE | Ran REGENIE 3.1 binary GWAS for 20 phenotypes across baseline and MILTON-defined cohorts, using QCed LD-pruned array markers for step 1 and imputed SNPs with INFO >0.7/MAC >=400 in step 2; approximate Firth controlled imbalance. | Methods / GWAS analysis on case–control cohorts |

XML SHA-256: `82d090d17841f2642bebd8890f39681ec9ce41c4710f53bcd44ada946fc418c9`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).
