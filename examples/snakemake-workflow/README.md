# Synthetic original Snakemake workflow

The example adds two invented counts, 2 and 3, and preserves explicit large and
small configuration values. It demonstrates orchestration, capture, benchmark
recording and replay. It performs no biological inference.

Use the current source build and a qualified Linux runtime adoption. See the
[workflow guide](../../docs/literature/SNAKEMAKE_WORKFLOWS.md) for setup, selected
versions, installation permissions, limitations and recovery instructions.

Inside Linux, from this repository:

```bash
SNK_RUNTIME=/absolute/path/to/runtime/adoption
SNK_EXAMPLE="$HOME/snakemake-example-NEW"
SNK_JAR=/absolute/path/to/omicsmatic-0.4.0.jar

python3 -I -B examples/snakemake-workflow/prepare.py \
  --runtime "$SNK_RUNTIME" --out "$SNK_EXAMPLE"

java -jar "$SNK_JAR" snakemake-tools run \
  --request "$SNK_EXAMPLE/recipe.json" --runtime "$SNK_RUNTIME" \
  --out "$SNK_EXAMPLE/analysis"

java -jar "$SNK_JAR" snakemake-tools verify \
  --bundle "$SNK_EXAMPLE/analysis/bundle" --out "$SNK_EXAMPLE/verification"

java -jar "$SNK_JAR" snakemake-tools replay \
  --bundle "$SNK_EXAMPLE/analysis/bundle" --runtime "$SNK_RUNTIME" \
  --out "$SNK_EXAMPLE/replay"
```

The synthetic summary is `analysis/workspace/published/summary.tsv`:

```text
total	huge	tiny
5	9007199254740993	1e-300
```

The workflow also creates an original Snakemake benchmark report. Find its
workflow directory in `analysis/invocation.json`, under `bindings.workflow`;
the report is `benchmarks/count.tsv` inside that directory. Do not assume an
uncaptured caller directory is the execution directory. Software provenance is
retained in the captured working state; its installation paths are not declared
as a byte-identical scientific output across fresh installations.

Use `prepare.py --help` for Conda, Apptainer or combined mode. Managed modes need
`--providers`. Installation permission defaults to false. With no matching
existing cache, the resulting analysis must **SKIP3**. Supply a complete immutable
cache at its original prefix, or explicitly use `--allow-install` where permitted
to create new owned caches. The generator itself never installs or executes
software. Keep all warnings and failed attempts; retry new output paths.
