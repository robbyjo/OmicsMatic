# Prepared native smartpca examples

See [the workflow guide](../../docs/literature/SMARTPCA_WORKFLOWS.md) for complete
commands, source scope, input rules and warning remedies. Run from the project
root using a freshly built complete project JAR:

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --request examples/smartpca-native/run.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --verify build/smartpca-native-example
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --request examples/smartpca-native/replay.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --verify build/smartpca-replay-example
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --request examples/smartpca-native/shrinkage.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.SmartpcaToolsCli --verify build/smartpca-shrinkage-example
```

The 16-individual, 96-marker synthetic inputs are byte-exact copies of
`shrink-complete/input.*` in the saved original-program reference ZIP. Twelve
individuals form two reference populations; four are projection targets. They
are not cohort data. `run.json` uses least-squares projection and no outlier
removal; `shrinkage.json` additionally uses source perturbation shrinkage.

Each request names a NEW output under `build/`. Preserve previous captures and
choose a different `out` before repeating a run. Read `WARNINGS.md`, the saved
status and per-individual admission flags. SKIP3 skips dependent work; FAIL2
admits no complete result. This example does not perform FST or reproduce a paper.
