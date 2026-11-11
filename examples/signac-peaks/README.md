# Original MACS peak calling with native Java preparation

These five synthetic examples call original MACS2 through `org.jlinalg.cli.SignacPeaksCli`. They demonstrate pooled BED, grouped BED with combined peaks, BEDPE, broad peaks and a valid empty result. A sixth analysis quantifies the combined intervals using the existing native FeatureMatrix workflow. The [development guide](../../docs/literature/SIGNAC_PEAKS_DEVELOPMENT.md) documents every setting and verification limit.

**Original peak calling needs Linux Java and an exact permitted private MACS/Python runtime. Windows users can run it inside WSL. Verification and native feature counting also run on Windows Java. A fresh clone does not contain the private runtime.** These inputs describe a synthetic coordinate system; do not interpret their peaks as biological results.

## Build the separate development JAR

From the repository root in PowerShell, using your installed JDK:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/signac-peaks-development.init.gradle executableJar
```

Remove `--offline` only when uncached Java dependencies may be downloaded. This produces `build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar`.

## Provide the runtime, then call peaks

Each example declares MACS2 **2.2.7.1**, Python **3.9.20** and NumPy **1.24.4**, using the original Signac 1.9.0 command profile. The checked requests point to this workspace's validated private home. For a different permitted home, change `macs2_home` in all five requests to its Linux path. Keep the complete virtual environment and its Python base available. The guide gives a bounded developer setup procedure and explains why a generic newest-version installation does not match this profile.

Inside a Linux shell, from the repository root, run:

```bash
java -Xmx768m -cp build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/pooled-request.json
java -Xmx768m -cp build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/grouped-request.json
java -Xmx768m -cp build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/bedpe-request.json
java -Xmx768m -cp build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/broad-request.json
java -Xmx768m -cp build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/empty-request.json
java -Xmx768m -cp build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/verify-request.json
```

For this Windows workspace, the same Linux invocation can be launched with `wsl.exe -d JLinAlg-Omics-SAIGE-20261026 --cd /mnt/d/projects/JLinAlg -- /usr/bin/java ...`, followed by the arguments above. Use your own distribution and repository path on another machine. Java must run **inside** Linux to call MACS.

Expected peak rows are pooled **3**, grouped **4** reduced to **3** intervals, BEDPE **3**, broad **3**, and empty **0**. BED and BEDPE peak coordinates differ. The empty request has successful original calls and a header-only result; that is distinct from a failed call. Every analysis directory must be **new**. Change `out` and dependent `analysis`/`regions` paths together before repeating examples; retain prior attempts.

## Count fragments in the called regions

After the grouped example succeeds, run the native analysis and verify its capture:

```powershell
$peakJar = 'build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar'
& "$env:JAVA_HOME/bin/java.exe" -Xmx768m -cp $peakJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-peaks/feature-request.json
& "$env:JAVA_HOME/bin/java.exe" -Xmx768m -cp $peakJar org.jlinalg.cli.SignacFragmentsRequestCli --verify build/signac-peaks-feature-demo-NEW
& "$env:JAVA_HOME/bin/java.exe" -Xmx768m -cp $peakJar org.jlinalg.cli.SignacPeaksCli --request examples/signac-peaks/verify-request.json
```

The matrix has **3 features, 4 cells and 3,602 total counts**: 3,602 of the 6,600 supplied fragment lines overlap these called regions. Read support does not multiply a row. `regions.tsv` is already **one-based, closed**, as required by the native FeatureMatrix interface: do not add one again. Fragment input remains zero-based, half-open BED. The example's stable sort and original source hashes are explicit in `INPUT-PROVENANCE.json`. Real FeatureMatrix inputs also need sorted records, consistent assemblies/chromosome names and a declared cell population.

## Warnings and recovery

**Read `WARNINGS.md` first, then `analysis-status.json`, `diagnostics.json`, `prepared-summary.json` and every retained `process/*/process.stderr`.** The CLI prints the warning path and required actions. Exit **3 / SKIP3** skips dependent peak analysis when an exact runtime is missing or execution is forbidden. Where permitted, supply the named runtime and retry into new paths; otherwise leave the analysis skipped. Exit **2 / FAIL2** admits no result after invalid input, source failure, integrity failure or exhausted limits. Correct the named cause and retry into new paths. Retained partial tables are evidence, not admitted results.

Require exit 0, `results_admitted: true`, a completion marker and a successful VERIFY before consuming a peak capture. VERIFY checks consistency, native split bytes, coordinates and grouped reduction. It does **not** independently refit MACS statistics, authenticate execution/host libraries or validate biology. A coordinated source-statistic forgery with matching copies and hashes can pass. These examples do not claim complete Signac, full-paper reproduction or catalog implementation credit.
