# Original rnasplice reference execution

The unreduced original nf-core/rnasplice 1.0.4 test completed all 128 tasks
with exit 0 and no owned processes remaining. Its corrected declared-output
capture completed, and the final development JAR verified the saved bundle on
Windows and Linux. The 39 output declarations cover 19 directories and 20
rMATS tables; the publication contains 4,371 files and 360,781,962 output bytes.
These output bytes exclude the captured runtime, inputs and working cache.

All 20 complete rMATS tables are byte identical across two full original
executions: 1,460 table rows, both original ID columns and 634 NA field values
are retained. The first execution's capture failed because our output
contract incorrectly required top-level rmats rather than star_salmon/rmats.
That failed capture remains retained and unadmitted. The corrected run used
new paths and the same complete reads, references, source and analysis defaults.

Across the full publication, 2,792 files are byte identical, 1,545 shared
paths have changed bytes and each run has 34 run-specific paths. Those
differences remain retained for source-aware scientific review. No path,
identifier, numeric, stochastic or timestamp normalization was applied.

This qualifies a complete original reference graph and its portable declared
capture only. It adds zero implementation-family credit. The catalog remains
181 scoped families (36 native, 145 upstream), 1,864 reviewed uses and 870
pending uses. The full 250-paper goal remains active. The test uses the
original four human chromosome-X samples, not the P027 worm study. P027
reports STAR 2.7.9a while this pipeline source selects 2.7.10a; that conflict
remains unresolved. Independent complete scientific-output contracts and a
usable typed rnasplice adapter remain to be implemented and validated.

**Warnings and required actions:** inspect the complete saved WARNINGS.md,
original process logs, Nextflow log and trace before interpreting outputs.
The original runtime reports 130 permission-mode departures and missing
Maven artifact descriptors; required executable access and every declared
dependency JAR's bytes were checked. Use a permitted filesystem and fresh
setup if exact POSIX modes are required. Original diagnostics remain retained.
The process monitor used bounded resampling; it imputed no live RSS/thread
measurements and verified that no owned process remained. These facts do not
certify biological results or unresolved author settings.

Source tables contain repeated ID header names. Keep both positional columns;
do not overwrite one through a dictionary importer or silently rename IDs.
Keep original NA tokens and missingness masks; do not turn them into zero,
negative evidence or a replacement statistical model. P027's reported
selection uses raw P < 0.05 and absolute delta PSI >= 0.05, with no FDR
substitution. Applying those cutoffs to the human test does not reproduce P027.

SKIP3 (exit 3) skips analysis when a named prerequisite is missing or forbidden:
complete permitted setup or retain the skip. FAIL2 (exit 2) admits no complete
result: preserve every attempt, correct its named cause and use new paths when
the immutable request changes. Follow the existing result's state; do not
restart a RUNNING session. The generic component is documented in
[NFCORE_VALIDATION_BRIDGE.md](../NFCORE_VALIDATION_BRIDGE.md).

[EXECUTION-1.json](EXECUTION-1.json) pins the producer, complete capture,
original process/trace evidence and both host verifications. Original source,
inputs, binaries and raw traces remain private. No whole nf-core family,
whole-paper replication, release or literature Pages deployment is admitted.
Preserve the source runs, saved bundles, runtimes, evidence and worktrees for
the remaining scientific validation. Publication/cleanup closure is recorded
in COMPLETED.json when present.
