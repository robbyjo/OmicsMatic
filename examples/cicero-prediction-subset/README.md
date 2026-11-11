# Supplied Cicero prediction subsetting

Run the command in [the guide](../../docs/literature/CICERO_PREDICTION_SUBSET_DEVELOPMENT.md) from the repository root. This synthetic example needs Java only and installs nothing. The request resolves all paths relative to this directory and refuses an existing output.

Expected membership: type-A retains p1; type-B retains p2 and p3; empty-type retains no rows. The half-open interval touching source3 at position60 does not overlap it. Source-only filtering never tests target gene expression: p1 remains eligible with `gene_detected=false` and an unannotated target.

**Payload warning:** p1's `NA` and p3's `NaN` are deliberate opaque input strings. The operation preserves them; it does not establish usable scores or valid biological predictions. Read the generated WARNINGS.md and complete membership ledger before interpretation. Empty subsets are filtering dispositions, not negative biological findings.
