# Original SuSiE fine-mapping workflows

The current source-built JAR provides explicit original **susieR 0.11.92,
0.12.35 and 0.14.2** fitting through a Java file-workflow adapter. It supports
individual-level matrices, version-specific RSS interfaces and sufficient
statistics. The existing native `susie` command is a separate implementation;
this adapter never selects it as a fallback.

Start with the [runnable synthetic example](../../examples/susie-workflow/README.md).
Build current source with `./gradlew executableJar`; published 0.4.0 predates
this entry point. From a shell with Java 17+:

```bash
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SusieWorkflowCli --help
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SusieWorkflowCli --request /absolute/path/request.json
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SusieWorkflowCli --verify /absolute/path/completed-analysis
```

This is currently a standalone packaged class, not a new global
`susie-tools` dispatch entry. The existing global `susie` route does not invoke
the original R package.

## Choose the actual source interface

Each request requires `schema_version: 1`, `mode: "RUN"`, an explicit profile
and model, `out`, `rscript`, and ordered `r_libraries`. Paths are relative to
the request file unless absolute. Existing output is refused. Profiles are
`SuSiE-0.11.92-R4.6.1`, `SuSiE-0.12.35-R4.6.1` and
`SuSiE-0.14.2-R4.6.1`.

| Model | Inputs and required quantities | Original function |
| --- | --- | --- |
| `INDIVIDUAL` | `design`, `phenotype`; optional `variants` prior weights | `susie` |
| `RSS_Z` | `variants`, labeled LD `matrix`, `ld_origin`; modern editions require integer `sample_size` or explicit `"not-provided"` | `susie_rss` |
| `RSS_BETA_SE` | 0.12.35/0.14.2 only: beta/SE `variants`, LD `matrix`, `ld_origin`, `sample_size`, positive `variance_y` | `susie_rss` |
| `SUFFICIENT` | `variants` with X′y, labeled X′X `matrix`, positive `yty`, integer `sample_size` | `susie_suff_stat` |
| `SUFFICIENT_BETA_SE` | 0.11.92 only: beta/SE `variants`, LD `matrix`, `ld_origin`, `sample_size`, positive `variance_y` | `susie_suff_stat` |

**0.11.92 RSS has no sample-size interface.** Its original function uses
arbitrary internal n=2, y′y=1 and fixed residual variance 1. A sample-size
argument is refused for that profile/model. The modern provided-n path uses
the original PVE adjustment. The explicit modern missing-n choice retains its
different source path and warning. No version/interface is silently replaced.
The report/transport `samples` value is supplied n or design row count;
**0 is a sentinel for no supplied n, not a zero-sample fit**. The original
object and captured request retain the actual source convention.

For LD inputs declare `ld_origin` as `"in-sample"` or `"reference"`.
Residual-variance estimation is refused for declared reference LD. Prepare
allele-aligned statistics/LD and covariate-adjusted data as scientifically
appropriate before RUN. Matching ID/order does not prove correct alleles,
ancestry, phenotype models or LD reference selection. The adapter does not
perform those preparations or choose them from paper prose.

## Exact UTF-8 TSV formats

Files are plain tab-separated text, without quoting, embedded controls,
missing numeric cells or comments. IDs are exact, unique, nonblank Unicode;
spaces and leading zeroes are retained. ` a ` differs from `a`. Numeric
tokens must be finite decimal values representable as nonzero float64 when
nonzero; NA/NaN/Inf, hex tokens and numeric whitespace are refused. Original
bytes are captured before a separately recorded float64 representation is
prepared. No normalization, variant filtering or allele flipping is added.

- RSS variants: header `variant_id<TAB>z`.
- Beta/SE variants: `variant_id<TAB>beta<TAB>standard_error`, with positive SE.
- Sufficient-statistic variants: `variant_id<TAB>xty`.
- Those variant tables may have a final `prior_weight` column. Weights are
  nonnegative with a finite positive total; explicit zeros are supported.
  Source prior normalization is retained; a positive term lost to float64
  normalization is refused.
- Matrix: header `variant_id` followed by the exact variant IDs in order;
  each row begins with the same ordered ID. LD requires exact unit diagonal
  and symmetry. X′X requires positive diagonal and symmetry. PSD and joint
  [X y]′[X y] checks use a documented 1e-10 roundoff tolerance without changing
  the supplied matrix.
- Design: header `sample_id` followed by variant IDs; one sample per row.
  Phenotype: `sample_id<TAB>response`, with the exact same sample order.
  Constant predictors/responses are refused. Individual-level optional prior
  weights use `variant_id<TAB>prior_weight` in exact design-column order.

`<TAB>` above means a literal tab character. The example preparer writes valid
files. The R transport reader preserves raw header fields: ordinary R table
reading trims unquoted header spaces even when name checking is disabled.

## Options and resources

Common controls include `effects` (default 10), `maximum_iterations` (100),
`tolerance` (0.001), `coverage` (0.95), `min_abs_corr` (0.5),
`estimate_prior_variance` (true), `estimate_prior_method` (`optim`, `EM`,
`simple`), `prior_tol` (1e-9), `refine` (false), `n_purity` (100) and `seed`
(1). These are passed to the original functions; effect count may be capped
at the number of variables by the source.

`scaled_prior_variance` defaults to 0.2 outside missing-n RSS. Legacy/missing-n
RSS instead uses `prior_variance` (50). Options that would be ignored or
duplicate source-supplied arguments are refused. Residual-variance and
standardization controls are model-specific; legacy RSS fixes them. Individual
`intercept` defaults to true. The complete closed contract is in
`SusieWorkflowContracts.java`; unsupported fields fail rather than being ignored.

Default bounds are 5,000 variables, 1,000,000 samples, 10,000,000 matrix entries,
2 billion estimated work units, 512 MB original input, 2 GB worker output and
3,600 seconds. Increase named limits and Java heap explicitly where appropriate;
integer-overflow, capacity and timeout failures admit no result. The work
estimate is an admission bound, not a prediction of refinement time. Timeout
and output-size monitoring own and clean up the started worker/descendants.
Preparation/PSD checks poll interruption and preserve partials.

## Required runtime and installation

RUN and REPLAY require exact **R 4.6.1**, the selected package edition and the
package versions in
[SOURCE-PROFILE.json](../../src/main/resources/org/jlinalg/omics/susie-workflow/SOURCE-PROFILE.json).
The selected base profile requires optional `Rfast` to be absent. All 95/98/98
literal original function bodies/formals are compared with the loaded
namespace before and after fitting. Original functions and their environments
are not replaced. Package versions/paths and the Rscript hash are recorded;
this is not complete host/native-DLL source authentication.

Download/install where the operating environment permits, into NEW private
libraries. Exact sources for 0.11.92 and 0.12.35 are in the
[official CRAN archive](https://cran.r-project.org/src/contrib/Archive/susieR/);
the selected 0.14.2 source URL/hash is in the profile. Keep the downloaded
archive, installation logs and package/source hashes. Install with the chosen
R version, supplying existing matching private dependencies, for example:

```bash
"/absolute/path/R-4.6.1/bin/R" CMD INSTALL --no-multiarch --library=/absolute/path/NEW/private-library /absolute/path/susieR-source-directory
```

Create the NEW library first. Restore/install the exact dependency editions
named in the profile into separate private libraries as necessary; do not
upgrade global libraries or retained reference prefixes. `jsonlite` 2.0.0 is
also required for the closed JSON protocol. Package/source mismatch or an
unavailable/forbidden prerequisite skips dependent analysis with its name and
required action. Model RUN does not install packages automatically.

The development machine has fresh per-version libraries under active
`build/susie-high-impact-1/runtime-install-1/<version>/library`, JSON support
under `runtime-profile-1/library`, and the unchanged shared dependency library
`build/coloc-high-impact-1/runtime-coloc603-1/library`. These paths are machine
bindings, not portable downloads. Scientific qualification used Windows R;
Linux Java audit checks passed, but an R runtime was absent from the inspected
WSL PATH and no Linux R fitting deployment is claimed.

**Historical 0.11.43:** the exact source has not been established; the requested
CRAN archive returned 404. That explicit profile returns SKIP3. Locate and
validate exact original source before supporting it. Do not substitute 0.11.42
or silently use a newer edition.

## Outputs, audit and replay

Read `analysis-status.json` and **`WARNINGS.md` first**. Raw inputs, original
request, prepared tables, original reference source, runtime/call descriptions,
RNG states, process logs and original/adapter warnings remain in the capture.
`worker/original-fit.rds` is the original unmodified model. Tables export
marginal PIPs/coefficient means, per-effect alpha/mu/mu2/log BFs, prior variances,
objective trace, credible-set membership, mass and purity. Indices are explicitly
one-based. Negative-infinite log BFs retain zero support; they are not capped.

Original summary fits may have an unavailable intercept without supplied means;
source `NA` is retained as typed metadata and a warning, not replaced by zero.
No qualifying credible sets is an explicit empty population; it does not
trigger an automatic single-causal-model fallback. Nonconvergence, invalid
model statistics and inconsistent source credible-set coverage/membership
prevent a complete result while preserving the raw object and partial exports.

VERIFY needs no R. It checks seals, packaged source identity, request/intake,
runtime/entry-point declarations and exported dimensions, identities, numeric
ranges and credible-set consistency. It does not deserialize RDS, independently
refit the model, establish biological/author validity or authenticate coordinated
replacement. An SHA-256 seal is integrity, not a signature.

For actual REPLAY, write a control request and pass it with `--request`:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"/absolute/path/completed-analysis","out":"/absolute/path/NEW/replay"}
```

Optional explicit `rscript` and `r_libraries` bindings allow a new machine
with the same exact profile; they do not permit changing scientific parameters.
The parent capture is verified before the original model is run again.

**SKIP3 — dependent analysis skipped:** restore the named permitted input,
runtime or package where installation/execution is allowed. If it is forbidden
or unavailable, keep the skip visible. **FAIL2 — no complete result:** preserve
inputs, original objects, warnings, partials and diagnostics; correct the named
data/model, convergence, integrity, capacity or process cause and retry NEW
output. Never silently repair IDs, alleles, dtype, thresholds, populations,
nonfinite values or algorithms.
