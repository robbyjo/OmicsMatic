# Auditable study pipeline tutorial

For the complete scientific workflow and improved pseudocode, see the
[analysis pipeline specification](analysis-pipeline-specification.md), including
primary/sensitivity rules, native variant consequences/AlphaGenome bridges and remaining AlphaFold follow-up.

For predeclared external population-frequency rare masks, see
[external ALT-frequency eligibility](#external-population-frequency-eligibility-for-rare-masks).

For explicit time-to-first-event analyses, see
[registered native Cox survival association](#registered-native-cox-survival-association).

For prespecified adjusted effect modification, see
[registered subgroup interactions](#registered-adjusted-subgroup-interactions).
The branch accepts participant matrices or typed completed interaction results.

For source measurement checks and completed lab QC, see
[molecular assay QC and supplied decisions](#molecular-assay-qc-and-supplied-decisions).

For registered hard-call relatedness QC and genotype PCs, see
[genotype sample QC](#registered-genotype-sample-qc-and-projected-pcs).
Completed external QC/PCA can use the
[scoped reuse and missing-PCA route](#reuse-external-sample-qc-and-pca-and-complete-missing-work).

For completed discovery meta-analysis followed by missing validation work, see
[frozen discovery families](#preserve-supplied-discovery-fdr-during-validation).
Discovery cohorts without original files can use
[references to supplied pools](#discovery-cohorts-represented-by-supplied-pools).

For mixed supplied and native analyses, see
[scientific scope and missing cohort networks](#scientific-scope-and-missing-cohort-networks).

For completed modules, module GO or an entirely analyzed study, see
[completed analyses through manuscript](#completed-analyses-through-manuscript).

For manuscript claims bound to frozen results and actual public source text, see
[principal-finding source review](#claim-bound-principal-finding-discussion).

For completed bibliographies or partly completed searches, see
[supplied literature and missing searches](#reuse-supplied-literature-and-complete-missing-searches).

For impact-based allocation before the 100-signal literature cap, see
[provisional literature priority](#provisional-impact-before-the-literature-cap).

For genome-wide result plots and source-aware calibration summaries, see
[GWAS QQ and Manhattan diagnostics](#gwas-qq-and-manhattan-diagnostics) and [cross-cohort module preservation](#cross-cohort-module-preservation).

For follow-up beyond replication and meta-analysis, see the [bioinformatics evidence tutorial](bioinformatics-evidence.md): source discovery, reviewed regional MR/fine-mapping/colocalization, proteins, genomic sequence, literature and local evidence archives. It includes a separate runnable offline synthetic example.

> **Source-build tutorial.** This walkthrough runs the checked-in, result-first
> two-cohort example through validation, resolution, plan approval, execution,
> verification, and resumable reuse. It also shows where an optional LLM may assist
> and how to keep patient-level data outside a remote LLM boundary.

## What you will build

The example begins with two association-result tables rather than participant
matrices. JLinAlg will standardize the inputs, compare discovery and replication
effects, perform fixed-effect meta-analysis, run deterministic diagnostics, and
write a traceable report. Every committed step is represented in the event
ledger and the readable research notebook.

The tutorial is intentionally small enough to inspect by hand. The same contract
and audit structure apply when a project begins with processed molecular
matrices, has more cohorts or tissues, or stops at a later evidence stage.

The checked-in fixture uses `analysis.mode: manual` to stay small and completely
offline. In a production study, omit that field or set `mode: auto`. Explicit
goals then become the minimum requested work, and JLinAlg adds eligible
association, replication, meta-analysis, enrichment, genetic-evidence,
literature, diagnostic, prioritization, and report branches from the objective
and declared inputs.

The default pipeline does not contact an LLM. An explicitly enabled `assistance`
block defaults to an audited manual browser handoff for bounded advice,
literature review and a first manuscript draft. Explicit alternatives include
OpenAI Responses, Gemini GenerateContent and OpenAI-compatible Chat Completions
with an API key or provider-issued OAuth bearer token. Browser transcripts and
citations are human-attested, not authenticated provider output. JLinAlg retains
control. MCP, provider-specific OAuth login/refresh, general pipeline local-model
adapters and autonomous LLM orchestration remain future work. The `llm` actor
option alone never enables a call.

## Reuse completed analyses and run missing work

The current source accepts completed analyses under each hypothesis's
`analysis.supplied_results`. These are scoped analysis artifacts, separate from
the original cohort datasets. Use `datasets: []` when only completed results
are available. This feature is local source-build behavior beyond release
v0.3.7; an enum naming a later dataset stage is not an import adapter.

For example, reuse an external meta-analysis and request only missing GO work:

```yaml
schema_version: 1
project:
  id: completed-meta
  title: Complete GO from an existing meta-analysis
  description: External estimates with a declared full tested family.
resources:
  directory: project-resources
  network: offline
  downloads: deny
datasets: []
hypotheses:
  - id: H1
    description: Interpret the supplied BMI association estimates.
    outcome: BMI
    analysis:
      mode: manual
      goals: [gene_ontology]
      supplied_results:
        - id: external-meta
          kind: meta_analysis
          role: primary
          files:
            results: inputs/meta.tsv
            feature_annotation: inputs/feature-annotation.tsv
          columns:
            feature_id: gene
            beta: effect
            se: standard_error
            p_value: P
            adjusted_p: FDR
          scope:
            phenotype: BMI
            cohorts: [FHS, CARDIA]
            feature_id_type: ensembl_gene_id
            species: human
          method:
            name: Externally fitted meta-analysis
            software: your-analysis-software
            version: your-recorded-version
          family:
            id: complete-bmi-family
            coverage: complete
            tested_count: 20000
            adjustment: BH
            adjusted_p_from_full_family: true
          provenance:
            producer: your-analysis-team
          limitations:
            - Original participant QC and model calibration were not reproduced here.
```

Replace the example's count and metadata with the actual source records. Place
the verified GO resource in this explicit project-local resource directory, or
configure an existing verified resource location. Offline mode does not acquire
missing resources. `feature_annotation` needs unique `feature_id` and gene IDs
or symbols; optional locations also require `scope.coordinate_system` and a
compatible build. GO uses the mapped complete eligible background and its
declared selection threshold. A selected-only source cannot establish that
background and leaves native enrichment waiting for the missing universe.

To import completed enrichment too, add a `kind: gene_ontology` artifact with
`files.results`, its scope/method/family and optionally
`depends_on: [external-meta]`. The planner reuses that artifact instead of
rerunning GO. List all requested completed stages to build an all-results
project. With `mode: manual`, goals define the requested missing computations;
with `mode: auto`, eligible downstream goals are also inferred. A supplied
primary meta-analysis removes inferred upstream association fits unless a
missing declared validation branch needs them; explicit association goals
still request those fits.

| Supplied kind | Required canonical columns or file roles |
| --- | --- |
| `meta_analysis`, `discovery_meta_analysis`, `mendelian_randomization` | `feature_id`, `beta`, `se`, `p_value` |
| `gene_ontology`, `pathway_enrichment`, `ranked_gsea` | `term_id`, `p_value` |
| `rare_variant_analysis` | `feature_id`, `mask_id`, `test`, `p_value`, `is_primary_test`; exactly one prespecified primary test per set/mask |
| `discovery_replication` | `feature_id`, `discovery_subset`, `replicated` |
| `effect_comparison` | `left`, `right`, `n_matched`, `correlation` |
| `colocalization` | `feature_id`, `region_id`, `pp_h4` |
| `fine_mapping` | `feature_id`, `variant_id`, `pip` |
| `network_analysis` | `files.nodes` with `node_id`; `files.edges` with `source`, `target`; optional `files.modules` with `node_id`, `module_id` |
| `network_modules` | `files.results` with `node_id`, `module_id`; optional `module_type` (default `supplied_module`) and `network_id`; optional full `files.nodes` universe and `files.feature_annotation` |
| `module_gene_ontology` | `module_id`, `term_id`, `p_value`; optional `module_type`, `network_id`, `adjusted_p`, finite natural-log probabilities, gene counts and `hit_genes`; declare the original module-by-term testing family |
| `risk_score_evaluation` | `score_id`, `phenotype`, `evaluation_population` |
| `variant_annotation`, `variant_scoring` | `variant_id` plus supported consequence/score/context columns |
| `protein_structure` | `protein_id`, `sequence_sha256` plus supported structure/confidence columns |

Mappings rename source headers to canonical names. Network mappings can be
nested under the file roles `nodes`, `edges` and `modules`. Graph endpoints and
module members must exist in the node table; signed weights and overlapping
memberships are retained. Imported rare-set rows without beta/SE remain
`unsigned_set`; no beta or direction is invented. The declared complete
mask/test family determines correction, including nonprimary tests. Generic
literature/source/investigation YAML summaries can also be retained as external
evidence, but that is not independent source appraisal or complete claim binding.

The importer checks typed identities and numerical ranges, retains originals
under `supplied/`, writes normalized tables and `supplied-analysis.yaml`, and
records hashes, methods, family, source limitations and external attribution.
Unknown table columns remain in the immutable raw snapshot, outside normalized
AI evidence previews. Missing files or unresolved correction scope produce
`waiting_for_input`; independent imports can continue. Invalid tables fail
their import rather than triggering an unrequested replacement analysis.

Complete P-value families can receive declared BH/Bonferroni correction when
`tested_count` matches their estimable tests. Existing source adjusted P values
are preserved by default for complete families. For selected-only rows, declare
`coverage: selected_only`, the original `tested_count` and
`adjusted_p_from_full_family: true`, and supply `adjusted_p`. Downstream
selection preserves those original values; significant-only rows never become
a new BH family. An undeclared denominator is a missing input.

Use `role: sensitivity` for alternative external analyses. They cannot replace
the primary or publish its canonical filename. Native omission/unfiltered
comparisons against an imported meta-analysis require complete matching feature
and cohort/source scopes, effect contracts, model/inference/confidence settings,
source versions in `provenance.input_sha256`, and the exact sampling matrix when
applicable. Otherwise that comparison waits. Neither a completed import nor
matching metadata independently certifies the original scientific analysis.

For a thresholded coloc export, use
`evidence_grade: exploratory_partial_region_major_limitation` and describe the
missing variants/coverage in `limitations`. The mandatory caveat follows the
import and evidence summary: conditional selected-variant evidence is limited,
and a null result cannot establish absence of a shared causal variant.

Run the offline synthetic acceptance example with:

```powershell
python examples\study-pipeline\supplied-results-workflow.py run build\supplied-results-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\supplied-results-workflow.py verify build\supplied-results-example --jar build\cli\jlinalg-0.3.7.jar
```

It checks all-supplied reuse, unsigned joint rare-test correction, missing native
GO against an independent hypergeometric calculation, selected-only source FDR,
immutable inputs and cache resume. It pins the executable and verifier. These
checks establish software behavior on synthetic inputs, not real analysis
calibration, AI appraisal, complete external-format support or full conformance.

## Preserve supplied discovery FDR during validation

Register a completed discovery pool as `kind: discovery_meta_analysis` in
`analysis.supplied_results`, with its original discovery scope, method and
testing family. It replaces the discovery-pooling operation. The separate
`discovery_replication` operation can then perform the missing validation work.
Use `analysis.mode: manual` and `goals: [discovery_replication]` when that is the
requested scope; automatic mode can request other applicable analyses.
Native all-cohort meta-analysis still needs compatible cohort estimates.

A selected-only export must retain the original complete-family `adjusted_p`,
declare `family.coverage: selected_only`, the original `tested_count`, correction
method and `adjusted_p_from_full_family: true`. For example, a row with
`p_value: 0.000001` and source `adjusted_p: 0.4` remains unselected at 0.05.
JLinAlg must not recompute BH on the selected rows or on their overlap with
validation results. Complete imported families preserve their source correction,
including BY, Bonferroni or NONE, even when validation uses BH.

The source family and the validation family are distinct. With
`replication.adjustment_scope: discovery_subset`, validation correction covers
the available validation tests in the frozen discovery-selected subset. With
`all_results`, it covers all available validation tests. A validation effect
cannot change discovery eligibility. The registered sign and threshold rules
still apply; significant opposite-direction effects do not satisfy a required
same-sign replication rule.

`primary-result-selection.yaml` records the discovery producer, canonical table
hash, source metadata hash, original denominator and adjustment origin. The
selection is marked `official_primary: false`: a discovery-only pool does not
become the official all-cohort meta-analysis. Producer-manifest and metadata
snapshots remain in the operation, and its Methods record includes the two
correction scopes. Changed, missing, incomplete or statistically unresolved
source artifacts cannot silently select validation candidates or trigger a
replacement discovery fit. Imported scope and scientific completeness remain
source declarations requiring review; normalization does not independently
reproduce or certify the original analysis.

Run the independent offline software regression with:

```powershell
python -B examples/study-pipeline/frozen-discovery-workflow.py run build/frozen-discovery-example --jar build/cli/jlinalg-0.3.7.jar
python -B examples/study-pipeline/frozen-discovery-workflow.py verify build/frozen-discovery-example --jar build/cli/jlinalg-0.3.7.jar
```

It checks selected-only and complete source families, native discovery pooling,
both validation scopes, effect direction, held unresolved inputs, unchanged
source files and cache reuse. All effects and cohorts are synthetic; it makes
no external requests or AI calls.

## Discovery cohorts represented by supplied pools

`study operations` lists `analysis_reference` as a supported context for
harmonization and discovery/replication. Native meta-analysis still advertises
cohort-estimate stages, because a reference cannot replace those estimates.

A completed discovery pool can support missing validation work even when the
original discovery matrices and cohort result tables are unavailable. Register
those discovery datasets with `stage: analysis_reference`, their real cohort
membership, a `supplied_result_id`, and `files: {}`. The ID names a supplied
**primary** supplied analysis in each hypothesis using the reference. For
discovery validation, that owner is `meta_analysis` or `discovery_meta_analysis`;
network-only reuse can instead name a `network_analysis`. The supplied scope
must own that dataset or its declared cohort
membership. A reference carries metadata; it contains no reconstructed cohort
estimate or participant-QC evidence.

For example, these fields connect a supplied discovery pool to raw validation
data in an otherwise complete study contract:

```yaml
datasets:
  - id: discovery_a
    cohort: A
    type: expression
    stage: analysis_reference
    supplied_result_id: discovery_pool
    files: {}
  - id: discovery_b
    cohort: B
    type: expression
    stage: analysis_reference
    supplied_result_id: discovery_pool
    files: {}
  - id: validation
    cohort: V
    type: expression
    stage: processed_matrix
    files: {matrix: validation-expression.tsv, phenotype: validation-phenotypes.tsv}
    columns: {sample_id: id}
hypotheses:
  - id: H
    description: Validate the frozen external discovery findings.
    outcome: BMI
    inputs: {discovery: [discovery_a, discovery_b], replication: [validation]}
    analysis:
      mode: manual
      goals: [association, discovery_replication]
      association: {model: ols}
      supplied_results:
        - id: discovery_pool
          kind: discovery_meta_analysis
          files: {results: discovery-pool.tsv}
          scope:
            datasets: [discovery_a, discovery_b]
            cohorts: [A, B]
            phenotype: BMI
          method: {name: Original discovery meta-analysis}
          family:
            id: original-discovery-family
            coverage: selected_only
            tested_count: 400000
            adjustment: BH
            adjusted_p_from_full_family: true
```

The pool table must contain the validated original `adjusted_p` values. Declare
the actual cohort relationships in the study; missing independence declarations
remain review needs. The example OLS route is only appropriate for its registered
outcome/design. A supplied pool does not choose or validate the missing model.

For this contract, association/QC execute for the validation matrix. Neither
discovery reference requires a placeholder file, a reconstructed association fit
or retrospective participant QC. `analysis-references.tsv` records reference
ownership separately from actual harmonized cohort tables. Ordinary dataset
stages retain their file requirements. A missing supplied file holds its import
while independent work can proceed; JLinAlg does not select a native discovery
replacement.

Before validation uses an imported discovery pool, `discovery-scope-audit.yaml`
checks exact discovery dataset/cohort sets, reference ownership, the registered
trait key and discovery/validation separation. If both source membership lists
are supplied, both must agree with the study. Trait keys match the registered
outcome/exposure exactly; aliases are not inferred. Known cohort relationships,
shared cohort identities and sampling-matrix links are checked using all role
metadata, including discovery cohorts without tables.

Known source/registered effect, units, assay, tissue, modality, species, build or
feature-namespace conflicts hold selection. Units preserve case, including
scientifically meaningful prefixes. Existing registered assay/tissue/modality
context exceptions retain their rationale; they do not waive cohort independence
or effect-unit conflicts. Incomplete effect contracts remain explicitly
uncertified by default; `pooling.require_complete_contract: true` requires
complete source declarations as well. Genomic source/validation comparisons
check available allele and coordinate coding on shared eligible variants;
conflicts hold without automatic allele flips or sign changes. Missing coding
remains a limitation and holds the strict profile.

A pooled reference cannot supply an independent validation estimate. It also
cannot be substituted for its constituent cohorts in a new native all-cohort
meta-analysis: that requested fit waits for the actual cohort estimates rather
than pooling only whichever validation tables happen to be available. Reusing
a supplied all-cohort primary meta-analysis remains a separate typed import.

Run the offline acceptance example with:

```powershell
python -B examples/study-pipeline/discovery-scope-workflow.py run build/discovery-scope-example --jar build/cli/jlinalg-0.3.7.jar
python -B examples/study-pipeline/discovery-scope-workflow.py verify build/discovery-scope-example --jar build/cli/jlinalg-0.3.7.jar
```

It independently checks native validation coefficients, SEs, P values and sample
counts, frozen discovery selection, source scopes and coding, held/invalid
inputs, cache reuse and unchanged source files. The data, contracts and source
approvals are synthetic. Accepted declarations do not independently prove
participant disjointness, scientific scope, source calibration or biological
transportability.

## GWAS QQ and Manhattan diagnostics

The source build adds `gwas_diagnostics` after available association and pooled
results. Automatic studies request it for compatible genomic association/raw
inputs or genomic supplied pools. Rare-set-only and later evidence-only inputs
do not imply missing upstream SNP scans; the SNP-based GWAS enrichment trigger
uses the same input distinction or an explicit SNP-summary file. Manual studies can
request the goal explicitly; `gwas_diagnostics.enabled: false` suppresses the
automatic goal. A supplied primary diagnostic artifact satisfies that stage
without refitting its external analysis.

```yaml
# Inside an existing hypothesis.analysis:
mode: manual
goals: [gwas_diagnostics]
gwas_diagnostics:
  minimum_lambda_tests: 20
  dataset_coverage: {cohort_a: complete, cohort_b: unknown}
  maf_breaks: [0.01, 0.05]
  mac_breaks: [20, 100]
  info_breaks: [0.8, 0.9]
  maximum_rows: 2000000
  maximum_plot_points: 100000
  maximum_strata: 200
  plot_y_limit: 1000
```

Coverage declarations name selected genomic dataset IDs. `complete` means the
declared original eligible export is available, including nonsignificant tests;
the declaration does not independently reproduce source QC or calibration.
Unannotated external cohort exports default to `unknown`. Completed native
association producers establish their observed export scope. Native pools have
a complete diagnostic scope only when all expected constituent dataset scopes
are complete. A typed supplied pool retains its authoritative source family:
selected-only exports or a tested denominator larger than available eligible
rows cannot become complete genomes merely because original FDR values exist.
Incomplete source producers retain observed diagnostics with an explicit limit.

Each source has an immutable statistics snapshot, producer-manifest snapshot,
original supplied-metadata snapshot when applicable, and a row disposition
table. The diagnostics keep cohorts, pooled/discovery results, model contrasts
and available pre-effect-QC companions separate. They do not combine P values
across cohorts or choose the least-inflated source as the primary result.

QQ numerical tables retain every eligible finite-log P value. The displayed
uniform reference uses order means `i/(n+1)` within the named source/contrast or
stratum. The summary's `lambda_p_chisq1` is the median one-degree-of-freedom
chi-square transform of those P values divided by the chi-square median. For
even counts it averages the two middle transformed statistics, rather than
transforming the median P value. This is a descriptive P transform; it is not
a model-specific raw-statistic inflation estimate, an LD-adjusted confidence
band or a calibration certificate. Selected, unknown, unresolved or too-small
exports have no reported lambda. Partial exports use an observed-rank display
without a null-reference line.

Finite nonpositive natural-log P values preserve underflowed or rounded zero
exports. A zero without a usable log P stays unresolved. Conflicting positive
P/log-P pairs are retained in the row audit and prevent a complete calibration
summary. No minimum floating-point P is silently substituted. Source exclusions
and failed fits remain distinct from unresolved eligible tests.

Separate summaries/QQ plots stratify available MAF, MAC, INFO and source
test/calibration labels using the registered breaks. Unknown fields remain
unknown; INFO interpretation depends on its provider. The strata are diagnostic
descriptions, not new association tests or retrospective SNP exclusions. Source
counts and historical filters must still be reviewed before accepting imported
results as compliant with a registered single-marker MAC policy.

Manhattan tables retain available declared chromosome/position coordinates and
their observed chromosome offsets. Registered source builds can supply missing
row context; known row/source build conflicts hold that coordinate plot without
liftover or inferred alleles. Mixed builds, missing coordinates and unknown
builds are reported separately. Coordinates are not guessed from rsIDs or gene
names, and missing-coordinate coverage is not called a genome-wide null result.
Native pooled plots can project coordinates from their sealed fitted-contributor
metadata ledger when contributor identities/counts and known coordinates/builds
agree. Missing/conflicting contributors retain explicit dispositions. That
projection never copies cohort P values, MAC, MAF or INFO into pooled statistics;
the pooled P distribution and unavailable pooled strata remain separate.

PNG/PDF rendering has explicit point and Y-axis limits. QQ rendering preserves
the strongest tail before thinning the remaining full-universe ranks.
Manhattan rendering retains the minimum-P peak in each consecutive coordinate
bin. Numerical tables retain all eligible rows and uncapped values. Crossing a
row or stratum budget holds the affected work instead of analyzing only top
hits. The current adapter bounds in-memory row processing; raising the budget
does not establish genome-wide scale or memory suitability on a particular host.

Inflation or deflation can reflect polygenicity, LD, ascertainment, discrete
tests, model behavior and confounding. This branch does not perform genomic
control, replace original P values, alter FDR, certify ancestry adjustment or
change the official primary source. Interpret it with the
[LD Score regression paper](https://doi.org/10.1038/ng.3211) and the actual study
design; probability plotting is described in the
[NIST handbook](https://itl.nist.gov/div898/handbook/eda/section3/eda33m.htm).

To reuse externally completed diagnostics, supply a `gwas_diagnostics` artifact
with `files.results` and the normal scope/method/provenance fields. Its TSV has
`source_id`, `contrast`, `stratum`, `testing_coverage`, `observed_tests`,
`diagnostic_tests`, `unresolved_tests`, `lambda_p_chisq1`, `lambda_status`,
`coordinate_tests` and `coordinate_status`. Counts and lambda dispositions must
agree; an incomplete/selected export cannot claim an available lambda. Extra
figure files can be retained under named file roles. External numbers remain
externally attributed and are not independently recalculated from missing data.
Aggregate source summaries enter finding dossiers and Methods with their
limitations; they provide study context rather than independent replication.

Run the offline synthetic verifier with NumPy, SciPy and PyYAML:

```powershell
python -B examples/study-pipeline/gwas-diagnostics-workflow.py run build/gwas-diagnostic-example --jar build/cli/jlinalg-0.3.7.jar
python -B examples/study-pipeline/gwas-diagnostics-workflow.py verify build/gwas-diagnostic-example --jar build/cli/jlinalg-0.3.7.jar
```

The cases include native OLS, two-cohort pooling, supplied complete/selected
pools and diagnostics, unknown exports, finite-log underflow, unresolved/conflict
rows, coordinate gaps/build conflicts, display/row limits, cache reuse and
aggregate finding context. Numerical references use NumPy/SciPy; the fixtures
do not establish real-population calibration or AI scientific judgment.

## Scientific scope and missing cohort networks

Each typed import writes `supplied-scope-validation.yaml` and
`supplied-scope-comparisons.tsv`. These compare declared traits, populations and
available effect/context metadata with the registered hypothesis. A known
conflict holds the import and prevents canonical statistical selection. The
original files remain available locally, including a normalized
`supplied-results-scope-limited.tsv` when table rows conflict with their declared
build or sequence. Missing context is recorded as
`partially_declared_not_certified`; it is not inferred from prose or treated as
an independently verified scientific match.

Trait keys match `hypothesis.outcome` exactly, with the registered score outcome
and related phenotype columns used for score work. A primary supplied
meta-analysis can provide an explicit registered target when no outcome key is
available. Primary population-bound association, diagnostic and enrichment
artifacts require matching known dataset/cohort sets; sensitivity imports may
cover a subset. Discovery-only imports use the discovery inputs. External MR,
colocalization, fine-mapping and score evaluation populations can differ from
the association cohorts. Annotation, structure and literature context do not
inherit the association phenotype; their own declared build/sequence checks
still apply. These comparisons are input checks, not source reproduction or
scientific certification.

A known incompatible declared upstream source holds its downstream import.
An unavailable upstream file alone does not require rerunning an independently
supplied downstream analysis: the missing lineage remains a review limitation.
Before consumers use a completed parent, its sealed metadata and hash must
agree. No conflicting import silently triggers a native replacement.

For networks, reuse is scoped by dataset/cohort rather than suppressing the
whole stage. For example, supply A's graph and retain B's raw molecular matrix:

```yaml
# Inside the existing hypothesis.analysis:
mode: manual
goals: [network_modules, module_gene_ontology]
network:
  complete_missing: true
  correlation: pearson
  threshold: 0.7
  preprocessing: Record the actual assay-specific normalization here.
supplied_results:
  - id: graph_a
    kind: network_analysis
    files:
      nodes: A-nodes.tsv
      edges: A-edges.tsv
      modules: A-modules.tsv
      feature_annotation: A-annotation.tsv
    scope: {datasets: [a], cohorts: [A], phenotype: BMI, species: human}
    method: {name: Original cohort A graph, edge_direction: undirected}
```

With dataset `a` bound to `graph_a` as an `analysis_reference` and a processed
matrix dataset `b` in cohort B, JLinAlg reuses A's graph/memberships and fits
only B's graph. It plans modules and module GO separately for each source.
Available raw data for A do not cause its supplied graph to be refitted. A
single supplied graph covering A and C remains one graph; its edges are not
reconstructed as separate cohort graphs. Multiple primary graph imports need
explicit comparable disjoint dataset or cohort scopes. Use separate hypotheses
or sensitivity roles for overlapping alternatives.

`network.complete_missing` defaults to `true`. If scope cannot be mapped and
native matrices are present, the source waits for review and the planner does
not guess which matrices to fit. A held source reserves its declared coverage;
clearly uncovered independent B work can still finish. Set `complete_missing:
false` to process only supplied graphs/modules when any primary graphs are
supplied. This is an explicit supplied-only choice, not evidence that omitted
cohorts were analyzed. It does not waive known source conflicts.

Original source methods, parameters, provenance, family rationale and free-text
limitations stay in local `supplied-analysis.yaml`. Automatic Methods records
and finding dossiers use aggregate scope flags and testing-family facts, with
a mandatory source-review caveat. Scientists must review the retained original
limitations before writing scientific conclusions. This boundary is not a
general disclosure or scientific-validity certification.

Run the offline synthetic acceptance with NumPy, SciPy and PyYAML:

```powershell
python -B examples/study-pipeline/scoped-supplied-workflow.py run build/scoped-supplied-example --jar build/cli/jlinalg-0.3.7.jar
python -B examples/study-pipeline/scoped-supplied-workflow.py verify build/scoped-supplied-example --jar build/cli/jlinalg-0.3.7.jar
```

It checks partial/all-supplied graph reuse, missing native work, signed residual
correlations, components/cliques, joint module-GO correction, held scope/row and
dependency conflicts, external population and related-score exceptions, bounded
unsent impact evidence, source hashes and cache reuse. All genes, terms, graphs
and approvals are synthetic; it makes no external requests or AI appraisals.

## Molecular networks, pathway overlap, modules and module GO

The source build registers `network_analysis`, `pathway_network`,
`network_modules` and `module_gene_ontology` as separate study operations.
Correlation networks use one supported bulk molecular matrix per dataset,
preserving cohort-specific populations. Pathway networks connect completed
enrichment/GSEA terms by gene overlap; the separate `ranked_gsea` operation fits
native preranked enrichment. Pathway overlap does not measure molecular
correlation. Automatic mode infers correlation/module/GO branches for supported
bulk matrices, unless `network.enabled: false`. A supplied enrichment source
matching `network.source_kind` can infer pathway/module/GO work. In manual mode,
request desired missing stages explicitly:

```yaml
analysis:
  mode: manual
  goals: [network_analysis, network_modules, module_gene_ontology]
  network:
    correlation: spearman
    threshold: 0.7
    thresholds: [0.6, 0.7, 0.8]
    categorical_covariates: [sex, batch]
    preprocessing: Log-normalized expression after the recorded assay QC.
    bootstraps: 100
    independent_samples: true
    minimum_clique_size: 3
    max_cliques: 10000
    max_search_states: 1000000
    max_clique_depth: 128
    go_min_module_genes: 3
    go_min_size: 10
    go_max_size: 500
    go_adjustment: BH
```

Use actual preprocessing records and an appropriate independent biological
unit; unique sample labels do not certify independence. Related samples,
repeated visits, cells/spots and compositional data require an appropriate
supported model or external graph. Explicit goals still request work when
automatic inference is disabled.

Correlation input is a feature-by-sample matrix. Sample selection is listwise
over that complete matrix and hypothesis covariates; outcome values and
association-hit selection are not used. Network populations can therefore
differ from association populations. Numeric covariates and explicitly named
categorical covariates enter an intercept plus full-rank OLS design, using the
dataset phenotype/sample mappings. Categorical reference levels are recorded.
Pearson or average-tie Spearman correlations are computed after residualization.
The latter is Spearman correlation of residuals, not rank-first partial-Spearman
equivalence. Rank-deficient designs fail rather than silently drop covariates.
Constant/fully explained or numerically unresolved features have dispositions
and a recorded numerical-variation rule.

Edges retain signs and use `abs(rho) >= threshold`. Prespecified threshold-range
edge/Jaccard diagnostics do not select a new primary cutoff. Row bootstraps
resample the frozen residual matrix and require `independent_samples: true`;
frequencies measure conditional stability, not edge P values/FDR or uncertainty
in fitted preprocessing. Degenerate resamples count as nonselection. Bootstrap
defaults to zero and is explicitly reported as unassessed. Signed tables,
feature QC, local sample audits, stability tables and GraphML are retained.
Participant-audit contents do not enter bounded network evidence summaries.

Dense work is bounded at 2,000 features, 20 million stored values and registered
`max_pair_work` (100 million by default, at most two billion
pair-observation-replicate units). `max_samples` defaults to 10,000 and
`minimum_samples` to 10. Exceeding a bound waits; no top-hit or top-variance
subset is invented. Register an outcome-independent universe or an external
scalable adapter.

Module analysis distinguishes connected components, including isolates, from
overlapping maximal cliques. Its pivoted Bron–Kerbosch search has count,
search-state, feature and depth bounds because
[maximal clique lists can grow exponentially](https://networkx.org/documentation/stable/reference/algorithms/generated/networkx.algorithms.clique.find_cliques.html).
Reaching a bound retains partial memberships with an incomplete status. Joint
module GO waits rather than correcting an incomplete prefix. Topology does not
establish biological function or a unique module partition.

For supplied graphs, `files.modules` is reused when present. Otherwise the
module branch computes topology from supplied nodes/edges. Declare
`method.edge_direction: undirected`; directed/multilayer graphs require an
explicit supported projection. Supply `files.feature_annotation` for gene
mapping and `scope.species`. Source caveats follow module and GO evidence.
Requesting only `network_analysis` in manual mode does not force module work.

GO uses all uniquely mapped eligible network genes, including isolates and
genes absent from GO, as background. Unmapped nodes and untested modules retain
dispositions. Modules below `go_min_module_genes` (default three mapped genes)
are excluded independently of their GO P values. Every remaining module is
tested against every eligible term, including zero-overlap tests; BH/BY uses
the joint module-by-term family for that graph. Duplicate/overlapping modules
remain dependent tests, not independent replications. `max_go_tests` is a hard
one-million-test bound; excess work waits without best-term/module selection.
ORA conditions on the estimated graph and memberships; it does not calibrate
graph estimation, outcome selection or feature-to-gene sampling bias.
If no pathways were selected, the empty graph completes without a missing-gene
question; module GO records zero eligible tests and `performed: false`.
Likewise, a nonempty graph with no terms passing the registered size filter
does not claim that GO tests were performed.

Declare species-matched resources. The default GO cache is human; a verified
custom `network.go_database` can supply another species-matched collection.
Missing human GO can be acquired under the existing automatic-download policy;
offline/denied acquisition waits. Actual resource manifests/data contribute to
cache identity. Tests/examples explicitly use project-local resources.

For pathway overlap, request `pathway_network` and choose
`network.source_kind: ranked_gsea|gene_ontology|pathway_enrichment`. Supply exact
gene memberships as `network.gene_sets` GMT or `files.gene_sets` on a supplied
artifact. An explicitly declared `membership_column` can instead contain
leading-edge/selected-hit genes; that overlap is conditional and differs from
full-set overlap. Counts cannot reconstruct membership. Source full-family
adjusted values determine term selection (`term_alpha`, default strictly below
.05). Jaccard edges use the separate `pathway_threshold` (default .25), following
the [EnrichmentMap definition](https://enrichmentmap.readthedocs.io/en/docs-3.3/Parameters.html).
Pathway-module GO requires `membership_gene_id_type: symbol`; other/unknown
namespaces need explicit mapping rather than guessed gene symbols.

Module membership and GO context can attach to existing feature dossiers with
source hashes/caveats. They do not create association discoveries or promote a
nonsignificant feature into AI eligibility. Standalone network-story selection,
null-calibrated preservation, clustered resampling, integrated community/WGCNA
adapters and large sparse graph acceptance remain separate work.

`examples/study-pipeline/network-workflow.py run NEW_DIRECTORY --jar JAR` and
`verify DIRECTORY --jar JAR` exercise synthetic native, supplied-module,
supplied-GSEA, bounded-clique and empty-pathway cases. The independent harness checks signed
orthogonal residual correlations, Jaccard overlap, hypergeometric/joint-BH
values, immutable inputs, GraphML and resume. This is software acceptance, not
biological validation, real AI appraisal or full conformance.

## Cross-cohort module preservation

`network_preservation` transfers frozen reference memberships to other molecular
graphs. It completes missing comparisons using native or supplied graphs without
refitting supplied edges, reassigning modules in the target, requiring target
module matches, or selecting a new primary graph. Automatic mode requests this
stage when at least two primary molecular graph scopes are available or can be
constructed. `network_preservation.enabled: false` suppresses automatic inference;
manual goals remain explicit. Pathway-overlap graphs have separate dependencies.

```yaml
analysis:
  mode: manual
  goals: [network_preservation]
  network_preservation:
    references: ["supplied:discovery_graph"]
    graph_namespaces:
      "supplied:discovery_graph": HGNC-symbols-release-2026
      "dataset:validation_matrix": HGNC-symbols-release-2026
    minimum_common_nodes: 3
    minimum_node_coverage: 0.8
    max_graphs: 50
    max_nodes: 2000
    max_edges: 1000000
    max_memberships: 200000
    max_modules: 10000
    max_comparisons: 100000
    max_pair_work: 100000000
```

Aliases are `dataset:ID` for native graphs and `supplied:ID` for imported graphs.
An empty `references` list compares every graph's frozen modules with every other
graph; specify discovery aliases for a discovery-to-validation question. A pooled
graph stays one graph. Explicit references require only their own memberships;
target modules are computed when separately requested, including independent
module-GO work. A namespace declaration records the asserted exact node-ID
system/release; matching strings do not independently verify biological identity.
Omit unknown namespaces rather than register placeholders such as `NA` or `auto`.
Known species, feature-ID type, annotation-release and genome-build conflicts hold
the affected comparison. Unknown species/namespaces require review. Tissue/assay
and participant/cohort context remain source limitations; shared registered
cohorts are recorded and independent replication is not inferred.

Native inputs use their complete observed thresholded graph. Supplied inputs need
`method.edge_direction: undirected`, `edge_semantics: signed_correlation`,
`edge_coverage: complete_thresholded`, `correlation: pearson|spearman` and a numeric
`threshold`. Reference and target estimators/thresholds must agree. Directed,
partial/unknown-coverage or otherwise incompatible graphs remain held. Every
edge needs a unique undirected pair, finite nonzero weight in [-1,1] and magnitude
at least its declared cutoff. This validates the declared export, without
reproducing historical preprocessing or certifying the underlying correlations.
Supplied identity context uses `scope.species`, `scope.feature_id_type` and
`scope.genome_build`; an optional annotation release belongs in
`method.annotation_release`. The versioned namespace declaration remains explicit.

Each reference module is intersected with the target's declared node universe.
The output retains original/shared node counts and coverage. Comparisons below
the registered common-node or coverage minimum retain a disposition and no edge
metrics; absent nodes are not counted as zero correlation. Among shared nodes:

- Edge retention is shared selected edges divided by reference selected edges.
- Edge Jaccard is shared selected edges divided by their selected-edge union.
- Sign agreement is the same-sign fraction among shared selected edges.
- Thresholded-weight cosine compares the two selected-edge vectors over their
  union, assigning an absent **thresholded edge** zero selected-graph weight.
  It does not impute an unobserved raw correlation of zero.

Undefined ratios/cosines remain `NA`, including empty/empty edge sets. Unsigned
topology can recur despite sign changes, so retention and sign agreement must
be interpreted together. Components and overlapping cliques remain separate,
dependent reference definitions. These summaries have no preservation P values,
FDR or automatic preserved/not-preserved declaration. They do not implement the
permutation-based WGCNA preservation statistics described by
[Langfelder et al.](https://doi.org/10.1371/journal.pcbi.1001057).

`network-preservation.tsv` retains all module/target comparisons, accompanied by
reference memberships, `graph-comparability.tsv`, source/producer/module snapshots
and a Methods record. Incomplete reference memberships stay held; no completed
clique prefix defines a complete reference set. Registered source/total work
bounds retain available audits without selecting favorable graphs or modules.
Modules retain both the original source receipt hash and its complete output
hash map. Cached receipt changes do not make identical graphs a new biological
input, and changed graph outputs cannot silently borrow older memberships.
Full result tables remain local. Finding dossiers receive bounded numerical
context and source links; native membership context is distinguished from a
study-level summary without an assigned reference membership. Neither promotes
an association candidate or invents a functional/causal claim.

To reuse finished comparisons, supply `kind: network_preservation` with
`files.results`, original scope and methods. The TSV columns are:

```text
reference_graph target_graph module_id module_type reference_nodes shared_nodes node_coverage possible_pairs reference_edges target_edges shared_edges edge_retention edge_jaccard signed_agreement thresholded_weight_cosine disposition
```

Use tab separators. Counts, coverage, ratios, undefined values and dispositions
must agree. An explicit source `method.precision_tolerance` in [0,0.005] permits
rounded ratios (default 1e-10); original numbers remain retained and attributed.
Missing source edges are not reconstructed. A supplied primary comparison
satisfies the stage; a supplied sensitivity stays a separate context and does
not suppress missing native comparisons.

Run the offline NumPy/SciPy/PyYAML acceptance:

```powershell
python -B examples/study-pipeline/network-preservation-workflow.py run build/network-preservation-example --jar build/cli/jlinalg-0.3.7.jar
python -B examples/study-pipeline/network-preservation-workflow.py verify build/network-preservation-example --jar build/cli/jlinalg-0.3.7.jar
```

Its synthetic fixtures include native residual-correlation graphs, supplied and
mixed inputs, incomplete node coverage, source-context mismatches, work bounds,
typed stage reuse, rounding and dossier provenance. Independent calculations
use the original matrices/edge tables. They do not establish real cohort
preservation, independence, biological identity or AI scientific appraisal.
The missing-target example delivers a previously unavailable node export
without changing the study, reuses the completed reference graph/memberships,
executes the missing comparison and verifies its next cached reuse.

## Native ranked GSEA and leading-edge pathway networks

The source build now executes `ranked_gsea` natively, using the public
`RankedEnrichment` API. It supports complete gene-level ranking files and
complete scoped signed association/meta-analysis results. This is preranked
**gene-set permutation** analysis. It does not silently replace requested
phenotype, pedigree or block-aware permutation with a gene-set null. See the
[GSEAPreranked documentation](https://gsea-msigdb.github.io/gseapreranked-gpmodule/v7/)
and the [original GSEA paper](https://doi.org/10.1073/pnas.0506580102) for the
method distinction. The later [phenotype-null guide](#phenotype-null-gsea-and-reusable-null-rankings) adds native independent OLS residual permutations and complete model-null ranking reuse.

For a study starting with a complete ranking and no raw cohorts:

```yaml
schema_version: 1
project:
  id: ranked-gsea
  title: Complete GSEA from an existing gene ranking
  description: A complete ranking with its recorded source model and universe.
resources:
  directory: project-resources
  network: offline
  downloads: deny
datasets: []
hypotheses:
  - id: H1
    description: Evaluate prespecified gene sets over the complete ranking.
    analysis:
      mode: manual
      goals: [ranked_gsea, pathway_network, network_modules, module_gene_ontology]
      gsea:
        ranking:
          file: inputs/ranking.tsv
          gene_column: gene_id
          statistic_column: statistic
          coverage: complete
          tested_genes: 20000
          provenance:
            producer: your-analysis-team
        gene_sets: inputs/collection.gmt
        collection_provenance:
          release: your-recorded-release
          citation: your-collection-citation
        species: human
        gene_id_type: symbol
        rank_interpretation: Signed statistic for the recorded contrast and model.
        weight_exponent: 1
        minimum_size: 15
        maximum_size: 500
        permutations: 10000
        maximum_exact_states: 100000
        seed: 1
        adjustment: BH
        curve_plots: 5
      network:
        source_kind: ranked_gsea
        pathway_threshold: 0.25
```

Replace the example's count/provenance with the actual source records. The
file needs unique gene IDs, finite statistics, declared complete coverage and
a `tested_genes` count matching its rows. Selected/significant-only lists and
unknown denominators wait. Table counts do not independently reproduce the
original model, QC or true universe. Align gene namespaces/releases upstream;
the program does not infer gene symbols or biological contrasts from arbitrary
IDs. A provided GMT defaults to collection `GMT`; without one, the default
collection is human GO. Supported built-in GO/Reactome packages require matching
species and namespaces and retain verified manifests/releases. Otherwise supply
a compatible local GMT; MSigDB files are user-supplied under their access terms.

Without `gsea.ranking`, the planner reuses the completed primary all-feature
meta-analysis, or creates separate GSEA operations for individual cohort
association results when no primary pool exists. It does not silently pool
cohorts. The default statistic is signed `beta/se`; an explicit `statistic:
column` with `statistic_column` selects another preserved numeric statistic.
Every valid eligible feature is considered, including nonsignificant features;
no association-FDR selection precedes ranking. Source exclusions/unestimable
statistics and unmapped genes retain dispositions. Supplied selected-only meta
results cannot establish a complete ranking. A declared full tested denominator
larger than the available feature table also waits, even if an export has been
relabeled `coverage: complete`.

Multiple features mapping to the same gene require an explicit coherent
`collapse: mean|median`; the default `require_unique` waits. No best-P probe is
chosen. Isoform/probe effects with different meanings should not be averaged
without a scientifically appropriate protocol. Source gene mapping can use
symbols or Ensembl gene IDs; other namespaces may require a prepared ranking.
Variant-level GWAS beta/SE or nearest-gene SNP assignments are not treated as
independent gene scores: supply an appropriate gene-based, LD-aware ranking.
Preranked gene-position inference is still distinct from LD-aware GWAS
enrichment and does not perform MAGMA or stratified-LD-score analysis.

Tied statistics default to an explicit prerequisite, following the ranking
ambiguity described by the GSEA documentation. `tie_policy: gene_id` permits
deterministic lexical ordering with a mandatory caveat; it does not resolve the
biological ordering. Gene IDs and source inputs are preserved as immutable,
hash-checked snapshots. `weight_exponent: 0` is classic uniform-hit scoring;
1 is the default weighted score, and 2 is supported. The chosen weight must fit
the interpretation of the ranking metric.

The running sum adds normalized absolute-statistic weights for hits and uniform
decrements for misses. ES is its signed largest absolute excursion; extrema
within `1e-12` choose the positive direction. Positive leading edges end at the
maximum; negative leading edges follow the minimum. Null memberships retain
the observed set size within the complete ranked universe. If their complete
combination count is at most `maximum_exact_states`, all memberships are
enumerated. Otherwise seeded sampling without replacement generates the
registered number of null draws, shared by set size. NES divides ES by the mean
absolute same-sign null ES. Exact P values use same-sign tail counts; Monte
Carlo P values use `(tail + 1)/(same_sign_draws + 1)` and never claim zero.

Outputs retain actual null/tail counts, conditional P resolution and 95% Wilson
intervals for Monte Carlo tail probabilities. Those intervals exclude ranking,
null-model and multiplicity uncertainty. BH/BY/NONE applies to the complete
eligible term family; it is **not Broad's pooled-NES empirical GSEA FDR**. Terms
with undefined zero hit weights or insufficient same-sign nulls leave the
family incomplete, produce retained limited results and block official pathway
selection. Terms excluded by prespecified size/universe rules have separate
dispositions. A valid collection with no eligible terms completes with zero
tests; an empty/missing resource is a different input problem.

Native limits default to 50,000 ranked genes, 20,000 sets and 250 million
estimated hit/null work units, including each term's tail comparison against
shared null draws. Hard limits and a five-million-membership
collection bound prevent unbounded dense work. Exceeding a bound waits or
rejects invalid resource input without choosing favorable terms. Registered
precision/work tradeoffs remain visible; reducing permutations cannot be
presented as strong evidence below the resulting simulation resolution.

`ranked-gsea.tsv`, `ranked-genes.tsv`, `leading-edge.tsv`, term/mapping
dispositions and `gsea-analysis.yaml` preserve the full analysis. Up to
`curve_plots` diagnostic curves are written as numeric TSV plus PNG/PDF,
ordered by adjusted P, absolute NES and term ID. This plot budget does not
truncate testing or select manuscript findings. `tested-gene-sets.gmt` preserves
matched membership and feeds the pathway network automatically. To connect
leading edges instead, explicitly set
`network.membership_column: leading_edge_genes`. The graph preserves GSEA's
actual correction and species/namespace, and downstream modules/GO retain the
conditional-null caveats. GSEA term dossiers retain NES, leading edges,
simulation precision and corresponding pathway-module memberships.

Automatic mode infers GSEA and compatible downstream pathway work for supported
molecular matrices/results; `gsea.enabled: false` suppresses that inference.
Explicit goals still request work. Supplied `ranked_gsea` artifacts satisfy the
stage without native refitting. Unsupported phenotype/block nulls require an
appropriate external analysis/import; they are not silently downgraded.

Run `examples/study-pipeline/gsea-workflow.py run NEW_DIRECTORY --jar JAR` and
`verify DIRECTORY --jar JAR` for eight offline synthetic cases. The harness pins
software before execution, independently reconstructs full-list ES/NES,
exhaustive tails, leading edges, BH/BY, network overlap and joint module GO;
checks finite simulation against an exact reference; and verifies curve data,
PNG/PDF exports, immutable inputs and cache reuse. It separately exercises
selected rankings, ties, requested phenotype nulls and zero eligible terms.
These checks establish local conditional software behavior, not Broad/fgsea/R
numerical parity, biological validity or real AI appraisal.

## Held-out risk scores and missing related-phenotype work

The source build registers native `risk_score_evaluation` and
`risk_score_related_phenotypes`. The first can develop a quantitative or binary molecular
score, apply a frozen model/weights/scores, evaluate held-out performance and
test registered related traits. The second completes related-trait testing
when performance has already been supplied. Its input is a frozen score/model;
it does not rerun development or performance estimation.

The [PRS reporting standards](https://pmc.ncbi.nlm.nih.gov/articles/PMC8609771/)
provide a framework for describing populations, development, evaluation and
limitations. Source study/design suitability still needs scientific appraisal.
[Training-only preprocessing](https://scikit-learn.org/stable/common_pitfalls.html#data-leakage)
also applies to feature selection, normalization, imputation and external batch
adjustment. A valid file layout cannot certify how upstream data were prepared.

An explicit participant-row input can start a study without cohort datasets:

```yaml
schema_version: 1
project:
  id: glucose-score
  title: Held-out molecular glucose score
  description: Prespecified development, tuning and evaluation populations.
resources:
  directory: project-resources
  network: offline
  downloads: deny
datasets: []
hypotheses:
  - id: H1
    description: Develop a glucose score and evaluate related cardiovascular traits.
    analysis:
      mode: manual
      goals: [risk_score_evaluation]
      risk_score:
        mode: train
        file: inputs/participants.tsv
        id_column: sample_id
        partition_column: partition
        outcome: glucose
        features: [molecular_feature_1, molecular_feature_2]
        covariates: [age, sex]
        categorical_covariates: [sex]
        group_columns: [family_id]
        resampling_group: family_id
        lambdas: [1, 0.1, 0.01]
        alpha: 0
        refit: true
        missing: training_mean
        bootstraps: 200
        seed: 1
        subgroups: [sex]
        related_phenotypes:
          - {column: BMI, family: quantitative}
          - {column: hypertension, family: binary}
        adjustment: BH
        preprocessing_provenance: Record actual upstream selection and preprocessing here.
```

Replace the illustrative feature list, grouping, phenotype definitions and
provenance with the prespecified study design. The table has unique IDs and
`partition` values `training`, `tuning`, `evaluation` or `excluded`. Every declared
family/cohort/visit grouping must stay within one partition; crossing a boundary
waits before fitting. `resampling_group` must contain every declared related
group, allowing connected independence blocks for intersecting groupings.
For genuinely independent records, explicitly set `independent_rows: true`.
Distinct IDs do not establish unrelatedness or absence of discovery-GWAS overlap.

For existing supported molecular matrices, omit `file`, select those datasets
through hypothesis inputs, and preserve the partition/group columns in their
phenotype tables. The adapter aligns sample IDs, uses dataset `phenotypes` and
`covariates` mappings, and selects the same prespecified matrix feature IDs in
each cohort. It retains alignment exclusions and source hashes. `dataset_id`
and `cohort_id` can be requested as grouping columns. A missing predictor waits;
no favorable cross-cohort intersection or association-FDR selection occurs.
The score operation has no dependency on all-cohort association fitting.
Genetic dosage scoring currently uses a prepared participant-row table.

For quantitative outcomes, native development fits an OLS baseline on the declared covariates,
then Gaussian ridge/lasso/elastic net on its residual outcome and the declared
predictors. This is the recorded two-stage prediction recipe, rather than a
joint unpenalized-covariate elastic-net fit. Alpha 0 is ridge, 1 is lasso.
Feature imputation means and standardization use actual development rows only.
The default missing-predictor policy is `error`; `training_mean` learns means
within each fit. An all-missing training feature remains a prerequisite.

A lambda grid requires a separate tuning partition. Fits and imputation are
learned from training rows; tuning MSE selects the penalty. Exact MSE ties retain
the first, more strongly penalized value in the strictly descending grid.
With `refit: true`, the selected recipe is fitted on training plus tuning rows.
Evaluation rows never choose the penalty, features, means or baseline. This
branch uses explicit holdout partitions; it does not estimate performance by
ordinary or nested CV. Native binary development uses the separately registered
[binomial score recipe](#native-binary-risk-score-development-and-frozen-probabilities).
Survival development still requires a suitable censoring-aware method.

`score-model.yaml` exports the complete frozen quantitative recipe: ordered
features, imputation means, raw-scale weights/intercept, covariate baseline,
categorical levels and a hash-linked development identity/group sidecar.
Keep `score-weights.tsv.training-ids.tsv` with the model. Set `mode: model` and
`model: path/to/score-model.yaml` to apply it elsewhere without refitting.
Known development IDs **and groups** are checked against the new evaluation
population; missing/changed identity sidecars and unseen categorical levels wait.
The component `score-weights.tsv` alone does not contain the whole baseline or
frozen preprocessing recipe.

Other entry points are `mode: scores` with `score_column`, or `mode: weights`
with `weights` and, for genetic weights, `alleles`. Weight files use the
[standalone score format](prediction-scores.md): `variant ea oa weight`, with
optional model/intercept columns. Diploid effect-allele dosages stay in [0,2]; an
explicit allele swap uses `2 - dosage`. Build, strand ambiguity, LD-aware weight
development, ancestry and assay suitability need source verification. Existing
weight training-ID sidecars are checked when present. Supplied entry points
require an `external_independence` statement covering development/GWAS overlap,
upstream preprocessing and evaluation reuse. That statement remains declared,
not independently certified.

Quantitative metrics use frozen predictions and a frozen baseline on the same
eligible held-out rows: RMSE, baseline RMSE and
`1 - SSE_prediction / SSE_baseline`. Negative incremental predictive R-squared
is retained. For supplied quantitative scores, provide `baseline_column` and,
when the raw score is not itself the prediction, `prediction_column`.
The evaluation sample is never used to fit the baseline. Quantitative
calibration intercept/slope are descriptive outcome-on-prediction regressions;
they do not replace frozen predictions.

Raw binary scores report rank AUC with half credit for ties. Explicit
`prediction_scale: probability`, `prediction_column` and `baseline_column`
enable probability/baseline AUC, Brier and log-loss metrics and descriptive
logit calibration when estimable. Values must lie in [0,1]; log loss clips
endpoints to [1e-15,1-1e-15] for numerical reporting. A raw polygenic score does
not become an absolute-risk probability merely because it discriminates cases.
Constant or separated calibration can be unavailable.

The default 200-replicate percentile bootstrap resamples the declared
independent units, retaining all rows in each sampled cluster. Intervals are
conditional on the frozen model and omit development, tuning and selection
uncertainty. Failed/single-class draws remain counted. At least
`max(20, ceil(0.8 * bootstraps))` valid draws are needed for an interval. A setting
of zero explicitly omits bootstrap uncertainty. Registered categorical subgroup
metrics retain small/unestimable subgroup dispositions; their intervals have
no simultaneous-coverage adjustment.

Related quantitative traits use score-plus-covariate OLS; binary traits use
binomial-logit models. Inference uses declared independent rows or cluster CR1
sandwich uncertainty with t(G-1) and at least ten sufficiently numerous clusters.
Few-cluster calibration is not certified. Complete cases are specific to each
registered trait. Constant outcomes, rank deficiency, convergence and insufficient
independent units remain unestimable. If any registered test is unestimable,
`related-phenotypes-limited.tsv` is retained and the official correction family
waits; no convenient subset is promoted. BH/BY/NONE applies to the complete
within-hypothesis related-trait family, separately from score development and
the original association scans. Shared participants and correlated traits remain
explicit limitations. A related phenotype used as a score predictor is rejected
as circular follow-up.

To retain a supplied `risk_score_evaluation` and complete only missing related
tests, register it in `analysis.supplied_results`, provide frozen participant
scores/model/weights, and request `risk_score_related_phenotypes`. Automatic
planning infers this missing branch when related traits are configured. The
target outcome column and frozen baseline are unnecessary for related-only
testing; the outcome identifier still records which score is being interpreted.
Separately supplied `risk_score_related_phenotypes` tables retain their complete
source correction family. Performance metric rows have identities that include
their metric/subgroup, so supplying several metrics does not create duplicate
result identities or an invented FDR family.

`participant-partitions.tsv`, participant input/scores and development identities
are local private artifacts. The evidence index and finding dossiers expose
allowlisted aggregate metrics, full-family related results, source provenance and
mandatory limitations. Default score-dossier projection suppresses identifier or
resampling-unit subgroup dimensions/labels, unestimable rows and subgroup cells
with N below ten; full dispositions remain local. Aggregate projection still
requires the project's disclosure policy. Prediction performance has no invented association P
value. A score can enter significance-based AI prioritization through supported
corrected related-trait evidence; descriptive/null prediction stories require
the existing explicit inclusion policy. Scientific importance and manuscript
depth still require source-linked appraisal.

Storage/work limits cover complete fitting, bootstrap and registered related
families. Dense source tables have a 128-MiB bound, numerical designs a
20-million-cell bound, and models have a 2-MiB metadata bound. Defaults allow
2,000 features, 100 covariates, 20 grouping/subgroup columns and 100 levels per
categorical covariate. Limits wait without selecting favorable participants or
tests. Automatic raw-matrix studies request score work but leave missing design,
features and partition inputs explicit; `risk_score.enabled: false` suppresses
that inference. Manual explicit goals remain requests for work.

`examples/study-pipeline/risk-score-workflow.py run NEW_DIRECTORY --jar JAR`
and `verify DIRECTORY --jar THE_PINNED_JAR` exercise ten offline synthetic cases.
The NumPy/SciPy reference independently reconstructs baseline/ridge fitting,
tuning, training imputation, clustered percentile intervals, CR1 inference and
the complete BY family. It verifies direct/matrix equivalence, full frozen-model
reuse, allele swaps, raw/probability binary metrics, supplied evaluation with only
missing related work, source hashes and resume. Executable and verifier are
pinned before execution. These are conditional software checks, not R/other-risk-
tool parity, clinical validity, ancestry transportability or real AI appraisal.

## Rare-variant study branches and unsigned results

Newly resolved genotype-matrix studies include a separate rare-set branch by
default. `analysis.rare_variants.enabled: false` is an explicit decision to omit
it, used by the focused genotype-QC teaching example. Missing annotation or masks
leave a documented prerequisite while independent single-variant work continues.
The single-variant MAC default is 20; the separate set-score default is 0, with
monomorphic/uninformative variants still excluded. The single-marker result file
is never used to reconstruct low-MAC set inputs.

For each genotype dataset, supply `genome_build`, `annotation_release` and
`files.variant_annotation`. Declare phenotype units in
`rare_variants.trait_units` or `dataset.metadata.phenotype_units`; genotype/assay
units and regression-effect units cannot substitute for phenotype units.
This tab-delimited annotation contains
`variant_id`, `set_id`, and `consequence`; keys are normalized, 1-based
`CHROM:POS:REF:ALT`. Consequence labels separated by `;`, `|` or `,` are matched
against the registered masks. Sets must have one chromosome, compatible annotation
releases and unambiguous biallelic alleles. Annotation is read before testing; no
association P value determines membership. For example:

~~~yaml
analysis:
  association: {model: ols, genotype_kind: imputed}
  rare_variants:
    trait_units: kg/m2
    primary_test: acat-o
    tests: [burden, skat, skat-o, acat-v, acat-o]
    minimum_mac: 0
    masks:
      - {id: lof, consequences: [stop_gained, frameshift_variant], maximum_maf: 0.01}
      - {id: lof-rare, consequences: [stop_gained, frameshift_variant], maximum_maf: 0.001}
~~~

These consequence labels are examples; use the exact vocabulary and reviewed
confidence/transcript rules of the supplied annotation release. The native
`rare_variant_masks`, `rare_variant_scores` and `rare_variant_analysis` operations
run independently of the single-variant association output. Scores use the
prefit-selected participants and explicit quantitative Gaussian OLS or GRM REML
model, with independent set MAC, missingness, INFO and HWE rules. INFO inherits
the declared association-quality threshold where applicable, including the
imputed-data default of 0.8. Gaussian export rejects 0/1 outcomes and unsupported
links/dependence; binary/survival and nonautosomal ploidy require appropriate
calibrated adapters or reviewed result imports. This route does not run
SAIGE/REGENIE automatically.

Mask MAF limits are applied to observed frequencies in the selected score-pooling
scope. Burden/SKAT/SKAT-O/ACAT-V/ACAT-O outputs retain native component, coverage,
calibration and weight records. ACAT-V's default pooled-MAC collapse threshold
of 10 is distinct from set eligibility and the single-marker MAC 20 cutoff.
The default covariance span is 1,000,000 bp, maximum retained window size is
2,000 variants, and SKAT-O uses 10,000 seeded simulations. A window that cannot
cover the declared sets, or a capacity limit, cannot silently drop pairs or
replace missing covariance with zero. Simulation resolution and approximate
calibration limit inference; increase justified budgets or select a supported
calibration before interpreting very small P values. See the
[ACAT method workflow](acat-rare-variants.md) and
[SAIGE set-test documentation](https://saigegit.github.io/SAIGE-doc/docs/set_step2.html)
for the distinction between masks, allele counts and calibrated tests.

`rare-results.tsv` identifies scope, set, mask, test and result type. Its adjusted
P values use the full eligible set × mask × registered-test family separately
within each cohort, discovery-only pool and all-cohort primary scope. The
registered primary test defaults to ACAT-O; other tests remain visible. The
primary rule never chooses whichever test gives the smallest P. The evidence
index retains these adjusted values when displaying the top primary-test rows.
These are set/mask tests, not an automatically calibrated gene-level omnibus
across masks or across phenotypes.

Multiple discovery cohorts are pooled without validation scores. Discovery
selection is frozen, then `rare-replication.tsv` applies the declared all-results
or discovery-subset validation correction. Missing validation rows remain
unavailable. Unsigned tests have `NA` for beta, SE and direction, and bypass
beta outlier QC, effect scatterplots and inverse-variance pooling. Native burden
effects are retained; `require_burden_same_sign` defaults to false and should be
enabled only for comparable burden definitions/orientations. Gene/set replication
does not establish the same causal alleles or mechanism.

`rare_variants.leave_one_cohort_out` defaults to true. With at least three
independent cohorts, the study repeats each permitted omission while retaining
at least two cohorts and respecting `minimum_cohorts`. These runs remain in
`native/leave-one-out-<dataset>/` for score inputs; P-only inputs repeat only
their explicitly approved P-value combination. `rare-sensitivity-results.tsv`
retains every eligible test and the complete, separately corrected family for
each omission. `rare-influence.tsv` compares primary and sensitivity P/adjusted P,
available burden effects, cohort/variant counts and coverage; unavailable
comparisons and unsigned direction changes remain `NA`.

Omitting a cohort can change observed frequency eligibility even when the
annotation mask definition is fixed. Inspect changed variant counts and the
native membership/QC outputs before attributing a changed result solely to an
influential effect. `rare-analysis.yaml` and the evidence index retain the
influence summary and primary-results hash. The primary table is unchanged;
omissions are exploratory diagnostics, not an extra search for a preferred
significant subset. There is no confirmatory correction across omissions or
fabricated Q/I-squared for unsigned tests. A disabled/inapplicable analysis or
an entirely unestimable family has an explicit disposition and header-only
comparison tables where appropriate.

Existing score/covariance inputs use `stage: rare_score_summary` with
`files.scores`, `files.covariance` and the annotation fields above. Version-1
quantitative model metadata is mandatory; conflicting trait identity, units,
build, calibration or covariance layout fails instead of silently pooling. Score
index companions are hashed for cache invalidation. If INFO QC is required,
reviewed `metadata.minimum_info_applied` must establish the upstream threshold,
because the score format does not carry per-variant INFO. Imported summaries
cannot recover variants discarded upstream.

P-only set inputs use `stage: set_association_results` and `files.results` with
`feature_id`, `mask_id`, `test`, and `p_value`. Declare
`metadata.complete_testing_family: true` only for a reviewed complete eligible
export. Multiple sources additionally require distinct cohort IDs, matching
`genome_build`, `metadata.set_definition_id` and `metadata.trait_id`, and an
explicit `rare_variants.unsigned_pooling: acat` choice. This combines calibrated
cohort P values; it estimates no pooled effect or directional heterogeneity.
Signed burden imports may supply beta/SE and `result_type: signed_burden`;
`unsigned_set` never acquires invented effects. P-only replication remains
meaningful without a sign. Missing required direction for a signed criterion is
unavailable, rather than a failed replication. Source model/calibration,
completeness and biological comparability still require scientific appraisal.

Empty eligible families remain `not_estimable`, with retained diagnostics and no
fabricated null estimates. The current source build supports the declared
Gaussian and typed-summary routes; automatic annotation acquisition, sparse
binary adapters, cross-phenotype families, ancestry-specific masks and all
downstream rare-set enrichment/sensitivity orchestration remain incomplete.

## Result metadata through QC and pooling

Signed-result normalization preserves supported aggregate fields alongside
`feature_id`, `beta`, `se` and `p_value`. Conventional headers such as `N`,
`n_samples`, `OBS_CT`, `MAC`, `MAF`, `EAF` and `INFO` map to canonical fields.
Alleles, coordinates, call/missing counts, HWE summaries, degrees of freedom,
confidence intervals and heterogeneity fields are retained when available.
Missing values remain `NA`; file length and cohort enrollment never substitute
for an analysis sample size. Unsupported source columns remain in the immutable
raw file. Arbitrary participant identifiers and free-text columns are not copied.

An ambiguous alias requires an explicit source mapping. Model/calibration/unit
labels also require explicit mappings, for example:

```yaml
columns:
  result_metadata:
    n: analyzed_N
    n_cases: analyzed_cases
    n_controls: analyzed_controls
    effect_allele: EA
    imputation_info: MACH_R2
    model: fitted_model
    effect_units: effect_units
```

Mapped columns must exist. Counts, probabilities, MAF, intervals and applicable
joint count constraints are checked. Invalid metadata follow the declared
`analysis.harmonization.invalid_rows` policy. A missing value is distinct from
an invalid supplied value. Imputation quality is preserved with its provider's
definition; copying an INFO/R-squared number does not certify its calibration.
The schema lists every supported field.

Each normalized table has a hash-linked YAML audit and an exclusion TSV under
`result-normalization/`. Their counts/hashes describe numerical eligibility
before optional effect screening and branch-specific universe selection.
Pre-effect-QC snapshots and derived branches retain the complete normalized
header. Existing sealed historical tables are not modified.

Study meta-analysis retains the backend output as `meta-estimates.tsv`, adds
coverage columns to `meta-analysis.tsv`, and records the transformation in
`meta-result-metadata.yaml`. `cohort-result-metadata.tsv` retains source-reported
per-feature/per-cohort fields. `reported_n_sum`, minimum/maximum and known/missing
cohort counts describe the contributing records. The sum is **not a deduplicated
participant count or effective N**. No missing N is reconstructed from degrees
of freedom. Pooled estimates, uncertainty and significance remain unchanged;
cohort dependence still needs its own appropriate analysis. Finding dossiers
retain these fields and an explicit coverage caveat.

## Study-wide impact assessment and manuscript selection

Publication-oriented auto plans now include one `finding_inventory` and one
`impact_assessment` across active hypotheses. An explicit manual plan can request
`goals: [impact_assessment]`. Enabling manuscript assistance also requests this
shared assessment; the manuscript draft waits for it. Cohort/phenotype analyses
retain their own hypotheses and testing families. Selection is 3–5 distinct
principal findings across the paper, with an explicit reason for fewer.

The uncapped `finding-inventory.jsonl` and `.tsv` retain primary, null, invalid
and effect-QC-excluded findings. Candidate eligibility uses the complete primary
testing family or a declared null/discordant finding, without a top-ten preview
or literature cap. Dossiers include source-linked statistics, annotation, design,
replication/sensitivity context, applicable genetic/literature observations and
mandatory caveats. Unsigned rare tests retain their stored joint-family adjusted
P values and do not acquire an effect direction. GO/pathway findings retain their
own term/background statistics and dependence caveat. Unknown upstream models,
sample sizes, replication and locus grouping remain explicit limitations.

For a configured local model, a fragment is:

```yaml
resources: {network: online, downloads: deny}
assistance:
  enabled: true
  provider: local
  endpoint: http://127.0.0.1:8080/v1/chat/completions
  model: YOUR_USER_MANAGED_MODEL
  impact: true
  manuscript: true
  impact_batch_size: 8
  impact_max_payload_chars: 120000
  impact_max_calls: 100
  impact_max_output_tokens: 8000
  manuscript_max_output_tokens: 24000
```

`local` and `llama_cpp` require literal loopback, an explicit port and that exact
path. Remote credentials are not read. Remote-deny policy remains compatible
with this local route; an explicit offline network policy blocks connections.
OpenAI, Gemini and configured HTTPS-compatible providers retain the existing
credential/disclosure controls. Browser packets retain the named-human import
boundary; an agent cannot attest browser disclosure. A nonempty assessment
without a permitted configured provider waits, preserving earlier analyses.

AI evaluates every supplied candidate using reliability, effect relevance,
novelty, biological coherence, complementary evidence, distinctness, limitations
and the next experiment. Responses must cover every supplied ID, cite supplied
sources and preserve mandatory caveat IDs. Intermediate comparisons advance at
most the manuscript finding limit; subsequent comparisons use complete advancing
dossiers and prior assessments. Every leaf assessment and elimination remains
auditable. This is a hierarchical judgment procedure, not a calibrated impact
probability or a guarantee of a globally optimal ranking.

Payloads are partitioned within the declared size/character limits. A dossier
that cannot fit is not truncated. `impact_max_calls` limits new interactions per
attempt; previously completed nodes are reused only after their payload/config,
parent manifest and every retained output hash verify. Missing responses or
exhausted budgets leave `impact-status.yaml` and no completed manuscript
selection. Provider/disclosure prerequisites stop further interactions.

`impact-assessment.yaml` retains selection, complete comparison records and
review status. `impact-candidate-dispositions.tsv` explicitly records every
candidate's selected/not-selected disposition and last comparison artifact.
`manuscript-outline.yaml` binds selected IDs to section/finding word allowances
around a flexible default 8,000-word main-text target. Section and per-finding
allowances are adjustable starting allocations. Provider output tokens are a
separate budget. AI drafts are counted by recognizable main-section headings;
native revisions are counted again after bindings, citations and abbreviations.

The main-text target records overages for editorial review while preserving the
complete draft. Set an explicit journal limit with:

```yaml
project:
  manuscript_policy:
    max_main_words: 8000
    word_limit_mode: strict  # use target for an adjustable planning target
```

New default manuscripts use `target`. An explicitly supplied legacy
`max_main_words` without a mode retains `strict`; set the mode explicitly to
change that intent. Neither mode limits supplement prose. No text is silently
truncated or automatically moved to change a word count.

Native manuscript revisions can retain an extended discussion as a
`section: supplementary` entry in the existing `sections` list:

```yaml
sections:
  - id: finding-1-extended
    section: supplementary
    title: Extended discussion of finding 1
    text: Complete literature synthesis, mechanism, alternatives and validation plans.
    sources: [H1.meta_analysis]
    rationale: Preserve the full finding discussion alongside its main-text synthesis.
```

Replace the illustrative source with an existing verified evidence ID. These
notes use the same source, binding, citation and attribution checks as main
prose. They render in `artifacts/supplement.html` with explicit links from the
manuscript, and `artifacts/word-counts.yaml` records main and supplementary
prose separately. A `finding_ids` list can connect a note to frozen inventory
findings when a study-wide impact assessment is available. A note cannot carry
main-text roles such as `discussion_role: principal_finding` to fill a finding
slot. Retain the principal conclusion and all material caveats in main text.

Thorough discussion is a scientific requirement, not a paragraph quota. The
drafting instructions ask for exact feature/isoform/tissue context, study
effects and uncertainty, relevant primary studies, a stepwise mechanism,
demonstrated versus inferred links, null/counterevidence, alternatives and
concrete next experiments. See the
[discussion-depth specification](analysis-pipeline-specification.md#thorough-principal-finding-discussions).

Declare `files.finding_groups` as a table with `feature_id`, `group_id`,
`rationale`, optional `hypothesis` and optional `include_null: true|false`.
It groups related SNPs/loci, isoforms or other findings before manuscript slots
are selected, and can register a consequential null finding. Existing gene
annotations provide conservative shared-gene grouping; they do not establish
LD or a common causal mechanism. Unresolved variant grouping carries a caveat.
An override cannot revive an invalid or primary-QC-excluded estimate.

Native manuscript templates carry `finding_ids` in linked Results and principal
Discussion paragraphs. A scientist's change to selected findings or order needs
`impact_selection_override_reason`; fewer findings still need
`discussion_selection_note`. `impact-selection-review.yaml` verifies IDs,
distinct groups, Results links, explicit overrides and required partial-region
caveats. This structural review does not certify biological interpretation or
source appraisal. Incomplete current assessments remain review needs.

The reproducible [impact protocol fixture](https://github.com/robbyjo/JLinAlg/blob/main/examples/study-pipeline/impact-workflow.py)
uses an explicitly labeled deterministic loopback server. It tests transport,
cross-batch coverage, source/FDR lineage and manuscript gating; it does not run
a real AI model or create human disclosure attestations.

## Effect-size quality control before downstream analysis

Numerically finite coefficients can still be extreme: a nearly constant predictor,
ill-conditioned design, separation, unit mismatch or unstable fit can produce a
very large coefficient and SE. A large raw coefficient is not itself proof of a
bad association, and summary statistics cannot establish the cause. Do not hide
these points by clipping a plot or choose exclusions to improve correlation or
significance. Inspect predictor variation, units and upstream fit diagnostics.

JLinAlg now applies an auditable, conservative effect-size QC screen to supplied
association results during harmonization and to newly fitted association results
before annotation. The defaults are:

~~~yaml
analysis:
  harmonization:
    effect_outliers:
      enabled: true
      robust_z: 100
      min_features: 20
      # max_abs_effect: 1000  # optional; choose only with known effect units
~~~

For each dataset separately, using all numerically eligible association rows,
the center is the median beta and the robust scale is
`1.482602218505602 * median(abs(beta - median(beta)))`. If this scale is zero
or nonfinite, use `(Q75 - Q25) / 1.348979500392164` with linearly interpolated
quantiles. Exclude estimates with `abs(beta - center) / scale > robust_z`.
This is a configurable QC heuristic, **not** a calibrated hypothesis test or a
universal biological plausibility cutoff. The conservative default of 100 is
intended to catch gross extremes, not trim an ordinary heavy tail. Uniform
changes of effect units do not change the relative selection. It uses neither
P values nor agreement with other cohorts. Relative screening is explicitly
skipped below `min_features` or without a positive finite robust scale; no
epsilon denominator is invented. An optional `abs(beta) > max_abs_effect`
rule still applies to these families. An approved plan with effect QC enabled
now includes an automatic, isolated unfiltered sensitivity branch. Set
`enabled: false` only when the declared primary analysis itself omits this screen.

The operation writes `effect-outlier-summary.tsv`,
`effect-outlier-exclusions.tsv` (dataset, feature, beta, SE, P, robust score and
reason) and `effect-outlier-qc.yaml` (effective settings, counts, fallbacks,
skips and caveats), all included in its output manifest and Methods inventory.
The source files are untouched. `pre-effect-qc-results/*.tsv` retains the
numerically/status-eligible estimates before effect screening. Primary
comparisons/correlations, discovery/replication, FDR, meta-analysis, cohort-omission
sensitivities, enrichment, MR and colocalization selection consume QC-eligible
estimates. With `common_universe: intersection`, the intersection is computed
**after** QC within each operation's selected cohort set. Otherwise only
the flagged dataset estimate is removed; valid estimates in other datasets
remain eligible under the usual minimum-cohort rules. The testing family is
the retained eligible family. This rule also applies to later matrix-association
outputs through derived branch-specific input tables, without rewriting the
upstream association results. Discovery-only pooling computes its family using
discovery cohorts; missing validation coverage cannot delete a discovery test.

The `sensitivity_analysis` operation retains `effect-qc-unfiltered/` alongside
other sensitivity outputs. It repeats cohort comparisons, any completed primary
meta-analysis, discovery-only pooling and validation with only the effect screen
disabled. Each branch recomputes its declared eligible universe and full BH/NONE
testing family. `feature-sensitivity.tsv` records retained estimates, adjusted
P values, significance and directions; `replication-sensitivity.tsv` compares
discovery selection and validation. `decision.yaml` records family denominators,
the precise scope and `primary_unchanged`. Upstream sample/genotype QC and invalid
or failed estimates remain excluded. No unfiltered finding enters primary
enrichment, MR or manuscript selection automatically. Missing/tampered snapshots
cannot silently substitute a smaller cohort set.

Because selection uses estimated effects, it is not an independent-filtering
procedure that guarantees unchanged FDR calibration merely by avoiding P values.
Prespecify the policy where possible and retain an unfiltered sensitivity analysis.

Record a post hoc QC change as an amendment, review exclusion counts and
unit/model diagnostics, and review the labeled unfiltered sensitivity before
changing scientific conclusions. Entirely excluded primary families are retained
as not estimable; the sensitivity does not replace them. New software has a
different cache identity and preserves earlier attempts rather than reusing
their outputs as current evidence. It does not overwrite sealed results, redraw
historical figures or repair an already completed manuscript. Never
claim old results used this screen based on the current software defaults.

## Probe annotation and genomic locations

The source build assesses annotation during result harmonization (or after an
association scan). It writes `feature-annotation.tsv` and
`annotation-summary.yaml`, both covered by the operation manifest. A feature can
be `located`, `missing_location`, `unmapped`, `ambiguous`, or
`incomplete_across_datasets`. Unresolved annotation is reported while association
and replication calculations continue.

Use existing dataset fields plus two optional declarations:

~~~yaml
datasets:
  - id: blood-results
    type: transcriptomics
    stage: association_results
    feature_id_type: probe
    genome_build: GRCh38
    coordinate_system: 1-based-closed
    annotation_release: laboratory-probes-2026-09
    files:
      results: blood-associations.csv
      annotation: probes.tsv
    columns:
      feature_id: Isoform_ID
      effect: Fx_BMI
      standard_error: SE_BMI
      p_value: P_BMI
      annotation_feature_id: Probe
      annotation_gene_id: EnsemblGene
      annotation_gene_symbol: Symbol
      annotation_chromosome: Chromosome
      annotation_start: Start
      annotation_end: End
~~~

Column mappings are unnecessary when the annotation table already has
`feature_id`, `gene_id`, `gene_symbol`, `chromosome`, `start`, and `end`.
`position` may replace start/end for a point annotation. Gene ID/symbol columns
are optional for coordinate-only annotations. `genome_build` may instead be a
table column, but conflicting table and dataset declarations fail. Table
coordinates require `coordinate_system`; BED intervals must declare
`0-based-half-open` and have both start and end. Output intervals are normalized
to `1-based-closed`. An omitted convention cannot establish a usable location.

`files.annotation` also accepts `.gtf` and `.gtf.gz`. GTF gene and transcript rows
provide their defined 1-based coordinates; declare `genome_build` separately.
An explicitly versioned transcript ID must match exactly. Unversioned IDs can
match the declared annotation, with multiple mappings retained as ambiguous.
The [GENCODE GTF format](https://www.gencodegenes.org/pages/data_format.html)
defines these fields. A transcript span is useful for locus discovery; it is
not necessarily the exact splicing junction or intron quantified in the study.
An sQTL follow-up must match the actual measured molecular feature.

For human Ensembl gene/transcript IDs, a declared modern GRCh38 GENCODE release
can trigger the existing public metadata download to resolve gene names. This
metadata provider does **not** supply genomic intervals: provide a local GTF or
annotation table for those. Lifted GRCh37 and nonhuman resources require explicit
local annotations. Resource/source hashes and declarations are recorded.
JLinAlg does not infer genome build from a gene symbol, CpG probe, or rsID.
GWAS result tables with explicit chromosome/position columns can supply their
own coordinates once build and coordinate convention are declared.

Set `feature_id_type: gene_symbol` only when the study IDs really are gene
symbols. Arbitrary probe IDs are never silently interpreted as gene symbols.
The small tutorial uses fictional gene symbols and therefore still needs real
annotation before any genomic follow-up.

## Reading findings and choosing the next analysis

`index/index.html` contains bounded previews of the primary result tables,
discovery/replication comparisons, meta-analysis, and available enrichment.
Feature rows include mapped gene IDs/symbols and mapping status. Full tables and
their SHA-256 hashes are linked. The displayed BH-FDR 0.05 counts are descriptive;
the approved plan retains the actual hypothesis selection/testing rules.
Each retained run has its own result ID; an older run is not silently replaced.

Waiting operations display their required inputs. For non-variant omics results,
variant scoring is `not_applicable` and contributes to a separate skipped count.
Auto-mode source discovery records phenotype-matched GWAS studies and nearby
catalogue SNPs, QTL service availability and tissue candidates. Reviewed canonical
regional inputs can drive automatic genetic inference; arbitrary full-genome
source selection/conversion remains separate. See the [bioinformatics workflow](bioinformatics-evidence.md).
Manual plans without source discovery still report `catalog_search_status: not_run`,
not a negative search. Explicit `analysis.operations` adapters remain available.

Diagnostics write `next-steps.yaml` with each proposal's trigger, required
inputs, and source artifact. Current rules propose scale/model audits for
effect-scale mismatches, uncertainty and power review for near-zero replication,
and fixed/random-effects review for widespread heterogeneity. With at least
three explicitly identified cohorts and a completed primary meta-analysis,
leave-one-cohort-out replication and meta-analysis run automatically as
registered influence diagnostics. Confirm comparable estimands before pooling; fitting
a between-cohort slope is not a justified way to correct measurement scales.
These are proposals, not completed analyses. Record the selected change through
an `analysis_requests` amendment, specify the operation or execute externally
with provenance, and approve the resulting plan. Subgroup analyses additionally
need enough cohorts and relevant metadata; result-only inputs cannot refit
participant-level covariate or batch-adjustment models.

## Advisory AI while JLinAlg controls execution

An optional advisor can suggest related phenotypes or sensitivity analyses
without receiving execution authority. JLinAlg constructs a bounded request,
asks an official API for structured advice, and checks proposed operations against registered methods,
available inputs, and the approved plan. Gemini supports JSON-schema-constrained
[structured outputs](https://ai.google.dev/gemini-api/docs/structured-output);
schema conformance alone does not validate scientific claims.

A general-topic request might contain only a public phenotype term, modality,
and tissue, with no cohort names, gene hit list, statistics, file paths, study
description, or patient records. More detailed advice may require a separately
permitted aggregate-results request. Even a topic-only request discloses the
research topic; keeping execution in JLinAlg does not remove that transmission.
`deny_all` must prohibit all remote advisor requests. A local model can be used
under a no-network policy. Provider terms depend on the selected service and
configuration; assess the [Gemini API terms](https://ai.google.dev/gemini-api/terms)
when implementing the connector.

Advice should include proposed action, rationale, evidence sources, required
inputs, expected benefit, and limitations. Exact prompts/responses, model ID,
timestamps, disclosure manifests, and accepted/rejected decisions enter the
audit trail. JLinAlg may execute already-approved, eligible operations; new
phenotypes, hypotheses, or changes to the estimand require an amendment. An
empty valid response, an unavailable provider, denied transmission, and a search
that was never run have different recorded dispositions. When public catalogs
are downloaded as whole reference snapshots and queried locally, private locus
and phenotype selections do not need to be sent as query parameters.

The implemented adapter sends allowlisted aggregate association/meta-analysis
previews, phenotype labels and up to ten unambiguously annotated top genes.
It does not yet implement a separate topic-only mode or validate phenotype
ontology IDs automatically. Retrieval citations are retained, but their scientific
claims still require review. See the configuration and privacy boundaries below.

## Data dictionaries, optional assistance, and first drafts

An optional local YAML or JSON dictionary supplies the same `columns` mappings
as the study contract. For example, `dictionary-bmi.yaml` may contain:

~~~yaml
columns:
  feature_id: Isoform_ID
  effect: Fx_BMI
  standard_error: SE_BMI
  p_value: P_BMI
descriptions:
  Fx_BMI: Association coefficient for BMI, on the declared analysis scale.
  SE_BMI: Standard error of that coefficient, not its variance.
~~~

Reference it using `datasets[].files.data_dictionary: dictionary-bmi.yaml`.
Explicit `datasets[].columns` values take precedence and each override is
recorded. Resolution snapshots the dictionary, mappings and SHA-256. Editing a
dictionary after plan approval stops execution: save a versioned file and amend
the dataset reference instead. Free-text descriptions are local context, never
instructions to choose a model, change an estimand, or transmit patient data.
CSV/TSV dictionaries are not currently supported. For a file containing BMI and
obesity results, use one dictionary/view per phenotype and explicit mappings.

Optional assistance is disabled unless enabled in the approved study:

~~~yaml
project:
  data_policy:
    llm:
      remote_transmission: aggregate_results  # or deny_all
      remote_patient_level_data: deny
assistance:
  enabled: true
  provider: browser          # default if omitted; no key, account or network to prepare
  next_steps: true
  literature: true
  manuscript: false           # explicit true permits an unreviewed HTML draft
  response_files:             # predeclare one new receipt directory per exchange
    - responses/H1-advisor/receipt.yaml
    - responses/H1-literature/receipt.yaml
  allow_analyses:
    - fixed_random_comparison
    - leave_one_cohort_out
~~~

Browser handoff prepares a local HTML/text packet. The scientist reviews the exact
prompt, submits it manually in an installed modern browser, then imports the
response and repeats the run command. Linux Links can view the packet, but is not
a dependable client for modern AI websites. See the [browser handoff and optional
API authentication tutorial](bioinformatics-evidence.html#complete-the-browser-handoff)
for commands, required attestations, immutable receipts and audit limitations.
No content is submitted by the handoff adapter. Assistance remains opt-in.

For an explicitly selected API provider, supply credentials securely through the
environment. Each requested advisory operation makes at most one provider request
per attempt; calls/search may incur
provider charges. Repeating a timed-out request can incur another charge if the
provider processed it before the local timeout. There is no browser scraping,
login automation, provider fallback, or execution of model-generated code.

The registered fixed/random comparison runs locally when explicitly listed in
`allow_analyses`, even with `enabled: false` or an unavailable LLM. Leave-one-
cohort-out influence analysis is automatic for a hypothesis with at least three
explicitly identified cohorts and a completed primary meta-analysis. It removes
each cohort in turn, reruns the approved meta-analysis and, where discovery and
replication roles remain, reruns replication and its plots. The resulting
`sensitivity-comparison.tsv` records the full set of omissions, replication-rate
and mean-I-squared deltas. A cohort is an `odd_one_candidate` only when omission
improves both available criteria. That candidate is featured in a labeled
sensitivity subsection, never silently substituted for the all-cohort primary
analysis. Promotion to primary requires a prespecified rationale or a reviewed
study amendment. Missing prerequisites appear in the index. These remain
exploratory influence diagnostics; comparable effects and valid cohort-dependence
assumptions are still required.

Other AI suggestions receive `requires_scientist_amendment` or
`unsupported_requires_scientist_review`. New phenotypes, hypotheses and changes
of estimand are never silently approved. Configure an existing project through
an amendment, not by rewriting its base `study.yaml`:

~~~yaml
schema_version: 1
amendment:
  id: advisor-01
  base_study_hash: CURRENT_RESOLVED_STUDY_HASH
  rationale: Permit a bounded review of aggregate findings.
changes:
  configure:
    assistance:
      enabled: true
      provider: browser
      next_steps: true
~~~

Save as `amendment-advisor-01.yaml`, use `study amend ... --check`, then approve
with `--approved-by PERSON`. `changes.configure` supports `assistance`,
`resources`, and project metadata/privacy; normal dataset/hypothesis changes
continue to use `changes.update`.

Every attempted advisory operation records `llm/interaction-001/metadata.yaml`
and its disposition. When applicable, it also stores the exact `query.txt`,
credential-free `request.json`, redacted `response.json`, `answer.txt`, and
`decision.yaml`. Metadata includes UTC start/end times, requested and returned
model, provider, response ID, endpoint, HTTP status and request/response hashes.
API keys are headers, never URL parameters or audit fields. The operation
records an attempted request separately from confirmed HTTP receipt: DNS/TLS
failures and timeouts leave `request_sent: unconfirmed`, not proof of delivery
to a model. A proxy HTTP response also does not establish downstream delivery.
The operation
manifest hashes these files; `SESSION_HISTORY.md` records attempted remote calls
with the LLM actor/provider/model. Reused answers are attributed to their original
attempt, not misrepresented as a new call.

Literature review uses [OpenAI web search](https://developers.openai.com/api/docs/guides/tools-web-search)
or [Gemini retrieval metadata](https://ai.google.dev/api/generate-content).
Only citations returned in provider retrieval metadata enter the reference list;
references invented in model prose are not accepted as retrieved evidence.
No citations, a failed request, and a request not sent are distinct states.
This is a bounded exploratory search, not a systematic review or independent
verification that a cited paper supports each claim.

With `manuscript: true`, `manuscript.html` contains a clearly labeled unreviewed
first draft, supplied aggregate findings and links to `source-map.yaml`, the
operation manifest and model record. The prompt requests Background, Methods,
Results, Discussion and Conclusion, and missing facts must remain placeholders.
The draft may be incomplete or wrong: every number, method, citation and causal
claim needs scientist review. No submission, authorship or ethics approval is
inferred. Instructions and returned HTML-like text are escaped, not executed.

## Publication citations and parameter reporting

### Explaining observed results from the study contract

JLinAlg records `study-context-review.yaml` for advisor requests and manuscript
revisions. It compares declared tissues, compartments, assays, preparation,
population metadata, upstream models, covariates, technical factors, scales,
annotation versions and cohort relationships. Missing values are unknown, not
evidence that cohorts used identical settings. The inventory lists omitted
fields for local review rather than silently assuming that all context is covered.

With `assistance.enabled: true` and `assistance.next_steps: true`, the existing
advisor step asks the configured LLM to consider this context against **all
observed patterns**, including heterogeneity, discordant replication, null or
unexpected findings. The advisor must identify the result and metadata fields,
separate facts from explanations, identify competing explanations/confounding,
and propose a sensitivity check or scientist question. Editorial review receives
the same context under its exact-payload approval. For example, a PBMC/whole-blood
contrast can motivate tissue-sensitive follow-up; it cannot establish a tissue
effect independently of cohort if only one cohort used PBMCs.

The structured projection, exact prompt, response, model, timestamps and decisions
are audited. Free-text notes, arbitrary dictionaries, file contents/paths,
donor/specimen identifiers and credentials are not automatically sent. Relevant
omitted context requires a scientist-reviewed summary; a field allowlist is not
a guarantee that user-entered metadata is non-sensitive. Existing deny-all,
offline and manual-browser-handoff rules remain in force. No LLM is invoked if
assistance is disabled. Explanations never authorize cohort exclusion, model
changes or analysis execution by themselves.

### Claim-adjacent references

Native `study manuscript` revisions use numbered citations in first-appearance
order, reuse the same number for repeated references, and link superscript
brackets to the **References** section at the end of the paper. The reference
list follows Nature-style numerical ordering and bibliographic fields; the
brackets are an explicit display choice, not a claim of exact journal typesetting.
The separate reference catalog retains retrieval provenance and uncited candidates.

Every statement drawn from a journal needs a citation, including Methods,
bioinformatics context beside Results, and Discussion—not only Introduction.
Place the token beside the supported claim, before punctuation:

~~~yaml
citation_policy: strict
sections:
  - id: methods-fdr
    section: methods
    methods_role: discovery_replication
    citation_basis: literature
    text: >-
      We used Benjamini–Hochberg adjustment[[cite:10.1111/j.2517-6161.1995.tb02031.x]],
      with FDR < 0.05.
    sources: [H1.discovery_replication]
    rationale: Cite the performed method without changing the analysis.
    parameters: {method: BH, alpha: 0.05}
~~~

`citation_basis` may be `literature`, `study_results`, `study_design`,
`interpretation`, or `not_applicable`. Mixed prose containing a journal-derived
claim is `literature`. `supporting_evidence` entries have the same citation fields
for their `relationship` text. Multiple references in one token are separated
by semicolons. DOI, `pmid:ID`, and retrieved reference IDs are supported.

Strict mode rejects missing classifications, uncited declared literature prose,
unplaced reference assignments and unresolved identifiers **before creating the
revision output**. Compatibility mode (`citation_policy: review`, the default
for older requests) preserves older prose, displays assigned references and
records gaps in `citation-review.yaml`. It does not certify the draft as ready.
No deterministic checker can establish that a citation supports every sentence
or detect all undeclared borrowing; scientist review is still required.

Acquire missing references through a separate audited native command:

~~~yaml
# references.yaml
schema_version: 1
references:
  - id: 10.1111/j.2517-6161.1995.tb02031.x
    rationale: Cite the multiple-testing method used in discovery and replication.
~~~

~~~powershell
java -jar $jar study references references.yaml --project . --out references-001
Get-FileHash references-001/metadata/manifest.yaml -Algorithm SHA256
~~~

This retrieves exact public DOI/PMID records from Europe PMC and, when a DOI has
no matching Europe PMC record, publisher-deposited Crossref metadata. The source
project's resolved transmission/download policy and `--offline` apply. Only
public identifiers are transmitted; manuscript text, local rationales, study
files and patient rows are not sent. Every request, reason, response, timestamp
and checksum is retained, including blocked or unresolved requests. Retrieval
is not evidence that the article supports a claim. Import the resulting manifest
and its exact checksum in the manuscript request:

~~~yaml
reference_bundles:
  - manifest: references-001/metadata/manifest.yaml
    sha256: REPLACE_WITH_THE_RECORDED_SHA256
~~~

Reference bundles are verified and copied into the new immutable revision.
The manuscript command remains offline; it neither fetches silently nor changes
the source study. Include the saved JAR and acquisition request when archiving
the full research bundle.

Report all effective parameters and their operators explicitly—for example,
`BMI > 30 kg/m2`, not an ambiguous “obesity threshold.” Customary values such as
`FDR < 0.05` need no special justification. List uncommon or deviating choices in
`uncommon_parameters` and supply their recorded reasons in
`parameter_justifications`; missing reasons remain review needs. These fields do
not change the analysis. Dataset tissues are preserved in the cohort inventory:
PBMC and whole blood are different sample compartments. Such a contrast may
contribute to heterogeneity but cannot be isolated from other cohort differences
without an appropriate sensitivity analysis.

## 1. Build and copy the example

From the repository root, build the executable JAR:

~~~powershell
.\gradlew.bat executableJar
$jar = (Resolve-Path "build/cli/jlinalg-0.3.7.jar").Path
~~~

Copy the example to a disposable working directory so the checked-in files stay
clean:

~~~powershell
$project = Join-Path $env:TEMP "jlinalg-study-tutorial"
Copy-Item "examples/study-pipeline" $project -Recurse
Set-Location $project
~~~

If that destination already exists, choose another empty directory. JLinAlg
protects approved contracts and completed artifacts against silent overwrite.

The full lifecycle can be run in one command:

~~~powershell
java -jar $jar study auto study.yaml --project . --actor-id scientist-1
~~~

That command validates input files, resolves defaults, plans, records the
command-line approval, executes or resumes every eligible branch, verifies the
audit chain, and rebuilds the indexes. The remaining sections unpack the same
lifecycle so each boundary is visible.

The input contract is `study.yaml`; its two tabular inputs are
`data/discovery.tsv` and `data/replication.tsv`. The contract explicitly declares
that the cohorts are independent, so this example has no pending relationship
assumption.

## 2. Confirm beginning state and validate inputs

A missing `state/project-status.yaml` means that the project is at its beginning,
not that an analysis completed without a status file:

~~~powershell
java -jar $jar study status --project .
java -jar $jar study validate study.yaml --check-files
~~~

`validate` checks the versioned structure, IDs, cross-references, analysis goals,
and—because `--check-files` is present—the declared paths. It reads but does not
resolve or execute the project.

Before continuing, inspect the input tables. Each row has a feature ID, effect,
standard error, and p-value. A result-first meta-analysis requires compatible
effect definitions and units; syntactically compatible columns are not evidence
that two scientific estimands are interchangeable.

If one physical table contains several phenotypes, declare one logical dataset
per phenotype and reuse the file path with different column mappings. The
[result-first contract vignette](auditable-omics-pipeline.md#multi-phenotype-result-files-and-strict-feature-universes)
shows how to handle R row names, invalid-row exclusion, a strict cross-cohort
feature intersection, discovery-subset FDR, and direction-independent validation.

## 3. Resolve defaults and inspect the contract

Resolve the human contract into an immutable, fully explicit contract:

~~~powershell
java -jar $jar study resolve study.yaml --actor-id scientist-1
~~~

This creates:

- `resolved-study.yaml`, including expanded defaults and assumptions;
- `ledger/events.jsonl`, the append-only machine-readable event history;
- `SESSION_HISTORY.md`, the readable research notebook;
- `state/project-status.yaml`, a materialized progress view; and
- `AGENTS.md`, a derived working brief for a person or future agent.

Review all five. `study.yaml` remains the human-authored source, while
`resolved-study.yaml` is the exact contract from which the plan is compiled.
Directly editing the base study after resolution cannot replace the approved
contract; project changes use amendments.

## 4. Compile and approve the plan

Compile a deterministic operation graph and inspect it before approval:

~~~powershell
java -jar $jar study plan --project . --actor-id scientist-1
java -jar $jar study status --project .
Get-Content plans/*/analysis-plan.yaml
~~~

The plan identifies each operation, its datasets, dependencies, executor,
configuration, expected outputs, and registered diagnostics. Planning does not
run an analysis.

Approval is a separate human event:

~~~powershell
java -jar $jar study approve --project . --approved-by scientist-1
~~~

If another project contains default independence assumptions, the approval
command stops until they are reviewed. `--accept-assumptions` is an explicit
acknowledgment; it should not be added merely to make a command pass.

## 5. Execute the approved operations

Run the plan:

~~~powershell
java -jar $jar study run --project . --actor-id scientist-1
~~~

The example completes five operations. Under
`runs/run-<plan-id>/operations/`, inspect:

- standardized association tables and their source hashes;
- `replication.tsv`, including adjusted discovery and replication p-values,
  sign concordance, and the final `replicated` indicator;
- `meta-analysis.tsv`, its log, and captured console output;
- `diagnostics.yaml`, including cross-cohort correlation, OLS scale/slope,
  sign concordance, discovery-selected and replicated counts, meta-analysis
  FDR/heterogeneity counts, the unique count replicated in any validation
  cohort, and the current unusual-observation list; and
- `report.md`, whose result IDs link the report to operation manifests.

Every operation directory also has a `manifest.yaml` with its configuration,
inputs, output hashes, timestamps, executor, and state. The run-level
`run-manifest.yaml` summarizes the operation states. The exact directory names
come from the plan and should be discovered from the manifest rather than
hard-coded in a downstream script.

For `mode: auto`, functional and genetic follow-up branches are independent.
JLinAlg runs local GO and Reactome enrichment when feature mapping is available;
`annotation_release: GENCODE_v48`, for example, permits an audited public
GENCODE transcript-ranking and HGNC-symbol mapping download. Public references
are fetched by the HTTP client embedded in the executable JAR; curl, Python,
and a separate downloader are not required. If eligible variant-level work requires LD, the
default is the checksum-verified `1000g-phase3` package with five ancestry
panels. It is downloaded only when that branch is eligible, and
`resources.ld.population: auto` waits for an ancestry choice rather than
silently assuming EUR. A missing regional molecular-QTL or outcome-GWAS table
becomes a precise `request.yaml`; it does not block enrichment or reporting.

## 6. Verify and safely resume

Verify the evidence chain:

~~~powershell
java -jar $jar study verify --project .
java -jar $jar study run --project . --resume --actor-id scientist-1
java -jar $jar study verify --project .
~~~

Verification recalculates hashes for the base and resolved contracts, plans,
amendments, observations, derived notebook files, manifests, and operation
outputs. Resume reuses only verified complete artifacts. A changed output is an
error, not a cache hit.

`--resume` is now optional. Both `study run` and `study auto` resume by default.
Every invocation retains a separate immutable attempt, reusing only completed
operations whose input hashes and implementation identity match. Waiting and
failed operations are retried; changed upstream outputs invalidate dependants.
The index shows the latest attempt by default and offers a historical-attempt
toggle; historical blockers do not count as current blockers.

### Resume preparation while downstream inputs arrive

Native `validate_harmonize`, `participant_qc` and `association` record the
`native_cohort_preparation_v1` input scope. They retain the current hypothesis,
its referenced dataset metadata, model/covariate/QC settings, scientific root
settings, input content hashes, operation/dependency identities and executable
identity. Distinct downstream genetic source file roles and downstream analysis
settings do not enter these preparation keys. Roles shared with preparation,
such as `results` or `annotation`, remain conservatively bound. A reporting-policy amendment or a separate
hypothesis can therefore reuse an unchanged completed fit. Changed phenotype,
matrix, model, covariate, relevant QC or dependency content invalidates it.

The scope remains deliberately conservative: operation IDs and dependencies
must match, and changes to root resources, cohort relationships or assumptions
can invalidate preparation. Other stages, supplied-result imports, configured
commands and third-party executors retain full-study context. A new executable
also invalidates reuse. This does not promise minimal recomputation for every
possible amendment or arbitrary external command.

`study auto` accepts unavailable declared dataset files as validation warnings.
An absent declared preparation input makes its native stage wait; downstream
source files belong to their consuming stages. Independent available work can
continue. Malformed contracts, integrity failures and missing data dictionaries
needed to resolve the contract still block execution. Use the explicit strict
check when every declared file should already be present:

~~~powershell
java -jar $jar study validate study.yaml --check-files
~~~

Each run retains `input-availability.yaml` and its hash in `run-manifest.yaml`.
This receipt records presence and observed size, not consumption or scientific
validity. The operation's `input_sha256` records its declared content inputs;
intake's `inputs.tsv` covers preparation inputs. Reused manifests retain
`execution_resolved_study_hash` from the original execution and separately
record `current_resolved_study_hash`, `context_hash`, `input_scope` and
`reused_from`. Prior attempt bytes remain unchanged. Availability observations
cannot replace typed source contracts, complete tested families or scientific
review.

The offline `examples/study-pipeline/stage-resume-workflow.py` source-checkout example
exercises native association followed by missing/completed MR, reporting and
hypothesis amendments, input revision and forced rerun. Synthetic declarations
and source-review flags establish software behavior only.

Use `study auto study.yaml --rerun` for a complete recomputation, bypassing result
reuse without deleting earlier evidence. `--overwrite` is deliberately not used.
`--offline` (or `resources.network: offline`) prevents built-in network requests;
available local work continues. Missing resources and blocked providers are
shown in the index. Repeating without `--offline` retries unfinished work.
An HTTP 403 is recorded as an endpoint/authorization denial, not asserted to be
a firewall diagnosis. A timeout or failed search never becomes “no association”
or “no literature.” No concurrent execution is allowed in the same project;
do not amend it while a run is active.

The rebuilt index has four complementary entry points:

- `index/index.html` for interactive search;
- `index/index.json` for artifact-level retrieval;
- `index/evidence-summary.json` for a portable machine handoff; and
- `index/LLM_CONTEXT.md` for paper drafting or drug-prioritization retrieval.

The LLM context embeds the compact deterministic diagnostics and the leading
GO, Reactome, or candidate-prioritization rows, while retaining the result ID,
artifact path, and SHA-256 needed to verify each value. It deliberately does not
embed entire result tables. A drafter should retrieve additional rows from the
indexed artifact rather than treating the context file as a new source.

The last file is an index, not a source of truth. Any LLM-generated claim must
follow its result ID to the hashed result table and operation manifest.

The command exit statuses are:

| Status | Meaning |
| --- | --- |
| `0` | Requested work completed or verified. |
| `2` | Validation, integrity, or execution failed. |
| `3` | The plan is waiting for an explicitly configured operation or resource. |

`study run --allow-partial` converts the last case to process success for batch
schedulers, but the project remains visibly `waiting_for_input`.
`study auto` instead treats documented scientific prerequisite gaps as a
successful completion of all currently available work and returns nonzero only
for validation, execution, or integrity failures.

## 7. Make a study change through an amendment

Copy the current `resolved_study_hash` from `study status` into an amendment.
An amendment contains only the change and its rationale:

~~~yaml
schema_version: 1
amendment:
  id: sensitivity-2026-10-01
  title: Add a prespecified heterogeneity sensitivity analysis
  base_study_hash: "<resolved_study_hash from study status>"
  proposed_at: 2026-10-01T16:30:00Z
  proposed_by:
    type: human
    id: scientist-1
  source:
    type: meeting
    id: methods-meeting-2026-10-01
  rationale: Test whether the pooled finding is sensitive to cohort heterogeneity.
changes:
  add:
    analysis_requests:
      - id: SA1
        type: sensitivity_analysis
        for_hypothesis: H1
        description: Compare fixed and random-effects pooling.
        reason: Cohort effects may not share one common effect.
~~~

Preview before approval:

~~~powershell
java -jar $jar study amend amendment-sensitivity-2026-10-01.yaml --project . --check
java -jar $jar study amend amendment-sensitivity-2026-10-01.yaml --project . --approved-by scientist-1
~~~

Applying an amendment supersedes the active plan. Compile, review, and approve a
new plan before more execution. Prior contracts, plans, runs, and rejected or
retired directions remain in the evidence history.

## 8. Record an unusual result

Suppose a generated effect-comparison plot contains an off-diagonal streak.
Keep the plot inside the project and record the observation without deciding in
advance whether it is biology or artifact:

~~~powershell
java -jar $jar study observe --project . `
  --id off-diagonal-1 --type off_diagonal_streak `
  --artifact plots/effects.png --hypothesis H1 `
  --description "Off-diagonal effect streak" `
  --explanation "processing batch" `
  --explanation "biological subgroup" `
  --proposed-direction sensitivity_analysis `
  --amendment-id check-batch `
  --actor-type human --actor-id scientist-1
~~~

The observation stores the plot hash, competing explanations, proposed
direction, hypothesis, and actor. If a local or remote LLM drafted the review,
use `--actor-type llm` and identify the provider/model or local model hash with
`--model`. That is provenance only: the proposed direction still needs a human-
approved amendment.

## Documented scientist questions

The source build now records deterministic questions in `QUESTIONS.md` and
`SESSION_HISTORY.md`, with their exact wording, reason, response options,
hypothesis/operation scope, input hashes, UTC timestamp, rule, and actor.
`state/questions.yaml` is the machine-readable view; `index/index.html` makes
questions and answers searchable alongside the analysis evidence. Documented
means available for review, not proof that someone read the question.

The small result-first example may need no questions. A matrix study without a
phenotype file/outcome, a shared-parent cohort comparison, or a genetic follow-up
without feature annotation or GWAS/QTL inputs will exercise the first rules.
Missing core required study fields still cause validation errors; general
incomplete-contract intake is not implemented. Unsupported older blockers remain
visible in operation requests even if they have no question rule yet.

In the project directory, inspect questions and export a blank answer batch:

~~~powershell
java -jar $jar study questions --project .
java -jar $jar study questions --project . --out answers-meeting-01.yaml
~~~

No provider call or analysis runs, and an existing export is never overwritten.
Open `QUESTIONS.md` beside the answer file. Retain the exported `project_id`,
question ID, revision, and hash. Choose your own batch `id`. Fill only responses
you are ready to record; remove the other response entries from this batch.
For example, a factual phenotype-file response has this shape (the IDs/hash
below are placeholders and must come from your export):

~~~yaml
schema_version: 1
id: meeting-01-answers
project_id: my-project
amendment_id: phenotype-metadata-01
meeting_id: lab-meeting-01
responses:
  - question_id: Q-EXPORTED-ID
    question_revision: 1
    question_hash: EXPORTED-HASH
    disposition: answer
    value: metadata/phenotypes.tsv
    rationale: The data manager confirmed this is the released phenotype file.
    evidence:
      - file: metadata/README.txt
        selector: Release and phenotype definitions
        note: Source description reviewed by the scientist.
~~~

The phenotype/annotation resource must exist inside the project. Its hash is
recorded without copying patient-level contents into the answer notebook.
Evidence `file` entries, in contrast, are copied into immutable local snapshots:
use short UTF-8 metadata documents, not patient records (maximum 1 MiB each).
A `citation` or explicit source `note` can be used without a file; citations
are recorded as scientist-supplied, not automatically verified.

Every response needs a rationale. `disposition` is `answer`, `unknown`, `defer`,
or `decline`; `value` must be absent/null for the last three. Mandatory
prerequisites cannot be declined. Unknown/deferred questions remain unresolved
and do not unblock an analysis. Declining optional review records work not
pursued, not a negative finding. Recommendations are never selected by default.

Check the complete batch, then record it explicitly:

~~~powershell
java -jar $jar study answer answers-meeting-01.yaml --project . --actor-id scientist-1 --check
java -jar $jar study answer answers-meeting-01.yaml --project . --actor-id scientist-1 --record
~~~

`--check` writes nothing. `--record` validates the entire batch before recording
it, preserves the submitted file, and appends a ledger event. Both require an
explicit local human actor. A local name is attribution, not authenticated
identity. Neither command runs an analysis or contacts an LLM.

Supported factual changes create a proposed amendment, not an immediate change
to `resolved-study.yaml`. Include a user-chosen `amendment_id` for those batches;
omit it when recording only unknown/defer/decline or narrative review responses.
The CLI receipt names the proposal, when one exists:

~~~powershell
java -jar $jar study amend amendments/proposed/amendment-phenotype-metadata-01.yaml --project . --check
java -jar $jar study amend amendments/proposed/amendment-phenotype-metadata-01.yaml --project . --approved-by scientist-1
java -jar $jar study auto study.yaml --project . --actor-id scientist-1 --offline
~~~

Inspect the amendment impact before approval. A narrative response about GWAS/QTL
source selection or diagnostic follow-up is documented but does not generate an
arbitrary analytical plan: prepare a supported amendment and any required local
inputs separately. Related/overlapping cohorts still require an appropriate
analysis; acknowledging dependence or passing `--accept-assumptions` does not
make independent-cohort meta-analysis valid.

To correct an unresolved answer, export with `--include-answered`, use a new
batch ID, and retain its generated `supersedes` reference to the previous answer.
Old answers remain auditable. Repeating an identical recorded batch is safe;
reusing its ID for changed content is rejected. Changed study/input hashes or
question revisions reject stale answers rather than silently applying them.
After an amendment the existing cache conservatively re-evaluates all operations
under the changed study hash.

Questions, answers, and small evidence snapshots are checked by `study verify`.
If only a derived notebook/status file was lost, this can rebuild the views:

~~~powershell
java -jar $jar study verify --project . --repair-views
~~~

Repair refuses missing or modified authoritative records. It does not alter the
ledger or recover corrupt evidence. The local HTML/JSON index contains answer
text and must be treated as sensitive. Raw answers and evidence snapshots are
omitted from `LLM_CONTEXT.md` and existing assistance previews; no file becomes
approved for upload simply because it is called an index.

See the [implemented scope and remaining design](study-questions-and-decisions.md#implemented-first-tranche).
LLM question generation, interactive prompting, authenticated remote approval,
and generated-code execution remain future work.

## Patient-level data and LLM non-egress

### What can be claimed today

LLM calls are opt-in. The adapter never uploads an input file or gives the model
filesystem, shell, sample-matrix or phenotype-row access. It constructs a small
allowlisted result preview; raw feature identifiers, project descriptions,
dictionary prose and local paths are omitted. Gene symbols, numeric aggregate
results, phenotype labels and retrieved reference metadata can be transmitted.
These are still sensitive unpublished findings. The approved input metadata
must correctly classify results; this is not general-purpose PHI detection.
`deny_all` blocks every remote advisory request, including topic-only requests,
and `--offline` also blocks built-in reference downloads.

JLinAlg cannot truthfully prove that unrelated software, a separately launched
agent, a shell command, or a user copied no data elsewhere. A defensible project
claim therefore needs both application evidence and enforcement outside the
application.

### A controlled aggregate-results disclosure design

A future LLM integration should never give a model process unrestricted access
to the study directory. Instead, JLinAlg should implement a release boundary:

~~~text
restricted project data
        |
        v
artifact classification + identifier/DLP checks + small-cell rules
        |
        v
separate disclosure bundle (aggregate, redacted artifacts only)
        |
        v
human approval + immutable disclosure manifest
        |
        v
sandboxed connector with read-only bundle and allowlisted network destination
        |
        v
LLM response, response hash, and provider/model attribution
~~~

The implemented adapter uses `aggregate_results` as the default eligibility
policy when explicitly enabled: bounded result previews may be disclosed, but
patient/sample-level data may not. This is permission for a controlled artifact
class, not blanket permission to upload every file in `results/` and not an
instruction to transmit results when no LLM operation needs them.

For this policy, an aggregate result is identified by its registered artifact
type, schema, content checks, and lineage—not by its filename. Eligible examples
include association and meta-analysis summary tables, enrichment and fine-mapping
results, summary diagnostics, and redacted figures. Participant matrices,
phenotype/covariate rows, sample identifiers, individual residuals or predictions,
and per-person scores remain prohibited even if a tool wrote them to a result
directory. Prompts and study metadata are also manifested because unpublished
hypotheses and findings can be sensitive despite containing no patient rows.

A project may set `deny_all` so no project content is transmitted to a remote
LLM. This is appropriate for embargoed, commercially sensitive, regulated, or
otherwise restricted unpublished work. The additional controls below are a
future high-assurance contract, not all implemented today:

~~~yaml
project:
  data_policy:
    classification: restricted_patient_level
    llm:
      remote_transmission: aggregate_results  # default; or deny_all
      remote_patient_level_data: deny
      eligible_remote_artifacts:
        - association_results
        - meta_analysis_results
        - enrichment_results
        - fine_mapping_results
        - summary_diagnostics
        - redacted_figures
      require_human_approval: true
      minimum_cell_count: 10
~~~

`remote_transmission` denial and patient-level exclusion are implemented for the
bounded adapter. Per-request human approval, artifact-specific disclosure lists,
small-cell checks, and OS isolation remain future work. If
`require_human_approval: true`, `eligible_remote_artifacts`, or
`minimum_cell_count` is present, the current adapter refuses to transmit and
records `additional_disclosure_review_required`; it does not silently ignore
these stricter policies. Do not remove a required control merely to get a call
to run. Application logs are not an independent non-egress guarantee.

For `aggregate_results`, the system should create an immutable
`llm-disclosure-manifest.yaml` before each request. It should record:

- hashes and media types of the exact bytes released;
- the source result IDs and transformations used for redaction or aggregation;
- direct-identifier, quasi-identifier, free-text, and minimum-cell checks;
- destination, provider, reported model, purpose, timestamp, and approver;
- the policy version and pass/fail decision; and
- request and response hashes, without duplicating restricted content into an
  unrestricted log.

Calling a table “aggregate” or “de-identified” is not by itself proof of
negligible re-identification risk. For regulated health data, the project should
tie release criteria to the applicable legal and institutional process—for
example, HIPAA Safe Harbor or Expert Determination in the United States—and
retain the determination as evidence. Small cells, rare combinations, genomic
variants, dates, geography, and free text require particular care.

### Independent enforcement and verification

For high-assurance studies, run the connector in a separate OS account,
container, or sandbox that can read only the approved disclosure directory.
Use default-deny outbound networking with an allowlist for the selected provider,
and retain proxy, DNS, firewall, or equivalent egress logs. Here, default-deny
describes connector filesystem and network capabilities; it does not change the
project-policy default that verified aggregate results are eligible. A local
model should run with networking disabled and record hashes for its weights,
runtime, prompt, and supplied artifacts.

Assurance should be tested, not inferred from configuration alone:

1. Review the connector and its transitive dependencies; publish an SBOM and
   signed/reproducible release artifacts where practical.
2. Run integration tests with canary identifiers in forbidden files and verify
   that requests, logs, crash reports, and telemetry never contain them.
3. Verify that the connector cannot read outside the disclosure directory and
   cannot reach an unapproved endpoint.
4. Reconcile disclosure manifests with network logs and the research ledger.
5. Periodically obtain an independent security/privacy review for regulated or
   high-consequence deployments.

Provider agreements, retention controls, training opt-outs, and a BAA or DPA may
be necessary, but they complement rather than replace technical data
minimization and egress enforcement. If an external tool cannot supply adequate
evidence, the project should record a provenance and privacy-assurance gap rather
than claim end-to-end non-egress.

## Current boundaries

- Direct deterministic adapters currently cover result standardization,
  bulk-matrix association, discovery/replication comparison, configurable
  fixed/random meta-analysis, GENCODE feature mapping, local GO/Reactome
  enrichment, native variant consequences/contextual scores, diagnostics, transparent candidate evidence tables, and
  traceable reporting.
- Configured downstream operations can call declared tools; absent tools or
  inputs produce a hashed request instead of an invented result. Eligible
  LD-dependent work automatically resolves the default 1000 Genomes reference.
- Optional AI provider adapters support bounded advice, retrieval-cited literature
  review and explicitly permitted unreviewed HTML drafts. AI protocol fixtures
  and separately recorded public-source retrieval checks do not establish actual
  scientific AI appraisal.
- Raw single-cell, spatial, microbiome, and multimodal data wait for a registered
  modality-specific operation rather than being analyzed as a generic bulk
  matrix.
- Full disclosure-bundle isolation, DLP/small-cell certification, general pipeline
  local models, MCP and provider-specific OAuth login/refresh remain unimplemented.
  Browser handoff is the default transport when assistance is enabled; optional
  API adapters accept API keys or provider-issued OAuth access tokens. The separate
  manuscript editorial command supports exact-payload approval and explicit loopback models;
  see the [bioinformatics vignette](bioinformatics-evidence.md). No regulatory
  compliance or publication-readiness certification is claimed.

For the full contract, amendment, provenance, and operation-registry design, see
the [auditable omics research pipeline vignette](auditable-omics-pipeline.md).
The runnable files are in the
[study-pipeline example](https://github.com/robbyjo/JLinAlg/tree/main/examples/study-pipeline).

## Compatible effects and covariance-aware pooling

Each hypothesis defines a proposed compatible analysis block. Pooling now audits
declared estimands, coefficient orientation, effect scale/units, phenotype and
feature transformations, modality, species, assay, tissue and genome build.
Known conflicts stop pooling or validation and remain in `pooling-audit.yaml`
and `pooling-compatibility.tsv`. For GWAS, known variant-coordinate or effect-allele
conflicts are retained in `pooling-variant-compatibility.tsv`; no automatic allele
flip or unit conversion is inferred. Separate incompatible datasets into suitable
hypotheses or supply scientifically harmonized inputs before pooling.

```yaml
analysis:
  pooling:
    require_complete_contract: true
    effect_contract:
      estimand: adjusted feature association with the declared phenotype
      orientation: feature_to_phenotype
      effect_scale: linear
      effect_units: phenotype units per common feature unit
      phenotype_transformation: none
      feature_transformation: none
    dependence:
      method: sampling_correlation
      matrix: inputs/sampling-correlations.tsv
      rationale: Explain how these sampling correlations were obtained.
      citation: Cite the covariance derivation and source.
  operations:
    meta_analysis:
      model: random
      tau_squared_estimator: reml
      inference: normal
      confidence_level: 0.95
      min_cohorts: 2
```

Datasets may declare their own `effect_contract`; it must agree with the common
contract. Assay units do not supply coefficient units. Optional
`pooling.context_exceptions` maps `assay`, `tissue` or `modality` to an explicit
scientific rationale. It cannot waive conflicting effect units, estimands,
orientation or transformations. Completeness remains an explicit choice:
`require_complete_contract` currently defaults to false for legacy contracts;
missing information is audited as `partially_declared_not_certified`, with a
mandatory dossier caveat. Strict mode requires the six effect-contract fields and
GWAS effect-allele coding. Automatic partitioning and complete model/source
appraisal remain unfinished.

Dependence defaults to `independent`. Reused cohort/source identities and declared
dependence cannot silently become independent estimates. The native
`sampling_correlation` and `sampling_covariance` routes use supplied matrices in
generalized least-squares pooling. The long TSV format uses dataset IDs:

```text
feature_id  dataset_a  dataset_b  value
*           a          a          1
*           a          b          0.4
*           b          b          1
```

The displayed columns are tab-separated in the input file. Supply every required
pair, including diagonals and explicit zero cross-source entries. One triangular
half is sufficient; duplicate directions must agree. `*` declares a shared
matrix, and feature-specific pairs override it. A correlation matrix is scaled
by each feature's source SEs; a covariance diagonal must agree with SE squared
within relative tolerance `1e-6`. Missing pairs remain unresolved. Singular
matrices or scaled Cholesky pivots at or below `1e-12` require review; no jitter,
source deletion or independent-model fallback is applied. Actual source subsets
are used when a cohort has no estimate for a feature.

Sampling dependence and between-source heterogeneity are separate: random-effects
GLS adds `tau_squared * I` to the supplied sampling covariance. Both independent
and GLS adapters honor `reml|dl|pm`, `normal|t|hk|modified-hk`, confidence level,
maximum iterations and tolerance. Their effective settings are recorded. For
GLS, generalized Q and its degrees of freedom are retained; conventional
independent-study I-squared and prediction intervals remain unavailable.
`pooling-feature-audit.tsv`, `pooling-weights.tsv`, `sampling-matrix-input.tsv`
and `correlated-pooling.yaml` retain coverage, weights, source hashes and caveats.
`n_sources` makes source counts explicit; legacy `n_cohorts` has the same source
count, while `n_unique_declared_cohorts` describes known cohort labels. Neither
participant independence nor a deduplicated/effective N is inferred.

Discovery pooling uses discovery sources alone. It may account for dependence
among those sources while retaining an independent validation cohort. A nonzero
discovery/validation sampling covariance, reused cohort identity or declared
cross-role dependence blocks an independent replication claim. Covariance files
participate in operation cache hashes. Cohort omissions and unfiltered-effect
sensitivities retain the same sampling model on their actual source subsets;
unresolved child fits propagate a waiting state and preserve the primary.
Scalar-effect covariance does not implement dependent rare-set score pooling.

For the method and matrix assumptions, see the [official metafor GLS documentation](https://wviechtb.github.io/metafor/reference/rma.mv.html)
and [Lin and Sullivan's overlapping-subject GWAS analysis](https://pmc.ncbi.nlm.nih.gov/articles/PMC2790578/).
The packaged `pooling-workflow.py` example independently checks scalar GLS,
Paule–Mandel heterogeneity, conditional intervals/tests and frozen validation.

## Native GWAS consequences and AlphaGenome context

`variant_scoring` now runs native consequence and AlphaGenome routes unless an
explicit configured command overrides the operation. Selection covers every
available primary variant, including nonsignificant variants, plus native
rare-set QC candidates. Set candidates retain that distinct disposition;
frequency masks still determine their eligibility in each set-test scope.
Excluded raw results remain in `variant-inventory.tsv` without being queried.

```yaml
analysis:
  mode: manual
  goals: [variant_scoring]
  variant_followup:
    reference_fasta: references/GRCh38.fa
    reference_normalized: true
    maximum_variants_per_batch: 100
    consequences:
      engine: import
      annotations: annotations/consequences.tsv
      format: canonical
      release: declared-annotation-release
    alphagenome:
      engine: atlas_then_predict
      python: environments/alphagenome/Scripts/python.exe
      api_key_env: ALPHAGENOME_API_KEY
      scorers: [AVI_SCORE]
      prediction_scorers: [RNA_SEQ, SPLICE_SITES, SPLICE_SITE_USAGE, SPLICE_JUNCTIONS]
      ontology_terms: [UBERON:0002107]
      model_version: ALL_FOLDS
      sequence_length: 1048576
      maximum_queries: 1000000
      maximum_predictions: 1000
      maximum_rows: 1000000
      timeout_seconds: 120
```

The supplied FASTA must agree with REF. Large FASTA files require an existing
`.fai`; reference files are never indexed or repaired in place. An indel also
requires the explicit normalization declaration. No liftover is inferred.
Canonical IDs are `BUILD:CHROM:POS:REF:ALT`; `files.variants` can resolve custom
feature IDs using `feature_id, genome_build, chrom, pos, ref, alt`. Conflicting
IDs, alleles or builds require review.

Consequences default to `vep-rest`, using the official build-specific Ensembl
host and retaining release metadata and response files. Other engines are
`annovar`, `vep` and `import`; imports support `canonical`, `vep` and
`annovar-vcf`. Installed ANNOVAR requires `executable`, `perl`, `database`,
`protocol` and `operation`; installed VEP requires `executable`, `cache`,
`cache_version` and `fasta`. Import/local engines require an explicit `release`.
Canonical import columns are `genome_build, chrom, pos, ref, alt, gene,
transcript, consequence`; VEP/ANNOVAR imports must preserve canonical IDs.
An import may contain other variants or builds: exact allele matching selects
each batch and retains the source hash and selection counts.

If rare-set masks need annotations and `files.variant_annotation` is absent,
the planner adds `variant_annotation` before `rare_variant_masks`. Its verified
gene/consequence rows feed masks without applying the single-marker MAC 20
filter. Aggregated/ambiguous genes and missing consequences receive explicit
coverage dispositions; high-confidence loss-of-function is never inferred from
a consequence name alone. Supplied mask annotations remain supported.

AlphaGenome defaults to Atlas lookup. `predict` uses the separately installed
[official Python SDK](https://www.alphagenomedocs.com/), while
`atlas_then_predict` tries supported Atlas misses and indels within the separate
prediction budget. `AVI_SCORE` is an Atlas scorer; live prediction uses the
separate `prediction_scorers` list. Native remote scoring accepts human GRCh38
autosomes/X/Y and performs no implicit assembly conversion. A missing SDK,
credential, budget, reference or usable response leaves an explicit disposition.
Offline or denied-transmission policy is checked before starting a remote
provider or reading its credential. Requests contain canonical variant/scorer
parameters, without phenotype, association statistics or participant inputs.

For existing scores, use `alphagenome: {engine: import, scores: scores.tsv}`.
The TSV columns are `variant_id, mode, resource_version, model_version, scorer,
gene_id, transcript_id, ontology_curie, track_id, raw_score, quantile_score,
interval, source_record`. Source/model versions are required. Preserve raw scores
and signed quantiles: signed scorers can have quantiles in `[-1,1]`, while
unsigned scorers use `[0,1]`. A quantile is contextual rank information, not a
causal probability. See the [official score explanation](https://www.alphagenomedocs.com/faqs.html).

Outputs include `variant-consequences.tsv`, `alphagenome-scores.tsv`,
`variant-inventory.tsv`, `variant-evidence.tsv` and `variant-followup.yaml`.
The SDK bridge preserves full returned arrays/layers and axes; tissue filters
limit exported rows while retaining raw outputs. Partial scorer coverage and
output-budget exhaustion cannot become complete scores. Sealed completed
batches can be reused on resume; changed/tampered evidence is rejected. Local
annotation/reference/tool inputs participate in cache hashes. Finding dossiers
retain transcript, tissue, scorer, version and a mandatory prediction caveat.
Installed-tool and actual prediction-service acceptance remain separate from
offline imports and SDK-format serialization tests.

## Exact protein sequences, structures and confidence

`protein_structure` now has a native branch. Register a target manifest and FASTA
to assess supplied PDB/mmCIF models without refitting associations or meta-analysis.
`protein_structure_assessment` limits this work to supplied coordinate models;
it never starts a new prediction or searches for a replacement structure. A
complete typed supplied `protein_structure` summary bypasses native assessment.
Use the manifest route when the missing work is sequence mapping, confidence,
PAE, peptide/variant context or the local structure view.

```yaml
analysis:
  mode: manual
  goals: [protein_structure]
  protein_structure:
    manifest: protein-targets.yaml
    sequences: exact-proteins.fasta
    retrieve: false
    require_exact_sequence: true
    require_plddt: true
    require_pae: true
```

A minimal `protein-targets.yaml` defines exact assay and model identities:

```yaml
schema_version: 1
targets:
  - feature_id: protein-assay-1
    protein_id: P04637
    sequence_id: exact-target-sequence
    taxon_id: 9606
    assay_mapping:
      status: accession_declared
      source: Reviewed assay annotation and release
      peptides: [EXACTPEPTIDE]
      limitations: Sequence identity alone does not establish assay specificity
    models:
      - id: source-model-v1
        file: models/exact-target.cif
        format: cif
        chain: A
        model_number: 1
        structure_kind: alphafold_prediction
        method: Source-declared AlphaFold release
        model_version: source-version
        confidence_source: b_factor_plddt
        confidence_provenance: Source documents pLDDT in the B-factor field
        pae: models/exact-target-pae.json
        pae_provenance: Matrix paired to this exact source model and version
    regions:
      - {id: region-1, start: 1, end: 50}
```

The peptide and region above are illustrative declarations, not assertions about
TP53; replace them with the actual measured peptides and justified regions.
FASTA identifiers are matched exactly, including pipe-delimited identifiers.
Paths in manifests resolve against the study project. All supplied inputs are
snapshotted and hashed; sequence hashes use uppercase amino-acid letters without
whitespace, without stripping stops or treating ambiguous letters as wildcards.
A known exact coordinate sequence can map despite different author numbering.
Missing coordinates require identity-checked polymer/sequence context or an
explicit `residue_map` TSV with `chain`, `residue_number`, optional
`insertion_code`, and `sequence_position` (1-based). Partial fragments retain
coverage and cannot silently establish full isoform identity. The default
`require_exact_sequence: true` requires complete exact sequence context; setting
it false permits explicitly limited fragment context. Assay/protein groups,
canonical versus alternative isoforms, precursor/mature processing and unknown
isoform specificity remain limitations even when a model matches its FASTA.

Set `retrieve: true` with permitted online automatic downloads to obtain an exact
public UniProt FASTA and compatible versioned AlphaFold DB models. Declare the
assay taxon explicitly or supply consistent dataset species. The native branch
checks accession/isoform, taxon and exact sequence; it inventories other models
rather than selecting the first hit or the highest confidence. Supplying models
prevents unnecessary replacement-model acquisition. Retrieved coordinate/PAE
URLs, versions, dates, raw responses, hashes and request rationales are retained.
For a retrieved AlphaFold DB model, `model_version` identifies the versioned
database artifact; it does not authenticate a local software or weight release.
Retrieving a prediction is distinct from executing AlphaFold inference. Public
requests transmit declared accession identifiers, never local sequence files.

New prediction requires an explicitly configured **argument vector**, software
and model versions, seeds, MSA/template provenance and a reviewed local program:

```yaml
protein_structure:
  manifest: protein-targets.yaml
  sequences: exact-proteins.fasta
  retrieve: false
  prediction:
    enabled: true
    command: [python, predictor-adapter.py, '{input_fasta}', '{request_json}', '{output_dir}']
    software: AlphaFold
    software_version: reviewed-local-version
    model_version: reviewed-weight-release
    seeds: [0, 1, 2]
    msa_provenance: Reviewed database releases and search settings
    template_provenance: Reviewed template cutoff and database release
    input_files: [predictor-adapter.py]
    network: offline
    output_manifest: prediction-outputs.yaml
```

This is a provider contract, not a bundled folding engine or an AlphaFold Server
client. The configured program receives immutable FASTA and JSON requests and
writes `schema_version: 1`, the exact `sequence_sha256`, and a `models` list using
the manifest model fields. Returned paths must stay inside its output directory.
Every declared seed must be represented; missing seeds, invalid outputs and
failed/timed-out processes remain prerequisites, with raw outputs retained.
Hash-verified, sequence-validated completed batches can be reused from an
otherwise incomplete prior operation. No favorable seed/model subset is
promoted. Declare predictor scripts and local model/configuration resources in
`input_files` so changes invalidate reuse. Process invocation alone does not
authenticate software/weights or enforce network/resource isolation. `network`
records the program's intended mode; online prediction also respects the study's
network/disclosure/download policy. Local administrators remain responsible for
the actual predictor's execution environment.

pLDDT is read only from an explicitly declared B-factor field or a paired
`confidence_file` TSV (`chain`, `residue_number`, optional `insertion_code`,
`plddt`). Experimental B-factors are not converted to confidence. Missing scores
remain unavailable; fractions use only scored observed residues. PAE JSON accepts
AlphaFold DB `predicted_aligned_error` or a `pae` matrix, in Angstroms. Matrices
must be finite, square and paired to the exact model. Rows are alignment frames;
columns are evaluated residues. Both directions remain separate. Multi-chain or
explicitly remapped residue contexts require a complete `pae_index` TSV
(`matrix_index`, `chain`, `residue_number`, optional `insertion_code`), with
1-based indices; token order is never guessed. Region summaries state mapping
coverage and mean/maximum directional PAE. Partners need exact FASTA IDs, chain
IDs and a complex justification; target/partner identity checks do not validate
an interface.

Residue substitutions may be registered as `variants` with `id`, `position`,
`ref`, `alt`. REF must match the exact target. A `genomic_variant` additionally
requires an exact matching target `transcript_id` and `mapping_provenance`;
JLinAlg does not guess a genome-to-protein translation. All exact peptide
occurrences are reported; one occurrence within a target does not establish
proteome-wide uniqueness. Neither peptide/variant context nor pLDDT supplies a
pathogenicity or causal-effect score.

Outputs include `protein-structures.tsv`, per-model source snapshots, residue,
peptide, variant and region tables, pLDDT/PAE PNG and PDF, and a self-contained
`structure-view.html` C-alpha trace with rotation, zoom and confidence colours.
PAE plots may average adjacent display bins; the full directional matrix remains
unchanged. These are local artifacts. Aggregate previews and finding dossiers
exclude raw sequences, peptide strings, coordinates and PAE arrays. Structure
context can support an existing statistical finding but cannot make it an
FDR-significant AI candidate. Method/manuscript records retain the source and
scientific limitations.

Without a manifest, `scope: primary_significant` selects strict source FDR < 0.05
from the unique completed primary meta-analysis, or scoped cohort results. Source
adjusted P values and tested-family declarations survive selection. A declared
UniProt namespace or reviewed feature annotation is needed; gene symbols and
arbitrary assay identifiers are not guessed as accessions. `all_primary` is an
explicit alternative. A manifest defines `scope: explicit`, with its selection
protocol retained in provenance. Default bounds are 100 targets, 20 models per
target, 500 public queries, 5,000 C-alpha residues, 32 MiB per input, four million
PAE cells and 10,000 region pairs. The complete region family also has a
50-million-cell work bound. Exceeding a bound leaves explicit prerequisites;
no confidence-ranked subset is silently selected.

Run the independent acceptance bundle with:

```powershell
python examples/study-pipeline/protein-workflow.py run build/my-protein-check --jar build/cli/jlinalg-0.3.7.jar
# Optional live public TP53 retrieval, distinct from synthetic configured prediction:
python examples/study-pipeline/protein-workflow.py run build/my-protein-live-check --jar build/cli/jlinalg-0.3.7.jar --live
```

The verifier pins the executable and its own source before execution, independently
reads PDB columns and directional PAE cells, checks supplied summaries and missing
work, and verifies native cache reuse and `study verify`. The optional live case
compares the retrieved mmCIF result with a separately downloaded, version-matched
public PDB. It does not execute or scientifically validate new AlphaFold inference.

## Multiway cohort overlap and registered heterogeneity

Source builds add native `cohort_overlap` and `heterogeneity_review` operations.
Multiple result sources receive exact membership tables and an UpSet plot; two
or three sets also receive area-neutral Venn plots. The primary meta-analysis
gets a registered feature/widespread heterogeneity review. Manual reuse of an
external meta-analysis does not force extra heterogeneity work unless that review
is requested. Completed supplied overlap/heterogeneity results bypass statistical
recalculation; the importer can create missing count and overlap plot views.

```yaml
analysis:
  mode: manual
  goals: [discovery_replication, meta_analysis]
  cohort_overlap:
    universe: intersection
    validation_family: analysis
    direction: report
    plot_patterns: 40
    zero_overlap_alert: true
  heterogeneity:
    metric: auto
    i_squared_threshold: 50
    q_alpha: 0.05
    q_adjustment: BH
    feature_scope: all_estimable
    minimum_sources: 2
    minimum_features: 10
    minimum_high_features: 1
    widespread_fraction: 0.10
    fraction_comparison: greater_than_or_equal
    minimum_retained_cohorts: 2
    minimum_common_features: 1
    comparison_universe: all_sensitivities
    leave_one_cohort_out: always
```

These are configurable diagnostic defaults, not universally validated biological
cutoffs. Their exact rules and boundaries are materialized in the resolved plan
and method records. Unknown or incompatible source information remains explicit.
Known feature-namespace, species or variant-build incompatibility blocks an
identifier-based overlap. Pooling and independent-validation audits also block
known feature-namespace incompatibility. `auto` denotes an unresolved namespace;
it is not a known conflicting namespace or proof of compatibility. Supply an
explicit harmonized universe or separate compatible analysis blocks rather than
matching unrelated identifiers or automatically dropping a cohort.

### Exact membership and frozen validation

`cohort-overlap-membership.tsv` records every observed feature/set combination,
including missing/unestimable features, overlap-universe inclusion, selection
family membership, P/adjusted P, beta, discovery candidacy, direction and selection.
`cohort-overlap-features.tsv` reports exact binary patterns, observed coverage,
selected dataset and distinct declared cohort counts, and direction context.
Repeated cohort IDs do not count as independent cohorts; labels alone do not
establish independence. Unknown directions are not reported as concordance.

`cohort-overlap-patterns.tsv` retains every exclusive pattern, including the
all-unselected pattern, and distinguishes full from partial tested coverage.
`cohort-overlap-degree.tsv` gives exact and at-least 0/1/2/3/.../all-set counts
with the analysis denominator. The default `intersection` universe contains
features estimable in every source. `union` retains partial coverage explicitly;
an unobserved cell is never converted to a non-significant result.

FDR is computed on each complete registered source family before a display-only
universe restriction. If `analysis.harmonization.common_universe: intersection`
registers a common testing family, that family is applied before FDR, matching
the other native cohort operations. Derived family inputs and original producer
hashes are retained. Source-provided full-family adjusted P values are preserved.

`validation_family: analysis` follows `replication.adjustment_scope`.
`all_results` uses the complete observed validation source family;
`discovery_subset` recomputes the declared adjustment only within candidates
frozen from discovery, inside the predefined common tested universe. Validation
P values cannot define that family. Multiple discovery sources require their
completed discovery-only pool. A selected-only discovery summary that omits
parts of the common universe cannot silently define a complete frozen family.
`direction: require_discovery` additionally requires nonzero concordant effects
for validation selection; `report` preserves significance overlap and reports
opposed or unavailable directions separately. These definitions remain distinct
from an independent replication claim.

Source/correction families, denominators and thresholds are recorded in
`cohort-overlap-families.tsv`. Strict `<` thresholds are the resolved default;
`less_than_or_equal` remains an explicit alternative. The default bounds are
64 sets, one million features and five million membership cells. Exceeding a
work bound leaves a prerequisite. `plot_patterns` limits only the displayed
largest nonempty patterns, with a stable binary-pattern tie break and a visible
caption; complete membership and patterns remain in the tables. Set-size bars
use the complete selected sets, including intersections omitted from the display.

Zero-overlap review flags compare each declared cohort with the others in their
observed shared tested universe. Default eligibility requires at least one own
selected feature and one selected feature elsewhere; an underpowered cohort with
no hits is not automatically classified as a failed cohort. Coverage, assay,
power and source definitions still require review. No alert automatically
excludes a cohort or changes the primary result.

### Registered heterogeneity and paired influence

`heterogeneity-features.tsv` retains all primary rows, eligibility and scope,
source counts, beta/SE/P/association-adjusted P, Q/df/P, I-squared, tau-squared and
the registered high-heterogeneity flag. `heterogeneity-review.yaml` identifies the
unique completed primary producer and source hash; discovery pools and sensitivity
fits cannot become that baseline by sharing a filename.

`metric: auto` uses conventional I-squared where available, and Q P values for
covariance-aware fits that do not define conventional independent-study I-squared.
The exact Q definition remains recorded. Q-based BH/BY/NONE adjustment uses its
own complete eligible feature family, before any `primary_significant` display
scope. Missing Q statistics, unresolved source counts, mixed covariance definitions
or a selected-only source with unavailable full coverage withhold a widespread
conclusion. Missing statistics are not interpreted as low heterogeneity. Too few
eligible features yield `not_evaluable_below_minimum_feature_count`.

A widespread flag requires the registered minimum sources/features/high-feature
count and fraction boundary. The default I-squared threshold is `>= 50`; default
Q-adjusted P is `< 0.05`. The default widespread rule is at least 10% of at least
10 scoped eligible features, with at least one high feature. These describe
recorded diagnostics; I-squared and tau-squared remain uncertain with few cohorts.
A larger Q P value after omission can reflect fewer degrees of freedom or lower
power and does not establish reduced biological heterogeneity.

With at least three declared cohorts, `leave_one_cohort_out: always` retains every
registered omission. `on_alert` runs after a completed positive heterogeneity or
zero-overlap review; an unavailable required review remains a prerequisite rather
than a negative alert. `disabled` suppresses automatic omissions. The minimum
retained cohort rule never relabels a one-source estimate as a meta-analysis.
The approved model's source-count requirement is also preserved.

`paired-meta-comparison.tsv`, `paired-influence/*.tsv` and `cohort-influence.yaml`
compare the primary and every completed sensitivity fit on the registered common
feature universe. The default is the intersection of eligible features across
all completed registered fits, with a recorded completeness flag for attempted
fits that failed or were inapplicable. `pairwise` is an explicit alternative with
a separate denominator for each fit. Under `primary_significant`, the primary
scope stays frozen even when a sensitivity loses significance. Each fit's
association/Q correction remains anchored to its original complete family;
common-feature comparison does not refit FDR on a favorable subset.

Per-feature records include effects, SEs, intervals, direction and significance
changes, source counts, Q degrees of freedom, I-squared/tau-squared, source hashes
and missing-coverage dispositions. There is no independent-SE test of differences
between overlapping fits. Reselected validation fractions remain descriptive and
cannot nominate a better cohort set. All attempts remain available. Review
candidates identify common-feature I-squared/count reductions or pooled direction
changes; none selects a replacement primary. Aggregate changes caused by losing
features remain separately labelled as full-family summaries. Context and caveats
flow into source-linked finding dossiers and manuscript methods.

### Supplied cohort diagnostics and packaged verification

Supply `kind: cohort_overlap` with a complete long table containing `feature_id`,
`set_id`, `in_universe`, `source_estimable` and `selected` (true/false). Each feature
needs an explicit row for every set, including unavailable cells. Imports preserve
source selection rather than recomputing P values or FDR, and generate missing
count/Venn/UpSet views. Missing cells leave the import waiting. A supplied selection
table alone does not establish the source's cohort/family alert rule.

Supply `kind: heterogeneity_review` with `feature_id`, `metric`, `metric_value`,
`in_alert_scope` and `high_heterogeneity`. Metric values describe source-assessed
I-squared or adjusted Q P values. The source's `method.parameters.alert_rule` can
record its exact metric/cutoff, minimum sources, feature scope, minimum features,
minimum high features, widespread fraction and boundary. Declare complete source
family coverage and denominator when establishing a widespread alert. The importer
checks flags against that source rule and preserves its cutoff rather than applying
a different current default. An undeclared source rule remains unavailable while
the completed source table is retained; it cannot supply a negative alert for
conditional omission. Original statistical calculations are not rerun by import.

```powershell
python examples/study-pipeline/cohort-diagnostics-workflow.py run build/my-cohort-check --jar build/cli/jlinalg-0.3.7.jar
```

The independent offline verifier pins the executable and its own source before
execution. It reconstructs exact memberships, frozen validation BH, inverse-variance
and known-covariance GLS estimates, Q/I-squared and paired common-universe summaries
from synthetic raw tables using NumPy/SciPy. Supplied diagnostics, missing coverage,
blocked incompatible namespaces, source immutability, native cache reuse and
`study verify` are included. Arithmetic parity does not establish a biological
cohort exclusion, real-model AI appraisal or a revised primary analysis.

## Complete colocalization records and regional coverage

Native `colocalization` now retains every computed signal pair, its registered
prior sensitivity and regional QC in canonical tables. Source discovery remains
configured through `analysis.bioinformatics.colocalization`; reviewed file/URL
pairs remain in `analysis.bioinformatics.genetic_pairs`. Statistical controls
are separate, under `analysis.colocalization`:

```yaml
analysis:
  mode: manual
  goals: [colocalization]
  colocalization:
    trait1_prior: 0.0001
    trait2_prior: 0.0001
    shared_prior: 0.000005
    shared_prior_sensitivity: [0.000001]
    minimum_component_overlap: 0.5
    maximum_effects: 10
    maximum_iterations: 2000
    tolerance: 0.000001
    prior_variance: 0.2
    credible_set_coverage: 0.95
    minimum_purity: 0.5
    maximum_regional_variants: 1000000
    coverage_bins: 50
```

These are captured software defaults, not proof that a prior, effect count or
regional definition suits a particular study. Shared priors must be positive,
no greater than either trait prior and distinct across the primary and 1–20
sensitivity settings. The approved primary prior stays primary; the runner does
not choose the setting giving the largest H4. No posterior decision threshold
is imposed by default. An optional `posterior_threshold: 0.8`, for example,
registers a `>=` H4 rule and reports whether each signal pair is above, below or
crosses it across the complete declared grid. It is a user-defined posterior
rule, not association FDR, replication or a causal-direction test.

Colocalization follows strict source-family adjusted P selection independently
of the contextual top-ten search limit. Native families use the declared
BH/BY/NONE correction; supplied full-family adjustments remain unchanged.
`analysis.colocalization.max_features` is an explicit optional cap. Reviewed
pairs can therefore reference eligible findings beyond the contextual search
cap. For manual pairs, the eligible mapped inventory within that registered scope
records unrequested features as `not_requested_no_reviewed_pair`; these are not null results or a
claim that sources were searched for every feature. Automatic source discovery
still has its own declared source/query, phenotype and variant bounds. Unmapped
or ambiguous features remain in upstream result/annotation inventories.

### Observe the region before restricting candidates

The native adapter streams an inventory of each source's valid distinct regional
IDs before restricting outcome rows to exposure IDs. It preserves the source
SHA-256, declared region, source-selection basis/export cutoff, declared complete
flag, sample size and optional `regional_variant_count`. A reported unexported
universe is a declaration; the adapter cannot infer its missing variants. When
provided, the universe count must cover the observed rows, and its observed
fraction is reported separately.

`exposure-regional-variants.tsv` and `outcome-regional-variants.tsv` contain every
observed ID/position/allele/P and the candidate-retention flag. A large outcome
can be counted without constructing an LD matrix for all of it. The aligned-LD
fit retains the existing 5,000-variant budget; the separate coverage-audit budget
defaults to one million IDs per source. Exceeding either bound leaves a prerequisite
and never authorizes a silently truncated complete-region claim. Normalized
source-ID duplicates fail; positional/allelic aliases with different IDs are
not independently resolved and remain a limitation.

`regional-coverage.yaml` distinguishes:

- All observed exposure/outcome IDs, candidates retained for alignment, aligned
  variants and each corresponding matched fraction.
- Each source's strongest reported-P variant and whether it was retained/aligned.
- Alignment exclusions, source selection/export declarations and missing fields.
- Distinct observed/aligned positions, endpoint gaps and the largest gap between
  observed positions. Binned distinct-position counts are in
  `regional-coverage-bins.tsv`; multiple IDs at one position count once there.

For example, matching 3 of 3 candidate-restricted outcome rows from a source
containing 5,004 regional variants gives candidate match fraction 1.0 and observed
outcome match fraction `3/5004`. Reporting only the first number obscures the
regional coverage. Positional gaps describe observed SNP positions, not callable
bases, and no density threshold certifies completeness. The
[official coloc input guide](https://chr1swallace.github.io/coloc/articles/a02_data.html)
recommends dense regional statistics without significance filtering.

### Preserve evidence grades and convergence

Without an explicit partial request, both sources must declare complete regional
statistics and pass the registered overlap check. A complete declaration combined
with a filtered export cutoff is contradictory and leaves a prerequisite. A
successful supported fit is labelled
`regional_analysis_with_declared_complete_sources`: source completeness is not
independently certified by the software.

An explicit `partial_colocalization: true` always retains
`exploratory_partial_region_major_limitation`, including when both sources carry
complete flags. Unknown sample overlap is accepted only for this exploratory
route, with its limitation. Every canonical partial result and its finding dossier
must state that posteriors condition on observed selected variants; missing
variants, filtering, unequal coverage, winner's curse and unknown overlap can
bias them, and a null result is not evidence against a shared causal variant.
An exploratory result is never promoted by its H4 value or numerical convergence.

Each trait's fine-mapping report retains every attempted maximum effect count,
iteration bound, tolerance, prior variance, credible-set coverage/purity and
backend/command logs. Nonconvergence keeps that record and produces no colocalization
posterior. The prespecified exploratory fallback may reduce the effect count to
obtain convergence; its use remains explicit and is not a model-validity certificate.
No credible signal or insufficient shared variants in an otherwise exhausted partial
analysis is `not_estimable`, distinct from missing declarations/sources that remain
`waiting_for_input` and distinct from evidence against sharing.

One aligned reference LD matrix is currently used for both trait fits. Allele
orientation, symmetry, bounds, unit diagonal and positive semidefiniteness are
checked; ancestry/source declarations remain scientific inputs. Source-specific
LD differences and LD-reference sensitivity are not automatically assessed. See
[the upstream SuSiE-coloc guide](https://chr1swallace.github.io/coloc/articles/a06_SuSiE.html)
for the role of multi-signal fine mapping and signal-pair interpretation.

### Complete signal tables, supplied results and dossiers

Per-pair `colocalization-results.tsv` and the operation-level concatenation retain
all H0–H4 values, H4 conditional on H3/H4 when defined, component IDs/leads, primary
priors, the complete sensitivity range and any registered threshold classification.
Rows also carry observed versus candidate coverage, source/export and overlap
context, convergence/fallback, region gaps, phenotype/assay/source identifiers and
the mandatory grade/caveat. Twenty displayed preview rows never replace the full
canonical result. Multiple signal pairs for one feature are not independent
findings; colocalization cannot create association-FDR eligibility.

`colocalization-prior-grid.tsv` retains every H0–H4 row at every declared prior.
The earlier `colocalization.tsv` and first `colocalization-prior-sensitivity.tsv`
remain available, with additional indexed files for more settings. The CLI also
retains per-variant posteriors conditional on H4. A conditional-H4 variant probability
is not an unconditional probability that the variant causes both traits. Complete
canonical rows and source-linked caveats flow to finding dossiers; Methods records
retain the actual coverage, convergence and prior settings. Prior dependence remains
part of the interpretation, as described in the
[official sensitivity guide](https://chr1swallace.github.io/coloc/articles/a04_sensitivity.html).

A supplied `kind: colocalization` stage bypasses native fitting and prior-grid
execution. Its required fields remain `feature_id`, `region_id`, `pp_h4`; optional
H0–H4, component IDs, source coverage, cutoffs, convergence and native canonical
columns are preserved. Multiple pairs in one region need explicit distinguishing
component/pair identifiers. Optional probabilities must be finite and in [0,1].
When all H0–H4 are present, their sum must be within the source method's
`parameters.posterior_sum_tolerance` (default `0.000001`, at most `0.05`). Rounded
sources can declare their precision; the importer does not renormalize values.
Source coverage without a declared grade remains `source_coverage_unverified`.
Import format checks do not reconstruct regional data, LD, priors or calibration.

`colocalization-feature-dispositions.tsv` retains requested, waiting, not-estimable
and unrequested mapped features. The full source artifacts remain available even
when a model fails or no posterior is computed. Real-data acquisition/appraisal,
independent SuSiE/LD calibration, broader assay/source adapters and full manuscript
claim validation remain separate verification requirements.

```powershell
python examples/study-pipeline/colocalization-workflow.py run build/my-coloc-check --jar build/cli/jlinalg-0.3.7.jar
```

This verifier pins its own source and the executable before running. Fifteen offline
synthetic cases exercise complete/partial regions, 25 native signal pairs, unobserved
coverage, imports, budgets, nonconvergence, source conflicts, a BY family, explicit
caps and findings beyond the contextual cap. It independently enumerates H0–H4
conditional on native SuSiE effect log Bayes factors and checks raw-source counts,
harmonized Z scores, fixed primary priors, source/input hashes, cache reuse and
`study verify`. It does not independently reproduce SuSiE fitting or certify a
biological mechanism, source/LD calibration or real AI appraisal.

## One registered primary source across downstream analyses

Each hypothesis resolves `analysis.primary_result` before native enrichment,
feature-level genetic/literature selection, automatic protein follow-up,
candidate ranking and the study-wide manuscript finding inventory use an
association source. The default `kind: auto` requires the registered
meta-analysis when one is planned. Missing, incomplete or ambiguous primary
meta-analysis work, including a retained partial external import, produces a
review need; it cannot fall back to a more
significant cohort. With no meta-analysis, one completed cohort result is
selected automatically. Multiple cohort sources require a meta-analysis or an
explicit prespecified choice:

```yaml
analysis:
  primary_result:
    kind: association
    dataset_id: target-cohort
    rationale: Prespecified population and assay for the primary estimand.
```

A meta-analysis can be designated by `kind: meta_analysis` and `operation_id`
from the compiled study plan. `term` selects an explicit coefficient when the
source has multiple contrasts. Every override requires a rationale. The default
meta-analysis contrast is `pooled`; other multi-contrast sources require a
registered term. Separate scientific estimands should normally be separate
hypotheses. The selector never searches for the smallest P value or the least
heterogeneous fit.

Each executing consumer retains `primary-result-selection.yaml`: hypothesis, producer
identity, exact canonical source path and SHA-256, metadata hash, registered
policy, selected contrast, eligible row count, correction family and stable
primary identity. Hash-named local snapshots preserve the producer manifest and
supplied family metadata even when cache replay updates the live manifest.
Only completed sealed producers for that hypothesis qualify.
An association fit supersedes its harmonization copy for the same dataset;
post-effect-QC results supersede that dataset's standard table. Discovery pools,
sensitivity outputs and nested files with similar names cannot impersonate the
primary. Changing the frozen identity within one operation is rejected; a
revised protocol/input requires a new execution attempt.

Native eligible observed rows use the registered BH/BY/NONE correction in the
selected contrast. Validated supplied full-family adjusted P values, original
denominators and coverage declarations remain authoritative. A selected-only
supplied result can support scoped significance selection, but cannot define a
complete enrichment background or GSEA ranking. Sealed source tables and their
family metadata remain unchanged.

Some branches deliberately have different input scopes. Per-cohort GSEA records
`dataset_ranking:<id>` and `official_primary: false`; an explicit complete rank
file is independently supplied. Cohort influence/heterogeneity diagnostics record
`all_cohort_meta_baseline`, distinct from an official cohort or contrast override.
Risk-score predictors and evaluation partitions, raw-data correlation networks,
and QC-eligible variant annotation retain their own declared inputs. Primary
significance does not select training features or discard annotation candidates.
These scopes do not silently become manuscript primary association findings.

The offline executable check is:

```powershell
python examples/study-pipeline/primary-results-workflow.py run build/primary-acceptance-new --jar build/cli/jlinalg-0.3.7.jar
```

It pins the executable and executing verifier before generating synthetic cases.
Its `verify DIRECTORY --jar PINNED_JAR` mode requires those exact pins and checks
cross-consumer identity, correction values, coverage gates, hypothesis separation,
cache replay and `study verify`. This tests software contracts; source-model
calibration and real scientific appraisal remain separate requirements.

## Genotype QC scopes and unavailable statistics

Genotype QC is calculated in the phenotype/covariate complete analyzed sample,
after participant selection and before marker mean imputation. The genetic
study defaults retain MAC 20, maximum marker missingness 0.02 and sample call
rate 0.98. Imputed studies declare minimum INFO 0.8. A manual association-only
study schedules rare-set work only when its goals or configuration request it;
automatic mode still adds eligible rare-set branches with separate prerequisites.

Here is an explicit binary study policy. The nonzero HWE and missingness
thresholds are illustrative registered decisions; both default to zero, which
reports statistics without excluding on their P values. Choose them for the
actual design, sample size and testing policy before examining associations.

```yaml
datasets:
  - id: cohort_genotypes
    type: gwas
    # Other required dataset declarations remain as in the study contract.
    files:
      matrix: data/cohort.vcf.gz
      phenotype: data/phenotypes.tsv
      hwe_samples: data/prespecified-reference.ids
hypotheses:
  - id: disease
    # Supply outcome, covariates and dataset references as usual.
    analysis:
      association:
        model: glm
        family: binomial
        case_value: case
        control_value: control
        genotype_kind: hard_calls
        minimum_mac: 20
        maximum_marker_missing: 0.02
        chromosome_scope: human_autosomes
        hwe_filter_scope: controls
        minimum_hwe_p: 0.000001
        minimum_missingness_p: 0.000001
        missingness_max_states: 100000
```

The native CLI equivalents include `--case-value`, `--control-value`,
`--genotype-kind`, `--autosomes-only`, `--hwe-filter-scope`, `--min-hwe-p`,
`--hwe-sample-file`, `--min-missingness-p` and `--missingness-max-states`.
The reference file is a unique sample-ID list. All IDs must exist in the
genotype source; only analyzed participants enter its HWE denominator. Its
SHA-256 and counts are recorded, with checks against changes during execution.
This list does not establish that samples are unrelated or ancestry homogeneous.

Inspect `association.tsv` for every variant disposition and the native manifest
for the executed policy. `standard-results/<dataset>.tsv` contains eligible
estimated rows. New QC fields include `allele_number`, `n_samples`, case/control
called/missing counts and MAC, the selected HWE sample count and method,
missingness P/method, genotype origin and ploidy status. Group MACs use one
cohort-minor allele orientation. They are not carrier counts. Missing genotype
counts remain visible even when association uses mean imputation. A diagnostic
sample count is not a certificate of model-specific effective sample size.

VCF/BGEN ploidy is retained in selected sample order. Unknown ploidy is explicitly
assumed diploid; observed non-diploid and split-multiallelic sources are excluded
from the generic biallelic association adapter. VCF quality can be per alternate
allele. A positive quality threshold distinguishes absent quality from low
quality. Declaring hard calls rejects fractional values. Source dosages do not
receive hard-call HWE tests, including when they are numerically integers.

HWE is the conditional exact diploid human-autosome test; `all`, `cases` and
`controls` select its scope. Unsupported source/region/ploidy, an empty panel,
or non-hard genotypes retain an explicit method/status. A requested but
unavailable HWE filter excludes with `HWE_UNAVAILABLE`. Case/control missingness
uses two-sided probability-ordering Fisher exact inference; an exhausted
enumeration budget also remains unavailable and excludes when its filter was
requested. No passing P value is invented. These are QC statistics, not
phenotype association evidence. Refer to the
[HWE paper](../CITATIONS.md#wigginton-hwe-2005),
[PLINK guidance](https://www.cog-genomics.org/plink/2.0/filter#hwe) and
[BGEN format](https://www.chg.ox.ac.uk/~gav/bgen_format/spec/latest.html)
for the statistical and source-format context.

Run the independent packaged acceptance fixture from the repository root,
using a freshly built executable and a new output directory:

```powershell
python examples\study-pipeline\genotype-qc-workflow.py run build\genotype-qc-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\genotype-qc-workflow.py verify build\genotype-qc-example --jar build\cli\jlinalg-0.3.7.jar
```

It requires NumPy, SciPy and PyYAML. Its 15 offline synthetic cases pin the
executable and executing verifier before execution and check exact exclusions,
allele denominators, HWE by independent conditional enumeration, Fisher
missingness, quantitative OLS estimates, correction families, cache replay and
`study verify`. The fixtures include MAC changes after sample selection,
case/control coding, reference panels, missing quality, per-ALT quality,
fractional dosages, unknown chromosomes and unsupported ploidy/multiallelic
inputs. No public resource cache is used.

This exercise does not establish sparse binary tail calibration, ancestry or
kinship correctness, biological sex/PAR models, genotype-level sequencing
quality or real scientific validity. The default genotype GLM uses the existing
efficient-score route; QC does not select an alternative inferential method.
The explicit Firth route below is separately registered. The association method report and its
`required_manuscript_caveat` retain that boundary for downstream discussion.
Such an analysis needs an explicitly appropriate inferential method; see the
[REGENIE methods](https://rgcgithub.github.io/regenie/overview/) for context.

## Firth association for separated binary GWAS designs

Register the estimator before inspecting variant P values:

```yaml
analysis:
  association:
    model: glm
    family: binomial
    binary_test: firth-lrt
    case_value: disease
    control_value: healthy
    firth_maximum_iterations: 300
    firth_tolerance: 0.00000001
    # Optional fixed offset, separate from estimated covariates:
    offset_column: known_logit_offset
```

The CLI equivalent is `--model glm --family binomial --binary-test firth-lrt`,
with optional `--firth-max-iterations` and `--firth-tolerance`. Default
`efficient-score` keeps the existing GLM genotype route. A missing registered
model still reaches the model-review gate. An `offset_column` uses the dataset
covariate mapping, enters complete-case participant selection and becomes
`offset(column)` in the fitted formula. Do not also list it among estimated
covariates. Its scientific origin and validation remain part of the design.

The full fit maximizes Bernoulli log likelihood plus half the log determinant
of the full-design Fisher information. The coefficient-zero constrained fit
uses that same penalty. Its likelihood-ratio statistic has an asymptotic
chi-square reference on one degree of freedom. The estimator can fit
identifiable separated data without first requiring a maximum-likelihood null.
Singular designs and exhausted fitting controls remain failures, with no ridge
repair or ordinary-score fallback. See the
[method paper](../CITATIONS.md#firth-bias-reduction-1993) and
[separation paper](../CITATIONS.md#heinze-schemper-separation-2002).

Successful ordinary GWAS rows also retain their existing `t`/`z` statistic
classification in `statistic_type`. Inspect `test_method`, `estimator`, `calibration`, `statistic_type`,
`standard_error_method`, full/constrained convergence flags, iterations,
penalized log likelihoods, `log_p_value` and `convergence_reason`. The effect standard error
is the inverse ordinary Fisher information at the penalized estimate. It does
not reconstruct the LRT P value. These typed fields survive canonical
standardization and evidence inventory. Subsequent beta/SE meta-analysis has
its own approximate Wald inference. Preserve source P values and their method.
Profile likelihood confidence intervals are not computed by this adapter.

Only independent individual Bernoulli logit observations with unit trial weights
are supported. Related/clustered declarations, other families, strict
accelerator backends and ordinary-null conditional score export are rejected.
The implementation uses portable CPU fits and bounded, ordered variant blocks.
Source sample/QC counts remain visible on fitting failure; failed rows have no
P value/FDR and do not enter the eligible testing family. A dosage or fixed
offset does not by itself establish sparse-tail or kinship adjustment.

```powershell
python examples\study-pipeline\firth-gwas-workflow.py run build\firth-gwas-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\firth-gwas-workflow.py verify build\firth-gwas-example --jar build\cli\jlinalg-0.3.7.jar
```

The verifier requires NumPy, SciPy and PyYAML. It pins the executable, executing
verifier and independent R reference files before running twelve offline studies.
Seven reference designs cover balanced and separated outcomes, nuisance
separation, imbalance, offsets and dosages. Additional cases retain explicit
nonconvergence, genotype/covariate collinearity, pre-imputation missingness,
fixed-offset complete cases and underflow-safe log P values.
Full/constrained objectives and effects are compared to the upstream CRAN
`logistf` numerical fitter and independent SciPy optimization. The R generator
and fixture provenance record the exact version, official binary/source hashes,
ordinary-Fisher SE calculation and scaled reparameterization. Cache replay and
`study verify` check workflow integrity.

`calibration-enumeration.json` is a separate exact fixed-margin null audit for
binary carrier indicators with an intercept. It uses analytic half-count Firth
estimates and hypergeometric null masses. At N=1,000 with five cases and 20
heterozygous carriers, the asymptotic test rejects about **9.6264%** of null
datasets at nominal 5%. This demonstrates a calibration limit; it does not
validate covariate-adjusted tails or related samples. The mandatory manuscript
caveat preserves that boundary. Broader exact, permutation, SPA and mixed-model
routes remain required work for the full specification.

## Exact conditional tests for sparse binary GWAS

Register `binary_test: exact-conditional` when the scientific null is an
independent individual Bernoulli logit with stratum intercepts and, optionally,
fixed known offsets. This route sums the finite conditional distribution of
the ALT case-allele score after conditioning on each stratum's case count.
It accepts observed integer 0/1/2 genotypes. It does not round dosages or drop
continuous covariates. It supplies no kinship, repeated-participant or arbitrary
continuous-covariate adjustment. The default route remains `efficient-score`;
MAC 20 alone does not choose a calibrated estimator.

```yaml
association:
  model: glm
  family: binomial
  binary_test: exact-conditional
  exact_strata_column: matching_group
  offset_column: known_log_odds
  exact_maximum_states: 200000
  exact_maximum_work: 50000000
  exact_effect_maximum_parameters: 50
  minimum_mac: 20
```

Omit `exact_strata_column` for one intercept-only stratum. A named stratum
column is categorical, needs at least two observed levels, and enters the
study adapter's nuisance formula and prefit complete cases. Native CLI users
also include that column in their formula, for example
`--formula 'case ~ matching_group + offset(known_log_odds) + <omics>'`
with `--exact-strata-column matching_group`. Ordinary reference or indicator
contrasts must span the declared strata; unsupported design terms fail.
Omit `offset_column` when no scientifically justified fixed offset is available.
A fitted risk predictor or LOCO predictor is not automatically a known offset;
its estimation, independence and model validity need separate justification.

Within a stratum, each outcome assignment with the observed case count has
null weight proportional to the exponential of the sum of its fixed offsets.
Constant offsets cancel. The implementation aggregates the 0/1/2 genotype
count combinations or uses a bounded elementary-symmetric recursion for
varying offsets, then convolves stratum score distributions. The native
study route uses a two-sided probability-ordering tail, including masses no
greater than the observed mass with relative tie tolerance `1e-7`. This is a
discrete finite conditional test; no mid-P or asymptotic tail is substituted.
Strata with a pure outcome or constant genotype contribute no test information.

The conditional score test is distinct from maximizing an exact conditional
likelihood and then using an asymptotic fitted-parameter test. The
[R survival conditional-logit documentation](https://search.r-project.org/CRAN/refmans/survival/html/clogit.html)
describes that likelihood. For a single stratum and binary genotype, the score
reduces to a fixed-margin hypergeometric test with the probability-ordering
rule documented by [R Fisher's test](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/fisher.test.html).
For three genotype levels, this additive-score test is not the omnibus 2-by-3
Fisher independence test.

Missing genotypes are omitted before each variant's case counts and strata
are formed. Raw `n_samples/n_cases/n_controls` and `called/missing` retain the
source QC scope. Canonical `n/n_cases/n_controls` instead describe the called
test sample; canonical `qc_n_samples/qc_n_cases/qc_n_controls` preserve the
pre-omission QC counts. `test_n_*`, `test_strata`, `informative_strata`, method,
calibration, score moments, score direction and natural `log_p_value` survive
normalization and finding inventories. `statistic_type` is
`conditional_centered_allele_score`, with no chi-square numerator df. Finite
log tails remain available when ordinary P underflows to zero.

An optional separate unconditional Firth stratum-logit fit supplies an
approximate effect and ordinary-Fisher SE. Its coefficient and SE are not
finite conditional estimates or a Wald explanation of the exact P value.
`exact_effect_maximum_parameters` counts all stratum intercepts plus genotype;
set it to 0 to skip effect estimation. A parameter-budget, singular or
nonconverged effect fit retains the valid exact P as `result_type:
p_value_only`, with missing beta and SE. These tests remain eligible for the
registered P-based family, official single-source selection, gene-set overlap
when the source is an eligible gene-level analysis, and literature/dossier evidence. They establish no effect magnitude or beta
direction. `exact_score_direction` describes the centered test score only.
Inverse-variance meta-analysis waits when any eligible P-only test is present;
default beta/SE GSEA also waits for a complete supported ranking. Effect
correlations use finite signed pairs and record that scope; required
replication sign concordance is unavailable for P-only pairs.

The state and work bounds prevent unbounded exact calculations. They are
support/table-state and counted recurrence/convolution work limits, not a total
heap-memory guarantee.
An exhausted bound or unrepresentable conditional normalization produces no
P value. Raw partial results remain auditable, every BH value in that native
family is withheld, and the study association and downstream official
selection wait for resolution. No approximate fallback or incomplete-family
FDR is reported. An explicitly unsupported genotype representation or a
no-information margin is separately recorded as ineligible/nonestimable.
Increasing a resource bound does not repair an unsupported model.

Externally produced P-only association tables may declare
`columns: {result_mode: p_value_only}` and supply feature ID and actual P value
without beta/SE columns. For mixed tables, retain beta/SE columns, leave them
missing on test-only rows, and explicitly map
`columns.result_metadata.result_type` to a column containing `p_value_only`
or `signed_effect`. Contradictory effect values on a P-only row are rejected.
Missing effects alone never imply a test-only declaration. Imported methods,
testing-family completeness and calibration still require source provenance.

The offline packaged acceptance script is
`examples/study-pipeline/exact-conditional-workflow.py`. It pins its executable,
verifier and rational-enumeration fixtures before execution. Run it with a new
output directory and verify using the same pinned JAR:

```powershell
python examples/study-pipeline/exact-conditional-workflow.py run build/exact-example --jar build/cli/jlinalg-0.3.7.jar
python examples/study-pipeline/exact-conditional-workflow.py verify build/exact-example --jar build/cli/jlinalg-0.3.7.jar
```

These fixtures test finite null masses and state/metadata propagation; they
are not biological evidence. Small reference fixtures explicitly lower MAC
and call-rate thresholds. Separate fixed-margin null scenarios retain MAC 20.
Their enumerated rejection probabilities establish those declared null cases,
not correctness under arbitrary covariates, estimated offsets, relatedness,
population transfer or informative genotype missingness.

## Native binary risk-score development and frozen probabilities

Register `risk_score.family: binary` with individual 0/1 outcomes to train a
binomial ridge, lasso or elastic-net molecular score. This source-build route
uses the same explicit, group-safe training/tuning/evaluation partitions as the
quantitative workflow. It fits a joint feature-plus-covariate probability model
and a separate covariate-only comparator. The recipe is distinct from the
quantitative residual-outcome model and from the standalone Gaussian
`score-train` command.

```yaml
risk_score:
  file: participants.tsv
  family: binary
  outcome: case_status
  features: [protein_A, protein_B]
  covariates: [age, sex]
  categorical_covariates: [sex]
  group_columns: [family_id]
  resampling_group: family_id
  lambdas: [1.0, 0.1, 0.01]
  alpha: 0.5
  binary_baseline_lambda: 1.0
  binary_maximum_iterations: 10000
  binary_tolerance: 1.0e-8
  max_work: 250000000
  missing: training_mean
  refit: true
  bootstraps: 200
  related_phenotypes:
    - {column: BMI, family: quantitative}
```

The full objective is mean individual Bernoulli negative log likelihood plus
`lambda * (alpha * sum(abs(beta)) + (1-alpha)/2 * sum(beta^2))` on
training-standardized molecular features, and
`binary_baseline_lambda / 2 * sum(gamma^2)` on training-standardized
non-intercept covariates. The intercept is unpenalized. The comparator has a
separately fitted intercept/covariates with the same registered covariate-ridge
penalty. Feature lambdas and the covariate penalty must be positive. This keeps
regularization explicit, including for separated development data; a Gaussian
likelihood or unregistered unpenalized binary fit is never substituted.

Alpha 0 is ridge, 1 is lasso, and intermediate values are elastic net. The
standard elastic-net objective is described by
[Friedman, Hastie and Tibshirani](https://doi.org/10.18637/jss.v033.i01).
JLinAlg uses bounded cyclic coordinate proximal-gradient steps with the global
logistic one-quarter curvature bound and a recorded KKT stopping tolerance.
This implementation is independently checked against convex SciPy optimization;
it is not a claim of complete glmnet path/option parity. Tolerance and KKT
residuals refer to the standardized coefficient coordinates. Iteration/work
exhaustion or unrepresentable preprocessing/serialization leaves the operation
waiting with a reason; no partially optimized frozen model is published.

Each candidate learns imputation means, population-variance feature/covariate
scales and both models from the training partition. Tuning **log loss** selects
the feature lambda; exact ties keep the first, stronger value in the descending
grid. Both development and tuning partitions need cases and controls. With
`refit: true`, the selected model, comparator, means and scales are fitted again
on training plus tuning records; categorical vocabulary remains frozen from
the training partition. Evaluation records never affect those fits or choices.
`max_work` also bounds counted optimizer visits across candidates and refits;
it is distinct from a wall-clock or total-memory limit. The dense-table bounds
and group/identity checks from the quantitative workflow still apply.

`tuning.tsv` records `tuning_log_loss`. `score-model.yaml` uses
`jlinalg-held-out-binary-score-v1` and retains raw feature weights, imputation
means/scales, centered-score intercept, full prediction covariate weights,
separate comparator weights, logit link, penalties, categorical levels and
hash-linked development identities. The centered molecular score is a
log-odds component. Its standalone weight table is insufficient for calibrated
probability application; apply the complete model with `mode: model` and its
original identity sidecar. Frozen predictions use the full joint-model
covariates plus the molecular component, while comparator predictions use its
own covariate-only weights. The two covariate coefficient vectors are different
estimators. No evaluation-baseline or probability-model refit is performed.
The effective method report uses source-model penalties and preprocessing;
unused training defaults in the application configuration are not reported as
parameters of a refit.

The default prediction scale is `probability` for native binary training and
complete binary-model reuse.
Evaluation log loss and calibration logits clip supplied probabilities to
`[1e-15, 1-1e-15]`; this reported numerical convention can cap more extreme
log-loss contributions. Tuning uses the stable, unclipped linear-predictor loss. Aggregate outputs include AUC, molecular-score
AUC, comparator AUC, Brier score and log loss, with optional conditional
independent-unit bootstrap intervals and subgroup metrics. Raw imported scores
keep their original raw-versus-probability contract. Logistic calibration
intercept/slope are optional evaluation summaries: complete/quasi separation,
constant predictions or failed fits leave them unavailable. Good discrimination
alone does not establish calibration. Related-phenotype tests use the frozen
molecular score on the declared evaluation population and retain the separate
complete BH/BY/NONE family. If a related input is missing, invalid or unestimable,
completed performance remains source-linked context in the finding inventory.
The related family stays unresolved, with no official adjusted P values or
significant related-finding claim. Per-table completion is explicit; the partial
operation is never relabeled complete.

For both newly exported quantitative and binary models, the development-ID
sidecar includes **training and tuning participants and groups even when
`refit: false`**. Tuning data were used in selection and cannot later become
independent evaluation data. `development_rows` counts actual final fitting
rows, whereas `development_identity_rows` counts all fitting/selection records.
Observed ID/group overlap blocks complete-model reuse. Legacy quantitative
models without selection-identity metadata retain an explicit coverage caveat;
a source independence statement is not independent software verification of
unavailable tuning identities.

All weights are predictive coefficients, not association effect estimates.
Case/control sampling prevalence does not establish population absolute risk;
independent population evaluation and appropriate recalibration remain
necessary. Penalty tuning here is explicit holdout selection, with conditional
performance intervals, rather than nested CV or an assessment of training and
feature-selection uncertainty. Large genome-wide fitting, censoring-aware
survival models and broader transportability analysis remain separate work.

The offline executable acceptance example is
`examples/study-pipeline/binary-risk-score-workflow.py`. It pins the executable,
executing verifier and independent reference fixtures before running. It covers
native ridge/lasso/elastic-net, optional refit, complete model reuse, unchanged
fitting after evaluation perturbation, ID/group leakage, single-class development,
optimizer limits and incomplete related-test families. The original quantitative/
import workflow remains separately checked.

```powershell
python examples/study-pipeline/binary-risk-score-workflow.py run build/binary-score-example --jar build/cli/jlinalg-0.3.7.jar
python examples/study-pipeline/binary-risk-score-workflow.py verify build/binary-score-example --jar build/cli/jlinalg-0.3.7.jar
```


## LD-aware GWAS functional-category enrichment

The native `gwas_enrichment` study stage fits observed-scale **stratified LD
score regression (S-LDSC)** for overlapping binary annotations. It is separate
from unpartitioned `ldsc`, ordinary GO over-representation, preranked molecular
GSEA and GWAS Catalog lookups. The implementation follows the partitioned
fixed-initial-weight estimator and overlap-aware category contrast described by
[Finucane et al. (2015)](../CITATIONS.md#finucane-sldsc-2015), with a pinned
[upstream numerical reference](https://github.com/bulik/ldsc/tree/2fdeeb3b44379408794154993dbd6101b8946b7e).

Register the complete GWAS source, ancestry/population and build-matched LD
reference, baseline/nuisance annotations, target categories and correction
family before looking at enrichment results. A target can represent a
prespecified gene set, tissue or regulatory category **only when its binary
variant annotation, LD scores and reference intersections have actually been
constructed**. Nearest-gene hit counts do not create those inputs. This stage
does not acquire or construct an LD reference automatically.

```yaml
analysis:
  mode: manual
  goals: [gwas_enrichment]
  gwas_enrichment:
    summary:
      file: dense-gwas.tsv
      coverage: complete
      tested_snps: 1200000
      population: EUR-reference-population
      genome_build: GRCh38
      statistic: chi_square
      calibration: one_df_chi_square
      ascertainment: quantitative
      model: "Registered calibrated marginal GWAS model and covariates"
      qc: "Source sample/variant QC, ancestry, relatedness, calibration and exclusions"
      provenance: {producer: "source study", release: "registered release"}
    reference:
      population: EUR-reference-population
      genome_build: GRCh38
      release: "registered LD and annotation release"
      common_variant_definition: "Same common-reference-SNP universe for M and intersections"
      regression_snp_definition: "Prespecified well-imputed regression SNP panel and LD windows"
      annotation_model: "All-SNP baseline plus registered binary nuisance and target annotations"
    ld_scores: ld-scores.tsv
    annotations: annotations.tsv
    overlap: annotation-intersections.tsv
    blocks: 200
    minimum_snps: 10000
    minimum_chromosomes: 20
    minimum_coverage: 0.9
    minimum_h2_z: 4
    intercept: free
    adjustment: BH
```

The full summary defaults to columns `variant_id`, `chromosome`, `position`,
`ref`, `alt`, `chi_square`, `n`; their names are configurable. `statistic: z`
squares the named statistic. `statistic: beta_se` computes the squared Wald
ratio from logical `beta` and `se`, and requires the same explicit calibrated
one-degree-of-freedom source declaration. An exact-test P value or penalized
likelihood-ratio P value does not establish a Wald chi-square statistic.
Declare the source model/calibration; do not convert arbitrary P values into
chi-squares to bypass that requirement. For case/control GWAS set
`ascertainment: case_control`; outputs remain on the sampled observed scale.
Per-SNP `n` must have the meaning required by the GWAS calibration, rather than
substituting an unverified enrollment or effective sample count.

Omit `summary.file` to consume the registered **official complete result** from
association or supplied meta-analysis. Keep the source declarations and exact
row count; the selected result must retain allele, coordinate, sample-size and
chosen statistic columns. Set `id_column: feature_id` when adapting an official
table with an explicitly configured summary. A selected-only primary export,
ambiguous primary source or unavailable upstream model cannot silently fall
back to another cohort. The retained primary-selection receipt identifies the
actual producer and its hash.

`ld-scores.tsv` has `variant_id`, `chromosome`, `position`, `ref`, `alt`,
`weight_ld` and one `ld_<term_id>` column per annotation. Regression weights
use LD summed over the regression SNP panel; annotation LD scores use the
declared reference and windows. Input row order may vary: matched retained SNPs
are sorted by autosome, position and ID before blocking. Only autosomal
biallelic SNPs are supported. Matched summary alleles must equal the reference
pair, optionally reversed because the statistic is unsigned; build, coordinate
or other allele discrepancies are audited. Two IDs for the same physical
coordinate/allele pair, including reversed coding, cannot duplicate a reference
SNP. Strand complements are not guessed.
Finite negative unbiased LD-score estimates are allowed; aggregate LD and
weight LD are clamped to at least one **for weighting only**, as in upstream.

`annotations.tsv` has `term_id`, `term_name`, `role`, `count`. The first row is
the unique `all` baseline covering every reference SNP. Other roles are
`nuisance` or `target`. Every annotation is fitted simultaneously. Counts must
be positive integer common-reference-SNP counts, **not regression-row counts**.
Identical binary annotations cannot be assigned distinct coefficients or
arbitrary different LD scores; remove an alias only through a prespecified
scientifically coherent model revision.
`annotation-intersections.tsv` has `term_id` and a column for each annotation
ID, giving the complete symmetric reference intersection matrix. Diagonals
equal annotation counts; the all-SNP row equals those counts. Bounds, symmetry
and positive semidefiniteness are checked. These checks cannot establish that a
declared reference was generated correctly. Continuous annotation/baseline-LD
models, ancestry mixtures, sex chromosomes and alternate S-LDSC-CTS separate
target models need a suitable external analysis rather than a relabeled binary
model.

The regression fits `E[chi_square_j] = intercept + N_j * sum_k(tau_k * LD_jk)`.
For category C, total heritability is `sum_k(M_k * tau_k)` and category
heritability is `sum_k(M_C_intersect_k * tau_k)`. Consequently, overlapping
category heritability differs from `tau_C * M_C`. Enrichment divides category
heritability proportion by its reference-SNP proportion. The primary nominal
test compares average per-SNP heritability inside C with its complement using
the complete coefficient covariance and a two-sided t distribution with B
degrees of freedom, matching the pinned overlap estimator. Nominal conditional
coefficient tests are retained separately and do not enter this target family.
BH/BY/NONE applies jointly to all registered targets. NONE is nominal testing.
No best baseline, annotation subset or model is selected from enrichment P
values.

Defaults exclude autosome 6 positions 25,000,000–34,000,000 inclusively as an
explicit broad MHC region; replace `excluded_regions` with build-appropriate
registered regions or an empty list with scientific justification. Reference
counts and intersections retain their declared full universe. After reference,
allele and region matching, the default exclusive chi-square cap is
`max(80, 0.001 * maximum_valid_matched_N)`, or supply `chi_square_max` before
analysis. `snp-qc-exclusions.tsv` retains mismatch, missing-source, region,
invalid-statistic and high-chi-square reasons. Coverage before the high-chi-square
filter is measured against nonexcluded regression-reference SNPs; the SNP and
chromosome minima apply after filtering. Row-count, source completeness and QC
declarations remain scientific provenance, not proof of calibration.

The estimator normalizes per-SNP N by its mean, uses the aggregate initial
partitioned LDSC weights, and holds those weights fixed in equal-count
genome-ordered block deletes. It does not reuse the iterative unpartitioned
estimator. Singular, collinear, unstable full or deleted designs cause an
explicit unresolved stage; no ridge repair or column removal occurs. The
default limits are 2,000,000 LD regression rows, 128 annotation columns and
10,000,000,000 estimated arithmetic work units. Exceeding a limit does not
truncate SNPs or targets. These are computation/row bounds, not a memory or
throughput guarantee: dense table parsing and LD matrices currently require
memory, and large real genome-wide scale acceptance remains to be done.
Small numerical fixtures lower coverage minima for
verification; such inputs do not establish reliable biological inference.

Successful outputs include `gwas-enrichment.tsv`, all conditional coefficients
in `sldsc-coefficients.tsv`, the full coefficient covariance, block deletes,
`regression-snps.tsv`, immutable input snapshots and
`gwas-enrichment-analysis.yaml`. Category proportions/enrichments use ratio
block-delete uncertainty. Nonpositive/weak total heritability, nonpositive
delete totals or an unestimable eligible target withhold **all official adjusted
P values** and leave `official_family_complete: false`. Estimates remain
available for diagnosis. Negative conditional effects and out-of-range
proportions are retained, with their interpretation limits. Fewer than 200,000
regression SNPs adds a precision/coverage caveat. Complete target rows enter
the study finding inventory with their FDR, enrichment uncertainty and source
caveats. Their aggregate fit context includes total heritability/SE/Z,
intercept/SE, regression-SNP/chromosome coverage, per-SNP N range/mean, the
reference population/build/release and the target correction family. Raw LD
and full GWAS input files are not embedded in these dossiers. Later AI
appraisal can weigh this evidence without treating category enrichment as a
causal or independently replicated story.

Previously completed enrichment can instead be registered as
`supplied_results.kind: gwas_enrichment`, with logical `term_id`, `p_value`, its
method, scope, source provenance and full testing family. Existing
`adjusted_p` values and enrichment estimates/uncertainties are preserved.
Selected-only exports require adjusted values explicitly traced to the full
source family and its tested count. The supplied stage satisfies this goal
without LD inputs or a statistical refit; the import receipt states that the
external analysis was not reproduced.

Run the offline packaged acceptance with a new output directory:

```powershell
python examples/study-pipeline/gwas-enrichment-workflow.py run build/my-sldsc-acceptance --jar build/cli/jlinalg-0.3.7.jar
```

The retained bundle pins the executable, verifier, independent QR reference and
fixtures before running, checks complete/partial/supplied branches and cache
replay, and verifies sealed artifacts. The synthetic numerical references
match upstream LDSC revision `2fdeeb3b44379408794154993dbd6101b8946b7e` for
free and fixed-one intercepts. This establishes the tested estimator's
numerical behavior; real genome-wide calibration, reference construction,
continuous models and scientific/AI appraisal remain separate work.

## Conditional binary rare-variant sets

Native binary set association uses a **fixed conditional Bernoulli null**. This
is a bounded alternative for sparse independent case/control designs, alongside
the existing Gaussian score/covariance and imported calibrated-result routes.
The MAC 20 single-marker default is not a set-test eligibility rule. Register
rare masks, consequences, frequencies, genotype QC, covariates and the testing
family independently before inspecting the outcome associations.

```yaml
analysis:
  mode: manual
  goals: [rare_variant_analysis]
  association: {model: glm, family: binomial, link: logit}
  multiple_testing: {method: BY, alpha: 0.05}
  rare_variants:
    trait_id: registered_disease
    primary_test: acat-o
    tests: [burden, skat, skat-o, acat-v, acat-o]
    minimum_mac: 0
    minimum_info: 0.8
    maximum_marker_missing: 0.02
    masks:
      - {id: coding, consequences: [missense, lof], maximum_maf: 0.01}
    binary:
      method: conditional
      mode: auto
      strata_column: registered_stratum
      variant_coverage: require_complete
      maximum_exact_states: 100000
      pilot_draws: 10000
      draws: 100000
      maximum_work: 1000000000
      maximum_total_work: 10000000000
      maximum_cells: 20000000
```

Each source is an autosomal, allele-resolved VCF/PLINK/BGEN cohort matrix with
aligned prefit-selected participants and the same registered phenotype/build,
case/control definitions and mask annotation release. Distinct cohort labels
are required for independent pools; declared dependence, related participants,
GRM or pedigree inputs wait for an appropriate calibrated analysis. They do
not become independent by conditioning on case totals.

Within each cohort/stratum, the case total `k` is fixed. For fixed log-odds
predictors `eta`, an allocation `z` has probability proportional to
`exp(sum(z_i * eta_i))` among allocations with `sum(z_i) = k`. Equal offsets
give uniform label allocations. Known offsets/slopes require
`binary.fixed_effects: true` and nonempty `known_effects_provenance`; numerical
coefficients fitted to these same outcomes do not establish this contract.
Every declared covariate must be the registered stratum or have a known slope
in `known_covariate_coefficients`. Unknown continuous slopes, kinship and
survival models are not estimated or silently omitted. Import a complete,
model-specific calibrated set analysis when this conditional null is unsuitable.

The cohort/stratum intercept profiles to its case count with the fixed offsets.
The score center is that restricted-null logistic mean; it is not asserted to
be the finite conditional expectation. Complete finite or sampled outer tails
calibrate the chosen fixed score statistic. All-case or all-control strata
contribute no information; a native cohort must retain both outcome labels
and at least one informative stratum. Offset spans above 700 within a stratum
are rejected as outside the implemented numerical range.

Genotypes/dosages are fixed predictors. Missing calls are mean-imputed within
the fixed prefit cohort, while MAF/MAC eligibility uses observed called alleles,
not imputed pseudo-counts. Observed monomorphic rows remain available for
other cohorts' joint sets; scope-wide monomorphism is excluded separately. Eligibility, minor-allele recoding and frequency
weights are recomputed for each selected cohort/discovery/all-cohort/omission
scope. All-sample HWE is optional and explicitly identified; it is not a
case/control-specific HWE protocol. Genotype, imputation, external-coefficient
and sample-selection uncertainty are not propagated. Caller-specific QC,
ancestry calibration and assay validation remain required scientific work.

The primary statistics are squared burden, a SKAT-style quadratic kernel, a
rho-grid adaptive kernel statistic, ACAT-V-style variant/collapsed-score
combination and the six-component ACAT-O combination using beta(1,25) and
uniform weights. Default burden weights are equal; default kernel weights use
the existing raremetal-beta frequency floor `2/N`, and ACAT-V uses beta(1,25)
weights. `acat_mac_threshold` defaults to 10 for the low-MAC collapsed component.
The [SKAT-O](../CITATIONS.md#lee-skato-2012) and
[ACAT](../CITATIONS.md#liu-acat-2019) references motivate these statistics;
**this finite conditional implementation does not claim R SKAT or asymptotic
ACAT numerical equivalence**.

Adaptive/Cauchy transformations use internal mid-tail ranks. Those ranks and
the raw Cauchy statistic are never reported as association P values. All five
final tests receive outer calibration:

- Exact mode evaluates complete weighted upper tails including ties. Genotype
  patterns compress equivalent participant allocations, preserving their
  combinatorial/offset probabilities. `allocation_states` counts grouped
  allocations, not individual label assignments. Outside complete enumeration,
  that count can be capped at `maximum_exact_states + 1`.
- Monte Carlo freezes transformations on an independent pilot, then samples
  the main null with a fixed seed and draw count. Final P is
  `(tail_draws + 1)/(draws + 1)`. Clopper-Pearson 95% bounds concern the true
  conditional tail of that frozen-pilot statistic, not a confidence interval
  for the plus-one estimate or a propagation of model/multiplicity uncertainty.
  The [conditional Monte Carlo reference](../CITATIONS.md#hemerik-goeman-2018)
  explains why the resampling law and inclusion of the observed statistic matter.
- All statistics retain 40 fraction bits to define roundoff ties consistently.
  Exact underflow can display P = 0 while `log_p_value` remains finite. It is
  a representational limit, not a true zero tail probability. Sampled tests
  have a nonzero resolution floor. `constant_evaluated_null` describes the
  evaluated support/draws; a sampled constant is not proof of a constant full null.

`auto` uses exact enumeration when its state/cell bounds permit it and otherwise
uses the registered sampled method. Explicit `exact` never silently switches.
Work limits count estimated numerical operations, and cell limits bound key
matrices/workspace estimates; neither is a process-RAM or wall-time guarantee.
The default retained variant bound is 2,000 across a source export, so this is
not yet a genome-wide streaming rare-set implementation. Exceeding a limit
retains a waiting branch rather than truncating variants or choosing favorable
sets. Failed fits conservatively charge their allowed work; changing work
budgets can reuse identical completed fits.

`require_complete` leaves a scope family unresolved when a required marker is
unobserved in a selected cohort; absence is not assumed reference homozygosity.
An observed marker failing registered outcome-independent QC is excluded jointly
before that coverage check. Explicit `intersection` uses the measured passing
intersection and retains every exclusion. A zero-eligible-variant mask is
recorded separately from an unresolved eligible fit. Every scope corrects all
eligible masks and requested tests jointly with registered BH/BY/NONE. BH needs
its usual dependence assumptions; overlapping sets do not establish them.
Any unresolved eligible fit withholds that scope's entire adjusted-P family.
Raw completed tests and diagnostics survive, and a separately complete primary
family remains in the aggregate findings inventory when other scopes wait.

Discovery-only tests freeze selection for validation. The all-cohort primary
and leave-one-cohort-out fits are separate scopes, with the registered minimum
cohort count. These are joint conditional tests, not inverse-variance pooling
of binary Gaussian scores. Burden is **unsigned test-only** evidence in this
branch: no odds ratio, beta, SE, direction, Q or I-squared is estimated. Replication
of a gene/mask does not establish the same causal variants or mechanism. A
requested burden direction requirement remains unavailable rather than being
fabricated.

Private `conditional-samples.tsv` and row-aligned `conditional-genotypes.tsv`
remain local. Aggregate `rare-results.tsv`, `rare-primary-results.tsv`,
`rare-replication.tsv`, omission/influence tables, `binary-null-diagnostics.tsv`
and `binary-mask-eligibility.tsv` retain sample/case/control counts, calibrated
P/log-P, family status and numerical precision. Private participant rows never
belong in AI evidence payloads. Completed per-gene fits have sealed identities
covering software, fixed null, data/mask hashes, weights, calibration and seed.
Raising per-fit/total work limits reuses those identical completed fits, even
from an incomplete prior family. Changed scientific inputs invalidate reuse.
Damaged derived caches are retained, identified and recomputed from verified
inputs; corrupt raw inputs are not repaired automatically.

Completed external binary set results can satisfy the typed
`supplied_results.kind: rare_variant_analysis` stage. Supply the complete
registered family and its methods, phenotype/cohort scope, calibration,
provenance, limitations and adjustment. A selected-only export needs original
full-family adjusted P values and denominator. This avoids private matrix
reconstruction and statistical refitting. See the
[supplied-results guide](#reuse-completed-analyses-and-run-missing-work).

The runnable offline workflow
`examples/study-pipeline/binary-rare-variant-workflow.py` covers native exact and
sampled three-cohort analyses, a cohort-private variant, known offsets,
missing/intersection coverage,
unknown slopes, relatedness, minimum-cohort gates and completed-stage reuse.
Its BY/family, private-row exclusion and integrity checks are synthetic software
acceptance, not real case/control calibration, power, mechanism or AI appraisal.
The fixture explicitly raises the aggregate payload/batch budgets to inspect
both complete large dossiers together, with assistance disabled. Production
batches retain pending candidates; no dossier is truncated to fit a budget.
The companion acceptance uses an externally attributed synthetic budget
amendment to finish missing omission analyses while reusing ten completed gene
fits and preserving primary result hashes. Completed study definitions change
through recorded amendments, not by replacing the base file.

## Rare-set findings in enrichment and contextual literature

The study planner now adds `rare_set_followup` after a native or supplied
`rare_variant_analysis`. This preserves the **all-cohort primary gene/set by
mask** results, including unavailable rows, and the original full joint
set/mask/test correction. It assesses explicit gene mappings, not association
models. A complete primary family can continue downstream while separate
omission scopes are unresolved; incomplete primary families cannot be replaced
with successful secondary tests, cohorts or discovery-only results.

```yaml
analysis:
  rare_variants:
    followup:
      enabled: true
      enrichment_adjustment: BY
      minimum_term_genes: 5
      maximum_term_genes: 5000
      maximum_gene_sets: 20000
      maximum_testing_rows: 2000000
  operations:
    gene_ontology: {result_source: rare_sets}
    pathway_enrichment: {result_source: rare_sets}
  bioinformatics:
    feature_source: auto
```

These defaults are resolved and recorded. `result_source` accepts `auto`,
`association` and `rare_sets`. `auto` enrichment uses the registered feature
association/meta-analysis primary when available, or the verified rare-set
projection in a rare-only study. Use `rare_sets` explicitly for a rare-set
ontology analysis in a mixed study. Separate compatible hypothesis/protocol
blocks can retain different association and rare-set enrichment analyses;
selecting a downstream source does not change the original primary estimate.
A configured external enrichment command and a completed supplied enrichment
retain their existing execution/reuse contracts.

Native set inputs use `datasets[].files.annotation` to map `feature_id` (the
set identifier) to `gene_id`/`gene_symbol`, with source/release, build and
coordinate declarations. Supplied rare results can carry
`files.feature_annotation`. A set called `GENE1` does not establish its gene
identity. Assessed mappings and `rare-feature-dispositions.tsv` retain unmapped,
ambiguous, nonsignificant and unavailable tests. The original complete source
results and producer manifest are snapshotted; the primary projection retains
its original adjusted P values and denominator. Source flags must identify one
prespecified primary test for every set/mask. Missing secondary-test adjustment
cannot redefine the joint source family.

GO uses explicitly mapped symbols, and Reactome uses mapped Ensembl gene IDs,
with an explicitly human source for the built-in human references. Each mask
has its own **mapped, estimable, tested-gene background**. Multiple tested sets
mapping to one gene in the same mask require a registered collapse/model or
supplied calibrated enrichment; no minimum-P gene collapse is inferred.
Selected-only source exports can retain their full-family adjusted P values for
literature, but cannot establish an enrichment background from the annotation
catalogue alone.

Ordinary one-sided hypergeometric ORA runs all background/size-eligible terms
for every estimable mask. Zero-hit terms and empty selections remain in the
joint mask-by-term correction family. GO and Reactome have separate declared
collection families. `enrichment_id` keeps a mask/term result distinct while
`term_id` retains its ontology identity. Supplied mask-specific enrichment and
pathway-network membership preserve these identities and the original source
adjustment. Collection manifests and actual resource bytes enter cache keys.
Bounds stop the complete operation rather than selecting favorable masks or
terms. No eligible source tests means no applicable enrichment, not a negative
association result.

**This ORA is exploratory.** Its exchangeable-gene selection assumption does
not account for gene length, variant opportunity, MAC, callability, ancestry,
coverage or annotation-dependent detection. BY is the default enrichment
correction; BH needs its usual dependence assumptions. These corrections do
not repair biased gene selection or establish pathway activation, replication
or causality. A calibrated bias-aware enrichment may be supplied instead.

Literature `feature_source: auto` retains eligible association and rare-set
signals together. A shared gene can carry both original source families; they can share
participants, genes and variants and do not establish independent replication.
Representative P/adjusted-P values only order contextual retrieval and are not
combined gene-level probabilities. Every qualifying primary mask/test remains
in `selection_context`. Conflicting gene identities require review. An
unavailable association source does not discard an separately complete
rare-set literature search, but keeps the overall source review incomplete.
Explicit `association` or `rare_sets` restricts contextual selection to that
branch. Protein/sequence investigation keeps its official association source
in mixed studies unless `rare_sets` is explicitly requested.

The default literature cap remains 100 queryable significant signals, with an
uncapped local eligibility inventory and separate query budget. Unmapped or
unsafe gene symbols cannot consume a rare-set literature query. Retrieval,
missing queries and no matches remain distinct from scientific appraisal.
Unsigned rare-set results do not become molecular exposure beta/SE estimates
for MR, signed GSEA rankings or causal effects.

`examples/study-pipeline/rare-followup-workflow.py` exercises native binary and
supplied rare results, complete versus selected-only families, mappings,
empty selections, reference/budget gates, mixed literature and supplied GO
with missing native Reactome work. It is an offline synthetic software
verification workflow; actual literature/AI appraisal and biological
calibration remain separate requirements.

## Rare-set cohort overlap with complete source families

The planner adds `rare_cohort_overlap` when rare-set analysis has at least two
declared cohort datasets and `analysis.cohort_overlap.enabled` is true (the
default). It runs alongside scalar `cohort_overlap` in mixed studies. The
source is the unique, hypothesis-matched, sealed `rare_variant_analysis`
producer. A primary pooled table cannot reconstruct per-cohort tested
universes. A typed supplied `rare_cohort_overlap` replaces this diagnostic
without rerunning the source association analysis.

Each counted item is an exact **set ID / mask ID / prespecified primary test**
tuple. Two masks for one gene remain separate. Secondary registered tests are
excluded from the display but remain in the original full joint source FDR
denominator. Overlap does not estimate a new gene-level P value or FDR. A
separate `cohort-overlap-set-summary.tsv` describes selection in any displayed
mask; matching set IDs there can represent different masks and variants.

The operation writes:

- `cohort-overlap-membership.tsv`: every observed primary-test tuple crossed
  with every declared cohort dataset, including explicit unavailable rows,
  original and selection adjusted P, source denominators, roles and applicable
  burden direction.
- `cohort-overlap-families.tsv`: full source joint denominators, selected
  family denominators, correction method, thresholds and source hash.
- `cohort-overlap-features.tsv`, `cohort-overlap-patterns.tsv` and
  `cohort-overlap-degree.tsv`: exact exclusive patterns and tested coverage.
- `cohort-overlap-upset.png/.pdf`, plus Venn PNG/PDF for two or three cohorts,
  when there are selected tuples. The complete tables remain available when
  no tuples pass selection. `plot_patterns` bounds the display, not the
  membership table or the analysis scope.
- `cohort-overlap.yaml`: counting unit, complete source-scope receipts,
  frozen-family definition, coverage-aware alerts and mandatory caveats.

The default `universe: intersection` compares tuples estimable in every
declared cohort. `union` retains cohort-specific coverage in the display.
Unobserved or unestimable rows are unavailable, not negative results. The
maximum set/feature/cell bounds leave the complete diagnostic waiting rather
than selecting favorable cohorts or top hits.

Every contributing cohort must have its own complete, source-declared joint
testing family, including adjusted P for all estimable registered tests and
masks. A waiting binary analysis can still provide complete cohort scopes;
an unresolved primary or omission scope does not invalidate those separately
complete scopes. An unresolved contributing cohort does block the complete
overlap analysis. Hit-only exports and a supplied primary rare-set family
without per-cohort families cannot establish absent hits or source denominators.

Plain descriptive overlap also remains available when a multiple-cohort
discovery pool is unresolved. The unavailable discovery reference and discovery
candidacy remain explicit. Reduced or directional validation still needs that
reference. An explicitly native, complete analysis with zero eligible tests and
`performed: false` instead records inapplicable overlap; an empty external
export cannot establish the same disposition merely because it has no rows.

With `validation_family: discovery_subset`, register `universe: intersection`.
The discovery reference is a complete discovery-only source. Multiple discovery
cohorts require a source-declared matching discovery-pool composition. Freeze
all significant registered tests and masks in the common tested universe,
including secondary tests; then correct validation P across that complete
frozen family. Only primary-test tuples enter the overlap display. Both the
original source adjusted P and the new validation-family adjusted P remain
visible. No discovery P or source adjustment is redefined by the display.

`direction: report` retains signed burden opposition where available. Unsigned
SKAT, ACAT and conditional binary tests have no beta or effect direction.
Requesting `require_discovery` when the necessary signed effects are unavailable
leaves the diagnostic waiting; it cannot turn unsigned tests into negative
validation evidence. A zero-shared-hit alert requires observed shared tested
coverage and selected tuples on both sides. It requests review of power,
coverage, assay and variant content; the primary cohort set stays unchanged.

To reuse an external membership analysis:

```yaml
analysis:
  mode: manual
  goals: [rare_cohort_overlap]
  supplied_results:
    - id: completed-rare-overlap
      kind: rare_cohort_overlap
      files: {results: rare-membership.tsv}
      scope: {datasets: [cohort_a, cohort_b, cohort_c]}
      method: {name: External rare-set membership and selection}
```

Required columns are `feature_id`, `mask_id`, `test`, `set_id`, `in_universe`,
`source_estimable` and `selected`. `set_id` names the dataset. Supply an explicit
row for every tuple/dataset pair, and declare all datasets in `scope.datasets`.
Missing rows or an entirely absent cohort leave views waiting. Selection flags
are retained as source-assessed labels; the importer builds missing count/plot
views without recalculating significance, claiming independent calibration or
asserting a native zero-overlap alert. Source-adjusted P, denominators, signed
effects and method context can accompany those required fields. All imported
inputs and generated views remain sealed and externally attributed.

The offline example `examples/study-pipeline/rare-cohort-overlap-workflow.py`
independently reconstructs source corrections, frozen validation families,
membership cells and exclusive counts from synthetic input tables. It also
exercises native conditional binary results, mixed masks, missing coverage,
signed and unsigned direction, bounded work and complete/partial supplied reuse.
This is software and mathematical verification, not real scientific appraisal.

## Resume completed native genetic pairs

`study auto` and `study run` resume completed work by default. MR,
colocalization and fine-mapping now also resume independent completed pairs
when a source amendment or another unfinished pair requires the enclosing
operation to run again. Completing the outcome source for one pair can reuse
the other completed pairs while attempting only the missing pair. Adding pairs
retains the complete registered feature inventory; it does not shrink the
testing family to the pairs that happened to finish.

Reuse requires identical reviewed pair declarations, current feature
eligibility, molecular/outcome/LD/allele source bytes, effective inference
parameters and running implementation. Local files must still exist.
Checksum-pinned public sources can reuse their sealed prior snapshots without
a new download. Automatic source/reference acquisition without a pinned
pair-level identity executes normally. Changed approval, ancestry, region,
instrument rules, priors or source bytes require a new attempt. Waiting pairs
are retried. A completed but unestimable pair can be reused with its reason
and limitations; it never becomes a null effect or evidence against sharing.

Each completed reusable pair retains `native-pair-receipt.yaml`, its input
identity, raw result record and all output hashes. The enclosing operation
must also seal that receipt and every pair output. At executor level, invalid
derived pair outputs are retained and bypassed in favor of a new attempt from
current inputs. The audited study CLI first verifies project history: damaged
historical hashes block execution and require an explicit investigator repair.
Pair reuse does not bypass that project integrity rule.
No partially verified set is copied into the new pair directory.
`genetic-pair-reuse.yaml` records reused pairs, new attempts, invalidations and
the exact prior receipts. A pair may be reused from an operation that is still
waiting on another pair. MR adjustment and family completeness are evaluated
again from the current full family; the per-pair receipt contains no old joint
adjustment. Colocalization retains its original priors, coverage, convergence
and all signal-pair rows. Finding dossiers identify reused native evidence and
carry the mandatory caveat that reuse is not independent replication.

If the entire enclosing operation is reused, its pair counts and attempt flags
describe the producing execution. Check the enclosing manifest's
`reused_from_cache` and `reused_from` fields to distinguish that copied operation
from a new composition of individually reused and newly attempted pairs.

Use `--rerun` to request fresh numerical execution:

```powershell
java -jar jlinalg-all.jar study run --project study-project --offline --allow-partial
java -jar jlinalg-all.jar study run --project study-project --offline --allow-partial --rerun
```

The offline packaged example `genetic-pair-reuse-workflow.py` exercises source
completion and pair addition, correction/prior amendments, damaged outputs,
revoked approval, continued waiting and explicit reruns. Its independent Python
calculations check MR estimates, normal-tail probabilities, full-family
adjustments and colocalization posterior normalization. These synthetic checks
do not constitute scientific source appraisal or independent replication.

## Complete missing MR features from supplied contributions

Use `role: contribution` when an external MR analysis covers explicit molecular
features and JLinAlg should complete the remaining eligible features. A supplied
`role: primary` still replaces the whole MR stage. Contributions currently
support MR; they do not silently combine arbitrary partial analyses from other
stages. The contribution requests a native MR completion operation even in a
manual workflow. The planner keeps the import and completion as separate nodes.

```yaml
analysis:
  supplied_results:
    - id: completed-probe-MR
      kind: mendelian_randomization
      role: contribution
      reported_status: complete  # The declared feature subset is complete.
      files: {results: completed-MR.tsv}
      scope:
        feature_ids: [probe_A, probe_B]
        exposure: isoform ratio
        outcome: BMI
        ancestry: European
        genome_build: GRCh38
        tissue: blood
        assay: irQTL
      method:
        name: Externally completed primary MR
        parameters:
          primary_estimator: ivw-fixed
          reuse_rationale: Features were registered from association selection
      family:
        id: original-external-family
        coverage: selected_only
        tested_count: 500
        adjustment: BY
        adjusted_p_from_full_family: true
      provenance:
        analysis_id: my-stable-analysis-and-version
        results_sha256: YOUR_64_CHARACTER_RESULTS_SHA256
```

The table needs logical `feature_id`, `beta`, `se`, `p_value`, `method` and
`n_instruments`, plus source `adjusted_p` when preserving an original larger
family. Use existing `columns` mappings for other source headers. A completed
estimable row requires finite effects, positive SE, valid P, an integral positive
instrument count and the declared primary method. One instrument uses
`wald-ratio`; multiple instruments use the declared `ivw-fixed` or `ivw-random`.
A completed unestimable row uses `status: not_estimable`, unavailable beta/SE/P,
a nonnegative instrument count, the declared method and a public bounded
`reason`. Missing rows are not completed tests.
An entirely unestimable contribution may declare `reported_status: not_estimable`
when every covered row has that status and unavailable beta/SE/P. This records
completed source limitations, not unresolved work or negative causal effects.

Declared feature coverage must match the table exactly. The source result digest
must match its registration. Imports retain immutable original snapshots,
canonical tables, external attribution, source correction/family and method
declarations. Completion verifies producer identity and every sealed output,
including the raw snapshot and metadata. It requires a matching hypothesis
outcome and primary MR policy. Source scientific suitability, original QC,
instruments and causal assumptions are not independently reproduced.

Contributions own their declared coverage. Native reviewed pairs for those
features are skipped and recorded, and automatic MR acquisition excludes them.
That choice is explicit in the registered contribution; it does not certify that
unused native source declarations reproduce the external fit. Raw QTL files for
completed external features are unnecessary. Corrupt, unavailable or conflicting
contributions reserve their coverage and leave it unresolved rather than
triggering native fallback. Missing features can still proceed independently.
Custom MR commands cannot claim this native completion contract.

Multiple disjoint contributions can supply different features. Identical aliases
with the same stable `analysis_id`, primary result and source contract share one
test; they are not independent replication. Competing analyses or estimates for
the same feature remain pending rather than selecting the smaller P value.
Known primary features outside the current association-FDR or explicit MR cap
stay outside the joint MR family. Unknown feature identifiers require review.
MR results cannot create association eligibility.

Original external adjusted P values remain unchanged in their imported tables
and `supplied_sources` context. Native completion computes a separate correction
across the registered joint MR family using its original unadjusted test P
values. These adjustments can differ because the external and joint families
differ. A source-family non-discovery is never relabeled as a discovery in that
original family. Source choice must be registered independently of MR significance.
Completed unestimable tests remain in the joint denominator with unavailable
P; unresolved tests keep the joint adjustments provisional and suppress
`significant_primary`.

`mr-completion.yaml`, `mr-completion-features.tsv` and
`mr-skipped-supplied-pairs.tsv` record coverage, source checks, reuse and skipped
native work. The composed `mr-primary-results.tsv` and `genetic-evidence.yaml`
retain joint estimates/adjustments, original source-family context and explicit
external/native execution origin. Reused tests add a mandatory `supplied_mr`
caveat to the finding dossier. Reported evidence must distinguish imported
results, local completion, new correction and independent scientific validation.

Run `mr-completion-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` for
seventeen offline synthetic cases. They include partial/multiple/all supplied
sources, result-only primary input with no cohort dataset, native completion,
missing input, changed provenance, source conflicts, explicit caps and an
external-actor amendment. The verifier preserves source adjustments and
independently reconstructs the new joint correction and native estimates.

## MR coverage before gene annotation

MR eligibility uses the registered primary's full testing family and adjusted
P values strictly below `analysis.bioinformatics.fdr` (default 0.05). Gene
annotation is evaluated after eligibility. Missing annotations, conflicting
mapping rows, ambiguous gene identities and incomplete locations remain in
`mr-feature-dispositions.tsv`; none can silently reduce the MR denominator.
An optional `bioinformatics.mr.max_features` cap selects by primary P then
feature ID before annotation, independently of the contextual/literature cap.
Outside-budget findings remain explicit and are not called null.

`mr-feature-dispositions.tsv` preserves `annotation_status`,
`association_p_value`, `association_adjusted_p`, `association_adjustment`,
`association_testing_denominator` and `association_source_sha256`, alongside
the source/analysis disposition. Source association FDR selects candidates;
the separate primary-MR correction is not inherited from that selection.
Unresolved features remain in the declared MR family. Adjustment bounds for
completed tests stay provisional and cannot establish `significant_primary`
until that family's missing sources or protocols are resolved. A completed,
unestimable instrument analysis remains different from a missing prerequisite.

### Reviewed location of an exact molecular feature

A molecular feature can have a usable QTL source even when its local gene name
is missing or ambiguous. An approved reviewed MR pair can supply a sourced
location of that exact measured feature:

```yaml
analysis:
  bioinformatics:
    genetic_pairs:
      - id: reviewed-probe-mr
        feature_id: assay-probe-123
        approved: true
        rationale: Reviewed exact probe QTL and phenotype-matched outcome
        feature_location:
          feature_id: assay-probe-123
          genome_build: GRCh38
          chromosome: '1'
          start: 100000
          end: 100250
          coordinate_system: 1-based-closed
          source_id: my-reviewed-assay-coordinate-release
          citation: Public source identifier and version for this exact probe
          rationale: Source establishes the measured feature interval
        # Declare the existing exposure, outcome, LD, region, overlap and
        # instrument-validity fields of the reviewed-pair contract as well.
```

The location declaration requires all nine shown fields. Coordinates are
positive one-based closed positions in GRCh37 or GRCh38; end must not precede
start. The source ID, citation and rationale remain recorded evidence, not
automatic verification of the source's scientific suitability. The declaration
must name the exact primary feature. It cannot override a conflicting sealed
location, change the exposure/outcome/LD build, bypass pair approval, substitute
a gene eQTL for an exact transcript/probe QTL or waive the existing ancestry,
sample-overlap, regional containment and instrument-validity requirements.
No gene identity is inferred. The original annotation status remains visible.

`measured_feature_location` and `primary_feature_selection` enter the pair
result and method report. The study-wide inventory adds source-linked
`mr_feature_coverage_context` and `mr_feature_location_context` observations.
Using a reviewed location while gene annotation is unresolved adds a mandatory
`mr_feature_identity` caveat to the AI dossier. Coordinate availability does
not validate a gene mechanism, assay equivalence or causal interpretation.
The location fallback currently applies to reviewed MR; it does not enable
unannotated colocalization or fine-mapping.

Automatic cis-irQTL acquisition still requires an appropriate verified GRCh38
location. If no selected feature has one, the adapter records per-feature
prerequisites and does not open candidate exports or outcome/LD providers.
Other completed analyses remain retained. Supplying only an annotation-free
primary result does not establish that no eligible MR features exist.

Reviewed `genetic_pairs` are not subject to the separate 100-signal literature
cap. Large families still require appropriate source review, resources and
multiple-testing/dependence assumptions; permitting a list does not establish
genome-wide performance or scientific validity. Automatic source adapters and
the explicit feature budget retain their documented separate scope.

Run `mr-feature-coverage-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR`
for offline synthetic cases with complete/missing/ambiguous annotation,
reviewed and conflicting locations, missing sources, explicit caps, empty
eligibility, supplied primary meta-analysis, location amendments and 105
reviewed pairs. The independent verifier reconstructs primary Wald/IVW
estimates, their P values and full-family BY adjustments. Source inputs,
annotation dispositions and actual unsent aggregate AI payloads remain
auditable. These are software checks, not scientific source appraisal.

## Registered LD evidence for distinct principal findings

Several significant SNPs can describe the same correlated signal even when
their gene annotations or user-supplied story labels differ. Register local LD
evidence to add a separate redundancy constraint to the finding inventory.
The statistical results, source FDR, eligibility, full candidate inventory and
existing `story_group` labels remain unchanged. Both AI principal-finding
selection and human-supplied principal prose reject two slots sharing a
registered `redundancy_groups` component.

```yaml
project:
  finding_grouping:
    enabled: true
    minimum_r_squared: 0.8
    maximum_distance_bp: 1000000
    require_complete_coverage: true
    maximum_variants: 200000
    maximum_pairs: 2000000
    references:
      - id: ancestry_matched_ld
        genome_build: GRCh38
        population: EUR
        release: my-reviewed-reference-v1
        hypotheses: [gwas_trait]
        variants: references/ld-variants.tsv.gz
        pairs: references/ld-pairs.tsv.gz
        coverage: complete_window
        window_bp: 1000000
```

Each reference explicitly selects one or more hypotheses. A hypothesis can
select only one reference. Tables can be plain TSV or gzip-compressed TSV.
The variant catalog needs `variant_id`, `chromosome`, `position`, `ref`, `alt`;
an optional `genome_build` column must agree with the declaration. Coordinates
are positive, one-based reference positions with explicit distinct DNA alleles.
Accepted assemblies are GRCh37/hg19 and GRCh38/hg38. Pair tables need
`variant_a`, `variant_b`, `r_squared`, with identifiers resolvable in that
catalog. Use squared genotype correlation in [0,1], not signed correlation.
Conflicting aliases, duplicate undirected pairs with different values,
unknown variant endpoints and invalid values leave grouping unavailable.

All reference variants participate, including unmeasured or nonsignificant
mediators. Edges join variants on the same chromosome within the registered
distance and at or above the r-squared cutoff. Deterministic connected
components supply redundancy IDs. For example, A--B and B--C above the cutoff
put A and C in one component even if A--C is below the cutoff and B is absent
from the association results. A chain can span more than the edge window.
This is a conservative transitive redundancy constraint. It does not estimate
a causal signal, establish one mechanism, or prove independence between
different components. It is different from the P-ranked index-variant procedure
described in the [official PLINK clumping documentation](https://www.cog-genomics.org/plink/1.9/postproc#clump).

Coverage has three explicit dispositions:

- `complete_window`: the program checks for an observed finite LD value for
  every within-window pair in the supplied catalog, including zero and
  below-cutoff values. This checks the supplied catalog, not its completeness
  against the population's entire variant universe.
- `thresholded_window`: declare `window_bp`,
  `minimum_reported_r_squared` and, where needed, `reporting_comparison`.
  The source reporting cutoff must be below the grouping cutoff; equality
  requires `greater_than_or_equal`. A source exported at strictly greater
  than the same cutoff cannot establish coverage at the equality boundary.
  This disposition relies on reviewed source-declared export completeness;
  JLinAlg does not reproduce it from participant genotypes.
- `positive_edges_only`: observed links are usable descriptively, but absence
  of a pair establishes no zero-LD value or independence.

`NA`, `NaN`, `.` and empty LD values remain unavailable. Unknown within-window
pairs invalidate complete coverage even in a thresholded export. A source
window shorter than the analysis window also cannot establish coverage.
The strict default requires an adequate source and reference membership for
every eligible primary single-variant finding. Missing references, identity
conflicts or work bounds retain the complete inventory and supplied results
while holding impact assessment before any model request. Resolving the
registered input permits a normal resume. Nothing silently selects a favorable
subset of successful references or findings.

Set `require_complete_coverage: false` only in a reviewed protocol that accepts
descriptive partial reference components. Known positive edges still constrain
shared slots; unmatched findings and missing pairs remain unknown, and the
mandatory limitations accompany the dossier. Setting `enabled: false` or
registering no references leaves the existing story-label rules in effect;
it does not certify those labels as independent genetic signals.

Known source build, position, REF/ALT, effect-allele, species and recognized
ancestry conflicts are held for review. Unrecognized population labels do not
certify ancestry compatibility. An externally supplied primary uses its own
registered scope rather than unused raw input datasets. Identical canonical
variants and positive links across hypotheses or reference populations are
conservatively joined; that join is not independent replication. Different
assemblies remain distinct. References exclusively scoped to retired
hypotheses are not opened or added to cache inputs.

`finding-grouping/grouping.yaml`, `variant-dispositions.tsv`,
`pair-dispositions.tsv`, `finding-membership.tsv` and `components.tsv` record
the checks and assignments. Source tables are snapshotted and hashed with a
reference receipt. Public aggregate dossiers cite those receipts and carry
the caveat; local paths are removed from model payloads. Editing or supplying
an LD file invalidates the inventory and dependent editorial work while an
unchanged supplied meta-analysis remains cached. Automatic ancestry-matched
reference download, native genotype-to-LD extraction and fine-mapping are
separate capabilities; this operation accepts explicitly registered local
LD tables.

### Retaining aggregate metadata in supplied statistical results

Typed statistical imports retain approved aggregate allele, coordinate,
sample-size, count and uncertainty fields through the same metadata validator
as native result tables. This includes supplied meta-analysis/discovery
meta-analysis, MR, rare-set tests and score evaluation/related-phenotype tables.
It allows the LD identity check to inspect supplied REF/ALT. Arbitrary extra
columns are excluded. Numeric consistency checks still apply, and descriptive
labels require explicit mappings.

Ambiguous aliases require a declared selection. For example, if the source
contains both `N` and `OBS_CT`, select one without renaming away the source:

```yaml
columns:
  result_metadata:
    n: OBS_CT
```

For a role-specific mapping, put the same object under
`columns.results.result_metadata`. Global and role-specific selections must
agree when both name the same metadata field. Original source snapshots,
P values and source family adjustments remain retained.

Run `finding-grouping-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` from
the examples directory for fifteen synthetic offline cases. The independent
verifier reconstructs components without using P values, checks shared
identities across hypotheses, source-FDR preservation, input amendment/cache
lineage and the actual unsent aggregate payloads. These checks do not establish
scientific calibration or constitute an AI appraisal of biological findings.

## Registered adjusted subgroup interactions

`subgroup_interactions` tests whether an association effect differs between two
prespecified groups. It is separate from the descriptive distribution plots and
Welch comparisons in `participant_qc`. A difference between subgroup P values
does not establish an interaction; compare effects in an appropriate model
([Altman and Bland](https://doi.org/10.1136/bmj.326.7382.219)).

Register the contrast before examining its test results. For an independently
sampled bulk matrix with a quantitative outcome, a hypothesis can include:

```yaml
outcome: BMI
covariates: [age, sex, ancestry_pc1]
inputs: {discovery: [cohort_a]}
analysis:
  mode: manual
  goals: [association, subgroup_interactions, impact_assessment]
  association: {model: ols}
  subgroup_interactions:
    independent_rows: true
    method: ols-hc3
    groups:
      - id: sex
        column: sex
        levels: [F, M]
        rationale: Prespecified effect modification question for this study.
    nuisance_interactions: all_two_way
    adjustment: BY
    alpha: 0.05
    minimum_group_samples: 10
    minimum_group_mac: 20
```

The two `levels` are ordered: reference first, comparison second. A numeric
contrast instead declares `cutoff` and `comparison` (`greater_than` by default,
or `greater_than_or_equal`, `less_than`, `less_than_or_equal`). It cannot also
declare categorical levels. Report its source variable and a scientific
`rationale`; do not search thresholds for a favorable P value. Register
`derived_from: [BMI]` when a grouping variable is constructed from BMI. A group
defined by the outcome, or declared to derive from it, produces neither an
interaction P value nor a Welch comparison of that outcome. Its distributions
and unavailable-test dispositions remain. Undeclared derivations cannot be
reliably inferred from an arbitrary column name; their disclosure needs review.

For feature X, group indicator G and encoded covariates C, the default full
model contains intercept, G, C, C×G, X, C×X and X×G. The tested parameter is the
X×G coefficient. Including both covariate interaction sets guards against the
specific interaction confounding described by
[Keller](https://pubmed.ncbi.nlm.nih.gov/24135711/); it does not establish that
all confounding is controlled. No three-way interaction is fitted. Reference
and comparison slopes apply at the complete test sample's centered covariates,
in original feature units. Logical column mappings and hypothesis covariates
are inherited. A categorical grouping variable is removed from the nuisance
list because its main term is already fitted; the continuous variable underlying
a threshold contrast remains when registered as a covariate. Non-numeric
covariates receive treatment encoding; `categorical_covariates` can explicitly
encode numeric category codes. The recorded terms identify reference levels.
`group_only` omits C×X and `none` omits both interaction sets; either requires an
explicit policy `rationale` and changes the scientific model.

The current native inference routes are:

| Method | Test and uncertainty |
| --- | --- |
| `ols-hc3` | Strict full-rank OLS, HC3 covariance and residual-df t approximation. HC3 does not solve clustering or guarantee finite-sample calibration. |
| `ols-classical` | Strict full-rank OLS with homoskedastic Gaussian residual-df t inference. |
| `logit-lrt` | Independent Bernoulli logit full versus nested constrained maximum-likelihood fits, with the same observations, offset and nuisance terms; asymptotic chi-square(1) tail. |
| `firth-lrt` | Full-design Jeffreys-penalized fit versus the X×G=0 constrained fit retaining that same full-design penalty; asymptotic chi-square(1) tail. |
| `inherit` | OLS selects HC3; explicit binomial/logit GLM selects ordinary logit or an explicitly declared Firth route. Missing or unsupported models remain held. |

Binary models retain the registered `case_value`, `control_value` and fixed
`offset_column`. Their reported ordinary-Fisher SE/Wald intervals are separate
approximations, not profile-likelihood intervals. Firth is based on
[bias reduction](https://doi.org/10.1093/biomet/80.1.27), not a universal exact
small-sample calibration guarantee. Ordinary separation, failed optimization
and insufficient numerical budgets remain unresolved; no automatic Firth
fallback selects a different method. A one-class complete outcome is unavailable.
Effects, uncertainties or interval bounds that cannot be represented in the
original feature units also remain unresolved; a finite standardized test tail
cannot rescue an infinite coefficient or invalid interval.

Native fitting requires the explicit `independent_rows: true` assertion and a
verified completed participant-QC producer matching current matrix, phenotype
and sample-file bytes. Declared related/repeated participants, pedigree/GRM,
unsupported mixed/survival/exact-conditional models and single-cell/spatial unit
designs need a suitable reviewed adapter or supplied result. The assertion is
not evidence that dependence was actually ruled out. A custom HWE sample panel
also requires an interaction-compatible reviewed QC export. Broader sex/PAR,
ploidy, kinship and source-specific calibration remain outside this adapter.

Every selected dataset×group×feature belongs to the registered scope; an
optional explicit `features` list narrows it prospectively. Primary association
P values and histogram differences never select the interaction family. Complete
observed feature samples are used, with sample support checked in both groups.
Genotype tests inherit single-marker QC and recompute within-group MAC; by
default each group needs MAC >=20, even when pooled MAC passes. Failed QC and
insufficient support retain explicit exclusions. Structurally unestimable tests
that passed QC remain in the denominator. Pending fits, absent registered
features, stale inputs or an unknown universe withhold **all** native interaction
adjusted P values and candidate flags; observed raw fits remain available.

The correction family is joint across this hypothesis's applicable datasets,
contrasts and features. The default is BY; BH, Bonferroni and explicit nominal
`NONE` are available. Separate hypotheses are separate families; a study-wide
cross-phenotype correction is not inferred. Multiple cohorts using overlapping
participants are not independent replications merely because their interaction
fits share a correction family. Default bounds are 1,000,000 attempted fits,
250,000,000 conservative work units, 100 parameters, 300 iterations and tolerance
1e-8. An exhausted bound holds the family instead of adjusting a truncated subset.

`subgroup-interactions.tsv` retains counts, group MAC where relevant, the signed
comparison-minus-reference difference and CI, P/logP, statistic, df, both group
slopes/SE, inference, test status, correction denominator and mandatory caveats.
`subgroup-interactions.yaml` records policy, source hashes, participant-QC proof,
design terms and prerequisites. `participant-membership.tsv` contains local
participant identifiers and exclusions. Only aggregate canonical rows enter
finding dossiers. Eligible interaction evidence can reach impact review when
the primary association is nonsignificant; its `significant` label and primary
artifacts remain unchanged. Incomplete and excluded rows retain context and
limitations; they do not become interaction discoveries.

Users with completed interactions can supply them with no participant matrix:

```yaml
analysis:
  mode: manual
  goals: [subgroup_interactions, impact_assessment]
  supplied_results:
    - id: completed_interactions
      kind: subgroup_interactions
      reported_status: complete
      files: {results: completed-interactions.tsv}
      scope: {phenotype: BMI}
      method: {name: Externally completed adjusted interaction model}
      family:
        id: original_interaction_family
        coverage: complete
        tested_count: 25000
        adjustment: BY
        adjusted_p_from_full_family: true
```

Required columns are `feature_id`, `dataset_id`, `group_id`, `reference_level`,
`comparison_level`, `beta`, `se`, `p_value`; retain `adjusted_p` from the complete
source family when using that declaration. Each dataset/group/feature identity
is unique, and a dataset/group's contrast orientation must agree across features.
Optional canonical counts, slopes, inference, scope and caveat fields survive
validation; arbitrary participant notes do not enter canonical dossiers.
The source family and adjustments remain external evidence, not reproduced
fits. A partial result can finish import validation while remaining context-only
in `supplied-results-limited.tsv`; this state is distinct from completed
scientific scope. Whole supplied interaction families replace native interaction
work. Automatic composition of partial per-feature interaction contributions is
not implemented. An explicit native budget amendment can complete a held family
and recompute its full correction; unchanged completed operations can resume
through the normal verified whole-operation cache.

The offline packaged acceptance script,
`examples/study-pipeline/subgroup-interactions-workflow.py`,
pins its executable, verifier, numerical fixture and software versions before
execution. It compares raw unscaled statsmodels/SciPy designs against all four
native inference routes, using relative/log-P checks for very small tails. It
also checks multiple-dataset correction, group MAC, circular contrasts, held
prerequisites, external complete/partial families, immutable base inputs and
external-actor budget amendment/resume. Its aggregate impact payloads are
retained unsent. These synthetic checks establish software behavior; actual
study independence, model adequacy, discovery impact and manuscript appraisal
remain scientific review tasks.

## Registered genotype sample QC and projected PCs

`analysis.genotype_sample_qc` connects a declared common-autosomal hard-call
panel to `participant_qc`, sample eligibility, optional genotype PC covariates,
native association and aggregate finding evidence. It extends the default
sample call-rate checks. The profile is disabled unless configured; enabling it
requires a declared QC-panel genome build and scientific rationale. It is a
bounded native implementation for the documented inputs, not complete assay or
genome-wide production QC.

For example, an imputed association matrix can use a separate upstream-QCed
observed hard-call panel, with exact selected sample-ID alignment:

```yaml
datasets:
  - id: cohort_a
    type: gwas
    stage: processed_matrix
    files:
      matrix: cohort.bgen
      sample: cohort.sample
      phenotype: phenotype.tsv
      qc_genotypes: observed-common-snps.vcf.gz
    metadata:
      qc_genome_build: GRCh38
    columns: {sample_id: id}
hypotheses:
  - id: H
    description: Prespecified quantitative association and sample QC.
    inputs: {discovery: [cohort_a]}
    outcome: BMI
    covariates: [age]
    analysis:
      mode: manual
      goals: [association, impact_assessment]
      association: {model: ols, genotype_kind: imputed, minimum_info: 0.8}
      genotype_sample_qc:
        genome_build: GRCh38
        rationale: Prespecified common-autosomal QC and population-structure model.
        relatedness_action: exclude
        maximum_excluded_fraction: 0.10
        pca_components: 2
        include_pcs: true
```

This example chooses a quantitative independent-error model and an explicit
related-sample exclusion rule; it is not a model recommendation for an arbitrary
BMI study. A related cohort may require an appropriate mixed model instead.
The original phenotype/genotype files remain unchanged. Resolution appends
`GPC1`, `GPC2` to the effective covariates, while the base contract retains its
source covariate list. Generated columns cannot overwrite existing columns or
be remapped to a different physical column. They apply to genotype participant
matrices; mixed participant modalities require separate hypotheses.

The profile also runs when explicitly configured with manual goals that omit
association, so users can complete missing QC without refitting existing
analyses. A summary-result input cannot reconstruct participant QC. Without
`qc_genotypes`, the main matrix supplies the panel. The current panel reader
uses the supported VCF/BCF hard-call representation, or an explicitly declared
unspecified hard-call contract with compatible locus metadata. Imputed dosages,
even integral values, are not silently rounded into observed calls. A separate
`qc_samples` file supports panel formats requiring external sample IDs. A
missing selected sample holds the scope; extra panel samples do not replace
the registered analysis population. QC and association panels can have distinct
build declarations because they need not share variants; QC coordinates and
registered exclusions must match the declared QC-panel build.

The executed flow is:

1. Intersect phenotype and matrix IDs, check unique IDs and source complete cases,
   then apply the registered sample call-rate rule to the observed hard-call
   source. Its denominator is diploid biallelic autosomal SNPs after registered
   region exclusions and before MAF/marker-call-rate/LD filters. Complete imputed
   dosages cannot conceal poor observed call rates; structural sex-chromosome
   absence does not enter this denominator. Generated PCs need not exist yet.
   An already impossible native sample/memory/PCA bound stops before the full
   call-rate scan. An interrupted scan cannot exclude participants.
2. Stream the complete permitted QC source. Restrict panel eligibility to human
   autosomes, unsplit biallelic SNPs, observed diploid hard calls and registered
   marker call-rate/MAF and region rules. Every examined marker retains a
   disposition. This does not remove that marker from association or rare sets.
3. Choose the smallest SHA-256 priorities from the complete eligible universe,
   using the registered seed and locus/allele/ID identity. This prospectively
   defined marker sample is independent of source order and phenotype P values;
   it is not the first rows of an interrupted scan. Sort the sampled markers by
   chromosome/position/ID and greedily retain them unless a previously retained
   marker within the physical window exceeds the declared pairwise-complete r².
4. Count shared observed hard calls for every sample pair. Retain heterozygote,
   opposite-homozygote and identical-call counts, KING equation 9 as context,
   and the equation 11 between-family estimator for the decision rule. Low shared
   support or zero/insufficient heterozygosity leaves a pair unavailable, not
   unrelated. Negative estimates remain visible.
5. Apply the registered relatedness action. Report per-sample panel call rate,
   heterozygosity and robust median/MAD warning score; heterozygosity warnings
   never automatically delete participants.
6. When requested, fit genotype PCs on the deterministic unrelated basis and
   project all current candidate samples with those basis frequencies/weights.
   Append generated PC columns only with `include_pcs: true`. Write the approved
   phenotype table only after all registered prerequisites pass.

The estimator uses the between-family rule for all pairs, as documented for
[PLINK 2 KING](https://www.cog-genomics.org/plink/2.0/distance), with equations
from [Manichaikul et al.](https://doi.org/10.1093/bioinformatics/btq559).
Relatedness is flagged strictly above the registered cutoff. A negative screen
does not prove unrelatedness or override a declared pedigree/related design.
The default cutoff `2^(-3.5)` screens approximately second-degree or closer
relationships; numerical thresholds and support floors require study-specific
appraisal. The minimum marker count is an eligibility floor, not a power or
calibration guarantee.

| Relatedness action | Executed behavior |
| --- | --- |
| `review` (default) | Hold when an estimable pair exceeds the cutoff; keep local diagnostics and request a design decision. |
| `exclude` | Greedily keep samples in descending panel call-rate order, breaking ties by ID, provided they have no above-cutoff edge to an already kept sample. This deterministic independent set is not guaranteed maximum. Hold if proposed exclusions exceed the registered fraction. |
| `model` | Keep all candidate samples only with an explicit supported quantitative GRM LMM route and `files.grm`, or the separately supported rare-score REML route. Downstream consumers still validate covariance inputs and fits. No binary mixed-model calibration is inferred. |

Known pedigree/relatedness requires an appropriate declared model even if a
panel screen or proposed pruning appears favorable. Repeated observations need
a separately reviewed observation/covariance model. Duplicate-like genotypes
cannot distinguish technical duplicates from monozygotic twins; review identity
and pedigree evidence before interpretation.

PCA uses a genotype GRM standardized by `sqrt(2 p (1-p))`, where frequencies are
estimated on the unrelated basis. Missing calls use that basis mean. Unit-norm
basis eigenvectors define the scores; stored weights project all candidate
samples, including held-out relatives, into the same basis. The mean, scale,
weights, eigenvalues and variance fractions are retained. Numerically unavailable
rank, invalid eigen residuals or a requested boundary splitting a tied eigenspace
hold the operation. There is no automatic reduction in the number of covariates.
The PCs can describe population, batch or family structure; they do not assign
ancestry labels. See [Price et al.](https://doi.org/10.1038/ng1847) and the
[PLINK PCA cautions](https://www.cog-genomics.org/plink/2.0/strat).

Precomputed PCs can be supplied as ordinary phenotype-table covariates. Keep
their provenance, include their column names in `hypotheses[].covariates`, and
set `pca_components: 0`, `include_pcs: false` to complete missing panel QC without
regenerating them. The method record explicitly reports PCA as not requested.
With positive `pca_components` and `include_pcs: false`, native PCs are generated
for diagnostics but do not silently change the regression. This is reuse of
declared covariates; it does not independently validate their original PCA.

Key defaults and limits are:

| Setting | Default and interpretation |
| --- | --- |
| Panel eligibility | MAF >=0.05; marker call rate >=0.98; 1,000 retained panel markers required. Caller/site QC is not inferred from these checks. |
| Prospective marker sample | `panel_markers: 10000`, `seed: 17`; hash sampling precedes physical-order LD pruning. |
| LD | 500,000-bp window, r² cutoff 0.2 and at least 10 shared observed samples per required comparison. This is a native greedy strategy, not a claim of exact PLINK pruning parity. |
| Pair support | At least 1,000 shared markers and 100 heterozygotes in each pair member, evaluated on their shared observed markers. |
| Selection and diagnostics | `relatedness_action: review`, exclusion fraction <=0.10 when exclusion is explicitly requested, heterozygosity MAD score warning >4. |
| PCs | 10 components, `include_pcs: false`, prefix `GPC`; choose the scientific covariate count before inspecting association results. |
| Native bounds | 2,000 samples; 25,000,000 panel genotype cells; 2,000,000 examined source variants; 1,000,000,000 source-read cells and pair-word work units; 250,000,000 PCA work units. |

The native pair table has a further two-million-row cap. Pair work scales with
sample pairs times packed 64-marker words. PCA work charges the actual Gram,
eigensolve-dimension and projection terms; limits are computational proxies,
not runtime promises. The source-cell bound also caps LD comparison work per
phase. The initial observed-call pass has its own source-read bound, except for
already impossible native bounds detected before it. An imputed main source
also needs finite imputation-quality metadata when association is requested.
The separate availability probe stops at its first finite value; it does not
certify every marker. Association consumers apply per-marker quality filters.
QC-only work does not require irrelevant main-matrix imputation metadata.
Zero eligible observed sites leave call rate unavailable; an entirely excluded
population retains all call-rate records and exclusions without approved rows.
Exhaustion holds the scope instead of
truncating samples, assuming missing pairs are unrelated or changing the marker
sample after seeing results. Use a suitable external QC/PCA workflow for larger
cohorts and the [scoped import contract](#reuse-external-sample-qc-and-pca-and-complete-missing-work)
to continue from its completed work. Already supplied downstream analyses retain their original QC
provenance rather than acquiring retrospective native QC credit.

`excluded_regions` optionally registers chromosome, 1-based closed start/end
and rationale for long-range LD exclusions in the stated QC build. No universal
MHC/inversion map is inferred. Biological sex/PAR/ploidy interpretation,
contamination, sample-swap confirmation, Mendelian errors, caller/site filters,
ancestry-reference classification and study-specific calibration remain separate
requirements. This profile must not be described as complete genotype QC.

Artifacts include `genotype-qc/initial-call-rates.tsv`, `marker-inventory.tsv`, `panel.tsv`, `pairs.tsv`,
`samples.tsv`, optional `pcs.tsv`, `pc-weights.tsv`, and a PC1/PC2 PNG/PDF. These
are local participant/genotype records. `genotype-qc-summary.yaml` contains the
bounded aggregate scope, counts, policy, method, source hashes and caveats.
`analysis-phenotype.tsv` is the approved model population, with generated PCs
only when requested. Held runs write `diagnostic-phenotype.tsv` instead and
cannot feed those rows into association. Requested groups based on unavailable
generated PCs retain a disposition while independent diagnostics continue.

Native association and rare-set consumers receive the selected participants
and recompute their own eligibility, including single-marker MAC >=20. Original
genotypes and rare variants remain intact. Finding dossiers and impact payloads
receive aggregate QC context with source IDs/caveats, never pair tables,
participant identifiers or source paths. This context concerns the current QC
dataset; independently supplied results are not retrospectively validated.
Verified whole-operation caching can reuse completed QC. A held numerical work
budget can be explicitly amended and retried while preserving earlier attempts
and base/raw input hashes.

The runnable offline acceptance is
`examples/study-pipeline/genotype-sample-qc-workflow.py`. It pins the executable,
verifier and synthetic raw/reference fixtures before analysis. Independent
NumPy pair counts/eigenanalysis and SciPy association tails check the full
panel, every pair, projection weights and downstream OLS after participant/PC
changes. Cases include complete and held scopes, reordered inputs, imputed data
with an observed-call panel, poor observed calls behind complete imputation,
structural sex-chromosome absence, all-participant call-rate failure, missing
imputation quality, partial sample coverage, supplied PC covariates,
resource amendments/resume, privacy and actual unsent impact payloads. These
are software/mathematical checks, not scientific validation of real cohorts.

## Reuse external sample QC and PCA, and complete missing work

The source build accepts completed genotype sample-QC and PCA records through
`analysis.genotype_sample_qc`. It validates the source artifacts and their
scope, retains external attribution and runs only the missing work selected
by the study contract. This is a normalized source-report interface, not an
automatic parser for arbitrary tool exports or independent scientific
certification of their results.

| Available work | Configuration and behavior |
| --- | --- |
| Missing QC and PCA | Default `source: native`, `pca_source: native`. Run the registered bounded native route. |
| Completed QC and PCA | `source: supplied`, `pca_source: supplied`. Reuse source decisions and scores; do not run native call-rate selection, KING or PCA. |
| Completed QC; missing PCA | `source: supplied`, `pca_source: native`. Reuse accepted QC decisions and the declared independent basis; construct the required native PCA panel and fit/project only PCA. Native PCA bounds still apply. |
| Completed QC; no PCA requested | `source: supplied`, `pca_components: 0`, `include_pcs: false`. Reuse QC without requiring or regenerating PCA. |
| Completed PCs as phenotype covariates; missing QC | Keep the named PC columns in the phenotype table and covariate list. Use native QC with `pca_components: 0`, `include_pcs: false`. Their original provenance remains external. |

For example, two cohorts can have different completed work:

```yaml
analysis:
  genotype_sample_qc:
    genome_build: GRCh38
    rationale: Prespecified reuse of cohort QC and completion of missing work.
    relatedness_action: exclude
    pca_components: 2
    include_pcs: true
    source: native
    dataset_sources:
      cohort_a: {source: supplied, pca_source: supplied}
      cohort_b: {source: supplied, pca_source: native}
```

The source selectors default to `native`. Choosing `source: supplied` defaults
its PCA source to `supplied`; set `pca_source: native` explicitly to complete or
deliberately replace PCA. `dataset_sources` overrides only selected genotype
datasets; unknown cohort IDs and incompatible selectors are errors. An
unlisted cohort keeps the global choice. The native-PC covariate count and
column prefix remain registered across the hypothesis. Different QC sources
do not establish cohort independence or justify pooling.

Each external source declares these dataset file roles:

```yaml
files:
  matrix: cohort.bgen
  sample: cohort.sample
  phenotype: phenotype.tsv
  sample_qc_report: completed-qc.yaml
  sample_qc_samples: completed-sample-decisions.tsv
  sample_qc_pcs: completed-pcs.tsv
```

The PCA file is required only when supplied PCA components are requested. A
separate observed `qc_genotypes` panel is needed for missing native PCA when
the main matrix cannot provide observed hard calls. Full reuse does not
require retaining a raw hard-call panel locally. Native imputed association
still needs actual main-matrix imputation-quality annotations; external QC
cannot replace per-marker INFO/R-squared fields. QC-only work does not inspect
irrelevant association-quality metadata.

The [external report schema](../schemas/genotype-sample-qc-v1.schema.json)
defines the normalized version-1 packet. It records:

- The dataset, QC genome build, analysis ID, source status and exact current
  association-matrix SHA-256. Binding identifies the analysis matrix and source
  population; it does not assert that upstream QC used the same marker file.
- Source software/versions, an observed-autosomal hard-call call-rate scope,
  threshold and source population count. The source threshold cannot be weaker
  than the registered analysis sample-call-rate requirement.
- The KING between-family estimator, cutoff, action, source pair/support counts
  and explicit independent-basis/retained-population assertions. The current
  adapter requires the registered cutoff/action and support floors to agree.
  These declarations remain source-reported; JLinAlg does not recompute the
  source pairs or prove that its calibration and basis assertions are correct.
  Native hash-sampling, MAF, marker-call-rate and LD defaults are not attributed
  to an imported source. Its own declared filter/method parameters remain
  available for scientific compatibility review.
- Original PCA software, ordered component columns, basis description and count
  when supplied PCA is requested. Source sample/pair counts and PCA basis counts
  retain their original population, separate from the current analyzed sample.
  The default `pca.basis_scope: matrix_population` keeps that basis inside the
  source matrix universe. An explicit `external_reference` basis may be larger;
  it requires a source-declared `reference_id` and `reference_sha256`. The
  reference model is not automatically retrieved, reproduced or independently
  verified. The number of reported components must be below the basis count.
- Lowercase SHA-256 values for the decision and configured score files, plus
  source provenance, parameters and limitations. No source command is executed.

The sample-decision table must cover **every matrix sample exactly once**,
including samples that currently lack a phenotype. Its columns are:

```text
sample_id  status  decision  reason  observed_call_rate  pca_basis
```

This is a tab-separated header, not space-separated input. `status` must be
`complete` for accepted decisions. `decision` is `keep` or `exclude`; excluded
rows use `call_rate`, `relatedness` or `other_qc` as their reason. Kept rows use
an empty reason or `retained`. Observed call rates must be finite in [0,1], and
retained samples must pass the declared source threshold. Relatedness-pruning
losses remain subject to the registered exclusion fraction. A `model` or
`review` action cannot claim relatedness-pruning exclusions. Keeping relatives
still requires an appropriate explicit quantitative GRM model; known pedigree
or repeated observations retain their separate design requirements.

`pca_basis` contains explicit `true`/`false` flags and is required for native
missing-PCA completion. That basis must exclude source-QC-excluded samples.
The independent-basis assertion and membership list are not prerequisites for
QC-only reuse or already completed external PCA. If they are missing when
native PCA is requested, accepted QC exclusions remain intact while PCA waits.
An absent list is unavailable information, not a fabricated zero-person basis.
The native fit uses its intersection with current phenotype/covariate
eligibility, retaining every source-QC-kept candidate for projection even if
not a basis member. It does not run KING again or remove another sample merely
to make a smaller native PCA problem fit.

The supplied score table uses `sample_id` and the component names declared in
`pca.columns`, for example `PC1`, `PC2`. Requested columns must be finite, IDs
unique and all source-QC-retained samples covered. Extra projected scores may
belong to source-QC-excluded members of the same matrix population. JLinAlg
normalizes requested components to the registered output names (`GPC1`,
`GPC2` by default), joins by ID and never overwrites existing phenotype columns.
`include_pcs: false` retains requested imported scores as diagnostics without
silently adding covariates. Changing the phenotype subset does not refit,
recenter or redefine the source PCs or their reported basis.

An incomplete QC universe, mismatched matrix/sample scope, hash mismatch,
unresolved pair support, missing scores, invalid values or exceeded import
budget holds approval. There is no silent intersection, automatic replacement
fit or promotion of a partial source report to a complete scientific analysis.
If only PCA is pending, accepted QC exclusions remain in the local diagnostics
while association waits. Explicitly choosing native missing-PCA completion can
finish that stage without re-executing source QC. Independent branches can
continue under their existing prerequisite rules.

Full imports use linear bounds distinct from native sample/pair/PCA limits:
`maximum_import_samples: 1000000`, `maximum_import_cells: 50000000` and
`maximum_import_bytes: 1073741824` per table/file by default. The report itself
has a two-megabyte metadata bound. Normalized UTF-8 TSV records allow at most
256 columns and 65,536 characters per line; embedded tabs/newlines or quoting
are not part of this format. Work limits are computational bounds, not runtime
or memory-use promises. Native missing-PCA completion keeps the native panel,
LD, memory and eigensolve/projection limits.

`genotype-qc/external/` retains immutable copies of the report and configured
tables, with local parse/validation diagnostics when needed. These contain
participant data. External method details and source limitations in local
source/summary artifacts may also contain private text; they are **not
automatically safe for remote disclosure**. Finding dossiers and impact
payloads receive only allowlisted aggregate counts, origin flags, source
hashes/IDs and fixed caveats. Private source notes, sample/basis lists and
scores are excluded. Automatic Methods records likewise retain only aggregate
facts and a source-hash/review pointer; they do not copy private source notes
into manuscript parameters. Source-specific limitations require local scientific
review before impact/manuscript claims; import validation does not replace it.
No imported QC record retrospectively validates a supplied meta-analysis,
MR result or its historical analysis population.

Completed operations use the normal verified cache. A source repair is a new
immutable source edition and explicit study amendment, preserving old files
and attempts; a changed source hash invalidates reuse. An import work budget
can likewise be explicitly amended. The offline acceptance script is:

```powershell
python -B examples/study-pipeline/external-genotype-qc-workflow.py run build/external-qc-example --jar build/cli/jlinalg-0.3.7.jar
```

It pins its executable, verifier, source generator and numerical fixtures before
execution. Synthetic source reports, independent NumPy PCA, downstream SciPy
OLS, scoped failure controls, mixed cohort sources, immutable source editions,
budget amendments and actual unsent impact payloads test software behavior.
They do not establish installed PLINK execution or scientific cohort QC.

## Cross-mask rare-set and gene omnibus

`rare_set_omnibus` forms a new set/gene hypothesis from registered component tests,
retaining original mask/test results and adjustments. The default supplemental
stage combines one primary test per mask using Bonferroni minimum-P, then applies
a separate BY family in each primary/discovery/cohort scope. `component_tests:
all` includes every registered test; no best observed test is selected. Automatic mode proposes the
stage for rare-set sources; manual mode runs an explicit omnibus goal or a
requested primary omnibus. Unrequested supplemental work does not hold an
otherwise completed manual rare-only analysis. An explicit
`tests` list requires `component_tests: all`; primary identities come from the
sealed source. `enabled: false` makes the optional stage not applicable.

```yaml
analysis:
  mode: manual
  goals: [rare_variant_analysis, rare_set_omnibus, gene_ontology, literature_search]
  rare_omnibus:
    selection: primary
    unit: gene
    gene_map: reviewed-set-to-gene.tsv
    masks: [lof-001, damaging-missense-001]
    component_tests: primary
    method: bonferroni_min
    adjustment: BY
    alpha: 0.05
```

Default `unit: set` keeps source IDs. `unit: gene` needs reviewed tab-delimited
`feature_id, gene_id, gene_symbol` mappings or sealed source annotation; a reviewed
symbol can identify a gene when a stable ID is absent. Several sets may map to
one gene: all their components enter that gene's omnibus before correction across
genes. Ambiguous mappings fail; unmapped sets remain visible and hold correction.

For K components, Bonferroni minimum-P is `min(1, K * min(P))`. Optional `method:
acat` uses equal-weight Cauchy combination with finite natural-log tails. This
approximation requires valid component P values. P=1 triggers the registered
Bonferroni fallback, as in [SAIGE-GENE+](https://pmc.ncbi.nlm.nih.gov/articles/PMC9534766/).
[Liu et al.](https://pmc.ncbi.nlm.nih.gov/articles/PMC6407498/) describe ACAT; this
stage does not reproduce SAIGE-GENE+'s mixed-model fitting.

Each source set needs the registered mask/test grid in every scope. Missing or
failed components remain unresolved, never P=1. A reviewed outcome-independent
`component_eligibility` file uses `scope, feature_id, mask_id, test, reason`;
scope `*` applies across scopes. Allowed reasons are `no_eligible_variants`,
`not_in_predeclared_mask` and `not_callable_in_scope`. It cannot discard a valid
test or failed model. Missing registered files hold selection. Entirely
ineligible units have no invented P value.

Default `selection: supplemental` preserves mask-family selection. Explicit
`selection: primary` makes the omnibus own rare downstream/finding eligibility;
an unresolved omnibus cannot fall back to favorable masks. GO/Reactome uses the
complete mapped tested-unit background and its own correction; ordinary
over-representation does not account for gene length or variant opportunity.
Literature/AI records retain source families and caveats. The unsigned omnibus
has no beta, SE, direction, Q or I-squared, and provides no MR instruments.

Discovery selection freezes significant units before validation.
`replication.adjustment_scope: all_results` corrects the full validation unit
family; `discovery_subset` corrects frozen discovery units in that cohort.
Unresolved scopes have no replication decision; a separately complete primary
family remains usable. Direction is unavailable; statistical repeat does not
certify cohort independence.

Reuse completed results through `supplied_results` with `kind: rare_set_omnibus`,
`method.unit: gene` or `set`, and a declared source `family`. Minimum columns are
`feature_id, p_value`, plus original `adjusted_p` for retained source adjustment.
Scope defaults to `primary`; `discovery` and `cohort:ID` each need a family under
`method.scope_families`. Natural-log `log_p_value`/`log_adjusted_p` retain underflow
tails. Supplied component counts must agree; absent counts are not invented.
Selected-only rows need original full-family adjusted values and a denominator;
they cannot define a complete enrichment background or a new omnibus. A larger
source denominator than the exported unit rows is reported as
`incomplete_export`, even when the input says complete; source q values remain
retained and native enrichment stays held. With no
supplied mask source, a supplied primary omnibus defaults to primary rare
selection and suppresses inferred upstream fitting; explicit upstream goals run.

Outputs include `rare-omnibus-results.tsv`, `omnibus-components.tsv`, source
snapshots, per-scope receipts and optional `rare-omnibus-replication.tsv`.
Mapping/eligibility file hashes participate in cache identity; deliver missing
files and rerun to reuse completed source work. Bounds cover rows, units,
components and grid work; no favorable prefix is selected.
The offline packaged acceptance workflow `examples/study-pipeline/rare-omnibus-workflow.py`
independently checks numerical results, native/supplied reuse, coverage,
selection and resumption. Synthetic checks do not certify real-data null
calibration, biological validity or full pipeline conformance.

## Reuse supplied literature and complete missing searches

The source build accepts typed public bibliographies and external search records
under `analysis.supplied_results`, with `kind: literature_search`. References now
enter the indexed citation catalog, manuscript citations and bounded advisor
previews. Raw files are snapshotted and hashed. Importing bibliographic metadata
does not authenticate the source or reproduce the search, and it supplies no
evidence that this study performed a published experiment.

Supplied search counts use exact numeric source tokens. `hit_count` must be a
nonnegative integer within the signed 64-bit range. Decimal integer notation is
accepted without rounding through double precision; fractional, underflowed,
nonfinite, quoted-count and out-of-range tokens are rejected. A fractional
publication year cannot round into a valid four-digit year. Integer-valued
numeric years and existing four-digit string years remain supported. Original
source bytes, hashes and the bounded safe-reader restrictions are retained.

Use `role: primary` and `reported_status: complete` when the supplied literature
stage should be reused in full. This can be a bibliography without search
records; its references become usable while search coverage remains explicitly
unestablished. JLinAlg honors that whole-stage choice and does not invent searches
to explain it. To finish partly completed searches, use `role: contribution` and
register the exact input hash:

```yaml
analysis:
  mode: manual
  goals: [literature_search]
  bioinformatics:
    literature_terms: [RNA splicing]
  supplied_results:
    - id: reviewed-searches
      kind: literature_search
      role: contribution
      reported_status: partial
      files: {results: literature.yaml}
      scope: {phenotype: body mass index}
      method: {name: External literature search}
      provenance:
        results_sha256: REPLACE_WITH_SHA256_OF_LITERATURE_YAML
```

A primary import marked partial is rejected: it cannot silently suppress the
missing-work branch. Literature contributions schedule native literature work,
independently of MR contributions. A full primary and contributions cannot both
own the same literature stage. Existing source/network/disclosure policies still
apply; the contribution itself is local work and can be reused offline.

The [supplied-literature schema](../schemas/supplied-literature-v1.schema.json)
defines the YAML/JSON file. For example, a bibliography entry can be:

```yaml
schema_version: 1
public_metadata: true
references:
  - id: wickenhagen
    doi: 10.1126/science.abj3624
    pmid: '34581622'
    title: A prenylated dsRNA sensor protects against severe COVID-19
    year: 2021
    journal: Science
queries: []
notes: Local review notes remain outside automatic editorial/advisor previews.
```

`public_metadata: true` explicitly classifies the bibliography and query labels
for the existing public-metadata pathway. It does not authorize transmission or
override a data policy. Notes, source paths, original provenance, complete supplied
abstracts and arbitrary extra method parameters stay outside the citation
whitelist. Raw source material remains local. Unrecognized free-form summaries or
native `evidence` exports are not silently promoted into verified references;
normalize them to the typed contract first.

Each reference needs a stable local `id`, a title and at least one DOI, PMID or
PMCID. Optional fields are `authors`, `year`, `journal`, `abstract_text` and local
`notes`. IDs link search records to references. Metadata aliases deduplicate in
the existing catalog while conflicting metadata remain visible. A short excerpt
generated from a supplied abstract is labeled caller-supplied and unauthenticated;
it cannot masquerade as a verified retrieved abstract in `finding_review`. A
later native abstract occurrence supersedes that excerpt with coherent native
offsets/audit, retaining both metadata origins. Detailed source-grounding still
uses the [public-text review contract](#claim-bound-principal-finding-discussion).

### Declare the completed query precisely

Add query records when search coverage or missing-work reuse is required:

```yaml
queries:
  - id: background-bmi
    provider: Europe PMC
    search_strategy: field_background
    query: '(TITLE_ABS:"RNA splicing") AND TITLE_ABS:"body mass index"'
    phenotype: body mass index
    status: complete
    coverage: complete
    hit_count: REPLACE_WITH_TOTAL_HIT_COUNT
    references: [REPLACE_WITH_ACTUAL_LOCAL_REFERENCE_KEYS]
    searched_at: '2026-10-01T00:00:00Z'
    reuse_rationale: Use this explicitly chosen source search and date under the study protocol.
```

Replace the placeholders with actual counts and reference keys from the
completed query; the bibliography example does not assert an OAS1 match to this
BMI query. Complete coverage requires every declared hit to be represented;
selected-reference exports use `coverage: selected_only` or `unknown`. A genuine
completed empty search has `hit_count: 0` and `references: []`. Unavailable or
partial searches remain unavailable or partial, regardless of their counts.
Source timestamps and reuse rationales are retained for review; an old search is
not silently relabeled current.

Native completion currently reuses Europe PMC queries only. Query text,
strategy, phenotype, gene, feature, Methods role, cohort identity/name and
explicit-reference scope must all match exactly. Missing optional scope labels
mean empty scope, not a wildcard. Complete query/hit coverage and a matching
registered source hash are also required. A provider mismatch, changed query or
different scientific scope does not suppress native retrieval. Conflicting
complete source versions for the same scope require choosing one explicitly.
Partial and unmatched records still contribute their public bibliography but
cannot establish coverage for a current query. No overlap or fuzzy title match
authorizes reuse.

Only missing queries consume native requests and query budget. Offline missing
work stays `waiting_for_input`; it is not recorded as no matches. The local
`literature-reuse.yaml` records completed-source hashes, eligibility, actual reuse
and unused queries. `literature-selection.tsv` distinguishes source-reported and
native records, including source-reported no-match results. Catalog coverage
distinguishes source-reported completion from native search completion. Neither
status certifies source quality, primary-study relevance, sufficient search
coverage, contradiction or novelty.

### Run the supplied-literature exercise

```powershell
python examples/study-pipeline/supplied-literature-workflow.py run NEW_DIRECTORY --jar JAR --references REFERENCES
python examples/study-pipeline/supplied-literature-workflow.py verify DIRECTORY --jar PINNED_JAR
```

`REFERENCES` is a native public reference bundle containing the Wickenhagen
bibliographic record. The fixture copies its real metadata into an external
typed bibliography, imports synthetic meta-analysis, and verifies an authored
manuscript citation without repeating the bibliography search. Other cases test
complete/partial imports, exact scope/provider/hash boundaries, ambiguous
sources, invalid links and missing work. Numerical results and external search
declarations are synthetic and supply no new OAS1 finding or AI appraisal.

The default exercise is offline. Optional `--live-missing-query` adds a case
that reuses six declared complete searches and sends one public dummy query to
Europe PMC for the remaining search. Its raw response/request audit is retained;
the dummy terms do not describe participants or scientific results. A failed
live retrieval remains a failed acceptance condition rather than a fabricated
negative result. The verifier pins its executable, input hashes, ledger,
manuscript and ownership inventory for independent rechecks.

## Claim-bound principal-finding discussion

The source build adds a local `finding_review` contract to `study manuscript`.
It complements study-wide impact selection: choosing three to five stories,
citing papers or writing long paragraphs does not establish the evidence behind
each claim. A story may span several connected paragraphs and linked supplements.
The adjustable 8,000-word main-text target and separate supplement counts apply.

Each principal story identifies one eligible frozen `finding_id`. Its dossier
links every main and supplementary paragraph to claims classified as
`observed_result`, `literature_result`, `inference`, `hypothesis`, `limitation`,
`proposed_experiment` or `editorial_context`. Each claim supplies a unique literal
span of current, unexpanded prose. Numerical template bindings remain in that
span and expand through the existing verified evidence contract. An observed
result must identify a primary analysis source for that same finding. A paper
cannot establish that this study performed the paper's experiments.

Inferences, hypotheses and proposed experiments name their evidence dependencies
within the dossier. Unknown dependencies, overlapping claim spans and dependency
cycles fail. This is a logical evidence graph, not a biological network; it does
not prohibit biological feedback loops. Every substantive prose token in the
linked paragraphs needs a claim span. The exact `finding_inventory_sha256` also
binds the review to current analysis results. Added assertions and changed
results cannot silently inherit an earlier review.

### Acquire the actual primary source

Bibliography retrieval remains metadata-only by default. Explicitly request an
available public full-text XML projection in a reference request:

```yaml
schema_version: 1
references:
  - id: 10.1126/science.abj3624
    rationale: Review the primary experimental context for the OAS1 account.
    full_text: true
```

```powershell
java -jar jlinalg.jar study references references.yaml --project STUDY --out REFERENCES
```

The existing public-download/network policy applies; only public reference
identifiers enter requests. Exact metadata matches must supply one unambiguous
PMCID. JLinAlg requests its Europe PMC JATS XML, checks DOI/PMID identity, rejects
external XML entities/DTDs, and retains the raw response, audit, license, text and
hashes. The projection contains abstract/body text and textual captions, without
inspecting image pixels or fetching external supplements. Text retrieval failure
does not erase successfully retrieved bibliographic metadata.

Inspect `artifacts/full-text-sources.yaml` and
`artifacts/finding-review-source-template.yaml`. Copy an available source entry
into `finding_review.source_texts`; add
`reference_bundle: {manifest: ABSOLUTE_MANIFEST, sha256: CLOSED_MANIFEST_HASH}`.
The runtime verifies text hash, reference and scope against a sealed native
full-text member. Also register the acquisition under the revision's
`reference_bundles` so its references enter the citation catalog. A separately
provided public text needs a path, exact hash, reference, explicit scope,
`public_literature: true` and provenance. It remains caller-declared, without
independent authentication as a journal article.

### Bind claims and review depth honestly

The [finding-review schema](../schemas/manuscript-finding-review-v1.schema.json)
describes this revision fragment; the
[reference-request schema](../schemas/reference-request-v1.schema.json) describes
acquisition. Each revision emits a `finding-discussion-template.yaml` with
current finding/paragraph IDs and inventory hash. Replace its placeholders with
the actual source-grounded assessment.

Declare reusable `passages` using a source-text ID, short literal `quote`, and
source `location`. Each excerpt must occur exactly once in its bound text;
offsets and source hashes are retained. The contract bounds quotations at 25
words per reference across distinct passages; reuse one passage ID for shared
evidence. These are short source anchors, not limits on paraphrased discussion
or supplements. A claim's `readings` identify the passage, `supports`,
`contradicts` or `contextualizes`, and an attributed assessment. That claim must
contain an inline citation to the same reference. A verified catalog abstract
excerpt retains `catalog_excerpt` scope and cannot satisfy full-text appraisal.

Each literature result describes the source's design, species, tissue, molecular
trait, phenotype and limitations. Each proposed experiment specifies comparison,
readout, competing predictions and limitations; it is labeled proposed rather
than performed. Eight facets link to actual prose claims: observation, primary
evidence, mechanism, contrary evidence, alternatives, relevance, next experiments
and limitations. Unestablished or inapplicable facets need explanations retained
as prose limitations. Primary-evidence appraisal needs a full-text-bound source
result. Honest missing evidence remains a need.

`mode: review` preserves drafts and exposes missing bindings/facets as
`needs_input`. `mode: strict` rejects these mechanical gaps before creating an
output bundle. Malformed, fabricated or stale bindings fail in both modes.
`mode: off` requires a reason and records the omission. A strict local pass does
not clear independent Methods, citation, impact, word-budget or scientific
reviews. Even a complete report says
`source_bindings_complete_scientific_review_required`: neither word counts nor
facet counts certify scientific depth or truth.

Revisions retain the YAML review, escaped HTML review, template, source snapshots
and copied native reference bundles. Successors inherit sealed copies despite
later edits to original acquisition directories. Dossier replacement records the
actual caller; actor labels imply no scientific approval. Source texts and this
automatic review stay local and are excluded from the existing editorial payload.
Authored manuscript prose retains the existing exact-payload disclosure boundary.
A public-text declaration is not a privacy certification.

### Run the packaged source-binding exercise

```powershell
python examples/study-pipeline/finding-discussion-workflow.py run NEW_DIRECTORY --jar JAR --references REFERENCES
python examples/study-pipeline/finding-discussion-workflow.py verify DIRECTORY --jar PINNED_JAR
```

Use a separately acquired native reference bundle with available full text for
Wickenhagen et al. (Science 2021). Subsequent cases are offline. The workflow
tests source/result/prose hashes, literal passages, coverage, dependencies,
review/strict modes, sealed-copy inheritance and tampered inputs, retaining a
pinned executable/verifier and acceptance inventory. Numerical results and the
OAS1-labelled feature are synthetic. This example establishes no new association,
performs no AI appraisal and is not a complete OAS1 literature review. Actual
scientific depth for real three-to-five-finding manuscripts remains a separate
acceptance requirement.

## Declared human chromosome and ploidy contrasts

The source workflow supports one explicitly registered independent-participant
OLS/GLM chromosome/complement contrast per hypothesis. This determines the
participant population, genotype scale and allele denominator **before** MAC,
MAF, missingness QC and fitting. It does not infer sex, gender or a karyotype.
Record the scientific reason for the contrast and its testing family.

```yaml
datasets:
  - id: discovery
    type: gwas
    species: human
    stage: processed_matrix
    genome_build: GRCh38
    files:
      matrix: genotype.vcf.gz
      phenotype: phenotype.tsv
      ploidy_samples: local-ploidy.tsv
hypotheses:
  - id: H_X_XY
    outcome: trait
    covariates: [age, PC1, PC2]
    inputs: {discovery: [discovery]}
    analysis:
      mode: manual
      goals: [association]
      association:
        model: ols
        human_ploidy:
          region: x_nonpar_xy
          genome_build: GRCh38
          haploid_source_scale: observed
          invalid_calls: error
          unknown_samples: error
```

This fragment belongs inside a complete study contract. The local declaration
table has `id` and `chromosome_complement` columns; use `XX` or `XY` for the
supported assumed complements. Missing IDs, unknown and unsupported labels such
as `XXY` stop selection unless `unknown_samples: exclude` is explicitly registered.
Other labels are retained in the local exclusion audit. Declarations are matched
by ID after phenotype/genotype alignment and source complete-case selection.
They must remain unchanged during the analysis.

| `region` | Participants | Physical dosage | Marker region |
| --- | --- | --- | --- |
| `autosomes` | Declared XX and XY | 0–2 | Human chromosomes 1–22 |
| `par` | Declared XX and XY | 0–2 | GRCh37/38 X PAR1/PAR2 coordinates |
| `x_nonpar_xx` | Declared XX | 0–2 | X outside PAR |
| `x_nonpar_xy` | Declared XY | 0–1 | X outside PAR |
| `y_nonpar_xy` | Declared XY | 0–1 | Y outside PAR |

Coordinates are 1-based and closed, using the
[Genome Reference Consortium PAR intervals](https://www.ncbi.nlm.nih.gov/grc/human).
X and Y have different PAR2 coordinates. The PAR contrast takes the X-coordinate
representation and excludes duplicate Y-PAR records. `PAR1`/`PAR2` chromosome
labels require matching X coordinates. Non-primary contigs and other assemblies
need a separately supported coordinate contract.

For a haploid contrast, `observed` requires observed source ploidy one and physical
0–1 dosages. `haploid` explicitly declares 0–1 source coding when ploidy is
unknown. `diploid` explicitly declares 0–2 source coding, requires unknown or
diploid source representation and divides dosage by two. No scale is guessed.
An apparent heterozygous haploid hard call becomes an invalid fractional call
after conversion. `invalid_calls: error` stops the scan with local receipts;
`missing` retains an audited missing call. Fractional *expected dosages* remain
valid for dosage-based OLS/GLM, subject to the registered model and quality filters.
See [PLINK's ploidy input guidance](https://www.cog-genomics.org/plink/2.0/input).

MAC uses physical alternate copies and called alleles among the actual analyzed
participants, before genotype imputation. For 80 called XY participants outside
PAR, AN is 80. Twenty alternate copies meet the default MAC 20 threshold; nineteen
do not. Excluding a carrier for a missing covariate can change eligibility. A
diploid-coded haploid input must not double its physical MAC. Group MAC uses the
same cohort-minor allele. Structural absence of Y in XX participants is outside
the Y contrast; it is not a failed Y call from an analyzed participant.

The legacy sample-call-rate scan uses this registered marker region and complement.
If the separate observed-autosomal sample QC/PCA profile is enabled, its registered
autosomal panel stays an autosomal quality/ancestry panel within the selected
population. Its denominators are reported separately. Such QC does not establish
the truth of the supplied complement or chromosome-specific model calibration.

Diploid hard-call HWE is available for PAR and the XX non-PAR X contrast, with the
registered all/control/case and source-panel scope. Requested HWE on a haploid
contrast is rejected. Genotype origin, missingness, source ploidy and requested
unavailable diagnostics remain explicit. This is not a pooled XX/XY X-HWE test.

Standalone association uses `--human-ploidy-region`, `--human-ploidy-build` and
`--ploidy-samples`, with `--haploid-source-scale`, `--invalid-ploidy-calls` and
`--unknown-ploidy-samples` as the corresponding controls. The independent
Bernoulli score, Firth LRT and finite conditional hard-call routes can use this
physical design when their own model requirements hold. Exact conditional tests
still require the supported stratum nuisance design; arbitrary covariate slopes
cannot be silently removed.

Each association retains `.human-ploidy.yaml`, `.human-ploidy-samples.tsv`,
`.human-ploidy-variants.tsv` and `.human-ploidy-invalid-calls.tsv`. Study participant
QC also retains population selection and, for its regional call-rate scan, separate
`prefit-callrate.human-ploidy*` receipts. These record outside-scope markers,
source representation, invalid calls, conversion policy, physical AC/AN and the
declaration hash. Sample and invalid-call receipts contain participant data and
stay local. Automatic remote context includes allowlisted protocol enums;
participant declarations, raw calls and audit rows are not projected.

This route rejects mixed/GRM/pedigree, survival, resume and conditional-summary
export combinations. Native rare-set scores explicitly wait for a validated
ploidy model. Pooled X dosage-compensation/sex-interaction models, aneuploid,
mitochondrial and broader chromosome-specific models remain unfinished.
Different contrasts are separate estimands and require declared multiplicity;
they are not independent replication cohorts and are not automatically pooled.
The original generic diploid route remains available when no profile is supplied.

The reproducible `examples/study-pipeline/human-ploidy-workflow.py` executable verifier
uses synthetic data, independent NumPy/SciPy OLS, physical-count checks and a
hypergeometric reference for a simple finite conditional test. It also exercises
five separate study contrasts. This validates software behavior within the stated
scope; it does not validate real complement declarations or scientific model fit.

## Declared cross-hypothesis primary MR families

MR correction within one phenotype does not establish correction across the
phenotypes discussed in a manuscript. Register a joint reporting family when
that is the intended scientific comparison:

```yaml
project:
  mr_testing_families:
    - id: cardiometabolic-mr
      hypotheses: [H_glucose, H_BMI, H_blood_pressure]
      adjustment: BY
      alpha: 0.05
      rationale: Primary molecular-trait MR tests across these declared outcomes.
```

Each member must be an active hypothesis with exactly one planned primary MR
producer. Native MR, a complete supplied MR stage, and native MR completed from
supplied contributions can supply those producers. A hypothesis can belong to
one joint MR family. Separate families are separate scientific scopes; the
workflow does not claim study-wide control across unregistered families.
The rationale records a declaration, not independently certified preregistration.

The planner places `mr_family_adjustment` after all member MR nodes and before
study-wide finding inventory, impact assessment and member reports. The stage
consumes the sealed canonical primary MR tables. It never uses association FDR,
source-adjusted P values, the smallest sensitivity-method P, or only significant
MR results as the new raw testing family.

Native source test IDs remain intact within each hypothesis. Alias pairs already
resolved by the producer do not become extra tests. Equal IDs in different
hypotheses remain separate declared hypotheses; this does not establish
independence. Definitive non-estimable tests reserve their declared denominator
positions while their raw and adjusted P values remain unavailable. Implicit
P=1 padding is only an adjustment calculation convention. It is not a null result.
An explicitly empty eligible native family requires a sealed zero-size receipt;
a missing producer or missing table cannot stand in for zero eligible tests.

BY is the default because correlated phenotypes can create general dependence.
BH can be explicitly registered with its appropriate dependence assumptions;
`bonferroni` controls familywise error and `NONE` is nominal with no multiplicity
control. See the official [R `p.adjust` documentation](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html).
Input P-value calibration, instrument validity, winner's curse, sampling overlap
and causal identification remain separate scientific requirements.

Complete supplied primary tables can join the family when their complete source
coverage, tested count and all raw primary P values are available. A selected-only
export may retain valid source adjustments but cannot reconstruct the joint raw
family. Counts that exceed the observed canonical table, incomplete member
states, unknown coverage and unresolved primary tests hold the joint stage.
Available source results remain visible; every joint adjusted P is unavailable
and every joint discovery flag is false until the whole registered family is
resolved. No partial-family adjustment or favorable member substitution occurs.

`mr-family-results.tsv` preserves source test/feature/pair IDs, raw primary P,
original adjusted P/method/family size/status, and source operation/hash. Separate
`joint_*` columns record the new adjustment, denominator, completeness and
discovery decision. `mr-family.yaml` records member coverage, method and review
needs. Neither source tables nor source corrections are overwritten. Effective
source feature budgets remain part of the test definition; the new family does
not silently include untested features.

The uncapped finding inventory attaches joint MR evidence and its limitations
to matching hypothesis/feature dossiers. MR-only primary findings use their
joint significance for AI-candidate eligibility while retaining source primary
significance. An association finding can remain an association candidate when
its MR support fails the joint threshold; the two claims remain distinct. AI
and manuscript instructions require separate joint/source labels. Selecting
3–5 stories is an editorial step and has no additional FDR guarantee.

The synthetic `examples/study-pipeline/mr-cross-family-workflow.py` exercises
two-outcome native MR, supplied primary reuse, missing coverage, joint correction,
manuscript context and cache replay. Independent Wald/IVW and SciPy BH/BY
calculations check the numerical contracts; they do not validate real instruments
or establish scientific causal effects.

## Conditional tests of frozen network wiring

`network_preservation_null` is a separate, opt-in stage. It tests a prespecified
reference edge pattern against a conditional target-label null. Descriptive
module preservation, GO enrichment, association discoveries and WGCNA Zsummary
remain separate results. The frozen-reference/permutation motivation follows
[Langfelder et al. (2011)](https://journals.plos.org/ploscompbiol/article?id=10.1371/journal.pcbi.1001057);
the implemented integer overlap statistic is different from WGCNA's statistics.

```yaml
analysis:
  mode: manual
  goals: [network_preservation_null]
  network_preservation:
    references: [dataset:discovery]
    graph_namespaces:
      dataset:discovery: HGNC:approved-symbols:registered-release
      dataset:validation: HGNC:approved-symbols:registered-release
  network_preservation_null:
    enabled: true
    statistic: signed_edge_overlap
    method: auto
    exact_maximum: 100000
    draws: 9999
    seed: 17
    adjustment: BY
    alpha: 0.05
    exchangeability_rationale: Scientist-authored justification for this conditional label null.
    # strata_file: registered-node-strata.tsv
```

Use the exact aliases of the native or supplied graphs. In automatic mode,
`enabled: true` requests this stage; an explicit goal also requests it. The
default is disabled. A missing scientific exchangeability rationale holds the
test rather than treating all gene labels as exchangeable by default. A label
or nonempty rationale does not independently certify the scientific assumption.

Let U be all nodes shared by the reference and target graphs, M the frozen
reference module restricted to U, and E the reference edges within M. For each
legal within-stratum bijection pi of U, the statistic counts edges (i,j) in E
whose mapped pair (pi(i),pi(j)) occurs in the target graph. The default
`signed_edge_overlap` also requires the target sign to match the reference
sign. `edge_overlap` ignores sign. Weight magnitudes are not tested. Module
membership and reference wiring stay fixed; the whole common-node universe is
eligible for relabeling, including nodes outside the module.

The target graph's topology and signs are retained under relabeling. Optional
strata are fixed before inspecting target preservation results and have columns
`node_id` and `stratum`. Every shared node needs one unique assignment. Missing
assignments never fall back to unrestricted permutation. Extra assignments
outside a comparison's shared universe are unused. Missing module nodes remain
missing; they do not become zero correlations. The existing source comparability,
coverage, minimum-size, graph and membership bounds apply first.

`auto` enumerates the entire product of within-stratum permutation groups when
it fits `exact_maximum`; otherwise it uses the fixed registered sampled draw
count. Explicit `exact` waits if enumeration exceeds that bound. Exact P is the
fraction of all legal permutations with statistic at least the observed value,
including ties and the identity. Sampled draws are independent uniform
within-stratum permutations with replacement, and P = (b + 1)/(B + 1). It is
never a zero or a tail extrapolated beyond the draw resolution. These rules
require the null's group-invariance assumptions; see
[Hemerik and Goeman (2018)](https://pmc.ncbi.nlm.nih.gov/articles/PMC6405018/).

Sampled results retain a 95% exact binomial interval for the conditional extreme
tail fraction, using beta quantiles as in the
[Clopper-Pearson procedure](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/binom.test.html).
This measures Monte Carlo precision, not
participant-sampling uncertainty, and is not a simultaneous interval across
modules. Exact results repeat their exact P as both interval limits. A small
draw budget may be unable to resolve significance after multiplicity correction;
the software does not extrapolate smaller P values or stop sampling early after
observing a favorable result.

The separate testing family contains all planned reference-module/target rows
for this hypothesis and one registered statistic. Definitively non-estimable
rows reserve denominator positions. Missing sources, unresolved comparisons or
work contracts withhold all adjusted P values and discoveries, while retaining
available raw tests. BY is the default for dependent comparisons. BH requires
its own dependence assumptions; Bonferroni controls familywise error and NONE
is nominal. These are not association FDR values or a joint cross-phenotype
family. Neither the primary graph/cohort set nor association candidates change.

`max_work` defaults to 100000000 declared label-position/edge-query work units;
`max_null_rows` defaults to 100000 histogram rows. Bounds apply to the complete
requested matrix, not a preferred module prefix. They are computational
contracts, not guarantees about wall time or support for genome-wide scale.
All source graphs and reference memberships stay available in
`prepared-preservation/`, with their original producer receipts.

Outputs include `network-preservation-tests.tsv`, the compressed full draw
histograms in `network-null-statistic-counts.tsv`, frozen memberships and
`network-preservation-null.yaml`. They retain source identity, statistic,
universe, method, counts, seed, P resolution, precision interval, denominator,
correction and every test disposition. A revised strata file invalidates this
stage's cache even when its path is unchanged; completed source graphs/modules
can be reused. Prior attempts remain immutable.

Typed supplied `network_preservation_null` results preserve original corrections
and counts without graph refitting. Their table uses the native canonical
columns and count/precision/family checks. Source declarations and full-family
coverage remain separately visible. Already-supplied descriptive preservation
can also be retained while only this missing test is computed from available
graph/membership sources. A descriptive table alone cannot supply missing graph
edges. Supplied P/count consistency does not independently reproduce the source
permutations or prove its exchangeability assumption.

Finding dossiers carry this as nonprimary conditional graph context, with
family/method status, source links and mandatory caveats. Local free-text
exchangeability rationale and strata paths are excluded from the native Methods
record sent through aggregate packets. Scientific review must consider shared
participants, gene properties, source/node selection and post hoc reference
selection. A small conditional P is neither independent replication nor evidence
of a module's biological function; GO and primary literature remain separate.

The source-checkout example `examples/study-pipeline/network-null-workflow.py`
uses synthetic graphs, independent Python enumeration and numerical references.
It exercises native/supplied graph sources, all correction policies, held
prerequisites, supplied inference, missing-strata completion and input revision.
It does not certify real-network exchangeability or biological conclusions.

## External population-frequency eligibility for rare masks

`analysis.rare_variants.external_frequency` is an optional screen before native
mask/score construction or score-summary pooling. It consumes a user-prepared,
versioned allele-count table. It uses the maximum **ALT AC/AN** across an explicit
population panel. This is separate from the existing observed-cohort `maximum_maf`
criterion and the single-variant MAC20 default. A common functional ALT allele
with a rare REF allele does not become externally rare by taking `min(AF,1-AF)`.

```yaml
analysis:
  rare_variants:
    external_frequency:
      enabled: true
      file: resources/external-candidate-counts.tsv
      source: gnomAD-derived-reviewed-export
      release: exact-export-release
      genome_build: GRCh38
      populations: [afr, nfe]  # Explicit source labels; choose a justified panel.
      minimum_allele_number: 1000  # Example protocol value, not a universal default.
      maximum_rows: 1000000
      rationale: Prespecified population panel and coverage rule for this study.
    masks:
      - id: lof
        consequences: [lof]
        maximum_maf: 0.01
        maximum_external_af: 0.01
```

Supply a UTF-8 TSV with `variant_id`, `population`, `allele_count`,
`allele_number`, `filter` and `genome_build` columns. Counts must be exact
nonnegative integers, AC <= AN. `NA`/`.` counts mark unresolved observations.
`variant_id` is the normalized 1-based `CHROM:POS:REF:ALT` annotation key;
GRCh37/hg19 and GRCh38/hg38 aliases are normalized. Both source and row builds
must agree with annotation. No variant normalization, liftover, reverse-complement
matching, allele swap or multiallelic decomposition is performed. Duplicate
canonical allele/population keys, invalid counts and row/build conflicts fail.
No coverage is required for annotation rows outside all declared consequence
masks; every candidate allele/population in their union is assessed before tests.

Every selected population requires an exact row with available counts and AN at
least the explicit minimum. `filter: PASS` is required for eligibility; a known
non-PASS filter excludes the allele. Unavailable filter labels (`NA`, `.`), absent
rows, allele mismatches, unavailable counts and insufficient AN hold the entire
mask stage. No prefix or successfully covered subset is tested. A missing file
or source declaration generates an actionable request. A complete observed
AC=0/adequate AN row can pass; an absent row or AN=0 cannot. The maximum observed
ALT AC/AN must be **<=** each mask's threshold; equality passes. The per-mask
`maximum_external_af` defaults to its `maximum_maf` only while this optional
screen is enabled. Declaring an external threshold with the screen disabled is
an error. No population or scientifically universal minimum AN is inferred.

The selected population panel, exact release, minimum AN and rationale must be
fixed before looking at results. A pooled/global AF may conceal an allele common
in one population. The source labels alone do not establish that those populations
represent study participants. This is a point-frequency screen, **not** gnomAD
FAF/grpmax, an allele-frequency confidence bound, pathogenicity classification or
automatic ancestry inference. The [REGENIE AAF/mask documentation](https://rgcgithub.github.io/regenie/options/)
uses ALT-specific frequencies. The [gnomAD v4.1 documentation](https://gnomad.broadinstitute.org/news/2024-04-gnomad-v4-1/)
explains why an unobserved variant needs callable-site AN before zero counts can
be interpreted. Prepare a correctly allele-resolved export; JLinAlg does not
download/convert a gnomAD release or infer absence/coverage from VCF omission.

If every allele is conclusively excluded, the mask stage records an empty
eligible universe. Downstream score/test stages record `performed: false` and
an empty result table without fitting models or constructing an FDR family.
This is distinct from unresolved coverage, which holds the stage for input.

Outputs retain the unchanged `external-frequency-source.tsv`, source SHA/release
and panel in `external-frequency.yaml`, all prefilter memberships in
`external-frequency-candidates.tsv`, population observations in
`external-frequency-qc.tsv` and per-mask allele decisions in
`external-frequency-mask-qc.tsv`. Successful frozen memberships and their Methods
record enter `masks.yaml` and downstream provenance. Local scientific rationale
is required, but software cannot certify its scientific adequacy. Source bytes
enter the mask cache key; a correction at the same path invalidates that stage
and dependent work. Completed cohort fits can be reused when their own audited
input contract is unchanged; previous execution attempts remain immutable.

Compatible supplied score/covariance plus annotations can use this filter before
pooling without generating genotype scores again. Absent variants removed
upstream remain absent and are reported by the score backend; the filter cannot
recover them. P-only set results cannot be refiltered, so enabling a new native
frequency rule on those inputs generates a request for appropriate inputs or a
separately reviewed upstream policy. A supplied completed primary rare-set result
continues to retain its own upstream method/frequency provenance; accepting that
result does not mean this filter was run.

The offline example below creates synthetic counts and cohorts; it does not test
real resource conversion, population matching, source appraisal or scientific
findings. Its independent burden checks use exported U/covariance summaries.

```powershell
python examples/study-pipeline/rare-frequency-workflow.py run NEW_DIRECTORY --jar PATH_TO_JAR
python examples/study-pipeline/rare-frequency-workflow.py verify NEW_DIRECTORY --jar PATH_TO_JAR
```

The frozen source-build checkpoint passed all 13 new tests, 18 packaged cases,
70 independent eligibility/burden comparisons and the earlier native/unsigned
rare-set workflows. Exact full-gate counts, hashes, retained artifacts and limits
are reported in the [implementation checkpoint](../analysis-pipeline-implementation.md#external-population-frequency-mask-eligibility-2026-10-25).
The overall analysis-pipeline objective remains incomplete.

## Registered native Cox survival association

`analysis.association.model: cox` now has a study adapter for independent
participants, baseline molecular predictors or human-autosome genotypes. It
performs a full Cox fit for each eligible predictor on the same prefit-selected
population. This is distinct from the standalone CLI's prepared-null Cox score
scanner. The study route records its full-fit estimator and test explicitly.

```yaml
analysis:
  mode: manual
  goals: [association, meta_analysis, discovery_replication, finding_inventory]
  association:
    model: cox
    offset_column: known_log_hazard_offset # Optional fixed baseline offset.
    survival:
      time_column: followup_time
      event_column: event_status
      entry_column: entry_time             # Optional; otherwise zero.
      strata_column: baseline_stratum      # Optional separate baseline hazards.
      time_units: years
      event_value: 1
      censor_value: 0
      event_definition: First incident endpoint recorded at followup_time.
      censoring_definition: Administrative or loss-to-follow-up censoring.
      rationale: Prespecified independent-participant baseline Cox estimand.
      independent_censoring_rationale: Record the conditional censoring assumption and its basis.
      proportional_hazards_rationale: Record the prespecified PH assumption and planned review.
      ties: efron                        # efron or breslow; fixed before results.
      test: likelihood_ratio             # Or wald, explicitly registered.
      minimum_events: 1                  # Computational floor, not calibration evidence.
      maximum_features: 1000000
      maximum_fit_work: 10000000000
      maximum_feature_missing: 0.02
      ph_adjustment: BY
      ph_alpha: 0.05
```

The public hypothesis `outcome` names the endpoint; it need not be a physical
phenotype column. Survival time/event/entry fields map through
`columns.phenotypes`, strata through `columns.covariates`. Covariates and offsets
retain the usual study mappings. Missing endpoint/assumption declarations request
input. A declared survival endpoint without a model retains the missing-model
review gate; it does not silently choose Cox. Unsupported Cox controls (including
observation weights or GLM family/link settings) are rejected explicitly.
QC aligns unique participant IDs, selects joint complete cases for
time/event/entry/strata/covariates/offset, then applies registered sample QC.
Every analyzed interval must satisfy finite `0 <= entry < time`; the risk-set
convention is `(entry,time]`. Only the two explicit event/censor codes are
accepted. Missing observations are excluded with the participant-flow record;
invalid codes/intervals or insufficient events hold the endpoint branch. There
is one interval and at most one event per distinct participant. This does not
infer recurrent-event, interval-censoring, competing-risk or time-varying models.

Numeric and categorical baseline covariates are compiled with reference contrasts
before removing the unidentifiable intercept. An unadjusted model uses a genuine
zero-coefficient null, with risk sets, offset and baseline hazard retained.
Nuisance columns constant within every registered baseline stratum are absorbed
by those baseline hazards and recorded; other singular designs require review.
No hidden information ridge is added when the study's zero-ridge model is used.
The current full-fit adapter runs one CPU worker and records actual execution.

Genotype QC operates on the selected phenotype population before predictor mean
imputation. MAC20 remains the single-variant default; independent rare-set
eligibility is unchanged. The Cox genotype default scope is `human_autosomes`,
with explicit GRCh37/38 build and source allele/ploidy checks. Event/censor codes
are not case/control groups: binary missingness tests and case/control HWE filters
are held for an appropriate QC contract. All-sample/explicit-panel HWE, declared
INFO/call-rate and other usual marker filters apply. The Methods parameters
record the effective QC thresholds, HWE scope/panel hash, genotype kind and build.
Sex/PAR, other ploidy,
GRM/pedigree/known-related designs need a matching survival workflow or compatible
provided results. No independent-model fallback is selected for those designs.

Molecular and genotype missing predictors are mean-imputed only after their
registered QC. The imputation is fixed and its uncertainty is not propagated.
The matrix is streamed by feature, but full fits/diagnostics are computationally
bounded and do not constitute genome-wide performance acceptance. The work
accounting unit is `n*(q+1)^2*(maximum_iterations*(maximum_step_halvings+1)+2*n)`
per charged fit; it is an estimate, not wall-clock prediction. Known source-count
bounds can hold before fits; an unknown-size source/work limit or unresolved
full fit retains partial raw artifacts and withholds official selection for the
whole association family. Available successful rows do not define a smaller FDR
family. Outcome-independent QC exclusions remain separately auditable.

Coefficients, SE and confidence intervals use the **log hazard-ratio** scale.
The default primary P value is a nested full/null partial-likelihood ratio with
one-degree-of-freedom asymptotic chi-square inference. Registered Wald tests use
the coefficient/ordinary-Fisher SE normal approximation. The log tail and actual
test/statistic labels survive normalization; event/censor counts stay distinct
from case/control counts. Log-HR is not a log odds ratio, risk ratio or causal
effect. Known conflicting typed effect scales/units stop pooling; missing source
contracts remain subject to the separate completeness policy. Subsequent
inverse-variance meta-analysis uses its own Wald approximation, even for source
LRT P values. MAC20, event counts, fitter agreement and a small P value do not
establish rare-event tail calibration or population transportability. Algorithmic
convergence alone does not prove existence of a finite maximum-likelihood estimate;
the [finite-MPLE certificate stage](#finite-cox-coefficient-estimates-before-signed-inference)
now supplies a separate mathematical check before native signed inference.

The PH diagnostic tests a log-time interaction after profiling the fitted
baseline coefficients, using the efficient information Schur complement.
Coefficient-specific predictor tests form a separate registered BH/BY/Bonferroni
or NONE family across the fitted features. Unavailable predictor diagnostics or
an incomplete association universe withhold that family's corrected alerts.
Model-wide global PH P values are nominal context. A single distinct event time
cannot estimate a time trend; it remains unavailable. NONE alerts are explicitly
nominal. Diagnostics do not replace the association P value, select a favorable
cohort/model or prove PH. An alert identifies a sensitivity/interpretation review
candidate under the declared protocol.

`association.tsv` retains every examined predictor disposition, fit controls and
source/QC metadata. `standard-results/` feeds existing effect QC, official-source,
pooling, frozen discovery/validation and downstream inference. Local artifacts
include `survival-analysis.yaml`, `survival-population.tsv`,
`cox-null-coefficients.tsv`, `cox-null-baseline.tsv` and
`survival-diagnostics.tsv`. Aggregate finding dossiers attach feature/cohort PH,
event counts, source hashes and mandatory limitations without participant arrays
or private phenotype columns. An explicit `finding_inventory` goal builds these
dossiers offline without requesting AI impact assessment. Supplied completed
cohort statistics skip native participant fits/PH reconstruction and retain
their upstream provenance; a model label alone does not reproduce endpoint QC.

Independent fixtures from local R4.6.1/survival3.8.6 compare `coxph` and
`cox.zph(transform="log",terms=FALSE)` for right censoring, delayed entry,
strata, offsets and Efron/Breslow ties. The generator, exact inputs, expected
values, versions and hashes are checked in under `src/test/R/` and
`src/test/resources/study-survival/`. The implementation corrects the earlier PH
calculation that omitted nuisance-coefficient profiling; historical checkpoints
are not rewritten. The [official Cox documentation](https://stat.ethz.ch/R-manual/R-devel/library/survival/html/coxph.html)
and [PH diagnostic documentation](https://stat.ethz.ch/R-manual/R-devel/library/survival/html/cox.zph.html)
describe the relevant model assumptions and score-test distinction.

```powershell
python examples/study-pipeline/survival-workflow.py run NEW_DIRECTORY --jar PATH_TO_JAR
python examples/study-pipeline/survival-workflow.py verify NEW_DIRECTORY --jar PATH_TO_JAR
```

The example's NumPy/SciPy implementation independently reconstructs full/null
likelihoods, coefficients/SE/P, efficient PH tests and fixed log-HR pooling. It
uses synthetic cohorts and typed provided results; it does not certify actual
censoring/PH assumptions or scientific calibration. The frozen checkpoint passed
all 24 new tests, 18 packaged cases and 401 independent numerical comparisons,
with fresh R reference regeneration and the prior seven-run resume/MR workflow.
Exact full-gate counts, hashes, retained evidence and boundaries appear in the
[implementation checkpoint](../analysis-pipeline-implementation.md#native-cox-study-association-and-efficient-ph-diagnostics-2026-10-25).
The overall analysis-pipeline objective remains incomplete.


## Finite Cox coefficient estimates before signed inference

Native Cox studies now check the nuisance null and each QC-eligible full model
before accepting signed effect estimates. The check uses event-minus-risk
predictor contrasts from the actual `(entry,time]` interval and baseline stratum;
the adaptive implementation described below can prove finiteness from a sufficient
subset. Tied failures constrain one another in both directions for
the supported unweighted Efron/Breslow likelihoods; finite fixed offsets do not
alter this recession cone. This extends the risk-contrast existence argument to
the actual risk sets used by this adapter. It does not establish censoring or
PH assumptions, event-tail calibration, appropriate predictor specification or
population validity.

A full-rank contrast matrix with strictly positive weights whose weighted
contrasts balance to zero proves a finite unique coefficient maximum. A direction
with every contrast nonnegative and at least one strictly positive proves
monotone likelihood. A bounded NNLS search proposes candidates; final acceptance
uses exact arithmetic on the supplied binary-double values. A finite certificate
includes positive base weights, sparse additional weights and exact rational
basis corrections. A monotone certificate includes an exact rational direction;
its approximate floating direction is only for display. A numerical tolerance
does not soften the final sign, balance or positivity requirements. Missing
proofs, rank/search ambiguity and exhausted bounds remain `UNRESOLVED`.

```yaml
association:
  model: cox
  survival:
    # Add these advanced controls to the registered endpoint declaration above.
    maximum_likelihood_pair_visits: 100000000
    maximum_likelihood_work: 1000000000
    maximum_likelihood_iterations: 1000
    maximum_likelihood_dimension: 64
    maximum_likelihood_certificate_bits: 16384
    likelihood_tolerance: 1.0e-10
```

Pair visits and work are shared by the nuisance null and all eligible features
in one cohort association family. Iterations, dimension and rational bit size
bound each search. Work counts are deterministic accounting units, not elapsed
time or a genome-wide performance claim. A bound cannot reset for each feature
to create an apparently complete smaller family. Existing feature, fit and
source bounds still apply. Failed null certification holds before association
fits; a failed full-model check retains the examined raw rows and local proofs,
withholds native whole-family primary selection and corrected PH alerts, and
requests an appropriate reviewed analysis. Compatible supplied results continue
downstream with upstream attribution and no invented native certificate.

The descriptive Welch comparisons use a common numeric scale and stable
variance fractions; cohort effect correlations/regressions use scaled Welford
moments. Extreme cohort scatter plots divide both display axes by one recorded
positive factor and label that factor. Source effect exports retain their units;
correlations and regression summaries retain their statistical definitions.
This avoids variance overflow/underflow and oversized/indistinguishable default
axis labels without reducing the tested source universe.

Ordinary Newton convergence does not prove coefficient existence. In particular,
an apparently converged infinite coefficient makes ordinary Wald effect inference
invalid. Likelihood-ratio and score tests can remain valid under their own
conditions; this signed full-fit adapter does not compute their limiting
supremum or silently substitute penalized Cox estimates. Separately justified
P-only/supplied analyses can use the existing typed-result route. Read the
[official Cox convergence documentation](https://stat.ethz.ch/R-manual/R-devel/library/survival/html/coxph.html),
[existence paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC2744117/) and
[monotone-likelihood paper](https://pubmed.ncbi.nlm.nih.gov/11252585/).

Unavailable constant-effect correlations/regressions use explicit `NA` values
and a `not_estimable` status in audit documents. Local certificate mappings use
deterministic key order so identical native inputs retain the same proof hashes
and standard tables across separate JVM executions.

Local `survival-likelihood-certificates/*.yaml` files retain exact proof terms
and input-row indices. Raw/normalized native results retain
`likelihood_existence` and `likelihood_proof_sha256`. Aggregate dossiers and
unsent assistance packets carry status/counts and source hashes, with local
witness vectors and row indices excluded. These are mathematical certificates
for the source matrix represented in binary doubles, not privacy certification
or independent scientific replication. Ordinary library `CoxRegression.fit`
retains its numerical convergence contract; `fitChecked` additionally requires
a finite-MPLE certificate and returns it or a typed `CoxLikelihoodException`.

```powershell
python examples/study-pipeline/cox-likelihood-workflow.py run NEW_DIRECTORY --jar PATH_TO_JAR
python examples/study-pipeline/cox-likelihood-workflow.py verify NEW_DIRECTORY --jar PATH_TO_JAR
```

The independent SciPy/HiGHS fixture generator and provenance are checked in at
`src/test/python/cox_likelihood_existence_reference.py` and
`src/test/resources/cox-likelihood-existence/`. Packaged numerical/contract and
frozen full validation passed: all 15 new tests, 11 new packaged cases,
142 numerical comparisons, 73 independent risk-cone LPs, 23,573 exact witness
contrasts and cross-process proof/table checks, plus the earlier survival and
resume/MR workflows. Exact counts, hashes, retained evidence and boundaries are
in the [implementation checkpoint](../analysis-pipeline-implementation.md#finite-cox-maximum-certificates-2026-10-25).

## Adaptive Cox proofs and prepared-null score scans

`CoxAdaptiveLikelihood` avoids exhaustive enumeration of all event/risk pairs
when a smaller exact certificate suffices. It tries deterministic participant
supports of 32, 128, 512 and 2,048 rows, adding covariate extrema and event anchors.
Every selected event/risk contrast is also a contrast of the original model.
A strictly positive full-rank balance on this support therefore excludes every
nonzero recession direction of the full likelihood. It proves finiteness of the
full model without claiming that the omitted contrasts balance.

A direction that separates a selected support can fail on an omitted participant.
The adapter accepts monotone likelihood only after an exact rational projection
and entry/exit sweep checks **every original event risk set** in each stratum.
This includes tied failures and strict `(entry,time]` boundaries. A refuted
direction triggers support expansion. Exhausted search, dimension, work, pair
or certificate-bit bounds remain unresolved; a partial search cannot establish
a finite maximum. All attempts and full-risk sweeps charge the same work budget.
These are bounded sufficient checks, with no completeness or genome-wide
throughput guarantee. Existing fit and PH diagnostics have separate costs.

Native signed studies use this adaptive check for their null and each eligible
full model, retaining the complete-family hold described above. Local certificate
schema 2 adds `supporting_source_rows`, `support_attempts`, `full_risk_queries`
and `full_direction_verified`. Positive base weights apply only to actual
contrasts **within the listed supporting rows**; sparse weights/corrections use
original analyzed-row indices. The local record defines the reconstruction rule
and scope. Aggregate finding context retains status/counts and hashes, with
support indices, directions and coefficient-level witnesses excluded. Defaults40
and implementation66 invalidate incompatible native caches.

`FastCoxAssociation.prepare` now certifies a finite nuisance maximum before
fitting or returning a reusable null. `nullLikelihood()` exposes the adaptive
result. Its explicit-options overload accepts the same certificate bounds as
`CoxLikelihoodExistence`; zero-column covariate matrices support unadjusted scans.
The prepared scanner centers/scales its internal nuisance and predictor geometry,
projects nuisance score and information out, and restores source-unit effects,
SEs, scores and covariance. Constant predictors or unrepresentable source-unit
results fail explicitly. These numerical controls do not calibrate rare-event
tails or turn a score-variance adjustment into a frailty fit.

Standalone Cox scans now accept numeric assay matrices as well as genotypes:

```powershell
java -jar jlinalg.jar --pheno phenotype.tsv --id id `
  --omics expression.tsv --omics-type expression --model cox `
  --formula "Surv(time,event) ~ <omics>" --out unadjusted.tsv
java -jar jlinalg.jar --pheno phenotype.tsv --id id `
  --omics expression.tsv --omics-type expression --model cox `
  --formula "Surv(entry,time,event) ~ age + sex + offset(o) + <omics>" `
  --ties efron --out adjusted.tsv
```

The CLI retains the ordinary reference coding of factors, then removes the
intercept absorbed by the Cox baseline. It requires event codes `0` (censored)
and `1` (observed), including fully observed cohorts; it never sends survival
events through binary-trait case/control recoding. Recode other labels explicitly
or use the study's registered `event_value`/`censor_value` mapping. Formula weights
and related-participant assay scans require a separate supported contract.

The scan tests the efficient null score. Its reported signed coefficient
`Ueff/Ieff` and SE `1/sqrt(Ieff)` are **one-step approximations**, not alternative
maximum-likelihood effects. The alternative model need not have a finite MLE
for this score route to run. The standalone coefficient command instead checks
the full independent model and requires numerical convergence before publishing
ordinary Wald coefficients. The convergence/existence distinction follows the
[official Cox documentation](https://stat.ethz.ch/R-manual/R-devel/library/survival/html/coxph.html).
It does not imply that an infinite alternative coefficient invalidates every
separately justified likelihood-ratio or score test.

Each standalone manifest records `cox_test`, `cox_effect_estimator`, event coding,
tie handling, certificate method/status/work/support count, finite-maximum scope
and asymptotic-only calibration. A prepared scan labels its certificate scope
`nuisance_null_only; alternative_MPLE_not_required`. Local standalone logs and
manifests contain certificate summaries; the detailed witness YAML belongs to
the native study adapter. The independent-model certificate does not certify
the standalone GRM/frailty branch. Keep these method distinctions when importing
results for pooling, evidence appraisal or manuscript writing.

`examples/study-pipeline/cox-score-workflow.py run NEW_DIRECTORY --jar JAR` checks
the actual executable with independent NumPy/SciPy null fits and explicit
risk-set score/information sums. It covers zero-covariate and adjusted numeric
assays, reference-coded factors, missing predictors and ID reordering, offsets,
delayed entry, Efron/Breslow ties, extreme changes of units, a monotone alternative
with an admissible score test, genotype MAC20 filtering and score/covariance
export, full coefficient fits, fully observed data, and deliberate endpoint/null/
coefficient rejections. Keep the sibling `survival-workflow.py` available.
Frozen full and packaged acceptance passed: all 13 added tests, 13 standalone
cases with 78 independent comparisons, and the native/supplied survival and
resume/MR regressions. Exact counts, hashes, support/full-cone LPs, retained
evidence and boundaries are in the
[implementation checkpoint](../analysis-pipeline-implementation.md#adaptive-cox-and-standalone-score-workflows-2026-10-25).

## Grouped nested development validation for risk scores

Native quantitative and binary score development can now repeat preprocessing
and penalty selection inside nested validation folds. This addresses the
optimism that arises when the same validation errors both select a model and
describe its performance. The design follows the separation described by
[Varma and Simon](https://doi.org/10.1186/1471-2105-7-91) and the
[official grouped-validation guidance](https://scikit-learn.org/stable/modules/cross_validation.html#cross-validation-iterators-for-grouped-data).
It does not establish performance in a real population from software fixtures.

```yaml
risk_score:
  file: participants.tsv
  outcome: y
  family: quantitative             # or binary, with individual 0/1 outcomes
  features: [x0, x1]
  covariates: [age, sex]
  categorical_covariates: [sex]
  group_columns: [family]
  resampling_group: family
  missing: training_mean
  lambdas: [0.2, 0.02]              # descending; positive for binary training
  alpha: 0.0
  refit: true
  nested_validation:
    outer_folds: 5
    inner_folds: 5
    seed: 42
    maximum_fits: 10000
    maximum_fold_rows: 2000000
```

The registered `training` and `tuning` rows form one development pool in this
mode. Each outer fold holds out entire declared independence units. Inner folds
inside its training portion select lambda using pooled row MSE for quantitative
outcomes or individual Bernoulli log loss for binary outcomes. Every inner fit
learns categorical encoding, predictor imputation and standardization from that
fit's training rows. The selected model is refitted on the outer training portion
and assessed once on its outer holdout. Final inner validation selects lambda
using the whole development pool, then freezes a final model fitted to that pool.
The separately registered `evaluation` rows enter neither splitting nor selection.
They remain necessary for the existing external evaluation and related-phenotype
workflow. Omit `nested_validation` to retain the existing single tuning-partition
procedure. Nested validation requires native quantitative/binary `mode: train`
and `refit: true`; it does not silently add survival development.

With generated folds, sorted development-unit identities and the registered
seed determine a shuffled round-robin allocation. No outcomes determine the
allocation, and a failed fold is never redrawn. Groups have balanced counts,
while unequal group sizes can yield unequal row counts. The loss estimand is
explicitly row weighted; grouping protects split boundaries rather than assigning
equal weight to each group. Existing checks require the resampling unit to
contain all declared related groups. Different IDs or these checks alone do not
establish biological independence or exclude overlap with an external GWAS.

Alternatively supply `outer_fold_column` and/or `inner_fold_column`. Development
values must be integers `1..outer_folds` or `1..inner_folds`. A unit cannot cross
fold boundaries. The inner column is reused inside each outer training portion
and for final development selection, so every requested inner fold must remain
nonempty in each of those populations. Outer and inner columns must differ and
cannot be predictors, covariates, outcome, sample ID or partition fields. Values
on evaluation/excluded rows are not used. Register splits before looking at
performance; the pipeline does not search for a favorable split or seed.

Each training and assessment fold needs `minimum_samples` rows and at least two
independence units. Binary training folds need both classes. Binary assessment
log loss can be computed for one class, while the separate external AUC still
needs both classes. Missing training features, unseen assessment categories,
nonconvergence, empty folds and unrepresentable losses hold the entire native
score stage. A tiny positive squared error cannot be reported as zero after
underflow. The source table remains immutable, and completed audit prefixes are
retained without a successful-subset validation metric or frozen final model.

`maximum_fits` bounds the complete candidate-fit family, whose size is
`(outer_folds + 1) * (inner_folds * lambda_count + 1)`. Each candidate includes
the native full and baseline model work. `maximum_fold_rows` bounds the complete
local membership audit, `(outer_folds + 1) * development_rows`. Existing dense
storage/sample limits also apply. Binary optimizer work is shared across all
inner, outer and final fits, with native encoding/prediction accounting added.
Quantitative matrix-work accounting estimates fit/design cost; it does not count
actual solver CPU operations or elapsed time. Each quantitative solver retains
its own convergence bound. Evaluation bootstrap and related-test work is reserved
before nested fitting. A failing optimizer can consume its remaining allowance;
an incomplete report records the completed prefix rather than a complete-family
work total. No participant or candidate is dropped to fit the budget.

The operation writes:

- `nested-validation.yaml`: aggregate method, scope, loss, work and completion.
- `nested-tuning.tsv`: inner-fold losses and pooled selection losses for each
  outer training portion and the final development fit.
- `nested-fold-membership.tsv`, `nested-models/*.yaml` and
  `nested-outer-predictions.tsv`: local unit/participant memberships, outer model
  parameters and predictions for every development row held out once.
- `nested-evaluation.tsv`: the pooled outer development loss. Outer training
  sets overlap; their spread is not a confidence interval, and this development
  estimate is distinct from performance in the independent evaluation cohort.

The existing final score/baseline, weights, development identity sidecar, external
metrics and related-trait family remain separate artifacts. `tuning.tsv` labels
its final selection column `nested_inner_cv_mse` or `nested_inner_cv_log_loss`.
Frozen model metadata retains the native selection method and report hash.
`mode: model` applies that frozen model without retuning or rerunning nested
validation; its source method/hash is source-reported metadata, not a fresh audit
of the original validation. Original development identities remain in the overlap
check, including every training/tuning participant used for model selection.

Finding dossiers receive only a whitelist of aggregate nested context and the
report source. Participant/group labels, fold predictions, imputation means and
model vectors stay in local artifacts. Nested validation repeats native fitting
steps; it cannot repair upstream normalization, feature selection or sample
selection that already used held-out outcomes. Prespecify the feature universe
and record upstream provenance. Conditional bootstrap intervals for the final
external evaluation still omit training/model-selection uncertainty. No clinical
calibration, population transportability, privacy certification or causal claim
follows from this workflow.

The self-contained `examples/study-pipeline/nested-risk-score-workflow.py` runs
the actual executable and independently refits every inner/outer/final ridge
model, preprocessing plan, selection loss and prediction with NumPy/SciPy. It
checks quantitative/binary native development, replay, external-data isolation,
frozen reuse, aggregate finding context and deliberate split/category/class/work
holds. Frozen full and packaged acceptance passed: all 11 added tests, 14 nested
runs with 1,680 independent numerical comparisons, and the earlier score, Cox
and resume/MR regression workflows. Exact counts, source/JAR hashes, the retained
subnormal-loss counterexample, scope and boundaries are in the
[implementation checkpoint](../analysis-pipeline-implementation.md#grouped-nested-score-validation-2026-10-25).

## Frozen genetic scores from BED genotypes

Use `risk_score.mode: weights` with `genotypes` to apply a complete frozen
additive model directly to BED/BIM/FAM files. Participant metadata contain
outcomes, partitions, covariates and exact FID/IID mappings; they do not need one
column per SNP. This branch streams selected BED rows using the same native
`DiploidScores`/`PlinkBedDataset` engine as the standalone genotype score tool.
The dense training/application limit of 2,000 predictors does not apply here.
It applies supplied weights; it does not train an LD-aware polygenic score.

```yaml
risk_score:
  mode: weights
  file: score-participants.tsv
  weights: frozen-weights.tsv
  outcome: glucose
  baseline_column: frozen_baseline
  independent_rows: true  # only if records are independent
  external_independence: Independently developed model; document GWAS overlap and preprocessing
  related_phenotypes:
    - column: BMI
  genotypes:
    weights_genome_build: GRCh38
    sources:
      - {id: cohortA, bfile: genotype/cohortA, genome_build: GRCh38}
      - {id: cohortB, bfile: genotype/cohortB, genome_build: GRCh38}
    source_column: cohort
    fid_column: fid
    iid_column: iid
    coefficient_scale: additive
    missing: error
    minimum_observed_fraction: 0.98
    prediction_link: identity
    intercept: 1.2
    prediction_rationale: Frozen source intercept; coefficients and outcome in source units
    # Optional separately frozen full-model covariate linear component:
    # prediction_linear_column: frozen_covariate_linear_predictor
```

The weight table requires `chromosome, position, ea, oa, weight`; `variant` is an
optional audit label. `ea_frequency` is required for reference-mean imputation.
An optional `genome_build` column must agree in every row. Match keys are human
autosomal chromosome, positive position and the unordered resolved allele pair;
the effect allele determines whether BED A1 dosage is used or replaced by
`2 - dosage`. The lowercase `chr` prefix is accepted. Alleles must be uppercase
ACGT sequences, distinct and already normalized; indel normalization is not
performed. Variant aliases may differ when coordinates and alleles match exactly.
Duplicate model keys, ambiguous source loci, incompatible alleles, absent model
members, unresolved/nonautosomal alleles or mismatched builds hold the stage.
No partial model is promoted. A BED source must contain every model member and
every active participant's exact FID/IID pair; unused source participants do not
enter the score/evaluation population.

Build declarations must both be GRCh37 or GRCh38. They are caller declarations;
the workflow does not certify positions against a reference FASTA or infer strand
or liftover. Palindromic A/T and C/G model variants hold by default. An explicit
`allele_alignment: forward_declared` with `alignment_rationale` permits them only
when the caller documents the forward-strand convention. Nonadditive PGS flags
(dominant/recessive, haplotype/diplotype, interaction), dosage-specific weights or
conditional inclusion rules require an appropriate supplied-score workflow.
They are not silently simplified to additive effects.

CSV/TSV and gzipped CSV/TSV weights are read with bounded lines and decompressed
bytes. PGS-style comment preambles are accepted. Map logical to physical names
with `weight_columns`, for example `chromosome: hm_chr`, `position: hm_pos`,
`ea: effect_allele`, `oa: other_allele`, `weight: effect_weight`. Select the build
corresponding to those mapped coordinates; comments do not choose it for you.
`coefficient_scale` is `additive` by default, with explicit `log_odds`,
`odds_ratio`, `log_hazard` or `hazard_ratio` alternatives. Positive OR/HR values
are logged once. PGS Catalog `effect_weight` may already be a log coefficient;
do not label it as an OR merely because a separate OR annotation exists.
Consult the [official scoring format](https://www.pgscatalog.org/downloads/)
for source conventions and nonadditive fields.

The score is `(sum(beta * effect_allele_dosage) - score_center) / score_scale`;
the fixed defaults are center 0 and scale 1. It uses the sum, rather than an
allele-normalized average. See [PLINK's scoring documentation](https://www.cog-genomics.org/plink/2.0/score)
for the distinction. There is no target-derived centering, scaling, frequency
estimation, MAC filtering or model refit. The association default MAC 20 does not
remove frozen-model members. If missing calls are allowed, register
`missing: reference_mean`, external effect-allele frequencies and
`frequency_provenance`; missing calls become `2 * external_ea_frequency`.
Participant eligibility follows `minimum_observed_fraction`, fixed before
outcomes are evaluated. Fully imputed records have no measured genotype
information and are explicitly flagged; accepting them requires an explicit
threshold of zero. Do not use participant-specific variant omission.

For quantitative evaluation, `prediction_link: identity` requires an explicit
frozen intercept/rationale and the separate frozen baseline comparator. For
binary raw-score discrimination, use `family: binary`, `prediction_scale: raw`
and `prediction_link: none`; no probabilities are invented. For binary
probability metrics use `prediction_scale: probability`, `prediction_link: logit`,
a source intercept/rationale, and a frozen probability baseline. The optional
`prediction_linear_column` supplies an already frozen covariate contribution.
Target outcomes, IDs, partitions, groups and registered related phenotypes
cannot be that component. Case/control probabilities do not establish population
absolute risk or clinical calibration. Survival score evaluation/development
still requires a censoring-aware route; frozen scores can support appropriately
registered related-trait follow-up.

Metadata use globally unique record IDs plus source and exact FID/IID. Shared
IIDs across families/cohorts are allowed. Repeated physical genotypes within a
source require the same partition and declared resampling unit, and cannot be
declared independent rows. Existing group/partition and training-ID overlap
checks also apply. Neither unique IDs nor source labels prove unrelatedness or
exclude discovery-GWAS overlap. Each registered source must contribute complete
eligible evaluation records; the workflow holds instead of dropping a whole
cohort because its calls, outcomes or frozen predictions are unavailable.

Default bounds are 1,000,000 model variants, 50,000,000 scanned source variants,
1,000,000 samples per BED source, 1,000,000,000 decoded genotype cells shared
across sources, 256 MiB compressed/decompressed weights, 1,024 MiB BIM/FAM input
per source, estimated 512 MiB retained metadata per model/reader, and 65,536 bytes
per line. Set the corresponding `maximum_*` fields to tighter limits as needed.
The native BED reader checks dimensions, header, metadata, duplicate IDs and
selected-row padding. Genotypes and weights enter cache identity through their
content hashes, and hashes are checked again after application. Inputs are not
rewritten; large genotype inputs are referenced and hashed, with raw weights
snapshotted locally. Byte/hash scanning and metadata validation are additional
I/O work; the cell count describes decoded genotype rows, not total CPU work.

Local outputs include `genetic-model-coverage.tsv`,
`genetic-variant-alignment.tsv`, `genetic-participant-disposition.tsv`,
`genetic-score-application.yaml`, participant scores, held-out metrics and the
registered related-trait family. Failed prerequisites retain local diagnostics
and completed alignment/coverage prefixes without official participant scores
or evaluation metrics. The finding inventory forwards whitelisted aggregate
model/application context only; genotype vectors and sample identities stay
local. A caller's model/strand/frequency/independence statements remain source
declarations requiring scientific appraisal.

Previously completed performance or related-trait families can still be supplied
through typed `supplied_results`. Supply frozen participant scores when only
related testing is missing. If both stages are supplied, the study does not open
the genotype files or reconstruct the score. This preserves the laboratory's
complete-only-missing-work contract.

`genetic-risk-score-workflow.py` generates offline multi-cohort BED data, aliases,
reversed alleles, shared IIDs, MAC-1 members, mapped gzip/OR weights, sample QC,
binary raw/logit evaluation, deliberate whole-stage holds and supplied-stage
reuse. Its NumPy/SciPy oracle checks every retained score/prediction and primary
metric plus related-test coefficients/uncertainty. Validation and remaining
format/training limits are recorded in the
[implementation checkpoint](../analysis-pipeline-implementation.md#frozen-genetic-score-application-2026-10-25).

## Frozen scores from VCF, BCF and BGEN

The complete frozen-weight study route also accepts VCF, compressed VCF, BCF and
BGEN, and can combine them with BED cohorts in one registered evaluation. The
same model, source-unit normalization, frozen prediction, observed-call
eligibility, held-out metrics and separate related-trait family apply. The new
source fields extend the [BED contract](#frozen-genetic-scores-from-bed-genotypes):

```yaml
genotypes:
  weights_genome_build: GRCh38
  source_column: cohort
  iid_column: genotype_id
  sources:
    - {id: cohortA, bfile: genotype/A, genome_build: GRCh38}
    - {id: cohortB, file: genotype/B.vcf.gz, genome_build: GRCh38}
    - id: cohortC
      file: genotype/C.bgen
      genome_build: GRCh38
      samples: genotype/C.sample  # only needed without embedded BGEN IDs
  prediction_link: identity
  intercept: 1.2
  prediction_rationale: Frozen quantitative source intercept and original units
```

Each source requires exactly one `bfile` or `file`. Supported inferred suffixes
are `.vcf`, `.vcf.gz`, `.vcf.bgz`, `.bcf` and `.bgen`. For an unconventional
filename, set `format: VCF`, `VCF_GZ`, `BCF` or `BGEN` explicitly. BED continues
to match exact source/FID/IID tuples. Variant-file headers contain one unique
sample ID per column, matched through `iid_column`; FID is not used for those
sources. Identical IIDs in different sources are disambiguated by `source_column`.
Repeated records for a physical source/IID require the same partition and
declared resampling unit and cannot be declared independent. Every active
metadata record must have an exact genotype match; a silent population
intersection is not substituted.

Keep supplied external development/training IDs in the same global record-ID
namespace used by the local overlap check, or retain an explicit identity map
before interpreting a match/non-match. Source labels and different local record
IDs do not establish independent people or exclude discovery-GWAS overlap.

VCF/BCF use declared FORMAT/DS when available, falling back to called GT. GP/PL
fields alone do not supply dosage in this reader. BGEN layout 2 supplies expected
ALT dosage from phased/unphased probabilities; its existing decoder supports
uncompressed, zlib and zstd data. The score counts ALT or `2 - ALT` according to
the model effect allele. It does not infer strand or change the frozen model.
Human autosomal, exact-build and complete-allele requirements remain in force.

A selected multiallelic decomposition holds: complementing one ALT dosage would
otherwise also count other ALT alleles. An observed non-diploid genotype in an
active participant also holds. Finite DS with unknown observed ploidy requires
the source's explicit `diploid_dosage_declaration`, for example a documented
autosomal diploid imputation export with GT withheld. That statement remains a
caller declaration. Unknown ploidy for a missing call does not establish an
observed haploid genotype. The local alignment audit and aggregate context
record assumed-diploid dosage cells. They do not certify biological karyotype.
Unselected VCF/BCF multiallelic rows can be decoded and counted; the current BGEN
reader requires biallelic source records throughout the file.

BGEN files without embedded IDs require `samples`: an Oxford two-header-line
`.sample` file or a headerless sample-ID list using the existing sample parser.
Oxford files use ID_2. A sidecar supplied alongside embedded IDs must match their
ordered identities exactly; it cannot override a conflict. Sidecars are bounded,
hashed and included in cache identity. Reordering VCF/BCF/BGEN sample headers
preserves the matched participant ordering.

Before genotype decoding, a separate admission scan checks bounded headers,
record framing, decompressed sizes, sample dimensions and the complete source
inventory. BCF 2.1/2.2 are streamed with compression detected from file magic,
including standard BGZF `.bcf` files. This fixes the former suffix-based core
reader path, which could send gzip bytes straight to the BCF codec. The direct
core reader also supports ordinary gzip streams. The scoring admission scan is
specific to this study route; it does not add these resource checks to every
other `VariantSources` consumer.

The scan then admits the whole operation's registered cell/work budgets before
score accumulation. Sequential sources count **all source samples times every
emitted ALT row**, including nonmodel variants and unused source participants.
The entire stream is exhausted, even after all model members have matched, to
detect later duplicate loci and malformed trailing data. BED seeks model rows,
so its decoded-cell contribution is source samples times model variants. Source
inventory work units sum `max(physical records, emitted ALT rows)` per sequential
source and BIM rows per BED source; `source_variants_scanned` reports that budget
counter. Per-source reports retain both physical and emitted counts where they
differ. No score, cohort or test prefix is promoted after an incomplete source.

Additional defaults are `maximum_source_mib: 65536` per raw genotype file,
`maximum_decompressed_mib: 131072` per sequential source,
`maximum_header_mib: 64` and `maximum_record_mib: 64`. The existing one-billion
cell default remains, but an explicit `maximum_cells` can now admit up to
50 trillion cells with long counters. Metadata/record sizes and retained-header
estimates are admission limits, not a JVM heap or elapsed-time guarantee.
Preflight, hashing and probability decompression add I/O/CPU work beyond the
decoded-cell counter. For large sequential files, register adequate budgets or
supply an independently prepared source subset containing every frozen-model
member. Do not select that subset using target outcomes or a target MAC cutoff.

The new `source_format`, `counted_allele` and `ploidy_status` alignment columns
make orientation explicit. Legacy `source_a1`/`source_a2` fields represent BED
A1/A2 or variant-file REF/ALT; the counted allele is BED A1 or file ALT before
the recorded reversal. Generic source-format and assumed-diploidy counts enter
the finding inventory; sample IDs, genotype vectors and free-text source
declarations remain local. Before/after hashes bind source and sample sidecars.
Typed supplied performance or related families still skip their completed work;
both supplied stages can be used with missing genotype files. When only related
testing is missing, frozen genotypes/weights can be reapplied without evaluating
the outcome again, or frozen participant scores can be supplied directly.

`variant-risk-score-workflow.py` provides independent raw-byte VCF, BCF2.1/2.2
and BGZF, and BGEN2 probability encoders, plus NumPy/SciPy score, prediction,
performance and related-test checks. It exercises mixed formats, explicit and
unknown ploidy, compression/phasing, sidecars, whole-source budgets, duplicate
and missing loci, complete supplied-stage reuse and genotype-cache mutation.
It requires the sibling `genetic-risk-score-workflow.py` fixture helper. The
[implementation checkpoint](../analysis-pipeline-implementation.md#variant-format-frozen-score-application-2026-10-25)
records source/JAR identities and exact acceptance evidence. This is frozen model
application, not native genome-wide LD-aware model training. PGEN and additional
ploidy/nonadditive scoring models still require an appropriate supplied-score
workflow.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as JLinAlg when reporting results.

- [Aravind Subramanian et al. (2005) — Gene set enrichment analysis: a knowledge-based approach for interpreting genome-wide expression profiles](../CITATIONS.md#subramanian-gsea-2005) — [PMID: 16199517](https://pubmed.ncbi.nlm.nih.gov/16199517/) · [PMCID: PMC1239896](https://pmc.ncbi.nlm.nih.gov/articles/PMC1239896/)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)
- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [Adam Frankish et al. (2021) — GENCODE 2021](../CITATIONS.md#frankish-gencode-2021) — [PMID: 33270111](https://pubmed.ncbi.nlm.nih.gov/33270111/) · [PMCID: PMC7778937](https://pmc.ncbi.nlm.nih.gov/articles/PMC7778937/)
- [Gene Ontology Consortium (2021) — The Gene Ontology resource: enriching a GOld mine](../CITATIONS.md#go-consortium-2021) — [PMID: 33290552](https://pubmed.ncbi.nlm.nih.gov/33290552/)
- [Marija Milacic et al. (2024) — The Reactome Pathway Knowledgebase 2024](../CITATIONS.md#milacic-reactome-2024) — [PMID: 37941124](https://pubmed.ncbi.nlm.nih.gov/37941124/) · [PMCID: PMC10767911](https://pmc.ncbi.nlm.nih.gov/articles/PMC10767911/)
- [1000 Genomes Project Consortium (2015) — A global reference for human genetic variation](../CITATIONS.md#thousand-genomes-2015) — [PMID: 26432245](https://pubmed.ncbi.nlm.nih.gov/26432245/) · [PMCID: PMC4750478](https://pmc.ncbi.nlm.nih.gov/articles/PMC4750478/)
- [U.S. Food and Drug Administration (2024) — Electronic Systems, Electronic Records, and Electronic Signatures in Clinical Investigations: Questions and Answers](../CITATIONS.md#fda-electronic-records-2024)
- [U.S. Department of Health and Human Services, Office for Civil Rights (2012) — Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule](../CITATIONS.md#hhs-hipaa-deidentification-2012)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)
- [Hannah Wand, Samuel A. Lambert, et al. (2021) — Improving reporting standards for polygenic scores in risk prediction studies](../CITATIONS.md#wand-prs-reporting-2021) — [PMID: 33692554](https://pubmed.ncbi.nlm.nih.gov/33692554/) · [PMCID: PMC8609771](https://pmc.ncbi.nlm.nih.gov/articles/PMC8609771/)
- [Arthur E. Hoerl and Robert W. Kennard (1970) — Ridge regression: biased estimation for nonorthogonal problems](../CITATIONS.md#hoerl-kennard-1970)
- [Hui Zou and Trevor Hastie (2005) — Regularization and variable selection via the elastic net](../CITATIONS.md#zou-hastie-2005)
- [M. Varadi et al. (2022) — AlphaFold Protein Structure Database: massively expanding the structural coverage of protein-sequence space with high-accuracy models](../CITATIONS.md#varadi-alphafold-2022)
- [UniProt Consortium (2026) — UniProt API queries](../CITATIONS.md#uniprot-api-documentation)
- [Alexander Lex, Nils Gehlenborg, Hendrik Strobelt, Romain Vuillemot and Hanspeter Pfister (2014) — UpSet: Visualization of Intersecting Sets](../CITATIONS.md#lex-upset-2014)
- [Julian P. T. Higgins and Simon G. Thompson (2002) — Quantifying heterogeneity in a meta-analysis](../CITATIONS.md#higgins-thompson-2002) — [PMID: 12111919](https://pubmed.ncbi.nlm.nih.gov/12111919/)
- [Jonathan J. Deeks, Julian P. T. Higgins, Douglas G. Altman, Joanne E. McKenzie and Areti Angeliki Veroniki (2024) — Chapter 10: Analysing data and undertaking meta-analyses](../CITATIONS.md#cochrane-meta-analysis-2024)
- [Gao Wang, Abhishek Sarkar, Peter Carbonetto, and Matthew Stephens (2020) — A simple new approach to variable selection in regression, with application to genetic fine mapping](../CITATIONS.md#wang-susie-2020) — [PMID: 37220626](https://pubmed.ncbi.nlm.nih.gov/37220626/) · [PMCID: PMC10201948](https://pmc.ncbi.nlm.nih.gov/articles/PMC10201948/)
- [Chris Wallace (2021) — A more accurate method for colocalisation analysis allowing for multiple causal variants](../CITATIONS.md#wallace-coloc-2021) — [PMID: 34587156](https://pubmed.ncbi.nlm.nih.gov/34587156/) · [PMCID: PMC8504726](https://pmc.ncbi.nlm.nih.gov/articles/PMC8504726/)
- [Claudia Giambartolomei et al. (2014) — Bayesian test for colocalisation between pairs of genetic association studies using summary statistics](../CITATIONS.md#giambartolomei-coloc-2014) — [PMID: 24830394](https://pubmed.ncbi.nlm.nih.gov/24830394/) · [PMCID: PMC4022491](https://pmc.ncbi.nlm.nih.gov/articles/PMC4022491/)
- [Yoav Benjamini and Daniel Yekutieli (2001) — The control of the false discovery rate in multiple testing under dependency](../CITATIONS.md#benjamini-yekutieli-2001)
- [Wigginton JE, Cutler DJ, Abecasis GR (2005) — A note on exact tests of Hardy-Weinberg equilibrium](../CITATIONS.md#wigginton-hwe-2005) — [PMID: 15789306](https://pubmed.ncbi.nlm.nih.gov/15789306/) · [PMCID: PMC1199378](https://pmc.ncbi.nlm.nih.gov/articles/PMC1199378/)
- [PLINK developers (2026) — PLINK 2.0 input filtering and basic statistics](../CITATIONS.md#plink2-qc-documentation)
- [BGEN developers (2026) — The BGEN format: layout 2 specification](../CITATIONS.md#bgen-layout2-specification)
- [REGENIE developers (2026) — REGENIE overview: quantitative and binary association methods](../CITATIONS.md#regenie-binary-methods)
- [David Firth (1993) — Bias reduction of maximum likelihood estimates](../CITATIONS.md#firth-bias-reduction-1993)
- [Georg Heinze and Michael Schemper (2002) — A solution to the problem of separation in logistic regression](../CITATIONS.md#heinze-schemper-separation-2002) — [PMID: 12210625](https://pubmed.ncbi.nlm.nih.gov/12210625/)
- [Georg Heinze and logistf contributors (2026) — logistf: Firth logistic regression and constrained penalized likelihood tests](../CITATIONS.md#logistf-reference-documentation)
- [R Core Team and survival contributors (2026) — Fisher's exact test: fixed margins and probability-ordering two-sided tails](../CITATIONS.md#r-fisher-exact-documentation)
- [R Core Team and survival contributors (2026) — Conditional logistic regression: exact conditional likelihood](../CITATIONS.md#r-survival-clogit-documentation)
- [Jerome Friedman, Trevor Hastie and Robert Tibshirani (2010) — Regularization Paths for Generalized Linear Models via Coordinate Descent](../CITATIONS.md#friedman-glmnet-2010) — [PMID: 20808728](https://pubmed.ncbi.nlm.nih.gov/20808728/) · [PMCID: PMC2929880](https://pmc.ncbi.nlm.nih.gov/articles/PMC2929880/)
- [Hilary K. Finucane et al. (2015) — Partitioning heritability by functional annotation using genome-wide association summary statistics](../CITATIONS.md#finucane-sldsc-2015) — [PMID: 26414678](https://pubmed.ncbi.nlm.nih.gov/26414678/) · [PMCID: PMC4626285](https://pmc.ncbi.nlm.nih.gov/articles/PMC4626285/)
- [Jesse Hemerik and Jelle Goeman (2018) — Exact testing with random permutations](../CITATIONS.md#hemerik-goeman-2018) — [PMID: 30930620](https://pubmed.ncbi.nlm.nih.gov/30930620/) · [PMCID: PMC6405018](https://pmc.ncbi.nlm.nih.gov/articles/PMC6405018/)
- [Seunggeun Lee et al. (2012) — Optimal unified approach for rare-variant association testing](../CITATIONS.md#lee-skato-2012) — [PMID: 22863193](https://pubmed.ncbi.nlm.nih.gov/22863193/) · [PMCID: PMC3415556](https://pmc.ncbi.nlm.nih.gov/articles/PMC3415556/)
- [Yaowu Liu et al. (2019) — ACAT: A fast and powerful p value combination method for rare-variant analysis in sequencing studies](../CITATIONS.md#liu-acat-2019) — [PMID: 30849328](https://pubmed.ncbi.nlm.nih.gov/30849328/) · [PMCID: PMC6407498](https://pmc.ncbi.nlm.nih.gov/articles/PMC6407498/)
- [Douglas G Altman and J Martin Bland (2003) — Interaction revisited: the difference between two estimates](../CITATIONS.md#altman-bland-interaction-2003) — [PMID: 12543843](https://pubmed.ncbi.nlm.nih.gov/12543843/) · [PMCID: PMC1125071](https://pmc.ncbi.nlm.nih.gov/articles/PMC1125071/)
- [Matthew C Keller (2014) — Gene x environment interaction studies have not properly controlled for potential confounders: the problem and the (simple) solution](../CITATIONS.md#keller-interaction-confounding-2014) — [PMID: 24135711](https://pubmed.ncbi.nlm.nih.gov/24135711/) · [PMCID: PMC3859520](https://pmc.ncbi.nlm.nih.gov/articles/PMC3859520/)
- [Ani Manichaikul et al. (2010) — Robust relationship inference in genome-wide association studies](../CITATIONS.md#manichaikul-king-2010) — [PMID: 20926424](https://pubmed.ncbi.nlm.nih.gov/20926424/) · [PMCID: PMC3025716](https://pmc.ncbi.nlm.nih.gov/articles/PMC3025716/)
- [Alkes L Price et al. (2006) — Principal components analysis corrects for stratification in genome-wide association studies](../CITATIONS.md#price-pca-stratification-2006) — [PMID: 16862161](https://pubmed.ncbi.nlm.nih.gov/16862161/)
- [Genome Reference Consortium (2026) — Human Genome Overview: GRCh38 and GRCh37 pseudoautosomal regions](../CITATIONS.md#grc-human-par-intervals)
- [PLINK developers (2026) — PLINK 2.0 human chromosome ploidy and dosage input](../CITATIONS.md#plink2-human-ploidy-documentation)
- [Peter Langfelder et al. (2011) — Is My Network Module Preserved and Reproducible?](../CITATIONS.md#langfelder-preservation-2011)
- [C. J. Clopper and E. S. Pearson (1934) — The Use of Confidence or Fiducial Limits Illustrated in the Case of the Binomial](../CITATIONS.md#clopper-pearson-1934)
- [Katherine Chao, Michael Wilson, Julia Goodrich and the gnomAD Production Team (2024) — gnomAD v4.1: allele numbers across called sites and joint frequencies](../CITATIONS.md#gnomad-v4-1-allele-number)
- [REGENIE developers (2026) — REGENIE documentation: external AAF file and mask definitions](../CITATIONS.md#regenie-mask-frequency-documentation)
- [D. R. Cox (1972) — Regression models and life-tables](../CITATIONS.md#cox-1972)
- [Bradley Efron (1977) — The efficiency of Cox's likelihood function for censored data](../CITATIONS.md#efron-1977)
- [Terry M. Therneau and survival contributors (2026) — survival: Cox proportional hazards regression and survival endpoint documentation](../CITATIONS.md#r-coxph-survival-documentation)
- [Terry M. Therneau and survival contributors (2026) — survival: proportional-hazards efficient score tests and transforms](../CITATIONS.md#r-cox-zph-documentation)
- [Ming-Hui Chen, Joseph G. Ibrahim and Qi-Man Shao (2009) — Maximum likelihood inference for the Cox regression model with applications to missing covariates](../CITATIONS.md#chen-ibrahim-shao-2009)
- [Georg Heinze and Michael Schemper (2001) — A solution to the problem of monotone likelihood in Cox regression](../CITATIONS.md#heinze-schemper-2001-cox)
- [Sudhir Varma and Richard Simon (2006) — Bias in error estimation when using cross-validation for model selection](../CITATIONS.md#varma-simon-nested-cv-2006)
- [scikit-learn developers (2026) — Cross-validation: nested model selection and grouped observations](../CITATIONS.md#sklearn-grouped-nested-cv)
- [PLINK 2 developers (2026) — Linear scoring](../CITATIONS.md#plink2-linear-scoring)
- [PGS Catalog team (2026) — PGS Catalog download information and scoring file format](../CITATIONS.md#pgs-catalog-scoring-format)
- [Global Alliance for Genomics and Health file-format contributors (2026) — The Variant Call Format specification: VCFv4.5 and BCFv2.2](../CITATIONS.md#hts-vcf-bcf-specification)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->


## Molecular assay QC and supplied decisions

Supported bulk molecular matrix consumers now receive a separate `assay_qc`
producer before participant selection, association (including Cox), native
correlation networks, adjusted subgroup interactions and direct-matrix risk
scores. The stage reads a feature-by-sample CSV/TSV and its optional paired
measurement-detection table. It reads no phenotype or outcome. Matching dataset
and resolved policy share one source producer across hypotheses. A completed
supplied analysis or metadata-only supplied graph does not trigger raw assay QC.
A standalone manual `goals: [assay_qc]` is also supported.

The default is **audit**: retain all samples/features and record proposed QC
flags. Explicit `mode: filter` materializes a new matrix; original input bytes
remain unchanged. These generic defaults are starting controls to review against
the assay protocol, not validated thresholds for every molecular platform:

```yaml
analysis:
  mode: manual
  goals: [association, network_analysis]
  association: {model: ols}
  assay_qc:
    source: native
    mode: filter
    value_scale: continuous
    maximum_feature_missing: 0.2
    maximum_sample_missing: 0.2
    minimum_observed: 3
    minimum_sd: 0
    drop_constant: true
    invalid_values: hold
    detection_kind: none
```

`value_scale` accepts `unspecified`, `continuous`, `nonnegative`, `counts` or
`proportion`. All retained numeric values must be finite; the latter three add
range/integer checks. Counts must be exact integers no larger than
9,007,199,254,740,991 per cell. Count totals use exact integer arithmetic, including
an overflow-safe accumulator, rather than rounded floating point sums. This is
measurement QC, not count-model normalization. Standard empty/NA/NaN/null/dot
cells remain missing. Invalid/nonrepresentable values hold the stage by default.
Only explicit `mode: filter, invalid_values: mask` replaces invalid native cells
with `NA`; it never rounds counts, clips values or imputes measurements.

Sample missingness always uses **every original source feature**. Feature QC
always uses **every original source sample**. Decisions are made once; removing
features does not recompute sample denominators, and removing samples does not
restart feature selection. Source missingness, proposed masking, feature means,
sample SDs, scaled moments, exact constant flags and log SDs are local audit
outputs. A rounded zero SD is not used to infer an exactly constant feature.
Source statistics do not describe a phenotype-specific model's actual N.

For platform detection, register `files.assay_detection` with identical sample
IDs/order and feature IDs/order. `detection_kind: p_value` passes a cell at
`p <= detection_threshold` (default 0.01); `detected` accepts only binary 0/1 and
passes 1. Detection bounds and cutoff comparisons preserve the decimal source value,
including probabilities below double precision and values just outside [0,1].
Missing detection fails detection. Reordered, missing, extra or invalid
detection records hold the whole stage. Audit mode records flags without changing
measurements; filter mode masks failed cells and applies the fixed QC rules.

Already completed lab QC can supply decisions for the **current supplied matrix**:

```yaml
assay_qc:
  source: supplied
  supplied_report: inputs/lab-qc.yaml
  supplied_samples: inputs/lab-sample-decisions.tsv
  supplied_features: inputs/lab-feature-decisions.tsv
  dataset_overrides:
    proteins: {value_scale: nonnegative}
```

The report must identify `schema_version: 1`, `dataset_id`, exact current
`matrix_sha256`, `sample_count`, `feature_count`, `input_state: before_qc` or
`post_qc`, and `method: {name: ..., version: ..., parameters: {...}}`. Each TSV/CSV
decision table has `sample_id` or `feature_id`, `decision: keep|exclude`, and an
optional `reason`. Complete exact coverage of the current matrix is mandatory;
missing, extra or duplicate IDs are not silently intersected. A post-QC matrix can
keep every currently supplied ID without recovering unavailable original raw
files. Upstream exclusions remain source-reported history. Excluded feature/sample
cells do not need to pass the current retained-value contract. Kept cells do.

Supplied reports and decisions are copied and hashed. Native thresholds and
moments are **not recomputed** for supplied decisions, and inherited native
controls are not represented as executed. Reuse validates identity, coverage,
structure and retained numeric values; it does not independently certify the
lab's methods. Only aggregate source-population QC context enters finding
appraisal; decision reasons and participant/feature vectors stay local.

The producer seals `assay-qc.yaml`, `assay-feature-qc.tsv`,
`assay-sample-qc.tsv` and, when applicable, `assay-matrix.tsv`. Consumers verify
its completed state, producer ID, dataset, resolved policy, original source hash
and all sealed output hashes. Invalid or incomplete source scopes and zero
retained samples/features hold consumers. Separate policies for one dataset get
separate producers. `enabled: false` explicitly bypasses this stage. Supplied
association/enrichment/MR/network results retain their own historical populations;
current source QC does not retrospectively validate them.

No imputation occurs in assay QC. Association already has its own within-analysis
mean-imputation behavior, while score imputation/scaling is learned inside the
appropriate development fit/fold. Score features remain independently prespecified:
if QC excludes a required predictor, the entire score is held instead of being
refitted on a favorable feature intersection. Fixed source denominators prevent
validation-driven feature selection from changing training sample QC. Outcome-free
QC alone does not prove statistical independent filtering, assay chemistry
validity, model validity, FDR calibration or independent external validation.

`maximum_features` (2,000,000), `maximum_samples` (1,000,000), `maximum_cells`
(1,000,000,000), `maximum_source_mib` (65,536), `maximum_record_mib` (64) and
`maximum_retained_mib` (512) bound source scans and estimated retained metadata.
The actual admissible combination can be smaller than each separate maximum.
This adapter covers plain CSV/TSV bulk matrices; platform chemistry, batch effects,
normalization, contamination and donor/cell-aware models require their own
reviewed preparation or compatible supplied results.

Run the independent generated-data acceptance with:

```powershell
python examples/study-pipeline/assay-qc-workflow.py run NEW_DIRECTORY --jar JAR
python examples/study-pipeline/assay-qc-workflow.py verify DIRECTORY --jar THE_PINNED_JAR
```

It exercises audit/filter/detection/count/supplied QC, incomplete/invalid input
holds, actual association/Cox/network/interaction consumers, required-predictor holds and
score development isolation. NumPy/SciPy independently reconstruct source moments,
fixed missingness, retained matrices and OLS effects. These generated examples
are not biological or clinical validation.

## Completed analyses through manuscript

The source build accepts standalone `network_modules` and
`module_gene_ontology` artifacts in addition to graphs and the other typed
completed stages above. A completed module GO import needs neither its original
graph nor a raw assay matrix, membership export or local GO database. JLinAlg
retains its original testing family and adjusted values; it does not reconstruct
or certify the source analysis. An entirely analyzed study can use `datasets: []`
and explicit manual goals, then continue with finding inventory, impact review
and manuscript work. Supply each completed stage under its actual result type.

For example, this block reuses completed memberships and computes only missing
module GO, using an independently declared complete background:

```yaml
# Inside hypothesis.analysis; declare the project-local GO resource separately.
mode: manual
goals: [network_modules, module_gene_ontology]
supplied_results:
  - id: completed-modules
    kind: network_modules
    files:
      results: inputs/memberships.tsv
      nodes: inputs/all-network-nodes.tsv
      feature_annotation: inputs/feature-annotation.tsv
    scope: {phenotype: BMI, species: human}
    method: {name: Original lab module analysis, version: recorded-source-version}
    limitations: [Original graph estimation and module stability require review.]
```

Memberships need `node_id` and `module_id`; `module_type` defaults to
`supplied_module`. A node may belong to overlapping modules. Identity includes
module type, so a community and a clique sharing the same local module ID
remain separate. The optional `network_id` also namespaces supplied identities.
To calculate missing native GO, use a single compatible network scope and supply
the full node universe, including isolates, plus compatible feature-to-gene
mapping. Memberships alone cannot establish the background. Missing background
leaves only GO waiting. Separate multiple network scopes before native GO rather
than pooling their backgrounds implicitly. An optional graph `depends_on` binds
memberships to its verified graph and rejects a conflicting node universe.

When GO is already complete, add a separate supplied artifact:

```yaml
  - id: completed-module-go
    kind: module_gene_ontology
    files: {results: inputs/module-go.tsv}
    scope: {phenotype: BMI, species: human}
    method: {name: Original lab module GO analysis}
    family:
      id: original-all-module-term-tests
      coverage: selected_only
      tested_count: 1000
      adjustment: BH
      adjusted_p_from_full_family: true
    limitations: [Original mapping and enrichment background require review.]
```

The table needs `module_id`, `term_id`, `p_value` and, for this selected-only
example, the source `adjusted_p`. Replace the example denominator with the
original full testing count. Missing count summaries remain `NA`. Reported
counts must satisfy integer, subset and feasible-overlap bounds. Optional finite
`log_p_value` and `log_adjusted_p` use natural logs. Complete-family adjustment
uses those logs, preserving source precision below floating-point P-value range.
A rounded zero without a usable logarithm cannot authorize a new BH/BY/Bonferroni
correction. Supplied full-family adjusted values remain source results. Do not
recalculate correction over a selected export. Partial, incompatible or
unresolved module imports remain held, with their source snapshots retained;
they do not trigger an unrequested graph reconstruction.

Finding context retains the source testing denominator, correction, coverage,
module identity and source limitations. The impact step compares the complete
eligible candidate inventory and records reasons for three to five distinct
principal stories. A manuscript revision can bind each story's main-text and
supplementary claims to the same frozen findings and source passages. The
default 8,000 words remains a main-text planning target; an explicitly strict
limit is separate. See [claim-bound discussion](#claim-bound-principal-finding-discussion)
for the eight depth facets and scientific review requirements. Mechanical
source matching does not establish that a proposed mechanism follows from the
paper or that the discussion meets the OAS1 depth benchmark.

Run the self-contained generated-data workflow with NumPy, SciPy and PyYAML:

```powershell
python -B examples/study-pipeline/completed-study-workflow.py run NEW_DIRECTORY --jar JAR
python -B examples/study-pipeline/completed-study-workflow.py verify DIRECTORY --jar THE_PINNED_JAR
```

It exercises entirely supplied analyses through native inventory, protocol
impact selection and drafting; missing GO with independent hypergeometric/BH
checks; late background arrival, immutable history and cache reuse; deliberate
scope/completeness/precision holds; and an authored three-story, 24-claim main
and supplementary revision. Its loopback responder, papers, data and prose are
synthetic. It verifies software contracts and bindings, not real AI appraisal,
source authenticity, biological conclusions or discussion quality.

## Missing-model review and explicit continuation

A native association without a model remains blocked, including a blank or
null model value. Its participant-QC dependency requests model review before
interpreting an undeclared outcome family or selecting modeled samples.
Standalone descriptive QC continues under its declared outcome/endpoint
contract. Supplied and configured association operations do not impose a
separate native-model requirement on that QC. The source build retains
`model-review.yaml`, audited recent
literature queries, conditional candidate models and a scientist question.
It does not fit a model merely because a candidate or paper is available.
Supplying a family alone does not choose an estimator or dependence model.

The review uses explicit public phenotype labels when configured and controlled
terms for known dataset types. Public `literature_terms` can describe a more
specific assay, such as isoform ratios, without copying dataset descriptions.
Declared Gaussian, binary, count, positive-continuous and survival endpoints
produce different candidate/search contexts. GRM/pedigree presence, declared
relatedness, repeated measures, random formula terms and conditional strata
contribute controlled design terms. Their paths, column/group names and values
are excluded from queries. Missing declarations do not establish independence
or cause a Gaussian family to be inferred.

```yaml
analysis:
  association:
    family: gaussian
    # model is deliberately absent until the scientist resolves the question
  bioinformatics:
    phenotype_terms: [body mass index]
    literature_terms: [RNA splicing, isoform ratio]
```

Public label lists are bounded to ten entries of at most 150 characters each.
The review retains up to two queries: recent phenotype/method papers, followed
by an assay/endpoint/design query when such context is available. Each query
requests at most 20 primary-publication candidates and sorts by publication
date using the [Europe PMC REST API](https://europepmc.org/RestfulWebService).
Searches obey the existing network/download/disclosure policy. Offline or
denied retrieval still preserves the query and missing-model question.

Candidate configuration names use the executable's `ols`, `lmm`, `glm`, `glmm`
and `cox` vocabulary. REML is estimation terminology for a justified linear
mixed model. A context match still requires review of assumptions, covariates,
sampling and calibration. The native study Cox route requires independent
participants; a dependence-aware survival proposal needs a suitable supported
configured or supplied analysis. Unknown or conflicting endpoints retain
conditional alternatives and the blocking question.

Paper identifiers, dates, search links and response audits remain available.
Duplicate identifiers retain both query links; unidentified records remain
explicit. Provider failures, invalid response metadata and bounded/truncated
retrieval cannot stand for completed scientific appraisal. Search matches do
not establish that a paper used or supports a suggested model. Model review
uses declared metadata; ordinary local QC stages follow their own input policy.

```powershell
python examples/study-pipeline/model-review-workflow.py run NEW_DIRECTORY --jar JAR
python examples/study-pipeline/model-review-workflow.py verify NEW_DIRECTORY --jar THE_PINNED_JAR
```

The example requires NumPy, SciPy and PyYAML. Eleven synthetic profiles cover
quantitative/related, binary GWAS, count, positive-continuous, survival, unknown,
categorical outcomes with no declared family, public assay labels, blank-model
and policy-denied cases. Every initial run
must stop without association. One explicit OLS amendment then completes,
checking beta/SE/P/BH against SciPy while preserving the earlier attempt.
Approval events are synthetic protocol fixtures. No public-provider or AI
request, actual scientific approval or model-suitability appraisal is claimed.

## Literature coverage in finding dossiers

Each final finding dossier carries a source-linked `literature_coverage`
record when literature was requested or supplied for its hypothesis. It keeps
native retrieval allocation separate from query receipts. A target outside the
native review cap can still have completed searches supplied by the scientist.
Pending allocation, missing annotation, failed or limited retrieval, bounded
queries reporting no matches, retrieved candidates and bibliography-only
inputs have distinct dispositions. None establishes novelty, absence of prior
work, an exhaustive review or scientific support for the finding.

The record preserves original statistical/review ranks, selection basis,
per-strategy completion, dates, source identifiers and declared scope. Exact
supplied query receipts reused by a native stage count once, with both artifact
links retained; native pages are grouped into their logical query. Gene-only
searches remain declared gene context and do not become isoform-specific
evidence. Conflicting identities and malformed or incomplete receipts remain
explicit. The existing article observations remain available for appraisal.

Coverage does not change significance, testing-family denominators or impact
eligibility. Its required caveat accompanies the finding through final impact
comparison, the selected-finding manuscript payload and editorial review.
The summary counts finding rows by coverage state; these counts are not
independent searches or publications. Allocation and query records are indexed
once per producer for large result inventories.

```powershell
python examples/study-pipeline/literature-coverage-workflow.py run NEW_DIRECTORY --jar JAR
python examples/study-pipeline/literature-coverage-workflow.py verify NEW_DIRECTORY --jar THE_PINNED_JAR
```

Keep `literature-priority-workflow.py` beside this example. NumPy, SciPy and
PyYAML are required. Four synthetic cases check native nonselection with and
without prior supplied searches, a completed supplied literature stage and
pending allocation. The verifier checks numeric preservation, source hashes,
required caveats, reuse deduplication and retained dispositions. No external
search or AI request occurs; these fixtures do not establish scientific source
support or discussion quality.

## Provisional impact before the literature cap

Native literature retrieval separates statistical eligibility from allocation
of review slots. It first retains every unambiguously mapped target passing the
original full-family threshold. If that pool exceeds the configured cap, an
opt-in provisional impact review can assess all targets before selecting the
ones to retrieve. A less extreme P value can therefore receive a review slot.
This does not change association P values, source FDR, primary results or the
later full-evidence selection of three to five manuscript stories.

```yaml
assistance:
  enabled: true
  impact: true
  provider: local
  model: your-local-model
  endpoint: http://127.0.0.1:8080/v1/chat/completions
  impact_batch_size: 8
  impact_max_calls: 100
  impact_max_payload_chars: 120000
# Inside hypothesis.analysis.bioinformatics:
literature_selection: auto
literature_max_features: 100
literature_max_queries: 1000
```

`literature_selection` accepts:

- `auto` (default): use provisional AI allocation when impact assistance is
  enabled and eligible targets exceed the cap; otherwise use source P value
  followed by feature ID.
- `impact`: require a complete provisional assessment and comparative selection
  when capping is necessary. Unavailable or invalid responses leave allocation
  waiting. They cannot silently authorize a statistical prefix instead.
- `p_value`: explicitly retain the deterministic source-P ordering.

When every eligible target fits, all are retained without an additional AI
interaction. Completed supplied literature also skips the native allocation
stage. This policy applies separately within each hypothesis's literature
scope; the final manuscript impact review remains study-wide.

The provisional stage uses bounded public phenotype/field labels, allowlisted
feature/gene identifiers, original P/FDR and available finite signed summaries.
Missing public phenotype context holds AI allocation. It exposes missing scale, mechanism and
source appraisal as uncertainty rather than inventing them. Original source
methods and free-text provenance stay in their existing local artifacts. The
same impact-purpose opt-in, provider, local-loopback and disclosure rules apply.
Provisional review adds interactions before final manuscript ranking; the call
budget applies separately to these two stages.

Every target receives a bounded assessment, including its evidence link,
provisional priority and missing evidence. Comparative rounds then reduce the
assessed pool to the registered number of review slots. Complete leaf
assessments and eliminated candidates stay in the audit. This advisory
procedure does not prove a globally optimal ranking or a biological mechanism.
Unknown prior literature can be a reason to investigate a target; it is not
evidence against it.

The native operation retains:

- `literature-priority/candidates.jsonl`: the uncapped allowlisted projection
  and stable candidate identities for AI allocation.
- `literature-priority/selection.yaml`: all assessments/comparisons, selected
  IDs, pending needs, attempted/reused interactions and observed requests sent.
- `literature-selection.tsv`: the complete statistical inventory, its original
  statistical rank, separate `review_rank`, selection basis and retrieval status.

If the request budget, comparative payload, policy or response is unresolved,
target-specific retrieval waits and all targets retain an explicit pending
disposition. Independent field/method searches can continue. A pending target
is neither outside the cap nor a negative literature result. Increase an
interaction budget through a native study amendment, then repeat `study auto`;
matching sealed node responses are reused without altering historical runs.
If the compact comparison cannot reduce the pool, increase the declared
payload bound rather than dropping an unexamined prefix.

Run the generated-data CLI workflow with NumPy, SciPy and PyYAML:

```powershell
python -B examples/study-pipeline/literature-priority-workflow.py run NEW_DIRECTORY --jar JAR
python -B examples/study-pipeline/literature-priority-workflow.py verify DIRECTORY --jar THE_PINNED_JAR
```

The fixture uses synthetic completed query declarations and a loopback
responder. It checks a 105-target pool, source-statistic preservation, a weaker-P
target receiving a slot, partial comparison reuse after a budget amendment,
completed-stage reuse, deliberate holds and the subsequent full-inventory
manuscript impact review. It makes no new external requests and does not test
real AI or scientific impact quality.


## Phenotype-null GSEA and reusable null rankings

`gsea.null_model: phenotype` now has two executable routes. Native analysis uses one bulk molecular cohort at a time and the registered independent Gaussian identity OLS model. The second route consumes complete observed and joint null gene rankings produced with another appropriate model, including a pooled model. A completed supplied `ranked_gsea` result continues to bypass fitting and allows only missing pathway/module work to run.

Native analysis is an additional, registered cohort analysis. It recomputes the complete eligible ranking from the QC population; it does not use a significance-selected list or turn one cohort's permutations into a pooled/meta-analysis null. It uses the same `PhenotypeData`/`Formula` machinery as matrix association for outcome orientation and treatment-coded covariates. Molecular missing values are mean-imputed within the aligned analyzed samples. All-missing, constant or nuisance-explained features and ambiguous/unmapped gene annotations retain explicit dispositions. A numerical predictor-variation threshold is applied before outcome ranking. Multiple probes per gene require a declared mean/median collapse, repeated identically in every draw.

```yaml
schema_version: 1
project:
  id: phenotype-gsea
  title: Cohort-specific phenotype-null GSEA
  description: Complete an independent quantitative-trait pathway analysis
resources: {directory: resources, network: offline, downloads: deny}
cohorts: [{id: A}]
datasets:
  - id: expression-a
    cohort: A
    type: expression
    stage: processed_matrix
    species: human
    feature_id_type: gene_symbol
    files: {matrix: expression.tsv, phenotype: phenotypes.tsv}
hypotheses:
  - id: quantitative-trait
    description: Association of the measured expression features with the trait
    outcome: trait
    covariates: [age, sex]
    analysis:
      mode: manual
      goals: [ranked_gsea, pathway_network, network_modules]
      association: {model: ols, family: gaussian}
      gsea:
        null_model: phenotype
        phenotype:
          independent_samples: true
          exchangeable_errors: true
          strata_column: sex
          rationale: Review the residual assumptions and strata for this study
        gene_sets: collection.gmt
        minimum_size: 15
        maximum_size: 500
        maximum_exact_states: 0
        permutations: 10000
        seed: 31
        maximum_work: 250000000
        adjustment: BY
```

The two declarations express assumptions the scientist must assess; the software does not establish independence or exchangeability from them. A declared stratum must also occur among the nuisance covariates. An omitted stratum permits permutations across all analyzed samples. This route requires at least ten samples by default and at least two full-model residual degrees of freedom. `minimum_samples`, `maximum_samples`, `maximum_features`, `maximum_cells` and `maximum_design_columns` are explicit native resource controls. Complete model refits and enrichment work share `maximum_work`; exceeding it holds the stage with no favorable subset or smaller draw count.

Freedman–Lane permutes reduced-model outcome residuals, adds nuisance fitted values and recomputes the full signed partial-t ranking. Every gene shares the same row permutation, preserving the dependence induced by the fixed measured features. With general nuisance covariates this is an **approximate** procedure, even when every residual permutation is enumerated. The registered errors must be exchangeable within strata. See [Winkler et al., 2014](https://doi.org/10.1016/j.neuroimage.2014.01.060) for nuisance adjustment and exchangeability assumptions, and the [Broad GSEA guide](https://docs.gsea-msigdb.org/GSEA/GSEA_User_Guide/) for the distinction between phenotype and gene-set nulls.

When the product of within-stratum factorials fits `maximum_exact_states`, the native route enumerates that entire residual-permutation census, including identity. Otherwise it uses the declared number of seeded Monte Carlo draws with replacement. Native kinship, repeated-measure, non-Gaussian, offset and custom-formula adapters remain prerequisites. These cases need an appropriate complete external null-ranking ensemble or a completed supplied GSEA stage. There is no automatic unadjusted-label or gene-set-null fallback.

To reuse a complete external ensemble, replace `phenotype` with explicit `ranking` and `null_rankings`:

```yaml
null_model: phenotype
ranking:
  file: observed.tsv
  coverage: complete
  tested_genes: 20000
  provenance: {producer: Your registered complete gene-level model}
null_rankings:
  file: null-rankings.tsv
  draws: 10000
  sampling: monte_carlo  # or exhaustive, only for the complete registered census
  coverage: complete
  observed_sha256: REPLACE_WITH_THE_64_HEX_DIGIT_OBSERVED_FILE_SHA256
  method: Your joint resampling procedure
  model: Exact model, contrast, sample population and feature preprocessing
  exchangeability: Appropriate joint-null assumptions and block structure
  provenance: {producer: Your software version and immutable source receipt}
```

The null file is UTF-8 TSV with the exact header `draw_id`, `gene_id`, `statistic`. Each unique draw ID occupies one contiguous block and contains every observed gene exactly once, with a finite statistic. Gene row order may differ across draws. Missing, extra, duplicate or mismatched genes/draws hold the whole correction family. The observed-file SHA binds the null ensemble to the exact ranking bytes; both files enter cache identity and are snapshotted. Monte Carlo ensembles must contain independent draws from the registered joint null; an exhaustive census must contain equiprobable states. This format does not carry unequal state-probability weights or an MCMC autocorrelation correction. Use an appropriate independent simulation or completed source inference for those designs. Source model, exchangeability and exhaustive-coverage declarations remain claims to review. These checks do not reproduce the external model's calibration. Whole-stage supplied results remain useful when null rankings are unavailable.

Both routes produce the canonical full-family `ranked-gsea.tsv`, leading edges, effective GMT and optional running-ES curves consumed by pathway graphs/modules. Each term gets its own null ES distribution under shared gene rankings. Positive/negative tails, same-sign normalization, draw/tail counts, P resolution and Wilson Monte Carlo intervals are retained. BH/BY/NONE correction is applied over the complete eligible family; this is not Broad's pooled-NES empirical GSEA FDR. Undefined draws/scores suppress official family correction. Native `null-rankings.tsv` and `native-observed-ranking.tsv` can seed a later aggregate-only reuse project; participant sample audits remain local.

The self-contained `examples/study-pipeline/phenotype-gsea-workflow.py` workflow generates independent OLS, stratified and enumerated synthetic cases, two raw-data-free reuse projects and three prerequisite cases. It checks observed and every null statistic with independent NumPy full-model fits, ES/NES/tails/correction/leading edges with separate full-rank scans, source immutability, cached reuse and project verification:

```powershell
python -B examples/study-pipeline/phenotype-gsea-workflow.py run NEW_DIRECTORY --jar PATH_TO_JAR
python -B NEW_DIRECTORY/workflow-verifier-source.py verify NEW_DIRECTORY --jar NEW_DIRECTORY/software/jlinalg.jar
```

The fixture demonstrates arithmetic and workflow behavior. Scientific calibration and suitability for a real assay, phenotype and study design require separate assessment.


The [implementation record](../analysis-pipeline-implementation.md#phenotype-null-gsea-checkpoint-2026-10-26) records the exact frozen source and JAR, 2,595-test gate (2,421 passed, 174 optional skips, no failures/errors), all 18 new tests, nine packaged regression workflows, five same-input old/new comparisons and retained intermediate failures. These are source-build/workflow results; real-data scientific calibration and full-pipeline conformance remain unestablished.


## Continuous GWAS annotations and conditional effects

`gwas_enrichment.annotation_mode: mixed` adds a joint native S-LDSC model containing binary and continuous annotations. It supports centred or signed continuous scores, including continuous nuisance variables. The existing `binary` default keeps its fixed aggregate-annotation weighting convention. Mixed mode uses fixed initial weights based on the first all-SNP LD column and its reference count, so changing a continuous score's units does not change the weighting model. All registered columns remain in every fit and delete-block calculation; there is no outcome-driven nuisance selection or automatic column dropping.

Use the complete GWAS/LD/reference contract in the [GWAS enrichment guide](#ld-aware-gwas-functional-category-enrichment), adding this explicit mode and the new annotation metadata:

```yaml
gwas_enrichment:
  annotation_mode: mixed
  summary:
    file: complete-gwas.tsv
    coverage: complete
    tested_snps: 600000
    population: YOUR_MATCHED_POPULATION
    genome_build: GRCh38
    calibration: one_df_chi_square
    model: YOUR_REGISTERED_GWAS_MODEL
    qc: YOUR_GWAS_QC_AND_CALIBRATION_RECORD
    ascertainment: quantitative
  reference:
    population: YOUR_MATCHED_POPULATION
    genome_build: GRCh38
    release: YOUR_IMMUTABLE_REFERENCE_RELEASE
    common_variant_definition: YOUR_COMPLETE_COMMON_REFERENCE_UNIVERSE
    regression_snp_definition: YOUR_GENOME_WIDE_REGRESSION_PANEL
    annotation_model: ALL plus all registered binary and continuous target/nuisance columns
  ld_scores: matched-ld-scores.tsv
  annotations: annotation-metadata.tsv
  overlap: reference-cross-products.tsv
  blocks: 200
  adjustment: BY
```

In mixed mode, `annotations` needs `term_id`, `term_name`, `role`, `annotation_type` and `sum`. The role is `all`, `target` or `nuisance`; annotation type is `binary` or `continuous`. The first and only `all` row must be the binary all-reference-SNP baseline. Here is an illustrative format, with values that must be replaced by the actual reference moments:

```text
term_id  term_name            role    annotation_type  sum
ALL      All reference SNPs   all     binary           1000000
BINARY   Registered category  target  binary           200000
SCORE    Registered score     target  continuous       0
```

The `sum` of a binary annotation is its SNP count. A continuous sum can be fractional, zero or negative; it is not a SNP count. The file registered as `overlap` contains the complete uncentred reference cross-products `Q[j,k] = sum_i a[i,j] * a[i,k]`, with one `term_id` row and one column for each registered annotation ID. It must cover the same full reference universe as the sums. For the illustrative metadata above, one possible moment matrix is:

```text
term_id  ALL      BINARY  SCORE
ALL      1000000  200000  0
BINARY   200000   200000  25000
SCORE    0        25000   1000000
```

Actual files are TSV/CSV with named columns. Native validation checks finite/symmetric moments, baseline identities, binary intersections, population variances, positive semidefiniteness and reference-model rank. The supplied `ld_<ID>` columns must correspond to those same annotation definitions; a noncollinear LD table cannot rescue a dependent reference model. Signed continuous LD scores are allowed. Include the appropriate prespecified LD/MAF/functional nuisance annotations: an all-SNP baseline alone does not establish adequate architecture adjustment. Correct population, build, annotation release, reference universe and GWAS calibration remain source declarations to assess scientifically.

A continuous target's primary test is the two-sided normal approximation for its **conditional coefficient**, accounting for all registered annotations. Its standardized effect is `tau_star = coefficient * population_SD(annotation) * reference_SNPs / total_h2`. The SD comes from the complete reference moments, not the matched regression subset. Delete-block coefficient/total-heritability ratios provide the standardized-effect SE, including denominator uncertainty. The primary P value uses the raw coefficient's jackknife SE; it is not obtained by dividing tau-star by its ratio SE. These are conditional association-model summaries, not proof of a functional mechanism. See [Gazal et al., 2017](https://doi.org/10.1038/ng.3954) for continuous annotation effects and their standardization.

Binary targets retain category-versus-complement t(B) tests and overlap-aware category heritability. Their conditional coefficients and standardized coefficients are additional context; `coefficient_p` is a secondary unadjusted test for binary rows. Continuous rows retain `NA` for binary category proportions, enrichment and complement tests. `annotation_type`, `inference_target` and `method` identify each row's estimand. All prespecified targets share one complete BH/BY/NONE family, with nuisance columns excluded from that testing denominator. Weak/nonpositive total heritability, invalid delete ratios or unestimable target tests withhold every official adjusted value.

Correction uses natural-log tails. `log_p_value`, `log_adjusted_p` and `coefficient_log_p` retain strong associations whose linear columns underflow to zero. They are natural logarithms, not minus-log10 scores. Use the log values for scientific tail reporting. The canonical `gwas-enrichment.tsv` adds raw/standardized coefficient estimates and SEs, annotation sum/SD, inference labels and mandatory caveats. `sldsc-standardized-block-deletes.tsv` exposes the standardized delete estimates, alongside the existing coefficient covariance, raw deletes and regression-SNP audits.

A completed external `gwas_enrichment` table can be supplied without GWAS summary or LD files. New numeric and inference-label columns, original family adjustment, log tails and row caveats survive import into finding dossiers. A declared continuous row must use the conditional-coefficient inference target and cannot claim binary enrichment statistics. Negative uncertainties, nonfinite numeric values and positive log probabilities are rejected. External analysis/calibration is not independently reproduced. Whole-stage reuse still completes only missing downstream work.

The self-contained `examples/study-pipeline/continuous-gwas-enrichment-workflow.py` generates mixed/fixed/affine/nuisance, source-reuse and deliberate prerequisite cases. It constructs synthetic chromosome-local LD and full-reference annotation moments, then checks native results against independent NumPy weighted fits, delete-block ratios and SciPy normal/t tails. It also checks log-space correction, invariant standardized effects, source/history immutability, cached replay and finding-context propagation:

```powershell
python -B examples/study-pipeline/continuous-gwas-enrichment-workflow.py run NEW_DIRECTORY --jar PATH_TO_JAR
python -B NEW_DIRECTORY/workflow-verifier-source.py verify NEW_DIRECTORY --jar NEW_DIRECTORY/software/jlinalg.jar
```

The prototype passed nine scenarios and 468 independent numeric comparisons. This validates synthetic arithmetic and workflow behavior. Mixed-mode baseline fixed weights are an explicitly documented extension; upstream LDSC estimator parity, real GWAS calibration, multi-ancestry inference and genome-wide scale acceptance remain separate requirements.


The [implementation record](../analysis-pipeline-implementation.md#continuous-gwas-annotation-checkpoint-2026-10-26) records the exact frozen source/JAR, 2,641-test full gate (2,462 passed, 179 optional skips, no failures/errors), all 17 new tests, nine regression workflows, six before/after cases, schema/output-hash verification and the retained initial numerical-reference failure. The evidence is synthetic/source-build validation; real scientific calibration and complete pipeline conformance remain unestablished.
