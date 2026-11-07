# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every current source pointer, including the already admitted exact HT-seq alias."""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, validate_supporting_evidence
from collect_htseq_omics import tool_matches as htseq_matches
from catalog_fixed_cohort import verify_fixed_cohort
from setup_deseq2_reference import ROOT, pin, save
from monocle_catalog_context import load_catalog_registry, verify_current_review_extension


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    data = ROOT/'docs/literature/omics-2023-2026'
    papers = json.loads((data/'papers.json').read_bytes())
    registry_rows = load_catalog_registry(ROOT)
    registry = {t['id']: t for t in registry_rows}
    verify_fixed_cohort(ROOT, data, papers)
    assert len(papers) == len({p['doi'].lower() for p in papers}) == len({p['pmcid'] for p in papers}) == 250
    assert len(registry) == len(registry_rows) == 427
    prior = json.loads((data/'htseq-role-coverage.json').read_bytes())
    assert pin(ROOT/'tools/literature/collect_htseq_omics.py')['sha256'] == prior['alias_guard_sha256']
    assert pin(ROOT/'tools/literature/htseq-source-additions.json')['sha256'] == prior['alias_ledger_sha256']
    observations = 0
    aliases = []
    sources = {}
    for i, paper in enumerate(papers, 1):
        assert paper['paper_id'] == f'P{i:03d}'
        assert '2023-10-23' <= paper['first_publication_date'] <= '2026-10-23'
        assert paper['journal_abbreviation'] in {'Nature', 'Nat Genet', 'Am J Hum Genet'}
        assert paper['article_type'] in {'research-article', 'methods-article'}
        assert paper['publisher_url'] == 'https://doi.org/'+paper['doi']
        assert paper['fulltext_url'] == 'https://pmc.ncbi.nlm.nih.gov/articles/'+paper['pmcid']+'/'
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert sha(raw) == paper['xml_sha256']
        xml = ET.fromstring(raw)
        doi = next(el.text for el in xml.findall('front/article-meta/article-id') if el.get('pub-id-type') == 'doi')
        assert doi.lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        sources[paper['pmcid']] = ps
        by_paragraph = {p['paragraph_index']: p for p in ps}
        cache = json.loads((ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')).read_bytes())
        by_tool = {t['tool_id']: t for t in cache['tools']}
        assert paper['tools'] and len({t['tool_id'] for t in paper['tools']}) == len(paper['tools'])
        for tool in paper['tools']:
            assert tool['tool_id'] in registry
            assert 'evidence_text' not in tool and 'evidence_sentence' not in tool
            source = by_tool[tool['tool_id']]
            anchor = by_paragraph[tool['evidence_paragraph_index']]
            assert anchor['text'] == source['evidence_text']
            assert sha(anchor['text'].encode('utf8')) == tool['evidence_paragraph_sha256']
            assert source['evidence_paragraph_index'] == tool['evidence_paragraph_index']
            if not re.search(registry[tool['tool_id']]['pattern'], anchor['match_text'], re.I):
                # This branch admits only the old reviewed P005 anchor. The original
                # alias collector checks its exact hash, section, spelling and source guards.
                assert (paper['paper_id'], tool['tool_id'], anchor['paragraph_index']) == ('P005', 'htseq', 54)
                recovered = htseq_matches([anchor], [registry['htseq']], {'htseq': tool['evidence_paragraph_sha256']})
                assert len(recovered) == 1 and recovered[0]['matched_text'] == 'HT-seq'
                aliases.append(dict(paper_id='P005', tool_id='htseq', paragraph=54, paragraph_sha256=tool['evidence_paragraph_sha256']))
            assert tool['use_summary']
            observations += 1
    assert observations == 2449 and len(aliases) == 1
    for tool in registry_rows:
        if tool['existing_source']:
            assert (ROOT/tool['existing_source']).is_file()
    reviews = json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len({(r['pmcid'], r['tool_id']) for r in reviews}) == len(reviews) == 917
    verify_current_review_extension(ROOT,data,reviews)
    by_pmcid = {p['pmcid']: p for p in papers}
    for review in reviews:
        paper = by_pmcid[review['pmcid']]
        tool = next(t for t in paper['tools'] if t['tool_id'] == review['tool_id'])
        assert paper['doi'].lower() == review['doi'].lower()
        for key in ['evidence_paragraph_sha256', 'use_summary', 'implementation_scope_note',
                    'usage_context', 'version_reported_near_name', 'supporting_evidence']:
            if key in review:
                assert tool[key] == review[key]
        assert tool['review_status'] == 'usage-summary-reviewed; paper-wide-completeness-review-open'
        validate_supporting_evidence(sources[paper['pmcid']], [review], registry)
    supporting = sum(len(r.get('supporting_evidence', [])) for r in reviews)
    assert supporting == 309
    result = dict(status='passed-current-primary-source-pointer-audit', source_records=250, unique_dois=250,
        source_xml_hashes_verified=250, fulltext_dois_verified=250, tool_observation_pointers_verified=observations,
        individually_reviewed_usage_summaries_verified=len(reviews), supporting_paragraph_pointers_verified=supporting,
        unchanged_previously_admitted_aliases=aliases, journals=dict(Counter(p['journal_abbreviation'] for p in papers)),
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(),
            ROOT/'tools/literature/collect_omics_review.py', ROOT/'tools/literature/collect_htseq_omics.py',
            ROOT/'tools/literature/htseq-source-additions.json', ROOT/'tools/literature/tool-registry.json', ROOT/'tools/literature/monocle_catalog_context.py', ROOT/'tools/literature/monocle-named-software-registry.json']},
        papers_pin=pin(data/'papers.json'), reviews_pin=pin(ROOT/'tools/literature/reviewed-uses.json'),
        semantic_paper_wide_completeness_review_open=True, numerical_or_biological_parity_claimed=False)
    save(data/'monocle-source-audit.json', result)
    print(json.dumps({k: v for k, v in result.items() if k not in ['source_pins', 'unchanged_previously_admitted_aliases']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source-pointer checks')
    main()
