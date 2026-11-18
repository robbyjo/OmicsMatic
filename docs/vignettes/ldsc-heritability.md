# Native LDSC heritability

`ldsc-tools h2` estimates unpartitioned, observed-scale SNP heritability from a
complete, explicitly prepared population of GWAS chi-square statistics, LD
scores, regression-weight LD scores and per-variant sample sizes. It runs in
Java; Python and an LDSC installation are not required for analysis.

The method includes a free intercept, an explicit fixed intercept, and the
two-step intercept estimator. It performs two IRWLS weight updates followed by
the final fit and a block jackknife with fixed final stage weights. Reference
validation executes unchanged numerical modules from
[CBIIT LDSC 3.0.2](https://github.com/CBIIT/ldsc/tree/6c673952cee74bd5c57aef1555a03b1c015399a0).
Historical editions and the rest of the LDSC package remain separate work.

**Require exit 0, `COMPLETED.json` and `analysis.json` with
`result_admitted=true`.** SKIP3 means dependent analysis skipped for an
unavailable/forbidden input or an unsupported requested analysis. FAIL2 admits
no result: retain the inputs and partial output, read `FAILURE.txt` and
`WARNINGS.md`, correct the named population, block, format or numeric-capacity
problem and retry a **new** output directory. Never remove a failed block,
replace nonfinite values or clamp the final estimate to force success.

## Run the complete examples

Build current source; this command is not in the previously released JAR.
Use `gradlew.bat` on Windows.

```text
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools h2 --request examples/ldsc-hsq/one-step.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools h2 --request examples/ldsc-hsq/two-step.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools --help
```

The [example directory](../../examples/ldsc-hsq/README.md) contains complete
synthetic input files and requests. These are numerical controls, not biological
estimates. Each output directory must be new. All relative paths resolve against
the request file.

## Prepare your own analysis

Input is UTF-8 TSV with the exact header:

```text
variant	chi_square	ld_score	weight_ld_score	n
rsExample1	1.4	12.7	8.2	50000
rsExample2	2.1	25.4	16.3	48500
```

Those two rows illustrate the format; they are not enough to fit this method.
Provide the complete intended regression population, with unique literal IDs,
finite values, nonnegative chi-square and positive per-variant N. Signed LD
estimates are permitted. Missing values, blank rows, duplicated IDs, malformed
decimals and incomplete rows reject the input; they are never silently removed.
An optional UTF-8 BOM is recorded and preserved in the captured original bytes.

Rows must already be aligned to the intended LD reference, QC-filtered and
genome-ordered. Choosing ancestry, MAF/regression SNP sets, sample-size units,
region exclusions and other QC is an explicit preparation responsibility. The
command does not harmonize alleles, compute LD scores, infer N, munge summary
statistics, select outliers or download a reference. `weight_ld_score` refers to
LD over the regression SNP set; it is not an arbitrary precision-weight column.

Save a JSON request with every key below:

```json
{
  "schema_version": 1,
  "input": "prepared.tsv",
  "out": "new-hsq-result",
  "reference_variants": 1000000,
  "n_blocks": 200,
  "intercept": "free",
  "two_step": 30,
  "data_provenance": "Replace with the GWAS, LD reference, genome build and complete QC population",
  "row_order_declaration": "Replace with the ordering and reference alignment used for these rows"
}
```

`reference_variants` is the supplied reference SNP count M, which can differ
from the number of regression rows. It is not inferred from the file length.
`n_blocks` is explicit: 2–10,000, subject to sufficient complete and delete-fit
populations. Real studies need an appropriate number and composition of genomic
blocks; passing the minimum numerical bound does not establish reliable inference.
The whole-input bounds are 64 MiB and two million rows. Capacity failures refuse
the analysis rather than truncate the population.

Choose the model explicitly:

| Request | Fit |
| --- | --- |
| `intercept="free", two_step=null` | One-step free-intercept IRWLS |
| `intercept=1, two_step=null` | IRWLS with the intercept fixed at the supplied number |
| `intercept="free", two_step=30` | Two-step intercept population with chi-square **strictly below** 30; the final slope uses all rows |

A fixed intercept and two-step are mutually exclusive. No default cutoff is
inferred from a paper or a historical command line. The native entry point is
`LdscHeritability.fit(chiSquare, ldScore, weightLdScore, sampleSize, M, blocks,
fixedInterceptOrNull, twoStepOrNull)`.

## Read the outputs and observed warnings

| File | Meaning |
| --- | --- |
| `source-request.json`, `source-input.tsv` | Complete original captured bytes |
| `estimates.tsv` | Observed h2/SE, per-SNP coefficient/SE, intercept/SE, mean chi-square, lambda GC and ratio/SE |
| `delete-values.tsv` | Every combined block-delete h2 and intercept |
| `covariance.tsv` | Jackknife covariance in h2/intercept coordinates |
| `block-boundaries.tsv` | Full-row and, for two-step, first-stage-row positions of every block boundary |
| `row-audit.tsv` | Original ID/order, cutoff membership and final raw precision weight for every row |
| `weight-updates.tsv` | Initial and both stage-specific h2 feedback values, bounded weighting values and intercepts |
| `analysis.json`, `WARNINGS.md` | Effective choices, complete-population counts, NA diagnostics and observed warnings |
| `manifest.json`, `COMPLETED.json` | Payload byte/SHA-256 identities and final completion pin |

The intercept SE is `NA` for a fixed intercept. Its covariance row is zero
because it is fixed, not estimated. Ratio/SE are `NA` for fixed-intercept fits
or mean chi-square at most one. Undefined diagnostics are not replaced with
zero. The selected original Hsq category-proportion diagnostic is undefined
when full/delete h2 is exactly zero; this profile refuses those cases rather
than repairing the ratio.

**Final h2 is never clamped.** Negative estimates and estimates above one remain
unchanged and produce an observed warning. Only the h2 used to update regression
weights is bounded to [0,1], matching the selected original method. LD and
weight-LD below one are floored for weights; original regression values remain
unchanged. These source-defined adjustments appear in the trace/warnings.

Block boundaries follow the original floor/linspace convention. The two-step
intercept stage uses only selected rows; internal boundaries then map back to
the complete original row axis. The first/last full-data blocks retain leading
and trailing rows that did not meet the intercept cutoff. The combined
jackknife includes the first-stage intercept uncertainty using the original
initial-weight correction. Re-running IRWLS after each deletion would change
this method and is not done.

The selected source uses the already N-scaled first design column during
first-stage weight updates. It also uses the literal `0.4549` denominator for
lambda GC. Both details are retained rather than silently replaced with other
formulas. Native weighted fitting uses centered moments and stable block
merges instead of subtracting raw normal-equation matrices; unrepresentable or
rank-deficient full/delete fits fail explicitly.

Hash checks establish recorded integrity, not authentic execution or biological
validity. The prepared design does not establish ancestry/reference matching,
adequate genomic blocks or a reproduced paper. Genetic correlation, liability
conversion, stratified/overlap enrichment, LD-score generation and munging remain
outside this Hsq command. Genetic correlation has a separate
[native RG command](ldsc-genetic-correlation.md) with an explicit common-allele
contract and correlation reportability. Unsupported `partitioned` and
`ld-score` requests produce an explicit skip.

The [qualification record](../literature/ldsc-hsq-development/README.md) describes
72 unchanged original-core reference models, field comparisons, packaged checks,
retained failures and the pending catalog source corrections.
