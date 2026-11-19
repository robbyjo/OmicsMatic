# Ordered original Scanpy file workflows

Use `java -cp CURRENT_SOURCE_BUILD.jar org.jlinalg.cli.ScanpyWorkflowCli
--request request.json` for explicit current CPU operations. The separate
[native preprocessing API](scanpy-preprocessing.md) remains available and
never starts Python. This workflow selects original Scanpy execution
explicitly; it has no native/other-package fallback.

The runnable [synthetic example](../../examples/scanpy-workflow/README.md)
contains the full request, exact input hashes and package versions. The
qualified scientific sequence is the 13-step example, including Seurat-flavor
HVGs, PCA, neighbors, Leiden, UMAP, gene scoring and Wilcoxon markers. The
supplied-H5AD sequence also qualifies a literal nested predicate, preserved
supplied embeddings, grouped embedding density and gene scoring, with six
additional original-array comparisons. Other
allowlisted dispatch entries are development functionality requiring further
original-program/host qualification. Current dispatch is not whole-Scanpy,
historical author-runtime, whole-paper or biological reproduction. See the
[current handover](../literature/HANDOVER_CURRENT.md) for qualification receipts
and remaining work. Global command dispatch and cumulative admission are open.

## Declare an actual ordered program

A RUN request has `schema_version:1`, `mode:"RUN"`,
`profile:"Scanpy-1.13.0-CPU-v1"`, `python`, `out`, `input` and `steps`.
Relative file paths resolve against the request file's directory. Every output
directory must be NEW. The native API's 1.9.3 profile does not establish a
1.9.3 upstream workflow; this file workflow requires the declared current
profile. Historical editions and author settings are not silently substituted.

`input.format:"matrix_market"` requires a general real/integer coordinate
matrix, `dtype:"float64"`, explicit `orientation:"features_by_cells"` or
`"cells_by_genes"`, and `cells`/`features` TSVs. Their first columns are exact
`cell` and `gene` identities. Every additional column is declared in
`obs_columns` or `var_columns`: `"float64"`, `"bool"`, `"string"`, or
`{"levels":["A","B"],"ordered":false}`. Numeric-looking identities and category
labels remain strings. Unknown/missing categories, duplicate coordinates and
duplicate identities are refused; no coordinate summation or name repair occurs.
Float64 is an explicit MatrixMarket transport choice, not a claim about the
paper's original integer/float32 arithmetic.

`input.format:"h5ad"` instead requires only `matrix`, pointing to a captured
AnnData file. Its original dtype, raw object, layers, metadata and supplied
representations are retained. Eager decompressed data and complete dimensions
are bounded before/after loading. External/soft and aliased/cyclic HDF5 links
are outside the self-contained capture contract. Nonfinite numeric input,
duplicate identities and unsupported metadata control characters are refused.

Each `steps` entry has an exact `operation`, JSON `params`, and where required
an explicit `input_kind` (`raw_counts`, `log1p`, `provided_transformed` or
`provided`). This declaration records supplied context; it does not establish
the historical provenance of a caller-provided matrix. No arbitrary Python,
callable metric, automatic recipe or function-spelling correction is accepted.
The pinned current profile uses `rng`, not an alias for historical
`random_state`. Randomized operations require an explicit integer seed.

| Operations | Explicit choices that matter |
| --- | --- |
| `pp.filter_cells`, `pp.filter_genes` | Exactly one non-null criterion; inclusive original thresholds |
| `pp.calculate_qc_metrics` | Explicit requested percentages/QC fields; source stored-zero behavior retained |
| `pp.normalize_total`, `pp.log1p`, `pp.scale` | Exact target, base, layer, centering and clip settings; no inferred 10K/CPM |
| `pp.highly_variable_genes`, experimental HVGs | Required flavor/input declaration; batching, cutoffs and n-top semantics belong to the original source |
| `pp.pca`, `pp.neighbors`, `tl.leiden`, `tl.umap` | Exact representation, mask, solver, graph, backend and random seed |
| `tl.score_genes`, cell-cycle scores | Exact genes, pool, raw/layer, control policy and seed |
| `tl.rank_genes_groups` | Explicit method, raw use, tie correction, correction method, n-genes and mean/log semantics |
| Remaining development dispatch | Regression, Pearson residuals, downsampling, Scrublet, t-SNE, diffusion/PAGA/draw-graph and density need further workflow qualification |

The source/API documentation is the [official preprocessing reference](https://scanpy.readthedocs.io/en/stable/api/preprocessing.html)
and [tools reference](https://scanpy.readthedocs.io/en/stable/api/tools.html).
The installed 1.13.0 source/profile, rather than changing online defaults,
governs execution. Omitted scientific arguments use that pinned source's
literal defaults under `scanpy-v1`; the step ledger records the original
signature and actual arguments. This does not infer an author's defaults.
The worker explicitly uses `out=False` to store upstream results in AnnData.

`snapshot_raw` and `copy_layer` preserve caller-selected stages and refuse
existing destinations; `set_X` explicitly copies a named layer into X.
`sqrt` implements a literal selected square-root stage, with nonfinite
outputs rejected. `subset` accepts `axis:"cells"|"genes"` and either a leaf
`field`/`operator`/`value`, or a `predicate` tree. Leaves support `lt`, `le`,
`gt`, `ge`, `eq`, `in` and `not_in`; trees use `all`, `any` and `not`.
An AND exclusion remains an AND exclusion, for example:

```json
{"operation":"subset","params":{"axis":"cells","predicate":{"not":{"all":[{"field":"n_genes_by_counts","operator":"lt","value":300},{"field":"total_counts","operator":"lt","value":500}]}}}}
```

Missing selected predicate values refuse the result. Literal inequalities and
paper transformation order remain explicit; the caller must not turn “most
highly expressed” into “highly variable”, OpenTSNE into Scanpy t-SNE, or an
integrated supplied object into a newly fitted generic RNA analysis.

## Results, verification and recovery

RUN captures the exact request and all input bytes before starting the owned
Python. It checks the exact 14 package versions and 143 installed Scanpy source
files. These are bounded source/version checks, not native-binary authentication.
Every ordered step records before/after shape, dtype, storage, identity/matrix
fingerprint, selected arguments and original warnings. Unsorted original
neighbor indices are retained; duplicate indices are refused without sorting
or summing. The original H5AD retains storage and raw/layer state; the exported
final MatrixMarket is a numeric features-by-cells view.

VERIFY is Python-free. It checks the complete captured file population/hashes,
packaged producer/profile, source/effective requests, input pins, ordered step
chain, recorded versions/command, exact ordered retained identities, exported
dimensions, finite numeric values and marker probability ranges. It does not
independently refit models, fully interpret H5AD, authenticate coordinated
forgeries or establish the author's execution. REPLAY first verifies the old
capture and executes the original requested program again into NEW output,
with a recorded link to the prior seal. It needs the exact permitted runtime;
host/stochastic byte equality is not promised.

Defaults bound 100,000 cells, 100,000 genes, 20 million stored and dense entries,
1 GiB estimated workspace and 2 billion estimated work units per operation.
There are at most 64 ordered steps. The owner bounds input/output bytes,
runtime, file population and the lifetime of only its owned worker/descendants.
Capacity failures do not authorize population truncation. Live cache monitoring
tolerates vanished temporary nodes; the completed capture seal remains strict.

**Read `WARNINGS.md`, `analysis-status.json`, `worker/report.json`,
`worker/status.json` and logs before using results. SKIP3 means the exact
permitted dependency is missing or execution/installation is unavailable:
skip that dependent analysis and restore the named item. FAIL2 means no
complete result is admitted.** Preserve inputs, original errors, warnings,
diagnostic partial H5AD and logs; correct the named cause and retry NEW paths.
Source NaN/Inf or undefined numeric annotations are not made valid merely
because final X looks finite. Do not replace them or alter saved hashes.
Installing into a shared bound runtime or silently choosing another backend
is not a recovery action. Cell Ranger remains excluded.
