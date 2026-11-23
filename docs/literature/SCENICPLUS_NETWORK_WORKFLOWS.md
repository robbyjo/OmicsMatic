# Prepared SCENIC+ network workflows

`org.jlinalg.cli.ScenicPlusNetworkCli` calls the unchanged SCENIC+ v1.0a2 APIs for
TF-to-gene inference, region-to-gene inference, GSEA eRegulons, triplet ranking
and gene/region activity. The Java controller captures local inputs, checks the
private runtime and owns execution. All generated captures are private.

Start with [the runnable artificial example](../../examples/scenicplus-network/README.md).
It chooses explicit inference seeds and an interface; those choices do not
reconstruct unreported author settings. This prepared core does not complete
unpaired/metacell preparation, motif discovery/Cluster-Buster scanning, global
dispatch or guarded SCENIC+ admission. It earns zero new family/review credit.
Current admitted totals remain 155 scoped families, 1,702 reviewed and 1,020
pending uses across the frozen 250-paper cohort.

## Warnings and required actions

| Outcome or restriction | What to do |
| --- | --- |
| **PRIVATE capture and runtime.** Original vendor traces can contain licensed source. | Keep raw captures, original source, runtime and caches private. Follow the user-accepted [academic license](https://github.com/aertslab/scenicplus/blob/v1.0a2/LICENCE.txt), including copying/transfer and citation terms. Whole-WSL transfer is subject to those restrictions; GitHub is not a runtime backup. No vendor code is included in this integration. |
| **Source interfaces differ.** Original CLI CSV parsing changes some floating-point values between steps. | Explicitly select `CLI_TSV` or `IN_MEMORY_API` to match the intended analysis. Keep the parser-boundary ledgers. Do not silently interchange them, round values to hide differences or infer an author's interface. |
| **Merged source objects omit GSEA statistics.** | Read `MODULE-WARNINGS.md` and `module-ledger.jsonl`. Null/nonfinite NES, p-value and adjusted-p fields remain unavailable; a successful call supplies none by inference. The separate unmerged API option changes the requested analysis and retains the statistics actually returned by that source path. |
| **TF-to-gene self-target rows are injected upstream.** | Preserve the original weights and distinguish these rows from fitted regulatory effects. The pinned API adds TFs as their own targets with source-defined importance; its warning is retained in the worker log. Do not interpret the edge count as a fitted-model count. |
| **Masked-call source failure.** The tested masked operation reproduces an original Boolean-index shape error. | Keep it FAIL2; obtain a vendor-supported correction or separately qualify a permitted upstream edition. Do not patch licensed vendor code, reshape data to conceal the failure or silently disable requested masking. |
| **SKIP3: dependent analysis skipped.** | Restore/install the named exact permitted runtime/reference/input, or retain the skipped analysis. If installation/download is unavailable or forbidden, skip that dependent analysis and continue independent work. Cell Ranger remains excluded. |
| **FAIL2: no complete result.** | Preserve all raw, partial, failed and undefined evidence. Resolve the named source/input/numerical/deadline/integrity/capacity cause and retry NEW output. Do not repair IDs, replace NaN/Inf or refresh historical hashes to obtain a passing capture. |

The complete result status is the outer `COMPLETED.json`, together with all
warnings and field/source-statistic flags. The worker's report remains a
pre-admission upstream record. Finiteness describes a numerical field; it does
not establish model fitting, significance, biological validity or whole-paper
reproduction. Inner per-target fit status is not exposed by the selected API.

## Inputs

Schema1 RUN requires `mode=RUN`, `profile=scenicplus-1.0a2-network-v1`,
`operation=NETWORK`, `python`, NEW `out`, `inputs` and `options`. Relative paths
resolve against the request file. The output parent must already exist and
resolve physically without aliases. Unknown request/option fields fail.

| `inputs` key | Required contents |
| --- | --- |
| `expression` | Literal UTF-8 cell-by-gene TSV, first header `cell`, unique nonempty cell/gene labels and finite float64 values. At least two cells. |
| `accessibility` | Same format with region columns; identical ordered cells. No inferred pairing, sorting, normalization, filtering or downsampling. |
| `search_space` | Exact `Name`, `Gene`, `Distance` header; unique region/gene pairs naming supplied features and canonical signed int64 distances. Supply the actual intended search space; genome assembly and distance construction are not inferred. |
| `tfs` | Unique literal TF names, one per line; one final line terminator is allowed. The original API receives the requested list. Requested names absent from expression are recorded; their presence or fitting is not invented. |
| `cistromes` | Original Boolean AnnData `.h5ad`: regions on observations, TFs on variables, with explicit comma-separated `motifs` annotation. Unique axes. Integer/score matrices are not silently cast to Boolean. Materialize external/soft/virtual HDF5 links before capture. |
| `ranking_db` | Explicit local original Feather-v2 region-by-motif ranking reference, consistent with cistrome coordinates and motif names. It is captured with a filename preserving the original loader's required suffix. |

The TSV matrix parser materializes **C-contiguous float64 cells-by-feature
arrays**, matching the tested original MuData path. Input representation matters:
the first independent API reference used a Fortran-order frame with bitwise
identical values and differed in 35 correlations by at most
3.3306690738754696e-16. That result remains retained. The separate reference
through original MuData matches the declared C representation; no values,
controller/vendor source or comparison tolerances were changed to reconcile it.

Literal `NA`, `0001` and Unicode cell labels are preserved. The original CLI's
intermediate parser can coerce feature identifiers such as literal `NA`.
`CLI_TSV` detects an identifier change and fails with the raw outputs retained;
it does not silently repair or accept the changed identity. Qualify the intended
source interface and input provenance before retrying that analysis.

## Scientific settings

Declare `options.interface` explicitly:

- `CLI_TSV` materializes the original adjacency and eRegulon TSVs and applies
  unchanged `pandas.read_table` defaults at the same downstream boundaries.
- `IN_MEMORY_API` passes returned frames directly between original APIs.

Both use original functions. `CLI_TSV` describes the serialization boundaries;
additional explicitly requested API thresholds are retained. The tested original
CLI comparison uses NES threshold 0, adjusted-p threshold 1, merged eRegulons,
activity fraction 0.05 and no activity normalization, matching that CLI's choices.
Other API configurations are separate analyses with their full options recorded.

Explicit `tf_seed` and `egrn.seed` are required uint32 values. `tf_method` is
original GBM or RF; R2G uses original GBM importance defaults with explicit/source
default `r2g_correlation` SR or PR and Boolean `mask_expr_dropout`. The actual
resolved parameters are saved in `invocation.json`. One worker and one per-process
BLAS/JIT thread are used in this profile; no original author seed is inferred.

The `egrn` object supports original quantiles, top-N heuristics, BASC selection,
ranking fields, rho dichotomization, activating-only selection, region/gene
minimums, NES/adjusted-p thresholds, annotation mode and `merge_eRegulons`.
Empty heuristic lists disable that heuristic. The supported target minimum is
at least two. Defaults are the pinned original API's settings. The `activity`
object supports original `auc_threshold` and `normalize`. Original default
activity-ranking and triplet-tie RNG behavior is retained in each fresh worker.
Do not treat the example's selected settings as paper-specific settings.

## Outputs and verification

The original TF2G, R2G and eRegulon TSVs retain complete source rows and values.
`activity.h5mu` retains gene/region matrices; `gene-activity.tsv` and
`region-activity.tsv` expose their complete scientific values and axes.
`scientific.jsonl` streams typed values/dtypes, including float hexadecimal
encodings, without building a second full result in memory. `field-ledger.tsv`
stores per-row finite masks in the numerical-column order declared in the
summary. Unreturned candidates are listed separately, with no invented cause.

`module-ledger.jsonl` retains returned source attributes and finite-statistic
flags, including null/absent fields. The TF selection ledger records requested,
present and absent names. The two interface-boundary JSON files record actual
source parser conversions. HDF5 encoding bytes can differ independently of the
complete matrix views; fresh verification compares those scientific views.

Use `--request JSON` on the Java class for RUN, VERIFY or REPLAY. VERIFY/REPLAY
require `schema_version=1`, the selected mode, read-only `analysis`, NEW disjoint
`out` and optional `python`. They inherit the parent's interface and scientific
settings and **actually repeat all source operations**. Full comparison covers
complete streamed values, five scientific TSVs, module/field/TF/parser/candidate
ledgers, invocation, summary and runtime fingerprint. `--inspect CAPTURE` checks
saved closure and the exact producer only; it performs no fresh science.

Java acknowledges the worker after checking captured inputs/caller/profile and
fingerprinting declared Python/package/stdlib roots. It repeats the runtime
fingerprint afterward, checks loaded-module coverage and source populations,
and tracks the owned root and observed descendants. Captures bind the exact
complete source-built JAR. Loose/mixed classes and existing outputs are refused.
The scientific runtime is unchanged Python 3.11.8, SCENIC+ v1.0a2, 228 installed
distributions/231 loaded metadata bindings, 41 SCENIC+ and 73 pinned Git Python
members. Bundled metadata names retain distinct origins.

Limits are monitored workspace/file counts and worker wall time. Defaults are
2 GiB combined inputs, 2 GiB per member, 8 GiB capture output, 50,000 files and
1,800 worker seconds. Explicit requests can raise those byte bounds up to 64,
32 and 256 GiB respectively; excessive inputs fail instead of being trimmed.
The runtime fingerprint has its own 16 GiB/100,000-file capacity. No OS job,
hard aggregate RAM quota, complete OS loader dependency capture, escaped-child
guarantee or host authentication is claimed. Bytecode caches are excluded from
runtime roots and use private capture cache paths. SHA-256 establishes integrity,
not execution authenticity or absence of changes restored between observations.

The [earlier handover](HANDOVER_SCENICPLUS_2026-11-23.md) records private runtime
and source locations and the six unadmitted roles. Continue original unpaired
metacells/preparation, motif discovery/resource scanning, global dispatch and
guarded role/family admission, including the missed P153 observation and unread
Supplementary Note2. The original 250-paper window remains 2023-10-23 through
2026-10-23. The same 33 website citation-panel failures remain; both literature
reports stay withheld from Pages. Preserve all private evidence, exact producers,
19 worktrees and restricted historical refs; do not export rejected histories.
