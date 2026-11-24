"""Audit all frozen primary sources and the 44 individually read cytometry roles."""
from pathlib import Path
import copy,gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words,matching_words

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/cytometry-catalog-sources'
DRAFT='docs/literature/cytometry-source-roles-2026-11-23.json'
TARGETS={'flowjo','facsdiva'}
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:Flow[\s_-]*Jo|FACS[\s_-]*Diva)(?![A-Za-z0-9_])',re.I)
def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def frozen():
    roles=json.loads((ROOT/FOLDER/'ROLES.json').read_bytes());draft=json.loads((ROOT/DRAFT).read_bytes())
    require(roles['status']=='44_CYTOMETRY_SOURCE_ROLES_BEFORE_GUARDED_ADMISSION' and roles['draft_source']==pin((ROOT/DRAFT).read_bytes()),'Changed frozen source roles')
    require(len(roles['roles'])==44 and len({(r['paper_id'],r['tool_id']) for r in roles['roles']})==44,'Changed unique role population')
    for role,old in zip(roles['roles'],draft['records'],strict=True):
        require(all(role[k]==v for k,v in old.items()),'Changed individually read role')
        require(not role['source_role_review_applied'] and not role['original_paper_analysis_reexecuted'] and not role['whole_paper_reproduced'] and not role['required_original_inputs_available'] and not role['vendor_statistics_qualified'],'Expanded paper qualification')
    return roles
def census(cache,papers_raw):
    papers=json.loads(papers_raw);require(len(papers)==250,'Changed frozen paper population');identities=[];anchors=[];structures=[]
    for paper in papers:
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(67108865)
        require(len(raw)<=67108864 and pin(raw)['sha256']==paper['xml_sha256'],'Changed XML: '+paper['paper_id']);tree=ET.fromstring(raw);doi=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi'];require(len(doi)==1 and doi[0].lower()==paper['doi'].lower(),'Changed DOI identity');identities.append({k:paper[k] for k in ('paper_id','pmcid','doi','xml_sha256')});standard=paragraphs(tree);seen={r['text'] for r in standard}
        for row in standard:
            matches=list(PATTERN.finditer(unicodedata.normalize('NFKC',row.get('match_text',row['text']))))
            if matches:anchors.append(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=pin(row['text'].encode())['sha256'],matches=sorted({m.group() for m in matches})))
        for tag in ('p','table-wrap','code','preformat'):
            for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
                text=words(element)
                if (tag!='p' or text not in seen) and PATTERN.search(matching_words(element)):structures.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,element_id=element.get('id'),text_sha256=pin(text.encode())['sha256']))
    return dict(status='ALL250_CYTOMETRY_NAMES_AND_EXTRA_STRUCTURES_CHECKED',papers_checked=250,identities=identities,anchors=anchors,structures=structures,extractor=pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes()),canonical_modified=False)
def audit(cache,papers_raw):
    roles=frozen();measured=census(cache,papers_raw);require(measured==json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes()),'Changed full source census');require(len(measured['anchors'])==48 and not measured['structures'],'Unreviewed source structures');papers={r['paper_id']:r for r in json.loads(papers_raw)}
    for role in roles['roles']:
        paper=papers[role['paper_id']];require(all(role[k]==paper[k] for k in ('doi','pmcid','xml_sha256')),'Changed role identity');uses=[u for u in paper['tools'] if u['tool_id']==role['tool_id']];require(uses==[role['original_observation']],'Changed original observation')
        pattern=re.compile(r'flow[\s_-]*jo' if role['tool_id']=='flowjo' else r'facs[\s_-]*diva',re.I)
        selected=[{k:a[k] for k in ('paragraph_index','section','paragraph_sha256')} for a in measured['anchors'] if a['paper_id']==role['paper_id'] and any(pattern.fullmatch(x) for x in a['matches'])];require(selected==role['primary_anchors'],'Changed role-specific primary anchors');require(any(a['paragraph_sha256']==uses[0]['evidence_paragraph_sha256'] for a in selected),'Raw primary evidence is not covered')
    actual={(paper['paper_id'],u['tool_id']) for paper in papers.values() for u in paper['tools'] if u['tool_id'] in TARGETS};require(actual=={(r['paper_id'],r['tool_id']) for r in roles['roles']},'Unreviewed target observation')
    return copy.deepcopy(roles),dict(primary_XML_checked=250,matched_papers=33,matched_paragraphs=48,reviewed_source_roles=44,extra_structures=0,original_paper_analyses_reexecuted=False)
