# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain MEME homonyms and named-suite evidence across the complete primary cohort."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_omics_review import paragraphs, words


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    if not __debug__:
        raise RuntimeError('Never disable primary-source identity checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    data = ROOT / 'docs/literature/omics-2023-2026'
    before = {name: sha((data / name).read_bytes()) for name in ['papers.json', 'tool-inventory.json', 'summary.json']}
    papers = json.loads((data / 'papers.json').read_bytes())
    assert len(papers) == 250
    records, census, extra = [], [], []
    named = re.compile(r'(?<![A-Za-z0-9_])MEME(?![A-Za-z0-9_])', re.I)
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert sha(raw) == paper['xml_sha256']
        xml = ET.fromstring(raw)
        ps = paragraphs(xml)
        matches = [dict(p, sha256=sha(p['text'].encode('utf-8'))) for p in ps if named.search(p['match_text'])]
        current = [t for t in paper['tools'] if t['tool_id'] == 'meme']
        assert len(current) <= 1
        census.append(dict(paper_id=paper['paper_id'], xml_sha256=paper['xml_sha256'], matching_paragraphs=len(matches), catalogued=bool(current)))
        if not matches:
            assert not current
            continue
        record = {k: paper[k] for k in ['paper_id', 'title', 'doi', 'pmcid', 'xml_sha256']}
        indices = {p['paragraph_index'] for p in matches}
        neighbors = [dict(p, sha256=sha(p['text'].encode('utf-8'))) for p in ps
                     if p['paragraph_index'] not in indices and any(abs(p['paragraph_index'] - n) == 1 for n in indices)]
        refs = [dict(id=ref.get('id'), text=words(ref)) for ref in xml.findall('.//ref')
                if re.search(r'MEME|HyPhy|Murrell|motif discovery|motif elicitation', words(ref), re.I)]
        record.update(matches=matches, neighboring_context=neighbors, candidate_references=refs)
        if current:
            primary = next(p for p in matches if p['paragraph_index'] == current[0]['evidence_paragraph_index'])
            assert primary['sha256'] == current[0]['evidence_paragraph_sha256']
            record.update(primary_paragraph=primary['paragraph_index'], current_use=current[0])
            records.append(record)
        else:
            extra.append(record)
    assert len(records) == 9
    assert all(sha((data / name).read_bytes()) == value for name, value in before.items())
    out.mkdir(parents=True)
    result = dict(schema_version=1, status='primary-source-MEME-homonym-and-suite-audit-curation-open',
                  source_papers_scanned=250, catalogued_roles=len(records), papers=records,
                  source_census=census, additional_uncatalogued_mentions=extra, catalog_before=before,
                  source_pins={path.relative_to(ROOT).as_posix(): sha(path.read_bytes()) for path in [Path(__file__), ROOT / 'tools/literature/collect_omics_review.py']},
                  catalog_changed=False, implementation_credit=False, scientific_runtime_validated=False,
                  scope='Candidate paragraphs and nearby context are retained for manual semantic review. MEME Suite discovery/scanning/comparison and HyPhy MEME must be distinguished. References and adjacent text do not establish execution or transfer tool versions/options.')
    (out / 'source-audit.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    text = []
    for record in records + extra:
        text.append(record['paper_id'] + ' ' + record['doi'])
        for p in record['matches']:
            text.extend([str(p['paragraph_index']) + ' ' + p['section'], p['text']])
        text.extend(['REFERENCES', *[r['id'] + ': ' + r['text'] for r in record['candidate_references']], ''])
    (out / 'named-evidence.txt').write_text('\n'.join(text), encoding='utf-8', newline='\n')
    print(json.dumps(dict(status=result['status'], source_papers_scanned=250, catalogued_roles=len(records), additional_mentions=len(extra), catalog_changed=False)))


if __name__ == '__main__':
    main()
