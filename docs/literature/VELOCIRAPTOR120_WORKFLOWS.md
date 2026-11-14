# Original velociraptor 1.20.0 workflows

**Review warnings before using a result.** The original R namespace can emit a native
`stack imbalance` diagnostic whose cause remains unresolved. The adapter retains it;
successful comparisons do not establish that it is harmless. R condition counts do
not count every native or Python console diagnostic. Raw-console totals remain
unavailable, and every run requires warning review.

Use the explicit Python source entry points below to run the unchanged Bioconductor
R package. Numeric execution uses its original R preprocessing, S4 dispatch,
SingleCellExperiment/AnnData conversion and managed scvelo 0.3.3 backend. The separate
R-only plotting profile reads supplied complete fit/projection inputs and never
installs or initializes a Python fitting backend. These are Linux/WSL workflows;
they are not bundled commands in the released Java CLI.

The selected profile is velociraptor 1.20.0, R 4.5.3, Python 3.12.10, NumPy 2.2.6,
Pandas 2.2.3 and SciPy 1.16.3. Plot dependencies are ggplot2 4.0.4, patchwork 1.3.2
and metR 0.19.0. Locks, original source identities and complete runtime inventories
are checked rather than accepting an arbitrary installed version.

[P228](https://pmc.ncbi.nlm.nih.gov/articles/PMC12696504/) describes the velociraptor
R interface for RNA velocity and reports downstream latent-time comparisons. It
does not identify the wrapper or Python edition, mode or exact calls. The selected
profile and synthetic parameters are explicit project choices. The paper's
Velocyto name beside a velociraptor URL remains a source/link discrepancy. No
paper result, biological interpretation or convergence is reproduced by these controls.

## Set up permitted dependencies

Use an allowed Linux/WSL environment with Python 3, Pixi and uv. If a manager or
dependency cannot be installed or executed under the operating environment's
policy, report **SKIP3**, name the missing requirement and skip dependent analysis.
Do not install globally or substitute another software edition to obtain a pass.

From the repository root, choose NEW writable paths. The following commands show
the same locked-base and standalone-interpreter procedure used in validation:

```sh
velociraptor_repo="$PWD"
velociraptor_setup="$PWD/build/NEW-velociraptor-setup"
mkdir "$velociraptor_setup"
mkdir "$velociraptor_setup/r-base" "$velociraptor_setup/plot-base"
cp tools/literature/velociraptor120/reference-pixi.toml "$velociraptor_setup/r-base/pixi.toml"
cp tools/literature/velociraptor120/reference-pixi.lock "$velociraptor_setup/r-base/pixi.lock"
cp tools/literature/velociraptor120/reference-plots-pixi.toml "$velociraptor_setup/plot-base/pixi.toml"
cp tools/literature/velociraptor120/reference-plots-pixi.lock "$velociraptor_setup/plot-base/pixi.lock"
pixi install --manifest-path "$velociraptor_setup/r-base/pixi.toml" --locked --no-config
pixi install --manifest-path "$velociraptor_setup/plot-base/pixi.toml" --locked --no-config
uv python install 3.12.10 --install-dir "$velociraptor_setup/python" --no-bin

python3 tools/literature/velociraptor120/install_runtime.py \
  --r-base "$velociraptor_setup/r-base" \
  --python-base "$velociraptor_setup/python/cpython-3.12.10-linux-x86_64-gnu" \
  --out "$velociraptor_setup/numeric" --allow-install --allow-download

python3 tools/literature/velociraptor120/install_plots.py \
  --r-base "$velociraptor_setup/plot-base" \
  --out "$velociraptor_setup/plots" --allow-install --allow-download
```

Run each next command only if the preceding command succeeded. Use installation
and download flags only where permitted. For offline numeric installation, supply
the exact original `--source-archive` and a complete permitted `--wheelhouse`
instead of `--allow-download`. Offline plotting installation needs the exact
`--source-archive`. The install tools refuse missing permission or prerequisites;
analysis itself installs nothing. Read each setup's `WARNINGS.md` and terminal
record. Readiness means installed prerequisites, not an analysis result.

## Run the six complete examples

The self-contained archive retains the original synthetic generator, full inputs,
caller parameters and saved fit/plot calls. It contains all 160 cells and 12 genes,
including literal `NA` and Greek identifiers. Preparation checks every member and
executes no R code:

```sh
python3 examples/velociraptor120/prepare.py \
  --provider "$velociraptor_setup/numeric/provider" \
  --plot-provider "$velociraptor_setup/plots" \
  --out "$velociraptor_repo/build/NEW-velociraptor-examples"

python3 tools/literature/velociraptor120/run.py \
  --request "$velociraptor_repo/build/NEW-velociraptor-examples/stochastic-request.json"
python3 tools/literature/velociraptor120/run.py \
  --request "$velociraptor_repo/build/NEW-velociraptor-examples/dynamical-request.json"
python3 tools/literature/velociraptor120/run.py \
  --request "$velociraptor_repo/build/NEW-velociraptor-examples/project-grid-request.json"

python3 tools/literature/velociraptor120/figures.py \
  --request "$velociraptor_repo/build/NEW-velociraptor-examples/plotVelocity-all-panels-request.json"
python3 tools/literature/velociraptor120/figures.py \
  --request "$velociraptor_repo/build/NEW-velociraptor-examples/plotVelocityStream-plain-request.json"
python3 tools/literature/velociraptor120/figures.py \
  --request "$velociraptor_repo/build/NEW-velociraptor-examples/plotVelocityStream-colored-request.json"
```

Run the examples independently: a failed numeric example does not grant permission
to repair its inputs or substitute a plotting result. The examples use a retained
synthetic fit for plotting and projection; they do not silently consume a partial
fit from a previous command. The dynamical 15-iteration budget is a caller choice,
not a convergence test. Rendering uses explicit dimensions, DPI, background and
seed; complete graph RDS and complete SVG/PNG files are retained.

## Supply a study script

Start from an example request and provide canonical paths, an exact permitted
provider, a NEW output directory, complete input provenance, intended population,
explicit capacities and the exact trusted R script. Set `execution_policy` and
`trusted_code_policy` to `ALLOW` only for permitted execution. These scripts run
with account permissions; process-group and capacity supervision is not an OS
sandbox or a total CPU/RSS enclosure.

The script receives `input_directory`, `result_directory`, `input_provenance` and
`analysis_population`. Assign the full original return to `analysis_result` and
the exact declared marker to `analysis_complete`; assert the intended rows,
identifiers and design yourself. Declare every additional result file. A marker
or prose description cannot validate arbitrary R code. Numeric capture retains
the full original RDS first and then every supported R-value-tree field. Figure
mode retains the complete original graph and both complete renderings; it does
not claim graph environment/pointer identity or opaque RDS byte parity.

## Interpret failure and recover

**File presence is not success.** Check the exit code, `analysis-status.json`,
`WARNINGS.md` and `MANIFEST.json`. A complete result requires successful terminal
admission and an intact complete manifest. **SKIP3** skips dependent analysis for
missing or forbidden prerequisites/execution. **FAIL2** admits no complete result.

If a final status cannot be saved, the console ends with **FAIL2** and, where
writable, `FINALIZATION_ERROR.json` records failure. Saved warnings are changed to
FAIL2 and their manifest hashes are rebuilt. If warnings, manifest, status and
fallback records are all blocked, retain the full console. Resolve the named
filesystem error and rerun into a NEW writable output directory; do not treat a
previously computed figure or RDS as admitted. Filesystem recovery instructions
apply to recording failures; original scientific errors require resolving their
reported input, parameter or dependency cause.

Keep complete raw consoles, original condition objects/calls, available caller
scope, Python last-error reports, full RDS returns, partial value trees, native
temporary copies and every diagnostic-error file. Native temporary links and IPC
nodes are metadata; live streams and terminated process memory are not recovered.
Preserve missing values, numeric NA/NaN payloads, infinities, identifier encodings
and deliberate caller subsets. Never drop failed cases or change saved hashes.

Original named-coordinate streamline calls can fail with a DFrame column-name
error. That failure remains a failure; do not rename coordinates automatically.
The separate successful streamline examples use unchanged original PCA coordinates
and their matching projected vectors. Earlier NumPy/Pandas incompatibilities and
SciPy 1.18.1 within-original pseudotime variability remain retained historical
failures. The selected SciPy 1.16.3 comparisons do not prove universal reproducibility.

The [development record](VELOCIRAPTOR_ADAPTER_DEVELOPMENT.md) and current catalog
describe qualification status. The [native Java grid component](VELOCIRAPTOR_GRID120_NATIVE.md)
is separately available and computes neither RNA velocity nor latent time. The
full 250-paper review and implementation objective remains open.
