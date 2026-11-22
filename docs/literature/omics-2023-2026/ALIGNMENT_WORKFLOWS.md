# BWA and Bowtie 2 read alignment

The source build provides `read-align` with actual Bowtie 2 **2.5.5** and BWA
**0.7.19-r1273** programs. It builds reference-bound index bundles and maps
unspliced short-read DNA FASTQ, preserving unmapped reads and checking every
primary mate, sequence, quality string and reference dictionary. These typed
operations do not establish complete published-pipeline or older-version parity.
See [warnings and recovery](WARNINGS.md) and the [implementation queue](implementation-plan.md).

## Private Windows runtimes

```powershell
python tools/literature/install_aligner_dependencies.py --profile bowtie2 --allow-download
python tools/literature/install_aligner_dependencies.py --profile bwa --allow-download --allow-source-build
```

Bowtie 2 uses the [official upstream Windows archive](https://github.com/BenLangmead/bowtie2/releases/tag/v2.5.5),
56525994 bytes, publisher SHA-256
`77bfac3a84c54f4032ee851c19917802decc1b28083ce17a5f92a0c4d729bdba`.
Use `build/omics-align-bowtie2/bowtie2-2.5.5-mingw-x86_64` as `--bowtie-home`.
The adapter invokes the direct small/large C++ binaries and implements its explicit
index-family, gzip-input and output-order contract without the Perl/Python wrappers.
It does not claim all wrapper functionality.

BWA uses the [unmodified tagged upstream source](https://github.com/lh3/bwa/releases/tag/v0.7.19).
The retrieved source archive SHA-256 is
`cdff5db67652c5b805a3df08c4e813a822c65791913eccfb3cf7d528588f37bc`;
this records the retrieved source bytes, not a separately published binary digest.
The helper builds it with a private, checksum-pinned MSYS2 POSIX toolchain
(21 declared compiler/runtime packages, about 85.37 MiB downloaded), using GNU C11
and the upstream optimization flags. No algorithm patches, OS installation,
package-manager scripts or global PATH changes are involved. The executable is
`build/omics-align-bwa/bin/bwa.exe`. Source, build log, compiler-package identities,
licenses and runtime DLL hashes are retained. Python `zstandard` is needed to
read the build-package archives; install that helper dependency only where allowed.

**Compiler warnings are retained:** upstream BWA warnings remain in the dependency
report and `build.log`; the unmodified source completed its build. Review them
before changing compiler settings or using unvalidated operations/input types.
Successful compilation/version probes are separate from actual analysis validation.

**When installation or source building is forbidden:** omit the corresponding
allow flags. If the runtime is needed and unavailable, setup and dependent analysis
skip with status 3 and actionable `WARNINGS.md`. An already verified managed runtime
can be reused without download/build permission. Unmanaged or integrity-failed
directories are preserved; choose a new `--environment` or `--toolchain` after
correcting the recorded cause. Other operating systems can supply native pinned
upstream programs explicitly; this installer supplies Windows x86_64 runtimes.

## Build an index once

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar read-align --method bowtie2-index `
  --bowtie-home build/omics-align-bowtie2/bowtie2-2.5.5-mingw-x86_64 `
  --reference GRCh38.fa --out build/grch38-bowtie2
java -jar build/cli/omicsmatic-0.3.7.jar read-align --method bwa-index `
  --bwa build/omics-align-bwa/bin/bwa.exe --reference GRCh38.fa `
  --out build/grch38-bwa
```

The input is an uncompressed FASTA. Each bundle contains `aligner-index.json`,
the original reference SHA-256 and sequence dictionary, a native FASTA index,
native logs, and hashes of every generated aligner index component. The reference
is checked for changes during construction; the original FASTA is not modified.
Alignment accepts this sealed bundle rather than an untracked external prefix.
Mixed/corrupt components, wrong engines/versions and changed manifests fail
explicitly. Components are checked again after alignment.

Bowtie `--index-mode auto` selects a large index at four billion reference bases;
`small` and `large` can also be selected explicitly. Both families have real tests.
BWA `--algorithm auto|is|bwtsw` selects the native builder; IS is rejected for
references at or above 2 GiB. Individual contig lengths must fit the Java/SAM
coordinate range. Select the actual genome build; no chromosome aliasing,
sex-specific reference construction or ALT-contig annotation is inferred.

## Bowtie 2 single/paired alignment

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar read-align --method bowtie2 `
  --bowtie-home build/omics-align-bowtie2/bowtie2-2.5.5-mingw-x86_64 `
  --index build/grch38-bowtie2 --reads R1.fastq.gz --reads2 R2.fastq.gz `
  --sample sample1 --threads 4 --preset very-sensitive `
  --min-insert 0 --max-insert 700 --dovetail true --out build/sample1-bowtie2
```

Omit `--reads2` for single reads. The example exposes P007's ATAC-seq/CUT&Tag
very-sensitive/dovetail/0–700 insert settings; that paper used 2.5.1, whereas this
adapter pins 2.5.5. Defaults are `--mode end-to-end --preset sensitive --seed 1`.
For P004's local mapping use `--mode local`; the selected preset becomes its
corresponding local variant. Presets are `very-fast`, `fast`, `sensitive`,
`very-sensitive`. Local soft-clipping, both index families, gzip inputs and
strand-adjusted qualities have known-origin fixtures.

Paired defaults are minimum insert 0, maximum insert 500, dovetail false, mixed
alignment true and discordant alignment true. Set `--mixed false` and/or
`--discordant false` explicitly when the experiment requires them. Input order
is preserved with upstream `--reorder`, including multithreaded runs. The adapter
retains one primary record per input mate, including unmapped mates.

P003's default GAM mapping was followed by nonunique-read, MAPQ and PCR-duplicate
selection. Those criteria are separate processing steps; one reported alignment
does not itself prove unique mapping. The P007 paper also contains a separate
MPRA barcode-association workflow requiring additional-use review.

## BWA-MEM and BWA backtrack

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar read-align --method bwa-mem `
  --bwa build/omics-align-bwa/bin/bwa.exe --index build/grch38-bwa `
  --reads R1.fastq.gz --reads2 R2.fastq.gz --sample sample1 `
  --threads 4 --out build/sample1-bwa
java -jar build/cli/omicsmatic-0.3.7.jar read-align --method bwa-backtrack `
  --bwa build/omics-align-bwa/bin/bwa.exe --index build/grch38-bwa `
  --reads merged.fastq.gz --sample ancient1 --seed-length 32 `
  --out build/ancient1-bwa
```

MEM defaults to seed length 19 and minimum score 30. Explicit
`--mark-shorter-split-secondary true` adds `-M`; known split-read fixtures check
the secondary versus supplementary reporting difference. Default is false.

Backtrack performs `aln` followed by `samse` or `sampe`, with synchronized paired
inputs when provided. Default seed length is 32. An optional integer
`--max-differences` sets `aln -n`; otherwise the native default false-negative-rate
behavior remains. `reads1.sai`/`reads2.sai` and native logs are retained.

**Choose the actual algorithm:** P002 reports BWA 0.7.12 and seed length 32 within
nf-core/eager but does not name MEM versus backtrack in that paragraph. Reconcile
the exact workflow before selecting one. P027 explicitly names BWA-MEM
0.7.17-r1188 for C. elegans mapping; its pooled variant-calling and GATK steps
remain separate. Neither case establishes full old-version equivalence here.

**BWA-MEM2 is a different implementation.** Its three reviewed observations are
now separately cataloged; this BWA 0.7.19 adapter does not supply MEM2, its versions
or options. It remains in the implementation queue. Do not replace it silently.

## Input, output and failure contracts

FASTQ must be strict four-line canonical DNA A/C/G/T/N with Phred+33 qualities,
plain or `.gz`. Native validation checks every record and synchronized pair ID,
normalizing terminal `/1` and `/2` for identity comparisons. Headers may include
comments; SAM reports the upstream read-name token. Sample/read-group/library
labels use 1–128 ASCII letters, digits, underscore, dot or hyphen.

`--sample` is required; `--read-group` defaults to `JL1`, with optional `--library`.
Read groups carry `PL:ILLUMINA`, matching the short-read library scope. The output
is unsorted `alignment.sam` plus counts for primary/mapped/proper-pair, secondary
and supplementary records. Every primary record is checked against the input
mate, strand-adjusted sequence/quality, sample/read group and exact reference
dictionary. FASTQ and index hashes must remain stable during analysis.

No trimming, uniqueness filtering, duplicate removal, splice mapping, ALT-aware
postprocessing, Dip-C-specific configuration or BAM sorting is inferred. Use the
[typed HTS workflows](HTS_WORKFLOWS.md) or Picard stages explicitly afterward.
Long-read/spliced-RNA platforms require appropriate separate methods and metadata.
Default threads is 1 (range 1–64), timeout 3600 seconds (range 1–86400).

- Missing/incompatible runtime or DLLs: **status 3**, skipped analysis, explicit
  setup/recovery instructions in `WARNINGS.md`.
- Invalid reads/index/settings, native execution or output validation failure:
  **status 2**, retained diagnostics, quarantined partials, no successful `results.tsv`.
- Native warnings or zero mapped primaries: prominent warning file and console
  notice. Review reference choice, preprocessing and settings before interpreting
  low alignment; it does not establish biological absence.

Output publication uses the existing bounded Windows retry/retained-failure
protocol. Existing destinations are never overwritten. Installer and analysis
reports are separate. `tools/literature/verify_read_alignment.py` retains the
independently generated DNA/read-origin fixtures and actual executable outputs.
Upstream method documentation: [BWA](https://github.com/lh3/bwa) and
[Bowtie 2](https://bowtie-bio.sourceforge.net/bowtie2/manual.shtml).
