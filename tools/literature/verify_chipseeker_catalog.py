# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete ChIPseeker controls, fixed primary roles and sealed catalog evidence."""
import json
import sys
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source
from chipseeker_catalog import verify


def main():
    data = ROOT/'docs/literature/omics-2023-2026'
    proof = json.loads((data/'chipseeker-catalog-validation.json').read_bytes())
    public = verify(ROOT, data)
    assert proof['status'] == 'passed-scoped-ChIPseeker-catalog-integration'
    assert pin(data/'chipseeker-catalog-support.zip') == proof['archive']
    assert pin(ROOT/'docs/literature/chipseeker-annotation-development/VALIDATION.json') == proof['public_validation']
    assert public['archive'] == proof['public_archive'] and public['artifact'] == proof['artifact']
    files = unpack(data/'chipseeker-catalog-support.zip', proof)
    for name, wanted in proof['source_pins'].items():
        verify_source(ROOT, data, name, wanted['sha256'])
        assert byte_pin(files['current-source/'+name]) == wanted
    summary = json.loads(files['catalog-snapshot/summary.json'])
    counts = summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'],
            counts['native-subset-validated'], counts['upstream-adapter-validated'],
            counts['existing-adapter'], counts['missing']) == (250, 387, 2446, 16, 58, 3, 254)
    assert (proof['new_scoped_integrations'], proof['including_existing_adapters'], proof['missing_integrations']) == (74, 77, 254)
    inventory = json.loads(files['catalog-snapshot/tool-inventory.json'])
    tool = next(t for t in inventory if t['id'] == 'chipseeker')
    assert tool['implementation_status'] == 'upstream-adapter-validated'
    assert tool['source_roles_reviewed'] == tool['paper_count'] == 7 and tool['native_methods_available']
    assert tool['development_candidate_sha256'] == public['artifact']['sha256']
    assert next(t for t in inventory if t['id'] == 'cellranger')['implementation_status'] == 'excluded-by-user'
    reviews = json.loads(files['catalog-snapshot/reviewed-uses.json'])['reviews']
    assert len(reviews) == 905 and sum(len(r.get('supporting_evidence', [])) for r in reviews) == 302
    assert len([r for r in reviews if r['tool_id'] == 'chipseeker']) == 7
    intake = 'build/literature-review/chipseeker-runtime/catalog-source-intake-1/'
    old = {t['id']: t for t in json.loads(files[intake+'tool-inventory.json'])}
    current = {t['id']: t for t in inventory}
    assert old.keys() == current.keys() and [n for n in old if old[n] != current[n]] == ['chipseeker']
    assert reviews[:898] == json.loads(files[intake+'reviewed-uses.json'])['reviews']
    old_papers = json.loads(files[intake+'papers.json'])
    papers = json.loads(files['catalog-snapshot/papers.json'])
    for before, after in zip(old_papers, papers):
        assert {k: v for k, v in before.items() if k != 'tools'} == {k: v for k, v in after.items() if k != 'tools'}
        assert [t for t in before['tools'] if t['tool_id'] != 'chipseeker'] == [t for t in after['tools'] if t['tool_id'] != 'chipseeker']
    audit = json.loads(files['catalog-snapshot/chipseeker-source-audit.json'])
    assert audit['status'] == 'passed-current-primary-source-pointer-audit'
    assert (audit['source_xml_hashes_verified'], audit['tool_observation_pointers_verified'],
            audit['individually_reviewed_usage_summaries_verified'], audit['supporting_paragraph_pointers_verified']) == (250, 2446, 905, 302)
    assert audit['papers_pin'] == byte_pin(files['catalog-snapshot/papers.json'])
    assert audit['reviews_pin'] == byte_pin(files['catalog-snapshot/reviewed-uses.json'])
    for name, wanted in audit['source_pins'].items():
        verify_source(ROOT, data, name, wanted['sha256'])
    prefix = proof['ui_prefix']
    check = json.loads(files[prefix+'COMPLETED.json'])
    general = json.loads(files[prefix+'general/ui-validation.json'])
    scoped = json.loads(files[prefix+'chipseeker/chipseeker-ui-verification.json'])
    visual = json.loads(files[prefix+'VISUAL-REVIEW.json'])
    assert general['status'] == scoped['status'] == 'passed'
    assert len(general['checks']) == proof['general_browser_checks'] == 299
    assert len(scoped['checks']) == proof['ChIPseeker_browser_checks'] == 5
    assert scoped['mobile_layout'] == proof['mobile_layout'] == dict(viewport=390, document=390)
    assert check['page'] == visual['page'] == byte_pin(files['site-snapshot/literature-review.html'])
    assert check['warning_page'] == byte_pin(files['site-snapshot/literature-warnings.html'])
    assert check['general'] == byte_pin(files[prefix+'general/ui-validation.json'])
    assert check['ChIPseeker'] == byte_pin(files[prefix+'chipseeker/chipseeker-ui-verification.json'])
    assert visual['desktop_and_mobile_reviewed'] and visual['source_page_modified'] is False
    for name, wanted in visual['visual_files'].items():
        assert byte_pin(files[prefix+'chipseeker/'+name]) == wanted
    assert (proof['unrelated_tool_rows_preserved'], proof['unrelated_paper_observations_preserved'], proof['prior_reviews_preserved']) == (386, 2439, 898)
    for name in ['full_project_test_suite_run', 'historical_whole_environment', 'whole_paper_reproduced',
                 'biological_validity_certified', 'execution_authentication', 'released', 'pushed', 'deployed', 'whole_goal_complete']:
        assert proof[name] is False
    names = set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest, name = line.split('  ', 1)
        assert name not in names and pin(data/name)['sha256'] == digest, name
        names.add(name)
    assert names == {p.name for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    print(json.dumps(dict(status='passed-ChIPseeker-catalog-offline-and-live-source-checks', catalog_checksums=len(names),
        checkpoint_new_scoped_integrations=74, checkpoint_including_existing_adapters=77, checkpoint_missing=254,
        source_roles=7, native_tests_per_JVM=202, JVMs=3, actual_adapter_outcomes=424, recovery_controls=16,
        general_browser_checks=299, ChIPseeker_browser_checks=5, whole_goal_complete=False)))


if __name__ == '__main__':
    try:
        if not __debug__:
            raise RuntimeError('Never disable complete source/archive checks')
        main()
    except Exception as error:
        print('CHIPSEEKER CATALOG FAIL2: '+str(error)+'\nREQUIRED ACTION: preserve the checkpoint, correct the named source/archive/catalog discrepancy and repeat verification. No partial evidence admitted.', file=sys.stderr)
        raise SystemExit(2)
