# Transcript-site mapping examples

The typed workflow is available as `fuma-tools --method transcript-sites` in the
validated development candidate. It reads study TSV inputs, preserves their raw
snapshots and emits complete variant/transcript dispositions, site hits, gene
mappings and provenance. This is a native supplied-resource method; the complete
FAVOR integration remains open.

Run three synthetic CLI workflows and their independent complete-reference audit
from the repository root:

```powershell
python tools/literature/verify_transcript_site_workflow.py --jar build/literature-review/transcript-site-candidate-1/jlinalg-0.3.7.jar --jar-sha256 ccf750c9091cc6136ea18d67eed553879919d8201654933a8d27837869316e62 --work build/site-cli-demo-1
```

Use `--java "C:/path/to/java.exe"` if Java is not on PATH. The example requires
Python 3 and Java 17+, with no third-party Python packages. Use an entirely ASCII
work directory and JAR command-line path on Windows. The verifier deliberately
puts Unicode data paths inside UTF-8 JSON carriers. Choose a new work directory
for every run. The private development JAR above is not a published release;
when using another locally built JAR, supply its actual SHA-256 explicitly.

The verifier creates 12 association rows and five transcript rows, then runs
inclusive TSS/TES, strict TSS-only and exact-site workflows. Its independent
Python Decimal/integer full scan checks every ordered disposition, transcript
count, site identity/distance and gene mapping, plus all output/source/snapshot
hashes and resource/mapping policies. Inspect `verification.json`, each result's
`WARNINGS.md` and every scientific table. A long gene's interior is not a site.
Coincident TSS/TES hits and repeated transcript sites remain separate evidence;
gene mappings count each gene once per original variant/context row.

**Warnings and recovery:** unavailable or forbidden Java means verifier **SKIP,
exit 3**. Provide Java 17+ on a permitted host or retain the analysis as skipped.
Invalid artifacts, inputs, models or capacities mean **FAIL, exit 2**. Inspect
the named cause, `verification.json`, stderr and result `WARNINGS.md`; scientific
partials remain in `partial/`. Correct the cause and rerun to a NEW directory.
Existing outputs are never overwritten and no analysis installs software.

For your own inputs, follow the [TSV and JSON workflow guide](../../docs/literature/omics-2023-2026/TRANSCRIPT_SITE_MAPPING.md).
The earlier sealed Fisher JAR does not include this command.

## Source API example

This runs the **source API** directly, using fixed synthetic inputs. It
does not read study data, access a network or install dependencies. The previously
sealed Fisher development JAR does not contain this new class. Compile these two
source files explicitly with a JDK 17 or newer.

From the repository root in PowerShell, choose a new compilation directory:

```powershell
New-Item -ItemType Directory -Path build/site-example-1 -ErrorAction Stop
javac --release 17 -Xlint:all -Werror -encoding UTF-8 -d build/site-example-1 src/main/java/org/jlinalg/genetics/TranscriptSiteMapping.java examples/transcript-site-mapping/TranscriptSiteExample.java
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; inspect compiler diagnostics before continuing.' }
java -cp build/site-example-1 TranscriptSiteExample
if ($LASTEXITCODE -ne 0) { throw 'Example failed; inspect the named cause before continuing.' }
```

The example checks nine complete variant dispositions, six site hits and five
unique variant-row/gene mappings. The long-gene interior remains unmapped; a
positive P value of `1e-1000` remains available; the exact 10,000-base window edge
is included. A reverse-strand transcript demonstrates signed site distances.
All dispositions and site hits print to stdout. Scope and missing-P warnings
print prominently to stderr with required actions.

**Required recovery:** if a JDK is unavailable or its use is forbidden, skip this
recipe and provide JDK 17+ on a permitted host. If the compilation directory already
exists, choose a new one. On a compiler or example failure, retain the diagnostics
and correct the named cause before rerunning. This source-only recipe prints to
stdout/stderr; use the typed workflow above for protected study input snapshots,
complete scientific tables and analysis status manifests.

Read the [scope, source findings and API rules](../../docs/literature/omics-2023-2026/TRANSCRIPT_SITE_MAPPING.md)
before adapting the API to an identified gene/transcript resource. FAVOR remains
an incomplete tool integration.
