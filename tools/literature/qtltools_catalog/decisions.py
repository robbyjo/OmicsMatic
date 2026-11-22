"""Individually read QTLtools roles; source statements and qualified components stay separate."""

ROLES = {
    'P073': dict(
        paragraphs=[40,45], version='1.2', context='cis-and-trans-splicing-QTL-mapping',
        analysis_role='Grouped cis-sQTL permutations, conditional mapping and distant trans-sQTL mapping',
        summary='Mapped cell-type-specific splicing QTLs with QTLtools 1.2: grouped cis permutations (1000) in a 1 Mb window, followed by conditional mapping; a separate trans analysis used variants beyond 5 Mb and 50000 permutations.',
        scope='The cis paragraph reports eight splicing PCs, five genotype PCs, sex and age, at least ten cells per donor/cell type, qvalue 2.30.0 and FDR<0.05. The bare --grp spelling does not identify best/pca1/mean: all three inspected original editions reject that ambiguous option. Do not select a grouping silently. The trans paragraph does not restate an edition; its mappability/cross-mapping filters, multiple-intron transformation and BH FDR0.01 are separate preparation/aggregation. Exact binary, grouping, seed, phenotype/group encoding and conditional threshold construction remain unresolved. Prepared original components do not reproduce these author/cohort steps.',
        operations=[dict(role='cis',edition='1.2',paragraph=40,operations=['CIS_PERMUTE','CIS_CONDITIONAL']),dict(role='trans',edition=None,paragraph=45,operations=['TRANS_NOMINAL','TRANS_PERMUTE'])],
        binding='reported-cis-edition-components;grouping-unresolved;trans-edition-not-restated'),
    'P074': dict(
        paragraphs=[45,68], version='1.1', context='eqtl-generation-before-separate-colocalization',
        analysis_role='Aortic-valve eQTL generation for subsequent COLOC analysis',
        summary='Generated human aortic-valve eQTLs with QTLtools 1.1 for a subsequent comparison with aortic-stenosis GWAS signals using COLOC 3.2.1; the code-availability list also names QTLtools 1.1.',
        scope='The paragraph assigns QTLtools to eQTL generation; COLOC performs the colocalization and LocusCompareR provides a separate validation/visualization step. Its 1 Mb start/end-site selection and PP4>0.75 criterion describe the colocalization analysis, not a fully specified QTLtools command. Mapping mode, covariates, permutations, seed, inputs and executed build are unstated. Original 1.1 mapping controls establish available prepared components, without assigning a nominal/permutation/conditional mode to this paper or performing COLOC.',
        operations=[dict(role='eqtl-generation-unspecified-mode',edition='1.1',paragraph=45,operations=[])],
        binding='reported-edition;mapping-mode-and-settings-unresolved'),
    'P104': dict(
        paragraphs=[42], version='1.3.1', context='functional-density-around-cis-eqtls',
        analysis_role='Functional-density summary of cell-type-specific OCRs around cis-eQTLs',
        summary='Used QTLtools 1.3.1 fdensity to summarize cell-type-specific open-chromatin regions in 10 kb bins around cis-eQTLs, describing a 2 Mb neighborhood.',
        scope='Native Java and exact original 1.3.1 fdensity are qualified on valid prepared BED inputs. The inspected source counts QTLs having any annotation overlap per bin, not annotation multiplicity or an enrichment significance test. Preserve its start+1 anchor, strand handling, duplicate-QTL behavior and small-tree input-order guard. The paper does not settle total span versus radius, exact BED/lead-QTL construction, annotation ordering or enrichment background. Require an explicit radius; do not infer it from the 2 Mb wording, silently sort annotations or claim the reported biological enrichment was replicated.',
        operations=[dict(role='fdensity',edition='1.3.1',paragraph=42,operations=['FDENSITY'])],
        binding='reported-edition-original-and-native-component;paper-coordinate-inputs-unresolved'),
    'P108': dict(
        paragraphs=[51], version='1.3.1', context='nominal-qtl-discovery-for-hidden-covariate-selection',
        analysis_role='Nominal cis-eQTL and cis-isoQTL counts for HCP selection',
        summary='Used QTLtools 1.3.1 nominal cis-QTL discoveries to select hidden covariates over a 100-300 HCP grid, retaining 200 for gene expression and 175 for isoform expression at the reported Bonferroni criterion.',
        scope='QTLtools supplies nominal cis-eQTL/isoQTL detection; Salmon/tximeta, DESeq2 transformation, WGCNA outlier removal, ComBat and HCP estimation/residualization are distinct steps. The paper reports removal of age, age-squared, sex and ten genotype PCs as well as HCPs, but does not specify the full QTLtools command, cis window, tested family, HCP grid spacing or executed build. The prepared 1.3.1 nominal component does not optimize HCPs, reproduce the preprocessing/2115-sample population or turn discovery-based parameter selection into independent validation.',
        operations=[dict(role='nominal-cis',edition='1.3.1',paragraph=51,operations=['CIS_NOMINAL'])],
        binding='reported-edition-nominal-component;external-HCP-selection'),
    'P190': dict(
        paragraphs=[33,42,62], version=None, context='original-grouped-sgene-baseline-and-method-comparison',
        analysis_role='Grouped cis-permutation sGene discovery and MaxT method comparison',
        summary='Supplement Note S1.7 explicitly uses QTLtools for baseline sGene discovery: a 1 Mb cis window, --permute 1000 and --grp-best on prepared MAJIQ/LeafCutter BED phenotypes, with no additional QTLtools normalization and BH control across genes at 0.05. The main text also discusses MaxT and compatibility.',
        scope='The supplement establishes execution use beyond the main-text method/compatibility mentions, but gives no QTLtools edition or executed commit. It calls --permute 1000 adaptive; the inspected 1.1/1.2/1.3.1 implementation accepts a fixed integer count. Retain that discrepancy without inventing adaptive stopping or a historical edition. Note S1.7 points to S1.4 for covariates, whereas the confounder description is in S1.5; preserve both locators. GTEx variant selection, pre-normalization, MOCCASIN covariate estimation/selection, BH and simulation comparisons remain separate work. The current exact-edition artificial components do not identify the author binary, reproduce the simulation or refit GTEx.',
        operations=[dict(role='grouped-cis-permutation',edition=None,paragraph=33,supplement='Note S1.7',operations=['CIS_PERMUTE'])],
        binding='supplement-explicit-execution-and-options;edition-unknown;adaptive-wording-unresolved'),
    'P201': dict(
        paragraphs=[14,15], version=None, context='cis-sqtl-mapping-and-effect-export',
        analysis_role='Cis-sQTL mapping, window restriction and effect/SE export for mashr',
        summary='Mapped intron-ratio cis-sQTLs with QTLtools using a reported 10000 adaptive permutations, first within 1 Mb and then a restricted 250 kb window, adjusting for 29 PEER factors and five genotype PCs; effect sizes and standard errors fed a separate mashr comparison.',
        scope='The QTLtools edition, exact adaptive rule, command, seed and grouping are unstated. The inspected editions implement fixed-count permutations, so 10000 fixed permutations cannot be relabelled as the paper adaptive analysis. The observation that 89% of discoveries lay within 250 kb motivates a data-dependent restriction, not an independent prespecified window. The standard-error statement does not establish edition 1.3.1 or authorize reconstruction of an unreported field from a p-value. Keep LeafCutter normalization, European coordinate/filter harmonization and mashr estimation separate. Caller-supplied prepared inputs and declared original operations do not reproduce that workflow.',
        operations=[dict(role='cis-permutation-and-nominal',edition=None,paragraph=14,operations=['CIS_PERMUTE','CIS_NOMINAL']),dict(role='effect-and-standard-error-export',edition=None,paragraph=15,operations=[])],
        binding='execution-reported;edition-adaptive-rule-and-export-settings-unresolved'),
    'P203': dict(
        paragraphs=[14,11], version=None, context='cis-eqtl-mapping-and-reported-match-qc',
        analysis_role='Nominal, permutation and conditional cis-eQTL mapping plus sample-match QC',
        summary='Used QTLtools nominal, permutation and conditional cis-eQTL passes with 20 PEER factors and a 1 Mb gene/intron-body window, followed by Storey q-value filtering. A separate QC paragraph reports a QTLtools match function for RNA/DNA sample identity.',
        scope='No QTLtools edition, permutation count, exact command/threshold files or executed build is given. The literal match label is retained: current prepared MBV controls do not prove which historical match command the paper ran or that it is the same implementation. Do not rename it silently, infer a sample identity decision from a raw concordance field or conflate PLINK sex checks with QTLtools. The author-repository URL is a source pointer, not an executed-commit pin. PEER estimation, body-window preparation, sample removal and qvalue thresholds remain distinct declared steps; generic component availability does not reproduce them.',
        operations=[dict(role='cis',edition=None,paragraph=14,operations=['CIS_NOMINAL','CIS_PERMUTE','CIS_CONDITIONAL']),dict(role='literal-match',edition=None,paragraph=11,operations=[])],
        binding='execution-reported;edition-unknown;literal-match-command-unresolved'),
    'P219': dict(
        paragraphs=[13,29,31,28], version=None, context='quan-and-separate-cis-eqtl-analysis',
        analysis_role='Skin gene quantification and separately versioned cis-eQTL discovery',
        summary='Quantified skin gene expression with QTLtools quan and GENCODE19 without specifying the quan edition. Separate QTLtools 1.2 nominal analyses selected 30 PEER covariates; the final 1 Mb cis-eQTL analysis included BMI and genotyping chip and used 1000 permutations for gene thresholds.',
        scope='Keep quan edition unknown; mapping edition 1.2 does not identify the quantifier. The PEER factors were learned from rank-normal residuals of a family/zygosity mixed model with BMI, while mapping used a second residual set without BMI and then adjusted for BMI/chip. Do not merge the two residual populations or apply an extra normal transformation silently. The paper separates BH5% for covariate selection from Storey-Tibshirani5% for final gene thresholds and reports betas relative to the minor allele; original exports do not automatically establish that orientation. Raw-read QC/alignment, GENCODE19 construction, mixed models, PEER selection, FDR/threshold construction and allele harmonization remain upstream/downstream responsibilities.',
        operations=[dict(role='quan',edition=None,paragraph=13,operations=['QUAN']),dict(role='nominal-covariate-selection',edition='1.2',paragraph=29,operations=['CIS_NOMINAL']),dict(role='final-cis',edition='1.2',paragraph=31,operations=['CIS_NOMINAL','CIS_PERMUTE'])],
        binding='quantifier-edition-unknown;reported-1.2-mapping-components;distinct-residual-preparation'),
    'P227': dict(
        paragraphs=[10,19], version='1.2', context='rna-dna-and-rna-wgs-sample-matching',
        analysis_role='MBV checks for sample swaps and RNA-seq/WGS pairing',
        summary='Used QTLtools MBV 1.2 to identify RNA/genotype mismatches during QC and pair RNA-seq with WGS samples; five sample swaps were corrected, and matching sets differed between SNV/indel and structural-variant calls.',
        scope='The raw flattened text joins version1.2 and bibliography label27. JATS citation-pruned text and the xref identify edition1.2, not1.2.27. Mapping/base/genotype quality thresholds, pairing decision rules and exact executed build are unreported. Prepared original1.2 MBV retains complete counters and floating-point fractions but does not automatically relabel donors, correct swaps or perform contamination/relatedness/read-quality filtering. Keep the distinct sample denominators and structural-variant call failures; do not treat a no-covered-site result as a donor match. The legacy1.1 integer-division behavior is a separate warning, not the reported paper edition.',
        operations=[dict(role='MBV',edition='1.2',paragraph=10,operations=['MBV']),dict(role='RNA-WGS-pairing',edition='1.2',paragraph=19,operations=['MBV'])],
        binding='reported-edition-original-MBV-component;matching-decisions-not-reconstructed')
}

PDF_URL='https://ars.els-cdn.com/content/image/1-s2.0-S0002929725004033-mmc1.pdf'
PDF_PIN=dict(bytes=3125933,sha256='468d4018f261f438654d873b539952dd157417910844d68de34be68e5d3678f0')
PDF_PAGES={21:'Note S1.5: Correcting Confounders',25:'Note S1.7: sGene Mapping'}
