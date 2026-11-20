# Native Scanpy Pearson normalization example

This synthetic example needs Java 17+ and the current source build or restored
development component; it uses no Python, subprocess, network or runtime install.
The public 0.4.0 release predates this API. From the repository root:

```powershell
python -B tools/literature/verify_scanpy_pearson.py --out build/pearson-readback-NEW --restore-component
javac --release 17 -cp build/pearson-readback-NEW/component/omicsmatic-scanpy-pearson-development.jar -d build/pearson-example-NEW examples/scanpy-pearson/ScanpyPearsonExample.java
java -cp "build/pearson-readback-NEW/component/omicsmatic-scanpy-pearson-development.jar;build/pearson-example-NEW" ScanpyPearsonExample
```

On Linux, replace the classpath separator `;` with `:`. Choose NEW directories;
preserve previous attempts. The restored JAR is a library without a Main-Class.
The example compiled and ran on Windows Java 21/25 and Linux Java 21.

The input has two cells and a gene with zero counts in every cell. The explicit
1.10.4 profile retains original NaNs, reports `usable=false`, and prints only
diagnostics. The 1.13.0 profile retains the newer source's zero convention and
admits its finite matrix with a warning. This demonstrates a version difference;
it is not permission to switch an author's edition to manufacture success.

Read the [component guide](../../docs/vignettes/scanpy-pearson-native.md).
Inspect `usable()` and `warnings()` before calling `matrix()`. Nonfinite model
terms or positive expected-count loss refuse a complete matrix even when clipped
values look finite. No variance floor, fitted SCT model, gene/cell filtering,
HVG selection, PCA or downstream biological inference is supplied by this API.

**SKIP3** in the saved proof reader means restore the named exact permitted
missing evidence/source item; skip its dependent audit until then. **FAIL2**
means preserve changed/invalid evidence, correct the cause and retry NEW output.
Native library exceptions/warnings carry equivalent no-result states; they are
not CLI exit codes. Do not replace NaN/Inf, round counts or refresh old hashes.
