# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review six source-bound scDRS uses, including the missed plural in P169; retain every prior review."""
import argparse
import copy
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from collect_omics_review import paragraphs, tool_matches, publication_record, validate_supporting_evidence
from curate_shapeit_uses import inverse, serialize
from catalog_source_integrity import reverse_delta, verify_source
from signac_scan_evidence import byte_pin
from scdrs_registry import entry, BASE_PATTERN, PATTERN

PAPERS = ['P021', 'P024', 'P055', 'P085', 'P123', 'P169']
VERSIONS = {'P021': '1.0.1', 'P024': None, 'P055': '1.0.2', 'P085': '1.0.0', 'P123': '1.0.2', 'P169': '1.0.1'}
ANCHORS = {'P021': 62, 'P024': 61, 'P055': 93, 'P085': 46, 'P123': 48, 'P169': 36}
USES = {
    'P021': 'Used scDRS1.0.1 to compute nucleus-level disease scores in DLPFC lifespan data from MAGMA gene z-score weights and gene-specific technical noise. Requested20 mean and20 variance bins; used200 mean/variance-matched control sets, vs weighting and normalized control-score output.',
    'P024': 'Applied scDRS after MAGMA across neurological and other GWAS traits, using the top1000 gene weights sorted by z-score and reported default scDRS settings. The AD GWAS excluded APOE; MAGMA annotation used35kb upstream/10kb downstream windows with named1000Genomes EUR and NCBI38 files.',
    'P055': 'Used scDRS1.0.2 with reported default settings to investigate GWAS-trait enrichment in snRNA-seq cell types, computing cell-level disease associations against control-gene-score distributions.',
    'P085': 'Used scDRS1.0.0 for cell-level and group-level polygenic enrichment in spatial and single-nucleus datasets, with1000 mean/variance-matched controls and supplied74 human/22 mouse disease gene sets. Analyzed datasets and disease/genotype/age strata separately; subsequently correlated AD scores with module eigengenes using Pearson correlation.',
    'P123': 'Used scDRS1.0.2 on PBMC and MS-lesion datasets with top1000 cross-population MAGMA genes outside chr6:25-34Mb MHC. Computed1000 control scores; included genes-per-cell, age and sex covariates; performed cell-type association tests and BH correction across cell types within each dataset, declaring FDR<0.05.',
    'P169': 'Used scDRS1.0.1, written as scDRSs in the article, to link CRC GWAS risk with Tabula Sapiens v4 and Gut Cell Atlas expression. MAGMA1.10 defined the top1000 putative disease genes; normalized cell scores were tested against pooled control scores across gene sets and cells.'}
COMMON = ('Scoped original scDRS1.0.0/1.0.1/1.0.2 workflows and separate native numerical components are implemented; '
          'read SCDRS_INTEGRATION.md. Tested release tags do not establish an author source commit or reconstruct unreported commands. ')
EXTRA = {
    'P021': 'The primary paragraph explicitly gives1.0.1, correcting the prior unreported version field. Preserve200 controls and the stated flags instead of substituting the CLI default1000. MAGMA weights and expression preparation remain explicit inputs.',
    'P024': 'The version, source commit and complete scDRS command are unreported. Gene ranking, APOE exclusion, genome/LD-reference compatibility and MAGMA windows are upstream preparation; no scDRS version or variant-exclusion policy is inferred.',
    'P055': 'Default settings are reported, but this anchor does not specify every input, normalization, covariate or downstream command. Do not attribute all package features to this use or infer a historical source commit.',
    'P085': 'Keep each dataset and stratum separate; scDRS does not automatically remove batch effects. Supply the selected curated gene-set release. Ordinary module-score Pearson coefficients are a distinct step from the scDRS correlation Monte Carlo output; the existing StatisticalTests.correlation Java API is available for explicitly aligned vectors.',
    'P123': 'Provide explicitly prepared disease genes and covariates; immune-cell removal and MHC exclusion are separate input decisions. Choose one BH family across all tested cell types in each dataset, with strict q<0.05. A successful synthetic control does not reproduce the cohort or biological findings.',
    'P169': 'The methods explicitly report1.0.1, while the plural scDRSs caused the old name matcher to omit this observation. The two atlas inputs are distinct datasets. The exact command, control count, preprocessing and source commit are not stated; do not infer these from the atlas sizes or version label.'}


def pointer(paragraph):
    return dict(paragraph_index=paragraph['paragraph_index'], section=paragraph['section'],
                paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--apply', action='store_true')
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'curator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    data = ROOT / 'docs/literature/omics-2023-2026'
    review_path, paper_path = ROOT / 'tools/literature/reviewed-uses.json', data / 'papers.json'
    before = dict(reviews=review_path.read_bytes(), papers=paper_path.read_bytes())
    original_reviews, original_papers = json.loads(before['reviews']), json.loads(before['papers'])
    assert len(original_reviews['reviews']) == 1043 and len(original_papers) == 250
    assert sum(len(p['tools']) for p in original_papers) == 2475
    assert not any(r['tool_id'] == 'scdrs' for r in original_reviews['reviews'])
    reviews, papers = copy.deepcopy(original_reviews), copy.deepcopy(original_papers)
    registry_path = ROOT / 'tools/literature/tool-registry.json'
    registry = {r['id']: r for r in json.loads(registry_path.read_bytes())}
    before_registry = copy.deepcopy(registry['scdrs'])
    registry['scdrs'] = entry(registry['scdrs'])
    assert before_registry['pattern'] == BASE_PATTERN
    updates, source_rows = [], []
    for paper in papers:
        if paper['paper_id'] not in PAPERS: continue
        name = paper['paper_id']
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        source = paragraphs(ET.fromstring(raw))
        by = {p['paragraph_index']: p for p in source}
        anchor = pointer(by[ANCHORS[name]])
        assert re.search(PATTERN, by[ANCHORS[name]]['match_text'], re.I)
        supports = [pointer(by[5])] if name == 'P169' else []
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='scdrs', evidence_paragraph_sha256=anchor['paragraph_sha256'],
            use_summary=USES[name], implementation_scope_note=COMMON + EXTRA[name],
            version_reported_near_name=VERSIONS[name], supporting_evidence=supports)
        validate_supporting_evidence(source, [review], registry)
        matched = tool_matches(source, [registry['scdrs']], {'scdrs': anchor['paragraph_sha256']})
        assert len(matched) == 1 and matched[0]['evidence_paragraph_index'] == ANCHORS[name]
        tool = publication_record(dict(paper, tools=matched), [review])['tools'][0]
        index = next((i for i, t in enumerate(paper['tools']) if t['tool_id'] == 'scdrs'), None)
        previous = paper['tools'][index] if index is not None else None
        if name == 'P169':
            assert previous is None and not tool_matches(source, [before_registry])
            paper['tools'].append(tool)
        else:
            assert previous is not None
            allowed = {'use_summary', 'implementation_scope_note', 'version_reported_near_name', 'supporting_evidence', 'review_status', 'evidence_selection'}
            assert {k: v for k, v in previous.items() if k not in allowed} == {k: v for k, v in tool.items() if k not in allowed}, name
            paper['tools'][index] = tool
        reviews['reviews'].append(review)
        updates.append(dict(paper_id=name, review=review, before_observation=previous, after_observation=tool))
        source_rows.append(dict(paper_id=name, pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'],
            primary=anchor, supporting=supports, reported_version=VERSIONS[name], new_observation=previous is None))
    assert [u['paper_id'] for u in updates] == PAPERS
    assert reviews['reviews'][:1043] == original_reviews['reviews'] and len(reviews['reviews']) == 1049
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews['reviews']) == 375
    assert sum(len(p['tools']) for p in papers) == 2476
    for old, new in zip(original_papers, papers, strict=True):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [t for t in old['tools'] if t['tool_id'] != 'scdrs'] == [t for t in new['tools'] if t['tool_id'] != 'scdrs']
    after = dict(reviews=serialize(reviews, before['reviews']), papers=serialize(papers, before['papers']))
    for key in before:
        (out / ('before-' + key + '.json.gz')).write_bytes(gzip.compress(before[key], mtime=0))
        (out / ('after-' + key + '.json')).write_bytes(after[key])
    registry_review = dict(status='REVIEWED_SCDRS_PLURAL_NAME_EXTENSION', original_pattern=BASE_PATTERN, pattern=PATTERN,
        original_registry_file=pin(registry_path), extension_helper=pin(ROOT / 'tools/literature/scdrs_registry.py'),
        source=next(r for r in source_rows if r['paper_id'] == 'P169'), original_registry_file_modified=False,
        scientific_source_allowlist_modified=False, article_prose_redistributed=False)
    save(out / 'registry-source-review.json', registry_review)
    save(out / 'applied-reviews.json', dict(status='SIX_SCDRS_PRIMARY_ROLE_REVIEWS', updates=updates))
    report = dict(schema_version=1, status='PASS_SIX_SCDRS_PRIMARY_ROLES_AND_PLURAL_CENSUS_DRAFT', applied=False,
        papers=PAPERS, source_proof=source_rows, added_reviews=6, prior_reviews_preserved=1043,
        reviewed_roles=1049, supporting_pointers=375, observations=2476, prior_observations=2475,
        new_observations=1, newly_detected_paper='P169', unrelated_observations_preserved=2470,
        current_reviews=byte_pin(after['reviews']), current_papers=byte_pin(after['papers']),
        reviews_before=byte_pin(before['reviews']), papers_before=byte_pin(before['papers']),
        curator_pin=pin(Path(__file__)), registry_review=pin(out / 'registry-source-review.json'),
        whole_paper_reproduced=False, new_catalog_credit=False)
    if a.apply:
        from verify_scdrs_workflow_evidence import inspect
        proof, files = inspect()
        assert proof['workflow_ready_for_catalog'] and proof['source_roles_reviewed'] == 6
        targets = ['scdrs-prior-reviews.json.gz', 'scdrs-prior-papers.json.gz', 'scdrs-applied-reviews.json',
            'scdrs-source-role-coverage.json', 'scdrs-reviews-shared-source-compatibility.json', 'scdrs-registry-source-review.json']
        assert not any((data / name).exists() for name in targets)
        assert review_path.read_bytes() == before['reviews'] and paper_path.read_bytes() == before['papers']
        for key in before: (data / ('scdrs-prior-' + key + '.json.gz')).write_bytes(gzip.compress(before[key], mtime=0))
        save(data / 'scdrs-applied-reviews.json', dict(status='SIX_SCDRS_PRIMARY_ROLE_REVIEWS', updates=updates))
        save(data / 'scdrs-registry-source-review.json', registry_review)
        delta = inverse(before['reviews'], after['reviews'])
        delta['path'] = 'tools/literature/reviewed-uses.json'
        assert reverse_delta(after['reviews'], delta) == before['reviews']
        save(data / 'scdrs-reviews-shared-source-compatibility.json', dict(schema_version=1,
            status='passed-dispatch-catalog-only-source-deltas', tool_id='scdrs', paths=[delta],
            scope='Six appended source-bound scDRS reviews;1043 prior review objects and all scientific sources unchanged.'))
        review_path.write_bytes(after['reviews'])
        paper_path.write_bytes(after['papers'])
        report.update(applied=True, prior_review_archive=pin(data / 'scdrs-prior-reviews.json.gz'),
            prior_papers_archive=pin(data / 'scdrs-prior-papers.json.gz'), applied_reviews=pin(data / 'scdrs-applied-reviews.json'),
            registry_review=pin(data / 'scdrs-registry-source-review.json'))
        save(data / 'scdrs-source-role-coverage.json', report)
        verify_source(ROOT, data, delta['path'], delta['before_sha256'])
    save(out / 'COMPLETED.json', report)
    print(report['status'], 'applied', report['applied'], flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable source/registry/review/observation preservation checks')
    main()
