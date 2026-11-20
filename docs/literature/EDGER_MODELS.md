# Original edgeR count-model workflows

The development command runs original edgeR filtering, normalization,
dispersion estimation and differential tests from prepared count matrices.
It retains the original model objects, warnings, partials and runtime records.
The supported profiles are **edgeR 3.40.2, 3.42.4 and 4.10.7 on R 4.6.1**.
These are explicit development environments, including the recorded dependency
versions. They do not reproduce every author's historical R/dependency host.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the exact permitted R, package,
dependency or input named in the console and `WARNINGS.md`; otherwise retain
the skip. Whole 3.32.1/3.36.0 packages remain unavailable under this R runtime.
The separately qualified old CPM kernels do not provide differential fitting.
**FAIL2: NO COMPLETE RESULT.** Preserve the attempt, original data, models,
warnings and diagnostics. Correct the named cause and choose NEW output.
A finite p-value table alone does not establish model convergence.

The [native normalization command](EDGER_NORMALIZATION.md) remains available
without R. This model command explicitly uses original edgeR operations;
caller-provided normalization factors can carry a separately prepared native
result. Factor provenance and the appropriate feature population remain the
caller's declarations. No original fit is replaced by a generic empirical
Bayes implementation or an automatic native fallback.

## First run

Use a current source build and a qualified Java 21 or 25 runtime. The published
0.4.0 release binary predates this component.

```text
gradlew.bat executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --help
```

On a Unix host, build with `./gradlew executableJar`. Original fitting has been
exercised on the declared Windows R environment; R-free capture checks are
also exercised on Linux Java 21. A Linux original-R deployment requires its
own exact permitted runtime and qualification.

Start with [the synthetic example](../../examples/edger-models/README.md).
Edit its `rscript` and `r_libraries` fields to name your permitted installation.
The command does not install software or search for a convenient adjacent
edition. Installing exact prerequisites privately is permitted by the project
task where the operating environment allows it; unavailable or forbidden
prerequisites skip the dependent analysis.

```text
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --request examples/edger-models/request.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --verify examples/edger-models/output-NEW
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --request examples/edger-models/replay.json
```

Relative paths resolve against the request's directory. `out` must not exist.
REPLAY uses the saved raw inputs and declared runtime bindings, or explicit
replacement runtime paths in its control request. It requires NEW output
outside its parent capture, including through filesystem aliases.

This is a standalone development entry point. Global command registration
and cumulative catalog admission are separate remaining steps.

## Inputs and identity

All tables are literal UTF-8 TSV, with one header and no quoting convention.
Quotes and spaces are part of an identity; `NA` and `001` remain literal IDs.
Duplicate, blank, control-containing, malformed or differently ordered IDs
are rejected. The command does not sort samples or repair names.

| Input | Required columns and order |
| --- | --- |
| `counts` | `feature_id`, then unique sample columns; nonnegative counts |
| `groups` | `sample_id`, `group`; exactly the count samples in order |
| `design` | `sample_id`, then unique numeric coefficient columns; required for GLM models |
| `libraries` | Optional `sample_id`, `library_size`; positive, exact sample order |
| `norm_factors` | `sample_id`, `norm_factor`; required only for provided normalization |

`group_levels` declares the complete level order; unused levels are refused.
EXACT uses the groups and refuses an unused design. GLM models require a
finite, full-rank numeric design with at least two columns and positive
residual sample degrees of freedom. The caller constructs and interprets
the design, including intercepts, covariates and interactions. Sample metadata
are not converted into an inferred formula. Biological replication,
pseudobulk aggregation and technical-replicate handling require the correct
prepared matrix and independent scientific justification.

`count_units` is explicitly `raw-counts` or `estimated-counts`. Raw decimal
tokens must be exact nonnegative integers no larger than 2^53-1 before
float64 transport; fractional tokens that would round to an integer are
rejected. Estimated counts explicitly allow finite nonnegative fractional
float64 values. CPM, logarithms, residuals and normalized expression are not
silently converted into raw counts. Numeric overflow, nonzero underflow,
invalid UTF-8 and nonfinite input tokens fail.

External identities are preserved in captured raw inputs and published tables.
Inside R, explicit `f1`, `s1`, `g1`, `d1` transport codes represent feature,
sample, group and design positions. Raw R objects retain those transport codes;
the input tables and request provide their literal external identities.

## Request choices

All requests use `schema_version: 1`, `mode: "RUN"`, a supported `profile`,
`out`, the required inputs, `group_levels`, `count_units`, `normalization`,
`filter`, `dispersion`, `results`, `rscript` and `r_libraries`.
Unknown fields and settings unused by the selected model are refused.

| `model` | Original fitting and result selector |
| --- | --- |
| `EXACT` | `estimateDisp` or supplied dispersion, then `exactTest`; `pair` is **denominator, numerator** |
| `GLM_LRT` | `estimateDisp` or supplied dispersion, then `glmFit` / `glmLRT` |
| `GLM_QLF` | Explicit legacy or current adjusted `glmQLFit` / `glmQLFTest` |

Each `results` item has a unique literal `id`. An EXACT item declares two
distinct group labels in `pair`. A GLM item declares either unique **1-based**
`coefficients` or one finite nonzero `contrast` vector whose length equals the
design width. Multiple coefficients form one joint test; they do not become
separate pairwise tests. Contrast weights refer to the declared design order.
The full retained feature population is tested and published without an
implicit significance or fold-change cutoff.

### Normalization and filtering

`normalization.method` is `TMM`, `TMMwsp`, `RLE`, `upperquartile`, `none` or
`provided`. TMM methods accept a 1-based `reference_column`, `logratio_trim`,
`sum_trim`, `do_weighting` and `a_cutoff`; upper-quartile accepts `p`.
TMMwsp's original source accepts but does not apply Acutoff; a supplied
nondefault value is explicitly warned about. Provided positive factors are
used literally, with no geometric-mean recentering or inferred provenance.

`filter.method` is `none` or `filterByExpr`. The latter requires:

- `basis`: `group` or, for a GLM, `design`.
- `order`: `before-normalization` or `after-normalization`.
- Optional source arguments `min_count` (10), `min_total_count` (15),
  `large_n` (10) and `min_prop` (0.7).
- `keep_library_sizes` (false): controls the original DGEList subset operation.
- For after-normalization filtering, `renormalize_after_filter` (false):
  explicitly requests a second original normalization calculation when true.

Before-normalization filtering starts with raw/supplied library sizes and
unity factors. After-normalization filtering uses the first normalized object.
When library sizes are recomputed during subsetting, retaining or recomputing
the normalization factors follows the declared order/options. Provided factors
cannot be recomputed automatically. An empty original keep mask is retained
and fails before a differential fit. `filterByExpr` is not a substitute for
a paper's different raw-count or CPM prevalence rule.

### Dispersion and QL editions

`dispersion.method` is:

- `estimateDisp`: original common, trended and optional tagwise estimation.
- `provided`: one explicitly supplied finite nonnegative `value`.
- `adjusted-QL-auto`: current 4.10.7 adjusted QL's own original common-dispersion
  route; requires `model: "GLM_QLF"` and `fit.ql_mode: "adjusted"`.

`estimateDisp` accepts `trend_method` (`none`, `loess`, `locfit`, `movingave`,
`locfit.mixed`), `tagwise`, `robust`, `prior_df`, `span`, `min_row_sum`,
`grid_length`, `grid_range`, `winsor_tail_p` and `tolerance`. Defaults are
recorded explicitly in `effective-request.json`. Historical papers' separate
power-trend procedures are not replaced with a convenient modern default.

EXACT fit options include `dispersion_selector` (`auto`, `common`, `trended`,
`tagwise`) when estimated, `rejection_region` (`doubletail`, `deviance`,
`smallp`), `big_count` and `prior_count`. An absent requested dispersion fails.

GLM fits accept `prior_count` (0.125). QL additionally requires `ql_mode`:
`legacy` for the older profiles; `legacy` or `adjusted` for 4.10.7. Estimated
QL dispersion requires the explicit `dispersion_selector` `common` or
`trended`. QL options also include `abundance_trend`, `robust`,
`winsor_tail_p`, `poisson_bound` and, for adjusted-auto only, `top_proportion`.
The current adjusted source disables the legacy Poisson bound internally;
capturing the original formal argument does not turn that legacy procedure
back on. Source behavior is retained and independently compared.

`adjust_method` is `BH` (default), `BY`, `holm`, `hochberg`, `hommel`,
`bonferroni` or `none`, through original `topTags` over the full tested
population with `sort.by="none"`, `p.value=1`, `n=Inf`. Reporting thresholds
are separate scientific decisions. A `seed` and original RNG states are saved.

## Results, verification and failures

Exit 0 means this declared request completed with its warnings and scope.
Read `analysis-status.json` and `WARNINGS.md` first. Exit 3 skips the dependent
analysis; exit 2 admits no complete result. Failed/skipped captures cannot
pass VERIFY even if some tables or model objects exist.

`results/index.tsv` maps literal result IDs to result files. Published tables
include filter masks, retained counts, normalization/library sizes, CPM/log
CPM (explicit prior count 2), dispersions and original test statistics and
adjustments. GLM captures also include coefficient and fit-diagnostic tables.
`worker/objects.rds` retains complete prepared/estimated DGELists, full fits,
observed intermediate fits, tests and top tables. Diagnostics retain source
`NA`/`Inf` where they represent declared absent or boundary quantities.
Source full result tables and published model coefficients must be finite.

The model worker checks original function bodies/formals against unchanged
sources. Unchanged `glmFit`, `predFC`, `glmQLFit`, `glmLRT` and `glmQLFTest`
bodies execute with explicit DGEList/matrix dispatch that observes original
returns. This retains full, prior-count, null and optional Poisson fits that
the stock API can otherwise discard. Original namespaces are checked unchanged.
Exposed `failed` flags and the source hardcoded 250-iteration limit are checked.
This does not establish convergence of lower-level quantities for which the
stock implementation exposes no flag.

Original all-zero Levenberg internal rows return `NA` coefficients, zero
counts/means/deviance/iterations and `failed=FALSE`. That exact unidentifiable
internal boundary is recorded, with its source `NA` values preserved. Other
nonfinite fit outcomes fail. Original one-way source routines apply their own
documented internal coefficient bound; the adapter does not introduce a new
clamp or replace the original algorithm.

**A real qualification counterexample matters:** old 3.40.2/3.42.4 null fits
returned finite stock test tables and `failed=FALSE` after 251 iterations.
Those requests are now FAIL2. The same original input converged in 4.10.7
in 90 iterations. Each edition retains its own outcome; there is no automatic
upgrade. Earlier unguarded numeric comparisons remain historical evidence,
and their affected completed captures are not current admitted results.
Separate positive-suite intercept designs and hypotheses are explicit NEW
inputs; the earlier matrices, requests and models remain untouched.

VERIFY needs Java and saved files, without R. It checks closed file populations,
source resources, original/effective requests, deterministic input transport,
literal populations, effective-library multiplication, CPM/log CPM, coefficient/contrast
effects, supported adjustment arithmetic, source result schemas, diagnostics
and warning/recovery text. It checks BH/BY/Bonferroni arithmetic independently;
other supported adjustments retain source tables and structural/probability
checks. It **does not refit NB/QL models, parse binary RDS model contents,
authenticate an installed native DLL's derivation, detect every coordinated
replacement, or establish biological validity**. SHA-256 supplies integrity,
not authentication or a signature. The independent R reference uses the
stock exported API and compares complete model objects, tests, tables and RNG.

Capacity limits are explicit request fields: input/output bytes, features,
samples, count entries, design columns, estimated dense work and timeout.
The capture inventory also bounds file/directory counts. Comparison metadata
is limited to two million bytes; result selectors are capped at 256. A limit
does not cause silent truncation, subsampling or approximate fitting. Only the
worker process tree started for this request is terminated on interruption,
timeout or output overflow; originals and partial evidence remain available.

## Source and paper scope

The packaged profile retains 425 unchanged R/C/C++ and metadata files from
the three exact original archives, with URLs and SHA-256 pins. It records
loaded package versions and DLL paths, rather than claiming full host/native
binary source authentication. Original edgeR sources are GPL >=2; adapter
code is GPL-3.0-or-later. See the packaged attribution notice and the
[official edgeR package page](https://bioconductor.org/packages/edgeR/).

The [17-role source census](edger-normalization-checks/CENSUS.json) remains
separate from admission. It distinguishes source editions, normalization,
dispersion/GLM roles, limma steps, filtering uncertainties and P088's historical
fork/code-availability role. These workflows operate on prepared count matrices.
Aggregation, quantification, clone-level designs, limma/voom, inverse-normal
transforms, PEER and complete authors' analyses require their own scopes.
Full 3.20.1/3.22.3/3.32.1/3.36.0/3.38.4 fitting profiles are not supplied here.

At this component checkpoint, the cumulative catalog has 138 admitted
integrations, 1,503 reviewed uses and 1,201 pending. This component milestone
does not assign new family or review credit.
