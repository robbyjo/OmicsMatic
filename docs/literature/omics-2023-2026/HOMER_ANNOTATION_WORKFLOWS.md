# Original HOMER supplied-GTF peak annotation

`homer-tools annotate` runs original `annotatePeaks.pl` 5.1, 4.11 or 4.11.1 with
`genome none`, `size given` and supplied GTF. Native Java preserves all peaks
and independently audits exact coordinates, complete populations, nearest TSS
and compatible gene identifiers. See `homer-annotation-validation.json` for the
current development executable and measured verification scope.

This supplies GTF-based peak annotation. Named-genome databases, repeat/CpG
tracks, sequence/motif extraction, peak calling, tag/coverage, RNA and GO
workflows require their own implementation and resources. Broader HOMER and the
full paper implementation goal remain active. Cell Ranger is excluded.

## Setup and prominent recovery

**Missing, forbidden, unsupported or changed dependencies SKIP this analysis,
exit3.** Read the cause and Required action in `WARNINGS.md`. Use Linux x86_64
Java, inside WSL on Windows, and a separate admitted annotation profile.

Where installation is allowed:

```sh
python3 tools/literature/install_homer_annotation.py \
  --profile linux-x86_64-annotation-5.1 --home NEW_PRIVATE_HOME \
  --cache ARCHIVE_CACHE --report NEW_REPORT --archive-dir VERIFIED_OFFLINE_ARCHIVES \
  --allow-install
```

Choose the 4.11 or 4.11.1 annotation profile explicitly when needed. Add
`--allow-download` only where permitted and offline archives are unavailable.
Analysis never installs, downloads a genome, substitutes a version, or silently
uses another annotation engine. Core-only `homer2` homes do not admit annotation. A runtime change detected after
execution starts invalidates provisional results with exit2; pre-run changed
dependencies skip3.

Setup admits the original Perl scripts and annotation executable with their
GPL notices. Only installation-path lines3/4 in `annotatePeaks.pl` and
`HomerConfig.pm` are relocated, following the upstream configuration pattern.
Scientific script bodies and compiled bytes remain pinned. No compiler is
needed for this prebuilt subset. The home needs a shell/Perl-safe ASCII path;
inputs/output paths can use UTF8 through an ASCII-named `--options-file`.

Perl, loaded modules/DSOs, shell utilities, dynamic libraries, host/kernel,
runtime inventory and actual scientific setup health are checked. If they
change, preserve the old home and reports and re-admit a new home where allowed.
Do not edit markers to bypass a mismatch. Official HTTP content hashes identify
the retained archives; they are not independently authenticated signatures.

**Other failures exit2 and quarantine provisional files under `partial/` when
there are files to retain.** Read `WARNINGS.md`, `analysis-status.json`,
`session.json`, `original.stderr` and retained inputs/ledgers. Correct the named
input, reference, parameter, resource or audit cause, then use a new output
directory. Existing files are preserved. Partial tables are not completed
results. An error before staging may have no provisional files to quarantine.

## Inputs and explicit choices

See [four runnable recipes](../../../examples/homer-annotation/README.md).
Supply all reference declarations and policies explicitly:

| Input or choice | Meaning |
| --- | --- |
| `peaks` / `peak-format bed6` | Exactly BED6: contig,start0,end_exclusive,id,score,strand. |
| `peak-format homer` | HOMER6/7: id,contig,start1,end1,strand,score,optional focus. Coordinates are one-based inclusive. |
| `gtf` | Unchanged UTF8 GTF9. The original default uses lowercase exon features and transcript_id. |
| `reference-lengths` | Exact contig TAB positive length dictionary used by native coordinate checks. No aliases or build conversion. |
| `reference-build` / `annotation-release` | Investigator declarations, retained with source hashes; declarations do not prove compatibility. |
| `gtf-id transcript` | Explicit original default. Gene-ID mode can conflate distinct TSS rows and is not silently substituted. |
| `gtf-unknown-strand` / `peak-unknown-strand` | `error` or explicit `original-plus` for dot-to-plus conversion. |
| `duplicate-ids` | `error` or `retain-ordinal`; every repeated peak remains a distinct alias. |
| `tss-ties` | `error` or `retain-original` with every compatible native candidate retained. |
| `missing-gtf-contig` | `error` or `retain` with original NA values for peaks on reference contigs lacking selected GTF exons. |
| `annotation-stats` | `omit` or `original-gtf-extent` diagnostic file. This does not certify a genome-wide statistical universe. |

No peak columns are silently discarded. Convert other BED variants explicitly
and preserve the source. Empty/reversed/out-of-reference intervals, invalid
UTF8, malformed attributes, inconsistent transcript parent identities and
unsupported integer ranges fail explicitly. Exon gene_id/transcript_id values
must use the single-space quoted attribute syntax accepted by the original.
The reserved `-HOMER` transcript delimiter fails before source ID truncation.

## Transcript sorting, metadata versions and nearest TSS

All three original `parseGTF.pl` versions sort selected exons by contig, gene,
transcript, start and end before grouping. Compatible noncontiguous rows belong
to the same transcript span. Native Java aggregates those spans independently
and records source-block counts; immutable inputs retain the original order.
The earlier experimental source-class warning claiming separate noncontiguous
groups was incorrect and was corrected before packaging.

Native checks use `floor((start1+end1)/2)`. Distances are transcript-oriented:
plus uses midpoint minus TSS; minus uses TSS minus midpoint. Peak strand does not
choose the sign. All equally closest transcript candidates remain in
`native-tss-candidates.tsv`. An original tied choice is checked for membership,
not certified as a uniquely supported gene. Feature labels can refer to a
different overlapping transcript than the nearest TSS.

Original gene metadata strips a terminal dot followed by decimal digits from
gene and transcript IDs, while Nearest PromoterID retains the full selected
transcript. Full GTF IDs and derived metadata IDs remain separate in
`gtf-transcripts.tsv`. Ambiguous version-stripped transcript-to-gene mappings
fail before execution. The original column labelled Entrez ID may contain a
version-trimmed GTF gene_id; it does not establish an Entrez namespace.
Do not automatically remove versions from source data or join gene symbols
without checking the actual namespace.

## What the audit establishes

Every original row must have a valid unique staged alias, exact input interval,
strand, score and focus metadata. The complete row count must match all supplied
peaks. Known-contig rows must have a native-compatible nearest transcript,
signed distance and the expected source metadata gene mapping. Missing-GTF
contig rows must retain NA annotation/TSS. Internal helper failures or absent
original finalization invalidate a zero parent exit.

Original feature labels, detailed annotations, gene symbols and diagnostic
numeric strings remain original-engine results. Native Java does not
independently reconstruct annotation priority, gene-symbol conversion, feature
category enrichment or empirical calibration. Source/GTF identity ledgers and
native nearest-TSS checks are separate from those claims.

The supplied length dictionary does not replace the original annotation
background: the original script does not read that dictionary. With supplied
GTF and genome none, annotation statistics reflect the original processed
annotation/extent, not a certified complete-assembly universe. `omit` omits the
statistics file, but original diagnostics can still appear in stderr. Retain
them as diagnostics and review the background/tested family before inference.
No q/E value or multiple-testing adjustment is inferred.

## Resources and outputs

Positive capacities cover combined input bytes, peak/GTF records, transcripts,
line length, original workspace, resident memory and time. The exact reference
dictionary also has a fixed one-million-contig bound.
Read `--help` for defaults. Original workspace and summed owned-process RSS are
monitored periodically; RSS can count shared pages more than once. These are
not OS-enforced disk or RAM quotas. Review `-Xmx` and capacities before changing
them. No automatic retuning or successful truncated result is published.

The shared runner retries brief fork/exec/exit memory-status absence, recognizes
terminal Z/X tasks and preserves the primary failure if cleanup also fails.
Missing live-process monitoring remains an unavailable dependency. Existing
failed experiments remain retained; they are not successful annotation results.

Review `WARNINGS.md`, `session.json`, `peaks.tsv`, `gtf-transcripts.tsv`,
`native-tss-candidates.tsv`, `results.tsv`, original stdout/stderr, optional
original statistics and source/runtime hashes. Synthetic comparisons do not
establish whole-paper, historical-environment, production-data or biological
accuracy parity.
