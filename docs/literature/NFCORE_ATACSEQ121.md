# Original nf-core/atacseq 1.2.1

**The scoped source-build adapter has completed its reference execution.** The
unchanged upstream pipeline completed all 88 tasks and captured 356 published
files in the paired-end, broad-peak yeast example. The complete typed result and
its portable export passed verification on Linux and Windows. The
[development evidence](nfcore-atacseq121-development/README.md) records the
qualification and retained failures. The nf-core family remains pending in the
admitted catalog while its other pipeline roles are unfinished.

This profile runs original read QC, trimming, BWA alignment, duplicate handling,
ATAC filtering, MACS2 peaks, HOMER annotation, consensus/featureCounts,
conditional DESeq2 comparisons and ataqv/MultiQC reporting. Original programs
perform these analyses. Java captures complete inputs/results, validates the
source and request, reconstructs sample mappings and checks selected task/output
contracts. Numerical models remain upstream methods.

P169 ([Nature Genetics](https://doi.org/10.1038/s41588-024-01900-w)) explicitly
reports nf-core/atacseq 1.2.1 with default parameters. P050
([Nature](https://doi.org/10.1038/s41586-023-06821-y)) describes an nf-core ATAC
workflow without identifying its edition. These are two additional roles beside
the ChIP-seq uses already recorded for those papers. This profile does not assign
an edition to P050. Both papers leave the Nextflow edition unreported.

The implementation selects source commit
`1b3a832db5a53c92c2c89a4d6d79455e860461ad`, all 86 original files and exact
Nextflow 21.04.1 as its reference execution environment. The original source is
MIT licensed; its notice is retained in the
[source resources](../../src/main/resources/org/jlinalg/omics/nfcore-atacseq121/LICENSE-upstream.txt).
The [upstream documentation](https://nf-co.re/atacseq/1.2.1) describes the
original pipeline. The public yeast fixture validates software behavior; it
does not reproduce either paper's biological experiment.

## Requirements and setup

Build the project with the JDK described in the [project README](../../README.md).
The adapter bytecode targets Java 17; the current execution controls use Java 21.
Use Linux x86_64 Java, including Linux Java within WSL. Windows supports saved
result and source-output verification. Preparation/setup helpers use Linux
Python 3.10 or newer.

Provide the exact managed Nextflow profile, its completed current-host setup
report, the admitted private Apptainer **1.5.5-1** runtime and the pinned original
image. The runtime inventory is checked; select that qualified installation
rather than relying only on a displayed version string. Setup is separate from
analysis. Download/install only where the operating environment permits it.
An absent or forbidden exact prerequisite produces **SKIP3; analysis skipped**.

Export the managed Nextflow setup kit:

```sh
java -jar "$OMICS_JAR" nfcore-tools ATACSEQ-1.2.1 \
  --export-runtime-setup /var/tmp/atac-nextflow-setup-1
```

Follow its `README.txt` and the [Nextflow setup guide](NEXTFLOW_DEVELOPMENT.md),
selecting `linux-x86_64-nextflow-21.04.1`. Record the managed home and the new
completed setup report. A changed host/kernel requires a new health report.
The [nf-core foundation](NFCORE_FOUNDATION.md) documents the existing private
container runtime qualification. Supply that admitted runtime home before
running image setup; this ATAC helper installs no container runtime.

Retrieve the pinned source, registry metadata and pinned public example data:

```sh
python3 tools/literature/nfcore_atacseq121/intake.py \
  --out /var/tmp/atac-intake-1
```

Build the original image in a new private Linux directory:

```sh
python3 tools/literature/nfcore_atacseq121/setup_image.py \
  --container-home "$ATAC_CONTAINER_HOME" \
  --out /var/tmp/atac-image-1 --allow-download true
```

Image setup selects the original OCI digest
`sha256:cb843b04a06abd3538f0dfc0e0e4f08c691a79e895638811096c3a94d4767d5c`
and original creation epoch `1596038424`. Two independent conversions produced
the same 1,130,561,536-byte SIF with SHA-256
`f1ccbf611070edec8a0bc774e584f728c88b4411c6d2ca2a70bbe23225a34c4c`.
These identify the qualified current registry artifact and reproducible build;
they do not establish an author's historical image digest.

Create `images.json`, using the actual image path from the successful setup:

```json
{
  "schema_version": 1,
  "images": {
    "docker://index.docker.io/nfcore/atacseq@sha256:cb843b04a06abd3538f0dfc0e0e4f08c691a79e895638811096c3a94d4767d5c": "/var/tmp/atac-image-1/nfcore-atacseq-1.2.1.sif"
  }
}
```

Keep the adapter JAR, managed Nextflow/Java home, prepared inputs and work on
private Linux filesystem storage. The initial image pull
into a Windows-mounted temporary directory was slow and its intentional stop
left a cleanup error. That attempt remains retained; both successful replacement
builds used Linux storage. Export completed portable results afterward.

## Prepare a complete analysis

Place original FASTQ files, FASTA, GTF and optional reference resources under one
explicit data root. This preparation accepts four literal comma-separated
fields, including an empty final field for single-end input:

```csv
group,replicate,fastq_1,fastq_2
CONTROL,1,reads/C1_1.fastq.gz,reads/C1_2.fastq.gz
CONTROL,2,reads/C2_1.fastq.gz,reads/C2_2.fastq.gz
TREATMENT,1,reads/T1_1.fastq.gz,reads/T1_2.fastq.gz
TREATMENT,2,reads/T2_1.fastq.gz,reads/T2_2.fastq.gz
```

Replicate IDs must form `1..number-of-replicates` within each group. Repeated
group/replicate rows represent technical libraries, following the original
checker. Quoted CSV, comma-containing paths and unrepresentable original shell
syntax are refused. Scientific IDs are not silently renamed.

```sh
python3 tools/literature/nfcore_atacseq121/prepare.py \
  --workflow /var/tmp/atac-intake-1/source \
  --data-root /absolute/data --design /absolute/data/design.csv \
  --fasta /absolute/data/genome.fa --gtf /absolute/data/genes.gtf \
  --macs-genome-size hs \
  --container-home "$ATAC_CONTAINER_HOME" --images /absolute/images.json \
  --executor-cpus 4 --executor-memory-bytes 25769803776 \
  --max-memory 6.GB --max-time 2.h \
  --out /var/tmp/atac-prepared-1 --run-root /var/tmp/atac-run-1
```

`--run-root` must match the eventual adapter output path. Preparation copies
complete inputs, preserves the original design and records every changed path
cell in `path-bindings.json`. Non-path cells remain unchanged. Check
`COMPLETED.json` and `WARNINGS.md`; preparation performs no analysis.

Declare additional original parameters in a JSON object through `--parameters`.
For example, use the actual mitochondrial contig and appropriate MACS settings;
reference names and genome size are not inferred. Optional files use explicit
resource bindings, for example `--resource blacklist=/absolute/data/blacklist.bed`
with `"blacklist":{"$input":"blacklist"}` in the parameter object.
`--uri-map` explicitly binds URLs in the upstream example design to pinned local
files. Preparation never downloads those declared read paths.

`--limits` accepts explicit complete-data/resource limits. The initial defaults
include 16 GiB per captured file, 32 GiB total captured inputs and 256 GiB working
space, with 1,000 ms process polling. Large experiments need capacities
appropriate to their full inputs.
Exceeding a limit produces a failure; it does not admit a truncated analysis.

This local profile requires complete selected branches and copied publication.
Skip flags, institutional configurations, floating iGenomes references, custom
run names and uncontracted notification/publication modes are refused. Supply
explicit local references and the supported typed settings. Original BED starts,
SAF coordinates, featureCounts semantics, model fallback behavior and missing
values are retained.

## Execute and verify

```sh
java -jar "$OMICS_JAR" nfcore-tools ATACSEQ-1.2.1 \
  --request /var/tmp/atac-prepared-1/request.json \
  --home "$ATAC_NEXTFLOW_HOME" --setup-report "$ATAC_NEXTFLOW_REPORT" \
  --out /var/tmp/atac-run-1

java -jar "$OMICS_JAR" nfcore-tools ATACSEQ-1.2.1 \
  --verify /var/tmp/atac-run-1
```

Require exit zero, `COMPLETED.json` and a successful verification. Inspect the
complete source results through the engine's saved output index and immutable
objects. `analysis.json` records source branch counts, sample mappings and the
engine manifest. Original R models, reports, tables, NA/nonfinite values, task
logs and failed attempts remain retained. The source publishes sorted BAMs;
some featureCounts input names refer to its unsorted task intermediates. The
published PCA/distance products are the original MultiQC-wrapped TSV files.

Export a portable result into a new directory:

```sh
java -jar "$OMICS_JAR" nfcore-tools ATACSEQ-1.2.1 \
  --export-result /var/tmp/atac-run-1 --out /absolute/new-portable-result
java -jar "$OMICS_JAR" nfcore-tools ATACSEQ-1.2.1 \
  --verify /absolute/new-portable-result
```

Portable verification needs the saved objects and Java. It checks source/output
contracts without reading the producer's original paths or rerunning analysis.
It does not readmit the original host or authenticate execution history.

## Warnings, failures and required actions

| Status | Meaning and required action |
|---|---|
| `0` with a verified completed result | Original execution and selected source/output contracts completed. Read warnings and original statistical diagnostics before downstream interpretation. |
| `SKIP3` | An exact prerequisite or requested capability is unavailable/forbidden. Skip the dependent analysis; complete permitted setup or provide the admitted local prerequisite, then prepare new paths. |
| `FAIL2` | Invalid/inconsistent input, changed integrity, failed/unassessable execution, output-contract failure or exceeded capacity. No adapter result is admitted. Preserve inputs and the failed directory, resolve the named cause and retry new paths. |

**The optional workflow SVG needs Graphviz.** The original engine warns and skips
SVG rendering when `dot` is unavailable. The adapter surfaces this separately.
If the diagram is needed, install Graphviz where permitted and declare its
executable with `prepare.py --dot /absolute/dot` before a new run. Its identity
is then recorded by the engine. Other original plotting/reporting outputs remain
subject to their selected source contracts.

Development runs encountered owned thread-group gauge refusals with both 500 ms
and 1,000 ms polling. The failed runs remain retained and admitted no result.
The successful environment used an exact native-Linux copy of the adapter and
managed runtime, with a fresh official health report and explicit
`{"poll_millis":1000,"java_cpus":4}` limits. Moving the runtime was the tested
environment change; it does not establish a general cause for the earlier
refusals. No resource gauge was imputed.
If this recurs, retain
the process ledger and lifecycle journal, reduce host load or use an allowed
environment, and retry a new preparation/run with explicit supported limits.

The two initial full engines completed all 88 tasks. Their outer development
adapters failed on incorrect expected plot-task counts and unpublished raw
PCA/distance filenames. Those failed wrappers remain failed. The corrected
consumer checks the actual per-library tasks and published TSV names.

The initial scientific execution control is paired-end/broad-peak with two
groups and two biological replicates each. Other data, parameter combinations
and experimental designs require appropriate qualification. Complete software
execution does not certify biological accuracy or reproduce a whole paper.

## Native mapping and separate saved-output component

`org.jlinalg.workflow.NfcoreAtacseq121Mappings.read` reconstructs the original
sample table in Java. It preserves Python code-point sorting, source whitespace
stripping, integer replicate labels and technical-library order. Thirty-three
unchanged-original-checker cases cover valid and invalid designs. It refuses
unsupported replicate spellings/capacities and never returns a partial mapping.

An independently captured complete upstream trace supplies the 39-process,
88-task regression reference. These native methods require no R, Python or
container at analysis time.

For an existing complete captured engine result, run the separate component:

```sh
java -jar "$OMICS_JAR" nfcore-tools ATACSEQ-AUDIT-1.2.1 \
  --engine-result /absolute/complete-engine --out /absolute/new-component-audit
```

Its marker is `COMPONENT_AUDIT_COMPLETED.json` with
`scientific_result_admitted=false`. It checks saved source, inputs, task counts
and selected outputs. It checks no live runtime/image and makes no new
whole-workflow admission. It does not turn a failed adapter into a success.

Generic caller-declared workflows remain available through `nfcore-tools ENGINE`
and `nfcore-tools CAPTURED`. Their completion alone supplies no nf-core-family
or whole-paper qualification.
