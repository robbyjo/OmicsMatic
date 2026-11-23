# Artificial SCENIC+ region-to-gene example

This development component calls an unchanged, privately installed SCENIC+
v1.0a2 runtime. These are wholly artificial inputs: 64 cells, four genes, 12
regions and 16 candidate pairs. Literal `NA`, `0001` and Unicode cell labels
are intentional. A constant accessibility region intentionally produces undefined
correlations. Keep those outputs and read their field-specific ledger.

**The separate masked Pearson control fails in the original v1.0a2 API with an
array-shape error.** It remains FAIL2 and is not a qualified masked analysis.
Do not silently turn off requested masking or patch the licensed implementation;
preserve the failure and obtain a vendor-supported correction or separately
qualify another permitted edition before retrying that operation.

**The complete eGRN workflow and catalog admission remain unfinished. No new
family or paper-review credit is claimed. Keep generated captures private:**
the runtime's errors/traces may contain licensed vendor source. Follow its
accepted academic-use, copying and transfer terms.

From a Linux/WSL shell, with a current complete source-built project JAR and the
already permitted private runtime, choose NEW output directories:

```bash
python3 examples/scenicplus-r2g/run.py \
  --jar /absolute/path/to/omicsmatic-0.4.0.jar \
  --python /absolute/path/to/permitted/environment/bin/python \
  --out /existing/private/parent/r2g-run

python3 examples/scenicplus-r2g/run.py \
  --jar /absolute/path/to/the-same/omicsmatic-0.4.0.jar \
  --mode VERIFY --analysis /existing/private/parent/r2g-run \
  --out /existing/private/parent/r2g-verify

python3 examples/scenicplus-r2g/run.py \
  --jar /absolute/path/to/the-same/omicsmatic-0.4.0.jar \
  --mode REPLAY --analysis /existing/private/parent/r2g-run \
  --out /existing/private/parent/r2g-replay
```

The driver uses an argument array, creates a new request beside the capture,
and propagates the Java exit code. It does not install software or run WSL
implicitly. VERIFY and REPLAY call the original computation again; `--inspect`
on the Java class checks saved closure without repeating the science.

Read `WARNINGS.md`, `worker/report.json`, `worker/out/SUMMARY.json` and
`worker/out/field-ledger.json`. **SKIP3** requires restoring the named permitted
runtime/input or leaving the dependent analysis skipped. **FAIL2** requires
preserving evidence, fixing the reported cause and retrying fresh output paths.
See [the component guide](../../docs/literature/SCENICPLUS_R2G_DEVELOPMENT.md)
for supported inputs, runtime scope and unfinished work.
