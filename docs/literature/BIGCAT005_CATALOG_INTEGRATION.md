# BIGCAT 0.0.5 catalog integration

The existing BIGCAT uses in P058 and P059 now have selected original workflows
and a separate native Java marker-doublet component. At this checkpoint the
catalog has **116 new scoped integrations: 20 native Java subsets and 96 upstream
workflows**, or 119 including three existing adapters. **263 named families
remain missing.** The 250 papers, 438 families, 2,679 observations and 1,180
reviewed uses are unchanged; 1,499 observations await review. The full goal
remains open. This admission adds one upstream family and zero native families.

**Read the warnings below before analysis.** Scope is tied to the original
functions, explicit source profiles and tested caller choices. Generic KNN,
clustering or imputation does not establish equivalent BIGCAT behavior.

## Primary paper uses

| Paper | Reported use | What remains unknown or outside this checkpoint |
| --- | --- | --- |
| P058, [PMC10719099](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719099/), DOI 10.1038/s41586-023-06817-8 | `build_dend` with type averages of 1,871 markers; `plot_constellation` using 15 neighbours and an outside-neighbour fraction greater than 0.05; `i_harmonize` for WB10x, WB MERFISH and SPN nuclei | Package edition, exact source commit and complete caller arguments are unreported. The printed availability URL contains a space in Allen Institute; that source discrepancy remains recorded. |
| P059, [PMC10719114](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719114/), DOI 10.1038/s41586-023-06812-z | Reports version 0.0.5, `iter_clust_big`, `find_doublet_by_marker`, `select_N_markers`, `plot_constellation`, `map_cells_knn_big` and `impute_cross_big`; 50,000-cell/500-gene Parquet bins, v2/v3 DE settings, centroid cross-validation and partitioned imputation | Actual Git commit, complete caller inputs/options, gene identities for synthetic controls and manual borderline-cluster decisions are not recovered. |

P058's at-least-five-SPN rule selects integrated clusters **after integration**
at a caller-chosen hierarchy level. It is not a clustering parameter. The
optional project selection does not reproduce the later reciprocal taxonomy
correspondence or its separate exclusion of clusters with fewer than three
mapped cells. These later operations are not silently assigned to another
package.

P059 defines centroids using **all training cells**. The project workflow uses
the original Parquet summaries over those cells, followed by original index
building and mapping. It does not use the separate summary helper's 200-cell
default. The fivefold control uses all 8,460 synthetic marker genes, a declared
534-gene panel and 20 random panels of 534 genes; a separate control has 240
training cells per class. Folds, identities, expression and unreported choices
are project inputs. The original Annoy cosine index returns Euclidean distances
between L2-normalized vectors; these distances remain unchanged.

Only implementation coverage notes change in the two existing observations and
two reviewed uses. All primary identifiers, editions, settings, anchors,
uncertainties and source hashes remain unchanged.

## Installation and execution

The [workflow guide](../../tools/literature/scrattchbigcat005_workflows/README.md)
gives complete commands, trusted request examples and constructor recipes. The
[native Java guide](../../tools/literature/scrattchbigcat005/README.md) documents
the separate dense marker-doublet API. The source adapter is a Python entry
point requiring original Linux R software; it is not a bundled Java CLI command.

Choose a profile explicitly:

| Profile | Original commit | Runtime and material limits |
| --- | --- | --- |
| `prepublication-2023` | `bbfe5277de63c65a5342e43f3e1eeb1015755fc7` | R 4.2.3. All eight named functions exist. Original ordinary-matrix harmonization indexing, vector-default mapping and free-variable imputation errors remain. Explicit mapping/imputation arguments are tested separately. |
| `pre-regression-2022` | `5134c0b0a107a724f7d1f091c23a73ab691b4a66` | R 4.1.3. Original three-dataset harmonization returns with explicit caller `sets` and retained warnings. This snapshot lacks `impute_cross_big`. |

These snapshots both label the package 0.0.5. Neither is established as the
authors' actual commit. P058's edition remains unknown. No single profile is
claimed to provide successful execution of every role. Both profiles use the
declared original Arrow 12.0.1 source and exact recipe-unpatched C++ artifact;
R 4.1 builds its R Arrow package from unchanged original source. Dependency
versions and the runtime environment are project choices.

Inside permitted Linux/WSL, create a private runtime:

```sh
python3 -B tools/literature/scrattchbigcat005_workflows/install_runtime.py \
  --profile prepublication-2023 --manager /path/to/permitted/pixi \
  --allow-install --allow-download --cache-dir /path/to/permitted/cache \
  --out /path/to/NEW/bigcat-install

python3 -B tools/literature/scrattchbigcat005_workflows/run.py \
  --runtime /path/to/completed-provider --request /trusted/request.rds \
  --assets /local/protected-assets --out /path/to/NEW/analysis
```

Use the exact provider path printed by the installer. For the earlier profile,
select `pre-regression-2022`. Where downloads are forbidden, omit
`--allow-download` and supply `--source-cache /path/to/exact-source-archives`
plus the complete permitted package cache. Where installation is forbidden,
use an existing exact permitted provider or skip the dependent analysis.
Installation does not silently substitute a missing source profile. Analysis
itself never downloads or installs software. Use trusted RDS inputs; this is
not a sandbox for arbitrary R code.

## Warnings and recovery

**SKIP3 skips dependent analysis.** If a required source, dependency artifact,
permission, installation, download or Linux execution is unavailable or
forbidden, read the named cause in `WARNINGS.md`, `FAILURE.json` and raw logs.
Provide that exact permitted requirement, then retry NEW writable paths.
A skipped operation supplies no result. The earlier profile's missing
`impute_cross_big` is a prerequisite skip before analysis begins.

**FAIL2 admits no complete result**, including a recording failure after an
original operation returned. Only a final `COMPLETED.json` admits the recorded
operation. Inspect original R status, raw consoles, manifests and failure
records; a leftover RDS or successful R process is insufficient. Preserve all
inputs, complete objects/conditions, IDs, NA/NaN/Inf and available partials.
Resolve the named cause and retry NEW writable output/workspace paths. No
source repair, ID correction, silent default change or runtime fallback occurs.

Six final controls retain original failures: a missing marker gene, sparse
default-marker scoring, vector-default mapping, a singleton final cell bin,
the 2023 harmonization indexing error and free-variable imputation defaults.
An expected-error comparison passing does not admit a result for that analysis.
Inspect warnings even when a complete return exists. R handler counts do not
enumerate every native diagnostic; unavailable totals are not zero.

**Complete R objects and comparison differences remain intact.** Temporary
Annoy paths, FBM class-definition identities and cyclic plot environments can
differ between processes. Scientific arrays, complete plot data, index payloads,
FBM backing bytes and paired derived renders are checked independently. This
does not establish live R-object identity or pointer persistence. Blank source
plot pages and original warnings remain unchanged. Manual biological review,
whole-package validity and paper reproduction remain unqualified.

## Evidence and cumulative verification

The [scoped workflow qualification](scrattchbigcat005-workflow-validation/QUALIFICATION.json)
and [complete evidence guide](scrattchbigcat005-workflow-validation/README.md)
retain 25 original controls: 19 complete returns and six failures without
results, with 3,198 comparison fields. The 3,335 logical files include complete
objects, source records, both public install branches and actual installed-runtime
analyses. Fifteen PDF pairs have identical rendered pixels across 18 pages per
side. Four nonzero Parquet partitions are reconstructed independently.
Twelve policy/runtime/recording guards passed. Two unchanged controls were
accepted; 11 changed results were rejected after resealing their manifests.
The native Java component separately has 753 original-source cases; its existing
proof and source hashes remain unchanged, and it adds no second family credit.

```sh
python3 -B tools/literature/bigcat_catalog/catalog.py \
  --verify --rescan-sources \
  --runtime2023 /permitted/bigcat-2023-provider \
  --runtime2022 /permitted/bigcat-2022-provider \
  --arrow-runtime /permitted/arrow-provider \
  --mapping-runtime /permitted/mapping-provider \
  --doubletfinder-runtime /permitted/DoubletFinder-provider \
  --out build/literature-review/monocle-runtime/NEW-bigcat-catalog-check
```

On Windows add `--wsl-distro NAME`. The audit verifies all 476 cumulative
definitions and every preceding scientific gate, rescans all 250 cached primary
sources and checks the full frozen BIGCAT evidence. It reparses saved Parquet,
recomputes training means and comparisons, and renders saved plot objects; it
does not fit new BIGCAT models. Only the eight exact predecessor metadata files
may be viewed at their previous state. The exact reviewed-use inverse changes
two coverage notes; scientific source overlays are forbidden. Use an isolated
checkout and the historically qualified legacy CLI for predecessors that
require it, preserving another active development build.

The mapped-omics commit added six literal CLI help lines after an older
dispatcher review. Their exact full-file before/after identities and inverse
are reviewed separately. Some legacy modules reopen their checkout and cannot
retain a read-only input view. Cumulative checks therefore require an isolated
linked Git worktree. Within that private fixture only, the six help lines are
temporarily removed for historical gates and the exact complete current file
is restored afterward. Both full inputs and restoration receipts are retained.
The shared checkout is never modified; it is rejected before the fixture is
used. No executable byte, scientific method, runtime, prior verifier or old
hash changes. The real current source also passes the complete dispatcher
inverse chain. Both earlier rejected attempts remain retained. This literal
help-text relationship does not qualify the mapped-omics algorithms.

HICAT and PyArrow remain separate missing families. Mapping retains its prepared
reference scope and private dependency restrictions. Arrow's invalidated
same-version C++ build50 evidence remains unqualified. DoubletFinder SCT
convergence and reported 3.0 remain unresolved. Cell Ranger/ARC stay excluded
by user request. Earlier runtime/source/stack/figure limits remain recorded.
This catalog milestone is local until explicitly published; development HTML
does not establish a live-site deployment. The full 250-paper goal is incomplete.
