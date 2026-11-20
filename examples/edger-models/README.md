# Original edgeR differential-model example

This is synthetic count data with a declared baseline-intercept design,
treatment/third-group coefficients and a centered numeric covariate. It is
not a paper cohort. Literal UTF-8 IDs, quotes, spaces, `NA` and `001` are data.

1. Build current source with Java 21: `gradlew.bat executableJar` on Windows
   or `./gradlew executableJar` on Unix. The old release JAR lacks this command.
2. Edit `request.json`: replace `Rscript-REPLACE` with an absolute permitted
   R 4.6.1 `Rscript` path; replace `private-R-library-REPLACE` with an array
   of permitted private library roots containing original edgeR 4.10.7 and
   its dependencies. For example, `r_libraries` can contain several paths.
   Exact versions and source bodies are checked. No installation or nearby
   edition substitution happens inside the command.
3. Choose NEW `out` and replay paths. The supplied `output-NEW` and `replay-NEW`
   names must not already exist. Run from the repository root:

   ```text
   java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --request examples/edger-models/request.json
   java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --verify examples/edger-models/output-NEW
   java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRWorkflowCli --request examples/edger-models/replay.json
   ```

The request uses original design-based `filterByExpr` before TMM, recomputes
library sizes during the explicit feature subset, and selects **current
adjusted QL with its own original dispersion estimation**. Historical legacy
QL requires an explicit compatible profile/mode. Read the
[complete method and file guide](../../docs/literature/EDGER_MODELS.md).

Start with `WARNINGS.md` and `analysis-status.json`. `results/index.tsv` maps
literal result IDs to complete result tables; no implicit FDR or fold-change
threshold is applied. Raw and intermediate original models are retained in
`worker/objects.rds`, using declared positional transport codes internally.
VERIFY works without R and checks intake, file populations, selected arithmetic
and exported structure. It does not refit original statistical models or
parse binary RDS model contents. REPLAY executes the declared original model
again and requires its exact permitted runtime.

**SKIP3:** the dependent analysis was skipped. Supply the named exact permitted
runtime/package/input, or retain the skip if unavailable or forbidden. Leaving
the example runtime placeholders unchanged intentionally cannot run R.
**FAIL2: NO COMPLETE RESULT.** Preserve originals, partials, source model
objects and warnings; correct the named cause and use NEW output. A finite
stock test table can still come from an exhausted null fit. The adapter checks
exposed intermediate fit failure flags and the original GLM iteration limit.

These prepared-matrix controls do not establish biological design adequacy,
historical author-host reproduction or complete paper-workflow coverage.
