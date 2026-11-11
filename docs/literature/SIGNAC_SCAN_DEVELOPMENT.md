# Signac reference statistics, motif scanning and enrichment

This development entry point takes raw peak counts, an explicit reference FASTA and declared position frequency matrices (PFMs). It runs original Signac `AddMotifs`, `RegionStats` and `FindMotifs` functions through a guarded R adapter. Java independently checks reference statistics, motif identities, hit geometry, the occurrence matrix and enrichment statistics before publishing results. It also generates a Java-only enrichment request using the captured query and background.

**Availability:** use the standalone class `org.jlinalg.cli.SignacScanRequestCli` in the development executable below. There is no root CLI route or released executable for this milestone. The canonical `build/cli/omicsmatic-0.3.7.jar` remains unchanged. This does not close the full Signac implementation or add catalog implementation credit; the catalog remains at 68 new scoped integrations, 71 including three existing tools, with 259 named tools missing.

**WARNINGS / REQUIRED ACTIONS:** missing or forbidden dependencies produce **SKIP3**, and dependent analysis must be skipped. Provide the exact permitted requirements in separate private libraries, then retry into a **NEW** output directory. Invalid input, source failures, exceeded capacities, timeouts and failed audits produce **FAIL2** and admit no result. Preserve the attempt and inspect its `WARNINGS.md`, status, manifest, diagnostics and worker logs. A completed process alone does not admit results. Do not use retained partial files as analysis outputs.

## Run the complete synthetic example

Run these PowerShell commands from the repository root. Java 21 or later is required. The R runtime requirements are listed below; neither FIT nor VERIFY installs software. Relative paths in a request resolve against that request's directory.

```powershell
$scanJar = (Resolve-Path 'build/literature-review/signac-runtime/scan-workflow-build-4/omicsmatic-0.3.7-signac-scan-workflow-development.jar').Path
$scanClass = 'org.jlinalg.cli.SignacScanRequestCli'
java -Xmx768m -cp $scanJar $scanClass --request examples/signac-scan/request.json
java -Xmx768m -cp $scanJar $scanClass --request examples/signac-scan/verify-request.json
```

The [checked request](../../examples/signac-scan/request.json) uses 80 synthetic peaks, 12 cells and three synthetic PFMs. It declares Signac `1.17.1-package`, scanner `1.34.0-package`, seed 7013 and 20 GC-matched background peaks. It writes `build/signac-scan-effective-demo-NEW`. These synthetic sequences and motifs are not a reference assembly or a JASPAR collection. If that destination already exists, preserve it and change `out` and the VERIFY request to fresh paths.

The [historical request](../../examples/signac-scan/legacy-request.json) uses unchanged Signac 1.6 source functions with the explicitly current scanner and dependencies. The [all-background request](../../examples/signac-scan/all-background-request.json) sets `background` to JSON `null`, selecting the complete peak population. Each has a different output directory. The [input directory](../../examples/signac-scan/inputs/) contains every required analysis input; it does not contain the R libraries.

The exact development JAR is 164805475 bytes, SHA-256:

```text
b76cf00c52356e9eb589c1640fc693d10a12d5f6dd9af49786cc951da9853961
```

## Supply the study inputs

Copy a checked request, change every input/provenance field to the study's actual data and choose a fresh output path. Preserve the original inputs and record how counts, peaks, reference and PFMs were generated.

| Request field | Required content |
| --- | --- |
| `counts` | Matrix Market **integer coordinate** matrix, peaks by cells, raw nonnegative integer counts. Duplicate coordinates aggregate exactly; no normalization is inferred. |
| `peak_features`, `cells` | One exact identifier per line, no header, matching matrix order. Cell identifiers may be Unicode. |
| `peak_ranges` | TSV header `chromosome`, `start`, `end`; rows in feature order. Coordinates are **one-based closed**. Peak IDs must be `chromosome-start-end`. Convert BED's zero-based starts deliberately before admission. |
| `fasta` | Plain uncompressed `.fa` or `.fasta`, exact chromosome IDs, LF or CRLF lines. No chromosome aliasing, clipping or reference substitution. Gzip FASTA is unsupported here. |
| `pfm` | UTF-8 TSV with the exact 11-column header shown below, counts and declared background probabilities. |
| `query` | One exact peak ID per line, no header. Repeated selections retain source multiplicity. |
| `background` | Integer for original matched selection; JSON `null` for all peaks; or path to explicit peak IDs. Omitted means 40000. The source caps an oversized requested background to available rows. |
| `genome_build`, `motif_collection` | Explicit provenance declarations, not certification or automatic database lookup. |

PFM columns, separated by literal tabs:

```text
motif_id	motif_name	position	A	C	G	T	bg_A	bg_C	bg_G	bg_T
```

Keep each motif's rows contiguous. Positions must be consecutive integers starting at 1. IDs must be unique across models; a display name is separate from an ID. Counts may be fractional, must be finite and nonnegative, at most 1e12, with a positive total per position. Background probabilities must be positive and sum to one within 2e-12. The typed Java reader preserves these declarations. **The original TFBSTools 1.50.0 PFMatrix constructor then converts counts to R integers, truncating nonnegative fractions toward zero.** Counts at or above 2147483648 fail original construction; fractional counts below that boundary truncate toward zero. The workflow captures the actual slots in `effective-pfms.tsv`; Java checks every count, background, identity and position, and reports changed counts and maximum change. Zero effective columns are preserved for the original scanner to admit or reject. Do not treat fractional TSV values as the effective scanner counts. No adapter pseudocount, background normalization or deduplication is applied; MOODS calibration remains upstream behavior. PWMatrix models are a different input contract.

If the study already has a `TFBSTools::PFMatrixList`, source the [export helper](../../tools/literature/export_signac_pfms.R) in that R session:

```r
source("tools/literature/export_signac_pfms.R", encoding="UTF-8")
# study_pfms is the exact PFMatrixList used by the study.
write_signac_pfms(study_pfms, "study-pfms-NEW.tsv")
```

By default IDs are existing PFMatrixList names, as consumed by Signac. If the study explicitly uses each matrix's `ID` slot instead, choose `id_source="matrix_id"` and record that mapping. Missing or duplicate IDs fail; there is no inferred fallback. The helper preserves A/C/G/T counts and background by base label and refuses existing outputs. It does not fetch a motif database. Record the collection edition, selection filters and export provenance yourself.

Default capacities include a 20 GB uncompressed reference, 200000 sequences, 10 million bases per FASTA line, 2 million peaks, width 1 million bases per peak, and 100 million queried bases. PFM input is limited to 256 MB, 100000 models, width 512 and 2 million combined positions. The request exposes `maximum_queried_bases`, `maximum_hits` (default 1 million) and `maximum_work` (default 100 million) within validated bounds. These limits are capacity guards, not performance guarantees. Reference chromosome identifiers must be ASCII; unsupported identities fail without repair.

## Exact runtime and source profiles

FIT currently requires Windows R **4.6.1** and these exact admitted packages in the three explicit private library paths:

| Package | Version |
| --- | --- |
| Signac / SeuratObject / Matrix | 1.17.1 / 5.4.0 / 1.7.5 |
| motifmatchr / TFBSTools | 1.34.0 / 1.50.0 |
| BSgenome / Biostrings / Rsamtools | 1.80.0 / 2.80.2 / 2.28.0 |
| GenomicRanges / GenomeInfoDb / jsonlite | 1.64.0 / 1.48.0 / 2.0.0 |

Transitive dependencies must also be present. The checked requests name this workspace's isolated libraries and Rscript path; edit those paths for another permitted environment. This is an explicitly supported dependency profile, not a portable install certificate. Absence of R or a package produces SKIP3; an incompatible version fails admission. Optional `wrswoR` must be absent because its presence changes Signac's sampler. No installation or download occurs during analysis. If installation is not permitted, retain the warning and skip dependent analysis. Do not modify shared or frozen libraries to force admission.

`profile` accepts `1.6.0-source`, `1.9.0-source`, `1.14.0-source` and `1.17.1-package`. Historical controls execute retained unchanged source functions with current containers/dependencies. A strict private slot-to-layer bridge handles historical Seurat access. The 1.6 FASTA bridge maps the older `getSeq(FaFile, names=GRanges)` call to the current `param` argument in the owned R process and restores the original binding. No installed library files change. Historical RegionStats matrix results convert losslessly to a data frame for current container admission. These are not complete historical package installations.

`scanner_profile` must explicitly be `1.34.0-package`. Paper-era motifmatchr packages have not been executed in this workflow. Original AddMotifs' fixed scanner defaults are preserved; its default method does not forward `...` to the scanner, so there is no configurable scan cutoff here. Do not infer paper-exact thresholds from this adapter. `features_match` defaults to `GC.percent` and can include the captured 16 dinucleotide fractions, `sequence.length` and raw peak `count`. Signac 1.16 changed matched-background KDE weighting; the selected source profile determines that behavior. RNG state and a seeded upstream background replay are captured, not independently reconstructed in Java.

If the admitted base and Signac libraries already exist in this workspace and a fresh motif library is permitted, the [isolated reference setup helper](../../tools/literature/setup_signac_motif_reference.py) can populate a NEW private directory:

```powershell
python tools/literature/setup_signac_motif_reference.py --out build/literature-review/signac-runtime/motif-reference-NEW --allow-install
```

This explicitly permits the helper's package downloads/installations. It preserves the existing base/Signac libraries and records setup status and file hashes. It is a reference setup for this workspace, not a general installer for a new machine; it requires the existing base/Signac paths and Windows R above. Check its terminal status and package editions, then set `r_motif_library` to the successful NEW `R-lib` path. Setup success alone admits no analysis. If installation is forbidden or prerequisites cannot be supplied, retain SKIP3 and skip the dependent analysis.

## Review results and source behavior

Successful output includes `effective-pfms.tsv`, `motif-presence.mtx`, `motif-labels.tsv`, `motif-positions.tsv`, `sequence-stats.tsv` and `enrichment.tsv`, plus captured inputs/source functions, worker outputs, session/package versions, effective selections and saved RNG states. Review `WARNINGS.md` and `diagnostics.json` even when exit code is zero.

GC is the number of literal C/G bases divided by the **full declared peak width**, including ambiguity positions. Dinucleotides count adjacent canonical bases only. There is no Tn5 shift, ambiguity expansion or gap removal. A missing chromosome has unavailable reference statistics; a range beyond the reference end fails. Missing query rows fail in 1.6 and are pruned with warnings in later source profiles.

**Missing-chromosome placeholder:** Signac 1.14/current source can insert one TRUE occurrence in the last missing row/final motif because of its sparse placeholder construction. The adapter preserves and identifies that source entry; it is **not an observed motif hit**. Older profiles fail on these missing chromosomes, and all-missing inputs fail in the retained source controls. Do not interpret placeholder entries as discoveries. Duplicate display names may change under source `make.unique`, including locale-dependent Unicode suffixes; IDs stay explicit.

FindMotifs uses the background alone in `phyper(observed-1, backgroundCount, backgroundRows-backgroundCount, queryRows, lower.tail=FALSE)`. It does not pool query and background for a Fisher test. Empty populations and invalid source populations can produce NaN/Inf; these remain visible. The complete finite motif family enters correction before ordering. `p_adjust_method` supports BH, BY, holm, hochberg, hommel, bonferroni, none and the fdr alias; Signac 1.6 ignores this choice and has no adjusted-p column. Enrichment is not per-cell TF activity, regulatory causality or a biological validation result.

Native audits independently calculate reference GC/dinucleotides/length, raw count metadata, hit width and containment, complete position-to-matrix membership, and enrichment/correction. Counts compare exactly; probabilities use relative tolerance 1e-9 with absolute floor 1e-300, preserving exact source zero; other numeric comparisons use relative 2e-11 or absolute 2e-12. Java does not independently recalibrate MOODS scores, implement R's KDE/RNG, or reconstruct all locale-specific suffix rules. Hashes check integrity, not authenticity or complete host/DLL provenance.

Unicode input/output paths and cell IDs are tested. A probe adding Unicode to motif IDs fails original Signac's matrix/PWM name-consistency check under the pinned C locale; that FAIL2 capture is retained. Do not silently rename motif IDs to bypass it. Unicode display names are separately supported, with the source suffix caveat above.

## Verify or rerun enrichment without R

The generated `native-enrichment-request.json` points to the scanned occurrence matrix and exact captured query/background IDs. It runs [native motif enrichment](SIGNAC_MOTIFS_DEVELOPMENT.md) without rescanning or selecting another background:

```powershell
java -Xmx768m -cp $scanJar org.jlinalg.cli.SignacMotifsRequestCli --request build/signac-scan-effective-demo-NEW/native-enrichment-request.json
java -Xmx768m -cp $scanJar $scanClass --request build/signac-scan-effective-demo-NEW/verify-request.json
```

The first command uses a NEW sibling output directory named in that generated request. The second verifies the existing scan capture without R. Captured verification runs on Windows Java 21/25 and Linux Java 21, including when original capture paths were Windows paths. It checks inventory, source/request bindings, recorded process protocol, native audits and published bytes. It does not rerun the upstream scanner or establish every unconsumed manifest field's authenticity.

FIT defaults to a 3600-second timeout (`timeout` accepts 1..86400). The adapter cleans its owned R process tree on timeout/interruption, preserves the failure capture and publishes no analysis result. Recovery tests observe actual live R PIDs and recheck identity by PID and creation time after cleanup. Other user processes are outside that ownership scope.

## Evidence and remaining work

The [validation record](signac-scan-development/VALIDATION.json), [controls archive](signac-scan-development/controls.zip) and [required actions](signac-scan-development/WARNINGS.md) retain original controls, source snapshots, invalidated and failed attempts, dependency skips and process recovery checks. The original fixture contains 96 protocols across four source profiles: 75 successes and 21 retained failures. Native reference/PFM/position and constructor audits pass 211 tests on each of three Java environments, including 26 constructor-audit checks against retained original slots and deliberate mutations. Ten additional original constructor cases retain truncation, zero-column and overflow behavior. Separate workflow, corruption, Unicode, exporter and literal-example evidence is recorded in the validation record.

Actual R execution here is Windows-only. Linux testing verifies native logic and completed captures, not a Linux upstream FIT. Per-cell chromVAR, paper-exact historical scanner/chromVAR execution, differential accessibility and the complete public Signac workflow remain open. This milestone is local development work, not a release, push or deployment. Literature review Pages remain withheld.

Original documentation: [AddMotifs](https://stuartlab.org/signac/reference/addmotifs), [FindMotifs](https://stuartlab.org/signac/reference/findmotifs), [chromVAR introduction](https://greenleaflab.github.io/chromVAR/articles/Introduction.html).
