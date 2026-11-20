from pathlib import Path
import gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');G=R/'build/gs-1';O=G/'build/gsea-high-impact-1/source-census-intake-1';O.mkdir(exist_ok=False)
sys.dont_write_bytecode=True;sys.path.insert(0,str(G/'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
def enc(v):return (json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode()
def save(n,v):(O/n).write_bytes(enc(v))
def digest(raw):return hashlib.sha256(raw).hexdigest()
try:
    papers=json.loads((G/'docs/literature/omics-2023-2026/papers.json').read_bytes());verify_fixed_cohort(G,G/'docs/literature/omics-2023-2026',papers)
    pattern=re.compile(r'(?<![A-Za-z0-9_])(?:preranked[- ]?)?GSEA(?![A-Za-z0-9_])|gene[- ]set enrichment analys[ie]s',re.I)
    identities=[];candidates=[];original=[]
    for p in papers:
        path=R/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')
        with gzip.open(path,'rb') as f:raw=f.read(64*1024*1024+1)
        assert len(raw)<=64*1024*1024 and digest(raw)==p['xml_sha256'],p['paper_id']
        xml=ET.fromstring(raw);dois=[n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi']
        assert len(dois)==1 and dois[0].lower()==p['doi'].lower(),p['paper_id']
        identities.append({k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256']})
        uses=[t for t in p['tools'] if t['tool_id']=='gsea']
        if uses:assert len(uses)==1;original.append(p['paper_id'])
        hits=[]
        for block in paragraphs(xml):
            text=block['text'];matches=list(pattern.finditer(text))
            if not matches:continue
            hits.append(dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=digest(text.encode()),
                matched=[dict(text=m.group(),start=m.start(),end=m.end()) for m in matches],text=text))
        if hits:candidates.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],existing_observation=uses[0] if uses else None,anchors=hits))
    assert len(papers)==250 and len(original)==19
    value=dict(schema_version=1,status='CANDIDATE_GSEA_SOURCE_CONTEXTS_REQUIRING_SEMANTIC_REVIEW',base_commit='0de612c6182b87d42a1d574af5f06a99630a0924',
        papers_scanned=250,source_identities=identities,original_observations=original,candidate_papers=len(candidates),candidates=candidates,
        canonical_metadata_modified=False,new_reviews_appended=0,new_family_credit=0)
    save('CANDIDATES.json',value)
    save('COMPLETED.json',dict(status=value['status'],papers=250,existing_observations=19,candidate_papers=len(candidates),
        candidate_additions=[c['paper_id'] for c in candidates if c['existing_observation'] is None],new_canonical_credit=0))
    print(json.dumps(json.loads((O/'COMPLETED.json').read_bytes()),indent=2),flush=True)
except Exception as error:
    save('FAILURE.json',dict(error=repr(error),detail=traceback.format_exc()));raise
