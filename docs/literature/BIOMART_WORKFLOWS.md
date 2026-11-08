# Original biomaRt annotation workflows

This development workflow retrieves annotation with unchanged original biomaRt **2.60.1 or 2.68.0**. Java plans the original XML queries, checks every complete batch, binds an explicit service/database/dataset/assembly, supervises the original R processes and audits their HTTP requests. Original R performs table conversion, column matching and optional HTML fallback. Gene symbols, transcript mappings, gene biotypes and GO annotations are returned as requested; the workflow does not perform enrichment or choose a nearest-gene rule.

**Require exit 0 and `analysis-status.json` with `result_admitted: true`. Read `WARNINGS.md` before using results.** Exit 3 means a required dependency or the exact requested service configuration is unavailable and dependent analysis was skipped. Exit 2 means no result from the batch is admitted, including earlier queries that finished. Keep original inputs and all failed attempts, correct the named cause and retry a new output directory. Files under `worker` are provisional until the whole batch passes.

**Also inspect the `warnings`, `error` and `trace_error` fields in every available `worker-report.json`** under `metadata-before`, `worker` and `metadata-after`. These retain original phase diagnostics even when failure prevents a complete top-level summary. A capture-capacity error names `maximum_http_bytes`; increase it within the supported bound and retry new output paths without omitting data.

Native query planning needs Java 17 or newer and runs on Windows and Linux. Original retrieval currently requires the captured **Windows x86_64 R 4.6.1** runtime and its exact package files. Loading historical biomaRt 2.60.1 in this runtime does not reproduce a historical R 4.4 installation. Analysis installs nothing and never substitutes another release, build, endpoint or R package.

## Prepare dependencies

Use an exact prepared runtime manifest. In the development workspace, the validated manifests are listed in the [validation receipt](biomart-workflow-development/VALIDATION.json). The canonical executable remains separate; use a current source build or the development JAR recorded there.

Where downloading and installation are permitted, these PowerShell commands capture both original source/binary packages and install them into new private libraries:

```powershell
python tools/literature/download_biomart_reference.py `
  --out build/literature-review/biomart-runtime/my-intake

python tools/literature/setup_biomart_reference.py `
  --intake build/literature-review/biomart-runtime/my-intake `
  --out build/literature-review/biomart-runtime/my-reference `
  --allow-install

python tools/literature/prepare_biomart_runtime.py `
  --setup build/literature-review/biomart-runtime/my-reference `
  --version 2.68.0 `
  --out build/literature-review/biomart-runtime/my-runtime
```

The reference installer needs the captured R 4.6.1 installation and the exact dependency libraries named in its source. It installs only the original biomaRt package; it is **not a general dependency resolver**. A fresh checkout does not contain those private libraries. Missing, different, forbidden or unloadable dependencies produce **SKIP3** and a preparation `WARNINGS.md` identifying the problem. Supply the exact permitted runtime/dependency files or keep the dependent analysis skipped. Installing arbitrary current package versions and refreshing hashes is not an accepted substitute. The development-only `--capture-initial-profile` option refuses existing profiles and must not be used to change a frozen scientific profile.

An existing exact installation can be copied to new private paths without changing scientific bytes:

```powershell
python tools/literature/export_biomart_runtime.py `
  --runtime-manifest C:/absolute/path/to/prepared/runtime.json `
  --out build/literature-review/biomart-runtime/my-private-copy
```

Use the resulting `my-private-copy/runtime.json`. The exporter verifies every copied file against the same captured profile and preserves the original installation. R and each dependency retain their original licenses and license files; this private preparation does not grant redistribution permission for a runtime bundle. The biomaRt source/resources in this project use [Artistic-2.0](../../src/main/resources/org/jlinalg/omics/biomart-workflow/ARTISTIC-2.0.txt).

## Run the public-gene example

Run from the repository root. Replace the runtime-manifest path below with your prepared manifest. The helper creates a request and runs no analysis:

```powershell
python tools/literature/prepare_biomart_workflow_example.py `
  --runtime-manifest C:/absolute/path/to/prepared/runtime.json `
  --profile GRCh37 `
  --out build/literature-review/biomart-runtime/my-example

java -Xmx2g -cp C:/absolute/path/to/current-development.jar `
  org.jlinalg.cli.BiomartWorkflowCli `
  --request build/literature-review/biomart-runtime/my-example/request.json
```

From a fresh source checkout, `./gradlew executableJar` produces `build/cli/omicsmatic-0.3.7.jar` containing the new classes. The existing workspace's older canonical JAR is intentionally unchanged and does not acquire these classes automatically.

The example explicitly queries TP53/BRCA2 identifiers, transcript mappings, GO annotations and symbols, and includes an unmatched identifier control. It supplies no study input or paper-derived filter. Available example profiles are `GRCh37`, `GRCh38-115` and `GRCh38-116`. They request their recorded database/release and assembly labels; an unavailable or changed profile remains skipped. A registry response alone does not prove that dataset queries work. Read the generated request before running it.

## Supply your own queries

Start from the complete generated request. Keep `schema_version: 1`, choose `edition: "2.60.1"` or `"2.68.0"`, supply a matching `runtime_manifest`, and choose a new `out`. All paths resolve against the request's directory. Unknown fields and duplicate query/filter IDs are refused.

| Field | Required meaning |
|---|---|
| `endpoint.base_url`, `port` | HTTPS hostname without a path, port, credentials, query or fragment; `port` is separate. HTTP is allowed only at literal `127.0.0.1` for explicit protocol controls. |
| `mart`, `registry_version`, `database`, `virtual_schema` | Exact registry metadata, including the display-version string and database identifier. Registry release numbers and genome assemblies are separate labels. |
| `dataset`, `assembly` | Exact dataset identifier and its returned assembly/version string. Metadata is checked before and after retrieval. No assembly conversion is performed. |
| `queries` | One to 32 unique case IDs, each containing `attributes`, `filters`, `unique_rows` and `check_filters`. Attribute/filter names are checked against the selected dataset. |
| `filters` | Ordered `{ "name": "...", "type": "CHARACTER", "values": ["..."] }` records. Types are `CHARACTER`, `NUMERIC` or `LOGICAL`; values remain explicit string tokens. Use an empty filter list for an unfiltered query. An individual filter's values cannot be empty. |
| `unique_rows` | Original server-side `uniqueRows` setting. It does not authorize local row deduplication or collapsing rows from repeated chunks. |
| `check_filters` | Original filter-name checking. Setting false explicitly permits unknown names; it does not infer boolean types or fix an invalid server query. |
| `allow_original_html_fallback` | Required boolean. False rejects any original HTML fallback. True permits only the original source's exact global `TSV` to `HTML` query replacement and reports it prominently. |

Moving Ensembl main/mirror URLs (`www`, `useast`, `asia`) are refused because original `getBM` cache hashing can consult the archive registry even with permanent caching disabled. Supply the intended explicit archive or GRCh37 endpoint. HTTP redirects and alternate endpoints are not accepted. Original Mart construction and `useDataset` are used without the archive factory's implicit mirror/current-archive rewriting. Original package functions are never patched.

Numeric filters retain original `as.integer` truncation and range-to-NA behavior, with original and encoded tokens in `native-plan.json`. Decimal tokens and recorded `NA`, `NaN`, `Inf`, `Infinity` forms are supported; Java suffixes/alternate numeric encodings are refused. Logical tokens are `TRUE`, `FALSE` or `NA`. A metadata boolean filter requires one logical value. Character tokens remain character tokens; a character `"NA"` is not declared numeric NA. The original source substitutes double quotes with apostrophes in filter values; that change is explicitly recorded and warned about.

## Read results and warnings

Only a successful complete batch creates `results/<query-id>/`:

| File | Contents |
|---|---|
| `original-result.rds` | Unchanged original R dataframe, including classes, row names, missing/nonfinite states and source column/row order. Use this when R type fidelity matters. |
| `original-result.R` | Original `dput` representation for inspection. |
| `original-result.tsv` | Quoted human-readable export with row names. Its `NA` text alone cannot distinguish a missing value from every literal string; consult RDS/typed states and raw responses. |
| `typed-result.json` | Complete ordered columns, original names/classes/storage types, row names and a state/text pair for every value. `NA`, `NaN`, `+Inf` and `-Inf` remain distinct. Finite doubles use round-trippable decimal text. |
| `original-report.json` | Original messages/warnings, original filter chunks and the explicit R console rendering model for native query strings. |

The top-level request capture, native query/coercion ledger, provenance, runtime binding and manifest retain settings and complete file hashes. `metadata-before`/`metadata-after` retain the registry/dataset/attribute/filter tables, metadata and original HTTP events. `worker/http` retains raw Curl diagnostic chunks in order: debug text, incoming headers, outgoing headers, incoming body and outgoing body. Event sizes, context and order are recorded in `worker-report.json`; `native-http-audit.json` records exact submitted queries. The complete raw responses remain available even when original R converts table tokens. Incoming bodies may contain HTTP transfer/content encoding; retain the associated headers and event sequence.

Each original process has stdout/stderr, session information and `process.json` with observed process identities and cleanup state. Supervision samples owned `ProcessHandle` descendants, wall time and workspace size every 25 ms. It is not an OS job container or a hard memory limit. No arbitrary-command analysis interface is provided.

The original 5000-value splitter is retained. For example, 10000 values form three balanced chunks rather than two fixed 5000-value chunks. Earlier filters vary fastest in Cartesian batching. Original temporary chunk caching can avoid a repeated HTTP submission while still repeating the returned rows; permanent cache disabling does not eliminate this source behavior. Original console/gettext conversion under C locale may print Unicode as `<U+03B1>`; native XML, actual HTTP and identifiers are preserved separately and never repaired from console text.

Original table/header conversion remains upstream. It can reorder attributes through its own matching rules and emits its own ambiguity warnings. HTML fallback replaces `TSV` throughout the XML, including filter values containing that text. Both original queries/responses and the warning remain visible; this adapter does not silently correct source behavior. Missing gene matches produce the original zero-row result rather than fabricated NA annotation rows. One-to-many mappings, repeated rows and empty values remain explicit.

## Limits and scope

Defaults are 600 seconds per R phase, 60 seconds per HTTP request, 512 MiB complete worker/published outputs and 128 MiB raw HTTP diagnostics per phase. Request overrides are `timeout_seconds` (1–86400), `http_timeout_seconds` (1–600), `maximum_output_bytes` (1024–4 GiB) and `maximum_http_bytes` (1024–1 GiB). Output capacity includes retained originals and published copies. Native planning allows at most one million total input values, 32 filters per query, 1000 attributes, 1024 complete batches per query, 8 MiB per XML query and 64 MiB combined XML per query. The request limit is 16 MiB; complete output file count is 100000. Capacity/timeout failures admit no batch result. Increase a supported limit or use an explicitly justified separate analysis; never silently omit values, rows or batches.

Complete worker reports and service metadata must fit 16 MiB each. A typed result JSON file must fit the smaller of 256 MiB and `maximum_output_bytes`; parsed JSON has a two-million-entry budget. These structural limits apply in addition to the byte/batch limits. An oversized complete query/result is refused, never truncated. Read the named report/capacity error and preserve the failed attempt.

Service metadata checks cannot independently authenticate biological reference content or turn separate HTTP calls into a database transaction. These controls do not reconstruct unspecified paper parameters, historical package images, GSEA/coloc/DE/enrichment tests, closest-gene tie/distance rules, gene masks or whole papers. The seven reviewed source roles are P049, P117, P134, P162, P223, P238 and P250; their original paper-specific qualifications remain in the catalog. In particular, P250's explicit GRCh37 context is retained, and P049's cited biomaRt 2.60.1 is available as a separate original edition.

The validation receipt distinguishes the complete original-comparison matrix from the final candidate's subsequent diagnostic-only refinement. The only changed JAR entry adds the explicit HTTP-capacity recovery message. Original R workers, planners, runtime profiles, query/result algorithms and all other JAR entries are byte-identical. The final candidate repeats the dependency/capacity/preservation/platform checks, complete Java25 original retrieval comparison and three-JVM native comparisons. The offline evidence verifier checks retained files and receipts; it does not rerun R or query a server.
