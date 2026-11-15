#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real denied-directory control; drop child privileges rather than mock os.walk."""
from pathlib import Path
import argparse, json, os, subprocess, sys
sys.dont_write_bytecode = True
from Runtime import HERE, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--private-test-root', required=True, type=Path)
    args = parser.parse_args()
    if sys.platform != 'linux' or os.geteuid() != 0:
        print('SKIP3: control requires a permitted Linux root process to run a child as UID/GID 65534', flush=True)
        return 3
    out = args.out.absolute()
    test = args.private_test_root.absolute()
    if out != out.resolve() or test != test.resolve() or any(
            a.is_relative_to(b) or b.is_relative_to(a) for a, b in [(out, HERE), (test, HERE), (out, test)]):
        raise ValueError('Use NEW separate canonical control paths')
    out.mkdir(exist_ok=False)
    test.mkdir(mode=0o755, exist_ok=False)
    denied = test / 'denied'
    denied.mkdir(mode=0o755)
    (denied / 'must-not-be-omitted.txt').write_text('Required inventory member\n', encoding='utf-8')
    denied.chmod(0)
    code = '''import sys,json
sys.dont_write_bytecode=True
sys.path.insert(0,sys.argv[1])
from Runtime import inventory
try:
 inventory(sys.argv[2])
except PermissionError as error:
 print(json.dumps(dict(status="PASS_REAL_DENIED_SUBTREE_REJECTED",uid=__import__("os").geteuid(),error_type=type(error).__name__,filename=error.filename)))
 raise SystemExit(0)
raise SystemExit("FAIL: unreadable subtree was silently omitted")
'''
    command = [sys.executable, '-B', '-c', code, str(HERE), str(test)]
    with (out / 'child.stdout').open('xb') as stdout, (out / 'child.stderr').open('xb') as stderr:
        child = subprocess.run(command, cwd='/', stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                               user=65534, group=65534, extra_groups=[])
    save(out / 'PROCESS.json', dict(command=command, exit_code=child.returncode, child_uid=65534))
    if child.returncode:
        print('FAIL2: denied-subtree control failed; retain test directory and inspect raw logs', flush=True)
        return 2
    result = json.loads((out / 'child.stdout').read_bytes())
    assert result['uid'] == 65534 and result['error_type'] == 'PermissionError'
    save(out / 'COMPLETED.json', dict(status='PASS_REAL_DENIED_SUBTREE_REJECTED', result=result,
                                    sources={name: pin(HERE / name) for name in ['Runtime.py', 'run.py', 'test_inventory_permissions.py']},
                                    denied_directory_retained=True, analysis_executed=False, new_family_credit=0))
    print('PASS_REAL_DENIED_SUBTREE_REJECTED', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
