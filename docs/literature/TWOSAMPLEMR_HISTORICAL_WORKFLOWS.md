# Original historical TwoSampleMR workflows

The standalone `org.jlinalg.cli.TwoSampleMrHistoricalCli` invokes original
tagged-package functions and retains complete original R objects, raised errors,
RNG endpoints, typed tables, runtime provenance and explicit no-result decisions.
Start with the [executed examples](../../examples/twosamplemr-historical/README.md)
and [qualification/checkpoint](TWOSAMPLEMR_HISTORICAL_CHECKPOINT_2026-11-21.md).
The packaged adapter still labels itself DEVELOPMENT_SCOPE; global registration,
historical clumping, provider coverage and catalog admission remain separate work.

## Select the exact edition and runtime

| Package | Request profile |
| --- | --- |
| 0.4.9 | `TwoSampleMR0.4.9-db55b97a-R4.6.1-Windows64` |
| 0.5.6 | `TwoSampleMR0.5.6-8db843c0-R4.6.1-Windows64` |
| 0.5.7 | `TwoSampleMR0.5.7-592ebe05-R4.6.1-Windows64` |
| 0.6.8 | `TwoSampleMR0.6.8-806703f0-R4.6.1-Windows64` |

Supply an explicit Rscript path and ordered `r_libraries`. User/site libraries
are excluded. All 146 functions for0.4.9 or149 for the later editions have
source-matched body/formal guards before/after execution. Source versions,
method/default lists and package identities are retained. These guards do not
authenticate the complete host/native-DLL stack or prove the paper's execution
commit. The dependencies are present-day installed packages, not dependency-era
reconstruction. Optional provider presence does not establish provider parity.

Version0.4.9's original documentation fails R4.6.1's Rd parser. The reference
private build omits only `man/`, preserving all remaining original bytes and
all146 function bodies/formals. Its license is Artistic-2.0; the later selected
editions use MIT. Full historical source/startup application configuration is
kept private. Restore the checksummed runtime or build a separately audited
profile in isolated libraries; do not install over protected/user libraries or
strip/change scientific source to make installation pass.

## Supported requests

Every RUN requires `schema_version:1`, `mode:"RUN"`, `profile`, `operation`,
`out`, `rscript`, `r_libraries`, an explicit integer `seed` and `preparation_note`.
Unknown keys and unused inputs fail. Paths resolve against the request JSON;
the output must be NEW, use its resolved path and sit outside previous captures.
The recipes show complete requests and literal column names.

| Operation | Input and explicit policy | Original behavior retained |
| --- | --- | --- |
| FORMAT | Raw TSV `data`; `format_policy:"source-defaults-with-overrides"`; `format_options.type` exposure/outcome | Original format_data coercion, duplicate handling, filtering, censored P values and row keep flags |
| HARMONISE | Canonical `exposure` and `outcome`; `harmonise_action`1/2/3 | Allele matching, retained/excluded rows and original log |
| MR | Canonical `harmonised`; explicit source `methods` and parameter policy | Original estimators, defaults, RNG and no-estimate method ledger |
| HARMONISE_AND_MR | Exposure/outcome plus harmonization/MR options | Same original stages, with optional four sensitivity stages |
| MV_HARMONISE | Exposure/outcome plus action | Original multivariable preparation, not a multivariable causal estimator |

MR requests must declare `parameter_policy:"source-defaults-with-overrides"`.
`parameters` contains only selected overrides. The worker invokes the original
`default_parameters()` and `mr_method_list()` rather than feeding modern defaults
to older packages. Version0.4.9 has no `test_dist` or `shrinkage` defaults and uses
`loss.function:"tukey"`; later selected defaults differ. Unsupported options
(including0.4.9 format_data's absent `log_pval`) fail instead of being ignored.

Sensitivity names are `HETEROGENEITY`, `PLEIOTROPY`, `SINGLE_SNP` and
`LEAVE_ONE_OUT`. A source method can return no estimate. Inspect the retained
method-population ledger and warnings; a valid formatting/preparation result is
not a causal estimate. The complete original return retains source classes and
attributes, including information not flattened into TSV exports.

Canonical input requires literal source column names, unique SNP/trait rows
and consistent trait labels; finite standard errors must be positive.
Missing/nonfinite kept MR effect/SE values fail; they are not silently dropped. FORMAT delegates its source
coercion/filtering to original format_data, while preserving the raw input first.
The Java formatter audit covers intake/structure/population, not an independent
reimplementation of every formatting rule.

Version0.4.9 MV output has six members with phenotype-label columns; later
versions have eight members with ID columns/name tables. Original R column order
is preserved even when it differs from Java sorting. NULL outcome-P members
remain absent. Do not assume schemas or infer omitted values across editions.

## Limits, warnings and interpretation

Optional explicit capacities include `timeout_seconds`, `maximum_input_bytes`,
`maximum_output_bytes`, `maximum_rows`, `maximum_columns`, `maximum_entries` and
`maximum_work`. Exceeding a capacity fails; the adapter does not reduce methods,
bootstrap samples or populations to fit. The owned process tree is cleaned on
timeout. The published synthetic timeout control is a lifetime-only harness,
not an R scientific analysis.

**Missing prerequisites: SKIP3, dependent analysis skipped.** Install/download
privately when allowed, or restore the named exact prerequisite. If installation
or execution is prohibited, retain the skip and tell users what they must supply.
An unavailable edition is not replaced with a nearby edition.

**Invalid data/design, numerical, integrity, capacity or process failure:
FAIL2, NO COMPLETE RESULT.** Retain raw originals, failed/partial artifacts and
diagnostics. Correct the reported cause and use NEW output. No automatic repair
of identifiers, units, allele/trait ordering, populations, thresholds, versions,
NaN/Inf or estimator/provider choices is authorized.

VERIFY works offline and checks inventory/source identity, source projections,
supported populations and selected deterministic native statistics. It does not
repeat every provider estimator or make a seal a signature. REPLAY executes the
original workflow in NEW output; inspect it as a new analysis. Linux R-free
verification and Windows original-R execution are distinct qualification claims.

**Not yet qualified:** local/API clumping for these historical profiles,
historical reference/API authentication, optional provider/dependency profiles,
other R/OS hosts, global CLI registration and canonical admission. Original
0.4.9/0.5.6/0.5.7 clump_data lacks local reference/PLINK arguments; do not route
those versions through modern0.7.14 clumping. Version0.6.8 local clumping is next.
