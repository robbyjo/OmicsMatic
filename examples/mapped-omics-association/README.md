# Association with mapped omics adjustment

Build from the repository root with `gradlew.bat executableJar` (Windows) or
`./gradlew executableJar`. This example uses synthetic test fixtures, including
different sample orders, missing measurements, and a sample absent from genes.
It fits two isoforms adjusted for age, each isoform's gene and protein p1.

Run from the repository root (PowerShell):

```powershell
java -jar build/cli/omicsmatic-0.3.7.jar `
  --pheno src/test/resources/mapped-omics/phenotype.tsv --id id `
  --omics src/test/resources/mapped-omics/isoforms.tsv --omics-type expression `
  --formula "BMI ~ age + <omics>" `
  --adjust-omics "genes=src/test/resources/mapped-omics/genes.tsv" `
  --adjust-omics "proteins=src/test/resources/mapped-omics/proteins.tsv" `
  --adjust-map src/test/resources/mapped-omics/mapping.tsv `
  --backend cpu --threads 2 --out output/mapped-omics-example.tsv
```

The mapping table is:

```text
feature_id	source	adjustment_id
tx1	genes	g1
tx1	proteins	p1
tx2	genes	g2
tx2	proteins	p1
```

Expected OLS results from R 4.6.1 `lm`:

| Feature | Complete observations | Isoform beta | SE | P |
| --- | ---: | ---: | ---: | ---: |
| tx1 | 67 | 0.5261686621 | 0.1099444238 | 1.094339384e-5 |
| tx2 | 67 | -0.1151543188 | 0.2227978954 | 0.6070959268 |

To put isoform expression on the left, add `--omics-response` and choose a new
output path. The tested coefficient is then BMI. For a binary phenotype,
replace the formula with `case ~ age + <omics>`, add `--family binomial`, and use
a new output path. Full binomial GLM and reverse-direction OLS references are
also in `src/test/resources/mapped-omics/expected-r.tsv`.

Regenerate the independent references with:

```text
Rscript tools/validation/mapped_omics_reference.R
```

For your own data, replace the matrices, phenotype table and mapping. No
gene/isoform naming convention is imposed. Repeat named sources and mapping
rows to include other omics types or measurement levels. Model-specific
complete-case masks, mapping IDs, failed fits, and input hashes are retained
in the output and manifest. The [full tutorial](../../docs/vignettes/cli-association-tutorial.md)
documents transformations, mixed models and restrictions.
