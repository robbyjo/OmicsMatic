# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the complete CellChat catalog/root/source/browser checkpoint and final integrity index."""
import argparse, json, os, stat
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack, byte_pin
from verify_cellchat_catalog import DATA, HOME, STATUS, verify


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    base = ROOT/'build/literature-review/cellchat-runtime'
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(base)
    out.mkdir(parents=True)
    prerequisites = {
        'catalog-root-validation-1': 'PASS48_COMPLETE_CELLCHAT_ROOT_BUNDLES30_SKIPS6_PREDECESSOR_FLOWS',
        'catalog-render-2': 'PASS_CELLCHAT_CATALOG_RENDER399_UNRELATED_TOOL_OBJECTS_UNCHANGED',
        'catalog-source-audit-1': 'PASS250_PRIMARY_SOURCES_2466_OBSERVATIONS_1024_REVIEWS_AND_SIX_CELLCHAT_ROLES',
        'catalog-shared-gates-2': 'PASS40_UNCHANGED_PREDECESSOR_AND_CELLCHAT_SCIENTIFIC_GATES',
        'catalog-ui-1': 'PASS_UNCHANGED299_GENERAL_AND12_CELLCHAT_BROWSER_CHECKS',
        'catalog-readme-1': 'PASS_CURRENT_CELLCHAT_README_HEADER_EXACT_REFERENCE_RETAINED'}
    assert all(json.loads((base/n/'COMPLETED.json').read_bytes())['status'] == value for n, value in prerequisites.items())
    destinations = [DATA/'cellchat-catalog-validation.json', DATA/'cellchat-catalog-support.zip',
        HOME/'VALIDATION.json', HOME/'controls.zip']
    assert not any(p.exists() for p in destinations)
    source_paths = [*sorted((ROOT/'tools/literature').glob('*cellchat*.*')),
        ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java', ROOT/'src/main/java/org/jlinalg/cli/CellChatToolsCli.java',
        ROOT/'tools/literature/render_review.py', ROOT/'tools/literature/reviewed-uses.json',
        ROOT/'docs/literature/CELLCHAT_INTEGRATION.md']
    assert all(p.is_file() for p in source_paths)
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in source_paths}
    files, objects = {}, {}

    def add(name, path):
        assert name not in files
        raw = path.read_bytes()
        files[name] = objects.setdefault(byte_pin(raw)['sha256'], raw)

    for name in source_pins:
        add('current-source/'+name, ROOT/name)

    def tree(root):
        for folder, dirs, names in os.walk(root, followlinks=False):
            dirs[:] = [d for d in dirs if d != '__pycache__']
            for name in names:
                p = Path(folder)/name
                assert stat.S_ISREG(p.stat(follow_symlinks=False).st_mode)
                add(p.relative_to(ROOT).as_posix(), p)

    for directory in sorted(base.glob('catalog-*')):
        if directory.is_dir() and not directory.name.startswith('catalog-seal-'):
            tree(directory)
    snapshot_names = ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md',
        'cellchat-role-coverage.json', 'cellchat-prior-reviews.json.gz', 'cellchat-prior-papers.json.gz',
        'cellchat-dispatch-shared-source-compatibility.json', 'cellchat-renderer-shared-source-compatibility.json',
        'cellchat-reviews-shared-source-compatibility.json']
    catalog_snapshot = {name: pin(DATA/name) for name in snapshot_names}
    for name in snapshot_names:
        add('catalog-snapshot/'+name, DATA/name)
    for name in ['tool-inventory.json', 'papers.json', 'summary.json', 'README.md', 'WARNINGS.md', 'reviewed-uses.json']:
        add('catalog-before/'+name, base/'catalog-preparation-1'/name)
    pages = {name: pin(ROOT/name) for name in ['site/literature-review.html', 'site/literature-warnings.html']}
    for name in pages:
        add('catalog-snapshot/'+name, ROOT/name)
    published = {p.relative_to(ROOT).as_posix(): pin(p) for p in HOME.glob('*.jar')}
    assert len(published) == 2
    frozen = {}
    for folder in ['cellchat-development', 'cellchat-projection-development', 'cellchat-legacy-development',
        'cellchat-inference-development', 'cellchat-workflow-development', 'cellchat-strength-development',
        'cellchat-network-development']:
        for p in (ROOT/'docs/literature'/folder).rglob('*'):
            if p.is_file():
                frozen[p.relative_to(ROOT).as_posix()] = pin(p)
    previous = json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cellchat-network-1/selection.json').read_bytes())
    assert len(previous['preserved']) == 18 and all(pin(ROOT/n) == v for n, v in previous['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    packing = pack(out/'cellchat-catalog-support.zip', files)
    assert pin(out/'cellchat-catalog-support.zip')['bytes'] < 95000000
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=True, source_pins=source_pins,
        catalog_snapshot=catalog_snapshot, page_pins=pages, archive=pin(out/'cellchat-catalog-support.zip'),
        published_root_artifacts=published, frozen_development_files=frozen, papers=250, tools=400,
        definitions=438, observations=2466, reviewed_roles=1024, supporting_pointers=357,
        new_scoped_integrations=94, including_existing_adapters=97, missing=247,
        unchanged_unrelated_tool_objects=399, unchanged_prior_reviews=1018, new_reviews=6, new_observations=0,
        complete_root_bundle_checks=48, actual_root_dependency_skips=30, predecessor_full_flows=6,
        scientific_gates=40, general_browser_checks=299, focused_browser_checks=12, source_roles_reviewed=6,
        root_command_available=True, root_command='cellchat-tools', canonical_adapter_available=False,
        canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18,
        scientific_verifiers_changed=False, source_allowlist_changed=False, whole_package_implemented=False,
        whole_paper_reproduced=False, whole_goal_complete=False,
        unresolved_P060_study_score_analysis_skipped=True,
        private_exclusions=['Raw primary article XML and unlicensed author notebooks', 'Installed runtimes/libraries/compilers'],
        post_gate_documentation_only_change='Current-count/warning README introduction added after gates; exact generated reference retained. Scientific JSON and pages unchanged. Final index adds the documentation and new closure files.',
        verification_scope='Stored source/archive integrity, exact review/observation preservation, unchanged scientific class/resource bytes and captured complete routed verification/skip relationships. No new model execution, producer authentication, independent biological validation, historical paper reproduction or whole-goal claim.',
        **packing)
    save(out/'cellchat-catalog-validation.json', proof)
    verify(False, out, False)
    for source, dest in [(out/'cellchat-catalog-validation.json', DATA/'cellchat-catalog-validation.json'),
        (out/'cellchat-catalog-support.zip', DATA/'cellchat-catalog-support.zip'),
        (out/'cellchat-catalog-validation.json', HOME/'VALIDATION.json'),
        (out/'cellchat-catalog-support.zip', HOME/'controls.zip')]:
        dest.write_bytes(source.read_bytes())
    index = {p.name: pin(p) for p in sorted(DATA.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'}
    (DATA/'SHA256SUMS').write_text(''.join(row['sha256']+'  '+name+'\n' for name, row in index.items()), encoding='utf8', newline='\n')
    assert all(pin(ROOT/n) == v for n, v in source_pins.items())
    assert all(pin(DATA/n) == v for n, v in catalog_snapshot.items())
    assert all(pin(ROOT/n) == v for n, v in previous['preserved'].items())
    verify(False)
    save(out/'COMPLETED.json', dict(status='PASS_FROZEN_CELLCHAT_CATALOG_AND_IDENTICAL_PUBLISHED_CHECKPOINT',
        source_pins=source_pins, published_files={p.relative_to(ROOT).as_posix(): pin(p) for p in [*destinations, DATA/'SHA256SUMS']},
        final_manifest_files=index, new_scoped_integrations=94, including_existing_adapters=97, missing=247,
        whole_goal_complete=False))
    print('Sealed94 new/97 including existing;247 missing;40 science and311 browser checks; goal active')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete catalog/source preservation checks')
    main()
