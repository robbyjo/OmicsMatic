# Original ggpubr0.4.0 and0.6.0 workflows

This development component runs the **complete installed original ggpubr namespace** through a trusted caller R script. The two editions have separate private providers. Their shared reference profile is R4.3.3, original ggplot2 3.4.3 and original rstatix0.7.2. The entry point is Python source; this component does not replace the canonical OmicsMatic executable or provide a Java reimplementation of ggpubr.

**A READY provider is not an analysis result.** An admitted run establishes completion of the caller's declared files and population marker under the stated controls. It does not independently validate arbitrary R code, the study design, every biological conclusion or a paper's historical runtime. The development checkpoint adds **zero catalog credit**; the cumulative catalog remains a separate admission step.

## Warnings, errors and recovery

| Outcome | Meaning | Required action |
| --- | --- | --- |
| SKIP3 | A required runtime, source, input, literal export/package, installation, download or execution permission is unavailable or forbidden. Dependent analysis is skipped. | Install only where permitted, supply the exact version and prerequisites, and retry **NEW** provider/output paths. Do not substitute another edition or export spelling. |
| FAIL2 | The request, protected source, original execution, population marker, file inventory, statistical layer or output completeness failed. | Keep all raw inputs, original objects, conditions, process logs, status and partial results. Correct the named cause and retry NEW paths. Failed/partial results are not complete analyses. |
| Exit0 with warnings | All declared files and the marker returned, but original warnings require review. | Read `WARNINGS.md`, `diagnostics/conditions.json`, complete condition RDS and raw stderr before interpreting results. Ordinary warnings can indicate removed rows or unsuitable data. |
| Incomplete worker diagnostics | R ended or was stopped before a complete status record was written. Condition totals can be unavailable. | Read the unmodified raw status, `WORKER_DIAGNOSTIC_ERROR.txt` when present, all logs and partial objects. The adapter writes an unadmitted final status and attempts a complete retained-file inventory. Unknown condition counts are **not zero**. |

Known original ggplot **`Computation failed in ...` warnings refuse admission**, even if the original renderer continues and produces files. The original condition object, including its parent error, is retained. Original `options(warn=2)` behavior remains an error; warnings are not muffled or rewritten to force success.

Some original functions return an empty layer silently. For example, constant x values can make `stat_cor` return no correlation row. **The caller must check its expected statistical layer rows and scientific eligibility.** The generic controller cannot infer a universal eligibility rule from a blank layer. A deliberately declared empty-layer protocol return is distinct from a scientifically complete correlation analysis.

Plot grouping is also explicit: coloring scatter points does not by itself establish a grouping for `stat_cor`. Use an explicit layer mapping such as `mapping=ggplot2::aes(color=group)` when that is the intended analysis. Pairing order, comparison families, direction, variance assumptions, label positions and plot eligibility remain caller decisions. `compare_means`, the global annotation path and the comparisons/ggsignif path retain their original behavior; do not assume one universal multiplicity correction.

## Provision a private Linux or WSL provider

The qualified profile is Linux/WSL. Native Windows execution of this R workflow is skipped. Use a **persistent permitted Linux directory** for the dependency base and providers; temporary WSL directories can disappear between invocations. The validation installation used a private `/opt` location, but a persistent user-owned directory needs no system-wide installation.

From a Linux/WSL checkout, with an installed permitted Pixi executable:

```bash
PRIVATE="$HOME/.local/share/omicsmatic/ggpubr-reference-1"
mkdir -p "$PRIVATE/runtime"
cp tools/literature/ggpubr/reference-pixi.toml "$PRIVATE/runtime/pixi.toml"
cp tools/literature/ggpubr/reference-pixi.lock "$PRIVATE/runtime/pixi.lock"
pixi install --locked --manifest-path "$PRIVATE/runtime/pixi.toml"

python3 tools/literature/ggpubr/provider.py \
  --version 0.4.0 --base "$PRIVATE/runtime" \
  --allow-install --allow-download --out "$PRIVATE/provider-040"
python3 tools/literature/ggpubr/provider.py \
  --version 0.6.0 --base "$PRIVATE/runtime" \
  --allow-install --allow-download --out "$PRIVATE/provider-060"
```

If Pixi, downloads or installation are unavailable/forbidden, warn and skip dependent analysis. Do not continue from a failed install. To avoid network access, supply `--source-cache /absolute/permitted/cache` containing the exact selected ggpubr archive, `ggplot2_3.4.3.tar.gz` and `rstatix_0.7.2.tar.gz`; the provider checks their complete hashes and every source member. Omit `--allow-download` in that case. A source archive alone is not an installed runtime.

Providers must be outside the protected dependency base, source and archive-cache directories. The provider installs unchanged original sources into its own library, checks every declared export and all directly defined source function bodies and ggproto methods, records actual fonts and package versions, and pins the selected R package files, R entry points and lock. These checks do not make the operating system hermetic. Keep providers unchanged; use NEW paths for a different edition, dependency profile or setup attempt.

The original source archives and their licenses are retained with the evidence. ggpubr0.4.0 declares GPL-2; ggpubr0.6.0 declares GPL >=2. Project controller code is GPL-3.0-or-later. The shared private R dependency base and a standalone original runtime are not redistributed in this component; setup diagnostics and complete installed-file identities are retained.

## Run the complete synthetic example

```bash
python3 tools/literature/ggpubr_workflow/make_example.py \
  --version 0.4.0 --provider "$PRIVATE/provider-040" \
  --out build/literature-review/monocle-runtime/NEW-ggpubr040-example
python3 tools/literature/ggpubr_workflow/run.py \
  --request build/literature-review/monocle-runtime/NEW-ggpubr040-example/request.json
```

Repeat with literal `0.6.0`, its matching provider and another NEW output. The example explicitly declares all24 synthetic subjects, an unpaired two-sided Welch t comparison, a pooled Pearson correlation, three original plots, complete original object/layer RDS, a full statistics table and SVG/PNG exports. It checks the expected statistical layer population. It also retains the original PDF device output used while constructing layouts. This is synthetic validation data, not recovered author data or a manuscript reproduction.

For a real trusted script, inspect the generated request and declare every expected result filename and minimum byte length, all input files, provenance, complete analysis population, literal exports, additional required package versions and exact final marker. The script receives `input_directory`, `result_directory`, `input_provenance` and `analysis_population`. Set `analysis_complete` only after the complete declared population succeeds. Keep incidental device files within declared results. R code runs with account permissions; this is **not an OS security sandbox**, arbitrary-code auditor or total CPU/RSS containment guarantee. The controller supervises the owned process group and declared workspace limits; independently created process sessions are outside that boundary.

## Scientific controls and limits

The qualification matrix contains79 complete cases per edition:25 original `compare_means` tables and54 original plots. It covers paired/unpaired t and Wilcoxon paths, alternatives, equal-variance t tests, omnibus tests, reference groups, multiple responses, grouped comparisons, all-pair annotations, Pearson/Spearman/Kendall correlation, facets and explicit group mappings, violin plots, a log scale, horizontal coordinates and `ggarrange`.

Each edition retains294 complete result files. Comparison against direct original R includes all416 returned table cells,171084 layer cells, labels, panel/layout surfaces, original calls, condition records and RNG states, plus exact comparison of all134 SVG/PNG/TSV files. The full original plot, built and grob objects remain unchanged in RDS. Opaque ggproto environment-pointer identity and whole RDS byte parity are not claimed. The complete construction PDF is retained; its clock metadata is not standardized or included in the SVG/PNG/TSV byte-comparison claim.

Failure controls include the original renderer returning exit0 with failed-statistical-layer warnings, caller rejection of a silent empty correlation layer, an explicitly declared empty-layer boundary, truncated worker status, zero-exit early termination, source/input changes, missing or extra output, forbidden policies, unsupported editions, missing packages, symlinks and owned-worker limits. Installation controls distinguish missing/forbidden prerequisites from contradictory source/lock/version data. Every prior failed harness, setup, adapter and partial-output attempt is retained and labelled separately.

The fixed primary cohort has250 papers. All exact XML/DOI identities and12 named ggpubr paragraphs are audited, with nine individual role readings:

| Paper | Reported edition | Source-bound role and unresolved choices |
| --- | --- | --- |
| P040 | 0.6.0 | `stat_compare_means` for signed-rank Wilcoxon and unpaired t tests; `stat_cor` for Pearson correlation. Author groups, pairing order, variance and correction family remain inputs. |
| P054 | 0.6.0 | ggpubr among plotting packages; no individual ggpubr function, figure, annotation or layout is assigned. |
| P058 | Unreported | Wilcoxon comparisons in three named image-derived measurement contexts; exact export, pairing and families remain unresolved. |
| P087 | Unreported | `stat_compare_means` for unpaired two-sample t comparisons of per-patient cell abundance; variance and families are unreported. |
| P098 | 0.4.0 | Violin plots for promoter-mark enrichment across PDX subgroups. The nearby Wilcoxon statement does not identify its implementing package. |
| P101 | 0.4.0 | General plotting/statistics package listing. Tests explicitly assigned to base R or agricolae are not reassigned to ggpubr. |
| P195 | 0.4.0 | General visualization library; nearby Mann-Whitney testing is not specifically assigned to ggpubr. |
| P231 | Unreported | Visualization/manipulation package listing; no exact operation or statistical test is inferred. |
| P241 | Unreported | Plots generated with ggplot2 or ggpubr, without per-figure assignments; the web-resource entry is not another analysis run. |

Other editions, unreported author choices, author data, historical R/dependency builds, other operating systems and whole-paper biological reproduction remain unresolved. The full250-paper semantic-review and implementation objective remains open.

## Inspect and verify evidence

Read `docs/literature/ggpubr-workflow-development/VALIDATION.json`, `CONTROLS_LAYOUT.json`, retained failure history and `SHA256SUMS`. The verifier checks saved evidence and current sources; it runs no new original analysis or installation:

```bash
python3 tools/literature/ggpubr_workflow/verify.py \
  --out build/literature-review/monocle-runtime/NEW-ggpubr-evidence-check \
  --rescan-sources
```

When opening a full original RDS, first use the matching provider library and R version. Do not let R silently replace unavailable namespaces while reading original objects. The evidence bundle preserves replay inputs and original outputs, but a permitted matching private runtime is still required to execute an analysis again. Integrity hashes establish recorded-byte consistency, not signatures or proof of every scientific conclusion.
