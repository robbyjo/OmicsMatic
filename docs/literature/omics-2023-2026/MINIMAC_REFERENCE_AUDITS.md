# Native Minimac reference audits and CSI indexing

**The project imputation adapter remains missing.** Native reference inspection
and indexing are available as Java APIs. They do not run an imputation HMM,
admit a production Minimac runtime or establish historical/server/paper parity.
See the [runnable example](../../../examples/native-minimac-reference/README.md),
[source-role review](MINIMAC_SOURCE_REVIEW.md) and
[validation record](minimac-reference-validation.json).

For source-aware legacy M3VCF1.2/v2.0 and complete MVCF with shared boundaries,
see the newer [`MinimacReferenceBlocks` reader](MINIMAC_REFERENCE_BLOCKS.md).
It requires an explicit boundary policy, verifies every ordered boundary allele
and retains both original rows and any ID/model changes. The older strict
`MinimacReferenceAudit` contract and its historical validation below remain
available. [Runtime admission/backend](MINIMAC_RUNTIME_STATUS.md) is now validated
separately; the typed user imputation command remains unfinished.

## Warnings and required actions

**Reject inconsistent mixed-ploidy reference output.** Original Minimac4 4.1.6
completed compression with exit zero for a synthetic reference containing eight
haploid and sixteen diploid samples on X. Savvy 2.1.0 exported 40 actual
haplotype mappings but `AN=48` on all 350 X variants. Independently expanded AC
was correct. The upstream serializer writes AN from the size of the strided
mapping vector, which includes end-of-vector padding. Java rejects the AC/AN
inconsistency. Preserve the original reference and diagnostics; skip this
reference until an explicitly documented, independently validated preparation
is available. Do not replace AN silently or change reference-panel membership
to evade the error. Separately prepared haploid and diploid fixtures pass;
their success does not certify mixed-ploidy reference preparation.

**Preserve converted haplotype identities.** In the observed Minimac3 2.0.1 to
current Minimac4 conversion, 24 diploid input samples become 48 named haplotype
columns (`REF1_HAP_1`, `REF1_HAP_2`, etc.). All 5,760 alleles match an explicit
fixture-specific sample/copy ledger. These columns are not 48 independent
diploid people. Require a supplied provenance ledger when reconciling original
sample identities. The Java reader preserves the exported names and ploidies;
it never infers an identity relationship from a name suffix. Reference IDs,
parameter estimates and posterior differences documented in the source review
remain distinct.

**Audit complete exports.** Use the original exporter with
`sav export --update-info never -O vcf reference.msav`; require exit zero and
retain the binary, input, output and diagnostic identities. This exports the
existing blocks without recompression. Do not pass a coordinate-cropped export
to the complete-block reader: cropping can omit rows while retaining original
block counts. Original Savvy 2.1.0 also omits single-base queries and certain
left bin-boundary records with both native and HTSlib CSI indices. Source
inspection shows one-based region bounds passed unchanged to the zero-based
CSI lookup. Retain this reader finding; do not alter CSI bin geometry or assume
an empty original query means no variants. The diagnostic comparison pads the
lookup start and independently filters requested one-based inclusive overlaps;
that diagnostic is not a production region-export policy.

**Report missing dependencies clearly.** If the original exporter cannot be
installed or run, name that dependency and cause, skip MSAV inspection, and
state that a supported local exporter or a complete traceable export is needed.
The native APIs never install software. CSI creation requires only Java and the
project's bundled HTSJDK. Cell Ranger stays excluded at the user's request.

## Native contracts

`MinimacReferenceAudit` accepts plain UTF-8 MVCFv3.0 exports with explicit full
phasing. It streams immutable compressed rows and checks exact sample order,
haploid/diploid UHM mappings, complete biallelic UHA values, expanded AC/AN,
block variant counts and allele extents, sorted contiguous contigs, duplicate
variant keys, per-contig sample ploidy and finite optional ERR/RECOM/CM. ERR and
RECOM must be probabilities. Missing parameters remain absent. Symbolic or
multiallelic variants, legacy M3VCF text, missing/polyploid reference copies,
within-contig ploidy changes and duplicate block-boundary variants fail with a
line-numbered error. No allele normalization, deduplication, sex, build, panel
or phasing inference is performed. A streaming visitor's results are provisional
until the entire audit succeeds.

The canonical SHA-256 covers ordered samples, contig ploidies, variant
identities/alleles and expanded ordered haplotypes. It is invariant to equivalent
representative renumbering and compression block layout. It excludes HMM
parameters and other metadata; preserve original file hashes for those details.

`VcfCsiIndex` uses HTSJDK's bin/chunk builder with native BGZF CSI serialization
at minimum shift 14/depth 5. Input must be sorted, complete BGZF VCF with its EOF
terminator and variant extents within 1..536,870,912. The input is preserved; an
existing destination is refused. An index is published only after a complete
write. Streams close on invalid input as well as success. The final chunk stops
before the empty BGZF EOF block: including that terminator caused an original
Minimac target-loading failure in the retained probe. This is an index contract,
without genotype or allele admission. Regenerate the index after compressed
input bytes change; use another explicitly supported indexer for larger
coordinates rather than truncating them. The serialization follows the
[official CSI specification](https://samtools.github.io/hts-specs/CSIv1.pdf).

## Validation scope

The development candidate passes 471 focused tests in 36 suites, strict Java 17
compilation, Javadoc and executable packaging. Independently compiled tests
against the exact packaged classes pass all 471 tests on each Java 21 and 25,
with no skips. All 15,071 prior class entries are byte-identical to the previous
candidate; no prior implementation was replaced.

Fifteen native audits on Windows Java21/25 and Linux Java21 compare complete
original exported references against immutable phased input calls or the
explicit conversion ledger. Across these runs, 4,920 variant records cover
101,520 genotype cells and 177,360 ordered haplotype entries. Tables match across
hosts. The three original mixed-ploidy failure controls reject inconsistent AN
without producing a final audit table. Three public example runs also pass.

A 47,877-variant fixture spans three contigs, multiple BGZF blocks, bin boundaries,
deletions, empty ranges and the maximum admitted coordinate. Sixty regions have
240 original-reader comparisons: native and HTSlib indices both match independent
overlap expectations through BCFtools 1.24; both indices match through original
Savvy with the explicit diagnostic padding/filter described above. The exact
packaged index is byte-identical to the tested index. Original Minimac4 then
imputes 120 variants/six targets with a packaged-native CSI; all decoded output
records equal the prior original-reference run.

These are synthetic conversion, inspection and interoperability checks. They
do not establish real-data accuracy, reference-panel quality, mixed-ploidy
production support or completed project imputation. The catalog remains at 250
papers, 379 tools, 2,428 observations, 671 reviewed roles and 197 supporting
pointers; 270 tool integrations and broader semantic review remain open.

The original programs are from the developers' official releases:
[Minimac4 4.1.6](https://github.com/statgen/Minimac4/releases/tag/v4.1.6) and
[Savvy 2.1.0](https://github.com/statgen/savvy/releases/tag/v2.1.0).
Pinned source/binary/license hashes and retained failed attempts are recorded in
the validation proof bundle. Savvy's original MPL 2.0 notice is retained; this
milestone does not claim a managed production runtime installation.
