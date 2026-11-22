# Original FUSION workflows

`fusion-tools` runs the bundled original FUSION R scripts, pinned to revision
`9346c1222bffbb34499fa7a8e23c1b701b55cb05`. It provides prepared-weight TWAS,
prediction-model training and postprocessing, with saved inputs, runtime versions,
original exports, warnings and an offline verifier. The native Java prepared-model
API is documented separately in [the native component checkpoint](FUSION_NATIVE_COMPONENT_2026-11-22.md).

Read the [measured qualification and remaining work](FUSION_QUALIFICATION_CHECKPOINT_2026-11-22.md)
before interpreting the examples. The current scripts do not establish historical
paper editions, reproduce complete papers or implement SuSiE prediction training.

## First run

Build with the project's JDK21 setup (`gradlew.bat executableJar` on Windows).
Existing release assets are separate from this source build. The original source
and GPL-3.0 license are bundled; external runtimes are not included in Git.

Start with the [artificial examples](../../examples/fusion-original/README.md).
They contain 64 generated samples, 12 variants and three prepared genes; they
are execution checks, not biological data. **Edit Rscript, private library and
binary paths in the JSON for your machine.** Input/output paths resolve relative
to the request JSON. Create the output's parent directory first; output itself
must be NEW and physically separate from inputs and replay parents.

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar fusion-tools --request examples/fusion-original/association-windows.json
java -jar build/cli/omicsmatic-0.4.0.jar fusion-tools --verify build/fusion-example-association
```

Expect a completion line with finite/no-result/colocalization counts. Read
`WARNINGS.md`, `summary.json`, `worker/original.stdout.log` and
`worker/original.stderr.log` every time. A complete result also has
`COMPLETED.json`, `manifest.json` and an `admitted/` directory. A failure preserves
logs and partial exports but admits no complete result.

The measured Windows runtime is R4.6.1; the measured Linux runtime is R4.5.2.
Both use here1.0.2, optparse1.8.2 and jsonlite2.0.0. TRAIN also requires glmnet5.2;
COLOC requires coloc5.2.3; SuSiE colocalization also requires susieR0.14.2.
Correlation plotting was tested with corrplot0.84; the separately retained
corrplot0.95 runtime emits warnings because the original cl.lim option is ignored.
Declare exactly the packages required by the operation/options. All loaded
namespace versions and their resolved roots are recorded. Other versions need
fresh qualification; editing a version declaration does not prove equivalence.

Use permitted private library locations. For example, R's ordinary
`install.packages(..., lib="YOUR_PRIVATE_LIBRARY")` can prepare a library, but
the current repository does not automatically install or select historical R/
package editions. Obtain the exact intended editions and their dependencies,
record the installation/source provenance, and declare them in the request.
Downloads/private installs are authorized where the environment permits them.
If installation is prohibited or an edition is unavailable, retain SKIP3 for
the dependent analysis and identify the missing prerequisite to the user.

## Request fields

RUN requires `schema_version:1`, `mode:"RUN"`, the full
`profile:"fusion-9346c1222bffbb34499fa7a8e23c1b701b55cb05"`, `operation`, `out`,
`rscript`, `r_libraries`, exact `expected_R` and `expected_packages`, `inputs`,
`options` and an explicit positive integer `seed`. TRAIN requires seed1 because
the original training script resets its R seed to1. This does not replace the
original PLINK/GEMMA random-number defaults.

| Operation | Required inputs | Optional inputs / external programs |
| --- | --- | --- |
| `ASSOCIATE` | `sumstats`, `weights`, `weights_dir`, `ref_ld_prefix`, `chromosome` | `panel_sizes` is passed as original PANELN. No external executable binding. |
| `TRAIN` | `bfile` prefix for PLINK BED/BIM/FAM | Separate `phenotype` and `covariates` files; otherwise original phenotype handling applies. Declare `binaries.plink`; BLUP/BSLMM also need GEMMA; estimated h2 without hsq_set needs GCTA. |
| `POSTPROCESS` | `association`, `sumstats`, `ref_ld_prefix`, `chromosome`, `weights_base` | Explicit `gene_annotation` is required for locus plotting. `weights_base` resolves FILE paths in the original association table. |

The LD prefix has a chromosome suffix: for chromosome `"1"`, prefix `"data/ref."`
resolves `data/ref.1.bed`, `.bim` and `.fam`. Supply the original association
formats and allele/build-matched inputs. Weight tables and selected RData must
contain the original required objects and dimensions. The wrapper validates
populations and capacities; it does not infer a genome build or repair alleles,
identifiers or incomplete inputs. Input weights are deserialized by original R;
use scientific inputs you trust, as with the upstream scripts.

Training copies BED/BIM/FAM, phenotype and covariates into the capture before
execution. Rank normalization operates on a working copy, preserving your
original files. Original temporary evidence is retained: explicitly set
`options.noclean:true`. Prediction models are `blup`, `lasso`, `top1`, `enet`,
`bslmm`. `coloc_susie_P` belongs to ASSOCIATE and is not a training model.
Linux training was tested with PLINK1.9.1, GEMMA0.98.5 and, for estimated h2,
GCTA1.94.1. Windows training with those executables is not qualified here.
Original training shell calls require tool paths without spaces/metacharacters;
install the binary into a suitable private location and declare that path.

The allowed scientific options are:

| Operation | Allowed option names |
| --- | --- |
| ASSOCIATE | `force_model`, `max_impute`, `min_r2pred`, `perm`, `perm_minp`, `coloc_P`, `coloc_susie_P`, `GWASN`, `caviar` |
| TRAIN | `resid`, `hsq_p`, `hsq_set`, `crossval`, `verbose`, `noclean`, `rn`, `save_hsq`, `models` |
| POSTPROCESS | `minp_input`, `max_r2`, `min_r2`, `locus_win`, `max_cz_increase`, `plot`, `plot_legend`, `plot_corr`, `plot_individual`, `plot_eqtl`, `plot_scatter`, `report`, `omnibus`, `omnibus_corr`, `eqtl_model`, `ldsc`, `save_loci`, `verbose`, `zthresh` |

Values are typed: booleans are JSON true/false, probabilities are finite [0,1],
counts are integers and nonnegative numeric thresholds must be finite. Unknown
fields/options are refused. Omitted scientific options use the pinned original
script's defaults, not values inferred from a paper. Save explicit options when
they matter to the analysis; `worker/original-arguments.json` and
`worker/expected-original-options.rds` retain what was passed/parsed.

LDSC and omnibus exit before plotting/reporting in the original script; request
those tasks in separate POSTPROCESS runs. `omnibus_corr` requires `omnibus:true`.
`plot_corr:true` works independently. Locus plot suboptions require `plot:true`;
`plot_legend` accepts `all` or `joint`. Original plots retain their upstream
layout, including dense/overlapping labels in some configurations. They are
source outputs, not automatically polished manuscript figures.

## Explicit runtime compatibility

Both `runtime_compatibility` flags default to false. They are recorded in the
canonical request and prominently reported when enabled:

- `gcta_no_analysis_probe_exit1:true` accepts only GCTA's exact no-analysis
  startup diagnostic/banner with original exit1. Actual analysis arguments and
  statuses are forwarded unchanged. Use only for estimated-h2 TRAIN with GCTA.
  The retained legacy GCTA1.21 binary segfaulted and is not a fallback.
- `gemma_working_directory_output:true` adds `-outdir .` to GEMMA analysis calls
  while preserving its help call, original prefixes, settings and statuses.
  The measured upstream default otherwise omitted expected files despite a zero
  exit status. Conflicting explicit output routing is refused. Use only for
  BLUP/BSLMM TRAIN with declared GEMMA; this is an explicit invocation change.

The wrapper never chooses either compatibility mode silently.

## Missing results, resource limits and recovery

`admission.allow_no_result_rows`, `allow_empty` and `allow_replay_differences`
default to false. Enable only the policy you intend. No-result retention keeps
original absent statistics and prints their counts; it does not manufacture
estimates. Source zero p-values, NA/NaN/Inf, permutation absences and filtering
conditions remain visible. Association gates use captured unrounded source
TWAS.P, with exact reconstruction of the original formatted main/MHC exports.
Internally rounded original COLOC posteriors retain that rounding.

| Limit | Default |
| --- | --- |
| `timeout_seconds` | 600 |
| `maximum_input_bytes` / `maximum_output_bytes` | 512 MiB / 1 GiB |
| `maximum_member_bytes` / `maximum_files` | 512 MiB / 10,000 |
| `maximum_features` / `maximum_samples` / `maximum_variants` | 50,000 / 100,000 / 1,000,000 |
| `maximum_cells` / `maximum_matrix_cells` | 100,000,000 / 16,000,000 |

Declare overrides under `limits`. Matrix checks follow the actual intended
GRM/prediction/LD allocation, including reference-matched variants. These are
input/workspace and declared matrix bounds with periodic monitoring. They are
not an OS memory quota or a guarantee of observing children that escape between
samples. Wall time measures the worker; JVM preparation/cleanup adds time.
Only the owned root and observed, birth-identified descendant handles are closed.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Restore the exact named permitted runtime,
library, input or binary, installing privately where allowed, and retry NEW
output. If installation/access is prohibited, keep the skip and explain the
specific prerequisite and remedy. Never substitute an undeclared edition.

**FAIL2: NO COMPLETE RESULT.** Read the cause in `FAILURE.json` and warnings.
Keep raw logs and partial/failed/null/superseded evidence, fix the stated input,
protocol, numerical, resource, time or process problem, and retry NEW output.
Do not silently repair the data or change scientific options to obtain success.

## Saved VERIFY and real REPLAY

VERIFY checks saved integrity and relationships without starting R or resolving
the former host's runtime paths. Use the matching producer; selected producer
class fingerprints are part of the saved protocol. Hashes do not authenticate
coordinated replacement. The verifier does not independently decode opaque RDS,
refit models, attest the host or prove biological validity.

REPLAY re-executes original R with captured inputs in NEW output. Save this JSON
beside the example requests, choosing new output names:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"../../build/fusion-example-association","out":"../../build/fusion-example-replay"}
```

Run it with `fusion-tools --request`. The default requires byte-identical
scientific exports; original external-tool stochastic fits and PDF metadata may
differ. Inspect `replay-comparison.json`. A RUN that explicitly sets
`admission.allow_replay_differences:true` permits recorded differences on replay,
with a prominent warning. Replay is not an independent refit/equivalence test.

Keep full saved captures, producer JARs, runtimes, input source provenance and
failed attempts for private transfer. Git contains the artificial qualification
bundle and source, not the complete workstation environment.
