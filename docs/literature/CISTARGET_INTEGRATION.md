# Native cisTarget integration

`cistarget-tools` provides native Java motif enrichment and exact leading targets from complete ranked-feature inputs, with optional lossless Feather-to-TSV conversion. This integration adds one scoped native tool to the fixed 250-paper catalog: **93 new integrations, 96 including existing adapters, and 248 missing tools**. The full implementation and paper-wide completeness review remain in progress.

This is the current entry point and catalog guide. [The development guide](CISTARGET_DEVELOPMENT.md) remains a frozen earlier checkpoint: its opening catalog-pending count is historical. Its numerical profiles, formats, example and failure instructions still apply.

## Run the main command

From the repository root, use Java 17 or later with the published root supplement and recorded project dependency:

```powershell
$cisTargetRootCp = 'docs/literature/cistarget-catalog-development/cistarget-root-javac21.jar;build/cli/omicsmatic-0.3.7.jar'
java -cp $cisTargetRootCp org.jlinalg.cli.JLinAlgCli cistarget-tools --help
java -cp $cisTargetRootCp org.jlinalg.cli.JLinAlgCli cistarget-tools `
  --settings examples/cistarget-ranked-features/settings.json `
  --rankings examples/cistarget-ranked-features/rankings.tsv `
  --signature examples/cistarget-ranked-features/signature.tsv `
  --annotations examples/cistarget-ranked-features/annotations.tsv `
  --out build/cistarget-root-example-1
```

Choose a new output directory whose parent already exists. On Linux/macOS, use `:` as the classpath separator and shell-appropriate line continuation. The canonical JAR is unchanged and does not supply the new route by itself. The standalone `CisTargetWorkflowCli` remains available in the development artifact.

The example is a numerical toy. Its four-motif background and permissive NES threshold are not biological analysis defaults. Native TSV analysis installs nothing and requires no Python, R, original cisTarget package or pycisTarget wrapper.

## Match the source role and numerical profile

The two explicit profiles are `CTXCORE_0_2_0`, with tested NumPy 1.26.4 reduction order, and `RCISTARGET_1_29_0_ICISTARGET`, with exact R-style leading edges. Their AUC integration cutoffs, coordinate domains and SD conventions differ. Supply the intended profile and all settings explicitly; no paper edition or rank-base conversion is inferred.

The complete motif collection defines the NES and recovery background. Apply motif-selection thresholds after calculation. Supply already prepared ranking data and an organism/build/collection-compatible signature. TF annotations are exact supplied joins, without orthology or motif-similarity inference. DNA/Cluster-Buster scoring, interval overlap, DMR selection, expression/methylation postfilters, SCENIC/SCENIC+ network fitting, AUCell and approximate R leading edges remain separate.

| Paper | Reviewed cisTarget role | Separate requirements |
| --- | --- | --- |
| [P047](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719087/) | Recovery-curve motif enrichment in cluster hypo-DMRs; strict AUC > 0.01 and NES > 3 selection | Dual methylation-quantile DMR filters and TF expression/mCH postfilters; the AUC selection threshold does not specify its integration parameter |
| [P058](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719099/) | SCENIC `ctx` with NES threshold 2.5 and minimum 10 genes; mouse v9 annotation resources | `grn`, regulon pruning, AUCell and cluster scaling; no standalone cisTarget edition inferred |
| [P061](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719113/) | Separate positive/negative TF-correlated DMR motif tests, NES > 3 and leading-edge assignment | Cluster-Buster scoring and TF–DMR edge construction; explicit source-profile choice |
| [P065](https://pmc.ncbi.nlm.nih.gov/articles/PMC10830415/) | cisTarget motif collection used by Tomtom to annotate TF-Modisco patterns | Motif comparison and annotation transfer; this is resource use rather than a cisTarget enrichment execution |
| [P094](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649600/) | Human ranking/score databases and motif resources supplied to SCENIC+ | Metacells, pycisTopic, RNA preprocessing, enhancer–gene linking and eGRN fitting; score databases are not ranking inputs |
| [P100](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695644/) | v9 human annotations and hg38 RefSeq-r80 mc9nr rankings supplied to pySCENIC 0.12.1 | GRNBoost2, dropout-masked correlation, regulon pruning, AUCell/RSS and downstream tests/network drawing |
| [P145](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553309/) | The Methods spelling “cis-target” identifies hg38 v10 rankings and motifs-v10nr resources used by pySCENIC 0.12.1 | GRNBoost2 on 20,000 sampled nuclei, AUCell on the full dataset and condition comparisons; no standalone cisTarget execution inferred |

The catalog now contains **1,018 reviewed roles, 356 supporting pointers and 2,466 observations** across the unchanged 250 papers and 400 named tools. P145 adds one missed Methods observation through an explicit spelling extension; no new tool definition is invented. P061 has one explicitly documented review enrichment, with its previous text archived. All 1,011 other prior reviews and every unrelated paper–tool role remain unchanged.

## Warnings and required actions

**Require exit 0, `run.json` status `PASS_NATIVE_CISTARGET_WITH_WARNINGS`, and `enriched-motifs.tsv`.** Read `WARNINGS.md`, every row in `all-motifs.tsv`, and every signature/annotation disposition before interpreting results. Preserve the input hashes and, when converting Feather, the conversion receipt alongside the native receipt.

**NaN NES is undefined enrichment. SOURCE ROUNDING WARNING identifies finite NES caused only by floating-point arithmetic on identical AUCs.** Neither state establishes biological enrichment. Original finite rounding artifacts remain visible; they are not silently converted to a null result or zero.

**FAIL2 admits no result.** Preserve failed partials, correct the named input, numerical, profile or capacity problem, and retry a new directory. Completed outputs are refused rather than overwritten. Do not filter motifs to pass a capacity/normalization check or repair identifiers/rank bases silently.

**SKIP3 during optional Feather conversion means skip that conversion and every dependent analysis.** Where installation is permitted, provide PyArrow 17.0.0 in a new private Python environment using the [development instructions](CISTARGET_DEVELOPMENT.md#convert-a-feather-ranking-database). If installation is forbidden, use an already available complete TSV database or leave the analysis skipped. Conversion never installs software automatically.

The Python reference uses original public ctxcore functions. R validation executes unchanged RcisTarget function definitions under R 4.6.1; the full R package is not installed. The separately licensed pycisTarget wrapper is not installed or executed. These declared references do not establish historical paper-package or whole-paper reproduction. Cell Ranger remains excluded at the user's request.

## Validation

The frozen numerical development evidence retains 672 complete original comparisons, 108 native file/example runs, 60 actual CLI refusals and actual converter skip/failure controls. The main command additionally matches all 108 standalone output populations across two compiler builds and three JVMs, with 12 complete predecessor MR-MEGA flows and 72 dispatch contracts. Every predecessor scientific class and every native cisTarget class remains byte-identical in the root supplement.

The source-role audit checks all 250 XML hashes/DOIs, every 2,466 observation pointer, every 1,018 review and the 356 supporting pointers. Catalog verification also checks unchanged unrelated metadata, earlier scientific gates and browser presentation. Run `python tools/literature/verify_cistarget_catalog.py` to check the [catalog evidence](omics-2023-2026/cistarget-catalog-validation.json). Frozen integrity checks do not execute or independently refit current user data, authenticate execution or certify biological validity.
