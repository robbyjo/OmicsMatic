# Native Levene testing and the original rstatix 0.7.2 provider

**Native Java one-way Levene testing is implemented and tested.** The separate original rstatix 0.7.2 package can now be installed privately over a locked Linux/WSL R 4.5.3 dependency base. This is a development checkpoint. The supervised original-package analysis adapter and catalog admission remain unfinished. Do not substitute native Levene testing for the other rstatix tests used by the papers. The main catalog remains at 107 new scoped integrations; this checkpoint earns zero additional catalog credit.

## Run native Levene testing

Use a published standalone development JAR with Java 21/25:

```powershell
java -jar docs/literature/rstatix072-development-checkpoint/levene-native-windows21-development.jar --request my-variance-tests.json --out build/my-new-levene-results
```

The Windows-built controller also contains portable Java bytecode. Linux Java 21 and Windows Java 21/25 were tested independently. The JAR includes the exact existing project JDistlib numerical classes; it needs no R installation or package download to compute native tests. Use a NEW output directory.

Example `my-variance-tests.json`:

```json
{
  "schema_version": 1,
  "cases": [
    {
      "id": "variance-check-α",
      "values": [1, 2, 4, 7, 1, 3, 8, 12],
      "groups": ["control", "control", "control", "control", "treated", "treated", "treated", "treated"],
      "center": "MEDIAN",
      "missing_policy": "FAIL"
    }
  ]
}
```

`MEDIAN` gives the Brown–Forsythe form; `MEAN` gives the original mean-centered Levene form. The native engine applies ordinary one-way ANOVA to absolute within-group deviations. It uses common location/scale normalization to avoid overflow and loss from a large shared offset. JDistlib supplies the F survival probability. This is an independent mathematical implementation, not a translation of the GPL-2-only rstatix/car R code and not a general R formula or linear-model interpreter.

The public Java API is `org.jlinalg.stats.LeveneTest.test(values, groups, center, missingPolicy)`. Input arrays stay unchanged. The immutable result includes statistic, p-value, degrees of freedom, literal group labels, group counts/centers, and every retained/omitted original zero-based row offset. Group order is first occurrence after omission. Singleton groups are allowed when residual degrees of freedom remain positive. Empty labels and the literal string `"NA"` are genuine labels.

Choose `OMIT` explicitly to treat numeric token `"NaN"` or a JSON `null` group as missing. `FAIL` refuses missing rows. `"+Inf"` and `"-Inf"` are always refused, including in an otherwise missing row. The native API does not distinguish an R `NA_real_` payload from Java NaN, accept unused factor levels, evaluate interaction formulas or invoke arbitrary R center functions. Supply a prepared one-way grouping or use the separately declared original workflow when available.

Read `analysis-status.json`, `cases.json`, `WARNINGS.md` and `MANIFEST.json`. An admitted batch has every declared case computed. A failed batch retains successful entries and exact failures but admits no complete batch. The manifest covers request, cases and warnings, excluding itself and the final status. Individual case p-values are not evidence of biological validity.

## Build the native executable

```powershell
python tools/literature/rstatix072/build_native.py --java-bin "C:/path/to/jdk-21/bin" --dependency-jar docs/literature/rstatix072-development-checkpoint/levene-native-windows21-development.jar --out build/my-new-levene-build
java -jar build/my-new-levene-build/levene-native-development.jar --help
```

Replace the compiler path with a permitted JDK 21/25 `bin` directory. The dependency JAR must contain all 933 exact pinned existing project JDistlib classes; a published native development JAR supplies them. The build compiles project code with Java 17 bytecode, `-Xlint:all -Werror`, and packages complete dependency classes. It does not replace the canonical executable or modify root dispatch. The evidence archive includes the complete JDistlib v0.10.2 corresponding source archive and current project source snapshots. No binary/source reproducible-build claim is made.

## Install the original package separately

Use native Linux/WSL Python and a permitted Pixi executable. Create a NEW dependency workspace and copy the exact manifest/lock:

```sh
mkdir -p build/my-new-rstatix-base
cp tools/literature/rstatix072/reference-pixi.toml build/my-new-rstatix-base/pixi.toml
cp tools/literature/rstatix072/reference-pixi.lock build/my-new-rstatix-base/pixi.lock
pixi install --locked --manifest-path build/my-new-rstatix-base/pixi.toml
python3 tools/literature/rstatix072/provider.py --base build/my-new-rstatix-base --allow-download --allow-install --out build/my-new-rstatix-provider
```

These commands require permitted network access and installation. For an offline provider, supply the exact archive with `--source-archive path/to/rstatix_0.7.2.tar.gz --allow-install`, omitting `--allow-download`. The dependency base must already contain the complete locked R 4.5.3 environment. Source hashes and all 166 original files are checked before installation. All 126 exports and 339 directly defined source functions were checked against the installed namespace. A READY record establishes installation/readiness, not an admitted paper analysis. Three installs succeeded: two local-source installs (one readiness refusal retained) and one independent public downloader install; only two providers were admitted READY.

The original package remains GPL-2 and runs separately in R. Its complete unmodified source archive and GPL-2 license are retained. The provider does not copy or translate its implementation into the project's GPL-3 Java library. Dependencies are a selected modern reference profile, including car 3.1-5; this does not establish historical author dependency or R-runtime equivalence. Exact/tie behavior and p-value rounding in other rstatix tests can differ across R/package editions. In particular, rstatix 0.7.2 rounds p-values in several wrappers; Levene's wrapper returns its unrounded car/broom values. General native statistical tests are not substituted for those source-specific wrappers.

## The six source roles

All 250 exact primary XMLs were checked for identity and the rstatix name. Six paper-family uses cover 12 paragraphs. [Individual readings](../../tools/literature/rstatix072/paper-use-intake.json) retain complete indexes, hashes, sections and unresolved settings. This reading intake has not yet changed the main paper records or individual-review count.

| Paper | Reported edition | Source role and unresolved inputs |
| --- | --- | --- |
| [P032](https://doi.org/10.1038/s41586-024-08314-y) | 0.7.2 | Adjusted p-values for MED12 CAR activation/GSVA scores. The test, adjustment method and comparison family remain unidentified. |
| [P040](https://doi.org/10.1038/s41586-026-10954-1) | 0.7.2 | Two-sided t-tests of IMC differential cell abundances. Pairedness, variance assumption and exact design remain unresolved; ggpubr, GeomxTools and GraphPad roles are separate. Existing main-catalog review remains unchanged. |
| [P054](https://doi.org/10.1038/s41586-025-09794-2) | 0.7.2 | Shapiro/Levene checks, Pearson correlation, Kruskal–Wallis and pairwise Mann–Whitney comparisons, Bonferroni/BH adjustment, paired t-tests and one-sided group-versus-all comparisons. Six paragraphs are retained. The literal printed `wilcoxon_test` differs from exported `wilcox_test`; no silent repair or inferred direction/pairing/family is made. Reported R 4.2.3 to 4.4.0 differs from this R 4.5.3 reference. |
| [P087](https://doi.org/10.1038/s41588-026-02774-w) | 0.7.2 | Wilcoxon rank-sum comparisons and BH adjustment for ARLD/PBC OLR1 CPM. Exact comparisons, family and tie/correction options remain inputs; stats Spearman and ggpubr tests are separate. |
| [P098](https://doi.org/10.1038/s41588-026-02745-1) | 0.7.2 | Pairwise `t_test` on VST-normalized expression with Holm adjustment. Variance assumptions and comparison family remain inputs. DESeq2 and separate signature tests are not this native Levene method. |
| [P241](https://doi.org/10.1016/j.ajhg.2024.05.023) | Unreported | Generic statistical analyses. The separate Web resources link is not another execution. No edition, test, design or family is inferred. |

## Warnings, refusals and recovery

**SKIP3 skips dependent analysis/checks** when permitted Java, input, R, download, installation, source, dependency base or execution prerequisites are unavailable. Supply only permitted prerequisites and retry NEW output paths. If installation is forbidden, preserve the warning and skip the affected original analysis. Native Levene itself needs Java and its bundled numerical dependency, not R.

**FAIL2 admits no complete result.** Preserve exact requests, every original returned object/error/warning, all native cases, logs, warnings and status. Correct the named cause and retry a NEW path. Do not discard failed cases or call a partial batch complete. Zero or numerically unresolved within-group deviation variance is refused. Constant/all-singleton original R cases returned NaN. A constant-deviation original R fit returned a huge finite statistic (about 3.42e31) with an essentially-perfect-fit warning; native Java explicitly refuses that unresolved variance. The full original values/conditions and native refusals are retained side by side, without relabeling or repairing either result.

The first provider installed the original package successfully and matched its source functions, then refused admission because the harness compared R's export-name order to Python's sort order. The corrected set comparison preserves R's original order. The failed setup, original namespace results and exact old helper sources remain retained. Source algorithms were not changed.

**Scientific scope:** 196 computed cases on each of Windows Java 21/25 and Linux Java 21 matched all 784 original scalars; maximum absolute F-statistic difference was about 1.42e-14 and p-value difference 1.55e-15. Ten native refusals, 29 public API checks and six CLI refusal cases per platform passed. Cases cover both centers, unequal sizes, ties, Unicode/empty/literal-NA labels, explicit missing rows, singleton groups, offsets, extreme scales and degeneracies. These are complete synthetic controls, not author data or reconstructed paper analyses. Native normalization and refusal semantics are explicit and do not claim literal equivalence to every R QR degeneracy. Hashes establish integrity, not authenticity or independent biological validity.

[Checkpoint validation](rstatix072-development-checkpoint/VALIDATION.json), [complete evidence inventory](rstatix072-development-checkpoint/CONTROLS_LAYOUT.json) and [evidence archive](rstatix072-development-checkpoint/controls.zip) retain the source, inputs, full original objects, returned warnings/errors, native outputs, process logs, setup refusals and earlier failed setup. The selected dependency runtime stays private; its exact manifest/lock and provider inventories remain recorded.

```powershell
python tools/literature/rstatix072/verify.py --out build/literature-review/monocle-runtime/my-new-rstatix-development-check
```

That gate checks saved complete controls/current sources and the unchanged current catalog. It executes no new statistical analysis or installation. Remaining work is the supervised original-package execution adapter, appropriate controls for all paper-used operations, and source-role catalog reconciliation. The canonical executable, all 23 unrelated unfinished files and the main eight catalog files remain unchanged. The full 250-paper objective stays active.
