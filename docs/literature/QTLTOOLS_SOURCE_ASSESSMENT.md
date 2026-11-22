# QTLtools source roles and implementation work

A fresh scan of all 250 frozen primary XML/DOI identities finds nine positive
papers and 19 paragraph pointers. The
[candidate census](qtltools-fdensity-development/QTLTOOLS-CENSUS.json) records
identities and source hashes; full paragraph context remains private. This
assessment adds no canonical observations, review records or family credit.
The catalog remains **145 scoped families, 1,630 reviewed and 1,092 pending uses**.

The first implementation is the
[native Java fdensity API](QTLTOOLS_FDENSITY.md). Association mapping, matching,
quantification, historical editions and a complete execution adapter remain
separate work. The existing generic association pipeline does not establish
QTLtools source equivalence.

| Paper | Reported edition and role | Implementation requirements |
|---|---|---|
| [P073](https://doi.org/10.1038/s41588-024-02019-8) | 1.2 cis-sQTL mapping: grouped intron phenotypes, a 1 Mb cis radius, 1,000 permutations and conditional forward/backward passes. Also reports trans-sQTL mapping with 50,000 permutations. | Preserve the reported --grp spelling pending exact-edition option review. Trans distance/mappability filters and the multi-intron correction remain separate. qvalue and qtlBHM do not receive credit from QTLtools. |
| [P074](https://doi.org/10.1038/s41588-025-02417-6) | 1.1 aortic-valve eQTLs supplied to a colocalization analysis. | Exact mapping commands, settings and prepared inputs remain unreported. COLOC and LocusCompareR are separate providers. |
| [P104](https://doi.org/10.1038/s41588-026-02733-5) | 1.3.1 fdensity around cis-eQTLs, 10 kb bins and a reported 2 Mb neighborhood, using cell-type-specific open-chromatin annotations. | The native component preserves the selected source's QTL-presence counting and input-order guard. Exact lead-BED construction, assembly, strand handling and --window invocation need actual inputs; a textual neighborhood size does not supply every command option. |
| [P108](https://doi.org/10.1038/s41588-023-01560-2) | 1.3.1 nominal cis scans to select hidden-covariate counts for gene and isoform analyses. | Preserve the separate preprocessing/residualization and covariate-selection steps. This is not a reported fdensity use. |
| [P190](https://doi.org/10.1016/j.ajhg.2025.10.012) | Unreported edition. Discusses Max T/std-sQTL comparison and input/output compatibility with MAJIQTL. | QTLtools executable use is unresolved from these paragraphs. Inspect supplement/author code before crediting execution or removing the candidate. A method comparison or compatible format alone does not prove a QTLtools run. |
| [P201](https://doi.org/10.1016/j.ajhg.2024.09.004) | Unreported edition. LeafCutter-derived sQTLs, reported 10,000 adaptive permutations, a 1 Mb initial window later restricted to 250 kb, 29 PEER covariates and five genotype PCs. | Do not invent the adaptive-permutation command or assign 1.3.1. Keep intron-cluster and external TSS window definitions distinct; mashr remains separate. |
| [P203](https://doi.org/10.1016/j.ajhg.2024.09.001) | Unreported edition. Sample matching described as match, plus nominal, permutation and conditional cis passes with 20 PEER factors. | Keep the paper's match wording. The renamed mbv command does not establish the paper edition. Prepared BAM/VCF/expression inputs, options and qvalue remain explicit. |
| [P219](https://doi.org/10.1016/j.ajhg.2024.07.010) | 1.2 for cis mapping and covariate optimization; a separate quan use has no edition stated in its paragraph. | Do not assign the cis edition to quan automatically. Preserve the two LMM residual definitions, PEER selection, BMI/chip covariates and reported minor-allele effect orientation. |
| [P227](https://doi.org/10.1016/j.ajhg.2024.06.017) | 1.2 MBV for RNA/genotype matching, swap assessment and RNA/WGS matching. | The extraction's matching text separates citation 27 from version 1.2; it does not report a 1.2.27 patch. Sample corrections and exclusions require actual declared data and must not be inferred by the adapter. |

## Why the editions and input order matter

The [official release notes](https://qtltools.github.io/qtltools/pages/basic_install.html)
describe a missing-dosage fix in 1.3.1 affecting several association modes, changes
to defaults, and incompatible quantification across the 1.2 boundary. Historical
editions must remain explicit. A current 1.3.1 provider is not substituted for a
paper's 1.1 or 1.2 analysis.

The selected official source is tag 1.3.1, peeled commit
`a9606ee736d8489e3ec54c8b8c92c32fecad3bd0`. The native fdensity work follows
the mode's BED conversion, closed candidate window, strand orientation, boolean
per-QTL counting, final-bin handling and small-tree order guard. The observed
reference executable is the explicitly pinned Ubuntu build
`1.3.1+dfsg-4build3`; it is not represented as an official upstream binary build.
Selected upstream source and GPL notices are retained in the source packet.

## Warnings and next work

**The native component is not a complete QTLtools implementation or a paper
reproduction.** It supplies no cis/trans inference, permutation calibration,
conditional mapping, MBV, quantification, FDR procedure or author preprocessing.
The catalog row remains method-overlap until a separately qualified admission.
Read the fdensity guide's source-order and allocation restrictions before use.

**Missing/prohibited dependencies:** skip the dependent upstream reference or
analysis, state its name and the precise missing binary/library/profile/input,
and supply recovery instructions. Downloads/private installation are authorized
where permitted. Native fdensity does not require the upstream executable.
Retained controls must not be described as fresh reference execution.

Next implement a tested exact-edition execution adapter for the mapping, MBV and
quantification roles, verify the 1.1/1.2 providers and options, and resolve P190's
binding before cumulative catalog review. Preserve failed builds, original/null
outputs, source differences and all private runtimes. Cell Ranger remains excluded.
