# Native Scanpy preprocessing API example

This component runs entirely in Java. It requires no Python, Scanpy installation
or remote service. It covers explicit float64 dense/CSR filtering,
`normalize_total`, `log1p` and `scale` profiles for Scanpy 1.9.3 and 1.13.0.
It does not implement HVGs, regression, PCA, neighbors, clustering, embeddings,
gene scores, differential expression, AnnData I/O or complete paper workflows.
The general Scanpy file/upstream workflow and catalog admission remain open.

From the repository root, restore the small saved component and run the
artificial example with Java 17 or later. Choose a NEW readback directory:

```powershell
python -B tools/literature/verify_scanpy_preprocessing.py --out build/scanpy-preprocessing-readback-NEW --restore-component
$scanpyJar = 'build/scanpy-preprocessing-readback-NEW/component/omicsmatic-scanpy-preprocessing-development.jar'
$scanpyClasses = 'build/scanpy-native-example-classes'
javac -encoding UTF-8 --release 17 -cp $scanpyJar -d $scanpyClasses examples/scanpy-native/ScanpyNativeExample.java
java -cp "$scanpyClasses;$scanpyJar" ScanpyNativeExample 1.9.3
java -cp "$scanpyClasses;$scanpyJar" ScanpyNativeExample 1.13.0
```

On Linux/macOS use `:` instead of `;` in the Java classpath. The restored JAR
is a development **library**, so use the example class; it has no `java -jar`
command. A normal current project executable build also contains this API.
The published 0.4.0 release predates it. The reader validates saved evidence;
it does not rerun Python or biological analysis.

The example chooses target 10,000, log base 10 and centered scaling with cap 1
explicitly. Those choices illustrate the API; they are not inferred defaults
or a biological recommendation. Source 1.9.3 clips only the upper tail, while
1.13.0 also clips the lower tail when centering. Compare the printed results.

Inputs use the existing immutable features-by-cells CSC container. Its storage
is the transpose view of Scanpy cells-by-genes CSR. `Storage.DENSE` requires
every entry, including zeros, explicitly stored; do not relabel a partial CSR
matrix as dense. Exact feature/cell identifiers, ordering and stored zeros are
retained. Integer and float32 casting rules are separate from this float64 API.

Always inspect `usable()` and `warnings()`. `matrix()` refuses undefined or
nonfinite statistics even if source clipping left finite-looking values.
`diagnosticValues()`, returned source normalization factors and scale statistics
remain available. Never replace NaN/Inf, silently change an edition, target,
log base or clipping rule, or treat a zero source standard deviation as evidence
of variability. Capacity/interruption/invalid-input failures admit no complete
matrix; retain inputs, correct the named cause and retry. Raise capacities only
when the complete population and resources have been reviewed.

See the [component guide](../../docs/vignettes/scanpy-preprocessing.md) for
version differences, source/runtime provenance and remaining author roles.
