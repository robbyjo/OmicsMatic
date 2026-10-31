# Native haplotype dosage moments

`org.jlinalg.genetics.HaplotypeDosage` calculates posterior probabilities,
expected ALT copies, variance and per-copy calls from supplied haplotype ALT
probabilities. It accepts explicit haploid or diploid sample rows, including
mixed ploidies in a cohort. This Java API does not phase or impute genotypes.

Use a development JAR containing this API and a new classes directory:

```sh
mkdir NEW_CLASSES_DIRECTORY
javac --release 17 -cp JAR -d NEW_CLASSES_DIRECTORY examples/native-haplotype-dosage/HaplotypeDosageExample.java
java -cp NEW_CLASSES_DIRECTORY:JAR HaplotypeDosageExample
```

On Windows, separate classpath entries with `;` instead of `:`. Expected output:

```text
GP=[0.1875, 0.625, 0.1875]
DS=1.0; variance=0.375
copy calls=[0, 1]
AF=0.5
haplotype variance R2=0.5
monomorphic R2 defined=false
```

**Interpretation and required action:** supply finite probabilities in `[0,1]`
for one or two already identified haplotypes. Missing values and unsupported
ploidy fail explicitly; no normalization, missing-value filling, allele flip or
phase reconstruction is inferred. The diploid probability formula assumes
independent copies. Each copy is called ALT at probability `>= 0.5`; those calls
can differ from the most probable unphased genotype.

The cohort R² is the population variance of haploid dosages divided by
`AF*(1-AF)`, weighted by actual copy count. It is not variance of diploid sample
dosages or empirical correlation with true genotypes. Mathematical R² is absent
at AF 0 or 1; `minimacR2Convention()` separately provides the upstream zero
convention. Stable double-precision results can differ from original float32
summaries. Keep upstream and recalculated values separate when reviewing QC
thresholds. This helper does not establish a working Minimac project command or
reproduction of any paper.
