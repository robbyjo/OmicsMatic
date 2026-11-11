# CellPhoneDB raw preparation and original full-method controls

**This adds native Java raw-count preparation and full original-method reference controls. A public CellPhoneDB execution adapter and independent full-output scientific audits remain unfinished. CellPhoneDB is still missing from the implemented-tool count.** Read this with [the prepared statistics guide](CELLPHONEDB_STATISTICS_DEVELOPMENT.md).

**SKIP3: skip dependent analysis when a required runtime, installation, download or input is unavailable or forbidden. FAIL2: unexpected source errors, invalid inputs, identity changes, numerical mismatches or capacity failures admit no partial batch.** Preserve all original files, aliases, mapping relations, float32 casts, failed directories and full diagnostics. Correct the named problem and retry into a **new** directory. Do not discard aliases, renumber a database, normalize values, patch upstream code or change frozen hashes to make a failing analysis pass.

## Native Java preparation

`org.jlinalg.singlecell.CellPhoneDbPreparation.prepare` accepts typed counts, metadata and explicit database gene/composition/interaction rows. It does not load a database, parse a file, infer identifiers, normalize expression, convert species or fit a differential-expression model. The database rows must be the actual rows for the declared identifier field. This API is a necessary preparation stage; successful preparation alone does not prove that an original full method can produce a result.

Example using Java 21 or 25 with the new sources or the additive development artifact recorded in `cellphonedb-preparation-development/VALIDATION.json`:

```java
import java.util.*;
import org.jlinalg.singlecell.CellPhoneDbPreparation;
import org.jlinalg.singlecell.CellPhoneDbStatistics;

var counts = new CellPhoneDbPreparation.Counts(
    List.of("gA", "gA", "gB", "UNMAPPED"), List.of("z", "a"),
    new double[][] {{1.1, 2}, {3.1, 4}, {5, 6}, {9, 9}});
var metadata = new CellPhoneDbPreparation.Metadata(
    List.of("a", "z"), List.of("A", "B"));
var genes = List.of(
    new CellPhoneDbPreparation.Gene(101, "toy_eA", "gA", "toy_hA"),
    new CellPhoneDbPreparation.Gene(102, "toy_eB", "gB", "toy_hB"));
var interactions = List.of(new CellPhoneDbStatistics.Interaction("toy", 101, 102));
var prepared = CellPhoneDbPreparation.prepare(
    counts, metadata, genes, CellPhoneDbPreparation.Identifier.GENE_NAME,
    List.of(), interactions, CellPhoneDbPreparation.Limits.defaults());
prepared.mappedFeatures();   // [101, 102]
prepared.cells();            // [a, z]
prepared.geneLedger();       // Includes the unchanged UNMAPPED row and both gA rows
prepared.castingLedger();    // Records the source float32 casts
prepared.warnings();
```

Every identifier, gene, interaction and value here is synthetic. The example makes no ligand–receptor or biological claim. Preserve the original `Counts` object and raw input files alongside the result. `prepared.prepared()` supplies the explicitly cast matrix for the already validated statistics API; its `FLOAT32` precision is declared. Downstream full methods have additional failure boundaries described below.

The finite nonnegative input contract is explicit. Negative, NaN and infinite raw input are rejected; they are not replaced or imputed. Casting a finite double to float32 can itself overflow or underflow. All changed cells, including cells later removed because they lack metadata, appear in the casting ledger. Derived nonfinite states remain visible with a warning and **do not admit downstream analysis**. Limits bound complete matrices, metadata/database row families, mapping joins and logical operations before new matrix allocation; cancellation rejects the computation. Public arrays are defensively copied.

Preparation follows the original sequence:

1. Cast the original matrix to float32, then remove count columns absent from metadata. Require every metadata cell to exist in counts. Preserve removed cell identities.
2. Sort retained cell names by Unicode code point and align cell types in that exact order. Typed literal identifiers are not normalized, guessed or silently converted to missing values.
3. Join each raw gene against the selected `ensembl`, `gene_name` or `hgnc_symbol` field. Preserve every many-to-many relation and source row identity; retain unmapped rows in a disposition ledger. The original package's disabled synonym substitution stays disabled.
4. Group by sorted multidata ID with the bound pandas 2.2.3 **float32 compensated mean**. This differs from the sequential numpy-groupies cluster mean in the statistics component. Repeated raw rows retain their original aggregation weight.
5. Filter zero-expression protein rows. Determine complete complexes using the original number of matching composition rows versus each row's `total_protein`. Duplicate composition rows and per-row totals are not replaced with a distinct-protein interpretation.
6. Remove duplicate interaction endpoint pairs, retaining the first original interaction identity. Filter interactions by the available protein/complex IDs. Preserve all first-partner IDs before all second-partner IDs when selecting direct protein counts; append complete-complex proteins in original mapped-feature order and remove exact duplicate count rows by first occurrence. Complete complexes can retain proteins even when no remaining interaction references them.
7. Retain filtered composition rows and build the sorted numeric complex-to-protein-row map. Do not confuse mapped-protein order, prepared-protein order and complex order.

The original CellPhoneDB MIT notice remains in the parent artifact; the pandas BSD 3-Clause notice and exact group-mean source profile are included with this addition. No curated database is included or relicensed.

## Full original methods and discovered boundaries

The reference suite executes unchanged published CellPhoneDB **5.0.1** under the previously bound private Linux runtime. It uses three entirely synthetic schema-complete databases, raw count/metadata TSV files and actual method 1/2/3 calls. Positive families cover gene-name, Ensembl and HGNC identifiers; repeated rows; fractional float32 conversion; Unicode cell ordering; duplicate interaction annotations; original 100-label permutations; categorical microenvironments; supplied DEGs; interaction-specificity scoring; CellSign; original subsampling; and an Ensembl input with no complete complex. These are tiny software controls, not biological validation or paper reconstruction.

The reference collection retains all returned frames, original dtypes/axes, full TSVs, binary debug pickles, lossless typed debug snapshots, RNG states, effective calls, emitted diagnostics and process receipts. Trusted original debug pickles are read only inside the controlled reference collector; the retained-evidence verifier reads their captured JSON/bytes and does not execute a pickle. Empty and missing result states remain explicit.

All **19 source controls** completed: **13 full workflows and six expected errors**. The output audit reads all **56 TSV tables and 8,179 cells**, including 1,669 cells whose actual returned dtype is float32, and retains ten original CellSign class sentinels. The full references include three scoring workflows, one CellSign workflow and one subsampling workflow. These counts certify complete captured reference families and file/return consistency; independent scientific audits of all final scores, TF logic, sampling geometry and full-method outputs remain open.

All **15 typed preparation families** match the actual original helper outputs exactly on Windows Java 21, Windows Java 25 and Linux Java 21: **3,835 numeric values and 1,374 identity/state values per JVM**, with no numeric tolerance, plus 15 native rejection controls. Each JVM's independently checked ledgers retain 111 gene rows, 26 unmapped rows, 85 mapping relations, 15 removed count columns and 104 changed float32 cells, including 26 positive values that underflow to zero. Alias and empty-complex warnings are explicit. Seven adversarial checks reject inconsistent CSV/returned values even after coherent file-hash updates, fabricated empty CellSign frames, substituted sequential means, reversed cell order, omitted cast entries and false public-result admission.

**The following original failures remain errors, not repaired analyses:**

| Trigger | Observed bound source behavior | Required action |
| --- | --- | --- |
| Distinct raw aliases map to the same protein and expand incompatible result annotations | `InvalidIndexError` during result assembly | Keep all alias relations and the failed batch. Use a separately validated upstream fix/profile or a different scientifically justified input prepared as a new, explicitly documented dataset. Never silently drop an alias. |
| `gene_name` simple analysis has no complete complex | `InvalidIndexError` from duplicate columns in the original empty-complex deconvolution branch | Retain the failure. Do not relabel the original input as Ensembl; a separate declared Ensembl control tests its own distinct source path. |
| DEG mode has no complete complex | `TypeError` from the original empty-composition DEG helper | Skip the failed dependent analysis and retain the traceback. A successful numerical preparation does not admit this full DEG workflow. |
| Scoring receives a custom database whose protein ID and protein multidata ID differ | Source scoring treats `protein_id` as the numeric count identity and fails (`ValueError` in this control) | Verify database compatibility and provenance. Select a source-compatible database or a separately validated upstream fix; never silently renumber the user's database. |
| No gene maps to the declared identifier field | `AllCountsFilteredException` | Check the actual identifier field, species and database version. Record any justified conversion as a new preparation; do not guess or silently convert. |
| A metadata cell is absent from counts | `ParseCountsException` | Correct the source identity/matching problem in a new explicit input bundle; preserve the original mismatch. |

The original statistical/DEG result dictionaries can contain the **`pandas.DataFrame` class itself**, rather than an empty frame, for CellSign states. The collector records that exact class sentinel and whether active TFs were requested; it does not invent an empty table or a successful TF result. The separate source progress error at one single-thread permutation remains documented in the statistics guide; full-driver controls use 100 iterations.

Scoring uses geometric expression for complexes, while the core interaction analysis uses subunit minima. These are different source paths and must not be substituted. A debug seed is a source testing option, not a guarantee of cross-platform or multiprocess randomness. The source runtime's global `UserWarning` filter is unchanged; ignored warnings cannot be claimed captured. Private caches, including the NumBa cache, keep JIT work outside the dependency prefix.

## Curated database provenance

The official database **v5.0.0**, commit `71ffa8a163389913907d40993bec2821a111f64d`, was captured privately with complete Git-blob checks and all eight ZIP-table identities. All 1,355 original protein rows have matching protein and multidata IDs, explaining why the scoring assumption holds for that edition. The official README documents use with CellPhoneDB, but no separate redistribution grant was confirmed. The curated data remains local and is **not** copied into the public component evidence or presented as covered by the software MIT license. The synthetic control databases contain project-authored toy data only.

Software and database editions remain separate. The current controls do not establish paper-exact equivalence for reported software 5.1, 5.0.0 or 2.1.4, or a reported version-2.0 interaction database. The 12 source-role drafts and five new named-tool definitions remain unapplied; the effective catalog counts are unchanged.

## Verification and remaining work

Use `python -B tools/literature/cellphonedb_preparation_evidence.py` to re-read the sealed evidence without installing CellPhoneDB. `VALIDATION.json` records the exact development artifact and checks; `controls.zip` retains complete original/native controls, source/license bindings and all failed local batches. The additive build preserves every entry from the statistics artifact and leaves the canonical executable unchanged. Hashes provide integrity, not source authenticity or a signature.

Next work is the public typed upstream execution/setup adapter, complete independent final-output audits, explicit runtime/download policy and process/error/timeout tests, followed by application of the primary-bound paper-role drafts through the existing catalog chain. Reference execution and preparation parity do not establish those unfinished stages. Cell Ranger stays excluded. No release, website deployment, whole-paper or whole-goal completion is claimed.
