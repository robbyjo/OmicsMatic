# Original Seurat RNA workflow checkpoint — 19 November 2026

This follows the published handover at
`07c40beeaf0e4d33dd36c687d092a54c02985315`. It implements the broader original
RNA workflow through typed Java capture/execution/verification/replay, plus
native SNN/Jaccard graph construction. **The 250-paper implementation/review
goal remains active and unfinished. Seurat remains method-overlap; no new
catalog family credit is claimed.**

Start with the [workflow guide](../vignettes/seurat-rna-workflow.md),
[example](../../examples/seurat-rna-workflow/README.md),
[qualification](seurat-workflow-development/README.md) and
[machine-readable proof](seurat-workflow-development/VALIDATION.json).
The entry point is `org.jlinalg.cli.SeuratWorkflowCli`; the native API is
`org.jlinalg.sequence.SeuratSnn`. Java 17+ and the declared original R profile
are required for RUN/REPLAY. VERIFY and native SNN need no R execution.
Use the current source-build JAR, not the earlier public 0.4.0 release.

## Implemented behavior

Closed requests support original RNA construction without initial feature/cell
filtering; LogNormalize/VST or explicit SCT v2; numeric/factor regression;
scaling; exact, source-default Gram or explicit IRLBA PCA; Annoy/RANN NN and
SNN; Louvain, multilevel, SLM or explicit-backend Leiden; optional uwot UMAP
and Rtsne. Unused method options are rejected. Public exact IDs are restored
from explicit safe transport codes; all source feature dispositions are
recorded. Partial/error outputs never become complete results.

Source-default PCA's actual Gram/fallback branch is observed through a
temporary delegating process-local binding, then restored. Original RunPCA
bodies/formals remain unchanged. Exact centering/full SD spectrum, rank
bounds, weighted/unweighted scores, SNN inclusive pruning/diagonals and
multi-resolution source clustering semantics are preserved. No generic PCA,
k-means, alternate backend or input repair is silently substituted.

The current source profile is Seurat 5.6.0 at official commit
`75dfa8603befe31505e6287d32dc86c607732bd6`, R 4.6.1. Seventy-four selected
installed Seurat R bodies/formals matched. Native DLLs, the entire dependency
tree and host authenticity are outside that guard's scope.

## Verification and limitations

Six original synthetic workflow profiles passed: exact/RANN/Louvain;
Gram/Annoy/SLM; numeric+factor regression/IRLBA/RANN/multilevel;
SCT-v2/Gram/Annoy/igraph-Leiden; exact unweighted without embeddings; and
uncentered/unweighted Gram without embeddings. Every request used all 96
cells and 300 input genes. SCT retained 299 active genes after its source
zero-expression exclusion. PCA used 80 selected features/ten PCs; exact PCA
retained 80 SDs. Native audits checked applicable normalization/scaling,
PCA input/projection/norm/loading-orthogonality relations and 21,786 SNN edges
across the six captures.

The packaged development JAR passed six meaningful JUnit tests on Windows
and Linux Java 21. All six saved captures passed Java verification on each
JVM. Twenty-six controls passed, including genuine SKIP3 prerequisites,
FAIL2 model/input/source/time/output/capacity refusals, failed/skipped capture
refusal, resealed scientific/protocol/warning corruptions, owned timeout
cleanup, and original-model replay. The replay preserved the prior capture;
its selected published views were byte-identical on this machine.

Independent standalone original calls using exactly captured scientific
inputs agreed on 5,376 embedding fields and exact cluster cells/columns/
labels. Original R fitting ran on Windows; no original Linux R, whole-project
suite, biological validity or full-paper reproduction is claimed.
The native auditor does not independently refit VST/regression/SCT, NN search,
clustering, UMAP or t-SNE. Integrity hashes are not authentication.

**The retained older regression t-SNE comparison differs substantially.**
Its independently generated covariates differ by at most `2.22e-16` from the
captured inputs. PCA remains nearly identical and cluster labels match;
t-SNE does not. The exact-input original rerun subsequently agrees within
the declared tolerance. Preserve exact inputs, seeds and source/runtime
provenance; do not erase this sensitivity or promise cross-host byte equality.

The first export and first reference-auditor header failures remain retained
and explicitly unadmitted. The successful source/evidence audit must bind the
current packet and JAR bytes; do not refresh old hashes to pass later changes.

## Warnings and remaining work

**SKIP3: dependent analysis was skipped.** Supply the named exact permitted
runtime/library/package/backend in declared private paths, or leave it skipped.
Download/install is authorized where allowed. The tested Leiden backend is
igraph; positive leidenbase execution is unqualified and its missing-package
path is tested. Never silently switch a requested backend.

**FAIL2: no complete result admitted.** Keep inputs, partial RDS/tables and
diagnostics; correct the named scientific, source, numerical, process or
capacity cause and retry a new path. A zero source exit or saved table alone
is insufficient. Require `results_admitted: true` and successful VERIFY.
Never silently alter IDs, NaN/Inf, P values, populations or failed models.

Next: review all 49 Seurat observations individually and admit supported
roles/editions/scopes through the cumulative catalog process. Preserve P061's
reimplementation role; P083 background and P160 supplementary-table context
are not automatic direct-use positives. Integration, WNN, transfer, Harmony,
module scores and historical editions remain separate scope. Then continue
the higher-impact missing/overlap families and remaining source reviews.

Canonical totals remain 250 papers, 446 named families, 485 effective
definitions, 2,695 observations, 130 new scoped integrations (24 native +
106 upstream), 1,290 reviewed positive uses, 1,405 awaiting review, 259 missing
and 45 method-overlap. The canonical catalog and website source are unchanged.
Both literature pages remain withheld from Pages. No release was created.

## Local handover locations

Authoritative worktree: `D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/seurat-workflows-20261119`. Current work: `build/seurat-workflow-1/`.
The exact qualified JAR is `qualification-1/omicsmatic-0.4.0.jar` there;
[PACKAGE.json](seurat-workflow-development/PACKAGE.json) records its SHA-256.
All 23,063 prior JAR payloads are unchanged. Preserve the old candidate and
all failures as well.

- Original inputs/first failure: `execution-1/`; first complete capture:
  `execution-2/`; five additional profiles: `execution-3/`.
- Package/JVM checks: `qualification-1/`; 26 controls: `controls-1/`.
- Independent exact-input original reference: `original-reference-exact-input-1/`;
  final comparison: `reference-comparison-3/`. Earlier comparison failures and
  sensitivity remain in `reference-comparison-1/` and `reference-comparison-2/`.
- Frozen-runtime/other-worktree preservation: `preservation-1/`; deep public
  source/package/capture audit: `audit-1/`.
- Previous native API/R libraries and private primary Seurat article intake:
  see the retained [handover](HANDOVER_2026-11-19.md). Do not publish full article
  paragraphs or restricted runtimes.
- Publication/preservation receipts:
  `D:/projects/JLinAlg/build/git-publish/20261119-seurat-workflow-26/`.

All 24,841 frozen R-library files, the other 20 worktrees and 36 previously
protected artifacts were rechecked unchanged. No installation was needed.
The old root and historical dirty overlays remain local; do not broadly stage,
reset or clean them. Private/large runtime stores require a separate permitted
machine handover. The full goal is not complete.
