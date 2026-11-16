# Streaming Sambamba execution and complete replay

**Warnings and required actions:** use this development source build to obtain
`org.jlinalg.cli.SambambaStreamingWorkflowCli`. The original-runtime v2 profile
requires the exact permitted Sambamba 0.6.6 or 0.8.2 Linux x86-64 executable.
Missing prerequisites return `SKIP3`: skip the dependent analysis until the named
permitted executable, helper or library is supplied. `FAIL2` admits no accepted
result. Read `FAILURE.txt`, `WARNINGS.md` and the raw phase consoles; preserve
failed attempts and retry new output after correcting the cause.

**BAI outputs remain unqualified collateral.** Use a separately qualified indexer
before index-dependent downstream analysis. This profile alone adds no catalog
integration credit. Current-tree catalog source qualification and the full
250-paper implementation objective remain open. Whole-tool coverage, complete
author-environment reproduction and biological correctness are not established.

The Java auditor reads complete BGZF/BAM streams and compares one record at a
time. It emits decision rows incrementally and verifies full compressed stream
identities. Memory depends on the declared per-record/header/dictionary bounds,
JVM overhead and the caller's sink, rather than the total number of records.
The workflow's sink writes and verifies the complete ledger on disk. It does
not collect all decisions in memory. Explicit 64-bit byte/record/workspace
capacities replace the earlier profile's fixed small-file limits.

The exact original executable still performs duplicate classification. Native
Java verifies framing, CRC, every raw record byte except the permitted duplicate
bit transition, exact retained records after removal, complete dictionaries,
source header values, source-defined scores and coordinates. `--verify` genuinely
re-executes marking/removal on the captured input and compares complete records.
A successful native transition audit or consistent hashes alone is insufficient.

## Run a complete example

From the project root in an approved Linux x86-64 environment, use Java 17 or
later, Python 3, `/usr/bin/setpriv`, `/usr/bin/prlimit`, `/usr/bin/ldd`, and the
libraries needed by the exact original executable. All output directories below
must be new. The setup recipe downloads exact pinned artifacts and their
licenses; analysis does not install software.

```sh
./gradlew :executableJar --no-daemon
python3 -B tools/literature/sambamba_validation/make_cases.py \
  --out build/sambamba-streaming-example-inputs
python3 -B tools/literature/sambamba_workflows/download_runtime.py \
  --edition 0.8.2 --out build/sambamba-streaming-example-runtime
java -Xmx256m -cp build/cli/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.SambambaStreamingWorkflowCli \
  --request examples/sambamba-streaming/request.properties \
  --out build/sambamba-streaming-example-analysis
java -Xmx256m -cp build/cli/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.SambambaStreamingWorkflowCli \
  --verify build/sambamba-streaming-example-analysis \
  --out build/sambamba-streaming-example-verification
```

On Windows, build with `gradlew.bat :executableJar` and execute setup/analysis
inside the selected WSL distribution using Linux paths. For example, from
PowerShell with the project at `D:\projects\JLinAlg`:

```powershell
wsl.exe -d YOUR_DISTRIBUTION --cd /mnt/d/projects/JLinAlg -- /usr/bin/java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SambambaStreamingWorkflowCli --help
```

Native Windows analysis returns `SKIP3`. Cancel the recorded Linux JVM when
needed; cancellation of the Windows WSL relay remains outside the qualified
process controls. Changing helper/library identities can prevent replay; retain
the original records and supply the matching permitted environment rather than
refreshing saved identities.

## Declare the complete request

The [example request](../../examples/sambamba-streaming/request.properties)
contains all twenty mandatory properties. Relative input and executable paths
resolve against the literal request file's directory. Scientific/resource
scalars must be canonical integer strings where numeric; missing/duplicate/
unknown fields are rejected. The original raw request and effective captured
paths are retained separately.

| Fields | Meaning |
| --- | --- |
| `bam`, `sambamba`, `edition` | Original input, exact executable and explicit `0.6.6` or `0.8.2`. |
| `action` | `mark` retains all records with recomputed duplicate flags; `remove` also runs original removal against the same original input and retains the marked companion. |
| `threads`, `compression` | Passed literally to original `-t` and `-l`. |
| `hash_table_size`, `overflow_list_size`, `sort_buffer_mb`, `io_buffer_mb` | Passed literally to original options; no source rounding or source-defined buffer allocation is replaced by a Java approximation. |
| `wall_ms`, `address_space_mb` | Wall limit per original marking/removal stage and child Linux address-space limit. MiB means 1,048,576 bytes for this wrapper's address/work limits. |
| `work_mb`, `max_files` | Total owned workspace byte and file/directory capacities, sampled during original execution and checked at admission. Include captured input, outputs, complete decision ledger, metadata and original temporary files. |
| `audit_compressed_bytes`, `audit_decoded_bytes`, `audit_records` | Explicit capacities for each complete audited BAM, using 64-bit counters. No data are truncated or split. |
| `audit_record_bytes`, `audit_header_bytes`, `audit_dictionary_bytes` | Bounds on one BAM record, text header and complete binary reference dictionary. These control retained parser memory. |

Audit capacities are upper bounds, not memory or disk reservations. The example
declares a 1-TiB compressed-byte ceiling, 8-TiB decoded-byte ceiling and a large
record ceiling, with an 8-GiB workspace ceiling. The actual complete captured
input and generated files must fit the smaller applicable capacities. Set
adequate permitted limits for real data. Copying checks the remaining workspace
before creating the captured file and bounds each actual write against growth.
The original and captured input hashes must remain consistent.

Metadata probes have fixed 10-second and 512-MiB address-space limits. Original
mark/remove stages use the explicit request limits, including startup/PID
recording. `prlimit` enforces child `RLIMIT_AS`; `setpriv` requests parent-death
KILL. Workspace and RSS observations are sampled, not OS disk quotas or limits
on total Java/native resident memory. No filesystem/network isolation is claimed.

Only standard coordinate-sorted short-read BAM is supported. Long-CIGAR `CG`
and nonstandard auxiliary types are refused. The original fewer-than-16,384
reference restriction remains. Use sufficient heap for the selected individual
record/header/dictionary limits; a small-heap capacity demonstration is not a
universal heap guarantee for all such limits. Never arbitrarily split duplicate
families or repair raw values to obtain acceptance.

## Inspect and replay the result

Require `COMPLETED.properties` plus a successful genuine-runtime verification.
Outputs include raw/effective requests, captured `input.bam`, `marked.bam`,
optional `deduplicated.bam`, complete `decisions.tsv`, `audit.tsv`, exact class
and runtime identities, original commands/PIDs, and raw consoles/process limits.
The audit summary includes identities of the actual compressed streams read.
Admission checks those against the complete recorded inventory. Re-sealing a
counterfeit duplicate classification cannot replace genuine replay.

Partial ledgers and failed original outputs remain provisional. Recording errors
after completion invalidate the marker. Verification writes a new bundle,
retains the original bundle, and uses captured data even if original input/request
paths are absent. The exact original runtime remains necessary. Existing v1
bundles use the preserved v1 entry point and pinned implementation; v2 does not
rewrite them or substitute its identities.

Original 0.8.2 writes header `VN:1.0`, which is preserved alongside its executable
identity. Raw missing quality byte 255 is not converted to zero; numeric auxiliary
representations, NaN payloads and signed zero are compared as bytes. Original
secondary/supplementary duplicate flag behavior and tie/spill semantics remain
original. This is not UMI-aware marking. P110/P198 report different duplicate
operations and editions; their full author flags and builds remain unknown.

## Validation and scope

Validation records distinguish the streaming library component, the v2 execution
profile, saved readback, and actual original-runtime replay. The earlier
[v1 execution checkpoint](SAMBAMBA_EXECUTION_PROFILE.md) and
[v1 qualification controls](SAMBAMBA_EXECUTION_QUALIFICATION.md) remain retained
with their original scope and identities. v1 sources and behavior are preserved.

The streaming component is checked against all 32 saved original output BAMs,
including rich auxiliary representations and both reported releases. Additional
tests cover explicit capacities, corrupted/truncated framing, changed NaN bits,
counterfeit transitions, sink failure, file growth and capture workspace refusal.
The large synthetic input contains four million records and 308,000,201 decoded
bytes. Both exact original releases produced marked/removal outputs; all flags,
source metrics, records and stream identities were audited using an actual
64-MiB maximum Java heap. This is a heap measurement, not a 64-MiB RSS claim.

The final v2 build passed all 32 original-runtime fixture cases, 32 genuine
replays and 96 complete BAM comparisons. Both releases completed four-million-
record marking/removal analyses and genuine replays with `-Xmx64m`; all four
complete persisted decision ledgers were checked independently. There were
27 passing focused Java tests with no failures/errors/skips. Literal space/quote
paths, Linux temporary-file churn, counterfeit rejection, late completion
invalidation, actual limits/cancellation/parent-death controls, expected Windows
skip behavior and genuine unchanged-v1 replay also passed.

The initial fixture matrix retained a real transient temporary-file type
observation failure: v2 now reads one attributes snapshot per entry instead of
separate existence/type probes. Final admission still requires a complete strict
inventory. A Windows delete-pending observation-test failure and an obsolete
validator message expectation are retained separately. Production permission
errors remain errors; the Windows test accounts for that platform's real
delete-pending behavior, and a separate Linux churn control passed without
suppressing permission errors. Failed validation attempts are not relabelled.

The [saved development record](sambamba-streaming-workflow-development/STATUS.json)
pins current sources, actual candidates, test reports, metadata and the complete
retained private inventory. Large raw BAMs, full ledgers, candidate JARs and
original executables are retained locally; the public archive contains validation
metadata and sources rather than copies of those large artifacts. Public saved
readback is not a fresh scientific run or a full private-data check. Use
`--private-base build/sambamba-streaming` to check every named retained raw member:

```sh
python3 -B tools/literature/sambamba_streaming_validation/records.py
python3 -B tools/literature/sambamba_streaming_validation/records.py \
  --private-base build/sambamba-streaming
```

Use the scripts in `tools/literature/sambamba_streaming_validation/` to repeat
the original fixture matrix, generate bounded-memory large inputs, execute the
large component/workflow controls, and exercise resealed-bundle/late-failure
refusals. Each requires explicit permitted paths and NEW output. A validation
failure retains its raw errors and admits no successful validation. Broader
capacity, different environments and whole-paper validity require their own
evidence; do not infer them from a synthetic fixture or a metadata readback.
