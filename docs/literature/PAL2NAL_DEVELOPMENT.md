# PAL2NAL native Java development

**Status: tested native Java foundation, not an integrated application workflow.**
`org.jlinalg.sequence.Pal2Nal` now performs bounded codon back-translation in Java.
There is no PAL2NAL application command, streamed input contract, portable result
bundle or catalog promotion yet. The published integration count remains **59**.
Do not treat this milestone as complete PAL2NAL support or reproduction of either
paper. Current work is local; no executable release or deployment accompanies it.

## Paper evidence and version boundaries

The audit of all 250 cached primary full texts found two PAL2NAL method mentions:

| Paper | Reported role | Reported version and unresolved choices |
| --- | --- | --- |
| P110, [Romero Charria et al., Nature Genetics](https://doi.org/10.1038/s41588-025-02409-6), Methods / Selection analysis, paragraph 44 | Combined a MAFFT protein alignment with matching nucleotide sequences to prepare codon alignments for EasyCodeML selection tests. | v14; the paragraph does not provide the complete command, genetic-code choice, filtering choices or exact ID policy. |
| P248, [Nature Genetics cucumber pangenome study](https://doi.org/10.1038/s41588-026-02682-z), Methods / Syntenic gene family analyses, paragraph 44 | Used ClustalW2/PAL2NAL alignments in the syntenic-family substitution-rate analysis performed with PAML CODEML. | v14.1; the identity of this reported edition and all effective settings remain unresolved. Do not silently substitute the official v14 archive. |

The paper inventory and registry still need these two entries and their reviewed
usage summaries. Their full paragraph hashes, XML hashes and exact normalized
source paragraphs are retained in the private discovery audit at
`build/literature-review/mafft-runtime/catalog-integration-1/pal2nal-discovery.json`.
MAFFT, ClustalW2, genetic-code selection, alignment curation and CODEML analyses
remain distinct steps. An organism name alone does not establish the authors'
genetic-code option.

The [original maintainer site](https://www.bork.embl.de/pal2nal/) serves the last
official Perl v14 archive. The current [maintainer Python port](https://github.com/grp-bork/pal2nal)
documents intentional differences from v14. Its current behavior must not replace
paper-named behavior without an explicit decision. This Java API uses the original
v14 matching tables and anchor rules within the declared scope below; it does not
implement the Python port's partial anchor chaining.

## Required warnings and recovery

**No verified residues means no evidence of sequence correspondence.** The original
relaxed matcher can produce an alignment even when every concrete peptide residue
disagrees with the DNA. Require a per-row minimum verified count and fraction that
are appropriate for the intended analysis. Both choices are mandatory API arguments.
The permissive values `0, 0` are useful for inspecting original behavior, not a
default admission recommendation. Read every `Result.warnings()` entry and inspect
the per-cell states, paired IDs, placement counts and dropped-triplet reasons.

**Source v14 table 10 is biologically different from NCBI table 10.** It retains
TGA as a stop rather than Euplotid Cys. The API warns and preserves that historical
source behavior. If the analysis requires the correct NCBI Euplotid code, skip it
with this foundation until an explicitly named corrected-code mode is implemented.
Do not relabel the historical table as the current NCBI definition.

The independent NCBI reference also finds five initiation codons missing from the
v14 patterns: table 3 GTG; table 11 ATT, ATC and ATA; table 13 ATA. Those source
patterns remain unchanged. Relaxed matching uses the original fixed start pattern,
which differs again from the table-specific exact start pattern. A source mismatch
report is not a substitute for biological translation validation.

**The original `-nogap` filter uses an uppercase universal-code stop pattern,
regardless of the selected code.** Lowercase stops can survive; nonstandard-code
sense codons can be removed and actual stops can survive. `V14_NOGAP` exposes and
warns about that behavior. `SELECTED_V14_TABLE_NOGAP` is an explicit native departure:
it applies the selected *v14* table without case sensitivity. It still retains the
table-10 definition above and is not a complete current-NCBI mode.

DNA flanks outside the chosen match remain in `Row.originalDna()` with the exact
inclusive match coordinates. Their biological roles are not inferred. Multiple
placements are counted up to two and reported as "at least two"; choose unique
placement or explicitly review the original leftmost choice. Filtered cells remain
in the cell ledger and expanded alignment. Every expanded triplet retains all drop
reasons and its final output coordinate, or zero when removed.

Invalid input, missing correspondence, unmet verification thresholds, capacity
exhaustion or cancellation returns **no `Result`**. Correct the named cause before
retrying. An empty filtered alignment carries an explicit warning; skip downstream
fits. This Java API has no numeric CLI exit-code contract yet. It needs no external
analysis executable or installation.

## Native API scope

The API accepts complete, equal-width peptide rows and corresponding ungapped
IUPAC DNA/RNA rows. Protein IDs and DNA IDs must be unique, and the two populations
must have equal size. Pairing is either exact ID or explicit input order; there is
no implicit positional fallback. IDs may be valid Unicode text, including spaces;
control characters and malformed UTF-16 are rejected.

Supported peptide symbols are the 20 standard residues, `X`, `*`, `-` and the
frame-shift digits `1` through `9`. Lowercase peptide letters are normalized to
uppercase and the original strings remain available. DNA case and RNA `U` remain
literal in output. Literal original reverse-table ambiguity matching is preserved;
the API does not infer or choose a genetic code.

With `normalizeDocumentedSymbols=true`, `.` becomes a gap and `_` becomes a stop.
This explicitly fixes the original v14 re-gapping defect and emits a warning. With
that choice false, these symbols fail. `normalizeTfastx=true` explicitly enables
the original backslash/slash frame-shift rewrite. Other unknown residues, including
`U`, `B`, `J`, `O` and `Z`, fail rather than being silently treated as `X`. An all-gap
row and the original undefined short-peptide relaxed fallback are refused.

All 17 v14 genetic-code tables are supported: 1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13,
14, 15, 16, 21, 22 and 23. Exact or original ten-residue relaxed matching is explicit.
Relaxed matching is not splice alignment or independently placed partial anchors.
Column selections, global mismatch-column removal and gap/stop-triplet filtering
are recorded separately. Frame shifts expand each peptide column to the maximum
needed multiple of three across all rows, preserving consumed bases and padding.

Limits bound sequence count, input width, DNA row length, total input cells,
expanded output cells, work steps and estimated retained storage. Capacity arithmetic
is checked. The cancellation callback is called during validation and matching and
again before returning. A caller must set these limits to fit its own memory budget.

## Small Java example

Build current source from the project root with `./gradlew classes` (PowerShell:
`.\gradlew.bat classes`). Then start JShell with the project classes and resources:

```powershell
jshell --class-path "build/classes/java/main;build/resources/main"
```

Paste:

```java
import java.util.List;
import org.jlinalg.sequence.Pal2Nal;

var proteins = List.of(new Pal2Nal.Sequence("geneA", "MAK"));
var dna = List.of(new Pal2Nal.Sequence("geneA", "ATGGCTAAA"));
var options = new Pal2Nal.Options(
    1,
    Pal2Nal.Pairing.EXACT_ID,
    Pal2Nal.Matching.EXACT_ONLY,
    Pal2Nal.Placement.REQUIRE_UNIQUE,
    Pal2Nal.GapStopFilter.KEEP,
    false, null, false, false,
    3, 1.0);
var result = Pal2Nal.align(proteins, dna, options,
    Pal2Nal.Limits.defaults(), () -> {});
result.alignedSequences(); // {geneA=ATGGCTAAA}
result.warnings();         // []
result.rows().get(0).cells();
```

This example supplies complete in-memory inputs. It does not demonstrate a FASTA
file parser, command-line analysis or portable result verification.

## Evidence and remaining gates

The retained original fixture contains **85 fresh full Perl CLI protocols**:
76 exact native sequence-row comparisons, two explicitly corrected documented-symbol
departures, and seven native refusals of unsupported or invalid source paths.
Every original stdout, stderr, input and process receipt is retained, including
original zero exits that contain errors. The fixture does not contain an upstream
executable. These are small synthetic controls, not independent biological datasets.

**98 focused cases pass against the actual native development JAR on Windows Java
21, Windows Java 25 and Linux Java 21**, with a 128 MiB heap and strict Java 17
compilation. The tests check complete DNA consumption, every cell coordinate,
triplet filtering, threshold failures, cancellation, capacities and immutable results.
An independent capture of [NCBI's genetic-code data](https://www.ncbi.nlm.nih.gov/Taxonomy/Utils/wprintgc.cgi)
checks all 1,088 concrete codons and all 1,088 initiation decisions across the 17
tables, retaining the source differences above. A first harness attempt required
98 while only 97 tests existed; all 97 passed, and that failed harness attempt remains
in the private evidence rather than being overwritten.

The MIT-licensed original reverse-table patterns were cross-checked against all
408 entries in the official archive. The [bundled MIT notice](../../src/main/resources/org/jlinalg/omics/pal2nal-v14-MIT-NOTICE.txt)
credits Mikita Suyama and EMBL. Current code does not download or run PAL2NAL.
See [the native development validation record](PAL2NAL_NATIVE_DEVELOPMENT_VALIDATION.json).

Remaining work: complete streamed UTF-8 FASTA and selected-block alignment input
contracts, typed application recipes, complete result/ledger serialization and
portable semantic verification, actual packaged public workflow tests, two reviewed
catalog observations, registry integration, and prominent application-level recovery
instructions. Resolve the paper's v14.1 provenance and authors' omitted settings
before claiming paper-exact execution. Full-paper reproduction and biological
alignment accuracy remain unproven. Cell Ranger remains excluded by the user.
