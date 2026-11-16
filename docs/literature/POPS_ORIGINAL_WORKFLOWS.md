# Original PoPS workflows

The current source provides `pops-original-tools` for selected, unchanged PoPS
0.1 and 0.2 programs. It supports original 0.1 feature selection and chromosome
prediction, original 0.2 ridge/OLS/LassoCV fitting, and original feature munging.
The [native prepared-input workflow](POPS_WORKFLOWS.md) provides a separate Java
PoPS 0.2 ridge/OLS implementation. Choose the edition and method explicitly;
there is no automatic substitution after an original-program failure.

**Build current source. The released v0.4.0 executable lacks these commands.**
These are bounded source-software integrations. They do not establish complete
tool equivalence, author settings, biological validity or whole-paper reproduction.
Paper-role catalog admission remains open and this extension adds no catalog credit.
The [qualification status](pops-original-workflows/STATUS.json) records the exact
scope; [warnings and recovery actions](pops-original-workflows/WARNINGS.md) apply
to both execution and saved-record readback.

## Warnings and required actions

- Run Java inside permitted **Linux x86-64**, including WSL. Java 21 was tested.
  Running the original workflow directly in Windows Java returns **SKIP3**.
- Missing, unreadable or forbidden prerequisites mean **SKIP3: dependent analysis
  skipped**. Supply the named permitted setup, or leave the analysis skipped.
  Analysis commands never download or install software.
- Other failures mean **FAIL2: no accepted complete result**. Retain
  `WARNINGS.md`, `FAILURE.txt`, worker consoles, `runtime-logs` and raw outputs.
  Correct the reported cause and retry a **new** output directory. Existing
  output is preserved. Do not overwrite evidence or refresh saved fingerprints.
- A source `ConvergenceWarning` rejects the fit even when coefficients are finite.
  Do not silently change iterations, random seeds, identifiers, nonfinite values
  or covariance to obtain a result. Review an unresolved numerical failure before
  any dependent interpretation.
- Raise declared resource bounds where permitted instead of thinning genes,
  features or dependencies. `java_heap_mb` bounds the owned worker heap;
  `python_address_space_mb` separately bounds each Python process. `wall_ms`
  includes runtime verification. File/byte monitoring is not an OS disk quota,
  filesystem sandbox or network sandbox.
- PoPS 0.1 has different covariance and regression semantics from 0.2. Its
  selected source fails for exactly one selected feature. An empty selection
  can yield a **control-only** prediction when controls are supplied.
- Munging requires an explicit missing-value policy. `mean` can leave an
  entirely missing feature as NaN; `zero` does not remove infinities. Remaining
  nonfinite values are reported prominently. Preparation and selection alone
  are **not fitted models**.
- Only the parent-written root `COMPLETED.properties` admits the requested task
  under this workflow contract. A worker receipt, exit-zero substage or finite
  partial report alone is insufficient. Synthetic negative fixtures retained in
  the qualification contain deliberately forged markers; they are not results.

## Build and set up the original runtime

From the repository root on Windows, build the current executable:

```powershell
.\gradlew.bat executableJar --no-daemon --max-workers=2 --console=plain
```

Run the following setup inside permitted Linux/WSL, from the repository root.
The examples place local runtime, data and result directories beside the source
checkout. Choose permitted persistent paths and retain each runtime at its
original adopted location for replay. Keeping these files outside the source
checkout preserves exact source-membership verification and keeps them outside
the checkout's Gradle `clean` target.
The installer requires the selected **Pixi 0.83.0** executable, not an arbitrary
Pixi version. Its [official release archive](https://github.com/prefix-dev/pixi/releases/tag/v0.83.0)
has SHA-256
`064f371e692aaa3400ac581c89da2843c91437f20c2d9155395a5d0037f33551`.
If download is permitted, obtain and verify it in a new directory:

```bash
(
  set -eu
  mkdir ../pops-pixi-bootstrap
  cd ../pops-pixi-bootstrap
  curl -fL https://github.com/prefix-dev/pixi/releases/download/v0.83.0/pixi-x86_64-unknown-linux-musl.tar.gz -o pixi.tar.gz
  printf '%s  %s\n' 064f371e692aaa3400ac581c89da2843c91437f20c2d9155395a5d0037f33551 pixi.tar.gz | sha256sum --check -
  tar -xzf pixi.tar.gz
  chmod +x pixi
  printf '%s  %s\n' 50571451e96f4a82cf675cd83baaf66fff9b718f0aaf153900b9faf4c29bd401 pixi | sha256sum --check -
)
```

Stop if verification fails. If downloads/installations are forbidden, skip the
dependent analysis. The scripts can also use an already permitted executable
with the exact verified binary identity. Set up and adopt a new runtime:

```bash
python3 -B tools/literature/pops_original/install_runtime.py \
  --pixi "$(pwd)/../pops-pixi-bootstrap/pixi" \
  --out "$(pwd)/../pops-runtime-new" \
  --cache-dir "$(pwd)/../pops-package-cache" &&
python3 -B tools/literature/pops_original/adopt_runtime.py \
  --installed "$(pwd)/../pops-runtime-new" \
  --out "$(pwd)/../pops-adoption-new" &&
java -jar build/cli/omicsmatic-0.4.0.jar pops-original-tools --help
```

Keep all generated locks, notices and records. The selected versions are Python
3.8.20, NumPy 1.19.5, pandas 1.0.5, SciPy 1.5.2, scikit-learn 0.24.2 and
statsmodels 0.12.2. Setup/adoption readiness is not a scientific result. Adoption
records complete selected environment/source membership and resolved shared-library
files; it is not a complete OS image or a claim of executable authenticity.
Runtime membership and bytes are checked before and after source execution.

## Run the small original 0.2 example

The existing [synthetic input ZIP](../../examples/pops-workflow/synthetic-inputs.zip)
contains 72 genes and 11 features. Extract it into a **new** directory. On
Windows, this can be done before entering WSL:

```powershell
Expand-Archive examples/pops-workflow/synthetic-inputs.zip ../pops-original-example-new
```

The ZIP's existing `request.properties` is for the native command. In the
extracted directory, create a separate `original-request.properties` with this
content. Replace the runtime value with the absolute **Linux** path of the
successful adoption directory containing `runtime.properties` and `READY.json`:

```properties
profile=pops-original-v1
edition=0.2
task=fit
runtime=/absolute/linux/path/to/pops-adoption-new
genome_build=synthetic-no-biological-build
feature_set_id=synthetic-original-pops-control
target_source_id=synthetic-ridge-plain
java_heap_mb=512
python_address_space_mb=8192
wall_ms=900000
max_input_bytes=10000000
max_work_bytes=100000000
max_files=500
gene_annotation=annotation.tsv
feature_prefix=features
feature_chunks=1
target_type=custom
scores=scores.tsv
method=ridge
```

From the repository root inside Linux/WSL:

```bash
java -jar build/cli/omicsmatic-0.4.0.jar pops-original-tools \
  --request ../pops-original-example-new/original-request.properties \
  --out ../pops-original-result-new
java -jar build/cli/omicsmatic-0.4.0.jar pops-original-tools \
  --verify ../pops-original-result-new --out ../pops-original-replay-new
```

These synthetic inputs, request settings and genuine replay were executed during qualification. Only
one of its 11 features passes the source's default selection criterion. The
example is synthetic and supplies no biological evidence. Replays use captured
input bytes, including when the original caller paths are unavailable. They
check full bundle membership, completion declarations, implementation/runtime
identity, and genuinely recompute and compare scientific reports and bindings.
Use the original matching artifact and runtime for an old bundle. Retain the
actual executable JAR used for each completed analysis alongside its records.

## Request profiles and inputs

Every request requires `profile`, `edition`, `task`, `runtime`, the three caller
labels shown above and the six resource fields shown above. Paths are relative
to the request directory unless absolute. Caller genome-build, target-source and
feature-set labels are declarations, not independently verified provenance.

| Edition/task | Additional required fields | Result |
|---|---|---|
| 0.1 `select` | `features_text`, `magma_prefix` | Selected feature list; no model |
| 0.1 `predict` | Above plus `gene_location`, `control_features`, `selected_features` | Requested chromosome predictions |
| 0.1 `select-predict` | Above prediction fields except `selected_features` | Original selection followed by prediction |
| 0.2 `fit`, custom target | `gene_annotation`, `feature_prefix`, `feature_chunks`, `target_type=custom`, `scores` | Original ridge/OLS/LassoCV reports |
| 0.2 `fit`, MAGMA target | Feature/annotation fields, `target_type=magma`, `magma_prefix` | Original MAGMA-based fitting reports |
| 0.2 `munge` | `gene_annotation`, `feature_directory`, `nan_policy` | Prepared feature chunks; no model |

For 0.1, `chromosomes=all` is the default; an explicit ordered list such as
`chromosomes=1,2` requests those predictions. The selected original programs
still require MAGMA information for all 22 autosomes. `magma_prefix` names both
`.genes.out` and `.genes.raw`; features contain an `ENSGID` column, and gene
locations supply `ENSGID` and `CHR` (the qualification fixture also contains
`START` and `END`). Selected/control files contain
feature names. Do not supply an external selected list for `select-predict`.

For 0.2, annotation contains `ENSGID`, `CHR`, `TSS`. A feature prefix names
`.rows.txt`, and each `.cols.N.txt`/`.mat.N.npy` pair. Custom scores contain
`ENSGID`, `Score`. Optional fields include `score_covariates`, `error_covariance`,
`subset_features`, `control_features`, `max_selected_by_p`, `forward_steps`,
`projection_chromosomes`, `selection_chromosomes`, `training_chromosomes`, and
the corresponding `*_remove_hla` booleans. `method` is `ridge`, `linreg` or
`lasso`; `p_cutoff=0.05` and `random_seed=42` are source defaults. The original
CLI does not accept a `none` p-value cutoff. MAGMA requests can explicitly set
`use_magma_covariates` and `use_magma_error_covariance`. `save_matrix_files=true`
retains the original additional matrix reports. Unknown/incompatible fields fail.

Munging processes all visible feature-directory files in the source's filename
order, retaining compressed filenames. `nan_policy` must be `raise`, `ignore`,
`mean` or `zero`; `chunk_columns` defaults to 5000. Visible non-file entries,
ambiguous duplicate identifiers and unsupported identifier coercion are refused.
Identifiers must be unique, nonempty and free of whitespace/control characters.
No feature or gene is silently removed to meet capacity bounds.

## Qualification and retained evidence

The new artifact passes 270 Windows regression tests and 32 original-profile
contract tests on Linux, with no failures or skips. Original 0.1 controls cover
all 22 chromosomes and genuine replay, standalone selection, the single-feature
source failure, and empty-selection control-only prediction. The complete 0.2
matrix covers 17 successful original ridge/OLS cases, two expected original
failures and three replays. Additional controls cover two LassoCV designs with
saved matrices and replay, all four munging policies, source convergence refusal,
resource limits, tampered membership/declarations and self-consistent forged
scores rejected by genuine source recomputation.

The complete official tutorial retains 16,980 targets, 18,383 prediction genes,
508 input features and 258 selected features. The three scientific reports are
byte-identical to an independent invocation of the unchanged original program;
genuine replay and replay of that replay also match. Complete original feature
munging retains all 18,383 rows and 508 columns in two chunks and matches the
provided prepared example and replay. No genes/features were subsampled.

Earlier failures remain retained. Nine completion-declaration tests exposed the
old admission gap, a missing-lock control exposed incorrect FAIL2/SKIP3 handling,
and the first large-matrix harness omitted saved chromosome masks and alternate
covariance filenames. The corrected harness translates every recorded source
option; source data, numerical code and comparison criteria were not relaxed.

Read current source and public metadata, or check complete named private records:

```bash
python3 -B tools/literature/pops_original/records.py --public-only --out ../pops-record-public-new
python3 -B tools/literature/pops_original/records.py --out ../pops-record-full-new
```

Public-only success does not validate missing private records or runtime readiness.
Full saved-record readback needs the exact named private records and official
example inputs and performs no fresh fit or current-host runtime check. Missing
records produce SKIP3 with recovery instructions. Installed package trees/caches
are excluded from the archival scope; their as-run manifests are retained.
Hashes establish integrity, not signatures or authenticity.

The [earlier development checkpoint](pops-original-development/STATUS.json) is
frozen at `3e8d406ef207244f2843267348eab6d3c28fe7b5`. Its status describes that
historical checkpoint. The native workflow evidence remains independently frozen
at `05f085355756c0e29115727a91b68b04c6847a3f`. Do not refresh either earlier
record set to make it accept later source changes. The 250-paper project and
PoPS paper-role catalog admission remain in progress.
