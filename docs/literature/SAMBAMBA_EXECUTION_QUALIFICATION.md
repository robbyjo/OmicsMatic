# Sambamba replay and spill qualification controls

**Warnings and required actions:** this qualification covers the bounded
Sambamba 0.6.6/0.8.2 execution profile. It adds no catalog credit and does not
complete the 250-paper implementation objective. BAI files remain unqualified
collateral; use a separately qualified indexer before index-dependent analysis.
The BAM audit still has its documented 64-MiB compressed / 256-MiB decoded /
one-million-record bounds. Paper-scale capacity requires further implementation
and verification. Never split duplicate families arbitrarily to fit a bound.

**Deliberately incorrect bundles are retained as negative controls.** The
qualification archive includes tampered bundles, a consistent but scientifically
wrong duplicate classification, expected failures and an invalidated completion
receipt. These are test evidence, not accepted analyses. A completion receipt or
successful saved-data readback alone is insufficient: genuine original-runtime
replay must succeed. If the exact runtime is unavailable, skip the dependent
analysis and supply the named permitted prerequisite before retrying new output.

This extends the [execution development checkpoint](SAMBAMBA_EXECUTION_PROFILE.md).
The adapter/auditor Java sources and compiled implementation classes are
unchanged from that checkpoint. Its original records and the historical guide
inside its source archive are preserved. The earlier record already contains
32 original-runtime cases, 32 genuine replays, 96 complete BAM comparisons and
five actual resource/process controls. The later evidence is separately captured
in [STATUS.json](sambamba-execution-qualification/STATUS.json).

## What the additional controls establish

The replay controls exercised real production bundle reading and real original
classification, using a compiled test harness alongside the unchanged JAR:

* Changed hashes, resealed contradictory requests, altered implementation
  identities and contradictory original command arguments were rejected before
  another original process started. Resealed extra and missing files were also
  rejected. The original bundles remained byte-identical.
* A four-record paired-quality bundle was deliberately changed to retain the
  lower-quality pair. Its duplicate counts, flag transitions, saved decision
  table, header, request and member hashes were internally consistent. Production
  saved-payload validation accepted it. Genuine replay executed the original
  classifier successfully and rejected the complete-record difference.
* A console exception was injected after the production completion write and
  readback. The actual original analysis had succeeded. The adapter returned
  `FAIL2`, retained the failure and outputs, and renamed the completed marker to
  `COMPLETION-INVALIDATED-...properties`. No accepted completion remained. This
  tests that recording boundary; it does not simulate every filesystem failure.
* Genuine removal replay succeeded after the original request and BAM were
  retained under different names. It used the captured input. A missing runtime
  returned `SKIP3`; a 0.8.2 binary requested as 0.6.6 returned `FAIL2`. Neither
  substituted an edition or started another original process.

The spill controls used 30,000 independent synthetic pairs, or 60,000 records.
One pair had a unique highest quality; the other 29,999 pairs were duplicates.
The validator independently checked every marked flag and the two retained
records, in addition to the production complete-record audits.

For each release, three explicit configurations ran marking, removal and genuine
replay: a large-table baseline at one thread/compression 6, a forced-spill case
at one thread/compression 0, and a forced-spill case at two threads/compression 9.
Both spill cases used requested hash size 1, overflow capacity 1,024 and sort
buffer 1 MiB. The request is retained literally; any effective source behavior
belongs to the original executable. All 24 original marking/removal processes
passed. Eight complete BAM comparisons across configurations passed, allowing
only each checked exact command string to differ in its header.

The validator observed actual original-process identities and temporary files
while each spill process was alive. Each spill stage produced more than two
pairing spill BAMs and more than one paired-info sorting chunk. Counts and
observed sizes are retained in the process records. Merely requesting a small
buffer was not treated as proof of disk spilling. RSS/workspace observations
remain sampled, and temporary file contents removed by the original executable
were not captured or reconstructed.

These cases do not establish all thread counts, all compression levels, equal
score winner invariance, biological correctness, BAI correctness, binary
authenticity, or complete paper reproduction. The original source retains its
own tie and spill semantics. P110/P198 author parameters and builds remain
incompletely reported. Complete current-tree catalog source qualification is
still pending; historical guards and saved identities must not be bypassed.

## Repeat or read the controls

Use the approved Linux/WSL Python and a JDK with `javac`, plus the development
JAR and exact permitted executables described in the execution guide. All output
directories must be new. For example, from a Linux project root after setup:

```sh
python3 -B tools/literature/sambamba_execution_validation/adversarial.py \
  --jar build/cli/omicsmatic-0.4.0.jar \
  --base /path/to/paired_quality/0.8.2/mark/analysis \
  --binary /path/to/exact/0.8.2/bin/sambamba \
  --out build/sambamba-adversarial-new
python3 -B tools/literature/sambamba_execution_validation/spill_controls.py \
  --jar build/cli/omicsmatic-0.4.0.jar \
  --binary066 /path/to/exact/0.6.6/bin/sambamba \
  --binary082 /path/to/exact/0.8.2/bin/sambamba \
  --pairs 30000 --out build/sambamba-spill-new
```

The adversarial base must be the complete, four-record `paired_quality` **mark**
bundle from `run_cases.py` or an equivalent execution with the pinned build.
The harness refuses a different record count and preserves the base. Supply
exact local paths; placeholder paths above must be replaced. The Java harness
does not replace or patch scientific functions, original executables or their
outputs except within explicitly labelled counterfeit control copies.

Read back saved public records with:

```sh
python3 -B tools/literature/sambamba_execution_validation/qualification_records.py
```

Add `--private-base build/sambamba-qualification` to check every retained local
member under the two recorded control roots. This verifies saved evidence and
current source identities; it does not launch an analysis or qualify a new build.
The status record distinguishes source correspondence, historical candidate
identity, actual recorded executions and the remaining scope restrictions.
