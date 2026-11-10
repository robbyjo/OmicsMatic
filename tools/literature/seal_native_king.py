# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native between-family kinship as a component without whole-tool catalog credit."""
import argparse, json, os, shutil, stat
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack, byte_pin
from verify_native_king_evidence import HOME, STATUS, verify
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    assert not HOME.exists()
    HOME.mkdir()
    sources = [ROOT/n for n in ['src/main/java/org/jlinalg/genetics/KingRobustKinship.java',
        'src/main/java/org/jlinalg/genetics/PlinkBedDataset.java',
        'src/test/java/org/jlinalg/genetics/KingRobustKinshipTest.java',
        'src/test/java/org/jlinalg/genetics/KingRobustReferenceProbe.java',
        'src/test/resources/org/jlinalg/genetics/native-king-original-reference.zip',
        'examples/native-king/KingBedExample.java', 'docs/literature/NATIVE_KING_ROBUST.md']]
    names = ['audit_plink_relatedness_roles.py', 'collect_plink_relatedness_reference.py',
        'validate_native_king_reference.py', 'freeze_native_king_reference.py', 'test_native_king_contracts.py',
        'build_native_king_api.py', 'verify_native_king_evidence.py', 'seal_native_king.py']
    sources += [ROOT/'tools/literature'/name for name in names]
    source_pins = {path.relative_to(ROOT).as_posix(): pin(path) for path in sources}
    files, objects = {}, {}

    def add(name, path):
        assert name not in files
        raw = path.read_bytes()
        files[name] = objects.setdefault(byte_pin(raw)['sha256'], raw)

    for name in source_pins:
        add('current-source/'+name, ROOT/name)
    add('build/literature-review/plink-relatedness-runtime/primary-intake-2/COMPLETED.json', BASE/'primary-intake-2/COMPLETED.json')
    for name, path in [('papers.json', ROOT/'docs/literature/omics-2023-2026/papers.json'),
        ('tool-inventory.json', ROOT/'docs/literature/omics-2023-2026/tool-inventory.json'),
        ('reviewed-uses.json', ROOT/'tools/literature/reviewed-uses.json')]:
        add('catalog-context/'+name, path)
    directories = ['reference-controls-1', 'reference-controls-2', 'reference-controls-3',
        'native-reference21-1', 'native-reference25-1', 'reference-fixture-1', 'native-contracts-1', 'api-build21-1', 'api-build25-1']
    for name in directories:
        folder = BASE/name
        assert folder.is_dir()
        for directory, dirs, names in os.walk(folder, followlinks=False):
            dirs[:] = [d for d in dirs if d != '__pycache__']
            for filename in names:
                path = Path(directory)/filename
                assert stat.S_ISREG(path.stat(follow_symlinks=False).st_mode)
                add(path.relative_to(ROOT).as_posix(), path)
    # Only immutable URL/hash metadata about official source intake is public.
    # Full publisher/official HTML, restricted KING assets and raw primary text stay private.
    official = json.loads((BASE/'source-intake-2/COMPLETED.json').read_bytes())
    metadata = {k: v for k, v in official.items() if k != 'source_records'}
    metadata['source_records'] = [{k: v for k, v in row.items() if k in ['url', 'final_url', 'path', 'bytes', 'sha256', 'fetched_utc']} for row in official['source_records']]
    files['official-source-intake.json'] = (json.dumps(metadata, indent=2)+'\n').encode()
    artifacts = {}
    for compiler in [21, 25]:
        build = json.loads((BASE/f'api-build{compiler}-1/COMPLETED.json').read_bytes())
        assert build['status'] == 'PASS_STANDALONE_NATIVE_KING_API_AND_FRESH_PUBLIC_CONSUMERS'
        source = ROOT/build['artifact']['path']
        target = HOME/f'native-king-api-javac{compiler}.jar'
        shutil.copy2(source, target)
        artifacts[target.name] = pin(target)
    packing = pack(out/'controls.zip', files)
    assert pin(out/'controls.zip')['bytes'] < 95000000
    shutil.copy2(out/'controls.zip', HOME/'controls.zip')
    previous = json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-diamond-2/selection.json').read_bytes())
    assert len(previous['preserved']) == 18 and all(pin(ROOT/name) == value for name, value in previous['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=False, whole_goal_complete=False,
        source_pins=source_pins, artifacts=artifacts, archive=pin(HOME/'controls.zip'),
        native_between_family_component_available=True, api_only=True, root_command_available=False,
        whole_tool_implemented=False, whole_paper_reproduced=False, catalog_statuses_unchanged=True,
        catalog_new_scoped_integrations=95, including_existing_adapters=98, missing_tools=246,
        original_cases=8, pairs_per_compiler=960, finite_binary_identical_per_compiler=445,
        nonfinite_classes_preserved_per_compiler=515, test_methods=9, test_executions=27,
        source_reference_pair_checks_in_tests=2880, fresh_public_consumers=4,
        original_filtered_nan_rows_preserved_per_cutoff=16, source_primary_papers_scanned=250,
        existing_plink_king_roles_audited=92, original_genome_reports_capture_only=True,
        existing_bed_reader_modified=False, canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18,
        private_exclusions=['Raw primary article XML and full extracted paragraphs', 'Original restricted KING archives/source/executables/example',
            'Original PLINK executables/archives and shared runtime libraries', 'Full official website HTML and test-framework dependencies'],
        scope='Native PLINK2 between-family KING-robust count/formula component and a fresh Java17-targeted BED consumer. Exact original synthetic pair populations and binary finite coefficients are checked; NaN/-Infinity retain unreportable dispositions. No standalone KING, within-family/SNPRelate equivalence, PI_HAT, segment inference, unrelated-selection, historical paper reproduction or whole-tool catalog credit.', **packing)
    save(HOME/'VALIDATION.json', proof)
    verify(HOME)
    save(out/'COMPLETED.json', dict(status=STATUS, source_pins=source_pins,
        published_files={path.relative_to(ROOT).as_posix(): pin(path) for path in HOME.iterdir() if path.is_file()},
        new_catalog_credit=False, whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable native/whole-tool boundary and frozen evidence checks')
    main()
