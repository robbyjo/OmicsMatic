# Native Seurat preprocessing example

Use a **JDK 17+** with `javac` and `java` on PATH. From the repository root:

```powershell
javac --release 17 -encoding UTF-8 -d build/seurat-preprocessing-demo-new/classes src/main/java/org/jlinalg/sequence/SeuratPreprocessing.java examples/seurat-preprocessing/SeuratPreprocessingExample.java
java -cp build/seurat-preprocessing-demo-new/classes SeuratPreprocessingExample
```

The same commands work in a Linux shell. Use a new output directory. The example
normalizes a synthetic 3-feature/4-cell matrix, then scales two explicitly
selected features in the requested order. No R, external package or whole
toolbox JAR is required. The standalone compiler output was checked against
the qualified packaged method classes on Windows and Linux Java 21.

To see a deliberate failure with retained diagnostic values:

```powershell
java -cp build/seurat-preprocessing-demo-new/classes SeuratPreprocessingExample --zero-total-demo
```

**Expected exit 2: FAIL2, no complete matrix.** This input contains an explicitly
stored zero in a zero-total column. Its original source arithmetic produces
NaN. The example prints the failure, warning codes, required actions and
diagnostic values, and emits no admitted matrix table. It does not repair
values or remove the column. This is an expected demonstration, not a failed
user analysis or a request to install anything.

Read the [API guide](../../docs/vignettes/seurat-native-preprocessing.md) for
CLR margins, declared/effective scales, source conventions, limits and warnings.
These are Seurat 5.6.0 matrix operations; variable-feature selection,
SCTransform, PCA, integration and clustering remain separate work. Neither
example performs a significance test or reproduces a paper.
