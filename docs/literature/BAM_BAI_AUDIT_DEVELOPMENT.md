# Native BAM/BAI audit development checkpoint

**Warning: this is a library development checkpoint. It does not qualify all
BAI producers or make the existing Sambamba workflows' collateral indexes
accepted downstream inputs. Broader coordinate, query and large-file controls
and current-tree catalog source review remain pending. No new catalog credit
or paper reproduction is claimed.**

`org.jlinalg.alignment.BamBaiAudit.audit` reads the exact BAM and BAI without
rewriting them. It checks BGZF framing and CRCs, coordinate order, referenced
virtual offsets, hierarchical bin/chunk coverage, linear offsets and present
reference/global count metadata. Its returned summary includes the SHA-256 and
actual streamed byte count of both files. These hashes establish integrity,
not producer authenticity. Resource bounds are explicit; a failed audit throws
an exception and returns no successful summary.

The first controls use the existing checksummed original Sambamba 0.6.6/0.8.2
synthetic fixture archive. Thirty of its 32 indexes pass. The two
`preexisting_flags/0.6.6` indexes (mark and remove) are refused because the last
reference's metadata extent includes the unplaced tail. The source review
traces this to BioD's unconditional metadata-end update in the original 0.6.6
dependency; 0.8.2 confines that update to placed records. The original bytes
and refusals remain retained. This observation applies to these fixtures and
source paths; it is not a claim that every 0.6.6 index is unusable.

The existing HTSJDK 5.0.0 native Java indexer produces a fresh index that passes
the same audit for the five-record affected mark fixture. No second indexing
algorithm was introduced. Additional controls reject wrong bins, interior
virtual offsets, incorrect mapped/unplaced counts, malformed/trailing bytes
and exceeded capacities. These are four new JUnit tests; they do not establish
arbitrary regional-query equivalence.

Example library invocation, with capacities chosen explicitly by the caller:

```java
import java.nio.file.Path;
import org.jlinalg.alignment.BamBaiAudit;
import org.jlinalg.alignment.SambambaStreamingBamAudit;

var summary = BamBaiAudit.audit(
    Path.of("alignment.bam"), Path.of("alignment.bai"),
    new SambambaStreamingBamAudit.Limits(
        1L << 40, 8L << 40, 1L << 40, 16 << 20, 4 << 20, 4 << 20),
    new BamBaiAudit.Limits(64L << 20, 1_000_000, 1_000_000, 120_000));
System.out.println(summary);
```

This strict profile requires a coordinate header, fewer than 16,384 references,
complete coordinates or fully unplaced records, supported raw BAM structures,
and the BAI coordinate/address range. Unsupported structures require a
separately reviewed profile; do not silently repair input data or treat an
exception as a successful analysis. This checkpoint adds no standalone CLI.

To create a separate index using the already available native command:

```text
java -jar build/cli/omicsmatic-0.4.0.jar hts-tools --engine native --method sam-index --input alignment.bam --out NEW_INDEX_DIRECTORY --no-config
```

The new index is `NEW_INDEX_DIRECTORY/alignment.bai`; the original BAM and
sidecar remain untouched. Audit the exact selected pair, retain both identities,
and explicitly configure that index in a separately qualified downstream
workflow. Do not replace an original sidecar implicitly.

The [saved checkpoint record](bai-audit-development/STATUS.json) identifies
the source snapshot, successful focused tests and retained initial compile
failure. Source-intake identities include the pinned
[SAM/BAI specification](https://github.com/samtools/hts-specs/blob/b5341fbb5181cc7811c10c741a682149ab0cccf8/SAMv1.tex),
[0.6.6 BioD dependency](https://github.com/biod/BioD/tree/b7f1db860d212ee5fb6f9adfb36c6e783aaeb6f5)
and [HTSJDK 5.0.0](https://github.com/samtools/htsjdk/tree/5.0.0).

**Skipped check and action:** the existing optional independent SAMtools 1.24
flag-statistics comparison was skipped because `JLINALG_SAMTOOLS` was unset.
Supply a permitted executable that runs on the test host, set that environment
variable to its path, and rerun `:test --tests
org.jlinalg.cli.NativeHtsCliTest.originalSamtoolsMatchesAll4096FlagCombinations`.
The native indexing and all four new BAI tests did run successfully.
