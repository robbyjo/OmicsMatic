# Prepared SCENIC+ artificial network example

These inputs are wholly artificial: 128 cells, 128 genes, 256 regions, three TFs,
240 candidate links and three motif rankings. The cistrome Boolean AnnData file
and Feather-v2 ranking database contain generated test data, not biological
references or vendor code. Cell labels `NA`, `0001` and Unicode labels are
intentional. The selected fixture seed is 713; inference seeds are 17 and the
example requests 100 GSEA permutations. No author settings are inferred.

The Java class `org.jlinalg.cli.ScenicPlusNetworkCli` calls the unchanged original
TF2G, R2G, GSEA eGRN, triplet-ranking and gene/region activity APIs. Use Linux/WSL,
a current complete source-built project JAR and an already permitted private
SCENIC+ v1.0a2 runtime. **Keep every generated capture private:** vendor traces
can include licensed source. Follow the accepted academic-use/transfer terms.

Choose an explicit interface. `CLI_TSV` uses the original CLI's intermediate
`pandas.read_table` defaults; `IN_MEMORY_API` passes returned frames directly.
Those parsing boundaries can change floating-point values. Choose the interface
the intended analysis used; the wrapper does not infer one from an unknown paper.

```bash
python3 examples/scenicplus-network/run.py \
  --jar /absolute/path/to/omicsmatic-0.4.0.jar \
  --python /absolute/path/to/permitted/environment/bin/python \
  --interface CLI_TSV --out /existing/private/parent/network-run

python3 examples/scenicplus-network/run.py \
  --jar /absolute/path/to/the-same/omicsmatic-0.4.0.jar \
  --mode VERIFY --analysis /existing/private/parent/network-run \
  --out /existing/private/parent/network-verify

python3 examples/scenicplus-network/run.py \
  --jar /absolute/path/to/the-same/omicsmatic-0.4.0.jar \
  --mode REPLAY --analysis /existing/private/parent/network-run \
  --out /existing/private/parent/network-replay
```

Both VERIFY and REPLAY actually repeat the computation into NEW disjoint witness
directories. Inspection alone checks saved closure. The driver creates a new
request beside the capture, passes an argument array and propagates Java's exit
code; it never downloads references, installs software or invokes WSL implicitly.

Read `WARNINGS.md`, `OUTCOME-WARNINGS.md`, `MODULE-WARNINGS.md`, the worker report,
complete source outputs, and numerical/module ledgers. Original merged modules
can omit GSEA statistics; their null values remain unknown. The known original
masked-call shape error remains FAIL2 and is not silently bypassed. SKIP3 means
the named permitted runtime/input must be restored or the dependent analysis
must remain skipped. FAIL2 requires preserving evidence, fixing the named cause
and retrying NEW output. No source patch or NaN/Inf substitution is applied.

This prepared core does not complete original unpaired/metacell preparation,
motif discovery/Cluster-Buster resource roles, global dispatch, catalog admission
or the 250-paper objective. It earns zero new family/review credit at this stage.
