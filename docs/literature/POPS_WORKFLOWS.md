# Native PoPS 0.2 prepared-input workflow

**Available in current source:** `pops-tools` fits native Java ridge or
minimum-norm OLS models from prepared gene/feature inputs, captures those inputs,
audits the results and genuinely recomputes completed bundles. It uses the
portable Java CPU backend. **Still open:** original PoPS 0.1 and LassoCV execution
adapters and paper-specific catalog admission. No additional catalog credit or
paper reproduction is claimed here. The released v0.4.0 download lacks this
command; build current source.

## Try the synthetic example

Run these PowerShell commands from the repository root. All three output
directories must be new. The example contains synthetic software controls,
with 72 target genes and 11 feature columns, and supplies every required file.
Java 17 or newer is required; Python, NumPy and R are not needed for this native
analysis.

```powershell
.\gradlew.bat executableJar
Expand-Archive -LiteralPath examples/pops-workflow/synthetic-inputs.zip -DestinationPath build/pops-example-inputs
java -jar build/cli/omicsmatic-0.4.0.jar pops-tools --request build/pops-example-inputs/request.properties --out build/pops-example-run
java -jar build/cli/omicsmatic-0.4.0.jar pops-tools --verify build/pops-example-run --out build/pops-example-replay
```

Read `WARNINGS.md` and `results/warnings.tsv` before interpreting results. A
successful bundle has a root `COMPLETED.properties`. A `WORKER_DONE.properties`
file alone supplies no accepted workflow result. Neither this example nor a
completion marker establishes a biological finding.

## Prepare real inputs

Use the gene identifiers and prepared feature layout from the exact selected
[official PoPS 0.2 source](https://github.com/FinucaneLab/pops/tree/76eb86cba10254490003c8f4dc7ff5ce492d3667).
The selected `pops.py` SHA-256 is
`35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc`.
The original package's feature-munging stage is separate from this prepared-input
workflow. This command neither generates annotations nor downloads feature sets
or MAGMA results. Preserve the original munging choices and their provenance.

The annotation is a whitespace-separated table containing `ENSGID`, `CHR` and
integer `TSS`. Every target gene needs an exact annotation identifier and an
exact feature-row identifier. Additional prediction genes are allowed. IDs are
literal: versions, chromosome labels and whitespace are not repaired.

For `feature_prefix=features`, supply:

- `features.rows.txt`: unique prediction-gene identifiers in matrix row order.
- `features.cols.0.txt`, and so on: unique feature identifiers, with no duplicate
  feature name across physical chunks.
- `features.mat.0.npy`, and so on: NumPy numeric matrices with those exact rows
  and columns. `feature_chunks` is the number of physical chunk files.

Identifier lists follow whitespace tokens and `#` comments. C and Fortran
storage orders and either byte order are read directly. No pickle or object
arrays are evaluated. Qualified numerical controls use float64 arrays; the
reader controls additionally cover primitive float32, integer and Boolean data
and NPY formats 1.0, 2.0 and 3.0. Nonfinite prepared feature values are errors;
there is no automatic imputation. `batch_columns` controls reading within each
physical chunk and does not change the selected scientific model.

Choose one target type:

- `custom`: `scores` is a tab-separated table with `ENSGID` and finite `Score`.
  Optional `score_covariates` is a tab-separated table with `ENSGID` and numeric
  covariate columns. Its genes are matched literally into target order. Optional
  `error_covariance` is a square NPY matrix or SciPy CSR, CSC or COO NPZ file
  already in target-score row order. Sparse duplicate coordinates are summed.
- `magma`: `magma_prefix` supplies `.genes.out` with `GENE` and `ZSTAT`, and
  `.genes.raw`. The raw parser skips two header lines and reads the source's
  nine fixed single-space fields followed by correlations to immediately
  preceding genes within each chromosome. Raw and score genes must have the
  same order and chromosome partitions must agree with annotation. Raw chromosome
  numbers may label the same partition differently, such as `23` versus `X`;
  gene IDs and output annotations are not rewritten.

The source uses within-chromosome covariance blocks and ignores cross-chromosome
entries. Nonzero or nonfinite ignored entries produce explicit counts, console
warnings and required actions in the output bundle. Covariance within a block
must be finite and exactly symmetric. Custom covariance is not regularized.
MAGMA covariance alone receives the original uniform diagonal shift:
`abs(min(0, minimum block eigenvalue)) + 0.05 + 0.9*max(0, populationVar(Y)-1)`.
The selected source's HLA exclusion is chromosome string `6`, inclusive TSS
20–40 Mb. These rules are retained even when another convention is customary.

## Write a request

Paths are relative to the request file's directory, or absolute. For absolute
Windows paths use forward slashes. The labels below must describe your actual
inputs; the program records declarations and does not establish their biological
correctness.

```properties
profile=pops-native-prepared-v1
edition=0.2
method=ridge
target_type=custom
gene_annotation=annotation.tsv
feature_prefix=features
feature_chunks=1
scores=scores.tsv
genome_build=GRCh38
feature_set_id=replace-with-exact-feature-set-and-preparation
target_source_id=replace-with-target-study-and-preparation
java_heap_mb=8192
wall_ms=900000
max_input_bytes=536870912
max_work_bytes=2147483648
max_files=1000
batch_columns=128
```

For MAGMA replace `target_type=custom` and `scores=...` with
`target_type=magma` and `magma_prefix=path/to/prefix`. MAGMA covariates and error
covariance default to enabled. Custom requests exclude MAGMA settings. Native
`method=linreg` selects minimum-norm OLS. Other editions and LassoCV are refused;
the program does not substitute ridge or a different edition.

Optional settings and defaults:

| Setting | Default and meaning |
| --- | --- |
| `p_cutoff` | `0.05`, strict marginal p-value cutoff; `none` omits this filter |
| `max_selected_by_p` | `none`; nonnegative maximum, excluding control features |
| `subset_features` | Omitted; path to literal feature names to consider |
| `control_features` | Omitted; path to controls within the considered subset; absent names are reported |
| `forward_steps` | `none`; positive count overrides the other selection settings |
| `projection_chromosomes` | `all`; comma-separated annotation chromosome labels otherwise |
| `selection_chromosomes` | `all`; independent marginal-selection mask |
| `training_chromosomes` | `all`; independent training mask |
| `projection_remove_hla` | `true` |
| `selection_remove_hla` | `true` |
| `training_remove_hla` | `true` |
| `use_magma_covariates` | `true`, MAGMA requests only |
| `use_magma_error_covariance` | `true`, MAGMA requests only |
| `max_prediction_genes` | `500000` |
| `max_target_genes` | `100000` |
| `max_features` | `1000000` |
| `max_selected_features` | `10000`; capacity failure does not truncate selection |
| `max_covariance_entries` | `100000000`; bounds complete dense chromosome blocks and sparse member storage |
| `max_header_bytes` | `1048576`, NPY header capacity |
| `max_identifier_chars` | `2048` |

All explicitly declared files are captured, including subset/control files
overridden by forward selection. Their contents do not affect forward selection.
To avoid requiring an unused file, omit its setting. Unknown or duplicate request
keys, inconsistent dimensions, duplicate gene identifiers and unavailable input
files are errors.

## Results, limits and failures

`results/predictions.tsv` retains every feature-row gene in its original order,
the score, unprojected/projected target and three masks. Prediction-only genes
retain `NA` target fields. `coefficients.tsv` contains every feature in original
order, with zero coefficients for unselected features. `marginals.tsv` preserves
all marginal diagnostics, selection flags and forward ranks, including constant
feature NaNs. `cv.tsv` retains all 25 ridge penalties and negative leave-one-out
MSE scores. OLS has a header-only CV table and explicit NaN ridge metadata.

The parent audits row identities, selection/rank/CV accounting and recomputes
predictions from captured features and saved coefficients before admission.
`fit.properties` contains model declarations and counts; `diagnostics.properties`
and `warnings.tsv` preserve source-specific diagnostics. No intercept or score
standardization is added to prediction.

`java_heap_mb` caps the worker JVM heap, not the parent or complete process.
`wall_ms` and work bytes/entries are monitored. They are not OS disk quotas or
a filesystem/network sandbox. Feature batching does not bound covariance,
selected-training or output storage. Cancel normally so the parent can stop
owned workers. Hard-killing a Windows parent has no process-job guarantee;
stop its recorded worker if needed.

**SKIP3 — analysis skipped:** a required Java runtime or execution permission is
missing. Read the named prerequisite in the console and `FAILURE.txt`; supply a
permitted Java 17+ installation and complete build, or skip this analysis. No
runtime is installed during analysis and no fallback engine runs.

**FAIL2 — no accepted result:** invalid inputs, numerical failure, exhausted
capacities, changed inputs/code, failed recording or replay mismatch. Retain the
whole bundle, including raw consoles and provisional files. The parent shows a
bounded excerpt of the worker's actual cause, including JVM startup errors written
to `worker.stdout`. Inspect both `worker.stdout` and `worker.stderr`, and
`WORKER_FAILURE.txt` when present. For JVM heap errors adjust `java_heap_mb` to an
adequate permitted value. Correct other errors at their named cause and retry a
new output directory. Increase permitted capacity without
dropping genes/features or splitting covariance dependencies. Existing output
directories are preserved. A failed final output write invalidates completion
even if fitting succeeded.

`--verify` first checks complete file/directory membership and hashes, then fits
again from captured files into a separate new directory and compares every
scientific result byte. Original external input paths are not needed. Use the
matching artifact/profile for historical bundles; never refresh hashes to force
verification. Hashes provide integrity, not authenticity or a signature. The
saved-record evidence reader is separate from this genuine analysis replay.

See [the workflow validation records](pops-workflow-development/STATUS.json)
for the current qualification and [the original matrix checkpoint](POPS_NATIVE_DEVELOPMENT.md)
for the unchanged numerical implementation and earlier original-source controls.
The latter describes its historical checkpoint's availability, not this later
command.
