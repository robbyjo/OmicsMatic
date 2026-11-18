# LDSC: native methods and reviewed paper roles

The current source-build toolbox provides native Java prepared-data LDSC
heritability and two-trait genetic covariance/correlation. These count as **one
scoped family integration**. The catalog now contains **128 new scoped
integrations: 24 native Java and 104 upstream**, or 131 including three existing
adapters. There are 260 missing families. The 250-paper catalog and implementation
goal remain open.

## Start with the command guides

Use Java 17 or newer and build the current project source. The published 0.4.0
binary predates these commands. Follow the request examples and output checks in:

- [Native unpartitioned heritability](../vignettes/ldsc-heritability.md):
  `ldsc-tools h2 --request REQUEST.json`.
- [Native two-trait genetic correlation](../vignettes/ldsc-genetic-correlation.md):
  `ldsc-tools rg --request REQUEST.json`.

Supply prepared, ordered SNP tables, explicit sample sizes, reference-variant
counts and model/intercept choices. For two traits, supply common SNPs with
aligned effect alleles. The commands retain the request, input, fitted model,
block/weight diagnostics and warnings. They use Java at analysis time; Python
and the original LDSC runtime are unnecessary for these native methods.

The numerical target is the selected CBIIT 3.0.2 core. Qualification covers
72 original-core heritability models and 72 separate cross-trait models, with
21 focused tests on Windows and Linux. Forty-three packaged controls exercise
success and expected failure/skip paths. All 133 corresponding RG payloads and
96 earlier heritability output-file comparisons match between platforms. The
[RG qualification](ldsc-rg-development/README.md) retains the exact sources,
reference outputs, tolerances, failed attempts and checksums. This catalog
admission rechecks their bindings; it does not rerun the scientific runtime or
claim parity with every historical LDSC edition.

## Read warnings before using a result

**SKIP3 means the dependent analysis was skipped.** Supply the named missing
input or permitted prerequisite, then retry in a **new output directory**.
If installation or access is forbidden, leave the dependent analysis skipped.

**FAIL2 means no complete result is admitted.** Read `FAILURE.txt`, `WARNINGS.md`
and `model.json` when present. Correct the named input, model, population,
numerical, capacity or integrity issue before retrying in a new directory.
Retain the failed attempt and its partial diagnostics.

For genetic correlation, nonpositive full-fit heritability can produce `NA`;
invalid delete-block products can produce `NaN` uncertainty. Keep those outcomes
visible. Inspect both component fits, SNP/reference preparation, sample sizes
and block settings. Do not remove failed SNPs/blocks, force positive heritability,
replace missing values or clamp estimates to obtain a successful result.
The explicit `retain-raw` presentation option cannot repair invalid uncertainty.
The command guides explain the source range policy and every required choice.

## What the 42 reviewed roles establish

The frozen **2023-10-23 through 2026-10-23** cohort remains unchanged. All 250
primary XML hashes were rechecked. A broad LDSC/LD-score spelling scan identified
44 candidate papers; each was reviewed against its source paragraphs. Forty-two
retained family roles include 40 applied method/direct/wrapper roles and two
resource-reuse roles. The
[machine-readable crosswalk](omics-2023-2026/ldsc-source-role-coverage.json)
records source identities, paragraph hashes, summaries, software-edition
provenance and the applicable prepared-data component for each paper.

| Source correction | Catalog treatment |
|---|---|
| P019: bivariate genetic correlations | Add the omitted positive observation and reviewed summary. |
| P092: stratified chromatin heritability enrichment | Add the omitted positive observation; the native unpartitioned methods do not supply its stratified model. |
| P151: comparison with RELI | Remove the previous positive LDSC observation; retain the negative review in the crosswalk. |
| P177: discussion of an alternative method | Keep excluded from positive observations. |
| P220: Neale-lab correlation estimates | Label result reuse; do not infer LDSC execution by this study. |
| P245: baseline LDSC annotations in GENIE | Label annotation-resource reuse; do not infer an LDSC regression fit. |

This adds two observations and removes one: **2,695 observations**, of which
**1,261 uses have individual reviewed summaries** and 1,434 await that review.
The 1,219 earlier positive reviews and every unrelated paper/tool observation
remain unchanged. There are 446 named families and 485 effective registry
definitions, with only the LDSC definition changed by this admission.

The native profiles cover prepared **unpartitioned observed-scale** fitting
components. Liability conversion, munging/allele QC, LD-score generation,
annotation-stratified estimation, enrichment testing and study-specific inputs
still require their own implementation or explicit preparation. Genomic SEM
multivariate sampling covariance, gsMap spatial analysis, CELLECT workflows,
Covariate-LDSC and custom historical pipelines retain separate boundaries in
the crosswalk. A component applicable to a paper does not establish reproduction
of that paper or coverage of every analysis it describes.

Reported LDSC software editions remain separate from annotation resources and
wrapper versions. P115's literal `1.0.11` is preserved. P203 reports `1.0.1`;
the adjacent citation number is excluded. Baseline-LD versions and Genomic SEM,
CELLECT or PESCA versions are not relabeled as LDSC software editions. P220's
reported spreadsheet display zeros are documented as representation limits,
never treated as exact-zero probabilities.

## Recheck the current admission

The new verifier checks the exact predecessor from Git, all frozen primary
source hashes, all reviewed source pointers, the saved scientific qualification
and current scientific source bytes, the eight catalog metadata files and the
complete current catalog checksum manifest. From the repository root, use a
new evidence directory and your permitted primary XML cache:

```powershell
python -B tools/literature/ldsc_hsq/catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/ldsc-catalog-recheck-1
```

Its [admission record](omics-2023-2026/ldsc-catalog-validation.json) binds the
before/after bytes and source census. The [evidence directory](ldsc-catalog/)
preserves the actual predecessor, previous checksum manifest, registry and
verifier snapshot. A reversible review-file journal supports compatibility
audits without changing earlier scientific fingerprints or verifier sources.
Historical checkpoints continue to describe their original catalog state.

The derived local HTML is updated with the same data and prominent actions.
This admission does not publish the website, create a release or push a branch.
