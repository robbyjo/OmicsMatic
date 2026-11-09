# Rsubread result values: warnings and required actions

**Development component, not a complete Rsubread analysis adapter.** The catalog remains at 89 new scoped integrations (17 native, 72 upstream), 92 including existing adapters, and 252 missing tools. Rsubread remains missing. These counts describe the 2026-11-09 checkpoint; the overall 250-paper implementation goal is unfinished. Cell Ranger remains excluded at the user's request.

Use this Java API to inspect already exported original results. It does not run featureCounts, count alignments, normalize expression, perform DESeq2 analysis, or admit a scientific analysis. Original execution, runtime binding, supervised process lifetime, cancellation and atomic batch admission remain unfinished. Do not treat a readable table or successful structural check as a reproduced paper.

If R execution or required installation is forbidden or unavailable, **skip the dependent original counting analysis** and explain that a permitted R runtime and the explicitly selected original package are required. Exporting an existing RDS requires R but does not load or install Rsubread. Reading previously exported values in Java requires only JDK 17 or newer.

**On any malformed, unsupported or over-capacity result, preserve the original inputs, complete RDS and diagnostics; skip dependent interpretation.** Correct the stated cause and retry a NEW export directory. Never rename, merge, reorder, round, replace missing values, or silently select a different package edition to make a result pass. A failed export may retain partial files; those files are not admitted results.

## What is implemented

[RsubreadDataFrame.java](/D:/projects/JLinAlg/src/main/java/org/jlinalg/sequence/RsubreadDataFrame.java) retains character, integer, double and logical storage; every scalar; column and expanded row names; automatic versus explicit row-name mode; duplicate/empty/missing column names; and original positions. Character NA, literal `NA`, and empty strings are distinct. Integer/logical NA remains the original int32 sentinel. Double storage retains raw IEEE-754 bits, including the original R NA payload, NaN, infinities and negative zero. Numeric arrays are copied; returned lists are immutable.

[RsubreadCountResult.java](/D:/projects/JLinAlg/src/main/java/org/jlinalg/sequence/RsubreadCountResult.java) checks the complete counts/annotation/targets/stat/optional-junction relationships for the source-checked 2.26.0 and selected 2.23.2 reference layouts. It preserves feature-level duplicate gene rows and extra annotation fields. It checks each stored interval, computes annotation union lengths **separately by chromosome and strand**, verifies all 14 summary categories and positional sample mappings, and accepts the original 14-metadata-column junction layout, including zero-row logical columns.

These are value and relationship checks, not independent read assignment or independent junction discovery/support counting. The union calculation checks the intervals in the returned annotation; it does not establish that an original GTF/SAF was correctly parsed. Junction classifications, genes, transcripts, donor/acceptor labels, strand and nearest-boundary fields remain literal upstream observations. The API does not independently establish their biological correctness.

GeneID storage supports character, integer and logical values, including missing values. Double GeneID storage is explicitly unsupported because reproducing R's textual conversion needs separate validation. All four scalar storage kinds remain available in the generic frame API. Factor/list columns, column attributes, additional data-frame classes and unrecognized result members are rejected by the R exporter instead of being flattened.

The wire is not complete R object serialization: R attributes, source character-encoding tags and internal compact row-name representation are not all reproduced. **Retain the complete original RDS** for object identity and provenance. The exporter checks original matrix/annotation and target relationships before writing.

## Export an existing original result

Use the checked-in helper; do not run an author script downloaded from a paper as part of this export:

```r
source("src/main/resources/org/jlinalg/omics/rsubread_result_wire.R")
original <- readRDS("complete-original-result.rds")
rsubread_write_result(original, "NEW-result-values")
```

The new directory contains `counts.matrix`, `annotation.frame`, `targets.frame`, `stat.frame`, optional `counts_junction.frame`, and the original member order in `members.txt`. Paths, target strings, names and numeric values are preserved by position. Repeated basenames can make upstream return full original input paths as targets. Do not replace them with sample labels; any subsequent rename needs a separate explicit mapping.

## Read the values in Java

Use [rsubread-result-values-api.jar](rsubread-result-development/rsubread-result-values-api.jar). The JAR includes the three native APIs and the optional R exporter, with no dependency on R, the canonical executable, HTSJDK or an earlier development JAR for Java reads.

Save this as `InspectRsubread.java`:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.jlinalg.sequence.*;

class InspectRsubread {
    static RsubreadDataFrame frame(Path directory, String name) throws Exception {
        try (var input = Files.newBufferedReader(directory.resolve(name + ".frame"),
                StandardCharsets.US_ASCII)) {
            return RsubreadDataFrame.readWire(input, RsubreadDataFrame.Limits.defaults());
        }
    }
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        RsubreadCountMatrix counts;
        try (var input = Files.newBufferedReader(directory.resolve("counts.matrix"),
                StandardCharsets.US_ASCII)) {
            counts = RsubreadCountMatrix.readWire(input, RsubreadCountMatrix.Limits.defaults());
        }
        var result = new RsubreadCountResult(counts, frame(directory, "annotation"),
                frame(directory, "targets").column(0).characters(), frame(directory, "stat"),
                Files.exists(directory.resolve("counts_junction.frame"))
                    ? frame(directory, "counts_junction") : null);
        System.out.println(result.counts().rows() + " rows, " + result.targets().size() + " libraries");
    }
}
```

```powershell
java -Xmx512m --class-path docs/literature/rsubread-result-development/rsubread-result-values-api.jar InspectRsubread.java NEW-result-values
```

Readers require canonical ASCII/LF, complete populations and EOF. Defaults per frame are 1,000,000 rows, 10,000 columns, 25,000,000 cells, 1 MiB per text value and 64 MiB total UTF-8 text. Matrix limits are described in [the matrix guide](RSUBREAD_MATRIX_COMPONENT.md). Limits are not a heap reservation or a hard aggregate memory bound: several tables and matrices coexist. Increase explicit capacities and Java heap only when resources permit. Do not truncate a result.

## Validation and reference limits

The evidence covers 32 retained original count lists across Rsubread 2.26.0/R 4.6.1 and the selected unchanged 2.23.2 source/R 4.4.1, plus 18 fresh original controls. All native value/relationship checks passed on Windows Java 21/25 and Linux Java 21 with a 128 MiB heap. Original annotation, summary, junction and target values from the 32 retained results were compared back in R after native round trips. Six scalar-table controls exercise nonfinite values, R NA payloads, signed zero, non-ASCII/tab/newline text, duplicate/missing names and empty dimensions. Each reference family has 37 malformed/capacity/result rejection controls per JVM; fresh controls add four per family/JVM.

Fresh controls test strand/chromosome union length, integer and logical GeneID coercion, empty junctions, additional GTF attributes and BAM input. A fixture encoder verifies every decoded SAM/BAM record field and observation order; eight separate complete original SAM-versus-BAM comparisons preserve annotation, counts, summary and junction fields with explicit target ledgers. HTSJDK is used only for fixture encoding; it is not a dependency of this value API.

The JDK 21 and 25 builds produce the same complete API JAR; all six prior matrix classes remain byte-identical. The canonical executable is unchanged.

The selected 2.23.2 commit is `666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6`. There are 22 recovered upstream commits with that version label. The paper binary and commit remain unidentified; upstream development warnings and earlier failed build attempts remain retained in the matrix evidence. Do not infer the paper runtime from a version label alone.

The paper-linked P133 ATAC code at immutable author commit `3aeb503348e52b1f0b88d47839427380250c0ea5` confirms `Rsubread::featureCounts`, a SAF-style data frame, paired-end input and the stated maximum fragment length. It leaves `checkFragLength` and `countReadPairs` unspecified; it subsequently renames columns in a separate step. No package edition is established. The inspected RNA figure/revision script contains no Rsubread counting call. See [the author-code audit](rsubread-result-development/AUTHOR-CODE-AUDIT.json) for exact source links and hashes. Raw author scripts are neither executed nor redistributed. The six source-role drafts remain unapplied; this component adds no tool-availability credit.

## Check the evidence

```powershell
python tools/literature/rsubread_result_evidence.py
```

This verifies archived observations and relationships, current scientific source bytes and the shipped native API. It does not refit R, recount alignments or run a fresh JVM. `--archive-only` checks historical archived observations without claiming current source/runtime validity. [VALIDATION.json](rsubread-result-development/VALIDATION.json) states the exact scope.
