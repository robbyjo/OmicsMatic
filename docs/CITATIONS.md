# Scientific citations

This bibliography links OmicsMatic features to the primary publications that introduced or established their statistical methods. Cite both the relevant method paper and OmicsMatic when reporting an analysis. Inclusion here documents methodological provenance; it is not a claim that every implementation detail is identical to the cited software.

The website provides a [searchable citation index](https://robbyjo.github.io/OmicsMatic/citations.html). This file and the per-vignette citation panels are generated from [citations.json](citations.json).

PMID and PMCID values are retrieved from [NCBI PubMed](https://pubmed.ncbi.nlm.nih.gov/) with [enrich-citation-identifiers.py](enrich-citation-identifiers.py).

## v0.3.6 inference additions

The v0.3.6 release adds four connected inference workflows. These quick links expose their primary methodological foundations; the canonical entries remain in the topic bibliography below.

### Empirical-Bayes differential analysis

Moderated Gaussian models, precision-weighted counts, and negative-binomial dispersion shrinkage.

- [Smyth (2004) — Linear models and empirical Bayes methods for assessing differential expression in microarray experiments](#smyth-limma-2004)
- [Law (2014) — voom: precision weights unlock linear model analysis tools for RNA-seq read counts](#law-voom-2014)
- [Robinson (2010) — edgeR: a Bioconductor package for differential expression analysis of digital gene expression data](#robinson-edger-2010)
- [Love (2014) — Moderated estimation of fold change and dispersion for RNA-seq data with DESeq2](#love-deseq2-2014)

### Region-level EWAS

Coordinate-aware aggregation with explicit spatial dependence and region-wide multiplicity control.

- [Pedersen (2012) — Comb-p: software for combining, analyzing, grouping and correcting spatially correlated P-values](#pedersen-combp-2012)
- [Peters et al. (2015) — De novo identification of differentially methylated regions in the human genome](#peters-dmrcate-2015)

### Adaptive and hierarchical testing

Weighted FDR and prespecified hierarchy-aware familywise inference.

- [Benjamini (1995) — Controlling the false discovery rate: a practical and powerful approach to multiple testing](#benjamini-hochberg-1995)
- [Ignatiadis (2016) — Data-driven hypothesis weighting increases detection power in genome-scale multiple testing](#ignatiadis-ihw-2016)
- [Meinshausen (2008) — Hierarchical testing of variable importance](#meinshausen-hierarchy-2008)

### Multiple-imputation inference

Chained equations, Rubin variance pooling, and finite-sample degrees of freedom.

- [Rubin (1987) — Multiple Imputation for Nonresponse in Surveys](#rubin-mi-1987)
- [Buuren (2011) — mice: Multivariate Imputation by Chained Equations in R](#vanbuuren-mice-2011)
- [Barnard (1999) — Small-sample degrees of freedom with multiple imputation](#barnard-rubin-1999)

## Mechanistic interpretation and discussion depth

<a id="wickenhagen-oas1-2021"></a>
- **Wickenhagen A, et al. (2021).** A prenylated dsRNA sensor protects against severe COVID-19. *Science 374(6567), eabj3624.* [DOI: 10.1126/science.abj3624](https://doi.org/10.1126/science.abj3624) · [PMID: 34581622](https://pubmed.ncbi.nlm.nih.gov/34581622/) · [PMCID: PMC7612834](https://pmc.ncbi.nlm.nih.gov/articles/PMC7612834/)
  - OmicsMatic methods: Gene and isoform mechanism, Experimental and clinical evidence, Discussion depth benchmark.

## Bioinformatics evidence resources

<a id="gwas-catalog-api-v2"></a>
- **NHGRI-EBI GWAS Catalog (2026).** GWAS Catalog REST API v2 documentation. *EMBL-EBI documentation.* [Primary source](https://www.ebi.ac.uk/gwas/docs/programmatic-access/rest-api/)
  - OmicsMatic methods: Phenotype-matched source discovery, Curated genomic-window GWAS candidates, Source and release provenance.

<a id="gwas-catalog-summary-statistics"></a>
- **NHGRI-EBI GWAS Catalog (2026).** GWAS Catalog summary statistics documentation. *EMBL-EBI documentation.* [Primary source](https://www.ebi.ac.uk/gwas/docs/methods/summary-statistics/)
  - OmicsMatic methods: Harmonized GRCh38 summary statistics, Indexed regional retrieval, Metadata and checksum provenance.

<a id="eqtl-catalogue-data-access"></a>
- **eQTL Catalogue (2026).** eQTL Catalogue data access. *EMBL-EBI documentation.* [Primary source](https://www.ebi.ac.uk/eqtl/Data_access/)
  - OmicsMatic methods: Molecular QTL source discovery, Bulk-access fallback and source citations.

<a id="gtex-atlas-2020"></a>
- **GTEx Consortium (2020).** The GTEx Consortium atlas of genetic regulatory effects across human tissues. *Science 369:1318-1330.* [DOI: 10.1126/science.aaz1776](https://doi.org/10.1126/science.aaz1776)
  - OmicsMatic methods: Tissue-specific molecular QTL context, Explicit cross-tissue scientific relevance.

<a id="uniprot-api-documentation"></a>
- **UniProt Consortium (2026).** UniProt API queries. *UniProt documentation.* [Primary source](https://www.uniprot.org/help/api_queries)
  - OmicsMatic methods: Reviewed gene and species protein mapping, Protein mapping ambiguity.

<a id="varadi-alphafold-2022"></a>
- **M. Varadi et al. (2022).** AlphaFold Protein Structure Database: massively expanding the structural coverage of protein-sequence space with high-accuracy models. *Nucleic Acids Research 50:D439-D444.* [DOI: 10.1093/nar/gkab1061](https://doi.org/10.1093/nar/gkab1061)
  - OmicsMatic methods: Predicted protein structure investigation, Model version and confidence provenance.

<a id="ucsc-api-documentation"></a>
- **UCSC Genome Browser (2026).** UCSC Genome Browser REST API. *UCSC documentation.* [Primary source](https://genome.ucsc.edu/goldenPath/help/api.html)
  - OmicsMatic methods: Build-specific genomic reference sequence, Audited coordinate conversion and browser links.

<a id="europe-pmc-rest"></a>
- **Europe PMC (2026).** Europe PMC RESTful Web Service. *Europe PMC documentation.* [Primary source](https://europepmc.org/RestfulWebService)
  - OmicsMatic methods: Non-LLM literature retrieval, Bibliographic provenance and bounded searches.

## Foundational inference

<a id="student-1908"></a>
- **Student [W. S. Gosset] (1908).** The probable error of a mean. *Biometrika 6:1-25.* [DOI: 10.2307/2331554](https://doi.org/10.2307/2331554)
  - OmicsMatic methods: Student t test, small-sample mean inference.

<a id="welch-1947"></a>
- **B. L. Welch (1947).** The generalization of Student's problem when several different population variances are involved. *Biometrika 34:28-35.* [DOI: 10.1093/biomet/34.1-2.28](https://doi.org/10.1093/biomet/34.1-2.28) · [PMID: 20287819](https://pubmed.ncbi.nlm.nih.gov/20287819/)
  - OmicsMatic methods: Welch t test, unequal-variance inference.

<a id="pearson-1895"></a>
- **Karl Pearson (1895).** Notes on regression and inheritance in the case of two parents. *Proceedings of the Royal Society of London 58:240-242.* [DOI: 10.1098/rspl.1895.0041](https://doi.org/10.1098/rspl.1895.0041)
  - OmicsMatic methods: Pearson correlation, linear association.

<a id="spearman-1904"></a>
- **Charles Spearman (1904).** The proof and measurement of association between two things. *American Journal of Psychology 15:72-101.* [DOI: 10.2307/1412159](https://doi.org/10.2307/1412159)
  - OmicsMatic methods: Spearman correlation, rank association.

<a id="wilcoxon-1945"></a>
- **Frank Wilcoxon (1945).** Individual comparisons by ranking methods. *Biometrics Bulletin 1:80-83.* [DOI: 10.2307/3001968](https://doi.org/10.2307/3001968)
  - OmicsMatic methods: Wilcoxon signed-rank test, rank inference.

<a id="mann-whitney-1947"></a>
- **H. B. Mann and D. R. Whitney (1947).** On a test of whether one of two random variables is stochastically larger than the other. *Annals of Mathematical Statistics 18:50-60.* [DOI: 10.1214/aoms/1177730491](https://doi.org/10.1214/aoms/1177730491)
  - OmicsMatic methods: Mann-Whitney U test, two-sample rank inference.

<a id="fisher-1922"></a>
- **R. A. Fisher (1922).** On the interpretation of chi-square from contingency tables, and the calculation of P. *Journal of the Royal Statistical Society 85:87-94.* [DOI: 10.2307/2340521](https://doi.org/10.2307/2340521)
  - OmicsMatic methods: Fisher exact test, contingency tables.

<a id="mcnemar-1947"></a>
- **Quinn McNemar (1947).** Note on the sampling error of the difference between correlated proportions or percentages. *Psychometrika 12:153-157.* [DOI: 10.1007/BF02295996](https://doi.org/10.1007/BF02295996) · [PMID: 20254758](https://pubmed.ncbi.nlm.nih.gov/20254758/)
  - OmicsMatic methods: McNemar test, paired binary data.

<a id="friedman-1937"></a>
- **Milton Friedman (1937).** The use of ranks to avoid the assumption of normality implicit in the analysis of variance. *Journal of the American Statistical Association 32:675-701.* [DOI: 10.1080/01621459.1937.10503522](https://doi.org/10.1080/01621459.1937.10503522)
  - OmicsMatic methods: Friedman test, blocked rank inference.

<a id="benjamini-hochberg-1995"></a>
- **Yoav Benjamini and Yosef Hochberg (1995).** Controlling the false discovery rate: a practical and powerful approach to multiple testing. *Journal of the Royal Statistical Society B 57:289-300.* [DOI: 10.1111/j.2517-6161.1995.tb02031.x](https://doi.org/10.1111/j.2517-6161.1995.tb02031.x)
  - OmicsMatic methods: Benjamini-Hochberg FDR, multiple testing.

## Regression

<a id="nelder-wedderburn-1972"></a>
- **J. A. Nelder and R. W. M. Wedderburn (1972).** Generalized linear models. *Journal of the Royal Statistical Society A 135:370-384.* [DOI: 10.2307/2344614](https://doi.org/10.2307/2344614)
  - OmicsMatic methods: generalized linear models, IRLS, logistic regression, Poisson regression.

<a id="bliss-1934"></a>
- **C. I. Bliss (1934).** The method of probits. *Science 79:38-39.* [DOI: 10.1126/science.79.2037.38](https://doi.org/10.1126/science.79.2037.38) · [PMID: 17813446](https://pubmed.ncbi.nlm.nih.gov/17813446/)
  - OmicsMatic methods: probit regression, binary response.

<a id="hoerl-kennard-1970"></a>
- **Arthur E. Hoerl and Robert W. Kennard (1970).** Ridge regression: biased estimation for nonorthogonal problems. *Technometrics 12:55-67.* [DOI: 10.1080/00401706.1970.10488634](https://doi.org/10.1080/00401706.1970.10488634)
  - OmicsMatic methods: ridge regression, prediction scores.

<a id="tibshirani-1996"></a>
- **Robert Tibshirani (1996).** Regression shrinkage and selection via the lasso. *Journal of the Royal Statistical Society B 58:267-288.* [DOI: 10.1111/j.2517-6161.1996.tb02080.x](https://doi.org/10.1111/j.2517-6161.1996.tb02080.x)
  - OmicsMatic methods: lasso, sparse regression.

<a id="zou-hastie-2005"></a>
- **Hui Zou and Trevor Hastie (2005).** Regularization and variable selection via the elastic net. *Journal of the Royal Statistical Society B 67:301-320.* [DOI: 10.1111/j.1467-9868.2005.00503.x](https://doi.org/10.1111/j.1467-9868.2005.00503.x)
  - OmicsMatic methods: elastic net, grouped shrinkage.

<a id="lee-2016"></a>
- **Jason D. Lee, Dennis L. Sun, Yuekai Sun, and Jonathan E. Taylor (2016).** Exact post-selection inference, with application to the lasso. *Annals of Statistics 44:907-927.* [DOI: 10.1214/15-AOS1371](https://doi.org/10.1214/15-AOS1371)
  - OmicsMatic methods: polyhedral selective inference, lasso inference.

<a id="koenker-bassett-1978"></a>
- **Roger Koenker and Gilbert Bassett Jr. (1978).** Regression quantiles. *Econometrica 46:33-50.* [DOI: 10.2307/1913643](https://doi.org/10.2307/1913643)
  - OmicsMatic methods: quantile regression.

<a id="friedman-1984"></a>
- **Jerome H. Friedman (1984).** A variable span smoother. *Laboratory for Computational Statistics Technical Report 5.* [DOI: 10.2172/1447470](https://doi.org/10.2172/1447470)
  - OmicsMatic methods: supersmoother, adaptive span smoothing.

<a id="nadaraya-1964"></a>
- **E. A. Nadaraya (1964).** On estimating regression. *Theory of Probability and Its Applications 9:141-142.* [DOI: 10.1137/1109020](https://doi.org/10.1137/1109020)
  - OmicsMatic methods: kernel regression, Nadaraya-Watson estimator.

<a id="robinson-1988"></a>
- **Peter M. Robinson (1988).** Root-N-consistent semiparametric regression. *Econometrica 56:931-954.* [DOI: 10.2307/1912705](https://doi.org/10.2307/1912705)
  - OmicsMatic methods: partially linear regression, semiparametric regression.

<a id="cleveland-1979"></a>
- **William S. Cleveland (1979).** Robust locally weighted regression and smoothing scatterplots. *Journal of the American Statistical Association 74:829-836.* [DOI: 10.1080/01621459.1979.10481038](https://doi.org/10.1080/01621459.1979.10481038)
  - OmicsMatic methods: LOESS, robust local regression.

<a id="hastie-tibshirani-1986"></a>
- **Trevor Hastie and Robert Tibshirani (1986).** Generalized additive models. *Statistical Science 1:297-310.* [DOI: 10.1214/ss/1177013604](https://doi.org/10.1214/ss/1177013604)
  - OmicsMatic methods: generalized additive models, additive smooths.

<a id="eilers-marx-1996"></a>
- **Paul H. C. Eilers and Brian D. Marx (1996).** Flexible smoothing with B-splines and penalties. *Statistical Science 11:89-121.* [DOI: 10.1214/ss/1038425655](https://doi.org/10.1214/ss/1038425655)
  - OmicsMatic methods: P-splines, penalized B-splines.

<a id="wood-2011"></a>
- **Simon N. Wood (2011).** Fast stable restricted maximum likelihood and marginal likelihood estimation of semiparametric generalized linear models. *Journal of the Royal Statistical Society B 73:3-36.* [DOI: 10.1111/j.1467-9868.2010.00749.x](https://doi.org/10.1111/j.1467-9868.2010.00749.x)
  - OmicsMatic methods: GAM REML, smoothness selection, penalized GLM.

<a id="rigby-stasinopoulos-2005"></a>
- **Robert A. Rigby and D. Mikis Stasinopoulos (2005).** Generalized additive models for location, scale and shape. *Applied Statistics 54:507-554.* [DOI: 10.1111/j.1467-9876.2005.00510.x](https://doi.org/10.1111/j.1467-9876.2005.00510.x)
  - OmicsMatic methods: GAMLSS, distributional regression.

<a id="yee-2010"></a>
- **Thomas W. Yee (2010).** The VGAM package for categorical data analysis. *Journal of Statistical Software 32:1-34.* [DOI: 10.18637/jss.v032.i10](https://doi.org/10.18637/jss.v032.i10)
  - OmicsMatic methods: VGAM, ordinal and categorical regression.

<a id="ferrari-cribari-neto-2004"></a>
- **Silvia Ferrari and Francisco Cribari-Neto (2004).** Beta regression for modelling rates and proportions. *Journal of Applied Statistics 31:799-815.* [DOI: 10.1080/0266476042000214501](https://doi.org/10.1080/0266476042000214501)
  - OmicsMatic methods: beta regression, mean-precision models.

<a id="beasley-2009"></a>
- **T. Mark Beasley, Stephen Erickson, and David B. Allison (2009).** Rank-based inverse normal transformations are increasingly used, but are they merited?. *Behavior Genetics 39:580-595.* [DOI: 10.1007/s10519-009-9281-0](https://doi.org/10.1007/s10519-009-9281-0) · [PMID: 19526352](https://pubmed.ncbi.nlm.nih.gov/19526352/) · [PMCID: PMC2921808](https://pmc.ncbi.nlm.nih.gov/articles/PMC2921808/)
  - OmicsMatic methods: rank inverse-normal transform, omics transforms.

## Mixed, longitudinal, and survival models

<a id="patterson-thompson-1971"></a>
- **H. D. Patterson and Robin Thompson (1971).** Recovery of inter-block information when block sizes are unequal. *Biometrika 58:545-554.* [DOI: 10.1093/biomet/58.3.545](https://doi.org/10.1093/biomet/58.3.545)
  - OmicsMatic methods: REML, variance components.

<a id="henderson-1975"></a>
- **C. R. Henderson (1975).** Best linear unbiased estimation and prediction under a selection model. *Biometrics 31:423-447.* [DOI: 10.2307/2529430](https://doi.org/10.2307/2529430)
  - OmicsMatic methods: BLUP, mixed-model equations.

<a id="kenward-roger-1997"></a>
- **Michael G. Kenward and James H. Roger (1997).** Small sample inference for fixed effects from restricted maximum likelihood. *Biometrics 53:983-997.* [DOI: 10.2307/2533558](https://doi.org/10.2307/2533558)
  - OmicsMatic methods: Kenward-Roger inference, small-sample mixed models.

<a id="breslow-clayton-1993"></a>
- **Norman E. Breslow and David G. Clayton (1993).** Approximate inference in generalized linear mixed models. *Journal of the American Statistical Association 88:9-25.* [DOI: 10.1080/01621459.1993.10594284](https://doi.org/10.1080/01621459.1993.10594284)
  - OmicsMatic methods: PQL, generalized linear mixed models.

<a id="lambert-1992"></a>
- **Diane Lambert (1992).** Zero-inflated Poisson regression, with an application to defects in manufacturing. *Technometrics 34:1-14.* [DOI: 10.1080/00401706.1992.10485228](https://doi.org/10.1080/00401706.1992.10485228)
  - OmicsMatic methods: zero-inflated Poisson, mixture count models.

<a id="lindstrom-bates-1990"></a>
- **Mary J. Lindstrom and Douglas M. Bates (1990).** Nonlinear mixed effects models for repeated measures data. *Biometrics 46:673-687.* [DOI: 10.2307/2532087](https://doi.org/10.2307/2532087)
  - OmicsMatic methods: nonlinear mixed-effects models.

<a id="liang-zeger-1986"></a>
- **Kung-Yee Liang and Scott L. Zeger (1986).** Longitudinal data analysis using generalized linear models. *Biometrika 73:13-22.* [DOI: 10.1093/biomet/73.1.13](https://doi.org/10.1093/biomet/73.1.13)
  - OmicsMatic methods: GEE, working correlation, sandwich inference.

<a id="cox-1972"></a>
- **D. R. Cox (1972).** Regression models and life-tables. *Journal of the Royal Statistical Society B 34:187-220.* [DOI: 10.1111/j.2517-6161.1972.tb00899.x](https://doi.org/10.1111/j.2517-6161.1972.tb00899.x)
  - OmicsMatic methods: Cox proportional hazards, partial likelihood.

<a id="efron-1977"></a>
- **Bradley Efron (1977).** The efficiency of Cox's likelihood function for censored data. *Journal of the American Statistical Association 72:557-565.* [DOI: 10.1080/01621459.1977.10480613](https://doi.org/10.1080/01621459.1977.10480613)
  - OmicsMatic methods: Efron ties, Cox regression.

## Quantitative and statistical genetics

<a id="henderson-1976"></a>
- **C. R. Henderson (1976).** A simple method for computing the inverse of a numerator relationship matrix used in prediction of breeding values. *Biometrics 32:69-83.* [DOI: 10.2307/2529339](https://doi.org/10.2307/2529339)
  - OmicsMatic methods: pedigree A inverse, animal models.

<a id="vanraden-2008"></a>
- **Paul M. VanRaden (2008).** Efficient methods to compute genomic predictions. *Journal of Dairy Science 91:4414-4423.* [DOI: 10.3168/jds.2007-0980](https://doi.org/10.3168/jds.2007-0980) · [PMID: 18946147](https://pubmed.ncbi.nlm.nih.gov/18946147/)
  - OmicsMatic methods: genomic relationship matrix, genomic prediction.

<a id="yang-gcta-2011"></a>
- **Jian Yang et al. (2011).** GCTA: a tool for genome-wide complex trait analysis. *American Journal of Human Genetics 88:76-82.* [DOI: 10.1016/j.ajhg.2010.11.011](https://doi.org/10.1016/j.ajhg.2010.11.011) · [PMID: 21167468](https://pubmed.ncbi.nlm.nih.gov/21167468/) · [PMCID: PMC3014363](https://pmc.ncbi.nlm.nih.gov/articles/PMC3014363/)
  - OmicsMatic methods: GRM, SNP heritability, GCTA.

<a id="kang-emmax-2010"></a>
- **Hyun Min Kang et al. (2010).** Variance component model to account for sample structure in genome-wide association studies. *Nature Genetics 42:348-354.* [DOI: 10.1038/ng.548](https://doi.org/10.1038/ng.548) · [PMID: 20208533](https://pubmed.ncbi.nlm.nih.gov/20208533/) · [PMCID: PMC3092069](https://pmc.ncbi.nlm.nih.gov/articles/PMC3092069/)
  - OmicsMatic methods: EMMAX, mixed-model GWAS.

<a id="zhang-p3d-2010"></a>
- **Zhiwu Zhang et al. (2010).** Mixed linear model approach adapted for genome-wide association studies. *Nature Genetics 42:355-360.* [DOI: 10.1038/ng.546](https://doi.org/10.1038/ng.546) · [PMID: 20208535](https://pubmed.ncbi.nlm.nih.gov/20208535/) · [PMCID: PMC2931336](https://pmc.ncbi.nlm.nih.gov/articles/PMC2931336/)
  - OmicsMatic methods: P3D, compressed mixed-model GWAS.

<a id="wu-skat-2011"></a>
- **Michael C. Wu et al. (2011).** Rare-variant association testing for sequencing data with the sequence kernel association test. *American Journal of Human Genetics 89:82-93.* [DOI: 10.1016/j.ajhg.2011.05.029](https://doi.org/10.1016/j.ajhg.2011.05.029) · [PMID: 21737059](https://pubmed.ncbi.nlm.nih.gov/21737059/) · [PMCID: PMC3135811](https://pmc.ncbi.nlm.nih.gov/articles/PMC3135811/)
  - OmicsMatic methods: SKAT, rare-variant set tests.

<a id="lee-skato-2012"></a>
- **Seunggeun Lee et al. (2012).** Optimal unified approach for rare-variant association testing. *American Journal of Human Genetics 91:224-237.* [DOI: 10.1016/j.ajhg.2012.06.007](https://doi.org/10.1016/j.ajhg.2012.06.007) · [PMID: 22863193](https://pubmed.ncbi.nlm.nih.gov/22863193/) · [PMCID: PMC3415556](https://pmc.ncbi.nlm.nih.gov/articles/PMC3415556/)
  - OmicsMatic methods: SKAT-O, burden-kernel omnibus tests.

<a id="liu-acat-2019"></a>
- **Yaowu Liu et al. (2019).** ACAT: A fast and powerful p value combination method for rare-variant analysis in sequencing studies. *American Journal of Human Genetics 104:410-421.* [DOI: 10.1016/j.ajhg.2019.01.002](https://doi.org/10.1016/j.ajhg.2019.01.002) · [PMID: 30849328](https://pubmed.ncbi.nlm.nih.gov/30849328/) · [PMCID: PMC6407498](https://pmc.ncbi.nlm.nih.gov/articles/PMC6407498/)
  - OmicsMatic methods: ACAT, ACAT-V, ACAT-O, rare-variant omnibus tests.

<a id="madsen-browning-2009"></a>
- **B. E. Madsen and S. R. Browning (2009).** A groupwise association test for rare mutations using a weighted sum statistic. *PLoS Genetics 5:e1000384.* [DOI: 10.1371/journal.pgen.1000384](https://doi.org/10.1371/journal.pgen.1000384) · [PMID: 19214210](https://pubmed.ncbi.nlm.nih.gov/19214210/) · [PMCID: PMC2633048](https://pmc.ncbi.nlm.nih.gov/articles/PMC2633048/)
  - OmicsMatic methods: weighted burden test, rare variants.

<a id="yang-cojo-2012"></a>
- **Jian Yang et al. (2012).** Conditional and joint multiple-SNP analysis of GWAS summary statistics. *Nature Genetics 44:369-375.* [DOI: 10.1038/ng.2213](https://doi.org/10.1038/ng.2213) · [PMID: 22426310](https://pubmed.ncbi.nlm.nih.gov/22426310/) · [PMCID: PMC3593158](https://pmc.ncbi.nlm.nih.gov/articles/PMC3593158/)
  - OmicsMatic methods: COJO, conditional summary statistics.

<a id="bulik-sullivan-ldsc-2015"></a>
- **Brendan K. Bulik-Sullivan et al. (2015).** LD Score regression distinguishes confounding from polygenicity in genome-wide association studies. *Nature Genetics 47:291-295.* [DOI: 10.1038/ng.3211](https://doi.org/10.1038/ng.3211) · [PMID: 25642630](https://pubmed.ncbi.nlm.nih.gov/25642630/) · [PMCID: PMC4495769](https://pmc.ncbi.nlm.nih.gov/articles/PMC4495769/)
  - OmicsMatic methods: LD Score regression, SNP heritability, confounding intercept.

<a id="bulik-sullivan-rg-2015"></a>
- **Brendan K. Bulik-Sullivan et al. (2015).** An atlas of genetic correlations across human diseases and traits. *Nature Genetics 47:1236-1241.* [DOI: 10.1038/ng.3406](https://doi.org/10.1038/ng.3406) · [PMID: 26414676](https://pubmed.ncbi.nlm.nih.gov/26414676/) · [PMCID: PMC4797329](https://pmc.ncbi.nlm.nih.gov/articles/PMC4797329/)
  - OmicsMatic methods: genetic correlation, bivariate LDSC.

<a id="gamazon-predixcan-2015"></a>
- **Eric R. Gamazon et al. (2015).** A gene-based association method for mapping traits using reference transcriptome data. *Nature Genetics 47:1091-1098.* [DOI: 10.1038/ng.3367](https://doi.org/10.1038/ng.3367) · [PMID: 26258848](https://pubmed.ncbi.nlm.nih.gov/26258848/) · [PMCID: PMC4552594](https://pmc.ncbi.nlm.nih.gov/articles/PMC4552594/)
  - OmicsMatic methods: PrediXcan, genetically predicted expression, TWAS.

<a id="gusev-fusion-2016"></a>
- **Alexander Gusev et al. (2016).** Integrative approaches for large-scale transcriptome-wide association studies. *Nature Genetics 48:245-252.* [DOI: 10.1038/ng.3506](https://doi.org/10.1038/ng.3506) · [PMID: 26854917](https://pubmed.ncbi.nlm.nih.gov/26854917/) · [PMCID: PMC4767558](https://pmc.ncbi.nlm.nih.gov/articles/PMC4767558/)
  - OmicsMatic methods: FUSION TWAS, summary TWAS, PWAS.

<a id="grotzinger-genomic-sem-2019"></a>
- **Andrew D. Grotzinger et al. (2019).** Genomic structural equation modelling provides insights into the multivariate genetic architecture of complex traits. *Nature Human Behaviour 3:513-525.* [DOI: 10.1038/s41562-019-0566-x](https://doi.org/10.1038/s41562-019-0566-x) · [PMID: 30962613](https://pubmed.ncbi.nlm.nih.gov/30962613/) · [PMCID: PMC6520146](https://pmc.ncbi.nlm.nih.gov/articles/PMC6520146/)
  - OmicsMatic methods: Genomic SEM, shared genetic factors.

<a id="euesden-prsice-2015"></a>
- **Jack Euesden, Cathryn M. Lewis, and Paul F. O'Reilly (2015).** PRSice: polygenic risk score software. *Bioinformatics 31:1466-1468.* [DOI: 10.1093/bioinformatics/btu848](https://doi.org/10.1093/bioinformatics/btu848) · [PMID: 25550326](https://pubmed.ncbi.nlm.nih.gov/25550326/) · [PMCID: PMC4410663](https://pmc.ncbi.nlm.nih.gov/articles/PMC4410663/)
  - OmicsMatic methods: polygenic scores, score evaluation.

<a id="thousand-genomes-2015"></a>
- **1000 Genomes Project Consortium (2015).** A global reference for human genetic variation. *Nature 526:68-74.* [DOI: 10.1038/nature15393](https://doi.org/10.1038/nature15393) · [PMID: 26432245](https://pubmed.ncbi.nlm.nih.gov/26432245/) · [PMCID: PMC4750478](https://pmc.ncbi.nlm.nih.gov/articles/PMC4750478/)
  - OmicsMatic methods: 1000 Genomes Phase 3, ancestry-stratified LD reference.

<a id="finucane-sldsc-2015"></a>
- **Hilary K. Finucane et al. (2015).** Partitioning heritability by functional annotation using genome-wide association summary statistics. *Nature Genetics 47:1228-1235.* [DOI: 10.1038/ng.3404](https://doi.org/10.1038/ng.3404) · [PMID: 26414678](https://pubmed.ncbi.nlm.nih.gov/26414678/) · [PMCID: PMC4626285](https://pmc.ncbi.nlm.nih.gov/articles/PMC4626285/)
  - OmicsMatic methods: Stratified LD score regression, Overlapping binary annotation heritability enrichment, GWAS functional-category testing.

<a id="hemerik-goeman-2018"></a>
- **Jesse Hemerik and Jelle Goeman (2018).** Exact testing with random permutations. *TEST 27:811-825.* [DOI: 10.1007/s11749-017-0571-1](https://doi.org/10.1007/s11749-017-0571-1) · [PMID: 30930620](https://pubmed.ncbi.nlm.nih.gov/30930620/) · [PMCID: PMC6405018](https://pmc.ncbi.nlm.nih.gov/articles/PMC6405018/)
  - OmicsMatic methods: Conditional Monte Carlo test validity, Random permutation P values.

## Latent variation and batch correction

<a id="leek-storey-2007"></a>
- **Jeffrey T. Leek and John D. Storey (2007).** Capturing heterogeneity in gene expression studies by surrogate variable analysis. *PLoS Genetics 3:e161.* [DOI: 10.1371/journal.pgen.0030161](https://doi.org/10.1371/journal.pgen.0030161) · [PMID: 17907809](https://pubmed.ncbi.nlm.nih.gov/17907809/) · [PMCID: PMC1994707](https://pmc.ncbi.nlm.nih.gov/articles/PMC1994707/)
  - OmicsMatic methods: SVA, latent confounders.

<a id="stegle-peer-2012"></a>
- **Oliver Stegle et al. (2012).** Using probabilistic estimation of expression residuals (PEER) to obtain increased power and interpretability of gene expression analyses. *Nature Protocols 7:500-507.* [DOI: 10.1038/nprot.2011.457](https://doi.org/10.1038/nprot.2011.457) · [PMID: 22343431](https://pubmed.ncbi.nlm.nih.gov/22343431/) · [PMCID: PMC3398141](https://pmc.ncbi.nlm.nih.gov/articles/PMC3398141/)
  - OmicsMatic methods: PEER, Bayesian factor analysis.

<a id="johnson-combat-2007"></a>
- **W. Evan Johnson, Cheng Li, and Ariel Rabinovic (2007).** Adjusting batch effects in microarray expression data using empirical Bayes methods. *Biostatistics 8:118-127.* [DOI: 10.1093/biostatistics/kxj037](https://doi.org/10.1093/biostatistics/kxj037) · [PMID: 16632515](https://pubmed.ncbi.nlm.nih.gov/16632515/)
  - OmicsMatic methods: ComBat, batch correction, empirical Bayes.

## Causal and mediation analysis

<a id="baron-kenny-1986"></a>
- **Reuben M. Baron and David A. Kenny (1986).** The moderator-mediator variable distinction in social psychological research. *Journal of Personality and Social Psychology 51:1173-1182.* [DOI: 10.1037/0022-3514.51.6.1173](https://doi.org/10.1037/0022-3514.51.6.1173)
  - OmicsMatic methods: mediation, path decomposition.

<a id="sobel-1982"></a>
- **Michael E. Sobel (1982).** Asymptotic confidence intervals for indirect effects in structural equation models. *Sociological Methodology 13:290-312.* [DOI: 10.2307/270723](https://doi.org/10.2307/270723)
  - OmicsMatic methods: Sobel test, indirect-effect inference.

<a id="staiger-stock-1997"></a>
- **Douglas Staiger and James H. Stock (1997).** Instrumental variables regression with weak instruments. *Econometrica 65:557-586.* [DOI: 10.2307/2171753](https://doi.org/10.2307/2171753)
  - OmicsMatic methods: 2SLS, weak instruments, first-stage diagnostics.

<a id="white-1980"></a>
- **Halbert White (1980).** A heteroskedasticity-consistent covariance matrix estimator and a direct test for heteroskedasticity. *Econometrica 48:817-838.* [DOI: 10.2307/1912934](https://doi.org/10.2307/1912934)
  - OmicsMatic methods: heteroskedasticity-robust covariance, robust 2SLS inference.

<a id="bowden-mr-egger-2015"></a>
- **Jack Bowden, George Davey Smith, and Stephen Burgess (2015).** Mendelian randomization with invalid instruments: effect estimation and bias detection through Egger regression. *International Journal of Epidemiology 44:512-525.* [DOI: 10.1093/ije/dyv080](https://doi.org/10.1093/ije/dyv080) · [PMID: 26050253](https://pubmed.ncbi.nlm.nih.gov/26050253/) · [PMCID: PMC4469799](https://pmc.ncbi.nlm.nih.gov/articles/PMC4469799/)
  - OmicsMatic methods: MR-Egger, directional pleiotropy, Egger intercept.

<a id="bowden-weighted-median-2016"></a>
- **Jack Bowden et al. (2016).** Consistent estimation in Mendelian randomization with some invalid instruments using a weighted median estimator. *Genetic Epidemiology 40:304-314.* [DOI: 10.1002/gepi.21965](https://doi.org/10.1002/gepi.21965) · [PMID: 27061298](https://pubmed.ncbi.nlm.nih.gov/27061298/) · [PMCID: PMC4849733](https://pmc.ncbi.nlm.nih.gov/articles/PMC4849733/)
  - OmicsMatic methods: weighted-median MR, invalid instruments.

<a id="hemani-steiger-2017"></a>
- **Gibran Hemani, Kate Tilling, and George Davey Smith (2017).** Orienting the causal relationship between imprecisely measured traits using GWAS summary data. *PLoS Genetics 13:e1007081.* [DOI: 10.1371/journal.pgen.1007081](https://doi.org/10.1371/journal.pgen.1007081) · [PMID: 29149188](https://pubmed.ncbi.nlm.nih.gov/29149188/) · [PMCID: PMC5711033](https://pmc.ncbi.nlm.nih.gov/articles/PMC5711033/)
  - OmicsMatic methods: Steiger directionality, MR orientation.

<a id="zhao-mr-raps-2020"></a>
- **Qingyuan Zhao et al. (2020).** Statistical inference in two-sample summary-data Mendelian randomization using robust adjusted profile score. *Annals of Statistics 48:1742-1769.* [DOI: 10.1214/19-AOS1866](https://doi.org/10.1214/19-AOS1866)
  - OmicsMatic methods: MR-RAPS, weak instruments, idiosyncratic pleiotropy.

<a id="verbanck-mr-presso-2018"></a>
- **Marie Verbanck et al. (2018).** Detection of widespread horizontal pleiotropy in causal relationships inferred from Mendelian randomization. *Nature Genetics 50:693-698.* [DOI: 10.1038/s41588-018-0099-7](https://doi.org/10.1038/s41588-018-0099-7) · [PMID: 29686387](https://pubmed.ncbi.nlm.nih.gov/29686387/) · [PMCID: PMC6083837](https://pmc.ncbi.nlm.nih.gov/articles/PMC6083837/)
  - OmicsMatic methods: MR-PRESSO, pleiotropic outliers.

<a id="burgess-mvmr-2015"></a>
- **Stephen Burgess and Simon G. Thompson (2015).** Multivariable Mendelian randomization: the use of pleiotropic genetic variants to estimate causal effects. *American Journal of Epidemiology 181:251-260.* [DOI: 10.1093/aje/kwu283](https://doi.org/10.1093/aje/kwu283) · [PMID: 25632051](https://pubmed.ncbi.nlm.nih.gov/25632051/) · [PMCID: PMC4325677](https://pmc.ncbi.nlm.nih.gov/articles/PMC4325677/)
  - OmicsMatic methods: multivariable MR, direct causal effects.

<a id="sanderson-mvmr-diagnostics-2021"></a>
- **Eleanor Sanderson, Wesley Spiller, and Jack Bowden (2021).** Testing and correcting for weak and pleiotropic instruments in two-sample multivariable Mendelian randomization. *Statistics in Medicine 40:5434-5452.* [DOI: 10.1002/sim.9133](https://doi.org/10.1002/sim.9133) · [PMID: 34338327](https://pubmed.ncbi.nlm.nih.gov/34338327/) · [PMCID: PMC9479726](https://pmc.ncbi.nlm.nih.gov/articles/PMC9479726/)
  - OmicsMatic methods: multivariable MR diagnostics, conditional instrument strength, pleiotropy heterogeneity.

<a id="zuber-mr2-2023"></a>
- **Verena Zuber et al. (2023).** Multi-response Mendelian randomization: Identification of shared and distinct exposures for multimorbidity and multiple related disease outcomes. *American Journal of Human Genetics 110:1177-1199.* [DOI: 10.1016/j.ajhg.2023.06.005](https://doi.org/10.1016/j.ajhg.2023.06.005) · [PMID: 37419091](https://pubmed.ncbi.nlm.nih.gov/37419091/) · [PMCID: PMC10357504](https://pmc.ncbi.nlm.nih.gov/articles/PMC10357504/)
  - OmicsMatic methods: multi-response MR literature, sparse Bayesian Gaussian-copula MR2 method boundary.

<a id="deng-mrmo-2023"></a>
- **Yangqing Deng et al. (2023).** Two-stage multivariate Mendelian randomization on multiple outcomes with mixed distributions. *Statistical Methods in Medical Research 32:1543-1558.* [DOI: 10.1177/09622802231181220](https://doi.org/10.1177/09622802231181220) · [PMID: 37338962](https://pubmed.ncbi.nlm.nih.gov/37338962/) · [PMCID: PMC10515454](https://pmc.ncbi.nlm.nih.gov/articles/PMC10515454/)
  - OmicsMatic methods: individual-level mixed-response MR literature, MRMO method boundary.

<a id="zhang-multivariate-mr-2026"></a>
- **Yuankai Zhang et al. (2026).** Multivariate Mendelian randomization for joint inferences of correlated outcomes. *European Journal of Epidemiology.* [DOI: 10.1007/s10654-026-01406-1](https://doi.org/10.1007/s10654-026-01406-1) · [PMID: 42207415](https://pubmed.ncbi.nlm.nih.gov/42207415/)
  - OmicsMatic methods: multivariate MR-IVW, joint correlated-outcome inference, multivariate MR-PRESSO.

<a id="burgess-overlap-2016"></a>
- **Stephen Burgess, Neil M. Davies, and Simon G. Thompson (2016).** Bias due to participant overlap in two-sample Mendelian randomization. *Genetic Epidemiology 40:597-608.* [DOI: 10.1002/gepi.21998](https://doi.org/10.1002/gepi.21998) · [PMID: 27625185](https://pubmed.ncbi.nlm.nih.gov/27625185/) · [PMCID: PMC5082560](https://pmc.ncbi.nlm.nih.gov/articles/PMC5082560/)
  - OmicsMatic methods: sample overlap, two-sample MR.

<a id="hemani-mrbase-2018"></a>
- **Gibran Hemani et al. (2018).** The MR-Base platform supports systematic causal inference across the human phenome. *eLife 7:e34408.* [DOI: 10.7554/eLife.34408](https://doi.org/10.7554/eLife.34408) · [PMID: 29846171](https://pubmed.ncbi.nlm.nih.gov/29846171/) · [PMCID: PMC5976434](https://pmc.ncbi.nlm.nih.gov/articles/PMC5976434/)
  - OmicsMatic methods: TwoSampleMR, MR-Base, phenome-wide MR.

## Evidence synthesis

<a id="dersimonian-laird-1986"></a>
- **Rebecca DerSimonian and Nan Laird (1986).** Meta-analysis in clinical trials. *Controlled Clinical Trials 7:177-188.* [DOI: 10.1016/0197-2456(86)90046-2](https://doi.org/10.1016/0197-2456(86)90046-2) · [PMID: 3802833](https://pubmed.ncbi.nlm.nih.gov/3802833/)
  - OmicsMatic methods: random-effects meta-analysis, DerSimonian-Laird.

<a id="paule-mandel-1982"></a>
- **Robert C. Paule and John Mandel (1982).** Consensus values and weighting factors. *Journal of Research of the National Bureau of Standards 87:377-385.* [DOI: 10.6028/jres.087.022](https://doi.org/10.6028/jres.087.022) · [PMID: 34566088](https://pubmed.ncbi.nlm.nih.gov/34566088/) · [PMCID: PMC6768160](https://pmc.ncbi.nlm.nih.gov/articles/PMC6768160/)
  - OmicsMatic methods: Paule-Mandel tau squared, random-effects meta-analysis.

<a id="knapp-hartung-2003"></a>
- **Guido Knapp and Joachim Hartung (2003).** Improved tests for a random effects meta-regression with a single covariate. *Statistics in Medicine 22:2693-2710.* [DOI: 10.1002/sim.1482](https://doi.org/10.1002/sim.1482) · [PMID: 12939780](https://pubmed.ncbi.nlm.nih.gov/12939780/)
  - OmicsMatic methods: Knapp-Hartung inference, meta-regression.

<a id="higgins-thompson-2002"></a>
- **Julian P. T. Higgins and Simon G. Thompson (2002).** Quantifying heterogeneity in a meta-analysis. *Statistics in Medicine 21:1539-1558.* [DOI: 10.1002/sim.1186](https://doi.org/10.1002/sim.1186) · [PMID: 12111919](https://pubmed.ncbi.nlm.nih.gov/12111919/)
  - OmicsMatic methods: I-squared, heterogeneity.

<a id="cochrane-meta-analysis-2024"></a>
- **Jonathan J. Deeks, Julian P. T. Higgins, Douglas G. Altman, Joanne E. McKenzie and Areti Angeliki Veroniki (2024).** Chapter 10: Analysing data and undertaking meta-analyses. *Cochrane Handbook for Systematic Reviews of Interventions, version 6.5; updated November 2024.* [Primary source](https://www.cochrane.org/authors/handbooks-and-manuals/handbook/current/chapter-10)
  - OmicsMatic methods: Prespecified synthesis and sensitivity analyses, Heterogeneity uncertainty and Q-test power, Interpretation with few studies.

## Time series

<a id="kalman-1960"></a>
- **Rudolf E. Kalman (1960).** A new approach to linear filtering and prediction problems. *Journal of Basic Engineering 82:35-45.* [DOI: 10.1115/1.3662552](https://doi.org/10.1115/1.3662552)
  - OmicsMatic methods: Kalman filter, state-space models, diffuse smoothing.

<a id="ljung-box-1978"></a>
- **Greta M. Ljung and George E. P. Box (1978).** On a measure of lack of fit in time series models. *Biometrika 65:297-303.* [DOI: 10.1093/biomet/65.2.297](https://doi.org/10.1093/biomet/65.2.297)
  - OmicsMatic methods: Ljung-Box test, residual diagnostics.

<a id="hyndman-khandakar-2008"></a>
- **Rob J. Hyndman and Yeasmin Khandakar (2008).** Automatic time series forecasting: the forecast package for R. *Journal of Statistical Software 27:1-22.* [DOI: 10.18637/jss.v027.i03](https://doi.org/10.18637/jss.v027.i03)
  - OmicsMatic methods: automatic ARIMA, stepwise order selection.

## Fine mapping, colocalization, and SEM

<a id="wang-susie-2020"></a>
- **Gao Wang, Abhishek Sarkar, Peter Carbonetto, and Matthew Stephens (2020).** A simple new approach to variable selection in regression, with application to genetic fine mapping. *Journal of the Royal Statistical Society B 82:1273-1300.* [DOI: 10.1111/rssb.12388](https://doi.org/10.1111/rssb.12388) · [PMID: 37220626](https://pubmed.ncbi.nlm.nih.gov/37220626/) · [PMCID: PMC10201948](https://pmc.ncbi.nlm.nih.gov/articles/PMC10201948/)
  - OmicsMatic methods: SuSiE, credible sets, fine mapping.

<a id="giambartolomei-coloc-2014"></a>
- **Claudia Giambartolomei et al. (2014).** Bayesian test for colocalisation between pairs of genetic association studies using summary statistics. *PLoS Genetics 10:e1004383.* [DOI: 10.1371/journal.pgen.1004383](https://doi.org/10.1371/journal.pgen.1004383) · [PMID: 24830394](https://pubmed.ncbi.nlm.nih.gov/24830394/) · [PMCID: PMC4022491](https://pmc.ncbi.nlm.nih.gov/articles/PMC4022491/)
  - OmicsMatic methods: coloc, H0-H4 hypotheses.

<a id="wallace-coloc-2021"></a>
- **Chris Wallace (2021).** A more accurate method for colocalisation analysis allowing for multiple causal variants. *PLoS Genetics 17:e1009440.* [DOI: 10.1371/journal.pgen.1009440](https://doi.org/10.1371/journal.pgen.1009440) · [PMID: 34587156](https://pubmed.ncbi.nlm.nih.gov/34587156/) · [PMCID: PMC8504726](https://pmc.ncbi.nlm.nih.gov/articles/PMC8504726/)
  - OmicsMatic methods: SuSiE colocalization, multiple causal variants.

<a id="wright-1921"></a>
- **Sewall Wright (1921).** Correlation and causation. *Journal of Agricultural Research 20:557-585.* [Primary source](https://naldc.nal.usda.gov/catalog/IND43966364)
  - OmicsMatic methods: path analysis, structural equation models.

<a id="mcardle-mcdonald-1984"></a>
- **John J. McArdle and Roderick P. McDonald (1984).** Some algebraic properties of the Reticular Action Model for moment structures. *British Journal of Mathematical and Statistical Psychology 37:234-251.* [DOI: 10.1111/j.2044-8317.1984.tb00802.x](https://doi.org/10.1111/j.2044-8317.1984.tb00802.x) · [PMID: 6509005](https://pubmed.ncbi.nlm.nih.gov/6509005/)
  - OmicsMatic methods: RAM formulation, SEM covariance structure.

<a id="enders-bandalos-2001"></a>
- **Craig K. Enders and Deborah L. Bandalos (2001).** The relative performance of full information maximum likelihood estimation for missing data in structural equation models. *Structural Equation Modeling 8:430-457.* [DOI: 10.1207/S15328007SEM0803_5](https://doi.org/10.1207/S15328007SEM0803_5)
  - OmicsMatic methods: FIML, SEM missing data.

<a id="katsikatsou-2012"></a>
- **Myrsini Katsikatsou et al. (2012).** Pairwise likelihood estimation for factor analysis models with ordinal data. *Computational Statistics and Data Analysis 56:4243-4258.* [DOI: 10.1016/j.csda.2012.04.010](https://doi.org/10.1016/j.csda.2012.04.010)
  - OmicsMatic methods: ordinal pairwise likelihood, ordinal SEM.

## Enrichment and annotation

<a id="subramanian-gsea-2005"></a>
- **Aravind Subramanian et al. (2005).** Gene set enrichment analysis: a knowledge-based approach for interpreting genome-wide expression profiles. *Proceedings of the National Academy of Sciences 102:15545-15550.* [DOI: 10.1073/pnas.0506580102](https://doi.org/10.1073/pnas.0506580102) · [PMID: 16199517](https://pubmed.ncbi.nlm.nih.gov/16199517/) · [PMCID: PMC1239896](https://pmc.ncbi.nlm.nih.gov/articles/PMC1239896/)
  - OmicsMatic methods: GSEA, gene-set enrichment.

<a id="wu-camera-2012"></a>
- **Di Wu and Gordon K. Smyth (2012).** Camera: a competitive gene set test accounting for inter-gene correlation. *Nucleic Acids Research 40:e133.* [DOI: 10.1093/nar/gks461](https://doi.org/10.1093/nar/gks461) · [PMID: 22638577](https://pubmed.ncbi.nlm.nih.gov/22638577/) · [PMCID: PMC3458527](https://pmc.ncbi.nlm.nih.gov/articles/PMC3458527/)
  - OmicsMatic methods: CAMERA, correlation-aware enrichment.

<a id="deleeuw-magma-2015"></a>
- **Christiaan A. de Leeuw et al. (2015).** MAGMA: generalized gene-set analysis of GWAS data. *PLoS Computational Biology 11:e1004219.* [DOI: 10.1371/journal.pcbi.1004219](https://doi.org/10.1371/journal.pcbi.1004219) · [PMID: 25885710](https://pubmed.ncbi.nlm.nih.gov/25885710/) · [PMCID: PMC4401657](https://pmc.ncbi.nlm.nih.gov/articles/PMC4401657/)
  - OmicsMatic methods: MAGMA, GWAS gene-set analysis.

<a id="frankish-gencode-2021"></a>
- **Adam Frankish et al. (2021).** GENCODE 2021. *Nucleic Acids Research 49:D916-D923.* [DOI: 10.1093/nar/gkaa1087](https://doi.org/10.1093/nar/gkaa1087) · [PMID: 33270111](https://pubmed.ncbi.nlm.nih.gov/33270111/) · [PMCID: PMC7778937](https://pmc.ncbi.nlm.nih.gov/articles/PMC7778937/)
  - OmicsMatic methods: GENCODE transcript-to-gene annotation, reference genome annotation.

<a id="go-consortium-2021"></a>
- **Gene Ontology Consortium (2021).** The Gene Ontology resource: enriching a GOld mine. *Nucleic Acids Research 49:D325-D334.* [DOI: 10.1093/nar/gkaa1113](https://doi.org/10.1093/nar/gkaa1113) · [PMID: 33290552](https://pubmed.ncbi.nlm.nih.gov/33290552/)
  - OmicsMatic methods: Gene Ontology, functional annotation.

<a id="milacic-reactome-2024"></a>
- **Marija Milacic et al. (2024).** The Reactome Pathway Knowledgebase 2024. *Nucleic Acids Research 52:D672-D678.* [DOI: 10.1093/nar/gkad1025](https://doi.org/10.1093/nar/gkad1025) · [PMID: 37941124](https://pubmed.ncbi.nlm.nih.gov/37941124/) · [PMCID: PMC10767911](https://pmc.ncbi.nlm.nih.gov/articles/PMC10767911/)
  - OmicsMatic methods: Reactome pathways, curated pathway knowledge.

## High-throughput inference

<a id="smyth-limma-2004"></a>
- **Gordon K. Smyth (2004).** Linear models and empirical Bayes methods for assessing differential expression in microarray experiments. *Statistical Applications in Genetics and Molecular Biology 3:Article 3.* [DOI: 10.2202/1544-6115.1027](https://doi.org/10.2202/1544-6115.1027) · [PMID: 16646809](https://pubmed.ncbi.nlm.nih.gov/16646809/)
  - OmicsMatic methods: moderated differential analysis, empirical-Bayes variance shrinkage.

<a id="law-voom-2014"></a>
- **Charity W. Law, Yunshun Chen, Wei Shi, and Gordon K. Smyth (2014).** voom: precision weights unlock linear model analysis tools for RNA-seq read counts. *Genome Biology 15:R29.* [DOI: 10.1186/gb-2014-15-2-r29](https://doi.org/10.1186/gb-2014-15-2-r29) · [PMID: 24485249](https://pubmed.ncbi.nlm.nih.gov/24485249/) · [PMCID: PMC4053721](https://pmc.ncbi.nlm.nih.gov/articles/PMC4053721/)
  - OmicsMatic methods: voom mean-variance precision weights, count differential analysis.

<a id="robinson-edger-2010"></a>
- **Mark D. Robinson, Davis J. McCarthy, and Gordon K. Smyth (2010).** edgeR: a Bioconductor package for differential expression analysis of digital gene expression data. *Bioinformatics 26:139-140.* [DOI: 10.1093/bioinformatics/btp616](https://doi.org/10.1093/bioinformatics/btp616) · [PMID: 19910308](https://pubmed.ncbi.nlm.nih.gov/19910308/) · [PMCID: PMC2796818](https://pmc.ncbi.nlm.nih.gov/articles/PMC2796818/)
  - OmicsMatic methods: negative-binomial differential analysis, dispersion moderation.

<a id="love-deseq2-2014"></a>
- **Michael I. Love, Wolfgang Huber, and Simon Anders (2014).** Moderated estimation of fold change and dispersion for RNA-seq data with DESeq2. *Genome Biology 15:550.* [DOI: 10.1186/s13059-014-0550-8](https://doi.org/10.1186/s13059-014-0550-8) · [PMID: 25516281](https://pubmed.ncbi.nlm.nih.gov/25516281/) · [PMCID: PMC4302049](https://pmc.ncbi.nlm.nih.gov/articles/PMC4302049/)
  - OmicsMatic methods: median-ratio count normalization, dispersion shrinkage.

<a id="pedersen-combp-2012"></a>
- **Brent S. Pedersen, David A. Schwartz, Ivana V. Yang, and Katerina J. Kechris (2012).** Comb-p: software for combining, analyzing, grouping and correcting spatially correlated P-values. *Bioinformatics 28:2986-2988.* [DOI: 10.1093/bioinformatics/bts545](https://doi.org/10.1093/bioinformatics/bts545) · [PMID: 22954632](https://pubmed.ncbi.nlm.nih.gov/22954632/) · [PMCID: PMC3496335](https://pmc.ncbi.nlm.nih.gov/articles/PMC3496335/)
  - OmicsMatic methods: spatially correlated p-values, region-level genomic inference.

<a id="peters-dmrcate-2015"></a>
- **Timothy J. Peters et al. (2015).** De novo identification of differentially methylated regions in the human genome. *Epigenetics and Chromatin 8:6.* [DOI: 10.1186/1756-8935-8-6](https://doi.org/10.1186/1756-8935-8-6) · [PMID: 25972926](https://pubmed.ncbi.nlm.nih.gov/25972926/) · [PMCID: PMC4429355](https://pmc.ncbi.nlm.nih.gov/articles/PMC4429355/)
  - OmicsMatic methods: differentially methylated regions, spatial methylation smoothing.

## Multiple testing

<a id="ignatiadis-ihw-2016"></a>
- **Nikolaos Ignatiadis, Bernd Klaus, Judith B. Zaugg, and Wolfgang Huber (2016).** Data-driven hypothesis weighting increases detection power in genome-scale multiple testing. *Nature Methods 13:577-580.* [DOI: 10.1038/nmeth.3885](https://doi.org/10.1038/nmeth.3885) · [PMID: 27240256](https://pubmed.ncbi.nlm.nih.gov/27240256/) · [PMCID: PMC4930141](https://pmc.ncbi.nlm.nih.gov/articles/PMC4930141/)
  - OmicsMatic methods: independent hypothesis weighting, covariate-adaptive FDR.

<a id="meinshausen-hierarchy-2008"></a>
- **Nicolai Meinshausen (2008).** Hierarchical testing of variable importance. *Biometrika 95:265-278.* [DOI: 10.1093/biomet/asn007](https://doi.org/10.1093/biomet/asn007)
  - OmicsMatic methods: hierarchical testing, familywise error control.

<a id="benjamini-yekutieli-2001"></a>
- **Yoav Benjamini and Daniel Yekutieli (2001).** The control of the false discovery rate in multiple testing under dependency. *The Annals of Statistics 29(4):1165-1188.* [DOI: 10.1214/aos/1013699998](https://doi.org/10.1214/aos/1013699998)
  - OmicsMatic methods: BY harmonic correction, Full declared testing families.

## Missing-data inference

<a id="rubin-mi-1987"></a>
- **Donald B. Rubin (1987).** Multiple Imputation for Nonresponse in Surveys. *Wiley.* [DOI: 10.1002/9780470316696](https://doi.org/10.1002/9780470316696)
  - OmicsMatic methods: multiple-imputation pooling, missing-data uncertainty.

<a id="vanbuuren-mice-2011"></a>
- **Stef van Buuren and Karin Groothuis-Oudshoorn (2011).** mice: Multivariate Imputation by Chained Equations in R. *Journal of Statistical Software 45:1-67.* [DOI: 10.18637/jss.v045.i03](https://doi.org/10.18637/jss.v045.i03)
  - OmicsMatic methods: chained-equations imputation, mixed-type imputers.

<a id="barnard-rubin-1999"></a>
- **John Barnard and Donald B. Rubin (1999).** Small-sample degrees of freedom with multiple imputation. *Biometrika 86:948-955.* [DOI: 10.1093/biomet/86.4.948](https://doi.org/10.1093/biomet/86.4.948)
  - OmicsMatic methods: Barnard-Rubin degrees of freedom, finite-sample multiple-imputation inference.

## Software interfaces

<a id="wilkinson-rogers-1973"></a>
- **G. N. Wilkinson and C. E. Rogers (1973).** Symbolic description of factorial models for analysis of variance. *Applied Statistics 22:392-399.* [DOI: 10.2307/2346786](https://doi.org/10.2307/2346786)
  - OmicsMatic methods: statistical formulas, model matrices.

## Censored, ordinal, and sampling models

<a id="tobin-1958"></a>
- **James Tobin (1958).** Estimation of Relationships for Limited Dependent Variables. *Econometrica 26:24-36.* [DOI: 10.2307/1907382](https://doi.org/10.2307/1907382)
  - OmicsMatic methods: Tobit, censored Gaussian regression.

<a id="kalbfleisch-prentice-2002"></a>
- **John D. Kalbfleisch and Ross L. Prentice (2002).** The Statistical Analysis of Failure Time Data, Second Edition. *Wiley Series in Probability and Statistics.* [DOI: 10.1002/9781118032985](https://doi.org/10.1002/9781118032985)
  - OmicsMatic methods: parametric survival regression, accelerated failure time, censored likelihood.

<a id="mccullagh-1980"></a>
- **Peter McCullagh (1980).** Regression Models for Ordinal Data. *Journal of the Royal Statistical Society Series B 42:109-142.* [DOI: 10.1111/j.2517-6161.1980.tb01109.x](https://doi.org/10.1111/j.2517-6161.1980.tb01109.x)
  - OmicsMatic methods: ordered logit, ordered probit, cumulative link models.

<a id="king-zeng-2001"></a>
- **Gary King and Langche Zeng (2001).** Logistic Regression in Rare Events Data. *Political Analysis 9:137-163.* [Primary source](https://gking.harvard.edu/files/0s.pdf)
  - OmicsMatic methods: rare-events logistic regression, coefficient-bias correction.

<a id="king-zeng-isq-2001"></a>
- **Gary King and Langche Zeng (2001).** Explaining Rare Events in International Relations. *International Studies Quarterly 45:693-715.* [Primary source](https://gking.harvard.edu/files/gking/files/baby0s.pdf)
  - OmicsMatic methods: case-control sampling, population-prevalence correction.

<a id="lumley-survey-2004"></a>
- **Thomas Lumley (2004).** Analysis of Complex Survey Samples. *Journal of Statistical Software 9(8):1-19.* [DOI: 10.18637/jss.v009.i08](https://doi.org/10.18637/jss.v009.i08)
  - OmicsMatic methods: survey regression, sampling weights, stratified PSU Taylor covariance.

## Reproducible omics follow-up

<a id="sandve-reproducibility-2013"></a>
- **Geir Kjetil Sandve et al. (2013).** Ten Simple Rules for Reproducible Computational Research. *PLoS Computational Biology 9:e1003285.* [DOI: 10.1371/journal.pcbi.1003285](https://doi.org/10.1371/journal.pcbi.1003285)
  - OmicsMatic methods: Configuration provenance, Reproducible workflows.

<a id="w3c-prov-o-2013"></a>
- **W3C Provenance Working Group (2013).** PROV-O: The PROV Ontology. *W3C Recommendation.* [Primary source](https://www.w3.org/TR/prov-o/)
  - OmicsMatic methods: Evidence provenance graph, Entity-activity-agent lineage.

<a id="fda-electronic-records-2024"></a>
- **U.S. Food and Drug Administration (2024).** Electronic Systems, Electronic Records, and Electronic Signatures in Clinical Investigations: Questions and Answers. *Final Guidance, October 2024.* [Primary source](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/electronic-systems-electronic-records-and-electronic-signatures-clinical-investigations-questions)
  - OmicsMatic methods: Electronic audit trails, Searchable electronic records.

<a id="hhs-hipaa-deidentification-2012"></a>
- **U.S. Department of Health and Human Services, Office for Civil Rights (2012).** Guidance Regarding Methods for De-identification of Protected Health Information in Accordance with the HIPAA Privacy Rule. *HHS Office for Civil Rights guidance.* [Primary source](https://www.hhs.gov/hipaa/for-professionals/special-topics/de-identification/index.html)
  - OmicsMatic methods: Safe Harbor and Expert Determination, Health-data disclosure boundary.

<a id="nist-genai-profile-2024"></a>
- **National Institute of Standards and Technology (2024).** Artificial Intelligence Risk Management Framework: Generative Artificial Intelligence Profile. *NIST AI 600-1.* [DOI: 10.6028/NIST.AI.600-1](https://doi.org/10.6028/NIST.AI.600-1)
  - OmicsMatic methods: Generative-AI data provenance, Data-flow testing and documentation.

<a id="wang-annovar-2010"></a>
- **Kai Wang, Mingyao Li, and Hakon Hakonarson (2010).** ANNOVAR: functional annotation of genetic variants from high-throughput sequencing data. *Nucleic Acids Research 38:e164.* [DOI: 10.1093/nar/gkq603](https://doi.org/10.1093/nar/gkq603)
  - OmicsMatic methods: ANNOVAR consequence adapter.

<a id="mclaren-vep-2016"></a>
- **William McLaren et al. (2016).** The Ensembl Variant Effect Predictor. *Genome Biology 17:122.* [DOI: 10.1186/s13059-016-0974-4](https://doi.org/10.1186/s13059-016-0974-4)
  - OmicsMatic methods: VEP consequence annotation, Variant consequence import.

<a id="langfelder-wgcna-2008"></a>
- **Peter Langfelder and Steve Horvath (2008).** WGCNA: an R package for weighted correlation network analysis. *BMC Bioinformatics 9:559.* [DOI: 10.1186/1471-2105-9-559](https://doi.org/10.1186/1471-2105-9-559)
  - OmicsMatic methods: Signed coexpression modules, Module eigengenes and hubs.

<a id="langfelder-preservation-2011"></a>
- **Peter Langfelder et al. (2011).** Is My Network Module Preserved and Reproducible?. *PLoS Computational Biology 7:e1001057.* [DOI: 10.1371/journal.pcbi.1001057](https://doi.org/10.1371/journal.pcbi.1001057)
  - OmicsMatic methods: WGCNA module preservation.

<a id="meinshausen-networks-2006"></a>
- **Nicolai Meinshausen and Peter Buhlmann (2006).** High-dimensional graphs and variable selection with the Lasso. *Annals of Statistics 34:1436-1462.* [DOI: 10.1214/009053606000000281](https://doi.org/10.1214/009053606000000281)
  - OmicsMatic methods: Gaussian neighborhood selection, Sparse conditional association.

<a id="huynh-thu-genie3-2010"></a>
- **VÃ¢n Anh Huynh-Thu et al. (2010).** Inferring Regulatory Networks from Expression Data Using Tree-Based Methods. *PLoS ONE 5:e12776.* [DOI: 10.1371/journal.pone.0012776](https://doi.org/10.1371/journal.pone.0012776)
  - OmicsMatic methods: GENIE3 regulatory networks.

<a id="liu-carnival-2019"></a>
- **Anika Liu et al. (2019).** From expression footprints to causal pathways: contextualizing large signaling networks with CARNIVAL. *npj Systems Biology and Applications 5:40.* [DOI: 10.1038/s41540-019-0118-z](https://doi.org/10.1038/s41540-019-0118-z)
  - OmicsMatic methods: Inverse CARNIVAL, Contextual signaling networks.

<a id="fisher-correlation-1921"></a>
- **R. A. Fisher (1921).** On the probable error of a coefficient of correlation deduced from a small sample. *Metron 1:3-32.* [Primary source](https://digital.library.adelaide.edu.au/dspace/handle/2440/15169)
  - OmicsMatic methods: Fisher correlation transformation, Independent-group differential correlation.

<a id="brin-pagerank-1998"></a>
- **Sergey Brin and Lawrence Page (1998).** The anatomy of a large-scale hypertextual Web search engine. *Computer Networks and ISDN Systems 30:107-117.* [Primary source](https://snap.stanford.edu/class/cs224w-readings/Brin98Anatomy.pdf)
  - OmicsMatic methods: Random walk with restart, Network propagation.

## Single-cell and spatial omics

<a id="crowell-muscat-2020"></a>
- **Helena L. Crowell et al. (2020).** muscat detects subpopulation-specific state transitions from multi-sample multi-condition single-cell transcriptomics data. *Nature Communications 11:6077.* [DOI: 10.1038/s41467-020-19894-4](https://doi.org/10.1038/s41467-020-19894-4) · [PMID: 33257685](https://pubmed.ncbi.nlm.nih.gov/33257685/)
  - OmicsMatic methods: Biological-replicate pseudobulk design, Differential state interpretation.

<a id="squair-pseudoreplication-2021"></a>
- **Jordan W. Squair et al. (2021).** Confronting false discoveries in single-cell differential expression. *Nature Communications 12:5692.* [DOI: 10.1038/s41467-021-25960-2](https://doi.org/10.1038/s41467-021-25960-2) · [PMID: 34584091](https://pubmed.ncbi.nlm.nih.gov/34584091/)
  - OmicsMatic methods: Donor-aware single-cell inference, Pseudoreplication limits.

<a id="palla-squidpy-2022"></a>
- **Giovanni Palla et al. (2022).** Squidpy: a scalable framework for spatial omics analysis. *Nature Methods 19:171-178.* [DOI: 10.1038/s41592-021-01358-2](https://doi.org/10.1038/s41592-021-01358-2) · [PMCID: PMC8828470](https://pmc.ncbi.nlm.nih.gov/articles/PMC8828470/)
  - OmicsMatic methods: Spatial graph and neighborhood analysis context, Spatial omics workflow design.

<a id="moran-1950"></a>
- **P. A. P. Moran (1950).** Notes on continuous stochastic phenomena. *Biometrika 37:17-23.* [DOI: 10.1093/biomet/37.1-2.17](https://doi.org/10.1093/biomet/37.1-2.17) · [PMID: 15420245](https://pubmed.ncbi.nlm.nih.gov/15420245/)
  - OmicsMatic methods: Moran spatial autocorrelation.

<a id="geary-1954"></a>
- **R. C. Geary (1954).** The Contiguity Ratio and Statistical Mapping. *The Incorporated Statistician 5(3).* [DOI: 10.2307/2986645](https://doi.org/10.2307/2986645)
  - OmicsMatic methods: Geary spatial autocorrelation.

## Prediction and risk-score reporting

<a id="wand-prs-reporting-2021"></a>
- **Hannah Wand, Samuel A. Lambert, et al. (2021).** Improving reporting standards for polygenic scores in risk prediction studies. *Nature 591, 211-219.* [DOI: 10.1038/s41586-021-03243-6](https://doi.org/10.1038/s41586-021-03243-6) · [PMID: 33692554](https://pubmed.ncbi.nlm.nih.gov/33692554/) · [PMCID: PMC8609771](https://pmc.ncbi.nlm.nih.gov/articles/PMC8609771/)
  - OmicsMatic methods: PRS reporting standards, Development and validation populations, Score reporting and limitations.

## Set overlap visualization

<a id="lex-upset-2014"></a>
- **Alexander Lex, Nils Gehlenborg, Hendrik Strobelt, Romain Vuillemot and Hanspeter Pfister (2014).** UpSet: Visualization of Intersecting Sets. *IEEE Transactions on Visualization and Computer Graphics 20(12), 1983-1992.* [DOI: 10.1109/TVCG.2014.2346248](https://doi.org/10.1109/TVCG.2014.2346248)
  - OmicsMatic methods: Exclusive intersection membership matrices, Multi-cohort overlap displays.

## Genotype quality control

<a id="wigginton-hwe-2005"></a>
- **Wigginton JE, Cutler DJ, Abecasis GR (2005).** A note on exact tests of Hardy-Weinberg equilibrium. *American Journal of Human Genetics 76(5), 887-893.* [DOI: 10.1086/429864](https://doi.org/10.1086/429864) · [PMID: 15789306](https://pubmed.ncbi.nlm.nih.gov/15789306/) · [PMCID: PMC1199378](https://pmc.ncbi.nlm.nih.gov/articles/PMC1199378/)
  - OmicsMatic methods: Exact hard-call HWE, Conditional genotype-count enumeration.

<a id="plink2-qc-documentation"></a>
- **PLINK developers (2026).** PLINK 2.0 input filtering and basic statistics. *Official documentation, accessed 2026-10-23.* [Primary source](https://www.cog-genomics.org/plink/2.0/filter#hwe)
  - OmicsMatic methods: Prespecified HWE thresholds and sample scope, Founder and control subset considerations, Distinct sex chromosome procedures.

## Genotype input formats

<a id="bgen-layout2-specification"></a>
- **BGEN developers (2026).** The BGEN format: layout 2 specification. *Official format specification, accessed 2026-10-23.* [Primary source](https://www.chg.ox.ac.uk/~gav/bgen_format/spec/latest.html)
  - OmicsMatic methods: Per-sample ploidy and missingness, Genotype probability representations.

<a id="hts-vcf-bcf-specification"></a>
- **Global Alliance for Genomics and Health file-format contributors (2026).** The Variant Call Format specification: VCFv4.5 and BCFv2.2. *Official HTS specification, e821e4f, 2026-02-25.* [Primary source](https://samtools.github.io/hts-specs/VCFv4.5.pdf)
  - OmicsMatic methods: Variant and genotype field semantics, BCF framing, sample dimensions and BGZF representation.

## Genetic association calibration

<a id="regenie-binary-methods"></a>
- **REGENIE developers (2026).** REGENIE overview: quantitative and binary association methods. *Official documentation, accessed 2026-10-23.* [Primary source](https://rgcgithub.github.io/regenie/overview/)
  - OmicsMatic methods: Design-specific binary testing, Firth and saddlepoint corrections, Separation of QC from association calibration.

## Bias-reduced logistic association

<a id="firth-bias-reduction-1993"></a>
- **David Firth (1993).** Bias reduction of maximum likelihood estimates. *Biometrika 80(1):27-38.* [DOI: 10.1093/biomet/80.1.27](https://doi.org/10.1093/biomet/80.1.27)
  - OmicsMatic methods: Jeffreys penalized likelihood, First-order bias reduction.

<a id="heinze-schemper-separation-2002"></a>
- **Georg Heinze and Michael Schemper (2002).** A solution to the problem of separation in logistic regression. *Statistics in Medicine 21(16):2409-2419.* [Primary source](https://pubmed.ncbi.nlm.nih.gov/12210625/) · [PMID: 12210625](https://pubmed.ncbi.nlm.nih.gov/12210625/)
  - OmicsMatic methods: Separated logistic data, Penalized likelihood inference.

<a id="logistf-reference-documentation"></a>
- **Georg Heinze and logistf contributors (2026).** logistf: Firth logistic regression and constrained penalized likelihood tests. *Official CRAN package documentation, accessed 2026-10-23.* [Primary source](https://search.r-project.org/CRAN/refmans/logistf/html/logistftest.html)
  - OmicsMatic methods: Full and constrained penalized fits, Independent reference implementation.

## Exact conditional binary inference

<a id="r-fisher-exact-documentation"></a>
- **R Core Team and survival contributors (2026).** Fisher's exact test: fixed margins and probability-ordering two-sided tails. *R reference documentation.* [Primary source](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/fisher.test.html)
  - OmicsMatic methods: Finite conditional binary-genotype reference, Two-sided tail definition.

<a id="r-survival-clogit-documentation"></a>
- **R Core Team and survival contributors (2026).** Conditional logistic regression: exact conditional likelihood. *R reference documentation.* [Primary source](https://search.r-project.org/CRAN/refmans/survival/html/clogit.html)
  - OmicsMatic methods: Stratum case-count conditioning, Distinguish conditional likelihood from finite test calibration.

## Regularized prediction and independent score evaluation

<a id="friedman-glmnet-2010"></a>
- **Jerome Friedman, Trevor Hastie and Robert Tibshirani (2010).** Regularization Paths for Generalized Linear Models via Coordinate Descent. *Journal of Statistical Software 33(1), 1-22.* [DOI: 10.18637/jss.v033.i01](https://doi.org/10.18637/jss.v033.i01) · [PMID: 20808728](https://pubmed.ncbi.nlm.nih.gov/20808728/) · [PMCID: PMC2929880](https://pmc.ncbi.nlm.nih.gov/articles/PMC2929880/)
  - OmicsMatic methods: Binomial elastic-net objective, Coordinate optimization, Prediction penalty selection.

## Adjusted subgroup effect modification

<a id="altman-bland-interaction-2003"></a>
- **Douglas G Altman and J Martin Bland (2003).** Interaction revisited: the difference between two estimates. *BMJ 326(7382):219.* [Primary source](https://pubmed.ncbi.nlm.nih.gov/12543843/) · [PMID: 12543843](https://pubmed.ncbi.nlm.nih.gov/12543843/) · [PMCID: PMC1125071](https://pmc.ncbi.nlm.nih.gov/articles/PMC1125071/)
  - OmicsMatic methods: Compare effects rather than subgroup P values.

<a id="keller-interaction-confounding-2014"></a>
- **Matthew C Keller (2014).** Gene x environment interaction studies have not properly controlled for potential confounders: the problem and the (simple) solution. *Biological Psychiatry 75(1):18-24.* [Primary source](https://pubmed.ncbi.nlm.nih.gov/24135711/) · [PMID: 24135711](https://pubmed.ncbi.nlm.nih.gov/24135711/) · [PMCID: PMC3859520](https://pmc.ncbi.nlm.nih.gov/articles/PMC3859520/)
  - OmicsMatic methods: Covariate by feature and covariate by group interaction terms.

## Genotype sample quality control

<a id="manichaikul-king-2010"></a>
- **Ani Manichaikul et al. (2010).** Robust relationship inference in genome-wide association studies. *Bioinformatics 26(22):2867-2873.* [Primary source](https://pubmed.ncbi.nlm.nih.gov/20926424/) · [PMID: 20926424](https://pubmed.ncbi.nlm.nih.gov/20926424/) · [PMCID: PMC3025716](https://pmc.ncbi.nlm.nih.gov/articles/PMC3025716/)
  - OmicsMatic methods: KING between-family estimator, Missing-aware genotype pair counts.

<a id="price-pca-stratification-2006"></a>
- **Alkes L Price et al. (2006).** Principal components analysis corrects for stratification in genome-wide association studies. *Nature Genetics 38:904-909.* [Primary source](https://www.nature.com/articles/ng1847) · [PMID: 16862161](https://pubmed.ncbi.nlm.nih.gov/16862161/)
  - OmicsMatic methods: Genotype principal components, Population structure adjustment.

## Human genome coordinates and ploidy

<a id="grc-human-par-intervals"></a>
- **Genome Reference Consortium (2026).** Human Genome Overview: GRCh38 and GRCh37 pseudoautosomal regions. *Official reference assembly documentation, accessed 2026-10-25.* [Primary source](https://www.ncbi.nlm.nih.gov/grc/human)
  - OmicsMatic methods: Build-specific 1-based closed X and Y PAR intervals, Chromosome-coordinate stability across patch releases.

<a id="plink2-human-ploidy-documentation"></a>
- **PLINK developers (2026).** PLINK 2.0 human chromosome ploidy and dosage input. *Official documentation, accessed 2026-10-25.* [Primary source](https://www.cog-genomics.org/plink/2.0/input)
  - OmicsMatic methods: Explicit haploid versus diploid dosage interpretation, Sex-chromosome and pseudoautosomal input handling.

## Exact statistical inference

<a id="clopper-pearson-1934"></a>
- **C. J. Clopper and E. S. Pearson (1934).** The Use of Confidence or Fiducial Limits Illustrated in the Case of the Binomial. *Biometrika 26(4):404-413.* [DOI: 10.1093/biomet/26.4.404](https://doi.org/10.1093/biomet/26.4.404)
  - OmicsMatic methods: Exact binomial confidence intervals, Conditional Monte Carlo tail precision.

## Rare-variant frequency eligibility

<a id="gnomad-v4-1-allele-number"></a>
- **Katherine Chao, Michael Wilson, Julia Goodrich and the gnomAD Production Team (2024).** gnomAD v4.1: allele numbers across called sites and joint frequencies. *gnomAD release documentation.* [Primary source](https://gnomad.broadinstitute.org/news/2024-04-gnomad-v4-1/)
  - OmicsMatic methods: Variant and nonvariant site coverage, Explicit allele-number provenance, Absence versus zero allele count.

<a id="regenie-mask-frequency-documentation"></a>
- **REGENIE developers (2026).** REGENIE documentation: external AAF file and mask definitions. *Official software documentation.* [Primary source](https://rgcgithub.github.io/regenie/options/)
  - OmicsMatic methods: External ALT allele frequencies, Prespecified functional masks, ALT frequency versus minor allele frequency.

## Survival association and diagnostics

<a id="r-coxph-survival-documentation"></a>
- **Terry M. Therneau and survival contributors (2026).** survival: Cox proportional hazards regression and survival endpoint documentation. *Official R package documentation.* [Primary source](https://stat.ethz.ch/R-manual/R-devel/library/survival/html/coxph.html)
  - OmicsMatic methods: Partial likelihood and tie methods, Delayed entry and baseline strata, Offsets and interpretation limits.

<a id="r-cox-zph-documentation"></a>
- **Terry M. Therneau and survival contributors (2026).** survival: proportional-hazards efficient score tests and transforms. *Official R package documentation and implementation.* [Primary source](https://stat.ethz.ch/R-manual/R-devel/library/survival/html/cox.zph.html)
  - OmicsMatic methods: Fitted-coefficient nuisance adjustment, Coefficient-specific and global diagnostics, Explicit log-time transformation.

<a id="chen-ibrahim-shao-2009"></a>
- **Ming-Hui Chen, Joseph G. Ibrahim and Qi-Man Shao (2009).** Maximum likelihood inference for the Cox regression model with applications to missing covariates. *Journal of Multivariate Analysis 100:2018-2030.* [Primary source](https://pmc.ncbi.nlm.nih.gov/articles/PMC2744117/)
  - OmicsMatic methods: Finite MPLE existence and full rank, Positive risk-contrast balance, Monotone partial likelihood.

<a id="heinze-schemper-2001-cox"></a>
- **Georg Heinze and Michael Schemper (2001).** A solution to the problem of monotone likelihood in Cox regression. *Biometrics 57:114-119.* [Primary source](https://pubmed.ncbi.nlm.nih.gov/11252585/)
  - OmicsMatic methods: Monotone likelihood and infinite coefficient estimates, Bias-reduced Cox methods are separate estimators, not silently substituted.

## Prediction model selection and evaluation

<a id="varma-simon-nested-cv-2006"></a>
- **Sudhir Varma and Richard Simon (2006).** Bias in error estimation when using cross-validation for model selection. *BMC Bioinformatics 7, 91.* [DOI: 10.1186/1471-2105-7-91](https://doi.org/10.1186/1471-2105-7-91)
  - OmicsMatic methods: Nested model selection, Repeat all development steps inside validation folds.

## Prediction validation implementation guidance

<a id="sklearn-grouped-nested-cv"></a>
- **scikit-learn developers (2026).** Cross-validation: nested model selection and grouped observations. *scikit-learn official documentation.* [Primary source](https://scikit-learn.org/stable/modules/cross_validation.html#cross-validation-iterators-for-grouped-data)
  - OmicsMatic methods: Group boundaries, Nested cross-validation, Independent test set.

## Frozen genetic score application

<a id="plink2-linear-scoring"></a>
- **PLINK 2 developers (2026).** Linear scoring. *PLINK 2 official documentation, accessed 2026-10-25.* [Primary source](https://www.cog-genomics.org/plink/2.0/score)
  - OmicsMatic methods: Additive allele score sums and allele-normalized averages, Missing-genotype conventions.

<a id="pgs-catalog-scoring-format"></a>
- **PGS Catalog team (2026).** PGS Catalog download information and scoring file format. *PGS Catalog official documentation, accessed 2026-10-25.* [Primary source](https://www.pgscatalog.org/downloads/)
  - OmicsMatic methods: Explicit genome-build, coordinate and effect-allele weights, Author-reported and harmonized scores; nonadditive flags.
