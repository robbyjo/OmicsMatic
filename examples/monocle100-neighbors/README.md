# Original1.0.0 neighbors to Java weights and residual Moran

This complete synthetic example retains **27 cells,74 directed links and three
prepared response genes**. Original RANN2.6.2 computes rankings from a prepared
original Monocle3 1.0.0 CDS. Choose `original` to also execute unchanged
`calculateLW` and compare every weight with Java, or choose `java` to construct
weights in Java from those original rankings. No fallback is automatic. Neither
mode learns a graph or transforms expression. The original mode uses the original
dense feasible-space allocation; the Java mode avoids that allocation.

Build current source and run inside Linux or WSL with a permitted exact runtime:

```bash
./gradlew executableJar
python3 -B examples/monocle100-neighbors/prepare.py --runtime /absolute/path/runtime.json --engine java --out build/NEW-neighbor-example
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100NeighborsCli RUN --request build/NEW-neighbor-example/neighbors-request.json
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100NeighborsCli VERIFY --capture build/NEW-neighbor-example/neighbors --out build/NEW-neighbor-verification
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar monocle-tools MORAN-LM --request build/NEW-neighbor-example/moran-request.json
```

Repeat in NEW output with `--engine original` to exercise original `calculateLW`.
RUN and REPLAY require Linux Java,Python3.10+ and the declared exact R4.3.3 stack.
VERIFY is R-free on Windows and Linux. This is a standalone source-build command
in the normal executable; the old 0.4.0 release predates it. For replay:

```bash
java -Xmx2g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.Monocle100NeighborsCli REPLAY --capture build/NEW-neighbor-example/neighbors --out build/NEW-neighbor-replay --runtime /absolute/path/runtime.json
```

`runtime-template.json` gives the closed schema. Replace every `/opt/...` path
with your actual permitted Linux paths, preserving the ordered private/base R
libraries. The selected packages are Monocle3 1.0.0,RANN2.6.2,spdep1.4-1,
slam0.1-55,Matrix1.6-5,igraph2.0.3,jsonlite2.0.0 and digest0.6.37, on R4.3.3
`x86_64-conda-linux-gnu`. These present-day dependencies do not establish a
paper-era environment. The command checks the profile, seven original function
bodies/formals or source fingerprints, and selected executable/package-code
identities. It installs nothing. Source and runtime provisioning are separate.

Require exit0, `neighbors/COMPLETED.json` and `analysis.json` with
`weights_admitted:true`. The graph still has `scientific_test_admitted:false`.
The subsequent MORAN-LM command separately requires its own completion marker and
`result_admitted:true`. Read both `WARNINGS.md` files and `ORIGINAL-WARNINGS.txt`.
Original source-selected self-links stay present; the current MORAN-LM method
refuses them. This control has none.

The supplied `complete-cds.rds` contains synthetic counts plus the prepared graph
and UMAP coordinates. Neighbor calculation uses those coordinates/graph, not the
counts. The subsequent `expression.tsv` contains three separately prepared
synthetic linear-model responses, with explicit design/intercept/covariates.
They are not an inferred transformation of the CDS count assay. All27 cell IDs
are literal and aligned. Original-spdep references, checked by the preceding
bridge, cover all21 inference fields. This example's literal slot is **UMAP**;
P021's reported **UMAT** remains unresolved and must never be silently renamed.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named accessible exact permitted
runtime or input. If installation/execution is prohibited, retain the skip and
state which dependency must be supplied. General permitted download/private
installation authorization does not make another edition interchangeable.

**FAIL2: NO COMPLETE GRAPH OR RESULT.** Preserve partial/original/failed evidence,
correct the named data, source, capacity, numerical or process cause, and retry
NEW output. Do not change ties, remove cells/genes/self-links or weaken a source
guard to force a result. Complete captures include inputs, source, records and
exports in their byte/file limits; oversize JSON/table exports are refused before
completion. Address-space limits are hard per R process; sampled RSS/output/wall
checks are not hard aggregate memory or disk quotas. VERIFY checks projections,
selected distances and Java weights without RDS parsing or nearest-neighbor
refitting; it does not authenticate a producer. REPLAY checks complete original
nearest/listw/RNG objects and preserves the parent.
