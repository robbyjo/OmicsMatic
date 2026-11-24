# Enrichr API component

**Development component: current-service API calls and response parsing are
implemented and tested. A general file-based RUN/VERIFY/REPLAY command and
cumulative Enrichr catalog admission remain unfinished.** No new integration
credit or released-binary availability is claimed by this component.

`org.jlinalg.enrichr.EnrichrClient` is a Java 17-compatible client using the
JDK HTTP implementation. It needs permitted HTTPS access to `maayanlab.cloud`;
it needs no R, Python, Julia or external executable for these API calls.
Enrichment statistics are computed by the upstream service. The Java client
does not independently reproduce the server's statistical algorithms.

The component supports the current main human/mouse Enrichr catalog, library
GMT downloads, explicit standard-list submission, standard enrichment, viewing
server-prepared gene identities, separate Speedrichr list/background submission
and custom-background enrichment. Fly/yeast/worm/fish deployments and fuzzy
weighted lists are outside this component. Current source-role coverage and
remaining author-workflow gaps are in the
[checkpoint](../literature/ENRICHR_COMPONENT_CHECKPOINT_2026-11-19.md).

## Explicit use

```java
var client = new org.jlinalg.enrichr.EnrichrClient();
var session = client.openSession();       // no upload yet
var names = session.libraries();          // download current library names
var library = session.library("GO_Biological_Process_2023");
// The next call explicitly sends these symbols and description to the service.
var list = session.submit(org.jlinalg.enrichr.EnrichrClient.Channel.STANDARD,
    java.util.List.of("TP53", "BRCA1", "RAD51"), "explicit example");
var results = session.enrich(list, "GO_Biological_Process_2023");
var serverPreparedGenes = session.view(list);
// Persist session.exchanges() and results.warnings(), including on failure.
```

Only use gene lists you intend to send to this external service. Library names
must be explicitly selected; no spelling, year or species alias is substituted.
The [synthetic example](../../examples/enrichr-api/README.md) shows complete
wire capture for eight predefined symbols and requires an upload flag. It is
an API probe, not a general analysis workflow.

For an explicit background, submit the query with `Channel.CUSTOM_BACKGROUND`,
call `session.background(backgroundGenes)`, then call
`session.enrich(customSubmission, background, libraryName)`. Standard and custom
submission IDs are different namespaces; cross-channel use is refused before
transmission. The client does not manufacture a tested-gene population or
intersect the query/background on the caller's behalf.

Each session is sequential and must not be shared between threads. Default
bounds are 45 seconds per complete HTTP request, 8 MiB per request/response,
200,000 submitted gene entries and one second between calls. `Settings` exposes
explicit bounded alternatives (up to 64 MiB and one million entries). A session
can make multiple calls; these limits are not a whole-session byte/time quota.
JSON parsing allows at most 64 nesting levels and one million value nodes.
Numeric token length is 128 and decimal exponent/scale capacity is 10,000.
There are no automatic retries or redirects.

## Data and statistical fidelity

Every `Exchange` retains request method/URI/content type/body and response
status/headers/body, completion state and errors. Array accessors return copies.
Timeout, capacity and interrupted default HTTP transfers retain their captured
prefix. Callers must save these exchanges before discarding the session or
handling a failure. The example writes each operation before continuing.

`EnrichrResponse` validates the exact requested library and nine result fields,
unique positive ranks/term identities, literal hit lists and finite probabilities
in [0,1]. It preserves server row order and numeric tokens, including values
such as `1e-400`; use `Numeric.decimal()` for exact finite values. It does not
narrow them to `double`, rerank rows, recompute BH or replace combined scores.
Duplicate hit entries remain visible. Source P=0 is retained with a warning,
without a numerical floor. Bare positive `Infinity` is retained and warned for
odds/combined scores; NaN, negative/infinite probabilities and invalid scores
are refused. The infinity/underflow cases have synthetic parser controls; the
live eight-symbol reference does not establish all possible service edge cases.

The current official API labels field four **Odds ratio**. Older inspected
Python/Julia clients call this slot `zscore`. No score conversion is invented.
GMT terms, descriptions and gene fields retain their spelling, case, duplicates
and terminal empty fields. Empty/duplicate fields generate warnings: declare a
preparation policy before passing memberships to a statistical method. The
client neither fits local enrichment nor silently repairs library identifiers.

The pinned official SmartAPI specification describes GET for custom-background
enrichment. The live GET control returned HTTP 200 with an endpoint-not-supported
error. POST succeeded, as used by the inspected enrichR/backr implementations.
The client explicitly uses that verified POST route. Both raw outcomes are
retained; the documented GET discrepancy is not relabelled as success.

## Warnings and required actions

**SKIP3:** missing/unavailable service prerequisites (network/timeout, HTTP
401/403/404/408/429/5xx) mean skip the dependent analysis. Restore permitted
HTTPS access, service availability or the exact named resource, or retain the
skip. Do not substitute a different library or silently fall back to local ORA.

**FAIL2:** invalid/nonfinite probabilities, malformed/error responses, capacity,
schema, interruption or other invalid execution means no complete result.
Retain original inputs, captured prefixes and errors; correct the cause and
retry a NEW output directory. `Failure.suggestedExitCode()` distinguishes these
cases. The synthetic example writes prominent `WARNINGS.md`/`FAILURE.txt` and
returns the applicable code. Input/URI constructors can also reject invalid
arguments before any request; callers must report those as invalid input.

A current library name is not an archived library snapshot. It does not recover
the settings/results used by the papers. The Enrichr resource-only paper,
length-matched background sampling, Julia local recalculation and unresolved
ClusterProfiler attribution remain separate source-role requirements.

Primary references are the
[pinned Enrichr specification](https://github.com/MaayanLab/smartAPIs/blob/ef62d43b4826fb64dfc17a3b420883e14553e552/enrichr_smartapi.yml),
[pinned Speedrichr specification](https://github.com/MaayanLab/smartAPIs/blob/ef62d43b4826fb64dfc17a3b420883e14553e552/speedrichr_smartapi.yml),
[Enrichr API help](https://maayanlab.cloud/Enrichr/help#api) and
[pinned enrichR client](https://github.com/wjawaid/enrichR/blob/79fad70a8c1c1ae2150025d616de434aa2bef05b/R/functions.R).
Keep source/data terms distinct: the specification repository has an Apache
license, while its API metadata declares CC-BY-NC-SA-4.0; individual gene-set
resources have their own terms. This component is original project Java code
under GPL-3.0-or-later. The public fixture contains synthetic query/results and
provenance, not a redistribution of the complete downloaded gene-set library.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Maayan Lab (2026) — Enrichr API help](../CITATIONS.md#enrichr-api-documentation)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
