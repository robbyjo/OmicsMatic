# Original-runtime DESeq2 examples

These are synthetic count datasets, not results from a paper. `paired.json` fits six paired donors, tests the treatment contrast and a log-fold-change threshold, and produces full VST/rlog matrices. `shrinkage.json` demonstrates normal, apeglm and ashr shrinkage. `lrt.json` tests an overall three-condition effect while showing two different fold-change contrasts.

From the repository root in PowerShell, build the development executable and install a **new private runtime** where downloads/installations are permitted:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/deseq2-inference-development.init.gradle executableJar
New-Item -ItemType Directory -Force build/deseq2-user-runtime | Out-Null
& 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --vanilla tools/literature/install_deseq2_runtime.R build/deseq2-user-runtime/R-lib build/deseq2-user-runtime/report allow
```

Set `JAVA_HOME` and the Rscript path to the actual installations on your machine. The tested profile requires R 4.6.1 and DESeq2 1.52.0. The installer uses fixed Bioconductor 3.23 repositories plus CRAN and installs dependencies into the new library. If the offline Java build lacks cached dependencies, remove `--offline` only where downloads are allowed.

**Read `build/deseq2-user-runtime/report/WARNINGS.md` and `STATUS.tsv` before analysis.** If setup succeeds, reuse that runtime; do not repeat setup into its existing directories. If setup fails, preserve its logs, correct the named requirement, choose new library/report paths and update `r_libraries` in the requests. When installation is forbidden, use `forbid` instead of `allow`; dependent analysis must be skipped. Do not substitute a different DESeq2 edition silently.

The example requests bind `../../build/deseq2-user-runtime/R-lib` relative to this directory. Update their `rscript`/`r_libraries` fields if your runtime is elsewhere. Run:

```powershell
$deseqJar = 'build/literature-review/deseq2-runtime/inference-development/omicsmatic-0.3.7-deseq2-inference-development.jar'
java -Xmx512m -cp $deseqJar org.jlinalg.cli.Deseq2InferenceCli --request examples/deseq2-inference/paired.json
java -Xmx512m -cp $deseqJar org.jlinalg.cli.Deseq2InferenceCli --request examples/deseq2-inference/shrinkage.json
java -Xmx512m -cp $deseqJar org.jlinalg.cli.Deseq2InferenceCli --request examples/deseq2-inference/lrt.json
java -Xmx512m -cp $deseqJar org.jlinalg.cli.Deseq2InferenceCli --request examples/deseq2-inference/verify-paired.json
```

The output folders are `build/deseq2-example-paired`, `build/deseq2-example-shrinkage` and `build/deseq2-example-lrt`. Read each `WARNINGS.md` and `analysis-status.json`, then the full `results/`, `transforms/`, normalization and gene-estimate tables. All supplied features contribute to size-factor estimation before the explicit `keep.tsv` selection. That selection retains 200 of 240 rows in its listed order. The paired examples deliberately contain low-count/all-zero genes and an outlier, so source `NA` values are expected; they are not imputed.

**Recovery:** `SKIP3` means a required runtime/package is unavailable and no result is admitted. Install/provide the named requirement only where permitted; otherwise skip that request. `FAIL2` means no result is admitted, including partial tables in the retained attempt. Correct its named input/model/contrast/process error and change `out` to a new path. Existing outputs are refused and preserved. The `mean` dispersion fit and disabled automatic outlier replacement are explicit settings for these synthetic examples; they are not inferred paper settings.

`VERIFY` needs Java only and works on a copied completed capture. It checks integrity, source/parameter correspondence, identities, normalization and table consistency. It does not rerun negative-binomial inference, shrinkage or transformations, and it does not authenticate a coordinated fabricated capture.

See the [development guide](../../docs/literature/DESEQ2_INFERENCE_DEVELOPMENT.md) for the typed design contract, scientific limits, error recovery and validation scope. The standalone development command is not registered in the main dispatcher or included in a new public release.
