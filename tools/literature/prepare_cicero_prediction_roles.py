# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind prediction-reuse and newly discovered normal-background roles to exact primary paragraphs."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir()
    papers = json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    expected = {('P153', 54): '7127ad8496e4253a67c941a3959d37f382490c354b00b13ed0ba61e663d1cf88',
                ('P153', 11): '982b9594921e574708548f68ef487768de203287109f977eca113bae66c2e28f',
                ('P043', 51): '41db6565b9c28d56ef328ce411a63865d3f6a88dfa070f1c94457546ac40598e'}
    records = []
    for (paper_id, index), digest in expected.items():
        paper = next(p for p in papers if p['paper_id'] == paper_id)
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        root = ET.fromstring(raw)
        assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type') == 'doi').lower() == paper['doi'].lower()
        paragraph = next(p for p in paragraphs(root) if p['paragraph_index'] == index)
        assert hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest() == digest
        records.append(dict(paper_id=paper_id, pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'],
            paragraph_index=index, section=paragraph['section'], paragraph_sha256=digest))
    save(out / 'COMPLETED.json', dict(status='passed-exact-primary-Cicero-prediction-reuse-and-fitdistrplus-discovery-pointers',
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), ROOT / 'tools/literature/collect_omics_review.py']},
        primary_pointers=records, Cicero_P153_role='Subset supplied predictions by peak accessibility; no detected-gene filter or new fitting run established.',
        unresolved_Cicero_settings=['prediction release', 'coordinate convention', 'overlap threshold', 'exact endpoint filtering'],
        fitdistrplus_P043_role='Normal fit to shuffled coaccessibility scores before stated BH/FDR procedure.',
        unresolved_fitdistrplus_settings=['function', 'estimation method', 'package version', 'shuffle implementation', 'filtering-direction interpretation'],
        fitdistrplus_catalog_registration_pending=True, paper_wide_semantic_completeness_open=True,
        implementation_credit=False, whole_paper_reproduced=False, whole_goal_complete=False))
    print('Bound three exact primary paragraphs; unknown settings remain explicit')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary-source checks')
    main()
