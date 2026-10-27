# Start here: your first study.yaml

This is the shared starting point for all three modes: [deterministic analysis](ai-no-ai.md),
[AI as an advisor](ai-advisor.md), and [AI as a driver](ai-driver.md).
You need the same study description and scientific inputs in every mode.
Start with the small offline example below; then substitute your own files.

These instructions target the **current source build** of OmicsMatic.
The executable is still named `omicsmatic-0.3.7.jar`; that filename does not
mean every source-build feature is in the previously published v0.3.7 binary.
In particular, the typed completed-analysis imports described here require a
current source build. See [runtime requirements](https://github.com/robbyjo/OmicsMatic/blob/main/docs/runtime-requirements.md)
for Java and optional software.

**Choose your shell:** use either the **PowerShell (Windows)** or the
**Bash (Linux/macOS)** version of each example. Keep the same terminal session
while following a walkthrough so its variables remain defined. On macOS, run
`bash` in Terminal before using the Bash examples if your default shell is zsh.
For steps that need review or filled-in YAML, stop at that point before continuing.

## What is study.yaml?

A `study.yaml` is a text file describing your study, its inputs and the work you
want done. It is a recipe, not a data table or a program. OmicsMatic reads it,
checks references and scientific prerequisites, and compiles an analysis plan.

YAML uses indentation to express containment. Use spaces, not tabs.
A dash starts a list item. An `id` is a stable machine-readable name:
`discovery-results` is a dataset ID; `data/discovery.tsv` is its file path.
A hypothesis refers to the ID, not the filename. Values in square brackets,
such as `[discovery_replication, meta_analysis]`, form a list.

There are three different records:

| Record | Who prepares it | Purpose |
| --- | --- | --- |
| `study.yaml` | You, with optional AI help | Describe known facts and requested analyses. |
| `resolved-study.yaml` | OmicsMatic | Freeze resolved paths, defaults, assumptions and the study hash. |
| `plans/<plan-id>/analysis-plan.yaml` | OmicsMatic | Record the exact operations and their dependencies for approval. |

Edit your draft before resolution. Once the project exists, scientific changes
go through an [amendment](ai-no-ai.md#add-an-analysis-later); preserve the frozen records.

## The minimum needed to begin

The required top-level keys are `schema_version`, `project`, `datasets` and
`hypotheses`. A useful study also needs actual files, enough metadata to interpret
them, and the inputs required by its goals. A structurally valid file can still
wait for a missing scientific input.

This complete small contract starts from **already calculated association results**.
It runs diagnostics and an evidence report; it cannot perform independent replication
with just one dataset.

~~~yaml
schema_version: 1
project:
  id: first-study
  title: My first result-first study
  description: Inspect completed molecular association estimates.
datasets:
  - id: discovery-results
    type: transcriptomics
    stage: association_results
    feature_id_type: gene_symbol
    files:
      results: data/discovery.tsv
      data_dictionary: data-dictionary.yaml
hypotheses:
  - id: H1
    description: Review the completed discovery associations.
    inputs:
      discovery: [discovery-results]
    analysis:
      mode: manual
      goals: []
~~~

Here `goals: []` deliberately requests no extra scientific branch.
Harmonization, diagnostic review, reporting and applicable safety/sensitivity
steps may still appear in the plan. For meaningful replication, add a second
dataset, its cohort relationship and `discovery_replication`; the complete
two-cohort example below does that.

The associated `data-dictionary.yaml` explains the result headers:

~~~yaml
columns:
  feature_id: feature_id
  effect: beta
  standard_error: se
  p_value: p_value
descriptions:
  feature_id: Molecular feature identifier, not a participant identifier.
  beta: Association coefficient on the recorded analysis scale.
  se: Standard error of beta, not its variance.
  p_value: Two-sided association P value from the original analysis.
~~~

The results are a tab-separated file, with one feature per row and a header:

~~~text
feature_id    beta    se    p_value
GENE1         0.50    0.10  0.000001
GENE2        -0.20    0.10  0.040000
~~~

The spacing above is illustrative; use real tab separators in a TSV.
These invented teaching values and identifiers are not biological evidence.
For real studies record the phenotype, units, transformations, covariates,
model, population and testing family. An odds ratio must not be silently treated
as a linear coefficient or a log odds ratio.

**Want all the options?** Read the [study.yaml field guide](study-yaml-reference.md)
for a map of optional sections and the machine-readable schema.

## Where do I put my data?

Use a dedicated analysis directory. Keep inputs in a stable folder; OmicsMatic
creates its own plans, operations and indexes beside them.

~~~text
my-study/
  study.yaml
  data-dictionary.yaml
  data/
    discovery.tsv
    replication.tsv
    feature-annotation.tsv
  inputs/
    completed-meta.tsv
  responses/                   # imported, reviewed browser AI receipts
~~~

File paths in your initial contract are resolved relative to the directory
containing that contract. This is why `data/discovery.tsv` works even when the
repository is elsewhere. Absolute paths can point to an institution-controlled
data store; you do not have to copy large or restricted files into the repository.
Use forward slashes inside YAML paths for portability. Keep original files
immutable, and use new versions when their content changes.

The commands here run from the study directory and use `--project .`, so the
contract and generated project records stay together. A separate project output
directory is supported, but adds path choices that a first rehearsal does not need.

Do not put patient records, credentials or private analysis directories in a public
GitHub repository. Keep a project backup containing the contracts, input provenance,
ledger and outputs; the HTML index alone is not the analysis archive.

## Choose your starting point

| What you already have | Declare it as | What OmicsMatic can do next |
| --- | --- | --- |
| Quantified or processed participant measurements plus phenotypes | A dataset with a supported matrix stage and its matrix/phenotype mappings | Fit supported models, then continue downstream. |
| Per-cohort beta, SE and P-value tables | `stage: association_results`, `files.results`, and header mappings | Reuse those estimates for harmonization, replication, pooling and follow-up. |
| Completed meta-analysis, enrichment, networks, MR or other supported stages | `analysis.supplied_results` with its typed `kind` and provenance | Import the completed stage and run the requested missing work. |

For matrices, start with a template rather than adapting the result-table
example by changing only its stage:

**Windows (PowerShell):**

~~~powershell
java -jar $jar study init --template bulk-transcriptomics --out draft-matrix-study.yaml
java -jar $jar study init --template association-results --out draft-results-study.yaml
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study init --template bulk-transcriptomics --out draft-matrix-study.yaml
java -jar "$jar" study init --template association-results --out draft-results-study.yaml
~~~

These template commands assume you have built the executable and set `jar`
for your shell as shown in the [offline rehearsal](#run-a-complete-offline-rehearsal).
Replace placeholders before running. Other templates include `gwas`,
`epigenomics`, `single-cell`, `spatial`, `microbiome` and `multimodal`.
A template is a starting description, not evidence that every raw-data processing
step for that assay is implemented. See the
[matrix and result contracts](auditable-omics-pipeline.md#minimal-study-schema).

For completed work, use the
[worked supplied-results example](study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work).
It shows the entire contract for reusing an external meta-analysis and calculating
only missing GO enrichment, including `datasets: []`, column mappings, method,
cohort scope and full testing-family declaration. Merely listing a PDF or setting
`stage: meta_analysis_results` does not import a completed analysis.

## Start from a completed meta-analysis

If the cohort fits and meta-analysis are already finished, preserve them.
Here is a complete small **synthetic** import contract. Put it in a new study
directory and place the completed table at `inputs/completed-meta.tsv`:

~~~yaml
schema_version: 1
project:
  id: completed-meta-example
  title: Inspect a completed synthetic meta-analysis
  description: Import externally produced teaching estimates without refitting them.
datasets: []
hypotheses:
  - id: H1
    description: Preserve externally calculated estimates for review.
    outcome: synthetic
    analysis:
      mode: manual
      goals: []
      supplied_results:
        - id: existing-meta
          kind: meta_analysis
          role: primary
          files:
            results: inputs/completed-meta.tsv
          scope:
            phenotype: synthetic
            feature_id_type: gene_symbol
            species: human
          method:
            name: Completed synthetic meta-analysis
            software: teaching-fixture
            version: "1"
          family:
            id: synthetic-full-family
            coverage: complete
            tested_count: 2
            adjustment: BH
          provenance:
            producer: teaching-fixture
          limitations:
            - Synthetic estimates; original cohort analyses are not reproduced.
~~~

For this teaching example, the table has exactly two tests and canonical
headers `feature_id`, `beta`, `se`, `p_value`:

~~~text
feature_id    beta    se    p_value
GENE1         0.50    0.10  0.000001
GENE2        -0.20    0.10  0.040000
~~~

Use real tab separators. The lifecycle commands below also apply to this
directory: validate, resolve, plan, review, approve, run and verify.
The meta-analysis stage is imported with external attribution; OmicsMatic does
not claim to have refitted it.

For your own study, replace every synthetic declaration with the actual source
records. `tested_count: 2` is correct only for this two-row full-family example.
If the source is a selected-hit export, declare its selected-only coverage and
original full-family adjusted P values instead. Never relabel it complete.

To perform only missing enrichment, add the appropriate goal, feature annotation
and a verified gene-set resource. Follow the
[complete meta-to-GO example](study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work);
a table of significant genes alone cannot supply the enrichment background.

## Run a complete offline rehearsal

From the **repository root**, choose your shell and run:

**Windows (PowerShell):**

~~~powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
$jar = (Resolve-Path "build/cli/omicsmatic-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/my-first-study"
if (Test-Path -LiteralPath $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

**Linux / macOS (Bash):**

~~~bash
bash ./gradlew executableJar --no-daemon --no-parallel || exit 1
jar="$PWD/build/cli/omicsmatic-0.3.7.jar"
project="$PWD/build/my-first-study"
if [ -e "$project" ]; then
    printf '%s\n' "Choose a fresh project directory." >&2
    exit 1
fi
cp -R examples/study-pipeline "$project" || exit 1
cd "$project" || exit 1
~~~

This copies synthetic results and a complete two-cohort `study.yaml`.
`$jar` is an absolute path, so changing directories does not lose the executable.
Keep this terminal session open: the later examples use that variable.
In another window, set it again to your executable's absolute path.

Validate, freeze and inspect the plan:

**Windows (PowerShell):**

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-id scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
Get-Content resolved-study.yaml
Get-Content plans/*/analysis-plan.yaml
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study validate study.yaml --check-files
java -jar "$jar" study resolve study.yaml --actor-id scientist-1
java -jar "$jar" study plan --project . --actor-id scientist-1
cat resolved-study.yaml
cat plans/*/analysis-plan.yaml
~~~

Check the cohorts' independence, effect scale, phenotype, selected inputs and
requested operations. The synthetic example explicitly declares independent
cohorts. For a real study, resolve uncertain overlap or relatedness with your
supervisor; `--accept-assumptions` is an explicit acceptance, not a repair for
missing knowledge.

After review, approve and execute:

**Windows (PowerShell):**

~~~powershell
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --offline --actor-id scientist-1
java -jar $jar study status --project .
java -jar $jar study verify --project .
Start-Process index/index.html
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study approve --project . --approved-by scientist-1
java -jar "$jar" study run --project . --offline --actor-id scientist-1
java -jar "$jar" study status --project .
java -jar "$jar" study verify --project .
if [ "$(uname -s)" = "Darwin" ]; then
    open index/index.html
elif [ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && command -v xdg-open >/dev/null 2>&1; then
    xdg-open index/index.html
else
    printf 'Open this report in a browser: %s/index/index.html\n' "$PWD"
fi
~~~

The report links replication, meta-analysis, plots, diagnostics and source
manifests. `study verify` checks recorded integrity; it does not validate your
study design or prove a scientific claim.

For a contract you have already reviewed, the convenience command is:

**Windows (PowerShell):**

~~~powershell
java -jar $jar study auto study.yaml --project . --offline --actor-id scientist-1
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study auto study.yaml --project . --offline --actor-id scientist-1
~~~

`study auto` validates, resolves, plans, **records command-line approval**, runs
eligible work, verifies and rebuilds the index. It is useful for a trusted
recipe; the explicit steps above make first-time review easier.
The command name `study auto` does not override `analysis.mode: manual`.

## Run everything, the rest, or one part

| Your intention | Configuration or command |
| --- | --- |
| Run every eligible registered branch | Set the hypothesis's `analysis.mode: auto`; review the resulting plan. Missing prerequisites remain visible. |
| Run a small set of branches | Set `analysis.mode: manual` and list only the desired `analysis.goals`. Inspect prerequisites and additional diagnostic/report operations. |
| Finish work after an interruption or missing input | Repeat `study run --project .` or the same `study auto` command. Verified completed operations are reused by default. |
| Continue from analyses completed elsewhere | Declare `analysis.supplied_results`, then request the missing goals. |
| Recompute completed work intentionally | Use `--rerun`; previous attempts remain in history. |
| Run a standalone method outside the study planner | Use its documented CLI; record inputs, command, software version and outputs before importing a supported result type. |

There is no general `study run --only` or `--from-step` switch.
Goals specify requested work; dependencies are still necessary.
For example, `goals: [meta_analysis]` still needs compatible per-cohort estimates
or a typed completed meta-analysis. For a resolved project, change goals through an
amendment, re-plan and approve. See the [deterministic recipes](ai-no-ai.md).

## If the run stops or waits

Run `study status --project .`, `study questions --project .`, and open
`index/index.html`. Follow the operation's input request.

| Message | Meaning and next step |
| --- | --- |
| Missing file or mapping | Check the declared path and dictionary; supply the right immutable input. |
| `waiting_for_input` | A scientific prerequisite or response is absent. Inspect the request; do not treat it as a null result. |
| `waiting_for_browser_response` | A local AI handoff was prepared; complete the [advisor workflow](ai-advisor.md). |
| `offline_policy` or `policy_denied` | The requested network action is blocked. Use approved local inputs or a reviewed policy amendment. |
| Failure or integrity mismatch | Read the operation manifest and verification report before continuing. Do not edit frozen outputs to clear the error. |

`study run` can return a nonzero exit code when work is waiting.
`--allow-partial` permits batch success for eligible partial completion; it does
not make missing scientific evidence complete. See the
[detailed status and exit-code guide](study-pipeline-tutorial.md#6-verify-and-safely-resume).

Next: [mode 1](ai-no-ai.md), [mode 2](ai-advisor.md), [mode 3](ai-driver.md),
[AI connections](ai-connections.md), or [literature and tool choices](study-tools-literature.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
