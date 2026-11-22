# g:GOSt Java API development checkpoint

The Java API now calls the original g:GOSt service with explicit scientific
settings and an exact required database build. Statistics, identifier mapping
and GO driver highlighting execute upstream. The API retains original request
and response bytes and exact JSON numeric tokens, including probabilities below
the range of a Java double. Inputs retain their supplied order and duplicates.

**Development component only.** The global file workflow, complete saved-bundle
VERIFY/REPLAY and catalog admission remain to be implemented. The canonical
catalog remains **149 scoped families (27 native Java + 122 upstream), 1,666
reviewed uses and 1,057 pending uses**. This checkpoint adds zero family credit
and zero canonical reviews. The full 250-paper implementation goal is unfinished.

## Exact-build warning and required action

**P052 reports `e111_eg58_p18_30541362`, but the inspected e111 archive returned
`e111_eg58_p18_28c0ce60` on 22 November 2026.** The shared archive prefix does not
establish the paper's full build. A real metadata-only prerequisite control
returned SKIP3, retained its raw exchange and admitted no result. Supply access
to the exact reported build or retain the skipped dependent historical analysis;
do not replace it with the inspected archive or today's service.

For gene-list execution, first call `Session.versions()` with the required
build. This checks the prerequisite without uploading genes. `profile()` also
checks its own response build and rejects a mismatch. Its check happens after
that service call; it is not a substitute for the metadata preflight. The future
file workflow must perform preflight before gene submission.

SKIP3 means a required exact service/build is unavailable. Restore the named
permitted endpoint/build or retain the skip. FAIL2 means no complete result:
retain the raw arguments, exchanges and error; fix the stated capacity, input,
protocol or timeout cause and retry into new output. No automatic retry,
redirect, archive substitution, identifier repair or statistical-method change
is performed. A complete empty result remains distinguishable from a failed
request. Inspect the original response metadata for failed, ambiguous and
duplicate identifiers and for service normalization of supplied settings.

## Implemented API and use

The entry points are `org.jlinalg.gprofiler.GprofilerGostClient` and
`GprofilerJson`. Settings bound request/response bytes, identifier occurrences,
request time and request spacing. The production transport validates TLS and
retains bounded partial replies on timeout, interruption or capacity failure.
Close each client after use. Missing service prerequisites report SKIP3;
invalid/protocol/capacity/interruption/timeout failures report FAIL2 through
`Failure.suggestedExitCode()`. Preflight input validation raises `IOException`;
treat it as FAIL2 and correct the request. Read and save `Failure.exchanges()` as well as the
caller arguments. This component does not yet write an analysis bundle for you.

Build with JDK 21; the normal build targets Java 17 bytecode:

```powershell
.\gradlew.bat test --tests "org.jlinalg.gprofiler.*" executableJar
```

Metadata discovery submits an organism name and no gene list:

```java
import org.jlinalg.gprofiler.GprofilerGostClient;

try (var client = new GprofilerGostClient(GprofilerGostClient.Settings.defaults())) {
    var metadata = client.openSession().versions(
        new GprofilerGostClient.Service("", null), "hsapiens");
    System.out.println(metadata.observedBuild());
}
```

For enrichment, choose a complete required build explicitly. An empty archive
key selects the current service; a key such as `e111_eg58_p18` selects archive3.
Calendar dates and R client editions do not supply a complete database build.
`profile(service, parameters, true)` explicitly authorizes external submission
of the supplied gene identifiers and query names. The provider requires inputs
to exclude personal data; submit only data you have the right to share. See
[the provider's terms](https://biit.cs.ut.ee/gprofiler/page/terms-of-service).

The parameter map requires all 16 fields. For example, these synthetic public
symbols request an unordered GO BP query with driver highlighting:

```json
{
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
}
```

Use `GprofilerJson.read()` to load the UTF-8 map and preserve exact numeric
tokens. Query arrays may instead be a map of named arrays. Supply custom
background identifiers and their domain explicitly. The API accepts literal
correction values `g_SCS`, `bonferroni` and `fdr`; it does not infer settings
from the name of a paper or client. Driver highlighting, ordered analysis,
under-representation and combined queries retain their distinct meanings.
The raw result is available through `Result.response()`.

## Measured checks and evidence limits

- A normal project build and executable JAR passed 13 tests, with zero failures,
  errors or skips. Tests include Unicode/numeric identity, unknown settings,
  upload refusal, exact archive mismatch, HTTP/protocol failures, real loopback
  response limits, redirects and partial-response timeout capture.
- Four actual packaged Java service cases returned 42 highlighted-query rows,
  986 ranked/custom-background rows, 919 HPO under-representation rows and
  1,081 combined-FDR rows. All report `e114_eg62_p19_9dc92ac8`. These are synthetic
  controls, not results from the seven papers.
- The unchanged official Python 1.0.0 source ran offline on those 3,028 recorded
  rows. All fields retained by that client, their row order and metadata match.
  Its HTTP transport was an injected recorded-response stub; the requests
  runtime was not installed or qualified. No new network requests were made
  by this reference comparison.
- That Python client has no driver-highlighting argument, serializes an absent
  background as an empty string and forces custom scope when given a background.
  Those differences remain explicit. The Java API preserves its caller's
  direct service settings. This is not complete original-client parity.
- Original R 0.2.1/0.2.3/0.2.4 source archives were inspected and hashed. No
  original R client or author analysis was executed. Statistics were not
  independently refit or biologically calibrated.
- The source audit rechecks SHA-256 and DOI identities for all 250 primary XML
  files and screens both named g:Profiler and broader g:GOSt/g:SCS mentions.
  Seven named papers and no unbound broader candidates or table candidates
  were found. Full primary text remains private; public evidence contains
  paraphrases, pointers and hashes.

See [development status](gprofiler-api-development/STATUS.json),
[source census and seven individual interpretations](gprofiler-api-development/CENSUS.json),
[JUnit results](gprofiler-api-development/junit.xml),
[live-service summary](gprofiler-api-development/live-service-summary.json) and
[original-client comparison](gprofiler-api-development/original-client-reference-summary.json).
These public receipts do not constitute a complete portable runtime evidence
bundle. The full wire replies, reference source, failed attempts and actual JARs
remain in the private development evidence below. Hashes are integrity checks,
not authentication against coordinated replacement.

Check the declared component source and saved receipt consistency without
network or Java/R execution:

```powershell
python -B tools/literature/verify_gprofiler_component_checkpoint.py
```

Optional `--jar PATH` additionally checks the exact original test JAR bytes.
This reader does not rerun the tests, reverify the private reply bundle or refit
upstream statistics. Later source contexts must retain or supersede this
checkpoint explicitly; do not refresh its old hashes to hide a source change.

The exact current development JAR is 197,959,229 bytes with SHA-256
`4a00173a9c1926eaf68921ed0310d116c2789a75923e8c92b155bb61d1cd46b3`:

`D:/projects/JLinAlg/build/gp-1/build/gprofiler-1/normal-build-3/project-build/cli/omicsmatic-0.4.0.jar`

It is a source-built component test producer, not a release. Other providers
were not requalified against this JAR. The earlier 11-test producer and its
exact source context remain under `normal-build-2/`; no old hashes were refreshed.

## Source distinctions to retain during admission

P052 names a full database build and unusual minus-log-q protein-selection
wording; preserve both. P088 uses 63 genes and Ensembl 110 protein-coding
background with g:SCS, separate from FUMA. P090 names R client 0.2.3, GO driver
selection and Bonferroni-adjusted probabilities. P096 dates the web build to
14 September 2023 and requests default g:SCS/driver highlighting. P208 uses
ordered de novo-variant gene lists and 22,321 exome targets as background.
P227 uses R client 0.2.1 for HPO under-enrichment with 3,092 background genes;
the paragraph's separate feature-regression Bonferroni statement does not
establish the g:Profiler correction. P228 uses GO analysis after DESeq2 gene
selection and Y-chromosome exclusion. Unreported editions, backgrounds and flags
remain unknown. The census gives the exact primary full-paragraph pointers.

## Work to resume

Implement global file RUN/VERIFY/REPLAY with exact prerequisite metadata checks
before gene upload, original protocol retention, response-derived reports,
prominent SKIP3/FAIL2 remedies and atomic admission. Exercise saved-protocol
corruption, dependency, capacity, permissions and alias controls. Then admit
the provider and seven reviews through a catalog transaction, retaining every
prior review/anchor and updating the full manifest and installed reports.
Continue FUSION and the remaining full-corpus tools and use reviews afterward.

All complete current development evidence and helpers must transfer privately:

`D:/projects/JLinAlg/build/gp-1/build/gprofiler-1/`

`D:/projects/JLinAlg/build/*gprofiler*20261122_118.py`

Publication receipts are in
`D:/projects/JLinAlg/build/git-publish/20261122-gprofiler-118/`.
Preserve all 74 worktrees and previous runtimes/evidence. Existing process-safety
restrictions, excluded Cell Ranger, outstanding 33 citation-panel failures and
blocked historical exports remain as documented in the preceding handover.

Source protocol documentation: [official API](https://biit.cs.ut.ee/gprofiler/page/apis).
Reference source: [official Python distribution](https://pypi.org/project/gprofiler-official/1.0.0/)
and [R client](https://cran.r-project.org/package=gprofiler2).
