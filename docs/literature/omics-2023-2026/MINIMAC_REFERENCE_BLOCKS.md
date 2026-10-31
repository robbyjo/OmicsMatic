# Legacy and current compressed-reference blocks

**The project Minimac imputation command remains unfinished.**
`MinimacReferenceBlocks` is a native Java complete-reference reader. It supports
text M3VCF1.2, M3VCFv2.0 and complete MVCFv3.0 exports, including upstream's
M3VCFv3.0 subformat alias. Binary MSAV requires an original complete Savvy export.
The [runtime backend](MINIMAC_RUNTIME_STATUS.md) is validated separately.

## Errors, warnings and required actions

**Select the boundary policy explicitly.** Legacy files can repeat the last
marker of one block as the first marker of the next. The Java reader verifies
the key and every ordered haplotype at that boundary. `VERIFY_SHARED_KEEP_LATER`
then selects the later row, matching current Minimac's append rule. It retains
both original rows, IDs, raw INFO and model parameters through visitor callbacks.
`REQUIRE_SHARED_KEEP_LATER` also rejects disjoint adjacent blocks on one contig;
the legacy Minimac3 reader assumes their boundaries overlap.
`REJECT_DUPLICATES` refuses shared rows as well as other duplicate keys.
Conflicting boundary haplotypes and repeated nonboundary keys always fail.
Required action: choose the policy for the declared consumer and inspect all
boundary identity/parameter changes. No duplicate is silently discarded.

**Reject missing or unsafe version-2 delta lists.** Its ALT representatives are
encoded as cumulative unsigned deltas, with the first delta allowed to be zero.
Later deltas must be positive and every cumulative index must remain within
REPS. The pinned current original reader checks individual deltas but not their
cumulative sum; malformed input can write outside its representative array.
It also parses an empty or nonnumeric first token as zero, inventing an ALT
copy. Java rejects these inputs before invoking that converter. The tested
v2 reader provides no safe empty-list representation for an all-reference row.
Required action: preserve the original file, report the failing row, and obtain
a documented valid encoding or a compatible v1/current reference. Do not remove
markers, invent ALT copies or repair a production panel automatically.

**Preserve column identities and phase.** M3VCF1.2 columns represent individual
haplotypes, without an inferred person/copy relationship. Version2 has exactly
two ordered mapping indices per sample. MVCF permits one or two explicit copies
per column. Per-contig ploidies must stay constant. The complete MVCF AC/AN must
match the actual expanded copies; the previously observed padded-AN error is
still rejected. Required action: supply an explicit provenance ledger when
reconciling haplotype columns to original people. No suffix, sex, assembly,
allele harmonization or PAR boundary is inferred.

**Keep original exports complete and traceable.** Use original
`sav export --update-info never -O vcf reference.msav`, require exit zero and
retain original/export hashes and diagnostics. Conversion preserves physical
overlap rows; an exported file can therefore contain more rows than its unique
marker count. Current original M3 conversion rejected the tested plain text
with exit1 and no reference, while lossless deterministic gzip of the same bytes
passed. Required action: retain the plain source and gzip derivation identities
when preparing converter input. Native plain-text acceptance does not establish
original-runtime compatibility. If the exporter/runtime cannot be installed or
run, **skip that analysis**, state its cause and provide local runtime/export
recovery. The Java API never installs software. Cell Ranger remains excluded.

**Historical reference-estimation probes failed.** Reference-only `--estimate`
probes on the admitted Min4 1.0.0 and 1.0.2 profiles returned255 with an empty
GWAS-chromosome mismatch and no output reference. Their separately validated
imputation workflows still pass. Required action: do not treat this tested
preparation route as available; retain the original logs and use a separately
validated reference-preparation workflow. No scientific parameter was silently
changed to force a successful output.

## Native contracts

The streaming reader checks complete headers, declared counts, mapping bounds,
ordered contigs/positions, block widths/extents and finite optional model
parameters. M3 block endpoints name first/last variant positions; MVCF END also
includes allele-string extent. M3 ERR/Err and RECOM/Recom aliases are checked;
CM text in legacy M3 is preserved but is not claimed consumed by its original
reader. MVCF CM is audited as a model parameter. Unknown INFO values and flags
remain available. Alleles are complete opaque single-ALT tokens: symbolic
labels are counted separately, without normalization or structural-variant
interpretation. Missing/polyploid reference mappings fail.

Plain/gzip/BGZF UTF-8, concatenated gzip members, CRLF and an absent final newline
are handled explicitly. Invalid UTF-8, controls, oversized lines and truncated
blocks fail with source line context. Metadata, columns, representatives,
variants per block and contigs have documented bounds in the source. Visitor
outputs are provisional until the entire read succeeds; discard or quarantine
them on any later error. Returned collections are immutable.

Three fingerprints have different scopes: identity/phase includes original
column kind/names and each selected ID/copy; ordered matrix excludes names,
grouping and IDs; model parameters include missingness and the original
binary32 values, with signed zeros merged. Matrix equality alone does not prove
sample identity. Preserve source bytes and explicit identity ledgers.

## Validation

The exact development candidate, test counts and proof hashes are recorded in
[`minimac-blocks-validation.json`](minimac-blocks-validation.json). The reader
matches all 610,000 ordered alleles in the original developer fixture's 5,000
markers/61 diploid samples. Its 368 blocks contain 5,367 physical variant rows and
367 shared boundaries. A fixture-specific column/copy ledger is checked against
the original VCF header; production admission does not infer those pairs.

Current original Minimac4 4.1.6 and Savvy2.1.0 convert and completely export the
full v1 fixture. Every selected ID, allele and ordered haplotype matches; block
counts, boundary counts and binary32 model fingerprints agree.

A separate **explicitly derived projection** tests v2. It excludes 1,793 physical
all-reference rows representing 1,569 unique markers, whose empty ALT lists cannot
be safely encoded for that reader. The original file is unchanged. The retained
subset has 3,431 unique markers, 3,574 physical rows, 143 shared boundaries and
418,582 ordered haplotype alleles. Included/excluded rows and sample/copy mapping
are individually recorded. Equivalent v1 and v2 projections both pass the
original converter and complete exporter, retaining selected IDs, ordered
alleles and model values. This is a subset interoperability check, **not** an
all-marker v1/v2 equivalence or an approved production filtering policy.

The candidate passes 504 focused tests in 37 suites, including 33 new reader
tests, plus all 504 tests against exact packaged classes on each Java 21/25,
with no skips. Strict Java 17 compilation, Javadoc and executable packaging pass.
Twenty-one packaged reader audits on Windows Java21/25 and Linux Java21 cover
8,700,264 ordered haplotype alleles, with equal decoded summaries and identical
selected-row tables. Six example policy runs pass and three explicit duplicate
policies reject the shared-boundary example. All 15,086 prior packaged class
entries remain byte-identical; only the new reader classes are added.

The runnable [example](../../../examples/native-minimac-reference/README.md)
demonstrates a changed boundary ID/model with equal phase. Tests also cover
unsafe deltas, contradictory counts, truncation, alias conflicts, ploidy,
ordering, model missingness, representative permutations and file cleanup.
These checks do not establish imputation accuracy, reference suitability,
server/paper parity or a complete project imputation command.
