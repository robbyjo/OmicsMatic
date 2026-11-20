# Native dispersion-HVG example

This synthetic Java example supplies 15 cells and 60 genes as explicitly
logarithmized float64 values, then selects Seurat-flavor HVGs with the
Scanpy 1.13.0 profile. It does not infer a recipe or reproduce a paper.

Use a JDK 17+ and current source build (`./gradlew executableJar`):

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar examples/scanpy-hvg/ScanpyHvgExample.java
```

Alternatively restore the exact small development library from the saved
proof, using Python 3.11+ and NEW output:

```powershell
python -B tools/literature/verify_scanpy_hvg.py --out build/hvg-proof-NEW --restore-component
java -cp build/hvg-proof-NEW/component/omicsmatic-scanpy-hvg-development.jar examples/scanpy-hvg/ScanpyHvgExample.java
```

The computation needs only Java's base module. Python is used by the proof
reader, not by HVG selection. The public 0.4.0 release predates this API; the
restored development library is a separately identified artifact.

Always check `result.usable()` and inspect warnings before calling
`selected()` or `selectedFeatures()`. Diagnostic arrays can contain explicitly
marked source-missing annotations or failed arithmetic. Selection refuses
invalid arithmetic, lost positive terms and capacity/cancellation failures;
it does not substitute another flavor or repair a dataset. Top-gene ties
can make an unbatched selection larger than the requested count.

Read the [API guide](../../docs/vignettes/scanpy-hvg-native.md) for the exact
two flavors, batch categories, export precision, missing-value conventions
and prominent failure/recovery instructions. Other Scanpy operations and the
original `scanpy-tools` workflow remain separate explicit paths.
