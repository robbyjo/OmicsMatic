# Native single-causal COLOC ABF example

The Java API accepts supplied regional beta/variance or P-value summary
statistics. It needs no R runtime for analysis. Read the
[API guide](../../docs/vignettes/coloc-native-abf.md) and the
[handover](../../docs/literature/HANDOVER_2026-11-19_COLOC_ABF.md) first.

From the repository root, after building the executable JAR:

```powershell
.\gradlew.bat :test --tests org.jlinalg.coloc.ColocAbfTest executableJar --no-daemon
javac --release 17 -cp build/libs/omicsmatic-0.4.0.jar -d build/coloc-abf-example examples/coloc-abf/NativeAbfExample.java
java -cp "build/coloc-abf-example;build/libs/omicsmatic-0.4.0.jar" NativeAbfExample
```

On Linux/macOS use `./gradlew` and a colon instead of the classpath semicolon.
If your build produces a different filename, substitute that file. Use a JAR
built from this source; the existing published 0.4.0 release predates this API.
The example prints H0 through H4, conditional SNP posteriors and warnings.
Its three variants are synthetic; the output is not a paper reproduction.

**No ordinary-summary-statistics ABF command-line workflow is implemented yet.**
The existing `coloc` CLI consumes SuSiE log-Bayes-factor rows and is a separate
method. Do not send beta/variance or P-value tables to that CLI.
