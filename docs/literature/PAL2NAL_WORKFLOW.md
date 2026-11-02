# PAL2NAL native codon-alignment workflow

The current source provides **`pal2nal-tools`**, a native Java workflow for codon
back-translation. It reads complete UTF-8 protein FASTA or CLUSTAL alignments and
one or more corresponding DNA/RNA FASTA files, records every consumed cell and
filtered triplet, and verifies saved results by complete native recomputation.
There is no external PAL2NAL runtime to download or install.

This guide supersedes the earlier [native-foundation checkpoint](PAL2NAL_DEVELOPMENT.md).
The workflow is available in current source and a separately built development
JAR. Older released 0.3.7 executables lack this command. The current catalog records
this as a validated scoped native integration; see summary.json for current totals.
No executable release, public-page deployment or whole-goal completion is claimed.

## Build a separate development executable

From the project root in PowerShell:

```powershell
.\gradlew.bat -I tools/literature/pal2nal-development.init.gradle "-Ppal2nalDevelopmentOut=build/pal2nal-review-1" executableJar
$palJar = 'build/pal2nal-review-1/omicsmatic-0.3.7-pal2nal-development.jar'
java -Xmx512m -jar $palJar pal2nal-tools --help
```

The output directory must be new and under `build`. Choose a new name for each
rebuild; the helper refuses to replace an earlier executable. On Linux use
`./gradlew` and the same `-I`/`-P` arguments. The implementation compiles for Java
17; actual packaged tests ran on Windows Java 21/25 and Linux Java 21.

## A complete small run

Create `protein.fasta`:

```text
>geneA
MAK
>geneB
MAK
```

Create `dna.fasta`:

```text
>geneB
ATGGCTAAA
>geneA
ATGGCTAAA
```

Save this UTF-8 JSON as `pal2nal.json`. Input paths are relative to this recipe.
These example choices are explicit; they are not inferred settings for either
catalog paper.

```json
{
  "schema_version": 1,
  "profile": "native-pal2nal-v14-matching",
  "protein": {
    "path": "protein.fasta",
    "format": "FASTA",
    "headers": "FIRST_FIELD"
  },
  "dna": {
    "paths": ["dna.fasta"],
    "headers": "FIRST_FIELD"
  },
  "selection": {"mode": "NONE"},
  "options": {
    "codon_table": 1,
    "pairing": "EXACT_ID",
    "matching": "EXACT_ONLY",
    "placement": "REQUIRE_UNIQUE",
    "gap_stop_filter": "KEEP",
    "remove_mismatch_columns": false,
    "normalize_documented_symbols": false,
    "normalize_tfastx": false,
    "minimum_verified_residues": 3,
    "minimum_verified_fraction": 1.0
  }
}
```

Run and verify:

```powershell
java -Xmx512m -jar $palJar pal2nal-tools --recipe pal2nal.json --out pal2nal-result-1
java -Xmx512m -jar $palJar pal2nal-tools --verify pal2nal-result-1
```

The output parent must exist and be canonical, and the result directory must be
new. Use persistent storage when evidence must be retained. Copy the whole bundle
and verify the copy before treating archival capture as complete; volatile temporary
locations may be removed after a successful run. Native input/output paths and headers may contain Unicode. Verification can
run after relocation, including on another supported OS, without the original
external input files. It reads the complete retained snapshots and recomputes all
codon placements and filtering decisions. It does not run the upstream program.

## Required review and recovery

**Require exit code 0, a valid `COMPLETED.json`, and successful `--verify` before
downstream use.** Read `WARNINGS.md`, `rows.ndjson`, `cells.tsv`, `triplets.tsv` and
`capture-receipts.json`. A provisional `session.json` or an alignment file alone is
not an admitted result. Every row and original snapshot must be present.

**SKIP3:** the requested edition, genetic code or capability is unavailable, or all
triplets were filtered. Skip dependent analysis. Resolve the named requirement or
change the explicitly intended analysis before retrying into a new directory.

**FAIL2:** invalid or changing input, integrity mismatch, unmet verification
thresholds, capacity exhaustion, interruption or finalization failure admits no
completed result. Read `FAILURE.md` and stderr. Preserve all snapshots and partial
outputs, correct the named cause, and retry into a new directory. Increase `-Xmx`
and the appropriate explicit capacity when needed; never truncate inputs to pass.

**Relaxed success is not evidence of biological correspondence.**
`ALLOW_V14_RELAXED` can replace failed ten-residue anchors by wildcards. Explicitly
permissive verification thresholds can therefore accept a sequence with no verified
residues. The workflow warns about zero verification, mismatches, X residues,
multiple placements and unplaced DNA flanks. Review the complete ledger and choose
scientifically appropriate per-row thresholds. The example requires every residue
to match.

**Historical source differences remain visible.** Original table 10 treats TGA as
a stop rather than NCBI Euplotid Cys; source tables 3, 11 and 13 lack five current
NCBI initiators. `V14_NOGAP` uses a case-sensitive uppercase universal stop pattern
regardless of the selected table. `SELECTED_V14_TABLE_NOGAP` is an explicit native
departure that uses the selected v14 table without case sensitivity; table 10 still
has its historical definition. If a corrected code is required and unavailable,
skip that analysis rather than relabeling the source result.

The two named profiles are `native-pal2nal-v14-matching` and
`native-pal2nal-v14.1-matching`. The official v14 archive and Bioconda-pinned v14.1
archive have identical executable lines after full-line comments/blank lines are
removed; 85 fresh runs of each source also have identical stdout bytes and exit
statuses. They remain explicitly named profiles, not a guessed version fallback.
This does not identify the authors' exact build, command or genetic-code choice.

Input data, hashes and native checks establish integrity, not authenticity or
biological alignment accuracy. No full-paper reproduction is claimed. Cell Ranger
remains excluded by the user.

## Inputs and choices

| Setting | Contract |
| --- | --- |
| Protein format | Explicit `FASTA` or complete `CLUSTAL` blocks. No format guessing, ragged blocks, hidden row loss or unrecognized footer. CLUSTAL row order, widths and optional cumulative counters must agree. |
| Headers | `FIRST_FIELD` or `WHOLE_HEADER`; complete original headers remain in raw snapshots and row metadata. CLUSTAL IDs are literal tokens. Collisions fail. |
| DNA files | 1–128 complete ungapped IUPAC FASTA files; IDs are unique across the complete population. DNA case and RNA U remain literal. Numeric/punctuation residues are refused. |
| Pairing | `EXACT_ID` checks complete ID sets, or explicitly select `INPUT_ORDER`. There is no implicit positional fallback. |
| Placement | `REQUIRE_UNIQUE`, or explicit `SOURCE_LEFTMOST` with ambiguity warnings; counts are capped at "at least two". |
| Selection | `NONE`; `CLUSTAL_HASH` for literal # positions; or `MASK_FILE` with a `path` and exactly one #/space per peptide column plus an optional final newline. At least one position must be selected. |
| Symbols | Standard residues, X, *, gap and frame-shift digits 1–9. Explicit documented-symbol normalization maps . to gap and _ to stop and warns about the original re-gapping defect. Explicit tfastx normalization supports the source slash/backslash rewrite. Other unknown residues, U, zero-width digits and all-gap rows fail. |
| Filters | Global mismatch-column removal and gap/stop-triplet removal preserve all cells and drop reasons. Original DNA flanks are retained without inferring their biological roles. |
| Unsupported | Partial anchor chaining, splice alignment, reverse-complement inference, automatic genetic-code choice, unresolved profiles and original HTML/web transport are unavailable. |

All source v14 tables are available: 1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 15,
16, 21, 22 and 23. Explicit limits cover combined input bytes/decoded characters,
rows, cells, line/header widths, estimated retained storage, native work, output
bytes and bundle members. The runtime lowers retained-storage ceilings to fit a
fraction of the actual Java heap and records both declared and effective limits.
Portable verification checks those receipts and applies its own available heap
bound. It fails rather than accepting a prefix when resources are insufficient.

## Saved results

| File | Meaning |
| --- | --- |
| `inputs/*` | Every original protein, DNA and optional selection-file byte. |
| `recipe-request.json`, `recipe-replay.json` | Original request and a replay with only owned input paths rebased. |
| `capture-receipts.json`, `input-report.json` | Source capture identities, snapshot hashes, parser counts and explicit input policies. |
| `rows.ndjson` | Paired original IDs/headers, snapshot files, DNA match coordinates, placement ambiguity, verification counts and opaque export IDs. |
| `cells.tsv` | Every peptide column, consumed DNA start/bases and verified, mismatch, wildcard, gap or frame-shift state. |
| `triplets.tsv` | Every expanded triplet, original column/part, final output coordinate or zero, and all drop reasons. |
| `alignment.fasta`, `alignment.expanded.fasta` | Filtered and complete expanded native nucleotide alignments with the declared peptide IDs. |
| `alignment.phy`, `alignment.aln` | Sequential PHYLIP and CLUSTAL exports using explicit opaque `PN` row IDs mapped in `rows.ndjson`. No PAML or phylogeny program is executed. |
| `protein.normalized.fasta` | Complete explicitly normalized peptide alignment. |
| `summary.json`, `WARNINGS.md` | Effective scientific choices, complete counts, source differences and required actions. |
| `session.json`, `manifest.json`, `COMPLETED.json` | Provisional session, complete file/directory census and final admission receipt. Hashes alone are insufficient; native recomputation must pass. |

See [workflow validation](PAL2NAL_WORKFLOW_DEVELOPMENT_VALIDATION.json) for retained
source pins, exact scope, executable evidence and remaining catalog gates. The
[earlier foundation validation](PAL2NAL_NATIVE_DEVELOPMENT_VALIDATION.json) is a
historical checkpoint with a smaller API-only scope.
