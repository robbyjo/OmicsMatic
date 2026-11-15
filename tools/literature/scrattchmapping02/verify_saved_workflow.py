#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fresh original calls versus actual adapter: strict values/RNG and complete saved R conditions."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse, shutil, subprocess, sys, traceback
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, check, pin, load, save, environment, sources

HELPERS = ['verify_saved_workflow.py', 'CompareSavedWorkflow.R', 'GenerateWorkflowReferences.R', 'DirectWorkflow.R']


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--jobs', type=int, choices=[1, 2, 3], default=1)
    parser.add_argument('--prepared', type=Path, help='Existing complete synthetic prepared request/asset directory; no rewriting')
    parser.add_argument('--cases', nargs='+', help='Explicit original control names; receipt reports only these controls')
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    try:
        runtime = args.runtime.resolve()
        protected = [HERE, runtime] + ([args.prepared.resolve()] if args.prepared else [])
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in protected):
            raise ValueError('Use a NEW separate canonical verification output')
        out.mkdir(exist_ok=False)
        owned = True
        ready = load(runtime / 'READY.json')
        check(ready)
        helper_pins = {name: pin(HERE / name) for name in HELPERS}
        for name in ['home', 'tmp', 'sources']:
            (out / name).mkdir()
        for name in HELPERS:
            shutil.copyfile(HERE / name, out / 'sources' / name)

        def call(label, command, directory):
            with (directory / (label + '.stdout')).open('xb') as stdout, (directory / (label + '.stderr')).open('xb') as stderr:
                child = subprocess.run(command, cwd=directory, env=environment(ready, directory),
                                       stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
            save(directory / (label + '-PROCESS.json'), dict(command=command, exit_code=child.returncode))
            return child.returncode

        rscript = str(Path(ready['r_prefix']) / 'bin/Rscript')
        prepared_directory = args.prepared.resolve() if args.prepared else out / 'prepared'
        if args.prepared is None:
            prepared_directory.mkdir()
            code = call('prepare', [rscript, '--vanilla', str(HERE / 'GenerateWorkflowReferences.R'), str(prepared_directory)], out)
            if code:
                raise ValueError('Original source control preparation failed; inspect complete logs')
        prepared = load(prepared_directory / 'PREPARED-REQUESTS.json')
        selected = args.cases or list(prepared['cases'])
        if len(set(selected)) != len(selected) or not all(name in prepared['cases'] for name in selected):
            raise ValueError('Select distinct existing original control names')

        def compare(item):
            name, request = item
            directory = out / name
            directory.mkdir()
            for child in ['home', 'tmp', 'original-reference']:
                (directory / child).mkdir()
            print('FRESH_ORIGINAL_AND_ADAPTER', name, flush=True)
            command = [rscript, '--vanilla', str(HERE / 'DirectWorkflow.R'), request['request'],
                       str(directory / 'original-reference')]
            if call('reference', command, directory):
                raise ValueError('Independent original process failed before recording ' + name)
            expected = load(directory / 'original-reference/ORIGINAL-CASE.json')
            command = [sys.executable, '-B', str(HERE / 'run.py'), '--runtime', str(runtime),
                       '--request', request['request'], '--assets', str(prepared_directory / 'assets'),
                       '--out', str(directory / 'adapter')]
            actual_code = call('adapter', command, directory)
            actual = load(directory / 'adapter/analysis-status.json')
            if actual_code != (0 if expected['returned'] else 2) or actual['result_admitted'] != expected['returned']:
                raise ValueError('Original admission/actual child exit differs for ' + name)
            command = [rscript, '--vanilla', str(HERE / 'CompareSavedWorkflow.R'),
                       str(directory / 'original-reference'), str(directory / 'adapter/original'),
                       str(directory / 'COMPARISON.json')]
            if call('comparison', command, directory):
                raise ValueError('Whole saved original workflow comparison differs for ' + name)
            return name, dict(exit_code=actual_code, original_returned=expected['returned'],
                              comparison=load(directory / 'COMPARISON.json'), R_handler_counts=actual['R_handler_counts'])

        with ThreadPoolExecutor(max_workers=args.jobs) as executor:
            results = dict(executor.map(compare, [(name, prepared['cases'][name]) for name in selected]))
        if helper_pins != {name: pin(HERE / name) for name in HELPERS} or sources() != ready['runner_sources']:
            raise ValueError('Verification/adapter sources changed; preserve this attempt and retry NEW paths')
        check(ready)
        (out / 'WARNINGS.md').write_text('# Original saved-workflow controls compared; warning review required\n\n'
            'Returns and RNG use strict R object comparison. Complete conditions use strict R identity or complete native R serialization; '
            'live external-pointer addresses are not serialized or claimed equal. The earlier reference-identity gate remains failed. '
            'Original failures admit no result. Zero-expression returns retain their warnings and nonfinite values.\n\n'
            + ACTION + '\n', encoding='utf-8')
        save(out / 'COMPLETED.json', dict(status='PASS_ORIGINAL_VALUES_RNG_AND_COMPLETE_SAVED_CONDITION_CONTROLS',
            cases=results, controls=len(results), actual_fresh_original_processes=len(results),
            provider=pin(runtime / 'READY.json'), runner_sources=ready['runner_sources'], verifier_sources=helper_pins,
            prepared_inputs=str(prepared_directory), selected_cases=selected,
            earlier_live_reference_identity_failure_superseded=False, live_execution_context_identity_claimed=False,
            new_family_credit=0, whole_tool_qualified=False, whole_goal_complete=False))
        print('PASS_ORIGINAL_VALUES_RNG_AND_COMPLETE_SAVED_CONDITION_CONTROLS', len(results), flush=True)
        return 0
    except BaseException as error:
        print('FAIL2_SAVED_WORKFLOW_VERIFICATION_UNADMITTED', repr(error), ACTION, flush=True)
        if owned:
            try:
                save(out / 'FAILURE.json', dict(status='FAIL2_SAVED_WORKFLOW_VERIFICATION_UNADMITTED', cause=repr(error),
                                              traceback=traceback.format_exc(), required_action=ACTION))
                (out / 'WARNINGS.md').write_text('# FAIL2: whole saved-workflow comparison incomplete\n\n' + ACTION + '\n', encoding='utf-8')
            except BaseException as recording:
                print('FAIL2_RECORDING_UNAVAILABLE', repr(recording), ACTION, flush=True)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
