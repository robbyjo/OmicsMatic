# Saved unfinished graph handover

Read [the handover](../HANDOVER_2026-11-21_GRAPH.md) first. This packet preserves
development attempts and Git-visible worktree variants. It adds no scientific
qualification or catalog credit. Production source/build/site remain unchanged.

INVENTORY.json maps logical evidence to SHA256-named evidence.zip blobs.
WORKTREES.json maps historical dirty files to baseline Git blobs or deltas.zip,
with exact staged patches and deletion states. No patch is applied automatically.
PROFILE.json pins packet/source/canonical bytes. SYNTAX-VALIDATION.json describes
strict compilation and syntax checks only. PRIVATE-TRANSFER.json identifies
ignored material still requiring separate transfer; this is not a machine backup.

Run `python -B tools/literature/monocle100_graph/verify_handover.py --out NEW` from
the checkout; add --restore to extract into NEW without touching original owners.
The reader needs Python3.11+ and Git; it runs no R/Java/model. SHA256 verifies
integrity, not producer identity or scientific correctness. Use the exact clean
build/mo-3 owner or baseline checkout for the preceding frozen P94 verifier.
