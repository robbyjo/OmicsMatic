"""Audit the 250 frozen XML identities and 14 explicit TwoSampleMR source roles."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from gsea_catalog.primary import identities,EXTRACTOR_SHA
from twosamplemr_source_roles import ROLES,ORIGINAL,ADDITIONS
ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/twosamplemr-source-review-proposal'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])TwoSampleMR(?![A-Za-z0-9_])',re.I)
def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def pointer(block):
    return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'],matching_text_sha256=pin(block.get('match_text',block['text']).encode())['sha256'])
def version_witness(byindex,spec):
    if 'edition' not in spec:return None
    literal,index=spec['edition'];block=byindex[index];text=block.get('match_text',block['text'])
    versions=list(re.finditer(r'(?:(?<=v\.)|(?<![0-9.]))'+re.escape(literal)+r'(?![0-9]|\.[0-9])',text,re.I))
    matches=[v for v in versions if any(0<=v.start()-name.end()<=50 for name in PATTERN.finditer(text))]
    require(bool(matches),'Explicit adjacent package edition missing or citation merged')
    return dict(literal=literal,paragraph_index=index,character_offset=matches[0].start(),matching_text_sha256=pin(text.encode())['sha256'])
def audit(cache):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Frozen extractor changed')
    sources=identities();candidates=[];blocks={}
    for identity in sources:
        with gzip.open(Path(cache)/(identity['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==identity['xml_sha256'],'Primary hash/capacity differs')
        tree=ET.fromstring(raw);doi=[n.text for n in tree.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];require(len(doi)==1 and doi[0].lower()==identity['doi'].lower(),'Primary DOI differs')
        byindex={b['paragraph_index']:b for b in paragraphs(tree)};anchors=[]
        for b in byindex.values():
            text=b.get('match_text',b['text']);matches=list(PATTERN.finditer(text))
            if matches:anchors.append(dict(paragraph_index=b['paragraph_index'],section=b['section'],paragraph_sha256=pin(b['text'].encode())['sha256'],matching_text_sha256=pin(text.encode())['sha256'],matches=[dict(literal=m.group(),character_offset=m.start()) for m in matches]))
        if anchors:
            require(identity['paper_id'] in ROLES,'Review new source-name candidate');spec=ROLES[identity['paper_id']]
            candidates.append(dict(**identity,anchors=anchors,reviewed_paragraph_witnesses=[pointer(byindex[i]) for i in spec['paragraphs']],reported_edition_witness=version_witness(byindex,spec)));blocks[identity['paper_id']]=byindex
    require({r['paper_id'] for r in candidates}==set(ROLES),'Different name candidate membership')
    roles=[]
    for pid,spec in ROLES.items():
        identity=next(r for r in sources if r['paper_id']==pid);pointers=[]
        for index in spec['paragraphs']:
            b=blocks[pid][index];pointers.append(dict(paragraph_index=index,section=b['section'],paragraph_sha256=pin(b['text'].encode())['sha256'],matching_text_sha256=pin(b.get('match_text',b['text']).encode())['sha256']))
        b=blocks[pid][spec['name_paragraph']];name=PATTERN.search(b.get('match_text',b['text']));require(name is not None,'Package name witness missing');edition=None
        if 'edition' in spec:edition=version_witness(blocks[pid],spec)
        roles.append(dict(paper_id=pid,pmcid=identity['pmcid'],doi=identity['doi'],source_xml_sha256=identity['xml_sha256'],tool_id='twosamplemr',kind=spec['kind'],usage_summary=spec['usage'],required_caution=spec['caution'],primary_evidence=pointers,source_name_witness=dict(literal=name.group(),character_offset=name.start(),paragraph_index=spec['name_paragraph']),reported_edition=edition,existing_observation=pid in ORIGINAL,proposed_observation=pid in ADDITIONS,author_runtime_executed=False,paper_executed_commit_confirmed=False,paper_edition_numerically_qualified=False,whole_tool_qualified=False,whole_paper_reproduced=False))
    result=dict(schema_version=1,status='PASS_FRESH250_TWOSAMPLEMR14_SOURCE_ROLE_PROPOSALS_NOT_ADMISSION',pattern=PATTERN.pattern,source_identities=sources,candidates=candidates,roles=roles,original_observations=ORIGINAL,proposed_additions=ADDITIONS,papers_scanned=250,positive_software_papers=14,new_family_credit=0,new_canonical_reviews=0,canonical_metadata_modified=False,whole_goal_complete=False)
    validate(result);return result
def validate(result):
    require(result['status']=='PASS_FRESH250_TWOSAMPLEMR14_SOURCE_ROLE_PROPOSALS_NOT_ADMISSION' and result['pattern']==PATTERN.pattern and result['source_identities']==identities(),'Changed source identity/pattern')
    require(result['papers_scanned']==250 and result['positive_software_papers']==14 and {r['paper_id'] for r in result['candidates']}==set(ROLES) and len(result['candidates'])==14,'Different candidate population')
    require(len(result['roles'])==14 and [r['paper_id'] for r in result['roles']]==list(ROLES),'Changed ordered role population')
    for role in result['roles']:
        pid=role['paper_id'];spec=ROLES[pid];identity=next(r for r in identities() if r['paper_id']==pid)
        require(role['tool_id']=='twosamplemr' and all(role[k]==identity[k] for k in ['pmcid','doi']) and role['source_xml_sha256']==identity['xml_sha256'],'Changed role identity')
        require(role['kind']==spec['kind'] and role['usage_summary']==spec['usage'] and role['required_caution']==spec['caution'],'Changed source role/caution')
        require([r['paragraph_index'] for r in role['primary_evidence']]==spec['paragraphs'],'Changed role paragraph membership')
        candidate=next(r for r in result['candidates'] if r['paper_id']==pid)
        require(role['primary_evidence']==candidate['reviewed_paragraph_witnesses'],'Changed primary paragraph/hash witness')
        anchor=next(r for r in candidate['anchors'] if r['paragraph_index']==spec['name_paragraph'])
        require({k:role['source_name_witness'][k] for k in ['literal','character_offset']} in anchor['matches'] and role['source_name_witness']['paragraph_index']==spec['name_paragraph'],'Changed literal package witness')
        edition=role['reported_edition'];require(edition==candidate['reported_edition_witness'] and (edition is None)==('edition' not in spec),'Invented/dropped/changed edition witness')
        if edition:require((edition['literal'],edition['paragraph_index'])==spec['edition'] and edition['character_offset']>=0,'Changed reported edition/provider')
        require(role['existing_observation']==(pid in ORIGINAL) and role['proposed_observation']==(pid in ADDITIONS),'Changed original/proposed membership')
        require(not any(role[k] for k in ['author_runtime_executed','paper_executed_commit_confirmed','paper_edition_numerically_qualified','whole_tool_qualified','whole_paper_reproduced']),'False author/edition/full-tool qualification')
    require(result['original_observations']==ORIGINAL and result['proposed_additions']==ADDITIONS and result['new_family_credit']==result['new_canonical_reviews']==0 and not result['canonical_metadata_modified'] and not result['whole_goal_complete'],'Source review cannot award catalog credit')
def frozen():
    result=json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes());validate(result);return result
