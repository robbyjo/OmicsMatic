# Native LDSC RG/Gencov development qualification

The [method guide](../../vignettes/ldsc-genetic-correlation.md) and
[complete examples](../../../examples/ldsc-rg/README.md) expose native Java
`ldsc-tools rg`. Both Hsq components, Gencov and ratio-jackknife correlation use
the same complete, explicitly prepared common SNP population. One-step fits
support free/fixed intercept combinations. Two-step fits use separate trait
masks and the joint covariance mask, with all intercepts free.

**Require exit0, `COMPLETED.json` and `analysis.json result_admitted=true`.**
Missing/forbidden input produces SKIP3. FAIL2 admits no requested correlation;
preserve `model.json`, component tables, inputs and failure/warning records,
correct the named population, block, numerical or model problem and retry NEW
output. A retained covariance fit is not an admitted correlation. NA, NaN,
finite out-of-range values and source +Inf/zero conventions remain distinct.
The guide provides explicit recovery actions. No Python runtime is required
for native analysis.

The unchanged selected reference is CBIIT LDSC3.0.2 commit
`6c673952cee74bd5c57aef1555a03b1c015399a0`. It runs in the previously qualified
private CPython3.10.11/NumPy1.23.3/SciPy1.9.2/pandas1.5.0 environment. Only the
original numerical modules are loaded under an isolated package namespace;
the unrelated web/package initializer is not executed. A profiler observes
returns without replacing original functions or changing arithmetic. This
does not establish full upstream CLI, legacy edition or web-service parity.

Seventy-two cross-trait models compare **54,202 reference fields**, including
both Hsq components, genetic covariance, normal tests, every component delete,
final row weights, boundaries, feedback estimates and original RG statuses.
The population includes 56 finite raw-inference cases, eight nonpositive-full-h2
cases with original NA correlation, and eight cases with NaN delete-based
uncertainty. It covers negative correlations, fixed/mixed intercepts,
two-step boundary/mask differences, and negative individual delete h2 values.
In particular, two negative delete heritabilities retain their positive product,
matching the source rather than imposing a different positivity rule. The
field tolerance is relative `3e-9` plus absolute `3e-13`; all original values
and statuses remain retained.

The source point is the full-data ratio, with the bias-corrected ratio recorded
separately. Source block-ordinal pairing is retained even when mapped row
boundaries differ among fits. Source-summary suppresses finite `abs(rg)>1.2`;
the explicit retain-raw policy changes presentation/admission only and leaves
the fitted tables unchanged. Neither policy admits nonpositive full h2 or NaN
uncertainty. Two-sided normal probabilities are mathematically equivalent to
the original one-df chi-square test; native log probabilities add resolution
diagnostics. The original zero-SE +Inf-Z convention is explicit.

Twenty-one focused tests pass on Windows and the same 21 on Linux. They include
the earlier 72-model Hsq reference population and the new 72-model RG population;
these are not 42 different test methods or 144 independent RG models. Packaged
validation completes 43 process controls, checking 10,964 original fields.
Every one of 133 corresponding retained RG payloads is byte-identical across
Windows/Linux, including both no-result cases. The two source-range policy
controls preserve identical fitted numeric tables while changing admission.
Both successful and both intentionally failed example requests execute with
their original copyable paths.

The Hsq fixed-final-weight fitting body was extracted into a shared internal
helper; numerical arithmetic was retained. Earlier Hsq output compatibility is
established by **96 byte-identical file comparisons** covering six models on
both platforms, along with the original Hsq reference tests. Existing JAR
members outside the explicitly changed LDSC helper/dispatch classes are
unchanged. Earlier Hsq evidence/source hashes are not refreshed; its original
JAR and qualification snapshot remain separate historical evidence.

The first native probe failed because the derived TSV exporter wrote original
length-one arrays into scalar cells. Original JSON and all previous fixture
bytes are retained. The corrected exporter unwraps only length-one transport
containers, and records the original default-zero initial covariance-weight
intercept. No original function, native numerical algorithm or original value
was altered to satisfy that failure. The initial successful build and later
help/discovery build are both retained.

`VALIDATION.json`, `WARNINGS.md` and `checks.zip` record source, fixture,
provider, artifact and complete archive identities. Original providers are
fingerprinted rather than distributed as an end-user Python deployment.
Hashes establish integrity, not authentic execution or biological validity.
All previous failed/prototype attempts remain preserved.

The prior [source census](../ldsc-hsq-development/SOURCE_CENSUS.json) records
41 canonical LDSC mentions and four targeted semantic reviews. P092's missing
actual stratified role and P151's comparison-only attribution still need a
source-bound canonical transition. The current code does not turn every
mention into an RG application. Liability conversion, stratification, LD-score
generation, munging, reference adequacy, historical whole-package parity and
full-paper reconstruction remain separate work. Catalog counts remain 127 new
scoped integrations; no new family badge, push, release, deployment or full-goal
completion is claimed.
