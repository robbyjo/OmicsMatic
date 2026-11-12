# Native replacements for external bioinformatics calls

The current source provides six bounded workflows without an external analysis
process. Build with `./gradlew check executableJar` or
`.\gradlew.bat check executableJar`, using Java 17 or newer. Existing adapters
retain their default upstream engine. Select the native engine explicitly;
native analysis never falls back to a subprocess.

| Workflow | Native entry point | Implemented boundary |
| --- | --- | --- |
| Local GSEApy enrichment | `gseapy-native` | One supplied local library, explicit background, source identifier preparation or already prepared literal identifiers, one-sided ORA and original BH family |
| MultiQC report subset | `sequence-qc --engine native --method multiqc` | Explicit FastQC ZIP/Cutadapt JSON reports and native equivalents; source identities, findings and complete source data retained in a self-contained HTML report |
| Basic SAMtools operations | `hts-tools --engine native` | SAM/BAM filtering, coordinate/lexicographic sorting, BAI indexing and complete 1.24 flagstat categories |
| Basic FASTQ QC | `sequence-qc --engine native --method fastqc` | Every-record raw length, quality, base-composition and GC measurements; unbinned per-base distributions |
| Adapter trimming | `sequence-qc --engine native --method cutadapt` | Cutadapt 5.2 regular 3-prime adapter/indel/wildcard/tie/prefilter rules, fixed left cut, Phred+33 quality trimming and paired minimum-length filtering |
| HTSeq file counts | `htseq-tools --engine native --request ...` | Explicit 2.1.2 SAM/BAMÃ¢â‚¬â€œplain-GTF preparation, name/position pairing and deterministic NONE/ALL/FRACTION counting; complete native replay verification |

These are native replacements for these operations. They do not reproduce whole
upstream packages or infer paper-specific versions, references or scientific
settings. Existing catalog tool counts are unchanged. Older released executables
do not acquire these commands from a source update.

## Run the complete synthetic example

From the repository root in PowerShell, after building:

```powershell
$nativeJar = 'build/cli/omicsmatic-0.3.7.jar'
java -jar $nativeJar gseapy-native --input examples/native-tools/enrichr.json --edition 1.1.8 --identifier-policy source --out build/native-tools-example/enrichment
java -jar $nativeJar sequence-qc --engine native --method fastqc --reads examples/native-tools/reads_R1.fastq --reads2 examples/native-tools/reads_R2.fastq --out build/native-tools-example/qc --no-config
java -jar $nativeJar sequence-qc --engine native --method cutadapt --reads examples/native-tools/reads_R1.fastq --reads2 examples/native-tools/reads_R2.fastq --adapter3 AGATCGGAAGAGC --adapter3-2 ACGT --quality-cutoff 20 --minimum-length 6 --error-rate 0 --out build/native-tools-example/trim --no-config
java -jar $nativeJar sequence-qc --engine native --method multiqc --reports examples/native-tools/reports.tsv --out build/native-tools-example/report --no-config
java -jar $nativeJar hts-tools --engine native --method sam-sort --input examples/native-tools/reads.sam --create-index true --max-records-in-ram 1 --out build/native-tools-example/sorted --no-config
java -jar $nativeJar hts-tools --engine native --method sam-view --input build/native-tools-example/sorted/alignment.bam --region chr1:1-100 --out build/native-tools-example/selected --no-config
java -jar $nativeJar hts-tools --engine native --method sam-flagstat --input examples/native-tools/reads.sam --out build/native-tools-example/flags --no-config
java -jar $nativeJar htseq-tools --engine native --request examples/native-tools/htseq-count.json
java -jar $nativeJar htseq-tools --engine native --request examples/native-tools/htseq-verify.json
```

Shell paths and line-continuation syntax may be adapted on Linux/macOS. The same
JSON inputs resolve paths relative to their request file. The example preserves
all synthetic inputs. Every destination must be new. Choose new output paths and
update the JSON/report mapping for a retry; an existing destination is refused.

Successful native bundles contain `analysis-status.json` with
`result_admitted: true`, `manifest.yaml` with engine/input/output identities, and
the operation's tables. A failure returns2, records `result_admitted: false`,
retains diagnostics and moves partial output under `partial/`; those files are
unadmitted. Read `WARNINGS.md` and the operation's disposition ledgers.

## Local enrichment details

`gseapy-native` requires edition 1.0.4, 1.1.2, 1.1.3 or 1.1.8 and an explicit
`--identifier-policy source|literal`. Input is a JSON object with
`schema_version: 1`, string-array `query`, a local `gene_sets` object mapping term
identities to string arrays, and explicit `background`. Background is either a
string array, a nonnegative 32-bit integer population, or deliberate null for the
complete library union. No Enrichr library name or remote lookup is accepted.
`--alpha` defaults to 0.05. JSON input is limited to 16 MiB and 2 million memberships.

SOURCE follows the pinned original class's edition-specific preparation. Version
1.1.8 strips list identifiers; the older editions retain their whitespace.
Integer identity and case conversion follow the selected edition. It retains original 1.1.8 errors for numeric
backgrounds/integer queries, and older editions' empty-result concatenation
errors. LITERAL accepts already prepared, unchanged identities and can retain an
empty statistical family or undefined numeric margins. Selecting LITERAL is an
explicit preparation choice, not a silent repair of an original failure.
Current JVM Unicode tables govern case conversion; inspect
`query-preparation.tsv`. Library identities retain their supplied spelling.

Only positive-overlap terms enter BH correction after explicit background
intersection. Read `omitted-terms.tsv`. `hits.tsv` preserves individual hit
identities without ambiguity from the display table's semicolon separator.
Undefined P values, source floating-point zeros, infinite combined scores and
edition 1.0.4's unavailable combined score remain visible. There is no
permutation GSEA, GSVA fitting, remote-library recovery or inferred annotation.

## Sequence QC and reports

Native FASTQ QC measures raw Phred+33 input, including every record. INFO denotes
measurement without a pass/fail threshold. It supplies no FastQC duplication,
contamination, adapter-content or overrepresentation decision. Per-base quality
percentiles use the inverse empirical CDF; per-read mean qualities are floored
integers; per-read GC bins round GC over canonical A/C/G/T bases to the nearest
integer. Empty reads have no mean-quality or GC observation. N and other IUPAC
bases have separate counts. No smoothing, sampling or read filtering occurs.
`--maximum-positions` defaults to 100000, with an explicit upper bound of 1 million;
each native ZIP member is bounded to 32 MiB. Mates must have matching identities
and order. Reports are named `read1_native_qc.*` and `read2_native_qc.*` and carry
an explicit native producer marker.

Native report aggregation accepts the same three mapping columns as the
upstream adapter: `sample_id`, `fastqc_zip`, `cutadapt_json`. Paths resolve against
the mapping file. Native QC ZIP/native trimming JSON inputs are also accepted.
Distinct source files and case-insensitively unique explicit sample IDs are
required. Original FastQC WARN/FAIL findings are retained, and original raw
module tables/JSON remain inspectable in the HTML. HTML-sensitive source text is
escaped. Complete source files and hashes are saved under `source-reports/`.
An aggregate report is not a new QC acceptance decision. Bounds are 64 MiB per
source file, 2 GiB of source files, 10000 sample rows, 32 MiB per ZIP text member, and
`--maximum-report-characters` (default 16777216). No MultiQC plugin or automatic
file discovery is used.

## Adapter trimming

Native trimming uses the same scientific CLI inputs as the bounded upstream
adapter. It performs fixed left cutting, then 3-prime quality trimming, then
regular 3-prime adapter removal. Adapter IUPAC wildcards are enabled, read
wildcards are disabled, and the source indel costs, score/tie rules and kmer
prefilter are retained. `--overlap` is capped to adapter length. Under the
original interface `--error-rate 1` means one absolute error, rather than a
100% error fraction. All-N adapters fail.

The original header and retained base/quality span survive together. Both mates
are discarded if either fails `--minimum-length`. Complete per-read stage,
alignment and pair dispositions are saved in `read-dispositions.tsv`.
`cutadapt.json` has an explicit OmicsMatic producer tag, not a claim that Cutadapt
ran. Empty inputs and outputs remain visible. `--alignment-operations` bounds
DP cells and conservative kmer-probe work across the complete run; its default
is 1 billion; the adapter kmer profile is bounded to 1 million probes. There is no linked/5-prime adapter, demultiplexing, reverse-complement
search or implicit installation. Native analysis rejects `--python`/`--cores`.

## SAM/BAM operations

Native `sam-view` applies explicit MAPQ, required/excluded flags and inclusive
one-based regions while streaming the input; it needs no input index. Native
`sam-sort` supports coordinate or lexical read-name order, spilling to disk after
`--max-records-in-ram` records (default100000). Equal-key order, header
serialization and compressed BAM bytes need not match SAMtools. Full records,
including tags and qualities, survive sorting and filtering. Coordinate BAI
indexing checks actual record order; `sam-index` preserves the source BAM.
`sam-flagstat` supplies complete source 1.24 integer categories and rounded
percentages with separate vendor-QC groups and null undefined percentages.

The native I/O library is pinned HTSJDK 5.0.0. Byte quotas or a native thread count
are not inferred from a record limit; native mode rejects `--memory-mb`,
`--threads` and external executable options. Duplicate marking, pileup, variant
calling and all BCFtools methods remain explicit upstream operations. Local
SAM/BAM is supported; CRAM and remote inputs are outside this native profile.

## HTSeq files and verification

Native COUNT uses the upstream JSON schema with explicit `version:"2.1.2"`,
without `runtime_home` or `memory_bytes`. The complete
[example request](../examples/native-tools/htseq-count.json) declares the
scientific choices. GTF is plain version2 syntax, including quoted semicolons
and last-duplicate attribute behavior. Source SAM/BAM is decoded with HTSJDK;
M/=/X CIGAR operations produce aligned blocks. NH must be an exact 32-bit integer.
The first record determines single/paired layout, as in the source. Native
pairing retains source name-group/position-buffer choices, orphans, ambiguity
order and primary-only prefilter behavior. Unknown annotation chromosomes,
missing NH short-circuits and overlapping summary categories remain explicit.

`counts.tsv` retains every feature, including zeros; `summary.tsv` retains all
five source categories. Inspect `read-preparation.tsv`, `unit-dispositions.tsv`
and `report.json`. Original inputs are captured before analysis. Capacity fields
are `maximum_features` (default 2 million), `maximum_buffer_records` (100000 actual
buffered alignments), `maximum_units` (1 trillion),
`maximum_blocks_per_alignment` (100000), `max_input_bytes` (1 GiB per file) and
`max_output_bytes` (64 MiB excluding captured inputs). The record-buffer bound is
deliberately explicit; it is not the source's unique-key buffer-size parameter
or an OS RAM quota. `timeout_seconds` is a cooperative whole-analysis timeout.

Native VERIFY checks the complete saved file/hash inventory and reparses the
captured raw inputs to replay all count and disposition tables. It detects
missing/extra/tampered files and differing scientific results. It does not
authenticate the producer or establish biological correctness. Historical HTSeq
editions and RANDOM allocation remain upstream. No proper-pair, barcode/UMI,
normalization or differential model is inferred.

## Validation and source provenance

The checked-in controls include 400 fresh original local-Enrichr class calls
across four editions, 7152 actual original Cutadapt 5.2 trimming/alignment controls,
and 40 saved original HTSeq 2.1.2 controls now independently replayed from raw
SAM/BAM/GTF. SAM flag categories are compared against the installed1.24 engine
over all 4096 flag combinations. Integration tests also compare actual
FastQC/MultiQC counts/findings and original/native trimming outputs. See
[the retained receipts](validation/native-tools/).

The original Cutadapt source and MIT license are bundled under
`org/jlinalg/sequence/cutadapt-5.2/`, with exact hashes in `SOURCES.json`.
GSEApy/HTSeq sources retain their existing pinned resources and license notices.
These are bounded synthetic/source comparisons. No whole-package equivalence,
whole-paper reproduction or biological calibration is claimed.
