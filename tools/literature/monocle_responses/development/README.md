# Unfinished response-preparation source

WARNING: development only, outside the main source and test trees. Read
[the handover](../../../../docs/literature/HANDOVER_2026-11-21_RESPONSES.md).
RESTORE.json maps four source files to exact saved bytes and prior target hashes.
Only the latest three production Java drafts were strictly compiled. The seven
draft API tests have not run; their original-controls.json.gz fixture is missing.
Do not copy the test into an active test tree before producing an admitted fixture.

Use a NEW worktree. Verify the saved hashes, compare any existing target with the
recorded P96 baseline, and copy deliberately after review. MonocleToolsCli.java
replaces an existing file; the other three are additions. No automatic patch is
applied. The original dirty build/mo-6 owner remains unchanged.

The R/Python helpers above retain machine-specific sibling-worktree and runtime
assumptions. Inspect and repoint a NEW copy when moving machines. reference.R
still has the final hash guard that failed reference attempt3. Diagnosis found
strict source equality but different serialized bytes; no fix or new qualification
is claimed. The reader verifies saved transport and executes no scientific method.
