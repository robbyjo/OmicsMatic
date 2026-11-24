"""Fresh 250-paper census and five individually interpreted FastQTL uses."""
from pathlib import Path
import copy,gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as E
from collect_omics_review import paragraphs
from fastqtl_workflow.primary import audit as workflow_census

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/fastqtl-catalog-sources'
TARGETS={'fastqtl'}
POSITIVE=['P019','P157','P170','P187','P212']
def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def frozen():
    value=json.loads((ROOT/FOLDER/'ROLES.json').read_bytes());census=(ROOT/FOLDER/'CENSUS.json').read_bytes()
    require(value['status']=='FIVE_FASTQTL_SOURCE_ROLES_BEFORE_GUARDED_ADMISSION' and value['source_census_identity']==pin(census),'Changed source-role freeze')
    require([r['paper_id'] for r in value['roles']]==POSITIVE and all(r['tool_id']=='fastqtl' for r in value['roles']),'Changed unique target roles')
    for r in value['roles']:
        for key in ('source_role_review_applied','catalog_admitted','original_paper_analysis_reexecuted','whole_paper_reproduced','paper_edition_qualified','required_original_inputs_available','exact_historical_flags_established'):require(r[key] is False,'Expanded source/scientific claim: '+key)
        require(r['original_observation']['version_reported_near_name'] is None,'Changed raw near-name capture')
        require(r['local_operation_established']==(r['paper_id']!='P212'),'Dataset reuse is not a new local execution')
    return value

def audit(cache,papers_raw):
    roles=frozen();papers={r['paper_id']:r for r in json.loads(papers_raw)};measured=workflow_census(ROOT,Path(cache));require(measured==json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes()),'Changed fresh full-cohort census');require(len(measured['anchors'])==10 and measured['structures']==[],'Unreviewed FastQTL source structure')
    for role in roles['roles']:
        paper=papers[role['paper_id']];require(all(role[k]==paper[k] for k in ('pmcid','doi','xml_sha256')),'Changed role identity');require([u for u in paper['tools'] if u['tool_id']=='fastqtl']==[role['original_observation']],'Changed raw observation')
        anchors=[{k:a[k] for k in ('paragraph_index','section','paragraph_sha256')} for a in measured['anchors'] if a['paper_id']==role['paper_id']];require(anchors==role['primary_anchors'],'Changed complete target anchors')
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as f:raw=f.read(67108865)
        require(pin(raw)['sha256']==paper['xml_sha256'],'Changed XML during role audit');texts={r['paragraph_index']:unicodedata.normalize('NFKC',r['text']) for r in paragraphs(E.fromstring(raw))};versions=[]
        for anchor in anchors:
            for version in re.findall(r'fastqtl\s+v\.?\s*(\d+\.\d+)',texts[anchor['paragraph_index']],re.I):versions.append((anchor['paragraph_index'],version))
        require(versions==([(71,'2.165'),(79,'2.165')] if role['paper_id']=='P157' else []),'Contextual source versions differ')
        require([(r['paragraph_index'],r['tool_edition']) for r in role['reported_version_contexts']]==versions,'Changed contextual version interpretation')
        if role['paper_id']=='P019':require('closest to 95%' in texts[37] and role['reported_settings']['yield_choice']=='closest-to-95-percent-of-maximum' and role['reported_settings']['candidate_PEER_range']==[5,50] and not role['reported_settings']['tie_policy_reported'],'Changed PEER choice rule')
        if role['paper_id']=='P170':require(role['reported_settings']['mapping_Q_max']==.05 and role['reported_settings']['profile_prioritization_Q_max']==.10 and '1,000 permutations' in texts[39] and 'nominal Q value' in texts[59],'Changed distinct thresholds/source terminology')
        if role['paper_id']=='P187':require(role['reported_settings']['caQTL_window_bp']==1000 and role['reported_settings']['eQTL_window_bp']==1000000 and role['reported_settings']['expression_PC_counts']==dict(hepatocyte=2,LSEC=3,Kupffer=6,mesenchymal=2,NK_T=3,cholangiocyte=6) and 'beta-adjusted' in texts[18] and 'BH procedure' in texts[22],'Changed caQTL/eQTL settings')
        if role['paper_id']=='P212':require('publicly available eQTL dataset' in texts[20] and 'assume only one signal per gene' in texts[20] and not role['related_prepared_operations'],'Dataset reuse became an invented execution')
    require({p['paper_id'] for p in papers.values() if any(u['tool_id']=='fastqtl' for u in p['tools'])}==set(POSITIVE),'Unreviewed target observation')
    return copy.deepcopy(roles),dict(primary_XML_checked=250,matched_papers=5,matched_paragraphs=10,reviewed_source_roles=5,extra_structures=0,contextual_2_165_declarations=2,original_paper_analyses_reexecuted=False)
