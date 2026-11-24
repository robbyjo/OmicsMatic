# Native LDSC genetic correlation

`ldsc-tools rg` fits two trait-specific heritabilities, genetic covariance and
genetic correlation from one explicitly prepared common SNP population. It runs
in Java; Python and LDSC installations are not required. The Java API is
`LdscGeneticCorrelation.fit(z1,z2,ld,weightLd,n1,n2,M,blocks,
interceptHsq1,interceptHsq2,interceptCovariance,twoStep)`.

**Require exit0, `COMPLETED.json` and `analysis.json result_admitted=true`.**
SKIP3 skips unavailable/forbidden input: supply a permitted accessible file.
FAIL2 admits no requested correlation. Preserve `model.json`, component tables,
inputs and `FAILURE.txt`/`WARNINGS.md`; correct the named population, block,
numeric or model problem and retry **new** output. Do not drop failed SNPs or
blocks, flip signs, clamp estimates or replace NA/NaN to force success.

## Run the complete examples

Build current source; the previously released JAR does not contain this command.
Use `gradlew.bat` for the Windows build.

```text
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --request examples/ldsc-rg/one-step.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --request examples/ldsc-rg/two-step.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --help
```

The [complete example directory](../../examples/ldsc-rg/README.md) includes two
intentional FAIL2 controls: nonpositive full heritability and nonfinite
delete-based uncertainty. Their component fits and diagnostics remain retained.
These are synthetic controls, not biological or ancestry/reference validation.
Output must not exist. Relative paths resolve against the request file.

## Prepare the common population

Input is UTF-8 literal TSV with the exact header below. These two rows illustrate
the format; a fit requires more rows and valid complete/delete designs.

```text
variant	z1	z2	ld_score	weight_ld_score	n1	n2
rsExample1	1.25	-0.81	12.7	8.2	50000	42000
rsExample2	-0.42	0.35	25.4	16.3	48500	40500
```

Both signed Z columns must already use the **same effect allele**, common SNP
intersection and intended LD reference. Rows must be QC-filtered and
genome-ordered. IDs are unique/literal, all numbers finite, and per-variant N
positive. Signed LD estimates are retained. Harmonization, joining, region
exclusion, N inference, ancestry/reference choice and missing-row removal are
explicit preparation responsibilities. An alignment declaration records the
caller preparation; it does not independently prove allele matching.

Supply every request field explicitly:

```json
{
  "schema_version": 1,
  "input": "common-snps.tsv",
  "out": "new-rg-result",
  "reference_variants": 1000000,
  "n_blocks": 200,
  "trait1": "Trait A",
  "trait2": "Trait B",
  "heritability_intercepts": ["free", "free"],
  "covariance_intercept": "free",
  "two_step": 36,
  "out_of_range_policy": "source-summary",
  "data_provenance": "Replace with both GWAS sources, LD reference, build and complete QC/intersection population",
  "row_order_declaration": "Replace with the genomic ordering used for these common rows",
  "allele_alignment_declaration": "Replace with how both Z columns were aligned to the same effect allele"
}
```

M is the supplied reference SNP count, which can differ from regression rows.
Use `two_step=null` for one-step fits; each intercept can then be `"free"` or
an explicitly justified JSON number. No sample-overlap or zero intercept is
inferred. A nonnull two-step cutoff requires **all three intercepts free**.
Heritability uses each trait's own chi-square strictly below the cutoff;
covariance uses the intersection of both masks. All common rows return for the
final slopes.

Whole-input bounds are 64 MiB, two million rows and 2–10,000 blocks, subject to
valid full/delete fits. These numerical bounds do not establish that a small
number of genomic blocks provides reliable inference. Excess capacity is refused.

## Interpret reportability and warnings

| Condition | Result |
| --- | --- |
| Positive full h2 values and finite correlation uncertainty | Admit if the selected presentation policy permits the raw estimate. |
| Either full h2 is nonpositive | Retain Hsq/Gencov fits and original correlation `NA` fields. FAIL2 admits no correlation. |
| A delete h2 product is negative | Retain finite raw rg and `NaN` uncertainty/test diagnostics. FAIL2 admits no correlation. |
| Both individual delete h2 values are negative | Their product is positive under the original rule. Retain the finite calculation with a prominent warning. |
| Undefined zero full/delete category-proportion diagnostic, rank deficiency or unrepresentable arithmetic | Refuse explicitly; preserve outputs already written. |

`NA` means an unavailable/not-estimated diagnostic; `NaN` means a nonfinite
numerical diagnostic. They are distinct. A retained covariance table from a
failed request is not an admitted correlation.

`source-summary` follows the original suppression of finite `abs(raw_rg)>1.2`;
such requests retain the fit but return FAIL2. `retain-raw` follows the source's
explicit `summary(silly=true)` presentation choice, retaining unchanged finite
out-of-range values with warnings. It does not repair nonpositive h2, NaN
uncertainty or model adequacy. Values between absolute 1 and 1.2 also remain
unchanged with a physical-range warning. Investigate power, reference/QC and
model choices; do not use a presentation setting to conceal invalid inference.

## Read the retained outputs

| File | Meaning |
| --- | --- |
| `source-request.json`, `source-input.tsv` | Complete original request and common-population bytes |
| `heritability.tsv` | Both Hsq estimates/SEs, coefficients, intercepts and chi-square summaries |
| `genetic-covariance.tsv` | Observed covariance/SE, coefficient, intercept, mean Z product and normal test/log probability |
| `correlation.tsv` | Full-data raw rg, ratio-jackknife SE, separate bias-corrected estimate, tests, status and policy |
| `delete-values.tsv` | Every component delete/intercept, h2 product and delete ratio |
| `parameter-covariances.tsv` | All three two-parameter jackknife covariance matrices |
| `block-boundaries.tsv` | Complete-row and first-stage boundaries for each component |
| `row-audit.tsv`, `weight-updates.tsv` | Every row/mask/final weight and all weighting feedback updates |
| `model.json`, `WARNINGS.md` | Effective choices, retained components and observed warnings |
| `analysis.json`, `manifest.json`, `COMPLETED.json` | Admission status and successful-result integrity records |

The reported point is the full-data covariance divided by the square root of
the two full-data heritabilities. The bias-corrected ratio estimate is separate;
it is not substituted into the reported point or normal test. SE uses the
original ratio jackknife.

Two-step masks can map components to different complete-row block boundaries.
The selected source pairs deletes by ordinal; this is retained and warned about,
rather than replaced with a shared-boundary scheme. Deletes retain final stage
weights instead of rerunning IRWLS. The first-stage N-scaled LD convention and
initial-weight intercept-uncertainty correction are also retained.

Only weighting feedback h2/covariance values are bounded; final estimates and
signed regressors remain unchanged. Fixed-intercept SEs are NA; zero covariance
entries describe fixed parameters. Native two-sided normal probabilities are
equivalent to the source's one-degree-of-freedom chi-square test. Additional
log probabilities retain resolution diagnostics; numeric zero does not establish
exact zero probability. The source zero-SE convention uses **+Inf Z even for
a negative effect**; this explicit convention is retained and warned about.

The API exposes correlation status and `finiteCorrelationInference()` separately
from `sourceSummaryOutOfRange()`. Callers must apply their explicit presentation
policy; finite raw inference does not establish biological/model adequacy.

The selected reference is [CBIIT LDSC3.0.2](https://github.com/CBIIT/ldsc/tree/6c673952cee74bd5c57aef1555a03b1c015399a0).
The [qualification record](../literature/ldsc-rg-development/README.md) retains
original-core references, failed fixture transport and cross-platform checks.
This unpartitioned observed-scale component does not establish liability
conversion, stratified enrichment, LD-score generation, munging, cross-ancestry
reference adequacy, historical-edition parity or whole-paper reproduction.
Hashes establish recorded integrity, not authentic execution or biology.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Brendan K. Bulik-Sullivan et al. (2015) — An atlas of genetic correlations across human diseases and traits](../CITATIONS.md#bulik-sullivan-rg-2015) — [PMID: 26414676](https://pubmed.ncbi.nlm.nih.gov/26414676/) · [PMCID: PMC4797329](https://pmc.ncbi.nlm.nih.gov/articles/PMC4797329/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
