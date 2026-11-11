#!/usr/bin/env python3
"""Verify and optionally apply the twenty bcl2fastq/RSEM primary-method reviews."""
import argparse
import copy
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, publication_record, tool_matches, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
DATA = ROOT / 'docs/literature/omics-2023-2026'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def serialize(document, original):
    raw = (json.dumps(document, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    return raw.replace(b'\n', b'\r\n') if original.count(b'\n') == original.count(b'\r\n') else raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args(); args.out.mkdir(parents=True, exist_ok=False)
    paper_path = DATA / 'papers.json'; review_path = HERE / 'reviewed-uses.json'
    paper_before = paper_path.read_bytes(); review_before = review_path.read_bytes()
    papers = json.loads(paper_before); updated = copy.deepcopy(papers)
    document = json.loads(review_before); merged = copy.deepcopy(document)
    registry = {r['id']: r for r in json.loads((HERE / 'tool-registry.json').read_text(encoding='utf-8'))}
    rows = json.loads((HERE / 'quantification-use-reviews.json').read_text(encoding='utf-8'))
    assert len(papers) == 250 and len(rows) == 20
    observed = {(p['paper_id'], t['tool_id']) for p in papers for t in p['tools'] if t['tool_id'] in {'rsem', 'bcl2fastq'}}
    assert observed == {(r['paper_id'], r['review']['tool_id']) for r in rows}
    original_reviews = {(r['pmcid'], r['tool_id']): r for r in document['reviews']}
    assert len(original_reviews) == len(document['reviews'])
    added = 0; proof = []
    allowed = {'use_summary', 'implementation_scope_note', 'version_reported_near_name', 'supporting_evidence', 'review_status', 'evidence_selection'}
    for row in rows:
        review = row['review']; paper = next(p for p in updated if p['paper_id'] == row['paper_id'])
        assert review['pmcid'] == paper['pmcid'] and review['doi'] == paper['doi']
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert digest(raw) == paper['xml_sha256']
        sources = paragraphs(ET.fromstring(raw)); validate_supporting_evidence(sources, [review], registry)
        tool_id = review['tool_id']; matched = tool_matches(sources, [registry[tool_id]], {tool_id: review['evidence_paragraph_sha256']})
        assert len(matched) == 1 and matched[0]['evidence_paragraph_index'] == row['primary_paragraph']
        checked = publication_record(dict(paper, tools=matched), [review])['tools'][0]
        index = next(i for i, t in enumerate(paper['tools']) if t['tool_id'] == tool_id); old = paper['tools'][index]
        assert {k: v for k, v in old.items() if k not in allowed} == {k: v for k, v in checked.items() if k not in allowed}
        key = (review['pmcid'], tool_id)
        if key in original_reviews:
            assert original_reviews[key] == review, 'Refuse an unreviewed prior-use revision'
        else:
            merged['reviews'].append(review); added += 1
        paper['tools'][index] = checked
        proof.append(dict(paper_id=paper['paper_id'], tool_id=tool_id, xml_sha256=paper['xml_sha256'], paragraph_sha256=review['evidence_paragraph_sha256'], paragraph_index=row['primary_paragraph']))
    assert merged['reviews'][:len(document['reviews'])] == document['reviews']
    for old, new in zip(papers, updated):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [t for t in old['tools'] if t['tool_id'] not in {'rsem', 'bcl2fastq'}] == [t for t in new['tools'] if t['tool_id'] not in {'rsem', 'bcl2fastq'}]
    assert sum(len(p['tools']) for p in updated) == 2428
    report = dict(status='passed-twenty-quantification-primary-source-role-reviews', applied=False, papers=250, observations=2428, reviewed_roles=20, added_reviews=added, prior_reviews_preserved=len(document['reviews']), total_reviews=len(merged['reviews']), supporting_pointers=sum(len(r.get('supporting_evidence', [])) for r in merged['reviews']), unrelated_observations_preserved=2408, source_proof=proof, curator_sha256=digest(Path(__file__).read_bytes()), draft_sha256=digest((HERE / 'quantification-use-reviews.json').read_bytes()), whole_paper_reproduced=False)
    (args.out / 'before-papers.json.gz').write_bytes(gzip.compress(paper_before, mtime=0))
    (args.out / 'before-reviewed-uses.json.gz').write_bytes(gzip.compress(review_before, mtime=0))
    if args.apply:
        assert paper_path.read_bytes() == paper_before and review_path.read_bytes() == review_before
        candidates = []
        for path, raw in [(review_path, serialize(merged, review_before)), (paper_path, serialize(updated, paper_before))]:
            with tempfile.NamedTemporaryFile('wb', dir=path.parent, delete=False) as stream:
                stream.write(raw); candidates.append((path, Path(stream.name), digest(raw)))
        try:
            for path, temporary, expected in candidates:
                os.replace(temporary, path); assert digest(path.read_bytes()) == expected
        finally:
            for _, temporary, _ in candidates: temporary.unlink(missing_ok=True)
        report.update(applied=True, papers_sha256=digest(paper_path.read_bytes()), reviews_sha256=digest(review_path.read_bytes()))
    (args.out / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'source_proof'}))


if __name__ == '__main__':
    main()
