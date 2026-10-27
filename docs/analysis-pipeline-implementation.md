# Analysis pipeline implementation status

Objective: implement all facets of the [analysis pipeline specification](vignettes/analysis-pipeline-specification.md), including its scientific safeguards and complete study-run integration. This checklist tracks the full objective; an implemented method or a passing narrow test is not evidence of complete workflow conformance.

Status vocabulary: pending, in-progress, implemented-unverified, verified. External service tests and real scientific execution are reported separately from local fixture validation.

The [managed variant software checkpoint](#application-managed-variant-software-checkpoint-2026-10-26) records application downloads, automatic study setup, the actual AlphaGenome install and current source/JAR validation.

The [scoped public evidence checkpoint](#scoped-public-evidence-citation-checkpoint-2026-10-26) records the preceding source/JAR and verifies that model responses can actually cite matching public passages as evidence and counterevidence.

The [public passage checkpoint](#public-passage-context-checkpoint-2026-10-26) records its source/JAR, native public-evidence handoff, 21 new tests and exact packaged verification.

See the [current finite 24-requirement audit](#finite-original-requirement-audit-and-citation-checkpoint-2026-10-26) for its source, 62-workflow inventory, targeted verifier correction and provider/scientific evidence boundaries.

**Current software acceptance: the requested workflow branches are implemented
and contract-verified within the documented input/model capabilities.** See the
[25-facet acceptance roll-up](#software-workflow-acceptance-2026-10-26) for the
requirement-to-source/workflow evidence. This covers the local
`codex/analysis-pipeline` checkout and retained verified runtime, including
uncommitted work. External live/scientific acceptance and public release status
remain separate; a configured adapter is not a claim that a real prediction or
scientifically reviewed manuscript has been produced.

The [partial-stage guide](vignettes/study-pipeline-tutorial.md#partial-supplied-stages-and-native-completion)
distinguishes a successful import from completed analysis coverage. Explicit
partial primaries retain separate evidence, permit requested native completion,
preserve source corrections, prevent cohort fallback from an incomplete primary
pool, and require network completion to match the declared population.

The [pooled-SNP guide](vignettes/study-pipeline-tutorial.md#snp-annotation-from-a-pooled-gwas) adds the previously missing native variant-catalogue route from a registered GWAS pool, with explicit source coverage, allele checks, per-alias provenance and multi-context supplied scoring reuse. Its acceptance is separate from external program/model execution, biological calibration and genome-wide performance.

The [automatic supplied-primary guide](vignettes/study-pipeline-tutorial.md#automatic-follow-ups-from-a-supplied-primary-meta-analysis) describes completion from a meta-analysis without original datasets: declared source scope can schedule missing GWAS enrichment, ranked GSEA/pathway modules, GO and protein context while respecting existing completed-stage owners and primary-source selection. The current source-only integration acceptance is recorded in the checkpoint below; this does not establish all external-source or SNP-annotation routes.

## Historical implementation ledger and capability extensions

The table below retains earlier progress labels and broader capability/live-validation work. The current requested software-facet verdict is the acceptance roll-up linked above.

| Requirement | Status | Required acceptance evidence |
| --- | --- | --- |
| Resolved, validated workflow defaults and capabilities | in-progress | Schema/resolver/planner tests; explicit model, QC, testing families, dependencies and effective parameters |
| Typed supplied results at any stage; execute only missing work | in-progress | Per-hypothesis typed imports retain snapshots, scopes, methods, external attribution, families and dependencies. Typed supplied bibliographies now feed manuscript citations, and exact complete literature-query contributions permit missing native retrieval with separate source-reported coverage. Explicit MR contributions own declared completed features while native MR fills uncovered eligibility, with reserved invalid coverage, original source adjustments and a separately corrected joint family. Native MR/colocalization/fine-mapping now retain per-pair completion receipts and resume matching completed pairs while recomputing current stage coverage/correction. Automatic primary metadata now requests missing native GWAS enrichment, GSEA/pathway modules, GO and protein context without reconstructing cohort matrices. Whole-stage meta/GO/network/MR/coloc/rare-set reuse and missing native GO/module-GO have separate acceptance cases. Kind-aware supplied trait/population/context checks now hold known mismatches, preserve missing declarations as uncertified and propagate sealed parent conflicts. External GWAS/evaluation and annotation contexts retain their distinct roles. Standalone completed modules/module GO and an all-supplied inventory-to-impact-to-manuscript route now have exact-JAR acceptance below. Complete external-format/source appraisal and real scientific discussion quality remain. |
| Participant, assay, genotype QC and distribution/subgroup diagnostics | in-progress | `StudyPrefitQc` selects aligned complete cases and creates local exclusion/membership tables and distribution diagnostics. The opt-in observed-call profile adds autosomal sample call rates, complete-source hash/LD panels, missing-aware KING decisions, unrelated-basis projected PCs, supplied-PC reuse and aggregate finding context. Typed external QC/PCA now validates exact source/sample/hash scope, preserves source populations and bases, permits per-cohort choices and explicitly completes missing native PCA. Scientific source appraisal and broader external/scale acceptance remain. Registered two-level adjusted OLS/HC3 and Bernoulli logit/Firth interactions now have native and typed supplied-result routes, separate complete-family adjustment and aggregate finding context. A source-only assay producer now supports native audit/filter, scale/missingness/constant/SD/detection checks and exact supplied decisions, with bounded scans, immutable derived matrices and verified consumer bindings. Platform chemistry, dependence-aware and broader interaction adapters remain. |
| Missing-model literature suggestions and branch gate | in-progress | Native reviews now use explicit public labels, controlled assay terms and declared endpoint/dependence context, bounded recent-primary searches and valid CLI candidate names. Missing, blank and null models retain a blocking review; an explicit model continuation has independent OLS references. Supplied literature count/year tokens are validated exactly before reuse. Source-specific checks are recorded below; actual scientific model suitability and source appraisal remain separate. |
| GWAS single-variant QC, MAC >= 20 and calibrated model routing | in-progress | Source-aware QC and conditional exports execute. Explicit GRCh37/38 autosome/PAR/XX-X/XY-X/XY-Y independent OLS/GLM contrasts now select declared complements, normalize physical dosages before MAC and retain local call/participant audits. Registered independent baseline Cox full fits now support explicit molecular and human-autosome survival endpoints, adaptive exact finite-MPLE certificates with whole-family holds and separate efficient PH diagnostics. Standalone molecular/genotype efficient-score scans certify their nuisance null, retain one-step effect semantics and expose unadjusted or adjusted source-unit scores/covariance. Pooled X, mixed/frailty, aneuploid, mitochondrial and native rare-set ploidy models remain. Explicit Firth LRT and finite conditional hard-call score routes retain typed inference, convergence/log tails and separate effect/test semantics. Exact tests condition declared stratum case counts with fixed known offsets; valid P-only tests remain eligible and incomplete exact families withhold native BH and official selection. Caller/assay, biological sex/PAR, ancestry/kinship and broader sparse binary/model routing remain. Exact null enumeration demonstrates that Firth asymptotic tails are not universally calibrated. Registered QQ/Manhattan diagnostics now retain full numerical exports, log-P precision, source/contrast and MAF/MAC/INFO strata, explicit testing coverage and native contributor-coordinate provenance; these descriptive views do not certify calibration or change primary statistics. |
| Rare-variant masks, independent eligibility and unsigned results | in-progress | Registered mask/score/analysis nodes, independent MAC 0 set eligibility, all five tests, strict imports, complete families, discovery/validation and cohort influence execute. Native consequences and an opt-in versioned external population ALT-frequency screen now precede masks, with exact allele/build identity and complete-coverage gates. Conditional binary independent-participant set tests now add exact/independent-pilot Monte Carlo nulls, fixed-null source validation, per-gene reuse and scope-complete adjustment. Remaining: unknown-slope/kinship binary and survival/ploidy adapters, broader consequence/confidence sources, external-resource conversion/confidence-bound filters and population appraisal, cross-phenotype correction, genome-wide scale and bias-aware rare-set enrichment. The new primary projection, exploratory mask-by-term GO/Reactome and contextual literature are described in the downstream checkpoint. Registered cross-mask set/gene combination now adds a separate complete unit family, scoped external omnibus reuse, frozen unsigned validation and primary/supplemental selection; its source-specific checkpoint follows below. |
| Effect QC and unfiltered sensitivity with immutable inputs | in-progress | Verified for supported signed-result/matrix routes: snapshots, separate eligible/BH families, cohort comparisons, discovery pools, validation and unchanged primary hashes. Full gate and two packaged fixtures passed. Unsigned result routing and broader modality/scale acceptance remain part of the complete specification. |
| Compatible analysis blocks and dependence handling | in-progress | A typed effect contract, context/known-GWAS-coding audit and native supplied-sampling-covariance GLS now connect primary/discovery pooling and sensitivities. Independent validation remains separately gated. Strict completeness is opt-in; automatic block partitioning, source appraisal and broader dependence models remain. Current acceptance evidence follows below. |
| Discovery-only pooling and frozen validation families | in-progress | Native and supplied discovery pools use sealed source-family selection. Metadata-only cohort references now support missing validation without original discovery files. Exact source membership/trait and explicit cross-cohort independence are gated; known context/coding conflicts hold, with optional strict source-effect completeness. Native all-cohort estimation cannot substitute pooled references for constituent estimates. The checkpoints below record source-specific acceptance. Scientific source/calibration appraisal and broader source/model scope remain. |
| Primary all-cohort meta-analysis, overlap plots and sensitivity | in-progress | All-cohort pooling, exact multiway scalar and primary rare-set/mask/test overlap with Venn/UpSet, registered I-squared/Q alerts and retained omissions execute. Sensitivity baselines require unique completed producer types and verified artifact hashes; influence compares a registered common feature universe. Complete rare cohort families remain usable while pooled scopes wait. The shared primary-source contract connects downstream consumers, with scoped exceptions for independent branches. Multicontrast and cross-cohort gene-level aggregation remain; current verification is recorded below. |
| GO/pathway, ranked GSEA and GWAS enrichment | in-progress | Native preranked GSEA now uses complete scoped gene rankings, exact or seeded Monte Carlo gene-set nulls, conditional-tail precision, BH/BY families, leading edges and PNG/PDF curves, with pathway/module-GO reuse and independent numerical acceptance. Native S-LDSC now fits prespecified binary or mixed binary/continuous annotation models with matched LD/reference moments, typed category/conditional-coefficient tests, standardized effects, log-space complete-family correction, source reuse and numerical references. Independent Gaussian OLS shared residual permutations and complete externally produced model-null ranking ensembles now connect; broader dependent/non-Gaussian native null adapters, multi-ancestry GWAS enrichment, broader weighting/reference/scale acceptance, assay mappings and real scientific acceptance remain. |
| GWAS ANNOVAR/AlphaGenome annotation and scoring | in-progress | Native dispatch, explicit references, versioned consequence imports/tools, official SDK/score imports, complete variant dispositions, sealed batches, rare-mask integration and contextual finding evidence now connect. Current acceptance evidence is recorded below; real prediction/installed-tool and broader variant scope remain. |
| Protein/isoform mapping and AlphaFold confidence/prediction | in-progress | Native exact-sequence supplied-model assessment, versioned live retrieval and configured-process adapter; pLDDT/PAE, mapping, views and partial-work reuse verified below. Real new AlphaFold inference and broader structure/complex scientific validation remain unverified. |
| Risk scores, independent evaluation and related phenotypes | in-progress | Native explicit-partition quantitative development, direct cohort matrices, complete frozen-model/weight/score reuse, conditional cluster-bootstrap metrics, subgroup diagnostics and separate BH/BY related-trait families now connect. Supplied evaluation can retain its metrics while only missing related tests execute. Native binary joint elastic-net development and frozen reuse now connect; native grouped nested quantitative/binary development now connects with local fold audits and separate independent evaluation; native complete frozen BED/VCF/BCF/BGEN weight application now connects with whole-source admission and mixed-cohort evaluation; native survival development, genome-wide LD-aware training, broader calibration/transportability and real scientific acceptance remain. |
| Pathway and molecular networks/modules/cliques/GO | in-progress | Native per-dataset covariate-residual correlation, source-FDR-preserving pathway overlap, supplied memberships, bounded components/cliques and joint module-by-term GO are registered. Signed edges, threshold/optional-bootstrap diagnostics, mapped backgrounds, source caveats and GraphML are retained. Supplied cohort graph scopes now reserve coverage while missing native datasets execute; metadata-only references, multiple disjoint supplied graphs and an explicit supplied-only policy are supported. Frozen reference memberships now transfer across compatible native/supplied cohort graphs with explicit node coverage, edge retention, sign agreement and source/bound audits. A separate optional conditional target-label stage now adds exact/sampled frozen-edge tests, precision, complete-family correction and provided-result reuse. Participant/block resampling, broader scientific null calibration, community/WGCNA study integration, broader namespaces/scale and real scientific acceptance remain. |
| Assay-matched source discovery, all-eligible MR and MR multiplicity | in-progress | Native reviewed/automatic MR records a separate prespecified primary family, source-based duplicate handling, correction bounds and provisional coverage gates. Eligibility now precedes annotation; missing/ambiguous features remain in the inventory and denominator. Reviewed exact-feature locations can enable MR without inferred gene names, with source-linked dossier context and caveats. Declared cross-hypothesis primary MR reporting families now have a native completeness-gated adjustment and dossier route. Broader assay/source adapters, other cross-phenotype analysis families and scientific appraisal remain. |
| Complete/partial-region colocalization and mandatory limitations | in-progress | Native pre-restriction source inventories, regional matching/gaps, all signal pairs, registered prior grids, convergence/fallback, partial grades/caveats and complete dossier rows now connect. Supplied multi-signal/coverage fields and declared posterior precision survive import. Fifteen packaged cases, independent posterior/coverage checks and a frozen full gate are verified below. Source-specific LD sensitivity, broader source/assay scope and independent calibration remain. |
| Bounded literature review and full evidence inventory | in-progress | Default 100-signal retrieval, separate query budget, strict full-family FDR and uncapped eligible mapped inventory now execute; earlier explicit caps remain honored. Rare-set source projections now retain mapped/unmapped and primary/secondary dispositions, original family adjustment and mixed-source literature context. A full-pool provisional impact review can now allocate the literature cap before retrieval, with sealed response reuse and explicit waiting states; its source-specific checkpoint follows below. Real scientific source/impact appraisal and broader annotation/source coverage remain. |
| AI impact dossiers and 3–5 manuscript findings | in-progress | A study-wide uncapped inventory, full eligible-candidate batch comparisons, source/caveat validation, distinct-story selection, resumable sealed responses and an evidence-linked outline now execute. Registered allele-resolved LD components add a separate redundancy constraint to AI and human principal-finding slots. Real-model scientific appraisal, complete source/assay coverage, automatic LD extraction and causal-signal/independence appraisal remain. |
| Flexible 8,000-word main text and complete finding supplements | in-progress | Default target versus explicit strict limits, separate counts, complete authored supplements and linked finding IDs integrate. A local claim ledger now binds frozen results, prose and public source snapshots, tracks inference dependencies and eight depth facets, and preserves review needs or rejects mechanical gaps in strict mode. Explicit native full-text acquisition retains identity/license/raw-response audits and reusable sealed copies. Actual scientific depth, semantic support and real-model source appraisal remain separate acceptance requirements. |
| Full local acceptance gate and packaged end-to-end examples | in-progress | Passing isolated snapshots and their exact test counts, optional skips and source identities are recorded in the source-specific checkpoints below; they do not automatically validate later dirty-worktree changes. Signed-result, participant-level, rare-set influence/downstream enrichment, literature, metadata, MR, impact/native-manuscript, annotation, supplied-covariance GLS, complete-supplement, typed-import/missing-work, native/supplied network/module-GO, native preranked-GSEA, held-out risk-score, protein-context and scalar/rare multiway cohort-diagnostic packaged checks have independent verifiers. The cohort, LD, MR-coverage and MR-completion checkpoints below record exact source-specific verification. Full modality/result-type and external coverage remain. |
| External-service/installed-tool verification | in-progress | Real Europe PMC model review and one native Ensembl consequence case succeeded. The official AlphaGenome SDK's serialization was checked without API requests. Actual AlphaGenome prediction and installed-tool checks remain; retrieval is distinct from scientific appraisal. |

## Current conformance audit, 2026-10-26

The local workflow **partially follows the revised specification**. The current
branch contains uncommitted implementations beyond release v0.3.7; the version
label on a locally built JAR does not establish public-release equivalence.
The detailed checklist above and acceptance checkpoints below distinguish
implemented study branches from standalone methods, configured commands,
optional external services and completed scientific analysis.

The main connected branches are source assay audit/filter and supplied-QC reuse, participant selection/distribution plots,
registered observed-call/KING/projected-PC sample QC, missing-model gating, single-marker MAC 20, separate quantitative and conditional
binary rare-set tests, signed-effect QC and unfiltered sensitivity, scalar and
gene/mask/test cohort comparisons,
discovery-only pooling, frozen validation, all-source meta-analysis and cohort
omissions. Native variant follow-up, bounded literature retrieval, reviewed MR,
impact-assessment contracts and manuscript count/word gates also connect for
their documented input types. These statements do not cover every modality,
phenotype model or external source.

Typed completed memberships and module GO now have standalone import routes.
An entirely supplied synthetic study has completed native inventory, impact
protocol selection and manuscript drafting, followed by a source-bound authored
main/supplementary revision. The [completed-study guide](vignettes/study-pipeline-tutorial.md#completed-analyses-through-manuscript)
distinguishes direct completed GO reuse from computing missing GO with a full
background. This source-build progress does not establish complete workflow
conformance or scientific quality of AI appraisal.

The major remaining workflow gaps are complete assay/genotype QC and calibrated
broader sparse/binary, mixed/frailty and survival rare-set/ploidy routing; broader source/assay appraisal and automatic
compatible-block partitioning and multicontrast overlap beyond registered rare-set routes;
broader dependent/non-Gaussian native ranked-enrichment nulls and multi-ancestry GWAS
enrichment, broader weighting/reference/scale acceptance; real new AlphaFold inference and broader structure/complex scientific validation; broader binary/survival and dependent-design validation
and native genome-wide LD-aware score development;
participant/block resampling for cross-cohort preservation and broader module/stability adapters; source-specific colocalization LD sensitivity and broader fine-mapping/source calibration;
and real-model scientific appraisal with complete claim/source coverage.

The original pseudocode should retain these corrections:

- Register the primary cohort set, estimand/model, eligibility, testing families
  and sensitivity rules before inspecting results. Lower heterogeneity or
  greater hit overlap cannot automatically promote an omitted-cohort result.
- Treat large betas and unusual distributions as audit signals. Check units,
  coding, sparsity and model fit; retain exclusions and an unfiltered branch.
- Apply MAC 20 to each actual single-marker analysis population. Preserve
  eligible lower-MAC variants for separately defined gene/region tests.
- Keep discovery selection, validation and all-cohort estimation distinct;
  audit overlap/dependence and do not count a discovery pool twice.
- Separate functional annotation, molecular-effect prediction and structural
  confidence from association significance and evidence of causality.
- Declare enrichment backgrounds, score evaluation splits, network definitions,
  MR instruments/families and colocalization coverage rather than running
  unspecified methods whenever an input file exists.
- Compare all eligible findings for scientific impact using source-linked
  dossiers. Choose 3–5 distinct supported stories around an adjustable default
  8,000-word main-text target; retain complete discussions in linked supplements,
  the full inventory and explicit evidence limitations.

This audit found a concrete sensitivity-baseline defect: selecting any
dependency file named `meta-analysis.tsv` could select a discovery-only pool
instead of the all-cohort producer. The executor now uses the unique completed
`meta_analysis` producer and its manifest-verified artifact, and independently
uses the completed `discovery_replication` producer for validation metrics.
`decision.yaml` records the exact project-relative artifacts and SHA-256s.
Incomplete or unlisted artifacts cannot authorize cohort omissions; changed
hashes or ambiguous producers fail explicitly. Discovery and supplementary
pools remain distinct. Execution-cache implementation 13 invalidates prior
cached sensitivity behavior.

The existing scalar sensitivity summary still uses descriptive BH 0.05 and
I-squared 50 thresholds and compares aggregate eligible families. These are
not a complete registered heterogeneity-alert policy or a common-feature
influence comparison. Supplied-covariance fits correctly leave conventional
I-squared unavailable; they need their own appropriate generalized-Q alert
policy. None of these diagnostics selects a replacement primary result.

The initial focused regression gate passed **45 tests, zero failures/errors or
skips**, covering seven new baseline tests plus pooled-discovery, effect-QC and
pooling workflows. Final acceptance evidence for this change is recorded below.

## Historical conformance audit, 2026-10-21

This preserves the audit before the subsequent fixture corrections,
cohort-influence, literature and AI-impact work. Current acceptance evidence is
recorded in the checkpoints below; historical failures are retained rather than
overwritten. In particular, the top-ten literature default and absence of an
AI-impact stage described in this historical audit have since changed.

The connected study workflow includes explicit model gating, participant-level
selection and distribution diagnostics, signed-effect QC and unfiltered
sensitivities, cohort effect comparisons, discovery-only pooling, validation,
all-cohort meta-analysis, pairwise overlap and cohort-omission sensitivity.
Genetic study defaults include single-marker MAC 20, marker missingness 0.02
and sample call rate 0.98, with INFO 0.8 for declared imputed data. These filters
do not constitute complete genotype QC or guarantee sparse-test calibration.

Rare-set operations use independently declared annotation masks and eligibility,
with native quantitative OLS/GRM score generation and burden, SKAT, SKAT-O,
ACAT-V and ACAT-O tests. The single-marker MAC 20 filter does not delete eligible
MAC 1/19 members of those sets. Binary/survival/ploidy-aware rare-set study
adapters and broader dependence handling remain prerequisites or further work.

GO/Reactome, reviewed-source MR/colocalization, public-source discovery and
AlphaFold metadata retrieval have existing implementations; their presence
does not complete all requested branches. ANNOVAR/VEP, prediction scores and
network tools also exist as standalone capabilities. Automatic full-universe
ANNOVAR/AlphaGenome scoring, exact protein-isoform structure assessment,
leakage-safe score orchestration, ranked GSEA/GWAS enrichment, network/clique
follow-up and the complete scientific acceptance matrix remain incomplete.

The manuscript validator enforces the declared finding-count and word-count
contracts, defaulting to 3–5 principal findings and 8,000 main-text words.
`candidate_prioritization` still orders association candidates by P value;
there is no complete automatic AI impact-dossier/rubric/selection stage.
Contextual literature searches default to 10 features, configurable up to 100;
this differs from reviewing every eligible significant feature when there are
at most 100. Neither a generated template nor a successful search establishes
scientific appraisal or a completed manuscript.

A fresh focused source test run covered `StudyRareVariantsTest`,
`StudyPrefitQcTest`, `StudyEffectSensitivityTest`, `StudyPipelineCliTest`,
`StudyWordBudgetTest` and `StudyWorkflowIntegrationTest` using one sequential
Gradle process. **49 tests ran: 47 passed, two failed, no errors or skips.**
The failed assertions are:

- `repeatedCohortSourcesCannotBecomeIndependentSetReplication`, line 218:
  the preceding waiting-state assertion passes, but the raw YAML message
  substring assertion fails. The source contains the distinct-cohort guard;
  this result does not demonstrate an independent-cohort pooling failure.
- `relatedStudyScoresMatchAnIndependentBalancedRandomInterceptReference`,
  line 327: the study does not reach the expected completed state. The fixture
  writes `annotation.tsv` but omits `files.variant_annotation`, which the mask
  operation requires. Its subsequent numerical reference assertions were not
  reached, so this run cannot establish GRM-score agreement.

The exact invocation was `gradlew.bat test` with those six fully qualified
class names supplied through repeated `--tests`, followed by `--no-daemon
--no-parallel`. The fresh reports are under `build/test-results/test/` and
`build/reports/tests/test/`. This was a focused source audit, not a new full
gate or packaged/external-service validation. No source or test fixes were
made as part of this audit. Historical passing runs below retain their original
scope and do not override these current failures.

## Earlier execution checkpoints

The feature branch is `codex/analysis-pipeline`; the initial baseline was
`57ec05d`. Existing documentation changes were preserved. New source code includes
workflow-policy resolution/validation, the MAC 20 CLI default and study filter
forwarding, explicit model review/gating, prefit sample selection/plots,
discovery-only pooling, isolated unfiltered effect-QC sensitivity, three-to-five finding validation, and rendered main-text
word accounting with a persisted `word-counts.yaml` artifact. Empty post-QC
testing families are retained explicitly rather than converted into fake null
associations or a malformed-table error.

An earlier sequential local gate, `gradlew.bat check executableJar --no-daemon
--no-parallel`, passed: **1,183 tests, zero failures/errors, five skips**. Two
skips are opt-in public genetics source tests; three require optional CHOLMOD.
Website/schema checks passed. Earlier gates exposed an outdated tiny-MAC
fixture, a wording expectation, and effect QC rejecting a valid empty input
family; those issues and the new sensitivity operation's resume-count
expectations were corrected and are covered by the passing gate.

The unfiltered-effect tranche adds `StudyEffectSensitivity` and
`StudyResultTables`. Pre-screen snapshots are hashed; invalid/failed estimates
and upstream participant/genotype exclusions remain excluded. Each branch
resolves its common universe without rewriting a sealed producer. Discovery-only
pooling retains discovery coverage independently of validation; cohort-omission
sensitivities pool all remaining discovery cohorts before validation. Entirely
excluded primary results remain not estimable and are never replaced by an
unfiltered result. A missing or changed snapshot cannot silently reduce the
cohort set. The affected 38-test gate passed. The final broad gate also covers
pooled-discovery omissions, all-excluded primary families, changed/missing
snapshots, immutable repeated attempts and cache invalidation.

Packaged evidence for this tranche is retained in
`build/effect-qc-unfiltered-smoke-20261021/` (run
`run-plan-20bfcb578a98-attempt-0002`, eight completed operations) and
`build/matrix-effect-qc-smoke-20261021/` (run
`run-plan-91195af20ba3-attempt-0002`, fourteen completed operations). Both independent
verifiers and `study verify` passed. The imported fixture has 30 primary versus
31 unfiltered pooled features and preserves the invalid-SE exclusion. The
matrix fixture preserves the MAC 19 exclusion and restores `v40` only in the
unfiltered effect branch; all three cohorts retain 62 analysis participants.
Each `packaged-verification.json` records its exact tested JAR hash
(`eaf256a669dae32df6e2b0523df6f1c36928bc718f46608305116767319cd4a3`)
and limited scope. The earlier attempts/reports are retained; verification
checks their evidence as well. The unfiltered effect scatterplot was visually
inspected. Reproduction uses `effect-qc-workflow.py` or the genotype scripts with
`--effect-qc` in `examples/study-pipeline/`.

Packaged CLI evidence is retained at
`build/analysis-pipeline-smoke-20261021/`, run `run-plan-ea29bf02a471`.
All 14 operations completed, and `study verify` passed. The reproducible
generators/checker in `examples/study-pipeline/` verify 62 analyzed samples per
cohort, exact exclusion reasons, MAC 19 excluded/MAC 20 retained, discovery-only
inverse-variance pooling, validation using those discovery estimates, and
all-cohort pooling without duplicating the discovery pool. Source hashes are
unchanged. The run produced 78 PNG/PDF files with valid signatures; the primary
phenotype histogram was visually inspected. `packaged-verification.json`
records the tested scope and exact JAR SHA-256. This does not establish rare-set,
service-adapter, AI-ranking or full-pipeline conformance.

A separate real missing-model example at
`build/model-review-live-smoke-20261021/`, run `run-plan-a55f7dcc8938`, retrieved
20 recent Europe PMC candidates with HTTP 200 using only the public label
`body mass index`. It completed independent diagnostics and retained one
`waiting_for_input` association operation with `association_model_required`;
no undeclared association was fitted. `study verify` passed. The query is broad:
recency and successful retrieval are not evidence of assay/design suitability,
and model appraisal remains incomplete. Other external services and installed
annotation/prediction tools were not verified by these examples.

The implementation is partial. The checklist retains every outstanding facet.

## Rare-variant tranche

The source build now plans `rare_variant_masks`, `rare_variant_scores` and
`rare_variant_analysis`. Masks precede test statistics; score generation uses
prefit samples and its own MAC/call-rate/INFO/HWE policy, preserving eligible
MAC 1/19 variants that the MAC 20 single-marker scan excludes. Explicit Gaussian
OLS or GRM REML and declared phenotype units are required for native export.
Genotype/assay/effect units cannot silently become phenotype units. Score imports
require strict quantitative model compatibility; P-only inputs have a separate
stage and reviewed cohort-combination contract. Unsigned tests never acquire
beta/SE/direction or enter signed adapters. Full set/mask/test FDR is retained
in the primary-test preview. Missing validation coverage and unestimable
direction remain unavailable; no family is reconstructed from selected hits.

The first full gate for this tranche passed 1,191 tests, zero failures/errors,
five optional skips. It covered native and imported quantitative scores,
MAC/quality/frequency separation, empty masks, binary-outcome rejection,
P-only correction/coverage and unsupported ploidy. The later focused conformance
audit above found two failing assertions after subsequent safeguards and tests;
both were corrected and all 11 then passed, reaching the independent GRM
numerical assertions. The subsequent cohort-influence gate passed **1,197 tests,
zero failures/errors and five optional skips**, including all 14 rare-set tests.

`leave_one_cohort_out` defaults to true for supported rare-set analysis and
retains at least two cohorts plus the configured minimum. It writes separate
sensitivity families and primary-versus-omission comparisons, including changed
frequency eligibility, counts, missing coverage and available burden directions.
No alternative replaces the primary. New checks cover an unchanged primary with
influence disabled, minimum-cohort enforcement, an unestimable omission and
frequency-driven membership changes. The packaged verifier now reconstructs
every omission's burden/ACAT values and BH family independently. Resolver
defaults version 4 and execution cache implementation 7 prevent silent reuse of
outputs made under the earlier behavior.

Initial packaged examples at `build/rare-native-smoke-20261021/` (16 completed
operations) and `build/rare-unsigned-smoke-20261021/` (four completed operations)
passed `study verify` and independent verifiers. The native verifier reconstructs
discovery-only and all-cohort burden beta/SE/P directly from exported U and V/N,
checks MAC 1/19 retention, excludes low-quality/common-mask variants and retains
raw input hashes. Each bundle retains the exact tested JAR and verifier source.
Fresh packaged influence examples at `build/rare-native-influence-20261021/`
(run `run-plan-717b74901dd1`, 16 completed operations) and
`build/rare-unsigned-influence-20261021/` (run `run-plan-66254402d2d7`, four
completed operations) passed independent verifiers and `study verify`. They
retain 30 and nine omission-result rows, respectively, with separate full-family
BH checks. The native verifier reconstructs every omitted-cohort burden result
from U and V/N; the P-only verifier reconstructs ACAT independently. Both preserve
source hashes and pin JAR SHA-256
`449fb611bd5f5ffdcbcc13386c4cd2029bc070f47a2ff90a739306cf86c30377`.

## Literature coverage tranche

Literature retrieval now defaults to 100 eligible significant mapped features,
separate from contextual protein/sequence limits and uncapped MR. An existing
explicit `max_features` remains the fallback unless the dedicated
`literature_max_features` overrides it. `literature_max_queries` defaults to
1,000 for this operation only; other public-evidence operations retain 200.
Strict full-family BH eligibility precedes capping and uses only pooled meta
rows when present. `literature-selection.tsv` retains the uncapped eligible
inventory, out-of-budget rows, retrieval states and pending scientific appraisal.
Budget exhaustion leaves the operation waiting rather than calling later
unqueried findings negative. Query limits are included in request audits.

The focused 49-test gate (29 bioinformatics, 14 rare-set and six workflow
integration tests) passed. It covers 125 eligible features with 100 selected,
more than 200 successful mock requests, preserved smaller legacy caps, explicit
budget exhaustion and exact FDR 0.05 exclusion in a full pooled family. Resolver
defaults version 5 and cache implementation 8 record the new behavior.

The packaged offline example at
`build/literature-selection-offline-20261021/`, run
`run-plan-efc1e958b20b`, has three complete operations and one literature
operation correctly waiting for retrieval. Its independent standard-library
verifier and `study verify` passed: BH was reconstructed across all 225 source
tests, all 125 eligible mapped features were retained, exactly 100 selected and
25 labeled outside the review budget. Source hashes were unchanged and no
network requests were sent. The exact JAR is pinned with SHA-256
`476b977a9abae7952ad8d501524abbbdd5a9c708ef085226925c8ac9adf75a61`;
the verifier source/hash and explicit incomplete-appraisal disposition are kept
in the bundle. The same JAR also verifies both retained rare-influence bundles.
This establishes offline selection and provenance, not live 100-feature service
coverage, source appraisal or AI impact ranking.

The final combined `gradlew.bat check executableJar --no-daemon --no-parallel`
gate passed **1,200 tests, zero failures/errors and five optional skips**.
The skips remain two opt-in public genetics tests and three optional CHOLMOD
tests. Website/schema checks passed. The packaged literature JAR above matches
this gate; the earlier rare-influence bundles retain their own exact software
and are also verifiable by this JAR. No claim of full workflow conformance or
live 100-feature retrieval follows from this checkpoint. Every remaining
requirement in the checklist remains active.

## Study-wide impact and manuscript tranche

The planner now creates one study-wide `finding_inventory`, `impact_assessment`
and optional `manuscript_draft`, rather than separate per-hypothesis paper drafts.
Publication objectives request impact assessment by default in automatically
resolved goals; explicit manual goals are retained. Inventories preserve all
primary, null, invalid and excluded rows and full-family eligibility. AI
comparisons receive every eligible candidate, source-linked aggregate dossiers,
eight rubric dimensions and required caveats. Distinct story groups constrain
the 3–5 principal findings. An explicit declaration can nominate a meaningful
null but cannot revive an invalid estimate. Comparison nodes are hash-verified
and resumable under call budgets. Hierarchical comparisons do not establish a
globally optimal ranking or a calibrated impact probability.

The selected IDs, complete candidate dispositions, reasons, supporting and
counterevidence, caveats, and section/finding word allowances are retained in
`impact-assessment.yaml`, `impact-candidate-dispositions.tsv` and
`manuscript-outline.yaml`. Native revision finding IDs must link to Results;
changed selections require an explicit scientist override reason. Default main
text remains capped at 8,000 words. Generated draft and native rendered counting
are separate from the provider output-token budget. A local loopback model route
uses the existing disclosure policy; browser replies still require genuine human
attestation. The deterministic harness is an external protocol fixture, not an
AI or a human actor.

Eleven impact tests and a new raw-draft word-count test brought the completed
suite to **1,212 tests, zero failures/errors and five optional skips** before the
native imported-result correction below. These checks cover all-candidate batch
coverage, strict source/caveat IDs, null/exclusion eligibility, sealed resume and
tamper rejection, local-only transport, distinct groups, partial-region caveats,
planner ordering and native finding-ID/override review. They do not establish
actual-model quality, source appraisal or every modality/annotation format.

The first packaged protocol example at
`build/impact-protocol-verified-20261021/`, run `run-plan-a6727da417cf`, completed
nine operations with 30 inventory rows, 26 eligible candidates, three selected
stories and eight loopback requests. It independently reconstructs BH and checks
every candidate's leaf comparison, cross-batch selection, input hashes and
private-prose sentinel exclusion. It pins JAR SHA-256
`ad9be450642f233624d9cc0e4d9eaa6b21e3020f7fdef92409fc1fabb6b56f9c`.
No external request, human attestation or scientific appraisal occurred.

Extending the packaged check to a native manuscript exposed a reporting defect:
disabled optional effect screening was recorded as `method_report.performed:
false` on completed imported-result harmonization. The native narrative reader
incorrectly treated the imported associations as unperformed. Its completion
check now accepts completed harmonization with a primary-results inventory,
while incomplete imports and unperformed association fits remain unavailable.
A native revision regression exercises the real import, sealed source ledger,
writer and verifier. Earlier unsuccessful fixture bundles are retained and do
not count as successful manuscript verification. The corrected focused gate
passed all **36 tests**, including that regression, narrative rules and the
impact assessment suite.

The fresh packaged example at `build/impact-native-complete-20261021/`, run
`run-plan-9b5c3ac9b63e`, completed all nine operations. Its independent verifier
reconstructed full-family BH, all 26 eligible candidates' comparison coverage,
three distinct selected stories, draft main-text word counts and the native
revision's Results/finding/selection links. Both `study verify` and
`study manuscript-verify` passed, with unchanged raw input hashes. The native
revision is the separate sibling bundle
`build/impact-native-complete-20261021-native-manuscript/`; the source is frozen.
The exact JAR SHA-256 is
`e235dfddf9fe072220306d6be868cda262c5f40e0c90ee9f91dbd3b889aa5aba`, and verifier
SHA-256 is `59b34d3f0180dfaf38aa66d67fda93039d741136af12e285b34be8b27afa3f67`.
Eight requests used only the deterministic loopback fixture; zero external
requests or human attestations occurred. `packaged-verification.json` explicitly
records that actual AI ranking, scientific appraisal and full-pipeline conformance
were not tested. The earlier failed bundles remain retained separately.

This imported-result example exposed a metadata contract gap at that checkpoint:
normalization retains feature ID, beta, SE and P, while the supplied `N` column
remains only in the immutable raw input. It is not yet carried into these
normalized rows/dossiers. The inventory therefore records sample size as
unverified rather than inventing it. Carrying N and other applicable diagnostic
fields through supported imports remains required; a source hash alone does
not make those fields available to AI appraisal. The following tranche addresses
this gap for the supported typed field/adapter routes.

After the reporting correction, the combined
`gradlew.bat check executableJar --no-daemon --no-parallel` gate passed in
5 minutes 18 seconds: **1,213 tests, zero failures/errors and five optional
skips** across 259 suites. Website/schema checks passed. The skips remain two
opt-in public genetics tests and three optional CHOLMOD tests. The executable
hash matches the fresh packaged/native-manuscript example above. This is local
source and protocol validation, not a released build, actual scientific impact
benchmark, live-service acceptance matrix or full workflow conformance.

## Aggregate metadata and primary MR testing tranche

`StudyResultMetadataPolicy` and `StudyResultMetadata` now preserve typed aggregate
sample counts, genotype QC, allele/coordinate fields, intervals, degrees of
freedom and heterogeneity alongside signed association results. Conventional
numeric aliases are recognized; ambiguous aliases and labels require explicit
`columns.result_metadata` mappings. Invalid numerical semantics follow the
declared row policy, missing values stay unavailable, and normalization records
source mappings, retained/missing counts, input/output hashes and row exclusion
reasons. Arbitrary text/participant columns are not copied. Numerical eligibility
audits precede effect QC and branch-universe selection; snapshots/derived tables
retain the additional fields. These checks validate supplied field semantics,
not biological correctness or unknown sample counts.

`StudyMetaMetadata` retains unchanged backend estimates as `meta-estimates.tsv`,
preserves per-cohort fields and appends separately named reported-N coverage to
the pooled table. Counts include known/missing contributing cohorts; the sum
is explicitly not a deduplicated or effective N. Source hashes and the
augmentation are recorded. Finding dossiers retain supported metadata and a
mandatory pooled-count caveat; no N is inferred from degrees of freedom or
enrollment.

`StudyMrTestingPolicy` and `StudyMrMultiplicity` separate the primary MR family
from association FDR and sensitivity estimators. The resolved defaults are
fixed IVW, BH and alpha 0.05, with a declared one-instrument Wald fallback;
random IVW, BY, Bonferroni and explicit nominal policies are supported. Exact
duplicate feature/source hypotheses share a test; conflicting primary estimates
or estimability require review. Missing reviewed pairs are pending and
definitively exhausted sources are not estimable. Both retain their place in
the denominator, without an invented observed P value. Pending families have
provisional adjustment bounds and cannot certify primary significance.
Explicit source caps retain out-of-scope eligible-feature dispositions.
Per-feature estimates, adjustment state and mandatory provisional/nominal
caveats reach AI dossiers. This is within-hypothesis correction, and does not
resolve cross-phenotype families, instrument validity, selection bias or every
assay/source adapter.

The accepted contracts are documented in the
[study tutorial](vignettes/study-pipeline-tutorial.md#result-metadata-through-qc-and-pooling)
and [genetic evidence guide](vignettes/bioinformatics-evidence.md#primary-mr-testing-and-complete-feature-dispositions).
Resolver defaults version 7 and execution cache implementation 10 distinguish
the changed behavior from historical sealed outputs. Exact validation and
packaged artifact evidence follow below.

The pre-dispatch full gate passed **1,227 tests, zero failures/errors and five
optional skips** in 5 minutes 39 seconds. A subsequent focused gate covering
the no-source MR dispatch, native HWE-method propagation, pipeline/resume and
impact behavior passed. The final combined
`gradlew.bat check executableJar --no-daemon --no-parallel` gate passed in
5 minutes 21 seconds: **1,228 tests, zero failures/errors and five optional
skips**, across 261 suites. Website/schema checks passed. The skips remain two
opt-in public genetics tests and three optional CHOLMOD tests. The final
executable hash matches every current packaged example below. No source code
changed after this gate; final documentation rendering is checked separately.

The current packaged examples all pin JAR SHA-256
`5ec9c1958a63f5156702ec627d18c5b388623a52780a3d52ee3c5b8edefefa3b`:

- `build/mr-testing-final-complete-20261021/`, run `run-plan-513afd7de8c2`,
  completed four operations. Four eligible features yield three estimable
  primary MR tests and one definitively exhausted instrument family. The
  duplicate pair shares one test. A standard-library verifier reconstructs
  allele-aligned fixed IVW, the one-instrument Wald fallback and full-family BH.
- `build/mr-testing-final-pending-20261021/`, run `run-plan-8bc9b60c53a3`,
  has three complete operations and one correctly waiting MR operation. The
  fourth eligible feature lacks reviewed source data, retains unavailable P/Q
  and its place in the denominator, and makes every primary significance
  decision provisional. Both MR bundles passed `study verify`; their pinned
  verifier SHA-256 is
  `a0c11062fd10bcf667950c456419f192006087819d450822a2d10f0f615e8745`.
- `build/impact-metadata-final-20261021/`, run `run-plan-3cdd942cfb2c`,
  completed nine operations and its separate native-manuscript revision.
  All 30 dossiers preserve supplied N, all 26 eligible candidates enter
  comparisons, three stories are selected, and draft/native word/selection
  checks pass. Its deterministic loopback fixture is not a scientific model;
  verifier SHA-256 is
  `0100c530efc3b8c0bc86768ef61634ddadcd7fba2d127987503676062a66e028`.
- `build/metadata-genotype-20261021/`, run `run-plan-e8fed274623b`, completed
  fourteen operations. Existing numerical-lineage checks confirm 62 selected
  samples per cohort, MAC 19 exclusion/MAC 20 retention, discovery pooling and
  all-cohort pooling. The new standard-library metadata verifier compares 207
  native values, including HWE method, and checks unchanged pooled statistical
  fields. Unknown source N remains unavailable. Its verifier SHA-256 is
  `65ae771a1fb2bf1daacc4f57fa3ee4cbe85663f49aac1c6567818cfedadb5b52`.

All examples retain unchanged raw-input hashes. No external service request or
human attestation occurred. These checks establish software contracts; no
scientific appraisal or full pipeline conformance is claimed. Exact tested
software/verifiers and earlier bundles are retained without overwriting pinned
sources. The first pending-fixture verifier compared a primary-test disposition
with the source-analysis waiting state; its corrected assertion checks the
separate columns. The analysis and its numerical results were unchanged.

## Variant annotation and AlphaGenome integration, 2026-10-22

Native `variant_scoring` now executes reference/identity checks, consequence
imports or installed ANNOVAR/VEP/official Ensembl REST, and score imports or an
official AlphaGenome Python SDK bridge. Canonical variants are deduplicated for
requests while preserving every dataset/feature alias and excluded raw row.
Selection includes nonsignificant primary results and native rare-QC candidates;
it does not infer a functional negative from unavailable annotation. Partial
responses cannot leave usable annotation rows or certify complete scorer
coverage. Tissue, transcript, signed raw/quantile values, source/model versions
and mandatory prediction caveats reach finding dossiers.

The planner adds `variant_annotation` before rare masks when supplied mask
annotation is absent. Verified unambiguous gene/consequence records feed masks
without deleting MAC 1/19 variants by the single-marker cutoff. Ambiguous gene
relationships, missing consequences, unsupported identities/reference agreement,
normalization prerequisites and unused coverage remain explicit. This does not
infer high-confidence loss-of-function or independently certify an assembly.

Local source/reference/index/tool files and databases participate in operation
cache hashes. Imports are indexed once, provider resources hashed once and prior
receipts indexed once per executing operation. Completed batches require both
their own receipt and the enclosing operation seal; changed evidence is rejected.
Computational-budget changes do not change the scientific identity of a complete
batch. Actual Ensembl release metadata survives sealed reuse. Resolver defaults
version 8 and cache implementation 11 distinguish this workflow from earlier
sealed outputs. Configured commands retain their explicit override.

The final `gradlew.bat check executableJar --no-daemon --no-parallel` passed in
**6 minutes 32 seconds: 1,242 tests, zero failures/errors, five optional skips,
262 suites**. All 14 annotation tests passed, covering native dispatch, all-primary
coverage, three import formats, partial-response rollback, policy denial,
reference/indel/build checks, separate Atlas/prediction budgets, false completion,
finding context, sealed/tampered batches, changed-import cache invalidation and
participant-level consequence-to-mask-to-score integration. Website/schema
checks passed; final documentation rendering is checked separately.

The final executable SHA-256 is
`2362bbc3e176265b3300af4a58835c516586cc2d232fed51ac6da89916ae9310`.
Acceptance evidence is retained at:

- `build/variant-followup-verified-20261022/`, run
  `run-plan-4d100274bf4c-attempt-0002`: the initial run completed four operations;
  the second reused all four. Its independent verifier confirms three primary
  variants including nonsignificant results, one retained excluded row, four
  consequence records, three contextual score records, negative signed quantiles,
  explicit missing-score coverage and unchanged inputs. `study verify` passed.
  The exact JAR and verifier source are pinned; verifier SHA-256 is
  `fc6c38ea4eba4d9dfb5771a7bb27ca1e2d7fb3a49c7f65061a93fbbb64c699bc`.
- `build/variant-vep-verified-20261022/`, run
  `run-plan-788081b9287f-attempt-0002`: a real public reference retrieval and native
  Ensembl annotation produced 10 transcript consequences for `GRCh38:MT:1:G:A`.
  The verifier reconstructs every row from raw JSON, verifies reference identity
  and Ensembl release 116, and passes `study verify`. Its source SHA-256 is
  `b66be77041d3bd07ecad8ea6656a9f33ac21b1d752e2a9cd5a785163c838d8a0`.
  Association statistics are synthetic. The initial HTTP 500 waiting attempt is
  retained; a separate earlier bundle records the diagnosed Java HTTP/2 EOF.
  Native Ensembl requests now use verified HTTP/1.1 compatibility with normal
  certificate verification and no redirects.
- `build/variant-vep-live-20261022/sealed-resume-verification.json` records
  cross-build reuse in `run-plan-788081b9287f-attempt-0003`: the real response and
  release 116 were preserved with zero new annotation requests. The earlier
  tested executable and original live verification remain retained.
- `build/alphagenome-bridge-20261022/verification.json` records official SDK
  **0.10.0** compatibility, full raw array/layer retention, signed values,
  transcript/tissue exports, budgets and mismatched-allele rejection. No API call
  occurred. The bridge source matches the packaged resource at SHA-256
  `217308f92092cc1c5a6814f7af0124b89f6a85ce78a267af013a7a0b07c8002e`;
  the verifier source SHA-256 is
  `47c535563abb72fda18adcaca4e69703ef847085f1bb3843686491bfd8689aa9`.

The accepted settings and limitations are documented in the
[native annotation tutorial](vignettes/study-pipeline-tutorial.md#native-gwas-consequences-and-alphagenome-context).
Reproduction scripts live in `examples/study-pipeline/`. Actual AlphaGenome
prediction requires an available credential and separate real-service acceptance;
installed ANNOVAR/VEP were not available for this check. No functional prediction
accuracy, pathogenicity, scientific appraisal or full pipeline conformance follows
from these software/retrieval checks. Other checklist requirements remain open.

## Compatible scalar effects and sampling dependence, 2026-10-22

`StudyPoolingPolicy`, `StudyPoolingAudit`, `StudyMetaOptions` and
`StudyCorrelatedMeta` connect effect/context checks and supplied sampling
covariance to scalar-effect pooling. Common/dataset effect contracts distinguish
estimand, coefficient orientation, effect scale/units and transformations from
assay units. Known conflicting modality/species/assay/tissue/build declarations,
GWAS identities/effect coding and reused cohort/source identities cannot silently
become independent commensurate estimates. Context exceptions require a rationale
and cannot waive conflicting effect definitions. Missing legacy contracts remain
explicitly uncertified; strict completeness is available but is not yet the
default. Incompatible blocks must currently be separated into hypotheses rather
than automatically partitioned.

Native known-sampling-correlation/covariance GLS uses actual feature/source
subsets, supports shared and feature-specific pairs, records normalized weights
and conditional uncertainty, and separates sampling covariance from the optional
independent `tau_squared * I` heterogeneity component. No covariance is inferred
from overlap fractions or effect correlations. Missing, inconsistent, nonfinite
or singular matrices retain feature/source counts and explicit waiting states;
there is no independent-model fallback, jitter or outcome-selected source removal.
Generalized Q is labeled; independent-study I-squared and prediction intervals
remain unavailable. Source counts, distinct declared cohort labels and reported
N sums retain separate meanings and cannot certify independence/effective N.

Primary/discovery pooling and cohort-omission/unfiltered sensitivities share this
model and declared inference/tau/confidence options. Incomplete child fits now
propagate rather than reading absent results or treating them as complete.
Dependent discovery sources can pool before a separate independent validation
source. Reused identities, cross-role dependence or nonzero discovery/validation
sampling covariance block an independent replication claim. Scalar covariance
cannot correct rare-set score pooling; that branch explicitly requests a suitable
model. Matrix contents participate in cache hashes. Resolver defaults version 9
and cache implementation 12 distinguish the new behavior. Finding dossiers carry
the comparability/sampling caveats and source-count/generalized-Q metadata.

The final `gradlew.bat check executableJar --no-daemon --no-parallel` passed in
**5 minutes 53 seconds: 1,255 tests, zero failures/errors, five optional skips,
263 suites**. All 13 new pooling tests passed, including independent analytical
fixed GLS and two-source REML references, covariance and correlation inputs,
source subsets, missing/singular/inconsistent matrices, explicit Student intervals,
incompatible effect/tissue/coding declarations, incomplete contracts, repeated
cohort identities, validation dependence and changed-matrix cache invalidation.
Existing pipeline, rare-set, effect-QC, metadata and impact tests passed. The skips
remain two opt-in public genetics tests and three optional CHOLMOD cases.

The tested executable SHA-256 is
`1ea52668050d602617a6253fddae406a3b295b1aec9f2648937ce8dc27f966d5`.
Fresh packaged evidence is retained at:

- `build/pooling-complete-final-20261022/`, run `run-plan-2b2f3f826f3e`:
  eight operations completed. The independent standard-library verifier
  reconstructs GLS, PauleÃ¢â‚¬â€œMandel heterogeneity, modified-HartungÃ¢â‚¬â€œKnapp conditional
  tests/intervals and frozen discovery/validation correction. It verifies 15
  primary/discovery rows, 22 cohort-omission rows and 44 omission weights. The
  largest absolute numerical difference was `5.906213296213991e-10`.
- `build/pooling-missing-final-20261022/`, same plan/run identity in a separate
  project: four operations complete, four correctly waiting and none failed.
  One feature's required sampling pair is absent. Primary/discovery rows retain
  their contributing sources with unavailable P values; 13 other reference rows
  match, with maximum absolute difference `2.544209287691501e-10`. Independent
  validation was not executed.

Both bundles pass `study verify`, preserve input hashes, and pin the exact tested
JAR and verifier source. The verifier SHA-256 is
`a83ed3dc6195659f8f24259c477ac965c1ac6e377605ca172b17b2810ae92bab`.
The correlations, source effects and participant counts are synthetic; they do
not validate real overlap, covariance derivation or model calibration. The initial
packaged verifiers and bundles are retained with their smaller verified scopes.
See the [pooling contract](vignettes/study-pipeline-tutorial.md#compatible-effects-and-covariance-aware-pooling).
Broader dependence/assay support, automatic partitioning, default strict metadata,
generalized heterogeneity-alert policies and the other full-spec requirements
remain open. Final rendered documentation checks are recorded separately.

## Primary sensitivity baseline identity, 2026-10-22

The filename-only baseline lookup was replaced with completed-producer and
manifest-hash verification. Seven new regression cases cover discovery-only
and supplementary decoys, dependency ordering, incomplete results, changed
hashes, ambiguous primary producers, independently typed validation artifacts
and unlisted outputs. The pooled-discovery effect-QC fixture also now checks
that sensitivity metrics use the 30-feature all-cohort family rather than the
31-feature discovery-only family. A changed primary fails before supplementary
fits start. The initial four-class gate passed 45 tests without failures,
errors or skips. The final sequential `gradlew.bat check executableJar
--no-daemon --no-parallel` passed **1,262 tests, zero failures/errors and five
optional skips**, across 264 suites, in 5m 54s. The skips are two opt-in live
public-source tests and three optional CHOLMOD tests; they are not passed
external/native checks. Website validation passed in that gate.

Fresh packaged evidence uses executable SHA-256
`f0be32eddf076a5370ad5c3a57dab2d307cce1a9387a339fc7e988388c767c10`
and verifier SHA-256
`d914ff47a5edef9d2e05d0001b2594947fcea0d5a7df0f8db23f0eba3630daaa`:

- `build/pooling-baseline-complete-20261022/`: eight completed operations.
  The independent verifier checks the exact primary/validation artifact paths
  and hashes, full all-cohort BH family and unchanged baseline metrics across
  omissions, alongside 15 primary/discovery and 22 omission estimates and 44
  weights. Maximum absolute numerical error is `5.906213296213991e-10`.
- `build/pooling-baseline-missing-pair-20261022/`: four completed, four waiting
  and none failed. Missing sampling covariance leaves no completed primary
  baseline, performs no cohort omissions or independent validation, and retains
  13 other checked estimates with error at most `2.544209287691501e-10`.

Both pass `study verify`, preserve raw-input hashes and pin the exact JAR and
verifier. The separately retained `build/pooling-baseline-missing-20261022/`
was generated without the missing-pair flag; its report explicitly records
`missing_pair: false`. It is a second complete-data run and does not count as
the missing-data acceptance case. Earlier bundles and their pinned verifiers
remain unchanged. These synthetic software checks establish baseline selection
and conditional numerical behavior; they do not establish real-data model
calibration, full downstream primary identity or complete pipeline conformance.

## Lab requirements: staged inputs and discussion depth, 2026-10-22

The specification now explicitly requires users to enter with meta-analysis,
enrichment, networks/modules, MR, colocalization, other typed results or all
analyses already completed. Validated supplied results must satisfy exact
dependency scopes, retain external attribution and avoid duplicate execution;
unavailable upstream raw-data checks remain unavailable. The new contract and
acceptance matrix are documented in
[Enter at any analysis stage](vignettes/analysis-pipeline-specification.md#0-enter-at-any-analysis-stage-and-complete-missing-work).
**Typed later-stage imports now have a source implementation**, documented in
the [supplied-result tutorial](vignettes/study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work).
The old generic later-stage dataset route now requests migration rather than
normalizing enrichment or causal results as another association cohort. The
checklist remains in progress: import checks do not reproduce the source
analysis or establish complete scientific/source/claim validity. The later
acceptance checkpoint records the tested scope.

New default manuscripts use an adjustable 8,000-word target. Over-target drafts
retain full prose and a visible editorial disposition. Explicit strict limits
still block overages; legacy policies that explicitly set `max_main_words`
without a mode remain strict. `word_limit_mode: target|strict` makes the intent
explicit. The resolver defaults version is 10 and execution-cache version 14.
Section/per-finding allowances are planning allocations, not discussion caps.

Native revision `section: supplementary` entries now preserve authored extended
discussion in `artifacts/supplement.html`, with manuscript links, exact source
and scalar-binding checks, citation rendering, attribution and integrity hashes.
They cannot impersonate main-text discussion roles. `word-counts.yaml` separately
records expanded main and supplementary prose; generated evidence previews are
not counted as authored supplementary discussion. Supplement prose has no
default word ceiling. Content is never silently truncated or relocated.

Drafting/editorial instructions require a developed account of each of the
3–5 distinct stories: actual effects/uncertainty and context, relevant primary
studies, stepwise mechanism, demonstrated versus inferred/unresolved links,
null/counterevidence, alternatives, importance and discriminating experiments.
The Wickenhagen OAS1 paper is a discussion-depth benchmark, never evidence that
this pipeline performed its experimental work. The source article and full
Discussion were read; the specimen is cited in the specification. Prompt and
structural checks do not validate real AI scientific appraisal or claim accuracy.

An initial four-class manuscript/impact/integration gate passed 44 tests without
failures, errors or skips. The first broad gate stopped because the new OAS1
registry entry was absent from the central rendered bibliography; the central
HTML/Markdown citation pages were regenerated without rewriting unrelated
vignettes. Final whole-repository evidence follows after completion.

The final sequential `gradlew.bat check executableJar --no-daemon --no-parallel`
passed **1,267 tests, zero failures/errors and five optional skips**, across 264
suites, in 6m 1s. This includes 19 native manuscript-revision tests and seven
word-budget tests. Website validation passed. The two opt-in live genetics
source tests and three optional CHOLMOD tests remain skipped, not passed.

Packaged evidence is retained at `build/manuscript-depth-final-20261022/`:

- `strict-supplement/` retains 156 main-text words and 9,009 separately counted
  supplementary words under an explicit strict 8,000-word main-text limit.
- `target-overage/` retains 8,656 main-text words, the full 9,009-word supplement
  and an explicit over-target editorial disposition.
- The same long main text with a strict limit is rejected before an output
  bundle exists; `strict-overage-error.txt` retains the expected diagnostic.

The independent fixture verifier checks complete prose retention by 4,500
expected repeated pairs, source-bound scalar expansion, main/supplement links,
separate counts, strict rejection and unchanged original input/ledger hashes.
Both native bundles pass `manuscript-verify`, and the source passes `study verify`.
It pins executable SHA-256
`fe2c70708ca537c341170ffea162d8f309be5691effe6c0ddc175f53fedd7368`
and verifier SHA-256
`419801c5fb5103916c4c4c4f587cc55f46ff1d27d8756fe15391f9bd3ee9c506`.
Reproduce with `examples/study-pipeline/manuscript-depth-workflow.py`; use each
historical bundle's pinned verifier/executable. Repeated synthetic prose tests
retention and counts, not scientific discussion quality. No real AI model,
external service or arbitrary later-stage import is exercised by this fixture;
their acceptance requirements remain open.

## Typed supplied-analysis checkpoint, 2026-10-22

`StudySuppliedResultPolicy`, `StudySuppliedResults` and planner/execution changes
now support per-hypothesis `analysis.supplied_results`. Imports are externally
produced analysis artifacts, separate from cohorts. Stable IDs, declared
dependencies, primary/sensitivity roles, methods/scopes, immutable source
snapshots and SHA-256 hashes are retained. An empty dataset list is valid for
all-results projects. Missing files wait at the affected import; independent
imports continue. Legacy later-stage dataset enums request migration instead
of normalizing those tables as cohort beta/SE input.

Source full-family adjusted P values survive normalization, native feature
selection, evidence summaries and finding dossiers. Complete declared families
can receive BH/Bonferroni correction; selected-only source rows need the
original adjusted values and tested denominator. No significant-only subset
defines a new correction family. Imported rare-set tests use the joint
set/mask/test family and preserve their prespecified primary test and unsigned
result type. Graph imports validate endpoints and membership while retaining
signed weights and overlapping modules. Thresholded-region coloc imports carry
their mandatory major limitation into summaries and dependent evidence.

Supplied primary meta-analysis suppresses inferred raw association fits when
they are unnecessary; explicit fit requests and missing registered validation
branches still request upstream work. Native omission/unfiltered comparisons
against an imported primary require matching feature/source/cohort scopes,
complete effect contracts, pooling/inference/confidence settings, exact source
versions and sampling matrix when applicable. An unsupported comparison waits
instead of silently changing the primary or inventing missing cohort estimates.

The focused supplied-result/sensitivity-baseline/impact gate passed **32 tests**
with no failures, errors or skips. Thirteen supplied-result tests include
all-supplied planning/execution, meta-to-native-GO with its full background,
selected-only q/N preservation and tampering rejection, matching sensitivity
scope/settings/versions, unsigned joint rare-test correction, explicit versus
inferred raw fits, independent progress with a missing file, graph validation,
import dependency ordering and primary/sensitivity separation.

Packaged acceptance is retained at `build/supplied-results-final-20261022/`.
Its independent verifier reports:

- `all-provided/`: six supplied analysis stages (meta, GO, network/modules, MR,
  partial-region coloc and unsigned rare sets) complete without duplicate
  scientific analyses. All nine planned operations reuse on the second run.
- `meta-to-go/`: native GO uses all 30 mapped eligible background genes and six
  selected genes. Its target term P value equals `choose(10,6)/choose(30,6)` and
  its BH value equals three times that quantity, independently reconstructed.
- `selected-only/`: source P=1e-6, q=.4 and tested N=400,000 remain unchanged;
  the one-row subset produces no discovery or AI candidate.
- All cases pass `study verify`, retain original input hashes, use explicit
  project-local resources and pin the executable and verifier. No network
  request or real AI appraisal is performed.

Executable SHA-256 is
`59753ceb9fd20581914178127e8fb896c238c79c7283b5a4a908096657e2db15`;
verifier SHA-256 is
`5264b1d8144b9983e0d76ea0ce968ca1e2efee5d16c62762f46ec7e55161d6b6`.
Reproduce with `examples/study-pipeline/supplied-results-workflow.py`; retain each
bundle's pinned software. Resolver defaults are version 11 and execution-cache
implementation version 15. The source/schema copies match.

The final sequential `gradlew.bat check executableJar --no-daemon --no-parallel`
passed in **6m 5s**, covering **1,280 tests across 265 suites**, with zero
failures/errors and five optional skips. Two live genetics-source tests and
three optional CHOLMOD tests were skipped, not passed. XML totals are retained
in `build/supplied-results-final-20261022/full-check-verification.json`.

These checks validate import/orchestration and conditional numerical behavior.
They do not independently reproduce external participant QC, statistical
calibration, instruments/LD, scientific coverage or conclusions. Complete
external formats, source/claim appraisal, all scientific-scope conflict cases,
standalone posterior/network story selection and the full all-results
manuscript acceptance matrix remain open. Module GO was still incomplete at
this checkpoint; the later network checkpoint records current native and
supplied-graph progress. Ranked-GSEA fitting, risk-score and protein goal nodes
still require their supported adapter or configured workflow and otherwise
record a missing capability.

### Local fixture cache incident and repair

An initial new GO unit fixture inherited the machine-wide resource directory
and replaced its GO manifest with a synthetic manifest, also creating a
synthetic `data.gmt`, from **13:11:54 to 13:17:18 EDT**. The original annotation
GAF and ontology OBO were not modified. The original manifest was recovered
byte-for-byte from a retained analysis resource manifest after verifying that
both referenced data hashes matched. Only the fixture's hash-verified GMT was
removed. The repaired manifest hash is
`e5c099dc4e06d68b73b186645cb7bf4a800172a82357e6b5b5656bc280355d9e`.

Evidence, originals, synthetic files, repair timestamps and hashes are retained
in `build/cache-isolation-incident-20261022-131154/repair-report.json` and its
companions. A read-only local CLI health check loaded the restored GAF/OBO,
verified their hashes and produced 887 tested terms under the recorded resource
release; its outputs are retained in that workspace folder. The fixture now
sets an explicit temporary-project resource directory and asserts containment
before creating or writing resource files. Other analyses during the affected
window have not been audited; this repair does not establish that none ran.
After the completed full test gate, `cache-health-check-verification.json`
reconfirmed the exact original manifest/GAF/OBO hashes and absence of the
synthetic GMT. Its verifier only reads the cache and writes its audit in this
workspace.

## Native network and module-GO checkpoint, 2026-10-22

`StudyNetworkPolicy`, `StudyNetworks`, `NetworkModules` and planner/cache/index
changes now connect four separate native study operations:

- `network_analysis`: one supported bulk molecular matrix per dataset, with
  matrix/covariate listwise selection, explicit categorical covariates,
  full-rank OLS residualization and signed Pearson or average-tie Spearman
  residual correlations. Outcome values and association-hit selection are not
  used. Constants/fully explained/numerically unresolved features and local
  sample exclusions are retained. GraphML, threshold-range diagnostics and
  optional independent-row conditional bootstrap frequencies are emitted.
- `pathway_network`: source-family term selection and Jaccard gene overlap from
  completed enrichment or supplied GSEA. Exact GMT/explicit table memberships
  are required; conditional leading-edge overlap is distinct from full sets.
  Correlation and pathway thresholds are separate, defaulting to .7 and .25.
- `network_modules`: connected components and bounded overlapping maximal
  cliques, or direct reuse of supplied memberships. Imported graphs require an
  undirected declaration; the pipeline does not silently project directed
  graphs. Count/state/depth/feature limits retain incomplete dispositions.
- `module_gene_ontology`: uniquely mapped eligible network genes, including
  isolates and genes absent from GO, form the background. All eligible modules
  and terms share one complete module-by-term correction family for that
  graph. Unmapped/too-small modules retain dispositions. Incomplete clique
  enumeration or excess work cannot define an official correction family.

Automatic bulk-matrix inference and manual goal selection are documented in
the [network tutorial](vignettes/study-pipeline-tutorial.md#molecular-networks-pathway-overlap-modules-and-module-go).
Source caveats, species, membership namespace, original adjusted P values,
methods and actual GO resource hashes propagate through the relevant steps.
GO acquisition follows the existing resource policy; offline fixtures supply
only project-local resources. Local sample-audit contents do not enter bounded
network evidence summaries. Module membership and gene-linked GO observations
can enter existing finding dossiers without changing association eligibility.

Ten focused network tests cover all 1,024 five-node simple graphs against an
independent exhaustive clique oracle; signed orthogonal residual correlations;
tied Spearman ranks and listwise samples; conditional bootstrap controls and
reproducibility; resource cache-file changes; missing direction; supplied
memberships; complete-background hypergeometric probabilities; a joint
two-module correction family; contextual evidence without candidate promotion;
bounded incomplete cliques; source-q-preserving pathway overlap; and separate
per-dataset versus pathway-only planning. Empty eligible graphs additionally
complete without inventing missing mappings or performed GO tests. The initial
network/import/impact gate passed before this null-case addition; the final
whole-repository gate covers it.

The final five-case packaged bundle is `build/network-final-20261022-r3/`:

- `native/` reconstructs three signed orthogonal residual edges over four
  retained features and eight samples, with two connected components and one
  maximal triangle. The second run reuses all operations, and GraphML nodes/
  edges match the canonical tables.
- `supplied-modules/` reuses the six provided memberships and runs missing GO
  using all 30 mapped network genes. The independent verifier reconstructs
  `choose(10,6)/choose(30,6)` and its three-term BH value; source caveats remain.
- `supplied-pathway/` imports source GSEA with denominator 100 and original
  adjusted values, excludes q=.4 despite nominal P=1e-5, and reconstructs a
  two-node, one-edge Jaccard graph with similarity .5. Module GO retains its
  four-gene background.
- `clique-limit/` stops after the registered clique-count limit, retains its
  partial memberships and leaves joint GO waiting without an official GO table.
- `empty-pathway/` retains source q=.4 for all three nominally small P values,
  completes its empty graph/modules and records zero GO tests with
  `performed: false`, without asking for missing mappings. The native case also
  confirms that no terms passing the registered size filter means GO was not
  tested, even though a mapped four-gene universe exists.

All five cases pass `study verify`, retain original input hashes, make no
external request and pin executable SHA-256
`210ed623131a776e15dbcb9095784665d51108d62aa8bb6f2a3860859d97687d`
and verifier SHA-256
`70708b8bf70640b99ad265e0ef14b2e64c561e8d1070588dd244efa575a56936`.
Reproduce with `examples/study-pipeline/network-workflow.py`; historical bundles
use their pinned executable/verifier. Defaults are version 12 and execution
cache implementation version 17, invalidating earlier no-test method reports
that incorrectly claimed GO had been performed.

The final sequential `gradlew.bat check executableJar --no-daemon --no-parallel`
passed in **6m 40s**, covering **1,290 tests across 266 suites**, with zero
failures/errors and five optional skips. It includes all ten network tests and
the empty-graph case. The two live genetics-source tests and three optional
CHOLMOD tests were skipped, not passed. The exact XML totals and executable hash
are retained in `build/network-final-20261022-r3/full-check-verification.json`.
`resource-isolation-verification.json` confirms after that gate that the original
global GO manifest, GAF and OBO hashes remain unchanged and the earlier
synthetic GMT is absent; this check only read the global cache and wrote its
audit in the new workspace bundle. It does not audit other analyses during the
earlier incident window.

The preceding four-case `build/network-final-20261022-r2/` passed with pinned
executable `12c737196e7bcbe8a659221ef5209f47d4c1608165815f366dba48a0dced92c6`
and verifier `c975b411869b76444c0fc757805ab52963fa8ea63664b1cb06017ee0609b8f22`.
Its pre-null-fix full gate covered 1,289 tests, zero failures/errors and five
optional skips in 6m 33s. The same executable also passed the earlier staged
result fixture in `build/supplied-results-network-regression-20261022/`,
retaining all six supplied analyses, missing native GO, source q/N and reuse.
Those retained bundles do not establish the later empty-pathway behavior.

The first bundle, `build/network-final-20261022/`, is retained as failed
diagnostic evidence. It exposed a planner bug that also requested a missing
molecular graph for pathway-only work. The extra placeholder/module branch was
removed and a planner regression added. Its executable was not pinned before
the verifier stopped; `failed-acceptance-diagnostic.json` explicitly excludes
that bundle from acceptance evidence.

This establishes conditional local software behavior, not biological validity
or full conformance. At this network checkpoint, cross-cohort preservation,
clustered resampling, integrated community/WGCNA adapters, native GSEA fitting,
broader namespaces and large
sparse matrices/graphs, supplied module-GO formats, standalone network story
selection and real scientific/manuscript appraisal remain unfinished. Row
bootstrap does not account for uncertainty in fitted preprocessing; module GO
does not correct graph estimation or feature-to-gene selection bias. A topology
or enrichment label is not an experimentally demonstrated function. The later
GSEA checkpoint below records subsequent native preranked work.

## Native preranked GSEA checkpoint, 2026-10-22

`ranked_gsea` now dispatches to native `StudyGsea` and the public
`RankedEnrichment` kernel. A complete supplied gene ranking can start a study
with `datasets: []`. Otherwise the planner uses the completed primary
all-feature meta-analysis or separate cohort-scoped association results. It
preserves nonsignificant eligible features, never chooses a best-P probe, and
requires a declared mean/median rule for multiple features per gene. Supplied
meta-analysis is reused without refitting. Selected-only exports, inconsistent
tested counts, ambiguous feature collapse and variant-level GWAS statistics
cannot authorize a complete gene ranking. GWAS requires an appropriate
gene-level, LD-aware upstream analysis.

The weighted running-sum kernel enumerates all same-size memberships when
feasible and otherwise uses seeded Monte Carlo gene-set draws. It retains
signed ES, same-sign normalized ES, leading edges, null/tail counts,
conditional P resolution and Monte Carlo Wilson intervals. BH/BY/NONE applies
to the complete eligible term family; this is distinct from Broad's pooled-NES
empirical GSEA FDR. Undefined hit/null weights or absent same-sign nulls retain
limited results and withhold the official correction family. Valid zero-test
collections complete with `performed: false`; missing/empty resources remain
prerequisites. Requested phenotype nulls wait rather than changing null model.

Rank/source/collection snapshots and receipts are hash-checked and sealed.
Source coverage, model calibration and GMT species/namespace declarations are
not independently certified by counting rows. Built-in human GO/Reactome
packages require matching species/namespace and manifest-verified inputs.
Cache identities include ranking/GMT or effective reference-package hashes.
Effective defaults are version 13 and execution-cache implementation is 20.
Bounded work, ranks, sets and memberships prevent silent truncation or selection
of convenient terms. The budget counts both shared null generation and every
term's signed-tail/normalization scan over those nulls; sharing a null does not
remove term-specific work.

Native artifacts include full result/rank/disposition tables, leading edges,
matched GMT membership and bounded diagnostic numeric/PNG/PDF curves. Actual
BH/BY/nominal values feed pathway selection without replacing BY with BH.
Pathway graphs use full matched memberships or explicitly requested leading
edges; cohort scope and source limitations survive module/GO integration.
Term dossiers retain ES/NES, precision, leading edges and matching pathway-module
memberships. The plot budget does not truncate testing or manuscript candidates.

The eight-case offline acceptance harness is
`examples/study-pipeline/gsea-workflow.py`. The final immutable bundle is
`build/gsea-final-20261022-r3/`, with executable SHA-256
`c434a8acdab1d7db148184542ae7db7fb0d646d6a5eae1ae544fd0fcb83b036f`
and verifier SHA-256
`9a0424fc3136640345eef6e9c74672d5a6662c112680dd75b898be313173cf1c`.
It independently reconstructs full-list ES/NES, exhaustive conditional tails,
leading edges, BH/BY, Jaccard graphs and joint module-GO tests. A finite
Monte Carlo case agrees with its exact reference within the declared six-SE
bound, with actual draw counts and nonzero corrected P values checked. Other
cases verify supplied all-nonsignificant meta-analysis reuse, selected-ranking
and tie prerequisites, requested phenotype nulls, valid zero eligible terms,
immutable inputs, all-operation cache reuse and sealed `study verify` output.
Numeric curves match the reference; PNG dimensions and PDF signatures are
checked. Positive- and negative-ES PNGs were visually inspected for legible labels and
unclipped content. Every resource fixture is project-local; no remote request
or gene-specific disclosure occurs in these tests.

The kernel tests additionally compare every nonempty proper subset of seven
genes at exponents 0, 1 and 2 against a separate full-list walk. Study tests
cover per-cohort planning, BY preservation into term dossiers, pathway-module
context links, work/input limits, resource hash cache invalidation and a
wrong-species built-in package. A work-limit regression verifies that a family
with many same-sized terms cannot evade its budget by sharing one null.

The final sequential `gradlew.bat check executableJar --no-daemon --no-parallel`
passed in **6m 44s**: **1,305 tests across 268 suites**, zero failures/errors and
five optional skips, including all five kernel, ten GSEA study and ten network
tests. The two live genetics-source tests and three optional CHOLMOD tests were
skipped, not passed. `build/gsea-final-20261022-r3/full-check-verification.json`
records the exact totals, skipped test names and executable hash.
`resource-isolation-verification.json` confirms after this final gate that the
original global GO manifest, GAF and OBO hashes remain unchanged and the earlier
synthetic GMT is absent. This verification only read that cache and wrote the
audit in the new workspace bundle; it does not audit other analyses during the
earlier incident window. Source/site schema parity, documentation fragments and
whitespace checks also passed. Local checks do not establish public release
behavior, external-provider acceptance or scientific interpretation.

The preceding `build/gsea-final-20261022-r2/` passed the same eight packaged cases
with executable
`7c3a58780bf8e2600ab39f1a19d489e59b11552ba86609d036e6de6c534bdf53`
and the same verifier. Its full sequential gate passed **1,305 tests across 268
suites**, zero failures/errors and five optional skips in **6m 55s**; exact
totals, skipped test names, executable identity and the read-only global-resource
hash audit are retained in that bundle. It predates the term-tail budget fix.
The earlier
`build/gsea-final-20261022/` bundle remains pinned to executable
`5d66919252b0355bc22089011dc384e58a996ba0e7a4289d403a5caea1635a3d`;
its passing cases predate the final species/context-link changes and are not
rewritten as evidence for later software.

This is conditional local acceptance, not Broad/fgsea/R numerical parity,
biological calibration or real AI appraisal. Preranked inference conditions on
the supplied ranking and gene-position null; it does not preserve inter-gene or
sample dependence. Phenotype/block-aware permutations, LD-aware GWAS enrichment,
broader assay-aware score construction, cross-cohort network preservation and
full scientific/manuscript acceptance remain unfinished. Enrichment does not
establish pathway activity, causality or experimentally demonstrated function.

## Held-out risk-score checkpoint, 2026-10-22

Native `risk_score_evaluation` now connects explicit training/tuning/evaluation
partitions, quantitative molecular score development, complete frozen-model
reuse and external weights/scores to the study workflow. Direct cohort matrices
align participant identities and select a prespecified feature universe without
consuming association-FDR results. Every declared group stays inside one
partition; the resampling unit must contain the declared related groups.
Outcome/group/identity leakage into predictors and the training baseline is
gated. Upstream preprocessing, relatedness and external GWAS overlap remain
source declarations that local layout checks cannot independently certify.

Gaussian development fits a training-only covariate baseline, then penalized
residual-outcome prediction. A lambda search requires a separate tuning set;
the selected recipe can refit on training plus tuning, preserving evaluation
rows for frozen application. Imputation/standardization and baseline estimates
respect those boundaries. The complete portable model retains ordered raw-scale
weights, imputation means, baseline coefficients, categorical levels and hashed
development identities/groups. Known development IDs or groups, changed/missing
sidecars and unseen baseline categories block independent application.
Native logistic/survival development is not silently replaced by Gaussian
training. Diploid external genetic weights preserve explicit allele swaps and
their constant term; LD, build/strand and ancestry suitability remain external
contracts.

Quantitative held-out RMSE, baseline RMSE and incremental predictive R-squared
use frozen predictions/baselines; negative values survive. Raw binary scores
provide AUC, and declared probability predictions enable Brier/log-loss and
descriptive calibration. Conditional percentile intervals resample registered
independent units, retaining all cluster rows, failed resample counts and
insufficient-precision dispositions. Registered categorical subgroup reports
remain descriptive. Independent-row or cluster CR1/t(G-1) related-trait tests
form a separate complete BH/BY/NONE family. Constant/unestimable registered
traits leave limited results and withhold official correction rather than
promoting a convenient subset.

The separate `risk_score_related_phenotypes` operation completes missing work
from frozen scores when evaluation has already been supplied. The supplied
performance table is retained exactly, with no model refit or repeated
performance calculation. Several metrics/subgroups have distinct import
identities without an invented performance-FDR family. Separately supplied
related tests retain a full source family over score/trait/population identities.
Aggregate index/dossier fields include actual metric uncertainty and related
correction, with source limitations. Participant input, partitions, predictions
and development identities remain local private artifacts outside those
allowlisted previews. Methods/manuscript provenance records the executed score
design and separates prediction, association and causal claims.

Default score-dossier subgroup projection omits identifier/resampling dimensions
and known participant/unit labels, unestimable rows and cells with N below ten;
the complete subgroup dispositions remain in local artifacts. The regression
explicitly groups by sample and family identifiers to check those values never
enter dossiers. Aggregate projection is not a deidentification certification.
The frozen numerical model itself retains its missing-value error/imputation
policy; excluded records are left unscored. Oversized model metadata is rejected
before either cache-sidecar parsing or application.

New effective defaults are version 14 and execution-cache implementation is 22.
Participant, model, dosage-allele and external development-ID contents enter
cache identities. Storage limits and estimated complete fitting/bootstrap/
related-family work are checked without favorable participant/test selection.
Automatic applicable molecular-matrix studies expose missing score design and
partition prerequisites; explicit disabling/manual goals remain available.

The final synthetic packaged bundle `build/risk-score-final-20261022-r3/`
pins executable SHA-256
`2d367ac965ee6b3e80f10a6164a8fb433f43c3054a98c7a3fda0c49e5e306aff`
and verifier SHA-256
`6f7d62e70fa17f2016b78346b49f8087ef3898751c7426274a19fa913f9b8bfd`.
All ten cases passed. `examples/study-pipeline/risk-score-workflow.py` uses
NumPy 2.4.4/SciPy 1.17.1 references to independently reconstruct baseline/ridge
fitting, training imputation, tuning selection, frozen predictions, clustered
bootstrap percentile intervals, CR1 t-tests and complete BY adjustment.
It also checks direct/matrix equivalence, complete model reuse, diploid allele
swaps, raw/probability binary metrics, supplied evaluation with only missing
related testing, group-leakage/constant-family/unsupported-model gates,
unchanged input hashes, cache reuse and `study verify`. No external requests
occurred, and every resource directory is explicitly project-local.

The final sequential `gradlew.bat check executableJar --no-daemon --no-parallel`
passed in **7m 2s** with **1,324 tests across 270 suites**, zero failures/errors
and five optional skips, including all six score-kernel and thirteen score-study
tests. The two live genetics-source tests and three CHOLMOD tests were skipped,
not passed. `build/risk-score-final-20261022-r3/full-check-verification.json`
records exact counts, skipped names and the executable identity.
`resource-isolation-verification.json` confirms after the gate that the original
global GO manifest, GAF and OBO hashes remain unchanged and the earlier
synthetic GMT remains absent. This was a read-only cache check, with its audit
written only inside the new workspace bundle; other analyses during the earlier
incident window remain unaudited. Source/site schema parity, changed-page
fragment checks and the citation audit (140 references, 122 documents) passed.
The citation audit refreshed only stale generated blocks and navigation anchors
in the three AI collaboration guides, preserving their prose.

The preceding `build/risk-score-final-20261022/` passed the same ten cases with
executable
`f7f4985fe8838c6c0028429dae0a84183b25fa26c3b4e331c4f266493b1be5d9`
and the same verifier. It predates the complete-work-family preflight check;
the retained bundle is not reassigned to later software. The new regression
also checks that the complete fitting/bootstrap/related workload cannot pass
its budget by counting only its separate parts. Earlier focused failures were
resolved explicitly: constant outcomes required a direct variation gate,
synthetic supplied evaluation required its source scope, and a fixture needed
an explicit generic map declaration. These do not establish calibration or
source validity in real studies.

`build/risk-score-final-20261022-r2/` also passed all ten cases with executable
`34b69baad645f8d7ef1d971f59a9a4acf68d627e259f83fcc8390df6a3e4b912`
and the same verifier. Its full sequential gate passed **1,324 tests across 270
suites**, zero failures/errors and five optional skips in **6m 6s**. Exact totals,
skipped test names, executable identity and a read-only global-GO hash audit
are retained in that bundle. It predates the final frozen-policy, metadata-bound
and subgroup-projection changes and cannot certify them.

Remaining work includes native binary/survival development, nested validation,
genome-wide LD-aware scoring and allele-aware genetic development, broader
calibration/missingness/cluster methods, simultaneous subgroup uncertainty,
cross-hypothesis correction, real ancestry/cohort transportability and actual
scientific/AI/manuscript acceptance. This checkpoint is conditional local
software verification; it does not establish R/other-risk-tool parity,
clinical utility, biological validity or full pipeline conformance.

No requirement is complete solely because it has an operation name. Update this file with exact test/artifact evidence as implementation proceeds. Do not mark the thread goal complete until every applicable requirement above and every explicit specification requirement has been audited against current evidence.

## Protein structure checkpoint, 2026-10-22

The native `protein_structure` branch now binds supplied PDB/mmCIF or versioned
AlphaFold DB models to the declared accession/isoform, taxon and exact FASTA. It
retains source snapshots/hashes, all model dispositions, observed coverage,
explicit residue maps and assay limitations. Experimental B-factors remain
distinct from declared pLDDT. Directional PAE, bounded complete region families,
peptide occurrences and REF-verified residue/transcript context are recorded.
Multi-chain PAE needs an explicit token map; partner sequence checks and a complex
justification do not establish an interface or function. Raw sequences, peptides,
coordinates and PAE arrays remain outside aggregate AI previews and dossiers.
Structure context never supplies association significance or AI eligibility.

Supplied final protein summaries bypass native execution. Supplied raw models
can complete only missing assessment; `protein_structure_assessment` does not
start acquisition or prediction. Explicit manifest-only studies need no raw
cohort data or association/meta refit. Automatic primary-significant scope uses
source-family adjusted P values and exact protein mappings, including a typed
supplied meta-analysis with no constituent cohorts. Target/model/work limits
leave explicit prerequisites rather than selecting favorable subsets.

An explicitly configured predictor receives immutable FASTA/JSON, source/model
versions, seeds and MSA/template declarations. Output paths, sequence identity,
all declared seeds, exit state and acceptance bounds are checked. Valid sealed
batches can be reused from an incomplete prior operation. This is an adapter
contract, not bundled AlphaFold inference or authenticated weights. The program's
network intent and storage limits are recorded; OS isolation/resource quotas are
not enforced by JLinAlg. New AlphaFold inference and broader structure/complex
scientific validation remain unverified.

The [protein tutorial](vignettes/study-pipeline-tutorial.md#exact-protein-sequences-structures-and-confidence)
documents supplied-model, assessment-only, public retrieval and configured
prediction contracts. `examples/study-pipeline/protein-workflow.py` pins the
executable and verifier before running. Its independent PDB-column/PAE-cell
calculations cover confidence, directional region summaries, variants, fragments,
invalid/missing inputs, external summaries and source-family preservation; the
configured predictor is a synthetic process fixture. An optional public TP53
control retrieves the native mmCIF and compares it to a separately downloaded,
version-matched PDB. Numeric plots were visually inspected. The self-contained
HTML trace and controls were generated, but browser interaction was not verified:
the browser runtime failed before launch with the Windows sandbox helper error.

The final packaged acceptance bundle is `build/protein-final-20261022-r4/`:
**12 cases passed**, including the public TP53 control. The pinned executable is
`5a27b896cd3b13cb879c0996561d33356e13a61132c3d77af800c26fc39a8ddd`;
the verifier is
`4a359c015d3faa3a0503225a6042e0be3a9b4298ac92485be24840a8dbc7999e`.
The native public case made four audited requests, selected the exact canonical
AlphaFold DB artifact version 6, and checked 393 residues. Native mmCIF and the
independent PDB reader both gave mean pLDDT 75.0501272264631. This is a model
confidence check, not new inference or a biological finding. Twelve source
studies passed schema validation; source/site schemas match. Generated viewer
JavaScript passed syntax validation, with browser interaction still unverified.
Earlier failed and superseded acceptance bundles are retained. Execution-cache
implementation 23 and defaults version 15 bind this contract.

The final sequential `check executableJar --no-daemon --no-parallel` gate passed
in **6m 56s: 1,346 tests, 272 suites, zero failures/errors, five optional skips**
(three unavailable CHOLMOD-native cases and two opt-in public MR/colocalization
cases). This includes eight protein-kernel and 14 native study tests. The pinned
JAR matched the 12-case packaged verifier after the full gate. Full test XML,
hashes, `full-check-verification.json`, schema and viewer-syntax records are
retained inside the final bundle. The read-only post-gate resource-isolation
check matched the repaired shared GO manifest/GAF/OBO hashes and confirmed the
synthetic GMT remains absent; no shared resource was written by that check.
Broader pipeline gaps remain in the current checklist; this is an uncommitted,
unreleased source-build checkpoint.


## Cohort diagnostic checkpoint, 2026-10-23

Native `cohort_overlap` now records complete long membership, exact exclusive
patterns, exact/at-least degree counts, tested coverage and direction context.
Two/three sets receive Venn PNG/PDF; larger combinations use UpSet. A display
cap never changes the retained pattern table or complete set-size bars. Registered
common testing families precede cohort FDR; source-provided full-family adjusted
P values survive reuse. Discovery-subset validation freezes its family from the
completed discovery-only source. Known incompatible feature namespaces, species
or builds block inference; unknown declarations do not establish comparability.
The namespace guard also reaches pooling and validation. Automatic overlap needs
multiple signed-result sources, so a rare-set-only workflow does not acquire an
unfulfillable single-marker prerequisite.

`heterogeneity_review` applies registered I-squared or complete-family BH/BY/NONE
Q rules and explicit widespread-count/fraction boundaries. Missing source coverage
or statistics withhold a widespread conclusion. Conditional omission needs completed
diagnostics; an unavailable supplied alert rule cannot become a negative alert.
Supplied overlap and heterogeneity stages retain their selection/cutoff rules and
can complete missing views without refitting the source analysis.

Paired cohort influence retains every attempted fit and uses a registered global
common feature universe, or explicit pairwise denominators. The primary-significant
scope stays frozen; each fit keeps its original complete correction family. Effects,
SEs, intervals, directions, significance, Q/df, I-squared, tau-squared, counts and
source hashes enter per-feature tables and finding dossiers. Full-family changes
are distinguished from common-feature changes. Neither a larger Q P value nor a
reselected validation fraction chooses the primary. The complete registered primary
artifact stays unchanged. Numeric cutoffs are configurable diagnostics, not universal
criteria for declaring a cohort invalid.

The [cohort tutorial](vignettes/study-pipeline-tutorial.md#multiway-cohort-overlap-and-registered-heterogeneity)
and `examples/study-pipeline/cohort-diagnostics-workflow.py` document the contracts.
The final acceptance bundle is `build/cohort-diagnostics-final-20261023-r6/`:
**13 cases passed**, including 2/3/4-way patterns, frozen validation, known-covariance
GLS, conditional omissions, missing coverage, source/primary preservation, supplied
stages, cache replay and `study verify`. Independent NumPy/SciPy calculations start
from the synthetic raw source tables. All 13 studies passed schema validation;
source/site schemas match. Twenty-two PNG/PDF pairs passed format checks, and three
representative Venn/UpSet PNGs were visually inspected. The pinned executable is
`d9c7113fe265431ea1c1a87278747eeeeba36713e14a4c3a39be8f3a77da7752`; the executing verifier is
`a8702c58458947a92c1d8d1e47610030a3389182f4150b14b44aaa6289887ff3`.

The full sequential `check executableJar --no-daemon --no-parallel` passed in
**8m 36s: 1,381 tests across 278 suites, zero failures/errors, eight optional skips**,
including 20 cohort-diagnostic and 14 pooling tests. Skips are three unavailable
CHOLMOD-native cases, two opt-in public MR/colocalization cases and three single-cell
Python checks whose explicit `JLINALG_CELL_PYTHON` environment was unset. Those
external checks were not passed by this run. Complete test XML/hashes, the frozen
source receipt, executable/verifier hashes and schema/export/visual records are
retained in the bundle. A read-only post-gate check matched the repaired shared
GO manifest/GAF/OBO hashes and confirmed that the synthetic global GMT remains absent.

Concurrent work in this checkout changed an earlier executable and interfered with
its shared Gradle test-report output. That failed run and its three reported assertion
failures are retained in the r5 bundle. The planner regression and dependent-operation
resume counts were corrected. Final verification therefore used the frozen copy
`build/cohort-source-20261023-r6/`, with 1,912 source files hashed before execution
and its own build output. Its executable matches the final packaged acceptance;
current implementation inputs were checked against that snapshot after the gate.
Concurrent documentation changes were preserved. Earlier failed/superseded bundles
are retained rather than relabelled. Execution-cache implementation 24 and defaults
version 16 bind the new diagnostic contract.

This is an uncommitted, unreleased source-build checkpoint. It does not establish
biological cohort exclusion, real-model AI appraisal, complete modality/contrast
coverage, new AlphaFold inference or full pipeline conformance. Unsigned/rare-set
and multicontrast overlap, uniform primary selection across remaining downstream
consumers and the broader requirements in the current checklist remain open.


## Colocalization checkpoint, 2026-10-23

The native branch now streams source inventories before restricting outcome
variants to exposure candidates. It retains source hashes, distinct observed IDs,
candidate/aligned counts, true observed matched fractions, source selection/export
cutoffs, declared counts/completeness, strongest reported-P matching, positional
gaps and binned distinct-position coverage. A 3/3 candidate match from 5,004
observed outcome IDs is reported separately from the 3/5004 observed-source match.
An export cutoff contradicting `complete_region:true` blocks inference; work bounds
cannot silently truncate a supposedly complete region. Source-ID aliases and
positional gaps do not certify independent variants or callable-base coverage.

Registered `analysis.colocalization` controls capture SuSiE fitting and the full
shared-prior grid. The approved primary prior is unchanged. Every computed signal
pair is retained in per-pair and operation-level `colocalization-results.tsv`,
with H0Ã¢â‚¬â€œH4, conditional H4, component IDs/leads, full sensitivity ranges and an
optional explicitly registered posterior threshold. No decision threshold is
imposed by default, and posterior rules do not supply association FDR or causal
direction. Coverage, source/trait/assay identifiers, export/overlap context,
convergence/fallback, evidence grades and mandatory caveats flow to canonical
rows, source-linked finding dossiers, Methods records and discussion instructions.
Twenty preview rows do not truncate the actual result or dossier inventory.

An explicit partial request always retains
`exploratory_partial_region_major_limitation`, including when source complete
flags are present. Every partial row states that its posteriors condition on
observed selected variants and that a null result is not evidence against a
shared causal variant. A declared-complete supported result is labelled
`regional_analysis_with_declared_complete_sources`; it does not certify coverage
or LD/model calibration. Nonconvergence retains its attempts and produces no
posterior claim. Exhausted partial input with too few shared variants or no
credible signal is not estimable, distinct from missing prerequisites.

Colocalization now has a separate optional feature cap; the contextual top-ten
limit cannot silently reject eligible reviewed pairs. Unrequested eligible mapped
features receive explicit dispositions. Native follow-up and manuscript candidate
selection now use the declared BH/BY/NONE correction instead of unconditionally
recomputing BH; supplied complete BY families can also be adjusted with their
full declared denominator. Source-provided adjusted P values remain unchanged.
Uniform primary-artifact selection and broader contrast/unmapped-source coverage
remain open requirements.

Supplied colocalization stages bypass fitting and prior-grid execution. Multiple
signal pairs in one region use explicit component/pair identifiers; optional
canonical source/coverage/convergence fields survive typed normalization.
Supplied probabilities, counts and booleans receive format checks. Complete
H0Ã¢â‚¬â€œH4 vectors must sum to one within the declared source precision (default
`posterior_sum_tolerance: 0.000001`); they are never renormalized. Source coverage
without a declared grade remains unverified. Import checks do not reproduce the
external source analysis or reconstruct missing priors/LD.

The [colocalization tutorial](vignettes/study-pipeline-tutorial.md#complete-colocalization-records-and-regional-coverage)
and `examples/study-pipeline/colocalization-workflow.py` document the contracts.
The final bundle `build/colocalization-final-20261023-r2/` passed **15 offline
packaged cases**, including 25 native signal pairs, partial/unknown-overlap cases,
nonconvergence, budgets/conflicting declarations, unavailable estimates, supplied
results, explicit caps and a BY selection family. Independent H0Ã¢â‚¬â€œH4 enumeration
conditions on the native SuSiE effect log Bayes factors; it does not independently
reproduce SuSiE fitting. The verifier checks raw-source counts, allele-adjusted
Z scores, primary priors, full signal/prior rows, input/source hashes, native
cache replay and `study verify`. Its pre-execution pinned executable is
`5aceaa72b8705f6e19705dbf0f97184ed74438138b90886afdc418154dcaad2b`; the executing verifier is
`15df5a457ad279d34e1ce480b3d60d4bda5bffdb5ee1be4fd6eb8c33ce239e30`.

All 15 JSON-formatted study inputs passed schema validation, and source/site
schemas match. A separate pinned audit independently checked **1,120 per-variant
H4-conditional rows across 48 tables**, including cache replays. This is conditional
arithmetic evidence, not an unconditional causal probability or biological result.
The first schema-audit attempt used a YAML reader that interpreted a JSON scientific
literal as text; its source/failure record is retained, and the corrected audit
parses the known JSON format. Earlier failed development and packaged bundles
are retained without relabelling them as accepted.

The sequential frozen-source `check executableJar --no-daemon --no-parallel`
passed in **8m 35s: 1,401 tests across 280 suites, zero failures/errors, eight
optional skips**, including six colocalization-review, 25 genetic-evidence and
16 supplied-result tests. Skips are three CHOLMOD-native cases, two opt-in public
MR/colocalization cases and three single-cell Python cases with their explicit
environment unset. Those external checks were not passed. Full XML/hashes,
source/executable/verifier receipts, schema/conditional arithmetic and read-only
shared-GO resource checks are retained in the final bundle. The repaired original
GO manifest/GAF/OBO hashes matched and the synthetic shared GMT remained absent.

Final verification used `build/colocalization-source-20261023-r2/`, a frozen copy
of **1,935 source files** with independent build output. Later concurrent embedding
changes in this checkout are preserved and excluded from this gate's claim;
colocalization and shared pipeline inputs were checked against the snapshot.
Execution-cache implementation 25 and defaults version 17 bind the new contract.
This remains an uncommitted, unreleased source checkpoint. Real public acquisition,
source-specific LD sensitivity, independent calibration, broader assays/models,
actual-model scientific appraisal and complete manuscript claim validation remain
separate requirements; full pipeline conformance is not established.

## Registered primary identity checkpoint, 2026-10-23

Verification status: eight packaged cases passed against the exact executable
from a frozen full gate: **1,431 tests across 286 suites, zero failures/errors,
15 optional skips**, completed in 8m 3s. The twelve primary-contract tests passed.
The full-gate source copy contains 1,966 hashed files. The skips cover optional
CHOLMOD, public MR/colocalization, single-cell Python, sequence-tool and Harmony
R environments; they do not establish those integrations on this machine.

The native statistical and editorial consumers now share `StudyPrimaryResults`
and the typed `analysis.primary_result` policy. Default selection requires the
registered meta-analysis when planned; incomplete work, retained partial imports,
ambiguous producers or multiple cohort sources cannot be replaced by a favorable
cohort. A single cohort can be selected automatically when no meta-analysis is
registered. Explicit producer/dataset/contrast overrides require a rationale.
A planned association fit cannot be replaced by its harmonization copy.

GO/pathway selection, mapped genetic/literature feature selection, automatic
protein targets, candidate ranking and the per-hypothesis manuscript evidence
inventory use the same scoped canonical source and correction family. A completed
association producer supersedes its validation copy; per-dataset post-effect-QC
rows supersede that dataset's standard table. Producer type, hypothesis,
completion and sealed hashes are checked, including path containment. Discovery,
sensitivity and nested lookalike files cannot become primary by filename or
iteration order. No minimum-P merge chooses a cohort or coefficient.

Every executing consumer records `primary-result-selection.yaml`. Its stable
identity includes the hypothesis, producer, source/metadata hashes, contrast,
selection policy, adjustment, tested denominator and primary alpha. Hash-named
copies preserve the producer manifest and supplied-family metadata across cache
replay. Source tables remain unchanged; an identity/correction change within a
frozen operation requires a new execution attempt. Native BH/BY/NONE correction
uses eligible observed rows in that contrast, with an explicit original-export
completeness caveat. Typed supplied adjusted P values and original full-family
counts/coverage survive downstream selection. Selected-only sources cannot
establish complete ORA backgrounds or GSEA rankings.

Independent scopes remain explicit: per-cohort GSEA has a dataset-ranking receipt;
a supplied complete ranking can execute despite an unavailable primary fit;
cohort heterogeneity/influence uses its separately recorded all-cohort meta
baseline. Predictor/split-based risk-score development, raw-matrix correlation
networks and all-QC-eligible variant annotation retain their own inputs. These
independent sources do not silently become primary feature association findings.
Consumer-specific follow-up thresholds retain their declared settings; sharing
an input identity does not make separate testing families or selection thresholds
scientifically interchangeable.

The new executable acceptance script is
`examples/study-pipeline/primary-results-workflow.py`. It generates eight offline
synthetic cases, pins its executable and executing verifier before running, and
checks single-cohort GO, BH/BY, supplied full/selected meta families, explicit
cohort choice, ambiguous sources, incomplete imports, independent ranking and
hypothesis separation. It compares source identities and correction values across
GO, GSEA, ranking and manuscript dossiers, checks reusable completed branches and
runs `study verify`. The additional independent audit passed 39 selected/unavailable receipts,
24 official-source identity comparisons, 400 adjusted-value checks and 675 sealed
file hashes, including all eight study schemas. Study cache implementation is 27;
resolver defaults are 18.

Evidence is retained under `build/primary-final-20261023-r6/`:
`verification.json`, `additional-primary-audit.json`, `full-gate.json`,
`full-test-xml/`, `tested-source-snapshot.json` and
`shared-go-resource-audit.json`. The read-only shared GO reference audit passed;
the three known resource hashes match and no `data.gmt` was introduced. The
executable SHA-256 is
`26357a39b3d1c69cb5cdb7e92bacdb15890f6fb23d062b4f81aeda08b8bb7569`;
the executing packaged verifier SHA-256 is
`fed134930e316c12ef6c4f0b657552a7df57f083e3d93beb5dd4218ea4f9869e`.
The tested source is `build/primary-source-20261023-r3/`. Subsequent shared-checkout
literature files and `HarmonyRCli.java` / `HarmonyRCliTest.java` edits are listed
in `post-gate-source-drift.json` and excluded from this gate's claim. Primary
selection code and its consumers match the tested snapshot. This checkpoint does not complete the full pipeline objective or establish
scientific calibration, biological validity, real AI appraisal or released behavior.

## Source-aware genotype QC checkpoint, 2026-10-23

The native association adapter now retains VCF/BGEN source ploidy and genotype
representation after sample alignment. Called allele number uses actual copies
where observed, with an explicit diploid assumption for unknown sources. MAC
is calculated directly as `min(AC, AN - AC)` so exact integer boundaries do not
change through an allele-frequency rounding step. Group MACs use the same
cohort-minor allele in cases and controls. Counts and missingness precede marker
mean imputation. These values do not establish carrier counts or model-specific
effective sample sizes.

Generic association and rare-score filtering retain explicit exclusions for
observed non-diploid and split-multiallelic sources. This does not implement a
sex-chromosome, PAR or multiallelic association model. VCF per-ALT quality and
BGEN reordered ploidy are preserved. An absent requested quality metric is
distinct from a low one; declared hard calls reject fractional values. Source
dosages, including integer values, do not receive fabricated hard-call HWE.
Caller-specific FILTER/GQ/depth/allele balance and biological sex inference
remain outside this checkpoint.

HWE and differential missingness default to report-only. Positive registered
thresholds activate filters and preserve explicit unavailable-statistic
exclusions. HWE uses the conditional exact diploid human-autosome test on the
declared all/case/control scope and optional reference-ID intersection. Panel
identity, source/analyzed counts and pre/post execution hashes are recorded;
IDs outside the source fail. The panel does not certify ancestry, founders or
unrelatedness. No sample-size-scaled, mid-P or specialized sex-chromosome HWE
implementation is claimed. Missingness uses two-sided probability-ordering
Fisher exact inference with a conditional-state budget. Exhaustion stays
unavailable rather than substituting an approximate or passing P value.

The full raw association table retains all dispositions. Eligible canonical
results propagate typed counts, source labels and QC methods. Method reports
and a mandatory manuscript caveat retain calibration and scope limitations.
The native GLM genotype efficient-score route is unchanged: QC adds no Firth,
SPA or specialized binary mixed-model correction. Appropriate sparse/imbalanced
inference and its independent calibration remain required work.

Conditional GWAS score exports share the base case/control QC columns, which
remain visible on filtered rows while score fields stay empty. Unsupported
ploidy/multiallelic conditioning variants fail explicitly. Manual association
studies no longer schedule unrequested rare-set work; automatic studies and
explicit manual goals/configuration retain eligible rare-set branches. Their
MAC 0 eligibility stays independent of the single-marker MAC 20 threshold.

The [QC tutorial](vignettes/study-pipeline-tutorial.md#genotype-qc-scopes-and-unavailable-statistics)
and `examples/study-pipeline/genotype-qc-workflow.py` describe the configuration
and independent packaged acceptance contract. Study execution-cache
implementation is 28 and resolver defaults are 19. This remains partial pipeline
conformance in uncommitted source, with external and scientific validation
reported separately.

Final local verification: **15 offline packaged cases passed**, including
**1,019 independent numerical checks**, reusable completed-operation replay
and `study verify`. The independent QC propagation audit passed **3,300 exact
native/canonical field comparisons**, **30 association manifests** and
**1,410 sealed file hashes**. All 15 study inputs passed the schema, and the
source/site schema hashes match. The packaged fixture uses its own offline
resource directory.

The sequential frozen-source `check executableJar --no-daemon --no-parallel`
passed in **7m 36s: 1,451 tests across 288 suites, zero failures/errors, 15
optional skips**. The skips are three CHOLMOD-native cases, two opt-in public
MR/colocalization cases, three single-cell Python cases, five sequence-tool
cases and two Harmony R cases. Those integrations were not established by the
local gate. The 42 focused tests also passed, including conditional score
comparisons to independent R reference calculations and the new integer-MAC
boundary and unsupported-conditioning cases.

Evidence is retained under `build/genotype-qc-final-20261023-r1/`, including
`verification.json`, `additional-qc-audit.json`, `schema-validation.json`,
`full-gate.json`, `full-test-xml/`, `tested-source-snapshot.json` and
`shared-go-resource-audit.json`. The tested source copy is
`build/genotype-qc-source-20261023-r2/`, containing **1,972 hashed files**.
At the post-gate check, every source file still matched that copy; final
documentation receipts are recorded separately. The executable SHA-256 is
`7f954793577d34f4f9d140712f0cef921b93928e1fb327ab67c216acfee25570`;
the executing packaged verifier SHA-256 is
`5a738dd7e4c7ef5339fa4337366ae74f686813f627e5d3728dbc1805fc247b9f`.
The read-only shared GO reference audit passed: all three known resource
hashes matched and no synthetic `data.gmt` was introduced.

The first full gate exposed seven conditional-export failures caused by
duplicated case/control columns; its source, log and failure summary remain
in `build/genotype-qc-source-20261023-r1/`. Corrected focused and full gates
passed without weakening the independent score/covariance checks. Earlier
packaged development failures are also retained without relabelling their
results. This checkpoint is uncommitted and unreleased, and does not complete
the pipeline objective or establish scientific/model calibration.

## Explicit Firth GWAS inference checkpoint, 2026-10-23

The native genotype GLM now accepts a registered `binary_test: firth-lrt` for
independent individual Bernoulli-logit observations. The default efficient-score
route is preserved. Each QC-eligible variant receives a full Jeffreys-penalized
fit and a coefficient-zero constrained fit with the same full design in the
penalty. No ML null must converge first. The LRT has an asymptotic chi-square
reference on one degree of freedom. It is separate from a Wald test.

The standard error uses inverse ordinary Fisher information at the penalized
estimate. Subsequent beta/SE meta-analysis has its own Wald approximation; the
source LRT P value is retained. Full/constrained objectives, convergence,
iterations, method/source labels and underflow-safe natural log P propagate to
canonical study results. Singular or nonconverged rows retain source/QC counts,
with no association P/FDR. Blocks retain input order and bounded memory with
portable CPU parallel fits. No ridge repair or score fallback silently changes
the declared test. Max-absolute column scaling and scaled-coordinate numerical
controls are explicitly recorded.

A registered `offset_column` is added to participant complete-case QC, mapped
through dataset covariate columns and fitted as a fixed offset. It cannot also
be an estimated covariate. Missing genotypes follow the declared mean-imputation
policy after QC; observed called/missing counts remain unchanged. This route
rejects related/clustered declarations, non-Bernoulli families, ordinary-null
conditional score export and strict accelerator backends. A supplied offset
does not establish a valid LOCO or kinship adjustment. The missing-model review
gate remains in force.

Independent reference fixtures use the CRAN `logistf` 1.26.1 numerical fitter,
with an isolated official Windows DLL and matching source wrappers. No global
R package was installed. Official ZIP/source/DLL and used wrapper hashes,
generator and fixture hashes are recorded. Full fits use upstream IRLS and
constrained fits upstream NR with default `maxhs=0`; explicit tighter controls
are retained. Ordinary Fisher SE is independently inverted in R, since upstream
IRLS reports a different augmented covariance. The scaled fixture is an exact
diagonal reparameterization. Nine reference cases and an analytic half-count
case test coefficients, both penalized objectives, LRTs and tails. Independent
SciPy objective optimization provides a second packaged point check.

Firth estimation does not imply universal sparse-tail calibration. A separate
exact fixed-margin audit enumerates hypergeometric carrier tables with analytic
full/constrained Firth objectives. At N=1,000, five cases and 20 heterozygous
carriers, nominal 5% inference rejects approximately 9.6264% of null datasets.
The audit is retained as calibration evidence, not silently marked as a passing
calibration guarantee. It does not cover covariate-adjusted or related designs.
The method report and mandatory manuscript caveat retain these limits.

The [Firth tutorial](vignettes/study-pipeline-tutorial.md#firth-association-for-separated-binary-gwas-designs)
and `examples/study-pipeline/firth-gwas-workflow.py` document configuration and
acceptance. Cache implementation is 29; resolver defaults are 20. Broad exact/
permutation/SPA calibration, binary mixed models, profile confidence intervals,
rare-set binary/survival routing and genome-scale performance remain separate
requirements. This is an uncommitted, unreleased source checkpoint and partial
pipeline conformance.

Final verification passed **12 offline packaged studies and 270 numerical/
workflow checks** against the exact frozen-gate executable. Cases cover seven
upstream-reference designs, explicit fitting failures, missing dosage, fixed-
offset complete cases and underflowing P values with finite log tails. Native
point values are independently compared to CRAN reference outputs and SciPy
full/constrained objective optimization. Cache replay and `study verify` passed.
All twelve study inputs passed schema validation; source/site schemas match.

The additional audit passed **380 exact typed-field comparisons**, **24 native
association manifests**, **1,160 sealed output hashes** and **24 copied primary
method manifests**. A separately pinned harness executed the packaged native
finding inventory on all twelve sealed projects. It retained **20 association
observations** and calibration caveats in **ten findings**, including method
labels and log P values. This is deterministic dossier propagation, not actual
AI scientific appraisal. The harness source/class and executable were pinned
before execution, with separate output receipts.

The corrected sequential frozen-source `check executableJar --no-daemon
--no-parallel` passed in **8m 2s: 1,462 tests across 290 suites, zero failures/
errors and 15 optional skips**, including five Firth-core and six Firth-CLI
tests. The expanded focused set also passed 51 tests. Optional skips cover
three CHOLMOD-native, two public MR/colocalization, three single-cell Python,
five sequence-tool and two Harmony R cases; those integrations were not
established by this gate. The tested copy contains **1,989 hashed source
files** under `build/firth-source-20261023-r2/`. At the post-gate check, all
source files still matched it. The shared GO reference integrity audit passed
read-only: all three known hashes matched and no synthetic `data.gmt` existed.

Evidence is retained in `build/firth-final-20261023-r2/`: `verification.json`,
`calibration-enumeration.json`, `additional-firth-audit.json`,
`finding-inventory-audit-r1.yaml`, `inventory-audit-software.json`,
`schema-validation.json`, `full-gate.json`, `full-test-xml/`,
`tested-source-snapshot.json` and `shared-go-resource-audit.json`. The executable
SHA-256 is
`6bd03989d980cbd66b0b2f8e986899e0f0e861a07b93226ca72ae8579fabcb0d`;
the executing packaged verifier SHA-256 is
`0c5304b4c3bdd5b449fa07c74a585ba345df809d56852154d28bac8e41db7a38`.
Final documentation/source correspondence is recorded separately.

The first full gate, `build/firth-source-20261023-r1/`, retained one failed
legacy assertion that GWAS output omitted `statistic_type`. The tiny legacy
fixture also had only MAC-filtered variants. It now explicitly declares MAC 0
for that fixture and asserts an estimated ordinary `z` result. The inference
field now records the existing ordinary statistic type as well as Firth
chi-square inference. Positive natural log P metadata is rejected, while finite
negative log tails remain available when ordinary P underflows. Earlier
development/packaged failures, including missing-dosage integration and a
misnamed audit-plan lookup, remain retained without relabelling their results.
This checkpoint does not establish universal sparse-tail calibration or
complete the full pipeline objective.

## Exact conditional binary GWAS checkpoint (2026-10-24)

**In progress, locally verified source build.** Explicit
`binary_test: exact-conditional` now tests the finite conditional ALT case-allele
score for independent Bernoulli observations, conditioned on declared stratum
case counts with optional fixed known offsets. Only observed integer 0/1/2
hard calls are accepted. Missing genotypes are omitted before variant-specific
margins. Continuous nuisance covariates, dosages, kinship and repeated
individuals require other scientifically suitable estimators. The
[tutorial](vignettes/study-pipeline-tutorial.md#exact-conditional-tests-for-sparse-binary-gwas)
details the model, two-sided probability-ordering tail, resource bounds and
sample-count scopes.

Optional separate Firth effects and ordinary-Fisher SE remain approximate.
Failed or skipped effect estimation retains a valid exact P as an explicit
P-only row. Such tests survive normalization, effect-QC retention, primary
selection and finding inventories with unavailable beta/SE and mandatory
interpretation caveats. Supplied association tables may declare P-only mode,
or map a result-type column in mixed signed/test-only tables. Contradictory
effects are rejected. Inverse-variance meta-analysis and default beta/SE GSEA
wait for suitable complete signed inputs; effect comparisons record their
finite signed-pair scope. A required replication sign rule remains unavailable
for a P-only pair.

Exact resource/numerical failures retain partial outputs but withhold all
native BH values and mark the study family ineligible for official selection.
The packaged audit exposed a completed-empty prioritization state after an
incomplete association. Scoped incomplete/ineligible native producers now
make primary selection wait, including when a completed harmonized table is
also present. A dedicated regression test protects that case. Cache identity
is 30 and resolved default-policy version is 21.

Seven independent reference fixtures enumerate individual outcome assignments
with rational offset weights and stratum convolution. They contain 37 support
masses and do not use JLinAlg or asymptotic tests as truth. The core also checks
a binary Fisher reference, allele/outcome recoding, underflow-safe natural log
P, numerical normalization failure and resource limits. The focused suite
passed **40 tests**. The frozen full `check executableJar --no-daemon
--no-parallel` passed **1,484 tests across 294 suites**, with **zero failures/
errors and 19 optional skips**, in **8m 44s**. Skips comprise three CHOLMOD,
two public MR/colocalization, five single-cell Python, five sequence-tool,
two Harmony R and two Picard alignment cases; those integrations remain
unverified by this gate.

The final executable passed **14 packaged scenarios and 537 checks**, with
cache replay and `study verify`. Separate audits checked **676 sealed operation
outputs, 52 canonical test/QC count rows and 28 original/resolved schema
inputs**. A separately pinned packaged inventory harness retained **102 native
association observations, including 88 P-only observations**. Both incomplete
projects retained unresolved primary hypotheses with zero selected candidates.
This verifies deterministic evidence propagation; AI scientific appraisal and
biological findings were not exercised.

Three complete fixed-margin null supports were enumerated through the
packaged route. At nominal 0.05 their rejection probabilities were
0.00366834 (N=1000, 5 cases, 20 carriers), 0.01554241 (N=1000, 10 cases,
20 carriers), and 0.04249710 (N=120, 24 cases, 24 carriers). These illustrate
conservative discreteness under the stated intercept-only binary-genotype
null. They do not certify arbitrary covariates, estimated offsets, relatedness,
population transfer or informative genotype missingness.

Evidence is retained in `build/exact-logit-final-20261024-r1/`:
`verification.json`, `calibration-enumeration.json`, `additional-exact-audit.json`,
`schema-validation.json`, `finding-inventory-audit.yaml`,
`inventory-audit-software.json`, `inventory-audit-output-sha256.json`,
`full-gate.json`, `full-test-xml/`, `tested-source-snapshot.json`,
`protected-source-guard.json` and `shared-go-resource-audit.json`.
The executable SHA-256 is
`c2ee3a9f3b366ad99a2704f311ca0afaaccf782698d08cd42d17e505afc16799`;
the packaged verifier SHA-256 is
`e4a887cb724b7dda476f8b0107a998d09a930d92f381e505b9293c669f4a86bf`.

The full gate applies to **2,011 hashed files** in
`build/exact-logit-source-20261024-r2/`. A guard verified **91 relevant
implementation/schema/fixture files** still matched that snapshot. Concurrent
literature and alignment changes in the shared checkout are recorded separately
in `post-gate-source-drift.json`; the full-test claim is scoped to the frozen
copy. The shared GO reference integrity audit passed read-only, including the
absence of a synthetic `data.gmt`. This checkpoint is an unreleased source
build; the broader pipeline objective remains in progress.

Earlier failed development fixtures and packaged attempts remain retained.
The first full-gate preparation copy (`exact-logit-source-20261024-r1`) was
interrupted after validation was started before its freeze command completed;
its incomplete receipt is recorded without a successful gate claim. The second
copy completed freezing before validation. The strict compiler also exposed a
missing exception serialization ID in concurrent alignment code; the minimal
serialization declaration was supplied without changing alignment behavior.

## Native binary risk-score checkpoint (2026-10-24)

**In progress, locally verified source build.** Registered binary risk-score
training now uses a joint binomial ridge/lasso/elastic-net model, explicit
positive ridge regularization of non-intercept covariates, a separately fitted
covariate-only comparator, and independent tuning log loss. Both models and all
imputation/scaling are fitted from training records, optionally refitted on
training plus tuning after penalty selection, then frozen for evaluation.
The [binary-score guide](vignettes/study-pipeline-tutorial.md#native-binary-risk-score-development-and-frozen-probabilities)
defines the normalized objective, positive penalties, KKT tolerance, counted
work/iteration bounds and interpretation limits. Scaled summation/hypot avoid
variance underflow/overflow; fitted-versus-exported linear predictors are
checked for numerical consistency. Independent references and unit changes of
1e-200/1e200 verify the supported preprocessing/representation.

Complete binary model export/reuse now retains centered molecular weights,
full prediction covariates, comparator, logit link, imputation/scales,
categorical vocabulary, penalties and hashed development identities. Effective
method reports use the saved model parameters and omit unused training
defaults when applying a frozen model. Predictive coefficients and case/control
sampling probabilities do not establish association effects or population
absolute risk. Calibration summaries remain unavailable for constant,
completely/quasi separated or failed logistic fits; other held-out metrics stay
frozen. Nested CV, survival development, large genome-wide/LD orchestration and
broader transportability/clinical acceptance remain separate work.

A shared reuse defect was corrected for quantitative and binary models:
**tuning identities remain development identities even when final refit is
disabled**. Final fitting rows and all fitting/selection identities have separate
counts. Observed ID/group overlap blocks reuse as independent evaluation.
Legacy quantitative models with unavailable selection-identity coverage carry
an explicit source caveat. Native complete binary reuse requires that coverage.

Completed score-performance tables now carry a per-table completion flag.
If related traits are missing, invalid or unestimable, the related family stays
unresolved with no official adjusted P values. Completed held-out metrics remain
source-linked, nonprimary context in the manuscript inventory. The incomplete
operation is not relabeled complete, and no significant related-phenotype
finding is inferred. Performance metrics are no longer labeled with the separate
related-family FDR rule. Native raw/standardized input snapshots and failed
family dispositions remain available for review.

The focused set passed **31 tests**, including five binary-score numerical,
seven common held-out, fourteen quantitative/import and five binary study tests.
Six independent SciPy BFGS/L-BFGS-B reference fits verify ridge/lasso/elastic-net
coefficients, objectives and KKT residuals. Independent packaged verification
reconstructs tuning, optional refit, frozen probabilities/comparator, AUC,
Brier/log loss and source/model identities, using an optimizer different from
the native cyclic majorization routine. This establishes the declared software
and numerical cases; it is not complete glmnet option parity or clinical
validation.

The final executable passed **13 packaged scenarios and 201 checks**, including
evaluation perturbation, model reuse, selection/group leakage, single-class
training, optimization limits and missing/unestimable related traits. A separate
audit checked **420 sealed outputs, 26 original/resolved study-schema inputs,
six completed score cache replays**, unchanged participant snapshots and the
entire frozen model's invariance to evaluation perturbation. The original
**ten-case quantitative/import workflow** also passed on this exact executable,
including its independent grouped uncertainty, allele-swap, complete-model and
supplied-evaluation/missing-related-work checks. Its historical
`unsupported-training` case supplies a nonbinary outcome to a binary model.

A separately pinned packaged inventory harness retained **216 aggregate
observations across thirteen projects**. Both partially completed projects
retained qualified score performance with no significant related finding;
five unavailable analyses acquired no score observations. Binary interpretation
caveats survived, performance/FDR labels remained distinct, and synthetic
participant/family identities were absent from the dossiers. This is
software/deterministic evidence propagation, not an actual AI scientific
appraisal or a biological manuscript.

The final frozen `check executableJar --no-daemon --no-parallel` passed in
**7m 27s: 1,506 tests across 300 suites, zero failures/errors and 24 optional
skips**. Skips cover three CHOLMOD, two public MR/colocalization, five single-cell
Python, five sequence tools, two Harmony R, two Picard alignment and five HTS
tool cases. Those optional installed/public integrations remain unverified by
this gate. The source snapshot has **2,040 hashed files** under
`build/binary-score-source-20261024-r2/`; **99 guarded implementation/schema/
fixture files** still matched it. Only unrelated literature-status files had
changed at the post-gate audit. The shared GO integrity audit passed read-only,
with all known hashes and absence of synthetic `data.gmt` preserved.

Evidence is retained in `build/binary-score-final-20261024-r1/`:
`verification.json`, `additional-binary-score-audit.json`,
`schema-validation.json`, `finding-inventory-audit.yaml`,
`inventory-audit-software.json`, `full-gate.json`, `full-test-xml/`,
`tested-source-snapshot.json`, `protected-source-guard.json` and
`shared-go-resource-audit.json`. The companion existing-workflow bundle is
`build/binary-score-gaussian-final-20261024-r1/`.
Executable SHA-256:
`2bcade69a60282464979d6246955582a47679052b4f9d78731656710bc3334d2`.
Packaged binary verifier SHA-256:
`ed7fae5b81f59bbe813248ea26f4addabfec1afdd5615d1c83f30d01a83b0cdb`.

Earlier development and packaged attempts remain retained. The preceding
frozen source copy (`binary-score-source-20261024-r1`) passed 1,502 tests in
8m 49s before the subsequent partial-work and effective-method-report fixes;
the final claim above applies to the second copy. A minimal missing exception
serialization ID in concurrent HTS code was supplied for strict compilation,
with its tool behavior preserved. The source build remains unreleased and the
full analysis-pipeline objective remains in progress.

## Binary-annotation GWAS enrichment checkpoint (2026-10-24)

The `gwas_enrichment` study operation now executes observed-scale overlapping
binary-annotation S-LDSC. It consumes a registered complete GWAS summary or
the official complete result, population/build-matched LD scores, all-SNP and
nuisance/target annotation counts and reference intersection moments. It
retains reference/allele/QC dispositions, simultaneous conditional estimates,
category-versus-complement tests, ratio uncertainty, all block deletes and
the complete target correction family. Missing coverage, weak/nonpositive
total heritability, unstable designs or unestimable eligible targets leave
official FDR unresolved; no annotations are dropped or cohorts/models selected
to improve significance.

The binary-annotation estimator uses LDSC's partitioned fixed-initial-weight
path, distinct from the existing iterative unpartitioned LDSC API. Overlap
counts define category heritability; a conditional coefficient times category
size is not substituted for it. The reference generator compares independent
NumPy QR fits and covariance/ratio/contrast estimates with upstream revision
`2fdeeb3b44379408794154993dbd6101b8946b7e` for free and fixed-one intercepts.
The synthetic inputs establish numerical implementation behavior only.

Supplied `gwas_enrichment` artifacts satisfy the corresponding goal and retain
their original complete-family adjustment without a statistical refit. Native
and imported enrichment uncertainty/provenance enter aggregate finding
dossiers. The [tutorial](vignettes/study-pipeline-tutorial.md#ld-aware-gwas-functional-category-enrichment)
documents the full source/reference contract, defaults, interpretation and
supported scope; `examples/study-pipeline/gwas-enrichment-workflow.py` supplies
the packaged positive/negative/reuse acceptance workflow.

Final packaged verification is retained in `build/sldsc-final-20261024-r1/verification.json`:
19 positive/negative/reuse cases, 1,174 independent checks,
747 sealed outputs, complete-stage cache replay,
unresolved-stage retries and `study verify` for every project. Seven native
fits use the independent NumPy QR/overlap/ratio/contrast oracle; complete and
selected-only supplied enrichment preserve their original full-family
adjustment. Duplicate physical SNPs under reversed coding, identical binary
annotations and collinear designs cannot define another fitted coefficient.

The same frozen JAR passed `check executableJar`: 1,527 tests across
303 suites, zero failures/errors and 30 optional
skips, including five S-LDSC core and nine study-stage tests. Its exact source
snapshot, test XML, executable/verifier hashes and local-only scope are sealed
in `full-gate.json` and `tested-source-snapshot.json`. The final archive CRC
audit finds no generated Python bytecode/cache entries; packaging explicitly
excludes those files. Earlier preliminary/interim bundles are retained and are
not represented as this exact executable.

The companion audits validate 38 original/resolved schemas, 17 source-linked
enrichment observations, 119 guarded implementation/schema/fixture
files, and nine actual impact-assessment payloads. Fourteen native enrichment
observations carry aggregate fit uncertainty, reference context and coverage
into that handoff without embedding GWAS/LD input tables. No model request or
scientific appraisal was executed. A separate companion raises the declared
batch/character budgets and checks that every eligible meta/enrichment finding
appears in one unsent payload; the original acceptance inputs are unchanged.
The read-only shared-GO reference audit
passed. These are numerical, workflow and artifact-integrity checks, not a
real GWAS calibration, experimental mechanism or manuscript acceptance result.

This checkpoint is uncommitted and unreleased. Continuous baseline-LD models,
separate per-target CTS models, mixed/admixed ancestry calibration, LD/reference
construction, real genome-wide scale/calibration acceptance and scientific/AI appraisal remain. The
full analysis-pipeline goal remains in progress.


## Conditional binary rare-set checkpoint (2026-10-24)

Native rare-set study execution now accepts independent-participant
`glm/binomial/logit` cohorts under a registered fixed conditional Bernoulli
null. Each cohort/stratum case total is fixed; optional log-odds offsets/slopes
must be genuinely known and source-qualified. Unknown slopes and declared
relatedness/dependence wait for a suitable calibrated external analysis.
Gaussian score/covariance summaries cannot reconstruct this conditional null.
The [binary rare-set guide](vignettes/study-pipeline-tutorial.md#conditional-binary-rare-variant-sets)
states the full mathematical, genotype, coverage, budget and interpretation
contract.

All five set statistics receive complete finite-null or independently piloted
Monte Carlo outer calibration. Internal mid-tail ranks define adaptive/Cauchy
statistics and never become the final association P values. Genotype-pattern
compression preserves count multiplicity and known-offset probabilities.
Exact log tails survive double underflow; fixed-draw sampled tests use plus-one
P values and conditional-tail uncertainty intervals. Binary burden remains
unsigned, without an odds ratio, beta, SE, direction or heterogeneity estimate.
The implementation does not claim numerical equivalence to asymptotic SKAT,
SKAT-O or ACAT packages.

The study branch preserves observed monomorphic cohort rows for joint tests
and independently registered low-MAC set eligibility,
observed-allele MAF/MAC and coverage dispositions; discovery, validation,
all-cohort and eligible omission scopes; BH/BY/NONE joint mask/test families;
and explicit unresolved-family gates. A separately complete primary family
survives an unresolved other scope in aggregate finding dossiers. Private
participant outcomes/offsets/strata and row-aligned genotype tables remain
local. Completed per-gene fits can be reused from a sealed incomplete family
when work limits increase, with unchanged scientific inputs/software. Damaged
derived caches are retained, recorded and recomputed; their contents do not
silently become evidence. Supplied complete set results retain full-family
adjustment without reconstructing private data or refitting tests.

The focused gate passed **27 tests**, including seven conditional numerical,
six native binary study and fourteen existing rare-set workflow tests. An
independent Python individual-label enumeration defines five 225-state cases,
all five final tests and finite-null rejection probabilities. The Java tests
also traverse the central complete null, compare weighted grouped-sampler
frequencies with independent individual-label probabilities, and check a
10,000-participant singleton design, extreme log tails and explicit budget
failures. A separate Python calculation uses frozen raw pilot/main scores to
check every sampled statistic, tail count and Clopper-Pearson interval;
this is distinct from independently checking the sampler's distribution.

Final evidence is retained in `build/binary-rare-final-20261024-r2/`:
`verification.json`, `full-gate.json`, `schema-validation.json`,
`ai-handoff-audit.json`, `numerical-reference-audit.json`,
`partial-primary-resume-audit.json` and `protected-source-guard.json`.
Ten packaged cases passed 251 verifier checks and 804 sealed-output checks,
including exact/sampled three-cohort fits, a cohort-private variant, known
offsets, missing/intersection coverage, unknown slopes, relatedness, minimum
cohort counts and complete supplied-stage reuse. Every main project passed
`study verify`; complete analyses replayed from cache and unresolved scopes
remained unresolved. Six actual unsent payloads carry both complete eligible
finding dossiers, full-family BY denominators and calibrated-test context
without participant rows. The fixture explicitly raises its batching/character
budgets so both large dossiers fit together; the production defaults retain
pending candidates across batches. No model request or scientific appraisal
was executed.

The exact packaged JAR passed **1,575 tests across 311 suites**, zero
failures/errors and 41 optional skips, in 9m24s. Its source snapshot is
`build/binary-rare-source-20261024-r2/`; the seven conditional numerical, nine
binary study and fourteen existing rare-set tests all passed. The archive
passes CRC checks, contains no generated Python bytecode/cache entries, and
its JLinAlg class bytes match the frozen tested classes. The packaged sampler
also reproduces the frozen raw-score inputs used by the independent sampled
statistic/tail/interval oracle.

A separate companion starts with a complete primary family and two unresolved
omission scopes. Both primary findings enter an unsent impact payload with
explicit unresolved-scope caveats. An externally attributed synthetic budget
amendment then reuses **10 completed sealed gene fits** from the waiting run and
completes the missing work. The base study, input data, old cache hashes and
primary results remain unchanged. Twenty main and four companion schema
documents passed validation. The read-only shared-GO resource audit passed.

136 guarded implementation/schema/fixture files match their verified evidence.
The Python workflow verifier was separately pinned and executed after its
payload-budget correction; this is recorded separately from the frozen Java
full gate. The final executable SHA-256 is
`1f6580787915072e0ab7ffc98bef3df8993eb4a6d13ef0f531607aa986e71b1c`.
Earlier preliminary/interim and failed-verifier attempts remain retained and
are not represented as this final exact executable/verification combination.
These are synthetic numerical, workflow and integrity checks, not real
case/control calibration, power, novel biological mechanisms or AI appraisal.

This checkpoint is uncommitted and unreleased. Unknown continuous slopes,
kinship/mixed models, survival, ploidy, ancestry/external-frequency masks,
complete caller/assay QC, genome-wide streaming/scale, cross-phenotype
multiplicity and rare-set enrichment remain. The full analysis-pipeline goal
remains in progress.


## Rare-set downstream integration checkpoint (2026-10-24)

**Verified source checkpoint.** A source-verified `rare_set_followup` projection connects
native and supplied primary gene/mask families to explicit gene mappings,
exploratory GO/Reactome ORA and bounded contextual literature. Original joint
association adjustments survive; enrichment has a separate complete
mask-by-term family. Mixed-source literature retains original probabilities
and does not estimate a combined gene-level FDR. Separate testing families may
share participants, genes and variants; they do not establish independent
replication. Supplied mask-specific
enrichment and pathway-network identities now retain the mask dimension.
The [guide](vignettes/study-pipeline-tutorial.md#rare-set-findings-in-enrichment-and-contextual-literature)
states source selection, backgrounds, correction families and limitations.

The final frozen source passed **1,600 tests across 314 suites**, zero
failures/errors and 41 optional-environment skips, in 8m14s. This includes all
**17** downstream-source/enrichment/literature tests. Its exact packaged JAR
SHA-256 is
`e8e4879c39adb756132e9228daa61e3a8ae414f2476f4cf9236731a8074342c9`;
the source snapshot is `build/rare-followup-source-20261024-r2/`.

Final evidence is retained in `build/rare-followup-final-20261024-r1/`:
`verification.json`, `full-gate.json`, `independent-ora-audit.json`,
`schema-validation.json`, `ai-handoff-audit.json`,
`partial-primary-resume-audit.json` and `protected-source-guard.json`.
Ten packaged workflows passed **320 checks**, with **722 sealed outputs**.
They cover native and supplied families, selected-only backgrounds, unmapped
or ambiguous genes, empty selections, species mismatches, work limits,
mixed association/rare sources and reuse of supplied GO while completing the
missing Reactome analysis. All ten projects passed `study verify`; native
projection and complete enrichment replayed from cache. An independent Python
oracle checks **54 term rows across ten analyses** using exact integer
combinations and rational hypergeometric tails, then independently calculates
the joint mask-by-term BY correction. This verifies numerical behavior, not
the biological exchangeability assumption behind ordinary rare-gene ORA.

A companion deliberately leaves two omission scopes waiting after the primary
family completes. That complete primary family reaches GO, Reactome, contextual
literature and an actual unsent impact payload before the missing work resumes.
An externally attributed synthetic work-budget amendment then reuses **ten
sealed gene fits** from the waiting run. The base study, source data, primary
results, gene projection and enrichment remain identical. The companion has
**220 sealed outputs** and four validated schema documents, in addition to
the twenty main schema documents. Nine main actual unsent impact payloads
include every eligible candidate; 98 public-query audit records explicitly
show no attempted request. No model request or scientific appraisal ran.

All **146 guarded implementation/schema/fixture files** match the frozen
source; archive CRC, compiled-class identity and read-only shared-GO checks
passed. Preliminary and failed attempts remain retained and are not final
acceptance evidence. These include a fixture lacking its coordinate convention,
an insufficiently scoped downstream-question identity and an audit helper
parsing JSON scientific notation through YAML. The implementation now scopes
question identities by operation and prerequisite; source incompleteness is
not reported as a negative result.

These changes are uncommitted and unreleased. Ordinary ORA does not correct
rare-gene detection bias; bias-aware enrichment, broader species/namespaces,
rare signed-ranking/GSEA orchestration, rare-set cohort-overlap plots,
cross-phenotype correction and real scientific/source/AI appraisal remain.
The full analysis-pipeline goal remains in progress.

## Rare-set cohort overlap checkpoint (2026-10-24)

**Verified source checkpoint.** `rare_cohort_overlap` connects native
and result-first per-cohort rare-set families to exact membership, complete
exclusive intersections, Venn/UpSet PNG/PDF and coverage-aware cohort alerts.
Every displayed item retains its set, mask and prespecified primary test;
secondary tests remain in the full source FDR denominator. The separate
any-mask set summary is descriptive and estimates no gene-level P or FDR.
The [guide](vignettes/study-pipeline-tutorial.md#rare-set-cohort-overlap-with-complete-source-families)
describes scope completeness, frozen validation, direction and supplied reuse.

The native diagnostic can use complete cohort families while the source's
primary, discovery pool or omissions remain unresolved. Reduced validation and
required discovery direction still need the discovery reference. Native empty
analyses explicitly declared not estimable do not become negative results or
artificial missing-data questions. Unavailable exported families still wait.
Typed supplied memberships retain source selection, masks and per-source
adjustments; local count/plot views do not repeat the statistical analysis.
An incomplete rectangular membership or entirely absent declared cohort blocks
complete views rather than treating missing rows as unselected observations.

Mixed-source acceptance exposed an existing BY incompatibility in scalar
discovery/replication and unfiltered effect-QC sensitivity. Both branches now
retain the declared BH/BY/NONE method. A regression example distinguishes
BY-adjusted P 0.055 from BH 0.03 and verifies the resulting discovery decision.
Rare and scalar overlap remain separate operations in a mixed hypothesis;
neither changes the primary cohort set or infers independent replication.

The final focused gate passed **90 tests across seven suites**, zero
failures/errors or skips, including ten rare-overlap tests, existing quantitative
and binary rare-set workflows, downstream enrichment/literature, supplied
imports, cohort diagnostics and the BY regression.

The final frozen source passed **1,620 tests across 317 suites**, zero
failures/errors and 41 optional-environment skips, in 8m14s. Its source snapshot
is `build/rare-overlap-source-20261024-r4/`. The packaged workflow and full gate
used the byte-identical executable SHA-256
`21543f94fcb380787a1caeab9762c977db848b2620db7db623364690c1f71b9c`.
Final evidence is retained in `build/rare-overlap-final-20261024-r1/`:
`verification.json`, `full-gate.json`, `schema-validation.json`,
`ai-handoff-audit.json`, `partial-cohort-audit.json` and
`protected-source-guard.json`.

All **16 packaged cases** passed, with **780 sealed outputs** and independent
checks of **1,158 membership cells**. The Python verifier reconstructs cohort
probabilities and full BY families from the original synthetic input tables,
checks reviewed ACAT pools independently, freezes all discovery-selected tests
and masks for reduced validation, and verifies every exclusive intersection.
Two-, three- and four-cohort, union/intersection, signed/unsigned, multiple
discovery, unusual-cohort, mixed scalar/rare and supplied/partial membership
cases retain their distinct dispositions. Every project passed `study verify`;
completed diagnostics replayed from cache while unresolved scopes stayed
unresolved. Required unsigned direction does not become negative validation.

A separate actual conditional-binary run registers `maximum_exact_states: 4`.
Each cohort's four-state null completes, while discovery and primary pooling
remain unresolved at sixteen and sixty-four states. Six cohort membership rows
remain usable with their original joint source adjustments. Discovery candidacy
stays unavailable and no unfinished primary is promoted into a manuscript
candidate. That companion passed two schema documents and **71 sealed-output
checks** with immutable study/data inputs. Thirty-two main schema documents
also passed. Thirteen actual unsent impact payloads were audited without
participant rows; where overlap context is present, its mask/test and
source-denominator fields survive extraction. No model requests or real
scientific appraisal ran.

All **152 guarded implementation/schema/fixture files** match the frozen
source; archive CRC, compiled-class identity and the read-only shared-GO audit
passed. Failed fixtures, the concurrent-document snapshot failure and the
native empty-mask regression remain retained separately from this passing
exact-executable checkpoint. Representative Venn/UpSet views are visually
checked alongside the complete numerical membership/count oracle.

This checkpoint is uncommitted and unreleased. Counts are set/mask/test tuples;
cross-mask gene-level plots, multicontrast overlap, independent variant/assay
comparability appraisal and broader dependence remain. The full
analysis-pipeline goal remains in progress.

## Registered LD finding redundancy (2026-10-24)

The uncapped inventory can attach hash-bound components from explicit local LD
references without changing P values, source FDR, statistical candidacy or
story labels. Both AI selection and human principal prose enforce shared
redundancy components. All supplied reference variants participate, including
unselected mediators; components are deterministic across input ordering.
Shared canonical identities and positive links across hypotheses are joined
conservatively. These are transitive positive-evidence components, not causal
signals, index-variant clumps or proof of independence.

Coverage checks distinguish complete pair windows, reviewed thresholded
exports and descriptive partial edges. Strict default coverage, equality
boundaries, missing/unknown pairs, source build/allele/population conflicts and
bounded work keep impact assessment waiting before model transport while
retaining the statistical inventory. Registered input changes recompute the
inventory and downstream work; the supplied meta-analysis remains reusable.
Unused raw datasets do not override the selected external primary scope.
Retired-only reference scopes are not read. The
[guide](vignettes/study-pipeline-tutorial.md#registered-ld-evidence-for-distinct-principal-findings)
documents configuration, source receipts, caveats and the fifteen-case offline
workflow. Typed statistical imports also preserve approved aggregate metadata,
including REF/ALT, through the existing consistency validator.

The focused gate passed **73 tests across seven suites**, with zero
failures/errors or skips, including sixteen grouping tests and the typed-import,
metadata, impact-selection, score, rare-overlap and colocalization regressions.
The final frozen source passed **1,657 tests across 321 suites**,
zero failures/errors and 51 optional-environment skips. Its snapshot
is `build/finding-grouping-source-20261024-r1/`, with source receipt SHA-256
`52f693571f40e31d98ef9260a2a58a107a24be652d8d155590122057979463ea`.
The full gate and packaged cases use byte-identical executable SHA-256
`87466a52d0e16e42a398f9d0d3213e3870e85204f15e313a66890eec28c2b181`.

Final exact-executable evidence is retained in
`build/finding-grouping-acceptance-20261024-r2/`: `verification.json`,
`full-gate.json`, `schema-validation.json`, `ld-source-audit.json`,
`ai-handoff-audit.json`, `protected-source-guard.json` and sealed receipts.
All **15 packaged cases** passed, with **762 sealed outputs** and
an independent graph reconstruction checking **60 finding memberships**.
Thirty source/resolved schema documents pass. Eight actual unsent impact
payloads include every eligible candidate when grouping is available, with
hash-bound LD sources and redacted local paths. All 158 guarded
implementation/schema/fixture files match the frozen source. Source effects,
uncertainty, P values, FDR, aggregate N and variant identities remain unchanged.
Missing/changed references invalidate only inventory/dependent work; supplied
meta-analysis statistics remain cached and no statistical fit is repeated.

Archive CRC, compiled-class identity and read-only shared-GO checks pass.
The preliminary allele-conflict fixture exposed discarded import REF/ALT;
that failure and a new-test compilation failure remain retained separately
from this passing checkpoint. No model request or real scientific appraisal
ran. The preceding checkpoints remain separately retained historical evidence.
This checkpoint is uncommitted and unreleased. Automatic LD-reference
retrieval, native LD extraction, conditional fine-mapping, real source
appraisal and scientific/AI evaluation remain. The full analysis-pipeline
goal remains in progress.

## MR coverage before annotation checkpoint (2026-10-24)

MR selection now retains all primary full-family eligible molecular features
before annotation and QTL matching. Missing, ambiguous, conflicting and unlocated
annotations remain in per-feature dispositions and the selected family. An
explicit cap applies before annotation; contextual/literature limits remain
independent. Source association adjustments, denominators and hashes remain
visible, and unresolved MR tests keep adjustment bounds provisional.

An approved pair can supply a sourced `feature_location` for its exact molecular
feature when local coordinates are unavailable. The existing source identity,
assay, genome-build, ancestry, overlap, regional containment and instrument
checks still apply. Known sealed-location conflicts are held. Gene identities
remain unresolved rather than inferred. Location provenance and annotation/
family context enter the finding inventory; a mandatory caveat reaches AI
dossiers. Unlocated-only automatic source selection opens neither candidate
exports nor outcome/LD providers. The separate 100-signal literature budget no
longer imposes a 100-pair validation cap on reviewed MR sources.

The packaged integration check also exposed a native MR inventory defect:
generic TSV ingestion attempted to parse internal LD matrices as result tables.
The inventory now reads the canonical MR table and the sealed genetic-evidence
document. A regression test retains matrix artifacts without treating them as
findings. The [guide](vignettes/study-pipeline-tutorial.md#mr-coverage-before-gene-annotation)
documents the contracts and fourteen-case independent workflow.

The final focused gate passed **93 tests across seven suites**, zero
failures/errors or skips. It includes exact-feature location/provenance policy,
missing/ambiguous eligibility, a denominator regression, native MR without
inferred gene names, source guards and the native MR-to-inventory regression.
The frozen source passed **1,679 tests across 324 suites**,
zero failures/errors and 52 optional-environment skips. Its snapshot
is `build/mr-coverage-source-20261024-r1/`, source receipt SHA-256
`93bbce20ecfcf6369c0e376151d4706acb3bc9c7bb9f0fd826fdca0c5e985592`. Full-gate and packaged executable SHA-256:
`f2176131baef4cc2278e4533515751b7cbdec477caa9303556ef5e478ec2930e`.

Exact final evidence is retained in `build/mr-coverage-acceptance-20261024-r5/`:
`verification.json`, `full-gate.json`, `schema-validation.json`,
`mr-coverage-audit.json`, `ai-handoff-audit.json` and sealed receipts.
All **14 packaged cases** passed, with **4915 sealed outputs** and
**846 independent estimate/P/adjustment checks**.
The 105-pair case retains a full family of 105 tests, including its completed
unestimable test. Missing prerequisites keep other completed P values and
adjustment bounds visible but provisional. Source association P values, BY,
denominators and primary hashes are independently checked before annotation.
Forty-two initial/base/resolved schema documents pass. 13 actual unsent impact
payloads retain every eligible candidate, including all 105 in the large case,
with source-linked annotation/coverage/location context and required caveats.
No model request or real scientific source appraisal ran.

An externally attributed amendment adds the fixed synthetic location; the
base study and source inputs stay immutable. MR completes after the amendment
and its next execution reuses the completed operation. A supplied primary
meta-analysis retains its original testing family and is not refitted.
All 168 guarded implementation/schema/fixture files match the frozen
source. Archive CRC, compiled-class identity, shared-GO read-only invariants,
website/citation and local-fragment checks accompany the sealed checkpoint.
Earlier matrix-ingestion, fixture-configuration and direct-base-edit failures
remain retained separately; the two-case amendment/supplied-primary preflight
is also retained. Earlier completed checkpoints remain historical evidence.
This checkpoint is uncommitted and unreleased.
Cross-phenotype correction, broader automatic assay/source adapters, annotation-
free colocalization, scientific source/assay appraisal and real-model validation
remain. The full analysis-pipeline goal remains in progress.

## Supplied MR contributions and missing-feature completion (2026-10-24)

Explicit role: contribution imports now retain externally completed molecular-feature MR while a separate native operation completes uncovered eligible features. Coverage is reserved even when a producer is missing, altered or conflicting; no smaller-P source or native fallback replaces unresolved provided work. Identical analysis aliases share one primary test. Original source-family adjustments remain intact, while the registered joint MR family is corrected separately. Completed unestimable tests and unresolved prerequisites retain different dispositions.

Source digests, original snapshots, producer metadata, declared primary estimator, exact feature coverage and hypothesis outcome are checked. Native reviewed pairs and automatic acquisition skip contributed features. Source and execution origin enter method reports and finding dossiers with mandatory external-reuse caveats. A full supplied primary still replaces the whole stage; contributions currently support native MR completion.

The [completion guide](vignettes/study-pipeline-tutorial.md#complete-missing-mr-features-from-supplied-contributions) documents the contract and seventeen-case independent workflow. The final focused gate passed **81 tests across five suites**, with zero
failures/errors or skips, including eleven contribution tests. A preceding
broader planner/resume gate also passed. The frozen source passed
**1,717 tests across 331 suites**, zero failures/errors and
67 optional-environment skips. Its snapshot is
`build/mr-completion-source-20261024-r2/`, source receipt SHA-256
`56a85e383afe85d3b334785d199b4335938a27d7984acb73d2c77caa116a7c1d`. The full gate and packaged workflow use
byte-identical executable SHA-256
`32984269453f7bb8cf95255ba1af6f1b85e30e1f0949cd4aa9e16de9a885ff73`.

Final evidence is retained in `build/mr-completion-acceptance-20261024-r4/`:
`verification.json`, `full-gate.json`, `schema-validation.json`,
`mr-completion-audit.json`, `ai-handoff-audit.json` and sealed receipts.
All **17 packaged cases** passed, with **1,556 sealed outputs** and
**140 independent estimate/P/joint-BY checks**.
Mixed completion processes only two uncovered native pairs. The all-provided
and result-only-primary cases process zero native pairs and require no raw QTL
inputs for the supplied features. One result-only case has no cohort dataset.
Wrong hashes, incomplete scopes, competing analyses, changed canonical/raw
snapshots, source protocols and unknown identifiers retain reserved coverage.
Known unit/protocol differences cannot be collapsed as aliases merely because
their estimates match. Association selection and explicit caps stay frozen.
The added completed-unestimable source case keeps all four features in the
joint denominator, reuses the two unavailable external tests and processes only
the two uncovered native pairs. A retained control run with the preceding JAR
remained waiting; the final JAR completes that same registered source scope.

Fifty-one initial/base/resolved documents pass the public schema, retaining
the existing network file-role condition. All 17 actual unsent impact payloads
include every eligible candidate, external execution origin, original source
adjustments and reuse caveats. No model request or scientific source appraisal
ran. An external-actor amendment completes the missing native source while base
data and supplied analyses remain immutable. A subsequent completed composition
replays from cache. Statistical source reuse remains distinct from independent
reproduction and the new joint correction.

All 171 guarded implementation/schema/fixture files match the frozen
source. Archive CRC, compiled-class identity, read-only shared-GO invariants,
website/citation and local-fragment checks accompany the sealed checkpoint.
The four-case preflight and passing preliminary sixteen-case run remain
retained separately from final exact-executable evidence. Preceding completed
checkpoints remain historical evidence. This checkpoint is uncommitted and unreleased. Broader per-stage partial composition, assay/source appraisal, cross-phenotype correction and real-model scientific validation remain. Native per-pair reuse is addressed by the subsequent checkpoint. The full analysis-pipeline goal remains in progress.

## Native genetic-pair completion and resume (2026-10-24)

MR, colocalization and fine-mapping retain completed per-pair numerical results
independently of the enclosing operation. `study run`/`study auto` resume can
reuse verified completed or completed-unestimable pairs from a waiting stage
while attempting its missing pairs. Fresh reruns bypass this reuse. Identity
includes source bytes and reviewed declarations, current feature eligibility,
trait authorization, effective inference controls, compiled implementation,
the numerical dependency and JVM identity. Current local sources must exist;
public sources need pinned checksums. Automatic source/reference acquisition
without a pinned pair identity remains outside pair reuse.

The receipt and every pair output must match the producer operation seal before
any file is copied, and copied bytes must still match their sealed hashes.
Acquired source snapshots must also match the current registered input bytes.
Invalid derived artifacts remain retained. The direct executor bypasses them
and attempts current inputs; the audited CLI first blocks execution if project
history itself has damaged hashes. Pair reuse does not waive that integrity
check. The receipt stores raw numerical results before joint
MR correction. Current family completeness, duplicate handling, adjustment and
colocalization output composition are evaluated again. Finding dossiers retain
source-linked reuse provenance and the caveat that reuse is not independent
replication. Execution-cache implementation 42 separates this behavior from
earlier operation-only cache results.

The packaged fine-mapping route exposed an inventory defect: internal numeric
LD matrices were being treated as result tables. Native fine-mapping inventory
now reads the sealed exposure/outcome posterior tables under their measured
feature, retains all variant rows with convergence/source caveats and excludes
internal matrices. Supplied fine-mapping still uses its canonical imported
table. Posterior context does not create association eligibility.

The final focused gate passed **84 tests across 6 suites**, with
zero failures/errors or skips, including seventeen pair-completion regressions.
The frozen source passed **1,747 tests across 335 suites**, with zero
failures/errors and 69 optional-environment skips. Source snapshot:
`build/genetic-pair-reuse-source-20261024-r2/`, receipt SHA-256
`09acd663426a2994a30e8b61ff743854e54215178457fc06f2e6829d610326fe`. The full gate and final packaged workflow use
byte-identical executable SHA-256
`8e68c4c5574e87105e931be7f8d535d7b42e8b746677e203a717781f88325705`.

Final evidence is retained in `build/genetic-pair-reuse-acceptance-20261024-r3/`:
`verification.json`, `full-gate.json`, `focused-gate.json`,
`native-pair-audit.json`, `schema-validation.json`, `ai-handoff-audit.json`
and sealed receipts. All **11 packaged CLI cases** passed, with
**5,462 artifact files sealed for acceptance**, including the
retained controlled-damage evidence, and
**266 independent numerical checks** of IVW estimates/SE,
normal-tail probabilities, current full-family adjustments and colocalization
posteriors. MR, colocalization and fine-mapping source amendments reuse three
completed pairs and attempt only one missing pair. Pair addition reuses two
pairs and attempts two. A correction-only amendment reuses all four raw fits;
a changed colocalization prior or explicit rerun performs four new attempts.
Revoked approval and an unavailable current source remain waiting, with no
substitution from an old completed fit. The damaged-history CLI case correctly
blocks all new execution. Direct executor tests separately prove per-pair
invalidation and recomputation while retaining invalid derived outputs.

All thirty-three initial/base/resolved documents pass the public schema. Eleven
actual unsent impact payloads include all four eligible candidates, source-linked
native reuse and the non-replication caveat; no model request or scientific
source appraisal ran. External-actor amendments preserve the base study and
raw inputs without human attestation. The fine-mapping inventory failure and
passing corrected run retain identical registered base input as a before/after
control. The successful four-case preflight and earlier failed attempt remain
separate historical evidence.

All 174 guarded implementation/schema/fixture files match the frozen
source. A separately pinned standalone verifier corrects only one history-case
assertion after the frozen source; fixture generation, analysis commands and
Java implementation are unchanged (`standalone-verifier-revision.json`).
The later separate `single-cell-qc` command dispatch/help addition is preserved
and excluded from this frozen test claim; an exact source comparison verifies
that study dispatch is unchanged (`post-snapshot-unrelated-dispatch.json`).
Archive CRC/compiled-class identity, read-only shared-GO invariants,
website/citation and local-fragment checks accompany the sealed checkpoint. This checkpoint is uncommitted
and unreleased. Broader supplied per-stage partial composition, versioned
automatic source/reference reuse, assay/source appraisal, cross-phenotype
correction and real-model scientific validation remain. The full
analysis-pipeline goal remains in progress.

## Registered subgroup interaction checkpoint, 2026-10-24

The source build now connects registered two-level adjusted interaction models
to participant QC, full interaction-family correction, typed supplied-result
reuse, finding dossiers and impact review. See the
[interaction guide](vignettes/study-pipeline-tutorial.md#registered-adjusted-subgroup-interactions).
This is local uncommitted implementation evidence, not a released feature claim
or scientific source/model appraisal.

Native routes cover classical and HC3 OLS and independent Bernoulli logit/Firth
likelihood-ratio tests. Explicit models, ordered contrasts and an independent-row
assertion are required. Default nuisance terms include covariateÃƒâ€”group and
covariateÃƒâ€”feature interactions. The workflow does not select interaction tests
by primary P values or histogram differences. Genotype tests inherit marker QC
and require group-specific MAC >=20 by default. Circular outcome-derived groups
retain descriptions without inferential interaction or outcome-comparison P.

Every test retains a disposition. Prespecified QC/support exclusions lie outside
the correction denominator; structurally unestimable eligible tests remain in
it. An unresolved fit, source, missing feature, work budget or unknown universe
withholds all native adjusted P values and candidate flags. Unrepresentable
input-unit coefficients or uncertainty remain unresolved even if a standardized
test tail is finite. Eligible interaction evidence can reach impact review
without changing primary association significance. Nominal interaction caveats
are source-specific and do not invent a primary MR analysis.

Complete supplied interaction families retain their original methods, scopes,
full-family adjustments and external attribution. Partial imports can finish
validation while remaining context-only. Whole supplied families skip native
fits; automatic per-feature partial interaction composition remains pending.
Verified whole-operation resume and an explicit external-actor budget amendment
complete a held native family without changing base inputs.

Acceptance bundle: `build/subgroup-interactions-acceptance-20261024-r2`. Its exact JAR SHA-256
is `40abb409687b45b1d59e7c43c693d9d89dd2a0ff565dd29477dac8c845c071ff`. The frozen source receipt is
`d873b3d70de8d3d3d1066dafc5586d8c98404db5af41c1955d34979cff22d87a`. The complete isolated `check executableJar`
gate passed **1,801 tests across 342 suites**, with zero
failures/errors and **85 optional skips**. The focused gate passed
**53 tests across six suites**, with zero skips. Optional external-tool skips are
not evidence of installed-tool execution.

The packaged example covers **16 cases and 18 retained execution
phases**, including all four inference routes, a joint multiple-dataset family,
group MAC, circularity, nominal inference, absent/unsupported models, dependence,
missing scope, budget amendment/resume, extreme units and complete/partial
external results. **142 independent numerical comparisons** use unscaled
statsmodels/SciPy designs, full-design constrained Firth objectives, relative/log
probability checks and an independently reconstructed correction family.
**1039 operation artifacts** are hash-sealed by the standalone verifier.

All **48 initial/base/resolved documents** pass the public schema, with
**10 positive/negative schema controls**, including preservation of the
network nodes/edges requirement. **7 actual unsent impact payloads** preserve
every eligible interaction candidate, source IDs and caveats while excluding
participant sentinels and absolute workspace paths. No model request or actual
scientific impact appraisal ran. Participant membership stays local.

The earlier 1,799-test snapshot, original overflow probe, corrected control,
failed attempts and preflights remain separate historical evidence. The same
raw numerical fixture establishes the before/after unit-overflow behavior.
All **185 guarded implementation/schema/fixture files** match the final frozen
source. Archive CRC and class identity, source/fixture versions, raw input
hashes, immutable external-actor amendments and unchanged read-only shared GO
references have separate receipts. Final website, citation and local-fragment
verification accompanies this checkpoint.

Dependence-aware mixed/clustered/repeated/survival interactions, multilevel or
continuous modifiers, biological sex/PAR and broader ploidy routing, automatic
partial per-feature interaction composition, cross-phenotype correction,
study-specific calibration and actual scientific/AI appraisal remain. Complete
assay QC and the other broad specification gaps remain tracked above. The full
analysis-pipeline goal remains in progress; this checkpoint is uncommitted and
unreleased.

## Registered genotype sample-QC checkpoint, 2026-10-25

The opt-in registered `analysis.genotype_sample_qc` profile connects observed
autosomal hard-call sample eligibility, a complete-source hash-sampled/local-LD
panel, missing-aware all-pair KING between-family estimates and unrelated-basis
projected genotype PCs to native association, rare consumers and aggregate
finding context. The [sample-QC guide](vignettes/study-pipeline-tutorial.md#registered-genotype-sample-qc-and-projected-pcs)
documents the exact policy and artifacts. This is local uncommitted source
evidence, not a released-feature or complete scientific-QC claim.

Observed call-rate selection can use a separately aligned hard-call panel;
complete imputed dosages cannot conceal poor original calls. Structural
sex-chromosome absence is outside its autosomal denominator. Initial rate
records and all exclusions remain available even if no participants survive.
A bounded main-matrix imputation-quality availability probe is distinct from
per-marker quality filters and is unnecessary for QC-only work.

Native pair decisions require complete source/pair scope and registered support.
Relatedness can hold for review, use explicitly registered greedy exclusions,
or retain all samples only with a declared supported quantitative GRM model.
The downstream model still validates covariance and fit. A negative screen
does not override known pedigree/dependence. Heterozygosity is a warning, not
automatic participant deletion. Related samples are projected into the same
unrelated PCA basis. Generated PCs enter the model only when requested.
Supplied ordinary PC covariates can be reused with native PCA disabled; the
method record does not claim regeneration. Held rows cannot enter association.

Acceptance bundle: `build/genotype-sample-qc-acceptance-20261025-r1`. Exact executable
SHA-256: `4bf246cbba1d062392441c6cdd394e98e0408a0aedda8c232c9088c7bae44974`. Frozen source receipt:
`5d9a30a59fe5c3e9633f577c1235d7e2511c8759deafe1762c9c3e49ba976da1`. The isolated `check executableJar` gate
passed **1,846 tests across 348 suites**, with zero
failures/errors and **90 optional skips**. The focused gate
passed **53 tests across 6 suites**, with zero
skips. Optional external-tool skips do not establish installed-tool execution.

The final pinned executable/verifier covers **25 cases
and 27 retained phases**. **425,000 independent
numerical comparisons** reconstruct call rates, every pair's exact counts and
kinship estimates, panel hash/LD selection, NumPy eigenanalysis/projection
weights and SciPy downstream OLS tails with relative/log-P checks. All
**1,489 operation artifacts** are hash-sealed. Cases include
same/separate/reordered panels, observed calls behind complete imputation,
all-participant call-rate failure, structural sex-chromosome absence, absent
imputation quality, QC-only scope, supplied PCs, relatedness policies, partial
sample coverage, PC collisions, missing phenotypes, work limits and an explicit
external-actor budget amendment/resume. The covariance-model case verifies QC
eligibility only; it does not claim an executed mixed-model association.

All **75 base/initial/resolved study documents** pass
the public schema. **13 positive/negative controls**
include preservation of the supplied-network nodes/edges condition. All
**11 actual unsent impact payloads** retain
eligible candidates, aggregate QC source IDs/hashes and required caveats,
excluding participant IDs/tables and absolute workspace paths. No model
request or scientific impact appraisal ran. Context concerns the current
QC dataset and does not retrospectively validate supplied result populations.

All **201 guarded implementation/schema/fixture files**
match the tested frozen source. Separate receipts bind archive CRC/classes,
raw inputs, software/reference versions, retained attempts and unchanged
read-only shared GO resources. Final website, citation and local-fragment
verification accompany this checkpoint.

This bounded native route does not complete assay/caller QC, confirmed sample
identity/contamination, biological sex/PAR/ploidy interpretation, Mendelian
checks, ancestry-reference classification, study-specific calibration,
genome-wide scaling or typed complete external-QC report integration. These
and the other specification gaps remain. Supplied-result reuse, flexible
main/supplement budgets and thorough principal-finding discussion requirements
remain in force; actual scientific/AI appraisal is a separate requirement.
The full pipeline goal remains in progress; this checkpoint is uncommitted
and unreleased.

## Supplied genotype QC and missing-PCA checkpoint, 2026-10-25

The [external QC/PCA guide](vignettes/study-pipeline-tutorial.md#reuse-external-sample-qc-and-pca-and-complete-missing-work)
documents scoped reuse of completed external sample QC and PCA, plus explicit
native completion of missing PCA. Global and selected-cohort source selectors
permit native and supplied work in the same hypothesis. This is local,
uncommitted source implementation; import validation does not independently
reproduce or certify the scientific source analysis.

The normalized report binds the current matrix bytes, full matrix-sample
universe, decision/score file hashes, original source populations, declared
KING between-family thresholds/actions/support and component completeness.
Current phenotype/covariate subsetting preserves imported PC values and their
original basis. An explicitly declared external-reference PCA basis may be
larger than the matrix population; its identity/hash remains source-reported,
without automatic retrieval or reproduction. Bad or incomplete scope cannot
silently select a smaller population or trigger a replacement fit.

Complete reuse performs no native call-rate selection, KING or PCA. It has
separate linear import bounds. Requested missing native PCA uses the accepted
external independent basis within current eligibility, with its own native
panel/LD/Gram/projection limits; it does not fit source kinship again or exclude
additional retained participants just because they are not basis members.
Source QC exclusions remain available when PCA is pending. Main-matrix
imputation metadata, per-marker QC/MAC and an appropriate dependence model are
still required by their native consumers.

Original reports, scores, provenance, methods and limitations remain local.
Automatic Methods records and finding/impact payloads exclude private source
notes, keeping aggregate facts, source hashes, origins and explicit review
requirements. A source-specific limitation still needs scientific appraisal;
the controlled summary is not a replacement for that review. Independently
supplied downstream results retain their historical QC/provenance.

Acceptance bundle: `build/external-genotype-qc-acceptance-20261025-r1`. Exact JAR SHA-256:
`e99e761b1579ee0842443dda9fe99817cee696694a430908814810e31d564de2`. Frozen source receipt:
`e3e3be749042bd53bd5c4160d6764d6051493aafd5a40d95559c22b4ce2fa28f`. The isolated `check executableJar` gate
covered **1,880 tests across 352 suites**, with zero
failures/errors: **1,790 passed and 90
optional skips**. The focused gate passed **72 tests across
7 suites**, without skips. Optional external-tool skips do not
establish installed-tool execution.

The pinned packaged workflow covers **23 cases and
27 retained phases**, with **27,116 independent
numerical comparisons** and **1,527 sealed operation
artifacts**. It verifies exact source PC reuse, missing native PCA and projected
non-basis members against NumPy, current-sample OLS/SciPy tails with relative/log
checks over all 45 association rows and 450 association comparisons, mixed cohort source routing, incomplete/mismatched source controls,
imputed-data prerequisites, source/import work bounds and verified resume.
An explicit external-actor amendment can complete a held import budget or bind
a new immutable source edition; old raw/base/source files and attempts remain.
Different source editions have different cache keys and score values; the next
unchanged run reuses the verified completed edition. No human attestation is
invented by these synthetic amendment drivers.

All **69 base/initial/resolved study documents** and
**24 external report documents** pass their
public schemas. **18 positive/negative controls** retain
the network nodes/edges condition and cover source selectors, incomplete
reference metadata, support and count declarations. Every actual eligible
candidate reaches its unsent comparison in the standalone verifier. The
**14 actual unsent impact payloads** preserve
source IDs/hashes and external caveats, excluding private method/limitation
notes, participant IDs and absolute paths. Automatic Methods records have a
separate sentinel check. No model request or scientific impact appraisal ran.

All **207 guarded implementation/schema/fixture files**
match the frozen tested source. Separate receipts verify archive CRC/classes,
raw/source hashes, source/reference versions, retained attempts and unchanged
read-only shared GO resources. Final website, citation and local-fragment
checks accompany the checkpoint.

Arbitrary upstream-tool export parsing, independent source/calibration
appraisal, genome-wide-scale scientific acceptance, caller/sex/PAR/ancestry
validation and the remaining broad pipeline requirements are not completed by
this source adapter. The full implementation goal remains in progress. These
changes are uncommitted and unreleased.

## Frozen discovery source-family checkpoint, 2026-10-25

The [frozen discovery guide](vignettes/study-pipeline-tutorial.md#preserve-supplied-discovery-fdr-during-validation)
and `examples/study-pipeline/frozen-discovery-workflow.py` document reuse of
completed discovery pooling while validation executes separately. The
implementation now preserves a sealed imported discovery family's adjusted P
values, method and denominator. Validation cannot redefine discovery FDR from
the exported rows or their validation overlap. Native discovery still computes
its registered correction on its complete observed eligible contrast.

A retained pre-fix regression demonstrated source **q = 0.4** becoming
**q = 0.000002** when BH was recomputed on two exported rows. The corrected
comparison retains q = 0.4 and the original denominator of 400,000; that row
does not become a discovery-selected or replicated finding. Source corrections
may differ from validation correction. The two validation scopes, same-sign
rule and actual selection thresholds remain explicit.

Discovery selection records a separate, non-official source identity with
canonical/metadata/producer hashes and local snapshots. Its aggregate Methods
record identifies the source correction, denominator, coverage and validation
scope. Changed/incomplete sources hold selection; a pooled discovery source
does not replace the official all-cohort primary meta-analysis. Execution cache
implementation 46 prevents reuse of older operation results under the revised
implementation.

The tested source is `build/frozen-discovery-source-20261025-r1`; its receipt
SHA-256 is `6d295e2b8ddf3658853d74c3343b593fa8286094de9c05891f5523197a556732`. The executable SHA-256 is
`2301f3e6fb7f7ddf0807bb16cfa7551bee79fcefa12be95a7ff4f143f1e2d52b`. The acceptance bundle is
`build/frozen-discovery-acceptance-20261025-r1`.

The isolated **`check executableJar`** gate completed **1,888 tests
across 353 suites**, with **zero failures/errors**,
**1,798 passing** and **90 optional skips**.
All seven dedicated frozen-discovery regressions passed, including real native
discovery pooling, selected-only and complete source correction, canonical and
metadata tampering, incomplete producers and ambiguous supplied contrasts.

The exact executable passed **9 packaged cases**, with
**16 replication rows and 80 independent
probability/selection/direction comparisons**. Cases cover supplied selected-only
exports, complete BY/Bonferroni/NONE families, native BH/BY discovery pools, both
validation scopes, opposite signs, cache resume and unresolved-family holds.
No association refits, external requests or AI calls ran in these fixtures.
Separate audits validate nine study schemas, all 8
completed replication Methods records, 605 sealed operation
artifacts, unchanged source inputs, archive CRC/compiled-class identity and
14 guarded source files. The failing pre-fix XML remains
separate from passing acceptance evidence.

These are synthetic software acceptance cases. Imported scientific scope,
independence, calibration and original analysis completeness still require
source review; the correction does not independently reproduce external
findings. The full implementation objective remains in progress. This local
checkpoint is uncommitted and unreleased.

## Discovery reference and scope checkpoint, 2026-10-25

The [reference and scope guide](vignettes/study-pipeline-tutorial.md#discovery-cohorts-represented-by-supplied-pools)
documents `stage: analysis_reference`, with `files: {}`, cohort membership and
`supplied_result_id` bound to a supplied primary meta/discovery pool. Validation
can execute from supplied validation tables or fit a raw validation matrix
without unavailable original discovery files. References preserve metadata;
they do not create constituent cohort estimates or retrospective QC evidence.

Before imported discovery selection, exact dataset/cohort membership, pool
ownership and the registered trait are checked against sealed source metadata.
Each discovery/validation cohort pair needs an explicit independence declaration;
default pending independence assumptions cannot establish this source contract.
Known overlap, reused cohort identities and sampling links are audited using
all role metadata, including discovery cohorts without tables. Known
effect/context/build/namespace or allele-coding conflicts hold selection;
registered assay/tissue/modality exceptions retain their declared role. Unit
comparison preserves case. Incomplete source effects remain uncertified by
default and hold the strict profile. A requested native all-cohort fit holds
when it lacks the constituent estimates, even with two validation tables
available; it cannot silently omit the referenced discovery cohorts.

The frozen source is `build/discovery-scope-source-20261025-r2`, receipt SHA-256
`5598836afd2a39e35f0b3950a0dbc6e0a05d7f0a96d5b46f446aee18231eb669`. Executable SHA-256:
`0a3a5a4deaecc0f1f69470f314ece8b76c132374a399a3f875aeb0235dc68947`. The main acceptance bundle is
`build/discovery-scope-acceptance-20261025-r2`.

The isolated **`check executableJar`** gate completed **1,897 tests
across 354 suites**, with **1,807 passing**,
**90 optional skips** and **zero failures/errors**. The focused
gate passed **43 tests in 5 suites**, including nine
dedicated source/reference regressions. Defaults version 31 and execution
implementation 47 identify the revised contract.

The exact executable passed **24 main packaged cases**: seven completed
missing validation, thirteen correctly held selection and four invalid
reference/scope declarations were rejected. Independent NumPy/SciPy checks
cover **2 native validation association rows**;
the sample count is established by the 48-row analysis roster and residual
df = 46 with two fitted parameters. Generic standardized association rows do
not export `n`; no nonexistent column is counted as verified. The workflow
checks **16 replication rows and 89
probability/selection/direction/sample comparisons**, immutable source inputs,
source-only ownership, cache reuse and 860 sealed operation
artifacts. The synthetic complete-family BH values are independently
reconstructed using 399,998 omitted null tests, preserving the original 400,000
denominator.

A separate exact-executable run of the earlier frozen-family workflow passed
**9 cases**, **16 replication rows** and
**80 independent comparisons**, with
**621 operation artifacts** rechecked. This covers the
earlier native/imported correction and direction contracts under the new scope
gate. Separate audits bind native fitting to its approved plan, verify all 24
study documents and seven completed replication Methods records, archive
CRC/compiled-class identity, 23 guarded source/schema files,
final website/citations and unchanged shared GO resources.

The retained pre-fix XML proves one source-scope leak. Its separate invalid test
fixture failure is explicitly not statistical defect evidence. The first
packaged preflight also retains the previously completed validation under an
undeclared independence assumption and the verifier's failed attempt to read
an absent generic `n` column. Corrected execution/verifier evidence is sealed
separately. Packaged example inputs and prior attempt outputs remain unchanged.

All data, scope declarations and approvals are synthetic. These checks do not
independently prove participant disjointness, original source calibration,
biological transportability or complete scientific scope. No external requests
or AI appraisal ran. The full implementation objective remains in progress;
this local checkpoint is uncommitted and unreleased.

## Supplied scientific scope and partial network checkpoint, 2026-10-25

The [scientific-scope and partial-network guide](vignettes/study-pipeline-tutorial.md#scientific-scope-and-missing-cohort-networks)
documents the new import audits, source-scoped graph completion and
`network.complete_missing` (default true; false selects supplied-only processing
when primary graphs are present). Metadata-only references can now be owned by
any supplied primary analysis. References are not constituent effect estimates
or retrospective participant-QC evidence.

Known registered trait/population/context conflicts and row build/sequence
contradictions hold the affected import without promoting canonical statistics
or silently replacing the source. Missing declarations stay explicitly
uncertified. External MR/colocalization/fine-mapping/score populations and
annotation/literature context have different matching roles. A sealed parent
with incompatible scope holds its declared downstream import; an unavailable
original parent file alone does not nullify an independently supplied downstream
analysis. Source declarations are not independently certified scientific truth.

Supplied graphs reserve declared dataset/cohort coverage. A provided A graph
does not suppress a missing native B graph or trigger an A refit when its raw
matrix exists. Multiple primary graphs need comparable disjoint scopes; a pooled
graph remains one graph. Unknown coverage does not guess native work. Modules
and joint module GO retain each graph's lineage. Original methods/provenance,
limitations and family rationale stay in local source records; automatic
Methods, finding dossiers and bounded unsent impact evidence use aggregate
facts with mandatory local-review caveats. This is not general disclosure or
scientific-validity certification.

Verified locally against `build/scoped-supplied-acceptance-20261025-r1/`:

- Frozen source: 1,922 tests in 357 suites;
  1,824 passed, 98 optional skips and zero
  failures/errors. The actual Gradle execution exit was zero. Focused acceptance
  passed 57 tests across five suites, including 13 dedicated scope tests.
- The unchanged pinned public verifier passed 20 scenarios, rebuilding
  75 signed native edges and 51 module-GO
  rows in 177 independent numerical comparisons.
  It verified 843 sealed operation artifacts, source hashes,
  cache reuse, held/invalid contracts and original-versus-aggregate metadata.
- Supplemental native/supplied-network, generic supplied-result and discovery
  scope/family regressions passed 41 scenarios against the
  same packaged executable. Complete-source and selected-only FDR rules remain
  distinct, with no denominator reconstructed from significant rows.
- Both schema copies matched and all 20 generated contracts passed schema
  validation; overlapping graph scopes are rejected by cross-document Java
  checks. 30 import Methods records and
  23 guarded source/schema/verifier files were audited.
  The shared GO installation remained unchanged; synthetic resources were
  project-local. Archive CRC and every compiled main class matched the frozen
  JAR. Source receipt: `a08de938603bf249d69806d49dec348c75a396c2dc0e3f9d5795c5700807bbcc`.
- Packaged executable SHA-256: `3b43bb3ccd3dbb8cb0a5fac71fbd0368dcd5b93a98ed26fc40ec6a9530053c46`.

The genuine pre-fix scope/planning and private-family dossier failures are
retained. Preflight editions with a fixture schema error, an interrupted run or
changed verifier pins are not final acceptance. The R4 patched-verifier probe is
explicitly diagnostic only; this checkpoint uses a fresh unchanged frozen
verifier and exact packaged executable. No external requests, real AI impact
judgment or biological validation occurred. These are uncommitted, unreleased
source-build checks, not complete pipeline conformance. Defaults version 32 and
implementation identity 48 invalidate earlier incompatible cached operations.

## GWAS result diagnostics checkpoint, 2026-10-25

The [GWAS diagnostic guide](vignettes/study-pipeline-tutorial.md#gwas-qq-and-manhattan-diagnostics)
documents the registered `gwas_diagnostics` operation, typed reuse and resolved
policy. Eligible SNP association sources trigger automatic diagnostics; rare-set
or later-stage results alone do not request an unavailable SNP scan. An explicit
SNP summary can independently enable GWAS enrichment. Explicit requests remain
subject to their own input requirements.

Each source and contrast retains a hashed statistics snapshot, producer receipt,
row dispositions and full numerical QQ/Manhattan tables. Finite natural-log P
values preserve underflow tails; inconsistent or unresolved exports stay
explicit. Coverage controls the uniform-reference/lambda display. Selected-only,
unknown, incomplete and unresolved exports do not acquire full-null evidence.
Lambda is a descriptive P-to-chi-square-one transform, not a calibration
certificate or a correction. Independent MAF/MAC/INFO/test strata, source build
and chromosome/position checks remain visible. A native pooled source can use
the sealed fitted-contributor metadata ledger for coordinate consensus; it
cannot borrow cohort P values or allele counts. Conflicts hold affected plots.
Plot thinning and y limits do not truncate the numerical result tables.

Typed supplied diagnostics retain the source summary and attribution; they are
not marked as JLinAlg-executed inference. Primary and sensitivity contexts enter
the finding dossiers with fixed scientific caveats. Neither these diagnostics
nor a lower lambda replace primary association statistics, exclude variants or
promote another cohort set. Original private source prose stays in local records.

Verified locally against `build/gwas-diagnostics-acceptance-20261025-r2/`:

- Frozen source: 1,946 tests in 360 suites;
  1,848 passed, 98 optional skips and zero
  failures/errors. The actual Gradle execution exit was zero. The full gate
  includes 44 diagnostic/import/scope tests,
  of which 13 exercise the new diagnostic contract.
- The unchanged pinned public verifier passed 18 scenarios,
  independently checking 3,890 QQ rows, 713 Manhattan
  rows and 6 fitted quantitative association rows
  with 9,332 numerical comparisons. It checked
  1,341 sealed operation artifacts, source hashes, cache
  reuse, bounded/invalid contracts, imported summaries and aggregate dossiers.
- 61 supplemental native/supplied network, scoped import,
  discovery/validation and frozen-family scenarios passed against the same
  executable. Source-family denominators and selected-only qualifications
  remain intact. Both schema copies matched, all 18 new fixture contracts
  passed schema checks, and the shared GO installation remained unchanged.
- Before/after packaged planning preserves an identical rare-set-only contract:
  the retained earlier build incorrectly requested SNP diagnostics and SNP
  enrichment; the final build retains rare-set work without those spurious
  requests. Neither planning proof executed an analysis. The earlier full gate
  passed but is retained as interim evidence, not final acceptance.
- Archive CRC and every compiled main class matched the frozen JAR. Source
  receipt: `078cb7d6e8213559c098d03e33e25b8e2aa705576f5eb166ab052d8ab4325cdc`.
- Packaged executable SHA-256: `9c4f70c73144510062569301e2a6cdf414224b11ae1f67445bf89f1312bc8c15`.

The failed early preflights (invalid verifier exit expectation and absent pooled
coordinate integration) remain retained. The final checkpoint uses a fresh,
unchanged verifier and exact frozen executable. No external requests, real AI
impact appraisal or biological validation occurred. This remains uncommitted,
unreleased source-build verification and partial pipeline conformance. Defaults
version 33 and implementation identity 49 invalidate incompatible earlier caches.

## Cross-cohort frozen-module preservation checkpoint, 2026-10-25

The [module-preservation guide](vignettes/study-pipeline-tutorial.md#cross-cohort-module-preservation)
documents the separate `network_preservation` operation. Native cohort graphs,
provided graphs and mixed sources retain distinct aliases and immutable
producers. Frozen reference memberships are evaluated in each target node
universe, without requiring a new target module match or refitting supplied
edges. Molecular comparisons have independent dependencies from pathway overlap.
Supplied primary preservation summaries satisfy the stage; sensitivities remain
separate and externally attributed. Explicit source rounding tolerances retain
the original numbers without replacing them with reconstructed precision.

Registered namespaces, source species/type/release/build, complete thresholded
signed-edge meaning and common estimators/thresholds control comparability.
Missing/partial inputs, unweighted graphs, incomplete reference membership and
bounded work retain dispositions and review needs. Source declarations are not
independently certified, and pooled graphs are not decomposed into constituents.
Shared cohorts are recorded without inferring independent replication.

Full tables report original/shared nodes, coverage, selected-edge counts,
retention, Jaccard, sign agreement and selected-weight cosine. Missing nodes are
not zero correlations; absence below a graph cutoff is not a measured zero raw
correlation. Undefined ratios remain NA, including empty/empty edge sets. These
descriptive metrics do not implement null-calibrated preservation tests or
WGCNA Zsummary, create association candidates, assign biological function or
select another primary cohort set/graph. Finding context distinguishes native
reference memberships from an external/study-level summary without membership
assignment and retains the full matrix as a source.

Verified locally against `build/network-preservation-acceptance-20261025-r2/`:

- Frozen source: 1,970 tests in 363 suites;
  1,867 passed, 103 optional skips, zero failures/errors
  and an actual Gradle exit of zero. The full gate includes 66 related tests,
  including 12 dedicated preservation tests.
- The unchanged public verifier passed 18 scenarios. It independently
  reconstructed 3 residual-correlation
  graphs, checked 28 module/target rows with
  150 numerical comparisons and verified
  1,556 sealed operation artifacts, original inputs and
  cached reuse. The incompatible-species meta source stayed outside official
  finding dossiers while the independent graph branch retained its limitation.
- 46 native/supplied network, scoped-import, generic supplied-result
  and GWAS-diagnostic regression scenarios passed against the same executable.
  Both schema copies matched and all 18 contracts passed schema validation.
  27 native/import Methods records and
  23 implementation/schema/verifier files were audited.
- A retained packaged failure demonstrates five conflicting review needs sharing
  one question key. The final identical raw contract now returns successfully
  with five stable distinct needs and unavailable numerical preservation.
  The fix also separates simultaneous GWAS-diagnostic review contracts.
- Explicit references now compute only their own missing memberships; separate
  target module/GO requests remain independent. Identical raw native inputs
  retain the same numerical comparisons with one reference module producer
  instead of an unnecessary target producer. On missing-target delivery, cached
  reference graphs and memberships survive producer receipt changes because
  complete sealed output maps bind them; changed graph outputs do not borrow
  old memberships. Original manifest hashes remain in the provenance.
- Archive CRC and all compiled main classes matched the frozen executable.
  The shared GO installation remained unchanged. Source receipt:
  `8fbe4fa0f3d1cb32dbcffe0469c121e552e7f85222fc583be3dccb9afa25da35`.
- Packaged executable SHA-256: `9cba3803a9c0736af06b8f337b81fb5e9f750bd43ed29a99be3229ac5dd14831`.

Early compile/fixture errors, the genuine pathway-dependency and question-key
defects, and a verifier assumption that incorrectly required full precision for
explicitly rounded source metrics remain retained as development evidence. Final
acceptance uses fresh unchanged source/verifier/executable pins. The public
workflow used offline synthetic inputs; no real AI appraisal or biological
validation occurred. Changes remain uncommitted and unreleased, with partial
pipeline conformance. Defaults version 34 and implementation identity 50 reject
incompatible earlier cached operations.

## Cross-mask set/gene omnibus checkpoint, 2026-10-25

The [cross-mask guide](vignettes/study-pipeline-tutorial.md#cross-mask-rare-set-and-gene-omnibus)
documents `rare_set_omnibus` and `analysis.rare_omnibus`. Registered components
combine through conservative Bonferroni minimum-P or optional equal-weight ACAT,
with the declared P=1 boundary fallback and finite natural-log tails. One source
primary test per mask is the default; all-test combination is explicit. Set IDs
stay sets unless reviewed mappings define a gene unit. Several sets can enter
one gene before its separate family correction. Original source results,
adjustments, component membership, producer receipts and scientific scopes
remain retained.

Missing components, failed models, ambiguous mappings and incomplete families
hold correction. An outcome-independent eligibility ledger records genuinely
ineligible components; it cannot remove a tested result or failed model. Empty
eligible sets do not become null P values. Native primary/discovery/cohort
families stay separate. Frozen discovery unit selection precedes validation
under the registered all-results or discovery-subset correction. Unsigned
omnibus results have no aggregate beta, SE, direction or heterogeneity estimate;
statistical repetition does not certify independent cohorts or causal biology.

Default supplemental selection preserves original mask-family ownership.
Explicit primary ownership routes rare-set enrichment, contextual literature
and finding eligibility through the omnibus, without a favorable-mask fallback.
Automatic mode proposes this stage for eligible rare sources. Manual mode
schedules an explicit omnibus goal or requested primary ownership; unrequested
supplemental work does not hold a completed rare-only analysis. Completed
external unit omnibus tables reuse their original q values and scoped families
without component refitting. Selected or incomplete exports cannot supply a
complete native enrichment background, even when their source q values are
available. GO remains exploratory and retains its variant-opportunity/callability
and gene-length limitations. Per-feature/per-scope native completion of arbitrary
external omnibus fragments is not implemented by whole-stage import reuse.

Verified locally against `build/rare-omnibus-acceptance-20261025-r8/`:

- The frozen full gate completed with actual exit zero: 2,016 tests
  across 368 suites, 1,910 passed and 106 optional
  skips, with zero failures/errors. All 80 focused tests across six suites also
  passed, including the original 14 rare-analysis tests.
- The unchanged packaged verifier passed 19 scenarios and
  229 independent numerical checks, including native binary
  source fitting, cohort-level sources, cross-mask/gene combination, ACAT
  underflow, GO selection/correction, frozen validation, completed external
  reuse, incomplete exports and missing-map resumption. It verified
  1,122 sealed operation artifacts and original input hashes.
- 29 rare-set downstream, rare-cohort overlap and generic supplied
  regression scenarios passed against the same executable. Both schema copies
  matched, all 19 raw main contracts validated, and
  19 native/import Methods records retained execution
  origin, effective parameters, software and applicable method references.
- A same-input before/after proof restores the supplied human species
  declaration through the native omnibus into GO while leaving the primary
  result table and source adjustments unchanged. A second proof holds GO for
  a selected export mislabeled complete while retaining the exact source q
  values. Source snapshots retain both earlier behaviors for review.
- All 1,755 compiled main classes matched the
  executable and archive CRC checks passed. 25
  implementation/test/schema/verifier files matched the frozen source. The
  shared GO installation remained unchanged. The source archive, full/focused
  test XMLs, manifests, original inputs and receipts are retained in the
  checkpoint bundle and linked regression bundles.
- Source receipt: `70ed992ee693616a1c5db022e380ad97bbc1dfd284631cec2087f14d3044eaec`.
  Executable SHA-256: `78dc5454857d9401827e33d02c265abe551c6f0995bf7fd0e6d38d61216437ca`.

The final source includes descriptive manuscript Methods records and exact
combination/family settings; it invents no justification for unusual choices.
Development failures and superseded interim bundles remain retained. Final
acceptance uses unchanged source, verifier and executable pins. These offline
synthetic checks establish software behavior, not real-data null calibration,
biological validity or actual AI appraisal. Broader rare-variant models,
cross-phenotype multiplicity, genome-wide scale and bias-aware enrichment remain
in progress. Changes are uncommitted and unreleased; full pipeline conformance
remains partial. Defaults version 35 and implementation identity 54 invalidate
incompatible interim caches.


## Principal-finding source review checkpoint, 2026-10-25

The [source-review guide](vignettes/study-pipeline-tutorial.md#claim-bound-principal-finding-discussion)
documents the local manuscript `finding_review` contract. Principal-finding
claims bind to current unexpanded prose and the exact frozen inventory hash.
Observed study results, source-reported experiments, inferences, hypotheses,
limitations and proposed experiments retain distinct epistemic labels. Each
literature reading names an exact source passage and a claim-local citation;
source study context and proposed experimental predictions remain explicit.
Unknown/cyclic dependencies, overlapping spans, invented passages and stale
source/prose/result bindings fail. Coverage checks require the entire prose
token, including a previously uncovered word-suffix edge case.

Eight prose-linked facets expose observation, primary evidence, mechanism,
contrary evidence, alternatives, relevance, next experiments and limitations.
The default review mode preserves incomplete drafts with needs; explicit strict
mode rejects mechanical gaps before output creation. The full-text primary
evidence requirement cannot be satisfied by a title/abstract. Even a complete
binding report retains `scientific_review_required`; declared relationships,
study context and facet coverage do not certify semantic support or scientific
depth. All independent manuscript reviews still apply.

Explicit `full_text: true` reference acquisition now retrieves available public
Europe PMC JATS with identity checks, bounded secure XML projection, license,
raw-response audit and separate availability. Metadata remains available when
full text fails. A real Wickenhagen et al. Science 2021 acquisition succeeded
after correcting the text transport's JSON-only Accept header. Caller-declared
texts stay distinct from verified native acquisition members. Revisions copy
source text and reference bundles; successor requests inherit sealed copies
despite changes to the original directories. These sources and automatic reviews
remain excluded from the existing editorial payload.

Verified locally against `build/finding-discussion-acceptance-20261025-r3/`:

- The frozen full `check executableJar` gate returned actual exit zero:
  2,044 tests in 370 suites, 1,937 passed,
  107 optional skips, zero failures/errors. The 4 directly
  relevant suites contain 54 passing tests.
- The packaged exercise passed 16 cases: six verified manuscript bundles
  and ten failures before output creation. It tests source/result/prose staleness,
  quote/citation/dependency/facet coverage, review/strict behavior and native/caller
  snapshot inheritance. A real primary paper supplies source text; numerical
  results and the OAS1-labelled feature are explicitly synthetic.
- Separate packaged impact, literature-selection and manuscript-depth regression
  workflows returned actual exit zero on the same frozen executable. They retain
  the three-finding protocol, 100-of-125 literature cap and complete supplementary
  text independently of the 8,000-word main target. Protocol fixtures are not real
  AI appraisal.
- Independent schema checks cover the native requests and paired published
  schemas. 1,761 compiled main classes match the packaged JAR;
  17 guarded source/test/schema/verifier files match the
  frozen receipt. The source archive, XML results, execution logs and hashes are
  retained. Final documentation/citation and bundle checks have separate receipts.

Executable SHA-256: `5e9fc2b08a444f39899863545b0b3397efafe95855676ab1dc96b8d513240b28`. Source receipt SHA-256:
`5646ba87b8b98db63f51e9267654bc96114629883dd4721b40edc346c1d9a402`. Defaults stay 35; implementation identity 56
invalidates earlier operation caches. Changes are uncommitted and unreleased.

This is verified mechanical source-grounding progress, not full pipeline
conformance. Actual-model comparison and scientifically thorough real
three-to-five-finding discussions, semantic/primary-source appraisal, complete
assay/source coverage and normalization of arbitrary supplied bibliographic
exports remain requirements. No new OAS1 finding, patient experiment, clinical
utility claim or automatic scientific approval was produced.


## Supplied literature and missing-query completion checkpoint, 2026-10-25

The [supplied-literature guide](vignettes/study-pipeline-tutorial.md#reuse-supplied-literature-and-complete-missing-searches)
documents the new typed bibliography/query input and study-schema contribution
route. Whole-stage primary imports now populate indexed evidence, the citation
catalog, bounded advisor previews and authored manuscript citations. A supplied
bibliography can provide references without claiming search coverage. Raw notes,
full supplied abstracts, source paths and extra provenance stay outside the
public citation whitelist. An explicit public-metadata declaration does not
authorize transmission or authenticate a source.

Literature contributions preserve complete source records and a registered input
hash, while native literature search executes missing queries. Exact public query,
strategy, gene/feature/phenotype and Methods/cohort/reference scope must match;
only Europe PMC complete all-hit exports can suppress a native request. Partial
exports, changed scopes/providers/hashes and conflicting source versions do not
silently become completed current searches. A partial primary import must use the
contribution route. Missing offline work stays pending. Catalog and feature
inventories distinguish source-reported searches/no matches from native retrieval.
Source dates and reuse rationales remain explicit; neither retrieval nor a
source-reported completion establishes scientific sufficiency or negative evidence.

Caller abstracts retain unauthenticated excerpt status. They cannot satisfy the
verified-retrieved-abstract passage route in principal-finding reviews. Later
native abstract occurrences replace those excerpts with coherent offsets and
audit, preserving both metadata origins. Planner, registry and MR loaders now
distinguish literature and MR contributions; literature imports cannot schedule
MR or enter its feature-completion family.

Verified locally against `build/supplied-literature-acceptance-20261025-r4/`:

- Frozen `check executableJar` returned actual exit zero: 2,074 tests
  across 373 suites, 1,967 passed, 107
  optional skips and zero failures/errors. The 6 directly
  relevant suites contain 120 passing tests, including the
  new normalization, scope/hash/citation/privacy and planner-separation cases.
- Ten packaged cases passed their expected completion/hold/failure checks. The
  authored manuscript cites a real supplied Wickenhagen bibliographic record
  without repeating its lookup. Seven exact declared searches reuse locally;
  partial/scope/provider/hash/ambiguous/invalid inputs retain their boundaries.
  The live completion case reused six searches and sent exactly one public dummy
  query, retaining its HTTP 200 response and raw audit. No LLM request was made.
- Exact raw input scopes, counts and source statuses were independently compared
  with reused records and the actual missing-query URL. Original files and the
  manuscript source ledger stayed unchanged. An editorial-payload check excludes
  private fixture notes, extra method/provenance fields and whole abstracts.
- Same-executable regressions passed for principal-finding source review, all
  seventeen MR-completion cases and existing supplied-stage/missing-GO reuse.
  Existing MR source adjustments and its separate joint family remain verified.
- Ten study fixtures, nine valid bibliography fixtures and one expected
  acknowledgement-schema rejection were checked against paired schemas.
  1,774 compiled main classes match the JAR; 22
  guarded source/test/schema/verifier files match the frozen receipt. Source
  archive, test XML, execution logs and documentation checks have separate receipts.

Executable SHA-256: `558675814a4b6b107efd60c8d08525a792ccbfe8181fff37140b9079b410a6b4`. Source receipt SHA-256:
`09c46db95e92d10f0a5d52e8423f245b9dab5ecb22fffae45fee0271a90d25cf`. Defaults remain 35; implementation identity 58
invalidates incompatible earlier execution caches. An interim source run was
stopped to correct an MR-only shared registry description; final authority is
the source-specific bundle above. Changes remain uncommitted and unreleased.

The numerical inputs and external search declarations are explicitly synthetic;
the OAS1 reference exercises bibliography reuse and does not support these dummy
findings. This checkpoint does not establish source authenticity, actual-model
scientific appraisal or a thorough real manuscript. General bibliography-format
adapters, other-provider query reuse, source/search sufficiency appraisal and the
remaining full-pipeline scientific/modality requirements remain in progress.


## 2026-10-25 checkpoint: declared human chromosome and ploidy contrasts

The [human-ploidy tutorial](vignettes/study-pipeline-tutorial.md#declared-human-chromosome-and-ploidy-contrasts)
now documents an opt-in native route for one independent OLS/GLM human chromosome
and declared-complement estimand per hypothesis. GRCh37/38 autosome, PAR, XX X,
XY X and XY Y contrasts select participants and physical dosages before MAC/MAF,
missingness and fitting. This is a bounded addition to the in-progress GWAS row;
the full pipeline objective remains incomplete.

The default MAC 20 rule uses physical called alleles among the actual analyzed
participants. Explicit source-scale conversion, invalid-call error/missing
policies, unknown-complement error/exclusion, X-only PAR representation and local
declaration/call/marker receipts prevent silent doubled haploid counts or
structurally absent Y calls from entering the denominator. Requested diploid HWE
is available for supported PAR/XX X hard calls and rejected for haploid contrasts.
No biological sex or karyotype is inferred or certified.

Final authority is `build/human-ploidy-acceptance-20261025-r5`, using the executable
from `build/human-ploidy-source-20261025-r2`. Its evidence includes:

- The full frozen `check executableJar` gate passed: 2,099 tests across
  377 suites, 1,987 executed successfully,
  112 optional skips and no failures/errors. The seven relevant
  genotype, Firth/exact, prefit and ploidy suites contain 50 tests, including
  sixteen new ploidy tests. Website validation passed separately within the gate.
- Twenty-one packaged acceptance cases passed. Five separately registered study
  contrasts completed all 25 planned operations and passed
  study provenance verification. Independent NumPy/SciPy calculations checked
  physical AC/AN/MAC and OLS beta, SE, statistic and P. A simple finite conditional
  case matched an independent hypergeometric probability-ordering calculation.
  Observed and explicitly converted binary score/Firth/conditional routes agreed.
- MAC 19/20, loss of a carrier through covariate exclusion, major/minor allele
  flips, duplicated Y-PAR records, both reference builds, invalid hard-call and
  source-scale cases, unsupported/unknown complements and haploid HWE refusal
  retain their distinct dispositions. Source files remained unchanged. The
  optional dataset stage also retains its processed-matrix default during native
  ploidy validation; malformed type metadata yields a validation error.
- The same executable passed all fifteen existing packaged GWAS QC regression
  cases and 1,019 independent value checks. Paired schemas and the five-contrast
  native JSON study fixture were validated. Public GRC assembly reports were
  retained with retrieval URLs and hashes to verify X/Y lengths; PAR intervals
  use the separately cited GRC tables. No LLM or network request was made by
  these analysis acceptance runs.
- 1,792 compiled main classes match the executable;
  26 guarded implementation/test/schema/verifier files
  match the frozen receipt. Source archive, test XML, raw execution receipts,
  primary reference receipts and local participant/call audits are retained.

Executable SHA-256: `027ff9655944725044815c49d82aca688718f42e05234e95c5474ea4bd8e95e1`. Source receipt SHA-256:
`f34de7b56c5f76e1bfbed67630d4880b8d1ee55f54b637f863ff21707fbc1339`. Defaults remain 35; implementation identity 59
invalidates incompatible earlier execution caches. These changes remain local,
uncommitted and unreleased. Earlier invocation drafts and the pre-default-stage
snapshot are intermediate evidence, not the final source authority.

Pooled XX/XY X-inactivation or dosage-compensation models, chromosome-specific
interaction/mixed/survival models, aneuploid/mitochondrial models and native
rare-set ploidy scores remain unsupported by this route. Native rare-set score
generation explicitly waits for a validated model. Resume and conditional-score
export are also refused. Separate contrasts are not independent replication
cohorts and are not automatically pooled. Wider source-format/scale, combined
sample-QC/PCA, and real scientific/model validation remain in progress. Declared
complements, plausible numerical estimates and passing software tests do not
establish source correctness or scientific validity.


## 2026-10-25 checkpoint: cross-hypothesis primary MR reporting families

The [cross-hypothesis MR tutorial](vignettes/study-pipeline-tutorial.md#declared-cross-hypothesis-primary-mr-families)
documents `project.mr_testing_families`. One registered node joins complete primary
MR testing families across declared hypotheses before finding inventory,
AI prioritization and member reports. This is a reporting/testing layer; it does
not pool causal effects or change association eligibility, model fits or source
corrections. Other cross-phenotype analysis families and scientific source
appraisal remain in progress.

Native, complete supplied and natively completed contribution-based MR sources
retain original raw P, adjusted P/method/family sizes and identities. Definitive
non-estimable tests reserve denominator positions without acquiring P values.
Sealed zero-eligible receipts are distinct from missing producers. Selected-only,
count-incomplete, ambiguous, unresolved and unpromoted sources cannot establish
joint discoveries; the official joint denominator and adjusted P values stay
unavailable until membership is complete. Source-declared counts and observed
rows remain distinct in coverage receipts, including truncated exports.

BY is the default; BH, Bonferroni and explicit nominal NONE remain named policies
with different error-control assumptions. Dossiers preserve source significance
and separate joint evidence. MR-only primary findings use joint eligibility;
association findings retain their association meaning. Failure records remain
indexable even when a family producer has no promoted summary. Methods, evidence
index and manuscript instructions retain the new scope and limitations.

Final authority is `build/mr-cross-family-acceptance-20261025-r4`, using
`build/mr-cross-family-source-20261025-r4`:

- Frozen `check executableJar` passed 2,115 tests across 378
  suites: 2,001 executed successfully, 114 optional
  skips and no failures/errors. Fourteen new tests cover correction, coverage,
  denominator/identity conflicts, external/native format boundaries, data
  integrity, source/joint candidate decisions, planning and failed-producer indexing.
- All eleven packaged cases passed: 8 complete and
  3 waiting joint families. They cover BH/BY/Bonferroni/NONE,
  missing sources, complete/partial/truncated supplied stages, two-phenotype
  native MR, zero eligibility, evidence context and completed MR without original
  raw inputs. Independent SciPy correction and 144
  native Wald/IVW value checks passed. Provenance verification and cache replay
  distinguish stable completed MR from consumers of resumed incomplete producers.
- The same executable passed all 17 existing MR
  completion cases and 140 independent estimate,
  P-value and within-hypothesis joint-BY checks. Original provided features were
  not refitted, source adjustments remained intact and invalid supplied coverage
  did not fall back to native computation.
- Paired schemas and eleven native JSON study fixtures passed schema validation.
  All 1,796 compiled main classes match the JAR;
  18 guarded code/test/schema/verifier files match the
  frozen source. Source archive, full test XML, raw execution receipts and source
  provenance are retained. Analysis fixtures sent no network or LLM requests.

Executable SHA-256: `587c0d5fc4a48e62e7887825b95d27f82ba5d6513ce9744b4b1b7d8da75cc2db`. Source receipt SHA-256:
`35bda191e007b83a10178b1360991c5534a02f0595f202bcc6adf43f6ebb8d8a`. Defaults remain 35; implementation cache identity
60 invalidates incompatible earlier operation caches. Changes are uncommitted
and unreleased. Source1's earlier full gate was a baseline; source2/source3 were
unexecuted intermediate candidates. Early verifier expectations about diagnostic
cache reuse were corrected before final acceptance. Final authority includes
the source-count and failed-summary handling fixes above.

These software checks do not certify scientific completeness of external
sources, valid instruments, input P-value calibration, causal identification or
semantic AI appraisal. Association-based source selection and declared feature
budgets remain part of the scientific limitations. Final selection of a few
stories has no separate FDR guarantee. The full pipeline objective remains
incomplete.

## Native preparation resume scope, 2026-10-25

The source adds explicit input/context contracts for native intake, participant
QC and association. Downstream genetic source arrival, reporting policy changes
and separate hypothesis amendments can retain verified completed preparation.
Scientific input/model/QC/dependency changes still invalidate consumers. All
other stages and unregistered executors retain conservative full-study scope.

Automatic execution defers unavailable dataset files to their consuming stages;
explicit `validate --check-files` remains strict. Run-level availability receipts
are separate from operation content hashes. Reused manifests retain original
execution and current resolved-study hashes. Prior attempts remain immutable.

The [resume guide](vignettes/study-pipeline-tutorial.md#resume-preparation-while-downstream-inputs-arrive)
and offline packaged example describe the contract and its limits. Final authority
is `build/stage-resume-acceptance-20261025-r1`, using frozen source
`build/stage-resume-source-20261025-r1`:

- Frozen `check executableJar` passed 2,125 tests across 380
  suites: 2,010 executed successfully, 115 optional
  skips and no failures/errors. Nine new scope/resume tests passed.
- Seven packaged runs verified missing/completed MR, preparation reuse across
  reporting and separate-hypothesis amendments, association-setting and phenotype
  invalidation, forced rerun parity and unchanged prior attempts. Independent
  calculations checked 48 OLS values and
  96 IVW/MR correction values.
- The same executable passed three existing supplied-result cases: six supplied
  stages reused, missing native GO completed, supplied FDR/testing denominators
  preserved and selected-only exports retained without invented discoveries.
- Paired schemas and four JSON study fixtures passed validation. All
  1,799 compiled main classes match the executable;
  10 guarded implementation/test/verifier files match
  frozen source. Source archives, full test XML and raw execution receipts are
  retained. The synthetic analysis fixtures sent no network or LLM requests.

Executable SHA-256: `4f3e586b9052a4126f6a987ab5ce7fd9f5f9b937a14039c5a357d79e0e50ef55`. Source receipt SHA-256:
`d9daba6bd75ccf813e0a2be8763490b69918482e5b5364312cbe35e5c0ceac1d`. Defaults remain 35; cache identity 61 prevents reuse
across earlier implementations. Changes are uncommitted and unreleased. The
first packaged iteration correctly held undeclared synthetic feature locations;
the final verifier uses explicit coordinate metadata. The focused test fixture
counts and CLI syntax were corrected before frozen acceptance.

This checkpoint does not extend stage-specific scopes to every downstream
adapter, guarantee minimal recomputation for arbitrary plan edits, certify
scientific source validity or establish full analysis-pipeline conformance. Data
dictionaries required to resolve the contract and integrity failures remain
global gates. Operation-ID/dependency changes can force recomputation. The
overall implementation goal remains incomplete.

## Conditional frozen-network label-null tests, 2026-10-25

The source adds an opt-in `network_preservation_null` stage with native graph and
frozen membership prerequisites, signed/unsigned edge overlap, optional explicit
node strata, whole-group exact enumeration or fixed independent plus-one Monte
Carlo tails, precision intervals and separate complete-family correction.
Definitive non-estimable tests reserve denominator positions; unresolved sources,
strata or complete-matrix work bounds withhold corrected discoveries. Supplied
conditional results and supplied descriptive/missing-inference routes retain
external attribution. The stage does not refit supplied graphs or change
association candidates, primary cohorts/graphs or GO interpretation.

The [conditional graph guide](vignettes/study-pipeline-tutorial.md#conditional-tests-of-frozen-network-wiring)
specifies the null, statistics, numerical and scientific limits. These are
conditional competitive topology tests, not participant resampling, WGCNA
Zsummary or a guarantee of scientific preservation. Rationale text stays local;
the aggregate context retains methods, family status and source caveats.

Final authority is `build/network-null-acceptance-20261025-r2`, using frozen
source `build/network-null-source-20261025-r2`:

- Frozen `check executableJar` passed 2,141 tests across 382
  suites: 2,026 executed successfully, 115 optional
  skips and no failures/errors. All 16 new numerical/stage tests passed. They
  include complete small-graph null enumeration, super-uniform exact tails over
  equiprobable target graphs, seed/stratum/count/precision and scope contracts.
- The unchanged pinned final verifier passed 18 cases: 13
  complete, 4 held prerequisites/bounds and
  1 deliberately invalid supplied-count export. Its
  117 independent numerical comparisons cover full
  Python permutation histograms, sampled precision via SciPy beta quantiles,
  signed/unsigned tests and all correction policies. Missing/revised strata,
  completed graph reuse, typed provided nulls and supplied-descriptive/missing-
  inference routes preserve prior attempts and association candidates.
- The same executable passed all 18 existing descriptive
  preservation cases, with 150 numerical comparisons over
  28 rows. Source graph/module provenance and original statistics
  remain intact. Fixtures sent no LLM or network requests; unsent aggregate and
  Methods evidence excludes private rationale/source sentinels and retains the
  conditional statistic, correction, alpha, family status and mandatory caveat.
- Two additional synthetic positive controls use deliberately nominal alpha 0.5
  to exercise positive native/imported reporting. They confirm unchanged
  association candidates, exact source correction reuse and explicit nominal
  scope in actual unsent AI packets. They make no biological significance claim.
- Paired schemas and 38 JSON study contracts passed validation.
  All 1,817 compiled main classes match the executable;
  21 guarded implementation/test/schema/verifier files
  match frozen source. Raw receipts, full test XML, dependency and source archive
  are retained separately from later documentation overlays.

Executable SHA-256: `2e287ae111bb307194fa2d7cf5dacf19ccccd277da86aa0c64b3e45f33d38563`. Source receipt SHA-256:
`100c5c8091fcabec0470bc6f3aa3d1d7e25e7b313bda62e972951c5e06bb3023`. Defaults36/cache62 invalidate incompatible prior
implementations. Changes remain uncommitted and unreleased. Source1 was an
earlier frozen baseline; Source2 adds explicit correction/alpha context and the
nominal-NONE caveat. The early compound-variable compile issue, missing fixture
family ID and verifier observation-key lookup were corrected before final
acceptance; intermediate results remain historical evidence.

Scientific exchangeability, source identity, participant independence, real-
network calibration, sampling uncertainty and biological interpretation are
not certified by these software checks. This conditional null does not replace
broader preservation/stability models, primary analysis or the full manuscript
acceptance matrix. The full analysis-pipeline implementation goal remains
incomplete.

## External population-frequency mask eligibility, 2026-10-25

An opt-in `rare_variants.external_frequency` policy now freezes a versioned
external count panel before association testing. Native masks use the maximum
ALT AC/AN across explicit populations with per-mask thresholds and a prespecified
minimum AN. Exact allele/build identity, source QC, full candidate coverage,
missing-resource requests, immutable source snapshots/decisions and actual
resource-byte cache identities are integrated. Existing observed cohort MAF and
single-marker MAC20 remain separate. Supplied score/covariance results can enter
the missing mask/pool stages; P-only set results cannot be refiltered. Supplied
completed primary analyses retain upstream attribution.

The [guide](vignettes/study-pipeline-tutorial.md#external-population-frequency-eligibility-for-rare-masks)
documents the schema, source table, coverage/exclusion rules and an offline
synthetic CLI verifier. Primary references explain ALT-specific masks and why
unobserved variants require callable-site allele numbers. This does not execute
a gnomAD download/conversion, infer participant ancestry or source fitness,
perform a FAF/confidence-bound screen or certify real scientific conclusions.

Final authority is `build/rare-frequency-acceptance-20261025-r1`, using frozen
source `build/rare-frequency-source-20261025-r1`:

- Frozen `check executableJar` passed 2,172 tests across 385
  suites: 2,057 executed successfully, 115 optional
  skips and no failures/errors. All 13 new tests passed, including
  exact allele/build/count, population/coverage, per-mask boundary, source-QC,
  resource-identity, P-only and empty-universe contracts. Existing quantitative,
  conditional-binary, supplied-result and empty-mask regressions also passed.
- The pinned verifier passed 18 cases: 9 completed, 7 held prerequisites/bounds
  and 2 deliberately malformed count/build sources. Its 70 independent checks
  reconstruct allele eligibility from counts and burden estimates/SE/P from
  score/covariance exports. Missing-frequency completion and same-path source
  revision retain input editions, immutable old attempts and reusable core fits.
  Compatible score imports reproduce the native result table. A completed
  supplied primary analysis skips native masks/scores and retains external
  attribution; an empty eligible universe performs no model fit or association.
- The same frozen JAR passed the prior native and unsigned rare-set packaged
  workflows, including discovery/validation, all registered tests, cohort
  influence and complete-family BH checks. This is local synthetic acceptance;
  no real gnomAD download, population appraisal, patient-data analysis or LLM
  invocation was performed.
- 1,824 compiled main classes matched the executable;
  17 guarded source/contract files matched the frozen receipt;
  paired schemas and 20 input editions/legacy configurations
  validated. The bundle retains exact source, dependency, XML, executable,
  verifiers, source editions and execution receipts. Final documentation overlays
  are separately rendered/linked and website checked before sealing.

Source receipt SHA-256: `dd813b4ffa822529572654e8a446b753b47bb8b20d168790843fb15834f9f657`. Executable:
`9ffbd9d68666fe6bc1d6199b11160b899898f3e161b6dc4397fbfdfa970b222b`. Verifier: `bfbccd087ef3f2928765622d9ac42976e363af04b18d09067d576a60d547fcf4`.
Defaults37/cache63 prevent incompatible older cache reuse. Changes remain
uncommitted and unreleased; the overall analysis-pipeline objective is incomplete.

## Native Cox study association and efficient PH diagnostics, 2026-10-25

The study adapter now carries an explicit survival endpoint through prefit QC,
full per-feature independent Cox fits and aggregate results. Molecular matrices
and human-autosome genotypes have registered time/event/entry/strata, codes,
offsets, tie/test choice, event counts, MAC20 where applicable, resource bounds,
unresolved-family holds, standard normalization/QC and log-HR downstream pooling.
Compatible provided statistics skip native participant refits. Separate PH
families/context and an explicit offline finding-inventory goal are integrated.
Known typed log-HR/log-odds/linear scale conflicts are held before pooling.

The PH kernel now profiles fitted baseline coefficients through efficient
information. Independent R coxph/cox.zph(log) fixtures cover censoring/entry,
strata, offsets and both tie methods; a genuine zero-coefficient null and a
strict zero-information-ridge contract are implemented. Singular/degenerate PH
information remains unavailable. The [guide](vignettes/study-pipeline-tutorial.md#registered-native-cox-survival-association)
records exact numerical/scientific boundaries and the executable example.

Final authority is `build/survival-acceptance-20261025-r2`, using frozen
source `build/survival-source-20261025-r2`:

- Frozen `check executableJar` passed 2,227 tests in 393 suites:
  2,112 executed passes, 115 optional skips, no
  failures/errors. All 24 new tests passed, including native
  molecular/autosome Cox, explicit endpoint/codes and mappings, zero-coefficient
  and absorbed-covariate nulls, strict zero ridge, missing-model review, bounds,
  whole-family fit failure, separate PH context and typed pooling/count contracts.
- The exact-JAR public workflow passed 18 cases: 9 completed and 9 deliberate
  prerequisite, fit, source-scale, resource or unsent-AI holds. Its 401 independent
  NumPy/SciPy comparisons reconstruct full/null partial likelihoods, coefficients,
  observed information/SE, LRT/Wald tails, profiled PH diagnostics and fixed log-HR
  pooling. A predictor with missing data independently exercises mean imputation;
  phenotype exclusion moves a genotype from MAC20 to MAC19 before filtering.
  The nominal PH positive control stays nonprimary, leaves association candidates
  unchanged and retains its scope in an actual unsent assistance packet without
  the fixture's private participant marker. No LLM request was sent or attempted.
- Compatible supplied cohort statistics bypass native fits. A synthetic explicit
  amendment completes a missing endpoint definition. Unchanged replay reuses
  7 core stages with identical cache/output hashes;
  3,297 recorded output hashes were verified.
  The previous stage-resume workflow also passed all 7 runs,
  48 independent OLS comparisons, deferred MR,
  scientific invalidation, fresh-rerun parity and immutable old-attempt checks.
- A fresh local R4.6.1/survival3.8.6 execution of the frozen generator reproduced
  every byte of the tracked input, expected-result and version files. Java
  fixtures match `coxph` and `cox.zph(transform="log",terms=FALSE)` for right
  censoring, delayed entry, strata, fixed offsets and Efron/Breslow ties. This
  is fixed synthetic numerical acceptance; it does not establish independent
  censoring, PH, rare-event tail calibration or biological validity.
- 1,848 compiled main classes matched the executable;
  29 guarded source/contract files matched the frozen receipt;
  paired schemas and 19 input configurations validated. Exact
  source, dependency, XML, executable, verifier, input editions, R regeneration
  and execution receipts are retained. Final documentation overlays are rendered,
  link-checked and website-checked separately before sealing.

Source receipt SHA-256: `47f38ea762e63e1ea342ff3fcf7459202f6fc77c677a59cfdb3bb505b3aba141`. Executable:
`999a3af0e7a87a11a4305c4ce16e3080255dbdf43b95458363aed5f42aac4e30`. Verifier: `1181d1c16dee62b629a4fbed483336d5782e83bd7f40d1527ec5201a180d049d`.
Defaults38/cache64 prevent incompatible earlier cache reuse. No actual participant
study or AI invocation is claimed. Dedicated monotone-likelihood/infinite-estimate
diagnostics were still future work at this checkpoint; the later finite-MPLE
checkpoint below implements those checks. Mixed/frailty, recurrent/time-varying/
competing risks, sparse-event calibration, genome-wide performance and survival score
development remain in the unfinished overall goal. Changes are uncommitted and
unreleased.

## Finite Cox maximum certificates, 2026-10-25

The native signed study adapter now requires a risk-contrast certificate for the
nuisance null and every eligible full model. Positive full-rank balanced weights
prove a finite unique maximum; an exact separating direction proves monotone
likelihood. Bounded NNLS proposes certificates, then original binary-double
contrasts undergo exact rational sign/positivity/balance checks. Ambiguity and
resource exhaustion hold the complete family. Pair visits/work share one cohort
budget; proofs stay local while typed status/hash and aggregate counts reach
downstream context. Provided analyses retain their upstream attribution.

The extreme-unit acceptance fixture also exposed and corrected descriptive
Welch variance overflow, effect-correlation moment overflow/underflow, and a
cohort scatter-axis rendering loop. Welch now uses common-unit scaling and
variance fractions; effect moments use scaled Welford updates. Scatter display
coordinates use an explicit common divisor with labelled units while source
exports remain in their original units. A retained live thread dump identified
the rendering issue after all native Cox fits had completed.

Final authority is `build/cox-likelihood-acceptance-20261025-r2`, using frozen
source `build/cox-likelihood-source-20261025-r2`:

- Frozen `check executableJar` completed 2,259 tests across 397
  suites: 2,130 executed passes, 129 optional skips,
  no failures/errors. All 15 new tests passed. The library tests
  include 80 independent HiGHS LP cases, hand-checked risk sets, joint separation,
  rank/resource limits and a counterexample where ordinary Newton convergence
  coexists with an infinite coefficient. Native tests retain whole-family holds,
  shared resource accounting and the nuisance-null gate. Extreme-unit Welch,
  effect moments and unavailable constant-effect audit records are covered.
- The exact-JAR new workflow passed 11 cases: 4 completed and 7 deliberate
  scientific/resource or unsent-AI holds. Its 142
  independent comparisons include coefficient/SE/P and PH parity after changes
  of units. 73 independent risk-cone LPs and
  23,573 exact Fraction witness-contrast checks passed.
  12 separate-JVM proof/standard-table hashes
  matched for identical native inputs. Supplied cohort statistics bypass new
  native fits and certificates; their attribution is retained. The actual unsent
  packet contains aggregate existence context and excludes local proof vectors,
  row indices and the synthetic private marker; no request was attempted or sent.
- The same frozen executable passed the prior 18-case survival workflow with
  401 independent numerical checks and the seven-run resume/MR workflow with
  48 independent OLS checks, deferred MR completion, scientific invalidation,
  fresh-rerun parity and old-attempt immutability. 14
  additional native core cache checks and 5,752
  recorded output hashes passed. A fresh independent reference generation
  reproduced all 80 input/LP expected cases and provenance bytes using the
  recorded NumPy/SciPy versions.
- 1,871 compiled main classes matched the executable;
  29 guarded source/contract files matched the frozen receipt;
  paired schemas and 30 input editions/configurations validated.
  The bundle retains source, dependency, XML, executable, pinned verifiers,
  original inputs/editions, local certificates, regenerated references, receipts
  and the interim four-JVM serialization probe. The earlier source candidate is
  retained separately; it was superseded after cross-process YAML key ordering
  was found to vary proof hashes. Final documentation overlays are rendered,
  linked and website checked separately before sealing.

Source receipt SHA-256: `bc27ebfcf5e9e22e3a9217cf0af1384b65fac24c43dddf47a4a436e200137a13`. Executable:
`6aed2839acdb37bcf9c1fe4c22fd81709f2f4a9b09f58a406d8d29dd72779e59`. Verifier: `c2d66dd4000a99da1739633df37fb51c0d17672bc7c3425febe54d0caa0b4aaa`.
Defaults39/implementation65 prevent stale native cache reuse. These are exact
mathematical coefficient-existence checks on source binary doubles and local
synthetic numerical/workflow acceptance. They do not establish sparse-event tail
calibration, censoring/PH assumptions, biological validity, penalized Cox inference
or genome-wide performance. The guide distinguishes separately justified
score/LRT P-only analyses. Changes are uncommitted and unreleased; the full
analysis-pipeline objective remains incomplete.

## Adaptive Cox and standalone score workflows, 2026-10-25

Native signed studies and prepared-null score scans now use bounded sufficient
participant supports for finite-MPLE proofs. Subset monotonicity never suffices:
an exact full-risk entry/exit sweep must verify the direction on every original
event risk set. Native local proof schema 2 records its supporting source rows
and scope, shared budgets span all attempts/features, and aggregate AI context
continues to exclude local witnesses. Defaults40/implementation66 prevent stale
native cache reuse.

Standalone Cox now accepts numeric assay matrices and zero-covariate nulls,
retains factor reference coding while removing the baseline-absorbed intercept,
requires explicit 0/1 events, and supports fully observed cohorts. The coefficient
command rejects uncertified independent full models and numerical nonconvergence.
Prepared scans normalize geometry and return source-unit efficient U/covariance
and one-step effect/SE, with manifest method/estimator/certificate-scope fields.
An infinite alternative MLE does not preclude the distinct score route.

Final authority is `build/cox-adaptive-acceptance-20261025-r1`, using frozen
source `build/cox-adaptive-source-20261025-r1`:

- Frozen `check executableJar` passed 2,272 tests across 399
  suites: 2,143 executed passes and 129 optional
  skips, with no failures/errors. All 13 added tests passed.
  The existing 80 independent HiGHS cases now agree with both exhaustive and
  adaptive checks. New library cases cover a 30,000-row sufficient-support
  certificate, exact 10,000-row full-risk monotonicity, refutation of an
  unrepresentative subset direction, entry/strata/tied failures, bounds,
  zero-covariate scans and source-unit invariance/representability. Existing
  actual-R-generated conditional U/covariance fixtures still agree.
- The exact-JAR standalone verifier passed 13 cases: 10 successful commands and
  three deliberate endpoint/null/full-coefficient rejections, with 78 independent
  NumPy/SciPy coefficient, score, information and covariance comparisons. It
  retains numeric-assay and genotype inputs, original commands, logs, method
  manifests and MAC20 exclusions. A monotone alternative is accepted by the
  distinct score route while a monotone nuisance or full coefficient is rejected.
- The updated native verifier passed 11 cases (4 completed, 7 deliberate holds),
  142 independent numerical comparisons, 75
  full-model and 54 supporting-contrast LPs,
  23,573 exact Fraction witness contrasts and 12
  separate-JVM certificate/table hash checks. It checks schema 2 scope, actual
  supporting row membership, source pair mapping, full-risk direction flags,
  shared budgets and the absence of local support indices/witnesses from the
  unsent aggregate packet. Supplied results bypass new participant fits/proofs.
- The same executable passed the prior 18-case survival workflow with 401
  independent numerical comparisons and the seven-run resume/MR workflow with
  48 independent OLS comparisons, deferred MR completion, upstream cache reuse,
  scientific invalidation, fresh-rerun parity and old-attempt immutability.
  14 additional native core-cache checks and
  5,752 recorded output hashes passed.
  Fresh reference generation reproduced the 80 LP inputs, expectations and
  provenance bytes using recorded NumPy/SciPy versions.
- 1,876 compiled main classes matched the executable;
  38 guarded source/contract files matched the snapshot;
  paired schemas and 30 input configurations validated.
  Frozen source, dependency, executable, full XML, verifier scripts, input
  editions, local proofs and independent regeneration are retained. Final
  documentation overlays are rendered/link-checked/website-checked separately
  before sealing. The failed interim verifier (scientific-unit deserialization)
  remains at its original path; it made no false passing claim.

Source receipt SHA-256: `3da73a7b2dfbaa35c055546a6923b083fe6b6a5b7927731b5e55cd341f53e920`. Executable:
`2539141297ddaa479831bf2207d4c663d42a1fa963aecdf5a84c1c5ce32ecede`. Score verifier: `856bc1a1997f4112ae016f089f24c686df755e03eeca5e5eafb5676ead2fcd9b`.
Defaults40/implementation66 record the changed native proof contract.
See the [guide](vignettes/study-pipeline-tutorial.md#adaptive-cox-proofs-and-prepared-null-score-scans).
This is finite-coefficient protection and numerical/workflow verification on
generated inputs. Sparse-event calibration, scientific assumptions, related
survival scans and genome-wide performance remain outside this checkpoint.
The overall pseudocode implementation remains incomplete, uncommitted and
unreleased.

## Grouped nested score validation, 2026-10-25

Native quantitative and binary risk-score development now support grouped
nested validation inside the registered training+tuning pool. Each inner/outer
fit relearns local covariate encoding, feature imputation/scaling and penalties;
final selection/refit excludes the separately registered evaluation population.
Generated or explicit fold assignments keep complete independence units together.
Losses pool rows, while fold dependence precludes a naive outer-fold CI. Invalid
folds, unseen categories, numerical failures or resource bounds hold the complete
stage and retain local audit prefixes. Source input remains immutable.

Complete local membership, per-inner losses, outer models/predictions, aggregate
development loss and a final frozen model connect to the existing independent
evaluation/related-trait workflow. Frozen reuse retains bounded source selection
method/hash attribution without rerunning validation. Finding dossiers contain
aggregate context rather than local identities, predictions or model vectors.
Defaults42/implementation68 invalidate incompatible cached native score work,
including the superseded candidate's subnormal-loss behavior.

Final authority is `build/nested-score-acceptance-20261025-r3`, using frozen
source `build/nested-score-source-20261025-r3`:

- Frozen `check executableJar` passed 2,324 tests across 404
  suites: 2,195 executed passes, 129 optional skips,
  no failures/errors. All 11 added tests passed. Generated/explicit group folds,
  row-order stability, external/outer-holdout isolation, per-fit categorical and
  predictor preprocessing, frozen reuse, shared optimizer work, incomplete folds,
  aggregate-only finding context and two squared-loss boundaries are covered.
- The exact-JAR nested verifier passed 14 runs: 7 completed and 7 deliberate
  scientific/resource holds. Its 1,680 independent comparisons refit every
  quantitative ridge and binary logistic inner/outer/final model, loss and
  prediction with NumPy/SciPy, including final independent evaluation metrics.
  Ten external-data isolation hashes, native replay and two complete frozen-model
  reuse cases passed. The source tables retain their original hashes; no external
  or LLM request occurred in these generated workflows.
- The same executable passed the prior 10-case quantitative score workflow
  (independent baseline/imputation/ridge/tuning, clustered metrics, related-family
  correction, matrix input, external weights/alleles, probability/raw scores,
  supplied performance and missing related work), plus 13
  binary cases with 201 independent checks. Native joint
  elastic net, ridge/lasso, refit, frozen source/identity reuse, point/metric
  agreement and deliberate relatedness/class/iteration/work holds still pass.
- Cox prepared scores passed 13 cases/78 comparisons; native Cox proofs passed
  11 cases/142 numerical comparisons, 75 full and 54 supporting-cone LPs,
  23,573 exact witness contrasts and 12 cross-process proof/table hashes. The
  earlier 18-case survival workflow passed 401 comparisons. Seven resume/MR runs
  passed 48 independent OLS checks, missing-MR completion, upstream cache reuse,
  scientific invalidation, fresh-rerun parity and old-attempt immutability.
- 1,896 compiled main classes matched the executable;
  19 guarded source/contract files matched the snapshot.
  Paired schemas and 73 input configurations validated;
  7,524 recorded output hashes and an additional native
  nested-stage replay/cache comparison passed. Frozen source/dependency, full XML,
  executable, pinned verifiers, inputs, local fold audits/models/predictions and
  exact prior counterexample are retained. Final documentation is rendered,
  link-checked and website-checked separately before sealing.

The earlier SourceR2 passed its then-existing full suite, but a subsequent
exact-Fraction probe proved that 14 nonzero original residuals among 96 rows
produced a false zero nested MSE after normalization underflow. SourceR3 tests
source residuals before scaling, holds before publishing the metric/model and
invalidates older caches with defaults42/implementation68. The superseded
source/full/JAR/XML/input/output evidence and exact positive rational MSE remain
retained. SourceR1 was superseded earlier for missing mandatory citation metadata;
no full job was launched for that documentation-invalid snapshot.

Source receipt SHA-256: `783d41280f706e8b7099ba5f4308757847f96acb3de9937faaa17b37971a3504`. Executable:
`b51d6e7026367b0393e4513e0ae82e64292b0d34f3bb0784c47d13685c7eab5d`. Nested verifier: `289a3cc092f97e808a13b939fa56df0b4c391b06b8dfb797f7dab4659f895681`. This is native numerical/workflow
development on generated inputs; biological generalization, upstream leakage,
survival development, genome-wide LD-aware scores and broader validation remain
outside this checkpoint. The full pseudocode objective remains active; changes
are uncommitted and unreleased. See the
[guide](vignettes/study-pipeline-tutorial.md#grouped-nested-development-validation-for-risk-scores).


## Frozen genetic score application, 2026-10-25

The study weight-application branch now streams complete frozen additive human
autosomal BED models into the common held-out evaluator and related-phenotype
family. It reuses native BED/diploid score libraries, supports exact genomic
keys independent of aliases, effect-allele reversal, explicit builds/forward
strand, external-frequency imputation, source-unit normalization and identity or
logit frozen prediction. Complete model coverage is required in every source;
no target MAC cutoff, strand inference, model shrinkage, target-frequency
estimation, favorable cohort omission or target calibration is performed.

Participant metadata use source/FID/IID mappings; repeated physical genotypes
cannot cross partitions or masquerade as independent rows. Sources with no
eligible evaluation records hold. Shared resource budgets, content-hash cache
invalidation, before/after input guards, local coverage/alignment/disposition
audits and aggregate-only finding context preserve the evidence chain. Typed
supplied performance and related results bypass completed work, including the
genotype read when both stages are supplied. Known nonadditive/conditional score
rows hold rather than being approximated with additive coefficients.

Final authority is `build/genetic-score-acceptance-20261025-r2`, built from
`build/genetic-score-source-20261025-r1`:

- Frozen `check executableJar` passed 2,334 tests in 405
  suites: 2,205 executed passes, 129 optional
  environment/runtime skips, no failures or errors. All 10 new tests passed.
- The exact-JAR public verifier passed 15 cases: 9 completed and 6 deliberate
  scientific/resource holds. Its 1,964 independent NumPy/SciPy comparisons cover
  every retained score/prediction, quantitative/raw/probability performance and
  related-family coefficients/uncertainty. Tests include coordinate aliases,
  effect-allele reversal, shared IIDs across families/cohorts, external-frequency
  imputation and fixed sample QC, mapped gzip/OR weights, a 2,101-member model
  with a nonzero MAC-1 member, incomplete/ambiguous/nonadditive models, source
  loss and shared work admission. Supplied stages bypass completed work.
- Cache replay reused all operations. Changing one synthetic BED genotype
  recomputed its stage and matched an independently calculated score change;
  restoring the original input reused its original cache. The earlier artifacts
  retained their hashes. Original and intentionally mutated input editions,
  both operation attempts and the independent mutation check are retained.
- The same JAR passed the prior 14 nested-score runs/1,680 numerical checks,
  10 quantitative score cases, 13 binary cases/
  201 checks, 13 Cox-score cases/78 checks, 11 Cox-proof
  cases/142 numerical comparisons plus independent cone/exact-witness checks,
  18 survival cases/401 checks and 7 resume/MR runs/48 OLS checks.
- 1,903 compiled main classes match the executable;
  25 guarded implementation/contract files match frozen source.
  Paired schemas and 103 workflow inputs validated;
  8,137 recorded output hashes were independently
  rechecked. Frozen source, dependency, full XML, executable, pinned verifiers,
  original inputs and local audits are retained. Final rendered documentation
  and website checks are recorded separately before sealing.

Source receipt SHA-256: `cf62e0446d01314505453c9cd9c759672a3285c89b64cd2941e507f361798c30`. Executable:
`70c4b03b0f740097a45f51a16dc6703ca504657909d3fd04629a2f6a5a3c4317`. Public verifier: `065d4c08a850ce465c189eafb26611c3daec0f8271a81ae08d993ab09283350b`.
This is generated-data numerical/workflow acceptance; no real biological
generalization, clinical validation or release is claimed. The interim
15-case preflight remains retained separately from the final frozen authority.
The independent verifier has a one-line overlay: absent `reused_from_cache` on
a fresh manifest means false. Its first cache-mutation attempt stopped on that
verifier assumption; the corrected verifier independently checked recomputation,
restoration and artifact retention. The compiled SourceR1 implementation, JAR
and full tests are unchanged; both verifier editions and the aborted attempt
remain retained.

This checkpoint applies supplied weights to BED genotypes. It does not add native
genome-wide LD-aware model training or connect VCF/BCF/BGEN/PGEN scoring to this
study branch, nor does it establish biological performance, model transportability,
reference-FASTA build certification or clinical utility. Use appropriately
validated supplied participant scores for other formats/models. The existing
2,000-feature dense quantitative/binary development and nested-validation routes
remain available for bounded predictor sets. Full pseudocode implementation
remains active. See the
[guide](vignettes/study-pipeline-tutorial.md#frozen-genetic-scores-from-bed-genotypes).


## Variant-format frozen score application, 2026-10-25

Complete frozen-weight study scoring now connects existing VCF/VCF.gz, BCF and
BGEN readers, including mixed BED/variant-file cohorts. Model matching remains
coordinate/allele/build based; no target MAC, strand inference, partial model,
target-frequency estimate or favorable cohort omission changes the weights.
Exact file-header IIDs, repeated-unit boundaries, selected biallelic/diploid
eligibility and explicit unknown-ploidy dosage declarations precede scoring.
Both supplied stages can bypass missing genotype files; related-only work can
reuse frozen weights without requiring the already evaluated target outcome.

Whole-stream framing/decompression/sample admission precedes decoding and shares
long-count genotype work limits across sources. End-of-source checks catch later
duplicates, missing model members and malformed tails. Local audits expose
format/counting/ploidy semantics; finding context retains only aggregate format
and declared-diploidy counts. Source/sidecar hashes enter cache identity and are
checked again after application. The default cell bound remains one billion,
with explicit admission up to 50 trillion, distinct from metadata/record bounds
and actual CPU/heap performance.

An independent BGZF BCF2.2 case found a real core bug: the suffix-based BCF path
passed compressed bytes to the codec. Core BCF iteration now detects compression
by magic and streams plain/gzip/BGZF data through the existing BCF2.1/2.2 codec.
Direct tests cover metadata, sample reorder, repeated opens, dosages and corrupt
gzip. Scoring admission/reader integration is separate from the core parser fix.

Final authority is `build/variant-score-acceptance-20261025-r1`, built from
`build/variant-score-source-20261025-r1`:

- Frozen `check executableJar` passed 2,370 tests in 410
  suites: 2,235 executed passes, 135 optional
  skips, no failures/errors. All 14 new tests passed: 12 study-source tests and
  two direct BCF tests. The focused five-suite gate also passed all 41 cases.
- The exact-JAR variant verifier passed 23 cases: 16 completed and seven
  deliberate holds, with 4,566 independent NumPy/SciPy numerical checks.
  Independently encoded raw/gzip VCF, raw/BGZF BCF2.1/2.2, raw/zlib/zstd and
  phased BGEN, external sample sidecars, reversed sample order and three mixed
  cohorts are exercised. Missing/duplicate loci, observed or unknown ploidy,
  multiallelic selection, shared whole-source budgets, source conflicts and
  supplied-stage bypasses have explicit dispositions. Related-only work
  completed without a target-outcome column. Source mutation recomputed a score
  matching an independent expected change; restoration reused the original
  cache and retained earlier artifact hashes.
- Actual PLINK v2.0.0-b.1.2 64-bit (24 Oct 2026) imports and score commands passed five
  profiles/ten processes on the exact final VCF, BGZF BCF and BGEN fixture bytes.
  The 240 sample rows support 480 direct native-versus-original score sum/average
  comparisons, 240 independent named-dosage sums and 240 exact allele counts.
  Comparisons explicitly allow one PGEN dosage unit of 1/16,384 plus recorded
  printed-score precision; quarter-valued VCF/BCF dosages need no import
  quantization allowance. The original profile retains DS, disables erasure and
  mean imputation and explicitly sorts the interleaved fixture variants. The
  first unsorted-import refusal is retained. No reference weights or samples
  were dropped to obtain agreement. Original binary SHA-256:
  `f498c754dd97b0010ae907fb92561f9cf5e2bca6fa15e0c527375077985f8105`.
- The same JAR passed all eight earlier workflows: 15 BED cases/1,964 numerical
  checks, 14 nested runs/1,680 checks, ten quantitative score cases, 13 binary
  cases/201 checks, 13 Cox-score cases/78 checks, 11 Cox-proof cases/142 numeric
  comparisons plus LP/exact-witness controls, 18 survival cases/401 checks, and
  seven resume/MR runs/48 OLS checks.
- 1,920 main classes match the executable;
  38 current implementation/contract files match frozen source.
  Paired schemas and 149 input configurations validated;
  9,040 recorded output hashes were independently
  rechecked. Frozen source/dependency, full XML, pinned JAR/verifiers, generated
  inputs, source/audit outputs, original comparison profiles and failed preflight
  editions are retained. Final documentation and website gates are recorded
  separately. Later concurrent worktree changes do not expand this frozen scope.

Source receipt SHA-256: `a1c077cad1f18c91b14796639cb0724d11fe246339e32fee40f9f5c85a12f7e5`. Executable:
`74121904e37d8494818099e4a0dcef2aa81cb082014adafc5838c12d62265305`. Variant verifier: `0203ba64343cb7f98efd5636dbebd4709b672af6136b04944b57c2ad2e5ad43c`;
required fixture helper: `065d4c08a850ce465c189eafb26611c3daec0f8271a81ae08d993ab09283350b`. The first packaged attempt
exposed and retains the compressed-BCF core defect; the next completed its
analysis cases but stopped on a verifier assertion that incorrectly treated
bookkeeping/report operations as supplied analyses. The corrected verifier
checks the two actual score stages. No biological or clinical generalization,
public-release equivalence or full-pseudocode conformance follows from these
generated-data checks.

This route scans sequential inputs and applies supplied additive autosomal
weights. Native LD-aware training, PGEN, broader ploidy/nonadditive models,
reference-FASTA certification, clinical transportability and real scientific
validation remain outside this checkpoint. Full pseudocode implementation stays
active. See the [guide](vignettes/study-pipeline-tutorial.md#frozen-scores-from-vcf-bcf-and-bgen).


## Molecular assay QC checkpoint — 2026-10-26

Native source-only `assay_qc` now precedes actual molecular matrix consumers:
participant selection, OLS/GLM/Cox association, native correlation networks,
adjusted interactions and direct-matrix risk development/application. Audit is
the default; filtering/masking requires explicit policy. Fixed original source
denominators avoid iterative sample/feature selection. Finite/range/count,
missingness, exact constant, scaled sample-SD/log-SD and optional exactly aligned
platform detection checks retain local audits. Exact integer count totals survive
floating point and long-integer overflow limits. No imputation occurs in QC.

Complete supplied sample/feature keep/exclude decisions bind an exact current
matrix hash, dataset, population dimensions and versioned source method report.
Post-QC matrices do not require unavailable original raw files. Native thresholds
are not reapplied or represented as executed. Invalid retained values, incomplete
scope, changed sources/resources, duplicate identities, bounds or empty retained
populations hold the whole stage. All consumers verify sealed source identity,
policy and outputs. Supplied analysis stages and graph-only references do not
acquire unnecessary raw-data prerequisites. Only aggregate QC context enters
finding appraisal; source vectors and decision reasons remain local.

Prespecified score predictors removed by QC hold the score rather than selecting
a favorable intersection. Score fitting retains training/fold-only imputation
and scaling. Source-QC cache scope excludes outcomes, phenotype contents and
unconsumed files; exact policy and consumed matrix/detection/supplied resources
remain bound. This does not certify assay chemistry, statistically independent
filtering, model validity, FDR or independent external evaluation.

Final authority is `build/assay-qc-acceptance-20261026-r3`, built from
`build/assay-qc-source-20261026-r5`:

- Frozen `check executableJar` passed 2,424 tests in
  415 suites: 2,281 executed passes,
  143 optional skips and no failures/errors. All 24 new
  tests passed (19 measurement-QC, four planning and one cache-scope test), as
  did the five updated resume tests.
- The exact-JAR workflow passed 19 cases: ten completed and nine deliberate
  holds, with 1,527 independent NumPy/SciPy numerical checks. Source moments,
  fixed original missingness, retained matrices, exact count totals and actual
  OLS/Cox/interaction consumers were checked. Network sample scope and the
  aggregate finding-context boundary were checked; participant identifiers and
  QC vectors were absent from the finding inventory. Evaluation changes left
  development weights unchanged; a dropped required predictor held the score.
  Source mutation recomputed QC, restoration reused the earlier cache, and
  historical artifacts retained their hashes.
- The same JAR passed all five earlier regression workflows: ten risk-score
  cases, native/supplied network and module-GO cases, 18 preservation cases,
  18 survival cases/401 numerical checks, and seven resume/MR runs/48 OLS checks.
- 1,940 compiled main classes match the JAR;
  all 27 current guarded implementation/contract files match the
  frozen source. Paired schemas and 139 generated configurations
  validated; 8,586 recorded output hashes were independently
  rechecked. Frozen sources, dependency, complete test XML, exact JAR/verifiers,
  generated inputs and local audits are retained. Final documentation overlays
  and website verification are recorded separately.

Three actual counterexamples are retained against superseded local JARs:
positive/negative decimal probabilities that rounded to zero, and oversized
supplied integer counts that wrapped to a plausible small count. Exact decimal
bounds/cutoffs and `longValueExact` now produce the required dispositions on the
same input bytes. Replay inputs, old binaries and corrected outputs are retained
inside the final bundle. The first full run's four hard-coded old operation-count
assertions were updated for the new source node and its independent cache scope;
that failure is retained. A later full attempt was deliberately stopped when
integer validation was strengthened. No superseded attempt is promoted to a
passing gate. This acceptance uses generated data and does not establish assay
chemistry, biological validity, model/FDR calibration or full-pseudocode conformance.

Source receipt SHA-256: `a531f13b743d0347f80df84f155d1e145721acd2d78d1091a9d37dc2a18f4936`.
Executable SHA-256: `f1e8f8dfcf355ecf1b35beaded7d5ff3a02892e6ced6ec80eb7626ecd272ee4c`.
Workflow verifier SHA-256: `5019f2cd77d9948509f6ec89736cf31f87af71a15e5f1551acf2a76d5a6e8de0`.
The first 14-case and expanded 16-/18-case preflights remain historical evidence;
only the final source/JAR pair above is this checkpoint's authority.

See [the policy and reuse guide](vignettes/study-pipeline-tutorial.md#molecular-assay-qc-and-supplied-decisions)
and `examples/study-pipeline/assay-qc-workflow.py`.

## Completed analysis and manuscript integration checkpoint — 2026-10-26

Standalone typed `network_modules` and `module_gene_ontology` imports now close
an actual missing reuse route. The prior sealed JAR rejected module GO as an
unsupported supplied kind. Completed source GO can now feed finding context
without unavailable raw data, graphs, memberships or a local GO database.
Supplied memberships can instead feed only missing native GO, with an explicit
complete node universe, compatible mapping and local resource. Missing or
conflicting scope stays unresolved; no significant-hit background or replacement
graph is inferred. Partial module/module-GO imports retain snapshots and hold
the requested stage.

The frozen source `build/completed-study-source-20261026-r5` contains 2,828
files. R3 derives from R1 with only the final public Python verifier corrected
to trace its original parent graph manifest through immutable cache history.
R5 also corrects paired schema conditions for existing local-model endpoints
and model settings; twelve provider-configuration comparisons agree with the
CLI validator. Every Java, test, resource and build input remains identical.
R1's fresh full gate passed in 12 minutes 26 seconds. The derived full gates
reuse those identical cached test inputs: **2,461 tests in 419 suites, 2,308 passed, 153 optional
skips, zero failures/errors**. All 11 new supplied-module tests passed. The
working tree's concurrent PLINK additions are outside this source checkpoint.

The exact packaged JAR passed the new 11-case completed-study workflow: six
completed and five intentionally held. Independent checks reconstruct missing
module GO hypergeometric probabilities and joint BH, preserve finite underflow
logs, original selected-export correction over 1,000 source tests, and distinct
module types sharing a local ID. Entirely supplied analysis tables continue
through native finding inventory, a deterministic impact protocol and drafting
without participant data. Seven eligible candidates are assessed; three
distinct gene stories proceed to an authored revision with **24 claim bindings,
eight declared depth facets per finding and linked supplements**. Late node
background arrival completes only missing work and retains old artifacts.

Nine same-JAR regression workflows also passed: supplied results, scoped
supplied/native work, networks/module GO, cross-cohort preservation, impact,
manuscript depth/word limits, supplied literature, principal-finding source
review and molecular assay QC. Same-input counterexample replays confirm both
the previous unsupported-kind error and a superseded preflight's partial-GO
completion defect, then demonstrate the corrected behavior. Earlier failed
preflights remain historical evidence.

Source receipt SHA-256:
`96b54df2617c564f931d793df58357b37ef674acb195c17911e496791d40fd8f`.
Executable SHA-256:
`35ece8099c495d3e36af1138a8858325f7209bf86794d49ab4706aa08be3325e`.
Public workflow verifier SHA-256:
`c373fa95548991292bdf46e6fe942809b5eaeef566ab2343db17c661f0f6e286`.
The retained bundle is `build/completed-study-acceptance-20261026-r1`.
See [completed analyses through manuscript](vignettes/study-pipeline-tutorial.md#completed-analyses-through-manuscript)
and `examples/study-pipeline/completed-study-workflow.py`.

The new responder, data, references and authored prose are synthetic. Existing
reference regressions reuse previously acquired public text without new live
requests. Literal source bindings and facet coverage do not certify source
support, biological conclusions, real AI ranking or OAS1-level discussion
quality. Scientific review remains required. This is uncommitted source-build
progress, not a release or full-pseudocode conformance claim.

## Provisional literature priority checkpoint — 2026-10-26

The native literature stage now permits impact allocation **before** applying
the review cap. `bioinformatics.literature_selection: auto` uses the enabled
impact purpose when eligible targets exceed the cap; `impact` explicitly
requires it, and `p_value` retains the deterministic order. All targets that fit
within the cap require no additional AI interaction. Completed supplied
literature skips native allocation. Statistical eligibility and the later
study-wide manuscript impact review remain separate.

All eligible mapped targets enter bounded provisional assessments, followed by
comparative rounds. Public phenotype/field labels, original P/adjusted P and
available finite signed summaries are supplied; project descriptions and
free-text source provenance are excluded. Missing public phenotype context,
incomplete responses, deferral, policy denial or exhausted budgets retain
explicit pending dispositions without a P-value fallback. Complete node
responses can be reused across attempts and budget amendments. Deferred
comparisons can be retried without repeating their completed leaf assessments.
The full statistical inventory, distinct review order, model interactions,
candidate projection and source-family values remain auditable.

The final frozen source `build/literature-priority-source-20261026-r2` passed
the full gate: **2,496 tests across 422 suites, 2,336 passed, 160 optional skips,
zero failures/errors**. All ten new tests passed, including native retrieval,
public-context gating, policy denial, incomplete/malformed replies, response
formats for the primary API adapters and immutable partial-response reuse.
The earlier R1 full gate is retained as an intermediate checkpoint before the
public-context refinement. Resolver defaults are version 47 and execution-cache
implementation is 74.

The exact final JAR passed the new ten-case packaged workflow: five completed,
five intentionally held, with 846 independent numeric checks. A 105-signal
fixture assesses the full pool before assigning 100 slots and retains the
original complete-family P/FDR values. Nine same-JAR regressions passed:
completed-study reuse/manuscript integration, supplied literature, final impact,
main/supplementary limits, principal-finding source review, scoped supplied
work, stage resume, rare-set follow-up and supplied results.

An identical-input old/new replay confirms the original ordering defect. With
source-P allocation, synthetic final principal targets 103–105 were outside the
first 100 literature slots. The revised native allocation reserves slots for
them before final review. These are synthetic priorities and completed-query
declarations, with no new public-provider requests or real scientific impact
appraisal. Source import validation may rerun after an amendment under its
conservative full-study cache contract; the original analysis is not rerun and
its canonical statistics remain byte-identical.

Source receipt SHA-256:
`9baf197fbba92f533aeda24c3b7098d95faf238282be668e8023c8f3f6888897`.
Executable SHA-256:
`e2805ce49a6777efe8512fe21ff104ac81a302e8846cf58af732b5e3f63936ff`.
Public workflow verifier SHA-256:
`224cd29f49385203d4b13a0add5b31ed67446bb79c989cb62b7744092a1108ab`.
Evidence is retained in `build/literature-priority-acceptance-20261026-r1` and
`build/literature-priority-counterexample-20261026-r2`.
See the [policy and runnable example](vignettes/study-pipeline-tutorial.md#provisional-impact-before-the-literature-cap).

Mechanical source/response checks do not establish biological importance,
source support or globally optimal rankings. Scientific appraisal and the
broader full-pipeline completion audit remain open. These are source-build
changes, without commit, release or publication acceptance.

## Checkpoint: literature coverage in final finding dossiers (2026-10-26)

The final inventory now carries typed per-finding literature coverage from
verified native allocation ledgers, native retrieval pages and supplied query
receipts. Native nonselection and pending allocation remain separate from any
valid prior searches. The record distinguishes bounded no-match results,
retrieved candidates, limited/incomplete retrieval, missing feature scope,
conflicting identities and bibliography-only inputs. It retains statistical
and review ranks, selection basis, dates, strategy coverage and artifact links.
Identical supplied receipts reused by a native stage count once; native pages
form logical queries. Producer ledgers are indexed once for large inventories.

Coverage does not change primary statistics, adjustment families or candidate
eligibility. Fixed source-linked caveats accompany final impact comparison,
selected manuscript dossiers and optional editorial payloads. Missing searches
and empty bounded queries cannot establish novelty or absence of prior work;
article retrieval and supplied bibliographies do not establish source support,
full-text appraisal or an exhaustive review.

The old/new replay uses identical nine-target inputs with only two targets
having supplied searches. The old executable omits coverage for the third
unallocated target; the revised executable records both its native allocation
and absent feature-query receipts. A separate case confirms that a target
outside the cap can still retain three completed prior queries. The earlier
105-target allocation fixture supplied completed queries for every target: its
allocation result must not be interpreted as absence of all prior evidence.

See the [coverage guide and runnable example](vignettes/study-pipeline-tutorial.md#literature-coverage-in-finding-dossiers).

Frozen source `build/literature-coverage-source-20261026-r1` passed the full
`check executableJar` gate: **2,529 tests across 426 suites, 2,365 passed,
164 optional skips, zero failures/errors**. All 12 new coverage tests passed.
The four-case exact-JAR coverage workflow has three completed studies and one
intentional allocation hold, with 72 independent numeric checks. Ten regression
workflows cover provisional allocation, completed-study reuse, supplied
literature, impact comparison, manuscript depth/source review, scoped imports,
resume, rare-set follow-up and supplied results. The rare-set verifier's legacy
one-observation assertion was corrected to check one primary enrichment result
and its separate nonprimary coverage context. Its failed process, original
verifier and successful recheck of retained native outputs remain available;
no native analyses or frozen Java sources were changed for that correction.

Read-only checks verify coverage/caveats in 405 final-impact finding instances,
three selected manuscript dossiers and three local editorial contexts. The
supplied-study example retains bibliography-only coverage rather than inventing
feature-level searches. The old/new replay preserves all primary values and
eligibility, as well as identical input bytes. All 1,988 compiled main classes
match the executable. Execution-cache implementation is 75; resolver defaults
remain 47.

Source receipt SHA-256:
`c8c2cf1dd045981733ced7ebc3c993f17ad52d6dd21a1399c41d8a94cda7e77e`.
Executable SHA-256:
`aa3c8d367147fa6df739e509fb02a0966da629e80536bb7aee5ccafb5d3597c5`.
Coverage-workflow verifier SHA-256:
`b03e74297ddedd166b98048f8fee8a9d4784cef34551eb179930ed15d4edde02`.
Evidence is retained in `build/literature-coverage-acceptance-20261026-r1`, with
the paired replay in `build/literature-coverage-counterexample-20261026-r2`.
The corrected rare-set example is explicitly archived as a verification overlay
beside the unchanged source snapshot. These are uncommitted source-build
checks; scientific source appraisal, real-model discussion quality and the
broader pipeline completion audit remain open.

## Checkpoint: contextual model review and exact literature-source numbers (2026-10-26)

Missing-model review now uses declared public phenotype/assay labels, controlled
endpoint and dependence terms, and up to two publication-date-ordered Europe
PMC queries. Candidate configuration names match the executable: `ols`, `lmm`,
`glm`, `glmm`, `cox`. A conditional context match does not establish scientific
suitability or paper support. Missing, blank and null models retain the blocking
question. No Gaussian family is inferred from an undeclared endpoint.

For supported native associations, the participant-QC dependency requests this
review before interpreting an unknown outcome family or selecting modeled
samples. This includes categorical case/control outcomes with no declared
family. Unsupported modalities retain their adapter prerequisites. Standalone
descriptive QC continues under its existing endpoint contract; supplied and
configured association operations do not impose a separate native-model
requirement. Model selection remains explicit before any association fit.

Typed supplied literature now reads decimal tokens without rounding through
double precision. Hit counts must be exact nonnegative signed-64-bit integers;
fractional, underflowing and overflowing values cannot become empty searches or
different counts. Numeric publication years must also be exact, while existing
four-digit string years remain supported. The opt-in source reader preserves
size, nesting, duplicate-key, alias and safe-tag restrictions. Original source
bytes/hashes remain intact; the default reader for other artifacts is unchanged.
The paired source schemas declare the count range.

Frozen source `build/model-review-source-20261026-r3` passed the full
`check executableJar` gate: **2,564 tests across 428 suites, 2,393 passed,
171 optional skips, zero failures/errors**. Twenty-four new tests cover model
review, source numbers and cache context, with the existing modality regression
strengthened. The earlier R2 gate exposed the unsupported-cell applicability
error; its failed log, test result and source identity are retained. The shared
supported-type guard and configured/supplied exclusions pass the corrected gate.

The exact executable passes twelve packaged workflows. The new eleven-profile
model-review workflow checks all initial holds, an explicit OLS continuation,
16 independent SciPy beta/SE/P/BH values and immutable earlier attempts. Eleven
regressions cover literature allocation/coverage, supplied/completed analyses,
impact review, manuscript depth/source binding, scope, resume and rare-set
follow-up. Synthetic approval events in the example are protocol fixtures.

Six same-input old/new cases confirm rejection of four malformed source inputs,
preservation of a valid decimal integer above `2^53`, valid candidate model names
and use of the explicitly declared public phenotype label. Precision-aware
source-schema checks independently validate the five numeric-source cases.
Two native live Europe PMC requests returned HTTP 200 and valid metadata, retaining
20 candidates per query and 39 records after identifier deduplication. The live
project remains waiting for an explicit model; retrieval is not model appraisal,
source authentication, a full-text review or scientific approval.

Source receipt SHA-256:
`641dabf260023cf6bd90bdafc526f4684d1ee4db04d6e96fadd842ad1e37b210`.
Executable SHA-256:
`6aaa44c8ccfa97e02bda667bfe0689d5880f0fa1aefd1c47e519725893fab505`.
Public model-review verifier SHA-256:
`3469e3e9103f9672946f615dcf4e536f97c5dd20654907a62093cc3a52e4d29a`.
Evidence is retained in `build/model-review-acceptance-20261026-r2`, with the
paired replay in `build/model-source-counterexample-20261026-r3`.
Execution-cache implementation is 76 and resolver defaults remain 47.
See the [missing-model guide and runnable example](vignettes/study-pipeline-tutorial.md#missing-model-review-and-explicit-continuation)
and [supplied-literature contract](vignettes/study-pipeline-tutorial.md#reuse-supplied-literature-and-complete-missing-searches).

These are uncommitted source-build changes. Scientific model suitability,
real-model discussion quality and the broader pipeline completion audit remain
open; no release or publication acceptance is claimed.


## Phenotype-null GSEA checkpoint, 2026-10-26

The previous preranked checkpoint's unconditional phenotype-null prerequisite is superseded for two explicit routes: cohort-specific independent Gaussian identity OLS Freedman–Lane residual permutations, and complete model-matched joint null-ranking ensemble reuse. Gene-set-null analysis and completed supplied-stage reuse retain their separate contracts.

Native planning adds the exact dataset-specific participant-QC producer even when association is not requested. Enabled assay-source QC binds the same matrix policy. Formula treatment coding, trait/covariate complete-case population and within-population molecular mean imputation match the matrix OLS conventions. Variable-feature eligibility precedes outcome ranking; gene collapse is repeated for every draw. Shared within-stratum permutations preserve the fixed feature dependence. General nuisance inference remains approximate, including exhaustive residual enumeration; independent/exchangeable errors are explicit scientist declarations. Relatedness, repeated measures, non-Gaussian, offset and custom-formula native adapters remain incomplete.

The external route validates the exact observed-ranking SHA, full finite identical gene universe, contiguous unique draw IDs and complete declared draw count. Missing, extra or ambiguous null rows cannot publish a favorable partial family. Its model/source/exchangeability/calibration and exhaustive-coverage statements remain source declarations. Both routes retain term-specific same-sign ES distributions, BH/BY/NONE complete-family correction, counts, resolution, Wilson Monte Carlo precision, leading edges, GMT and curve outputs; no Broad pooled-NES FDR or real pathway function is claimed.

The new public `phenotype-gsea-workflow.py` prototype passed eight packaged synthetic scenarios with 1,911 independent partial-t checks and 15 independently checked enrichment terms. These counts describe the prototype run, not a full source-snapshot gate or a release. The guide documents resource controls, applicability, aggregate-only reuse and remaining scientific requirements. This checkpoint is progress on the overall goal; it is not full-pipeline conformance.


The completed frozen-source gate for this checkpoint is `build/phenotype-gsea-source-20261026-r2`: **2,595 tests across 433 suites**, 2,421 passed, 174 optional skips, zero failures/errors; `check`, `executableJar` and the snapshot website check passed in 13m 23s. All **18 new tests** ran and passed. The source receipt covers 3,055 files, SHA-256 `11adf43a617e2e2de9bda7d4276cead24f5149b1ea8156d88fd94bc6e940b18b`; the exact packaged JAR SHA-256 is `afeadecbd2f4584a7258be9289af5d3d1b497af048fb29ed5248be46eadfd303`. These identities cover the frozen source, including its concurrent source additions, rather than later changes in the shared worktree.

`build/phenotype-gsea-acceptance-20261026-r1` retains eight new phenotype-null scenarios with 1,911 independent observed/null partial-t comparisons and 15 enrichment-term comparisons. Nine regression workflows also passed: preranked GSEA, molecular/pathway networks, frozen-network nulls, model review, assay QC, stage-specific resume, completed-study manuscript assembly, supplied results and primary-result selection. Five same-input old/new comparisons additionally show the old executable rejecting the new contracts and the new executable completing them with independent checks.

The first frozen gate's website failure is retained: its new relative tutorial link escaped the published site root, and was corrected before the successful R2 gate. The acceptance runner's initial network-null attempt also failed because its adjacent preservation fixture had not been pinned. The original runner, log, receipt and partial output are retained; after pinning that exact frozen helper, only the affected workflow was rerun into `network-null-complete` and passed. Neither failure is relabeled as a successful first attempt. Final documentation clarifies that imported Monte Carlo draws must be independent and exhaustive states equiprobable; weighted state enumeration and MCMC precision correction are outside this format.

The new path still needs scientific assessment of exchangeability, nuisance approximation, assay preprocessing, gene mappings and collection suitability. Broader native dependent/non-Gaussian/pooling null adapters, real-data calibration, complete pipeline conformance and publication remain outside this checkpoint. The work remains uncommitted and unreleased.


## Continuous GWAS annotation checkpoint, 2026-10-26

The native enrichment stage now adds explicit mixed binary/continuous S-LDSC, rather than treating a continuous score as a category or relying only on external completed imports. Complete reference sums/cross-products support signed and centred nuisance/target columns, population SD and standardized conditional effects. Reference-moment/rank validation, joint fits, fixed all-SNP baseline initial weights, covariance and delete-ratio uncertainty connect to the study graph. Legacy binary mode retains its aggregate-annotation weighting convention. The mixed weighting choice is explicit; upstream estimator parity and scientific calibration are not claimed.

Continuous targets use raw conditional-coefficient normal-tail tests and tau-star estimates with full-reference SD and total-h2 ratio uncertainty. Binary targets retain category-versus-complement t(B) tests. All declared targets share a complete correction family, with nuisance columns outside that denominator. Natural-log normal/t tails and log-space adjustment retain associations whose linear P values underflow. Typed source results preserve new estimate/uncertainty/label fields, original adjustment and row caveats; incompatible continuous/category semantics or invalid uncertainties/log probabilities fail import. Finding dossiers retain aggregate method/reference context and the distinct statistic meanings.

The public continuous-GWAS prototype passed nine scenarios and 468 independent numerical comparisons, including fixed intercept, affine score units, continuous nuisance-only roles, source reuse, weak-heritability/moment/work/selected-source holds, immutable inputs/history and cached replay. Focused binary/mixed/import tests passed before the last input-domain test was added. An initial constrained-zero baseline-SE comparison failed at machine epsilon; its test XML and executable hash are retained and the corrected check uses an absolute tolerance only for that known-zero baseline, preserving tight coefficient/covariance/P tolerances elsewhere. The completed frozen-source validation is recorded below.


The completed gate is `build/continuous-gwas-source-20261026-r1`: **2,641 tests across 439 suites**, 2,462 passed, 179 optional skips and zero failures/errors. All **17 new tests** ran and passed. `check`, `executableJar` and the frozen website gate passed in 16m 24s. The source receipt covers 3,297 files, SHA-256 `9249f3cc804126b17635bc0984bac4f6022cfc7c0a98f00a926c80eafa566718`; the exact JAR SHA-256 is `1d5f277680d243a2437bd5ff6bb09348623242eeba298963fcd447fb5afad271`. Frozen compiled classes were matched to that JAR. These results cover the recorded snapshot, including its concurrent additions, rather than subsequent edits in the shared checkout.

`build/continuous-gwas-acceptance-20261026-r1` records ten successful packaged workflow executions: the nine-scenario continuous-annotation verifier (468 independent numeric comparisons) and nine regressions covering binary S-LDSC, phenotype-null/preranked GSEA, completed/supplied/primary/scoped result workflows, impact and stage-specific resume. The binary verifier uses its exact frozen reference/oracle repository layout; its existing estimator references remain distinct from the new mixed-mode baseline weighting choice. Six same-input old/new comparisons additionally verify native mixed/affine execution, preservation of valid typed effects and rejection of three contradictory source tables: continuous category enrichment, negative coefficient uncertainty and positive log probability. The initial machine-epsilon baseline-SE reference failure and its correction remain in the evidence bundle.

The completed checkpoint verifies 165 study-schema cases, five explicit annotation-mode schema cases and 8,580 operation-output hashes; 2,033 compiled main classes match the exact JAR and 13 guarded current implementation/schema/verifier files match the snapshot. Final documentation is checked and retained separately. None of these checks establishes real GWAS calibration, adequacy of a particular annotation/nuisance model, uncertainty in the LD/reference estimates, upstream mixed-estimator parity or complete pipeline conformance. Broader weighting/reference construction, multi-ancestry and genome-wide scientific/scale acceptance remain open. The work remains uncommitted and unreleased.


## Supplied-primary automatic follow-up checkpoint, 2026-10-26

The registered supplied-primary workflow now uses explicit source modality/feature namespace to request missing GWAS enrichment and diagnostics, ranked GSEA/pathway modules, GO/pathway enrichment and protein context even with no original datasets. It respects manual goals, disabled branches, completed supplied-stage owners and the registered primary selector. Discovery-only and sensitivity pools cannot own these inferences; gene-level GWAS does not become a SNP scan. Compatible selected `analysis_reference` entries owned by the exact primary pool can supply an omitted modality. `_resolution.followup_applicability` records the source, generated operation ID, effective modality and its origin, goals and merge decision. Native full-family/model/identity gates still determine whether a requested stage can execute. Pooled statistics do not replace participant matrices.

The tutorial contains [the input contract and runnable examples](vignettes/study-pipeline-tutorial.md#automatic-follow-ups-from-a-supplied-primary-meta-analysis). A header-only harmonization annotation table is now accepted by the literature-support reader, preventing source-only studies from crashing. Explicit feature-to-gene mappings remain necessary; applicability metadata does not manufacture them.

The frozen source is `build/auto-supplied-source-20261026-r2` (3,319 files), with source-receipt SHA-256 `ea9384db15345c59c079a9556afdbc08d6e9dc0393bb338e78a1dfbfd2d3f76c`. The exact executable is `build/auto-supplied-acceptance-20261026-r2/software/jlinalg.jar`, SHA-256 `034476d22a8cb85197a3267cf4bb3bdea311762f87dc4e494cb2c55910df842c`. Its version label remains 0.3.7; this locally modified artifact is uncommitted and unreleased.

The complete frozen `check executableJar` gate passed **2,660 tests in 440 suites**: 2,481 passed, 179 optional integration skips, no failures/errors. All **19 new supplied-follow-up tests** passed. The skips do not establish installed-tool or external-service execution.

Ten exact-JAR packaged workflows passed: expanded GWAS enrichment (20 scenarios, 1,331 independent arithmetic/workflow checks), expanded preranked GSEA (9 scenarios with independent scores, null tails, leading edges and network/module GO), continuous GWAS, phenotype-null GSEA, completed-study publication, supplied-result reuse, primary selection, scoped reuse, impact assessment and stage-specific resume. Completed meta/enrichment remain cached; incomplete automatic literature/heterogeneity branches retry and can refresh downstream inventories. Seven identical-input old/new comparisons demonstrate newly completed GWAS/GSEA work, protein sequence prerequisites, missing mappings without crashes, exclusion of discovery-only/gene-level SNP routes, and preservation of the existing reference-metadata route.

Checkpoint verification matched all 2,036 compiled main classes to the JAR, checked 8 current guarded source files against the frozen receipt, validated 169 study documents, and verified 8,907 indexed operation output hashes. The bundle retains its frozen source archive, full test XML/logs, exact software/verifiers, all workflow projects and before/after inputs. Final documentation and workspace drift are recorded separately before sealing.

Initial failures are retained. The first focused fixture used an invalid GSEA setting; its correction exposed the real header-only reader failure. The first expanded GWAS verifier incorrectly required a refreshed automatic inventory to remain cached; its statistical-stage reuse assertion now reflects the pending branches. The first full gate had one compatibility failure because the new applicability check overlooked existing genomic `analysis_reference` metadata. The corrected full gate preserves that valid contract and adds a dedicated test and executable comparison. These failed revisions have no passing checkpoint seal.

This is synthetic arithmetic and workflow acceptance, not external scientific appraisal or complete pipeline conformance. A separate exact-JAR audit imported complete allele-resolved pooled GWAS statistics but native `variant_scoring` returned `not_applicable` because its catalogue still reads cohort sources. The retained `validation/remaining-pooled-variant-gap` project proves this remaining requirement; pooled-only native SNP annotation and its automatic scheduling remain unfinished. Other open requirements in the full checklist remain active.


## Pooled-GWAS SNP annotation checkpoint, 2026-10-26

The previously retained pooled-only SNP gap now has a native input route. `StudyPooledVariantInputs` obtains the registered, producer-verified primary meta-analysis, preserves its full source/contrast and selection receipts, and adds statistically eligible available SNPs without significance selection. Pooled aliases have no fabricated cohort identity. They share exact physical allele queries with real cohort aliases while retaining `input_source` and `source_operation_id`. The registered source, original testing family, coverage and all available dispositions remain explicit. Selected-only eligible exports can produce available annotations but keep full-GWAS coverage pending; ineligible upstream sources remain blocked.

The [pooled-SNP guide](vignettes/study-pipeline-tutorial.md#snp-annotation-from-a-pooled-gwas) documents canonical/build/row/ref-alt checks, reference FASTA and indel normalization, controlled namespaces and compatible owned cohort metadata. Reference/alternate alleles are not inferred from effect/other alleles. An unambiguous explicit cohort identity can identify a pooled rsID; conflicts require review. Excluded cohort aliases cannot borrow evidence from an eligible pooled alias. Discovery-only, sensitivity, gene-level, foreign-hypothesis and incompatible primary sources cannot substitute for the registered SNP pool. Source-only automatic mode now schedules missing scoring with the ordinary provider policy. Resolver defaults are 51 and execution cache implementation is 80.

Completed supplied variant annotations/scores preserve multiple genes, transcripts, tissues, scorers, tracks, intervals, models and source records per SNP. Exact-context duplicates are rejected; missing source-record markers are normalized consistently. Rich original source records remain intact, with hashes only for identity bookkeeping, and signed quantiles are bounded to `[-1,1]`. Native method reports distinguish functional records from reference-only inventory. Neither source adjustments nor an annotation score are converted into association/causal probabilities.

The final frozen source is `build/pooled-variant-source-20261026-r2` (3,350 files), receipt SHA-256 `9dad70627a9072b27932e9b67520164679ae07d2a09b7f10aa1a8fa65454efc0`. Its exact executable is `build/pooled-variant-acceptance-20261026-r1/software/jlinalg.jar`, SHA-256 `e271c0cb6d310cac7cb56ab899614c749cbe7cffe7b6e76348afb7b5351d633a`. This locally modified 0.3.7 artifact is uncommitted and unreleased.

The complete frozen `check executableJar` gate passed **2,717 tests in 444 suites**: 2,538 passed, 179 optional integration skips, no failures/errors. All **29 new tests** passed: 23 pooled-input/selection/QC cases and 6 supplied-context cases. Optional skips do not establish installed-tool execution.

Eleven exact-JAR workflows passed: pooled SNPs, continuous GWAS, binary GWAS enrichment, phenotype-null GSEA, preranked GSEA, completed-study publication, supplied reuse, primary selection, scoped reuse, impact assessment and stage-specific resume. The new self-contained pooled-SNP example passed 16 scenarios and 336 assertions, including native fixed-effect pooling from two actual cohort result tables, external source-only and owned-reference cases, ANNOVAR-format/AlphaGenome imports, selected coverage, missing/conflicting identities, multi-context completed-stage reuse, mixed aliases and indels. Original supplied FDR is preserved; native pooled P/BH arithmetic is independently checked. Eleven identical-input comparisons show the old missing route, incomplete mixed coverage, rejected multi-context imports and the additional missing-marker normalization fix.

Artifact verification matched all 2,043 compiled main classes to the JAR, guarded 12 current source files, validated 201 study documents and checked 10,590 indexed output hashes. The bundle retains the exact source/dependency archive, XML/logs, software/verifiers, workflow projects and paired inputs. Its preliminary source also passed its full gate and is retained as superseded by the final missing-marker fix. Initial malformed/incorrect fixtures, typed-score failure and verifier filename/statistic assumptions are retained separately; they are not represented as successful accepted revisions.

This verifies the pooled input route, synthetic arithmetic, import/context retention and execution bookkeeping. The offline imports do **not** execute real ANNOVAR/VEP/AlphaGenome, establish biological calibration or genome-wide scale, supply external scientific appraisal, or complete every requirement in the full pipeline checklist. Those evidence boundaries remain explicit. The older checkpoint's `remaining-pooled-variant-gap` records the previous artifact and is superseded for this input route, rather than rewritten.


## Partial-supplied completion checkpoint, 2026-10-26

Explicit partial primaries now retain a `supplied_partial` import and do not own
completed stages. Automatic completion respects stage policies and explicit
manual goals. The source family and adjusted values remain intact, without a
new correction from an incomplete fragment. Completed supplied analyses retain
their stage ownership and skip native reruns. See the
[guide](vignettes/study-pipeline-tutorial.md#partial-supplied-stages-and-native-completion).

An incomplete registered primary pool blocks implicit cohort fallback. A valid
matching native pool or an explicit protocol choice of a cohort can establish
the primary source. Partial network completion must cover the declared
population: a native graph for another dataset leaves the original scope
waiting. Missing or unresolved source populations retain specific needs.

Acceptance uses frozen source `build/partial-supplied-source-20261026-r2`
(3,377 files; receipt SHA-256 `969b4172f330bd43fb66f29e3309781b8cf808a7870da03a20ce02245211b97a`) and
the exact JAR in `build/partial-supplied-acceptance-20261026-r2/software/jlinalg.jar`
(SHA-256 `065177861f33a97d10b184fa880e2f587d7f93a9d7b88c0915e8564b7c561d14`). The full `check executableJar` gate passed
**2,774 tests in 448 suites**: 2,595 passed,
179 optional skips, no failures or errors. All **28 new tests** passed,
covering automatic stage planning, manual/disabled policies, source corrections,
network population coverage, primary holds and explicit/native completion.

Eight exact-JAR workflows passed: partial stages, pooled SNPs, completed-study
publication, supplied-stage reuse, primary selection, scoped networks, impact
assessment and stage resume. The new partial-stage example checks five cases
with 67 checks; five identical-input old/new comparisons retain the original
suppression defect and verify missing-input waiting, manual retention and
completed-stage reuse. Verification checked 122 schema cases,
7,573 indexed output hashes,
2,053 compiled main classes against the JAR, and
17 guarded source/test/example files.

The first source revision passed its unit gate but packaged checks exposed
cohort fallback and unmatched network completion. It is retained as superseded,
alongside the original audit, failed workflow logs and old verifier expectations.
The final frozen revision includes the fixes and updated completed-GO/network
verifiers. These local synthetic checks do not execute external predictors,
certify biological or real AI discussion quality, establish full pipeline
conformance, or publish a release. The changes remain uncommitted and unreleased.


## Finite original-requirement audit and citation checkpoint, 2026-10-26

This audit maps **24 finite requirements** from the original pseudocode and the
lab amendments to the frozen source and packaged workflows. It separates
implemented, locally verified contracts from live execution and scientific
acceptance. It does not add every possible assay, estimator or future extension
to the original request. **Overall scientific conformance remains partial.**
Earlier dated checkpoints below describe their own source editions; this table
is the current finite mapping for `build/citation-handoff-source-20261026-r1`.

| ID | Requirement | Evidence status | Packaged examples/checks |
| --- | --- | --- | --- |
| P01 | Supplied association models and missing-model literature/error gate | Local contract verified | `model-review, genotype-plain, survival` |
| P02 | Consistency, participant/assay QC, distributions and registered subgroup tests | Local contract verified | `assay-qc, genotype-sample-qc, subgroup-interactions` |
| P03 | Analyzed-population MAC 20, sparse/uncommon and separate rare-set routes | Local contract verified | `genotype-qc, human-ploidy, firth-gwas, binary-rare-variant` |
| P04 | Effect QC, exclusions and retained unfiltered sensitivity | Local contract verified | `effect-qc, genotype-effect` |
| P05 | Pairwise effects, correlations and direction | Local contract verified | `cohort-diagnostics, pooling` |
| P06 | Discovery/validation roles and frozen discovery-family validation | Local contract verified | `discovery-scope, frozen-discovery` |
| P07 | Cohort overlap and omission sensitivities | Local contract verified | `cohort-diagnostics, rare-cohort-overlap` |
| P08 | Meta-analysis and heterogeneity; prespecified primary retained | Local contract verified | `pooling, primary-results` |
| P09 | GO, ranked GSEA and LD-aware GWAS enrichment | Local contract verified | `gsea, gwas-enrichment, continuous-gwas-enrichment` |
| P10 | SNP annotation/scoring and pooled-variant coverage | Provider execution conditional | `variant-followup, pooled-variant; live VEP and installed SDK` |
| P11 | Protein/isoform mapping, confidence, retrieval and prediction adapter | Provider execution conditional | `protein; live AlphaFold DB retrieval` |
| P12 | Score development/reuse, independent evaluation and related traits | Local contract verified | `risk-score, nested-risk-score, genetic-risk-score, cox-score` |
| P13 | Ranked/phenotype-null GSEA and pathway networks/module GO | Local contract verified | `gsea, phenotype-gsea, network` |
| P14 | Registered rho threshold, components, cliques and module GO | Local contract verified | `network, network-preservation, network-null` |
| P15 | Matched GWAS/IV sources and full-eligible-feature MR coverage | Local contract verified | `mr-feature-coverage, mr-completion, mr-cross-family` |
| P16 | Regional colocalization and partial-region caveats | Local contract verified | `colocalization, genetic-pair-reuse` |
| P17 | Default 100-feature literature cap and uncapped eligible inventory | Local contract verified | `literature, literature-priority, literature-coverage` |
| P18 | Full-pool comparative impact review and 3–5 distinct stories | Scientific appraisal unverified | `impact, finding-grouping, completed-study` |
| P19 | Eight discussion facets and full-text/result/claim bindings | Scientific appraisal unverified | `finding-discussion, completed-study` |
| P20 | Adjustable 8,000-word target and complete authored supplements | Local contract verified | `manuscript-depth, completed-study` |
| P21 | Completed supplied stages and missing-work execution | Local contract verified | `supplied-results, scoped-supplied, completed-study` |
| P22 | Partial stages, primary holds and matched population coverage | Local contract verified | `partial-supplied, primary-results, scoped-supplied` |
| P23 | Public citation candidates in impact and manuscript requests | Local contract verified | `completed-study; same-input old/new comparison` |
| P24 | Immutable inputs, scoped reuse, explicit needs and retained history | Local contract verified | `stage-resume, genetic-pair-reuse, completed-study` |

"Local contract verified" means the documented input/design and completion
behavior passed the named fixtures. It does not mean every phenotype, molecular
assay, ancestry, dependent design or external data source is supported or
scientifically calibrated. The expanded mappings and source hashes are retained
in `build/full-pipeline-audit-20261026-r2/validation/pipeline-requirements.json`.
Broader scientific/method extensions listed in the historical checklist remain
separate work; the finite table does not certify them.

### Citation metadata now reaches scientific review

The audit found a concrete manuscript gap: accepted bibliography metadata was
absent from impact and manuscript model requests. The pipeline now carries
query-linked public citation candidates with canonical identifiers, metadata
conflicts/corrections and source bindings. Feature/gene matches remain distinct
from shared study background. Whole-manuscript background includes unselected
and null hypotheses, while impact batches receive the relevant hypothesis scope.

The metadata is explicitly unreviewed and is not proof of source support.
Abstracts, full texts, query prose, paths, private notes and arbitrary nested
fields are excluded from this candidate projection. This is a bounded field
projection, not a general privacy or DLP guarantee. The existing native public
full-text and claim-ledger route remains a separate source-appraisal step.
See the [citation handoff guide](vignettes/study-pipeline-tutorial.md#citation-candidates-in-impact-review-and-manuscript-drafting).

Two exact JARs processed the same **15 unchanged input files**. The old JAR
omitted the known citation DOI from both actual model requests; the new JAR
included it in both. Canonical meta-analysis bytes and the protocol's selected
finding IDs were identical. Loopback responses and synthetic bibliography prove
transport and provenance behavior, not actual AI appraisal or source truth.

### Acceptance and retained history

The frozen source contains **3,380 files** (receipt SHA-256
`b2cdc70c5d67b5a3e84eea71be177f1ba62e0ce5fb0762cfaa36018a94d2fb15`). The exact tested JAR has SHA-256
`d5fe42d445a5ac4fdfabfd2f02b022f6305705106ba3725eeb25aeb6ddb16347`. The full `check executableJar` gate passed:
**2,793 tests in 449 suites**, 2,614 passed,
179 optional skips and no failures/errors. All **18 new tests** passed.
Verification matched 2,054 main class files to
the JAR, 189 guarded pipeline source files,
1144 study schemas and
94,207 indexed output hashes across
661 indexed project copies (the 645
inventory projects plus 16 retained subgroup-checker correction copies). These
counts are software cases, not distinct participant populations. One deliberate MR-output
corruption is separately checked against its recorded before/after hashes and
the fixture's expected native integrity rejection; it is not counted as an intact
producer output. There are no unexpected integrity mismatches.

Concurrent work added only `electrostatic-grid` and `apbs-tools` dispatch/help
entries to the shared CLI entry point after this snapshot. The complete remaining
entry-point text matches the frozen source; the before/after files and exact
hashes are retained separately. This gate verifies the frozen JAR, not a full
build of subsequent concurrent workspace edits.

The current 62-workflow inventory initially passed **61/62**. Its subgroup
verifier expected a waiting native test and participant QC despite supplying no
registered native family or participant datasets. The corrected verifier checks
`not_applicable` separately from the retained partial import, its primary
ineligibility and unchanged source-adjusted values. A fresh exact-JAR rerun
passed **16 cases, 18 phases and 142 independent numerical checks**. Thus all 62
workflow requirements have accepted evidence after the targeted correction;
the initial failure is retained and is not rewritten as a single clean run.
The earlier audit's four stale-verifier failures and the first malformed
comparison-response harness are also retained with their original logs/checkers.

Separate checks passed real public Ensembl consequence retrieval, human TP53
AlphaFold DB retrieval and official AlphaGenome SDK 0.10.0 serialization.
**No live AlphaGenome prediction, installed ANNOVAR run or new AlphaFold
inference was performed.** Prediction requires the corresponding key or local
runtime/databases. No API secret belongs in the study files or manuscript.

The entirely supplied example passed 11 cases (six completions, five intended
holds), with three authored principal findings, 24 bound claims and a complete
linked supplement. These are software fixtures, including synthetic protocol
actors. They do not establish a real human review, biological findings,
publication-ready scientific depth or the validity of source claims. A real
study and scientific review must establish the thoroughness sought in the
Wickenhagen/OAS1 example. Provider execution and this scientific acceptance
remain explicit outstanding evidence; the overall goal is not marked complete.
The source changes remain uncommitted and unreleased.


## Public passage context checkpoint, 2026-10-26

The earlier citation checkpoint transported public bibliography metadata. This
source edition adds explicit, hash-pinned **public passage context** to the
study-wide finding inventory, final AI impact comparisons and native manuscript
drafting. See the [public passage guide](vignettes/study-pipeline-tutorial.md#explicit-public-passages-for-impact-review-and-drafting)
and [context schema](schemas/public-literature-context-v1.schema.json).

`assistance.public_literature_context` pins context files by SHA-256. Each source
must match an available full-text member of a closed native public-reference
bundle, its reference identity, text hash and bundle hash. The reader extracts
explicit UTF-16 ranges rather than accepting replacement prose or arbitrary
source paths. Purpose, hypothesis and exact feature scopes control inclusion;
manuscript background can cover an unselected or null hypothesis. Context source
and passage IDs must be retained together when citing or reviewing the evidence.
Impact-only evidence does not transfer to manuscript requests, and
manuscript-only evidence does not enter impact comparisons.

The context is absent by default. The metadata-only candidate projection still
excludes source prose. The new route sends only explicitly configured public
passages and selected bibliographic fields, with integrity/source hashes and
scope caveats. It does not bypass existing assistance purposes, disclosure,
provider, offline or remote-transmission policies. Provisional literature-cap
allocation and editorial disclosure remain separate protocols. Changing a
context, bundle or hashed bundle member invalidates AI-stage reuse; missing or
changed evidence holds the AI step while retained association results remain.

Native drafting also receives all eight discussion facets explicitly, with
instructions to distinguish study observations, source experiments, inference
and proposed experiments. It must explain insufficient evidence and retain
complete extended discussions in supplements. Selected passages are not a
whole-paper appraisal: figures, controls, supplements, corrections, applicability
and contrary evidence still require scientific review. Integrity, literal
matching and a facet list cannot certify OAS1-level discussion depth or source
support, nor provide de-identification or DLP certification.

### Exact source and verification

Acceptance uses frozen source `build/public-literature-context-source-20261026-r1`
(3,436 files; receipt SHA-256 `89577f1aca1e74dc8175d5d55bdd7e549617be2284766fd6b8bff8f21a2caf85`) and
the exact JAR `build/public-literature-context-acceptance-20261026-r1/software/jlinalg.jar`
(SHA-256 `3d4371c0b4406197e6f8ff623f3a4d91ae8d4fc54c33caca1008808a954a027b`). The full `check executableJar` gate passed
**2,877 tests in 456 suites**: 2,698 passed,
179 optional skips, no failures or errors. All **21 new tests** passed.

Eight exact-JAR packaged workflows passed: public passage context, completed
supplied-study publication, full-pool impact, source-bound discussion review,
supplied-literature reuse, stage resume, provisional literature allocation and
manuscript word budgets. The new public example passed **eight cases, 11 phases
and 12 actual loopback requests**, covering both purposes, purpose isolation,
feature mismatch, changed pins, missing-file arrival, unchanged-input reuse,
changed native text and an explicit budget hold. Canonical meta-analysis bytes
were identical across cases. Verification checked 90 schema
cases, 6,962 indexed operation-output hashes,
2,080 compiled main classes against the JAR and
18 current source/schema/verifier files. Decoded payload strings
were checked against original command-recorded workspace paths.

The example uses the pinned public Wickenhagen et al. text to test transport and
provenance. Its study features, responses and actor labels are synthetic; they
are not OAS1 findings or actual human scientific attestations. The first
prototype checker expected `complete` instead of the actual project state
`completed`; its original checker, histories and captured requests are retained.
The corrected prototype and final frozen-source run passed.

The integration tests also exposed YAML aliases when a reference object appeared
in both the passage packet and source catalog. The assistance writer now emits
independent containers, preserving the strict reader's alias rejection. An
identical-value old/new JAR comparison reproduces the old reader failure and
verifies unchanged values after the new writer round trip; both JARs and the
probe are retained. Its first probe-harness signature error is retained separately.

These checks execute no external model or predictor and do not certify
scientific appraisal, source support, biological validity or manuscript quality.
Live AlphaGenome/ANNOVAR execution, new AlphaFold inference and real-study
scientific acceptance remain unverified. The full pipeline goal remains active;
this source-build checkpoint is uncommitted and unreleased.


## Scoped public evidence citation checkpoint, 2026-10-26

The previous public-passage checkpoint verified transport into model requests.
A stronger response test exposed a validation gap: the model could receive a
public passage source ID but its response was rejected for citing that ID as
support or counterevidence. The validator previously admitted only the finding's
analysis, literature-coverage, annotation and LD sources.

The source build now admits a public passage source in `evidence_ids` and
`counterevidence_ids` when the context is complete, the packet catalog contains
its `public_literature_passages` record, and the source matches that individual
finding's hypothesis and exact feature scope. Declared hypothesis background
can apply across its features. A shared batch catalog cannot lend a source to
another hypothesis or feature. Invented IDs, absent catalog records and
unresolved contexts remain errors. Required caveats, complete candidate
assessment, distinct story groups and statistical eligibility are unchanged.
Accepting an ID establishes supplied provenance, not scientific support for a
claim. See the [public passage guide](vignettes/study-pipeline-tutorial.md#explicit-public-passages-for-impact-review-and-drafting).

The new prompt explains this scope rule, and implementation identity 85
invalidates earlier execution-cache identities. No new study configuration is
required. The source-citation relationship remains an unreviewed AI assertion;
the existing claim ledger and scientific appraisal still apply.

If the impact source scope exceeds the drafting scope, the manuscript projection
also omits upstream AI appraisal prose and its outline copies. Removing only
source IDs or literal quotations would not catch paraphrases. Selected finding
identities, order, statistical dossiers and caveats still transfer, and drafting
reassesses claims from its permitted evidence. The full original impact audit
remains local. The exact-JAR example injects derived appraisal text, verifies
its absence from the impact-only draft, and verifies retention when both stages
permit the source. A retained run with the earlier JAR and the strengthened
responder reproduces both a dangling public-source citation and derived prose
in its impact-only manuscript request, despite an empty public-source catalog.
That expected verifier failure is archived alongside the passing new run.

### Exact source and acceptance

Frozen source `build/public-evidence-citation-source-20261026-r3` contains
**3,458 files**, receipt SHA-256
`865c15f0856f10225dcb83320b28c5b422ba9842ccf5a469cbac433b91e7f910`. The exact JAR is in
`build/public-evidence-citation-acceptance-20261026-r3/software/jlinalg.jar`,
SHA-256 `dde797912f76db076f74a8794876d6441d5bc61bf66318112d2cf75df146187d`. Its full `check executableJar` gate passed
**2,931 tests in 460 suites**: 2,752 passed,
179 optional skips and no failures/errors. All **ten new tests**
passed, covering valid support/counterevidence, feature and hypothesis scope,
missing catalog records, unresolved context, invented IDs and declared background.
Three additional tests cover the drafting projection: impact-only derived prose is omitted, matching dual-purpose appraisals remain, and a dual-purpose source outside the selected drafting scope also triggers projection. The strengthened native handoff test injects paraphrased source text into every rubric, claim scope and selection note. The original local audit retains those appraisals. The existing native transport test now returns public IDs in both citation fields.

Eight exact-JAR workflows passed: public context, completed supplied-study
publication, impact, source-bound discussion, supplied literature, resume,
provisional literature priority and manuscript word budgets. The public-context
example passed eight cases and 11 phases, with **12 actual loopback requests and
eight public-source citations in responses**. Its responder actually uses the
provided IDs; the verifier checks their scopes and acceptance. Canonical
meta-analysis bytes were identical across the cases.

An old/new comparison used the same previously captured native packet and
identical response files. The old JAR rejected valid support and counterevidence;
the new JAR accepted both. Both JARs rejected wrong-feature and invented-source
citations. The packet SHA-256 is
`27683fa8a64cb29a26096a431e1c4d9c93f4dfb32754cd2a173992b821785a52`;
the response-set SHA-256 is
`71a26ce7b81c3447342b42ec0e817174346e1407f735bc1898c29055258489f1`.
Both JARs, the packet, replies, probe and results are retained. Verification also
checked 90 schema cases,
6,962 indexed output hashes,
2,085 main class files against the JAR and
9 guarded current source/test/verifier files.

### External provider observation and scientific limits

The [official AlphaGenome downloads page](https://deepmind.google.com/science/alphagenome/downloads) returned HTTP 200 and listed an AVI
score archive. Bounded anonymous HEAD and range-GET requests to that archive
both returned HTTP 500 on 2026-10-26. The original page, hashes, response metadata
and error bytes are retained under `validation/external-download-observations`.
This observation does not identify the upstream/network cause, establish a
credential error, or constitute downloaded variant scores. No bulk-file format
was guessed and no predictor result was claimed.

The real public Wickenhagen text tests transport and provenance; study data,
responses and actor labels are synthetic. These checks do not perform actual
external model appraisal, new AlphaFold inference, AlphaGenome/ANNOVAR execution
or scientific acceptance of a real manuscript. Source-ID acceptance cannot
certify that a paper supports a claim. The full pipeline goal remains active;
changes are uncommitted and unreleased.


## Application-managed variant software checkpoint, 2026-10-26

The application now downloads and installs variant software for its users through
`variant-tools install alphagenome` and `variant-tools install annovar`, with
machine-readable `variant-tools status` commands for frontend integration.
The [managed setup guide](vignettes/variant-followup.md#application-managed-software-download-and-installation)
contains commands, automatic study settings and registration handling.

AlphaGenome setup downloads the official **0.10.0** PyPI wheel, checks pinned
SHA-256 `ed90696f7f0900b2e0d68e28e36ab7d6d95000f59cd19b969fec0bea806bd2c2`,
resolves binary dependency wheels, records every version/hash and installs from
the local locked wheel set into a private environment. It checks dependencies
and the Atlas/prediction imports. The system Python package set is unchanged;
Python 3.10 or newer is the bootstrap prerequisite. This installs the official
client, rather than model weights or credentials. Installing it makes no model
request and needs no AlphaGenome API key.

The default home is `~/.jlinalg/tools/alphagenome-0.10.0` or `annovar`, shared by
the setup command and study execution; `--home` and study `installation.home`
override it. Complete homes are verified and reused. Missing, changed or extra
files invalidate a managed profile; incomplete homes and diagnostics are retained
instead of silently repaired or overwritten. Runtime identities participate in
scientific execution-cache inputs.

Native study configuration accepts `variant_followup.alphagenome.installation`
and `variant_followup.consequences.installation`. SDK setup defaults to automatic
when no external Python is supplied; ANNOVAR setup defaults to automatic when
its engine is selected without an external executable. Other consequence
engines keep external setup. Explicit external mode preserves prepared runtimes.
Permitted automatic software downloads use the existing resource-download
policy. A missing API key holds a model request without downloading an unusable
SDK during that analysis; the independent install command still works without
credentials. A complete managed home or registered local ANNOVAR archive can be
used with software downloads forbidden.

ANNOVAR's [official download process](https://annovar.openbioinformatics.org/en/latest/user-guide/download/)
requires individual registration and licence acknowledgement. The application
returns the registration URL, accepts the user's issued link through a named
environment variable or an already registered archive, and performs the download
and extraction itself. It never invents registration or redistributes someone
else's licensed copy. Issued URLs are excluded from manifests and HTTP error
messages. Downloads and extraction are bounded, checksums recorded and archive
traversal/links rejected. Perl and chosen annotation databases remain separate
declared prerequisites.

### Source and executable evidence

Frozen source `build/managed-variant-tool-source-20261026-r1` has
**3,515 files**, receipt SHA-256 `7977f6809409c2cb64d97893c1e0d0072cb5f23c2a15512c736b766840a4b818`.
Final JAR: `build/managed-variant-tools-20261026-r1/software/final-jlinalg.jar`,
SHA-256 `ee0e6d11631550b1fee6d7072c632d4712afbea9ea8d4a0f4daaf41bbd702b0e`. Engine implementation identity is **86**, resolver
defaults **53**. Full `check executableJar` passed **2,963 tests in
464 suites**: 2,784 passed, 179 optional skips and
no failures/errors. All **13 new Java tests** and **eight Python
installer tests** passed. Eleven packaged regressions passed: public context,
completed supplied study, impact, discussion, supplied literature, resume,
literature priority, word budgets, variant follow-up, pooled variants and partial
supplied stages.

The application itself downloaded and installed AlphaGenome, including
**50 dependency packages** and
**8,965 managed runtime files**. A second install with
downloads disabled reused the same marker. Actual SDK imports through the
packaged bridge succeeded. That download used JAR
`be97a3457473c11faf9e3f106df02c74dc569a9710d723644e1d84240801f3b9`. Its installer/runtime/workflow class files
and packaged installer/SDK resources were compared byte-for-byte with the final
JAR; final-JAR status and offline reuse then passed against the same installation.
This preserves the actual download evidence without needlessly downloading the
unchanged dependency set again.

The ANNOVAR local-archive integration uses a clearly labelled synthetic archive,
not genuine ANNOVAR software or a fabricated registration. It proves the
Java-to-packaged-installer path, checksum, archive layout, licence gate and
offline import behavior. An actual personally registered ANNOVAR download and
new model predictions were not performed. Those are separate live integration
checks, not a demand for users to provide a real study before this software
feature can be implemented or installed.

The CLI provides the application-facing setup boundary. A separate OmicsMatic
checkout was not available here, so a separate GUI button is not claimed as
verified. Changes remain local and uncommitted; no release was published.


## Software workflow acceptance, 2026-10-26

The original pipeline, lab amendments and application-managed variant downloads
have implemented and contract-verified software paths in this checkout. This
acceptance covers the documented models, formats, resources and capability
gates. It does not promise support for every possible study design or certify
biological conclusions. User inputs, provider credentials and licensed resources remain requirements
when the corresponding operation is run. Software setup and implementation
validation can be completed independently of a particular research study.

The requirement audit is retained at
`build/software-completion-audit-20261026-r1/software-requirements-audit.json`.
It checks 25 software facets and 139 pinned evidence files. The earlier 62-workflow
acceptance preserves its initial 61/62 outcome and the separately recorded
16-case subgroup-verifier correction with 142 independent numerical checks.
Eleven packaged regressions were rerun against the current verified JAR. The
full gate passed 2,784 tests with 179 optional skips and no failures/errors.
The nine changed files among 189 original pipeline guards concern public-source
handling, software setup and execution identities; unchanged compiled entry
points retain their earlier verified implementations.

A subsequent shared-dispatcher edit adds only the disjoint `unrelated-select`
route and its help entry. Its exact diff was checked, the current dispatcher
and dependencies compiled at Java 17, and study/setup/status/offline-reuse
commands passed against the verified runtime. That independent command itself
is outside this pipeline acceptance; concurrent work was preserved.

| ID | Implemented software facet | Representative accepted workflows |
| --- | --- | --- |
| P01 | Per-cohort association and missing-model literature/error gate | `model-review`, `genotype-plain`, `survival` |
| P02 | Consistency, assay/sample QC, distributions and subgroup diagnostics | `assay-qc`, `genotype-qc`, `genotype-sample-qc` |
| P03 | GWAS MAC >=20 and separate uncommon/rare-variant routes | `genotype-plain`, `genotype-qc`, `human-ploidy` |
| P04 | Effect-outlier exclusion, raw retention and unfiltered sensitivity | `effect-qc`, `genotype-effect` |
| P05 | Matched cohort effect plots, correlations and direction | `cohort-diagnostics`, `pooling`, `genotype-plain` |
| P06 | Discovery/validation roles and separate discovery pooling | `frozen-discovery`, `discovery-scope`, `genotype-plain` |
| P07 | Cohort overlap, odd-cohort flags and omission sensitivity | `cohort-diagnostics`, `rare-cohort-overlap`, `effect-qc` |
| P08 | Protocol primary meta-analysis, heterogeneity and leave-one-out fits | `pooling`, `cohort-diagnostics`, `primary-results` |
| P09 | GO/pathway and LD-aware GWAS enrichment | `gwas-enrichment`, `continuous-gwas-enrichment`, `gsea` |
| P10 | All eligible SNP consequences and contextual AlphaGenome scoring | `variant-followup`, `pooled-variant`, `partial-supplied` |
| P11 | AlphaFold structure retrieval, exact mapping and configured prediction | `protein` |
| P12 | Risk-score development/reuse, independent evaluation and related traits | `risk-score`, `binary-risk-score`, `nested-risk-score` |
| P13 | GSEA, pathway networks, modules and module GO | `gsea`, `phenotype-gsea`, `network` |
| P14 | Correlation networks, components/cliques and module GO | `network`, `network-preservation`, `network-null` |
| P15 | Matched GWAS/QTL source acquisition and all-eligible-feature MR | `mr-feature-coverage`, `mr-testing`, `mr-completion` |
| P16 | Colocalization, including explicitly limited thresholded regions | `colocalization`, `genetic-pair-reuse` |
| P17 | Literature review cap 100, uncapped inventory and provenance | `literature`, `literature-priority`, `literature-coverage` |
| P18 | Full-pool AI impact comparisons and 3–5 distinct principal stories | `impact`, `finding-grouping`, `completed-study` |
| P19 | Source-bound discussion facets, evidence/claim ledger and review states | `finding-discussion`, `completed-study` |
| P20 | Manuscript, flexible 8,000-word main target and full supplements | `manuscript-depth`, `completed-study`, `impact` |
| P21 | Completed supplied stages and requested missing-work completion | `supplied-results`, `scoped-supplied`, `completed-study` |
| P22 | Partial-stage scope, retained source evidence and primary ownership | `partial-supplied`, `primary-results`, `scoped-supplied` |
| P23 | Public citation/passage handoff with feature and purpose scope | `completed-study`, `literature-coverage`, `supplied-literature` |
| P24 | Immutable inputs, explicit needs, scoped cache reuse and provenance | `stage-resume`, `genetic-pair-reuse`, `completed-study` |
| P25 | Application-managed AlphaGenome and registered ANNOVAR downloads | Managed installer acceptance, 13 Java + 8 Python tests |

The source/JAR, software download and installed-runtime checks are recorded in
[the managed software checkpoint](#application-managed-variant-software-checkpoint-2026-10-26).
The remaining tables and dated checkpoints retain the development history,
capability extensions and separate live/scientific evidence limits. They must
not be read as evidence that an earlier partial result was regenerated.

Actual AlphaGenome predictions, a genuine personally licensed ANNOVAR execution,
new AlphaFold inference, real-model scientific appraisal and scientific manuscript
certification remain unperformed by these checks. Those flags remain false in
the audit. The framework provides their configured interfaces and review gates;
it does not turn fixture outputs into biological findings. A separate
OmicsMatic GUI checkout was unavailable; the verified application boundary here
is JLinAlg's CLI and JSON setup status. No release, commit or push is claimed.
