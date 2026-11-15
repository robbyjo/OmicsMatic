# MELODI Presto: source identity and complete service responses

**P169's Presto is the MELODI Presto literature-triple service. It is not the presto single-cell Wilcoxon/AUC R package.** The earlier catalog assignment was incorrect. The original observation and catalog bytes are retained in predecessor evidence; the correction uses the explicit `melodi-presto` identity. An unrelated R source archive was downloaded during this assessment but was not installed or executed. Prior R dependency evidence from other workflows remains unchanged.

**Warnings and required actions:** the service does not identify its deployed source commit or database snapshot. P169 accessed it on **3 April 2024**. Current queries require an explicit `"server_snapshot":"live-unversioned"`; a historical snapshot request fails instead of silently using current data. Current literature responses cannot establish the paper's historical results, biological novelty, absence of evidence or causal links. Only send query terms that you intend to transmit to the recorded service.

**SKIP3 skips dependent analysis** for named unavailable or forbidden runtime, networking, service/access or output prerequisites. Supply that permitted prerequisite, inspect the exact HTTP diagnostics and retry NEW paths. **FAIL2 admits no complete result**, including bad requests, upstream `Error` records, malformed/non-JSON responses, count mismatches, lower-bound/capped populations, truncation and recording failure after a server return. Read `WARNINGS.md`, `FAILURE.json`, `HTTP.json` and complete `response.bin`. Resolve the stated cause, preserve all originals and partials, and retry NEW paths. Only the final `COMPLETED.json` admits a recorded response. A server returning HTTP200 is insufficient by itself.

## Source roles and corrections

[P169](https://doi.org/10.1038/s41588-024-01900-w), primary paragraphs15 and49, describes a literature survey using candidate genes as subjects and colorectal cancer as the object in Semantic MEDLINE subject-predicate-object triples. The exact author gene-list query, server source, database snapshot and original responses remain unknown. The cohort's other standalone Presto matches are a PRESTO-Tango plasmid kit (P038,68) and JST PRESTO funding (P157,67), not software uses.

The same source review identifies omitted families and preserves their distinct settings:

| Tool | Source-specific use | Preserved uncertainty |
| --- | --- | --- |
| OncoScore | P169: literature-based cancer associations, version1.30.0; stated novelty threshold21.09 | The comparison direction at the threshold and full query settings are not stated. |
| oncoEnrichR | P169: cancer/drug associations, synthetic lethality, gene fitness and protein interactions, version1.4.2.1 | Exact functions and unreported settings remain unknown. It is separate from Enrichr pathway enrichment. |
| IntOGen | P042: v.2023 driver pipeline combining seven named methods; separate annotation against prior cohorts | Component versions and reused cohort snapshot remain unknown. |
| IntOGen | P169: driver resource released1February2020, restricted to COADREAD cohorts | This does not establish execution of the mutation-analysis pipeline in P169. |
| HaploReg | P073: functional annotations at rs6751481/rs74258942; release unreported | Other coloc/SuSiE analyses in the paragraph are separate. |
| HaploReg | P169: gene-distance information, version4.1 | Exact queried variants and settings remain unknown. |
| HaploReg | P192: version4.2 for the reported LD R-squared=0 at rs10494217/rs4659153 | The METSIM R-squared=0.03 comparison is separate; the HaploReg population is unspecified. |

These four companion families receive **no implementation credit**. They require their own permitted sources, inputs and validation. Explicitly named IntOGen component methods are retained in the review rather than inferred to have separate reported editions. The complete paper-wide software audit remains open.

The fixed cohort remains **250 papers** (71 Nature,104 Nature Genetics,75 AJHG;2023-10-23 through2026-10-23). The corrected local catalog has **445 named families,2,689 observations,1,195 reviewed uses and1,494 unreviewed uses**. It has **121 scoped new integrations:20 native Java subsets and101 upstream workflows**, or124 including three existing adapters. **265 families remain missing**. The missing count rises because four previously omitted families are now represented. The R Presto registry definition is retained but excluded from the MELODI context; it has no explicit software use in this corpus census. No R analytical implementation credit is assigned to the Java HTTP adapter.

## Upstream contract and qualification boundary

The [official repository](https://github.com/MRCIEU/MELODI-Presto) describes the [public service](https://melodi-presto.mrcieu.ac.uk/) and `/api/overlap/`, `/api/enrich/` and `/api/sentence/` routes. The assessed original source is project-selected pre-access-date commit `b18d04350ba940804933a7faa4dedb2da78ae4cb`; its archive SHA-256 is `92e5922c5e66ace05479459a582c80dc6375e32e0cf1b90811e732b5d9f8dcfc`. This selection is not the authors' known commit and does not authenticate the live deployment. The repository root license is GPL-3.0; the API schema's BSD label is retained as supplied rather than treated as a replacement source license. The software license does not relicense underlying SemMedDB data, PubMed text or live responses.

The original service code performs PubMed/SemMedDB retrieval, enrichment and overlap. Its source includes a significance filter and a join through overlapping triple terms; the Java adapter does not replace those algorithms or infer that the current deployment is identical. It transmits the literal payload and preserves the entire original response. The original server may normalize query terms; the client preserves both its source request and exact emitted JSON. Every returned field, unknown field, numeric lexeme, null, identifier and record ordering remains in `response.bin` without rewriting.

Sentence counts may be Elasticsearch metadata objects. An exact `{ "value":N, "relation":"eq" }` is preserved, with `N` required to match the full returned list. A `gte`, unknown relation or count mismatch fails. The adapter does not invent pagination or accept a server cap as complete evidence. Error-bearing records remain errors, including a zero-count `Error` response. A genuine complete zero-count response is recorded without inferring biological absence.

## Build and run

Use a permitted existing **Java17+** runtime. If Java or a required build/download prerequisite cannot be installed under the operating environment's rules, skip the dependent query and supply a permitted built JAR/runtime before retrying. Analysis itself installs no software. Build the ordinary project JAR:

```sh
./gradlew jar
```

On Windows, use `gradlew.bat jar`. This is a standalone public Java entry point included in `build/libs/OmicsMatic-0.3.7.jar`; it has no root command-dispatch alias. Save a trusted explicit request, for example:

```json
{
  "schema":1,
  "operation":"overlap",
  "payload":{"x":["TP53"],"y":["colorectal cancer"]},
  "server_snapshot":"live-unversioned",
  "timeout_seconds":300,
  "max_response_bytes":67108864
}
```

TP53 is a public demonstration query, not the authors' recovered gene list. For `enrich`, use `"payload":{"query":"TP53"}`. For `sentence`, use a PubMed identifier string such as `"payload":{"pmid":"23715093"}`. Unknown fields, duplicate JSON keys and unsupported snapshot names fail. The default endpoint is `https://melodi-presto.mrcieu.ac.uk`; an explicit `endpoint` may name another permitted HTTPS service origin. Plain HTTP is accepted only for explicit loopback test servers. Credentials, query strings and fragments are rejected in the endpoint.

```sh
java -Xmx512m -cp build/libs/OmicsMatic-0.3.7.jar \
  org.jlinalg.cli.MelodiPrestoCli run \
  --request /trusted/request.json --out /results/NEW-response --allow-network
```

Without `--allow-network`, the query is skipped without transmission. No redirects are followed. Time and byte capacities are explicit; stalled bodies, incomplete bodies and capacity overruns retain available partial bytes and admit no result. HTTP401/403/429 and service5xx responses skip the dependent operation with complete diagnostics; other non200 responses fail visibly.

Keep the complete output directory. `source-request.json` is the original request; `request-payload.json` is exactly what was sent; `HTTP.json` records the route, status, timestamps and headers. `response.bin` retains the original payload; `response-validation.json`, `runtime.json` and `MANIFEST.json` record structure, current client bytecode and file integrity. Only final `COMPLETED.json` admits the recorded operation. An existing output is refused without overwriting it. Recording failure after a full return is still FAIL2, with all available originals retained.

## Verify and interpret the evidence

```sh
java -Xmx512m -cp build/libs/OmicsMatic-0.3.7.jar \
  org.jlinalg.cli.MelodiPrestoCli verify \
  --bundle /results/SAVED-response --out /validation/NEW-readback
```

Verification checks the complete saved file family, all original bytes, emitted payload, current implementation bytecode and full response population. It sends no network request and reruns no scientific service analysis. Hashes establish integrity, not server authorship; a self-resealed fabricated response is not independently authenticated. The source-qualified tests use real local HTTP servers to exercise all three routes, high numeric precision, unknown fields, exact/zero counts, original errors, missing network permission, unsupported historical snapshots, actual body deadlines, truncation, nonJSON replies, duplicate keys, complete-output preservation and a recording failure after a whole response. Live packaged tests and Linux readback have separate receipts; failures remain retained rather than counted as successes.

To repeat the public demonstration queries through your built JAR (this explicitly transmits the three example payloads):

```sh
python3 -B tools/literature/melodi_service/qualify.py \
  --java /permitted/java --jar build/libs/OmicsMatic-0.3.7.jar \
  --out /validation/NEW-live-demonstrations --allow-network
```

To check saved software receipts without contacting the service:

```sh
python3 -B tools/literature/melodi_service/records.py --out build/NEW-service-records
```

The optional `--private-records` checks every retained original file at the indexed local Linux/WSL paths when those permitted records are available. It does not download or reconstruct missing data. Public receipts alone are not a fresh query or private-content readback.

Complete real literature payloads remain private, with file identities and operation receipts retained publicly. Synthetic fixtures and source records are separate from biological/literature data licensing. The entire semantic review, source-specific implementation goal, whole-tool qualification and paper reproduction remain open. Cell Ranger/ARC remain excluded by user request; the literal SingleR2.41 remains unresolved.
