# Connect an AI advisor: browser, API key or OAuth token

Use this page after [preparing your study](study-getting-started.md).
These settings configure OmicsMatic's built-in advisor. An
[external driver](ai-driver.md) has its own application, login and tool permissions.

## Will the JAR start an OAuth login?

**No. In the current source build, running the JAR does not open a browser,
display a device code, register an OAuth app or request your consent.**
Writing `auth: oauth` in `study.yaml` selects how an existing credential is used;
it does not start a login.

OmicsMatic currently handles two later steps:
[password-encrypted credential storage and supported renewal](ai-credentials.md).
`study credentials-save` asks for credentials you already obtained and encrypts
them. It does not contact a provider to obtain them. A browser already signed
into ChatGPT, Claude, Grok or Gemini does not automatically authorize Java.

This behavior is the same on Windows, Linux, macOS and a cloud VM.
A cloud VM means a computer you control remotely; it does not add a hosted
OmicsMatic sign-in service. Your browser can be on your laptop while the JAR runs
elsewhere, but the running JAR still needs its own credential.

| Step | Who performs it today? | What you see |
| --- | --- | --- |
| First login / authorization | You create an API key in the provider console, or separate OAuth tooling completes registration and consent. | The provider's website, or that tooling's login instructions. |
| Save the credential | OmicsMatic's `study credentials-save`. | Hidden passphrase and token prompts; a new encrypted file. |
| Use the credential | An enabled, policy-permitted advisor operation in `study run`. | A passphrase prompt if needed, then an API request. |
| Renew OAuth access | OmicsMatic, for the supported encrypted provider profiles. | Usually no browser; a saved refresh credential or workload identity obtains a replacement. Revoked consent still requires reauthorization elsewhere. |

Use a **current source build** for encrypted credentials. The executable remains
named `omicsmatic-0.3.7.jar`; the previously published binary with that name does
not necessarily include the newer commands. See
[build and runtime requirements](https://github.com/robbyjo/OmicsMatic/blob/main/docs/runtime-requirements.md).

Choose one connection below. Before resolving a new project, merge its YAML into
your existing `study.yaml`. For an existing project, use an approved amendment.
Keep your datasets and use a single top-level `assistance` mapping.

## Choose a connection

| Connection | First setup | What OmicsMatic does |
| --- | --- | --- |
| Browser handoff | Sign into your chosen AI website yourself. | Prepares a local packet for you to review, submit and import. No API credential is required. |
| OpenAI API key | Create a key in the OpenAI API platform. | Calls Responses using the saved key. |
| Gemini API key | Create a Gemini API key in Google AI Studio. | Calls GenerateContent using the saved key. |
| Claude API key | Create a workspace-scoped key in the Claude Console. | Calls Messages using the saved key. |
| Grok API key | Create a key in the xAI API console. | Calls Chat Completions using the saved key. |
| OpenAI or Gemini OAuth | Separate tooling completes the provider's first login. See [provider-specific instructions](#oauth-first-login-by-provider). | Uses the access token; a supported encrypted profile can renew it. |
| Claude federation | An administrator configures workload identity and a rotating identity-token file. | Exchanges the workload identity for API access. This is not a personal Claude browser login. |
| Compatible gateway | Obtain its endpoint, model and API key or OAuth instructions from the gateway operator. | Calls the configured compatible API. Gateway-specific OAuth renewal is not implemented. |

An API connection can incur charges. A browser subscription and API access are
separate arrangements; use the provider's documented route for your account.
`APPROVED_MODEL_ID` below is a placeholder for a model your account can use.

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

## On your own computer: a complete API-key setup

This route works today without an OAuth client or login helper. The OpenAI
example also shows where the key, encrypted file and YAML belong.

### 1. Create the key in your browser

Choose the provider you intend to use:

| Provider | Where to go | What to do |
| --- | --- | --- |
| OpenAI | [API platform keys](https://platform.openai.com/api-keys) | Sign in, choose the intended project and create a secret API key. See the [official quickstart](https://developers.openai.com/api/docs/quickstart). |
| Gemini | [Google AI Studio keys](https://aistudio.google.com/api-keys) | Select/create the intended project and create a Gemini API key. See [Google's key guide](https://ai.google.dev/gemini-api/docs/api-key). |
| Claude | [Claude Console](https://platform.claude.com/) | Select the intended workspace, open API keys and create a workspace-scoped key. See [API prerequisites](https://platform.claude.com/docs/en/api/overview). |
| Grok | [xAI API console](https://console.x.ai/) | Choose your team/project and create an API key. See the [official quickstart](https://docs.x.ai/developers/quickstart). |

Use the institution-approved account/project and its API access arrangements.
Keep the generated key ready for the hidden prompt below. Do not put it into
`study.yaml` or a shell command.

### 2. Build the JAR and save the key

From an existing source checkout with JDK 17 or newer, choose your operating system.
These commands build the application and define the JAR and credential paths.

**Windows, PowerShell:**

~~~powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
$jar = (Resolve-Path "build/cli/omicsmatic-0.3.7.jar").Path
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/openai.credentials.json"
java -jar $jar study credentials-save --provider openai --auth api_key --out $vault
java -jar $jar study credentials-check --file $vault
~~~

**Linux or macOS, Bash:**

~~~bash
bash ./gradlew executableJar --no-daemon --no-parallel
jar="$PWD/build/cli/omicsmatic-0.3.7.jar"
vault="$HOME/.omicsmatic/credentials/openai.credentials.json"
java -jar "$jar" study credentials-save --provider openai --auth api_key --out "$vault"
java -jar "$jar" study credentials-check --file "$vault"
~~~

Run in an ordinary interactive terminal. At `credentials-save` you will see:

~~~text
Unlock credential file openai.credentials.json:
Confirm credential-file passphrase:
API key or OAuth access token:
Saved password-encrypted credential file: ...
~~~

Type a strong passphrase of at least 12 characters, repeat it, then paste the API
key. Input is hidden. The command creates the parent directory and a new file;
it refuses to replace an existing file. For another provider, change `openai` to
`gemini`, `anthropic` or `xai` and choose a separate credential filename.

`credentials-check` asks for the passphrase again. Success means the file decrypts
and passes its integrity checks. **It does not contact the provider or prove
model access.** Neither command displays your secret.

### 3. Point study.yaml to that file

In your study's existing `assistance` mapping, use this OpenAI example and replace
the path and model:

~~~yaml
assistance:
  enabled: true
  provider: openai
  model: APPROVED_MODEL_ID
  auth: api_key
  credential_file: C:/Users/YOUR_ACCOUNT/.omicsmatic/credentials/openai.credentials.json
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

Typical Linux path:

~~~text
/home/YOUR_ACCOUNT/.omicsmatic/credentials/openai.credentials.json
~~~

Typical macOS path:

~~~text
/Users/YOUR_ACCOUNT/.omicsmatic/credentials/openai.credentials.json
~~~

Use the actual absolute path on the computer running Java. YAML does not expand
`$HOME`, `$vault` or `~`. Omit `api_key_env` and `oauth_token_env` when using a file.

For Claude, Grok or Gemini, change both the YAML provider and the credential file
to match the record you saved. Use that provider's model identifier.
Keep the [disclosure policy](#first-set-the-disclosure-policy) configured above.

### 4. Review the study and run it

Navigate to your study directory, retaining the absolute JAR path defined above.
For a **new, unresolved** project in PowerShell:

~~~powershell
java -jar $jar study validate study.yaml --check-files
java -jar $jar study resolve study.yaml --project . --actor-id scientist-1
java -jar $jar study plan --project . --actor-id scientist-1
~~~

Review `resolved-study.yaml` and `plans/<plan-id>/analysis-plan.yaml`.
When the plan is acceptable:

~~~powershell
java -jar $jar study approve --project . --approved-by scientist-1
java -jar $jar study run --project . --actor-id scientist-1
java -jar $jar study status --project .
~~~

In Bash use `"$jar"` in place of `$jar`. For an already resolved study, follow the
[amendment procedure](ai-no-ai.md#add-an-analysis-later) rather than resolving over it.

The enabled advisor operation unlocks the file when it needs a credential.
Enter the **file passphrase**, not your provider-account password. With an
unexpired API key, no browser opens. This run can make a real billable request.
An offline run cannot verify the connection. Inspect status and the operation's
`llm/` artifacts afterward; scientific prerequisites can delay the advisor.

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

## OAuth: understand the first login

OAuth has two different activities. **First authorization** opens the provider's
login page and asks which account and permissions to allow. **Renewal** uses
permission already granted to obtain another short-lived access token.
Automatic renewal does not implement the first activity.

These terms explain what a login tool or administrator supplies:

| Term | Plain-language meaning | Where it belongs |
| --- | --- | --- |
| Access token | A temporary credential accepted by the model API. | An encrypted record, or the environment variable named by `oauth_token_env`. |
| Refresh token | A credential used to obtain another access token. It is not the token sent to the model. | A supported encrypted renewal record. |
| Client ID | Identifies the app registration to which consent was given. It is not your email or model ID. | `--client-id` when importing a supported renewal record. |
| Client secret | A registration credential required by some Google OAuth clients. | The named secret input to `--client-secret-env`; it is encrypted with the record. |
| Audience / resource | Which API the access token is intended for. A token for another service can be rejected. | Chosen by the login flow, not invented in study.yaml. |
| Scopes | The permissions the user grants to the app. Login alone need not authorize model inference or renewal. | Requested and verified during authorization. |
| ID token | Evidence of the account's identity. | Validated by the sign-in implementation; it is not the model API credential. |

Your provider-account password is entered only on the provider's login page.
Your encrypted-file passphrase unlocks OmicsMatic's local file.
Neither belongs in `study.yaml`.

## OAuth first login by provider

### OpenAI: a first-login integration is still required

**There is currently no OmicsMatic command you can run to start Sign in with
ChatGPT.** If you have only a ChatGPT account and a study.yaml, you cannot complete
this route using the JAR alone. Use the API-key or browser-handoff walkthrough
today, or have an integrator supply a legitimate first-login implementation.

For that integrator, OpenAI's
[registration and sign-in procedure](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
requires a stable host ID, a loopback callback, state/nonce checks and PKCE.
The browser performs registration and consent; the helper exchanges the returned
code and validates identity and granted permissions. The issued client ID,
access token, refresh token and expiry then feed
[OmicsMatic's encrypted renewal import](ai-credentials.md#4-enable-openai-oauth-renewal).

The expected user experience is: run a separate helper, choose the ChatGPT
account/workspace in your browser, grant access, return to the helper, then save
the issued credentials. OmicsMatic does not currently provide that helper.
Do not copy Codex credentials, browser cookies or another application's tokens.

### Gemini: a worked browser login using Google's CLI

Here the login program is **gcloud**, not the OmicsMatic JAR.
Install the [Google Cloud CLI](https://docs.cloud.google.com/sdk/docs/install).
In [Google Cloud Console](https://console.cloud.google.com/), select your project,
enable the Generative Language API, configure the consent screen/test user,
and create a **Desktop app** OAuth client under Google Auth platform > Clients.
Download its client JSON outside your study. Google's
[Gemini OAuth quickstart](https://ai.google.dev/gemini-api/docs/oauth)
describes those screens.

This example uses Application Default Credentials (ADC). It writes Google's own
local credential cache, which is separate from OmicsMatic's encrypted file.
**The login command replaces any existing ADC for this environment.**
Use an appropriate development environment if other applications depend on it.
See the [gcloud command reference](https://docs.cloud.google.com/sdk/gcloud/reference/auth/application-default/login).

From your local terminal, substitute the downloaded file's absolute path:

~~~text
gcloud auth application-default login --client-id-file="ABSOLUTE_PATH_TO_CLIENT_JSON" --scopes="https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/generative-language.retriever"
~~~

Your browser opens Google's account/consent screens. Choose your configured user
and review the permissions. Return to the terminal when authorization finishes.
The scopes above follow Google's quickstart; your organization may require a
different approved registration. A successful login alone does not establish
that your account/model/quota configuration will accept an inference request.

For a short interactive trial, obtain an access token directly into the
environment without printing it.

**Windows, PowerShell:**

~~~powershell
$env:RESEARCH_AI_ACCESS_TOKEN = gcloud auth application-default print-access-token
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($env:RESEARCH_AI_ACCESS_TOKEN)) {
    Remove-Item Env:RESEARCH_AI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    throw "Google did not return an access token."
}
~~~

**Linux or macOS, Bash:**

~~~bash
RESEARCH_AI_ACCESS_TOKEN="$(gcloud auth application-default print-access-token)" || exit 1
test -n "$RESEARCH_AI_ACCESS_TOKEN" || exit 1
export RESEARCH_AI_ACCESS_TOKEN
~~~

Use this mapping in place of the API-key/file mapping:

~~~yaml
assistance:
  enabled: true
  provider: gemini
  model: APPROVED_MODEL_ID
  auth: oauth
  oauth_token_env: RESEARCH_AI_ACCESS_TOKEN
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

Review/resolve/approve as above, then run Java from that same terminal.
When the access token expires, repeat the token-capture command before another
run. OmicsMatic does not read Google's ADC cache and does not refresh an
environment token. Remove the session variable afterward with
`Remove-Item Env:RESEARCH_AI_ACCESS_TOKEN` in PowerShell or
`unset RESEARCH_AI_ACCESS_TOKEN` in Bash. This does not remove or revoke Google's
separate ADC credentials.

For OmicsMatic-managed renewal, the
[encrypted Google profile](ai-credentials.md#5-enable-gemini-oauth-renewal)
also needs the issued refresh token, client ID, actual expiry and any required
client secret. `gcloud ... print-access-token` supplies only the access token;
it does not supply that complete import. Have the OAuth integration securely
supply those fields rather than dumping a credential cache to the terminal.

The current Gemini adapter targets the Generative Language API, not Vertex AI.
It does not send a configurable `x-goog-user-project` quota header. If your
Google configuration requires one, this adapter cannot satisfy it; use a
supported API-key route or an approved compatible gateway.

### Claude: federation is an administrator setup

For a personal computer, the currently documented direct route is a Claude API
key. OmicsMatic does not offer a personal Claude browser-login command.

For workload identity, an organization administrator opens Claude Console >
Workload identity > Connect workload, configures the identity provider and
creates the issuer, service account and federation rule. The workload platform
must supply a fresh identity JWT to a protected file. Collect the rule,
organization, service-account and workspace IDs, then follow
[the encrypted Claude setup](ai-credentials.md#6-enable-claude-workload-renewal).
OmicsMatic exchanges that identity for API access and later repeats the exchange;
it does not configure the organization or rotate the upstream JWT itself.
See the [official federation walkthrough](https://platform.claude.com/docs/en/manage-claude/workload-identity-federation).

### Grok: use an API key

The xAI adapter supports API-key authentication. There is no Grok OAuth login or
renewal route in OmicsMatic. Create a key in the xAI console and use the
[encrypted API-key walkthrough](#on-your-own-computer-a-complete-api-key-setup).

## OAuth: use an already issued API access token

For an institutional compatible gateway, the operator must supply these concrete
items: the model API endpoint, model ID, login command or application, required
resource/audience and scopes, and a way to obtain an **API access token**.
There is no universal login command that works for every gateway.
Ask for a tested example for that gateway; a link to its ordinary chat website
is insufficient.

Use this mapping only if the gateway officially accepts bearer OAuth tokens
on its compatible Chat Completions endpoint:

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

After completing the gateway's login procedure, put its issued access token in
the named variable. For PowerShell 7:

~~~powershell
$env:RESEARCH_AI_ACCESS_TOKEN = Read-Host "Paste the gateway API access token" -MaskInput
java -jar $jar study run --project . --actor-id scientist-1
Remove-Item Env:RESEARCH_AI_ACCESS_TOKEN
~~~

The study must already be reviewed and approved. Environment-token mode does not
refresh tokens; renew them through the issuer's tooling. An
[encrypted gateway profile](ai-credentials.md) can save the token and check its
declared expiry, but it also does not add gateway-specific renewal.

For an external signed-in agent, configure login in that application and follow
the [driver guide](ai-driver.md#connect-the-external-driver).
That login does not populate OmicsMatic's built-in advisor credential.

## On a cloud VM or another remote computer

### Interactive run: put the credential where Java runs

A browser on your laptop and a JAR on a Linux VM are two separate processes on
two computers. Laptop environment variables and file paths do not appear on the
VM. Use a credential authorized for that environment.

For an **API key**, you can save the encrypted file directly in an SSH terminal
with `ssh -t` and `credentials-save`, or securely transfer the encrypted file
created above. The following transfer example assumes a Linux VM with OpenSSH
and an existing study. Replace `researcher@vm.example.org` with your SSH destination.
Choose a new destination filename if one already exists.

From your laptop, with `vault` still set to your encrypted API-key file:

~~~text
ssh researcher@vm.example.org 'install -d -m 700 "$HOME/.omicsmatic/credentials"'
scp "$vault" researcher@vm.example.org:.omicsmatic/credentials/openai.credentials.json
~~~

These commands work in PowerShell or Bash with the corresponding `vault` variable.
Send the encrypted file through SSH; keep its passphrase separate. On the Linux VM:

~~~bash
chmod 600 "$HOME/.omicsmatic/credentials/openai.credentials.json"
jar="$HOME/OmicsMatic/build/cli/omicsmatic-0.3.7.jar"
java -jar "$jar" study credentials-check --file "$HOME/.omicsmatic/credentials/openai.credentials.json"
~~~

Set `jar` to the actual executable location. Checking the file asks for its
passphrase on the VM. In the VM's study.yaml use the **VM path**, for example:

~~~yaml
assistance:
  enabled: true
  provider: openai
  model: APPROVED_MODEL_ID
  auth: api_key
  credential_file: /home/researcher/.omicsmatic/credentials/openai.credentials.json
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

Keep the disclosure policy and approval steps. Run from the VM's study directory:

~~~bash
java -jar "$jar" study run --project . --actor-id scientist-1
~~~

The VM needs outbound HTTPS access to the configured provider. Your input files
and deterministic execution remain on the analysis machine; the enabled remote
advisor receives the policy-permitted aggregate preview described above.

### OAuth on a machine without a browser

A loopback callback such as `127.0.0.1` returns to the computer running the
browser. Opening a VM-generated localhost URL on your laptop does not, by
itself, complete a callback listener on the VM.

For Google's worked example, run the same `gcloud auth application-default login`
command **on the VM**, adding `--no-browser`. Follow its remote-bootstrap
instructions on a trusted computer with a browser and current gcloud, then
return the result to the waiting VM command. The
[official gcloud instructions](https://docs.cloud.google.com/sdk/gcloud/reference/auth/application-default/login#--browser)
explain that exchange. Do not replace it by merely clicking a printed URL.
Capture the access token into the VM's environment and use the Gemini YAML above.

OpenAI documents a [self-hosted VM sign-in/session procedure](https://developers.openai.com/siwc/token-sharing-open-source/self-hosted-vms).
It still requires a first-login integration and per-host identity management;
the current JAR does not provide those steps. Moving an encrypted file alone
does not implement that full sign-in integration.

For a transferred rotating OAuth session, designate one machine to own refresh.
Two copies on different machines do not share OmicsMatic's local file lock and
can invalidate each other's refresh tokens. Prefer separately authorized
credentials where the provider supports them.

### Batch job, container or scheduler: supply the unlock secret

A background job cannot answer a terminal prompt. Make the encrypted file
available at a private, writable path outside the study, and configure:

~~~yaml
assistance:
  enabled: true
  provider: openai
  model: APPROVED_MODEL_ID
  auth: api_key
  credential_file: /home/researcher/.omicsmatic/credentials/openai.credentials.json
  credential_password_env: OMICSMATIC_CREDENTIAL_PASSWORD
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

Configure your scheduler/cloud secret manager to inject the passphrase into the
Java process as `OMICSMATIC_CREDENTIAL_PASSWORD`. YAML stores only that variable's
name. An encrypted renewable OAuth profile needs a writable parent directory
for its lock and atomic replacement; a read-only secret mount is unsuitable for
that profile. Container paths must match the paths visible inside the container.

For an interactive preparation session, you can set the variable without
displaying the passphrase. In PowerShell 7:

~~~powershell
$env:OMICSMATIC_CREDENTIAL_PASSWORD = Read-Host "Credential-file passphrase" -MaskInput
java -jar $jar study run --project . --actor-id scientist-1
Remove-Item Env:OMICSMATIC_CREDENTIAL_PASSWORD
~~~

Or in Bash:

~~~bash
read -r -s -p "Credential-file passphrase: " OMICSMATIC_CREDENTIAL_PASSWORD
printf '\n'
export OMICSMATIC_CREDENTIAL_PASSWORD
java -jar "$jar" study run --project . --actor-id scientist-1
unset OMICSMATIC_CREDENTIAL_PASSWORD
~~~

A scheduler may discard the submitting terminal's environment. Configure the
job's actual environment; setting a laptop variable is insufficient.
The provider credential must still be valid, and the study must still be approved.
Starting a batch job never opens an OAuth consent screen.

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
