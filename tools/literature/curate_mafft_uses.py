#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify/apply eight MAFFT primary-source roles, preserving every unrelated review."""
import argparse
import copy
import gzip
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from curate_shapeit_uses import digest, serialize, inverse
from collect_omics_review import paragraphs, publication_record, tool_matches, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
DATA = ROOT / 'docs/literature/omics-2023-2026'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args(); args.out.mkdir(parents=True, exist_ok=False)
    paper_path, review_path = DATA / 'papers.json', HERE / 'reviewed-uses.json'
    paper_before, review_before = paper_path.read_bytes(), review_path.read_bytes()
    papers, document = json.loads(paper_before), json.loads(review_before)
    updated, merged = copy.deepcopy(papers), copy.deepcopy(document)
    rows = json.loads((HERE / 'mafft-use-reviews.json').read_bytes()); assert len(rows) == 8
    registry = {row['id']: row for row in json.loads((HERE / 'tool-registry.json').read_bytes())}
    assert {(p['paper_id'], 'mafft') for p in papers for t in p['tools'] if t['tool_id'] == 'mafft'} == {(r['paper_id'], 'mafft') for r in rows}
    old_keys = {(r['pmcid'], r['tool_id']): i for i, r in enumerate(document['reviews'])}
    assert len(old_keys) == len(document['reviews']) == 752
    added = changed = 0; proof = []
    allowed = {'use_summary', 'implementation_scope_note', 'version_reported_near_name', 'supporting_evidence', 'review_status', 'evidence_selection'}
    for row in rows:
        review = row['review']; paper = next(p for p in updated if p['paper_id'] == row['paper_id'])
        assert (review['pmcid'], review['doi'], review['tool_id']) == (paper['pmcid'], paper['doi'], 'mafft')
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert digest(raw) == paper['xml_sha256']
        source = paragraphs(ET.fromstring(raw)); validate_supporting_evidence(source, [review], registry)
        matched = tool_matches(source, [registry['mafft']], {'mafft': review['evidence_paragraph_sha256']})
        assert len(matched) == 1 and matched[0]['evidence_paragraph_index'] == row['primary_paragraph']
        checked = publication_record(dict(paper, tools=matched), [review])['tools'][0]
        index = next(i for i, t in enumerate(paper['tools']) if t['tool_id'] == 'mafft'); old = paper['tools'][index]
        assert {k: v for k, v in old.items() if k not in allowed} == {k: v for k, v in checked.items() if k not in allowed}
        key = (review['pmcid'], 'mafft')
        if key in old_keys:
            assert paper['paper_id'] == 'P235'; merged['reviews'][old_keys[key]] = review; changed += 1
        else:
            merged['reviews'].append(review); added += 1
        paper['tools'][index] = checked
        proof.append(dict(paper_id=paper['paper_id'], xml_sha256=paper['xml_sha256'], paragraph_sha256=review['evidence_paragraph_sha256'], primary_paragraph=row['primary_paragraph'], supporting_paragraphs=[p['paragraph_index'] for p in review['supporting_evidence']]))
    assert (added, changed) == (7, 1)
    assert [r for r in merged['reviews'] if r['tool_id'] != 'mafft'] == [r for r in document['reviews'] if r['tool_id'] != 'mafft']
    for old, new in zip(papers, updated):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [t for t in old['tools'] if t['tool_id'] != 'mafft'] == [t for t in new['tools'] if t['tool_id'] != 'mafft']
    assert sum(len(p['tools']) for p in updated) == 2428
    paper_after, review_after = serialize(updated, paper_before), serialize(merged, review_before)
    compatibility = dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='mafft', scope='Exact inverse for seven added MAFFT review objects and the explicitly revised P235 MAFFT role. All 751 unrelated review objects and every scientific implementation remain unchanged.', prior_review_objects_preserved=751, new_review_objects=7, revised_review_objects=1, paths=[inverse(review_before, review_after)])
    (args.out / 'before-papers.json.gz').write_bytes(gzip.compress(paper_before, mtime=0))
    (args.out / 'before-reviewed-uses.json.gz').write_bytes(gzip.compress(review_before, mtime=0))
    result = dict(status='passed-eight-MAFFT-primary-source-role-reviews', applied=False, reviewed_roles=8, added_reviews=7, revised_reviews=1, prior_unrelated_reviews_preserved=751, total_reviews=len(merged['reviews']), supporting_pointers=sum(len(r.get('supporting_evidence', [])) for r in merged['reviews']), unrelated_observations_preserved=2420, source_proof=proof, curator_sha256=digest(Path(__file__).read_bytes()), draft_sha256=digest((HERE / 'mafft-use-reviews.json').read_bytes()), whole_paper_reproduced=False)
    if args.apply:
        assert paper_path.read_bytes() == paper_before and review_path.read_bytes() == review_before
        with (DATA / 'mafft-use-shared-source-compatibility.json').open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(json.dumps(compatibility, indent=2) + '\n')
        for path, raw in [(review_path, review_after), (paper_path, paper_after)]:
            with tempfile.NamedTemporaryFile('wb', dir=path.parent, delete=False) as stream:
                stream.write(raw); temporary = Path(stream.name)
            os.replace(temporary, path); assert digest(path.read_bytes()) == digest(raw)
        result.update(applied=True, papers_sha256=digest(paper_after), reviews_sha256=digest(review_after))
    (args.out / 'verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'source_proof'}))


if __name__ == '__main__':
    main()
