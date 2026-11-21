# Monocle1.0.0 prepared-CDS neighbor workflows

The source-built executable supplies standalone
`org.jlinalg.cli.Monocle100NeighborsCli` RUN/VERIFY/REPLAY. Start with the
[complete synthetic example](../../examples/monocle100-neighbors/README.md).
This workflow computes original RANN2.6.2 nearest neighbors from a complete,
caller-prepared Monocle3 1.0.0 CDS. It does not learn a graph, choose roots,
transform a response, encode covariates or perform a statistical test.

Choose an explicit engine:

| Engine | Executed operations | Scientific boundary |
| --- | --- | --- |
| `original` | Original RANN, then original1.0.0 `calculateLW`; Java checks every selected neighbor and binary64 weight | Preserves the original dense feasible-space allocation and original failures |
| `java` | Original RANN, then the tested native Java selection/weight API | Avoids the dense feasible-space allocation; does not execute or claim an original `calculateLW` return |

No engine fallback is automatic. KNN retains original rank order after dropping
the first ranking column literally. Principal-graph mode uses the complete source
graph, original principal-point axis, explicit closest-point membership and
source-compatible gating/order. Weights are binary-neighbor W-style row-standard
weights, not Jaccard similarities. Tied-coordinate self-links remain present and
are prominently reported. The existing MORAN-LM method refuses those self-links.
Observed original singleton-column, single occupied factor level and all-scalar
SLAM cases remain failures rather than silently repaired graphs.

## Inputs and commands

RUN requires a UTF-8 JSON object with these closed fields:

```json
{
  "schema_version": 1,
  "source_cds": "complete-cds.rds",
  "runtime": "runtime.json",
  "engine": "java",
  "neighbor_graph": "principal_graph",
  "k": 5,
  "reduction_slot": "UMAP",
  "seed": 100021,
  "dataset_provenance": "All prespecified cells/genes and the explicitly prepared embedding/graph",
  "out": "NEW-output"
}
```

Relative paths resolve against the request file. `neighbor_graph` is `knn` or
`principal_graph`; `k` is an integer1..1,000,000 and seed0..2,147,483,647. The
literal reduction slot must exist in the supplied CDS. **Do not replace P021's
reported UMAT with UMAP.** The synthetic example independently declares UMAP.
The input is a complete original1.0.0 `cell_data_set`, not a wrapper, subclass or
newer-edition object. It must have unique complete cell/gene axes, an in-memory
`dgCMatrix` counts-only assay and complete finite selected coordinates. Principal
mode also requires the complete named undirected graph, `dp_mst` and original
numeric one-column closest-vertex matrix. No assay or cell is discarded.

After building current source, use the exact argument order:

```bash
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100NeighborsCli RUN --request request.json
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100NeighborsCli VERIFY --capture NEW-output --out NEW-verification
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100NeighborsCli REPLAY --capture NEW-output --out NEW-replay --runtime runtime.json
```

RUN/REPLAY require Linux Java, Python3.10+ and the exact Linux R4.3.3 profile in
the example's [runtime template](../../examples/monocle100-neighbors/runtime-template.json).
Use actual accessible Linux paths and the declared ordered libraries. Selected
packages are Monocle3 1.0.0,RANN2.6.2,spdep1.4-1,slam0.1-55,Matrix1.6-5,
igraph2.0.3,jsonlite2.0.0 and digest0.6.37. This is a present-day dependency
profile, not a reconstruction of the paper's historical environment. VERIFY
requires neither R nor Linux and treats stored runtime paths as provenance.
Analysis performs no installation. Permitted source/runtime setup is separate;
the [preceding original-source setup](MONOCLE100_CHECKPOINT_2026-11-21.md) records
the local source/library owner. The old release predates this command.

## Complete capture and verification

Successful RUN output includes the captured request/runtime/CDS and worker source,
original nearest-neighbor RDS plus exact numeric JSON, complete scientific input
projections, before/after selected runtime-code hashes, native-module identities,
RNG RDS, original warnings and process receipts. The original engine additionally
retains the full original listw RDS and exact17-digit weights. `results/weights.tsv`
is the literal-cell edge table for prepared-design MORAN-LM; `weights.json` and
`audit.json` preserve its complete numeric projection.

Require **exit0, `COMPLETED.json` and `analysis.json` with `weights_admitted:true`**.
`scientific_test_admitted` stays false. A graph is not an admitted gene test.
Use a separate explicitly prepared response/design and separate test admission.

VERIFY checks the full captured file inventory, packaged worker/reference bytes,
input/output axes and projections, original selected Euclidean distances, Java
selection/weights, original weight equality when applicable, and coherent
admission/cleanup records. It does not parse RDS, refit nearest neighbors or
authenticate the producer. A resealed checksum is not proof of computation.
REPLAY reruns original neighbors in NEW output and requires complete original
nearest/listw/RNG object identity, while rechecking and preserving its parent.

Seven original functions are checked against compatible original reference
bodies/formals or a private-source fingerprint. The SLAM fingerprint uses explicit
serialization version2 for the selected ASCII source objects. Version3 records
the native encoding, as described in the [R serialization manual](https://www.stat.ethz.ch/R-manual/R-devel/doc/manual/R-ints.html#Serialization-Formats).
The retained C/UTF-8 diagnosis shows strictly identical original/loaded objects,
different version3 hashes and matching version2 hashes. Deliberately changed
body/formals are refused. Earlier version3 failures remain retained; expected
hashes in historical owners are unchanged. SLAM GPL-2-only source is not
redistributed with this GPL3 project. Selected compatible Monocle/RANN/spdep
source, original notices and full-source archive references remain retained.

## Capacity and failure recovery

Optional request capacities have explicit bounds. Defaults: CDS2GiB,
JSON/table64MiB, complete capture2GiB,10,000 files,1,000,000 cells,
16,000,000 rank entries,50,000,000 native logical operations,600 seconds,
8GiB R address space and8GiB sampled aggregate RSS. Accepted maxima are
CDS32GiB, JSON256MiB, complete capture1TiB,1,000,000 files,
1,000,000 cells,16,000,000 rank entries,2,000,000,000 native operations,
86,400 seconds and1TiB for each memory limit. No prefix or subset is admitted.
Complete byte/file limits include captured inputs, source, records, exports and
the pending completion marker. JSON/table export sizes must fit VERIFY's read
limit before admission. Java heap and storage availability remain independent.

The Linux supervisor owns the R process group, monitors Java-parent identity,
time, sampled RSS and complete-capture growth, and records TERM/KILL cleanup.
The R leader requests a kernel parent-death signal. R address-space limits are
hard per process; sampled checks are not hard aggregate memory/disk quotas.
Self-detaching processes are outside this execution profile. Process-only
controls do not establish a scientific original-R run.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named exact permitted runtime,
executable, accessible input or path. If installation/execution is prohibited,
retain the dependent skip and clearly state what must be supplied. General
permitted download/private-install authorization remains in effect.

**FAIL2: NO COMPLETE GRAPH.** Retain raw/partial/original/null/failed evidence,
`analysis.json`, `FAILURE.txt`, `worker/error.json`, original error/RNG objects,
stdout/stderr and process receipts. Correct the named input, source, numerical,
capacity or process cause and retry NEW output. An incomplete saved capture is
FAIL2; it is not a missing-runtime SKIP3. Do not weaken guards, substitute
editions, remove cells/genes/self-links or repair identifiers to force success.
Whole Monocle/P021 pipelines and remaining paper-use review stay separate.
