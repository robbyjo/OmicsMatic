# Mode 2: AI as a scientific advisor

JLinAlg v0.3.7 offers opt-in AI collaboration for bounded next-step advice,
literature review and explicitly enabled first manuscript drafts. JLinAlg
constructs the evidence preview, runs the statistics and records provenance.
The model proposes interpretations and follow-up; its answer is not a numerical
result or approval to change the study.

## Prepare the example and disclosure policy

Start at the repository root with a new copy of the synthetic result-first study:

~~~powershell
.\gradlew.bat executableJar
$jar = (Resolve-Path "build/cli/jlinalg-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/ai-advisor"
if (Test-Path $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

Before resolving this new study, merge the following `data_policy` into its
existing `project` mapping, and add `assistance` at the top level. Preserve its
datasets, cohorts, hypotheses and other project fields. Do not create a second
`project` mapping.

~~~yaml
project:
  data_policy:
    llm:
      remote_transmission: aggregate_results
      remote_patient_level_data: deny
assistance:
  enabled: true
  provider: browser
  next_steps: true
  literature: false
  manuscript: false
  response_files:
    - responses/H1-advisor/receipt.yaml
~~~

This example enables advice only. Browser handoff is the default transport and
needs no API credential to prepare. The policy permits consideration of a
bounded aggregate preview; a scientist still reviews the exact prompt before
manual disclosure. For an already resolved study, configure assistance through
an approved amendment instead of rewriting its base file.

## Run the deterministic analysis first

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-id scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
Get-Content plans/*/analysis-plan.yaml
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --actor-id scientist-1
java -jar $jar study status --project .
~~~

The independent numerical work completes while advice waits for a browser
response. The run command returns a nonzero exit status while required input is
pending; inspect `study status` and the waiting operation rather than treating
this as a failed numerical analysis. Open `index/index.html` to find its
`handoff/index.html`. Set `$handoff` to that exact local directory:

~~~powershell
$handoff = "FULL_PATH_FROM_THE_WAITING_OPERATION/handoff"
java -jar $jar study handoff-open --handoff $handoff --browser none
~~~

`--browser none` reports the verified packet path for manual opening. Select an
installed browser such as `edge` or `firefox` to request opening it. The adapter
never submits the prompt or signs into a website for you.

## Review and import the answer

Inspect the complete prompt. Only if disclosure is authorized, copy that exact
prompt into your chosen AI service. Copy `response-template.yaml` outside the
frozen packet to `response.yaml`. Fill in the complete answer, provider/model
declaration, timestamps, references, disposition and rationale. Advice must use
the packet's requested JSON `proposals` contract. Confirm `exact_prompt_submitted`,
`disclosure_approved` and `no_patient_data` only when true.

~~~powershell
java -jar $jar study handoff-import response.yaml --handoff $handoff --out responses/H1-advisor --actor-id scientist-1
java -jar $jar study handoff-verify responses/H1-advisor/receipt.yaml
java -jar $jar study run --project . --actor-id scientist-1
java -jar $jar study verify --project .
~~~

The receipt preserves the reviewed exchange and matches the exact request.
Completed upstream results are reused. Browser attribution, timestamps and
citations are human-attested; hashes establish integrity rather than provider
authenticity or scientific correctness. Unsupported or study-changing proposals
require scientist review and an amendment. Model-generated code is not executed.
Preapproved registered sensitivity analyses run locally under the study contract.

## API collaboration and patient-data boundaries

Explicit alternatives are OpenAI Responses, Gemini GenerateContent and
OpenAI-compatible APIs. See the [connection instructions](bioinformatics-evidence.md#optional-api-key-and-oauth-access-token-connections)
for model, endpoint and environment-variable credentials. Provider calls can
incur charges. Browser preparation remains local; sending its prompt is a
separate human action.

The built-in assistance adapter does not upload input files or grant filesystem,
shell, phenotype-row or sample-matrix access. Its allowlisted preview omits raw
feature identifiers, dictionary prose and local paths; it can include gene
symbols, phenotype labels, structured study metadata and numeric aggregate
findings. These can still be confidential. `deny_all` blocks remote advice and
`--offline` blocks built-in network requests. Classification depends on correct
input metadata; this is not automatic PHI detection or a regulatory certification.
It cannot control a separate agent or a user's manual uploads.

Compare [no AI](ai-no-ai.md) and [AI as an external driver](ai-driver.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as JLinAlg when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [U.S. Department of Health and Human Services, Office for Civil Rights (2012) — Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule](../CITATIONS.md#hhs-hipaa-deidentification-2012)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
