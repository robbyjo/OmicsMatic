from pathlib import Path
import json,gzip,sys,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';sys.path.insert(0,str(T/'tools/literature'))
from collect_omics_review import paragraphs
source=json.loads((B/'source-alias-followup-1/RAW-PRIVATE.json').read_bytes());papers=json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes())
for row in source['new_contexts']:
    paper=next(p for p in papers if p['paper_id']==row['paper_id']);print(paper['paper_id'],paper['title'],paper['doi'],flush=True);print('Matched',row['paragraph_index'],row['text'],flush=True)
    if row['paper_id']=='P169':
        with gzip.open(R/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz'),'rb') as stream:root=ET.fromstring(stream.read())
        for p in paragraphs(root):
            if p['paragraph_index'] in [23,25]:print('Context',p['paragraph_index'],p['section'],p['text'],flush=True)
