# Monocle 3 original-runtime readiness checkpoint

This research checkpoint prepares an original runtime for a future Java workflow adapter. It provides no new root CLI command and awards no Monocle catalog integration credit. Use the separately validated [native prepared-graph API](MONOCLE3_PSEUDOTIME_DEVELOPMENT.md) and [its executed example](../../examples/monocle3-pseudotime/README.md) when you already have the required projected graph.

The exact original Monocle 3 **1.4.27** source was installed privately with R 4.6.1, a private Rtools45/GCC 14.3 compiler, the author-published BPCells 0.3.1 Windows binary, and Monocle's declared `cole-trapnell-lab/speedglm` 0.3-4 fork. Source/binary archives, hashes, dependency records, compiler checks and setup receipts are retained under `build/literature-review/monocle-runtime/`. Existing R libraries were not updated. These are current compatible dependencies, not a reconstructed historical environment or authenticated compilation attestation.

`reference-setup-1` retains the failed 1.3.1 setup. `reference-setup-2` installed 1.4.27 and passed the original constructor health check. `reference-setup-3` repeats installation from a fresh manifest-bound source stage, avoiding cached object files. All original source member bytes and archives were preserved. The first directory-based 1.4.27 install created three object/DLL files beside the intake source; those files are retained as generated diagnostics and are not declared original source members.

## Original scientific probe

`probe_monocle_current_v2.R` creates 80 genes by 160 cells of seeded synthetic raw counts and explicit cell/gene metadata. It runs the original constructor, PCA preprocessing, UMAP, Leiden clustering, principal-graph learning, cell-root pseudotime, quasipoisson gene models, coefficient extraction and principal-graph Moran tests. The saved worker source records every numerical option, formula and seed. The selected root is a declared synthetic cell, not an inferred biological origin.

The completed `current-readiness-probe-2` produced a 12-vertex/11-edge principal graph, finite pseudotime for all 160 cells, 80 model rows, 240 coefficient rows and 80 graph-test rows. Every returned coefficient and graph-test row had original status `OK`. The original full save/load path preserved pseudotime and cell order exactly, including the required nearest-neighbor index files. These are runtime/source controls on synthetic data; they do not certify biological conclusions or any paper pipeline.

`fitted-native-audit-2` exports the actual fitted projected graph and compares it with the new Java API. All 160 cell values and 172 projected graph vertex values agree exactly on Windows Java 21, Windows Java 25 and Linux Java 21. This comparison replays the original prepared-graph ordering stage; it does not independently fit PCA, UMAP, clusters, gene models or Moran statistics in Java. The original full-package fitting was performed on Windows only.

For a fresh private readiness run after the recorded source/dependency setup, use the project Python runtime with:

```text
python tools/literature/probe_monocle_current.py --setup build/literature-review/monocle-runtime/reference-setup-3 --out build/literature-review/monocle-runtime/current-readiness-probe-NEW
```

The output directory must not exist. This fixed synthetic probe is development evidence, not an interface for user expression analyses. Retain all attempts and their warnings/errors. Java execution supervision, typed user inputs, complete report validation, broader independent scientific controls and paper-specific integration remain open work.

## Prominent warnings and recovery

**SKIP3 — original 1.3.1 runtime analysis is skipped under the attempted R 4.6.1 profile.** Required archived `grr` 0.9.5 fails to compile because `STRING_PTR` is unavailable. Preserve `reference-setup-1`. Required action: provision and independently validate a compatible R/dependency environment for 1.3.1, or explicitly choose a separately validated newer profile. Never silently patch the dependency or substitute editions. The separately installed 1.4.27 profile does not establish 1.3.1 parity.

**Current-profile compatibility warning:** original model fitting emitted the message that `subbars` moved to the `reformulas` package. The warning is retained in the probe's `RESULT.json`; no source function was patched to suppress it. Inspect this dependency warning when admitting a future workflow. Constructor/installation success alone is insufficient for scientific admission.

**Installation message:** both 1.4.27 installations also recorded `no definition for class "IterableMatrix"` during lazy-load method registration. The probes explicitly load the declared BPCells dependency before Monocle and completed the tested sparse-count workflows and full save/load checks. Retain the installer message and that load order. Disk-backed `IterableMatrix` inputs have not been validated here; do not infer their availability from these sparse-count probes or from an empty R warning list.

**Saved-state restriction:** the first readiness probe used RDS checkpoints that omitted nearest-neighbor indices, as the original package warned. Those files are retained for inspection and are not complete saved Monocle objects. The corrected probe uses original `save_monocle_objects`/`load_monocle_objects` with complete index files. Preserve the entire saved directory; do not copy only `cds_object.rds` and claim resumability.

**Report schema restriction:** original coefficient tables contain `model` and `model_summary` list columns. Their complete original frame is preserved in `coefficients.rds`; the TSV contains only declared atomic columns, with `coefficients-column-types.json` identifying the omitted columns. The graph-test TSV uses R's explicit row-name convention: its first data column holds gene row names while the header lists only data fields. Read it with the declared R convention (`read.table(..., header=TRUE, row.names=1, sep="\t")`) or the strict audit reader, and retain those row names. A future user-facing Java report must provide an explicit identifier heading. Do not shift scientific columns silently or treat the TSV as the complete original model object.

**FAIL2 — preserve a failed probe and admit no partial workflow result.** The first probe stopped at the TSV writer's unsupported list columns; the first fitted-graph audit stopped at an incorrect generic TSV reader. Both attempts remain retained. Use the corrected worker/audit and a NEW output directory. Original source/model values were preserved; the corrected transport does not rewrite their statistical meaning.

Cell Ranger remains excluded. Full-package parity, Monocle 2 differential testing, the custom paper `graph_test`, Cicero, `py-monocle`, unknown historical settings and the full 250-paper toolbox objective are not certified by this checkpoint.
