# Literature-driven implementation queue

The implementation goal is active. A catalog entry is not a claim that its upstream tool has been implemented.

User-approved approach: native Java where practical, tested upstream adapters for substantial external tools. Downloads and installations are allowed where the environment permits them. Unavailable dependencies must skip the dependent analysis and produce prominent actionable diagnostics.

Prioritize repeated workflows, scientific input contracts, real upstream validation, packaged CLI behavior, and clear scope. Do not replace specialized methods with similarly named approximations.

| Tool | Papers | Current implementation | Scope and remaining work |
| --- | ---: | --- | --- |
| STAR | 53 | upstream-adapter-validated | STAR 2.7.11b typed bulk RNA index/alignment, raw GeneCounts and distinct Basic or supplied-junction two-pass workflows. Native exact-reference GTF/FASTQ checks, sealed index hashes, primary mate/base/quality audit and explicit strand/filter/overhang choices. Private Windows source build includes one documented transform.outQuant initialization fix; compiler warnings and failed attempts retained. BySJout uses bounded disk-backed SHA-256 content multisets because upstream disallows ordered output. STARsolo/WASP, UMI processing, fusions, legacy versions and full paper pipelines remain open |
| PLINK | 52 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. Separately validated native genotype-score applies supplied weights to resolved diploid human autosomal BED hard calls. Explicit ea/oa and supplied effect-allele frequencies, error/mean-impute/omit policies, sum/average/observed versus denominator counts and all-missing unreportability. Java only. See GENOTYPE_SCORE_WORKFLOWS.md. Typed original GLM/QC/pruning/PCA subsets are available in the development candidate; the broader family retains method-overlap. |
| SAMtools | 51 | upstream-adapter-validated | Native Java FASTA index generation/validation plus SAMtools/HTSlib 1.24 typed BAM selection, coordinate/lexicographic sort, BAI indexing, flagstat, name-sort/fixmate/position-sort/markdup pipeline and textual mpileup. Known selected records, boundary regions, QC counts, duplicate pairs and pileup bases/depths validated. Other commands and published versions remain open |
| Seurat | 49 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| UMAP | 47 | upstream-adapter-validated | UMAP via umap-learn 0.5.12; supplied PCA coordinates; real seeded CLI fixture validated |
| DESeq2 | 47 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Cell Ranger | 44 | missing | No equivalent workflow identified in the current source inventory. |
| BEDTools | 43 | native-subset-validated | Intersection, merge, subtraction, union coverage; canonical headered TSV, half-open coordinates |
| LDSC | 41 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| BWA | 39 | upstream-adapter-validated | BWA 0.7.19-r1273 index, MEM and backtrack aln/samse/sampe with explicit seeds, sample/RG and paired inputs. Unmodified upstream source built privately on Windows with a pinned MSYS2 toolchain. Known coordinates, strands, qualities, pairs and split-read reporting validated. BWA-MEM2 is a separate missing implementation; old-version and complete study-pipeline parity remain open |
| Ensembl VEP | 38 | existing-adapter | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| Picard | 34 | upstream-adapter-validated | Official hash-pinned Picard 3.5.0 typed SortSam coordinate/queryname, MarkDuplicates mark/remove, ValidateSamFile and reference-based CollectAlignmentSummaryMetrics; real SAM/BAM order, duplicates, removal, index, QC errors and alignment counts validated. Other programs/old-version parity remain open |
| Bowtie 2 | 33 | upstream-adapter-validated | Official Bowtie 2 2.5.5 direct small/large index/align binaries, explicit end-to-end/local presets, seeded ordered gzipped single/paired DNA reads and dovetail/insert settings. Sealed reference/index hashes and every primary mate/strand-adjusted sequence/quality checked. No automatic trimming, uniqueness, duplicate or splice processing; older paper versions remain separate checks |
| Harmony | 33 | upstream-adapter-validated | Explicit R harmony 2.0.5 engine validated on Windows: seeded supplied-PCA batch correction, IDs/order and balanced batch/type fixture. Python harmonypy 2.1.0 remains unavailable on Windows after wheel/source failures; engines are not interchangeable coordinate references |
| MACS | 32 | native-subset-validated | Native track-peaks supplied-score threshold/gap/span and plateau-summit subset, checked against 129 actual MACS3 3.1.0 bdgpeakcall runs for positive float32 cutoffs. Explicit reference/missing/precision choices, 64-bit native bounds and actionable warnings. Alignment-based callpeak failed on Windows; HMMRATAC is unavailable. No background/PQ scoring, full old-version, zero/signed-summit or whole-pipeline equivalence |
| GATK | 30 | upstream-adapter-validated | Official hash-pinned GATK 4.7.0.0 HaplotypeCaller with explicit uniform ploidy and Java kernels, CombineGVCFs, GenotypeGVCFs and typed SNP/indel VariantFiltration. Native exact FASTA metadata helper, strict BAM/read-group/reference/gVCF contracts, preserved sites/genotypes/prior filters and explicit missing-annotation policy. Legacy realignment, Mutect2/somatic, BQSR/VQSR, capture/ALT pipelines and old-version parity remain open |
| Leiden | 27 | upstream-adapter-validated | leidenalg 0.12.0 RB configuration partition on explicit weighted union kNN or supplied undirected nodes/edges; seeded real fixtures, original weights and isolates validated |
| BCFtools | 27 | upstream-adapter-validated | BCFtools/HTSlib 1.24 mpileup/multiallelic calling with explicit uniform ploidy, reference normalization/allele splitting, QUAL soft filters, sample selection with allele-count updates, and stats. Known diploid/haploid SNV calls and genotype/allele fixtures validated. No inferred pool/mosaic/sex model or full old-version parity |
| coloc | 27 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| clusterProfiler | 25 | upstream-adapter-validated | Original clusterProfiler4.20.3 supplied-set enricher ORA and GSEA through enrichit0.2.6,explicit multilevel/permute/sample,null/tail/seed/universe/query and complete-family rules. Native Java score/log-hypergeometric/adjustment and original identity/count/leading-edge audits;raw RDS,original values and all omissions retained. Storey q-values are not BH values. No enrichGO/online annotation,old-version or whole-paper parity. See ENRICHMENT_WORKFLOWS.md |
| PLINK 2 | 25 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. Separately validated native genotype-score applies supplied weights to resolved diploid human autosomal BED hard calls. Explicit ea/oa and supplied effect-allele frequencies, error/mean-impute/omit policies, sum/average/observed versus denominator counts and all-missing unreportability. Java only. See GENOTYPE_SCORE_WORKFLOWS.md. Typed original GLM/QC/pruning/PCA subsets are available in the development candidate; the broader family retains method-overlap. |
| FastQC | 24 | upstream-adapter-validated | FastQC 0.13.0 compiled Java application; validated Phred+33 FASTQ, per-file reports, read counts and retained WARN/FAIL findings; official archive checksum and dependency-file hashes retained |
| METAL | 24 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| REGENIE | 23 | upstream-adapter-validated | REGENIE 4.1.3 fit, quantitative/binary single-variant association and max/sum/comphet gene masks through a verified private runtime. Native Java exact FID/IID/coding/column/header checks and sealed phenotype/covariate/LOCO bundles; BED/BGEN/PGEN, explicit allele/build/missingness/RINT and Firth/SPA choices. Burden and RGC SKAT/ACAT/SBAT/GENE_P subset. Sum-mask BED export is unavailable and warned. No inferred binary model, HTP/REMETA, conditional/interaction/multitrait, old-version or full-paper parity |
| Enrichr | 22 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Scanpy | 22 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| deepTools | 21 | upstream-adapter-validated | Native supplied-signal comparison/scaling and eight original 3.5.6 workflows are in the canonical validated executable: BAM coverage,BigWig comparison,ATAC shifting,region matrices,profiles,heatmaps,BAM summaries and correlations. 23 API/CLI tests,4064 original arithmetic references,58 actual upstream workflow calls/20 independent checks and the1593-test full gate passed. Exact model/reference/sample/region identities,explicit missing/zero/bin/reduction choices and no pickle loading. Constant columns fail with named recovery. ATAC drops SEQ/QUAL/ALL optional tags. GTF/BED12,multiple region groups,clustering,multiBigwigSummary,plotPCA,interactive Plotly,historical and full-paper parity remain open. See DEEPTOOLS_WORKFLOWS.md. |
| MAGMA | 21 | upstream-adapter-validated | Original unmodified MAGMA 1.10 private Windows runtime: annotation, SNP-wise mean/top/multi, raw PC regression, competitive/self-contained sets, continuous properties and conditional/joint models. Native Java strand-aware gene-window mapping, PLINK/GWAS/build/N/identity checks and sealed gene correlations. Audited raw0/1 coding preserves controls and declared quantitative data. No restricted binary/source redistribution, FUMA/R wrappers, PoPS/scDRS, meta/interaction or historical/full-paper parity |
| PyMOL | 20 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| UCSC liftOver | 20 | native-subset-validated | Java HTSJDK basic single-result chain mapping with exact full source/target dictionaries, structural/32-bit checks, explicit minMatch and unmapped retain/error policy, optional target-span cap and exact or bidirectional-overlap round trips. Every input/status retained; no preferred alternative hit, split/BED12, variant allele/REF/indel, R metadata or whole UCSC/CrossMap/rtracklayer/paper equivalence |
| Cutadapt | 19 | upstream-adapter-validated | Cutadapt 5.2 regular 3-prime adapter/quality trimming, fixed 5-prime cut and single/paired minimum-length filtering; exact/partial adapters and paired preservation validated |
| GSEA | 19 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| SuSiE | 19 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Scrublet | 18 | upstream-adapter-validated | Original Scrublet 0.2.3 with pinned scientific sources/dependencies; explicit independent-library/raw-or-estimate count contract, exact or Annoy approximate neighbors, simulated parents, scores/errors, gene selection, thresholds and manifolds. Native Java verifies every Bayesian score/error from actual neighbors and exact identities. Upstream invalid-sqrt variance warning retained. No Scanpy/SnapATAC2, diffusion/cluster-median/consensus, historical or full-paper parity |
| sctransform | 18 | upstream-adapter-validated | Distinct standalone sctransform 0.4.3 vst and Seurat 5.6.0 SCTransform, v1/v2, explicit glmGamPoi 1.24.0, count/estimate contracts and exact cell/feature maps. Model latent variables differ from Seurat post-fit regression; residual/corrected/log layers and RDS retained. Native supplied-model NB residual helpers validated. No integration, deconvolution, DE, historical or full-paper parity |
| Integrative Genomics Viewer (IGV) | 17 | missing | The interactive viewer, exact track/reference loading and published versions require separate setup/integration; a BED export is not a tested IGV session. |
| GCTA | 17 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| edgeR | 16 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Louvain | 16 | upstream-adapter-validated | igraph 1.0.0 multilevel partition on explicit weighted union kNN or supplied undirected nodes/edges; seeded real fixtures, original weights and isolates validated |
| limma | 16 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| AlphaFold | 15 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| featureCounts | 15 | upstream-adapter-validated | Subread featureCounts 2.1.1 typed gene/peak/junction meta-feature counting. Explicit single/paired and read/fragment units, per-sample strand, NH missing-tag policy, overlap/multimapping/fraction/primary/duplicate filters and exact dictionaries. Native GTF/SAF, union lengths, splice and TPM/RPKM/FPKM helpers; raw annotation retained upstream. --primary disables NH fractional weighting upstream and is prominently warned. No Rsubread/custom juncCounts API, UMI, EDASeq/DE or historical/full-paper parity |
| KING | 15 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| PEER | 15 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| minimap2 | 15 | upstream-adapter-validated | minimap2 2.31-r1302 explicit ONT/HiFi/splice/sr/assembly presets, canonical FASTQ SAM or FASTA/FASTQ PAF, secondary/MD/cs/eqx and noncanonical/minimum-DP-score options. Native exact PAF/CIGAR/query geometry and complete primary name/mate/base/quality audits. MD overrides cs upstream; smaller split indexes require both tags disabled. Target-span BED includes introns. No SV/variant calling, paftools, assembly filtering, old-version or full-paper parity |
| CADD | 15 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| SAIGE | 15 | missing | No equivalent workflow identified in the current source inventory. |
| FUMA | 14 | missing | No equivalent workflow identified in the current source inventory. |
| ADMIXTURE | 13 | missing | No equivalent workflow identified in the current source inventory. |
| CellBender | 13 | missing | No equivalent workflow identified in the current source inventory. |
| Minimac | 13 | missing | No equivalent workflow identified in the current source inventory. |
| HISAT2 | 12 | upstream-adapter-validated | HISAT2 2.2.3 linear or annotated splice graph indexes, explicit sealed small/large families and splice-aware single/paired RNA alignment. Native transcript splice/exon helpers, gzip staging and exact primary name/mate/base/quality/reference/RG audits. Explicit strand, intron, DTA, score, mapping limit and splice-learning choices; compiler warnings retained. No StringTie/Ballgown, quantifier, CIRIquant pseudo-reference, HISAT-genotype SNP/haplotype graph or old-version/full-paper parity |
| HOMER | 12 | missing | No equivalent workflow identified in the current source inventory. |
| PRS-CS | 12 | upstream-adapter-validated | Original PRS-CS source-fbc9ec0,1.1.0 and1.0.0 through prs-cs-tools,with native Java allele/marginal/unit/thinning arithmetic,streamed input ledgers,read-only HDF5 bridge and exact model/LD/posterior checks. Explicit formats,N interpretation,trait/panel/population/build labels,model/sampling/seed and exclusion policies. Means/draws/psi follow version capabilities;optional explicit per-allele mean-weight export. Known current NumPy and legacy SciPy alias deprecations retained;unknown/numerical warnings fail. Windows x64 sealed runtime;historical Python3.10.11 requires offline-reproduction choice and prominent EOL warning. Current source has separate compatible Python3.12. Missing/changed/forbidden dependencies skip exit3;scientific/input failures exit2 with quarantined canonical partials and exact recovery. Development candidate prscs-candidate-1 SHA starts33b26e76;canonical a104 is unchanged. Study-specific reference,chain stability,tuning/combination,overlap and prediction calibration require separate assessment. See PRSCS_WORKFLOWS.md. |
| TwoSampleMR | 12 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| RepeatMasker | 11 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| scVI | 11 | missing | No equivalent workflow identified in the current source inventory. |
| Trim Galore | 11 | upstream-adapter-validated | Original unmodified Perl 0.6.7/0.6.10 runs single/paired adapter and quality trimming through Cutadapt 5.2. Typed options cover auto/preset/literal adapters, fixed cuts, N/length filters, orphan rescue and first/last-base hardtrim. Original FastQC 0.13.0 is optional. Java audits headers, bases, qualities, source spans, counters and every output stream. There are 22 independent original comparisons and 25 packaged calls. Declare Phred encoding. Ordinary headers/comments must be ASCII; a UTF8 options-file supports Unicode Windows data paths. Private runtimes are sealed; unavailable dependencies skip with exit 3 and exact recovery. The native post-Cutadapt/hardtrim helper is also available. No RRBS, explicit poly-A, clock/UMI, multicore, Rust rewrite, historical Cutadapt/FastQC or full-paper parity. See TRIM_GALORE_WORKFLOWS.md. |
| Eagle | 11 | missing | No equivalent workflow identified in the current source inventory. |
| Nextflow | 10 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| RSEM | 10 | missing | No equivalent workflow identified in the current source inventory. |
| Space Ranger | 10 | missing | No equivalent workflow identified in the current source inventory. |
| SHAPEIT | 10 | missing | No equivalent workflow identified in the current source inventory. |
| bcl2fastq | 10 | missing | Upstream runtime availability and instrument/sample-sheet contracts require separate integration. |
| SIFT | 10 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| AUCell | 10 | native-subset-validated | Normalized recovery AUC; deterministic input-order ties differ from randomized R ties |
| SpliceAI | 10 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| MEME | 9 | missing | No equivalent workflow identified in the current source inventory. |
| Trimmomatic | 9 | upstream-adapter-validated | Original Trimmomatic0.39/0.41 single/paired trimming with simple/palindrome adapter clipping and full trim-log/header/span/base/quality/summary audits. Native Java13 ordered quality/length/MAXINFO operations, explicit Phred/mate/1000base contracts. Four paired/orphan outputs retained; optional private Java25, versioned archives and GPL notices. No bundled Illumina adapters, FLASH/quantification,0.32/0.36 or full-paper parity |
| Signac | 9 | missing | No equivalent workflow identified in the current source inventory. |
| Monocle | 9 | missing | No equivalent workflow identified in the current source inventory. |
| Beagle | 9 | upstream-adapter-validated | Original Beagle 5.4-29Oct24, 5.1-18May20 and exact 4.1-08Jun17/4.1-21Jan17 GT-only diploid phasing/reference imputation through beagle-tools. Native Java streams input calls, checks complete sample/marker families, observed genotypes, explicit phase preservation and two-decimal DS/AP/GP identities. Explicit map, assembly, panel, seed/model/window and target-only exclusion policies; exact identifiers restored. Modern partially missing prephased inputs require compatible preservation inputs or explicit allow-rephase. Never reconstruct GT from GP. Available in the validated development candidate beagle-candidate-1 (SHA starts 5ed54e44); canonical a104 is unchanged and lacks this command. Missing/changed/forbidden runtime skips exit 3 with exact recovery; failures return 2 and quarantine canonical partials. No GL/PL, IBD, polyploid/pedigree model, automatic panel retrieval or full-paper parity. See BEAGLE_WORKFLOWS.md. |
| IQ-TREE | 9 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| ANNOVAR | 9 | existing-adapter | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| QTLtools | 9 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| GCTA-COJO | 9 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| mashr | 8 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| LOFTEE | 8 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| MAFFT | 8 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| fgsea | 8 | upstream-adapter-validated | Original fgsea1.38.0 multilevel/simple GSEA on explicit supplied ranked statistics and local gene sets,positive/negative/standard tails,power weights and RNG seed. Native Java indexed score/leading-edge/complete-family audit;897 original arithmetic reference cases across enrichment helpers. Balanced standard extrema select zero. Undefined/omitted families suppress all native adjusted P/significance under explicit retention. No phenotype-label permutations,resource retrieval,historical or whole-paper parity. See ENRICHMENT_WORKFLOWS.md |
| kallisto | 8 | upstream-adapter-validated | Original official Windows x86_64 kallisto0.48.0 sealed index and bulk single/paired quantification,legacy bias,strand/fragment choices and every requested plaintext bootstrap. Native Java reference/FASTQ/model/identity/counter/effective-length TPM audits; actual fractional EM20.5+20.5,27packaged calls/8checks. Raw upstream metadata retained and only a verified Windows call-string defect reescaped in a derived audited copy. No HDF5/Sleuth,BUS/UMI,kb count,reference construction,DE,historical or full-paper parity. Failed0.52.0 source build retained separately. See KALLISTO_WORKFLOWS.md |
| PolyPhen-2 | 8 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| Hail | 8 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| SKAT-O | 8 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| StringTie | 8 | missing | No equivalent workflow identified in the current source inventory. |
| REVEL | 8 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| Salmon | 8 | missing | No equivalent workflow identified in the current source inventory. |
| FIMO | 7 | upstream-adapter-validated | Original FIMO 5.5.9 DNA P/q scanning with exact admitted private Windows/Cygwin/GNU-regex runtime, plus independent native Java PSSM/P scanning that needs no MEME runtime. Native command motif-scan uses explicit bounded models (256 MiB workspace and one billion convolution updates by default); original command fimo-tools preserves its admitted program. Explicit backgrounds, strands, coordinates, cohorts, pseudocounts and resource contracts. Original q-estimator/input preprocessing/raw reports retained; stored-hit truncation fails and quarantines even empty exit-0 output. No-Q zero placeholders are blank in the canonical table with warnings. Minimal DNA input subset; no PSP, protein/custom alphabet, arbitrary host, old-version, custom empirical calibration or full-paper parity. See FIMO_WORKFLOWS.md. |
| fastp | 7 | missing | No equivalent workflow identified in the current source inventory. |
| CellPhoneDB | 7 | missing | No equivalent workflow identified in the current source inventory. |
| SoupX | 7 | missing | No equivalent workflow identified in the current source inventory. |
| GenomicRanges | 7 | method-overlap | Native interval intersection/coverage provides related arithmetic, not R GRanges/S4/metadata/sequence-info or full package equivalence. |
| ChIPseeker | 7 | missing | Genome/annotation editions and specific R ChIPseeker annotation functions require separate integration. |
| HTSeq | 7 | missing | No equivalent workflow identified in the current source inventory. |
| AlphaMissense | 7 | existing-lookup | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| dndscv | 7 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| biomaRt | 7 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| g:Profiler | 7 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| hifiasm | 7 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| SMR | 7 | missing | No equivalent workflow identified in the current source inventory. |
| LeafCutter | 7 | missing | No equivalent workflow identified in the current source inventory. |
| ACAT | 7 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| FUSION | 7 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| nf-core | 6 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| t-SNE | 6 | native-subset-validated | Exact seeded 2D t-SNE on supplied components, <=2000 observations; independent affinity/KL/gradient checks; quadratic implementation, no Barnes-Hut or FFT approximation |
| cell2location | 6 | missing | No equivalent workflow identified in the current source inventory. |
| Squidpy | 6 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| gseapy | 6 | missing | No validated equivalent workflow identified in the current source inventory. |
| Rsubread | 6 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| MaxQuant | 6 | missing | No equivalent workflow identified in the current source inventory. |
| smartpca | 6 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Sniffles | 6 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| cisTarget | 6 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| CellChat | 6 | missing | No equivalent workflow identified in the current source inventory. |
| IMPUTE | 6 | missing | No equivalent workflow identified in the current source inventory. |
| SKAT | 6 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| MR-PRESSO | 6 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| DIA-NN | 5 | missing | No equivalent workflow identified in the current source inventory. |
| SCENIC+ | 5 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| UCSC bedGraphToBigWig | 5 | missing | The upstream converter or independently validated native bigWig writer remains missing; signal summaries do not write bigWig. |
| DIAMOND | 5 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| FastQTL | 5 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| scDRS | 5 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| voom | 5 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Snakemake | 5 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Cytoscape | 5 | existing-export | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| seqtk | 5 | native-subset-validated | Native MT19937-64 fractional/2-pass count sampling with synchronized mates, seqtk trimfq Mott/fallback or fixed length/cuts, exact-reference BED3 extraction and terminal motif-rotation scoring. Chunked FASTA access; preserved qualities, old indexes and strict bounds. Explicit warnings for poor-quality fallback, empty reads/no hits. No whole-tool, dirty-build, assembly editing or full paper-pipeline parity |
| Guppy | 5 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| Genomic SEM | 5 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| chromVAR | 5 | missing | No equivalent workflow identified in the current source inventory. |
| tximport | 5 | native-subset-validated | Native Java dense point-estimate import of Salmon quant.sf,kallisto abundance.tsv,RSEM isoforms.results or canonical TSV; transcript/gene output,no/scaledTPM/lengthScaledTPM and transcript dtuScaledTPM. Exact ordered IDs,fractional estimates,explicit annotation/unmapped/uncertainty choices,zero-expression length fallback and library totals. 204actual original R1.40.0cases/15615values and44packaged workflow calls. No R runtime needed. RSEM gene files,HDF5,sparse/alevin,inferential draws,quantification,DE,old-version and full-paper parity remain open. See TXIMPORT_WORKFLOWS.md |
| scVelo | 5 | missing | No equivalent workflow identified in the current source inventory. |
| DoubletFinder | 5 | missing | No equivalent workflow identified in the current source inventory. |
| SCENIC | 5 | missing | No equivalent workflow identified in the current source inventory. |
| WGCNA | 5 | existing-adapter | Scope limited to the existing adapter, lookup or export; upstream software is not bundled. |
| LDpred | 5 | missing | No equivalent workflow identified in the current source inventory. |
| PRSice | 5 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| MTAG | 5 | missing | No equivalent workflow identified in the current source inventory. |
| PoPS | 5 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| LDAK | 5 | missing | No equivalent workflow identified in the current source inventory. |
| HEIDI | 5 | missing | No equivalent workflow identified in the current source inventory. |
| SnpEff | 4 | missing | No equivalent workflow identified in the current source inventory. |
| cooltools | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| CrossMap | 4 | missing | Independent CrossMap execution/input/output validation is required; a related Java interval mapper is not full CrossMap parity. |
| WASP | 4 | missing | Named allelic mapping-bias method remains missing; bulk STAR alignment and gene counts do not implement the required variant-aware filtering. |
| ChimeraX | 4 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| Azimuth | 4 | missing | No equivalent workflow identified in the current source inventory. |
| PrediXcan | 4 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| DiffBind | 4 | missing | The exact replicate/design/count/filter/statistical workflow remains missing. |
| GSVA | 4 | native-subset-validated | Raw ssGSEA only; kernel GSVA and across-dataset normalization remain open |
| FlashPCA | 4 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| DRAGEN | 4 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| Popcorn | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| RFMix | 4 | missing | No equivalent workflow identified in the current source inventory. |
| ALLCools | 4 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| SingleR | 4 | missing | No equivalent workflow identified in the current source inventory. |
| velocyto | 4 | missing | No equivalent workflow identified in the current source inventory. |
| Juicer | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Merqury | 4 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| GEMMA | 4 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| sva | 4 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| ASCAT | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Graphtyper | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| HiC-Pro | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| tensorQTL | 4 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Matrix eQTL | 4 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| pbmm2 | 4 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| CellTypist | 3 | missing | No equivalent workflow identified in the current source inventory. |
| Milo | 3 | missing | No equivalent workflow identified in the current source inventory. |
| Slingshot | 3 | missing | No equivalent workflow identified in the current source inventory. |
| STARsolo | 3 | missing | Distinct single-cell barcode/UMI workflow; the bulk STAR adapter does not implement STARsolo. |
| UCell | 3 | native-subset-validated | Positive UCell signatures; no negative suffixes or smoothing |
| MungeSumstats | 3 | missing | Paper-specific formatting, allele/strand/reference rules and R workflow orchestration require separate integration; coordinate mapping alone does not implement this package. |
| Jalview | 3 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| BCLConvert | 3 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| BWA-MEM2 | 3 | missing | Separate upstream implementation; the BWA 0.7.19 adapter does not supply or establish equivalence with BWA-MEM2. Typed integration and real upstream validation remain pending. |
| DEPICT | 3 | missing | No equivalent workflow identified in the current source inventory. |
| Mutect2 | 3 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| Strelka2 | 3 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| Bismark | 3 | missing | No equivalent workflow identified in the current source inventory. |
| scDblFinder | 3 | missing | No equivalent workflow identified in the current source inventory. |
| Cluster-Buster | 3 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| Subread suite | 3 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| scran | 3 | missing | No equivalent workflow identified in the current source inventory. |
| SHAP | 3 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| SyRI | 3 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| GLIMPSE | 3 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| MR-MEGA | 3 | missing | No equivalent workflow identified in the current source inventory. |
| GWAMA | 3 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| MetaXcan | 3 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| scArches | 3 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| minfi | 3 | missing | No equivalent workflow identified in the current source inventory. |
| InterProScan | 3 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| LDstore | 3 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| AutoComplete | 3 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| BOLT-LMM | 3 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| FINEMAP | 3 | missing | No equivalent workflow identified in the current source inventory. |
| SBayesR | 3 | missing | No equivalent workflow identified in the current source inventory. |
| Verkko | 3 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| MAJIQ | 3 | missing | No equivalent workflow identified in the current source inventory. |
| WhatsHap | 3 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| Locityper | 3 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| BEAST | 2 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| TreeAnnotator | 2 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| MAPS | 2 | missing | The specific MAPS chromatin-contact pipeline and its AND/XOR/background model remain missing. |
| MetaboAnalyst | 2 | missing | No equivalent workflow identified in the current source inventory. |
| ArchR | 2 | missing | No equivalent workflow identified in the current source inventory. |
| pycisTopic | 2 | missing | Full pycisTopic orchestration, topic selection, consensus peaks and cell-state handling remain missing. |
| souporcell | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| tradeSeq | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Tangram | 2 | missing | No equivalent workflow identified in the current source inventory. |
| GREAT | 2 | missing | No equivalent workflow identified in the current source inventory. |
| IRanges | 2 | method-overlap | Related native interval arithmetic exists; R IntegerRanges/Rle, grouping and package-specific metadata/recycling semantics require separate work. |
| MapMyCells | 2 | missing | No validated equivalent workflow identified in the current source inventory. |
| variancePartition | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Vireo | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| snpAD | 2 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| rMATS | 2 | missing | No equivalent workflow identified in the current source inventory. |
| MultiQC | 2 | upstream-adapter-validated | MultiQC 1.35 aggregation of explicit FastQC ZIP/Cutadapt JSON inputs; exact sample naming and raw read totals checked across colliding original basenames; AI/version checks/uploads disabled |
| RSeQC | 2 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| rtracklayer | 2 | missing | Specific R functions, split/multiple GRanges semantics, file formats and published versions require their own integration. |
| UMI-tools | 2 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| NanoSeq | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| ggtree | 2 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| MetaBAT2 | 2 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| inferCNV | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| SnapATAC2 | 2 | missing | No validated equivalent workflow identified in the current source inventory. |
| CIBERSORT | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| scCODA | 2 | missing | No validated equivalent workflow identified in the current source inventory. |
| cemba-data / YAP | 2 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| scHiCluster | 2 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| RCTD | 2 | missing | No equivalent workflow identified in the current source inventory. |
| pyBigWig | 2 | missing | Distinct source-named library/program; integration and independent scientific validation pending. |
| plotsr | 2 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| DeepSTARR | 2 | missing | Neural architecture, training/evaluation splits and exact model inference remain missing. |
| ancIBD | 2 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| FLASH | 2 | native-subset-validated | Native Java FLASH 1.2.11 ungapped paired-read merging, original binary32 scoring/ties and quality consensus, innies/optional outies, N exclusion, both Phred encodings and older capped mismatch qualities. Streaming paired/interleaved plain/gzip FASTQ retains merged and both unmerged streams, source hashes, every original/merged header, per-pair scores and normalization counts. Maximum overlap caps the scoring denominator; it is not a hard length exclusion. Original slash/barcode header changes and sequence cleanup produce actionable warnings. Strict mate identities are the default; original positional pairing is explicit. No external runtime. Worst-case quadratic search; no wrapped/tab input, original threading/text-report, historical-version or whole-paper parity. See FLASH_WORKFLOWS.md. |
| PRS-CSx | 2 | missing | No equivalent workflow identified in the current source inventory. |
| Arriba | 2 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| SeSAMe | 2 | missing | No equivalent workflow identified in the current source inventory. |
| CAUSE | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| PolyFun | 2 | missing | No equivalent workflow identified in the current source inventory. |
| DAVID | 2 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| MetaSTAAR | 2 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| SAIGE-GENE | 2 | missing | No equivalent workflow identified in the current source inventory. |
| ComBat | 2 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| muscat | 2 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| Sambamba | 2 | missing | Actual Sambamba execution and study-specific duplicate semantics remain missing; SAMtools/Picard are distinct implementations. |
| gsMap | 2 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| scds | 2 | missing | No validated equivalent workflow identified in the current source inventory. |
| DeepRVAT | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| EasyQC | 2 | missing | Exact source filtering, reference/allele/duplicate/INFO rules and published version require a tested profile. |
| Yak | 2 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| Exome Germline Single Sample | 2 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| hap-ibd | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| demuxlet | 2 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Strobealign | 2 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| Bowtie 1 | 1 | missing | Primary-methods software recovered during citation-boundary and per-paper completeness review; implementation and upstream validation pending. |
| FASTX-Toolkit | 1 | native-subset-validated | Native fixed-span FASTQ trimming with one-based inclusive positions, matching clipped qualities and synchronized paired filtering; post-adapter stage; independent actual Cutadapt reference fixtures; other FASTX programs/FASTA/Phred64 not implemented |
| riboWaltz | 1 | missing | Primary-methods software recovered during citation-boundary and per-paper completeness review; implementation and upstream validation pending. |
| nf-core/eager | 1 | missing | Recovered from individually read primary alignment Methods. Exact workflow/input/platform integration and real upstream validation remain pending; no standalone CLI or complete pipeline equivalence is assumed. |
| Avocado | 1 | missing | Reusing published imputed tracks is distinct from running Avocado or recreating its model. |
| NicheNet | 1 | missing | No equivalent workflow identified in the current source inventory. |
| AutoDock Vina | 1 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| Hornet | 1 | missing | Study-specific modified WASP pipeline remains missing; source/version, allele swaps and species-specific variants require separate integration. |
| pycisTarget | 1 | missing | The specific Python motif workflow and its supplied databases remain missing. |
| BAMscale | 1 | missing | Coverage generation and the specific BAMscale normalization workflow remain missing. |
| DeStruct | 1 | missing | Recovered from individually read primary alignment Methods. Exact workflow/input/platform integration and real upstream validation remain pending; no standalone CLI or complete pipeline equivalence is assumed. |
| ICE Analysis | 1 | missing | Recovered from individually read primary alignment Methods. Exact workflow/input/platform integration and real upstream validation remain pending; no standalone CLI or complete pipeline equivalence is assumed. |
| Isabl | 1 | missing | Recovered from individually read primary alignment Methods. Exact workflow/input/platform integration and real upstream validation remain pending; no standalone CLI or complete pipeline equivalence is assumed. |
| Mondrian | 1 | missing | Recovered from individually read primary alignment Methods. Exact workflow/input/platform integration and real upstream validation remain pending; no standalone CLI or complete pipeline equivalence is assumed. |
| BEDOPS | 1 | method-overlap | Native interval and signal helpers overlap with some mathematics; BEDOPS command, file-format and whole-workflow equivalence is unvalidated. |
| FitHiChIP | 1 | missing | FitHiChIP contact models, input contracts and study configuration remain missing. |
| UCSC bigWigToWig | 1 | missing | The named converter and indexed signal decoder remain missing. |
| DropEst | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| ChIP_Zwart | 1 | missing | Workflow orchestration and exact archived source/version require separate integration; individual adapters do not reproduce it. |
| HMMRATAC | 1 | missing | Original HMMRATAC and the MACS3 hmmratac program need version/model-specific integration; ordinary MACS callpeak is a different workflow. |
| CLAPAnalysis | 1 | missing | Named workflow remains missing; independently validated tool stages do not reproduce its complete orchestration or study modifications. |
| IsoQuant | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| MSstats | 1 | missing | No equivalent workflow identified in the current source inventory. |
| EWCE | 1 | missing | Distinct source-named method/wrapper; integration and independent scientific validation pending. |
| LAVA | 1 | missing | No equivalent workflow identified in the current source inventory. |
| MAGMA.Celltyping | 1 | missing | Distinct source-named method/wrapper; integration and independent scientific validation pending. |
| MiXeR | 1 | missing | No equivalent workflow identified in the current source inventory. |
| CancerVision | 1 | missing | Specific named workflow recovered from primary methods. GATK germline calling does not supply this distinct caller/model, platform or complete pipeline; integration and upstream validation remain pending. |
| DELLY | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| Spectronaut | 1 | missing | No equivalent workflow identified in the current source inventory. |
| HPRep | 1 | missing | The specific contact-reproducibility metric and sampling/normalization contracts remain missing. |
| LS-GKM | 1 | missing | LS-GKM training/prediction and deltaSVM sequence-scoring workflows remain missing. |
| scDist | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| BAMTools | 1 | missing | Related SAM/BAM operations exist through other programs; actual BAMTools integration and published-version compatibility remain missing. |
| TPMCalculator | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| scater | 1 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| SingleM | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| biobambam2 | 1 | missing | Related BAM/QC tools do not establish biobambam2 clipping, duplicate semantics or published-version equivalence. |
| qnorm | 1 | native-subset-validated | Native finite float64 column-wise rank-target normalization, default rank means or supplied sorted target, tied-rank averages and exact input/output order. 500actual original0.8.0cases/61403values, independent API/CLI/error/precision checks and20packaged runs. Explicit raw/estimated/continuous input units; raw integer text limited to2^53-1. No float32/int32 casting, ndarray axis0 transpose, pandas/incremental/multiprocessing, inverse-normal scoring, bitwise or downstream/full-paper parity. See QNORM_WORKFLOWS.md |
| SkyPilot | 1 | missing | Individually identified in primary Methods during completeness review; scientific role, resource-only use and runtime requirements must be checked per paper; integration pending. |
| MAGIC | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| PyWGCNA | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| SysVI | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| HiFiAdapterFilt | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| BridgePRS | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| SPRINTER | 1 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| ArchMap | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| splitpipe | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| Manta | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| ProteinPaint | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| MultiXcan | 1 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| popV | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| NS-Forest | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| BlastKOALA | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| KofamKOALA | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| Subjunc | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| popEVE | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| SPARK | 1 | missing | No equivalent workflow identified in the current source inventory. |
| CAVIAR | 1 | missing | No equivalent workflow identified in the current source inventory. |
| Meta-SAIGE | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| SNPTEST | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| isotwas | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| selscan | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| UCSC bigWigMerge | 1 | missing | Distinct source-named library/program; integration and independent scientific validation pending. |
| METASOFT | 1 | method-overlap | Related native method; upstream software options and numerical equivalence require separate validation. |
| DOSE | 1 | upstream-adapter-validated | Explicit original legacy DOSE4.5.0 generic ORA/GSEA in a separate sealed library;the main current4.6.0 dependency profile is unchanged. Multilevel/simple/DOSE permutation choices,annotated-query denominator and exact original core-enrichment helper audited. A negative first-hit boundary can exclude the first hit,and ES0 can retain a nonempty raw-helper core. No silent downgrade,DOSE3.24.2 parity,online annotation or full-paper reconstruction. See ENRICHMENT_WORKFLOWS.md |
| Numbat | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| MendelVar | 1 | missing | Distinct source-named method/wrapper; integration and independent scientific validation pending. |
| gwas-sumstats-harmoniser | 1 | missing | Named procedure/code execution and reference-consensus rules must be distinguished; genomic coordinate conversion alone is insufficient. |
| RE2C | 1 | missing | Source-specific Lin-Sullivan/sample-overlap behavior requires its own implementation and validation; independent-cohort inverse-variance meta-analysis is not equivalent. |
| FAVOR | 1 | missing | Distinct source-named method/wrapper; integration and independent scientific validation pending. |
| GSA-MiXeR | 1 | missing | Distinct source-named method/wrapper; integration and independent scientific validation pending. |
| LCV | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| ARACNe | 1 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| aREA | 1 | missing | Distinct named analytic method; ordinary area and GSEA running scores are excluded. Original VIPER/aREA runtime eligibility is pending. |
| metaVIPER | 1 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| ShortRead | 1 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| VIPER | 1 | missing | Separate from metaVIPER and msVIPER. Original runtime held pending applicable restricted-license eligibility confirmation; no installation or original-engine numerical validation claimed. |
| fastGWA | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| LTPI | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| scE2G | 1 | missing | Paper-specific method identified during title and code-link review; authentic integration and scientific validation pending. |
| STAAR | 1 | missing | No equivalent workflow identified in the current source inventory. |
| Trailmaker | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| GoodpeakAnalysis | 1 | missing | Study-specific R workflow/source and statistical contracts require separate integration. |
| Nascent RNA Sequencing Analysis (NRSA) | 1 | missing | The specific nascent RNA pipeline, normalization, gene-body and pause models require upstream workflow integration. |
| MashMap | 1 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| PretextMap | 1 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| PretextView | 1 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| Winnowmap | 1 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| Presto | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| LUSH-DNASeq-pipeline | 1 | missing | Recovered from individually read primary alignment Methods. Exact workflow/input/platform integration and real upstream validation remain pending; no standalone CLI or complete pipeline equivalence is assumed. |
| StainedGlass | 1 | missing | The exact repeat-structure visualization workflow remains missing. |
| TEMR | 1 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| ExpansionHunter | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| eCAVIAR | 1 | missing | No equivalent workflow identified in the current source inventory. |
| LaDDR | 1 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| dupRadar | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| CIRI2 | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| CIRIquant | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| ataqv | 1 | missing | The named ATAC QC workflow and its metric definitions remain missing. |
| CELLECT | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| CELLEX | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| DEXSeq | 1 | missing | No equivalent workflow identified in the current source inventory. |
| liftOver R workflow (rtracklayer interface) | 1 | missing | Named workflow is distinct from the UCSC executable and from the general rtracklayer package. Primary paper/citation identifies the R interface; versions remain explicit. |
| SUPPA2 | 1 | missing | No equivalent workflow identified in the current source inventory. |
| DiscoDivas | 1 | missing | Discovered during per-paper completeness review; integration and independent validation pending. |
| APEX | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |
| PUMICE | 1 | missing | The essential/nonessential predictor and regulatory-region modeling workflow remains missing. |
| Ballgown | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| seqinr | 1 | missing | Additional named software from primary alignment/quantification source review; a related adapter, installed executable or generic Java helper does not establish this workflow. Integration and independent validation pending. |
| AnnotationHub | 1 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| BiocFileCache | 1 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| BiocGenerics | 1 | missing | Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending. |
| sccomp | 1 | missing | No validated equivalent workflow identified in the current source inventory. |
| rustybam | 1 | missing | Named primary-source workflow recovered during semantic review. Upstream integration, exact input/settings and meaningful validation remain pending. |
| FAME | 1 | missing | The epistasis model and stochastic estimators/trace reuse require independent scientific validation; no substitute ordinary interaction regression. |
| mvMAPIT | 1 | missing | Marginal epistasis scientific model, covariance/trace estimates and test calibration require separate implementation and validation. |
| Sparse marginal epistasis (SME) | 1 | missing | Annotation mask, variance-component model, stochastic traces/shared random vectors and one-sided test require distinct implementation and calibration. |
| ashr | 1 | missing | Additional software identified during full-text/code-link completeness review; integration and independent validation pending. |

Next: sequencing/QC and GWAS profiles, followed by less frequent workflows. UMAP, supplied-graph Leiden/Louvain, R Harmony and four typed Picard operations have real fixture validation. Python Harmony remains dependency-skipped on this Windows environment. Reconcile novel software links and remaining ambiguous matches before claiming exhaustive tool coverage.
