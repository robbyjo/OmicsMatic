# Complete synthetic LD-score reference examples

Each directory supplies all three BED/BIM/FAM files, the complete literal
reference TSV and all 20 request fields. Inputs are synthetic original-source
controls, not a biological reference panel.

Build the current source, then run from the repository root:

```powershell
.\gradlew.bat :executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdscReferenceCli --request examples/ldsc-reference/unpartitioned-snps/request.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdscReferenceCli --request examples/ldsc-reference/binary-annotations/request.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.LdscReferenceCli --request examples/ldsc-reference/continuous-annotations/request.json
```

Java 17+ and the complete source-build JAR are required. The earlier published
0.4.0 release does not contain this command. Each request creates its own new
`result` directory. For retries, copy the complete input folder to a new work
directory or change `out` to a new path; preserve old results and failures.

Read the [guide](../../docs/vignettes/ldsc-reference-preparation.md) and
[warnings](../../docs/literature/ldsc-reference-workflow/WARNINGS.md).
Require exit 0, a valid manifest/completion pair and `result_admitted: true`.
SKIP3 is skipped dependent work; provide the named permitted prerequisite.
FAIL2 admits no complete result; retain partials and follow the stated action.
The explicit whole-reference permission in these small synthetic examples
covers the original source's chunk expansion/fallback after review. It is not
a recommendation for an unreviewed real reference population.
