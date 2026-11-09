# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay both whole count-list value families on three JVMs and independently compare them back in R."""
import argparse, json, os, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save, environment


def linux(path): return '/mnt/d/projects/JLinAlg/' + path.relative_to(ROOT).as_posix()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['build', 'exports', 'out']: parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/rsubread-runtime'); out.mkdir(parents=True)
    state = dict(status='FAIL2_RSUBREAD_RESULT_VALUE_VALIDATION', analysis_admitted=False, implementation_credit=False,
                 full_adapter_available=False, whole_goal_complete=False, runs={})
    try:
        build = json.loads((args.build / 'COMPLETED.json').read_bytes())
        assert build['status'] == 'passed-JDK-only-native-Rsubread-result-value-API-build'
        exports = args.exports.resolve(); reference = json.loads((exports / 'COMPLETED.json').read_bytes())
        assert reference['status'] == 'passed-two-original-Rsubread-result-value-export-families-without-refitting'
        jar = ROOT / build['artifact']['path']; assert pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        before = {p.relative_to(exports).as_posix(): pin(p) for p in exports.rglob('*') if p.is_file()}
        source = ROOT / 'tools/literature/java/org/jlinalg/cli/RsubreadResultValueAudit.java'
        script = ROOT / 'tools/literature/recheck_rsubread_result_values.R'
        jdk = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
        classes = out / 'classes'; classes.mkdir()
        command = [str(jdk / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), str(source)]
        run = subprocess.run(command, capture_output=True, timeout=120)
        (out / 'compile.stdout').write_bytes(run.stdout); (out / 'compile.stderr').write_bytes(run.stderr)
        save(out / 'compile.process.json', dict(command=command, exit_code=run.returncode))
        assert run.returncode == 0, run.stderr.decode(errors='replace')
        for name, profile in reference['references'].items():
            state['runs'][name] = {}
            for runtime, prefix in [('windows21', [str(jdk / 'java.exe')]), ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']),
                                    ('linux21', ['wsl', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'])]:
                islinux = runtime == 'linux21'; home = out / (name + '-' + runtime)
                classpath = linux(classes) + ':' + linux(jar) if islinux else str(classes) + os.pathsep + str(jar)
                command = prefix + ['-Xmx128m', '-cp', classpath, 'org.jlinalg.cli.RsubreadResultValueAudit', linux(exports / name) if islinux else str(exports / name), linux(home) if islinux else str(home)]
                with (out / (name + '-' + runtime + '.stdout')).open('xb') as first, (out / (name + '-' + runtime + '.stderr')).open('xb') as second:
                    run = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=first, stderr=second, timeout=120,
                                         creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
                save(out / (name + '-' + runtime + '.process.json'), dict(command=command, exit_code=run.returncode))
                assert run.returncode == 0, (name, runtime, 'Preserve native stderr and partial outputs; no replay admitted')
                audit = json.loads((home / 'audit.json').read_bytes())
                assert audit['status'] == 'passed-complete-original-Rsubread-result-values-and-relationships-only' and audit['original_results'] == 16 and audit['contract_rejections'] >= 35
                state['runs'][name][runtime] = audit
            runtime = Path(profile['rscript']['path']); assert pin(runtime) == {k: profile['rscript'][k] for k in ['bytes', 'sha256']}
            original = (ROOT / profile['original_receipt']['path']).parent / 'original'
            env = environment()
            if name == '2232-R441': env['R_HOME'] = runtime.parents[2].as_posix()
            status = out / (name + '-R-roundtrip.txt')
            command = [str(runtime), '--vanilla', str(script), str(original), str(exports / name), str(out / (name + '-windows21')), str(status)]
            run = subprocess.run(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, capture_output=True, timeout=120,
                                 creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            (out / (name + '-R.stdout')).write_bytes(run.stdout); (out / (name + '-R.stderr')).write_bytes(run.stderr)
            save(out / (name + '-R.process.json'), dict(command=command, exit_code=run.returncode))
            assert run.returncode == 0 and status.read_text().strip() == 'PASS16_NATIVE_RESULT_VALUE_ROUNDTRIPS_AND3_SCALAR_CONTROLS_IN_ORIGINAL_R', name
        assert all(pin(exports / p) == v for p, v in before.items())
        assert all(pin(ROOT / p) == v for p, v in build['source_pins'].items())
        state.update(status='passed-two-original-Rsubread-result-value-families-on-three-JVMs-and-back-in-R',
                     artifact=build['artifact'], build_receipt=dict(path=(args.build / 'COMPLETED.json').resolve().relative_to(ROOT).as_posix(), **pin(args.build / 'COMPLETED.json')),
                     export_receipt=dict(path=(exports / 'COMPLETED.json').relative_to(ROOT).as_posix(), **pin(exports / 'COMPLETED.json')),
                     source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), source, script]},
                     original_evidence_unchanged=True, independent_read_counting=False, independent_junction_counting=False,
                     original_R_object_serialization=False, production_process_supervision=False)
    except Exception as error:
        state['error'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('error', ''))
    return 0 if state['status'].startswith('passed-') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete reference/runtime/value checks')
    raise SystemExit(main())
