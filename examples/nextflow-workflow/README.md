# Declared Nextflow workflow example

This example counts sequence length and G/C bases in three tiny synthetic FASTA
files. It demonstrates a workflow engine and its evidence contract. It does not
reproduce a paper or establish biological accuracy.

Use Linux x86_64 Java 17 or newer, including Java inside WSL on Windows. The
original engine uses its separately admitted, pinned Java 11 runtime. The old
released 0.3.7 executable does not contain this development command.

From the repository directory, export a NEW example directory. Choose an exact
engine explicitly:

```bash
python3 examples/nextflow-workflow/create_example.py \
  --engine-profile linux-x86_64-nextflow-24.04.4 \
  --out /absolute/new-example
```

Set these paths to your current-source executable and the successful matching
runtime setup. Use NEW session paths outside the example source directory:

```bash
JAR=/absolute/omicsmatic-current-development.jar
HOME_NXF=/absolute/admitted-nextflow-24.04.4
SETUP_REPORT=/absolute/completed-nextflow-setup

java -jar "$JAR" nextflow-tools \
  --recipe /absolute/new-example/recipe.json \
  --home "$HOME_NXF" --setup-report "$SETUP_REPORT" \
  --out /absolute/new-session

java -jar "$JAR" nextflow-tools --verify /absolute/new-session
cat /absolute/new-session/workspace/published/counts.tsv

java -jar "$JAR" nextflow-tools --resume /absolute/new-session \
  --home "$HOME_NXF" --setup-report "$SETUP_REPORT"
java -jar "$JAR" nextflow-tools --verify /absolute/new-session
```

The expected output is `a: 8 bases, 4 G/C`, `b: 6 bases, 0 G/C`, and
`c: 10 bases, 8 G/C`. All four tasks should be cached on an unchanged resume.

Each completed `attempts/NNNNNN` directory is a standalone evidence bundle.
`outputs.json` maps every logical published file to its immutable copy at
`objects/SHA256`. Those copies are authoritative for that attempt. Working
publication/cache files can change during later runs. A copied completed attempt
can be verified on Windows Java without a producer runtime; verification does
not execute the original workflow.

**SKIP3:** skip dependent analysis when an exact runtime or declared executable
is missing or forbidden. **FAIL2:** no result is admitted for invalid input,
changed evidence, process/capacity failure or cancellation. Inspect the latest
attempt's `WARNINGS.md`, `FAILURE.json` and raw process/engine logs. Preserve all
attempts and use new session paths when the immutable request must change.

Setup is separate. Export its kit with
`java -jar "$JAR" nextflow-tools --export-runtime-setup /absolute/new-kit`.
The exported installer documents explicit `--allow-install` and
`--allow-download` flags, or complete offline archives via `--archive-dir`.
Installation must be permitted by the operating environment. A setup health
check alone admits no user analysis.

Read [the development scope and validation notes](../../docs/literature/NEXTFLOW_DEVELOPMENT.md).
The guard observes owned local processes; remote scheduler and service lifecycles
require separate supervision. Hashes establish integrity, not authenticity.
