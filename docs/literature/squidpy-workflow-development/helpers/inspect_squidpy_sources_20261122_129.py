from pathlib import Path
import gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
T=Path('D:/projects/JLinAlg/build/fu-1');sys.path.insert(0,str(T/'tools/literature'))
from collect_omics_review import paragraphs,words
from gsea_catalog.primary import identities
B=T/'build/squidpy-1';B.mkdir();O=B/'primary-intake-1';O.mkdir();cache=Path('D:/projects/JLinAlg/build/literature-review/xml');selected=[];tables=[]
pattern=re.compile(r'(?<![A-Za-z0-9_])squidpy(?![A-Za-z0-9_])|\bsq\.(?:gr|pl|im)\.',re.I)
for identity in identities():
 raw=gzip.decompress((cache/(identity['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==identity['xml_sha256'];tree=ET.fromstring(raw)
 dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi'];assert len(dois)==1 and dois[0].lower()==identity['doi'].lower()
 hits=[]
 for block in paragraphs(tree):
  if pattern.search(block.get('match_text',block['text'])):
   hits.append(dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=hashlib.sha256(block['text'].encode()).hexdigest(),text=block['text']))
 if hits:selected.append(dict(**{k:identity[k] for k in ['paper_id','pmcid','doi','xml_sha256']},anchors=hits))
 for table in tree.iter('table-wrap'):
  text=words(table)
  if pattern.search(text):tables.append(dict(paper_id=identity['paper_id'],id=table.get('id'),text=text))
result=dict(primary_XML_checked=250,candidates=selected,table_candidates=tables,new_family_credit=0,new_reviews=0,canonical_modified=False)
(O/'PRIVATE-SOURCE-EXCERPTS.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(primary_XML_checked=250,candidates=[dict(paper=x['paper_id'],anchors=[b['paragraph_index'] for b in x['anchors']]) for x in selected],table_candidates=[dict(paper=x['paper_id'],id=x['id']) for x in tables])),flush=True)
for record in selected:
 print(record['paper_id'],flush=True)
 for hit in record['anchors']:print(hit['paragraph_index'],hit['text'],flush=True)
