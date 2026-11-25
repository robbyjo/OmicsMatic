# ChIP-seq through the packaged command

The current source build exposes both original nf-core ChIP-seq adapters from
the main command. Build the project as described in the
[README](../../README.md), and set `OMICS_JAR` to that new executable. The
earlier published v0.4.1 release asset predates these routes. This source
integration does not publish a new release or replace a released JAR.
The examples use Linux shell syntax, including Linux Java inside WSL on Windows.
Replace the absolute placeholder paths with your actual paths.

```sh
OMICS_JAR=/absolute/omicsmatic-0.4.1.jar
java -jar "$OMICS_JAR" nfcore-tools CHIPSEQ-1.2.1 --help
java -jar "$OMICS_JAR" nfcore-tools CHIPSEQ-2.0.0 --help
```

Select an exact edition. Neither a paper's unreported edition nor its Nextflow
version is inferred. `CHIPSEQ`, `latest`, and unlisted patches do not substitute
for one of these profiles. Existing ATAC, source-setup, engine and capture
commands remain available.

| Profile | Selected engine | Source-output checks | Statistical distinction |
| --- | --- | --- | --- |
| `CHIPSEQ-1.2.1` | Nextflow 21.04.1 | Read/alignment/FRiP, consensus, complexity, annotation/QC, conditional differential analysis | Original DESeq2 1.26.0 performs its differential branch only when the source replicate/group condition requests it. |
| `CHIPSEQ-2.0.0` | Nextflow 22.10.6 | Read/alignment/FRiP, peaks/annotation, complexity, saved DESeq2 QC | Original DESeq2 1.28.0 uses design `~1` for QC; this is not the 1.2.1 differential Wald workflow. |

Original programs perform the scientific processing and models. Java captures
complete declared inputs/results and checks the selected source contracts.
Original coordinate conventions, missing/nonfinite values, warnings, filenames
and the actual Picard `2.27.4-SNAPSHOT` provider in the 2.0.0 image remain
preserved. These controls do not establish biological accuracy or whole-paper
reproduction. The full nf-core catalog family remains pending while its eager,
RNA-splicing and Hi-C roles are unfinished.

## Prepare the exact prerequisites

Execution requires x86_64 Linux Java, including Linux Java inside WSL. Saved
result verification works on Windows and Linux. Obtain the exact source,
managed engine/Java home, completed current-host setup report, admitted private
Apptainer runtime and pinned original image(s) through separate setup. Source
setup alone does not make the analysis runnable. Analysis performs no automatic
installation, download or host security configuration change.

Use [the pinned source setup](NFCORE_SOURCE_WORKFLOWS.md) for the original
pipeline files. Pass the installed home's `source` directory to the preparer.
`--export-runtime-setup NEW_DIRECTORY` on either ChIP command exports the
separate managed Nextflow setup kit; select the exact engine from the table.
The container runtime and original image(s) remain separately checked
prerequisites.

Read the edition-specific preparation instructions:

- [ChIP-seq 1.2.1](NFCORE_CHIPSEQ121.md): six-column design, original image,
  reference FASTA/GTF and optional explicitly declared resources.
- [ChIP-seq 2.0.0](NFCORE_CHIPSEQ200.md): five-column samplesheet, all 33 locked
  source images, reference FASTA/GTF and declared resources.

Use complete input streams and experiment/control mappings. The preparers
capture selected inputs and bind paths to a **new** run root; they do not infer
mates, repair scientific IDs or overwrite originals. Keep prepared requests,
runtime homes, sources, images and execution directories private. Genome size
and resource settings in reference examples are fixture choices, not general
scientific recommendations.

## Run and verify

After preparation, use the edition's generated request and its matching
Nextflow home/setup report. For example:

Use canonical ASCII preparation/run paths without spaces or commas. Scientific
identifiers are retained; the adapter does not repair them to fit a path.

```sh
java -jar "$OMICS_JAR" nfcore-tools CHIPSEQ-1.2.1 \
  --request /absolute/prepared/request.json \
  --home /absolute/nextflow-21-home \
  --setup-report /absolute/current-host-report \
  --out /absolute/new-run
java -jar "$OMICS_JAR" nfcore-tools CHIPSEQ-1.2.1 \
  --verify /absolute/new-run
```

For 2.0.0, use `CHIPSEQ-2.0.0`, its prepared request, and the separately
qualified Nextflow 22.10.6 home/report. Existing typed requests and legacy
fully qualified Java entry points keep their options. The dispatcher calls a
reusable method that returns errors to the main dispatcher. The legacy
standalone `main` entry points retain normal process exit codes.

An explicit resume preserves the original captured request, inputs and cache:

```sh
java -jar "$OMICS_JAR" nfcore-tools CHIPSEQ-2.0.0 \
  --resume /absolute/original-run \
  --home /absolute/nextflow-22-home \
  --setup-report /absolute/current-host-report
```

Resume requires a known terminal prior attempt and the original run address.
It does not recapture changed scientific requests or repair an indeterminate
run. Use new preparation/run paths for changed inputs, settings or limits.
Complete saved portable payloads can also be supplied to `--verify`; their
inspection does not rerun a model or readmit the producer host.

## Warnings and required action

- **Exit 0:** the requested scoped operation completed. For an analysis,
  require its completed result and successful `--verify` before downstream use.
  A help or setup-export success does not admit an analysis.
- **SKIP3 / exit 3:** an exact runtime, image, platform or permitted capability
  is unavailable. The dependent analysis is skipped. Install/download separately
  where allowed, provide the named exact local prerequisite, or retain the skip.
- **FAIL2 / exit 2:** invalid input, integrity, process, cleanup, capacity or
  unassessable-output failure admits no complete adapter result. Read the latest
  attempt's `WARNINGS.md`, `FAILURE.json`, engine log/trace, process ledgers and
  source-component reports. Preserve every failure and original input; correct
  the named cause and use new paths where required.
- Broken output streams return a failure code. Existing result directories and
  setup exports are preserved rather than overwritten. Cell Ranger remains
  excluded.
- Preseq can refuse a library-complexity estimate when the observed duplication
  histogram is too shallow. The adapter then admits no complete result under
  this profile. The positive 1.2.1 software control uses the previously qualified,
  explicitly labelled PCR-rich synthetic derivative, retaining every original
  read. The shallow failed control remains separate. A study with unassessable
  complexity must retain that outcome; synthetic control reads are not a remedy
  for a study's insufficient duplication histogram.
- The fresh PCR-rich 1.2.1 control also retained a `PLOTPROFILE` timeout at its
  configured two-hour task limit. A resume uses the same captured request,
  inputs, limits and original cache; it never converts the failed cold attempt
  into a successful cold run. If limits must change, prepare a new run instead.

The [primary source audit](nfcore-source-audit/ROLES.json) distinguishes P032's
chipseq 2.0.0, P169's chipseq 1.2.1 and P050's unversioned workflow. Spike-in
scaling, pooled-donor/control selection and other separately described paper
methods are not inferred from a pipeline's default run.

## Check the software checkpoint

From the exact source checkout recorded by this checkpoint, use Python 3.11
or newer:

```sh
python3 -B tools/literature/verify_nfcore_chipseq_commands.py
```

This checks the saved public source, hashes, counts and qualification scope.
It does not rerun scientific processing. Optional `--producer`, `--private-root`
and `--cache` arguments add checks of the exact retained JAR, private evidence
closure and all 250 primary XMLs, respectively. Those private prerequisites
are not downloaded by this reader. Missing prerequisites produce **SKIP3**;
changed bytes or expanded claims produce **FAIL2**. Restore the exact recorded
files and physical source context; preserve failed checks and old expected
hashes instead of refreshing a historical checkpoint to accept new files.
