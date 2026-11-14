# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Invoke unchanged native and complete original-R evidence verifiers."""
from pathlib import Path
import json, os, subprocess, sys, uuid


def qualification(root, runtime, wsl_distro=None):
    if not runtime:
        raise OSError('Exact permitted DoubletFinder R provider required; dependent verification skipped')
    actual = Path(os.fspath(root)).resolve()
    parent = actual / 'build/literature-review/monocle-runtime'
    parent.mkdir(parents=True, exist_ok=True)
    suffix = uuid.uuid4().hex
    native = parent / ('doubletfinder-catalog-native-' + suffix)
    workflow = parent / ('doubletfinder-catalog-original-R-' + suffix)
    commands = [[sys.executable, '-B', str(actual / 'tools/literature/verify_doubletfinder206_native.py'), '--out', str(native)]]
    if sys.platform == 'linux':
        command = [sys.executable, '-B', str(actual / 'tools/literature/verify_doubletfinder206.py'), '--runtime', str(runtime), '--out', str(workflow)]
    elif sys.platform == 'win32' and wsl_distro:
        def linux(path):
            p = Path(path)
            if p.drive.lower() != 'd:':
                raise OSError('This explicit WSL route requires the checkout on D:; use Linux for other layouts')
            return '/mnt/d/' + p.as_posix()[3:]
        command = ['wsl', '-d', wsl_distro, '-u', 'root', '--', '/usr/bin/python3', '-B', linux(actual / 'tools/literature/verify_doubletfinder206.py'), '--runtime', str(runtime), '--out', linux(workflow)]
    else:
        raise OSError('Complete R verification requires Linux or an explicitly selected permitted WSL distribution/provider')
    commands.append(command)
    for out, command in zip([native, workflow], commands):
        with out.with_suffix('.stdout').open('xb') as so, out.with_suffix('.stderr').open('xb') as se:
            child = subprocess.run(command, cwd=actual, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
        if child.returncode == 3:
            raise OSError('Original prerequisite unavailable; retain full logs at ' + str(out))
        if child.returncode:
            raise ValueError('Unchanged scientific verifier failed; retain full logs at ' + str(out))
    n = json.loads((native / 'COMPLETED.json').read_bytes())
    r = json.loads((workflow / 'COMPLETED.json').read_bytes())
    if (n['cases'], n['compared_numeric_or_classification_values'], r['complete_original_R_objects_recompared'], r['complete_PNG_files'], r['actual_protocol_and_recording_controls']) != (388, 3535, 22, 2, 18):
        raise ValueError('Complete selected scientific comparison population differs')
    if r['convergence_qualified'] or r['whole_tool_qualified'] or n['new_family_credit'] or r['new_family_credit']:
        raise ValueError('Unsupported convergence, whole-tool or duplicate family claim')
    return dict(status='PASS_UNCHANGED_NATIVE_AND_COMPLETE_ORIGINAL_R_VERIFIERS', native=n, workflow=r,
                new_upstream_family_credit=1, native_family_credit_added=0, whole_goal_complete=False)
