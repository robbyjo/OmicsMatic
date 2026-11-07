# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original edition-specific discovery families, including failures and unsupported tests."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import shutil
import signal
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def tree(root):
    rows = {}
    for p in sorted(root.rglob('*')):
        if p.is_symlink() or not (p.is_file() or p.is_dir()):
            raise ValueError('Unsupported original reference node: ' + str(p))
        if p.is_file():
            rows[p.relative_to(root).as_posix()] = dict(mode=p.stat().st_mode & 0o777, **pin(p))
    return rows


def limits():
    resource.setrlimit(resource.RLIMIT_AS, (4 * 1024**3, 4 * 1024**3))
    resource.setrlimit(resource.RLIMIT_CPU, (180, 180))
    resource.setrlimit(resource.RLIMIT_FSIZE, (128 * 1024**2, 128 * 1024**2))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-proof', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    proof = json.loads(args.build_proof.read_bytes())
    if proof['status'] not in {'built-original-historical-MEME-reference-not-scientifically-admitted',
                              'built-original-Linux-MEME-Suite-reference-not-scientifically-admitted'}:
        raise ValueError('Require a completed exact original source build, not a partial installation')
    version = proof['version']
    if version not in {'5.5.4', '5.5.9'}:
        raise ValueError('This collector has no validated legacy argument translation; preserve the old edition separately')
    out = args.out.resolve()
    if not out.is_relative_to(BASE.resolve()) or out.exists():
        raise ValueError('Require a NEW owned reference output')
    home = Path(proof['prefix'])
    runtime_before = tree(home)
    if {n: {k: v[k] for k in ['bytes', 'sha256']} for n, v in runtime_before.items()} != proof['installed_files']:
        raise ValueError('Complete original installation changed')
    source = BASE / 'original-controls-2/captured/inputs'
    input_before = tree(source)
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-discovery-', dir='/var/tmp'))
    area.chmod(0o700)
    out.mkdir(parents=True)
    record = dict(status='collecting-original-discovery-reference-not-admitted', version=version,
                  runtime_home=str(home), linux_area=str(area), build_proof=pin(args.build_proof),
                  collector=pin(Path(__file__)), input_pins=input_before, processes=[],
                  analysis_admitted=False, implementation_credit=False)
    write(out / 'OWNER.json', record)
    inputs = area / 'inputs'
    shutil.copytree(source, inputs)
    lines = (inputs / 'negative.fa').read_text().splitlines()
    (inputs / 'short-negative.fa').write_text('\n'.join(s if s.startswith('>') else s[:40] for s in lines) + '\n')
    jobs = []
    for mode in ['oops', 'zoops', 'anr']:
        for rc in [False, True]:
            name = 'meme-' + mode + ('-both' if rc else '-forward')
            command = [str(home / 'bin/meme'), str(inputs / (mode + '.fa')), '-dna', '-mod', mode,
                       '-nmotifs', '2', '-w', '6', '-bfile', str(inputs / 'background.txt'), '-seed', '17',
                       '-nostatus', '-maxsize', '100000', '-searchsize', '0', '-o', str(area / name / 'result')]
            if rc:
                command.append('-revcomp')
            jobs.append((name, command))
    for name, options in [
        ('streme-supplied', ['--n', str(inputs / 'negative.fa'), '--hofract', '0.25', '--nmotifs', '2']),
        ('streme-shuffled', ['--hofract', '0.25', '--nmotifs', '2']),
        ('streme-no-holdout', ['--n', str(inputs / 'negative.fa'), '--hofract', '0', '--nmotifs', '2']),
        ('streme-forward', ['--n', str(inputs / 'negative.fa'), '--hofract', '0.25', '--nmotifs', '1', '--norc']),
        ('streme-binomial', ['--n', str(inputs / 'short-negative.fa'), '--hofract', '0.25', '--nmotifs', '2']),
        ('streme-central-distance', ['--objfun', 'cd', '--hofract', '0.25', '--nmotifs', '2']),
        ('streme-no-motifs', ['--n', str(inputs / 'negative.fa'), '--hofract', '0.25', '--thresh', '1e-100', '--patience', '1']),
        ('streme-invalid-width', ['--minw', '1', '--maxw', '2', '--nmotifs', '1']),
    ]:
        command = [str(home / 'bin/streme'), '--p', str(inputs / 'zoops.fa'), '--dna', '--order', '0',
                   '--seed', '17', '--minw', '6', '--maxw', '8', '--o', str(area / name / 'result'), *options]
        jobs.append((name, command))
    staged_before = tree(inputs)
    for name, command in jobs:
        folder = area / name
        folder.mkdir()
        env = dict(PATH=str(home / 'bin') + ':' + str(home / ('libexec/meme-' + version)) + ':/usr/bin:/bin',
                   HOME=str(area), TMPDIR=str(area), LC_ALL='C', PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1')
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
        row = dict(name=name, command=command, environment=env, exit_code=code, timed_out=timeout)
        write(folder / 'PROCESS.json', row)
        record['processes'].append(dict(**row, complete_family=tree(folder)))
        write(out / 'PROGRESS.json', record)
        print(version + ' ' + name + ': exit ' + str(code), flush=True)
    unchanged = tree(home) == runtime_before and tree(source) == input_before and tree(inputs) == staged_before
    record.update(status='collected-original-discovery-references-not-admitted', unchanged_inputs_and_runtime=unchanged,
                  successful_processes=sum(p['exit_code'] == 0 and not p['timed_out'] for p in record['processes']),
                  required_action='Audit every original family and statistical/site interpretation. Unsupported objectives remain distinct. Failed processes admit no result. Exit0 alone is not analysis admission.')
    shutil.copytree(area, out / 'captured')
    write(out / 'CAPTURE-PINS.json', tree(out / 'captured'))
    write(out / 'COMPLETED.json', record)
    if not unchanged:
        raise RuntimeError('Original source/runtime/inputs changed; reject all references from this attempt')


if __name__ == '__main__':
    main()
