#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete frozen workflow bundle and replay all saved original R comparisons."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, shutil, subprocess, sys, traceback, zipfile
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, Unavailable, check, environment, load, pin, save, sources

ROOT = HERE.parents[2]
DATA = ROOT / 'docs/literature/scrattchmapping02-workflow-validation'


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument('--runtime', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    try:
        runtime = args.runtime.resolve()
        if out != out.resolve() or not out.is_relative_to(ROOT / 'build/literature-review/monocle-runtime'):
            raise ValueError('Use a NEW canonical monocle-runtime verification directory')
        if out.is_relative_to(runtime) or runtime.is_relative_to(out):
            raise ValueError('Output overlaps protected provider')
        out.mkdir(exist_ok=False)
        owned = True
        if sys.platform != 'linux':
            raise Unavailable('Complete R replay requires Linux/WSL and the exact permitted mapping provider')
        proof = load(DATA / 'QUALIFICATION.json')
        if (proof['original_controls'], proof['strict_complete_returns'], proof['original_failures_without_result'],
                proof['condition_fields_deleted_or_coerced'], proof['live_execution_context_identity_claimed']) != (11, 7, 4, False, False):
            raise ValueError('Complete qualified original workflow population/claims differ')
        if sources() != proof['runner_sources']:
            raise ValueError('Qualified workflow source bytes differ; preserve the old bundle and qualify NEW evidence')
        for name, identity in proof['verifier_sources'].items():
            if pin(HERE / name) != identity:
                raise ValueError('Frozen original comparison helper differs: ' + name)
        bundle = DATA / 'original-runtime-and-workflow-evidence.zip'
        if pin(bundle) != proof['evidence_bundle']:
            raise ValueError('Complete qualified workflow bundle differs')
        reference = out / 'frozen'
        reference.mkdir()
        with zipfile.ZipFile(bundle) as archive:
            names = archive.namelist()
            if len(names) != proof['regular_bundle_members'] or len(names) != len(set(n.casefold() for n in names)):
                raise ValueError('Incomplete or colliding original bundle members')
            manifest = __import__('json').loads(archive.read('BUNDLE-MANIFEST.json'))['files']
            if set(names) != set(manifest) | {'BUNDLE-MANIFEST.json', 'QUALIFICATION.json'}:
                raise ValueError('Complete original bundle inventory differs')
            frozen = __import__('json').loads(archive.read('QUALIFICATION.json'))
            if frozen != {k: v for k, v in proof.items() if k not in ['evidence_bundle', 'regular_bundle_members']}:
                raise ValueError('Outer qualification differs from the frozen complete record')
            for name in names:
                path = PurePosixPath(name)
                if path.is_absolute() or '..' in path.parts or '\\' in name:
                    raise ValueError('Unsafe complete evidence member')
                raw = archive.read(name)
                if name in manifest and dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) != manifest[name]:
                    raise ValueError('Original bundle member differs: ' + name)
                target = reference.joinpath(*path.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw)
        # Native Java is a separately qualified, unchanged optional component.
        native = load(ROOT / 'docs/literature/scrattchmapping02-native-development/VALIDATION.json')
        for name, identity in native['sources'].items():
            if pin(ROOT / name) != identity:
                raise ValueError('Independent native component source differs: ' + name)
        for name in ['PROJECT-VALIDATION.json', 'PROJECT-JUNIT.xml', 'COMPLETE-R-COMPARISON.json']:
            if not (ROOT / 'docs/literature/scrattchmapping02-native-development' / name).is_file():
                raise ValueError('Independent native original/project evidence missing')
        ready = load(runtime / 'READY.json')
        if ready['runner_sources'] != proof['runner_sources']:
            raise ValueError('Provider does not bind the admitted exact runner')
        check(ready)
        for name in ['home', 'tmp']:
            (out / name).mkdir()
        results = {}
        for name, expected in proof['cases'].items():
            directory = out / name
            directory.mkdir()
            case = reference / 'current-complete-workflow' / name
            command = [str(Path(ready['r_prefix']) / 'bin/Rscript'), '--vanilla', str(HERE / 'CompareSavedWorkflow.R'),
                       str(case / 'original-reference'), str(case / 'adapter/original'), str(directory / 'COMPARISON.json')]
            with (directory / 'original.stdout').open('xb') as stdout, (directory / 'original.stderr').open('xb') as stderr:
                child = subprocess.run(command, cwd=out, env=environment(ready, out),
                                       stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
            save(directory / 'PROCESS.json', dict(command=command, exit_code=child.returncode))
            if child.returncode:
                raise ValueError('Complete original saved comparison differs: ' + name)
            result = load(directory / 'COMPARISON.json')
            if result != expected['comparison']:
                raise ValueError('Exact complete comparison report differs: ' + name)
            results[name] = result
        check(ready)
        (out / 'WARNINGS.md').write_text('# All complete saved comparisons replayed; original warnings require review\n\n'
            'Seven complete returns and four original failures were checked. No new model or reference was fitted. '
            'Live C pointer-address identity is not persisted by RDS and is not claimed. '
            'The previous live-reference comparison remains failed and retained.\n\n' + ACTION + '\n', encoding='utf-8')
        save(out / 'COMPLETED.json', dict(status='PASS_COMPLETE_FROZEN_MAPPING02_BUNDLE_AND_ORIGINAL_R_REPLAY',
            complete_controls=11, complete_returns=7, original_errors_without_result=4, bundle_members=len(names),
            all_original_bundle_members_checked=True, complete_saved_conditions_recompared=True,
            values_and_RNG_strict=True, live_execution_context_identity_claimed=False,
            original_model_fitting_executed=False, independent_native_source_pins_checked=True,
            cases=results, provider=pin(runtime / 'READY.json'), new_family_credit=0, whole_goal_complete=False))
        print('PASS_COMPLETE_FROZEN_MAPPING02_BUNDLE_AND_ORIGINAL_R_REPLAY', flush=True)
        return 0
    except BaseException as error:
        code = 3 if isinstance(error, OSError) and not isinstance(error, FileExistsError) else 2
        status = 'SKIP3_MAPPING02_CHECKPOINT_UNAVAILABLE' if code == 3 else 'FAIL2_MAPPING02_CHECKPOINT_UNADMITTED'
        print(status, repr(error), ACTION, flush=True)
        if owned:
            try:
                save(out / 'FAILURE.json', dict(status=status, cause=repr(error), traceback=traceback.format_exc(), required_action=ACTION))
                (out / 'WARNINGS.md').write_text('# ' + status + '\n\n' + repr(error) + '\n\n' + ACTION + '\n', encoding='utf-8')
            except BaseException as recording:
                code = 2
                print('FAIL2_CHECKPOINT_RECORDING_UNAVAILABLE', repr(recording), ACTION, flush=True)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
