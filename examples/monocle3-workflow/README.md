# Synthetic original Monocle3 workflow

This example contains seeded **synthetic** raw counts for 80 genes by 160 cells, a numeric `progress` covariate and a two-level `batch` factor. It runs original Monocle3 1.4.27 preprocessing, UMAP, Leiden clustering, graph learning, pseudotime, quasipoisson gene models and principal-graph Moran tests. It provides software controls, not biological findings or paper reproduction.

**Runtime required:** Windows R 4.6.1 and the exact private dependencies in `src/main/resources/org/jlinalg/omics/monocle3-workflow/SOURCE-PROFILE.json`. The recorded development environment is `build/literature-review/monocle-runtime/reference-setup-3`. Read the [setup history and its retained warnings](../../docs/literature/MONOCLE3_CURRENT_READINESS.md). A normal fresh clone does not include that installed runtime. Provision it only where installation is permitted; otherwise retain SKIP3 and skip the dependent analysis. The helper and analysis command install nothing. An R 1.3.1 environment is not silently replaced with 1.4.27.

From the repository root, with a completed private setup and JDK available, build the separate development artifact into a NEW directory:

```powershell
python tools/literature/build_monocle_workflow.py --out build/literature-review/monocle-runtime/workflow-build-NEW
python examples/monocle3-workflow/make_request.py --setup build/literature-review/monocle-runtime/reference-setup-3 --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --request build/literature-review/monocle-runtime/example-NEW/request.json --out build/literature-review/monocle-runtime/example-NEW/result
java -Xmx1g -cp build/literature-review/monocle-runtime/workflow-build-NEW/omicsmatic-0.3.7-monocle3-workflow-development.jar org.jlinalg.cli.Monocle3WorkflowCli --request build/literature-review/monocle-runtime/example-NEW/request.json
```

The build also needs the previously recorded `native-build-1` parent development artifact; it refuses an absent or changed parent. It leaves the canonical/released JAR untouched. Use the project Python/JDK paths if `python` or `java` is not on PATH. Replace each `NEW` consistently with an unused name. Keep outer JAR/request paths ASCII on Windows. The generated JSON contains all effective source options and actual private R library paths; inspect it before adapting an analysis.

Require **exit 0 and `result/analysis-status.json` with `result_admitted: true`**. Expected synthetic controls: 160 cells with finite pseudotime, 80 genes, 240 coefficient rows and 80 graph-test rows, with all original statuses `OK`. The Java audit checks the source's actual roots and prepared-graph distances. Complete `cds/` directories are reloaded after publication during independent validation; retain all index files. Do not copy a lone RDS and claim a resumable saved state.

**Read `result/WARNINGS.md` even on success.** Original model fitting retains the `subbars` relocation warning. Gene failures, invalid input, timeouts and capacity failures reject the whole batch with FAIL2, keeping original diagnostics under `worker/`. Missing/unsupported runtime profiles give SKIP3. Gaussian fits currently return original `FAIL` statuses in this profile and are not admitted. Preserve every failed attempt and retry into NEW paths; never remove genes or replace `NA`/`Inf` to force success.

The [full request and result guide](../../docs/literature/MONOCLE3_WORKFLOW_DEVELOPMENT.md) documents exact input order, literal identifiers, factor levels, per-value kinds, complete original objects, model adjustments, unsupported paper roles and recovery actions. In particular, coefficient q-values retain original Holm adjustment; stock Moran tests do not implement a paper's custom covariate model. There is no portable VERIFY command or new root CLI route in this checkpoint.
