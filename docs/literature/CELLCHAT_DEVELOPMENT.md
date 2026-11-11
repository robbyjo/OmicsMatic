# CellChat prepared RNA calculations and database export

**Development checkpoint; CellChat remains missing in the catalog.** The project
still has 93 new scoped integrations, 96 including existing adapters, and 248
missing tools. This checkpoint adds a native Java component and a separately
tested database exporter. The complete CellChat adapter, paper-specific workflows
and catalog promotion remain unfinished. The canonical executable is unchanged;
there is no root `cellchat-tools` command in that executable.

**Warnings and required actions:**

- A database export is a resource result. It never admits communication inference.
- The Java API accepts prepared, declared normalized nonnegative RNA expression,
  exact identifiers, explicit interactions/resources and explicit group order.
  Obtain those inputs from the intended workflow. Counts, integrated assays with
  negative values, imputation and projected expression are not interchangeable.
- Java does not generate R permutations from a seed. Supply the actual complete
  cell permutations when replaying a reference. An empty array requests observed
  scores and returns no p-values. For authoritative permutation p-values at exact
  or near floating-point ties, execute the unchanged original R functions. Never
  round scores or repair ties to force agreement.
- `SKIP3` means the required exact source, species resource or R runtime is missing
  or unsupported: **skip dependent analysis**. Supply the named original files or
  install base R where permitted, pass its executable path, then retry with a
  **new output directory**. If installation is forbidden, leave analysis skipped.
- `FAIL2` means invalid input, changed bytes, process failure, capacity or
  finalization failure: **admit no result**. Read `WARNINGS.md`, `COMPLETED.json`,
  process receipts and retained stderr; correct the named cause and retry to new
  paths. Preserve failed attempts and partial files.
- Historical 1.0.0/1.1.3 single-subunit complex dimension dropping is outside the
  Java component. It is refused explicitly; use original R. The API also refuses
  missing required complex subunits, duplicate identifiers, unused group levels,
  negative/non-finite/all-zero expression and malformed permutations.

## Native Java use

The API is
[`CellChatRna`](../../src/main/java/org/jlinalg/omics/CellChatRna.java).
The development JARs below contain that API and its process probes, compiled for
Java17. They are separate from the released/root executable.

```java
import java.util.List;
import java.util.Map;
import org.jlinalg.omics.CellChatRna;

var pair = new CellChatRna.Interaction("LR", "L", "R", "", "", "", "");
var result = CellChatRna.infer(
    CellChatRna.Profile.CELLCHAT_2_2_0_RNA,
    new String[] {"L", "R"},
    new double[][] {{0.1, 0.2, 0.8, 0.9}, {0.9, 0.8, 0.2, 0.1}},
    new int[] {0, 0, 1, 1}, 2,
    List.of(pair), Map.of(), Map.of(),
    new CellChatRna.Parameters(CellChatRna.Average.TRI_MEAN, 0.1, 0.5, 1, false),
    new int[0][]);
double[][][] scores = result.probabilities(); // interaction, source, target
System.out.println(scores[0][0][1]);
result.warnings().forEach(System.err::println);
```

Compile/run a class containing that example with the JAR on the classpath:

```powershell
javac --release 17 -cp docs/literature/cellchat-development/cellchat-rna-javac21.jar Example.java
java -cp ".;docs/literature/cellchat-development/cellchat-rna-javac21.jar" Example
```

Complex maps contain ordered component genes. Cofactor maps contain ordered
cofactor genes. Empty interaction cofactor names mean absent cofactors. Original
CellChat cofactor intersection ignores unmeasured genes and removes duplicates;
required complex subunits remain mandatory. These are distinct source behaviors.
All input arrays remain caller-owned; result accessors return copies.

The profiles preserve differences in complex/cofactor formation before versus
after group averaging, population weights and inclusive versus strict bootstrap
tails. Methods include Tukey trimean with R type7 quantiles, truncated mean,
median, and the modern thresholded mean. Group order is explicit. Scores use the
original global maximum scaling and Hill/cofactor products; they can exceed1
when agonists amplify a strength. They are not calibrated event probabilities.

## Export a database separately

Run from the repository root with Python3.11+ and base R. This path does not need
the installed CellChat package. Its helper uses standard-library Python and base
R only. Source-label and species choices are bound to
[`cellchat-source-lock.json`](../../tools/literature/cellchat-source-lock.json).

To prepare a fresh checkout, run metadata capture, edition discovery and source
intake in that order. These commands download six immutable original GPL source
candidates and require explicit download authorization:

```powershell
python tools/literature/intake_cellchat_metadata.py --allow-download --out build/literature-review/cellchat-runtime/source-metadata-1
python tools/literature/discover_cellchat_editions.py --allow-download --out build/literature-review/cellchat-runtime/edition-discovery-1
python tools/literature/intake_cellchat_sources.py --allow-download --out build/literature-review/cellchat-runtime/source-intake-1
```

Keep existing output directories. A checkout with the captured original files can
export immediately; it does not need to repeat downloads:

```powershell
python tools/literature/export_cellchat_database.py --source-label 2.2.0-2025-release --source-root build/literature-review/cellchat-runtime/source-intake-1/2.2.0-2025-release/source --species human --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe" --out build/literature-review/cellchat-runtime/my-human-export
python tools/literature/verify_cellchat_database.py --bundle build/literature-review/cellchat-runtime/my-human-export
```

Require exit0, `completed-cellchat-database-export-only`,
`resource_export_complete: true`, matching file hashes and a successful verifier.
The verifier checks retained structure and bindings; it does not rerun R or
authenticate a producer.

The output contains interaction, complex, cofactor and geneInfo tables. Each
table retains an explicit `resource_row` column and original column/component
order. `column-classes.tsv` retains source classes; `missing-values.tsv` gives
one-based row and original-column positions for actual missing values. Literal
`NA` strings and actual missing values must remain distinct. Do not trim, repair
or alias identifiers. The exporter does not merge databases, apply a protein-only
filter, select interactions or infer which database a paper used.

Original1.0.0 and1.1.3 sources provide human/mouse databases; the four modern
candidates also provide zebrafish. No cross-edition/species fallback is allowed.
The recorded1.0.0/1.1.3 human database has1939 interaction rows; modern human
candidates have3233. A version label alone is insufficient provenance.

## Original source profiles and licensing

Original CellChat is GPL3, by Suoqin Jin and contributors. Read
[the upstream notice](CELLCHAT_UPSTREAM_NOTICE.txt) and the complete
[GPL3 license](../../LICENSE). Unchanged source definitions and source metadata
are included in the evidence archive, with original URLs/commit/hash bindings.

| Explicit candidate | Repository | Commit |
| --- | --- | --- |
| 1.0.0 | sqjin/CellChat | 1a719922cc3a7549011cb59e160a9c94f89ab2a8 |
| 1.1.3 | sqjin/CellChat | fea1c8ecdea0f393d32513cd00b866f8b4457839 |
| 2.1.2 | jinworks/CellChat | a0d3b2d231d46c8787177fffeac908270c253747 |
| 2.2.0 initial2025 | jinworks/CellChat | af13f5125bccccefd736da31d96378d248d0ce33 |
| 2.2.0 release2025 | jinworks/CellChat | 346fb615aaefa3c1a2830ec2fe3a933c5f723c83 |
| 2.2.0 description2026 | jinworks/CellChat | 7661a6b2687d46f0815c03a1575da0f792cecbca |

These candidates identify source trees. They do not identify a paper's actual
commit or historical R environment. The current upstream development version
2.2.0.9001 is not substituted for a paper reporting2.2.0.

## Six primary paper roles

The audit read all250 original XML records, checked DOI/XML/evidence hashes,
found the six existing observations and no additional unobserved primary mention.
Six role drafts are retained unapplied in the evidence; the catalog's1018 reviews
and every existing observation are unchanged.

| Paper | Primary role and remaining work |
| --- | --- |
| [P060](https://doi.org/10.1038/s41586-023-06808-9) | Database defines pairs; custom imputed-expression score. Its printed component product is not silently resolved into a guessed implementation. |
| [P085](https://doi.org/10.1038/s41588-024-01961-x) | CellChat1.1.3, human database, CellTrek region annotations, disease/control objects, overexpression, PPI projection, inference, pathways, centrality, merge/similarity/rankNet. Region annotation does not establish coordinate-distance modeling. |
| [P090](https://doi.org/10.1038/s41588-024-01952-y) | CellChatv1.0 defaults in R4.0.3. The original1.0.0 default raw.use=FALSE differs from the explicitly prepared raw.use=TRUE controls here. |
| [P100](https://doi.org/10.1038/s41588-025-02407-8) | CellChat2.1.2 communication feeds the separate FlowSig0.1.2 analysis. Downstream tests/thresholds remain separate. |
| [P145](https://doi.org/10.1038/s41588-026-02709-5) | CellChat2.2.0, human database excluding non-protein signaling; condition pooling, min.samples3/min.cells10 filtering, pathways/centrality and separate per-sample inference/Wilcoxon comparison. |
| [P167](https://doi.org/10.1038/s41588-025-02353-5) | Human database combines with CellPhoneDB/iTALK; Spyrrow computes a neighborhood geometric-mean score. This is a separate execution method. |

Before catalog promotion, implement/test the complete original-runtime workflow,
including overexpression selection, explicit raw/projected input handling,
sample-aware filters, pathway/net summaries and the reported condition comparison
operations. Spatial inference, CellTrek, FlowSig, Spyrrow and the custom P060 score
have separate contracts. Do not replace their roles with a generic RNA score.

## Retained validation

See [VALIDATION.json](cellchat-development/VALIDATION.json) and
[controls.zip](cellchat-development/controls.zip). The archive includes failure
attempts, source snapshots, original function definitions, inputs, original R
outputs, native outputs, commands, warnings and per-file hashes.

- 150 prepared source/control families, including both 1.1.3 fast and slow paths,
  four modern averaging methods, zero and constant expression, unequal groups,
  complexes, missing/duplicate cofactors, Hill parameters and population weights.
- Two Java compilers crossed with WindowsJava21, WindowsJava25 and LinuxJava21:
  900 comparisons,69984 observed score cells, maximum scaled difference
  6.106226635438361e-16. Permutation exceedance counts match in these controls.
- 18 scientific refusal contracts and two averaging boundaries per build/runtime
  pair; result-copy and observed-only behavior checked for each control.
- 16 original database exports, five missing/invalid capability dispositions and
  two changed/extra-output verification refusals. Base-R table roundtrips preserve
  all table cells and missing positions.
- The first native validation attempt is retained as a verifier failure: R's
  shorter TSV decimal serialization was initially compared as exact doubles.
  The corrected verifier compares exact integer permutation exceedance counts.
  Original functions, expression and result files were not changed to force a fit.

Original function/reference execution and database roundtrips used Windows
R4.6.1. Linux validation here executes native Java; it does not establish Linux R
or historical R4.0.3 compatibility. Prepared controls, hashes and a structural
verifier do not establish biological accuracy, statistical universality,
whole-paper reproduction or completion of the250-paper goal.
