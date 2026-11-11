# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Census all fixed-cohort HTSeq mentions without attributing nearby work to HTSeq."""
import argparse
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from catalog_fixed_cohort import verify_fixed_cohort
from collect_omics_review import paragraphs

ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/htseq-runtime') and not out.exists()
    out.mkdir(parents=True)
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    verify_fixed_cohort(ROOT,ROOT/'docs/literature/omics-2023-2026',papers)
    named=re.compile(r'(?<![A-Za-z0-9_])HT[-_ ]?Seq(?![A-Za-z0-9_])',re.I)
    records=[]
    census=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        ps=paragraphs(ET.fromstring(raw))
        found=[p for p in ps if named.search(p['match_text'])]
        selected=[t for t in paper['tools'] if t['tool_id']=='htseq']
        census.append(dict(paper_id=paper['paper_id'],xml_sha256=paper['xml_sha256'],named_paragraphs=len(found),catalogued=bool(selected)))
        if not found:
            assert not selected
            continue
        assert len(selected)<=1
        if selected:
            anchor=next(p for p in ps if p['paragraph_index']==selected[0]['evidence_paragraph_index'])
            assert hashlib.sha256(anchor['text'].encode()).hexdigest()==selected[0]['evidence_paragraph_sha256']
        decorate=lambda p:dict(p,sha256=hashlib.sha256(p['text'].encode()).hexdigest())
        related=[p for p in ps if p not in found and (any(abs(p['paragraph_index']-q['paragraph_index'])<=1 for q in found)
            or re.search(r'code availability|data availability',p['section'],re.I))]
        links=sorted({e.attrib[k] for e in ET.fromstring(raw).iter() for k in e.attrib
            if k.endswith('href') and re.search(r'github|gitlab|codeocean',e.attrib[k],re.I)})
        records.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],
            selected=selected[0] if selected else None,matches=list(map(decorate,found)),
            neighboring_or_availability_context_not_automatically_attributed=list(map(decorate,related)),author_code_links=links))
    result=dict(schema_version=1,status='audited-all250-primary-XMLs-for-HTSeq',papers_scanned=250,
        source_papers=len(records),catalogued_papers=sum(bool(r['selected']) for r in records),
        named_paragraphs=sum(len(r['matches']) for r in records),records=records,census=census,
        implementation_credit=False,whole_paper_reproduced=False)
    (out/'PRIMARY-USES.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({k:result[k] for k in ['status','papers_scanned','source_papers','catalogued_papers','named_paragraphs']}))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary source identity checks')
    main()
