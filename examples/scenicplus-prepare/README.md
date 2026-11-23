# Original unpaired preparation

Run this on Linux with the exact permitted private SCENIC+ runtime and a complete
source-built OmicsMatic JAR. Supply your own RNA AnnData and **trusted** original
cisTopic pickle containing the selected fitted model. Pickle loading executes
code: do not load files from untrusted sources. No licensed objects or runtimes
are included in this example.

```bash
python3 examples/scenicplus-prepare/run.py \
  --jar /absolute/path/omicsmatic-0.4.0.jar \
  --python /absolute/path/private/environment/bin/python \
  --rna /absolute/path/RNA.h5ad \
  --cistopic /absolute/path/trusted-selected-model.pkl \
  --out /absolute/path/NEW-preparation \
  --group-key cell_type --use-raw false \
  --metacells 4 --cells-per-metacell 5 --separator '|'
```

`--use-raw true` requires the original `RNA.raw`. `false` uses the supplied
processed `.X`; it performs no normalization. Choose deliberately. Omit
`--metacells` for the original automatic count. Both assays need the same group
column and shared annotation labels; cell barcodes may be completely different.
Choose a separator absent from every shared annotation label.

**Warnings and remedies:** exit 3 means a prerequisite is unavailable: restore
the exact permitted private runtime/input or retain the skipped analysis. Exit 2
means no complete result: read console output, `FAILURE.md`, `analysis-status.json`
and private `worker/report.json`, preserve the evidence, fix the named cause and
retry a NEW output directory. Per-group cells-per-metacell mappings are unsupported
because the original API raises `TypeError`. A JSON `nr_metacells` mapping is
supported; see the [workflow reference](../../docs/literature/SCENICPLUS_PREPARE_WORKFLOWS.md).

On success read `PREPARATION-WARNINGS.md` and `worker/out/SUMMARY.json` for shared
and omitted groups. Captures and source-bearing traces stay private under the
accepted academic terms. This component does not establish complete SCENIC+
admission or biological validity.
