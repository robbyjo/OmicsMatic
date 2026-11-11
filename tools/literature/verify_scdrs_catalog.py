# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped scDRS catalog admission, six primary roles and preserved predecessor science; keep the full goal open."""
import argparse
from collections import Counter
import gzip
import json
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source, reverse_delta
from verify_scdrs_workflow_evidence import inspect as workflow

DATA = ROOT / 'docs/literature/omics-2023-2026'
PREFIX = 'build/literature-review/scdrs-runtime/'
STATUS = 'PASS_SCDRS_SCOPED_WORKFLOWS_SIX_PRIMARY_ROLES_AND_CATALOG_WITH_GOAL_OPEN'


def verify(check_current=True):
    proof = json.loads((DATA / 'scdrs-catalog-validation.json').read_bytes())
    assert proof['status'] == STATUS and proof['new_catalog_credit'] and proof['new_tool_id'] == 'scdrs'
    assert not proof['whole_goal_complete'] and not proof['whole_package_implemented'] and not proof['whole_paper_reproduced']
    assert not proof['predecessor_scientific_verifiers_changed'] and not proof['scientific_source_allowlist_changed']
    for n, wanted in proof['source_pins'].items(): verify_source(ROOT, DATA, n, wanted['sha256'])
    for n, wanted in proof['component_files'].items(): assert pin(ROOT / n) == wanted
    workflow()
    from verify_mtag_catalog import verify as previous
    previous(False)
    assert pin(DATA / 'scdrs-catalog-support.zip') == proof['archive']
    files = unpack(DATA / 'scdrs-catalog-support.zip', proof)
    for n, wanted in proof['source_pins'].items(): assert byte_pin(files['current-source/' + n]) == wanted
    before = {r['id']: r for r in json.loads(files['catalog-before/tool-inventory.json'])}
    after = {r['id']: r for r in json.loads(files['catalog-snapshot/tool-inventory.json'])}
    assert len(after) == 402 and set(after) == set(before) and before['scdrs']['implementation_status'] == 'missing'
    assert all(after[n] == v for n, v in before.items() if n != 'scdrs')
    tool = after['scdrs']
    assert tool['implementation_status'] == 'upstream-adapter-validated' and tool['source_roles_reviewed'] == tool['paper_count'] == 6
    assert tool['root_command'] == 'scdrs-tools' and tool['papers'] == ['P021', 'P024', 'P055', 'P085', 'P123', 'P169']
    assert tool['supported_source_versions'] == ['1.0.0', '1.0.1', '1.0.2'] and not tool['canonical_adapter_available']
    assert not tool['analysis_installs_software'] and after['cellranger']['implementation_status'] == 'excluded-by-user'
    summary = json.loads(files['catalog-snapshot/summary.json'])
    counts = dict(Counter(r['implementation_status'] for r in after.values()))
    assert counts == summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'], counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (250, 402, 2476, 19, 80, 3, 244)
    prior_reviews = json.loads(gzip.decompress(files['catalog-snapshot/scdrs-prior-reviews.json.gz']))['reviews']
    reviews = json.loads(files['current-source/tools/literature/reviewed-uses.json'])['reviews']
    assert len(prior_reviews) == 1043 and len(reviews) == 1049 and reviews[:1043] == prior_reviews
    assert all(r['tool_id'] == 'scdrs' for r in reviews[1043:]) and len({(r['pmcid'], r['tool_id']) for r in reviews}) == 1049
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews) == 375
    updates = json.loads(files['catalog-snapshot/scdrs-applied-reviews.json'])['updates']
    assert [u['review'] for u in updates] == reviews[1043:]
    papers = json.loads(files['catalog-snapshot/papers.json'])
    old_papers = json.loads(gzip.decompress(files['catalog-snapshot/scdrs-prior-papers.json.gz']))
    assert len(papers) == len(old_papers) == 250 and sum(len(p['tools']) for p in papers) == 2476
    by = {u['paper_id']: u for u in updates}
    for a, b in zip(old_papers, papers, strict=True):
        assert {k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}
        assert [t for t in a['tools'] if t['tool_id'] != 'scdrs'] == [t for t in b['tools'] if t['tool_id'] != 'scdrs']
        if b['paper_id'] in by:
            row = by[b['paper_id']]
            assert next((t for t in a['tools'] if t['tool_id'] == 'scdrs'), None) == row['before_observation']
            assert next(t for t in b['tools'] if t['tool_id'] == 'scdrs') == row['after_observation']
        else: assert a == b
    assert by['P169']['before_observation'] is None and by['P021']['review']['version_reported_near_name'] == '1.0.1'
    coverage = json.loads(files['catalog-snapshot/scdrs-source-role-coverage.json'])
    assert coverage['applied'] and coverage['prior_reviews_preserved'] == 1043 and coverage['added_reviews'] == 6 and coverage['new_observations'] == 1
    registry = json.loads(files['catalog-snapshot/scdrs-registry-source-review.json'])
    from scdrs_registry import BASE_PATTERN, PATTERN
    assert registry['original_pattern'] == BASE_PATTERN and registry['pattern'] == PATTERN and registry['source']['paper_id'] == 'P169'
    assert not registry['original_registry_file_modified'] and not registry['scientific_source_allowlist_modified']
    assert pin(ROOT / 'tools/literature/tool-registry.json') == registry['original_registry_file']
    def obj(name): return json.loads(files[PREFIX + name])
    audit = obj('catalog-source-audit-3/COMPLETED.json')
    assert (audit['papers'], audit['observations'], audit['reviewed_roles'], audit['supporting_pointers']) == (250, 2476, 1049, 375)
    assert len(audit['package_census']) == 20 and audit['new_observations'] == 1 and audit['unchanged_non_SCDRS_observations'] == 2470
    gates = obj('catalog-shared-gates-2/COMPLETED.json')
    assert len(gates['checks']) == 57 and not gates['scientific_verifiers_changed'] and not gates['scientific_source_allowlist_changed']
    ui = obj('catalog-ui-4/COMPLETED.json')
    assert (ui['general_checks'], ui['preserved_genesis_scope_checks'], ui['focused_mtag_checks'], ui['focused_scdrs_checks']) == (299, 18, 11, 12)
    assert ui['all_predecessor_scientific_scope_assertions_unchanged'] and ui['current_count_expectations_updated']
    assert obj('catalog-render-4/COMPLETED.json')['unchanged_other_tool_objects'] == 401
    assert obj('catalog-visual-review-1/COMPLETED.json')['status'] == 'PASS_SCDRS_DESKTOP_AND_MOBILE_BROWSER_ARTIFACTS_VISUALLY_REVIEWED'
    contract = obj('catalog-contracts-2/COMPLETED.json')
    assert contract['status'] == 'PASS_SCDRS_METADATA_IDEMPOTENCY_AND_PUBLISHED_TYPED_REQUEST_THREE_JVMS'
    assert contract['rendered_catalog_unchanged'] and not contract['scientific_analysis_executed']
    root_delta = json.loads(files['catalog-snapshot/scdrs-dispatch-shared-source-compatibility.json'])['paths'][0]
    live = files['current-source/src/main/java/org/jlinalg/cli/JLinAlgCli.java']
    assert len(root_delta['edits']) == 2 and byte_pin(live)['sha256'] == root_delta['after_sha256']
    assert reverse_delta(live, root_delta) == files[PREFIX + 'dispatch-live-1/root.before.java']
    readme = files['catalog-snapshot/README.md']
    for token in [b'99 new scoped integrations', b'102 including three existing adapters', b'244 named tools still missing', b'1,049', b'375']: assert token in readme
    warnings = files['catalog-snapshot/WARNINGS.md']
    assert warnings.startswith(b'## scDRS: warnings and required actions')
    for token in [b'SKIP3', b'FAIL2', b'NO RESULT ADMITTED', b'--allow-install', b'--allow-download', b'Pearson', b'corr_mcp', b'P021', b'200 controls', b'P169', b'Cell Ranger']: assert token in warnings
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    if check_current:
        assert all(pin(DATA / n) == v for n, v in proof['catalog_snapshot'].items())
        assert all(pin(ROOT / n) == v for n, v in proof['page_pins'].items())
        expected = {p.name: pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
        rows = (DATA / 'SHA256SUMS').read_text(encoding='utf8').splitlines()
        actual = {r.split('  ', 1)[1]: r.split('  ', 1)[0] for r in rows}
        assert len(rows) == len(actual) and actual == expected
    return proof, files


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable full source, role, scientific, browser or preservation checks')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archived-catalog-only', action='store_true')
    a = p.parse_args()
    verify(not a.archived_catalog_only)
    print(STATUS, '250 papers;99 new/102 including existing;244 missing;goal open')
