# Native LDSC Hsq numerical examples

Build current source, then run from the repository root:

```text
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools h2 --request examples/ldsc-hsq/one-step.json
java -jar build/cli/omicsmatic-0.4.0.jar ldsc-tools h2 --request examples/ldsc-hsq/two-step.json
```

Use `gradlew.bat` on Windows. The complete synthetic files correspond to original
core reference models C000 and C004. They retain signed/subunit LD estimates,
varying per-row N, and, in C004, values at/above the strict intercept cutoff.
No real GWAS or ancestry/reference alignment is claimed.

Results go to new `build/ldsc-hsq-example-*` directories. Preserve existing
results; change `out` to a new path before rerunning. Require exit0,
`COMPLETED.json` and `analysis.json result_admitted=true`. Read every
`WARNINGS.md`, especially source-defined weighting floors/clamps and any
unbounded final h2 estimate. FAIL2 admits no result; correct the named problem
and retry new output. SKIP3 skips unavailable inputs or unsupported analyses.

The [method guide](../../docs/vignettes/ldsc-heritability.md) explains every
request field, input preparation, scope and output. These examples do not
implement genetic correlation, stratified enrichment, LD-score construction,
munging or whole-paper reproduction.
