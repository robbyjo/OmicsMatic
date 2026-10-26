# Native supplied-weight scoring example

All input samples, genotypes, weights and coordinates are synthetic. The 13-sample,
8-variant complete BED set is byte-identical to the executed original-reference
fixture; `inputs/provenance.json` records hashes and generation lineage. Its
supplied frequencies come from the synthetic calls for deterministic arithmetic;
they are not independent population estimates.

In the audit checkout run this with Java:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar genotype-score `
  --bfile examples/native-genotype-score/inputs/cohort `
  --weights examples/native-genotype-score/inputs/weights.tsv `
  --missing mean-impute --genome-build unspecified --out output/synthetic-score
```

Use a new output directory. The expected unspecified-build warning explains that
the synthetic coordinates do not establish a reference assembly. Review the
output `WARNINGS.md`, sums, averages, observed/denominator alleles and orientations.
This is an arithmetic example, not a calibrated polygenic risk model.

For a standard new source build, `gradlew.bat executableJar` writes
`build/cli/omicsmatic-0.3.7.jar`; substitute that path. No PLINK, Python or R runtime
is needed by native scoring.

`DiploidScoreExample.java` contains self-contained hand cases for supplied
reference-frequency imputation and all-missing omission. It is separately
compiled with `--release 17 -Xlint:all -Werror` and executed on actual Java21/25.
An optional BED prefix argument must be this particular fixture; omit it to run
only the hand cases. Full commands, limits, units and error recovery are in
[GENOTYPE_SCORE_WORKFLOWS.md](../../docs/literature/omics-2023-2026/GENOTYPE_SCORE_WORKFLOWS.md).
