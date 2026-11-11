# scFates tree, root and pseudotime on supplied coordinates

**The original CPU coordinate adapter is implemented and validated.** Its
development artifact is separate from the canonical published executable.
Catalog availability and counts are tracked separately from this execution
checkpoint. The 250-paper implementation goal remains active.

The adapter executes unchanged **scFates1.2.5 / SimplePPT1.1.7** with an exact
50-wheel Linux runtime. It fits a CPU Euclidean principal tree, chooses an
explicit node or supplied observation-based root, and computes likelihood or
probabilistic pseudotime projections. Java checks input identity, observed
fitting equations, retained nodes, weighted root distances, source segments,
projection equations, categorical milestones and exported rows. Initialization,
MST/RNG, random mapping draws and NumPy sort ties execute upstream. This is not
an independent trajectory refit or a biological/whole-paper reproduction.

**Warnings must be reviewed before using results.** Original NaN/Inf scores,
zero-mass assignments, nonconvergence, isolated-node removal and missing
milestones are never repaired. A source exception, timeout or failed audit
rejects the whole requested batch, including earlier completed cases. Original
milestone-color indexing can fail after provisional pseudotime was computed;
those observations and earlier CSV files remain unadmitted.

P094 reports scFates after Palantir diffusion-space construction, including
200-node UMAP and 3,000-node individual diffusion settings. Its paragraph does
not report the package edition or establish this guide's root, iteration budget
or random seeds. Supply your actual prepared coordinates and scientifically
declared choices. Neither normalization, feature selection, PCA, diffusion-space
construction nor UMAP is inferred by this adapter. See the
[paper-role catalog guide](PALANTIR_CATALOG.md) and the separate
[earlier arithmetic component checkpoint](SIMPLEPPT_NATIVE_DEVELOPMENT.md).
That historical checkpoint predates the complete execution adapter.

## Prepare the runtime separately

Use the exact development JAR from the build receipt. It is separate from the
canonical published executable. Java source targets Java17+; the original
execution adapter runs through Linux x86_64 Java, including WSL. Native arithmetic
and data-only auditing are portable. Validation records actual JVM versions.

```sh
java -cp DEVELOPMENT.jar org.jlinalg.cli.ScfatesSetupCli --export /existing/parent/NEW_SCFATES_KIT
```

Read the exported `SETUP.md`. Export installs nothing. First prepare the exact
read-only project Python3.11.17 base using the separate CellPhoneDB setup kit if
necessary; no CellPhoneDB analysis runs. Then, from the exported kit on Linux:

```sh
python3 -I -B setup/prepare.py \
  --out /existing/parent/NEW_SETUP_RECEIPTS \
  --prefix /existing/CASE_SENSITIVE_PARENT/NEW_SCFATES_RUNTIME \
  --base-runtime /absolute/BASE_SETUP/COMPLETED.json \
  --archive-cache /absolute/EXACT_WHEEL_CACHE \
  --allow-install
```

Every `NEW_...` directory must be new. Repeat `--archive-cache` for additional
caches. The kit's two original wheels are checked automatically. Add
`--allow-download` only when downloading missing exact archives is permitted.
No new versions are resolved. Setup checks original source/license members,
complete imports and both complete runtime trees. Worker code is bound
separately from runtime installation; changing a worker does not authorize
refreshing an old runtime profile or inventory.

**SKIP3 means dependent analysis stays skipped.** Read the named dependency,
platform, filesystem or permission error in setup `WARNINGS.md` and full logs.
Supply compatible software in a permitted environment and retry new paths, or
retain the skip. **RUN never downloads or installs.** A missing or unadmitted
runtime receipt produces SKIP3 rather than an invented substitute result.

## Explicit request

CSV/TSV inputs are strict UTF-8 without a BOM, with a literal `cell_id` header, unique nonempty
cell IDs and complete numeric coordinate columns. Labels are not trimmed,
normalized, renamed, joined or reordered. Literal `NA`/`null`, Unicode and
properly quoted delimiters, quotes, tabs and line breaks remain data. Unsupported
control characters, malformed UTF-8 and unpaired surrogates fail explicitly.
Numbers must be finite decimal float64 values: missing tokens, overflow and
nonzero tokens underflowing to zero fail without imputation.

Save this small **software demonstration**, not biological data, as `coordinates.csv`:

```csv
cell_id,axis1,axis2,axis3
cell_A,0.2,0.01,0.03
cell_B,0.4,0.071,0.05
cell_C,0.6,0.138,0.07
cell_D,0.8,0.202,0.09
cell_E,1.0,0.283,0.11
cell_F,1.2,0.36,0.13
```

Save `request.json` alongside it, replacing the runtime path and choosing a new
output directory. Paths are resolved relative to the request file. Node root0
is an arbitrary software choice; it is not a biological root or cell ID.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "edition": "scfates-1.2.5_simpleppt-1.1.7",
  "runtime": "/absolute/NEW_SETUP_RECEIPTS/COMPLETED.json",
  "out": "NEW_SCFATES_RESULT",
  "analyses": [{
    "id": "demonstration",
    "method": "tree-root-pseudotime",
    "input": {"path": "coordinates.csv", "format": "csv", "source": "Six-point software demonstration"},
    "global_seed": 8223,
    "tree": {"nodes": 2, "sigma": 0.1, "lambda": 1, "metric": "euclidean", "nsteps": 50, "err_cut": 0.00001, "seed": 11},
    "root": {"mode": "node", "node": 0, "tips_only": false, "min_val": false},
    "pseudotime": {"n_map": 1, "n_jobs": 1, "seed": 27},
    "policies": {
      "nonconvergence": "retain-with-warning",
      "nonfinite_diagnostics": "retain-with-warning",
      "zero_assignment_rows": "fail",
      "removed_nodes": "fail",
      "missing_milestones": "retain-with-warning"
    }
  }],
  "timeout_seconds": 1800,
  "maximum_matrix_cells": 25000000,
  "maximum_audit_operations": 2000000000,
  "python_hash_seed": 0
}
```

```sh
java -Xmx3g -cp DEVELOPMENT.jar org.jlinalg.cli.ScfatesWorkflowCli --request request.json
```

Require **exit0 and `analysis-status.json` with `result_admitted=true` together**.
Then read the console warnings, `WARNINGS.md`, `native-audit.json` and original
stdout/stderr. `worker/CASE_ID/pseudotime.tsv` has exact cell order and columns
`cell_id,t,seg,edge,milestones`, plus `t_sd` when `n_map>1`. Fields are quoted;
missing milestones are empty fields, not fabricated labels. Data-only snapshots
retain original types, categorical levels, all projections, random states and
primitive binary arrays with complete shapes and SHA-256 hashes. No user pickle
or arbitrary Python object is loaded.

## Weights, initial nodes and observation roots

Optional analysis-level `weights` uses the same `{path,format,source}` descriptor.
Its cells and coordinate labels must match the coordinate file exactly in order.
Finite signed weights are passed literally; no reweighting recipe is inferred.

Optional `initialization` uses the same descriptor with a `node_id` header and
matching coordinate labels. **Original SimplePPT uses the supplied node matrix
and overwrites its local `Nodes` value with the coordinate dimension.** The
wrapper preserves that source behavior and reports actual fitted/retained node
counts separately from the source local value. Do not infer node count from a
log message; inspect the request and native audit. No initializer is resampled,
padded or repaired.

For an observation-based root, replace the root object with:

```json
{"mode":"obs","values":{"path":"root-values.csv","format":"csv","source":"Declared root covariate"},"tips_only":true,"min_val":true}
```

The file needs `cell_id` plus one finite numeric value, with exact input cell
order. Its original header is retained in prepared metadata; the upstream
container uses the adapter's `root_signal` field. The original assignment-weighted
average chooses the root. Its zero mask
and ties can select a non-tip even with `tips_only=true`; that literal choice
is retained with an explicit warning. Inspect recorded root scores, and declare
an explicit node in a new run if scientifically required. Root node positions
refer to the retained graph, not cell IDs or pre-removal node positions.

## Source conventions and recovery

| Condition | Interpretation and required action |
| --- | --- |
| **FAIL2 / source exception** | Admit no batch, including earlier completed cases. Preserve all originals, provisional files and logs. Correct the named cause and retry new paths. A retain policy cannot repair an exception. |
| **SKIP3 / missing dependencies** | Skip dependent analysis. Obtain compatible permitted setup or retain the skip. Do not run an approximate substitute. |
| **Nonconvergence** | The policy uses the original strict signed-coordinate-sum error cutoff, not a Euclidean change norm or biological certificate. Original final-iteration logging can print conflicting messages; retain both and inspect actual error/iteration values. |
| **NaN/Inf scores** | Literal correlation across coordinates and `R*log(R)` can be nonfinite. Preserve values and inspect the score definitions. `retain-with-warning` is explicit permission to retain them, not repair or certification. |
| **Zero assignments / removed nodes** | No fallback probabilities or renormalization are added. Inspect retained-node positions and affected rows; change scientific choices only on a new request. |
| **Root-edge directional rounding** | Original sklearn distances can differ slightly by direction. Both values remain saved. The native check follows SciPy1.11.4's CSR-plus-transpose scan on a positive tree; it never averages or rewrites source weights. General asymmetric graphs are outside this profile. |
| **Projection edges** | Original scFates broadcasts the first distance scalar to every graph edge, then takes the first tied one-hop neighbor. This differs from root-distance weighting and is preserved literally. |
| **Tied segment reassignment** | NumPy performs the actual sort. Java verifies the bounded tied-choice family rather than inventing a tie break. Actual source-selected labels remain unchanged. |
| **Missing milestones / palette IndexError** | Midpoint and single-cell segment states can remain missing. The requested policy governs successfully returned missing states. An original palette exception rejects the whole batch; do not generate replacement colors or admit partial pseudotime. |
| **Capacity, timeout or cancellation** | No partial result is admitted. Inspect complete process receipts, correct the named resource/request cause and retry new paths. |

Limits apply to complete inputs, assignments, dense node systems, mapping families
and charged audit operations. Dense node storage grows quadratically. Raising
limits requires enough memory for the entire analysis; supervision covers owned
processes, wall time and sampled output size, not hard OS memory/CPU quotas.
Optional R/GAM, GPU and unrelated scFates methods remain outside this prepared
coordinate workflow.

The [validation manifest](scfates-workflow-development/VALIDATION.json) binds the
exact artifact, source snapshots, original software, failed attempts and full
control outputs. Nine public workflows match 83,789,804 array states from the
independent frozen original executions, including exact random states and
scientific observation columns. Native workflow checks pass on Windows Java21,
Windows Java25 and Linux Java21. The same production tree-scan helper matches
21,593 compiled SciPy distance states across 31 positive-tree controls on each
JVM. Identifier/CSV, corruption, setup/skip, later-source-failure, policy,
timeout, output-limit and live-cancellation checks retain explicit outcomes.
The exact six-point CSV and scientific JSON above were executed successfully.

The directional-distance review retains exact official
[sklearn1.3.2 source](https://github.com/scikit-learn/scikit-learn/blob/1.3.2/sklearn/metrics/pairwise.py)
and [SciPy1.11.4 source](https://github.com/scipy/scipy/blob/v1.11.4/scipy/sparse/csgraph/_shortest_path.pyx).
Native consistency checks and SHA-256 manifests do not establish independent
trajectory refitting, authenticity or biological validity.
