# ChIPseeker development examples

These are synthetic controls: five transcripts, 13 exons and 70 peaks. They do
not reproduce an experiment, a real reference assembly or a paper. One query
has no matching source contig. Keep its disposition in the result.

Use the separate development JAR:

```text
build/literature-review/chipseeker-runtime/root-build-1/omicsmatic-0.3.7-chipseeker-root-development.jar
```

The canonical/released JAR was not replaced. The native mode needs Java 17 or
later. The original annotation mode also needs the exact declared R/package
environment. Runtime analysis installs nothing.

For native prepared nearest-feature mapping, from the repository root:

```sh
python examples/chipseeker/create_example.py \
  --mode NEAREST \
  --jar build/literature-review/chipseeker-runtime/root-build-1/omicsmatic-0.3.7-chipseeker-root-development.jar \
  --out build/chipseeker-native-example \
  --run
```

For original annotation using a completed private reference setup:

```sh
python examples/chipseeker/create_example.py \
  --jar build/literature-review/chipseeker-runtime/root-build-1/omicsmatic-0.3.7-chipseeker-root-development.jar \
  --runtime-setup build/literature-review/chipseeker-runtime/reference-setup-4 \
  --out build/chipseeker-annotation-example \
  --run
```

Those are shell continuation examples. In PowerShell put each command on one
line, or use PowerShell's continuation syntax. To supply another installed
reference environment, replace `--runtime-setup` with `--rscript R_SCRIPT` and
one or more `--r-library R_LIBRARY` arguments. The worker checks exact versions
and original function bodies/formal arguments. The helper's default `java` is
resolved on PATH; use `--java /absolute/path/to/java` to select it explicitly.

Omit `--run` to inspect the new inputs, request and `command.json` first. The
helper refuses an existing output directory. On Windows use an ASCII path for
the JAR and the outer request JSON; UTF-8 paths and identifiers inside the JSON
are supported and tested, including a Unicode output directory.

The annotation example explicitly requests ChIPseeker 1.48.1, transcript-level
annotation, a -2,000/+1,000 bp promoter window, and post-annotation selection of
literal `Promoter` labels. That selection defines no enhancer rule. Original
annotation runs on the entire peak population before selection. The prepared
native feature IDs are original transcript names in source extraction order;
they are not inferred gene symbols.

After a run, read `analysis-status.json`, `WARNINGS.md`, `report.json` and the
manifest. Require **`result_admitted: true`**. The native result has a complete
query ledger. The annotation result has:

- `results/genomic-annotation/query-ledger.tsv`: all 70 input queries, including
  source-dropped queries, exact feature order and literal contigs/strands.
- `original-annotations.tsv`, `original-detail.tsv` and
  `original-distribution.tsv`: the original R tables, with original R quoting.
- `original-output.json`, `original-cell-kinds.json` and original RDS outputs:
  retain raw values and distinguish missing values from literal text.
- `selected-query-ordinals.tsv`: the caller's explicit label-prefix selection.

**Do not reinterpret original `geneChr` or `geneStrand` numeric codes as literal
chromosome names or `+`/`-` strands.** The separate feature ledger provides the
literal prepared ranges. Source-dropped, missing and boundary-dummy states are
not biological absence or valid gene assignments.

**SKIP3:** a required R runtime, exact package/source profile, library or UTF-8
locale is unavailable. Supply the named dependency in permitted private
locations. If installation is forbidden, retain the skip and skip that dependent
analysis. Native `NEAREST` requires no R environment.

**FAIL2:** no result is admitted from the annotation batch. Preserve its worker
directory and read the exact per-case `source-status.json`, original warnings
and stdout/stderr. A failed member rejects the entire batch, including another
member that returned an R object. Correct the named cause and use new output
paths. Do not change coordinates, directions, identifiers or software versions
to force agreement. There is no portable `VERIFY` command for this interface.

The synthetic TxDb constructor warned that its chromosome genome-version
information is unavailable. Its explicit synthetic metadata does not establish
a real assembly. See
[the annotation development reference](../../docs/literature/CHIPSEEKER_ANNOTATION_DEVELOPMENT.md)
for exact profiles, controls, original errors and remaining publication work.
