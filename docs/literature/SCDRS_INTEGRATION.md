# scDRS workflows and native Java components

Use `scdrs-tools` for explicitly requested original scDRS 1.0.0, 1.0.1 or 1.0.2 workflows. The separate `ScdrsScores` Java API implements numerical components described in [SCDRS_DEVELOPMENT.md](SCDRS_DEVELOPMENT.md). That earlier document and its evidence describe a historical component checkpoint. The workflow evidence here does not reconstruct a paper's complete analysis or establish biological validity.

**SKIP3: skip dependent analysis when the exact permitted runtime is missing or installation is forbidden.** Read the named prerequisite in `WARNINGS.md`, `setup.json` or the console. Supply it where permitted, or leave the analysis skipped. Analysis never installs software. Setup requires explicit download/install options and new paths.

**FAIL2: NO RESULT ADMITTED.** Preserve the complete attempt, including logs and `unadmitted-original` output. Resolve the named input, integrity, capacity, process or numerical cause and retry using new output paths. Never interpret partial output or silently change source versions, control counts, covariates, identifiers or nonfinite results to obtain success.

**Before interpretation require all four:** wrapper exit 0, `result_admitted=true`, `COMPLETED.json`, and successful `--verify`. Read `WARNINGS.md`, `session.json`, `analysis-summary.json`, `inspection/input-contract.json`, the identifier disposition ledger and original source logs. Hashes establish integrity, not producer authenticity. Saved verification checks complete output and provenance contracts; it does not independently refit scDRS scoring, LOESS, covariate regression or neighbor graphs.

## Start with the standalone development artifact

The canonical `build/cli/omicsmatic-0.3.7.jar` is unchanged and does not contain the new command. From the repository root, use the self-contained workflow artifact:

```sh
java -jar docs/literature/scdrs-workflow-development/scdrs-workflow-javac21.jar --help
java -jar docs/literature/scdrs-workflow-development/scdrs-workflow-javac21.jar --export-runtime-helper /absolute/NEW-scdrs-helper
```

The root supplement also preserves the predecessor MTAG and GENESIS commands. It requires the separately pinned canonical JAR. Linux example (`;` replaces `:` in a Windows Java classpath):

```sh
java -cp docs/literature/scdrs-workflow-development/scdrs-root-javac21.jar:build/cli/omicsmatic-0.3.7.jar org.jlinalg.cli.JLinAlgCli scdrs-tools --help
```

Original analysis requires Linux x86_64, including a permitted WSL distribution on Windows, and the exact private Python 3.10.16 dependency profile. Windows Java 21/25 and Linux Java 21 can verify completed saved bundles without Python. Both compiler artifacts target Java 17 bytecode; the named JVMs were tested.

## Prepare a private runtime

Run the exported helper with Linux Python 3.11 or later. To install the locked environment, provide a permitted native Linux path and an existing micromamba executable:

```sh
python3 /absolute/NEW-scdrs-helper/prepare_scdrs_runtime.py \
  --version 1.0.2 \
  --out /absolute/NEW-scdrs-profile \
  --environment-home /var/tmp/NEW-scdrs-environment \
  --micromamba /absolute/path/to/micromamba \
  --allow-install --allow-download
```

`--environment-home` is the new installation destination. Use a persistent, permitted location; temporary locations can disappear. The helper does not install micromamba itself. Missing software or denied downloads produce SKIP3; corrupted archives produce FAIL2. Resolve the reported cause and retry new paths. No analysis runs during setup.

For an already prepared exact private environment, replace the installation options with:

```sh
python3 /absolute/NEW-scdrs-helper/prepare_scdrs_runtime.py \
  --version 1.0.2 --out /absolute/NEW-scdrs-profile \
  --existing-environment /absolute/exact-private-environment \
  --archive-dir /absolute/complete-locked-archives
```

Offline setup requires the complete named archives in `--archive-dir`; missing archives are skipped unless `--allow-download` is explicit. The lock pins 25 base packages, 46 wheels and three original source archives. Three small wheels are exported alongside the helper; their packaged Python source matches the pinned original source distributions and their licenses are included. Setup and every analysis check complete installed/source file inventories and imported versions. System loader and shared libraries are outside that inventory guarantee.

Require `PASS_PRIVATE_HISTORICAL_SCDRS_RUNTIME_PROFILE_ONLY` in `setup.json` and retain the complete setup directory. The analysis request points to its `runtime.json`. Prepare a separate profile for each explicitly chosen source version. Do not substitute 1.0.3 or 1.0.4 to conceal a historical failure.

## Run one explicit dataset

Save this complete request as `scdrs-request.json`. Replace every `/absolute/` path and choose capacities appropriate for your data before running. Every top-level field is required; unused operation blocks and inputs are JSON `null`. Example inputs are an H5AD with literal unique cell/gene identifiers, a weighted `.gs` gene-set file, and a numeric covariate table with explicitly aligned identifiers. The H5AD must contain the `celltype` and `module` observation annotations used below.

```json
{
  "schema_version": 1,
  "version": "1.0.2",
  "runtime_profile": "/absolute/NEW-scdrs-profile/runtime.json",
  "output_dir": "/absolute/NEW-scdrs-analysis",
  "operation": "score-and-downstream",
  "inputs": {
    "h5ad": "/absolute/expression.h5ad",
    "gene_sets": "/absolute/disease.gs",
    "covariates": "/absolute/covariates.tsv",
    "scores": null,
    "gene_statistics": null
  },
  "expression": {"species": "human", "raw_counts": false, "filter_data": false},
  "scoring": {
    "gene_set_species": "human", "controls": 1000, "match": "mean_var",
    "weighting": "vs", "adjust_proportions": null,
    "return_control_raw": false, "return_control_normalized": true
  },
  "downstream": {
    "groups": ["celltype"], "correlations": ["module"], "gene_analysis": false,
    "knn_neighbors": 15, "knn_pcs": 20, "group_bh": true, "group_fdr": 0.05
  },
  "gene_sets": null,
  "limits": {
    "input_bytes": 20000000, "output_bytes": 256000000,
    "working_bytes": 2000000000, "files": 3000, "wall_seconds": 600,
    "cells": 1000, "genes": 5000, "nonzeros": 500000,
    "matrix_elements": 1000000, "traits": 10
  }
}
```

```sh
java -jar docs/literature/scdrs-workflow-development/scdrs-workflow-javac21.jar --request /absolute/scdrs-request.json
java -jar docs/literature/scdrs-workflow-development/scdrs-workflow-javac21.jar --verify /absolute/NEW-scdrs-analysis
```

Analysis uses immutable staged input copies, the unchanged original CLI functions, explicit source flags and bounded supervised processes. It retains original output bytes. A complete manifest covers every admitted file. Existing output paths are refused without overwriting them. Review the identifier ledger for filtering, literal intersections and any source covariate disposition.

The example capacities are for small controls. They are not a recommendation for an atlas. Logical ceilings include one million cells, 200,000 genes, ten billion nonzeros and 200 billion matrix elements; declared working memory also accounts for dense preprocessing, control-score matrices and retained control-gene/weight lists. These checks are estimates and do not impose OS RAM quotas. Passing a declaration check does not demonstrate that an atlas was processed or that its memory/time requirements fit the machine. The native component API has a separate 50-million cell/control-pair ceiling.

## Other operations and scientific choices

`score` uses the same expression and scoring blocks, with `downstream=null`. `downstream` uses an explicitly supplied full normalized-control score file in `inputs.scores`, `inputs.gene_sets=null`, `inputs.covariates=null` and `scoring=null`. It reports the literal cell intersection; it does not silently repair or reorder identifiers. Gene prioritization and annotation correlation use the original algorithms. Groups use the source's 95th percentile and its Geary heterogeneity procedure where applicable; an upstream CLI description referring to a 5th percentile does not change the executed code.

`munge-gs` sets `expression`, `scoring` and `downstream` to null, supplies `inputs.gene_statistics`, sets the other inputs to null, and uses this block:

```json
"gene_sets": {
  "input_kind": "pvalue", "weighting": "zscore",
  "minimum_genes": 100, "maximum_genes": 1000,
  "fdr": 0.05, "fwer": null
}
```

Supported input kinds are `pvalue` and `zscore`; munging weights are `uniform` and `zscore`. Keep unused FDR/FWER thresholds null. Original munging sorts genes, drops source-defined missing values, floors P at 1e-100, caps converted Z at 10 and writes weights with five significant digits. These transformations are reported. Supplying author-prepared raw MAGMA Z weights directly in `.gs` is a different input decision; do not automatically munge them and claim identical weights.

Scoring supports original `mean`/`mean_var` matching and `uniform`/`vs`/`inv_std`/`od` weighting, explicit control counts and source proportion adjustment. Preprocessing requests 20 mean and 20 variance bins; upstream can reduce binning for small gene universes. `raw_counts=true` and `filter_data=true` deliberately invoke the original normalization/filtering policies. Choose them from input provenance. No automatic batch correction, variant exclusion, MAGMA annotation or gene ranking is inferred.

Historical 1.0.0/1.0.1 preprocessing can fail on text covariates; the failure is retained. Version 1.0.2 uses its original categorical encoding and missing-covariate imputation, with warnings and disposition records. The wrapper does not invent an encoding or impute expression/score values. Duplicate IDs, invalid inputs and nonfinite admitted outputs are refused. A gene set with fewer than ten overlapping genes is a source capability skip. Do not reduce controls or switch versions as an automatic retry.

Optional native group BH follows Statsmodels 0.13.5 arithmetic on the literal exported group P values. Its family is one trait and one annotation across all tested groups in this single dataset, using strict `q < group_fdr`. Do not pool independent datasets, strata or unrelated trait families. This BH calculation is separate from original scDRS statistics.

Original annotation-correlation output contains `corr_mcp`, `corr_mcz` and `n_ctrl`, which are Monte Carlo quantities. A plain Pearson module-eigengene coefficient, as used separately in P085, is a distinct calculation. For explicitly aligned finite vectors, the project's existing Java `StatisticalTests.correlation(x, y, CorrelationMethod.PEARSON, Alternative.TWO_SIDED)` API returns `estimates().get("correlation")`. Choose and document the intended statistic; do not label `corr_mcz` as Pearson r.

## Six primary paper roles

The source review covers six papers. P169 was previously missed because the article writes the plural `scDRSs`; a source-reviewed registry overlay accepts that spelling without altering the historical base registry. Primary paragraph hashes and pointers remain auditable; article prose and participant data are not distributed in this evidence.

| Paper | Reported role and constraints |
| --- | --- |
| P021 | scDRS 1.0.1 in lifespan DLPFC nuclei; MAGMA Z weights, technical-noise weighting, 200 matched controls and normalized control output. Preserve 200, rather than substituting 1000. |
| P024 | Top 1000 MAGMA Z genes across GWAS traits; reported default scDRS settings and no stated scDRS version. APOE exclusion, MAGMA windows and genome/LD files are separate explicit preparation. |
| P055 | scDRS 1.0.2, reported defaults, cell-level disease association for cell-type enrichment. The anchor does not specify every preprocessing or downstream command. |
| P085 | scDRS 1.0.0, 1000 controls, supplied human/mouse gene sets; separate datasets and disease/genotype/age strata. Ordinary Pearson module correlations are a separate step. |
| P123 | scDRS 1.0.2, top 1000 MAGMA genes outside MHC, 1000 controls and genes-per-cell/age/sex covariates; BH across cell types within each dataset, FDR < 0.05. Cohort filtering remains explicit preparation. |
| P169 | scDRS 1.0.1 (`scDRSs`), top 1000 CRC MAGMA genes, separate Tabula Sapiens v4 and Gut Cell Atlas inputs; pooled normalized control scores for cell P. Exact commands, controls, preprocessing and source commit are unreported. |

Release labels do not establish author commits. Synthetic workflow agreement does not reproduce these cohorts or validate biological conclusions. The 250-paper goal and remaining tool/semantic reviews remain open; Cell Ranger remains excluded by the user's choice.

## Evidence and replay

`scdrs-workflow-development/VALIDATION.json` pins the source, both standalone/root artifacts, current controls and retained earlier attempts. The workflow controls compare original output data bytes with independent original CLI calls, covering the three historical versions, 200/1000 controls, alternative weights/matching, species mapping, covariates, filtering, identifiers, multiple traits, downstream and munging operations. Two controls use 1000 disease genes with 1000 control sets. These are synthetic controls, not large-atlas benchmarks.

The refusal controls exercise missing/forbidden prerequisites, invalid input, capacity, timeout, existing-output preservation and coherently resealed saved-data tampering. Native BH is compared bit for bit with Statsmodels, including strict-threshold neighbors, ties, signed zero and subnormals. Root checks preserve predecessor scientific bytes and exercise legacy routes. Earlier failures remain retained and labeled.

```sh
python tools/literature/verify_scdrs_workflow_evidence.py
python tools/literature/verify_scdrs_workflow_evidence.py --replay windows21 --out build/literature-review/scdrs-runtime/NEW-saved-replay
```

Choose `windows25` or `linux21` for the other tested JVMs. The evidence verifier unpacks complete saved bundles and invokes Java verification; it installs nothing and performs no Python scientific fitting. Missing permitted Java is SKIP3; integrity or contract failure is FAIL2. Keep the replay receipt and warnings.
