# Saved smartpca analysis development

Read [the complete handover](../SMARTPCA_ANALYSIS_HANDOVER_2026-11-23.md) first.
The numerical components are source-compared; the file workflow is unfinished
and grants no family/review credit. The full 250-paper goal remains open.

`STATUS.json` pins current source, evidence and the exact development producer.
`evidence.zip` retains corrected and failed original comparisons, source/build
contexts, normal-test XML and the predecessor-reader checks. `INVENTORY.json`
identifies every member. `helpers/` retains machine-specific orchestration
scripts; inspect paths before reuse. No original executable or private primary
text is included. Exact local/WSL producers still require private transfer.

```powershell
python -B tools/literature/verify_smartpca_analysis_handover.py
```

The reader checks saved integrity and declared evidence consistency; it does not
rerun original analyses, prove authenticity or qualify the unfinished workflow.
Use `--jar PATH` only with the exact producer recorded in `STATUS.json`.
