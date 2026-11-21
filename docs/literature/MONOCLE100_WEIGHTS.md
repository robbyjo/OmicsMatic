# Original-edition Monocle1.0.0 neighbor weights in Java

The native `Monocle100Weights` API and standalone `Monocle100WeightsCli` now
construct selected Monocle3 **1.0.0** graph-test weights from a complete supplied
original RANN ranking and, for principal-graph mode, the explicit principal graph
and cell mapping. The [runnable example](../../examples/monocle100-weights/README.md)
connects that stage to the existing covariate-aware residual-Moran method.

This implements a missing stage identified in P021. Nearest-neighbor calculation,
original1.0.0 graph learning and the paper's response/covariate preparation remain
separate. The paper's `UMAT` label, unreported roots/assay and unknown executed
commit are not inferred. The native method is available from a source build, with
no R runtime for its prepared-input operation. The overall goal remains unfinished;
catalog counts remain144 scoped families,1630 reviewed uses and1092 pending.

## Source behavior

The selected original source is [Monocle3 commit004c096](https://github.com/cole-trapnell-lab/monocle3/tree/004c096f3a3bdaf1e6709a65fb36f40ba775f1ab),
`R/graph_test.R` and `src/clustering.cpp`, under the upstream MIT license.
The exact166-member source inventory and archive are pinned in
[`source-profile.json`](../../tools/literature/monocle100/source-profile.json).

- Discard the first supplied ranking column literally. Do not search for and
  remove the query cell instead. Original tied-coordinate rankings can retain
  self-links, which remain visible in output and warnings.
- KNN mode preserves ranking order. Principal-graph mode keeps links within the
  same or adjacent principal-point memberships, then extracts neighbor indices
  in ascending cell order. Principal indices follow the actual `dp_mst` column
  order, not an inferred sorting of vertex labels.
- The selected source passes FALSE to `jaccard_coeff`; its eventual listw uses
  binary neighbors with W-style row-standardization. Jaccard magnitudes are not
  edge weights in this path. Parallel principal edges add no second weight.
- Keep isolated cells in the complete row population. The selected original
  runtime fails for a single occupied principal level, for singleton-neighbor
  extraction and when every principal-mode sparse row simplifies to a scalar
  (zero or one neighbor). The Java stage explicitly refuses those cases.

Source checks cover33 actual original controls:25 returned graphs and eight
original errors. Successful controls include directed KNN rankings, shuffled
principal-point order, noncontiguous memberships, extra/shuffled projection rows,
disconnected graphs, parallel edges/loops, tied coordinates and uniform neighbor
counts greater than one. Full original RDS objects, warnings, classes, names and
RNG endpoints remain retained. The Java interface compares neighbor order and
weights; it is not an R-object attribute emulator.

Original numeric JSON views round some doubles. Exact comparison therefore uses
17-digit strings exported directly from all25 retained binary listw objects.
The rounded views remain retained; no tolerance replaces exact weight comparison.

## Java API and input contract

```java
import org.jlinalg.singlecell.Monocle100Weights;

var selected = Monocle100Weights.knn(originalRanking, 50_000_000L);
System.out.println(selected.arcs());
```

`originalRanking` is a complete rectangular `int[][]` of zero-based original
neighbor indices, including the first column that the source discards. Each row
must contain unique indices on the complete cell axis. `principalGraph()` also
accepts a complete zero-based cell-to-principal array, the full principal-point
count and undirected `PrincipalEdge` records. Its result owns immutable neighbor
lists and directed `Arc` values, with isolate/self-link counts.

The standalone request declares `schema_version:1`, a `neighbor_input` JSON path,
a NEW `out` path and optional `maximum_operations`. The prepared JSON declares:

| Field | Required meaning |
| --- | --- |
| `schema_version` | 1 |
| `source_profile` | `monocle3-1.0.0-004c096f3a3bdaf1e6709a65fb36f40ba775f1ab` |
| `neighbor_graph` | Explicit `knn` or `principal_graph` |
| `ranking_provenance` | Actual original ranking generation, inputs and settings |
| `cell_ids` | Complete literal cell axis |
| `original_ranking_zero_based` | Complete ranking matrix, including first column |
| `principal_ids` | Principal-mode complete point axis in dp_mst column order |
| `cell_to_principal_zero_based` | Principal-mode complete memberships |
| `principal_edges_zero_based` | Principal-mode complete undirected endpoint pairs |
| `principal_graph_provenance` | Principal-mode source/settings for that graph |

Unknown fields, substituted editions, duplicate axes/ranking indices, incomplete
populations and out-of-range indices are refused. Paths inside requests resolve
relative to the request file. Use an ASCII request pathname on Windows; UTF-8
labels and JSON data paths remain literal. Profile/provenance declarations do
not authenticate a caller's ranking producer.

The CLI accepts at most64MiB per prepared input and128MiB per recorded output file,
one million cells,16million
ranking entries and a declared logical-work capacity (default50million, CLI
maximum2billion). These are logical bounds, not heap, disk or wall-time quotas.
The result is sparse; no cell-by-cell dense adjacency matrix is allocated. Full
atlas execution at every bound is not established by the synthetic controls.

## Results and recovery

Read `analysis.json`, `manifest.json`, `weights.json`, `weights.tsv`, warnings and
`COMPLETED.json`. Input bytes are captured, checked again before completion, and
preserved. Existing output and concurrent foreign input bytes are never replaced.
Weight output uses `weights_admitted`, while `scientific_test_admitted` remains
false. No expression model is fit by this stage.

**SKIP3: WEIGHT PREPARATION SKIPPED.** Supply named accessible permitted paths and
retry NEW output. The Java stage installs and executes no R software. For a fresh
original reference, provision the exact permitted runtime described below; if
installation or execution is disallowed, retain the skip and state what is missing.

**FAIL2: NO COMPLETE WEIGHT GRAPH ADMITTED.** Preserve raw inputs, errors, original
warnings and partial outputs; read `FAILURE.txt` and `WARNINGS.md`, correct the
named population, index, original-source error, capacity or integrity cause, then
retry NEW output. Never change tie order, delete self-links, reorder cells or
remove testing genes to make a downstream method pass. A JVM/process interruption
without complete admission markers supplies no completed result.

The existing MORAN-LM operation refuses self-links. A source-selected self-link
is a reported compatibility boundary, not permission to remove it. The public
example separately checks a complete loop-free source control against original
spdep inference for all three genes and21 fields.

## Original runtime and verification

An unmodified original1.0.0 source build is now installed in a NEW private library
over the existing R4.3.3 dependency base. All preexisting protected library,
GenomeInfoDbData and source-file hashes remain exact. Eleven selected original
function bodies/formals match. This is a present-day declared dependency profile,
not a reconstructed historical paper environment. Dense matrix input to the
original constructor fails under this R version; the retained retry explicitly
uses complete sparse counts with identical numeric values. Original source is
never patched.

The local provisioning command, from the publication checkout, is:

```powershell
python -B tools/literature/monocle100/setup.py --base D:/projects/JLinAlg/build/literature-review/monocle-runtime/monocle133-reference-setup-1 --source-candidate D:/projects/JLinAlg/build/literature-review/monocle-runtime/source-intake-1/1.0.0 --genome-library D:/projects/JLinAlg/build/literature-review/monocle-runtime/monocle133-original-install-1/R-lib --out build/NEW-monocle100-runtime --allow-install
```

This is the current Windows/WSL host recipe. Inspect paths and use NEW output.
Restore the pinned source and dependency base first; the older base's installer
is documented in the [1.3.3 guide](MONOCLE133_GRAPH_DEVELOPMENT.md). The source
profile, library order, runtime paths and installed-file census are retained in
the new setup receipt. Analysis commands do not install software automatically.

The normal producer and each Windows21/25/Linux21 packaged JVM pass636 focused
tests:77 new API/CLI controls and559 preceding regression/root controls. All
22,378 preceding JAR payloads remain byte-identical; five classes are added.
The public example passes on all three hosts, retaining all74 exact original
weights and matching21 original-spdep fields within2e-12 absolute/relative
tolerance. Fresh all250 XML/DOI checks retain the nine Monocle source roles.

Use the [saved checkpoint](monocle100-weights-checkpoint/README.md) for source pins,
retained failed attempts, raw evidence and the portable reader. It reads saved
bytes without Java/R execution. Catalog admission and the remaining Monocle
roles, nearest-neighbor/graph integration and full250-paper objective remain open.
Both literature reports remain off Pages; no release is created.
