# Mode 3: AI as an external workflow driver

A driver is a separately operated agent that helps prepare a study, coordinates
permitted CLI commands, reviews authorized outputs and proposes next steps.
OmicsMatic supplies the deterministic planner, numerical operations and evidence
ledger. You retain scientific decisions and approval.

**Start with the same recipe.** A `study.yaml` contains `schema_version`,
`project`, `datasets` and `hypotheses`: the study identity, file locations,
scientific questions and requested goals. Read
[Start here](study-getting-started.md) before giving an agent a task.
The [field guide](study-yaml-reference.md) lists optional configuration.

This guide targets the current source build. Driver mode is a supervised
integration pattern using the existing CLI. OmicsMatic does not bundle an
autonomous agent runtime or enable one with an `analysis.mode: driver` setting.

## Understand which connection goes where

| Connection | Where you configure it | What it authorizes |
| --- | --- | --- |
| External driver login, API key or OAuth | The external agent application's settings or approved execution service | That agent's model connection. Its host controls tools and accessible data. |
| OmicsMatic's optional built-in advisor | `study.yaml` → `assistance` | Only bounded assistance purposes such as next-step advice or literature support. |
| Statistical/resource adapters | The supported study method/resource configuration | Registered numerical or public-evidence operations, subject to prerequisites and policy. |

An external driver can coordinate a study with built-in assistance disabled.
Its login is not something to paste into the study file.
If you want both driver and advisor, configure the second connection using
[API-key/OAuth study examples](ai-connections.md); that can produce additional
calls and costs. Setting `--actor-type llm` only records attribution and never
connects a model.

## Connect the external driver

Choose an institution-approved agent and follow that client's documented sign-in
or API setup. If it supports OAuth, perform its login and token management there.
If it uses an API key, provide the secret through its documented secure settings
or environment. OmicsMatic does not maintain those credentials.

The surrounding environment should expose only the inputs and tools that the
agent needs. For a synthetic rehearsal it may inspect all synthetic files.
For patient-level work, use a controlled execution account or service: keep
restricted inputs local and disclose only reviewed aggregate artifacts.

A prompt saying “do not read patient data” is not an enforced boundary.
An agent with a general shell and unrestricted filesystem can bypass the
built-in assistance preview entirely. Limit file access, commands and network
destinations in the host. The built-in `deny_all` policy and `--offline` flag
cannot govern the agent's separate model connection.

Do not assume `index/LLM_CONTEXT.md` or a generated project `AGENTS.md` is
automatically approved for remote transmission. They are local working records;
review their actual contents and provenance.

## Prepare a synthetic rehearsal

From the repository root:

~~~powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
$jar = (Resolve-Path "build/cli/omicsmatic-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/ai-driver"
if (Test-Path -LiteralPath $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

The copied study contains completed synthetic association tables and a manual
replication/meta-analysis goal list. Built-in assistance is disabled.
The CLI steps below can be rehearsed by a human or script without a live agent.

For real files, supply the agent a reviewed manifest of permitted paths and
scientific metadata. If work is already completed, declare
[typed supplied results](study-pipeline-tutorial.md#reuse-completed-analyses-and-run-missing-work);
ask it to plan missing work instead of recomputing completed stages by default.

## Give the driver a concrete task

Use a task brief like this, replacing the bracketed scope with actual permissions:

~~~text
Prepare an OmicsMatic plan for this study.
Scientific question: [explicit question and estimand].
Starting inputs: [approved matrices, association tables or completed stages].
Requested work: [explicit goals, or review all eligible registered branches].
Permitted files: [exact paths and approved aggregate outputs].
Permitted actions: validate, resolve, plan, status, questions and verify.
After scientist approval, request run through the controlled executor.
Do not call approve, accept assumptions, upload files or run new executables.
Do not change hypotheses, thresholds, cohorts, units or exclusions silently.
Leave unknown ancestry, build, LD, overlap and instrument validity unresolved.
Return the contract, plan, missing inputs and exact source artifacts for review.
~~~

The brief communicates your intent; the host's permissions must enforce it.
For more automation, expose a small command wrapper or service rather than a
general shell. The wrapper is deployment work outside the built-in advisor.
There is no supported study YAML list of arbitrary MCP servers or agent tools.

Useful tool choices for a driver include read-only inspection of approved
artifacts, the documented OmicsMatic CLI and an approved public-literature search.
Install or expose additional bioinformatics software only when its task,
version, license, inputs and outputs have been reviewed. See
[tool selection and blocking](study-tools-literature.md#choose-and-limit-the-tools).

## Let the driver prepare the contract and plan

The agent can help translate your question into a draft study description,
but it must not invent sample sizes, independence, annotation versions or methods.
Use the actual starting stage: a table of completed meta-analysis results is
not a participant matrix.

These examples attribute actions to an actual agent. Replace
`REPORTED_MODEL_ID` with its reported model identity:

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-type llm --actor-id external-agent --actor-model REPORTED_MODEL_ID
java -jar $jar study plan --project . --actor-type llm --actor-id external-agent --actor-model REPORTED_MODEL_ID
java -jar $jar study status --project .
Get-Content resolved-study.yaml
Get-Content plans/*/analysis-plan.yaml
~~~

If you are manually rehearsing these steps, use
`--actor-type human --actor-id scientist-1`. For a non-AI script, use
`--actor-type external --actor-id rehearsal` and omit `--actor-model`.
Actor labels are recorded declarations, not authenticated identity or proof that
a model performed the work.

For a full analysis, the agent may propose `analysis.mode: auto`, then explain
the inferred branches. For a partial analysis it should use `manual` and a
specific goal list. There is no general `study run --only` shortcut.
For remaining work, repeat the approved run or import completed results and
request missing goals. See [the execution choices](study-getting-started.md#run-everything-the-rest-or-one-part).

## Keep scientist approval separate

The scientist reviews the exact study and plan, including input lineage,
effect definitions, testing family, cohort dependence, missing prerequisites,
tool configuration and permitted disclosure. The scientist then runs:

~~~powershell
java -jar $jar study approve --project . --approved-by scientist-1
~~~

Reserve this action through the surrounding permissions. A person-name string
does not authenticate the person or prevent an unrestricted agent from invoking
the command. Do not let the driver fill unknown assumptions just to clear a gate.

For this supervised pattern, prefer the explicit lifecycle.
`study auto` records command-line approval as part of its convenience workflow,
so it is unsuitable as an unrestricted driver command when a separate human
approval step is required.

## Execute and continue the approved work

After approval, the permitted execution service can run:

~~~powershell
java -jar $jar study run --project . --offline --actor-type llm --actor-id external-agent --actor-model REPORTED_MODEL_ID
java -jar $jar study status --project .
java -jar $jar study questions --project .
java -jar $jar study verify --project .
~~~

The rehearsal remains offline. A real literature or public-resource branch
requires approved online execution or supplied local resources.
If the scientist or a non-AI service actually invokes the command, record that actor.

The agent can inspect authorized manifests and summarize completed evidence.
Each reported number should link to the actual artifact and operation; a model's
narrative is not the statistical output. A waiting operation is not negative
biological evidence. Independent eligible branches can still finish.

On interruption, repeat the same `study run` command. Verified completed work is
reused by default. Do not use `--rerun` unless deliberate recomputation is wanted.
New inputs or changed scientific scope require the appropriate amendment rather
than edits to frozen results.

## Literature and bioinformatics with a driver

Ask the driver first to propose the registered goals, references and resource
needs. Native `literature_search`, source discovery and supported bioinformatics
operations can run under an approved plan without an AI account.

A separate agent may also search literature through tools that its host exposes.
That search is not automatically an OmicsMatic evidence operation. Preserve exact
queries, dates, source IDs, source text and appraisal, and use the
[supplied-literature route](study-pipeline-tutorial.md#reuse-supplied-literature-and-complete-missing-searches)
when incorporating it. Do not label an automated exchange as a human-transcribed
browser receipt.

For external tool execution, record the executable and version, command,
parameters, inputs, output hashes, references and limitations. Use a supported
typed import for completed results. A standalone command appearing in a vignette
does not make it an automatic study operation or an approved agent tool.

If a method needs LD, full regional QTL/GWAS statistics or valid instruments, the
agent should present the missing contract. It must not substitute lead SNPs for
complete regions or a gene eQTL for an unreviewed splicing QTL.

## Steer priorities and handle new directions

Tell the driver your primary question and reasons for prioritizing particular
evidence in the task brief. For example: “Discuss replicated molecular signals
first, compare contrary studies, and keep exploratory mechanisms distinct.”
Ask it to justify priority using uncertainty and evidence rather than P values alone.

For recorded manuscript emphasis, use
[principal-finding revision fields](study-tools-literature.md#emphasize-findings-with-an-evidence-trail).
If built-in `impact` assistance is enabled, review its rankings before accepting
them. There is no generic study `emphasize` prompt field.

When the driver proposes a new phenotype, exclusion, input source or analysis:

1. Record the observation or question with its source artifact.
2. Draft an amendment containing the exact change, rationale and current study hash.
3. Show the amendment's impact to the scientist.
4. Apply only after scientist approval, then re-plan, review and approve the new graph.

The [deterministic amendment example](ai-no-ai.md#add-an-analysis-later) shows
the syntax. A driver can prepare the proposal and review materials; it should
not impersonate the approver or execute arbitrary model-generated code.

## Check what was actually established

`study verify` checks local recorded integrity, not whole-computer non-egress,
agent behavior or scientific truth. Preserve the ledger, `SESSION_HISTORY.md`,
contracts and source artifacts alongside the report. Keep the external driver's
own exchanges and tool logs under the approved retention policy.

Compare [deterministic mode](ai-no-ai.md) and
[the built-in advisor](ai-advisor.md), or return to
[study setup](study-getting-started.md) and [AI connections](ai-connections.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [U.S. Department of Health and Human Services, Office for Civil Rights (2012) — Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule](../CITATIONS.md#hhs-hipaa-deidentification-2012)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
