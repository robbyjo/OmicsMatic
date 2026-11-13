# Monocle3 1.3.7 with Batchelor 1.20.0

This standalone Java entry point runs the original packages through
preprocessing, batch alignment, aligned UMAP, clustering, graph learning and
explicitly rooted pseudotime. It captures complete inputs, original source,
models, RNG states, warnings and process receipts. Native Java independently
compares all supplied projected-graph distances, cell pseudotimes and selected
root indices with the original outputs.

**Development component.** Other Monocle roles in the 250-paper project remain
open. This component does not grant a whole-Monocle or whole-Batchelor badge,
reproduce the paper, recover its source commits or certify biological roots.
The canonical executable is unchanged; use the standalone development JAR.

## Source and paper scope

P051, [Architecture of the neutrophil compartment](https://doi.org/10.1038/s41586-025-09807-0),
reports standard Monocle preprocessing, Batchelor 1.20.0 integration and
Monocle3 1.3.7 dimensional reduction, clustering and pseudotime. It separately
reports a continuity-corrected Wilcoxon comparison of immature-cell groups.
The integration function, exact preprocessing/settings, graph controls and
roots are not established by that paragraph.

The selected [official Monocle source](https://github.com/cole-trapnell-lab/monocle3/tree/98402ed0c10cac020524bebbb9300614a799f6d1)
implements `align_cds()` with Batchelor `reducedMNN()` on preprocessed PCA/LSI
coordinates. This workflow implements that explicit original-package path;
it does not assert that the paper called those exact functions. The
[official Batchelor source](https://bioconductor.org/packages/3.19/bioc/src/contrib/batchelor_1.20.0.tar.gz)
is version 1.20.0. Original R function bodies/formals and qualified dependency
versions are checked before execution. Both source trees are captured and
checked again after analysis. Selected runtime-package files are also pinned
before and after execution. These are integrity checks, not producer signatures
or authentication of every dependency binary.

The [reviewed tool addition](monocle137-workflow-development/BATCHELOR_DISCOVERY.json)
records a real extraction gap: the legacy 405-tool inventory omitted Batchelor
despite its explicit name/version in this paper. That evidence is retained
separately pending a documented catalog amendment. Historical primary records
and badge counts have not been silently rewritten.

## Build and provision the private runtime

```powershell
python tools/literature/monocle137/build.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/literature-review/monocle-runtime/new-137-build

python tools/literature/monocle137/setup.py `
  --base build/literature-review/monocle-runtime/monocle133-reference-setup-1 `
  --out build/literature-review/monocle-runtime/new-137-runtime `
  --allow-install --allow-download
```

These host examples require the existing locked private R4.3.3 dependency
base. Its recipe is `tools/literature/monocle133/setup_reference.py --out NEW
--allow-install`; on this Windows host that recipe uses the existing WSL
distribution and private pixi bootstrap. Supply those prerequisites through
permitted installation or skip the dependent analysis. The dependency base
contains Monocle1.3.1/Batchelor1.18; **those are not substituted for the requested
versions**. The new setup compiles exact 1.3.7/1.20.0 source in a separate
library and preserves existing libraries.

Where source/data files are already available, replace downloads with
`--monocle-source-root PATH --batchelor-source-root PATH
--genome-data-package PATH`. Both source roots must match every pinned original
member; the data donor must be GenomeInfoDbData1.2.11. Keep `--allow-install`
because this still creates and compiles a new private library. Installation or
download that is missing or forbidden produces **SKIP3**, a warning and a
required-action message. It admits no analysis.

Read `RESULT.json` and `WARNINGS.md`. A READY result supplies Linux `rscript`,
`libraries` and the two `sources` paths for a request. Readiness alone is not
analysis. An interrupted installer with no observed final exit remains
unqualified; retain it and use NEW output paths for a fresh permitted setup.

## Complete inputs and explicit settings

Inputs are UTF-8, unquoted literal TSV. Gene metadata is exactly
`gene_id<TAB>gene_short_name`. Cell metadata starts with `cell_id`, followed by
exactly the declared columns. Expression is either TSV (`gene_id` then all
cell IDs in metadata order, with all gene rows in gene-metadata order) or
Matrix Market coordinate real/integer general with matching dimensions,
one-based unique coordinates and omitted zeros. Duplicate coordinates are
refused rather than summed. Missing values, identifier repair, row reordering
and implicit population filtering are not supplied by the adapter.

Declare `raw-integer-counts` or `nonnegative-normalized-expression`, including
the actual provenance and complete prepared population. Raw counts must be
exact nonnegative integers; prepared expression is not rounded into counts.
The original constructor still estimates Size_Factor from the supplied matrix,
then applies the explicitly selected original preprocessing. Choosing normalized
input does not silently select `norm_method=none`: declare the intended method.
Original internal feature selection/transforms remain original source behavior.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "version": "1.3.7",
  "batchelor_version": "1.20.0",
  "expression": "/path/to/counts.mtx",
  "expression_format": "MATRIX_MARKET",
  "expression_units": "raw-integer-counts",
  "expression_provenance": "Declare the actual count assay and export",
  "population_declaration": "Complete caller-prepared cell and gene population",
  "cells": "/path/to/cells.tsv",
  "genes": "/path/to/genes.tsv",
  "metadata_types": {
    "Batch": {"type": "factor", "levels": ["A", "B"]},
    "position": {"type": "numeric"}
  },
  "seed": 251052,
  "preprocessing": {
    "method": "PCA", "num_dim": 10, "norm_method": "log",
    "scaling": true, "pseudo_count": "ORIGINAL_DEFAULT"
  },
  "alignment": {"batch_column": "Batch", "k": 20, "residual_terms": []},
  "reduction": {
    "max_components": 2, "n_neighbors": 20, "min_dist": 0.1,
    "metric": "cosine", "fast_sgd": false
  },
  "clustering": {
    "method": "leiden", "k": 20, "num_iter": 2,
    "partition_qval": 0.05, "weight": false,
    "resolution": 0.01, "random_seed": 42
  },
  "graph": {
    "use_partition": false, "close_loop": false,
    "ncenter": 130, "minimal_branch_len": 15
  },
  "root_cells": ["C001"],
  "require_terminal_roots": false,
  "out": "/path/to/new-result",
  "rscript": "/path/to/dependency-base/.pixi/envs/default/bin/Rscript",
  "r_libraries": [
    "/path/to/new-137-runtime/R-lib",
    "/path/to/dependency-base/.pixi/envs/default/lib/R/library"
  ],
  "monocle_source_root": "/path/to/new-137-runtime/source/monocle3",
  "batchelor_source_root": "/path/to/new-137-runtime/source/batchelor",
  "execution_policy": "ALLOW"
}
```

Replace all example paths, columns, levels and settings with the declared
analysis inputs. The example root is a synthetic cell ID, not a biological
recommendation. Run the JAR using Linux Java:

```bash
java -Xmx2g -jar /path/to/monocle137-workflow-development.jar \
  --request /path/to/request.json
```

Preprocessing supports original PCA/LSI and log/size_only/none normalization.
Residual terms are additive declared covariates, not executable R formulas.
Monocle's original alignment resets the RNG seed to 2016; the adapter records
that behavior and does not reseed between stages to override it. Original
residual-alignment NA-coefficient handling is also retained. UMAP runs with
one core; BLAS/OpenMP threads are limited to one. Other controls retain the
pinned source defaults. Clustering may be Leiden or Louvain. Graph `ncenter`
and `minimal_branch_len` may be numeric or `ORIGINAL_DEFAULT`.

For root selection, first use `mode=LEARN` with both root fields absent. It
retains the learned graph but admits no pseudotime. Inspect `principal-graph.json`,
then run the same inputs/settings/seed with `mode=RUN` and exactly one of
`root_cells` or `root_pr_nodes`. Set `expected_principal_graph_sha256` to the
SHA256 of the prior graph to reject a changed refit. Roots are never inferred.
With root cells, original Monocle maps them to principal points and then chooses
nearest graph reference cells; the declared root cell itself need not receive
zero pseudotime. Disconnected-cell `Inf` values are retained and compared.

## Admission, warnings and recovery

**Read `analysis-status.json` and `WARNINGS.md` before using any output.**

| Outcome | Meaning and required action |
| --- | --- |
| `pipeline_admitted=true`, `result_admitted=true`, exit 0 | Complete original RUN and required-state roundtrip plus native graph ordering checks passed |
| `pipeline_admitted=true`, `result_admitted=false`, exit 0 | LEARN only; choose explicit roots before requesting pseudotime |
| SKIP3, exit 3 | Supply the named permitted runtime/dependency/platform or execution permission; otherwise skip the dependent analysis |
| FAIL2, exit 2 | Retain the attempt, resolve the named source/input/original-method/numeric/capacity cause and retry NEW paths; no partial result is admitted |

Successful original warnings are copied into `WARNINGS.md`. Full original
messages and errors remain in `worker-result.json`, `worker-error.txt` and
process stdout/stderr. Failures after a CDS is created retain
`unadmitted-last-cds.rds` where saving succeeds. A valid upstream model or table
alone does not override a failed final admission gate.

`cds.rds` retains the original object; `rng.rds` records stage RNG states.
Roundtrip checks cover complete counts, metadata, reduced coordinates,
clusters, graph attributes/vertices/edges and pseudotime. igraph recreates its
private cache environments during serialization; those environment identities
are not scientific-state comparisons. The check does not certify every
external nearest-neighbor pointer or every future Monocle operation.

Optional capacities are `maximum_input_bytes`, `maximum_output_bytes`,
`maximum_entries`, `maximum_json_bytes`, `maximum_native_operations`,
`timeout_seconds`, `maximum_address_space_bytes` and
`maximum_sampled_rss_bytes`. The native-operation budget applies to graph
comparison work. Raise suitable explicit budgets and Java heap only where the
environment permits the whole job. No prefix or sampled population is admitted.
The Linux supervisor records process cleanup, wall time, hard per-process
limits, output size and sampled RSS; sampled aggregate RSS is not a hard
aggregate memory quota. Synthetic controls do not establish full study-scale
resource execution.

Portable `--audit-graph JSON --out NEW` compares supplied complete graph
statistics only. It performs no original fitting, RDS reload or producer
authentication and admits no original pipeline result.

## Reproducible software controls

The [control bundle](monocle137-workflow-development/README.md) retains complete
synthetic populations, original source, models, RNG, tables, warnings and process
receipts. Eleven original workflow controls cover dense/sparse input equivalence,
PCA/LSI, normalized-expression residual adjustment, Leiden/Louvain, LEARN/RUN
graph identity, original-root failure, native-budget refusal, graph-identity
refusal, the separately provisioned runtime and disconnected populations.
The disconnected control retains all 480 cells, including 240 `Inf` values.
An independent source control matches the complete alignment with a direct
original `reducedMNN()` call. These are software controls, not study results.

Windows Java21/25 each pass 34 packaged graph/refusal checks; Linux Java21
passes those plus six missing-prerequisite/input checks before R launch.
Five setup controls check installation/download permission, missing prerequisites
and source/lock contradictions. The interrupted installer remains unqualified;
a fresh complete setup and its full pipeline were validated separately.

```powershell
python tools/literature/monocle137/verify.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/literature-review/monocle-runtime/new-137-saved-replay
```

On Linux use Linux paths and its JDK. A NEW output on a permitted Linux temporary
filesystem can avoid slow small-file restoration through the Windows mount;
retain the replay receipts. The verifier checks every archived object, restores
the complete logical evidence population, rebuilds the adapter and compares
seven original graph controls, including disconnected `Inf` values. It repeats
all 34 portable checks, performs no original R fitting or RDS reload, and
authenticates no producer. RDS scientific-state roundtrips were checked during
the separate live original workflow runs.
