"""Read-only full-cohort GEMMA source-role audit. Full article texts stay private."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from collect_omics_review import matching_words, paragraphs

ROOT = Path(__file__).resolve().parents[2]
FOLDER = 'docs/literature/gemma-source-audit'
PATTERN = re.compile(r'(?<![a-z0-9_])gemma(?![a-z0-9_])|genome[\s-]*wide[\s-]+efficient[\s-]+mixed[\s-]*model[\s-]+association', re.I)
VERSION = re.compile(r'\bGEMMA\s*\(?v\.?\s*([0-9]+(?:\.[0-9]+)+)', re.I)

def require(condition, message):
    if not condition:
        raise ValueError(message)

def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())

def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()

def audit(root, cache):
    root, cache = Path(root), Path(cache)
    census = json.loads((root / FOLDER / 'CENSUS.json').read_bytes())
    roles = json.loads((root / FOLDER / 'ROLES.json').read_bytes())['roles']
    papers = json.loads((root / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    expected = census['XML']
    require(len(papers) == len(expected) == 250, 'Changed frozen cohort population')
    require([p['paper_id'] for p in papers] == list(expected), 'Changed cohort identity/order')
    require(census['broad_alias_pattern'] == PATTERN.pattern, 'Changed broad alias contract')
    hits, other, literal_versions = [], [], []
    for paper in papers:
        identity = expected[paper['paper_id']]
        require(paper['pmcid'] == identity['pmcid'] and paper['xml_sha256'] == identity['sha256'], 'Changed frozen XML declaration')
        raw = gzip.decompress((cache / (paper['pmcid'] + '.xml.gz')).read_bytes())
        require(pin(raw) == {k: identity[k] for k in ('bytes', 'sha256')}, 'Changed original XML bytes')
        xml = ET.fromstring(raw)
        for q in paragraphs(xml):
            if PATTERN.search(q['match_text']):
                hits.append(dict(paper_id=paper['paper_id'], doi=paper['doi'], pmcid=paper['pmcid'],
                                 xml_sha256=paper['xml_sha256'], paragraph_index=q['paragraph_index'],
                                 section=q['section'], paragraph_sha256=pin(q['text'].encode())['sha256']))
                literal_versions.append((paper['paper_id'], q['paragraph_index'], VERSION.findall(q['match_text'])))
        for location in ('front/article-meta/abstract', 'back', 'floats-group', 'body//supplementary-material'):
            for element in xml.findall(location):
                if PATTERN.search(matching_words(element)):
                    other.append(dict(paper_id=paper['paper_id'], location=location))
    require(hits == census['anchors'] and len(hits) == 6 and other == census['other_structures'], 'Changed broad or other-structure source census; inspect without refreshing historical hashes')
    require([r['paper_id'] for r in roles] == ['P102', 'P171', 'P176', 'P248'], 'Changed source-role population')
    for role in roles:
        paper = next(p for p in papers if p['paper_id'] == role['paper_id'])
        uses = [u for u in paper['tools'] if u['tool_id'] == 'gemma']
        require(len(uses) == 1, 'Missing/duplicate raw GEMMA observation')
        require({k: uses[0].get(k) for k in role['raw_source_fields']} == role['raw_source_fields'], 'Raw source/version fields changed')
        anchors = [a for a in hits if a['paper_id'] == role['paper_id']]
        require(role['primary_anchors'] == anchors, 'Changed semantic-role anchors')
        versions = [v for p, i, v in literal_versions if p == role['paper_id']]
        require(versions == [[role['literal_reported_version']]] * len(anchors), 'Literal primary-source version differs')
        require(role['paper_edition_reproduced'] is False and role['whole_paper_reproduced'] is False, 'Expanded paper reproduction claim')
    p176 = next(r for r in roles if r['paper_id'] == 'P176')
    require(p176['literal_reported_version'] == '0.983' and p176['reported_edition'] is None, 'Unresolved P176 edition was silently repaired')
    return dict(status='PASS_FULL250_GEMMA_PRIMARY_SOURCE_ROLES', primary_XML_checked=250,
                matched_papers=4, eligible_method_paragraphs=6,
                literal_P176_0_983_preserved_and_unresolved=True,
                author_unreported_editions_or_modes_inferred=False,
                original_paper_analyses_reexecuted=False, whole_goal_complete=False)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--cache', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.root, args.cache), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3 DEPENDENT AUDIT SKIPPED: ' if code == 3 else 'FAIL2 NO SOURCE AUDIT ADMITTED: ')
              + str(error) + '. Restore the named exact permitted source; preserve old hashes and attempts.', file=sys.stderr)
        return code

if __name__ == '__main__':
    raise SystemExit(main())
