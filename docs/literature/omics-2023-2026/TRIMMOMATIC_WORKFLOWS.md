# Native read trimming and original Trimmomatic workflows

`trimmomatic-tools` adds thirteen native Java quality/length operations and an
adapter to original, hash-pinned Trimmomatic 0.39 and 0.41. Native execution needs
only the JLinAlg executable JAR. Adapter clipping runs the original application;
it is not approximated by the native quality steps. Both engines preserve
literal step order and audit every retained read against the original trim span,
header, bases and qualities, including duplicate headers and all paired/orphan
outcomes. No downstream analysis runs automatically.

**ACTION REQUIRED: declare Phred encoding, step order and per-mate settings.**
Supply `--phred 33` or `64`; encoding is never inferred. Read all four paired
output files and their counts before alignment. Review `WARNINGS.md`,
`analysis-status.json`, `read-status.tsv`, `trim.log`, `summary.txt`, source hashes
and actual command/version in `session.yaml`. All-filtered reads, orphan-only
outputs and Phred64 are promoted to warnings and the console.

**RUNTIME UNAVAILABLE: skip the affected upstream analysis.** Exit 3 means the
original program/JVM could not be verified or launched. The output names the
exact cause and recovery action. Native mode remains usable. Where downloads
are permitted, from the project root on Windows x64 with Python 3.11+ run:

```powershell
python tools/literature/install_trimmomatic.py --version 0.41 --private-java --allow-download
python tools/literature/install_trimmomatic.py --version 0.39 --java build/omics-java25/bin/java.exe --allow-download
```

The first command installs an optional private portable Temurin 25.0.4.1+1 JRE;
it does not change the OS, registry or PATH. Trimmomatic 0.41 requires Java 25+;
0.39 requires Java 8+. `--java` can select an existing supported installation.
Private homes default to `build/omics-trimmomatic/VERSION` and
`build/omics-java25`. Report directories contain dependency status, immutable
attempt logs and warnings. Verified installations can be reused without
`--allow-download`; absent/changed/unmanaged homes are preserved and skipped.
If installation is forbidden or fails, supply a permitted verified runtime and
rerun to a new output directory. A version probe alone does not validate an
analysis. Private runtime files are hashed before and after execution; an
explicit unmanaged Java installation has its executable hashed but its external
libraries are not sealed.

## Explicit pipelines

The examples use PowerShell 7 with UTF-8 without a byte-order mark. Create a real
tab-separated text file. Its first line is exactly
`step<TAB>arguments`; subsequent rows contain one operation followed by its
tab-separated arguments. Blank and `#` comment lines are allowed. No default
steps, silent reordering or arbitrary upstream arguments are supplied.

For the leading/trailing Phred20 operations reported in [P201](https://doi.org/10.1016/j.ajhg.2024.09.004):

```powershell
"step`targuments`nLEADING`t20`nTRAILING`t20" | Set-Content -Encoding utf8 quality.tsv
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar trimmomatic-tools --engine native --reads reads.fq.gz --phred 33 --steps quality.tsv --out output/quality --no-config
```

This executes the specified operations. P201 used 0.36, so it does not establish
whole historical-pipeline parity. The executable JAR is the isolated validated
source build; this work is unreleased.

| Operation | Arguments after the name |
| --- | --- |
| LEADING, TRAILING, AVGQUAL | Minimum quality |
| SLIDINGWINDOW | Window size, quality threshold |
| HEADCROP, TAILCROP | Bases; optionally max length; optionally second-mate bases and second-mate max length (1, 2 or 4 fields) |
| CROP, MINLEN, MAXLEN | Length |
| BASECOUNT | Literal base characters; optional minimum and maximum |
| TOPHRED33, TOPHRED64 | No fields |
| MAXINFO | Target length (1–1000), strictness (0–1) |

HEADCROP's max-length parameter removes additional bases from the **head**;
TAILCROP removes them from the tail. Uppercase `N` contributes quality zero in
upstream quality scoring; lowercase `n` is not normalized. BASECOUNT is literal
and case sensitive. SLIDINGWINDOW includes upstream's final tail cleanup, and
reads shorter than its window are dropped. **TRAILING rejects one-base reads in
both tested releases; this behavior is preserved and warned.**

**MAXINFO has an upstream 1000-base limit at that step.** A longer read fails
with an exact action; no automatic crop is inserted. Choose an assay-appropriate
pipeline explicitly. CROP and crop max-length parameters must be positive;
empty input reads and zero-length crop workflows are rejected. Phred64 cannot
encode a score above 62 in ASCII FASTQ; add TOPHRED33 explicitly if required.
FASTQ output uses canonical plus lines; original plus-line comments are not
preserved. Input headers and duplicate multiplicities are retained.

P091 removes ten bases from **forward reads only**, before FLASH merging:

```powershell
"step`targuments`nHEADCROP`t10" | Set-Content -Encoding utf8 forward.tsv
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar trimmomatic-tools --engine native --reads forward.fq.gz --phred 33 --steps forward.tsv --out output/forward --no-config
```

Keep its original reverse file for the later, separate merger. FLASH's reported
overlap/mismatch rules are not implemented by trimming. Do not silently apply
HEADCROP10 to both files.

## Original adapter clipping and paired reads

ILLUMINACLIP is available only with `--engine upstream`. Its tab-separated
fields are adapter FASTA, seed mismatches, palindrome threshold, simple
threshold, optional minimum palindrome length and optional keep-both boolean.
Adapter paths resolve relative to the pipeline. `@NAME.fa` selects an adapter
inside the verified private runtime; **stock Illumina adapter sequences have
separate rights and are not bundled in JLinAlg**. Supply the actual assay
adapter file. A safe relative copy avoids upstream's Windows drive-colon parser
problem. Named adapter pairs and palindrome behavior remain upstream behavior.

For the **literal order** reported by [P171](https://doi.org/10.1038/s41588-026-02678-9):

```powershell
"step`targuments`nILLUMINACLIP`tadapter.fa`t2`t30`t10`nMINLEN`t36`nSLIDINGWINDOW`t4`t20" | Set-Content -Encoding utf8 chip.tsv
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar trimmomatic-tools --engine upstream --home build/omics-trimmomatic/0.39 --reads R1.fq.gz --reads2 R2.fq.gz --phred 33 --steps chip.tsv --threads 2 --memory-mb 512 --timeout 3600 --out output/chip --no-config
```

MINLEN36 runs **before** SLIDINGWINDOW here. Later shortening can retain a read
below 36 bases; appending/reordering a length filter would change the pipeline.
The paper's unspecified adapter bytes must still be supplied, so this example
does not certify reproduction of the study.

Single-end output is `trimmed.fastq.gz`. Paired output is
`read1.paired.fastq.gz`, `read1.unpaired.fastq.gz`, `read2.paired.fastq.gz` and
`read2.unpaired.fastq.gz`. The full original trim log and independent streaming
audit check input ordering, mate slots, every retained/orphan/drop disposition,
trim coordinates and encoding conversion. Actual upstream summary counts must
agree. Missing/extra/changed reads fail. Exit 2 retains partials under `partial/`
and publishes no successful `results.tsv`; correct the named cause and rerun
to a new destination. Do not treat orphan output as paired data.

## Evidence, limitations and licensing

All nine primary Trimmomatic contexts were individually read; P124's separate
ATAC paragraph is a supporting pointer. Paper-specific versions, unspecified
parameters and the P226 TPM/DESeq2 wording remain explicit in the catalog.
Trimming does not implement Galaxy, FLASH, Salmon/StringTie quantification,
blacklist filtering, peak calling, BAMscale/deepTools coverage or differential
expression. Versions 0.32 and 0.36 remain unvalidated.

Native tests compare 2,118 cases against **each** original release: 4,236 frozen
outputs covering all thirteen operations, ordered pipelines, both Phred offsets,
mate-specific cropping, `N`/`n`, boundary lengths and MAXINFO. A standalone
reflection writer invokes actual upstream step objects; it does not calculate
expected values with JLinAlg. Additional tests exercise original single/paired
programs, simple/palindrome clipping with keep-both choices, read fates, exact
headers/qualities, all-filtered warnings, invalid contracts and Java21 recovery.
Sources and reference hashes are in `src/test/resources/trimmomatic`.

To regenerate the frozen references independently, supply a permitted Java25+
runtime and a JDK compiler (`javac` on PATH by default):

```powershell
python tools/literature/reproduce_trimmomatic_references.py --out build/literature-review/my-new-trimming-references
```

The writer compiles with Java17 bytecode, loads each actual original JAR in an
isolated class loader, invokes its step objects and checks all 4,236 outputs
against frozen hashes. It never replaces checked-in references automatically.

The native algorithms are adapted from the [official Trimmomatic sources](https://github.com/usadellab/Trimmomatic)
under GPL-3.0-only with their copyright attribution and full license in
`META-INF/NOTICE-Trimmomatic.txt` and `LICENSE-Trimmomatic.txt`. Original
[0.39](https://github.com/usadellab/Trimmomatic/releases/tag/v0.39) and
[0.41](https://github.com/usadellab/Trimmomatic/releases/tag/v0.41) archives/JARs
are hash-pinned by the installer. Their separate adapter exception is retained.
No stock adapter sequence or external JRE/application binary is redistributed
inside the JLinAlg JAR.
