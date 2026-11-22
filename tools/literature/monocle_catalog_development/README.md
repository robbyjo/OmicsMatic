# Unapplied Monocle catalog development files

Read [the handover](../../../docs/literature/HANDOVER_2026-11-21_MONOCLE_CATALOG.md).
These are exact draft files, outside active tool/catalog locations. The nested
integration guide describes a proposed 145-family state; canonical main remains
144 families. Apply, installed verification/UI and final admission are pending.

RESTORE.json maps every saved file to its intended repository path, exact bytes
and baseline target (null means absent). Restore deliberately into a NEW P99-based
worktree, comparing existing targets first. No patch is applied automatically.
The draft's relative links/imports resolve only after deliberate restoration to
their intended locations. Machine-specific sibling/runtime paths need review.

Keep original build/mo-8, its plan, captures and all failed attempts. If any source
or guide changes, preserve the old plan and create a NEW one. Check the handover's
scope and required-action warnings before executing any draft command.
