# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all250 primary XMLs,2476 observations,1049 reviews and the scDRS/MTAG/GENESIS/SNPRelate census."""
import argparse
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from collect_omics_review import paragraphs, validate_supporting_evidence, tool_matches
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from scdrs_catalog import verify_review_extension, load_catalog_registry
from curate_mtag_uses import primary_equation
from curate_scdrs_uses import PAPERS, VERSIONS, ANCHORS


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    papers = json.loads((data / 'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    reviews = json.loads((ROOT / 'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    registry = {r['id']: r for r in load_catalog_registry(ROOT)}
    assert (len(papers), len(reviews), len(registry)) == (250, 1049, 440)
    sources, observations, census = {}, 0, []
    package_ids = ['snprelate', 'genesis', 'mtag', 'scdrs']
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        xml = ET.fromstring(raw)
        assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type') == 'doi').lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        sources[paper['pmcid']] = ps
        by = {p['paragraph_index']: p for p in ps}
        cached = {r['tool_id']: r for r in json.loads((ROOT / 'build/literature-review/extracted' / (paper['pmcid'] + '.json')).read_bytes())['tools']}
        selected = {r['tool_id']: r for r in paper['tools'] if r['tool_id'] in package_ids}
        extracted = tool_matches(ps, [registry[k] for k in package_ids], {k: r['evidence_paragraph_sha256'] for k, r in selected.items()})
        assert {r['tool_id'] for r in extracted} == set(selected), ('Incomplete source-normalized package census', paper['paper_id'])
        for tool in paper['tools']:
            anchor = by[tool['evidence_paragraph_index']]
            assert hashlib.sha256(anchor['text'].encode()).hexdigest() == tool['evidence_paragraph_sha256']
            if tool['tool_id'] in selected:
                original = next(r for r in extracted if r['tool_id'] == tool['tool_id'])
                assert original['evidence_text'] == anchor['text']
                census.append((paper['paper_id'], tool['tool_id']))
            else: assert anchor['text'] == cached[tool['tool_id']]['evidence_text']
            if not re.search(registry[tool['tool_id']]['pattern'], anchor['match_text'], re.I):
                assert (paper['paper_id'], tool['tool_id'], anchor['paragraph_index']) == ('P005', 'htseq', 54)
                assert htseq_matches([anchor], [registry['htseq']], {'htseq': tool['evidence_paragraph_sha256']})[0]['matched_text'] == 'HT-seq'
            observations += 1
        if paper['paper_id'] in PAPERS:
            tool = selected['scdrs']
            assert tool['evidence_paragraph_index'] == ANCHORS[paper['paper_id']]
            assert tool['version_reported_near_name'] == VERSIONS[paper['paper_id']]
        if paper['paper_id'] == 'P088': assert json.loads((data / 'mtag-P088-equation-source-review.json').read_bytes()) == primary_equation(paper, raw)
    assert observations == 2476 and len(census) == 20
    assert {k: sum(t == k for _, t in census) for k in package_ids} == dict(snprelate=4, genesis=5, mtag=5, scdrs=6)
    verify_review_extension(ROOT, data, reviews)
    by = {p['pmcid']: p for p in papers}
    for review in reviews:
        paper = by[review['pmcid']]
        assert paper['doi'].lower() == review['doi'].lower()
        tool = next(t for t in paper['tools'] if t['tool_id'] == review['tool_id'])
        validate_supporting_evidence(sources[paper['pmcid']], [review], registry)
        for key in ['evidence_paragraph_sha256', 'use_summary', 'implementation_scope_note', 'usage_context', 'version_reported_near_name', 'supporting_evidence']:
            if key in review: assert tool[key] == review[key]
    prior = json.loads(gzip.decompress((data / 'scdrs-prior-papers.json.gz').read_bytes()))
    for a, b in zip(prior, papers, strict=True):
        assert {k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}
        assert [t for t in a['tools'] if t['tool_id'] != 'scdrs'] == [t for t in b['tools'] if t['tool_id'] != 'scdrs']
    old_reviews = json.loads(gzip.decompress((data / 'scdrs-prior-reviews.json.gz').read_bytes()))['reviews']
    assert len(old_reviews) == 1043 and reviews[:1043] == old_reviews
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews) == 375
    save(out / 'COMPLETED.json', dict(status='PASS250_PRIMARY_SOURCES_2476_OBSERVATIONS_1049_REVIEWS_AND_SIX_SCDRS_ROLES', papers=250, observations=2476,
        reviewed_roles=1049, supporting_pointers=375, package_census=census, unchanged_prior_reviews=1043,
        unchanged_non_SCDRS_observations=2470, new_observations=1, newly_detected_paper='P169',
        all_unrelated_source_roles_preserved=True, separate_P088_author_equation_revalidated=True,
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), ROOT / 'tools/literature/scdrs_catalog.py', ROOT / 'tools/literature/scdrs_registry.py',
            ROOT / 'tools/literature/curate_scdrs_uses.py', ROOT / 'tools/literature/collect_omics_review.py', ROOT / 'tools/literature/collect_htseq_omics.py']},
        papers_pin=pin(data / 'papers.json'), reviews_pin=pin(ROOT / 'tools/literature/reviewed-uses.json'), whole_goal_complete=False))
    print('PASS250 primary sources;2476 observations;1049 reviews;375 pointers;six scDRS roles', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable complete primary-source, role, census or preservation checks')
    main()
