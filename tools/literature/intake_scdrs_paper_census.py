# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every fixed-cohort primary XML for scDRS mentions, including uncataloged uses."""
import argparse
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from collect_omics_review import paragraphs, tool_matches


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--include-plural', action='store_true', help='Apply the source-reviewed scDRSs alias without editing the frozen base registry')
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    papers = json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    registry = json.loads((ROOT / 'tools/literature/tool-registry.json').read_bytes())
    entry = next(t for t in registry if t['id'] == 'scdrs')
    if a.include_plural:
        from scdrs_registry import entry as expanded_entry
        entry = expanded_entry(entry)
    assert len(papers) == 250
    census = []
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        source = paragraphs(ET.fromstring(raw))
        raw_mentions = [p for p in source if re.search('scdrs', p['text'], re.I)]
        matched = tool_matches(source, [entry])
        current = next((t for t in paper['tools'] if t['tool_id'] == 'scdrs'), None)
        if raw_mentions or matched or current:
            row = dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
                xml_sha256=paper['xml_sha256'], cataloged=current is not None, matched_by_registry=bool(matched),
                current_observation=current, extracted=matched, private_primary_paragraphs=raw_mentions)
            census.append(row)
    save(out / 'private-primary-census.json', census)
    public = [{k: row[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256', 'cataloged', 'matched_by_registry']} for row in census]
    save(out / 'COMPLETED.json', dict(status='PASS_ALL_250_SCDRS_PRIMARY_MENTION_CENSUS_ONLY',
        papers_audited=250, mention_papers=public, current_observations=sum(r['cataloged'] for r in census),
        matched_papers=sum(r['matched_by_registry'] for r in census),
        newly_detected=[r['paper_id'] for r in census if r['matched_by_registry'] and not r['cataloged']],
        plural_extension_used=a.include_plural, effective_pattern=entry['pattern'],
        private_intake=pin(out / 'private-primary-census.json'),
        catalog_modified=False, article_prose_private_only=True, new_catalog_credit=False))
    print(json.dumps(public))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable full-cohort primary-source census')
    main()
