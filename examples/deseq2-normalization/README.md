# Native DESeq2 normalization example

This development command estimates sample size factors and divides raw counts by them. It does not run differential expression or transform counts with VST/rlog. The main CLI dispatcher does not yet register it.

From the repository root in PowerShell, using Java 21 or newer:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/deseq2-normalization-development.init.gradle executableJar
$deseqJar = 'build/literature-review/deseq2-runtime/normalization-development/omicsmatic-0.3.7-deseq2-normalization-development.jar'
java -cp $deseqJar org.jlinalg.cli.Deseq2NormalizationCli --request examples/deseq2-normalization/normalize.json
java -cp $deseqJar org.jlinalg.cli.Deseq2NormalizationCli --request examples/deseq2-normalization/frozen-means.json
```

Set `JAVA_HOME` to your actual Java 21 installation. The offline build uses already cached project dependencies; if they are missing, rerun without `--offline` where downloads are permitted. If required dependencies cannot be installed, skip the command and report the missing dependency. This build uses a separate development folder and preserves the canonical executable.

The first request estimates geometric means from all supplied samples. The second uses explicit geometric means and an ordered control-gene list containing a repeated entry. The repeated control contributes repeatedly, as in the original low-level function. Using supplied means also centers the resulting factors to a geometric mean of one; it does not recompute those means with the requested `poscounts` setting.

Outputs appear under the two `build/` paths named in the requests. Read `WARNINGS.md` and `analysis-status.json` first, then `size-factors.tsv`, `normalized-counts.tsv`, captured `inputs/`, `source-request.json` and `manifest.yaml`. `usable_for_differential_fit` checks whether factors are positive and finite; it does not establish that a biological design is appropriate.

**Recovery:** existing output folders are refused. Preserve each attempt and change `out` to a new path before retrying. `FAIL2` admits no result, including any partial table retained in that folder. If factors are `NA`, `NaN`, zero or infinite, review zeros, controls and supplied means before any later differential fit. No R installation is needed for these requests.

See [the development guide](../../docs/literature/DESEQ2_NORMALIZATION_DEVELOPMENT.md) for supported inputs, capacities, validation and pending inference work.
