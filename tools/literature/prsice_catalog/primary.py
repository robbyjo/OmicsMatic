"""Bind five PRSice observations and all eligible anchors to the full primary corpus."""
from pathlib import Path
import copy,hashlib,json,subprocess
from prsice_scores import primary as original

ROOT=Path(__file__).resolve().parents[3]
BASE='59c7f5411c48d1f0ca37167ad4a3de474307f3a5'
FOLDER='docs/literature/prsice-source-audit'
POSITIVE=['P076','P115','P128','P147','P150']
KINDS=dict(P076='Population meta-analysis PRS and LD-panel comparison',P115='Tenfold MDD scores and external-GWAS prediction',P128='Ancestry-specific C&T and Bayesian-weight scoring',P147='Cross-validated target/nontarget PRS pleiotropy',P150='Additional European-to-KoGES PRSice comparison')

def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return(json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def committed(name):return subprocess.check_output(['git','--no-replace-objects','show',BASE+':'+name],cwd=ROOT,stdin=subprocess.DEVNULL)

def frozen():
    raw=(ROOT/FOLDER/'ROLES.json').read_bytes();require(raw==committed(FOLDER+'/ROLES.json')and(ROOT/FOLDER/'CENSUS.json').read_bytes()==committed(FOLDER+'/CENSUS.json'),'Changed frozen PRSice roles/census; never refresh old hashes')
    source=json.loads(raw);require(source['status']=='FIVE_PRSICE_SOURCE_ROLES_NOT_ADMITTED'and[r['paper_id']for r in source['roles']]==POSITIVE,'Changed source-role population');roles=[]
    for item in source['roles']:
        require(all(item[k]is False for k in('catalog_admitted','paper_edition_qualified','original_paper_analysis_reexecuted','whole_paper_reproduced')),'Expanded original source-role claim');versions=[]
        for v in item['reported_edition_contexts']:
            if v['literal']not in versions:versions.append(v['literal'])
        roles.append(dict(paper_id=item['paper_id'],role_kind=KINDS[item['paper_id']],original_observation=copy.deepcopy(item['source']['original_observation']),source_identity={k:item['source'][k]for k in('doi','pmcid','xml_sha256')},primary_anchors=copy.deepcopy(item['reviewed_paragraphs']),use_summary=item['summary'],reported_version='; '.join(versions)if versions else'unreported',reported_version_contexts=copy.deepcopy(item['reported_edition_contexts']),reported_settings=copy.deepcopy(item['settings']),separate_methods=copy.deepcopy(item['separate']),remaining_scope=item['limitation'],historical_analysis_status='Current pinned 2.3.5 workflows are qualified; historical patch editions, complete author commands/inputs and original paper analyses remain unqualified.',paper_edition_qualified=False,original_paper_analysis_reexecuted=False,whole_paper_reproduced=False,catalog_admitted=False,source_role_identity=pin(encoded(item))))
    require([r['original_observation']['version_reported_near_name']for r in roles]==[None,'2',None,'2',None],'Raw near-name versions changed');require([r['reported_version']for r in roles]==['PRSice-2 v.2','PRSice v2','unreported','PRSice version 2','unreported'],'Product/generic major versions were replaced with patch editions')
    return dict(schema_version=1,status='FIVE_PRSICE_SOURCE_ROLES_FOR_GUARDED_REVIEW',original_roles_identity=pin(raw),roles=roles)

def required_primary(cache,before_papers):
    for paper in json.loads(before_papers):
        path=Path(cache)/(paper['pmcid']+'.xml.gz')
        if not path.is_file():raise FileNotFoundError('Missing exact primary XML; dependent verification skipped: '+str(path))

def audit(cache,before_papers,source_root):
    required_primary(cache,before_papers);source_root=Path(source_root).absolute();require(source_root.resolve()==source_root,'Use the physical immutable predecessor source context');require((source_root/'docs/literature/omics-2023-2026/papers.json').read_bytes()==before_papers,'Changed original predecessor papers')
    for name in('tools/literature/prsice_scores/primary.py','tools/literature/collect_omics_review.py',FOLDER+'/ROLES.json',FOLDER+'/CENSUS.json'):require((source_root/name).read_bytes()==(ROOT/name).read_bytes()==committed(name),'Original source audit changed; do not refresh old hashes')
    actual=original.audit(before_papers,cache);roles=frozen();papers=json.loads(before_papers)
    for role in roles['roles']:require([u for u in next(r for r in papers if r['paper_id']==role['paper_id'])['tools']if u['tool_id']=='prsice']==[role['original_observation']],'Changed original PRSice observation')
    return roles,dict(primary_XML_checked=actual['primary_XML_checked'],matched_papers=actual['roles'],reviewed_source_roles=5,matched_paragraphs=actual['eligible_anchors'],extra_structures=actual['other_structures'],raw_near_name_versions_preserved=True,historical_paper_editions_qualified=False,original_paper_analyses_reexecuted=False)
