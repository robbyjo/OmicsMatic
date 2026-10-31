# scVI model workflows

**Validated development build: exact original scvi-tools 1.3.0 Linux x86_64 CPU workflows.** These examples contain synthetic data. One epoch demonstrates transport and auditing; it does not establish convergence, biological accuracy, a paper's preprocessing, or a reproduction of a published analysis. Historical environments and the complete toolbox goal remain open.

Use the current source build of OmicsMatic. The older released JAR does not contain this command. In the repository root, build with `./gradlew executableJar` on Linux or `gradlew.bat executableJar` on Windows. The result is `build/cli/omicsmatic-0.3.7.jar`.

## Runtime setup and required action

**This profile requires Linux x86_64 Java, Python 3.12.3 and original scvi-tools 1.3.0 with CPU PyTorch 2.6.0+cpu.** On Windows, run Linux Java inside WSL. Windows Java, CUDA, macOS, ARM and historical scvi-tools 1.0.3/1.2.0 environments are not substituted. If the exact permitted environment is unavailable, the affected analysis must remain skipped.

Installation is optional when you already have a compatible private runtime. When installation is permitted, use an existing Python **3.12.3** interpreter and a new private virtualenv. The example below runs from this directory. Replace `/absolute/path/to/python3.12` with that interpreter, and `$HOME/omicsmatic-scvi130` with a new writable location. These commands install only into that private environment.

```bash
set -eu
SCVI_HOME="$HOME/omicsmatic-scvi130"
mkdir "$SCVI_HOME"
/absolute/path/to/python3.12 -c 'import sys; assert sys.version_info[:3] == (3,12,3), sys.version'
/absolute/path/to/python3.12 -m venv "$SCVI_HOME/venv"
"$SCVI_HOME/venv/bin/python" -m pip install --no-index --no-deps --require-hashes -r bootstrap-requirements.txt
"$SCVI_HOME/venv/bin/python" -m pip install --no-index --no-deps --no-build-isolation --require-hashes -r requirements-cpu.txt
"$SCVI_HOME/venv/bin/python" -m pip check
```

The two requirement files record the **82 exact official artifacts** used by the existing private validation installation. All downloads use explicit original artifact URLs and SHA-256 hashes; no package index or unlisted dependency is selected. The runtime list includes source distributions, so bootstrap packages are installed first and build isolation is disabled. `pip check` verifies the complete installed dependency set. Artifact provenance is preserved in `runtime-artifacts.json`. A fresh private installation of this artifact set passed `pip check` without changing the shared standard library. Installation into another environment still needs the `CHECK` operation below: package versions and hashes alone do not certify runtime behavior or authenticate the installation/host.

**If download, installation or execution is forbidden, stop the affected analysis.** Ask the environment owner to supply a permitted compatible private runtime, or keep its status skipped. Do not replace the original model with another method or version. A missing, forbidden, unsupported or untrusted required capability uses exit **3**. Input, integrity and execution errors use exit **2** and retain the failed attempt. Fix the named cause and use a **new output directory** for the next attempt.

## Run the examples

Copy this entire folder into a writable workspace, preserving its `data` subdirectory. In each recipe you use, replace the `python` value with your private environment's absolute `venv/bin/python` path. Recipe input paths are relative to the recipe file; output paths come from `--out`. There is no environment-variable expansion in recipe values.

From the repository root, with the copied recipe folder at `/absolute/path/to/scvi-workflow`, first run:

```bash
java -jar build/cli/omicsmatic-0.3.7.jar scvi-tools \
  --recipe /absolute/path/to/scvi-workflow/check.json \
  --out /absolute/path/to/scvi-workflow/check-run-1
```

`CHECK` fingerprints the actual private software and performs fixed synthetic SCVI, SCANVI and SysVI training/reload health checks. It does **not** analyze your study data. A receipt from an earlier process is not automatically reused as live admission.

Then choose a recipe. Every invocation needs a new `--out` path and performs live runtime admission.

| Recipe | Requested behavior |
| --- | --- |
| `fit-scvi.json` | Fit SCVI to the supplied raw counts; retain all 32 cells and eight genes. |
| `transfer-scanvi-shared.json` | Select shared genes on both datasets before fitting, train SCVI and SCANVI reference models, then transfer eight query cells. |
| `fit-sysvi-normal.json` | Fit SysVI with a normal prior to signed, synthetic gene-centered expression. |
| `fit-sysvi-vamp.json` | Fit SysVI with five explicitly reviewed VampPrior cell indices. |
| `query-saved-scanvi.template.json` | Map against a separately reviewed saved SCANVI reference, with explicit gene padding/removal. Complete source trust fields before use. |

For example:

```bash
java -jar build/cli/omicsmatic-0.3.7.jar scvi-tools \
  --recipe /absolute/path/to/scvi-workflow/transfer-scanvi-shared.json \
  --out /absolute/path/to/scvi-workflow/transfer-run-1
```

Each dataset supplies Matrix Market **genes by cells**, one exact gene/cell identifier per line, and a metadata TSV whose first column is `cell_id`. Metadata is joined by exact ID, then written in matrix cell order. Literal strings such as `NA` remain strings. Selected continuous covariates are admitted as float32 with an explicit conversion policy. All matrix cells remain present. A raw-count cell made empty by gene selection fails with a named cause; signed zero rows remain present. No normalization, HVG selection, cell filtering or identifier/species conversion is performed automatically.

`category_order` can declare the reference's reviewed category order, including unused categories. The example reference order is `type_B, type_A, Unlabeled`. Saved-reference queries use the actual saved registry. `query_bridge.rename` requires explicit source-to-reference field renames; `query_bridge.values` requires explicit typed value mappings when reference categories are numeric or otherwise differ from source strings. Integer `0` is distinct from string `"0"`. The target layer and mapping review are mandatory, and continuous units are never converted automatically.

## Saved references and results

**An original saved model can execute pickle code. Never authorize loading from a hash alone.** Review the genuine model source, its precise SHA-256, declared family/release, and your decision permitting original pickle execution. Fill those fields in `query-saved-scanvi.template.json`; its placeholder hash and declined permission deliberately prevent execution. A model produced by your own completed run still needs an explicit review when reused in a later public invocation. No third-party model is loaded by the bundled examples.

The saved reference from the transfer example is `transfer-run-1/reference-analysis/run/result/reference-model/model.pt`. Supply that actual file and its reviewed hash in the saved-query template. The query's separate gene list intentionally differs from the fitted reference. Native projection records every retained, padded and removed identifier, both index mappings, every cell and its before/retained/dropped sum.

Read these root files first:

- **`WARNINGS.md`** contains prominent status, original warning messages, diagnostic paths and required action.
- **`analysis-status.json`** records exit status and whether the entire requested model workflow completed. A completed reference stage is not proof that a later requested query stage succeeded.
- **`manifest.json`** exists only after native admission of the entire requested workflow; `CHECK` explicitly records no user model analysis.

Each analysis stage retains latent TSVs, class probabilities/hard labels when applicable, full training split membership, metric histories, original saved/reloaded models, complete tensor/registry inspection, owned-process ledgers and original stdout/stderr. Native checks compare every cell, learned class order, probability mass, first-argmax hard label and complete float32 reload values. Class aliases `class_0`, `class_1`, etc. follow the exact typed `classes` array in the stage admission/manifest. Preserve that mapping; do not alphabetize or coerce it.

**A failed/skipped attempt is retained for diagnosis and has no successful root manifest.** Its root status marks the requested workflow incomplete. Do not use partial products as a successful result. Supply the named missing capability or correct the exact cause, then rerun to a new output path.

## Scientific and resource limits

Raw-count SCVI/SCANVI and signed-expression SysVI are separate model families. Review the actual input layer, preprocessing, gene selection and cell population. The signed demonstration only centers synthetic genes; it does not implement P062's reported Pearson normalization. This profile does not reproduce a paper's unspecified settings, historical runtime, Python 3.10 environment or full analysis.

Original SysVI 1.3.0 chooses automatic VampPrior pseudoinput **cell** indices with the **gene** count as its upper bound. That can fail or restrict the candidate pool. The explicit indices in this example are a reviewed different choice. An automatic original failure is retained without clamping or changing the bound. SysVI also disables KL warmup; its recipes explicitly require `NONE`.

`limits.preset: bounded-scvi-v1` supplies bounded defaults; optional capacities can be lowered or raised explicitly. They cover encoded/decoded inputs, cells/genes/entries, sorting, retained-memory reservations, model bytes, output rows/columns and process/workspace limits. Hard AS/CPU/file/nofile limits apply per original process; aggregate RSS is sampled and wall/workspace/process/thread counts are monitored. Source integrity is checked before/after execution. This is not an OS security sandbox. Read the recorded limits and ledgers for the exact scope of each bound.

See [development status](../../docs/literature/SCVI_DEVELOPMENT.md) for validation evidence and remaining gates, and the original [SCVI](https://docs.scvi-tools.org/en/1.3.0/api/reference/scvi.model.SCVI.html), [SCANVI](https://docs.scvi-tools.org/en/1.3.0/api/reference/scvi.model.SCANVI.html) and [SysVI](https://docs.scvi-tools.org/en/1.3.0/api/reference/scvi.external.SysVI.html) APIs for the original model behavior.
