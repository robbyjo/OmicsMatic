# Native MALT RMA reassignment

This development command reads a complete classified MALT 0.4.1 RMA6 file through the original upstream reader, then recomputes taxonomic assignments in Java. It connects the native taxonomy, active-hit/LCA and minimum-support components. Its supported profile is the original default **serial, unpaired, naive LCA, readCount** processor. Alignment, paired reads, weighted/long-read LCA, rank-identity, contaminant and reference-coverage modes still need separate implementation and validation. This component adds zero whole-tool credit to the 250-paper catalog.

## Prerequisites and build

Use Python 3.10+ and JDK 21 or 25. The Java source targets Java 17 bytecode; runtime qualification here covers Windows Java 21/25 and Linux Java 21. Run the following PowerShell commands from the repository root after checking out the commit containing this command. Each worktree, runtime and analysis directory must be new.

Build in a separate worktree to preserve an existing executable and unrelated local work:

```powershell
git worktree add --detach build/malt-rma-development HEAD
Push-Location build/malt-rma-development
try {
    .\gradlew.bat executableJar --no-daemon --no-parallel
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; retain its diagnostics.' }
} finally {
    Pop-Location
}
$jar = Join-Path (Get-Location) 'build/malt-rma-development/build/cli/omicsmatic-0.3.7.jar'
java -Xmx2g -cp $jar org.jlinalg.cli.MaltReassignCli --help
```

This is a standalone class entry point in the development JAR. The central `java -jar ...` launcher does not route this command. Version 0.3.7 in the development artifact's filename does not make it a new published release.

Install the exact original reader provider into a permitted directory:

```powershell
python tools/literature/setup_malt_rma_runtime.py --out build/malt-rma-runtime-1
if ($LASTEXITCODE -ne 0) { throw 'Runtime unavailable or invalid; dependent analysis must be skipped. Read the installation diagnostics.' }
```

The installer downloads [Bioconda's original malt-0.41-1 package](https://conda.anaconda.org/bioconda/noarch/malt-0.41-1.tar.bz2), verifies the complete package and extracts only `MALT.jar` and `data.jar`. It executes no package scripts and installs no alignment launchers or legacy JVM. The package declares OpenJDK >=8.0.144,<9 for the whole original program; this project separately qualifies reader interoperability on the modern JVMs above. The upstream MALT code is [GPLv3](https://github.com/husonlab/malt).

For an offline installation, use the complete package downloaded in an allowed environment:

```powershell
python tools/literature/setup_malt_rma_runtime.py --archive C:/downloads/malt-0.41-1.tar.bz2 --out build/malt-rma-runtime-offline-1
```

Required complete-byte identities:

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| malt-0.41-1.tar.bz2 | 30074910 | `9757ceb54dbf9b193e05b0c43d5faa19b9457ab070f1e568120ce5f695debc51` |
| MALT.jar | 4641119 | `9106de3134a8f9d96bcd35986ee7a61738a1b51fca1683bb32bb108e82e92cf4` |
| data.jar | 24731618 | `718a23ad8defbe34a35c9b0967c5fa616900adba34e8b961fb5531cd2382d2e3` |

## Run an analysis

Replace the RMA path with your complete, classified input. Do not create the output directory first.

```powershell
java -Xmx2g -cp $jar org.jlinalg.cli.MaltReassignCli `
    --rma C:/data/sample.rma6 `
    --runtime build/malt-rma-runtime-1 `
    --out build/malt-reassignment-1
if ($LASTEXITCODE -ne 0) { throw 'Analysis did not complete; inspect WARNINGS_FAILURE.md or the console diagnostic.' }
```

Defaults are minimum score 50, top percent 1, maximum expected value 1, minimum identity 0, LCA coverage 80 and minimum-support percent 0.01. `--minimum-support 0` selects the source base minimum of one with percentage selection. A positive `--minimum-support` overrides percentage selection, even if both options are supplied; the report records `MINIMUM_SUPPORT_OVERRIDE`. Example:

```powershell
java -Xmx2g -cp $jar org.jlinalg.cli.MaltReassignCli `
    --rma C:/data/sample.rma6 --runtime build/malt-rma-runtime-1 `
    --out build/malt-reassignment-support50-1 --minimum-support 50
```

Optional `--disabled-taxonomy-ids FILE` accepts one literal integer per line, with no header, blank lines or duplicate IDs. `--help` lists the complete-input byte/read/match/text/taxonomy limits. If a limit is too small, the analysis fails; increase that explicit limit and retry a new directory after checking resources. It does not silently analyze a prefix.

A completed directory contains `assignments.tsv`, `reassignment.json`, `WARNINGS.md` and `COMPONENT_COMPLETED.json`. The completion receipt pins the assignment table and report. Each assignment row retains its original RMA UID, Base64 UTF-8 read name, raw source weight, stored/initial/final taxon, stored/available match counts and active-match indices. UIDs are identifiers within the pinned input archive. The original RMA is never rewritten.

**Read WARNINGS.md before interpreting any output.** Exit 0 establishes completion of this component profile; it establishes no biological result or completeness of the upstream FASTQ population. Only reads stored in the supplied RMA are processed. The two validation archives contain all 172 stored reads and 175 matches; they do not contain every query from the original FASTQ inputs.

## Warnings, errors and recovery

| Status | Meaning | What to do |
| --- | --- | --- |
| `SKIP3`, exit 3 | Required provider or permitted installation is unavailable. | Read the console and `WARNINGS_FAILURE.md`; install the exact provider where allowed or transfer the complete pinned package for offline installation. If unavailable or forbidden, skip this dependent analysis. |
| `FAIL2`, exit 2 | Invalid arguments, corrupted input, capacity failure or another failed check. No result is admitted. | Retain original inputs and the entire failed directory. Read `FAILURE.json`, `WARNINGS_FAILURE.md` and the console; resolve the named cause and retry a new directory. |
| Existing output directory | The command refuses to overwrite it. | Keep that directory and select a new `--out`. The console carries the diagnostic because an unowned directory is not modified. |
| `UNAVAILABLE_MATCH_BLOCKS` | The original reader exposed fewer matches than the archive stores under the explicit getter profile. | Keep both counts and original diagnostics. Do not claim complete raw-match access; evaluate whether the source getter's omissions are appropriate for the intended analysis. |
| `NONPOSITIVE_READ_WEIGHTS` or `EMPTY_STORED_RMA` | Raw archive metadata requires interpretation. | Resolve the source meaning before scientific admission; neither condition proves biological absence. |

Installation uses `INSTALLATION.json` and `WARNINGS.md` on success. An owned failed installation writes `FAILURE.json` and `WARNINGS_FAILURE.md`; a pre-existing output is untouched and the diagnostic is printed. If directory creation itself is forbidden, retain the console diagnostic. A partial table without a valid completion receipt is not an admitted result.

## Source behavior and verification

The source context is the May 2018 [MALT RMA6 writer](https://github.com/husonlab/malt/blob/5984e0672000604e8ef0b5ebb2a41f0ad00fb67d/src/malt/io/RMA6Writer.java) and [MEGAN serial DataProcessor](https://github.com/husonlab/megan-ce/blob/24e70bf6cd0a434ff40a62b98cf0b54d72954908/src/megan/algorithms/DataProcessor.java). Saved source and actual original JAR bytecode are both retained; the historical source commit is context, not proof that a nightly binary was built from that exact commit.

The original writer chooses readCount. Each stored read therefore contributes one to assignment/support accounting, including reads without an assignment in the support-percentage denominator. Raw header weight remains metadata. The serial rule is `(int) max(1, (percent / 100.0) * totalWeight)` for a positive percentage; its narrowing conversion truncates and saturates. The experimental parallel processor uses a different denominator and rounding rule and is outside this profile. The raw Java helper preserves source nonfinite behavior; the command requires finite numeric options.

The assignment reader uses the original serial getter profile: minimum score 0, maximum expected value 10, no sequence, with matches. Native assignment filtering then applies the selected thresholds. The separate raw-reader API uses 0/Float.MAX_VALUE/sequence and preserves all exported read/match/classification fields. Callbacks remain provisional until `read()` returns its complete, integrity-checked report.

Taxonomy comes from the pinned provider's bundled `resources/files/ncbi.tre`, with all 1,703,609 nodes including three sentinels. The differently versioned alignment-database taxonomy is not interchangeable. The [earlier failed assignment audit](MALT_ASSIGNMENT_AUDIT.md) and failed reader binding/line-ending controls remain retained; corrections have separate evidence.

Validation covers two original RMA archives, complete raw fields, 12 reader refusals per configuration, and 32 actual original `Document.processReadHits()` profiles with 2,752 final read assignments per configuration. The command has five source-matched profiles and four refusal checks per configuration. Source builds use JDK 21/25 targeting 17, run on Windows Java 21/25 and Linux Java 21. Four project JUnit tests also pass with the exact provider installed. A separate packaged-JAR check covers the three qualified JVMs.

The public [validation record](malt-rma-reassignment-development/VALIDATION.json) and [compressed evidence](malt-rma-reassignment-development/controls.zip.xz) contain complete controls, original receipts, failed attempts and source pins. They contain no upstream JARs, private workflow image or protected GATK program. Replay checks saved original references; it does not rerun the private image or claim independent biological validation.

```powershell
python tools/literature/verify_malt_rma_reassignment.py `
    --runtime build/malt-rma-runtime-1 `
    --out build/literature-review/nfcore-runtime/malt-rma-saved-replay-1
```

Alternatively, `--install-runtime` allows the replay to obtain its own provider. The complete cross-runtime replay also requires local JDK 21/25 and WSL Linux Java 21; override `--java21-bin`, `--java25-bin` and `--linux-distro` where needed. Missing prerequisites produce prominent SKIP3 diagnostics and skip the dependent replay. Set `OMICSMATIC_MALT_RMA_RUNTIME` to the provider directory when running the four project tests; three runtime-dependent tests skip explicitly without it.
