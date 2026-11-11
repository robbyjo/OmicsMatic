# Original CellChat 1.0.0 and 2.1.2 RNA inference

**Development checkpoint. Read `WARNINGS.md` before using any result.** The
standalone Java controller runs the selected original CellChat package in a
private Windows R 4.6.1 environment. It accepts prepared, nonnegative,
externally log-normalized expression and fits each declared condition separately.
It exports the original objects, complete ligand–receptor probability arrays,
pathway arrays, communication tables, aggregate networks and centrality measures.

This checkpoint does not add CellChat catalog credit. The 250-paper catalog
remains at **93 new scoped integrations: 19 native Java methods and 74 upstream
adapters; 96 including three existing adapters; 248 missing tools**. CellChat has
several paper-specific roles, and their combined review remains unfinished.
The canonical OmicsMatic executable is unchanged.

## Failures, skipped analyses and required actions

| Outcome | Meaning | Required action |
| --- | --- | --- |
| Exit 0, `result_admitted=true`, successful `--verify` | A complete result passed this controller's source and transport checks. | Read `WARNINGS.md`, original stderr, source/runtime identities and all relevant tables. Admission does not establish biological validity. |
| `SKIP3` / exit 3 | The exact permitted runtime or dependency is unavailable. | **Skip dependent analysis.** Install the named prerequisite only where permitted, export a fresh profile and retry with a new output directory. If installation is forbidden, leave the analysis skipped. |
| `FAIL2` / exit 2 | Input, source, runtime, process, capacity or result checks failed. | **Admit no result, including earlier condition fits.** Preserve the attempt, fix the named cause and retry with new paths. |
| Original statistical or centrality warning | The source package returned a warning, nonfinite value, out-of-range p-value or fallback zero. | Preserve it and its cause. A fallback zero is not an estimated centrality; an invalid p-value is not evidence. |

Analysis never downloads or installs software. An explicit setup command is
required first. Setup success alone does not admit an analysis. The controller
prints the warning path and required actions prominently to stderr, including
when an error occurs before an output directory can be created. After an owned
R-process timeout, that process is terminated and the incomplete attempt remains.
Declared capacities are logical checks, not hard operating-system RAM/disk quotas.

## Quick start

Run commands from the repository root. These are development artifacts, separate
from the released command line. Use Java 17 or later; Java 21 is the tested
Windows execution environment. Original R execution also requires the exact
inventoried Windows R 4.6.1 installation and dependencies. Linux supports saved
bundle verification, not execution of this Windows runtime profile.

1. Complete the [original CellChat setup](CELLCHAT_WORKFLOW_DEVELOPMENT.md),
   including its source intake, private dependencies and compiler prerequisites.
   Do not change shared libraries or substitute another package edition.
2. Install the selected unchanged edition into a new private library. Installation
   must be permitted in your operating environment. For example:

   ```powershell
   python tools/literature/setup_cellchat_editions.py --source-label 2.1.2 --allow-install --out build/literature-review/cellchat-runtime/edition-2.1.2-next
   python tools/literature/export_cellchat_inference_runtime.py --setup build/literature-review/cellchat-runtime/edition-2.1.2-next --out build/literature-review/cellchat-runtime/inference-runtime-2.1.2-next
   ```

   Use `1.0.0` and fresh corresponding directories for the older edition.
   Without authorized installation, setup records a skip and dependent analysis
   must remain skipped. Missing prerequisites are named in the setup warnings.
3. Copy the [2.1.2 example](cellchat-inference-development/examples/2.1.2/request.template.json)
   or [1.0.0 example](cellchat-inference-development/examples/1.0.0/request.template.json)
   and both TSV files into your own working directory. In the copied JSON,
   replace `out` with a **new absolute directory path** and `runtime_profile`
   with the **absolute path** to the freshly exported edition-specific profile.
   Relative input paths resolve against the JSON's directory. These examples
   contain synthetic prepared data, not paper data or independent biological
   replicates; the templates require these two path edits before execution.
4. Run the controller and then verify the complete output:

   ```powershell
   java -Xmx2g -jar docs/literature/cellchat-inference-development/cellchat-inference-javac21.jar --request C:/work/cellchat/request.json
   java -Xmx2g -jar docs/literature/cellchat-inference-development/cellchat-inference-javac21.jar --verify C:/work/cellchat/result-new
   ```

   Use the output path you chose in step 3. Check both exit codes and read
   `WARNINGS.md` even after success. Larger inputs may require more heap and
   explicitly larger capacity settings; never truncate the PPI to fit memory.

## Input and source behavior

Expression TSV starts with `gene_id`, followed by literal ordered cell IDs.
Metadata has exactly `cell_id`, `group`, `sample`, `condition`, in that order,
with one row per expression column in the same order. Supply explicit group,
sample and condition orders. No identifier repair, species conversion,
normalization, imputation, sample independence or historical environment is
inferred. Sample labels are retained; this controller does not compare replicates.
See [CellChatInferenceRequest.java](../../src/main/java/org/jlinalg/cli/CellChatInferenceRequest.java)
for the complete explicit schema and parameter bounds.

| Choice | Original 1.0.0 | Original 2.1.2 |
| --- | --- | --- |
| Database categories | One category, or all three original categories meaning the unchanged full database | Explicit supported categories through original `subsetDB` |
| Partial multi-category selection | Refused: original `subsetDB` uses vector-recycling equality | Supported by original membership selection |
| PPI function/slot | `projectData` / `data.project` | `smoothData` / `data.smooth` |
| Communication filter | Optional; explicit `min.cells` only | Optional; explicit `min.cells`, `min.samples`, `rare.keep` |
| `filter: null` | Do not call the filter | Do not call the filter; required for the inspected P100 author recipe |
| Centrality | Nine metrics; LR scores are unmasked by significance | Eleven metrics; LR scores with p-value at least the threshold are masked |
| Overexpression p-value columns | Original raw `pvalues` only | Original raw and adjusted columns |

`raw_use=false` requires an explicit projection. `raw_use=true` consumes the
unprojected signaling expression even when a projection is also requested.
Database and PPI species are independently explicit; a mismatch produces a
prominent warning and performs no homolog mapping. No modern fast/slow parameter
is invented for editions that lack it. Original `<= min.cells` filtering,
pathway `<=` versus aggregate `<` significance bounds and fallback causes remain.

Native Java checks the full original PPI projection using the existing
[CellChatProjection](CELLCHAT_PROJECTION_DEVELOPMENT.md) solver. The complete
human network has 4,815 genes. Lossless row-major little-endian float64 transport
uses 185,473,800 bytes instead of the earlier approximately 487 MB padded text
decode. Complete axes, byte count, EOF, nonnegative finite weights and independent
original-resource hashes are checked. No unmeasured network genes are removed.
All included cell columns share one native matrix factorization because the
projection operator is the same. Original R fitting still runs separately for
each condition; normalization, selection, probabilities and bootstrap inference
are never pooled by that native optimization.

## Paper roles and limits

P090 ([paper](https://doi.org/10.1038/s41588-024-01952-y), PMC11631752) specifies
CellChat 1.0 and R 4.0.3 with defaults. The pinned upstream 1.0.0 source is a
tested candidate, not a recovered historical commit. **R 4.0.3 compatibility and
the paper's exact database/PPI choice have not been reproduced.** Its inspected
public code pointer covers the main GWAS; it does not establish the missing
CellChat recipe. The full human database/PPI controls here are explicit test
settings, not inferred paper settings.

P100 ([paper](https://doi.org/10.1038/s41588-025-02407-8), PMC12695644) specifies
CellChat 2.1.2 before FlowSig. Its
[immutable author recipe](https://github.com/OMIC-coding/Spatial_multiomics_analysis_MASLD/blob/85767ada483db0d326477b7b3d5d8d355426664b/single_cell_analysis/cellchat.R)
fits CTRL, MASL and MASH separately, retains sample labels, uses human Secreted
Signaling, smooths on `PPI.human`, sets `raw.use=FALSE` and
`population.size=FALSE`, and does not call `filterCommunication`. Those choices
are represented explicitly in the 2.1.2 controls. The author installer is
unversioned, so its script is not evidence of an exact installed commit.
Raw author code was inspected privately and neither executed nor redistributed;
the capsule retains immutable metadata and verified facts. **FlowSig and its
downstream differential tests are outside this checkpoint.**

The [1.1.3 comparison checkpoint](CELLCHAT_LEGACY_WORKFLOW_DEVELOPMENT.md),
[2.2 workflow checkpoint](CELLCHAT_WORKFLOW_DEVELOPMENT.md) and database-only
roles remain separately scoped. P145's incoming/outgoing LR/pathway comparisons
and replicate tests remain pending. No whole-paper or whole-goal completion is
claimed.

## Evidence

The [validation index](cellchat-inference-development/VALIDATION.json) binds both
isolated compiler builds, original source resources, complete admitted controls,
separate original-R reference fits, portable native checks, admission failures,
earlier unsuccessful attempts and their diagnostics. Independent original-R
refits test agreement with the selected source edition; native Java does not
independently reimplement its Wilcoxon/bootstrap or all graph estimators.
Saved-bundle `--verify` checks hashes, populations and native transport consistency;
it neither reruns R nor authenticates a producer.

Earlier 1.0.0 prototypes that used a recycled multi-category vector are preserved
and explicitly superseded in `paper-source-addendum.json`. They are not the
full-database default-family controls. Only the six complete bundles named in
the current validation index support this checkpoint. Earlier failed admissions
remain failed, including attempts that produced some original R fits.

```powershell
python tools/literature/verify_cellchat_inference_evidence.py --check-current-source
python tools/literature/verify_cellchat_inference_evidence.py --materialize evidence/inference-controls-1.0.0-javac21-3/analysis --out build/literature-review/cellchat-runtime/inference-materialized-next
```

Materialization requires a new directory and grants no analysis admission by
itself. Private installed libraries/compiler and unlicensed author-code contents
are excluded; exact source resources and setup inventories remain traceable.
