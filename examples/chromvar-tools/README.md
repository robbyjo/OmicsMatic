# chromvar-tools public example

Run from the repository root with the validated local development executable:

```powershell
$chromvarJar = (Resolve-Path 'build/literature-review/signac-runtime/chromvar-public-workflow-1/omicsmatic-0.3.7-chromvar-tools-development.jar').Path
java -jar "$chromvarJar" chromvar-tools --help
java -jar "$chromvarJar" chromvar-tools --request examples/chromvar-tools/background.json
java -jar "$chromvarJar" chromvar-tools --request examples/chromvar-tools/deviations.json
java -jar "$chromvarJar" chromvar-tools --request examples/chromvar-tools/verify-background.json
java -jar "$chromvarJar" chromvar-tools --request examples/chromvar-tools/verify-deviations.json
java -jar "$chromvarJar" chromvar-tools --source-report
```

The background request computes native GC from the included FASTA and ordered one-based closed ranges, then runs the original R sampler. The deviation request scores the supplied motif annotations in Java with that generated background. These are synthetic controls with 20 peaks, six cells, seven background iterations and three annotations, including an empty annotation. They do not represent a biological result.

Inspect `build/chromvar-tools-background-demo-NEW/WARNINGS.md` and `analysis-status.json` before proceeding to deviations. Inspect the same files in `build/chromvar-tools-deviations-demo-NEW` before using scores. An empty annotation retains source `NA`; ambiguity is excluded from the chromVAR GC denominator. Preserve existing outputs and use new paths in every affected request before repeating this example.

**SKIP3 / required action:** BACKGROUND needs R4.6.1, chromVAR1.34.1, Matrix1.7.5, nabor0.5.0, Rcpp1.1.2, RcppArmadillo15.6.2-1 and jsonlite2.0.0 with their dependencies. The example's four runtime paths point at the already validated local private installations. Supply permitted equivalent paths with these exact requirements. The three library paths may name the same private library if it contains all requirements. If software is unavailable or installation is forbidden, preserve the skip and omit dependent scoring without a valid separately prepared background. Nothing is installed automatically.

**FAIL2 / required action:** preserve the failed capture, read its warnings/status/worker logs, correct the named input, reference, covariance, capacity or process error and retry into new paths. Do not impute bias or silently filter peaks. Native DEVIATIONS and VERIFY require Java; VERIFY does not run R sampling or independently reproduce RNG and cannot authenticate fabricated data after coordinated rehashing.

**Build/publication scope:** current workspace source includes this command; older canonical/released JARs do not. A normal source build uses `gradlew.bat executableJar` on Windows or `./gradlew executableJar` on Linux, producing `build/cli/omicsmatic-0.3.7.jar`. The validated development artifact above has a distinct recorded hash. These local changes have not been released or pushed.

Read the [full workflow, five paper uses and source qualifications](../../docs/literature/CHROMVAR_TOOLS.md).
