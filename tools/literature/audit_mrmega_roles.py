# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all 250 primary articles and draft three MRMEGA roles without catalog credit."""
import argparse
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from setup_deseq2_reference import ROOT, pin, save
from collect_omics_review import paragraphs, validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from sniffles_catalog import load_catalog_registry

ROLES = {
    'P072': dict(version='0.2', support=[19, 22],
        summary='Used MR-MEGA0.2 with three ancestry axes for multi-ancestry Parkinson disease meta-regression across seven cohorts, distinguished ancestry-related from residual heterogeneity, and used reported per-variant lnBF values for downstream locus fine-mapping.',
        note='The study retained variants present in at least three of seven cohorts and explicitly applied no genomic control before meta-analysis. CrossMap hg19 harmonization, imputation/MAF/autosomal filters and the separate PLINK random-effects analysis remain distinct. Fine-mapping normalizes Bayes factors within loci, uses the reported significance/credible-set conditions and excludes MHC/MAPT regions. Complete commands, cohort effect/CI encoding and the exact reference binary are not frozen; the regression kernel alone does not reconstruct allele harmonization, ancestry-axis inference or credible-set construction.'),
    'P105': dict(version='0.2',
        summary='Used MR-MEGA0.2 for cross-ancestry meta-regression with four multidimensional-scaling ancestry axes, then recalculated P values from chi-square statistics because the original probability calculation did not support very small P values.',
        note='The named four-axis MR-MEGA analysis is separate from the preceding METAL fixed-effect meta-analyses. Do not infer two aggregate input cohorts from the EAS/EUR population labels. Exact MR-MEGA cohort inputs, GC flags, source binary and recalculation code are unstated. Preserve original chi-square/degree fields and original probability diagnostics separately from any explicitly labeled stable recalculation; no complete paper or file workflow is established by a prepared-cohort regression kernel.'),
    'P162': dict(version=None,
        summary='Used MR-MEGA to examine index-variant effect heterogeneity attributable to ancestry rather than residual effects; three index variants showed significant ancestry-related heterogeneity.',
        note='The source does not specify the MR-MEGA edition, number of ancestry axes, complete input/test family, command or genomic-control settings. The three significant variants are reported findings, not evidence that only those variants were tested. The adjacent SuSiEx credible-set analysis is separate and must not be attributed to MR-MEGA. A supplied-coordinate native regression does not infer the paper ancestry coordinates or reproduce its variant population.'),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    out = parser.parse_args().out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/mrmega-runtime')
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    paths = [data / 'papers.json', data / 'tool-inventory.json', data / 'summary.json',
             ROOT / 'tools/literature/reviewed-uses.json']
    before = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    papers = json.loads((data / 'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    registry = {r['id']: r for r in load_catalog_registry(ROOT)}
    reviews = json.loads(paths[-1].read_bytes())['reviews']
    assert len(reviews) == 1009
    census, records, drafts = [], [], []
    observations = 0
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        xml = ET.fromstring(raw)
        doi = next(q.text for q in xml.findall('front/article-meta/article-id') if q.get('pub-id-type') == 'doi')
        assert doi.lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        by = {p['paragraph_index']: p for p in ps}
        cache = json.loads((ROOT / 'build/literature-review/extracted' / (paper['pmcid'] + '.json')).read_bytes())
        cached = {t['tool_id']: t for t in cache['tools']}
        for tool in paper['tools']:
            anchor = by[tool['evidence_paragraph_index']]
            assert anchor['text'] == cached[tool['tool_id']]['evidence_text']
            assert hashlib.sha256(anchor['text'].encode()).hexdigest() == tool['evidence_paragraph_sha256']
            observations += 1
        matches = [p for p in ps if re.search(registry['mrmega']['pattern'], p['match_text'], re.I)]
        current = [t for t in paper['tools'] if t['tool_id'] == 'mrmega']
        assert len(current) <= 1
        census.append(dict(paper_id=paper['paper_id'], matching_paragraphs=len(matches), catalogued=bool(current)))
        if not current:
            assert not matches
            continue
        tool = current[0]
        role = ROLES[paper['paper_id']]
        if role['version'] is not None: assert role['version'] in ' '.join(p['text'] for p in matches)
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='mrmega',
                      evidence_paragraph_sha256=tool['evidence_paragraph_sha256'],
                      use_summary=role['summary'], implementation_scope_note=role['note'],
                      version_reported_near_name=role['version'])
        support = []
        for index in role.get('support', []):
            paragraph = by[index]
            support.append(dict(paragraph_index=index, section=paragraph['section'],
                                paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
        if support:
            review['supporting_evidence'] = support
        validate_supporting_evidence(ps, [review], registry)
        prior = [r for r in reviews if r['pmcid'] == paper['pmcid'] and r['tool_id'] == 'mrmega']
        assert not prior
        drafts.append(dict(paper_id=paper['paper_id'], review=review))
        def pointer(paragraph):
            return dict(paragraph_index=paragraph['paragraph_index'], section=paragraph['section'],
                        paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest())
        permissions=xml.find('front/article-meta/permissions')
        license_links=sorted({value for node in permissions.iter() for value in node.attrib.values()
                              if value.startswith('https://creativecommons.org/licenses/')}) if permissions is not None else []
        records.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
                            xml_sha256=paper['xml_sha256'], anchor=tool['evidence_paragraph_index'],
                            article_url='https://doi.org/'+paper['doi'], article_license_links=license_links,
                            matching_paragraphs=[pointer(p) for p in matches],
                            supporting_paragraphs=[pointer(by[i]) for i in role.get('support', [])], review=review,
                            original_paragraph_text_redistributed=False))
    assert len(papers) == 250 and observations == 2465
    assert {r['paper_id'] for r in records} == set(ROLES) and len(drafts) == 3
    assert all(pin(ROOT / p) == value for p, value in before.items())
    save(out / 'role-drafts.json', dict(status='three-primary-bound-MRMEGA-role-drafts-only', reviews=drafts))
    save(out / 'source-audit.json', dict(status='read-only-three-MRMEGA-primary-role-drafts',
                                       papers=records, source_census=census, catalog_changed=False,
                                       implementation_credit=False))
    save(out / 'COMPLETED.json', dict(status='passed250-primary-XML-DOI-and2465-anchors-with-three-MRMEGA-drafts',
        source_papers=250, observations=2465, roles=3, prior_roles=0, new_role_drafts=3,
        supporting_pointers=sum(len(d['review'].get('supporting_evidence', [])) for d in drafts),
        catalog_before=before, role_drafts=pin(out / 'role-drafts.json'), source_audit=pin(out / 'source-audit.json'),
        catalog_changed=False, implementation_credit=False, whole_goal_complete=False,
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(),
                     ROOT / 'tools/literature/collect_omics_review.py']}))
    print('Audited 250 primary XML/DOIs and 2465 anchors; drafted three MRMEGA roles. Catalog unchanged.')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary-source and unchanged-catalog checks')
    main()
