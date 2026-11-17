#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the actual packaged command with independent R TukeyHSD fixtures."""
from pathlib import Path
import argparse
import csv
import hashlib
import json
import os
import subprocess
import sys
import time

if not __debug__:
    raise RuntimeError('Reference assertions must remain enabled; rerun without -O')


def pin(path):
    with Path(path).open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(bytes=Path(path).stat().st_size, sha256=digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    root = Path(__file__).resolve().parents[3]
    fixtures = root/'src/test/resources/org/jlinalg/stats/tukey'
    provenance = json.loads((fixtures/'PROVENANCE.json').read_bytes())
    for name, expected in provenance['files'].items():
        assert pin(fixtures/name) == expected
    prefix = [str(args.java.resolve()), '-Xmx512m', '-jar', str(args.jar.resolve()), 'posthoc-tools', 'tukey']
    cases = {}
    with (fixtures/'inputs.tsv').open(encoding='utf-8', newline='') as stream:
        for row in csv.DictReader(stream, delimiter='\t'):
            cases.setdefault(row['case'], []).append(row)
    references = {}
    with (fixtures/'expected.tsv').open(encoding='utf-8', newline='') as stream:
        for row in csv.DictReader(stream, delimiter='\t'):
            references.setdefault(row['case'], []).append(row)
    with (fixtures/'two-group-exact.tsv').open(encoding='utf-8', newline='') as stream:
        for row in csv.DictReader(stream, delimiter='\t'):
            assert len(references[row['case']]) == 1
            references[row['case']] = [row]
    records = []
    def run(name, command, expected=0):
        began = time.monotonic()
        with (out/(name+'.stdout')).open('xb') as so, (out/(name+'.stderr')).open('xb') as se:
            process = subprocess.run(command, cwd=root, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=120)
        row = dict(name=name, command=command, exit_code=process.returncode, expected_exit=expected, seconds=round(time.monotonic()-began, 3))
        (out/(name+'.PROCESS.json')).write_text(json.dumps(row, indent=2)+'\n', encoding='utf-8')
        records.append(row)
        assert process.returncode == expected, 'Inspect retained '+name+' diagnostics'
        return row
    checked = 0
    for name, rows in cases.items():
        source = out/(name+'.tsv')
        source.write_text('group\tvalue\n'+''.join('g'+row['group']+'\t'+row['value']+'\n' for row in rows), encoding='utf-8', newline='\n')
        target = out/name
        run(name, prefix+['--input', str(source), '--confidence', rows[0]['confidence'], '--out', str(target)])
        assert (target/'input.tsv').read_bytes() == source.read_bytes()
        assert json.loads((target/'COMPLETED.json').read_bytes())['status'] == 'PASS_NATIVE_TUKEY_KRAMER'
        manifest = json.loads((target/'manifest.json').read_bytes())
        for filename, identity in manifest.items():
            assert pin(target/filename) == identity
        with (target/'comparisons.tsv').open(encoding='utf-8', newline='') as stream:
            actual = {(row['first_group'], row['second_group']):row for row in csv.DictReader(stream, delimiter='\t')}
        assert len(actual) == len(references[name])
        for reference in references[name]:
            found = actual[('g'+reference['first'], 'g'+reference['second'])]
            for field, expected_field in [('difference_second_minus_first', 'difference'), ('standard_error', 'standard_error'), ('studentized_range_q', 'q'), ('adjusted_p', 'adjusted_p'), ('lower', 'lower'), ('upper', 'upper')]:
                expected = float(reference[expected_field])
                assert abs(float(found[field])-expected) <= 2e-10+2e-10*abs(expected), (name, field, reference)
            checked += 1
        print('PASS packaged '+name, flush=True)
    # Real executable refusal paths; no original scientific program is mocked.
    source = out/'invalid-value.tsv'
    source.write_text('group\tvalue\na\t1\na\t2\nb\t3\nb\tNaN\n', encoding='utf-8')
    run('invalid-value', prefix+['--input', str(source), '--out', str(out/'invalid-value')], 2)
    assert not (out/'invalid-value/COMPLETED.json').exists()
    run('missing-input', prefix+['--input', str(out/'absent.tsv'), '--out', str(out/'missing-input')], 3)
    assert not (out/'missing-input').exists()
    run('invalid-confidence', prefix+['--input', str(out/'balanced.tsv'), '--confidence', '1', '--out', str(out/'invalid-confidence')], 2)
    assert not (out/'invalid-confidence/COMPLETED.json').exists()
    retained = pin(out/'balanced/COMPLETED.json')
    run('existing-output', prefix+['--input', str(out/'balanced.tsv'), '--out', str(out/'balanced')], 2)
    assert pin(out/'balanced/COMPLETED.json') == retained
    source = out/'numerical-zero.tsv'
    source.write_text('group\tvalue\n'+''.join('a\t'+str(-1 if i%2==0 else 1)+'\n' for i in range(512))+''.join('b\t'+str(99 if i%2==0 else 101)+'\n' for i in range(512)), encoding='utf-8')
    run('numerical-zero', prefix+['--input', str(source), '--out', str(out/'numerical-zero')])
    assert (out/'numerical-zero/NUMERIC-WARNING.txt').is_file()
    assert json.loads((out/'numerical-zero/analysis.json').read_bytes())['numerical_zero_adjusted_p_observed']
    source = out/'finite-tiny-tail.tsv'
    source.write_text('group\tvalue\na\t1\na\t2\na\t3\nb\t1000001\nb\t1000002\nb\t1000003\n', encoding='utf-8')
    run('finite-tiny-tail', prefix+['--input', str(source), '--out', str(out/'finite-tiny-tail')])
    with (out/'finite-tiny-tail/comparisons.tsv').open(encoding='utf-8', newline='') as stream:
        tiny = float(next(csv.DictReader(stream, delimiter='\t'))['adjusted_p'])
    assert abs(tiny/2.6666666666548094e-24-1) < 5e-8
    assert not (out/'finite-tiny-tail/NUMERIC-WARNING.txt').exists()
    source = out/'positive-integral-tail.tsv'
    source.write_text('group\tvalue\na\t-1\na\t1\nb\t999999\nb\t1000001\nc\t-1000001\nc\t-999999\n', encoding='utf-8')
    run('positive-integral-tail', prefix+['--input', str(source), '--out', str(out/'positive-integral-tail')])
    report = json.loads((out/'positive-integral-tail/analysis.json').read_bytes())
    assert report['positive_tail_evaluations'] >= 3
    with (out/'positive-integral-tail/comparisons.tsv').open(encoding='utf-8', newline='') as stream:
        for row in csv.DictReader(stream, delimiter='\t'):
            # The general-family route must retain a finite positive small tail.
            assert 0 < float(row['adjusted_p']) < 1e-12
    assert checked == 57 and len(cases) == 10
    value = dict(status='PASS_PACKAGED_NATIVE_TUKEY_KRAMER_ALL57_R_COMPARISONS_TAILS_AND_REFUSALS', reader=pin(args.jar), cases=10, comparisons=checked, oracle_tukeyhsd_pairs=55, oracle_exact_student_t_pairs=2, tail_controls=3, refusal_controls=4, processes=records, r_required_for_native_analysis=False, source_fixtures_unchanged=True)
    (out/'COMPLETED.json').write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
    print(value['status'], flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
