# SAMtools and BCFtools workflows

The source build exposes native Java FASTA indexing plus eleven typed `hts-tools` methods using SAMtools,
BCFtools and HTSlib **1.24**. These are actual upstream applications, with
independent fixture checks; unexposed commands and older paper versions still
need separate reconciliation. See the [catalog](README.md),
[implementation queue](implementation-plan.md), and [warnings](WARNINGS.md).

## Runtime setup

Windows x86_64 users can install a private runtime where downloads are permitted:

```powershell
python tools/literature/install_hts_dependencies.py --allow-download
```

Executables are `build/omics-hts/ucrt64/bin/samtools.exe` and
`build/omics-hts/ucrt64/bin/bcftools.exe`. The helper verifies all 38 packages and
their declared dependency closure against the frozen publisher checksums in
`tools/literature/msys2-hts-lock.json`. These are
[MSYS2 SAMtools](https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-samtools)
and [BCFtools](https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-bcftools)
builds. Package metadata and licenses are retained. No OS packages, package-manager
scripts or global PATH changes are involved. Other hosts can supply explicitly
installed executables with the same pinned application and HTSlib versions.

**If installation is forbidden:** omit `--allow-download`; unavailable runtime
setup returns 3 and writes the exact recovery action to its report's `WARNINGS.md`.
Skip dependent analyses until an administrator supplies the runtime. An already
verified managed installation can be used without download permission. Unmanaged
or integrity-failed directories are preserved; select a new `--environment` after
resolving the recorded cause. The helper needs Python `zstandard` to read the
pinned package archives. Availability/version checks alone do not prove an analysis.

## Alignment processing

Use your actual input files and a new output directory for every command:

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method sam-sort `
  --samtools build/omics-hts/ucrt64/bin/samtools.exe --input aligned.sam `
  --create-index true --out build/hts-sorted
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method sam-markdup `
  --samtools build/omics-hts/ucrt64/bin/samtools.exe `
  --input build/hts-sorted/alignment.bam --out build/hts-duplicates
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method sam-view `
  --samtools build/omics-hts/ucrt64/bin/samtools.exe `
  --input build/hts-duplicates/alignment.bam --require-flags 2 `
  --exclude-flags 1024 --min-mapq 20 --out build/hts-selected
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method sam-flagstat `
  --samtools build/omics-hts/ucrt64/bin/samtools.exe `
  --input build/hts-selected/alignment.bam --out build/hts-flags
```

`sam-sort` checks record content/counts and actual ordering. Query-name sorting
uses explicit lexicographical `-N`, selected with `--sort-order queryname`;
this differs from natural `-n` ordering. Memory defaults to 256 MiB per sorting
thread; `--memory-mb` and `--threads` are explicit resource controls.
`--threads` means upstream additional/compression threads, not total CPU threads.

`sam-markdup` performs name sorting, `fixmate -m`, coordinate sorting and
`markdup`. It marks duplicates by default; use `--remove-duplicates true` for
removal. The independently tested paired fixture contains two pairs with one
duplicate pair; removal retains one complete pair. Successful intermediate BAMs
are removed after their hashes are recorded; failed stages are retained under
`partial/`. Review native markdup statistics in `external.log`.
The upstream default duplicate matching is used, without barcode/UMI or
read-group-specific options. Supply an appropriate library grouping; do not
assume separate read groups automatically prevent cross-library duplicate matches.
Optical distance defaults to zero; choose `--optical-distance` for the actual
instrument and read-name layout before interpreting optical duplicates.

`sam-view` requires every `--require-flags` bit and excludes any
`--exclude-flags` bit; neither mask is inferred. Output identities/content are
checked against an independent Java scan. P004's properly paired/nonduplicate,
MAPQ >=20 selection corresponds to the example above. The preceding 1.9 markdup
workflow and this 1.24 runtime are not claimed numerically identical.

An optional `--region chr1:100-200` selects overlapping alignments with **one-based,
inclusive** coordinates and requires an indexed BAM. No chromosome aliases or
genome-build conversion are inferred. `sam-index` writes a new `alignment.bai`
in its output bundle while preserving the source BAM; use it alongside that
exact source file, or use `sam-sort --create-index true` for a colocated pair.
This subset exposes BAI indexing, not CSI generation; references/positions beyond
BAI's range require a separate supported indexing workflow.

`sam-flagstat` retains the upstream JSON counts, separating QC-passed and
QC-failed records. Total, mapped, primary and duplicate counts are independently
checked; flag statistics do not replace assay-specific QC decisions.

## Textual pileup and variant calling

These operations need an uncompressed local reference FASTA and its existing matching `.fai`.
Indexed operations also require a coordinate BAM and its matching `.bai`/`.csi`.
The reference, FASTA index and consumed BAM index are hashed.

Generate an index directly in Java, without SAMtools or BCFtools:

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method fasta-index `
  --input reference.fa --out build/reference-index
```

The result contains `reference.fa.fai`, sequence/base counts and source hashes.
Place that generated file beside the **exact original FASTA**, preserving any old
index for audit. Neither the source nor an existing index is modified by the
native command. The Java implementation uses HTSJDK 5.0.0, indexes actual LF/CRLF
byte widths, and checks serialization and source stability. Compressed references
need a separate `.gzi`-aware workflow. Variant processing and Picard alignment
metrics check existing `.fai` lengths, byte offsets and line widths against the
actual FASTA before use. A stale index is an explicit error; changing line endings
after indexing can corrupt reference retrieval even if sequence letters match.

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method sam-mpileup `
  --samtools build/omics-hts/ucrt64/bin/samtools.exe `
  --input build/hts-sorted/alignment.bam --reference reference.fa `
  --min-mapq 30 --min-baseq 30 --baq false --out build/hts-pileup
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method bcf-call `
  --bcftools build/omics-hts/ucrt64/bin/bcftools.exe `
  --input build/hts-sorted/alignment.bam --reference reference.fa `
  --region chr1:100-200000 --ploidy 2 --out build/hts-calls
```

`sam-mpileup` emits raw six-column `pileup.tsv`, with one-based positions.
Its defaults are minimum mapping quality 0, base quality 13, depth cap 8000,
BAQ enabled and excluded flag mask 1796 (unmapped, secondary, QC-failed,
duplicate). Set `--baq false` for `-B`. It is single-threaded; `--threads` is
rejected. The P002 ancient-DNA paragraph specifies MAPQ 30, base quality 30 and
BAQ disabled. Its separate pileupCaller random pseudo-haploid genotype sampling,
SNP panels and upstream ancient-DNA processing remain outside this method.

`bcf-call` runs modern BCFtools `mpileup` followed by the multiallelic caller and
retains `pileup.bcf` plus variant-only `variants.vcf`. Uniform ploidy **must be
specified** as 1 or 2. Defaults are minimum mapping quality 0, base quality 13 and
depth cap 250; change the typed options for the actual study. Read-group sample
names are required. Sample identities are checked. Known independent fixtures
test a heterozygous diploid SNV and a haploid alternate call.

**The declared model matters:** uniform ploidy does not infer sex/chromosome,
pooled lineages, mosaicism or mitochondrial heteroplasmy. Empty variant output
does not prove all sites were callable. P027 reports old SAMtools/BCFtools variant
identification from pooled mutant lineages without exact subcommands; this adapter
does not establish its older-version or pooled-model equivalence. Later GATK
mapping-SNP genotyping remains separate work.

## Variant processing

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method bcf-norm `
  --bcftools build/omics-hts/ucrt64/bin/bcftools.exe --input calls.vcf `
  --reference reference.fa --out build/hts-normalized
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method bcf-filter `
  --bcftools build/omics-hts/ucrt64/bin/bcftools.exe --input calls.vcf `
  --min-qual 30 --out build/hts-filtered
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method bcf-select `
  --bcftools build/omics-hts/ucrt64/bin/bcftools.exe --input calls.vcf `
  --samples-list samples.txt --out build/hts-samples
java -jar build/cli/omicsmatic-0.3.7.jar hts-tools --method bcf-stats `
  --bcftools build/omics-hts/ucrt64/bin/bcftools.exe --input calls.vcf `
  --out build/hts-stats
```

`bcf-norm` checks REF against the supplied FASTA, left-aligns and normalizes
alleles, and splits multiallelic records by default (`--split-multiallelic false`
disables splitting). A reference mismatch fails and quarantines partial output.
Use the correct genome assembly and allele representation before interpretation.

`bcf-filter` **soft-labels**, without removing sites, missing QUAL or QUAL below
`--min-qual`; existing filters remain. The default new label is `JL_LOW_QUAL`.
Choose a new `--filter-name` if that label already exists; the adapter rejects
ambiguous reuse. It does not infer study-wide hard filters, depth thresholds or
genotype masking.

`samples.txt` contains one unique known sample ID per line, no header or whitespace.
`bcf-select` retains sites and checks the requested sample order; BCFtools updates
standard AC/AN fields for the selected samples. Independent fixtures check the
genotypes and allele counts. It does not automatically recalculate every custom
annotation. The list is bounded to 10,000 samples and 1 MiB.

`bcf-stats` retains native `variant-stats.txt` and checks its record total against
an independent VCF scan. This command has no compression output; `--threads` is
rejected. Variant-processing inputs are local VCF or VCF.gz; arbitrary BCF inputs,
plugins, filter expressions, remote sources and CRAM are outside this typed subset.

## Errors, skips and reproducibility

Every attempt retains `analysis-status.json`, session/version information,
commands, hashes, native logs and a manifest. Private Windows executable/DLL
hashes are recorded. Successful BAM validation uses explicit iterator closure
and non-memory-mapped index reads, so Windows handles are released before the
result directory is published. BAM/VCF records are streamed.

- Missing/incompatible executables or DLL dependencies: **status 3**, skipped
  analysis and prominent `WARNINGS.md` with installation/recovery steps.
- Input, reference, native-runtime or output-validation failure: **status 2**;
  partial outputs are quarantined and no successful `results.tsv` is published.
- Upstream warnings or empty output: retained `WARNINGS.md` and console notice;
  inspect the named settings and logs before downstream use.

Defaults and scope come from the [SAMtools manuals](https://www.htslib.org/doc/)
and [BCFtools manual](https://samtools.github.io/bcftools/bcftools.html).
The fixture checker `tools/literature/verify_hts_tools.py` preserves real
packaged-command inputs and outputs; it is separate from production study data.
