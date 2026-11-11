# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit complete cached PLINK/KING primary uses before extending relatedness workflows."""
import argparse, gzip, hashlib, json, re, xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
from setup_deseq2_reference import ROOT, pin, save
from catalog_fixed_cohort import verify_fixed_cohort
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    data = ROOT/'docs/literature/omics-2023-2026'
    papers = json.loads((data/'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    inventory = {r['id']: r for r in json.loads((data/'tool-inventory.json').read_bytes())}
    reviews = json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    previous = {(r['pmcid'], r['tool_id']): r for r in reviews}
    patterns = {name: re.compile(inventory[name]['pattern'], re.I) for name in ['plink', 'plink2', 'king']}
    terms = {name: re.compile(pattern, re.I) for name, pattern in {
        'kinship': r'kinship|KING|relatedness|unrelated|relationships?|related individuals',
        'ibd': r'\bIBD\b|pi[_ -]?hat|identity.by.descent|\bgenome\b|\bZ[012]\b',
        'ld': r'\bLD\b|clump|prun|indep.pairwise|linkage disequilibrium',
        'qc': r'\bQC\b|quality control|\bHWE\b|Hardy|missing|\bMAF\b|sex check',
        'pca': r'\bPCA\b|principal components?|\bPCs\b',
        'association': r'\bGWAS\b|regression|--glm|--linear|--logistic|firth|association',
        'score': r'--score|polygenic|\bPRS\b|\bPGS\b|risk score',
        'conversion': r'convert|recode|--make|format|\bVCF\b|\bPGEN\b|dosage',
        'nonhuman': r'wheat|cucumber|plant|tetraploid|polyploid|accession|--chr-set|extra.chr'}.items()}
    records, all_mentions = [], []
    for paper in papers:
        path = ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')
        raw = gzip.decompress(path.read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        xml = ET.fromstring(raw)
        assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type') == 'doi').lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        for paragraph in ps:
            paragraph['paragraph_sha256'] = hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest()
        observed = {r['tool_id']: r for r in paper['tools'] if r['tool_id'] in patterns}
        matches = {name: [p for p in ps if pattern.search(p['match_text'])] for name, pattern in patterns.items()}
        for name, rows in matches.items():
            if rows:
                all_mentions.append(dict(paper_id=paper['paper_id'], tool=name, observed=name in observed,
                    paragraphs=[p['paragraph_index'] for p in rows]))
        for name, observation in observed.items():
            source = next(p for p in ps if p['paragraph_index'] == observation['evidence_paragraph_index'])
            assert source['paragraph_sha256'] == observation['evidence_paragraph_sha256']
            candidates = {p['paragraph_index']: p for p in matches[name]}
            candidates[source['paragraph_index']] = source
            prior = previous.get((paper['pmcid'], name))
            if prior:
                for support in prior.get('supporting_evidence', []):
                    p = next(p for p in ps if p['paragraph_index'] == support['paragraph_index'])
                    assert p['paragraph_sha256'] == support['paragraph_sha256']
                    candidates[p['paragraph_index']] = p
            rows = sorted(candidates.values(), key=lambda p: p['paragraph_index'])
            text = '\n'.join(p['text'] for p in rows)
            records.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
                tool=name, xml_sha256=paper['xml_sha256'], primary=observation['evidence_paragraph_index'],
                prior_review=prior, operation_hints=[k for k, pattern in terms.items() if pattern.search(text)],
                paragraphs=[dict(paragraph_index=p['paragraph_index'], section=p['section'],
                    paragraph_sha256=p['paragraph_sha256'], text=p['text']) for p in rows]))
    counts = {name: sum(r['tool'] == name for r in records) for name in patterns}
    assert counts == {'plink': 52, 'plink2': 25, 'king': 15}
    save(out/'source-roles.private.json', records)
    save(out/'all-mentions.private.json', all_mentions)
    related = [r for r in records if 'kinship' in r['operation_hints'] or 'ibd' in r['operation_hints']]
    save(out/'COMPLETED.json', dict(status='PASS250_PRIMARY_SOURCES_AND92_PLINK_KING_ROLE_INTAKE_ONLY',
        papers_scanned=250, source_roles=92, source_counts=counts,
        previously_reviewed_roles=sum(r['prior_review'] is not None for r in records),
        relatedness_candidates=[dict(paper_id=r['paper_id'], tool=r['tool'], hints=r['operation_hints']) for r in related],
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), data/'papers.json', data/'tool-inventory.json', ROOT/'tools/literature/reviewed-uses.json']},
        regex_hints_are_semantic_reviews=False, no_new_catalog_credit=True, catalog_modified=False,
        analysis_performed=False, whole_goal_complete=False))
    print('Verified250 primary XML/DOIs;92 existing PLINK/KING roles; no catalog mutation')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete primary/source identity checks')
    main()
