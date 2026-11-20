# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit edgeR roles against the frozen 250 primary sources; no catalog mutation."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[2]
DATA='docs/literature/omics-2023-2026'
ARCHIVE='docs/literature/susie-dispatch-catalog/metadata-before.zip'
ARCHIVE_SHA='bcb3de9fefc5347c65226cca713b1038b7287cbb915d8bf8158c544114e31c3d'
PAPERS_SHA='a12402212de69694cfe09acd8908ddf1d0b0a4a80f41787ff4defac17e9d7b5c'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:edgeR|calcNormFactors|filterByExpr|glmQLFit|glmQLFTest|exactTest)(?![A-Za-z0-9_])',re.I)
# Body paragraph indices belong to the frozen collector. Associated-data repeats
# are retained as context, not additional executions or observations.
DECISIONS={
 'P005':([67],[], 'lipid-ion-count-GLM',
    'Used edgeR after the in-house CLAW lipidomics workflow to model lipid ion counts by condition, with a blank sample and a log-library-size offset.',
    'The named primary paragraph concerns lipidomics. A native normalization matrix does not implement CLAW preprocessing, its blank/design conventions or GLM testing; do not infer an edition or attribute all RNA-seq testing from this paragraph.'),
 'P037':([48,49],[(48,'3.32.1','v.3.32.1')], 'TMM-and-quasi-likelihood-GLM',
    'Filtered microbial transcripts by CPM and prevalence, applied TMM, estimated dispersions, fitted treatment/week or WLZ-quartile/week interaction models and used glmQLFTest.',
    'Retain >=5 CPM in >=35% of samples and the separate interaction designs. The reported power-method dispersion step is not silently recast as the default estimateDisp pipeline. Native normalization supplies neither filtering, dispersion fitting nor the F-test.'),
 'P046':([30],[(30,'3.32.1','v.3.32.1')], 'TMM-RPKM',
    'Used edgeR to obtain TMM-normalized RPKM for gene-level expression, then averaged replicates for downstream analyses.',
    'Literal supplied gene lengths, count preparation and replicate aggregation remain required. Differential tests used DESeq2 and batch-effect removal used limma; neither is attributed to edgeR.'),
 'P051':([79],[(79,'3.20.1','v.3.20.1')], 'limma-edgeR-pairwise-DE',
    'Used limma and EdgeR together for count processing and pairwise differential expression; used a separate DESeq2 LRT for variation across conditions.',
    'Preserve the capitalized spelling and distinct package versions: limma 3.32.2 is not the edgeR edition. Exact edgeR functions, designs and options are unstated; 3.20.1 is not a supported native profile.'),
 'P053':([6,72,73,75],[(72,'3.36.0','v.3.36.0'),(73,'3.36.0','v.3.36.0')], 'cross-species-pseudobulk-GLM-and-peaks',
    'Applied TMM and tagwise dispersion estimation with locfit to species/cell-type pseudobulks, used glmFit and between-species contrasts, and reused the setup for orthologous chromatin peaks.',
    'Preserve one-to-one orthologues, stated FDR 8.33e-6, gene fold-change >=2 and 15% detection requirement, versus peak fold-change >=4 with no cell-detection cut. Native normalization does not qualify filtering, GLM contrasts, orthology construction or detection rules.'),
 'P087':([65],[(65,'3.42.4','v.3.42.4')], 'TMM-and-unlogged-normalized-CPM',
    'Used calcNormFactors with TMM, then cpm with normalized library sizes and log=FALSE for liver expression; tested OLR1 relationships separately with Spearman or Wilcoxon procedures.',
    'PCA outlier removal after sctransform VST and downstream stats/rstatix tests are separate. Native prepared-matrix TMM/CPM does not select samples or reproduce the author R4.3.2 deployment.'),
 'P088':([59,70],[(59,'3.22.3','v.3.22.3')], 'cell-type-workflow-package-role',
    'Named edgeR 3.22.3 among the packages used for cell-type-specific heritability analyses, alongside DESeq2, limma and stratified LDSC.',
    'The code-availability statement supports a missed package observation but does not identify a precise edgeR function here. The associated-data copy is not another execution. Supplement-specific filtering details and the historical 3.22.3 fork require separate audit; no native counterpart is inferred.'),
 'P090':([37],[(37,'3.32.1','v.3.32.1')], 'cell-type-pseudobulk-disease-DE',
    'Used edgeR on cell-type pseudobulks to compare DCM and control left-ventricular samples, defining disease-specific gene programs for heritability analysis.',
    'Retain the donor/cell populations, FDR <0.05, absolute log2 fold change >0.5 and reported minimum normalized log2 expression rule. Precise functions/design are deferred to earlier methods; native normalization alone does not implement the disease analysis.'),
 'P104':([35],[], 'pseudobulk-expression-filtering',
    'Used filterByExpr for donor-level pseudobulk expression filtering after requiring at least five nuclei per donor/cell type; described five reads in at least 40% of donors.',
    'Do not substitute a hand-written raw-count prevalence filter for the named function: its group/design, library-size and CPM semantics need explicit reconciliation. Native normalization has no automatic filterByExpr step; edition is unstated.'),
 'P108':([44,47],[(47,'3.40.2','v3.40.2')], 'gene-and-isoform-TMM-for-prediction',
    'TMM-normalized Salmon/tximeta gene and isoform estimates with edgeR before the described DESeq2 transformation and covariate residualization for prediction.',
    'Version 3.40.2 is stated for developmental-brain processing; do not transfer it to the GTEx paragraph without evidence. Preserve the authors mixed VST/log-CPM wording, inferential replicates, covariate populations and downstream training as separate steps.'),
 'P158':([23],[(23,'3.38.4','v.3.38.4')], 'csaw-ChIP-differential-enrichment',
    'Used EdgeR for differential ChIP enrichment after csaw peak definition and loess-normalized reads, then assigned genomic features and visualized peaks.',
    'Keep the stated log fold change >2 and P<0.05, rather than converting them to other fold-change/FDR rules. Exact edgeR functions/design are unstated; csaw, loess, genomic assignment and unsupported 3.38.4 remain separate.'),
 'P182':([13],[], 'TMM-and-filterByExpr-before-exploration',
    'Used TMM normalization and filterByExpr before exploratory cartilage analyses, reporting >=10 counts in at least 158 samples and subsequent robust-PCA outlier removal.',
    'Retain the reported 158-sample rule and its 226-sample reference group; do not silently replace it with the function defaults. The source R4.1.3/Bioconductor3.1 combination is retained literally. Repeated-subject structure, filtering and outlier selection are not native normalization.'),
 'P183':([13],[], 'TMM-before-RINT-and-PEER',
    'Applied edgeR TMM to robustly expressed lncRNA and protein-coding genes before rank-inverse-normal transformation and PEER covariate estimation.',
    'The preceding TPM/count/prevalence filters, RINT tie policy, PEER factors and sample-size rules are distinct operations. No edgeR differential-expression fit or package edition is established by this paragraph.'),
 'P212':([10],[], 'CPM-TMM-before-inverse-normal-transform',
    'Described a low-count filter, CPM calculation and TMM normalization in edgeR before inverse-normal transformation for study-level eQTL analysis.',
    'Preserve the literal low-count/prevalence wording and CPM/TMM order uncertainty; do not silently invert thresholds or infer a standard pipeline. Annotation, genotype selection, population PCs and association models are separate; edgeR edition is unstated.'),
 'P223':([10],[], 'short-read-timepoint-DE',
    'Used edgeR on Salmon-generated short-read counts for comparisons between day 0 and later iNeuron time points, reporting log fold changes and adjusted P values.',
    'The upstream HISAT2/StringTie/Ballgown wording, Salmon options, annotation mapping and junction counting are separate. No precise edgeR function, edition, design or contrast settings are recovered; normalization alone does not perform the tests.'),
 'P228':([9,12],[], 'HiC-dispersion-and-pseudobulk-TMMwsp',
    'Used estimateDisp for Hi-C interaction count overdispersion and GLM testing, and used TMMwsp on pseudobulk snRNA-seq counts before limma voomLmFit.',
    'Keep the two omics roles separate. Hi-C ICE/LOESS processing, interaction filters and GLM tests are not TMM; snRNA filtering, limma fitting, batch effects and adjusted-P<0.2 are not supplied by native normalization.'),
 'P237':([12,14],[], 'CPM-filtering-technical-replicates-and-voom',
    'Used edgeR for count normalization/expression analysis, CPM-based filtering, sumTechReps in selected external datasets and log2 CPM with prior.count=2; inference used voomLmFit.',
    'Retain filtering before replicate merging, donor/cell-line definitions, clone:dox contrasts and imbalance cautions. Native CPM does not aggregate replicates, implement filterByExpr or fit limma models; edition is unstated.'),
}
def require(value,message):
    if not value:raise ValueError(message)
def digest(raw):return hashlib.sha256(raw).hexdigest()
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def audit(cache):
    from collect_omics_review import paragraphs
    from catalog_fixed_cohort import verify_fixed_cohort
    require(digest((ROOT/'tools/literature/collect_omics_review.py').read_bytes())=='64198266fd706c13a1a4e624a2d68e9779477081c5b8644f2fade3114c780396','Changed frozen paragraph extractor')
    raw=(ROOT/ARCHIVE).read_bytes();require(digest(raw)==ARCHIVE_SHA,'Changed explicit preceding metadata archive')
    with zipfile.ZipFile(ROOT/ARCHIVE) as z:raw=z.read(DATA+'/papers.json')
    require(digest(raw)==PAPERS_SHA,'Changed exact preceding 250-paper population');papers=json.loads(raw);verify_fixed_cohort(ROOT,ROOT/DATA,papers)
    original={p['paper_id'] for p in papers if any(t['tool_id']=='edger' for t in p['tools'])};require(original==set(DECISIONS)-{'P088'},'Different preceding edgeR observations')
    identities=[];roles=[];named=[]
    for p in papers:
        path=Path(cache)/(p['pmcid']+'.xml.gz')
        with gzip.open(path,'rb') as f:raw=f.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and digest(raw)==p['xml_sha256'],'Changed XML: '+p['paper_id'])
        root=ET.fromstring(raw);dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];require(len(dois)==1 and dois[0].lower()==p['doi'].lower(),'Changed DOI')
        identities.append({k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256']});blocks=paragraphs(root);by_index={b['paragraph_index']:b for b in blocks};hits=[b for b in blocks if PATTERN.search(b.get('match_text',b['text']))]
        if not hits:continue
        pid=p['paper_id'];named.append(pid);require(pid in DECISIONS,'Review newly named paper: '+pid);indices,versions,role,summary,caution=DECISIONS[pid]
        require(set(indices)<={b['paragraph_index'] for b in hits},'Named anchors differ: '+pid)
        anchors=[dict(paragraph_index=i,section=by_index[i]['section'],paragraph_sha256=digest(by_index[i]['text'].encode())) for i in indices]
        for i,version,literal in versions:require(literal in by_index[i]['text'],'Reported version text differs: '+pid+' '+version)
        roles.append(dict(paper_id=pid,pmcid=p['pmcid'],doi=p['doi'],primary_url='https://doi.org/'+p['doi'],role=role,usage_summary=summary,
            scope_and_required_work=caution,anchors=anchors,reported_versions=[dict(version=v,paragraph_index=i,literal=t) for i,v,t in versions],
            existing_observation=pid in original,proposed_missed_observation=pid=='P088',author_runtime_executed=False,whole_paper_reproduced=False))
    require(set(named)==set(DECISIONS),'Expected edgeR role missing')
    return dict(schema_version=1,status='AUDITED_EDGER_SOURCE_ROLES_CUMULATIVE_ADMISSION_OPEN',baseline_commit='ae5c476b435edf72838dfa288b336c92975d7c41',
        explicit_preceding_metadata_archive=ARCHIVE,source_identities=identities,papers_scanned=250,positive_papers=len(roles),existing_observations=len(original),
        proposed_additions=['P088'],roles=roles,canonical_metadata_modified=False,new_family_credit=0,new_canonical_review_records=0,whole_goal_complete=False)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');a.out.mkdir(parents=True,exist_ok=False);owned=True
        result=audit(a.primary_cache);(a.out/'CENSUS.json').write_bytes(encoded(result));print('PASS 250 exact sources; 17 edgeR roles; proposed P088 addition; no catalog mutation');return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),complete_result=False,
            required_action='Restore the named exact permitted source/cache or retain the dependent skip. Preserve failures and retry NEW output; never rewrite old hashes to accept changed sources.')
        if owned:(a.out/'FAILURE.json').write_bytes(encoded(record))
        print(record['status']+': '+str(error)+'\n'+record['required_action'],file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
