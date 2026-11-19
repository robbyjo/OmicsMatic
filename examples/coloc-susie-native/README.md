# Native stable SuSiE colocalization

These synthetic files contain **credible-signal log Bayes factors**, not beta,
variance or P values. Each effect must cover the same exact variant population
within its file. ` a ` and `a` are deliberately different IDs. Their whitespace
and the Unicode ID are preserved.

Use a JAR built from current source; the published 0.4.0 release predates these
fixes. From the repository root:

```powershell
java -jar build/libs/omicsmatic-0.4.0.jar coloc --trait1 examples/coloc-susie-native/trait1.effects.tsv --trait2 examples/coloc-susie-native/trait2.effects.tsv --minimum-overlap 1 --out build/coloc-susie-example-1.tsv
```

All variants are shared, so an overlap threshold of exactly one retains the
pair. Choose new output names for each attempt. Inspect the regional table,
`.variants.tsv`, `.log` and standard error. The log records the native method
and all returned warnings. The small example does not establish colocalization
or reproduce a paper.

For optional prior weights, the Java API example displays every warning and
checks that the conditional posterior is defined:

```powershell
javac --release 17 -cp build/libs/omicsmatic-0.4.0.jar -d build/coloc-susie-example examples/coloc-susie-native/NativeSusieVariantExample.java
java -cp "build/coloc-susie-example;build/libs/omicsmatic-0.4.0.jar" NativeSusieVariantExample
```

On Linux/macOS use a colon as the classpath separator. The native stable
method's H3 arithmetic and weighted conditional SNP probabilities differ from
selected coloc 6.0.3 behavior; read the [guide](../../docs/vignettes/coloc-susie-native.md)
before interpreting results. R's reserved `null` hypothesis column is refused;
prepare variant-only BFs relative to a zero-null baseline through an explicit
documented conversion and preserve the originals. No automatic removal or
baseline correction is performed.

An undefined conditional SNP posterior produces exit 2 and no complete CLI
result table. Preserve input files/logs, inspect the shared BF support and
retry only after correcting the cause. No retained pairs means no hypothesis
posterior is reported; it is not evidence for H0. Ordinary summary statistics
belong to the separate [`coloc-abf` workflow](../coloc-abf-workflow/README.md).
