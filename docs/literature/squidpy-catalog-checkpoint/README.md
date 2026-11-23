# Squidpy admission checkpoint

Read [the current checkpoint and warnings](../SQUIDPY_CATALOG_CHECKPOINT_2026-11-22.md).
`STATUS.json` binds current canonical/source bytes and the content-addressed
`EVIDENCE.json`/`evidence.zip` packet. The279 logical entries retain current and
earlier checks, plans, receipts, browser screenshots and exact failed-test/checker
source. Full paper XML, private runtimes and browser profiles are excluded.
`VALIDATION.json` retains the33 existing website-citation failures and remedies.

`READER-CONTROLS.json` records valid Windows/Linux reads and three contradictory
checkpoint refusals per host. `reader-checks/` preserves their stdout/stderr.
`READER-REFINEMENT.json`/`reader-draft1.zip` retain the unpublished preliminary
reader context before explicit reported-counter binding; original scientific
inputs, methods and results were unchanged. `reader-control-recovery.zip` and
`CLEANUP-PLAN.json` restore the completed temporary negative-reader copies.
After publication, `CLEANUP.json` records their narrowly scoped removal and
measured free space. `FILES.json` inventories this complete publication packet.

```powershell
python -B tools/literature/verify_squidpy_catalog_checkpoint.py
python -B tools/literature/close_squidpy_catalog.py --verify --out build/NEW-squidpy-catalog-check
```

Use Python3.11+ with assertions enabled. These commands read existing evidence;
they do not rerun original analyses or authenticate a host. A matching SHA256
establishes byte integrity. Older source-bound catalog readers need their
recorded historical Git contexts. Do not blindly rerun maintenance helpers with
their fixed output names. No full-tool, whole-paper, full-website or full-goal
completion is claimed; private transfers and remaining implementations/reviews
are still required.
