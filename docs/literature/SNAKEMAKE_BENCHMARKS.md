# Native Snakemake benchmark aggregation

**Current scope:** a Java benchmark reader, complete-input workflow and genuine
native recomputation. It sums recorded `cpu_time` and takes the maximum recorded
`max_rss` per explicit pipeline/replicate. It reads the standard and extended TSV
formats of selected Snakemake 9.27.1, commit
`f79d9eb5e2b679e1191818f24335907b041578be`. It does not implement the Snakemake
workflow language or measure jobs. The separate original-engine adapter and
catalog admission remain in progress; this component adds no catalog credit.
Build current source: released v0.4.0 executables lack the command.

## Start with the example

The [supplied example](../../examples/snakemake-benchmark/README.md) contains
invented benchmark values, explicitly labelled as arithmetic/format examples.

```powershell
.\gradlew.bat executableJar --no-daemon --max-workers=2 --console=plain
java -jar build/cli/omicsmatic-0.4.0.jar snakemake-tools benchmark --help
java -jar build/cli/omicsmatic-0.4.0.jar snakemake-tools benchmark --request examples/snakemake-benchmark/request.properties --out build/NEW-snakemake-benchmark
java -jar build/cli/omicsmatic-0.4.0.jar snakemake-tools benchmark --verify build/NEW-snakemake-benchmark --out build/NEW-snakemake-benchmark-replay
```

The native operation needs Java 17+ and this project's built JAR. It needs no
external Snakemake/Python installation. `SUMMARY.tsv` contains one row per declared
pipeline/replicate; `MEASUREMENTS.tsv` retains each job/benchmark-row assignment.
`inputs/` retains exact original benchmarks and the job manifest. Original and
effective requests, code fingerprints and complete file inventories accompany
the output. `COMPLETED.properties` is written last. `--verify` checks the saved
input/code/output membership and recomputes into new output, including verification
of the original summary against a fresh arithmetic audit. It can use captured
inputs after original caller paths disappear. Hashes establish integrity, not
authenticity, successful biological execution or author-environment reproduction.

## Inputs and repeats

Copy `request.properties` from the example. Paths resolve relative to that request.
Every field is required; duplicate or unknown properties are rejected. Capacities
are caller choices: `max_input_bytes`, `max_manifest_bytes`, `max_work_bytes`,
`max_files`, `max_rows`, `max_line_characters` and `wall_ms`. Exceeding a limit
rejects the complete operation. Cooperative checks are not OS quotas; provide
adequate permitted Java heap, filesystem and host resources.

The benchmark directory must contain exactly the complete set of benchmark files
listed in the TSV job manifest, plus optional subdirectories. Keep the manifest
outside that directory. Its columns are exactly:

```text
pipeline	replicate	job	benchmark	row
workflow-a	run-1	align-sample-a	align/sample-a.tsv	1
```

`benchmark` is a portable relative path under the benchmark directory. `row` is
the **one-based data row**, excluding the header. Every row of every file must be
assigned exactly once. All rows in one benchmark file belong to the same job and
pipeline. Different rows from `benchmark: repeat(...)` must have separate group
labels: do not relabel repeats as different jobs, select a favorable repeat, or
silently pool them. A group labelled by per-job iteration is not evidence of an
independent whole-pipeline replicate. Choose labels that describe the actual
experiment. Duplicate jobs within a group and row reuse across groups fail.

The caller must establish that the benchmark directory and job list cover the
intended DAG and that its jobs completed successfully. These supplied reports
alone cannot prove either condition. The command never infers omitted jobs from
file names, drops incomplete jobs or assigns unreported author settings.

## Meaning of the numbers

The selected source divides memory bytes by 1024 twice: `max_rss` is **MiB**.
The output `max_job_rss_mib` is a maximum over job reports, not concurrent
whole-pipeline peak memory. Summed CPU seconds are recorded process CPU time,
not elapsed pipeline time. `summed_job_wall_seconds` is an explicitly named
additional accounting field, not a pipeline makespan. There is no MB/MiB inference
or conversion and no statistical comparison across groups.

Original TSV formatting can round measurements to two decimal places. Java uses
the decimals actually written, sums them exactly with `BigDecimal`, and does not
claim recovery of lost precision. A source-written `0.00` remains zero. An `NA`
measurement can occur because a fast job ended before resource sampling. It is
not zero and cannot support a complete CPU/memory aggregate. Standard/extended
headers are exact selected-edition profiles; other versions and JSONL need their
own reviewed profiles rather than automatic format substitution.

## Warnings, failures and recovery

**FAIL2 — NO RESULT ADMITTED:** missing/nonfinite required metrics, malformed UTF-8
or rows, duplicate/omitted assignments, aliases/hard links, capacity/deadline
failure, changed inputs/code or a failed recomputation admit no complete summary.
Keep raw benchmarks, captured inputs, `WARNINGS.md`, `FAILURE.txt` and every failed
attempt. Correct the named cause and retry a **NEW** output directory. Obtain
complete permitted measurements when metrics are unavailable; never impute zero,
thin the job set, relabel repeats or refresh old code fingerprints to force a pass.

**SKIP3 — UPSTREAM DEPENDENT ANALYSIS SKIPPED:** when using an upstream workflow,
missing or forbidden engine/software installation skips that dependent execution.
Install only where permitted or leave it skipped. This rule does not require an
upstream installation for native aggregation. Original-engine setup/control runs
in development are separate from a qualified project execution adapter.

## Paper roles reviewed so far

| Paper | Source-supported role | Separate or unresolved work |
|---|---|---|
| P027 | Modified CLAPAnalysis RIP-seq workflow | Exact workflow revision, references, aligner/counting options and biological execution |
| P061 | Large-scale analysis pipelines producing Zarr tensors | Cloud provisioning/services, methylation/3D-genome algorithms and author deployment |
| P181 | LaDDR/Pantry comparison on 100 Geuvadis samples: sum `cpu_time`, maximum `max_rss` | Author engine/options, complete sample experiment, success/coverage of the measured DAG |
| P182 | Cartilage bulk RNA-seq alignment/counting/expression/QC pipeline | Exact STAR, featureCounts, RSEM and QC preparation and author settings |
| P190 | MAJIQTL provides a Snakemake pipeline; contributions identify its developer | Pipeline availability does not establish execution of the paper's analyses through it |
| P210 | SHAPEIT5 wrapper for combined exome/array phasing | Harmonization, common/rare scaffold/reference details and downstream recessive tests |

P190's lower-case name in Data/code availability was absent from the existing
five-paper Snakemake inventory. Its primary paragraph and supporting contribution
paragraph are retained in the development audit; catalog admission is pending.
The six selected roles establish no author engine version of 9.27.1.

Primary sources include [P181](https://pmc.ncbi.nlm.nih.gov/articles/PMC13361010/)
and [P190](https://pmc.ncbi.nlm.nih.gov/articles/PMC12626415/). The original
[9.27.1 source](https://github.com/snakemake/snakemake/tree/f79d9eb5e2b679e1191818f24335907b041578be)
and [installation instructions](https://snakemake.readthedocs.io/en/v9.27.1/getting_started/installation.html)
are upstream references. The selected engine is MIT licensed; its notice is
retained in [LICENSES](../../LICENSES/Snakemake-MIT.txt).

## Java API

`org.jlinalg.workflow.SnakemakeBenchmarks.read(Path, Limits)` reads an entire
bounded selected-format table. `Measurement` holds recorded wall/CPU seconds and
RSS MiB as `BigDecimal`. `Assignment` explicitly binds a one-based table row to
pipeline, replicate and job. `aggregate(tables, assignments)` returns immutable,
sorted `Group`/`Summary` results and requires exact complete assignment of all
supplied rows. It performs no file capture or DAG completeness inference; use the
CLI workflow when complete captured inputs and recomputation receipts are needed.

## Development validation

The [saved development status](snakemake-benchmark-development/STATUS.json) records
44 passing Windows regressions, original-written serialization controls, three
complete successful upstream synthetic DAGs (standard, repeated and extended
benchmarks) and one expected failing-rule control. The packaged Java component
matches all recorded benchmark decimals and completes Windows/Linux recomputation,
including replay of a Linux replay on Windows, after caller paths are preserved
under different names. Per-job iterations are not described as independent
pipeline replicates. These are software controls, not the papers' analyses.

The first isolated environment solve's dependency conflict and the first CLI
test run's Windows file-identity failure are retained, together with as-run code
and diagnostics. A fresh environment satisfies the original dependency bounds;
the Java implementation now compares actual file identities when Windows provides
no basic file key. The hard-link refusal criterion remains tested. The private
installed environment is not archived or certified as currently healthy by a
saved-record check. Missing private records mean the dependent full check must
remain skipped until the exact permitted records are restored.

```powershell
python -B tools/literature/snakemake/records.py --public-only --out build/NEW-snakemake-public-record-check
python -B tools/literature/snakemake/records.py --private-root build/snakemake --out build/NEW-snakemake-full-record-check
```

Run these from this component's unchanged source snapshot; do not disable Python
assertions. These commands check saved evidence and independently audit its recorded
benchmark arithmetic. They perform no fresh Java/engine execution or current
runtime health check. Full checking also requires the exact named private files,
including the frozen candidate JAR. Keep missing evidence skipped until restored.
