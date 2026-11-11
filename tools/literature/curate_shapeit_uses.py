#!/usr/bin/env python3
"""Verify/apply the ten SHAPEIT paper-use reviews while preserving prior reviews."""
import argparse
import base64
import copy
import difflib
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from catalog_source_integrity import reverse_delta
from collect_omics_review import paragraphs, publication_record, tool_matches, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
DATA = ROOT / 'docs/literature/omics-2023-2026'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def serialize(document, original):
    raw = (json.dumps(document, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    return raw.replace(b'\n', b'\r\n') if original.count(b'\n') == original.count(b'\r\n') else raw


def inverse(before, after):
    a, b = before.splitlines(keepends=True), after.splitlines(keepends=True)
    edits = []
    for tag, i, j, k, l in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        if tag == 'equal':
            continue
        old, new = b''.join(a[i:j]), b''.join(b[k:l])
        edits.append(dict(after_start_line=k, after_end_line=l, after_chunk_sha256=digest(new), before_bytes=len(old),
                          before_chunk_sha256=digest(old), before_gzip_base64=base64.b64encode(gzip.compress(old, mtime=0)).decode()))
    row = dict(path='tools/literature/reviewed-uses.json', before_sha256=digest(before), after_sha256=digest(after),
               before_bytes=len(before), after_bytes=len(after), edits=edits)
    assert reverse_delta(after, row) == before
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    paper_path, review_path = DATA / 'papers.json', HERE / 'reviewed-uses.json'
    paper_before, review_before = paper_path.read_bytes(), review_path.read_bytes()
    papers, document = json.loads(paper_before), json.loads(review_before)
    updated, merged = copy.deepcopy(papers), copy.deepcopy(document)
    rows = json.loads((HERE / 'shapeit-use-reviews.json').read_bytes())
    registry = {r['id']: r for r in json.loads((HERE / 'tool-registry.json').read_bytes())}
    assert len(papers) == 250 and len(rows) == 10
    observed = {(p['paper_id'], t['tool_id']) for p in papers for t in p['tools'] if t['tool_id'] == 'shapeit'}
    assert observed == {(r['paper_id'], r['review']['tool_id']) for r in rows}
    original_reviews = {(r['pmcid'], r['tool_id']): r for r in document['reviews']}
    assert len(original_reviews) == len(document['reviews'])
    allowed = {'use_summary', 'implementation_scope_note', 'version_reported_near_name', 'supporting_evidence', 'review_status', 'evidence_selection'}
    added, proof = 0, []
    for row in rows:
        review = row['review']; paper = next(p for p in updated if p['paper_id'] == row['paper_id'])
        assert (review['pmcid'], review['doi'], review['tool_id']) == (paper['pmcid'], paper['doi'], 'shapeit')
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert digest(raw) == paper['xml_sha256']
        source = paragraphs(ET.fromstring(raw)); validate_supporting_evidence(source, [review], registry)
        matched = tool_matches(source, [registry['shapeit']], {'shapeit': review['evidence_paragraph_sha256']})
        assert len(matched) == 1 and matched[0]['evidence_paragraph_index'] == row['primary_paragraph']
        checked = publication_record(dict(paper, tools=matched), [review])['tools'][0]
        index = next(i for i, t in enumerate(paper['tools']) if t['tool_id'] == 'shapeit'); old = paper['tools'][index]
        assert {k: v for k, v in old.items() if k not in allowed} == {k: v for k, v in checked.items() if k not in allowed}
        key = (review['pmcid'], 'shapeit')
        if key in original_reviews:
            assert original_reviews[key] == review, 'Refuse an unreviewed prior-use revision'
        else:
            merged['reviews'].append(review); added += 1
        paper['tools'][index] = checked
        proof.append(dict(paper_id=paper['paper_id'], xml_sha256=paper['xml_sha256'], paragraph_sha256=review['evidence_paragraph_sha256'],
                          primary_paragraph=row['primary_paragraph'], supporting_paragraphs=[p['paragraph_index'] for p in review['supporting_evidence']]))
    assert merged['reviews'][:len(document['reviews'])] == document['reviews']
    for old, new in zip(papers, updated):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [t for t in old['tools'] if t['tool_id'] != 'shapeit'] == [t for t in new['tools'] if t['tool_id'] != 'shapeit']
    assert sum(len(p['tools']) for p in updated) == 2428
    paper_after, review_after = serialize(updated, paper_before), serialize(merged, review_before)
    compatibility = None
    if review_after != review_before:
        delta = inverse(review_before, review_after)
        for path in DATA.glob('*-shared-source-compatibility.json'):
            assert not any(r['path'] == delta['path'] and r['after_sha256'] == delta['after_sha256'] for r in json.loads(path.read_bytes())['paths'])
        compatibility = dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='shapeit',
                             scope='Exact inverse only for ten appended SHAPEIT source-use review objects. Every prior object and scientific implementation remains unchanged.',
                             prior_review_objects_preserved=len(document['reviews']), new_review_objects=added, paths=[delta])
    (args.out / 'before-papers.json.gz').write_bytes(gzip.compress(paper_before, mtime=0))
    (args.out / 'before-reviewed-uses.json.gz').write_bytes(gzip.compress(review_before, mtime=0))
    report = dict(status='passed-ten-SHAPEIT-primary-source-role-reviews', applied=False, reviewed_roles=10, added_reviews=added,
                  prior_reviews_preserved=len(document['reviews']), total_reviews=len(merged['reviews']),
                  supporting_pointers=sum(len(r.get('supporting_evidence', [])) for r in merged['reviews']),
                  unrelated_observations_preserved=2418, source_proof=proof, curator_sha256=digest(Path(__file__).read_bytes()),
                  draft_sha256=digest((HERE / 'shapeit-use-reviews.json').read_bytes()), whole_paper_reproduced=False)
    if args.apply:
        assert paper_path.read_bytes() == paper_before and review_path.read_bytes() == review_before
        if compatibility:
            destination = DATA / 'shapeit-use-shared-source-compatibility.json'
            assert not destination.exists()
            destination.write_bytes((json.dumps(compatibility, indent=2) + '\n').encode())
        for path, raw in [(review_path, review_after), (paper_path, paper_after)]:
            with tempfile.NamedTemporaryFile('wb', dir=path.parent, delete=False) as stream:
                stream.write(raw); temporary = Path(stream.name)
            os.replace(temporary, path)
            assert digest(path.read_bytes()) == digest(raw)
        report.update(applied=True, papers_sha256=digest(paper_after), reviews_sha256=digest(review_after))
    (args.out / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'source_proof'}))


if __name__ == '__main__':
    main()
