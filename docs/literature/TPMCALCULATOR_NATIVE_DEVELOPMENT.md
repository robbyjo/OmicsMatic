# TPMCalculator native Java core: development status

This document records the component milestone of 15 November 2026. Consult the [catalog](omics-2023-2026/README.md) for subsequent integration status.

**At this milestone, the native core passes its source comparisons, but its public analysis workflow and catalog admission remain unfinished. This record adds no integration credit.** The catalog has 121 scoped new integrations, including 20 native Java methods. Do not describe this component record as a completed paper reproduction or an admitted command-line analysis.

The implementation is [TpmCalculator.java](../../src/main/java/org/jlinalg/sequence/TpmCalculator.java). It constructs the original gene/transcript models from GTF, streams BAM alignments through HTSJDK, assigns reads to genes/features/transcripts, and computes the original separate TPM denominators. It invokes no external counting program. It does substantially more than the existing generic `ExpressionNormalization` arithmetic helper.

## Paper and source identities

P050, *In vitro production of cat-restricted Toxoplasma pre-sexual stages*, [DOI 10.1038/s41586-023-06821-y](https://doi.org/10.1038/s41586-023-06821-y), [PMC10781626](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781626/), reports TPMCalculator for transcript quantification and normalization. The same RNA-seq paragraph separately describes Subread 2.0.1 alignment, featureCounts, and DESeq2 defaults within iDEP.96. It does **not** report the TPMCalculator version, options, exact GTF, or precise output selection. Stranded library preparation does not establish a TPMCalculator strand option. These remain unknown.

The project comparison uses [TPMCalculator tag 0.0.4](https://github.com/NLM-DIR/TPMCalculator/tree/b275fb3d749dc46e7e0efeca9bdd1c230702339d), commit `b275fb3d749dc46e7e0efeca9bdd1c230702339d`. That original executable prints **0.0.3**. Both identities are retained. They are not inferred author versions.

The complete original archive of [tag 0.0.6](https://github.com/NLM-DIR/TPMCalculator/tree/c3be63622e27b36b9aeb114860b32fe09018c57a) was also compared. Every quantification source file is byte-identical. Four archive paths differ: README, an added workflow, an added build script, and main.cpp. The main.cpp differences are its version string and final newline. **This is source equivalence evidence; a 0.0.6 executable was not run.**

The original 0.0.4 source was built without algorithm changes using GCC 7.5, BAMtools 2.5.2 and a locked Linux environment. These dependency choices are project comparison settings. The earlier GCC 15 comparator error and GCC 7 link failure without the zlib development library are retained. The successful build adds that library; it does not patch the original comparator.

## Behavior retained from the original

- Coordinates are zero-based inclusive after GTF parsing. Exon/intron sets deduplicate coordinates irrespective of feature type. Disconnected transcript groups become numbered copies of a gene ID.
- Gene IDs containing the case-sensitive substring `-AS` are excluded, as in the original. `excludedAntisenseGeneIds()` exposes the excluded IDs. Lowercase `-as` is different.
- Both mates count separately. Duplicates, secondary records and supplementary records remain eligible. Unmapped and QC-failed records are excluded. Proper-pair filtering tests the original flag bit; it does not assemble fragments. Strand does not participate in assignment.
- CIGAR `I`, `D`, `=`, and `X` contribute to the source's block span; `S`, `H`, and `P` do not. `N` splits blocks. Read-block sets compare starting coordinates only. These are the original rules, rather than HTSJDK's generic aligned blocks.
- The overlap predicate has the original boundary and uint32 behavior. A splice block that fails gene assignment can leave counts from an earlier block in place and prevent transcript processing. The bounded backwards gene scan can miss a long nested gene. The port preserves these effects.
- The original current-transcript pointer can be reused across gene IDs with a repeated transcript ID. This observed behavior is retained.
- The original GNU string-map order affects a lower_bound search when genes share coordinates. [TpmCalculatorOrder.java](../../src/main/java/org/jlinalg/sequence/TpmCalculatorOrder.java) reproduces the selected 64-bit GNU comparison profile, including hashing and rehash order. It is not a general C++ container implementation. The GNU source references and runtime-library exception accompany the validation sources.
- Each sample is normalized immediately after counting it. Processed flags persist across samples. Missing data and present zero-valued data remain distinguishable in the result records.
- Bridge TPM is stored in **single precision** by the original, although its getter returns double. Other TPMs use double. This distinction is retained.

**Nonfinite values are retained.** Original overlapping-exon and zero-length derived-feature behavior can yield `NaN` or infinity. The native result keeps those values, including the unusual unsigned coordinates that produced them. It does not replace them with zero, drop the feature, or change an annotation silently. Review the original inputs and conditions before interpreting them. A changed annotation is a new analysis.

The parser requires positive decimal signed-int input coordinates and quoted gene/transcript attributes. Invalid, inverted, zero or overflowing input coordinates fail explicitly. It does not emulate every permissive or undefined `atoi` case. Original signed counter/CIGAR arithmetic overflow also fails rather than publishing wrapped counts. A mapped read on an annotation chromosome with no constructed genes fails because the original empty-set search is undefined. These are declared input/error boundaries.

## What the comparisons establish

There are 57 complete original source cases: 20 named cases, 30 seeded randomized cases, and seven annotation edge cases. They cover filtering flags, proper pairs, splice-prefix mutation, indels/clips/padding, cutoffs, alternative transcripts, gene copies, antisense exclusions, equal-coordinate genes, long nested genes, multiple samples/chromosomes, empty and fully filtered BAMs, repeated transcript IDs, origin coordinates, and overlapping/adjacent/single-base exons.

For every case, the unchanged original executable's five tables per sample are byte-equal to tables written by a transparent reader linked to the unchanged original object files. That reader also exports full annotation lengths, strands, processed/data-presence flags, all seven counters and all TPM fields at 17-digit precision. The native API is compared against that complete state. Integer values and inventories must match exactly, nonfinite classifications must match, and finite values have a 64-ULP tolerance for floating reductions.

A separate actual GNU runtime run supplies **100,000** string hashes and the full resulting iteration order, including repeated rehashes and UTF-8 keys. The Java profile matches them exactly.

The 57 cases, source-binding check and map-profile check form **59 passing tests on Windows and Linux**. The Linux run loads the packaged library JAR and HTSJDK. It uses no original quantification executable for the Java calculation.

**Observed Linux runtime message:** HTSJDK reports that its optional libdeflate class is unavailable and falls back to JDK compression/decompression. The complete message and stack are retained. All 59 tests pass with that fallback; no installation is needed for those computations. Normal packaging should supply the complete declared Java runtime classpath. Optional acceleration is not a prerequisite for these validated computations.

Two failed native comparisons remain retained with the exact earlier Java/test sources: the initial bridge-precision/map-order mismatch and a later signed-overlap mismatch for an origin-overlapping-exon model. Their assertions were resolved by matching the original behavior. No tolerance was widened to hide them.

The [development qualification record](tpmcalculator-native-development/QUALIFICATION.json) binds the current component, source profiles, complete fixtures and actual test records. It is a **component record**, not a public workflow qualification. It does not prove biological accuracy, broad input compatibility, source authorship, a whole-tool validation, or the paper's historical results.

## Using and checking the component

The API is available for development:

```java
import org.jlinalg.sequence.TpmCalculator;
import java.nio.file.Path;
import java.util.List;

var options = TpmCalculator.Options.defaults();
var model = TpmCalculator.readAnnotation(Path.of("annotation.gtf"), options);
var result = model.quantify(List.of(
    new TpmCalculator.Sample("sample1", Path.of("sample1.bam"))));
// Inspect result.samples(), processed/dataPresent flags and all original metrics.
```

Run the focused checks from the project checkout:

```powershell
.\gradlew.bat test --tests org.jlinalg.cli.TpmCalculatorOriginalTest --tests org.jlinalg.sequence.TpmCalculatorOrderTest jar --no-daemon --console=plain
```

These tests use complete saved synthetic controls. They do not install or run TPMCalculator. The original control producers are under [tools/literature/tpmcalculator_validation](../../tools/literature/tpmcalculator_validation/). Fresh original comparisons require the recorded permitted original source/compiler/library environment; the original source archive and build records are separate from the native runtime. Do not substitute another source edition or quietly omit a prerequisite.

Still required before catalog integration: the native command-line and file-output workflow, complete input/result provenance, verification/replay and failure recording, clear runtime warnings/recovery, further caller-option/error checks, packaged workflow checks on both systems, and the source-bound P050 catalog review. No new TPMCalculator integration credit is assigned until those are completed.
