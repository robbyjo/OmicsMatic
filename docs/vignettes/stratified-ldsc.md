# Native stratified LDSC API

`org.jlinalg.xwas.StratifiedLdsc` fits a prepared joint, observed-scale
partitioned heritability model and summarizes explicitly supplied overlapping
binary annotations. It extends the native Java LDSC library for repeated
chromatin and eQTL annotation analyses in the
[42 reviewed paper roles](../literature/LDSC_CATALOG_INTEGRATION.md).
Access this component through its Java API; the existing catalog qualification
continues to describe the unpartitioned `ldsc-tools h2/rg` commands.

Build the current source with Java 17 or newer. The published 0.4.0 binary
predates this component. The executable source-build JAR includes its Java
dependencies, including the Student t distribution used for enrichment tests.
No Python, R or external LDSC installation is required for analysis.

## Prepare the complete model explicitly

Supply chi-square values, a row-by-annotation LD-score matrix, the weight-LD
column, positive per-SNP sample sizes, positive reference annotation counts,
the number of consecutive jackknife blocks and the intercept setting. Pass
`null` to estimate the intercept or a finite number to fix it. Rows must already
be QC-filtered, aligned to their reference and placed in the intended genome
order. The method retains signed LD-score estimates and every supplied row and
annotation. It does not infer munging, outlier cutoffs, coordinate conversions,
alleles, ancestry matching, annotation construction or LD-score generation.

```java
import org.jlinalg.xwas.StratifiedLdsc;

// Small synthetic API example; these four rows are not a study analysis.
double[] chi = {1.2, 1.3, 1.4, 1.5};
double[][] ld = {{1, 1}, {2, 1}, {1, 2}, {2, 2}};
double[] weightLd = {2, 3, 3, 4};
double[] n = {1e6, 1e6, 1e6, 1e6};
double[] annotationCounts = {1e6, 5e5};
var fit = StratifiedLdsc.fit(chi, ld, weightLd, n,
    annotationCounts, 2, 1.0);
for (String warning : fit.warnings()) System.err.println(warning);
System.out.println("Observed h2: " + fit.totalH2());
System.out.println("Jackknife SE: " + fit.totalStandardError());
```

The result exposes annotation coefficients followed by the intercept in
`estimates()`, complete block deletes, covariance matrices, exact row weights,
block boundaries and both the full-fit proportions and their bias-corrected
ratio-jackknife estimates. Returned arrays are defensive copies. A fixed
intercept has a `null` SE and ratio diagnostics; its zero covariance entries
represent a fixed parameter. An undefined free-intercept ratio is also `null`
when mean chi-square is at most one.

## Supply overlap information separately

For overlapping binary annotations, supply the symmetric matrix of reference
annotation-intersection counts and the **unique reference-universe count**:

```java
double[][] intersections = {{1e6, 0}, {0, 5e5}};
var overlap = StratifiedLdsc.overlap(fit, intersections, 1.5e6);
```

The diagonal must equal `annotationCounts`, and intersection counts must obey
the supplied universe bounds. The matrix, LD scores and counts must describe
the same annotation columns and reference population; the caller must establish
that provenance. Continuous-annotation overlap summaries require a separately
specified model; this overlap entry point accepts binary counts.

Use `h2Proportions()`, `enrichments()` and their SEs from this overlap result.
The unadjusted `M_j * coefficient_j` values in the fit are coefficient
allocations. Shared variants make `sum(M_j)` different from the unique universe
count, so those allocations must not be presented as overlapping-category
heritability.

Enrichment tests use the original category-versus-complement coefficient
contrast, with a two-sided Student t tail and **df equal to the number of
blocks**. A zero contrast SE produces a `null` enrichment P value and `NaN` log
P sentinel, including an all-variants baseline category. Keep that diagnostic
missing. Coefficient Z scores and enrichment P values are distinct tests;
study-specific one-sided tests and outer multiple-testing corrections remain
explicit caller choices.

## Numerical behavior and required actions

**Retain warnings and failed attempts.** Parameter and numerical exceptions
refuse the requested result; do not catch them and publish partial estimates as
a completed analysis. Before fitting, retain exact inputs, column identities,
reference/QC provenance and model choices in the surrounding workflow.

- If a needed input, dependency or permitted resource is unavailable, skip the
  dependent analysis and identify what the user must supply. The Java API itself
  performs no download or installation.
- For invalid input, an unsupported model, rank deficiency, numerical failure
  or capacity refusal, admit no complete result. Correct the named issue and
  rerun as a new attempt with the original attempt retained.
- Review the complete design/reference population and block composition when
  any full or delete fit is deficient. The solver never drops a row, annotation
  or failed block and never substitutes a pseudoinverse.
- Negative coefficients, proportions outside zero to one and final heritability
  outside zero to one are retained. They require model/reference review, rather
  than clamping. Only the initial weighting heritability is clamped as upstream.
- Keep undefined diagnostics missing. Do not substitute zero, force positive
  heritability or remove failed observations to obtain an admitted result.

The selected numerical target is the
[CBIIT 3.0.2 core](https://github.com/CBIIT/ldsc/tree/6c673952cee74bd5c57aef1555a03b1c015399a0).
Its partitioned CLI selects **`old_weights=True`**: weights are calculated once
from the aggregate initial estimate and retained for full and delete fits.
The unpartitioned IRWLS/two-step method is a separate estimator. The native
method does not silently apply the wrapper's raw-data filtering defaults.

The joint solve standardizes columns before Cholesky factorization and refuses
a standardized pivot at or below `1e-12`. This explicit numerical refusal bound
does not reproduce every historical solver's decision on ill-conditioned input.
Whole-population limits are 2 million rows, 256 annotations, 10,000 blocks,
8 million design cells, 8 million packed block cells and a conservative
500-million operation budget. Inputs exceeding a limit are refused intact.

The [qualification and warnings](../literature/ldsc-stratified-development/README.md)
cover 48 original-core models and 22,596 reference fields. The same nine focused
tests run against the packaged library on Windows and Linux. Qualification
addresses this numerical API, with source-derived boundaries retained for
liability-scale, multivariate, spatial and wrapper-specific workflows. This
extension adds no additional family count or whole-paper reproduction claim.
