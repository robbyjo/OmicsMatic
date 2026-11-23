# Qualified workflow handover packet

Read [the current handover](../SQUIDPY_QUALIFIED_HANDOVER_2026-11-22.md).
`CHECKS.json` records fresh saved-checkpoint, canonical catalog and all250 primary
audit checks. `READER-CONTROLS.json` records valid Windows/Linux qualification
reads plus four contradictory checkpoint refusals on each host. `ROLES.json`
is a source-only audit, not catalog admission. `DRAFT-SYNTAX.json` records syntax
checks, not qualification of the draft model/controller. The current catalog
remains151 scoped families,1,679 reviewed uses and1,043 pending uses.

The completed original-science qualification is retained separately at
[`../squidpy-workflow-qualification/`](../squidpy-workflow-qualification/STATUS.json).
`control-recovery.zip` and `CLEANUP-PLAN.json` restore the exact temporary negative
reader copies; source-identical bytes are referenced from the checked-in source.
After publication, `CLEANUP.json` records their removal and measured free space.
`FILES.json` pins this packet. The maintenance scripts are historical exact
helpers, use fixed output paths, and must not be blindly rerun.

```powershell
python -B docs/literature/squidpy-workflow-qualified-handover-20261122/verify_checkpoint.py
```

The reader uses the Python3.11+ standard library. It reads preserved records and
bytes; it does not rerun Java/Python analyses, install software or use the network.
The full goal remains unfinished. Private runtimes, original primary text,
complete local evidence and restricted historical snapshots are not uploaded.
