# Palantir coordinate workflows: development adapter

This adapter runs original Palantir **1.4.4 or 1.4.6** for diffusion maps, trajectory analysis, or their combination. Java prepares strict coordinate inputs and audits the original observations and exported tables. Original nearest neighbors, ARPACK, random draws, waypoint/root/terminal selection and trajectory fitting remain upstream. The native audits are not independent trajectory refits. The separate [native multiscale component](PALANTIR_MULTISCALE_DEVELOPMENT.md) remains usable on Windows and Linux.

**Require exit0 and `analysis-status.json` with `result_admitted=true`. Read `WARNINGS.md` before using results. FAIL2 admits no case from a requested batch, including an earlier completed case. SKIP3 skips dependent analysis when the exact permitted runtime is unavailable.** Preserve originals, failed setup prefixes and provisional outputs; correct the named cause and retry new paths. RUN installs nothing. Never refresh an old scientific binding hash.

The adapter accepts prepared float64 PCA or multiscale coordinates. It does not normalize raw expression, select highly variable genes, infer a PCA recipe, fit gene trends, or infer paper parameters. The selected papers do not report a Palantir version; neither bound edition establishes historical paper reproduction. Cell Ranger remains excluded as requested. This addition does not finish the 250-paper semantic audit or all missing tool implementations.

## Obtain the development artifact

Use the artifact path and SHA-256 recorded in [the validation receipt](palantir-workflow-development/VALIDATION.json). Build locally from the project root using the bundled Python interpreter, or an environment capable of running the repository's literature build scripts:

```powershell
python -I -B tools/literature/build_palantir_workflow.py --out build/literature-review/palantir-runtime/NEW_BUILD
```

The build needs the previously validated development parent at its recorded path and Java21 at the configured build-tool path. It compiles the new classes for Java17, preserves every parent JAR entry and adds the new classes/resources. It leaves `build/cli/omicsmatic-0.3.7.jar` unchanged. This is a development artifact, not a release or a Pages deployment. Direct `java -cp` invocation is the supported command interface.

## Export and prepare the exact runtime

Replace `DEVELOPMENT.jar` with the receipt's actual artifact path:

```powershell
java -cp DEVELOPMENT.jar org.jlinalg.cli.PalantirSetupCli --export D:/absolute/NEW_PALANTIR_KIT
```

Export verifies the complete kit and installs nothing. Read its `SETUP.md`. Actual analysis requires Linux x86_64 Java, including WSL. The setup uses the project's separately admitted Python3.11.17 base read-only. If absent, prepare that base with the separate `org.jlinalg.cli.CellPhoneDbSetupCli` kit and its instructions; no CellPhoneDB analysis is run for Palantir. The new Palantir prefix must use case-sensitive storage.

From the exported kit in Linux:

```sh
python3 -I -B setup/prepare.py \
  --out /absolute/NEW_SETUP_RECEIPTS \
  --prefix /absolute/CASE_SENSITIVE/NEW_PALANTIR_RUNTIME \
  --base-runtime /absolute/BASE_SETUP/COMPLETED.json \
  --archive-cache /absolute/EXACT_ARCHIVE_CACHE \
  --allow-install
```

Add `--allow-download` only if missing pinned archives may be downloaded. Repeat `--archive-cache` for more caches. Setup uses exact archive hashes and versions, installs into the new Palantir prefix, verifies complete original namespaces and compares full base/runtime inventories. It preserves original scientific code and notices; regenerated installer RECORD files remain separately identified. It does not patch scientific functions or load a pickle.

If installation, downloads or the required environment are forbidden or unavailable, setup writes a prominent SKIP3 warning and dependent analysis stays skipped. Obtain a permitted compatible environment or dependencies, then use new paths. Setup FAIL2 admits no runtime. Read setup `COMPLETED.json`, `WARNINGS.md` and full logs. Hashes provide integrity checks, not a signed attestation.

## Prepare an explicit input and request

A CSV has literal `cell_id` as its first header, followed by distinct coordinate names:

```text
cell_id,PC1,PC2,PC3
cell-A,0.12,-0.45,0.03
cell-B,0.18,-0.39,0.07
```

This two-row illustration describes the format; it is too small for the example's neighbor/component settings. Supply your complete prepared coordinate matrix and scientifically chosen parameters. TSV is also supported. Cell IDs, component names and row order are preserved. Duplicate IDs, whitespace in numeric tokens, missing/nonfinite coordinates, float64 overflow and nonzero decimal underflow to zero fail; rows and identities are never silently repaired or dropped. Decimal conversion uses Java float64 and is serialized as explicit float64 for the original runtime. Original raw files are captured byte for byte.

For a combined workflow, save an explicit request such as:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "edition": "1.4.6",
  "runtime": "/absolute/NEW_SETUP_RECEIPTS/COMPLETED.json",
  "out": "/absolute/NEW_ANALYSIS",
  "python_hash_seed": 0,
  "analyses": [{
    "id": "explicit-coordinate-analysis",
    "method": "diffusion-trajectory",
    "input": {
      "path": "/absolute/prepared-pca.csv",
      "format": "csv",
      "representation": "pca",
      "interface": "dataframe",
      "source": "Describe the actual preprocessing, PCA provenance and supplied sample family here"
    },
    "global_seed": 71651,
    "threads": 1,
    "diffusion": {
      "n_components": 8,
      "knn": 10,
      "alpha": 0,
      "seed": 4,
      "kernel_backend": "scanpy",
      "n_eigs": 6
    },
    "trajectory": {
      "early_cells": ["YOUR_ACTUAL_EARLY_CELL"],
      "terminal_states": [
        {"cell": "YOUR_ACTUAL_TERMINAL_A", "annotation": "state-A"},
        {"cell": "YOUR_ACTUAL_TERMINAL_B", "annotation": "state-B"}
      ],
      "knn": 10,
      "waypoints": {"count": 24},
      "scale_components": true,
      "use_early_cell_as_start": true,
      "max_iterations": 25,
      "seed": 20,
      "empty_fates": "fail"
    }
  }]
}
```

All paths may be absolute or relative to the request's directory. The example's method parameters are controlled test settings, not parameters inferred from a paper or general biological recommendations. Every requested early, terminal and explicit waypoint ID must exist exactly in the input. A partially matched terminal family fails before execution.

| Choice | Meaning |
|---|---|
| `method: diffusion` | Provide PCA input and `diffusion` options; omit `trajectory`. |
| `method: trajectory` | Provide `representation: multiscale` and `trajectory` options; omit `diffusion`. |
| `method: diffusion-trajectory` | Provide PCA input and both option groups. |
| `interface: dataframe` | Use the original DataFrame interface. |
| `interface: anndata` | Use the original coordinate-only AnnData interface with an empty expression matrix; it does not introduce expression preprocessing. |
| `kernel_backend: scanpy` or `sklearn` | Select the actual source backend. The exact sklearn backend can drift relative to Scanpy and emits the original warning. |
| `n_eigs: null` | Use the original automatic eigengap choice; native checking defers by failure when a tied sort order cannot be represented unambiguously. Choose an explicit bound only with scientific justification. |
| Integer `n_eigs` | An exclusive eigenpair position, at least2 in this adapter. The leading component is excluded:6 returns five multiscale coordinates. `n_components` includes the leading component. |
| `terminal_states: null` | Request the original automatic terminal-state procedure. |
| Unannotated terminals | Use objects containing only `cell`. All terminals must be annotated or all unannotated. |
| `waypoints: {"cells": [...]}` | Supply distinct exact waypoint IDs instead of a count. The source adds boundary/root/terminal cells as applicable. |
| Multiple `early_cells` | Supported by1.4.6;1.4.4 requires one. |
| `empty_fates: retain-pseudotime-only` | Explicitly permit a source result with zero fate columns. Its warning states that zero entropy is not evidence of a differentiated state. The default example fails the whole batch instead. |

Choose capacities explicitly when needed. Defaults bound input bytes536870912, output bytes2147483648, files100000, original timeout1800seconds, native audit operations1000000000, cells5000, components256, waypoints1500, matrix values100000000 and sparse edges2000000. Each data-only JSON observation is limited to64MiB and4000000 parser elements; exceeding any complete-family limit fails rather than truncates. Supervision tracks owned Linux processes, wall time and sampled output footprint; it is not hard OS CPU/memory containment. Larger capacities can require more Java heap and original-runtime memory.

## Run and interpret the result

From Linux, using Linux paths to the artifact and request:

```sh
java -Xmx2g -cp /absolute/DEVELOPMENT.jar \
  org.jlinalg.cli.PalantirWorkflowCli --request /absolute/request.json
```

`--help` explains the contract. A batch is admitted only after all cases complete, all native checks pass, and original request/input/source/runtime bindings are rechecked. Earlier completed cases remain provisional if any later case fails. The saved status and warnings identify the cause and next action. A public command rejected before a valid new output is allocated reports the error on stderr and does not overwrite an existing output.

Retained outputs include raw input captures, prepared float64 JSON, exact source/profile copies, effective request, original logs and process receipts, data-only stage observations, native audit, full output manifest and final admission status. Per-case results include `multiscale.tsv` for diffusion modes, and `pseudotime.tsv` plus `fate-probabilities.tsv` for trajectory modes.

Native checks cover original input axes/values, observed kernel edge distances and adaptive radii, exponential/symmetric/density-normalized kernel equations, diffusion transition and eigenpair residuals, native multiscale weights, minmax scaling, version-specific observed Gaussian/softmax weights, Markov weights and absorbing-probability residuals, waypoint-to-cell projection, preclip entropy, clipping and complete TSV exports. They do not independently repeat neighbor search, shortest-path graph construction, RNG, terminal selection, boundary tie ordering or the trajectory fit.

**Do not silently renormalize returned fate rows.** Original `PResults` clips probabilities below0.01 after computing entropy and does not renormalize rows. The warning reports clipped states and rows below unit mass. AnnData annotated-terminal output uses annotation headers; the original DataFrame output retains cell-ID headers. Actual waypoint counts can differ from requested counts after source sampling and unions. Both distinctions are preserved.

## Validation and provenance

The linked validation receipt and `controls.zip` retain source pins, exact runtime setup,21 original workflows, native audits on three JVMs,11 corrupted-output controls,18 input/export/permission/timeout controls, a late atomic source failure and process logs. The Java audit checks that the complete captured worker output stays unchanged while auditing. Source and test failures from development remain retained and labelled; failed states are not replaced by a later passing result. The canonical executable and unrelated local work remain unchanged. Original code and MIT notices are bundled, with immutable source evidence in the preceding native-component proof. This guide's receipt establishes only the declared coordinate workflows and tested controls, not all Palantir functions, paper reproduction or completion of the larger project goal.
