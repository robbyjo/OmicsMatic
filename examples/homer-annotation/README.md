# Original HOMER supplied-GTF peak annotation

Synthetic inputs demonstrate the typed development command, not biological results.
The current executable and SHA256 are named in `homer-annotation-validation.json`.
Run from the repository root with `JAR` pointing to that executable,
`HOMER_ANNOTATION_HOME` to an admitted annotation home, and `PROFILE` to
`linux-x86_64-annotation-5.1`, `linux-x86_64-annotation-4.11` or
`linux-x86_64-annotation-4.11.1`. Core-only homes do not admit Perl annotation.

All output paths must be new. Dependencies unavailable or forbidden: **SKIP3**.
Other failures: **exit2**, retained diagnostics and quarantined provisional files.
Read every `WARNINGS.md` and follow its Required action before using results.
See [setup and interpretation](../../docs/literature/omics-2023-2026/HOMER_ANNOTATION_WORKFLOWS.md).

The fixtures contain four exon rows, two transcripts, versioned synthetic gene
IDs, both strands, and one peak on chrX without GTF annotation. chrX remains NA
under explicit retain policy. The reference dictionary is synthetic.
The version-trimmed metadata gene IDs are GA and GB; full GA.1 and GB.2 remain
in the input and native transcript ledger.

## BED6, given intervals and no statistics file

```sh
java -Xmx1g -jar "$JAR" homer-tools annotate \
  --home "$HOMER_ANNOTATION_HOME" --profile "$PROFILE" --out OUT_BED \
  --peaks examples/homer-annotation/peaks.bed --peak-format bed6 \
  --gtf examples/homer-annotation/genes.gtf \
  --reference-lengths examples/homer-annotation/lengths.tsv \
  --reference-build synthetic-v1 --annotation-release synthetic-exons-v1 \
  --gtf-id transcript --gtf-unknown-strand error --peak-unknown-strand error \
  --duplicate-ids error --tss-ties retain-original --missing-gtf-contig retain \
  --annotation-stats omit
```

## HOMER positions and retained original diagnostic statistics

```sh
java -Xmx1g -jar "$JAR" homer-tools annotate \
  --home "$HOMER_ANNOTATION_HOME" --profile "$PROFILE" --out OUT_POS \
  --peaks examples/homer-annotation/peaks.pos --peak-format homer \
  --gtf examples/homer-annotation/genes.gtf \
  --reference-lengths examples/homer-annotation/lengths.tsv \
  --reference-build synthetic-v1 --annotation-release synthetic-exons-v1 \
  --gtf-id transcript --gtf-unknown-strand error --peak-unknown-strand error \
  --duplicate-ids error --tss-ties retain-original --missing-gtf-contig retain \
  --annotation-stats original-gtf-extent
```

The original statistics are not certified genome-wide enrichment results.
The supplied length dictionary audits coordinates; the original does not read
it to define its statistical universe. `omit` omits the statistics file, but
original diagnostics may still appear in stderr.

## Out-of-order GTF exons

```sh
java -Xmx1g -jar "$JAR" homer-tools annotate \
  --home "$HOMER_ANNOTATION_HOME" --profile "$PROFILE" --out OUT_SHUFFLED \
  --peaks examples/homer-annotation/peaks.bed --peak-format bed6 \
  --gtf examples/homer-annotation/shuffled-genes.gtf \
  --reference-lengths examples/homer-annotation/lengths.tsv \
  --reference-build synthetic-v1 --annotation-release synthetic-exons-v1 \
  --gtf-id transcript --gtf-unknown-strand error --peak-unknown-strand error \
  --duplicate-ids error --tss-ties retain-original --missing-gtf-contig retain \
  --annotation-stats omit
```

The original sorts selected exon rows before grouping transcripts. Native Java
independently aggregates compatible spans; the original row order stays in the
immutable input copy. Noncontiguous source-block counts are provenance, not
extra transcripts.

## Explicit duplicate retention and original dot-to-plus convention

```sh
java -Xmx1g -jar "$JAR" homer-tools annotate \
  --home "$HOMER_ANNOTATION_HOME" --profile "$PROFILE" --out OUT_DUPLICATES \
  --peaks examples/homer-annotation/duplicate-dot-peaks.bed --peak-format bed6 \
  --gtf examples/homer-annotation/dot-genes.gtf \
  --reference-lengths examples/homer-annotation/lengths.tsv \
  --reference-build synthetic-v1 --annotation-release synthetic-exons-v1 \
  --gtf-id transcript --gtf-unknown-strand original-plus \
  --peak-unknown-strand original-plus --duplicate-ids retain-ordinal \
  --tss-ties retain-original --missing-gtf-contig retain --annotation-stats omit
```

Repeated names remain separate ordinal/alias records; dot strands become plus
only with these explicit choices. Nearest-TSS distance uses transcript
orientation and an integer midpoint, independently of peak strand.

Inspect `results.tsv`, `peaks.tsv`, `gtf-transcripts.tsv`,
`native-tss-candidates.tsv` and original stdout/stderr. Feature labels and gene
metadata remain original-engine outputs. Native auditing does not reconstruct
feature priorities or validate gene-symbol databases. Nearest-TSS proximity is
not uniquely supported causal-gene evidence. Cell Ranger remains excluded.
