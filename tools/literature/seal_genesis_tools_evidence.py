# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete GENESIS root/branch/example controls; catalog admission is a separate gate."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source
from verify_genesis_tools_evidence import HOME, STATUS, inspect


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    base = ROOT / 'build/literature-review/genesis-runtime'; assert not out.exists() and out.is_relative_to(base); out.mkdir(parents=True)
    root = json.loads((base / 'root-examples-validation-2/COMPLETED.json').read_bytes()); assert root['status'] == 'PASS_GENESIS_ROOT13_LITERAL_ANALYSES11_ADMITTED2_REFUSED33_SAVED_REPLAYS12_HELPS1_SKIP'
    branch = json.loads((base / 'extended-61-branch-controls-1/COMPLETED.json').read_bytes()); assert branch['status'] == 'PASS61_EXTENDED_GENESIS_DECLARED_BRANCH_DISPOSITIONS_53_POSITIVE_AND8_REFUSALS'
    assert HOME.is_dir() and {p.name for p in HOME.iterdir()} == {'README.md'}
    python_sources = ['tools/literature/' + name for name in ['validate_genesis_workflow_branches.py', 'wire_genesis_dispatch.py', 'build_genesis_dispatch.py',
        'prepare_genesis_examples.py', 'validate_genesis_dispatch.py', 'apply_genesis_catalog_roles.py', 'genesis_catalog.py', 'verify_genesis_tools_evidence.py', 'seal_genesis_tools_evidence.py']]
    attrs = ROOT / '.gitattributes'; before = attrs.read_bytes()
    addition = ('\n# Preserve scoped GENESIS root workflow evidence and complete literal examples.\ndocs/literature/genesis-tools-development/** -text whitespace=cr-at-eol\nexamples/genesis-tools/** -text whitespace=cr-at-eol\n' + ''.join(n + ' -text whitespace=cr-at-eol\n' for n in python_sources)).encode('utf8')
    assert b'docs/literature/genesis-tools-development/**' not in before; after = before + addition
    row = inverse(before, after); row['path'] = '.gitattributes'; assert reverse_delta(after, row) == before
    data = ROOT / 'docs/literature/omics-2023-2026'; descriptor = data / 'genesis-tools-sourcebytes-shared-source-compatibility.json'; assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='genesis', scope='Append-only Git byte preservation for full GENESIS evidence, literal examples and helper sources. No scientific-source waiver.', paths=[row]))
    (out / 'gitattributes.before').write_bytes(before); attrs.write_bytes(after); verify_source(ROOT, data, '.gitattributes', row['before_sha256'])
    extension = ROOT / 'docs/literature/genesis-extended-workflow-development'; previous = json.loads((extension / 'VALIDATION.json').read_bytes()); source_pins = dict(previous['source_pins'])
    names = [*python_sources, 'src/main/java/org/jlinalg/cli/JLinAlgCli.java', '.gitattributes', descriptor.relative_to(ROOT).as_posix(),
        'docs/literature/GENESIS_INTEGRATION.md', 'docs/literature/genesis-tools-development/README.md',
        'docs/literature/omics-2023-2026/genesis-dispatch-shared-source-compatibility.json']
    names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'examples/genesis-tools').iterdir()) if p.is_file()]
    source_pins.update({name: pin(ROOT / name) for name in names}); assert all(pin(ROOT / name) == value for name, value in source_pins.items())
    predecessors = {}
    for directory in ['genesis-native-development', 'genesis-workflow-development', 'genesis-extended-workflow-development']:
        predecessors.update({p.relative_to(ROOT).as_posix(): pin(p) for p in (ROOT / 'docs/literature' / directory).iterdir() if p.is_file()})
    for name in ['snprelate-root-javac21.jar', 'snprelate-root-javac25.jar', 'VALIDATION.json']:
        path = ROOT / 'docs/literature/snprelate-tools-development' / name; predecessors[path.relative_to(ROOT).as_posix()] = pin(path)
    files = {}

    def add(name, path):
        assert name not in files and path.is_file() and not path.is_symlink(); files[name] = path.read_bytes()

    for name in source_pins:
        add('current-source/' + name, ROOT / name)
    for name in ['extended-61-branch-controls-1', 'root-wire-1', 'root-build21-1', 'root-build25-1', 'literal-examples-1', 'literal-examples-2', 'root-examples-validation-1', 'root-examples-validation-2', 'example-preparation-contracts-1']:
        assert (base / name).is_dir(), name
        for path in sorted((base / name).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                assert path.suffix.lower() not in ['.exe', '.dll', '.so', '.tar', '.gz', '.xz', '.pdf'], ('Do not redistribute runtimes or article prose', str(path))
                add(path.relative_to(ROOT).as_posix(), path)
    for name in ['create_genesis_examples_1.py', 'example-preparer-before-profile-diagnostics-1.py', 'validate_genesis_dispatch-before-original-error-contract-1.py', 'test_example_preparation_contracts_1.py']:
        add((base / name).relative_to(ROOT).as_posix(), base / name)
    artifacts = {}
    for compiler in [21, 25]:
        source = base / f'root-build{compiler}-1/genesis-root-supplement.jar'; name = f'genesis-root-javac{compiler}.jar'; shutil.copyfile(source, HOME / name); artifacts[name] = pin(HOME / name)
    packed = pack(out / 'controls.zip', files); assert pin(out / 'controls.zip')['bytes'] < 95_000_000; shutil.copyfile(out / 'controls.zip', HOME / 'controls.zip')
    retained = json.loads((ROOT / 'build/literature-review/push-checkpoint-2026-11-10-genesis-extended-1/selection.json').read_bytes())
    assert len(retained['preserved']) == 18 and all(pin(ROOT / name) == value for name, value in retained['preserved'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == retained['canonical']
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=False, workflow_ready_for_catalog=True, root_command_available=True, whole_package_implemented=False,
        whole_paper_reproduced=False, whole_goal_complete=False, source_pins=source_pins, predecessor_evidence_files=predecessors, artifacts=artifacts,
        archive=pin(HOME / 'controls.zip'), declared_option_branch_controls=61, branch_native_successes=53, branch_scientific_refusals=5, branch_original_errors=3,
        root_original_attempts=13, root_admitted_analyses=11, root_expected_scientific_refusals=2, root_saved_replays=33, root_help_checks=12, root_prerequisite_skips=1,
        saved_bundle_paths=['literal-examples-2/results/' + name for name in root['admitted_cases']],
        canonical_executable_unchanged=retained['canonical'], unrelated_files_preserved=18, participant_data_or_article_prose_distributed=False,
        upstream_runtime_binaries_or_package_archives_distributed=False,
        scope='Declared current GENESIS2.42.0 typed branch, root-dispatch and complete synthetic example controls with exact prior scientific bytes. No independent estimator refit, full package, historical-paper, biological-validity or whole-goal certificate. Catalog admission is separate.', **packed)
    save(HOME / 'VALIDATION.json', proof); inspect()
    save(out / 'COMPLETED.json', dict(status=STATUS, proof=pin(HOME / 'VALIDATION.json'), archive=proof['archive'], new_catalog_credit=False, whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source, predecessor science or held-edit preservation')
    main()
