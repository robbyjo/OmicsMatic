# Original Monocle3 1.3.3 prepared-graph workflow

This standalone Java adapter runs original `learn_graph()` on the complete
caller-prepared UMAP and declared cluster/partition factors. `RUN` also calls
original `order_cells()` with caller-declared roots and compares every cell and
projected-graph pseudotime with the existing native Java implementation. `LEARN`
saves the learned graph without admitting pseudotime.

**This is a development component, separate from the canonical executable.**
It addresses the reported graph-learning/ordering operations in catalog paper
P117. It does not complete the other Monocle roles in the nine observed papers,
and grants no new whole-tool or catalog-integration credit. The full 250-paper
implementation goal remains in progress.

## Exact source and supported runtime

The selected [official original source commit](https://github.com/cole-trapnell-lab/monocle3/tree/4b5cd9bf0dc525144723b0d41eb445cada7cd7c7)
declares Monocle3 1.3.3. It is a DESCRIPTION-change candidate, not a recovered
paper commit. The packaged profile pins all 207 source files, 80 package
versions, and seven original graph/construction/ordering function definitions.
The runtime must be Linux x86_64 R4.3.3 with the declared private libraries.
Existing Monocle3 1.4.27 and Monocle2 libraries remain separate.

Current host setup helpers are under `tools/literature/monocle133/`:

```powershell
python tools/literature/monocle133/setup_reference.py `
  --out build/literature-review/monocle-runtime/my-dependency-base `
  --allow-install

python tools/literature/monocle133/install_original.py `
  --base build/literature-review/monocle-runtime/my-dependency-base `
  --source-candidate build/literature-review/monocle-runtime/related-source-intake-1/monocle3-1.3.3 `
  --out build/literature-review/monocle-runtime/my-original-install `
  --allow-install
```

These setup recipes currently target the named WSL distribution
`JLinAlg-Omics-SAIGE-20261026`, the existing Pixi bootstrap under
`build/literature-review/saige-runtime/pixi-bootstrap-1/`, and a previously
captured source candidate containing `SOURCE.json`, `source.tar.gz`, and
`source/`. They are host-specific developer recipes, not general installers.
The dependency recipe now copies `reference-pixi.lock` and uses `--locked`;
this is the exact manifest/lock used by the validated R4.3.3 base. It prevents
a fresh solve from silently choosing newer, incompatible package versions.
An absent bootstrap, distribution, source candidate, permitted installation, or
matching package profile means **SKIP3: skip dependent original analysis**.
Retain the setup directory and inspect its `RESULT.json`/`WARNINGS.md` for the
named missing item. A dependency base containing Monocle3 1.3.1 alone does not
satisfy a 1.3.3 analysis. Do not substitute editions or patch original source.

## Build and portable saved comparisons

```powershell
python tools/literature/monocle133/build.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/literature-review/monocle-runtime/my-graph-build

python tools/literature/monocle133/verify.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/literature-review/monocle-runtime/my-saved-graph-check
```

Use a new output path for every attempt. The verifier rebuilds the current
Java source and checks six complete saved original graph populations. It repeats
no original graph learning and reloads no RDS. No upstream R installation is
needed for this portable mathematical comparison. It does not authenticate
the original producer or independently establish graph-learning correctness.

## Complete input tables

All tables are UTF-8 tab-separated files with literal identifiers and headers;
quoted-field interpretation is disabled. Preserve every supplied row and its
order. The gene and cell axes must match exactly:

| File | Columns and meaning |
| --- | --- |
| `counts.tsv` | `gene_id`, then every cell ID in cell-table order; raw nonnegative integer counts, never TPM or protein activity |
| `genes.tsv` | `gene_id`, `gene_short_name`; exactly the count-table gene axis |
| `cells.tsv` | `cell_id`, `cluster`, `partition`, `UMAP_1`, `UMAP_2`, optionally further consecutive UMAP dimensions |

Declare ordered factor levels explicitly. Cluster labels remain literal.
Partition levels must be the original numeric strings `1` through `K` in order;
unsupported labels are refused rather than silently recoded. No UMAP,
clustering, cell selection, count normalization, or biological root discovery
is substituted for the supplied preparation. The original constructor may
estimate its size-factor metadata; graph learning uses the supplied UMAP.

## Run original graph learning and ordering

Run the Java program **inside Linux/WSL**, using Linux paths in the JSON:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "version": "1.3.3",
  "count_units": "raw-integer-counts",
  "embedding_slot": "UMAP",
  "counts": "/path/to/counts.tsv",
  "cells": "/path/to/cells.tsv",
  "genes": "/path/to/genes.tsv",
  "out": "/path/to/new-analysis",
  "rscript": "/path/to/dependency-base/.pixi/envs/default/bin/Rscript",
  "r_libraries": [
    "/path/to/original-install/R-lib",
    "/path/to/dependency-base/.pixi/envs/default/lib/R/library"
  ],
  "source_root": "/path/to/source-candidate/source",
  "execution_policy": "ALLOW",
  "cluster_levels": ["stem", "upper", "lower"],
  "partition_levels": ["1"],
  "seed": 173,
  "use_partition": false,
  "close_loop": false,
  "ncenter": 130,
  "minimal_branch_len": 15,
  "root_pr_nodes": ["Y_1"],
  "require_terminal_roots": true
}
```

```bash
java -Xmx2g -jar /path/to/monocle133-graph-development.jar \
  --request /path/to/request.json
```

The example root and factors describe the synthetic controls, not a biological
recommendation. Declare a justified root for your analysis. You may provide
`root_cells` instead of `root_pr_nodes`; the original mapping from root cells
to principal nodes is retained. `require_terminal_roots=true` refuses roots
whose degree is not one. Repeated supplied roots remain represented.
Choosing the cell nearest to a desired principal root does not guarantee that
`root_cells` maps back to that root: the two original mappings are not inverses.
The terminal check is applied to the actual mapped principal nodes. For the
terminal-node workflow, declare an inspected terminal principal node explicitly.

To inspect a graph first, set `mode` to `LEARN` and omit both root fields. Read
`principal-graph.json`, choose roots explicitly, then rerun `RUN` with unchanged
inputs/seed and `expected_principal_graph_sha256` set to the SHA256 of that
file. `RUN` relearns the graph and refuses an unexpected fingerprint. No
direction is inferred from numeric node names. Source defaults apply to graph
controls other than the explicitly declared `ncenter` and `minimal_branch_len`.

Optional logical capacities are `maximum_input_bytes` (64 MiB by default),
`maximum_output_bytes` (1 GiB), `maximum_native_operations` (50 million), and
`timeout_seconds` (900). Full populations are refused at a capacity boundary;
prefixes are never analyzed as substitutes. The owned Linux supervisor records
hard per-process address-space/CPU/file limits, wall time, workspace size,
process/thread counts, sampled aggregate RSS, and cleanup. Sampled RSS is not a
hard aggregate memory quota. Java heap selection remains separate.

## Results, warnings, and recovery

**Use no partial output unless `analysis-status.json` admits it.**

| Status | Meaning and action |
| --- | --- |
| `graph_admitted=true`, `result_admitted=true` | Original graph, ordering, required saved-state roundtrip, and complete Java distance/root comparison passed |
| `graph_admitted=true`, `result_admitted=false` | Successful `LEARN`; choose roots explicitly before using any pseudotime |
| `SKIP3`, exit 3 | Required runtime/installation/execution unavailable or forbidden; provide the named permitted prerequisite, then retry a new directory |
| `FAIL2`, exit 2 | Input, source, runtime integrity, scientific, process, or capacity failure; retain diagnostics, resolve the named cause, and retry a new directory |

Successful `RUN` retains full input tables, caller parameters, exact original
source, runtime fingerprints, complete `cds.rds`, before/after RNG states,
principal and projected graphs, original `cell-pseudotime.tsv`, package/session
information, process receipts, warnings, and the native all-cell/all-vertex
comparison. `Inf` for unreachable cells remains `Inf`; no disconnected cell is
dropped. Finite distance comparisons use absolute tolerance `1e-12` plus
relative tolerance `1e-11`; selected root-cell indices and infinity categories
must agree exactly.

Save/reload checks compare count and coordinate axes/values, cluster state,
principal/projected graph attributes, all auxiliary model fields, and
pseudotime. igraph private cache-environment identity is not scientific graph
data and is recreated by serialization. This does not certify every external
nearest-neighbor pointer or every downstream Monocle operation.

The separate `--audit-graph PREPARED_GRAPH_JSON --out NEW_DIRECTORY` command is
portable and checks only supplied graph ordering against its supplied original
reference arrays. It admits no original graph-learning or biological claim.
Integrity fingerprints are not producer signatures.

Validation receipts and complete synthetic controls are in
[`monocle133-graph-development/`](monocle133-graph-development/README.md).
Earlier failed attempts remain retained in the private build evidence.
