# Original CellChat RNA workflow

This development checkpoint adds a typed Java controller for the original
CellChat2.2.0 package. It runs two pooled conditions and each biological sample
from explicitly supplied normalized expression, then retains communication,
pathway, network, centrality and sample-comparison results.

**CellChat remains under implementation in the catalog.** The original1.0/1.1.3/
2.1.2 workflows, PPI projection, merge/similarity/rankNet operations and final
catalog/root-command integration remain unfinished. Paper-specific comparisons
of incoming/outgoing centrality and pathway strengths also remain to be bound;
the supplied cross-condition table compares directed LR probability scores.
Counts stay **93 new scoped
integrations, 96 including existing adapters, and 248 missing tools**. The
canonical executable remains unchanged. Use the separate development JAR below.

The earlier [native calculations and database-export checkpoint](CELLCHAT_DEVELOPMENT.md)
remains available with its own frozen evidence.

## Warnings and required actions

**Require exit0, `result_admitted: true`, and successful `--verify`.** A worker
completion, installed package, RDS file or one successful fit is provisional.
Read `WARNINGS.md`, `analysis-status.json`, `provenance.json`, original stdout/
stderr, and each case's `original-centrality-fallbacks.tsv`.

Errors that occur before a new output directory can safely be created are
reported on stderr with the disposition and required action. Keep that log;
there is no admitted result.

- **SKIP3 — dependent analysis skipped:** an exact permitted runtime/source is
  unavailable. Supply the named WindowsR4.6.1, CellChat2.2.0, Presto1.0.0 and
  private-library/source requirements where installation is allowed. If it is
  forbidden, leave dependent analysis skipped. Analysis installs nothing and
  never substitutes a version or changes `do_fast`.
- **FAIL2 — no result admitted:** input, integrity, process, time, capacity or
  finalization failed. Earlier completed fits in that batch remain unadmitted.
  Preserve partial files, correct the named cause and retry **new output paths**.
  Failed worker/result directories are retained under `unadmitted-*`.
- **Original centrality fallback:** CellChat catches some `flowbet`/`info`
  failures and emits zeros. The wrapper retains those original zeros and records
  their causes. They do not establish successful graph estimates.
- **Original nonfinite test:** identical sample scores can give a nonfinite
  original Wilcoxon result. Its literal token and status are retained. It does
  not establish an association or a null result.
- **Comparison coverage:** an interaction/group not selected in a fit remains
  missing for comparison. It is never silently filled with zero. The complete
  table records untested and non-comparable rows.
- **Pooled versus sample scores:** the original filtering workflow can retain a
  positive pooled score while all corresponding selected per-sample scores are
  zero. Review the condition/sample tables before interpreting replicate support.
  Native provenance records the full count and up to five examples. These are
  raw filtered scores; the warning does not claim statistical significance.

Logical byte/file/time limits do not provide a hermetic host or hard process RAM/
disk quota. The original R algorithms perform the fits; native validation does
not independently refit their statistical or graph models.

## Run the supplied synthetic example

The example contains synthetic expression and six biological-sample labels, with
three samples per condition. It has no biological validation claim. Its
normalization provenance uses original `CellChat::normalizeData` with scale10000
and log1p. The example uses19 bootstrap permutations to keep control runs short;
choose and report an appropriate explicit `nboot` for an intended analysis.
Original CellChat2.2.0's default is100.

From the repository root, with the prepared runtime profile described below:

```powershell
java -Xmx512m -jar docs/literature/cellchat-workflow-development/cellchat-workflow-javac21.jar --request docs/literature/cellchat-workflow-development/example/request.json
java -Xmx512m -jar docs/literature/cellchat-workflow-development/cellchat-workflow-javac21.jar --verify build/literature-review/cellchat-runtime/public-workflow-example-1
```

Keep an existing output directory. Copy the request and change `out` for another
run. Relative paths resolve against the request's directory. The supplied request
points to this workspace's prepared `runtime-profile-1/runtime-profile.json`.
That profile binds absolute local paths and is not a portable execution runtime.
Completed-bundle verification does not need the original R installation.

The source includes matching payloads under
`src/main/resources/org/jlinalg/omics/cellchat-workflow`. The standalone JAR is
compiled for Java17. The current validation uses WindowsJava21/25 for original R
execution; portable verification also has separate LinuxJava21 checks.

## Prepare a runtime where permitted

The tested execution profile is WindowsR4.6.1 with original CellChat2.2.0 from
`jinworks/CellChat` commit `346fb615aaefa3c1a2830ec2fe3a933c5f723c83`, plus
original Presto1.0.0 tag commit `7636b3d0465c468c35853f82f1717d3a64b3c8f6`.
It does not identify either paper's historical commit or environment.

The workspace preparation recipe reuses its previously validated private R
compiler and dependency libraries. Its prerequisite records are
`build/literature-review/monocle-runtime/compiler-health-3/HEALTH.json` and
`reference-setup-3/STARTED.json`. Those prior bootstrap inputs are not an R
installation bundled with this JAR. A fresh checkout must first provide the
matching permitted compiler/dependency stack; otherwise skip analysis.

Existing completed setup directories should be retained. In a prepared workspace
without these outputs, the recipe is:

```powershell
python tools/literature/setup_cellchat_reference.py --allow-install --source-label 2.2.0-2025-release --out build/literature-review/cellchat-runtime/reference-setup-1
python tools/literature/intake_cellchat_presto_metadata.py --allow-download --out build/literature-review/cellchat-runtime/presto-metadata-1
python tools/literature/intake_cellchat_presto.py --allow-download --out build/literature-review/cellchat-runtime/presto-source-intake-1
python tools/literature/setup_cellchat_presto.py --allow-install --out build/literature-review/cellchat-runtime/presto-reference-setup-2
python tools/literature/export_cellchat_runtime_profile.py --out build/literature-review/cellchat-runtime/runtime-profile-1
```

CellChat source intake must already have completed, using the metadata/discovery/
intake commands in the earlier guide. Download/install flags are explicit. The
helpers use new private libraries and preserve the captured original source and
existing libraries. The author archive contains Linux object/shared-library
files; compilation cleans an owned copy and leaves the original tree unchanged.

Package setup checks actual native `ComputeSNN`, constructor and Presto Wilcoxon
calls. Presto's continuity-corrected normal approximation is checked against the
corresponding base-R calculation. The first incorrect uncorrected health
expectation is retained as a failed development attempt.

The runtime profile binds34099 declared files, including complete selected R
library populations and R bin/etc/modules. Every declared file is checked before
and after execution; extra library files are refused. This is a declared runtime
binding, not whole-host authentication. The profile and receipts record exact
dependency versions and original [CRAN](https://cran.r-project.org/) /
[Bioconductor](https://bioconductor.org/install/) download sources.

## Supply an analysis

Use the supplied request as a complete schema example. Unknown fields, duplicate
JSON keys, unsupported units and missing explicit parameters are refused.

- `expression` is literal UTF-8 TSV. Its header starts with `gene_id`, followed
  by exact cell IDs. Each remaining row gives one unique gene and nonnegative,
  finite normalized expression. No NA/NaN/Inf, duplicate aggregation or identifier
  repair is performed. Source gene symbols must match the chosen database.
- `metadata` has exactly `cell_id`, `group`, `sample`, `condition`, in that order.
  Rows define the cell order and must match expression columns exactly. Each
  biological sample belongs to one condition.
- `group_order`, `sample_order` and `condition_order` declare the complete literal
  populations. There are exactly two conditions; at least four samples are
  required by this controller. A complete-case Wilcoxon comparison needs at least
  two samples per condition. Missing groups/interactions are not imputed.
- `database_categories` explicitly selects original database annotation classes.
  The example excludes non-protein signaling, as reported for P145. Human, mouse
  and zebrafish resources are edition-bound choices; no species fallback occurs.
- `overexpression` declares positive-only selection, percentage/fold-change/
  p-value thresholds, `do_fast` and `variable_both`. Fast mode requires the pinned
  Presto runtime. Slow mode must be explicitly chosen.
- `inference` declares averaging, trim, population weights, bootstrap count, seed
  and Hill parameters. This checkpoint explicitly uses `raw.use=TRUE`, selecting
  normalized `data.signaling`; it does not mean raw counts. PPI smoothing and
  spatial constraints are outside this command.
- `condition_filter` and `sample_filter` are separate. The example uses
  `min_samples: 3` for each pooled condition and `min_samples: 1` per sample.
  Original `min_cells` excludes counts **less than or equal to** its value.
  The optional preliminary group filter instead removes counts strictly below
  `prefilter_min_group_cells`; these boundaries remain distinct.
- `comparison` declares a strict treated-minus-control cutoff, Wilcoxon
  alternative, exact/asymptotic choice and continuity correction. No paired test
  or multiple-test adjustment is inferred. Thresholding strengths is not a
  replacement for the separately reported statistical test.

The original package performs overexpression and ligand/receptor selection,
communication inference, filtering, pathway inference, aggregation and centrality
for every fit. The complete batch is admitted only after all fits and native
audits pass. Original statistical/graph results, errors and fallbacks are retained.

## Inspect the result

`results/cases/condition-*` and `results/cases/sample-*` contain original fitted
CellChat RDS objects, selected interactions, overexpression tables, unfiltered/
filtered probability cubes, pathway/network tables, centrality, cell/gene
dispositions, RNG states and source fallback diagnostics. RDS objects are
retained outputs, never accepted as arbitrary executable request inputs.

`results/comparison/pooled-and-sample-probabilities.tsv` retains every union key.
It distinguishes an original numeric zero from an unselected/missing interaction
and records original nonfinite test results explicitly. `provenance.json` reports
coverage and discrepancies without changing original results.

Original threshold conventions differ: aggregate networks and centrality use
strict p<threshold; pathway aggregation retains p=threshold. Native audits check
those distinct boundaries, full ordered axes, exact permutation-count transports,
size filters, weights/counts, pathway sums and incoming/outgoing strengths.
Other original graph measures are retained and compared against separate direct
package refits in the controls, rather than independently estimated in Java.

`--verify` reads retained files and replays native consistency checks. It launches
no R process and checks no unavailable producer paths. Hashes provide integrity;
they do not authenticate a producer or establish biological accuracy.

## Validation and remaining scope

The checkpoint retains complete synthetic workflows on Java21/25, eight separate
direct original-package refits, exact ten-cell/three-sample boundary families,
nonfinite and comparison-coverage cases, and actual-JAR disposition/tamper tests.
Read the frozen `VALIDATION.json` beside the JARs for exact current counts and
the evidence shard hashes. Prior failed attempts and source snapshots remain
retained; they are not promoted to successful analyses.

Keep `VALIDATION.json`, both JARs, the example directory and all numbered
`evidence-*.zip` shards together. Repeated files are stored once by SHA256; the
manifest retains every original evidence path. To verify the capsule and restore
one complete bundle into a new directory, with Python3.11+:

```powershell
python tools/literature/cellchat_workflow_evidence.py
python tools/literature/cellchat_workflow_evidence.py --materialize evidence/typed-controls-7/analysis --out build/literature-review/cellchat-runtime/materialized-example-1
java -Xmx512m -jar docs/literature/cellchat-workflow-development/cellchat-workflow-javac21.jar --verify build/literature-review/cellchat-runtime/materialized-example-1
```

Those verification/materialization commands install nothing and execute no R.
The capsule includes current result bundles, original source trees, separate
direct refits, source snapshots and failure evidence. It does not bundle the
existing R installation/compiler/dependency libraries. Their fingerprints and
preparation records are retained. Older successful prototype bulk outputs remain
in the workspace, with historical receipts/source snapshots in the capsule.

The source boundary experiments removed54 pooled VEGFA edges using the
three-sample filter and1428 per-sample rare-group edges at exactly ten cells.
They retained448 positive pooled scores with zero corresponding selected sample
scores,48 untested sample-comparison rows and224 nonfinite sample tests. A
separate constant-score family retained315 original nonfinite Wilcoxon results.
These are synthetic software checks, not biological findings.

The other CellChat paper roles remain distinct: P060 uses a database with a
custom score; P085 uses1.1.3 plus projection/condition-comparison operations;
P090 reportsv1.0 defaults in R4.0.3; P100 reports2.1.2 feeding separate FlowSig;
P145 uses pooled and per-sample2.2.0 inference; P167 uses the human resource in
separate Spyrrow neighborhood analysis. This checkpoint supplies no substituted
execution for those remaining roles and does not complete the250-paper goal.

CellChat and Presto original sources declare GPL3. Source notices and the complete
GPL3 license are retained with the evidence; see the
[CellChat notice](CELLCHAT_UPSTREAM_NOTICE.txt),
[CellChat repository](https://github.com/jinworks/CellChat) and
[Presto repository](https://github.com/immunogenomics/presto).
