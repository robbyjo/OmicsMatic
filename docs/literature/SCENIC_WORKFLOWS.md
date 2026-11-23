# SCENIC network inference, motif pruning and activity

`scenic-tools` combines original GRNBoost2 or GENIE3 inference, original SCENIC
coexpression/motif functions, and the project's native Java pySCENIC AUCell
scorer. It records a fit outcome for every target and a pruning outcome for every
module/database pair. Fitting and activity matrices can contain different cells
and genes; neither is silently sampled, filtered or normalized.

The supported profile is
`pyscenic-0.12.1-arboreto-0.1.6-ctxcore-0.2.0-ledger-v1`: Linux x86_64,
Python 3.10.22, pySCENIC 0.12.1, Arboreto 0.1.6, ctxcore 0.2.0, NumPy 1.23.5,
pandas 1.5.3, scikit-learn 1.1.3 and the remaining pinned dependencies. This
profile establishes neither an earlier pySCENIC edition nor an author's
unreported seed, preprocessing, cell membership or database edition.

## Start with current source and a permitted runtime

Use a current source-built OmicsMatic JAR and Java 21. Older release assets can
lack this command or predate the process-ownership fix. Keep an analysis's exact
producer JAR: VERIFY/REPLAY reject a replacement JAR even with the same filename
and project version. Native cell scoring itself is Java; complete GRN/CTX runs
need the original Linux runtime. On Windows, run the full command inside WSL
using Linux Java and Linux paths. Saved-capture `--inspect` also works on Windows
Java 21/25 without starting Python.

The exact dependency lock is
[requirements.lock](../../tools/literature/scenic_workflow/requirements.lock).
Its hashes come from the resolved upstream distributions; the packaged
[source profile](../../tools/literature/scenic_workflow/SOURCE-PROFILE.json)
also checks the selected installed Python source bytes before import. Supply a
permitted `uv` executable and choose a **new directory under an existing parent**.
The tested installation pattern is:

```bash
(
  set -eu
  SCENIC_PREFIX=/absolute/new/scenic-runtime
  test ! -e "$SCENIC_PREFIX"
  mkdir "$SCENIC_PREFIX"
  export UV_CACHE_DIR="$SCENIC_PREFIX/cache"
  export UV_LINK_MODE=copy
  export UV_PYTHON_INSTALL_DIR="$SCENIC_PREFIX/python"
  export UV_PYTHON_BIN_DIR="$SCENIC_PREFIX/python-bin"
  uv python install 3.10.22
  SCENIC_PYTHON="$(uv python find 3.10.22 --managed-python)"
  "$SCENIC_PYTHON" -m venv --without-pip --copies "$SCENIC_PREFIX/environment"
  uv pip sync tools/literature/scenic_workflow/requirements.lock \
    --python "$SCENIC_PREFIX/environment/bin/python" --require-hashes
  "$SCENIC_PREFIX/environment/bin/python" -B -c \
    'import pyscenic.aucell,pyscenic.prune,arboreto.core,ctxcore.recovery'
)
```

The lock deliberately includes setuptools 68.2.2 for the legacy `pkg_resources`
import. A newer unrestricted dependency installation is not this profile. If a
required installation/download is prohibited or unavailable, keep the dependent
analysis **SKIP3**, identify the missing prerequisite and supply an allowed
matching runtime later. Never patch the original scientific source to make an
import succeed. Retain failed installation logs and retry into new directories.

## Prepare explicit local inputs

Expression files are complete UTF-8, cell-by-gene TSVs:

```text
cell	TF1	GENE_A	GENE_B
cell-1	3.0	2.0	0.0
cell-2	1.0	0.0	4.0
```

Use literal tabs and a final newline. The first header is `cell`; all remaining
headers are unique gene labels. Cell labels are unique. Labels remain strings,
including `NA` and leading zeros. Expression and TF labels are not trimmed,
case-folded or repaired. Numeric entries are FP64 decimal text, `NaN`, `Infinity`,
`+Infinity` or `-Infinity`; shorthand `inf`, blank values and decimal overflow
are refused. Nonfinite fitting values can make the original regressor fail;
they are not imputed. Activity NaN values follow the source's bottom-rank rule.
All-zero or identical expression can still produce positive ranked activity;
numeric eligibility does not establish cell QC or biological validity.

Provide these other inputs:

- A unique TF list, one literal name per line, without a header. The original
  expression-column intersection and every absent requested TF are recorded.
- One or more explicitly named Feather-v2 **motifs-by-genes ranking** databases.
  Each motif row must be a complete int16/int32 permutation of zero through
  `number_of_database_genes-1`. Species, assembly, gene-ID namespace and reference
  edition are the caller's choices. Input bytes are copied and hashed; captures
  use standard filenames by role. Ranks are validated and never repaired.
- A motif-annotation TSV for the original `load_motif_annotations` parser, with
  columns `#motif_id`, `gene_name`, `motif_similarity_qvalue`,
  `orthologous_identity` and `description`. Its pandas parsing and annotation
  filters follow the original source; they are separate from the strict
  expression/TF text reader. Preserve the reference's original provenance.

The workflow uses gene-based rankings. It does not infer an assembly or convert
region-based references, gene IDs, old Feather editions or preprocessing choices.
It does not download changing reference defaults during analysis.

## Run a prepared analysis

Save the following as `run.json`, replace the runtime/reference paths, and keep
it beside the prepared matrices. Relative paths resolve from this JSON file.
The output parent must exist, and `scenic-run` must be new.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "engine": "upstream-and-native",
  "profile": "pyscenic-0.12.1-arboreto-0.1.6-ctxcore-0.2.0-ledger-v1",
  "operation": "NETWORK",
  "python": "/absolute/new/scenic-runtime/environment/bin/python",
  "out": "scenic-run",
  "inputs": {
    "fit_expression": "fit.tsv",
    "activity_expression": "activity.tsv",
    "tfs": "tfs.txt",
    "annotations": "motifs.tbl",
    "databases": [{
      "name": "explicit-species-assembly-reference-edition",
      "file": "reference.genes_vs_motifs.rankings.feather"
    }]
  },
  "options": {
    "method": "GRNBOOST2",
    "seed": 17,
    "workers": 2,
    "modules": {"min_genes": 20},
    "ctx": {"rank_threshold": 1500, "auc_threshold": 0.05, "nes_threshold": 3.0},
    "activity": {"auc_threshold": 0.05, "no_weights": false, "normalize": false}
  },
  "limits": {"threads": 2},
  "policies": {"allow_empty_regulons": false, "allow_unreportable_activity": false}
}
```

```bash
java -Xmx4g -jar /path/to/omicsmatic-0.4.0.jar scenic-tools --request run.json
```

An explicit uint32 seed is required. `GENIE3` selects the original 1,000-tree
random forest; `GRNBOOST2` selects the original stochastic gradient-boosting
parameters and early-stop window. Source target-fit metadata distinguishes a
successful fit with no positive links from a retry fallback with no fitted model.
A missing fit prevents complete network admission; targets are never omitted
silently. BLAS/JIT/Arrow thread pools stay one per Python process, and `workers`
must fit the declared `limits.threads` budget.

Default module parameters are thresholds `[0.75,0.90]`, top targets `[50]`, top
regulators `[5,10,50]`, `min_genes=20`, `absolute_thresholds=false`,
`rho_dichotomize=true`, `keep_only_activating=true`, `rho_threshold=0.03` and
`rho_mask_dropouts=false`. Empty heuristic arrays disable that heuristic; at
least one must remain enabled. Repressing modules and dropout masking are
explicit choices, not inferred from a paper or data matrix.

CTX also accepts `motif_similarity_fdr=0.001`,
`orthologuous_identity_threshold=0.0` (the original API spelling),
`weighted_recovery=false` and `filter_for_annotation=true`. Its rank threshold
must be below the database gene count. The native activity seed defaults to the
explicit fitting seed and can be set separately. `SERIAL_UNSTACK` sorts cell and
regulon axes by Unicode scalar order; `MULTIPROCESS_INPUT_ORDER` retains their
input order. Optional normalization divides by the maximum across **all** cells.

## Read the result and warnings

Start with `WARNINGS.md` and `analysis-status.json`. A completed capture can have
no scorable regulons or explicitly allowed unreportable fields. `results_admitted`
indicates that some numeric activity is eligible; **each field still requires its
own eligibility flag**. It is not a biological validation flag.

| File | Meaning |
| --- | --- |
| `worker/out/target-ledger.jsonl` | Every original target, fit outcome, estimator count, warnings and retry logs |
| `worker/out/ctx-ledger.jsonl` | Every module/database pair, source mapping omissions and original errors |
| `worker/out/adjacencies.json` | Complete typed source TF–target importance frame |
| `worker/out/modules.jsonl` | One complete original coexpression object per line |
| `worker/out/motif-enrichment.json` | Full typed source enrichment, annotation, contexts and leading targets |
| `worker/out/regulons.jsonl` | One complete original regulon per line, including ordered gene weights |
| `activity/scores.tsv` | Native activity in the selected source axis order |
| `activity/eligibility.bin` | One byte per score in TSV row/column order: 1 eligible, 0 unreportable |
| `activity/diagnostics.json` | Coverage, cutoff, maxima, weight and normalization diagnostics per regulon |
| `activity/ranks.bin` | Big-endian int32 zero-based ranks; axes/permutation in `rank-axes.json` |
| `activity/raw-scores.bin` | Big-endian FP64 raw scores, with input cell order in `input-cell-axis.json` |

The original CTX mapping rule omits a module when its **missing fraction is at
least 20%**; exactly 80% mapped is omitted there. Native AUCell uses its distinct
source rule, which admits at least 80% signature coverage. Do not conflate these
two boundaries. Source placeholders, empty recovery windows, negative-weight
diagnostics and zero-max normalization are retained with explicit eligibility.
Legitimate finite zero activity is a value, not evidence of biological absence.

An original CTX `MemoryError` can be logged and converted to an empty frame by
the source function. This controller records that error and refuses complete
activity admission. Raw import warnings, target/module warnings and consoles
remain available; deprecations are not patched away.

**SKIP3:** the dependent analysis was skipped because an exact permitted
prerequisite could not run. Supply the named runtime/input or keep the skip.
**FAIL2:** no complete result was admitted. Read the cause and required action at
the top of `WARNINGS.md`, retain raw/failed/partial outputs, fix the stated cause
and retry a **new** output directory. Hashes and identifiers must not be edited
to make a failed capture appear complete.

## Recompute or replay

VERIFY and REPLAY both make a new capture, rerun original GRN/CTX and native
activity, and compare complete declared scientific exports. They preserve the
physical parent capture. Save either request in a new JSON file:

```json
{"schema_version":1,"mode":"VERIFY","analysis":"scenic-run","out":"verify-witness"}
```

```json
{"schema_version":1,"mode":"REPLAY","analysis":"scenic-run","out":"replay-run"}
```

Run it through the same `scenic-tools --request` command. The new output must be
disjoint from the parent. Both modes require the exact producer JAR and recorded
runtime closure at its original physical paths; an optional explicit `python`
path does not waive those checks. Transfer the complete private runtime/managed
Python and necessary OS dependencies before retiring a machine. A GitHub clone
or a copied venv alone is insufficient.

```bash
java -jar /path/to/the-exact-producer.jar scenic-tools --inspect scenic-run
```

`--inspect` checks saved closure, protocol/source bindings and declared field
outcomes; it does **not** repeat the statistics or inspect the current Python
installation. In the controls, resealed one-ULP scientific changes pass mere
closure checks but fail full fresh VERIFY. Use VERIFY for scientific recomputation.
SHA-256 supplies integrity metadata, not a signature or proof of execution.

## Resource bounds and method scope

Activity is processed in bounded batches (default maximum 128 cells), preserving
one shared seeded gene permutation, global axis order and global normalization.
The batch size also respects declared numeric workspace/entry bounds. Models
are exported as JSON Lines so a large module set is not one giant parsed JSON
array. Every cell and signature is still included; batching is not downsampling.

Defaults include 32 million fitting dense entries, 32 million activity output
entries, 4 GiB estimated numeric workspace, 2 billion conservative GRN/CTX work
units, 2 billion native work units, 8 GiB per captured member, 16 GiB combined
inputs, 32 GiB complete output and 7,200 seconds. `maximum_activity_batch_cells`
can be at most 8,192. The fitting work refusal reports
`cells*genes*matching_TFs*maximum_trees`; it is a conservative work bound, not a
runtime prediction. Large paper-sized matrices require explicit capacity choices.
Limits never trigger automatic data reduction.

Worker deadlines close the owned root and observed children by process identity.
Native math uses an owned interruptible calculation thread. Hashing/copying,
cooperative cancellation and process settling can extend elapsed time. These
controls are not an OS job/RAM quota, host authentication or a guarantee against
unobserved/transient changes restored between observations. Python-audited
external socket connections are refused; this is not an OS network namespace.

This workflow covers prepared GRN/CTX/activity. Cell QC, preprocessing, RSS,
binarization, clustering, Wilcoxon tests, heatmaps, SCENIC+, author-environment
reproduction and biological inference are separate analyses. The existing
generic deterministic-tie AUCell remains separate from
[source-specific native pySCENIC AUCell](PYSCENIC_AUCELL_COMPONENT.md).

Original sources: [pySCENIC 0.12.1](https://github.com/aertslab/pySCENIC/tree/ce41b61b6570490949bd12b514e9f6de46d19c1f),
[ctxcore 0.2.0](https://github.com/aertslab/ctxcore/tree/e68a0b168b9511efb3140c5cf1dd2b07bbd811cc),
and the [official Arboreto 0.1.6 distribution](https://pypi.org/project/arboreto/0.1.6/).
Original GPL/NumPy notices remain packaged with the native component; the exact
Arboreto BSD license and source/worker notice accompany this profile.
