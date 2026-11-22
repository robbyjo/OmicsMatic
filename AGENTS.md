# Finishing feature work

The user requires disk cleanup as part of finishing every feature. After the feature's validation and any requested publication are complete, remove its verified stale temporary files before reporting completion. Do this during the feature task; do not leave cleanup for a later request.

- Identify the task's temporary checkouts, compiler outputs, intermediate copies, and download caches. Remove those that are no longer needed and can be reproduced from retained source or verified replacements.
- Check current process ownership, file changes, Git status, merge state, nested worktrees, and applicable instructions first. Preserve active jobs, changing worktrees, unique local edits, original inputs, required runtimes, and credentials. Never stop an unrelated process to make cleanup possible.
- Before retiring a temporary Git worktree, confirm its commits remain reachable in persistent refs and recheck its HEAD and status. Keep branch refs and use `git worktree remove`. For a dirty worktree, verify that all local and staged changes are already represented in retained Git history and archive their exact bytes before removal. Keep unique or uncertain local edits in place.
- `build/` also contains scientific validation, release evidence, reports, logs, and runtimes. Keep retained evidence at its existing paths. Use the study cleanup workflow for governed study results; never infer that a result is disposable merely because it is old or under `build/`. Avoid blanket deletion of `build/` or broad `git clean` commands.
- Before a recursive deletion or move on Windows, verify each resolved absolute path stays within the intended task directory, check for reparse points, and use native PowerShell operations with literal paths.
- Record the removed paths, preservation checks, recovery locations, and measured free-space change in a cleanup receipt. Verify the main checkout's existing changes and unrelated work remain intact, then report the space recovered and anything deliberately retained.

Routine cleanup within the user's authorized task does not require another confirmation. Investigate uncertain data instead of deleting it or blocking unrelated safe cleanup.
