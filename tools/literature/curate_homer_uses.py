#!/usr/bin/env python3
"""Validate twelve excerpt-free HOMER software and motif-resource roles against the frozen original article XML."""
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


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    papers = {p['paper_id']: p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    registry = {r['id']: r for r in json.loads((HERE/'tool-registry.json').read_text(encoding='utf-8'))}
    rows_path = HERE/'homer-use-reviews.json'
    rows = json.loads(rows_path.read_text(encoding='utf-8'))
    observed = {p['paper_id'] for p in papers.values() if any(t['tool_id'] == 'homer' for t in p['tools'])}
    assert len(rows) == 12 and len({r['paper_id'] for r in rows}) == 12 and observed == {r['paper_id'] for r in rows}
    path = HERE/'reviewed-uses.json'
    before = path.read_bytes()
    before_hash = hashlib.sha256(before).hexdigest()
    document = json.loads(before)
    merged = list(document['reviews'])
    by_key = {(r['pmcid'], r['tool_id']): r for r in merged}
    assert len(by_key) == len(merged)
    proof = []
    updated_reviews = []
    for row in rows:
        paper = papers[row['paper_id']]
        review = row['review']
        assert review['tool_id'] == 'homer' and review['pmcid'] == paper['pmcid'] and review['doi'] == paper['doi']
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        sources = paragraphs(ET.fromstring(raw))
        source = sources[row['primary_paragraph']-1]
        assert source['paragraph_index'] == row['primary_paragraph'] and hashlib.sha256(source['text'].encode()).hexdigest() == review['evidence_paragraph_sha256']
        validate_supporting_evidence(sources, [review], registry)
        matched = tool_matches(sources, [registry['homer']], {'homer': review['evidence_paragraph_sha256']})
        assert len(matched) == 1 and matched[0]['evidence_paragraph_index'] == row['primary_paragraph']
        current = next(t for t in paper['tools'] if t['tool_id'] == 'homer')
        assert current['evidence_paragraph_sha256'] == review['evidence_paragraph_sha256']
        checked = publication_record(dict(paper, tools=matched), [review])['tools'][0]
        for field in ['use_summary', 'implementation_scope_note', 'version_reported_near_name']:
            assert checked[field] == review[field]
        key = (review['pmcid'], review['tool_id'])
        if key in by_key:
            old = by_key[key]
            if old != review:
                expected = dict(pmcid='PMC11525197', doi='10.1038/s41588-024-01911-7', tool_id='homer',
                    evidence_paragraph_sha256='b7c4273d16d9c388e975990c42dd98f2966f691674eca94a1b99786ea0834757',
                    use_summary='Performed motif analysis and peak annotation with the Homer package 4.11.1 after CUT&Tag peak generation.',
                    implementation_scope_note='Background sequences, motif search and genomic annotation parameters are not stated. These analyses remain separate missing integrations.',
                    version_reported_near_name='4.11.1')
                assert old == expected and review['evidence_paragraph_sha256'] == old['evidence_paragraph_sha256'], ('Refuse an unreviewed replacement', key)
                merged[merged.index(old)] = review
                by_key[key] = review
                updated_reviews.append(dict(paper_id=row['paper_id'], prior=old, replacement=review,
                    reason='Expand the unchanged primary CUT&Tag pointer with checked lowercase-Homer result links, distinguish canonical PU.1 scoring and time-course ATAC roles, and retain parameter uncertainty.'))
        else:
            merged.append(review)
            by_key[key] = review
        proof.append(dict(paper_id=row['paper_id'],observed_role=row['observed_role'],primary_paragraph=row['primary_paragraph'],paragraph_sha256=review['evidence_paragraph_sha256'],xml_sha256=paper['xml_sha256'],reported_version=review['version_reported_near_name'],supporting_pointers=len(review['supporting_evidence'])))
    assert [r for r in merged if r['tool_id'] != 'homer'] == [r for r in document['reviews'] if r['tool_id'] != 'homer']
    report = dict(status='passed-homer-source-role-validation',applied=args.apply,reviewed_roles=12,added_reviews=len(merged)-len(document['reviews']),updated_reviews=updated_reviews,total_reviews=len(merged),total_supporting_pointers=sum(len(r.get('supporting_evidence', [])) for r in merged),prior_reviews_sha256=before_hash,registry_sha256=digest(HERE/'tool-registry.json'),curator_sha256=digest(Path(__file__)),review_rows_sha256=digest(rows_path),proof=proof,scope='Source-role audit only. Peak calling/coverage,exon counting/RPKM,genomic or FASTA motif analysis,known motifs,feature/TE/TSS annotation and motif-model resource reuse remain distinct. P126 does not report a HOMER software run;P040 recovers5.1,older4.11/4.11.1 and P215 E-value wording remain explicit. No runtime/adapter/upstream-parity credit. Cell Ranger remains excluded;paper-wide semantic completeness stays open.')
    if args.apply:
        assert digest(path) == before_hash
        history = ROOT/'build/literature-review/catalog-history'
        history.mkdir(exist_ok=True)
        archive = history/(before_hash+'-reviewed-uses.json.gz')
        if not archive.exists():
            archive.write_bytes(gzip.compress(before, mtime=0))
        document['reviews'] = merged
        with tempfile.NamedTemporaryFile('w',encoding='utf-8',newline='\n',dir=path.parent,delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(json.dumps(document,ensure_ascii=False,indent=2)+'\n')
        try:
            os.replace(temporary,path)
        finally:
            temporary.unlink(missing_ok=True)
        report['reviews_sha256'] = digest(path)
    (args.out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ['status','applied','reviewed_roles','added_reviews','total_reviews','total_supporting_pointers']}))


if __name__ == '__main__':
    main()
