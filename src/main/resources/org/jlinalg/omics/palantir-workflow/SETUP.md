# Palantir original coordinate workflows

Export this complete kit with `java -cp DEVELOPMENT.jar org.jlinalg.cli.PalantirSetupCli --export NEW_DIRECTORY`. Export installs nothing. The adapter runs only through Linux x86_64 Java (including WSL); the native multiscale Java API remains portable.

The kit binds original Palantir 1.4.4 and 1.4.6 and 48 exact dependency archives. Original wheels and MIT notices are retained. These editions have distinct random-generator and pseudotime-weight behavior. Neither is inferred to be an unreported paper version. Inputs are explicit float64 PCA or multiscale coordinates, not raw expression, inferred normalization or an inferred PCA recipe.

Prepare the project's exact Python3.11.17 base using the separate `org.jlinalg.cli.CellPhoneDbSetupCli` export kit and its instructions if it is not already available. This is a Python runtime dependency; no CellPhoneDB analysis is executed. Palantir setup reads that completed base and its full inventory without installing into it. Its new Palantir prefix must use case-sensitive storage (WSL ext4 or a separately enabled case-sensitive directory).

From this exported kit on Linux, run:

```sh
python3 -I -B setup/prepare.py \
  --out /absolute/NEW_SETUP_RECEIPTS \
  --prefix /absolute/CASE_SENSITIVE/NEW_PALANTIR_RUNTIME \
  --base-runtime /absolute/BASE_SETUP/COMPLETED.json \
  --archive-cache /absolute/EXACT_ARCHIVE_CACHE \
  --allow-install
```

Repeat `--archive-cache` for other caches. Add `--allow-download` only when downloading missing hash-pinned archives is permitted. No dependency versions are silently resolved or substituted. Setup creates new directories, verifies every original package member except separately retained installer-generated RECORD metadata, imports the complete namespaces, and checks complete base/runtime inventories before and after. Read setup `WARNINGS.md`, `COMPLETED.json` and all logs.

**SKIP3 means dependency analysis was skipped.** When installation, downloads or required dependencies are forbidden or unavailable, retain the warning and skip dependent analysis. Obtain permitted dependencies or a permitted environment and retry new paths. **FAIL2 admits no analysis or partial batch.** Preserve all originals, provisionals and logs, resolve the named cause, then retry new paths. Never refresh an old scientific binding hash. Analysis RUN never downloads or installs.

Execute an explicit request with `java -cp DEVELOPMENT.jar org.jlinalg.cli.PalantirWorkflowCli --request request.json`. Require exit0 **and** `analysis-status.json` with `result_admitted=true`. Read analysis `WARNINGS.md` and `native-audit.json` before using results. Original fitting remains upstream; native audits check observed equations and exports, not an independent refit. Original probabilities below0.01 are clipped after entropy computation, without row renormalization. Empty fate families require an explicit policy and are never invented. This kit does not establish biological or whole-paper reproduction. Supervision covers owned processes, sampled output limits and wall timeout, not hard OS CPU/memory containment.
