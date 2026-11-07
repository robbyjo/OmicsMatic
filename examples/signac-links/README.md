# Checked LinkPeaks examples

These synthetic inputs contain 84 peaks, 16 paired cells and three genes. They demonstrate the guarded original Signac LinkPeaks workflow with an independent Java numerical audit. Read the [development guide](../../docs/literature/SIGNAC_LINKS_DEVELOPMENT.md) before using study data. Full Signac integration remains incomplete.

**SKIP3 — A fresh fit needs the exact R runtime listed below.** Missing/unavailable dependencies skip the analysis and admit no result. Install or provide those dependencies only where permitted; otherwise keep LinkPeaks and its dependent analyses skipped. This command performs no installation. Captured `VERIFY` needs Java only.

From the repository root:

```powershell
$linksJar = 'build/literature-review/signac-runtime/links-workflow-build-3/omicsmatic-0.3.7-signac-link-workflow-development.jar'
java -Xmx768m -cp $linksJar org.jlinalg.cli.SignacLinksRequestCli --request examples/signac-links/request.json
java -Xmx512m -cp $linksJar org.jlinalg.cli.SignacLinksRequestCli --request examples/signac-links/verify-request.json
```

The first command writes `build/signac-links-demo-NEW`. Open its `WARNINGS.md`, `analysis-status.json`, `result-links.tsv` and `diagnostics.json`. Exit 0 means completed with warnings, 2 means failure and 3 means dependency skip. **A failed verification or a partial table is not an admitted result.** Keep all attempts. Existing destinations are refused; edit `out` and the matching verification path to new paths before rerunning.

| Literal request | Profile and settings | Output |
| --- | --- | --- |
| `request.json` | Current 1.17.1 package; Pearson; 6 backgrounds; p cutoff 1 | `build/signac-links-demo-NEW` |
| `historical-request.json` | Literal 1.14.0 functions on current pinned dependencies; sparse Spearman; 10 backgrounds; p cutoff 0.05 | `build/signac-links-historical-demo-NEW` |
| `empty-request.json` | Current package; Pearson; 8 backgrounds; p cutoff 0 | `build/signac-links-empty-demo-NEW` |

Run either alternative with the same command and its filename. The first request's **p cutoff 1 is for demonstration only**. Source p values are `pnorm(-abs(z))`, without a factor of two or multiple-testing correction. They are not FDR values or proof of regulatory causality. The empty example succeeds with a header-only links table; no threshold is relaxed to obtain discoveries.

The checked JSON explicitly points to this workspace's R 4.6.1, private library `build/literature-review/signac-runtime/reference-setup-1/R-lib` and base library `build/omics-sctransform-r`. A fresh fit guards Signac 1.17.1, Matrix 1.7.5, SeuratObject 5.4.0, GenomicRanges 1.64.0, GenomeInfoDb 1.48.0 and jsonlite 2.0.0, with working native dependencies. The optional `wrswoR` package must be absent because these profiles retain base R `sample.int`. Do not remove packages from a shared library; use a suitable separate private runtime. Adjust explicit paths when preparing another permitted installation, preserve provenance and rerun into new outputs.

Every relative path resolves against the JSON file's directory. Keep the request filename/path ASCII on the tested Windows launchers; exact Unicode data paths and IDs belong inside UTF-8 JSON/tables. Inputs use actual tab-separated headers, unique IDs and exactly the same cell order in both matrices. Peak IDs must equal the corresponding one-based closed `chromosome-start-end` ranges. The synthetic genome label and supplied GC/length metadata do not certify a reference assembly.

To verify another successful capture, create a UTF-8 JSON file such as:

```json
{"schema_version":1,"mode":"VERIFY","analysis":"../../build/signac-links-historical-demo-NEW"}
```

Place it beside these requests or adjust its path accordingly. Verification checks captured inputs, exchange files, source/settings bindings, sampled-background identities, coefficients, z scores, nominal p values and retained links. It does not independently reproduce R's KDE weights or RNG and does not establish authenticity of a capture.

The three literal fit requests were executed and compared with the immutable original controls, including RNG states. Each capture passed verification on Windows Java 21, Windows Java 25 and Linux Java 21; the literal current VERIFY request was also executed. See [validation evidence](../../docs/literature/signac-link-development/VALIDATION.json).
