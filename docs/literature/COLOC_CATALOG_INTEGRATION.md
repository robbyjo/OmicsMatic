# COLOC catalog integration and source crosswalk

This admission adds one **bounded native integration**, centered on the qualified
single-causal ABF file workflow. The available native stable credible-BF method
has explicit source differences. The [admission proof](omics-2023-2026/coloc-catalog-validation.json)
and fresh verification establish the completed catalog state; this guide alone
does not establish that admission has been applied.

250 papers, 446 named families, 2,700 observations, 1,439 individually reviewed positive uses and 1,261 awaiting individual review. There are 135 new scoped integrations (25 native Java and 110 upstream), 259 missing and 40 method-overlap families; 138 integrations include three existing adapters. Twenty-six COLOC reviews are appended and all 1,413 earlier reviews, including P190, are retained. The full implementation and semantic-review goal remains open.

Native Java single-causal coloc 6.0.3 ABF from supplied regression/P-value regional statistics, with exact input capture, warnings, RUN/VERIFY/REPLAY and complete native rederivation. A separately qualified native stable credible-BF method is available with explicit source differences.

The 27 reviewed roles distinguish six explicit ABF roles, two mixed ABF/SuSiE roles, three SuSiE roles, 13 unspecified roles, one unspecified multi-causal role and two FUSION-hosted roles. The native BF method is not literal whole-package source parity: H3 arithmetic, weighted conditional SNP probabilities, extreme-weight handling and boundary/refusal behavior differ. Hosted execution, original multi-causal fits, null/per-SNP-prior source mode, historical editions and complete author workflows remain explicit gaps. Current source-build artifacts are required; the published 0.4.0 release predates these workflows.

## Available scopes and evidence

- [ABF file workflow](../vignettes/coloc-abf-workflow.md): explicit regression/P-value
  inputs, trait types/scales, priors/weights, exact capture, RUN/VERIFY/REPLAY and
  complete native rederivation. Thirty original cases, 62 workflow tests per JVM;
  [qualification](coloc-abf-workflow-checks/README.md).
- [Native stable BF method](../vignettes/coloc-susie-native.md): supplied credible
  rows, exact identities, overlap/priors, visible warnings and undefined-posterior
  safeguards. Fifty tests per JVM and 28 explicitly classified source profiles;
  [qualification](coloc-susie-audit-checks/README.md). Twelve source profiles match
  completely; weighted regional-only matches and other outcomes are not literal
  whole-package parity.
- [Earlier API component](coloc-abf-handover/README.md): actual original coloc
  6.0.3 capture and immutable synthetic fixture. Current source-build artifacts
  are required; the public 0.4.0 release predates these scopes.

The original fits/cases are synthetic references. No author cohort was analyzed
or original paper reproduced. The native BF method is an alternative statistical
implementation, with stable H3, weighted conditional SNP probabilities and
extreme-weight/boundary behavior differing from selected source output.

## Source-role mapping

All 250 frozen XML identities and 51 named COLOC body paragraphs were checked.
The [machine-readable coverage](omics-2023-2026/coloc-source-role-coverage.json)
contains exact paragraph hashes, source XML identities, literal editions, host
declarations, method mapping and remaining roles. There are 24 direct-package
roles, two FUSION-hosted roles and one role with an unspecified host. Eight
papers explicitly contain an ABF method branch and five a SuSiE branch; two
papers contain both. Unreported entry points are not assigned an exact author
invocation merely because a current component is available.

P190's existing review and observation text/anchor remain unchanged. Its fuller
current source context is stored in the separate role qualification; 26 other
COLOC reviews are appended. All 1,413 earlier review objects remain exact.
Paper-wide completeness remains open for every newly summarized use.

### P073 — abf

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631754/); DOI `10.1038/s41588-024-02019-8`. Host: direct. Reported editions: 5.2.3.

Used coloc.abf with GWAS/sQTL beta estimates, at least ten variants and default priors; used sQTL MAFs to estimate quantitative-trait scale. Applied H4>0.75, including cis/trans and dynamic sQTL comparisons.

Current mapping: source-compatible-current-ABF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Keep each comparison and sample-size/scale declaration explicit. Regional H4 differs from a conditional SNP posterior; LocusCompare visualization and S-LDSC are separate.

### P074 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12807872/); DOI `10.1038/s41588-025-02417-6`. Host: direct. Reported editions: 3.2.1, 5.1.0.

Used COLOC for aortic-valve eQTL/AS TWAS candidates around gene boundaries and separately for GTEx extravalvular eQTL/GWAS regions around lead variants; both used PP4>0.75.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: The valve stage names 3.2.1 and the GTEx stage 5.1.0; code availability repeats 3.2.1. Preserve the stage-specific editions, distinct window centers and unreported function/priors.

### P088 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631763/); DOI `10.1038/s41588-024-01975-5`. Host: direct. Reported editions: 4.0.4.

Applied coloc after significant MR results for heart/whole-blood eQTLs and UKB protein pQTLs with DCM/MTAG-DCM. Set p1=p2=1e-4 and p12=1e-6; PP4>0.5 was some evidence and >0.7 strong evidence.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: MR instrument clumping and Bonferroni screens are preceding steps, not coloc SNP filters. Preserve the custom strict shared prior, two thresholds, source datasets and unreported function/input details.

### P090 — multi-causal-unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631752/); DOI `10.1038/s41588-024-01952-y`. Host: direct. Reported editions: 5.2.3.

Used coloc allowing multiple causal variants to compare DCM GWAS and GTEx eQTLs for genes at DCM loci, choosing tissues with the lowest TWAS P value.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: The multi-causal entry point, LD, priors and signal-fitting settings are not named here. Do not infer an exact coloc.susie recipe solely from allowing multiple variants.

### P097 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695657/); DOI `10.1038/s41588-025-02413-w`. Host: direct. Reported editions: Not reported.

Compared Tarkin/QMDiab shared regional variants within +/-1 Mb for replication, requiring H4 to be the most likely hypothesis plus a joint association threshold and direction concordance. Also formed peptide-detection-weighted coloc-MSPA fractions for H3/H4 against UKB-PPP.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: H4 argmax is not a fixed PP4 cutoff. Preserve the separate datasets and joint-P criterion. The weighted coloc-MSPA aggregation is custom post-processing, not coloc prior weighting.

### P102 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549052/); DOI `10.1038/s41588-024-01957-7`. Host: direct. Reported editions: 5.1.0.

Compared overlapping SV-eQTL and agronomic SV-GWAS loci with COLOC defaults, using regional H4>0.5 and a shared-variant posterior threshold >0.5.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Keep regional and variant conditional probabilities distinct. Nonhuman structural-variant association inputs, exact function and phenotype-scale choices are not established by a human SNP workflow.

### P104 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13649572/); DOI `10.1038/s41588-026-02733-5`. Host: direct. Reported editions: Not reported.

Used coloc on overlapping molecular-QTL/GWAS meta-analysis regions around gene bodies without a P-value filter; normalized trait variance was set to one, other settings default, and PP4>=0.8 denoted colocalization.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Do not introduce significance filtering or infer a version/function. The separate three-signal moloc analysis is outside two-trait coloc.

### P105 — single-and-susie

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703676/); DOI `10.1038/s41588-023-01569-7`. Host: direct. Reported editions: 5.1.0.

Applied single-causal-variant coloc at significant EAS loci and applied coloc to SuSiE signals at loci with multiple conditionally independent signals.

Current mapping: source-compatible-current-ABF-method, native-stable-supplied-credible-BF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Keep the single-causal and SuSiE branches distinct. Exact entry points, priors, LD and conditional/SuSiE settings are not supplied in this paragraph.

### P120 — hosted-unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695664/); DOI `10.1038/s41588-025-02410-z`. Host: FUSION. Reported editions: Not reported.

Used FUSION TWAS and its internal COLOC method with selected GTEx tissue weights to link hypothyroidism risk to expression; reported regional PP4>0.75 for candidate gene mapping.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: This is a FUSION-hosted method, not proof of a standalone coloc invocation. The eQTL/TWAS screen is not an automatic SNP-level coloc filter; version/function/priors remain unspecified.

### P130 — abf

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11549054/); DOI `10.1038/s41588-024-01939-9`. Host: direct. Reported editions: 5.2.2.

Used coloc.abf for neuropathology loci and significant GTEx/ROSMAP QTLs, with ordinal case fractions chosen by dichotomization, shared prior 1e-5 and posterior >80%. Also compared selected concordant NPE signals and excluded APOE for that comparison.

Current mapping: source-compatible-current-ABF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Preserve region-selection versus within-region coverage, source screening and case definitions. The source uses PrC for prior and posterior wording; do not conflate the prior 1e-5 with the posterior threshold.

### P131 — hosted-unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13553304/); DOI `10.1038/s41588-026-02735-3`. Host: FUSION. Reported editions: Not reported.

Used FUSION colocalization for second-order disease-MDD GWAS factors with the same GTEx tissue weights, then integrated PP4>0.80 hits with T-SEM and normalized tissue gene proportions.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: FUSION hosts this analysis; standalone coloc version/function is unreported. T-SEM/QGENE filtering, ACAT and normalized gene proportions are separate operations.

### P132 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703693/); DOI `10.1038/s41588-023-01555-z`. Host: direct. Reported editions: Not reported.

Used COLOC in R to compare CH GWAS with expression QTLs; the TCL1A example compared the remaining eQTL signal after conditioning on rs78986913.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: The conditional association fit is separate from coloc. Exact regional inputs, software edition, function, priors and a general decision threshold are not specified here.

### P157 — abf-and-susie-benchmark

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11525184/); DOI `10.1038/s41588-024-01896-3`. Host: direct. Reported editions: Not reported.

Benchmarked single-causal coloc from P-value Bayes factors and SuSiE-coloc over pairs of pure mRNA/protein credible sets; the maximum pairwise H4 represented the gene.

Current mapping: source-compatible-current-ABF-method, native-stable-supplied-credible-BF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: These are comparison methods, not the main CLPP analysis. The authors retained their strict purity filter and used minimum SuSiE/FINEMAP PIPs separately; do not replace that procedure with generic coloc defaults.

### P170 — abf

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597823/); DOI `10.1038/s41588-025-02344-6`. Host: direct. Reported editions: Not reported.

Used coloc.abf defaults for perturbation-dependent reQTL/GWAS comparisons, H4>0.5, conditional variant probabilities and 95% credible sets; reran QTL/coloc analyses within perturbation-score tertiles.

Current mapping: source-compatible-current-ABF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: FastQTL eGene selection, pseudobulk/tertile construction, credible-set summarization and Fisher enrichment are separate. Results and Methods differ on the nonimmune-trait count; no count is silently repaired.

### P181 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13361010/); DOI `10.1016/j.ajhg.2026.06.005`. Host: direct. Reported editions: Not reported.

Applied tests from the coloc R package to significant FUSION multimodal-TWAS hits across traits, using COLOC.PP4>0.8.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: TWAS construction/screens are preceding operations. The coloc function, version, regional variant coverage, priors and scale inputs are not specified here.

### P183 — abf

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12824624/); DOI `10.1016/j.ajhg.2025.11.012`. Host: direct. Reported editions: Not reported.

Used coloc.abf with p1=p2=1e-4 and p12=1e-5 to integrate eQTL/GWAS data and compare lncRNA/circRNA with neighboring PCG eQTL/sQTL signals in shared regions.

Current mapping: source-compatible-current-ABF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Methods use PP4>0.8 for GWAS integration; results also describe PP3/PP4>=0.8 for RNA pairs. Preserve these distinct decisions. SMR/HEIDI, OPERA, FUSION and eCAVIAR are separate.

### P185 — abf

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639090/); DOI `10.1016/j.ajhg.2024.10.017`. Host: direct. Reported editions: 5.1.0.

Used coloc.abf separately for OA GWAS phenotypes and cartilage mQTLs within +/-500 kb, retaining all mQTL test results before colocalization and using shared variants. Applied H4>0.8, then retained genotype-present significant mQTL results.

Current mapping: source-compatible-current-ABF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: The later mQTL FDR filter is post-processing, not the input P-value cutoff. Preserve GWAS-phenotype/probe pairing and distinguish regional H4 from SNP-level evidence.

### P189 — abf

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11639085/); DOI `10.1016/j.ajhg.2024.10.008`. Host: direct. Reported editions: Not reported.

Used coloc.abf for protein pQTL/PCOS GWAS regions within +/-500 kb, with p1=p2=1e-4 and p12=1e-5 and H4>0.8. With multiple pQTLs, reported the strongest colocalization per protein.

Current mapping: source-compatible-current-ABF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Retain per-pQTL tests before the strongest-hit summary. Function priors are explicit but version, scale and complete input construction are not.

### P190 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12626415/); DOI `10.1016/j.ajhg.2025.10.012`. Host: unspecified. Reported editions: Not reported.

Reported COLOC comparisons of MAJIQTL- versus LeafCutter-derived sQTLs with AD/PD GWAS, using PPH4>0.5.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: This Results paragraph establishes reported COLOC analysis, but not its exact R entry point/host, version or settings. Detailed supplementary Note S7.1 is not reviewed here; no default ABF or SuSiE recipe is invented.

### P193 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808957/); DOI `10.1016/j.ajhg.2025.10.002`. Host: direct. Reported editions: Not reported.

Used coloc with GWAS Catalog associations, locally generated linear-model irQTL summary statistics and GTEx v8 eQTLs, checking robustness using an independent eQTL dataset.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Version, entry point, priors, region, threshold and scale inputs are not stated in this paragraph. The subsequent scipy transcription model is unrelated to coloc inference.

### P201 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568790/); DOI `10.1016/j.ajhg.2024.09.004`. Host: direct. Reported editions: 5.2.3.

Compared significant sQTL loci with hematological GWAS traits using p1=p2=1e-4 and declared p12=1e-6, with common-causal posterior >0.8 and H4/H3>5.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: The source calls 1e-6 a default; retain that explicit value rather than replacing it with the selected current API default. Lead/locus selection and LD visualization are separate; the exact function is not named.

### P203 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568756/); DOI `10.1016/j.ajhg.2024.09.001`. Host: direct. Reported editions: 5.1.0.

Selected independent significant GWAS lead variants, then used all variants in surrounding 100-kb regions for coloc with eQTLs, declaring PP4>0.8.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Keep all within-region variants and the separate lead-selection step. The stated 10e-6 prior is attributed to eQTL association without an unambiguous parameter allocation; do not silently relabel it p12 or normalize author wording.

### P212 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12614742/); DOI `10.1016/j.ajhg.2025.09.003`. Host: direct. Reported editions: 5.1.0.1.

After a separate PLINK LD screen, ran coloc on eQTL/GWAS signal pairs with lead r2>=0.5, using all shared variants within 500 kb of the GWAS lead. Used PPH4>=0.7 and prior-sensitivity comparisons.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Retain the literal edition 5.1.0.1 and separate LD preselection. Exact function and sensitivity grid are unreported; do not substitute a particular historical runtime or infer its prior choices.

### P219 — susie

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11393713/); DOI `10.1016/j.ajhg.2024.07.010`. Host: direct. Reported editions: Not reported.

Used SuSiE-coloc on paired skin meQTL/eQTL signals after allele/beta alignment. Calibrated shared priors using four scenarios and selected p12=0.0019; marginal association quantities are described as p1+p12=0.0026 and p2+p12=0.0122.

Current mapping: native-stable-supplied-credible-BF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Preserve the source marginal-versus-exclusive prior definitions; do not silently infer the exact p1/p2 arguments from rounded quantities. LD/fitting, calibration selection and mediation are separate; reported comparison uses posterior >0.8.

### P234 — susie

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087463/); DOI `10.1016/j.ajhg.2026.02.022`. Host: direct. Reported editions: v.5.

Used coloc-SuSiE to compare pcQTL, eQTL and GWAS credible sets within tissues; linked hits and constructed connected-component credible-set groups using reported PPH4>=0.75.

Current mapping: native-stable-supplied-credible-BF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: The source also says greater than 0.75 in prose; retain the explicit >= expressions and note the wording difference. Edition v.5 is partial. PLINK LD estimation, graph grouping and cross-tissue variant-trait deduplication are separate.

### P246 — susie

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12414667/); DOI `10.1016/j.ajhg.2025.06.013`. Host: direct. Reported editions: Not reported.

Fine-mapped the CASR region and tested colocalization with 55 phenotypes using Coloc-SuSiE.

Current mapping: native-stable-supplied-credible-BF-method. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Exact functions, version, LD, priors and thresholds are deferred to supplementary methods not reviewed here. SKAT-O and logistic-direction analyses are separate; this is not a complete author workflow.

### P248 — unspecified

[Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447089/); DOI `10.1038/s41588-026-02682-z`. Host: direct. Reported editions: 5.2.3.

Used COLOC to compare SV-eQTL and GWAS signals in a plant RIL study, using H4>0.50 for shared causal variants.

Current mapping: Exact entry point remains unreported; no author invocation inferred. This is method-level availability, not author-edition/workflow reproduction.

Source boundary: Retain nonhuman structural-variant context. Exact coloc function and inputs are not stated. EMMAX association/TWAS, PLINK clustering and hotspot permutations are separate steps.

## Warnings, recovery and admission procedure

SKIP3: skip the dependent analysis/audit when an exact permitted source, runtime or validation dependency is unavailable or forbidden; name what must be restored or leave the skip visible. FAIL2: admit no complete result; retain raw inputs, partials and errors, correct the cause and retry NEW outputs. Do not repair IDs, alleles, source inequalities, nonfinite values, statistics or algorithms. Check native BF warnings and defined-posterior flags; undefined zero markers are not probabilities. Earlier PLINK fixture failures and historical marker-verifier SKIP3 gaps remain retained. Both literature reports stay withheld from Pages; no release is created.

Read the actual workflow's warnings/status. The native BF API's zero marker for
undefined H4 conditioning is not a probability vector; check its defined flag.
The CLI refuses a complete result for that case. Native source-mode null and
per-SNP-prior handling, original multi-causal fitting/LD/credible-set parity,
FUSION-host behavior, unspecified author settings and historical editions
remain distinct gaps. Never convert source ambiguity into an invented recipe.

The [driver instructions](../../tools/literature/coloc_catalog/ADMISSION.md)
provide plan/inspect/apply/verify commands and exact private prerequisites.
The transition binds the actual PLINK/Seurat/METAL/LDAK predecessor stages,
current component qualifications and fresh 27-role source census. It preserves
the old review inverse and cumulative manifest. Earlier fingerprints/verifiers
are not refreshed and original scientific models are not rerun by catalog
verification. Two historical full marker-verifier mains remain SKIP3 for their
missing exact old JAR; other qualified scopes are evidenced separately.

Both literature reports stay withheld from Pages. An authorized main push may
redeploy the rest of the website while preserving those exclusions; no release
is created. Scientific results are not authenticated by SHA-256 alone. Full
goal completion, source-paper reproduction and whole-tool parity are not
claimed by this scoped admission.
