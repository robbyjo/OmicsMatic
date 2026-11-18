# Native LDSC RG complete numerical examples

Build current source, then run from the repository root (`gradlew.bat` on Windows):

```text
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --request examples/ldsc-rg/one-step.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --request examples/ldsc-rg/two-step.json
```

Those complete synthetic original-core cases should succeed. Results go to new
`build/ldsc-rg-example-*` directories. Preserve existing results and change `out`
before rerunning. Require exit0, `COMPLETED.json` and
`analysis.json result_admitted=true`.

The following deliberately return **FAIL2: no correlation admitted**, while
retaining component fits and distinct NA/NaN diagnostics:

```text
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --request examples/ldsc-rg/nonpositive-heritability.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools rg --request examples/ldsc-rg/nonfinite-delete-uncertainty.json
```

No `COMPLETED.json` is expected for those requests. Read `FAILURE.txt`,
`WARNINGS.md`, `model.json` and `correlation.tsv`. Retained genetic covariance
does not admit correlation. Never replace NA/NaN or drop failed blocks to force
success.

All inputs are synthetic, already-aligned common-row controls. They do not
establish real ancestry/reference matching or biological inference. The
[method guide](../../docs/vignettes/ldsc-genetic-correlation.md) explains every
field, allele-alignment contract, reporting policy and recovery action.
