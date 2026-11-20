# Run original SuSiE through the main command

Use **`susie-tools`** to call the qualified original susieR workflows from a
current source-built JAR. The native **`susie`** command is a separate method.
Choose the engine explicitly; the program does not fall back between them.
Published release 0.4.0 predates this global command.

From a fresh source checkout in PowerShell:

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.0.jar susie-tools --help
```

The retained standalone class invocation also works. The earlier
[standalone workflow guide](susie-workflow.md) documents the same request
schemas, exact package versions, input formats, limits, output files and
installation requirements. Its global-command-open statement belongs to
that earlier qualification checkpoint. This guide provides the current
invocation. The [20-paper source-role guide](../literature/SUSIE_CATALOG_INTEGRATION.md)
retains literal author roles and remaining historical/hosted-workflow gaps.

## Prepare a NEW example

The preparer writes synthetic inputs and a request, not analysis results.
It installs no packages. Supply a permitted R 4.6.1 executable and private
libraries containing the exact selected profile. These development-machine
paths are examples; another machine needs its own permitted exact runtime.
The original workflow guide lists all dependency versions and installation
steps. Do not install into an existing reference library.

```powershell
python -B examples/susie-workflow/prepare_example.py --out build/susie-global-example-NEW --profile SuSiE-0.14.2-R4.6.1 --model RSS_Z --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --library 'build/susie-high-impact-1/runtime-install-1/0.14.2/library' --library 'build/susie-high-impact-1/runtime-profile-1/library' --library 'build/coloc-high-impact-1/runtime-coloc603-1/library'
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar susie-tools --request build/susie-global-example-NEW/request.json
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar susie-tools --verify build/susie-global-example-NEW/analysis
```

Successful captures retain raw inputs, prepared data, the original RDS,
namespace/source checks, runtime bindings, all warnings and partials. Read
`WARNINGS.md` and `analysis-status.json` before using tables. VERIFY needs
no R and checks saved integrity, input preparation and result structure;
it does not independently refit models or authenticate the host.

## Replay in a NEW output

Save a replay request as `build/susie-replay-request-NEW.json`, using actual
absolute paths for your prior analysis and new destination:

```json
{
  "schema_version": 1,
  "mode": "REPLAY",
  "analysis": "D:/analysis/susie-prior",
  "out": "D:/analysis/susie-replay-NEW"
}
```

```powershell
java -Xmx2g -jar build/cli/omicsmatic-0.4.0.jar susie-tools --request build/susie-replay-request-NEW.json
```

Replay uses captured inputs/options and the original runtime bindings.
Explicit `rscript` and `r_libraries` fields can rebind a permitted exact
runtime. Unknown scientific fields, unannounced package/version changes,
existing output and aliases into the parent are refused. The parent capture
is retained. RUN and REPLAY do not download/install or select a substitute.

## Supported profiles and remaining scope

The explicit original profiles are `SuSiE-0.11.92-R4.6.1`,
`SuSiE-0.12.35-R4.6.1` and `SuSiE-0.14.2-R4.6.1`. Individual-level,
version-specific RSS and sufficient-statistic interfaces retain their
original differences. For example, legacy 0.11.92 RSS has no sample-size
argument, and its beta/SE interface is sufficient-statistic based; later
editions provide RSS beta/SE. Do not translate these silently. No sample
size, allele matching, locus/LD preparation, prior weights or author settings
are inferred from a paper name.

**Exact 0.11.43 is unsupported here.** Its original source/runtime remains
unestablished for this adapter; dependent requests are SKIP3. An adjacent
edition is not a substitute. Linux JVM tests and offline verification pass;
**Linux R scientific deployment remains unqualified**. Current scientific
runs use the explicitly qualified Windows R profiles. TensorQTL/PolyFun/
coloc/FUSION hosting, cross-validation, sequential lead selection, external
result resources and complete author workflows remain separate tasks.

The global route changes invocation only. It adds no scientific function,
family/review credit, historical parity, full-package or whole-paper claim.
The admitted catalog remains 138 scoped integrations, 1,503 reviewed uses
and 1,201 pending out of 2,704 observations. The full goal is unfinished.

## Warnings and required recovery

**SKIP3 — dependent analysis skipped:** supply the named exact permitted
input, source, R executable or package profile. If installation/access is
forbidden or unavailable, retain the skip and do not use partial output as
an analysis result. Exact requirements appear in the console and warnings.

**FAIL2 — no complete result:** retain raw inputs, original model objects,
partials and diagnostics. Correct the named invalid schema/data/identity,
integrity, numeric, convergence, capacity or process cause and use NEW
output. Nonconverged objects are retained and refused. Never silently repair
IDs, alleles, thresholds, population labels, nonfinite values or algorithms.

Two separate historical full-marker verifier mains still require the exact
original 0.3.7 JAR recorded in the catalog checkpoint. Otherwise they remain
SKIP3; this does not grant historical verification credit. Cell Ranger stays
excluded. The literature reports remain withheld from Pages; no release is
created by adding this command.
