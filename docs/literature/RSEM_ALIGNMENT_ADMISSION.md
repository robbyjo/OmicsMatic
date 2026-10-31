# RSEM alignment accounting and source-linked decoding

The current development source adds `RsemSamAlignments`, a bounded Java audit of complete canonical SAM streams. A source-linked decoder also supports original SAM, BAM and CRAM inputs while retaining effective per-record reference lengths. **Protected runtime/reference admission, the public fitting workflow and portable setup remain unfinished. RSEM still counts as missing.**

The native library checks source grouping and coordinates, preserves every physical record and hit, and compares complete original read exports and `.cnt`/`.dat`/`.omit` outputs. Parser-compatible counts are separate from input safety findings; they do not certify a valid fit, biological accuracy or whole-paper reproduction.

## Corrected count invariant

The original `HitContainer::calcNumIsoformMultiReads` counts **every group with more than one alignment**, including cross-gene mappings and duplicate hits to the same transcript. It is not a subset of uniquely mapped genes. The earlier Java reader rejected a valid case with `nUnique=0`, `nMulti=1`, `nIsoMulti=1`.

Fresh unchanged-original runs confirm that case across all five releases and SAM/BAM/CRAM. The old Java failure is retained. `RsemCounts` now checks `nIsoMulti` exactly against the complete multiplicity histogram, and requires `nMulti <= nIsoMulti`. Its legacy record component `multipleIsoformGroups` keeps its source-compatible name but means all multiple-alignment groups. Earlier file-reader/result-audit validation records are explicitly superseded for current count admission; their original source hashes and historical evidence remain retained.

## Using the native audit

Read the complete prepared reference with `RsemReference` first; see [the file-reader guide](RSEM_FILE_READERS.md). Build current source with Java 17 or newer. The earlier released JAR does not contain these development APIs.

```java
var options = new RsemSamAlignments.Options(
        RsemAbundance.Release.V1_3_3, 3, "", 25);
var limits = new RsemSamAlignments.Limits(
        1_000_000, 500_000, 5_000_000, 10_000,
        100_000, 10_000, 100_000, 1_000_000_000,
        500_000, 50_000_000, 10_000, 1_000_000_000);
var audit = RsemSamAlignments.read(
        Path.of("reviewed-canonical.sam"), reference, options, limits);
var summary = audit.summary();
System.out.print(summary.accounting().originalText());
```

These are illustrative capacities, not inferred study sizes. Review budgets for the complete dataset. Exceeding a record/group/hit/reference/name/line/character/byte capacity fails the whole audit instead of dropping members. The Path API retains every raw source byte's SHA-256, byte count and resolved path, and checks source identity while reading. It accepts UTF-8 SAM with LF or CRLF framing. It does not decode raw binary inputs or certify the provenance of text produced by an arbitrary decoder.

Read types are original `0` single/no-quality, `1` single/quality, `2` paired/no-quality and `3` paired/quality. Paired counts remain fragment counts, without doubling. The empty read-type tag disables suppressed-read classification; an explicitly selected positive integer auxiliary tag classifies otherwise-unmapped groups as N2. Mapped hits do not use that tag for filtering.

For streaming exports, the Reader overload accepts a `Sink` and caller-owned deadline/integrity `Check`. Every physical record, complete source group and finding reaches the sink. Sink output remains partial until the method returns successfully. The caller owns the reader, original binary/reference snapshots and decoder execution receipts.

## Decoder metadata is required for binary provenance

Do not equate emitted SAM header text with a binary decoder's effective dictionary. In the retained CRAM mismatch case, header text says length 799 while the original decoder uses 800. That changes the reverse position from 679 to 680. Native admission retains both values and flags the disagreement; it does not silently repair the header or guess from the prepared reference.

`tools/literature/rsem-reference/decode.cpp` builds against the exact unmodified runtime's bundled HTSlib. It emits canonical SAM plus `rsem-decoded-records-v1` metadata for every physical record. The metadata binds ordinal, full query name, flags, target, position, sequence length, CIGAR and effective reference length. It requires an `END` population marker after complete decoding. Use `decodedRecordMetadata(metadataReader, limits, check)` with the metadata-bound Reader overload. Every record and terminal count must match; swapped, truncated, extended or mismatched metadata fails.

For adjacent pairs, coordinates use the dictionary state after both physical mate records have been decoded, including reversed physical mate order. The raw SAM header remains visible. The source-linked helper and exact original references are privately tested; this is not yet a portable runtime receipt or a public project fitting command.

## Findings and required actions

**Resolve every blocking finding before fitting.** `safeForFitting()` is false for dangling mates, split/repeated query identities, conflicting mate names, historical inconsistent lengths, missing qualities, empty sequences, reference-span or dictionary disagreements and other named source problems. Read `findings()` and preserve all records, counts and diagnostics. Correct the input source or explicit original options, then rerun to a NEW output. Never discard a failing row or change a checksum to manufacture a successful run.

Important source behavior is retained:

- The original validator returns exit code 0 even when its diagnostic says the input is invalid. Inspect the diagnostic verdict; exit 0 alone is insufficient. It can also declare a multi-operation matching CIGAR valid while the parser rejects it. RSEM's parser requires exactly one `M`, `=` or `X` operation of the sequence length.
- A final dangling paired record is consumed and silently omitted by the original parser. The native physical census retains it, reports the ignored record, and blocks fitting.
- Secondary, supplementary, duplicate and QC-fail flags are not filters in this original parser. Duplicate hits remain hits. Query names use the first whitespace-delimited word; `/1` and `/2` suffixes are not removed.
- Original 1.2.28 can retain first-read lengths when later alignments have different lengths. The 1.3.x parser rejects those differences. The historical result and its blocking warning are retained.
- First-alignment sequences and qualities are restored to original read orientation; later content is ignored by the source parser and remains reported. Missing quality bytes wrap to ASCII space in original exports. This is not a quality estimate: resolve the source qualities or explicitly choose an appropriate quality-free read type.
- Seed and poly-A flags use the original `OLEN=25` logic. They are reported without altering N0/N1/N2 or filtering parser groups. Omitted header references remain explicit, without treating them as biological absence.

**If Java is unavailable and installation is forbidden, skip the dependent native audit.** Supply a permitted Java 17+ host before running it. For binary decoding, use an admitted exact original decoder and permitted local resources. Missing required CRAM FASTA or `.fai` returns helper exit 3 with a required-resource diagnostic and creates no output. Supply the exact reference/index on a permitted host, or keep dependent decoding skipped. No installer or arbitrary-runtime admission is implied here.

**Invalid input, decoder errors or existing outputs fail helper execution with exit 2.** Preserve partial SAM/metadata and the named error; no complete metadata marker is produced for a truncated binary input. Correct the cause and rerun to NEW paths. Existing outputs retain their original bytes. The helper's private validation processes have explicit memory/CPU/file/deadline bounds; the public protected execution contract still requires completion.

## Evidence and remaining work

The complete corpus spans 580 source format/release cases, 478 successful decodable original parser projections, 97 retained original-parser failure cases and five decoder failures. Comparisons cover every counter, original hit coordinate, omission and emitted FASTA/FASTQ byte, including nonuniform reversed qualities. Five releases also supply 2,640 original read-flag vectors. Existing result/reference/model/theta and arithmetic tests remain part of the current focused suite.

All 111 focused tests pass against the exact candidate JAR on Windows Java 21/25 and Linux Java 21, with zero skips, strict Java 17 compilation and private Javadoc checks. The only changed predecessor JAR member is the deliberately corrected `RsemCounts.class`; all other existing payload bytes remain unchanged. New alignment classes are recorded separately. The five decoder recovery controls cover missing references/indexes, existing-output refusal and truncated binary input without a complete metadata marker.

The [alignment validation record](RSEM_ALIGNMENT_ADMISSION_VALIDATION.json) records the exact candidate, test/JVM outcomes, count correction, source/fixture pins and decoder recovery controls. The earlier lossy canonicalization comparison and failed attempts remain in development history. Protected reference/runtime closure, typed fitting execution, setup, public commands and fitting examples are the next required work. The broader 250-paper implementation goal remains open.
