"""Reaudit all 250 XML identities, PEER roles, and generic peer/person-name exclusions."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from gsea_catalog.primary import identities,EXTRACTOR_SHA
from peer_source_roles import ROLES,ORIGINAL,ADDITIONS

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/peer-source-review-proposal'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])PEER(?![A-Za-z0-9_])|probabilistic[\s-]+estimations?[\s-]+of[\s-]+(?:gene[\s-]+)?expression[\s-]+residuals?',re.I)

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def audit(cache):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Frozen paragraph extractor changed')
    require(set(ROLES)==set(ORIGINAL)|set(ADDITIONS) and len(ROLES)==18 and set(ORIGINAL).isdisjoint(ADDITIONS),'Different reviewed role membership')
    candidates=[];blocks_by_paper={};sources=identities()
    for identity in sources:
        with gzip.open(Path(cache)/(identity['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==identity['xml_sha256'],'Primary XML hash/capacity differs: '+identity['paper_id'])
        root=ET.fromstring(raw);doi=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];require(len(doi)==1 and doi[0].lower()==identity['doi'].lower(),'Primary DOI differs')
        blocks=paragraphs(root);anchors=[];positive=False
        for block in blocks:
            text=block.get('match_text',block['text']);matches=[]
            for match in PATTERN.finditer(text):
                literal=match.group()
                if literal=='PEER' or len(literal)>4:kind='software-mention';positive=True
                elif re.match(r'peer[ -]*review',text[match.start():],re.I):kind='generic-peer-review'
                elif identity['paper_id']=='P052' and re.search(r'\bDan\s+$',text[:match.start()]):kind='published-reviewer-person-name'
                else:raise ValueError('Review unclassified PEER/peer occurrence: '+identity['paper_id']+' '+str(block['paragraph_index']))
                matches.append(dict(literal=literal,character_offset=match.start(),kind=kind))
            if matches:anchors.append(dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'],matching_text_sha256=pin(text.encode())['sha256'],matches=matches))
        if anchors:
            require(not positive or identity['paper_id'] in ROLES,'New positive PEER source candidate requires review')
            candidates.append(dict(**identity,positive_software_use=positive,anchors=anchors));blocks_by_paper[identity['paper_id']]={b['paragraph_index']:b for b in blocks}
    require({r['paper_id'] for r in candidates if r['positive_software_use']}==set(ROLES),'Positive PEER candidate missing')
    roles=[]
    for pid,spec in ROLES.items():
        candidate=next(r for r in candidates if r['paper_id']==pid);anchors={a['paragraph_index']:a for a in candidate['anchors']};pointers=[]
        for index in spec['paragraphs']:
            anchor=anchors[index];require(any(w['kind']=='software-mention' for w in anchor['matches']),'Role paragraph lacks software witness');pointers.append({k:anchor[k] for k in ['paragraph_index','section','paragraph_sha256','matching_text_sha256']})
        first=anchors[spec['paragraphs'][0]];name=next(w for w in first['matches'] if w['kind']=='software-mention');edition=None
        if spec.get('edition'):
            block=blocks_by_paper[pid][spec['paragraphs'][0]]['text'];match=re.search(r'PEER factors \(v\.(1\.3)\)',block);require(match is not None,'PEER version label is not explicitly witnessed')
            edition=dict(literal=match[1],paragraph_index=spec['paragraphs'][0],character_offset=match.start(1))
        roles.append(dict(paper_id=pid,pmcid=candidate['pmcid'],doi=candidate['doi'],source_xml_sha256=candidate['xml_sha256'],tool_id='peer',kind=spec['kind'],usage_summary=spec['usage'],required_caution=spec['caution'],reported_factor_counts=spec['counts'],
            primary_evidence=pointers,source_name_witness=copy.deepcopy(name),reported_edition=edition,existing_observation=pid in ORIGINAL,proposed_observation=pid in ADDITIONS,
            author_runtime_executed=False,author_preparation_complete=False,paper_executed_commit_confirmed=False,whole_tool_qualified=False,whole_paper_reproduced=False))
    result=dict(schema_version=1,status='PASS_FRESH250_PEER18_POSITIVE_ROLES_WITH_CASEFOLD_EXCLUSIONS_NOT_ADMISSION',pattern=PATTERN.pattern,source_identities=sources,candidates=candidates,roles=roles,original_observations=ORIGINAL,proposed_additions=ADDITIONS,
        papers_scanned=250,positive_software_papers=18,case_insensitive_candidate_papers=len(candidates),negative_candidate_papers=sum(not r['positive_software_use'] for r in candidates),
        canonical_metadata_modified=False,new_family_credit=0,new_canonical_reviews=0,whole_goal_complete=False)
    validate(result);return result

def validate(result):
    require(result['status']=='PASS_FRESH250_PEER18_POSITIVE_ROLES_WITH_CASEFOLD_EXCLUSIONS_NOT_ADMISSION' and result['pattern']==PATTERN.pattern and result['source_identities']==identities(),'Changed primary/source pattern identity')
    require(result['papers_scanned']==250 and result['positive_software_papers']==18 and result['case_insensitive_candidate_papers']==181 and result['negative_candidate_papers']==163,'Changed source candidate population')
    require(len(result['candidates'])==181 and {r['paper_id'] for r in result['candidates'] if r['positive_software_use']}==set(ROLES),'Changed positive software classification')
    for candidate in result['candidates']:
        software=any(w['kind']=='software-mention' for a in candidate['anchors'] for w in a['matches'])
        require(candidate['positive_software_use']==software,'Inconsistent software/exclusion classification')
        for anchor in candidate['anchors']:
            for witness in anchor['matches']:
                require(witness['kind'] in ['software-mention','generic-peer-review','published-reviewer-person-name'] and witness['character_offset']>=0,'Unknown/invalid source classification witness')
                if witness['kind']=='published-reviewer-person-name':require(candidate['paper_id']=='P052' and witness['literal']=='Peer','Changed reviewer-name exclusion')
    require(len(result['roles'])==18 and [r['paper_id'] for r in result['roles']]==list(ROLES),'Changed ordered role population')
    for role in result['roles']:
        pid=role['paper_id'];spec=ROLES[pid];identity=next(r for r in identities() if r['paper_id']==pid);candidate=next(r for r in result['candidates'] if r['paper_id']==pid)
        require(role['tool_id']=='peer' and all(role[k]==identity[k] for k in ['pmcid','doi']) and role['source_xml_sha256']==identity['xml_sha256'],'Changed role identity')
        require(role['kind']==spec['kind'] and role['usage_summary']==spec['usage'] and role['required_caution']==spec['caution'] and role['reported_factor_counts']==spec['counts'],'Changed source-use role or caution')
        expected=[{k:next(a for a in candidate['anchors'] if a['paragraph_index']==index)[k] for k in ['paragraph_index','section','paragraph_sha256','matching_text_sha256']} for index in spec['paragraphs']]
        require(role['primary_evidence']==expected,'Changed primary paragraph witness')
        first=next(a for a in candidate['anchors'] if a['paragraph_index']==spec['paragraphs'][0]);require(role['source_name_witness'] in [w for w in first['matches'] if w['kind']=='software-mention'],'Changed literal name/offset')
        require((role['reported_edition'] is None)==('edition' not in spec),'Invented/dropped PEER edition')
        if role['reported_edition']:require(role['reported_edition']==dict(literal='1.3',paragraph_index=11,character_offset=66) and pid=='P212','Changed reported edition/provider')
        require(role['existing_observation']==(pid in ORIGINAL) and role['proposed_observation']==(pid in ADDITIONS),'Changed original/proposed observation membership')
        require(not any(role[k] for k in ['author_runtime_executed','author_preparation_complete','paper_executed_commit_confirmed','whole_tool_qualified','whole_paper_reproduced']),'False author/full-workflow execution claim')
    require(result['original_observations']==ORIGINAL and result['proposed_additions']==ADDITIONS and not result['canonical_metadata_modified'] and result['new_family_credit']==result['new_canonical_reviews']==0 and not result['whole_goal_complete'],'Source review cannot award catalog/implementation credit')

def frozen():
    result=json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes());validate(result);return result
