# Monocle workflows in the source-build command

Current source builds expose the separately developed Monocle workflows through
one `monocle-tools` command. Select the operation and edition explicitly. The
four version-suffixed actions run the existing original-package workers and
retain their closed input contracts, complete populations and admission rules.
They do not substitute for one another.

```bash
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar monocle-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar monocle-tools MODELS-1.3.1 --help
```

On Windows use `./gradlew.bat executableJar`. This is source-build availability;
the released 0.4.0 JAR is unchanged. Earlier standalone qualification guides and
archives remain historical records. Their statements about the executable refer
to those checkpoints; the current entry points are listed here.

| Action | Analysis | Required input and runtime |
|---|---|---|
| `ORDER` | Native prepared-graph ordering | Explicit graph, coordinates, roots and source profile; Java only. |
| `RUN` | Original Monocle3 1.4.27 trajectory/model/stock Moran workflow | Raw counts and the separately qualified 1.4.27 runtime. The existing Windows profile remains distinct from the Linux profiles below. |
| `MODELS-1.3.1` | Original gene models and coefficient extraction; native probability, grouped Holm and optional Pearson audits | Complete prepared expression, typed metadata, explicit model/population and the exact separate Linux R4.3.3/1.3.1 profile. |
| `GRAPH-1.3.3` | Original graph learning and optional rooted ordering; native ordering audit | Complete caller-prepared UMAP and cluster/partition factors, roots when ordering, and the exact Linux R4.3.3/1.3.3 profile. |
| `ALIGN-1.3.7` | Original preprocessing, Batchelor 1.20.0 alignment, UMAP, clustering, graph learning and optional ordering | Complete prepared expression/metadata, explicit settings, and the exact Linux R4.3.3/1.3.7/Batchelor 1.20.0 profile. |
| `DIFFERENTIAL-2.26.0` | Original Monocle2 differential testing; native BH audit | Explicit response units, typed nested models and the separate Linux R 4.2.1/2.26.0 profile, including its private HSMMSingleCell library. |

Each action accepts `--request PATH.json`. The graph/alignment/model actions
also retain their own portable audit modes; their `--help` lists those contracts.
Auditing supplied output does not repeat original fitting or authenticate its
producer. Factor encodings, roots, expression units and complete testing
populations are explicit scientific choices.

## Run the native example

Create a **new** working directory. Copy
[`order.template.json`](../../examples/monocle-tools/order.template.json) to
`order.json`, and copy
[`prepared-graph.json`](../../examples/monocle-tools/prepared-graph.json) beside
it. From the repository root, run:

```bash
java -jar build/cli/omicsmatic-0.4.0.jar monocle-tools ORDER \
  --request /absolute/path/to/new-work/order.json
```

The request resolves its input and output relative to its own directory. The
expected cell pseudotimes in `[a,b,c]` order are `[3,0,5]`. Inspect
`ordered-result/analysis-status.json`, the selected-root ledger and `WARNINGS.md`.
An existing output is preserved and refused.

## Run an original-package workflow

Use the corresponding guide for its complete request, input representation,
exact private runtime provisioning and scientific limitations:

- [1.3.1 gene models](MONOCLE131_MODELS_DEVELOPMENT.md), including the original default-family/cleaning behavior and optional Pearson selection.
- [1.3.3 prepared graph](MONOCLE133_GRAPH_DEVELOPMENT.md), including LEARN versus RUN and explicit root selection.
- [1.3.7 batch alignment and trajectory](MONOCLE137_WORKFLOW_DEVELOPMENT.md), including complete required-model save/reload state.
- [Monocle2 differential testing](MONOCLE2_DIFFERENTIAL_DEVELOPMENT.md), including response units and known original-family failures.
- [1.4.27 and native ordering](MONOCLE_TOOLS_DEVELOPMENT.md), preserving the existing ORDER/RUN contracts.

Replace the guide's standalone invocation with the selected source-build action.
For example, in the Linux shell with an already prepared **Linux-path** request:

```bash
/usr/bin/java -Xmx2g -jar /absolute/path/to/omicsmatic-0.4.0.jar \
  monocle-tools ALIGN-1.3.7 --request /absolute/path/to/request.json
```

Windows users invoke the version-suffixed workflows through Linux Java in WSL,
using Linux paths to the JAR, request, inputs, private libraries and output.
Provision the declared runtime only where download/install is permitted. The
analysis command installs no software and silently selects no replacement
edition. A missing runtime means dependent analysis is skipped.

The 1.3.1 numerical guard supports both the original standalone JDistlib 0.10.2
archive and the main executable's bundled classes. It verifies all **933**
original `jdistlib/**/*.class` entries, including anonymous/helper classes,
against a manifest derived from the exact pinned upstream archive. Every
class's actual defining artifact must match; class-path shadowing is rejected.
The defining artifact is also checked before and after analysis. This is
content-integrity evidence, not a producer signature. The Java runtime and
unselected external accelerator libraries are not authenticated by this check.

## Warnings and required actions

**SKIP3 — dependent analysis skipped:** provide the exact permitted runtime,
libraries and source trees named in the selected profile. If installation or
execution is unavailable or forbidden, retain the skip and do not interpret
partial outputs as results. Unsupported editions are not replaced automatically.

**FAIL2 — no complete result admitted:** preserve original inputs, logs and
partial files; read `analysis-status.json`, `WARNINGS.md` and original worker
diagnostics, correct the named cause and retry a **new** output directory.
Original failed genes, `NA`/`Inf`, capacity failures and audit mismatches retain
their meaning. No rows or testing families are trimmed to make a run pass.

Require exit 0 and the appropriate admission flag. Inferential results and
pseudotime require `result_admitted: true`. A LEARN-only operation may admit its
graph/pipeline under the explicitly reported flag and still admit no pseudotime.
Read warnings even after success. Preserve full model/CDS directories and their
required indices, not only a single RDS file.

The paper-specific [source-use review](MONOCLE_SOURCE_REVIEW.md) remains relevant.
These routes do not recover unreported formulas, roots or historical commits;
stock Moran tests do not implement the P021 modified covariate-aware test.
The [new P021 method review](monocle-command-development/P021_METHOD_DISCOVERY.json)
traces that test to a regression-residual Moran primitive and records the next
native implementation target without executing or redistributing author code.
Cicero, py-monocle, protein-activity workflows and Seurat conversion have
separate scopes. No whole-Monocle or new catalog family credit follows from
making these existing workflows accessible in one source-build executable.
