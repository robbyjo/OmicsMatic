# Mode 2: AI as a scientific advisor

In advisor mode, OmicsMatic still runs the statistical analysis and records the
evidence. You ask an optional model to review bounded aggregate findings,
suggest next steps, help interpret literature or propose a draft. You review
the answer; advice does not approve a new analysis or change a scientific result.

**First understand the recipe.** `study.yaml` has a project, dataset/file
descriptions and hypotheses with analysis goals. The minimum top-level keys are
`schema_version`, `project`, `datasets` and `hypotheses`.
Read [Start here](study-getting-started.md) for the complete minimum, data locations,
matrix/result/completed-analysis routes and execution commands.
The [field guide](study-yaml-reference.md) covers optional settings.

These instructions target the current source build. Start with browser handoff
to learn the disclosure and review steps; switch to a documented API connection
when that route is approved.

**Choose your shell:** use either the **PowerShell (Windows)** or the
**Bash (Linux/macOS)** version of each example. Keep the same terminal session
while following a walkthrough so its variables remain defined. On macOS, run
`bash` in Terminal before using the Bash examples if your default shell is zsh.
For steps that need review or filled-in YAML, stop at that point before continuing.

## Decide what you want advice about

| Request | Setting | Your responsibility |
| --- | --- | --- |
| What should we investigate next? | `next_steps: true` | Check feasibility, assumptions and whether an amendment is needed. |
| What does the relevant literature say? | `literature: true` | Verify retrieved sources and contrary findings. |
| Which related traits might be informative? | `related_phenotypes: true` | Review labels, references and source suitability. |
| Which findings deserve the most discussion? | `impact: true` | Review comparative importance and preserve limitations. |
| Can I obtain a first manuscript draft? | `manuscript: true` | Verify every claim, number, reference and disclosure before use. |

Enable only the purposes you need. Advice alone requires neither manuscript
drafting nor impact ranking. No arbitrary shell or bioinformatics tools are
given to the advisor.

## Prepare a fresh synthetic study

From the repository root:

**Windows (PowerShell):**

~~~powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
$jar = (Resolve-Path "build/cli/omicsmatic-0.3.7.jar").Path
$project = Join-Path (Get-Location) "build/ai-advisor"
if (Test-Path -LiteralPath $project) { throw "Choose a fresh project directory." }
Copy-Item examples/study-pipeline $project -Recurse
Set-Location $project
~~~

**Linux / macOS (Bash):**

~~~bash
bash ./gradlew executableJar --no-daemon --no-parallel || exit 1
jar="$PWD/build/cli/omicsmatic-0.3.7.jar"
project="$PWD/build/ai-advisor"
if [ -e "$project" ]; then
    printf '%s\n' "Choose a fresh project directory." >&2
    exit 1
fi
cp -R examples/study-pipeline "$project" || exit 1
cd "$project" || exit 1
~~~

Its `data/` directory contains synthetic association tables.
Keep the existing project ID/title, cohorts, datasets, dictionary and hypotheses.
**Merge** this policy inside the existing project, and add one top-level
assistance block:

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
  impact: false
  manuscript: false
  response_files:
    - responses/H1-advisor/receipt.yaml
~~~

This is a fragment, not a replacement contract. The receipt need not exist yet.
No credential is required to prepare browser packets. A real remote disclosure
still needs your review of the exact prompt.

For an already resolved project, use `changes.configure.assistance` and the
appropriate project policy in an approved amendment; see the
[existing-project example](study-pipeline-tutorial.md#data-dictionaries-optional-assistance-and-first-drafts).

## Run the deterministic part first

**Windows (PowerShell):**

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --actor-id scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
Get-Content resolved-study.yaml
Get-Content plans/*/analysis-plan.yaml
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --actor-id scientist-1
java -jar $jar study status --project .
Start-Process index/index.html
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study validate study.yaml --check-files
java -jar "$jar" study resolve study.yaml --actor-id scientist-1
java -jar "$jar" study plan --project . --actor-id scientist-1
cat resolved-study.yaml
cat plans/*/analysis-plan.yaml
java -jar "$jar" study approve --project . --approved-by scientist-1
java -jar "$jar" study run --project . --actor-id scientist-1
java -jar "$jar" study status --project .
if [ "$(uname -s)" = "Darwin" ]; then
    open index/index.html
elif [ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && command -v xdg-open >/dev/null 2>&1; then
    xdg-open index/index.html
else
    printf 'Open this report in a browser: %s/index/index.html\n' "$PWD"
fi
~~~

Review and approve as in deterministic mode. The independent numerical work can
complete while the advisor waits for a response. The run can return nonzero
because an input is pending; inspect the waiting operation instead of assuming
that the numerical analysis failed.

Open the waiting advisor operation in the project index and locate its
`handoff/index.html`. There can be several handoffs when several purposes or
hypotheses are enabled. Match the packet's purpose and hypothesis.

## Complete a browser exchange

Set this to the **actual** handoff directory shown by the operation:

**Windows (PowerShell):**

~~~powershell
$handoff = "FULL_PATH_FROM_THE_WAITING_OPERATION/handoff"
java -jar $jar study handoff-open --handoff $handoff --browser none
~~~

**Linux / macOS (Bash):**

~~~bash
handoff="FULL_PATH_FROM_THE_WAITING_OPERATION/handoff"
java -jar "$jar" study handoff-open --handoff "$handoff" --browser none
~~~

`--browser none` verifies and reports the local packet path for manual opening.
An installed browser such as `edge` or `firefox` can be requested instead.
The adapter never signs in or submits anything for you.

1. Read the entire packet and exact prompt. Confirm that the aggregate content is
   permitted for your chosen AI service.
2. Sign in through that service's normal browser interface. Only when disclosure
   is authorized, submit the **exact prompt** from the packet.
3. Save a copy of the packet's `response-template.yaml` outside the frozen
   handoff folder, for example as `response.yaml` in the project directory.
4. Fill in the full model answer, provider/model declaration, submission and
   receipt timestamps, references, disposition and rationale. Preserve its YAML
   structure. The requested JSON `proposals` contract must remain intact.
5. Confirm `exact_prompt_submitted`, `disclosure_approved` and
   `no_patient_data` only when they are true. Review the proposals and record
   your acceptance or rejection honestly.

Do not edit the packet or shorten the prompt while attesting that it was exact.
For instructions beyond the packet's scope, use a separately recorded
review or a supported study change.

Import and verify the response, then resume:

**Windows (PowerShell):**

~~~powershell
java -jar $jar study handoff-import response.yaml --handoff $handoff --out responses/H1-advisor --actor-id scientist-1
java -jar $jar study handoff-verify responses/H1-advisor/receipt.yaml
java -jar $jar study run --project . --actor-id scientist-1
java -jar $jar study verify --project .
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study handoff-import response.yaml --handoff "$handoff" --out responses/H1-advisor --actor-id scientist-1
java -jar "$jar" study handoff-verify responses/H1-advisor/receipt.yaml
java -jar "$jar" study run --project . --actor-id scientist-1
java -jar "$jar" study verify --project .
~~~

Use a fresh receipt output directory for a new import. A receipt is bound to the
exact request, purpose, hypothesis and policy. It is not interchangeable with
another handoff. Completed upstream numerical work is reused when still valid.

The browser exchange is human-attested. Hashes establish local integrity, not
that the provider/model identity is independently authenticated or that the
answer is scientifically correct.

## Use an API key or OAuth token instead

The [AI connections guide](ai-connections.md) provides complete `study.yaml`
mappings and PowerShell/Bash credential setup for:

- `provider: openai` with a model and `api_key_env`.
- `provider: gemini` with a model and `api_key_env`.
- `provider: openai_compatible` with HTTPS endpoint, model and API key.
- The same compatible route with `auth: oauth` and `oauth_token_env`.

Choose one mapping instead of the browser mapping. API configurations omit browser
`response_files`. Keep the aggregate disclosure policy and enable only intended
purposes. Credentials stay in environment variables or an [encrypted credential file](ai-credentials.md); YAML contains only the reference.

OAuth uses credentials legitimately issued for the configured API. OmicsMatic can [renew supported encrypted credentials](ai-credentials.md) for OpenAI, Gemini and Claude federation; environment-only tokens need external renewal. Use [study login](ai-login.md) for initial OpenAI/Gemini sign-in or guided Claude/Grok key setup before running the study. An external
agent's or chat website's sign-in is a separate connection.

After review and approval, `study run` can make the enabled requests automatically.
This avoids manual browser transcription, but still requires scientific review
of the outputs and any proposed study change. It can incur charges.
`--offline` blocks built-in provider calls.

## Search literature and call bioinformatics tools

For a reproducible literature search without AI, request the native
`literature_search` goal and configure `analysis.bioinformatics`.
For optional AI literature assistance, additionally enable `assistance.literature`.
These are separate operations with separate source and response requirements.

The advisor can suggest related phenotypes, QTL sources, questions or code text.
It does not receive a general tool menu or automatically install/run arbitrary
bioinformatics software. Only registered analyses authorized by the study
contract execute. For example:

~~~yaml
assistance:
  allow_analyses: [fixed_random_comparison]
~~~

Merge that setting into your existing assistance mapping if you want the
registered local fixed/random sensitivity comparison and its prerequisites are
available. It can run independently of a successful model response.
This setting is not an executable allowlist. Leave-one-cohort-out also has a
separate automatic rule for suitable studies with at least three explicit cohorts.

Use [literature and tool controls](study-tools-literature.md) for available
sources, method choices, exclusions and blocking unwanted network/tool access.
There is no general `blocked_tools` field to paste into the study.

## Review proposals and emphasize findings

Read the advisor's decision record alongside the actual source artifacts.
Check every number and citation. Classify a proposal as a possible interpretation,
a question needing more input, a registered sensitivity analysis or a new
scientific direction needing an amendment.

For narrative priorities, enable impact assessment only when wanted, review its
comparison and then record accepted priorities in a manuscript revision using
`impact_rank`, `impact_reason` and linked Results/source IDs.
See [the worked emphasis recipe](study-tools-literature.md#emphasize-findings-with-an-evidence-trail).
There is no free-form `system_prompt` or `emphasize_genes` setting in the
current study assistance schema.

Never discard contrary evidence because a model favors an exciting mechanism.
A proposed code snippet is quarantined text; implementing a new adapter requires
code review and validation outside the advisor proposal handler.

## API collaboration and patient-data boundaries

The built-in preview omits raw feature identifiers, local paths and dictionary
prose. It can contain gene symbols, phenotype labels, structured metadata and
numeric aggregate findings. Those may still disclose confidential research
information. It does not give the model input files, phenotype rows, matrices,
filesystem access or a shell.

Classification relies on correct metadata; it is not automatic PHI detection
or privacy certification. A remote driver with wider file/tool access has a
different boundary. See [connections and policy](ai-connections.md#first-set-the-disclosure-policy).

Compare [deterministic mode](ai-no-ai.md) and
[external driver mode](ai-driver.md), or return to the
[beginner study setup](study-getting-started.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [U.S. Department of Health and Human Services, Office for Civil Rights (2012) — Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule](../CITATIONS.md#hhs-hipaa-deidentification-2012)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
