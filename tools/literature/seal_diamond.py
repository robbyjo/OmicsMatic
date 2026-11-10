# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze DIAMOND source controls, retained failures, complete adapters and portable root evidence."""
import argparse, json, os, shutil, stat
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save
from validate_diamond_dispatch import populations
from signac_scan_evidence import pack, byte_pin
from verify_diamond_evidence import HOME, STATUS, verify


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    assert not HOME.exists()
    HOME.mkdir()
    cases = populations()
    rooted = json.loads((BASE/'root-validation-1/COMPLETED.json').read_bytes())
    assert rooted['status'] == 'PASS216_DIAMOND_ROOT_COMPLETE_CHECKS18_REFUSALS3_SKIPS15_PREDECESSORS3_ALIGNMENTS'
    sources = [ROOT/'src/main/java/org/jlinalg/cli'/(name+'.java') for name in ['DiamondToolsCli',
        'DiamondRequest', 'DiamondSequences', 'DiamondAudit', 'NextflowParameters', 'NextflowFiles',
        'NextflowJson', 'SimpleJson', 'JLinAlgCli']]
    sources += list((ROOT/'src/main/resources/org/jlinalg/omics/diamond').glob('*'))
    sources += [ROOT/'tools/literature'/name for name in ['diamond-source-lock.json', 'setup_diamond_runtime.py',
        'create_diamond_controls.py', 'collect_diamond_reference.py', 'build_diamond.py',
        'validate_diamond_execution.py', 'test_diamond_protocol.py', 'validate_diamond_setup.py',
        'bridge_diamond_dispatch.py', 'build_diamond_dispatch.py', 'validate_diamond_dispatch.py',
        'seal_diamond.py', 'verify_diamond_evidence.py']]
    sources += [ROOT/'docs/literature/DIAMOND_INTEGRATION.md']
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    files, objects, excluded = {}, {}, {}

    def add(name, path):
        assert name not in files
        raw = path.read_bytes()
        files[name] = objects.setdefault(byte_pin(raw)['sha256'], raw)

    for name in source_pins:
        add('current-source/'+name, ROOT/name)
    directories = ['controls-1', 'controls-2', 'reference-controls-1', 'reference-controls-2',
        'runtime-preflight-1', 'field-profile-adjustment-1', 'output-field-probe-1',
        'runtime-profile-2.0.0-1', 'runtime-profile-2.0.15-1', 'runtime-profile-2.1.8-1',
        'build21-1', 'build25-1', 'build21-2', 'build25-2', 'build21-3', 'build25-3',
        'adapter-pilot-1', 'adapter-source2.0.0-1', 'adapter-source2.0.0-2', 'adapter-source2.0.0-3',
        'adapter-source2.0.15-1', 'adapter-source2.1.8-1', 'adapter-existing-db-1', 'protocol-1',
        'packaged-setup-1', 'root-bridge-1', 'root-bridge-2', 'root-build21-1', 'root-build25-1', 'root-validation-1', 'root-validation-exit-1']
    for name in directories:
        folder = BASE/name
        assert folder.is_dir(), name
        for directory, dirs, names in os.walk(folder, followlinks=False):
            dirs[:] = [n for n in dirs if n != '__pycache__']
            for filename in names:
                path = Path(directory)/filename
                relative = path.relative_to(ROOT).as_posix()
                assert stat.S_ISREG(path.stat(follow_symlinks=False).st_mode)
                if name == 'packaged-setup-1' and (path.name == 'original-linux64.tar.gz' or path.parent.name == 'bin' and path.name == 'diamond'):
                    excluded[relative] = dict(reason='Private installed original runtime; official download/hash provenance retained', **pin(path))
                else:
                    add(relative, path)
    for version in ['2.0.0', '2.0.15', '2.1.8']:
        for path in sorted((BASE/'source-intake-1'/version).iterdir()):
            if path.is_file() and path.name != 'original-linux64.tar.gz':
                add(path.relative_to(ROOT).as_posix(), path)
    artifacts = {}
    for co in [21, 25]:
        for name, source in [(f'diamond-javac{co}.jar', BASE/f'build{co}-3/diamond-development.jar'),
            (f'diamond-root-javac{co}.jar', BASE/f'root-build{co}-1/diamond-root-supplement.jar')]:
            shutil.copy2(source, HOME/name)
            artifacts[name] = pin(HOME/name)
    packing = pack(out/'controls.zip', files)
    assert pin(out/'controls.zip')['bytes'] < 95000000
    shutil.copy2(out/'controls.zip', HOME/'controls.zip')
    previous = json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cellchat-catalog-1/selection.json').read_bytes())
    assert len(previous['preserved']) == 18 and all(pin(ROOT/n) == v for n, v in previous['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=False, whole_goal_complete=False,
        source_pins=source_pins, artifacts=artifacts, archive=pin(HOME/'controls.zip'),
        original_cases=78, successful_source_cases=72, preserved_original_failure_cases=6,
        existing_database_alignments=6, explicit_download_alignment=1, routed_original_alignments=3,
        complete_root_bundle_checks=216, failed_root_bundle_refusals=18, dependency_skips=3,
        predecessor_complete_checks=15, protocol_checks=22,
        cases=[{k: v for k, v in row.items() if k in ['version', 'case', 'original_exit', 'adapter_exit', 'rows']} |
            dict(home=row['home'].relative_to(ROOT).as_posix()) for row in cases],
        original_source_archives_retained=True, exact_runtime_download_validated=True,
        earlier_verifier_notation_failure_retained=True, earlier_explicit_wall_time_failure_retained=True,
        extended_field_source_crashes_retained=True, whole_package_implemented=False,
        whole_paper_reproduced=False, analysis_installs_software=False,
        canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18,
        private_exclusions=excluded, raw_primary_article_xml_not_distributed=True,
        scope='Complete original source-option populations and typed native input/coordinate/statistic transports. Runtime identity/setup and complete saved-bundle verification are explicit. No independent alignment placement, bit-score/E-value refit, producer authentication, historical compilation/paper reproduction or biological interpretation.', **packing)
    save(HOME/'VALIDATION.json', proof)
    verify(HOME)
    save(out/'COMPLETED.json', dict(status=STATUS, proof=pin(HOME/'VALIDATION.json'), artifacts=artifacts,
        archive=proof['archive'], new_catalog_credit=False, whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source/evidence preservation checks')
    main()
