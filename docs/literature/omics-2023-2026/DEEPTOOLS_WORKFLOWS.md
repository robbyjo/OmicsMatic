# deepTools native methods and tested original workflows

**EIGHT ORIGINAL WORKFLOWS ARE VALIDATED THROUGH JAVA:** BAM coverage, BigWig
comparison, simple-M-CIGAR ATAC shifts, region matrices, profiles, heatmaps,
BAM summaries and correlations using original3.5.6. They passed58Java workflow
calls with20independent checks and11fixture/inspection utilities. All23prior
executable verifier groups passed on the same JAR. The full isolated gate passed
1,461tests with five optional skips; all55required suites ran without skips.

**ATAC SHIFT DROPS SEQ, QUAL AND ALL OPTIONAL SAM TAGS.** Preserve the original
BAM and acknowledge the loss. Model, missing/zero, constant-sample and figure
presentation warnings below require review before downstream use.

Native supplied-signal methods remain available without Python:4064actual
original arithmetic references and21native executable runs/10checks.
The canonical unreleased task artifact is
`build/literature-review/validated-cli/jlinalg-0.3.7.jar`, SHA-256
`5680cbc4e457007f8903d1d4db3e74ecc890a150b350870f0b82d5650bdc0fec`.
See [matrix/plot/summary validation](deeptools-matrix-validation.json),
[all original executable checks](deeptools-upstream-packaged-validation.json),
[export checks and limitations](deeptools-matrix-export-validation.json) and
[native validation](deeptools-native-validation.json).
Unexposed package options and historical/full-paper parity remain open.

## Original Java workflows

The eight original methods are `bam-coverage`, `bigwig-compare`, `atac-shift`,
`matrix`, `plot-profile`, `plot-heatmap`, `bam-summary` and `correlation`. The
separate17original-runtime probes are retained as earlier evidence; the current
Java commands have their own typed contracts and executable checks.

All three commands require an exact **ordered** chromosome-size dictionary
(`--sizes-format sizes` for two columns, or `fai` for five), a declared genome
build and a new output directory. A matching dictionary does not independently
prove the declared biological build. No coordinate conversion is inferred.
Inputs and adjacent BAI/CSI indexes are hashed before and after use. BAM audits
scan physical records, verify coordinate order, bounds and index mapped counts.
An existing adjacent index is required; input files are never indexed in place.
The default limits are one million nominal bins and ten million input records.

`bam-coverage` requires explicit normalization, exact-scaling and uncovered-bin
policies. It uses actual original read-overlap counting and emits bedGraph.
Without extension, aligned read blocks are counted. With `--extend-reads N`,
upstream paired-fragment handling also applies. RPGC requires an effective genome
size; its denominator uses the upstream median read length without extension,
or the declared extension. `--exact-scaling false` can estimate retained depth.
The adapter exposes MAPQ, inclusion/exclusion flags, duplicate and fragment-length
filters; blacklists, MNase, strand selection and smoothing remain separate work.

`bigwig-compare` reads actual binary BigWigs with float32 stored signal. All eight
operations are exposed, with explicit `--missing zero|retain|error` and
`--invalid error|retain`. `missing retain` requires `invalid retain`: original
fixed-step output keeps missing bins as NaN. `results.tsv` retains NA/status;
`signal.bedgraph` contains only finite rows; `original.bedgraph` retains the
original output verbatim. Its text uses six significant digits. Filtered bins
are absent from these tables and must not be interpreted as measured zeros.

**ORIGINAL SIGNED ZERO-FILTER BEHAVIOR:** with `--skip-zero-zero true`, original
3.5.6 checks whether the sum of the two unscaled bin means is zero. Nonzero
signed values can therefore cancel and be omitted. This differs from the native
helper's test that both values individually equal zero. The warning is retained
in every affected bundle. Scale factors precede ratio pseudocounts.

**ATAC SHIFT LOSES SEQUENCE, QUALITIES AND ALL OPTIONAL SAM TAGS.** The original
program creates replacement records, including a single M CIGAR, and drops RG,
NM, CB, RX and other optional tags. The adapter requires
`--accept-sequence-tag-loss true --cigar-policy match-only`; complex-CIGAR input
fails in this first subset. Preserve the original BAM for sequence, UMI/barcode,
read-group and variant analyses. Only proper pairs survive. Output is not
asserted sorted or indexed: sort and index the derived `shifted.bam` before
indexed downstream use. Review original filtering metrics and the separate
output-record audit. Short shifts can be clamped by upstream behavior.

Use the validated task artifact with a matching managed runtime:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method bam-coverage --input reads.bam --sizes reference.sizes --genome-build GRCh38 --bin-size 50 --normalization CPM --exact-scaling true --uncovered zero --runtime-home build/omics-deeptools-runtime-v2 --out output/coverage
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method bigwig-compare --input sample.bw --input2 control.bw --sizes reference.sizes --genome-build GRCh38 --bin-size 50 --operation log2 --pseudo1 1 --pseudo2 1 --missing retain --invalid retain --runtime-home build/omics-deeptools-runtime-v2 --out output/log2-bw
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method atac-shift --input paired.bam --sizes reference.sizes --genome-build GRCh38 --accept-sequence-tag-loss true --cigar-policy match-only --runtime-home build/omics-deeptools-runtime-v2 --out output/atac-shift
```

### Dependencies, errors and required actions

Analysis never installs software. Missing, changed or incompatible managed
dependencies skip with exit **3**, a prominent `WARNINGS.md` and exact recovery.
Input, scientific or execution failures return **2**, quarantine partial files
and publish no successful results. Exit **0** still requires reviewing warnings.
The Java boundary verifies the complete seal with four bounded hash readers,
checks 13 exact dependency versions and all 42 original source modules, and
requires explicit completion markers. Cygwin also requires one private Python
core DLL and its validated v390 font-cache template. An exit-0 process that
stopped early is a failure. `runtime-integrity.log` reports progress and timing.

On supported native Linux/macOS hosts, where downloads are allowed:

```powershell
python tools/literature/install_deeptools.py --environment build/omics-deeptools-runtime --allow-download
```

Add `--allow-source-build` only where compilation is permitted. Native Windows
stock-pip setup remains unavailable. The tested private Cygwin environment
already exists in this workspace; it can be reused without download/build
authorization. On another Windows host, an administrator must provide an
equivalent prepared profile, then seal it with
`--environment NEW_HOME --prepared-cygwin SPEC`. The preparation contract and
retained build limitations are described below; this is not an automated
fresh-machine Cygwin provisioner. If installation is forbidden, skip the
affected analysis until a matching managed profile is supplied. Preserve failed
attempts and use a new environment rather than modifying a sealed one in place.
The current execution evidence is Windows/Cygwin; native Linux/macOS execution
has not been certified by these Windows tests.

## Region matrices, profiles and heatmaps

The input sample manifest is an unquoted TSV with exact `sample` and `path`
columns. Paths can be absolute or relative to that manifest. IDs are stable
strings, including numeric-looking strings; duplicate IDs fail. For example:

```text
sample	path
signal	signal.bw
control	control.bw
```

`matrix` requires one explicitly labelled BED6 group. Region IDs must be unique;
coordinates are zero-based half-open, score is numeric0..1000 and strand is `+`
or `-`. Group directives/comments, GTF/BED12 and multiple groups are not exposed.
The entire ordered BigWig dictionary must match the declared reference. This
checks names/lengths/order, not the biological truth of an assembly label.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method matrix --samples tracks.tsv --sizes reference.sizes --genome-build GRCh38 --regions genes.bed --group-label genes --mode reference-point --reference-point TSS --bin-size 50 --upstream 2000 --downstream 2000 --missing retain --skip-zero-rows false --bin-average mean --runtime-home build/omics-deeptools-runtime-v2 --out output/tss-matrix
```

Reference points are TSS, TES or center. `--mode scale-regions` instead requires
`--body-length BASES`; omit `--reference-point` in that mode. Flanks and scaled
body must be exact bin-width multiples. `--nan-after-end true|false` is explicit
when supplied; the original default is false. Input region order and strand
geometry are audited. Short/omitted regions and missing values remain visible.

`--missing zero|retain|error` and `--skip-zero-rows true|false` are required.
`retain` preserves original NaN cells; `error` rejects them; zero filling is an
explicit choice. Out-of-reference flanks are not invented observations. Every
input region appears as kept or omitted-by-original in `region-status.tsv` and
producer `results.tsv`, even when it is absent from the numerical matrix.

`--bin-average mean|median|min|max|std|sum` reduces supplied BigWig bases within
each region bin. It differs from the later plot reduction across regions. In
particular, a sum depends on the number of represented bases and is not an
inferred alignment count. BigWig values retain their supplied units. Nothing
estimates normalization, methylation depth or library size from these tracks.

The bundle includes original `matrix.gz`, `values.tsv`, exported regions and a
`model.json` sealing sample IDs, region/reference files, grid parameters and
member hashes. Plotting accepts that model file, verifies its members and matrix
identities, and rejects changes. It does not require original BigWigs to remain
available. Arbitrary external matrix import is not part of this interface.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method plot-profile --model output/tss-matrix/model.json --average mean --format pdf --runtime-home build/omics-deeptools-runtime-v2 --out output/tss-profile
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method plot-heatmap --model output/tss-matrix/model.json --average mean --sort-regions keep --format svg --runtime-home build/omics-deeptools-runtime-v2 --out output/tss-heatmap
```

The required `--average` supports mean, median, min, max, std or sum. Original
profile data are exported and checked against the matrix reduction. Heatmap
order is keep, ascend or descend, with explicit sort statistic and region
export. Keep-order is checked exactly. PNG, PDF and SVG are exposed; profile and
heatmap DPI is40..300. Clustering, custom color limits and other package options
remain separate work. `deep-tools --help` lists the complete exposed choices.

**FIGURE PRESENTATION REQUIRES REVIEW.** Original tiny-window labels round in kb:
the20bp validation flanks can display as0.0Kb. Check exact model coordinates and
profile data, then annotate the exported axes accurately before publication.
SVG keeps DejaVuSans text references, so another viewer can substitute fonts;
embedded-font PDF avoids that specific portability issue. Very sparse scatter
data can yield tiny bins or edge points. Inspect numerical exports as well as
the image at its intended publication size. SE/STD across supplied regions is
descriptive variation, not biological-replicate uncertainty or a formal test.
Routine vector checks are structural; the executable validation separately
rendered three PDFs with Poppler and three SVGs with an offline browser and
visually reviewed all three plot types. These synthetic previews are test
fixtures, not proposed publication figures.

## BAM summaries and correlation

For BAM input, use the same sample/path manifest format and existing adjacent
BAI/CSI indexes. Each BAM undergoes a physical record/dictionary/index audit.
`--mode bins` requires `--bin-size`; `--distance-between-bins N` adds a gap after
each bin. `--mode regions --regions WINDOWS.bed6` instead counts supplied
windows. Overlapping regions can reuse reads. Counts are original alignment
overlaps with the chosen filters/extension, not inferred unique molecules,
fragments or library-normalized expression.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method bam-summary --samples bams.tsv --sizes reference.sizes --genome-build GRCh38 --mode bins --bin-size 10000 --runtime-home build/omics-deeptools-runtime-v2 --out output/bam-summary
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method correlation --model output/bam-summary/model.json --correlation-method spearman --what-to-plot heatmap --zero-rows omit --runtime-home build/omics-deeptools-runtime-v2 --out output/bam-correlation
```

Summary NPZ labels/counts must match the raw coordinate table exactly. NPY
headers and expanded sizes are inspected before loading; object/pickle arrays
are forbidden. The sealed model carries the reference and sample identities.
It can be used without retaining the original BAMs in the plotting location.

Correlation requires at least two nonconstant samples and two retained rows.
Choose Pearson or Spearman, heatmap or scatterplot, and `--zero-rows keep|omit`.
Shared empty bins can substantially change similarity: the synthetic example
changes from a positive whole-grid Spearman value to-0.5after omitting shared
zero rows. This is a scientific choice, not an automatic QC rule. Actual
four-decimal output coefficients are checked against independent centered dot
products or average tied ranks, with exact sample labels even if the heatmap
changes display order. No outlier removal or causal/sample-removal decision is
inferred. A constant sample fails with its name and recovery instructions.

Default `--max-cells 10000000` and `--max-bytes 268435456` bound matrix/output
work. BAM records default to10million per sample. Larger jobs require explicit
resource choices; no rows or samples are silently subsampled. PNG/PDF/SVG are
available for correlation; DPI is not an exposed correlation option. All
failures retain partial diagnostics and publish no successful result.

The implementation follows the original documentation for
[computeMatrix](https://deeptools.readthedocs.io/en/3.5.6/content/tools/computeMatrix.html),
[plotProfile](https://deeptools.readthedocs.io/en/3.5.6/content/tools/plotProfile.html),
[multiBamSummary](https://deeptools.readthedocs.io/en/3.5.6/content/tools/multiBamSummary.html)
and [plotCorrelation](https://deeptools.readthedocs.io/en/3.5.6/content/tools/plotCorrelation.html).
The tests verify these exposed contracts; published older versions and complete
paper pipelines still need separate compatibility work.

## Native method details

`compare-native` transforms two supplied plain bedGraph signals over an exact
declared reference and bin grid. It supports ratio, log2, reciprocal ratio,
subtract, add, first, second and mean. Scaling precedes pseudocounts, which apply
only to ratio operations. Zero-over-zero filtering occurs before those changes.
Missing values in either input propagate even for first/second operations.

Declare `--missing zero`, `covered` or `error` and `--invalid error` or `retain`.
Zero-filled means and covered-base means are different estimands. Retained
missing/nonfinite results have `NA` plus a status in `results.tsv`; those bins are
omitted from `signal.bedgraph`. No zero is invented. Omissions, unrepresented
positions and an empty finite result have prominent warnings. The source tests
cover weighted intervals, short terminal bins and 64-bit coordinates.

`scale-native` applies a declared NONE/CPM/RPKM/BPM/RPGC multiplier to supplied
per-bin alignment counts. Specify raw or estimated counts. Raw counts must be
nonnegative integers, and segments must align with the nominal bin grid except
at reference ends. Merged consecutive bins are accepted. RPKM retains the nominal
bin width for a short terminal bin. Normalized modes require positive declared
kept-alignment depth; RPGC additionally requires effective genome bases and
represented read/fragment length. NONE uses only the explicit multiplier.

The helper **does not estimate counts, depth or fragment length from BAM**.
Filtering, excluded normalization chromosomes, duplicates, blacklists and read
versus fragment units must be established separately. BPM follows the original
bamCoverage kept-alignment scaling function; it is not inferred TPM normalization
from the sum of supplied bin counts. Native finite output is float64 bedGraph,
distinct from binary float32 BigWig. Inputs remain unchanged and are hashed.

## Running the native methods

For existing plain bedGraph files and an exact two-column chromosome-size file:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method compare-native --input sample.bedgraph --input2 control.bedgraph --sizes reference.sizes --genome-build GRCh38 --bin-size 50 --operation log2 --scale1 1 --scale2 1 --pseudo1 1 --pseudo2 1 --missing zero --invalid retain --out output/log2-track
```

For supplied raw per-bin alignment counts with a separately established kept
alignment denominator (replace the illustrative 1,000,000 with your audited value):

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar deep-tools --method scale-native --input counts.bedgraph --sizes reference.sizes --genome-build GRCh38 --bin-size 50 --normalization CPM --count-kind raw --mapped-alignments 1000000 --out output/cpm-track
```

Use a new output directory for each run. Review `WARNINGS.md`, `session.yaml`,
`analysis-status.json` and per-bin statuses before using the finite-only
`signal.bedgraph`. A declared build label alone does not establish reference
compatibility. Missing/nonfinite handling changes which positions are reported.

## Runtime failures and recovery

The original deepTools 4.0.0 native Windows attempt failed while preparing pysam
0.24.1 because a make subprocess could not be found. A second attempt supplied
the existing read-only Cygwin build tools; it advanced to pyBigWig 0.3.26, whose
setup raised `AttributeError: 'NoneType' object has no attribute 'split'`.
Neither attempt installed or scientifically validated deepTools.

Exact attempt records and logs are retained under
`build/literature-review/deeptools-runtime/`: `windows-dependency-attempt.json`,
`windows-stage-1.log`, `windows-with-make-attempt.json` and
`windows-with-make.log`. A version/import check alone would not validate an
analysis. Original [installation guidance](https://deeptools.readthedocs.io/en/latest/content/installation.html)
describes supported dependency approaches and the available wheel platforms.

Required action: keep affected upstream analyses skipped. Where permitted,
supply a supported isolated runtime, install the declared original version and
its dependencies, then pass real coverage/BigWig/matrix/plot scientific fixtures
before enabling the adapter. Do not substitute a new version, generic CPM, zero
filling or interval arithmetic for a paper's actual operation. No WSL or OS
service is provisioned automatically.

The alternate private route built original deepTools 3.5.6 with pinned Cygwin
Python 3.9.25, NumPy 2.0.1, SciPy 1.13.1, Matplotlib 3.9.4, pyBigWig 0.3.26,
py2bit 0.3.3, deeptoolsintervals 0.1.9 and Pysam 0.23.3. Scientific source modules
remain unchanged; all 42 deepTools module hashes match the original wheel.
Pysam uses an explicit linker-export flag and a PE-export-aware build audit with
a positive control for genuine duplicate exports. Compiler failures and the
retained GCC12 pybind11 array-bounds warning are in the development records.

A private layout now allocates 172 fresh DLL copies around 519 existing DLL
preferred ranges. Earlier arbitrary ranges collided with Cygwin iconv and
private LAPACK/OpenBLAS. Original/loaded system DLLs were preserved. The managed
profile also pins `LD_LIBRARY_PATH` so Python's private and system core DLLs are
not loaded together. All required files and fonts are hashed.

**CYGWIN STARTUP REQUIRES THE VALIDATED FONT-CACHE TEMPLATE.** Fresh font-cache
startup under the isolated/no-bytecode configuration exited before completion,
even with exit code 0. The managed profile copies the validated Matplotlib v390
font-cache template into each attempt and verifies completion markers and four
real subprocess/eigenvalue checks. Missing/changed cache or runtime data skips
with code 3 and exact recovery steps. Do not accept a version probe or exit code
0 alone as an analysis result. The original source, failed attempts and cached
font metadata remain retained. This is a platform requirement, not imputation
or a substitute scientific calculation.

`tools/literature/install_deeptools.py` seals/reuses an explicitly prepared private
Cygwin profile, or installs pinned dependencies in a private supported native
venv where permitted. Source builds require `--allow-source-build`; downloads
require `--allow-download`. A real managed reuse check passed with both forbidden.
The current managed profile seals 56,199 files; hashing is deliberately broad
and can be slow. The Java adapter verifies every member before/after analysis
with four bounded readers and retained progress/timing logs. Do not modify the sealed runtime in place. Choose a new environment
and preserve the failed/changed one if a dependency needs repair.

Local genomics builds disable curl/GCS/S3 remote input. The original-program
verifier is `tools/literature/verify_deeptools_original.py`; it checks actual
unmodified functions and requires explicit completion markers plus scientific
output checks. The actual managed launcher passed all 17 scientific runs and a post-run check
of all 56,199 sealed files, with no changes. See [managed scientific validation](deeptools-managed-scientific-validation.json).
All eight exposed Java methods passed typed input/output and packaged checks.
Other programs/options,including plotPCA and multiBigwigSummary,remain pending.
No OS/global package, registry or PATH installation was performed.

One development retry had CRLF shell lines and selected system Cygwin Python.
It failed before package installation; the existing package directory was
verified unchanged. The unintended download-cache directory was verified,
inventoried and removed. Corrected scripts use LF, an absolute private-Python
prefix assertion and `PIP_REQUIRE_VIRTUALENV`. Details are in
`stray-cache-cleanup.json` and the retained development logs. There was no global
Python package, OS, registry or PATH modification.

**ATAC SHIFT DISCARDS SEQUENCE, QUALITIES AND ALL OPTIONAL SAM TAGS:** original
3.5.6 alignmentSieve emits replacement coordinates/CIGAR/template fields without
SEQ,QUAL,RG,NM,CB,RX or other tags. Preserve the original BAM for analyses that require those fields. A
shifted alignment is not a replacement input for variant calling or sequence QC.

## Primary-source review

All 21 currently extracted deepTools contexts are individually reviewed,
including a newly detected DeepTools2 methylation-profile use. Eleven supporting
paragraphs retain additional assay/settings evidence. Key distinctions include:

- RPGC effective genome and represented length; excluding chromosome X from a
  normalization denominator does not necessarily omit its signal.
- Per-bin sums versus means, scaled gene bodies versus reference points,
  original region order, zero-row inclusion and missing-data handling.
- Supplied boundary probabilities, methylation fractions and ChromHMM state
  percentages versus alignment counts.
- Distinct ATAC alignmentSieve/Tn5 shifts, ChIP coverage, spike-in scaling,
  FRiP counting units and DESeq2-derived scale factors.
- pyBigWig library statistics versus deepTools commands; zoom/default/missing
  behavior must not be inferred. Python qnorm normalization is distinct from
  R's inverse-normal `qnorm` function. UCSC bigWigMerge is a separate program.

The eight-operation reference writer loads the original unmodified deepTools
3.5.6 `getRatio.py` with NumPy scalar inputs, without JLinAlg result formulas or
an I/O stub. Frozen references and provenance are in
`src/test/resources/deeptools`; `tools/literature/generate_deeptools_references.py`
reproduces them from the pinned source. This is arithmetic validation, not
whole-program I/O or paper-pipeline parity. MIT attribution and the original
license accompany the adapted native helper in `META-INF`.
