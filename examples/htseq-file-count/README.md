# Original HTSeq file-count example

These six synthetic reads and two GTF exons test strand-specific counts, repeated overlaps, an NH-multimapping exclusion, one no-feature read and one unaligned read. They are not biological data. Run COUNT inside Linux/WSL with Linux Java 17+ and an exact installed private runtime listed by `htseq-tools --source-report`.

From the repository root, replace `/absolute/private/htseq-home` with the matching installed Linux home. This helper only writes an explicit request; it installs nothing:

```sh
python3 examples/htseq-file-count/make_request.py \
  --runtime-home /absolute/private/htseq-home --version 2.1.2 \
  --request build/htseq-example-count.json --run-output build/htseq-example-run

java -jar build/literature-review/htseq-runtime/tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar \
  htseq-tools --request build/htseq-example-count.json
```

Successful `counts.tsv` has the exact header `feature_id`, `count` and two rows: A=2 and B=1. The five summary values are no_feature=1, ambiguous=0, too_low_aQual=0, not_aligned=1, alignment_not_unique=1. Source summary names have `__` prefixes except in 0.5.3. The helper requires the version explicitly; it chooses the only supported SCORE flag policies for 0.5.3/0.6.0 and IGNORE for later editions. This choice applies to this synthetic example, not an unstated paper workflow.

VERIFY needs Java only and also runs on Windows. With the example paths above:

```sh
java -jar build/literature-review/htseq-runtime/tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar \
  htseq-tools --request examples/htseq-file-count/verify-request.json
```

Choose new request/run/verification paths for another attempt. The checked-in verification request resolves paths against its own folder. Windows Java requires a Windows-accessible source run; a run saved under this Windows workspace through `/mnt/d/...` is accessible from either side.

**SKIP3 means analysis skipped:** the exact permitted Linux runtime/helper is missing or forbidden. Read `WARNINGS.md`, provision the required exact profile only where installation is allowed, and retry into new paths; otherwise retain the skip. **FAIL2 means no result admitted:** preserve the attempt/logs, correct the named source/input/option/process/capacity cause and retry into new paths. Uncommitted diagnostic files can remain after a failure. Require `COMPLETED.json`, no `INCOMPLETE.json`, and successful VERIFY before admitting the complete result bundle.

This is original counting, without independent native SAM/GTF parsing/pairing, normalization, proper-pair filtering or cell/UMI/peak-matrix logic. VERIFY checks integrity and internal consistency; it does not authenticate execution or biological accuracy. Read [the tools guide](../../docs/literature/HTSEQ_TOOLS.md) for full limitations and dependency/recovery instructions. Older canonical/released JARs have no HTSeq route.
