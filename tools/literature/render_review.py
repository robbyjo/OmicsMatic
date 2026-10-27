"""Render the offline paper/tool catalog and explicit implementation queue."""
from collections import Counter
import hashlib
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/literature/omics-2023-2026"
NATIVE = {
    "flash-merge": ("native-subset-validated", "src/main/java/org/jlinalg/sequence/FlashReads.java", "Native Java FLASH 1.2.11 ungapped paired-read merging, original binary32 scoring/ties and quality consensus, innies/optional outies, N exclusion, both Phred encodings and older capped mismatch qualities. Streaming paired/interleaved plain/gzip FASTQ retains merged and both unmerged streams, source hashes, every original/merged header, per-pair scores and normalization counts. Maximum overlap caps the scoring denominator; it is not a hard length exclusion. Original slash/barcode header changes and sequence cleanup produce actionable warnings. Strict mate identities are the default; original positional pairing is explicit. No external runtime. Worst-case quadratic search; no wrapped/tab input, original threading/text-report, historical-version or whole-paper parity. See FLASH_WORKFLOWS.md."),
    "tximport": ("native-subset-validated", "src/main/java/org/jlinalg/sequence/TranscriptImport.java", "Native Java dense point-estimate import of Salmon quant.sf,kallisto abundance.tsv,RSEM isoforms.results or canonical TSV; transcript/gene output,no/scaledTPM/lengthScaledTPM and transcript dtuScaledTPM. Exact ordered IDs,fractional estimates,explicit annotation/unmapped/uncertainty choices,zero-expression length fallback and library totals. 204actual original R1.40.0cases/15615values and44packaged workflow calls. No R runtime needed. RSEM gene files,HDF5,sparse/alevin,inferential draws,quantification,DE,old-version and full-paper parity remain open. See TXIMPORT_WORKFLOWS.md"),
    "qnorm": ("native-subset-validated", "src/main/java/org/jlinalg/sequence/QuantileNormalization.java", "Native finite float64 column-wise rank-target normalization, default rank means or supplied sorted target, tied-rank averages and exact input/output order. 500actual original0.8.0cases/61403values, independent API/CLI/error/precision checks and20packaged runs. Explicit raw/estimated/continuous input units; raw integer text limited to2^53-1. No float32/int32 casting, ndarray axis0 transpose, pandas/incremental/multiprocessing, inverse-normal scoring, bitwise or downstream/full-paper parity. See QNORM_WORKFLOWS.md"),
    "ucsc-liftover": ("native-subset-validated", "src/main/java/org/jlinalg/genetics/ChainLiftover.java", "Java HTSJDK basic single-result chain mapping with exact full source/target dictionaries, structural/32-bit checks, explicit minMatch and unmapped retain/error policy, optional target-span cap and exact or bidirectional-overlap round trips. Every input/status retained; no preferred alternative hit, split/BED12, variant allele/REF/indel, R metadata or whole UCSC/CrossMap/rtracklayer/paper equivalence"),
    "ucell": ("native-subset-validated", "src/main/java/org/jlinalg/enrichment/SignatureScores.java", "Positive UCell signatures; no negative suffixes or smoothing"),
    "aucell": ("native-subset-validated", "src/main/java/org/jlinalg/enrichment/SignatureScores.java", "Normalized recovery AUC; deterministic input-order ties differ from randomized R ties"),
    "gsva": ("native-subset-validated", "src/main/java/org/jlinalg/enrichment/SignatureScores.java", "Raw ssGSEA only; kernel GSVA and across-dataset normalization remain open"),
    "bedtools": ("native-subset-validated", "src/main/java/org/jlinalg/genetics/GenomicIntervals.java", "Intersection, merge, subtraction, union coverage; canonical headered TSV, half-open coordinates"),
    "tsne": ("native-subset-validated", "src/main/java/org/jlinalg/embedding/Tsne.java", "Exact seeded 2D t-SNE on supplied components, <=2000 observations; independent affinity/KL/gradient checks; quadratic implementation, no Barnes-Hut or FFT approximation"),
    "fastx": ("native-subset-validated", "src/main/java/org/jlinalg/sequence/FixedReadTrimmer.java", "Native fixed-span FASTQ trimming with one-based inclusive positions, matching clipped qualities and synchronized paired filtering; post-adapter stage; independent actual Cutadapt reference fixtures; other FASTX programs/FASTA/Phred64 not implemented"),
    "seqtk": ("native-subset-validated", "src/main/java/org/jlinalg/sequence/SeqtkMethods.java", "Native MT19937-64 fractional/2-pass count sampling with synchronized mates, seqtk trimfq Mott/fallback or fixed length/cuts, exact-reference BED3 extraction and terminal motif-rotation scoring. Chunked FASTA access; preserved qualities, old indexes and strict bounds. Explicit warnings for poor-quality fallback, empty reads/no hits. No whole-tool, dirty-build, assembly editing or full paper-pipeline parity"),
    "macs": ("native-subset-validated", "src/main/java/org/jlinalg/genetics/ThresholdPeaks.java", "Native track-peaks supplied-score threshold/gap/span and plateau-summit subset, checked against 129 actual MACS3 3.1.0 bdgpeakcall runs for positive float32 cutoffs. Explicit reference/missing/precision choices, 64-bit native bounds and actionable warnings. Alignment-based callpeak failed on Windows; HMMRATAC is unavailable. No background/PQ scoring, full old-version, zero/signed-summit or whole-pipeline equivalence")
}
ADAPTERS = {
    "igv": ("upstream-adapter-validated", "Original IGV2.19.8 local audited views through igv-tools;bounded development candidate,full paper-specific scope remains open. See IGV_WORKFLOWS.md."),
    "prscs": ("upstream-adapter-validated", "Original PRS-CS profiles source-fbc9ec0, 1.1.0 and 1.0.0 run through prs-cs-tools. Native Java checks allele orientation, marginal effects, units, thinning, input ledgers, raw HDF5 LD and posterior identities. Declare the format, N interpretation, trait, panel, population/build labels, model, sampling, seed and exclusion policies. Means, draws and psi follow the selected version's capabilities; per-allele mean-weight export is explicit. Known NumPy and historical SciPy alias deprecations remain visible; unknown or numerical warnings fail. The sealed runtime requires Windows x64. Historical Python 3.10.11 requires an offline-reproduction choice and an EOL warning; the current source has a separate compatible Python 3.12 environment. Missing, changed or forbidden dependencies skip exit 3; input/process failures return exit 2 with quarantined canonical partials and recovery steps. Use development candidate prscs-candidate-1, SHA starts 33b26e76; canonical a104 is unchanged. Reference compatibility, chain stability, tuning, score combination, overlap and predictive calibration require separate assessment. See PRSCS_WORKFLOWS.md."),
    "beagle": ("upstream-adapter-validated", "Original Beagle 5.4-29Oct24, 5.1-18May20 and exact 4.1-08Jun17/4.1-21Jan17 GT-only diploid phasing/reference imputation through beagle-tools. Native Java streams input calls, checks complete sample/marker families, observed genotypes, explicit phase preservation and two-decimal DS/AP/GP identities. Explicit map, assembly, panel, seed/model/window and target-only exclusion policies; exact identifiers restored. Modern partially missing prephased inputs require compatible preservation inputs or explicit allow-rephase. Never reconstruct GT from GP. Available in the validated development candidate beagle-candidate-1 (SHA starts 5ed54e44); canonical a104 is unchanged and lacks this command. Missing/changed/forbidden runtime skips exit 3 with exact recovery; failures return 2 and quarantine canonical partials. No GL/PL, IBD, polyploid/pedigree model, automatic panel retrieval or full-paper parity. See BEAGLE_WORKFLOWS.md."),
    "fimo": ("upstream-adapter-validated", "Original FIMO 5.5.9 DNA P/q scanning with exact admitted private Windows/Cygwin/GNU-regex runtime, plus independent native Java PSSM/P scanning that needs no MEME runtime. Native command motif-scan uses explicit bounded models (256 MiB workspace and one billion convolution updates by default); original command fimo-tools preserves its admitted program. Explicit backgrounds, strands, coordinates, cohorts, pseudocounts and resource contracts. Original q-estimator/input preprocessing/raw reports retained; stored-hit truncation fails and quarantines even empty exit-0 output. No-Q zero placeholders are blank in the canonical table with warnings. Minimal DNA input subset; no PSP, protein/custom alphabet, arbitrary host, old-version, custom empirical calibration or full-paper parity. See FIMO_WORKFLOWS.md."),
    "trimgalore": ("upstream-adapter-validated", "Original unmodified Perl 0.6.7/0.6.10 runs single/paired adapter and quality trimming through Cutadapt 5.2. Typed options cover auto/preset/literal adapters, fixed cuts, N/length filters, orphan rescue and first/last-base hardtrim. Original FastQC 0.13.0 is optional. Java audits headers, bases, qualities, source spans, counters and every output stream. There are 22 independent original comparisons and 25 packaged calls. Declare Phred encoding. Ordinary headers/comments must be ASCII; a UTF8 options-file supports Unicode Windows data paths. Private runtimes are sealed; unavailable dependencies skip with exit 3 and exact recovery. The native post-Cutadapt/hardtrim helper is also available. No RRBS, explicit poly-A, clock/UMI, multicore, Rust rewrite, historical Cutadapt/FastQC or full-paper parity. See TRIM_GALORE_WORKFLOWS.md."),
    "clusterprofiler": ("upstream-adapter-validated", "Original clusterProfiler4.20.3 supplied-set enricher ORA and GSEA through enrichit0.2.6,explicit multilevel/permute/sample,null/tail/seed/universe/query and complete-family rules. Native Java score/log-hypergeometric/adjustment and original identity/count/leading-edge audits;raw RDS,original values and all omissions retained. Storey q-values are not BH values. No enrichGO/online annotation,old-version or whole-paper parity. See ENRICHMENT_WORKFLOWS.md"),
    "fgsea": ("upstream-adapter-validated", "Original fgsea1.38.0 multilevel/simple GSEA on explicit supplied ranked statistics and local gene sets,positive/negative/standard tails,power weights and RNG seed. Native Java indexed score/leading-edge/complete-family audit;897 original arithmetic reference cases across enrichment helpers. Balanced standard extrema select zero. Undefined/omitted families suppress all native adjusted P/significance under explicit retention. No phenotype-label permutations,resource retrieval,historical or whole-paper parity. See ENRICHMENT_WORKFLOWS.md"),
    "dose": ("upstream-adapter-validated", "Explicit original legacy DOSE4.5.0 generic ORA/GSEA in a separate sealed library;the main current4.6.0 dependency profile is unchanged. Multilevel/simple/DOSE permutation choices,annotated-query denominator and exact original core-enrichment helper audited. A negative first-hit boundary can exclude the first hit,and ES0 can retain a nonempty raw-helper core. No silent downgrade,DOSE3.24.2 parity,online annotation or full-paper reconstruction. See ENRICHMENT_WORKFLOWS.md"),
    "kallisto": ("upstream-adapter-validated", "Original official Windows x86_64 kallisto0.48.0 sealed index and bulk single/paired quantification,legacy bias,strand/fragment choices and every requested plaintext bootstrap. Native Java reference/FASTQ/model/identity/counter/effective-length TPM audits; actual fractional EM20.5+20.5,27packaged calls/8checks. Raw upstream metadata retained and only a verified Windows call-string defect reescaped in a derived audited copy. No HDF5/Sleuth,BUS/UMI,kb count,reference construction,DE,historical or full-paper parity. Failed0.52.0 source build retained separately. See KALLISTO_WORKFLOWS.md"),
    "trimmomatic": ("upstream-adapter-validated", "Original Trimmomatic0.39/0.41 single/paired trimming with simple/palindrome adapter clipping and full trim-log/header/span/base/quality/summary audits. Native Java13 ordered quality/length/MAXINFO operations, explicit Phred/mate/1000base contracts. Four paired/orphan outputs retained; optional private Java25, versioned archives and GPL notices. No bundled Illumina adapters, FLASH/quantification,0.32/0.36 or full-paper parity"),
    "magma": ("upstream-adapter-validated", "Original unmodified MAGMA 1.10 private Windows runtime: annotation, SNP-wise mean/top/multi, raw PC regression, competitive/self-contained sets, continuous properties and conditional/joint models. Native Java strand-aware gene-window mapping, PLINK/GWAS/build/N/identity checks and sealed gene correlations. Audited raw0/1 coding preserves controls and declared quantitative data. No restricted binary/source redistribution, FUMA/R wrappers, PoPS/scDRS, meta/interaction or historical/full-paper parity"),
    "scrublet": ("upstream-adapter-validated", "Original Scrublet 0.2.3 with pinned scientific sources/dependencies; explicit independent-library/raw-or-estimate count contract, exact or Annoy approximate neighbors, simulated parents, scores/errors, gene selection, thresholds and manifolds. Native Java verifies every Bayesian score/error from actual neighbors and exact identities. Upstream invalid-sqrt variance warning retained. No Scanpy/SnapATAC2, diffusion/cluster-median/consensus, historical or full-paper parity"),
    "sctransform": ("upstream-adapter-validated", "Distinct standalone sctransform 0.4.3 vst and Seurat 5.6.0 SCTransform, v1/v2, explicit glmGamPoi 1.24.0, count/estimate contracts and exact cell/feature maps. Model latent variables differ from Seurat post-fit regression; residual/corrected/log layers and RDS retained. Native supplied-model NB residual helpers validated. No integration, deconvolution, DE, historical or full-paper parity"),
    "featurecounts": ("upstream-adapter-validated", "Subread featureCounts 2.1.1 typed gene/peak/junction meta-feature counting. Explicit single/paired and read/fragment units, per-sample strand, NH missing-tag policy, overlap/multimapping/fraction/primary/duplicate filters and exact dictionaries. Native GTF/SAF, union lengths, splice and TPM/RPKM/FPKM helpers; raw annotation retained upstream. --primary disables NH fractional weighting upstream and is prominently warned. No Rsubread/custom juncCounts API, UMI, EDASeq/DE or historical/full-paper parity"),
    "minimap2": ("upstream-adapter-validated", "minimap2 2.31-r1302 explicit ONT/HiFi/splice/sr/assembly presets, canonical FASTQ SAM or FASTA/FASTQ PAF, secondary/MD/cs/eqx and noncanonical/minimum-DP-score options. Native exact PAF/CIGAR/query geometry and complete primary name/mate/base/quality audits. MD overrides cs upstream; smaller split indexes require both tags disabled. Target-span BED includes introns. No SV/variant calling, paftools, assembly filtering, old-version or full-paper parity"),
    "hisat2": ("upstream-adapter-validated", "HISAT2 2.2.3 linear or annotated splice graph indexes, explicit sealed small/large families and splice-aware single/paired RNA alignment. Native transcript splice/exon helpers, gzip staging and exact primary name/mate/base/quality/reference/RG audits. Explicit strand, intron, DTA, score, mapping limit and splice-learning choices; compiler warnings retained. No StringTie/Ballgown, quantifier, CIRIquant pseudo-reference, HISAT-genotype SNP/haplotype graph or old-version/full-paper parity"),
    "regenie": ("upstream-adapter-validated", "REGENIE 4.1.3 fit, quantitative/binary single-variant association and max/sum/comphet gene masks through a verified private runtime. Native Java exact FID/IID/coding/column/header checks and sealed phenotype/covariate/LOCO bundles; BED/BGEN/PGEN, explicit allele/build/missingness/RINT and Firth/SPA choices. Burden and RGC SKAT/ACAT/SBAT/GENE_P subset. Sum-mask BED export is unavailable and warned. No inferred binary model, HTP/REMETA, conditional/interaction/multitrait, old-version or full-paper parity"),
    "umap": ("upstream-adapter-validated", "UMAP via umap-learn 0.5.12; supplied PCA coordinates; real seeded CLI fixture validated"),
    "leiden": ("upstream-adapter-validated", "leidenalg 0.12.0 RB configuration partition on explicit weighted union kNN or supplied undirected nodes/edges; seeded real fixtures, original weights and isolates validated"),
    "louvain": ("upstream-adapter-validated", "igraph 1.0.0 multilevel partition on explicit weighted union kNN or supplied undirected nodes/edges; seeded real fixtures, original weights and isolates validated"),
    "harmony": ("upstream-adapter-validated", "Explicit R harmony 2.0.5 engine validated on Windows: seeded supplied-PCA batch correction, IDs/order and balanced batch/type fixture. Python harmonypy 2.1.0 remains unavailable on Windows after wheel/source failures; engines are not interchangeable coordinate references"),
    "cutadapt": ("upstream-adapter-validated", "Cutadapt 5.2 regular 3-prime adapter/quality trimming, fixed 5-prime cut and single/paired minimum-length filtering; exact/partial adapters and paired preservation validated"),
    "fastqc": ("upstream-adapter-validated", "FastQC 0.13.0 compiled Java application; validated Phred+33 FASTQ, per-file reports, read counts and retained WARN/FAIL findings; official archive checksum and dependency-file hashes retained"),
    "multiqc": ("upstream-adapter-validated", "MultiQC 1.35 aggregation of explicit FastQC ZIP/Cutadapt JSON inputs; exact sample naming and raw read totals checked across colliding original basenames; AI/version checks/uploads disabled"),
    "picard": ("upstream-adapter-validated", "Official hash-pinned Picard 3.5.0 typed SortSam coordinate/queryname, MarkDuplicates mark/remove, ValidateSamFile and reference-based CollectAlignmentSummaryMetrics; real SAM/BAM order, duplicates, removal, index, QC errors and alignment counts validated. Other programs/old-version parity remain open"),
    "samtools": ("upstream-adapter-validated", "Native Java FASTA index generation/validation plus SAMtools/HTSlib 1.24 typed BAM selection, coordinate/lexicographic sort, BAI indexing, flagstat, name-sort/fixmate/position-sort/markdup pipeline and textual mpileup. Known selected records, boundary regions, QC counts, duplicate pairs and pileup bases/depths validated. Other commands and published versions remain open"),
    "bcftools": ("upstream-adapter-validated", "BCFtools/HTSlib 1.24 mpileup/multiallelic calling with explicit uniform ploidy, reference normalization/allele splitting, QUAL soft filters, sample selection with allele-count updates, and stats. Known diploid/haploid SNV calls and genotype/allele fixtures validated. No inferred pool/mosaic/sex model or full old-version parity"),
    "bwa": ("upstream-adapter-validated", "BWA 0.7.19-r1273 index, MEM and backtrack aln/samse/sampe with explicit seeds, sample/RG and paired inputs. Unmodified upstream source built privately on Windows with a pinned MSYS2 toolchain. Known coordinates, strands, qualities, pairs and split-read reporting validated. BWA-MEM2 is a separate missing implementation; old-version and complete study-pipeline parity remain open"),
    "bowtie2": ("upstream-adapter-validated", "Official Bowtie 2 2.5.5 direct small/large index/align binaries, explicit end-to-end/local presets, seeded ordered gzipped single/paired DNA reads and dovetail/insert settings. Sealed reference/index hashes and every primary mate/strand-adjusted sequence/quality checked. No automatic trimming, uniqueness, duplicate or splice processing; older paper versions remain separate checks"),
    "gatk": ("upstream-adapter-validated", "Official hash-pinned GATK 4.7.0.0 HaplotypeCaller with explicit uniform ploidy and Java kernels, CombineGVCFs, GenotypeGVCFs and typed SNP/indel VariantFiltration. Native exact FASTA metadata helper, strict BAM/read-group/reference/gVCF contracts, preserved sites/genotypes/prior filters and explicit missing-annotation policy. Legacy realignment, Mutect2/somatic, BQSR/VQSR, capture/ALT pipelines and old-version parity remain open"),
    "star": ("upstream-adapter-validated", "STAR 2.7.11b typed bulk RNA index/alignment, raw GeneCounts and distinct Basic or supplied-junction two-pass workflows. Native exact-reference GTF/FASTQ checks, sealed index hashes, primary mate/base/quality audit and explicit strand/filter/overhang choices. Private Windows source build includes one documented transform.outQuant initialization fix; compiler warnings and failed attempts retained. BySJout uses bounded disk-backed SHA-256 content multisets because upstream disallows ordered output. STARsolo/WASP, UMI processing, fusions, legacy versions and full paper pipelines remain open")
}


def json_write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    papers = json.loads((DATA / "papers.json").read_text(encoding="utf-8"))
    gate_path=DATA / "isolated-validation.json"
    gate=json.loads(gate_path.read_text(encoding="utf-8")) if gate_path.exists() else {}
    full_test_count=gate.get("tests","unverified")
    required_suite_count=len(gate.get("required_native_and_adapter_suites",{}))
    registry = {t["id"]: t for t in json.loads((ROOT / "tools/literature/tool-registry.json").read_text(encoding="utf-8"))}
    assert len(papers) == 250
    counts = Counter(t["tool_id"] for p in papers for t in p["tools"])
    tools = []
    for tool_id, count in counts.most_common():
        tool = dict(registry[tool_id]); tool["paper_count"] = count
        tool["papers"] = [p["paper_id"] for p in papers if any(t["tool_id"] == tool_id for t in p["tools"])]
        if tool_id == "cellranger":
            tool["implementation_status"] = "excluded-by-user"
            tool["implementation_source"] = None
            tool["implementation_scope"] = "Excluded from implementation at the user's explicit request on 2026-10-26 after reviewing the restricted 10x license. No Cell Ranger, ARC or ATAC runtime was downloaded, installed or executed. Its 44 source roles remain catalogued, with GEX, ARC, ATAC, mkfastq-only, reused resources and downstream analyses distinguished. This exclusion is not an implemented integration or a failed scientific result. No installation action is required; leave the tool skipped unless the user explicitly reopens it. See CELLRANGER_SOURCE_REVIEW.md."
        elif tool_id == "apbs":
            tool["implementation_status"] = "upstream-adapter-validated"
            tool["implementation_source"] = "src/main/java/org/jlinalg/cli/ApbsToolsCli.java"
            tool["implementation_scope"] = 'Validated development original APBS3.5.0 solver via apbs-tools in apbs-workflow-candidate-3 (SHA-256 begins b1c26fa4),plus typed original PDB2PQR3.8.0 preparation and actual PyMOL3.1.0 potential-surface. Windows x86_64 prepares/solves;Linux Java in WSL renders. Every potential node,grid and raw energy is audited;raw total energy includes self-energy and is NOT solvation/binding free energy. Source-PQR zero radii remain. Explicit units,protonation,force field,grid,model,radius policy and ramp are required. Analysis never installs;missing,changed or forbidden dependencies skip3 with recovery;input/process/audit failures return2 and quarantine partials. Native electrostatic-grid needs only Java. Original DX long-token exponent truncation is prevented by canonical short float32 delivery with a complete exact float32 field hash;raw DX retained. Read APBS_WORKFLOWS.md,PDB2PQR_WORKFLOWS.md,POTENTIAL_SURFACE_WORKFLOWS.md and APBS_SOURCE_REVIEW.md. Historical/paper-specific reconstruction remains open;canonical a104 is unchanged.'
            tool["project_adapter_available"] = True
            tool["canonical_adapter_available"] = False
            tool["development_adapter_cli"] = "apbs-tools"
            tool["development_candidate"] = 'build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar'
            tool["development_candidate_sha256"] = 'b1c26fa467769d265115e9d5101f91eaa432da2df906e3a679086f68bfeb49ea'
            tool["available_native_method"] = "electrostatic-grid"
            tool["native_helpers"] = ["src/main/java/org/jlinalg/protein/OpenDxGrid.java", "src/main/java/org/jlinalg/protein/PqrStructure.java", "src/main/java/org/jlinalg/protein/ElectrostaticUnits.java"]
        elif tool_id == "pymol":
            tool["implementation_status"] = "upstream-adapter-validated"
            tool["implementation_source"] = "src/main/java/org/jlinalg/cli/PymolToolsCli.java"
            tool["implementation_scope"] = 'Validated development original PyMOL subset via pymol-tools in candidate pymol-candidate-4 (SHA-256 begins 09e4be51). Use Ubuntu 24.04 Linux x86_64 with CPython 3.12; on Windows run Linux Java inside WSL. Original rendering, measurements, align/super, bond/unbond, torsion and explicit canonical library mutations have independent native input, identity, numerical and graph audits. Keep full-precision final JSON ledgers: original mmCIF rounds coordinates, B factors and occupancy and omits bond categories. Supply the exact local validated wheel; install/download permission flags are explicit and analysis never installs. Missing, changed or forbidden dependencies skip with exit 3. Input/process/audit failures return 2 with quarantined partials. Typed PDB2PQR/APBS/potential-surface integration is available in workflow candidate b1c26fa4;read PDB2PQR_WORKFLOWS.md and POTENTIAL_SURFACE_WORKFLOWS.md. Manual interobject construction,paper-specific reconstruction and historical/commercial/full-paper parity remain open. Read PYMOL_WORKFLOWS.md and PYMOL_SOURCE_REVIEW.md. The separate native molecular-geometry helper needs only Java; canonical a104 is unchanged.'
            tool["available_native_method"] = "molecular-geometry"
            tool["native_method_candidate"] = "build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar"
            tool["native_method_candidate_sha256"] = "798d3a08b64107eb7a7679359902efae3591f6ae5a89075f8e9a428698cc32b3"
            tool["project_adapter_available"] = True
            tool["canonical_adapter_available"] = False
            tool["development_adapter_cli"] = "pymol-tools"
            tool["development_candidate"] = "build/literature-review/pymol-candidate-4/jlinalg-0.3.7.jar"
            tool["development_candidate_sha256"] = '09e4be51caf00fa252d8c37685e2cd043015a49875eadf131e96b0d0974985b2'
        elif tool_id in NATIVE:
            tool["implementation_status"], tool["implementation_source"], tool["implementation_scope"] = NATIVE[tool_id]
        elif tool_id in ADAPTERS:
            tool["implementation_status"], tool["implementation_scope"] = ADAPTERS[tool_id]
            tool["implementation_source"] = "src/main/java/org/jlinalg/cli/GatkToolsCli.java" if tool_id == "gatk" else "src/main/java/org/jlinalg/cli/ReadAlignmentCli.java" if tool_id in {"bwa", "bowtie2"} else "src/main/java/org/jlinalg/cli/HtsToolsCli.java" if tool_id in {"samtools", "bcftools"} else "src/main/java/org/jlinalg/cli/AlignmentToolsCli.java" if tool_id == "picard" else "src/main/java/org/jlinalg/cli/SequenceToolsCli.java" if tool_id in {"cutadapt", "fastqc", "multiqc"} else "src/main/java/org/jlinalg/cli/HarmonyRCli.java" if tool_id == "harmony" else "src/main/java/org/jlinalg/cli/CellEmbeddingCli.java"
            if tool_id == "fimo": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/FimoToolsCli.java"
            if tool_id == "beagle": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/BeagleToolsCli.java"
            if tool_id == "prscs": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/PrsCsToolsCli.java"
            if tool_id == "kallisto": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/KallistoToolsCli.java"
            if tool_id in {"clusterprofiler", "fgsea", "dose"}: tool["implementation_source"] = "src/main/java/org/jlinalg/cli/EnrichmentToolsCli.java"
            if tool_id == "star": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/StarToolsCli.java"
            if tool_id == "regenie": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/RegenieToolsCli.java"
            if tool_id == "magma": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/MagmaToolsCli.java"
            if tool_id == "trimmomatic": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/TrimmomaticToolsCli.java"
            if tool_id == "trimgalore": tool["implementation_source"] = "src/main/java/org/jlinalg/cli/TrimGaloreToolsCli.java"
            if tool_id in {"scrublet", "sctransform"}: tool["implementation_source"] = "src/main/java/org/jlinalg/cli/SingleCellQcCli.java"
            if tool_id in {"featurecounts", "minimap2", "hisat2"}:
                tool["implementation_source"] = "src/main/java/org/jlinalg/cli/" + {"featurecounts":"FeatureCountsCli", "minimap2":"MinimapToolsCli", "hisat2":"HisatToolsCli"}[tool_id] + ".java"
        elif tool_id == "deeptools":
            tool["implementation_status"] = "upstream-adapter-validated"
            tool["implementation_source"] = "src/main/java/org/jlinalg/cli/DeepToolsCli.java"
            tool["implementation_scope"] = f"Native supplied-signal comparison/scaling and eight original 3.5.6 workflows are in the canonical validated executable: BAM coverage,BigWig comparison,ATAC shifting,region matrices,profiles,heatmaps,BAM summaries and correlations. 23 API/CLI tests,4064 original arithmetic references,58 actual upstream workflow calls/20 independent checks and the{full_test_count}-test full gate passed. Exact model/reference/sample/region identities,explicit missing/zero/bin/reduction choices and no pickle loading. Constant columns fail with named recovery. ATAC drops SEQ/QUAL/ALL optional tags. GTF/BED12,multiple region groups,clustering,multiBigwigSummary,plotPCA,interactive Plotly,historical and full-paper parity remain open. See DEEPTOOLS_WORKFLOWS.md."
        else:
            tool["implementation_status"] = tool["current_coverage"]
            tool["implementation_source"] = tool["existing_source"]
            tool["implementation_scope"] = tool["coverage_note"]
        tool["validation_note"] = "New native subset has reference/property/CLI tests; complete upstream software parity is not claimed." if tool_id in NATIVE else "Real upstream CLI fixture validated; input and graph contracts remain narrower than the cited paper pipelines." if tool_id in {"umap", "leiden", "louvain"} else "Dependency-skip reporting validated; no Harmony scientific output was produced." if tool_id == "harmony" else "Existing overlap/adapters require paper-specific option checks; missing integrations remain pending."
        if tool_id in {"apbs", "pymol"}:
            tool["potential_surface_cli"] = "potential-surface"
            tool["preparation_cli"] = "pdb2pqr-tools"
            tool["workflow_candidate"] = "build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar"
            tool["workflow_candidate_sha256"] = "b1c26fa467769d265115e9d5101f91eaa432da2df906e3a679086f68bfeb49ea"
            tool["workflow_validation"] = "apbs-workflow-validation.json"
        if tool_id == "igv":
            tool["implementation_source"] = "src/main/java/org/jlinalg/cli/IgvToolsCli.java"
            tool["project_adapter_available"] = True
            tool["canonical_adapter_available"] = False
            tool["original_runtime_probe_only"] = False
            tool["development_adapter_cli"] = "igv-tools"
            tool["development_candidate"] = "build/literature-review/igv-candidate-3/jlinalg-0.3.7.jar"
            tool["development_candidate_sha256"] = "38889b96d55b6f7ec8d8a4a66cad8168cf8908429a920386936928bce1f93a28"
            tool["workflow_validation"] = "igv-workflow-validation.json"
            tool["implementation_scope"] = "Original IGV2.19.8 bounded igv-tools development command renders local FASTA/BED6/bedGraph/BigWig/SEG/BAM using a sealed Windows x64 fullJava21 runtime and hidden frame. Native exact reference/feature/scalar/missing/selected-read/raw-tag/full-count audits;explicit coordinates,units,scales,filters,overlapping mates and downsampling=false.126focused tests/13suites and126standalone perJava21/25;17packaged cases include12scientific workflows/5failure-skip controls.7,860reference bases,41,600integer counts and94selected-read comparisons. 18source roles/7supporting pointers,including P217,remain reviewed;current2426observations/623reviewed roles remain verified. Missing/changed dependencies skip3;input/process/audit failures return2 with retained partials and Required action. Read IGV_WORKFLOWS.md,IGV_SOURCE_REVIEW.md and IGV_RUNTIME_STATUS.md. Automatic CNA cohort-frequency/custom synteny summaries,methylation interpretation,manual diagnosis,unstated allele-depth choices and historical/full-paper parity remain unfinished. Canonical executable/release unchanged."
            tool["validation_note"] = "Tool-level bounded development integration validated;source-paper-specific reconstruction remains open. 27actual installer/integrity controls across v1/v2 include allowed official download,forbidden install/download,healthy reuse,corrupt/unmanaged/unlisted/changed homes and changed Java admission hashes. Original OAuth/logger diagnostics and ruler coordinate-label behavior are retained;actual scientific values and zero attempted URL requests are independently checked. All250source identities remain verified;the current catalog has2426observations,623reviewed summaries and153supporting pointers."
        if tool_id in {"saige", "saige-gene", "metasaige"}:
            tool["project_adapter_available"] = False
            tool["canonical_adapter_available"] = False
            tool["original_runtime_probe_only"] = tool_id == "saige"
            tool["implementation_scope"] = "SAIGE family project integration remains missing. Nineteen reviewed roles distinguish 16 SAIGE, 2 SAIGE-GENE(+) and 1 Meta-SAIGE uses; P147 published-summary reuse is newly recovered. Direct association, framework reuse, gene tests, LD and external-summary reuse remain distinct. Original unmodified SAIGE 1.5.2 built privately and ran a controlled binary pipeline with 1,000 participants and 32 variants, with bounded independent cohort, allele, count and score checks. There is no admitted runtime, installer or typed project workflow yet. Skip dependent project analyses until validated. The separate native unrelated-select component in development candidate 9786ba29 requires only Java. It applies explicit phenotype eligibility, ties and complete additive-GRM graph assertions. Its result is maximal, not guaranteed largest; it does not calculate a GRM, fit SAIGE or run association tests. Read SAIGE_SOURCE_REVIEW.md, SAIGE_RUNTIME_STATUS.md and UNRELATED_SELECTION_WORKFLOWS.md."
            tool["validation_note"] = "All 604 earlier review objects are preserved. The current catalog has 250 papers, 377 tools, 2,426 observations, 623 reviews and 153 supporting pointers. Native component: 32 tests in 5 suites, 32 standalone tests per Java 21 and 25, 501 independent full-decision graphs and 21 actual packaged cases. The original probe does not establish independent GLMM, SPA, Firth or historical/full-paper parity. SAIGE, SAIGE-GENE and Meta-SAIGE badges remain missing."
            if tool_id == "saige":
                tool["available_native_method"] = "unrelated-select"
                tool["native_method_candidate"] = "build/literature-review/saige-native-candidate-1/jlinalg-0.3.7.jar"
                tool["native_method_candidate_sha256"] = "9786ba29bd4dc92c43174af90e1ca643e5479cd5c8b824144a10635874059adf"
                tool["native_helpers"] = ["src/main/java/org/jlinalg/genetics/RelatedSampleSelection.java"]
                tool["native_method_validation"] = "unrelated-selection-validation.json"
                tool["runtime_intake"] = "saige-runtime-intake.json"
        if tool_id == "apbs":
            tool["validation_note"] = 'Two primary source roles reviewed;neither specifies an APBS version. P026 APBS74 is a citation suffix;nearby PyMOL versions are separate. No explicit PDB2PQR/PROPKA paper use was found in250hash-checked source XMLs. Earlier solver candidate:48 focused tests;10solver cases match6,013,002nodes;Born/charge-scaling and12runtime policies passed. New workflow candidate:107 focused tests/10suites,strictJava17,Javadoc,packaging and107standalone tests perJava21/25. Preparation:14independent original comparisons/8988atoms/44,940scalars,5controls and16installer/runtime policies. Surface:6actual packaged cases/1,647,750nodes,7controls and3nativeWindows skips. RealJava25 Unicode full preparation-to-solve-to-surface chain passed with exact provenance hashes. Read apbs-workflow-validation.json and stage WARNINGS.md;no full-paper equivalence.'
        if tool_id == "cellranger":
            tool["validation_note"] = "All 44 observed family roles and 26 supporting pointers passed source-identity and paragraph-hash checks. Scientific runtime validation is skipped by user request. A separately installed private Ubuntu WSL environment does not establish Cell Ranger availability or production capacity."
            tool["exclusion_reason"] = "user-requested-license-exclusion"
            tool["exclusion_date"] = "2026-10-26"
        if tool_id == "pymol":
            tool["validation_note"] = 'Twenty source roles and three supporting pointers are reviewed. All 30 new tests pass on Java 21 and 25. The focused 30-test gate, strict Java 17 compilation, Javadoc and executable packaging passed. Fourteen actual packaged Linux workflows and five semantic failure controls checked 57 steps, 24 measurements and 221 original inlier pairs, including all 20 canonical mutation targets, Cl/Br/Mg/Se symbols and three unchanged public recipes with relative Unicode paths. Six native Windows cases skipped with exit 3 and Linux/WSL recovery. Installer permissions, allowed public downloads, corrupt/linked caches, changed homes, valid poisoned bytecode and actual owned timeout termination were tested. Original align and super retain distinct gapped correspondences. Six actual packaged potential-surface cases and a complete preparation/solver/surface example are separately validated in workflow candidate b1c26fa4;read POTENTIAL_SURFACE_WORKFLOWS.md and apbs-workflow-validation.json. Full-paper/edition equivalence is not claimed. See pymol-adapter-validation.json.'
        if tool_id in {"cutadapt", "fastqc", "multiqc"}:
            tool["validation_note"] = "Real pinned upstream fixtures validated trimming, pair preservation, QC flags and multi-sample source counts/identities. All-filtered Cutadapt and source FastQC WARN/FAIL findings remain prominent through MultiQC console/status/WARNINGS.md plus source-qc.tsv. Published tool versions and unexposed options remain paper-specific compatibility work."
        if tool_id == "harmony":
            tool["validation_note"] = "Real R 2.0.5 CLI fixture passed identity alignment, deterministic repeat, batch-shift reduction and retained independent type signal on a balanced synthetic fixture. No general guarantee for confounded batches or Python/legacy Harmony parity."
            tool["engine_status"] = {"r": "upstream-adapter-validated", "python": "adapter-implemented-dependency-skipped-on-windows"}
        if tool_id == "picard":
            tool["validation_note"] = "Actual official 3.5.0 JVM application passed known record-order/index, one duplicate, explicit removal, clean/error validation and expected reference alignment counts. Upstream warnings retained; CLI 4 distinguishes QC errors from execution failure 2 and dependency skip 3. Published versions and unexposed programs remain paper-specific work."
        if tool_id in {"samtools", "bcftools"}:
            tool["validation_note"] = "Real 1.24 applications with HTSlib 1.24 passed independent known BAM/VCF fixtures, full paired duplicate pipeline, textual pileup, diploid/haploid calling and reference-mismatch/unknown-sample failures. Windows uses hash-pinned MSYS2 packages, not an upstream Windows release. Exposed operations are a subset; published versions and downstream study steps remain open."
        if tool_id == "samtools":
            tool["native_helper_source"] = "src/main/java/org/jlinalg/sequence/FastaIndexes.java"
        if tool_id in {"bwa", "bowtie2"}:
            tool["validation_note"] = "Real upstream fixtures validate reference-index families/hashes, gzipped input, known coordinates/strands/mismatches, complete primary mates and original qualities, local soft clips and split-read flags. BWA uses unmodified tagged source, not a guessed substitute or BWA-MEM2. Native logs and source/compiler/package identities retained. Full paper preprocessing/model/version reconciliation remains open."
        if tool_id == "seqtk":
            tool["validation_note"] = "620 actual tagged upstream reference runs (1.3/1.4/1.5 where applicable) freeze 48 sampling, 155 trimming, four telo settings and extraction. Native API/packaged checks verify exact memberships/qualities, 64-bit seeds, paired order, chunk boundaries, CRLF references, bounds failures and visible fallback/empty warnings. Count output follows seqtk -2 input order; native fraction 1 explicitly means all. Published unspecified/dirty settings remain separate."
        if tool_id == "gatk":
            tool["validation_note"] = "Real pinned Java-profile fixtures recover known heterozygous/homozygous SNPs and a 3-bp deletion, haploid interval selection and tetraploid dosage; joint genotyping preserves sample identities. Annotation boundary/missing policies and old filters are checked. Intermediate gVCFs and upstream Windows temp-probe warnings remain prominent. Typed interval lists avoid the Windows colon-parser failure; the initial failed attempt is retained."
        if tool_id == "star":
            tool["validation_note"] = "Known 200/300-base introns, exact gzip/lowercase/variable qualities, reverse strands, paired/orphan reads, multimapping, stranded raw gene counts, automatic/manual/novel-junction two-pass modes and actual BySJout filtering passed real fixtures. Native disk-spill content checks retain duplicate multiplicities. Missing runtime/annotation/overhang, wrong contig and corrupt indices reject with actionable diagnostics. Native GTF inspection needs no STAR. Windows source patch is hash-guarded and disclosed; full STARsolo or published-pipeline parity is not claimed."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/sequence/GtfAnnotations.java"
        if tool_id == "macs":
            tool["validation_note"] = "Native Java track-peaks passed 129 real upstream bdgpeakcall geometry/summit references plus independent API/packaged boundary, zero-fill, empty-result, precision and 64-bit checks. It needs no external runtime and does not fabricate P/q-values. MACS callpeak remains unavailable: the private Windows 3.1.0 known-truth fixture crashed with access violation 0xc0000005; AnnData/HDF5 import also failed. The native-subset badge does not validate those failed programs or a MACS external adapter. Native Java genomic-signal means/state enrichment are also available for supplied tracks. See TRACK_PEAK_WORKFLOWS.md and MACS_RUNTIME_STATUS.md."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/genetics/BedGraphSignals.java"
            tool["engine_status"] = {"native_track_peaks":"native-subset-validated", "alignment_callpeak":"scientific-runtime-validation-failed-on-windows", "hmmratac":"dependency-unavailable-on-windows"}
        if tool_id == "regenie":
            tool["validation_note"] = "Real pinned source runtime and Java adapter passed quantitative/binary BED/BGEN/PGEN statistics against frozen direct-upstream outputs, 0/1 versus 1/2 coding, allele sign reversal, dominant/recessive tests, exact/approximate Firth and SPA, RINT/categorical covariates and distinct observed sample universes. Independent generated signal recovered its known effect. Gene max/sum/comphet and RGC metadata/SKAT/ACAT/SBAT/GENE_P tested; model corruption/build mismatch and unavailable dependencies produce actionable failures/skips. Private Windows Cygwin build has two disclosed BGEN compatibility edits, unchanged REGENIE scientific sources and retained compiler warnings. Explicit LF prediction-list writing avoids an observed Windows parser failure. This is a tested subset, not whole-paper or old-version parity. See REGENIE_WORKFLOWS.md."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/genetics/RegenieTables.java"
        if tool_id == "ucsc-liftover":
            tool["validation_note"] = "Native API/CLI and real executable-JAR checks passed forward/reverse strands, gapped spans, exact minMatch boundaries, ambiguous/all-gap/no-chain outcomes, gzip, integer/reference failures, size caps and exact/overlap reciprocal policies. Four independently sourced primary dbSNP coordinate pairs agree in both directions through actual UCSC chains. Diagnostic float percentages never decide mapping. Whole-tool, split/variant/metadata and old published-pipeline parity remain open. Existing PanUkbbGwasOutcome already uses the same engine for narrower SNP-instrument projection. See COORDINATE_LIFT_WORKFLOWS.md."
        if tool_id in {"featurecounts", "minimap2", "hisat2"}:
            tool["validation_note"] = {
                "featurecounts": "Real official Windows 2.1.1 adapter recovered independent gene/peak/splice-junction counts, reads versus fragments, both strand directions, ambiguous/multimapping fractional allocations, primary-weighting behavior, duplicate filtering and multiple sample identities. Native union/splice/normalization fixtures and explicit zero/invalid input/runtime failures passed. Rsubread API and old-version workflows remain separate.",
                "minimap2": "Unmodified source runtime and Java adapter recovered known HiFi/ONT-style, reverse, spliced and paired-short-read coordinates; assembly PAF CIGAR and target BED geometry, unmapped queries and multi-part index merging were checked. MD/cs precedence and split-prefix incompatibility are explicit. Native PAF mismatch/duplicate-tag failures and dependency skips passed.",
                "hisat2": "Unmodified source runtime and Java adapter recovered known annotated/unannotated splice and paired RNA alignments through small and forced-large index families. Native exon/splice helper geometry, exact primary content, strand/RG, unmapped outcomes, changed index and bad pair/site failures were checked. Existing read-only Cygwin used; 213 compiler warnings and 20 omitted dangling research test-data links are retained."
            }[tool_id] + " See RNA_ALIGNMENT_COUNTING_WORKFLOWS.md; complete paper/version parity is unvalidated."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/sequence/" + ("PafAlignments.java" if tool_id == "minimap2" else "GeneFeatures.java")
        if tool_id in {"scrublet", "sctransform"}:
            tool["validation_note"] = ("Actual original-package fixture on 252 synthetic cells/600 genes, 756 native neighbor-score/error cases, exact and approximate cosine CLI paths, strict/no-call thresholds, partial-failure and dependency skip contracts; exact barcodes and Seurat-sensitive feature IDs preserved. " if tool_id == "scrublet" else "Actual standalone vst v1/v2 and Seurat v1/v2 phase-regression fixtures; 400 supplied-model residual references and 400 Seurat regressed positions, layer/metadata/memory/identity and runtime-failure checks. ") + "See SINGLE_CELL_QC_WORKFLOWS.md. Full-paper/version parity remains open."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/sequence/" + ("DoubletScores.java" if tool_id == "scrublet" else "NegativeBinomialResiduals.java")
        if tool_id == "trimmomatic":
            tool["validation_note"] = "4236 independent actual step-object reference outputs across0.39/0.41 and all13native operations; original SE/PE four fates, simple/palindrome keep-both, Phred conversion, MAXINFO and contract/runtime failure fixtures. Literal MINLEN-before-SLIDINGWINDOW and forward-only cropping retained. TRAILING one-base behavior and unavailable/old JVM recovery are prominent. See TRIMMOMATIC_WORKFLOWS.md;0.32/0.36 and complete paper parity remain open."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/sequence/ReadQualityTrimmer.java"
            tool["engine_status"] = {"native_quality_length_maxinfo":"native-subset-validated", "original_0.39_0.41":"upstream-adapter-validated", "historical_0.32_0.36":"not-validated"}
        if tool_id == "magma":
            tool["validation_note"] = "Actual original 1.10 program and Java CLI recover804 independently frozen association rows across four models, exact995variant/202gene mapping, pathway/property truth, conditional/joint models and complete0/1 versus1/2 sample universes. Coordinate/build/model/tamper failures and missing-runtime skip recovery checked. Upstream program/source/manual remain private and unmodified; no whole-paper/version parity. See MAGMA_WORKFLOWS.md."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/genetics/GeneWindowMapping.java"
        if tool_id == "deeptools":
            tool["validation_note"] = f"All eight exposed original methods passed actual executable checks on the same JAR as{required_suite_count} required suites and{full_test_count} full tests. Matrices match hand-calculated TSS/TES/center/scaled-body values; all six profile reductions,region ordering,bin spacing,BED counts and Pearson/Spearman policies passed. PDF/SVG were independently rendered and inspected; original small-window kb labels,SVG font fallback and sparse-scatter visibility need review. Every omitted region remains listed; model tampering,object arrays,constant samples and resource/dependency failures are explicit. See deeptools-matrix-validation.json and DEEPTOOLS_WORKFLOWS.md."
            tool["native_helper_source"] = "src/main/java/org/jlinalg/genetics/DeepToolsSignals.java"
            tool["engine_status"] = {"native_supplied_signal":"native-subset-validated", "original_eight_typed_workflows":"upstream-adapter-validated", "unexposed_package_options":"not-validated"}
        if tool_id == "kallisto":
            tool["native_helpers"] = ["src/main/java/org/jlinalg/sequence/KallistoTables.java"]
            tool["engine_status"] = {"windows-x86_64-official-0.48.0": "upstream-adapter-validated-bulk-plaintext", "cygwin-source-0.52.0": "build-failed-retained", "HDF5-Sleuth-BUS-UMI": "unavailable-through-this-adapter"}
            tool["validation_note"] = "Twelve API/CLI tests,original independent point/seeded-bootstrap fixtures and27actual executable calls/8checks passed. Counts are fractional estimates; raw/reference/effective lengths differ. Paired supplied Gaussian fragments override empirical estimates. Official Windows metadata is retained byte-for-byte; only the launched call string is reescaped in a separately hashed run-info-audited.json copy. A direct HDF5-capability probe exited0 while omitting requested outputs,so plaintext and exact output inventory are mandatory. Eight actual paper uses are reviewed; P108compatible examples are excluded as execution evidence.0.43/0.44/0.46,personalized-reference generation,BUS/UMI and whole-paper parity remain open. See kallisto-adapter-validation.json,kallisto-packaged-validation.json and KALLISTO_WORKFLOWS.md."
        if tool_id == "tximport":
            tool["validation_note"] = "Original unmodified R tximport1.40.0 supplied204 frozen import cases/15615scalar values;11API/CLI tests plus44actual executable calls validate all four scaling modes,format imports,matching/omission/fallback and failure recovery. Counts are fractional estimates. Reconstructed counts must not also receive a length offset. Bootstrap/Gibbs uncertainty is explicitly dropped; original1.14.2 and full-paper parity remain open. See tximport-native-validation.json,tximport-native-packaged-validation.json and TXIMPORT_WORKFLOWS.md."
            tool["engine_status"] = {"native_dense_point_estimates":"native-subset-validated", "R_or_quantifier_runtime_required":False, "unexposed_formats_uncertainty_historical":"not-validated"}
        if tool_id in {"clusterprofiler", "fgsea", "dose"}:
            tool["native_helper_source"] = "src/main/java/org/jlinalg/enrichment/GseaStatistics.java"
            tool["validation_note"] = "Sixteen native/CLI tests include897 independent original-R arithmetic cases and actual generic ORA/GSEA calls. Known hypergeometric tails,engine-specific query denominators,balanced scores,legacy core definitions,literal IDs,UTF-8,seed/sampling/tail/family and dependency recovery are checked. Native score alone computes no NES/P-value. Supplied annotations and historical/default/full-paper compatibility remain separate. See enrichment-adapter-validation.json,enrichment-packaged-validation.json and ENRICHMENT_WORKFLOWS.md."
            tool["engine_status"] = {"native_running_score_only":"native-helper-validated-no-inference", "original_current_clusterProfiler_enrichit":"upstream-adapter-validated", "original_fgsea_1.38":"upstream-adapter-validated", "explicit_legacy_DOSE_4.5":"upstream-adapter-validated", "historical_versions_and_annotation_retrieval":"not-validated"}
        if tool_id == "trimgalore":
            tool["native_helpers"] = ["src/main/java/org/jlinalg/sequence/TrimGaloreReads.java"]
            tool["engine_status"] = {"native_post_cutadapt_finalization_and_hardtrim":"native-helper-validated", "original_0.6.7_0.6.10_ordinary_hardtrim":"upstream-adapter-validated", "unexposed_historical_and_full_paper":"not-validated"}
            tool["validation_note"] = f"Twelve original audit/CLI tests and 13 native helper tests ran in the {full_test_count:,}-test full gate. The original adapter passed 25 packaged calls with 22 independent original comparisons, including an actual incomplete-adapter warning with its primer caveat. Native finalization/hardtrim matched 1,692 original read observations across 38 cases and 40 packaged calls. Missing or forbidden dependencies skip with exit 3; input/execution/audit errors return 2 with retained partials and required actions. Windows Unicode paths use a UTF8 options-file with an ASCII recipe path. No identifiers are silently renamed. P122 version/crop attribution remains ambiguous. See trim-galore-adapter-validation.json and TRIM_GALORE_WORKFLOWS.md."
        if tool_id == "flash-merge":
            tool["native_helpers"] = ["src/main/java/org/jlinalg/sequence/FlashFastqIO.java", "src/main/java/org/jlinalg/cli/FlashReadsCli.java"]
            tool["validation_note"] = f"Eighteen API/CLI tests, 5,171 frozen actual-original cases and the {full_test_count:,}-test full gate passed. There are 53 packaged calls: 45 workflow comparisons with 45 independently launched original FLASH references across 5,185 pairs, six invalid-input controls and two additional Unicode recipe checks under Temurin 21/Oracle 25. Exact merged/unmerged partitions, headers, sequence, quality and counts passed. Long reference mates span 1,501 to 4,096 bases; a separate hand-golden test covers a 100,000-base perfect overlap. P075 unstated options and P091 forward-only HEADCROP preparation remain explicit. No FLASH installation is required; failures return 2 with quarantined partials and recovery. See flash-native-validation.json, flash-native-packaged-validation.json and FLASH_WORKFLOWS.md."
        if tool_id == "fimo":
            tool["native_helpers"] = ["src/main/java/org/jlinalg/sequence/DnaPssm.java", "src/main/java/org/jlinalg/sequence/MemeDnaMotifs.java", "src/main/java/org/jlinalg/sequence/DnaMotifScanner.java", "src/main/java/org/jlinalg/cli/MotifScanCli.java"]
            tool["engine_status"] = {"native_DNA_PSSM_and_P_scan":"native-subset-validated-no-q-values", "original_5.5.9_private_Windows_GNU_profile":"upstream-adapter-validated", "stock_Cygwin_parser_failure":"retained-not-used", "PSP_protein_historical_alternate_host_full_paper":"not-validated"}
            tool["validation_note"] = f"Seven primary-paper uses preserve P/q thresholds, allele-score completion and HOMER/conservation boundaries. The {full_test_count:,}-test full gate and all {required_suite_count} required suites passed, including 48 new tests: 32 native and 16 original API/CLI cases. Native PSSM references include 118 original function models/24,069 words and independent short-null enumeration; 72 packaged native workflows compare 6,805 sites. The original adapter has 96 packaged P/q workflows, 31,643 complete sites plus 16 quarantined incomplete sites, 17 controls and 208 actual original processes. Java 21/25 Unicode and actual timeout termination passed. Native P needs no external runtime; original q requires the permitted exact sealed Windows profile. Missing dependencies skip with exit 3; truncation/errors fail with exit 2 with named recovery and no successful partial table. Original no-Q zero placeholders become blank canonical fields with a warning, raw reports retained. No replacement BH, custom empirical calibration, PSPs, full-format/historical/full-paper parity or commercial permission is inferred. See FIMO_WORKFLOWS.md, fimo-native-validation.json, fimo-upstream-validation.json and FIMO_VALIDATION_STATUS.md."
        if tool_id == "prscs":
            tool["validation_note"] = "218 focused tests across 25 required suites passed with zero failures, errors or skips. Each of Java 21 and 25 passed 30 native tests and 8 live command tests; 12 installer policy tests also passed. All 208 output files from 56 unmodified original runs reproduced from the revised sealed installation. Native checks covered 1,728 allele triples, 592 marginal values and 1,040 unit relationships. Twelve actual packaged workflows and 28 controls checked 240 model rows, 520 posterior scalars, 120 psi fields, 200 explicit score weights and 44 exact original output files. Actual upstream edge controls cover version 1.1.0 title-case flags and integral thinning rules. Twelve paper roles distinguish simulation, common-variant inference, score application, tuning and ancestry combination. Synthetic checks do not establish chain convergence or predictive calibration. Development candidate, canonical history and public release remain separate. See prscs-adapter-validation.json."
            tool["native_helpers"] = ["src/main/java/org/jlinalg/genetics/PrsCsMath.java", "src/main/java/org/jlinalg/cli/PrsCsInputs.java", "src/main/java/org/jlinalg/cli/PrsCsAudits.java", "src/main/java/org/jlinalg/cli/PrsCsOutputs.java"]
            tool["development_candidate"] = "build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar"
            tool["development_candidate_sha256"] = "33b26e76b989ce3387d3965f4b625a6e4a02e3e2b55d8bccd90bb1e48981a764"
            tool["project_adapter_available"] = True
            tool["canonical_adapter_available"] = False
            tool["development_adapter_cli"] = "prs-cs-tools"
        if tool_id == "beagle":
            tool["validation_note"] = "176 focused tests passed with zero failures/errors/skips, plus eleven standalone tests per Java 21/25 and fourteen installer policy tests. Twenty-four original reference workflows independently checked 144,096 genotype cells and 576,000 posterior fields; forty-two actual packaged workflows and twenty-four failure controls checked 244,992 cells and 950,400 posterior fields on Java 21/25. Exact original GT/DS/AP/GP, observed calls, exclusions, opaque identifiers and Unicode paths passed. Actual timeout termination and changed-runtime preservation passed. Synthetic contract checks do not establish biological accuracy or posterior calibration. Nine paper roles and the P209 new-5.4 versus reused-5.2 distinction are source-reviewed. Development candidate validation is separate from the older canonical full gate and from a public release. See beagle-adapter-validation.json."
            tool["native_helpers"] = ["src/main/java/org/jlinalg/cli/BeagleInputs.java", "src/main/java/org/jlinalg/cli/BeagleOutputs.java"]
            tool["development_candidate"] = "build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar"
            tool["development_candidate_sha256"] = "5ed54e44acc9d50bb5af240418a5497b9c235506ecc45f318bad9168bd867cac"
            tool["canonical_adapter_available"] = False
        if tool_id in {"plink", "plink2"}:
            tool["implementation_source"] = "src/main/java/org/jlinalg/genetics/PlinkBedLdClumper.java"
            tool["validation_note"] = "Release names, alpha builds, citations and genuine dual-family source uses are reviewed. File-format/URL mentions alone are excluded. The broader adapter remains in progress, while the validated development candidate contains typed original autosomal hard-call GLM/QC, LD pruning and exact/randomized PCA subsets through plink-tools. Their separate original comparisons, focused/package checks and immutable candidate proofs remain distinct; the older canonical a104 lacks this command. The family retains method-overlap because broader options, PRS training, historical and full-paper parity remain open. See PLINK_SOURCE_REVIEW.md and each paper-specific cohort, model, unit and annotation scope."
            tool["project_adapter_available"] = True
            tool["canonical_adapter_available"] = False
            tool["development_adapter_cli"] = "plink-tools"
            tool["development_candidate"] = "build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar"
            tool["native_helper_source"] = "src/main/java/org/jlinalg/genetics/DiploidScores.java"
            tool["native_helper_cli"] = "genotype-score"
            tool["implementation_scope"] += " Separately validated native genotype-score applies supplied weights to resolved diploid human autosomal BED hard calls. Explicit ea/oa and supplied effect-allele frequencies, error/mean-impute/omit policies, sum/average/observed versus denominator counts and all-missing unreportability. Java only. See GENOTYPE_SCORE_WORKFLOWS.md. Typed original GLM/QC/pruning/PCA subsets are available in the development candidate; the broader family retains method-overlap."
            tool["validation_note"] += " Native score API/BED/CLI passed 41 tests, six actual original comparison workflows with 2,007 reported values, 670 native sample rows and 23 packaged controls. Fresh Java 17 API consumers ran on Java 21/25. Existing overlap status and the PlinkBedLdClumper pointer remain for the broader tool family."

        if tool_id in {"viper", "area"}:
            tool["validation_note"] = "Explicit P134 source use is reviewed. No restricted VIPER runtime was installed or run;applicable noncommercial/nonprofit eligibility remains pending. metaVIPER is a separate ensemble and aREA is not the native GSEA running score. See ENRICHMENT_WORKFLOWS.md."
        tools.append(tool)
    # Implemented utilities remain visible even if a tool is absent from this sample.
    json_write(DATA / "tool-inventory.json", tools)
    journals = dict(Counter(p["journal_abbreviation"] for p in papers))
    years = dict(sorted(Counter(p["publication_year"] for p in papers).items()))
    summary = {"papers": len(papers), "journals": journals, "years": years,
               "paper_tool_observations": sum(counts.values()), "distinct_named_tools": len(counts),
               "implementation_status_counts": dict(Counter(t["implementation_status"] for t in tools)),
               "window_start": "2023-10-23", "window_end": "2026-10-23", "window_extended": False,
               "literature_status": "250-source-catalog-built; semantic-completeness-review-open",
               "goal_status": "implementation-in-progress",
               "user_excluded_tools": {"cellranger": "User requested exclusion on 2026-10-26 after restricted-license review; retain source roles, skip implementation and runtime analysis."},
               "dependency_policy": "Install/download where environment permits; if a required dependency cannot be installed, skip the dependent analysis and prominently report error, affected analysis and exact recovery steps."}
    json_write(DATA / "summary.json", summary)
    bibliography = []
    markdown = ["# Omics and GWAS paper catalog", "",
        f"{len(papers)} papers; {len(tools)} named tools; {sum(counts.values())} paper-tool observations.", "",
        "Window: 2023-10-23 through 2026-10-23, inclusive. No extension was required.", "",
        "This is a purposive open-access full-text sample, not a systematic or representative literature review. "
        "Methods-section extraction and context checks supply source-linked tool uses. The registry-bounded inventory "
        "can omit software or choose a partial description; semantic completeness review remains open. "
        "A related OmicsMatic method does not establish upstream package equivalence.", "",
        "[Searchable offline view](../../../site/literature-review.html) · [Tool inventory](tool-inventory.json) · "
        "[Implementation queue](implementation-plan.md) · [Warnings and next actions](WARNINGS.md)", ""]
    for p in papers:
        markdown += [f"## {p['paper_id']}: {p['title']}", "",
                     f"{p['journal']} · {p['first_publication_date']} · [DOI]({p['publisher_url']}) · [Primary full text]({p['fulltext_url']})", "",
                     "| Tool | How used | Evidence location |", "| --- | --- | --- |"]
        for tool in p["tools"]:
            cells = [tool["tool"], tool["use_summary"], tool["evidence_section"]]
            markdown.append("| " + " | ".join(c.replace("|", "\\|").replace("\n", " ") for c in cells) + " |")
        markdown += ["", f"XML SHA-256: `{p['xml_sha256']}`. Exact evidence paragraph hashes and locators are in [papers.json](papers.json).", ""]
        title = p["title"].replace("{", "\\{").replace("}", "\\}")
        bibliography.append(f"@article{{jlinalg_{p['paper_id']},\n  title = {{{title}}},\n  journal = {{{p['journal']}}},\n  year = {{{p['publication_year']}}},\n  doi = {{{p['doi']}}},\n  url = {{{p['publisher_url']}}}\n}}")
    (DATA / "catalog.md").write_text("\n".join(markdown), encoding="utf-8")
    (DATA / "bibliography.bib").write_text("\n\n".join(bibliography) + "\n", encoding="utf-8")
    plan = ["# Literature-driven implementation queue", "",
        "The implementation goal is active. A catalog entry is not a claim that its upstream tool has been implemented.", "",
        "User-approved approach: native Java where practical, tested upstream adapters for substantial external tools. "
        "Downloads and installations are allowed where the environment permits them. "
        "Unavailable dependencies must skip the dependent analysis and produce prominent actionable diagnostics.", "",
        "Prioritize repeated workflows, scientific input contracts, real upstream validation, packaged CLI behavior, and clear scope. "
        "Do not replace specialized methods with similarly named approximations.", "",
        "| Tool | Papers | Current implementation | Scope and remaining work |", "| --- | ---: | --- | --- |"]
    for tool in tools:
        plan.append(f"| {tool['name']} | {tool['paper_count']} | {tool['implementation_status']} | {tool['implementation_scope'].replace('|', '/')} |")
    plan += ["", "Next: sequencing/QC and GWAS profiles, followed by less frequent workflows. "
             "UMAP, supplied-graph Leiden/Louvain, R Harmony and four typed Picard operations have real fixture validation. Python Harmony remains dependency-skipped on this Windows environment. "
             "Reconcile novel software links and remaining ambiguous matches before claiming exhaustive tool coverage.", ""]
    (DATA / "implementation-plan.md").write_text("\n".join(plan), encoding="utf-8")
    warnings = [
        "**APBS RUNTIME / MODEL / ENERGY REVIEW:** apbs-tools in development workflow candidateb1c26fa4 runs the untouched official32bit Windows x86 APBS3.5.0 on Windows x86_64. Required action: where permitted run install_apbs.py with --home, --cache and --allow-install;add --allow-download only when permitted. Analysis never installs. Missing/changed/forbidden dependencies skip3. Explicit PQR charge model,Angstrom units,grid,equation,boundary,dielectrics,temperature and salt are required. Every potential value is audited;upstream exit0 with NaNs fails2 and quarantines partials. Raw total energy includes self-energy and is NOT solvation/binding free energy. Native electrostatic-grid needs only Java;declare units/outside policy,retain missing rows and valid finite zeros. Read APBS_WORKFLOWS.md,WARNINGS.md,session.json and original logs;correct the named issue and rerun to a new directory. Typed preparation and potential-surface are now available in workflow candidate b1c26fa4;read PDB2PQR_WORKFLOWS.md,POTENTIAL_SURFACE_WORKFLOWS.md and apbs-workflow-validation.json. Historical/source-paper reconstruction and alternate profiles remain open.", "",
        '**NATIVE MOLECULAR GEOMETRY REQUIRES EXPLICIT UNITS AND PAIRS:** use the validated development molecular-geometry-candidate-1 executable (SHA starts798d3a08);canonical a104 is unchanged. Native distance/angle/torsion and fixed-pair proper rigid fits need only Java. Declare angstrom or nanometer;nanometers multiply by10. Fits require explicit error or allow-nonunique,retain every supplied pair,and never infer sequence/atom correspondence,scale,reflection,outlier rejection or periodic images. Undefined measurements fail;explicitly allowed nonunique rotations warn. Read WARNINGS.md,session.yaml,analysis-status.json and fit-pairs.tsv. Correct named input/unit/pair/geometry/limit errors and rerun to a new directory;exit2 quarantines scientific partials. Read MOLECULAR_GEOMETRY_WORKFLOWS.md.', "",
        '**PYMOL ORIGINAL SUBSET: LINUX RUNTIME AND EXPORT REVIEW REQUIRED:** Validated development original PyMOL subset via pymol-tools in candidate pymol-candidate-4 (SHA-256 begins 09e4be51). Use Ubuntu 24.04 Linux x86_64 with CPython 3.12; on Windows run Linux Java inside WSL. Original rendering, measurements, align/super, bond/unbond, torsion and explicit canonical library mutations have independent native input, identity, numerical and graph audits. Keep full-precision final JSON ledgers: original mmCIF rounds coordinates, B factors and occupancy and omits bond categories. Supply the exact local validated wheel; install/download permission flags are explicit and analysis never installs. Missing, changed or forbidden dependencies skip with exit 3. Input/process/audit failures return 2 with quarantined partials. Typed PDB2PQR/APBS/potential-surface integration is available in workflow candidate b1c26fa4;read PDB2PQR_WORKFLOWS.md and POTENTIAL_SURFACE_WORKFLOWS.md. Manual interobject construction,paper-specific reconstruction and historical/commercial/full-paper parity remain open. Read PYMOL_WORKFLOWS.md and PYMOL_SOURCE_REVIEW.md. The separate native molecular-geometry helper needs only Java; canonical a104 is unchanged.', "",
        '**CELL RANGER SKIPPED BY USER REQUEST:** Cell Ranger, ARC and ATAC implementation/runtime analysis are excluded following the user\'s 2026-10-26 license decision. No original runtime was downloaded, installed or executed. All 44 paper roles remain visible for provenance; this is not a successful integration or a negative biological result. No installation action is required. Leave this tool skipped unless the user explicitly reopens it. Read CELLRANGER_SOURCE_REVIEW.md.', "",
        '**BEAGLE DEVELOPMENT BUILD, PHASE AND EXCLUSIONS REQUIRE ACTION:** use build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar (SHA-256 5ed54e44acc9d50bb5af240418a5497b9c235506ecc45f318bad9168bd867cac). Its 176 focused tests, 42 packaged workflows and 24 failure controls passed. The older canonical a104 full-gate build is unchanged and lacks beagle-tools/plink-tools; no new full-project gate or release is claimed. Supply an exact pinned private Beagle home, explicit map/assembly/panel/model choices and phase/exclusion policies. Modern 5.1/5.4 can rephase partially missing provided phase; preserve-provided rejects incompatible retained inputs. Reference workflows exclude target-only markers only with an explicit error/drop-with-warning decision. Keep original GT separate from rounded GP. Where permitted run python tools/literature/install_beagle.py --java JAVA_EXECUTABLE --allow-install; add --allow-download only for permitted missing official downloads. Missing, changed or forbidden software skips exit 3; failures return 2, retain logs and quarantine canonical partials. Review WARNINGS.md, both ledgers, session.yaml and analysis-status.json; correct the named issue and rerun into a new directory. Read BEAGLE_WORKFLOWS.md.', "",
        '**NATIVE GENOTYPE SCORE INPUTS AND MISSINGNESS REQUIRE ACTION:** genotype-score needs only Java and applies supplied weights to resolved diploid human autosomal BED hard calls. Declare exact ea/oa,build and error/mean-impute/omit. Mean imputation requires explicit effect-allele frequencies;no target-cohort inference. Observed alleles and average denominators differ with imputation. Fully imputed samples are conditional reference expectations;no-observation omission produces blank unreportable scores,not zero. A wholly unreportable cohort or invalid ID/allele/file/limit fails2with named recovery and scientific outputs under partial/. Correct the cause,preserve inputs and rerun to a new directory. Review sample counts and WARNINGS.md. Native scoring needs no original PLINK installation. Read GENOTYPE_SCORE_WORKFLOWS.md;original QC/PCA/GWAS adapters and weight estimation remain open.', "","# Warnings, errors, skipped analyses, and required actions", "",
        "**NATIVE FLASH HEADER/INPUT REVIEW:** flash-reads needs no external runtime and requires an explicit Phred33/64 encoding. Original cleanup converts non-ACGT ASCII bases to N; original merged headers remove the last slash suffix, even inside comments, retaining /X# barcodes. Use the JSON-escaped original/merged mapping in read-status.tsv before downstream joins and inspect normalization counts. Strict mate IDs are the default; explicit positional pairing always warns. Maximum overlap caps the score denominator, not physical overlap eligibility. No merges produces a QC warning; malformed records, empty files or mismatched pairs fail2 with quarantined partials and no successful results.tsv. Correct the named cause and rerun to a new directory. Windows Unicode paths use an ASCII-named UTF8 JSON recipe. See FLASH_WORKFLOWS.md.", "",
        "**PLINK SOURCE IDENTITIES AND REPLAY REQUIRE REVIEW:** release-2 spelling, genuine release-1/2 uses and GWAS appendix roles are source-checked; file formats and URLs are not executions. The project PLINK adapter remains in progress: native supplied-weight genotype-score is separately validated, while original QC/PCA/GWAS and PRS weight estimation remain open. Preserve each cohort, phenotype model, missing-data rule and supplied-weight origin. Resolve P200 bp versus --clump-kb units, P019 HWE wording, P242 generic HWE shorthand and P112 covariate wording before replay. Do not transfer P150 binary firth-fallback to quantitative phenotypes. Tested original1.9 rejects sum plus no-mean-imputation; original2 requires memory>=640MiB. Retain unsupported-option errors rather than silently changing the statistic. Read PLINK_SOURCE_REVIEW.md. Missing or forbidden required dependencies must skip the dependent analysis with a named cause and exact recovery.", "",
        "**FIMO SOURCE THRESHOLDS ARE DISTINCT:** P003 reports P<1e-5, P007 uses --text --thresh 1e-4 plus a separate custom empirical score-completion procedure, P068 retains q<0.01, and P217 reports P<0.001. Text-only original FIMO does not compute q-values. Do not fill unreported allele matches with zero, equate empirical-tail calibration with the original dynamic-programming null, transfer HOMER E<0.001 to FIMO, or adjust only a thresholded hit list. Seven primary uses are reviewed; native P and admitted original P/q subsets are validated separately. Custom empirical calibration remains a separate operation.", "",
        '**FIMO RUNTIME AND INFERENCE REQUIRE ACTION:** native motif-scan needs only Java. Original P/q requires the exact admitted private Windows x86_64 FIMO 5.5.9/Cygwin/GNU-regex profile and declared educational/research/nonprofit use. Where permitted run python tools/literature/install_fimo.py --home NEW_PRIVATE_HOME --reference-home build/omics-fimo-5.5.9-gnurx-reference --cache build/literature-review/fimo-runtime --intended-use educational-research-nonprofit --allow-install; add --allow-download only for permitted source-archive downloads. The helper reuses the existing authenticated reference build; it does not bootstrap alternate builds or hosts. If absent, changed, unsupported or installation forbidden,SKIP original analysis with exit 3; native P remains available. Stored-hit truncation fails with exit 2 and quarantines even empty exit-0 tables. Raise the cap or restrict the declared analysis before rerunning; empty capped output is not absence of binding. Full no-Q zeros are placeholders, blank in canonical results; raw reports remain unchanged. Keep tested cohorts distinct from nucleotide backgrounds and use unique allele IDs. Original estimator/small-cohort warnings remain visible. No silent normalization, BH substitution or missing-allele zero score. Read FIMO_WORKFLOWS.md and WARNINGS.md.', "",
        "**ORIGINAL TRIM GALORE REQUIRES ACTION:** trim-galore-tools runs unmodified0.6.7/0.6.10 with Cutadapt5.2 and optional original FastQC0.13.0. Declare Phred33/64;ordinary headers/comments must be ASCII,while hardtrim preserves UTF8headers. On tested Windows Cp1252 launchers place Unicode data paths in a UTF8JSON --options-file with an ASCII recipe path. No identifier renaming is silent. Missing,changed,forbidden or unavailable dependencies SKIP exit3;input/execution/audit failures exit2 with retained partials and no successful results.tsv. Where permitted use install_trim_galore.py --environment NEW_PRIVATE_HOME --allow-install --allow-download with an existing Cygwin Perl/Python,or explicit --prepare-python for a new private pinned environment. Analysis never installs. Read TRIM_GALORE_WORKFLOWS.md for exact commands and recovery.", "",
        "**TRIM GALORE ORIGINAL EDGE CASES AND QC:** default orphan35 can be below pair minima and rescue precedes maximum length,so oversized or both separate orphans can survive. Uppercase N is literal;0.6.10single fractional N handling differs from paired. Auto small-RNA can default to minimum18. Review every output stream,original Cutadapt warning blocks (including their primer caveat),trimming reports and promoted FastQC WARN/FAIL/encoding diagnostics before downstream use. A50base crop differs from a minimum-length filter. WGBS does not imply RRBS;reported default poly-A wording does not establish --poly_a. P122 wrapper/Cutadapt version/flag ambiguity remains. No RRBS,explicit poly-A,historical-version or full-paper parity.", "",
        "**ENRICHMENT DEPENDENCIES REQUIRE ACTION:** native enrichment-tools score needs only Java;original ORA/GSEA needs the exact permitted Windows x86_64 R4.6.1 private profile. Where permitted run Rscript --vanilla tools/literature/install_enrichment_dependencies.R build/omics-enrichment-r build/literature-review/enrichment-dependency allow. Explicit legacy DOSE additionally needs install_dose_legacy.R with a separate library and the main library;current DOSE4.6 is never silently downgraded to4.5. Missing,changed,unsealed,forbidden or unsupported dependencies SKIP affected analysis with exit3 and exact recovery. Preserve existing/incomplete libraries;correct the cause and select a new path. Installation seals its locally resolved dependency closure;compare marker/package versions with the linked scientific evidence and validate any new closure before interpretation. See ENRICHMENT_WORKFLOWS.md.", "",
        "**ENRICHMENT INFERENCE AND FAMILY REQUIRE REVIEW:** declare exact annotation,ranks/ties,universe/query mapping,tail/exponent,algorithm,seed and sampling settings. Current clusterProfiler/enrichit,fgsea and legacy DOSE have distinct balanced-score,query-denominator and core-enrichment rules;legacy negative first-hit exclusion and ES0/nonempty cores are retained visibly. Storey q-values are not BH-adjusted P-values. Native scores alone have no NES/P inference. Undefined/zero-resolution/omitted hypotheses fail or remain explicitly unreportable;an incomplete family suppresses all native adjusted P/significance. Read reported-family.tsv,all audits and original raw objects. No phenotype-label permutation,online annotation,historical or whole-paper parity is inferred.", "",
        "**VIPER/aREA ORIGINAL-ENGINE VALIDATION IS HELD:** the catalog now records the explicitly named VIPER package and aREA method separately from metaVIPER. No restricted VIPER runtime was installed or executed. Applicable noncommercial/nonprofit license eligibility remains unanswered;do not install/run it until confirmed or an appropriate separate license is supplied. Native GSEA is not aREA. See ENRICHMENT_WORKFLOWS.md and the linked official license.", "",
        "**KALLISTO RUNTIME AND OUTPUT REQUIRE ACTION:** validated bulk index/quantification uses the exact original official0.48.0 Windows x86_64 private profile,retaining legacy --bias. Where permitted run python tools/literature/install_kallisto.py --allow-download,then supply --home build/omics-kallisto-0.48.0 or JLINALG_KALLISTO_HOME. Missing,changed,forbidden or unsupported dependencies return3 and SKIP affected analysis; provision this permitted sealed profile before retrying. This Windows executable has NO HDF5 output: an actual probe exited0 but omitted HDF5/bootstrap files. The adapter always requests plaintext and checks every requested draw. HDF5/Sleuth requires a separate permitted HDF5-enabled profile plus adapter validation; BUS/UMI/kb count is not implemented. The original0.52.0 Cygwin build failed on platform/linkage issues and remains retained separately; do not reuse that failed build or infer0.52validation. See KALLISTO_WORKFLOWS.md and kallisto-dependency-status.json.", "",
        "**KALLISTO MODEL, ESTIMATES AND METADATA REQUIRE REVIEW:** declare exact transcript/contig/personalized-reference identity,strict or upstream ambiguous-base/U/poly-A processing,library,strand,fragment distribution,bias,bootstrap count and seed. Supplied paired Gaussian fragments override empirical estimation; inspect promoted upstream warnings. Fractional estimated counts are not observed integer reads; raw and effective lengths differ and TPM uses count/effective-length. No-mapping,changed model,mate mismatch,resource or output failures return2 with retained diagnostics and named recovery. The original Windows run_info.json can contain invalid backslashes in its call field: preserve it unchanged and use the separately hashed run-info-audited.json,which reescapes only the verified launched call string. No numeric repair occurs. Reference construction,gene aggregation,DE and full-paper/historical parity require separate work.", "",
        "**TRANSCRIPT IMPORT UNITS AND LENGTH CORRECTION REQUIRE ACTION:** native transcript-import reads dense Salmon/kallisto/RSEM isoform/TSV point estimates and needs no R or quantifier runtime. Fractional estimated counts are not raw observed reads. Choose no,scaledTPM,lengthScaledTPM or transcript-only dtuScaledTPM explicitly. Reconstructed counts must not also receive a length offset; no-scaling gene counts need the downstream package's appropriate effective-length normalization workflow. --inferential-replicates drop explicitly excludes bootstrap/Gibbs uncertainty. Inspect every omitted transcript,zero-expression length fallback,reference/annotation ID and library total. RSEM gene files,HDF5,sparse/alevin,quantification,DE and historical/full-paper parity remain open. Input/resource/zero-denominator failures return2 with retained partial diagnostics and named recovery. Read TXIMPORT_WORKFLOWS.md.", "",
        "**The complete implementation goal is still in progress.** Missing tools in the implementation queue are not completed integrations.", "",
        "**DEEPTOOLS DEPENDENCY AND DATA-LOSS ACTIONS:** eight original3.5.6 workflows pass the Java/full-gate/actual-JAR checks: BAM coverage,BigWig comparison,ATAC shifts,region matrices,profiles,heatmaps,BAM summaries and correlations. ATAC DROPS SEQ,QUAL AND ALL OPTIONAL SAM TAGS: preserve the original BAM and sort/index the derived BAM before indexed use. Matrix/summary models seal exact sample,region,reference and file identities. Choose missing values,zero rows,binning and reductions explicitly; constant correlation columns fail with named recovery. No object/pickle arrays,implicit normalization or automatic data removal. Unavailable/changed runtime skips3; errors2 retain partials and no successful result. Native Windows stock-pip setup remains unavailable: use the tested prepared private Cygwin profile with its v390font cache/private libpython,or an administrator-prepared profile on a new host. If installation is forbidden,skip until a matching profile is supplied. Native helpers need no Python. Unexposed package options and historical/full-paper parity remain open. Read DEEPTOOLS_WORKFLOWS.md and each WARNINGS.md.", "",
        "**DEEPTOOLS FIGURES REQUIRE PRESENTATION REVIEW:** original small-window axis labels use coarse kb precision:20bp flanks can appear as0.0Kb. Inspect exact coordinates in model.json/profile data and annotate axes accurately before publication. SVG retains DejaVuSans text references; missing viewer fonts can change appearance. Embedded-font PDF avoids that specific portability issue. Sparse scatter fixtures can show very small bins/edge points; inspect the numerical correlation and source counts. SE/STD over supplied regions is descriptive and does not establish biological-replicate uncertainty. Independent PDF/SVG raster/visual checks are recorded separately from routine structural vector checks.", "",
        "**TRIMMOMATIC ORDER, MATE AND ENCODING REQUIRE ACTION:** declare Phred33/64 and every ordered step. P091 trims only forward reads; P171 literally places MINLEN36 before SLIDINGWINDOW4:20, so later shortened reads can be below36bases. Native13-step quality/length/MAXINFO output agrees with4236original0.39/0.41 references. TRAILING rejects one-base reads; uppercase N contributes quality0. MAXINFO fails above1000bases at that step; no automatic crop. Phred64 cannot represent scores above62 in ASCII FASTQ: explicitly select TOPHRED33 when needed. Review all four paired/orphan outputs and retained-read counts. No0.32/0.36/full-paper parity. Read TRIMMOMATIC_WORKFLOWS.md.", "",
        "**TRIMMOMATIC/JAVA UNAVAILABLE: SKIP AFFECTED UPSTREAM ANALYSIS:**0.41 requires Java25+;0.39 requires Java8+. Where permitted run python tools/literature/install_trimmomatic.py --version 0.41 --private-java --allow-download, or supply an existing supported --java. Private installation changes no OS/PATH; verified reuse needs no download permission. Missing/changed/forbidden runtime skips3 with exact cause/action; failure2 retains partials and no successful results.tsv. Native steps need no external application. ILLUMINACLIP uses actual assay adapters in the original program; stock Illumina sequences have separate rights and are not bundled. FLASH merging, Salmon/StringTie and DE are separate; P226 TPM/DESeq2 wording requires input reconciliation.", "",
        "**COORDINATE CONVERSION REQUIRES EXPLICIT REFERENCES AND UNMAPPED HANDLING:** native coordinate-lift needs the full exact source/target dictionaries, directional chain, minMatch and --unmapped retain or error. "
        "Every original interval/status is retained; ambiguous/gap/low-match, target-span and reciprocal failures are never silently discarded. minMatch is aligned SOURCE bases, not sequence identity. Destination bounding spans can include gaps. "
        "No chromosome aliases, split/BED12, allele/REF/indel normalization, peak-identity matching or orthology inference is performed. Retain requires reviewing warnings/results/diagnostics; error publishes only retained partial diagnostic tables if any input is unacceptable. "
        "The Java engine is bundled and needs no external runtime/install. Supply correct local chain/reference data from a permitted source; no automatic downloads occur. Missing/wrong data fails with cause and exact recovery in WARNINGS.md. "
        "Source evidence distinguishes direct UCSC use from rtracklayer/MungeSumstats workflows, CrossMap chain URLs, pre-lifted GENCODE resources and custom pangenome mapping. Read COORDINATE_LIFT_WORKFLOWS.md.", "",
        "**REGENIE MODEL AND DEPENDENCY CHOICES REQUIRE ACTION:** use regenie-tools with explicitly declared trait/coding, missingness, RINT, genome build and allele order. Quantitative 0/1 data remains quantitative; binary/Firth/SPA is never inferred. "
        "Step 2 uses sealed phenotype/covariate/LOCO bytes from the fit and rejects changed models or absent eligible predictions. Review exact matched sample counts, NA cells, omitted tests and convergence warnings before interpretation. "
        "Private Windows source setup where permitted: python tools/literature/install_regenie.py --allow-download --allow-source-build --cygwin-root C:/cygwin64; then --runtime build/omics-regenie. Missing/forbidden runtime skips analysis with exit 3 and prominent recovery. "
        "Existing Cygwin is read-only; two BGEN compatibility edits and compiler warnings are retained. Upstream recommends Linux/macOS or WSL. This profile cannot read/write gzipped phenotype/covariate/result text. "
        "Sum-mask BED genotype export is unsupported and omitted with a warning; membership lists/results remain. Comphet collapsing is not proof of variants in trans or pathogenicity. HTP/REMETA, conditional/interaction/multitrait and old-version/full-paper parity remain open. Read REGENIE_WORKFLOWS.md.", "",
        "**MAGMA PRIVATE RUNTIME AND PHENOTYPE CODING REQUIRE ACTION:** original MAGMA1.10 is downloaded privately and unmodified; its authors prohibit binary/source redistribution and modification. No upstream program/manual/source is bundled in OmicsMatic. Where permitted run python tools/literature/install_magma.py --allow-download and supply --home build/omics-magma; unavailable/forbidden runtime skips3 with exact recovery. MAGMA treats zero as missing under binary detection: declared0/1 controls map to1/2, while explicitly quantitative0/1/2 data receive an audited+3 affine shift to preserve the requested model. Original FAM remains unchanged. Read MAGMA_WORKFLOWS.md.", "",
        "**MAGMA GENE UNIVERSE AND LD/N CHOICES:** verify exact SNP/gene/reference coordinates and build, ancestry-matched LD and total/per-variant GWAS N; do not substitute panel/effective N or infer PC regression from summary P-values. Duplicate/zero/missing SNP P-values fail until explicitly repaired. Empty/omitted genes, unmatched variants and set members are retained with statuses. Properties require explicit missing-value/gene policies; competitive background, technical corrections and outlier truncation require review. Gene Z scores express unsigned association strength. FUMA,MAGMA.Celltyping,PoPS,scDRS,orthologues and cross-trait correction remain separate. No historical/full-paper parity inferred.", "",
        "**SINGLE-CELL MODEL WARNINGS REQUIRE REVIEW:** original Scrublet 0.2.3 emits an upstream invalid-sqrt variance-fitting warning on the synthetic fixture; exported scores/errors are finite and independently verified, but the warning is retained. Review input/library assumptions and score distributions before removing cells. Automatic threshold failure retains scores with NA calls; no cells are silently removed. Raw UMI and declared fractional/processed estimates are separate contracts. Scanpy/SnapATAC2, custom diffusion/cluster-median/consensus and manual calls remain distinct. Read SINGLE_CELL_QC_WORKFLOWS.md.", "",
        "**SCTRANSFORM LAYERS AND RUNTIMES:** standalone vst and Seurat SCTransform differ; model latent variables are not Seurat post-fit regression. Residual, corrected-count and logdata layers are separate; RCTD-derived estimates and spatial profiles need explicit model justification. Exact feature-ID aliases are retained for Seurat-sensitive names. The output-value limit is not an internal model RAM guarantee. No integration, DE or reference annotation is inferred. Where permitted use install_scrublet.py --allow-install --allow-source-build (Windows x86_64 Python3.12 and existing C++ compiler), or Rscript --vanilla install_sctransform_dependencies.R LIBRARY REPORT allow seurat-v2. Missing/forbidden dependencies skip3 with exact recovery; failed partials are retained. See SINGLE_CELL_QC_WORKFLOWS.md for complete commands, the official Bioconductor mirror and v1/v2/fallback boundaries.", "",
        "**RNA/FEATURE COUNTING REQUIRES EXPLICIT LIBRARY CHOICES:** feature-counts requires single/paired and reads/fragments; modern -p alone does not count fragments. Select actual library strand and review NH missing-tag assumptions. "
        "Subread 2.1.1 --primary disables NH fractional weighting even with --fraction; overlapping-feature fractional allocation remains. Native genomic-length TPM/FPKM is separate from transcript effective-length abundance and DE testing. Rsubread/custom juncCounts APIs remain pending. Read RNA_ALIGNMENT_COUNTING_WORKFLOWS.md.", "",
        "**MINIMAP2 TAG/INDEX AND HISAT2 REFERENCE CHOICES:** minimap2 2.31 emits MD rather than cs when both are selected. Multi-part --split-prefix requires --md false --cs none; target-span BED includes introns. "
        "HISAT2 uses sealed small/large indexes, exact reference/RG and native splice geometry; parallel novel-splice learning can depend on scheduling. -k=1 or suppressed secondary output does not establish unique mapping. No CIRIquant pseudo-reference or StringTie quantifier is supplied.", "",
        "**RNA RUNTIMES/COMPILER WARNINGS REQUIRE REVIEW:** where permitted run python tools/literature/install_rna_aligners.py --profile featurecounts --allow-download. Select --profile minimap2 or --profile hisat2 for those source profiles, additionally supplying --allow-source-build and an existing Cygwin gcc/g++/make/zlib-devel toolchain. "
        "No OS/global install occurs. Missing/forbidden dependencies skip the affected analysis with exit 3 and exact recovery. Unmodified minimap2/HISAT2 builds retained 2/213 compiler warnings; 20 exact dangling HISAT2 research test-data links were omitted and recorded. Inspect installer attempt logs and runtime provenance before extending the tested scope. Read RNA_ALIGNMENT_COUNTING_WORKFLOWS.md.", "",
        "**MACS CALLPEAK FAILED ON THIS WINDOWS RUNTIME — SKIP AFFECTED ANALYSES:** the private MACS3 3.1.0 source build installed, but its known-truth BEDPE callpeak test crashed with access violation 0xc0000005. "
        "AnnData/HDF5 also failed to import because a DLL procedure was unavailable; HMMRATAC/AnnData-dependent analyses are unavailable. No successful callpeak result or project MACS adapter is provided. "
        "Do not use build/omics-macs3-ucrt as a validated analysis runtime. Use a permitted Linux/macOS host with Python 3.12+, install the pinned upstream version and compiler prerequisites, and pass actual scientific fixtures before analyzing data. "
        "Exact setup, retained errors and validation boundaries are in MACS_RUNTIME_STATUS.md and macs-runtime-attempt.json. If that environment cannot be installed or supplied, keep these analyses skipped; no WSL/OS service is provisioned automatically.", "",
        "**NATIVE GENOMIC SIGNAL REQUIRES EXPLICIT MISSING-DATA AND BACKGROUND CHOICES:** genomic-signal needs the exact reference dictionary and --missing zero or error. "
        "Sparse zero filling, zero-state NA and positive pseudocount changes remain prominent. State enrichment uses every base in the declared genome, not only represented positions. "
        "These native means/log2 ratios do not call peaks, normalize libraries, compute MACS P-values or establish full SPIN-study parity. Read GENOMIC_SIGNAL_WORKFLOWS.md before choosing the signal units, reference and pseudocount.", "",
        "**NATIVE TRACK PEAKS ARE A SUPPLIED-SCORE SUBSET:** track-peaks thresholds an existing track; it does not estimate a treatment/control background, normalize libraries or generate P/q-values. "
        "Its positive float32 geometry/summits have 129 actual MACS3 bdgpeakcall references. Choose cutoff, minimum span, maximum gap, precision and missing policy explicitly. "
        "Zero filling, float32 rounding, no-hit results and nonpositive cutoffs remain prominent. Upstream bdgpeakcall expects continuous tracks; reconcile sparse gaps before comparison. Native zero/signed summits use the mathematical maximum and do not reproduce upstream zero-reset quirks. "
        "The native-subset MACS badge does not validate the failed alignment-based caller, HMMRATAC, old published versions or full study pipelines. Read TRACK_PEAK_WORKFLOWS.md for commands and recovery.", "",
        "**R HARMONY IS AVAILABLE ON WINDOWS:** the explicit R harmony 2.0.5 engine installed and passed real analysis fixtures. "
        "Use `cell-embed --method harmony --engine r --rscript RSCRIPT --r-library build/omics-harmony-r` with the matrix, metadata and batch options documented in README.md. "
        "**Python harmonypy 2.1.0 remains skipped:** Windows wheels were unavailable and the authorized MSVC source build failed. No Python Harmony scientific result was produced; the separate R engine is not a silent fallback or a coordinate-parity claim.", "",
        "**Literature completeness:** the 250 source records are available, but tool extraction remains registry-bounded and semantic completeness review is open. "
        "Use the full-text locators and paragraph hashes to inspect an observation; do not infer every upstream package feature was used.", "",
        "**Scientific scope:** native overlap and tested subsets do not establish full equivalence with PLINK, Seurat, DESeq2, LDSC, BEDTools, GSVA, UCell or AUCell. "
        "The signature score CLI documents its tie, cutoff, missing-gene and normalization rules.", "",
        "**Sequencing QC findings:** FastQC WARN/FAIL flags are preserved in its HTML/ZIP reports, results.tsv and prominent WARNINGS.md. "
        "Review the flagged modules and assay/library context before downstream analysis. A QC flag is not an execution failure or an automatic data-discard rule; MultiQC retains the source findings in its aggregated report.", "",
        "**PICARD VALIDATION ERRORS REQUIRE ACTION:** `alignment-tools --method validate` returns **4** when validation ran and found errors. "
        "Read `validation.txt` and `WARNINGS.md`, correct the named SAM/BAM issues, and rerun validation before downstream analysis. "
        "Warnings alone return 0 and remain prominent; execution failure returns 2 and unavailable dependency returns 3. "
        "Use `python tools/literature/install_picard.py --allow-download` only where permitted, then `--picard build/omics-picard/picard.jar` with Java 17+. "
        "If downloads are forbidden, skip these analyses until an administrator supplies the pinned runtime. The helper preserves unmanaged/corrupt installations. No CRAM or implicit reference download is exposed.", "",
        "**Picard JVM warnings:** newer JVMs can report restricted/native-access calls from Picard dependencies even with JDK compression selected. "
        "These warnings are promoted to the console and `WARNINGS.md`; the tested fixture still completed. Select a Java 17/21 runtime explicitly with `--java JAVA` if they occur. "
        "For optical-name warnings, review the original read names, instrument geometry and `--optical-distance` before interpreting optical duplicate counts.", "",
        "**Source retrieval:** one screened source returned HTTP 500 and was excluded from selection. "
        "The catalog still has 250 different eligible source records. No user action is required for this excluded source. "
        "Preserve the frozen cohort; any retry of the excluded source must remain a separate retrieval audit rather than resampling the catalog.", "",
        "**Dependency policy:** a failed or forbidden installation must mark affected analyses as skipped, retain the error/log, and state how to enable the required software. "
        "Successful installation alone is not an analysis-validation result.", ""]
    warnings += ["## SAMtools and BCFtools runtime and scientific scope", "",
        "Windows x86_64 can use `python tools/literature/install_hts_dependencies.py --allow-download`, then executables under `build/omics-hts/ucrt64/bin/`. "
        "This private runtime uses 38 checksum-pinned MSYS2 UCRT64 packages (SAMtools, BCFtools and HTSlib 1.24 plus declared dependencies). It does not install OS packages or modify global PATH. "
        "If downloads are forbidden, omit the allow flag: unavailable dependencies produce a skipped analysis with recovery instructions. Existing unmanaged/corrupt environments are preserved; choose a new directory after correcting the cause.", "",
        "**Calling assumptions require an explicit choice:** `bcf-call --ploidy 1|2` uses uniform ploidy and a depth cap; it does not infer pooled, mosaic, sex-specific or mitochondrial biology. "
        "No alternate call is not evidence of complete callable coverage. `sam-mpileup` emits raw pileup, not pseudo-haploid genotypes. "
        "Published older versions and unexposed commands require separate checks. Read README.md for flag masks, coordinates, BAQ, quality/depth thresholds, soft filters and retained sample order.", ""]
    warnings += ["**A FASTA index must match the actual bytes:** LF/CRLF changes invalidate its offsets even if the sequence letters match. "
        "Native `hts-tools --method fasta-index --input reference.fa --out NEW_DIR` generates a checked `.fai` without an external executable; place it beside the exact original uncompressed FASTA, retaining any previous index for audit. "
        "Variant processing and Picard alignment metrics reject mismatched lengths, offsets or wrapping before use. Compressed references need a separate `.gzi`-aware workflow.", "",
        "**Output publication failures remain visible:** the external HTS workflow retries transient Windows directory-access failures briefly. If publication still fails, "
        "the console names a retained attempt with `failed-publication` status, manifest and `WARNINGS.md`. Release file/directory locks or choose a new writable destination and rerun; an existing destination is never overwritten.", ""]
    warnings += ["## Native sequence transformations and quality findings", "",
        "`sequence-tools` runs sampling, trimfq, subseq and telo directly in Java; no external executable is required. "
        "**MINIMUM-LENGTH FALLBACK CAN RETAIN POOR-QUALITY BASES:** trimfq is not a read-level quality filter. "
        "Review its counted fallback reads and quality reports; the CLI promotes these findings to WARNINGS.md and a console notice. "
        "Zero-length trimmed mates and empty sampled results also require explicit downstream handling before alignment.", "",
        "Fraction sampling is probabilistic; exact-count sampling uses a two-pass index reservoir (eight bytes per selected record/pair). "
        "Twenty-five million indices require about 200 MB plus JVM/buffers. Native --fraction 1 means all records; choose --count for exact counts. "
        "Count order matches seqtk -2, not its one-pass reservoir-slot order.", "",
        "**NO TERMINAL REPEAT HIT IS NOT BIOLOGICAL ABSENCE:** inspect the supplied motif, sequence orientation, score/drop settings and exact assembly. "
        "Extraction uses strict headerless BED3, forward orientation and request order; invalid bounds fail instead of clipping/skipping. "
        "Source/old indexes are preserved. Assembly editing, dirty/unreported paper versions and full downstream workflows remain separate. Read NATIVE_SEQUENCE_WORKFLOWS.md for commands and recovery.", "",
        "**REFERENCE-BUILD WARNINGS RETAINED:** the three upstream seqtk reference builds completed with 11, 13 and 19 compiler warnings respectively. "
        "Their exact source/compiler identities and warning excerpts are in seqtk-reference-build.json; full logs remain in the research cache. "
        "Review these when regenerating references. The native Java analysis does not execute those C binaries or require a compiler installation.", "",
        "**MULTIQC PRESERVES SOURCE QC FINDINGS:** all-filtered Cutadapt outputs and source FastQC WARN/FAIL now appear in the top-level warning file, console, completed-with-qc-warnings status and source-qc.tsv. "
        "Inspect the sample-specific findings, preserved source reports and assay context before downstream analysis. Successful aggregation does not mean every source passed QC; no automatic data-discard decision is made.", ""]
    warnings += ["## STAR setup, source fix, filtering and quantification", "",
        "**PRIVATE WINDOWS STAR BUILD:** where permitted run `python tools/literature/install_star.py --allow-download --allow-source-build`, then supply `--star build/omics-star/bin/STAR.exe`. "
        "The pinned private MSYS2/SSE4.1 build has local fixture evidence; upstream lists Linux/macOS support. Forbidden/unavailable required runtime skips analysis with exit 3 and exact recovery. Existing unmanaged runtimes are preserved. Native GTF inspection needs no external runtime. Read STAR_WORKFLOWS.md.", "",
        "**RECORDED STAR SOURCE FIX:** the pinned source leaves transform.outQuant uninitialized. Expanded Windows fixtures failed while trying to load /geneInfo.tab; "
        "a documented one-line initialization fix is applied with original/patched source hashes and retained failed builds/tests. This is patched source, with compiler warnings retained, and no rewritten scoring/alignment algorithm. "
        "Review the source-build report before using unvalidated operations or changing compilers.", "",
        "**STAR GENE COUNTS REQUIRE LIBRARY-STRAND CHOICE:** raw count columns are unstranded, read-1 strand and read-2 strand. Select using the actual library protocol. "
        "These counts do not perform UMI deduplication, normalization or featureCounts-equivalent quantification. STARsolo barcode/UMI quantification and WASP/Hornet mapping-bias control remain separate missing integrations.", "",
        "**STAR FILTER THRESHOLDS BOTH APPLY:** absolute and fractional match/score thresholds remain active; paired fractional thresholds use combined mate length and can reject an otherwise matching orphan mate. "
        "Inspect explicit settings and native mapping totals before changing thresholds. BySJout rejects input-order output upstream; the adapter retains native order and checks primary content with a bounded disk-backed SHA-256 multiset audit. "
        "Basic and supplied-junction two-pass modes are distinct; unannotated-index insertion requires an explicit overhang. NoSharedMemory and native FASTQ decompression avoid shared-memory services, shell pipelines and FIFOs.", ""]
    warnings += ["## GATK model, intermediate-output and missing-annotation decisions", "",
        "Install the verified official local 4.7.0.0 JAR privately where permitted: `python tools/literature/install_gatk.py --allow-download`. "
        "Use Java 17+; there is no implicit Conda/Spark/Python installation. Forbidden/unavailable required runtime skips analysis with status 3 and exact recovery. Native reference metadata generation needs no GATK executable. Read GATK_WORKFLOWS.md.", "",
        "**GVCF IS AN INTERMEDIATE:** HaplotypeCaller gVCFs and CombineGVCFs outputs contain reference blocks/<NON_REF>; genotype them with the intended cohort/model before association or variant interpretation. "
        "Ploidy is required explicitly and is uniform; no sex, mitochondrial heteroplasmy, pool or mosaic model is inferred. Modern germline calling does not implement legacy GATK indel realignment or Mutect2 somatic workflows.", "",
        "**CHOOSE MISSING-ANNOTATION BEHAVIOR:** hard-filter requires --missing-policy pass or fail and retains GATK compound-expression semantics. "
        "Missing QD/rank-sum/other metrics are counted and warned; absence of a filter label does not establish that every metric was available and passed. "
        "The typed defaults are P248 study thresholds, not universal assay recommendations. Records/genotypes/previous filters are preserved; biallelic/depth/population selection remains explicit separate work.", "",
        "**OBSERVED WINDOWS TEMP-PROBE WARNING:** 4.7.0.0 can warn that it failed to delete a temp test file after skipping the POSIX-only executable probe, with a null-path stack trace. "
        "The successful Java-kernel truth fixtures retain this diagnostic. Inspect actual exit/status/output validation and the full log; nonzero analysis exits still fail with quarantined partials. "
        "Typed interval-list files avoid the separate colon-region Windows parser failure; that initial failed attempt remains in validation history.", ""]
    warnings += ["## BWA and Bowtie 2 alignment setup and boundaries", "",
        "Use `python tools/literature/install_aligner_dependencies.py --profile bowtie2 --allow-download` for the checksum-pinned official Windows Bowtie 2 2.5.5 archive. "
        "BWA setup additionally needs explicit source-build permission: `--profile bwa --allow-download --allow-source-build`. "
        "The private MSYS2 build uses unmodified upstream 0.7.19 source and pinned compiler packages, without OS installation or global PATH changes. "
        "If installation or building is forbidden, skip the dependent analysis until a validated runtime is supplied; the helper reports the exact recovery step and preserves unmanaged environments.", "",
        "**BWA-MEM2 is separate:** its paper observations were previously conflated with BWA. The BWA 0.7.19 adapter does not implement BWA-MEM2, its published versions or its options. "
        "The implementation queue retains that missing integration. Pipeline/platform uses recovered during this review (including nf-core/eager, Mondrian, Isabl, DeStruct and LUSH) also remain separate work.", "",
        "**Compiler warnings remain visible:** the BWA source build succeeded with upstream compiler warnings retained in its dependency report and build.log. "
        "Review them before changing compiler settings or using unvalidated operations/input types. Installation success and fixture validation do not establish every published workflow's equivalence.", "",
        "Aligner output is unsorted SAM, preserving unmapped reads. It performs no inferred trimming, uniqueness filtering, duplicate removal, splice mapping or ALT-aware postprocessing. "
        "Select the correct reference, algorithm and paired settings explicitly; use the existing typed HTS/Picard stages afterward where appropriate. Read ALIGNMENT_WORKFLOWS.md for exact commands and diagnostics.", ""]
    dependency = ROOT / "build/literature-review/dependencies/dependency-status.json"
    isolated_report = DATA / "isolated-validation.json"
    if isolated_report.exists():
        validation = json.loads(isolated_report.read_text(encoding="utf-8"))
        if validation.get("status") == "passed":
            warnings += ["## Validation scope and optional test skips", "",
                "The isolated baseline plus toolbox passed the full check with " + str(validation["tests"]) + " tests, "
                + str(validation["failures"]) + " failures and " + str(validation["skipped"]) + " optional skips. "
                "The exact baseline, executable hash and per-test skip reasons are in isolated-validation.json. Native t-SNE and the real upstream cell-adapter checks executed without skips.", "",
                "Two public-genetics live tests are opt-in; to exercise them, set `JLINALG_LIVE_PUBLIC_TEST=true` and explicitly rerun the selected tests with `--rerun-tasks`. "
                "Three checks require the optional CHOLMOD JNI backend; build/install that backend for the host before rerunning them. "
                "These skipped checks are not counted as passed or as validation of those optional capabilities.", ""]
    warnings += ["## Use the validated R Harmony engine on Windows", "",
        "R Harmony is an explicit alternative with its own pinned version and settings. It does not make a failed Python attempt successful. "
        "Run the following from the project root where R and package installation are permitted; supply your actual PCA matrix, matching metadata and batch column.", "",
        "```powershell", "& 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --vanilla tools/literature/install_harmony_dependencies.R build/omics-harmony-r build/literature-review/harmony-r-dependency allow",
        "java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar cell-embed --method harmony --engine r `",
        "  --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --r-library build/omics-harmony-r `",
        "  --matrix pca.tsv --metadata cells.tsv --batch batch --seed 1 --out build/harmony-r-results", "```", "",
        "Adjust the Rscript path to the installed runtime. Use `forbid` instead of `allow` for a read-only availability/policy check. "
        "Batch correction requires at least two batches and an identifiable study design; the synthetic fixture result is not a guarantee for confounded biological data.", ""]
    repository_report = DATA / "repository-validation.json"
    if repository_report.exists():
        repository = json.loads(repository_report.read_text(encoding="utf-8"))
        website = repository.get("shared_website_validation", {})
        if website.get("status") == "failed":
            warnings += ["## Shared website gate requires action", "",
                "**CHECKWEBSITE FAILED IN THE SHARED CHECKOUT:** " + website["error"] + ". "
                "The independent catalog browser checks passed; the broader shared documentation gate remains failed. "
                + website["required_action"] + " Concurrent study/citation changes were preserved. See repository-validation.json for the retained result.", ""]
        if repository.get("status") == "shared-checkout-unvalidated":
            warnings[4:4] = ["**SHARED CHECKOUT REQUIRES ITS OWN FULL CHECK:** this task's last shared build recorded a compile failure while separate study-pipeline edits were changing. "
                "That historical compilation error was in `StudyRegionalCoverage.java:66`, which this toolbox task did not edit; current shared sources have not been revalidated by this task. "
                "Toolbox validation uses an isolated committed baseline plus this task's additions; its exact baseline and result are recorded in isolated-validation.json and must be distinguished from the shared checkout. "
                "See repository-validation.json for both scopes.", ""]
            warnings += ["## Shared-checkout integration check", "",
                "The first full snapshot had three study-workflow test failures; all three passed in a later focused current-source recheck. "
                "A subsequent current-source compile failed in the separately added regional-coverage class. "
                "Finish/reconcile those concurrent study-pipeline edits and run a complete check against stable current sources before treating the shared checkout as validated. "
                "Do not interpret an isolated baseline build as verification of those separate changes.", ""]
        if repository.get("status") == "shared-checkout-changed-during-passing-gate":
            snapshot = repository["shared_gate_snapshot"]
            warnings[4:4] = ["**SHARED BUILD PASSED, BUT SOURCES CHANGED DURING THE RUN:** the actual shared gate completed with "
                + str(snapshot["tests"]) + " tests, zero failures/errors and " + str(snapshot["skipped"]) + " optional skips. "
                + str(len(snapshot["sources_changed_during_gate"])) + " unrelated study source files changed during the run. "
                "This passing snapshot does not certify the latest shared checkout. The toolbox's isolated gate has matching owned-source hashes; see isolated-validation.json. "
                "Before release or treating all current study changes as validated, obtain a complete gate on stable current sources. Exact snapshots, file names and logs are recorded in repository-validation.json.", ""]
        if repository.get("status") == "failed":
            warnings[4:4] = ["**FULL REPOSITORY GATE NOT GREEN:** the retained full-suite snapshot has "
                + str(len(repository.get("failures", []))) + " study-workflow failures. "
                "All 14 new workflow tests passed, but that does not make the complete repository green. "
                "The checkout changed after compilation; affected cases are being rechecked. See repository-validation.json.", ""]
            warnings += ["## Full-suite failures requiring resolution", ""]
            for failure in repository.get("failures", []):
                warnings += ["- `" + failure["suite"] + "." + failure["test"] + "`: " + failure["message"], ""]
            warnings += ["Required action before treating the full repository as validated: rerun the affected cases against stable current sources, "
                         "investigate any remaining failures, then obtain a passing complete check. The active implementation work is continuing. "
                         "No release or push has been performed by this task.", ""]
    if dependency.exists():
        status = json.loads(dependency.read_text())
        warnings += ["## Local optional dependency attempts", ""]
        for record in status["records"]:
            checked = record.get("method") in {"umap", "leiden", "louvain"} and record["status"] == "installed-not-yet-analysis-validated"
            label = "installed; real upstream CLI analysis validated" if checked else record["status"]
            warnings += [f"- **{record.get('method', 'Environment')}: {label}** — {record.get('dependency', '')}. " + record.get("action", "See cell-adapter-validation.json for independent workflow-test status."), ""]
            if record.get("method") == "harmony" and record["status"].startswith("skipped"):
                warnings += ["  Both wheel-only installation and source-build fallback were attempted. "
                             "The source build reached MSVC and failed with upstream C++ compilation errors, including C2146 in harmony.cpp. "
                             "Use a supported Linux/macOS Python environment with harmonypy 2.1.0 and pass its Python to `cell-embed`. "
                             "An approved Windows source-build repair is an alternative. Existing UMAP/graph analyses remain usable.", ""]
        json_write(DATA / "dependency-status.json", status)
        if any(r.get("method") == "harmony" and r["status"].startswith("skipped") for r in status["records"]):
            warnings += ["## Enable the skipped Python Harmony analysis", "",
                "Run these commands inside Linux, macOS or an already configured WSL distribution, from an OmicsMatic checkout with Java 17+ and the built executable JAR. "
                "Supply your PCA matrix and matching cell metadata. Run the Java command inside that same environment; a Windows JVM cannot directly launch a Linux Python executable.", "",
                "```bash", "python3 -m venv build/harmony-env",
                "build/harmony-env/bin/python -m pip install harmonypy==2.1.0 scikit-learn",
                "java -jar build/cli/omicsmatic-0.3.7.jar cell-embed --method harmony \\",
                "  --matrix pca.tsv --metadata cells.tsv --batch batch \\",
                "  --python build/harmony-env/bin/python --out build/harmony-results", "```", "",
                "Alternatively repair/build the pinned upstream C++ package in an approved Windows development environment. "
                "The failed local attempts remain in `build/literature-review/dependencies/harmony-install.log` and `harmony-source-install.log`. "
                "Rerun to a new output directory after the dependency is available.", ""]
    igv_warning = "**IGV DEVELOPMENT WORKFLOW: WARNINGS REQUIRE REVIEW.** Use igv-tools in candidate38889b96 with an admitted original2.19.8/fullJava21 Windows x64 runtime. Where permitted run install_igv.py --home NEW_ASCII_PRIVATE_HOME --cache OFFICIAL_ARCHIVE_CACHE --java FULL_JAVA21_EXECUTABLE --allow-install;add --allow-download only when permitted. If dependencies cannot be supplied,SKIP this analysis(exit3) until an administrator provides an admitted runtime. Failures return2 with retained partials. Required action:read WARNINGS.md,FIGURE_LEGEND.md and the exact native audits;correct the named cause and rerun to a new directory. Preserve changed homes/caches;never re-seal modified software. Automatic CNA/synteny summaries,methylation interpretation and full-paper/historical parity remain unfinished. Read IGV_WORKFLOWS.md and IGV_RUNTIME_STATUS.md.\n\n"
    workflow_warning = (
        "**PREPARATION / SOLVER / POTENTIAL-SURFACE: REQUIRED ACTION.** Use development workflow candidate b1c26fa4. "
        "On Windows x86_64 explicitly admit PDB2PQR with `install_pdb2pqr.py --python CPYTHON312_EXECUTABLE --home NEW_HOME --cache WHEEL_CACHE --allow-install` "
        "and APBS with `install_apbs.py --home NEW_HOME --cache ARCHIVE_CACHE --allow-install`. "
        "Use the admitted Ubuntu24.04 x86_64 CPython3.12 PyMOL runtime and Linux Java in WSL for `potential-surface`; "
        "`install_pymol.py` needs the exact validated local wheel,cache,report and explicit permission. Add `--allow-download` only where permitted. "
        "Analysis never installs. Missing,changed,unsupported or forbidden dependencies skip3;input/process/timeout/audit failures return2 with quarantined partials. "
        "The example runner stops at the affected stage and marks downstream analyses skipped. Read all stage WARNINGS.md/session.json/logs,correct the named cause and rerun to a new directory. "
        "Choose protonation/force field,grid,units and source-PQR versus PyMOL display radii explicitly;charged zero-radius atoms remain. "
        "Keep raw potential.dx and canonical short-float32 render-grid.dx: original PyMOL can truncate long numeric exponents;every loaded node still requires the exact float32 field hash. "
        "Keep the unit-labeled figure.svg and original surface.png. Raw APBS energy is not solvation/binding free energy. "
        "Read PDB2PQR_WORKFLOWS.md,POTENTIAL_SURFACE_WORKFLOWS.md and apbs-workflow-validation.json."
    )
    saige_warning = "**SAIGE FAMILY: PROJECT ANALYSIS UNAVAILABLE — SKIP DEPENDENT ANALYSES.** SAIGE,SAIGE-GENE and Meta-SAIGE remain missing project integrations. Original unmodified1.5.2 built and ran privately,but is unsealed and has no project installer/typed command. Required action:do not treat this prototype as an admitted runtime;complete typed runtime/installer/model and scientific/failure audits before enabling analyses. If installation is forbidden or unavailable,retain skipped status with the dependency and exact environment recovery. The separate native unrelated-select command(candidate9786ba29) needs only Java. Verify complete additive-GRM pair coverage,generation cutoff <= selection cutoff,phenotype/covariate eligibility and explicit ties. Initial-degree greedy selection is maximal,not guaranteed largest;no GRM/mixed model/association is performed. Read SAIGE_RUNTIME_STATUS.md,SAIGE_SOURCE_REVIEW.md,UNRELATED_SELECTION_WORKFLOWS.md and every WARNINGS.md;correct the named cause and rerun to a new directory.\n\n"
    (DATA / "WARNINGS.md").write_text(saige_warning + igv_warning + workflow_warning + "\n\n" + "\n".join(warnings), encoding="utf-8")

    warnings += ["## PRS-CS RUNTIME, MODEL AND INFERENCE REQUIRE ACTION", "",
        "Use the validated development prscs-candidate-1 executable (SHA starts33b26e76);canonical a104 is unchanged and lacks prs-cs-tools. Read PRSCS_WORKFLOWS.md and prscs-adapter-validation.json.", "",
        "Where permitted install a sealed Windows x64 private profile using tools/literature/install_prscs.py --profiles source-fbc9ec0 --python CPYTHON312 --home NEW_PRIVATE_HOME --allow-install;add --allow-download only for permitted missing official artifacts. Supply --home or JLINALG_PRSCS_HOME. Missing,changed or forbidden software skips analysis(exit3). Analysis never installs or downloads an LD panel;preserve changed/unmanaged homes and choose a new path.", "",
        "Historical1.0.0/1.1.0 explicitly require --legacy-use offline-reproduction and use unsupported Python3.10.11. Known SciPy alias and current NumPy array-to-scalar deprecations remain counted and visible;unknown or numerical warnings fail. Preserve compatible pinned environments and inspect original stderr.", "",
        "Declare uniform GWAS N and total/effective interpretation,trait/format,reference/population/build,a/b/phi,seed/sampling and allele/exclusion policies. BETA/OR plus SE means SE(beta)/SE(logOR). Extreme P values need explicit original-floor permission;palindromic strand remains ambiguous. Full requested source LD counts toward work/storage ceilings,which do not guarantee total process RAM.", "",
        "Input/process/native-check failures exit2,retain diagnostics and quarantine canonical partials. Read WARNINGS.md,analysis-status.json,session.yaml,input/variant/LD ledgers and raw logs;correct the named cause and rerun into a new directory. Original effect strings have seven significant digits and filenames round model labels;use exact session parameters. Explicit score exports use LD-panel frequencies. Chain stability,training-target overlap,tuning,ancestry combination and predictive/clinical calibration need separate assessment.", ""]

    prscs_actions = """<section id="prscs-required-actions" aria-labelledby="prscs-actions-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="prscs-actions-title">PRS-CS: required actions</h2>
<p><strong>Use the validated development candidate:</strong>
<code>build/literature-review/prscs-candidate-1/jlinalg-0.3.7.jar</code> (SHA starts <code>33b26e76</code>).
The older canonical a104 is unchanged and lacks <code>prs-cs-tools</code>. The 218-test focused gate and packaged checks are separate from a full-project release gate.</p>
<p><strong>Missing, changed or forbidden dependency: skip analysis (exit 3).</strong>
Where permitted run <code>python tools/literature/install_prscs.py --profiles source-fbc9ec0 --python CPYTHON312 --home NEW_PRIVATE_HOME --allow-install</code>.
Add <code>--allow-download</code> only for permitted missing official artifacts. Supply <code>--home</code> or <code>JLINALG_PRSCS_HOME</code>.
Preserve changed homes and choose a new private path. Analysis never installs software or downloads an LD panel.</p>
<p><strong>Historical runtimes are out of support.</strong> Profiles 1.0.0/1.1.0 require <code>--legacy-use offline-reproduction</code> and use Python 3.10.11.
Use the separately pinned current-source/Python 3.12 profile for new work. Known version-specific deprecations remain visible in raw stderr and warnings; unknown or numerical warnings fail.</p>
<p><strong>Choose model, sample size and allele policies explicitly.</strong> Declare total/effective N, trait and summary format, panel/population/build, a/b/phi, sampling and seed.
SE is SE(beta) or SE(logOR); original extreme-P flooring needs explicit permission. Palindromic SNPs retain strand ambiguity.
Review every input/variant exclusion and exact LD/SNP order. Work/storage guards include full source LD and do not guarantee total process RAM.</p>
<p><strong>Input, process or validation failure: exit 2.</strong> Canonical partials go to <code>partial/</code>; logs and ledgers remain.
Read <code>WARNINGS.md</code>, <code>analysis-status.json</code>, <code>session.yaml</code> and <code>PRSCS_WORKFLOWS.md</code>.
Correct the named cause and rerun into a new directory. Retained log paths use <code>&lt;bundle&gt;</code> for that published directory.</p>
<p><strong>Preserve units and assess the scientific design.</strong> Original values retain seven significant digits; session metadata has exact parameters.
Optional per-allele mean-weight exports use supplied LD-panel frequencies. Chain convergence, training-target overlap, hyperparameter tuning, ancestry combination and predictive calibration require separate assessment.</p>
</section>"""
    beagle_actions = """<section id="beagle-required-actions" aria-labelledby="beagle-actions-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="beagle-actions-title">Beagle: required actions</h2>
<p><strong>Use the validated development candidate:</strong>
<code>build/literature-review/beagle-candidate-1/jlinalg-0.3.7.jar</code>
(SHA-256 starts <code>5ed54e44</code>). The older canonical a104 full-gate build is unchanged
and lacks <code>beagle-tools</code> and <code>plink-tools</code>. No new full-project gate or release is claimed.</p>
<p><strong>Missing, changed or forbidden dependency: skip analysis (exit 3).</strong>
Where permitted, prepare a new private home with
<code>python tools/literature/install_beagle.py --java JAVA_EXECUTABLE --allow-install</code>.
Add <code>--allow-download</code> only for permitted missing official downloads.
Supply the sealed home with <code>--home</code> or <code>JLINALG_BEAGLE_HOME</code>.
Preserve changed homes and use a new path. Analysis never installs software.</p>
<p><strong>Choose phase preservation and marker exclusions explicitly.</strong>
Modern 5.1/5.4 can rephase partially missing prephased targets. Use compatible complete
prephased input for <code>preserve-provided</code> or explicitly choose <code>allow-rephase</code>.
With a reference panel, target-only markers require an explicit error/drop policy;
review every exclusion in <code>variant-ledger.tsv</code>.</p>
<p><strong>Keep GT separate from rounded GP.</strong> Original GT/DS/AP/GP strings are retained;
GT need not equal the largest printed unphased GP. Review <code>variant-results.tsv</code>,
the exact map/assembly/panel and both identity ledgers before downstream analysis.</p>
<p><strong>Input, process or validation failure: exit 2.</strong> Logs and diagnostics remain;
canonical partials go to <code>partial/</code>. Correct the named issue and rerun into a new directory.
Read <code>analysis-status.json</code>, <code>WARNINGS.md</code>, <code>session.yaml</code> and
<code>docs/literature/omics-2023-2026/BEAGLE_WORKFLOWS.md</code>. Synthetic agreement does not certify biological accuracy.</p>
</section>"""
    fimo_actions = """<section id="fimo-required-actions" aria-labelledby="fimo-actions-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="fimo-actions-title">FIMO: required actions</h2>
<p><strong>Native P-value scanning needs only Java.</strong> Use <code>motif-scan</code>.
Original P/q analysis with <code>fimo-tools</code> requires the admitted private
Windows x86_64 FIMO 5.5.9 profile and an applicable intended-use declaration.</p>
<p><strong>Missing, changed, unsupported or forbidden runtime: skip the original analysis (exit 3).</strong>
Keep the diagnostics and changed home. Where installation is permitted, provision a new private home:</p>
<pre style="white-space:pre-wrap;overflow-wrap:anywhere">python tools/literature/install_fimo.py --home NEW_PRIVATE_HOME --reference-home build/omics-fimo-5.5.9-gnurx-reference --cache build/literature-review/fimo-runtime --intended-use educational-research-nonprofit --allow-install</pre>
<p>Add <code>--allow-download</code> only for permitted missing source-archive downloads.
This helper reuses the authenticated local reference build; it does not bootstrap
another host or build. If that reference is absent, retain the skip until an
appropriate validated profile is available. Analysis never installs software.</p>
<p><strong>Stored-hit cap reached: fail with exit 2 and quarantine results, even if the original
program exits 0 and prints an empty table.</strong> Raise <code>--maximum-stored-scores</code>,
restrict the declared analysis or choose a scientifically justified threshold,
then rerun to a new output directory. Capped empty output is not evidence of absent binding.</p>
<p><strong>Uncomputed q-values are blank in canonical results.</strong> Original full-mode no-Q
zeros are placeholders; raw reports remain unchanged. Use full output with q-values
enabled when required. Preserve the tested cohort and small-cohort estimator warnings;
an original q-value differs from a native P-value or a custom empirical score.</p>
<p>Read <code>docs/literature/omics-2023-2026/FIMO_WORKFLOWS.md</code> for complete commands
and scope. Each run's <code>WARNINGS.md</code> names its exact cause and recovery action.</p>
</section>"""
    plink_actions = """<section id="plink-source-actions" aria-labelledby="plink-source-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="plink-source-title">PLINK: native scores and development adapter</h2>
<p><strong>The project PLINK/PLINK2 adapter remains in progress.</strong> Native BED
clumping and the separate supplied-weight score helper do not establish complete QC, PCA, GWAS or PRS training/replay.</p>
<p id="genotype-score-actions"><strong>Native <code>genotype-score</code> is validated and needs only Java.</strong>
Supply exact effect/other alleles and an explicit missing policy. Mean imputation
requires supplied effect-allele frequencies. Review observed versus denominator
alleles; no-observation omission leaves blank unreportable scores, not zero.
Fully imputed scores are conditional reference expectations. Errors return 2,
quarantine scientific partials and name the correction in <code>WARNINGS.md</code>.
Read <code>GENOTYPE_SCORE_WORKFLOWS.md</code> for commands and a synthetic example.</p>
<p><strong>Resolve paper-specific choices before replay:</strong> P200 describes bp with
<code>--clump-kb</code>; P019 has ambiguous HWE direction wording; P112 has unusual
covariate wording. Keep P150 binary <code>firth-fallback</code> separate from
quantitative regression. P242 generic PLINK shorthand does not establish a PLINK1 run.</p>
<p>PLINK/PGEN formats and website URLs are not execution evidence. Preserve release,
cohort, phenotype model, allele and missing-data choices. PRS weight estimation,
score application, annotation lookup and model execution are different operations.</p>
<p><strong>Original options differ:</strong> tested PLINK1.9 rejects <code>sum</code>
with <code>no-mean-imputation</code>; tested PLINK2 requires <code>--memory</code>
of at least 640 MiB. Retain the error and correct the request explicitly. Missing
or forbidden required software must skip analysis with exact recovery instructions.</p>
<p>Read <code>docs/literature/omics-2023-2026/PLINK_SOURCE_REVIEW.md</code>.
Source review and private original-program reference work are separate from the
broader Java adapter validation. Typed original GLM/QC/pruning/PCA subsets are available in the
validated development candidate named above; the older canonical a104 lacks them. Review the
separate proofs and explicit input/model contracts in <code>examples/plink-tools/</code>.</p></section>"""
    cellranger_actions = """<section id="cellranger-excluded" aria-labelledby="cellranger-excluded-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="cellranger-excluded-title">Cell Ranger: skipped by user request</h2>
<p><strong>Implementation and runtime analysis are excluded.</strong> The user requested this
on October 26, 2026 after reviewing the restricted 10x license. No Cell Ranger, ARC
or ATAC runtime was downloaded, installed or executed.</p>
<p>The 44 paper roles remain catalogued for provenance, with the different program
families and downstream tools distinguished. This is not a completed integration
or a negative biological result.</p><p><strong>Required action: none.</strong> Leave the tool
skipped unless the user explicitly reopens it. Read
<code>docs/literature/omics-2023-2026/CELLRANGER_SOURCE_REVIEW.md</code>.</p></section>"""
    pymol_actions = """<section id="pymol-actions" aria-labelledby="pymol-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="pymol-title">Original PyMOL subset: Linux runtime and export review</h2>
<p><strong>Development candidate <code>pymol-candidate-4</code>, SHA-256 starts <code>09e4be51</code>.</strong>
<code>pymol-tools</code> runs original rendering, measurements, align/super and explicit bond, torsion and rotamer edits,
with native audits. The canonical a104 executable is unchanged.</p>
<p><strong>Required action:</strong> use an admitted Ubuntu 24.04 x86_64 private runtime and Linux Java;
on Windows run inside WSL. The pinned PyMOL wheel is a local validated build and must be supplied from the permitted reference cache.
Where allowed, run <code>tools/literature/install_pymol.py</code> with explicit <code>--allow-install</code>;
<code>--allow-download</code> only covers missing pinned public dependency wheels. Analysis never installs.
Missing, changed, unsupported or forbidden dependencies <strong>skip analysis with exit 3</strong>.</p>
<p><strong>Export limitation:</strong> original mmCIF rounds coordinates to 0.001 Angstrom and B/occupancy to 0.01,
and omits bond categories. Keep <code>final-*.json</code> as the authoritative full-precision atom/bond graph.
Inspect figures, <code>WARNINGS.md</code>, <code>session.json</code>, <code>original-results.json</code> and <code>native-audit.json</code>.
Input, process or audit failures return 2 and quarantine partials; correct the named issue and rerun to a new directory.</p>
<p><strong>Typed APBS surface integration is available.</strong> Workflow candidate <code>b1c26fa4</code> adds <code>pdb2pqr-tools</code>,<code>apbs-tools</code> and <code>potential-surface</code>; read <code>PDB2PQR_WORKFLOWS.md</code> and <code>POTENTIAL_SURFACE_WORKFLOWS.md</code>. A colored surface does not perform electrostatics. Manual interobject construction,
paper-specific genomic reconstruction, historical/commercial editions and full paper pipelines remain separate work.
Read <code>PYMOL_WORKFLOWS.md</code>, <code>PYMOL_SOURCE_REVIEW.md</code> and <code>pymol-adapter-validation.json</code>.</p></section>"""
    geometry_actions = """<section id="molecular-geometry-actions" aria-labelledby="molecular-geometry-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="molecular-geometry-title">Native molecular geometry: units and pairs require review</h2>
<p><strong>Available in development candidate <code>molecular-geometry-candidate-1</code>; its SHA-256 starts with 798d3a08.</strong>
The native command needs only Java. Declare <code>angstrom</code> or <code>nanometer</code>;
nanometers multiply by 10. Fits use every explicitly supplied pair and require
<code>error</code> or <code>allow-nonunique</code>. No atom/sequence matching, outlier rejection,
scale, reflection or periodic images are inferred. A low RMSD alone does not establish biological equivalence.</p>
<p><strong>Undefined measurements fail; explicitly allowed nonunique fits warn.</strong>
Read <code>WARNINGS.md</code>, <code>analysis-status.json</code>, <code>session.yaml</code>
and <code>fit-pairs.tsv</code>. Errors return 2 and quarantine scientific partials.
Correct the named units, IDs, pairs, geometry or limits, and rerun to a new directory.
Read <code>MOLECULAR_GEOMETRY_WORKFLOWS.md</code> for commands and synthetic examples.</p>
<p><strong>Original typed PyMOL subset is available in development candidate 09e4be51.</strong>
Use <code>pymol-tools</code> with an admitted Linux runtime for rendering, measurements, original align/super and explicit edits.
Typed APBS surface integration is available through <code>pdb2pqr-tools</code>,<code>apbs-tools</code> and <code>potential-surface</code>;manual interobject modelling and paper-specific reconstruction remain pending. Read <code>PYMOL_WORKFLOWS.md</code>.
The canonical a104 executable is unchanged.</p></section>"""
    apbs_actions = """<section id="apbs-actions" aria-labelledby="apbs-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="apbs-title">APBS solver: runtime, charges, grid and energy review</h2>
<p><strong>Development candidate <code>apbs-workflow-candidate-3</code>, SHA-256 starts <code>b1c26fa4</code>.</strong>
<code>apbs-tools</code> runs original APBS3.5.0 LPBE/NPBE on an explicitly charged PQR model.
The Windows executable is <strong>32bit x86</strong>, tested on Windows x86_64. The canonical a104 executable is unchanged.</p>
<p><strong>Required action:</strong> where permitted run <code>python tools/literature/install_apbs.py --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --allow-install</code>;
add <code>--allow-download</code> only when permitted and the pinned archive is absent. Supply the admitted <code>--home</code>.
Analysis never installs. Missing, changed, unsupported or forbidden dependencies <strong>skip analysis with exit 3</strong>.
If installation is forbidden, skip the solver analysis until an admitted runtime is supplied.</p>
<p><strong>Model inputs must be explicit:</strong> charge-model provenance, Angstrom units, grid counts/lengths/center,
LPBE/NPBE, boundary, dielectrics, probe radius, surface density, temperature and salt.
No pH, protonation, force field, atom repair or PDB2PQR preparation is inferred. Charged zero-radius atoms are retained.
The bounded profile uses <code>mg-manual/mol/spl2</code>; inspect original logs and grid refinement.</p>
<p><strong>Successful process exit is insufficient:</strong> an all-atomic-charges-zero original calculation exits 0 but writes NaNs.
Every node is audited; invalid output, input, process or timeout failures return 2 and quarantine partials.
Raw total energy includes self-energy and <strong>is NOT solvation or binding free energy</strong>.
Potential units are kT/e at the declared temperature; voltage conversion uses exact SI2019 k_B/e.</p>
<p><strong>Native helper:</strong> <code>electrostatic-grid</code> needs only Java. Declare units and an outside policy;
missing rows have blank values, finite zero grids remain valid, and sampling never extrapolates.
Read <code>WARNINGS.md</code>, <code>session.json</code>, original logs and <code>results.json</code> before interpretation.
Correct the named issue and rerun to a new directory. Read <code>APBS_WORKFLOWS.md</code>, <code>APBS_SOURCE_REVIEW.md</code>
and <code>apbs-adapter-validation.json</code>. Typed preparation and map surfaces are available through
<code>pdb2pqr-tools</code> and <code>potential-surface</code>;read <code>apbs-workflow-validation.json</code>. Historical/source-paper parameter reconstruction remain in progress.</p></section>"""
    workflow_actions = """<section id="electrostatic-workflow-actions" aria-labelledby="electrostatic-workflow-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="electrostatic-workflow-title">Charge preparation to actual potential surfaces: required review</h2>
<p><strong>Validated development candidate <code>apbs-workflow-candidate-3</code>,SHA-256 starts <code>b1c26fa4</code>.</strong>
<code>pdb2pqr-tools</code> runs original PDB2PQR3.8.0/PROPKA3.5.1 on Windows x86_64 CPython3.12;
<code>apbs-tools</code> solves with original APBS3.5.0; <code>potential-surface</code> renders the actual map with original PyMOL3.1.0 through Linux Java in WSL.</p>
<p><strong>Required action:</strong> where permitted run <code>install_pdb2pqr.py --python CPYTHON312_EXECUTABLE --home NEW_HOME --cache WHEEL_CACHE --allow-install</code>,
<code>install_apbs.py --home NEW_HOME --cache ARCHIVE_CACHE --allow-install</code> and the documented Linux <code>install_pymol.py</code> with an exact validated local wheel.
Add <code>--allow-download</code> only when permitted. Analysis never installs. Missing,changed,unsupported or forbidden dependencies
<strong>skip analysis with exit 3</strong>;input,process,timeout or audit failures return 2 with quarantined partials.
<code>examples/apbs-tools/run-workflow.py</code> stops at a failed/unavailable stage and marks dependent stages skipped.</p>
<p><strong>Model and display decisions:</strong> explicitly choose force field,protonation or pH,repair/omission,water,state/alternates,
Angstrom frame,temperature,grid,salt and dielectrics. Charged zero-radius atoms remain. PyMOL loads display radii;
<code>source-pqr</code> explicitly applies supplied radii,while <code>pymol</code> keeps different display radii and warns.
Choose excluded/accessible surface,probe radius,vertex/probe-offset sampling and a labeled three-color ramp.</p>
<p><strong>DX parser / precision:</strong> original PyMOL truncates long numeric tokens and can silently lose exponents.
Keep raw <code>potential.dx</code> and canonical short-float32 <code>render-grid.dx</code>. Every loaded field node must match the native float32-rounded source exactly.
Keep <code>figure.svg</code>,the unchanged original PNG and full atom/map/ramp ledgers. Raw APBS energy is <strong>NOT solvation or binding free energy</strong>.</p>
<p>107 focused tests/10 suites,107 standalone tests per Java21/25,14 original preparation comparisons/44,940 numeric scalars,
16 preparation runtime policy cases,6 actual surface workflows/1,647,750 field nodes and7 surface controls passed.
A full Java25 Unicode preparation-to-solve-to-surface example passed with exact cross-stage hashes.
Read <code>PDB2PQR_WORKFLOWS.md</code>,<code>POTENTIAL_SURFACE_WORKFLOWS.md</code>,<code>apbs-workflow-validation.json</code> and every stage <code>WARNINGS.md</code>;
correct the named issue and rerun to a new directory. These are bounded workflows;source-paper parameters and historical/full-paper parity remain unresolved.</p></section>"""
    igv_actions = """<section id="igv-actions" aria-labelledby="igv-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="igv-title">Original IGV development workflow: warnings and required actions</h2>
<p><strong>Validated bounded command: <code>igv-tools</code>,candidate38889b96.</strong> Original2.19.8/fullJava21 on Windows x64 renders hidden local FASTA/BED/bedGraph/BigWig/SEG/BAM views.
126tests/13suites,126standalone perJava21/25 and17packaged cases passed:12scientific workflows/5controls,7,860reference bases,41,600integer counts and94selected-read comparisons.</p>
<p><strong>Required action:</strong> where permitted run <code>python tools/literature/install_igv.py --home NEW_ASCII_PRIVATE_HOME --cache OFFICIAL_ARCHIVE_CACHE --java FULL_JAVA21_EXECUTABLE --allow-install</code>;add <code>--allow-download</code> only when permitted.
Supply usable Windows graphics/fonts. If installation/dependencies are unavailable,<strong>SKIP the dependent analysis(exit3)</strong> until an administrator supplies an admitted runtime. Input/process/audit failures return2;partials and diagnostics remain retained.
Read every <code>WARNINGS.md</code>,<code>FIGURE_LEGEND.md</code> and full native audit. Correct the named cause and rerun to a new directory. Preserve changed homes/caches;never re-seal modified software.</p>
<p>Coordinates,raw float32 values/gaps,BED identity,selected BAM reads/raw tags and every base count are audited. Native frame checks bypass the original ruler's coordinate-label behavior. Original OAuth/logger diagnostics remain visible.
Automatic CNA cohort-frequency/custom synteny summaries,methylation interpretation,manual diagnosis,allele-depth rules and historical/full-paper parity remain unfinished. The canonical executable/release is unchanged.</p>
<p>All587previous source reviews are preserved;the current catalog has250papers/377tools/2426observations/623reviewed roles. P110session availability alone remains excluded as execution evidence.
Read <code>IGV_WORKFLOWS.md</code>,<code>IGV_SOURCE_REVIEW.md</code>,<code>IGV_RUNTIME_STATUS.md</code> and <code>igv-workflow-validation.json</code>.</p></section>"""
    saige_actions = """<section id="saige-actions" aria-labelledby="saige-title"
style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere">
<h2 id="saige-title">SAIGE family: project analysis unavailable; native cohort selection available</h2>
<p><strong>SKIP dependent SAIGE/SAIGE-GENE/Meta-SAIGE project analyses.</strong> The original unmodified version 1.5.2 private build and controlled prototype with 1,000 participants and 32 variants do not establish an admitted project runtime. The installer and typed association, gene and LD workflows remain pending; all three tool badges remain missing.</p>
<p><strong>Required action:</strong> do not treat the prototype as an admitted runtime. Complete typed runtime/model and scientific/failure audits before enabling these analyses. If installation is forbidden or unsupported, retain a prominent skipped status, identify the dependency/environment and obtain an admitted compatible runtime before rerunning to a new output directory. Analysis must not install implicitly.</p>
<p><strong>Native Java component: <code>unrelated-select</code>, development candidate 9786ba29.</strong> No external runtime or install. Apply phenotype/covariate eligibility before initial degree counting. Declare complete additive-GRM pair coverage, generation cutoff &lt;= selection cutoff and explicit ties. Read every named blocking participant. The greedy output is <strong>maximal, not guaranteed largest</strong>; it does not calculate a GRM, fit SAIGE or run association tests. Input/process failures return 2 and preserve partial diagnostics; existing outputs remain. Correct the named cause and rerun to a new directory.</p>
<p>32 tests in 5 suites, 32 standalone tests per Java 21 and 25, 501 independent full-decision graphs and 21 actual packaged cases passed, including the unchanged Unicode example. All 604 earlier source reviews are preserved. The current catalog has 250 papers, 377 tools, 2,426 observations, 623 reviewed roles and 153 supporting pointers. Nineteen new family reviews distinguish 16 SAIGE, 2 SAIGE-GENE and 1 Meta-SAIGE roles; P147 published-summary reuse is newly recovered. Original score checks distinguish 29 centered-fast and 3 projected-retest rows; no independent GLMM/SPA/Firth parity is claimed.</p>
<p>Read <code>SAIGE_SOURCE_REVIEW.md</code>, <code>SAIGE_RUNTIME_STATUS.md</code>, <code>UNRELATED_SELECTION_WORKFLOWS.md</code>, <code>saige-runtime-intake.json</code>, <code>unrelated-selection-validation.json</code> and every analysis <code>WARNINGS.md</code>. Historical/paper-specific reconstruction and the wider implementation goal remain active.</p></section>"""
    warning_body = saige_actions + igv_actions + workflow_actions + apbs_actions + pymol_actions + geometry_actions + cellranger_actions + prscs_actions + beagle_actions + plink_actions + fimo_actions + "<pre style=\"white-space:pre-wrap;overflow-wrap:anywhere\">" + html.escape("\n".join(warnings)) + "</pre>"
    (ROOT / "site/literature-warnings.html").write_text('<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Literature review warnings · JLinAlg</title><link rel="stylesheet" href="assets/styles.css"><script src="assets/app.js" defer></script></head><body><site-header root="./"></site-header><main id="main" class="container"><h1>Warnings and required actions</h1><p><a href="literature-review.html">Return to the 250-paper catalog</a></p>' + warning_body + '</main><site-footer root="./"></site-footer></body></html>', encoding="utf-8")
    # Self-contained page: no network calls, data upload, CDN, or file:// fetch.
    data = json.dumps({"papers": papers, "tools": tools, "summary": summary}, ensure_ascii=False).replace("<", "\\u003c")
    page = r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>250-paper omics and GWAS review · OmicsMatic</title>
<link rel="stylesheet" href="assets/styles.css"><script src="assets/app.js" defer></script>
<style>.review-controls{display:flex;gap:.7rem;flex-wrap:wrap;margin:1.5rem 0}.review-controls input{flex:1;min-width:16rem}.review-controls input,.review-controls select{padding:.6rem;border:1px solid var(--border,#8798a0);border-radius:.4rem;background:var(--bg,#fff);color:inherit}.review-card{min-width:0;border:1px solid var(--border,#8798a0);border-radius:.6rem;padding:1.3rem;margin:1rem 0}.review-card>p{overflow-wrap:anywhere}.review-card h2{margin-top:0;font-size:1.25rem}.review-meta{opacity:.8;font-size:.9rem}.review-card table{width:100%;font-size:.9rem}.review-card td,.review-card th{text-align:left;vertical-align:top;padding:.5rem}.review-warning{border-left:5px solid #c78417;padding:1rem;background:#c7841712}.review-table{overflow-x:auto}.review-tabs button{margin-right:.7rem;padding:.6rem 1rem;border:1px solid var(--border);border-radius:.4rem;background:var(--surface-2);color:var(--text)}.review-tabs button[aria-pressed="true"]{border-color:var(--brand);box-shadow:inset 0 0 0 1px var(--brand)}.review-controls input::placeholder{color:var(--muted);opacity:1}.review-count{font-weight:600}.review-limit{max-width:1000px;padding-bottom:4rem}details{margin:.3rem 0}summary{cursor:pointer}code{overflow-wrap:anywhere}@media(max-width:650px){.review-card{padding:.7rem}.review-card td,.review-card th{padding:.3rem}.review-controls input{min-width:10rem}}</style>
</head><body><site-header root="./"></site-header><main id="main"><section class="page-hero"><div class="container">
<span class="eyebrow">Literature catalog · Source build · Implementation in progress</span><h1>Omics and GWAS tools across 250 papers</h1>
<p>Nature, Nature Genetics and AJHG · October 23, 2023–October 23, 2026</p></div></section>
<div class="container review-limit"><div class="review-warning"><strong>Scope and required actions</strong><p>250 primary-source records are catalogued. Tool extraction is registry-bounded; semantic completeness review remains open. Native overlap does not establish upstream software equivalence. Coverage badges describe tool-level integrations; read each paper-specific scope note before assuming its reported operation is supported. Missing integrations remain visible below.</p>
<p>If a required dependency cannot be installed, its analysis must be marked <strong>skipped</strong>, with the error, dependency and recovery steps. See the <a href="literature-warnings.html">warnings and actions report</a>.</p></div>
<p id="overview"></p><div class="review-tabs"><button id="papers-tab" aria-pressed="true">Papers and uses</button><button id="tools-tab" aria-pressed="false">Tools and implementation</button></div>
<div class="review-controls"><input id="query" aria-label="Search papers, tools or uses" placeholder="Search a paper, tool, method or DOI">
<select id="journal" aria-label="Journal"><option value="">All journals</option></select><select id="year" aria-label="Year"><option value="">All years</option></select><select id="coverage" aria-label="Implementation status"><option value="">All implementation states</option></select></div>
<p class="review-count" id="count" aria-live="polite"></p><div id="results"></div>
<p>Sources: <a href="https://europepmc.org/RestfulWebService">Europe PMC API</a>; linked primary papers. The selection is a purposive open-access sample. Long source excerpts are not reproduced here; exact paragraph hashes and locators accompany each observation.</p>
</div></main><site-footer root="./"></site-footer><script id="review-data" type="application/json">__DATA__</script>
<script>
'use strict';
const {papers,tools,summary}=JSON.parse(document.getElementById('review-data').textContent);
const byId=new Map(tools.map(t=>[t.id,t]));let mode='papers';
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function supportingEvidence(p){let value=`<br>Supporting paragraph ${esc(p.paragraph_index)}: ${esc(p.section)}<br><code>${esc(p.paragraph_sha256)}</code>`;
if(p.evidence_role==='method-details'){const a=p.named_anchor;value+=`<br><strong>Method details; named tool anchor:</strong> paragraph ${esc(a.paragraph_index)}: ${esc(a.section)}<br><code>${esc(a.paragraph_sha256)}</code><br>Shared method: ${esc(p.shared_terms.join(', '))}<br>${esc(p.relation_note)}`;}return value;}
const el=id=>document.getElementById(id);
for(const journal of Object.keys(summary.journals))el('journal').add(new Option(journal,journal));
for(const year of Object.keys(summary.years))el('year').add(new Option(year,year));
for(const state of [...new Set(tools.map(t=>t.implementation_status))])el('coverage').add(new Option(state,state));
el('overview').textContent=`${summary.papers} papers · ${summary.distinct_named_tools} named tools · ${summary.paper_tool_observations} tool observations. `+Object.entries(summary.journals).map(([j,n])=>`${j}: ${n}`).join(' · ');
function renderContent(){const q=el('query').value.trim().toLowerCase(),j=el('journal').value,y=el('year').value,c=el('coverage').value;
const filtered=papers.filter(p=>(!j||p.journal_abbreviation===j)&&(!y||p.publication_year===y));
if(mode==='papers'){const rows=filtered.filter(p=>(!q||JSON.stringify(p).toLowerCase().includes(q))&&(!c||p.tools.some(t=>byId.get(t.tool_id).implementation_status===c)));
el('count').textContent=`${rows.length} of 250 papers`;
el('results').innerHTML=rows.map(p=>`<article class="review-card" id="${esc(p.paper_id)}"><h2>${esc(p.paper_id)}: ${esc(p.title)}</h2><p class="review-meta">${esc(p.journal)} · ${esc(p.first_publication_date)} · <a data-link="${esc(p.publisher_url)}">DOI</a> · <a data-link="${esc(p.fulltext_url)}">Primary full text</a></p><div class="review-table"><table><thead><tr><th>Tool</th><th>How used</th><th>Tool-level coverage</th></tr></thead><tbody>${p.tools.filter(t=>!c||byId.get(t.tool_id).implementation_status===c).map(t=>`<tr><td>${esc(t.tool)}${t.version_reported_near_name?'<br>reported '+esc(t.version_reported_near_name):''}</td><td>${esc(t.use_summary)}<details><summary>Evidence locator</summary>${esc(t.evidence_section)}<br>paragraph ${t.evidence_paragraph_index}<br><code>${esc(t.evidence_paragraph_sha256)}</code><br>${esc(t.usage_context)}${(t.supporting_evidence||[]).map(supportingEvidence).join('')}</details></td><td>${esc(byId.get(t.tool_id).implementation_status)}${t.implementation_scope_note?'<p><strong>Scope for this paper:</strong> '+esc(t.implementation_scope_note)+'</p>':''}</td></tr>`).join('')}</tbody></table></div><details><summary>Source provenance</summary>PMCID: ${esc(p.pmcid)}<br>XML SHA-256: <code>${esc(p.xml_sha256)}</code><br>${esc(p.review_status||'Source catalogued; semantic completeness review open')}</details></article>`).join('');
}else{const ids=new Set(filtered.flatMap(p=>p.tools.map(t=>t.tool_id)));const rows=tools.filter(t=>ids.has(t.id)&&(!q||JSON.stringify(t).toLowerCase().includes(q))&&(!c||t.implementation_status===c));el('count').textContent=`${rows.length} named tools`;
el('results').innerHTML=rows.map(t=>`<article class="review-card"><h2>${esc(t.name)} <small>(${t.paper_count} papers)</small></h2><p>${esc(t.role)}</p><p><strong>${esc(t.implementation_status)}</strong> · ${esc(t.implementation_scope)}</p><p>${esc(t.validation_note)}</p>${t.implementation_source?'<p>Local source: <code>'+esc(t.implementation_source)+'</code></p>':''}<details><summary>Paper IDs</summary>${esc(t.papers.join(', '))}</details></article>`).join('');}}
function render(){renderContent();for(const a of document.querySelectorAll('a[data-link]')){const u=new URL(a.dataset.link);if(u.protocol==='https:')a.href=u.href;}}
for(const id of ['query','journal','year','coverage'])el(id).addEventListener(id==='query'?'input':'change',render);
for(const value of ['papers','tools'])el(value+'-tab').onclick=()=>{mode=value;for(const tab of ['papers','tools'])el(tab+'-tab').setAttribute('aria-pressed',String(tab===mode));render();};render();
</script></body></html>'''
    page = page.replace('<p id="overview"></p>', saige_actions + igv_actions + workflow_actions + apbs_actions + pymol_actions + geometry_actions + cellranger_actions + '<p id="overview"></p>')
    (ROOT / "site/literature-review.html").write_text(page.replace("__DATA__", data), encoding="utf-8")
    manifest = {}
    for path in sorted(DATA.iterdir()):
        if path.is_file() and path.name != "SHA256SUMS":
            manifest[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    (DATA / "SHA256SUMS").write_text("".join(f"{digest}  {name}\n" for name, digest in manifest.items()), encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
