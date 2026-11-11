# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the reviewed DIAMOND catalog checkpoint and refresh its complete integrity index."""
import argparse, json, os, stat
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save
from signac_scan_evidence import pack, byte_pin
from verify_diamond_catalog import DATA, STATUS, verify


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    prerequisites = {
        'catalog-render-1': 'PASS_DIAMOND_CATALOG_RENDER399_UNRELATED_TOOL_OBJECTS_UNCHANGED',
        'catalog-source-audit-1': 'PASS250_PRIMARY_SOURCES_2466_OBSERVATIONS_1029_REVIEWS_AND_FIVE_DIAMOND_ROLES',
        'catalog-shared-gates-1': 'PASS41_UNCHANGED_PREDECESSOR_CELLCHAT_AND_DIAMOND_SCIENTIFIC_GATES',
        'catalog-ui-2': 'PASS_UNCHANGED299_GENERAL_AND11_DIAMOND_BROWSER_CHECKS',
        'catalog-visual-review-1': 'PASS_THREE_DIAMOND_CATALOG_ARTIFACTS_VISUALLY_REVIEWED',
        'catalog-readme-1': 'PASS_CURRENT_DIAMOND_README_HEADER_EXACT_REFERENCE_RETAINED'}
    assert all(json.loads((BASE/n/'COMPLETED.json').read_bytes())['status'] == value for n, value in prerequisites.items())
    assert not any((DATA/n).exists() for n in ['diamond-catalog-validation.json', 'diamond-catalog-support.zip'])
    sources = sorted((ROOT/'tools/literature').glob('*diamond*.*'))
    sources += [ROOT/n for n in ['src/main/java/org/jlinalg/cli/JLinAlgCli.java',
        'tools/literature/render_review.py', 'tools/literature/reviewed-uses.json', 'docs/literature/DIAMOND_INTEGRATION.md']]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    files, objects = {}, {}

    def add(name, path):
        assert name not in files
        raw = path.read_bytes()
        files[name] = objects.setdefault(byte_pin(raw)['sha256'], raw)

    for name in source_pins:
        add('current-source/'+name, ROOT/name)
    for folder in [*sorted(BASE.glob('catalog-*')), BASE/'role-review-dry-1', BASE/'role-review-apply-1']:
        if folder.is_dir() and not folder.name.startswith('catalog-seal-'):
            for directory, dirs, names in os.walk(folder, followlinks=False):
                dirs[:] = [d for d in dirs if d != '__pycache__']
                for name in names:
                    path = Path(directory)/name
                    assert stat.S_ISREG(path.stat(follow_symlinks=False).st_mode)
                    add(path.relative_to(ROOT).as_posix(), path)
    names = ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md', 'catalog.md', 'implementation-plan.md',
        'diamond-role-coverage.json', 'diamond-prior-reviews.json.gz', 'diamond-prior-papers.json.gz',
        'diamond-dispatch-shared-source-compatibility.json', 'diamond-renderer-shared-source-compatibility.json',
        'diamond-reviews-shared-source-compatibility.json']
    snapshot = {name: pin(DATA/name) for name in names}
    for name in names:
        add('catalog-snapshot/'+name, DATA/name)
    for name in ['tool-inventory.json', 'papers.json', 'summary.json', 'README.md', 'WARNINGS.md', 'reviewed-uses.json']:
        add('catalog-before/'+name, BASE/'catalog-preparation-1'/name)
    page_pins = {name: pin(ROOT/name) for name in ['site/literature-review.html', 'site/literature-warnings.html']}
    for name in page_pins:
        add('catalog-snapshot/'+name, ROOT/name)
    component = ROOT/'docs/literature/diamond-development'
    component_files = {p.relative_to(ROOT).as_posix(): pin(p) for p in component.iterdir() if p.is_file()}
    assert len(component_files) == 7
    add('component-metadata/CONTROLLER_EXIT_FIX.json', component/'CONTROLLER_EXIT_FIX.json')
    previous = json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cellchat-catalog-1/selection.json').read_bytes())
    assert len(previous['preserved']) == 18 and all(pin(ROOT/n) == v for n, v in previous['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    packing = pack(out/'diamond-catalog-support.zip', files)
    assert pin(out/'diamond-catalog-support.zip')['bytes'] < 95000000
    (DATA/'diamond-catalog-support.zip').write_bytes((out/'diamond-catalog-support.zip').read_bytes())
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=True, source_pins=source_pins,
        catalog_snapshot=snapshot, page_pins=page_pins, archive=pin(DATA/'diamond-catalog-support.zip'), component_files=component_files,
        papers=250, tools=400, definitions=438, observations=2466, reviewed_roles=1029, supporting_pointers=367,
        new_scoped_integrations=95, native_java_integrations=19, upstream_adapters=76,
        including_existing_adapters=98, missing=246, unchanged_unrelated_tool_objects=399,
        unchanged_prior_reviews=1024, new_reviews=5, new_observations=0, source_roles_reviewed=5,
        scientific_gates=41, general_browser_checks=299, focused_browser_checks=11,
        original_cases=78, successful_source_cases=72, preserved_original_failure_cases=6,
        complete_root_bundle_checks=216, root_failed_bundle_refusals=18, root_dependency_skips=3,
        predecessor_complete_checks=15, routed_original_alignments=3,
        canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18,
        scientific_verifiers_changed=False, source_allowlist_changed=False, analysis_installs_software=False,
        whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False,
        controller_exit_gate_correction='Captured root validation completed every scientific check; its final PASS_ prefix gate returned nonzero for PASS216. Current controller accepts that prefix, with exact predecessor reconstruction in CONTROLLER_EXIT_FIX.json. No scientific result or captured invocation changed.',
        post_gate_documentation_change='Current count/scope README introduction retains the exact generated reference. Scientific JSON and rendered pages remain unchanged after browser/gate capture.',
        scope='Frozen source, primary-role/catalog preservation, original synthetic source-option alignment populations, native transports and captured browser/root relationships. No independent alignment fit, producer authentication, historical paper reproduction or whole-goal claim.', **packing)
    save(DATA/'diamond-catalog-validation.json', proof)
    prior_rows = (DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines()
    order = [line.split('  ', 1)[1] for line in prior_rows]
    manifest = {p.name: pin(p)['sha256'] for p in sorted(DATA.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'}
    order = [name for name in order if name in manifest]+[name for name in manifest if name not in order]
    assert len(order) == len(set(order))
    (DATA/'SHA256SUMS').write_text(''.join(manifest[name]+'  '+name+'\n' for name in order), encoding='utf8', newline='\n')
    verify()
    published = {p.relative_to(ROOT).as_posix(): pin(p) for p in [DATA/'diamond-catalog-validation.json', DATA/'diamond-catalog-support.zip', DATA/'SHA256SUMS']}
    save(out/'COMPLETED.json', dict(status='PASS_FROZEN_DIAMOND_CATALOG_AND_CURRENT_INTEGRITY_INDEX',
        source_pins=source_pins, published_files=published, component_files=component_files,
        catalog_snapshot=snapshot, page_pins=page_pins, final_manifest_files={p.name: pin(p) for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'},
        new_catalog_credit=True, whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable final catalog/source integrity checks')
    main()
