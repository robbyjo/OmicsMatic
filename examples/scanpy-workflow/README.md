# Explicit original Scanpy workflow example

This is a synthetic 60-cell, 80-gene example, with no participant data. It
selects 13 ordered operations: copy counts, QC, normalize, log1p, snapshot raw,
Seurat-flavor HVGs, scale, PCA, neighbors, Leiden, UMAP, a gene score and
tie-corrected Wilcoxon markers. It is not an inferred recipe for the 22 papers.

Use Java 17+ and build the current source with `./gradlew executableJar`.
The request-preparation helper requires Python 3.11+.
The public 0.4.0 release predates this command. The standalone entry point is
`org.jlinalg.cli.ScanpyWorkflowCli`; global CLI dispatch is not added yet.
Scientific execution currently has Linux CPU qualification; Python-free saved
bundle checks also run on Windows. Read the
[workflow guide](../../docs/vignettes/scanpy-workflow.md) for exact scope.

The declared Python needs Python 3.13+ and the exact 14 package versions in
[runtime-versions.txt](runtime-versions.txt), including Scanpy 1.13.0. Install
only into a NEW private environment where permitted. For example, after
creating a Python 3.13 virtual environment:

```bash
/path/to/NEW/environment/bin/python -m pip install -r examples/scanpy-workflow/runtime-versions.txt
```

This specifies the guarded package versions; it is not a universal,
platform-independent native-library lock. Preserve the actual wheel/installer
receipts and `pip freeze`. The worker checks installed versions and 143 Scanpy
source files before analysis. Supply the already qualified private runtime
when continuing on the original workstation; its exact transfer path is in
the [current handover](../../docs/literature/HANDOVER_CURRENT.md).

From the repository root, choose NEW paths:

```bash
python3 -m zipfile -e examples/scanpy-workflow/synthetic-inputs.zip build/scanpy-example-inputs-NEW
python3 examples/scanpy-workflow/prepare_request.py --python /path/to/environment/bin/python --inputs build/scanpy-example-inputs-NEW --request build/scanpy-example-request-NEW.json --out build/scanpy-example-result-NEW
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.ScanpyWorkflowCli --request build/scanpy-example-request-NEW.json
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.ScanpyWorkflowCli --verify build/scanpy-example-result-NEW
```

`prepare_request.py` verifies the three original synthetic input hashes and
writes an explicit request. It does not run or install scientific software.
The first scientific run may spend minutes compiling original numeric kernels.
Read `WARNINGS.md`, `analysis-status.json` and `worker/report.json`; retain the
whole attempt, including raw inputs, original warnings, logs and partials.

The result contains `worker/outputs/result.h5ad`, exact ordered cell/gene TSVs,
the final features-by-cells MatrixMarket matrix, embeddings/graphs, and the
original complete returned marker table. Layer/raw selections and every
executed step are recorded. `out=False` explicitly selects original AnnData
storage; the worker does not discard returned QC tables.

For actual REPLAY, save this control request and pass it to `--request`:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"/absolute/path/to/complete/bundle","out":"/absolute/path/to/NEW/replay","python":"/absolute/path/to/exact/environment/bin/python"}
```

**SKIP3:** the exact permitted runtime/input dependency is unavailable; skip
its dependent analysis and restore the named item. Installation being
forbidden is not permission to substitute another version or engine.
**FAIL2:** preserve failed/partial captures, correct the named data/source/
numerical/process/capacity cause and retry NEW paths. No complete result is
admitted. VERIFY works without Python but checks capture/source consistency
and exported numeric structure; it does not independently refit the models or
authenticate a coordinated fabricated capture. REPLAY executes upstream again.
