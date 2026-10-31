# RNA, long-read alignment and feature counting

The source CLI now provides `feature-counts`, `minimap-tools` and `hisat-tools`.
They run pinned upstream programs through Java input/output checks. Java also
implements GTF/SAF validation, genomic feature union lengths, transcript splice
boundaries, count normalization and PAF geometry checks. Existing STAR alignment
and SAMtools/BAM operations remain available in their separate guides.

This is unreleased source work. The verified JAR from this session is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`; the commands below use
that retained copy. Its exact hash and isolated-source scope are recorded in
`isolated-validation.json`.

**Read warnings before using a result.** Missing, forbidden or wrong runtime:
exit **3**, `analysis-status.json` says `skipped-dependency-unavailable`, and
`WARNINGS.md` identifies the cause and setup action. Input, upstream execution or
output validation failure: exit **2**. Partial files are retained under
`partial/`; no successful result table is published. Choose a new output
directory after correcting the cause. Analysis commands never install software.

## Private setup where permitted

From the project directory on Windows x86_64:

```powershell
python tools/literature/install_rna_aligners.py --profile featurecounts --allow-download
python tools/literature/install_rna_aligners.py --profile minimap2 --allow-download --allow-source-build --cygwin-root C:/cygwin64
python tools/literature/install_rna_aligners.py --profile hisat2 --allow-download --allow-source-build --cygwin-root C:/cygwin64
```

The private homes are `build/omics-featurecounts`, `build/omics-minimap2` and
`build/omics-hisat2`. The installer preserves existing unmanaged/corrupt homes;
use a new `--environment` after resolving the named problem. Verified reuse
requires neither download nor build permission. The source profiles use an
**existing, read-only Cygwin installation** with gcc-core, gcc-g++, make and
zlib-devel. If those prerequisites cannot be supplied, skip the dependent
analysis or use a permitted native host. No OS package, WSL distribution,
service or global PATH is installed or changed.

Pinned programs: official Windows Subread **2.1.1**, minimap2 **2.31-r1302** and
HISAT2 **2.2.3**. Publisher/archive SHA-256, source-file integrity, executable/DLL
hashes, compiler commands and version probes are retained. For native Linux or
macOS, supply `--home HOME` with the exact upstream version in `HOME/bin`;
local programs are hashed and probed, but this tranche's execution evidence is
from Windows. System libraries on other hosts are not bundled or certified.

**BUILD WARNINGS:** the tested minimap2 build retained **2** compiler warnings;
HISAT2 retained **213**. Review `build/literature-review/TOOL-dependency/` and its
retained attempt logs before extending the tested operation/input scope. These
warnings also appear in analysis bundles. No scientific source was patched.
HISAT2's pinned archive has **20 dangling research test-data links** under
`evaluation/tests`; only those exact links were omitted, with names/targets
recorded. Failed extraction/packaging attempts remain in history. Installation
and version success are separate from scientific validation.

## Gene, peak and junction counts

Prepare a TSV with exact header `sample`, `alignment`, `strand`, separated by
tabs. Each SAM/BAM path resolves relative to that TSV, unless absolute. Strand
is **0** unstranded, **1** same strand, **2** reverse strand, chosen from the
actual library protocol. Files must share exact reference names and lengths.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar feature-counts --method count --home build/omics-featurecounts --inputs samples.tsv --annotation genes.gtf --library paired --unit fragments --junctions true --out output/gene-counts
```

**Read versus fragment units are mandatory.** Since Subread 2.0.2, `-p` declares
paired data; `--countReadPairs` counts fragments. `--library paired --unit reads`
deliberately counts mates separately. The adapter preserves that distinction.
Gene exons require a quoted grouping attribute (default `gene_id`), but counting
does not require `transcript_id`. GTF uses `--feature-type exon` and
`--group-attribute gene_id`. Peak/other intervals can use `--format SAF` with
exact `GeneID Chr Start End Strand` tab-separated header and **one-based
inclusive** coordinates. The original annotation goes upstream; `features.saf`
is a validated audit export, so GTF transcript/junction metadata is preserved.

Default `--nh-policy require` rejects mapped records without positive NH tags.
Use `--nh-policy assume-unique` only after reviewing the aligner: untagged
records are treated as unique upstream, which **does not establish uniqueness**.
`--multimapping exclude|include` controls NH-based exclusion. `--primary-only`
filters bit 0x100; it does not itself remove supplementary bit 0x800.

`--overlap unique|all|largest` controls multi-feature assignments.
`--fraction true` requires multimapping inclusion and/or all-overlap counting.
**Upstream weighting caveat:** with `--primary-only true`, Subread 2.1.1 disables
NH-based fractional weighting, even when `--fraction true` is selected.
Allocation across overlapping features with `-O` remains fractional. The
adapter warns about this combination rather than labeling it ordinary 1/NH
weighting. Read/filter choices, paired distance checks and long-read mode are
listed in `feature-counts --help`.

`counts.tsv` preserves all meta-features, sample IDs and genomic union lengths;
`assignment-summary.tsv` preserves assigned/unassigned totals. Raw upstream
files remain available. `--junctions true` additionally produces validated
`junction-counts.tsv` with upstream boundary/strand/annotation columns and
sample IDs; the original `.jcounts` file is retained. Zero assigned units are a
prominent warning. Rsubread's R objects/custom `juncCounts` code, UMI
deduplication, EDASeq and DESeq2/edgeR remain separate implementations.

## Native annotation and normalization

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar feature-counts --method annotation --reference ref.fa --annotation genes.gtf --out output/features
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar feature-counts --method splice-sites --reference ref.fa --annotation genes.gtf --out output/splice-sites
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar feature-counts --method normalize --counts output/gene-counts/counts.tsv --normalization tpm --out output/gene-tpm
```

No external runtime is needed for these methods. Gene lengths merge overlapping
exons/isoforms per gene and contig, without double counting. Splice extraction
requires consistent transcript/gene/contig and known +/- strand. Output has
**zero-based exonic flanks**, as expected by HISAT2, not an ordinary BED intron.
Default minimum intron is five bases. Nested exons merge by maximum end;
single-base exons are retained. These stricter corner cases can differ from
the upstream Python helper; invalid records fail rather than being skipped.

TPM divides counts by supplied lengths, then scales the **supplied feature
universe** to one million. An all-zero sample fails explicitly because that
denominator is undefined. For `--normalization rpkm|fpkm`, provide
`--library-units` with one comma-separated denominator per sample column. FPKM
requires fragment counts and fragment denominators. The caller must supply
those units; the normalizer cannot infer them. Genomic union-length gene TPM
does not establish transcript effective-length abundance, or reproduce an
unspecified paper's TPM/FPKM denominator. Raw counts remain the usual input
contract for separate count-based differential-expression tools.

## Long reads, short reads and assemblies with minimap2

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar minimap-tools --home build/omics-minimap2 --reference ref.fa --query reads.fastq.gz --query-format fastq --preset map-hifi --format sam --eqx true --md true --out output/hifi
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar minimap-tools --home build/omics-minimap2 --reference ref.fa --query contigs.fa --query-format fasta --preset asm20 --format paf --secondary false --bed true --out output/assemblies
```

Choose the actual `map-ont`, `map-hifi`, `map-pb`, `lr:hq`, `splice`, `splice:hq`,
`sr`, `asm5`, `asm10` or `asm20` preset. Paired FASTQ requires `sr`, SAM and
`--query2`. SAM retains unmapped reads; every primary name/mate/base/quality is
audited in original orientation, using disk-backed multisets. PAF always
requests base-alignment CIGAR, validates exact names/lengths/spans and records
unmapped query IDs separately. PAF FASTQ needs unique names and at most five
million queries; assembly FASTA is uncompressed. Reference FASTA is uncompressed.

**Tag/index choices:** upstream 2.31 emits MD instead of cs if both are selected.
Use `--md false --cs short|long` for cs. The adapter warns when both are
requested, including the published P068 combination. Default index batch holds
the complete reference and requires sufficient RAM. A smaller explicit
`--index-batch-bases` uses `--split-prefix` merging; upstream then requires
`--md false --cs none`. Existing `.mmi` files are not reused. BED6 exports
complete target spans; spliced spans include introns. PAF spans are zero-based
half-open and query coordinates remain forward-oriented on minus-strand hits.
Suppressed secondary output does not prove unique mapping. Assembly filtering,
genome coverage, variant/SV callers and paftools/k8 remain separate.

## RNA alignment with HISAT2

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar hisat-tools --method index --home build/omics-hisat2 --reference ref.fa --annotation genes.gtf --out output/hisat-index
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar hisat-tools --method align --home build/omics-hisat2 --index output/hisat-index --reads R1.fastq.gz --reads2 R2.fastq.gz --sample SAMPLE --strand RF --dta true --max-intron 40000 --out output/hisat-rna
```

Annotation is optional. Native splice/exon helpers feed upstream annotated
graph construction; all eight small `.ht2` or large `.ht2l` components and
annotation files are sealed and checked before/after use. `--index-mode auto`
selects large at four billion reference bases. Native gzip staging and explicit
small/large dispatch replace the upstream wrapper; no Perl or shell pipeline
is required. Reference/read argument paths containing commas are rejected
because upstream interprets commas as file-list separators.

Single-end RNA strand is `F|R`, paired is `FR|RF`, or explicit/default
`unstranded`. Min/max intron, DTA, soft clipping, linear score, multimapping
limit, splice learning and paired options are visible in `hisat-tools --help`.
`-k=1` does not prove uniqueness. Known splice lists use zero-based exonic
flanks. Learned splice sites and raw mapping summaries remain in the bundle;
parallel splice learning has a scheduling warning. SAM is audited for exact
reference/sample/RG and every primary name/mate/base/quality, without discarding
secondary/supplementary output. StringTie/Ballgown, gene quantification,
HISAT-genotype SNP/haplotype graphs and CIRIquant pseudo-circle construction
are separate workflows.

## Primary references and evidence boundary

Official documentation: [featureCounts](https://subread.sourceforge.net/featureCounts.html),
[Subread releases](https://sourceforge.net/projects/subread/files/subread-2.1.1/),
[minimap2 releases/source](https://github.com/lh3/minimap2/releases),
[HISAT2 releases/source](https://github.com/DaehwanKimLab/hisat2/releases), and
[HISAT2 manual](https://daehwankimlab.github.io/hisat2/manual/).
HISAT2's older download page lists 2.2.1; the pinned GitHub release is 2.2.3.

The catalog distinguishes all 42 reviewed featureCounts/minimap2/HISAT2 uses,
published versions, missing settings, R interfaces and downstream operations.
Known-input tests cover exact splice geometry, strand/reverse orientation,
read/pair units, overlapping/multimapping fractions, peak and junction counts,
small/large and split indexes, all-unmapped inputs and actionable failures.
These are tested source integrations, not a release or complete reproduction
of every paper or historical program version.
