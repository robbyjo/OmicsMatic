# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preview distinct MEME-related program identities without changing the catalog."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_omics_review import paragraphs, tool_matches

PROGRAMS = [
    ('meme-chip', 'MEME-ChIP', r'(?<![A-Za-z0-9_])MEME[- ]ChIP(?![A-Za-z0-9_])', 'Combined motif discovery, comparison and positional-enrichment workflow'),
    ('dreme', 'DREME', r'(?<![A-Za-z0-9_])DREME(?![A-Za-z0-9_])', 'Discriminative short-motif discovery'),
    ('centrimo', 'CentriMo', r'(?<![A-Za-z0-9_])CentriMo(?![A-Za-z0-9_])', 'Positional motif enrichment'),
    ('tomtom', 'Tomtom', r'(?<![A-Za-z0-9_])Tomtom(?![A-Za-z0-9_])', 'Comparison of supplied or discovered sequence motifs'),
    ('streme', 'STREME', r'(?<![A-Za-z0-9_])STREME(?![A-Za-z0-9_])', 'Sequence motif discovery with enrichment or central-distance objectives'),
    ('hyphy', 'HyPhy', r'(?<![A-Za-z0-9_])HyPhy(?![A-Za-z0-9_])', 'Phylogenetic selection models; method and tested family must be specified'),
    ('bwa-meme', 'BWA-MEME', r'(?<![A-Za-z0-9_])BWA[- _]+MEME(?![A-Za-z0-9_])', 'DNA read alignment with the distinct BWA-MEME implementation'),
]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    if not __debug__:
        raise RuntimeError('Never disable cohort and source checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    path = ROOT / 'tools/literature/tool-registry.json'
    original = path.read_bytes()
    registry = json.loads(original)
    assert len(registry) == 418 and len({t['id'] for t in registry}) == 418
    updated = copy.deepcopy(registry)
    for tool in updated:
        if tool['id'] == 'meme':
            tool['pattern'] = r'(?<!HyPhy\s)(?<!BWA[-\s])(?-i:(?<![A-Za-z0-9_])(?:MEME(?:[- ]Suite)?)(?![A-Za-z0-9_]))'
        elif tool['id'] == 'bwa':
            tool['pattern'] = r'(?<![A-Za-z0-9_])BWA(?![- _]?MEM(?:[- ]?[23]|E\b))(?:[- ]MEM)?(?![A-Za-z0-9_])'
    for tool_id, name, pattern, role in PROGRAMS:
        assert tool_id not in {t['id'] for t in updated}
        re.compile(pattern, re.I)
        updated.append(dict(id=tool_id, name=name, pattern=pattern, role=role, current_coverage='missing', existing_source=None,
                            coverage_note='Distinct named program identified during primary-text completeness review. Parent suite mentions, another program or resource format do not supply this implementation; source-specific integration and original validation pending.'))
    papers = json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    reviews = json.loads((ROOT / 'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len(papers) == 250 and len(reviews) == 793
    preferred = {(r['pmcid'], r['tool_id']): r['evidence_paragraph_sha256'] for r in reviews}
    affected = {'meme', 'bwa', *[p[0] for p in PROGRAMS]}
    before_counts, after_counts, records = {}, {}, []
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert sha(raw) == paper['xml_sha256']
        ps = paragraphs(ET.fromstring(raw))
        prefs = {tool: value for (pmc, tool), value in preferred.items() if pmc == paper['pmcid']}
        matches = tool_matches(ps, updated, prefs)
        prior = tool_matches(ps, registry, prefs)
        assert {t['tool_id']: t for t in matches if t['tool_id'] not in affected} == {t['tool_id']: t for t in prior if t['tool_id'] not in affected}
        removed = sorted({t['tool_id'] for t in prior} - {t['tool_id'] for t in matches})
        added = sorted({t['tool_id'] for t in matches} - {t['tool_id'] for t in prior})
        assert not (set(removed) - {'meme', 'bwa'})
        for values, census in [(prior, before_counts), (matches, after_counts)]:
            for t in values:
                census[t['tool_id']] = census.get(t['tool_id'], 0) + 1
        if added or removed or any(t['tool_id'] == 'meme' for t in matches):
            records.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'],
                                removed_ids=removed, added_ids=added, proposed_matches=[t for t in matches if t['tool_id'] in affected]))
    assert path.read_bytes() == original
    out.mkdir(parents=True)
    (out / 'registry-before.json.gz').write_bytes(gzip.compress(original, mtime=0))
    (out / 'registry-proposed.json').write_text(json.dumps(updated, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    result = dict(status='preview-distinct-MEME-suite-and-evolutionary-programs-manual-review-open', source_papers_scanned=250,
                  registry_rows_before=418, registry_rows_proposed=len(updated), before_counts=before_counts, after_counts=after_counts,
                  affected_papers=records, catalog_changed=False, registry_changed=False, implementation_credit=False,
                  source_pins={str(Path(__file__).relative_to(ROOT).as_posix()): sha(Path(__file__).read_bytes()), 'tools/literature/tool-registry.json': sha(original)})
    (out / 'PREVIEW.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    text = []
    for row in records:
        text.append(row['paper_id'] + ' ' + row['doi'] + ' ADD ' + ','.join(row['added_ids']) + ' REMOVE ' + ','.join(row['removed_ids']))
        for t in row['proposed_matches']:
            if t['tool_id'] == 'bwa' and not row['removed_ids']:
                continue
            text.extend([t['tool_id'] + ' paragraph ' + str(t['evidence_paragraph_index']) + ' ' + t['evidence_section'], t['evidence_text']])
        text.append('')
    (out / 'candidate-roles.txt').write_text('\n'.join(text), encoding='utf-8', newline='\n')
    print(json.dumps(dict(status=result['status'], registry_rows_proposed=len(updated), changes=[{k: row[k] for k in ['paper_id', 'added_ids', 'removed_ids']} for row in records],
                          proposed_counts={key: after_counts.get(key, 0) for key in sorted(affected)}, catalog_changed=False)))


if __name__ == '__main__':
    main()
