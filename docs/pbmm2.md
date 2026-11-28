# Original pbmm2 prepared-input mapping

`pbmm2-tools` runs an unchanged, pinned Linux x86_64 Bioconda pbmm2 1.13.1 or 1.17.0 binary for an explicit `INDEX` or `ALIGN` request. Native Java recomputes the original CIGAR base/event arithmetic, edit distance and available identity/concordance float tags across every produced BAM record. Public API `org.jlinalg.sequence.Pbmm2CigarMetrics` also exposes that arithmetic independently.

**Version 1.17.0 retains three original legacy test failures.** Both native and isolated container execution passed 106/109 unmodified public functional cases; `fastxinput_22/23/24` expected seven mapped reads and 11,994 bases, while the original binary produced nine reads and 14,102 bases from the same complete input. The [official changelog](https://github.com/PacificBiosciences/pbmm2/blob/v1.17.0/README.md#full-changelog) documents changed HiFi DP filtering and the discontinuation of corresponding public source updates. This is an observed original binary profile, without complete binary/source equivalence. Version 1.13.1 passed 109/109 public functional cases. Both editions used explicitly selected public Cram 0.7; the inaccessible private original runner and C++ unit suite remain unqualified.

## Prerequisites and installation

Use Linux Java 17 or later on x86_64, including Linux Java inside WSL. Windows Java can verify a complete saved bundle. Installation is a separate explicit operation; an analysis never downloads software, changes an existing environment or substitutes another edition.

The source, binary, dependency-version and image pins are in [PROFILES.json](../src/main/resources/org/jlinalg/omics/pbmm2/PROFILES.json) and [IMAGES.json](../src/main/resources/org/jlinalg/omics/pbmm2/IMAGES.json). The selected binary packages are:

| Edition | Original Linux x86_64 package | Archive SHA-256 |
|---|---|---|
| 1.13.1 | `pbmm2-1.13.1-h9ee0642_0.tar.bz2` | `3f2b39a6dd5fd9bbd198db8e0c1a630e02686d6413ff2a58cd8555e006ede21d` |
| 1.17.0 | `pbmm2-1.17.0-h9ee0642_0.tar.bz2` | `66f42214c9a77b109a68df18ccc5b3edb19a9a355f5549cc0d584b440e59e782` |

Check available tools and environment policy before installation. Where permitted, obtain the exact archive from the [official Bioconda distribution](https://anaconda.org/bioconda/pbmm2/files), verify its complete size and SHA-256 against the profile, and use a new private prefix with an existing permitted Conda manager:

```sh
"$CONDA" create --offline --no-default-packages --yes \
  --prefix /private/new-pbmm2-1.17.0 \
  /private/verified/pbmm2-1.17.0-h9ee0642_0.tar.bz2
```

Select the archive/prefix matching the requested edition. Both selected packages declare no Conda dependencies; their dynamic OS dependencies still require the qualified image below. Retain installation logs, package/license metadata and exact archives privately. The [pbbioconda notice](https://github.com/PacificBiosciences/pbbioconda) describes these as research binaries; identity with SMRT Link and clinical validation are not established. BSD-3-Clause-Clear notices for pbmm2 and the pbcopper arithmetic reference are included with the profile. Floating dependency wraps and unavailable matching newer dependency sources prevent a complete source closure for either packaged edition.

Prepare the exact private Apptainer 1.5.5-1 payload using the existing [container setup guide](literature/NFCORE_CHIPSEQ200.md). Its complete 350-entry inventory is checked before execution. Supply the one image identified by `IMAGES.json`: `https://depot.galaxyproject.org/singularity/samtools:1.15.1--h1170115_0`, 22,114,304 bytes, SHA-256 `8934a4bfde1c80b16ea7b679b97816e77c34fe89fc0cbc7fe33054379378557b`. A complete matching image already installed for another workflow may be reused without downloading it again. Obtain it separately where downloads are permitted and verify the complete file. It provides only the OS/libc carrier; the selected original pbmm2 executable performs the analysis. This adapter never executes the image's SAMtools as a scientific substitute. Unprivileged namespace capability must already be permitted by the environment.

## Declare complete inputs and the operation

Start from [the explicit request example](../examples/pbmm2/request.json). Its CCS preset and capacities are illustrative, not inferred paper parameters or experiment recommendations. Supply the correct whole reference and read population for the intended analysis. Reference labels or file names do not establish assembly identity.

Every resource is a complete directory root with a metadata `id` and its canonical original absolute Linux `path`. Declare all recursively referenced roots, including FOFN members, dataset XML external resources and datastore JSON targets. Roots must be disjoint from one another, the new session and the runtimes/image. The wrapper copies every regular file and empty directory into immutable `inputs/` and a separate `staged/` working copy. It does not select a smaller chromosome/read set to fit capacity. Symlinks are outside this initial snapshot profile and cause an explicit skip. Colon/comma paths unrepresentable by the selected container bind syntax are skipped without renaming. Literal whitespace, Unicode and scientific identifiers remain in their original arguments and file contents.

Inside the container, working copies are bound at the original absolute root paths. Relative and absolute native resource locators remain unchanged. Host home, cwd, hostfs, admin binds and automatic host `/tmp` exposure are disabled; an owned private temporary directory is explicitly mounted at `/tmp` because the original sorter requires that literal path. Choose `working_directory` inside a declared root, or `OUTPUT`. Original working-copy files must remain unchanged; source-generated indexes are retained. Uncaptured external locators are not silently materialized.

For `ALIGN`, explicitly select a source preset (`SUBREAD`, `CCS`, `ISOSEQ`, `UNROLLED`), complete `reference` and `reads`, and output kind `BAM`, `ALIGNMENTSET`, `CONSENSUSALIGNMENTSET`, `TRANSCRIPTALIGNMENTSET` or `DATASTORE`. For `INDEX`, use `reads: null`, `output_kind: "MMI"` and an explicit preset. The `parameters` object accepts the exact edition's source-defined options and types; aliases cannot duplicate each other. Preset, thread count and owned mapping report paths have dedicated bindings. Numeric argument spellings and source IDs are preserved. An unsupported original option skips the analysis rather than being ignored. Public 1.17.0 adds the explicitly documented binary `include-fail-reads` flag, without claiming its complete public implementation source.

Declare capacities for the entire capture, output record population and owned process tree. Address space, CPU, file-size and open-file limits are enforced per process; aggregate RSS, process/thread counts, wall time and workspace limits are observed. These are not reservations or hard aggregate memory/disk quotas. Provision the front-end Java heap and host resources for the whole workload.

```sh
java -Xmx3g -jar "$OMICS_JAR" pbmm2-tools \
  --request request.json --out /opt/new_pbmm2_session

java -Xmx3g -jar "$OMICS_JAR" pbmm2-tools --verify /opt/new_pbmm2_session

java -Xmx3g -jar "$OMICS_JAR" pbmm2-tools \
  --replay /opt/new_pbmm2_session --out /opt/new_pbmm2_replay
```

A replay verifies the saved bundle and copies its whole immutable inputs, using the same original logical locators and request. It needs the declared exact runtime/container prerequisites, but does not recapture subsequently changed original input roots. Every execution uses a new output path; live or indeterminate jobs are never restarted. Complete producing JAR, copied original executable/image, input/staging snapshots, commands, process ownership/exit diagnostics, all outputs and failures remain available. Completion is written atomically only after full saved-file verification and native contract recomputation.

## Warnings and required action

**SKIP3 / exit 3: dependent analysis skipped.** An exact permitted prerequisite/capability or representable input form is missing. Read the named cause in stderr and `FAILURE.json`; install or restore only the named permitted prerequisite separately and use a new session, or retain the skip. Do not substitute another edition or change host namespace/security policy implicitly. Cell Ranger remains excluded.

**FAIL2 / exit 2: no result admitted.** Preserve the attempt and its original stdout/stderr, `failure.stacktrace`, `FAILURE.json` and `WARNINGS.md`. Correct the named input, integrity, runtime, numerical or capacity cause; use a new output path. Require `COMPLETED.json` and successful `--verify` before downstream use. A process exit of zero alone does not establish completion.

The BAM audit checks all mapped CIGARs, literal provider versions, signed integer edit distances, available identity/concordance float bits, reference-header geometry and read/CIGAR lengths. Unmapped and empty outputs are explicit. `M` counts as a match under the original arithmetic convention; this does not establish nucleotide identity against the reference. All nonmetric tags, original mapping reports and native XML/JSON/index sidecars remain fully captured, with no general independent correctness claim. An index audit checks the original process and MMI artifact signature; it does not independently reproduce minimizer construction. Saved verification reruns these named contracts, without rerunning alignment, re-admitting a host, certifying methylation/variant calls or reproducing an unresolved author recipe. Hashes establish integrity, not authenticity. Keep private upstream binaries, raw outputs and scientific inputs out of public Git history.

## Literature scope

The frozen 250-paper cohort names pbmm2 in four papers: P171 (wheat redundant isoform mapping, 1.17.0, before separate IsoSeq collapse), P214 (human long-read mapping and a separate methylation-tagged read branch, 1.17.0), P232 (JAG1 target mapping, edition and exact target/preset unresolved), and P248 (1.13.1 in the PBSV branch, separate from minimap2-based callers). See the [primary-source roles](literature/pbmm2-source-audit/ROLES.json) and [qualification](literature/pbmm2-validation/QUALIFICATION.json). The separate catalog admission records any earned family and source-review credit. A tested tool adapter does not reproduce these papers or resolve missing author parameters.
