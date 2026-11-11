# CellChat workflows and database resources

`cellchat-tools` connects the tested original CellChat workflows to the main
OmicsMatic command. Native Java methods check or implement prepared RNA scores,
PPI projection, source network similarity and strength contrasts. Original R
remains authoritative for its statistical and graph methods; the original
network workflow also requires its pinned Python runtime.

**Require exit 0, `result_admitted=true`, successful complete `--verify`, and
review of `WARNINGS.md`, original output, provenance and parent warnings.**
This integration does not establish biological validity or historical paper
reproduction. Source candidates and all scientific choices remain explicit.

## Warnings, skipped analyses and required actions

| Situation | What to do |
| --- | --- |
| `SKIP3` / exit 3 | **Skip the dependent analysis.** Supply/download/install the exact named prerequisites only where permitted, in new private paths. If unavailable or forbidden, keep analysis skipped. Analysis installs nothing. |
| `FAIL2` / exit 2 | **Admit no new result**, including earlier successful steps. Preserve the attempt, fix the named cause and retry a new output directory. |
| Invalid original p-fields | Keep the recorded values and causes. Negative p-values are invalid; source success does not make them valid estimates. |
| Original graph or `rankNet` fallback zero | Preserve the zero and its cause. It does not establish a successfully estimated statistic or independent donor replication. |
| FNN/SNN anomalies | Keep original duplicate/omitted neighbors and similarity values outside `[0,1]`. Do not deduplicate, rerank, clip or rescale them silently. |
| Missing/nonfinite strengths or tests | Preserve `NA`, `NaN` and `Inf` with their dispositions. A missing feature/group is not a measured zero. |
| Original parallel RNG warning | Source automatic clustering uses random draws without declaring `future.seed`; warnings report potentially invalid random-number use. Preserve warnings/clusters. Explicit original sequential clustering is a separate analysis. |
| UMAP variability | Original `runUMAP` omits constructor `random_state`; separate default runs yielded different coordinates and co-clustering despite source seeds. Neither seeds nor sequential R clustering establish exact whole-pipeline replay. Do not patch the source or silently change engine/metric/orientation. |
| Unresolved P060 custom score | **Skip that study-score analysis** until its exact component-product aggregation is supplied. Resource export is supported; Hill communication probabilities do not replace the custom score. |

Warnings/actions are printed even if failure precedes output creation. Owned
processes are supervised; timeouts stop and await them. Failed quarantine is
reported explicitly and never grants admission. Input/output capacities are
logical limits, not host RAM/disk quotas or producer authentication. Full-network
PPI checks may take substantial time and memory; do not truncate a network to
make validation pass.

## Run the main command

The root supplement preserves the previous root's scientific classes and adds
unchanged CellChat classes/resources. It requires the separately recorded
canonical dependency. From the repository root with Java 17 or later, on Windows:

```powershell
java -Xmx2g -cp "docs/literature/cellchat-catalog-development/cellchat-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar" org.jlinalg.cli.JLinAlgCli cellchat-tools --help
java -Xmx2g -cp "docs/literature/cellchat-catalog-development/cellchat-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar" org.jlinalg.cli.JLinAlgCli cellchat-tools strength --request C:/work/cellchat-strength/request.json
java -Xmx2g -cp "docs/literature/cellchat-catalog-development/cellchat-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar" org.jlinalg.cli.JLinAlgCli cellchat-tools strength --verify C:/work/cellchat-strength/result-new
```

On Linux use `:` as the classpath separator. Portable complete saved-bundle
verification needs Java without R/Python installation. New original analyses
require the exact tested Windows runtime. The canonical released JAR is unchanged;
placing this supplement first on the classpath selects the new root command.
Keep the required dependency and check the recorded hashes. No release or hosted
deployment is claimed.

A fresh checkout may lack that exact canonical dependency. In that case, use
the self-contained standalone family JAR linked in the guides below. For example:

```powershell
java -Xmx2g -jar docs/literature/cellchat-strength-development/cellchat-strength-javac21.jar --request C:/work/cellchat-strength/request.json
java -Xmx2g -jar docs/literature/cellchat-strength-development/cellchat-strength-javac21.jar --verify C:/work/cellchat-strength/result-new
```

It accepts the same family request without the root command prefix. Root
supplement compatibility is verified for its recorded canonical dependency.

Choose one family and follow its guide to prepare explicit inputs, runtime and
request. Replace the example's `out` with a **new absolute directory** and
`runtime_profile` with the correct **absolute profile path**. Keep prior attempts.

| Family | Available method family | Request and runtime guide |
| --- | --- | --- |
| `inference` | Original 1.0.0 or 2.1.2 overexpression selection, projection/smoothing, condition inference, optional filtering, pathways and centrality | [Inference guide](CELLCHAT_INFERENCE_DEVELOPMENT.md) |
| `legacy` | Original 1.1.3 two-condition inference, merged functional/structural similarity, information flow, mapped differential expression and bubble plots | [Legacy comparison guide](CELLCHAT_LEGACY_WORKFLOW_DEVELOPMENT.md) |
| `network` | Original 1.1.3 pathway embedding, automatic/fixed kmeans clustering and plots on a complete verified `legacy` parent | [Network guide](CELLCHAT_NETWORK_DEVELOPMENT.md) |
| `strength` | Original 2.2.0 pooled conditions and each sample, LR/pathway incoming/outgoing node strengths, explicit strict contrasts and original Wilcoxon tests | [Strength guide](CELLCHAT_STRENGTH_DEVELOPMENT.md) |
| `workflow` | Original 2.2.0 pooled/per-sample inference with directed-edge comparison diagnostics | [Workflow guide](CELLCHAT_WORKFLOW_DEVELOPMENT.md) |

Families accept `--request JSON`, `--verify BUNDLE` and `--help`. Unknown families,
fields and unsupported profiles are refused. There is no automatic edition,
database/species, PPI, raw/projected-expression choice, UMAP engine, parallel
strategy or strength-difference direction. The directed-edge diagnostic does not
replace the node-strength method.

## Database resources and native Java methods

The [resource/native guide](CELLCHAT_DEVELOPMENT.md) supplies complete original
edition/species database exports using Python plus base R without installing
CellChat. Exports preserve interaction/complex/cofactor/geneInfo tables, row and
component order, literal identifiers and actual missingness versus literal `NA`.
Export does not merge databases, infer a historical release, select interactions
or implement a study's custom score. Its standalone verifier must pass.

The native APIs are `CellChatRna`, `CellChatProjection`, `CellChatNetworkSimilarity`
and `CellChatSignalingStrengths`. Their source-specific profiles and limits remain
documented in the component guides. The [projection guide](CELLCHAT_PROJECTION_DEVELOPMENT.md)
also provides a standalone native projection CLI. A prepared native component
alone does not run the original complete CellChat workflow.

## What the six reviewed papers actually used

| Paper | CellChat role and scope |
| --- | --- |
| [P060](https://doi.org/10.1038/s41586-023-06808-9) | Database resource with a custom imputed-expression score. Exact component-product aggregation is unresolved; skip that score analysis and retain the distinction from CellChat inference. |
| [P085](https://doi.org/10.1038/s41588-024-01961-x) | Original 1.1.3 condition inference, comparison, pathway embedding/clustering and plots. Caller-supplied CellTrek annotations do not make this coordinate-based CellChat spatial inference. Posted unevaluated chunks do not establish historical execution. LIANA and the Pearson comparison remain separate. |
| [P090](https://doi.org/10.1038/s41588-024-01952-y) | CellChat v1.0 defaults in R4.0.3. The tested original 1.0.0 candidate under R4.6.1 is available; the exact historical commit/environment is not recovered. |
| [P100](https://doi.org/10.1038/s41588-025-02407-8) | Original 2.1.2 communication input to FlowSig. The tested profile supports posted human Secreted Signaling, smoothData/PPI.human, projected-expression inference and no filterCommunication call. FlowSig remains separate. |
| [P145](https://doi.org/10.1038/s41588-026-02709-5) | Original 2.2.0 human protein signaling, pooled min.samples3/min.cells10, LR/pathway incoming/outgoing strengths, strict0.25 differences and sample tests. Difference sign is not recovered from the primary wording: request it explicitly. |
| [P167](https://doi.org/10.1038/s41588-025-02353-5) | Human database resource combined with CellPhoneDB/iTALK for a separate Spyrrow neighborhood score. Spyrrow remains missing in the catalog. |

Six source-bound reviews retain exact paragraph/DOI/XML hashes, the P085 figure
anchor and all prior reviews. Component evidence retains original reference runs,
native checks, complete workflows, warning cases, failed attempts and explicit
reproduction limits. The root supplement adds routing without changing those
scientific bytes. Frozen evidence verification checks integrity and recorded
relationships; it does not independently refit every model or authenticate a
producer.

Full CellChat package implementation, historical paper reproduction, separate
tool roles and the full 250-paper toolbox goal remain distinct. Earlier component
guides retain their historical checkpoint counts and pending-integration notes.
The current catalog is authoritative for overall availability.
