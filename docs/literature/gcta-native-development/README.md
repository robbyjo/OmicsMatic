# Native GCTA GRM component evidence

Read the [current checkpoint](../GCTA_NATIVE_CHECKPOINT_2026-11-20.md) for exact
scope, warnings, recovery and remaining work. This is an API component; no
GCTA/COJO file workflow, global command or catalog credit is added.

`PROFILE.json` pins current component source/tests/example/launcher/reader,
the saved evidence archive and exact normal producer. `evidence.zip` retains
synthetic original inputs/results, independent rational checks, pre-correction
cancellation failure, configuration-cache preparation failure, successful normal
build reports, packaged three-JVM checks, example execution, source correction
and machine-specific research scripts. It excludes original runtime executables,
full paper text and the large producer JAR. The 35-case JUnit resource is
separately versioned under `src/test/resources/org/jlinalg/genetics` and has an
internal member checksum manifest. SHA-256 is integrity, not a signature.

Verify saved evidence from the repository root:

```powershell
python -B tools/literature/gcta_native/verify_evidence.py --out build/gcta-native-saved-check-1 --jar path/to/exact/captured/omicsmatic-0.4.0.jar
```

The output must be new. This reads saved evidence and source pins; it executes
no Java or new statistical models. Producer integrity is SKIP3 if the exact JAR
is not supplied/present; a different supplied JAR fails. Restore the qualified
source/evidence/producer or use its historical owner; never refresh old hashes
to pass a check. Source/tool workflow execution remains separate.

Run the focused Gradle tests to perform actual Java evaluation. The developer
launcher `tools/literature/gcta_native/PackagedGrmTests.java` runs all nine tests
with the normal JAR first on the classpath, plus only exact test classes,
JUnit dependencies and the test resource. Captured commands and dependency
hashes show this arrangement on Windows Java21/25 and Linux Java21. Linux
source compilation remains SKIP3 until a permitted JDK is supplied.
