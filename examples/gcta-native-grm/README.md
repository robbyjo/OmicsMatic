# Native GCTA prepared-hard-call GRM example

This Java API computes the declared GCTA 1.94.1 autosomal diploid hard-call GRM
algorithms 0/1. It accepts **variant-by-sample** counts of the declared allele
(0, 1 or 2), with `Double.NaN` explicitly representing missing calls. Sample
identities are ordered `(FID, IID)` pairs; the same IID in different families
remains distinct. Counts are not inferred dosages or polyploid genotypes.

Build the normal executable JAR with `gradlew.bat executableJar` (Windows) or
`./gradlew executableJar` (a host with a permitted JDK). From the repository
root, compile and run the supplied synthetic example:

```powershell
javac -cp build/cli/omicsmatic-0.4.0.jar -d build/gcta-example examples/gcta-native-grm/GctaGrmExample.java
java -cp "build/cli/omicsmatic-0.4.0.jar;build/gcta-example" GctaGrmExample
```

On Linux use `:` instead of `;` in the runtime classpath. If `javac` is absent,
source compilation is SKIP3 until a permitted JDK is supplied; the exact
source-built example classes can also be transferred for an explicit runtime
check. Do not overwrite an existing research evidence directory.

The example reports three included markers, the lower triangle of the GRM and
the exact jointly called count for every sample pair. `allMissing` remains in
the marker audit with disposition `NO_CALLED_ALLELES`. Allele-monomorphic
markers have disposition `MONOMORPHIC`; a constant heterozygote column still has
nonzero HWE variance and remains included. Results defensively retain ordered
sample/marker metadata and expose cloned value/count arrays.

Algorithm 0 standardizes each marker by its HWE variance. Algorithm 1 uses raw
centered products and the source's float variance weighting. Both use a
denominator for each sample pair. Source float32 output and algorithm-1 rounding
are retained. An indefinite matrix caused by pairwise missingness is preserved.
The integer counts are exact; the source binary N format uses float32 and can
lose integer precision at large counts. This API does not write that format.

**No complete estimate:** a sample pair with zero jointly called retained
markers or a matrix with no usable polymorphic markers is refused. Invalid
dimensions, identifiers, hard calls, capacities or nonfinite results also throw
an explicit error; interruption throws `CancellationException`. A caller must
treat these as **FAIL2: NO COMPLETE RESULT**, retaining its inputs and available
diagnostics. Input/output/work capacities are logical bounds, not an OS memory
limit. No external program is invoked and there is no fallback.

Genotype-file intake, allele updates, external frequencies, sex/chromosome
coding, dominance, additional filters and GCTA's COJO/PCA/REML/LD-score/fastGWA/
mtCOJO workflows are separate unfinished work. This component does not add
GCTA/COJO catalog credit or reproduce a full paper. See the native GRM
qualification checkpoint under `docs/literature` for evidence and limitations.
