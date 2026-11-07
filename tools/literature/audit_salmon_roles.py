# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit Salmon mentions across the unchanged 250-paper primary-source cohort."""
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


def main():
    if not __debug__: raise RuntimeError('Never disable source hash checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/salmon-runtime').resolve()) and not out.exists()
    out.mkdir(parents=True)
    papers = json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    assert len(papers) == 250
    records = []; scanned = []; other_mentions = []
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        ps = paragraphs(ET.fromstring(raw))
        matches = [dict(p, sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in ps if re.search(r'\bSalmon\b', p['match_text'], re.I)]
        scanned.append(dict(paper_id=paper['paper_id'], xml_sha256=paper['xml_sha256'], matching_paragraphs=len(matches)))
        current = [t for t in paper['tools'] if t['tool_id'] == 'salmon']
        if not matches:
            assert not current
            continue
        assert len(current) <= 1
        if not current:
            other_mentions.append(dict(paper_id=paper['paper_id'], doi=paper['doi'], pmcid=paper['pmcid'], matches=matches))
            continue
        primary = next(p for p in ps if p['paragraph_index'] == current[0]['evidence_paragraph_index'])
        assert hashlib.sha256(primary['text'].encode()).hexdigest() == current[0]['evidence_paragraph_sha256']
        record = {k:paper[k] for k in ['paper_id', 'title', 'doi', 'pmcid', 'xml_sha256']}
        record.update(primary_paragraph=primary['paragraph_index'], matches=matches, current_use=current[0])
        records.append(record)
        print(json.dumps(dict(paper_id=paper['paper_id'], doi=paper['doi'], paragraphs=[p['paragraph_index'] for p in matches])))
    assert len(records) == 8
    result = dict(status='audited-eight-Salmon-primary-source-roles-curation-open', source_papers_scanned=250,
        source_roles=8, papers=records, source_census=scanned, additional_uncatalogued_mentions=other_mentions,
        catalog_changed=False, scientific_runtime_validated=False)
    (out / 'source-audit.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8', newline='\n')


if __name__ == '__main__': main()
