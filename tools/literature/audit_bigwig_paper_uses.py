# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain every named bedGraphToBigWig paragraph and reference boundary in the fixed 250-paper cohort."""
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
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/bigwig-native').resolve()) and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());records=[];census=[];match=re.compile(r'(?<![A-Za-z0-9_])bedGraphToBigWig(?![A-Za-z0-9_])',re.I)
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));found=[p for p in ps if match.search(p['match_text'])];selected=[t for t in paper['tools'] if t['tool_id']=='ucsc-bedgraphtobigwig'];census.append(dict(paper_id=paper['paper_id'],matching_paragraphs=len(found),catalogued=bool(selected),xml_sha256=paper['xml_sha256']))
        if not found:assert not selected;continue
        neighbors=[p for p in ps if p not in found and any(abs(p['paragraph_index']-q['paragraph_index'])==1 for q in found)];records.append(dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],catalogued=selected,matches=[dict(p,sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in found],neighboring_context=neighbors))
    assert len(papers)==250 and {r['paper_id'] for r in records}=={'P013','P050','P110','P138','P163'};(out/'PRIMARY-USES.json').write_text(json.dumps(dict(status='audited-all250-primary-XMLs-for-named-bedGraphToBigWig',papers_scanned=250,census=census,records=records,implementation_credit=False),indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(papers_scanned=250,source_roles=len(records),matching_paragraphs=sum(len(r['matches']) for r in records))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete cohort/source identity checks')
    main()
