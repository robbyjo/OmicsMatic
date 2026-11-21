from pathlib import Path
import gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
R=Path('D:/projects/JLinAlg');T=R/'build/gc-1';O=T/'build/gcta-high-impact-1/source-intake-1';O.mkdir(exist_ok=False);sys.path.insert(0,str(T/'tools/literature'))
from collect_omics_review import paragraphs
papers=json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes());inventory=json.loads((T/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())
pattern=re.compile(r'(?<![A-Za-z0-9_])(?:GCTA(?:[- ](?:COJO|GREML|fastGWA(?:-GLMM)?|MLMA(?:-LOCO)?|mtCOJO|fastBAT|GSMR))?|COJO|mtCOJO|fastGWA(?:[- ]GLMM)?|GREML|MLMA(?:[- ]LOCO)?)(?![A-Za-z0-9_])',re.I)
from gsea_catalog.primary import PATTERN as gsea_pattern
oldgsea={c['paper_id'] for c in json.loads((T/'docs/literature/gsea-catalog-sources/CENSUS.json').read_bytes())['candidates']}
sources=[];candidates=[];gsea_followup=[];rawonly=[]
for p in papers:
    with gzip.open(R/'build/literature-review/xml'/(p['pmcid']+'.xml.gz'),'rb') as f:raw=f.read(64*1024*1024+1)
    assert len(raw)<=64*1024*1024 and hashlib.sha256(raw).hexdigest()==p['xml_sha256'];root=ET.fromstring(raw)
    dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi'];assert len(dois)==1 and dois[0].lower()==p['doi'].lower()
    sources.append({k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256']});anchors=[];blocks=paragraphs(root)
    for b in blocks:
        match_text=b.get('match_text',b['text']);hits=list(pattern.finditer(match_text));direct=list(pattern.finditer(b['text']))
        if hits or direct:
            anchors.append(dict(paragraph_index=b['paragraph_index'],section=b['section'],paragraph_sha256=hashlib.sha256(b['text'].encode()).hexdigest(),text=b['text'],match_text=match_text,matching_text_sha256=hashlib.sha256(match_text.encode()).hexdigest(),matched=[dict(literal=m.group(),start=m.start(),end=m.end()) for m in hits],raw_matched=[dict(literal=m.group(),start=m.start(),end=m.end()) for m in direct]))
        if p['paper_id'] not in oldgsea and gsea_pattern.search(match_text):
            gsea_followup.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],paragraph_index=b['paragraph_index'],section=b['section'],paragraph_sha256=hashlib.sha256(b['text'].encode()).hexdigest(),text=b['text'],match_text=match_text,reason='Citation-cleaned matching context not represented by the closed raw-text GSEA candidate pattern; follow up separately without editing old census.'))
    if anchors:
        candidates.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],title=p['title'],existing_observations=[t for t in p['tools'] if t['tool_id'] in ['gcta','cojo']],anchors=anchors))
gcta=next(r for r in inventory if r['id']=='gcta');cojo=next(r for r in inventory if r['id']=='cojo');union=sorted(set(gcta['papers'])|set(cojo['papers']))
result=dict(schema_version=1,status='GCTA_COJO_ALL_250_SOURCE_INTAKE_SEMANTIC_REVIEW_OPEN',baseline_commit='bdf1c3e8067896131d31cbca3e7de9191aa93a0d',pattern=pattern.pattern,matching_input='frozen collector citation-cleaned match_text, with raw text retained for paragraph identity',papers_scanned=250,source_identities=sources,candidates=candidates,original_gcta_observations=gcta['paper_count'],original_cojo_observations=cojo['paper_count'],original_gcta_cojo_union=union,original_union_count=len(union),canonical_metadata_modified=False,new_canonical_reviews=0,new_family_credit=0)
(O/'CANDIDATES.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');(O/'GSEA-FOLLOWUP-CANDIDATES.json').write_text(json.dumps(gsea_followup,indent=2)+'\n',encoding='utf-8',newline='\n')
for p in candidates:
    lines=[p['paper_id']+' '+p['title'],p['doi'],'EXISTING '+json.dumps(p['existing_observations']), '']
    for a in p['anchors']:lines.extend(['PARAGRAPH '+str(a['paragraph_index'])+' / '+a['section']+' / '+a['paragraph_sha256'],a['text'],''])
    (O/(p['paper_id']+'.txt')).write_text('\n'.join(lines),encoding='utf-8',newline='\n')
print(json.dumps(dict(papers_scanned=250,gcta=gcta['paper_count'],cojo=cojo['paper_count'],original_union=len(union),candidates=len(candidates),candidate_ids=[p['paper_id'] for p in candidates],additional_GSEA_followup_papers=sorted({p['paper_id'] for p in gsea_followup}),canonical_metadata_modified=False),indent=2),flush=True)
