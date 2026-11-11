# Original Seurat marker analyses from typed requests

These four examples use **synthetic counts: 120 features and 36 cells**, with no biological interpretation. Java prepares sparse layers; the original guarded Seurat runtime performs Wilcoxon or logistic-regression tests. Native Java audits summaries, filtering, correction, ordering and transport.

**Before running:** provide R 4.6.1 and the exact private packages listed in the [development guide](../../docs/literature/SEURAT_DIFFERENTIAL_DEVELOPMENT.md). Edit `rscript` and `r_libraries` in each analysis JSON to your permitted local paths. The supplied paths describe this validation workspace; they are not included with a fresh clone. Presto and limma must be absent from those libraries. Missing requirements produce **SKIP3: skip the dependent analysis**. No example installs software.

From the repository root in PowerShell, using your installed JDK:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot'
./gradlew.bat --offline --no-daemon --no-configuration-cache --init-script tools/literature/seurat-differential-development.init.gradle executableJar
$seuratJar = 'build/literature-review/signac-runtime/differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar'
& "$env:JAVA_HOME/bin/java.exe" -Xmx768m -cp $seuratJar org.jlinalg.cli.SeuratDifferentialCli --request examples/seurat-differential/paired.json
& "$env:JAVA_HOME/bin/java.exe" -Xmx768m -cp $seuratJar org.jlinalg.cli.SeuratDifferentialCli --request examples/seurat-differential/verify-paired.json
```

Remove `--offline` only when cached Java dependencies are missing and downloads are permitted. The separate development JAR leaves the canonical executable unchanged. This milestone has no main-command route; invoke the class shown above.

| Request | Behavior |
| --- | --- |
| `paired.json` | Native Signac TF-IDF method 1, then original base-R Wilcoxon for group `a` versus `b`. Group `c` remains in the full normalization population. |
| `paired-lr.json` | The same pair, with original logistic regression and numeric `depth` plus factor `batch` covariates. Inspect original convergence warnings. |
| `all-lr.json` | Original one-versus-rest logistic regression for all three ordered groups, adjusted for `depth`; positive effects only. Its raw-p return threshold is deliberately 1 for comparison with the complete synthetic reference. Choose your own declared threshold for a real analysis. |
| `count-means.json` | Raw counts serve as the data layer; both testing and fold summaries explicitly select counts. No TF-IDF is computed. |

Run another case by replacing `paired.json` in the command. Each has a corresponding `verify-NAME.json`. Paths resolve against the JSON file's directory. Outputs go to separate `build/seurat-differential-example-NAME` directories. **Outputs must be new.** For another attempt, change `out` in the analysis request and `analysis` in its verification request; preserve previous evidence.

After every request, read `WARNINGS.md`, `analysis-status.json`, `diagnostics.json`, `worker/worker-report.json` and worker logs. Exit 0 can include an explicitly labelled **partial source comparison set**: require `complete_comparison_set: true` before treating it as a complete requested set. An omitted failed group is not a negative result. Exit 2 is **FAIL2: no result admitted**; exit 3 is **SKIP3: dependent analysis skipped**. A generic capture `COMPLETED.json` alone does not admit scientific results.

`markers.tsv` contains exact restored identities and source values. The `fold_change` column follows the selected source slot: a log fold change for counts/data, or an arithmetic mean difference for scale data. Source warnings and distinct NA/NaN/Inf kinds remain visible. Cell-level p values do not establish donor-level replication or biological validity.

**VERIFY is a consistency audit.** It does not rerun statistical tests, independently replay seeded sampling, authenticate installed DLLs or detect every coordinated fabrication. Read the guide's demonstrated forgery limitation before interpreting it as evidence.
