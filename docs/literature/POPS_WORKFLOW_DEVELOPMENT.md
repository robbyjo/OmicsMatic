# PoPS streaming development snapshot

**Development status:** the native PoPS 0.2 batch API is available to Java
callers. The prepared-file input classes are internal scaffolding. They have
compiled successfully, but their file-binding behavior has not completed
qualification. There is no `pops-tools` command, supervised workflow, completed
result bundle or replay command in this snapshot. Do not use the internal input
classes as an accepted analysis workflow.

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
matrix and eigenvalue tests, the complete PoPS test selection contains 60 tests.
Run it and build the current source with:

```powershell
.\gradlew.bat :test --tests 'org.jlinalg.genetics.Pops*' executableJar --no-daemon --max-workers=2 --console=plain
```

The internal `PopsWorkflowNpy`, `PopsWorkflowFiles` and `PopsWorkflowInputs`
classes prepare future NPY/NPZ, identifier-join and input-capture work. Their
compilation is not evidence of numerical file-workflow parity, process resource
enforcement, prominent runtime warning delivery or genuine replay. Those checks
and the user-facing command remain required before workflow admission. The full
official tutorial has been compared for the earlier matrix API; it has not yet
been rerun through this new batch API or these input bindings.

This snapshot adds no catalog integration credit. PoPS 0.1, original LassoCV
execution and paper-specific cumulative catalog admission remain open. The
released v0.4.0 download does not contain these additions; build the current
source for development use.

The earlier evidence verifier deliberately checks an exact source-file
membership. To read the frozen evidence with that unchanged verifier, use a clean
checkout of commit `284dc02002305073136158ade28b48beb64e2721`. New streaming source
files require a separate extension review; do not refresh historical source or
evidence hashes merely to make the earlier verifier accept a later tree.
