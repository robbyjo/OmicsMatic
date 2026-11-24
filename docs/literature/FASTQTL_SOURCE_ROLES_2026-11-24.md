# FastQTL source roles: audit pending catalog admission

The frozen 250-paper corpus contains FastQTL candidates in **five papers**,
across ten paragraphs, including repeated code-availability text. All 250 fresh
primary XML records and DOI identities were checked; a scan of table/code and
other nonstandard structures found no additional candidates. This audit adds
**zero** family or review credit. Canonical totals remain **158 scoped families,
1,755 reviewed and 969 pending uses** until guarded catalog admission.

| Paper | Actual source role and boundary |
|---|---|
| [P019, Nature](https://doi.org/10.1038/s41586-026-10836-6) | FastQTL supports selection of PEER factor counts from 5–50, choosing the count with significant-eQTL yield closest to 95% of the maximum. The procedure is used within PsychAD subcohorts and after combination. Version, exact flags, tie handling and exact yield unit are not established. A generic first-above-95% rule would change the stated method. |
| [P157, Nature Genetics](https://doi.org/10.1038/s41588-024-01896-3) | GTEx-based eQTL mapping uses 60 PEER factors, sex and five genotype PCs. Disease-stratum fits and fixed-effects meta-analysis are separate stages. Methods do not state a nearby version; code availability explicitly gives **FastQTL 2.165**, repeated in Associated Data. Preserve the original missing nearby-version observation and add contextual evidence. The new 2.184/GTEx adapter does not establish 2.165 compatibility. |
| [P170, Nature Genetics](https://doi.org/10.1038/s41588-025-02344-6) | PBMC pseudobulk cis-eQTL mapping uses a ±1 Mb TSS window and 1,000 permutations, with age/sex where available, five genotype PCs and 15 PEER factors. Log-CPM/inverse-normal preparation and covariate residualization are separate preprocessing stages. The mapping threshold and later profile-prioritization threshold differ. Preserve the source's colocalization-stage terminology rather than silently converting it to a nominal-P claim. Version is unreported. |
| [P187, AJHG](https://doi.org/10.1016/j.ajhg.2025.11.009) | caQTL mapping uses ±1 kb of peak centers, MAF ≥0.1, sex, two genotype PCs and TSS enrichment; expression QTL mapping uses ±1 Mb TSS windows with cell-type-specific expression-PC counts. BH adjustment of FastQTL beta-adjusted P values is distinct from the upstream beta calculation. Inverse-normal preparation, pseudobulk aggregation and later LD clumping are separate stages. Version is unreported. Cell Ranger remains user-excluded. |
| [P212, AJHG](https://doi.org/10.1016/j.ajhg.2025.09.003) | Colocalization **reuses public pancreatic-islet FastQTL eQTL results**; a new local FastQTL execution is not established. Lack of isolated signals motivates a one-signal-per-gene assumption for that dataset. APEX conditional analyses in other tissues do not establish conditional FastQTL mapping. |

The prepared adapter implements nominal and fixed/adaptive permutation passes
for the named Ubuntu 2.184/GTEx edition. It does not implement upstream PEER
optimization, phenotype construction, BH/q-value policy, meta-analysis,
colocalization or these complete paper workflows. Its biallelic-SNV input scope
also does not establish all variant types used in a paper.

The author's bundled older binary could not start because `libgsl.so.0` was
missing. That failed probe is retained. The official documentation endpoints
returned unavailable responses during intake (404 and 403); no access controls
were bypassed. A public Hall-lab mirror was inspected but describes a **modified
structural-variant fork**, so its old INSTALL directory name was not accepted as
proof of an unmodified 2.165 edition. No 2.165 analysis has been admitted.

The tested runtime instead uses the pinned Ubuntu package, its five declared
distribution patches, author source tag `v7` at
`d7396fc4ac16e8c0e6686c4988f1c331a41a9dc6`, and separately fingerprinted private
dependencies. Upstream is unmaintained; these provenance and edition limits must
remain visible when adding the family to the catalog.

Retained private source evidence: `build/fu-1/build/fastqtl-701/CENSUS.json`,
`PRIVATE-ANCHORS.json`, upstream intake receipts, original archives and the
unchanged predecessor source context. Full primary text is not republished here.
