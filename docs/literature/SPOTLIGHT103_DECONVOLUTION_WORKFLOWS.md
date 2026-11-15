# Original SPOTlight 1.0.3 deconvolution

**Warnings and required actions:** this workflow runs unchanged original R/C++ code. A recorded return does not certify NMF convergence or biological accuracy. In this pinned source, `model="ns"` and `model="std"` use the same NMF routine, `min_prop` is checked but does not filter proportions, and extra arguments can be accepted without being used. Inspect the original diagnostics and validate your analysis. Do not interpret these arguments as guarantees. Original zero-signal spots can return `NaN`; these values remain unchanged.

**SKIP3 skips the dependent analysis** when a named exact runtime, source, Linux execution, network isolation or permitted installation/download prerequisite is unavailable. Supply that permitted prerequisite and retry with NEW paths. **FAIL2 admits no complete result**, including an original error, native crash, changed input, missing completion marker or recording failure after the original function returned. Read `WARNINGS.md`, `FAILURE.json`, `original/R-status.json`, complete original conditions/RNG and both raw consoles. Resolve the stated cause, preserve all original and partial records, and retry NEW paths. Only the final public runner `COMPLETED.json` admits the recorded operation.

The adapter is a separate Python entry point under `tools/literature/spotlight103/`; it has no Java root CLI dispatch and earns no native Java credit. It supports complete `SPOTlight`, `trainNMF` and `runDeconvolution` calls, plus an explicitly composed `train-deconvolve` operation. Plotting, Seurat and SpatialExperiment inputs are not qualified by this comparison profile. Synthetic matrix, sparse, delayed and SingleCellExperiment controls establish bounded software behavior, not biological performance.

## Source identity and paper role

P051, [Architecture of the neutrophil compartment](https://doi.org/10.1038/s41586-025-09807-0), reports SPOTlight 1.0.3 at source paragraphs 114 and 119. It describes seeded NMF spatial deconvolution using a LungMap single-cell reference in the first context and another described single-cell reference in the PDAC/MI context. Both label spots at predicted neutrophil composition **>=10%**. These analyses remain separate from SingleR annotation and celldex reference retrieval. Exact source commit, input objects, marker lists, settings, seed and environment used by the authors are not established.

The target is the complete [original upstream commit `0a85246f93167f869de704166627fe6679fe03b5`](https://github.com/MarcElosua/SPOTlight/tree/0a85246f93167f869de704166627fe6679fe03b5), whose DESCRIPTION declares 1.0.3 and GPL-3. Its archive SHA-256 is `cc694e5326846563658738d6c6b5095c6ba65cbeeaa12eaddf24fa95423d484a` (581,749 bytes). This is an explicit project source selection matching the reported version, not proof of the authors' commit. The attempted Bioconductor 3.15 1.0.3 archive URL returned 404; that release's documentation lists 1.0.0. No fallback edition is substituted.

The target includes its own C++ NMF/NNLS code, attributed upstream to RcppML/NNLM. The source is not rewritten, recompiled from generated replacement algorithms or replaced by a newer SPOTlight package. `source-profile.json` pins every original regular member. All installed target R function formals/bodies and the active native-library path are audited. Complete dependency prefix and private library inventories are checked before and after execution. The lock selects an explicit **R 4.4.3** comparison environment, not a recovered author runtime. Original GenomeInfoDbData 1.2.13 is built separately to satisfy the comparison environment's annotation dependency.

## Install

Run from the repository root in permitted Linux/WSL. Supply an existing permitted Pixi executable, Python 3, writable NEW output paths and enough disk space. The installer builds unchanged sources in a private library; analysis never installs software or downloads references.

```sh
python3 -B tools/literature/spotlight103/install_runtime.py \
  --manager /permitted/pixi --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-spotlight-install
```

The resulting provider is `/private/NEW-spotlight-install-provider`. For offline installation, omit `--allow-download`, supply a complete locked package cache and `--source-cache /permitted/exact-source-cache`. That cache must contain the exact `SPOTlight-git-1.0.3.tar.gz` and `GenomeInfoDbData_1.2.13.tar.gz` files declared in the profile. Missing exact files cause SKIP3; no release is substituted. Native compilation requires the locked compiler dependencies and permitted operating-system facilities. Do not run a dependent analysis if installation skipped or failed.

## Construct a trusted request

Use the installed R and its private library. Save the following constructor in your own `prepare-request.R`, with your original inputs loaded explicitly. Run it with the installed profile:

```sh
R_LIBS_USER=/private/NEW-spotlight-install/library \
R_LIBS_SITE=/private/NEW-spotlight-install/runtime/.pixi/envs/default/lib/R/library \
  /private/NEW-spotlight-install/runtime/.pixi/envs/default/bin/Rscript \
  --vanilla /trusted/prepare-request.R
```

Supply your own trusted original expression objects, marker table, groups and intended RNG state. RDS objects may contain executable behavior; this interface is not a sandbox for untrusted objects.

```r
RNGkind("Mersenne-Twister", "Inversion", "Rejection")
set.seed(3103) # Your explicit intended seed, not an inferred paper setting.
request <- list(
  schema=1L, edition="1.0.3", operation="SPOTlight",
  arguments=list(x=single_cell_counts, y=spatial_counts,
    groups=cell_type_labels, mgs=marker_table,
    gene_id="gene", group_id="cluster", weight_id="weight",
    verbose=TRUE, threads=1L),
  rng=list(kind=RNGkind(), seed=.Random.seed),
  options=list(), caller_packages=character())
saveRDS(request, "/trusted/NEW-request.rds", version=3)
```

Rows of the expression matrices are genes, columns are single cells or mixture spots, and marker/group identifiers must match your intended data. Keep original identifiers and ordering. The adapter does not repair IDs, fill missing values, infer labels or align biological references. Original input errors remain errors. The original functions require sufficient shared features; unsupported or missing input classes may fail.

For `trainNMF`, `arguments$y` is the intended character vector of mixture gene names. For `runDeconvolution`, supply the intended original `mod` and `ref` along with the mixture `x`. For `operation="train-deconvolve"`, use `arguments=list(training=<original trainNMF arguments>, deconvolution=<original runDeconvolution arguments without mod/ref>)`. The original model and topic profiles are passed directly between the two complete calls; the intermediate training return is retained. No different algorithm is introduced.

```sh
python3 -B tools/literature/spotlight103/run.py \
  --runtime /private/NEW-spotlight-install-provider \
  --request /trusted/NEW-request.rds --out /results/NEW-deconvolution
```

The child runs in an actual offline network namespace. If that facility is not permitted, SKIP3 skips analysis. Windows users must run the original runtime through permitted Linux/WSL; invoking the runner in Windows Python skips without an R child. The default time limit is 3600 seconds; choose `--wall-seconds` explicitly when needed. A timeout retains partial records and admits no result.

## Read the result and keep the complete records

For a successful `SPOTlight` request, `original/complete-original-return.rds` contains the original `mat`, `res_ss` and `NMF` components, with all original fields and attributes. Inspect every original warning, both consoles, source audits, request snapshot, full condition objects/calls and RNG before/after. `R-status.json` and `MANIFEST.json` describe recording and integrity, not convergence or cell-type correctness. Original R warnings are also printed and included prominently in `WARNINGS.md`. Failed calls preserve full conditions, native traces and available RNG without returning an empty substitute result.

The paper's >=10% annotation is a separate downstream choice. Do not implement it by assuming `min_prop=0.1` filters this source's output. After verifying a final completion marker, explicitly select the correct neutrophil column from your own reference and retain the original result alongside any derived labels. Unknown author column choices are not guessed. All-zero spots' `NaN` values require an explicit analysis decision; they are never silently recoded.

## Verification scope

Fresh comparisons are separate from saved-record integrity checks. `MakeRequests.R` creates project synthetic fixtures, not author data. The validation driver launches direct original exports independently of the production worker, restores the same explicit RNG, and compares complete returned R objects, attributes, intermediate models, RNG, condition class/message/call and native origin. Rcpp native backtraces contain ASLR addresses and wrapper-specific frames: both complete raw traces are retained; those process-specific bytes are not required to be equal. No numerical result or original diagnostic is modified to make a comparison pass.

```sh
python3 -B tools/literature/spotlight103_validation/controls.py \
  --runtime /private/NEW-spotlight-install-provider \
  --out /validation/NEW-controls --jobs 2
```

The fixtures cover dense/sparse/delayed/SCE inputs, automatic-label warnings and warning-as-error behavior, literal marker-column names, S4 marker tables, reordered genes, factor groups, marker selection, regularization, complete training and deconvolution, zero-signal `NaN`, unapplied arguments, an iteration-limited return and original input/native errors. Matching errors never count as successful deconvolution. Public failure guards additionally test real child timeouts, postreturn recording failure, a changed captured request, missing final status, unchanged existing outputs and installation without permission. Owned fault injections affect only captured copies or recording behavior, never upstream algorithms.

Whole-tool validity, model convergence, biological accuracy, original reference recovery, paper reproduction and the complete 250-paper integration goal remain unqualified. Cell Ranger/ARC remain excluded by user request.
