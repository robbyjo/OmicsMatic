# Original scFates CPU coordinate workflows

This development kit binds scFates1.2.5 and SimplePPT1.1.7 and 50 exact official
dependency archives. It executes tree fitting, root selection and pseudotime
on explicit float64 coordinates. It does not infer preprocessing, UMAP,
diffusion coordinates, package editions or unreported paper parameters.

Export with `java -cp DEVELOPMENT.jar org.jlinalg.cli.ScfatesSetupCli --export NEW_DIRECTORY`.
Export installs nothing. Original execution requires Linux x86_64 Java, including
WSL. The native SimplePPT arithmetic API remains portable and needs no Python.

Prepare the exact read-only project Python3.11.17 base with the separate
`org.jlinalg.cli.CellPhoneDbSetupCli` kit if necessary. No CellPhoneDB analysis is
run. Use case-sensitive storage for the new scFates prefix.

```sh
python3 -I -B setup/prepare.py \
  --out /absolute/NEW_SETUP_RECEIPTS \
  --prefix /absolute/CASE_SENSITIVE/NEW_SCFATES_RUNTIME \
  --base-runtime /absolute/BASE_SETUP/COMPLETED.json \
  --archive-cache /absolute/EXACT_ARCHIVE_CACHE \
  --allow-install
```

Repeat `--archive-cache` as needed. Add `--allow-download` only where download
is permitted and exact archives are missing. Setup resolves no new versions.
It checks original sources/licenses, full namespace health and both complete
runtime inventories. Read `COMPLETED.json`, `WARNINGS.md` and full logs.

**SKIP3: skip dependent analysis** if dependencies, platform or permissions are
unavailable. Obtain compatible permitted dependencies and retry NEW paths, or
retain the skip. **FAIL2: admit no batch or partial case.** Preserve all original
inputs, failed directories and logs, correct the named cause and retry NEW
paths. Never refresh old scientific binding hashes. RUN never installs.

Run `org.jlinalg.cli.ScfatesWorkflowCli --request request.json` with an explicit
request. Require exit0 and `analysis-status.json result_admitted=true` together.
Read `WARNINGS.md` and `native-audit.json` before use. Source failures, including
milestone-color IndexError after provisional pseudotime, reject the entire batch.
Do not invent colors, normalize failed assignments or admit earlier cases.

Declare policies for not-converged fitting, nonfinite diagnostic scores,
zero-mass assignments, removed nodes and missing milestones. A retain-with-warning
policy preserves original values; it cannot repair an original exception.
Root distances use weighted edges. Original cell projection broadcasts one
scalar weight to all graph edges and can select the first tied neighbor; the
wrapper preserves this convention. Probabilistic mappings and random choices
execute upstream. Numerical checks do not independently refit a trajectory.
Optional R/GAM, GPU and other scFates workflows are outside this CPU profile.
Supervision bounds owned processes, wall time and sampled output size; it does
not impose hard OS memory/CPU containment. No whole-paper or biological
reproduction is claimed.
