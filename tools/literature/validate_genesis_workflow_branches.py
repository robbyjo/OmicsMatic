# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run all declared GENESIS development option branches against original R and the extended native auditor."""
import argparse
import importlib.util
import json
import os
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

BASE = ROOT / 'build/literature-review/genesis-runtime'
ACTION = 'Missing or forbidden exact R/GENESIS/Java prerequisites require SKIP3 and skipped dependent validation. Supply a permitted complete private runtime; this helper installs nothing. FAIL2: preserve every source attempt and output, inspect the named scientific/process mismatch and retry only NEW paths after resolving the cause. This option-branch component collection admits no project analysis or catalog credit.'
KNOWN_NATIVE_REFUSALS = {'pcair-source-forced-related-twins', 'pcair-source-all-components-request', 'pcrelate-source-high-MAF', 'null-gaussian-source-iteration1', 'null-gaussian-source-constant-covariate'}
KNOWN_ORIGINAL_ERRORS = {'pcair-source-empty-QC', 'null-gaussian-source-collinear-covariate', 'null-two-stage-all-model'}


class Unavailable(RuntimeError):
    pass


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ['controls', 'jar', 'profile', 'out']:
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--java', type=Path, default=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'))
    p.add_argument('--javac', type=Path, default=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'))
    a = p.parse_args(); out = a.out.resolve(); assert not out.exists() and out.is_relative_to(BASE); out.mkdir(parents=True)
    state = dict(status='FAIL2_GENESIS_OPTION_BRANCH_VALIDATION', project_analysis_admitted=False, new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION)
    code = 2; runtime_before = None; roots = []; protected = {}; records = []
    try:
        if os.name != 'nt' or not all(x.is_file() for x in [a.jar, a.profile, a.java, a.javac]):
            raise Unavailable('The selected Windows Java, compiler, development artifact or exact private runtime profile is unavailable')
        profile = json.loads(a.profile.read_bytes())
        assert (profile['tool'], profile['version'], profile['r_version']) == ('GENESIS', '2.42.0', '4.6.1')
        roots = [Path(profile['r_home']), Path(profile['private_library'])]
        if not all(x.is_dir() for x in roots) or not Path(profile['rscript']).is_file():
            raise Unavailable('The selected exact R executable or private library is missing')
        helper = ROOT / 'src/main/resources/org/jlinalg/omics/genesis/prepare_genesis_runtime.py'
        spec = importlib.util.spec_from_file_location('branch_runtime', helper); module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        runtime_before = module.closure(roots); assert runtime_before == profile['files']
        source = ROOT / 'src/test/java/org/jlinalg/cli/GenesisWorkflowReferenceProbe.java'
        old = json.loads((a.controls / 'COMPLETED.json').read_bytes())
        names = [r['case'] for r in old['records']]; assert len(names) == len(set(names)) == 61
        protected = {str(f.resolve()): pin(f) for f in [a.jar, a.profile, helper, source, Path(__file__), ROOT / 'build/cli/omicsmatic-0.3.7.jar', a.controls / 'COMPLETED.json']}
        for name in names:
            file = a.controls / name / 'request.json'; protected[str(file.resolve())] = pin(file)
        classes = out / 'probe-classes'; classes.mkdir()

        def run(command, home, label, timeout, environment=None):
            try:
                result = subprocess.run(command, cwd=home, env=environment, stdin=subprocess.DEVNULL, capture_output=True, timeout=timeout)
            except OSError as error:
                raise Unavailable('A required selected process could not be launched: ' + repr(error)) from error
            (home / (label + '.stdout')).write_bytes(result.stdout); (home / (label + '.stderr')).write_bytes(result.stderr)
            save(home / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
            return result

        compiled = run([str(a.javac), '--release', '17', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-cp', str(a.jar.resolve()), '-d', str(classes), str(source)], out, 'compile-probe', 120)
        assert compiled.returncode == 0, compiled.stderr[-6000:]
        cp = str(classes) + os.pathsep + str(a.jar.resolve())
        declared = [dict(case=name, expected_original_exit=2 if name in KNOWN_ORIGINAL_ERRORS else 0,
                         expected_native_exit=None if name in KNOWN_ORIGINAL_ERRORS else 2 if name in KNOWN_NATIVE_REFUSALS else 0) for name in names]
        save(out / 'declared-cases.json', declared)
        for expected in declared:
            name = expected['case']; home = out / name; home.mkdir()
            request = json.loads((a.controls / name / 'request.json').read_bytes()); request['out'] = str(home); request['runtime_profile'] = str(a.profile.resolve())
            file = home / 'request.json'; save(file, request)
            prepared = run([str(a.java), '-Xmx2g', '-cp', cp, 'org.jlinalg.cli.GenesisWorkflowReferenceProbe', 'prepare', str(file), str(home)], home, 'prepare', 90)
            record = dict(**expected, prepare_exit=prepared.returncode, original_exit=None, native_component_exit=None, project_analysis_admitted=False)
            if prepared.returncode == 0:
                env = dict(os.environ, R_HOME=profile['r_home'], R_LIBS_USER=profile['private_library'], R_LIBS_SITE='', R_DEFAULT_PACKAGES='base', TMPDIR=str(home / 'tmp'), LC_ALL='C', LANG='C', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
                original = run([profile['rscript'], '--vanilla', str(home / 'worker.R'), profile['private_library'], str(home), str(home / 'worker'), str(home / 'parameters.tsv')], home, 'original', 360, env)
                record['original_exit'] = original.returncode
                if original.returncode == 0:
                    native = run([str(a.java), '-Xmx2g', '-cp', cp, 'org.jlinalg.cli.GenesisWorkflowReferenceProbe', 'audit', str(file), str(home)], home, 'native-audit', 90)
                    record['native_component_exit'] = native.returncode
            record['matches_declared_disposition'] = record['prepare_exit'] == 0 and record['original_exit'] == expected['expected_original_exit'] and record['native_component_exit'] == expected['expected_native_exit']
            save(home / 'DISPOSITION.json', record); records.append(record); save(out / 'progress.json', records)
            print(json.dumps(record), flush=True)
        assert all(r['matches_declared_disposition'] for r in records), 'Unexpected source/native branch dispositions; inspect every retained attempt'
        state.update(status='PASS61_EXTENDED_GENESIS_DECLARED_BRANCH_DISPOSITIONS_53_POSITIVE_AND8_REFUSALS', records=records,
                     original_successes=58, native_component_successes=53, native_component_refusals=5, original_errors=3,
                     candidate_artifact=pin(a.jar), producer=pin(Path(__file__)), protected=protected)
        code = 0
    except Unavailable as error:
        state.update(status='SKIP3_GENESIS_BRANCH_PREREQUISITE_UNAVAILABLE', cause=repr(error), records=records); code = 3
    except Exception as error:
        state.update(cause=repr(error), records=records); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    finally:
        try:
            assert all(pin(Path(n)) == value for n, value in protected.items())
            if runtime_before is not None:
                assert module.closure(roots) == runtime_before
        except Exception as error:
            state.update(status='FAIL2_GENESIS_BRANCH_PRESERVATION', cause=repr(error)); code = 2
        (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Declared synthetic component dispositions only; no original user analysis is admitted.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
        save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', '')[:6000], flush=True)
    return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete source, runtime or declared scientific-disposition checks')
    raise SystemExit(main())
