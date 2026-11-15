# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay the unchanged qualified complete-R workflow checkpoint, fitting no models."""
from pathlib import Path
import json, os, subprocess, sys, uuid


def qualification(root, runtime, wsl_distro=None):
    if not runtime:
        raise OSError('Exact permitted original mapping provider required; dependent verification skipped')
    actual = Path(os.fspath(root)).resolve()
    parent = actual / 'build/literature-review/monocle-runtime'
    parent.mkdir(parents=True, exist_ok=True)
    out = parent / ('scrattchmapping-catalog-R-replay-' + uuid.uuid4().hex)
    script = actual / 'tools/literature/scrattchmapping02/verify_checkpoint.py'
    if sys.platform == 'linux':
        command = [sys.executable, '-B', str(script), '--runtime', str(runtime), '--out', str(out)]
    elif sys.platform == 'win32' and wsl_distro:
        def linux(path):
            p = Path(path)
            if p.drive.lower() != 'd:':
                raise OSError('Use the explicit D: WSL route or run inside Linux for other layouts')
            return '/mnt/d/' + p.as_posix()[3:]
        command = ['wsl', '-d', wsl_distro, '-u', 'root', '--', '/usr/bin/python3', '-B', linux(script),
                   '--runtime', str(runtime), '--out', linux(out)]
    else:
        raise OSError('Complete mapping replay requires Linux or an explicitly selected permitted WSL provider')
    with out.with_suffix('.stdout').open('xb') as stdout, out.with_suffix('.stderr').open('xb') as stderr:
        child = subprocess.run(command, cwd=actual, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
    if child.returncode == 3:
        raise OSError('Original mapping prerequisite unavailable; inspect ' + str(out))
    if child.returncode:
        raise ValueError('Complete original mapping evidence replay failed; inspect ' + str(out))
    result = json.loads((out / 'COMPLETED.json').read_bytes())
    if (result['complete_controls'], result['complete_returns'], result['original_errors_without_result'], result['bundle_members']) != (11, 7, 4, 1248):
        raise ValueError('Complete mapping comparison population differs')
    if result['new_family_credit'] or result['live_execution_context_identity_claimed'] or result['original_model_fitting_executed']:
        raise ValueError('Unsupported duplicate, live-context or model-fitting claim')
    return dict(status='PASS_COMPLETE_ORIGINAL_MAPPING02_CHECKPOINT_REPLAY', workflow=result,
                new_upstream_family_credit=1, native_family_credit_added=0, whole_goal_complete=False)
