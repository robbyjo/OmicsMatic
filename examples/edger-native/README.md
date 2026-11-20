# Native edgeR normalization example

These synthetic examples run in Java without R. Build the current source; the
published 0.4.0 release predates this component. From the repository root:

```powershell
.\gradlew.bat executableJar
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --request examples/edger-native/request.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --verify build/NEW-edger-native-cpm
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --request examples/edger-native/log-rpkm.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.EdgeRNormalizationCli --request examples/edger-native/replay.json
```

The first pipeline uses TMM and unlogged CPM; the second uses TMMwsp and log2
RPKM with prior count 2. Both use literal supplied library sizes; the second
also requires the supplied gene lengths. The original edgeR 3.42.4 package
independently reproduces both example matrices and normalization factors.
The saved original objects and comparison are in the qualification packet.
`compare-original.R` is an optional reference script, not a native runtime.

Choose NEW output paths if any example was already run. Paths in a request
resolve from that request's directory. REPLAY output must be outside its parent
capture. Inputs are copied exactly; VERIFY reparses those copies and recomputes
their arithmetic in Java, rather than relying only on an inventory checksum.

**SKIP3: supply the named permitted missing input or supported exact edition,
or retain the skip. FAIL2: no complete result; preserve partials and diagnostics,
resolve the cause and retry NEW output. Read `WARNINGS.md` and
`analysis-status.json`.** These examples do not perform filtering, replicate
aggregation, dispersion estimation, differential tests or author workflows.

Read the [complete method and file guide](../../docs/literature/EDGER_NORMALIZATION.md)
for the original-provider scopes, retained failures and outstanding work.
