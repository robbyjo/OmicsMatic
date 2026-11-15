#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual installer subprocesses: forbidden installation/downloads and missing manager."""
from pathlib import Path
import argparse, subprocess, sys
sys.dont_write_bytecode = True
from Runtime import HERE, pin, save, load


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--manager', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    if out != out.resolve() or out.is_relative_to(HERE) or HERE.is_relative_to(out):
        raise ValueError('Use a NEW separate canonical control directory')
    out.mkdir(exist_ok=False)
    cases = [
        ('installation-forbidden', ['--manager', str(args.manager)]),
        ('downloads-forbidden', ['--manager', str(args.manager), '--allow-install']),
        ('manager-unavailable', ['--manager', str(out / 'unavailable-pixi'), '--allow-install', '--allow-download'])
    ]
    results = {}
    for name, options in cases:
        directory = out / name
        command = [sys.executable, '-B', str(HERE / 'install_runtime.py'), *options, '--out', str(directory)]
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            child = subprocess.run(command, cwd=out, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
        failure = load(directory / 'FAILURE.json')
        assert child.returncode == 3 and not failure['analysis_executed'] and not failure['result_admitted']
        assert not failure['calls'] and not (directory / 'downloads').exists()
        assert 'SKIP3' in (directory / 'WARNINGS.md').read_text(encoding='utf-8')
        results[name] = dict(actual_exit_code=child.returncode, installer_children_started=len(failure['calls']),
                             analysis_executed=False, status=failure['status'])
    save(out / 'COMPLETED.json', dict(status='PASS_ACTUAL_FORBIDDEN_INSTALL_DOWNLOAD_AND_MISSING_MANAGER_CONTROLS',
         cases=results, source_files={name: pin(HERE / name) for name in ['install_runtime.py', 'install-source-profile.json', 'test_install_policy.py']},
         new_family_credit=0, whole_goal_complete=False))
    print('PASS_ACTUAL_FORBIDDEN_INSTALL_DOWNLOAD_AND_MISSING_MANAGER_CONTROLS', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
