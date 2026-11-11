# Checked native motif enrichment examples

These synthetic inputs contain 84 peak rows and ten motif columns. The command reproduces Signac FindMotifs statistics from a **prepared motif matrix and explicit query/background peak lists**. It requires Java only. Read the [development guide](../../docs/literature/SIGNAC_MOTIFS_DEVELOPMENT.md) for the source inference, formats and limits.

**WARNING — This command does not scan a FASTA/PFM, select GC-matched backgrounds or calculate per-cell chromVAR scores.** Its inputs are prepared motif occurrences and selections. Those upstream workflows remain under development. An independent background population is used directly in the source hypergeometric call; it is not a pooled query-plus-background Fisher test.

From the repository root:

```powershell
$motifJar = 'build/literature-review/signac-runtime/motif-workflow-build-1/omicsmatic-0.3.7-signac-motif-workflow-development.jar'
java -Xmx512m -cp $motifJar org.jlinalg.cli.SignacMotifsRequestCli --request examples/signac-motifs/request.json
java -Xmx512m -cp $motifJar org.jlinalg.cli.SignacMotifsRequestCli --request examples/signac-motifs/verify-request.json
```

Open `build/signac-motifs-demo-NEW/WARNINGS.md`, `analysis-status.json`, `enrichment.tsv` and `diagnostics.json`. Exit 0 means completed with warnings; exit 2 means failure. **Read the warnings even after success.** Source fold enrichment can be `Inf`/`NaN`, and some source p values can be zero. These do not establish TF activity or regulatory causality. Failed verification and partial tables are not admitted results.

| Request | Statistical profile | Output directory |
| --- | --- | --- |
| `request.json` | 1.17.1-style statistics; explicit 12 query / 72 background rows; BH correction | `build/signac-motifs-demo-NEW` |
| `legacy-request.json` | 1.6-style statistics; same selections; no `p.adjust` column | `build/signac-motifs-legacy-demo-NEW` |
| `all-background-request.json` | 1.17.1-style statistics; null background uses all 84 rows, including query rows | `build/signac-motifs-all-demo-NEW` |

Run an alternative with the same command and its filename. Relative paths resolve against the JSON file's directory. Existing output directories are refused: preserve each attempt, choose a new `out`, and update the matching verification path before rerunning. Keep request filenames/paths ASCII on the tested Windows launchers; put exact Unicode paths/labels inside UTF-8 JSON and tables.

`inputs/matrix.mtx` is peaks-by-motifs, not peaks-by-cells. `peaks.tsv` has one matrix row ID per line. `motifs.tsv` has actual tab-separated `motif_id` and `motif_name` columns; it includes a Unicode display name. Query/background files have one peak ID per line and no header. Duplicate selection rows remain counted repeatedly. Motif IDs and matrix peak IDs must be unique.

No R installation is needed for these native requests or their verification. For future upstream scanning/chromVAR workflows, unavailable or forbidden dependencies must produce **SKIP3**: provide/install the named exact runtime only where permitted; otherwise retain the skip and skip dependent analyses. This native command never installs software.

The three literal enrichment requests and the literal VERIFY request were executed. Additional fresh enrichment runs used Windows Java 25 and Linux Java 21; all five outputs matched original statistics and passed three-platform captured verification. See [validation evidence](../../docs/literature/signac-motif-development/VALIDATION.json).
