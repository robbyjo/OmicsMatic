# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit retained primary DESeq2 paragraphs; keep all long text in the private build cache."""
import argparse
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/deseq2-runtime') and not out.exists();out.mkdir()
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers)
    registry=json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes());tool=next(t for t in registry if t['id']=='deseq2');pattern=re.compile(tool['pattern'],re.I)
    census=[];records=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw))
        named=[dict(p,sha256=hashlib.sha256(p['text'].encode('utf-8')).hexdigest()) for p in ps if pattern.search(p.get('match_text',p['text']))]
        observed=[t for t in paper['tools'] if t['tool_id']=='deseq2'];census.append(dict(paper_id=paper['paper_id'],named_body_paragraphs=len(named),catalogued=len(observed),xml_sha256=paper['xml_sha256']))
        if observed or named:
            assert len(observed)<=1;anchor=observed[0] if observed else None
            if anchor:
                source=ps[anchor['evidence_paragraph_index']-1]
                assert hashlib.sha256(source['text'].encode('utf-8')).hexdigest()==anchor['evidence_paragraph_sha256']
            related_indices={p['paragraph_index'] for p in ps if re.search(r'rlog|varianceStabilizingTransformation|lfcShrink|estimateSizeFactors|collapseReplicates|(?<!\w)vst(?!\w)',p.get('match_text',p['text']),re.I)}
            related_indices.update(i for n in named for i in [n['paragraph_index']-1,n['paragraph_index']+1])
            neighbors=[dict(p,sha256=hashlib.sha256(p['text'].encode('utf-8')).hexdigest()) for p in ps if p['paragraph_index'] in related_indices and all(p['paragraph_index']!=n['paragraph_index'] for n in named)]
            records.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],title=paper['title'],xml_sha256=paper['xml_sha256'],anchor=anchor,named=named,neighbors=neighbors))
    report=dict(status='private-main-text-DESeq2-primary-role-audit-not-full-supplement-review',papers_scanned=len(papers),catalogued_papers=sum(r['catalogued'] for r in census),mentioned_papers=sum(r['named_body_paragraphs']>0 for r in census),records=records,census=census)
    (out/'PRIVATE-PRIMARY-USES.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ['status','papers_scanned','catalogued_papers','mentioned_papers']}))


if __name__=='__main__':main()
