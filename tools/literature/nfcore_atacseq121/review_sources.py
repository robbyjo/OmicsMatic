#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify all frozen paper XMLs and preserve the additional ATAC roles without catalog credit."""
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


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    catalog = ROOT/'docs/literature/omics-2023-2026'
    paths = [catalog/'papers.json', ROOT/'tools/literature/reviewed-uses.json']
    guards = {str(path): pin(path) for path in paths}
    papers = json.loads(paths[0].read_bytes())
    verify_fixed_cohort(ROOT, catalog, papers)
    original_cache = ROOT/'build/literature-review/xml'
    # The active worktree deliberately reuses the original read-only cache.
    if not original_cache.is_dir():
        original_cache = Path('D:/projects/JLinAlg/build/literature-review/xml')
    census, primary = [], []
    for paper in papers:
        raw = gzip.decompress((original_cache/(paper['pmcid']+'.xml.gz')).read_bytes())
        if hashlib.sha256(raw).hexdigest() != paper['xml_sha256']:
            raise ValueError('Frozen original primary XML differs: '+paper['paper_id'])
        primary.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], xml_sha256=paper['xml_sha256']))
        for paragraph in paragraphs(ET.fromstring(raw)):
            match_text = unicodedata.normalize('NFKC', paragraph['match_text']).replace('\u2010', '-').replace('\u2011', '-').replace('\u2013', '-')
            if re.search(r'\bnf[\s-]*core\b|nf-co\.re|github\.com/nf-core/', match_text, re.I):
                census.append(dict(paper_id=paper['paper_id'], doi=paper['doi'], pmcid=paper['pmcid'], xml_sha256=paper['xml_sha256'],
                    paragraph_index=paragraph['paragraph_index'], paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(), section=paragraph['section']))
    expected = ['P002', 'P027', 'P032', 'P050', 'P169', 'P228']
    if sorted({row['paper_id'] for row in census}) != expected or len(census) != 11:
        raise ValueError('Frozen nf-core source census differs; inspect primary source before changing expected scope')
    roles = [dict(paper_id='P169', paragraph_index=32, pipeline='atacseq', reported_pipeline_version='1.2.1', reported_nextflow_version=None,
                  use='Processed ATAC-seq data for the reported colorectal cell-line panel with nf-core/atacseq1.2.1 default parameters.',
                  boundary='The paper does not identify the Nextflow edition, exact source commit or image digest. The yeast upstream fixture does not reproduce the human experiment.'),
             dict(paper_id='P050', paragraph_index=47, pipeline='atacseq', reported_pipeline_version=None, reported_nextflow_version=None,
                  use='Used the reported nf-core ATAQ-SEQ/ATAC workflow for accessibility processing, followed by broad peaks, HOMER annotation, featureCounts and DESeq2.',
                  boundary='Pipeline edition is unreported. Do not assign 1.2.1 or any engine from publication date or nearby tool mentions; the parasite reference, replicate design and subsequent coverage plots remain explicit.')]
    for role in roles:
        selected = [row for row in census if row['paper_id'] == role['paper_id'] and row['paragraph_index'] == role['paragraph_index']]
        if len(selected) != 1:
            raise ValueError('Expected ATAC primary paragraph is missing')
        role.update(selected[0], catalog_credit=False, review_state='ADDITIONAL_SOURCE_ROLE_NOT_APPLIED_TO_CATALOG')
    if any(pin(Path(name)) != value for name, value in guards.items()):
        raise ValueError('Frozen catalog/reviews changed during source census')
    report = dict(status='PASS250_XML_HASHES_AND_SIX_PAPER_ELEVEN_PARAGRAPH_NFCORE_CENSUS', primary_sources=primary,
                  nfcore_paragraphs=census, additional_atac_roles=roles, catalog_unchanged=guards, new_catalog_integrations=0,
                  scope='Source-role audit only; adapter/runtime/scientific completion remain separate.')
    (out/'SOURCE_REVIEW.json').write_text(json.dumps(report, indent=2, ensure_ascii=False)+'\n', encoding='utf-8', newline='\n')
    print('PASS 250 frozen XMLs; 6 nf-core papers; 11 matching paragraphs; 2 additional ATAC roles; zero catalog credit')


if __name__ == '__main__':
    main()
