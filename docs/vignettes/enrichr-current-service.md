# Qualified current-service Enrichr workflow

The normal strict Gradle executable JAR passed 42 API/workflow tests on each
of Windows Java 21/25 and Linux Java 21, live standard/custom/library-only
commands, independent nine-field comparisons of 600 returned rows, cross-host
offline VERIFY/REPLAY and all six documented synthetic example commands.
The [saved evidence reader](../literature/enrichr-current-checks/README.md)
checks the exact source/artifact identities and reconstructs those result
comparisons without network access. Statistical computation uses the upstream
service; this is an upstream adapter with Java capture and verification.

Use the [runnable example](../../examples/enrichr-workflow/README.md) and
[request/input/output reference](enrichr-workflow.md). This qualification
supersedes the earlier guide/handover's statement that normal-JAR live and
example checks were pending. Those older checkpoint files and their hashes
remain retained. **Catalog admission is a separate guarded operation**; this
guide alone does not establish that it has been applied.

Build current source using `gradlew.bat executableJar`, then run
`java -jar build/cli/omicsmatic-0.4.0.jar enrichr-tools --help`. The published
0.4.0 release predates this command. The exact qualified normal JAR is
194,636,306 bytes, SHA-256
`605674339041983a30997bf421b39cacc989ea36762c7d54cc5523bc63dc3923`.
Keep the producer JAR with saved bundles. A different build can change class
fingerprints; never refresh an old fingerprint to bypass verification.

**Read `WARNINGS.md` and `analysis-status.json` before interpreting results.**
RUN submits gene identifiers only with explicit upload consent. Prepare
permitted literal identifiers and a scientifically appropriate background.
The supplied 16-gene example background is artificial and supports no
biological inference. All returned terms, raw numeric tokens, source zeros and
positive infinite margins are retained. No automatic mapping, library aliases,
reranking, local ORA fallback or BH recomputation occurs. Read token columns
as strings or finite values as `BigDecimal`; narrowing `1e-400` to a double
loses information. TSV fields use quoting and need a quote-aware parser.

The standard stored-query echo does not establish the final scoring population.
Full custom normalization is unobserved and requires explicit acknowledgement.
Pre/post library equality establishes observed byte stability, not an
authenticated atomic or historical snapshot. Historical clients/settings,
P055's multi-resource target intersections/FGSEA, P121's Julia recalculation
and P182's unresolved function attribution remain separate scopes. Current
remote statistics are not evidence that those original workflows were run.

**SKIP3:** required exact permitted dependency/service unavailable; skip its
dependent analysis, restore the named item and retry NEW output. **FAIL2:**
invalid data/schema, changed evidence, capacity or interruption; retain raw
inputs, partial captures and errors, correct the cause and retry NEW output.
A failed attempt has no accepted complete result. Historical marker-verifier
SKIP3 gaps and earlier failed builds/archival attempts remain retained.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Maayan Lab (2026) — Enrichr API help](../CITATIONS.md#enrichr-api-documentation)
- [Yoav Benjamini and Yosef Hochberg (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](../CITATIONS.md#benjamini-hochberg-1995)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
