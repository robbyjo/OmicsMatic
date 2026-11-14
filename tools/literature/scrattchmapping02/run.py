#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged scrattch.mapping 0.2 using complete caller-prepared reference assets."""
from pathlib import Path
import argparse, os, shutil, signal, subprocess, sys, time, traceback
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, Unavailable, pin, save, load, check, inventory, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    for name in ['runtime', 'request', 'assets', 'out']:
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--wall-seconds', type=int, default=1800)
    parser.add_argument('--maximum-files', type=int, default=50000)
    parser.add_argument('--maximum-workspace-bytes', type=int, default=2147483648)
    args = parser.parse_args()
    out = args.out.absolute()
    child = None
    owned = False
    admitted = False
    record = None
    code = 2
    label = 'FAIL2_UNADMITTED'

    def capacity():
        count = total = 0
        for parent, dirs, files in os.walk(out, followlinks=False):
            for name in files:
                try:
                    size = (Path(parent) / name).lstat().st_size
                except FileNotFoundError:
                    continue  # An original temporary file can disappear during live scans.
                count += 1
                total += size
        if count > args.maximum_files or total > args.maximum_workspace_bytes:
            raise ValueError('Declared workspace file/byte capacity exceeded; preserve partial files and retry NEW paths')

    def stop_child():
        if child is not None and child.poll() is None:
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()

    def warnings():
        text = '# ' + label + '\n\n**WARNING REVIEW REQUIRED. Complete result admitted: ' + str(admitted) + '.**\n\n'
        text += 'Original R handler totals: ' + str(None if record is None else record['R_handler_counts']) + '. '
        text += 'Raw/native diagnostic totals are unavailable, never zero.\n\n'
        path = out / 'original/condition-summary.json'
        if path.is_file():
            for group in load(path)['groups']:
                if group['kind'] in ['warning', 'error']:
                    text += '**Original ' + group['kind'] + ' x ' + str(group['count']) + '**\n\n'
                    text += ''.join('    ' + line + '\n' for line in group['message'].splitlines()) + '\n'
        (out / 'WARNINGS.md').write_text(text + ACTION + '\n', encoding='utf-8', newline='\n')

    try:
        request, runtime, assets = args.request.resolve(), args.runtime.resolve(), args.assets.resolve()
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out)
                                       for p in [HERE, request, runtime, assets]):
            raise ValueError('Use a NEW nonoverlapping canonical output directory')
        if min(args.wall_seconds, args.maximum_files, args.maximum_workspace_bytes) < 1:
            raise ValueError('Positive explicit capacities required')
        out.mkdir(exist_ok=False)
        owned = True
        if sys.platform != 'linux':
            raise Unavailable('The selected original runtime requires Linux/WSL; dependent analysis skipped')
        ready = load(runtime / 'READY.json')
        for key in ['prefix', 'source', 'manager_workspace']:
            protected = Path(ready[key])
            if out.is_relative_to(protected) or protected.is_relative_to(out):
                raise ValueError('Output overlaps protected runtime/source')
        print('CHECK_ORIGINAL_RUNTIME_BEFORE', flush=True)
        check(ready)
        for name in ['home', 'tmp', 'original']:
            (out / name).mkdir()
        request_pin = pin(request)
        asset_inventory = inventory(assets, links=False)
        shutil.copyfile(request, out / 'request.rds')
        save(out / 'assets-before.json', asset_inventory)
        save(out / 'INPUT.json', dict(request=str(request), request_identity=request_pin,
                                    assets=str(assets), runtime=str(runtime), runtime_identity=pin(runtime / 'READY.json'),
                                    limits=dict(wall_seconds=args.wall_seconds, maximum_files=args.maximum_files,
                                                maximum_workspace_bytes=args.maximum_workspace_bytes)))
        control = dict(library=ready['library'], base_library=ready['base_library'], source=ready['source'],
                       profile=str(HERE / 'upstream-source-profile.json'), audit=str(HERE / 'AuditRuntime.R'),
                       output=str(out / 'original'), request=str(out / 'request.rds'), assets=str(assets))
        save(out / 'control.json', control)
        capacity()
        command = [str(Path(ready['r_prefix']) / 'bin/Rscript'), '--vanilla',
                   str(HERE / 'Worker.R'), str(out / 'control.json')]
        start = time.monotonic()
        with (out / 'original.stdout').open('xb') as stdout, (out / 'original.stderr').open('xb') as stderr:
            child = subprocess.Popen(command, cwd=out, env=environment(ready, out), stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, start_new_session=True)
            save(out / 'LIVE.json', dict(pid=child.pid, command=command, started_unix=time.time()))
            while True:
                try:
                    result = child.wait(timeout=2)
                    break
                except subprocess.TimeoutExpired:
                    capacity()
                    if time.monotonic() - start > args.wall_seconds:
                        raise ValueError('Declared original-analysis time capacity exceeded; retry NEW paths with adequate capacity')
        save(out / 'PROCESS.json', dict(command=command, exit_code=result, elapsed_seconds=time.monotonic() - start))
        capacity()
        record = load(out / 'original/R-status.json')
        if result == 3 and record['status'] == 'SKIP3_ORIGINAL_REQUIREMENT_UNAVAILABLE':
            raise Unavailable('Original readiness failed before dependent analysis; inspect complete conditions and logs')
        if result or record['status'] != 'PASS_ORIGINAL_MAPPING02_COMPLETE_R_OBJECT' or not record['complete_original_RDS_result']:
            raise ValueError('Original analysis failed; complete conditions and all partial files retained')
        if pin(request) != request_pin or pin(out / 'request.rds') != request_pin:
            raise ValueError('Request changed during execution; qualify NEW input/output paths')
        if inventory(assets, links=False) != asset_inventory:
            raise ValueError('Reference assets changed during execution; retain both histories and qualify NEW paths')
        print('CHECK_ORIGINAL_RUNTIME_AFTER', flush=True)
        check(ready)
        capacity()
        if not (out / 'original/complete-result.rds').is_file():
            raise ValueError('Complete original RDS result missing')
        code, label, admitted = 0, 'PASS_ORIGINAL_MAPPING02_COMPLETE_R_OBJECT', True
    except BaseException as error:
        code = 3 if isinstance(error, Unavailable) or (child is None and isinstance(error, OSError)
                                                      and not isinstance(error, FileExistsError)) else 2
        label = ('SKIP3_ORIGINAL_REQUIREMENT_UNAVAILABLE' if code == 3 else 'FAIL2_ORIGINAL_ANALYSIS_UNADMITTED') + ': ' + repr(error)
        print(label, flush=True)
        if owned:
            try:
                save(out / 'FAILURE.json', dict(status=label, traceback=traceback.format_exc(), required_action=ACTION))
            except BaseException as recording:
                code = 2
                label = 'FAIL2_FAILURE_RECORDING_UNAVAILABLE: ' + repr(recording) + '; ' + label
                print(label, flush=True)
    finally:
        stop_child()
        if owned:
            try:
                warnings()
                capacity()
                save(out / 'MANIFEST.json', dict(files=inventory(out)))
                save(out / 'analysis-status.json', dict(status=label, exit_code=code, result_admitted=admitted,
                    R_handler_counts=None if record is None else record['R_handler_counts'],
                    raw_console_diagnostic_counts_complete=False, warning_review_required=True,
                    complete_original_RDS_return=admitted, whole_tool_qualified=False, whole_goal_complete=False,
                    required_action=ACTION))
            except BaseException as recording:
                code, admitted = 2, False
                label = 'FAIL2_FINAL_RECORDING_UNAVAILABLE: ' + repr(recording) + '; ' + label
                print(label + '\n' + ACTION, flush=True)
                for action in [lambda: save(out / 'FINALIZATION_ERROR.json', dict(status=label, exit_code=2,
                               result_admitted=False, required_action=ACTION)), warnings]:
                    try:
                        action()
                    except BaseException as fallback:
                        print('FAIL2_FALLBACK_RECORDING_UNAVAILABLE', repr(fallback), flush=True)
        print(label + '\nWARNING REVIEW REQUIRED. ' + ACTION, flush=True)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
