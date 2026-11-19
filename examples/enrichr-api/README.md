# Explicit synthetic Enrichr API probe

**This example uploads eight predefined gene symbols to maayanlab.cloud.**
It tests the development Java API component and current remote service.
It is not a general file-based workflow or a reproduction of any catalog paper.
The [component guide](../../docs/vignettes/enrichr-api-component.md) explains
bounds, exact values, warnings, the POST/GET discrepancy and remaining gaps.

Published 0.4.0 predates this component. From the repository root in PowerShell,
verify the saved packet and restore the exact small development component:

```powershell
python -B tools/literature/verify_enrichr_component.py --out build/enrichr-check-NEW --restore-component
$enrichrJar = 'build/enrichr-check-NEW/component/omicsmatic-enrichr-component-development.jar'
New-Item -ItemType Directory -Path build/enrichr-example-classes
javac --release 17 -encoding UTF-8 -cp $enrichrJar -d build/enrichr-example-classes examples/enrichr-api/EnrichrApiExample.java
java -cp "$enrichrJar;build/enrichr-example-classes" EnrichrApiExample build/enrichr-example-NEW --allow-synthetic-upload
```

On Linux/macOS, use `:` rather than `;` in the Java classpath. Java 17+ is
required. The restored JAR contains this API component, not the OmicsMatic
executable or a release. Its corresponding Java sources are in this repository;
the restorer supplies the project license. Alternatively, build current sources
with the normal `executableJar` task and point `$enrichrJar` to that new build.

The example queries the exact `MSigDB_Hallmark_2020` name, saves the raw current
GMT, and explicitly constructs a synthetic background from the exact `DNA Repair`
and `p53 Pathway` terms plus the eight query IDs. Its background preparation
deliberately excludes empty fields and deduplicates/sorts; that recipe is written
to `BACKGROUND-PREPARATION.txt`. It is not a default scientific population.

Read `query.txt`, `background.txt`, `server-prepared-genes.txt` and `WARNINGS.md`.
Numbered `.request`, `.response` and `.properties` files retain each operation,
hashes, status, headers, completion and errors. The raw API result bodies contain
the full returned term families. `COMPONENT-PROBE-COMPLETED.properties` is saved
only on success; it explicitly states that the general workflow is unqualified.

**If the service/exact library is unavailable, exit 3 skips this dependent
analysis. Restore permitted access/the exact resource or retain the skip.**
Invalid data/schema, capacity and interruption return 2 with no complete
result. Inspect `FAILURE.txt` and `WARNINGS.md`, preserve captures, correct the
cause and retry a NEW directory. Existing destinations are never overwritten.
Do not share captures from real gene lists without reviewing their contents:
requests contain submitted symbols and responses can contain shareable list IDs.
