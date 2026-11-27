# Native rMATS post-fit selection

The new `rmats-results` command and `org.jlinalg.splicing.RmatsResults` API
provide native Java positional import and raw-P/absolute-PSI selection.
Both original ID columns, every source row and numeric token, NA masks,
original row order and explicit JC/JCEC mode are retained. Missing selection
inputs remain UNASSESSABLE. Exact decimal cutoffs preserve boundary behavior.
Saved verification regenerates all derived files from captured input/request.

This component is part of the continuing full RNA integration. It performs no
BAM counting or model fit and adds zero whole-tool/family credit. Current
totals remain 181 scoped families (36 native, 145 upstream), 1,864 reviewed
uses and 870 pending uses across the frozen 250-paper cohort. The full goal
remains active; no catalog counts or prior usage reviews change here.

The final producer is checked by 13 Java tests, all 20 complete original
rMATS 4.1.2 tables on Windows and Linux, 40 separate saved verifications,
and both-host synthetic boundary/refusal cases. The original tables cover
1,460 rows and 634 NA field tokens per host; selection matches an independent
Python decimal projection. One-ID formats are additionally covered by
synthetic schema tests. The exact evidence and final counts are in
[QUALIFICATION.json](QUALIFICATION.json).

**Warnings/actions:** read WARNINGS.md, summary.json and unassessable.tsv.
Never interpret missing raw P/PSI as a negative result, merge positional IDs
or replace raw P with FDR. Require COMPLETED.json and fresh `--verify`.
Missing input is SKIP3 (exit 3): restore the original file or retain the skip.
Invalid data, capacity or changed bytes are FAIL2 (exit 2): preserve evidence,
correct the named cause and retry NEW paths. No partial result is admitted.
The [guide](../../rmats-results.md) documents capacities and the exact gate;
the [example](../../../examples/rmats-results/README.md) runs without upstream
rMATS, R or Python once its synthetic input has been generated.

Remaining work includes independent scientific contracts for all other RNA
methods and a usable typed RNA execution adapter. Retain the original
cross-run differences and the unresolved paper/source STAR version conflict.
No whole-paper replication, complete nf-core/rMATS family, release or
literature Pages deployment is admitted. Original inputs, complete/failed
runs, raw traces, runtime images, development JARs and unrelated work remain
retained. Publication/preservation/mandatory-cleanup closure is recorded in
COMPLETED.json when present.
