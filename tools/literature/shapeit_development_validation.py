#!/usr/bin/env python3
"""Verify the bounded SHAPEIT native-audit evidence; this never admits a phaser."""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import zipfile
from catalog_source_integrity import verify_source

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/literature/omics-2023-2026'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def archive(raw, maximum_bytes):
    members = {}
    total = 0
    with zipfile.ZipFile(io.BytesIO(raw)) as source:
        for info in source.infolist():
            if info.is_dir() or info.filename in members or len(members) >= 2048 or info.file_size > maximum_bytes:
                raise ValueError('Invalid, duplicate or oversized SHAPEIT evidence member')
            total += info.file_size
            if total > maximum_bytes:
                raise ValueError('Complete SHAPEIT evidence exceeds decoded capacity')
            members[info.filename] = source.read(info)
    rows = members['SHA256SUMS'].decode().splitlines()
    if len(rows) != len(members) - 1:
        raise ValueError('Incomplete SHAPEIT evidence checksum membership')
    checked = set()
    for row in rows:
        expected, name = row.split('  ', 1)
        if name == 'SHA256SUMS' or name in checked or name not in members or digest(members[name]) != expected:
            raise ValueError('SHAPEIT evidence checksum differs: ' + name)
        checked.add(name)
    return members


def verify(root=ROOT, data=DATA):
    if not __debug__:
        raise RuntimeError('Verification requires normal Python; remove -O or -OO')
    root, data = Path(root), Path(data)
    proof = json.loads((data / 'shapeit-development-validation.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == 'passed-native-reader-and-diploid-output-audit-foundation'
    assert not proof['public_upstream_adapter_available'] and not proof['whole_paper_reproduced']
    for item in proof['source_evidence']:
        verify_source(root, data, item['path'], item['sha256'])
    path = data / proof['native_controls']['path']
    raw = path.read_bytes()
    assert len(raw) == proof['native_controls']['bytes'] and digest(raw) == proof['native_controls']['sha256']
    controls = archive(raw, 16777216)
    matrix = json.loads(controls['matrix/observed.json'])
    assert matrix['status'] == 'passed15exact-native-package-tests-three-JVMs-and-strict17-javadoc'
    assert set(matrix['profiles']) == {'Windows21', 'Windows25', 'Linux21'}
    assert all(p == {'tests': 15, 'passed': 15, 'skipped': 0} for p in matrix['profiles'].values())
    assert all(run['exit_code'] == 0 for run in matrix['runs'])
    candidate = controls['artifacts/shapeit-native-audit.jar']
    assert digest(candidate) == matrix['candidate_sha256'] == proof['native_candidate_sha256']
    with zipfile.ZipFile(io.BytesIO(candidate)) as jar:
        classes = [name for name in jar.namelist() if name.endswith('.class')]
        assert classes and all(name.startswith('org/jlinalg/genetics/ShapeitGenotypes') for name in classes)
        assert all(int.from_bytes(jar.read(name)[6:8], 'big') == 61 for name in classes)
        for name in ['SHAPEIT4-MIT.txt', 'SHAPEIT5-MIT.txt']:
            assert jar.read('META-INF/licenses/' + name) == controls['source/src/main/resources/META-INF/licenses/' + name]
    for name, expected in matrix['source_sha256'].items():
        assert digest(controls['source/' + name]) == expected
    reference_raw = controls['artifacts/initial-reader.zip']
    assert digest(reference_raw) == proof['original_controls_sha256']
    reference = archive(reference_raw, 4194304)
    provenance = json.loads(reference['provenance.json'])
    assert provenance['original_reader_cells'] == provenance['original_cohort_phasing_cells'] == 800
    assert len(provenance['excluded_original_prebuilt_ELF_members']) == 2
    for profile, manifest_name in [('4.1.3', '4.1.3-selected-source.json'), ('5-c34d', '5-c34d-selected-source.json')]:
        manifest = json.loads(reference['records/' + manifest_name])
        excluded = {item['path']: item for item in provenance['excluded_original_prebuilt_ELF_members'] if item['profile'] == profile}
        for item in manifest['files']:
            if item['path'] in excluded:
                assert excluded[item['path']]['sha256'] == item['sha256']
                assert 'upstream/' + profile + '/' + item['path'] not in reference
            else:
                raw = reference['upstream/' + profile + '/' + item['path']]
                assert digest(raw) == item['sha256']
                assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == item['git_blob_sha1']
    reconciled = json.loads(reference['records/reader/build-reconciliation.json'])
    assert reconciled['all_original_algorithm_and_makefile_bytes_unchanged']
    assert reconciled['make_queries_up_to_date'] == reconciled['working_help_probes'] == 5
    assert [item['path'] for item in reconciled['allowed_changed_original_member']] == ['ligate/bin/ligate']
    source4 = json.loads(reference['records/build/observed.json'])['profiles'][0]
    assert source4['profile'] == '4.1.3' and source4['exit_code'] == 0 and source4['source_bytes_unchanged']
    phase_programs = {'4.1.3': source4['binaries'][0]['sha256'],
                      '5-c34d': next(p['sha256'] for p in reconciled['binaries'] if p['program'] == 'phase_common')}
    reader = json.loads(reference['records/reader/observed.json'])
    cohort = json.loads(reference['records/cohort/observed.json'])
    assert digest(reference['protocols/ReaderProtocol.cpp']) == reader['reader_protocol_sha256']
    assert digest(reference['protocols/MakeWireFixture.cpp']) == reader['fixture_generator_sha256']
    assert len(reader['cases']) == len(cohort['cases']) == 4
    cells = 0
    for case in reader['cases']:
        raw = reference['captures/' + case['result']]
        assert digest(raw) == case['sha256']
        rows = list(csv.DictReader(io.StringIO(raw.decode()), delimiter='\t'))
        assert len(rows) == 200 and len({(r['variant'], r['sample']) for r in rows}) == 200
        assert {r['variant'] for r in rows} == {'0', '1', '2', '3'}
        assert {r['sample'] for r in rows} == {'S' + str(i) for i in range(1000, 1050)}
        cells += len(rows)
    assert cells == 800
    for case in cohort['cases']:
        name = case['profile'] + '-' + case['format']
        assert case['program_sha256'] == phase_programs[case['profile']]
        assert digest(reference['captures/' + name + '.phased.bcf']) == case['output_sha256']
        assert digest(reference['captures/' + name + '.phased.vcf']) == case['decoded_sha256']
        assert digest(reference['inputs/' + ('wire.bcf' if case['format'] == 'bcf' else 'wire.vcf.gz')]) == case['source_sha256']
        records = [line.split('\t') for line in reference['captures/' + name + '.phased.vcf'].decode().splitlines() if not line.startswith('#')]
        assert len(records) == 4 and all(len(row) == 59 for row in records)
        for row in records:
            gt = row[8].split(':').index('GT')
            assert all(sample.split(':')[gt] in {'0|0', '0|1', '1|0', '1|1'} for sample in row[9:])
    result = dict(status='passed-current-native-SHAPEIT-audit-evidence', original_reader_cells=800,
                  original_cohort_output_cells=800, tests_per_JVM=15, JVM_profiles=3, public_upstream_adapter_available=False,
                  scope='Native initial target-reader and complete diploid-output contracts only. This verifier does not run or admit an original phaser, certify phase accuracy, or establish a complete paper workflow.')
    print(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--data', type=Path, default=DATA)
    arguments = parser.parse_args()
    verify(arguments.root, arguments.data)
