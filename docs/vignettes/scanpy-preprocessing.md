# Native Scanpy preprocessing components

The Java API supplies explicitly selected **Scanpy 1.9.3 and 1.13.0 float64
dense/CSR profiles** for cell/gene filtering, `normalize_total`, `log1p` and
`scale`. It uses no Python process or network. It is a component integration;
general file/upstream workflows, HVGs, regression, PCA, neighbors, clustering,
embeddings, gene scoring, differential expression and catalog admission remain
open. The 22 reviewed source roles cover a broader set of tasks.

The normal strict Gradle build passed. The standalone component passed **426
checks on each of Windows Java 21/25 and Linux Java 21**, including all 418
original-reference cases and eight boundary controls. Six API example runs
passed. All 20,182 existing class payloads in the preceding normal project JAR
remain byte-identical; 13 classes are added. These checks do not qualify whole
Scanpy, historical author inputs/settings, complete papers or biology.

Read the [runnable Java example](../../examples/scanpy-native/README.md),
[saved qualification and restorer](../literature/scanpy-preprocessing-checks/README.md)
and [source-role checkpoint](../literature/SCANPY_PREPROCESSING_CHECKPOINT_2026-11-19.md).
The public 0.4.0 release predates this API. A current source build or the exact
restored development component is required. The small component is a library;
it has no `java -jar` command.

## Inputs and output contracts

Use the shared immutable `SeuratPreprocessing.Matrix` container: features by
cells in CSC storage. It is the transpose view of Scanpy cells by genes in
CSR storage, so pointers, indices and data need no numerical conversion.
`Storage.CSR` retains sparse coordinates and explicitly stored zeros through
normalization/log transforms. `Storage.DENSE` requires every entry, including
zeros, explicitly stored in cell-major order. Exact unique identities and
their order are retained; no name mapping, missing-value repair or dtype
guessing occurs. Input values must be finite. Normalize/filter require
nonnegative counts or abundances; scaling/log1p also accept signed values.

This API evaluates **float64** inputs. Scanpy's integer-to-float32 casting,
float32 arithmetic, backed/Dask/GPU representations, AnnData layers/views,
metadata side effects and masked scaling are separate scopes. Do not label
an integer/float32 author workflow reproduced by changing its dtype silently.
Scaling returns a complete dense diagnostic array in this API, including when
the upstream source retained a sparse output; numeric source rules are
selected by the declared input storage/profile. Densification is bounded.

`filter` accepts exactly one criterion at a time. Counts and detected-count
thresholds use inclusive boundaries. Detected entries are strictly positive;
stored zeros are not detected. An all-false mask remains an explicit valid
filter result. Do not silently turn an author's compound AND rule into a
sequence of filters whose exclusions have OR semantics.

`NormalizeOptions.targetSum=null` chooses the median of positive normalization
totals after any high-expression exclusion. It does not imply 10,000 or CPM.
A gene is excluded from the normalization denominator if it exceeds
`max_fraction` of a cell's total in at least one cell; all genes remain in the
returned expression matrix. Literal paper percentages do not independently
establish an exact author argument. Targets must be positive and finite.

`log1p` uses a natural logarithm when `base=null`, otherwise divides by the
logarithm of the declared positive finite base (other than one). Inputs at or
below -1 can produce source NaN/Inf diagnostics and no admitted matrix.
Operation order is chosen by the caller. In particular, the API does not
silently reverse a paper's stated log/normalization order.

## Edition differences retained

| Behavior | Scanpy 1.9.3 profile | Scanpy 1.13.0 profile |
| --- | --- | --- |
| Returned `norm_factor` | Unscaled totals, with zero totals incremented to one in the selected `inplace=False` source path | Totals divided by the effective target; zero factors remain zero |
| CSR normalization arithmetic | Multiplies by a reciprocal | Divides directly, retaining finite values where the reciprocal could overflow |
| Zero denominator with a fixed positive target | Zero becomes one before division by target | Zero normalized divisor is treated as one |
| Centered clipping | Upper-only cap | Upper and lower caps |
| Uncentered sparse scaling | Earlier sparse variance path; negative caps can fill implicit zeros | Current source path retains implicit zeros and uses its literal `min(cap,value)` rule for stored values |
| One-cell scale | Original `ZeroDivisionError`; Java refuses a scaled result | Current source skips the unavailable sample correction; retained statistics determine admission |

Both profiles substitute one for an exactly zero source standard deviation.
They retain their own variance arithmetic; the Java code does not clamp a
negative/undefined variance into a valid result. Nonpositive scale caps retain
their literal source behavior with a warning. In the current uncentered CSR
path, source clipping can hide an undefined standard deviation behind finite
values. That finite-looking matrix is still **unreportable**.

The selected current reference uses Scanpy 1.13.0 and fast-array-utils 1.5.1,
with a private Python 3.13 runtime and one numeric thread. Exact dependency,
source, wheel and fixture identities are saved. The historical reference ran
the unchanged Scanpy 1.9.3 environment; all 25,252 historical runtime entries
were identical before/after capture. These are synthetic original-package
comparisons, not author-runtime execution. Finite reference values are checked
with a tolerance of the larger of 64 ULPs and 4e-12 relative; NaN/Inf diagnostics
are checked separately. Output bitwise identity across numerical libraries
or arbitrary ill-conditioned inputs is not claimed.

Very small targets can overflow a normalization divisor, and a positive total
can underflow to a zero factor. The literal source output is retained for
diagnostics, but those cases admit no complete normalized matrix. Sixteen
additional original range controls cover those failures and a large valid
target. The first candidate used reciprocal multiplication for both versions;
seven of the new controls failed against that candidate. It and its successful
earlier 402-case evidence remain preserved as superseded work. The corrected
candidate uses current source division and explicit divisor/underflow warnings;
old candidate hashes and original scientific source are unchanged.

The first exploratory capture exposed two actual interface boundaries: the
older AnnData default could cast normalization input to float32, and the new
public scale API rejected the old `return_mean_std` argument. That attempt is
retained and excluded from float64 qualification. Fresh captures explicitly
fixed the declared dtype and used the actual current AnnData `out=True` API;
no original scientific function or saved reference hash was patched.

## Warnings, capacity and recovery

**Always inspect `usable()` and `warnings()` before downstream work.**
`matrix()` refuses nonfinite/undefined statistics, even when source sparse
storage or clipping left finite-looking output. Diagnostic values, source
normalization factors, means and standard deviations remain available through
copying accessors. Do not replace NaN/Inf, erase sparse zeros, discard failed
attempts or alter an edition/target/base/clip rule to manufacture success.

Default limits bound features (1,000,000), cells (10,000,000), stored values
(10,000,000), dense entries (20,000,000), estimated internal workspace (1 GiB)
and work visits (1,000,000,000). Whole-population capacities are checked before
copies/dense allocation. A cancellation supplier and thread interruption
refuse a complete result. Returned defensive copies also need caller memory.

**FAIL2-equivalent library failures:** invalid input/options, capacity,
interruption, overflow or undefined source arithmetic admit no complete
matrix. Retain inputs and diagnostics, correct the cause and retry a new
analysis. The library reports exceptions/warnings; it is not a file-analysis
CLI with exit codes. The saved reader uses **SKIP3** for missing exact permitted
source/evidence dependencies and **FAIL2** for changed/invalid evidence. Restore
the named exact item or retain the skipped dependent audit; do not refresh
old hashes. The native API itself needs only Java 17+, not Python installation.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Scanpy developers (2026) — Scanpy API and tutorials](../CITATIONS.md#scanpy-api-documentation)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
