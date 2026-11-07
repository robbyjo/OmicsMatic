# Monocle tools root command

The separate root development JAR now exposes two explicitly scoped interfaces:

```text
java -jar PATH_TO_ROOT_DEVELOPMENT_JAR monocle-tools --help
java -jar PATH_TO_ROOT_DEVELOPMENT_JAR monocle-tools ORDER --request order.json
java -Xmx1g -jar PATH_TO_ROOT_DEVELOPMENT_JAR monocle-tools RUN --request run.json
```

**Development availability:** use the artifact built by `tools/literature/build_monocle_root.py`. The canonical/released JAR is unchanged. The root build replaces only the root dispatcher class and adds two facade/ORDER classes. Every native scientific API, original R worker, runtime guard, dependency profile and original source resource from the previously validated workflow artifact is preserved byte for byte. An exact inverse of the two routing/help edits independently recompiles to the parent root class. This does not award Monocle catalog integration credit or establish whole-paper reproduction.

## Choose the appropriate contract

**ORDER needs no R installation.** It runs native Java ordering on a caller-prepared projected cell graph. You must already have the actual cell/principal coordinates, principal roots, projected graph vertex order and edges. It does not learn a graph, embed expression data, choose a biological origin, or fit gene models. It implements the original Monocle3 helper behavior reviewed for exact source profiles 1.0.0, 1.3.1 and 1.4.27; a profile declaration does not establish that the full historical package was run.

**RUN invokes original Monocle3 1.4.27.** Its raw-count input, typed metadata, complete numerical options, model/spline terms and result contract are unchanged from the [validated original workflow](MONOCLE3_WORKFLOW_DEVELOPMENT.md). Supply the exact declared Windows R 4.6.1/private package profile. The analysis command installs nothing. Missing/forbidden runtime provision means **SKIP3: skip the dependent analysis**. Unsupported historical editions are never silently replaced. The classpath interface remains available in its earlier development artifact; the root command adds direct access to that same worker.

Read the [executed root example](../../examples/monocle-tools/README.md) for both invocations and expected synthetic values. All paths inside JSON resolve against the request file. On Windows keep the outer request/JAR paths ASCII; internal paths and literal identifiers support UTF-8. Output must be NEW.

## Native ORDER request

```json
{
  "schema_version": 1,
  "mode": "ORDER",
  "source_profile": "1.4.27",
  "prepared_graph": "prepared-graph.json",
  "out": "ordered-result",
  "maximum_graph_bytes": 67108864,
  "maximum_native_operations": 50000000
}
```

The graph file has exactly `cell_ids`, `cell_coordinates`, `principal_ids`, `principal_coordinates`, `roots`, `graph_vertex_ids`, `edge_weight_mode`, and `edges`. Coordinate arrays contain one row per corresponding ID; cells and principal points share a finite coordinate dimension. IDs are literal, nonempty, unique within each axis, and free of control characters. Spaces, quotes, Unicode and the literal string `NA` are preserved. `roots` names supplied principal points and may repeat. Cells may be a subset of projected graph vertices, with extra projection vertices retained in their separately declared order.

Declare `edge_weight_mode: "weighted"` and give each edge `from`, `to`, and finite nonnegative `weight`. Alternatively declare `"unweighted"` and give edges only `from` and `to`; unit weights are then explicit. Endpoints are zero-based indices in `graph_vertex_ids`. Loops and parallel edges are retained; ordering uses undirected distances. No weight mode, graph vertex order or root population is inferred. Unknown fields, including expected-output fields, are rejected.

Each principal root maps to the **first nearest cell in the supplied cell order**, using the source's naive squared Euclidean arithmetic and tie behavior. Pseudotime is the minimum graph distance from those selected cells. Principal-root-to-cell Euclidean distance is not added to pseudotime. Repeated roots retain separate selection ledger rows.

`maximum_graph_bytes` defaults to 64 MiB and may be at most 256 MiB. `maximum_native_operations` defaults to 50 million. The native API additionally limits populations/dimensions. These are logical bounds, not an OS memory quota; supply appropriate heap/resources. Inputs with one coordinate dimension and multiple principal-root selections are refused because the original helper genuinely errors in that domain.

## Admission, warnings and recovery

Require exit 0, a published bundle, and `analysis-status.json` with **`result_admitted: true`**. A manifest or an R return alone is insufficient. **FAIL2 admits no partial result**. A failed RUN case also rejects earlier completed cases in the same batch. Preserve every failed attempt, then correct its named cause and use NEW output paths.

ORDER retains request/graph bytes, before/after source hashes, cell and graph values in separate declared orders, one-based output ordinals, zero-based nearest-cell indices, distance/pseudotime tokens and explicit kinds. JSON and quoted TSV preserve literal IDs. `Inf` remains `positive-infinity`, including disconnected cells. Naive root-distance overflow and path-sum overflow remain visible warnings. Do not rescale, impute, rewrite IDs, drop cells or replace nonfinite values to force success. Output meanings and tie/overflow controls are documented in the [native API guide](MONOCLE3_PSEUDOTIME_DEVELOPMENT.md).

RUN retains all original warnings/errors and complete model objects/CDS directories, including required nearest-neighbor indices. Preserve entire save directories. Its current Gaussian controls return original `FAIL` statuses and are unadmitted; do not relabel them as valid Gaussian results. Original `subbars` dependency warnings and earlier `IterableMatrix` installation messages remain recorded. Disk-backed inputs are not validated. Coefficient q-values retain original Holm adjustment, and stock Moran tests do not implement a paper's modified covariate model. See the [workflow recovery instructions](MONOCLE3_WORKFLOW_DEVELOPMENT.md).

Canonical/released availability, historical editions, Monocle2 differential testing, custom paper tests, Cicero, py-monocle, batch alignment, supplied activity/embedding representations and remaining paper reviews are separate outstanding work. Root routing, complete reference controls and checksum verification do not certify biological findings. Cell Ranger remains excluded. No portable product VERIFY command is introduced.
