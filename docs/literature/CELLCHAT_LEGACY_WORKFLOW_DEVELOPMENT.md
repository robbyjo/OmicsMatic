# Original CellChat 1.1.3 condition-comparison workflow

This checkpoint provides a typed Java controller for original **CellChat 1.1.3**, including two condition fits, PPI projection, ligand/receptor selection, communication inference and filtering, pathways, aggregate networks, centrality, merged functional/structural similarity, information-flow comparison, differential ligand/receptor mapping and original bubble plots. Native Java independently checks the complete projection, directed Jaccard, explicit FNN ranks and source SNN smoothing, plus input/output axes and network transports. Original R remains authoritative for its statistical and structural/graph methods.

**The full 250-paper goal remains incomplete.** This completes a tested workflow for the P085 method family on caller-prepared data. Other CellChat editions and remaining paper roles still need work before catalog promotion. Counts remain **93 new scoped integrations, 96 including three existing adapters, and 248 missing tools**. These development executables are separate from the canonical released JAR; no release or website deployment is claimed.

## Warnings that matter before analysis

Read `WARNINGS.md`, the raw R output, `provenance.json`, and the tables named below. Require exit 0, `result_admitted=true` in `COMPLETED.json`, and successful `--verify`. A completion filename alone is insufficient.

* **Original inference can ignore an executed projection.** `raw_use: true` sends `data.signaling` into `computeCommunProb`; `false` consumes `data.project`. Both choices are explicit. The baseline uses the authors' posted `CellChatDB.human`, `PPI.mouse` and `raw.use=TRUE` choices. No species conversion or replacement is performed. In the synthetic human baseline, the mouse PPI has no overlapping signaling genes, so the saved projection is unchanged. A separate matched-mouse test actually consumes its projected expression and is checked against the full native solve.
* **Original FNN/SNN output can exceed one.** Ties can leave self among returned FNN neighbors after CellChat prepends self. Original Eigen code counts duplicate indices with multiplicity. The baseline retains six duplicate entries, two omitted strictly closer neighbors and four similarity entries outside `[0,1]`; some similarities equal `2.0`. The controller reports these facts without deduplication, reranking or clipping. The native API also retains original `Inf` behavior demonstrated by a separate direct C++ control.
* **Invalid original p-value fields remain invalid.** The tested Windows R 4.6.1 baseline emits 27 negative p-value fields across the two overexpression tables, merged differential-gene table and mapped ligand/receptor table. This counts exported fields, including repeated information, not 27 independent hypotheses. Values such as `-4.44e-15` remain visible. Do not treat them as valid p-values, clip them, or claim a significant result from them. Review the original R behavior and its compatibility before scientific interpretation.
* **Original `rankNet` can replace an unestimated test with zero.** For three or fewer retained nonzero paired edges, a pathway absent in a condition, or an original nonfinite paired test, source code assigns zero. `original-rankNet-zero-fallbacks.tsv` preserves the cause. These zeros do not establish estimated p-values. `rankNet` compares paired cell-group edges, not independent biological replicates.
* **Source warnings and failures remain visible.** The baseline includes an original replacement-length warning in `rankNet` and ggplot compatibility warnings. Original flowbet/info failures, when present, retain their zero replacements and causes. A no-interaction/no-selected-DEG error from original CellChat fails the complete analysis; earlier fits remain unadmitted. No interactions are added to make plotting succeed.

The cell-bootstrap and edge-level results do not establish independent donor replication, biological validity, historical paper reproduction, or large-network performance.

## Run the included synthetic example

Use Java 17 or newer and a validated private Windows R 4.6.1/CellChat 1.1.3 runtime profile. The [Java 21 compiler build](cellchat-legacy-development/cellchat-legacy-javac21.jar) and [Java 25 compiler build](cellchat-legacy-development/cellchat-legacy-javac25.jar) target Java 17. Analysis installs nothing and never runs user R code.

The example assumes that this workspace's validated profile exists at `build/literature-review/cellchat-runtime/legacy-runtime-profile-1/runtime-profile.json`. Otherwise prepare the permitted prerequisites described below and change the request's `runtime_profile` to your new profile. If installation is unavailable or forbidden, leave the dependent analysis skipped.

From the repository root:

```powershell
java -Xmx1g -jar docs/literature/cellchat-legacy-development/cellchat-legacy-javac21.jar --request docs/literature/cellchat-legacy-development/example/request.json
java -Xmx1g -jar docs/literature/cellchat-legacy-development/cellchat-legacy-javac21.jar --verify build/literature-review/cellchat-runtime/legacy-public-example-1
```

The output directory must be new. To repeat the example, copy/edit the request and select another new `out`; retain the previous attempt. Paths resolve relative to the request file. The supplied expression and phenotype tables are synthetic, with 33 genes and 216 cells across two conditions and three groups. The example explicitly uses the original inference defaults, including 100 bootstrap permutations and `trim: null` with `triMean`. The smaller paired compiler controls use 19 permutations. These are controls, not recommended biological sample sizes.

For your data, start with [request.json](cellchat-legacy-development/example/request.json), then change all paths and scientific choices explicitly. The controller accepts only the documented schema: unknown fields, RDS input objects, scripts and formulas are not supported. Source edition selection is fixed to the tested 1.1.3 candidate.

## Input and scientific choices

Expression is complete plain UTF-8 TSV, first column `gene_id`, then phenotype-ordered cell IDs. Metadata is complete plain UTF-8 TSV with exactly `cell_id`, `group`, `condition`, in that order. Every expression column must match the metadata cell order. Names are literal and unique; identifiers, missingness and values are never repaired. Supply finite nonnegative expression that has already been externally log normalized. Normalization, CellTrek coordinate prediction and construction of cell-type/region labels happen outside this adapter. Its `group` column can retain those prepared combined annotations; this is not a coordinate/distance CellChat spatial model.

Declare exact `group_order` and two-element `condition_order`. The second condition is positive for merged differential expression. Choose `prefilter_min_group_cells` and `prefilter_scope` explicitly. `any-condition` removes a group from **both** fits when any condition has fewer than the chosen number; exactly the threshold survives unless another condition falls below it. This matches the posted author-code selection rule. `each-condition` filters separately. Both final fits must retain the same ordered group axes for this comparison protocol; unequal compositions fail without inferred padding, relabeling or automatic restriction to a common subset.

CellChat's later `min_cells` is a separate, original communication filter. Original 1.1.3 uses its `<= min.cells` boundary. It has no modern `min.samples` filter in this workflow. Its LR centrality uses the raw filtered probabilities, including scores whose permutation p-values exceed the network threshold; the native check preserves that legacy behavior. Aggregated networks use `< threshold`, whereas pathway aggregation uses `<= threshold`.

Select the original database species/categories and original PPI species independently. The PPI resource is taken from the byte-locked original source. Projection requires `0 < alpha < 1` and explicit row/column normalization. Native verification solves the **complete** original PPI space, including unmeasured intermediate genes, then recombines measured rows. It checks the dense PPI export against an independent original-resource decode. The original resources have 557 mouse and 4,815 human genes. The completed workflow controls use the mouse resource. The dense human export is about 487 MB and its cubic solve needs substantially larger planned resources; it is not claimed as a completed human-PPI workflow. An insufficient full-matrix capacity fails before starting R. No network truncation or PPI substitution is performed.

Inference options name the original averaging method, trim, raw/projected choice, population weighting, legacy `do.fast`, bootstrap count/seed and Hill parameters. `trim: null` is supported for `triMean`/`median`; truncated mean requires a numeric trim. Merged overexpression and up/down LR thresholds are independent explicit choices. Bubble source and target lists refer to literal group names. In the original bubble table, `pval` is a **plot significance category** (for example 1/2/3), not the underlying p-value. `prob` is plot-transformed; `prob.original` retains the underlying communication score. Use the original case probability tables for scientific p-values.

`similarity.k: null` retains the original neighbor-count default. `trim_fraction: null` retains all scores; a numeric value explicitly trims within each condition before similarity. Native directed Jaccard treats a positive directed edge as present, including self edges. It preserves caller-supplied original FNN tie choices. Structural distance remains the original R/igraph calculation; native replay checks its complete axes and SNN transport, not an independently implemented structural estimator. The native similarity transport has a declared internal capacity of 10 million matrix/graph cells.

## Runtime, limits and recovery

The controller binds the complete declared R libraries, R executable/runtime files and original source before/after execution, compares 31 installed function bodies/formals with original definitions, captures complete inputs, owns the process, enforces logical time/output bounds, and admits a result only after native audits. Failed worker/results directories are retained as `unadmitted-*`. `COMPLETED.json` may describe a failed/skipped attempt: inspect `result_admitted` and status.

The `limits` object explicitly bounds input/output bytes, genes, cells, files, R wall time, inventoried runtime bytes and native projection working-byte/arithmetic estimates. Java heap, native/R allocations and disk use are not hard OS quotas. Preserve incomplete output after heap exhaustion or process termination, plan permitted resources for complete inputs, and retry new paths. Do not reduce the network, drop cells or replace a source edition merely to pass a capacity gate.

**SKIP3:** skip dependent analysis; supply the named exact permitted runtime/source prerequisite, or leave it skipped. **FAIL2:** admit no result, including earlier completed fits; read the named input/source/runtime/process/capacity cause, preserve all files, correct that cause and retry a new output path. A controlled timeout confirms the owned R process terminates. No source function is patched and no package is installed during analysis.

Portable `--verify` works on Windows Java 21/25 and Linux Java 21 without R. It checks every file, exact captured worker/source profile, complete input axes, network cubes/filters/centrality strengths, native PPI projection, Jaccard/SNN transports, information-flow sums, original fallback diagnostics and bubble original probabilities. It repeats native calculations. It does not refit original R tests or certify producer identity. SHA-256 establishes byte integrity, not authorship.

## Prepare a private original runtime

This development setup reuses the earlier pinned dependency/source/compiler groundwork described in [the original workflow guide](CELLCHAT_WORKFLOW_DEVELOPMENT.md) and [projection guide](CELLCHAT_PROJECTION_DEVELOPMENT.md). Those prerequisites must actually exist and pass; a fresh clone does not include the private R libraries or compiler. Use the named setup scripts/receipts, or supply the exact permitted prerequisites before continuing. No global installation is performed. Missing or forbidden installation produces a prominent warning and skipped dependent analysis.

With that groundwork available:

```powershell
python tools/literature/setup_cellchat_editions.py --allow-install --source-label 1.1.3 --out build/literature-review/cellchat-runtime/edition-1.1.3-user-setup-1
python tools/literature/export_cellchat_legacy_runtime.py --setup build/literature-review/cellchat-runtime/edition-1.1.3-user-setup-1 --out build/literature-review/cellchat-runtime/legacy-user-profile-1
```

Require successful setup/profile statuses, read their warnings/raw installer output, and point your request to the new profile. The setup compiles an owned copy with `--preclean --clean`; original source and pre-existing libraries remain unchanged. Setup/DLL/constructor success alone never admits scientific analysis. Never use a partial profile after a failed export. If installation is not permitted, retain the warning and skip the original-R analysis; native [projection](CELLCHAT_PROJECTION_DEVELOPMENT.md) is a separate no-R capability.

## Evidence, provenance and remaining scope

The [validation receipt](cellchat-legacy-development/VALIDATION.json), evidence archive and read-only verifier retain the tested inputs, complete outputs, original process diagnostics, failed/skipped attempts, source snapshots and licenses. Two compiler builds run actual original fits; a separate fixed original-package script independently refits both baseline conditions and comparisons and matches 58 slot/table checks. Portable verification covers both builds on three JVMs and five complete bundles. The boundary control has a group with 30 cells in one condition and 29 in the other: all 59 cells are excluded by the explicit any-condition rule, and both retained fits still have 108 cells. The matched mouse case validates a singleton overlapping gene and consumed projection. Native SNN controls match original C++ for unique ranks, repeated ranks and infinity.

Earlier unsuccessful compiler/native-auditor/JSON attempts are retained with their actual outcomes; none is relabeled admitted. The later P085 source addendum supersedes the earlier projection-choice uncertainty without changing the earlier frozen receipts.

P085 is [10.1038/s41588-024-01961-x](https://doi.org/10.1038/s41588-024-01961-x). Its [posted author code](https://github.com/swaruplabUCI/DSAD_Spatial_Miyoshi_Morabito_2024/blob/5775f130506d80917461fb27226f44130e2ba783/snRNA/cell_cell_communication/spatial_cellchat.Rmd) supplies the explicit PPI/raw-use and joint-prefilter facts in the [source addendum](cellchat-legacy-development/P085-source-addendum.json). The posted `eval=FALSE` code is not an execution receipt. The authors use eight parallel workers; this adapter uses one for controlled execution. No historical package commit, R environment, preprocessing object or biological result is inferred. The author code was inspected without executing or redistributing it. LIANA/Pearson, CellTrek, and other preprocessing steps remain separate tools.

The tested original CellChat candidate is commit `fea1c8ecdea0f393d32513cd00b866f8b4457839`, which declares GPL-3. Original source/data and the [upstream notice](CELLCHAT_UPSTREAM_NOTICE.txt) retain their licenses. This identifies the tested source candidate, not a proven historical paper commit. Current repository metadata, public resource paths and the exact original member hashes remain in the evidence.

```powershell
python tools/literature/verify_cellchat_legacy_evidence.py --check-current-source
```

Remaining work includes other required source editions, modern paper-specific comparisons, application of all six reviewed CellChat roles and catalog integration. Neither this workflow nor the source addendum completes the full cohort goal.
