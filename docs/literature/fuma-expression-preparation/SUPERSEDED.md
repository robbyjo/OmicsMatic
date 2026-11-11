# Use the corrected expression-preparation checkpoint

**Required action:** use the candidate identified by
[fuma-expression-preparation-2/VALIDATION.json](../fuma-expression-preparation-2/VALIDATION.json)
and the current [usage guide](../FUMA_EXPRESSION_PREPARATION.md).

This initial checkpoint is retained as historical evidence. Its scientific
tables passed, but `session.json` serialized `label_sample_counts` as a Java
array identity string. The corrected candidate writes a JSON integer list.
The existing test and public verifier now check that field directly; all663
affected tests and148 tests on each of three JVMs passed again.

The correction changes only the CLI metadata writer. All26 compared scientific
output tables, including expression/DEG results and the original MAGMA fit,
are byte-identical. This directory's original validation JSON and evidence ZIP
remain unchanged; no failed evidence or original inputs were deleted.
