# Original SAIGE sparse GRM and null-model workflows

**Available in a development candidate:** `saige-tools` exposes original SAIGE 1.5.2 `sparse-grm`, `fit-null` and diploid autosomal PLINK `single` association. Read [single-association guide](SAIGE_ASSOCIATION_WORKFLOWS.md) and `saige-association-validation.json` for the earlier112-test milestone. The current122-test candidate and numerical guard are documented in [SAIGE_NUMERICAL_FINDINGS.md](SAIGE_NUMERICAL_FINDINGS.md) and `saige-numerical-validation.json`. This guide describes GRM/null preparation; `saige-workflow-validation.json` preserves its earlier88-test milestone. BGEN/VCF dosage association, conditional/gene tests, LD export and Meta-SAIGE integration remain unfinished and dependent analyses must be skipped. The SAIGE tool badge credits this bounded integration; SAIGE-GENE and Meta-SAIGE remain missing. The canonical executable and public release have not changed.

**Missing, forbidden, unsupported or changed dependencies mean SKIP.** Read the output `WARNINGS.md`: it names the cause and required action. Native Windows Java cannot execute this Linux profile. Use Linux Java inside an Ubuntu 24.04 x86_64 WSL environment, or a matching Linux host. Cell Ranger remains excluded by the user's request.

## Install explicitly where permitted

Setup requires Ubuntu 24.04 x86_64 and Python 3.12, private writable directories, compilation resources and sufficient free storage. Use an ASCII installation prefix without whitespace: original upstream build files do not quote every prefix. Analysis input and output paths may contain Unicode and spaces.

```sh
python3 tools/literature/install_saige.py \
  --home /home/researcher/.local/share/omics-saige-v1 \
  --cache /home/researcher/.cache/omics-saige-archives \
  --report /home/researcher/saige-setup-report-1 \
  --allow-install --allow-download
```

Replace these example paths with your own new private directories. Omit `--allow-download` when network access is forbidden; supply populated archive and `--package-cache` directories for an offline build. Without `--allow-install`, an absent runtime is skipped and no runtime home is created. A verified existing home can be reused without either permission. An existing unmanaged or changed home, corrupt archive and existing report are preserved. Correct the named cause and choose a new home/cache/report where needed. Never edit its admission marker or re-seal a changed runtime.

Only setup launches the pinned Pixi package manager, compilers and R installation. Analysis invokes the admitted `Rscript` directly. The profile pins original source commit `365deeff9149fc2b5a66da8fa3799637bcb30af3`, four source/setup archives, 266 Conda package identities and 14 critical R package versions. The actual private build retained all 481 original source files unchanged. Runtime checks cover the complete admitted file/link inventory and recorded OS dependencies before and after execution. An OS/library update can invalidate a home; provision and verify a new compatible runtime instead of editing its receipts.

## Run typed recipes

The five JSON examples under `examples/saige/` declare every scientific choice. Copy the recipes to your analysis directory and supply the referenced `cohort.bed/.bim/.fam` and `phenotypes.tsv`. Paths inside a recipe resolve relative to that recipe. The examples declare GRCh38 and diploid autosomal hard calls; replace the assembly declaration only after confirming your actual reference and coding. No sex-chromosome, ancestry, allele-strand or REF/ALT conversion is inferred.

Run these commands from Linux, with the exact development candidate named in `saige-workflow-validation.json`:

```sh
java -Xmx4g -jar /path/to/development-candidate.jar saige-tools \
  --recipe /analysis/sparse-grm.json \
  --home /home/researcher/.local/share/omics-saige-v1 \
  --out /analysis/grm-run

java -Xmx4g -jar /path/to/development-candidate.jar saige-tools \
  --recipe /analysis/fit-binary-sparse.json \
  --home /home/researcher/.local/share/omics-saige-v1 \
  --out /analysis/binary-null-run
```

Existing output directories are preserved. An ASCII-named UTF8 `--options-file` JSON object containing string `recipe`, `home`, `out` and optional `timeout` values supports Unicode paths across Windows launchers; direct flags override its values. The timeout applies to each owned R process. Native admission additionally bounds input bytes, samples, variants, decoded genotype cells, sparse matrix entries and exported model cells. Select limits and Java heap size suitable for your data.

The sparse recipe preserves observed GRM diagonals and the original fixed seed of 1. Its native observed-call QC count is diagnostic; original float arithmetic and best-guess imputation can affect the original marker decisions. All-missing source markers are rejected with a request for an explicitly filtered source array. The source BED/BIM/FAM, staged snapshots and recipe hashes must remain unchanged through publication.

The null recipes declare binary case/control labels or a quantitative response, ordered categorical levels, missing tokens and `error` or `exclude-with-ledger` complete-case handling. Numeric and explicit dummy covariates receive safe generated column names, so source names cannot become R formulas. The first declared category is the baseline. Native rank/separation checks reject unsupported designs; the original fit and native output checks must also pass. `covariate_offset` is explicit: original SAIGE forces it false with no covariates and with one expanded binary-trait covariate, so request false in those cases. The full covariate design is still audited when offset fitting is requested.

Quantitative `inverse_normalize` uses average ranks and `qnorm((rank-0.5)/N)` on the complete model cohort, matching the original outcome transformation. It does not imply residual normalization. Full-GRM LOCO requires source markers on all 22 autosomes and audits all 22 chromosome models plus the base fit. Sparse fitting and sparse variance-ratio calibration are separate explicit choices. A sparse fit consumes a completed, hash-bound typed GRM bundle with the same assembly declaration, all model IIDs and a fit cutoff at least as high as the generation cutoff.

Single variance-ratio calibration uses the original fixed MAC >= 20 selection; declare `minimum_mac: 20`. Categorical calibration declares contiguous lower-exclusive/upper-inclusive MAC bins, with the last bin unbounded above. `num_markers` is the original initial sample size: upstream can increase it to meet the declared coefficient-of-variation criterion. Trace estimation is also adaptive. The owned process timeout bounds upstream execution; these initial settings do not certify a fixed number of stochastic calculations.

## Outputs and required review

Successful runs retain the supplied recipe, immutable inputs, actual runtime/version and process ledger in `session.json`, original stdout/stderr, `native-audit.json`, `results.json`, `analysis-status.json`, `manifest.yaml` and prominent `WARNINGS.md`.

Sparse-GRM canonical files are `grm.mtx` and `sample-ids.txt`, byte-identical to the original outputs. The audit checks every stored finite coefficient, dimension, coordinate ordering, triangle, cutoff and sample ID. Implicit matrix entries, including unstored diagonals, are zero. No unit diagonal or positive definiteness is inferred.

**Candidate screening does not certify complete related-pair coverage.** In the controlled fixture, independent full-GRM calculation identified 1,549 above-threshold pairs absent from the sparse output. This is a fixture-specific observation. Assess graph coverage independently before asserting complete coverage for [unrelated selection](UNRELATED_SELECTION_WORKFLOWS.md), and assess GRM suitability for your model.

Null fitting retains original `original-null.rda` and variance ratios plus byte-identical canonical `null-model.rda` and `variance-ratios.tsv`. `cohort-ledger.tsv` names included and excluded samples, and `phenotype-admission.json` records coding and counts. Static R inspection exports primitive fields to `original-model.json`; Java checks exact cohort/order, explicitly coded or independently rank-normalized outcomes, full covariate column space, convergence, finite variance components, ratio bins and every base/LOCO residual identity. This does not independently re-fit the original GLMM/LMM or certify scientific calibration, ancestry adjustment or historical paper equivalence.

Exit 0 means completed with review warnings. Exit 3 means an unavailable/unsupported/changed dependency skipped the analysis. Exit 2 means invalid inputs, execution, timeout or audit failure. Failed/skipped runs publish no admitted results; prior files remain under `partial/`, with status, diagnostics and required recovery at the top level. Correct the named cause and rerun to a new output directory. Read original R warnings, including retained import-replacement and R 4.5.3-built-package notices under admitted R 4.5.2, before scientific use.

The earlier [data audit findings](SAIGE_DATA_AUDITS.md) remain applicable to the association and wider workflows: exact BIM IDs, empty exit-0 gene output, pre/post-imputation QC, floating MAC boundaries, maximum-dosage ultra-rare collapse and unreportable self-conditioning are retained explicitly.

The sixth public recipe, `examples/saige/single.json`, reuses the completed null bundle. Read [SAIGE_ASSOCIATION_WORKFLOWS.md](SAIGE_ASSOCIATION_WORKFLOWS.md) before running it; every model IID is required and all QC, imputation, cleanup, allele and reportability choices are explicit.
