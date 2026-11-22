# Native ACAT 0.91 matrix combination

This artificial example combines five p-values in each of two columns. Each
column is a separate combination; rows do not identify subjects or genotypes.
The native implementation reproduces the selected checked `ACAT()` source
profile. It requires Java and the current source-built JAR; it does not run R.
See the [scope and warnings](../../docs/literature/ACAT091_NATIVE.md).

From the repository root, build the current executable JAR:

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.0.jar acat-tools --request examples/acat091/native-matrix.json
java -jar build/cli/omicsmatic-0.4.0.jar acat-tools --verify build/acat091-example
java -jar build/cli/omicsmatic-0.4.0.jar acat-tools --request examples/acat091/replay.json
java -jar build/cli/omicsmatic-0.4.0.jar acat-tools --verify build/acat091-example-replay
```

Java 21 and 25 are the qualified runtime versions for this checkpoint. On Linux,
use `./gradlew` for the build. Build prerequisites are those of the project;
install or download permitted dependencies when needed. The command is present
in the current source build, rather than an earlier release JAR. There is no
new release associated with this checkpoint.

Request paths resolve relative to their JSON file. Output directories must be
NEW. A second execution with the same output is refused; preserve the existing
capture and choose another path. After completion, read `analysis-status.json`
and `WARNINGS.md`, then use `p-values.tsv` only when `result_admitted` is true.
Replay creates a separate capture and verifies the original before and after.

**SKIP3: the dependent analysis did not run.** Supply the exact named missing
runtime or packaged resource. If installation is prohibited, retain the skip
and identify the prerequisite needed. **FAIL2: no complete result is admitted.**
Keep the failed or partial capture, fix the named input, capacity, numerical or
integrity cause, and retry NEW output. Never change nonfinite values, endpoints
or weights silently. Verification can confirm a failed capture's consistency;
it does not turn that capture into an admitted result.
