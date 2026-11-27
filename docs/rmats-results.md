# Native rMATS result selection

`rmats-results` imports complete rMATS statistical result tables and selects
rows with **raw P < maximum** and **absolute source delta PSI >= minimum**.
It runs in Java without R, Python or an installed rMATS runtime. This is a
post-fit method component; BAM counting and rMATS model fitting remain upstream
operations. It does not qualify the entire RNA workflow or reproduce a paper.

```text
java -jar OMICSMATIC.jar rmats-results --input SE.MATS.JC.txt --event SE --mode JC --group1 2 --group2 2 --max-p 0.05 --min-abs-dpsi 0.05 --out NEW-result
java -jar OMICSMATIC.jar rmats-results --verify NEW-result
```

Declare the actual event type, original JC/JCEC count mode and replicate counts
of the two source groups. Count mode is retained as user-declared provenance;
the identical column schemas cannot establish it independently.
Supported event schemas are SE, MXE, A3SS, A5SS and RI. Both one-ID and two-ID
statistical-result schemas are explicit. The same importer accepts JC and
JCEC result tables; keep their analyses and provenance distinct. Coordinates,
quoted gene identifiers, row order, both positional ID columns and original
numeric tokens are retained. Counts, lengths, coordinates, vector sizes and
numeric domains are checked. Unexpected schemas and repeated ID tuples are
refused. Header-only tables are valid zero-result populations.

Thresholds use exact decimal comparison, including values too close to a
boundary for binary double precision to distinguish. Source FDR is preserved
and never used in place of raw P. The [original rMATS 4.1.2 output guide](https://github.com/Xinglab/rmats-turbo/blob/v4.1.2/README.md#output)
describes these fields; its summary command has separate selection semantics.
This command applies the explicitly requested post-fit gate, and does not
recalculate PSI, P values or multiple-testing adjustments. P027 reports the
raw-P/absolute-PSI gate above; using it with other inputs does not reproduce P027.

The output contains the exact `input.tsv`, explicit `request.json`, complete
`decisions.tsv`, `selected.tsv`, `unassessable.tsv`, `summary.json`, warnings
and a manifest/completion marker. Every source row receives one decision.
Derived TSV files use LF endings while retaining each original field token.
Saved verification hashes the complete bundle and independently regenerates
every derived file from its captured input and request. Updating hashes after
editing a selected table cannot satisfy that semantic check.

**Warnings and required actions:** read WARNINGS.md and every missingness
category in summary.json. Missing raw P or delta PSI yields UNASSESSABLE,
including a row with a known non-significant P but missing delta PSI. Inspect
unassessable.tsv; never interpret those rows as negative results. Literal NA
tokens remain NA. NaN, Inf, malformed numeric tokens and invalid domains are
refused instead of being repaired. NA counts include individual replicate
fields and are not a count of unique rows. FDR or replicate-PSI missingness
is recorded even when the two explicit gate inputs are assessable.

SKIP3 (exit 3) skips analysis when the input is missing: restore the original
file or retain the skip. FAIL2 (exit 2) admits no partial result: preserve the
attempt, correct its named invalid data, capacity or changed-byte cause, and
retry a new directory. Existing output directories are preserved. Require
COMPLETED.json and fresh `--verify` before downstream use. Hashes establish
integrity, not authenticity, model validity or biological accuracy.

CLI capacities are 64 MiB per input/derived file, 1,000,000 rows and 1,048,576
characters per line. Larger inputs require an explicitly reviewed capacity
extension. The library API is `org.jlinalg.splicing.RmatsResults`: call
`read(Reader, Event, Limits)` and `Threshold.classify(Row)`. Its limits and
replicate counts are explicit; returned source rows and collections are
immutable. Callers own the Reader and should use strict decoding.

[Synthetic examples](../examples/rmats-results/README.md) and
[qualification](literature/rmats-results-validation/QUALIFICATION.json) provide
the tested scope. The original full source executions and failed attempts
remain retained separately. Scientific contracts for the other RNA methods,
the typed RNA adapter and the unresolved P027/source STAR version conflict
remain pending. This component adds zero implementation-family credit.
