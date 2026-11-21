# Historical dirty worktree snapshots

**These are retained historical working copies, not the current build or catalog.**
The [current handover](../HANDOVER_2026-11-21_LOCAL_CLUMPING.md) gives the next
actions and qualified baseline. The current local-clumping worktree is saved
separately in [its development packet](../twosamplemr-clump-handover/README.md).

`WORKTREES.json` records all 40 predecessor worktree heads, branch names, raw
Git status, Git-visible changed/untracked file bytes, modes and staged patches.
Git-visible bytes already identical to the qualified baseline are referenced
by their Git blob ID; all other bytes are in content-addressed `deltas.zip`.
This avoids replacing current main with older catalog and code variants.
SHA256 pins the complete files; line ending differences are retained exactly.
The original owners and their indexes are preserved locally.

The referenced base is `51a3115051832b26cb5b8036ca940d806db895cf`, reachable from
main. **Use a full Git clone including that commit**, not a GitHub source ZIP or
shallow clone lacking the base. Git blob contents can be read with
`git cat-file blob <oid>`. Archive entries use `blobs/<sha256>`. Reconstruct only
in a NEW separate directory, check each resulting byte count and SHA256 against
`WORKTREES.json`, and review historical staged patches before applying them.
Do not overwrite active worktrees or promote old scientific claims to current.
The reader below validates the packet and referenced Git objects; it does not
automatically restore files or execute scientific software.

```powershell
python -X utf8 -B tools/literature/verify_handover_worktrees.py --out build/historical-worktrees-readback-new
```

**Git-ignored files are not in these snapshots.** Installed runtimes, producer
JARs, primary full text and complete private/failed scientific evidence require
a separate checksummed transfer. This is not a machine backup. Local-clumping
development is incomplete, adds zero catalog credit and does not complete the
250-paper implementation and source-review goal.
