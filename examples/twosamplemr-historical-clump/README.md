# Original0.6.8 local-clumping examples

Read the [guide](../../docs/literature/TWOSAMPLEMR_HISTORICAL_LOCAL_CLUMPING.md)
and [checkpoint](../../docs/literature/TWOSAMPLEMR_HISTORICAL_CLUMP_CHECKPOINT_2026-11-21.md).
The recipes use a synthetic36-sample panel, not biological validation.

Copy a recipe in `recipes/` to a NEW filename in the same directory. Replace
rscript, all three r_libraries entries and plink_executable with exact runtime
paths. Library order is original0.6.8, historical extras, shared dependencies.
Keep the explicit profile/hash and scientific settings; paths resolve against
the JSON file without environment-variable expansion. Choose NEW output for
each attempt. Basic, multiple-group and numeric-looking-ID examples were
executed with only those runtime-path substitutions.

```powershell
java -Xmx1g -cp "C:/path/to/omicsmatic.jar" org.jlinalg.cli.TwoSampleMrHistoricalClumpCli --request "D:/path/to/examples/twosamplemr-historical-clump/recipes/my-basic.json"
```

Inspect WARNINGS.md,analysis-status.json,worker/report.json,all original logs
and results/decisions.tsv. A singleton can bypass reference/significance checks.
The numeric-ID example deliberately uses its separately paired BIM, preserving
original R identifier coercion rather than repairing leading zeros. A complete
empty selection produces no MR estimate. The ancestry declaration is not verified.

Offline verification executes no R:

```powershell
java -Xmx1g -cp "C:/path/to/omicsmatic.jar" org.jlinalg.cli.TwoSampleMrHistoricalClumpCli --verify "D:/path/to/completed-analysis"
```

`recipes/replay.json` shows REPLAY with a NEW sibling output. Use it through
`--request`; runtime overrides must still supply the exact profile. Never write
inside or over a retained capture. This profile requires a space-free output
path for its Windows R4.6.1 temporary area.

**SKIP3** skips dependent analysis and names the exact prerequisite to restore
using permitted installation/download. **FAIL2: NO COMPLETE RESULT** retains
the failed/partial evidence; fix the named cause and retry NEW output. No silent
ID, population, threshold, version or estimator repair is performed. Installed
runtimes/private full evidence are transferred separately from GitHub.
