# Warnings and required actions

**Windows request pathname:** the tested Java 21 launcher uses Cp1252 and
changes command-line beta to a different character. Analysis of that path is
skipped before reading a request. Use an ASCII request pathname and keep exact
Unicode data/output paths inside UTF-8 JSON. The example helper accepts
`--request NEW_ASCII.json`. No input file or identifier is renamed or repaired.
The raw earlier skip, code-point probe and successful Unicode-path example
are retained in the evidence.

**SKIP3:** missing or forbidden input/output access skips the dependent
analysis. Supply permitted accessible paths and retry new output. Native
analysis requires no R, Monocle or external software installation.

**FAIL2:** no complete testing family is admitted. Preserve raw input and
partials, read FAILURE.txt, analysis.json and WARNINGS.md, correct the named
input, rank, variance, capacity or integrity cause, and retry NEW output.
No failed gene is dropped or relabeled as a valid null. Final recording failure
retains computed metadata as unadmitted-analysis.json and sets admission false.
Require exit 0, COMPLETED.json and result_admitted=true.

**Numeric zero:** retain log_p_value, z and full design/graph metadata.
NUMERIC-WARNING.txt identifies p/q underflow. Numeric zero is not exactly zero
mathematical probability, and a -Inf log token is an explicit representation
limit. No probability floor is inserted. The final 10000-observation control
retains finite log tails near -5000 while ordinary probabilities reach zero.

**Declared scientific choices:** responses are already prepared, the complete
numeric design includes any intended intercept/factor contrasts, and directed
weights are supplied explicitly. Only unweighted linear-model residual inference
with normal reference is provided. No graph learning, row normalization,
missing-value handling, formula construction or count/log transform is inferred.
zero_policy adjusts the reference count exactly as declared while retaining
all model/response rows. This differs from deleting isolated observations.

**Capacities:** input limits are 64MiB/file, 128MiB total, 16 million entries per
matrix, 1000000 observations, 128 design columns, 2000000 directed arcs and 100000
genes. The complete-work bound defaults to 500 million and may be at most 2 billion.
These are refusal bounds, not OS quotas or a whole-atlas scalability claim.
Do not subset the intended observations/testing family to force acceptance;
use a justified larger-capacity implementation with the complete family.

**Availability:** rebuild current source; released 0.4.0 is unchanged. Current
success does not recover P021's executed author commit, historical Monocle1.0.0
weight calculation, full assay/lineage choices or biological validity. Author
code was not executed or redistributed. The full toolbox goal remains open.
