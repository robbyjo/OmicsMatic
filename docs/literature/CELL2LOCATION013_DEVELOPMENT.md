# Original cell2location 0.1.3: development workflow

**This component qualification adds no catalog credit by itself.** See the [current250-paper catalog](omics-2023-2026/README.md) for admission and coverage. The full 250-paper review and implementation goal remains open. The new source entry points execute original cell2location 0.1.3 in a separate, pinned Linux/WSL CPU environment. They are Python programs, not commands in the released JAR. The canonical executable is unchanged.

The complete synthetic example has run through reference regression, spatial abundance fitting, posterior export, official save/load, both NMF factor settings and all requested wrapper plots. Its 684 result files are retained. All 647 scientific arrays, containing 65,212 elements, matched a direct upstream run at tolerance zero. Complete histories, model/parameter states, AnnData contents, all four NMF models and caller RNG states are included in the comparison. All 41 Linux workflow controls, the Windows execution refusal, seven monitor controls, seven Linux setup refusals and three Windows setup refusals passed. The frozen-evidence gate and five deliberate verifier refusals also passed. Catalog admission is a separate step; these controls do not establish independent statistical validity or paper reproduction.

## Install only where permitted

Run these commands in a permitted **Linux/WSL x86-64 shell**, from the project root. Use NEW paths every time. The setup creates a private Python 3.9.25 interpreter and environment; it does not change a system Python or create global Python executable links. Downloads include the official CPU PyTorch wheel and the original cell2location source archive. Package versions are pinned separately from metadata exposed by setuptools' bundled dependencies.

```sh
python3 tools/literature/cell2location013/install.py \
  --allow-install --allow-download \
  --prefix build/my-new-cell2location-runtime \
  --out build/my-new-cell2location-setup
```

If download or installation is forbidden, omit the corresponding permission flag: setup reports **SKIP3**, retains its warning/error records and skips dependent analysis. An existing permitted provider can be used directly. Setup itself fits no model. `provider/READY.json` records source/runtime readiness; it is not an analysis result.

Review the complete setup console, `WARNINGS.md`, `PROCESSES.json`, individual command logs and `provider/READY.json`. If setup fails, retain the partial prefix and evidence directory, correct the named requirement and retry NEW paths. Do not reuse or delete a failed prefix to make a later run appear clean. Original package notices remain separate; cell2location's Apache-2.0 notice is retained in the source folder and original archive.

## Run the complete example

```sh
python3 tools/literature/cell2location013/make_example.py \
  --provider build/my-new-cell2location-setup/provider \
  --out build/my-new-cell2location-example

python3 tools/literature/cell2location013/run.py \
  --request build/my-new-cell2location-example/request.json
```

The example uses all 48 reference cells, 16 spatial locations, 24 genes and three literal cell types. It retains nonalphabetic category order, Unicode labels and a gene named `NA`. Its 32 reference epochs, 64 spatial epochs and 20 posterior samples are short integration controls. **They do not establish convergence or biological accuracy and must not be presented as an author's settings.** The separately exercised original gene filter is not applied to the fitted population. Both NMF factor settings, 2 and 3, with two restarts each, remain in the complete result.

For a real analysis, supply your exact trusted Python script, complete input files, every expected result file, scientific provenance/population and literal required original symbols/dependency versions in a request. The example request shows the schema. `input_directory`, `result_directory`, `diagnostic_directory`, `analysis_population` and `original_cell2location` are available to the script. Set the exact requested `analysis_complete` marker only after the entire declared population finishes. Scientific identifiers are not renamed, trimmed or converted from literal `NA` to missing data. Preserve literal IDs when reading exported TSV files as well, for example with pandas `keep_default_na=False`.

Trusted scripts run with account permissions. Owned process-group supervision and declared worker wall-time/file/workspace limits are not an OS sandbox or total CPU/RSS enclosure. Separate sessions created by caller code are outside the stated process-group boundary. Analysis installs nothing and never substitutes another edition or a generic statistical method.

## Warnings, errors and required actions

**Read `analysis/WARNINGS.md` and `analysis/analysis-status.json` before using results.** Also retain and review full `process.stdout` / `process.stderr`, `diagnostics/conditions.json`, `diagnostics/worker-status.json`, `SUPERVISOR_ERROR.txt`, `WORKER_DIAGNOSTIC_ERROR.txt` when present, all complete/partial original objects and `MANIFEST.json`.

- **SKIP3:** the exact permitted runtime, provider, input, symbol, distribution, installation/download permission or execution policy is unavailable. Skip the dependent analysis, supply only permitted prerequisites and retry NEW paths. Native Windows execution skips; use the Linux/WSL profile.
- **FAIL2:** no complete result is admitted. Preserve every original object, partial output, raw worker report, warning/error and full console. Correct the actual cause and retry a NEW output without omitting failed cells, genes, locations, factor settings or cases.
- **Unknown condition totals:** interrupted, missing or malformed worker reports produce JSON `null` totals, `condition_counts_complete: false` and `warning_review_required: true`. Unknown totals are unavailable, never zero. Raw reports remain unchanged even after the original process exits zero.
- **Unwritable records:** the controller attempts warning, status and inventory records independently and prints named failures and required actions to its console. Keep that console. An unwritable status or incomplete inventory cannot constitute complete evidence.

The complete example intentionally retains upstream warnings: headless `Agg` cannot show an interactive figure; the original training setup reports absent validation steps and a logging interval longer than its single batch; NMF stability diagnostics emit invalid-division warnings. The graph comparison found no nonfinite retained result arrays, which does not make an undefined intermediate diagnostic informative. Inspect the original plots and conditions rather than silently replacing diagnostic values or dropping the factor setting. The CPU profile can also retain JAX's warning about an available NVIDIA device without CUDA-enabled jaxlib; it establishes CPU execution, not GPU execution. Original scanpy figure-saving log records are retained in both the console and structured conditions.

`original_warnings` counts warnings delivered through Python's original warning machinery. A warning promoted to an exception remains an original error; the worker does not change warning filters to suppress it. `original_log_conditions` records original warning/error logging dispatches while preserving default handlers. Structural completion does not infer biological eligibility, convergence, historical author settings or correctness of a caller's scientific script.

## Six individually read primary-source roles

Every exact cached primary XML identity in the fixed 250-paper cohort was checked. Nine named paragraphs support six individual readings; three result/figure contexts remain distinct from the six methods anchors. Candidate readings and their paragraph hashes are generated by:

```sh
python3 tools/literature/cell2location013/paper-readings.py \
  --xml-directory build/literature-review/xml \
  --out build/my-new-cell2location-source-readings
```

| Paper | Established use and limits |
| --- | --- |
| [P005](https://doi.org/10.1038/s41586-025-09887-y) | Maps spinal-cord snRNA-seq subclusters to spatial data: reported N=12, detection alpha=200 and 40,000 epochs; abundance feeds a separate sklearn NMF call. Edition, count layer and the exact stage/API interpretation of printed covariate shorthand remain unresolved. Earlier PCA/UMAP normalization is not assigned to the cell2location inputs. |
| [P008](https://doi.org/10.1038/s41586-025-09875-2) | Fits negative-binomial reference signatures for the greater-than-10-PCW female/male scRNA-seq views and decomposes Visium counts into cell-type abundance. Edition, filtering and fitting settings are unreported here. |
| [P070](https://doi.org/10.1038/s41586-023-06806-x) | Fits developing-limb reference signatures, retains 13,763 genes, maps all slides jointly using sample ID covariates and uses the cell2location NMF wrapper. A fraction above 0.2 defines downstream microenvironment membership. Edition and several fitting settings remain unknown. |
| [P096](https://doi.org/10.1038/s41588-024-01990-6) | Reports **0.7a0**, 75 reference populations, N=7, alpha=20, 30,000 mapping iterations, posterior q05 and NMF-selected 15 compartments. **The reported edition is not qualified by this 0.1.3 runtime; its dependent historical-edition analysis remains skipped.** The paper reports raw reference counts, but the author script's input filename contains `log_normalised`; a filename cannot establish the actual matrix transformation. The reported factor range is 5–30, while author Script26 uses `np.arange(5,30)`, yielding 5–29. Both are retained. |
| [P099](https://doi.org/10.1038/s41588-023-01572-y) | Aggregates about 50 transcriptomes using UMAP-space k-means, maps EasySci subtypes with reported defaults, reviews the top 10 pixels and retains scores above 1. Edition, aggregation operator, count layer and exact score field remain unknown. |
| [P100](https://doi.org/10.1038/s41588-025-02407-8) | Explicitly reports **0.1.3**: batch-aware reference signatures, separate mapping per Visium section, shared untransformed/unnormalized spatial counts excluding mitochondrial/ribosomal genes, N=8, alpha=20 and 30,000 mapping iterations. The paper reports convergence and ELBO scaling by locations × genes; the short example reproduces neither that convergence conclusion nor unreported author settings. |

For unreported editions, selecting 0.1.3 is a caller choice. Historical upstream metadata `0.07a` normalizes to `0.7a0`, but multiple commits use that label with different scvi requirements; it does not identify P096's exact environment. Its [author code](https://doi.org/10.5281/zenodo.13321265) is retained separately for inspection and was not executed on inferred or fabricated author inputs. Adjacent image alignment, segmentation, aggregation, anatomical review and downstream biological findings remain separate operations.

## Qualification evidence

Development currently retains the exact 55-member original source archive, all 44 installed package source files, 249 source declarations, 103 installed distributions, 119 import-visible distribution records including vendored metadata, actual imported module origins, every captured interpreter/environment member and loaded system-library identities. Source hashes establish integrity, not authenticity.

The first complete object recorder failed on a Python-3.9-incompatible `pathlib` argument after original fitting had returned; its console, raw models, arrays and source remain retained. A separate control reproduced the prototype logging omission before its correction. Other retained setup failures include an undeclared import prerequisite, a metadata view that mixed installed and vendored distributions, and an interpreter alias pointing to an earlier private candidate. Fresh paths preserve those failures while correcting the actual prerequisite or recorder. Original cell2location algorithms were not patched.

The current 4,658-record comparison independently captures 17 stage snapshots and preserves every recorded scientific value, literal label, category order and within-snapshot object reference. Only identified generated scvi UUIDs, exact caller output directories and original elapsed-time strings are separately recorded metadata exceptions. Raw originals remain untouched. Opaque pickle bytes, PDF bytes and generated UUID identity are not asserted equal. The comparator's negative controls refuse altered arrays, omitted NMF models, changed NMF iteration counts, altered literal `NA`, human IDs made to resemble UUIDs, invalid generated UUIDs, wrong output paths, undeclared arrays, the superseded global snapshot schema and missing originals. Runtime/warning/supervision qualification remains separate from this numerical comparison and from catalog admission. The full public installer was also used to provision a fresh private environment, fit the complete example and compare every independent-stage array with direct upstream execution.

The earlier schema-1 recorder used one evolving global object graph. A retained mutation control showed that reusing an array at a later stage could leave a reference to its earlier value; 55 cross-stage references existed in that graph. Those older 4,273-record/535-array comparisons remain retained with their narrower scope. Schema2 resets reference tracking at each top-level stage while preserving cycles/aliases inside the stage. Two fresh direct fits, the installed public adapter, the mutation control and all ten comparator refusals were rerun for the complete schema2 population. No old result, source or proof was rewritten. The 41 policy/recovery controls exercise the same byte-identical controller, worker and provider code; the independently expanded scientific-recorder evidence qualifies the new data snapshot scope.

The lossless evidence store retains30,129 logical files,2 link identities and1,299 directory identities. Its64,188,754-byte main ZIP stores2,563 unique regular-file payloads; every original logical path, size, SHA256 and available mode remains in `INDEX.json`. No scientific bytes were reencoded. The18-member auxiliary ZIP retains initial verifier failures, five refusal controls and a lossless append-delta for the deliberately damaged main archive. Publisher full-text excerpts remain in the local licensed cache; the distributable evidence contains exact source/paragraph hashes, summaries and primary links. Original uv bootstrap wheels/binaries and both upstream notices are explicitly retained; scientific interpreter/model runtime binaries remain private.

Verify without installing or fitting any model:

```sh
python3 tools/literature/cell2location013/verify.py \
  --out build/literature-review/monocle-runtime/my-new-cell2location-frozen-check
```

To inspect one exact retained file, add `--member` with its logical path from `INDEX.json`, for example `cell2location-final-example-1/example/analysis/WARNINGS.md`. The selected regular file is copied into a NEW owned directory. Link targets are retained as identities and are not followed during evidence review.
