# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every chromVAR mention against retained primary XML across the fixed 250-paper cohort."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
    out=BASE/'chromvar-primary-role-audit-1';assert not out.exists();out.mkdir();data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());assert len(papers)==250;named=re.compile(r'(?<![A-Za-z0-9_])chromVAR(?![A-Za-z0-9_])',re.I);records=[];census=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));matches=[dict(p,sha256=sha(p['text'].encode('utf8'))) for p in ps if named.search(p['match_text'])];catalogued=[t for t in paper['tools'] if t['tool_id']=='chromvar'];assert len(catalogued)<=1;census.append(dict(paper_id=paper['paper_id'],xml_sha256=paper['xml_sha256'],matching_paragraphs=len(matches),catalogued=bool(catalogued)))
        if matches:
            indices={p['paragraph_index'] for p in matches};neighbors=[dict(p,sha256=sha(p['text'].encode('utf8'))) for p in ps if p['paragraph_index'] not in indices and any(abs(p['paragraph_index']-i)==1 for i in indices)];record={k:paper[k] for k in ['paper_id','pmcid','doi','title','xml_sha256']};record.update(matches=matches,neighbors=neighbors,catalogued=catalogued);records.append(record)
        for tool in catalogued:assert any(m['paragraph_index']==tool['evidence_paragraph_index'] and m['sha256']==tool['evidence_paragraph_sha256'] for m in matches)
    result=dict(status='audited-retained-primary-chromVAR-mentions-not-catalog-promoted',papers_scanned=250,source=dict(path='tools/literature/audit_chromvar_roles.py',sha256=sha(Path(__file__).read_bytes())),catalogued_papers=sum(c['catalogued'] for c in census),mentioned_papers=len(records),census=census,records=records)
    (out/'PRIMARY-USES.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n');print(json.dumps(dict(status=result['status'],papers_scanned=250,mentioned_papers=len(records),catalogued_papers=result['catalogued_papers'])))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary source identity checks')
    main()
