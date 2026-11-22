# CellBender source roles and original-runtime intake

**A bounded original 0.4.0 CPU adapter is now validated in development.** Read
[CELLBENDER_WORKFLOWS.md](CELLBENDER_WORKFLOWS.md) and `cellbender-validation.json`
for the packaged command, independent count audits, permitted setup and recovery.
The original-runtime intake below is retained as earlier evidence; it does not
establish historical-version or paper-wide reproduction. The full goal remains
active. Cell Ranger remains excluded and was not installed or run.

The frozen cohort still contains 250 papers, 379 tools and 2,428 tool observations.
There are now 658 individually reviewed roles and 191 supporting pointers. The
thirteen CellBender observations retain their original primary paragraph hashes;
four additional source pointers cover multiome/spatial/downstream reuse and
published-data reprocessing. Paper-wide semantic completeness remains open.

| Paper | Reported release | Reviewed use |
| --- | --- | --- |
| P008 | unstated | Ambient correction after STARsolo quantification |
| P024 | unstated | Contamination assessment within nucleus QC |
| P039 | 0.2.0 | Background correction and cell calling; 40,000 included droplets; conditional learning-rate adjustment; multiome GEX and spatial/downstream reuse |
| P046 | 0.3.2 | Ambient correction of pooled oRG libraries |
| P048 | 0.1.0 | Ambient correction before DoubletDetection/SCANPY |
| P049 | 0.3.0 | Ambient/background correction with 150 epochs, FPR 0.01 and learning rate 0.0001 |
| P055 | literal `3-alpha` | Reported removal of ambient-contaminated cells; precise alpha commit and operation unclear |
| P058 | 0.2.1 | Per-sample remove-background with reported defaults |
| P085 | 0.2.0 for new data | Parse-derived counts and separate reprocessing of kallisto-derived published samples |
| P088 | unstated | Reuse of published differential-expression results based on corrected counts, with a background-contamination flag |
| P104 | 0.4.0 | Initial contamination assessment reporting no significant ambient effect; separate SoupX confirmation |
| P123 | 0.1.0 | Background correction and cell calling before independent QC/doublet filtering |
| P161 | 0.2.0 | Expected cells 10,000; 50,000 droplets; FPR 0.01; 150 epochs; manual learning-curve inspection |

**Required action:** read each paper's reviewed summary and scope note in
`papers.json` or the catalog. P088 does not establish a new CellBender run. P024
and P104 do not establish that all counts were replaced by denoised matrices.
P055's alpha label must not be silently converted to stable 0.3.0. Historical
defaults, preprocessing, upstream quantifiers and downstream QC remain distinct.
The reviewed rows are in `tools/literature/cellbender-use-reviews.json`; validate
them with `curate_cellbender_uses.py` against the frozen cached article XML.

## Version and dependency requirements

The [official repository](https://github.com/broadinstitute/CellBender) describes
CellBender as an ambient/background-count removal tool. Its current stable release
was verified as 0.4.0 from official GitHub and PyPI metadata; the source tag points
to commit `18263f4fe744fe5821962e2ededa09a732e3993c`. The upstream wheel SHA-256 is
`b8cfb9426b29357ce3d24033febece0867bf2c25160212924a34ea216b4f0128`.
The original software has a BSD 3-Clause license.

**Do not use redacted version 0.3.1.** Upstream reports an integer-overflow defect
in its corrected count matrices. See the [official changelog](https://cellbender.readthedocs.io/en/latest/changelog/index.html).
Current 0.4.0 execution is not validation of historical 0.1.x, 0.2.x or alpha
releases. Even versions described by upstream as algorithmically unchanged need
their own environment/input checks before exact historical equivalence is claimed.

A separate private Linux x86_64 environment was installed at
`/opt/jlinalg-cellbender-0.4.0-cpu-1` in the existing validation WSL distribution.
It uses Python 3.12.3, original CellBender 0.4.0 and PyTorch 2.8.0+cpu. Existing
SAIGE/FUMA environments and global Python packages were preserved. Import and
version probes passed. Pip reports retain dependency versions, download URLs and
hashes. This environment is a validation intake, not yet a sealed project runtime
profile or a production installer. GPU presence does not make a CPU build CUDA
capable; no GPU analysis is claimed.

**If required software cannot be installed or run where permitted, skip the
dependent analysis.** Record the missing/forbidden dependency, affected analysis
and exact recovery. Supply an approved original runtime on a supported host, or
follow the [official installation guide](https://cellbender.readthedocs.io/en/latest/installation/index.html)
in a new isolated environment where installation is permitted. Preserve failed
or partial outputs. No automatic installation or unsupported version substitution
should occur in an analysis command. A dependency probe is not an analysis result.

## Original synthetic reference

The original 0.4.0 `remove-background` full model completed 150 epochs on CPU with
FPR 0.01, learning rate 0.0001, expected cells 100, 400 analyzed droplets, two
threads and one training attempt. Input contains 1,000 unfiltered synthetic
droplets and 100 genes, with 100 true cells. Independent seeded Poisson signal and
ambient counts were generated without using CellBender's simulator or fitted model.

The original run produced full/filtered HDF5 matrices, posterior, cell barcode
CSV, metrics, log, PDF, HTML report and checkpoint. Independent checks verified
the complete feature/barcode order, integer nonnegative counts no greater than
the input, probability bounds, and exact agreement among strict probability
`>0.5` calls, filtered columns and barcode CSV. All 100 simulated cells were called.
This is a controlled toy check, not proof of real-data accuracy or convergence.

| Population | Raw UMIs | Corrected UMIs | Removed UMIs |
| --- | ---: | ---: | ---: |
| All 1,000 input droplets | 178,754 | 123,071 | 55,683 |
| The 100 called cells | 132,542 | 123,071 | 9,471 |

**The H5AD/CSR run's upstream headline metrics use called-cell totals because
of an in-memory aliasing defect.** During MCKP target preparation, the original
code zeroes noncell rows through its raw CSR matrix reference. The input file is
unchanged. The gzip MTX loader supplies CSC storage, which the helper copies
before zeroing; that run retains the correct full-population totals. A direct
original-helper probe reproduced CSR mutation and unchanged CSC input. See the
[pinned original call](https://github.com/broadinstitute/CellBender/blob/18263f4fe744fe5821962e2ededa09a732e3993c/cellbender/remove_background/run.py#L266)
and [in-place helper](https://github.com/broadinstitute/CellBender/blob/18263f4fe744fe5821962e2ededa09a732e3993c/cellbender/remove_background/sparse_utils.py#L58).
Independent audits must retain immutable raw totals for both populations.
FPR describes the requested
count-removal tradeoff; it is not a GWAS/DE P-value or a cell-classification error
rate. The sum of absolute count errors against known synthetic signal in the true
cells was 10,738 before correction and 4,749 after correction. That diagnostic
does not certify performance on biological data.

The uncompressed three-column `features.tsv` Matrix Market attempt failed because
the original loader selected its v2 convention and looked for `genes.tsv`.
The original v3 loader requires `matrix.mtx.gz`, `features.tsv.gz` and
`barcodes.tsv.gz`. The failed attempt is retained. Do not silently change feature
names or drop feature-type metadata to force loading. Input conversion must be
explicit, hashed and count-preserving. See `cellbender-source-validation.json`
for the latest format-comparison result and exact evidence hashes.

The successful H5AD and gzip MTX runs agree exactly on corrected full/filtered
counts, barcode calls, learning curves and 61 HDF5 datasets. The full output's
background-fraction array differs in 300 noncell rows because of the same CSR
mutation; the filtered array agrees. Three CSV metrics differ: full raw counts,
full removed counts and full removed fraction. The absent MTX genome annotation
also remains explicitly missing. These diagnostic differences are retained; no
complete output-format equivalence is claimed. The integrated adapter must use
an explicit count-preserving input convention and independently check both raw
and corrected population totals before admission.

For real analysis, supply raw integer UMI counts with empty droplets retained.
Do not substitute normalized, log-transformed, feature-selected or cell-filtered
data merely to make fitting easier. Review the [upstream usage and QC guidance](https://cellbender.readthedocs.io/en/latest/usage/index.html),
learning curves, cell probabilities, removed counts and biological controls.
No paper data, historical runtime, RNA alignment, doublet removal, downstream
expression analysis, hosted service or excluded Cell Ranger execution is reproduced
by this checkpoint.
