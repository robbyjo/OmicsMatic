# nf-core development foundation

**Status: development foundation. The catalog still records nf-core as missing.**
The validated catalog contains 99 new scoped integrations, including 19 native
Java implementations and 80 upstream adapters; 102 integrations include the three
pre-existing adapters. All 250 papers are collected. Method review and tool
implementation continue.

This checkpoint provides a separate Java engine for workflows whose original
configuration files define methods used by included configurations. Each
declared configuration is passed as a primary member of Nextflow's `-C` list,
in its declared order. The generated final config adds trace fields. The
original method bodies, workflow source, scientific parameters and tool
versions are preserved.

`org.jlinalg.workflow.NfcoreInputBindings` plans literal Linux paths for a single
declared common input root. Its contract was checked against the existing
production capture implementation, including Unicode, spaces and apostrophes.
It performs path planning. The execution adapter separately captures and checks
the actual files and rejects symlink escapes or incomplete declarations.

## Try the standalone development artifact

From the repository root, on Windows Java 21/25 or Linux Java 21:

```text
java -jar docs/literature/nfcore-engine-development/nfcore-engine-javac21.jar --help
java -jar docs/literature/nfcore-engine-development/nfcore-engine-javac21.jar --runtime-profiles
```

This artifact takes the displayed options directly. The central `omicsmatic`
command has no `nfcore-engine-tools` route in this checkpoint. The canonical
released JAR remains unchanged.

Export the existing exact Nextflow setup kit to a new directory:

```text
java -jar docs/literature/nfcore-engine-development/nfcore-engine-javac21.jar --export-runtime-setup NEW_SETUP_DIRECTORY
```

Read its `README.txt`. Setup supports exact engines 21.04.1, 22.10.6 and 24.04.4,
uses explicit installation/download flags, and runs a separate synthetic health
check. An existing complete runtime can be rechecked using a new report. A host
or kernel change requires that health recheck; preserve the old report.

Execution requires Linux Java, including Linux Java in WSL on Windows. With a
fully declared local recipe and a completed setup report for that host:

```text
java -jar nfcore-engine-javac21.jar --recipe /absolute/recipe.json --home /absolute/exact-runtime --setup-report /absolute/completed-report --out /absolute/new-session
java -jar nfcore-engine-javac21.jar --resume /absolute/session --home /absolute/exact-runtime --setup-report /absolute/completed-report
java -jar nfcore-engine-javac21.jar --verify /absolute/session-or-saved-attempt
```

Use the typed recipe schema in [the Nextflow guide](NEXTFLOW_DEVELOPMENT.md).
Declare every original config explicitly, including `nextflow.config` when
present. Choose each profile explicitly. Config paths containing a comma cannot
be represented by the original `-C` interface and produce SKIP3; use new
comma-free staging/session paths while retaining scientific identifiers.

CSV or TSV files containing filenames require complete path binding before
execution. Declare one common root containing the workflow, original and
derived samplesheets, reads, references and runtime-only config as resource
`bundle`. For example, if the root is `/producer/materialized` and the session
is `/owned/session`, its captured location is
`/owned/session/workspace/resources/r0/materialized`. Only path columns in a
separately retained derived samplesheet should point into that capture. Preserve
original sample identifiers, rows, non-path metadata and original file bytes.
`NfcoreInputBindings.plan` checks the producer/capture relation; the complete
common-root declaration is required for that relation to hold.

## Evidence and remaining work

The [checkpoint proof](nfcore-engine-development/README.md) records 15 controls
across three original engines, independent Java 21/25 builds, cached resumes,
portable verification and 12 saved-result integrity controls. The old nested
config path fails with the original `check_max` binding error in a retained
baseline. Original helpers and predecessor scientific classes/resources remain exact.

The 250-source census found six nf-core papers and 11 matching methods
paragraphs. Six role drafts retain the separately reported versions:

| Paper | Pipeline | Pipeline version | Nextflow version reported |
| --- | --- | --- | --- |
| P002 | eager | 2.4.4 | 21.04.1 |
| P027 | rnasplice | 1.0.4 | 24.04.4 |
| P032 | chipseq | 2.0.0 | Unreported |
| P050 | chipseq | Unreported | Unreported |
| P169 | chipseq | 1.2.1 | Unreported |
| P228 | hic | 2.1.0 | Unreported |

The source lock records five exact pipeline tags, commits, archive hashes and
769 source-file hashes with the original MIT notices. A selected release tag
does not establish the source commit used by an author. An unreported engine or
pipeline edition remains unreported.

Complete source/runtime/data preparation, full pipeline-family controls,
scientific output contracts and catalog admission remain required. The generic
engine and path planner receive no additional tool-level implementation credit.
The original six-sample ChIP-seq 1.2.1 fixture produced two insufficient-depth
Preseq failures that upstream ignored; the project retained and rejected that
attempt. A separate control retains all original reads and adds explicitly
labeled simulated PCR duplicates. Its results concern synthetic workflow
behavior; these fixtures provide no independent biological validation or paper
reproduction claim. Full local work directories and every failed attempt are
retained.

The additional control completed all 97 original tasks, including all six
Preseq tasks. Its first helper failed a guessed MultiQC path check after
execution. A separate read-only audit confirmed the source-declared
`multiqc/broadPeak/multiqc_report.html`, inventoried outputs and rechecked
unchanged source, image and inputs. The earlier failed path-check receipt is
preserved. This execution intake adds no nf-core implementation credit.

## Warnings and required actions

**SKIP3: skip the affected analysis.** The exact runtime, permitted installation,
required executable or requested task capability is unavailable. Read
`WARNINGS.md` and `FAILURE.json`; complete permitted setup and retry to new paths
when the immutable request changes. Installation and downloading occur only
through explicit setup flags. Execution installs nothing.

**FAIL2: no result is admitted.** Inspect the original logs and trace for invalid
inputs, integrity discrepancies, failed or ignored tasks, missing outputs,
capacity limits or cancellation. Preserve all evidence, correct the named cause
and use new paths for changed inputs or settings. Keep original IDs and
`NaN`/`Inf` values visible; scientific branches and defaults must retain their
declared meaning.

Require `COMPLETED.json` and successful `--verify` before downstream use. The
latest failed attempt prevents fallback to an earlier success. A completed
declared result establishes the recorded execution/output contract; biological
accuracy and unresolved paper settings require separate assessment. Hashes
establish integrity, not execution authenticity. Read permission-mode warnings;
use a permitted filesystem preserving POSIX modes when exact modes are needed.
The original reference run also warns that Graphviz is absent for the optional
DAG image. Cell Ranger remains excluded at the user's request.
