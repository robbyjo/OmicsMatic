#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual directory disappearance and actual denied-directory controls for live/static scans."""
from pathlib import Path
import argparse, os, subprocess, sys, json
sys.dont_write_bytecode = True
from Runtime import HERE, walk_error, live_walk_error, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--private-test-root', required=True, type=Path)
    args = parser.parse_args()
    if sys.platform != 'linux' or os.geteuid() != 0:
        print('SKIP3: permitted Linux root needed to run the denied-directory child as UID 65534', flush=True)
        return 3
    out, root = args.out.absolute(), args.private_test_root.absolute()
    if out != out.resolve() or root != root.resolve() or any(
            a.is_relative_to(b) or b.is_relative_to(a) for a, b in [(out, root), (out, HERE), (root, HERE)]):
        raise ValueError('Use NEW separate canonical test paths')
    out.mkdir(exist_ok=False)
    root.mkdir(mode=0o755, exist_ok=False)
    results = {}
    for name, callback in [('static', walk_error), ('live', live_walk_error)]:
        directory = root / name
        directory.mkdir(mode=0o755)
        transient = directory / 'new-empty-temporary-directory'
        transient.mkdir()
        iterator = os.walk(directory, followlinks=False, onerror=callback)
        first = next(iterator)
        assert transient.name in first[1] and not list(transient.iterdir())
        # Remove only this newly created, verified-empty test directory.
        assert transient.resolve().parent == directory.resolve() and directory.parent == root
        transient.rmdir()
        try:
            tail = list(iterator)
            outcome = 'continued'
            assert not tail
        except FileNotFoundError:
            outcome = 'FileNotFoundError'
        assert outcome == ('FileNotFoundError' if name == 'static' else 'continued')
        results[name + '_actual_disappearance'] = outcome
    denied_root = root / 'denied-control'
    denied_root.mkdir(mode=0o755)
    denied = denied_root / 'denied'
    denied.mkdir(mode=0o755)
    (denied / 'required-file.txt').write_text('Required file\n', encoding='utf-8')
    denied.chmod(0)
    code = '''import os,sys,json
sys.dont_write_bytecode=True
sys.path.insert(0,sys.argv[1])
from Runtime import live_walk_error
try:
 list(os.walk(sys.argv[2],followlinks=False,onerror=live_walk_error))
except PermissionError as error:
 print(json.dumps(dict(uid=os.geteuid(),error_type=type(error).__name__,filename=error.filename)))
 raise SystemExit(0)
raise SystemExit("FAIL: live scan silently omitted an unreadable subtree")
'''
    command = [sys.executable, '-B', '-c', code, str(HERE), str(denied_root)]
    with (out / 'denied.stdout').open('xb') as stdout, (out / 'denied.stderr').open('xb') as stderr:
        child = subprocess.run(command, cwd='/', stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                               user=65534, group=65534, extra_groups=[])
    save(out / 'PROCESS.json', dict(command=command, exit_code=child.returncode))
    assert child.returncode == 0
    permission = json.loads((out / 'denied.stdout').read_bytes())
    assert permission['uid'] == 65534 and permission['error_type'] == 'PermissionError'
    save(out / 'COMPLETED.json', dict(status='PASS_REAL_STATIC_LIVE_DISAPPEARANCE_AND_PERMISSION_CONTROLS',
        results=results,live_denied_directory=permission,
        sources={name: pin(HERE / name) for name in ['Runtime.py', 'run.py', 'test_live_inventory.py']},
        removed_only_new_verified_empty_test_directories=True,analysis_executed=False,new_family_credit=0))
    print('PASS_REAL_STATIC_LIVE_DISAPPEARANCE_AND_PERMISSION_CONTROLS', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
