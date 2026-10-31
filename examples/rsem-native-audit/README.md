# Native RSEM result audit example

This small Java-only example checks an existing original RSEM result pair. It consumes every row and numeric field and retains source hashes. **It does not fit an RSEM model. The project's protected public quantification command and runtime setup remain unfinished; RSEM still counts as missing.**

The bundled synthetic original 1.3.3 run has six transcripts, four genes, 32 alignable fragments and two unalignable fragments. It requested posterior means, credibility intervals and read-start distribution estimation with seed 1618. The input ZIP includes the complete ordinary reference core, `.cnt`, `.model`, both theta vectors, both result tables and all eight file checksums. Its SHA-256 is `656c1ed7a470a4b8c442b112c902846c81117a6c5765420958a795eb099d1884`.

Run from the repository root with a JDK providing Java 17 or newer. Build the current source first; the earlier published 0.3.7 release does not contain these development APIs. No RSEM installation or installation permission is needed for this native audit. The first Gradle build needs the project's declared build dependencies.

PowerShell:

```powershell
.\gradlew.bat executableJar
New-Item -ItemType Directory -Force build/rsem-native-example-classes | Out-Null
javac --release 17 -encoding UTF-8 -cp build/cli/omicsmatic-0.3.7.jar -d build/rsem-native-example-classes examples/rsem-native-audit/RsemNativeAuditExample.java
java -cp "build/cli/omicsmatic-0.3.7.jar;build/rsem-native-example-classes" RsemNativeAuditExample examples/rsem-native-audit/synthetic-original-output.zip build/rsem-native-example-run-1
```

Linux:

```bash
bash gradlew executableJar
mkdir -p build/rsem-native-example-classes
javac --release 17 -encoding UTF-8 -cp build/cli/omicsmatic-0.3.7.jar -d build/rsem-native-example-classes examples/rsem-native-audit/RsemNativeAuditExample.java
java -cp 'build/cli/omicsmatic-0.3.7.jar:build/rsem-native-example-classes' RsemNativeAuditExample examples/rsem-native-audit/synthetic-original-output.zip build/rsem-native-example-run-1
```

Expected: `Audited 6 transcripts, 4 genes and 162 numeric fields.` The new run directory contains the original inputs and `audit-summary.txt`, including all eight consumed file hashes and the precision/inference warnings. This example's `status` means the native result checks completed; it does not certify biological accuracy, convergence or an arbitrary runtime. The tiny run is a method check.

**Failure recovery:** preserve any partial output and read the named exception. Use a NEW run directory after correcting the cause. An existing output directory is refused. A changed ZIP is refused before output creation; restore the bundled file instead of editing its expected checksum. The example intentionally accepts only this exact synthetic bundle. For other reviewed inputs, call the library API described in [RSEM_RESULT_AUDIT.md](../../docs/literature/RSEM_RESULT_AUDIT.md), with complete source files, the exact release, original output options and explicitly reviewed capacities.

The original captured result is also covered by the complete five-release fixture tests. The earlier [result-audit validation](../../docs/literature/RSEM_RESULT_AUDIT_VALIDATION.json) remains historical; current count admission uses the corrected [alignment validation](../../docs/literature/RSEM_ALIGNMENT_ADMISSION_VALIDATION.json). Original `nIsoMulti` includes cross-gene groups, and the earlier narrower count invariant is superseded. This example also passes against the current candidate on Windows Java 21/25 and Linux Java 21, including existing-output and changed-input refusal checks. Original source: [RSEM 1.3.3](https://github.com/deweylab/RSEM/tree/e4dda70e90fb5eb9b831306f1c381f8bbf71ef0e).
