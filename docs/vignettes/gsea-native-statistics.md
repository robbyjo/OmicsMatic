# Native Broad GSEA arithmetic

**This is a Java library component, not a complete Broad GSEA file workflow.**
It does not rank expression data, generate permutations, map identifiers,
collapse probes or retrieve gene-set collections. The project RUN/VERIFY/REPLAY
adapter, global command, provider-aware paper reviews and catalog admission
remain unfinished. Reuse this component when implementing that workflow.

`org.jlinalg.enrichment.BroadGseaStatistics` implements the original
max-deviation score walk and `meandiv` normalization, sign-separated nominal P,
empirical FDR and FWER from supplied original null ES vectors. It keeps the
original float-backed scores/weights, double walk accumulation, grouped misses,
member summation order and float storage. It does not replace the existing
fgsea-specific `GseaStatistics` or the BH/BY-based ranked-enrichment workflow.

```java
import org.jlinalg.enrichment.BroadGseaStatistics;

float[] ranking = {3f, 1f, -1f, -2f}; // explicitly supplied rank order
var positive = BroadGseaStatistics.score(ranking, new int[]{0, 1},
    BroadGseaStatistics.Weight.WEIGHTED);
var negative = BroadGseaStatistics.score(ranking, new int[]{2, 3},
    BroadGseaStatistics.Weight.WEIGHTED);

// These small illustrative vectors only demonstrate the API.
// For an analysis, supply actual complete null vectors from the declared model.
float[][] originalNullEs = {
    {0.5f, -0.4f, 0.75f, -0.2f},
    {0.6f, -0.55f, 0.2f, -0.7f}
};
var family = BroadGseaStatistics.meandiv(
    new float[]{positive.es(), negative.es()}, originalNullEs, 8L);
float[] nes = family.nes();
float[] nominalP = family.nominalP();
float[] empiricalFdr = family.fdr();
float[] fwer = family.fwer();
```

Indices are zero based. Pass members in their actual original summation order;
the score routine does not sort them. Input scores must be finite floats,
members unique/in range, and the set a nonempty proper subset. All four original
scoring modes are exposed: CLASSIC, WEIGHTED, WEIGHTED_P2 and WEIGHTED_P1_5.
The 1.5 source numerator uses `pow(score, 1.5)`, while its denominator uses the
absolute score. Negative-member hits therefore enter an original substitution
branch. This API refuses that branch explicitly; it does not change the source
formula to a generic absolute-power definition.

`meandiv` requires the complete tested family. Rows are sets and columns are
permutations. Keep column identity, including for independently generated
gene-set nulls. `maximumCells` is explicit; exceeding it refuses the request
without reducing the family or permutation count. An absent unused sign tail
is allowed; a missing required tail or nonfinite required statistic is refused.
Zero observed ES is also refused: the original FDR map inserts it in both sign
passes and fails. The returned arrays are defensive copies.

Nominal P uses a strict comparison within the observed sign's null tail. FWER
uses strict comparison against each column's family extreme. There is no
automatic pseudocount or doubling. Empirical FDR averages the source's
within-column sign-tail proportions, with its inclusive null thresholds,
stable observed-rank denominator and source cap at one. Flattening every null
column into a single denominator, or substituting BH, changes the method.

**FAIL2 / NO COMPLETE ANALYSIS RESULT:** callers must retain inputs and original
objects/conditions, report `IllegalArgumentException` or `ArithmeticException`
with the affected analysis, and correct the named issue before a NEW attempt.
The component refuses source fallback/rescaling branches, zero/nonfinite
denominators, unsupported zero observed ES, nonfinite values, malformed shapes
and exceeded capacity. It never manufactures a finite replacement. A library
exception is not a prerequisite skip.

**SKIP3 / DEPENDENT ANALYSIS SKIPPED:** an upstream workflow that needs an exact
permitted input/runtime/source must name the missing prerequisite and recovery
action, or retain the skip if installation is forbidden. The native methods
themselves require only Java and do not install or launch other software.

[Qualification and saved proof](../literature/gsea-native-development/README.md)
cover three source editions, actual original expression/phenotype, expression/
gene-set and preranked reference compositions, native boundary refusals, the
normal packaged JAR and Windows/Linux JVMs. The checks are synthetic method
comparisons, not author-data, historical-host, whole-tool or biological
validation. [Current checkpoint](../literature/GSEA_NATIVE_CHECKPOINT_2026-11-20.md).

Source: [official GSEA Desktop repository](https://github.com/GSEA-MSigDB/gsea-desktop).
The original BSD-style notice is retained verbatim in
`META-INF/licenses/GSEA-DESKTOP-LICENSE.txt`, including in the normal JAR.
