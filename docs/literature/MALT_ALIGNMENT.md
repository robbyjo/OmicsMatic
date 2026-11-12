# Original MALT BlastN alignment adapter

This development adapter runs the unchanged **MALT 0.4.1 BlastN** program with its exact Linux x86_64 Java 8 provider. The controlling application uses Java 17+ and captures complete FASTQ/index inputs, owns the upstream process, checks every SAM alignment against the input/reference geometry, checks original per-file completion counters, and recomputes naive/readCount taxonomic assignments in Java. It supports eager 2.4.4 software-control parameters and original standalone defaults as separate profiles.

**The full MALT package, historical paper analysis and nf-core/eager family remain incomplete.** This adapter does not implement BlastP/BlastX, database building, paired/weighted/contaminant/functional assignment, HOPS authenticity or the authors' historical 2017 database. A three-sequence test index is a software control. It cannot substitute for the complete historical database required by the paper. No biological result or extra catalog-tool credit follows from these software checks.


**Known original Local-mode discrepancy:** the source can emit a hard-clip record that leaves input bases unaccounted for. The complete Local control has 147 alignments and four affected records, with differences of 1, 1, 1 and 4 bases. The native audit retains the original SAM/RMA, identifies the first affected read and CIGAR, and returns FAIL2 without a completion receipt. Resolve the discrepancy with an independently validated provider/method before downstream interpretation; retain and declare any changed method. Do not silently edit clips or trim the original input.

Effective gap defaults depend on the selected mode. For BlastN both profiles use 7/3. Running the old program's help without `--mode BlastN` displays 11/1 for its protein-mode context; those values are not standalone BlastN defaults.

## Install only where permitted

Alignment requires Linux x86_64, `/usr/bin/setsid` and `/usr/bin/prlimit` from util-linux, plus the exact MALT/Java8 provider. Linux in WSL is supported for the original program. Java 21/25 can read RMA files and verify saved bundles, but the unmodified MALT alignment entry point fails on those JVMs because `javax.xml.bind.JAXBException` is unavailable. No compatibility shim is substituted.

Run setup in a Linux shell from the repository root:

```bash
python3 tools/literature/setup_malt_alignment_runtime.py \
  --out /var/tmp/my-malt041-runtime-1
```

This downloads the exact public [MALT package](https://conda.anaconda.org/bioconda/noarch/malt-0.41-1.tar.bz2) and [OpenJDK8 package](https://conda.anaconda.org/conda-forge/linux-64/openjdk-8.0.144-zulu8.23.0.3_2.tar.bz2), checks complete-byte SHA-256 values, and extracts only the two MALT jars and complete JRE. It executes no package installation scripts and changes no system Java, Conda environment or released executable. Keep the bundled JRE `LICENSE` and `THIRD_PARTY_README` with the provider.

| Package | Bytes | SHA-256 |
| --- | ---: | --- |
| malt-0.41-1.tar.bz2 | 30074910 | `9757ceb54dbf9b193e05b0c43d5faa19b9457ab070f1e568120ce5f695debc51` |
| openjdk-8.0.144-zulu8.23.0.3_2.tar.bz2 | 72731991 | `fc842165b76a859c2a4800eb2945279fee55ec85af91fdde326f823c12fecdf5` |

For offline installation, copy both complete packages from an allowed environment and add `--malt-archive /path/malt-0.41-1.tar.bz2 --java-archive /path/openjdk-8.0.144-zulu8.23.0.3_2.tar.bz2`. The installer requires a new output directory and canonical path. It preserves the original two internal JRE symlinks and verifies version/help; **READY establishes prerequisites, not alignment qualification**. The original MALT help command exits zero.

**If download, installation or execution is unavailable or forbidden, skip the dependent alignment.** Setup prints `SKIP3` and required recovery steps. An owned failed directory contains `FAILURE.json` and `WARNINGS_FAILURE.md`; a pre-existing or inaccessible directory remains untouched and the console carries the diagnostic. Missing/corrupt packages must not produce substitute scientific results.

## Build the small standalone component

The minimal builder needs a complete JDK 21 or 25 and the exact HTSJDK 5.0.0 library. It compiles the controller/auditor source closure and creates a small executable beside its 2 MB dependency. It needs no private development JAR or workflow image.

```powershell
python tools/literature/build_malt_alignment.py --download-htsjdk --java-bin "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin" --out build/malt-alignment-small-1
java -Xmx3g -jar build/malt-alignment-small-1/malt041-alignment-development.jar --help
```

For offline builds, replace `--download-htsjdk` with `--htsjdk-jar PATH_TO_EXACT_HTSJDK_5.0.0.jar`. Keep the adjacent library with the executable. `BUILD.json` records every source and artifact hash. Missing compilers/dependencies produce SKIP3; corrupt dependency bytes fail. No analysis occurs during a build. The same builder runs in Linux with its complete JDK bin path.

Use `java -jar .../malt041-alignment-development.jar` for alignment/verification, with the same options below. The full project build remains available if other toolbox commands are needed.

## Build a development executable

After checking out the checkpoint containing `MaltAlignCli`, use an isolated worktree to preserve local edits and an existing executable. From the repository root in PowerShell:

```powershell
git worktree add --detach build/malt-alignment-development HEAD
Push-Location build/malt-alignment-development
try {
    .\gradlew.bat executableJar --no-daemon --no-parallel
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; retain its diagnostics.' }
} finally {
    Pop-Location
}
```

The new JAR is `build/malt-alignment-development/build/cli/omicsmatic-0.3.7.jar`. It has the development source for this command; the version in its filename does not make it a new release. Use the standalone class entry point below. The central `java -jar ...` launcher does not route this command.

## Run complete FASTQ alignment

In Linux, set the JAR path to the separate build above. For a build created at `D:/projects/JLinAlg`, WSL sees it beneath `/mnt/d/projects/JLinAlg`.

```bash
malt_jar=/absolute/path/to/build/cli/omicsmatic-0.3.7.jar
java -Xmx3g -cp "$malt_jar" org.jlinalg.cli.MaltAlignCli --help

java -Xmx3g -cp "$malt_jar" org.jlinalg.cli.MaltAlignCli \
  --runtime /var/tmp/my-malt041-runtime-1 \
  --query /absolute/path/sample.fastq.gz \
  --database /absolute/path/complete-malt-index \
  --profile EAGER244 \
  --out /absolute/path/new-malt-analysis-1
```

Use a complete plain or gzip four-line FASTQ with unique literal read names. The adapter preserves original input bytes in the bundle; staging the file as `inputs/reads.fastq[.gz]` does not rename read identifiers. Wrapped FASTQ, ambiguous duplicate identities and other input formats are not silently repaired. The taxonomy-only index needs `ref.db`, `ref.idx`, `ref.inf`, `taxonomy.idx`, `taxonomy.map`, `taxonomy.tre`, plus every complete contiguous `indexN.idx`/`tableN.idx`/`tableN.db` seed-table group starting at zero. Unexpected functional or other index members cause an explicit capability skip; missing seed-table parts fail. The index is supplied by the user and is not manufactured from a different taxonomy.

Choose the profile explicitly:

| Option | EAGER244 | STANDALONE |
| --- | --- | --- |
| Alignment | SemiGlobal | Local |
| Minimum score / maximum expected value | 50 / 1 | 50 / 1 |
| Minimum alignment identity | 85 | 0 |
| Maximum alignments per query | 100 | 25 |
| Top percent | 1 | 10 |
| Minimum-support percent | 0.01 | 0.001 |
| Gap open / extend | 7 / 3 | 7 / 3 |

Both profiles use original naive LCA coverage 80, readCount, unpaired assignment, two workers and memory mode `load` by default. A positive `--minimum-support` overrides percentage selection in original MALT. The native comparison applies the same rule. Support percentages use all stored RMA reads with the original serial truncation rule; the denominator changes when unaligned queries are stored.

`--help` lists explicit alignment/strand/memory options and complete-input/process capacities. `--strand FORWARD` or `REVERSE` restricts alignment; `BOTH` is the default. `--include-unaligned true` stores no-hit queries in RMA. It does not add unmapped rows to MALT's alignment-only SAM. Original DNA5 normalization maps lower case to upper, U to T and ambiguity letters to N before alignment. Native checks apply this source projection to expected query bytes and retain the original FASTQ unchanged.

The original completion counters must match the complete native FASTQ/SAM populations. In particular, the old source parser can stop early on CRLF input; an upstream exit zero does not admit an incomplete prefix. Retain that failed run. If a line-ending transformation is needed, create and declare a separate LF input derivative, retaining the original and its provenance.

## Results and required action after failures

Read `WARNINGS.md` and the complete `native/process.stderr` before interpreting output. On success, the bundle contains captured input/index files, `native/reads.rma6`, `native/reads.blastn.sam.gz`, process ownership/resource diagnostics, `audit.json`, and `COMPONENT_COMPLETED.json` with the complete file inventory. The source SAM's MAPQ255 remains a token; it is not genomic mapping confidence. Raw RMA read counts may be smaller than all input queries. No-hit/empty output cannot establish biological absence.

Exit 3 means a prerequisite or supported capability is unavailable and the dependent analysis was skipped. Supply the exact permitted provider, util-linux tools or supported complete input/index; otherwise skip the analysis. Exit 2 means a failed integrity, format, process, capacity or scientific consistency check and admits no result. Retain the entire directory, inspect `FAILURE.json`, `WARNINGS_FAILURE.md` and `native/process.*`, resolve the named cause and retry a new output directory. Existing output directories are refused without modification. A partial SAM/RMA without a valid completion receipt is not admitted.

The shared Linux supervisor enforces per-process address-space, CPU, file and open-file bounds and checks wall time, workspace, process/thread counts and sampled aggregate RSS. Its recorded polling interval is 1000ms, including bounded waiting for a coherent exiting thread group. Missing live metrics are not imputed. Sampled RSS is not a hard aggregate RSS bound. Historical diagnostics retain the shared supervisor's internal Nextflow labels.

## Verify a saved bundle

Saved verification is read-only and opens captured files, not the original producer paths. It recomputes complete input/SAM geometry, original completion counters and naive assignments. It needs the exact two upstream reader jars, obtainable through the [RMA runtime installer](MALT_RMA_REASSIGNMENT.md); it needs no Java8 process or private image. It does not refit alignments or authenticate a producer or biological result.

```bash
java -Xmx3g -cp "$malt_jar" org.jlinalg.cli.MaltAlignCli \
  --verify /absolute/path/completed-malt-analysis-1 \
  --runtime /absolute/path/original-reader-jar-directory
```

The same class works for saved verification on Windows Java 21/25, using Windows paths and the two reader jars. To export a full assignment table, use `MaltReassignCli` from the [RMA guide](MALT_RMA_REASSIGNMENT.md), explicitly matching the recorded score, expected value, top-percent and support parameters. Its defaults match the eager control profile; select standalone values explicitly for a standalone run.

## Replay the public software controls

The [validation record](malt-alignment-development/VALIDATION.json) pins the source and [complete control archive](malt-alignment-development/controls.zip.xz). Its content-addressed objects preserve complete captured inputs and outputs; the case and evidence indexes map each original relative filename to its full byte count and SHA-256. Complete case bundles are reconstructed beneath the replay output. The archive contains no original runtime jars, JRE, workflow image or protected GATK program.

From Windows PowerShell with JDK 21, JDK 25 and the named WSL distribution available:

```powershell
python tools/literature/verify_malt_alignment.py `
  --reader-runtime D:/permitted/malt041-reader `
  --download-htsjdk `
  --out build/literature-review/nfcore-runtime/my-malt-alignment-replay-1
```

Use the [public reader installer](MALT_RMA_REASSIGNMENT.md) to obtain the two exact reader jars; use `--htsjdk-jar PATH` for an offline build. Supply `--java21-bin`, `--java25-bin` and `--linux-distro` when their locations differ from the documented defaults. The replay builds the minimal component twice and checks nine complete saved profiles plus three retained original-source refusals on Windows Java 21/25 and Linux Java 21. It performs 54 admitted saved-profile checks and 18 expected refusal checks. It does not refit the alignments or rerun the original workflow image. Missing prerequisites skip the dependent replay with SKIP3 and required actions; corrupt supplied dependencies fail with FAIL2. Read `WARNINGS.md` and `COMPLETED.json` in the new output directory.

The original execution matrix separately compared all SAM rows and full raw-RMA fields for 16 admitted fits against original MALT runs. Six source-defect fits were refused; an additional two packaged fits checked the all-stored-read support denominator. The support control has six stored reads, five aligned reads and 59% support: the original and native processors both select three, rather than two from an assigned-only denominator or four from rounding. Synthetic controls establish software behavior and do not add biological samples.

The separate custom-screening role in [P002](MALT_HOPS_PAPER_ROLES.md) requires MALT 0.5.2 and MaltExtract; this 0.4.1 component does not satisfy that role.

The implementation remains in development. Original baseline alignments, complete raw-RMA comparisons, installer refusals, native project tests and broader input/option controls are retained separately with their exact source versions. Failed compiler, help-exit, process-shutdown and container-binding attempts remain retained. These software controls do not resolve the distinct paper roles or convert the historical database gap into a biological analysis.
