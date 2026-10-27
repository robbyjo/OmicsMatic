# Original IGV views with native Java audits

The development command `igv-tools` renders local FASTA,BED6,bedGraph,BigWig,SEG and indexed BAM inputs with original IGV2.19.8/build14. The executable is `build/literature-review/igv-candidate-3/jlinalg-0.3.7.jar`,SHA-256 `38889b96d55b6f7ec8d8a4a66cad8168cf8908429a920386936928bce1f93a28`. The canonical executable and public release are unchanged.

**Read `WARNINGS.md` and its Required action first.** Exit3 skips unavailable,changed or forbidden dependencies;exit2 retains failed inputs,processes,timeouts or audits under `partial/`. Existing output directories and runtime homes are preserved. Failed publication retains a separately named staging directory and prints its location.

## Setup and use

Run from the repository root,where installation/downloads are permitted:

```powershell
python tools/literature/install_igv.py --home build/omics-igv-runtime-v2 --cache build/igv-archive-cache --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe" --allow-install --allow-download
java -jar build/literature-review/igv-candidate-3/jlinalg-0.3.7.jar igv-tools --recipe LOCAL_VIEWS.json --home build/omics-igv-runtime-v2 --out NEW_OUTPUT
python examples/igv-tools/run-workflow.py --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe" --jar build/literature-review/igv-candidate-3/jlinalg-0.3.7.jar --home build/omics-igv-runtime-v2 --out "build/IGV synthetic example"
```

Omit `--allow-download` when the exact official `IGV_2.19.8.zip` is already cached. Healthy admitted homes can be reused without either permission flag. Analysis never installs software. A full Java21 JDK,Windows x64 and usable Windows graphics/fonts are required. Runtime/JDK/report and temporary argument-carrier paths are ASCII;data/output paths can contain Unicode. Use an ASCII-named UTF8 JSON string-options file with `--options-file` for Unicode paths;keys are `recipe`,`home`,`out` and optional `timeout`,`max-bytes`,`max-records`. Direct flags override file values.

If installation or a dependency is unavailable,**skip the analysis** until an administrator supplies an admitted runtime. Preserve changed homes/caches and reports;never re-seal modified software. Correct the named cause and rerun to a new output directory.

## Explicit scientific choices

The recipe declares `assembly`,`reference`,`reference_index` (`generate` or `verify-supplied`),`width`,`height`,`sequence`,`views` and `tracks`. Views declare unique safe `id`,exact `contig`,and zero-based half-open `start`/`end`. Complete BigWig/BAM dictionaries must equal the FASTA names/lengths. Supplied FASTA indexes must match actual byte offsets and line widths. No aliases,liftover,reference download or assembly inference is performed.

Every track declares `id`,`kind`,`file`,`height`,`display` (`expanded`,`squished`,`collapsed`),and `color`/`negative_color` as RGB integer triples.

|Kind|Additional required choices|Meaning|
|---|---|---|
|`bed6`|None|Exactly six tab-delimited columns;score0..1000;explicit +,- or . strand.|
|`bedgraph`,`bigwig`|`units`,`range`,`window:"none"`|Raw finite float32 scalar values;missing remains missing.|
|`seg`|`units`,`range`,`coordinates`,`mode:"raw-supplied-units"`|Exact six-column header;explicit zero-based-half-open or one-based-closed source coordinates;range brackets0.|
|`bam`|`index`,`range:[0,MAX]`,root `reads`|Supplied index is staged adjacent to the BAM.|

SEG is staged as original type OTHER to preserve already supplied units. This bypasses the original copy-number log heuristic and automatic cohort-frequency track. Exact palette parameters are audited;the original251-step/raster gradient quantizes some colors. Automatic CNA cohort-frequency/custom synteny summaries remain unfinished.

BAM requires every `reads` field: `minimum_mapq`,`duplicates`,`failed`,`secondary`,`supplementary`,`soft_clips`,`pairs`,`junctions`,`deletions_in_coverage`,`color_by` (`none`,`strand`,`base`),`downsample:false`,`overlapping_mates:"count-each-alignment"`,and `base_quality_policy:"all-bases;quality-shading-only"`. Each alignment counts independently,including overlapping mates. All qualities count;softclips are excluded from coverage,N skips reference,D contributes to totals only under its explicit policy,and I counts events at the current reference cursor. Raw MM/ML/MN/SA/HP/PS tags are compared without methylation interpretation. These counts are not independent molecules,variant calls or inferred allele fractions.

Limits:1..32tracks/views;40..50million bases per view,or at most1million with BAM. Bytes,records and wall time are bounded;reduce tracks/windows when resources are insufficient. Text tracks are at most128MiB. The native raw BigWig reader has independent original references for all three section types,branching indexes and351,836intervals. Retained positive references cover compressed little-endian files;other encodings lack that positive reference family.

## Retained results and verification

Unmodified original PNGs have a companion `FIGURE_LEGEND.md` with units,coordinates,scales,filters and interpretation limits. The original ruler can print601bp for an actual0..600 half-open frame;use native audited coordinates. Original sessions use relative staged input paths and remain portable after publication. Reopened interactive sessions can apply different global preferences;the PNGs and execution ledger are the verified output.

`original-igv.json` records actual settings,frames,tracks,values and reads. `native-audit.json` independently checks complete view reference bases,BED identity/bounds/name/score/strand,exact float32 scalar/missing functions,SEG sample/palette/raw values,selected BAM identities/CIGAR/sequence/MAPQ/flags/raw tags and every per-base count column. Identical cache duplicates or adjacent subdivisions do not change the signal function;missing differs from measured0. Native and original full count TSVs are retained.

The runtime authenticates302 original files,two helper/license files,the bundled lock and selected Java executable/module/DLL/config files before/after execution. Original binaries and stock `igv.args` remain unchanged;launch exports target actual `jide.oss`. A hidden frame,private user/temp/preferences,disabled port/cloud provisioning and Java URL-handler guard produced zero attempted requests. Windows graphics/fonts/system ABI remain host inputs;this is not an OS sandbox claim. Original OAuth-provider/SLF4J startup diagnostics remain visible.

Validation:126tests/13suites,strict Java17 compilation,Javadoc/packaging,126standalone tests per Java21/25,and17actual packaged cases (12scientific workflows plus5failure/skip controls). The scientific checks cover7,860reference bases,41,600integer counts and94selected-read comparisons. Java25 Unicode parent execution launches the admitted Java21 worker. Installer/integrity controls cover forbidden installation/download,healthy reuse,corrupt caches,unmanaged/unlisted/changed homes and changed Java admission hashes;an allowed official download was also exercised. Read [validation details](igv-workflow-validation.json).

The18reviewed paper roles remain in [IGV_SOURCE_REVIEW.md](IGV_SOURCE_REVIEW.md). Bounded availability does not establish historical/full-paper equivalence,automatic diagnosis,variant calling,allele-specific inference or methylation interpretation. The overall implementation goal remains active.
