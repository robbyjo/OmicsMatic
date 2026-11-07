# ChIPseeker nearest-feature component: development

**This is the native Java component reference.** Catalog admission is recorded
separately in `omics-2023-2026/chipseeker-catalog-validation.json`. The component
and prepared-file workflows pass on three JVMs. The
separate original annotation adapter and root route are now implemented and
tested; see [the annotation development reference](CHIPSEEKER_ANNOTATION_DEVELOPMENT.md).
The workflow, paper-role and catalog evidence carry their own scoped admission.
No
released or canonical executable was changed.

`org.jlinalg.genetics.ChipSeekerNearestFeatures` implements the prepared
unstranded-peak (`sameStrand=FALSE`) part of the original nearest-feature
function. Callers provide ordered gene/transcript ranges with explicit `+` or
`-` strands. Coordinates are one-based inclusive and contig names remain exact.
There is no TxDb extraction, gene-symbol lookup, genomic feature classification,
plotting, stranded-peak workflow or whole-package/paper equivalence.

The source function is byte-identical in the four captured official source
packages: 1.22.1, 1.26.2, 1.34.1 and 1.48.1. The original reference calls use
GenomicRanges 1.64.0, IRanges 2.46.0, S4Vectors 0.50.3, GenomeInfoDb 1.48.0 and
BiocGenerics 0.58.1. Those are the present reference dependencies. The original
652-call component collection sourced unchanged R functions rather than loading
the full package. A separate full-package annotation reference is described
below. Historical dependency environments were not reproduced. The upstream
development branch has different code and is not
silently substituted for these releases.

The Java method preserves strict-flank selection, last-left/first-right
duplicate choices, the left choice on equal absolute distances, and the
reference NCList overlap order. For each contig, IRanges indexes queries when
their count is smaller than the feature count, giving the first original
matching feature. Otherwise it indexes features in start-ascending,
end-descending order, with original ordinal for identical ranges. Both the full
query population and feature order matter: subsetting a query batch can change
an arbitrary overlap assignment. `ALL` first considers complete feature-body
overlaps; TSS-point overlaps still have final priority. Source directional flags
select a physical flank; do not reinterpret them as a new gene-strand filter.
When both flags are true, the original `ignoreUpstream` precedence remains.

Full annotation reference preparation now loads each original package edition
in a separate R process using the captured official source archives. Exact
bodies and formal arguments of 15 annotation functions match those sources.
The common reference is R 4.6.1, GenomicFeatures 1.64.0, AnnotationDbi 1.74.0,
txdbmaker 1.8.0 and the recorded current dependency libraries. Source 1.48.1 is
installed directly because the official Windows binary was 1.48.0 at intake;
there is no silent binary-version substitution. The private setup's final
health annotation passes with no warnings and leaves all 29,755 pre-existing
library files unchanged. Downloads and installation were permitted for this
workspace; analysis commands do not gain permission to install elsewhere.

The small synthetic TxDb has five transcripts, 13 exons and 70 query peaks.
Its constructor's body and formal arguments match the official txdbmaker
source. Fifty configurations per ChIPseeker edition cover gene/transcript
levels, asymmetric and zero-width promoter windows, complete nearest flags,
annotation priorities, downstream windows and flanking genes. Across four
editions, **200 original annotation calls produced 152 returned results and
48 original errors**. The Java nearest component matches every retained query
ordinal, feature ordinal and signed distance: **10,488 triples on each of
Windows Java 21, Windows Java 25 and Linux Java 21**. There are also 2,208 exact
promoter-boundary checks across the original results. This extends nearest
component evidence. The subsequently implemented original annotation adapter
has separate actual-JAR controls described in the linked annotation reference;
these nearest-component checks alone do not establish its coverage.

**Original errors require attention.** Twelve configurations per edition raise
`67 elements in value to replace 69 elements`. Read the exact case IDs,
settings and error records under `annotation-source-audit-1` and each edition's
`controls/WARNINGS.md`. These errors are retained original outcomes, not empty
annotations or passing analyses. Do not drop queries or alter directions to
force a result. The original annotation adapter retains these failures and
admits no result from an affected atomic batch. The synthetic constructor also warns that chromosome genome-version
information is unavailable: synthetic metadata does not establish a real
assembly or reproduce a paper.

**Annotation editions are not interchangeable.** The original 1.22.1 and
1.26.2 sources use `precede()` for downstream annotation; 1.34.1 and 1.48.1 use
`follow()` and different downstream-window handling. All 38 returned
configurations differ between the old and new groups. The 1.22.1 and 1.26.2
results also differ for both exon-first priority configurations. The 1.34.1 and
1.48.1 captured annotation JSON outputs match for all 38 returned cases. Exact label, detail
flag and distribution-cell differences remain in
`annotation-edition-deltas-1/DELTAS.json`; none were repaired. Preserve each
requested edition when implementing genomic labels.

The raw original `geneChr` and `geneStrand` fields contain numeric codes in
these captures. Literal feature contigs, strands, duplicate feature names and
feature order are retained separately, including original GRanges RDS objects.
Do not silently turn those original fields into chromosome names or `+`/`-`
values. The Java nearest command publishes its separately declared literal
input features. Full feature labels, TxDb intake, enhancer selection rules and
paper-specific resources remain outside that native command.

Earlier setup and capture failures remain retained. The first setup stopped
on a named-scalar metadata assertion before installation; the next stopped on
R's normalized spelling of a hyphenated package version. The third installed
the runtime but selected a transcript that ChIPseeker excludes from its
gene-associated transcript population. `health-fixture-audit-1` preserves its
original -751 distance and the source-associated TSS result of zero. The final
setup uses the audited source feature population. The first full control
capture stopped while exporting duplicate feature row names; the second
preserves those names explicitly, uses unique transport row names only and
retains original ordered GRanges. No original interval, ID or result was
changed to resolve these capture issues.

Every query stays in an explicit disposition ledger:

- `MATCHED` has the original one-based feature ordinal and signed endpoint-to-TSS
  distance. It uses the endpoint selected by the source, without midpoint
  substitution.
- `NO_FEATURE` retains an original query that the source drops. Its feature
  ordinal is zero and its missing distance is `NaN`; it is not a zero-distance
  assignment. `sourceRetained()` excludes these rows in original input order.
- `BOUNDARY_DUMMY` retains the original feature ordinal `N+1` and positive
  infinite distance when a forced direction has no candidate. A dummy is not a
  gene annotation. Read the warning and resolve the direction choice before
  using it downstream.

**Unsupported integer boundary:** the original GenomicRanges implementation
computes integer `start + width`. A peak ending at 2,147,483,647 or a resized
feature TSS at that coordinate produces integer-overflow warnings and can drop
many otherwise ordinary queries. The native API throws `UnsupportedInput` for
that domain. Preserve the original warnings and rows; skip native parity
analysis without clipping or silently repairing coordinates. The official
GenomicRanges 1.64.0 function's AST matches the installed reference function.
Original overflow outputs remain retained as unsupported controls.

Wildcard feature strands, reserved `chrNA` collisions and empty queries also
remain outside the native contract. The audited original function refuses the
tested empty query and reserved-feature collision. Empty feature populations
yield an explicit no-feature ledger. There is no alias conversion or identifier
repair.

TSS flank searches use a per-contig sorted index. Complete feature-body overlap
selection currently scans its sorted feature population. Explicit population
and operation capacities prevent admitting a partial result; callers must
declare larger supported capacities when appropriate. Logical bounds are not
OS quotas or Java heap guarantees. Cancellation returns no partial annotation.

The current component candidate is
`build/literature-review/chipseeker-runtime/native-build-4/omicsmatic-0.3.7-chipseeker-nearest-development.jar`.
Every entry of its selected parent is preserved. Only the new API classes are
added. This artifact includes other development features whose regression
status remains separately scoped.

There are 652 retained original component calls: 644 returned and eight raised
original errors. The current fixture includes both ordinary and overflow
populations. All 202 component checks pass without skips on each of Windows
Java 21, Windows Java 25 and Linux Java 21: 128 complete original-output comparisons, 64
overflow-domain refusals and ten independent geometry/recovery checks. The file
workflow additionally passes nine actual analyses against original results,
six expected skips and 24 failure controls across those same three JVMs.
Twenty additional Windows Java 21 file controls match 80 original calls across
small/equal/large query populations and mixed-contig indexing branches. The
exact IRanges 2.46.0 C source independently establishes the per-contig
`query_count < feature_count` preprocessing choice.

The separate file candidate is
`build/literature-review/chipseeker-runtime/files-build-4/omicsmatic-0.3.7-chipseeker-files-development.jar`.
It adds the public `org.jlinalg.cli.ChipSeekerNearestCli` entry point and preserves
all native/scientific parent entries. That earlier file candidate has no root
route; the later `root-build-1/omicsmatic-0.3.7-chipseeker-root-development.jar`
offers `chipseeker-tools NEAREST` and `chipseeker-tools ANNOTATE` while preserving
the selected annotation/native backend bytes.

For file input, prepare literal tab-separated tables:

```text
peak_id	contig	start	end
p1	chr1	95	105
p2	chrUn	10	20
```

```text
feature_id	contig	start	end	strand
geneA	chr1	100	180	+
```

Both tables use one-based inclusive coordinates and must refer to the same
reference build. Ordinary BED uses a different start convention; do not label a
BED table as one-based or convert an already converted table again. The method
does not prepare annotation resources, filter peaks or infer assembly identity.
Duplicate biological IDs remain ordered and are distinguished by row ordinal.

Save this UTF-8 JSON as an ASCII-named `request.json` alongside `peaks.tsv` and
`features.tsv`. Paths resolve against the request file. All option values are
strings, matching the direct command flags:

```json
{
  "schema_version": 1,
  "options": {
    "input": "peaks.tsv",
    "features": "features.tsv",
    "reference-build": "GRCh38",
    "coordinates": "one-based-inclusive",
    "overlap": "TSS",
    "ignore-overlap": "false",
    "ignore-upstream": "false",
    "ignore-downstream": "false",
    "out": "new-nearest-results"
  }
}
```

```sh
java -Xmx768m -cp /absolute/path/to/chipseeker-files-development.jar \
  org.jlinalg.cli.ChipSeekerNearestCli --request /ascii/path/request.json
```

On Windows, keep the request file's own command-line path and the JAR path in an
ASCII location. Unicode input/output paths can be written inside the UTF-8 JSON.
The Windows Java 21 launcher did not preserve a tested direct Unicode filename;
that failed attempt remains retained. No file is renamed to work around it.

Read `WARNINGS.md`, `analysis-status.json`, `report.json` and `manifest.yaml`.
Require `result_admitted: true` before using the component result. Original table
bytes and any JSON request remain captured under `inputs/`. `annotations.tsv`
is the complete query ledger; `source-retained.tsv` has the source-retained
population, including explicitly labelled boundary dummies. Retained does not
mean that a row is a valid gene assignment. Raw `NaN` and `Inf` states remain
literal and are accompanied by disposition labels and warnings.

**SKIP3:** the input needs an unadmitted native contract, including wildcard
feature strands or the captured integer-overflow boundary. Skip this native
analysis; preserve the cause and original data. Do not relabel strands or clip
coordinates to force admission. **FAIL2:** malformed/changed inputs, invalid
coordinates/options, capacity, cancellation or publication failure admit no
result. Correct the named cause and retry into NEW paths. Partial result tables
are quarantined; an existing output directory is not overwritten. This native
command installs nothing and needs no R runtime. Full upstream annotation is a
separate workflow. There is no portable VERIFY command for this native file
interface; do not infer the MEME adapter's verification contract for it.

Inspect the retained records under
`build/literature-review/chipseeker-runtime/`: `source-intake-2`,
`nearest-original-3`, `fixture-intake-2`,
`integer-boundary-source-audit-1`, `native-build-4` and
`native-matrix-4`, `files-build-4`, `files-matrix-4`,
`nclist-order-source-intake-1` and `overlap-order-probe-2`.
**Earlier native builds 1–3 and file builds 1–3 are superseded for general
overlap use.** Their initial matrices passed the tested cases but did not cover
the query-indexing branch. `overlap-order-probe-1` exposed that gap before
catalog admission; the corrected candidate and additional controls are retained
separately. Earlier intake, compilation and comparison failures
remain alongside them. The initial native comparison exposed the original
overflow and NCList ordering differences; those attempts are not passing
evidence and are not deleted.

The additional full-package reference records are `reference-setup-4`,
`annotation-fixture-preflight-1`, `health-fixture-audit-1`,
`annotation-original-2`, `annotation-source-audit-1`,
`annotation-edition-deltas-1` and `txdb-native-matrix-1` beneath that same
development root. `annotation-original-1` and setup attempts 1–3 are preserved
failed/superseded evidence. These local references are not a released or
catalog-admitted ChIPseeker annotation workflow.
