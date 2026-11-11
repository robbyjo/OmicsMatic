# HTSeq original file-counting development

This development adapter runs the original HTSeq counter on one explicit SAM/BAM and plain GTF, retaining raw counts, warnings, inputs and process records. It supports exact source profiles 0.5.3, 0.6.0, 0.11.1, 0.13.5 and 2.1.2. The [tools guide](HTSEQ_TOOLS.md) describes the final development root command and current scope. The canonical/released JAR is preserved. The class entry point below also remains available for direct validation.

The separate [native prepared-read counter](HTSEQ_READ_COUNT_DEVELOPMENT.md) needs Java only. It implements current deterministic counting after original parsing/pairing preparation; it does not independently read these files. The file adapter executes the upstream program and preserves its edition behavior.

**Missing or forbidden runtime: SKIP3, analysis skipped.** COUNT needs Linux Java 17+, an exact private Python/HTSeq/NumPy/pysam profile, and the installed Linux `setsid`/`prlimit` helpers. COUNT performs no installation or download. When installation is permitted, provision the exact profile and retry into **new output paths**. If installation is forbidden, retain the skip and skip the dependent analysis. Windows Java can run VERIFY; run original counting under a permitted Linux/WSL Java environment.

**Invalid input, changed source, failed process, capacity or cancellation: FAIL2, no result admitted.** Read `WARNINGS.md`, `analysis-status.json`, `FAILURE.json` and the retained process logs. Correct the stated cause and retry into new paths. `INCOMPLETE.json` remains after an unsuccessful attempt. Files under `partial/` are retained diagnostics. They are not successful count results. Never relax filters, rewrite identifiers or switch versions silently.

Use this standalone development entry point:

```sh
java -cp /absolute/path/omicsmatic-0.3.7-htseq-file-count-development.jar \
  org.jlinalg.cli.HtseqToolsCli --source-report

java -cp /absolute/path/omicsmatic-0.3.7-htseq-file-count-development.jar \
  org.jlinalg.cli.HtseqToolsCli --request /absolute/path/count-request.json
```

The current candidate is built by `tools/literature/build_htseq_file_count_development.py` into `build/literature-review/htseq-runtime/file-count-development-3/`. That builder compiles only the five new adapter classes and overlays the immutable native-count parent, preserving every parent entry. It does not package unrelated worktree changes. The normal canonical executable has no HTSeq route yet.

Every scientific option is explicit in COUNT. Paths resolve against the request JSON, and the output parent must already exist. Keep request filenames ASCII when invoking legacy Windows launchers; UTF-8 data paths and identities belong inside the JSON. This example describes the schema; replace its absolute runtime path and declared reference labels before use:

```json
{
  "schema_version": 1,
  "operation": "COUNT",
  "version": "2.1.2",
  "runtime_home": "/absolute/private/htseq-2.1.2-home",
  "alignment": "reads.sam",
  "annotation": "features.gtf",
  "format": "sam",
  "order": "name",
  "stranded": "yes",
  "overlap": "union",
  "nonunique": "none",
  "secondary": "ignore",
  "supplementary": "ignore",
  "minimum_mapq": 21,
  "feature_type": "exon",
  "id_attribute": "gene_id",
  "genome_label": "explicit-user-genome-label",
  "annotation_label": "explicit-user-annotation-label",
  "counting_unit": "original-auto",
  "out": "new-count-run"
}
```

`original-auto` means the original parser determines single-read or paired-read units. It does not establish a paper's unstated read/fragment choice. Genome/annotation labels are user declarations; the adapter does not establish their biological reference identity. Plain SAM/GTF and BAM are supported; compressed SAM/GTF and CRAM are outside the admitted format contract. No proper-pair prefilter, cell/barcode/UMI logic, custom ATAC script, normalization, FPKM or differential model is supplied.

| Exact HTSeq | Exact private Python | NumPy | pysam | Admitted options |
| --- | --- | --- | --- | --- |
| 0.5.3 | 2.7.18 | 1.16.6 | 0.15.4 | Name-ordered SAM; nonunique NONE; secondary and supplementary SCORE |
| 0.6.0 | 2.7.18 | 1.16.6 | 0.15.4 | SAM/BAM, name/position; NONE; both flag policies SCORE |
| 0.11.1 | 3.9.20 | 1.23.5 | 0.19.1 | SAM/BAM; NONE/ALL; IGNORE/SCORE flags |
| 0.13.5 | 3.9.20 | 1.23.5 | 0.19.1 | SAM/BAM; NONE/ALL/FRACTION; IGNORE/SCORE flags |
| 2.1.2 | 3.12.3 | 2.2.6 | 0.23.3 | SAM/BAM; NONE/ALL/FRACTION; IGNORE/SCORE flags |

`--source-report` lists the exact original scientific-source hashes as well as these dependencies. A major/minor report such as 0.13 is insufficient to select 0.13.5. RANDOM is not admitted. For 0.5.3/0.6.0, asking for IGNORE fails explicitly because the original commands score those flags. Historical source input errors remain errors: the tested old SAM readers reject header-only files and `=`/`X` CIGARs. All options, including MAPQ, are passed explicitly, so a historical default is never inferred.

For a permitted current-profile installation, first ensure `python3.12 --version` reports exactly 3.12.3, choose a new private home, and use the original packages:

```sh
python3.12 -m venv /new/private/htseq-2.1.2-home
/new/private/htseq-2.1.2-home/bin/python -m pip install \
  'numpy==2.2.6' 'pysam==0.23.3' 'HTSeq==2.1.2'
```

Installation still depends on operating-environment permission, architecture and available wheels/build requirements. Inspect installation logs; do not replace a missing exact dependency with a newer release. An unavailable or forbidden setup means skip COUNT. Historical research setup helpers are retained in `tools/literature/setup_htseq_historical_reference.py` and its GCC-only derivation; they use the explicitly configured research WSL distribution and new workspace paths, not a universal end-user installer. Preserve compiler/download failures and their recovery records.

Successful runs contain `counts.tsv` (`feature_id`, `count`) and `summary.tsv` (`category`, `count`), together with exact original stdout/stderr, prepared inputs, the fixed bridge, arguments, resolved paths, actual import profile, runtime snapshot, capacity/process records and a complete file manifest. Count strings remain literal, including fractions and their original rounding. The five source summary categories may overlap under ALL/FRACTION; they need not sum to gene counts or read/pair units. Minimum MAPQ is inclusive, so **21 implements strict MAPQ>20**. FRACTION divides by assigned-feature count rather than NH.

All installed scientific HTSeq Python modules must match their release originals. Fresh package snapshots include bytecode, compiled/generated files and adjacent package libraries, plus the resolved interpreter. Before/after process checks detect changes to these guarded files, original inputs and prepared copies. Base standard library, loader/system DLLs, signatures and whole-host/execution authenticity are outside this guard. Exact dependency versions and original sources do not establish a whole historical environment or paper-exact/biological equivalence.

Optional logical capacities are `max_input_bytes` per input (default 1 GiB, maximum 100 GiB), `max_output_bytes` (default 64 MiB, maximum 256 MiB), `max_count_rows` including five summary rows (default/maximum 2,000,000), `timeout_seconds` (default 3,600, maximum 604,800), and `memory_bytes` per-process address-space capacity (default 2 GiB, maximum 64 GiB). Input copies enforce the complete source byte count while copying. The shared supervisor adds CPU/file/nofile/wall/process/thread/workspace checks and sampled aggregate RSS monitoring. Sampled RSS is not a hard aggregate RSS bound; logical Java table/tree bounds are not a Java heap guarantee. Tables have at most 65,536 characters per line. Package inventories are bounded to 20,000 files/1 GiB. The BAM magic check examines at most the first 128 KiB of compressed bytes; it is a format gate, not independent full BAM validation. Inputs exceeding declared capacities fail without an admitted result.

VERIFY is portable and requires Java only:

```json
{
  "schema_version": 1,
  "operation": "VERIFY",
  "run": "new-count-run",
  "out": "new-verification-run"
}
```

VERIFY checks the complete committed bundle, exact original-source profile, recorded explicit arguments/commands/capacities, actual probe record, zero-exit/owned-process cleanup records, original count-table schema and exact derived count/summary tables. It detects missing/extra files and incomplete/failed attempts. It does not independently parse/pair SAM/BAM-GTF, execute HTSeq again, prove execution authenticity or establish biological accuracy. Hashes establish integrity and consistency, not who generated a bundle. Zero counts are not evidence of biological absence.

The custom binary peak-by-cell HTSeq role in P099 remains separate from this original gene counter. P121's proper-pair filter and unresolved count unit, P113's EU-region preparation and unstated EU edition, and P195's unresolved patch release remain explicit paper boundaries. Cell Ranger is excluded. No catalog promotion, release, push, deployment or whole-goal completion is claimed while adapter admission is pending.
