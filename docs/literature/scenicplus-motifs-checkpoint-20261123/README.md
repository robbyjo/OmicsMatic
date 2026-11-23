# Motif/cistrome and explicit handoff checkpoint

`STATUS.json` records public source identities and private evidence identities.
It distributes no vendor runtime/source or source-bearing trace. Read the
[checkpoint and next steps](../SCENICPLUS_MOTIF_CHECKPOINT_2026-11-23.md) and
[workflow contract](../SCENICPLUS_MOTIF_WORKFLOWS.md).

```bash
python3 tools/literature/verify_scenicplus_motifs_checkpoint.py
```

This checks source/saved identities only. Optional `--jar` checks the exact
retained complete producer; `--private-root` checks retained private evidence.
It executes no fresh science and reruns no tests. Java VERIFY and REPLAY perform
fresh original motif/cistrome computation and compare the actual saved HDF5
logical arrays and attributes as well as the numerical exports.
