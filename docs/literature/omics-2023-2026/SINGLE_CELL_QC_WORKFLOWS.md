# Single-cell counts, doublets and SCTransform

Source work for the active 250-paper goal; unreleased. Native Java count handling,
Bayesian doublet-score arithmetic and negative-binomial residual arithmetic need no
Python or R. Full original Scrublet and SCTransform model fitting use tested upstream
software. The catalog records 18 Scrublet and 18 SCTransform paper contexts, with
explicit differences between wrappers, custom procedures and count layers.

**CHECK WARNINGS BEFORE FILTERING CELLS OR USING EXPRESSION VALUES.** On this host,
the original Scrublet variance fit emits `invalid value encountered in sqrt` on the
synthetic fixture; exported scores and uncertainties are finite and independently
checked. This warning is retained in `worker-status.json`, `session.yaml`, the console
and `WARNINGS.md`. It does not establish accuracy on a new dataset. No method here
automatically removes cells or infers a biologically appropriate doublet rate.

**UNAVAILABLE DEPENDENCY = SKIPPED ANALYSIS (exit 3).** Read the named package,
executable, version, source-hash or DLL cause in `WARNINGS.md` and `analysis.log`.
Supply the pinned runtime or install it where permitted using the commands below.
If installation is forbidden or fails, keep the affected analysis skipped and run
it on a permitted supported host. Corrected inputs or dependencies require a new
output directory. Execution/contract failures exit 2. Failed partial files remain
under `partial/`; no successful `results.tsv` is published. A completed analysis
can still contain scientific warnings requiring review.

## Verified boundaries

| Operation | Implementation and input | Scope |
| --- | --- | --- |
| `counts` | Native Java external COO sorting of a genes-by-cells MatrixMarket file | Sums duplicate coordinates before totals/detected genes; exact IDs, zero cells and feature types retained |
| `doublet-score` | Native Java Bayesian score and standard error from supplied simulated/total neighbor counts | No preprocessing, simulated cells, neighbor graph or automatic threshold fitting |
| `nb-residual` | Native Java NB2/Poisson Pearson residual from supplied mean/dispersion, variance floor and clipping bounds | No SCT parameter fitting, regularization, covariate regression or gene selection |
| `scrublet` | Original Scrublet 0.2.3 on one independent loading library | Actual exact/approximate neighbors, simulation, scores, errors, thresholds, parents, genes and optional manifolds |
| `sct-vst` | Original sctransform 0.4.3 `vst()` | Explicit v1/v2, fit sampling, model latent variables and corrected counts |
| `sct-seurat` | Seurat 5.6.0 / SeuratObject 5.4.0 `SCTransform()` | Explicit v1/v2, separate post-fit regression, variable-gene residuals, corrected/log layers and complete RDS |

Current validation uses 600 genes and 252 synthetic cells (240 singlets in two
lineages plus 12 injected cross-lineage count sums), original-package reference
outputs and exact-label edge cases. Native score/error checks cover 756 observed
and simulated cases; native residual checks cover 400 positions from fitted v1/v2
models. Seurat checks include 400 positions with phase-score regression. See
`single-cell-qc-validation.json` and `single-cell-qc-packaged-validation.json` for
the completed gate identities. Historical package versions and whole-paper
pipelines remain unvalidated.

The examples use the locally validated artifact:

```powershell
$jar = 'build/literature-review/validated-cli/jlinalg-0.3.7.jar'
java -jar $jar single-cell-qc --help
```

The executable and its validation manifest must come from the same completed
source snapshot. This work is not included in the public v0.3.7 release.

## Input and memory contract

Use standard 10x ID files **without headers**: features column 1 contains unique
feature IDs; optional columns 2/3 contain symbols/type. Barcodes contain unique
cell IDs. Counts are MatrixMarket coordinate integer/real general, genes by cells;
plain and gzip inputs are supported. `--feature-type 'Gene Expression'` selects that
type when present; `all` retains every type explicitly. Do not mix RNA and antibody
measurements accidentally. Numeric-looking barcodes and literal quote characters
are retained. IDs cannot contain tabs/newlines.

`--count-kind raw-umi` requires nonnegative integer counts and exact totals up to
2^53-1. `--count-kind estimate` explicitly permits nonnegative fractional values,
emits a model-assumption warning, and prohibits Scrublet binomial UMI subsampling
other than 1. Acceptance of estimates does not validate their biology. P060 uses
processed MERFISH profiles; P117 uses RCTD-derived weighted estimates. Neither
should silently be presented as raw droplet UMI data.

Native canonicalization uses bounded sorting (`--sort-records 100000`) with
temporary disk spill. SCT fitting and residual matrices still need substantial
RAM and disk: `--max-residual-values 50000000` bounds the requested output, not all
internal model memory. For Seurat, `--conserve-memory true` requires
`--variable-only true`. Choose feature universes and limits scientifically; the
adapter never silently reduces principal components, subsamples cells or filters
zero cells to make a failed model run.

```powershell
java -jar $jar single-cell-qc --method counts `
  --matrix counts.mtx.gz --features features.tsv.gz --barcodes barcodes.tsv.gz `
  --count-kind raw-umi --feature-type 'Gene Expression' --out build/count-audit
```

`counts/matrix.mtx`, `features.tsv`, `barcodes.tsv` and `cell-counts.tsv` retain the
canonical matrix and totals. Raw sources are hashed before/after and preserved.
The native count operation retains zero-count cells with a warning; external
models can reject them and retain explicit failure diagnostics.

## Private upstream runtimes

Scrublet's exact lock targets **Windows x86_64 Python 3.12**, with 33 original
package archives and SHA-256 hashes. Annoy 1.17.3 needs an existing native C++
compiler because no matching Windows wheel was available. Permission to build is
explicit; no OS/compiler installation is performed automatically. The successful
source build and earlier binary-only failure are retained. Build backends and the
host compiler are not hermetically locked; a repeated build need not be bit-identical.

```powershell
& $python312 tools/literature/install_scrublet.py --allow-install --allow-source-build
$scrubletPython = 'build/omics-scrublet-env-locked/Scripts/python.exe'
```

Set `$python312` to a permitted Python 3.12 executable. Existing managed runtimes
can be checked without install flags; unmanaged/changed/partial environments are
preserved. Choose a new `--environment` after correcting the exact failure. Runtime
import/version/pip checks are separate from scientific validation. Original
Scrublet scientific source hashes are checked again by each worker.

R 4.6.1 on this host uses a private library with sctransform 0.4.3, jsonlite 2.0.0,
Seurat 5.6.0, SeuratObject 5.4.0 and glmGamPoi 1.24.0. These versions and all actual
installed package identities/archives are recorded; transitive R dependencies are
not a complete future archive lock. The main Bioconductor index returned HTTP 403;
the officially listed [Posit mirror](https://bioconductor.org/about/mirrors/) worked.

```powershell
$rscript = 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe'
& $rscript --vanilla tools/literature/install_sctransform_dependencies.R `
  build/omics-sctransform-r build/literature-review/sctransform-r-dependency allow seurat-v2
$sctLibrary = 'build/omics-sctransform-r'
```

Use `vst-v2` for standalone fitting, or `vst`/`seurat` for v1 profiles. Replace
`allow` with `forbid` to check/reuse installed dependencies without installing.
An unmanaged existing library is preserved. V2 requires glmGamPoi by default;
`--allow-nb-fallback true` explicitly permits upstream `nb_offset` only when
glmGamPoi is absent. A wrong installed glmGamPoi version is not silently accepted.
Fallback is a different model path and is not the validated glmGamPoi fixture.

## Full model runs and result layers

```powershell
java -jar $jar single-cell-qc --method scrublet --python $scrubletPython `
  --dataset LIBRARY_A --matrix counts.mtx --features features.tsv --barcodes barcodes.tsv `
  --components 10 --neighbors 10 --expected-rate 0.1 --seed 42 `
  --approximate false --out build/doublets-library-a

java -jar $jar single-cell-qc --method sct-seurat --rscript $rscript --r-library $sctLibrary `
  --dataset LIBRARY_A --matrix counts.mtx --features features.tsv --barcodes barcodes.tsv `
  --flavor v2 --metadata phase-covariates.tsv --regress 'S.Score,G2M.Score' `
  --variable-features 3000 --seed 42 --out build/sct-library-a
```

The parameter values illustrate explicit choices, not universal QC defaults.
Scrublet must use the correct independent loading-library unit. Per-batch paper
wording does not justify pooling unrelated loading libraries. Scores and errors
are exported for every observed/simulated cell and independently recomputed in
Java from the actual neighbor counts. Predictions use strict `score > threshold`.
If automatic threshold selection fails, scores remain, calls are `NA` and a warning
requires threshold review. `--threshold` supplies an explicit reviewed value; an
undefined detectable-doublet fraction/rate is retained with a warning.

Metadata is a headered TSV with `cell_id` first and unique covariate columns, matching
all cells exactly. Rows are aligned by IDs; missing/extra cells fail. Declare
categorical columns with `--categorical`. Seurat `--regress` performs post-fit
regression; standalone `sct-vst --latent` defines model latent variables. They are
different operations. Feature IDs containing `_` or `|` receive internal aliases
to prevent Seurat rewriting/collisions. Exported tables restore original IDs;
`feature-id-map.tsv` records the mapping, and the RDS retains internal aliases with
a prominent warning.

Keep `residuals.mtx`, `corrected-counts.mtx` and `sct-logdata.mtx` separate. Each
has an explicit feature map and common barcode order; `selected-features.tsv`,
model/gene parameter tables, `worker-status.json` and the RDS preserve decisions.
Standalone vst does not provide Seurat's post-fit regression or logdata layer.
Residuals are not raw counts for arbitrary count-based differential-expression tools.

The original Scrublet implementation does not reproduce Scanpy wrappers,
SnapATAC2 ATAC doublets, two-step diffusion, cluster-median/FDR postprocessing,
DoubletFinder/DoubletDecon/scds consensus or manual marker decisions. SCT fitting
does not implement RCTD, reference annotation, integration, Harmony, WNN,
differential expression, scDist or composition analysis. See the source-linked
paper summaries and implementation queue for those remaining methods.

## Native arithmetic and reproducibility

`doublet-score --input` requires TSV columns `id`, `simulated_neighbors`,
`neighbors`, `simulation_ratio`, `expected_rate`, `rate_sd`; optional `--threshold`.
`nb-residual --input` requires `id`, `count`, `mean`, `theta`, `minimum_variance`,
`clip_low`, `clip_high`. `Inf` is allowed for Poisson dispersion and infinite
clipping bounds. Both commands require `--method` and a new `--out` directory.

Native APIs are `SparseCounts`, `DoubletScores` and `NegativeBinomialResiduals`
under `org.jlinalg.sequence`. Original synthetic fixture hashes and reproducible
direct-package reference scripts are in `src/test/resources/single-cell-qc`,
`src/test/python/scrublet_reference.py` and `src/test/R/single-cell-qc-reference.R`.
Scrublet attribution and its MIT license are retained in the executable resources.
The package authors' [Scrublet source](https://github.com/swolock/scrublet) and
[SCTransform documentation](https://satijalab.org/seurat/reference/sctransform)
describe the upstream methods; OmicsMatic exposes the narrower boundaries above.
