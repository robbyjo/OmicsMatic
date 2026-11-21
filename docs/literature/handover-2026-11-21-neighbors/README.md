# Saved handover and historical worktree variants

**The Monocle neighbor adapter is unfinished and failed its latest guard check.**
Read the [ordered handover](../HANDOVER_2026-11-21_NEIGHBORS.md) before continuing.
The draft is excluded from compiled sources. No new result or catalog credit is
admitted by this packet.

`PROFILE.json` pins the current development sources, unchanged canonical files,
evidence archive, inventory and all49 historical worktree snapshots.
`INVENTORY.json` maps synthetic attempt/error evidence to `blobs/SHA256` in
`evidence.zip`. `WORKTREES.json` maps Git-visible dirty files and staged patches:
unchanged baseline files use exact Git blob IDs; other bytes are in `deltas.zip`.
Deletion entries and original status/mode/branch/head are retained. Restoring
snapshots does not merge them, execute them or qualify them as current source.

Run the source-pinned reader with Python3.11+ and Git, from the project root:

```powershell
python -B tools/literature/monocle100_neighbors/verify_handover.py --out build/NEW-handover-check
```

Add `--restore` to extract into NEW output only. This verifies exact file bytes,
recorded failure markers and the declared snapshot population. It performs no
RDS deserialization, scientific calculation, original R call, runtime install,
Java build, worktree modification or automatic staged-patch application.
SHA256 protects integrity; it does not authenticate the producer.

Compatible source archives and complete licenses are under `upstream/`.
SLAM GPL-2-only source/archive and earlier resource drafts containing that source
are excluded; fingerprint metadata remains. Java bytecode, runtimes, full primary
text and licensed author source are omitted. **Transfer those permitted private
artifacts separately before decommissioning the machine**, following
`PRIVATE-TRANSFER.json` and the preceding transfer records. Preserve all attempts.
