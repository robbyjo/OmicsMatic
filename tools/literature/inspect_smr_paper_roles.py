# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect all named SMR/HEIDI source uses and their surrounding primary paragraphs; catalog unchanged."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());records=[]
    for paper in papers:
        uses=[t for t in paper['tools'] if t['tool_id'] in ['smr','heidi']]
        if not uses:continue
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];tree=ET.fromstring(raw);assert next(e.text for e in tree.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(tree);indices={p['paragraph_index'] for p in ps if re.search(r'(?<!\w)(SMR|HEIDI)(?!\w)',p['match_text'],re.I)};near={i for n in indices for i in [n-1,n,n+1]};selected=[p for p in ps if p['paragraph_index'] in near];records.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],observations=uses,paragraphs=selected));(out/(paper['pmcid']+'.xml.gz')).write_bytes(gzip.compress(raw,mtime=0))
    assert len(records)==7 and sum(len(r['observations']) for r in records)==12;save(out/'sources.json',records);save(out/'COMPLETED.json',dict(status='inspected-seven-SMR-and-five-HEIDI-source-observations-not-reviewed',papers=7,observations=12,sources=pin(out/'sources.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},catalog_changed=False,implementation_credit=False,whole_goal_complete=False));print('Inspected7 papers/12 named observations; no catalog or implementation credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/role checks')
    main()
