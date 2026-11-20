# Provisional GSEA provider/source notes

**Research notes only; no canonical reviews or new positive observations have
been appended.** Re-read exact primary contexts before implementing a role or
admitting a source review. `CANDIDATE-ANCHORS.json` supplies paper DOI/PMCID,
paragraph indices/hashes and matching spans. Full paragraph text remains in
the private `source-census-intake-1/CANDIDATES.json` and exact raw XML cache.
Other software versions in a paragraph are not automatically GSEA versions.

## Nineteen existing observations

| Paper | Primary paragraph(s) | Provider/scope finding to preserve |
| --- | --- | --- |
| P018 | 48 | clusterProfiler 4.10.0 GSEA and separate ORA; DE-selected log2FC ranking. Do not silently replace a selected universe with all genes. |
| P020 | 34, 35, 39 | clusterProfiler 3.18.1 RNA workflows; paired ranking uses signed absolute log10 P. Proteomic WebGestalt ranking is literally log2FC times P; do not change it to minus-log P. |
| P031 | 71 | clusterProfiler 3.14.3, log2FC ranking, KEGG/GO sets; separate Enrichr use. |
| P037 | 45, 50 | MAG statistics and transcript fgsea; literal source version label `v3.14` remains unresolved. Transcript ranking is direction times minus-log10 P, minimum set size 10, q below 0.1. |
| P040 | 33 | clusterProfiler **4.3.3**, not Broad GSEA 4.3.3. Custom immune signatures and BH. |
| P049 | 35, 57 | clusterProfiler GSEA on reference-cell markers; separate neuronal GSEA role with executor unspecified in that paragraph. Other RNA versions are unrelated. |
| P051 | 84, 120 | Paragraph 84 mainly uses GO/GSEA as signature resources plus Seurat AddModuleScore; 120 describes actual GSEA/ORA on tumour cores without an established executor/edition. Prefer actual execution context. |
| P055 | 92 | fgsea 1.20.0, positive one-tailed testing, set size 15-500, expressed protein-coding universe and Fos-correlation rankings. Do not turn into a Broad two-sided test. |
| P062 | 43 | GSEApy 1.1.8; not evidence for the standalone Broad program. |
| P077 | 55 | Explicit Broad GSEA software 4.3.2 for cluster identity where author labels are absent; supplementary methods needed for exact inputs/sets/options. |
| P092 | 48 | clusterProfiler `GSEA` on distance-weighted, L2-normalized peak/topic gene scores. msigdbr 7.5.1 is the collection accessor, not Broad software. |
| P096 | 48, 60 | Broad label literally `2023.1`; cannot map automatically to an executable edition. Inspect code-availability script 20 and Data 15. All DESeq2-tested genes ranked log2FC times minus-log2 adjusted P; zero adjusted P must not be silently floored. Positive NES is paediatric; negative is adult. |
| P098 | 39, 54 | GSEApy 1.1.3 ChEA regulator-target analysis described as a two-tailed hypergeometric test. Do not silently reinterpret as Broad ranked GSEA or one-tailed ORA. |
| P109 | 43 | clusterProfiler 4.0 GO/GSEA; plant-expression analysis and FDR below 0.05. |
| P117 | 52 | cacoa/DOSE/clusterProfiler extended GSEA. Citation digits concatenate with version text; do not incorporate them into package editions. Composition bootstrap count is not GSEA permutation count. |
| P134 | 52, 53 and results | GSEA on metaVIPER protein activity/essential signatures and sequential leading-edge conservation; reprogramming uses a stated two-sided GSEA. Executor/exact algorithm unresolved. |
| P144 | 47 | Spearman/Fisher-Z rankings and separate adjusted OLS heteroplasmy coefficients/P; executor/edition unspecified. Preserve nonzero-cell selection. |
| P197 | 13, 36 | clusterProfiler; R 4.2.2 is not the GSEA software edition. Preserve stated selected-gene thresholds and the source's P versus adjusted-P wording. |
| P220 | 51, 52 | Explicit GSEA **4.3.3 on DESeq2 rlog expression matrices**, always **10,000 permutations**, MSigDB 2023.2.Hs KEGG/Reactome/GO collections. Positive NES = empty Cas9, negative = knockdown or wild-type hWAT. Exact null type/metric not established. |

## Some new lexical candidates already inspected

| Paper | Paragraph(s) | Provisional disposition |
| --- | --- | --- |
| P034 | 42 | Single-sample gene-set scoring; Broad name refers to Firebrowse origin, not established Broad GSEA execution. |
| P042 | 30 | Actual GSEA result label for HRD tumour pathways; inspect exact methods before adding a positive generic role. |
| P069 | 40, 56, 62 | Explicit preranked clusterProfiler 4.2.1 with mean-expression difference ranking, GO BP and q below 0.1. Other matches are MSigDB resource links. |
| P082 | 63, 70 | Resource URLs; Broad execution is not established by these anchors. |
| P145 | 64, 71 | MSigDB collection resources; no standalone Broad execution established. |
| P165 | 8, 52 | Actual fetal-repair signature GSEA with descending DESeq2 log2FC ranks; executor unresolved. |
| P170 | 7 | GSEA result for IFN-alpha perturbation-correlation genes; find exact methods/provider. |
| P228 | 12, 30 | Explicit fgsea preranked all-expressed-gene t-statistics and GSEA result label; separate enrichR ORA/rrvgo. |

The 31 lexical candidates beyond the original 19 are P008, P009, P010, P019,
P024, P032, P034, P035, P039, P042, P052, P069, P079, P082, P088, P089,
P094, P105, P121, P130, P133, P135, P145, P151, P163, P165, P170, P213,
P228, P231 and P236. **Finish semantic classification of every candidate.**
These are candidates, not 31 new Broad uses. Resource-only, alternative
provider, ssGSEA, ORA and background/result-only contexts need distinct
dispositions. Duplicate evidence for an existing role does not add a role.

## Code-reading resume points

Use the exact saved source tags. Re-read the relevant small sections; this
note does not replace the original implementation:

- `xapps/gsea/CLI.java` routes `GSEA` and `GSEAPreranked` through the
  `org/genepattern/modules/GseaWrapper.java` and `GseaPrerankedWrapper.java`
  option/cleanup handlers. These wrappers do not accept `-help`. Check cleanup
  and `run_as_genepattern` handling before using a dedicated cwd.
- `xtools/gsea/Gsea.java` defaults to Collapse; `GseaPreranked.java` defaults
  to Remap_Only/Max_probe. Both defaults can alter prepared identifiers.
  Inspect and explicitly qualify No_Collapse rather than inheriting defaults.
  Preranked execution can uniquize duplicates; reject or explicitly account
  for every disposition instead of silently repairing inputs.
- `edu/mit/broad/genome/alg/gsea/KSTests.java` has expression/phenotype and
  ranked-list entry points, original random-template generation and null ES.
  Preserve metric minimum sample checks, missing/nonfinite behavior, null
  model and original runtime RNG. Collect original complete objects/null
  vectors through an independent API composition without modifying source.
- `KSCore.java` uses float-backed scores/weights with double running-score
  accumulation and grouped misses. Evaluation order matters. Read the full
  max-deviation/sign/leading-edge rules before implementing native primitives.
- `FdrAlgs.java`, `SkewCorrectedFdrStruc` and the normalization/sign helpers
  define pooled positive/negative null tails. Inspect nominal P, normalization,
  zero/sign behavior, empirical FDR and FWER; do not substitute generic BH.
- Inspect original EDB/report serialization and ranking exports. Exit zero
  alone does not establish a complete finite analysis result. Capture complete
  expected artifacts, diagnostics and failed attempts.

The existing project `GseaStatistics` is an fgsea-source component, while
`RankedEnrichment`/`StudyGsea` use their own explicit model/null and BH/BY/NONE
adjustment. They do not establish Broad's pooled-NES FDR or phenotype workflow.
Keep provider/algorithm distinctions in APIs, CLI warnings and catalog scope.
