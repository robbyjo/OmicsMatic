# Monocle3 original workflow development interface

This interface runs original **Monocle3 1.4.27** on explicitly supplied raw integer counts and typed metadata. It can preprocess with PCA or LSI, calculate UMAP, cluster cells, learn/order a principal graph, fit additive or natural-spline gene models, and run the stock Moran `graph_test`. A separate native Java audit checks pseudotime on the actual fitted projected graph. Original graph learning, gene models and Moran tests remain upstream computations.

**Development availability:** invoke `org.jlinalg.cli.Monocle3WorkflowCli` through the separate development JAR's classpath. The canonical/released JAR and root dispatcher are unchanged. The previously validated [native prepared-graph API](MONOCLE3_PSEUDOTIME_DEVELOPMENT.md) needs no R runtime. The [original runtime readiness checkpoint](MONOCLE3_CURRENT_READINESS.md) records source intake, private installation, compiler/dependency receipts and earlier failed attempts. This interface alone earns no Monocle catalog credit: historical and paper-specific roles remain open.

## Warnings and required actions

**SKIP3 — unavailable runtime means no dependent analysis.** This profile requires Windows R 4.6.1 and the exact dependencies in the packaged `SOURCE-PROFILE.json`. Supply explicit private R libraries; install only where the operating environment permits it. The analysis command downloads/installs nothing. If provision is forbidden or unavailable, retain the attempt and skip its dependent analysis. Other platforms or editions receive SKIP3; there is no fallback to 1.4.27 when 1.0.0, 1.3.1, 1.3.3 or 1.3.7 was requested. Native Java checks on Linux do not establish Linux original-package fitting.

**Historical 1.3.1 installation was skipped.** Its archived `grr` 0.9.5 dependency failed under the attempted R 4.6.1 environment because `STRING_PTR` was unavailable. A separately validated compatible environment is needed for that edition. Do not patch a dependency or substitute a newer edition silently. Reported paper versions 1.3.3 and 1.3.7 remain unresolved.

**FAIL2 — no result from a failed atomic batch is admitted.** Every requested analysis must return successfully, every original model/graph-test gene status must be `OK`, and every Java audit must pass. A completed earlier case is also unadmitted if another case fails. Complete/partial original files stay under `worker/`; only a successful batch has `results/`. Inspect the named cause and retry into a NEW output directory. Do not drop failed genes, reorder identifiers or replace `NA`/`Inf` to force success.

**Gaussian compatibility failure:** direct original and adapter controls both return `FAIL` for all tested Gaussian models in this exact runtime profile. Their models and coefficients are retained under `worker/cases/gaussian/`; no Gaussian result is admitted. The source status extractor recognizes the `speedglm` class, while these fits return `speedlm`. This workflow preserves that source classification. Choosing Gaussian is therefore not a workaround for failed count models.

**Other original warnings remain visible.** The original `subbars` relocation warning is retained without patching the source. Installation also recorded an `IterableMatrix` class registration message; the tested runtime uses sparse in-memory counts. Disk-backed BPCells inputs are not supported by this interface. Warnings, original errors and required recovery appear in `WARNINGS.md`, the terminal message and JSON reports. Original messages and transcripts remain in `worker/`.

**Read admission before using output:** require exit 0, `analysis-status.json` with `result_admitted: true`, and a complete published bundle. A manifest or a returned R table alone does not establish admission. Publication/cancellation/capacity failures do not certify a completed analysis. Logical input/output/operation limits and periodic monitoring are not OS memory or disk quotas; provision appropriate resources.

## Input and invocation

Start with the [executed synthetic example](../../examples/monocle3-workflow/README.md). For your own analysis, prepare three **literal, unquoted UTF-8 TSV** files. Use LF or CRLF. Quotes and spaces are literal identifier characters; there is no CSV quote interpretation. IDs must be nonempty, unique, and free of control characters. The literal text `NA` is an identifier or declared factor level, never an implicit missing value.

* `counts.tsv`: header `gene_id`, followed by ordered cell IDs; each following row is one gene and its raw nonnegative integer counts. Values must be at most `9007199254740991`. Zero-total cells are refused; zero-count genes remain and can trigger an original model failure. No filtering, imputation, normalization or identifier repair occurs at intake.
* `cell-metadata.tsv`: header `cell_id`, then exactly the declared metadata columns. Rows must match the count header order exactly. Numeric covariates must be finite. Factors require explicit unique levels in the intended order; undeclared levels fail. Column names use `[A-Za-z][A-Za-z0-9_]{0,63}` and may not be reserved original fields (`Size_Factor`, `pseudotime`, `cluster`, `partition`, `f_expression`) or R keywords.
* `gene-metadata.tsv`: exactly `gene_id<TAB>gene_short_name`. Rows must match count gene order exactly. Short names may repeat; gene IDs may not. No gene symbol or assembly is inferred.

Use `count_units: "raw-integer-counts"`. TPM, log counts, metaVIPER protein activity and other transformed matrices are outside this interface. Inputs with the same numerical shape are not automatically suitable biological measurements.

```text
java -Xmx1g -cp PATH_TO_DEVELOPMENT_JAR org.jlinalg.cli.Monocle3WorkflowCli --request request.json
java -cp PATH_TO_DEVELOPMENT_JAR org.jlinalg.cli.Monocle3WorkflowCli --help
```

JSON paths resolve against the request file. On Windows keep the outer request path and JAR path ASCII; internal JSON paths and TSV identifiers support UTF-8. Output must be NEW and must not overlap any declared R library. The example helper fills the actual R library paths from a completed private setup receipt.

Every request declares `schema_version: 1`, `mode: "RUN"`, `version`, the three input paths, `count_units`, `metadata_types`, `rscript`, nonempty `r_libraries`, `out`, and 1–32 `analyses`. Numeric type: `{"type":"numeric"}`. Factor type: `{"type":"factor","levels":["a","b"]}`. Analysis IDs are unique portable names, including case folding; each analysis declares its own positive integer `seed`.

Optional capacities: `maximum_input_bytes` (default 64 MiB, maximum 256 MiB across captured inputs), `maximum_entries` (default 1 million, maximum 10 million count entries), `maximum_json_bytes` (default 64 MiB), `maximum_output_bytes` (default 512 MiB of worker growth), `maximum_native_operations` (default 50 million), and `timeout_seconds` (default 600). Unknown fields are refused. The complete effective request records selected defaults.

## Analysis contract

Each analysis needs `trajectory` and/or `fit_models`. `graph_test` additionally requires `trajectory`. No executable formula, R code or caller RDS is accepted.

`trajectory` requires all of the following explicit objects and exactly one nonempty `root_cells` or `root_pr_nodes` list:

```json
{
  "preprocess": {"method":"PCA","num_dim":10,"norm_method":"log","pseudo_count":1,"scaling":true},
  "umap": {"n_neighbors":15,"min_dist":0.1,"metric":"cosine","fast_sgd":false},
  "cluster": {"method":"leiden","k":15,"resolution":0.01,"partition_qval":0.05,"weight":false},
  "graph": {"use_partition":false,"close_loop":false,"ncenter":12,"minimal_branch_len":3,"prune_graph":true,"maxiter":10},
  "root_cells": ["cell-001"]
}
```

Preprocessing method is `PCA` or `LSI`; normalization is `log`, `size_only` or `none`; UMAP metric is `cosine`, `euclidean` or `manhattan`; clustering is `leiden` or `louvain`. Embedding is two-dimensional UMAP in this interface. Other source options retain the pinned edition's defaults. Numerical domain, rank, neighborhood and graph constraints can still produce an original error. The source's cell-root projection/nearest-cell behavior is preserved. Unreachable cells retain `Inf` pseudotime and explicit warnings. Roots declare an analysis direction, not a biological conclusion.

`fit_models` requires an original family, an explicit intercept flag and additive terms:

```json
{"family":"quasipoisson","intercept":true,"terms":[{"column":"progress"},{"column":"batch"}]}
```

A numeric term can instead be `{"column":"progress","spline_df":3}`, producing the original `splines::ns` term. Numeric `pseudotime` can be used only when this analysis runs a trajectory. Infinite pseudotime may make the original model fail; it is never replaced. Interactions, random effects and arbitrary formula functions are outside this contract.

Exposed families are `quasipoisson`, `poisson`, `negbinomial`, `gaussian`, `binomial`, `zipoisson`, and `zinegbinomial`. Synthetic direct-source comparisons validate six successful families and natural splines, and preserve Gaussian's original failure described above. A parseable family is not a scientific suitability recommendation. Binomial requires suitable response values; requesting it does not convert counts into presence/absence.

The worker calls original `fit_models` with one core, `clean_model=FALSE` and `verbose=TRUE`, preserving complete fit objects and diagnostic output. The pinned source applies size-factor offsets; the adapter does not substitute a generic unadjusted GLM. `coefficient_table` retains its original estimates, uncertainty, normalized effects and q-values. **Its q-values use R's default Holm adjustment within `model_component` and `term`; they are not relabeled as BH FDR.** Inspect `original-arguments.json`, complete RDS and column metadata when interpreting them.

`graph_test` requires e.g. `{"neighbor_graph":"principal_graph","k":25,"alternative":"greater"}`. Neighbor graph can be `principal_graph` or `knn`; alternatives are `greater`, `less` and `two.sided`. It invokes original stock Moran I with one core and the source's quasipoisson setting. This is not a paper's custom covariate-adjusted `graph_test`.

## Retained results and verification limits

Successful `results/ID/` contains the **entire** original `cds/` save directory, including nearest-neighbor indices, original model/coefficient/graph-test RDS objects when requested, atomic TSV/JSON exports, source diagnostics, arguments, RNG settings and Java audit. The worker reloads the full CDS and checks literal axes, count matrix, and applicable UMAP/cluster/partition/pseudotime values before admission. Preserve the entire directory; a lone `cds_object.rds` omits required index state. Relocation is tested by independently loading the published bundle after atomic staging has moved.

Every table's `.json` records ordered columns, original column classes/types, per-cell tokens and kinds, and columns omitted from TSV. `model` and `model_summary` list columns remain in complete RDS. `NA`, `NaN`, signed infinity and a literal string `NA` remain distinguishable through the kinds. Graph-test gene row names have an explicit `source_row_name` heading. TSV contains quoted atomic tokens and is not the complete original object.

`inputs/` preserves all three files byte for byte. Request/input hashes are checked before and after execution. `source/` contains original MIT-licensed R sources/notices and the worker; selected loaded R bodies/formals and exact normalized package versions must match. Version matching alone does not authenticate all dependency source or distinguish an author fork; use the recorded private setup's declared author packages. No native DLL authentication is claimed. `worker/` retains stdout/stderr, original warnings/messages/errors, progress, runtime bindings/session, process cleanup receipt and all partial scientific objects. `manifest.yaml` binds published files by SHA-256; checksums are integrity records, not signatures or biological certification.

The native audit compares actual source root selections and all cell/projected-vertex distances with Java. This does not independently refit PCA, UMAP, clusters, gene models or graph statistics. Independent direct-source controls perform those comparisons separately. There is no portable `VERIFY` command for this development interface.

Historical edition reproduction, batch alignment, supplied embeddings/activity values, modified paper-specific tests, Monocle2 `differentialGeneTest`, Cicero, `py-monocle`, paper review application and the full 250-paper toolbox objective remain open. Cell Ranger remains excluded as requested.
