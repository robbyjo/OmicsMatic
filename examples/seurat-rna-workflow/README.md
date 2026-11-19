# Captured Seurat RNA example

This example uses **300 synthetic genes and 96 synthetic cells**, including
exact Unicode IDs and a zero-expression gene. It does not infer cell types or
reproduce a biological paper. Read the [workflow guide](../../docs/vignettes/seurat-rna-workflow.md)
and its warnings before using real data.

Use a current source-build JAR containing
`org.jlinalg.cli.SeuratWorkflowCli`; the older public 0.4.0 release lacks this
entry point. The qualified development JAR on the author workstation is
listed in the [checkpoint](../../docs/literature/SEURAT_WORKFLOW_CHECKPOINT_2026-11-19.md).

1. Extract `synthetic-inputs.zip` into a **new** working directory. It contains
   `inputs/counts.mtx`, `features.txt`, `cells.txt` and `metadata.tsv`.
2. Copy `request.json` into that directory. Set `rscript` and `r_libraries` to
   your permitted, separately prepared exact runtime profile. The example
   contains placeholders; it does not install software or change a library.
   Keep the request UTF-8 without a byte-order mark.
3. Run the following with your actual JAR and request paths:

```powershell
java -Xmx2g -cp "D:/path/to/current/omicsmatic-0.4.0.jar" org.jlinalg.cli.SeuratWorkflowCli --request "D:/path/to/new-example/request.json"
```

The example uses LogNormalize, VST selection of 80 variable features, scaled
RNA, exact PCA with ten requested PCs, RANN with 12 neighbors, two Louvain
resolutions, UMAP and t-SNE. `out` is `run-1`, relative to the request JSON.
An existing output directory is refused. Supply sufficient JVM/R memory;
the explicit capacities are admission checks, not OS memory quotas.

Read `run-1/WARNINGS.md` and `analysis-status.json` first. A usable capture
requires `results_admitted: true` and a successful verification:

```powershell
java -Xmx2g -cp "D:/path/to/current/omicsmatic-0.4.0.jar" org.jlinalg.cli.SeuratWorkflowCli --request "D:/path/to/new-example/run-1/verify-request.json"
```

VERIFY needs Java and the same source-profile resources, not R. It rechecks
capture/identity consistency, native normalization/no-regression scaling, PCA
relations and SNN overlaps. It does not independently refit the original
models or authenticate a fabricated capture. Public embeddings, loadings,
clusters and the complete feature ledger restore the original exact IDs.
Raw worker files use explicitly mapped transport codes.

For a fresh original-model replay, save this as a new UTF-8 request in the
working directory and invoke the same entry point:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"run-1","out":"replay-1"}
```

REPLAY first verifies `run-1`, retains it unchanged, then executes original R
again into a new output. It uses the recorded runtime by default; explicit
`rscript`/`r_libraries` overrides are allowed and captured. Byte equality is
not promised across runtime/host/input changes. On the qualified machine the
tested replay's selected published views were byte-identical.

**SKIP3 means the affected analysis was skipped.** Supply the named exact
permitted runtime/package, or leave it skipped. **FAIL2 means no complete
result is admitted.** Retain inputs, partials and logs; correct the named
input/model/source/capacity cause and retry into a new path. Do not discard
cells, replace nonfinite values or switch requested backends just to obtain
a success status. A failed/skipped capture cannot pass VERIFY.
