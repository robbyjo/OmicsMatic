#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the frozen 250-source cytometry census; no catalog status promotion."""
import argparse
import gzip
import hashlib
import json
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', required=True, type=Path)
    p.add_argument('--xml-cache', required=True, type=Path); args = p.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    catalog = ROOT/'docs/literature/omics-2023-2026'
    papers = json.loads((catalog/'papers.json').read_bytes()); verify_fixed_cohort(ROOT, catalog, papers)
    primary, matches = [], []
    for paper in papers:
        raw = gzip.decompress((args.xml_cache/(paper['pmcid']+'.xml.gz')).read_bytes())
        if hashlib.sha256(raw).hexdigest() != paper['xml_sha256']:
            raise ValueError('Frozen primary XML differs: '+paper['paper_id'])
        primary.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], xml_sha256=paper['xml_sha256']))
        for paragraph in paragraphs(ET.fromstring(raw)):
            text = unicodedata.normalize('NFKC', paragraph['text']).replace('\u2010', '-').replace('\u2011', '-').replace('\u2013', '-')
            names = [name for name, pattern in [('FlowJo', r'\bflow[\s-]*jo\b'), ('FACSDiva', r'\bfacs[\s-]*diva\b')] if re.search(pattern, text, re.I)]
            if names:
                matches.append(dict(paper_id=paper['paper_id'], doi=paper['doi'], pmcid=paper['pmcid'], xml_sha256=paper['xml_sha256'],
                                    paragraph_index=paragraph['paragraph_index'], paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(), families=names))
    counts = {name: len({r['paper_id'] for r in matches if name in r['families']}) for name in ['FlowJo', 'FACSDiva']}
    if counts != {'FlowJo': 27, 'FACSDiva': 17} or len(matches) != 48 or len({r['paper_id'] for r in matches}) != 33:
        raise ValueError('Cytometry source census changed; inspect sources before changing expected scope')
    report = dict(status='PASS250_XML_HASHES_AND_CYTOMETRY_NAME_CENSUS', primary_sources=primary, paragraphs=matches,
                  counts=counts, unique_papers=33, matching_paragraphs=48, new_catalog_integrations=0,
                  scope='Name census and source pointers only. Acquisition/sorting, analysis models, gating, versions and plugins require separate role review. This is not 44 independent workflows or a claim of implemented vendor software.')
    (args.out/'SOURCE_CENSUS.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf8', newline='\n')
    print('PASS 250 original XMLs; FlowJo27/FACSDiva17; 33 unique papers; 48 paragraphs; no catalog promotion')


if __name__ == '__main__':
    main()
