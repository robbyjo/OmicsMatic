"""Read-only full-cohort MetaXcan/PredictDB role audit; article texts stay private."""
from pathlib import Path
import argparse
import gzip
import json
import re
import sys
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, matching_words
from gemma_primary import encoded, pin, require

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/metaxcan-source-audit'
PATTERN = re.compile(r'predi[\W_]*xcan|multi[\W_]*xcan|meta[\W_]*xcan|hakyimlab/MetaXcan|predictdb', re.I)
TARGETS = frozenset(('metaxcan', 'predixcan', 'multixcan'))


def anchor(paper, paragraph):
    return dict(paper_id=paper['paper_id'], doi=paper['doi'], pmcid=paper['pmcid'],
                xml_sha256=paper['xml_sha256'], paragraph_index=paragraph['paragraph_index'],
                section=paragraph['section'], paragraph_sha256=pin(paragraph['text'].encode())['sha256'])


def audit(root, cache):
    root, cache = Path(root), Path(cache)
    census = json.loads((root / FOLDER / 'CENSUS.json').read_bytes())
    roles = json.loads((root / FOLDER / 'ROLES.json').read_bytes())
    papers = json.loads((root / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    require(len(papers) == len(census['XML']) == 250 and [p['paper_id'] for p in papers] == list(census['XML']), 'Changed frozen cohort')
    require(census['broad_alias_pattern'] == PATTERN.pattern, 'Changed broad alias pattern')
    hits, other, all_paragraphs = [], [], {}
    for paper in papers:
        expected = census['XML'][paper['paper_id']]
        require(paper['pmcid'] == expected['pmcid'] and paper['xml_sha256'] == expected['sha256'], 'Changed XML declaration')
        raw = gzip.decompress((cache / (paper['pmcid'] + '.xml.gz')).read_bytes())
        require(pin(raw) == {k: expected[k] for k in ('bytes', 'sha256')}, 'Changed complete XML bytes')
        tree = ET.fromstring(raw)
        for q in paragraphs(tree):
            a = anchor(paper, q)
            all_paragraphs[paper['paper_id'], q['paragraph_index']] = a
            if PATTERN.search(q['match_text']):
                hits.append(a)
        for location in ('front/article-meta/abstract', 'back', 'floats-group', 'body//supplementary-material'):
            for element in tree.findall(location):
                if PATTERN.search(matching_words(element)):
                    other.append(dict(paper_id=paper['paper_id'], location=location))
    classified = census['anchors']
    require([{k: v for k, v in row.items() if k not in ('classification', 'decision')} for row in classified] == hits
            and len(hits) == 25 and other == census['other_structures'], 'Changed broad census; inspect without refreshing old hashes')
    require(all(r['classification'] in ('positive-method', 'supporting-method', 'code-availability-duplicate',
            'framework-or-background', 'comparator-not-executed', 'discussion-not-executed') and r['decision'] for r in classified), 'Unreviewed source hit')
    expected_pairs = {('P019', 'predixcan'), ('P074', 'metaxcan'), ('P074', 'predixcan'), ('P090', 'multixcan'),
                      ('P203', 'metaxcan'), ('P221', 'metaxcan'), ('P221', 'predixcan'), ('P239', 'predixcan')}
    require({(r['paper_id'], r['tool_id']) for r in roles['roles']} == expected_pairs and len(roles['roles']) == 8, 'Changed eight source roles')
    observed = {(p['paper_id'], u['tool_id']) for p in papers for u in p['tools'] if u['tool_id'] in TARGETS}
    require(observed == expected_pairs, 'Missing/extra positive MetaXcan observation')
    for role in roles['roles']:
        paper = next(p for p in papers if p['paper_id'] == role['paper_id'])
        use = next(u for u in paper['tools'] if u['tool_id'] == role['tool_id'])
        require({k: use.get(k) for k in role['raw_source_fields']} == role['raw_source_fields'], 'Raw source/version changed')
        require(role['whole_paper_reproduced'] is False and role['paper_edition_reproduced'] is False, 'Expanded reproduction claim')
    training = roles['additional_training_roles']
    require([(r['paper_id'], r['tool_id']) for r in training] == [('P019', 'predictdb'), ('P074', 'predictdb')], 'Changed training software roles')
    for role in roles['roles'] + training:
        require(all(all_paragraphs[a['paper_id'], a['paragraph_index']] == a for a in role['primary_anchors']), 'Changed semantic role anchor')
        require(role['remaining_scope'] and role['use_summary'], 'Missing role/limitations')
    require(next(r for r in training if r['paper_id'] == 'P074')['literal_reported_version'] == '7', 'PredictDB v7 silently changed')
    require(all(r['implementation_available'] is False for r in training), 'Training wrongly admitted through summary association')
    require(roles['nonexecuted_mentions'] == ['P095', 'P238'], 'Comparator/discussion promoted to execution')
    return dict(status='PASS_FULL250_METAXCAN_AND_PREDICTDB_PRIMARY_SOURCE_ROLES', primary_XML_checked=250,
                matched_papers=8, broad_paragraphs=25, existing_positive_observations=8,
                additional_positive_training_observations=2, positive_study_papers=6,
                author_unreported_editions_or_modes_inferred=False, original_paper_analyses_reexecuted=False,
                training_implemented=False, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--cache', type=Path, required=True)
    a = parser.parse_args()
    try:
        print(json.dumps(audit(a.root, a.cache), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT SOURCE AUDIT SKIPPED: ' if code == 3 else 'FAIL2 NO SOURCE AUDIT ADMITTED: ')
              + str(error) + '. Preserve attempts and hashes; restore the exact permitted source.', file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
