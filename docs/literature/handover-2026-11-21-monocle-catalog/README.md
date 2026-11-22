# Saved unapplied Monocle catalog handover

Read [the handover](../HANDOVER_2026-11-21_MONOCLE_CATALOG.md) first.
Canonical counts remain144 families. The planned145-family state is not applied.
Exact draft files are outside active tool/source/catalog paths. No family credit,
release, Pages deployment or whole-goal completion is claimed.

INVENTORY.json maps evidence.zip SHA256 blobs. WORKTREES.json maps57 existing
worktrees' Git-visible file variants, deletion states and staged patches to
baseline Git blobs or deltas.zip. PROFILE.json pins saved source/canonical/packet
bytes. PRIVATE-TRANSFER.json lists material requiring separate private transfer.

Run `python -B tools/literature/verify_monocle_catalog_handover.py --out NEW`;
add --restore for copies in NEW only. Python3.11+ and baseline Git history required.
The reader executes no model and applies no patch. SHA256 establishes integrity,
not scientific correctness or producer authentication. Historical readers stay
frozen; use their actual unchanged owners or exact commits.
