# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original ChIPseeker paper contexts without changing reviews, papers or implementation counts."""
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
from collect_omics_review import paragraphs,words
from setup_deseq2_reference import pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    data=ROOT/'docs/literature/omics-2023-2026';bound=[data/'papers.json',data/'tool-inventory.json',data/'summary.json',ROOT/'tools/literature/reviewed-uses.json']
    before={p.relative_to(ROOT).as_posix():pin(p) for p in bound};papers=json.loads((data/'papers.json').read_bytes());selected=[]
    for paper in papers:
        uses=[t for t in paper['tools'] if t['tool_id']=='chipseeker']
        if not uses:continue
        assert len(uses)==1;use=uses[0]
        source=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(source.read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        xml=ET.fromstring(raw);ps=paragraphs(xml);hits=[p for p in ps if re.search(r'ChIPseeker',p['match_text'],re.I)];indices={p['paragraph_index'] for p in hits}
        anchor=next(p for p in ps if p['paragraph_index']==use['evidence_paragraph_index']);assert hashlib.sha256(anchor['text'].encode('utf8')).hexdigest()==use['evidence_paragraph_sha256']
        record={k:paper[k] for k in ['paper_id','title','doi','pmcid','xml_sha256']};record.update(current_use=use,primary=anchor,matches=hits,
            context=[p for p in ps if any(abs(p['paragraph_index']-i)<=2 for i in indices)],
            source_references=[dict(id=r.get('id'),text=words(r)) for r in xml.findall('.//ref') if re.search(r'ChIPseeker|peak annotation',words(r),re.I)])
        save(out/(paper['paper_id']+'.json'),record);selected.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],anchor_paragraph=anchor['paragraph_index'],matching_paragraphs=len(hits)))
    assert {p['paper_id'] for p in selected}=={'P020','P043','P044','P124','P133','P139','P195'}
    assert all(pin(ROOT/name)==wanted for name,wanted in before.items())
    save(out/'COMPLETED.json',dict(status='captured-seven-primary-ChIPseeker-paper-contexts-not-curated-or-promoted',papers=selected,catalog_and_reviews_unchanged=before,script=pin(Path(__file__)),implementation_credit=False))
    print('Captured seven original ChIPseeker paper contexts; catalog and reviews unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original paper/catalog checks')
    main()
