# Salmon bulk example (source build)

These examples use the source implementation of `salmon-tools`. The existing
older released/canonical JAR does not contain this command. Use a source-built
JAR that includes it and read the development evidence guide for exact validation
records and limitations. Each actual analysis still requires its own complete
runtime, input, process and output admission.

Use Linux x86_64 Java17+ and permitted Python3.12+ for runtime setup. On Windows,
run execution inside a Linux/WSL shell. Setup export and complete captured
verification also work with Windows Java. A copied Linux report is evidence;
execution still requires a fresh current-host check.

The paired and single recipes use the exported synthetic health inputs. They
demonstrate software contracts, not a biological study or historical paper
reproduction. Their library codes, model choices and small capacities are chosen
for this example. Do not infer these settings for a paper that did not report
them. Production data requires a new explicit recipe and suitable capacities.

From a source-built JAR containing `SalmonToolsCli`, in the repository's Linux
shell, set its absolute path and export the standalone setup kit:

```sh
JAR=/absolute/path/to/source-built-omicsmatic.jar
java -jar "$JAR" salmon-tools --export-runtime-setup /var/tmp/omicsmatic-salmon-demo-kit
python3 -I -B /var/tmp/omicsmatic-salmon-demo-kit/install_salmon.py --profile linux-x86_64-salmon-1.9.0 --home /var/tmp/omicsmatic-salmon-demo-home --report /var/tmp/omicsmatic-salmon-demo-setup --cache /var/tmp/omicsmatic-salmon-demo-cache --allow-install --allow-download
```

Installation and downloading have independent permission flags. If either is
forbidden but required, the installer must report SKIP3 and dependent analysis
must remain skipped. For complete offline archives, use `--archive-dir` and omit
`--allow-download`. Read `RUN-SETUP.txt`, `LICENSE-Salmon.txt`, the setup report's
`WARNINGS.md` and all original logs. Require installer exit0 and
`/var/tmp/omicsmatic-salmon-demo-setup/COMPLETED.json` before proceeding.

All example work directories must be new. Preserve failed attempts and choose
new names after corrections. Build and verify the exact same-edition index:

```sh
java -Xmx512m -jar "$JAR" salmon-tools --index-recipe examples/salmon-bulk/index.json --home /var/tmp/omicsmatic-salmon-demo-home --setup-report /var/tmp/omicsmatic-salmon-demo-setup --out /var/tmp/omicsmatic-salmon-demo-index
java -Xmx512m -jar "$JAR" salmon-tools --verify-index /var/tmp/omicsmatic-salmon-demo-index
```

An index completion is not an expression result. Inspect `INDEX-AUDIT.json` and
every row of `REFERENCE-LEDGER.jsonl`, including raw records removed by original
preprocessing. Original complete length, normalized index length and fitted
effective length differ. GENCODE splitting is explicit and transcript versions
are preserved. Decoy names bind the raw FASTA token before that splitting.

Run a paired-end VB fit with sequence/GC/position bias and 100 bootstraps, or the
separate single-end EM example with its explicit fragment-length prior:

```sh
java -Xmx512m -jar "$JAR" salmon-tools --recipe examples/salmon-bulk/paired.json --home /var/tmp/omicsmatic-salmon-demo-home --setup-report /var/tmp/omicsmatic-salmon-demo-setup --out /var/tmp/omicsmatic-salmon-demo-paired
java -Xmx512m -jar "$JAR" salmon-tools --verify /var/tmp/omicsmatic-salmon-demo-paired
java -Xmx512m -jar "$JAR" salmon-tools --recipe examples/salmon-bulk/single.json --home /var/tmp/omicsmatic-salmon-demo-home --setup-report /var/tmp/omicsmatic-salmon-demo-setup --out /var/tmp/omicsmatic-salmon-demo-single
java -Xmx512m -jar "$JAR" salmon-tools --verify /var/tmp/omicsmatic-salmon-demo-single
```

For your own inputs, copy the recipe and replace every absolute data/work path,
exact edition and scientific choice. `library_type` must be an original code,
such as `ISR`, `IU`, `SR` or explicitly selected automatic `A`. A phrase such as
“SAF Pattern” is not guessed into a code. Single-end fragment mean/SD are not read
lengths. Use `PLAIN_UTF8` or `GZIP_UTF8` explicitly for reference FASTA. Four-line
FASTQ, including all gzip members, is validated and captured unchanged; the
adapter does not convert Phred encodings or silently normalize bases. Pairing
identity rules and any ordinal-only uncertainty remain explicit.

For transcriptome alignment mode, edit `alignment.json` to supply your complete
query-grouped SAM or BAM and exact transcript FASTA. Set `format` to `SAM` or
`BAM` and declare `SINGLE` or `PAIRED` layout. The full dictionary must match
transcript names, versions, order and lengths. Paired hits require complete
sequence fields and adjacent first-then-second records. Noncontiguous query
groups and genomic spliced CIGARs are rejected. Genome-aligned BAM is not
automatically projected, reordered or substituted for transcriptome alignment.

The default `REQUIRE_DECLARED_NONDECOYS` policy skips a mapping fit before
execution if the original short-reference/decoy classifier would omit a declared
retained transcript. Inspect its exact names in the index ledger. Revise the
reference/index explicitly when appropriate. A separately chosen
`ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER` policy keeps the source output population
and false declared-retained coverage visible. It never relabels omitted records
or fills them with zeros. The alignment mode has no such mapping-index policy.

Before downstream use, require exit0, `COMPLETED.json` and successful complete
verification. Inspect `metadata/input-audit.json`, `OUTPUT-AUDIT.json`,
`quant/quant.sf`, all requested files under `quant/aux_info/bootstrap/`, original
`quant-process/process.stderr`/`process.stdout` and `quant/logs/`. Input/output
audit documents state their own component checks; the final completion record
governs admission of the selected complete population. Preserve model,
burn-in and effective-length fallback warnings. The small setup example is below
original burn-in thresholds; its health does not calibrate a user model. Original
small-reference positional-bias crashes remain unresolved. Never silently turn
off requested bias to make a run pass.

**SKIP3 — analysis skipped:** a required exact runtime, permitted helper,
installation, download, execution or admitted capability is unavailable. Complete
only permitted setup or leave the dependent analysis skipped. **FAIL2 — no result
admitted:** invalid input, changed bytes, original failure, numerical inconsistency,
capacity, cancellation or finalization failure. Preserve every partial output,
read the named error and rerun into new paths after correction. No passing table
prefix is a result. Metadata memory estimates are not JVM heap guarantees.

TPM is relative abundance; estimated fragment counts can be fractional. Neither
is raw integer read counts. Gene aggregation, tximport, differential expression,
SUPPA2, MAJIQTL and missing paper settings require their own separate contracts.
Portable verification checks captured integrity and recorded contracts; hashes
are not execution signatures, current-host admission or biological validation.
See [the development evidence guide](../../docs/literature/SALMON_INDEX_DEVELOPMENT.md)
and [setup evidence guide](../../docs/literature/SALMON_SETUP_EVIDENCE.md).
