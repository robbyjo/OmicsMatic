# SoupX: supplied-cluster markers and original ambient RNA workflows

**Development workflow. Read the warnings before using corrected counts.** The native Java [TF-IDF marker component](TFIDF_MARKERS_DEVELOPMENT.md) and [ambient arithmetic](SOUPX_AMBIENT_DEVELOPMENT.md) are available alongside a typed adapter to unchanged original SoupX **1.6.2 or 1.3.0**. The adapter estimates contamination and/or corrects supplied raw counts. These are scoped implementations, with toy/reference validation; they do not reconstruct a paper's unspecified settings or establish biological accuracy. The earlier component guides describe retained historical checkpoints.

**Require exit 0 and `result_admitted=true` in `analysis-status.json`.** Read `WARNINGS.md`, `provenance.json`, the original worker logs and every case's `source-status.json`. **SKIP3 means dependencies are unavailable and dependent analysis was skipped. FAIL2 means no result in the atomic batch is admitted**, even if an earlier case produced a matrix. Keep failed attempts and retry new paths after fixing the named cause. Never interpret a partial worker file as an admitted result.

## Quick start on a prepared Windows host

Run commands from the checkout root with Python 3, Windows Java 21 or 25, and the exact permitted R profile below. Analysis installs nothing. The development JAR is separate from the canonical executable and releases. In a fresh checkout build current sources with `./gradlew.bat executableJar`, then use `build/cli/omicsmatic-0.3.7.jar` as `$jar`. This command creates the local executable; an older released JAR will not contain the new class. The additive development builder is for a development workspace that already has its validated ambient-core parent JAR:

```powershell
python tools/literature/build_soupx_workflow.py --out build/literature-review/soupx-runtime/my-workflow-build
$jar = 'build/literature-review/soupx-runtime/my-workflow-build/omicsmatic-0.3.7-soupx-workflow-development.jar'
python tools/literature/prepare_soupx_workflow_example.py --reference-home build/literature-review/soupx-runtime/my-reference --out build/literature-review/soupx-runtime/my-example
java -cp $jar org.jlinalg.cli.SoupXWorkflowCli --request build/literature-review/soupx-runtime/my-example/request.json
```

`my-reference` must be a successful preparation from the next section; existing successful original-reference preparations also work. All outputs must be new paths. The example preserves the official toy matrices and supplied clusters, declares fixed rho 0.1 and performs cell-level subtraction. **Fixed rho is a caller assumption, not an estimated biological contamination fraction.** The original toy raw and filtered matrices are independent; the example declares this explicitly and retains the warning. No counts are changed to make them a subset.

## Prepare the separate original runtime

Supported execution profile: **Windows x86_64, R 4.6.1 (`x86_64-w64-mingw32`)**, SoupX 1.6.2 **or** 1.3.0, Seurat 5.6.0, SeuratObject 5.4.0, Matrix 1.7-5, ggplot2 4.0.3 and jsonlite 2.0.0. Supply their permitted dependencies in existing private R libraries. Other package versions and Linux upstream execution are **SKIP3**; the adapter does not launch WSL/Windows interoperability or silently select another release. Native Java components run on Windows and Linux.

Export exact source from the committed evidence, without downloading or installing anything:

```powershell
python tools/literature/export_soupx_workflow_source.py --version 1.6.2 --out build/literature-review/soupx-runtime/my-source
```

This creates `source/`, its original `SOURCE.json`, GPL-2 license and notice. Current source was obtained from CRAN; historical 1.3.0 is an official upstream GitHub source candidate. It is not identified as a paper-specific commit or historical runtime. Keep the original source separate from the Java JAR and retain its license/notices. The owned R worker is a separate GPL-2.0-or-later program communicating through files.

If local installation is permitted, install only that exported source into a **new private library**. Repeat `--dependency-library` for each existing dependency root, in resolution order:

```powershell
python tools/literature/setup_soupx_workflow_runtime.py --source-home build/literature-review/soupx-runtime/my-source --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --dependency-library 'D:/private-R/dependencies' --out build/literature-review/soupx-runtime/my-reference --allow-install
```

The helper installs no dependencies, uses no repository/network fallback and records before/after inventories of existing libraries. It checks original source function bodies/formals, exact package versions and a small original health calculation. **If installation is forbidden, omit `--allow-install`: the helper writes SKIP3 and does not launch installation.** Missing executables, dependencies or the exact profile also produce SKIP3 with named actions in `WARNINGS.md` and the logs. Obtain the permitted exact dependencies in private libraries or skip the analysis; do not patch source functions or replace a version to force success. Preparation alone admits no user analysis. Select a new source/preparation path to use 1.3.0.

## Supply a complete request

Paths resolve relative to the request JSON. The generated example is a runnable template. Change all caller choices explicitly; unknown/unused fields are refused. The required top-level fields are:

```json
{
  "schema_version": 1,
  "mode": "AMBIENT",
  "version": "1.6.2",
  "count_units": "raw-integer-counts",
  "matrix_format": "matrix-market",
  "clusters": "supplied",
  "counts_relation": "raw-subset",
  "raw_counts": "raw.mtx",
  "filtered_counts": "filtered.mtx",
  "gene_metadata": "genes.tsv",
  "droplet_metadata": "droplets.tsv",
  "cell_metadata": "cells.tsv",
  "soup_source": "../my-source/source",
  "soup_range": [0, 100],
  "analyses": [{
    "id": "fixed-example",
    "seed": 190731,
    "estimation": {"kind": "fixed", "rho_mode": "constant", "rho": 0.1, "force_accept": false},
    "adjustment": {"method": "subtraction", "clustering_scope": "cell-only", "round_to_integer": false, "tolerance": 0.001, "p_cut": 0.01}
  }],
  "out": "result",
  "rscript": "C:/Program Files/R/R-4.6.1/bin/Rscript.exe",
  "r_home": "C:/Program Files/R/R-4.6.1",
  "r_libraries": ["../my-reference/R-lib", "D:/private-R/dependencies"]
}
```

The [closed request parser](../../src/main/java/org/jlinalg/cli/SoupXWorkflowRequest.java) is the full field/range reference. A request has 1–32 analyses, unique portable case-insensitive IDs and an explicit nonnegative 32-bit seed per analysis. Every analysis supplies `adjustment` as an object or `null` for estimation only. The worker records RNG state before/after each case with Mersenne-Twister/Inversion/Rejection; historical parallel expansion is fixed to one core.

| Input | Required format and meaning |
|---|---|
| `genes.tsv` | Header `gene_id`; complete unique gene axis shared by both matrices. |
| `droplets.tsv` | Header `droplet_id`; complete unique raw droplet axis. |
| `cells.tsv` | Headers `cell_id` and `cluster` for `clusters:"supplied"`; only `cell_id` for `clusters:"none"`. No clustering or annotation is inferred. |
| Matrix Market | General coordinate integer/real matrix, exact nonnegative integer counts ≤ 2^53−1, unique coordinates including explicit zeros. No duplicate summation or identifier repair. |
| `dense-tsv` alternative | First column `gene_id`, remaining column headers exactly the declared droplet/cell axis; every gene and cell in declared order. Zeros count toward capacity. |
| `counts_relation` | `raw-subset` proves identical matched raw/filtered counts. `supplied-independent` preserves independent inputs with a prominent warning. The same raw/filtered file is allowed. |
| `soup_range` | Strict lower/upper raw total-count bounds. Selected droplets must produce a positive finite profile. Empty selection or zero profile is a failure, not a reason to widen bounds silently. |

All metadata are literal UTF-8 with no BOM/NUL/bare CR or missing fields. IDs such as `NA`, `Inf`, `TRUE`, spaces and Unicode remain literal. No cell calling is performed. **Selected profile droplets are not certified empty droplets**; IDs also present among filtered cells are retained and explicitly warned/listed in provenance. Zero-total filtered cells and nonfinite/fractional counts are refused without dropping observations. A first supplied cluster label literally `FALSE` is refused for clustered correction because the upstream branch would silently bypass clustering.

## Choose the estimator and adjustment

Each row lists the full estimator-specific fields in addition to `kind`:

| Estimator | Explicit fields and scope |
|---|---|
| `fixed` | `rho_mode:"constant",rho` or `rho_mode:"per-cell"` with a top-level `rho_by_cell` file headed `cell_id,rho` (tab-separated, exact cell order); no scalar rho for per-cell mode. Current 1.6.2 also requires `force_accept`. |
| `manual-global-poisson` | `mask_mode:"provided"` requires `gene_sets` and `nonexpression_mask`. `mask_mode:"estimate"` requires `gene_sets`, `clustering_scope`, `maximum_contamination` and `fdr`. Current requires `force_accept`; historical requires `cell_specific:false`. The original iterative GLM, confidence limits and warnings are retained. Cell-specific STAN is outside this profile. |
| `automatic-gamma` (1.6.2) | `marker_selection:"original-quickMarkers-defaults",tfidf_min,soup_quantile,maximum_markers,contamination_range,rho_max_fdr,prior_rho,prior_rho_sd,force_accept`. Runs original markers, non-expression selection and gamma estimation. |
| `automatic-legacy-density` (1.3.0) | `marker_selection:"original-quickMarkers-defaults",number_markers,tfidf_min,soup_min,rho_max,fdr,minimum_counts,density,apply_density_mode`. `density` explicitly supplies `bandwidth,adjust,kernel,n,cut,ext,old_coordinates`. Original return is a data frame, not a channel. `apply_density_mode:true` is required before correction; no implicit return-type translation. |

`gene_sets` has headers `gene_set,gene_id` (tabs), first-appearance group order and no repeated member within a group; shared members across groups are retained. `nonexpression_mask` has headers `cell_id,gene_set,non_expressing`, complete **cell-major, then gene-set order**, with literal `true`/`false`. No omitted rows or automatic reorder. Unused optional files/parameters are refused. Historical requests refuse current-only `force_accept`.

An adjustment selects `method` from `subtraction`, `soupOnly`, `multinomial`; `clustering_scope` is `cell-only` or `supplied-clusters`. It requires `round_to_integer`, `tolerance` and `p_cut`. The unchanged source performs estimation/correction. Fractional corrected values are retained; integer rounding is stochastic with the declared seed. **Do not silently round a corrected matrix for DESeq2 or infer how a paper converted fractions.** Current high-rho acceptance requires the explicit `force_accept` choice; original refusals stay failures.

P145 explicitly reports `tfidfMin=.95`, `soupQuantile=.6`, `priorRho=.1`, `contaminationRange=c(.05,.8)` and `adjustCounts()`. Other settings are not established by that paper. P087's human ALB/manual-annotation role and mouse Csf1r/1.6.2 role remain separate; supplied manual masks cannot be reconstructed from an automatic run. P104 establishes secondary ambient validation, not use of corrected counts. P008 establishes marker ranking only. P182 reports 1.3.0; P225 does not identify a version; P233 reports 1.6.2. Consult the catalog's primary/supporting paragraphs before attributing a workflow to a paper.

## Outputs, verification and limits

An admitted case has complete original RDS objects, chunked JSON with 17-digit numeric tokens and missing/nonfinite kinds, literal TSV metadata, and `corrected-counts.mtx` with 17-digit coordinates when adjustment was requested. Every table cell and sparse coordinate is checked across JSON and TSV/Matrix Market; axes, complete inputs, profiles, fixed rho, masks, GLM response/offset, automatic gene/cluster diagnostics, marker statistics and the recorded posterior-grid selection are audited in Java. All corrected entries must be finite, nonnegative and no greater than the observed counts. Original nonfinite metadata/confidence bounds and diagnostics remain visible; they are not replaced.

**The native analytical Poisson ratio is a diagnostic, not a substitute for the original iterative GLM.** Source estimates differ from the analytical limit by about 7.20e-10 (estimated masks) and 2.59e-10 (provided masks) in the preserved controls. The adapter retains original rho and score/iteration information. It does not independently refit gamma posteriors, density estimates, confidence intervals, corrections or RNG; exact complete corrected matrices are compared against separate original calls.

Validation includes 30 portable checks per JVM on Windows Java 21/25 and Linux Java 21; 14 Windows Java 21 runtime protocols with 38 complete corrected-matrix comparisons; two Windows Java 25 original executions with four comparisons; two literal/shared-file/no-cluster comparisons; and one documented example with 3,422 exact corrected coordinates. Semantic tampering of coherent outputs is refused. Missing/wrong runtime/source profiles, late original failure, high-rho refusal, resource exhaustion, worker growth and timeout are exercised, with atomic admission and owned-process cleanup. Setup validation exports both exact source trees, installs privately, prepares examples and exercises an unenabled-install skip. Read [the sealed warnings](soupx-workflow-development/WARNINGS.md) and run `python tools/literature/soupx_workflow_evidence.py` for the portable evidence check.

Requests have explicit limits for input bytes/axes/stored entries, intermediate entries, chunk size, output files/bytes, JSON size/nodes, native audit operations and timeout. Their defaults/ranges are in the request parser; effective values are captured. Dense gene×cluster diagnostics must fit the declared capacity. **Capacity exhaustion is FAIL2; retry with permitted capacity/heap and new paths without removing genes, cells or counts.** Worker timeouts stop owned processes and retain cleanup receipts/partial output. No failed atomic result is admitted. A dependency skip never silently becomes another tool/version. This development workflow is not a release, full SoupX package implementation, full-paper reproduction or completion of the 250-paper objective. Cell Ranger remains excluded by request.
