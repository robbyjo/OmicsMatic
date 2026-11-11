# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply five primary-bound DIAMOND reviews while preserving every previous review/tool row."""
import argparse, copy, gzip, hashlib, json, re, xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from curate_shapeit_uses import inverse, serialize
from collect_omics_review import paragraphs, tool_matches, publication_record, validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from cellchat_catalog import load_catalog_registry
from run_mrmega_catalog_checks import ArchivedCatalogView
from catalog_source_integrity import reverse_delta
BASE = ROOT / 'build/literature-review/diamond-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--apply', action='store_true')
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    paths = dict(papers=data/'papers.json', reviews=ROOT/'tools/literature/reviewed-uses.json')
    before = {k: p.read_bytes() for k, p in paths.items()}
    papers, old = json.loads(before['papers']), json.loads(before['reviews'])
    verify_fixed_cohort(ROOT, data, papers)
    assert len(old['reviews']) == 1024 and not any(r['tool_id'] == 'diamond' for r in old['reviews'])
    draft_path = ROOT/'tools/literature/diamond-use-reviews.json'
    draft = json.loads(draft_path.read_bytes())
    assert draft['status'] == 'five-primary-bound-diamond-role-reviews'
    drafts = {r['paper_id']: r for r in draft['reviews']}
    assert set(drafts) == {'P017', 'P037', 'P054', 'P171', 'P248'}
    relative = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    descriptor = json.loads((data/'diamond-dispatch-shared-source-compatibility.json').read_bytes())
    prior_root = reverse_delta((ROOT/relative).read_bytes(), descriptor['paths'][0])
    registry = {r['id']: r for r in load_catalog_registry(ArchivedCatalogView(ROOT, {relative: prior_root}))}
    updated, reviewed = copy.deepcopy(papers), copy.deepcopy(old)
    caches, anchors, changed = [], {}, []
    for paper in updated:
        source = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(source).hexdigest() == paper['xml_sha256']
        xml = ET.fromstring(source)
        assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type') == 'doi').lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        matches = [p for p in ps if re.search(registry['diamond']['pattern'], p['match_text'], re.I)]
        assert bool(matches) == (paper['paper_id'] in drafts), ('Unreviewed source mention', paper['paper_id'])
        if not matches:
            continue
        item, review = drafts[paper['paper_id']], drafts[paper['paper_id']]['review']
        assert item['prior_review'] is None
        assert (review['pmcid'], review['doi'], review['tool_id']) == (paper['pmcid'], paper['doi'], 'diamond')
        validate_supporting_evidence(ps, [review], registry)
        extracted = tool_matches(ps, [registry['diamond']], {'diamond': review['evidence_paragraph_sha256']})
        assert len(extracted) == 1
        publication = publication_record(dict(paper, tools=extracted), [review])['tools'][0]
        at = [i for i, r in enumerate(paper['tools']) if r['tool_id'] == 'diamond']
        assert len(at) == 1
        paper['tools'][at[0]] = publication
        reviewed['reviews'].append(review)
        cache = ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')
        raw = cache.read_bytes()
        cached = json.loads(raw)
        ix = [i for i, r in enumerate(cached['tools']) if r['tool_id'] == 'diamond']
        assert len(ix) == 1
        cached['tools'][ix[0]] = extracted[0]
        caches.append((cache, raw, serialize(cached, raw)))
        changed.append(paper['paper_id'])
        anchors[paper['paper_id']] = dict(pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'],
            primary_paragraph=extracted[0]['evidence_paragraph_index'], primary_sha256=review['evidence_paragraph_sha256'],
            role_kind=item['role_kind'])
    assert len(changed) == 5
    assert reviewed['reviews'][:1024] == old['reviews']
    assert len(reviewed['reviews']) == len({(r['pmcid'], r['tool_id']) for r in reviewed['reviews']}) == 1029
    assert sum(len(r.get('supporting_evidence', [])) for r in reviewed['reviews']) == 367
    assert sum(len(p['tools']) for p in updated) == 2466
    for previous, current in zip(papers, updated, strict=True):
        assert {k: v for k, v in previous.items() if k != 'tools'} == {k: v for k, v in current.items() if k != 'tools'}
        assert [r for r in previous['tools'] if r['tool_id'] != 'diamond'] == [r for r in current['tools'] if r['tool_id'] != 'diamond']
        assert len(previous['tools']) == len(current['tools'])
    after = dict(papers=serialize(updated, before['papers']), reviews=serialize(reviewed, before['reviews']))
    for name, raw in before.items():
        (out/('before-'+name+'.json.gz')).write_bytes(gzip.compress(raw, mtime=0))
    for name, raw in after.items():
        (out/('candidate-'+name+'.json')).write_bytes(raw)
    record = dict(status='passed-five-diamond-primary-role-reviews', applied=a.apply, prior_reviews=1024,
        unchanged_prior_reviews=1024, new_reviews=5, total_reviews=1029, supporting_pointers=367,
        observations=2466, source_records=250, changed_paper_roles=changed, source_anchors=anchors,
        draft_pin=pin(draft_path), curator_sha256=pin(Path(__file__))['sha256'],
        before_reviews_sha256=hashlib.sha256(before['reviews']).hexdigest(),
        reviews_sha256=hashlib.sha256(after['reviews']).hexdigest(), papers_sha256=hashlib.sha256(after['papers']).hexdigest(),
        all_non_diamond_source_roles_preserved=True, primary_article_text_added_to_reviews=False,
        whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False)
    if a.apply:
        routed = json.loads((BASE/'root-validation-1/COMPLETED.json').read_bytes())
        assert routed['status'] == 'PASS216_DIAMOND_ROOT_COMPLETE_CHECKS18_REFUSALS3_SKIPS15_PREDECESSORS3_ALIGNMENTS'
        destinations = [data/n for n in ['diamond-role-coverage.json', 'diamond-prior-reviews.json.gz',
            'diamond-prior-papers.json.gz', 'diamond-reviews-shared-source-compatibility.json']]
        assert not any(p.exists() for p in destinations)
        assert all(p.read_bytes() == before[k] for k, p in paths.items())
        assert all(p.read_bytes() == raw for p, raw, _ in caches)
        destinations[1].write_bytes(gzip.compress(before['reviews'], mtime=0))
        destinations[2].write_bytes(gzip.compress(before['papers'], mtime=0))
        record.update(prior_review_archive=pin(destinations[1]), prior_papers_archive=pin(destinations[2]))
        row = inverse(before['reviews'], after['reviews'])
        row['path'] = 'tools/literature/reviewed-uses.json'
        save(destinations[3], dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='diamond',
            scope='Five appended primary-bound DIAMOND role reviews; every1024 prior review retained in an exact archive. No new observation or scientific-source exception.', paths=[row]))
        for key, p in paths.items():
            p.write_bytes(after[key])
        for p, _, raw in caches:
            p.write_bytes(raw)
        save(destinations[0], record)
    save(out/'COMPLETED.json', record)
    print('Validated250 primary records;1029 reviews/367 pointers/2466 observations; applied='+str(a.apply))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/population preservation checks')
    main()
