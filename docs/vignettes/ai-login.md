# Sign in and save AI credentials

The current source build includes `study login`. For OpenAI and Gemini, this
command opens your browser, receives the authorization callback, validates the
signed account identity and required permissions, and saves access/refresh
credentials in a password-encrypted file. No tokens need to be copied by hand.

Run login **once before running your study**. Writing `auth: oauth` in
`study.yaml` does not start a login, and `study run` never unexpectedly opens a
consent screen. Login itself sends no study data and makes no inference request.

## Which login does each provider support?

| Provider | Command route | Prerequisite |
| --- | --- | --- |
| OpenAI / ChatGPT | `study login --provider openai`: browser OAuth and automatic renewal. | A ChatGPT account permitted to grant the requested model access. The helper performs the documented dynamic client registration. |
| Gemini | `study login --provider gemini --client-json FILE`: browser OAuth and automatic renewal. | Your Google Cloud project, enabled Generative Language API, consent configuration and Desktop app client JSON. |
| Claude | `study login --provider anthropic --auth api_key`: guided console/key setup; or `--auth wif` for organization-configured federation. | A workspace API key, or an administrator-configured issuer/rule/service account and current identity JWT. |
| Grok | `study login --provider xai --auth api_key`: guided console/key setup. | An xAI API key. No public Grok API OAuth flow is configured. |

Claude.ai subscription OAuth is reserved for Anthropic's own applications; its
[credential rules](https://code.claude.com/docs/en/legal-and-compliance#authentication-and-credential-use)
do not permit adding Claude.ai login to a third-party application.
The API's [supported authentication routes](https://platform.claude.com/docs/en/manage-claude/authentication)
include API keys and workload federation. xAI's
[public API setup](https://docs.x.ai/developers/quickstart) uses API keys.
The helper follows those supported routes and reports an explanation if you
explicitly request an unsupported OAuth mode.

## 1. Build the current executable

Use JDK 17 or newer and a current source checkout. These login commands are newer
than the previously published v0.3.7 binary, even though the source build still
produces a JAR with that version in its filename.

Choose your shell, and keep the same terminal session open for subsequent steps.
On macOS, run `bash` in Terminal if your default shell is zsh.

**Windows (PowerShell), from the repository root:**

~~~powershell
.\gradlew.bat executableJar --no-daemon --no-parallel
$jar = (Resolve-Path "build/cli/omicsmatic-0.3.7.jar").Path
~~~

**Linux / macOS (Bash), from the repository root:**

~~~bash
bash ./gradlew executableJar --no-daemon --no-parallel || exit 1
jar="$PWD/build/cli/omicsmatic-0.3.7.jar"
~~~

## 2. OpenAI: run the login command

Choose a new credential filename outside the study and outside source control.

**Windows (PowerShell):**

~~~powershell
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/openai-oauth.credentials.json"
java -jar $jar study login --provider openai --out $vault
~~~

**Linux / macOS (Bash):**

~~~bash
vault="$HOME/.omicsmatic/credentials/openai-oauth.credentials.json"
java -jar "$jar" study login --provider openai --out "$vault"
~~~

What happens:

1. OmicsMatic asks for an encryption passphrase of at least 12 characters and
   confirmation, using hidden input. This is your **local file passphrase**.
2. The terminal displays **Continue with ChatGPT** and opens the provider's
   authorization URL in your system browser. If opening fails, open the printed
   URL manually on the same computer.
3. In the browser, choose the intended ChatGPT account/workspace and review the
   model-access permissions. Enter your provider password only on its website.
4. The provider returns the browser to a temporary local callback. Return to
   your terminal: the browser's acknowledgement alone does not prove completion.
5. After signature, identity, permission and callback checks succeed, the terminal
   reports the encrypted file path and prints the provider/auth/file YAML fields.

The host ID is generated once in `~/.omicsmatic/openai-host.json` and reused on
that computer. It is an identifier, not a secret. Account identity, issued client
ID, granted permissions and tokens are inside the encrypted credential record.
See the [official OpenAI sign-in flow](https://developers.openai.com/siwc/token-sharing-open-source/sign-in).

## 3. Put the saved file in study.yaml

Merge the following into your existing `assistance` mapping, replacing both the
path and model. The helper prints the actual file-path value for your machine.

~~~yaml
assistance:
  enabled: true
  provider: openai
  model: APPROVED_MODEL_ID
  auth: oauth
  credential_file: C:/Users/YOUR_ACCOUNT/.omicsmatic/credentials/openai-oauth.credentials.json
  next_steps: true
  literature: false
  impact: false
  manuscript: false
~~~

Use an absolute Linux or macOS path when running there; YAML does not expand
`$HOME`, `$vault` or `~`. Remove `api_key_env` and `oauth_token_env` when selecting
`credential_file`. Keep the
[aggregate disclosure policy](ai-connections.md#first-set-the-disclosure-policy),
then validate, resolve, inspect and approve the study using the
[advisor walkthrough](ai-advisor.md).

During a permitted advisor operation, Java asks for the saved file's passphrase.
It uses the access token and renews supported credentials when needed.
A separate login is unnecessary on each run. Model availability, usage limits
and the provider's permission requirements still apply.

## 4. Gemini: register a desktop client, then sign in

In [Google Cloud Console](https://console.cloud.google.com/), select the intended
project, enable the **Generative Language API**, configure Google Auth platform's
consent screen/audience and add your test user when required. Under **Clients**,
create a **Desktop app** client and download its JSON outside your study.
See the [official Gemini OAuth setup](https://ai.google.dev/gemini-api/docs/oauth).

That JSON identifies your app registration; it is not an access token.
OmicsMatic uses the installed-client registration and fixed Google endpoints.
Replace the JSON filename below with your actual downloaded file.

**Windows (PowerShell):**

~~~powershell
$client = Join-Path $env:USERPROFILE "private/google-desktop-client.json"
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/gemini-oauth.credentials.json"
java -jar $jar study login --provider gemini --client-json $client --out $vault
~~~

**Linux / macOS (Bash):**

~~~bash
client="$HOME/private/google-desktop-client.json"
vault="$HOME/.omicsmatic/credentials/gemini-oauth.credentials.json"
java -jar "$jar" study login --provider gemini --client-json "$client" --out "$vault"
~~~

Choose the configured Google account in the browser and review consent.
The helper requests offline access and saves the refresh token and any registered
client secret encrypted. Use `provider: gemini`, `auth: oauth`, the matching
credential path and a permitted Gemini model in YAML. The helper does not create
or replace Google's separate Application Default Credentials cache.

This is a standard bearer-token desktop flow, not DPoP or Vertex AI.
The existing adapter does not add a configurable `x-goog-user-project` quota
header; configurations that require that header need another supported route.

## 5. Claude and Grok: guided key setup

These commands open the appropriate provider console, then ask for a passphrase
and API key through hidden prompts. Create a workspace-scoped key for Claude.
Choose the command for your provider; use distinct files.

**Windows (PowerShell):**

~~~powershell
java -jar $jar study login --provider anthropic --auth api_key --out (Join-Path $env:USERPROFILE ".omicsmatic/credentials/claude.credentials.json")
java -jar $jar study login --provider xai --auth api_key --out (Join-Path $env:USERPROFILE ".omicsmatic/credentials/grok.credentials.json")
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study login --provider anthropic --auth api_key --out "$HOME/.omicsmatic/credentials/claude.credentials.json"
java -jar "$jar" study login --provider xai --auth api_key --out "$HOME/.omicsmatic/credentials/grok.credentials.json"
~~~

Use `provider: anthropic` or `provider: xai` and `auth: api_key` in YAML.
Saving a key checks its format/storage locally; it does not prove API access.

For Claude workload federation, first have your administrator configure the
issuer, federation rule, service account and protected rotating identity file.
Then save that configuration:

**Windows (PowerShell):**

~~~powershell
java -jar $jar study login --provider anthropic --auth wif --identity-token-file C:/private/identity.jwt --federation-rule-id YOUR_RULE_ID --organization-id YOUR_ORGANIZATION_ID --service-account-id YOUR_SERVICE_ACCOUNT_ID --workspace-id YOUR_WORKSPACE_ID --out (Join-Path $env:USERPROFILE ".omicsmatic/credentials/claude-wif.credentials.json")
~~~

**Linux / macOS (Bash):**

~~~bash
java -jar "$jar" study login --provider anthropic --auth wif --identity-token-file /ABSOLUTE/PRIVATE/PATH/identity.jwt --federation-rule-id YOUR_RULE_ID --organization-id YOUR_ORGANIZATION_ID --service-account-id YOUR_SERVICE_ACCOUNT_ID --workspace-id YOUR_WORKSPACE_ID --out "$HOME/.omicsmatic/credentials/claude-wif.credentials.json"
~~~

Use `auth: oauth` in **study.yaml** for this saved WIF record. The CLI's `wif`
choice configures federation; the first permitted API operation exchanges the
current identity JWT. The upstream identity provider still performs identity
login and JWT rotation. See the [federation setup details](ai-credentials.md#6-enable-claude-workload-renewal).

## 6. A cloud VM without a browser

Run the login helper on the **VM**, and use SSH to connect its loopback callback
to your laptop's browser. This avoids copying a rotating session between machines.

From a terminal on your laptop, open an SSH connection with local forwarding:

~~~text
ssh -L 1455:127.0.0.1:1455 researcher@vm.example.org
~~~

Replace the username/host. The command works with an installed OpenSSH client
from PowerShell or Bash. Keep that SSH connection open. Inside its **VM shell**,
set the actual JAR path and choose a new VM-local credential file:

~~~bash
jar="$HOME/OmicsMatic/build/cli/omicsmatic-0.3.7.jar"
vault="$HOME/.omicsmatic/credentials/openai-oauth.credentials.json"
java -jar "$jar" study login --provider openai --no-browser --port 1455 --out "$vault"
~~~

Enter the file passphrase in the SSH terminal. Open the printed authorization URL
in your **laptop browser**. After consent, the browser's `127.0.0.1:1455` callback
travels through the SSH tunnel to the waiting JAR. The JAR saves credentials and
uses the VM's persistent host ID. Gemini works the same way when you add its
VM-local `--client-json` file.

The helper listens only on `127.0.0.1`. Keep that binding; do not expose a public
callback port. If port 1455 is occupied, choose another unused port in **both**
the SSH forward and `--port`. SSH forwarding must be permitted by your server.

`--no-browser` means “print the URL instead of launching a browser.”
It does not implement a device-code flow or an out-of-band code-paste flow.
A tunnel or a browser on the same machine must still deliver the callback.

## 7. Returning accounts, expired consent and background jobs

Normal access-token expiry is handled by renewal. If consent or refresh access
has expired/revoked, reauthorize the saved account to a **new file**:

**Windows (PowerShell):**

~~~powershell
$old = Join-Path $env:USERPROFILE ".omicsmatic/credentials/openai-oauth.credentials.json"
$new = Join-Path $env:USERPROFILE ".omicsmatic/credentials/openai-oauth-renewed.credentials.json"
java -jar $jar study login --provider openai --from $old --out $new
~~~

**Linux / macOS (Bash):**

~~~bash
old="$HOME/.omicsmatic/credentials/openai-oauth.credentials.json"
new="$HOME/.omicsmatic/credentials/openai-oauth-renewed.credentials.json"
java -jar "$jar" study login --provider openai --from "$old" --out "$new"
~~~

The helper reuses the saved client registration and requires the newly verified
account to match. Update the study credential path through its amendment process.
For another account, omit `--from` and choose a different output filename.
Gemini records created by this helper can also be selected with `--from`.

If a returned authorization code expires or is rejected as invalid, the helper
tries one fresh authorization with the same issued client registration and fresh
state, nonce and PKCE. It does not reuse the rejected code.

The default wait is five minutes; `--timeout-seconds` accepts 1–900 seconds.
Wrong state, denied consent, bad signatures, account mismatch or timeout save no
new credential file. An existing output file is always refused before login.
Keep previous records until you deliberately retire them.

For a scheduler or container, log in interactively first. Supply the encrypted
file and inject its passphrase through the variable named by
`credential_password_env`. The login command itself accepts `--password-env NAME`
for a secret-manager supplied passphrase. Guided key setup also accepts
`--token-env NAME`. These options name variables; never put secrets in CLI arguments.
See [unattended execution](ai-connections.md#batch-job-container-or-scheduler-supply-the-unlock-secret).

Return to [AI connections](ai-connections.md) or [encrypted credential storage](ai-credentials.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
