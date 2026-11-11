# Native Signac normalization example

From the repository root, use the separate development JAR:

```powershell
$signacJar = 'build/literature-review/signac-runtime/tools-development/omicsmatic-0.3.7-signac-tools-development.jar'
java -jar $signacJar signac-tools --workflow normalize --request examples/signac-normalize/request.json
java -jar $signacJar signac-tools --workflow normalize --request examples/signac-normalize/verify-request.json
```

Java 17+ is required; the packaged validation uses Java 21 and 25. R and external software are unnecessary. Build this development artifact with the repository Gradle wrapper and `-I tools/literature/signac-tools-development.init.gradle executableJar`; older released JARs lack this route. See [the workflow guide](../../docs/literature/SIGNAC_TOOLS.md).

Every relative path is relative to its JSON file. The example output must not exist. For another run, copy the request, adjust input paths if moving it, and choose a NEW output. Preserve failed attempts.

The synthetic matrix is the exact `hand-count-versus-document-frequency` original-control population. It contains three features, three cells, and five stored coordinates. Feature totals are 3, 3, and 12. The COUNT cutoff 5 selects only `chr1:200-280`, but normalization still writes all three features and all five coordinates. `INPUT-PROVENANCE.json` records the retained control ZIP and input hashes.

| Request | What to inspect |
| --- | --- |
| `request.json` | Method 1, computed IDF, COUNT selection greater than 5 |
| `supplied-idf-request.json` | Method 2; supplied IDF is already on the requested method scale |
| `overflow-request.json` | Method 1 with supplied IDF deliberately retains source `Inf`; skip downstream analyses requiring finite values |
| `method4-request.json` | Method 4 and percentile selection; scale has no effect for this method |
| `top-features-request.json` | Selection and feature metadata without normalization |
| `empty-selection-request.json` | A valid empty selection; skip downstream LSI instead of silently lowering the cutoff |
| `keep-existing-request.json` | Preserve the declared existing variable-feature order |

**Warnings and required action:** read `WARNINGS.md`, `analysis-status.json`, `diagnostics.json`, and all output tables. Exit 2 means **FAIL2: no result admitted**, including partial files. Fix the named count, identity, IDF, source, or capacity error and retry into NEW paths. Source behavior replaces stored `NaN` with zero and retains infinity; neither permits silently repairing data for downstream use. Native arithmetic can complete with warnings, so exit 0 alone is insufficient for downstream admission.

`variable-features.tsv` is headerless and may be empty. `feature-metadata.tsv` has one row per original feature. `normalized.mtx` is real-valued and retains the input coordinate geometry; it is absent in TOP_FEATURES mode. Use original integer counts for the separate LSI workflow, which performs its own normalization.

Arithmetic profiles cover retained Signac 1.6.0, 1.7.0, 1.9.0, 1.14.0, and 1.17.1 functions. Selection follows the retained **current 1.17.1 assay-wrapper contract** for every profile. This example does not execute historical whole packages or establish biological validity. VERIFY replays arithmetic, geometry, selection, and saved-source bindings; it does not authenticate who executed a bundle.
