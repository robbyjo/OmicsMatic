# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Scan all 250 hash-verified primary XML texts for previously unregistered fitdistrplus roles."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/fitdistrplus-runtime') and not out.exists()
    out.mkdir(parents=True)
    source = Path(__file__).resolve()
    source_pin = pin(source)
    papers_path = ROOT / 'docs/literature/omics-2023-2026/papers.json'
    papers = json.loads(papers_path.read_bytes())
    assert len(papers) == 250 and len({p['pmcid'] for p in papers}) == 250
    pattern = re.compile(r'(?<![A-Za-z0-9_])fitdistrplus(?![A-Za-z_])', re.I)
    hits, private, sources = [], [], []
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        digest = hashlib.sha256(raw).hexdigest()
        assert digest == paper['xml_sha256']
        root = ET.fromstring(raw)
        doi = next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type') == 'doi')
        assert doi.lower() == paper['doi'].lower()
        sources.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=doi, xml_sha256=digest))
        for paragraph in paragraphs(root):
            # Raw XML text can append citation digits directly to software names.
            # Check the citation-stripped match text as well as the literal text.
            match = pattern.search(paragraph['match_text']) or pattern.search(paragraph['text'])
            if not match:
                assert 'fitdistrplus' not in paragraph['text'].lower(), 'Unclassified spelling needs review'
                continue
            hit = dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=doi,
                paragraph_index=paragraph['paragraph_index'], section=paragraph['section'],
                paragraph_sha256=hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest())
            hits.append(hit)
            private.append(dict(**hit, text=paragraph['text'], match_text=paragraph['match_text']))
    assert hits and ('P043', 51) in {(h['paper_id'], h['paragraph_index']) for h in hits}
    assert pin(source) == source_pin
    save(out / 'MATCHES.private.json', private)
    save(out / 'COMPLETED.json', dict(status='passed-250-primary-XML-fitdistrplus-named-role-scan',
        source_pins={source.relative_to(ROOT).as_posix(): source_pin,
                     'tools/literature/collect_omics_review.py': pin(ROOT / 'tools/literature/collect_omics_review.py')},
        papers_pin=pin(papers_path), source_records=sources, matches=hits,
        named_papers=len({h['paper_id'] for h in hits}), semantic_review_complete=False,
        catalog_registration_pending=True, implementation_credit=False, whole_goal_complete=False))
    print(json.dumps(dict(verified_sources=len(sources), matches=hits, catalog_registration_pending=True)))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source checks')
    main()
