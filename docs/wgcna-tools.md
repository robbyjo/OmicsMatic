# WGCNA prepared workflows

`wgcna-tools` provides native Java components and a typed adapter to original
**R WGCNA 1.72-1 / R 4.2.3 on Linux x86-64**. The pre-existing `network`
command remains available. This command adds source-bound operations and
portable saved verification; it does not reproduce a complete paper.

```text
java -Xmx2g -jar omicsmatic-0.4.1.jar wgcna-tools --request request.json
java -Xmx2g -jar omicsmatic-0.4.1.jar wgcna-tools --verify saved-analysis
```

**Warnings and required actions:** exit **3 / SKIP3** means a required permitted
input, exact R runtime, package or library is unavailable. Dependent analysis is
skipped: restore the named prerequisite or retain the skip. Exit **2 / FAIL2**
means invalid data, changed source/runtime bytes, model, process or capacity
failure. No complete result is admitted. Preserve the failed directory, read
`WARNINGS.md`, `analysis-status.json` and original `worker` logs, correct the
cause, and use a **new** output directory. A directory containing partial TSVs
or RDS files is not evidence of success. Require a completed status and fresh
`--verify` before downstream analysis. Original warnings remain relevant after
a successful exit.

Use UTF-8 TSV matrices with a first header field `id`, followed by unique column
identifiers. Subsequent rows have unique literal identifiers and numeric cells.
Spaces, apostrophes and Unicode are retained. Quoting/escaping, embedded tabs,
control characters, blank cells, duplicate names, automatic name repair and
automatic row alignment are unsupported. `NA`, `NaN`, `Inf`, `+Inf` and `-Inf`
are explicit input tokens. Ordinary numeric overflow fails. Original input bytes
remain in `inputs`; missing/nonfinite values are never silently imputed or
filtered by the Java intake. Original WGCNA may apply its documented internal
algorithms, including eigengene imputation; the original objects are retained.

Start with [the native example](../examples/wgcna-tools/request.json). Every
request requires `schema_version:1`, `mode:"RUN"`, `backend`, `operation`,
`matrix` and `out`. Paths are relative to the request file. Unknown or unused
options fail. Supported operations are:

| Operation | Native Java | Original R | Matrix orientation / extra input |
| --- | --- | --- | --- |
| `RANK_PVALUE` | yes, `calculate_qvalue:false` | yes, original qvalue calls | items by score lists |
| `CONNECTIVITY` | yes | yes | square adjacency, identical ordered row/column IDs; `modules` TSV with `id`, `module` |
| `SIGNED_KME` | no | yes | samples by genes; `eigengenes` matrix with identical ordered samples |
| `NETWORK` | no | yes | samples by genes; explicit `power` |

For rank tests, declare `calculate_qvalue` explicitly. `ties` supports `average`
(default), `min`, `max`, `first`, `last`; `na_last` supports `keep` (default),
`first`, `last`. Random ties and dimension-changing NA removal are not accepted.
`p_value_method` supports `all` (default), `rank`, `scale`. Optional `weights`
are finite nonnegative values in exact column order and are normalized as in
the source. A zero total preserves the source's missing results. Constant
scaled columns, all-missing rows and infinities preserve source behavior.
The native implementation computes **p-values only**, using the source's
`calculateQvalue=FALSE` column names. It never substitutes BH for qvalue.

The source rank statistic uses `(rank - 0.5) / max(rank)` separately for scores
and negated scores. Its extreme-rank p-value is the **minimum** of the two
one-sided probabilities, without doubling. The scaled-score extreme p-value
uses the source's doubled normal tail. These rules differ, including for tied
or missing data. Original qvalue calculations can fail; the failure is retained.
This is an approximate CLT method, not an exact permutation test.
If original qvalue reports an estimated `pi0 <= 0`, retain the failed analysis.
This adapter does not expose alternative qvalue estimators or silently alter
the p-value family. Such a change requires an explicitly chosen, separately
qualified workflow; switching off q-values creates a different analysis.

Connectivity uses **column** sums, omits missing edge values, zeroes the
diagonal and sets within-module connectivity to zero for modules with fewer
than three nodes. With `scale_by_max:true`, scaled zero maxima remain missing;
outside and differential connectivity remain missing as in the source. Grey
is a literal module here and is not automatically discarded. Missing module
identities are unsupported.

Original requests also require `profile:"WGCNA-1.72-1-R4.2.3-linux64"`,
`rscript` and `r_libraries`. The complete exact version and core byte profile is
[packaged with the adapter](../src/main/resources/org/jlinalg/omics/wgcna1721/SOURCE-PROFILE.json).
The upstream source is the [official CRAN 1.72-1 archive](https://cran.r-project.org/src/contrib/Archive/WGCNA/WGCNA_1.72-1.tar.gz),
SHA-256 `1dbf82761ef3e76464b18fc9f698ad0f971aafecabf66ca937b950930bd57fdc`.
Install dependencies only where permitted. Runtimes and third-party source/data
are not bundled in GitHub. An adjacent edition or differently built core
package is not automatically qualified: restore the exact profile or separately
qualify that build. Missing packages/versions skip; changed core file populations,
bytes or original function bodies/formals fail. Sixteen package versions, 214
files across five core packages and eight original functions are checked before
and after execution. This does not attest every dependency, BLAS or host file.

For networks, `network_type` defaults to `signed` (`unsigned` and `signed hybrid`
also supported), `tom_type` to `signed` (`unsigned` supported), and `cor_type` to
`pearson` (`bicor` supported). `max_p_outliers` is available only for bicor
(default 1). Defaults: `deep_split:2`, `min_module_size:30`,
`merge_cut_height:0.15`, `detect_cut_height:0.995`, `reassign_threshold:0.000001`,
`pam_respects_dendro:true`, `seed:54321`, `threads:1`. This wrapper's explicit
minimum module size is 30; it does not infer a paper's unreported choices.
Original `goodSamplesGenes` must accept every supplied sample and feature;
failed QC retains diagnostics and requires explicit input preparation.

The network fit processes all supplied genes in **one block**, records original
RNG states, saves original TOM/RDS objects, and exports complete module labels,
eigengenes, membership, adjacency and connectivity. There is no automatic
block splitting, gene reduction, thresholded edge-only export, manual module
editing or alternate correlation fit after failure. `SIGNED_KME` uses the
selected original Pearson/bicor function and pairwise missingness settings.

Default ceilings: 512,000,000 aggregate input bytes, 2,000,000,000 output bytes,
1,000,000 rows, 10,000 columns, 20,000,000 input matrix entries, 8,000,000,000
estimated working bytes, 10^12 estimated work units and 3,600 seconds.
Each has an explicit `maximum_*` / `timeout_seconds` setting. Input lines are
limited to 16,000,000 characters. Network estimates include dense quadratic
storage and cubic work. These estimates and periodically monitored owned-worker
output/time limits are not OS memory/CPU quotas or guarantees of catching every
escaped descendant. Exceeding a limit fails without truncation. Declare an
appropriate Java heap separately; full 3,600-gene saved verification uses 2 GiB
in the validation environment.

Saved verification works without R. It checks the complete capture population,
hashes, current packaged source/profile, effective request, literal intake,
result identities/shapes and valid probability/membership values. It recomputes
all native results. It does **not** independently refit original R networks,
decode RDS or authenticate execution history against coordinated replacement.
To replay, first verify the prior bundle, copy its `request.json` to a new
request file, resolve captured input paths against the prior bundle, bind the
exact permitted runtime if needed, and set `out` to a new sibling directory.
Never place replay output within a retained capture.

The reviewed paper roles distinguish R rankPvalue, network fitting, connectivity,
Python PyWGCNA, robust rWGCNA and manually edited modules. An adapter's current
availability does not establish author version equivalence or whole-paper
replication. The full 250-paper goal remains open.
