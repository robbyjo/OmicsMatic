from pathlib import Path
import gzip,json,re,sys,xml.etree.ElementTree as ET
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-catalog-255'
sys.path.insert(0,str(T/'tools/literature'));from smartpca_catalog import model as m;from collect_omics_review import paragraphs,matching_words,words
cache=R/'build/literature-review/xml';primary=m.frozen_primary();selected={r['paper_id']:r for r in primary['roles']};source=[];expanded=[];extra=[]
pattern=re.compile(r'(?<![A-Za-z0-9_])(?:EIGENSTRAT|lsqproject|shrinkmode|fstonly)(?![A-Za-z0-9_])|(?:DReichLab|argriffing)/EIG(?:ENSOFT)?\b',re.I)
for paper in json.loads((T/m.META[0]).read_bytes()):
 with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb') as f:raw=f.read(64*1024*1024+1)
 assert pin(cache/(paper['pmcid']+'.xml.gz'))['bytes']>0
 import hashlib
 assert len(raw)<=64*1024*1024 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
 tree=ET.fromstring(raw)
 standard=paragraphs(tree)
 for row in standard:
  record=dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],text_sha256=hashlib.sha256(row['text'].encode()).hexdigest())
  if paper['paper_id'] in selected and row['paragraph_index'] in [a['paragraph_index'] for a in selected[paper['paper_id']]['anchors']]:source.append(dict(**record,text=row['text']))
  if pattern.search(row.get('match_text',row['text'])):expanded.append(dict(**record,excerpt=row['text'][:1500]))
 seen={r['text'] for r in standard}
 for tag in ('p','table-wrap','code','preformat'):
  for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
   display=words(element)
   if (tag!='p' or display not in seen) and pattern.search(matching_words(element)):extra.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,text_sha256=hashlib.sha256(display.encode()).hexdigest()))
science=json.loads((T/'docs/literature/smartpca-fst-qualification/STATUS.json').read_bytes())['source_code'];assert all(pin(T/n)==wanted for n,wanted in science.items())
private=B/'private-source-contexts';private.mkdir(exist_ok=True)
save(private/'SOURCE-CONTEXTS.json',dict(source_anchors=source,expanded_contexts=expanded))
save(B/'SOURCE-CONTEXTS.json',dict(status='PASS_CURRENT_QUALIFIED_SMARTPCA_SCIENCE_UNCHANGED',original_native_scientific_source_changed=False,qualified_source_files=len(science),source_identities=science,source_anchors=[{k:v for k,v in r.items() if k!='text'} for r in source],full_primary_texts_included=False))
additional=sorted({x['paper_id'] for x in expanded}-set(selected));assert not additional and not extra,'Additional name/structure candidate needs source review'
save(B/'EXPANDED-NAME-SCREEN.json',dict(papers_checked=250,pattern=pattern.pattern,candidates=[{k:v for k,v in r.items() if k!='excerpt'} for r in expanded],candidates_papers=sorted({x['paper_id'] for x in expanded}),additional_candidate_papers=additional,additional_body_table_code_candidates=extra,individual_classification_pending=False,classification='All expanded-name/option candidates are in the six individually reviewed papers; no additional positive paper inferred. External supplementary/code completeness remains a full-goal review task.',new_family_credit=0,new_reviews=0))
print(json.dumps(dict(source_anchors=len(source),expanded_candidates=len(expanded),additional_candidate_papers=additional,additional_body_table_code_candidates=extra),indent=2))
