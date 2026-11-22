from pathlib import Path
import gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from prepare_genomicranges_20261122_114 import R,PRIOR
sys.path.insert(0,str(PRIOR/'tools/literature'))
from collect_omics_review import paragraphs
from handover_inventory_20261122_111 import save
papers=json.loads((PRIOR/'docs/literature/omics-2023-2026/papers.json').read_bytes());inventory=json.loads((PRIOR/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes());target=next(r for r in inventory if r['id']=='genomicranges');print('INVENTORY',json.dumps(target,ensure_ascii=False),flush=True)
records=[]
for paper in papers:
 uses=[u for u in paper['tools'] if u['tool_id']=='genomicranges']
 if not uses:continue
 path=R/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];blocks=paragraphs(ET.fromstring(raw));named=[b for b in blocks if re.search(r'GenomicRanges',b.get('match_text',b['text']),re.I)]
 record={'paper_id':paper['paper_id'],'title':paper['title'],'pmcid':paper['pmcid'],'doi':paper['doi'],'journal':paper['journal'],'primary_XML_sha256':paper['xml_sha256'],'uses':uses,'named_paragraphs':named};records.append(record)
 print('PAPER',json.dumps({k:record[k] for k in ['paper_id','title','pmcid','doi','journal']},ensure_ascii=False),flush=True)
 print('USES',json.dumps(uses,ensure_ascii=False),flush=True)
 for block in named:print('PRIMARY',json.dumps({'paragraph_index':block['paragraph_index'],'section':block['section'],'characters':len(block['text']),'text':block['text'][:2400],'complete_text_displayed':len(block['text'])<=2400},ensure_ascii=False),flush=True)
save(R/'build/genomicranges-source-screening-20261122-114.json',{'inventory':target,'papers':records,'source_fields_unmodified':True,'new_catalog_credit':0,'whole_goal_complete':False})
