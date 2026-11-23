# Own caller for original SCENIC+ unpaired preparation

This directory contains our orchestration code and hash-only runtime profile.
The separately permitted private vendor runtime remains unmodified. The
`r2g_runtime.py` checker is our previously published checker copied unchanged;
the three runtime files are mirrored under
`src/main/resources/org/jlinalg/omics/scenicplus-prepare/`.

Use `org.jlinalg.cli.ScenicPlusPrepareCli` from the complete source-built JAR.
See [examples](../../../examples/scenicplus-prepare/README.md) and the
[workflow contract](../../../docs/literature/SCENICPLUS_PREPARE_WORKFLOWS.md).
`--inspect` checks saved byte closure only. `VERIFY` and `REPLAY` call the
unchanged original scientific API in fresh workers. No vendor source, executable,
fitted object, raw trace or licensed cache is distributed here.
