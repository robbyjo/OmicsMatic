# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check frozen native KING component sources and complete original count/binary relationships."""
import argparse, csv, io, json, math, struct, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import unpack
HOME = ROOT/'docs/literature/native-king-development'
STATUS = 'PASS_FROZEN_NATIVE_KING_BETWEEN_FAMILY_COMPONENT_WITH_TOOL_FAMILIES_OPEN'


def verify(home=HOME):
    home = Path(home)
    proof = json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status'] == STATUS and not proof['new_catalog_credit'] and not proof['whole_goal_complete']
    assert not proof['whole_tool_implemented'] and not proof['root_command_available']
    for name, value in proof['source_pins'].items():
        assert pin(ROOT/name) == value, ('Changed native/reader/component source', name)
    assert pin(home/'controls.zip') == proof['archive']
    files = unpack(home/'controls.zip', proof)
    base = 'build/literature-review/plink-relatedness-runtime/'

    def obj(name):
        return json.loads(files[base+name])

    intake = obj('primary-intake-2/COMPLETED.json')
    assert intake['status'] == 'PASS250_PRIMARY_SOURCES_AND92_PLINK_KING_ROLE_INTAKE_ONLY'
    assert intake['source_counts'] == {'plink': 52, 'plink2': 25, 'king': 15}
    for name, logical in [('docs/literature/omics-2023-2026/papers.json', 'papers.json'),
        ('docs/literature/omics-2023-2026/tool-inventory.json', 'tool-inventory.json'),
        ('tools/literature/reviewed-uses.json', 'reviewed-uses.json')]:
        raw = files['catalog-context/'+logical]
        import hashlib
        assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == intake['source_pins'][name]
    context = json.loads(files['catalog-context/tool-inventory.json'])
    assert all(r['implementation_status'] == 'method-overlap' for r in context if r['id'] in ['plink', 'plink2', 'king'])
    original = obj('reference-controls-3/COMPLETED.json')
    assert original['status'] == 'PASS_COMPLETE_ORIGINAL_PLINK_RELATEDNESS_REFERENCE_CONTROLS'
    assert len(original['records']) == 25
    assert all(r['exit_code'] == r['expected_exit'] for r in original['records'])
    assert {r['id']: r['exit_code'] for r in original['records'] if r['exit_code']} == {'king-cutoff-0.5': 8, 'rejected-split-chromosome': 6}
    for compiler in [21, 25]:
        name = f'native-reference{compiler}-1'
        report = obj(name+'/COMPLETED.json')
        assert report['status'] == 'PASS_NATIVE_KING960_COMPLETE_ORIGINAL_COUNT_AND_BINARY_COMPARISONS'
        assert (report['pairs'], report['finite_bit_identical'], report['nonfinite_classes_preserved']) == (960, 445, 515)
        for case in report['records']:
            folder = case['case']
            native = list(csv.DictReader(io.StringIO(files[base+name+'/'+folder+'/native.tsv'].decode('utf8')), delimiter='\t'))
            direct = list(csv.DictReader(io.StringIO(files[base+'reference-controls-3/'+folder+'/counts/original.kin0'].decode('utf8')), delimiter='\t'))
            raw = files[base+'reference-controls-3/'+folder+'/binary/original.king.bin']
            binary = struct.unpack('<'+'d'*(len(raw)//8), raw)
            assert len(native) == len(direct) == len(binary) == 120
            for actual, expected, coefficient in zip(native, direct, binary, strict=True):
                for field in ['FID1', 'IID1', 'FID2', 'IID2', 'NSNP', 'HETHET', 'IBS0', 'HET1_HOM2', 'HET2_HOM1']:
                    assert actual[field] == expected['#'+field if field == 'FID1' else field]
                value = actual['COEFFICIENT_HEX']
                value = float(value) if value in ['NaN', 'Infinity', '-Infinity'] else float.fromhex(value)
                if math.isnan(coefficient):
                    assert math.isnan(value)
                else:
                    assert struct.pack('<d', value) == struct.pack('<d', coefficient)
                assert (actual['DISPOSITION'] == 'FINITE') is math.isfinite(coefficient)
        api = obj(f'api-build{compiler}-1/COMPLETED.json')
        assert api['status'] == 'PASS_STANDALONE_NATIVE_KING_API_AND_FRESH_PUBLIC_CONSUMERS'
        assert len(api['invocations']) == 2 and all(r['standalone_public_consumer'] for r in api['invocations'])
    contracts = obj('native-contracts-1/COMPLETED.json')
    assert contracts['status'] == 'PASS_NATIVE_KING27_JUNIT_EXECUTIONS_AND2880_ORIGINAL_REFERENCE_PAIR_CHECKS'
    assert (contracts['test_methods'], contracts['test_executions'], contracts['skipped'], contracts['original_reference_pair_checks']) == (9, 27, 0, 2880)
    for cutoff in ['0', '0.177', '0.5']:
        table = list(csv.DictReader(io.StringIO(files[base+'reference-controls-3/table-filter-'+cutoff+'/original.kin0'].decode('utf8')), delimiter='\t'))
        assert sum(math.isnan(float(r['KINSHIP'])) for r in table) == 16
    for name, value in proof['artifacts'].items():
        assert pin(home/name) == value
        with zipfile.ZipFile(home/name) as jar:
            assert 'org/jlinalg/genetics/KingRobustKinship.class' in jar.namelist()
            assert 'org/jlinalg/genetics/PlinkBedDataset.class' in jar.namelist()
            assert not any('Probe' in n or 'Runner' in n or n.endswith('Test.class') for n in jar.namelist())
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof, files


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable frozen original/native component relationships')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home', type=Path, default=HOME)
    args = p.parse_args()
    report, _ = verify(args.home)
    print(report['status'])
