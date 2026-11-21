# Original Monocle3 1.0.0 graph learning and ordering

The standalone `org.jlinalg.cli.Monocle100GraphCli` runs unchanged original
`learn_graph` and `order_cells` from Monocle3 package1.0.0, commit
`004c096f3a3bdaf1e6709a65fb36f40ba775f1ab`, on complete caller-prepared CDS input.
Existing native Java pseudotime independently checks the exported original
ordering. Start with the [copyable example](../../examples/monocle100-graph/README.md)
and read the [qualification checkpoint](MONOCLE100_GRAPH_CHECKPOINT_2026-11-21.md).

**This is a prepared-input graph stage.** It does not preprocess counts, learn an
embedding or clusters, infer roots, fit a gene test or reproduce a whole paper.
P021 reports **UMAT**; the original graph functions require literal **UMAP**.
The adapter refuses substitution. Package1.0.0 is an edition of Monocle3.

## Commands and inputs

```text
Monocle100GraphCli RUN --request JSON
Monocle100GraphCli VERIFY --capture PATH --out NEW
Monocle100GraphCli REPLAY --capture PATH --out NEW --runtime JSON
```

Invoke with `java -cp /path/to/omicsmatic-0.4.0.jar` followed by the class name and
arguments. RUN/REPLAY use Linux Java and the selected Linux R runtime. Actual
packaged qualification used Linux Java21; R-free VERIFY also passed Windows
Java21/25. Source targets Java17. No root-dispatcher registration or additional
family/catalog credit is claimed at this checkpoint.

RUN requires a closed JSON object with `schema_version:1`, `source_cds`, `runtime`,
`operation`, literal `reduction_slot:"UMAP"`, explicit `seed`, `dataset_provenance`
and NEW `out`. Paths resolve relative to that JSON. Existing output and overlap
with input, parent captures or protected runtime paths are refused.

| Operation | Additional required settings | Result |
| --- | --- | --- |
| LEARN | `use_partition`, `close_loop`, `learn_graph_control`, `convergence_policy` | Original graph; no roots or pseudotime |
| ORDER | `graph_provenance` and exactly one `root_cells` or `root_pr_nodes` list | Orders the complete supplied graph; no learning settings |
| RUN | Both learning settings and exactly one explicit root list | Learns and orders |

The original-edition input must be a complete `cell_data_set`, metadata version
1.0.0, a counts-only in-memory `dgCMatrix`, unique complete literal gene/cell IDs,
finite named UMAP coordinates and complete named cluster/partition factors.
Additional assays, delayed/external backends, subclasses and silent version
updates are outside this capture profile. Input axes, assays, metadata, embeddings,
factors, other graphs and preprocessing state are checked for preservation.
Identifiers and partition factors are never recoded. Original errors remain errors.

All twelve original learning controls are accepted inside `learn_graph_control`:
`euclidean_distance_ratio`, `geodesic_distance_ratio`, `minimal_branch_len`,
`orthogonal_proj_tip`, `prune_graph`, `scale`, `ncenter`, `rann.k`, `maxiter`, `eps`,
`L1.gamma`, `L1.sigma`. Missing entries retain original defaults. An explicitly
empty object is allowed. Explicit NULL is supported for `ncenter` and `rann.k`;
the original outer function maps NULL `rann.k` to25, despite its documentation's
estimation wording. The original `scale=TRUE` coordinate behavior is retained.
No source repair or automatic setting changes are performed.

Choose `convergence_policy:"require"` to refuse a returned graph without complete
finite objective convergence or with a reported nonzero kmeans ifault. A single
iteration cannot establish convergence. Alternatively choose `"retain-original"`
explicitly: the original return is kept with a prominent nonconvergence warning,
and convergence remains false. ORDER does not refit or re-establish learning
convergence for a caller-supplied graph.

Principal-root repetitions follow the original; repeated cell roots are mapped
and uniqued as in the original. First-nearest-cell ties and cell order are retained.
Disconnected cells keep `Inf`. Nonfinite/negative unused principal-edge metadata
are preserved separately; native ordering uses the captured projected graph,
which must have finite nonnegative weights or be explicitly unweighted.

## Runtime and capacities

The closed runtime JSON declares schema1, profile
`monocle100-original-graph-r43-v1`, `python`, `rscript`, `r_home`,
`native_library_path` and ordered `r_libraries`. Use absolute Linux paths in the
example template. R4.3.3 on `x86_64-conda-linux-gnu` and the selected18 exact package
versions are in the packaged PROFILE.json. Twenty-one original Monocle functions
are checked against byte-exact original reference bodies/formals. Selected package
code/executables and loaded native/math modules are captured before/after calls.
This selected environment does not establish a reconstructed paper-era host or
authenticate a producer. A declared runtime is never downloaded or installed by
the analysis command.

Optional capacity fields are `maximum_cds_bytes` (default2GiB),
`maximum_json_bytes` (64MiB), `maximum_output_bytes` (2GiB), `maximum_files`
(10,000), `maximum_cells` (1,000,000), `maximum_native_operations` (50,000,000),
`timeout_seconds` (600), `maximum_address_space_bytes` (8GiB) and
`maximum_sampled_rss_bytes` (8GiB). Native principal/root capacity is9,999 and
coordinate dimension capacity1,024. No rank-capacity parameter is accepted here.
Output tables must fit their declared verification read capacity. Complete capture
limits include the manifest and pending completion marker. Address-space limits
are hard per process; sampled RSS/output/wall checks are not hard aggregate quotas.
Self-detaching processes are outside the owned-session profile.

## Outputs and verification boundary

Accept a result only after exit0, `COMPLETED.json` and `analysis.json` with
`graph_admitted:true`. Ordering separately requires `pseudotime_admitted:true`.
`scientific_test_admitted` and verified biological direction remain false.

The capture retains complete source/output CDS, learned CDS when applicable,
input/output scientific state and invariants, original principal/projected graph
objects, original ordering when applicable, RNG records, source, convergence,
warnings/errors and process/runtime receipts. Required model members cannot be
removed or emptied and then admitted by resealing the manifest.

Exports include complete `principal-nodes.tsv`, `principal-graph.json`, audit JSON
and, for ORDER/RUN, a complete `pseudotime.tsv`. No cell/point prefix is admitted.
Read WARNINGS.md and ORIGINAL-WARNINGS.txt, including original R/C++ logs.

VERIFY checks the saved manifest, packaged source, explicit settings, required
model member presence, complete projections, original objective classification,
root mapping, native geodesic ordering and full exports. It neither parses opaque
RDS nor refits/independently optimizes graph learning. It checks integrity and
internal consistency, not a producer's identity.

REPLAY executes original functions again and compares all scientific model slots,
original ordering and RNG, while preserving the parent capture. Only igraph's
private cache representation is canonicalized. The independent qualification
comparator separately checks complete original-reference objects and attributes;
it never sources the production worker.

## Warnings and required actions

**SKIP3: dependent analysis skipped.** Supply the exact permitted runtime,
original-edition input or accessible path named in the error. Downloads/private
installation remain authorized where the operating environment allows them. If
installation/execution is prohibited, retain the skip and tell the user exactly
what must be supplied. Do not substitute editions or claim an analysis ran.

**FAIL2: no complete graph/pseudotime admitted.** Preserve raw, partial, null,
failed and superseded model/error/RNG evidence. Correct the named source, input,
numeric, capacity or process problem and retry NEW output. Never weaken guards,
rename slots, infer roots, recode factors, drop cells or silently repair nonfinite
values. A graph or pseudotime is not an admitted gene test or biological direction.

Original reference R source is MIT,2019 Cole Trapnell. The normal JAR already
includes full matching terms at
`org/jlinalg/omics/monocle3-pseudotime/1.0.0/LICENSE.md` and its LICENSE holder file.
The complete original source archive and additional notices remain in the
[preceding source packet](monocle100-weights-checkpoint/README.md). Project adapter
code is GPL-3.0-or-later. No private author pipeline or GPL-2-only SLAM source is
redistributed by this graph stage.
