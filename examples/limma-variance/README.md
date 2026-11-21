# Prepared-variance native example

This example calls the native limma3.68.5 variance component with explicit
legacy/nonrobust/untrended selection. It does not fit expression models or
perform a differential test. No R process or package is required to run it.
Read the [scope, warnings and original-source evidence](../../docs/literature/LIMMA_VARIANCE.md).

After the normal executable JAR is built, use a new output directory. PowerShell:

```powershell
New-Item -ItemType Directory -Path build/NEW-limma-example
javac --release 17 -cp build/cli/omicsmatic-0.4.0.jar -d build/NEW-limma-example examples/limma-variance/VarianceExample.java
java -cp "build/cli/omicsmatic-0.4.0.jar;build/NEW-limma-example" VarianceExample
```

On Linux/macOS, use `mkdir build/NEW-limma-example` and replace the classpath
separator `;` with `:`. An available javac is required to compile the example;
the current Linux qualification transferred classes compiled for Java17.

The original zero-variance offset warning is printed prominently to stderr.
The example checks `finite()` and exits 2 with **NO COMPLETE RESULT** if the
component is invalid. Success prints the prior and posterior variance values;
preserve warnings and supplied inputs when incorporating this component into
a larger analysis. Seven output fields were checked against actual original R
results on Windows21/25 and Linux21. No full limma catalog credit is added.
