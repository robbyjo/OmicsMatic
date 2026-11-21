# mtCOJO development handover helpers

Read [the current handover](../../../docs/literature/HANDOVER_2026-11-20_MTCOJO.md)
before using these files. These are exact workspace-bound development-script
snapshots. They preserve source/input/output paths, original execution commands,
independent NumPy reference construction and exclusive-create destinations.
They are not installed product commands or a portable installer. Do not execute
them blindly: preserve every old attempt, provision the exact named licensed
runtime, and select new owners/output directories for new work. Wrapper scripts
also require their exact saved predecessor helper; no source hash is refreshed.

The product's Java sources and tests are under `src/`. The developer-only
`../gcta_workflows/PackagedMtcojoTests.java` launcher requires the exact normal
JAR first on its classpath, then its separately compiled tests/resources and
JUnit dependencies. Gradle runs the same 24 focused tests at this checkpoint.

Read saved evidence without running Java, original tools or these helpers:

```text
python tools/literature/gcta_mtcojo/verify_saved.py --out NEW_RECEIPT_DIRECTORY
```

Use Python 3.11 or later. `--restore` additionally restores byte-exact logical
files into the new receipt directory. This verifies saved bytes and assertions
in receipts; it does not rerun models or independently certify scientific results.
Use the handover commit as source owner. Both test corpora are wholly synthetic;
no participant data or original executable is redistributed here.
