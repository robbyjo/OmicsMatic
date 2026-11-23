# SCENIC+ original-runtime caller sources

`worker.py` is our own API caller. Its packaged mirror is under
`src/main/resources/org/jlinalg/omics/scenicplus-workflow/`; preserve exact bytes
of both caller and hash-only `SOURCE-PROFILE.json` when building. No original
SCENIC+ source, binaries or runtime cache is included here.

The current operation is prepared region-to-gene GBM inference, with explicit
Spearman/Pearson correlation and dropout masking. Use the Java class
`org.jlinalg.cli.ScenicPlusToolsCli`, which captures inputs, fingerprints the
runtime, acknowledges the worker and owns process lifetime. Direct worker
execution is unsupported and waits for that acknowledgement.

The profile records 41 unchanged original SCENIC+ Python members, 73 source
files in four pinned Git dependencies, and full metadata-origin populations
before and after selected imports. Post-import metadata includes legitimate
bundled duplicates; entries are not silently overwritten by package name.
Runtime manifests additionally fingerprint complete recorded package locations,
Python executable and stdlib, with the limitations in the component guide.

Keep all generated captures private: vendor tracebacks can contain licensed
source lines. The user accepted the specific unmodified academic-runtime terms;
that does not authorize public source/runtime redistribution or unrestricted
machine transfer. Never monkeypatch or port the licensed vendor implementation.

Read [the development guide](../../../docs/literature/SCENICPLUS_R2G_DEVELOPMENT.md),
[the artificial example](../../../examples/scenicplus-r2g/README.md) and
[the handover](../../../docs/literature/HANDOVER_SCENICPLUS_2026-11-23.md).
Full eGRN/metacell/resource integration, global dispatch, complete qualification
and guarded family/paper-role admission remain unfinished. This source alone
earns zero family/review credit. Preserve failed captures and exact older JARs.
