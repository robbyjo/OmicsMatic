# Original TwoSampleMR examples

These three examples use synthetic canonical package tables. They exercise the
original TwoSampleMR0.7.14 c08ab8a6 namespace on R4.6.1, with complete retained
original returns/errors and explicit RNG settings. They are not author data or
biological evidence. Build current source: the released 0.4.0 predates this workflow.

1. Copy this whole directory to a working directory, preserving `data/`.
2. Edit `rscript` and `r_libraries` in each JSON to the exact permitted R4.6.1
   executable and private library. Use the separately transferred checksummed
   runtime to reproduce the checkpoint, or qualify a newly installed environment.
   The [workflow guide](../../docs/literature/TWOSAMPLEMR_WORKFLOWS.md) explains installation,
   source/version guards and dependency-environment limits.
3. Keep `out` NEW for every attempt. Run from the repository root with your built JAR:

```powershell
.\gradlew.bat executableJar
java -Xmx1g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrWorkflowCli --request D:/work/mr-demo/mr.json
java -Xmx1g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrWorkflowCli --request D:/work/mr-demo/pipeline.json
java -Xmx1g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrWorkflowCli --request D:/work/mr-demo/multivariable.json
java -Xmx1g -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrWorkflowCli --verify D:/work/mr-demo/output/pipeline-001
```

`mr.json` estimates effects from six already harmonized independent synthetic
instruments. `pipeline.json` starts with canonical exposure/outcome tables,
retains allele flips/removals under action2, then runs MR and four sensitivity
stages. Four of its six source rows remain eligible. `multivariable.json` prepares
four instruments against two exposures; absent outcome P-values remain original
`NULL`, with an explicit warning. Both estimation examples use 1,000 original R
bootstrap replicates. The existing native MR API is a separate implementation.

To replay, save this as `replay.json` beside the example requests and run the same
class with `--request D:/work/mr-demo/replay.json`:

```json
{
  "schema_version": 1,
  "mode": "REPLAY",
  "analysis": "output/pipeline-001",
  "out": "output/pipeline-replay-001"
}
```

**Read `WARNINGS.md`, `analysis-status.json`, original logs and
`results/method-population.tsv` before using an estimate.** Source-omitted
methods have no result. Invalid/nonfinite/empty-original/capacity/process
failures are FAIL2, with evidence retained. Missing exact prerequisites are
SKIP3; restore the named permitted prerequisite or retain the skip. Downloads
and private installs are allowed where permitted; if installation is prohibited,
name the affected analysis and prerequisite and skip it. No edition, effect
scale, identifier, instrument population or estimator is silently substituted.
