# Source-review checkpoint: newly recognized software and Rphenograph candidate

**Source-review checkpoint (2026-11-13): 429 software families, 2,668 paper-tool observations, 1,079 reviewed uses; 103 new scoped integrations and 267 missing tools.** The [catalog index](omics-2023-2026/README.md) is authoritative for later totals; this guide preserves this checkpoint. This source correction adds 22 names and 186 observations across the unchanged 250-paper cohort. Every previous 2,482 observation and 1,055 review is retained without modification. No new implementation badge is awarded. The full goal and paper-wide semantic review remain open.

**SKIP3: skip only analyses that depend on an unavailable or forbidden required runtime, license, download, installation or execution. FAIL2: admit no result on integrity, input, numeric, resource or process failures.** Keep complete inputs, status, WARNINGS.md, original logs and unadmitted models. Correct the named cause and retry NEW output. Acquisition-only software requires the corresponding instrument/vendor runtime; supplied downstream data can be processed by separately qualified methods. Cell Ranger remains excluded at the user's request.

## Rphenograph status at this checkpoint

A complete, tested **Rphenograph 0.99.1 development candidate** and native Java coefficient implementation are now available. Read the [versioned qualification/build/runtime guide](RPHENOGRAPH099_WORKFLOW_DEVELOPMENT.md) and [complete evidence](rphenograph099-workflow-development/README.md). Those documents retain their qualification-time catalog totals; the current totals are stated above.

**P040 reports 0.1.1 with k=40. That package identity remains unresolved.** The 0.99.1 candidate does not substitute for the reported edition, infer manual annotations or earn a paper-specific coverage badge. The source's k1 error and empty graph with NaN modularity remain retained and unadmitted. Candidate qualification includes 863 original C++ cases/105,921 rows on three platforms, five full original controls, four complete independent graph/community/component comparisons, 86 packaged checks, eight installer refusals, two private source installations and 80 public saved replay checks. The original model RDS files are retained; their raw bytes differ while complete public graph/community state is strictly identical. No raw-model bytewise identity or biological validation is claimed.

The old Rphenograph intake and review notes remain historical records. The current inventory describes the available 0.99.1 candidate and the unresolved reported edition separately. No old scientific code, source verifier or original control is changed by this amendment.

## P040 source roles

Source: [the P040 primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645638/), DOI 10.1038/s41586-026-10954-1. The evidence paragraph numbers use the project's fixed primary-text parser. Each has an exact SHA-256 anchor in the records. The table paraphrases source roles; full article paragraphs remain in the local XML cache and are not redistributed as project software.

| Software | Reported version | How it was used; unresolved details | Primary paragraph |
| --- | --- | --- | --- |
| ABI SDS software | 1.5.1 | ABI SDS1.5.1 determined qPCR Cq values for ACTB and HPRT1 before the separate reported geNorm normalization step. The licensed runtime and instrument measurements are not supplied. | 51 |
| CellProfiler | 4.2.5 | Cropped IMC images for supervised pixel classification, then generated cell masks, segmented images and extracted single-cell staining measurements in the named IMC segmentation workflow. | 27 |
| CyTOF software | 7.1 | CyTOF software7.1 acquired IMC images on the Hyperion Imaging System. This is an acquisition-software role; no separate offline analytical procedure or instrument control is implemented here. | 26 |
| cytomapper | not reported | Used for marker-based phenotype gating and visual validation of labels on cell masks with image marker expression; a separate paragraph explicitly reports plotPixels for channel visualization. The gating function and package version are not reported. | 29 |
| DeepCell/Mesmer | 0.4.1 | DeepCell/Mesmer provided automated cell segmentation in the steinbock validation workflow using stated nuclear and cytoplasmic markers. TissueNet is described as its training-data provenance; author-side model retraining is not established. Preserve the reported Mesmer0.4.1 literal. | 27 |
| BD FACSDiva | 9 | BD FACS Diva9 accompanied acquisition/analysis on the FACSymphony A5 in the flow-cytometry workflow. Exact software commands and instrument configuration are not reported. | 38 |
| Fiji/ImageJ | 1.54t | Generated composite images of selected channels with reported ImageJ core version1.54t in Fiji; removed outlier pixels, applied median or Gaussian filtering at reported0.5-pixel radius, and scaled contrast. Exact outlier/scaling settings and the Fiji distribution are not recovered. | 31 |
| FlowJo | 10.0 | Used FlowJo10.0 to analyze flow-cytometry data from the TDLN T-cell stimulation assay. Exact FlowJo workspace/gating settings are not reported. | 39 |
| geNorm | not reported | Used ACTB and HPRT1 reference genes to derive a normalization factor for PLA2G2D qPCR, then expressed fold changes relative to controls. The text describes a geometric mean of Cq values; the exact software and input representation remain unresolved and must not be silently reinterpreted. | 51 |
| GeomxTools | 3.10.0 | Used Q3 normalization and linear mixed models for differential expression in GeoMx whole-transcriptome spatial profiling, after the reported segment filtering; the statistical-analysis paragraph independently names its LMM role. | 32 |
| GEPIA2 | not reported | Used the GEPIA2 TCGA browser to calculate Pearson correlations and associated P values between PLA2G2D and cytokine expression across tumor types, with stated subtype/isoform selection for plotting. | 34 |
| ggpubr | 0.6.0 | Used stat_compare_means for Wilcoxon signed-rank and unpaired t-test comparisons, and stat_cor for Pearson correlation coefficients. Preserve the reported test choices; figure-specific calls and plotting settings are not recovered. | 54 |
| GraphPad Prism | 10 | Used Prism10 for statistical analyses of flow cytometry, tumor growth, survival and IHC/immunofluorescence. The paragraph states paired/unpaired t-tests and Wilcoxon comparisons where appropriate but does not recover every figure-specific Prism procedure or project. | 54 |
| ilastik | 1.4.0 | Supervised pixel classification of nucleus, cytoplasm and background from selected IMC markers, trained on cropped images from multiple patient ROIs; its stacks were upscaled before segmentation. | 27 |
| Bodenmiller IMC segmentation pipeline | not reported | Named the Bodenmiller IMC segmentation pipeline for conversion of MCD/text into TIFF, hot-pixel filtering, CellProfiler cropping/segmentation and ilastik classification. Exact pipeline source revision and orchestration settings remain unresolved. | 27 |
| imcRtools | 0.1.8 | Used buildSpatialGraph with Delaunay triangulation and maximum distance20; testInteractions compared cell-type interaction counts with label-permutation nulls and interaction/avoidance scores; aggregateNeighbors counted phenotypic neighbors before k-means(k10); minDistToCells supported distances and dyad neighborhoods at15 or30micrometers. | 30 |
| Metascape | 3.5 | Performed pathway enrichment on differentially expressed genes from GeoMx spatial profiling and bulk T-cell RNA-seq. The bulk workflow reports Metascape3.5; detailed enrichment settings are not recovered. | 49 |
| QuPath | 0.5.1 | Used QuPath0.5.1 positive-cell detection on brightfield H-DAB slide ROIs with stated stain vectors, resolution, background/nuclear/area thresholds, cell expansion and DAB positivity thresholds0.08/0.16/0.24; exported ROI measurements for tissue/patient summaries. | 36 |
| rrscale | 1.0 | The paper attributes the inverse-hyperbolic-sine transformation of IMC measurements to the asinh function in rrscale1.0. Its precise package source and any cofactor are not recovered; do not silently substitute a similarly named package or another transform. | 28 |
| rstatix | 0.7.2 | Used a two-sided t-test for differential IMC cell abundances. The variance assumption, exact call and comparison grouping are not reported. | 54 |
| steinbock | 0.16.3 | Validated the IMC segmentation strategy using the authors-provided Linux Docker workflow: hot-pixel preprocessing at threshold50, Mesmer segmentation with stated nuclear/cytoplasmic markers, and per-object intensities and spatial properties. | 27 |
| UCSC Xena | not reported | Obtained TCGA melanoma RSEM-normalized expression and curated phenotype data from UCSC Xena, followed by the stated log2(x+1) transformation. This is data reuse; it does not establish a new RSEM execution by the authors. | 34 |

Cytomapper has two separate reported roles: marker-based gating/visual phenotype checking in paragraph29, and explicit plotPixels image visualization in paragraph31. Covering visualization alone would leave its gating role unresolved. GeomxTools has supporting LMM evidence in paragraph54; Metascape has supporting spatial-enrichment evidence in paragraph32.

The original cytomapper **1.24.0** source was recovered from Bioconductor release branch RELEASE_3_23, commit `f3e2850ae4ca6df9810eecf6de1b7755160da7c8`. Its DESCRIPTION declares GPL>=2 and R>=4.0. P040 reports no cytomapper version or gating function. This is source intake only: no cytomapper runtime was executed or qualified and no image/gating result is admitted. `CYTOMAPPER_SOURCE.json` retains all 553 source-member fingerprints and the complete archive fingerprint for future implementation.

TissueNet is reported as Mesmer training-data provenance, not as evidence of author-side model retraining or an additional executable software use. R is a platform language; instrument models, antibodies, staining kits and named reagents are not additional analysis-software entries merely because a model/catalogue number appears. Explicitly named acquisition/Cq-calling programs (CyTOF software, FACSDiva and ABI SDS) are retained with their distinct roles. This Methods review does not prove that every tool in every part of the paper or supplements has been found.

## Cohort census and review limits

All 250 exact cached XML sources were searched for the 22 newly recognized names, with DOI and existing XML fingerprint checks. The census contains 186 distinct paper-tool pairs and 273 named-tool/paragraph matches. Fiji/ImageJ appears in 51 papers, GraphPad Prism in 46 and FlowJo in 27. Existing 445 matcher definitions are retained, including 38 without observations; 22 definitions are appended. The only previous registry metadata update describes the current Rphenograph candidate; all prior name/pattern/role matching semantics are unchanged.

The individual use review covers 22 P040 observations plus two explicit author-inventory statements in [P082](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631769/), paragraph64. The latter declares GraphPad Prism9.3.1 and FACSDiva used, but does not recover their analytical functions; the repeated statement at paragraph71 is a mirror, not an independent use. The other 162 new observations retain named-source pointers and explicitly require individual usage-summary review. Overall 1,589 observations remain without individual use reviews.

P250's DeepCell, Inc. mention is a competing-interest company disclosure. It is excluded from software uses, with the source identity and negative matcher control retained. The updated name pattern does not turn a company disclosure into a Mesmer execution.

## Verify without executing analysis

From the repository root:

```powershell
python tools/literature/p040_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-p040-check
```

Add `--rescan-sources` to reread all 250 exact XML files. Missing/forbidden XML prerequisites skip that dependent rescan with SKIP3. The default verifies retained source evidence, live scientific code hashes, complete candidate model/archive integrity, the exact append-only catalog transition and historical metadata reconstruction. It performs no original analysis, software installation, R fitting or RDS loading and authenticates no producer.

See [the catalog evidence](p040-software-catalog/README.md), [the current catalog](omics-2023-2026/README.md) and [warnings](omics-2023-2026/WARNINGS.md). Historical generators and qualification snapshots must not overwrite the cumulative current catalog. Missing implementations, unrecovered editions/settings and open source-semantic reviews remain work toward the full 250-paper goal.
