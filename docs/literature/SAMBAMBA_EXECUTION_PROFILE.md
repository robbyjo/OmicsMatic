# Sambamba execution development checkpoint

**Warnings and required actions:** this is a bounded development profile, with
no new catalog integration credit. Generated BAI files are retained but have not
been independently qualified. Use a separately qualified indexer before any
index-dependent downstream analysis. The [subsequent qualification controls](SAMBAMBA_EXECUTION_QUALIFICATION.md)
cover resealed bundles, late completion-recording failure and observed spill,
thread and compression behavior. Complete current-tree catalog source review
and paper-scale capacity remain open. The complete 250-paper implementation
objective remains open.

The standalone Java entry point runs the exact original Sambamba 0.6.6 or 0.8.2
Linux x86-64 executable. It captures the input and request, records commands,
process identities, runtime libraries and original consoles, audits complete BAM
records, and re-executes the original classifier during `--verify`. The native
BAM audit checks permitted transitions; it does not independently decide which
molecules are duplicates. See the earlier
[source and BAM audit checkpoint](SAMBAMBA_WORKFLOW_DEVELOPMENT.md) for original
paper roles, source identities, licenses and deliberately retained discrepancies.
Its download-only notice describes that earlier checkpoint and remains unchanged.

## Run the synthetic example

Use Java 17 or later inside an approved Linux x86-64 environment with Python 3,
`/usr/bin/setpriv`, `/usr/bin/prlimit`, `/usr/bin/ldd`, and the libraries needed by
the selected executable. Run the following from the project root. Every output
directory must be new; retain failed attempts and choose another output name.

```sh
./gradlew :executableJar --no-daemon
python3 -B tools/literature/sambamba_validation/make_cases.py \
  --out build/sambamba-example-inputs
python3 -B tools/literature/sambamba_workflows/download_runtime.py \
  --edition 0.8.2 --out build/sambamba-example-runtime
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.SambambaWorkflowCli \
  --request examples/sambamba-execution/request.properties \
  --out build/sambamba-example-analysis
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.SambambaWorkflowCli \
  --verify build/sambamba-example-analysis \
  --out build/sambamba-example-verification
```

The [example request](../../examples/sambamba-execution/request.properties)
selects a four-record synthetic paired BAM and marks duplicates. Relative input
and executable paths resolve against the request file's directory. All fourteen
properties are required; no scientific or resource defaults are silently added.
For removal, set `action=remove`; the original marked companion is retained and
the original removal operation reads the same captured input. For 0.6.6,
download that exact edition into another new directory and set both `edition`
and `sambamba` accordingly. Another executable build is refused.

On Windows, build with `gradlew.bat :executableJar`, then run the Python setup
and Java commands inside the chosen WSL distribution. For example, from
PowerShell with the repository at `D:\projects\JLinAlg`:

```powershell
wsl.exe -d YOUR_DISTRIBUTION --cd /mnt/d/projects/JLinAlg -- /usr/bin/java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SambambaWorkflowCli --help
```

Use Linux paths inside request files passed to the Linux JVM. Native Windows
analysis returns `SKIP3`.
Cancellation tests targeted the recorded Linux JVM; cancellation of the Windows
`wsl.exe` relay has not been qualified. Each phase's PID receipt records the
Linux JVM and original process identities.

## Outputs, limits and failures

A successful analysis contains `COMPLETED.properties`, `WARNINGS.md`, the raw
and effective requests, captured `input.bam`, `marked.bam`, optional
`deduplicated.bam`, native audit/decision tables, runtime identities, and the
original process receipts and consoles. Successful verification writes a new
bundle plus a complete-record comparison. Original input/request paths may be
absent during replay; the captured data are used. The pinned runtime is still
required. A receipt or checksum alone does not establish computational validity.

The profile accepts coordinate-sorted short-read BAM within these audit bounds:
64 MiB compressed, 256 MiB decoded, one million records, 16 MiB per record,
4 MiB of header text and fewer than 16,384 references. Long-CIGAR `CG` and
nonstandard auxiliary types are refused. Never truncate data or arbitrarily
split duplicate families to fit these bounds. Use a separately qualified
larger-capacity workflow for larger inputs.

`wall_ms` applies separately to each original marking/removal process, including
startup and PID recording. `address_space_mb` is enforced with Linux `RLIMIT_AS`.
Metadata probes have fixed 10-second and 512-MiB limits. Workspace size, entries
and RSS are sampled; this is not an OS disk quota. JVM audit memory is separate.
`setpriv` requests termination of the original process when its Linux JVM dies.
No network or filesystem isolation is claimed.

* `SKIP3` means a named execution prerequisite is unavailable. Read the notice,
  supply the exact permitted executable, helper or library, then retry with new
  output. If installation is unavailable, skip the dependent analysis.
* `FAIL2` means the result is unaccepted: invalid input, contradictory request or
  runtime, source-process error, exceeded limit, or recording failure. Read
  `FAILURE.txt`, `WARNINGS.md` and the phase consoles. Preserve provisional files,
  correct the named cause and retry with new output. A late completion marker
  is invalidated rather than accepted.

P110 reports removal with 0.6.6 and P198 marking with 0.8.2. Their complete author
parameters and binary builds remain unknown. The profile does not infer them,
substitute editions, repair BAM metadata, or claim full paper reproduction.
Original 0.8.2 writes header `VN:1.0`; that value is preserved. Missing quality
byte 255 is not replaced with zero. Marking is not UMI-aware.

## Recorded validation and remaining work

The [saved validation record](sambamba-execution-development/VALIDATION.json)
contains the tested source identities, original runtime results, process guard
results, and explicit outstanding controls. Before integration, 13 focused Java
tests passed; 32 original-runtime fixture cases and 32 genuine replays passed
with both editions and both actions, including empty inputs and missing data.
There were 96 complete BAM comparisons against separately executed originals.
Five actual process controls passed: wall time, address space, workspace entry
count, Linux JVM termination and parent death. Unrelated control jobs survived.
These synthetic controls do not establish biological validity, independent BAI
correctness, binary authenticity, full tool coverage or catalog qualification.

The validation helpers under `tools/literature/sambamba_execution_validation/`
can generate inputs and repeat the fixture matrix and Linux process controls.
They require explicit paths to the development JAR and permitted original
executables. Their saved records are evidence of the named executions; reading
them does not perform a new analysis. Prior checkpoints and failed attempts are
retained unchanged. The original validation archive retains the earlier version
of this guide, including its then-pending controls. The
[subsequent qualification record](SAMBAMBA_EXECUTION_QUALIFICATION.md) records
later executions without rewriting that checkpoint. Current-tree catalog source
review and the documented capacity/index limitations still constrain broader
qualification claims.
