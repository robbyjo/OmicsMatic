# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze GENESIS source/role/browser evidence and the scoped one-tool catalog admission."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source
from verify_genesis_catalog import DATA, STATUS, verify


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    base = ROOT / 'build/literature-review/genesis-runtime'; assert not out.exists() and out.is_relative_to(base); out.mkdir(parents=True)
    required = {
        'catalog-render-2': 'PASS_GENESIS_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED',
        'catalog-source-audit-1': 'PASS250_PRIMARY_SOURCES_2475_OBSERVATIONS_1038_REVIEWS_AND_NINE_NEW_PACKAGE_ROLES',
        'catalog-shared-gates-1': 'PASS51_GENESIS_AND_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',
        'catalog-ui-1': 'PASS299_UNCHANGED_GENERAL_AND18_GENESIS_BROWSER_CHECKS',
        'catalog-visual-review-1': 'PASS_TWO_GENESIS_BROWSER_ARTIFACTS_VISUALLY_REVIEWED',
        'catalog-metadata-idempotency-1': 'PASS_GENESIS_CURRENT_README_AND_ADDITIONAL_WARNING_METADATA_ONLY',
    }
    assert all(json.loads((base / name / 'COMPLETED.json').read_bytes())['status'] == status for name, status in required.items())
    for name in ['tools-replay-windows21-1', 'tools-replay-windows25-1', 'tools-replay-linux21-1']:
        assert json.loads((base / name / 'COMPLETED.json').read_bytes())['status'] == 'PASS11_GENESIS_TOOLS_SAVED_SCIENTIFIC_REPLAYS_WITHOUT_R'
    assert not any((DATA / name).exists() for name in ['genesis-catalog-validation.json', 'genesis-catalog-support.zip'])
    metadata_sources = ['tools/literature/' + name for name in ['audit_genesis_catalog.py', 'run_genesis_catalog_checks.py', 'wire_genesis_catalog.py',
        'render_genesis_catalog.py', 'refresh_genesis_catalog_metadata.py', 'validate_genesis_catalog_ui.py', 'check_genesis_catalog_ui.cjs', 'verify_genesis_catalog.py', 'seal_genesis_catalog.py']]
    attrs = ROOT / '.gitattributes'; before = attrs.read_bytes()
    after = before + ('\n# Preserve exact GENESIS catalog metadata verifier and browser source bytes.\n' + ''.join(name + ' -text whitespace=cr-at-eol\n' for name in metadata_sources)).encode('utf8')
    row = inverse(before, after); row['path'] = '.gitattributes'; assert reverse_delta(after, row) == before
    descriptor = DATA / 'genesis-catalog-sourcebytes-shared-source-compatibility.json'; assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='genesis', scope='Append-only byte-preservation attributes for catalog/browser metadata scripts. No scientific-source or allowlist change.', paths=[row]))
    (out / 'gitattributes.before').write_bytes(before); attrs.write_bytes(after); verify_source(ROOT, DATA, '.gitattributes', row['before_sha256'])
    sources = [p for p in sorted((ROOT / 'tools/literature').glob('*genesis*.*')) if p.suffix in ['.py', '.json', '.cjs']]
    sources += [ROOT / 'tools/literature' / name for name in ['render_review.py', 'reviewed-uses.json', 'check_cistarget_general_review_ui.cjs', 'check_snprelate_general_review_ui.cjs']]
    sources += [ROOT / name for name in ['.gitattributes', 'src/main/java/org/jlinalg/cli/JLinAlgCli.java', 'docs/literature/GENESIS_INTEGRATION.md', 'docs/literature/GENESIS_NUMERICAL_FINDINGS.md']]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}; files = {}

    def add(name, path):
        assert name not in files and path.is_file() and not path.is_symlink(); files[name] = path.read_bytes()

    for name in source_pins:
        add('current-source/' + name, ROOT / name)
    folders = [*required, 'catalog-render-1', 'catalog-role-apply-1', 'catalog-wire-1', 'catalog-findings-wire-1', 'catalog-helper-derivations-1',
        'tools-replay-windows21-1', 'tools-replay-windows25-1', 'tools-replay-linux21-1', 'cross-option-controls-1', 'cross-option-refusal-cli-1']
    for name in dict.fromkeys(folders):
        for path in sorted((base / name).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                assert path.suffix.lower() not in ['.exe', '.dll', '.so', '.tar', '.gz', '.xz', '.pdf'], ('Do not distribute runtimes or article prose', str(path))
                add(path.relative_to(ROOT).as_posix(), path)
    for name in ['collect_cross_option_controls_1.py', 'test_cross_option_refusal_cli_1.py', 'summarize_cross_option_findings_1.py']:
        add((base / name).relative_to(ROOT).as_posix(), base / name)
    names = ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md', 'catalog.md', 'implementation-plan.md']
    names += [p.name for p in sorted(DATA.glob('genesis-*.json'))]
    names += ['genesis-prior-reviews.json.gz', 'genesis-prior-papers.json.gz']; assert len(names) == len(set(names))
    snapshot = {name: pin(DATA / name) for name in names}
    for name in names:
        add('catalog-snapshot/' + name, DATA / name)
    for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        add('catalog-before/' + name, base / 'catalog-role-apply-1' / name)
    pages = {name: pin(ROOT / name) for name in ['site/literature-review.html', 'site/literature-warnings.html']}
    for name in pages:
        add('pages/' + name, ROOT / name)
    component = ROOT / 'docs/literature/genesis-tools-development'; component_files = {p.relative_to(ROOT).as_posix(): pin(p) for p in component.iterdir() if p.is_file()}
    packed = pack(out / 'genesis-catalog-support.zip', files); assert pin(out / 'genesis-catalog-support.zip')['bytes'] < 95_000_000
    (DATA / 'genesis-catalog-support.zip').write_bytes((out / 'genesis-catalog-support.zip').read_bytes())
    previous = json.loads((ROOT / 'build/literature-review/push-checkpoint-2026-11-10-genesis-extended-1/selection.json').read_bytes())
    assert len(previous['preserved']) == 18 and all(pin(ROOT / name) == value for name, value in previous['preserved'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=True, new_tool_id='genesis', new_tool_integration_count=1, whole_package_implemented=False,
        whole_paper_reproduced=False, whole_goal_complete=False, source_pins=source_pins, catalog_snapshot=snapshot, page_pins=pages, component_files=component_files,
        archive=pin(DATA / 'genesis-catalog-support.zip'), papers=250, definitions=440, observed_tools=402, observations=2475, reviewed_roles=1038, supporting_pointers=369,
        new_scoped_integrations=97, native_java_integrations=19, new_upstream_adapters=78, including_existing_adapters=100, missing=246, unchanged_other_tool_objects=401,
        revised_GENESIS_scope_notes=5, primary_usage_summaries_changed=False, scientific_gates=51, general_browser_checks=299, focused_browser_checks=18,
        current_branch_controls=61, positive_branch_components=53, branch_scientific_refusals=5, branch_original_errors=3,
        root_original_attempts=13, root_admitted_analyses=11, root_expected_refusals=2, root_saved_replays=33, root_helps=12, root_missing_runtime_skip=1,
        additional_cross_option_controls=12, additional_native_successes=10, additional_source_NA_refusals=2, additional_actual_root_refusals=2, public_scientific_replays_without_R=33,
        canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18, scientific_verifiers_changed=False, scientific_source_allowlist_changed=False,
        analysis_installs_software=False, primary_article_prose_distributed=False, participant_data_distributed=False,
        retained_failures='The first root validator checked the success-worker path for a malformed original PC object that was correctly quarantined; its failed attempt is preserved, completed original executions reused and all33 scientific replays repeated. The first successful catalog render retained the older manual README header; that wrapper failure is preserved and a pure final metadata hook updates only the README prefix/additional warnings. Original true mixed CMP, probabilities above1, malformed PC dimensions and missing GxE estimates remain failures without repair.',
        scope='One scoped original GENESIS2.42.0 project adapter with native partition/rank/input/identity/covariance/model/association contracts. Original estimators execute upstream; saved checks do not refit models, authenticate producers, reproduce historical papers or establish biological/production-scale validity. Full250-paper semantic review and implementation remain open.', **packed)
    save(DATA / 'genesis-catalog-validation.json', proof)
    old = (DATA / 'SHA256SUMS').read_text(encoding='utf8').splitlines(); order = [r.split('  ', 1)[1] for r in old]
    manifest = {p.name: pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    order = [name for name in order if name in manifest] + [name for name in sorted(manifest) if name not in order]
    assert len(order) == len(set(order)); (DATA / 'SHA256SUMS').write_text(''.join(manifest[name] + '  ' + name + '\n' for name in order), encoding='utf8', newline='\n')
    verify(); save(out / 'COMPLETED.json', dict(status=STATUS, proof=pin(DATA / 'genesis-catalog-validation.json'), archive=proof['archive'], whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source, preserved science, manifest or unrelated-work checks')
    main()
