# PLINK-hosted KING and IBD workflows

This workflow executes the project's exact private PLINK profile, captures
original inputs/reports/process records, and independently checks pairwise
IBS/KING arithmetic in Java. It supports four explicit operations on already
prepared, sorted human autosomal biallelic SNP BED/BIM/FAM files. It performs
no inferred QC, LD pruning, ancestry restriction, imputation or identifier repair.

Use Java 17+ and a project JAR containing `org.jlinalg.cli.PlinkRelatednessCli`.
The [qualification checkpoint](../literature/PLINK_RELATEDNESS_CHECKPOINT_2026-11-19.md)
names the tested isolated artifact and source pins. A normal source build uses
`gradlew.bat check executableJar` (or `./gradlew check executableJar`), producing
`build/cli/omicsmatic-0.4.0.jar`; that full build was not run for this tranche.
There is currently no root-CLI alias or new release for this component.

## Prepare the runtime once

The tested RUN profile is **Windows x86-64**, PLINK 1.9.0 dated 27 September
2026 and PLINK 2.0.0-b.1.2 dated 24 October 2026. It deliberately reuses the
existing project profile; it does not follow the newest upstream download.
RUN never installs dependencies. Export and invoke setup explicitly:

```powershell
$jar = 'build/plink-relatedness-development-1/build-6/omicsmatic-0.4.0.jar'
java -cp $jar org.jlinalg.cli.PlinkRelatednessCli --export-runtime-setup build/plink-setup-NEW
python build/plink-setup-NEW/setup_plink_relatedness.py `
  --home build/plink-private-home-NEW `
  --archives build/plink-private-archives `
  --report build/plink-setup-report-NEW `
  --allow-install --allow-download
```

Python 3.11+ is required. Omit `--allow-download` when using an existing exact
archive cache or when network access is prohibited. Setup checks the locked
hashes and original version probes, writes only explicit private paths and
changes no OS/PATH configuration. A changed or partially installed home is not
accepted. If Windows blocks final directory publication after successful
probes, retain the report, release the named lock and retry **new** home/report
paths. One such failure and its successful successor were retained during
development. Installation admission alone does not validate scientific results.

## Run an explicit operation

The [synthetic example](../../examples/plink-relatedness/README.md) writes full
requests with all settings and capacities, then shows a table RUN and VERIFY.
Use `--bfile` with the common prefix, without `.bed`. Keep FID/IID tuples exact;
the same IID may occur in different families. The workflow preserves numeric
phenotype missing codes and requires resolved distinct uppercase A/C/G/T alleles,
chromosomes 1..22, sorted chromosome/position order and valid SNP-major BED
padding. Unsupported input fails explicitly and is not rewritten.

| Operation | Required settings | Original operation and Java checks |
|---|---|---|
| `KING_TABLE` | `counts`, `rel_check`, `kinship_filter` (number or null) | `--make-king-table`; native pair counts, proportions and coefficients for every returned row; complete pair population only when unfiltered |
| `KING_MATRIX` | `precision`: `FLOAT64` or `FLOAT32` | `--make-king triangle bin` or `bin4`; exact FAM axes, lower triangle without diagonal, exact native numerical bits |
| `KING_CUTOFF` | `threshold`, `audit_retained_pairs` | `--king-cutoff`; complete original keep/exclude partition, optionally every retained pair's native kinship |
| `GENOME` | `rel_check`, `unbounded`, `nudge`, `ppc_gap_kb`, `min_pi_hat`, `max_pi_hat`, `frequency_mode`: `COHORT_FOUNDERS` | `--genome full`; native IBS counts/DST, count-derived PPC arithmetic and source rounded-field consistency |

Every RUN also requires `schema_version: 1`, `mode: "RUN"`, `bfile`,
`runtime_home`, new `out`, `threads`, `memory_mib`, `seed`, `settings` and
`limits`. Unknown or missing fields are refused. The example emits all twelve
limits: `input_bytes`, `metadata_bytes`, `working_bytes`, `disk_bytes`,
`output_bytes`, `files`, `samples`, `variants`, `pair_variant_operations`,
`pair_batch_size`, `line_bytes`, `wall_seconds`. They are logical/observed
capacities, not hard OS memory or process quotas. Set the Java heap separately.
The original program's `memory_mib` is also separate from Java audit working
memory. Combined input size is checked before large input hashing/capture.

Read `diagnostics.json` for actual checked rows, reportable/unreportable rows,
native pair-by-variant work, batches and BED scan bytes. Batches project only
the participating samples for pairwise audit; the original analysis retains
its full input cohort. Small batches repeatedly scan the entire BED. The
257-participant qualification fixture is not a population-scale benchmark.

## Interpret the estimator and its scope

PLINK 2 applies the between-family KING estimator to all pairs, including
same-FID pairs. Its table filter includes coefficients at the threshold;
its cutoff selector removes conflicts above the threshold and uses a heuristic.
The wrapper does not certify optimality. These choices follow the
[official KING documentation](https://www.cog-genomics.org/plink/2.0/distance)
and the tested original program. A partition-only request does not certify
that its retained participants form an independently checked unrelated set.

KING kinship and PLINK 1.9 PI_HAT are different quantities. GENOME uses the
original cohort-founder frequency model here, with no external frequency-file
interface. LD and frequency-panel choices must be prepared explicitly. The
[official IBD documentation](https://www.cog-genomics.org/plink/1.9/ibd) describes
the source bounding, optional unbounded/nudge behavior and PPC gap. Java does
not refit source frequencies, Z estimates, pedigree inference or PPC thinning.
It checks their stated arithmetic relations and printed precision. Unbounded
negative estimates remain unchanged. Do not interpret rounded source values
as exact threshold decisions.

## Verify or replay a capture

After successful RUN, invoke its `verify-request.json` with the same entry
class. VERIFY reads the capture and recomputes native checks without loading
PLINK or accessing its old runtime/input paths. It works after relocation on
Windows and Linux Java. It refuses failed/skipped captures and inconsistent
resealed reports, axes, partitions, process metadata, warnings or summaries.
Hashes provide integrity; they do not authenticate a coordinated fabricated
capture or prove biological relationships.

REPLAY requires a separate JSON object with exactly `schema_version: 1`,
`mode: "REPLAY"`, `analysis` (prior capture), new `out`, `runtime_home` and
`wall_seconds`. It verifies the old capture, uses its captured inputs/settings,
and runs a new original analysis. The old capture is unchanged. The example
writes `REPLAY_TABLE.json`; invoke it only after its table capture exists.

## Warnings, errors and recovery

| Outcome | Meaning | Required action |
|---|---|---|
| Exit 0, `completed-with-warnings` | Declared checks passed for the stated scope; individual table rows can still be unreportable | Read `WARNINGS.md`, `analysis-status.json`, `diagnostics.json`, original worker logs and per-row dispositions before interpretation |
| Exit 3, `SKIP3_DEPENDENT_ANALYSIS_SKIPPED` | Exact permitted runtime/platform missing, changed or unavailable | Supply the sealed supported profile using explicit setup, or leave dependent analysis skipped. No analysis is substituted |
| Exit 2, `FAIL2_NO_RESULT_ADMITTED` | Input, original process, numerical, capacity or integrity failure | Retain inputs/raw/partial outputs, fix the named cause and retry new paths. A zero upstream exit alone is insufficient |

Nonfinite matrix output and all-unreportable tables fail. A mixed table
retains explicitly flagged unreportable rows. A full retained-pair cutoff
audit fails on an unassessable pair; the partition-only alternative carries
its narrower scope explicitly. Do not replace NaN/Inf/NA with zero or discard
failed evidence. Original cutoff 0.5 is rejected by the pinned program; its
exit 8 is retained as FAIL2 without silently changing the threshold.

The [checkpoint](../literature/PLINK_RELATEDNESS_CHECKPOINT_2026-11-19.md)
lists observed tests and remaining work. Qualification covers these workflows;
paper-specific role review and cumulative catalog admission remain separate.
