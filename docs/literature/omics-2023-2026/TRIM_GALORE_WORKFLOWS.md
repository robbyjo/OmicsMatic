# Original Trim Galore workflows and native read operations

`trim-galore-tools` runs the unmodified Perl releases 0.6.7 or 0.6.10 with
original Cutadapt 5.2. Java validates paired identities, preserves headers and
qualities, and independently audits the source spans, final filters and every
paired, single and orphan output. Optional FastQC runs the original 0.13.0 JVM
application. The [native read helper](../../trim-galore-native-reads.md) separately
provides ordinary **post-Cutadapt** finalization and first/last-base hardtrim
without external software. Native finalization alone does not remove adapters.

The validated canonical source-build JAR is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`66be181d854806d9ae64d6f060e8defba8af5bdf594d3d352c9c76097a2a8d2e`. It passed the isolated 1,552-test gate
with zero failures/errors, all 72 required suites without skips and five
optional skips. Twenty-two separately launched original workflows matched
25 packaged calls; two further packaged checks preserved Unicode input/output
paths under Temurin 21 and Oracle 25. All 25 earlier/native packaged verifier
groups and the complete 197-test Python policy suite passed. The earlier
legacy-R policy timeout is retained; the final complete run supersedes it.
See [adapter validation](trim-galore-adapter-validation.json),
[packaged comparisons](trim-galore-packaged-validation.json) and
[dependency status](trim-galore-dependency-status.json). This work is unreleased.

## Dependency failure means analysis skipped

**ACTION REQUIRED: read `WARNINGS.md` before using any output.** Missing,
changed, forbidden or unavailable dependencies return **exit 3** and
`analysis-status.json` records `skipped-dependency`. The command prints
**ANALYSIS SKIPPED** and the warning names the cause and recovery action.
No successful `results.tsv` is published. Analysis never installs dependencies.
When installation is unavailable, skip this upstream analysis until a permitted
verified runtime is supplied.

Input, execution or audit failures return **exit 2**, with failed status, the
error, recovery steps and retained logs/partial streams under `partial/`.
Correct the cause and use a new output directory. QC warnings can accompany
**exit 0**: `completed-with-qc-warnings` still requires review of the named
original reports before downstream analysis. An installer version probe proves
availability; it does not prove scientific validity.

The prepared runtime supports Windows x86_64 with an existing permitted Cygwin
Perl and `sh`, native Python 3.13 and a JVM when FastQC is requested. The installer
reads existing installations, writes a new private profile and retains source
licenses. It never changes the OS, registry or persistent PATH. Other platforms
need a separately validated profile.

With an existing permitted Python environment containing the exact pinned
Cutadapt dependencies, run from the project root:

```powershell
python tools/literature/install_trim_galore.py --environment build/omics-trim-galore-authenticated --report build/literature-review/trimgalore-runtime/setup --perl C:/cygwin64/bin/perl.exe --python build/omics-sequence-tools-env/Scripts/python.exe --fastqc-home build/omics-fastqc/FastQC --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe" --allow-install --allow-download
```

For a fresh private Python environment, replace the Python selection with:

```powershell
python tools/literature/install_trim_galore.py --environment build/omics-trim-galore-fresh-fixed --report build/literature-review/trimgalore-runtime/fresh-setup --perl C:/cygwin64/bin/perl.exe --prepare-python --python-base D:/anaconda3/python.exe --python-environment build/omics-trim-galore-python-fixed --fastqc-home build/omics-fastqc/FastQC --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe" --allow-install --allow-download
```

Choose paths that match permitted installations on the host. New profile
creation requires `--allow-install`; network fetching separately requires
`--allow-download`. Verified cached files can be used without download permission.
A verified existing profile can be checked/reused without either flag. An
existing unmanaged, incomplete or changed home is preserved and skipped; select
a new private `--environment`. The optional Python environment is created at its
final path, its actual Cutadapt console launcher is checked, and existing/global
Python packages remain unchanged. Failed attempts and dependency-status history
are retained; an incomplete Python directory likewise requires a new destination.

The lock pins original scripts, README/license files and six official Windows
wheels: Cutadapt 5.2, dnaio 1.2.4, xopen 2.1.0, isal 1.8.0, zlib-ng 1.0.0 and
backports.zstd 1.8.0. Installed package members are authenticated against the
verified wheels. Optional FastQC files are authenticated against its original
0.13.0 archive. Profiles seal source files and the read-only referenced
Perl/Python/JVM/FastQC dependency inventory before and after execution. Windows
system DLLs remain an explicit host boundary. No legacy Cutadapt version or the
new Rust Trim Galore rewrite is silently substituted.

## Explicit ordinary processing

Supply the actual original release and `--phred 33` or `64`. Omitted adapter,
quality and length settings retain the original program's defaults and adapter
auto-detection. Auto-selected small RNA can use minimum length 18 rather than
ordinary 20; actual reports supply the minimum used by the Java audit.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar trim-galore-tools --home build/omics-trim-galore-authenticated --release 0.6.7 --reads reads_R1.fastq.gz --reads2 reads_R2.fastq.gz --phred 33 --quality 20 --minimum-length 20 --overlap 1 --fastqc true --out output/trimmed
```

`--adapter-kind` accepts `auto`, `custom`, `none`, `illumina`, `nextera`,
`small-rna` and, for 0.6.10, `stranded-illumina`. Custom adapters are literal DNA
strings supplied with `--adapter1` and optionally paired `--adapter2`; arbitrary
Cutadapt expressions, shell arguments and adapter files are not accepted.
`--overlap` is original stringency, not a length filter. `--nextseq` and
`--quality` are mutually exclusive. Original Phred64 behavior shifts the quality
cutoff; it is preserved rather than replaced with a different Cutadapt encoding
flag. Combined NextSeq/Phred64 is unvalidated and rejected.

Fixed-end options are `--clip-r1`, `--clip-r2`, `--three-prime-clip-r1` and
`--three-prime-clip-r2` (0..99 bases). They operate after Cutadapt and preserve
original length guards: a cut at least as long as the current read is skipped.
`--minimum-length`, `--maximum-length`, `--maximum-n`, `--trim-n`,
`--retain-unpaired`, `--orphan-min1` and `--orphan-min2` expose typed original
options. Uppercase `N` is counted literally; lowercase `n` is distinct.

**Review every orphan stream separately.** Original rescue occurs before the
maximum-length test, so a rescued orphan can exceed the paired maximum.
Default orphan minimum 35 can be below a paired minimum of 50 or 100, and both
mates can be rescued individually. Explicit orphan thresholds must be 15..100
and exceed the paired minimum. Original 0.6.7 rejects paired maximum length and
fractional N limits. Original 0.6.10 ordinary single-end fractional N handling
also compares the N count to that fraction, rejecting any uppercase N when the
fraction is below one; paired handling differs. The audit preserves and reports
these observed behaviors.

## Hardtrim and Unicode paths

`--mode hardtrim5 --bases 50` keeps the **first** 50 bases;
`--mode hardtrim3 --bases 50` keeps the **last** 50. Short and empty reads are
preserved. The original hardtrim branch exits before ordinary adapter, quality
or length processing. The adapter rejects extra filtering/QC options in this
mode instead of silently ignoring them.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar trim-galore-tools --home build/omics-trim-galore-authenticated --release 0.6.10 --mode hardtrim5 --bases 50 --reads reads.fastq.gz --out output/first50
```

**Ordinary original Cutadapt requires ASCII read headers/comments.** Non-ASCII
identifiers cause an early input error with a request for an explicit reversible
ASCII-ID mapping. The adapter does not silently truncate or rename identifiers.
The original hardtrim branch and native helper preserve UTF-8 headers.

**Windows paths need care.** The tested Cp1252 JVM launchers replace some
non-ANSI command-line path characters before Java receives them. Use an ASCII
path for a UTF-8 JSON `--options-file`; the file can contain the actual Unicode
data paths. Its keys are known long-option names without `--`, and every value
is a string. Explicit command-line values override the file. Recursive option
files, unknown keys and non-string values are rejected. Paths are resolved from
the command's working directory. The exact recipe snapshot/hash is retained
and checked again before results are published. Reads and identities are not
renamed by this mechanism.

```json
{
  "home": "build/omics-trim-galore-authenticated",
  "release": "0.6.10",
  "reads": "data/reads-μ.fastq.gz",
  "phred": "33",
  "minimum-length": "20",
  "out": "output/trimmed-μ"
}
```

Save this as UTF-8 without a byte-order mark, using an ASCII recipe filename:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar trim-galore-tools --options-file trim-options.json
```

## Outputs, paper boundaries and evidence

The original program runs on safe local staged filenames, using one core and
uncompressed intermediate streams. Raw stdout/stderr, actual arguments, exit
codes, module provenance and original reports remain under `original/`.
Transport normalization only converts Windows CRLF output from native console
programs to the LF form required by the original Perl parser. It does not change
bases, quality values or scientific algorithms. Java matches both sequence and
quality to the source, records ambiguous source positions as unknown, verifies
all output streams/counters and checks that inputs/dependencies did not change.
Default published FASTQ streams are gzip-compressed; `--gzip false` selects
plain output. The result bundle includes `results.tsv`, `read-status.tsv`,
`session.yaml` and `analysis-status.json`.

Original Trim Galore/Cutadapt `WARN`/`WARNING` blocks from
`original/original.stderr` are promoted with their full indented context into
`WARNINGS.md` and `session.yaml` (`original_warning_blocks`). In an actual
32-read fixture, Cutadapt warned that a frequently preceding base could mean
an incomplete adapter; its report also says to ignore that warning when
trimming primers. The summary preserves both statements. Review the assay and
adapter context explicitly; a warning does not silently rewrite the adapter,
change trimming decisions or discard reads.

Requested original FastQC runs on the original output files, not input reads;
pre-trim QC must be a separate explicit analysis. `fastqc-results.tsv` preserves
original module PASS/WARN/FAIL flags. `WARNINGS.md` and the console promote those
flags and original quality-encoding diagnostics. A tiny high-quality fixture
triggered an original encoding heuristic diagnostic; it is retained rather than
silently repaired. QC flags are not automatic instructions to discard reads.
An empty-output QC execution failure returns exit 2 and retains partial data.

With paired orphan rescue, original FastQC assesses the retained paired
streams. The native audit verifies orphan contents and counts, but the paired
QC table does not assess orphan quality. Run separate explicit QC for orphan
streams before using them downstream.

The 11 reviewed papers describe multiple assays and older versions. The
implementation does not establish parity for 0.3.1, 0.3.7, 0.5.0, 0.6.2, 0.6.4
or 0.6.6. P044's explicit RNA options must not be transferred to its ATAC assay.
P037's DNA/RNA pair cutoffs of 50/100 differ. P046 reports WGBS, which does not
imply RRBS. P099 reports a default step described as removing poly-A; this does
not establish an explicit `--poly_a` flag. P122's wording attributes version
0.6.10 ambiguously to TrimGalore/Cutadapt and does not establish a cropping flag;
a 50-base crop and minimum-length filter remain distinct. P163's 35/75-base
values describe input read lengths. See the catalog's primary/supporting source
pointers for these statements.

RRBS, explicit poly-A mode, clock/UMI handling, multicore execution, arbitrary
shell/FastQC options, historical Cutadapt/FastQC versions and complete paper
pipelines remain unvalidated through this adapter. Source releases and bounded
independent fixtures define the available scope.
