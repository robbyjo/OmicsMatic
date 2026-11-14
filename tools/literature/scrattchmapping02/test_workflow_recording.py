#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual missing-runtime and post-analysis recording-failure admission controls."""
from pathlib import Path
import argparse, contextlib, importlib.util, io, subprocess, sys
from unittest.mock import patch
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, load, save, pin


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    for name in ['runtime', 'reference', 'out']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    reference, runtime = args.reference.resolve(), args.runtime.resolve()
    if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out)
                                   for p in [HERE, reference, runtime]):
        raise ValueError('Use NEW nonoverlapping recording-control output')
    out.mkdir(exist_ok=False)
    common = ['--request', str(reference / 'requests/depth_1.rds'), '--assets', str(reference / 'assets')]
    command = [sys.executable, '-B', str(HERE / 'run.py'), '--runtime', str(out / 'unavailable-runtime'),
               *common, '--out', str(out / 'missing-runtime')]
    with (out / 'missing-runtime.stdout').open('xb') as stdout, (out / 'missing-runtime.stderr').open('xb') as stderr:
        missing = subprocess.run(command, cwd=out, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
    status = load(out / 'missing-runtime/analysis-status.json')
    assert missing.returncode == 3 and status['exit_code'] == 3 and not status['result_admitted']
    assert not (out / 'missing-runtime/original/complete-result.rds').exists()
    assert 'SKIP3' in (out / 'missing-runtime/WARNINGS.md').read_text(encoding='utf-8')
    specification = importlib.util.spec_from_file_location('mapping02_recording_runner', HERE / 'run.py')
    runner = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(runner)
    original_save = runner.save

    def deny_final_manifest(path, value):
        if Path(path).name == 'MANIFEST.json':
            raise PermissionError('TEST: final manifest recording unavailable after actual original analysis')
        return original_save(path, value)

    console = io.StringIO()
    argv = ['run.py', '--runtime', str(runtime), *common, '--out', str(out / 'final-recording-failure')]
    with patch.object(sys, 'argv', argv), patch.object(runner, 'save', deny_final_manifest), contextlib.redirect_stdout(console):
        code = runner.main()
    (out / 'final-recording-failure.stdout').write_text(console.getvalue(), encoding='utf-8')
    failure = load(out / 'final-recording-failure/FINALIZATION_ERROR.json')
    assert code == 2 and failure['exit_code'] == 2 and not failure['result_admitted']
    assert (out / 'final-recording-failure/original/complete-result.rds').is_file()
    assert not (out / 'final-recording-failure/analysis-status.json').exists()
    assert 'FAIL2_FINAL_RECORDING_UNAVAILABLE' in console.getvalue()
    warning = (out / 'final-recording-failure/WARNINGS.md').read_text(encoding='utf-8')
    assert 'FAIL2' in warning and 'Complete result admitted: False' in warning
    save(out / 'COMPLETED.json', dict(status='PASS_REAL_MISSING_RUNTIME_AND_INJECTED_FINAL_RECORDING_FAILURE',
        missing_runtime_actual_exit_code=missing.returncode, final_recording_exit_code=code,
        original_analysis_really_executed_before_injected_recording_error=True,
        failed_recording_result_admitted=False, partial_original_RDS_retained=True,
        provider=pin(runtime / 'READY.json'), test_source=pin(HERE / 'test_workflow_recording.py'),
        new_family_credit=0, whole_goal_complete=False))
    print('PASS_REAL_MISSING_RUNTIME_AND_INJECTED_FINAL_RECORDING_FAILURE', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
