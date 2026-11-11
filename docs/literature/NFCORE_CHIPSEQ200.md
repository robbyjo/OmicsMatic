# Original ChIP-seq 2.0.0 workflow adapter

This development adapter executes all selected branches of the locked original nf-core/chipseq **2.0.0** source, using explicitly selected Nextflow **22.10.6**, the admitted private Apptainer **1.5.5-1** runtime and all **33 source-declared images**. Four required component checks cover the complete inputs/BAMs/FRiP, peaks/annotations, duplication/library complexity and original saved DESeq2 QC. The declared source profile passed a complete 215-task cold run and both final compiler builds' real resumes, each with 213 cached and two completed tasks. Require the frozen checkpoint's final validation status and successful result verification. The released executable and catalog are unchanged. The broader nf-core family and the 250-paper implementation goal remain open.

**Warnings and required actions must be read before using outputs.**

- **Exit 3 / SKIP3:** the affected analysis was skipped because an exact runtime, image, platform or permitted capability is unavailable. Install/download the named prerequisite separately only where permitted, or use an allowed environment. Analysis does not install software or change host namespace settings.
- **Exit 2 / FAIL2:** no adapter result is admitted. Read the latest attempt's `FAILURE.json`, `WARNINGS.md`, named `process.json`, `process.stderr` and original engine log/trace. Retain that attempt and original data. Resolve the cause; use new preparation/run paths for changed inputs, settings or capacities.
- **Source coordinates:** original zero-based BED Start is copied unchanged into SAF. The checks preserve that Start and inclusive featureCounts `Length=End-Start+1`. A coordinate conversion requires a separate declared analysis.
- **Actual provider:** the original Picard image reports **2.27.4-SNAPSHOT**, and BAM program records contain literal **`Version:2.27.4-SNAPSHOT`**. These are preserved. The image's `2.27.4` URI does not authenticate an official release binary. The actual provider's five numerical methods match the earlier original reference bytecode; 20 numerical cases, all applicable ROI bins and complete marked-BAM metrics were checked.
- **Actual QC:** original R **4.0.3** / DESeq2 **1.28.0** uses design **`~1`**, default VST and all genes for the final PCA table. It does not perform the 1.2.1 workflow's differential Wald analysis. Its original MultiQC heatmap caption still says “rlog”; consult the actual saved-assay metadata. Original locale startup warnings remain visible, with no package or locale replacement during analysis.
- **Source naming:** original Preseq filenames contain `.mLb.clN` although the source supplies the marked BAM. Every one of the 10,000 default curve rows per library is checked. No input stage is inferred from that filename, and no shorter or repaired curve is substituted.
- **Assessability:** use the source group/replicate convention, for example `CONDITION_R1`. The guard refuses an unassessable final underscore-delimited replicate token, empty called-peak populations or unassessable required QC instead of repairing IDs, manufacturing peaks or reporting a negative biological finding.

Execution needs x86_64 Linux Java 17 or later, including Linux Java inside WSL. Complete saved results are intended to support verification on Windows and Linux without rerunning science or installing scientific runtimes. Software controls do not certify biological accuracy, independently reimplement every upstream model, reproduce a paper or establish unreported author settings. Cell Ranger remains excluded.

The executable takes its options directly, without a central `omicsmatic` route. Build the development artifact from the repository root on the configured Windows development environment:

```powershell
python tools/literature/verify_nfcore_chipseq200_checkpoint.py
java -jar docs/literature/nfcore-chipseq200-development/nfcore-chipseq200-javac21.jar --help

# Rebuild only when the validated development predecessors are available locally:
python tools/literature/build_nfcore_chipseq200_workflow.py --compiler 21 --out build/literature-review/nfcore-runtime/chipseq200-workflow-build21-NEW
java -jar build/literature-review/nfcore-runtime/chipseq200-workflow-build21-NEW/nfcore-chipseq200.jar --help
```

The builder requires the unchanged validated predecessor artifacts named in its source and refuses existing output paths. This is a development build chain, separate from the released executable. A frozen, independently verifiable checkpoint is required for distribution; do not infer a release from a successful compilation.

Prepare the exact prerequisites separately. `--export-runtime-setup NEW_DIRECTORY` exports the managed Nextflow setup kit; select `linux-x86_64-nextflow-22.10.6` and retain its completed current-host report. Repeat separate health checks after a host/kernel change. See the [foundation guide](NFCORE_FOUNDATION.md) and [private runtime description](NFCORE_DESEQ126.md). The private runtime must match the complete 350-entry inventory in `src/main/resources/org/jlinalg/omics/nfcore-deseq126/runtime-inventory.json`. The source-image profile `src/main/resources/org/jlinalg/omics/nfcore-chipseq200/images.json` gives all exact original URIs and observed-byte pins. Supply their local paths in a separate declaration:

```json
{"schema_version":1,"images":{"EXACT_SOURCE_URI_1":"/absolute/image-1.sif","EXACT_SOURCE_URI_2":"/absolute/image-2.sif"}}
```

Include all 33 entries; the abbreviated example is not a complete declaration. The images total 4,296,798,456 bytes. Source URI/hash checks establish byte integrity, not author history. An arbitrary runtime rebuild, retagged image or floating Conda environment is not interchangeable. If permitted exact prerequisites cannot be supplied, dependent execution is skipped. Native saved-fact replays need no container installation.

The separate Linux image helper downloads the exact original source URLs and verifies every complete image before writing a declaration. Enable downloading only where permitted:

```bash
python3 tools/literature/setup_nfcore_chipseq200_images.py \
  --allow-download true --out /absolute/new-original-images
```

Use `/absolute/new-original-images/image-set.json` in preparation. An existing explicit declaration can supply a cache with `--from-cache /absolute/existing-image-set.json`; cached originals are copied and checked before/after setup. Downloading defaults to disabled. A missing required image then produces SKIP3 and no ready declaration. Partial downloads and failed directories remain available for diagnosis. Setup installs no runtime and executes no scientific analysis. Its control copies 32 complete original images and downloads the remaining original BedTools image over HTTPS; all 33 final pins match. The no-cache/download-disabled control skips as expected.

Obtain the original source with executable permissions intact. All 203 files must match `nfcore-source-lock.json`, profile `chipseq-2.0.0`, selected source commit `51eba00b32885c4d0bec60db3cb0a45eb61e34c5`. P032 names pipeline 2.0.0 and leaves its engine edition unreported; the explicit engine selection here must not be attributed to the authors. Its separate human/mouse alignments and downstream spike-in normalization are distinct paper roles, not a claim that this software control reproduces the experiment.

The original samplesheet differs from 1.2.1:

```csv
sample,fastq_1,fastq_2,antibody,control
T0_R1,reads/T0_R1_1.fastq.gz,reads/T0_R1_2.fastq.gz,MARK,INPUT_R1
INPUT_R1,reads/INPUT_R1_1.fastq.gz,reads/INPUT_R1_2.fastq.gz,,
```

Supply the complete experiment and appropriate controls; these rows illustrate the schema only. Source sample IDs already identify biological libraries. The source adds `_T1`, `_T2`, etc. for technical runs, uses those technical IDs for BAM `ID`/`LB` and retains the biological ID in `SM`. Do not carry over the 1.2.1 library-tag convention. Every source row and technical run is checked. Source normalizations, ignored extra cells and raw values are available through `NfcoreChipseq200Mappings`; the workflow requires literal scientific identifiers and consistent antibody/control metadata across a library's technical runs. It refuses a source change to an identifier rather than renaming it. Original mates must refer to distinct physical files.

The helper below requires the canonical five-column unquoted samplesheet. It retains original bytes and writes a separate derivative changing only read-path columns. Relative read paths resolve against `--data-root`. All selected original FASTQs, samplesheet, FASTA, GTF and optional resources must lie under that explicit root. It copies complete files without selecting reads or reducing the experiment. Preparation and run directories must be new and disjoint, with existing canonical parents. Use ASCII run paths without whitespace, commas or colons.

On Linux, replace these absolute example paths and resource/scientific values with your actual selections:

```bash
python3 tools/literature/prepare_nfcore_chipseq200_workflow.py \
  --workflow /absolute/source/chipseq-2.0.0 \
  --data-root /absolute/experiment \
  --design /absolute/experiment/samplesheet.csv \
  --fasta /absolute/experiment/reference/genome.fa \
  --gtf /absolute/experiment/reference/genes.gtf \
  --macs-genome-size 1.2e7 \
  --container-home /absolute/admitted-apptainer-home \
  --image-set /absolute/complete-image-set.json \
  --out /absolute/new-preparation --run-root /absolute/new-run \
  --max-cpus 4 --executor-memory-bytes 25769803776 \
  --max-memory 8.GB --max-time 2.h

java -Xmx2g -jar /absolute/nfcore-chipseq200.jar \
  --request /absolute/new-preparation/request.json \
  --home /absolute/exact-nextflow22-home \
  --setup-report /absolute/current-completed-host-report \
  --out /absolute/new-run

java -Xmx2g -jar /absolute/nfcore-chipseq200.jar --verify /absolute/new-run
```

Preparation alone admits no analysis. The native request guard verifies original source/schema, bound paths, complete read capture, physical mate identity, primary config order, declared source branches, runtime-only executor/image overlay and typed parameter consistency. Each actual task must use its original module's exact image and required namespace options. No failed, ignored, aborted or unfinished task is admitted.

Additional original parameters use `--parameters /absolute/parameters.json`. Unknown/duplicate keys, wrong types, invalid source enums and schema bounds fail. Explicit optional inputs use `--resource blacklist=/absolute/experiment/blacklist.bed` and `{"blacklist":{"$input":"blacklist"}}` in the parameter file. Directory members use `{"$input":"resource_name","path":"relative/member"}`. Literal external input/output paths are refused. The local-reference profile does not admit iGenomes resolution, institutional overrides, notifications, floating Conda or alternative publication modes. Required branches cannot be skipped to hide failures. Original scientific defaults remain unchanged unless an explicit supported parameter selects otherwise; the complete execution control uses paired reads, BWA, broad peaks and the original defaults.

`--limits /absolute/limits.json` records explicit execution-capacity overrides. Defaults include four-hour wall/per-process CPU limits, 64 GiB address space per process, 256 GiB owned workspace, 512 processes, 16,384 threads, 32 GiB sampled aggregate RSS, 2 GiB child Java heap and 64 MiB engine metadata. Sampled RSS is not a hard kernel aggregate memory limit. Fixed native auditors also have complete-table/record capacities. Insufficient capacity fails; no smaller matrix, shorter curve or subset is admitted.

`--resume /absolute/original-run --home ... --setup-report ...` accepts only a known terminal attempt and reuses its immutable captured request, input population and engine cache. It does not recapture changed producers. An indeterminate `RUNNING` attempt needs inspection of its actual owned-process evidence before any recovery. Changed input/settings/capacities, or a failure before an engine session was initialized, require new paths.

Each completed `results/NNNNNN` contains a separate portable result: complete original engine attempt/objects, four component reports, all saved QC objects and exported cells, runtime and owned-process receipts, original request/recipe/image declaration, exact adapter JAR and warnings/manifests. Copy the entire directory and run `--verify` against it. Verification checks all members and source/task/component bindings, required process cleanup and complete native QC geometry. A failed latest run never falls back to an earlier result; an explicitly selected retained completed result remains independently verifiable. Hashes are not execution signatures.

Consensus/annotation checks preserve C/Unicode source ordering, bookended merging before group filtering, source gene-ID lookup using its last transcript row, original missing values and compatible nearest-TSS ties. Original feature classifications and PDF rendering remain upstream. Duplication checks cover complete per-technical-library counters, fractions, estimated-size absence/presence, duplicate-set histograms and every applicable ROI bin; optical clustering, duplicate marking and Preseq extrapolation remain upstream.

The saved-QC worker verifies complete DDS/RDS/size-factor object agreement and exports all original counts, normalized counts and stored VST/rlog values without refitting. Native Java checks every source ratio factor and normalized cell, all pairwise Euclidean distances and the full PCA basis against the centered all-gene Gram matrix. Global signs and valid rotations within equal eigenspaces are allowed. It also checks published PC1/PC2 variance labels, sample populations, full distance tables, seven-significant-digit size-factor text and exact original MultiQC header/body composition. FeatureCounts assignments and nonlinear transformations are not independently recomputed. Source-ineligible QC groups are explicitly recorded without a negative biological finding.

ChIP-seq 1.2.1 has its [separate validated profile](NFCORE_CHIPSEQ121.md). eager, rnasplice, Hi-C and unversioned paper roles remain required work. Full methods-use review of the 250-paper catalog is still open.
