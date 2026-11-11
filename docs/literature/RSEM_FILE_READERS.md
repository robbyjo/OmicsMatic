# RSEM native file readers

The current source adds Java readers for ordinary RSEM reference, count, model and theta files. These are library APIs. **The public RSEM quantification adapter and runtime installer remain unfinished. RSEM still counts as missing in the toolbox inventory.** The separate [native result-pair audit](RSEM_RESULT_AUDIT.md) now checks complete ordinary gene/isoform tables. See [RSEM development status](RSEM_DEVELOPMENT.md) for the native abundance methods and original-tool development protocols.

**Count-admission correction:** original `nIsoMulti` counts every group with more than one alignment, including cross-gene mappings and duplicate hits. It is not a subset of uniquely mapped genes. The earlier file-reader validation is historical and superseded for current count admission; use the [source-verified alignment/count milestone](RSEM_ALIGNMENT_ADMISSION.md) and its current validation record.

`RsemReference` reads complete `.ti`, `.seq` and `.grp` files, plus `.chrlist` for genome-derived references. It checks transcript order, exact identifiers, gene groups, exon coordinates, sequence lengths, poly-A tails and mask words. Commas, transcript versions and Unicode names retain their original meaning. Allele-specific reference type 2 is explicitly unsupported. Other prepared-reference files and aligner indexes are outside this reader's scope.

`RsemCounts` checks every `.cnt` header and histogram row using exact integer arithmetic. It checks group totals and weighted alignment-hit totals without converting fragments into individual reads. `RsemModel` consumes every stored scalar of all four ordinary model types and both theta vectors. It retains optional read-length fields, estimated start distributions, complete quality/error/noise matrices, mappability weights and legitimate unused zero rows.

`RsemPrintedNumber` retains the original numeric token and conservative bounds from its declared output precision. A rounded value is not a recovered unrounded model parameter. The bounds check serialization compatibility; they do not certify a unique `printf` output, biological uncertainty, convergence or the correctness of an upstream fit.

The readers require explicit capacities before allocation, strict UTF-8 and LF internal files. Truncation, trailing data, invalid dimensions and inconsistent populations fail with diagnostics. Every successfully consumed file has its path, byte count and SHA-256 retained. Do not rewrite line endings to get a rejected file through admission. These checks do not yet establish a protected runtime/reference snapshot or trust in an arbitrary installation.

## Calling the library

The following capacities are an illustrative small-workflow budget. Review and declare capacities appropriate to the complete intended dataset; exceeding one rejects the input instead of silently dropping rows.

```java
import java.nio.file.Path;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemCounts;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;

var referenceLimits = new RsemReference.Limits(
        1_000, 1_000, 1_000_000, 10_000, 5_000_000, 1_000_000);
var modelLimits = new RsemModel.Limits(
        1_000, 100_000, 100_000, 100_000,
        1_000_000, 5_000_000, 1_000_000);
var reference = RsemReference.read(Path.of("work/reference"), referenceLimits);
var counts = RsemCounts.read(Path.of("work/sample.stat/sample.cnt"),
        1_000_000, 10_000_000, 100_000, 5_000_000, 1_000_000);
var fit = RsemModel.readFitState(
        Path.of("work/sample.stat/sample.model"),
        Path.of("work/sample.stat/sample.theta"), counts,
        reference.transcripts().size(), RsemAbundance.Release.V1_3_3,
        modelLimits);
```

Select the release actually used. With no alignable groups, original 1.3.2/1.3.3 write empty model and theta files. The count-bound reader represents that state as `learned() == false` with no invented model or theta. Empty files are rejected when alignable groups exist. Releases through 1.3.1 cannot complete the original no-alignment result path. Parsing these files does not validate the paired gene/isoform result tables.

## Validation and provenance

The historical file-reader milestone passed 55 focused tests: 22 abundance tests, 22 reference/model/count-admission tests and 11 printed-number tests. The later result-audit milestone expanded that suite to 89. The current [alignment/count milestone](RSEM_ALIGNMENT_ADMISSION.md) passes all 111 focused tests against the exact built JAR on Windows Java 21, Windows Java 25 and Linux Java 21, with zero skips, strict Java 17 compilation and private Javadoc checks. Each JVM also runs the existing 668 original-function abundance vectors. The current count correction and its fresh source evidence supersede the earlier count-admission invariant; historical validation records retain their original sources and results.

The checked-in `src/test/resources/org/jlinalg/sequence/rsem-reference/reference-model-fixtures.zip` contains 305 original-tool output files and its own `SHA256SUMS`. Tests verify every checksum before use. The synthetic fixtures cover 15 references, 40 learned models with both theta vectors, and two explicit no-alignment empty states across original releases 1.2.28, 1.3.0, 1.3.1, 1.3.2 and 1.3.3. They include genome/GTF names, comma identifiers, a singleton exon, strand, poly-A masks, all four model types and the single-end optional-length branches. These are method checks, not biological or whole-paper reproduction.

Pinned release commits, fixture hashes, tested source hashes and the local validation receipt are recorded in [RSEM_FILE_READERS_VALIDATION.json](RSEM_FILE_READERS_VALIDATION.json). Earlier failed protocols and incomplete result-parser work remain in the private development history. The broader RSEM workflow and 250-paper implementation goal remain open.
