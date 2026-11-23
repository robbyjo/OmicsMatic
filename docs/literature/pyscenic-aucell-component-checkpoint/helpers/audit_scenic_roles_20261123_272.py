from pathlib import Path
import gzip,hashlib,json,re,sys,unicodedata,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-1/primary-audit-1';B.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from collect_omics_review import paragraphs,words,matching_words;from gsea_catalog.primary import EXTRACTOR_SHA
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(n,obj):(B/n).write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
assert pin((T/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA
rawpapers=(T/'docs/literature/omics-2023-2026/papers.json').read_bytes();papers=json.loads(rawpapers);assert len(papers)==250
pattern=re.compile(r'(?<![A-Za-z0-9_])(?:SCENIC\+?|pySCENIC|AUCell|GRNBoost2?|GENIE3)(?![A-Za-z0-9_])',re.I)
identities=[];anchors=[];structures=[]
for paper in papers:
 with gzip.open(R/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz'),'rb') as f:raw=f.read(64*1024*1024+1)
 assert len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'];tree=ET.fromstring(raw)
 dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi'];assert len(dois)==1 and dois[0].lower()==paper['doi'].lower()
 identities.append({k:paper[k] for k in ('paper_id','doi','pmcid','xml_sha256')});standard=paragraphs(tree);seen={r['text'] for r in standard}
 for row in standard:
  text=unicodedata.normalize('NFKC',row.get('match_text',row['text']))
  if pattern.search(text):anchors.append(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=pin(row['text'].encode())['sha256'],matches=sorted(set(m.group(0) for m in pattern.finditer(text))),private_text=row['text']))
 for tag in ('p','table-wrap','code','preformat'):
  for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
   display=words(element)
   if (tag!='p' or display not in seen) and pattern.search(matching_words(element)):structures.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,element_id=element.get('id'),text_sha256=pin(display.encode())['sha256'],private_text=display))
observations=[dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],tool=u) for p in papers for u in p['tools'] if u['tool_id'] in ('scenic','scenicplus')]
save('PRIVATE-ANCHORS.json',anchors);save('PRIVATE-STRUCTURES.json',structures);save('IDENTITIES.json',identities);save('OBSERVATIONS.json',observations)
save('SUMMARY.json',dict(status='PASS_ALL250_SCENIC_CENSUS_INDIVIDUAL_ROLES_PENDING',papers_checked=250,papers_identity=pin(rawpapers),extractor_sha256=EXTRACTOR_SHA,anchors=[{k:v for k,v in r.items() if k!='private_text'} for r in anchors],structures=[{k:v for k,v in r.items() if k!='private_text'} for r in structures],new_family_credit=0,new_reviews=0,whole_goal_complete=False))
for r in anchors:
 if any(x in ('SCENIC','pySCENIC') for x in r['matches']):print(json.dumps(dict(paper=r['paper_id'],paragraph=r['paragraph_index'],context=r['private_text']),ensure_ascii=False),flush=True)
print(json.dumps(dict(papers_checked=250,anchors=len(anchors),structures=len(structures),observations=len(observations)),ensure_ascii=False))
