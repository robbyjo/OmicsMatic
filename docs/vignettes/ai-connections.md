# Connect an AI advisor: browser, API key or OAuth token

Use this page after [preparing your study](study-getting-started.md).
These settings configure OmicsMatic's built-in advisor. An
[external driver](ai-driver.md) has its own application, login and tool permissions.

**Save credentials once:** the current source build supports
[password-encrypted files and automatic renewal](ai-credentials.md), including
OpenAI OAuth, Google OAuth and Claude federation. That guide also covers
Claude and Grok API keys. The environment-variable alternatives remain below.

Choose one transport below. Before resolving a new project, merge its YAML into
your existing `study.yaml`. For an existing project, use an approved amendment.
Do not replace your datasets or create duplicate top-level keys.

## Choose a connection

| Connection | What you need | How an exchange happens |
| --- | --- | --- |
| Browser handoff | Access to your chosen AI website | OmicsMatic prepares a local packet; you review and manually submit its exact prompt, then import the response. |
| OpenAI API | An approved API model and API credential | OmicsMatic calls the Responses API. |
| Gemini API | An approved API model and API credential | OmicsMatic calls GenerateContent. |
| OpenAI-compatible API with key | A documented compatible HTTPS endpoint, model and key | OmicsMatic calls non-streaming Chat Completions. Compatibility must include the required response format. |
| OpenAI-compatible API with OAuth access token | The same API plus an already issued access token accepted by that endpoint | OmicsMatic sends the token as a bearer credential. Environment-token mode does not perform login or refresh; encrypted provider profiles can renew supported credentials. |

API access and browser sign-in are separate arrangements. Do not assume that a
consumer chat subscription grants the API access your endpoint requires.
An API connection can incur charges. Use an approved model identifier from your
provider; `APPROVED_MODEL_ID` below is a placeholder, not a model name.

## First set the disclosure policy

For remote aggregate assistance, merge this into the existing project:

~~~yaml
project:
  data_policy:
    llm:
      remote_transmission: aggregate_results
      remote_patient_level_data: deny
~~~

The built-in adapter constructs a bounded evidence preview. It does not upload
input files or give the model a filesystem, shell, participant table or sample
matrix. The preview can include gene symbols, phenotype labels, study metadata
and aggregate statistics; these can still be confidential. Raw feature IDs,
dictionary prose and local paths are omitted by the preview adapter.

This policy is permission for the configured bounded route, not automatic
identification or removal of sensitive information. Review what your study can
disclose. `deny_all` blocks remote requests even when a key is present.
Policies requiring extra per-request checks that the adapter cannot enforce
remain blocking. A separate agent or a manual upload is outside this adapter's
control. See the [data-policy field guide](study-yaml-reference.md#reference-resources-and-network-policy).

## Browser handoff: no API key required

Add this at the top level:

~~~yaml
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

The receipt path can be declared before it exists. After deterministic work,
the advisor operation waits for that response. Preparing the packet does not
sign into a website or submit a prompt.

Follow the [complete advisor handoff](ai-advisor.md#complete-a-browser-exchange),
including response-template fields, import, verification and resume.
This is a practical way to use an institution-approved browser account while
reviewing each exact disclosure.

## OpenAI with an API key

Use this top-level mapping in place of the browser mapping:

~~~yaml
assistance:
  enabled: true
  provider: openai
  model: APPROVED_MODEL_ID
  auth: api_key
  api_key_env: RESEARCH_AI_KEY
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

`api_key_env` contains the **name of an environment variable**, never the secret.
The process that runs Java must receive that variable.

In PowerShell 7, set a session-only value without showing it in the command:

~~~powershell
$env:RESEARCH_AI_KEY = Read-Host "Paste the approved API key" -MaskInput
~~~

If `-MaskInput` is unavailable in your PowerShell version, use a secure prompt:

~~~powershell
$credential = Get-Credential -UserName "api" -Message "Enter the API key in the password field"
$env:RESEARCH_AI_KEY = $credential.GetNetworkCredential().Password
Remove-Variable credential
~~~

Both methods create a plaintext environment value for the Java process.
Use your institution's secret manager for production. Do not echo it, commit it,
put it in YAML, or include it in screenshots. Check presence without printing it:

~~~powershell
if ([string]::IsNullOrWhiteSpace($env:RESEARCH_AI_KEY)) { throw "Credential is missing." }
~~~

After configuring the policy and reviewing/resolving/approving the study, run
from the same terminal:

~~~powershell
java -jar $jar study run --project . --actor-id scientist-1
~~~

This can send a real provider request. `--offline` blocks it.
When finished with the session:

~~~powershell
Remove-Item Env:RESEARCH_AI_KEY
~~~

Removing your environment value does not revoke the provider's key.
See the official [API authentication reference](https://developers.openai.com/api/reference/overview)
for credential handling. The adapter uses Responses with `store: false`;
that setting alone is not a guarantee of zero provider retention.

## Gemini with an API key

Use the same environment-variable procedure, then this alternative mapping:

~~~yaml
assistance:
  enabled: true
  provider: gemini
  model: APPROVED_MODEL_ID
  auth: api_key
  api_key_env: RESEARCH_AI_KEY
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

The adapter selects the Gemini endpoint; do not add `endpoint` for either
`gemini` or `openai`. Omitting `api_key_env` uses the built-in defaults
`GEMINI_API_KEY` or `OPENAI_API_KEY`, respectively. Explicitly naming your
variable makes shared instructions easier to audit.

## An OpenAI-compatible API with a key

Your institution may provide a compatible gateway. Ask its administrator for
the exact endpoint, model ID and credential procedure. Replace the example URL:

~~~yaml
assistance:
  enabled: true
  provider: openai_compatible
  model: APPROVED_MODEL_ID
  endpoint: https://gateway.example.org/v1/chat/completions
  auth: api_key
  api_key_env: RESEARCH_AI_KEY
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

The endpoint must be HTTPS and end in `/chat/completions`, with no embedded
credentials, query string or fragment. Redirects are disabled.
This adapter requests a non-streaming response; a service requiring a different
protocol or a streaming-only endpoint is not interchangeable.

## OAuth: use an already issued API access token

Use this only when the gateway officially accepts an OAuth access token for
the declared model API:

~~~yaml
assistance:
  enabled: true
  provider: openai_compatible
  model: APPROVED_MODEL_ID
  endpoint: https://gateway.example.org/v1/chat/completions
  auth: oauth
  oauth_token_env: RESEARCH_AI_ACCESS_TOKEN
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

1. Obtain access through your institution's or provider's approved login tooling.
   The token needs the audience and scopes required by that API.
2. Put the **access token** in the terminal environment; do not use an ID token,
   refresh token, browser cookie or unrelated application's saved credential.
3. Validate the contract, review and approve the plan, then run Java in that
   environment. Renew expired tokens using the issuer's tooling.

For PowerShell 7:

~~~powershell
$env:RESEARCH_AI_ACCESS_TOKEN = Read-Host "Paste the approved API access token" -MaskInput
java -jar $jar study run --project . --actor-id scientist-1
Remove-Item Env:RESEARCH_AI_ACCESS_TOKEN
~~~

OmicsMatic does not register an OAuth application, open a device login or
request consent. Tokens supplied only through environment variables are not
renewed. For persistent credentials, use the [encrypted-file workflow](ai-credentials.md).
OAuth is supported for OpenAI, Gemini, Claude's federated API route and an
authorized compatible gateway; Grok uses API keys.
Choose credential_file or the appropriate token variable, not both.

A ChatGPT or Codex sign-in is not an access token to copy into this mapping.
If your workflow uses an external signed-in agent, configure it in that client
and follow the [driver guide](ai-driver.md#connect-the-external-driver).
The [advanced connection notes](bioinformatics-evidence.md#optional-api-key-and-oauth-access-token-connections)
explain the distinction from separately registered sign-in integrations.

## Turn on only the assistance you want

| Key | What it requests |
| --- | --- |
| `next_steps: true` | Bounded proposals, scientific questions and follow-up advice. |
| `literature: true` | A separate AI literature interpretation/search exchange; requires mapped genes and retrieval evidence. |
| `related_phenotypes: true` | Cited phenotype-label suggestions for bounded public GWAS discovery; suggestions remain unreviewed. |
| `impact: true` | Study-wide comparative impact assessment from the finding inventory. |
| `manuscript: true` | An explicitly enabled first draft; also requests impact assessment. |

Start with `next_steps`. Adding purposes may create more calls, browser packets,
prerequisites and costs. A key alone enables none of them.
The schema defines call, payload, output-token and timeout bounds.
`manuscript: true` requests impact even if you also write `impact: false`;
leave manuscript disabled when you want advice only.

The OpenAI literature/related-phenotype route requests hosted `web_search`;
Gemini requests Google Search grounding. The compatible adapter does not attach
an equivalent search tool. A chat response without usable retrieval evidence
does not complete a literature review. See
[the official OpenAI web-search guide](https://developers.openai.com/api/docs/guides/tools-web-search)
and [the local tool-control guide](study-tools-literature.md).

## Diagnose a connection without guessing

Validate locally first; `study validate` does not test your credentials.
After an actual attempt, inspect the operation's `llm/` artifacts and status:
the request/prompt, redacted response, requested/returned model and decision
show what happened.

| Problem | Check |
| --- | --- |
| Missing credential | Variable name and the terminal that launched Java. |
| HTTP denial or authentication error | Correct endpoint, token audience/scope, model access and expiry. |
| Policy or offline block | Resolved disclosure/resource policy and `--offline`. |
| Truncated, malformed or unsupported reply | Provider response contract, model capabilities and output budget. |
| Literature still waiting | Gene annotation, retrieved source metadata, browser receipts and search availability. |

Do not weaken the study policy to make an error disappear. Resolve the cause
and resume; retain unsuccessful attempts. Local adapter tests do not establish
that your account, chosen model or gateway works. No paid request is needed to
rehearse the [browser advisor](ai-advisor.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [U.S. Department of Health and Human Services, Office for Civil Rights (2012) — Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule](../CITATIONS.md#hhs-hipaa-deidentification-2012)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
