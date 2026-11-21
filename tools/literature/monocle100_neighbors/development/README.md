# Unfinished Monocle1.0.0 neighbor adapter

**Development snapshot: do not use for scientific results.** These five Java
files and eleven resources were moved byte-for-byte from `src/main` for handover.
They are excluded from the normal build, executable JAR and root CLI dispatcher.
The published, tested `Monocle100Weights` native component remains available.

The latest quick-compiled Linux Java pilot failed before neighbor calculation:
`Changed canonical original function: slam rowapply_simple_triplet_matrix`.
No weight graph or statistical result was admitted. The exact failure, process
receipts, earlier direct-worker attempts, input CDS objects and source snapshots
are retained in the [handover packet](../../../../docs/literature/handover-2026-11-21-neighbors/README.md).
An earlier worker passed before the final fingerprint guard; it does not qualify
the current draft. Do not remove the guard or refresh its expected hash to pass.

Resume from the [ordered handover](../../../../docs/literature/HANDOVER_2026-11-21_NEIGHBORS.md).
For development, restore `java/org/...` to `src/main/java/org/...` and
`resources/org/...` to `src/main/resources/org/...` in a new worktree only after
checking for existing files and verifying saved hashes. The original parent
scripts still target those compiled-resource paths; relocation did not rewrite
them. Use new evidence directories. Strict compilation alone is not execution
qualification. No complete graph-learning, paper pipeline, or catalog credit is
claimed. Preserve original package source, warnings, failures and all prior owners.
