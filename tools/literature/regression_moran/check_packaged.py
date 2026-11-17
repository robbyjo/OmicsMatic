#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the real native command against every independent original-spdep fixture."""
from pathlib import Path
import argparse
import csv
import gzip
import hashlib
import json
import os
import subprocess
import time

if not __debug__:
    raise RuntimeError('Keep scientific reference assertions enabled; rerun without -O')


def pin(path):
    return dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def scalar(value):
    while isinstance(value, list):
        assert len(value) == 1
        value = value[0]
    return float(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['java', 'jar', 'out']:
        parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--wsl-distro', help='Additionally run Linux Java with PATH empty in this existing Windows WSL distribution')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    fixtures = root/'src/test/resources/org/jlinalg/spatial/regression-moran'
    provenance = json.loads((fixtures/'PROVENANCE.json').read_bytes())
    fixture = fixtures/'original-spdep-1.4-1.json.gz'
    assert pin(fixture) == provenance['fixture']
    data = gzip.decompress(fixture.read_bytes())
    assert dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest()) == provenance['uncompressed']
    reference = json.loads(data)
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    jar = args.jar.resolve(strict=True)
    prefix = [str(args.java.resolve(strict=True)), '-Xmx512m', '-jar', str(jar), 'monocle-tools', 'MORAN-LM']
    processes, cases, inputs = [], [], []

    def run(name, command, expected=0):
        began = time.monotonic()
        with (out/(name+'.stdout')).open('xb') as so, (out/(name+'.stderr')).open('xb') as se:
            child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
            (out/'RUNNING.json').write_text(json.dumps(dict(name=name, pid=child.pid, command=command), indent=2)+'\n')
            child.wait(timeout=120)
        record = dict(name=name, command=command, exit_code=child.returncode, expected_exit=expected, seconds=round(time.monotonic()-began, 3))
        processes.append(record)
        (out/(name+'.PROCESS.json')).write_text(json.dumps(record, indent=2)+'\n')
        assert child.returncode == expected, name+': inspect retained actual-process diagnostics'

    def check(target, case):
        assert json.loads((target/'COMPLETED.json').read_bytes())['status'] == 'PASS_NATIVE_REGRESSION_RESIDUAL_MORAN'
        report = json.loads((target/'analysis.json').read_bytes())
        assert report['result_admitted'] and report['observations'] == case['observations']
        assert report['effective_observations'] == case['effective_n'] and report['original_outgoing_isolates'] == case['outgoing_isolates']
        assert report['model_rank'] == case['rank']
        rows = json.loads((target/'tests.json').read_bytes())
        assert len(rows) == len(case['genes']) == 3
        with (target/'tests.tsv').open(encoding='utf-8', newline='') as stream:
            table = list(csv.DictReader(stream, delimiter='\t'))
        for index, (row, gene) in enumerate(zip(rows, case['genes'])):
            assert row['gene_id'] == (' gene"β ' if index == 0 else gene['id']) == table[index]['gene_id']
            for field, source in [('morans_I', 'moran_I'), ('expectation', 'expectation'), ('variance', 'variance'), ('z', 'statistic'), ('p_value', 'p_value'), ('log_p_value', 'log_p_value'), ('q_value', 'q_value')]:
                expected = scalar(gene['expected'][source])
                assert abs(float(row[field])-expected) <= 1e-10+1e-11*abs(expected), (case['id'], gene['id'], field, expected, row[field])
                if field in ['p_value', 'q_value']:
                    assert (expected == 0) == (float(row[field]) == 0), 'Numeric-zero classification differs'
        if any(float(row['p_value']) == 0 for row in rows):
            assert (target/'NUMERIC-WARNING.txt').is_file() and report['numerical_zero_probability_observed']
        manifest = json.loads((target/'manifest.json').read_bytes())
        for name, identity in manifest.items():
            assert pin(target/name) == identity
        return len(rows)

    checked = 0
    for index, case in enumerate(reference['cases']):
        name = f'case-{index:02d}'
        folder = out/name
        folder.mkdir()
        cells = [' cell"β ', 'NA']+['C'+str(i) for i in range(2, case['observations'])]
        design = ('\ufeff' if index == 0 else '')+'cell_id\t'+'\t'.join('X'+str(i) for i in range(case['rank']))+'\n'
        design += ''.join(cells[i]+'\t'+'\t'.join(format(float(v), '.17g') for v in row)+'\n' for i, row in enumerate(case['design']))
        weights = 'from_cell\tto_cell\tweight\n'+''.join(cells[row['from']]+'\t'+cells[row['to']]+'\t'+format(float(row['weight']), '.17g')+'\n' for row in case['weights'])
        expression = 'gene_id\t'+'\t'.join(cells)+'\n'+''.join((' gene"β ' if i == 0 else gene['id'])+'\t'+'\t'.join(format(float(v), '.17g') for v in gene['response'])+'\n' for i, gene in enumerate(case['genes']))
        for filename, text in [('design.tsv', design), ('weights.tsv', weights), ('expression.tsv', expression)]:
            (folder/filename).write_text(text, encoding='utf-8', newline='\n')
        request = dict(schema_version=1, design=str(folder/'design.tsv'), weights=str(folder/'weights.tsv'), expression=str(folder/'expression.tsv'),
            response_units='prepared-linear-response', response_provenance='Complete seeded synthetic original-spdep fixture '+case['id'],
            population_declaration='All '+str(case['observations'])+' synthetic cells and3 genes', zero_policy=case['zero_policy'], alternative=case['alternative'], out=str(folder/'result'))
        path = folder/'request.json'
        path.write_text(json.dumps(request, indent=2, ensure_ascii=False)+'\n', encoding='utf-8', newline='\n')
        run(name, prefix+['--request', str(path)])
        checked += check(folder/'result', case)
        for filename in ['design.tsv', 'weights.tsv', 'expression.tsv']:
            assert (folder/filename).read_bytes() == (folder/'result/inputs'/filename).read_bytes()
        cases.append(dict(name=name, source_case=case['id'], genes=3))
        inputs.append(request)
        print('PASS '+case['id'], flush=True)

    # Actual executable refusal paths: preserve completed outputs and never admit a prefix.
    base = inputs[0]
    def refuse(name, request, code, exists=True):
        request = dict(request, out=str(out/name))
        path = out/(name+'.json')
        path.write_text(json.dumps(request, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
        run(name, prefix+['--request', str(path)], code)
        assert not (out/name/'COMPLETED.json').exists()
        if exists:
            assert json.loads((out/name/'analysis.json').read_bytes())['result_admitted'] is False
        else:
            assert not (out/name).exists()
    refuse('missing-input', dict(base, weights=str(out/'absent.tsv')), 3, False)
    refuse('unknown-field', dict(base, formula='response~sex'), 2, False)
    expression = out/'late-zero-residual.tsv'
    expression.write_bytes(Path(base['expression']).read_bytes()+('bad'+'\t1'*reference['cases'][0]['observations']+'\n').encode())
    refuse('late-zero-residual', dict(base, expression=str(expression)), 2)
    assert not (out/'late-zero-residual/tests.tsv').exists()
    weights = out/'duplicate-weights.tsv'
    original = Path(base['weights']).read_text(encoding='utf-8')
    weights.write_text(original+original.splitlines()[1]+'\n', encoding='utf-8', newline='\n')
    refuse('duplicate-weights', dict(base, weights=str(weights)), 2)
    case = reference['cases'][0]
    bound = 64*case['observations']*case['rank']**2+16*len(case['weights'])*case['rank']+32*(case['observations']+len(case['weights']))
    refuse('whole-family-operation-bound', dict(base, maximum_operations=bound), 2)
    assert 'Complete response family' in (out/'whole-family-operation-bound/FAILURE.txt').read_text()
    retained = pin(out/'case-00/result/COMPLETED.json')
    run('existing-output', prefix+['--request', str(out/'case-00/request.json')], 2)
    assert pin(out/'case-00/result/COMPLETED.json') == retained

    linux_checks = []
    if args.wsl_distro:
        if os.name != 'nt':
            parser.error('--wsl-distro is for a Windows coordinator; Linux users pass their local --java directly')
        def linux(path):
            path = Path(path).resolve()
            return '/mnt/'+path.drive[0].lower()+'/'+path.as_posix()[3:]
        for index in [18, 35]:
            name = 'linux-case-'+str(index)
            request = dict(inputs[index])
            for key in ['design', 'weights', 'expression']:
                request[key] = linux(request[key])
            request['out'] = linux(out/name)
            path = out/(name+'.json')
            path.write_text(json.dumps(request, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
            command = [str(Path(os.environ['SystemRoot'])/'System32/wsl.exe'), '-d', args.wsl_distro, '--exec', '/usr/bin/env', 'PATH=', '/usr/bin/java', '-Xmx512m', '-jar', linux(jar), 'monocle-tools', 'MORAN-LM', '--request', linux(path)]
            run(name, command)
            check(out/name, reference['cases'][index])
            linux_checks.append(dict(case=reference['cases'][index]['id'], PATH='', comparisons=3))
    assert checked == 108 and len(cases) == 36
    value = dict(status='PASS_PACKAGED_NATIVE_RESIDUAL_MORAN_ALL108_ORIGINAL_SPDEP_COMPARISONS_AND_REFUSALS', reader=pin(jar), cases=cases,
        comparisons=checked, refusal_controls=6, linux_checks=linux_checks, processes=processes, source_fixture=provenance['fixture'], original_R_required_for_native_analysis=False)
    (out/'COMPLETED.json').write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8', newline='\n')
    print(value['status'], flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
