"""Reaudit all250 frozen XML sources; keep reported editions and unresolved preparation literal."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from gsea_catalog.primary import identities,EXTRACTOR_SHA
from gcta_source_roles import ROLES,EXCLUSIONS,PROPOSED_ADDITIONS,PROPOSED_REMOVAL

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/gcta-catalog-sources'
EXPANDED=re.compile(r'genome[\s\-\u2010-\u2015]*wide[\s\-\u2010-\u2015]*complex[\s\-\u2010-\u2015]*trait[\s\-\u2010-\u2015]*analysis',re.I)
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:GCTA(?:[- ](?:COJO|GREML|fastGWA(?:-GLMM)?|MLMA(?:-LOCO)?|mtCOJO|fastBAT|GSMR))?|COJO|mtCOJO|fastGWA(?:[- ]GLMM)?|GREML|MLMA(?:[- ]LOCO)?)(?![A-Za-z0-9_])|genome[\s\-\u2010-\u2015]*wide[\s\-\u2010-\u2015]*complex[\s\-\u2010-\u2015]*trait[\s\-\u2010-\u2015]*analysis',re.I)

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(v):return (json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode()
def anchor(block):return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'],matching_text_sha256=pin(block.get('match_text',block['text']).encode())['sha256'])
def foundation():
    profile=json.loads((ROOT/'docs/literature/gcta-global-development/PROFILE.json').read_bytes())
    for name,key in [('SOURCE-ROLES.json','source_roles'),('SOURCE-CENSUS.json','source_census')]:require(pin((ROOT/'docs/literature/gcta-global-development'/name).read_bytes())==profile[key],'Changed qualified source crosswalk')
    require(pin((ROOT/'tools/literature/gcta_source_roles.py').read_bytes())==profile['source_inputs']['tools/literature/gcta_source_roles.py'],'Changed reviewed source decisions')
    roles=json.loads((ROOT/'docs/literature/gcta-global-development/SOURCE-ROLES.json').read_bytes());census=json.loads((ROOT/'docs/literature/gcta-global-development/SOURCE-CENSUS.json').read_bytes())
    require(len(roles['roles'])==27 and len(ROLES)==19 and len(EXCLUSIONS)==4,'Different source decision population')
    return roles,census
def audit(cache):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed frozen paragraph extractor')
    reviewed,previous=foundation();sources=identities();require([{k:r[k] for k in ['paper_id','pmcid','doi','xml_sha256']} for r in sources]==previous['source_identities'],'Different primary identities')
    candidates=[];witnesses={}
    for source in sources:
        with gzip.open(Path(cache)/(source['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==source['xml_sha256'],'Primary XML hash/capacity differs: '+source['paper_id'])
        root=ET.fromstring(raw);dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];require(len(dois)==1 and dois[0].lower()==source['doi'].lower(),'Primary DOI differs')
        blocks=paragraphs(root);selected=[b for b in blocks if PATTERN.search(b.get('match_text',b['text'])) or PATTERN.search(b['text'])]
        if not selected:continue
        pid=source['paper_id'];require(pid in ROLES or pid in EXCLUSIONS,'Review new GCTA candidate: '+pid)
        old=next((r for r in previous['candidates'] if r['paper_id']==pid),None);require(old is not None,'Unexpected source candidate')
        pointers=[anchor(b) for b in selected];require(pointers==old['anchors'],'Changed exact candidate anchors: '+pid)
        candidates.append(dict(paper_id=pid,pmcid=source['pmcid'],doi=source['doi'],anchors=pointers,existing_tool_ids=old['existing_tool_ids']))
        witnesses[pid]={b['paragraph_index']:b for b in blocks}
    require({r['paper_id'] for r in candidates}==set(ROLES)|set(EXCLUSIONS),'Missing source candidate')
    roles=[]
    for frozen in reviewed['roles']:
        role=copy.deepcopy(frozen);pid=role['paper_id'];spec=ROLES[pid];blocks=witnesses[pid]
        require(role['usage_summary']==spec['usage'] and role['required_caution']==spec['caution'] and role['operations']==spec['operations'] and role['reported_edition']==spec['edition'],'Reviewed role specification changed')
        require(role['primary_evidence']==[anchor(blocks[i]) for i in spec['paragraphs']],'Reviewed source paragraph differs')
        editions=[('PCA','1.94.1'),('REML','1.94')] if pid=='P248' else [('reported',spec['edition'])] if spec['edition'] else []
        versions=[]
        for operation,literal in editions:
            locations=[(i,m.start()) for i in spec['paragraphs'] for m in re.finditer(r'(?<![0-9])'+re.escape(literal)+r'(?![A-Za-z0-9]|\.[0-9])',blocks[i]['text'])]
            require(bool(locations),'Reported edition has no literal source witness: '+pid+' '+literal);i,offset=locations[0];versions.append(dict(operation=operation,literal=literal,paragraph_index=i,character_offset=offset))
        first=blocks[spec['paragraphs'][0]];match_text=first.get('match_text',first['text']);name=EXPANDED.search(match_text) or PATTERN.search(match_text);require(name is not None,'Missing literal primary tool-name witness')
        role.update(source_xml_sha256=next(r['xml_sha256'] for r in sources if r['paper_id']==pid),reported_version_witnesses=versions,
            source_name_witness=dict(literal=name.group(),paragraph_index=first['paragraph_index'],character_offset=name.start(),matching_text_sha256=anchor(first)['matching_text_sha256']),
            existing_observation=pid not in ['P112','P169'],proposed_observation=pid in ['P112','P169'],matching_runtime_edition_only=(spec['edition']=='1.94.1'),
            preparation_and_author_settings_complete=False,author_runtime_executed=False,whole_tool_qualified=False,whole_paper_reproduced=False)
        roles.append(role)
    result=dict(schema_version=1,status='PASS_FRESH_250_GCTA_SOURCES_23_CANDIDATES_27_ROLES_19_EXECUTED_PAPERS',pattern=PATTERN.pattern,papers_scanned=250,source_identities=sources,candidates=candidates,roles=roles,exclusions=reviewed['exclusions'],proposed_additions=PROPOSED_ADDITIONS,proposed_removal=PROPOSED_REMOVAL,canonical_metadata_modified=False,new_family_credit=0,new_canonical_reviews=0,whole_goal_complete=False)
    validate(result);return result
def validate(result):
    reviewed,previous=foundation();require(result['schema_version']==1 and result['status']=='PASS_FRESH_250_GCTA_SOURCES_23_CANDIDATES_27_ROLES_19_EXECUTED_PAPERS','Different source audit schema/status')
    require(result['pattern']==PATTERN.pattern and result['source_identities']==identities() and result['papers_scanned']==250,'Different source population/extractor')
    require(len(result['candidates'])==23 and {r['paper_id'] for r in result['candidates']}==set(ROLES)|set(EXCLUSIONS),'Different candidate population')
    for candidate in result['candidates']:
        old=next(r for r in previous['candidates'] if r['paper_id']==candidate['paper_id']);require(candidate=={k:old[k] for k in ['paper_id','pmcid','doi','anchors','existing_tool_ids']},'Changed candidate witnesses/identity/membership')
    require(len(result['roles'])==27 and [(r['paper_id'],r['tool_id']) for r in result['roles']]==[(r['paper_id'],r['tool_id']) for r in reviewed['roles']],'Different positive label population/order')
    for role,old in zip(result['roles'],reviewed['roles'],strict=True):
        require(all(role[k]==v for k,v in old.items()),'Changed reviewed role: '+role['paper_id']);require(not role['author_runtime_executed'] and not role['whole_paper_reproduced'] and not role['preparation_and_author_settings_complete'],'False author reproduction claim')
        require(role['matching_runtime_edition_only']==(role['reported_edition']=='1.94.1'),'False edition match')
        require(bool(role['reported_version_witnesses'])==bool(role['reported_edition']),'Missing/invented reported version')
        expected=[('PCA','1.94.1'),('REML','1.94')] if role['paper_id']=='P248' else [('reported',role['reported_edition'])] if role['reported_edition'] else []
        require([(v['operation'],v['literal']) for v in role['reported_version_witnesses']]==expected and all(v['paragraph_index'] in ROLES[role['paper_id']]['paragraphs'] and isinstance(v['character_offset'],int) and v['character_offset']>=0 for v in role['reported_version_witnesses']),'Changed source edition/operation witness')
        name=role['source_name_witness'];first=role['primary_evidence'][0];require(name['paragraph_index']==first['paragraph_index'] and name['matching_text_sha256']==first['matching_text_sha256'] and isinstance(name['character_offset'],int) and name['character_offset']>=0 and PATTERN.fullmatch(name['literal']) is not None,'Changed literal source-name witness')
        require(role['source_xml_sha256']==next(r['xml_sha256'] for r in result['source_identities'] if r['paper_id']==role['paper_id']),'Changed role XML identity')
    require(result['exclusions']==reviewed['exclusions'] and result['proposed_additions']==PROPOSED_ADDITIONS and result['proposed_removal']==PROPOSED_REMOVAL,'Different exclusions or additions/removal')
    require(not result['canonical_metadata_modified'] and result['new_family_credit']==result['new_canonical_reviews']==0,'Source audit cannot award credit')
def frozen():
    result=json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes());validate(result);return result
