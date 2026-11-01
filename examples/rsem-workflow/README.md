# RSEM alignment-mode workflow example

**Validated local alignment-mode subset.** The source setup, documented preparation and fitting workflow have passed actual original controls. These files contain synthetic data only; the full toolbox objective and paper semantic review remain active.

Run every command inside Linux x86_64 or WSL with Linux Java 17 or newer and Python 3. Original RSEM paths must be canonical ASCII paths without spaces or shell metacharacters. Keep the compiler prefix, source setup, example data and fitting output separate. Existing outputs are preserved.

First obtain a permitted working private compiler prefix with GCC/G++, GNU make, ar, ranlib, zlib headers and libraries. Setup compiles the pinned original RSEM archive; it does not install a compiler. If the operating environment forbids a required installation or execution, skip the dependent analysis. The failure report names the missing dependency and the correction needed.

Create `compiler.json` for that actual prefix. For a conda-forge compiler prefix with the executable names shown below, replace `/opt/rsem-compiler` with its canonical path:

```json
{
  "cc": "/opt/rsem-compiler/bin/x86_64-conda-linux-gnu-gcc",
  "cxx": "/opt/rsem-compiler/bin/x86_64-conda-linux-gnu-g++",
  "make": "/opt/rsem-compiler/bin/make",
  "ar": "/opt/rsem-compiler/bin/x86_64-conda-linux-gnu-ar",
  "ranlib": "/opt/rsem-compiler/bin/x86_64-conda-linux-gnu-ranlib",
  "roots": ["/opt/rsem-compiler"],
  "includes": ["/opt/rsem-compiler/include"],
  "libraries": ["/opt/rsem-compiler/lib"]
}
```

The declaration must describe the real executable files and complete read-only compiler roots. Paths are explicit; the command does not silently select programs from your shell's `PATH`. Compiler and helper compatibility checks can refuse an unsupported environment.

From the repository root, set `JAR` to the built executable JAR and run setup into a NEW directory. Installation and download permissions are separate:

```bash
JAR="$PWD/build/cli/omicsmatic-0.3.7.jar"
mkdir -p /tmp/rsem-demo/source-cache
java -Xmx2g -jar "$JAR" rsem-tools --method setup \
  --version 1.3.3 --compiler-spec /absolute/path/compiler.json \
  --cache /tmp/rsem-demo/source-cache --out /tmp/rsem-demo/setup \
  --allow-install --allow-download
```

For offline setup, replace `--cache ... --allow-download` with `--source-archive /absolute/path/exact-original.tar.gz`. The archive must match the pinned source release. Supported source releases are 1.2.28, 1.3.0, 1.3.1, 1.3.2 and 1.3.3. Some original scripts report a different version label; source identity remains explicit.

**Stop if setup returns a nonzero exit code.** Read its `WARNINGS.md` and `analysis-status.json`. Exit 3 means a required dependency or platform is unavailable or forbidden; exit 2 means an input, integrity, execution or capacity failure. Correct the named cause and retry with NEW paths. A setup completion record establishes no expression result and is not portable permission to run an arbitrary copied runtime.

Prepare the checksummed synthetic reference and paired-end SAM input. The helper reads the successful setup completion record and writes a recipe with its exact source-build identity:

```bash
python3 examples/rsem-workflow/prepare_inputs.py \
  --setup-completion /tmp/rsem-demo/setup/COMPLETED.json \
  --out /tmp/rsem-demo/data
java -Xmx1g -jar "$JAR" rsem-tools --method fit \
  --recipe /tmp/rsem-demo/data/fit.json --out /tmp/rsem-demo/result
```

Use `--case single` for a separate single-end preparation, or `--posterior MEAN` / `--posterior INTERVAL` to request posterior output. Choose NEW preparation and result directories for every run.

A successful result requires the root `COMPLETED.json` and `analysis-status.json`, plus the completed fitting audit they identify. Read the root and fitting `WARNINGS.md`, all original stdout/stderr, `input-audit/INPUT_AUDIT.json` and `fit/RESULT_ADMITTED.json`. The fitted-result audit retains complete model, theta, parser, gene and isoform checks; the parent public workflow must also complete successfully.

For real data, supply a complete ordinary transcript reference prefix produced for the declared source release and the unmodified transcript-aligned SAM/BAM/CRAM input. Use the correct explicit read type, strand, auxiliary tag, fragment assumptions and other model settings. Allele-specific references are currently refused. CRAM additionally requires `cram_reference` pointing to the exact local uncompressed FASTA with its adjacent `.fai`; no URI or cache fallback is admitted. Do not repair a header, filter records or re-encode input to hide an audit failure.

Defaults are deliberately small complete-population capacities for this synthetic example. Export every capacity with:

```bash
java -jar "$JAR" rsem-tools --method limits > /tmp/rsem-demo/limits.json
```

Copy that object into the recipe's `limits` field, or supply named nested overrides under `inputs`, `audit` and `fit`. Increase the named complete-file, reference, query-name, hit, model, result and workspace capacities together with enough Java heap for the complete populations. Unknown fields and invalid values fail; the command never samples or truncates rows or parameters to fit a budget. A heap estimate refusal requires a reviewed larger `-Xmx` or smaller complete limits that still include all data.

The [provenance record](provenance.json) identifies the pinned synthetic archive, original control bundle and all 38 scientific source files. These examples and structural/numeric audits do not establish convergence, biological accuracy or whole-paper equivalence.
