# Provisional GSEA source-role audit: resume here

**These are research findings, not admitted reviews or new integration credit.**
All 50 frozen lexical candidate contexts were read. Full primary paragraph text
stays in the private source/provider exports identified in the
[current handover](../HANDOVER_2026-11-20_GSEA_GLOBAL.md). Exact public anchors,
DOIs and PMCIDs are in
[CANDIDATE-ANCHORS.json](../gsea-handover-20261120/CANDIDATE-ANCHORS.json).
Use those identities and frozen raw XML; preserve source errors/unknowns.

The [earlier 19-role notes](../gsea-handover-20261120/SOURCE-NOTES.md) remain
useful, with these follow-up corrections/additions:

- P062 explicitly attributes its GSEA operation to GSEApy 1.1.8 after Scanpy.
  That is a different provider, not evidence for standalone Broad execution.
- P098 has distinct roles. Paragraph 39 attributes ChEA regulator-target
  hypergeometric analysis to GSEApy 1.1.3. Paragraphs 16/54 separately report
  RNA perturbation GSEA; its caption describes two-sided 1,000-permutation
  results, NES above 1.5 and BH-adjusted P below 0.05. Reanchor the actual
  ranked role without assigning it the other operation's provider/version.
- P051 paragraph 120 is actual tumour-core GSEA/ORA; paragraph 84 largely
  concerns resources and AddModuleScore. Prefer the execution context.
- P040's 4.3.3 belongs to clusterProfiler, not Broad. P037's literal fgsea
  `v3.14` label remains unresolved. P117 citation digits are not versions.
- P077 names Broad 4.3.2 for cluster identity when author labels are absent.
  Nearby MAGMA 1.09b/GTEx settings do not become GSEA options. The exact named
  supplementary PDF download failed DNS; it has not been read.
- P220's frozen final article PMC12696503 names Broad 4.3.3, DESeq2 rlog and
  10,000 permutations with MSigDB 2023.2.Hs collections. Null type/ranking
  metric remain unknown. Do not import another role from same-title preprint
  PMC11451655. Positive NES denotes empty Cas9; negative denotes knockdown or
  wild-type hWAT.

## P096 author source recovered

[The author's Zenodo record](https://zenodo.org/records/13321265), linked by
[the frozen primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631765/),
was retrieved through its official API. The record reports CC BY 4.0. Saved
HTTP receipts include byte length/SHA-256; each script also matches the record's
published MD5. Both were read only, never executed; do not submit the shell
script or send its embedded scheduler notifications.

Script 20 invokes GSEAPreranked with classic scoring, meandiv normalization,
1,000 permutations, size bounds 15-200, No_Collapse and a timestamp seed.
Its GMT filename identifies the 2023.1.Hs GO BP collection. It does not establish
the GSEA executable edition or the historical random seed. Its ranking comment
is generic; recover the actual RNK-generation script (try Script 18) before
resolving the paper's more specific adjusted-P/ranking wording. Do not floor
zero adjusted P or infer missing settings. Script 19 performs a separate
hypergeometric comparison and is not the RNK generator.

## All 31 additional lexical candidates

| Paper | Paragraphs/context | Provisional source-role disposition |
| --- | --- | --- |
| P008 | 84 | EnrichR selected upregulated genes and Hallmark 2020: ORA, not ranked Broad GSEA. |
| P009 | 27; full methods 70/71/110/118/119 | topGO 2.54.0 Fisher tests and GSEApy 1.0.4 enrichr on selected tissue DEGs, with stated backgrounds. The GSEA result label does not establish ranked execution. |
| P010 | 6 | ssGSEA for peripheral T-cell pathways; single-sample method. |
| P019 | 9/46 | Selected TWAS genes versus 1,949 OMIM genes; one-sided Fisher/FDR. |
| P024 | 26/106 | GWAS gene-set roles and Zenith neural-network/PLS prediction enrichment; other algorithms. |
| P032 | 42 | clusterProfiler 4.10.1/msigdbr 7.5.1 generic enrichment wording; ranking/function unresolved. Do not infer GSEA from package capability. |
| P034 | 42 | Single-sample signature scoring and GDF15 correlations; Broad/Firebrowse denotes a provider/resource. |
| P035 | 47 | MAGMA competitive regression for cell types, European 1000 Genomes and 35 kb upstream/10 kb downstream mapping. |
| P039 | 16/80 | EnrichR 3.1 GO 2021 on selected DEGs. Preserve the source's literal direction/threshold wording. |
| P042 | 30 | Actual GSEA result for HRD tumour pathways; candidate generic ranked role. Executor, inputs and exact options remain unresolved. |
| P052 | 43 | gProfiler2 defaults/build and selected DE genes/proteins; do not normalize the source's unusual minus-log-q threshold wording. |
| P069 | 40/56/62 | Explicit clusterProfiler 4.2.1 preranked mean IL18-minus-PBS expression, NK GO BP and FDR below 0.1; collection mentions separate. |
| P079 | 19/21/51 | GSEApy selected oncogene amplifications/combined scores; exact operation needs Supplementary Note 34. No Broad/ranked assignment yet. |
| P082 | 63/70 | MSigDB resources only at these anchors. |
| P088 | 12/35/59/70 | FUMA 1.6.1 Gene2Func and gProfiler (20 September 2023), selected 63 genes/Ensembl 102 background and stated one-sided correction; not Broad ranked execution. |
| P089 | 38 | clusterProfiler 4.4.4 generic enrichment wording; no resolved ranking/function. Keep ambiguous. |
| P094 | 14 | Congenital-disorder overlap/enrichment; Supplementary Note 4 needed. No established Broad/ranked operation. |
| P105 | 59/64 | MAGMA 1.08/FUMA 1.3.8, MSigDB 7.0, ancestry-specific 1000 Genomes backgrounds and IVW meta-analysis; distinct GWAS algorithm. |
| P121 | 15 | EnrichR selected signatures: ORA. |
| P130 | 56 | MAGMA 1.10, FUMA/FAVOR, GO sets and Bonferroni; distinct algorithm. |
| P133 | 25 | Explicit fgsea preranking by DESeq2 Wald statistics with Hallmark. The source's citation DOI points to GSEABase; preserve that discrepancy. |
| P135 | 36 | FUMA SNP2GENE/PASCAL gene-set analysis; distinct algorithm. |
| P145 | 64/71; full methods 51/52 | MSigDB resources plus base::phyper hypergeometric tests; UCell maturation is a separate scoring role. |
| P151 | 29/72 | MAGeCK pathway 0.5.9.5, 8,149-gene score ranks and 100,000 permutations; candidate generic ranked role with another provider. |
| P163 | 18/69 | Explicit fgsea using direction times minus-log adjusted P and MSigDB 7.4 collections/Hallmark; Enrichr is separate. |
| P165 | 8/52 | Actual fetal-repair GSEA, bulk DESeq2 descending log2FC ranks; executor unknown. |
| P170 | 7; full methods 38 | fgsea on perturbation-correlation ranks, sizes 15-500, 100,000 permutations, Hallmark/C7 sets. Provider resolved in a nonmatching fgsea sentence. |
| P213 | 17/36 | R phyper hypergeometric tests and p.adjust; not ranked GSEA. |
| P228 | 12/30 | fgsea on all expressed-gene t-statistics; enrichR ORA and rrvgo are separate roles. |
| P231 | 7 | Reactome 2024.1 Fisher ORA/BH. |
| P236 | 12 | Speos core-gene overlap using hypergeometric/FDR. |

Eight entries are promising additional generic ranked-GSEA candidates: P042,
P069, P133, P151, P163, P165, P170 and P228. **Eight is a working hypothesis,
not an admitted count.** Unknowns, different providers and result-only evidence
must remain explicit. P032/P089/P094 must not be promoted by inference. Decide
the catalog's generic-method versus provider-specific policy explicitly before
adding observations. Preserve all 19 old observation identities and the 1,520
already admitted reviews; source findings do not authorize silent deletion.
