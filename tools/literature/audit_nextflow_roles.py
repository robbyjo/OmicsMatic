# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect complete cached primary Nextflow paragraphs without changing the catalog."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs


def main():
    if hasattr(sys.stdout,'reconfigure'):sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    assert not args.out.exists();args.out.mkdir(parents=True)
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))
    uses=[p for p in papers if any(t['tool_id']=='nextflow' for t in p['tools'])];assert len(papers)==250 and len(uses)==10
    records=[]
    for paper in uses:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        ps=paragraphs(ET.fromstring(raw));use=next(t for t in paper['tools'] if t['tool_id']=='nextflow')
        primary=next(p for p in ps if p['paragraph_index']==use['evidence_paragraph_index'])
        assert hashlib.sha256(primary['text'].encode()).hexdigest()==use['evidence_paragraph_sha256']
        matches=[dict(p,sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in ps if re.search(r'\bnextflow\b',p['match_text'],re.I)]
        assert matches and primary['paragraph_index'] in {p['paragraph_index'] for p in matches}
        record={k:paper[k] for k in ['paper_id','title','doi','pmcid','xml_sha256']};record.update(primary_paragraph=primary['paragraph_index'],matches=matches,current_use=use);records.append(record)
        print(paper['paper_id']+' '+paper['title'])
        for p in matches:print(str(p['paragraph_index'])+' '+p['section']+'\n'+p['text'])
    result=dict(status='inspected-ten-primary-source-Nextflow-roles-not-yet-curated',source_roles=10,papers=records,scientific_runtime_validated=False)
    (args.out/'source-audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')


if __name__=='__main__':main()
