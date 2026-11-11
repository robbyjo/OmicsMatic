# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect complete original DNA discovery/comparison families; no project fit admission."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import resource
import shutil
import signal
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def census(area):
    rows = {}
    for path in sorted(area.rglob('*')):
        name = path.relative_to(area).as_posix()
        if path.is_symlink():
            rows[name] = dict(kind='symlink', target=os.readlink(path))
        elif path.is_dir():
            rows[name] = dict(kind='directory', mode=path.stat().st_mode & 0o777)
        elif path.is_file():
            rows[name] = dict(kind='file', mode=path.stat().st_mode & 0o777, **pin(path))
        else:
            raise ValueError('Unexpected original output node: ' + str(path))
    return rows


def limits():
    resource.setrlimit(resource.RLIMIT_AS, (4 * 1024**3, 4 * 1024**3))
    resource.setrlimit(resource.RLIMIT_CPU, (180, 180))
    resource.setrlimit(resource.RLIMIT_FSIZE, (1024**3, 1024**3))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-proof', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists()
    proof = json.loads(args.build_proof.read_bytes())
    assert proof['status'] == 'built-original-Linux-MEME-Suite-reference-not-scientifically-admitted' and proof['unchanged_original_C_and_H'] == 615
    home = Path(proof['prefix'])
    for name, expected in proof['installed_files'].items():
        assert pin(home / name) == expected, name
    runtime_before = census(home)
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-controls-', dir='/var/tmp'))
    area.chmod(0o700)
    out.mkdir(parents=True)
    write(out / 'OWNER.json', dict(linux_area=str(area), runtime_home=str(home), build_proof=pin(args.build_proof), source_collection_only=True))
    inputs = area / 'inputs'; inputs.mkdir()
    rng = random.Random(137)
    background = ''.join(base + ' 0.25\n' for base in 'ACGT')
    (inputs / 'background.txt').write_text(background, encoding='ascii')
    for name, sites in [('oops',1), ('zoops',1), ('anr',2), ('negative',0)]:
        records = []
        for i in range(80):
            sequence = list(''.join(rng.choice('ACGT') for _ in range(80)))
            count = sites if not (name == 'zoops' and i % 4 == 0) else 0
            for start in ([37] if count == 1 else [21,51] if count == 2 else []):
                sequence[start:start+6] = 'GACGTA'
            records.extend(['>' + name + '-' + str(i), ''.join(sequence)])
        (inputs / (name + '.fa')).write_text('\n'.join(records) + '\n', encoding='ascii')
    motifs = ['MEME version 5', '', 'ALPHABET= ACGT', '', 'strands: + -', '', 'Background letter frequencies', 'A 0.25 C 0.25 G 0.25 T 0.25', '']
    for name, consensus in [('planted','GACGTA'),('other','TTTGCA'),('similar','GACGTT')]:
        motifs.extend(['MOTIF ' + name, 'letter-probability matrix: alength= 4 w= 6 nsites= 80 E= 0'])
        motifs.extend(' '.join('0.97' if base == nucleotide else '0.01' for nucleotide in 'ACGT') for base in consensus)
        motifs.append('')
    (inputs / 'motifs.meme').write_text('\n'.join(motifs), encoding='ascii')
    input_pins = census(inputs)
    jobs = []
    for mode in ['oops','zoops','anr']:
        for reverse in [False, True]:
            name = 'meme-' + mode + ('-both' if reverse else '-forward')
            command = [str(home/'bin/meme'), str(inputs/(mode+'.fa')), '-dna', '-mod', mode, '-nmotifs', '2', '-w', '6', '-bfile', str(inputs/'background.txt'), '-seed', '17', '-nostatus', '-maxsize', '100000', '-searchsize', '0', '-o', str(area/name/'result')]
            if reverse:
                command.append('-revcomp')
            jobs.append((name, command))
    for distance in ['ed','kullback','pearson']:
        for reverse in [False, True]:
            name = 'tomtom-' + distance + ('-both' if reverse else '-forward')
            command = [str(home/'bin/tomtom'), '-dist', distance, '-motif-pseudo', '0.1', '-min-overlap', '1', '-thresh', '1', '-o', str(area/name/'result')]
            if not reverse:
                command.append('-norc')
            command += [str(inputs/'motifs.meme'), str(inputs/'motifs.meme')]
            jobs.append((name, command))
    for supplied in [False, True]:
        name = 'streme-' + ('supplied-control' if supplied else 'shuffled-control')
        command = [str(home/'bin/streme'), '--p', str(inputs/'zoops.fa'), '--dna', '--order', '0', '--seed', '17', '--hofract', '0.25', '--nmotifs', '2', '--minw', '6', '--maxw', '8', '--o', str(area/name/'result')]
        if supplied:
            command += ['--n', str(inputs/'negative.fa')]
        jobs.append((name, command))
    for supplied in [False, True]:
        name = 'centrimo-' + ('supplied-control' if supplied else 'uniform-position-null')
        command = [str(home/'bin/centrimo'), '--score', '1', '--ethresh', '1000000', '--maxreg', '30', '--bfile', str(inputs/'background.txt'), '--o', str(area/name/'result')]
        if supplied:
            command += ['--neg', str(inputs/'negative.fa')]
        command += [str(inputs/'oops.fa'), str(inputs/'motifs.meme')]
        jobs.append((name, command))
    results = []
    for name, command in jobs:
        folder = area/name; folder.mkdir()
        env = dict(PATH=str(home/'bin') + ':' + str(home/'libexec/meme-5.5.9') + ':/usr/bin:/bin', HOME=str(area), LC_ALL='C', TMPDIR=str(area), PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1')
        with (folder/'stdout.log').open('wb') as stdout, (folder/'stderr.log').open('wb') as stderr:
            process = subprocess.Popen(command, cwd=folder, env=env, stdout=stdout, stderr=stderr, start_new_session=True, preexec_fn=limits)
            timed_out = False
            try:
                code = process.wait(timeout=240)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
                code = process.returncode
        row = dict(name=name, command=command, environment=env, exit_code=code, timed_out=timed_out, source_collection_only=True)
        write(folder/'PROCESS.json', row)
        row['native_inventory'] = census(folder)
        write(out/(name+'-receipt.json'), row)
        results.append(row)
        print(name + ': exit ' + str(code), flush=True)
    unchanged = census(home) == runtime_before and census(inputs) == input_pins
    write(out/'NATIVE-INVENTORY.json', census(area))
    shutil.copytree(area, out/'captured')
    copied = census(out/'captured')
    native = census(area)
    assert set(copied) == set(native)
    for name, value in native.items():
        assert {k:v for k,v in copied[name].items() if k != 'mode'} == {k:v for k,v in value.items() if k != 'mode'}, name
    assert unchanged and census(home) == runtime_before and census(inputs) == input_pins
    result = dict(status='collected-original-MEME-suite-families-native-audit-pending', version='5.5.9', jobs=results, families=len(results),
                  successful_processes=sum(r['exit_code'] == 0 and not r['timed_out'] for r in results), runtime_and_inputs_unchanged=True,
                  source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))}, installed_build_proof=pin(args.build_proof),
                  scientific_runtime_admitted=False, implementation_credit=False,
                  required_action='Original process exit0 is not a validated fit. Audit every complete model, site, sequence, comparison and inferential field before Java/project admission. Preserve every failure and raw warning. Missing/forbidden dependencies skip dependent analysis; install only where permitted and retry with NEW paths. Generated controls do not establish biological calibration or historical paper reproduction.')
    write(out/'COMPLETED.json', result)
    print(json.dumps(dict(status=result['status'], families=result['families'], successful_processes=result['successful_processes'])), flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original evidence identity checks')
    main()
