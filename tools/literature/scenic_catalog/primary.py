"""Recheck all frozen primary XML and five individually interpreted SCENIC roles."""
from pathlib import Path
import copy,gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words,matching_words
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[3]
POSITIVE=['P058','P070','P100','P145','P225']
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:SCENIC\+?|pySCENIC|AUCell|GRNBoost2?|GENIE3)(?![A-Za-z0-9_])',re.I)
FOLDER='docs/literature/scenic-catalog-sources'

def require(value,message):
    if not value:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def census(cache,papers_raw):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed primary paragraph extractor')
    papers=json.loads(papers_raw);require(len(papers)==250,'Changed cohort size')
    identities=[];anchors=[];structures=[]
    for paper in papers:
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'],'Changed primary XML: '+paper['paper_id'])
        tree=ET.fromstring(raw);dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==paper['doi'].lower(),'Changed DOI: '+paper['paper_id'])
        identities.append({k:paper[k] for k in ('paper_id','doi','pmcid','xml_sha256')});standard=paragraphs(tree);seen={r['text'] for r in standard}
        for row in standard:
            text=unicodedata.normalize('NFKC',row.get('match_text',row['text']))
            if PATTERN.search(text):anchors.append(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=pin(row['text'].encode())['sha256'],matches=sorted(set(m.group(0) for m in PATTERN.finditer(text)))))
        for tag in ('p','table-wrap','code','preformat'):
            for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
                display=words(element)
                if (tag!='p' or display not in seen) and PATTERN.search(matching_words(element)):structures.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,element_id=element.get('id'),text_sha256=pin(display.encode())['sha256']))
    return dict(papers_checked=250,extractor_sha256=EXTRACTOR_SHA,primary_identities=identities,anchors=anchors,structures=structures,full_primary_texts_redistributed=False,new_reviews=0,new_family_credit=0,whole_goal_complete=False)

def frozen():
    result=json.loads((ROOT/FOLDER/'ROLES.json').read_bytes())
    require(result['status']=='SCENIC_FIVE_SOURCE_ROLES_WITH_HISTORICAL_EDITION_SKIP_NOT_ADMISSION' and [r['paper_id'] for r in result['roles']]==POSITIVE and result['new_reviews']==result['new_family_credit']==0 and not result['canonical_modified'],'Changed frozen role scope')
    require(not any(r['source_role_review_applied'] for r in result['roles']),'Frozen draft falsely admitted')
    return result

def audit(cache,papers_raw):
    measured=census(cache,papers_raw);expected=json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes())
    require(measured==expected,'Changed all250 SCENIC/SCENIC+ census; individually review new candidates')
    result=frozen();papers=json.loads(papers_raw)
    for role in result['roles']:
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools'] if u['tool_id']=='scenic']
        require(uses==[role['original_observation']] and paper['xml_sha256']==role['xml_sha256'],'Changed source extraction: '+role['paper_id'])
        require([r for r in measured['anchors'] if r['paper_id']==role['paper_id']]==role['anchors'],'Changed source role anchors')
    require(result['missed_provider_candidates'][0]['paper_id']=='P153' and result['missed_provider_candidates'][0]['tool_id']=='scenicplus','Lost separate SCENIC+ candidate')
    return copy.deepcopy(result)
