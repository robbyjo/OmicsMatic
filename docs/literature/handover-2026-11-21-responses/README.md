# Saved response-preparation handover

Read [the handover](../HANDOVER_2026-11-21_RESPONSES.md) first. All response
drafts remain outside compiled source/test trees. The original fixture and full
qualification are pending. Partial original results are not admitted. Production
source, build, catalog and site are unchanged. No integration credit is added.

INVENTORY.json maps evidence to evidence.zip SHA256 blobs. WORKTREES.json maps
54 historical dirty worktrees to baseline Git blobs or deltas.zip, preserving
staged patches and deletion states. No patch is applied automatically. PROFILE.json
pins packet/source/canonical bytes; SYNTAX-VALIDATION.json scopes syntax checks.
PRIVATE-TRANSFER.json lists excluded material requiring separate transfer.

Run `python -B tools/literature/monocle_responses/verify_handover.py --out NEW`
with Python3.11+ and Git. Add --restore to extract into NEW only. It executes
no R/Java/model. SHA256 proves integrity, not scientific correctness or identity.
Use the actual unchanged build/mo-5 owner for the frozen P96 reader; do not
refresh its saved HANDOVER_CURRENT hash to match this newer page.
