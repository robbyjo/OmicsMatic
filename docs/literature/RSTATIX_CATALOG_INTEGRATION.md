# rstatix catalog integration

**Original rstatix 0.7.2 is available through a supervised trusted-script workflow.** The entry point is Python source and requires the documented private Linux/WSL R 4.5.3 provider. The original engine handles formulas, tidy grouping and explicit caller arguments; complete result files, original objects, conditions and failures are retained. The separate native Java one-way Levene API/executable needs no R. This admission gives one upstream family credit; it does not count the native helper twice or qualify every rstatix function, author workflow or paper.

The catalog now contains 250 papers, 432 named software families and 2,671 paper-family observations. Individually reviewed uses total 1,148; 1,523 await individual review. New scoped integrations total 108: 20 native Java subsets and 88 upstream workflows, or 111 including three existing adapters. There are 265 missing families. The cohort stays fixed at 71 Nature, 104 Nature Genetics and 75 AJHG papers, 2023-10-23 through 2026-10-23. The full semantic review and implementation objective remain open.

## Run the original workflow

Use permitted Linux/WSL Python, R and Pixi, with NEW directories:

```sh
mkdir -p build/my-new-rstatix-base
cp tools/literature/rstatix072/reference-pixi.toml build/my-new-rstatix-base/pixi.toml
cp tools/literature/rstatix072/reference-pixi.lock build/my-new-rstatix-base/pixi.lock
pixi install --locked --manifest-path build/my-new-rstatix-base/pixi.toml
python3 tools/literature/rstatix072/provider.py --base build/my-new-rstatix-base --allow-download --allow-install --out build/my-new-rstatix-provider
python3 tools/literature/rstatix072_workflow/make_example.py --provider build/my-new-rstatix-provider --out build/my-new-rstatix-example
python3 tools/literature/rstatix072_workflow/run.py --request build/my-new-rstatix-example/request.json
```

Install/download only where permitted. If unavailable or forbidden, preserve SKIP3 and skip the dependent analysis. For an offline provider, supply the exact original archive using `--source-archive path/to/rstatix_0.7.2.tar.gz --allow-install`, omitting `--allow-download`; the locked dependency base must already exist. A READY provider confirms installation/readiness, not an admitted statistical result. Windows original execution skips; use the qualified Linux/WSL provider. No package is installed by analysis.

The example runs seven explicitly chosen operations on 24 synthetic rows: Shapiro, Levene, Kruskal–Wallis, Wilcoxon/BH, Welch two-sample t-test, Pearson correlation and Holm adjustment. These are demonstration choices, not inferred author methods. The [complete workflow guide](RSTATIX072_WORKFLOW_DEVELOPMENT.md) explains every request field, trusted-script inputs/results, literal required exports/packages, final population marker and resource capacities. The entry point is not a standalone JAR, and it does not bundle the original runtime.

Read `analysis/analysis-status.json`, `analysis/WARNINGS.md`, `analysis/diagnostics/conditions.json`, complete condition/run RDS objects, `analysis/MANIFEST.json` and full `analysis/process.stdout`/`process.stderr`. The original returned RDS objects preserve attributes, arguments and formula metadata. Supplementary tables/indexes do not replace R serialization. Original p-value rounding/adjustment remains unchanged; generic native tests are not substituted.

## Use the native Levene method separately

```sh
java -jar docs/literature/rstatix072-development-checkpoint/levene-native-windows21-development.jar --request my-variance-tests.json --out build/my-new-native-levene-results
```

The portable Java executable was qualified on Windows Java 21/25 and Linux Java 21. See [the native/provider guide](RSTATIX072_DEVELOPMENT.md) for a copyable JSON request and build/API instructions. Native Java supplies prepared one-way mean/median-centered Levene/Brown–Forsythe testing with explicit missing-row policy, literal group labels and retained row offsets. It does not evaluate general R formulas, grouped tidy workflows, custom R center functions or the other original tests. The canonical executable and root dispatch remain unchanged.

The native/provider guide retains its earlier unfinished-adapter statement as checkpoint history. The later workflow guide records completion of the supervised original engine, and this catalog records its subsequent admission. Neither frozen component is edited to rewrite historical credit or qualification statements.

## Six source roles and unresolved settings

All 250 exact primary XMLs were searched and their DOI/hash identities checked. Six paper-family uses cover 12 paragraphs. The [individual reviews](../../tools/literature/rstatix-paper-use-reviews.json) retain every named paragraph index/hash/section and literal reported edition. Five old extracted observations gain source summaries while retaining their exact former records. P040's already-reviewed observation/global review remain unchanged; its current workflow availability is displayed separately. P241's resource link is not another execution.

| Paper | Reported edition | Source role and unresolved inputs |
| --- | --- | --- |
| [P032](https://doi.org/10.1038/s41586-024-08314-y) | 0.7.2 | Adjusted p-values for MED12 CAR activation/GSVA scores. The test, correction method and family are unknown; no t/Wilcoxon or Holm/BH choice is inferred. DESeq2 and GSVA remain separate. |
| [P040](https://doi.org/10.1038/s41586-026-10954-1) | 0.7.2 | Two-sided t-tests of IMC cell abundances. Exact grouping, variance assumption and pairing remain unresolved; the example does not establish the author's Welch/equal-variance choice. ggpubr, GeomxTools and GraphPad remain separate. |
| [P054](https://doi.org/10.1038/s41586-025-09794-2) | 0.7.2 | Six paragraphs cover Shapiro/Levene, Pearson correlation, Kruskal–Wallis, pairwise Mann–Whitney with Bonferroni/BH, paired t-tests and one-sided group-versus-all comparisons. The literal printed `wilcoxon_test` differs from exported `wilcox_test`; no silent spelling repair is made. Pairing, families, direction and group-versus-all dependence remain explicit. R 4.2.3 to 4.4.0 differs from the selected reference. |
| [P087](https://doi.org/10.1038/s41588-026-02774-w) | 0.7.2 | `wilcox_test`/BH for ARLD/PBC OLR1 CPM comparisons. Dataset-specific groups/family and exact/tie/correction choices remain unresolved; separate stats Spearman, ggpubr, MiloR and Seurat/MAST roles remain separate. |
| [P098](https://doi.org/10.1038/s41588-026-02745-1) | 0.7.2 | Pairwise `t_test` on VST-normalized expression with Holm adjustment. The primary source explicitly reports the edition despite its omission by the earlier extractor. Variance assumption and comparison family remain inputs; DESeq2 and other signature tests remain separate. |
| [P241](https://doi.org/10.1016/j.ajhg.2024.05.023) | Unreported | Generic statistical analyses and a separate Web resources link. No edition, test, design or family is inferred. Selected 0.7.2 is a caller choice, not a recovered author runtime. |

Selected original R 4.5.3/car 3.1-5 does not prove historical author-R/dependency equivalence. The script engine supplies explicit operations; it does not recover unknown author code, data, comparison families or preprocessing. Original rounding, tie handling and adjustment behavior stay in the selected runtime. No paper is reproduced.

## Warnings, errors and recovery

**SKIP3 skips dependent analysis.** Supply missing permitted source, runtime, provider, input, installation/download, required literal export/package or execution policies, then retry NEW paths. A request for literal `wilcoxon_test` skips before caller analysis because selected 0.7.2 does not export it. Resolve the source/method choice explicitly; do not silently repair the name. Cell Ranger and Cell Ranger ARC remain excluded by user request.

**FAIL2 admits no complete result.** Preserve all complete/partial results, exact inputs, original objects/errors/warnings, raw logs, manifests and status. Correct the named cause and retry a NEW output. Original warnings are not muffled, and `warn=2` remains an error. Early zero-exit quit without complete final diagnostics/marker, missing/extra/truncated outputs, changed inputs, symlinks and capacity violations refuse admission. Do not drop failed cases or label partial populations complete. Review and justify/correct every warning before using results.

Native Levene refuses unresolved within-group deviation variance. Original constant/all-singleton cases returned NaN; a constant-deviation R fit returned a huge finite statistic with an essentially-perfect-fit warning. Both original outcomes and native refusals remain retained, without replacement or relabeling.

Trusted R scripts run with account permissions. This is not an OS sandbox, arbitrary-code scientific validator or total CPU/RSS containment guarantee. Same-group process supervision does not cover children that create separate sessions. Scripts must propagate scientific failures and validate their own expected objects before setting the completion marker. File completion cannot infer nonfinite-value eligibility, designs, biological validity or authenticity. Hashes establish byte integrity, not producer authentication or independent statistical validity.

## Validation

```sh
python tools/literature/rstatix_catalog/verify.py --out build/literature-review/monocle-runtime/my-new-rstatix-catalog-check --rescan-sources
python tools/literature/rstatix_catalog/test_catalog.py
```

The admission checks the exact eight-path predecessor archive, complete current source/evidence pins, unchanged cumulative catalog/scientific gates, all 2,411 workflow and 3,852 native/provider evidence members, and both sets of four frozen-verifier refusals. It performs no new original analysis or installation. Only eight preserved catalog metadata paths may be overlaid for historical replay; scientific sources, inputs/results, profiles and verifier definitions remain live and unchanged. The cumulative registry stays at 470 definitions, with only rstatix coverage fields updated.

The original control has 248 calls, 444 returned rows and 5,664 table cells. Complete objects, attributes, arguments, conditions, caller RNG and all 250 scientific result files matched independent direct original R. Formula execution-environment pointers and opaque RDS byte parity are not claimed. The 31 Linux protocols, Windows skip and public seven-operation example passed. Native Levene separately matched 196 computed cases/784 original scalars per Java platform, with ten refusals, 29 API checks and six CLI refusals. Full warnings, earlier path/export-order/harness failures and all their scientific outputs remain retained.

The frozen component records keep their pre-admission zero-credit/main-catalog-unchanged statements. The later [catalog validation](omics-2023-2026/rstatix-catalog-validation.json) records one admitted upstream workflow and five additional reviews. The overall objective remains active.
