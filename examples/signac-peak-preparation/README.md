# Grouped peak-call fragment preparation

This synthetic native Java example splits fragments before grouped peak calling. **It does not run MACS, identify peaks or produce significance results.** It needs Java only. The [development guide](../../docs/literature/SIGNAC_PEAK_PREPARATION_DEVELOPMENT.md) explains the exact Linux Signac source behavior and remaining runtime workflow.

From the repository root in PowerShell, using your JDK:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/signac-peak-preparation-development.init.gradle executableJar
$peakPreparationJar = 'build/literature-review/signac-runtime/peaks-preparation-development/omicsmatic-0.3.7-signac-peak-preparation-development.jar'
New-Item -ItemType Directory -Force build/signac-peak-example-classes | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" --release 17 -encoding UTF-8 -cp $peakPreparationJar -d build/signac-peak-example-classes examples/signac-peak-preparation/PeakPreparationExample.java
& "$env:JAVA_HOME/bin/java.exe" -cp "$peakPreparationJar;build/signac-peak-example-classes" PeakPreparationExample examples/signac-peak-preparation/fragments.tsv build/signac-peak-example-output
```

Remove `--offline` only if Java dependencies are uncached and downloading is permitted. The output directory must be **new**; use a different path on another attempt. Expected records are `group_A: 2`, `group_B: 1` and `group_C: 0`, in `g1.bed`, `g2.bed` and `g3.bed`. Read support 99 does not create 99 records; the zero support value remains unchanged. The unselected barcode is excluded.

Group names undergo the original Linux Signac space/slash-to-underscore substitution. The example prints warnings and the original-label mapping is retained in the API result. If two labels collapse to the same source name, their fragments are pooled with an explicit collision warning. Review that grouping before calling peaks; do not interpret separate comparisons for collapsed labels.

Input cells equal raw barcodes here by explicit declaration. For real multi-file experiments, supply per-source `SignacFragments.Cell(objectCell, rawBarcode)` maps and preserve sample identities. The native API checks complete plain/gzip input, UTF-8, record shape and capacities. It preserves selected data-line bytes and line endings, including the possibility that a missing terminal newline joins records when files are appended. No silent repair occurs.

This API has no capture/VERIFY CLI or automatic installer. Retain input provenance, outputs and warnings yourself until using a separately admitted complete workflow. Invalid input, capacity or cancellation throws an exception and can leave owned partial files; preserve the attempt and retry into a new directory after correction. Existing output directories are refused. Missing/forbidden MACS requirements in the unfinished downstream workflow must mean **SKIP3 and skip dependent peak analysis**, not a substitute peak-calling algorithm.
