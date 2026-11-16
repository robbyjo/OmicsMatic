# PoPS streaming development snapshot

**Current source:** the native PoPS 0.2 batch API and the `pops-tools` prepared-input
workflow are available. Read [the runnable workflow guide](POPS_WORKFLOWS.md) for
input contracts, resource settings, accepted results, warnings and genuine replay.
Original PoPS 0.1 and LassoCV adapters and cumulative catalog admission remain
open. This development extension adds no catalog credit.

The existing [native matrix API and its frozen original-source validation](POPS_NATIVE_DEVELOPMENT.md)
remain unchanged. The additional public API is
`org.jlinalg.genetics.PopsChunked.fit`. It consumes a repeatable `Features`
provider, an explicit mapping from target rows to prediction rows, the same
covariates, chromosome covariance blocks, masks and selection settings as
`Pops.fit`, and a maximum selected-feature count. Features must retain their
original order. The provider supplies every prediction row for each contiguous
batch of feature columns. Every reread is checked against its first fingerprint.
Changed batches, duplicate target-row mappings, incomplete column partitions and
insufficient selected-feature capacity fail without returning an accepted model.

Feature batching bounds the feature columns read together. It does not bound
total process memory: chromosome covariance factors, selected training features,
the fitted model and predictions still require memory. Capacity failure must be
resolved by providing adequate resources; features or genes must not be silently
dropped. The existing explicit backend policy remains required.

The regression tests compare all 13 saved original-program cases at batch widths
of 1, 3 and 64, including both expected failure cases. Additional controls cover
permuted target rows with extra prediction genes, changed batches, selected-feature
capacity and invalid feature partitions. Together with the unchanged original
matrix and eigenvalue tests, the numerical API selection contains 60 tests. The
workflow adds 132 actual NumPy matrix-format controls, two further reader tests
and 25 complete workflow/negative controls. All 219 tests pass on Windows and
Linux Java 21 without skips. Run the combined selection and build current source:

```powershell
.\gradlew.bat :test --tests 'org.jlinalg.genetics.Pops*' --tests 'org.jlinalg.cli.PopsWorkflow*' executableJar --no-daemon --max-workers=2 --console=plain
```

The unchanged original CLI was run for six additional covariance-format cases:
CSC, CSR/COO duplicates, dense C-order NPY, big-endian Fortran-order NPY and ignored
nonfinite cross-chromosome entries. Complete workflow tests compare all 19 cases
and genuinely replay all 17 successes. Expected source failures remain failures.
Controls also reject tampered membership, resealed wrong scores, timeout,
insufficient selection capacity, missing Java and failed output recording.

The full official tutorial passed through the packaged command at batch widths
128 and 1, and through genuine Windows-to-Linux replay. All three scientific
result inventories are byte-identical. It retains 16,980 target genes, 18,383
prediction genes, 508 input features and 258 selected features without
subsampling. Maximum absolute differences from original PoPS are 2.414e-14 for
scores, 4.808e-14 for marginal diagnostics and 2.360e-16 for coefficients.
Each worker had an 8 GiB JVM heap bound and the parent a 1 GiB heap bound.
These are source-software controls, not author-environment or biological
reproduction. Complete named private records are separately inventoried.

This snapshot adds no catalog integration credit. PoPS 0.1, original LassoCV
execution and paper-specific cumulative catalog admission remain open. The
released v0.4.0 download does not contain these additions; build the current
source for development use.

The earlier evidence verifier deliberately checks an exact source-file
membership. To read the frozen evidence with that unchanged verifier, use a clean
checkout of commit `284dc02002305073136158ade28b48beb64e2721`. New streaming source
files use [a separate extension reader](../../tools/literature/pops_workflow/records.py)
and [saved qualification](pops-workflow-development/STATUS.json). Do not refresh
historical source or evidence hashes merely to make an earlier verifier accept
a later tree.
