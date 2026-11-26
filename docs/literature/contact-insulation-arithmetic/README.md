# Finite median overflow guard

This source update supplements the [prepared contact-insulation baseline](../contact-insulation-validation/README.md). It adds no tool family, source review or paper. The catalog remains at 167 scoped families: 33 native and 134 upstream.

Four finite scores of `1e308` expose an arithmetic problem in the original NumPy median operator: adding the two central values overflows, the median becomes infinity, and dividing by it yields zero scores. The retained synthetic baseline diagnostic shows those zeros and missing log2 tracks; it is explicitly marked invalidated and retained. Its original inputs, executable and outputs have not been rewritten.

Java now detects overflow when adding two finite central scores and throws an explicit arithmetic failure. The command returns **FAIL2**, keeps the attempt and its failure diagnostic, and admits no complete result. It does not rescale the values or substitute a different median formula. This is an explicit restriction relative to the upstream warning-producing behavior. Normal zero/NaN median handling is unchanged. Correct the input magnitude/preparation and use a new output path; do not bypass the refusal with an older executable.

Validation uses 85 focused tests per host, all 459 existing original-function comparison cases, complete reference-map captures, large finite positive controls, overflow refusals and refusal of resealed legacy zero-score results. Windows and Linux reference-map output files are byte-identical to the qualified baseline. Saved verification also recomputes this guard.

`QUALIFICATION.json` and the frozen source/evidence indexes record the exact producer and retained controls. Raw traces, scientific data, runtimes and cleanup receipts stay private. Earlier checkpoint hashes and source contexts remain unchanged. Use the exact older producer/source context for historical captures; this supplement does not turn the diagnostic overflow attempt into a valid result.
