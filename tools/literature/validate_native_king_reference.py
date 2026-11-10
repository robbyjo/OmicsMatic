# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build native KING-robust independently and compare every original count/binary coefficient."""
import argparse, csv, json, math, shutil, struct, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_NATIVE_KING_ORIGINAL_REFERENCE', new_catalog_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
        before = pin(canonical)
        files = [ROOT/'src/main/java/org/jlinalg/genetics/KingRobustKinship.java',
            ROOT/'src/test/java/org/jlinalg/genetics/KingRobustReferenceProbe.java', Path(__file__)]
        pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in files}
        for name in pins:
            target = out/'source-snapshot'/name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT/name, target)
        tools = Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        classes = out/'classes'
        classes.mkdir()
        cmd = [str(tools/'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-d', str(classes), *map(str, files[:2])]
        compiled = subprocess.run(cmd, capture_output=True, timeout=120)
        (out/'compile.stdout').write_bytes(compiled.stdout)
        (out/'compile.stderr').write_bytes(compiled.stderr)
        save(out/'compile.json', dict(command=cmd, exit_code=compiled.returncode))
        assert compiled.returncode == 0, compiled.stderr[-1800:]
        jar = out/'native-king-probe.jar'
        with zipfile.ZipFile(jar, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(classes.rglob('*.class')):
                info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), (1980, 1, 1, 0, 0, 0))
                info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
                archive.writestr(info, path.read_bytes())
        reference = BASE/'reference-controls-3'
        original = json.loads((reference/'COMPLETED.json').read_bytes())
        assert original['status'] == 'PASS_COMPLETE_ORIGINAL_PLINK_RELATEDNESS_REFERENCE_CONTROLS'
        cases = json.loads((reference/'inputs/cases.json').read_bytes())
        records, total_pairs, finite_pairs, nonfinite_pairs = [], 0, 0, 0
        for case in cases:
            folder = out/case['id']
            folder.mkdir()
            cmd = [str(tools/'java.exe'), '-cp', str(jar), 'org.jlinalg.genetics.KingRobustReferenceProbe',
                str(reference/case['prefix']), str(folder/'native.tsv')]
            process = subprocess.run(cmd, capture_output=True, timeout=60)
            (folder/'stdout').write_bytes(process.stdout)
            (folder/'stderr').write_bytes(process.stderr)
            save(folder/'process.json', dict(command=cmd, exit_code=process.returncode))
            assert process.returncode == 0, process.stderr[-1800:]
            with (folder/'native.tsv').open(encoding='utf8', newline='') as stream:
                native = list(csv.DictReader(stream, delimiter='\t'))
            with (reference/case['id']/'counts/original.kin0').open(encoding='utf8', newline='') as stream:
                direct = list(csv.DictReader(stream, delimiter='\t'))
            binary = (reference/case['id']/'binary/original.king.bin').read_bytes()
            values = struct.unpack('<'+'d'*(len(binary)//8), binary)
            assert len(native) == len(direct) == len(values) == 120
            finite = nonfinite = 0
            for row, raw, exact in zip(native, direct, values, strict=True):
                for name in ['FID1', 'IID1', 'FID2', 'IID2', 'NSNP', 'HETHET', 'IBS0', 'HET1_HOM2', 'HET2_HOM1']:
                    assert row[name] == raw['#'+name if name == 'FID1' else name], (case['id'], name, row, raw)
                actual = float.fromhex(row['COEFFICIENT_HEX']) if row['COEFFICIENT_HEX'] not in ['NaN', 'Infinity', '-Infinity'] else float(row['COEFFICIENT_HEX'])
                if math.isnan(exact):
                    assert math.isnan(actual) and math.isnan(float(raw['KINSHIP']))
                    assert row['DISPOSITION'] in ['NO_JOINT_CALLS', 'ZERO_SMALLER_HETEROZYGOSITY']
                    nonfinite += 1
                elif math.isinf(exact):
                    assert actual == exact == float(raw['KINSHIP']) == -math.inf
                    assert row['DISPOSITION'] == 'ZERO_SMALLER_HETEROZYGOSITY'
                    nonfinite += 1
                else:
                    assert struct.pack('<d', actual) == struct.pack('<d', exact), (case['id'], row, exact)
                    assert row['DISPOSITION'] == 'FINITE'
                    # The source table is decimal-rounded; the binary population is the exact comparison.
                    text = raw['KINSHIP'].lower()
                    precision = 0.5*10**(-len(text.split('.')[1])) if '.' in text and 'e' not in text else 0.00000051
                    assert abs(actual-float(text)) <= precision+1e-14
                    finite += 1
            total_pairs += len(native); finite_pairs += finite; nonfinite_pairs += nonfinite
            records.append(dict(case=case['id'], variants=case['variants'], pairs=len(native),
                all_original_counts_equal=True, finite_binary_values_bit_identical=finite,
                original_nonfinite_classes_preserved=nonfinite, packed_and_decoded_equal=True))
        assert total_pairs == 960 and finite_pairs > 0 and nonfinite_pairs > 0
        assert pin(canonical) == before and all(pin(ROOT/n) == v for n, v in pins.items())
        state.update(status='PASS_NATIVE_KING960_COMPLETE_ORIGINAL_COUNT_AND_BINARY_COMPARISONS',
            compiler=a.compiler, records=records, pairs=total_pairs, finite_bit_identical=finite_pairs,
            nonfinite_classes_preserved=nonfinite_pairs, source_pins=pins,
            artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), canonical_executable_unchanged=before,
            scope='PLINK2 between-family estimator on complete synthetic diploid hard calls. Not a within-family KING, SNPRelate, PI_HAT, segment inference, unrelated-selection or biological validation claim.')
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:1200], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete original coefficient/count checks')
    raise SystemExit(main())
