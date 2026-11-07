# Native accessibility tracks and gene models

These synthetic examples use `org.jlinalg.cli.SignacCoverageCli` to create browser-style accessibility tracks with gene or transcript models. **Analysis needs Java only; it does not need R, Signac, MACS or an installation step.** The native arithmetic and annotation geometry follow retained Signac source profiles. SVG and PNG use this project's rendering style. See the [development guide](../../docs/literature/SIGNAC_COVERAGE_DEVELOPMENT.md) for source behavior, limits and verification scope.

From the repository root in PowerShell, using your installed full JDK:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/signac-coverage-development.init.gradle executableJar
$coverageJar = 'build/literature-review/signac-runtime/coverage-tools-development/omicsmatic-0.3.7-signac-coverage-development.jar'
& "$env:JAVA_HOME/bin/java.exe" -Djava.awt.headless=true -Xmx768m -cp $coverageJar org.jlinalg.cli.SignacCoverageCli --request examples/signac-coverage/gene-request.json
& "$env:JAVA_HOME/bin/java.exe" -Djava.awt.headless=true -Xmx768m -cp $coverageJar org.jlinalg.cli.SignacCoverageCli --request examples/signac-coverage/transcript-request.json
& "$env:JAVA_HOME/bin/java.exe" -Djava.awt.headless=true -Xmx768m -cp $coverageJar org.jlinalg.cli.SignacCoverageCli --request examples/signac-coverage/peak-assay-request.json
& "$env:JAVA_HOME/bin/java.exe" -Djava.awt.headless=true -Xmx768m -cp $coverageJar org.jlinalg.cli.SignacCoverageCli --request examples/signac-coverage/verify-request.json
```

Remove `--offline` only when uncached Java build dependencies may be downloaded. On Linux use the same classpath and requests with your `java` executable; no WSL-specific runtime is required. Keep request filenames ASCII on older Windows launchers. JSON and captured data are UTF-8.

Open `coverage.svg` or `coverage.png` in each `build/signac-coverage-*-demo-NEW` output. The gene example has two accessibility panels and one gene model. The transcript example displays both synthetic isoforms separately. The peak-assay example uses the [peak-calling example](../signac-peaks/README.md)'s supplied sorted fragments and feature-assay column sums. Its declared nCount values are **1,202, 600, 1,200 and 600** in `cell1`–`cell4` order. They are assay totals, not read-support sums. Source hashes and derivations are retained in `INPUT-PROVENANCE.json`; all loci and models are synthetic.

Every analysis directory must be **new**. Before repeating, choose different `out` paths and update dependent `analysis` paths together. Preserve previous attempts.

**Read `WARNINGS.md` first.** Then inspect `analysis-status.json`, `diagnostics.json`, complete `group-cuts.tsv`, `group-scaling.tsv`, sampled `coverage.tsv` and annotation tables. Supplied assay counts include the complete declared object population, including a cell with no local fragments. Signac's extra zero NA scaling slot and historical cut-position offset remain explicit. The source smoothing operation is a rolling **sum**. `q95` multiplies a rounded sampled maximum by 0.95; it is not an empirical quantile.

For a deliberate failure example:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Djava.awt.headless=true -Xmx768m -cp $coverageJar org.jlinalg.cli.SignacCoverageCli --request examples/signac-coverage/empty-request.json
```

This requests zero sampled positions and must return **FAIL2 / exit 2**, with `results_admitted: false`. Retained counts are evidence, not an admitted figure or a negative accessibility result. Correct the declared sampling/population/limit cause and retry into a new output path. **SKIP3 / exit 3** means a required full Java desktop/font/PNG runtime is missing or forbidden; skip dependent plotting. Where permitted, provide a suitable full JDK and retry into new paths; otherwise leave the analysis skipped. Analysis never installs software.

VERIFY recalculates numbers, model geometry and exact SVG bytes from captured inputs. It checks PNG format/dimensions and retained hashes, but does not independently redraw raster fonts or authenticate execution/biology. Require exit 0, `results_admitted: true`, a completion marker and successful VERIFY before consuming results. This standalone interface does not yet grant whole-Signac or catalog implementation credit.
