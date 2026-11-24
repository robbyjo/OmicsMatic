# Scanpy from the main command

Build current source with `./gradlew executableJar`, then use Java 17+:

```bash
java -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --help
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --request /absolute/path/request.json
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --verify /absolute/path/complete-capture
```

The global command calls the existing ordered Scanpy workflow. The standalone
`org.jlinalg.cli.ScanpyWorkflowCli` entry point also remains available.
Published 0.4.0 predates both entry points; use the current source build or the
exact retained normal candidate named in the checkpoint. A restored native
Pearson/preprocessing development library is a separate API artifact.

For a complete synthetic RUN request, follow the existing
[60-cell example](../../examples/scanpy-workflow/README.md). Its exact input
ZIP, hash-checking request preparer and 13-step protocol remain unchanged.
Use the `java -jar ... scanpy-tools` command above with the generated request.
The [workflow guide](scanpy-workflow.md) describes explicit layers/raw,
MatrixMarket orientation, typed metadata, supplied H5AD, capacities and logs.
Only the two sequences covering 15 operations are qualified; 29 development
dispatch entries are not 29 qualified analyses.

Scientific RUN/REPLAY needs the exact permitted Linux CPU runtime: Python
3.13+ with the 14 versions and 143 Scanpy source hashes in the packaged
1.13.0 profile. The request names that runtime explicitly. Downloads/installs
are allowed where the environment permits; install into NEW environments and
retain installer/version receipts. The command never silently changes engine
or scientific settings to accommodate a missing dependency.

VERIFY is Python-free. To inspect the compact synthetic captures:

```bash
python -B tools/literature/verify_scanpy_dispatch.py --out build/scanpy-global-proof-NEW --restore-capture
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --verify build/scanpy-global-proof-NEW/capture
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar scanpy-tools --verify build/scanpy-global-proof-NEW/replay-capture
```

The two restored captures are explicit derivatives of actual global RUN and
REPLAY. JIT caches are omitted; original scientific inputs, outputs, source,
producer, warnings and logs are retained, with derivation records and new
integrity seals. The originals remain local and unchanged. VERIFY checks
captured consistency and exported structure; it does not independently refit
models, interpret every H5AD feature, prove author execution or authenticate
coordinated fabricated evidence.

For actual REPLAY, write a control request with NEW output:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"/absolute/path/complete-capture","out":"/absolute/path/NEW/replay","python":"/absolute/path/exact/environment/bin/python"}
```

Pass that JSON to `scanpy-tools --request`. REPLAY verifies its parent capture
before running the original requested operations again. Moving machines may
require an explicit runtime binding; it does not authorize changing analysis
parameters. Cross-host stochastic byte equality is not promised.

**SKIP3 — dependent analysis skipped:** restore the exact named permitted
input/runtime/source or retain the skip if it cannot be installed or executed.
**FAIL2 — no complete result:** preserve inputs, partials and diagnostics;
correct the named schema/data, numerical, integrity, capacity or process cause
and retry NEW output. Read `WARNINGS.md`, `analysis-status.json`,
`worker/report.json`, `worker/status.json` and logs before downstream use.
Never silently repair IDs, dtype, layer/raw choice, thresholds or NaN/Inf.

The [checkpoint](../literature/SCANPY_DISPATCH_CHECKPOINT_2026-11-19.md) lists
qualification, historical verifier recovery, remaining work and transfer paths.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Scanpy developers (2026) — Scanpy API and tutorials](../CITATIONS.md#scanpy-api-documentation)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
