# Root Monocle tools examples

These examples use synthetic data and the separate root development JAR. They install nothing and leave the canonical/released JAR unchanged. Start from the repository root with the recorded validated parent workflow artifact available:

```powershell
python tools/literature/build_monocle_root.py --out build/literature-review/monocle-runtime/root-build-NEW
java -jar build/literature-review/monocle-runtime/root-build-NEW/omicsmatic-0.3.7-monocle-root-development.jar monocle-tools --help
```

Choose a NEW build directory. Use the project Python/JDK paths if needed. The build verifies its parent, independently recompiles the exact inverse root source, and changes only routing/help plus the new native ORDER facade. It does not rebuild unrelated local Study code.

## Native ORDER, with no R runtime

Copy `order.template.json` to a NEW work directory and copy `prepared-graph.json` beside it. Rename the request to `order.json`. The request's `out` must not already exist:

```powershell
java -jar build/literature-review/monocle-runtime/root-build-NEW/omicsmatic-0.3.7-monocle-root-development.jar monocle-tools ORDER --request PATH_TO_NEW_WORK_DIRECTORY/order.json
```

The supplied principal point `(2.1, 0)` selects cell `b` at `(4, 0)` rather than cell `a` at `(0, 0)`. The undirected edges have weights 3 and 5, so expected cell pseudotimes in input order `[a,b,c]` are **`[3,0,5]`**. They do not include the Euclidean principal-point distance. Check `ordered-result/results/selected-roots.json` and the separate ordered cell/graph tables.

Require exit 0 and `ordered-result/analysis-status.json` with `result_admitted: true`. Read `WARNINGS.md` on success. Disconnected cells would retain `Inf` and a warning; an invalid graph or exhausted logical capacity produces FAIL2 with no admitted result. Preserve each attempt and retry NEW paths after correcting the named cause. Do not repair IDs, drop cells or replace `Inf` to force success.

## Original RUN, requiring the private R runtime

Use the existing [raw-count request helper and inputs](../monocle3-workflow/README.md), then pass the generated request to the root development JAR:

```powershell
python examples/monocle3-workflow/make_request.py --setup build/literature-review/monocle-runtime/reference-setup-3 --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --request build/literature-review/monocle-runtime/root-example-NEW/request.json --out build/literature-review/monocle-runtime/root-example-NEW/result
java -Xmx1g -jar build/literature-review/monocle-runtime/root-build-NEW/omicsmatic-0.3.7-monocle-root-development.jar monocle-tools RUN --request build/literature-review/monocle-runtime/root-example-NEW/request.json
```

Expected controls are 160 finite cell pseudotimes, 80 genes, 240 coefficient rows and 80 Moran-test rows. The root RUN command is independently compared with the original complete fitted objects, including relocated CDS directories. It does not imply a historical edition, custom paper test or whole-paper reproduction.

**SKIP3:** if the exact Windows R 4.6.1/private dependency runtime is missing or installation is forbidden, skip dependent RUN analysis. Provision it only where permitted; there is no fallback edition. **FAIL2:** any requested analysis/model/audit failure rejects the whole batch, including completed cases. Gaussian's original failure status remains unadmitted. Inspect original errors/warnings, including `subbars`, retain complete CDS/model objects and use NEW paths after correcting the cause.

The [root guide](../../docs/literature/MONOCLE_TOOLS_DEVELOPMENT.md) describes scope, literal axes, source profiles and recovery. These development interfaces do not yet award Monocle catalog integration credit.
