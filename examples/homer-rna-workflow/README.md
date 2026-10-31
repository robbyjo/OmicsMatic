# Original RefSeq exon counts and RPKM

This development example uses `homer-tools rna`. It creates two tiny synthetic
SAM populations for each selected genome. Each command still audits the **whole
original RefSeq resource**, including unexpressed transcripts. These are technical
checks, not paper data or a differential-expression analysis.

**Read `WARNINGS.md` in every setup and analysis report.** Exit 3 means the
dependent operation was skipped, with a named cause and required action. Exit 2
means failure; analysis files are retained under `partial/`. Use new output paths
after correcting the cause. An incomplete directory is not a completed result.

Use Linux x86_64 Python and Java, inside WSL on Windows. The original runtime
needs Perl, POSIX utilities and compatible host libraries. Compilation needs a
C++11 compiler, make, headers, pthread/math and static C++/GCC libraries. An
administrator can supply permitted private tools. Analysis never installs software.

From the repository root, prepare the eight pinned reference files. The four
original v7.0 archives total approximately 3 GB; extraction retains only the
selected RNA, chromosome-length and accession-description files.

```sh
python3 tools/literature/prepare_homer_rna_resources.py \
  --resource-root build/private-rna-resources \
  --cache build/private-rna-reference-archives \
  --report build/private-rna-resource-report \
  --allow-install --allow-download

python3 tools/literature/install_homer_rna.py \
  --home build/private-homer-rna \
  --resource-root build/private-rna-resources \
  --cache build/private-rna-software-archives \
  --report build/private-rna-install-report \
  --allow-install --allow-download \
  --compiler /usr/bin/g++ --make /usr/bin/make
```

For offline use, replace `--allow-download` with `--archive-dir /ABSOLUTE/ARCHIVES`.
Reference and software caches, homes and reports must be separate and nonnested.
The exact archive names and hashes are in `tools/literature/homer-rna-lock.json`.
Existing resources/homes can be verified without installation permission. If a
dependency is unavailable or installation is forbidden, skip the analysis until
the named dependency is supplied. Corrupt archives and partial homes are retained.

Setup builds the original tag helper and runs fourteen original health operations
over hg38 and mm10. This can take many minutes. It requires at least 6 GiB available
memory and 4 GiB allowed upstream RSS; the default allowance is 8 GiB. Java heap
is separate. No genome sequence, software binary or accession database is bundled
with the project. HTTP archive hashes identify content, without an authenticated
upstream signature.

Generate inputs using the admitted private home. For a fresh checkout, build a
JAR containing the RNA command and confirm its help before running analysis:

```sh
./gradlew executableJar --no-daemon --no-parallel
java -jar build/cli/jlinalg-0.3.7.jar homer-tools rna --help
```

This workspace's scoped development candidate is
`build/literature-review/homer-rna-candidate-1/jlinalg-0.3.7.jar`, SHA256
`fe05465a847f1f5d8fad2e94135236d1cfa2c167d32e9e321197e6f034cc5da1`.
Use that path in place of `build/cli/jlinalg-0.3.7.jar` to select these exact
tested bytes. The shared ROOT checkout has not received a full build gate.

Run the two explicit recipes:

```sh
python3 examples/homer-rna-workflow/prepare_inputs.py \
  --out build/rna-example-inputs --runtime-home build/private-homer-rna

java -Xmx3g -jar build/cli/jlinalg-0.3.7.jar homer-tools rna \
  --options-file build/rna-example-inputs/hg38-options.json

java -Xmx3g -jar build/cli/jlinalg-0.3.7.jar homer-tools rna \
  --options-file build/rna-example-inputs/mm10-options.json
```

Use an ASCII Linux output parent without spaces or shell metacharacters. Original
Unicode source paths and replicate IDs can be supplied through the UTF8 options
JSON; source bytes and identities remain in immutable inputs and ledgers.

The recipes explicitly choose unique/MAPQ30, both mates and both relative strands.
Those are fixture choices: P040 does not report its complete filtering, strand,
paired assignment, assembly or RefSeq edition. Review these choices for your data.
Paired SAM rows retain half weight. Counts use original inclusive 5-prime anchors,
with no fragment-overlap integration, tag adjustment or tag cap. Source boundary
ownership, empty parts and end-minus-start effective lengths remain explicit.
The fixed 150-bp diagnostic library estimate is ignored by this fixed counter.

`raw.stdout` and `rpkm.stdout` retain complete original tables. The command checks
every tag key, expanded exon part, raw count, family length/copy count and printed
raw/RPKM value. Read `part-counts.tsv`, `boundary-families.tsv`, `contributors.tsv`,
the native matrices, original stderr and `session.json`. RPKM uses the whole
original tagInfo mass; headers can use the upstream word FPKM. Fractions in raw
counts are preserved. Check downstream DESeq2 requirements explicitly.

See [the reference guide](../../docs/literature/omics-2023-2026/HOMER_RNA_WORKFLOWS.md)
for capacities, input policies and interpretation boundaries. No alignment,
deduplication, differential expression, GO or Cell Ranger analysis is performed.

To run both packaged recipes and compare complete fresh original tag/QC and
raw/RPKM tables in a new report directory:

```sh
python3 tools/literature/verify_homer_rna_workflow.py \
  --home build/private-homer-rna --resource-root build/private-rna-resources \
  --jar build/cli/jlinalg-0.3.7.jar --out build/rna-fresh-reference-report
```

This repeats whole-reference work and can take many minutes. The verifier skips
an older JAR without the RNA capability with a named cause and required action.
Existing reports and unsafe output paths are refused before analysis. Read its
`WARNINGS.md` and each analysis report; verification does not certify paper or
biological accuracy.
