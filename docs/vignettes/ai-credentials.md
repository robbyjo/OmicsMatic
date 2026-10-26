# Save AI credentials securely and renew OAuth access

OmicsMatic can keep API keys and OAuth credentials in a password-encrypted JSON
text file. Enter the passphrase when a study starts; the application unlocks the
file locally and uses the credential in memory. You do not need to paste your
API token for every run.

This is a **current source-build feature**. Rebuild the executable before using
these commands. It adds credential storage and supported renewal, not a bundled
provider-registration or browser sign-in screen.

## Supported providers and renewal

| Provider | study.yaml provider | What can be saved | Automatic renewal |
| --- | --- | --- | --- |
| OpenAI | openai | Platform API key, or OAuth credentials legitimately issued to this application's Sign in with ChatGPT registration | OAuth refresh with the issued client ID and refresh token; rotating replacements are saved encrypted. API keys require replacement. |
| Claude | anthropic | A workspace-scoped API key, or a federated API access token and its workload identity configuration | Workload Identity Federation exchanges a freshly supplied IdP JWT for a new access token. The IdP or platform must rotate the identity file. |
| Grok | xai | An xAI API key | No OAuth refresh grant is configured. Replace an expired/revoked key with a newly issued key. |
| Gemini | gemini | API key, or Google OAuth access/refresh credentials | Standard Google OAuth refresh, including an optional registered client secret. |
| Compatible gateway | openai_compatible | API key or an authorized OAuth bearer token bound to that endpoint | Stored token and expiry checks; gateway-specific refresh is not configured. |

The provider routes are based on the official
[OpenAI session/refresh documentation](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[Claude federation reference](https://platform.claude.com/docs/en/manage-claude/wif-reference),
[xAI API key guidance](https://docs.x.ai/developers/faq/security), and
[Google OAuth refresh documentation](https://developers.google.com/identity/protocols/oauth2/native-app#offline).
Account authorization, scopes, provider availability and billing still apply.

Do not import another application's saved login credentials. An OpenAI OAuth
token must come from the supported flow for your application's own issued
registration. The CLI below saves credentials you already obtained; it does not
claim that a token copied from Codex or a chat website authorizes this application.

## 1. Choose a private credential location

Keep credential files **outside the study project** and outside source control.
For example, from PowerShell:

~~~powershell
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/openai.credentials.json"
~~~

Each file holds one provider/account/endpoint credential. Use distinct filenames
for different accounts. The password is never written in that file or in the
study contract.

The file is ordinary JSON containing the format version, encryption settings,
random salt/nonce and text-encoded ciphertext. It does not contain a plaintext
token, provider record or password. Creation restricts file access to its owner.

## 2. Save an API key

From an ordinary interactive terminal, with $jar set to the executable's absolute
path:

~~~powershell
java -jar $jar study credentials-save --provider openai --out $vault
~~~

The command asks for a passphrase, confirmation and the API key with hidden
input. Use a strong passphrase of at least 12 characters. It creates a new file
and refuses to overwrite an existing credential.

The same command works with --provider gemini, --provider anthropic for a
workspace-scoped Claude API key, or --provider xai for a Grok API key. Use a
separate file path for each. For a compatible gateway, also supply its approved
HTTPS --endpoint ending in /chat/completions.

Check the encrypted record locally:

~~~powershell
java -jar $jar study credentials-check --file $vault
~~~

This asks for the passphrase and reports provider, authentication type, renewal
mode and declared expiry when present. It prints no token and makes no network
request. It checks local decryption/integrity, not whether the provider accepts
the credential.

## 3. Reference the file in study.yaml

Merge this into the existing contract after replacing the file path and model:

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

Use the **actual absolute path**, with forward slashes for convenient YAML
quoting. YAML does not expand $vault, $HOME or environment-variable placeholders.
The saved provider, authentication method and endpoint must match the study.

Omit api_key_env and oauth_token_env when credential_file is set; there is no
silent fallback to another credential. Omit credential_password_env for a masked
console prompt. During one study CLI invocation, the passphrase is requested
once per file, then cleared from the application's password cache on exit.

Configure the [aggregate disclosure policy](ai-connections.md#first-set-the-disclosure-policy),
validate, resolve, inspect and approve the plan as in the
[advisor walkthrough](ai-advisor.md). Run from the same terminal:

~~~powershell
java -jar $jar study run --project . --actor-id scientist-1
~~~

The password prompt appears only if an enabled, policy-permitted operation
needs the credential. Offline and denied-transmission paths do not unlock or
renew it. Ordinary deterministic work does not need a credential file.

## 4. Enable OpenAI OAuth renewal

Obtain access through the supported
[Sign in with ChatGPT registration and sign-in flow](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
for this application. Preserve the returned issued client ID and token-expiry
information after the flow has validated identity, consent and granted scopes.

Use that issued client ID, not the initial dynamic registration marker.
Replace ISSUED_CLIENT_ID and UTC_EXPIRY with the actual values:

~~~powershell
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/openai-oauth.credentials.json"
java -jar $jar study credentials-save --provider openai --auth oauth --renewal openai --client-id ISSUED_CLIENT_ID --expires-at UTC_EXPIRY --out $vault
~~~

The command prompts separately for the access token and refresh token, so neither
belongs in command history. UTC_EXPIRY is the actual ISO-8601 expiry, such as
2026-10-26T20:00:00Z, calculated from the token response's receipt time and
expires_in. Do not substitute a later expiry to extend a token's apparent lifetime.
If supplied by the provider, preserve its earliest_refresh_at using
--earliest-refresh-at with the equivalent UTC timestamp.

In the study mapping, use:

~~~yaml
assistance:
  enabled: true
  provider: openai
  model: APPROVED_MODEL_ID
  auth: oauth
  credential_file: C:/Users/YOUR_ACCOUNT/.omicsmatic/credentials/openai-oauth.credentials.json
  next_steps: true
~~~

Near expiry, OmicsMatic sends the supported refresh grant to OpenAI's documented
token endpoint, using the saved client ID and refresh token. It saves the
replacement credential before sending the analysis request. Concurrent users of
the same file cannot spend the same rotating refresh token simultaneously.

OAuth inference uses the public Responses endpoint with store: false and
stream: true. An incomplete stream is not accepted as completed advice.
Use a model available to the authorized account. See the
[official inference requirements](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference).

## 5. Enable Gemini OAuth renewal

Follow the official [Gemini OAuth setup](https://ai.google.dev/gemini-api/docs/oauth):
enable the API, register the appropriate Google OAuth client, grant the required
scopes, and obtain the access/refresh token through an approved flow.

Save the issued credentials:

~~~powershell
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/gemini-oauth.credentials.json"
java -jar $jar study credentials-save --provider gemini --auth oauth --renewal google --client-id GOOGLE_CLIENT_ID --expires-at UTC_EXPIRY --out $vault
~~~

Use the real client ID and expiry. Enter access and refresh tokens at the masked
prompts. If the registered client requires a client secret, supply its
environment-variable **name** using --client-secret-env, with the secret provided
by your secret manager or a masked prompt. It is encrypted with the token record.

Set provider: gemini, auth: oauth, the approved model, and this absolute
credential_file path in the study. The adapter sends the access token as a bearer
credential. Refresh retains the existing refresh token when Google does not
return a replacement, and stores a replacement when one is returned.

This implementation supports Google's standard bearer-token flow. DPoP-bound
tokens that require proof signing are not supported; use an appropriate
provider-supported bearer flow. It does not silently downgrade a proof-bound
credential or acquire new consent.

## 6. Enable Claude workload renewal

Claude's federated API route requires an organization-configured issuer,
federation rule and service account. It renews access by exchanging a fresh IdP
identity token; this is different from refreshing a Claude chat-subscription token.

Follow the official
[Workload Identity Federation setup](https://platform.claude.com/docs/en/manage-claude/workload-identity-federation).
Your workload platform or IdP must provide and rotate a protected JWT file
outside the study. OmicsMatic re-reads that file on each exchange.

~~~powershell
$vault = Join-Path $env:USERPROFILE ".omicsmatic/credentials/claude-wif.credentials.json"
java -jar $jar study credentials-save --provider anthropic --auth oauth --renewal anthropic_wif --identity-token-file C:/private/identity.jwt --federation-rule-id YOUR_RULE_ID --organization-id YOUR_ORGANIZATION_ID --service-account-id YOUR_SERVICE_ACCOUNT_ID --workspace-id YOUR_WORKSPACE_ID --out $vault
~~~

Replace every placeholder with the configured account identifiers and actual
absolute identity-file path. The command asks for the encryption passphrase; no
initial Anthropic access token is needed for this route. Set provider: anthropic,
auth: oauth and this credential_file in study.yaml.

When needed, the configured identity is exchanged at the documented Anthropic
token endpoint. The encrypted file caches the issued API access token until it
needs renewal. If the IdP JWT is expired or has already been spent, provide a
fresh one through the workload identity system. OmicsMatic cannot mint that
upstream identity or create the organization's federation rules.

For API-key access instead, save a workspace-scoped key with provider anthropic
and auth api_key. The adapter uses the Messages API with an explicit API version.
The current Claude/Grok adapters do not attach provider-native search tools;
use native literature retrieval or an existing supported search route for
literature evidence.

## 7. Grok and key replacement

Save an xAI API key with provider xai and reference the file using auth api_key.
The adapter uses xAI's documented OpenAI-compatible Chat Completions endpoint.
A Grok browser subscription is not a refresh token for that API.

An optional --expires-at records a key's known expiry; an expired key is blocked
locally. To replace a key, obtain its replacement through the provider, save a
**new filename**, and update the study through an approved amendment.
There is no invented OAuth endpoint or automated API-key replacement.

## Unattended runs and editorial review

If no secure console exists, provide the passphrase through an approved secret
manager and reference only its environment-variable name:

~~~yaml
assistance:
  credential_file: /home/researcher/.omicsmatic/credentials/openai.credentials.json
  credential_password_env: OMICSMATIC_CREDENTIAL_PASSWORD
~~~

Merge this fragment into the existing provider mapping. Do not save the
passphrase itself in YAML or shell scripts. credentials-save and
credentials-check accept --password-env NAME for the same use case.
--token-env and --refresh-token-env read input secrets from named variables
when securely importing credentials in a noninteractive process.

For manuscript editorial review, replace the environment-token flags with:

~~~text
--credential-file ABSOLUTE_FILE --credential-password-env VARIABLE_NAME
~~~

Omit the password-variable flag to use a masked terminal prompt. Keep the exact
payload-hash approval and source disclosure policy; credentials do not waive them.

## What happens when something goes wrong?

| Condition | Behavior |
| --- | --- |
| Wrong password, tampered file or invalid format | Unlock fails; no inference request or environment-token fallback. |
| Provider/auth/endpoint mismatch | Credential is rejected before use. |
| Another process holds the credential lock | The operation waits with credential_busy; retry when the other process finishes. |
| Expired credential with no supported renewal | Request a new credential or reauthorization. |
| Renewal denied, unavailable or malformed | Preserve the encrypted record and report the failure; do not submit inference with an expired token. |
| Offline or remote transmission denied | Neither unlock nor renewal runs for that blocked operation. |

Inspection and inference logs record only the credential source and whether
renewal occurred. Token exchanges are not copied into analysis artifacts.
The file must remain outside the study so rotating credentials are not mixed with
immutable scientific evidence.

Storage uses AES-256-GCM authenticated encryption, a fresh random salt and nonce,
and PBKDF2-HMAC-SHA256 with 600,000 iterations. See
[OWASP cryptographic storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
and [password-derived key guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
This protects the saved record at rest. The running JVM must hold usable
credentials in memory; a compromised running account is a separate boundary.
Keep the passphrase separate and use institution-controlled permissions and backups.

Return to [AI connections](ai-connections.md), [advisor mode](ai-advisor.md),
or [driver mode](ai-driver.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Geir Kjetil Sandve et al. (2013) — Ten Simple Rules for Reproducible Computational Research](../CITATIONS.md#sandve-reproducibility-2013)
- [W3C Provenance Working Group (2013) — PROV-O: The PROV Ontology](../CITATIONS.md#w3c-prov-o-2013)
- [National Institute of Standards and Technology (2024) — Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile](../CITATIONS.md#nist-genai-profile-2024)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
