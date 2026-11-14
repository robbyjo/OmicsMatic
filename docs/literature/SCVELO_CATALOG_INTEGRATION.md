# scVelo: original workflows and exact paper roles

The current admission adds **one scoped upstream scVelo integration**, five
reviews of existing scVelo observations, and one separately named **missing**
velociraptor R wrapper with its own observation/review. It adds no native Java
credit. Coverage is **111 new integrations:20 native Java subsets and91 upstream
workflows;114 including three existing adapters**. The250-paper catalog has433
named families,2,672 observations,1,168 reviewed uses and1,504 awaiting review.
**263 families remain missing; the full review and implementation goal is open.**

Use the unchanged original **scVelo0.2.4 or0.2.5 Linux/WSL CPU source workflow**.
These Python entry points use a separate private environment; they are not
commands in the released JAR. The [frozen component guide](SCVELO024025_DEVELOPMENT.md)
and its candidate metadata retain the earlier component checkpoint, including
its then-pending catalog statements. This page and the [current catalog](omics-2023-2026/README.md)
describe current admission and coverage.

## Install and run

Run from the project root in a permitted Linux/WSL x86-64 shell. Use NEW paths:

```sh
python3 tools/literature/scvelo024025/install.py \
  --version 0.2.5 --allow-install --allow-download \
  --prefix "$HOME/omicsmatic-runtimes/scvelo025-new" \
  --out build/my-new-scvelo025-setup

python3 tools/literature/scvelo024025/make_example.py \
  --provider build/my-new-scvelo025-setup/provider \
  --version 0.2.5 --mode dynamical \
  --out build/my-new-scvelo025-example

python3 tools/literature/scvelo024025/run.py \
  --request build/my-new-scvelo025-example/request.json
```

For0.2.4, install a separate0.2.4 provider and use that exact requested edition.
Choose `--mode stochastic` for the stochastic/PAGA example. Original filtering,
moments, velocity, graph, pseudotime/latent time, projection and plots remain
upstream operations. Setup creates no global Python executable links and fits
no model. Analysis installs nothing. Native Windows execution skips; use the
Linux/WSL profile. Supply real analyses as explicit trusted scripts with complete
input/output declarations, provenance, populations, selected original symbols
and dependency versions. Preserve all literal IDs, category order and NaN/Inf.

## Warnings and required actions

**P070 prints0.24. Do not relabel it0.2.4.** Its exact PyPI endpoint returned
HTTP404; the dependent exact-edition analysis remains **SKIP3**. Selecting0.2.4
or0.2.5 for a separate exploration is an explicit caller choice, not recovery of
an author's environment or defaults.

**P228 uses the R velociraptor interface, which remains missing here.** Its
selected original1.20.0 source declares Python scvelo==0.3.3. R preprocessing,
SingleCellExperiment/AnnData conversion and the managed Python environment must
be qualified separately. Direct Python0.2.x execution does not qualify that
interface. Neither author edition, velocity mode nor exact latent-time call is
inferred. Its successful private dependency setup is not an analysis, and the
original Git-source configure failure remains retained. Skip dependent wrapper
analysis until its exact permitted runtime and workflow are qualified.

**Read every delivered condition and original loss history.** Both selected
scVelo editions suppress `UserWarning` at import. The worker preserves those
filters, caller-requested visibility and promoted errors. Zero delivered warnings
does not prove absence of numerical issues. Dynamical loss-history NaN padding
is retained exactly; do not replace it with zero or infer convergence from an
exit code. Original and project retention-review log records remain inspectable.

**Four earlier SVGs were overwritten and cannot be recovered.** Their original
trial remains refused; fresh figures are not presented as recovered old bytes.
**Do not rely on historical `/tmp` paths for durable evidence.** At archival
review168 original analysis temporary directories and one unit fixture were no
longer available. All13 recorded regular file copies/268,435,479 bytes remain
verified in saved bundles. Use `native-temporary/` copies and retained node
identities. Historical retention flags describe the original controller step,
not later availability. The stopped pack worker and independent complete
archive/source recovery remain separately recorded without rewriting old proofs.

**SKIP3 skips dependent analysis** when exact permitted prerequisites,
installation/download or execution are unavailable. Supply permitted requirements
and retry NEW paths. **FAIL2 admits no complete result.** Preserve every
available original object, partial file, warning/error, raw worker report and
full console; resolve the named cause without dropping cases or changing hashes.
Unknown condition counts are JSON null, never zero, with
`condition_counts_complete: false` and `warning_review_required: true`.

Before use, inspect `WARNINGS.md`, `analysis-status.json`, full
`process.stdout`/`process.stderr`, `diagnostics/conditions.json`, raw
`diagnostics/worker-status.json`, `MANIFEST.json`,
`native-temporary-inventory.json` and all supervisor/recording errors. If
recording is blocked, retain the console and resolve the named filesystem
requirement. Trusted scripts run with account permissions. Owned process-group
and capacity supervision is not an OS sandbox or total CPU/RSS enclosure.

## Individually reviewed roles

| Paper | Established use and retained limits |
| --- | --- |
| [P051](https://doi.org/10.1038/s41586-025-09807-0) | Mouse-neutrophil stochastic velocity; count threshold30,2,000 HVGs,50 neighbors/30 PCs and projection onto an existing UMAP. Edition and exact threshold keyword remain unknown. The larger control's `min_shared_counts=30` is an explicit caller choice. |
| [P070](https://doi.org/10.1038/s41586-023-06806-x) | PCW7.2 mesenchymal subset with named preprocessing, moments, stochastic velocity, graph and graph plotting. Printed0.24 and unknown default-derived values remain unresolved. |
| [P117](https://doi.org/10.1038/s41588-026-02737-1) | Explicit0.2.4 stochastic dynamics and scVelo PAGA in selected mouse-ZG clusters. Synthetic state grouping and PAGA options do not recover author settings. |
| [P161](https://doi.org/10.1038/s41588-026-02665-0) | Explicit0.2.5 dynamical velocity with30 neighbors. The separate CellRank kernel mixture, macrostates and absorption probabilities remain downstream operations. |
| [P228](https://doi.org/10.1016/j.ajhg.2025.07.020) | scVelo through R velociraptor and reported organoid latent-time comparisons. Both software editions, mode and calls remain unknown. The Velocyto name beside a velociraptor URL is a retained source/link discrepancy. The new R-wrapper observation remains missing and receives zero implementation credit. |

All250 exact XML identities were scanned. Five scVelo roles have six named
paragraphs; the wrapper has one explicit method anchor in the same P228 methods
paragraph, giving seven family-specific anchors across six distinct paragraphs.
Results context is retained separately from methods and from biological validity.

## Qualification and verification

Complete original-versus-adapter comparisons cover54 independent stages,
2,124 arrays/25,165,036 elements,2,272 result files and10 figures at tolerance
zero. Four complete examples retain160 cells/12 genes and include stochastic
PAGA or dynamical recovery/latent time. Two larger stochastic controls retain96
cells and select2,000 HVGs from2,100 genes, with50 neighbors/30 PCs. No opaque
pickle/HDF5/SVG byte parity, convergence, statistical validity or paper
reproduction is claimed. Source/runtime capture includes both complete original
namespaces and45 installed/import-visible distributions per provider.

Controls include100 Linux workflow cases, a Windows refusal,7 monitor cases,
10 setup refusals,10 native temporary cases,12 narrow mapping cases,8 portable
recorder groups,11 scientific-comparator refusals and6 archive-reader refusals.
Both public installers and four complete fresh-provider examples were exercised.
All30,983 logical files,4 links and3,205 directories remain in nine bounded ZIPs,
with explicit historical-loss and temporary-directory limits. SHA-256 establishes
integrity, not authenticity. The original statistical code was not patched.

Verify admission, exact source roles and all earlier scientific gates without
installing or fitting any model:

```sh
python3 tools/literature/scvelo_catalog/verify.py \
  --rescan-sources \
  --out build/literature-review/monocle-runtime/NEW-scvelo-catalog-check
```

Only eight exact historical catalog metadata inputs are replayed to earlier
gates. The shared review-file inverse reconstructs the exact predecessor bytes;
scientific code, inputs, results and verifier definitions remain live and
unchanged. The scVelo component verifier runs in a fresh process so older
components' Python module names cannot shadow its source-bound evidence reader.
Cell Ranger/ARC exclusions and all earlier family-specific restrictions remain.
