# Named-genome feature, TE and TSS annotation

The development entry `homer-tools named-annotate` runs the unchanged scientific
body of HOMER 5.1 `annotatePeaks.pl` against explicitly pinned v7.0 hg38/mm10
annotations. It uses `-size given` and the default 19 output columns. Only the
configured installation prefixes in two Perl files change. Original compiled
helpers, Perl/module/library bindings, configuration and resources are checked.

The paper role motivating this entry is P053 (Nature,
[10.1038/s41586-023-06819-6](https://doi.org/10.1038/s41586-023-06819-6)):
hg38 TE/TSS association for human genomic regions and mm10 repeat annotation
for mouse ATAC regions. The paper does not state the HOMER/database edition.
Selecting 5.1/v7.0 supplies an explicit, auditable operation; it does not prove
the paper used those editions or reproduce its data-dependent conclusions.

See [the executable examples](../../../examples/homer-named-annotation/README.md)
for resource preparation, private setup, both coordinate formats and a verifier.
The lock is `tools/literature/homer-named-lock.json`; the same bytes are packaged
with Java. Resources stay private and are not redistributed with the project.

## Required resource and capacity review

The preparer verifies four official archives and extracts twelve original files:
each genome's `chrom.sizes`, `.tss`, `.basic.annotation`, `.full.annotation`, plus
human/mouse `2gene.tsv` and `.description`. Sequence, RNA, repeat construction,
GO, MSigDB and KEGG collections are not admitted by these twelve files.
Downloads and extraction require their explicit flags. Missing permissions or
dependencies skip the affected operation and explain recovery. Official HTTP
hash pins are content identities, not independently authenticated signatures.

The inspected basic/full files contain 21,173,390 rows in total. They include
overlaps, negative starts, reference overruns, missing-reference contigs and
artificial ends at 1.5 billion. Their original bytes remain intact. The explicit
`resource-bounds` policy permits review and retention of these irregularities;
input peaks must still lie inside the exact pinned reference dictionary.
No contig alias, clipping, filtering or coordinate repair is inferred.

Promoter windows have a 4,000-base span and a fixed original 2,000-base offset.
A window can extend outside a contig even when its TSS anchor is valid. Invalid
or missing-reference anchors require their separate `tss-resource-anchors`
choice. The inspected hg38 TSS resource contains 2,425 such anchors. All raw
records and source windows remain available in `resource-tss.tsv`.

Full original annotation requires several GB of RAM even for a few peaks.
The entry requires at least 6 GiB of allowed upstream RSS and available memory;
the default allowance is 8 GiB. Java heap is separate. Time, workspace and
owned-process RSS checks are periodic monitors, not OS quotas. An exiting
Linux task with no address space is recognized only with the exiting flag and
zero virtual/RSS fields; permission failures and unexplained missing memory
still stop the affected analysis. Kernel teardown can continue reclaiming pages
after a task loses its userspace address space.
The relevant upstream implementation is retained in
[Linux 6.18 procfs](https://raw.githubusercontent.com/torvalds/linux/v6.18/fs/proc/array.c)
and [process exit](https://raw.githubusercontent.com/torvalds/linux/v6.18/kernel/exit.c).

## Independent native checks

Native Java streams the complete feature resources with bounded memory. Feature
lookup uses inclusive containment of the positive integer midpoint and source
start/end/strand ordering. Construction priority remains a separate resource
field. All names sharing the first matching geometry remain compatible
candidates; no unique choice is invented for an original tie.

Feature display cleanup removes terminal numeric and HOMER suffixes according
to the original wrapper. TSS displayed IDs remove everything from the first
`-HOMER` marker. Raw IDs stay unchanged alongside derived displayed IDs, including
numeric dot versions. Native nearest-TSS auditing retains all equally close
records and checks the original gene-oriented signed distance independently of
peak strand. The original helper's repeated raw-ID lookup is last-row-wins per
contig; raw source rows remain retained. The pinned TSS files have no duplicate
raw IDs, while many distinct raw IDs collapse to one displayed ID.

Every input peak has an ASCII execution alias and an immutable source/ordinal
ledger. Duplicate names, dot strands, feature ties, observable TSS ties and
missing TSS contigs each require explicit choices. BED6 is zero-based half-open;
HOMER6/7 is one-based inclusive. No field is silently discarded.

Native category backgrounds sum every inclusive source interval on chromosomes
represented by the input queries. Overlaps are included. Source rules exclude
negative starts and ends beyond 1.4 billion. This is neither an interval union
nor a complete assembly-wide universe. When requested, both complete original
category count/background blocks are checked against native populations.
Original P values and ratios remain diagnostics; their calibration is not
established by exact count/bp agreement.

All 19 original result columns and gene metadata strings remain in the original
stdout and the joined result table. Native auditing does not independently
certify the gene-symbol conversion, namespace or statistical calibration.
Read all compatible candidates before assigning a unique gene or TE; a feature
can differ from the nearest TSS.

## Status and recovery

**Always read `WARNINGS.md`.** Exit 0 can still require resource, ambiguity and
scientific review. Exit 3 means a dependency or capability is unavailable or
forbidden; the warning states the cause and **Required action**. Exit 2 means
input, capacity, execution or audit failure; provisional outputs are retained in
`partial/` and are not completed results. Setup reports preserve their own
original diagnostics. Correct the cause and use new paths; existing outputs
are never overwritten. Analysis never downloads, installs or substitutes an
edition. Cell Ranger remains excluded at the user's request.

The broader HOMER scope still includes separately tracked motif, peak/coverage
and RNA roles. This named entry alone does not close those roles or the full
250-paper implementation goal. [The validation record](homer-named-validation.json)
identifies the tested development candidate and exact evidence: 890 focused tests
in 68 suites, 890 tests against the packaged JAR on each Java 21/25, two 24-region
packaged protocols, two fresh direct original references, 16 prelaunch controls,
four admission mutation controls and four Windows host skips.
Every 19-column row and the optional complete human statistics bytes match.
Strict Java 17 compilation, Javadoc and packaging pass; the broader goal remains active.
