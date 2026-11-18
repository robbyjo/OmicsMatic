# Complete native stratified LDSC examples

Use a current source-build JAR and Java 17+. From the repository root:

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.StratifiedLdscCli --request examples/ldsc-stratified/fixed-overlap.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.StratifiedLdscCli --request examples/ldsc-stratified/free-overlap.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.StratifiedLdscCli --request examples/ldsc-stratified/negative-hsq.json
```

Each request includes its entire synthetic source table and overlap matrix.
They come from original-reference cases S000, S002 and S008, respectively.
No study GWAS inputs, biological interpretation or author-method reproduction
are claimed. The negative-heritability case retains its numerical estimate and
observed warning; it does not force positive heritability.

Success requires exit 0, `COMPLETED.json` and `analysis.json` with
`result_admitted: true`. Read `WARNINGS.md`. A second invocation with the same
output refuses to overwrite it; keep the previous attempt and select a new
`out` value. See the [workflow guide](../../docs/vignettes/stratified-ldsc-workflow.md)
for required fields, output interpretation and SKIP3/FAIL2 recovery actions.
