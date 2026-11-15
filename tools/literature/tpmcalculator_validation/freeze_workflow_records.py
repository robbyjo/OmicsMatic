#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze/read back complete synthetic public workflow records; assign no catalog credit."""
import argparse, hashlib, json, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT.parent / 'literature-review/monocle-runtime'
EVIDENCE = ROOT / 'docs/literature/tpmcalculator-workflow-development'

def rawpin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())

def pin(path):
    return rawpin(path.read_bytes())

def write(path, obj):
    path.write_text(json.dumps(obj, indent=2) + '\n', encoding='utf-8')

def members(folder, prefix):
    for path in sorted(folder.rglob('*')):
        relative = path.relative_to(folder)
        if 'artifacts' in relative.parts:
            continue
        assert not path.is_symlink(), path
        name = prefix + '/' + relative.as_posix()
        yield name + '/' if path.is_dir() else name, b'' if path.is_dir() else path.read_bytes()

def export_linux(target, fat_only=False):
    roots = {
        'Linux': Path('/opt/omicsmatic-tpmcalculator-public-linux-qualification-20261115-1'),
        'Cross-Linux': Path('/opt/omicsmatic-tpmcalculator-cross-linux-qualification-20261115-1'),
        'Original-workflow': Path('/opt/omicsmatic-tpmcalculator-workflow-original-20261115-1')}
    if fat_only:
        roots = {'Fat-Linux': Path('/opt/omicsmatic-tpmcalculator-fat-linux-qualification-20261115-1')}
    for name, folder in roots.items():
        if name != 'Original-workflow':
            assert (folder / 'COMPLETED.json').is_file(), name
    with zipfile.ZipFile(target, 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for prefix, folder in roots.items():
            for name, raw in members(folder, prefix):
                archive.writestr(name, raw)
    print(json.dumps(dict(status='EXPORTED_COMPLETE_LINUX_SYNTHETIC_RECORDS_EXCEPT_RUNTIME_ARTIFACTS', archive=pin(target))))

def freeze(linux_records, linux_fat_records):
    EVIDENCE.mkdir(exist_ok=False)
    (EVIDENCE / '.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    index = {}
    with zipfile.ZipFile(EVIDENCE / 'complete-workflow-records.zip', 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        def add(name, raw):
            assert name not in index and not name.startswith('/') and '..' not in Path(name).parts, name
            archive.writestr(name, raw)
            index[name] = dict(kind='directory') if name.endswith('/') else dict(kind='file', **rawpin(raw))
        for prefix, folder in {
            'Windows': BASE / 'tpmcalculator-public-windows-qualification-1',
            'Cross-Windows': BASE / 'tpmcalculator-cross-windows-qualification-1',
            'Fat-Windows': BASE / 'tpmcalculator-fat-windows-qualification-1'}.items():
            for name, raw in members(folder, prefix):
                add(name, raw)
        for incoming in [linux_records, linux_fat_records]:
            with zipfile.ZipFile(incoming) as source:
                assert len(source.namelist()) == len(set(source.namelist()))
                for name in source.namelist():
                    add(name, source.read(name))
        for path in sorted((ROOT / 'build/test-results/test').glob('TEST-*.xml')):
            add('JUnit/' + path.name, path.read_bytes())
        for name in ['tpm-workflow-complete-matrix-1.log', 'tpm-workflow-publication-package-1.log']:
            add('Build/' + name, (ROOT / 'build' / name).read_bytes())
        # Retain the resolved test preparation failure as well as successful current checks.
        for path in sorted((ROOT / 'build').glob('tpm-workflow*guards*.log')):
            add('Historical-development/' + path.name, path.read_bytes())
    write(EVIDENCE / 'RECORD-INDEX.json', index)
    names = [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('TpmCalculator*.java'))]
    names += ['src/main/java/org/jlinalg/sequence/TpmCalculator.java', 'src/main/java/org/jlinalg/sequence/TpmCalculatorOrder.java',
              'src/test/java/org/jlinalg/cli/TpmCalculatorWorkflowTest.java', 'src/test/java/org/jlinalg/cli/TpmCalculatorOriginalTest.java',
              'src/test/java/org/jlinalg/sequence/TpmCalculatorOrderTest.java', 'docs/literature/TPMCALCULATOR_NATIVE_WORKFLOW.md',
              'docs/literature/tpmcalculator-native-development/QUALIFICATION.json', 'build.gradle']
    names += ['src/main/java/org/jlinalg/cli/' + name + '.java' for name in ['NextflowFiles', 'NextflowParameters', 'NextflowJson', 'SimpleJson']]
    names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'tools/literature/tpmcalculator_validation').iterdir()) if p.is_file()]
    names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'src/test/resources/org/jlinalg/sequence').glob('tpmcalculator-original-*.zip'))]
    proof = dict(status='PASS_NATIVE_TPMCALCULATOR_PUBLIC_WORKFLOW_DEVELOPMENT_NOT_CATALOG_ADMISSION',
                 source_files={name: pin(ROOT / name) for name in names},
                 evidence_files={p.name: pin(p) for p in EVIDENCE.iterdir() if p.is_file()},
                 actual_focused_tests=141, original_cases_per_host=64, public_failure_controls_per_host=9,
                 actual_public_processes_per_host=137, cross_host_replays_each_direction=5, original_number_controls=50049,
                 self_contained_application_replay_checks_each_host=5,
                 native_java_analysis=True, upstream_quantification_subprocess=False,
                 exact_source_table_bytes=True, full_state_original_cases=57, finite_full_state_tolerance_ULPs=64,
                 original_version_unreported_by_paper=True, source_tag='0.0.4', original_printed_version='0.0.3',
                 dependency='HTSJDK 5.0.0 with explicitly selected JDK decompression',
                 runtime_artifact_binaries_in_record_archive=False,
                 runtime_artifacts='Recorded by complete SHA256 and per-scientific-class implementation identities; preserved separately from public synthetic records.',
                 catalog_credit_added=0, new_scoped_integrations_unchanged=121, native_integrations_unchanged=20,
                 whole_tool_qualified=False, paper_reproduced=False, biological_accuracy_established=False, whole_goal_complete=False)
    write(EVIDENCE / 'QUALIFICATION.json', proof)
    return proof

def verify():
    proof = json.loads((EVIDENCE / 'QUALIFICATION.json').read_bytes())
    assert proof['status'] == 'PASS_NATIVE_TPMCALCULATOR_PUBLIC_WORKFLOW_DEVELOPMENT_NOT_CATALOG_ADMISSION'
    assert proof['catalog_credit_added'] == 0 and not proof['whole_goal_complete']
    for key, base in [('source_files', ROOT), ('evidence_files', EVIDENCE)]:
        for name, identity in proof[key].items():
            assert pin(base / name) == identity, (key, name)
    with zipfile.ZipFile(EVIDENCE / 'complete-workflow-records.zip') as archive:
        index = json.loads((EVIDENCE / 'RECORD-INDEX.json').read_bytes())
        assert len(archive.namelist()) == len(set(archive.namelist())) == len(index)
        assert set(archive.namelist()) == set(index)
        for name, identity in index.items():
            raw = archive.read(name)  # also checks ZIP CRC, including every complete captured stream/input
            assert identity == (dict(kind='directory') if name.endswith('/') else dict(kind='file', **rawpin(raw))), name
        def read(name):
            return json.loads(archive.read(name))
        counts = dict(tests=0, failures=0, errors=0, skipped=0)
        for name in index:
            if name.startswith('JUnit/') and name.endswith('.xml'):
                node = ET.fromstring(archive.read(name))
                for key in counts:
                    counts[key] += int(node.attrib.get(key, 0))
        assert counts == dict(tests=141, failures=0, errors=0, skipped=0), counts
        for host in ['Windows', 'Linux']:
            completed = read(host + '/COMPLETED.json')
            assert completed['status'] == 'PASS_ACTUAL_PACKAGED_NATIVE_TPMCALCULATOR_FULL_REFERENCE_WORKFLOWS_REPLAY_AND_PUBLIC_GUARDS'
            assert len(completed['cases']) == 64 and len(completed['public_guards']) == 9 and completed['actual_processes'] == 137
            processes = [n for n in index if n.startswith(host + '/') and n.endswith('-PROCESS.json')]
            assert len(processes) == 137
            assert read(host + '/SOURCE.json')['producer'] == pin(Path(__file__).with_name('qualify_workflow.py'))
            for case in completed['cases']:
                prefix = host + '/cases/' + case['name'] + '/'
                assert case['source_tables_byte_equal'] and case['complete_captured_input_replay'] and case['reference_inputs_unchanged']
                metadata = read(prefix + 'reference/CASE.json')
                for ordinal, sample in enumerate(metadata['sample_order'], 1):
                    tables = ['genes.out', 'genes.ent', 'genes.uni'] + (['transcripts.out', 'transcripts.ent'] if metadata.get('extended', True) else [])
                    for table in tables:
                        assert archive.read(prefix + 'reference/original-main/' + sample + '_' + table) == archive.read(prefix + 'analysis/products/sample-%06d/' % ordinal + table)
                for operation, marker in [('analysis', 'result_admitted'), ('replay', 'saved_replay_verified')]:
                    assert read(prefix + operation + '/COMPLETED.json')[marker]
                    assert read(prefix + operation + '/worker/EXIT.json')['exit_code'] == 0
                assert read(prefix + 'public-run-PROCESS.json')['exit_code'] == 0
                assert read(prefix + 'public-replay-PROCESS.json')['exit_code'] == 0
            for guard in completed['public_guards']:
                prefix = host + '/guards/' + guard['name'] + '/'
                operation = 'replay' if guard['name'] == 'resealed-results' else 'analysis'
                assert prefix + operation + '/COMPLETED.json' not in index
                assert not read(prefix + operation + '/FAILURE.json')['result_admitted']
                process = 'public-replay' if operation == 'replay' else 'public-run'
                assert read(prefix + process + '-PROCESS.json')['exit_code'] == guard['exit_code']
        for host in ['Cross-Windows', 'Cross-Linux', 'Fat-Windows', 'Fat-Linux']:
            completed = read(host + '/COMPLETED.json')
            assert completed['status'] == 'PASS_FIVE_OTHER_HOST_COMPLETE_BUNDLE_REPLAYS' and len(completed['cases']) == completed['actual_processes'] == 5
            for case in completed['cases']:
                prefix = host + '/' + case['name'] + '/'
                assert case['complete_file_and_directory_copy_equal'] and case['source_bundle_unchanged']
                assert read(prefix + 'PROCESS.json')['exit_code'] == 0
                assert read(prefix + 'replay/COMPLETED.json')['saved_replay_verified']
                inventory = read(prefix + 'SOURCE-INVENTORY.json')
                for name, identity in inventory.items():
                    member = prefix + 'incoming/' + name + ('/' if identity['kind'] == 'directory' else '')
                    assert index[member] == identity, member
        assert len(archive.read('Original-workflow/original-number.stdout').splitlines()) == 50049
        for name in ['Build/tpm-workflow-complete-matrix-1.log', 'Build/tpm-workflow-publication-package-1.log']:
            assert b'BUILD SUCCESSFUL' in archive.read(name), name
    return proof

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--export-linux', type=Path)
    parser.add_argument('--export-linux-fat', type=Path)
    parser.add_argument('--freeze', action='store_true')
    parser.add_argument('--linux-records', type=Path)
    parser.add_argument('--linux-fat-records', type=Path)
    args = parser.parse_args()
    if args.export_linux:
        export_linux(args.export_linux)
    elif args.export_linux_fat:
        export_linux(args.export_linux_fat, fat_only=True)
    else:
        if args.freeze:
            assert args.linux_records is not None and args.linux_fat_records is not None
            freeze(args.linux_records, args.linux_fat_records)
        result = verify()
        print(json.dumps({k: result[k] for k in ['status', 'actual_focused_tests', 'original_cases_per_host',
              'public_failure_controls_per_host', 'cross_host_replays_each_direction', 'catalog_credit_added', 'whole_goal_complete']}, indent=2))
