"""Recheck the complete primary corpus and five frozen PRSice source roles."""
from pathlib import Path
import gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as E
from collect_omics_review import paragraphs

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/prsice-source-audit'
TARGETS=['P076','P115','P128','P147','P150']
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:PRSice(?:[- ]?2)?)(?![A-Za-z0-9_])',re.I)

def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return(json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def census(papers_bytes,cache):
    papers=json.loads(papers_bytes);require(len(papers)==250 and len({p['doi']for p in papers})==250 and len({p['paper_id']for p in papers})==250,'Require the complete unique 250-paper source corpus')
    anchors=[];targets=[];texts={};extras=[]
    for paper in papers:
        pmc=paper['pmcid'];require(re.fullmatch('PMC[0-9]+',pmc),'Unsafe PMCID')
        with gzip.open(Path(cache)/(pmc+'.xml.gz'),'rb')as f:raw=f.read(67108865)
        require(len(raw)<=67108864 and pin(raw)['sha256']==paper['xml_sha256'],'Changed primary XML: '+pmc);doc=E.fromstring(raw)
        require(paper['doi'].lower()in{''.join(e.itertext()).strip().lower()for e in doc.findall('.//article-meta/article-id')if e.get('pub-id-type')=='doi'},'Changed primary DOI: '+pmc);rows=paragraphs(doc)
        for row in rows:row['paragraph_sha256']=pin(row['text'].encode())['sha256']
        hits=[row for row in rows if PATTERN.search(unicodedata.normalize('NFKC',row['match_text']))]
        anchors.extend(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=row['paragraph_sha256'])for row in hits)
        uses=[u for u in paper['tools']if u['tool_id']=='prsice']
        if uses:
            require(len(uses)==1,'Duplicate source observation');u=uses[0];require(next(row for row in rows if row['paragraph_index']==u['evidence_paragraph_index'])['paragraph_sha256']==u['evidence_paragraph_sha256'],'Changed original evidence paragraph')
            targets.append(dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=pmc,xml_sha256=paper['xml_sha256'],original_observation=u));texts[paper['paper_id']]={r['paragraph_index']:r for r in rows}
        for tag in('table-wrap','fig','supplementary-material'):
            for i,node in enumerate(doc.iter(tag)):
                text=' '.join(node.itertext())
                if PATTERN.search(unicodedata.normalize('NFKC',text)):extras.append(dict(paper_id=paper['paper_id'],tag=tag,index=i,text_sha256=pin(text.encode())['sha256']))
    require([p['paper_id']for p in targets]==TARGETS,'Changed full-corpus target set')
    return dict(status='PRIMARY_SOURCE_CENSUS_NO_IMPLEMENTATION_CREDIT',papers_identity=pin(papers_bytes),primary_XML_checked=250,targets=targets,anchors=anchors,other_structures=extras,catalog_admitted=False,new_family_credit=0,new_review_credit=0),texts

def audit(papers_bytes,cache):
    measured,texts=census(papers_bytes,cache);frozen=json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes());require(measured==frozen,'Changed frozen source census; use the exact preserved predecessor papers metadata')
    roles=json.loads((ROOT/FOLDER/'ROLES.json').read_bytes());require(roles['status']=='FIVE_PRSICE_SOURCE_ROLES_NOT_ADMITTED'and roles['census_identity']==pin(encoded(frozen))and[r['paper_id']for r in roles['roles']]==TARGETS,'Changed role/census binding')
    reviewed=[]
    for role,target in zip(roles['roles'],measured['targets'],strict=True):
        require(role['source']==target,'Changed raw source observation');require(all(role[k]is False for k in('catalog_admitted','paper_edition_qualified','original_paper_analysis_reexecuted','whole_paper_reproduced')),'Expanded source qualification claim')
        for anchor in role['reviewed_paragraphs']:
            row=texts[role['paper_id']][anchor['paragraph_index']];require(anchor=={k:row[k]for k in('paragraph_index','section','paragraph_sha256')},'Changed reviewed paragraph');reviewed.append(dict(paper_id=role['paper_id'],**anchor))
        for edition in role['reported_edition_contexts']:
            require(edition['literal']in unicodedata.normalize('NFKC',texts[role['paper_id']][edition['paragraph_index']]['text']),'Changed literal edition context')
    require(reviewed==measured['anchors'],'Incomplete or extra eligible source anchors');require([r['source']['original_observation']['version_reported_near_name']for r in roles['roles']]==[None,'2',None,'2',None],'Raw near-name versions changed')
    require(roles['roles'][2]['reported_edition_contexts']==[]and roles['roles'][4]['reported_edition_contexts']==[],'Do not infer precise editions from a product name');return dict(status='PASS_250_PRIMARY_XML_FIVE_PRSICE_ROLES',primary_XML_checked=250,roles=5,eligible_anchors=len(reviewed),other_structures=len(measured['other_structures']),catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_paper_reproduced=False)
