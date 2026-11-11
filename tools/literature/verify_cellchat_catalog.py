# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete CellChat catalog/root/source/browser checkpoint without runtime analysis."""
import argparse, gzip, json, zipfile
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source, reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
DATA = ROOT/'docs/literature/omics-2023-2026'
HOME = ROOT/'docs/literature/cellchat-catalog-development'
BASE = 'build/literature-review/cellchat-runtime/'
STATUS = 'PASS_CELLCHAT_CATALOG_ROOT_SOURCE_AND_BROWSER_INTEGRATION_WITH_GOAL_OPEN'


def verify(emit=True, folder=None, check_manifest=True):
    folder = DATA if folder is None else Path(folder)
    proof = json.loads((folder/'cellchat-catalog-validation.json').read_bytes())
    assert proof['status'] == STATUS and proof['new_catalog_credit'] and not proof['whole_goal_complete']
    assert (proof['papers'], proof['tools'], proof['observations'], proof['reviewed_roles'],
            proof['supporting_pointers']) == (250, 400, 2466, 1024, 357)
    assert (proof['new_scoped_integrations'], proof['including_existing_adapters'], proof['missing'],
            proof['unchanged_unrelated_tool_objects']) == (94, 97, 247, 399)
    assert pin(folder/'cellchat-catalog-support.zip') == proof['archive']
    files = unpack(folder/'cellchat-catalog-support.zip', proof)
    for name, wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name]) == wanted
        verify_source(ROOT, DATA, name, wanted['sha256'])
    for name, wanted in {**proof['frozen_development_files'], **proof['published_root_artifacts']}.items():
        assert pin(ROOT/name) == wanted, name
    for name, wanted in proof['catalog_snapshot'].items():
        assert pin(DATA/name) == wanted and byte_pin(files['catalog-snapshot/'+name]) == wanted
    for name, wanted in proof['page_pins'].items():
        assert pin(ROOT/name) == wanted and byte_pin(files['catalog-snapshot/'+name]) == wanted

    def document(name):
        return json.loads(files[BASE+name])

    def current(name):
        return json.loads(files['catalog-snapshot/'+name])

    papers = current('papers.json')
    verify_fixed_cohort(ROOT, DATA, papers)
    oldpapers = json.loads(gzip.decompress(files['catalog-snapshot/cellchat-prior-papers.json.gz']))
    oldreviews = json.loads(gzip.decompress(files['catalog-snapshot/cellchat-prior-reviews.json.gz']))
    reviews = json.loads(files['current-source/tools/literature/reviewed-uses.json'])
    assert len(oldreviews['reviews']) == 1018 and len(reviews['reviews']) == 1024
    assert reviews['reviews'][:1018] == oldreviews['reviews']
    changed = []
    for old, new in zip(oldpapers, papers, strict=True):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [r for r in old['tools'] if r['tool_id'] != 'cellchat'] == [r for r in new['tools'] if r['tool_id'] != 'cellchat']
        assert len(old['tools']) == len(new['tools'])
        if old != new:
            changed.append(new['paper_id'])
    assert changed == ['P060', 'P085', 'P090', 'P100', 'P145', 'P167']
    assert sum(len(p['tools']) for p in papers) == 2466
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews['reviews']) == 357
    drafts = json.loads(files['current-source/tools/literature/cellchat-use-reviews.json'])['reviews']
    assert reviews['reviews'][1018:] == [d['review'] for d in drafts]
    by = {p['paper_id']: p for p in papers}
    for d in drafts:
        t = next(t for t in by[d['paper_id']]['tools'] if t['tool_id'] == 'cellchat')
        for field in ['evidence_paragraph_sha256', 'use_summary', 'implementation_scope_note',
                      'version_reported_near_name', 'supporting_evidence']:
            if field in d['review']:
                assert t[field] == d['review'][field]
    roles = current('cellchat-role-coverage.json')
    assert roles['applied'] and roles['unchanged_prior_reviews'] == 1018 and roles['new_reviews'] == 6
    assert roles['required_figure_evidence']['figure_id'] == 'Fig14'
    review_delta = current('cellchat-reviews-shared-source-compatibility.json')['paths'][0]
    assert reverse_delta(files['current-source/tools/literature/reviewed-uses.json'], review_delta) == gzip.decompress(files['catalog-snapshot/cellchat-prior-reviews.json.gz'])
    old = {r['id']: r for r in json.loads(files['catalog-before/tool-inventory.json'])}
    new = {r['id']: r for r in current('tool-inventory.json')}
    assert set(old) == set(new) and len(new) == 400
    assert all(old[k] == new[k] for k in old if k != 'cellchat')
    assert old['cellchat']['implementation_status'] == 'missing'
    row = new['cellchat']
    assert row['implementation_status'] == 'upstream-adapter-validated' and row['root_command'] == 'cellchat-tools'
    assert row['paper_count'] == row['source_roles_reviewed'] == 6
    assert not row['whole_package_implemented'] and not row['whole_paper_reproduced'] and not row['whole_goal_complete']
    assert new['cellranger']['implementation_status'] == 'excluded-by-user' and new['spyrrow']['implementation_status'] == 'missing'
    counts = dict(Counter(r['implementation_status'] for r in new.values()))
    assert counts == current('summary.json')['implementation_status_counts']
    assert (counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (19, 75, 3, 247)
    dispatch = current('cellchat-dispatch-shared-source-compatibility.json')['paths'][0]
    assert len(dispatch['edits']) == 2
    assert reverse_delta(files['current-source/src/main/java/org/jlinalg/cli/JLinAlgCli.java'], dispatch) == files[BASE+'catalog-dispatch-bridge-1/JLinAlgCli.before.java']
    routed = document('catalog-root-validation-1/COMPLETED.json')
    assert routed['status'] == 'PASS48_COMPLETE_CELLCHAT_ROOT_BUNDLES30_SKIPS6_PREDECESSOR_FLOWS'
    assert len(routed['runs']) == 6
    for r in routed['runs']:
        prefix = BASE+'catalog-root-validation-1/'+f"javac{r['compiler']}-"+r['platform']+'/'
        result = json.loads(files[prefix+'COMPLETED.json'])
        assert (result['complete_bundles'], result['dependency_skips'], result['predecessor_full_flows']) == (8, 5, 1)
        assert len(result['records']) == r['invocations'] == 31
        assert all(v['exit_code'] == v['expected_exit'] for v in result['records'])
        for v in result['records']:
            assert json.loads(files[prefix+v['id']+'/process.json']) == v
            if v['id'].startswith('skip-'):
                assert b'SKIP3' in files[prefix+v['id']+'/stderr']
    for co in [21, 25]:
        published = ROOT/f'docs/literature/cellchat-catalog-development/cellchat-root-javac{co}.jar'
        parent = ROOT/f'docs/literature/cistarget-catalog-development/cistarget-root-javac{co}.jar'
        with zipfile.ZipFile(published) as z, zipfile.ZipFile(parent) as old:
            assert all(z.read(n) == old.read(n) for n in old.namelist() if n != 'org/jlinalg/cli/JLinAlgCli.class')
            for family in ['inference', 'legacy', 'network', 'strength', 'workflow']:
                with zipfile.ZipFile(ROOT/f'docs/literature/cellchat-{family}-development/cellchat-{family}-javac{co}.jar') as child:
                    assert all(z.read(n) == child.read(n) for n in child.namelist() if n.endswith('.class') or n.startswith('org/jlinalg/omics/cellchat-'))
            with zipfile.ZipFile(ROOT/f'docs/literature/cellchat-development/cellchat-rna-javac{co}.jar') as child:
                assert all(z.read(n) == child.read(n) for n in child.namelist() if n.endswith('.class'))
            assert all(int.from_bytes(z.read(n)[6:8], 'big') == 61 for n in z.namelist() if n.endswith('.class'))
    assert document('catalog-render-2/COMPLETED.json')['unchanged_unrelated_tool_objects'] == 399
    audit = document('catalog-source-audit-1/COMPLETED.json')
    assert (audit['source_records'], audit['observations_verified'], audit['reviewed_uses_verified'], audit['supporting_pointers_verified']) == (250, 2466, 1024, 357)
    gates = document('catalog-shared-gates-2/COMPLETED.json')
    assert gates['status'] == 'PASS40_UNCHANGED_PREDECESSOR_AND_CELLCHAT_SCIENTIFIC_GATES' and len(gates['checks']) == 40
    assert not gates['scientific_verifiers_changed'] and not gates['scientific_source_allowlist_changed']
    ui = document('catalog-ui-1/COMPLETED.json')
    assert ui['status'] == 'PASS_UNCHANGED299_GENERAL_AND12_CELLCHAT_BROWSER_CHECKS'
    assert document('catalog-visual-review-1/COMPLETED.json')['status'] == 'PASS_CELLCHAT_BROWSER_SCREENSHOT_REVIEW'
    warning = files['catalog-snapshot/WARNINGS.md'].decode('utf8')
    assert warning.startswith('## CellChat: warnings and required actions')
    for marker in ['SKIP3', 'FAIL2', 'future.seed', 'random_state', 'unresolved', 'Spyrrow']:
        assert marker in warning
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    if check_manifest:
        declared = {}
        for line in (DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines():
            digest, name = line.split('  ', 1)
            assert name not in declared and pin(DATA/name)['sha256'] == digest
            declared[name] = digest
        assert set(declared) == {p.name for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    if emit:
        print('PASS CellChat integration:250 papers,94 new/97 including existing,247 missing;48 routed bundles,40 science gates,311 browser checks; whole goal remains active')
    return proof


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable catalog verification')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--folder', type=Path)
    a = p.parse_args()
    verify(folder=a.folder)
