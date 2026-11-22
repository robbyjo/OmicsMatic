# Original FUSION workflow examples

These are **artificial** data: 64 samples, 12 variants and three prepared weight
files. `ART...` and `synth...` identify generated records, not people or discoveries.
The association example uses prepared weights; the training example estimates
five original models with two-fold cross-validation from a separate artificial
phenotype and covariate. They are execution checks, not biological calibration.

Build the current source using the project's JDK21 setup (`gradlew.bat executableJar`
on Windows). Existing release assets have not been replaced. Read the
[workflow checkpoint](../../docs/literature/FUSION_WORKFLOW_CHECKPOINT_2026-11-22.md)
for the measured producer, supported operations, versions, warnings and scope.

**Edit runtime paths before running.** The JSON files name the private library
layout used in the qualified checkout, and the Linux file names its permitted
Rscript and PLINK/GEMMA locations. Git does not install those runtimes. Provide
the exact declared R/package editions in permitted private locations, or expect
**SKIP3: dependent analysis skipped**. Do not accept an undeclared fallback.
The required versions are recorded in each request. Other versions require fresh
qualification; editing a declaration does not prove numerical equivalence.

The request's input/output paths resolve relative to the JSON file. `build/` must
exist; output must be NEW. The checked-in input files are ready to read and are
never modified by training, including rank normalization.

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar fusion-tools --request examples/fusion-original/association-windows.json
java -jar build/cli/omicsmatic-0.4.0.jar fusion-tools --verify build/fusion-example-association
```

Run `training-linux.json` from a permitted Linux environment with its runtime
paths edited for that machine. It explicitly enables GEMMA output routing;
upstream scientific parameters and exit statuses are retained. Current-source
prediction model names are `blup`, `lasso`, `top1`, `enet`, `bslmm`. SuSiE
colocalization is a separate association option, not a prediction model here.

Inspect `WARNINGS.md`, `summary.json`, original logs and admitted exports after
every run. The completion line prints finite/no-result counts. **FAIL2** admits
no complete result: keep logs/partial exports, fix the cause and retry NEW output.
`allow_no_result_rows=true` retains documented source absence; it does not turn
missing statistics into estimates. Missing/filtered values and zero p-values
remain as produced by the source.

REPLAY re-executes the same original runtime using captured inputs into NEW output:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"../../build/fusion-example-association","out":"../../build/fusion-example-replay"}
```

Save that JSON beside these examples. The default requires byte-identical
scientific exports. Original external-tool RNG settings and PDF metadata can
produce differences. Only a RUN request that explicitly sets
`admission.allow_replay_differences=true` permits differing replays, with their
comparison and warnings retained. VERIFY is an offline integrity/relationship
check, not an RDS decoder, independent refit or authentication.

To regenerate a separate NEW copy of the fixtures:

```powershell
python -B tools/literature/fusion_workflow_reference/generate_data.py --out build/NEW-fusion-data --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe"
```

Byte identity is checked with the qualified Windows R4.6.1; other R/platform
versions can serialize the same data differently. Preserve the frozen inputs
and generation logs. Original software/source license notices remain included
with the bundled [pinned FUSION source](https://github.com/gusevlab/fusion_twas/tree/9346c1222bffbb34499fa7a8e23c1b701b55cb05).
