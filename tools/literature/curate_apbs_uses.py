#!/usr/bin/env python3
"""Validate Two APBS source roles;original solver subset has separate validation."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, tool_matches, publication_record, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True, help='New excerpt-free evidence directory')
    parser.add_argument('--apply', action='store_true', help='Append validated reviews; never replace an existing different review')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    data = ROOT/'docs/literature/omics-2023-2026'
    papers = {p['paper_id']: p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))}
    registry = {r['id']: r for r in json.loads((HERE/'tool-registry.json').read_text(encoding='utf-8'))}
    rows_path = HERE/'apbs-use-reviews.json'
    rows = json.loads(rows_path.read_text(encoding='utf-8'))
    assert len(rows) == 2 and len({r['paper_id'] for r in rows}) == 2
    assert {r['paper_id'] for r in rows} == {'P013', 'P026'}, 'Frozen source scan supplies exactly two APBS uses'
    path = HERE/'reviewed-uses.json'
    before = path.read_bytes()
    original_hash = hashlib.sha256(before).hexdigest()
    document = json.loads(before)
    merged = list(document['reviews'])
    by_key = {(r['pmcid'], r['tool_id']): r for r in merged}
    assert len(by_key) == len(merged)
    proof = []
    for row in rows:
        paper = papers[row['paper_id']]
        review = row['review']
        assert review['tool_id'] == 'apbs'
        assert paper['doi'] == review['doi'] and paper['pmcid'] == review['pmcid']
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        sources = paragraphs(ET.fromstring(raw))
        primary = sources[row['primary_paragraph']-1]
        assert primary['paragraph_index'] == row['primary_paragraph']
        assert hashlib.sha256(primary['text'].encode()).hexdigest() == review['evidence_paragraph_sha256']
        validate_supporting_evidence(sources, [review], registry)
        tools = tool_matches(sources, [registry['apbs']], {'apbs': review['evidence_paragraph_sha256']})
        assert len(tools) == 1 and tools[0]['evidence_paragraph_index'] == row['primary_paragraph']
        checked = publication_record(dict(paper, tools=tools), [review])['tools'][0]
        for field in ['use_summary', 'implementation_scope_note', 'version_reported_near_name']:
            assert checked[field] == review[field], (row['paper_id'], field)
        key = (review['pmcid'], review['tool_id'])
        if key in by_key:
            assert by_key[key] == review, ('Existing review changed; inspect separately', key)
        else:
            merged.append(review)
            by_key[key] = review
        proof.append(dict(paper_id=row['paper_id'], primary_paragraph=row['primary_paragraph'], observed_role=row['observed_role'], evidence_sha256=review['evidence_paragraph_sha256'], supporting_pointers=len(review.get('supporting_evidence', []))))
    result = dict(status='passed-source-role-validation', applied=args.apply, reviewed_apbs_roles=2,
                  total_reviews=len(merged), added_reviews=len(merged)-len(document['reviews']),
                  original_reviews_sha256=original_hash, proof=proof, curator_sha256=sha(__file__),
                  review_rows_sha256=sha(rows_path),
                  scope='Excerpt-free APBS roles in P013/P026 only. The APBS74 citation suffix is recognized;neighboring PyMOL versions are not APBS versions. No explicit PDB2PQR use was found in the250-paper name scan. Original3.5.0 solver validation,charge preparation,PyMOL rendering and historical/paper-specific reconstruction remain distinct evidence. Paper-wide completeness remains open.')
    if args.apply:
        assert sha(path) == original_hash, 'Review file changed during validation'
        history = ROOT/'build/literature-review/catalog-history'
        history.mkdir(exist_ok=True)
        archive = history/(original_hash+'-reviewed-uses.json.gz')
        if not archive.exists():
            archive.write_bytes(gzip.compress(before, mtime=0))
        document['reviews'] = merged
        with tempfile.NamedTemporaryFile('w', encoding='utf-8', dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(json.dumps(document, ensure_ascii=False, indent=2)+'\n')
        try:
            assert sha(path) == original_hash, 'Review file changed before publication'
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)
        result['reviews_sha256'] = sha(path)
    (args.out/'verification.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k: result[k] for k in ['status', 'applied', 'total_reviews', 'added_reviews']}))


if __name__ == '__main__':
    main()
