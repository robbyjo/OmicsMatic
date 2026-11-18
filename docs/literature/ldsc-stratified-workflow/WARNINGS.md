# Stratified LDSC workflow: warnings and required actions

**Check all three success conditions:** exit 0, valid `COMPLETED.json` and
`analysis.json` with `result_admitted: true`. Read the attempt's `WARNINGS.md`.
A calculated component, model file, log or old output directory alone does not
admit the requested analysis.

**SKIP3 means dependent analysis skipped.** Supply the named accessible,
permitted prepared input or the complete current source-build JAR with Java
17+. Unsupported analyses/weighting need a separately qualified method. No
estimator or runtime is silently substituted. If access or installation is
forbidden, keep the dependent analysis skipped.

**FAIL2 means no complete result admitted.** Preserve exact inputs, partial
output and the failed attempt. Read `FAILURE.txt` and `WARNINGS.md`, correct the
named input, rank, reference, block, numeric, capacity or recording issue and
retry with a new output directory. Do not delete a previous attempt or reuse
its success marker for a new invocation. Existing output is preserved in full.

**Retained partials:** `component-model.json` explicitly has
`complete_analysis_admitted: false`. A missing runtime or failed overlap
summary can retain this fit component. Late failures retain an unadmitted
analysis snapshot and remove completion. These are diagnostics, not a complete
requested analysis.

**Scientific boundaries:** prepared observed-scale partitioned Hsq, selected
CBIIT 3.0.2 fixed initial weights. Caller preparation establishes filtering,
allele/reference alignment, annotation/LD generation and genome order. Binary
overlap mode requires integer matching counts/intersections and a unique
reference-universe count; continuous overlap is a separate model. Annotation
allocations double-count shared variants. Enrichment tests use the original
two-sided category/complement t contrast with df equal to the block count.
Baseline/zero-contrast P/log-P remain NA. Other sidedness, corrections, liability
conversion and multivariate/spatial/wrapper behavior require separate choices.

**No repair to force success:** preserve signed/out-of-range estimates and
review model/reference adequacy. Do not clamp final estimates, force positive
heritability, round continuous overlaps, remove failed SNPs/annotations/blocks,
replace missing values or substitute a pseudoinverse.

**Historical qualification:** the earlier API evidence and its exact source
snapshot remain unchanged at Git `e5dafcacaba4d7db6c36ecca52518474b8ca7df7`.
Its guarded priority document predates this workflow. Use the current workflow
verifier for the current tree. It checks that older snapshot against actual Git
and requires unchanged current scientific API/fixture bytes; it never refreshes
old fingerprints or waives a changed numerical engine.

**Development build history:** the first build stopped on a missing
serialization identifier in the new exception under `-Werror`. That source/log
is retained. The fixed build passes; a later binary-count refusal check and its
test are included in the packaged verification on both platforms.
