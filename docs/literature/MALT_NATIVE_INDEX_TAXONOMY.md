# Native accession mapping for a MALT index

This Java command maps **every reference accession** in an unclassified MALT index to a supplied NCBI taxon. It creates a new index, preserves all reference sequence bytes and seed tables, and checks the full result. It also captures the original index and mapping for a complete, read-only replay. No external Java library or MALT executable is required for this native stage.

**Warning: this is an explicitly different indexing method.** The tested original MALT 0.5.2 packages ignore the legacy taxonomy accession map when building these controls. Both exited zero with taxonomy IDs `0, 0, 0` after organism-name parsing was disabled. The two package builds contain identical MALT, MEGAN and jloda jars. The [upstream August 2021 fix](https://github.com/husonlab/malt/commit/88d1a6331f0a7c8e8df2cf4b8ce576eeb88cf745) adds the missing taxonomy-map registration. This native alternative does not reproduce that original code path, the paper's complete historical database, or a biological result.

The command refuses missing or conflicting mappings, unknown taxa, existing assignments, duplicate reference identities, truncated files and capacity overflows. It never selects a subset, strips accession suffixes, overwrites an existing index, or repairs an upstream result in place. Its source index must have **all-zero taxonomy IDs and no existing taxon tags**. The taxonomy tree must contain every requested positive taxon ID.

## Build

From the repository root, use a permitted JDK 21 or 25. This creates a development JAR targeting Java 17; it does not replace the released executable.

```powershell
python tools/literature/build_malt_index_taxonomy.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/malt-native-index-1
```

On Linux, use `python3`, your JDK's `bin` path and ordinary shell line continuations. Keep `LICENSE-project.txt` with the component. The build prints **SKIP3** when the compiler or installation cannot be accessed. **Required action:** supply a permitted complete JDK, choose a new output directory and retry. If installation is unavailable or forbidden, skip the dependent build/analysis; no substitute result is produced.

## Map a complete index

For a two-column, tab-separated mapping such as `NC_012920.1<TAB>9606`, use literal keys. The first word of each original reference header must match a mapping key exactly.

```powershell
java -Xmx3g -jar build/malt-native-index-1/malt-index-taxonomy-development.jar `
  --database path/to/complete-unclassified-index `
  --mapping path/to/accession-taxonomy.tsv `
  --format two-column --namespace literal `
  --out build/malt-native-index-result-1
```

For an original NCBI accession2taxid file, including a `.gz` file, use `--format ncbi-accession2taxid --namespace accession.version` to select its second column. Choose `--namespace accession` for its first column. The literal header must be `accession<TAB>accession.version<TAB>taxid<TAB>gi`. No suffix is removed from reference IDs: a versioned reference will not match an unversioned key automatically. Every mapping row is consumed and checked for format. Repeated selected keys with the same taxon are counted; conflicting selected keys fail. Unselected keys are not retained in memory or checked for cross-row uniqueness.

The output contains:

- `inputs/index/` and `inputs/mapping.tsv[.gz]`: complete captured inputs.
- `index/`: the separately derived index. It changes only `ref.db`, `ref.idx` and `taxonomy.idx`, adding `|tax|ID` to each first-word reference identity while preserving its description and sequence.
- `audit.json`, `COMPONENT_COMPLETED.json` and **`WARNINGS.md`**: full byte inventories, mapping results and scope.

This command does not run an aligner. Use the derived `index/` only with a separately permitted, qualified MALT provider and a complete declared query population. Capture all upstream warnings and validate SAM/RMA outputs before downstream interpretation. MALT 0.5.2 requires **different directories** for `--output`, `--alignments` and `--outAligned`; using the same directory for RMA and SAM fails. Its naive-mode coverage in the tested provider is 80%, despite the help's weighted-mode display of 100%.

If any required index, mapping, original runtime or complete dataset cannot be supplied or installed where permitted, **skip that dependent analysis**. An empty alignment or failed mapping does not establish biological absence. The paper's original indexing path and the complete MALT/HOPS/MaltExtract roles remain open.

## Verify saved output

```powershell
java -Xmx3g -jar build/malt-native-index-1/malt-index-taxonomy-development.jar `
  --verify build/malt-native-index-result-1
```

Replay reads every captured mapping row and reference sequence, checks the complete numeric taxonomy, recalculates each expected header/taxon, compares every unchanged member, and checks the full bundle inventory before and after. It does not execute MALT or authenticate an unsigned producer. A mismatch returns **FAIL2 NO RESULT ADMITTED** with a required action. Preserve the failed directory and diagnostics, resolve the named issue, then retry a **new** directory. A pre-existing output directory is left untouched.

Defaults admit files up to 1 GB, one million references, 100 billion reference bases, one billion mapping rows, 1 MiB lines/headers, and a taxonomy of up to three million nodes. Use `--help` for explicit capacity overrides. Limits refuse a complete analysis; they never truncate it.

## Validation boundary

Six JUnit tests cover complete derivation/replay, explicit accession versions and gzipped NCBI input, conflicting/missing/unknown mappings, existing assignments and capacity limits, changed sequence/taxonomy/seed refusal, and rejecting nested output before changing the source index. Real controls use all three reference genomes (52,244 bases) and all 16,856 queries across two complete libraries and a six-read control. Original MALT 0.5.2 produces 106 aligned queries and 117 SAM alignments with the native-derived index. Every SAM row and complete canonical raw RMA read/match/classification field matches the original named-header control; floating-point fields are compared by raw bits and file-local UIDs are retained separately. These controls qualify this explicit native mapping operation, not whole-tool or paper reproduction.

The [development evidence](malt-index-taxonomy-development/VALIDATION.json) and [portable controls](malt-index-taxonomy-development/controls.zip.xz) retain complete data, source pointers, original diagnostics and the failed header-tag-only attempt. The archive excludes upstream executables, complete papers and restricted programs. Run its independent native replay with:

```powershell
python tools/literature/verify_malt_index_taxonomy.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/malt-native-index-replay-1
```

This component adds no whole-tool catalog credit. The fixed 250-paper catalog and full implementation objective remain in progress.
