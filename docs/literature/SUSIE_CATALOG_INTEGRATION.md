# SuSiE: qualified supplied-input workflows and 20 source roles

Standalone Java file workflow calling original susieR 0.11.92, 0.12.35 and 0.14.2 with exact private R 4.6.1 profiles. Individual design/phenotype, version-specific RSS and sufficient-statistic interfaces; raw input/original RDS capture, RUN, R-free structural VERIFY and original-runtime REPLAY. Invoke org.jlinalg.cli.SusieWorkflowCli from a current source-built JAR. The native global susie command is separate, with no automatic fallback; global susie-tools is not yet added.

Twenty source roles distinguish fitting/hosted analysis from three external-result reuse roles. Two missed observations are added and the unreviewed P235 orangutan-name false positive is removed with exact-before preservation. No author standalone function is inferred from a method name. Exact 0.11.43 is unavailable in this adapter (SKIP3); host wrappers, original author inputs/settings/dependencies, allele/locus/LD preparation, sequential selection, FINEMAP consensus, CLPP/coloc and whole author workflows remain explicit gaps. Linux R scientific deployment is not qualified. Build current source; the published 0.4.0 binary predates this entry point.

The formal [admission proof](omics-2023-2026/susie-catalog-validation.json) is authoritative for installed metadata. 250 papers, 446 named families, 2,704 observations, 1,503 individually reviewed positive uses and 1,201 awaiting individual review. There are 138 new scoped integrations (25 native Java and 113 upstream), 259 missing and 37 method-overlap definitions; 141 integrations include three existing adapters. All 1,483 earlier reviews remain exact. The full implementation and semantic-review goal remains open.

## Qualification and explicit boundaries

The [workflow qualification](susie-workflow-checks/README.md) preserves 41 independent original-model comparisons: 38 accepted converged fits and three expected nonconvergence refusals. Compared numeric model fields agree exactly; credible-set membership/order agrees. All 73 capture/contract checks pass on each of Windows Java 21, Windows Java 25 and Linux Java 21. Those JVM tests do not refit R. Actual original-runtime REPLAY and all five input-interface examples pass on Windows. Linux R fitting, full host authentication, historical author runtimes, whole tools and whole papers are not qualified. Read the [workflow guide](../vignettes/susie-workflow.md) and [example](../../examples/susie-workflow/README.md).

The existing native global `susie` command is separate. This admission adds no native family credit, global `susie-tools` route, scientific-engine change or release. Original package edition matches do not recover the author R/dependency profile. Unknown author functions remain unknown. A supplied-input counterpart is a reusable capability, not evidence that authors called that standalone function.

## Paper-by-paper source roles

### P073: fine-mapping-result-description

[PMC11631754](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631754/) · DOI `10.1038/s41588-024-02019-8` · host/context: `direct-unspecified`.

Used SuSiE fine-mapping to identify the most likely causal variants for HNRNPLL cis-eQTL/PTPRC trans-sQTL signals in naive CD4+ and CD4+ TEM cells.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: none assigned.

**Remaining scope:** Input/function attribution is unspecified; no individual/RSS/sufficient interface is assigned to the author. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. This results paragraph supports an analysis use missed by extraction. Edition, input interface, LD, priors and settings are not specified; colocalization H4 is a separate quantity. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `1c5a9bc6f69280e434474ef1cb19729d0fb6bcbb8977c0daf6a68765553bdfe4`; body paragraph anchors 21.

### P090: functionally-informed-summary-fine-mapping

[PMC11631752](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631752/) · DOI `10.1038/s41588-024-01952-y` · host/context: `PolyFun`.

Used PolyFun 2020-11-14 priors with SuSiE 0.11.92, up to five causal variants and 95% credible sets, separately for DCM GWAS and MTAG loci; LD came from 10,000 UKB European participants.

Reported susieR editions: `0.11.92`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** The named hosting stack is not executed by this standalone adapter. PolyFun prior estimation, reference construction, GWAS/MTAG locus populations, cross-analysis aggregation and annotation remain distinct from a supplied-prior standalone fit. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `9134de1e47ed25c8ca3033729d6b60846a95603b762996d6aac9d2ef4926c3ca`; body paragraph anchors 26.

### P092: harmonized-summary-fine-mapping

[PMC13649587](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649587/) · DOI `10.1038/s41588-026-02755-z` · host/context: `direct`.

Fine-mapped Crohn disease summary statistics with susieR 0.12.35, a uniform prior and up to ten causal variants; compared UKBB and 1000 Genomes European LD references after mapgen harmonization and LDetect block selection.

Reported susieR editions: `0.12.35`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** Mismatched variants and the chromosome-6 25–35 Mb region were excluded. Reference choice, harmonization and locus selection are author preparation, not inferred or repeated by the fit adapter. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `d0274e977260abcc52aecd4abe6f0e8b8901a5948beae068b28661a402dd5126`; body paragraph anchors 59.

### P095: received-fine-mapped-eqtl-results

[PMC10703684](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703684/) · DOI `10.1038/s41588-023-01574-w` · host/context: `result-reuse`.

Reused GTEx v8 SuSiE-fine-mapped eQTLs from Avsec et al. to benchmark Enformer effect direction; selected EBV-transformed lymphocyte variants with PIP greater than 0.9 and removed conflicting cis-gene directions.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: none assigned.

**Remaining scope:** Reuse of external fine-mapped results: no new SuSiE fit is established; the fit adapter does not retrieve or qualify those resources or downstream selection. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. No new SuSiE fit is established here. The supplied results, strict PIP selection, direction exclusions, Enformer track and bootstrap evaluation are separate from model fitting. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `bc4b0bc8bde6e9a1ab0b70d6e4befeb1e91943bfe86a03c342b69a0ced96e2f4`; body paragraph anchors 19.

### P105: summary-fine-mapping-and-signal-colocalization

[PMC10703676](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703676/) · DOI `10.1038/s41588-023-01569-7` · host/context: `direct-and-coloc`.

Used SuSiE 0.11.92 with default configurations and ten putative causal variants per locus, using LDstore dosage LD from 171,085 unrelated BBJ participants; used SuSiE signals in multi-signal coloc comparisons.

Reported susieR editions: `0.11.92`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** Do not silently alter the reported KING threshold, LD preparation, locus boundaries or defaults. Signal colocalization, annotation and HP-stratified analyses require their own declared settings. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `e62de8f4b3c797ec66020a59b641c28d173b3129e3bc6c26f9d731f7daff017f`; body paragraph anchors 50, 54, 11.

### P108: univariate-predictive-regression

[PMC10703692](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703692/) · DOI `10.1038/s41588-023-01560-2` · host/context: `FUSION-and-isoTWAS`.

Trained per-isoform and gene-level SuSiE predictive models alongside elastic-net and BLUP alternatives, selecting predictive models by cross-validated adjusted R-squared within the isoTWAS/FUSION framework.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: `INDIVIDUAL` / `susie`.

**Remaining scope:** The named hosting stack is not executed by this standalone adapter. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. This is predictive regression, not only a summary fine-mapping task. Model selection, five-fold cross-validation, gene windows, isoform aggregation and the CV R-squared >=0.01 rule are not provided by one standalone fit. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `2b6379547edec29576319cbf8354701af40c7480514415c36d8657075449c182`; body paragraph anchors 36, 46.

### P127: fine-mapped-variant-reuse-for-conditioning

[PMC12695653](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695653/) · DOI `10.1038/s41588-025-02390-0` · host/context: `result-reuse`.

Used the highest conditional PIP variant from each SuSiE credible set as conditioning SNPs in REMETA/REGENIE comparisons, and generated REGENIE conditional summary statistics for a REMETA analysis.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: none assigned.

**Remaining scope:** Reuse of external fine-mapped results: no new SuSiE fit is established; the fit adapter does not retrieve or qualify those resources or downstream selection. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. The named results paragraphs do not establish a standalone SuSiE runtime, edition, input interface or fitting settings. REMETA/REGENIE conditioning and buffer/LD-threshold comparisons remain separate. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `77606c71ec8c2a524b62e37c7a59014631d5196648c30bd11737ba55d7ac15e5`; body paragraph anchors 26, 27.

### P151: summary-fine-mapping-with-explicit-fallback

[PMC13447110](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447110/) · DOI `10.1038/s41588-026-02681-0` · host/context: `multiCOGS`.

Applied susieR to summary-imputed GWAS blocks with minimum P <1e-6; passed SNP probabilities for detected 95% credible-set signals to multiCOGS. Other blocks or runs with no qualifying signals used a separate single-causal-variable approach.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** The named hosting stack is not executed by this standalone adapter. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. Preserve the reported fallback as a distinct author algorithm; no automatic substitute is implemented. Per-signal probabilities must not be silently replaced by marginal PIPs. Imputation and multiCOGS scoring are separate. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `1104a9bc5cfeac5ade7332ce7d0f1ab2680e780cdf1d736a3db6a047aa2e9a4c`; body paragraph anchors 55, 17, 22.

### P153: received-fine-mapped-benchmark-results

[PMC13447104](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447104/) · DOI `10.1038/s41588-026-02695-8` · host/context: `result-reuse`.

Reused eQTL Catalogue V7 and Finucane-laboratory UKB SuSiE results to benchmark enhancer/gene prediction; applied distinct >50% eQTL, >10% noncoding GWAS and >50% coding-variant PIP criteria.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: none assigned.

**Remaining scope:** Reuse of external fine-mapped results: no new SuSiE fit is established; the fit adapter does not retrieve or qualify those resources or downstream selection. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. No new SuSiE fit is reported. Retain the different input resources, phenotype/trait populations, strict PIP cuts and 500-kb credible-set/gene linking rule. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `58d7366dacc144c8821f5657fa302461b72ca21490d647dbd8fef420ae138aa5`; body paragraph anchors 61, 62.

### P157: multi-tool-consensus-fine-mapping-and-colocalization

[PMC11525184](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525184/) · DOI `10.1038/s41588-024-01896-3` · host/context: `direct-and-coloc`.

Used susieR 0.11.43 with summary statistics and in-sample covariate-adjusted LD, five effects, and no minimum-P screening of genes. Reported the minimum FINEMAP/SuSiE PIP and used pure credible-set combinations for SuSiE-coloc comparisons.

Reported susieR editions: `0.11.43`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** Exact original 0.11.43 source/runtime is unestablished; explicit profile requests are SKIP3. No adjacent-version substitution. The exact 0.11.43 source is not established by adjacent CRAN editions. FINEMAP minimum-PIP consensus, purity filtering, GTEx result reuse, CLPP enrichment/permutations and conditional sample restrictions remain separate. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `98edf86475be2175e390999f6b7b6cd2ec2d2aefd39c79956a91384df78b59d1`; body paragraph anchors 43, 58, 61, 63, 17, 71.

### P168: sequential-component-count-fine-mapping

[PMC13447103](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447103/) · DOI `10.1038/s41588-026-02697-6` · host/context: `direct`.

Used susieR 0.14.2 on integrated GWAS/ExWAS regions, sequentially considering one through ten credible sets and retaining the largest approximately independent set of lead variants with maximum r-squared <0.25.

Reported susieR editions: `0.14.2`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** Exonic/imputed variant selection, 690-participant LD construction, iterative choice of component count and corrected lead-significance filtering are explicit author preparation/post-processing, not one fixed-L fit. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `e68251d7b700b83479ee28f992b07c396ca42e800acfa35436035248b1ffc149`; body paragraph anchors 50.

### P169: functionally-informed-summary-fine-mapping

[PMC11525171](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525171/) · DOI `10.1038/s41588-024-01900-w` · host/context: `PolyFun`.

Fine-mapped CRC loci with PolyFun 1.4.1 and susieR 0.11.92 using annotation-based variant priors and Genomics England LD. Chose L from the number of independent variants, with L=2 for a single independent signal.

Reported susieR editions: `0.11.92`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** The named hosting stack is not executed by this standalone adapter. Preserve the results/methods difference between cumulative PIP >0.95 and reaching 0.95, and the individual PIP >0.001 rule. These do not define a replacement for source credible sets; PolyFun and locus/reference preparation remain separate. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `35e6eef968caa63cd301d6867f6353d88441df9bd960be9d32288a4e86561782`; body paragraph anchors 39, 6.

### P181: hosted-individual-level-fine-mapping-and-pip-colocalization

[PMC13361010](https://pmc.ncbi.nlm.nih.gov/articles/PMC13361010/) · DOI `10.1016/j.ajhg.2026.06.005` · host/context: `tensorQTL-cis_susie`.

Ran SuSiE via tensorQTL cis_susie for the strongest RNA phenotype per gene in each GTEx tissue, then used 95% credible-set PIPs for functional enrichment and eCAVIAR-style xQTL/FinnGen CLPP.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: `INDIVIDUAL` / `susie`.

**Remaining scope:** The named hosting stack is not executed by this standalone adapter. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. A standalone susieR fit does not qualify tensorQTL hosting. Preserve chromosome/position/REF/ALT matching, shared-variant PIP products and CLPP >0.01; this CLPP is not coloc H4. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `1bf57e8fc7856dd285a55fcae868bd9496d7940ada31f88b80a86897fe0af37b`; body paragraph anchors 20, 21.

### P186: gwas-locus-fine-mapping

[PMC11639076](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639076/) · DOI `10.1016/j.ajhg.2024.10.018` · host/context: `direct`.

Fine-mapped genome-wide significant FinnGen female-cohort regions using SuSiE 0.12.35 and used good-quality credible sets to define independent loci after additive and recessive REGENIE analyses.

Reported susieR editions: `0.12.35`. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** The paragraph does not recover the good-quality rule, L, LD source or exact fit arguments. REGENIE models, case/control composition and recessive eligibility are not SuSiE defaults. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `0d895551794d073cddbc9ea40cac2c5e1778ca28df63e5272f3ef06651c3ea64`; body paragraph anchors 10.

### P200: ancestry-weighted-reference-summary-fine-mapping

[PMC13393102](https://pmc.ncbi.nlm.nih.gov/articles/PMC13393102/) · DOI `10.1016/j.ajhg.2026.05.011` · host/context: `direct`.

Used SuSiE with L=10 and default prior variance for multi-biobank meta-analysis regions, ancestry/effective-sample-size-weighted reference LD and LD–Z concordance assessment; described 95% minimal credible sets.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. No edition is supplied. Preserve cross-biobank variant intersection, window definitions and the reported LD–Z concordance metric; do not substitute unweighted LD or infer exact s-diagnostic arguments. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `56c38e0cf1ead752938f859157f6b57f053b897937df0573ddc492f8cdb8fa61`; body paragraph anchors 16, 24.

### P203: population-specific-eqtl-fine-mapping-result-description

[PMC11568756](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568756/) · DOI `10.1016/j.ajhg.2024.09.001` · host/context: `direct-unspecified`.

Applied SuSiE separately to European, East Asian and African American cis-eQTL results to identify credible SNP sets and candidate causal variants.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: none assigned.

**Remaining scope:** Input/function attribution is unspecified; no individual/RSS/sufficient interface is assigned to the author. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. This results paragraph supports a missed analysis use. The 5% empirical gene-level FDR belongs to preceding eQTL mapping; no SuSiE edition, fit interface, prior, L or credible-set coverage is specified. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `2d035da198a42fbfd1067cedca09c0fc77b108e5269d02af3481cebd8bd1d644`; body paragraph anchors 31.

### P219: multi-signal-colocalization

[PMC11393713](https://pmc.ncbi.nlm.nih.gov/articles/PMC11393713/) · DOI `10.1016/j.ajhg.2024.07.010` · host/context: `coloc`.

Used SuSiE-coloc for skin cis-eQTL/meQTL pairs after explicit allele/beta flipping, allowing multiple causal signals; reported p12=1.93e-3 and posterior shared-causal probability >0.8.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** The named hosting stack is not executed by this standalone adapter. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. Keep the 83,529 methods versus 83,530 results pair counts unresolved. SuSiE fitting, coloc H4, allele harmonization and later mediation are separate; no SuSiE package edition or fit arguments are provided. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `e4fadb062adf4cc95728affb2ee19c5e59af94b50cce138a693a6dbb5f073112`; body paragraph anchors 33, 58, 77.

### P221: fine-mapping-informed-prediction-penalties

[PMC13078710](https://pmc.ncbi.nlm.nih.gov/articles/PMC13078710/) · DOI `10.1016/j.ajhg.2026.03.008` · host/context: `direct-and-glmnet`.

Applied SuSiE to MESA multi-ancestry gene windows using in-sample LD, L=10 and minimum absolute credible-set correlation 0.5; used 1-PIP as elastic-net variant penalties for EN-FM prediction.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: `RSS_Z` / `susie_rss`.

**Remaining scope:** No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. The precise fit interface/edition and upstream phenotype/genotype preparation are not stated. The 1-PIP penalty, glmnet alpha=0.5 and predictive training are not performed by the SuSiE fit itself. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `927bc7bc410e96e1682ea57155b66dc3b7e436caeedabc7e9819a6e254d8bdda`; body paragraph anchors 13.

### P234: individual-level-pcqtl-eqtl-and-null-fine-mapping

[PMC13087463](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087463/) · DOI `10.1016/j.ajhg.2026.02.022` · host/context: `direct-and-coloc`.

Ran susieR 0.12.35 on individual-level genotype and PC/expression phenotypes with uniform priors, L=10 and minimum absolute correlation 0.5, without prefiltering phenotype significance; repeated the workflow after genotype shuffling and used coloc.SuSiE for colocalization.

Reported susieR editions: `0.12.35`. Supplied-input counterparts: `INDIVIDUAL` / `susie`.

**Remaining scope:** Preserve the PC versus single-gene phenotype populations and genotype/phenotype matching. Calibration shuffles, phenotype-level FDR, coloc v5 hosting and GWAS/LD preparation remain separate. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `e9f518b47ea11fda0d58555e269e27a83b3a27a62a1491b07f1c17646ebf4d2f`; body paragraph anchors 16, 17, 18, 20, 31, 37.

### P246: casr-fine-mapping-and-colocalization

[PMC12414667](https://pmc.ncbi.nlm.nih.gov/articles/PMC12414667/) · DOI `10.1016/j.ajhg.2025.06.013` · host/context: `direct-and-coloc`.

Fine-mapped common CASR signals with SuSiE, reporting ten independent causal sets, and tested colocalization with 55 phenotypes using Coloc-SuSiE.

Reported susieR editions: not established at these anchors. Supplied-input counterparts: none assigned.

**Remaining scope:** Input/function attribution is unspecified; no individual/RSS/sufficient interface is assigned to the author. No literal susieR edition is established by these source anchors; do not select a historical profile on behalf of the author. Ten reported sets do not establish the supplied L argument. Full computational details are deferred to supplemental methods; no edition, exact inputs/LD, priors or fit/colocalization settings are established by these body paragraphs. Original author inputs, runtime dependencies, complete settings and the whole reported workflow remain unqualified.

Primary XML SHA-256: `ee8566722fcb23f987013f28182d693347f6013ae3df653212feb758f564a5ea`; body paragraph anchors 10, 32.

## Corrections and conservation

P073 and P203 are source-supported SuSiE uses missed by extraction. P235 names the Sumatran orangutan “Susie”; its exact unreviewed observation is retained in the census and before archive, then removed from active observations. P212 is an introductory method comparison and acquires no observation. P095, P127 and P153 reuse external fine-mapped results; they acquire source-use reviews, not new-fit or resource-retrieval qualification.

All 250 frozen identities, dates, DOI/XML hashes, other tool observations and all 1,483 previous review objects are conserved. The 20 new reviews point to exact paragraph hashes; different PIP inequalities, LD populations, hosted calls and unknown settings remain literal. The original 250-paper collection needs no time extension.

The unchanged Scanpy admission owner must run in its separate exact clean checkout, then invoke unchanged Enrichr and COLOC owners in their separate clean checkouts. The new shared-source inverse restores only the appended reviewed-use JSON; no scientific lookup is overlaid and no prior fingerprint is refreshed. Saved original scientific proofs remain frozen at their producing checkpoint counts.

## Warnings and recovery

SKIP3: skip dependent analysis/audit when an exact permitted source, runtime, package or input is missing or installation is forbidden; name what must be supplied and retain the skip. FAIL2: no complete result; preserve raw inputs, original model objects, partials, warnings and diagnostics, correct invalid schema/data, integrity, model arithmetic, convergence, capacity or process causes and retry NEW output. Never silently repair IDs, alleles, populations, source editions, thresholds, nonfinite values or algorithms. Inspect WARNINGS.md and analysis-status.json. Structural VERIFY does not independently refit R models or authenticate the host. Two historical marker-verifier JAR prerequisites remain SKIP3. Cell Ranger stays excluded. Both literature reports stay withheld from Pages; no release is created.

Use the [guarded driver instructions](../../tools/literature/susie_catalog/ADMISSION.md). Plan/inspect/apply/fresh verify must pass against actual files before claiming admission. Earlier source probes, console-reporting failures and the unavailable first Linux temporary directory remain recorded; the final Linux JVM run is archived privately. Preserve every historical attempt and use NEW outputs.
