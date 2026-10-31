# Original named-genome region annotation

These fixtures exercise `homer-tools named-annotate` with explicit HOMER 5.1 and
v7.0 hg38/mm10 resources. They are software examples derived from local pinned
annotations. They contain no paper measurements or inferred biological findings.

The mouse example uses HOMER7 coordinates and omits an optional statistics file.
The human example uses BED6 and requests original category statistics. Both retain
duplicate Unicode names by ordinal, an explicitly assigned dot strand, repeat/TE
and gene feature categories, raw `-HOMER` TSS identities, both query strands, and
reference endpoints. Input regions always lie inside the pinned reference.

Use Linux x86_64 Python and Java; on Windows run them inside WSL. Run commands
from the repository root. Each output, report and private installation path must
be new. Keep failed paths for diagnosis.

```sh
python3 tools/literature/prepare_homer_named_resources.py \
  --resource-root /private/homer-named-resources \
  --cache /private/homer-named-cache \
  --archive-dir /private/offline-homer-archives \
  --report /private/homer-named-resource-report --allow-install
```

Supply the four exact archives named in `tools/literature/homer-named-lock.json`.
If downloads are permitted, omit `--archive-dir` and add `--allow-download`.
Downloads use official HTTP URLs and locked sizes/SHA256. These pins are not
independently authenticated signatures. The preparer extracts only the twelve
files needed for this annotation operation. A pre-existing resource root is
verified without modifying it.

Create the independent named home from an already admitted 5.1 supplied-GTF
annotation home. See `tools/literature/install_homer_annotation.py --help` if the
base home still needs installation.

```sh
python3 tools/literature/install_homer_named.py \
  --annotation-home /private/homer-annotation-5.1 \
  --home /private/homer-named-5.1-v7 \
  --resource-root /private/homer-named-resources \
  --report /private/homer-named-setup-report --allow-install

python3 examples/homer-named-annotation/prepare_inputs.py \
  --resource-root /private/homer-named-resources --out /private/named-inputs
```

Allow several minutes and at least 6 GiB of available upstream memory. The
default upstream RSS allowance is 8 GiB; Java heap is separate. The installer
runs the original full annotation against each genome. A successful core,
supplied-GTF or genomic-motif admission does not supply this named admission.

```sh
java -Xmx1536m -jar build/cli/jlinalg-0.3.7.jar homer-tools named-annotate \
  --home /private/homer-named-5.1-v7 \
  --profile linux-x86_64-named-annotation-5.1-v7.0 --out /private/mm10-result \
  --genome mm10 --reference-build mm10 --annotation-release v7.0 \
  --peaks /private/named-inputs/mm10.pos --peak-format homer \
  --peak-unknown-strand original-plus --duplicate-ids retain-ordinal \
  --resource-bounds retain-original --tss-resource-anchors retain-original \
  --feature-ties retain-original --tss-ties retain-original \
  --missing-tss-contig retain-NA --annotation-stats omit
```

For the human fixture, use `--genome hg38 --reference-build hg38`,
`--peaks /private/named-inputs/hg38.bed --peak-format bed6`,
`--annotation-stats original-query-contigs`, and a new output directory.
For your own analyses, choose each policy deliberately; these fixtures use
explicit retention to exercise source irregularities and ambiguity.

Run the reusable verifier to execute both examples and compare every original
19-column result row with two fresh direct original runs. It also compares the
complete optional human statistics file. This takes four full annotation runs.

```sh
python3 tools/literature/verify_homer_named_workflow.py \
  --home /private/homer-named-5.1-v7 --jar build/cli/jlinalg-0.3.7.jar \
  --out /private/named-verification
```

**Review `WARNINGS.md` before using results.** Missing, forbidden, changed or
unsupported dependencies skip the affected analysis with exit 3 and a named
cause plus **Required action**. Other failures use exit 2 and retain provisional
files in `partial/`. Successful analysis uses exit 0 with review warnings.
Installation failures retain their original setup diagnostics. An existing
output is never overwritten. Analysis never downloads or installs.

Inspect `peaks.tsv`, `resource-tss.tsv`, `native-feature-candidates.tsv`,
`native-tss-candidates.tsv`, `native-category-populations.tsv`, `results.tsv`,
`session.json`, and `original.stdout`/`original.stderr`. Raw source identities,
displayed identities, ties and unchanged original metadata remain separate.
P053 specifies hg38/mm10 but does not specify its HOMER/database edition; this
explicit edition does not establish exact paper reproduction. Exact population
agreement does not certify P-value calibration or gene metadata namespaces.

This entry implements default named region annotation with `-size given`. It
does not admit sequence/GC, motif, peakcalling, coverage, RNA or ontology
operations. The broader HOMER paper roles remain separately tracked.
