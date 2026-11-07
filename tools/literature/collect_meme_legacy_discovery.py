# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture unchanged original MEME 4.9.1, DREME and MEME-ChIP discovery controls; no admission."""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
from collect_meme_discovery_references import BASE, limits, pin, tree, write


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--recovered-build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    proof = json.loads(args.recovered_build.read_bytes())
    assert proof['status'] == 'built-original-historical-MEME-reference-not-scientifically-admitted'
    assert proof['version'] == '4.9.1' and proof['edition'] in {'4.9.1-base', '4.9.1-patch2'}
    out = args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists()
    home = Path(proof['prefix'])
    before = tree(home)
    assert {n: {k: v[k] for k in ['bytes', 'sha256']} for n, v in before.items()} == proof['installed_files']
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-legacy-controls-', dir='/var/tmp'))
    area.chmod(0o700)
    out.mkdir(parents=True)
    source = BASE / 'original-controls-2/captured/inputs'
    input_before = tree(source)
    inputs = area / 'inputs'
    shutil.copytree(source, inputs)
    record = dict(status='collecting-original-legacy-discovery-not-admitted', edition=proof['edition'], version='4.9.1',
                  linux_area=str(area), runtime_home=str(home), recovered_build=pin(args.recovered_build), processes=[],
                  collector=pin(Path(__file__)), helper=pin(Path(__file__).with_name('collect_meme_discovery_references.py')),
                  source_inputs=input_before, implementation_credit=False, analysis_admitted=False)
    write(out / 'OWNER.json', record)
    (out / 'COLLECTOR-SOURCE.py').write_bytes(Path(__file__).read_bytes())
    (out / 'HELPER-SOURCE.py').write_bytes(Path(__file__).with_name('collect_meme_discovery_references.py').read_bytes())
    jobs = []
    for mode in ['oops', 'zoops', 'anr']:
        for rc in [False, True]:
            name = 'meme-' + mode + ('-both' if rc else '-forward')
            command = [str(home / 'bin/meme'), str(inputs / (mode + '.fa')), '-dna', '-mod', mode,
                       '-nmotifs', '2', '-w', '6', '-bfile', str(inputs / 'background.txt'),
                       '-nostatus', '-maxsize', '100000', '-o', str(area / name / 'result')]
            if rc:
                command.append('-revcomp')
            jobs.append((name, command))
    for supplied in [True, False]:
        name = 'dreme-supplied' if supplied else 'dreme-shuffled'
        command = [str(home / 'bin/dreme'), '-p', str(inputs / 'zoops.fa'), '-m', '2', '-e', '0.05',
                   '-s', '17', '-o', str(area / name / 'result')]
        if supplied:
            command += ['-n', str(inputs / 'negative.fa')]
        jobs.append((name, command))
    name = 'meme-chip-historical-options'
    command = [str(home / 'bin/meme-chip'), '-oc', str(area / name / 'result'), '-db', str(inputs / 'motifs.meme'),
               '-bfile', str(inputs / 'background.txt'), '-ccut', '80', '-nmeme', '600', '-meme-mod', 'zoops',
               '-meme-minw', '6', '-meme-maxw', '18', '-meme-maxsize', '50000000', '-meme-nmotifs', '2',
               '-dreme-e', '0.00001', '-dreme-m', '20', '-centrimo-maxreg', '30', str(inputs / 'oops.fa')]
    jobs.append((name, command))
    for name, command in jobs:
        folder = area / name
        folder.mkdir()
        env = dict(PATH=str(home / 'bin') + ':/usr/bin:/bin', HOME=str(area), TMPDIR=str(area), LC_ALL='C',
                   PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1')
        with (folder / 'stdout.log').open('xb') as stdout, (folder / 'stderr.log').open('xb') as stderr:
            process = subprocess.Popen(command, cwd=folder, env=env, stdin=subprocess.DEVNULL, stdout=stdout,
                                       stderr=stderr, start_new_session=True, preexec_fn=limits)
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
        print(proof['edition'] + ' ' + name + ': exit ' + str(code), flush=True)
    record.update(status='collected-original-legacy-discovery-not-admitted',
                  unchanged_inputs_and_runtime=tree(home) == before and tree(source) == input_before,
                  successful_processes=sum(p['exit_code'] == 0 and not p['timed_out'] for p in record['processes']),
                  paper_boundary='P155 patch level unspecified. P013 suite edition unspecified. Original 4.9.1 MEME has no -seed option: its defaults are retained, not silently mapped to seed17 in a newer edition. The pipeline uses historical quoted option names on synthetic 80bp controls, not the paper 200bp peaks or historical author dataset. Every failed or incomplete nested output remains unadmitted.')
    shutil.copytree(area, out / 'captured')
    write(out / 'CAPTURE-PINS.json', tree(out / 'captured'))
    write(out / 'COMPLETED.json', record)
    assert record['unchanged_inputs_and_runtime']


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original reference identity checks')
    main()
