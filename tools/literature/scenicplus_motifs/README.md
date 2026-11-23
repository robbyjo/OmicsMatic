# Own original-API motif orchestrator

Our Java controller calls unchanged SCENIC+ v1.0a2 scientific helpers for
cisTarget and DEM, then original `get_and_merge_cistromes`. This directory and
its classpath mirror contain our caller, our unchanged runtime checker and a
hash-only profile. No vendor source/runtime, fitted object or source-bearing
trace is distributed here.

Use `org.jlinalg.cli.ScenicPlusMotifsCli` from a complete source-built JAR. Read the
[example](../../../examples/scenicplus-motifs/README.md) and
[workflow contract](../../../docs/literature/SCENICPLUS_MOTIF_WORKFLOWS.md).
`--inspect` checks saved identities only. VERIFY and REPLAY run fresh original
computation and compare numerical exports and the actual saved HDF5 logical
arrays/attributes. Saved PyTables objects are not deserialized by this audit.
