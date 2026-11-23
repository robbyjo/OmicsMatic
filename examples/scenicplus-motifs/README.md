# Original motif enrichment and cistrome preparation

Copy [request.json](request.json), replace every absolute path, and deliberately
choose the species, annotation edition, foreground/background sets, thresholds
and DEM seed. These are example settings, not settings recovered from a paper.
Use the complete source-built JAR and the exact permitted private Linux runtime.

```bash
java -cp /absolute/path/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.ScenicPlusMotifsCli --request /absolute/path/request.json
```

The prepared MuData must contain `scRNA` and `scATAC`, for example the output of
the [unpaired preparation adapter](../scenicplus-prepare/README.md). It supplies
the assayed region universe and expressed-gene universe; this stage does not fit
RNA or accessibility models. Supply matching local motif-to-TF annotations and
Feather-v2 ranking/score databases. No licensed reference or runtime is included.

`region-sets.tsv` has this exact tab-separated header and zero-based half-open
coordinates. The labels used in the JSON must exist in this table. Use complete
region sets for your analysis; these two lines demonstrate the format only.

```text
set	Chromosome	Start	End
Tcell	chr1	1000	1200
Bcell	chr1	2000	2200
```

**Warnings and remedies:** read `MOTIF-WARNINGS.md`, `worker/out/SUMMARY.json`,
`results.json`, `source-warnings.json`, `field-ledger.tsv` and private
`worker/report.json`. Exit 3 means restore the exact permitted prerequisite or
retain the skipped analysis. Exit 2 means no complete result: retain evidence,
resolve the named cause and retry a NEW output. Empty enrichment stays explicit.
With promoter balancing off, the original source ignores `max_bg_regions`;
the example requests no cap. A requested ignored cap produces a prominent warning
with the actual background size. No silent subsampling or vendor patch is used.

The original merger fails if either direct or extended cistrome categories are
entirely empty, including an all-empty enrichment run. This remains FAIL2;
retain the empty enrichment evidence and skip the dependent network. No empty
cistrome is invented. A mix of empty and nonempty analyses can complete when both
annotation categories remain available.

The example explicitly requests `export_network_inputs`. On success,
`network-expression.tsv` and `network-accessibility.tsv` use the network adapter's
`cell` header and exact float64 promotion, with original ordered labels and no
normalization. Their source/promoted dtypes appear in `network-export-bridge.json`.
This requires identical ordered RNA/ATAC cells; no pairing is inferred. Set the
option to false for motif/cistrome preparation without these matrix exports.

ClusterBuster genome scanning is skipped pending permission for an exact runtime.
This profile analyzes prepared databases. Captures and source-bearing traces stay
private under the accepted terms. See the [workflow reference](../../docs/literature/SCENICPLUS_MOTIF_WORKFLOWS.md)
for all parameters, fresh VERIFY/REPLAY and the exact limits.
