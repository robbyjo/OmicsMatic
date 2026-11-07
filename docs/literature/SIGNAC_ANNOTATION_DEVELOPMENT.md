# Native Signac TSS enrichment and gene activity

This local development candidate adds native Java legacy TSS enrichment in fast and position-profile modes, annotation-derived TSS positions and gene-activity fragment matrices. It extends the validated [fragment workflow](SIGNAC_FRAGMENTS_DEVELOPMENT.md) and [LSI workflow](SIGNAC_LSI_DEVELOPMENT.md). **Full Signac integration remains incomplete and receives no catalog credit yet.** Motifs/chromVAR, peak links, differential accessibility and their paper-specific workflows still need implementation and validation.

**WARNING — Fast and profile TSS modes can produce different scores in the original package.** They use different center windows, endpoint offsets and populations. This candidate reproduces those legacy contracts. Do not substitute one mode, a generic interval count or a Tn5 shift when reproducing a paper. The installed original Signac 1.17.1 marks TSSEnrichment deprecated in favor of ATACqc; this candidate does not implement ATACqc.

**FAIL2 — Source failures and invalid analysis are retained, not repaired silently.** Missing-chromosome-only fast chunks, certain trimmed profile windows, single-row profiles and zero-background profile normalization can fail in the original. Read the named error, `WARNINGS.md` and status. Preserve the attempt, review coordinates, chromosome names, mode, `process_n` and population, then choose a scientifically justified correction and a **new output directory**. Changing modes or dropping TSSs merely to obtain a result is not an automatic recovery. Existing outputs are refused, and partial files or failed verification are not admitted results.

**No R installation is required for these native analyses.** Optional downstream LSI still requires its exact R runtime. Missing R, packages/libraries or working native dependencies produce **SKIP3** there. Provide/install the named runtime only where permitted; otherwise retain the skip and skip dependent LSI. The native request workflows perform no downloads or installation.

## Start with the checked examples

Run from the repository root. See [examples/signac-annotations](../../examples/signac-annotations/README.md) for all four native requests, exact input files, verification and the optional gene-to-LSI step.

```powershell
$annotationJar = 'build/literature-review/signac-runtime/annotation-workflow-build-1/omicsmatic-0.3.7-signac-annotation-workflow-development.jar'
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/fast-request.json
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/gene-request.json
java -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/verify-request.json
```

The candidate is **164547699 bytes**, SHA-256 `e3586d1e30a82f7168225fbed54ccea9daae62520f9f6e773d3a8c7258f8de64`. It preserves every validated parent entry and adds the native APIs plus a standalone request CLI. It does not replace the canonical release JAR or add a root command. Compilation targets Java 17; execution is tested on Windows Java 21, Windows Java 25 and Linux Java 21.

Keep the request filename/path ASCII on the tested Windows launchers. Put exact Unicode input/output paths and IDs inside UTF-8 JSON and tables. Relative paths resolve against the request's directory. Use a `VERIFY` JSON request for Unicode output paths too. Select a new `out` for every rerun and update downstream input/verification paths; preserve the previous result.

## Input files and minimal requests

Fragments use the [native fragment format and admission rules](SIGNAC_FRAGMENTS_DEVELOPMENT.md): sorted five-column BED-style records, optionally a consistent strand column, in plain/gzip/BGZF files. Native operations do not require Tabix indexes. Complete scans validate compression integrity. Fragment PCR/read-support counts do not multiply endpoint or gene counts.

TSS positions are one-based closed ranges with this exact tab-separated header:

```text
chromosome	start	end	strand	sequence_length
chr1	3000	3000	+	.
chr2	4000	4000	-	.
```

The code block shows tabs as column spacing in the checked TSV. Use actual tab characters when creating a file. Strand is `+`, `-` or `*`. A dot in `sequence_length` means unknown. Otherwise supply a positive linear chromosome length. All positions on the same chromosome must agree on its known or unknown length. Circular chromosomes are outside this contract. Initial coordinates and known lengths must be within the supported 2^29 domain.

Gene annotations are flattened one-based closed TSV rows with these seven columns:

```text
chromosome	start	end	strand	gene_id	gene_name	gene_biotype
chr1	2800	3100	+	ENSG-example-1	EXAMPLE	protein_coding
```

Preserve every relevant annotation row and its order; metadata order affects the original union. Blank gene names are permitted and fall back to gene IDs for gene activity. The native intake consumes these columns directly; it does not download annotations, parse a GTF/EnsDb or infer assembly compatibility. Export the actual annotation used by the study and retain its provenance.

`object_cells_file` contains one object cell ID per line, without a header. A source map TSV has header `cell<TAB>barcode`: the first column is the object ID, the second the barcode stored in that fragment file. Maps must be one-to-one within each file. Use explicit sample-specific mappings when samples reuse barcodes.

```json
{
  "schema_version": 1,
  "mode": "TSS_FAST",
  "sources": [{"path": "fragments.tsv.gz", "map_file": "cell-map.tsv"}],
  "object_cells_file": "cells.tsv",
  "positions": "tss-positions.tsv",
  "genome_build": "GRCh38-declared-by-investigator",
  "process_n": 2000,
  "region_extension": 1000,
  "out": "tss-run-NEW"
}
```

`genome_build` is required provenance, not certification. Confirm matching chromosome names, assembly, fragment processing and annotation versions before interpreting results.

For annotation-derived TSSs replace `positions` with `annotation`. The default source GetTSSPositions path filters protein-coding annotation rows **before** union and chooses the strand-aware start site. For gene activity, use `mode: "GENE_ACTIVITY"` and `annotation`. This default uses 2000 bases upstream, zero downstream, protein-coding genes and strict width below 500000 bases.

## Request reference

Every analysis requires `schema_version: 1`, `mode`, `sources`, `genome_build`, `out` and exactly one of `object_cells` (array) or `object_cells_file`. A source requires `path` and may have either `map_file` or an inline `map` array of `{"cell":"sample-A","barcode":"A"}` entries. Omitted maps use the relevant original identity/discovery behavior. Unknown fields are rejected.

| Mode | Additional inputs | Options |
| --- | --- | --- |
| `TSS_FAST` | Exactly one of `positions` or `annotation` | `process_n: 2000`, `region_extension: 1000`, optional positive `first_n`; optional legacy `cells` array |
| `TSS_PROFILE` | Exactly one of `positions` or `annotation` | Same TSS settings; `profile_values: 20000000` allocation bound |
| `GENE_ACTIVITY` | `annotation` | `upstream: 2000`, `downstream: 0`, `biotypes: ["protein_coding"]`, `max_width: 500000`, `gene_ids: false`, `process_n: 2000`, `sort_records: 100000`; optional `features` or `features_file` |
| `VERIFY` | `analysis`: captured directory | Only `schema_version`, `mode` and `analysis` |

Use `biotypes: null` to retain all biotypes, and `max_width: null` to disable that filter. An explicitly empty feature list fails, matching the source. Feature order follows annotation order; the requested list is a membership filter. When `gene_ids` is true, feature membership uses gene IDs; otherwise it uses source-uniquified gene names. The width filter is **strictly less than** `max_width`, applied before promoter extension.

`first_n` selects the first n positions, capped at the number supplied, **before** mitochondrial exclusion. The native valid-input contract requires positive n; omitted/null means all. `chrM`, `Mt` and `MT` are then excluded. `*` strand is treated as plus and warned. The legacy `cells` argument is ignored by these source paths and emits `SOURCE_CELLS_ARGUMENT_IGNORED`; use an explicit, scientifically justified object universe when a subset is intended.

Native promoter extensions must be nonnegative integers up to one million. Region extension is bounded from zero to one million, with source profile failures retained for unsuitable small values. Profile allocation may be explicitly bounded from one to 100 million values. JSON files are at most one million bytes; use files for larger cell/mapping lists.

## TSS endpoint and population behavior

The original SingleFileCutMatrix first queries whole-fragment overlap, then computes endpoint positions as `BED_endpoint - (extended_start + 1)`. It retains positions strictly above zero and at most the **first retained region's width** in that call. Both fragment endpoints can contribute; read-support weights and fragment strand do not change the count. No +4/-5 Tn5 shift is applied in this legacy path.

Source midpoint extension is `start + width/2`, with fractional endpoints cast toward zero. Unknown chromosome lengths leave negative extended coordinates in the source declaration. Native physical queries clip at the chromosome origin while retaining those original offsets. Known linear lengths trim ranges, including zero-width ranges outside a chromosome. These cases can change the width used for other regions in a source call. Do not replace this with uniform inclusive-window arithmetic.

Fast mode builds fixed center windows and two flank sets, chunks them with the source algorithm, counts endpoints and divides flank counts by 200. Zero per-cell backgrounds use the population background mean. Source NaN center normalization becomes zero; positive center signal over a zero whole-population background remains infinity. `NONFINITE_TSS_SCORE` requires reviewing coverage and mappings; infinity or an imputed undefined zero is not a passing biological QC decision. The score divides normalized center counts by 1001.

A fast chunk containing only absent-chromosome rows can fail when retained sequence levels still overlap the file. Review the chromosome inventory and `process_n`; a justified single chunk can avoid that particular source failure if resources permit. Do not silently skip the failed chunk. An entirely absent-chromosome set can instead produce zero scores in fast mode, as observed in the original controls.

Profile mode retains the source's full position matrix, reverses the minus-strand columns and combines strand matrices by row position. Rows follow fragment-file/map order. Chromosome pruning persists across files in the original helper, so later files can lose chromosomes already removed by an earlier one. Duplicate cell names attach the **first** matching profile score to object metadata; they are not pooled. Fallback backgrounds use profile rows, while percentiles are computed on attached object metadata. Missing attached cells remain `NA`.

Source profile normalization rejects certain zero-background dense Matrix results. Single-row slicing can drop matrix dimensions before rowMeans, and trimmed windows can make flank/center indices out of bounds. Native code retains these failures. Profile-mode center averaging differs from fast mode, including source offsets and the extension-500 through extension+500 column selection. Review file order, mappings and mode rather than repairing these differences implicitly.

Both modes use the inherited R-compatible ECDF percentile rounding. Counts, missing/nonfinite classes and percentile values match the controls exactly; finite scores and profile values are compared with absolute/relative tolerance 2e-12. Native tables replay exactly across the tested JVMs.

## Gene union and row naming

The source helper named CollapseToLongestTranscript unions **all rows for each gene ID** from the minimum start to maximum end. It keeps the first strand, biotype and name, converts `*` to plus, and emits each encountered chromosome if one gene ID spans several chromosomes. It does not select a longest isoform. GeneActivity filters biotypes **after** this union; annotation-derived TSSs filter coding rows **before** it. Those operations can therefore select different ranges for mixed annotations.

Source `make.unique` reserves names already present before generating suffixes. Missing/blank activity names use gene IDs. With `gene_ids: true`, IDs replace the uniquified names before feature filtering. Original sequence-length metadata is lost during union, so this candidate does not invent upper-bound trimming from a reference that the source discarded.

Gene bodies extend upstream/downstream according to strand and use whole-fragment FeatureMatrix counts, one per overlapping line. Physical queries below the chromosome origin are clipped and warned; original annotation ranges and requested extension remain recorded. The result is an accessibility-derived proxy, **not measured gene expression**.

If different selected genes have the same extended coordinate key, original row naming selects the first gene name for every alias. Native output retains the duplicated rows/names with `IDENTICAL_COORDINATES_USE_FIRST_GENE_NAME`. Do not silently assign those counts to a different gene. Downstream native normalization/LSI intake requires unique feature IDs; ambiguous output must be reviewed and explicitly transformed with provenance, or that downstream analysis must remain skipped/failed.

## Outputs and verification

TSS modes write `tss.tsv` with explicit score/percentile kinds and `selected-tss.tsv`. Profile mode also writes `profile.tsv`: row index, cell label, source position label, raw count, normalized value and number kind. The extra `expected` and `motif` rows have blank raw counts. The explicit index preserves repeated cell labels and source position labels, which need not be centered as a conventional symmetric axis would be.

Gene activity writes Matrix Market integer `matrix.mtx`, `features.tsv`, `cells.tsv` and `selected-genes.tsv`. The selected-gene table records the union before promoter extension; requested extensions are in the captured JSON. Gene names in the matrix can repeat as described above.

Every bundle includes original/canonical requests, copied inputs, `manifest.json`, `analysis-status.json`, `operation.json`, `WARNINGS.md` and `artifact-checksums.tsv`. TSS uses a complete chromosome-survey pass and a complete endpoint/position pass per file; `operation.json` records both. Gene quantification reports source indices for the files actually used. Disk space must accommodate captured inputs, sorting and potentially large profile tables.

Verification checks the entire inventory, symlinks, request/manifest bindings and completion status, then recomputes result tables, selected ranges, metadata, warnings and required actions from captured inputs. It needs no original input locations or R for native modes. Temporary sorting belongs to the verifier and is removed. Hashes provide integrity, not a signature or proof against coordinated replacement of the entire bundle.

Default fragment bounds carry over from the [fragment guide](SIGNAC_FRAGMENTS_DEVELOPMENT.md). Annotation/position/map TSVs are limited to two million rows and one GB, with bounded line lengths. Full-profile arrays have a separate allocation bound; a 512 MB heap is not a claim that every maximum-size request fits. Use sufficient resources, reduce the explicit profile bound, or choose a scientifically justified analysis plan. The workflow does not silently switch to fast mode or partition away evidence when capacity fails.

## Evidence and remaining work

The [validation record](signac-annotation-development/VALIDATION.json), [evidence archive](signac-annotation-development/controls.zip) and [warnings](signac-annotation-development/WARNINGS.md) retain exact sources, original outputs, failed/superseded attempts, test and process logs, and checksums.

The full installed original Signac 1.17.1 produced 50 TSS and 26 gene-activity controls, including normalized/raw profiles, annotation reduction and source failures. Native APIs passed **357 tests per JVM** on Windows Java 21, Windows Java 25 and Linux Java 21, with plain/gzip/BGZF inputs, forced sorting, extension boundaries, metadata attachment, aliases, cancellation and capacity gates. Historical package binaries were not executed.

The final workflow matrix passed 22 original-result comparisons, 66 captured verifications, six additional fresh native runs on Windows Java 25/Linux Java 21, 30 rehashed corruption rejections and three unlisted nested-file rejections. Two Unicode identity runs add six verifications. Four exact checked native examples match four **fresh full-package original calculations**, including annotation-derived TSS; their captures and the optional LSI example pass 15 verifications. The workflow and checked examples each complete one native-gene-matrix-to-pinned-R-LSI fit.

The catalog remains **68 new integrations, 71 including existing tools, 259 missing**. Signac stays missing until its remaining paper roles and integration gates are satisfied. Complete DLL/host closure and biological QC validity are not certified. The canonical JAR and prior evidence are preserved; no release, push or deployment occurred. Official references: [TSSEnrichment](https://stuartlab.org/signac/reference/tssenrichment) and [GeneActivity](https://stuartlab.org/signac/reference/geneactivity), interpreted alongside retained implementation sources and original executions.
