# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original MEME site/matrix/scan controls: edges, reverse strands, priors, backgrounds and weights."""
import argparse
import json
import os
from pathlib import Path
import random
import shutil
import signal
import subprocess
import tempfile
from collect_meme_discovery_references import BASE, limits, pin, tree, write


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-proof', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    original = json.loads(args.build_proof.read_bytes())
    assert original['status'] in {'built-original-historical-MEME-reference-not-scientifically-admitted',
                                  'built-original-Linux-MEME-Suite-reference-not-scientifically-admitted'}
    version = original['version']
    assert version in {'4.9.1', '5.5.4', '5.5.9'}
    out = args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists()
    home = Path(original['prefix'])
    before = tree(home)
    assert {name: {k: v[k] for k in ['bytes', 'sha256']} for name, v in before.items()} == original['installed_files']
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-sites-', dir='/var/tmp'))
    area.chmod(0o700)
    inputs = area / 'inputs'
    inputs.mkdir()
    out.mkdir(parents=True)
    records = []
    rng = random.Random(420731)
    word = 'GACGTAGTCA'
    for i in range(60):
        size = 96 + i % 5
        sequence = list(''.join(rng.choice('ACGT') for _ in range(size)))
        start = [0, 31, size - len(word)][i % 3]
        oriented = word if i % 2 == 0 else word.translate(str.maketrans('ACGT', 'TGCA'))[::-1]
        if i % 7:
            sequence[start:start + len(word)] = oriented
        records.append(dict(name='edge-' + str(i), bases=''.join(sequence), weight=1,
                            planted_start=start if i % 7 else None, planted_strand='plus' if i % 2 == 0 else 'minus'))
    (inputs / 'positive.fa').write_text(''.join('>' + r['name'] + '\n' + r['bases'] + '\n' for r in records))
    weighted = [dict(r, weight=(i % 4 + 1) / 4) for i, r in enumerate(records)]
    (inputs / 'weighted.fa').write_text('>WEIGHTS ' + ' '.join(str(r['weight']) for r in weighted) + '\n' +
                                      ''.join('>' + r['name'] + '\n' + r['bases'] + '\n' for r in weighted))
    uniform = [.25] * 4
    asymmetric = [.123456, .234567, .345678, .296299]
    for name, values in [('uniform', uniform), ('asymmetric', asymmetric)]:
        (inputs / (name + '.bg')).write_text(''.join(f'{b} {p}\n' for b, p in zip('ACGT', values)))
    before_inputs = tree(inputs)
    report = dict(status='collecting-original-MEME-site-references-not-admitted', version=version,
                  edition=original.get('edition', version), linux_area=str(area), runtime_home=str(home),
                  build_proof=pin(args.build_proof), collector=pin(Path(__file__)), processes=[],
                  original_source_build_unchanged=True, implementation_credit=False, analysis_admitted=False)
    write(out / 'OWNER.json', report)
    (out / 'COLLECTOR-SOURCE.py').write_bytes(Path(__file__).read_bytes())
    (out / 'HELPER-SOURCE.py').write_bytes(Path(__file__).with_name('collect_meme_discovery_references.py').read_bytes())
    for name, filename, background, beta, reverse, population in [
        ('edge-both', 'positive.fa', 'uniform', .01, True, records),
        ('asymmetric-both', 'positive.fa', 'asymmetric', .5, True, records),
        ('asymmetric-forward', 'positive.fa', 'asymmetric', 20, False, records),
        ('weighted-both', 'weighted.fa', 'uniform', .01, True, weighted),
    ]:
        folder = area / name
        folder.mkdir()
        command = [str(home / 'bin/meme'), str(inputs / filename), '-dna', '-mod', 'zoops', '-nmotifs', '2',
                   '-w', '10', '-prior', 'dirichlet', '-b', str(beta), '-bfile', str(inputs / (background + '.bg')),
                   '-nostatus', '-maxsize', '100000', '-o', str(folder / 'result')]
        if reverse:
            command.append('-revcomp')
        if version != '4.9.1':
            command += ['-seed', '17', '-searchsize', '0']
        env = dict(PATH=str(home / 'bin') + ':' + str(home / ('libexec/meme-' + version)) + ':/usr/bin:/bin',
                   HOME=str(area), TMPDIR=str(area), LC_ALL='C', PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1')
        with (folder / 'stdout.log').open('xb') as stdout, (folder / 'stderr.log').open('xb') as stderr:
            process = subprocess.Popen(command, cwd=folder, env=env, stdin=subprocess.DEVNULL,
                                       stdout=stdout, stderr=stderr, start_new_session=True, preexec_fn=limits)
            timeout = False
            try:
                code = process.wait(timeout=240)
            except subprocess.TimeoutExpired:
                timeout = True
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
                code = process.returncode
        supplied = uniform if background == 'uniform' else asymmetric
        resolved = [(supplied[b] + supplied[3 - b]) / 2 for b in range(4)] if reverse else supplied
        expected = dict(version=version, mode='zoops', motifs=2, minw=10, maxw=10,
                        seed=0 if version == '4.9.1' else 17, beta=beta, both_strands=reverse,
                        resolved_background=resolved, input_records=population)
        write(folder / 'PREPARED-INPUTS.json', expected)
        row = dict(name=name, command=command, environment=env, exit_code=code, timed_out=timeout,
                   prepared_inputs=pin(folder / 'PREPARED-INPUTS.json'))
        write(folder / 'PROCESS.json', row)
        report['processes'].append(dict(**row, complete_family=tree(folder)))
        write(out / 'PROGRESS.json', report)
        print(report['edition'] + ' ' + name + ': exit ' + str(code), flush=True)
    report.update(status='collected-original-MEME-site-references-not-admitted',
                  successful_processes=sum(p['exit_code'] == 0 and not p['timed_out'] for p in report['processes']),
                  unchanged_inputs_and_runtime=tree(inputs) == before_inputs and tree(home) == before,
                  scope='Synthetic controls only. Exact caller-prepared inputs, source weights and resolved strand backgrounds are retained. Complete numerical/site/score/output-family admission remains separate.')
    shutil.copytree(area, out / 'captured')
    write(out / 'CAPTURE-PINS.json', tree(out / 'captured'))
    write(out / 'COMPLETED.json', report)
    assert report['unchanged_inputs_and_runtime']


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original reference identity checks')
    main()
