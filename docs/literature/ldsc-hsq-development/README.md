# Native LDSC Hsq development qualification

The [method guide](../../vignettes/ldsc-heritability.md) and
[complete examples](../../../examples/ldsc-hsq/README.md) expose the native
`ldsc-tools h2` command and Java `LdscHeritability` API. This adds the prepared,
unpartitioned observed-scale heritability component to a family with 41 current
cataloged mentions. It does not attribute this univariate method to every
mention or reproduce whole paper pipelines.

The implementation supports free-intercept one-step, explicitly fixed-intercept
one-step, and two-step intercept estimation. It preserves exactly two IRWLS
weight updates, strict cutoff membership, source block placement/mapping,
initial-weight correction for intercept uncertainty, the source's N-scaled
first-stage weight update and its literal lambda-GC constant. Centered weighted
moments and stable block merges provide an independent Java fitting route.
Final negative/out-of-range h2 estimates remain unchanged; original weight
feedback bounds and LD weighting floors are recorded as warnings.

**Warnings and required actions:** require exit0, `COMPLETED.json` and
`analysis.json result_admitted=true`. Missing/forbidden inputs and explicitly
unsupported analyses produce SKIP3; supply permitted prepared inputs or a
qualified required method. FAIL2 admits no result: preserve inputs/partial
outputs, read `FAILURE.txt`/`WARNINGS.md`, correct the named population, block,
format or numeric-capacity issue and retry NEW output. Undefined diagnostics
remain NA; zero-h2 category-proportion diagnostics, rank-deficient delete fits
and unrepresentable arithmetic refuse completion. No Python installation is
required for native analysis.

The independent reference executes unchanged numerical modules from CBIIT LDSC
3.0.2 commit `6c673952cee74bd5c57aef1555a03b1c015399a0`. Its package/web initializer
is deliberately not executed: a private package namespace loads the original
regressions, IRWLS and jackknife modules without altering their files, functions
or arithmetic. A Python profiler observes weight/stage returns without replacing
those functions. This qualifies the selected Hsq core, not the full Python CLI,
web service, munging, LD-score generation or legacy bulik runtime.

A private Windows CPython 3.10.11 embedded runtime uses NumPy 1.23.3, SciPy 1.9.2
and pandas 1.5.0, matching the selected core's declared numerical versions.
Only its private `_pth` configuration was changed to enable private extracted
wheels; the original configuration is retained. HTTPS intakes, wheel/source
identities, licenses, runtime manifests and original outputs are preserved.
The older bulik source is separately captured for provenance; it is not claimed
to have executed under this Python 3 environment. No system Python installation
or unrelated package library was modified.

Seventy-two original reference models cover unbalanced block sizes, varying N,
signed/subunit LD scores, free and fixed intercepts, negative and above-one
weighting estimates, strict-cutoff equality, leading/trailing excluded rows and
both one/two-step workflows. The fixture compares **12,034 fields**, including
estimates, standard errors, covariance, every block delete, final row weights,
boundaries and weight-update parameters. The selected Java tolerance is relative
`2e-9` plus absolute `2e-13`; source outputs remain unchanged.

Eleven focused tests pass on Windows and the same eleven on Linux. Packaged
checks run six representative original models on both platforms, eight expected
input/refusal processes and the two actual copyable example requests. Together
with the Linux test process this is 23 process controls. The packaged comparisons
check 1,218 original fields; all **48** corresponding scientific/audit files are
byte-identical between Windows and Linux. These reuse the original 72-model
reference population, not an additional 72 independent models. Every preexisting
JAR member is unchanged except the explicitly updated root dispatcher.

Earlier attempts are retained. The first build had an incorrect root-dispatcher
variable name and failed compilation. The first source-census assumption equated
raw-body regex matches with canonical extraction, overlooking inline citation
formatting and semantic context; the diagnostic preserves both differing sets.
The corrected census verifies exact canonical paragraph hashes. An example
preparation helper initially refused the already-created README directory
before writing data; its corrected helper refuses overwriting generated inputs.
No numerical algorithm was changed to mask a failed scientific reference.

## Pending canonical source corrections

All 250 primary XML identities and the 41 current canonical LDSC records are
checked in `SOURCE_CENSUS.json`. Four targeted semantic reviews establish:

| Paper | Finding |
| --- | --- |
| P072 | Methods describe European/East-Asian genome-wide heritability in a PESCA workflow. Exact author reference/QC/version settings still matter. |
| P092 | Methods actually use sLDSC baseline-LD2.2 with supplied peak annotations and additional conditioning. This family role is missing from the current catalog; the new univariate method does not supply its stratified model. |
| P151 | RELI is compared with stratified LD-score regression. The cited paragraph does not establish LDSC execution; its canonical role attribution needs correction. |
| P177 | Discussion lists LDSC as a possible alternative; the study used a simple Z-score method. This is not a missing execution record. |

The source pointers and paraphrases are retained. A source-bound canonical
transition remains pending; the current metadata and historical qualifications
are untouched. The census is not a completed individual review of every LDSC
role. Catalog counts remain 127 new scoped integrations; no family badge is
awarded for this standalone component.

`VALIDATION.json`, `WARNINGS.md` and `checks.zip` record exact current source,
fixture, original-core, artifact and evidence identities. Original providers are
fingerprinted rather than bundled as an end-user deployment. Genetic correlation,
stratified/overlap enrichment, liability conversion, LD-score construction,
summary-statistic munging, historical edition parity and full-goal completion
remain separate work. Nothing was pushed, released or deployed by this checkpoint.
