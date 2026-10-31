# REGENIE regression and gene-mask workflows

`regenie-tools` connects native Java input/model checks to pinned REGENIE 4.1.3.
It fits whole-genome predictions, tests single variants and constructs/tests gene
masks. The examples use this task's validated local source-build executable; its
exact hash and isolated baseline are recorded in `isolated-validation.json`. Its scientific validation is recorded separately from installation in the
literature validation reports. This is an unreleased project workflow.

**Required dependency unavailable or forbidden: skip the affected analysis.**
Exit 3, console diagnostics, `analysis-status.json` and `WARNINGS.md` identify the
dependency and required action. Exit 2 means an input, model or execution failure;
inspect the retained `partial/` files and logs, correct the cause and rerun to a
new output directory. Neither failure publishes a successful `results.tsv` or
model bundle. Existing output and runtime directories are preserved.

## Install where permitted

```powershell
python tools/literature/install_regenie.py --allow-download --allow-source-build --cygwin-root C:/cygwin64
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar regenie-tools --help
```

Windows uses an existing Cygwin toolchain **read-only**: GCC C/C++, GFortran,
make, Python 3.8–3.10 and zlib development headers. The installer builds in private
directories and copies the required runtime DLLs into `build/omics-regenie/bin`.
It does not modify Cygwin, install OS packages, change global PATH or mix MSYS2
and Cygwin compiler runtimes. If prerequisites are absent and installation is
forbidden, keep the analysis skipped and request a permitted environment.

REGENIE's [installation documentation](https://rgcgithub.github.io/regenie/install/)
lists Linux/macOS and recommends WSL for Windows. The private Cygwin profile is
an additional locally tested source build, not an official Windows binary.
Linux/macOS x86_64 profiles download pinned official archives with
`--allow-download`; those platforms require their own scientific verification.
Other architectures return an actionable skip.

The Windows build pins upstream 4.1.3 and BGEN 1.1.7 archive hashes. It applies
two hash-guarded BGEN compatibility edits: `_DEFAULT_SOURCE` feature visibility
and the standard `std::streampos` type spelling in file metadata handling.
Compiler flags explicitly select GFortran and Cygwin feature visibility. REGENIE
scientific sources are checked unchanged against the original archive. Original
compiler warnings, toolchain/file hashes, source edits and licenses are retained
in `build/literature-review/regenie-dependency/`. Installation/version success
alone is not scientific validation. Gzipped phenotype, covariate and result text
is disabled in this no-Boost-Iostream profile; internal BGEN zlib/zstd is available.

Verified managed runtimes can be reused with downloads/builds forbidden. A corrupt
or unmanaged destination is preserved: inspect it and select a different
`--environment`. Do not delete or overwrite it to hide an installation failure.
Analysis commands never install automatically.

## Fit an explicitly declared model

The following values illustrate the interface; select cohort-specific QC,
phenotypes, covariates, block size and allele conventions before running it.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar regenie-tools --method fit `
  --runtime build/omics-regenie --format bed --genotypes data/array_qc `
  --allele-order last --genome-build b38 --block-size 1000 `
  --phenotypes data/phenotypes.tsv --phenotype-columns trait1,trait2 `
  --trait quantitative --rint true --missing per-trait `
  --covariates data/covariates.tsv --covariate-columns age,sex,PC1,PC2 `
  --categorical sex --folds 5 --threads 1 --seed 17 --out results/fit
```

Tables begin `FID IID` followed by uniquely named columns. Names are exact;
column expansion syntax is not accepted. Only selected columns are canonicalized,
with original token values and sample order preserved. Numeric cells must be
finite complete tokens; missingness is explicitly `NA`. Numeric `-999` is rejected
because REGENIE reserves it internally. Rescale a legitimate value explicitly
before analysis rather than silently changing its meaning. Duplicate sample
pairs and distinct pairs that collide when joined as `FID_IID` are rejected.

`--trait quantitative` forces quantitative regression, including when values
happen to be 0/1; few distinct values produce a prominent warning. It never
infers logistic/Firth/SPA. For a binary phenotype use `--trait binary`, explicitly
select `--binary-coding 0/1` or `1/2`, and set `--rint false`. The upstream `--cc12`
flag is used only for 1/2 coding. One bundle has one homogeneous model/coding.

`--missing complete` passes `--strict` in both steps. `per-trait` retains the
upstream step-1 mean-imputation behavior and tests observed values per trait in
step 2. Missing selected covariates remove those samples. Exact matched counts,
missingness and exclusions are recorded in `session.yaml`; inspect these before
interpretation. An ancestry or phenotype-specific cohort is not inferred.

Fit uses low-memory blocks and relative prediction paths. Every requested phenotype
must produce usable LOCO predictions; a nominally successful process that omits
a phenotype fails model publication. `regenie-model.json` seals the selected
phenotype/covariate tables, prediction list and all LOCO files by SHA-256. Prediction
lists use explicit LF endings: the upstream prediction-list reader otherwise
treats Windows carriage returns as filename characters. Paths with spaces are
handled without a shell and without whitespace-containing LOCO list entries.

## Test variants using the sealed model

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar regenie-tools --method test `
  --runtime build/omics-regenie --format bgen --genotypes data/imputed.bgen `
  --allele-order last --genome-build b38 --block-size 400 `
  --model results/fit --test additive --min-mac 5 --correction none `
  --threads 1 --seed 17 --out results/association
```

BED uses a `.bed/.bim/.fam` prefix, PGEN an uncompressed `.pgen/.pvar/.psam`
prefix, and BGEN a layout-2 file. BGEN can supply `--sample OXFORD.sample`; otherwise
embedded IDs must match the exact phenotype `FID_IID` keys. The upstream sample
file takes precedence when explicitly supplied. PGEN requires `#FID IID` in PSAM,
biallelic PVAR rows and `--allele-order pvar`. An IID-only PSAM is rejected with an
explicit explanation. BED/BGEN require the declared reference-allele choice
`first` or `last` separately for each step. No allele harmonization is inferred.

Native Java validates sample identities, table/schema contracts, BED dimensions,
and BGEN/PGEN metadata. Upstream REGENIE decodes the original binary genotypes;
there is no Java conversion or recoding. BIM/PVAR unique-ID checking stores the
IDs in memory, so budget heap for very large variant collections. Assembly is
declared `b37`/`b38`, mapped to upstream `--par-region hg19`/`hg38`; no liftover or
automatic assembly verification occurs. Array step-1 and sequencing/imputed
step-2 variants may differ. Optional `--extract` accepts a nonempty, unique,
one-ID-per-line variant list without a header.

Step 2 inherits phenotype values, covariates, type/coding, categorical selection,
RINT and missing policy from the sealed fit. Changed bundle bytes, wrong build
and eligible samples missing from LOCO predictions fail before association.
It copies predictions into its own bundle; the fit remains unchanged.

Select `--test additive`, `dominant` or `recessive`. For a fitted binary model,
`--correction firth`, `firth-approx` or `spa` additionally requires a declared
`--p-threshold` in (0,1]. Firth modes optionally accept `--firth-se true`.
`--correction none` applies no rare-case correction. Binary correction cannot be
applied to a fitted quantitative model. Thresholds and coding must follow the
intended analysis, not whichever defaults happen to run successfully.

## Construct and test gene masks

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar regenie-tools --method genes `
  --runtime build/omics-regenie --format bed --genotypes data/exome_qc `
  --allele-order last --genome-build b38 --block-size 400 --model results/fit `
  --test additive --min-mac 5 --correction none `
  --annotations data/annotations.txt --set-list data/sets.txt --masks data/masks.txt `
  --mask-rule max --aaf-bins 0.001,0.01 --vc-max-aaf 0.01 `
  --gene-tests rgc --sbat-draws 10 --out results/genes
```

Files use the pinned upstream [annotation/set/mask syntax](https://rgcgithub.github.io/regenie/options/).
`--mask-rule max`, `sum` and `comphet` have different meanings. Supply the AAF bins,
variance-component maximum AAF and MAC threshold deliberately. Variant membership
lists are retained. Max/comphet mask genotypes are also written; upstream cannot
write sum-mask genotypes as BED, so that output is omitted with a prominent warning.
Sum masks support additive tests only. There is no automatic transcript,
pathogenicity, carrier-count, ancestry or gene-universe selection.

`--gene-tests burden` requests individual burden tests. `rgc` uses the version's
RGC gene strategy: burden/ACAT; additive variance-component/ACATV combinations;
quantitative SBAT and combined `GENE_P` where available. Non-additive testing
omits variance-component tests, with retained upstream warnings. SBAT draws are
explicitly recorded. This interface does not expose every separate SKAT/joint
test option, custom weight or multi-trait simulation setting.

**Comphet collapsing is not proof of variants in trans or pathogenicity.** Review
phase and the intended recessive/carrier definition before interpretation. The
paper-specific privacy perturbation, conditional PGEN, HTP/REMETA export,
time-to-event and interaction/multitrait workflows remain separate open work.
Version 4.1.3 does not establish parity with a paper's older REGENIE version.

## Read results and warnings

`upstream/assoc_<phenotype>.regenie` contains unchanged upstream results, including
`##MASKS` metadata, original alleles, test names, `NA` cells, effects, standard
errors and `LOG10P`. No automatic p-value conversion, FDR, meta-analysis or gene
filtering is performed. `results.tsv` is a bundle summary, not a replacement
association table. Sample/statistic/header checks run before success publication.

Review `WARNINGS.md`, `session.yaml`, `analysis-status.json`, `manifest.yaml` and
the actual upstream logs together. Empty results, unavailable statistics,
excluded samples, ignored annotations/filters and convergence problems remain
prominent. A process exit of zero alone does not establish usable predictions or
successful scientific inference. Preserve the original inputs and all failed
attempts; correct the cause and use a new output directory.
