# Prepared native TwoSampleMR statistics

Build current source with `gradlew.bat executableJar` on Windows or
`./gradlew executableJar` on Linux. The released 0.4.0 predates these classes.
The source-built JAR defaults to `build/cli/omicsmatic-0.4.0.jar`.

From the project root, with a JDK on PATH:

```powershell
New-Item -ItemType Directory build/NEW-native-mr-example
javac --release 17 -cp build/cli/omicsmatic-0.4.0.jar -d build/NEW-native-mr-example examples/twosamplemr-native/NativeMrExample.java
java -cp "build/cli/omicsmatic-0.4.0.jar;build/NEW-native-mr-example" NativeMrExample
```

On Linux, create the new directory with `mkdir` and replace the Java classpath
separator `;` with `:`. Compilation needs a JDK; the resulting class runs on
the tested Windows Java 21/25 and Linux Java 21 runtimes. This synthetic example
is checked against the unmodified original source's IVW and median point.

Inputs are already harmonized independent-variant effect/SE vectors in identical
order. Four aligned, complete finite vectors and positive standard errors form
the native API contract. No variant matching, filtering, normalization or data
download takes place. Read each statistic's `kind` and every warning before use.
`NA`, arithmetic `NAN` and infinities remain distinct. A warning beginning
`NO_COMPLETE_RESULT` means no complete estimate should be reported.

`weightedMedianPoint` has no bootstrap SE. `medianFromDraws` accepts paired
replicate-by-variant **sampled effects**, not standard-normal deviates. Its
weights are fixed from the original inputs. Java does not generate or claim to
reproduce R random draws. The reference test fixtures contain actual R draws.

Read the [API scope, eight methods and warnings](../../docs/literature/TWOSAMPLEMR_NATIVE_COMPONENTS.md).
This API targets pinned 0.7.14 source; paper editions, file workflows, modes,
clumping and harmonization remain open. The existing `mr-estimate` command
retains its earlier implementation and does not select this API automatically.
