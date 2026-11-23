"""Reconcile six roles against all frozen primary XML and exact author blobs."""
from pathlib import Path
import copy,gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words,matching_words
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[3]
POSITIVE=['P008','P021','P047','P061','P094','P153']
FOLDER='docs/literature/scenicplus-catalog-sources'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:SCENIC\+?|pySCENIC|Cluster[- ]?Buster|cbust|pycisTopic|cisTopic|pycisTarget)(?![A-Za-z0-9_])',re.I)

def require(value,message):
    if not value:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def census(cache,papers_raw):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed primary extractor')
    papers=json.loads(papers_raw);require(len(papers)==250,'Changed cohort');identities=[];anchors=[];structures=[]
    for paper in papers:
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'],'Changed XML: '+paper['paper_id'])
        tree=ET.fromstring(raw);dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==paper['doi'].lower(),'Changed DOI')
        identities.append({k:paper[k] for k in ('paper_id','doi','pmcid','xml_sha256')});standard=paragraphs(tree);seen={r['text'] for r in standard}
        for row in standard:
            text=unicodedata.normalize('NFKC',row.get('match_text',row['text']))
            if PATTERN.search(text):anchors.append(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=pin(row['text'].encode())['sha256'],matches=sorted(set(m.group(0) for m in PATTERN.finditer(text)))))
        for tag in ('p','table-wrap','code','preformat'):
            for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
                display=words(element)
                if (tag!='p' or display not in seen) and PATTERN.search(matching_words(element)):structures.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,element_id=element.get('id'),text_sha256=pin(display.encode())['sha256']))
    return dict(status='ALL250_SCENICPLUS_AND_DEPENDENCY_NAME_CENSUS_NOT_SEMANTIC_COMPLETION',papers_checked=250,extractor_sha256=EXTRACTOR_SHA,identities=identities,anchors=anchors,structures=structures,full_primary_texts_redistributed=False)

def frozen():
    result=json.loads((ROOT/FOLDER/'ROLES.json').read_bytes())
    require(result['status']=='SIX_SCENICPLUS_SOURCE_ROLES_AND_ONE_MISSED_OBSERVATION_PRE_ADMISSION','Changed role kind')
    require([r['paper_id'] for r in result['roles']]==POSITIVE and not any(r['source_role_review_applied'] for r in result['roles']),'Changed frozen roles')
    require(result['new_family_credit']==result['new_reviews']==0 and not result['canonical_modified'],'Draft falsely admitted')
    return result

def audit(cache,papers_raw,author_root=None):
    measured=census(cache,papers_raw);require(measured==json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes()),'Changed all250 source census')
    roles=frozen();papers=json.loads(papers_raw)
    for role in roles['roles']:
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools'] if u['tool_id']=='scenicplus']
        require(uses==([] if role['original_observation'] is None else [role['original_observation']]),'Changed original observation')
        require(paper['xml_sha256']==role['xml_sha256'] and [a for a in measured['anchors'] if a['paper_id']==role['paper_id']]==role['anchors'],'Changed source anchors')
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as stream:tree=ET.fromstring(stream.read())
        source={row['paragraph_index']:row for row in paragraphs(tree)}
        for anchor in role['additional_supporting_paragraphs']:
            row=source[anchor['paragraph_index']];require(row['section']==anchor['section'] and pin(row['text'].encode())['sha256']==anchor['paragraph_sha256'],'Changed supporting paragraph')
    require({r['paper_id'] for r in measured['anchors'] if any(x.lower()=='scenic+' for x in r['matches'])}==set(POSITIVE),'Unreviewed positive source candidate')
    checked=0
    if author_root is not None:
        manifest=json.loads((ROOT/FOLDER/'AUTHOR-SOURCES.json').read_bytes());require(len(manifest['files'])==42 and manifest['commit']=='8096f6548b112f97f0f2eb739f25c6c9dbdba8b4','Changed author source scope')
        for name,identity in manifest['files'].items():
            require(not Path(name).is_absolute() and '..' not in Path(name).parts,'Unsafe author path');raw=(Path(author_root)/name).read_bytes()
            require(pin(raw)=={k:identity[k] for k in ('bytes','sha256')} and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==identity['git_blob'],'Changed exact author blob')
            checked+=1
    return copy.deepcopy(roles),dict(primary_XML_checked=250,additional_supporting_paragraphs=2,exact_author_blobs_checked=checked,original_author_notebooks_executed=False)
