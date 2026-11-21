"""Reaudit all250 primary XML identities and23 limma/voom roles with pinned code witnesses."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from limma_source_roles import ROLES,VOOM_ROLES,EXCLUSIONS,PROPOSED_ADDITIONS,PROPOSED_REMOVAL
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/limma-global-development'
PROPOSAL='docs/literature/limma-source-review-proposal/CENSUS.json'
PROPOSAL_PIN=dict(bytes=102291,sha256='3bfb57f35a9d01038e481adf49c956ab9503606c4b1fb92247d656231dd0b661')

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def foundation():
    raw=(ROOT/PROPOSAL).read_bytes();require(pin(raw)==PROPOSAL_PIN,'Changed source-role proposal foundation')
    proposal=json.loads(raw);require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed frozen paragraph extractor')
    require(len(ROLES)==18 and len(VOOM_ROLES)==5 and set(VOOM_ROLES)<=set(ROLES) and set(ROLES)|set(EXCLUSIONS)=={r['paper_id'] for r in proposal['reviews']},'Different source decision population')
    require(PROPOSED_ADDITIONS==['P088','P183','P203'] and PROPOSED_REMOVAL=='P059','Different proposed membership change')
    return proposal

def code_witnesses(cache=None):
    document=json.loads((ROOT/FOLDER/'AUTHOR-CODE-WITNESSES.json').read_bytes())
    require(document['status']=='PASS_TWO_PINNED_LINKED_AUTHOR_CODE_SOURCE_WITNESSES_NOT_EXECUTION' and {r['paper_id'] for r in document['repositories']}=={'P183','P203'},'Different linked-code witness population')
    for row in document['repositories']:
        require(re.fullmatch('[0-9a-f]{40}',row['commit']) is not None and not row['author_pipeline_executed'] and not row['paper_executed_commit_correspondence_proven'] and not row['original_author_code_redistributed'],'False code execution/correspondence claim')
        if cache is not None:
            source=Path(cache)/row['paper_id']/'files'/row['file']['path'];raw=source.read_bytes();require(pin(raw)=={k:row['file'][k] for k in ['bytes','sha256']},'Pinned author code differs')
            git_blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest();require(git_blob==row['file']['git_blob_sha'],'Different author Git blob')
            lines=raw.decode('utf-8-sig').splitlines()
            for witness in row['witnesses']:
                line=lines[witness['line_number']-1];require(pin(line.encode())['sha256']==witness['line_sha256'] and re.search(witness['pattern'],line) is not None,'Author code symbol/line witness differs')
    observation=document['P228_public_code_observation'];require(not observation['observed_repository_publicly_accessible'] and not observation['dataset_downloaded'] and not observation['author_pipeline_executed'],'False missing-code/data execution claim')
    require(next(r['status'] for r in observation['observations'] if r['name']=='linked-repository')==404,'Different frozen public code availability observation')
    return document

def anchor(block):return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'],matching_text_sha256=pin(block.get('match_text',block['text']).encode())['sha256'])

def audit(primary_cache,author_code_cache=None):
    proposal=foundation();code=code_witnesses(author_code_cache);pattern=re.compile(proposal['pattern'],re.I);reviewed={r['paper_id']:r for r in proposal['reviews']};blocks_by_paper={};candidates=[]
    for identity in proposal['source_identities']:
        with gzip.open(Path(primary_cache)/(identity['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==identity['xml_sha256'],'Primary XML differs: '+identity['paper_id'])
        root=ET.fromstring(raw);doi=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];require(len(doi)==1 and doi[0].lower()==identity['doi'].lower(),'Primary DOI differs')
        blocks=paragraphs(root);hits=[b for b in blocks if pattern.search(b['text']) or pattern.search(b.get('match_text',b['text']))]
        if not hits:continue
        pid=identity['paper_id'];require(pid in reviewed,'Unreviewed new limma candidate: '+pid);prior=reviewed[pid]
        require([anchor(b) for b in hits]==[{k:a[k] for k in ['paragraph_index','section','paragraph_sha256','matching_text_sha256']} for a in prior['anchors']],'Changed primary anchors: '+pid)
        blocks_by_paper[pid]={b['paragraph_index']:b for b in blocks};candidates.append(dict(paper_id=pid,pmcid=identity['pmcid'],doi=identity['doi'],anchors=[anchor(b) for b in hits]))
    require(set(blocks_by_paper)==set(ROLES)|set(EXCLUSIONS),'Missing source candidate')
    roles=[]
    for pid,spec in ROLES.items():
        identity=next(r for r in proposal['source_identities'] if r['paper_id']==pid);blocks=blocks_by_paper[pid]
        for tool in ['limma']+(['voom'] if pid in VOOM_ROLES else []):
            selected=spec if tool=='limma' else VOOM_ROLES[pid];pointers=[anchor(blocks[i]) for i in selected['paragraphs']]
            witness_pattern=re.compile(r'(?<![A-Za-z0-9_])'+('limma' if tool=='limma' and pid not in ['P183','P203'] else 'voom')+r'(?![A-Za-z0-9_])',re.I)
            first=blocks[selected['paragraphs'][0]];match_text=first.get('match_text',first['text']);name=witness_pattern.search(match_text);require(name is not None,'Missing tool/method name witness: '+pid)
            versions=[]
            for operation,literal,index in spec['editions']:
                found=re.search(r'(?<![0-9])'+re.escape(literal)+r'(?![A-Za-z0-9]|\.[0-9])',blocks[index]['text']);require(found is not None,'Reported edition not in source: '+pid)
                versions.append(dict(operation=operation,literal=literal,paragraph_index=index,character_offset=found.start()))
            related=copy.deepcopy(next((r for r in code['repositories'] if r['paper_id']==pid),None))
            roles.append(dict(paper_id=pid,pmcid=identity['pmcid'],doi=identity['doi'],tool_id=tool,catalog_family_id='limma',operations=selected['operations'],usage_summary=selected['usage'],required_caution=spec['caution'],
                primary_evidence=pointers,source_xml_sha256=identity['xml_sha256'],source_name_witness=dict(literal=name.group(),paragraph_index=first['paragraph_index'],character_offset=name.start(),matching_text_sha256=anchor(first)['matching_text_sha256']),
                reported_editions=versions,qualified_related_current_operations=spec['supported'] if tool=='limma' else ['VOOM'] if 'VOOM' in spec['supported'] else [],linked_code_source=related,provider_attribution_unresolved=(pid=='P228'),
                existing_observation=(tool=='voom' or pid not in PROPOSED_ADDITIONS),proposed_observation=(tool=='limma' and pid in PROPOSED_ADDITIONS),author_runtime_executed=False,preparation_and_author_settings_complete=False,whole_tool_qualified=False,whole_paper_reproduced=False))
    # Callable names are often unstated; retain that uncertainty rather than infer
    # lmFit/eBayes/dreamlet bindings solely from a related current component.
    result=dict(schema_version=1,status='PASS_FRESH_250_LIMMA_VOOM_SOURCES_20_CANDIDATES_23_ROLES_18_PAPERS',papers_scanned=250,source_identities=proposal['source_identities'],candidates=candidates,roles=roles,exclusions=[dict(paper_id=pid,reason=spec['reason'],primary_evidence=[anchor(blocks_by_paper[pid][i]) for i in spec['paragraphs']]) for pid,spec in EXCLUSIONS.items()],proposed_additions=PROPOSED_ADDITIONS,proposed_removal=PROPOSED_REMOVAL,voom_family_alias='limma',alias_additional_integration_credit=0,author_code_cache_verified=author_code_cache is not None,canonical_metadata_modified=False,new_catalog_credit=0,whole_goal_complete=False)
    validate(result);return result

def validate(result):
    proposal=foundation();require(result['status']=='PASS_FRESH_250_LIMMA_VOOM_SOURCES_20_CANDIDATES_23_ROLES_18_PAPERS' and result['source_identities']==proposal['source_identities'] and result['papers_scanned']==250,'Different source identity/schema')
    require(len(result['candidates'])==20 and len(result['roles'])==23 and len({(r['paper_id'],r['tool_id']) for r in result['roles']})==23,'Different role population')
    require({r['paper_id'] for r in result['roles']}==set(ROLES) and {r['paper_id'] for r in result['exclusions']}==set(EXCLUSIONS),'Different positive/excluded papers')
    code=code_witnesses();old={r['paper_id']:r for r in proposal['reviews']}
    expected_pairs={(pid,'limma') for pid in ROLES}|{(pid,'voom') for pid in VOOM_ROLES}
    require({(r['paper_id'],r['tool_id']) for r in result['roles']}==expected_pairs,'Different positive tool-label membership')
    for role in result['roles']:
        pid,tool=role['paper_id'],role['tool_id'];spec=ROLES[pid] if tool=='limma' else VOOM_ROLES[pid]
        identity=next(r for r in proposal['source_identities'] if r['paper_id']==pid)
        require(all(role[k]==identity[k] for k in ['pmcid','doi']) and role['source_xml_sha256']==identity['xml_sha256'],'Changed role primary identity')
        require(role['usage_summary']==spec['usage'] and role['operations']==spec['operations'] and role['required_caution']==ROLES[pid]['caution'],'Changed reviewed role prose/operations')
        require(not role['author_runtime_executed'] and not role['preparation_and_author_settings_complete'] and not role['whole_tool_qualified'] and not role['whole_paper_reproduced'],'False author/full-workflow claim')
        expected_ops=ROLES[pid]['supported'] if tool=='limma' else ['VOOM'] if 'VOOM' in ROLES[pid]['supported'] else []
        require(role['provider_attribution_unresolved']==(pid=='P228') and role['qualified_related_current_operations']==expected_ops,'False provider/component correspondence')
        require([(v['operation'],v['literal'],v['paragraph_index']) for v in role['reported_editions']]==ROLES[pid]['editions'],'Changed/dropped operation-specific edition')
        require([a['paragraph_index'] for a in role['primary_evidence']]==spec['paragraphs'],'Changed primary paragraph population')
        expected_anchors=[{k:next(a for a in old[pid]['anchors'] if a['paragraph_index']==i)[k] for k in ['paragraph_index','section','paragraph_sha256','matching_text_sha256']} for i in spec['paragraphs']]
        require(role['primary_evidence']==expected_anchors,'Changed primary source witnesses')
        first=next(a for a in old[pid]['anchors'] if a['paragraph_index']==spec['paragraphs'][0]);w=role['source_name_witness']
        require(w['paragraph_index']==first['paragraph_index'] and w['matching_text_sha256']==first['matching_text_sha256'] and any(w['literal']==a['literal'] and w['character_offset']==a['start'] for a in first['matched']),'Changed literal source name/case/offset')
        require(role['linked_code_source']==next((r for r in code['repositories'] if r['paper_id']==pid),None),'Changed linked author code evidence')
        require(role['catalog_family_id']=='limma' and role['existing_observation']==(tool=='voom' or pid not in PROPOSED_ADDITIONS) and role['proposed_observation']==(tool=='limma' and pid in PROPOSED_ADDITIONS),'Changed catalog family/membership flags')
    require(result['proposed_additions']==PROPOSED_ADDITIONS and result['proposed_removal']==PROPOSED_REMOVAL and result['voom_family_alias']=='limma' and result['alias_additional_integration_credit']==0,'Different membership/family counting')
    require(not result['canonical_metadata_modified'] and result['new_catalog_credit']==0 and not result['whole_goal_complete'],'Source review cannot award integration credit')
