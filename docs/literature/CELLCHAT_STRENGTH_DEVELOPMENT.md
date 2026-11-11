# CellChat incoming and outgoing strength comparisons

**Development checkpoint. Require exit 0, `result_admitted=true`, successful
`--verify`, and review of `WARNINGS.md`.** This standalone Java controller runs
original CellChat 2.2.0 for two pooled conditions and each declared sample. It
then compares incoming and outgoing ligand–receptor (LR) and pathway strengths
per cell type, selects explicit strict differences and runs the original R
Wilcoxon rank-sum test across complete sample populations.

The earlier [2.2 workflow](CELLCHAT_WORKFLOW_DEVELOPMENT.md) remains frozen.
Its directed-edge comparison is an ancillary output here; the new node-strength
comparison supplies the distinct role described by P145
([paper](https://doi.org/10.1038/s41588-026-02709-5), PMC13553309).
The paper describes original inference, human protein signaling, pooled
`min.samples=3` / `min.cells=10`, LR/pathway centrality, a strict 0.25 strength
difference and subsequent tests across samples. It reports no custom study code.
This implements a parameterized method family, not historical or biological
reproduction of that paper.

CellChat's combined catalog review and root-command integration remain pending.
Counts remain **93 new scoped integrations: 19 native Java methods and 74
upstream adapters; 96 including existing adapters; 248 missing tools**. The
canonical executable is unchanged.

## Warnings, skipped analyses and required actions

| Outcome | What to do |
| --- | --- |
| `SKIP3` / exit 3 | **Skip dependent analysis.** Supply the named exact permitted runtime/source/library only where installation is allowed. Otherwise leave it skipped. Export a fresh profile and retry new paths. Analysis installs nothing. |
| `FAIL2` / exit 2 | **Admit no result**, including earlier successful fits in that attempt. Preserve the files, fix the named input/source/runtime/process/capacity cause and retry a new output directory. |
| Original `flowbet` / `info` fallback | Preserve the source zero and its recorded cause. It is not a successful graph estimate. These metrics do not replace the `indeg` / `outdeg` strengths used for comparison. |
| Missing feature or group | Keep its strength and comparison as `NA` with the recorded reason. It is not a measured zero. Tests requiring it remain untested. |
| Nonfinite original Wilcoxon result | Preserve `NA` / `NaN` / `Inf` literally and the `original-nonfinite-wilcox-result` status. It establishes neither an association nor a null result. |
| Below or equal to the requested cutoff | A sample test is intentionally not run. The complete table still records the contrast and sample coverage; this is an expected selection disposition. |

Read `WARNINGS.md`, `analysis-status.json`, `provenance.json`, original stderr and
the full comparison tables. The controller prints warning paths and required
actions to stderr even when failure precedes directory creation. An owned R
process is terminated after timeout; no partial batch is admitted. Logical
capacities are not hard operating-system RAM/disk quotas or host authentication.

## Run the prepared example

The [example template](cellchat-strength-development/example/request.template.json)
contains synthetic prepared expression and three control/four treated sample
labels. The fourth treated label is an explicitly perturbed copy, documented
in `INPUT-PROVENANCE.json`; these are not biological replicates or paper data.
The example uses 100 bootstrap permutations, the original default family,
human protein signaling, pooled minimum three samples/ten cells and per-sample
minimum one sample/ten cells.

1. Complete the permitted private setup and source intake in the
   [2.2 runtime guide](CELLCHAT_WORKFLOW_DEVELOPMENT.md#prepare-a-runtime-where-permitted).
   It requires Windows R 4.6.1, original CellChat source commit
   `346fb615aaefa3c1a2830ec2fe3a933c5f723c83`, Presto 1.0.0 and the inventoried
   dependency stack. A fresh checkout does not contain an installed R runtime.
   Missing or forbidden prerequisites mean dependent analysis must be skipped.
2. Copy the template, both TSV files and provenance file into your working
   directory. Replace `out` with a **new absolute directory** and
   `runtime_profile` with the **absolute path** to the exact exported 2.2 profile.
   Relative input paths resolve against the JSON's directory.
3. Run and verify using Java 17 or later:

   ```powershell
   java -Xmx1g -jar docs/literature/cellchat-strength-development/cellchat-strength-javac21.jar --request C:/work/cellchat-strength/request.json
   java -Xmx1g -jar docs/literature/cellchat-strength-development/cellchat-strength-javac21.jar --verify C:/work/cellchat-strength/result-new
   ```

   Substitute your chosen paths. Check both exit codes and the warnings. R
   inference is tested on Windows; complete saved bundles can also be verified
   on Linux without R or installation.

## Explicit settings and outputs

Expression TSV starts with `gene_id` and ordered literal cell IDs. Metadata is
exactly `cell_id`, `group`, `sample`, `condition`, with matching row/column order.
Declare the full group, sample and two-condition orders. Each sample belongs to
one condition. Inputs must be finite, nonnegative, externally log-normalized
expression. No identifier repair, normalization, imputation, species conversion
or biological independence is inferred. See
[CellChatStrengthRequest.java](../../src/main/java/org/jlinalg/cli/CellChatStrengthRequest.java)
for the complete explicit schema.

`comparison.difference_mode` must be `absolute`, `positive` or `negative`.
The signed delta is treated minus control. All three strict threshold flags are
always exported; equality does not pass. P145's wording alone does not establish
the sign convention, so the example's explicit absolute choice is not presented
as recovered author code. No Wilcoxon p-value cutoff or multiplicity correction
is invented.

`comparison.exact` must be explicitly `null`, `true` or `false`. Null delegates
automatic method selection to the bound R version. Alternative and continuity
correction are also explicit. Every performed test retains its actual original
method and p-value. Automatic and explicitly asymptotic controls are separate
analyses; neither silently replaces the other. For incomplete populations, the
test remains unperformed. Tests require at least two complete samples per
condition and a selected comparable pooled contrast.

The first declared condition is control (`x`) and the second is treated (`y`).
R's `less` alternative tests control below treated; `greater` tests control above
treated. The example explicitly uses the two-sided alternative.

| Output under `results/comparison/` | Content |
| --- | --- |
| `strengths-by-case.tsv` | Every declared feature/group/direction in each pooled and sample fit, including missingness reasons |
| `pooled-and-sample-strengths.tsv` | Full ordered contrasts, three cutoff flags, selection mode, sample counts/missing counts, original test fields and disposition |
| `selected-strengths.tsv` | Exact selected subset in the same order, retaining incomplete/nonfinite test dispositions |
| `strength-report.json` | Coverage, selection and original warning census |
| `guarded-original-stats.json` | Original generic/default Wilcoxon function bodies/formals before and after comparison |
| `pooled-and-sample-probabilities.tsv` | Separately retained ancillary directed LR edge comparison |

For original 2.2 LR strengths, scores at p-value at least the network threshold
are masked before centrality; pathway and aggregate bounds retain their original
source semantics. Native validation already checks these source networks and
`indeg` / `outdeg` sums. The new
[CellChatSignalingStrengths](../../src/main/java/org/jlinalg/omics/CellChatSignalingStrengths.java)
calculates strict contrasts and independently computes the control midrank sum
minus `n1*(n1+1)/2`, matching the unpaired R statistic. It does not independently
reimplement p-value distributions, overexpression/bootstrap tests or all graph
estimators. Runtime/source populations and original package functions are bound
before/after inference; complete-bundle verification checks transport consistency,
not producer authenticity or a fresh R fit.

## Evidence and limits

The [validation index](cellchat-strength-development/VALIDATION.json) binds seven
complete bundles, both compiler builds, six Windows/Linux Java configurations,
separate original default-family refits and original parameter-specific export
checks. The controls cover absent pooled features, sample-only feature omissions,
groups removed by prefiltering, all three difference modes and explicit
asymptotic nonfinite tests. The latter retain 38 original nonfinite strength-test
p-values; they are never clipped or replaced.

Earlier attempts that completed original fits but failed the first Java
map-order header check remain unadmitted. The published build tightens native
TSV header validation; its R worker and owned execution controller are byte
identical to the successful full-fit producers. A member comparison records the
single changed audit class, and the final JARs replay every complete bundle.
Private installed R libraries/compiler and primary article text are excluded
from the capsule; inventories, source hashes and factual paper notes remain.

```powershell
python tools/literature/verify_cellchat_strength_evidence.py --check-current-source
python tools/literature/verify_cellchat_strength_evidence.py --materialize evidence/strength-controls-3x4-javac21-2/analysis --out build/literature-review/cellchat-runtime/strength-materialized-next
```

Use a new materialization directory. Materialization alone admits no analysis.
The earlier [1.0.0/2.1.2](CELLCHAT_INFERENCE_DEVELOPMENT.md),
[1.1.3 comparison](CELLCHAT_LEGACY_WORKFLOW_DEVELOPMENT.md) and
[database/native calculation](CELLCHAT_DEVELOPMENT.md) checkpoints retain their
own evidence and scope. This checkpoint does not establish full-paper results,
historical package commits/environments, spatial modeling or FlowSig analysis.
