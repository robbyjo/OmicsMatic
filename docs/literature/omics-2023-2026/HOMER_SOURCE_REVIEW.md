# HOMER: source roles and implementation boundaries

**Current catalogued-role coverage is available in development.** [The role audit](homer-role-coverage.json) maps eleven software uses and one motif-resource reuse entry to the implemented commands, with explicit unreported choices. [RNA guide](HOMER_RNA_WORKFLOWS.md) and [validation](homer-rna-validation.json) close the P040 exon raw/RPKM role. This covers the catalogued HOMER roles, not every HOMER module or exact paper reproduction. P126 remains data-resource reuse; P215 E-value wording is not converted into a HOMER P/q value. The implementation checkpoint notes below retain historical states. The whole goal remains active with 268 missing integrations and paper-wide semantic review open.

Twelve paper roles were checked against the immutable primary article XML.
Eleven reviews were added and the existing P163 review was expanded with checked
supporting links. The catalog now has 682 reviewed uses and 202 supporting
pointers, including four explicit method-detail links. All 250 paper identities,
2,428 tool observations and primary evidence pointers are unchanged.

HOMER is broader than motif scanning. P016 uses peak calling and coverage,
P040 uses exon counting/RPKM, P050/P053 use genomic annotation, and P126 reuses
a motif collection without reporting a HOMER program run. These roles must not
be replaced by a single motif-scanning label. The bounded native `homer-scan`
workflow is described in [HOMER_NATIVE_SCANNING.md](HOMER_NATIVE_SCANNING.md).
The catalogued HOMER roles have development implementations; no entire-package, paper-wide,
historical or biological-accuracy parity is credited.

| Paper | Reported release | Actual role and retained limits |
| --- | --- | --- |
| [P016](https://doi.org/10.1038/s41586-023-06845-4) | unstated | Called mm10 ATAC open-chromatin regions with HOMER region mode, a 150-bp fragment size and 300-bp peak size; generated replicate-merged 1-bp signal tracks normalized to ten million reads, with the reported 4-bp upstream Tn5 shift and paired-end fragment extension. Peak calling, normalized coverage, read shifting and full-fragment extension are separate operations. The paragraph does not name a HOMER release or exact executable/track commands. Alignment, MAPQ30 filtering and duplicate removal are upstream steps, not supplied by a motif scanner. |
| [P040](https://doi.org/10.1038/s41586-026-10954-1) | 5.1 | Used HOMER 5.1 analyzeRepeats.pl to calculate raw exon counts and RPKM for RefSeq genes in stimulated CD8 T-cell bulk RNA-seq before DESeq2 1.48.2 differential expression. This is exon-level gene quantification, not motif discovery. The RPKM eligibility rule and DESeq2 model, scaling and adjusted-P/fold-change criteria are downstream choices; RefSeq edition, read-strand and paired-fragment assignment details are unstated. |
| [P044](https://doi.org/10.1038/s41586-024-08307-x) | 4.11 | Analysed DNA-binding-factor motifs in differential fetal-hepatocyte ATAC peaks using HOMER 4.11 after the peak atlas was expanded 500 bp on each side of the summit and differential peaks were selected with DESeq2. The source does not identify the HOMER command, motif database, known-versus-de-novo mode or background. The summit expansion, differential analysis and heatmap are separate; do not infer motif-window defaults or a genome build from this paragraph. |
| [P046](https://doi.org/10.1038/s41586-026-10987-6) | unstated | Ran HOMER findMotifsGenome.pl on cell-type-specific cCREs and differentially accessible regions with -size given and otherwise stated defaults, then retained significant-motif TFs with matching-cell-type RPKM greater than 10. Preserve actual region widths and the separate TF-expression filter. HOMER motif enrichment corroborates ASCL1/LHX2 accessibility associations; TOBIAS footprinting and its TF-binding differences are a separate analysis. No HOMER release, motif-set edition or explicit background is stated. |
| [P050](https://doi.org/10.1038/s41586-023-06821-y) | unstated | Used HOMER annotatePeaks in nf-core analyses to classify Toxoplasma gondii ME49 ChIP-seq and ATAC-seq peak distributions relative to gene features. The ChIP workflow and ATAC workflow are distinct. MACS2 calls peaks; featureCounts and DESeq2 quantify and compare annotated ATAC peaks. Do not relabel annotation as HOMER peak calling or motif analysis. The exact HOMER version, feature annotation edition and annotation flags are unstated. |
| [P053](https://doi.org/10.1038/s41586-023-06819-6) | unstated | Used HOMER annotatePeaks.pl with hg38 to annotate transposable-element association and TSS proximity for human DMRs, peaks, loops and boundaries, and repeated TE annotation for mouse ATAC peaks with mm10. The source includes several region classes, not just peaks. Species/build-specific TE annotations and transcript/TSS resources must remain explicit. It does not report HOMER version, exact annotation database editions or motif discovery. |
| [P124](https://doi.org/10.1038/s41588-023-01554-0) | 4.11 | Used HOMER 4.11 to identify TF-binding motifs associated with chromatin-accessibility changes in BATF3-overexpressing versus control CD8 T cells. The paragraph delegates extra detail to Supplementary Method 2 and does not specify a HOMER command, motif mode or background. ChIPSeeker supplies nearest-gene/genomic-region annotation, while MACS2, featureCounts and DESeq2 supply peak calling, counting and differential analysis. |
| [P126](https://doi.org/10.1038/s41588-024-01947-9) | motif collection; HOMER release unstated | Used HOMER-origin TF motif models alongside ENCODE, HOCOMOCO and FactorBook models obtained through MotifDB 1.42.0 for motifbreakR 2.13.7 weighted-sum disruption analysis of noncoding diabetes variants. HOMER is a motif collection here; the paper does not report running a HOMER program. MotifDB 1.42.0 and motifbreakR 2.13.7 are not HOMER releases. The dbSNP/GRCh38 resources, motifbreakR probability/strong-effect rule and tissue-expression filtering remain separate. |
| [P133](https://doi.org/10.1038/s41588-025-02388-8) | 4.11 | Applied the HOMER 4.11 Known Motif Discovery approach to motifs in prostate-cancer ChIP-seq analyses after separately defined alignment, filtering and MACS3 peak calling. Known Motif Discovery is the source label and does not establish de-novo motif discovery. The exact HOMER command, known-motif database edition, window and target/background sets are unstated. DeepTools normalization and heatmaps are separate. |
| [P163](https://doi.org/10.1038/s41588-024-01911-7) | 4.11.1 | Used HOMER 4.11.1 for motif analysis and peak annotation of PU.1 CUT&Tag data; the results also describe motif enrichment in changing ATAC regions during the DB2115 time course. Retain the exact 4.11.1 patch release and distinguish the CUT&Tag and time-course ATAC region sets. Canonical PU.1 log-odds scoring is described alongside HOMER, so do not assume HOMER supplied every affinity score. Alignment, coverage, MACS2 peak calling and DiffBind differential selection are separate. |
| [P187](https://doi.org/10.1016/j.ajhg.2025.11.009) | 4.11 | Tested liver cell-type marker ATAC peaks for enrichment of known TF motifs with HOMER 4.11, using -size 200 and all merged cell-type peaks as the background. Preserve the specified 200-bp window and merged-peak background, including any target/background overlap; do not silently remove targets or substitute random genomic background. GREAT/GO gene-function enrichment is a separate test. Other HOMER options and motif-set edition are unstated. |
| [P215](https://doi.org/10.1016/j.ajhg.2024.07.022) | unstated | Analysed lost ARID1A-bound regions, bound promoters, gained ARID1A regions and relocated ZIC2 regions with HOMER using BEDTools-derived FASTA and shuffled-input background; motif presence in lost regions was subsequently quantified with FIMO. Retain the source E-value <0.001 wording without relabeling it a HOMER P/q value. HOMER version, exact command, known/de-novo mode and shuffle algorithm/seed are unstated. FIMO motif quantification is a separate downstream program; the reported ZIC-family motif presence does not identify a new motif-discovery algorithm. |

P040's 5.1 version was recovered from the source even though automated
near-name extraction had missed it. P133's “Known Motif Discovery” label remains
known-motif analysis. P215's stated E-value threshold is retained without
converting it to a HOMER P/q value; its subsequent FIMO quantification is a
different program. P187's merged-peak background can overlap the target set:
removing targets or substituting random genomic background changes the analysis.

The source-name guard recognizes `HOMER` and `Homer package`, while avoiding
unrelated Homer homonyms. P163's lowercase result references are checked
same-paper method-detail links to the explicitly named package paragraph.
This preserves the guard and does not assign the CUT&Tag release automatically
to every ATAC result.

Read `homer-source-validation.json`, `tools/literature/homer-use-reviews.json`
and `tools/literature/curate_homer_uses.py` for paragraph hashes, sections and
links. The prior review file is archived by hash; the P163 before/after prose is
retained in the validation report. Paper-wide semantic completeness remains open.

Original 5.1, 4.11 and 4.11.1 archives were obtained from the official update
server and run privately for reference comparisons. The available HTTP endpoint
provided the archives; HTTPS could not be fetched. Their recorded SHA256 hashes
identify the downloaded bytes and are not independently authenticated upstream
signatures. Full original notices/COPYING and failed extraction attempts are
retained. Private reference execution alone does not establish runtime admission.
Current explicit profiles support original core known/find/discovery workflows;
read `HOMER_DISCOVERY_WORKFLOWS.md` and its current validation metadata. Genome,
annotation, peak/coverage and RNA workflows remain unfinished. Native scanning
requires no external software.
If an upstream workflow cannot be installed or executed where permitted, skip
that dependent analysis with its cause and required recovery. No automatic
background/model/version substitution is authorized. Cell Ranger remains
excluded.


The typed supplied-GTF peak annotation command is now implemented through original
annotatePeaks.pl with genome none and size given. See
[the current guide](HOMER_ANNOTATION_WORKFLOWS.md) and
`homer-annotation-validation.json`. This addresses supplied-GTF feature/TSS
annotation within a declared reference; it does not reconstruct unspecified
named-genome/repeat/CpG annotation packages, the P053 TE workflow, peak calling,
coverage or P040 RNA/exon counting. All paper-specific resource/version
uncertainties above remain retained.


## Explicit named annotation development entry

The separate `homer-tools named-annotate` entry now supplies original 5.1/v7.0
hg38/mm10 region,TE/TSS annotation with complete native resource/identity/
geometry and population checks. P053 leaves software/database editions unstated;
this explicit choice does not establish exact paper reproduction. See
[the named guide](HOMER_NAMED_ANNOTATION_WORKFLOWS.md) and
[validation record](homer-named-validation.json). Original metadata and P values
remain diagnostics;source irregularities and all compatible ties stay explicit.
P016 peak/coverage,P040 RNA and other broader HOMER roles remain unfinished.
The tool keeps its missing badge and the 250-paper goal remains active.


## Explicit ATAC development entry

The separate `homer-tools atac` operation now supplies the selected original
5.1 P016 region/coverage role and pinned UCSC 2.10 BigWig conversion. Native Java
streams SAM projection and exact disk-backed tag populations, checks every final
region count, replays all float32 printed coverage and audits every raw BigWig
interval. The paper leaves editions and complete commands unstated. Its upstream
alignment, duplicate removal and viewer remain separate. See
[the ATAC guide](HOMER_ATAC_WORKFLOWS.md) and [validation](homer-atac-validation.json).
Peak-discovery scores and FDR/Poisson calibration remain original results; exact
count agreement does not certify them. P040 RNA and broader HOMER roles remain
unfinished, so the tool retains its missing badge and the whole goal is active.
