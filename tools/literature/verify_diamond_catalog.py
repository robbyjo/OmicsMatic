# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen DIAMOND catalog relationships without changing earlier scientific verifiers."""
import argparse, collections, gzip, json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack
from catalog_source_integrity import verify_source
from verify_diamond_evidence import verify as verify_component
DATA = ROOT/'docs/literature/omics-2023-2026'
STATUS = 'PASS_DIAMOND_CATALOG_SOURCE_ROOT_BROWSER_INTEGRATION_WITH_GOAL_OPEN'


def verify(check_current=True):
    proof = json.loads((DATA/'diamond-catalog-validation.json').read_bytes())
    assert proof['status'] == STATUS and proof['new_catalog_credit'] and not proof['whole_goal_complete']
    assert not proof['whole_package_implemented'] and not proof['whole_paper_reproduced']
    for name, value in proof['source_pins'].items():
        verify_source(ROOT, DATA, name, value['sha256'])
    for name, value in proof['component_files'].items():
        assert pin(ROOT/name) == value, ('Frozen DIAMOND component changed', name)
    verify_component()
    archive = DATA/'diamond-catalog-support.zip'
    assert pin(archive) == proof['archive']
    files = unpack(archive, proof)
    before = {r['id']: r for r in json.loads(files['catalog-before/tool-inventory.json'])}
    after = {r['id']: r for r in json.loads(files['catalog-snapshot/tool-inventory.json'])}
    assert set(before) == set(after) and len(after) == 400
    assert all(before[n] == after[n] for n in before if n != 'diamond')
    assert before['diamond']['implementation_status'] == 'missing'
    assert after['diamond']['implementation_status'] == 'upstream-adapter-validated'
    assert after['diamond']['source_roles_reviewed'] == after['diamond']['paper_count'] == 5
    assert after['diamond']['root_command'] == 'diamond-tools' and not after['diamond']['canonical_adapter_available']
    assert after['cellranger']['implementation_status'] == 'excluded-by-user' and after['spyrrow']['implementation_status'] == 'missing'
    counts = dict(collections.Counter(r['implementation_status'] for r in after.values()))
    summary = json.loads(files['catalog-snapshot/summary.json'])
    assert counts == summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'],
        counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (250, 400, 2466, 19, 76, 3, 246)
    reviews = json.loads(files['current-source/tools/literature/reviewed-uses.json'])['reviews']
    prior = json.loads(gzip.decompress(files['catalog-snapshot/diamond-prior-reviews.json.gz']))['reviews']
    assert len(prior) == 1024 and reviews[:1024] == prior and len(reviews) == 1029
    assert len({(r['pmcid'], r['tool_id']) for r in reviews}) == 1029
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews) == 367
    assert all(r['tool_id'] == 'diamond' for r in reviews[1024:])
    papers = json.loads(files['catalog-snapshot/papers.json'])
    old_papers = json.loads(gzip.decompress(files['catalog-snapshot/diamond-prior-papers.json.gz']))
    changed = []
    for old, new in zip(old_papers, papers, strict=True):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [r for r in old['tools'] if r['tool_id'] != 'diamond'] == [r for r in new['tools'] if r['tool_id'] != 'diamond']
        if old != new:
            changed.append(new['paper_id'])
    assert changed == ['P017', 'P037', 'P054', 'P171', 'P248']
    assert sum(len(p['tools']) for p in papers) == 2466
    p037 = next(p for p in papers if p['paper_id'] == 'P037')
    assert next(t for t in p037['tools'] if t['tool_id'] == 'diamond')['version_reported_near_name'] == '2.0.0'
    assert files['catalog-snapshot/WARNINGS.md'].startswith(b'## DIAMOND: warnings and required actions')
    assert b'95 new scoped integrations' in files['catalog-snapshot/README.md']
    assert b'246 named tools still missing' in files['catalog-snapshot/README.md']
    base = 'build/literature-review/diamond-runtime/'
    assert json.loads(files[base+'catalog-source-audit-1/COMPLETED.json'])['reviewed_uses_verified'] == 1029
    assert len(json.loads(files[base+'catalog-shared-gates-1/COMPLETED.json'])['checks']) == 41
    browser = json.loads(files[base+'catalog-ui-2/COMPLETED.json'])
    assert browser['status'] == 'PASS_UNCHANGED299_GENERAL_AND11_DIAMOND_BROWSER_CHECKS'
    correction = json.loads(files['component-metadata/CONTROLLER_EXIT_FIX.json'])
    assert correction['prior_controller_final_return_gate_nonzero'] and correction['scientific_result_changes'] is False
    if check_current:
        for name, value in proof['catalog_snapshot'].items():
            assert pin(DATA/name) == value
        for name, value in proof['page_pins'].items():
            assert pin(ROOT/name) == value
        expected = {p.name: pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
        rows = (DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines()
        actual = {line.split('  ', 1)[1]: line.split('  ', 1)[0] for line in rows}
        assert len(rows) == len(actual) and actual == expected
    return proof, files


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact frozen catalog checks')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archived-catalog-only', action='store_true')
    args = p.parse_args()
    report, _ = verify(not args.archived_catalog_only)
    print(report['status'])
