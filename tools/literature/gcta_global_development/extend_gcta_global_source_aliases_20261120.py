from pathlib import Path
import gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';O=T/'build/gcta-global-1/source-alias-followup-1';O.mkdir();sys.path.insert(0,str(T/'tools/literature'))
from collect_omics_review import paragraphs
pattern=re.compile(r'genome[\s\-\u2010-\u2015]*wide[\s\-\u2010-\u2015]*complex[\s\-\u2010-\u2015]*trait[\s\-\u2010-\u2015]*analysis',re.I)
papers=json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes());prior=json.loads((T/'build/gcta-global-1/source-intake-1/CANDIDATES.json').read_bytes());known={p['paper_id']:p for p in prior['candidates']};hits=[];new=[]
for p in papers:
    with gzip.open(R/'build/literature-review/xml'/(p['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
    assert len(raw)<=64*1024*1024 and hashlib.sha256(raw).hexdigest()==p['xml_sha256']
    for paragraph in paragraphs(ET.fromstring(raw)):
        text=paragraph.get('match_text',paragraph['text'])
        if pattern.search(text):
            row=dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),text=paragraph['text']);hits.append(row)
            if p['paper_id'] not in known or not any(a['paragraph_index']==paragraph['paragraph_index'] for a in known[p['paper_id']]['anchors']):new.append(row)
(O/'RAW-PRIVATE.json').write_text(json.dumps(dict(pattern=pattern.pattern,hits=hits,new_contexts=new),indent=2)+'\n',encoding='utf-8',newline='\n')
public=dict(status='ALL_250_EXPANDED_GCTA_NAME_FOLLOWUP',papers_scanned=250,pattern=pattern.pattern,hits=[{k:v for k,v in row.items() if k!='text'} for row in hits],new_contexts=[{k:v for k,v in row.items() if k!='text'} for row in new],canonical_metadata_modified=False)
(O/'READBACK.json').write_text(json.dumps(public,indent=2)+'\n',encoding='utf-8',newline='\n');print('Expanded GCTA alias contexts',[(r['paper_id'],r['paragraph_index']) for r in hits],'new',[(r['paper_id'],r['paragraph_index']) for r in new],flush=True)
