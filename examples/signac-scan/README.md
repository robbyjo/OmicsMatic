# Complete synthetic Signac motif scan

Read the [development guide](../../docs/literature/SIGNAC_SCAN_DEVELOPMENT.md) for exact runtime requirements, input formats and scientific limits. These inputs contain 80 synthetic peaks, 12 cells and three synthetic PFMs; they are not a reference assembly or a JASPAR collection.

From the repository root in PowerShell, with the exact permitted R libraries already available:

```powershell
$scanJar = (Resolve-Path 'build/literature-review/signac-runtime/scan-workflow-build-4/omicsmatic-0.3.7-signac-scan-workflow-development.jar').Path
$scanClass = 'org.jlinalg.cli.SignacScanRequestCli'
java -Xmx768m -cp $scanJar $scanClass --request examples/signac-scan/request.json
java -Xmx768m -cp $scanJar $scanClass --request examples/signac-scan/verify-request.json
```

`request.json` selects 20 GC-matched background peaks using seed 7013. It writes `build/signac-scan-effective-demo-NEW`. `legacy-request.json` uses unchanged Signac 1.6 functions on the explicitly current scanner/dependencies. `all-background-request.json` uses every peak. Each request has its own output path. Relative paths resolve against the request directory, and existing output directories are refused.

Inspect `WARNINGS.md`, `diagnostics.json` and `effective-pfms.tsv` before using results. TFBSTools' original constructor truncates fractional PFM counts toward zero; the effective slots are captured and checked in Java. Modern source missing-chromosome placeholder entries are not observed motif hits. Enrichment uses the background alone as its hypergeometric population, and NaN/Inf remain explicit.

The generated Java-only request reuses the scanned matrix and exact captured query/background, without R:

```powershell
java -Xmx768m -cp $scanJar org.jlinalg.cli.SignacMotifsRequestCli --request build/signac-scan-effective-demo-NEW/native-enrichment-request.json
```

**SKIP3 means skip dependent analysis when a required runtime is unavailable or forbidden.** Provide the exact permitted libraries and update the request paths, or retain the skip. FIT never installs software. **FAIL2 admits no result:** preserve the attempt, correct the named input/runtime/source/audit problem and retry into NEW output paths. Unicode cell IDs and paths pass; Unicode motif IDs can fail the original Signac name-consistency check. Do not rename identities silently.

This standalone development workflow does not implement per-cell chromVAR or all Signac paper roles, and it is not a released executable or root CLI command.
