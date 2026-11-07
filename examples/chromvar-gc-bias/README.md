# Native chromVAR GC bias example

Run these PowerShell commands from the repository root with JDK 17 or newer and the validated local development JAR:

```powershell
$gcJar = (Resolve-Path 'build/literature-review/signac-runtime/background-gc-controls-1/omicsmatic-0.3.7-chromvar-gc-development.jar').Path
javac --release 17 -encoding UTF-8 -cp "$gcJar" -d build/chromvar-gc-example-classes examples/chromvar-gc-bias/BiasExample.java
java -cp "build/chromvar-gc-example-classes;$gcJar" BiasExample examples/chromvar-gc-bias/reference.fa build/chromvar-gc-example-NEW
```

The output directory must be new. Preserve an existing run and change the path when repeating the example. Inspect both `bias.tsv` and `WARNINGS.md`.

The four peaks produce `0.5`, `1.0`, `NaN` and `0.5`. The all-N peak has no A/C/G/T denominator. Its `NaN` is retained. Review undefined bias before downstream sampling; do not silently replace it or remove the row. The mixed peak `ACGTNN` has bias `0.5` because chromVAR excludes the two Ns from its denominator.

This is a synthetic Java API example. It calculates sequence GC bias, with no R dependency or software installation. It does not sample matched backgrounds or calculate biological motif activity. See the [API guide and validation scope](../../docs/literature/CHROMVAR_GC_DEVELOPMENT.md).
