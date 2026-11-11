# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain all named StringTie paragraphs, not just the selected extraction anchor."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    papers = json.loads((data / 'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    pattern = re.compile(r'(?<![A-Za-z0-9_])StringTie(?:2)?(?![A-Za-z0-9_])', re.I)
    records, census = [], []
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        ps = paragraphs(ET.fromstring(raw))
        found = [p for p in ps if pattern.search(p['match_text'])]
        selected = [t for t in paper['tools'] if t['tool_id'] == 'stringtie']
        assert bool(found) == bool(selected)
        census.append(dict(paper_id=paper['paper_id'], matching_paragraphs=len(found), catalogued=bool(selected), xml_sha256=paper['xml_sha256']))
        if not found:
            continue
        assert len(selected) == 1
        anchor = next(p for p in found if p['paragraph_index'] == selected[0]['evidence_paragraph_index'])
        assert hashlib.sha256(anchor['text'].encode()).hexdigest() == selected[0]['evidence_paragraph_sha256']
        records.append(dict(paper_id=paper['paper_id'], doi=paper['doi'], pmcid=paper['pmcid'], xml_sha256=paper['xml_sha256'],
                            catalogued=selected, matches=[dict(p, sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in found],
                            neighboring_context=[p for p in ps if p not in found and any(abs(p['paragraph_index'] - q['paragraph_index']) == 1 for q in found)]))
    assert {r['paper_id'] for r in records} == {'P101', 'P102', 'P109', 'P110', 'P171', 'P183', 'P223', 'P248'}
    result = dict(status='audited-all250-primary-XMLs-for-named-StringTie', papers_scanned=250, census=census, records=records,
                  implementation_credit=False, whole_paper_reproduced=False)
    (out / 'PRIMARY-USES.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(papers_scanned=250, source_papers=len(records), matching_paragraphs=sum(len(r['matches']) for r in records))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete cohort/source identity checks')
    main()
