# Artificial SCENIC workflow example

These 96 cells, 104 genes, four TFs, 64 motif rankings and motif annotations are
**entirely artificial**. They test the workflow and are not reference information
for any organism or evidence of biological regulation. The fixture seed is719;
the request's fitting and scoring seed is17. Do not infer paper settings from it.

Build current source, install/supply the permitted exact Linux runtime described
in [the workflow guide](../../docs/literature/SCENIC_WORKFLOWS.md), then replace
`python` in `run.json` with that runtime's **Linux** executable path. Use Linux
Java21, including inside WSL when working from Windows:

```bash
java -Xmx4g -jar /path/to/omicsmatic-0.4.0.jar scenic-tools --request examples/scenic-workflow/run.json
java -Xmx4g -jar /path/to/the-same-exact.jar scenic-tools --request examples/scenic-workflow/verify.json
java -Xmx4g -jar /path/to/the-same-exact.jar scenic-tools --request examples/scenic-workflow/replay.json
```

Paths resolve from each request file. The example creates `run`, `verify-witness`
and `replay` here; each must be new. It leaves earlier output intact. The latter
two commands perform fresh original fits/pruning and native scoring, compare the
complete scientific exports and retain the parent read-only. Running from another
working directory is supported when the request path is explicit.

Read `WARNINGS.md`, `analysis-status.json`, both target/module ledgers and activity
eligibility flags. The output is a numerical demonstration. **SKIP3** names an
unavailable/forbidden prerequisite; supply it or retain the dependent analysis as
skipped. **FAIL2** admits no complete result; retain original/raw/partial/failed
output, resolve its named cause and retry new paths. Never change saved hashes,
IDs or scientific values to make a check pass. `--inspect` reads saved closure;
full VERIFY repeats the scientific computation. GitHub does not contain the
installed private runtime or an analysis's exact executable JAR.
