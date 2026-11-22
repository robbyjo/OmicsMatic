# FUSION handover snapshot, version 2

**UNQUALIFIED DEVELOPMENT SOURCE.** This snapshot preserves all 39 latest draft
files, artificial examples, generators, exact orchestration helpers and selected
synthetic execution receipts. It is outside the normal source set. Main's
production command is unchanged; there is no new family/catalog credit.

Read the [current handover](../../../docs/literature/FUSION_WORKFLOW_CHECKPOINT_2026-11-22.md)
for completed checks, failed historical attempts, exact remaining work, runtime
locations, private transfer and prominent SKIP3/FAIL2/process/export notices.
The older `fusion-workflow-20261122/` snapshot remains immutable and historical.

From the repository root, check the saved inventory without R or network:

```powershell
python -B tools/development/fusion-workflow-20261122-v2/verify_snapshot.py
```

Restore into a NEW clean development worktree based on this handover commit:

```powershell
git worktree add -b codex/fusion-resume ../omicsmatic-fusion-resume HEAD
cd ../omicsmatic-fusion-resume
git -c core.autocrlf=false apply --check tools/development/fusion-workflow-20261122-v2/draft.patch
git -c core.autocrlf=false apply tools/development/fusion-workflow-20261122-v2/draft.patch
python -B tools/development/fusion-workflow-20261122-v2/verify_snapshot.py --source-tree .
```

Choose a different NEW branch/path if those names exist. Do not apply over dirty
files or an existing restored draft. The patch includes binary artificial PLINK
and RData fixtures. Disabling automatic newline conversion is required to retain
their source-bound identities; the snapshot itself also has Git byte-preservation
attributes. The patch was actually restored and all 39 files compared exactly.
Building the restored draft enables its experimental command only in that new
development checkout; finish qualification before admitting it to production.

`source/` is a mirror of the draft paths, not a runnable project by itself. Its
example README describes the proposed development workflow; read the handover
first. Its paths only make sense after restoration at the repository root.
Machine-specific helper scripts are historical executable records, not an
idempotent installer: they use NEW output directories, private runtimes and exact
producer paths. Inspect them and choose fresh paths before any rerun. Some
prepared helpers have never been executed, as recorded in the handover.

The newest producer has 75 passing tests and the targeted COLOC rounding/NA
regression. The 29-case packaged suite belongs to the preceding producer; do not
promote its evidence to the newer producer. Complete capture data, original and
failed evidence, actual JARs, runtimes and binaries require private transfer.
Hash checks establish integrity, not independent scientific validation or
authorship authentication. No whole-tool, whole-paper or full-goal completion.
