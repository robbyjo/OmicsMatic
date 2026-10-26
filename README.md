# JLinAlg

JLinAlg is a Java library and command-line toolkit for statistical analysis,
including regression, mixed models, omics association, and meta-analysis.
It uses [JDistlib](https://github.com/robbyjo/JDistlib) for numerical computation.
Rare-variant APIs and the `rare-meta` CLI include Burden, SKAT, SKAT-O,
ACAT-V, and canonical six-component ACAT-O tests.

The source-build [250-paper omics/GWAS review](docs/literature/omics-2023-2026/README.md)
catalogs recent Nature, Nature Genetics and AJHG tool use and tracks missing
integrations. Open the [searchable local catalog](site/literature-review.html)
and its [warnings and required actions](docs/literature/omics-2023-2026/WARNINGS.md).
The [enrichment guide](docs/literature/omics-2023-2026/ENRICHMENT_WORKFLOWS.md)
covers native Java running scores and typed original clusterProfiler, fgsea and
legacy DOSE workflows, with explicit testing families, engine differences,
private runtime setup and actionable skip/error reports. VIPER/aREA validation
remains held pending applicable license eligibility.
New source commands include `signature-score`, `intervals`, `cell-embed`, `sequence-qc`, `sequence-tools`, `alignment-tools`, `hts-tools`, `read-align`, and `gatk-tools`;
`cell-embed --method tsne` provides native exact 2D t-SNE without Python.
The [native FLASH guide](docs/literature/omics-2023-2026/FLASH_WORKFLOWS.md) adds
`flash-reads` for Java paired-read merging without external software, with actual
original-program references, explicit encoding, header mappings and failure recovery.
The [PLINK source review](docs/literature/omics-2023-2026/PLINK_SOURCE_REVIEW.md)
separates release families, cohort-specific QC/GWAS/score settings and data-format
mentions, with explicit limits on the original reference work and pending adapter.
The [native genotype-score guide](docs/literature/omics-2023-2026/GENOTYPE_SCORE_WORKFLOWS.md)
adds Java supplied-weight BED scoring with explicit effect/other alleles,
frequencies, missing policies and score denominators. It includes a small
synthetic input bundle and tested Java API example. Failures quarantine partials
and explain recovery; original QC/PCA/GWAS adapters remain in progress.
The [DNA motif guide](docs/literature/omics-2023-2026/FIMO_WORKFLOWS.md) adds
native Java `motif-scan` without external software and a tested original FIMO
P/q adapter, `fimo-tools`. Explicit backgrounds, cohorts and P/q choices,
dependency skips, stored-hit truncation, uncomputed-q placeholders and exact
recovery instructions are prominent.
The [original Trim Galore guide](docs/literature/omics-2023-2026/TRIM_GALORE_WORKFLOWS.md)
adds tested original 0.6.7/0.6.10 adapter/quality workflows and optional FastQC,
with explicit encoding, UTF-8 input recipes and actionable skip/error reports.
The [native read guide](docs/trim-galore-native-reads.md) covers Java hard
trimming and ordinary post-Cutadapt finalization without external dependencies.
`sequence-qc` provides pinned Cutadapt, FastQC and MultiQC workflows with retained reports and actionable failure/skip diagnostics.
`cell-embed` also accepts supplied weighted graphs for Leiden/Louvain. `alignment-tools` runs four typed, checksum-pinned Picard operations and prominently reports validation findings and recovery actions.
The [SAMtools/BCFtools guide](docs/literature/omics-2023-2026/HTS_WORKFLOWS.md) covers native FASTA indexing and eleven tested alignment, pileup and variant workflows with private Windows runtime setup and explicit calling assumptions.
The [BWA/Bowtie 2 guide](docs/literature/omics-2023-2026/ALIGNMENT_WORKFLOWS.md) adds reference-bound read mapping with tested paired, local, dovetail and split-read behavior, plus clear installation/build recovery steps.
The [native sequence guide](docs/literature/omics-2023-2026/NATIVE_SEQUENCE_WORKFLOWS.md) adds Java subsampling, length/quality trimming, indexed extraction and terminal-repeat scoring with independent seqtk references and actionable warnings.
The [GATK guide](docs/literature/omics-2023-2026/GATK_WORKFLOWS.md) covers germline calling, gVCF combination/genotyping, annotation filters and explicit model/missing-value contracts. Cutadapt empty outputs and source FastQC findings now remain prominent through MultiQC aggregation.
The [STAR guide](docs/literature/omics-2023-2026/STAR_WORKFLOWS.md) adds native GTF checks, splice alignment, two-pass modes and raw strand-specific gene counts, with tested Windows source fixes and prominent filter/dependency warnings.
The [native genomic-signal guide](docs/literature/omics-2023-2026/GENOMIC_SIGNAL_WORKFLOWS.md) adds length-weighted bedGraph summaries and state/genome enrichment with explicit missing-data, reference and pseudocount choices.
The [native track-peak guide](docs/literature/omics-2023-2026/TRACK_PEAK_WORKFLOWS.md) adds threshold/gap/span calling and plateau summits, checked against actual MACS track-command references. Alignment-based MACS calling remains unavailable on the tested Windows runtime.

The [REGENIE guide](docs/literature/omics-2023-2026/REGENIE_WORKFLOWS.md) adds native Java sample/table/model checks, sealed whole-genome fits, quantitative/binary variant tests and gene masks through a pinned upstream runtime. Phenotype coding, missingness, alleles and correction choices are explicit; unavailable dependencies skip with exact recovery steps.

The [Java coordinate-conversion guide](docs/literature/omics-2023-2026/COORDINATE_LIFT_WORKFLOWS.md) adds explicit chain/reference mapping, reciprocal checks and per-interval unmapped diagnostics without an external runtime. Allele harmonization and complete UCSC/R/CrossMap workflows remain separate.

The [RNA alignment and counting guide](docs/literature/omics-2023-2026/RNA_ALIGNMENT_COUNTING_WORKFLOWS.md) adds featureCounts gene/peak/junction counting, minimap2 read/assembly mapping, HISAT2 RNA alignment, and native Java annotation, splice, PAF and count-normalization helpers. Read/pair units, strand, missing tags, compiler warnings and dependency recovery are explicit.

The [single-cell QC and SCTransform guide](docs/literature/omics-2023-2026/SINGLE_CELL_QC_WORKFLOWS.md) adds native sparse counts, doublet-score and NB residual helpers plus original Scrublet and distinct R VST/Seurat adapters. Exact cell identities, raw versus estimated counts, regression, expression layers and actionable runtime/model warnings are explicit.

The [MAGMA guide](docs/literature/omics-2023-2026/MAGMA_WORKFLOWS.md) adds native gene-window mapping and tested original gene, gene-set, property and conditional/joint models. Build/LD/sample-size contracts, raw zero coding, excluded genes and private runtime terms are explicit.

The [read-trimming guide](docs/literature/omics-2023-2026/TRIMMOMATIC_WORKFLOWS.md) adds thirteen native Java quality/length/MAXINFO steps and original Trimmomatic0.39/0.41 adapter clipping. Step order, forward-only cropping, Phred encoding, four paired/orphan outputs and exact Java/runtime recovery are explicit; older0.32/0.36 parity remains unvalidated.

The [native quantile-normalization guide](docs/literature/omics-2023-2026/QNORM_WORKFLOWS.md) adds Java column normalization, explicit targets/ties, exact raw-integer checks and model warnings. The [native transcript-import guide](docs/literature/omics-2023-2026/TXIMPORT_WORKFLOWS.md) documents Salmon/kallisto/RSEM estimate import, gene summarization and tximport count scaling in Java, with exact IDs, explicit omissions, length-offset warnings and no R runtime requirement. The [deepTools guide](docs/literature/omics-2023-2026/DEEPTOOLS_WORKFLOWS.md) documents native signal arithmetic and eight tested original workflows through Java, including matrices, profiles, heatmaps, BAM summaries and correlations. Reusable model identities, missing/zero policies, ATAC sequence/quality/all-tag loss, figure limitations and dependency recovery are prominent.
It also provides native fixed-span clipping (`--method trim-fixed`); Harmony has an explicit tested R engine for Windows (`--engine r`).
The broader implementation goal remains in progress.

## Choose your degree of AI collaboration

JLinAlg v0.3.7 combines deterministic statistical execution with optional AI
collaboration. Start with one of three worked guides:

| Mode | Who plans and decides | Worked guide |
| --- | --- | --- |
| No AI | The investigator defines and approves a reproducible local workflow. | [Run without AI](docs/vignettes/ai-no-ai.md) |
| AI as an advisor | The built-in, opt-in adapter provides bounded advice through browser handoff or an explicitly selected API; the investigator reviews proposals. | [Collaborate with an advisor](docs/vignettes/ai-advisor.md) |
| AI as a driver | A separately managed agent coordinates existing CLI commands; scientific approval and data-access enforcement remain explicit. | [Coordinate an external agent](docs/vignettes/ai-driver.md) |

The built-in advisor never uploads patient files or grants shell or sample-matrix
access. It uses a bounded allowlisted preview subject to project policy;
aggregate findings and metadata still require disclosure review. External agents
need separate operating-system and network controls. The driver guide describes
a supervised use pattern, not a bundled autonomous agent or privacy certification.
These collaboration modes are distinct from deterministic `analysis.mode` planning.

## Requirements

- JDK 17 or newer.
- Internet access for the first build to download verified dependencies.
- CPU operation needs no accelerator software. Optional GPU backends need
  compatible drivers and runtimes; NVIDIA CUDA uses Toolkit 12.6.
  See [runtime setup](docs/runtime-requirements.md).

## Build

Use the included Gradle wrapper; no separate Gradle installation is required.

```shell
# Linux / macOS
./gradlew check executableJar
```

```powershell
# Windows
.\gradlew.bat check executableJar
```

The self-contained executable is `build/cli/jlinalg-0.3.7.jar`.
[Published releases](https://github.com/robbyjo/JLinAlg/releases) are also
available; newer source-build features may not be in the latest release asset.

## Run an analysis

Fit a linear regression using the included synthetic phenotype table:

```shell
java -jar build/cli/jlinalg-0.3.7.jar --pheno examples/quickstart/phenotype.tsv --id sample --formula "trait ~ age" --out build/quickstart/results.tsv
```

The result contains coefficient estimates, standard errors, and p-values,
with a companion run log. Choose a fresh output path for another run.

## Draw a plot

The `plot` command reads CSV/TSV columns and writes PNG, JPEG, BMP, GIF, TIFF,
SVG, EPS, or PDF. It supports grouped scatter, line, connected-scatter, area,
step, bubble, histogram, density, dot, box, violin, bar, pie, donut, clustered
heatmap, spider/radar, circular bar, parallel-coordinate, dendrogram, treemap,
network, chord, bundled-edge, Sankey, and two-set Venn diagrams. Palettes, line widths and
patterns, marker shapes, and point sizes are configurable from the CLI.

```powershell
java -jar build/cli/jlinalg-0.3.7.jar plot `
  --type scatter --input results.csv --x estimate --y negative_log10_p `
  --group cohort --title "Association results" --out associations.pdf
```

See the [command-line plotting guide](docs/plotting.md) for mappings, themes,
axis and legend controls, missing-value behavior, and chart-specific examples.

Visit the **[JLinAlg website](https://robbyjo.github.io/JLinAlg/)** for the
complete feature guide, **[tutorials and vignettes](https://robbyjo.github.io/JLinAlg/vignettes/)**,
and Java/CLI examples, including [GRM construction](https://robbyjo.github.io/JLinAlg/vignettes/grm.html).
The [searchable scientific citation index](https://robbyjo.github.io/JLinAlg/citations.html)
links each method family to its primary publication.

## Numerical accuracy and performance

Source builds add [replicated single-cell RNA](docs/vignettes/single-cell.md) and
[physical spatial RNA workflows](docs/vignettes/spatial-analysis.md), with sparse
QC, pseudobulk, donor-aware inference, tissue graphs and spatial tests. See the
[validation and remaining scope](docs/cell-spatial-validation.md).

The source build adds [project-over-local YAML configuration](docs/vignettes/project-configuration.md),
[variant annotation, consequence adapters and evidence scoring](docs/vignettes/variant-followup.md),
and [six network workflows](docs/vignettes/network-followup.md), including WGCNA,
GENIE3 and CARNIVAL adapters. These use explicit local/remote data sources and
audited outputs. The study pipeline also offers separately opted-in AI assistance.

The [analysis pipeline specification](docs/vignettes/analysis-pipeline-specification.md)
describes the complete scientific workflow, improved pseudocode, primary and
sensitivity rules, GWAS QC and rare-variant testing, annotation/AlphaGenome
scoring, protein AlphaFold follow-up, AI selection of 3–5 principal findings
and an 8,000-word manuscript budget. It distinguishes current capabilities
from planned extensions. The source branch now enforces MAC >= 20 by default
for CLI genotype scans and resolves explicit study QC/model/manuscript policies;
the [implementation checklist](docs/analysis-pipeline-implementation.md) tracks
the remaining workflow requirements and verification.

The source build includes the [auditable omics research pipeline](docs/vignettes/auditable-omics-pipeline.md):
a minimal versioned study contract, deterministic planning and approval,
matrix- or result-first execution, immutable amendments, hash-chained evidence,
`SESSION_HISTORY.md`, a one-command `study auto` runner, automatic public
GO/Reactome and GENCODE resource resolution through an embedded HTTPS client, a
conditional checksum-verified 1000 Genomes LD download, quantitative
cross-cohort anomaly flags, and an LLM-oriented evidence index with bounded key
findings. Repeating a command resumes verified work and retries unfinished steps;
HTML evidence and manuscript supplement previews have explicit nesting, row and
text budgets; full machine-readable evidence and checksum-linked result files
remain unchanged. Index search covers operation identity, state and summary.
An offline manuscript revision can explicitly declare `editorial_payload: {mode:
omit, rationale: ...}` to retain a full local paper without generating an external
editorial handoff. Omission is audited and prevents editorial submission; the
default 120,000-character payload limit and exact-hash approval remain unchanged.
`--rerun` starts a fresh immutable attempt. Optional data dictionaries, preapproved
sensitivity analyses, audited browser handoff (the default assistance transport),
explicit OpenAI/Gemini or OpenAI-compatible API connections, cited literature review,
and explicitly enabled HTML manuscript drafts extend the same provenance chain. See the
[hands-on tutorial](docs/vignettes/study-pipeline-tutorial.md) and
[runnable two-cohort example](examples/study-pipeline/README.md). Missing
fine-mapping, colocalization, MR, or literature inputs become explicit indexed
blockers while independent evidence branches continue. Autonomous LLM orchestration
and certified submission workflows remain future work.

See [cross-cohort module preservation](docs/vignettes/study-pipeline-tutorial.md#cross-cohort-module-preservation) for frozen-module node/edge/sign transfer across native and supplied graphs.

Source builds add [source-specific GWAS QQ and Manhattan diagnostics](docs/vignettes/study-pipeline-tutorial.md#gwas-qq-and-manhattan-diagnostics),
with declared export coverage, frequency/count/test strata and descriptive
calibration context that preserves original P values and primary inference.

Source builds support [scientific scope checks and partial cohort-network reuse](docs/vignettes/study-pipeline-tutorial.md#scientific-scope-and-missing-cohort-networks):
retain supplied graph scopes, complete only uncovered dataset graphs, or select
an explicit supplied-only policy. Known target/context conflicts hold imports;
original source notes remain local for scientific review.

Source builds also execute [native preranked GSEA](docs/vignettes/study-pipeline-tutorial.md#native-ranked-gsea-and-leading-edge-pathway-networks)
from complete gene rankings or scoped association/meta-analysis results, with
exact/seeded gene-set nulls, leading edges, BH/BY correction and PNG/PDF curves.
The resulting pathways connect to overlap networks, modules and joint module GO.
Supplied analyses can satisfy completed stages while missing work continues.
Phenotype/block-aware GSEA nulls remain separate work; the native correction
differs from Broad's empirical GSEA FDR.

Source builds add [LD-aware GWAS functional-category enrichment](docs/vignettes/study-pipeline-tutorial.md#ld-aware-gwas-functional-category-enrichment)
through overlapping binary-annotation S-LDSC, with a complete calibrated GWAS
source, matched LD/reference moments, SNP QC, target-family correction,
supplied-stage reuse and aggregate finding dossiers. Continuous annotation and
multi-ancestry models, reference construction and real genome-wide acceptance
remain separate work.

The source build adds [held-out risk-score workflows](docs/vignettes/study-pipeline-tutorial.md#held-out-risk-scores-and-missing-related-phenotype-work):
explicit training/tuning/evaluation partitions, quantitative molecular-score
development from cohort matrices or participant tables, complete frozen-model
reuse, external weights/scores, conditional cluster-bootstrap metrics and a
separate related-phenotype correction family. Supplied score evaluation can
remain intact while only missing related-trait tests run. Native binary/survival
development, nested CV and genome-wide LD-aware scoring remain future work.

The source build also adds [native protein structure context](docs/vignettes/study-pipeline-tutorial.md#exact-protein-sequences-structures-and-confidence):
exact sequence/isoform/taxon checks, supplied PDB/mmCIF assessment, versioned
AlphaFold DB retrieval, explicit pLDDT and directional PAE, residue/peptide/variant
context and local PNG/PDF/HTML views. Supplied summaries skip execution; missing
assessments and configured prediction can run independently. Configured-process
fixtures and live retrieval are verified; new AlphaFold inference and biological
structure/interface validation remain unverified.

For hypotheses with two or more result tables, the study pipeline automatically
creates every pairwise effect scatterplot as PNG and PDF, with the Pearson
correlation shown to four decimals in the legend. Pairs sharing a cohort ID are
labeled within-cohort phenotype comparisons; pairs from different cohorts are
labeled cross-cohort comparisons. Discovery/replication operations also create
two-set Venn diagrams. Source builds add
[multiway cohort overlap and registered heterogeneity diagnostics](docs/vignettes/study-pipeline-tutorial.md#multiway-cohort-overlap-and-registered-heterogeneity):
exact membership tables, two/three-set Venn and UpSet PNG/PDF views, frozen
validation families and configurable I-squared or corrected Q-test alerts.
With at least three named cohorts, leave-one-cohort-out analysis runs by default;
registered rules can make it conditional on completed diagnostic alerts or disable
it. Paired influence uses the same eligible features in each comparison and
retains directions, intervals, source counts and source-family adjusted P values.
Supplied overlap/heterogeneity results can complete those stages without refitting.
The registered all-cohort analysis remains primary; an exploratory omission
cannot replace it merely by lowering heterogeneity or increasing hit overlap.
Source builds add [complete colocalization records and regional coverage](docs/vignettes/study-pipeline-tutorial.md#complete-colocalization-records-and-regional-coverage):
pre-restriction source inventories, observed versus candidate matching, position gaps,
all signal pairs and registered prior grids, convergence/fallback and explicit
partial-data grades/caveats. Supplied multi-signal results retain their source fields
without refitting. Colocalization has its own optional feature cap and no default
posterior decision threshold; it never supplies association FDR or causal direction.

The [bioinformatics evidence workflow](docs/vignettes/bioinformatics-evidence.md)
adds audited public-source discovery, reviewed regional fine-mapping/colocalization/MR,
protein and reference-sequence investigation, non-LLM literature search, per-feature
candidate evidence, and local evidence archives. A separate offline synthetic example
exercises the numerical chain; missing scientific inputs remain explicit questions.
MR defaults to all eligible measured transcripts, proteins or probes passing the
configured full-family adjusted-P threshold (BH `FDR < 0.05` by default), independently of
contextual search limits. `analysis.bioinformatics.mr.max_features` is an optional
explicit cap, not a default top-N selection; existing explicit caps remain honored.
Source/assay and instrument prerequisites still apply. This changes future default
selection only and does not regenerate existing study results.
Association harmonization and newly fitted results also receive an audited,
conservative, scale-aware extreme-effect QC screen before comparisons and
downstream analysis. Configure `analysis.harmonization.effect_outliers`; inspect
the exclusion log, skipped-screen warnings and unit/model diagnostics before
interpreting the filtered results. See the [QC policy](docs/vignettes/study-pipeline-tutorial.md#effect-size-quality-control-before-downstream-analysis).

The first [study questions and decisions tranche](docs/vignettes/study-questions-and-decisions.md#implemented-first-tranche)
adds deterministic prerequisite questions, checked batch answers, answer-linked
amendment proposals, branch-specific gates, and stale-answer protection. Exact
questions and answers appear in `QUESTIONS.md`, the research notebook, and the
searchable local index. Optional LLM-suggested questions now enter the same local
review inbox; interactive prompting remains future work. See the [question workflow](docs/vignettes/study-pipeline-tutorial.md#documented-scientist-questions).

The source build adds [Tobit, AFT and ordinal likelihoods](docs/vignettes/censored-ordinal.md)
and [rare-event and survey inference](docs/vignettes/sampling-models.md).
See [regression validation](docs/regression-inference-validation.md) and the
[scientific reference page](docs/CITATIONS.md) for tested scope and assumptions.

Validation includes independent R comparisons and analytic/reference fixtures.
For example, tested SEM coefficient errors were below `7.4e-8`. A synthetic
20,000-variant score meta-analysis ran about **2.46× faster than RAREMETAL**
on the measured workload. These are fixture-specific results, not general
accuracy bounds or speed guarantees.

See the [accuracy and performance tables](docs/verification-and-performance.md),
[numerical audit](docs/advanced-validation.md), and
[rare-variant benchmark details](docs/rare-meta-validation.md) for methods,
measurement conditions, and limitations.
The [ACAT vignette](docs/vignettes/acat-rare-variants.md) covers stable Cauchy
tails, ultra-rare collapsing, R reference fixtures, and the fused ACAT-O path.

The [estimator extension guide](docs/estimator-extensions.md) covers mixed and
ordinal SEM, DWLS/WLSMV, multigroup invariance, ARIMA regression and historical
smoothing, mixed-type kernels, quantile inference, and non-tensor integration.

JLinAlg v0.3.6 adds [LDSC](docs/vignettes/ldsc.md),
[genetically predicted TWAS/PWAS](docs/vignettes/predicted-omics.md),
[genetic-factor GWAS](docs/vignettes/genomic-factor.md), and
[prediction score training and evaluation](docs/vignettes/prediction-scores.md),
each with CLI commands and worked examples. See their
[validation and supported scope](docs/xwas-followup-validation.md).

JLinAlg v0.3.6 also includes [binary probit and a shared GLM/GEE
prediction-and-contrast API](docs/vignettes/predictions-and-contrasts.md),
[individual-level IV/2SLS](docs/vignettes/instrumental-variable-regression.md),
[summary-only conditional-score inference](docs/vignettes/conditional-score-conditioning.md),
and scalable exact diffuse [ARIMA smoothing and parameter-aware forecasts](docs/vignettes/time-series.md).
Each guide states the estimand, validation reference, and important inference
boundaries.

JLinAlg v0.3.6 provides [PCA, standard SVA, AutoSVA, and PEER latent
factors plus ComBat batch adjustment](docs/vignettes/latent-confounders-and-batch.md).
The dedicated `confounders` and `batch-adjust` commands align feature matrices
by sample ID and write reusable factors, adjusted matrices, and audit manifests.
The five preceding additions are CLI-accessible through `--family probit`,
`glm-predict`, `iv-regression`, `conditional-score`, and `arima-regression`.

JLinAlg v0.3.6 provides [empirical-Bayes differential analysis,
coordinate-aware EWAS regions, adaptive/hierarchical multiple testing, and
multiple-imputation inference](docs/vignettes/differential-regions-testing-imputation.md).
The `differential`, `ewas-regions`, `multiple-test`, `multiple-impute`,
and `mi-pool` commands preserve complete testing families, required genome
builds, reproducible imputation streams, and auditable method sidecars. See the
[validation report](docs/inference-workflows-validation.md) for frozen
limma/voom, edgeR, and DESeq2 comparisons and explicit parity boundaries.

[Gene-set enrichment](docs/vignettes/enrichment.md) adds analysis-specific
backgrounds and selection expressions, Fisher ORA, EWAS `gsameth`, BH/BY correction,
and explicit annotation downloads. The vignette includes a runnable synthetic
example, source access requirements, and guidance for overlapping ontologies.

## License

[GNU General Public License, version 3 or later](LICENSE) (`GPL-3.0-or-later`).
