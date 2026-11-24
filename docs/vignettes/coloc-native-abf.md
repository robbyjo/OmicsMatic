# Native COLOC ABF from regional summary statistics

`org.jlinalg.coloc.ColocAbf.analyze(first, second, options)` implements the
single-causal-variant ABF component audited against coloc **6.0.3**, commit
`27776b1bb14e19d1542798ac7477fa378fc2fc0d`. It returns H0..H4, per-variant
statistics, effective priors, scale estimates and explicit warnings. Start
with the [copyable Java example](../../examples/coloc-abf/README.md).

**This is a tested library component. An ordinary-summary-statistics CLI,
bundle verification/replay and cumulative catalog admission remain open.**
The existing `coloc` CLI/API uses SuSiE signal log-Bayes factors. Its input
contract and default priors have not been changed. No new family credit or
whole-paper reproduction is claimed here.

## Supply the data explicitly

Create one `ColocAbfInput` per trait. Arrays follow its variant-ID order and
are defensively copied. Each input allows 1..1,000,000 variants; that is an
allocation boundary, not a million-variant performance qualification.

| Input mode | Required fields |
| --- | --- |
| Quantitative regression | `beta`, **variance** of beta, and either `traitStandardDeviation` (sdY), or frequencies plus scalar sample size for sdY estimation |
| Case-control regression | `beta` on the log-odds scale and variance; no case fraction is needed for this mode |
| Quantitative P values | P values, frequencies and scalar sample size; the source approximation assumes unit trait variance |
| Case-control P values | P values, frequencies, scalar sample size and case fraction in (0,1) |

When both beta/variance and P values are supplied, beta/variance determine the
ABFs. Supplied optional arrays must still satisfy the native input contract.
Variance is SE squared, not SE. Per-variant sample sizes are unsupported.
IDs must be exact, unique, nonblank valid Unicode strings. No allele or ID
harmonization is performed. Frequencies may be above 0.5 but must be in (0,1);
they are never folded. All scientific inputs must be finite, with positive
variance/sample size/sdY and P values in (0,1]. The P<=1 requirement is an
intentional stricter native contract: the upstream reference can return a
finite value for an invalid probability above one.

Optional `positions` are nonnegative `long` values. If **both** inputs have
positions, shared IDs must also have equal positions; mismatches are excluded
with a warning. If only one input has positions, matching is by ID alone.
Results use deterministic Unicode code-point order matching the reference's
UTF-8 C collation. Supply appropriate regional data and record the upstream
association model, allele alignment, variant coverage and selection choices.

## Priors, outputs and source behavior

`ColocAbfOptions.defaults()` requests p1=p2=1e-4 and p12=1e-5. Each prior must
lie in (0,1). The source allows p12 to exceed p1 or p2; this API preserves that
behavior. If n*p>=1, the relevant prior is adjusted to 1/(n+1) with a warning.
Trait-specific adjustments use original input sizes; p12 uses the merged size.
Quantitative sdY estimation also uses all original trait variants before the
intersection.

Optional nonnegative finite prior weights follow each original input order
and are normalized after intersection. Zero entries are permitted. All-zero
or disjoint support can yield nonfinite source arithmetic and are refused,
without replacing them by uniform weights. Both-trait weighting carries an
independence warning.

The returned `sourceConditionalSharedPosterior` reproduces the source's
`SNP.PP.H4` column: **it is unweighted even when regional H0..H4 use weights**.
It is conditional on H4 and is distinct from the regional H4 probability.
The actual selected source fixes the effect prior SD at 0.15*sdY for
quantitative regression, 0.15 for quantitative P-value approximation and 0.2
for case-control data. A top-level R `effect_priors` override through `...`
is ignored in this pinned source; no effective custom override is exposed.

The source log-difference can produce H3=0 at machine resolution; this is
reported and retained. Negative-infinite individual log-ABFs may be retained
only when the final posterior is finite. No alternate H3 formula or probability
floor is substituted. Weak association (no P<1e-6) and estimated sdY generate
warnings and do not trigger silent row filtering.

**On invalid/nonfinite input or posterior, the API throws
`IllegalArgumentException` and returns no result.** Keep the original data,
options and error; correct the cause before retrying. Applications must print
the returned warnings prominently and preserve them with the result. Missing
Java/build/reference prerequisites require skipping the dependent analysis,
with the exact missing component and restoration instructions. Never fabricate
a completed result or silently alter IDs, alleles, P values or NaN/Inf.

## Evidence and scope

Thirty original-package cases cover both input modes, quantitative/case-control
traits, scale estimation, weights, priors, merge keys, Unicode, source quirks,
eight expected native refusals and a 12,000-by-10,000 regional input (8,000
shared variants). Forty reference/boundary tests passed on Windows Java 21/25
and Linux Java 21. The frozen fixture includes synthetic inputs, original
outputs/RDS, capture code, session information and source identity; tests do
not require R and do not refit original paper data. See the
[compact evidence/readback](../literature/coloc-abf-handover/README.md).

No historical edition parity, multi-causal signal fitting, allele harmonization,
GWAS/QTL fitting, LD generation, sensitivity grid, plotting, whole-tool parity,
biological validation or original-paper reproduction is established.
The [pinned source](https://github.com/chr1swallace/coloc/blob/27776b1bb14e19d1542798ac7477fa378fc2fc0d/R/claudia.R)
and [input checks](https://github.com/chr1swallace/coloc/blob/27776b1bb14e19d1542798ac7477fa378fc2fc0d/R/check.R)
are the primary behavior references. coloc records GPL licensing in its
DESCRIPTION; this implementation carries GPL-3.0-or-later attribution.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Claudia Giambartolomei et al. (2014) — Bayesian test for colocalisation between pairs of genetic association studies using summary statistics](../CITATIONS.md#giambartolomei-coloc-2014) — [PMID: 24830394](https://pubmed.ncbi.nlm.nih.gov/24830394/) · [PMCID: PMC4022491](https://pmc.ncbi.nlm.nih.gov/articles/PMC4022491/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
