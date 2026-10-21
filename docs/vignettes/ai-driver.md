# Mode 3: AI as an external workflow driver

An external AI agent can help translate a research objective into a JLinAlg
study contract, coordinate documented CLI commands, inspect authorized outputs
and propose amendments. JLinAlg v0.3.7 supplies the deterministic planner,
execution engine and evidence ledger. The agent is separately operated software;
JLinAlg does not bundle an autonomous agent runtime or grant it access to data.
This vignette describes a supervised collaboration pattern using existing CLI
interfaces, not an autonomous scientific decision-maker.

## Agree on scope and access

Specify the estimand, outcomes, cohort relationships, data sources, approved
operations, resource limits and decisions reserved for the scientist. The agent
may draft a study, but must leave unknown ancestry, LD, genome build, units and
instrument validity unresolved rather than inventing them.

For patient-level work, keep data and execution in an institution-controlled
environment. Use a separate account or execution service with permissions that
expose only reviewed aggregate artifacts to a remote agent. A prompt instruction
is not an access boundary: if an agent can read raw files or invoke arbitrary
networked shell commands, JLinAlg's assistance policy cannot prevent disclosure.
Even `index/LLM_CONTEXT.md` and the generated `AGENTS.md` are local working
artifacts, not automatically approved external payloads.

## Prepare an offline synthetic rehearsal

From the repository root:

~~~powershell
.\gradlew.bat executableJar
$jar = (Resolve-Path "build/cli/jlinalg-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/ai-driver"
if (Test-Path $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

The fixture contains synthetic association results. Its built-in assistance is
disabled, so this rehearsal itself sends no model requests. A real external
agent connection and its disclosure controls must be established independently.

## Let the agent prepare a reviewable plan

These commands show the agent's proposed actions. Replace `REPORTED_MODEL_ID`
with the actual model identifier for an agent-run project:

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-type llm --actor-id external-agent --actor-model REPORTED_MODEL_ID
java -jar $jar study plan --project . --actor-type llm --actor-id external-agent --actor-model REPORTED_MODEL_ID
java -jar $jar study status --project .
Get-Content resolved-study.yaml
Get-Content plans/*/analysis-plan.yaml
~~~

Actor labels record who is reported to have performed a step. They are not
authenticated identities or proof that an LLM ran. An ordinary scripted rehearsal
should instead use `--actor-type external --actor-id rehearsal` and omit the
model. Never attribute a human's actions to a model merely because this guide
contains agent examples.

The scientist reviews the exact contract and operation graph, including missing
inputs, statistical assumptions and any proposed disclosure. The scientist then
runs the separate approval command:

~~~powershell
java -jar $jar study approve --project . --approved-by scientist-1
~~~

Reserve this command for the authorized scientist through the surrounding
execution environment. Do not give the agent an unrestricted approval shell.
`study auto` records command-line approval as part of its convenience workflow;
use the explicit lifecycle here to preserve a separate human review step.

## Coordinate execution and evidence review

After approval, the agent can request the permitted run, status and verification
commands through the controlled execution service:

~~~powershell
java -jar $jar study run --project . --offline --actor-type llm --actor-id external-agent --actor-model REPORTED_MODEL_ID
java -jar $jar study status --project .
java -jar $jar study questions --project .
java -jar $jar study verify --project .
~~~

JLinAlg runs the registered numerical operations and writes manifests, hashes,
diagnostics, reports, an append-only ledger and `SESSION_HISTORY.md`. The agent
can summarize approved evidence using result IDs; each reported number must
resolve to its actual source artifact. Missing MR, colocalization, fine-mapping
or enrichment prerequisites remain questions, not successful analyses. Standalone
network and other methods outside the study registry require separately reviewed
commands and captured provenance.

For new hypotheses, exclusions, phenotypes or data sources, prepare an amendment,
show its impact and obtain scientist approval. Do not rewrite resolved contracts,
approve on the scientist's behalf or execute arbitrary model-generated code.
Keep post-hoc sensitivity findings distinct from the primary analysis.

## Boundaries and verification

`study verify` checks recorded local integrity; it does not certify scientific
truth, remote-agent behavior or whole-computer non-egress. `--offline` controls
built-in JLinAlg requests, not the agent's own network connection. Use operating
system permissions, network controls and institution-approved disclosure review
to enforce the wider boundary. No live external agent or remote provider is
required to rehearse these CLI steps.

Compare [no AI](ai-no-ai.md) and [the built-in AI advisor](ai-advisor.md), whose
allowlisted provider adapter has a narrower disclosure surface.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

- [Sandve et al. (2013): reproducible computational research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C (2013): evidence provenance](../CITATIONS.md#w3c-prov-o-2013)
- [HHS OCR (2012): de-identification guidance](../CITATIONS.md#hhs-hipaa-deidentification-2012)
- [NIST (2024): generative AI risk management](../CITATIONS.md#nist-genai-profile-2024)

<!-- SCIENTIFIC-CITATIONS:END -->
