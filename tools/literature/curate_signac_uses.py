# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review nine retained Signac uses and one explicit LR caption; preserve unrelated observations."""
import argparse
import copy
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs, publication_record, validate_supporting_evidence
from collect_signac_omics import tool_matches
from curate_shapeit_uses import inverse, serialize
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import reverse_delta

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/signac-runtime'
HERE = ROOT / 'tools/literature'
DATA = ROOT / 'docs/literature/omics-2023-2026'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    paths = dict(papers=DATA/'papers.json', reviews=HERE/'reviewed-uses.json', enrichment=DATA/'catalog-enrichment.json')
    before = {k: p.read_bytes() for k, p in paths.items()}
    papers = json.loads(before['papers'])
    previous = json.loads(before['reviews'])
    drafts = json.loads((HERE/'signac-use-reviews.json').read_bytes())
    registry = json.loads((HERE/'tool-registry.json').read_bytes())
    assert (len(papers), len(registry), len(previous['reviews']), len(drafts)) == (250, 425, 880, 10)
    assert not any(r['tool_id'] == 'signac' for r in previous['reviews'])
    verify_fixed_cohort(ROOT, DATA, papers)
    reviews = copy.deepcopy(previous)
    reviews['reviews'].extend(d['review'] for d in drafts)
    assert len(reviews['reviews']) == 890 and reviews['reviews'][:880] == previous['reviews']
    preferred = {(r['pmcid'], r['tool_id']): r['evidence_paragraph_sha256'] for r in reviews['reviews']}
    by_draft = {d['review']['pmcid']: d for d in drafts}
    updated = copy.deepcopy(papers)
    cache_updates = {}
    source_proof = []
    changed = []
    added = []
    for original, paper in zip(papers, updated):
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert sha(raw) == paper['xml_sha256']
        if paper['pmcid'] not in by_draft:
            assert paper == original
            continue
        draft = by_draft[paper['pmcid']]
        review = draft['review']
        ps = paragraphs(ET.fromstring(raw))
        preferences = {tool: h for (pmc, tool), h in preferred.items() if pmc == paper['pmcid']}
        found = {t['tool_id']: t for t in tool_matches(ps, registry, preferences)}
        old = {t['tool_id']: t for t in original['tools']}
        assert set(found) == set(old) | {'signac'}, paper['paper_id']
        for name, tool in old.items():
            assert (tool['evidence_paragraph_index'], tool['evidence_paragraph_sha256']) == (
                found[name]['evidence_paragraph_index'], sha(found[name]['evidence_text'].encode('utf8'))), (paper['paper_id'], name)
        candidate = found['signac']
        assert (draft['paper_id'], review['doi'], review['evidence_paragraph_sha256']) == (
            paper['paper_id'], paper['doi'], sha(candidate['evidence_text'].encode('utf8')))
        validate_supporting_evidence(ps, [review], {r['id']: r for r in registry})
        published = publication_record(dict(paper, tools=[candidate]), [review])['tools'][0]
        paper['tools'] = sorted([t for t in paper['tools'] if t['tool_id'] != 'signac'] + [published], key=lambda t: t['tool_id'])
        assert [t for t in original['tools'] if t['tool_id'] != 'signac'] == [t for t in paper['tools'] if t['tool_id'] != 'signac']
        assert {k:v for k,v in original.items() if k != 'tools'} == {k:v for k,v in paper.items() if k != 'tools'}
        cache = ROOT/'build/literature-review/extracted'/(paper['pmcid']+'.json')
        old_cache = cache.read_bytes()
        cached = json.loads(old_cache)
        is_new = 'signac' not in old
        assert sum(t['tool_id'] == 'signac' for t in cached['tools']) == (0 if is_new else 1)
        cached['tools'] = sorted([t for t in cached['tools'] if t['tool_id'] != 'signac'] + [candidate], key=lambda t: t['tool_id'])
        cache_updates[cache] = (old_cache, serialize(cached, old_cache))
        changed.append(paper['paper_id'])
        if is_new:
            added.append(paper['paper_id'])
        source_proof.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
            xml_sha256=paper['xml_sha256'], paragraph_index=candidate['evidence_paragraph_index'],
            paragraph_sha256=review['evidence_paragraph_sha256'], supporting_pointers=len(review['supporting_evidence']),
            reported_version=review['version_reported_near_name'], newly_recovered_analysis_caption=is_new))
    assert len(changed) == len(cache_updates) == 10 and added == ['P134']
    assert sum(len(p['tools']) for p in updated) == 2445
    assert len({t['tool_id'] for p in updated for t in p['tools']}) == 387
    verify_fixed_cohort(ROOT, DATA, updated)
    prior = json.loads(before['enrichment'])
    enrichment = dict(previous_catalog_sha256=sha(before['papers']), registry_sha256=sha((HERE/'tool-registry.json').read_bytes()),
        source_cohort_unchanged=True, paper_count=250, history=prior.get('history', [])+[{k:v for k,v in prior.items() if k!='history'}],
        changes=[dict(paper_id=pid, reviewed_ids=['signac'], newly_recovered_analysis_caption=pid in added) for pid in changed],
        extension_method='All250 retained XML hashes checked; unchanged425-row registry and nine original Signac anchors preserved. Add only P134 exact Results/Figure5g Signac logistic-regression declaration. Exclude institutional SignAC P007. Append10 reviews, preserve880 prior reviews and every unrelated paper observation. No implementation credit by curation.')
    after = dict(papers=serialize(updated, before['papers']), reviews=serialize(reviews, before['reviews']), enrichment=serialize(enrichment, before['enrichment']))
    delta = inverse(before['reviews'], after['reviews'])
    assert reverse_delta(after['reviews'], delta) == before['reviews']
    bridge = dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='signac',
        scope='Append10 primary Signac reviews; preserve880 prior reviews and reconstruct every prior byte. No scientific-source waiver or implementation credit.', paths=[delta])
    report = dict(schema_version=1, status='passed-ten-Signac-primary-role-reviews', applied=False,
        source_papers_XML_hashes_checked=250, selected_papers_reextracted=10, registry_rows=425,
        distinct_named_tools=387, prior_observations=2444, total_observations=2445, prior_reviews=880,
        prior_reviews_preserved=880, new_reviews=10, total_reviews=890, reviewed_roles=10,
        supporting_pointers=sum(len(r.get('supporting_evidence', [])) for r in reviews['reviews']),
        Signac_supporting_pointers=sum(p['supporting_pointers'] for p in source_proof), recovered_analysis_captions=added,
        institutional_name_collision_excluded='P007', source_proof=source_proof,
        curator_sha256=sha(Path(__file__).read_bytes()), draft_sha256=sha((HERE/'signac-use-reviews.json').read_bytes()),
        reviewed_extractor_sha256=sha((HERE/'collect_signac_omics.py').read_bytes()),
        declaration_ledger_sha256=sha((HERE/'signac-source-additions.json').read_bytes()),
        implementation_promoted=False, whole_paper_reproduced=False)
    for name, path in paths.items():
        (out/('before-'+path.name+'.gz')).write_bytes(gzip.compress(before[name], mtime=0))
    for path, (raw, _) in cache_updates.items():
        (out/('PRIVATE-before-cache-'+path.name+'.gz')).write_bytes(gzip.compress(raw, mtime=0))
    if args.apply:
        target = DATA/'signac-use-shared-source-compatibility.json'
        roles = DATA/'signac-role-coverage.json'
        assert not target.exists() and not roles.exists()
        assert all(p.read_bytes() == before[k] for k,p in paths.items())
        assert all(p.read_bytes() == v[0] for p,v in cache_updates.items())
        target.write_text(json.dumps(bridge, indent=2)+'\n', encoding='utf8', newline='\n')
        for key, path in paths.items():
            path.write_bytes(after[key])
        for path, (_, raw) in cache_updates.items():
            path.write_bytes(raw)
        report.update(applied=True, papers_sha256=sha(after['papers']), reviews_sha256=sha(after['reviews']))
        roles.write_text(json.dumps(report, indent=2)+'\n', encoding='utf8', newline='\n')
    (out/'verification.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf8', newline='\n')
    print(json.dumps({k:v for k,v in report.items() if k!='source_proof'}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact cohort/review guards')
    main()
