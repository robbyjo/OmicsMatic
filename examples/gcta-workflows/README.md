# GCTA development workflow: minimal native example

**Development checkpoint; global discovery and GCTA catalog admission remain
unfinished.** Read the [handover and warnings](../../docs/literature/HANDOVER_2026-11-20_GCTA_WORKFLOWS.md).
Java 21+ and Python 3 are required for this example. The native engine requires
no GCTA executable. Build the normal executable JAR using the project's Gradle
wrapper; use the actual JAR path reported by `executableJar`.

From the repository root, these PowerShell commands create a new synthetic
fixture and run the standalone class. Replace the JAR path with your build's
actual output. Choose a new fixture directory if the named directory exists.

```powershell
python -B examples/gcta-workflows/make_fixture.py --out build/gcta-example-new
$jar = 'C:/absolute/path/to/omicsmatic-0.4.0.jar'
java -cp $jar org.jlinalg.cli.GctaWorkflowCli --request build/gcta-example-new/request.json
java -cp $jar org.jlinalg.cli.GctaWorkflowCli --verify build/gcta-example-new/native-grm-result
```

Input paths and `out` in a request resolve relative to that request file.
Read the generated `request.json`: it explicitly selects `profile`, `engine`,
`model`, prepared genotype files and GRM algorithm. The FAM phenotype `-9` is
irrelevant to this genotype-only GRM request; phenotype models require a
separate complete, finite phenotype file and do not accept missing `-9` values.

Each output directory must be new. Keep the complete bundle, including captured
inputs, marker dispositions, exact pair counts, binary GRM, status and warnings.
Exit 0 means this bounded request completed. Exit **2 means NO COMPLETE RESULT**;
preserve all partials and fix the reported cause. Exit **3 means the dependent
analysis was skipped**; supply the exact permitted prerequisite or keep the
skip. Never change a saved hash or substitute a model/runtime to clear an error.

For a native replay, write this UTF-8 JSON beside `request.json` and run the
same class with `--request` pointing to it:

```json
{
  "schema_version": 1,
  "mode": "REPLAY",
  "analysis": "native-grm-result",
  "out": "native-grm-replay-new"
}
```

The parent bundle is verified and preserved. A replay destination cannot be
inside a retained capture. `VERIFY` does not require the original GCTA runtime;
it checks the saved bundle. Native GRM is recomputed, while original-model
verification checks declared input/export invariants without independently
refitting the model.

The eleven typed models are `GRM`, `PCA`, `COJO_SELECT`, `COJO_JOINT`,
`COJO_CONDITION`, `REML`, `LD_SCORE`, `LD_SCORE_REGION`, `SPARSE_GRM` and
`FASTGWA_BINARY` and `MTCOJO`. Only `GRM` and `SPARSE_GRM` accept `engine: "native"`.
The remaining models require `engine: "original"` and an exact qualified
Windows or Linux GCTA 1.94.1 executable in `runtime`; Windows also requires its
matching adjacent `libomp.dll`. They are not installed by this example.
The [mtCOJO examples](../mtcojo/README.md) explain its prepared-parameter native
component, original-runtime requests and source-specific warnings. Its standalone
prepared-data execution paths are qualified for 1.94.1; exact older editions,
global command discovery and GCTA/COJO catalog admission remain open.

Complete machine-specific requests and raw synthetic inputs for all development
controls are saved in `normal-workflow-runs-1` inside the public checkpoint
archive. Inspect and adapt paths into a new output directory before reuse;
those historical scripts are evidence, not a portable installer. The closed
request fields and ranges are defined in
[GctaWorkflowContracts.java](../../src/main/java/org/jlinalg/cli/GctaWorkflowContracts.java).
Do not pass arbitrary upstream flags. Original inputs must be independently
prepared for the declared complete cohort; this component does not perform
sample/variant QC or infer a paper's ancestry, ploidy, phenotype coding or model.
