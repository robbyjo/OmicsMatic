#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay five complete bundles made on the other host; preserve every process record."""
import argparse, datetime, hashlib, json, os, shutil, subprocess
from pathlib import Path

NAMES = ['flags', 'sample-state', 'overlapping-exons-origin', 'custom-keys', 'raw-control-identifiers']

def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())

def tree(folder):
    return {p.relative_to(folder).as_posix(): dict(kind='file', **pin(p)) if p.is_file() else dict(kind='directory')
            for p in sorted(folder.rglob('*'))}

def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['java', 'jar', 'htsjdk', 'bundles', 'out']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(exist_ok=False)
    shutil.copyfile(__file__, out / 'qualify_cross_host.py')
    command = [str(args.java.resolve()), '-cp', str(args.jar.resolve()) + os.pathsep + str(args.htsjdk.resolve()),
               'org.jlinalg.cli.TpmCalculatorCli']
    write(out / 'SOURCE.json', dict(java=str(args.java.resolve()), application=pin(args.jar),
          htsjdk=pin(args.htsjdk), producer=pin(Path(__file__)), platform=os.name))
    results = []
    for name in NAMES:
        folder = out / name
        folder.mkdir()
        source = args.bundles / name / 'analysis'
        before = tree(source)
        incoming = folder / 'incoming'
        shutil.copytree(source, incoming)
        assert tree(incoming) == before == tree(source), name
        write(folder / 'SOURCE-INVENTORY.json', before)
        argv = command + ['verify', '--bundle', str(incoming), '--out', str(folder / 'replay')]
        with (folder / 'process.stdout').open('xb') as so, (folder / 'process.stderr').open('xb') as se:
            child = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
            write(folder / 'LIVE.json', dict(pid=child.pid, command=argv))
            try:
                code = child.wait(timeout=240)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
                raise RuntimeError('Owned cross-host qualification process timed out; no replacement started')
        write(folder / 'PROCESS.json', dict(pid=child.pid, command=argv, exit_code=code))
        assert code == 0, name
        completed = json.loads((folder / 'replay/COMPLETED.json').read_bytes())
        assert completed['saved_replay_verified'] and not completed['result_admitted'], name
        assert tree(source) == before == tree(incoming), name
        results.append(dict(name=name, complete_file_and_directory_copy_equal=True,
                            captured_input_replay_verified=True, source_bundle_unchanged=True))
        print('CROSS_HOST_COMPLETE_BUNDLE_REPLAY_PASS', name, flush=True)
    write(out / 'COMPLETED.json', dict(status='PASS_FIVE_OTHER_HOST_COMPLETE_BUNDLE_REPLAYS',
          checked_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), platform=os.name,
          cases=results, actual_processes=5, paper_reproduced=False, whole_goal_complete=False))

if __name__ == '__main__':
    main()
