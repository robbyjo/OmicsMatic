# Own prepared SCENIC+ network caller

`worker.py` orchestrates unchanged original TF2G/R2G/GSEA/triplet/activity APIs.
`r2g_runtime.py` is an exact copy of **our own** previously tested R2G runtime
checker, imported as a helper. It is not vendor code. `SOURCE-PROFILE.json`
contains versions, metadata origins and source identities, not original source.
The three files have exact packaged mirrors under
`src/main/resources/org/jlinalg/omics/scenicplus-network/`.

Use `org.jlinalg.cli.ScenicPlusNetworkCli` from a complete source-built JAR. The
worker waits for the Java source/input/runtime acknowledgement before computing.
Direct worker execution is unsupported. Preserve the existing R2G component
and its historical producer/captures separately.

The protocol declares C-contiguous float64 input arrays and an explicit
`CLI_TSV` or `IN_MEMORY_API` interface. Original CSV boundary conversions,
nonfinite values, source null module statistics and failed masked calls remain
visible. Module statistics and numerical values are not inferred from success.
Full VERIFY and REPLAY actually recompute; metadata inspection does not.

All generated captures are private because original traces can contain licensed
source. Do not modify, port or redistribute SCENIC+ vendor code/runtime; follow
the accepted private academic-use and transfer terms. Read the
[guide](../../../docs/literature/SCENICPLUS_NETWORK_WORKFLOWS.md) and
[artificial example](../../../examples/scenicplus-network/README.md).

Unpaired/metacell preparation, motif discovery/Cluster-Buster roles, global
dispatch and guarded catalog admission remain unfinished. No family/review
credit or whole-paper/biological reproduction is claimed here.
