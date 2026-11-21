from pathlib import Path
import gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/lm-1';B=T/'build/limma-high-impact-1';O=B/'source-intake-1';O.mkdir(parents=True,exist_ok=False);sys.path.insert(0,str(T/'tools/literature'))
from collect_omics_review import paragraphs
papers=json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes());inventory=json.loads((T/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes());pattern=re.compile(r'(?<![A-Za-z0-9_])(?:limma|voom(?:WithQualityWeights)?|removeBatchEffect|duplicateCorrelation|lmFit|eBayes|squeezeVar|fitFDist|normalizeBetweenArrays|normalizeWithinArrays)(?![A-Za-z0-9_])|linear models for (?:microarray|microarrays|microarray and omics) data',re.I);sources=[];candidates=[]
for paper in papers:
    with gzip.open(R/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
    assert len(raw)<=64*1024*1024 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];assert len(dois)==1 and dois[0].lower()==paper['doi'].lower();sources.append({k:paper[k] for k in ['paper_id','pmcid','doi','xml_sha256']});anchors=[]
    for block in paragraphs(root):
        text=block.get('match_text',block['text']);hits=list(pattern.finditer(text))
        if hits or pattern.search(block['text']):anchors.append(dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=hashlib.sha256(block['text'].encode()).hexdigest(),matching_text_sha256=hashlib.sha256(text.encode()).hexdigest(),text=block['text'],match_text=text,matched=[dict(literal=m.group(),start=m.start(),end=m.end()) for m in hits]))
    if anchors:
        row=dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],title=paper['title'],existing_observations=[r for r in paper['tools'] if r['tool_id']=='limma'],anchors=anchors);candidates.append(row)
        (O/(paper['paper_id']+'.txt')).write_text(paper['paper_id']+' '+paper['title']+'\n'+paper['doi']+'\n\n'+'\n\n'.join('PARAGRAPH '+str(a['paragraph_index'])+' / '+a['section']+'\n'+a['text'] for a in anchors)+'\n',encoding='utf-8',newline='\n')
tool=next(r for r in inventory if r['id']=='limma');result=dict(schema_version=1,status='LIMMA_ALL250_SOURCE_INTAKE_REVIEW_OPEN',pattern=pattern.pattern,papers_scanned=250,source_identities=sources,candidates=candidates,existing_inventory_row=tool,canonical_metadata_modified=False,new_review_credit=0,new_family_credit=0)
(O/'CANDIDATES-PRIVATE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
(O/'READBACK.json').write_text(json.dumps({**{k:v for k,v in result.items() if k not in ['candidates']},'candidates':[{**{k:v for k,v in r.items() if k!='anchors'},'anchors':[{k:v for k,v in a.items() if k not in ['text','match_text']} for a in r['anchors']]} for r in candidates]},indent=2)+'\n',encoding='utf-8',newline='\n')
print('limma all250 census: existing',tool['paper_count'],'candidates',len(candidates),[(r['paper_id'],[a['paragraph_index'] for a in r['anchors']]) for r in candidates],flush=True)
