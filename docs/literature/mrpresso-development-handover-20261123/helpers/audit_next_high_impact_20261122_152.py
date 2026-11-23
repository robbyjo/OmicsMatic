from pathlib import Path
import gzip,hashlib,json,re,sys,unicodedata,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/high-impact-audit-152';B.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from collect_omics_review import paragraphs,words
from gsea_catalog.primary import EXTRACTOR_SHA
assert hashlib.sha256((T/'tools/literature/collect_omics_review.py').read_bytes()).hexdigest()==EXTRACTOR_SHA
def save(name,value):(B/name).write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
papers=json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes())
inventory=json.loads((T/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())
selected=[r for r in inventory if r['id'] in ('smartpca','mrpresso','nfcore') or re.search('eigensoft|SCENIC',r['name'],re.I)]
save('INVENTORY.json',selected)
observations=[dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],tool=u) for p in papers for u in p['tools'] if u['tool_id'] in {r['id'] for r in selected}]
save('OBSERVATIONS.json',observations)
pattern=re.compile(r'(?<![A-Za-z0-9_])(?:smartpca|eigensoft|MR[- ]?PRESSO|nf[- /]?core|SCENIC\+?|pySCENIC)(?![A-Za-z0-9_])',re.I)
anchors=[];tables=[];identities=[]
for paper in papers:
    path=R/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')
    with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
    assert len(raw)<=64*1024*1024 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
    tree=ET.fromstring(raw);doi=[e.text for e in tree.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi']
    assert len(doi)==1 and doi[0].lower()==paper['doi'].lower()
    identities.append({k:paper[k] for k in ('paper_id','pmcid','doi','xml_sha256')})
    for paragraph in paragraphs(tree):
        text=unicodedata.normalize('NFKC',paragraph.get('match_text',paragraph['text'])).translate(str.maketrans({c:'-' for c in '‐‑‒–—−'}))
        matches=sorted(set(m.group(0) for m in pattern.finditer(text)))
        if matches:anchors.append(dict(paper_id=paper['paper_id'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],
            section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),matches=matches,private_full_paragraph=paragraph['text']))
    for table in tree.iter('table-wrap'):
        text=words(table)
        if pattern.search(text):tables.append(dict(paper_id=paper['paper_id'],table_id=table.get('id'),private_table_text=text,sha256=hashlib.sha256(text.encode()).hexdigest()))
save('PRIVATE-ANCHORS.json',anchors);save('PRIVATE-TABLES.json',tables)
save('IDENTITIES.json',identities)
summary=[]
for row in selected:
    summary.append(dict(id=row['id'],name=row['name'],status=row['implementation_status'],papers=row['papers'],
        source=row.get('implementation_source'),scope=row.get('implementation_scope'),reviews=row.get('source_roles_reviewed')))
save('SUMMARY.json',dict(status='PASS_ALL250_PRIMARY_NEXT_PRIORITY_CENSUS_SOURCE_REVIEW_PENDING',papers_checked=250,
    selected_inventory=summary,matching_paragraphs=len(anchors),table_candidates=len(tables),new_reviews=0,new_family_credit=0,whole_goal_complete=False))
print(json.dumps(dict(selected=summary,observations=[dict(paper_id=o['paper_id'],id=o['tool']['tool_id'],summary=o['tool']['use_summary'],version=o['tool']['version_reported_near_name'],paragraph=o['tool']['evidence_paragraph_index']) for o in observations],paragraphs=len(anchors),tables=len(tables)),indent=2),flush=True)
