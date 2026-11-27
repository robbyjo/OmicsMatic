# Native Hornet allele sequences

`hornet-alleles` implements the allele-sequence component of [Hornet revision e642ac0e](https://github.com/TheFraserLab/Hornet/tree/e642ac0ee9ed6625ce415305b9ced4d686403c79) in Java. It supports unphased single reads, unphased pairs and phased pairs, with original sequence ordering and disposition counters. It needs Java 17 or later and no Python runtime.

**Scope:** this command does not perform a mapping analysis. BAM/FASTQ extraction and writing, quality/orientation handling, variant preparation, original alignment, remapping, remapped-read filtering and duplicate removal remain separate work. It adds no completed tool-family or paper-recipe credit. Standalone WASP and STARsolo's WASP mode require their own implementations and validation.

## Run and verify

```powershell
java -jar omicsmatic-0.4.1.jar hornet-alleles --example
java -jar omicsmatic-0.4.1.jar hornet-alleles --request request.json --out new-capture
java -jar omicsmatic-0.4.1.jar hornet-alleles --verify new-capture
```

Save the example as UTF-8 without a byte-order mark and replace its synthetic data and preparation statement with the actual input provenance. The example is one same-chromosome paired unit. A request may instead contain one single read. Coordinates are **zero based**. Read sequences must already have BAM alignment orientation; reverse-strand reads must not be reverse-complemented before calling this component. No qualities or strand transformations occur here.

Each variant has `chromosome`, `position`, `reference` and `alternate`. Each read has `chromosome`, `start`, `sequence`, `cigar` and the explicit Boolean `unmapped`. `phased` is an explicit Boolean. Missing or unknown request fields fail. Duplicate variant positions use the last row, as in the original loader. Chromosomes are literal identifiers; no normalization, genome-build inference or coordinate repair occurs.

The result contains one sequence list per read. The original sequence comes first; subsequent entries are alternatives. An empty list is a source-defined discard. The lists remain synchronized for pairs. `dispositions` contains the counters actually incremented by the selected source; it is not a complete read-accounting table for a BAM pipeline.

A successful capture contains exactly `request.json`, `result.json`, `WARNINGS.md`, `manifest.json` and `COMPLETED.json`. The complete request bytes and exact JAR identity are retained. Verification checks every file and recomputes every sequence and counter with that JAR. Preserve the producing JAR. A later JAR does not authorize refreshing an older capture's seals.

## Source behavior retained

- Shared SNPs in overlapping mates receive concordant substitutions. Discordant overlap bases cause a discard.
- Paired mode checks the product of allele-string lengths against 32 before phased processing. Consequently phased pairs with more than five biallelic sites are discarded even though that mode would otherwise emit two sequences.
- Single-read mode checks its 32-sequence threshold before each aligned base. A sixth SNP at the final aligned base can emit 64 sequences; an additional aligned base triggers a discard. This differs from paired mode.
- Single reads with a base outside ref/alt leave that site unchanged. Unphased pairs can substitute such sites. Phased pairs discard them without incrementing the original unused `non_refalt_base` counter. Phased single-read mode raises an error in the original and fails here.
- The original concatenates ref and alt strings. Its effective indel test detects a hyphen; its test for multi-base alleles iterates characters and does not detect them. Some multi-base inputs therefore produce individual-character substitutions. Duplicate generated sequences remain duplicated. These behaviors are retained and explicitly reported; they do not establish that such inputs are scientifically suitable.
- The source single-read function ignores the unmapped flag when a CIGAR is present; paired mode discards a unit if either flag is unmapped.

Mapped reads require a CIGAR consistent with the sequence. `M`, `=` and `X` align query/reference bases; `I`/`S` consume query; `D`/`N` consume reference; `H` consumes neither. Cross-chromosome pairs, CIGAR padding (`P`) and nonmonotonic CIGAR (`B`) are outside this native component's admitted domain and fail explicitly. The original Pysam 0.21 padding behavior can shift query positions or raise an error; this implementation does not silently substitute conventional padding semantics.

## Warnings, failures and recovery

**SKIP3:** a request or the exact producing JAR is unavailable. The dependent calculation is skipped. Restore the named file or build the executable JAR, then use a new output path.

**FAIL2:** invalid/changed bytes, unsupported input domain, cancellation or capacity exhaustion. No complete component result is admitted. When a capture directory was created, its original request, warnings and failure record remain; correct the named cause and retry a new directory. An existing directory is never overwritten. After any failure, do not use a partial result or remove an error marker to admit it.

Native protective capacities are separate from source read dispositions: request/result 32 MiB each, one million variant rows, one million bases per read, 4,096 generated sequences and 16 MiB generated sequence characters. Capacity exhaustion throws; it does not turn a read into an ordinary source discard. Thread interruption is checked cooperatively. These bounds do not reserve JVM memory or establish a hard wall-time bound.

No software is downloaded by this command. A complete future mapping workflow must skip dependent analysis when a required runtime/reference, install permission or licence is unavailable, and identify the prerequisite needed to resume.

## Evidence and remaining paper work

The Java component is compared against 334 cases produced by the unmodified original Hornet module: 332 successful sequence/counter cases and two original failures. The controls cover phased/unphased pairs, overlapping mates, clipping, insertions, deletions, splice gaps, `=`/`X`, duplicate positions, other bases, hyphens, multi-base source behavior and the asymmetric sequence limit. Three original reverse-complement controls and four input/error tests complete the native test suite. Synthetic inputs, results and source identities are retained in the test fixture. Original source/runtime files remain private. Apache 2.0 attribution and licence are packaged under `META-INF/licenses/`.

P007 uses a modified WASP pipeline linked to Hornet, but the authors' exact Hornet revision and complete species-specific variant preparation remain unestablished. P073 uses STARsolo 2.7.10a's built-in WASP mode; P187 reports standalone WASP 0.3.4 with updates as of 2023-02-09 and BWA-mem 0.7.17. P157 explicitly omitted WASP filtering for its sQTL analysis. These contexts must not be merged into one executable recipe. Canonical observations and completed-family counts are unchanged by this component.
