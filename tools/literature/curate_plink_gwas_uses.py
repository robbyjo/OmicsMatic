"""Source-checked PLINK families and newly recovered GWAS-section roles."""
from pathlib import Path
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,named_tool_match,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'

# (paper,tool,primary,supports,reported release,paraphrased use,scope)
USES=[
('P019','plink',59,[45,67,71],None,
 'Used PLINK for several separate genotype workflows: an ancestry-specific 1000 Genomes LD panel for GWAS-summary imputation; MVP genotype QC and sex checks; ROSMAP sample/variant QC and IBD relatedness; and staged FACS-MG QC, LD pruning and genetic PCs. The LD panel reports --ld-window-kb 1000000, --ld-window 1000, --maf 0.01 and --ld-window-r2 0. FACS-MG reports --indep-pairwise 1000 10 0.02, followed by 50 5 0.02.',
 'Preserve each cohort and sequence of filters. MVP/FACS-MG HWE less-than wording does not unambiguously specify retention versus exclusion; do not invert it silently. ROSMAP explicitly excludes HWE P<1e-6 and reports IBD pi_hat>0.1875; KING 0.0884 is a separate coefficient. The first FACS-MG pruning argument is not explicitly labeled kb. Version and detailed LD-statistic flags are unstated. Imputation, EIGENSOFT projection, run_imputez and S-PrediXcan are separate.'),
('P023','plink',78,[],'1.90b5.3',
 'Used PLINK 1.90b5.3 --clump to select independent GWAS variants with P=5e-8, r2=0.01 and a 10-Mb window. Subsequently merged lead SNPs within 100 kb, retained the smallest-P lead, annotated nearby protein-coding genes using bedtools and excluded HLA.',
 'The later 100-kb lead-merging step differs from the 10-Mb LD-clump window. The stated P threshold does not establish both --clump-p1 and --clump-p2. Bedtools annotation, HLA exclusion and downstream enrichment are separate. Existing native clumping is related functionality, without a claim of complete 1.90b5.3 option or numerical parity.'),
('P024','plink2',59,[60],'2.0',
 'Used explicitly named PLINK (v.2.0) to calculate ancestry PCs after MAF>=0.01, HWE P>=1e-10, variant missingness<=0.01, high-LD-region exclusion and LD pruning with a 1000-kb window, step 10 and r2=0.2. The later EUR set uses autosomal biallelic variants, imputation R2>0.8, HWE P>=1e-6, missingness<=0.02 and ancestry-specific ALFA MAF>=0.01. PLINK 2 also computes AD PRS from separately estimated PRS-CS-auto weights, including and excluding APOE.',
 'This is explicit release 2. BCFtools merging, QDA ancestry assignment, ALFA annotation and PRS-CS estimation are separate. Do not assign PRS-CS priors/MCMC settings to PLINK scoring. Exact/approximate PCA and score allele/column/normalization/missing-dosage options are unstated.'),
('P076','plink2',54,[51,53,55,56],'2.0',
 'Used PLINK v.2.0 --glm for ancestry-specific UKB quantitative-trait GWAS with sex, age, genotyping batch, assessment center and 40 PCs; blood biochemistry also includes fasting time and dilution factor. Converted imputed variants to hard calls with biallelic, MAF, missingness and MACH-R2 filters. Separate training/test/validation GWASs and additional European-test GWASs supplied the PRS portability comparisons.',
 'The input conversion reports --max-alleles 2, MAF 0.001, --geno 0.01 and --mach-r2-filter 0.8. Preserve ancestry, trait and independent data partitions; PLINK does not perform BridgePRS or PRS-CSx fitting. METAL combines GWAS results separately. Do not transfer PRS tuning or LD-score heritability filters into --glm, or infer binary/Firth settings from these continuous-trait analyses.'),
('P084','plink',38,[],'1.9',
 'Processed screening-cohort genotype data using both PLINK1.9 and PLINK2. The shared workflow excludes sample/variant missingness above 10%, sex mismatches and variants with HWE P<1e-50, followed by a separate Michigan-server imputation workflow.',
 'The paragraph names both releases but does not allocate each shared QC operation to one executable. Its later ten-PC calculation and KING-robust kinship estimation explicitly use PLINK2. Do not assign those later steps to PLINK1.9 or infer an independent KING executable.'),
('P084','plink2',38,[],'2',
 'Used PLINK2 in the shared genotype-processing workflow, then estimated ten genetic PCs and KING-robust kinship in PLINK2. Removed five individuals with kinship greater than 0.177. PLINK1.9 is separately named in the same paragraph.',
 'KING-robust here is an estimator within PLINK2; a standalone KING runtime is not specified. Jointly described QC steps cannot all be attributed exclusively to release 2. Eagle2/Minimac4 imputation, ancestry cohort selection and downstream GWAS are distinct.'),
('P084','king',38,[],None,
 'Used the KING-robust kinship estimator through PLINK2 and removed five individuals with kinship greater than 0.177 from the screening cohort.',
 'This identifies the kinship method and its PLINK2 host. It does not establish standalone KING execution, an independent KING version or IBD pi_hat equivalence. The PC and shared QC steps have separate PLINK source notes.'),
('P097','plink',34,[],'1.90b7.1',
 'Used PLINK v.1.90b7.1 --linear to associate residualized, inverse-normal protein levels with genotypes in 1260 samples, excluding missing data points. The source separately describes lead selection, LD clumping and iterative conditional association.',
 'PLINK2 performs the separately described genotype QC and PCA. Do not assign that processing to release 1.90b7.1, assume every later conditional/clumping operation used the same flags, or treat residualization and protein exclusions as part of --linear.'),
('P097','plink2',32,[],'2.00a5LM',
 'Filtered Tarkin genotypes using PLINK2 v.2.00a5LM with --geno 0.1, --mac 10, --maf 0.05 and --hwe 1E-15. The 1260-sample proteomics subset was further filtered with --maf 0.05, --hwe 1E-6 and --geno 0.02; the first ten PCs were computed with --pca.',
 'Conversion to PLINK format is a data-format statement; the named PLINK2 filtering/PCA supplies execution evidence. BCFtools preprocessing and the PLINK 1.90b7.1 --linear protein association are separate. Preserve stage-specific cohorts and thresholds.'),
('P112','plink',26,[],'1.9',
 'Used PLINK 1.9 to analyze a new Yale-Penn case-control tranche of unrelated individuals not included in earlier GWAS or meta-analyses.',
 'The same paragraph names PLINK2 logistic regression for MVP, a different cohort. Do not transfer MVP flags or covariates to the Yale-Penn analysis; exact release-1.9 model flags are unstated. Imputation and later cross-cohort meta-analysis are separate.'),
('P112','plink2',26,[],'2.0',
 'Used PLINK 2.0 logistic regression for MVP cannabis-use-disorder GWAS, with sex, age and ten covariates called positive controls in the source. Applied ancestry-specific allele-frequency and best-guess genotype missingness rules.',
 'Preserve the source covariate wording rather than silently repairing it. The Yale-Penn tranche uses PLINK1.9; other cohort models and meta-analysis are distinct. An explicit Firth mode, phenotype encoding and complete command line are not stated here.'),
('P118','plink2',46,[],'2',
 'Used PLINK v.2 for GWAS of observed UKBB phenotypes with 5776313 imputed SNPs after MAF>5% and INFO>0.9 selection. Chose logistic or linear regression according to the observed phenotype type; GWAS of imputed phenotypes used linear regression.',
 'AutoComplete phenotype imputation is separate from PLINK genotype association. Do not infer logistic/Firth processing for imputed phenotypes, genotype-dosage/hard-call flags or an exact PLINK2 build from the family-only version.'),
('P150','plink2',63,[24,61],'2.0',
 'Used PLINK v.2.0 logistic regression with firth-fallback for the KoGES dichotomized MetS GWAS, adjusting for sex, age and ten genetic PCs. Separately computed UKB PRS by summing effect-allele genotypes weighted by posterior mean effects generated with PRS-CS.',
 'Keep KoGES association distinct from UKB score application and external PRS-CS fitting. The PRS-CS global-shrinkage/prior/MCMC/LD-panel settings do not describe PLINK --score. FUMA clumping, COJO conditional analysis and Popcorn correlations are separate programs. Do not transfer firth-fallback to unrelated quantitative analyses.'),
('P176','plink2',16,[17,18],'2.0.0',
 'Used Plink v.2.0.0 to subset and QC exome SNVs. Common-variant QC reports --mind<=0.1, --geno<=0.05, HWE=1e-6, MAF>=0.01 and a heterozygosity deviation rule. Rare-SNV QC uses the same missingness limits and MAF<0.01 before separate gene-based analysis.',
 'The source HWE equality is a threshold statement without a fully specified tail/flag contract. Preserve the reported version spelling and distinct common/rare sets. GEMMA fits association models; burden/SKAT-O and CADD annotation are separate. A different genome-wide genotyping workflow explicitly uses PLINK1.9.0.'),
('P176','plink',19,[],'1.9.0',
 'Used Plink v.1.9.0 to QC previously generated genome-wide SNV genotypes in 163 samples: --geno<=0.02, --mind<=0.02, MAF>=0.01 and a heterozygosity deviation rule. The retained variants underwent a separate GEMMA mixed-model GWAS.',
 'This release and cohort differ from the PLINK2 exome QC described earlier. Do not attribute GEMMA relatedness-adjusted association to PLINK or collapse the 0.02 genotype-array missingness limits into the exome 0.1/0.05 rules.'),
('P200','plink',14,[],'1.9',
 'Used Plink 1.9 LD clumping with r2=0.05 and a European 1000 Genomes reference to select index variants. Applied P<5e-8 first, then P<5e-7 for regions without an earlier clump, and retained unsupported singleton loci separately.',
 'The source pairs --clump-kb with a description of one million bp. Its physical-window description must not be pasted as a numeric kb argument without resolving units. The two P thresholds are sequential locus-selection stages, not automatically --clump-p1/--clump-p2. A named generic/1.9 use can coexist with PLINK2 elsewhere.'),
('P224','plink2',48,[],'2.00a3LM',
 'Performed separate sex-stratum MVP GWASs for four sleep phenotypes using PLINK v.2.00a3LM. Analyses adjusted for age and ten PCs; each trait had models with and without BMI, with linear and squared BMI terms in the adjusted model.',
 'The Appendix GWASs heading contains direct new-study execution evidence. KING v.2.0 kinship filtering, HARE ancestry selection and previously published OSA summary statistics are separate. The source states no full --glm/Firth/phenotype-encoding command. Citation label 56 is not part of the PLINK build.'),
('P224','king',48,[],'2.0',
 'Estimated kinship using KING v.2.0 and removed related individuals at coefficients>=0.0884 before sex-specific sleep GWAS, including relatives across the sex strata.',
 'The GWAS itself names PLINK2. Sex-chromosome checks are described without explicitly assigning them to KING. Do not treat this kinship coefficient as IBD pi_hat or infer a PLINK2-hosted KING implementation from this standalone version statement.'),
('P242','plink',19,[],None,
 'Used the generically named PLINK --hwe 0.000001 step to remove HWE-deviating SNPs during cohort-specific UKB QC. The surrounding MAF and complete-call filters explicitly name PLINK2.',
 'The generic HWE shorthand does not prove a separate PLINK1 executable or version. Preserve its connection to the PLINK2-named workflow. The source calls the HWE test Fisher exact; do not turn that wording into an instruction to substitute a generic 2x2 Fisher test for the genotype HWE procedure.'),
('P242','plink2',19,[],None,
 'Used PLINK 2.0 --maf 0.01 and --geno 0.00 in cohort-specific UKB variant QC, after removing structural and AT/CG variants. The intervening --hwe 0.000001 step is called PLINK without restating its version.',
 'Do not infer two separate executables from generic shorthand, silently choose a different missingness cutoff or assign surrounding custom SNP selection/epistasis analysis to PLINK. The HWE source wording requires the separate generic-PLINK scope note.'),
('P246','plink2',6,[],'2.00a3.1LM',
 'Used explicitly named PLINK v.2.00a3.1LM alongside HAIL v.0.2.78 for QC of qualifying CASR variants in the MANE transcript within UKB.',
 'The paragraph does not allocate all QC steps between PLINK and HAIL or specify their filters. Fisher tests, phenotype mapping and ethnicity-specific covariate-adjusted Firth analyses are described separately without naming PLINK as their execution engine. Do not infer them as PLINK2 commands.'),
('P249','plink2',68,[],'2',
 'Computed genetic PCs for GEUVADIS samples using PLINK version 2 with default settings on chromosome-1 genotypes, then used those PCs in expression correction.',
 'The first three PCs mentioned earlier belong to GTEx, not an explicit GEUVADIS PC count. PEER and DESeq2 expression correction are separate. The exact build, number of GEUVADIS PCs and exact/approximate PCA mode remain unstated.'),
('P108','leafcutter',22,[],None,
 'Calculated developmental-brain intron usage using LeafCutter for an executed splicing-event comparison, transformed usage percentages to M-values and supplied those events to the isoTWAS multivariate trait-mapping framework.',
 'This is an actual comparative analysis rather than a compatible-quantifier example. LeafCutter quantification differs from isoTWAS SNP-weight training and trait association. Clustering, version and preprocessing options are unstated; do not infer the entire LeafCutter differential-splicing pipeline.'),
('P109','umap',11,[],None,
 'Used a two-component UMAP representation to explore 29 grapevine agronomic traits and group differences, then mapped normalized trait values onto that representation.',
 'The source names the UMAP method, without identifying umap-learn, a software version or neighbor/min_dist/metric/seed settings. This trait representation differs from the variant GWAS and does not imply the specific tested Python backend or default parameters.'),
('P150','prsice',26,[],None,
 'Computed an additional PRSice-2 score analysis when evaluating transferability of European MetS GWAS scores to KoGES; detailed results are referred to the supplement.',
 'This comparative PRSice-2 use differs from the main PRS-CS posterior-weight workflow. Build, clumping/threshold tuning and full commands are not in this paragraph; do not transfer PRS-CS hyperparameters or assume they are PRSice options.'),
('P190','coloc',56,[],None,
 'Used COLOC to compare MAJIQTL-derived splicing QTLs with Alzheimer and Parkinson GWAS summary statistics and retained colocalized signals at PPH4>0.5.',
 'The phenotype GWAS summaries come from named previous studies. Priors, exact COLOC implementation/version, variant harmonization and causal-signal assumptions are unstated. A PPH4 cutoff is not a GWAS P-value or experimental causal validation.'),
('P190','spliceai',55,[],None,
 'Defined splice-site-disrupting sQTL annotations using SpliceAI delta scores>0.2 at the relevant 5-prime or 3-prime junction site, then compared their enrichment among discovered sQTLs.',
 'The source does not establish whether SpliceAI was executed locally or its annotations were retrieved. Neural-model inference, precomputed-score lookup and downstream enrichment are distinct. The reported score threshold is not a motif P/q threshold or proof of a causal splice change.'),
('P192','vep',37,[],None,
 'Used VEP to inspect predicted consequences of TBX15 missense variant rs10494217 across two transcripts, including the SIFT annotations reported through that interpretation.',
 'The source does not specify VEP release, cache/reference or consequence options. Transcript-specific predictions and downstream functional interpretation must remain distinct; standalone SIFT execution is not established.'),
('P192','sift',37,[],None,
 'Read SIFT deleteriousness predictions for rs10494217 through the VEP-based transcript interpretation, including a low-confidence prediction for one transcript.',
 'This is a reported annotation, not evidence of a standalone SIFT run. Preserve transcript identities and confidence status; exact SIFT model/version/score thresholds are unstated.'),
('P192','cadd',37,[],None,
 'Reported a database-derived value of 25.7 for rs10494217, labeled CADD-SV in the source, as one component of TBX15 variant interpretation.',
 'Retain the unusual CADD-SV label for this missense-variant example rather than silently converting it to a generic SNV PHRED score or assuming a structural-variant scoring run. CADD release, input/retrieval route and executable use are unstated; score lookup differs from model execution.'),
('P205','prscs',36,[],None,
 'Used PRS-CS to adjust SNP effects for simulated GWAS data in the conventional multi-population PRS comparator pipeline, followed by independent tuning and linear combination of single-GWAS scores.',
 'LDpred2 is used for real GWAS data in the described comparator. Preserve simulation versus empirical inputs and the tuning/evaluation partitions. The source gives no PRS-CS version or exact hyperparameters here; downstream tuning is separate from Bayesian effect estimation.'),
('P205','ldpred',36,[],None,
 'Used LDpred2 to adjust SNP effects for real GWAS data in the conventional multi-population PRS comparator, then selected predictive scores and trained their linear combination in fine-tuning data.',
 'The named method is LDpred2, not an inferred LDpred1 execution. PRS-CS supplies the simulated-data comparator. Version, exact LDpred2 implementation, LD panel and grid/auto settings are unstated; downstream ancestry-specific tuning and performance evaluation are separate.'),
]

def main():
    papers={p['paper_id']:p for p in json.loads((DATA/'papers.json').read_text(encoding='utf-8'))}
    registry={t['id']:t for t in json.loads(Path(__file__).with_name('tool-registry.json').read_text(encoding='utf-8'))}
    path=Path(__file__).with_name('reviewed-uses.json');document=json.loads(path.read_text(encoding='utf-8'));pending=[];sources={}
    for pid,tool_id,primary,supports,version,summary,boundary in USES:
        paper=papers[pid]
        if pid not in sources:
            raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];sources[pid]={p['paragraph_index']:p for p in paragraphs(ET.fromstring(raw))}
        by_index=sources[pid];main_source=by_index[primary];assert named_tool_match(registry[tool_id],main_source['match_text']),(pid,tool_id,primary)
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool_id,evidence_paragraph_sha256=hashlib.sha256(main_source['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=boundary)
        if version:review['version_reported_near_name']=version
        if supports:review['supporting_evidence']=[dict(paragraph_index=i,section=by_index[i]['section'],paragraph_sha256=hashlib.sha256(by_index[i]['text'].encode()).hexdigest()) for i in supports]
        if (pid,tool_id) in {('P192','sift'),('P192','cadd'),('P190','spliceai')}:review['usage_context']='reported-annotation;direct-executable-not-specified'
        if (pid,tool_id)==('P109','umap'):review['usage_context']='named-method;software-backend-not-specified'
        if (pid,tool_id)==('P084','king'):review['usage_context']='named-estimator-through-PLINK2;standalone-executable-not-specified'
        if (pid,tool_id) in {('P108','leafcutter'),('P150','prsice'),('P205','ldpred'),('P205','prscs')}:review['usage_context']='benchmark-or-comparison'
        validate_supporting_evidence(list(by_index.values()),[review],registry)
        previous=[r for r in document['reviews'] if (r['pmcid'],r['tool_id'])==(review['pmcid'],tool_id)]
        if previous:assert len(previous)==1 and previous[0]==review,(pid,tool_id,'existing reviewed prose differs')
        else:pending.append(review)
    assert len({(r['pmcid'],r['tool_id']) for r in pending})==len(pending)
    document['reviews'].extend(pending);path.write_text(json.dumps(document,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(dict(reviewed_roles=len(USES),added=len(pending),total_reviews=len(document['reviews']),supporting_pointers=sum(len(r.get('supporting_evidence',[])) for r in document['reviews']),scope='Source prose and role boundaries only;new full-cohort enrichment/audit required before publication. No implementation credit.')))

if __name__=='__main__':main()
