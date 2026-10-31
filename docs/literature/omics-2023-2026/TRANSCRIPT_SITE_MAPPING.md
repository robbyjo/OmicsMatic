# Transcript TSS/TES mapping: native development workflow

`org.jlinalg.genetics.TranscriptSiteMapping` maps supplied variant associations to
explicitly selected transcription start/end sites. The native API, bounded
streamed TSV reader, typed CLI, sealed development JAR and
[synthetic source example](../../../examples/transcript-site-mapping/README.md)
are available. The complete FAVOR workflow remains open. **FAVOR stays marked missing; the
validated unique-tool count has not increased.**

**Required action:** verify the exact assembly, transcript snapshot, gene and
transcript ID namespaces, gene biotypes, one-based coordinates and strand before
using this API. Choose TSS, TES or both, the distance radius and whether its exact
boundary is inclusive. Empty mappings only describe the supplied reference.
They do not establish biological absence or gene causality. Every result carries
these scope warnings; missing P and reported zero P add specific recovery text.
Invalid inputs, capacity exhaustion and cancellation reject the entire native
call without returning a partial result. The CLI reports **exit 2**, quarantines
scientific partials and retains `WARNINGS.md`, `session.json`,
`analysis-status.json` and a hashed manifest. Existing output directories are
refused. The public verifier reports **SKIP 3** if Java is unavailable or
forbidden; provide Java 17+ on a permitted host or retain that analysis as
skipped. Neither the native method nor verifier installs software.

## Run a study with explicit inputs

Use `fuma-tools --method transcript-sites --help` for all supported options.
The [runnable examples](../../../examples/transcript-site-mapping/README.md)
include a three-workflow verifier and a direct source API example. The checked
development JAR is `build/literature-review/transcript-site-candidate-1/jlinalg-0.3.7.jar`,
SHA-256 `ccf750c9091cc6136ea18d67eed553879919d8201654933a8d27837869316e62`.
The earlier Fisher JAR does not contain this new command. No public release or
complete current-root build is asserted.

Inputs are plain UTF-8 TSV, without quoting. Required columns may be reordered;
additional columns remain in exact raw snapshots. Only literal `NA` means a
missing input P. Blank values, invalid numbers and duplicate headers reject.
Expected row counts include all missing, above-cutoff and unselected-biotype
rows. Coordinates and ID/build labels are matched exactly.

For a fully synthetic minimal example, save `variants.tsv`:

```text
context	variant_id	chromosome	position	p
synthetic-trait	at-start	1	100	1e-6
synthetic-trait	interior	1	500000	1e-1000
```

Save `transcripts.tsv`:

```text
gene_id	transcript_id	chromosome	start	end	strand	gene_biotype
geneA	txA	1	100	1000000	+	protein_coding
```

Save `site-options.json` beside both tables. Replace these synthetic reference
labels and data with the exact identified resource for an actual study:

```json
{
  "method": "transcript-sites",
  "analysis-id": "synthetic-sites",
  "variants": "variants.tsv",
  "transcripts": "transcripts.tsv",
  "expected-variant-rows": "2",
  "expected-transcript-rows": "1",
  "build": "synthetic-build",
  "reference-build": "synthetic-build",
  "reference-id": "synthetic-transcripts-v1",
  "gene-namespace": "synthetic-genes-v1",
  "transcript-namespace": "synthetic-transcripts-v1",
  "variant-namespace": "synthetic-variants-v1",
  "context-namespace": "synthetic-traits-v1",
  "p-cutoff": "1e-5",
  "radius-bp": "10000",
  "boundary": "INCLUSIVE",
  "sites": "TSS,TES",
  "gene-biotypes": "protein_coding",
  "missing-p": "EXCLUDE",
  "out": "new-site-analysis"
}
```

Run `java -jar PATH_TO_JAR fuma-tools --site-options-file site-options.json`.
JSON values must be strings; data/output paths resolve beside the carrier.
Direct flags override JSON values. Use an ASCII carrier command-line path on
Windows, declaring Unicode data paths inside the JSON. Optional
`variants-sha256` and `transcripts-sha256` enforce exact lowercase SHA-256 pins;
every run also records and re-verifies original and snapshot hashes.

Inspect `mapping-policy.tsv`, `variant-dispositions.tsv`,
`transcript-dispositions.tsv`, `site-hits.tsv`, `gene-mappings.tsv` and
`resource-ledger.tsv`. The synthetic interior variant must remain unmapped even
though its P is very small. `WARNINGS.md` is required interpretation guidance.
Bounds on combined input rows, cells, line/file/snapshot/output bytes, output
rows, hits, operations and elapsed time reject the complete analysis rather than
truncating populations. Defaults and overrides are listed by `--help`.

## Reported use and separate annotation models

[P130, Shade et al., Nature Genetics (2024)](https://www.nature.com/articles/s41588-024-01939-9)
describes FUMA and FAVOR jointly. Its reported mapping selects variants with
GWAS P at most `1e-5` within 10 kb of a protein-coding gene's transcription start
or end. The paragraph does not identify a transcript-selection rule, endpoint
boundary convention or FAVOR version. It also describes MAGMA gene testing and
gene-set analyses. Joint wording does not assign every operation to FAVOR.

The article's [archived author code, v0.11](https://zenodo.org/records/11090130)
contains `code/magma/01_snp_gene_annot.sh`, which calls MAGMA with
`--annotate window=10`. Its separate comment says `gene window: 100 Mb`; retain
that original comment without treating it as the executed setting. The pinned
MAGMA 1.10 executable reports a 10,000-base window for the actual command. A
fresh synthetic annotation maps nine variants to a long gene body and its
flanks; the native TSS/TES rule maps six. The three additional MAGMA variants
are gene-interior positions outside both site windows. These are distinct
annotation models. The archive does not identify a historical FAVOR execution,
and neither result proves the exact published execution or biological policy.

The archive SHA-256 is
`5995ae4b0997e04da92474023d2b42b63546351b1d8d889875e97e8219ad67d9`.
The reviewed frozen P130 methods paragraph SHA-256 is
`a5778298c8eef536f52b99ac63afe60a8053b44c4a06f806c1de472820308fdd`.
Existing `GeneWindowMapping` and `FumaVariantAnnotations` remain separate
gene-body methods. This new API never silently substitutes one model for another.

## API contract

- `Variant` stores context, exact variant ID, literal chromosome, positive
  one-based `long` position and `BigDecimal` P; null means missing. P selection
  uses exact decimal `P <= cutoff`, including positive values below binary64
  range. The same variant ID across contexts must have identical coordinates;
  duplicate variant/context rows reject.
- `Transcript` stores gene ID, unique transcript ID, chromosome, one-based
  closed start/end, explicit `+`/`-` strand and **gene biotype**. A reverse-strand
  TSS is the right endpoint; its TES is the left endpoint. One gene must have
  consistent chromosome, strand and gene biotype across all supplied rows.
- `Reference` identifies the supplied snapshot, assembly and ID namespaces.
  The requested build must match literally. Chromosome aliases, gene versions,
  liftover, canonical-transcript choices and biotype aliases are never inferred.
- `Options` declares the exact decimal cutoff, radius, strict/inclusive boundary,
  selected sites, gene biotypes, missing-P policy and capacities. Missing `ERROR`
  rejects; `EXCLUDE` retains an excluded disposition. An inclusive radius of zero
  admits exact sites; a strict radius of zero admits none.
- Every original variant and transcript receives a result. Each original
  transcript/site hit remains visible, including coincident sites and repeated
  sites on different transcripts. Gene mappings collapse these only within the
  same original variant row and gene, retaining hit and transcript counts.
- Signed distance is positive in the transcript's 3-prime direction. Nearest
  distance refers only to matching selected sites. Unmapped and excluded rows
  retain a missing nearest-hit distance.
- Input/hit/work bounds and cooperative cancellation apply during indexing,
  sorting, searches and output construction. Exceeding a capacity rejects rather
  than truncates. Positive `long` coordinates and the maximum `long` radius are
  handled without arithmetic overflow. A binary-search site index avoids a
  complete variant-by-transcript scan in production.

## Validation and remaining work

The [validation record](transcript-site-validation.json) records strict Java 17
compilation, 36 native plus 28 CLI tests per Windows Java 21/25 and Linux Java 21,
a combined 1,459-test gate in 100 suites with clean Javadoc, and 1,459 exact-JAR
tests on each Windows Java profile. All 64 new tests also pass against the exact
JAR on Linux Java 21. Forty-eight independent Python
Decimal/arbitrary-integer brute-force protocols compare **every** disposition,
site hit, transcript count and unique gene mapping. Each Java profile covers
10,436 association rows, 3,456 transcript rows, 15,797 site hits and 4,831 gene
mappings. All 144 actual `java -jar` protocol commands pass, including complete
resource/policy/snapshot/member-hash audits. Scientific TSV bytes agree across
all three profiles. The public verifier passes all three recipes on each
profile. Nine additional packaged/verifier controls check Java skips, artifact
pins, protected existing outputs, model/input failures, limits and quarantine.

This validates the supplied-resource native workflow. It does not validate FAVOR's current
API, its historical variant annotations, annotation acquisition, gene enrichment,
MAGMA fitting, gsMap or a complete paper pipeline. Missing or
forbidden runtimes must remain skipped with a named cause and required action;
invalid scientific inputs must remain failures with retained diagnostics.
