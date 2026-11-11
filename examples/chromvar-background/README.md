# chromVAR background sampling and native deviations

Run from the repository root with Java 17 or newer. The exact R runtime and private libraries below are already available in the validated local environment. A fresh checkout needs those requirements supplied separately; this command does not install software.

```powershell
$backgroundJar = (Resolve-Path 'build/literature-review/signac-runtime/background-workflow-build-2/omicsmatic-0.3.7-chromvar-background-development.jar').Path
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarBackgroundRequestCli --request examples/chromvar-background/request.json
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarRequestCli --request examples/chromvar-background/deviations-request.json
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarBackgroundRequestCli --request examples/chromvar-background/verify-request.json
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarRequestCli --request examples/chromvar-background/deviations-verify-request.json
```

Inspect `build/chromvar-background-demo-NEW/WARNINGS.md` and `analysis-status.json` before using its `background.tsv`. Inspect the corresponding files in `build/chromvar-background-deviations-demo-NEW` before using scores. Every output path must be new. Preserve an existing run and change all affected analysis/background/verification paths before repeating it.

The first request computes native GC bias from the supplied FASTA and ordered one-based closed peak ranges, then runs the unchanged original background sampler. The second request uses that generated background matrix in native deviation scoring. These are synthetic numerical controls: 20 peaks, six cells, seven background iterations and three annotations, including an empty annotation. Scores are not biological findings.

To exercise the retained legacy source profile with an explicit prepared bias table instead, run:

```powershell
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarBackgroundRequestCli --request examples/chromvar-background/legacy-request.json
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarRequestCli --request examples/chromvar-background/legacy-deviations-request.json
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarBackgroundRequestCli --request examples/chromvar-background/legacy-verify-request.json
java -cp "$backgroundJar" org.jlinalg.cli.ChromVarRequestCli --request examples/chromvar-background/legacy-deviations-verify-request.json
```

This runs unchanged chromVAR 1.16.0 source functions with the recorded current dependencies; it does not execute a historical whole package. Both example bias populations are the same canonical-base GC fractions. Each peak contains five Ns that are excluded from the chromVAR GC denominator.

**SKIP3 / required action:** BACKGROUND requires R 4.6.1 and chromVAR 1.34.1, Matrix 1.7.5, nabor 0.5.0, Rcpp 1.1.2, RcppArmadillo 15.6.2-1 and jsonlite 2.0.0. Update the four runtime path fields in the request to permitted private installations of these exact versions. If they are unavailable or installation is forbidden, leave the dependent analysis skipped. Do not run the deviation follow-up without a valid background result or substitute the toy matrix in a biological study.

**FAIL2 / required action:** preserve the attempt, inspect warnings, worker logs, reference/bias and count populations, correct the named error and choose new output paths. Constant or undefined bias, nonpositive pooled peak counts and insufficient covariance rank can fail in the original source. No row removal or imputation occurs.

**VERIFY scope:** verification requires Java and audits saved capture consistency. It recomputes native count/reference/GC preparation but does not rerun R sampling or independently reproduce RNG. Coordinated fabrication of worker and published data followed by rehashing cannot be authenticated by this capture. Read the [workflow guide and limits](../../docs/literature/CHROMVAR_BACKGROUND_DEVELOPMENT.md).
