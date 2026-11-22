# g:Profiler service workflows

Use a JDK 21 source-built JAR with the `gprofiler-tools` command. One scoped upstream
family and seven source reviews are now admitted; see the
[catalog checkpoint and remaining work](GPROFILER_CATALOG_CHECKPOINT_2026-11-22.md).
Java validates/records requests and checks results; enrichment, identifier mapping
and GO highlighting run in the original online service. RUN sends the declared
identifiers/background upstream only with explicit `allow_upload: true`.

First discover current service metadata without uploading genes. Save this as
`metadata.json`, with a fresh output directory relative to the JSON file:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "operation": "metadata",
  "service": {"archive": "", "required_build": null},
  "payload": {"organism": "hsapiens"},
  "allow_upload": false,
  "out": "metadata-new"
}
```

```powershell
java -jar omicsmatic-0.4.0.jar gprofiler-tools --request metadata.json
```

Inspect `metadata-new/metadata.json` and `summary.json` for the observed full
database build. This synthetic enrichment request uses the build tested in the
saved checkpoint; replace it deliberately if a different build is intended and
available. Every scientific field is explicit; these are illustrative settings,
not a paper's inferred defaults. Save as `enrichment.json`:

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "operation": "enrichment",
  "service": {"archive": "", "required_build": "e114_eg62_p19_9dc92ac8"},
  "payload": {
    "organism": "hsapiens",
    "query": ["TP53", "BRCA1", "RAD51"],
    "sources": ["GO:BP"],
    "user_threshold": 0.05,
    "all_results": false,
    "ordered": false,
    "combined": false,
    "measure_underrepresentation": false,
    "no_iea": false,
    "domain_scope": "annotated",
    "numeric_ns": "ENTREZGENE_ACC",
    "significance_threshold_method": "g_SCS",
    "background": null,
    "output": "json",
    "no_evidences": true,
    "highlight": true
  },
  "allow_upload": true,
  "policies": {"mapping_changes": "warn", "server_normalization": "warn", "empty_results": "warn"},
  "out": "enrichment-new"
}
```

```powershell
java -jar omicsmatic-0.4.0.jar gprofiler-tools --request enrichment.json
java -jar omicsmatic-0.4.0.jar gprofiler-tools --verify enrichment-new
```

VERIFY is read-only and offline. For a fresh offline replay save `replay.json`:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"enrichment-new","out":"replay-new"}
```

```powershell
java -jar omicsmatic-0.4.0.jar gprofiler-tools --request replay.json
java -jar omicsmatic-0.4.0.jar gprofiler-tools --verify replay-new
```

Replay requires a physically separate NEW destination; parent/child paths and
junction aliases are refused. Keep the original parent unchanged. VERIFY/REPLAY
read saved protocol, validate it and reproduce response-derived reports; they do
not refit enrichment or establish biological correctness/authenticity.

Read `WARNINGS.md` and `summary.json` even after exit 0. Mapping changes, duplicates,
ambiguity, server normalization and empty results are retained and warned; choose
`fail` for the relevant policy if your analysis requires refusal. The service does
not report the full resolved custom background mapping, so that remains explicitly
unobserved. A nonempty background requires `custom` or `custom_annotated` scope.
Ordered inputs preserve original order/duplicates. Combined `-1` absence sentinels,
original null evidence and literal zero p-values are preserved, never replaced by
zero, NA, invented probabilities or a floor. R client versions and database builds
are distinct provenance and must not be substituted for one another.

**SKIP3 (exit 3): the dependent analysis was skipped.** Supply the exact permitted
prerequisite/build named in the warning; if unavailable retain the skip. In
particular P052's exact historic build is unavailable, and no fallback is admitted.
**FAIL2 (exit 2): no complete result is admitted.** Keep logs and provisional wire
output; correct the stated input, protocol, numeric, capacity or runtime cause and
retry in a NEW directory. Resource limits can be declared under `limits`; use
`--help` and `GprofilerWorkflowContracts.java` for the precise checked schema.
Completion requires a valid `COMPLETED.json` and matching closed manifest.

**Original HTTP headers may contain Set-Cookie values. Keep raw captures private.**
For public sharing make a separately declared derivative with cookie values
redacted, retain originals and leave scientific payloads unchanged. The saved
qualification packet demonstrates that process; do not upload raw originals.
No account or separate local R/Python installation is required for this Java
adapter. Online service availability and exact database snapshots remain external
prerequisites. Observe the service/data acknowledgements in the evidence notice.
