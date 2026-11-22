# Native compressed-reference audit and VCF indexing

**The project Minimac imputation adapter is still under development.** These
Java helpers inspect supplied reference text and create CSI indices. They do
not impute missing genotypes or acquire reference panels.

For legacy M3VCF or references with shared block boundaries, use
`MinimacBlocksExample` with an **explicit** boundary policy:

```sh
javac -cp /path/to/omicsmatic.jar MinimacBlocksExample.java
java -cp .:/path/to/omicsmatic.jar MinimacBlocksExample legacy-shared.m3vcf VERIFY_SHARED_KEEP_LATER
```

On Windows use `;` in the classpath. The supplied `legacy-shared.m3vcf` is a
handwritten synthetic two-block example, containing no participant data. Its
four physical rows represent three selected markers. At position20 the blocks
encode equal ordered haplotypes through different representative indices; the
later row changes the ID and model parameters. Both physical rows remain in
the API's visitor ledger. `REJECT_DUPLICATES` rejects this file, while
`REQUIRE_SHARED_KEEP_LATER` additionally requires adjacent same-contig blocks
to overlap, for a legacy Minimac3 consumer. Choose the policy for the intended
consumer; the example does not infer it.

`MinimacReferenceBlocks` reads plain/gzip/BGZF M3VCF1.2, M3VCFv2.0 and complete
MVCFv3.0 exports. **Version-2 missing/empty ALT-index lists are rejected** rather
than decoded as an invented ALT allele at representative0. Unsafe cumulative
indices fail before the original converter is invoked. The observed current
converter requires gzip M3 input even though the native text reader supports
plain text. Retain source bytes and any explicit lossless gzip staging step.
For full format contracts, conversion evidence and required recovery, read
[`MINIMAC_REFERENCE_BLOCKS.md`](../../docs/literature/omics-2023-2026/MINIMAC_REFERENCE_BLOCKS.md).

An existing MSAV reference can be exported by the original Savvy 2.1.0 program:

```sh
/path/to/sav export --update-info never -O vcf reference.msav > reference.mvcf
```

Check that the exporter exited successfully before admitting its output. Keep
the original MSAV, exporter version/hash, diagnostics and export hash. This is
an export of the existing compression blocks, without refitting or recompressing
the reference. `--update-info never` preserves the original AC/AN fields for the
independent Java audit.

The included `reference.mvcf` is a complete unmodified export of the project's
synthetic original-Minimac4 4.1.6 reference: 24 diploid samples and 120 variants.
It contains no participant data. It was exported by original Savvy 2.1.0 with
`--update-info never`; its source hashes and original-call comparison are in
`docs/literature/omics-2023-2026/minimac-reference-validation.json`. The example
can run directly on this supplied file.

Compile the example against the executable project JAR using Java 17 or later:

```sh
javac -cp /path/to/omicsmatic.jar MinimacReferenceExample.java
java -cp .:/path/to/omicsmatic.jar MinimacReferenceExample reference.mvcf
```

On Windows use `;` in place of the classpath `:`. The optional final arguments
create a **new** target index:

```sh
java -cp .:/path/to/omicsmatic.jar MinimacReferenceExample reference.mvcf target.vcf.gz target.vcf.gz.csi
```

`MinimacReferenceAudit` streams MVCFv3.0 text, checks full phasing, sample IDs,
complete haploid/diploid UHM mappings, UHA alleles, exact expanded AC/AN, block
extents/counts, variant order/uniqueness, per-contig sample ploidies and finite
optional HMM parameters. Missing parameters remain absent. The content hash
covers sample order, contig ploidies, variant IDs/alleles and ordered expanded
haplotypes; it excludes compression layout and HMM parameters. Preserve the
original file hashes to identify those omitted details. A `Visitor` can inspect
individual immutable rows; its output stays provisional until the complete
audit succeeds.

**Required action on an error:** inspect the exception, original exporter log
and input files; do not use a partial export or a partial visitor result. Supply
literal, complete biallelic alleles and explicitly split regions when a sample's
ploidy changes within a contig, including PAR/non-PAR where applicable. Assembly,
panel identity, sex, strand and allele harmonization are not inferred. Legacy
M3VCF, binary MSAV, polyploid/missing reference copies, symbolic/multiallelic
alleles and duplicate block-boundary variants require separate explicit handling.
Do not silently deduplicate them to pass this reader.

Original Minimac4 produced an inconsistent `AN=48` for 40 actual haplotypes in
the tested mixed-ploidy X reference. This reader rejects that output. Skip such
a reference until a documented, independently validated preparation is available;
do not silently replace AN or alter panel membership. Original Minimac3 conversion
also exposed separate haplotype sample names. Reconcile them through an explicit
source-sample/copy ledger, without guessing identity from a suffix. Use a complete
export: coordinate filtering can omit block rows, and original Savvy 2.1.0 has
observed single-base/left-bin-boundary query omissions with both Java and HTSlib
CSI. See `MINIMAC_REFERENCE_AUDITS.md` for evidence and recovery.

`VcfCsiIndex` uses HTSJDK's bin/chunk construction and native CSI serialization
(minimum shift 14, depth 5). It requires sorted, complete BGZF VCF with an EOF
terminator and variant extents within 1..536,870,912. It preserves the input,
rejects an existing destination and publishes the index only after successful
writing. This is indexing, without genotype or allele validation. Regenerate
the index whenever the compressed VCF bytes change. Files outside these bounds
need another supported indexer or explicitly prepared inputs; do not truncate
coordinates.

**If Savvy cannot be installed or run in your environment, skip the MSAV
inspection analysis and report that dependency failure prominently.** No
installation is attempted by these Java APIs. VCF indexing itself needs no
Savvy, Minimac or BCFtools runtime. The eventual managed imputation workflow and
runtime admission are described separately in
[`MINIMAC_RUNTIME_STATUS.md`](../../docs/literature/omics-2023-2026/MINIMAC_RUNTIME_STATUS.md).
The runtime backend is validated; the typed imputation command remains unfinished.
