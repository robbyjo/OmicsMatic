# Native LOWESS and DESeq2 independent filtering

**Two Java numerical methods are now available. Full nf-core integration, fitted-model/contrast auditing and the broader 250-paper goal remain unfinished.** These methods add no separate catalog-tool credit: the catalog remains at 99 new scoped integrations and 244 missing tools. The canonical/released JAR and existing shared numerical source are unchanged.

`org.jlinalg.loess.RLowess` ports the original R 3.6.2 LOWESS algorithm. `org.jlinalg.sequence.Deseq2IndependentFiltering` implements the default base-mean/Type7/BH/LOWESS filter selection used by DESeq2 1.26.0. They run in Java without R, containers, native libraries or package installation. [Development library and evidence](source-numerics-development/README.md) include source, notices and original numerical references. Add the development JAR to a Java classpath; it has no executable `java -jar` entrypoint. The sources are also part of the project.

## Warnings and required actions

- **Filter-selection alpha and reporting FDR are separate choices.** Original nf-core/chipseq 1.2.1 calls DESeq2's default filtering alpha **0.1**, then writes result files using strict `padj < 0.01` and `padj < 0.05`. Do not substitute a reporting cutoff for the filtering alpha when reproducing that source recipe.
- **Use full-precision base means and p-values for filter reconstruction.** A retained failed control shows that R's saved text metadata dump rounded values enough to change a reconstructed curve. The passing references come directly from the fitted object and use round-trip numeric exports. Do not infer full-precision values from rounded published text or use an already filtered gene list as the complete family.
- The finite-pair LOWESS API refuses missing/nonfinite input pairs, invalid dimensions, unrepresentable source span counts, unassessable arithmetic and exceeded capacities. It does not drop points, rescale coordinates or reduce the span to make a result pass. Correct the input or choose explicit permitted capacities; preserve the failed attempt.
- Independent filtering requires complete equal arrays, finite nonnegative base means, an explicit alpha in `(0,1)`, and p-values in `[0,1]` or source NA/NaN. Source NA and NaN remain distinct. Excluded rows receive the source's NA outcome; no p-value is imputed. Inspect missing results before downstream use.
- Capacity refusal or cancellation returns no result. A completed mathematical calculation does not validate the supplied fitted model, contrast, biological interpretation, whole pipeline or unresolved author settings. These are source-specific default-method APIs, not all DESeq2 methods or R object/coercion interfaces.

The native methods require only a compatible JVM. Repeating original-software validation additionally requires the exact permitted R/DESeq2 environment. If such dependencies are unavailable and installation is forbidden, skip that dependent analysis, retain a prominent SKIP3 warning and complete permitted setup first. The native calculation itself does not download or install software.

## LOWESS

```java
import org.jlinalg.loess.RLowess;

double[] x = {3, 1, 2, 4};
double[] y = {2.5, 1.2, 2.1, 3.8};
var fit = RLowess.fit(x, y); // Source defaults: span=2/3, robustness=3,
                             // delta=0.01*(max(x)-min(x)).
double[] sortedX = fit.x();
double[] fitted = fit.fitted();
int[] originalIndices = fit.originalIndices(); // Zero-based occurrence mapping.
```

The calculation uses local linear tricube weighting, the original slope-spread rule, delta interpolation, bisquare robustness weights, tied-coordinate handling and the source's zero-weight fallback. It retains source order within equal coordinates, including equal signed zeros. `delta=0` is supported. This is LOWESS; the project's separate `Loess` API implements a different operation.

For explicit settings, call `fit(x, y, new RLowess.Options(span, iterations, delta))`. The overload accepting `Limits` and a cancellation supplier exposes whole-operation capacities. Output arrays are copied; caller inputs remain unchanged. Do not mutate inputs during a call. The finite-pair API intentionally requires explicit numeric x/y arrays.

## Default independent filtering

```java
import org.jlinalg.sequence.Deseq2IndependentFiltering;
import org.jlinalg.sequence.Deseq2SizeFactors;

double[] baseMeans = {0, 1, 10, 20};
double[] p = {Deseq2SizeFactors.MISSING, 0.2, 0.001, 0.05};
var result = Deseq2IndependentFiltering.filter(baseMeans, p, 0.1);
double[] adjusted = result.adjusted(); // Original hypothesis order, including NA.
double chosenTheta = result.selectedTheta();
double chosenCutoff = result.selectedCutoff();
```

The method computes all 50 source percentile cutoffs and BH families, counts strict rejections, runs native LOWESS with span `1/5`, applies the source residual threshold and chooses the original filter index. `selectedIndex()` is zero-based. When the source's maximum-rejection count is at most ten, it chooses the first cutoff; `selectionThreshold()` is empty because that branch does not compute one. No artificial NaN threshold is introduced.

`theta()`, `cutoffs()`, `rejections()` and `smoothed()` expose the complete diagnostics. `adjustAtCutoff(...)` exposes the source's `baseMean >= cutoff` family operation. It preserves included NA versus NaN, excludes them from the default BH family size, and retains NA for filtered-out rows. `quantile7(...)` exposes the finite-value Type7 operation without changing its input array.

For missing output, use `Deseq2SizeFactors.isMissing(value)` to identify source NA; `Double.isNaN(value)` also includes source NaN. Output arrays are copied. Explicit capacities and cancellation are available through the main `filter` overload.

## Validation and provenance

- Seventeen complete curves match original R 3.6.2 across both Java 21/25 compiler builds on Windows Java 21/25 and Linux Java 21. Cases include repeated/unsorted/signed-zero coordinates, delta interpolation, robustness/outliers, exact lines, large offsets, tiny/subnormal values and the actual DESeq2 filtering curve.
- Twelve complete filtering populations match original DESeq2 1.26.0 and genefilter across the same six compiler/JVM combinations. Every value in all 50 BH families is checked. The real control includes all **1,472 hypotheses**, selects zero-based index **21**, and reproduces the complete filtering decision. Equal-to-alpha, just-below-alpha, zero-base-mean and NA/NaN cases remain explicit.
- Thirty-two focused JUnit tests pass with frozen original references and no R installation. They exercise the selected numerical classes and unchanged NA-representation dependency. This is not a full-project test run over the unrelated held study changes.
- The packaged library is checked against the original-software-tested class bytes and replayed independently using only the small JAR and test helpers. Source generation, prior failures, complete references and license notices are retained.

The R source comes from the [official R 3.6.2 archive](https://cran.r-project.org/src/base/R-3/R-3.6.2.tar.gz); its SHA-256 is `bd65a45cddfb88f37370fbcee4ac8dd3f1aebeebe47c2f968fd9770ba2bbc954`. Java ports retain source attribution and GPL notices. DESeq2/genefilter behavior is version-bound; original external package binaries are not shipped. The unchanged existing `Deseq2SizeFactors` source supplies the shared NA representation; its inclusion does not extend all-method/version admission.

From the repository root, run `python tools/literature/verify_source_numerics_checkpoint.py` to check frozen source/artifact/reference bytes and bounded evidence. That verifier does not repeat scientific analysis or authenticate execution history. The original runtime/image and fitted-model inputs remain locally retained; numeric reference fixtures are bundled for independent Java replay.
