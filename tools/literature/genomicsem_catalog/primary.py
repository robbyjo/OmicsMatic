"""Bind five existing observations to the unchanged full-cohort primary audit."""
from pathlib import Path
import copy,hashlib,json
from genomicsem import primary as original

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/genomicsem-source-audit'
POSITIVE=['P035','P125','P131','P140','P150']
ROLE_SHA='805d152a048c476453d1ff3318e25171943c4a2b9004d584589cd760dd5bed04'
KINDS=dict(P035='Psychiatric factor models and stratified enrichment',P125='Subtype correlations and conditional regression',P131='Higher-order GWAS and FUSION-based T-SEM',P140='Cholesky GWAS by subtraction',P150='MetS factor models and hierarchical GWAS')

def require(value,message):
    if not value:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return(json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def frozen():
    raw=(ROOT/FOLDER/'ROLES.json').read_bytes();require(pin(raw)['sha256']==ROLE_SHA,'Original source-role interpretations changed');source=json.loads(raw);require(source['status']=='FIVE_REVIEWED_SOURCE_ROLES_NOT_ADMITTED'and [x['paper_id']for x in source['roles']]==POSITIVE,'Changed source-role population');roles=[]
    for item in source['roles']:
        require(all(item[k]is False for k in('catalog_admitted','paper_edition_qualified','original_paper_analysis_reexecuted','whole_paper_reproduced')),'Changed frozen historical admission flag');versions=[]
        for v in item['reported_edition_contexts']:
            if v['literal']not in versions:versions.append(v['literal'])
        roles.append(dict(paper_id=item['paper_id'],role_kind=KINDS[item['paper_id']],original_observation=copy.deepcopy(item['source']['original_observation']),source_identity={k:item['source'][k]for k in('doi','pmcid','xml_sha256')},primary_anchors=copy.deepcopy(item['reviewed_paragraphs']),use_summary=item['summary'],reported_version='; '.join(versions)if versions else'unreported',reported_version_contexts=copy.deepcopy(item['reported_edition_contexts']),reported_settings=copy.deepcopy(item['settings']),separate_methods=copy.deepcopy(item['separate']),remaining_scope=item['limitation'],historical_analysis_status='Current pinned APIs are qualified; author edition, exact scripts/inputs and original analyses remain unqualified.',paper_edition_qualified=False,original_paper_analysis_reexecuted=False,whole_paper_reproduced=False,catalog_admitted=False,source_role_identity=pin(encoded(item))))
    require(roles[0]['reported_version']=='0.5.0'and roles[3]['reported_version']=='0.0.3f','Do not normalize distinct reported editions');require([r['original_observation']['version_reported_near_name']for r in roles]==[None,None,None,None,'0.0.5'],'Contextual versions overwrote raw fields')
    return dict(schema_version=1,status='FIVE_GENOMICSEM_SOURCE_ROLES_FOR_GUARDED_REVIEW',original_roles_identity=pin(raw),roles=roles)

def required_primary(cache,before_papers):
    for paper in json.loads(before_papers):
        path=Path(cache)/(paper['pmcid']+'.xml.gz')
        if not path.is_file():raise FileNotFoundError('Missing exact primary XML; dependent verification skipped: '+str(path))

def audit(cache,before_papers,source_root):
    required_primary(cache,before_papers)
    source_root=Path(source_root).absolute();require(source_root.resolve()==source_root,'Use the physical pre-admission source context');require((source_root/'docs/literature/omics-2023-2026/papers.json').read_bytes()==before_papers,'Changed immutable predecessor papers');require((source_root/FOLDER/'ROLES.json').read_bytes()==(ROOT/FOLDER/'ROLES.json').read_bytes(),'Changed original source roles')
    module='tools/literature/genomicsem/primary.py';require((source_root/module).read_bytes()==(ROOT/module).read_bytes(),'Original primary-source reader changed; do not refresh old hashes')
    actual=original.audit(source_root,cache);roles=frozen();papers=json.loads(before_papers)
    for role in roles['roles']:
        paper=next(x for x in papers if x['paper_id']==role['paper_id']);require([u for u in paper['tools']if u['tool_id']=='genomicsem']==[role['original_observation']],'Changed original GenomicSEM observation')
    return roles,dict(primary_XML_checked=actual['primary_XML_checked'],matched_papers=5,reviewed_source_roles=5,matched_paragraphs=actual['anchors'],extra_structures=actual['other_structures'],raw_near_name_versions_preserved=True,historical_paper_editions_qualified=False,original_paper_analyses_reexecuted=False)
