# Native Signac coverage and annotation development

`org.jlinalg.cli.SignacCoverageCli` creates accessibility tracks and gene/transcript models from explicit fragment populations, assay-wide cell counts and exon annotations. Native Java implements the retained source calculations and produces complete data tables, deterministic SVG and a Java2D PNG preview. Start with the [copyable examples](../../examples/signac-coverage/README.md), including a chain from the native peak/feature-assay workflow.

The catalog's P225 paper (doi `10.1016/j.ajhg.2024.07.002`, PMCID `PMC11339625`) explicitly uses Signac 1.9.0 accessibility tracks, gene annotations and CoveragePlot. This milestone addresses those plotting roles. **Signac remains missing pending its complete role review and public integration.** Counts remain 70 new scoped integrations, 73 including existing adapters, 258 missing named tools. Cell Ranger remains excluded. No release, push, deployment or whole-goal completion is claimed.

## Warnings and recovery

| Condition | Required action |
| --- | --- |
| **FAIL2 / exit 2** | No result is admitted after invalid input, ambiguous identity, source geometry failure, exhausted capacity, empty/nonfinite/zero plot limits or failed capture verification. Preserve partial files, read the concrete `Cause:` and warning path, correct the named cause and retry into **NEW** outputs. Do not interpret an unplottable track as negative accessibility. |
| **SKIP3 / exit 3** | The full Java desktop/font/PNG runtime needed for plotting is unavailable or forbidden. Skip dependent plotting. Where allowed, provide a compatible full JDK and retry into new paths. Otherwise keep it skipped. Analysis performs no installation and launches no R process. |
| Source scaling | Provide assay-wide `nCount` values for every declared object cell, including cells with no local reads. The original `table(useNA='always')` adds a zero NA slot, which remains in the group factors and default median. Unused factor levels preserve original positional recycling; they can change a group's apparent factor. Read the full scaling table before interpretation. |
| Source cut positions | The retained `SingleFileCutMatrix` subtracts `region.start + 1` from raw BED endpoints, keeps columns 1–width and labels them region.start–end. That source offset is preserved. No new Tn5 shift or coordinate repair occurs. Support values do not multiply records. |
| File-order chromosome pruning | If an earlier source lacks the requested chromosome, original CutMatrix empties the region for that source and all subsequent sources. Review source order and warnings; do not silently restore the region or infer zero coverage. If no matrix remains, analysis fails. |
| Source smoothing/sampling | Smoothing is a centered rolling **sum**. Edge NA positions are dropped. Source seed 1234 resets per assay; native R 4.6.1 Mersenne-Twister/Rejection sampling reproduces ordered draws. Full group counts remain separate from sampled plot positions. Zero sampling admits no figure. |
| `ymax: "qXX"` | The retained code multiplies the two-significant-digit **sampled maximum** by XX/100. It does not compute an empirical quantile. The original empty maximum is −Inf, while ggplot's axis object transforms its displayed limit to +Inf; neither is repaired into a usable native figure. |
| Annotation identity | Gene mode groups by literal gene_name; transcript mode groups by literal tx_id. Whole selected models are retained before source body clipping. First-exon metadata, lane assignment, strand and exon/arrow positions follow the source controls. Missing exon bodies or invalid clipped ranges fail. Mixed chromosome/strand identities are warned about, not repaired. |
| Rendering/VERIFY | The native plot style is independent of ggplot pixel rendering. VERIFY recomputes native arithmetic, annotation layout and exact SVG bytes. PNG format/dimensions/hashes are checked, but pixels are not independently rerendered across platform fonts. Neither consistency nor hashes authenticate execution, biological meaning or a paper. |

Read `WARNINGS.md`, `analysis-status.json`, `diagnostics.json`, `group-scaling.tsv`, `group-cuts.tsv`, `coverage.tsv` and every annotation table. Require exit 0, an admitted status, completion marker and successful VERIFY. A retained file alone is insufficient.

**PNG limitation demonstrated:** a TEST-ONLY blank white PNG with unchanged dimensions and rebuilt hashes is accepted on all three JVMs because raster pixels are not independently redrawn. It is not a scientific figure. Use the replayed SVG for audited figure geometry, and retain the numeric tables and original evidence. This distinction goes beyond platform font differences; PNG hashes do not authenticate its visual content.

## Source and dependency scope

Profiles are `Signac-1.9.0`, `Signac-1.14.0` and `Signac-1.17.1`. Original functions execute unchanged for reference collection in an explicit Signac 1.17.1 dependency namespace under R 4.6.1, RcppRoll 0.4.0, dplyr 1.2.1 and ggplot2 4.0.3. Full composed original coverage/annotation plots and underlying source calculations are retained. This is not execution of every historical package binary/environment. Old ggplot deprecation and R startup locale warnings remain retained; the collector explicitly verifies C collation for identity ordering.

Java's sampling port retains the exact official R 4.6.1 RNG.c/random.c sources and GPL notices. Signac sources and MIT attribution are packaged. Reference generators are developer evidence tools, not analysis dependencies or installation steps. Parent peak, fragment, annotation, Seurat and canonical executable artifacts remain unchanged.

## Closed request and input formats

Use UTF-8 JSON with `schema_version: 1`, `mode: "COVERAGE"`, `source_profile`, declared `genome_build`, `out`, `region`, `sources` and `cell_metadata`. Unknown fields are refused. Relative paths resolve against the JSON directory. Outputs must be new. VERIFY accepts only schema_version, mode and analysis. The genome build is a declaration; it does not validate a FASTA or reconcile assemblies/chromosome names.

`region` contains chromosome, start and end in **one-based closed** coordinates. Width is bounded to 2 million bases. Fragment rows remain sorted zero-based half-open BED, with five fields or a consistent sixth strand field, in plain/gzip/BGZF files. Complete scans check shape, sorting and compression integrity. There is no prefix sampling or read-support weighting.

`sources` is an ordered list of 1–256 objects containing `fragments` and optional `map`. A map is a literal UTF-8 TSV headed `cell<TAB>barcode`, mapping object IDs to raw barcodes. Omitting it explicitly selects identity mapping. Maps must be one-to-one within a source. Cross-source contributions pool matching object identities; sample namespaces and QC filters are not inferred.

`cell_metadata` is a literal TSV headed `cell<TAB>group<TAB>n_count`. It declares a nonempty unique complete object population and finite nonnegative assay-wide counts. Missing group IDs are refused; literal strings remain strings. Do not replace these values by regional totals, raw fragment counts or support sums. Optional `group_levels` declares ordered factor levels, including intentional unused levels. Otherwise groups use the source character-order profile. Sampling order and displayed factor order remain separate.

Optional `annotation` is a literal TSV headed `chromosome<TAB>start<TAB>end<TAB>strand<TAB>gene_name<TAB>tx_id<TAB>gene_biotype<TAB>type`. Coordinates are one-based closed; type `exon` supplies model segments. `annotation_mode` defaults to gene when a file is supplied, otherwise none; transcript is also supported. Unused/missing annotation settings are refused. Native output labels retain literal identifiers; they are XML-escaped, not silently repaired. Unsupported XML controls are refused.

## Options, capacities and outputs

| Setting | Default / contract |
| --- | --- |
| window | 100; positive centered rolling-sum window. JSON null disables smoothing. |
| downsample_rate / maximum_downsample | 0.1 / 3000; source sample size is trunc(min(cap, region_width × rate)), bounded by available positions per group. |
| scale_factor | Omitted means source median of the complete recycled group-factor vector. Explicit finite nonnegative values are recorded. |
| ymax | Omitted means two-significant-digit sampled maximum; finite nonnegative numeric or qXX (0–100) is accepted. Unusable plot limits fail. |
| title / plot_width | Chromatin accessibility / 1200 pixels; width 600–4000. |
| maximum_plot_height | 20000 pixels. All groups/models must fit; no silent dropping occurs. |
| maximum_input_bytes / maximum_output_bytes | 1 GB / 2 GB. Output capacity covers the complete capture, including captured inputs/sources, tables and images. |
| maximum_signal_values / maximum_smoothing_operations | 20 million / 250 million. Complete dense group-position signals and exact rolling-sum work must fit declared bounds. |
| maximum_cells / maximum_groups | 2 million / 128. |
| maximum_annotations / maximum_segments | 100000 / 2 million. |
| maximum_fragment_records / maximum_decoded_bytes | 1 billion records / 250 GB decoded per source. |
| maximum_contributions | 250 million retained endpoint contributions per source. |

All limits are explicit and bounded. Use a suitable Java heap; memory/cancellation/capacity failures admit no partial result. The workflow captures original/captured/effective requests, exact input bytes/hashes, original source/license profiles, native diagnostics/warnings, transaction manifests and an inventory seal.

`group-cuts.tsv` contains complete integer group-by-position cuts. `group-scaling.tsv` includes the extra NA slot with an explicit boolean flag, keeping it distinct from a literal group name. `coverage.tsv` retains sampled source order, raw cuts, normalization and clipped smoothed coverage. Annotation labels/exons/plus/minus tables retain full source geometry. `coverage.svg` uses deterministic replayable geometry; `coverage.png` is a platform-font preview. SVG/PNG styling is not a ggplot pixel replica.

This scope is single assay with accessibility and optional gene/transcript panels. Expression, peaks, links, BigWig, tiles, multi-assay overlays, interactive browsing and other Signac functions are not inferred. Full catalog admission requires the remaining package-wide role review and public integration gates.
