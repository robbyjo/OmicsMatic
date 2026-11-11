# Cicero co-accessibility: development workflow

This separate development workflow runs original **Cicero-Monocle3 1.3.9** on explicit raw ATAC counts, peak metadata, caller-prepared cell coordinates and chromosome lengths. It retains original cell aggregation, size-factor normalization, distance calibration, graphical-lasso models and every connection. Native Java independently checks the input mapping, nearest-neighbor membership with ties, aggregation and numeric summaries, genomic windows, covariance/penalty inputs and signed connection reconciliation.

The adapter is a separate development JAR, invoked by its class name. The canonical executable is unchanged. Graph embedding, author-specific shuffled backgrounds/FDR, distal/proximal gene annotation, CCANs, gene activity, plotting, historical paper environments and biological calibration are separate operations. [The source-use review](MONOCLE_SOURCE_REVIEW.md) distinguishes P043's Cicero fitting role from P153's prediction reuse and from Monocle trajectories or the separate py-monocle implementation.

## Warnings and required actions

**SKIP3:** this captured runtime requires Windows Java, Windows R4.6.1, Cicero1.3.9, Monocle3 1.4.27 and the exact private dependency versions in [SOURCE-PROFILE.json](../../src/main/resources/org/jlinalg/omics/cicero-workflow/SOURCE-PROFILE.json). Native helpers also run on Linux Java; original runtime analysis needs a separately validated Linux profile. Missing, forbidden, mismatched or unsupported dependencies skip dependent analysis. Analysis installs nothing. Provision only where permitted; otherwise retain the skip. Never silently select another edition or use interop as a fallback.

**FAIL2:** any input mismatch, original error, bad model error flag, audit failure, capacity breach or timeout rejects the **entire atomic batch**, including earlier completed cases. Preserve the output and read `WARNINGS.md`, `analysis-status.json`, `worker/worker-report.json`, per-case `source-status.json`, original stdout/stderr and the process receipt. Correct the named cause and retry **new** paths. Do not force success by dropping peaks/cells, repairing identifiers, changing values or replacing nonfinite scores.

**Unresolved connections remain `NA`.** Original Cicero returns `NA` when overlapping model contributions disagree in sign. The reference has four such rows. They are neither zero co-accessibility, absent interactions nor valid null findings. The request explicitly chooses `retain-source-NA` or `fail-on-NA`. Nonfinite `NaN`/infinite connection scores are not admitted.

**Original precision matrices may be asymmetric.** Independent inspection confirms that the unchanged glasso1.11 source constructs precision columns separately. The original reference returns asymmetric `WI` matrices with zero error flags. Raw matrices and their maximum asymmetry are retained and prominently reported; no symmetrization is applied. Cicero assembles connections from covariance `W`. Java checks the declared input calculations and every resulting connection; it does not independently refit glasso, replay R RNG/FNN tie selection, certify inverse accuracy or authenticate execution.

**Source ordering and geometry matter.** `make_atac_cds` applies original factor/C ordering and chromosome/start ordering, recorded in an exact input-to-source ledger. Connections use a C-character `Peak1` and a factor `Peak2`, whose levels are unioned in model-column order. Do not sort both as strings to recreate the source result. A peak belongs to a window when either literal endpoint is strictly inside it. This is not generic interval overlap. The declared coordinate convention is recorded; endpoints are never silently converted.

## Build and run the example

Run from the repository root in PowerShell, using an existing Python and JDK21:

```powershell
python tools/literature/build_cicero_workflow.py --out build/literature-review/monocle-runtime/my-cicero-build
```

The builder needs the retained validated `differential-build-4` development parent. It compiles new classes with Java17 compatibility and adds new resources while preserving every parent JAR entry. It refuses an existing build directory and never replaces the canonical executable. On another checkout, reconstruct and validate the earlier development parents first. Development JARs and installed runtimes are not distributed in Git; public evidence binds their identities and owned entries.

The validated local runtime is `build/literature-review/monocle-runtime/cicero-reference-setup-1`. The [setup helper](../../tools/literature/setup_cicero_reference.py) installs only into a new private library and may download required packages from its recorded CRAN/Bioconductor repositories when `--allow-install` is supplied. It requires the retained exact Cicero source intake, private Monocle3 library chain and compiler health; it is an environment-specific preparation helper, not a portable installer. Initial missing Gviz/glasso/VGAM dependencies and all setup records remain retained. Namespace readiness alone does not admit analysis.

```powershell
$jar = 'build/literature-review/monocle-runtime/my-cicero-build/omicsmatic-0.3.7-cicero-workflow-development.jar'
python examples/cicero-coaccessibility/prepare_request.py `
  --reference-setup build/literature-review/monocle-runtime/cicero-reference-setup-1 `
  --r-home 'C:/Program Files/R/R-4.6.1' `
  --request build/my-cicero-request.json --out build/my-cicero-result
java -Xmx2g -cp $jar org.jlinalg.cli.CiceroWorkflowCli --request build/my-cicero-request.json
```

The helper verifies the synthetic input hashes, reads the explicit clean setup receipt and writes a new request. It installs no software. The example has 80 peaks and 240 cells, with prepared coordinates from the recorded generative simulation; those coordinates are not an inferred UMAP. Its explicit k20, seeds, six calibration samples, normalization and other settings are synthetic controls, not recommendations or inferred paper choices. It uses chunks of 1,000 entries to exercise streaming transports.

Require exit0 and `result_admitted=true`. Each admitted case retains original aggregation/CDS objects, complete models, calibration samples when estimated, connection RDS/TSV/JSON, source/RNG/argument ledgers, window dispositions, model matrix transports and `java-audit.json`. Original worker captures remain alongside the admitted copies. `models.json` indexes separate per-window matrix files; large sparse matrices and tables index ordered chunks. Factor levels, literal values and original order are preserved across chunk boundaries. Use the index files to read the complete population rather than selecting one chunk. Base CDS saves retain the original Monocle3 warning about unsaved Annoy/HNSW indices; this workflow uses caller-prepared coordinates and original FNN aggregation, and claims no learned Monocle graph/index restoration.

## Closed request contract

Run `java -cp $jar org.jlinalg.cli.CiceroWorkflowCli --help` for the fields. Unknown options, executable R/formula strings and input RDS files are refused. Required top-level fields are schema version1, mode `COACCESS`, exact version, raw count units, matrix format, coordinate system, five input paths, numeric metadata declarations, binarization choice, analyses, new output, R home/Rscript and explicit library paths.

| Input | Required representation |
| --- | --- |
| Peak metadata | Literal unquoted UTF-8 TSV: `peak_id`, `chromosome`, `start`, `end`. Each ID must encode its exact chromosome/endpoints with the original `:`, `-` or `_` separators. |
| Cell metadata | `cell_id`, then exactly the declared numeric metadata columns in declaration order. Literal `NA` cell IDs, whitespace, quotes and Unicode remain identifiers. |
| Counts | Dense TSV with `peak_id` then ordered cell IDs, or general coordinate integer/real Matrix Market with axes from metadata. Matrix/metadata order must agree. Duplicated sparse coordinates are refused, not summed. Values are nonnegative exact integers ≤2^53−1; per-peak sums must remain exactly representable. No TF-IDF, TPM or activity-to-count conversion occurs. |
| Embedding | `cell_id` followed by 2–50 finite numeric dimensions in metadata order. Absolute coordinates ≤1e150 keep squared distances finite. No embedding is learned. |
| Genome | `chromosome`, `length`; positive explicit lengths and source-compatible endpoints. Original literal `chr` removal is audited; aliases that would merge chromosomes are refused. |

Choose `one-based-closed` or `zero-based-half-open` explicitly. The label documents the input; it does not modify original endpoint handling. Duplicate IDs, missing/nonfinite input values, zero-total cells, coordinates outside the declared genome, implicit reordering and unsupported original IRanges ranges are refused. All-zero peaks remain declared features: zero-valued dimension anchors preserve their axes in the original triplet constructor without adding observations. Their identities are independently audited and reported. Binarization, if true, is the explicit original conversion of positive counts to1.

Each analysis requires a unique portable `id`, `aggregation_seed`, `k`, `size_factor_normalize`, `summary_stats`, `model`, `distance_parameter`, `skipped_windows_policy` and `unresolved_connections_policy`. Summary statistics name unique declared numeric fields; reserved original state fields cannot be supplied. Models declare `window`, `power` strictly between0 and1, and `max_elements`.

Distance mode `fixed` requires a finite nonnegative `value`. Mode `estimate` requires `seed`, `maxit`, `sample_num`, `distance_constraint` smaller than the window, positive `convergence`, `max_sample_windows`, `require_full_sample` and `allow_maxit_warning`. Returned samples and their original mean are retained. An empty/nonfinite calibration cannot produce an admitted result. The explicit policy can reject partial sampling or original iteration-limit warnings.

Skipped-window policy is `retain-source-dispositions` or `require-all-modeled`. Original zero/one-element and too-many-elements windows retain their exact reasons; they are not fictitious fits or evidence of absent interactions. No other unexpected model/window result is admitted. The explicit NA policy is described above. Multiple analyses form one atomic batch.

## Capacities and verification scope

Defaults are 128MiB total input, 200,000 peaks, 100,000 cells, one million input entries, five million intermediate entries, 50million exported model-matrix entries, 50,000 windows, ten million connection rows, 100,000 worker files, 50,000 entries per chunk, 128MiB/20million nodes per JSON file, 1GiB worker output growth, 100million native audit operations and 600seconds for the original R worker. Requests can raise these within parser bounds. Large runs need an appropriate Java heap, permitted storage and external resource limits. These are logical checks, not OS quotas; admitted copies and evidence add storage beyond worker growth. Native checks are bounded by the declared operation budget.

The independent original control compares every connection, complete model/calibration object, aggregation map and saved counts/axes for dense and sparse inputs. Additional controls cover fixed distance, unnormalized aggregation, all-zero peaks, explicit binarization, numeric summaries, tied coordinates, Unicode/quoted identifiers, multiple chromosomes, odd windows and chunk equivalence. Native methods use 498 actual original-function outcomes; 516 native and contract checks run on each of three JVMs. Semantic replay accepts an unchanged copied original and refuses 14 corruptions without relying on capture hashes. These checks are scoped method evidence, not independent glasso optimization, paper reproduction or biological validity. A public portable `VERIFY` analysis mode is not provided.

The [sealed controls](cicero-coaccessibility-development/VALIDATION.json) retain complete original objects, failed attempts, warnings, cleanup receipts and exact source/artifact entry inventories. Read the [prominent warnings and recovery actions](cicero-coaccessibility-development/WARNINGS.md). Verify the published synthetic evidence, without installing R or running an analysis, using:

```powershell
python tools/literature/cicero_workflow_evidence.py
```

This portable checker also independently recalculates every admitted connection from the captured original model covariance matrices, preserving factor order and unresolved `NA`. This checkpoint adds a development adapter and native methods; the catalog still reports 74 scoped integrations pending the separate Cicero paper-role review and promotion.
