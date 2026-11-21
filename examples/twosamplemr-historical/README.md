# Historical TwoSampleMR examples

These small synthetic examples exercise four tagged editions through the
standalone original-package adapter. They are not paper replications. Read the
[guide](../../docs/literature/TWOSAMPLEMR_HISTORICAL_WORKFLOWS.md) before using your
own data. There is no global CLI subcommand yet.

| Recipe | Original operation |
| --- | --- |
| `recipes/format-0.4.9.json` | FORMAT raw exposure columns using original format_data |
| `recipes/mr-0.5.6.json` | MR from an already harmonized canonical table |
| `recipes/pipeline-0.5.7.json` | Harmonization, MR and four sensitivity stages |
| `recipes/multivariable-0.6.8.json` | Multivariable harmonization/preparation; no causal model |

1. Build the project's executable JAR or restore the exact normal producer
   identified in the [checkpoint](../../docs/literature/TWOSAMPLEMR_HISTORICAL_CHECKPOINT_2026-11-21.md).
2. Copy a recipe to a new filename in the same `recipes` directory. Replace its
   `rscript` placeholder with your R4.6.1 Rscript path, then supply all three
   explicit library paths in order: selected historical version library,
   historical dependency library, preceding shared dependency library. Relative
   input/output paths resolve against the recipe's directory. No environment
   variable expansion is performed. Use a NEW output path on every attempt.
3. Run, replacing the JAR and copied recipe paths with your actual paths:

```powershell
java -Xmx1g -cp "C:/path/to/omicsmatic.jar" org.jlinalg.cli.TwoSampleMrHistoricalCli --request "D:/path/to/examples/twosamplemr-historical/recipes/my-format-0.4.9.json"
```

On success, inspect `WARNINGS.md`, `analysis-status.json`, `manifest.json`,
`worker/report.json`, original logs and `results/`. Formatting and multivariable
preparation are not causal estimates. `mr_keep` flags, omitted methods and
upstream nonfinite/error behavior require inspection before interpretation.

Offline verification needs Java and the matching producer, but no R execution:

```powershell
java -Xmx1g -cp "C:/path/to/omicsmatic.jar" org.jlinalg.cli.TwoSampleMrHistoricalCli --verify "D:/path/to/completed-analysis"
```

Replay uses a separate JSON file containing absolute paths and a NEW output
outside the retained parent analysis:

```json
{
  "schema_version": 1,
  "mode": "REPLAY",
  "analysis": "D:/path/to/completed-analysis",
  "out": "D:/path/to/new-replay"
}
```

Pass this file with `--request`. Replay reuses the captured original inputs,
seed and declared runtime binding; optional `rscript` and `r_libraries`
overrides must still provide the exact qualified version. It does not overwrite
the parent. VERIFY checks saved integrity and supported semantic/native fields;
it does not repeat all original R calculations or authenticate coordinated
replacement of an entire capture.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Restore the named exact runtime, package
or input using permitted private installation; if installation is disallowed,
leave it skipped. Adjacent package versions are not substitutes.

**FAIL2: NO COMPLETE RESULT.** Retain raw inputs, partials, original returns/errors
and logs. Fix the reported data/design, numerical, integrity, capacity or process
cause and retry a NEW output. Do not silently change identifiers, populations,
thresholds, source defaults or versions to obtain a result.

R execution was qualified on Windows R4.6.1 with declared present-day dependencies.
Linux tests verify saved captures; they do not establish Linux original-R
execution. Version0.4.9's reference build omits only broken `man/` documentation;
all scientific source/namespace/data bytes remain original. Keep full historical
source/private startup configuration and installed runtimes out of public logs.
