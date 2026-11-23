# Saved SCENIC workflow evidence

Read [the handover](../HANDOVER_SCENIC_WORKFLOW_2026-11-23.md) first.
`STATUS.json` gives the scope, exact executable identity, unchanged catalog
totals and validation counts. `READBACK.json` records Windows/Linux checkpoint
readback and three controlled refusal checks; it does not claim a fresh fit.

`FILES.json` maps 4,212 logical evidence paths to byte lengths and SHA-256 hashes.
The ZIP stores each unique byte sequence once as `objects/<sha256>`: 1,248 objects,
39,752,731 uncompressed bytes. To read a logical file, look up its hash in the
manifest and read that object from the ZIP. Repeated runtime inventories are
deduplicated, not discarded. The original retained files are under the private
`build/scenic-3` tree; `OMISSIONS.json` explicitly lists omitted artifacts and
subtrees. No whole paper XML, author dataset, installed runtime or executable
producer JAR is supplied by this packet. Synthetic fixtures and saved failures
remain evidence of their stated scope only.

From the repository root:

```bash
python tools/literature/verify_scenic_workflow_checkpoint.py
```

This validates the archive, saved outcomes, source/resource mirrors, historical
dispatcher extension and unchanged catalog. It performs no original statistical
fit and provides integrity, not authorship authentication. The optional `--jar`
argument also checks the exact retained executable's bytes. Fresh scientific
recomputation is a separate `scenic-tools` VERIFY request requiring that exact
producer, matching permitted runtime and a new witness directory.

`PUBLICATION.json` records the implementation commit and public byte readback.
`CLEANUP.json` lists only removed, verified redundant compiler copies and their
recovery locations. `finalization.zip` and `FINALIZATION-FILES.json` retain the
exact readback/cleanup helper sources and receipts; private worktree inventories
and restricted historical payloads are excluded. Exact JARs, runtimes, source,
scientific captures and failed unit contexts remain retained locally.
