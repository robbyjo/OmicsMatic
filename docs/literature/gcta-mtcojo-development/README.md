# Saved mtCOJO development checkpoint

Read [the handover and exact remaining tasks](../HANDOVER_2026-11-20_MTCOJO.md).
Full mtCOJO qualification, global registration and catalog admission remain open.
No GCTA/COJO credit is added. Counts remain 140 admitted scoped integrations,
1,548 reviewed uses and 1,166 pending. The 250-paper cohort is complete.

`evidence.zip` stores 2,204 logical files as 353 distinct SHA-256 blobs;
`INVENTORY.json` maps the exact logical paths, lengths and hashes. This retains
synthetic inputs, original outputs including NaN/failure tables, prototype and
normal captures, build source snapshots, raw test/process logs and receipts,
and selected official GPL source files with their `LICENSE`. It contains no
original executable, runtime DLL, participant data, full paper text or product
JAR. The separate two test-corpus ZIPs contain frozen synthetic reference data.

The normal producer passes 24 focused tests on three JVMs, including 300,000
original conditional-field comparisons per JVM, eight saved mtCOJO captures
and a resealed nonfinite-SE refusal. It also verifies 56 earlier complete GCTA
captures per JVM. Eight actual normal-JAR compositions match original tables
and pass separate-JVM VERIFY. Eighteen additional original behavior runs retain
the source's prevalence, sentinel, decimal-option and failed-GSMR behavior.
See the handover for precise limits and unfinished execution-path qualification.

Use Python 3.11+ from the exact handover checkout:

```text
python tools/literature/gcta_mtcojo/verify_saved.py --out NEW_RECEIPT_DIRECTORY
```

Add `--restore` to restore byte-exact logical files beneath the new receipt
directory; add `--jar PATH` only with the exact saved normal producer. The reader
checks source/resource/archive hashes, saved receipt assertions, all eight
saved normal/original table comparisons, selected raw source-behavior controls,
and all 300,000 original reference fields against their recorded export bounds.
It never runs Java, original tools or model scripts. A saved-byte check does not
rerun the independent oracle, establish provenance authenticity or validate a
biological analysis. SHA-256 provides integrity, not a signature.

`PROFILE.json` freezes the reader, helper, Java source and test-resource hashes.
Run it in this exact owner; do not refresh hashes to accept later changed code.
Earlier GCTA profile owners remain separate and preserved. Private original
runtimes, exact producer JARs, complete historical evidence/full text and all
27 worktrees require a separate checksummed transfer when moving machines.
Both literature reports remain withheld from Pages. No release is made.
