# Monocle 3 prepared-graph pseudotime development component

`org.jlinalg.singlecell.Monocle3Pseudotime` is a native Java API for ordering an **already prepared projected cell graph**. It implements the numerical steps in the original `extract_general_graph_ordering` and its `find_nearest_vertex` helper. It does not fit a Monocle package workflow. This component checkpoint awards no catalog integration credit; current availability is recorded separately in `omics-2023-2026/tool-inventory.json`.

The separate candidate is `build/literature-review/monocle-runtime/native-build-1/omicsmatic-0.3.7-monocle3-pseudotime-development.jar`. The canonical/released executable is preserved. The candidate adds the API and source notices to the validated ChIPseeker root parent; it changes no existing JAR entry. No new root CLI route is claimed.

## Start with the executed example

Use [the example and copyable PowerShell commands](../../examples/monocle3-pseudotime/README.md). The API needs Java 17 or later and no external runtime. The example selects `cell-b` as the nearest cell to a declared principal root and returns pseudotimes `[3, 0, 5]` in the original cell order.

For real input, retain the original cell/principal-point identifiers and coordinate order. Supply the actual `pr_graph_cell_proj_tree` produced by the intended Monocle preparation and the matching principal-point coordinates. Replacing it with an arbitrary nearest-neighbor graph changes the method. Roots are investigator choices; this API does not infer a biological starting state.

## Numerical contract

The input includes literal cell IDs and their finite coordinates, literal principal-point IDs and coordinates, an explicit list of principal roots, graph vertices in a declared order, and zero-based edge endpoints in that vertex order. All coordinate rows must share one dimension count. Each cell must occur in the graph; additional projection vertices are allowed. IDs are unique within each population, nonempty, and preserved literally, including Unicode, whitespace and the string `NA`.

For each principal root, the method selects the first nearest cell in the supplied cell order. It compares Euclidean distances with the original arithmetic and tie behavior. It then computes the minimum weighted undirected graph distance from the selected cells. Graph loops, parallel edges and zero weights are allowed. `Edge.unweighted(a, b)` explicitly supplies the unit-weight convention used when an igraph graph has no weight attribute. Missing weights are not repaired.

`result.pseudotime()` follows cell input order. `result.graphPseudotime()` follows graph vertex order and includes extra projection nodes. `result.selectedRoots()` records every supplied root, its selected cell and Euclidean distance. Repeated root selections remain in that ledger. Result collections are immutable; returned arrays are copies.

Positive infinity is retained. The implementation does not substitute `NA`, zero or an arbitrary maximum for disconnected or overflowed distances. Squared Euclidean distances can overflow or underflow; a stable `hypot` replacement would change source ties. The source-overflow controls retain the original first-cell selection and graph pseudotime, with additional explicit Java warnings.

The native domain requires finite, nonnegative edge weights and finite coordinates. Negative/nonfinite weights, missing coordinates and ambiguous identifiers are refused. One-dimensional coordinates with multiple roots are also refused: all three original profiles fail after dropping matrix dimensions in that domain. No repaired result is supplied.

Logical limits are one million cells/graph vertices, five million edges, 9,999 principal points/roots, 1,024 dimensions, and 50 million work units by default. An explicit positive work budget can be supplied to the API. These limits are not heap or operating-system quotas. There is no approximate or subsampled fallback.

## Warnings, failures and required actions

**Retain every `result.warnings()` entry prominently.** `ROOT_DISTANCE_OVERFLOW` records an overflowed nearest-cell comparison. `PATH_SUM_OVERFLOW` records an overflowed candidate graph path sum. `INFINITE_PSEUDOTIME` records infinite cell output. Preserve the original values and inspect coordinate scales, edge weights, graph components and declared roots; do not silently rescale, repair or infer biological absence.

**An `IllegalArgumentException` admits no result.** Read its named cause, preserve the input population and correct the exact coordinate/identifier/edge/root/budget problem before retrying. Increasing the work limit is an explicit caller choice. A failure does not authorize switching the graph, changing roots, imputing missing values or dropping cells.

**The original-package adapter is unfinished.** Preprocessing, alignment, embedding, clustering, graph learning, gene-wise models, original R output supervision and paper-specific workflows remain pending. Full runtime analyses must remain skipped until those dependencies and workflows are available and validated. Where installation is forbidden, retain a prominent `SKIP3` record with the affected analysis and exact permitted provisioning steps; do not replace the analysis with this prepared-graph API. The current component itself has no R dependency and provides no upstream analysis or portable `VERIFY` command.

**SKIP3 — original Monocle 3 1.3.1 runtime analysis is skipped in the present R 4.6.1 environment.** Its required archived `grr` 0.9.5 dependency fails to compile because the `STRING_PTR` R API is unavailable. The failed attempt and compiler diagnostics are retained in `build/literature-review/monocle-runtime/reference-setup-1/`; all preexisting R libraries remained unchanged. Required action: preserve that attempt and provision a separately validated compatible R/dependency environment, or declare and validate a distinct newer original profile. Do not silently patch `grr`, substitute a Monocle edition, or admit analysis from the partial installation. A private compiler and shell-helper smoke test now passes without the earlier shell warnings; that does not resolve this R API incompatibility.

## Source and validation scope

Exact official source commits are retained for [1.0.0](https://github.com/cole-trapnell-lab/monocle3/tree/004c096f3a3bdaf1e6709a65fb36f40ba775f1ab), [1.3.1](https://github.com/cole-trapnell-lab/monocle3/tree/b545460966874948eb11a57a225594a107f1694d) and [1.4.27](https://github.com/cole-trapnell-lab/monocle3/tree/536f1033d6de7c957f26a1f403f81efbd825e0db). Original source and MIT notices accompany the candidate under `org/jlinalg/omics/monocle3-pseudotime/`; `SOURCE-PROFILES.json` records commits and file hashes. Source intake, original controls, candidate build and test receipts are under `build/literature-review/monocle-runtime/`.

The controls execute the unchanged original class/accessor declarations and the two original function bodies with R 4.6.1, SingleCellExperiment 1.34.0, igraph 2.3.4 and proxy 0.4-29. They use explicitly constructed synthetic prepared graphs. They do not load or fit the complete Monocle package. The first transport attempt was invalidated because `SimpleList` flattened a nested list; none of its 519 failures are admissible reference results. The corrected attempt retains 516 original returns and three genuine source shape errors across the three profiles.

The candidate passes **529 tests without skips on each of Windows Java 21, Windows Java 25 and Linux Java 21**: 516 complete original comparisons, three original-domain refusals and ten independent API/property checks. Checks include nearest-cell ties, vertex/cell order, multiple roots, loops, parallel/zero edges, disconnected components, projection vertices, overflow/underflow, immutable output, input refusal, exact logical budgets and an independent min-plus shortest-path calculation. The initial Java test reader failed to recognize the project's numeric-token representation; that attempt is retained and excluded. The scientific Java implementation did not change to resolve that reader failure.

These controls certify the stated native component on the admitted domain. They do not certify Monocle graph learning, historical dependency environments, biological accuracy, complete paper pipelines, execution authenticity, the canonical executable, release or deployment. The whole-project test suite has not been run for this isolated component.

## Paper-role work still required

All 250 source XML hashes were checked while collecting private context for the nine Monocle observations. Those uses include Monocle 3 trajectories, Monocle 3 `fit_models` GLMs, Monocle 2 `differentialGeneTest` likelihood-ratio analyses, a modified covariate-aware `graph_test`, the separate `py-monocle` wrapper, and Cicero's Monocle platform. These roles cannot all be credited to pseudotime ordering. Reported 1.3.3/1.3.7 editions and unspecified versions must retain their exact evidence; GENE/GLM settings, transformations, graph construction, resources and roots are not inferred.

Public catalog roles/counts were unchanged at this component checkpoint. Typed original workflows, additional named-software recovery and the nine paper-specific reviews were still open work toward the full 250-paper goal. See the current catalog for later integration milestones; this frozen API guide certifies only its stated component.
