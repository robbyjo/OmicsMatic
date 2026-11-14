#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare each actual adapter run with independently captured original workflow objects."""
from pathlib import Path
import argparse, subprocess, sys, traceback
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, pin, load, save, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    for name in ['runtime', 'reference', 'out']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    try:
        reference, runtime = args.reference.resolve(), args.runtime.resolve()
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out)
                                       for p in [HERE, reference, runtime]):
            raise ValueError('Use a NEW nonoverlapping comparison output')
        out.mkdir(exist_ok=False)
        owned = True
        ready = load(runtime / 'READY.json')
        original = load(reference / 'ORIGINAL-CONTROLS.json')
        results = {}
        for name, expected in original['cases'].items():
            case = out / name
            case.mkdir()
            for directory in ['home', 'tmp']:
                (case / directory).mkdir()
            command = [sys.executable, '-B', str(HERE / 'run.py'), '--runtime', str(runtime),
                       '--request', str(reference / 'requests' / (name + '.rds')),
                       '--assets', str(reference / 'assets'), '--out', str(case / 'analysis')]
            print('ORIGINAL_VERSUS_ADAPTER', name, flush=True)
            with (case / 'adapter.stdout').open('xb') as stdout, (case / 'adapter.stderr').open('xb') as stderr:
                child = subprocess.run(command, cwd=case, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
            save(case / 'PROCESS.json', dict(command=command, exit_code=child.returncode))
            expected_code = 0 if expected['returned'] else 2
            actual = load(case / 'analysis/analysis-status.json')
            if child.returncode != expected_code or actual['result_admitted'] != expected['returned']:
                raise ValueError('Actual adapter admission/exit differs for ' + name + ': ' + str(child.returncode))
            command = [str(Path(ready['r_prefix']) / 'bin/Rscript'), '--vanilla', str(HERE / 'CompareWorkflow.R'),
                       str(reference / 'references' / name), str(case / 'analysis/original'), str(case / 'COMPARISON.json')]
            with (case / 'comparison.stdout').open('xb') as stdout, (case / 'comparison.stderr').open('xb') as stderr:
                comparison = subprocess.run(command, cwd=case, env=environment(ready, case),
                                            stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
            save(case / 'COMPARISON-PROCESS.json', dict(command=command, exit_code=comparison.returncode))
            if comparison.returncode:
                raise ValueError('Complete original comparison differs for ' + name)
            results[name] = dict(exit_code=child.returncode, comparison=load(case / 'COMPARISON.json'),
                                 R_handler_counts=actual['R_handler_counts'])
        (out / 'WARNINGS.md').write_text('# Scoped original workflow controls passed; warning review required\n\n'
            'Original errors are expected controls, not completed analyses. Inspect every case WARNINGS.md and raw console.\n\n'
            + ACTION + '\n', encoding='utf-8')
        save(out / 'COMPLETED.json', dict(status='PASS_SCOPED_ORIGINAL_MAPPING02_WORKFLOW_CONTROLS', cases=results,
             reference=pin(reference / 'ORIGINAL-CONTROLS.json'), provider=pin(runtime / 'READY.json'),
             verifier_sources={name: pin(HERE / name) for name in ['verify_workflow.py', 'CompareWorkflow.R', 'GenerateWorkflowReferences.R', 'DirectWorkflow.R']},
             new_family_credit=0, whole_tool_qualified=False, whole_goal_complete=False))
        print('PASS_SCOPED_ORIGINAL_MAPPING02_WORKFLOW_CONTROLS', len(results), flush=True)
        return 0
    except BaseException as error:
        print('FAIL2_WORKFLOW_COMPARISON_UNADMITTED', repr(error), ACTION, flush=True)
        if owned:
            try:
                save(out / 'FAILURE.json', dict(status='FAIL2_WORKFLOW_COMPARISON_UNADMITTED', cause=repr(error),
                                              traceback=traceback.format_exc(), required_action=ACTION))
                (out / 'WARNINGS.md').write_text('# FAIL2: comparison incomplete; no qualification\n\n' + ACTION + '\n', encoding='utf-8')
            except BaseException as recording:
                print('FAIL2_RECORDING_UNAVAILABLE', repr(recording), ACTION, flush=True)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
