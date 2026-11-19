# Enrichr workflow: synthetic example

Build the current source (`gradlew.bat executableJar`) and run the following
from the repository root with Java 17 or later. The released 0.4.0 JAR predates
this command. The normal source-build artifact used for qualification is pinned
in the [workflow guide](../../docs/vignettes/enrichr-workflow.md).

**The eight query genes and 16-gene background are artificial demonstration
inputs. They do not define a scientifically justified background or biological
result.** RUN uploads these gene names to the remote current human/mouse
Enrichr service; `allow_upload: true` makes that consent explicit. Historical
paper/client versions are not reproduced by a current service request.

```powershell
$enrichrJar = (Resolve-Path 'build/cli/omicsmatic-0.4.0.jar').Path
java -jar $enrichrJar enrichr-tools --request examples/enrichr-workflow/run-standard.json
java -jar $enrichrJar enrichr-tools --request examples/enrichr-workflow/run-custom.json
java -jar $enrichrJar enrichr-tools --request examples/enrichr-workflow/run-library.json
java -jar $enrichrJar enrichr-tools --request examples/enrichr-workflow/verify.json
java -jar $enrichrJar enrichr-tools --request examples/enrichr-workflow/replay.json
java -jar $enrichrJar enrichr-tools --verify build/enrichr-example-replayed
```

Each RUN/REPLAY output must be NEW. Requests resolve paths relative to their
own JSON files; outputs go under repository `build/`. To repeat, make copies
of the request JSON with different `out` paths. Preserve earlier results and
failed attempts. VERIFY and REPLAY are offline and require the exact producer
artifact used for the parent; keep the JAR with your evidence.

Inspect `WARNINGS.md`, `analysis-status.json`, `summary.json`, `terms.tsv` and
`hits.tsv`. The library-only bundle has no enrichment results. Keep numeric
tokens as strings; use a quote-aware TSV parser. Do not cap source positive
infinite scores, invent a finite replacement for P=0, rerank terms or
recompute adjusted P values silently. Custom normalization is unobserved;
the explicit acknowledgement is retained in the bundle. A standard stored
query echo is not proof of the service's final scoring population.

**SKIP3:** service/exact dependency unavailable; skip dependent analysis,
restore the named service/dependency and retry NEW output. **FAIL2:** invalid
data/schema, changed evidence, capacity or interruption; preserve raw/partial
captures and `FAILURE.json`, fix the cause and retry NEW output. A failed
attempt has no complete result marker. Neither a successful service reply
nor a complete synthetic bundle proves biological validity.
