# Original scVelo 0.2.4 and 0.2.5 development workflows

This original workflow has **complete runtime, scientific-state and supervision
component qualification**. Verify its frozen evidence with the command below.
Catalog admission remains separate and pending; the component itself adds no
catalog integration or individual-review credit.
The [current catalog](omics-2023-2026/README.md) remains authoritative. These
Python source entry points use unchanged original scVelo; they are not commands
in the released JAR and do not establish paper reproduction or convergence.

**Use an exact matching provider.** A 0.2.4 request cannot run with a 0.2.5
provider. P070 prints **0.24**, for which the exact PyPI endpoint returned
HTTP404; preserve that label and skip its dependent exact-edition analysis.
Choosing a supported edition for exploration is an explicit caller choice.
P228 uses the separate **velociraptor R wrapper**, whose project execution is
not qualified here. Skip that dependent interface until its requirements and
workflow are separately qualified.

**SKIP3 skips dependent analysis** when installation, download, execution or a
required exact runtime, input, symbol or dependency is missing or forbidden.
Supply only permitted requirements and retry NEW paths. **FAIL2 admits no
complete result.** Keep every original object, partial output, warning/error,
raw worker report and complete console. Correct the named cause without dropping
failed cells, genes, stages, parameters or files.

## Install and run a complete example

Run these commands from the project root in Linux/WSL x86-64. Use a NEW native
Linux prefix and NEW setup/example paths. The prefix below uses your Linux home
directory; the project, setup receipts and final results can remain on the
mounted Windows drive.

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

Use `--version 0.2.4` with a separately installed 0.2.4 provider for that edition.
Choose `--mode stochastic` for the stochastic velocity, graph, pseudotime, PAGA,
arrows and streamline example. The dynamical example recovers gene kinetics,
estimates dynamical velocity, builds the velocity graph and computes latent time.
It also retains the original returned recovery model and a streamline figure.

Installation uses private Python 3.9.25 and 45 exact installed distributions,
including Scanpy 1.9.6, NumPy 1.23.5, SciPy 1.10.1, pandas 1.5.3,
Matplotlib 3.6.3 and igraph 0.10.8. Hash-locked dependency downloads, the original
scVelo wheel/source archive and all original package files are checked. The uv
bootstrap creates no global Python executable links. Existing environments and
the released executable are not changed. Setup/READY runs no model. Analysis
installs nothing. Native Windows original execution skips; use Linux/WSL.

The complete example uses the same 160 synthetic cells and 12 genes in both
editions. Its input was generated once with original scVelo0.2.5 Gillespie
simulation. `NA`, Greek identifiers and nonalphabetical categorical order remain
literal. The two-dimensional coordinates are caller-supplied synthetic
coordinates, not a fitted UMAP. Its 30 neighbors, eight PCs and 15 recovery
iterations are integration settings, not inferred author defaults or evidence
of convergence. Real analyses use their own trusted scripts and populations.

## Read warnings and recover failures

Before using any result, inspect:

- `WARNINGS.md`, `analysis-status.json` and the complete `process.stdout` and
  `process.stderr`;
- `diagnostics/conditions.json`, the raw `diagnostics/worker-status.json`,
  `diagnostics/original-error.txt` and `diagnostics/example-retention-review.json`;
- `SUPERVISOR_ERROR.txt`, `WORKER_DIAGNOSTIC_ERROR.txt` and
  `NATIVE_TEMPORARY_ERROR.txt` when present;
- `MANIFEST.json`, `temporary-directory.json` and
  `native-temporary-inventory.json`.

Delivered-condition counts cover the trusted caller script and original runtime
dependencies. Original warning filters, promoted errors and default logging
handlers are preserved. Filtered warnings remain filtered. Unknown totals are
JSON `null`, never zero, with `condition_counts_complete: false` and
`warning_review_required: true`; a complete result is not admitted. A zero
delivered-warning count does not prove numerical validity.

Both selected scVelo editions suppress `UserWarning` during import. A caller
who needs those warnings delivered can explicitly use
`warnings.simplefilter('always', UserWarning)` in the trusted script. The
worker preserves the upstream filters and the caller's explicit policy. The
protocol controls exercise visible, ignored and promoted warnings separately.

The dynamical examples retain the original NaN padding in `varm['loss']`
histories. Original scVelo allocates a NaN-filled rectangular array and pads
different per-gene history lengths. Preserve these values and inspect all loss
histories. The example emits a separate project retention-review log so the
nonfinite snapshots remain conspicuous. They are not replaced with zero, and
successful process exits are not convergence decisions.

The controller owns a short native Linux temporary directory under `/tmp`.
This is needed because multiprocessing uses Unix sockets, which the tested
mounted Windows filesystem does not support. Both the analysis directory and
native temporary directory share the declared entry/byte capacities. Available
regular temporary files are copied into `native-temporary/`; original modes,
directories, remaining socket/link identities and the original root path are
recorded separately. The controller leaves the original native root present
when its retention step completes. Retained copies also count toward admission
capacities. The manifest makes the saved regular temporary bytes available with
the analysis bundle.

**Original temporary paths are not durable evidence.** At archival review,
168 original analysis temporary directories and the separate unit-test fixture
were no longer available. All13 recorded regular temporary-file copies,
268,435,479 bytes in total, remain in the saved analysis bundles and match their
original receipts exactly. The cause of later analysis-root unavailability is
not established. `original_root_retained: true` records presence at the original
controller retention step; it does not assert that a directory remains present
later. Use the verified `native-temporary/` copies and saved node identities,
and preserve/verify the whole analysis bundle before leaving an ephemeral
runtime. Do not claim the deleted directories or live IPC state were recovered.

Only recognized temporary Manager sockets are allowed during monitoring;
unexpected special nodes and analysis-result links fail admission. Socket
metadata does not claim to preserve live IPC payloads. The worker's process
group is supervised; trusted scripts still run with account permissions. This
is not an OS sandbox, total CPU/RSS enclosure or guarantee about child-created
sessions and arbitrary external writes.

If recording is blocked, keep the complete console and every writable original
object. Resolve the named filesystem requirement, permission or capacity and
retry NEW paths. Preserve the analysis bundle and every still-available original
temporary root until review; an existing output is never overwritten.

## Complete-state comparison

The fixed examples retain every original stage as pickle and H5AD files, plus
independently sampled inspectable object graphs and NPY arrays. Each edition has
453 stochastic result files and 417 dynamical result files. Across the four
edition/mode combinations there are 40 stages, 1,628 arrays, 1,863,018 elements
and 1,740 result files. All arrays and typed scientific stage graphs have matched
direct original execution at tolerance zero, including fresh public installations.

`compare_complete.py` checks the entire fixed original population and each
numeric array's exact bytes. It also checks all declared figures and, with
`--supervised`, the complete controller and native-temporary manifests. It does
not unpickle arbitrary objects or claim byte parity for pickle, HDF5, generated
SVG identifiers or timestamps. No identifier, NaN, dtype, failed stage or extra
file is normalized away. See `test_compare.py` for isolated omission, repair,
typed-identifier and unadmitted-status refusal fixtures.

A separate larger stochastic control exercises 2,100 synthetic input genes in
96 cells. Original HVG selection retains 2,000 genes, followed by moments with
50 neighbors and30 PCs, stochastic velocity, graph construction, projection and
a streamline figure. Both editions retain the complete raw population and
gene-selection identities. All 248 arrays/11,651,009 elements per edition match
direct original execution at tolerance zero, and the original neighbor metadata
records50 neighbors and30 PCs. `min_shared_counts=30` is an explicit caller
choice for this control; the P051 paragraph does not establish that exact keyword.
The input is synthetic negative-binomial layer data, not author data or a
biologically validated kinetic simulation. These two controls add532 retained
result files and do not add catalog credit.

## Current controls and remaining qualification

Both editions passed all50 Linux workflow controls each:100 total. These include
visible/ignored/promoted warning policies, edition/provider matching, native
multiprocessing IPC, combined workspace capacities, complete output admission,
malformed reports and recording recovery. Separate controls passed for native
Windows refusal,7 monitor cases,10 setup refusals,10 native temporary cases,
12 narrow semaphore-mapping cases,8 portable-recorder groups and11 comparator
refusals plus its complete baseline. The full public installers and all four
complete examples were exercised in fresh private environments.

The complete available evidence is retained in the lossless bounded archives
and exact logical identity map linked below. Catalog admission remains pending;
installation/import success alone does not qualify an analysis. The current catalog still grants scVelo zero credit. The
original inputs/results and all available failures are preserved. No released
JAR or unrelated work was changed.

## Primary-source roles

All 250 exact cached XML identities were checked. Five papers contain six named
scVelo paragraphs. These are intake readings, not additional catalog review
credit until admission.

| Paper | Reported role and limits |
| --- | --- |
| [P051](https://doi.org/10.1038/s41586-025-09807-0) | Mouse-neutrophil stochastic velocity; reported count threshold30, 2,000 HVGs, 50 neighbors and 30 PCs, with projection into an existing UMAP. Edition and exact threshold keyword are unreported. Velocyto/BAM preprocessing is separate. |
| [P070](https://doi.org/10.1038/s41586-023-06806-x) | PCW7.2 mesenchymal subset; named filtering/normalization, moments, stochastic velocity, graph and plotting functions. Printed edition0.24 remains unresolved; reported defaults are not inferred from the example. |
| [P117](https://doi.org/10.1038/s41588-026-02737-1) | Explicit0.2.4 stochastic dynamics and scVelo PAGA for selected mouse-ZG clusters. Exact preprocessing, grouping field and PAGA options are unreported here. |
| [P161](https://doi.org/10.1038/s41588-026-02665-0) | Explicit0.2.5 dynamical velocity with30 neighbors. CellRank kernel mixing, macrostates and absorption probabilities are a separate downstream workflow. |
| [P228](https://doi.org/10.1016/j.ajhg.2025.07.020) | scVelo through the R velociraptor wrapper and reported organoid latent-time comparisons. The R-wrapper and Python scVelo editions and exact calls are unreported. Preserve the text's Velocyto name beside a velociraptor URL as a source/link discrepancy. |

The original BSD-3-Clause scVelo distributions and MIT/Apache bootstrap notices
remain retained. Provider capture hashes all selected interpreter/environment
members and loaded library files. A specifically recognized, non-executable
shared semaphore page is recorded as transient process state with its raw map
and data; its address/value is not cross-process code identity. All other deleted
mappings refuse readiness. Original archive modes remain in the untouched source
archive; extracted audit-copy modes are recorded as the host filesystem exposes
them. SHA-256 establishes integrity, not authenticity or biological validity.

## Retained failures and irrecoverable history

An initial diagnostic recorder failed on a NumPy boolean in H5AD categorical
metadata and stopped before fitting. Its source, inputs and error remain saved.
The corrected recorder retains the logical flag and scalar type; original
objects are saved before portable export.

**Four SVGs from an earlier original0.2.4 trial were overwritten by a caller
path error and cannot be recovered.** Leading-slash names bypassed scVelo's
figure directory and later0.2.5 runs reused those absolute paths. All remaining
originals, raw arrays, stage pickles, logs and rescued figures are retained.
`scvelo-original-candidates-2/QUALIFICATION_REFUSED.json` marks that trial's
artifact population incomplete. Fresh runs use the identical retained input,
basename-only plot names and complete per-plot checks. Their new figures are not
presented as recovered historical bytes.

The first provider capture refused a deleted semaphore mapping; that refusal
is preserved alongside the narrower source-based classification and executable-
mapping refusal controls. The first supervised trials failed at Unix-socket path
creation. A direct mounted-filesystem socket probe also failed. Those attempts
remain retained; the dedicated monitored native temporary root is a separate
correction, with real socket, combined-capacity and retention controls.

## Frozen evidence and verification

The [frozen qualification](scvelo024025-development/VALIDATION.json) binds every
current source file, all nine ZIP parts, the complete logical identity map and
the additive retention journal. Each ZIP is below40 MiB. All30,983 regular file
contents,4 link identities and3,205 directories were independently rechecked
against the originals. The retained regular files total3,332,807,593 logical
bytes; identical payloads are stored once without reencoding scientific bytes.

The original pack worker stopped during its final read-only source recheck;
its tool handle and Linux process were absent and its exact exit/cause is
unknown. Its incomplete records remain unchanged. A separate full recovery
verification checked all archive objects, original file bytes/types/modes,
scientific populations, protocol controls, providers and native byte copies.
The complete recovery and observation receipts remain in the retention journal.
This does not recover the four overwritten historical SVGs or deleted temporary
directories. All other available failed objects and diagnostics remain retained.

Verify without installing or fitting any model, from the project root:

```sh
python3 tools/literature/scvelo024025/verify.py \
  --out build/literature-review/monocle-runtime/NEW-scvelo-frozen-check
```

To inspect one exact retained regular file, add `--member` with a logical path
from `INDEX.json`. The verifier writes only that selected file in a NEW output;
it never follows recorded links or special nodes. Read its `WARNINGS.md` and
status before using outputs. SHA-256 establishes integrity, not authenticity.
The publisher paragraph cache remains local; the bundle retains source hashes,
independent role summaries and primary links. Original scVelo BSD, uv bootstrap
MIT/Apache and the unmodified glibc reference's LGPL notice remain retained.
Complete interpreter/dependency installations remain private. Generated analysis
caches are retained as build-specific artifacts, not portable runtime promises.
