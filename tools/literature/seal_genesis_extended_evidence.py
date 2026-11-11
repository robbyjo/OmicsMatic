# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain native rank and extended GENESIS development evidence without granting catalog credit."""
import argparse
import json
import shutil
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack, byte_pin
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source
from verify_genesis_extended_evidence import HOME, PREFIX, STATUS, inspect

BASE = ROOT / 'build/literature-review/genesis-runtime'
NEW_SOURCES = [
    'src/main/java/org/jlinalg/cli/GenesisWorkflowAudit.java',
    'src/main/java/org/jlinalg/cli/GenesisWorkflowToolsCli.java',
    'src/main/java/org/jlinalg/genetics/GenesisRankNormalisation.java',
    'src/main/resources/org/jlinalg/omics/genesis/rank_reference.R',
    'src/main/resources/org/jlinalg/omics/genesis/run_genesis_workflows.R',
    'src/test/java/org/jlinalg/cli/GenesisBranchReferenceProbe.java',
    'src/test/java/org/jlinalg/cli/GenesisWorkflowReferenceProbe.java',
    'src/test/java/org/jlinalg/genetics/GenesisRankNormalisationTest.java',
    'src/test/java/org/jlinalg/genetics/GenesisRankReferenceProbe.java',
    *['tools/literature/' + name for name in [
        'build_genesis_rank_native.py', 'validate_genesis_rank_native.py', 'extend_genesis_runtime.py',
        'build_genesis_workflow_tools.py', 'test_genesis_workflow_tools_protocol.py',
        'verify_genesis_extended_evidence.py', 'seal_genesis_extended_evidence.py', 'test_genesis_extended_replay_contracts.py']],
    'docs/literature/GENESIS_EXTENDED_WORKFLOW_DEVELOPMENT.md',
    'docs/literature/genesis-extended-workflow-development/README.md',
]


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE); out.mkdir(parents=True)

    def record(name):
        return json.loads((BASE / name / 'COMPLETED.json').read_bytes())

    assert record('rank-native-validation-2')['status'] == 'PASS_COMPLETE_NATIVE_GENESIS_RANK_REFERENCE_AND_API_CONTRACTS'
    assert record('workflow-e2e-controls-1')['status'] == 'PASS_EIGHT_ACTUAL_WORKFLOW_EXECUTION_AND_REFUSAL_CONTRACTS'
    assert record('workflow-extended-protocol-1')['status'] == 'PASS_EXTENDED_GENESIS_SAVED_REPLAYS_AND_RESEALED_SCIENTIFIC_REFUSALS'
    papers = record('paper-pc-and-mixed-association-e2e-1')
    assert papers['status'] == 'COMPLETE_DECLARED_PAPER_PC_AND_TRUE_MIXED_ASSOCIATION_DISPOSITIONS' and len(papers['records']) == 5
    assert all(r['exit_code'] == 0 and r['result_admitted'] is True and r['verify_exit'] == 0 for r in papers['records'])
    for name in ['optional-original-setup-5', 'optional-no-permission-contract-1', 'rank-missing-profile-contract-1']:
        assert (BASE / name / 'COMPLETED.json').is_file()
    assert record('optional-original-setup-5')['status'] == 'PASS_PRIVATE_ORIGINAL_GENESIS_RUNTIME_SETUP_ONLY'
    assert record('optional-no-permission-contract-1')['status'].startswith('SKIP3_')
    assert record('rank-missing-profile-contract-1')['status'].startswith('SKIP3_')
    assert HOME.is_dir() and {f.name for f in HOME.iterdir()} == {'README.md'}
    previous = json.loads((ROOT / 'build/literature-review/push-checkpoint-2026-11-10-genesis-workflows-1/selection.json').read_bytes())
    assert len(previous['preserved']) == 18 and all(pin(ROOT / n) == value for n, value in previous['preserved'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    attrs = ROOT / '.gitattributes'; before = attrs.read_bytes()
    python_sources = [n for n in NEW_SOURCES if n.endswith('.py')]
    addition = ('\n# Preserve separate native rank and extended GENESIS development evidence.\ndocs/literature/genesis-extended-workflow-development/** -text whitespace=cr-at-eol\n' + ''.join(n + ' -text whitespace=cr-at-eol\n' for n in python_sources)).encode('utf8')
    assert b'docs/literature/genesis-extended-workflow-development/**' not in before
    after = before + addition; row = inverse(before, after); row['path'] = '.gitattributes'; assert reverse_delta(after, row) == before
    data = ROOT / 'docs/literature/omics-2023-2026'; descriptor = data / 'genesis-extended-sourcebytes-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='genesis',
                         scope='Exact append-only Git byte-preservation metadata. No scientific source change or source waiver.', paths=[row]))
    (out / 'gitattributes.before').write_bytes(before); attrs.write_bytes(after); verify_source(ROOT, data, '.gitattributes', row['before_sha256'])
    source_pins = {}
    for compiler in [21, 25]:
        build = record('workflow-candidate-build' + str(compiler) + '-6')
        assert build['status'] == 'PASS_ISOLATED_GENESIS_DEVELOPMENT_ADAPTER_BUILD_ONLY'
        source_pins.update(build['source_pins'])
        native = record('rank-native-build' + str(compiler) + '-2'); source_pins.update(native['source_pins'])
    source_pins.update({name: pin(ROOT / name) for name in [*NEW_SOURCES, '.gitattributes', descriptor.relative_to(ROOT).as_posix()]})
    assert all(pin(ROOT / n) == v for n, v in source_pins.items())
    files = {}

    def add(name, path):
        assert name not in files and path.is_file() and not path.is_symlink()
        files[name] = path.read_bytes()

    for name in source_pins:
        add('current-source/' + name, ROOT / name)
    folders = [
        'helper-source-before-prerequisite-refresh-1',
        'rank-native-build21-1', 'rank-native-build25-1', 'rank-native-validation-1',
        'rank-native-build21-2', 'rank-native-build25-2', 'rank-native-validation-2',
        'branch-controls-1', 'optional-branch-controls-1', 'positive-binomial-mixed-controls-1',
        *['workflow-candidate-build21-' + str(n) for n in range(1, 7)],
        'workflow-candidate-build25-5', 'workflow-candidate-build25-6', 'workflow-probe-build21-1',
        'workflow-candidate-controls-1', 'workflow-candidate-controls-3',
        'workflow-e2e-controls-1', 'workflow-extended-protocol-1', 'paper-pc-and-mixed-association-e2e-1',
        'optional-no-permission-contract-1', 'rank-missing-profile-contract-1',
    ]
    for name in folders:
        assert (BASE / name).is_dir(), name
        for path in sorted((BASE / name).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                assert path.suffix.lower() not in ['.exe', '.dll', '.so', '.tar', '.gz', '.xz', '.pdf'], ('Do not distribute runtime binaries, downloads or article prose', str(path))
                add(path.relative_to(ROOT).as_posix(), path)
    # Retain all setup attempt receipts and process/error logs, excluding installed
    # libraries, package archives and their extracted third-party binaries.
    for i in range(1, 6):
        home = BASE / ('optional-original-setup-' + str(i))
        assert home.is_dir()
        for path in sorted(home.iterdir()):
            if path.is_file() and path.suffix.lower() in ['.json', '.md', '.txt', '.traceback', '.stdout', '.stderr', '.r', '.py']:
                add(path.relative_to(ROOT).as_posix(), path)
    for path in sorted(BASE.glob('*.py')):
        add(path.relative_to(ROOT).as_posix(), path)
    original = BASE / 'official-intake-1/original-source/GENESIS'
    for folder in ['R', 'src']:
        for path in sorted((original / folder).rglob('*')):
            if path.is_file():
                add('upstream-source/GENESIS-2.42.0/' + path.relative_to(original).as_posix(), path)
    for name in ['DESCRIPTION', 'NAMESPACE']:
        add('upstream-source/GENESIS-2.42.0/' + name, original / name)
    add('upstream-source/GPL-3.0.txt', ROOT / 'LICENSE')
    artifacts = {}
    for compiler in [21, 25]:
        sources = {
            f'genesis-workflows-javac{compiler}.jar': BASE / f'workflow-candidate-build{compiler}-6/genesis-workflows-development.jar',
            f'genesis-rank-api-javac{compiler}.jar': BASE / f'rank-native-build{compiler}-2/genesis-rank-api.jar',
            f'genesis-rank-consumer-javac{compiler}.jar': BASE / f'rank-native-build{compiler}-2/genesis-rank-consumer.jar',
        }
        for name, source in sources.items():
            shutil.copyfile(source, HOME / name); artifacts[name] = pin(HOME / name)
    with zipfile.ZipFile(HOME / 'genesis-workflows-javac21.jar') as archive:
        vendor = {n: byte_pin(archive.read(n)) for n in archive.namelist() if n.startswith('jdistlib/') and not n.endswith('/')}
    packed = pack(out / 'controls.zip', files); assert pin(out / 'controls.zip')['bytes'] < 95_000_000
    shutil.copyfile(out / 'controls.zip', HOME / 'controls.zip')
    paths = ['workflow-e2e-controls-1/' + n for n in ['true-binomial-mixed-null', 'binomial-SPA', 'binomial-BinomiRare', 'gaussian-GxE', 'gaussian-two-stage-association']]
    paths += ['paper-pc-and-mixed-association-e2e-1/' + r['case'] for r in papers['records']]
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=False, GENESIS_tool_still_missing=True, whole_goal_complete=False,
                 whole_package_implemented=False, whole_paper_reproduced=False, root_command_available=False,
                 source_pins=source_pins, archive=pin(HOME / 'controls.zip'), artifacts=artifacts,
                 native_rank_original_cases=74, native_rank_complete_scores_compared=756264, native_rank_JUnit_executions=24,
                 extended_saved_replays=15, extended_resealed_scientific_refusals=87,
                 actual_extended_workflow_protocols=13, actual_successes=10, expected_dependency_skips=1, expected_scientific_refusals=2,
                 saved_bundle_paths=paths, vendored_jdistlib_entries=vendor,
                 canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18,
                 upstream_runtime_binaries_or_package_archives_distributed=False, participant_data_or_article_prose_distributed=False,
                 scope='Native finite ranking and stated typed-source/admission/replay contracts on synthetic controls only. No independent PCA/projection/kinship/model estimator refit, full package or historical-paper reproduction. Root command, broader coverage, complete examples and catalog admission remain outstanding.', **packed)
    save(HOME / 'VALIDATION.json', proof); inspect()
    old = (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines(); order = [r.split('  ', 1)[1] for r in old]
    manifest = {p.name: pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'}
    order = [n for n in order if n in manifest] + [n for n in manifest if n not in order]
    (data / 'SHA256SUMS').write_text(''.join(manifest[n] + '  ' + n + '\n' for n in order), encoding='utf8', newline='\n')
    from verify_genesis_partition_evidence import inspect as inspect_partition
    from verify_genesis_workflow_evidence import inspect as inspect_previous
    from verify_snprelate_catalog import verify as verify_catalog
    inspect_partition(); inspect_previous(); catalog, _ = verify_catalog()
    assert (catalog['including_existing_adapters'], catalog['missing']) == (99, 247)
    assert all(pin(ROOT / n) == value for n, value in previous['preserved'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    save(out / 'COMPLETED.json', dict(status=STATUS, proof=pin(HOME / 'VALIDATION.json'), archive=proof['archive'], new_catalog_credit=False, whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/catalog/canonical or unrelated-edit preservation')
    main()
