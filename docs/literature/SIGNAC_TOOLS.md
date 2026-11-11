# Signac workflows in the development executable

`signac-tools` provides one explicit entry point for eleven scoped workflows. Native Java methods handle normalization, feature selection, fragments, TSS/gene activity, supplied-population motif enrichment, and coverage plotting. Original upstream programs provide SVD, motif scanning, link backgrounds, differential tests, peak calling, and chromVAR background sampling under their declared runtime profiles.

```powershell
$signacJar = 'build/literature-review/signac-runtime/tools-development/omicsmatic-0.3.7-signac-tools-development.jar'
java -jar $signacJar signac-tools --help
java -jar $signacJar signac-tools --source-report
java -jar $signacJar signac-tools --workflow normalize --request examples/signac-normalize/request.json
java -jar $signacJar signac-tools --workflow normalize --request examples/signac-normalize/verify-request.json
```

Build this separate artifact with the repository Gradle wrapper:

```powershell
.\gradlew.bat --no-daemon -I tools/literature/signac-tools-development.init.gradle executableJar
```

Use Java 17+; actual development validation uses Windows Java 21/25 and Linux Java 21. This command is absent from older released/canonical JARs. The separate build leaves the canonical executable unchanged. Development validation and catalog admission are distinct; an available workflow is not proof of a whole package or paper being reproduced.

Always choose a workflow explicitly, including for VERIFY. Its JSON schema stays unchanged. Relative paths resolve against the request directory. Use NEW output paths and retain every attempt. On legacy Windows launchers, use ASCII request filenames and put exact UTF-8 data paths and identities inside JSON.

| Workflow | Requirements and scope | Examples and detailed contract |
| --- | --- | --- |
| `normalize` | Java; RunTFIDF methods 1–4 and explicit FindTopFeatures selection | [Examples](../../examples/signac-normalize/README.md); [API reference](SIGNAC_NORMALIZATION_DEVELOPMENT.md) |
| `lsi` | Declared R/Signac and SVD dependencies; native count preparation | [Examples](../../examples/signac-lsi/README.md); [Contract](SIGNAC_LSI_DEVELOPMENT.md) |
| `fragments` | Java; feature counts, fragment totals, legacy nucleosome QC, binarization | [Examples](../../examples/signac-fragments/README.md); [Contract](SIGNAC_FRAGMENTS_DEVELOPMENT.md) |
| `annotations` | Java; TSS profiles/enrichment and raw gene activity | [Examples](../../examples/signac-annotations/README.md); [Contract](SIGNAC_ANNOTATION_DEVELOPMENT.md) |
| `motifs` | Java; supplied motif, query, and background populations | [Examples](../../examples/signac-motifs/README.md); [Contract](SIGNAC_MOTIFS_DEVELOPMENT.md) |
| `scan` | Declared R/motifmatchr, motifs and reference; original motif scanning/construction | [Examples](../../examples/signac-scan/README.md); [Contract](SIGNAC_SCAN_DEVELOPMENT.md) |
| `links` | Declared R/Signac background dependencies; original sampling and native link audit | [Examples](../../examples/signac-links/README.md); [Contract](SIGNAC_LINKS_DEVELOPMENT.md) |
| `markers` | Declared current Seurat/R profile; original Wilcoxon or logistic regression | [Examples](../../examples/seurat-differential/README.md); [Contract](SEURAT_DIFFERENTIAL_DEVELOPMENT.md) |
| `peaks` | Linux Java and exact private MACS2/Python; native preparation/reduction | [Examples](../../examples/signac-peaks/README.md); [Contract](SIGNAC_PEAKS_DEVELOPMENT.md) |
| `coverage` | Full Java desktop/font/PNG runtime, including when headless; native tracks and annotation models | [Examples](../../examples/signac-coverage/README.md); [Contract](SIGNAC_COVERAGE_DEVELOPMENT.md) |
| `chromvar` | Existing chromvar-tools contracts; R background or native deviations | [Examples](../../examples/chromvar-background/README.md); [Contract](CHROMVAR_TOOLS.md) |

**Missing requirements: SKIP3 and skip the dependent analysis.** Analysis never installs software. Where installation is permitted, follow the relevant guide to provision the exact declared profile, then retry into NEW output paths. Where installation is forbidden, retain the skip and report the affected analysis. MACS calling needs Linux Java, including WSL on Windows; its completed-bundle VERIFY works with Java on Windows. Do not substitute another version, method, or package silently. Cell Ranger remains excluded at the user's request.

**FAIL2: no result admitted, including partial files.** Read the console cause, `WARNINGS.md`, status/diagnostics, and raw logs. Preserve the attempt, correct the named input, profile, integrity, process, or capacity problem, and retry into NEW paths. Require the workflow's completed status and successful VERIFY before using outputs. Review warnings even after exit 0. A partial FindAllMarkers comparison set is not evidence that omitted groups have no markers.

For normalization, input is coordinate integer MatrixMarket (features × cells) plus exact headerless feature/cell files. Counts and feature/cell totals must fit the exact nonnegative integer domain through 2^53−1. Duplicate coordinates coalesce; explicit zeros remain stored. Supply `selection` explicitly: ALL, COUNT with numeric value, PERCENTILE with value 0–100, or KEEP_EXISTING with `existing_features`. COUNT uses strict `>`; percentiles use source ECDF/tie behavior, not a fixed number of features. KEEP_EXISTING preserves declared order. The normalized matrix retains all original features independently of variable-feature selection.

NORMALIZE accepts `tfidf_method` 1–4 (default 1), positive finite `scale_factor` (default 10000), and optional `idf` with exact `feature\tidf` header and all features in original order. Supplied IDF is already on the desired method scale and is not logged again. Scale affects methods 1 and 3. TOP_FEATURES refuses unused normalization fields. **An empty selection is valid: skip dependent LSI rather than silently relaxing the cutoff. Infinity is retained: skip analyses requiring finite values.** Feed original integer counts to `lsi`, which normalizes internally.

Normalization bounds are explicit: default 2 million features, cells, and input entries; entries can be raised to 20 million. Default aggregate input capture is 1 GB and complete output capture is 2 GB; each can be declared up to 1 TB within available resources. `maximum_line_characters` defaults to 16384 and accepts 4096–1000000. Resource failures admit no result. The normalization schema accepts no arbitrary user command or R code.

The arithmetic source profiles retain five Signac versions; normalization selection follows the current 1.17.1 assay-wrapper contract. Original-source functions running with current dependencies do not establish historical whole-package equivalence. Other packages' RNA normalization, WNN/integration, custom correlations, motif collections, reference annotations, and author-specific settings remain separate.

VERIFY has a workflow-specific scope. Native normalization replays arithmetic and selection. Coverage replays SVG and plot data; PNG verification checks format/dimensions/hashes, not pixels. Some original statistical, RNG, and KDE fits are captured and audited without independent native replay. Read the linked guide for the exact boundary. Hashes establish integrity, not execution authenticity or biological validity. The existing chromVAR integration receives no additional tool credit for appearing here.
