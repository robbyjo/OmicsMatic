#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify all 250 primary XMLs and distinguish PoPS computation, reuse and a lipid name."""
from pathlib import Path
import argparse, gzip, hashlib, json, re, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PATTERN = re.compile(r'(?<![A-Za-z0-9_])pops(?![A-Za-z0-9_])|polygenic\s+priority\s+scor', re.I)
ROLES = {
    'P088': dict(anchor='291f5d08b6a9ab633bcb3cb49c9ec2f4c0b31801183c3d411aa09a06f6973e88', version='0.2', context='analysis-method-description',
                 summary='Computed PoPS for 18,383 genes using the full provided features and MAGMA 1.10 gene results based on European 1000 Genomes Phase 3 LD. Locus and genome-wide PoPS ranks then contributed separately weighted components to an omnibus cardiomyopathy gene-prioritization score.',
                 open_scope='The author commit, complete commands and numerical controls are not identified. MAGMA, biological reference-panel construction, locus windows, top-three/top-200 scoring and the omnibus prioritization model are distinct steps.'),
    'P090': dict(anchor='47b445c0ead88859592b723a383a67cb8d89224e1d0fb6f388c1e1346837848f', version='0.1', context='analysis-method-description',
                 summary='Computed PoPS v0.1 gene-feature enrichment scores. Top-three genes at each locus helped define initial candidates, followed by eight equally weighted sources of evidence. Tied final scores did not yield a single high-confidence gene.',
                 open_scope='Legacy v0.1 has separate feature-selection and chromosome-held-out prediction programs. The v0.2 native kernel is not a v0.1 substitute. Exact author source, complete feature/LD settings and downstream combined evidence are unresolved.'),
    'P120': dict(anchor='b5d3c2dc6b07d4029edce95b1f075b24529eefed6c5e26a89deeb24d1b2adc52', version='0.2', context='analysis-method-description',
                 summary='Used PoPS to map hypothyroidism loci through MAGMA gene associations/correlations from European 1000 Genomes LD and gene-feature enrichment over more than 50,000 features. Genes in the top score decile were prioritized. Code availability explicitly lists PoPS v0.2.',
                 open_scope='The exact commit, commands, reference preparation and decile tie policy are not reported here. Do not treat project defaults or a native regression control as the author analysis.'),
    'P153': dict(anchor='38fe252ccd36b46fed400516b31c9b20be1b1a7767a6e29431a3046f68a9ad9d', version=None, context='benchmark-or-comparison',
                 summary='Benchmarked enhancer-to-gene predictions by intersecting the two highest model-ranked genes with the two highest PoPS-ranked genes per credible set. PoPS ranks were also displayed for genes near the lymphocyte-count variant rs7696969.',
                 open_scope='The excerpt establishes ranking/intersection use; it does not establish a fresh PoPS fit, version, author binary or upstream score provenance. Credible-set construction and model ranking are separate.'),
    'P220': dict(anchor='1aed40711d552b3b8f544a2de13899d8afbbd02c4eccbf260770affec4b41a73', version=None, context='external-result-reuse',
                 summary='Downloaded published PoPS gene scores for BMI, waist-to-hip ratio adjusted for BMI and body-fat percentage from FinucaneLab. Compared score enrichment between genes with significant rare-variant associations and other genes.',
                 open_scope='This is reuse of published scores, not a PoPS estimator run. Score release identity, the three trait mappings and downstream one-sided t tests must be recorded independently.'),
    'P236': dict(anchor='8191e29af05a0df9c4f9263e59fc95d9af26d4ff8c18da71850bb39895d7408f', version=None, context='external-result-reuse',
                 summary='Used published PoPs gene-prioritization scores as a comparator for Speos ulcerative-colitis core-gene analyses and linked FinucaneLab score data. This use was missed by the former case-sensitive PoPS name pattern.',
                 open_scope='No new PoPS fit or version is established. Score release, comparator gene-selection rules, enrichment statistics and Speos are separate. Admit a new paper-tool observation only through a validated catalog transition.')
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def census(root=ROOT):
    root = Path(root).resolve()
    papers = json.loads((root / 'docs/literature/omics-2023-2026/papers.json').read_bytes())
    expected = json.loads((root / 'docs/literature/pops-native-development/PRIMARY-PINS.json').read_bytes())
    assert len(papers) == 250 and set(expected['software_papers']) == set(ROLES)
    primary, found, reviews = {}, {}, []
    for paper in papers:
        raw = gzip.decompress((root / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert digest(raw) == paper['xml_sha256'], paper['paper_id']
        pid = paper['paper_id']
        primary[pid] = dict(pmcid=paper['pmcid'], bytes=len(raw), xml_sha256=digest(raw))
        matches = []
        for i, node in enumerate(ET.fromstring(raw).iter('p')):
            text = ' '.join(''.join(node.itertext()).split())
            if PATTERN.search(text):
                matches.append(dict(xml_all_p_index=i, paragraph_sha256=digest(text.encode())))
        if not matches:
            continue
        assert pid in expected['matches'], 'New broad PoPS mention needs manual review: ' + pid
        assert matches == expected['matches'][pid], 'Primary PoPS paragraph population differs: ' + pid
        found[pid] = matches
        if pid in expected['rejected_name_matches']:
            assert pid == 'P239'
            continue
        role = ROLES[pid]
        assert role['anchor'] in {p['paragraph_sha256'] for p in matches}, pid
        reviews.append(dict(paper_id=pid, pmcid=paper['pmcid'], doi=paper['doi'], xml_sha256=paper['xml_sha256'],
                            tool_id='pops', evidence_paragraph_sha256=role['anchor'],
                            source_normalization='Unicode-whitespace collapse over XML p itertext',
                            use_summary=role['summary'], implementation_scope_note=role['open_scope'],
                            usage_context=role['context'], version_reported_near_name=role['version'],
                            exact_author_binary_build_established=False, whole_paper_reproduced=False))
    assert set(found) == set(expected['matches'])
    inventory = json.loads((root / 'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())
    prior = next(p for p in inventory if p['id'] == 'pops')
    return dict(status='PASS_ALL_250_POPS_PRIMARY_XML_SIX_SOFTWARE_USES_AND_ONE_LIPID_REJECTION',
                papers_scanned=250, primary_files=primary, broad_name_match_papers=found,
                software_papers=sorted(ROLES), rejected_name_matches=expected['rejected_name_matches'],
                observations_pending_catalog_admission=sorted(set(ROLES) - set(prior['papers'])), reviews=reviews,
                current_catalog_changed=False, new_catalog_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        result = census()
        (args.out / 'CENSUS.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(result['status'])
        return 0
    except Exception as error:
        (args.out / 'FAILURE.json').write_text(json.dumps(dict(cause=repr(error), required_action='Retain exact primary XML and name-match pins. Review the named added/missing role before admission; never change old hashes to suppress a refusal.'), indent=2) + '\n', encoding='utf-8')
        print('FAIL2_POPS_PRIMARY_REVIEW', repr(error))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
