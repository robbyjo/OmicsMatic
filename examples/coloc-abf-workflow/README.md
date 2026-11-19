# Native COLOC ABF file workflow

This synthetic example compares supplied quantitative-trait beta estimates
with case-control log-odds estimates. **`varbeta` is variance (SE squared).**
It demonstrates file handling; it is not a paper reproduction.

From the repository root, use a JAR built from the current source:

```powershell
.\gradlew.bat :test --tests org.jlinalg.cli.ColocAbfWorkflowTest executableJar --no-daemon
java -jar build/libs/omicsmatic-0.4.0.jar coloc-abf --request examples/coloc-abf-workflow/run.json
java -jar build/libs/omicsmatic-0.4.0.jar coloc-abf --request examples/coloc-abf-workflow/verify.json
java -jar build/libs/omicsmatic-0.4.0.jar coloc-abf --request examples/coloc-abf-workflow/replay.json
```

On Linux/macOS use `./gradlew`. Substitute the filename produced by your build
if different. The existing published 0.4.0 release predates this command.
Request-relative paths put outputs under `build/`; use new output names for
each new attempt. Outputs are never overwritten. For larger data choose a
suitable explicit heap, for example `java -Xmx4g -jar ...`.

Read `build/coloc-abf-example-run-1/WARNINGS.md`, `analysis-status.json`,
`summary.tsv` and `variants.tsv`. Standard error displays warnings and recovery
guidance. `COMPLETED.json` is written only after the complete native calculation
and internal verification pass. `VERIFY` also works directly:

```powershell
java -jar build/libs/omicsmatic-0.4.0.jar coloc-abf --verify build/coloc-abf-example-run-1
```

**Exit 3 (SKIP3):** dependent analysis skipped because a named prerequisite is
missing/unreadable. Restore it and retry a new output. No R installation is
needed for native analysis.

**Exit 2 (FAIL2):** invalid/nonfinite data, changed evidence or another failure;
no complete result is admitted. Preserve raw inputs, partials and errors,
correct the cause and retry a new output. Do not repair IDs/alleles, floor
probabilities, replace NaN/Inf, or silently change scientific settings.

Read the [workflow guide](../../docs/vignettes/coloc-abf-workflow.md) for P-value
inputs, weights, exact merge behavior, output fields, limits and verification
scope. The existing `coloc` command takes SuSiE signal files and remains a
separate method.
