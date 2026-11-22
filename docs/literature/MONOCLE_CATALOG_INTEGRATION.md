# Monocle catalog integration and paper-specific scope

This admission credits **one scoped upstream Monocle family**, bringing the
catalog to **145 scoped integrations: 26 native Java and 119 upstream**. The
250 papers, 446 labels, 2,722 observations, 1,630 reviewed uses and 1,092 pending
uses stay unchanged. Nine existing Monocle scope records and one related P024
py-monocle policy note are corrected; **no observations or reviews are added**.
The remaining queue includes 258 missing and 29 method-overlap labels. Three
existing adapters bring the inclusive integration count to 148. Aliases and
native helpers add no extra family credit.

Use the [source-build command guide](MONOCLE_COMMANDS.md),
[native response example](../../examples/monocle-responses/README.md),
[1.0.0 graph example](../../examples/monocle100-graph/README.md) and
[qualified response/global-stage checkpoint](MONOCLE_RESPONSES_CHECKPOINT_2026-11-21.md).
The registration is source-build availability. This update does not qualify or
publish a release asset, rerun a paper's biological experiment, or complete the
full 250-paper implementation objective.

## Warnings and required actions

**Select the exact operation and edition.** The `monocle-tools` entry point
exposes original 1.0.0 graph/neighbors, 1.3.1 models, 1.3.3 graph learning,
1.3.7/Batchelor 1.20 alignment, selected Monocle2 2.26.0 differential testing
and selected 1.4.27 workflows. Native graph ordering, weight selection, explicit
response/design preparation and residual-Moran inference have distinct contracts.
Native preparation/graph/weights do not admit a gene test. No provider, edition,
root, assay, population or factor coding is inferred or automatically substituted.

**SKIP3 means the dependent analysis is skipped.** Supply the exact permitted
runtime/profile, input or accessible path named by the command, then retry NEW
output. Downloads/private installation are authorized where the operating
environment permits them. Absence alone does not mean installation is prohibited.
If a needed installation is prohibited, state the affected analysis, reason and
exact software/input/path the user must supply; retain the skip and emit no result.

**FAIL2 means no complete result is admitted.** Preserve raw, original, null,
failed and partial evidence, error messages and logs. Correct the named data,
design/rank, numerical, source/integrity, capacity or process cause and retry NEW
output. Never repair IDs/nonfinite values, remove failed cells/genes, infer roots
or replace an edition to force success. Require exit 0, valid completion and the
selected stage's explicit admission flag; read its WARNINGS.md and numeric warnings.
Retain log-p when a numeric p value rounds to zero.

**The native and author failure rules differ.** P021's privately inspected
modified test uses per-gene FAIL status and BH over original OK rows. Native
residual-Moran testing refuses a complete family with unresolved residuals or
unsupported self-arcs. The retained degenerate control has finite original R
inference from rounding residuals; native refuses all three alternative-specific
families. No original result is replaced or silently promoted. Prepared responses
alone provide no statistical or whole-paper admission.

**py-monocle is still unimplemented.** Its inspected Community-Use License has
noncommercial terms. The recorded intended use category is educational/research/
nonprofit; the old note waiting for that category is stale and is corrected in
both related P024 records. No Python runtime, paper commit or py-monocle analysis
is qualified by R/native Monocle controls. Cicero and Batchelor already have
separate scoped catalog rows; neither receives additional credit here.

Cell Ranger remains excluded. The accepted research-use and GATK 3.8 decisions
remain recorded; this update requests no repeated permission. Both unfinished
literature reports remain off Pages. The inherited full-site citation gate and
six descriptive registry/inventory differences remain recorded. No release,
Pages deployment or new full-site pass is claimed.

## Nine reviewed source roles

The [source census](monocle-catalog-sources/CENSUS.json) scans all 250 frozen
XML/DOI identities and records nine Monocle-positive papers with 15 exact source
pointers. Full XML and full paragraph context remain private. Source versions,
identifiers, reported UMAT spelling and the paper window remain literal.

| Paper | Reported use | Qualified component and remaining requirements |
|---|---|---|
| [P021](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600939/) | Monocle3 1.0.0 lineage trajectories and a modified covariate-aware Moran test. | Original prepared-CDS 1.0.0 graph/neighbors plus explicit native weights, responses/design, ordering and residual-Moran components exist. The paper says **UMAT**. Its author execution commit, original embedding/preparation, selected shortest paths, exact coding, metacells, spline models and clustering are not recovered. Author per-gene failure/BH rules remain distinct from native family refusal. |
| [P024](https://pmc.ncbi.nlm.nih.gov/articles/PMC13600946/) | py-monocle versus neural-network/Palantir pseudotime in microglia, selecting the highest Braak correlation from 200 eligible roots. | This is a separate unqualified Python provider. Keep donor/root eligibility and selection; the selected correlation is not independent predictive validation. The intended-use category has already been supplied. |
| [P043](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719105/) | Cicero for Monocle3 cis-co-accessibility, mm10 and 500 kb windows. | This is platform context, not a Monocle trajectory run. Use the separately scoped Cicero workflow where applicable. Shuffle/background fitting, literal filtering direction/FDR, TSS annotation and the failed Hypendymal_NN subclass remain explicit. |
| [P051](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823425/) | Monocle3 1.3.7 preprocessing, Batchelor 1.20.0 integration, reduction/clustering and pseudotime. | `ALIGN-1.3.7` has original full-pipeline controls. Exact paper settings and roots remain unreported. The separate Wilcoxon comparison used continuity correction. Batchelor already has separate credit. |
| [P085](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631771/) | Monocle3 1.3.1 spatial-expression GLMs with amyloid scores, sample and UMI covariates; subsequent positive Pearson filtering. | `MODELS-1.3.1` has original prepared-model controls; the old grr/R4.6.1 skip is historical. Family/formula, units and population remain explicit. Preserve original t/grouped-Holm behavior alongside paper Wald/FDR and documentation BH wording. Human grey matter, mouse regions and stains stay separate. |
| [P099](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703679/) | Monocle2 differentialGeneTest across gene/exon/accessibility and human/mouse comparisons. | Selected 2.26.0 typed differential tests are qualified; the paper patch/family/formulas remain unresolved. Preserve distinct populations, replicate definitions, sex covariates and sampling. TPM thresholds do not prove raw-count eligibility. Retained source-family failures remain unadmitted. |
| [P117](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553300/) | Monocle3 1.3.3 prepared-embedding graph learning, ncenter 130/140/150, branch length 15/20, no loops and assumed-direction ordering. | `GRAPH-1.3.3` has original controls for all six reported combinations. Actual selected cells/embedding, mapping of controls and root IDs remain unreported. Synthetic roots do not establish biological direction. |
| [P134](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553295/) | Pseudotrajectories of metaVIPER protein-activity profiles for 1,856 malignant pancreatic cells. | The Monocle edition/model/graph/root are unreported. General supplied-graph ordering or explicitly declared response arithmetic requires actual prepared inputs. Protein activity must not be treated as raw RNA counts or automatically logged/size-factor normalized. |
| [P233](https://pmc.ncbi.nlm.nih.gov/articles/PMC12461018/) | Condition-specific stromal trajectories following Seurat-wrapper conversion and separate FD/WT Liger reclustering. | The exact patch, conversion layers, graph settings and roots are unknown. Keep condition-specific objects and do not pool them or assign a selected runtime as the unreported paper edition. |

## Evidence for the source-built implementation

The preceding normal JAR is 197,260,589 bytes with SHA256
`f2d6203093a21355e415664f5d307ff58cd4d92f23e9de177020b14d6af9c0ba`.
It passed 889 focused tests per packaged Windows Java 21/25 and Linux Java 21
host, six native response replays/three-host verifications and actual global
graph, neighbor and weight controls. Six cases preserve 984 original response
values and all 66 original test returns; admitted native inference matches
54 tests/378 fields, with three complete-family degeneracy refusals retained.

This admission additionally runs the **current normal JAR** through original
1.3.1 models, 1.3.3 graph learning, 1.3.7/Batchelor alignment and Monocle2 2.26.0
differential testing. All 29 selected complete scientific files match retained
original outputs byte for byte. Only a NEW output path changes in each executed
request. The executed requests come from the pinned public command archive;
its `reference_request` field identifies a different earlier standalone input
and is not misused as the executed request's fingerprint.

Three older standalone verifiers correctly reject later packaging-guard source
changes. Those original fingerprints remain intact. The four exact source/builder
changes match the separately qualified command-integration transition, and the
current-JAR original runs provide fresh evidence. No old guard, scientific source,
expected statistic or old capture is rewritten to obtain a pass. Initial harness
preflight errors and the corrected archive-field interpretation remain retained.

Model/transaction controls verify one-family credit, zero appended reviews,
unrelated-row preservation, exact review inverses, rollback/new-file retention
and concurrent-edit protection. Fresh Windows/Linux source audits reproduce
all nine roles. Local desktop/mobile preview and installed reports verify current
counts, provider boundaries and prominent recovery instructions. These checks
do not authenticate external producers or establish full paper/tool reproduction.

## Recheck or continue

With Python 3.11+ and an accessible exact XML cache:

```powershell
python -B tools/literature/audit_monocle_source_roles.py --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/NEW-monocle-source-check
python -B tools/literature/close_monocle_catalog.py --verify --primary-cache D:/projects/JLinAlg/build/literature-review/xml --out build/NEW-monocle-catalog-check
```

Omit `--primary-cache` from catalog verification for retained metadata/evidence
verification only; that does not become a fresh source audit. Neither command
runs scientific R models. Git is required for exact historical metadata checks.
In WSL on the linked Windows checkout, set `MONOCLE_CATALOG_GIT` to
`/mnt/c/Program Files/Git/cmd/git.exe`; use native Git in an ordinary Linux clone.

The admission retains exact-before metadata, a deterministic transition, the
actual unchanged P99 predecessor and a review-only inverse for nine Monocle
scopes plus one related py-monocle policy note. The old models, source providers,
worker code and numerical library remain unchanged. This is not a hash waiver or
an old-metadata overlay. Run frozen P99 and earlier readers on their actual
unchanged owners or exact commits; do not refresh their hashes for newer catalog
or handover pages. See [ADMISSION.md](../../tools/literature/monocle_catalog/ADMISSION.md)
for plan/inspect/apply mechanics and required checks.

Continue the remaining 1,092 use reviews and high-impact GWAS/omics gaps. Candidate
frequencies retained for reprioritization are QTLtools 9, SKAT-O 8, ACAT 7,
FUSION 7, MR-PRESSO 6, SKAT 6, fastQTL 5, GenomicSEM 5 and PRSice 5. Recheck actual
coverage before implementing; existing method overlap is not source-equivalence
proof. Prefer feasible native Java implementations and tested upstream adapters.

**Before retiring this machine**, separately transfer ignored exact runtimes,
normal JARs, primary full text, private author source, all ignored outputs and
complete private evidence. Verify destination checksums and preserve all raw,
failed, null, invalidated and superseded results. GitHub is not a machine backup.
The collection window remains 2023-10-23 through 2026-10-23; no extension was needed.
