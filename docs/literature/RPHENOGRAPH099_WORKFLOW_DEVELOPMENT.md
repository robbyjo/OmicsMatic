# Rphenograph 0.99.1 clustering and native Java coefficients

**Version warning: P040 reports Rphenograph 0.1.1. That package identity remains unresolved. This implementation explicitly selects recovered upstream version 0.99.1; it does not reproduce the reported edition or the paper. Rphenograph remains missing in the source-qualified 250-paper catalog. The catalog still has 103 new scoped integrations among 407 named tools, with 245 missing tools.**

**Execution warnings: a required installation, download or execution that is unavailable or forbidden produces SKIP3 and skips the dependent analysis. FAIL2 admits no clustering. Keep the output directory and read `WARNINGS.md` and the original process logs before retrying with a NEW output path.**

This standalone development workflow calls the complete, unmodified upstream function: original RANN nearest neighbors, original Jaccard coefficient events, the original undirected multigraph and seeded igraph Louvain clustering. It retains every input cell and feature in order. Java also implements the entire integer-neighbor coefficient calculation and checks every original coefficient, graph edge and cell assignment. Java does not replace nearest-neighbor search or Louvain.

The selected source is [JinmiaoChenLab/Rphenograph](https://github.com/JinmiaoChenLab/Rphenograph), commit `0298487f0ee13aac55eb77d19992f6bd878ba2fc`, version 0.99.1, Artistic-2.0. All four inspected reachable commits declare 0.99.1; no 0.1.1 tag was recovered. The separate [robustSingleCell 0.1.1 CRAN archive](https://cran.r-project.org/src/contrib/Archive/robustSingleCell/robustSingleCell_0.1.1.tar.gz) contains matching R function bodies and coefficient code differing only by its terminal newline. That is a source lead, not evidence that P040 used robustSingleCell. The earlier [source review](PHENOGRAPH_SOURCE_REVIEW.md) is retained as its historical intake snapshot.

## Build without installing R

From the repository root, use an installed Python and a permitted JDK21 or JDK25. This compiles a standalone JAR targeting Java17 and does not modify the main executable.

```powershell
python tools/literature/rphenograph099/build.py --java-bin "$env:JAVA_HOME/bin" --out build/rphenograph099-java
java -jar build/rphenograph099-java/rphenograph099-workflow-development.jar --help
```

`--out` must be NEW. The builder copies the project license, upstream Artistic-2.0 license and source attribution into that output. The R runtime is separate and is not bundled in the JAR.

## Use the native Java calculation

The JAR includes `org.jlinalg.singlecell.RphenographJaccard099`. It needs no external R installation. For example, start `jshell --class-path build/rphenograph099-java/rphenograph099-workflow-development.jar` and enter:

```java
import org.jlinalg.singlecell.RphenographJaccard099;
int[][] neighbors = {{2, 3}, {1, 3}, {1, 2}, {1, 2}};
var coefficients = RphenographJaccard099.compute(neighbors, 1_000L);
for (int i = 0; i < coefficients.rawRows(); i++) {
    System.out.println(coefficients.fromOneBased(i) + "\t"
        + coefficients.toOneBased(i) + "\t" + coefficients.weight(i));
}
```

Rows are cells; neighbor entries are **one-based row indices**. Repeated neighbors and self-neighbors are permitted. The result contains all `number_of_cells * neighbor_columns` rows: positive events in source order followed by the original zero padding. Each positive weight is half the source Jaccard coefficient. Reciprocal events are retained separately. The declared comparison capacity bounds `2 * cells * neighbor_columns^2`; sorting and storage are additional resource costs. The flat overload also represents zero-row/zero-column matrices explicitly. Invalid shapes, indices, overflow or insufficient capacity fail before returning a partial result.

This API calculates coefficients from supplied neighbor indices. It does not find those neighbors or perform clustering. Its valid helper domain includes cases that the original full R wrapper cannot execute, such as a one-column neighbor matrix.

## Install the original runtime privately on Linux

Original analysis requires Linux Java, including permitted WSL with **Linux paths**, and the exact selected R4.3.3/RANN2.6.2/igraph2.0.3/Rphenograph0.99.1 provider. The complete profile checks 32 namespace versions and all three installed Rphenograph function bodies. Do not point it at an arbitrary R library and infer equivalence from its package name.

If an exact dependency base already exists, install only the original package into NEW private paths:

```bash
python3 tools/literature/rphenograph099/setup.py \
  --base /absolute/path/to/exact-dependency-base \
  --allow-download --allow-install \
  --out /absolute/path/to/new-rphenograph-provider
```

The base must contain the exact `pixi.toml` and `pixi.lock` distributed with this workflow and their installed `.pixi/envs/default` environment. This profile reuses the previously qualified R4.3.3 dependency base; it includes additional packages used by the earlier Monocle workflow. The adapter does not execute those additional methods. Both a local pristine source installation and fixed-commit source download/installation are tested. The existing base is preserved.

To provision that locked base as well, use a permitted Pixi executable and explicit download/install authorization:

```bash
python3 tools/literature/rphenograph099/setup.py \
  --provision-base --pixi /absolute/path/to/pixi \
  --allow-download --allow-install \
  --out /absolute/path/to/new-rphenograph-provider
```

The locked-base provisioning option is provided; this Rphenograph qualification tests installation against the existing exact base, not a new dependency download. To avoid source downloading, supply `--source-root /absolute/path/to/pristine-source` instead of `--allow-download`. The ten original source members must match the profile exactly. Compilation uses a separate working copy. Installation performs no analysis. Read the provider's `RESULT.json`, `readiness.json` and warnings; readiness is not an analysis result.

## Run complete clustering

Prepare one UTF-8 cells-by-features TSV. Its first header is `cell_id`; subsequent headers are literal feature IDs. Each remaining row is one literal cell ID followed by a finite decimal value for every feature. IDs may contain spaces or Unicode, but not tabs/newlines/control characters. Cell IDs and feature IDs must each be unique. Missing values, `NaN`, `Inf`, hex notation, ragged rows and duplicate IDs are rejected. Signed values are supported. No feature selection, normalization, logarithm, asinh transform or imputation is applied.

Save a JSON request such as the following, replacing paths and declarations with your own. Copy `rscript`, `r_libraries` and `source_root` directly from the successful provider `RESULT.json`. The example's `k=40` requires at least 42 cells. The seed is a caller choice, not a recovered paper setting.

```json
{
  "schema_version": 1,
  "version": "0.99.1",
  "matrix": "/absolute/path/to/prepared-cells-by-features.tsv",
  "input_units": "caller-prepared-continuous-features",
  "input_provenance": "Describe the actual preparation and upstream result",
  "analysis_population": "Describe every included cell and feature",
  "k": 40,
  "seed": 20161231,
  "execution_policy": "ALLOW",
  "rscript": "/absolute/path/to/exact-base/.pixi/envs/default/bin/Rscript",
  "r_libraries": [
    "/absolute/path/to/provider/R-lib",
    "/absolute/path/to/exact-base/.pixi/envs/default/lib/R/library"
  ],
  "source_root": "/absolute/path/to/provider/source",
  "out": "/absolute/path/to/new-clustering-output"
}
```

Run from Linux with the standalone JAR and Linux Java:

```bash
java -Xmx2g -jar /absolute/path/to/rphenograph099-workflow-development.jar \
  --request /absolute/path/to/request.json
```

The optional capacity fields use the following exact JSON names. Specify only values appropriate for your permitted host and complete input.

| Request field | Default | What it limits |
| --- | --- | --- |
| `maximum_input_bytes` | `268435456` | Complete source matrix bytes. |
| `maximum_output_bytes` | `1073741824` | Captured analysis workspace and original process output/file budget. |
| `maximum_entries` | `10000000` | Complete input cells times features. |
| `maximum_json_bytes` | `67108864` | Original result JSON bytes. |
| `maximum_native_operations` | `500000000` | Conservative coefficient intersection comparison budget, `2 * cells * k^2`. |
| `timeout_seconds` | `900` | Original process wall time and CPU allowance. |
| `maximum_address_space_bytes` | `17179869184` | Hard original per-process address space. |
| `maximum_sampled_rss_bytes` | `8589934592` | Sampled aggregate RSS of the owned original process tree. |

Process execution also has file/process/thread limits and owned-process cleanup. Sampled RSS is not a hard aggregate memory limit. Capacity failures never return a prefix of the requested population. Unknown fields and policies are rejected.

## Inspect results and recover from warnings

Start with `analysis-status.json` and `WARNINGS.md`. Only `exit_code=0` and `result_admitted=true` admit the original candidate clustering. The result is not biological validation or paper reproduction.

| Output | Meaning |
| --- | --- |
| `original-model.rds` | Complete original returned graph and community object. |
| `original-state.rds` | All public graph attributes, vertices/edges and the complete community object, including all six original community fields. |
| `complete-components.rds` | Every full/used original neighbor index and every raw coefficient. |
| `neighbors-full.tsv` | All `k+1` original RANN neighbors, before first-column removal. |
| `coefficients-raw.tsv` | Every original coefficient row, including trailing zeros. |
| `graph-vertices.tsv`, `graph-edges.tsv` | Original vertex order/names, literal-cell projection and every parallel weighted edge. |
| `cell-assignments.tsv` | Every input cell in input order. Cells absent from the original graph remain `NA`. |
| `native-audit.json` | Complete Java coefficient/multigraph/assignment consistency checks. It neither redoes RANN/Louvain nor authenticates the producer. |
| `runtime-provenance.json`, `process/` | Runtime fingerprints, original stdout/stderr, limits, terminal process record and cleanup evidence. |

The source drops the **first returned neighbor column**, not an explicitly identified self-neighbor. Ties can therefore leave self-neighbor events. Positive reciprocal events become separate undirected half-weight edges; no graph simplification or vertex insertion is performed.

| Warning or failure | What to do |
| --- | --- |
| SKIP3: installation/download/execution forbidden or unavailable | Use a permitted host and exact provider, or leave the dependent analysis skipped. Do not substitute another algorithm or silently continue. |
| FAIL2: wrong source/profile, invalid input or capacity | Keep diagnostics, correct the named cause and retry to NEW output. Never repin original controls to make altered scientific code pass. |
| Original `k=1` error: `Not a matrix.` | The source drops the matrix dimension. No `drop=FALSE` repair is applied. Choosing another scientifically justified `k` is a new analysis. |
| Original empty graph / nonfinite modularity | Complete original output is retained, including `NaN` and unassigned cells, but clustering is not admitted. Inspect data and parameters; any changed request is a new analysis. |
| Paper edition 0.1.1 requested | Obtain evidence of the exact reported package, or explicitly analyze with candidate 0.99.1 as a separate analysis. No automatic substitution is available. |

Input file identity/size/timestamp is monitored during execution and full input hashes are checked before and after. Complete runtime package contents are hashed before and after. These checks are integrity evidence, not producer authentication.

## Validation and portable replay

The retained qualification compares every native coefficient with unmodified original C++ output across 863 cases and 105,921 complete rows, bit for bit, on Windows JDK21, Windows JDK25 and Linux JDK21. It includes all 729 three-by-two integer-neighbor matrices, duplicate/self-neighbor cases, zero dimensions/zero links, 128 random four-by-three cases and a complete 100,000-row case.

Five full original controls cover iris (`k=30`), prepared signed protein features (`k=40`), tied identical rows, an empty-graph square and the original `k=1` failure. Three yield admitted candidate clusterings; the empty graph and original failure remain unadmitted. Every full/used neighbor index and raw coefficient and every public graph/community value from the four returned original models are compared with independent original runs. Complete original model RDS files are retained; their raw bytes differ between runs. Complete public graph/community state is strictly identical and its saved state files match byte for byte. No raw-model bytewise identity is claimed.

The packaged protocol tests exercise all four returned component populations, final zero padding, missing/excess/reordered rows, wrong coefficients/edges/assignments, false identity/reportability claims, capacity limits, unsupported editions and forbidden execution. Linux also checks invalid complete inputs before original process launch. Eight actual installer refusals verify that forbidden/missing/corrupt/overlapping inputs never launch a compiler or analysis.

See [the complete retained evidence](rphenograph099-workflow-development/README.md). A portable replay rebuilds current Java, checks all saved coefficient and graph controls, and retains original failure diagnostics. It performs no original fitting or RDS loading:

```powershell
python tools/literature/rphenograph099/verify.py --java-bin "$env:JAVA_HOME/bin" --out build/rphenograph099-replay
```

To audit another complete saved component directory, keep its sibling tables together:

```powershell
java -jar build/rphenograph099-java/rphenograph099-workflow-development.jar --audit-result /path/to/worker-result.json --out build/new-component-audit
```

A successful portable audit admits no original analysis. Neither these controls nor the matching robustSingleCell source establish the identity of P040's reported Rphenograph0.1.1 package. The full 250-paper objective remains open.
