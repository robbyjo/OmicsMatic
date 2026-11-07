# Native chromVAR example

Read the [guide and required actions](../../docs/literature/CHROMVAR_DEVIATIONS_DEVELOPMENT.md). These files are complete synthetic numerical inputs: 20 peaks, six cells, three annotations and seven supplied background iterations. The backgrounds are **not GC/intensity matched** and must not be used as a substitute for study preparation.

From the repository root in PowerShell:

```powershell
$chromvarJar = (Resolve-Path 'build/literature-review/signac-runtime/chromvar-workflow-build-1/omicsmatic-0.3.7-chromvar-workflow-development.jar').Path
$chromvarClass = 'org.jlinalg.cli.ChromVarRequestCli'
java -Xmx768m -cp $chromvarJar $chromvarClass --request examples/chromvar-deviations/request.json
java -Xmx768m -cp $chromvarJar $chromvarClass --request examples/chromvar-deviations/verify-request.json
```

Native DEVIATIONS and VERIFY require Java only. `legacy-request.json` selects the original 1.16.0 formula profile; `grouped-request.json` supplies two groups. Each has a separate NEW output path. If an output directory already exists, preserve it and update the request/verification paths before retrying.

**Review WARNINGS.md even after success.** NA, NaN and Inf remain explicit source outputs; they are not validated discoveries. `norm=true` sums per-cell fractions rather than averaging them. Full-core scoring retains original peak-count and shape failures.

**FAIL2 admits no result.** Preserve the attempt, correct the named input/capacity/source cause and use NEW paths. If separate upstream preparation requires unavailable/forbidden software, retain SKIP3 and skip that dependent analysis. Native scoring does not install software, scan motifs or generate matched backgrounds. This is a standalone development class, not a released/root CLI tool.
