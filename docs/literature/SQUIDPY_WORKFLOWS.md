# Original Squidpy workflow adapter: development guide

The adapter calls original Squidpy1.1.2,1.2.2 or1.8.4 public functions for spatial
graphs, supplied-graph Moran/Geary statistics, ligand-receptor inference and
Nanostring reading. It captures inputs, exact options, dependency/source identities,
original exports, diagnostics and an explicit admission decision. The separate
[native Java Moran/Geary component](SQUIDPY_AUTOCORRELATION.md) remains available.

**Scoped original-runtime qualification has passed; catalog admission is separate.**
The current source routes `squidpy-tools` to the typed adapter. Existing release
JARs may predate this command. Consult the current checkpoint for catalog totals
and the exact producer/evidence packet. These workflows do not establish
whole-package or whole-paper reproduction.

## Warnings and the action to take

**SKIP3: the dependent operation is skipped.** Provide/install the exact permitted
Python, Squidpy/dependency editions or local input identified in the diagnostic.
If installation is prohibited or the input is unavailable, retain the skip. The
adapter will not silently run the native component as an alternative analysis.

**FAIL2: no complete result is admitted.** Keep the original and partial exports,
warnings and failed journal. Correct the stated input, numerical, capacity,
protocol or runtime problem and retry with a **NEW output directory**. Read
`FAILURE.json`, `analysis-status.json`, `WARNINGS.md`, the original warning records
and complete worker logs. A successful Python API return can still fail later
export/admission checks. Never use a completion claim from `worker/report.json`:
that file describes the original API-return phase. Final admission belongs to
`analysis-status.json` and `COMPLETED.json`.

Old source failures on retained fixtures remain original failures. In particular,
1.1.2/1.2.2 spectral transformation and1.1.2 raw-expression gene subsetting have
specific original exceptions. Preserve their evidence and choose settings or a
different edition explicitly, with appropriate provenance. Do not patch the
original installed functions or silently upgrade a reported paper edition.

## Runtime and commands

Actual original-runtime validation uses Linux Java/Python in WSL. Windows
Java21/25 validation covers packaged component tests and saved-bundle verification;
it does not establish Windows-native Squidpy analysis. On Windows, run the original
workflow inside a suitable Linux/WSL environment and use Linux absolute paths in
its request. A Windows JVM cannot execute a declared Linux Python path directly.

Private installation is allowed under the user's existing authorization where
the operating environment permits it. Use isolated prefixes and pinned official
packages. Keep managed interpreter bases when a virtual environment links to them.
The tested patches are Python3.8.20/3.10.22/3.12.3 with Squidpy1.1.2/1.2.2/1.8.4.
P009 reports Python3.8.13;3.8.20 is an explicitly different patch, and the authors'
complete environments have not been reproduced.

For a JAR built from the development source, substitute its actual path:

```text
java -jar PATH_TO_JAR squidpy-tools --request request.json
java -jar PATH_TO_JAR squidpy-tools --verify CAPTURE_DIRECTORY
java -jar PATH_TO_JAR squidpy-tools --help
```

Java `--verify` checks a saved capture offline with its matching producer classes
and worker/profile resources. It does not need the original Python installation
or rerun a model. Preserve exact producer JARs and source contexts for historical
captures. A newer producer may correctly refuse an older capture; never refresh
old verifier hashes to conceal that difference.

The independent reader requires Python3.9+ and NumPy:

```text
python -B tools/literature/verify_squidpy_workflow_capture.py --analysis CAPTURE_DIRECTORY --jar EXACT_PRODUCER_JAR
```

It checks saved closure, producer/source identities, recorded runtime membership,
typed frames, matrices, primitive NPY images, point/variance/normality consistency,
admission and replay claims. It does not refit upstream models, decode H5AD
transport bodies, independently interpret every opaque metadata array, or verify
that a private runtime is currently installed. Original direct-call comparisons
are separate qualification evidence. SHA256 records integrity, not outside-party
authentication or protection against coordinated evidence replacement.

## The request declares the scientific operation

A RUN request contains `schema_version:1`, `mode:"RUN"`,
`profile:"squidpy-original-workflow-v1"`, `operation`, `out`, the actual executable
`python`, `expected_python`, `expected_squidpy`, `expected_packages`, original
`options`, and the appropriate input declaration. Paths resolve relative to the
request file. `out` must be new and its physical parent must already exist.

Declare actual exact dependency editions. The required core names are `anndata`,
`numpy`, `scipy`, `pandas`, `scanpy`, `numba`, `scikit-learn`, `statsmodels` and
`omnipath`; a full installed-package inventory is also captured. These declarations
describe the chosen runtime. Do not invent a paper's missing dependency versions.
The source profile limits options to those supported by the selected edition.
The complete original bound signature/defaults are saved in `worker/invocation.json`.
The adapter owns `copy` behavior so it can retain returned frames and data objects.

| Operation | Original API and required data |
| --- | --- |
| `SPATIAL_NEIGHBORS` | `sq.gr.spatial_neighbors`; prepared coordinates and explicit KNN, radius, Delaunay or grid options. |
| `SPATIAL_AUTOCORR` | `sq.gr.spatial_autocorr`; a supplied weighted graph and declared expression/raw/layer/attribute selection. |
| `LIGREC` | `sq.gr.ligrec`; expression, a cluster column, and a captured local interaction table with provenance. |
| `READ_NANOSTRING` | `sq.read.nanostring`; a captured directory containing declared counts, metadata, optional FOV table and available image folders. Absent in1.1.2. |

Historical autocorrelation/ligand-receptor APIs use `seed`;1.8.4 uses `rng`.
The adapter does not translate between them. Squidpy1.1.2 has no neighbor
`library_key`. For that edition, supply per-sample inputs as separate explicit
analyses where the paper requires them. Coordinate units remain those of the
input; a radius is a micrometre radius only if the supplied coordinates use those
units. Make sample/library/FOV grouping explicit. In1.8.4, the original common
neighbor function is deprecated and has changed option precedence; its warnings
and original behavior are preserved.

`LIGREC` requires an `interactions_file` JSON object with unique `columns` including
`source`/`target`, rectangular scalar-valued `rows`, and `provenance`. Supply the
intended CellPhoneDB/OmniPath snapshot and annotations explicitly. The worker
denies external Python socket connections and accepts local IPC; this is not
an OS network namespace or an automatic database-retrieval service. Source
Squidpy uppercases symbols, handles complexes, filters and deduplicates pairs,
and can leave threshold-related missing probabilities. Those original
transformations and raw annotations remain recorded. The summary's
`identifier_changes` counter concerns **neighbor observation-name changes**;
it is not a count of every source ligand-receptor symbol transformation.

## Supported input representations

`prepared_json` declares a local schema1 object with `obs_names`, `var_names`, `X`,
typed observation/feature columns and optional `obsm`, `obsp`, `layers` and `raw`.
Matrices declare dense values or CSR shape/indptr/indices/data and precision.
Use explicit `NaN`/`Inf`/`-Inf` tokens where supported; literal identifier strings
remain distinct from missing objects. Column declarations preserve categorical
values/order and exact Boolean/integer/text semantics.

`h5ad` captures the original complete file. Preflight refuses soft/external HDF5
links. Genuine null datasets have no array payload and remain for the original
AnnData reader to interpret; this follows the
[HDF5 null-dataset representation](https://docs.h5py.org/en/stable/high/dataset.html#creating-and-reading-empty-or-null-datasets-and-attributes).
Categorical order flags are encoded as equivalent JSON Booleans, retaining their
value. No source file or installed library is rewritten.

`matrix_market` declares general coordinate real/integer `.mtx` or `.mtx.gz`,
`cells`/`features` TSV files, precision and `orientation` (`cells_by_genes` or
`features_by_cells`). The first TSV headers are `cell_id` and `feature_id`;
declare every additional metadata column in `obs_columns`/`var_columns`.
Duplicate coordinates that CSR conversion would combine are refused. The
orientation is explicit. This representation supplies expression and row/column
metadata; choose prepared JSON or H5AD when spatial coordinates or weighted
graphs are also required.

## Original output and admission

Graph/reader outputs include the original AnnData H5AD transport plus complete
typed observation/feature/raw metadata, MatrixMarket expression/graph/layer slots,
and original uns metadata/images. Primitive NPY arrays retain shape, descriptor,
layout and complete payload bytes, including RGB images and exact64-bit integer
bits. The Squidpy inspector supports up to16 dimensions, NPY1.0/2.0/3.0, primitive
Boolean/integer/float32/64 arrays and both byte orders, following the
[NumPy format](https://numpy.org/doc/stable/reference/generated/numpy.lib.format.html).
Other context array types remain explicitly opaque and checksummed where supported.

**Earlier development float32 MatrixMarket exports can contain NUL bytes.**
The independent comparison found this in the retained137/139 development captures;
those malformed exports are not qualification evidence. Keep their original bytes,
exact JARs and diagnostics, and rerun with the corrected producer in NEW output.
Do not strip padding from old exports. The adapter now declares9 significant
digits for binary32 and17 for binary64, preserving the original array/dtype and
testing a full round trip. The worker rejects NUL bytes in matrix inputs/exports;
Java and independent matrix validation reject unexpected control bytes explicitly.
H5AD/NPY objects and failed evidence remain retained separately.

Autocorrelation frames retain original `I`/`C`, analytic/permutation variances,
probabilities, adjusted columns and source order. Admission requires a finite
point and a defined probability in `[0,1]`. Analytic variance additionally must
exceed a source-precision64-epsilon graph-moment cancellation bound. Recorded
standard error and standardized score must agree with the original point,
expectation, variance and declared arithmetic precision; the normality probability
must agree with the source tail convention. Old-edition Geary variance conventions
remain edition-specific. Original undefined `numpy.empty` cells are retained and
withheld. An adjusted probability family containing an undefined raw probability
is withheld as a family. Finite point statistics can remain useful even when
their analytic probabilities are withheld; inspect each typed status.

Ligand-receptor means, probabilities and metadata must retain matching interaction
axes. Each interaction-by-cluster-pair cell has an explicit admitted or
not-tested/undefined probability status. Finite means do not make a missing
probability a tested result. Original NaN-handling during source FDR correction
is preserved; the adapter does not substitute a different correction procedure.

`finite_result_rows` counts feature point statistics for autocorrelation,
interaction-by-cluster-pair cells for ligand-receptor results, and observations
for graph/reader results. It is not uniformly a number of tested hypotheses.
`no_result_rows` and `withheld_probability_cells` are separate diagnostics.
Partial outcomes require predeclared `admission.allow_no_result_rows:true`;
the default refuses completion if any required probability/point is withheld.

## Resources, replay and preservation

Requests can declare `limits` and separate Boolean `admission` policies. Defaults
include100,000 cells/features,20million stored/dense entries,2GiB estimated
working memory,20billion estimated work units, one thread and a3600-second worker
deadline. Larger declared values remain bounded by the source contract. These
are admission estimates and monitored workspace/time limits, not hard aggregate
RAM quotas, OS job containment or scalability claims. Ligand-receptor work
estimates account for the original default1000 permutations, complex expansion
and interaction-by-cluster output dimensions before the original call is acknowledged.

REPLAY reruns the original API using captured input bytes and the same scientific,
runtime and policy declarations; only the new output address changes:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"PARENT_CAPTURE","out":"NEW_CAPTURE"}
```

The original runtime must still be available. Typed semantic exports are compared
with their retained parent inventories. Original H5AD transport bytes are retained
but excluded from byte-identity comparison; typed scientific exports provide the
declared comparison. Differences require a predeclared
`allow_replay_differences:true`, remain reported, and do not bypass admission.
VERIFY independently checks the saved comparison maps, Boolean claims, parent
metadata bindings and that policy. Changing settings or policies requires a new
RUN, preserving the previous result and its conditions.

Owned-worker cleanup uses captured process handles with checked birth identity.
Never rerun pre-fix historical worker producers or stop unrelated processes.
Preserve complete original/failed captures, exact producer JARs, source contexts,
locks, runtime prefixes and linked interpreter bases. The full250-paper objective
also requires the remaining high-impact tools and all pending/missed-provider
reviews; this development adapter does not complete it.
