# Original CellChat network embedding and clustering

This Java controller runs unchanged CellChat 1.1.3 `netEmbedding`, `runUMAP`,
`netClustering` and original plots on a **complete verified
[legacy comparison](CELLCHAT_LEGACY_WORKFLOW_DEVELOPMENT.md)**. It retains both
functional and structural network results. P085's Extended Data Figure 7d
explicitly describes joint functional pathway embedding and clustering
([paper](https://doi.org/10.1038/s41588-024-01961-x), PMC11631771).
The author notebook contains corresponding calls in unevaluated chunks;
it does not establish historical execution or an exact historical environment.

**Require exit 0, `result_admitted=true`, successful `--verify`, and review of
`WARNINGS.md` and the captured parent's warnings.** This is a standalone
development checkpoint. Combined CellChat paper-role review and catalog/root
integration remain pending. Counts remain **93 new scoped integrations,
96 including existing adapters, and 248 missing tools**. The full 250-paper
implementation goal remains incomplete.

## Warnings and required actions

| Outcome or warning | Required action |
| --- | --- |
| `SKIP3` / exit 3 | **Skip dependent analysis.** Supply the named exact runtime where permitted. If installation is forbidden or unavailable, keep the analysis skipped. Analysis installs nothing. |
| `FAIL2` / exit 2 | **Admit no result.** Preserve the attempt, correct the named cause and retry a new output directory. Earlier successful steps in that attempt remain unadmitted. |
| Original parallel RNG warnings | Original automatic clustering calls `future_sapply` without declaring `future.seed`; warnings report potentially invalid random-number use. Preserve them and the original clusters. Transport success does not validate the random draws. An explicitly requested sequential variant is a separate analysis. |
| UMAP reproducibility warning | Original `runUMAP` sets source seeds but omits the UMAP constructor's `random_state`. Separate default runs produced different coordinates and co-clustering. A seed or sequential R clustering does not establish exact whole-pipeline replay. Preserve each result; do not silently patch the source. |
| Parent numerical warnings | The prepared parent retains 27 negative p-value fields, six duplicate neighbor entries, two omitted strictly closer neighbors and four similarity entries outside `[0,1]`. These are counts of exported fields/entries. Do not clip, rescale, deduplicate or interpret invalid p-values as valid estimates. |
| Original plot boundary placement | In the structural control plot, the upper SPP1 and lower FGF labels touch or partly cross the plotting boundary. Original PDFs are retained without repair; visual review does not make them publication-ready. |

Warnings and actions are printed even when a failure occurs before output
creation. Owned processes are stopped and awaited on timeout. Failed outputs
have no admission; any unsuccessful quarantine is explicitly reported.
Logical capacities do not enforce host RAM/disk quotas or authenticate producers.

## Run a complete parent and then postprocess it

1. Prepare and verify a complete 1.1.3 parent using the legacy guide. Its
   full bundle must be available; an arbitrary RDS or similarity table is
   insufficient. Group/condition order and original source warnings are retained.
2. Prepare the permitted private runtime below. Copy the
   [request template](cellchat-network-development/example/request.template.json)
   to your working directory. Set `out` to a **new absolute directory**,
   `parent_bundle` to the **absolute complete verified parent directory**, and
   `runtime_profile` to the exported network profile. Relative paths resolve
   against the request file. Keep every scientific choice explicit.
3. Run with Java 17 or later:

   ```powershell
   java -Xmx2g -jar docs/literature/cellchat-network-development/cellchat-network-javac21.jar --request C:/work/cellchat-network/request.json
   java -Xmx2g -jar docs/literature/cellchat-network-development/cellchat-network-javac21.jar --verify C:/work/cellchat-network/result-new
   ```

   Check both exit codes and the warning files before using results. Saved
   complete bundles can be verified on Windows or Linux without R or Python.
   New original analyses require the tested Windows runtime.

## Prepare a runtime where installation is allowed

The tested candidate is original CellChat 1.1.3 commit
`fea1c8ecdea0f393d32513cd00b866f8b4457839`, Windows R 4.6.1,
reticulate 1.47.0, **private future 1.23.0 / future.apply 1.8.1** and Python
3.13.13 with umap-learn 0.5.12, NumPy 2.5.4, SciPy 1.18.1, Numba 0.68.0,
llvmlite 0.50.0 and scikit-learn 1.9.1. This tested candidate is not a recovered
paper environment. The newer shared future installation lacks `multiprocess`;
the private compatible pair preserves the original default branch.

First complete the legacy guide's original source/library setup and export
`build/literature-review/cellchat-runtime/legacy-runtime-profile-1/runtime-profile.json`.
The network setup helper consumes that exact prerequisite. Restore the original
future archive intake from this capsule into the expected **new** directory:

```powershell
python tools/literature/verify_cellchat_network_evidence.py --materialize evidence/network-dependency-intake-1 --out build/literature-review/cellchat-runtime/network-dependency-intake-1
python tools/literature/setup_cellchat_network_reference.py --out build/literature-review/cellchat-runtime/network-reference-setup-new --allow-install
```

The setup currently expects an existing private Python at
`build/omics-cell-tools-env/Scripts/python.exe`. Supply the exact permitted
dependencies there before setup. If this is unavailable, **skip network
analysis**; setup health is not analysis admission. The helper installs the
two hash-bound original R archives only into the new private setup library
and checks that previous libraries remain unchanged. Installed libraries,
compilers and the host Python installation are excluded from the public capsule.

Inventory the existing Python and export a new profile:

```powershell
python tools/literature/inventory_cellchat_network_python.py --out build/literature-review/cellchat-runtime/network-python-inventory-new --python build/omics-cell-tools-env/Scripts/python.exe
python tools/literature/export_cellchat_network_runtime.py --out build/literature-review/cellchat-runtime/network-profile-new --setup build/literature-review/cellchat-runtime/network-reference-setup-new --python-inventory build/literature-review/cellchat-runtime/network-python-inventory-new/python-files.json
```

Set the request's profile to the resulting `network-profile-new/runtime-profile.json`.
The controller prescribes the inventoried interpreter, disables managed Python
discovery/install, hashes selected R/Python files before and after execution,
and directs Numba cache writes into owned output. It never falls back to UWOT,
another metric or sequential clustering automatically. Host/OS libraries outside
the declared inventory are not authenticated.

## Explicit source semantics and outputs

The [typed schema](../../src/main/java/org/jlinalg/cli/CellChatNetworkRequest.java)
accepts only `umap-learn`, correlation distance, the original transposed
similarity orientation and `kmeans`. `pathway_remove: null` preserves original
R `colSums(similarity)==1` removal; an explicit list requests literal pathway
removal. No approximate equality or similarity rescaling is introduced.
`n_neighbors: null` forwards the original `ceiling(sqrt(N))+1` argument;
`neighbors_argument` records the argument, not a guarantee that upstream UMAP
will not truncate it. `k: null` uses original eigengap selection. Explicit `k`
uses original fixed clustering. `parallel` selects the original branch
explicitly. At least four retained features are required by this bounded profile.

Outputs include complete captured parent, requests/runtime inventory, original
stdout/stderr, before-matrices, feature dispositions, transposed UMAP inputs,
coordinates/clusters, centroids/sizes/within-cluster sums of squares, original
S4 objects and requested original PDFs. Native Java checks order, values,
selection, matrix orientation, finite coordinates and cluster summaries.
It does **not** independently refit UMAP/kmeans or establish biological validity.

## Retained validation

Four complete controls cover both compiler builds, default parallel and explicit
sequential automatic clustering, plus explicit removal/neighbors/min-distance/
seed/fixed-k/no-plot choices. Separate original-R checks compare exported
coordinates, labels and plot data, and refit sequential clustering from captured
embedding/RNG states. Parallel clustering and UMAP are not claimed to replay.
The first timeout regression exposed an output-lock race; its failed evidence
is retained alongside the corrected process-cleanup checks. Original plots,
earlier prototypes and stochasticity observations remain retained.

The evidence verifier checks checksummed source, original package/dependency
archives, complete controls and failed attempts. It can materialize an exact
prefix into **new paths**, after which the standalone Java `--verify` must still
succeed to verify analysis admission. Hashes establish integrity, not signatures.

```powershell
python tools/literature/verify_cellchat_network_evidence.py --check-current-source
```

Primary article text and the unlicensed author notebook remain private. The
public source addendum retains source identifiers/hashes and factual role notes.
No historical paper reproduction, release, hosted deployment or completion of
all CellChat paper roles is claimed by this checkpoint.
