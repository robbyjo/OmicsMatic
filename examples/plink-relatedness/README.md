# PLINK-hosted relatedness example

Read the [workflow guide](../../docs/vignettes/plink-relatedness-workflows.md)
and its warning/recovery table first. The bundled `synthetic/cohort.{bed,bim,fam}`
contains 17 synthetic participants and 129 SNPs, including a deliberate clone.
It is test data, not a scientific reference panel. `SYNTHETIC.json` retains
the generated calls and metadata. No upstream executable is bundled.

After preparing the exact private runtime and a JAR containing this workflow,
run from the project root in PowerShell:

```powershell
$jar = 'build/plink-relatedness-development-1/build-6/omicsmatic-0.4.0.jar'
python examples/plink-relatedness/make_requests.py `
  --bfile examples/plink-relatedness/synthetic/cohort `
  --runtime-home build/plink-relatedness-development-1/runtime-2 `
  --out build/plink-relatedness-example-NEW
java -cp $jar org.jlinalg.cli.PlinkRelatednessCli --request build/plink-relatedness-example-NEW/KING_TABLE.json
java -cp $jar org.jlinalg.cli.PlinkRelatednessCli --request build/plink-relatedness-example-NEW/captures/KING_TABLE/verify-request.json
```

Inspect `analysis-status.json`, `WARNINGS.md`, `pairs.tsv` and the original
`worker/result.kin0`. The request generator also writes independent matrix,
cutoff and GENOME requests and an explicit replay request. It runs nothing.
Choose the operation/settings that answer your question; preparing a JSON
request does not establish that its population or frequency model is suitable.

Every output path must be new. To repeat, choose another `--out`; do not edit
or overwrite a retained capture. Java VERIFY is portable and needs no PLINK
runtime. RUN and REPLAY use the exact supported Windows x86-64 profile.
