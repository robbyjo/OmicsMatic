# Original nf-core/rnasplice1.0.4 FASTQ adapter

`nfcore-tools RNASPLICE-1.0.4` prepares a new complete cohort, runs the exact original FASTQ workflow and checks selected scientific output contracts. It retains all original default branches: STAR/Salmon, standalone Salmon, DEXSeq, edgeR, SUPPA2, rMATS and MISO. The original `skip_bigwig=true` default remains in effect. Every selected method must complete; failing methods are never silently disabled.

The source profile binds all 206 original files at commit `1d0494ae3402d1a46e0adadad24f81a0ff855c77`, including 29 original Git executable flags, and all 31 source-declared container images across 72 process aliases. Original source files remain unchanged. A separate runtime configuration supplies local image paths, a local executor and explicit resource maxima. Execution uses exact nf-validation1.1.3, standard Nextflow24.04.4 and the admitted private Apptainer runtime. This is an explicitly selected engine edition, not an inference about a paper's engine.

## Prepare prerequisites

Use x86_64 Linux Java21, including WSL, with permitted unprivileged containers and a native Linux filesystem supporting STAR's FIFO files. Complete the separate [source setup](literature/NFCORE_SOURCE_WORKFLOWS.md), [standard engine and plugin setup](literature/NFCORE_VALIDATION_BRIDGE.md) and [private container setup](literature/NFCORE_CHIPSEQ200.md). Analysis never downloads or installs software. Required exact images and their integrity pins are listed in the packaged `org/jlinalg/omics/nfcore-rnasplice104/IMAGES.json` resource; supply a JSON declaration:

```json
{"schema_version":1,"images":{"SOURCE_URI_FROM_PROFILE":"/absolute/private/exact-image.sif"}}
```

Include all 31 URIs from the profile. Unknown URIs, changed binaries or changed source bytes fail validation. Keep upstream source, runtimes, images and full raw traces private according to their terms.

## Describe the complete input cohort

The sample CSV has exact columns `sample,fastq_1,fastq_2,strandedness,condition`. Use absolute Linux FASTQ paths; an empty second path means single-end. Multiple rows for one biological sample represent technical libraries, which never count as independent biological replicates. Each condition needs at least two distinct biological samples. Strandedness is explicitly `unstranded`, `forward` or `reverse`. The contrast CSV has exact columns `contrast,treatment,control` and retains the declared directions.

The locked source checker accepts `.fq.gz` and `.fastq.gz`; its usage documentation also mentions uncompressed FASTQs. This adapter follows the actual checker and never compresses or renames input to bypass it. Mate suffixes must match. Biological IDs containing the source's first-match `_T<number>` pattern can change during its technical-run regrouping and are refused. Source-interpolated identifiers and basenames containing unsupported shell characters are skipped without repair; safe literal letters/digits and `_.-+:=@%` are supported, with source-permitted spaces retained only in condition labels. Such labels can still fail an original downstream method; no successful fit is inferred from request parsing alone.

Use [the request generator](../examples/nfcore-rnasplice104/create_request.py), passing the two CSVs and every prerequisite/reference path. It requires explicit `--miso-read-length`, repeated `--miso-plot-gene` values, `--rmats-read-length` and one statistical-model choice: `--unpaired-statistics` or `--rmats-pairings PAIRS.json`. These pairing settings apply to rMATS; the other methods retain their own source models. Paired-end sequencing does not imply paired statistics. A paired model requires a complete one-to-one biological pairing for every contrast. The actual original BAM order must represent those pairs; a mismatch fails without reordering BAMs, relabelling subjects or changing models. Do not change declared pairs or retry until arrival order happens to pass; use a workflow that explicitly represents the intended pairs.

The generator exposes task/executor limits, complete-table row capacities and an optional complete engine/process/capture limits object. Choose capacities for the whole cohort. Exceeding a capacity fails the analysis; input and result populations are never truncated. Reference basenames must also fit the source's literal shell-token scope. Original paths, bytes, biological IDs, technical-run mapping and contrast labels remain recorded.

```sh
python3 examples/nfcore-rnasplice104/create_request.py --help

java -Xmx3g -jar omicsmatic.jar nfcore-tools RNASPLICE-1.0.4 \
  --request request.json --home /private/exact-nextflow24-home \
  --setup-report /private/completed-setup-report --out /opt/NEW_owned_rna_session

java -Xmx3g -jar omicsmatic.jar nfcore-tools RNASPLICE-1.0.4 --verify /opt/NEW_owned_rna_session
```

The output parent must already exist and be permitted. Use the actual development/release JAR filename. For an explicitly requested resume, replace `--request` and `--out` with `--resume SESSION`; retain the exact producing JAR, original path and runtime home/report. A terminal preparation failure without `init.json` needs new paths. Live or indeterminate attempts cannot be restarted. A completed original engine can be reused to finish failed wrapper checks; otherwise resume uses the unchanged captured engine cache. External input edits never change the captured analysis.

## Interpret results and failures

**Require the latest wrapper attempt's `COMPLETED.json` and successful typed `--verify` before using results.** An inner engine completion marker alone is insufficient: wrapper checks can reject its cohort or statistical pairing. Read `WARNINGS.md`, any `FAILURE.json`, original engine trace/logs and process diagnostics. The full original engine capture remains in the session; derived rMATS tables live under the wrapper attempt's `rmats/` directory.

- **SKIP3, exit3 — analysis skipped.** An exact prerequisite or permitted capability is absent. Complete the named setup where installation is allowed, or leave the dependent analysis skipped. No partial result is admitted.
- **FAIL2, exit2 — no result admitted.** Inspect the named input, integrity, numerical, model, process or capacity failure. `FAILURE.json` records the phase and `failure.stacktrace` retains complete causes and suppressed diagnostics for new attempts. Preserve the run; correct the cause and use new paths when its immutable request must change. Never interpret partial outputs as a completed analysis. A memory-allocation error requires checking available host/VM memory and the native front-end JVM heap as well as the declared workflow capacities. Do not reduce the input cohort to pass. Once capacity is available, use new paths; a preparation failure without `init.json` cannot be resumed.
- **Completed selected contracts.** Every biological sample has its expected published BAM. All two-family Salmon `quant.sf` tables have complete within-family target populations, finite numerical domains and TPM totals consistent with their printed precision. Every contrast's ten rMATS tables is streamed in full, retaining original row order, both positional ID columns and all NA tokens. The native post-fit gate applies raw P strictly below the declared maximum and absolute delta PSI at least the declared minimum. It never substitutes FDR. Rows with unavailable gate values remain explicitly unassessable.

The other original model fits run and remain retained, but do not have independent numerical parity contracts in this adapter. MISO can produce stochastic differences. The source uses STAR2.7.10a while P027 reports2.7.9a; the human test fixture does not reproduce that worm paper. Source runtime permission departures, lifecycle resampling and missing offline Maven descriptor warnings remain visible in the engine evidence. Portable verification recomputes selected contracts from retained original output bytes; it does not rerun fits, readmit a host, certify biological accuracy or reproduce a whole paper. Hashes establish integrity, not authenticity.
