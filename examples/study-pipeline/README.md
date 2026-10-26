# Auditable result-first study example

For a separate conditional permutation test of frozen graph wiring, see
[the network-null guide](../../docs/vignettes/study-pipeline-tutorial.md#conditional-tests-of-frozen-network-wiring).

```powershell
python -B examples/study-pipeline/network-null-workflow.py run build/network-null-example --jar build/cli/omicsmatic-0.3.7.jar
```

It uses synthetic native/supplied graphs, independent exact enumeration,
Monte Carlo precision checks, complete-family correction, supplied-stage reuse
and missing/revised strata. Its label null is conditional on the declared
exchangeability model and does not certify biological preservation.

For native fitting while downstream MR inputs are missing, see
[stage-specific resume](../../docs/vignettes/study-pipeline-tutorial.md#resume-preparation-while-downstream-inputs-arrive).

```powershell
python -B examples/study-pipeline/stage-resume-workflow.py run build/stage-resume-example --jar build/cli/omicsmatic-0.3.7.jar
```

The offline synthetic example checks deferred source arrival, reuse across
reporting and unrelated-hypothesis amendments, invalidation after scientific
input changes, immutable prior attempts and independent SciPy OLS comparisons.

For discovery cohorts represented by supplied pools, with no original cohort
files, see [the reference and scope guide](../../docs/vignettes/study-pipeline-tutorial.md#discovery-cohorts-represented-by-supplied-pools).

```powershell
python -B examples/study-pipeline/discovery-scope-workflow.py run build/discovery-scope-example --jar build/cli/omicsmatic-0.3.7.jar
```

It checks missing native validation, exact source membership/trait contracts,
overlap and effect/coding gates, strict completeness, source-file failures and
an all-cohort fit that must wait for actual cohort estimates.

For supplied discovery pools and missing validation, see
[frozen discovery families](../../docs/vignettes/study-pipeline-tutorial.md#preserve-supplied-discovery-fdr-during-validation).
The offline regression example runs with:

```powershell
python -B examples/study-pipeline/frozen-discovery-workflow.py run build/frozen-discovery-example --jar build/cli/omicsmatic-0.3.7.jar
```

It independently checks discovery and validation adjustment, effect direction,
sealed source families, cache reuse and a held unresolved export. All data and
scientific declarations are synthetic.

For completed external sample QC/PCA and missing native PCA, see
[the scoped reuse guide](../../docs/vignettes/study-pipeline-tutorial.md#reuse-external-sample-qc-and-pca-and-complete-missing-work).
The offline acceptance runs with:

```powershell
python -B examples/study-pipeline/external-genotype-qc-workflow.py run build/external-qc-example --jar build/cli/omicsmatic-0.3.7.jar
```

It checks exact source/sample binding, faithful PC reuse, explicitly selected
native completion, mixed cohort sources, immutable replacement source editions,
budget amendments and aggregate unsent impact evidence. It does not establish
actual PLINK execution or certify scientific QC.

For observed genotype call rates, KING relatedness and unrelated-basis projected
PCs, see the [registered sample-QC guide](../../docs/vignettes/study-pipeline-tutorial.md#registered-genotype-sample-qc-and-projected-pcs).
The offline synthetic acceptance runs with:

```powershell
python -B examples/study-pipeline/genotype-sample-qc-workflow.py run build/sample-qc-example --jar build/cli/omicsmatic-0.3.7.jar
```

It checks imputed versus observed calls, complete pair counts, PCA weights and
projection, downstream associations, supplied PCs, held prerequisites and
aggregate unsent impact payloads. These checks do not certify a real cohort.

For registered adjusted feature-by-group tests and typed supplied interaction
results, see the [subgroup interaction guide](../../docs/vignettes/study-pipeline-tutorial.md#registered-adjusted-subgroup-interactions).
The offline synthetic acceptance runs with:

```powershell
python -B examples/study-pipeline/subgroup-interactions-workflow.py run build/subgroup-example --jar build/cli/omicsmatic-0.3.7.jar
```

It checks classical/HC3 linear tests, ordinary/Firth logistic likelihood ratios,
joint correction, group-specific MAC, held prerequisites, external result reuse,
amended work budgets and aggregate impact payloads. It makes no model requests.

For native MR, colocalization and fine-mapping pair completion, see
[the pair reuse guide](../../docs/vignettes/study-pipeline-tutorial.md#resume-completed-native-genetic-pairs).
The offline packaged acceptance example runs with:

```powershell
python -B examples/study-pipeline/genetic-pair-reuse-workflow.py run build/pair-reuse-example --jar build/cli/omicsmatic-0.3.7.jar
```

It pins the executable and scripts before execution, retains all attempts, and
checks pair ownership, immutable inputs, current correction families and
independent numerical calculations. All source approvals are synthetic.

This small project starts from two supplied association-result tables. It runs
input validation and standardization, a cross-cohort effect scatterplot with
four-decimal Pearson correlation, discovery/replication comparison and Venn
diagram, fixed-effect meta-analysis, deterministic diagnostics, and a traceable report.
The explicit cohort relationship avoids a pending default assumption. This tiny
offline teaching fixture uses `analysis.mode: manual` so it does not download
public annotation or enrichment resources.

From the repository root, first build the executable JAR:

```powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
```

Copy this directory before running it if you want to keep the checked-in example
clean. In the copied directory, run:

```powershell
$jar = "D:\path\to\JLinAlg\build\cli\jlinalg-0.3.7.jar"
java -jar $jar study auto study.yaml --project . --actor-id scientist-1
```

The run creates `resolved-study.yaml`, `plans/`, `runs/`, the hash-chained
`ledger/events.jsonl`, derived `project-status.yaml`, `SESSION_HISTORY.md`,
`AGENTS.md`, searchable `index/index.html`, machine-readable
`index/evidence-summary.json`, and `index/LLM_CONTEXT.md`. Re-run the same command
to verify and reuse completed operation artifacts and retry unfinished work.
Each invocation keeps a separate immutable attempt; use `--rerun` to recompute
everything without deleting earlier evidence, or `--offline` to prohibit built-in
network requests. The index defaults to the latest attempt.

The run's operation directories include `effect-comparisons.tsv` and
`effect-scatterplots/*.png|pdf`, plus `replication-venn.tsv` and
`replication-venn/*.png|pdf`. In a hypothesis with several result datasets,
every pair is plotted. Reusing one cohort ID for two phenotype-specific result
datasets labels that pair `within_cohort`; distinct cohort IDs label it
`cross_cohort`.

When a supported rule needs scientist input, the run also creates `QUESTIONS.md`
and `state/questions.yaml`, with the exact questions and answers reflected in the
session notebook and searchable index. Inspect with `study questions --project .`;
export blank answers with `--out answers.yaml`. Check and record answers using
`study answer answers.yaml --project . --actor-id scientist-1 --check` or
`--record`. Supported factual answers produce proposed amendments requiring
separate review and approval. This minimal example may have no questions;
genetic follow-up or missing matrix metadata exercise the first rules. See the
[question workflow tutorial](../../docs/vignettes/study-pipeline-tutorial.md#documented-scientist-questions).

`data-dictionary.yaml` supplies the column roles and explanations for both result
files. Its contents and hash are snapshotted when the study is resolved. To
change a dictionary later, version it and approve a dataset amendment.

Optional API advice, literature review and manuscript drafts require explicit
configuration and permission; this fixture enables none of them. See the
[tutorial](../../docs/vignettes/study-pipeline-tutorial.md#data-dictionaries-optional-assistance-and-first-drafts)
for privacy settings, recorded interactions, and preapproved local sensitivities.

For a real end-to-end project, omit `mode: manual` or set `mode: auto`. Explicit
goals then become a minimum rather than a ceiling: OmicsMatic adds eligible
meta-analysis, functional enrichment, genetic-evidence, literature, diagnostics,
and reporting branches. Public reference downloads default to automatic; an
unavailable scientific input is indexed as a blocker while independent branches
continue.

For `study auto`, exit 0 includes a successfully indexed partial run with input
requests; inspect its recorded state. Exit 2 indicates validation/execution
failure. `study run` additionally returns 3 for waiting operations unless
`--allow-partial` is supplied.

## Participant-level genotype verification example

From the repository root, generate a new synthetic project and run the built
CLI. The generator refuses an existing output directory. It uses only Python's
standard library; no participant data or external services are involved.

```powershell
python examples\study-pipeline\generate-genotype-workflow.py build\genotype-workflow-example
$jar = (Resolve-Path build\cli\jlinalg-0.3.7.jar).Path
java -jar $jar study auto build\genotype-workflow-example\study.yaml --project build\genotype-workflow-example --actor-id synthetic-check --offline
java -jar $jar study verify --project build\genotype-workflow-example
python examples\study-pipeline\verify-genotype-workflow.py build\genotype-workflow-example --jar $jar
```

The 14-operation example fits three synthetic cohorts with explicit OLS models,
two discovery cohorts and one validation cohort. Each cohort has 64 genotype
samples; phenotype missingness and a sample call-rate failure leave 62 analyzed
participants. The default single-variant filter excludes MAC 19 and retains
MAC 20. Both discovery-only and all-cohort fixed-effect estimates are checked
against independently reconstructed inverse-variance averages. Validation uses
the discovery-only estimates; the all-cohort pool contains three original
cohorts. Distributions and declared subgroup diagnostics export PNG/PDF.

The verifier also checks source hashes and writes `packaged-verification.json`.
Use `--run-id ID` after multiple attempts. `study verify` separately checks the
study ledger and artifact hashes. This example establishes the stated local
boundaries; it does not exercise rare-set integration, service adapters or the
complete analysis specification.

This focused fixture explicitly sets `rare_variants.enabled: false`. Automatic
genotype studies otherwise plan the applicable rare-set branch, whose
annotation/mask prerequisites are independent of the single-marker scan. Manual
studies request rare-set work through their goals or explicit configuration.

Add `--effect-qc` to both genotype generator and verifier commands to exercise
an explicit absolute-effect bound and a common-feature intersection. This
retains the sample and MAC checks while verifying that only the separate
unfiltered effect sensitivity recovers the excluded `v40` result.

## Imported-result effect-QC sensitivity example

This separate fixture has four cohorts, three designated for discovery. It
includes one extreme effect, an invalid SE and a feature absent from validation.
From the repository root:

```powershell
python examples\study-pipeline\effect-qc-workflow.py generate build\effect-qc-example
$jar = (Resolve-Path build\cli\jlinalg-0.3.7.jar).Path
java -jar $jar study auto build\effect-qc-example\study.yaml --project build\effect-qc-example --actor-id synthetic-check --offline
python examples\study-pipeline\effect-qc-workflow.py verify build\effect-qc-example --jar $jar
```

The verifier runs `study verify`, checks unchanged source hashes, and confirms
30 primary versus 31 unfiltered pooled features. Invalid estimates stay excluded
from both branches. Discovery-only testing retains its own coverage; cohort
omissions pool the remaining discovery cohorts before validation. Exact testing
denominators and the primary-versus-sensitivity differences are retained in
`effect-qc-unfiltered/`. This is a synthetic lineage check, not scientific
validation of the heuristic or a biological finding.

## Native and unsigned rare-set examples

These new-directory fixtures run without downloads or real participant data:

```powershell
python examples\study-pipeline\rare-variant-workflow.py generate build\rare-native-example
python examples\study-pipeline\rare-variant-workflow.py generate build\rare-unsigned-example --kind unsigned
$jar = (Resolve-Path build\cli\jlinalg-0.3.7.jar).Path
java -jar $jar study auto build\rare-native-example\study.yaml --project build\rare-native-example --actor-id synthetic-check --offline
java -jar $jar study auto build\rare-unsigned-example\study.yaml --project build\rare-unsigned-example --actor-id synthetic-check --offline
python examples\study-pipeline\rare-variant-workflow.py verify build\rare-native-example --jar $jar
python examples\study-pipeline\rare-variant-workflow.py verify build\rare-unsigned-example --jar $jar
```

The native example has three 1,000-participant cohorts, predeclared consequence
masks and all five supported tests. Its verifier checks MAC 1/19 set retention
versus the MAC 20 single-marker boundary, quality/frequency exclusions, unsigned
semantics and unchanged inputs. It independently reconstructs discovery-only
and all-cohort equal-weight burden beta/SE/P, plus every cohort omission, from
exported U scores and V/N covariances. It independently checks full-family BH
within every omission and verifies that the primary table retains its hash.
It uses 1,000 SKAT-O simulations for a small teaching fixture;
production tail resolution requires a justified budget/calibration.

The P-only example checks full-family correction, cohort-omission ACAT values
against a separate Cauchy calculation, and unavailable validation coverage
without fabricated beta or direction. Both verifiers run `study verify`
and retain an exact tested JAR plus verifier source under the project, with
hashes in `packaged-verification.json`. `--run-id` can select an earlier attempt.
These examples verify the named numerical/input contracts, not binary-model
calibration, external annotation services or complete pipeline conformance.

## Literature selection and offline disposition

```powershell
python examples\study-pipeline\literature-workflow.py generate build\literature-selection-example
java -jar build\cli\jlinalg-0.3.7.jar study auto build\literature-selection-example\study.yaml --project build\literature-selection-example --actor-id synthetic-check --offline
python examples\study-pipeline\literature-workflow.py verify build\literature-selection-example --jar build\cli\jlinalg-0.3.7.jar
```

This fixture imports 225 synthetic association results, retains all 125 eligible
mapped significant features and selects the first 100 for literature retrieval.
Its independent verifier reconstructs BH over all 225 tests, checks the order,
25 out-of-budget records, source hashes and the offline gate. Every selected
search remains incomplete; no network request or scientific appraisal is
claimed. The exact JAR and verifier are retained in the output bundle. Mock
transport tests separately cover successful 100-signal retrieval beyond the old
200-query ceiling, strict pooled-family FDR and explicit budget exhaustion.

## Native result metadata verification

After running the generated genotype example, use the additional verifier:

```powershell
python examples\study-pipeline\verify-result-metadata.py build\genotype-workflow-example --jar build\cli\jlinalg-0.3.7.jar
```

It compares 207 retained native metadata values with the raw association output,
including counts, frequencies, alleles, HWE method/results and model diagnostics.
It checks unchanged backend statistical fields after meta-analysis augmentation.
Missing source N stays unavailable rather than being reconstructed from the
62-person participant-flow tables. `metadata-verification.json` retains an exact
executable and verifier-source hash; use the pinned verifier for historical
bundles. This supplements the genotype numerical-lineage verifier.

## Primary MR testing and source coverage

```powershell
python examples\study-pipeline\mr-testing-workflow.py run build\mr-testing-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\mr-testing-workflow.py run build\mr-testing-pending-example --jar build\cli\jlinalg-0.3.7.jar --missing-source
python examples\study-pipeline\mr-testing-workflow.py verify build\mr-testing-example --jar build\cli\jlinalg-0.3.7.jar
```

These offline fixtures retain four eligible molecular features, three estimable
MR tests, an exact duplicate source alias and either an exhausted fourth
instrument family or a missing reviewed source. The standard-library verifier
independently reconstructs allele-aligned Wald/IVW estimates and BH using the
four-test denominator. It checks that absent P values remain unavailable and
that a pending family cannot certify primary significance. It also verifies
source sample-size propagation, raw input hashes, the exact executable and the
pinned verifier source. Existing directories are refused. Use a historical
bundle's pinned verifier when its source differs from the current script.
No external requests, human attestations or scientific appraisal occur.

## Compatible scalar effects and sampling dependence

```powershell
python examples\study-pipeline\pooling-workflow.py run build\pooling-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\pooling-workflow.py run build\pooling-missing-pair-example --jar build\cli\jlinalg-0.3.7.jar --missing-pair
```

These offline synthetic projects use two dependent discovery sources and a
separate validation source. A standard-library scalar reference reconstructs
GLS weights, Paule–Mandel heterogeneity, modified-Hartung–Knapp conditional
uncertainty, intervals/tests and frozen validation families. Missing source
estimates use actual matrix subsets. All cohort-omission estimates and weights
are independently reconstructed. The incomplete-pair case must wait without
inventing independence or deleting a contributing source. Both retain source
hashes and pin their exact executable/verifier; no biological covariance or
independence is established by the fixture.

## Variant consequences and AlphaGenome bridge

```powershell
python examples\study-pipeline\variant-followup-workflow.py run build\variant-followup-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\variant-followup-workflow.py verify build\variant-followup-example --jar build\cli\jlinalg-0.3.7.jar
```

This offline fixture checks annotation of nonsignificant variants, exact
canonical matching across import batches/builds, transcript/tissue/scorer
retention, negative signed quantiles, explicit missing-score coverage and
preserved excluded rows. It verifies unchanged source hashes, runs
`study verify`, and pins the executable and verifier source. Its score values
are synthetic software fixtures and establish no biological prediction accuracy.

With the official AlphaGenome SDK installed in a separate environment, run
`verify-alphagenome-bridge.py --out NEW_DIRECTORY` using that environment's
Python. It checks actual SDK `AnnData` serialization, full raw arrays/layers,
signed values, tissue-filtered exports, output budgets and allele mismatches
without making an API request. Credentials and a separate real-service check
are needed to validate prediction execution.

`variant-vep-live-workflow.py run NEW_DIRECTORY --jar JAR` separately retrieves
the public mitochondrial reference, submits one public variant to Ensembl and
independently reconstructs its transcript consequences and release metadata.
Its association statistics are synthetic. `verify` performs local integrity
checks without re-querying Ensembl. It tests real consequence retrieval, not
AlphaGenome prediction or installed ANNOVAR/VEP.

## Study-wide impact protocol and manuscript outline

```powershell
python examples\study-pipeline\impact-workflow.py run build\impact-protocol-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\impact-workflow.py verify build\impact-protocol-example --jar build\cli\jlinalg-0.3.7.jar
```

The standard-library harness starts and closes a deterministic loopback HTTP
fixture in one process. It imports 30 synthetic findings across two hypotheses,
checks all 26 eligible candidates through leaf and cross-batch comparisons,
produces a three-finding outline and exercises the manuscript draft/word gate.
It also creates a native manuscript revision beside the source project, links
the same finding IDs into Results and Discussion, and verifies its selection
record. It independently reconstructs draft word counts and each full-family
BH result, verifies immutable
inputs and preserves the exact JAR, verifier source and request audit. A sentinel
in private project/dataset prose must never enter a request. Actor provenance
identifies the external synthetic harness; no browser-human attestation is used.

This fixture is not an AI model, source appraisal, scientific-impact benchmark
or submission-ready manuscript. Real provider/model behavior and scientific
review are separate acceptance work. The source directory and its sibling
`-native-manuscript` revision bundle must both be new. An existing bundle is
refused. `verify` needs no running server.

## Manuscript depth, flexible targets and complete supplements

`manuscript-depth-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
creates an offline synthetic association project and native manuscript revisions.
It verifies complete supplementary prose, source/value bindings, separate word
counts, retention of a draft above the default target, enforcement of an explicit
strict limit and unchanged source/ledger hashes. Use `verify DIRECTORY --jar JAR`
to repeat the checks against the bundle's pinned executable/verifier. The repeated
prose tests length/retention only; it does not assess real AI discussion quality
or implement arbitrary later-stage result imports.

## Supplied analyses and completion of missing work

`supplied-results-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
creates three offline projects. The all-supplied case imports meta-analysis,
GO, networks/modules, MR, partial-region colocalization and unsigned rare-set
results, then verifies cache reuse without duplicate computations. A second
case imports meta-analysis and runs missing native GO against a project-local
synthetic resource; its P value and BH correction are independently rebuilt.
The selected-only case preserves the original source q=.4 and denominator
400,000, so its nominal P=1e-6 cannot become a new discovery or AI candidate.
`verify DIRECTORY --jar JAR` repeats integrity checks with the pinned software.
Original inputs are immutable. No network request or real-model/scientific
appraisal is performed, and this fixture is not full pipeline conformance.

## GWAS QQ, Manhattan and source calibration context

`gwas-diagnostics-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
exercises native OLS and two-cohort pooled results, complete/unknown/selected
exports, natural-log underflow and unresolved/conflicting P values, coordinate
gaps/mixed builds, display/row bounds and typed supplied diagnostic reuse. It
independently reconstructs QQ order positions, P-transformed chi-square medians,
cumulative coordinates and native OLS estimates with NumPy/SciPy; it checks
source hashes, cache reuse, bounded aggregate finding context and immutable
original results. `verify DIRECTORY --jar JAR` uses the pinned software. All
inputs are synthetic; no genomic-control correction, biological/model
certification or real AI appraisal is performed.

## Scoped reuse and missing cohort networks

`scoped-supplied-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
exercises supplied A graphs with missing native B work, multiple supplied and
pooled graphs, metadata-only references and an explicit supplied-only option.
It checks scientific scope and row/dependency conflicts, preserves independent
downstream imports with missing upstream files, and distinguishes external MR
and score populations. NumPy/SciPy reconstruct signed residual correlations and
hypergeometric/joint-BH module GO. Original source prose remains local while
automatic Methods/dossiers and unsent impact evidence use aggregate facts.
Use `verify DIRECTORY --jar JAR` with the pinned software. All data/resources
are synthetic and project-local; no biological or real-AI appraisal is claimed.

## Native and supplied networks, modules and joint GO

`network-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
exercises native covariate-adjusted molecular correlations, supplied module
reuse with missing GO, pathway overlap from supplied GSEA, a bounded
incomplete-clique case that must leave GO waiting, and empty-pathway/no-GO-test
dispositions. It independently reconstructs
signed orthogonal correlations, Jaccard overlap and hypergeometric/BH values;
checks source caveats, GraphML, unchanged input hashes and native cache reuse;
and pins the executable/verifier. `verify DIRECTORY --jar JAR` repeats integrity
checks. All resources are synthetic and explicitly project-local. No biological
validation, real AI appraisal or full pipeline conformance is claimed.

## Native preranked GSEA and automatic pathway/module completion

`gsea-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar` runs eight
offline cases: exact complete rankings, BY preservation, finite Monte Carlo,
supplied complete meta-analysis with nonsignificant genes, selected rankings,
ties, requested phenotype null and zero eligible terms. Its independent Python
full-list scans/exhaustive nulls rebuild ES/NES/tails/leading edges and BH/BY;
verify pathway overlap, complete joint module GO and curve data/PNG/PDF; and
check unchanged source hashes and resume. Monte Carlo is checked against the
exact reference within a declared six-standard-error bound. Software is pinned
before execution, including failed runs; `verify DIRECTORY --jar JAR` requires
the original pinned executable and verifier and cannot relabel old data as
acceptance of a new executable. No biological/real-AI or Broad/fgsea/R-parity
claim is made. All resources are explicitly project-local synthetic fixtures.

## Held-out scores and missing related-phenotype analyses

`risk-score-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
runs ten offline synthetic cases. It requires NumPy, SciPy and PyYAML. Independent
calculations reconstruct baseline/ridge fitting, training-only means, tuning,
cluster bootstrap intervals, CR1 t-tests and the complete BY related-trait
family. Cases include direct cohort matrices, complete frozen-model reuse,
diploid allele swaps, raw versus probability binary evaluation, supplied metrics
with only missing related-trait tests, group overlap, an incomplete related
family and an unsupported model. It checks source hashes, native cache reuse and
`study verify`. Software is pinned before execution; `verify DIRECTORY --jar JAR`
requires the same executable and verifier. Numerical checks do not establish
clinical/biological validity, real AI appraisal or R/other-risk-tool parity.

## Protein context, supplied structures and configured prediction

`protein-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar [--live]`
runs eleven synthetic supplied-model/summary/assessment, confidence, directional
PAE, partial-sequence, wrong-input, configured-process and primary-family cases.
It uses Python stdlib and PyYAML, pins the JAR and verifier before execution and
checks independent PDB/PAE calculations, cache reuse and `study verify`. The
optional twelfth case retrieves public human TP53 through the native CLI and
compares mmCIF summaries with a separately retrieved matching PDB. The configured
predictor is a synthetic fixture, not AlphaFold. New AlphaFold inference,
interface/function/pathogenicity validation and browser interaction are separate
verification needs. Raw sequence/coordinate artifacts remain local.

## Multiway overlap, heterogeneity and paired cohort influence

`cohort-diagnostics-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
uses NumPy, SciPy and PyYAML for independent synthetic arithmetic: exact
2/3/4-way patterns, frozen validation families, fixed/known-covariance GLS
estimates, Q/I-squared, registered alerts and common-feature influence. It also
checks supplied diagnostics, missing coverage, source and primary hashes, cache
reuse and `study verify`. The executable and verifier are pinned before execution;
`verify DIRECTORY --jar JAR` requires that exact executable and executing verifier
source. No biological exclusion, real AI appraisal or new primary subset is claimed.


## Colocalization, complete regional coverage and supplied signal pairs

`colocalization-workflow.py run NEW_DIRECTORY --jar build/cli/omicsmatic-0.3.7.jar`
uses Python stdlib and PyYAML for fifteen offline synthetic source, model,
coverage, import and missing-work cases. It independently enumerates H0–H4
from native SuSiE effect log Bayes factors, retaining the distinction between
conditional arithmetic verification and independent fine-mapping/LD calibration.
It checks full signal-pair/prior-grid retention, source and primary-prior
preservation, mandatory partial grades/caveats, cache reuse and `study verify`.
The executable and executing verifier source must match their pre-execution pins.
No scientific source approval, real AI appraisal or biological causality is claimed.

## Registered primary result identity

`primary-results-workflow.py run NEW_DIRECTORY --jar JAR` checks eight offline
synthetic single-cohort, BH/BY, supplied full/selected meta-analysis, explicit
cohort, ambiguous-cohort, incomplete-meta and two-hypothesis cases. It compares canonical producer
and source identities, source-adjusted P values and complete-background gates
across GO, ranked GSEA, candidate ranking and the manuscript evidence inventory.
The executable and executing verifier are pinned before execution; verify uses
those exact pins. Completed-operation cache replay and `study verify` are checked.
These are software-contract checks, not biological or AI-appraisal validation.

## Genotype QC acceptance example

`genotype-qc-workflow.py` generates and executes 15 offline synthetic studies in
a new directory. It requires NumPy, SciPy and PyYAML. From the repository root:

```powershell
python examples\study-pipeline\genotype-qc-workflow.py run build\genotype-qc-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\genotype-qc-workflow.py verify build\genotype-qc-example --jar build\cli\jlinalg-0.3.7.jar
```

The executable and executing verifier are pinned before execution. Independent
calculations check sample/allele counts, MAC 19/20, HWE reference scopes, exact
Fisher missingness, quantitative OLS and retained-test BH. Cases cover dosage
versus hard-call provenance, per-ALT quality, absent quality, sample/marker
missingness and explicit exclusions for unsupported ploidy and multiallelic
sources. Completed operations are replayed from cache and `study verify` checks
the evidence chain. The fixture uses its own resource directory with network
access disabled. See the [QC tutorial](../../docs/vignettes/study-pipeline-tutorial.md#genotype-qc-scopes-and-unavailable-statistics)
for configuration and current model limits. Binary-tail calibration, biological
sex/PAR, ancestry/kinship and caller-level assay QC are separate requirements.

## Firth binary GWAS acceptance example

`firth-gwas-workflow.py` runs twelve offline synthetic studies in a new output
directory, with NumPy, SciPy and PyYAML:

```powershell
python examples\study-pipeline\firth-gwas-workflow.py run build\firth-gwas-example --jar build\cli\jlinalg-0.3.7.jar
python examples\study-pipeline\firth-gwas-workflow.py verify build\firth-gwas-example --jar build\cli\jlinalg-0.3.7.jar
```

It pins the executable, executing verifier and independent R reference files.
It checks full/constrained Jeffreys penalized fits, effects, ordinary Fisher SE,
LRT P values, method/QC propagation, missing-dosage imputation, fixed-offset
complete cases, explicit singular/nonconverged outcomes, underflow-safe log P,
cache replay and `study verify`. Upstream `logistf` 1.26.1 numerical reference
fixtures, their R generator and official source/binary hashes are retained under
`src/test/resources/firth-logistic` and `src/test/R`. Independent SciPy objective
optimization provides a second point check.

`calibration-enumeration.json` separately enumerates fixed-margin null carrier
tables. It exposes finite-sample tail inflation in some extremely sparse
designs; point agreement does not prove tail calibration. See the
[Firth tutorial](../../docs/vignettes/study-pipeline-tutorial.md#firth-association-for-separated-binary-gwas-designs)
for the explicit `binary_test: firth-lrt` configuration and remaining limits.
The default efficient-score route is retained; the new method supplies no
implicit relatedness adjustment or ordinary-null score/covariance export.

## Exact conditional binary GWAS

`exact-conditional-workflow.py` runs and verifies offline packaged studies for
observed hard calls, stratum case-count conditioning, fixed offsets, missing
genotype omission, separate optional Firth effects, retained P-only tests,
incomplete-family FDR suppression, cache replay and sealed output verification.
The tutorial documents the explicit model and resource bounds:
[exact conditional tests](../../docs/vignettes/study-pipeline-tutorial.md#exact-conditional-tests-for-sparse-binary-gwas).
Independent rational outcome-subset references are under
`src/test/resources/exact-conditional-logit`, with their standard-library Python
generator in `src/test/python/exact-conditional-reference.py`.

## Native binary risk scores

`binary-risk-score-workflow.py` runs thirteen offline packaged cases with pinned
software and synthetic data. It independently reconstructs ridge/lasso/elastic-net
convex objectives, training-only standardization/imputation, tuning log loss,
frozen probabilities and comparator metrics. Complete reuse, tuning/group
leakage, evaluation perturbation, optimizer limits and incomplete related-test
families have explicit cases. The [binary score guide](../../docs/vignettes/study-pipeline-tutorial.md#native-binary-risk-score-development-and-frozen-probabilities)
describes the objective, controls and interpretation. Independent test references
and their generator are in `src/test/resources/binary-score` and
`src/test/python/binary-score-reference.py`. `risk-score-workflow.py` retains the
existing quantitative/import acceptance cases; its historical
`unsupported-training` case supplies a nonbinary outcome to a binary model.

### LD-aware GWAS functional-category enrichment

`gwas-enrichment-workflow.py` verifies the native overlapping binary-annotation
S-LDSC study stage against independent NumPy QR references also checked against
pinned upstream LDSC. It exercises free/fixed intercepts, source reordering,
unsigned allele reversal, prespecified QC, official-meta input, complete and
selected-only supplied-result reuse, unresolved families, sealed outputs and
aggregate finding inventory. Use `run NEW_DIRECTORY --jar EXECUTABLE_JAR`, then
the bundle's pinned verifier and JAR for `verify DIRECTORY --jar PINNED_JAR`.
All inputs are synthetic; numerical agreement is not biological calibration or
AI scientific appraisal. See the [study tutorial](../../docs/vignettes/study-pipeline-tutorial.md#ld-aware-gwas-functional-category-enrichment)
for schemas, default thresholds and limitations.

### Conditional binary rare-variant sets

`binary-rare-variant-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` pins
the executable/verifier before ten offline native, failure and supplied-stage
cases. It checks exact/independent-pilot sampled tests, three-cohort scopes,
BY families, coverage/null/relatedness gates, immutable inputs, local private
rows, cache replay and reuse of completed external tests. See the
[binary rare-set guide](../../docs/vignettes/study-pipeline-tutorial.md#conditional-binary-rare-variant-sets).
These synthetic fixtures do not establish real biological calibration or
genome-wide performance.

### Rare-set enrichment and literature follow-up

`rare-followup-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` checks native
and supplied primary rare-set families, explicit mappings, complete joint
mask-by-term enrichment correction, source-FDR-preserving literature, original
ontology identities, partial prerequisites and supplied GO with missing
Reactome work. Read the
[rare-set downstream guide](../../docs/vignettes/study-pipeline-tutorial.md#rare-set-findings-in-enrichment-and-contextual-literature)
for the exploratory ORA assumptions and source/namespace requirements.

### Rare-set cohort overlap

`rare-cohort-overlap-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` checks
two/three/four-cohort gene-mask-test membership, full joint source BY families,
frozen validation including secondary tests, coverage gaps, unusual overlap,
signed/unsigned direction, work bounds and complete/partial supplied-stage
reuse. The independent verifier reconstructs each selected cell and exclusive
intersection from the original synthetic input tables. Native conditional
binary results also enter the diagnostic. Read the
[rare-set cohort guide](../../docs/vignettes/study-pipeline-tutorial.md#rare-set-cohort-overlap-with-complete-source-families)
for counting units and limitations; these fixtures do not establish biological
calibration or independent replication.

### Complete missing MR features

`mr-completion-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` checks
explicit external feature contributions and native completion of uncovered
features. It verifies that covered features are not refitted, original source
adjustments survive, the joint MR correction is separate, and invalid coverage
never falls back to a favorable native result. Read the
[MR completion guide](../../docs/vignettes/study-pipeline-tutorial.md#complete-missing-mr-features-from-supplied-contributions)
for the source/protocol, family and execution-origin contracts.

### MR coverage with unresolved gene annotations

`mr-feature-coverage-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR`
checks eligibility before annotation, exact-feature reviewed locations,
unresolved and completed source dispositions, supplied primary meta-analysis,
resumed location declarations and a 105-pair family. Its independent verifier
reconstructs primary estimates/P values and BY correction, checks immutable
inputs and audits the actual unsent impact payloads. Read the
[MR coverage guide](../../docs/vignettes/study-pipeline-tutorial.md#mr-coverage-before-gene-annotation)
for the location contract and scientific limits.

### Distinct GWAS principal findings with registered LD evidence

`finding-grouping-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR` checks
fifteen offline cases using supplied meta-analysis results and registered local
LD tables. It independently reconstructs transitive components, including
unselected mediators, shared identities and assembly distinctions. It checks
coverage/cutoff boundaries, unknown pairs, source conflicts, work bounds and
missing/changed-reference resume without refitting supplied statistics.
The actual unsent impact payloads retain all eligible candidates and cite
the aggregate LD evidence. Read the
[LD finding guide](../../docs/vignettes/study-pipeline-tutorial.md#registered-ld-evidence-for-distinct-principal-findings)
for the distinction between redundancy components, index-variant clumping,
fine-mapping and scientific independence.

### Cross-cohort module preservation

`network-preservation-workflow.py run NEW_DIRECTORY --jar EXECUTABLE_JAR`
and `verify DIRECTORY --jar EXECUTABLE_JAR` independently reconstruct native
residual-correlation graphs and frozen-module edge/sign transfer from the
original synthetic matrices/tables. Cases include supplied/native/mixed work,
coverage/context holds, bounds, stage reuse, source rounding and dossier context.
Read the [preservation guide](../../docs/vignettes/study-pipeline-tutorial.md#cross-cohort-module-preservation)
for definitions, source requirements and the distinction from calibrated
preservation tests or biological validation.


`rare-omnibus-workflow.py run NEW_DIRECTORY --jar JAR` checks cross-mask set/gene
combination, grouping, scoped families, completed omnibus reuse, frozen validation
and missing-map resume. `verify DIRECTORY --jar PINNED_JAR` independently rechecks
results and seals. Requires NumPy, mpmath and PyYAML; offline synthetic fixtures
do not certify biological conclusions.

`finding-discussion-workflow.py run NEW_DIRECTORY --jar JAR --references REFERENCES`
tests claim/source/result bindings with a separately acquired native full-text
bundle for Wickenhagen et al. (Science 2021). Numerical results are synthetic.
`verify DIRECTORY --jar PINNED_JAR` rechecks snapshots, sealed inheritance,
coverage and scientific-review status. Read the
[source-review guide](../../docs/vignettes/study-pipeline-tutorial.md#claim-bound-principal-finding-discussion)
for acquisition, schema, disclosure boundaries and limitations.

`supplied-literature-workflow.py run NEW_DIRECTORY --jar JAR --references REFERENCES`
verifies supplied bibliographic citations and exact-query completion. It retains
source-reported provenance, private-note boundaries, malformed/partial cases and
missing offline work. Optional `--live-missing-query` exercises one real public
dummy query; source search declarations and numerical results remain synthetic.
Read the [reuse guide](../../docs/vignettes/study-pipeline-tutorial.md#reuse-supplied-literature-and-complete-missing-searches).

### External population-frequency rare masks

`rare-frequency-workflow.py run NEW_DIRECTORY --jar JAR` exercises a versioned
synthetic ALT-count panel before native masks and score pooling. It checks
population maxima, boundary/zero counts, coverage and allele/build holds, malformed
sources, compatible score imports, missing-source completion, resource revisions,
independent single-marker eligibility and immutable cached fits. `verify` rechecks
the sealed outputs and independent burden formulas. Keep the adjacent
`rare-variant-workflow.py` generator available; dependencies are Python and PyYAML.
See the [contract](../../docs/vignettes/study-pipeline-tutorial.md#external-population-frequency-eligibility-for-rare-masks).

### Native and supplied Cox survival studies

`survival-workflow.py run NEW_DIRECTORY --jar JAR` checks explicit molecular and
genotype time-to-first-event analyses, supplied-statistic reuse, endpoint/fit/
resource holds, corrected log-time PH diagnostics, log-HR discovery/meta-analysis
and offline aggregate finding context. `verify` independently reconstructs
likelihood fits, information, PH tests and pooling with NumPy/SciPy. Dependencies
are Python, PyYAML, NumPy and SciPy. Fixtures are synthetic; no external requests
or real censoring/calibration claims are made. See the
[contract](../../docs/vignettes/study-pipeline-tutorial.md#registered-native-cox-survival-association).

### Finite Cox maxima and monotone-likelihood holds

`cox-likelihood-workflow.py run NEW_DIRECTORY --jar JAR` uses the sibling
`survival-workflow.py` and NumPy/SciPy/PyYAML. It exercises native/replayed,
monotone LRT/Wald, joint and nuisance separation, shared resource bounds,
rescaled finite models, supplied reuse and an actual unsent aggregate packet.
`verify` uses independent HiGHS risk-cone LPs and exact Fraction witness signs.
Fixtures do not establish biological assumptions, rare-event calibration or
privacy certification. See the [finite-estimate guide](../../docs/vignettes/study-pipeline-tutorial.md#finite-cox-coefficient-estimates-before-signed-inference).

### Prepared Cox scores and standalone coefficients

`cox-score-workflow.py run NEW_DIRECTORY --jar JAR` checks standalone numeric
assay/genotype score scans and full coefficient fits against independent
NumPy/SciPy nuisance fits and explicit risk-set sums. It covers unadjusted and
factor-adjusted nulls, entry/offsets/ties, missingness and reordered IDs, source
units, MAC20, score covariance export, an infinite alternative with a valid
score route, all-event data and deliberate endpoint/null/coefficient holds.
Keep `survival-workflow.py` alongside it; dependencies are Python, PyYAML, NumPy
and SciPy. `verify` rechecks retained inputs and exact-JAR receipts. See the
[method and proof scope](../../docs/vignettes/study-pipeline-tutorial.md#adaptive-cox-proofs-and-prepared-null-score-scans).

### Grouped nested score validation

`nested-risk-score-workflow.py run NEW_DIRECTORY --jar JAR` exercises native
quantitative/binary development with nested group folds, final independent
evaluation and frozen model reuse. NumPy/SciPy independently refit every inner,
outer and final ridge model, loss and prediction. The verifier also checks
external-data isolation, replay, aggregate context and deliberate group/category/
class/work holds. Dependencies are Python, NumPy, SciPy and PyYAML; no sibling
script or external service is required. See the
[nested-validation contract](../../docs/vignettes/study-pipeline-tutorial.md#grouped-nested-development-validation-for-risk-scores).


`genetic-risk-score-workflow.py run NEW_DIRECTORY --jar JAR` generates 15 offline
complete-model BED scoring cases, including multi-cohort exact FID/IID mapping,
aliases/allele flips, external-frequency imputation, a 2,101-member model with a
MAC-1 nonzero-weight member, fixed normalization/logit probabilities, shared
budgets and supplied-stage reuse. It independently checks scores, held-out
metrics and related tests with NumPy/SciPy, then checks cache replay, intentional
genotype mutation, recomputation and retention of the earlier artifacts. Use
`verify DIRECTORY --jar THE_PINNED_JAR` to rerun the independent checks without
reexecuting analysis. All inputs and biological labels are synthetic. See the
[guide](../../docs/vignettes/study-pipeline-tutorial.md#frozen-genetic-scores-from-bed-genotypes).


`variant-risk-score-workflow.py run NEW_DIRECTORY --jar JAR` exercises complete
frozen scoring across VCF GT/DS, gzip VCF, raw/BGZF BCF2.1/2.2, raw/zlib/zstd and
phased BGEN, external sample IDs and mixed BED/file cohorts. Independent byte
encoders and NumPy/SciPy verify scores, frozen predictions, held-out metrics and
related tests. Whole-source work, unknown/non-diploid/multiallelic models,
duplicates/missing loci, sidecar conflicts, supplied-stage reuse and cache
mutation/restoration have explicit acceptance cases. Keep the sibling
`genetic-risk-score-workflow.py` beside it; pinned copies/hashes of both scripts
are retained. Requires NumPy/SciPy/PyYAML/zstandard. `verify DIRECTORY --jar
THE_PINNED_JAR` reruns checks without reexecuting analyses. All inputs are
synthetic. See the [guide](../../docs/vignettes/study-pipeline-tutorial.md#frozen-scores-from-vcf-bcf-and-bgen).


`assay-qc-workflow.py run NEW_DIRECTORY --jar JAR` generates nineteen offline
molecular source-QC cases, including native audit/filter/detection/counts, supplied
post-QC decisions and deliberate complete-stage holds. NumPy/SciPy independently
check moments, fixed original denominators, retained matrices and actual downstream
OLS/Cox effects and adjusted interactions. Native network sample scope and risk-score development isolation are
also checked. `verify DIRECTORY --jar THE_PINNED_JAR` rechecks retained evidence.
Requires NumPy, SciPy and PyYAML; all inputs are synthetic. See the
[assay-QC contract](../../docs/vignettes/study-pipeline-tutorial.md#molecular-assay-qc-and-supplied-decisions).


`completed-study-workflow.py run NEW_DIRECTORY --jar JAR` checks completed
network modules and module GO, an entirely supplied study through inventory,
impact selection and manuscript drafting, and an authored three-story revision
with 24 claim bindings and finding-specific supplements. Independent SciPy
hypergeometric/BH checks, preserved source denominators, underflow logs, missing
background arrival, cache reuse and deliberate scope/completeness holds are
included. `verify DIRECTORY --jar THE_PINNED_JAR` rechecks retained evidence.
Requires NumPy, SciPy and PyYAML. Its loopback responder is a synthetic protocol
fixture; it does not assess real AI or scientific discussion quality. See the
[completed-study guide](../../docs/vignettes/study-pipeline-tutorial.md#completed-analyses-through-manuscript).


`model-review-workflow.py run NEW_DIRECTORY --jar JAR` checks eleven missing-model
profiles, bounded public assay/endpoint/design queries and an explicit OLS
continuation with independent SciPy beta/SE/P/BH references. It preserves
earlier attempts and checks that private sample/column/description values do
not enter queries. Protocol approvals are synthetic, with no public-provider
or AI requests. NumPy, SciPy and PyYAML are required.
`verify DIRECTORY --jar THE_PINNED_JAR` rechecks retained inputs and outputs.
See the [missing-model guide](../../docs/vignettes/study-pipeline-tutorial.md#missing-model-review-and-explicit-continuation).

`literature-coverage-workflow.py run NEW_DIRECTORY --jar JAR` checks final
dossier coverage for native nonselection, prior supplied searches outside the
cap, completed supplied literature and pending allocation. It verifies exact
query-reuse deduplication, source links, mandatory caveats and unchanged P/FDR.
Keep `literature-priority-workflow.py` alongside it; NumPy, SciPy and PyYAML are
required. `verify DIRECTORY --jar THE_PINNED_JAR` rechecks retained artifacts.
All query declarations are synthetic, with no external requests. See the
[coverage guide](../../docs/vignettes/study-pipeline-tutorial.md#literature-coverage-in-finding-dossiers).

`literature-priority-workflow.py run NEW_DIRECTORY --jar JAR` exercises
provisional impact allocation before the literature cap, using synthetic
completed query declarations and a loopback protocol responder. It checks all
105 eligible targets before allocation, original P/FDR preservation, native
budget-amendment resume, immutable history, complete supplied-stage reuse and
deliberate policy/response/payload holds. The final full-inventory manuscript
impact review remains separate. `verify DIRECTORY --jar THE_PINNED_JAR` rechecks
the retained evidence. Requires NumPy, SciPy and PyYAML. No real AI or scientific
impact quality is certified. See the
[allocation guide](../../docs/vignettes/study-pipeline-tutorial.md#provisional-impact-before-the-literature-cap).


`phenotype-gsea-workflow.py` checks native independent OLS shared residual permutations, stratified/exhaustive nulls, raw-data-free complete null-ranking reuse and prerequisite holds against independent NumPy full fits and enrichment scans. Requires NumPy/PyYAML; see the [guide](../../docs/vignettes/study-pipeline-tutorial.md#phenotype-null-gsea-and-reusable-null-rankings).


`continuous-gwas-enrichment-workflow.py` checks joint binary/continuous S-LDSC, signed reference moments, standardized conditional effects with ratio SEs, log-tail correction and typed completed-source reuse. Requires NumPy/SciPy/PyYAML; see the [continuous annotation guide](../../docs/vignettes/study-pipeline-tutorial.md#continuous-gwas-annotations-and-conditional-effects).
