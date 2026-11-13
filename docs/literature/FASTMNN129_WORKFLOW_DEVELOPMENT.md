# Original FastMNN 1.29.0 workflow

**Qualified standalone development workflow.** The exact selected provider
passed 19 original controls: 17 complete successful cases and two preserved
original weight failures. Every successful model, metadata file and exported
numeric population matched its independent original reference. Portable
qualification passed 133 checks across Windows JDK21/25 and Linux JDK21,
plus seven actual setup refusals. Read the complete evidence and limitations
below. This component does not claim paper reproduction or a new catalog badge.

The standalone workflow calls original Batchelor 1.29.0 `fastMNN`. It accepts
complete prepared log-expression or signed asinh-protein matrices, with
explicit batch labels, PCA settings, feature selection, restrictions, weights
and merge order. The original SingleCellExperiment, including its complete
lazy LowRankMatrix assay and metadata, is retained. There is no Rphenograph
annotation, automatic marker selection, library-size normalization, log/asinh
conversion or inferred paper setting. Requested upstream cosine normalization
remains part of `fastMNN`.

P040 reports FastMNN method version `1.29` for imaging mass cytometry.
Bioconductor's official Git source supplies a 1.29.0 candidate at
`165c212df5312de6da18dc0a2b78929beaffc812`. This does not recover the authors'
patch, commit, correction call or handling of Ki-67. Their linked notebook
imports Batchelor but does not provide the correction call. Its license is
undeclared; author code was neither executed nor redistributed.

This is distinct from P051's selected Monocle 1.3.7/Batchelor 1.20 `reducedMNN`
workflow. Read [the Batchelor role guide](BATCHELOR_INTEGRATION.md). Neither
function substitutes for the other.

## Prepare the runtime

Original analysis requires Linux, including permitted WSL with Linux Java and
Linux paths. The selected tested dependency profile uses R 4.5.3. It is a
custom source build satisfying the package's declared imports; it does not
claim Bioconductor's recommended R 4.6 development environment or historical
paper environment. All 105 installed named Batchelor source functions are
checked against the pristine source, with 51 namespace versions recorded.
The JAR bundles no R runtime or upstream executable.

Provision only where downloads and installations are permitted. The helper
supports a complete existing exact dependency base, or a new private base
from the supplied manifest/lock. Run from the project root on Linux:

```bash
python3 tools/literature/fastmnn129/setup.py --help

# Fresh private dependencies and original source; supply a permitted Pixi.
python3 tools/literature/fastmnn129/setup.py \
  --provision-base --pixi /absolute/path/pixi \
  --allow-download --allow-install \
  --out /absolute/path/NEW-fastmnn-runtime

# Reuse a complete exact base and a local pristine source donor.
python3 tools/literature/fastmnn129/setup.py \
  --base /absolute/path/exact-dependency-base \
  --source-root /absolute/path/pristine-batchelor129-source \
  --allow-install --out /absolute/path/NEW-fastmnn-runtime
```

Use the `rscript`, `libraries` and `source_root` paths returned in `RESULT.json`
for requests. Namespace readiness is not a completed analysis. Compilation
uses a separate working tree and preserves the pristine original source.
Existing environments and output directories are not overwritten.

The source profile pins all 90 raw Git blobs, with LF text bytes, at the
selected official commit. The helper requests that exact archive encoding.
It rejects altered or CRLF-converted donors without normalizing them. Use
the provider-generated pristine `source` path, not its compiler working tree.
The earlier CRLF capture and failed fresh-install attempt are retained with
a complete source-encoding correspondence proof.

**SKIP3 — skip dependent analysis if the required runtime, download,
installation or execution is unavailable or forbidden.** Retain the cause
and `WARNINGS_FAILURE.md`. Supply the named exact prerequisites on a permitted
host, or leave that analysis skipped. Analysis installs nothing.

## Inputs and request

Use UTF-8 TSV or real/integer general coordinate Matrix Market. TSV matrices
have a `feature_id` header followed by literal cell IDs, with one feature per
row. Feature metadata has `feature_id` and `feature_name`; cell metadata has
exactly `cell_id` and `batch`, in the same complete cell order as the matrix.
All identifiers remain literal. Duplicate axes/coordinates, missing or
nonfinite numeric values, incomplete populations and unexpected columns fail.
Matrix Market coordinates are one-based and are not implicitly summed.

`prepared-asinh-protein` permits signed values. `prepared-log-expression`
also requires caller-prepared finite values. Units and preparation provenance
are declarations; they are not inferred from values. No raw-count conversion,
normalization or marker filtering is silently performed.

The request below uses absolute Linux paths. Replace runtime paths with the
actual fields in `RESULT.json`; paths inside a request may also be relative
to that request's directory.

```json
{
  "schema_version": 1,
  "version": "1.29.0",
  "matrix": "/absolute/path/matrix.tsv",
  "cells": "/absolute/path/cells.tsv",
  "features": "/absolute/path/features.tsv",
  "format": "TSV",
  "input_units": "prepared-asinh-protein",
  "input_provenance": "Describe assay preparation, transformation and QC",
  "analysis_population": "Describe the complete supplied feature/cell population",
  "batch_order": ["run-C", "run A", "run-B"],
  "seed": 20161231,
  "parameters": {
    "k": 20,
    "dimensions": 50,
    "cosine_normalization": true,
    "pca_method": "IRLBA",
    "neighbor_method": "KMKNN",
    "workers": 1
  },
  "reconstruction_audit": "FACTORS",
  "execution_policy": "ALLOW",
  "rscript": "/absolute/path/runtime/dependency-base/.pixi/envs/default/bin/Rscript",
  "r_libraries": [
    "/absolute/path/runtime/R-lib",
    "/absolute/path/runtime/dependency-base/.pixi/envs/default/lib/R/library"
  ],
  "source_root": "/absolute/path/runtime/source",
  "out": "/absolute/path/NEW-result"
}
```

```text
python tools/literature/fastmnn129/build.py --java-bin /absolute/path/jdk21/bin --out /absolute/path/NEW-build
java -jar /absolute/path/NEW-build/fastmnn129-workflow-development.jar --help
java -Xmx2g -jar /absolute/path/NEW-build/fastmnn129-workflow-development.jar --request /absolute/path/request.json
```

Select the standalone development class/JAR explicitly. Its entry point is
`org.jlinalg.cli.FastMnn129WorkflowCli`. The existing release executable and
root dispatcher do not expose this new workflow.

## Method choices

Absent method fields use the recorded original defaults: `k=20`,
`prop_k=null`, `cosine_normalization=true`, `ndist=3`, `dimensions=50`,
`deferred=true`, `weights=null`, `get_variance=false`, `merge_order=null`,
`auto_merge=false`, `min_batch_skip=0`, `subset_features=null`,
`correct_all=false`, `restrict_cells=null`, IRLBA PCA, KMKNN neighbors and one
worker. EXACT PCA and EXHAUSTIVE neighbors are also exposed. Other upstream
backends/constructors are not qualified by this typed interface.

- `dimensions: null` passes original `d=NA` and omits PCA. It does not omit
  the rest of the original correction algorithm.
- `deferred: null` preserves original `deferred=NULL`, letting the PCA
  parameter object determine deferred centering/scaling.
- `subset_features` selects literal feature IDs in the supplied order.
  `correct_all` controls whether the original output includes other features.
- `restrict_cells` limits cells used for correction estimation. Original
  output retains the complete cell population; this is not output filtering.
- `merge_order` uses every one-based batch index exactly once, as a linear
  sequence or nested binary tree. Indices refer to `batch_order`.
  `auto_merge` selects original automatic ordering; original code ignores
  `merge_order` when automatic ordering is enabled, and a warning records it.
- `workers` selects SerialParam or original MulticoreParam, up to 64 workers.
  The seed is explicit. It is not a recovered author seed.

**Weighting warning:** this original single-matrix path indexes numeric
weights by batch names. An unnamed numeric vector can therefore produce NA
centers and fail. The controller preserves that behavior and admits no result;
it does not add names or switch algorithms. Both observed IRLBA and exact-SVD
failures are retained. Changing the PCA backend alone is not a demonstrated
fix for unnamed weights.

Choose a representation explicitly:

```json
"weights": null
"weights": true
"weights": false
"weights": [1, 2, 3]
"weights": {"batch_weights": {"run-C": 1, "run A": 2, "run-B": 3}}
"weights": {"tree": [[1, 2], 3]}
```

These are alternatives, not one JSON object. Null/true use original equal
batch contributions; false leaves larger batches with larger contributions.
The array remains unnamed. The explicit dictionary supplies named values
ordered by `batch_order`; a weighting tree scales contributions by hierarchy.
The weight tree and merge tree control different operations. Do not silently
replace a failed unnamed-vector request with a named one.

## Complete outputs and checking modes

Require exit 0 and `analysis-status.json` with `result_admitted=true`, then
read `WARNINGS.md`. File presence alone does not admit a result.

- `original-fit.rds` preserves the complete original SingleCellExperiment,
  including the lazy reconstructed assay, all cell/feature axes and metadata.
- `metadata.rds` retains the complete original MNN pairings, merge diagnostics
  and PCA metadata. `metadata.txt` is a readable preview, not a replacement.
- `corrected.tsv` is cells by corrected dimensions; `rotation.tsv` is features
  by dimensions. `assay-components.tsv` and `assay-rotation.tsv` independently
  expose the original LowRankMatrixSeed factors for complete comparisons.
- `worker-result.json` contains metadata and axes, with bulk matrices in TSV.
  Captured inputs, pristine sources, runtime provenance, process receipts and
  original warnings remain alongside the model.

**FACTORS**, the default, checks every original seed factor against the
complete corrected/rotation factors and preserves the original lazy assay.
It avoids an unnecessary cells-by-features allocation. It does **not** claim
that every unmaterialized reconstructed entry has been numerically evaluated
or found finite.

**ALL_VALUES** additionally exports `reconstructed-cells.tsv` as **cells by
features**, using original lazy-assay extraction in bounded cell blocks.
Native compensated dot products check every entry against rotation/corrected
factors, with an explicit floating-point bound. This mode can require large
disk space and work proportional to cells × features × dimensions. No prefix
or sampled comparison substitutes for the complete check.

Dense and sparse original calls may differ in floating-point details even
with the same values. Input representation is recorded. Independent controls
must match the representation and materialization strategy; unrelated dense
references must not be relabeled as sparse parity.

Portable auditing needs Java, the saved metadata and all sibling factor TSVs;
ALL_VALUES also needs the complete reconstructed-cells TSV. It reads the
source directory rather than duplicating potentially large matrix files:

```text
java -jar fastmnn129-workflow-development.jar --audit-result /absolute/path/result/worker-result.json --out /absolute/path/NEW-audit
```

Optional `--maximum-native-operations` and `--maximum-table-bytes` set declared
audit capacities. A portable audit admits no original analysis, reloads no
RDS, refits no MNN model and authenticates no producer.

## Replay the complete evidence

The public evidence directory is
[fastmnn129-workflow-development](fastmnn129-workflow-development/README.md).
It retains complete synthetic inputs, all original successful models and
metadata, full exported matrices, both original weight failures, source
fingerprints, installation diagnostics and platform-control receipts. The
content-addressed ZIP deduplicates identical files; it does not sample them.

The verifier restores the complete archive, compares all 17 complete
successful models and numeric populations with the independent originals,
rebuilds the adapter, and tests the packaged native interface. It does not
reload RDS or repeat original R fitting. Run from the project root, using a
NEW output directory:

```bash
python3 tools/literature/fastmnn129/verify.py \
  --java-bin /absolute/path/jdk21/bin --out /absolute/path/NEW-replay
```

On Windows, run the same Python script with your installed Python3 and
Windows JDK21/25 paths. Original R analysis remains a Linux operation; the
saved-result checks run on Windows or Linux. Optional `--runtime-request`
adds five invalid-input refusal checks using an existing exact Linux
provider. Those checks launch no original analysis and install nothing.

## Failures, capacities and limitations

**FAIL2 — no result is admitted.** Preserve complete inputs, original errors,
partial tables, `worker-failure.json`, process logs, status, warnings and any
unadmitted RDS. Correct the named source, input, numeric, timeout or capacity
cause and retry a NEW directory. Do not discard cells/features, impute,
rename identifiers or repair nonfinite values to obtain success.

Requests expose input/output byte limits, nonzero/coordinate entry limits,
metadata JSON size, native-operation limits, original process timeout,
address space and sampled RSS. Output limits include captured inputs and
provenance. Java heap is separate; choose `-Xmx` and declared capacities
appropriate to the complete population. Input TSV validation streams values;
Matrix Market duplicate checking stores one long coordinate key per entry.
FACTORS uses row-wise comparisons. ALL_VALUES retains the rotation and one
cell row rather than a full reconstructed matrix. Large input content hashes
are checked before and after execution, with identity/size/timestamp monitoring
during execution. These are integrity checks, not producer authentication.

Use the recorded private R environment and load its classes/packages before
reading model RDS files, as the worker does. A separate ad hoc inspection
using on-demand namespace loading emitted stack-imbalance warnings; that
transcript is retained and is not a qualified analysis. Raw stderr was empty in
all17 qualified successful workers and the lazy-factor default control.
Captured original method warnings remain in the result metadata and human
warnings file. Do not ignore stack-imbalance warnings if they appear in
another environment or operation.

The [original Batchelor package](https://bioconductor.org/packages/devel/bioc/html/batchelor.html)
declares GPL-3. This project uses GPL-3.0-or-later. The evidence directory
retains source credits and the project license. Cell Ranger remains excluded at the user's request.
Paper-wide semantic completeness and the broader toolbox goal remain open.
