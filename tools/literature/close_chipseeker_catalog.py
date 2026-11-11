# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal one ChIPseeker catalog integration, preserving every unrelated row and prior review."""
import argparse
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack
from chipseeker_catalog import verify
from chipseeker_catalog_support import refresh_checksums


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ui', type=Path, required=True)
    args = parser.parse_args()
    data = ROOT/'docs/literature/omics-2023-2026'
    base = ROOT/'build/literature-review/chipseeker-runtime'
    ui = args.ui.resolve()
    assert ui.is_relative_to(base)
    target = data/'chipseeker-catalog-validation.json'
    archive = data/'chipseeker-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    public = verify(ROOT, data)
    summary = json.loads((data/'summary.json').read_bytes())
    counts = summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations'],
            counts['native-subset-validated'], counts['upstream-adapter-validated'],
            counts['existing-adapter'], counts['missing']) == (250, 387, 2446, 16, 58, 3, 254)
    intake = base/'catalog-source-intake-1'
    old = {t['id']: t for t in json.loads((intake/'tool-inventory.json').read_bytes())}
    current = {t['id']: t for t in json.loads((data/'tool-inventory.json').read_bytes())}
    assert old.keys() == current.keys() and len(current) == 387
    assert [n for n in old if old[n] != current[n]] == ['chipseeker']
    assert old['chipseeker']['implementation_status'] == 'missing'
    assert current['chipseeker']['implementation_status'] == 'upstream-adapter-validated'
    old_papers = json.loads((intake/'papers.json').read_bytes())
    papers = json.loads((data/'papers.json').read_bytes())
    for before, after in zip(old_papers, papers):
        assert {k: v for k, v in before.items() if k != 'tools'} == {k: v for k, v in after.items() if k != 'tools'}
        assert [t for t in before['tools'] if t['tool_id'] != 'chipseeker'] == [t for t in after['tools'] if t['tool_id'] != 'chipseeker']
    old_reviews = json.loads((intake/'reviewed-uses.json').read_bytes())
    assert gzip.decompress((data/'chipseeker-prior-reviews.json.gz').read_bytes()) == (intake/'reviewed-uses.json').read_bytes()
    reviews = json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())
    assert reviews['reviews'][:898] == old_reviews['reviews'] and len(reviews['reviews']) == 905
    source_audit = json.loads((data/'chipseeker-source-audit.json').read_bytes())
    assert source_audit['status'] == 'passed-current-primary-source-pointer-audit'
    assert (source_audit['source_xml_hashes_verified'], source_audit['tool_observation_pointers_verified'],
            source_audit['individually_reviewed_usage_summaries_verified'], source_audit['supporting_paragraph_pointers_verified']) == (250, 2446, 905, 302)
    assert source_audit['papers_pin'] == pin(data/'papers.json')
    assert source_audit['reviews_pin'] == pin(ROOT/'tools/literature/reviewed-uses.json')
    check = json.loads((ui/'COMPLETED.json').read_bytes())
    general = json.loads((ui/'general/ui-validation.json').read_bytes())
    scoped = json.loads((ui/'chipseeker/chipseeker-ui-verification.json').read_bytes())
    visual = json.loads((ui/'VISUAL-REVIEW.json').read_bytes())
    assert check['page'] == visual['page'] == pin(ROOT/'site/literature-review.html')
    assert check['warning_page'] == pin(ROOT/'site/literature-warnings.html')
    assert check['general'] == pin(ui/'general/ui-validation.json')
    assert check['ChIPseeker'] == pin(ui/'chipseeker/chipseeker-ui-verification.json')
    assert general['status'] == scoped['status'] == 'passed' and len(general['checks']) == 299 and len(scoped['checks']) == 5
    assert visual['desktop_and_mobile_reviewed'] and visual['source_page_modified'] is False
    assert scoped['mobile_layout'] == dict(viewport=390, document=390)
    source_names = ['chipseeker_catalog.py', 'wire_chipseeker_catalog.py', 'close_chipseeker_catalog.py',
        'verify_chipseeker_catalog.py', 'check_chipseeker_review_ui.cjs', 'validate_chipseeker_catalog_ui.py',
        'audit_current_omics_catalog.py', 'capture_chipseeker_catalog_visuals.py', 'render_review.py', 'check_review_ui.cjs',
        'meme_discovery_current_catalog.py', 'meme_discovery_provider_compatibility.py',
        'derive_meme_discovery_current_provider.py', 'verify_meme_discovery_current_catalog.py',
        'validate_meme_review_extension.py']
    paths = [ROOT/'tools/literature'/n for n in source_names]
    paths += sorted(data.glob('chipseeker-*-shared-source-compatibility.json'))
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    files = {'current-source/'+n: (ROOT/n).read_bytes() for n in pins}
    for name in ['summary.json', 'tool-inventory.json', 'papers.json', 'catalog.md', 'implementation-plan.md',
                 'WARNINGS.md', 'chipseeker-role-coverage.json', 'chipseeker-source-audit.json']:
        files['catalog-snapshot/'+name] = (data/name).read_bytes()
    files['catalog-snapshot/reviewed-uses.json'] = (ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    for name in ['literature-review.html', 'literature-warnings.html']:
        files['site-snapshot/'+name] = (ROOT/'site'/name).read_bytes()
    # Source summaries/hashes are public; private complete article paragraphs and
    # extraction caches are intentionally excluded from the deliverable.
    folders = [intake, ui, base/'meme-provider-intake-1', base/'meme-provider-refusals-1',
        base/'meme-provider-refusals-2', base/'catalog-render-1']
    for folder in sorted(base.glob('catalog-ui-*')):
        if folder != ui:
            folders.append(folder)
    for folder in folders:
        for path in folder.rglob('*'):
            if path.is_file():
                files[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    layout = pack(archive, files)
    proof = dict(schema_version=1, status='passed-scoped-ChIPseeker-catalog-integration',
        public_validation=pin(ROOT/'docs/literature/chipseeker-annotation-development/VALIDATION.json'),
        public_archive=public['archive'], artifact=public['artifact'], source_pins=pins, archive=pin(archive), **layout,
        papers=250, distinct_named_tools=387, paper_tool_observations=2446, new_scoped_integrations=74,
        including_existing_adapters=77, native_integrations=16, upstream_integrations=58, missing_integrations=254,
        method_overlap_tools=47, reviewed_uses=905, supporting_pointers=302, source_roles_reviewed=7,
        unrelated_tool_rows_preserved=386, unrelated_paper_observations_preserved=2439, prior_reviews_preserved=898,
        general_browser_checks=299, ChIPseeker_browser_checks=5, ui_prefix=ui.relative_to(ROOT).as_posix()+'/',
        mobile_layout=scoped['mobile_layout'], visual_desktop_and_mobile_review=True,
        native_tests_per_JVM=202, JVMs=3, actual_adapter_outcomes=424, recovery_controls=16,
        original_annotation_references=224, original_annotation_returns=175, original_annotation_errors=49,
        Cell_Ranger_excluded=True, semantic_paper_wide_completeness_review_open=True,
        full_project_test_suite_run=False, historical_whole_environment=False, whole_paper_reproduced=False,
        biological_validity_certified=False, execution_authentication=False, released=False, pushed=False,
        deployed=False, whole_goal_complete=False)
    assert all(pin(ROOT/n) == value for n, value in pins.items())
    save(target, proof)
    refresh_checksums(data)
    print(json.dumps({k: proof[k] for k in ['status', 'new_scoped_integrations', 'including_existing_adapters', 'missing_integrations', 'archive']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable catalog closure checks')
    main()
