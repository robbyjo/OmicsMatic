# Native Seurat-style marker summaries

This synthetic Java example computes fold changes, detection fractions and explicit prefilters. It **does not test differential expression/accessibility or produce p values**. It uses the current 5.6.0 summary profile and raw-count mean semantics; no normalization occurs.

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/seurat-marker-summary-development.init.gradle executableJar
$markerJar = 'build/literature-review/signac-runtime/differential-summary-development/omicsmatic-0.3.7-seurat-marker-summary-development.jar'
New-Item -ItemType Directory -Force build/marker-summary-example-classes | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" --release 17 -encoding UTF-8 -cp $markerJar -d build/marker-summary-example-classes examples/seurat-marker-summary/MarkerSummaryExample.java
java -cp "$markerJar;build/marker-summary-example-classes" MarkerSummaryExample
```

Use your actual JDK. Remove `--offline` only if cached Java dependencies are unavailable and downloads are permitted. This example itself needs Java only. It prints equal-magnitude opposing fold changes for two synthetic peaks; those are summaries, not significance results.

The API requires explicit profiles, matrix slots, normalization provenance and disjoint cell populations. Do not replace historical settings with current defaults or use these summaries to claim whole Signac/Seurat workflow reproduction. See the [development guide](../../docs/literature/SEURAT_MARKER_SUMMARY_DEVELOPMENT.md) for warnings, exact source differences and remaining statistical-workflow work.
