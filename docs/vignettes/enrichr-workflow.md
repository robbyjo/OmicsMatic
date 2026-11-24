# Current-service Enrichr file workflow

**Development handover checkpoint.** RUN/VERIFY/REPLAY are implemented;
normal source-build tests passed, but fresh live and example qualification of
the normal Gradle artifact and cumulative catalog admission remain open. Read
the [handover](../literature/HANDOVER_2026-11-19_ENRICHR_WORKFLOW.md) before use.
The released 0.4.0 JAR predates this command. Build the current source with
`gradlew.bat executableJar`, then use its `build/cli/omicsmatic-0.4.0.jar`.
Java 17 or later is required; the recorded test hosts used Java 21/25.

From the repository root, inspect the command with:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar enrichr-tools --help
```

The command accepts one request JSON. Paths are resolved relative to that
request. RUN creates a NEW output directory and never overwrites a result.
It accesses the current human/mouse service; historical editions and settings
are not inferred. A standard request has this shape:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "out": "new-result",
  "service_profile": "enrichr-current-human-mouse-workflow-v1",
  "operation": "enrichment",
  "allow_upload": true,
  "libraries": ["MSigDB_Hallmark_2020"],
  "query": {"file": "query.txt", "format": "lines"},
  "background": {"kind": "server-default"}
}
```

Prepare `query.txt` as literal, headerless UTF-8 identifiers, one per line.
LF/CRLF and one final newline are allowed. Duplicates/order are retained;
BOM, internal empty lines, trimming, comma weights and control characters
are rejected. No automatic gene mapping or library-name aliases are applied.
Run with `enrichr-tools --request path/to/request.json`.

For an explicit custom background, supply
`"background": {"kind": "explicit", "file": "background.txt", "format": "lines"}`
and `"allow_unobserved_custom_normalization": true`. The service does not
expose its complete normalized background or stored custom query echo. The
workflow records this limitation and checks returned hits against the literal
query/background. The caller must choose a scientifically appropriate
background; a synthetic test background is not a biological recommendation.

For library retrieval only, use `"operation": "library-download"`,
`"allow_upload": false`, the exact library list and no query/background or
description fields. Library use may have separate terms; preserve them.

Optional `policies` choose `"warn"` or `"fail"` for `identity_changes`
(default fail), `empty_results` (default warn) and `infinite_scores` (default
warn). Optional `limits` bound timeout, response/request/input/output bytes,
gene count and request interval. Use the command help/source contract for
accepted numeric bounds. Lowering a limit can produce a partial capture and
FAIL2; a capacity failure never silently becomes a successful analysis.

Successful bundles contain literal input ledgers, request/configuration,
producer hashes, raw wire captures, library GMT snapshots, exact-token result
JSON, quoted TSVs, `summary.json`, `analysis-status.json` and `WARNINGS.md`.
`COMPLETED.json` is written last. All returned terms and source score/P-value
tokens are retained; no automatic thresholding, reranking, BH recomputation,
top-five filtering or local statistical fallback occurs. Source P=0 and
positive infinite scores are explicitly flagged; invalid probabilities,
NaN or negative infinite scores fail. The slot commonly called a z-score by
older clients is recorded as the current service's odds ratio.

Read TSV with a quote-aware parser. Keep all `*_token` columns as strings
(Python's `csv.DictReader`, for example), or use Java `BigDecimal` for finite
values. A double cannot represent every retained token, including `1e-400`.

Offline verification derives outputs again from manifest-bound raw inputs and
protocol captures. It rejects changed/extra files, semantic resealing, links
and changed producer classes. Replay first verifies its parent, then writes
new outputs without contacting the service:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar enrichr-tools --verify path/to/bundle
```

```json
{"schema_version": 1, "mode": "REPLAY", "analysis": "bundle", "out": "new-replay"}
```

Use the same exact producer artifact for a saved bundle; another build can
have different class fingerprints even when scientific source behavior is
unchanged. Never refresh a fingerprint to bypass this check. Replay refuses
the parent itself and lexical/physical descendants or aliases. Retain the
original artifact and source proof when transferring bundles.

**Exit 3 (SKIP3): required service/dependency unavailable.** Restore the named
service/dependency and retry in a new directory; skip the dependent analysis.
**Exit 2 (FAIL2): invalid request/data/schema, changed evidence, interruption
or capacity failure.** Inspect `FAILURE.json`, `WARNINGS.md`, console errors
and retained raw prefixes, correct the cause and retry into new outputs.
Failed attempts have no complete marker; preserve their inputs and diagnostics.

Standard stored-query echoes are not proof of the service's final scoring
population. Pre/post library-byte equality shows observed stability during
capture, not an authenticated atomic or historical database snapshot. The
current custom endpoint uses POST; the inspected SmartAPI GET returned an
unsupported-endpoint error. Historical paper parity, Julia/local recalculation,
FGSEA and library-intersection preparation are separate scopes. See the
[source/API checkpoint](../literature/ENRICHR_COMPONENT_CHECKPOINT_2026-11-19.md).

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Maayan Lab (2026) — Enrichr API help](../CITATIONS.md#enrichr-api-documentation)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
