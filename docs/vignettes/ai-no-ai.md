# Mode 1: deterministic analysis without AI

OmicsMatic can plan, execute and report a study without an AI account or key.
You specify the question and inputs, review the deterministic plan, and approve
the work. This is a useful first rehearsal even if you later want an advisor or driver.

**New to study.yaml?** It is the study recipe: `project` describes the study,
`datasets` points to files, and `hypotheses` selects inputs and analysis goals.
The minimum top-level keys are `schema_version`, `project`, `datasets` and
`hypotheses`. [Start here](study-getting-started.md) explains YAML indentation,
a complete minimal contract, where data belong, and a runnable synthetic example.
The [field guide](study-yaml-reference.md) maps the optional details.

These instructions use the current source build. The three collaboration modes
are ways of working, not values of `analysis.mode`. Both `manual` and `auto`
are deterministic planning settings; neither enables AI.

## What you need

Use JDK 17 or newer, the repository or an appropriate executable, and a dedicated
study directory. Your starting files can be:

- Participant matrices and phenotypes for a supported model.
- Completed per-cohort association tables with documented effect definitions.
- Typed completed analyses, using the current source build's
  [supplied-results route](study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work).

For a first attempt, use synthetic results. This avoids making scientific or
data-disclosure decisions while learning the commands.

## Prepare the two-cohort example

From the repository root:

~~~powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
$jar = (Resolve-Path "build/cli/omicsmatic-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/ai-no-ai"
if (Test-Path -LiteralPath $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

Open `study.yaml`, `data-dictionary.yaml`, `data/discovery.tsv` and
`data/replication.tsv`. The tables contain feature ID, beta, standard error and
P value. They contain synthetic association results, not patient records.

The contract declares two independent cohorts and selects their datasets as
discovery and replication. Its analysis block is:

~~~yaml
analysis:
  mode: manual
  goals: [discovery_replication, meta_analysis]
~~~

Keep assistance omitted or explicitly disabled. In a real study, different cohort
names do not prove independent participants. Matching headers do not prove
comparable models, outcome scales or effects.

## Validate and review the exact work

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-id scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
Get-Content resolved-study.yaml
Get-Content plans/*/analysis-plan.yaml
~~~

Validation checks structure, IDs, references and declared files. Resolution freezes
explicit defaults and assumptions. Planning compiles operations without running
the analyses. Read the dataset roles, effect mappings, testing family,
cohort relationship and pending decisions before approval.

A larger plan can include harmonization, annotation, QC, sensitivity, diagnostic
and reporting steps in addition to the named goals. A goal needs inputs and checks.
If an assumption is unknown, resolve it scientifically.
Use `--accept-assumptions` only when you deliberately accept the stated
approvable assumptions.

## Approve, run and read the results

~~~powershell
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --offline --actor-id scientist-1
java -jar $jar study verify --project .
java -jar $jar study status --project .
Start-Process index/index.html
~~~

The small fixture runs offline. In a real offline project, required public
references must already be installed. For authorized public literature or
source retrieval, use an online policy and omit `--offline`.

Use the HTML index and manifests to locate:

| Output | Why you inspect it |
| --- | --- |
| `replication.tsv` | Which discovery findings meet the recorded replication rule, including direction. |
| `meta-analysis.tsv` | Pooled estimates, uncertainty and heterogeneity. |
| PNG/PDF comparisons and diagnostics | Discordant effects, unusual patterns and possible model/data issues. |
| Operation manifests | Actual inputs, implementation, status, output paths and hashes. |
| `ledger/events.jsonl` and `SESSION_HISTORY.md` | The analysis's recorded decisions and event history. |
| `QUESTIONS.md` | Scientific inputs or decisions still requiring attention. |

Operation folder names depend on the plan. Use manifest links instead of
guessing a numbered output directory. Completion means the operation ran under
the recorded contract; it does not establish that your model or interpretation is correct.

## Start the entire analysis or finish the rest

For a trusted, reviewed contract, use:

~~~powershell
java -jar $jar study auto study.yaml --project . --offline --actor-id scientist-1
~~~

This command includes command-line approval, execution, verification and indexing.
It honors `analysis.mode`: a manual contract stays limited to its requested
goals and necessary supporting steps.

To investigate all eligible registered branches, use `analysis.mode: auto`
before initial resolution, or change it through an amendment. Review the expanded
plan; eligibility depends on available data and supported adapters. Missing MR
inputs or LD do not become a completed negative result.

After interruption or a supplied missing input, repeat:

~~~powershell
java -jar $jar study run --project . --offline --actor-id scientist-1
~~~

Verified completed operations are reused by default. `--resume` is optional.
`--rerun` intentionally starts new attempts while retaining history.
See [resume and status details](study-pipeline-tutorial.md#6-verify-and-safely-resume).

## Run only selected parts

Set manual mode and choose a narrow goal list before resolving:

| Intended work | Example goals |
| --- | --- |
| Pool compatible completed cohort estimates | `[meta_analysis]` |
| Compare discovery with replication | `[discovery_replication]` |
| Search literature for an existing study | `[literature_search]` with its labels and annotations |
| Reuse completed meta-analysis and fill missing GO | `[gene_ontology]` plus a typed supplied meta-analysis |
| Inspect completed results without an extra branch | `[]`; diagnostics/reporting and applicable safeguards still run |

There is no `study run --only` flag. The approved graph determines the work.
Some explicit configurations trigger their own branches, so inspect the plan
even in manual mode. Do not delete operations from the generated plan.

If all primary analyses were completed elsewhere, put their files under
`inputs/` or another stable path and declare `analysis.supplied_results`.
Do not give a completed network or MR table the association-results stage merely
to make it readable. Follow the
[typed imports](study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work).

## Add an analysis later

Suppose the approved two-cohort study is complete and you now want native
literature retrieval. Obtain the current `resolved_study_hash` from
`study status --project .`. Save this as `amendment-add-literature.yaml`,
replacing the hash and adapting search labels to the actual study:

~~~yaml
schema_version: 1
amendment:
  id: add-literature
  base_study_hash: CURRENT_RESOLVED_STUDY_HASH
  rationale: Add a recorded literature search after the primary numerical work.
changes:
  update:
    hypotheses:
      - id: H1
        analysis:
          mode: manual
          goals: [discovery_replication, meta_analysis, literature_search]
          bioinformatics:
            phenotype_terms: [body mass index]
            literature_terms: [gene expression]
            literature_selection: p_value
            literature_max_features: 20
            max_pages: 3
~~~

The synthetic fixture's invented genes are useful for numerical rehearsal,
not a real literature review; use a properly annotated real study for gene-level
searches. The amendment changes the requested work and preserves the earlier record.
Preview and approve, then review the new plan:

~~~powershell
java -jar $jar study amend amendment-add-literature.yaml --project . --check
java -jar $jar study amend amendment-add-literature.yaml --project . --approved-by scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
Get-Content plans/*/analysis-plan.yaml
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --actor-id scientist-1
~~~

This final run is online and can perform the newly authorized search. Unchanged
compatible completed work can be reused. A changed scientific dependency may
require recomputation; reuse is verified, not forced.

## Record an unusual finding

When a plot or estimate needs investigation, first record the observation.
Replace this illustrative path with a real artifact in your project:

~~~powershell
java -jar $jar study observe --project . --id unusual-effects-01 --type off_diagonal_streak --artifact plots/effects.png --hypothesis H1 --description "Unexpected streak in the effect comparison" --explanation "Check scale, alignment and cohort-specific effects before a biological interpretation"
~~~

An observation records a question, not evidence for your preferred explanation.
New exclusions, changed phenotypes or sensitivity analyses require a reasoned
amendment. Preserve the original result.

Literature retrieval, protein context and many bioinformatics operations work
without AI. See [literature, tools and finding emphasis](study-tools-literature.md)
for goals, tool choices, network controls and manuscript priorities.

## Protect local data

For sensitive work, explicitly deny remote transmission in the study policy
when appropriate, and use an offline resource policy for offline execution.
Keep participant inputs, outputs and logs in institution-controlled storage.
Local analysis does not de-identify records.

The built-in `--offline` option blocks OmicsMatic requests; external software
needs its own network controls. See the
[policy reference](study-yaml-reference.md#reference-resources-and-network-policy).

Next: [add an advisor](ai-advisor.md), [coordinate a driver](ai-driver.md),
or [explore every study.yaml field](study-yaml-reference.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [U.S. Department of Health and Human Services, Office for Civil Rights (2012) — Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule](../CITATIONS.md#hhs-hipaa-deidentification-2012)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
