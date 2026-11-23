"""Fresh full-250 primary census and six curated MR-PRESSO roles; no admission."""
from pathlib import Path
import gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[3]
POSITIVE=['P105','P125','P135','P150','P189','P224']
ANCHORS={'P105':[20,58],'P125':[24],'P135':[40],'P150':[29,30,70,71],'P177':[39],'P189':[10,26],'P224':[28,36]}
NAMED=re.compile(r'(?<![A-Za-z0-9_])MR[- ]?PRESSO(?![A-Za-z0-9_])',re.I)
ROLES={
 'P105':dict(summary='Used MR-PRESSO to identify pleiotropic outliers and estimate outlier-corrected effects of peptic ulcer disease and its subtypes on gastric cancer; the corrected results were not significant.',
  observed_operations=['outlier_pleiotropy_correction','conditional_outlier_corrected_univariable_estimate'],source_role='analysis-method',
  scope_limit='F-statistic filtering, allele harmonization, sample splitting, power calculations and the study conclusion are separate. Package edition, Monte Carlo count, seed and exact caller settings are unreported.'),
 'P125':dict(summary='Added MR-PRESSO as an outlier/pleiotropy sensitivity analysis alongside the other Mendelian randomization estimators.',
  observed_operations=['outlier_pleiotropy_sensitivity_analysis'],source_role='sensitivity-method',
  scope_limit='IVW/median/mode/Egger, Q and F statistics, I-squared and the conditional SIMEX correction are separate methods. Do not infer an unreported function call, edition, seed or simulation count.'),
 'P135':dict(summary='Used MR-PRESSO outlier exclusion in multivariable height/BMI analyses of fracture risk to assess heterogeneity introduced by height instruments.',
  observed_operations=['multivariable_outlier_detection','conditional_multivariable_outlier_corrected_estimate'],source_role='multivariable-sensitivity-method',
  scope_limit='This is multiple exposures with one fracture outcome, not the existing multiple-outcome diagnostic. MR-LASSO and MendelianRandomization package estimators remain separate. Exact MR-PRESSO edition and simulation settings are unreported.'),
 'P150':dict(summary='Used MR-PRESSO global/outlier tests and corrected estimates for metabolic-syndrome outcome analyses, with conditional method reporting and a separate heterogeneity/pleiotropy decision tree.',
  observed_operations=['global_pleiotropy_test','outlier_detection','conditional_outlier_corrected_univariable_estimate'],source_role='conditionally-reported-analysis-method',
  scope_limit='The study omits the MR-PRESSO method when the global test is nonsignificant or too few instruments remain. Its method-selection tree, Wald/IVW/median/Egger analyses and 325-outcome Bonferroni correction are separate. Preserve Table3 as a source anchor, not a reproduced result; exact edition, seed and simulation count remain unreported.'),
 'P189':dict(summary='Used MR-PRESSO to assess horizontal pleiotropy and possible outlier correction in circulating-protein/PCOS associations; reported global tests for the selected65 proteins were nonsignificant.',
  observed_operations=['global_pleiotropy_test','conditional_outlier_correction'],source_role='sensitivity-method-with-negative-global-findings',
  scope_limit='A nonsignificant global test and conditional corrected NA are not proof of no pleiotropy or of causality. Other MR estimators, colocalization and SMR remain separate. Exact MR-PRESSO edition, seed and simulation count are unreported.'),
 'P224':dict(summary='Compared MR-PRESSO outlier-adjusted causal estimates with the primary MR-RAPS results in primary and secondary analyses.',
  observed_operations=['outlier_adjusted_comparator_estimates'],source_role='comparator-method',
  scope_limit='MR-PRESSO is a comparator here; it is not the primary MR-RAPS engine, cML method or an implementation of the entire paper analysis. Exact caller, edition, seed and simulation settings are unreported.'),
}

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)
def normalized(text):
    return unicodedata.normalize('NFKC',text).translate(str.maketrans({x:'-' for x in '‐‑‒–—−'}))

def audit(cache,papers_bytes=None):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Frozen primary extractor changed')
    if papers_bytes is None:papers_bytes=(ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes()
    papers=json.loads(papers_bytes);require(len(papers)==250,'Changed paper population')
    roles=[];identities=[];found={};table_candidates=[]
    for paper in papers:
        path=Path(cache)/(paper['pmcid']+'.xml.gz');require(path.stat().st_size<=64*1024*1024,'Compressed source capacity exceeded')
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'],'Changed primary bytes: '+paper['paper_id'])
        tree=ET.fromstring(raw);doi=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
        require(len(doi)==1 and doi[0].lower()==paper['doi'].lower(),'Primary DOI identity mismatch')
        identity={key:paper[key] for key in ('paper_id','pmcid','doi','xml_sha256')};identities.append(identity)
        anchors=[]
        for paragraph in paragraphs(tree):
            if NAMED.search(normalized(paragraph.get('match_text',paragraph['text']))):
                anchors.append(dict(paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=pin(paragraph['text'].encode())['sha256']))
        if anchors:found[paper['paper_id']]=anchors
        tables=[]
        for table in tree.iter('table-wrap'):
            text=words(table)
            if NAMED.search(normalized(text)):
                item=dict(paper_id=paper['paper_id'],table_id=table.get('id'),table_text_identity=pin(text.encode()))
                tables.append(item);table_candidates.append(item)
        if paper['paper_id'] in POSITIVE:
            uses=[u for u in paper['tools'] if u['tool_id']=='mrpresso'];require(len(uses)==1,'Changed original positive observation')
            use=uses[0];anchor=next((x for x in anchors if x['paragraph_index']==use['evidence_paragraph_index']),None)
            require(anchor is not None and anchor['paragraph_sha256']==use['evidence_paragraph_sha256'],'Original extraction binding changed')
            require(use['version_reported_near_name'] is None,'A newly reported edition needs explicit review')
            role=dict(**identity,**ROLES[paper['paper_id']],anchors=anchors,table_anchors=tables,
                original_observation=use,reported_near_name_version=None,reported_simulations=None,reported_seed=None,
                author_environment_reproduced=False,whole_paper_reproduced=False)
            roles.append(role)
    require(sorted(found)==sorted(ANCHORS),'Changed full250 named-paper census')
    for paper_id,expected in ANCHORS.items():require([x['paragraph_index'] for x in found[paper_id]]==expected,'Changed paragraph census: '+paper_id)
    require([x['paper_id'] for x in roles]==POSITIVE,'Changed six-positive population')
    require([(x['paper_id'],x['table_id']) for x in table_candidates]==[('P150','Tab3')],'Changed table candidate population')
    require(table_candidates[0]['table_text_identity']['sha256']=='201f89ff6377f85b6ca45a89d792e8bebabfb81f67ffc72f900aff5198d29444','Changed P150 source table')
    return dict(status='PASS_FULL250_MRPRESSO_SOURCE_ROLES_WITH_EXPLICIT_UNREPORTED_SETTINGS',papers_checked=250,
        source_papers_identity=pin(papers_bytes),source_extractor_sha256=EXTRACTOR_SHA,roles=roles,primary_identities=identities,
        discussion_only=[dict(paper_id='P177',classification='RECOMMENDED_AVAILABLE_METHOD_EXECUTION_NOT_ESTABLISHED',anchors=found['P177'],
            reason='Discussion recommends detecting/removing outliers with existing methods before TEMR; actual MR-PRESSO execution is not established. No positive observation or review is added.')],
        table_candidates=table_candidates,full_primary_texts_redistributed=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
