# Analysis pipeline specification

> **Scientific workflow specification, updated 2026-10-26.** This document
> improves the proposed end-to-end pseudocode and maps it to the current source
> build. Requirements describe the intended workflow; they do not imply that
> every branch is already automatic. Documenting this specification performs no
> analyses, downloads or model predictions.

OmicsMatic should turn a declared scientific question and available data into
reproducible association, replication, functional, genetic and literature
results, followed by a manuscript draft. It should accept participant-level
data, existing association results or later-stage evidence, and execute the
branches supported by those inputs. The rules apply across phenotypes, cohorts,
tissues and molecular assays.

Use the [study kernel](auditable-omics-pipeline.md) for contracts and provenance,
the [runnable tutorial](study-pipeline-tutorial.md) for existing commands, and
the [bioinformatics workflow](bioinformatics-evidence.md) for source acquisition
and genetic inference. This pseudocode is a specification, not a new command
or additional accepted `study.yaml` syntax.

## Changes to the original pseudocode

| Proposed step | Refined rule |
| --- | --- |
| Official meta-analysis has the least heterogeneity | Prespecify the primary cohort set and model. Retain all eligible cohorts; report alternative subsets as sensitivity analyses. |
| Fixed/random effects depend on cohort count | Choose the estimand and model from scientific assumptions. Cohort count affects reliability, not which assumptions are true. |
| Exclude a cohort with no overlapping hits | Investigate power, coverage, scale, ancestry, tissue and model differences. Hit overlap alone cannot justify exclusion. |
| Remove outlying betas | Use explicit QC, exclusion records and an unfiltered sensitivity analysis. Large effects alone do not establish errors. |
| Subgroup histogram P values trigger sensitivities | Describe distributions; register justified subgroups; assess effect modification with adjusted interaction tests and multiplicity control. |
| Perform every enrichment analysis | Run each declared analysis with suitable inputs, gene mapping and background; record other dispositions. |
| Build risk scores from significant results | Keep feature selection, preprocessing and tuning inside training data, then evaluate frozen scores independently. |
| GSEA networks and correlation cliques are modules | Distinguish enrichment, pathway-overlap networks, molecular networks, connected components and overlapping maximal cliques. |
| MR on all significant features | Attempt all eligible features with association FDR < 0.05; require valid instruments and a separate MR testing policy. |
| Colocalization on thresholded QTL data anyway | Attempt exploratory partial-region analysis when numerical prerequisites hold; preserve a mandatory major limitation. |
| Score GWAS SNPs using ANNOVAR/AlphaGenome | Annotate all eligible variants and preserve functional predictions by transcript, tissue and output. |
| Use AlphaFold for proteins | Verify the measured protein/isoform, structure coverage and confidence; distinguish retrieval from new prediction. |
| Treat every GWAS variant with the same association test | Route by phenotype, frequency/count, relatedness and case/control imbalance; keep single-variant and set-test eligibility separate. |
| Recompute completed lab QC or filter using changing denominators | Reuse complete source-bound decisions, or audit/filter once using the original source populations; preserve derived data, masks and assay limitations. |
| Omit concrete genotype QC | Use an explicit filter profile, including a default single-variant minimum MAC of 20, with per-cohort/per-phenotype counts and exclusions. |
| Choose manuscript findings by P-value rank | Ask AI to compare scientific impact and evidence strength, then select 3–5 distinct findings across the manuscript with recorded reasons. |
| Draft without a length contract | Use an adjustable 8,000-word main-text target, honor explicit journal limits, and retain complete finding discussions and spillover in supplements. |
| Require the entire analysis to start from raw data | Accept typed, validated results at any supported stage and compute only requested missing work; preserve external provenance and unavailable upstream checks. |
| Fit every principal finding into a short summary | Develop a complete source-linked discussion for each selected story, keeping essential reasoning in main text and extended evidence in finding-specific supplements. |
| Give AI only paper identities while requiring substantive discussion | Optionally supply explicitly scoped public passage context with exact source/bundle hashes, purpose-specific disclosure and retained evidence gaps; metadata alone never establishes source support. |

These primary/sensitivity rules are scientific design choices for this workflow.
The [Cochrane meta-analysis guidance](https://training.cochrane.org/handbook/current/chapter-10)
also explains model assumptions and why heterogeneity tests should not select
between fixed and random effects.

## End-to-end pseudocode

```text
initialize study:
    declare hypotheses, phenotype definitions, effect direction and units
    declare cohorts, overlap, kinship, visits, tissues and assay versions
    assign discovery and validation roles before inspecting their results
    register models, QC, testing families, primary analysis and sensitivity rules
    register applicable follow-up branches, sources and computational budgets
    register GWAS single-variant MAC >= 20 and separate rare-set eligibility
    use 8000 main-text words as an adjustable target; honor strict journal limits
    retain complete finding discussions and spillover in linked supplements
    inventory user-supplied results/artifacts at every analysis stage
    validate their types, exact scopes, provenance, families and dependencies
    hold known scope conflicts; record missing declarations without inventing them
    mark accepted supplied results as external evidence, not native re-execution
    plan requested missing operations from supplied and previously verified outputs
    if all requested analyses are supplied, perform validation/reporting only
    check each requested operation against its model/input/source capabilities
    register managed or external variant software and its private installation home
    provide application setup commands independently of study/model credentials
    plan required software acquisition under the resource download policy
    require the user's registered ANNOVAR source and licence acknowledgement
    reuse verified software offline; retain incomplete/changed homes with recovery
    resolve the contract and preserve the approved plan
    inventory availability separately from operation-specific content hashes
    defer unavailable downstream files to their consuming branches
    reuse verified native work only under an audited input/context contract
    retain conservative full-study scope for other adapters
    preserve original execution provenance and immutable historical attempts

partition hypotheses into blocks with compatible estimands, assays and effects
for each phenotype/hypothesis and compatible analysis block:
    execute a following analysis only when requested and not already satisfied
    use accepted supplied artifacts without requiring unavailable upstream raw data
    allow cohort metadata references bound to their supplied primary analyses
    never treat a pooled reference as a constituent cohort estimate
    if starting with participant-level data:
        for each cohort:
            check sample/feature identity, units, coding, missingness and design
            if a supported bulk molecular matrix will actually be consumed:
                share source-only QC for matching dataset/policy across phenotypes
                reuse complete current-matrix sample/feature QC decisions when supplied
                otherwise audit measurement scale, missingness, constants and detection
                use all original features for sample missingness denominators
                use all original samples for feature statistics; do not iterate selection
                filter/mask only under an explicit policy, without imputation
                retain original data and verify the sealed derived matrix in consumers
                hold incomplete, invalid or empty selected scopes
            summarize the actual analysis population and participant flow
            if genotype data are involved:
                reuse accepted cohort-scoped sample-QC/PCA components
                complete only explicitly requested missing components
                retain external populations, PCA bases and source limitations
                perform sample, genotype, variant and ancestry/batch QC
                recompute allele counts in this phenotype's analyzed samples
                load versioned functional annotations needed for rare-set masks
                apply prespecified external ALT-frequency/panel/coverage rules when declared
                hold the complete native mask family for unresolved external frequencies
                retain upstream frequency-policy attribution for completed supplied analyses
                define MAC >= 20 single-variant eligibility and rare-set masks
            plot phenotype/covariate distributions and declared subgroups
            flag unusual values, imbalance and possible sensitivity analyses
            if the association model is unspecified:
                search current primary literature when network policy permits
                save proposed models, references, assumptions and search limits
                record a missing-model error; block association for this branch
                continue independent diagnostics and other runnable branches
            apply declared preprocessing and fit the appropriate null/design model
            if this is a GWAS:
                register chromosome, PAR and ploidy models before count QC
                select the relevant participant population and physical allele scale
                audit declared complements, invalid calls and structural absence
                count MAC in the actual analyzed population before imputation
                keep different chromosome contrasts as separate estimands/families
                require supported models for mixed, rare-set and unusual ploidy designs
                run eligible single-variant tests with calibrated sparse methods
                for a registered survival endpoint, validate event/censoring and entry/strata
                use the declared Cox estimator/test and separate PH diagnostic family
                require finite-MPLE risk-contrast certificates before native signed Cox inference
                retain local witnesses, shared family bounds and unresolved/nonfinite holds
                retain unavailable/failed fits without a successful-subset FDR fallback
                run declared rare-variant gene/region tests on their own QC set
                retain score/covariance summaries for compatible rare-set pooling
            else:
                fit the declared feature-level association models
            save N, effect/SE where defined, P, diagnostics and feature failures
        if adjusted subgroup interactions are registered:
            use explicit ordered contrasts and a compatible reviewed model
            reject outcome-defined contrasts as inferential evidence
            include justified covariate-by-group and covariate-by-feature terms
            test the registered feature family independently of primary P values
            check support and genotype MAC in each contrast level
            retain every test disposition; withhold adjustment if scope is unresolved
            retain group slopes, interaction uncertainty and separate family adjustment
            send eligible interaction evidence to impact review with its own caveats
            preserve primary association results and their significance labels
    else:
        import results and audit schema, estimand, model and provenance
        identify participant-level checks that cannot be reconstructed

    harmonize feature identity, effect coding, units and cohort comparability
    validate each result type; do not require betas for unsigned set-test P values
    exclude invalid estimates with explicit reasons
    apply registered effect QC where betas are meaningful; retain exclusions
    retain a separately identified unfiltered sensitivity analysis
    define eligible feature universes and multiple-testing families after QC
    calculate per-cohort association FDR over each full eligible testing family
    for GWAS, also apply the declared genome-wide and gene/set significance rules
    inspect Manhattan/QQ, frequency/count-stratified calibration and lead variants

    if no eligible cohort estimates remain:
        record no estimable primary result and retain completed diagnostics
        queue independent branches and continue to the next phenotype
    else if multiple comparable cohorts:
        plot every cohort pair on matched features with comparable signed effects
        save matched N, effect correlation and direction concordance
        if a discovery/validation design is declared:
            require at least one discovery and one validation cohort
            combine discovery cohorts using the registered model when needed
            calculate discovery FDR across its full eligible testing family
            freeze discovery candidates, then test them in validation cohorts
            preserve validated supplied discovery full-family adjusted P values
            never recompute discovery FDR from a selected-only export or validation overlap
            check source membership and trait against registered discovery scope
            check independence using all role metadata, even without source cohort tables
            report per-cohort and combined validation under declared FDR rules
        show significant-set overlap with Venn/UpSet and exact membership
        investigate unusual cohorts without selecting exclusions by hit overlap
        for signed comparable effects:
            fit primary inverse-variance meta-analysis of all eligible cohorts
            save pooled effects, intervals, P, FDR, N and effect directions
        for unsigned gene/set tests:
            pool compatible scores/covariances or use a reviewed combination
            save test-specific results without inventing beta or direction
        save heterogeneity estimates and their limitations
        run registered leave-one-cohort-out and other sensitivity analyses
        flag effect/significance/heterogeneity changes without replacing primary
    else:
        designate eligible single-cohort association as the primary analysis
        explicitly record that external replication was not performed

    resolve one primary source per hypothesis and estimand before follow-up
    require the registered primary meta-analysis when it is planned
    never replace missing/incomplete primary work with cohort or sensitivity hits
    resolve ambiguous producers/contrasts using a prespecified policy and rationale
    freeze primary result identity, exact artifact/hash, metadata and producer type
    allow a validated supplied primary result to satisfy the same scoped contract
    retain replication/sensitivity evidence with their distinct result identities

    for each declared eligible functional follow-up:
        run GO/pathway ORA, ranked GSEA or GWAS enrichment as applicable
        record mapping, tested background, resource release and multiplicity

    if this is a GWAS:
        annotate every QC-eligible SNP/variant with ANNOVAR or an equivalent
        look up versioned AlphaGenome scores; predict eligible gaps within budget
        retain per-variant status, per-output scores and missingness reasons
        investigate fine-mapped candidates while retaining other annotations

    if protein findings or supported protein mappings are available:
        resolve organism, accession, exact isoform/sequence and ambiguity
        retrieve matching AlphaFold structures and available confidence data
        if a matching model is absent and new prediction is in scope:
            run the configured prediction workflow with full provenance
        retain coverage, sequence mismatches and uncertain regions

    if suitable participant-level matrices are available:
        train and independently evaluate a risk/prediction score as applicable
        if grouped nested validation is registered for native quantitative/binary development:
            keep independent evaluation absent from every development split and selection
            relearn native preprocessing and penalties inside each training fold
            retain all fold/model/loss audits; hold incomplete validation families
            assess outer held-out development predictions separately from external evaluation
            refit the selected model on the full registered development pool and freeze it
        test frozen scores against registered related phenotypes with correction
        construct declared molecular networks and assess stability/preservation
        if a conditional frozen-wiring label null is registered:
            fix reference patterns, common node universes and exchangeability strata
            enumerate the whole group or use fixed independent sampled plus-one tails
            retain null counts, precision, all test dispositions and source caveats
            withhold corrected discoveries while the complete family is unresolved
            keep topology evidence separate from association FDR and GO function
        identify communities/modules and optional bounded maximal cliques
        perform module/clique GO against the mapped network gene universe
    build a pathway-overlap network from enrichment results when appropriate

    discover and review phenotype-matched GWAS and assay-matched QTL sources
    for every eligible mapped molecular feature with association FDR < 0.05:
        attempt the declared molecular-exposure -> phenotype MR analysis
        require valid alleles, ancestry/build, LD, instruments and overlap policy
        save estimates, strength/pleiotropy/influence diagnostics and disposition
    correct MR P values across the declared MR testing family

    for each locus in the declared colocalization selection scope:
        prefer dense regional GWAS/QTL data including nonsignificant variants
        harmonize variants and LD and run the declared model
        if only thresholded/partial data are available:
            attempt the permitted exploratory partial-region computation
            label outputs as conditional on selected observed variants
            carry the major caveat into tables, reports and manuscript
        record insufficient data or failed fits without inventing a null result

    build an uncapped evidence inventory with findings/loci and caveats
    where registered, attach allele-resolved LD redundancy evidence
    preserve source statistics; retain unknown LD coverage as unavailable
    ask AI for a provisional impact shortlist using the registered rubric
    review literature for up to 100 eligible significant features per phenotype
    retain queries, ranking, identifiers, citations and conflicting evidence

for each declared cross-phenotype primary MR reporting family:
    verify every member producer and its complete raw primary testing family
    retain original source corrections, test identities and non-estimable tests
    if any member is unresolved: withhold all official joint discoveries
    else: apply the declared joint correction over the full registered family
    attach separate joint/source results and limitations to manuscript dossiers

assemble manuscript and supplements from frozen, verified evidence:
    ask AI to rank supported findings across phenotypes and evidence branches
    keep statistical eligibility, primary result identity and FDR families frozen
    select 3 to 5 distinct principal findings, or fewer if evidence is insufficient
    do not fill multiple slots with a shared registered LD component
    record accepted/rejected candidates, reasons, uncertainty and source artifacts
    thoroughly discuss each selected story; retain its full supplementary dossier
    assess the adjustable 8000-word main-text target or explicit strict journal limit
    report primary, replication, sensitivity, null and unavailable findings
    distinguish association, prediction, colocalization and MR claims
    link quantitative claims to exact versioned result artifacts
    retain amendments, selection counts, limitations and reporting checklists
    produce a draft for scientific review
```

Dependencies determine execution order. Source/literature discovery can proceed
while an analysis waits; MR, colocalization, prediction and networks are
conditional branches. A report may document incomplete work, but must identify
requested analyses that remain unfinished.

### Execution contracts and acceptance criteria

For each planned operation, resolve its exact model, input/result type,
prerequisites, eligibility policy, thresholds/comparison operators, testing
family, resource budget, outputs and failure disposition before execution.
Check actual adapter capabilities rather than treating a registered operation
name or a working standalone command as proof of study integration. Missing
required quality metadata, unsupported ploidy/models, noncomparable effects or
unresolved sample dependence must not silently select a convenient fallback.
Independent operations can continue with their own recorded dispositions.

A compatible analysis block is more specific than a phenotype label: declare
molecular assay/feature definition, tissue, exposure/outcome orientation, effect
units/transformations, model/estimand and ancestry/target-population strategy.
Do not pool RNA abundance, isoform ratios and protein abundance because all were
tested against BMI, or count multiple tissues from the same participants as
independent validation cohorts. Record dependencies across blocks and combine
their interpretation in the evidence inventory rather than mixing their effects.

Define separate graph nodes for discovery-only pooling, frozen discovery
selection, validation, and all-cohort estimation. Freeze each testing family and
exact `<` versus `<=` threshold rule; report the evaluated denominator and losses
from missing coverage. Association, validation, gene/mask and MR families are
distinct contracts. AI impact ranking may change manuscript emphasis or propose
an explicitly exploratory amendment, but must not retroactively redefine a
primary result, testing family or cohort exclusion to improve the story.

An implementation is conformant only when output manifests and integration
tests establish the required behavior, not just command availability. Important
acceptance cases include MAC 19 versus 20, preservation of eligible low-MAC set
members, unsigned results without beta/SE, multiple discovery cohorts pooled
without validation leakage, overlapping cohort rejection/handling, empty/null
results, unavailable AI/resources, four/five ranked findings, and an over-budget
manuscript. A passing structural test does not establish calibrated inference,
successful external services or a validated scientific conclusion.

## 0. Enter at any analysis stage and complete missing work

Users may supply participant data, association or meta-analysis results,
enrichment, networks/modules, risk-score evaluation, MR, colocalization,
functional annotations or every completed analysis. A study can mix supplied
and native work across hypotheses and stages. An all-results project should
support validation, interpretation and manuscript construction without fitting
associations, rebuilding networks, repeating MR or downloading new inputs
unless those steps are explicitly requested.

Treat each supplied analysis as a **typed artifact with a scientific scope**,
separate from a source cohort. A meta-analysis covering four cohorts is a
pooled result, not a fifth cohort. A network edge table is not an association
table, and enrichment P values cannot enter a beta/SE harmonizer. Preserve
original files and hashes, validate an immutable imported representation, and
record externally produced versus natively executed evidence distinctly.

The import contract must identify the artifact/result type; hypothesis and
phenotype; feature/variant IDs, build and allele/isoform definitions where
applicable; cohort/tissue/ancestry/sample scope and overlap; primary versus
sensitivity role; method/model/version/parameters; resource releases; upstream
artifact IDs/hashes; complete tested universe, selection/filtering and
multiple-testing family; coverage, failures and existing limitations; and the
producer/date/citation when available. Explicitly mark missing provenance;
schema validity cannot certify calibration, prior QC or biological conclusions.

| Supplied artifact | Validation needed to reuse it | Examples of remaining work |
| --- | --- | --- |
| Association or meta-analysis | Result type, estimand/units/direction, source/cohort coverage, P/SE where defined, complete tested family and model/heterogeneity provenance | Enrichment, annotation, independent validation if actually available, genetic follow-up, literature and drafting |
| Enrichment or ranked GSEA | Gene-set/resource release, mappings, tested background or ranked universe, method/null model, full correction family and term membership | Term-overlap network, interpretation and manuscript integration |
| Network, modules or cliques | Node/edge IDs, directed/signed/weighted meaning, estimator/threshold, analyzed population, module membership/method and available stability evidence | Missing module GO or preservation analyses when their required inputs exist |
| Completed module GO | Module/type/term identity, original module-by-term correction family, source-adjusted values and declared scope; keep unavailable background and graph checks explicit | Finding context and discussion without requiring unavailable upstream raw data; missing native GO from memberships requires a full node universe and compatible mapping |
| MR | Exposure/outcome direction and units, QTL/GWAS sources, instruments/alleles/LD, method, instrument strength, overlap, complete primary testing family and limitations | Source review, missing sensitivity or colocalization, interpretation |
| Colocalization or fine-mapping | Exact region/signals, alleles/build/LD, priors/model, complete versus thresholded coverage, convergence, PIPs/posteriors and sensitivity scope | Missing prior/multiple-signal checks if dense inputs exist, evidence grading and discussion |
| Risk scores or functional/structural results | Frozen weights/preprocessing/evaluation partitions or exact sequence/variant/tissue/model context, source/version and coverage | Independent validation or contextual interpretation supported by remaining inputs |
| All requested analyses | Each artifact's scope, dependencies, provenance and complete/partial/failed dispositions | Evidence inventory, literature gaps, 3–5 finding dossiers, manuscript and supplements |

Plan a dependency graph around the **requested outputs and validated available
artifacts**, not an obligatory linear rerun. For each required operation:

```text
if an accepted supplied artifact satisfies this exact scientific scope:
    reuse its immutable imported representation; retain external attribution
else if a verified prior native result matches the resolved contract and inputs:
    reuse it under the existing cache/provenance rules
else if the required inputs and a supported method are available:
    execute the requested missing analysis
else:
    record the specific missing input/capability; continue independent branches
```

For cohort networks, apply that decision separately to each supplied graph's
declared dataset/cohort coverage. Preserve a supplied pooled graph as one graph;
do not fabricate constituent estimates. Complete only uncovered native datasets
by default, or use an explicit supplied-only policy. Multiple primary supplied
graphs need disjoint comparable scopes; unresolved coverage cannot silently
choose a native rerun. See the source-build
[scope and partial-network guide](study-pipeline-tutorial.md#scientific-scope-and-missing-cohort-networks).

Independent completed native MR, colocalization and fine-mapping pairs can also
be reused when the enclosing stage is incomplete. Verify each pair's current
source bytes, scientific contract, eligible feature, implementation and all
sealed outputs. Retain incomplete attempts, retry their missing work and
recompute current stage-wide testing families and coverage; do not copy a
previous joint correction or count reuse as replication. An explicit rerun
bypasses native pair reuse. Automatic unversioned references require their own
source identity before they qualify for pair-level reuse.

An explicitly `reported_status: partial` primary is retained as a
`supplied_partial` import, without completed-stage ownership. Automatic mode
requests completion of that stage when its policy is enabled; explicit manual
goals control which analyses run. Native completion uses its registered inputs
and methods as a separate analysis, without merging unverified fragments into
its result. A partial network covers no completed cohort graph. Source adjusted
P values and testing-family declarations remain intact; correction is not
recomputed from a partial fragment. When no native operation can be scheduled
for a requested stage, the import records a specific completion need. Successful
import validation alone does not establish completed analysis coverage. See the
[partial-stage guide](study-pipeline-tutorial.md#partial-supplied-stages-and-native-completion).

A conflicting import needs a recorded disposition and a precise repair request;
it cannot silently choose a replacement scope or be treated as an empty/null
result. Rerunning an accepted completed supplied analysis requires an
explicit request or registered sensitivity/amendment. Do not reconstruct FDR
from only supplied significant rows, invent a tested denominator, infer absent
edges/variants as nulls, or describe unrecoverable participant QC as performed.
Missing raw data blocks only analyses that require it; it need not block a
report that honestly states the available evidence and its limits.

Acceptance must cover meta-only input without association refitting; enrichment
input without ORA rerunning; supplied graphs with missing module GO; supplied
MR/colocalization with exact source/caveat retention; mixed native/imported
branches; all-analyses-supplied reporting; changed hashes, wrong result types,
conflicting scopes and missing tested universes; and deterministic resume
without duplicate analyses or evidence. These remain target requirements for
the complete scientific acceptance matrix. The current source implements
per-hypothesis `analysis.supplied_results` with typed tables, immutable snapshots,
external attribution, testing-family preservation and import dependencies.
Native intake, participant QC and association also have scoped resume contracts
that defer downstream genetic source files. Run-level file availability remains
distinct from per-stage content hashes and original execution provenance.
Reporting and unaudited adapters retain conservative full-study cache scope.
The [supplied-result tutorial](study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work)
documents the syntax and supported columns. Legacy dataset stages such as
`meta_analysis_results`, `enrichment_results` and `mr_results` now request
migration to this contract instead of entering association normalization.
Import validation does not independently reproduce the source analysis or
certify its scientific scope, calibration or conclusions. Native module-GO
orchestration now accepts supplied graphs/memberships under the
[network contract](study-pipeline-tutorial.md#molecular-networks-pathway-overlap-modules-and-module-go).
Complete external formats and source/claim appraisal remain open.

## 1. Study contract and participant data

Define the analysis unit, exposure, outcome, covariates, transformations,
interactions, effect scale, reference categories and target population. Specify
whether a molecular feature predicts the phenotype or is the response; those
coefficients are not interchangeable. A model family such as OLS is insufficient
without its formula and design.

Check sample/feature uniqueness and alignment, visits, relatedness, cohort
overlap, missingness, impossible values, constant predictors, design rank and
preprocessing. Assay-specific checks are required; genotype checks include
allele coding, call/imputation quality, frequency, population structure and a
justified HWE policy. Use appropriate models for families, repeated measurements
and single-cell donors. Report the actual analysis population, exclusions,
case/control counts and feature-specific N. Summary-only data require upstream
QC records; they cannot establish participant-level consistency retrospectively.

Use histograms/density plots for continuous variables, category counts for
categorical variables, and missingness summaries for both. Show raw/modeled
scales when relevant. Flag outliers; a histogram alone should not delete
participants. Prespecify handling and preserve sensitivity comparisons.

Subgroups need explicit definitions. Comparing BMI between groups defined by a
BMI obesity cutoff is circular and does not establish effect modification.
Distribution comparisons can be descriptive; an inferential test must match
the variable and dependence structure, report effect size/uncertainty and correct
the declared comparison family. Assess association differences using an adjusted
interaction/contrast. Significance in one subgroup and nonsignificance in another
is not itself a significant subgroup difference. Data-driven proposals remain
exploratory and do not gate every biologically justified sensitivity analysis.

When the model is missing, preserve the requested error even if literature
suggests one. Save the search date, primary sources, suitability, conflicting
recommendations and search limitations. A literature/LLM suggestion or software
default does not substitute for an explicit scientific model decision.

Treat blank/null models as missing. Use the executable's accepted model names
and request review before model-dependent QC when a native association is
planned. Preserve standalone descriptive QC under its declared endpoint
contract and the separate prerequisites for supplied/configured analyses and
unsupported modalities.
Preserve conditional requirements for endpoint, assay, dependence and
calibration. Recent source queries should incorporate declared public assay
and endpoint/design context, retaining a broad phenotype/method search and
its bounded date order. Keep retrieval, candidate compatibility and scientific
model appraisal distinct; unavailable searches must not authorize fitting.
See the [missing-model guide](study-pipeline-tutorial.md#missing-model-review-and-explicit-continuation).

## 1a. GWAS QC and association strategy

GWAS requires explicit branches for array genotypes, imputed dosages and
sequencing calls. Single-marker scans and rare-variant set tests have different
eligibility rules. QC is performed before association, repeated after sample
selection where counts or rates change, and retained as a sample/variant flow
table. A missing required QC field is an unresolved input requirement, not a
silent pass. For imported summaries, record which checks were performed upstream
and which cannot be verified locally.

### Variant identity and analysis population

Declare genome assembly, reference FASTA/version, assay/caller/imputation panel,
chromosome naming, REF/ALT, dosage allele and ploidy. Normalize indels and split
multiallelic sites under a documented policy; retain original-to-normalized
identities. Resolve duplicates and incompatible alleles before joining cohorts.
Strand-ambiguous SNPs need an allele-frequency/reference check or an explicit
exclusion from allele-sensitive analyses. LD and functional annotations must
match build, alleles and the population being analyzed.

Apply phenotype/covariate eligibility, sample QC and declared ancestry/visit
selection first. Compute AC, AN, MAC, MAF, call rate and available case/control
counts in the resulting cohort/phenotype analysis sample. For a biallelic site,
`MAC = min(AC, AN - AC)` and `MAF = MAC / AN`; AN counts observed allele copies
under the applicable ploidy. Record hard-call versus dosage-derived quantities,
founder/related-sample policy and genotype uncertainty. Do not infer carrier
counts from MAC, round dosages to manufacture hard calls, or replace per-variant
AN with `2 * total_enrolled_N` when observations/ploidy differ. For ancestry or
subgroup scans, recompute eligibility in that stratum; a large pooled MAC does
not make a sparse cohort estimate trustworthy.

### Default QC profile and required decisions

These are **proposed workflow defaults**, not a claim about the current CLI's
defaults or a universal optimum. A study may override them before inspecting
associations, with reasons and a retained sensitivity comparison where needed.

| Check | Default action in the proposed workflow | Important scope |
| --- | --- | --- |
| Single-variant MAC | Exclude MAC < 20; retain MAC = 20 if other requirements pass | Apply per analyzed cohort/phenotype/stratum, including dosages with a declared count definition. This is not a guarantee of test calibration. |
| Sample genotype call rate | Exclude below 98% in the declared callable QC panel | Record the panel/denominator and iterate sample/variant QC to a documented stable rule. Structural absence is not a random missing genotype. |
| Variant genotype call rate | Exclude below 98% among eligible analyzed samples | Assess ancestry, case/control and batch missingness separately; a high overall rate can hide differential failure. |
| Imputation quality | Require a declared provider-specific quality metric, with 0.8 as the starting threshold for a compatible INFO/R-squared measure | Confirm its definition and available range; stricter low-frequency sensitivity may be needed. Missing quality is not quality = 1. Sequenced hard calls do not receive an invented imputation score. |
| Monomorphic/no-information sites | Exclude from single-variant testing and remove uninformative set columns | MAC = 0, no callable alleles, zero modeled variance or an empty usable genotype vector cannot produce an association estimate. |
| HWE | Record the test, eligible samples and ancestry; flag exact-test P < 1e-6 for review rather than impose an unconditional universal exclusion | Use appropriate controls/founders and ploidy. Large-sample, ancestry and excess-heterozygosity behavior matter; an exclusion rule requires a declared sample-size-aware policy. |
| Phenotype/covariate availability | Apply the declared complete-case or supported imputation strategy and report losses | Recompute allele counts after the final analysis sample is selected. Do not silently impute outcomes. |
| Genotype-level sequencing quality | Apply caller/platform-specific FILTER, depth, genotype quality, allele-balance and callable-region rules before calculating site metrics | No universal depth/GQ threshold is assumed. Low coverage is missing information, not homozygous reference. |
| Identity, contamination and unusual heterozygosity | Flag and resolve under declared assay-specific criteria; exclude confirmed failures | Preserve duplicate/sample-swap decisions, genetic-versus-recorded sex/ploidy checks and family inconsistencies with their evidence. |
| Population structure, relatedness and batch | Use documented ancestry assessment, PCs/covariates and an appropriate relationship/cluster model | Related or diverse participants are not automatically bad samples. Separate ancestry-specific inference and cross-population pooling need explicit plans. |

The [PLINK filtering documentation](https://www.cog-genomics.org/plink/2.0/filter)
describes count, missingness, HWE and quality filters and their differing sample
definitions. In particular, its current HWE guidance cautions against fixed
threshold misuse in large or stratified samples. The values above are this
workflow's starting profile, not thresholds attributed to PLINK or another tool.

Assess differential missingness and allele-frequency discrepancies by phenotype,
ancestry, batch and cohort. Register a test/flag policy and its multiplicity;
investigate technical causes without selecting exclusions to improve P values.
Inspect lead variants in imputation/genotype clusters or read-level evidence
when available, especially sparse carrier signals. Preserve audit-only fields
and privacy-appropriate aggregates rather than disclose participant identities.

### Implemented genotype QC boundary

The optional registered `analysis.genotype_sample_qc` profile now connects an
observed diploid common-autosomal panel to participant eligibility, missing-aware
KING between-family estimates and unrelated-basis genotype PCA with projection.
A separate hard-call source can accompany imputed association data. Initial
sample call rates use observed autosomal calls, rather than dosage completeness;
the denominator excludes structural sex-chromosome absence. All-pair support,
prespecified relatedness actions, source completeness and work limits gate the
approved population. Generated PCs enter regressions only when requested.
Pre-existing PCs can remain declared covariates without native regeneration.
See the [sample-QC guide](study-pipeline-tutorial.md#registered-genotype-sample-qc-and-projected-pcs)
for exact inputs, bounded native execution, held dispositions, source-linked
aggregate context and remaining caller/sex/PAR/ancestry/scale limitations.
This profile cannot retrospectively validate supplied downstream populations.
The [external-QC/PCA contract](study-pipeline-tutorial.md#reuse-external-sample-qc-and-pca-and-complete-missing-work)
adds exact matrix/sample/hash binding, source-reported pair/support checks,
imported scores, per-cohort source choices and explicit native completion of
missing PCA. Imported PCs retain their source basis, including a declared
external reference, rather than being refitted after phenotype subsetting.
Full reuse has separate linear import bounds and performs no native KING/PCA.
Source-specific limitations and private provenance stay local; aggregate
finding context preserves external origin and the requirement for scientific
source review. This does not certify that external QC was scientifically
adequate or independently reproduce its computations.

The source implementation now calculates allele number from source ploidy when
VCF/BGEN provides it, after phenotype/covariate and participant selection. A
dosage-only table without ploidy declares an assumed diploid denominator. The
generic association adapter excludes observed non-diploid genotypes and split
multiallelic sources with explicit reasons; supporting those inputs requires an
appropriate association model. This exclusion does not establish biological
sex, pseudoautosomal regions, ancestry, kinship or caller-level quality.

`analysis.association.minimum_hwe_p` and `minimum_missingness_p` default to zero
(report only). A positive threshold activates an exclusion rule: an unavailable
required statistic excludes the variant with an explicit unavailable reason.
The HWE implementation uses the conditional exact diploid test on eligible human
autosomes. Its declared scope is all samples, cases or controls; binomial/probit
study defaults select controls. An optional `files.hwe_samples` ID list restricts
this further to the intersection with analyzed participants. Its hash and source
and analyzed counts are retained. Supplying a list does not certify founder,
unrelatedness or ancestry status. Dosage-derived genotypes never acquire a hard
call HWE P value merely because their values happen to be integers. See
[Wigginton et al.](../CITATIONS.md#wigginton-hwe-2005) and the
[PLINK HWE guidance](https://www.cog-genomics.org/plink/2.0/filter#hwe) when
prespecifying the sample set and threshold; sample-size scaling, mid-P and
specialized sex-chromosome HWE are not implemented by this adapter.

Case/control missingness uses a two-sided Fisher exact test on called versus
missing genotypes before mean imputation. Enumeration has an explicit state
budget; exceeding it records an unavailable result without substituting an
approximate P value. Group MACs count the same cohort-minor allele in both
groups, and are not carrier counts or independent stratum-minor counts. No
default requirement demands MAC 20 separately in both groups. A declared
`genotype_kind: hard_calls` rejects fractional dosages; imputed data require the
declared quality metric, including per-ALT VCF quality where supplied.

The default GLM genotype route uses its documented efficient-score inference.
An explicitly registered `binary_test: exact-conditional` now supports finite
conditional hard-call score inference for independent Bernoulli observations
with declared stratum intercepts and fixed known offsets. Optional approximate
Firth effects remain separate; valid P-only tests survive downstream evidence
selection. Resource/numerical failures withhold native BH for the complete
family and put official selection into a waiting state. See the
[exact-test tutorial](study-pipeline-tutorial.md#exact-conditional-tests-for-sparse-binary-gwas)
for its narrow model, import contract and scope. Continuous nuisance covariates,
kinship, dosage-based exact inference and broader calibrated routing remain
separate requirements.

An explicitly registered `binary_test: firth-lrt` selects full Firth logit fits
and constrained penalized likelihood-ratio inference for independent individual
Bernoulli outcomes. This is separate from QC and does not implement saddlepoint
or binary mixed-model inference. Method reports and mandatory manuscript caveats
retain the estimator and its calibration limits. The
[REGENIE methods](https://rgcgithub.github.io/regenie/overview/) illustrate why
sparse and imbalanced binary analyses need an explicitly suitable inferential
route. Broader sparse-tail calibration and related-sample routes remain required work.
The [QC tutorial](study-pipeline-tutorial.md#genotype-qc-scopes-and-unavailable-statistics)
shows the current configuration and packaged acceptance example.

### Explicit Firth logit inference

For independent individual binary GWAS observations, a supplied `glm` model
with `family: binomial` can register `binary_test: firth-lrt`. The full
Jeffreys-penalized objective is maximized separately for each eligible variant.
The coefficient-zero constrained fit retains the same full design and penalty;
deleting the genotype column would define a different objective. The test
statistic is twice the penalized log-likelihood difference, with an asymptotic
chi-square tail on one degree of freedom. See
[Firth (1993)](../CITATIONS.md#firth-bias-reduction-1993),
[Heinze and Schemper (2002)](../CITATIONS.md#heinze-schemper-separation-2002)
and the [reference fitter documentation](../CITATIONS.md#logistf-reference-documentation).

No maximum-likelihood null must converge first, so separated nuisance designs
can be fitted when the full design remains identifiable. Numerical singularity
and nonconvergence stay explicit failures with no association P value. The
ordinary Fisher-information standard error is evaluated at the penalized
estimate; it is not derived from the LRT P value. Subsequent inverse-variance
meta-analysis uses a separate Wald approximation. Preserve both methods and
their original testing families. Profile likelihood confidence intervals and
population-prevalence correction are not supplied by this route.

The method has no implicit relatedness adjustment. It rejects GRM/pedigree,
random-effect/cluster declarations, non-Bernoulli families and ordinary-null
conditional score export. The implementation uses portable CPU fitting with
bounded blocks and ordered parallel variant fits; production genome-scale
throughput is a separate performance question. Study `offset_column` registers
a fixed offset, adds it to participant complete-case QC and prevents accidental
use as an estimated covariate. An offset alone does not establish valid LOCO,
relatedness or population correction.

Firth estimation does not guarantee nominal finite-sample tails for every rare
design. The packaged acceptance audit enumerates an intercept-only fixed-margin
null exactly. At N=1,000, five cases and 20 heterozygous carriers, the asymptotic
Firth LRT rejects approximately 9.6264% of null datasets at a nominal 5% level.
This is an explicit calibration limitation, not a biological finding. Register
design-appropriate exact/permutation or other validated inference where needed;
do not use point agreement with a reference fitter as proof of tail control.
Automatic suitable-method selection, broader calibration and sparse/related
set-test routing remain incomplete.

### Common, low-frequency and rare single-variant tests

Use an explicit frequency convention, for example common MAF >= 5%, low-frequency
1% <= MAF < 5%, and rare MAF < 1%. These are descriptive bins; MAC, sample size,
case/control allocation, imputation and the null model determine whether a
particular test is adequate. Do not impose a minimum MAF of 1% on the entire
workflow if rare-variant analysis is intended. A rare variant with MAC >= 20 may
still enter a properly calibrated single-variant scan.

| Situation | Required association strategy |
| --- | --- |
| Quantitative phenotype, adequate information | Fit the declared linear/mixed model with the registered covariates and relatedness/population structure controls; inspect sparse-carrier influence, residual assumptions and robust or transformed-model sensitivity where justified. |
| Binary phenotype, sparse alleles, separation or strong case/control imbalance | Use a method validated for that design, such as appropriate Firth logistic or saddlepoint-calibrated mixed-model testing. Specify test and effect-estimation methods separately; Firth alone does not model family dependence. Ordinary logistic Wald inference is not an automatic fallback. |
| Related participants or large structured cohorts | Use the registered mixed/whole-genome model, appropriate PCs and relationship handling. Declare LOCO/proximal-contamination controls and their marker construction; do not LD-prune away the variants being tested merely because pruning was used for PCs. |
| Survival phenotype | Use the declared Cox/frailty model with event/censoring checks and carrier/event information; MAC >= 20 alone does not establish adequate event information. |
| Sex chromosomes, mitochondrial or other nonautosomal data | Use explicit ploidy, PAR boundaries, sex/ancestry strata, coding and test assumptions. Block an unsupported chromosome/model combination instead of treating it as diploid autosomal data. |
| Sparse counts unsupported by the configured engine | Record not estimable or route to a reviewed validated external method. Do not produce a routine nominal P value by changing models after seeing the signal. |

For binary traits save case/control allele and carrier counts when available.
Do not require MAC >= 20 separately in both cases and controls by default:
that can remove real associations with carriers concentrated in one group.
Instead require calibrated sparse-data inference and disclose concentration,
separation and uncertainty. [SAIGE's single-variant guide](https://saigegit.github.io/SAIGE-doc/docs/single.html)
recommends MAC >= 20 and a separate set-based rare-variant analysis. Its
[testing options](https://saigegit.github.io/SAIGE-doc/docs/single_step2.html)
documents imbalance-aware testing and separate effect-size options; these
capabilities should not be assumed of every OmicsMatic model.

### Rare-variant gene and region tests

**MAC < 20 excludes a variant from the default single-variant branch; it does
not delete a high-quality rare variant from the raw data or automatically remove
it from gene/region tests.** Define a separate set-test QC profile that retains
informative rare/ultra-rare variants when the method supports them. Set tests do
not rescue low-quality calls, systematic batch artifacts or uncallable samples.

Define gene/transcript/region boundaries and annotation release before testing.
Prespecify masks such as high-confidence predicted loss of function, a declared
damaging-missense mask and their union, with explicit splice/noncoding masks
where justified. Set frequency limits, for example MAF <= 1% and optional nested
0.1%/0.01% or singleton masks, using ancestry-appropriate internal/external
frequencies and a declared rule for variants common in another population.
The optional native [external-frequency screen](study-pipeline-tutorial.md#external-population-frequency-eligibility-for-rare-masks)
now applies a fixed versioned ALT AC/AN panel before masks/score pooling, with
explicit populations, per-mask limits and adequate-AN requirements. Missing
frequencies or coverage hold the complete mask family, while known source-QC
failures exclude alleles. Retain source bytes, decisions and the original mask
universe. This is point-frequency eligibility; it does not implement gnomAD FAF,
confidence-bound filtering, automatic ancestry matching or resource conversion.
Record weights, transcript aggregation, annotation confidence, overlapping masks
and coverage. ANNOVAR/AlphaGenome predictions may define an a priori secondary
mask or weight, but do not tune that choice using the association results.

Use burden tests for a justified aggregate of similarly directed effects;
SKAT permits mixed directions; SKAT-O and ACAT-O provide specified omnibus
alternatives; ACAT-V is useful for sparse variant-level signals. Declare a
primary test/omnibus rule rather than report whichever method has the smallest
P value. Report all attempted masks/tests and correct across genes/regions,
frequency masks, tests and phenotypes under an explicit family or valid
hierarchical/omnibus strategy. A minimum P across masks is not already adjusted.

A registered cross-mask stage may combine prespecified tests and correct across
the complete resulting set/gene family. Retain original mask corrections,
component coverage, independent eligibility and reviewed gene mappings.
Missing/failed masks cannot define a smaller successful-test family. Freeze
discovery unit selection before validation. Reuse supplied omnibus results with
their original scoped family, without reconstructing variant scores. The
[source-build guide](study-pipeline-tutorial.md#cross-mask-rare-set-and-gene-omnibus)
describes the current contract and limitations.

Ultra-rare collapse is method-specific and distinct from the single-variant
MAC threshold. The existing [ACAT workflow](acat-rare-variants.md) collapses pooled
MAC <= 10 into a burden component by default; preserve its score orientation and
covariance requirements. [REGENIE's set-test documentation](https://rgcgithub.github.io/regenie/options/)
and [SAIGE's region-test documentation](https://saigegit.github.io/SAIGE-doc/docs/set_step2.html)
also distinguish rare masks, collapse rules and set-level testing. Do not apply
an upstream MAC >= 20 export filter and then claim an inclusive ultra-rare test:
lost variants and their joint scores cannot be reconstructed from retained hits.

Record callable N, allele counts, distinct carriers when available, effective
variant count, mask membership, collapsed components, test/null model and
convergence/numerical failures. Minimum cumulative MAC/carriers and binary-trait
calibration are method-specific requirements; a universal count of 20 is not
silently transferred to every set test. A one-variant or empty set must follow
the selected method's documented behavior and be identified as such.

For replication, match gene/region definitions, annotation masks, frequency
policies and tests, even if the exact rare alleles differ. Burden direction is
meaningful only for the same burden definition/orientation. SKAT/ACAT P values
have no signed beta; do not draw beta scatterplots or calculate inverse-variance
meta-analysis from fabricated effects. Use compatible cohort scores/covariances
for pooled rare tests, or a declared valid P-value combination with its dependence
assumptions. Record variants absent versus unmeasured/uncallable. A single-variant
MAC exclusion is branch-specific and must not erase the separate set-test input.

### Post-association calibration and locus follow-up

Save sample/variant flow, effective N, AC/AN/MAC/MAF, quality, effect allele,
method, beta/SE when meaningful, P/log P, convergence and exclusions. Check
QQ/Manhattan plots by phenotype, ancestry, frequency/MAC and test class. Investigate
inflation/deflation, sparse-tail artifacts, batch effects and proximal contamination;
genomic inflation is not automatically population stratification or permission
for a second blanket genomic-control correction. Use appropriate LD-score or
null simulations/negative controls when supported; keep correction choices explicit.

Declare single-variant and gene/set significance rules separately. Report the
conventional common-variant GWAS threshold of P < 5e-8 where applicable, alongside
the declared FDR family. Dense sequencing, multiple traits and gene/mask tests
may need different/more extensive correction; FDR < 0.05 is not interchangeable
with genome-wide significance. Keep exploratory FDR-selected findings labeled.

Define independent loci using ancestry-matched LD and documented windows;
conditional analysis/fine-mapping is needed to distinguish multiple signals
within a locus. Replicate exact variants/alleles when appropriate and distinguish
locus, gene-mask and molecular-feature replication. Use regional plots, leave-one-
variant-out set sensitivity and carrier/quality review for leading findings.
Retain credible-set uncertainty, secondary signals, ancestry differences and
functional evidence; neither the nearest gene nor the smallest P identifies
the causal mechanism automatically.

### Modality-specific checks beyond GWAS

The same intake must declare assay-appropriate checks rather than treating a
generic finite matrix as sufficient QC:

| Modality | Required considerations before choosing the association model |
| --- | --- |
| RNA abundance/splicing | Library/sample quality, low abundance and informative junction coverage, normalization, compositional effects, transcript/cluster definitions, batch and cell composition; count, transformed expression and isoform ratios have different models. |
| Methylation | Detection/bead/coverage quality, cross-reactive or SNP-affected probes, sex-chromosome policy, beta/M-value scale, cell composition and array/batch effects. |
| Proteins/metabolites | Assay controls, detection limits/censoring, missingness, plate/batch/drift, dilution/transformation, target ambiguity and known binding artifacts; a pQTL may reflect binding rather than abundance. |
| Microbiome or compositional assays | Contamination, depth, sparsity, zero handling, taxonomic/feature definitions, compositional model and batch; ordinary correlations on raw proportions require justification. |
| Single-cell/spatial | Donor/sample/cell/spot QC, doublets, ambient signal, cell-state labels and spatial context; cells/spots do not provide independent donor replication. |

Each family needs explicit thresholds, modeled scale, missingness and diagnostics
in its contract or a documented external preprocessing record. Unsupported intake
or models remain visible prerequisites; these rows do not imply automatic
OmicsMatic implementations for all assays.

## 2. Result QC, testing families and comparisons

Audit field semantics as well as headers: beta versus R-squared, SE versus
variance/SD, odds ratio versus log odds, effect allele, units, transformations,
N and P-value calculation. Model rank and feature-specific degrees of freedom
can explain legitimate reconstruction differences. Record numerical underflow;
a rounded zero does not imply infinite evidence.

Distinguish invalid estimates from heuristic outliers. Nonfinite betas,
nonpositive SEs, invalid P values and failed fits need explicit dispositions.
Apply these requirements by result type: unsigned gene/set tests can legitimately
have no beta/SE, and cannot enter beta outlier screens, effect scatterplots,
direction concordance or inverse-variance pooling as though they did.
The current [effect-QC policy](study-pipeline-tutorial.md#effect-size-quality-control-before-downstream-analysis)
uses per-dataset median/MAD screening, IQR fallback and an optional unit-specific
bound, with defaults `robust_z: 100` and `min_features: 20`. These are conservative
audit settings, not biological thresholds. Preserve raw tables and every
exclusion/reason; do not tune cutoffs to improve replication. Filtering on
estimated effects can alter inference and does not guarantee calibrated FDR
merely because the rule does not use P values. Retain an unfiltered sensitivity.

Declare intersection versus available-cohort handling, minimum cohorts per
pooled feature and each eligible testing family. An intersection follows QC and
can propagate one cohort's exclusion to the common feature set. Missing,
unmeasured, excluded and tested-nonsignificant remain distinct. Compute association
FDR over the full declared eligible family before hit selection. State whether
correction is per phenotype or across phenotypes; per-phenotype adjustment does
not control a combined cross-phenotype claim.

Effect comparisons use all matched eligible features before significance
selection, with selected-hit overlays identified separately. Save units, matched
N, Pearson correlation, sign concordance and optional declared rank correlation.
Uncertainty requires a justified dependence model; genes/probes are not
necessarily independent bootstrap units. Correlation is diagnostic, not a
replication test. A fitted slope cannot justify rescaling incompatible estimates.

## 3. Discovery, validation and primary meta-analysis

Assign at least one discovery and one validation cohort before inspecting a
multi-cohort discovery/validation study. A declared meta-analysis-only design is
possible, but cannot claim independent validation. A single cohort can support
discovery while replication remains unavailable. Cohort labels alone do not
establish independence: account for shared participants, families and reused
public datasets using an appropriate covariance model or block unsupported pooling.

Pool multiple discovery cohorts using discovery data alone. Freeze candidates
before validation; prespecify per-cohort/combined validation, directional criteria
and testing families. Under independence, a declared reduced validation family
may use phenotype-specific discovery candidates within a predefined common tested
universe. Never select it using validation P values. Report exact 1/2/3/all-cohort
membership as appropriate. The all-cohort estimate may subsequently include
validation cohorts; its stronger P value is not further independent replication.

Use Venn for small cohort counts and UpSet for larger ones, with exact membership
and tested-universe denominators. Hit overlap confounds effect agreement with
power and coverage. A cohort with no significant overlap needs investigation,
not automatic removal.

Choose fixed/common-effect or random-effects pooling from the estimand and
scientific comparability. Record the heterogeneity estimator and interval method;
few cohorts make between-cohort variance and prediction intervals uncertain.
One remaining cohort is a cohort estimate, not a meta-analysis. Report pooled
beta, SE, interval, P, FDR, contributing cohorts/N, per-cohort directions,
Cochran Q, I-squared and tau-squared when estimable. Add a prediction interval
when justified by the model and available cohorts.

Register leave-one-cohort-out analyses with at least three cohorts, minimum
retained cohorts and a heterogeneity-alert rule. Define both a per-feature
threshold and the fraction of eligible features constituting widespread
heterogeneity. Compare a common feature universe where possible and retain all
attempted omissions. Report changes in effects, intervals, direction, significance
and heterogeneity. Fewer degrees of freedom and uncertainty can make a smaller
heterogeneity statistic misleading. Do not search arbitrary subsets for the
minimum I-squared or remove every disagreeing cohort.

Primary exclusions need defensible reasons such as incompatible assays/estimands,
duplicate participants or documented technical failure. A post hoc change needs
an amendment, the original analysis and an all-cohort sensitivity comparison.
The primary result is identified by its prespecified rule and exact artifact,
not its rank among sensitivity analyses. Replicated findings are a separate tier.

## 4. Enrichment and networks

Use the frozen primary result and its actual eligible universe. For GO/pathway
overrepresentation, specify feature-to-gene mapping, many-to-one/one-to-many
handling and deduplication. Use genes that could have been selected as the
background; transcript/probe multiplicity and assay biases may need specialized
correction. Record species, resource/annotation release, set-size filters,
term counts and correction family. The [GO guide](https://www.geneontology.org/docs/go-enrichment-analysis/)
explains reference lists. No significant hits is a valid outcome and need not
prevent an eligible ranked analysis.

Ranked GSEA uses a declared signed statistic over the eligible ranked universe,
not only significant genes. Preserve probe-to-gene aggregation and the
permutation/null model; see the [GSEA formats](https://docs.gsea-msigdb.org/GSEA/Data_Formats/)
and [user guide](https://docs.gsea-msigdb.org/GSEA/GSEA_User_Guide/).
Define GWAS enrichment precisely: gene-set enrichment in GWAS, overlap with known
GWAS loci/genes and omics-hit enrichment in GWAS-derived sets have different
LD, mapping and background requirements. An unadjusted nearby-catalogue count
does not answer all of these questions.

A pathway network can connect enriched sets by declared gene/leading-edge
overlap and cluster related terms. Ranked GSEA and pathway-overlap networks can
use summary results. Molecular correlation networks require appropriate feature
matrices or a documented external network. Normalize and address covariates,
batch, tissue composition and relatedness under a declared model. Avoid building
the entire network from outcome-selected hits unless that conditional analysis
is the explicit scientific question.

Declare Pearson/Spearman, signed/unsigned treatment, minimum observations,
missingness and edge uncertainty. `abs(rho) >= 0.7` is an exploratory starting
threshold; inspect a prespecified range for stability rather than tune it for
preferred GO terms. Preserve negative-edge signs. Connected components are
networks; communities or WGCNA clusters are candidate modules. Optional maximal
cliques are fully connected, potentially overlapping subgraphs, not a unique
partition. Bound their minimum size, count and runtime and report limits reached.
Assess stability and cross-cohort preservation, then run module/clique GO using
the mapped network universe and declared multiplicity. Functional labels remain
supported hypotheses rather than experimentally proven functions.

The source build now registers separate correlation/pathway-overlap graph,
bounded module and joint module-GO operations. Supported native bulk matrices
receive per-dataset listwise selection and covariate residualization; supplied
GSEA/graphs/memberships can satisfy upstream work. Count/state/depth limits,
resource/species/namespace needs and conditional stability are explicit. See
the [network tutorial](study-pipeline-tutorial.md#molecular-networks-pathway-overlap-modules-and-module-go).
Native preranked GSEA now supplies complete scoped rankings, exact/Monte Carlo
gene-set nulls, NES/leading edges, term-family correction and automatic pathway
membership. Its precision, model and mapping contracts are documented in the
[GSEA tutorial](study-pipeline-tutorial.md#native-ranked-gsea-and-leading-edge-pathway-networks).
Phenotype/block-aware nulls, cross-cohort preservation, clustered resampling,
integrated community/WGCNA orchestration and broader large-scale acceptance
remain separate requirements.

## 5. GWAS annotation and AlphaGenome scoring

Attempt annotation of **every QC-eligible tested variant**, and retain a status
for each original variant, including exclusions. Normalize representation,
split multiallelic records as appropriate, and verify build, coordinates and
REF/ALT against the reference. rsIDs alone are insufficient identity. Preserve
transcript-specific consequences and ambiguous gene assignments.
Include eligible low-MAC members of rare-variant sets even when they did not
enter the single-variant scan; their annotations define the declared set masks.

ANNOVAR supplies gene/region annotations and versioned annotation/score databases;
it does not produce one universal SNP impact score. Save protocols, operations,
database versions, score definitions and missing annotations. The [official
startup guide](https://annovar.openbioinformatics.org/en/latest/user-guide/startup/)
describes its separate tool/database configuration.

AlphaGenome complements annotation with predicted molecular effects. Use a
versioned precomputed resource when build, variant and scoring context match;
otherwise use a configured prediction service or external workflow. Google
DeepMind describes [AlphaGenome Atlas and its variant-impact score](https://deepmind.google/blog/alphagenome-atlas-a-predictive-map-of-every-possible-dna-letter-change-in-the-human-genome/);
the [official scoring guide](https://www.alphagenomedocs.com/colabs/variant_scoring_ui.html)
retains output/scorer and biological-context metadata. Preserve model/resource
version, sequence window, build, REF/ALT, tissue/cell ontology, gene/transcript,
output type, raw score, quantile/calibration and scoring options where available.
An impact score or quantile is not an association P value or causal posterior.

For a genome-wide pass, prefer compatible precomputed Atlas lookups and cache
their resource version. Route eligible gaps to a bounded live-prediction queue;
record its coverage separately from annotation coverage. The [official
AlphaGenome client documentation](https://github.com/google-deepmind/alphagenome)
distinguishes precomputed Atlas queries from live predictions and advises
against workloads requiring more than a million live predictions. Check the
current provider limits when resolving the plan rather than assume that every
tested SNP can receive a fresh prediction in one run.

Target a per-variant annotation/scoring record across the eligible GWAS universe.
If a declared budget restricts prediction to fine-mapped/credible-set candidates,
report the rule, unscored count and reasons; do not claim all SNPs were predicted.
Continue annotation of other eligible variants. Never restrict fine-mapping to
functionally scored SNPs. Retain unsupported alleles, reference mismatches,
missing predictions and service failures explicitly. Cache versioned public
predictions under the study's external-query policy.

Combine functional, association, fine-mapping, QTL and colocalization evidence
in a transparent candidate table. Preserve components; any priority index needs
declared weights and sensitivity checks. Correlated predictions are not
independent corroboration. Scores cannot replace association FDR, establish
the causal SNP or automatically identify its target gene. Public annotation
does not require participant genotypes once the permissible variant list exists.

## 6. Protein interpretation and AlphaFold

Resolve eligible protein findings to organism, UniProt accession, sequence and
exact isoform where possible. Record ambiguous antibody/aptamer targets, peptide
coverage, protein groups and sequence versions. A gene symbol alone does not
establish which protein isoform was measured.

Retrieve a matching AlphaFold DB model first; retain its identifier, sequence
coverage, version and source. When available, retain per-residue pLDDT and PAE
for relative-domain confidence, alongside a structure view. The [AlphaFold DB
documentation](https://www.alphafold.ebi.ac.uk/) describes confidence outputs.
Mark missing confidence data and sequence mismatches explicitly.

A missing exact isoform or separately justified complex may require a configured
new AlphaFold prediction. Retain sequences, software/model version, templates/MSA
provenance where applicable, seeds and confidence outputs. Retrieval is not new
prediction. Variant-to-residue mapping requires exact transcript/sequence and
residue numbering. Structural confidence does not establish pathogenicity,
protein abundance changes, binding, druggability or efficacy. An isoform-ratio
association alone does not establish a changed protein structure. Keep these
predictions alongside pQTL/MR/colocalization evidence with distinct interpretations.

For a missense-variant hypothesis, consider a suitable variant-effect predictor
separately from the structure model. [AlphaMissense is a separate
system](https://www.ebi.ac.uk/training/online/courses/alphafold/classifying-the-effects-of-missense-variants-using-alphamissense/)
that classifies missense variants; a confident AlphaFold structure is not a
prediction of the effect of a point mutation. Preserve exact sequence/residue
mapping, model version and uncertainty, and avoid treating correlated model
outputs as independent experimental evidence.

## 7. Risk scores and related phenotypes

Distinguish molecular prediction, polygenic scoring and absolute-risk modeling.
Define outcome, intended use, training population and evaluation population.
Keep participant/family/cohort/visit groups intact across partitions. Full-data
association selection, imputation, normalization, batch adjustment and tuning
can leak evaluation information; fit them within training folds or use genuinely
external frozen discovery weights. Nested validation is needed when both tuning
and performance estimation use CV.

Freeze feature definitions, transformations, weights and missing-feature handling
before independent application. Report incremental performance over a declared
covariate baseline, uncertainty and outcome-appropriate calibration: for example,
R-squared/RMSE for quantitative outcomes, discrimination/calibration for binary
risk, and time-dependent metrics for survival. Assess transportability/subgroup
performance when supported. In-sample score association is not validation.

Test frozen scores against registered related phenotypes, such as BMI or
cardiovascular traits for a glucose score, using adjusted models and a separate
multiple-testing family. Record shared participants and correlated outcomes.
New related-trait suggestions need an exploratory label or amendment. Association,
prediction and causality remain separate claims.

## 8. MR and colocalization

Find phenotype-matched GWAS and assay-matched QTL data: irQTL/sQTL for the actual
isoform/splicing feature, eQTL for expression, pQTL for proteins and corresponding
resources for other assays. Match tissue, organism, ancestry, build, trait,
version and sample overlap. Prefer full summary statistics to catalogue lead-SNP
lists. Download permissible sources with citations/checksums; unavailable or
unsuitable sources are not negative biological evidence.

Attempt MR for **all** unambiguously mapped primary features with association
FDR strictly below 0.05 and valid available instruments. The literature cap of
100 is not an MR cap. Save a feature-level disposition for every attempt.
Declare direction, usually molecular exposure to phenotype; reverse/bidirectional
MR requires a separate instrument set and hypothesis.

That feature loop applies to molecular exposures. In a GWAS-only study, define
trait-level exposure/outcome hypotheses and suitable instruments instead of
treating every associated SNP as a separate molecular exposure. A GWAS may also
provide the outcome statistics for the molecular-feature loop.

Apply molecular-feature eligibility before gene annotation or source matching.
Missing/ambiguous annotations and unlocated features need explicit dispositions
inside the selected MR family; they cannot shrink its denominator or be called
null. Reviewed exact-feature MR can use a sourced pair-level `feature_location`
when a local annotation interval is unavailable. Retain its coordinate source
and original annotation status, require the existing exact QTL/build/ancestry/
instrument contract, and do not infer a gene mechanism from that declaration.
The [MR coverage tutorial](study-pipeline-tutorial.md#mr-coverage-before-gene-annotation)
documents the implemented path, independent correction and dossier caveats.
The literature-review cap does not cap reviewed MR pairs.

Partial external MR coverage now has an explicit contribution contract: declare
the exact completed features, source result digest, stable analysis identity,
primary estimator and original testing family. Preserve the external rows and
adjustments; run native MR only for uncovered eligible features. Conflicting or
unavailable provided coverage stays pending rather than causing a native
replacement. Compute and label the separate joint MR correction, with unresolved
tests and completed unestimable tests kept distinct. The
[MR completion tutorial](study-pipeline-tutorial.md#complete-missing-mr-features-from-supplied-contributions)
documents this branch; a full supplied primary still owns the entire stage.

Prespecify cis/trans scope, instrument threshold, clumping/LD-aware method,
strength, allele/palindrome handling and overlap policy. Do not select instruments
or retrieve outcome effects only when outcome-significant. A thresholded QTL
export can support instrument selection only if its threshold includes every
instrument eligible under the rule; retain winner's-curse/selection limitations.
A single instrument supports a ratio under a suitable reviewed method but not
multi-instrument pleiotropy diagnostics. Unsupported/insufficient designs have
explicit dispositions rather than substituted methods.

Save estimates/intervals and method-appropriate strength, heterogeneity, pleiotropy
and influence diagnostics. Adjust the declared family of primary MR tests
separately from association selection; identify sensitivity estimators. Association
FDR < 0.05 establishes neither valid instruments nor MR FDR control. Registered `project.mr_testing_families` can combine complete primary MR test families across declared phenotypes, preserving source adjustments and withholding joint discoveries for unresolved members. See the [cross-hypothesis MR tutorial](study-pipeline-tutorial.md#declared-cross-hypothesis-primary-mr-families). Post-selection
MR remains follow-up evidence. Use the [STROBE-MR guidance](https://www.bmj.com/content/375/bmj.n2233).

Colocalization needs a regional locus, measured molecular feature, phenotype,
selection scope and shared-variant coverage. Prefer dense statistics including
nonsignificant variants, compatible alleles and appropriate LD for multi-signal
methods. Report signal-specific posteriors, credible sets, convergence, prior
sensitivity and missingness. Colocalization alone does not establish causal
direction. The [coloc input guide](https://chr1swallace.github.io/coloc/articles/a02_data.html)
requires dense coverage without P-value thresholding.

When only thresholded data exist, retain the requested exploratory partial-region
attempt if sufficient aligned variants and LD remain. Save export cutoffs,
observed counts, per-source matched fractions, regional gaps, overlap uncertainty
and model/convergence choices. Use the existing evidence grade
`exploratory_partial_region_major_limitation`. Carry this major caveat into all
tables, reports and manuscript: **posteriors are conditional on observed, selected
variants; missing variants, significance filtering, unequal coverage, winner's
curse and unknown overlap can materially bias them. A null result is not evidence
against a shared causal variant.** Numerical success does not promote partial
results to conventional complete-region evidence. Insufficient aligned data are
not estimable; missing required sources/declarations are waiting prerequisites.

## 9. Literature, manuscript and evidence disposition

### Literature scope

For each phenotype, review at most 100 eligible association-FDR-significant
features after full-family adjustment, also retaining their declared GWAS or
gene/set significance category. Preserve every significant feature and mark
those outside the review budget as not reviewed. Use the provisional AI impact
shortlist below to allocate this review budget. The native `auto` policy uses
provisional impact assistance when enabled and the eligible pool exceeds the
cap, otherwise retaining source-P/feature-ID ordering. An explicitly requested
AI allocation that is incomplete or denied must wait, rather than silently
falling back to a P-value prefix. Completed supplied literature remains reused.
Record the final review order and every departure from that fallback. Merge
duplicate gene searches while preserving distinct probes/transcripts and
phenotype links. A study-wide rather than per-phenotype cap must be explicit.
The 100-signal limit bounds detailed signal review, not statistical analysis,
the full evidence inventory or the number of references in the manuscript.

Save search dates, queries, synonym resolution, sources, budgets, deduplication
and article identifiers. Verify cited claims against papers, identify abstract-only
evidence, and retain contrary/null findings and known retractions. A failed or
limited search cannot establish novelty. Optional LLM appraisal/drafting follows
the existing assistance policy and still requires source and claim checks.

Validate supplied hit counts as exact nonnegative integers before declaring a
search completed or empty. Numeric underflow, overflow or fractional rounding
must not become a zero or a different integer. Apply exact year validation to
supplied citation metadata and retain the original source bytes and hashes.

Carry a per-finding coverage ledger into final impact comparison, manuscript
drafting and editorial review. Keep native allocation separate from completed
supplied searches: nonselection by the current cap does not invalidate prior
evidence. Distinguish pending, failed, limited, no-match, retrieved-unreviewed
and bibliography-only states; deduplicate exact reused query receipts without
discarding source links. Coverage records do not certify scientific appraisal
or change statistical eligibility. See the [coverage guide](study-pipeline-tutorial.md#literature-coverage-in-finding-dossiers).

Carry bibliographic candidates alongside those coverage records. Preserve
normalized paper identifiers, public title/author/year/journal metadata,
correction notices, metadata conflicts, query links and the verified producer
source IDs. Match feature queries by exact feature ID, with declared gene-only
queries kept as gene-level context. A general bibliography remains shared
hypothesis background and cannot become feature-specific search coverage or
support. Impact batches receive background only for their included hypotheses;
the whole manuscript retains the study's background even when a hypothesis has
no selected principal finding. Keep unsupported or conflicting query contexts
quarantined. Preserve all candidate records under the declared payload budgets,
without an arbitrary twenty-paper preview truncation. Metadata alone does not
establish that a paper was read or supports a claim. See the
[citation handoff guide](study-pipeline-tutorial.md#citation-candidates-in-impact-review-and-manuscript-drafting).

### AI-assisted selection of 3–5 principal findings

AI is used here for comparative scientific interpretation: weigh the impact of
the supported findings and propose **3–5 distinct principal findings for the
whole manuscript**, across phenotypes and analysis branches. This is separate
from numerical association testing and transparent statistical candidate tables.
It is not a request for three to five findings per cohort, phenotype or method,
nor for a five-paragraph Results limit. If fewer than three defensible findings
exist, use fewer and record why; never invent findings to fill the quota.

First create a complete finding inventory with stable IDs and exact source
artifacts. Group linked SNPs into loci and overlapping molecular/pathway evidence
into scientific stories where justified, preserving the underlying rows.
Several significant SNPs in one LD block or several annotations of one protein
should not become five supposedly distinct discoveries. Distinct conditional
signals or genuinely different conclusions may still warrant separate findings,
with their relationship explained.

Each candidate dossier contains the hypothesis/estimand, cohort and sample
coverage, effect magnitude and interval, adjusted significance/test family,
replication and direction, sensitivity/heterogeneity, QC and allele/count flags,
fine-mapping uncertainty, applicable MR/colocalization evidence grades,
functional/structural predictions, literature support/conflict and missing
evidence. Include a meaningful null result, failed replication or limitation
when it changes the scientific interpretation. The AI should not see only a
top-ten dashboard preview or rank a search-success status as a finding. If
context limits require batches, preserve full inventory coverage, a consistent
rubric and an explicit cross-batch comparison; record any unassessed candidates.

Use this review rubric without presenting an arbitrary score as a calibrated
impact probability:

| Dimension | Questions the AI must answer with evidence |
| --- | --- |
| Reliability and validation | Is the signal estimable, QC-eligible, corrected for the appropriate testing family, replicated and robust to the registered sensitivities? Are the counts and model calibrated for this design? |
| Effect and practical relevance | How large and precise is the effect on an interpretable scale, for which population? Does absolute impact differ from statistical precision? |
| Contribution and novelty | Does it resolve a meaningful question, strengthen or contradict prior work, or reveal a supported new relationship? Is novelty backed by an adequate search rather than missing search results? |
| Biological/clinical coherence | Do tissue, assay, isoform, ancestry and direction fit the proposed interpretation? What mechanism is supported, and what remains speculative? |
| Complementary evidence | Do fine-mapping, QTL, MR, colocalization, pathways or structures add appropriate evidence? Are they dependent, partial-region, uncertain or only contextual? |
| Distinctness and narrative value | Does this add a different conclusion rather than repeat the same locus or the same evidence in another format? Does an important null/conflict change the main message? |
| Limitations and next experiment | What could reverse the conclusion, what evidence is missing, and which feasible validation would resolve the uncertainty? |

Apply evidence eligibility before narrative appeal. Retained invalid/unreportable
results cannot be promoted to supported positive findings. Major partial-data
limitations, weak instruments or an unreplicated result must remain explicit;
novelty or an attractive mechanism cannot cancel them. Small P values, database
availability and AI confidence are not sufficient evidence of high impact.
The shortlist may contain well-labeled exploratory or null findings when those
are scientifically consequential, with their weaker/different claim scope intact.

Use two passes: provisional prioritization allocates bounded literature and
follow-up effort; final prioritization uses verified results and source appraisal.
Reassess any proposed main finding lacking a sufficient literature review within
the declared budget, or explicitly qualify what has not been established. Save
the ranked list, selected 3–5 finding IDs, supporting/counterevidence, selection
reasons, near-miss/rejected alternatives, uncertainty and unresolved disputes.
Keep an auditable scientist decision and any override rationale alongside the
AI recommendation; review does not require asking for permission at every step
of a workflow already authorized by the scientist.

Record model/provider/version, prompts, supplied evidence IDs/hashes, responses,
rubric and review decisions through the existing assistance/provenance boundary.
Use permitted aggregate evidence; patient-level data are unnecessary for this
ranking. If AI is required by the study and unavailable or disallowed by network
policy, record ranking as pending or an explicit manual substitution. Do not
silently label a smallest-P ordering as AI impact assessment.

The source build now registers study-wide `finding_inventory` and
`impact_assessment` operations with an uncapped primary/null/excluded inventory,
full-family candidate eligibility, source-linked aggregate dossiers, mandatory
caveats and bounded hierarchical AI comparisons. `impact-assessment.yaml`,
`impact-candidate-dispositions.tsv` and `manuscript-outline.yaml` retain selected
and unselected candidates, source/limitation links, comparative reasons and
review disposition. The outline binds the selected IDs to Results/Discussion
word allowances. Native revision `finding_ids` and explicit scientist override
reasons enter `impact-selection-review.yaml`. See the
[runnable tutorial](study-pipeline-tutorial.md#study-wide-impact-assessment-and-manuscript-selection)
for exact settings, local/remote/browser boundaries and limitations. Source
appraisal, actual-model quality and broader assay/source acceptance remain
separate requirements; a deterministic protocol fixture is not real AI review.

For primary GWAS findings, registered local allele-resolved LD tables can add
an independent redundancy constraint without changing significance or manual
story labels. All supplied reference variants enter the graph, including
unselected mediators. Two principal slots cannot share a positive-linkage
component even if the genes or story labels differ. Source scope, missing
variants/pairs, threshold/window coverage and ancestry/build/allele conflicts
remain explicit. Connected components can be transitive and are not
fine-mapped causal signals or proof of independence. The
[registered LD tutorial](study-pipeline-tutorial.md#registered-ld-evidence-for-distinct-principal-findings)
documents complete and explicitly partial sources, source hashes, cache
invalidation and both AI/native-prose constraints. Automatic reference
retrieval, native LD extraction and scientific independence appraisal remain
separate work.

### Thorough principal-finding discussions

Use [Wickenhagen et al., *Science* 2021](https://doi.org/10.1126/science.abj3624)
as a depth benchmark; an [author-deposited published copy](https://eprints.gla.ac.uk/253009/)
is available. Its OAS1 account connects splice-acceptor variation and isoform
prenylation/localization to RNase L-dependent antiviral experiments and patient
observations. It also examines null results and limitations, including the
relationship between blood measurements, infection-site biology and timing.
The standard is an explicit evidence chain and serious discussion of alternatives,
not an assertion that a pipeline association reproduces that experimental work.

For each selected story, create a complete finding dossier and a developed
discussion addressing:

1. **What this study observed:** the exact phenotype, feature/variant/isoform,
   effect scale/magnitude/interval, population, correction family, replication
   and registered sensitivity results, with exact artifact links.
2. **What previous work established:** relevant primary studies, their designs,
   experimental systems, populations and specific results, with claim-level
   citations. Resolve isoform, tissue, ancestry and disease-stage mismatches.
3. **How the proposed mechanism works:** explain each link from variant or
   molecular change to cellular function and phenotype. Label each link as
   directly demonstrated, supported indirectly, predicted or unresolved; show
   which evidence comes from this study versus external work.
4. **What could explain the result differently:** competing genes/isoforms,
   confounding, reverse causation, LD, pleiotropy, assay binding artifacts,
   selection, model sensitivity, null findings and contradictory studies.
5. **What follows and what does not:** practical/biological importance with
   appropriate uncertainty, generalizability and conditions on any clinical
   or therapeutic interpretation.
6. **What should be done next:** concrete experiments or independent analyses
   that distinguish competing mechanisms, identify required measurements and
   specify what outcome would strengthen or weaken the interpretation.

Give each story the space its evidence needs, potentially several paragraphs.
The main text needs a self-contained synthesis, the strongest evidence,
important counterevidence and every caveat that changes the conclusion. A
linked finding-specific supplementary note retains the full evidence/claim
ledger, detailed literature synthesis, secondary mechanisms, source/method
comparisons, full sensitivity results and proposed follow-up. Equal per-finding
word allowances are starting allocations, not caps on scientific depth. A
large volume of prose, a checklist pass or an AI's self-rating does not certify
thoroughness or accuracy; scientific review still checks every substantive
claim against the underlying results and primary sources.

The source build now adds a local claim/source ledger with frozen inventory and
prose hashes, retained public texts, exact passage anchors, epistemic labels,
dependency checks and eight prose-linked depth facets. Native reference
acquisition can explicitly request a public full-text XML projection and retain
its license/raw-response audit. The
[source-review guide](study-pipeline-tutorial.md#claim-bound-principal-finding-discussion)
documents review/strict modes, source scope, inherited sealed copies and local
disclosure boundaries. A mechanical pass always retains scientific-review status;
actual-model appraisal and scientifically thorough real manuscripts remain
separate acceptance requirements.

Typed supplied bibliographies now feed the same citation catalog and native
manuscript path. Explicit literature contributions can reuse complete,
hash-bound searches and perform missing native queries, with exact scientific
scope and separate source-reported/native coverage. Partial exports and provider
or scope mismatches cannot suppress retrieval. See the
[supplied-literature guide](study-pipeline-tutorial.md#reuse-supplied-literature-and-complete-missing-searches)
for the contract, source dates, public-metadata boundaries and limitations.

### Flexible 8,000-word main-text target and unrestricted supplements

Use **8,000 words as an adjustable default main-text target**, not a ceiling on
the complete scientific account or a requirement to pad prose. A declared
strict journal limit takes precedence. An over-target working draft is retained
with word counts and an editorial disposition; it is not truncated or rejected
solely for exceeding the default. By default the
count includes Introduction, Methods, Results, Discussion and any Conclusion,
with their headings and inline citations; exclude the title page, abstract,
reference list, tables, figure legends and supplements. Record separate counts
for those items. If the target journal counts them differently, apply its scope
and retain the resulting section/total counts. This is a proposed document
contract. The source build accepts `project.manuscript_policy.word_limit_mode`
as `target` or `strict`. Newly resolved default manuscripts use `target`; an
explicit legacy `max_main_words` without a mode remains strict so existing
declared limits are preserved. Set the mode explicitly when changing that
intent. No default supplement word ceiling is imposed.

A starting allocation is:

| Main-text section | Default word allowance |
| --- | ---: |
| Introduction | 900 |
| Methods | 2,200 |
| Results | 2,300 |
| Discussion | 2,400 |
| Conclusion, if separate | 200 |
| Total main text | 8,000 |

These section allocations are adjustable planning targets. Fold the conclusion
allowance into Discussion when there is no separate conclusion. Draft the
abstract separately, provisionally up to 250 words unless the journal specifies
otherwise. Allocate Results/Discussion space among the selected findings based
on evidential importance and explanatory needs, not equal paragraph lengths or
P-value rank. Fewer findings may deserve more room when uncertainty and contrary
evidence require explanation.

Start Results with participant/variant flow and the applicable global results,
then retain the Methods-aligned analysis order. Emphasize the selected findings
within those sections; open Discussion with their importance and supporting
reasons. Preserve essential QC, null/discordant findings and limitations in main
text even if they make a less attractive narrative. Place exhaustive variant,
gene/mask, enrichment, sensitivity and ranking tables and extra figures in
supplements, with explicit main-text links and full artifact provenance.

Validate section and total word counts after citations, tables and revisions
are finalized. If over budget, shorten repeated background and secondary detail
and move detailed supporting discussion to linked supplements before removing
essential evidence or caveats; record any further reduction in
the selected finding set. The statistical result set and significant-signal
tables do not shrink to match a word budget. AI ranking selects emphasis, not
which analyses count as performed or which results are publicly retained.

Build the manuscript from exact verified artifacts and citations. Include
participant flow/Table 1, design/models, QC/exclusions, testing families,
discovery/validation, direction, heterogeneity, all registered sensitivities,
functional/network/score results, genetic evidence, predictions, limitations and
data/code availability. Report null and incomplete branches. Numerical claims
need phenotype, cohort set, feature universe, analysis version and artifact
provenance. The draft requires scientific review of selection, causal wording
and modality-specific reporting requirements.

Distinguish completed results (including true null findings), completed-but-not-
estimable results, waiting prerequisites, failed execution, inapplicable analyses,
declined optional work and budget-limited work. These reporting dispositions do
not introduce new executor-state enum values. Preserve immutable raw inputs,
amendments, exclusions, resource versions, commands, seeds, hashes, warnings and
attempted alternatives. Historical/invalidated results remain traceable.

## Implementation map and remaining work

An optional separate `network_preservation_null` stage now tests frozen signed
or unsigned edge patterns under a registered within-stratum target-label null.
Exact or fixed-draw sampled tails, complete family correction and supplied-result
reuse remain conditional graph evidence, separate from association discoveries,
GO, participant-sampling stability and WGCNA Zsummary. See the
[conditional wiring guide](study-pipeline-tutorial.md#conditional-tests-of-frozen-network-wiring).
Broader network models and scientific exchangeability appraisal remain.

**Current conformance is partial.** The audited baseline was `57ec05d`;
implementation is now proceeding on `codex/analysis-pipeline`. The source build
adds prefit sample QC, explicit model gating, discovery-only pooling and manuscript
policy enforcement alongside the existing kernel. There are still standalone
methods, configured adapters and unimplemented requirements. The
expanded pseudocode is the target workflow, not the behavior of every current
`study auto` run. Local packaged examples verify the stated genotype-QC,
pooling, rare-set influence, offline literature-selection and study-wide
impact/native-manuscript protocol subsets; one real Europe PMC request verifies
model-review retrieval. The impact fixture uses deterministic local responses,
so it does not establish actual AI scientific appraisal.
These checks do not validate the full workflow, other services, installed tools
or biological findings. Exact evidence is retained in the implementation status.

An earlier focused source audit ran 49 pipeline tests: 47 passed and two
rare-variant assertions failed. The repeated-cohort test reaches its expected
waiting state but fails a message assertion; the GRM reference fixture omits
its required annotation declaration and does not reach numerical comparisons.
Both fixture issues were subsequently corrected: the request is parsed as YAML
and the GRM fixture declares its annotation input. All 11 tests then passed,
including the independent GRM numerical comparison. Rare-set cohort influence
has since been added; its refreshed acceptance evidence is recorded separately.
This is not a claim of a passing full-pipeline gate. See the
[dated conformance audit](../analysis-pipeline-implementation.md#historical-conformance-audit-2026-10-21)
for the precise scope, failures and distinction between local source and releases.

| Area | Current source capability | Remaining work for this specification |
| --- | --- | --- |
| Contracts/execution | `StudyContract`, `StudyPlanner`, `StudyExecutionEngine`, questions, amendments and evidence index | Extend registered operations/contracts as needed; this pseudocode adds no executable settings. |
| Association/model choice | Every study association requires an explicit model. Missing models produce a bounded recent-literature/candidate-model review and a blocking need, including processed matrices. Offline and one live Europe PMC case were verified | Extend assay/design-specific search and scientific model appraisal, and complete model-specific routing; the current broad phenotype search cannot establish suitability. |
| GWAS QC | MAC 20, marker missingness 0.02 and sample call rate 0.98 are captured for genetic studies; imputed studies declare INFO 0.8. Source ploidy, case/control counts, exact autosomal hard-call HWE with optional reference IDs, exact missingness and unavailable-statistic exclusions are retained. Generic association excludes observed non-diploid and split-multiallelic inputs | Complete caller/assay, biological sex/PAR, ancestry/kinship QC and calibrated sparse binary/model routing. HWE/missingness thresholds remain prespecified optional filters, not universal defaults; low-level set-test eligibility stays separate. |
| Rare-variant association | Registered annotation-mask, Gaussian score and set-analysis nodes keep MAC 0 set eligibility separate from MAC 20 scans; run burden/SKAT/SKAT-O/ACAT-V/ACAT-O, strict score imports, discovery-only/all-cohort pooling, full set/mask/test correction and typed P-only result imports. Cohort omissions retain separate complete families, frequency eligibility, coverage and influence summaries without replacing the primary. Native consequences can now precede masks; unavailable direction/coverage and empty families remain explicit | Conditional independent-participant binary sets now support fixed-stratum/known-offset exact or independently piloted sampled tests and sealed completed-gene reuse; see the [binary rare-set guide](study-pipeline-tutorial.md#conditional-binary-rare-variant-sets). Unknown-slope/kinship binary and survival adapters, genome-wide scale, broader consequence/confidence sources, ancestry/external-frequency masks, cross-phenotype multiplicity, bias-aware rare-set enrichment and broader acceptance remain. Native/supplied primary rare sets now connect to explicit gene mappings, exploratory per-mask GO/Reactome with a joint mask-by-term family, and source-FDR-preserving contextual literature; see the [rare-set follow-up guide](study-pipeline-tutorial.md#rare-set-findings-in-enrichment-and-contextual-literature). Masks use explicitly mapped supplied or verified consequence records and observed scope-specific frequencies; omission comparisons are exploratory. |
| Participant QC/plots | Shared source-only `assay_qc` now supports native measurement audit/filter, exact detection bounds/cutoffs, complete supplied current-matrix decisions, fixed original populations, exact count totals and verified downstream matrices. See the [assay-QC guide](study-pipeline-tutorial.md#molecular-assay-qc-and-supplied-decisions). Registered `participant_qc` gates association on aligned complete cases and genotype call rates, retaining exclusions and analysis membership. The optional observed-call profile adds complete-source hash/LD panels, missing-aware KING all-pair decisions and unrelated-basis projected PCs with explicit model inclusion and supplied-PC reuse. Typed external sample-QC/PCA records now support exact source binding, per-cohort choices, separate source counts and requested native missing-PCA completion. It generates numeric/categorical PNG/PDF distributions, robust outlier warnings and declared categorical/cutoff subgroups, with corrected diagnostic mean comparisons. Separate registered two-level OLS/HC3 and Bernoulli logit/Firth interaction routes retain full testing-family dispositions and typed supplied results; see the [interaction guide](study-pipeline-tutorial.md#registered-adjusted-subgroup-interactions) and [sample-QC guide](study-pipeline-tutorial.md#registered-genotype-sample-qc-and-projected-pcs) | Complete assay/caller/sex/PAR QC, ancestry-reference appraisal, larger-scale scientific acceptance and broader external formats, dependence-aware and broader interaction adapters remain. Outcome-defined groups cannot establish effect modification. Table 1 verification remains separate. |
| Effect QC/comparisons | `StudyEffectQc` preserves pre-screen snapshots; automatic `effect-qc-unfiltered` sensitivity repeats cohort comparisons, completed primary meta-analysis, discovery pooling and validation. Branch-specific universes, adjusted-P denominators, primary/sensitivity differences and unchanged primary hashes are recorded | Continue model/assay-specific diagnostics and broader modality acceptance; heuristic exclusion still does not establish invalidity or calibrated FDR. |
| Replication/meta-analysis | Separate discovery pooling excludes validation estimates; all-cohort pooling receives original sources. Typed effect/context audits and supplied-sampling-covariance GLS now guard compatible scalar-effect pooling, with separate independent-validation checks. Missing/invalid matrices retain source counts and wait; omission/unfiltered branches use their actual source subsets. Declared tau/inference/confidence settings reach both scalar backends. | Complete automatic block partitioning, strict default completeness, source/model appraisal, unsigned-result dependence models, explicit primary designation and all declared influence diagnostics. Legacy incomplete contracts remain uncertified. See the [pooling contract](study-pipeline-tutorial.md#compatible-effects-and-covariance-aware-pooling). |
| Sensitivity/overlap | Native exact multiway memberships, two/three-set Venn and UpSet PNG/PDF, source/frozen validation families, explicit tested-universe coverage and registered I-squared or BH/BY Q alerts. Leave-one-cohort-out is default for at least three cohorts, with conditional/disabled policies, minimum retained cohorts and common-feature paired influence. Supplied diagnostics can skip refitting; missing source alert rules remain unavailable. Primary hashes and every fit disposition are retained | Extend unsigned/rare-set and multicontrast overlap, uniform primary selection across remaining downstream consumers and broader scientific acceptance. Diagnostic improvement cannot automatically replace the primary. See the [cohort tutorial](study-pipeline-tutorial.md#multiway-cohort-overlap-and-registered-heterogeneity). |
| Enrichment | Native preranked GSEA consumes complete scoped gene rankings, including supplied primary meta-analysis, with exact/seeded gene-set nulls, conditional-tail precision, NES, BH/BY, leading edges, PNG/PDF curves and pathway/module-GO integration. GO/pathway ORA and supplied results retain their own universes and correction families | Native binary or mixed-annotation S-LDSC now consumes a complete calibrated GWAS source and matched reference sums/cross-products, with joint binary category or continuous conditional-coefficient tests, standardized effects, log-space target correction, SNP QC and supplied-stage reuse. Native independent Gaussian OLS shared residual GSEA nulls and complete external model-null ranking reuse also connect. Broader native dependent/non-Gaussian GSEA nulls, multi-ancestry GWAS enrichment, LD/reference construction, broader weighting/scale/calibration acceptance and assay mappings remain. Native GSEA BH/BY is distinct from Broad's pooled-NES empirical FDR. See the [GSEA tutorial](study-pipeline-tutorial.md#native-ranked-gsea-and-leading-edge-pathway-networks) and [GWAS enrichment tutorial](study-pipeline-tutorial.md#ld-aware-gwas-functional-category-enrichment). |
| GWAS annotation/scoring | Native `variant_scoring` connects reference checks, versioned consequence imports/ANNOVAR/VEP/Ensembl REST and the official AlphaGenome SDK bridge or score imports. It inventories primary variants and rare-QC candidates without significance selection, retains transcript/tissue/scorer context, hashes local inputs and reuses sealed complete batches. `variant_annotation` can supply prespecified rare masks before association. | Complete real provider/installed-tool acceptance and broader formats/species/variant classes. SDK-format fixtures do not validate functional predictions; reference matching does not independently certify assembly. See the [native annotation contract](study-pipeline-tutorial.md#native-gwas-consequences-and-alphagenome-context). |
| Proteins/structures | Native exact accession/isoform/taxon/FASTA binding, supplied PDB/mmCIF assessment, versioned AlphaFold DB retrieval, explicit pLDDT/directional PAE, residue/peptide/variant mappings, local views and configured predictor contracts; supplied summaries and assessment-only work can skip acquisition. Synthetic configured-process and live public retrieval checks passed | Real new AlphaFold inference and broader structure/complex scientific validation remain unverified. Confidence and exact sequence identity do not establish function or causality. See the [protein tutorial](study-pipeline-tutorial.md#exact-protein-sequences-structures-and-confidence). |
| Scores | Registered explicit-partition quantitative development from participant tables/cohort matrices; complete frozen-model and external weight/score application; held-out frozen-baseline metrics, conditional independent-unit bootstrap, subgroup reports and complete related-trait BH/BY families. Supplied evaluation can skip to missing related testing. Private participant records stay outside aggregate dossiers | Native joint binomial elastic-net development and complete model reuse now connect; survival development, nested CV, genome-wide LD-aware scoring, broader calibration/transportability and real scientific acceptance remain. See the [held-out score tutorial](study-pipeline-tutorial.md#held-out-risk-scores-and-missing-related-phenotype-work). |
| Networks | Registered per-dataset residual correlation, native preranked GSEA to pathway overlap, bounded component/clique and joint module-GO operations; supplied graph/membership reuse, actual correction preservation, signed edges, threshold/optional-bootstrap diagnostics and GraphML | Phenotype/block-aware GSEA nulls, participant-resampling and broader preservation calibration, clustered resampling, community/WGCNA study orchestration, broader mappings/scale and real scientific acceptance remain. Dense native matrices retain a 2,000-feature bound. |
| Genetic evidence | Reviewed pairing, supported public discovery, all-significant-feature MR selection and caveated partial-region colocalization. Native MR declares primary IVW/Wald, separate BH/BY/Bonferroni/nominal families, duplicate-source tests, eligible-feature dispositions and provisional-family gates. Colocalization now retains pre-restriction coverage/gaps, all signal pairs, registered priors, convergence/fallback and complete/partial source grades in canonical results/dossiers. Supplied multi-signal/source fields survive reuse | Extend automatic assay/source adapters, cross-phenotype MR correction, LD-reference sensitivity and broader fine-mapping calibration. See the [colocalization tutorial](study-pipeline-tutorial.md#complete-colocalization-records-and-regional-coverage). Within-hypothesis correction does not establish instrument validity or remove selection bias. |
| Literature/manuscript | Study-wide inventory, full eligible-candidate AI comparisons, source/caveat validation, resumable sealed nodes and a 3–5-finding outline are registered. Typed normalization carries supported source-reported N/QC/interval fields into dossiers; pooled count sums have a distinct meaning. Native revisions link finding IDs and scientist overrides. Draft/rendered accounting supports a flexible 8,000-word target, explicit strict limits and separately counted authored supplementary discussions. Prompts require thorough mechanism, primary literature, alternatives and next experiments. Literature review defaults to 100 eligible mapped features with an uncapped eligible inventory and separate query budget; earlier explicit caps remain honored | Verify actual-model scientific depth/source appraisal, locus/story grouping, all assay/source formats and the full manuscript acceptance matrix. An absent N remains unknown; copying a normalized field does not independently certify it. Hierarchical AI ranking is an unreviewed recommendation. Protein/sequence/contextual work retains its 10-feature default; MR is independently scoped. |

See [variant follow-up](variant-followup.md), [prediction scores](prediction-scores.md)
and [network follow-up](network-followup.md) for existing commands and limits.
Runner changes require implementation and acceptance checks tracked in
[the implementation status](../analysis-pipeline-implementation.md). Existing
sealed results are retained; new software/defaults do not regenerate historical
studies or imply that old runs used the updated workflow.

### Implemented source-build settings

The following are accepted settings in the current source build; they are a
configuration fragment, not a complete study. The explicit OLS choice here is
only an example for an appropriate quantitative design. The resolver captures
the displayed QC and manuscript defaults for newly resolved genetic studies;
subgroups require explicit scientific justification.

```yaml
project:
  manuscript_policy:
    max_main_words: 8000
    word_limit_mode: target
    minimum_findings: 3
    maximum_findings: 5
hypotheses:
  - id: H
    analysis:
      association:
        model: ols
        genotype_kind: imputed
        minimum_mac: 20
        maximum_marker_missing: 0.02
        minimum_sample_call_rate: 0.98
        minimum_info: 0.8
      subgroups:
        - id: obesity-threshold
          column: BMI
          cutoff: 30
          comparison: greater_than_or_equal
```

The cutoff contract is exact; its clinical appropriateness depends on the study
population. Outcome-defined group comparisons do not receive circular
inferential P values. Local `participant_qc` artifacts contain the analyzed
phenotype table, exclusions, aggregate diagnostics and PNG/PDF distributions.
The association adapter consumes that selected table. Participant-bearing
artifacts are not eligible remote assistance payloads.

The default MAC filter applies to single-variant study/CLI scans. Rare-set input
eligibility retains separate defaults. The source build registers quantitative
and conditional-binary rare-set study branches; broader model/ploidy adapters
remain listed in the [implementation status](../analysis-pipeline-implementation.md).
Existing saved runs retain their original policies.

The source build also adds [GWAS QQ and Manhattan diagnostics](study-pipeline-tutorial.md#gwas-qq-and-manhattan-diagnostics)
for available cohort and pooled sources. It retains declared coverage,
frequency/count/INFO/test strata, finite-log P values, unresolved rows and
coordinate/build limitations. Descriptive calibration summaries do not perform
genomic control or replace the registered primary inference.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Wickenhagen A, et al. (2021) — A prenylated dsRNA sensor protects against severe COVID-19](../CITATIONS.md#wickenhagen-oas1-2021) — [PMID: 34581622](https://pubmed.ncbi.nlm.nih.gov/34581622/) · [PMCID: PMC7612834](https://pmc.ncbi.nlm.nih.gov/articles/PMC7612834/)
- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)
- [Rebecca DerSimonian and Nan Laird (1986) — Meta-analysis in clinical trials](../CITATIONS.md#dersimonian-laird-1986) — [PMID: 3802833](https://pubmed.ncbi.nlm.nih.gov/3802833/)
- [Julian P. T. Higgins and Simon G. Thompson (2002) — Quantifying heterogeneity in a meta-analysis](../CITATIONS.md#higgins-thompson-2002) — [PMID: 12111919](https://pubmed.ncbi.nlm.nih.gov/12111919/)
- [Aravind Subramanian et al. (2005) — Gene set enrichment analysis: a knowledge-based approach for interpreting genome-wide expression profiles](../CITATIONS.md#subramanian-gsea-2005) — [PMID: 16199517](https://pubmed.ncbi.nlm.nih.gov/16199517/) · [PMCID: PMC1239896](https://pmc.ncbi.nlm.nih.gov/articles/PMC1239896/)
- [Gene Ontology Consortium (2021) — The Gene Ontology resource: enriching a GOld mine](../CITATIONS.md#go-consortium-2021) — [PMID: 33290552](https://pubmed.ncbi.nlm.nih.gov/33290552/)
- [Kai Wang, Mingyao Li, and Hakon Hakonarson (2010) — ANNOVAR: functional annotation of genetic variants from high-throughput sequencing data](../CITATIONS.md#wang-annovar-2010)
- [M. Varadi et al. (2022) — AlphaFold Protein Structure Database: massively expanding the structural coverage of protein-sequence space with high-accuracy models](../CITATIONS.md#varadi-alphafold-2022)
- [Chris Wallace (2021) — A more accurate method for colocalisation analysis allowing for multiple causal variants](../CITATIONS.md#wallace-coloc-2021) — [PMID: 34587156](https://pubmed.ncbi.nlm.nih.gov/34587156/) · [PMCID: PMC8504726](https://pmc.ncbi.nlm.nih.gov/articles/PMC8504726/)
- [Jack Bowden, George Davey Smith, and Stephen Burgess (2015) — Mendelian randomization with invalid instruments: effect estimation and bias detection through Egger regression](../CITATIONS.md#bowden-mr-egger-2015) — [PMID: 26050253](https://pubmed.ncbi.nlm.nih.gov/26050253/) · [PMCID: PMC4469799](https://pmc.ncbi.nlm.nih.gov/articles/PMC4469799/)
- [B. E. Madsen and S. R. Browning (2009) — A groupwise association test for rare mutations using a weighted sum statistic](../CITATIONS.md#madsen-browning-2009) — [PMID: 19214210](https://pubmed.ncbi.nlm.nih.gov/19214210/) · [PMCID: PMC2633048](https://pmc.ncbi.nlm.nih.gov/articles/PMC2633048/)
- [Michael C. Wu et al. (2011) — Rare-variant association testing for sequencing data with the sequence kernel association test](../CITATIONS.md#wu-skat-2011) — [PMID: 21737059](https://pubmed.ncbi.nlm.nih.gov/21737059/) · [PMCID: PMC3135811](https://pmc.ncbi.nlm.nih.gov/articles/PMC3135811/)
- [Seunggeun Lee et al. (2012) — Optimal unified approach for rare-variant association testing](../CITATIONS.md#lee-skato-2012) — [PMID: 22863193](https://pubmed.ncbi.nlm.nih.gov/22863193/) · [PMCID: PMC3415556](https://pmc.ncbi.nlm.nih.gov/articles/PMC3415556/)
- [Yaowu Liu et al. (2019) — ACAT: A fast and powerful p value combination method for rare-variant analysis in sequencing studies](../CITATIONS.md#liu-acat-2019) — [PMID: 30849328](https://pubmed.ncbi.nlm.nih.gov/30849328/) · [PMCID: PMC6407498](https://pmc.ncbi.nlm.nih.gov/articles/PMC6407498/)
- [Hannah Wand, Samuel A. Lambert, et al. (2021) — Improving reporting standards for polygenic scores in risk prediction studies](../CITATIONS.md#wand-prs-reporting-2021) — [PMID: 33692554](https://pubmed.ncbi.nlm.nih.gov/33692554/) · [PMCID: PMC8609771](https://pmc.ncbi.nlm.nih.gov/articles/PMC8609771/)
- [Arthur E. Hoerl and Robert W. Kennard (1970) — Ridge regression: biased estimation for nonorthogonal problems](../CITATIONS.md#hoerl-kennard-1970)
- [Hui Zou and Trevor Hastie (2005) — Regularization and variable selection via the elastic net](../CITATIONS.md#zou-hastie-2005)
- [Alexander Lex, Nils Gehlenborg, Hendrik Strobelt, Romain Vuillemot and Hanspeter Pfister (2014) — UpSet: Visualization of Intersecting Sets](../CITATIONS.md#lex-upset-2014)
- [Jonathan J. Deeks, Julian P. T. Higgins, Douglas G. Altman, Joanne E. McKenzie and Areti Angeliki Veroniki (2024) — Chapter 10: Analysing data and undertaking meta-analyses](../CITATIONS.md#cochrane-meta-analysis-2024)
- [Gao Wang, Abhishek Sarkar, Peter Carbonetto, and Matthew Stephens (2020) — A simple new approach to variable selection in regression, with application to genetic fine mapping](../CITATIONS.md#wang-susie-2020) — [PMID: 37220626](https://pubmed.ncbi.nlm.nih.gov/37220626/) · [PMCID: PMC10201948](https://pmc.ncbi.nlm.nih.gov/articles/PMC10201948/)
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

The source build adds [frozen-module transfer across cohort graphs](study-pipeline-tutorial.md#cross-cohort-module-preservation)
with exact namespaces, source coverage, shared-node/edge/sign diagnostics and
typed stage reuse. These descriptive comparisons do not provide null-calibrated
preservation tests or select a new primary graph.


The [25-facet software acceptance](../analysis-pipeline-implementation.md#software-workflow-acceptance-2026-10-26) distinguishes implemented workflow contracts from live-model and scientific acceptance. [Managed variant software setup](variant-followup.md#application-managed-software-download-and-installation) is performed by the application, with explicit registration handling for ANNOVAR.
