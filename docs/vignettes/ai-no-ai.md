# Mode 1: reproducible analysis without AI

JLinAlg v0.3.7 supports a complete deterministic workflow without an AI account,
model, API key, or model-generated code. This is the default operating mode.
The same statistical engines and evidence records are used when AI collaboration
is added later. These three modes describe ways of working with JLinAlg; they
are not values of `analysis.mode`, whose `manual` and `auto` settings control
deterministic operation planning.

## Prepare a small study

From the repository root, build the executable and copy the synthetic two-cohort
example into a fresh directory:

~~~powershell
.\gradlew.bat executableJar
$jar = (Resolve-Path "build/cli/jlinalg-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/ai-no-ai"
if (Test-Path $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

The fixture contains association results, not patient records. It declares
independent discovery and replication cohorts, matching effect definitions,
column mappings, and discovery/replication and meta-analysis goals. Assistance
is omitted and therefore disabled. `analysis.mode: manual` keeps this teaching
example small; `analysis.mode: auto` can add eligible registered branches without
using AI.

## Review, approve, and run

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-id scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
Get-Content resolved-study.yaml
Get-Content plans/*/analysis-plan.yaml
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --offline --actor-id scientist-1
java -jar $jar study verify --project .
java -jar $jar study status --project .
~~~

Review assumptions, units, covariates, cohort dependence and the testing family
before approving. Do not accept an independence assumption simply to clear a
gate. `--offline` blocks built-in network requests, including public-reference
downloads. A larger offline study must have its required resources installed.

The example standardizes both tables, compares discovery and replication,
pools effects, produces diagnostics and writes a report. Inspect the operation
manifests, `replication.tsv`, `meta-analysis.tsv`, PNG/PDF comparisons,
`index/index.html`, `ledger/events.jsonl`, and `SESSION_HISTORY.md`.
Use the manifest to locate outputs; operation directory names are plan-derived.
Repeating `study run --project . --offline --actor-id scientist-1` reuses verified
completed operations. A scientific change goes through `study amend`, never a
silent edit to the resolved contract or completed results.

## Extend the analysis

JLinAlg's CLI and APIs cover association analysis, regression and mixed models,
differential analysis, enrichment, networks, MR, fine-mapping, colocalization,
replication, meta-analysis and sensitivity analysis. The study planner connects
registered operations when their scientific prerequisites are available;
standalone commands also provide methods outside the current study registry.
Missing regional statistics, LD, ancestry or instrument review remain explicit
blockers. A broad method catalog does not make every conceivable pipeline valid.

## Protect patient-level data

No AI request is needed for this mode. On a sensitive project, explicitly set
`project.data_policy.llm.remote_transmission: deny_all` and
`project.data_policy.llm.remote_patient_level_data: deny` before resolution.
Apply institution-controlled storage and account permissions to inputs, outputs
and logs. These files can themselves contain sensitive information; local
execution does not de-identify them. `--offline` governs JLinAlg's built-in
requests, not unrelated software or external tools.

Continue with [AI as an advisor](ai-advisor.md) or
[AI as an external driver](ai-driver.md), or follow the
[full study tutorial](study-pipeline-tutorial.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

- [Sandve et al. (2013): reproducible computational research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C (2013): evidence provenance](../CITATIONS.md#w3c-prov-o-2013)
- [HHS OCR (2012): de-identification guidance](../CITATIONS.md#hhs-hipaa-deidentification-2012)

<!-- SCIENTIFIC-CITATIONS:END -->
