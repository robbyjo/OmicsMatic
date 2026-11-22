# Coordinate conversion in Java

`coordinate-lift` maps nonempty genomic intervals through a supplied directional
chain using the project's existing HTSJDK basic LiftOver engine. The reusable
`ChainLiftover` API adds explicit reference, structure, bounds and unmapped
contracts. It requires no external executable or automatic installation.

This is an unreleased basic interval subset. Consult the current full-build and
packaged validation reports before treating the working source as a verified
artifact. Existing Pan-UKB SNP-instrument projection already uses the same Java
engine for its narrower hg38-to-hg19 task; the general command does not replace
its allele-specific contract.

**Every input interval remains in the result table.** A mapped interval has one
qualifying chain. Ambiguous or insufficient mappings, target-span rejections and
failed reciprocal checks remain explicit. Choose `--unmapped retain` or `error`;
neither policy silently drops unsuccessful inputs.

## Supply the exact local chain and references

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar coordinate-lift `
  --input data/peaks.tsv --chain data/hg19ToHg38.over.chain.gz `
  --source-sizes data/hg19.chrom.sizes --target-sizes data/hg38.chrom.sizes `
  --source-build UCSC_hg19 --target-build UCSC_hg38 --min-match 0.95 `
  --unmapped retain --out results/peaks_hg38
```

Input is canonical tab-delimited text with the exact column order:

```tsv
chrom	start	end	id	strand
chr1	100000	100500	peak1	.
```

The final `strand` column is optional; omitted values are unknown (`.`). Bounds
are zero-based, half-open and nonempty. IDs must be unique nonempty tokens.
Known strands are `+` or `-`. Names match exactly; no chromosome aliases, rsID
lookup or assembly inference is performed. There is a one-million-row limit.
Project BED/narrowPeak/VCF data into this explicit interval contract before use;
summits, alleles, scores, transcript blocks and other metadata are not lifted.

Each reference file is headerless `chrom<TAB>length`. Alternatively declare
`--source-sizes-format fai` and/or `--target-sizes-format fai` for a five-column
FAI. FAI supplies lengths only; this command does not verify original FASTA
byte geometry or sequence identity.

Every chain-header contig must occur with its exact length in the corresponding
full dictionary, including unplaced/alternate contigs present in that chain.
Additional dictionary contigs may have no chain. Primary-only dictionaries can
therefore fail against a full chain; supply the correct full dictionaries or a
deliberately prepared compatible chain. Do not silently rename/drop contigs or
change sequence lengths to make validation pass.

In UCSC chain terminology, **t is the source and q is the destination** for
liftOver. A filename/build label does not establish direction. Chain blocks,
gaps, endpoint sums, duplicate IDs, strands and consistent sequence sizes are
checked before mapping. Plain or gzip bytes are detected directly. Limits are
one GiB encoded input, 512 MiB decoded character work, one million chain headers
and ten million blocks. The engine holds chains in memory; budget JVM heap for
large maps. The underlying basic engine requires signed-32-bit sequence lengths,
coordinates and chain IDs; other project commands' 64-bit limits do not apply.

Build names are declarations. Matching lengths does not prove assembly identity;
retain the chain/reference origin and hashes. Obtain chain/reference data from a
permitted source such as the [UCSC conversion documentation](https://genome.ucsc.edu/cgi-bin/hgLiftOver).
The analysis command never downloads them. Missing or incompatible local data
fails with exit 2, an explicit cause and required recovery in `WARNINGS.md`.

## Choose alignment and target-span criteria

`--min-match` is required and lies in (0,1]. It measures the fraction of **source
bases aligned through chain blocks**. It is not direct nucleotide sequence
identity and not the fraction of the destination bounding interval aligned.
Destination intervals can span target gaps and can change length. Accepted
length changes produce a prominent warning and an explicit `span_ratio`.

`--max-target-length 0` disables the optional destination-span limit. Supply a
positive limit when the analysis needs one; for example, the reviewed cross-species
ATAC workflow imposed at most 1 kb on lifted 500-bp peaks. Its separate minMatch
settings for peaks/DMRs and 10-kb loop anchors must not be replaced by one default.

Only one qualifying chain is accepted, regardless of score. The basic engine
does not select the highest-scoring hit, emit multiple regions, split mappings,
or process BED12 exon/block relationships. These restrictions differ from other
UCSC options, CrossMap formats and rtracklayer GRanges/list behavior.

## Optional reciprocal checks

Supply the independently directed reverse chain and explicit thresholds:

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar coordinate-lift `
  --input data/peaks.tsv --chain data/hg19ToHg38.over.chain.gz `
  --source-sizes data/hg19.chrom.sizes --target-sizes data/hg38.chrom.sizes `
  --source-build UCSC_hg19 --target-build UCSC_hg38 --min-match 0.95 `
  --reverse-chain data/hg38ToHg19.over.chain.gz --reverse-min-match 0.95 `
  --roundtrip-policy exact --unmapped error --out results/reciprocal_peaks
```

`exact` requires returning to the original contig, bounds and known strand.
`overlap` instead requires `--roundtrip-fraction` in (0,1]; that threshold must
hold for **both** the original and returned spans, with matching contig and known
strand. A forward size rejection is not evaluated for a round trip and is marked
accordingly. Failed reverse mappings and coordinate/strand mismatches are retained.

This test does not establish matching the same original peak identity among a
collection of overlapping peaks. It also does not prove cross-species orthology
or conserved chromatin accessibility. Those are separate reviewed study rules.

## Read results, failures and provenance

`results.tsv` contains every original ID, source interval and status, raw forward
coordinates, target length/span ratio, and requested reciprocal results. Forward
coordinates remain visible even when a size/reciprocal rule rejects the mapping.
`mapped.bed` contains accepted BED6 intervals only, with score 0; original scores
or summit offsets are not implied. Unknown strand stays unknown; known strands
are transformed with chain orientation.

Statuses include `MAPPED`, `NO_CHAIN`, `BELOW_MIN_MATCH`,
`NOT_UNIQUELY_MAPPED`, `TARGET_SPAN_EXCEEDS_LIMIT` and `REJECTED_ROUNDTRIP`.
`NOT_UNIQUELY_MAPPED` includes both ambiguous hits and chain overlaps entirely
inside unaligned gaps. It is deliberately not presented as an exact ambiguity
classifier. `BELOW_MIN_MATCH` uses the engine's exact diagnostic counter, avoiding
rounded-float decisions at threshold boundaries.

`diagnostics.tsv` retains the upstream human-readable candidate descriptions for
failed mappings. At most 100 descriptions are written per query/direction; any
truncation is counted and warned. Mapping decisions use the full engine, not
parsed diagnostic strings or their rounded percentages. The API serializes calls
because its exact diagnostic counter is mutable.

With `--unmapped retain`, unsuccessful mappings and an entirely empty accepted
set complete with prominent warnings/counts. With `error`, any unacceptable input
fails the analysis: no successful `results.tsv` or `mapped.bed` is published, and
the diagnostic tables remain under `partial/`. Read `WARNINGS.md`,
`analysis-status.json`, `session.yaml` and `manifest.yaml`. Correct the named
chain/reference/coordinate/threshold issue and rerun to a new output directory.
Existing inputs, chains, dictionaries and output directories are preserved.

Coordinate conversion does not normalize indels, verify REF, complement effect
alleles, harmonize GWAS summaries, convert BAM/WIG/VCF data, or perform downstream
QC, meta-analysis or clinical interpretation. The R liftOver workflow,
rtracklayer, MungeSumstats, CrossMap and reuse of pre-lifted GENCODE annotations
are separately catalogued rather than treated as direct UCSC executable runs.

Validation combines hand-calculated forward/reverse/gapped/ambiguous/integer and
threshold cases with independent primary dbSNP GRCh37/GRCh38 placements for four
public SNPs mapped in both directions through actual UCSC chains. Raw chain and
primary API download identities remain in the private research cache; derived
coordinate fixtures retain their source URLs and hashes. This does not establish
every chain region, software option, old version or complete paper pipeline.
