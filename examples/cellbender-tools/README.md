# Synthetic CellBender count correction

This example contains **synthetic raw unfiltered UMI counts**, including empty
droplets: 1,000 barcodes, 100 genes and 100 simulated cells. It contains no
patient data. Keep the empty droplets; this example does not begin with reads.

Use the development executable that includes `cellbender-tools`. The command
needs Linux Java and a previously admitted private CellBender CPU runtime on
Ubuntu 24.04 x86_64. On Windows, run Linux Java inside WSL. The example does not
install anything. Cell Ranger is excluded and is never executed.

```sh
java -jar JAR cellbender-tools --cellbender-options-file examples/cellbender-tools/options.json --home /path/to/admitted-cellbender-home --out build/cellbender-example-NEW --no-config
```

The three raw-count paths in `options.json` resolve relative to that file.
Direct command-line overrides resolve relative to the current directory. A
UTF-8 JSON carrier can hold Unicode input/output paths while the launcher uses
an ASCII carrier filename.

For an independent comparison with the original software's synthetic reference:

```sh
python3 tools/literature/verify_cellbender.py --jar JAR --home /path/to/admitted-cellbender-home --out build/cellbender-verification-NEW
```

Python's standard library is sufficient for this verifier. It runs a fresh fit,
checks every corrected coordinate and cell probability against `reference.json`,
and independently checks conservation, cell marginals, strict `p > 0.5` calls,
filtered matrix subsetting and input hashes. The reference predates the project
adapter and comes from the unmodified original 0.4.0 CPU full/MCKP model with
150 epochs, FPR 0.01 and two CPU threads. Counts are 178,754 raw, 123,071 corrected
and 100 called cells. This small fixture is a regression check, not evidence of
real-data accuracy or a model-convergence criterion.

**Warnings and recovery:** read the root `WARNINGS.md`, `analysis-status.json`,
`session.json`, count/feature ledgers, learning curves and original report/logs.
Exit 3 means a dependency was unavailable and the analysis was skipped; exit 2
means input, process or audit failure. Correct the named cause and use a new
output path. Failed artifacts remain under `partial/`. Never substitute zero
counts for a failed analysis. Where installation is permitted, follow
[`CELLBENDER_WORKFLOWS.md`](../../docs/literature/omics-2023-2026/CELLBENDER_WORKFLOWS.md).
CUDA requests skip on this CPU profile. No historical release or paper-wide
reproduction is claimed.
