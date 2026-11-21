# Explicit original Monocle3 1.0.0 graph example

This example starts from a **complete, prepared synthetic CDS** with 27 cells,
three genes, UMAP coordinates and named cluster/partition factors. LEARN runs the
original graph learning function. ORDER runs the original ordering function on a
supplied graph with an explicit root. RUN combines both. Native Java checks the
complete exported pseudotime against the original R result.

It does not preprocess counts, learn UMAP/clusters, infer a biological root or
perform a gene test. **P021 reports UMAT; this example uses literal UMAP and does
not reconstruct that paper's pipeline.** Source/input provenance is recorded in
PROVENANCE.json. Package version1.0.0 is an edition of Monocle3, not Monocle1.

Use the normal executable JAR produced by the matching source build. The entry
point is currently standalone. Actual RUN/REPLAY was tested with Linux Java21 and the
selected Linux x86_64 R4.3.3 runtime, original Monocle3 commit
004c096f3a3bdaf1e6709a65fb36f40ba775f1ab and the exact dependency versions declared
in `src/main/resources/org/jlinalg/omics/monocle100-graph/PROFILE.json`. Dependencies
must be installed in the ordered private/base libraries; this example installs
nothing. Windows users run these commands inside WSL using `/mnt/d/...` paths.

1. Copy runtime-template.json to a private location and replace every placeholder
   with an absolute Linux path for the exact permitted runtime. Keep its graph
   profile unchanged. A different Monocle or R edition is not interchangeable.
2. Set paths below and prepare requests in NEW output. The selected cell is an
   explicit synthetic demonstration root, not a biological direction.
3. Execute LEARN before ORDER, or use the separate combined RUN request.

```sh
JAR=/absolute/path/to/omicsmatic-0.4.0.jar
RUNTIME=/absolute/path/to/private-runtime.json
WORK=/absolute/path/to/NEW-graph-example
CLI=org.jlinalg.cli.Monocle100GraphCli

python3 -B examples/monocle100-graph/prepare.py \
  --runtime "$RUNTIME" --root-cell cell-001 --out "$WORK"

java -cp "$JAR" "$CLI" RUN --request "$WORK/learn-request.json"
# Inspect learn/results/principal-nodes.tsv and the original warnings.
java -cp "$JAR" "$CLI" RUN --request "$WORK/order-request.json"

# An independent combined fit/order from the same complete prepared input:
java -cp "$JAR" "$CLI" RUN --request "$WORK/run-request.json"
java -cp "$JAR" "$CLI" VERIFY --capture "$WORK/run" --out "$WORK/verified"
java -cp "$JAR" "$CLI" REPLAY --capture "$WORK/run" \
  --runtime "$RUNTIME" --out "$WORK/replayed"
```

The generated requests explicitly require convergence and preserve all original
controls. A graph that returns without that convergence is refused. Advanced
callers may explicitly choose `retain-original`; the result then retains a
prominent nonconvergence warning and cannot claim convergence. Disconnected cells
retain original `Inf` pseudotimes. Original source errors are retained.

Successful graph captures contain `COMPLETED.json` and `analysis.json` with
`graph_admitted=true`. Ordering additionally requires `pseudotime_admitted=true`.
LEARN has no pseudotime output. Complete raw CDS/model/RNG files, graph projections,
principal-node tables, pseudotime tables when applicable, source and process
receipts are retained. Read WARNINGS.md and ORIGINAL-WARNINGS.txt for every run.
REPLAY compares the complete scientific model, original ordering and RNG while
preserving the parent. Only igraph's private cache representation is canonicalized.

VERIFY works on Windows and Linux without R. It checks saved projections, model
member presence, complete file hashes, explicit roots, convergence and native
geodesic ordering; it does not parse RDS, refit a graph or authenticate a producer.

**SKIP3 means dependent analysis was skipped.** Supply the exact permitted
runtime/input or accessible path. Download/private installation is allowed where
the operating environment permits; if installation is forbidden, preserve the
skip and state what the user must supply.

**FAIL2 means no complete result was admitted.** Keep logs, original errors and
partial/raw outputs. Correct the named cause and retry NEW output. Never remove
guards, rename slots, recode factors, infer roots or drop cells to force success.
Graph/pseudotime output does not admit a gene test or verify a biological direction.
