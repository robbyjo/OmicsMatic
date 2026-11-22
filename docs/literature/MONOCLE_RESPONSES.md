# Native Monocle response preparation and explicit stage commands

`monocle-tools RESPONSES-1.0.0` prepares complete supplied responses using Java,
with optional supplied detected-gene metadata and explicit design assembly.
It supports RUN, recalculating VERIFY and parent-preserving REPLAY. The same
global dispatcher now exposes the previously qualified original 1.0.0 graph,
neighbor and native weight stages as GRAPH-1.0.0, NEIGHBORS-1.0.0 and WEIGHTS-1.0.0.
Each stage retains its own closed contract, exact edition and admission flags.

Start with the [complete synthetic example](../../examples/monocle-responses/README.md).
It includes checked inputs, explicit requests and copyable native preparation,
verification, replay and separate residual-Moran commands. No R runtime is needed
for response preparation, native weights or residual-Moran testing. Original
graph/neighbor execution still requires its exact permitted private R runtime;
verification of those captured stages remains portable and R-free.

## Model and source scope

Explicit `size-factor-log10-plus-0.1` computes
`log10(expression / supplied_size_factor + 0.1)`. `as-is` preserves supplied finite
responses and refuses an ignored size-factor input. This follows the exact branch
in official Monocle3 package 1.0.0 `graph_test.R`: the uninormal/binomialff labels
take the as-is branch; the other source branch applies the supplied factor/log
arithmetic. The label does not fit a quasi-Poisson GLM. Native requests select the
arithmetic explicitly, rather than infer it from a gene-family or assay label.

Optional detected-gene metadata is a complete supplied pre-filter nFeature_RNA
population, with explicit provenance. Its natural log is emitted separately.
It is never recomputed from the selected gene matrix. Optional numeric base-design
columns are copied literally and `log_n_genes` appended. Intercepts, factor coding,
sample selection, missingness and size-factor estimation are explicit caller
responsibilities. Rank and residual inference belong to the downstream test.

The implementation preserves literal gene/cell IDs, including quotes, spaces,
Unicode and `NA`. It refuses duplicate, reordered, missing, ragged or nonfinite
inputs. Raw decimal metadata is checked before it could silently round an invalid
integer to a representable double. Positive integral counts are bounded by 2^53.
Limits cover the complete population, optional design work, captured files and
pending completion marker; no prefix result is admitted.

RUN captures and pins the source request and every declared input. VERIFY checks
the complete capture, closed markers, effective request, exact input population,
all recalculated outputs and admission metadata. REPLAY recalculates into NEW
output and checks complete response/design equality while preserving the parent.
Neither depends on the original input paths remaining available. SHA256 provides
integrity, not authentication of an external producer.

**Preparation admits no gene test, biological direction or whole-paper result.**
It estimates no graph, assay, root, cell population, feature selection or covariate
coding. P021 says UMAT; this component does not rename it UMAP or establish the
paper's executed author commit, complete preprocessing, graph settings or recipe.
The privately inspected author fork was not executed or redistributed.

## Warnings, failures and the retained source difference

Require exit 0, `COMPLETED.json` and `response_preparation_admitted=true` before
consuming preparation outputs. Its `scientific_test_admitted` stays false.
For the separate MORAN-LM analysis, require `result_admitted=true` and its own
completion. Read each stage's WARNINGS.md and any numeric warning.

**A finite original R statistic does not guarantee resolved residual variation.**
One retained as-is control has `y=-4+(48/7)*trend` in the supplied intercept/trend
design. Original R's saved residual-to-response norm is about 3.61e-16, while the
unchanged native resolution threshold is about 2.84e-14. R returns a finite
greater-tail p of about 0.126 from rounding residuals. Native MORAN-LM refuses
the entire four-gene family for each of the three alternatives. All original
returns are retained as source evidence. No gene is removed, original return
replaced or guard weakened to force agreement. The preparation itself completes.

**SKIP3:** supply the exact permitted runtime/input/path named in the error and
retry NEW output. Download/install authorization applies where the operating
environment permits it. If a needed installation is prohibited, skip that
dependent analysis and state the affected analysis, reason and item to supply.
**FAIL2:** preserve raw inputs, logs, errors and partials; correct the named cause
and retry NEW output. Do not use incomplete output, repair nonfinite values,
recode IDs, infer missing metadata or substitute tool editions to force success.

## Source identity and recorded validation

The official R reference evaluates the unchanged response-expression AST and
calls original spdep 1.4-1 residual-Moran inference. It does not invoke full
Monocle graph_test or an author fork. Selected function bodies and formals must
strictly match independently parsed official source in their expected namespace
environments, before and after the run. Six changed owned-function controls
(body, formals, environment for each selected function) are refused. Installed
functions and runtime source are not modified.

The previous raw-body hash guard failed because R 4.3.3 serializes argument-list
matching state: `substitute()` calls `matchArgs_NR`, which records ARGUSED in
the pairlist LEVELS field. A minimal unchanged `function(listw) substitute(listw)`
reproduces the one-byte change while strict body/formal equality remains true.
The exact R 4.3.3 source archive, selected C/header/manual files, before/after
bytes and commands are retained. Serialized hashes remain diagnostic; old
failed attempts are preserved with their original hashes and statuses.

The [current checkpoint](MONOCLE_RESPONSES_CHECKPOINT_2026-11-21.md) records the
exact normal JAR, 889 focused tests per packaged JVM, complete response controls,
actual global-stage operations and complete replay/verification evidence. Six
cases preserve all 984 original response values and all 66 original gene-test
returns. Native admitted inference is compared for 54 gene tests (378 fields);
the three complete-family degeneracy refusals above remain explicit.

The initial normal-build test failure is retained; the correction makes the
pre-existing numerical policy explicit with an independent design-space proof.
An initial Python comparator failure on an original 1x1 scalar wrapper is also
retained; only exact singleton wrappers are unwrapped, with all values checked.
No scientific model, original input or expected statistic was changed.

This component adds no catalog family/review credit. The original goal still
requires bounded Monocle admission, correction of stale nine-role scope notes,
the remaining high-impact omics/GWAS integrations and 1,092 individual-use reviews.
The canonical catalog remains 250 papers and 144 scoped families. Existing paper,
license and private-transfer boundaries in the handover remain applicable.
