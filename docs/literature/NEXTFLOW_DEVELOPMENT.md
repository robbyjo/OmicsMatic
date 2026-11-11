# Declared Nextflow workflows

The current source provides `nextflow-tools` for exact original Linux x86_64
Nextflow **21.04.1, 22.10.6 and 24.04.4**. It captures a complete declared workflow,
data roots, lossless parameters, required executables and published outputs.
The old released 0.3.7 executable lacks this command. Catalog credit requires the
completed typed-workflow checkpoint and catalog/UI verification; earlier runtime,
trace and admission checkpoints alone did not earn credit.

Use Linux Java 17+, including Java inside WSL on Windows. Each engine uses its
separately admitted, pinned Java 11 runtime. Windows Java can verify a copied
completed attempt without installing or accessing the producer runtime.

## Build and try the example

From the repository root, build to a NEW directory under `build`:

```bash
./gradlew -I tools/literature/nextflow-development.init.gradle \
  -PnextflowDevelopmentOut=build/nextflow-example-package executableJar
```

Set `JAR` to that executable. Follow the complete synthetic FASTA example in
[examples/nextflow-workflow/README.md](../../examples/nextflow-workflow/README.md).
It exports its inputs and recipe and gives expected counts and run/verify/resume
commands. Successful synthetic controls do not establish biological accuracy or
reproduce a paper.

## Supply an explicit recipe

Use absolute Linux input paths. Every declared directory is captured completely,
including empty directories; a first-N input/output prefix is not a population.

```json
{
  "schema_version": 1,
  "engine_profile": "linux-x86_64-nextflow-24.04.4",
  "workflow": {
    "path": "/absolute/workflow",
    "entrypoint": "main.nf",
    "configs": ["nextflow.config"],
    "profiles": ["local"]
  },
  "resources": {"reads": {"path": "/absolute/reads"}},
  "parameters": {
    "reads": {"$input": "reads"},
    "outdir": {"$output": ""}
  },
  "required_tools": [{"name": "awk", "path": "/usr/bin/awk"}],
  "outputs": [{"path": "counts.tsv", "kind": "file", "allow_empty": false}],
  "require_tasks": true,
  "limits": {}
}
```

The example exporter supplies a working recipe; adapt bindings to your actual
workflow's parameter names. Parameters retain JSON number lexemes, ordering and
literal values. `$input` accepts an optional relative `path`; `$output` takes an
empty string or relative output member. Bindings resolve within declared captured
roots. Use `$literal` to protect an object with reserved binding keys. Include
`nextflow.config` explicitly when present; declare author configs and profiles
without guessing omitted settings. Fixed `-C` configuration bypasses ambient
Nextflow configuration. Nested includes remain relative to the including file.

Original input paths and names may contain Unicode, spaces or apostrophes. The
NEW session root must be canonical ASCII with no whitespace, outside originals.
Captured internal hardlink aliases and relative symlink chains are retained;
absolute/cross-root links are remapped within owned captures. Undeclared targets
and special stream inputs are rejected. Working FIFO/socket entries are recorded
as metadata without reading them. Declared outputs must resolve to regular files
or directories under the published root; a required nonempty directory must
contain a regular file.

```bash
java -jar "$JAR" nextflow-tools --recipe /absolute/recipe.json \
  --home /absolute/exact-home --setup-report /absolute/completed-setup \
  --out /absolute/new-session
java -jar "$JAR" nextflow-tools --verify /absolute/new-session
java -jar "$JAR" nextflow-tools --resume /absolute/new-session \
  --home /absolute/exact-home --setup-report /absolute/completed-setup
```

## Results, resume and verification

Each `attempts/NNNNNN` records original logs, trace, parameters, commands, runtime
admission, complete before/after working state and outputs. Every completed
attempt includes all referenced immutable blobs and verifies independently.
`outputs.json` maps logical published files to authoritative `objects/SHA256`
copies. Later cache/publication files are mutable and are not historical output
copies. Verify a specific old attempt explicitly when reviewing it.

Resume retains the immutable request, captured inputs, working directory and
original Nextflow cache. It checks the whole workspace against a hash-bound prior
state. A terminal preflight skip can recover from the preceding proven state;
an unknown process lifecycle or executed attempt without a complete state cannot.
Changed workspace files are rejected repeatedly rather than adopted as a new
baseline. A failed/skipped latest attempt makes default verification fail; it
does not silently fall back to a previous success. Use NEW sessions to change
inputs, parameters, profiles or capacities.

Recovered retries retain failed tasks. Ignored/unresolved task failures do not
admit a result even after engine exit zero. A workflow with no tasks is admitted
only with explicit `require_tasks: false` and valid complete declared outputs.
Resource bounds cover captured files, metadata, outputs and cumulative owned
workspace plus supervised local process limits. Supply adequate declared limits
and Java heap; a capacity failure has no successful-prefix result.

## Warnings and required recovery

**SKIP3: skip dependent analysis when the exact runtime, permitted dependency or
requested capability is unavailable.** Export setup separately:

```bash
java -jar "$JAR" nextflow-tools --export-runtime-setup /absolute/new-kit
```

The kit documents `--profile`, `--home`, `--report`, `--cache`, `--archive-dir`,
`--allow-install` and `--allow-download`. Install/download only where the operating
environment permits. Analysis never installs. A setup health check alone admits
no user analysis. The validated DrvFS installation reports 130 effective
permission-mode departures while retaining exact content/link checks; review
those warnings on that filesystem rather than suppressing them.

**FAIL2: no result is admitted for invalid input, changed evidence, process,
capacity, cancellation or finalization failure.** Inspect `WARNINGS.md`,
`FAILURE.json`, original `nextflow.log`, `trace.tsv` and process logs. Preserve all
attempts, correct the named cause, and use NEW paths when the request must change.
Require `COMPLETED.json` and successful `--verify` before downstream use. A
late finalization failure keeps pre-final warnings and writes a prominent FAIL2
heading even when the original engine completed successfully.

The guard tracks owned local processes. Remote scheduler/service lifecycles need
separate supervision. Arbitrary workflows can access undeclared external
resources; capture proves the declared population, not exhaustive isolation.
Executable file pins do not certify every external library/container/database.
Hashes establish integrity, not authenticity. Portable verification replays
contracts and completion evidence; it does not rerun biology, readmit the producer
host, certify accuracy or infer missing paper settings. Whole-paper pipelines and
component dependencies require separate admission. **Cell Ranger remains excluded.**

Historical component proofs in `nextflow-trace-foundation`,
`nextflow-runtime-development` and `nextflow-native-admission` retain their
original scope and hashes. The typed-workflow checkpoint supersedes their
unfinished-command status only after its own validation completes.
