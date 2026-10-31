# Native repeat annotation example

This example uses the new Java API from this checkout. It imports two synthetic
RepeatMasker-format annotation fragments and computes exact unions and masking.
It does not search for repeats or reproduce a paper's genome analysis.

From the repository root on Windows:

```powershell
.\gradlew.bat executableJar
New-Item -ItemType Directory -Force build/repeat-annotation-example | Out-Null
javac --release 17 -cp build/cli/omicsmatic-0.3.7.jar -d build/repeat-annotation-example examples/native-repeat-annotations/RepeatAnnotationExample.java
java -cp "build/repeat-annotation-example;build/cli/omicsmatic-0.3.7.jar" RepeatAnnotationExample examples/native-repeat-annotations/annotations.rmout
```

On Linux:

```bash
./gradlew executableJar
mkdir -p build/repeat-annotation-example
javac --release 17 -cp build/cli/omicsmatic-0.3.7.jar -d build/repeat-annotation-example examples/native-repeat-annotations/RepeatAnnotationExample.java
java -cp "build/repeat-annotation-example:build/cli/omicsmatic-0.3.7.jar" RepeatAnnotationExample examples/native-repeat-annotations/annotations.rmout
```

The first four output lines are:

```text
query_covered_bases=90
consensus_covered_bases=80
consensus_at_least_75_percent=true
masked_bases=90
```

The final line contains the 100-base masked synthetic sequence. Its ten-base
gap remains uppercase. Overlapping consensus fragments contribute 80 bases,
not the sum of their lengths. The threshold is exactly inclusive at 75%; the
API also supports strict comparisons, such as more than 20%.

**Required action:** use a JAR rebuilt from this checkout and Java 17 or later.
Select the annotation format explicitly: OUT with/without IDs or UCSC `rmsk`
with/without its bin column. Keep the original annotation, reference/build
labels and source provenance. UCSC signed consensus fields remain raw and
cannot be passed to ordinary OUT consensus coverage. Inspect exceptions for
invalid data or capacity and correct the cause; excess input is not truncated.

Original RepeatMasker searches require a separately admitted upstream runtime;
the full project CLI and paper-role integration are still in development.
Read [the current scope and warnings](../../docs/literature/omics-2023-2026/REPEATMASKER_NATIVE_STATUS.md).
