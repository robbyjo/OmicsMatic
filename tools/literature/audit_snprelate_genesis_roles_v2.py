# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Full250 source-name census using citation-normalized match_text; raw primary paragraph hashes stay unchanged."""
import argparse
import collections
import gzip
import hashlib
import json
import re
import traceback
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_GENESIS_CITATION_NORMALIZED_INTAKE',catalog_applied=False,new_catalog_credit=False,whole_goal_complete=False)
    try:
        data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers)
        assert len(papers)==250
        patterns={'snprelate':re.compile(r'(?<![A-Za-z0-9_])SNPRelate(?![A-Za-z0-9_])',re.I),
            'genesis':re.compile(r'(?<![A-Za-z0-9_])GENESIS(?![A-Za-z0-9_])',re.I),
            'snpgds_function':re.compile(r'(?<![A-Za-z0-9_])snpgds[A-Za-z0-9_]+',re.I)}
        records=[];source_pins={}
        for paper in papers:
            path=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes())
            assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];source_pins[paper['pmcid']]=paper['xml_sha256']
            for paragraph in paragraphs(ET.fromstring(raw)):
                searchable=paragraph.get('match_text',paragraph['text'])
                for tool,pattern in patterns.items():
                    matches=sorted({m.group() for m in pattern.finditer(searchable)})
                    if matches:
                        records.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],tool=tool,
                            paragraph_index=paragraph['paragraph_index'],section=paragraph.get('section',''),
                            paragraph_sha256=hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest(),matches=matches,
                            found_in_raw_text=bool(pattern.search(paragraph['text'])),text=paragraph['text']))
        save(out/'all-mentions.private.json',records);public=[{k:v for k,v in r.items() if k!='text'} for r in records]
        save(out/'source-pointers.json',dict(status='citation-normalized-unreviewed-primary-pointers',pointers=public,article_prose_redistributed=False))
        by_tool={tool:sorted({r['paper_id'] for r in public if r['tool']==tool}) for tool in patterns}
        state.update(status='PASS250_CITATION_NORMALIZED_SNPRELATE_GENESIS_MENTION_CENSUS_ONLY',papers=250,papers_by_tool=by_tool,
            mention_paragraphs_by_tool=dict(collections.Counter(r['tool'] for r in records)),source_pins=source_pins,producer=pin(Path(__file__)),
            citation_adjacent_matches_previously_missed=[{k:v for k,v in r.items() if k!='text'} for r in records if not r['found_in_raw_text']],
            prior_raw_text_intake_superseded_for_completeness=True,
            scope='Mention census only. The earlier raw-text name audit missed citation-adjacent names and is superseded for source completeness, not for arithmetic evidence. Review actual software roles and generic-word exclusions before catalog application.')
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],json.dumps(state.get('papers_by_tool',{})),state.get('error','')[:1800],flush=True)
    return 0 if state['status'].startswith('PASS250_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete250 primary-source and raw-hash checks')
    raise SystemExit(main())
