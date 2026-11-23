# Unpaired preparation checkpoint

`STATUS.json` contains public source and private evidence identities plus scoped
validation results. It distributes no licensed runtime, source-bearing trace or
scientific capture. See the [checkpoint and resume instructions](../SCENICPLUS_PREPARE_CHECKPOINT_2026-11-23.md)
and [workflow contract](../SCENICPLUS_PREPARE_WORKFLOWS.md).

```bash
python3 tools/literature/verify_scenicplus_prepare_checkpoint.py
```

This checks source and saved identities only. Optional `--jar` checks the exact
retained complete producer; `--private-root` checks retained private evidence at
its original location. These checks execute no fresh preparation and rerun no
tests. Java `VERIFY` and `REPLAY` perform the fresh original calculations.
