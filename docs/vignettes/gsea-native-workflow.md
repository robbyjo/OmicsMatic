# Native GSEA prepared-data workflow

The standalone `BroadGseaWorkflowCli` runs the native Broad GSEA engine from
prepared JSON data. It captures inputs and explicit parameters, runs an owned
Java worker, retains every null enrichment score, and writes complete-family
statistics. VERIFY recomputes from the captured inputs in NEW output and compares
every scientific output byte. REPLAY does the same computation while recording
replay lineage. Both preserve the parent capture.

The supported source profiles are 4.3.2, 4.3.3 and 4.4.0. Their exact prepared-data
scope is ranking, source randomization, enrichment walks and meandiv statistics.
This interface does not ingest vendor GCT/CLS/GMT/RNK files, transform expression,
map identifiers, collapse probes, fetch collections or generate vendor marker/GUI
reports. It requires no external GSEA installation. It is currently a standalone
entry point; global command registration and catalog admission are separate work.

## Try the complete example

Build the normal executable JAR from the project root. Use Java 21 or 25 in the
qualified environments and the project's normal build prerequisites:

```powershell
.\gradlew.bat executableJar
$gseaJar = (Resolve-Path 'build/cli/omicsmatic-0.4.0.jar').Path
$gseaDemo = Join-Path (Get-Location) 'gsea-example-001'
if (Test-Path -LiteralPath $gseaDemo) { throw 'Choose a NEW demo directory.' }
Copy-Item -LiteralPath 'examples/native-gsea' -Destination $gseaDemo -Recurse
java -cp $gseaJar org.jlinalg.cli.BroadGseaWorkflowCli --request (Join-Path $gseaDemo 'run.json')
java -cp $gseaJar org.jlinalg.cli.BroadGseaWorkflowCli --request (Join-Path $gseaDemo 'verify.json')
java -cp $gseaJar org.jlinalg.cli.BroadGseaWorkflowCli --request (Join-Path $gseaDemo 'replay.json')
```

The example contains synthetic data, 300 literal genes, 16 samples and five
synthetic gene sets. It runs nine analyses: three forms in each source profile,
with 10,000 permutations per analysis. This demonstrates source-counterpart
behavior; it is not author-data or biological reproduction. The 4.3.2/4.3.3 tags
resolve to the same upstream source commit, with separately built reference
profiles. A source-profile label does not assert byte identity with vendor builds.

The recipes use relative paths, resolved against the recipe file's directory.
They create `run-out`, `verify-out` and `replay-out` there. Existing output is
preserved and refused; choose another directory rather than deleting evidence.
If the build cannot obtain a prerequisite, follow the build's exact diagnostic.
The workflow itself never installs software or substitutes an upstream engine.

Start with `analysis-status.json` and **`WARNINGS.md`** in each output. A successful
run has `complete_result: true` and status `PASS_RUN`, `PASS_VERIFY` or
`PASS_REPLAY`. Any failed or skipped analysis prevents completion of the entire
requested batch, including analyses that finished earlier. Partial results remain
available for diagnosis and are not a complete batch result.

## Provide prepared data and explicit choices

Each RUN request requires `schema_version: 1`, `mode: "RUN"`,
`engine: "native-broad-gsea-source-core"`, a NEW `out`, `limits` and one through
64 `analyses`. The examples provide every required field. Analysis IDs are literal
labels; output directories use opaque `case-0000` names instead of those IDs.

Each analysis supplies `id`, `input`, `form`, `edition`, `normalization: "meandiv"`,
`permutations`, `seed`, `minimum_size`, `maximum_size` and `weight`.

| Form | Required additional choices | Prepared input |
| --- | --- | --- |
| `PRERANKED` | No implicit sort or metric | `genes`, `ranking_scores` in the intended order |
| `EXPRESSION_GENE_SET` | Complete `ranking` object | `genes`, `samples`, `expression`, ordered `class0` and `class1` indices |
| `EXPRESSION_PHENOTYPE` | Complete `ranking` and `randomization` | Same expression contract |

`seed` is a canonical signed 64-bit decimal **string**, for example `"12345"` or
`"-9223372036854775808"`. JSON numbers are not accepted for the seed. Weights are
`CLASSIC`, `WEIGHTED`, `WEIGHTED_P2` and `WEIGHTED_P1_5` within finite admitted
source branches. No other normalization is inferred or substituted.

The ranking object explicitly selects `metric`, `median`, `biased_variance`,
`fix_low_variance`, `absolute_sort`, `ascending` and `class_of_interest` (0 or 1).
Metric values are `SIGNAL_TO_NOISE`, `T_TEST`, `DIFFERENCE`, `RATIO` and
`LOG2_RATIO`. Randomization values are `NO_BALANCE`, `BALANCE_CLASS0`,
`BALANCE_CLASS1` and `EQUALIZE_AND_BALANCE`. Choose the actual study settings;
the example is not an inferred default for a paper. A source low-variance floor
is part of the explicitly selected metric behavior, not an automatic data repair.

All input files require `schema_version: 1`, unique literal `genes`, `gene_sets`
and `provenance`, plus the form-specific fields above. A gene-set object has
`name` and an ordered `members` array. Duplicate gene/set/member/sample identities
are refused; no trimming, uppercasing, mapping or deduplication is performed.
Unknown schema fields and duplicate JSON keys are refused. UTF-8 is decoded
strictly, and malformed Unicode is not replaced.

Expression rows correspond to `genes`; columns correspond to `samples`.
`class0`/`class1` are zero-based sample-column indices. Their order is retained,
and they must partition the samples completely without overlap. Signal-to-noise
and t-test require at least three samples per class. Prepared expression and rank
values are JSON numbers converted directly from their original decimal tokens
to finite float32. No intermediate JSON double conversion, log transformation,
normalization, missing-value removal or count conversion occurs. Overflow to
nonfinite float32 is refused; ordinary float32 rounding remains explicit.

The provenance object requires nonempty `organism`, `identifier_namespace`,
`expression_units`, `universe_description`, `gene_set_source` and
`preparation_note`. Describe the actual supplied data, collection/version and
preparation. The workflow records these declarations; it does not establish their
biological truth or retrieve missing source material.

Each analysis uses its own complete admitted gene-set family. Statistics are not
pooled across analyses. Source size filtering and namespace projection are
reported in dispositions. In particular, source **`minimum_size == maximum_size`
disables both size filtering and projection**; it does not select exactly that
size. The source's `EQUALIZE_AND_BALANCE` does not equalize class sizes. Do not
reinterpret these source behaviors or silently replace an impossible randomizer.

## Limits, warnings and recovery

All nine limits are explicit; the example uses conservative synthetic-run values.

| Limit | Meaning |
| --- | --- |
| `maximum_input_bytes` | Combined bytes of all captured prepared inputs, counting each analysis's copy |
| `maximum_output_bytes` | Observed generated scientific/worker output; captured inputs and bounded closure metadata are separate |
| `maximum_files` | Complete capture file count, reserving closure records |
| `timeout_seconds` | Wall time of the owned Java computation worker |
| `heap_mib` | Java heap ceiling for that worker |
| `maximum_dense_cells` | Prepared rank/expression cell bound per analysis |
| `maximum_null_cells` | Engine null-family cell bound per analysis |
| `maximum_work_per_analysis` | Deterministic engine work ceiling per analysis |
| `maximum_permutations` | Allowed permutations per analysis |

These are logical capacities, a Java heap ceiling, wall supervision and observed
live output bounds. They do not provide OS RSS/CPU containment. File/output checks
are polled; bytes can exceed a ceiling before detection, and failure closure adds
bounded diagnostic metadata. Large arrays and their defensive copies can exhaust
the declared heap before reaching a cell limit. The worker is stopped on a limit
failure; it never truncates a computation into a successful result.

**SKIP3:** a named exact external input or current Java executable is unavailable,
inaccessible or forbidden. Supply that exact permitted prerequisite or keep the
dependent analysis skipped. No vendor GSEA runtime is needed for native RUN.

**FAIL2: NO COMPLETE RESULT:** schema/data/design, source or capture integrity,
numerical, capacity, timeout or process failure. Preserve original inputs,
partials and diagnostics. Correct the reported cause and run with NEW output.
Nonfinite ranks/weights/statistics, undefined families and original substitution
branches are refused. No fallback constant, edition change, automatic repair or
upstream substitution is used to turn a failure into a result.

Read `worker/status.json`, `worker/stderr.log`, `process.json` and each analysis's
`partial/` files. After a forceful termination, the last buffered prefix may be
incomplete; it is diagnostic evidence, not a finished null distribution. Missing
or inaccessible members of an existing sealed capture are integrity failures.
The supervising JVM also needs memory for input copies and inventories; its heap
is set by the Java launch, while `heap_mib` controls the computation worker. If
the supervising JVM or operating system terminates before closure, a missing
`analysis-status.json` means **NO COMPLETE RESULT**. Preserve that unfinished
capture and launch stderr, correct the launch/resources and use NEW output.

## Saved evidence and semantic verification

The capture keeps the raw request, effective portable recipe, raw per-analysis
input copies, bindings/provenance, owned class payload identities and producer
JAR hash. It retains real rankings, dispositions, observed profiles, every raw
null ES, normalized null vectors and complete-family ES/NES/nominal-P/FDR/FWER.
`result.bin` uses the documented BGS1/version-1 big-endian binary encoding with
literal UTF-8 strings and exact IEEE-754 float/double fields. JSON-escaped identity
cells in TSV reports preserve literal tabs/newlines instead of corrupting columns.

`capture-files.json` inventories all saved payloads; `analysis-status.json` pins
that inventory and marks completion only after all analyses succeed. The current
compatible producer must have identical owned workflow/engine/parser class bytes
for VERIFY/REPLAY. Unrelated JAR payloads may differ; the saved whole-JAR hash
remains provenance. Missing/changed/extra files, links/junctions, undeclared
directories, inconsistent recipes and incomplete families are refused.

VERIFY and REPLAY controls explicitly contain `schema_version: 1`, their `mode`,
`analysis`, NEW `out` and `limits`. They structurally inspect the parent, copy its
captured inputs, recompute in the owned worker and compare all scientific output
bytes. Different resource limits may be chosen without changing scientific
parameters. Parent bytes and modification times remain intact, and output cannot
be nested in or alias a captured parent. Control output is itself a fresh complete
capture with lineage. Cross-host use does not require the original external input
paths to exist.

SHA-256 establishes saved integrity, not authenticity against coordinated
replacement. Semantic verification establishes recomputation from the retained
inputs and stated source profile; it does not authenticate the declared study,
prove upstream input provenance or establish biological validity.
