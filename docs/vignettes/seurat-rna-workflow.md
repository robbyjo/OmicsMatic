# Captured original Seurat RNA workflow

`org.jlinalg.cli.SeuratWorkflowCli` executes the original **Seurat 5.6.0** RNA
workflow from a closed JSON request: LogNormalize and VST variable features
or SCTransform v2; optional typed covariate regression; scaling; PCA;
nearest-neighbor/SNN graphs; clustering; and optional UMAP/t-SNE. A native
Java SNN API and surrounding arithmetic audits check the captured output.
Start with the [copyable synthetic example](../../examples/seurat-rna-workflow/README.md).
Use a current source-build JAR, Java 17+ and a separately prepared permitted
exact R profile. The existing public 0.4.0 release predates this entry point.

This is an unsplit RNA workflow. Existing native preprocessing, marker-test
and SCT interfaces remain separate and unchanged. Integration, WNN, anchor
transfer, Harmony, module scores, cell-type annotation and portable UMAP
projection models are separate work. The source profile does not reproduce
historical author editions or full papers. Seurat remains method-overlap in
the catalog pending individual source-role review and cumulative admission.

## Requests and input identities

Run `--help` or supply `--request UTF8_JSON`. All unknown fields and
method-inapplicable settings are refused. Relative paths resolve against the
request file, not the shell's working directory. Outputs must be new paths.
The example is a complete request; this table describes its settings.

| Field | Meaning and restrictions |
|---|---|
| `schema_version`, `mode`, `profile` | Version 1; `RUN` by default; fixed `Seurat-5.6.0-R4.6.1-RNA-workflow` profile. VERIFY/REPLAY have separate requests below. |
| `counts`, `features`, `cells` | Uncompressed Matrix Market **coordinate integer general**, features by cells, plus one exact ID per line in matrix order. Counts and row/column totals must be exact nonnegative integers within 2^53−1. |
| `metadata`, `regressors` | Optional exact ordered TSV, first column `cell`. Regressors declare `name`, `kind: numeric` or `factor`, and explicit ordered `levels` for a factor. Regression requires metadata. No imputation, inferred donors, pseudobulking or reordered cells. Extra metadata is retained and explicitly reported as unmodeled. |
| `normalization`, `variable_features` | `LogNormalize` or `SCT-v2`; variable-feature target defaults to 2,000. The complete source feature disposition is recorded, including source exclusions. |
| LogNormalize options | Integral positive `scale_factor` (10,000), `scale_center`/`scale_scale` (true), `scale_max` (10). Fractional scales are rejected instead of silently undergoing the original integer coercion. VST uses span 0.3 and automatic clipping. |
| SCT v2 options | `sct_cells` (5,000), `sct_min_cells` (5). Explicit `glmGamPoi_offset`, corrected UMI counts and full retained-feature residuals; no silent alternate fitting backend. LogNormalize scaling options are not SCT options. |
| `pca` | `solver: source_default`, `irlba` or `exact`; `components` (50), `weight_by_variance` (true). Only explicit IRLBA accepts `tolerance` (1e-5) and `max_iterations` (1,000). Reverse PCA is outside this profile. |
| `neighbors` | Strictly increasing 1-based PCA `dimensions`; `k` (20), `method: annoy` or `rann`, `prune` (1/15). Annoy has `trees` (50) and `metric` (euclidean/cosine/manhattan/hamming); RANN has `epsilon` (0) and uses Euclidean distance. Requested dimensions must exist; k cannot exceed cell count. |
| `clustering` | `algorithm`: 1 Louvain, 2 multilevel refinement, 3 SLM, 4 Leiden; unique positive `resolutions` (0.8), `iterations` (10), `group_singletons` (true). Algorithms 1–3 use `starts` (10). Leiden uses explicit `leiden_backend` and `objective` (modularity/CPM); it does not use starts. |
| `umap` | `enabled` (true); uwot only, two components, `neighbors` (30), `epochs` (500), `metric` (cosine/euclidean/manhattan), `min_distance` (0.3), `spread` (1). Neighbors must be below cell count. No saved projection model. |
| `tsne` | `enabled` (true); original Rtsne, two dimensions, `perplexity` (30), `iterations` (1,000; at least 250), `theta` (0.5). Requires `3*perplexity <= cells-1`. |
| `seed` | Nonnegative integer, default 42. R RNG kind, source warnings and the observed PCA branch are recorded. R worker threads are set to one. A seed does not guarantee identical coordinates after input or host changes. |
| Runtime/capacities | Explicit `rscript`, nonempty `r_libraries`; timeout/input/output/feature/cell/stored-entry/dense-entry/work/graph bounds. Full RNA dense population and possible Gram allocation are checked before launch. Work bounds cover Java audits and input-based estimates; they are not a count of all external floating-point operations or an OS RAM quota. |

Source RNA construction uses no initial min-cell/min-feature filtering.
Duplicate count coordinates are combined by exact integer sums with a named
warning, and original tokens remain captured. Public feature/cell IDs remain
opaque and exact; safe f#/c#/v#/l# codes isolate them from Seurat name handling.
`prepared-inputs.json` records the mapping. Unknown, duplicate, empty or
control-character IDs and unmatched metadata are refused.

The tested Leiden profile is **igraph 2.3.4, modularity**. The `leidenbase`
selector has an exact dependency guard and a genuine missing-package SKIP3
control; positive execution with leidenbase 0.1.36 has **not** been qualified.
Do not infer support validation from that selector or silently switch its
requests to igraph. Likewise, declared configurable metrics/objectives are
not all individually benchmarked by the six tested profiles.

## Runtime and source behavior

The admitted runtime has R 4.6.1; Seurat 5.6.0; SeuratObject 5.4.0; Matrix
1.7.5; jsonlite 2.0.0; future 1.80.0; irlba 2.4.1; RANN 2.6.3;
RcppAnnoy 0.0.23; and igraph 2.3.4. SCT additionally requires sctransform
0.4.3/glmGamPoi 1.24.0, UMAP requires uwot 0.2.5, and t-SNE requires Rtsne
0.17. Missing or incompatible prerequisites skip the affected whole request.
Disabled embeddings accept no unused options and require no corresponding
additional package. The CLI does not install or alter runtime libraries.

Seventy-four installed Seurat R function bodies/formals must match the
[pinned official source](https://github.com/satijalab/seurat/tree/75dfa8603befe31505e6287d32dc86c607732bd6).
This does not authenticate native DLLs, every transitive dependency or the
host. The source/reference directory retains source files, hashes and notices.

- Source-default dense approximate PCA first attempts EigenGramPCA. A
  process-local observer delegates its identical arguments/results and is
  restored after PCA. Original RunPCA bodies/formals remain unchanged. If the
  original silently catches a Gram error and uses IRLBA, this workflow records
  the fallback and emits a named warning. Explicit IRLBA options bypass Gram.
- Exact `prcomp` centers again by default. Its full SD spectrum is preserved,
  rather than truncated to the requested embedding dimensions. Approximate
  and exact rank bounds and weighted/unweighted embeddings remain distinct.
- SNN uses shared-neighbor Jaccard, retains weights equal to the prune
  threshold, retains applicable self/diagonal edges and does not create
  zero-overlap edges at threshold zero. Native `SeuratSnn.compute` accepts
  distinct, in-range, zero-based k-neighbor rows and returns the full sorted
  directed edge population, with explicit work/edge/cancellation refusals.
- Multiple modularity resolutions use the original multi-resolution routine.
  Cluster labels, including possible `singleton`, remain literal. Output
  columns map to their declared resolutions; labels are not cell types.

## Outputs, verification and replay

Read `WARNINGS.md`, `analysis-status.json` and `diagnostics.json` first.
Completed captures include public-ID PCA/UMAP/t-SNE embeddings (when enabled),
PCA loadings, clusters and a feature ledger covering **every input feature**.
`worker/` retains original RDS, normalized/scaled layers, full SD spectrum,
PCA input, graphs, source warnings, versions, stage records and logs. Safe
worker codes are not repaired external identities; use the captured mapping.
Java VERIFY does not deserialize RDS files.

```json
{"schema_version":1,"mode":"VERIFY","analysis":"path/to/capture"}
```

VERIFY checks full file membership/hashes, original/captured/effective
requests, exact input preparation and source resources, final process status,
warnings/recovery guidance and published identities. It independently checks
LogNormalize and no-regression scaling when applicable, PCA input/projection/
norm/orthogonality relations and the complete SNN overlap/pruning calculation.
It does not independently refit VST/regression/SCT, nearest-neighbor search,
clustering, UMAP or t-SNE, authenticate native DLLs, or defeat coordinated
fabrication of all inputs/evidence/verifier code. SHA-256 provides integrity,
not producer authentication or biological truth.

```json
{"schema_version":1,"mode":"REPLAY","analysis":"path/to/capture","out":"new/replay"}
```

REPLAY verifies the old capture, retains it unchanged, and executes the
original models again with the captured scientific inputs/settings. Optional
`rscript` and `r_libraries` overrides are explicit and captured. Exact
coordinates are not promised across hosts or input changes. In qualification,
the same-machine replay's selected published views were byte-identical.
An older prototype with covariates differing by only floating-point rounding
produced substantially different regression t-SNE coordinates. That discrepancy
is retained; exact-input reference comparison is reported separately.

## Prominent warnings and recovery

**SKIP3 — DEPENDENT ANALYSIS SKIPPED:** no complete result is admitted. Read
the named missing/incompatible runtime, library, package or backend. Supply
the exact permitted prerequisite in a new private library where installation
is allowed, or leave that analysis skipped. Never change a frozen/shared
library or substitute the requested backend to obtain a success status.

**FAIL2 — NO RESULT ADMITTED:** keep inputs, partial RDS/tables, warnings and
logs. Correct the named identity, metadata, model, numerical, integrity,
timeout/output or capacity cause, then use a new output path. A zero original
exit, finite-looking partial table or generic transaction manifest alone is
insufficient. Require `results_admitted: true` and successful VERIFY.
Do not silently drop cells, replace NaN/Inf, cap P values or hide source
filters/convergence warnings. A failed/skipped capture cannot pass VERIFY.

The worker's owned process tree is cleaned up after timeout/cancellation;
cleanup outcomes are captured. Only this request's owned handles are targeted.
Original errors preserve a partial source object when serialization remains
possible. Explicit bounds can fail before launch or during capture/auditing;
partial outputs remain evidence and must not be reported as completed analysis.

See the [qualification packet](../literature/seurat-workflow-development/README.md)
and [checkpoint](../literature/SEURAT_WORKFLOW_CHECKPOINT_2026-11-19.md) for the
tested combinations, refusals, reference discrepancies and remaining work.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Satija Lab (2026) — Seurat documentation and tutorials](../CITATIONS.md#seurat-api-documentation)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
