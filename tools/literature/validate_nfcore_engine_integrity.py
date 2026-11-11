# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Cross-compiler execution plus resealed-command and stale-success refusal controls."""
import argparse, concurrent.futures, copy, json, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux, wsl


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--artifact', type=Path, required=True)
    p.add_argument('--matrix', type=Path, required=True)
    p.add_argument('--setup-reports', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out, jar, matrix, reports = map(Path.resolve, [a.out, a.artifact, a.matrix, a.setup_reports])
    assert out.is_relative_to(BASE) and jar.is_relative_to(BASE) and matrix.is_relative_to(BASE) and not out.exists()
    proof = json.loads((matrix / 'COMPLETED.json').read_bytes())
    assert proof['status'] == 'PASS_NFCORE_PRIMARY_CONFIG_ENGINE_AND_LITERAL_CAPTURE_CONTROLS_ONLY'
    assert len(proof['controls']) == 15 and all(r['passed'] for r in proof['controls'])
    out.mkdir()
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    artifact = pin(jar)
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__),
        ROOT / 'tools/literature/validate_nfcore_engine.py', ROOT / 'tools/literature/java/NfcoreBindingsControl.java']}
    state = dict(status='FAIL2_NFCORE_ENGINE_INTEGRITY_CONTROLS', new_catalog_credit=False,
        artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact), matrix_pin=pin(matrix / 'COMPLETED.json'))
    try:
        remote = wsl('/usr/bin/python3', '-c', 'import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-nfcore-integrity-",dir="/var/tmp"))').decode().strip()
        save(out / 'OWNER.json', dict(owned_linux_root=remote, cleanup='Retain every original and modified fixture; no deletion.'))
        compiled = out / 'classes'; compiled.mkdir()
        result = subprocess.run([str(JAVA25 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-cp', str(jar),
            '-d', str(compiled), str(ROOT / 'tools/literature/java/NfcoreBindingsControl.java')], capture_output=True, timeout=90)
        (out / 'javac25.stderr').write_bytes(result.stderr); assert result.returncode == 0, result.stderr
        for label, executable in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
            result = subprocess.run([str(executable), '-cp', str(jar) + ';' + str(compiled), 'org.jlinalg.cli.NfcoreBindingsControl', '--portable'], capture_output=True, timeout=60)
            (out / (label + '-native.stdout')).write_bytes(result.stdout); assert result.returncode == 0, result.stderr
        for label, argument in [('portable', '--portable'), ('capture', remote + '/capture')]:
            (out / (label + '-native.stdout')).write_bytes(wsl('/usr/bin/java', '-cp', linux(jar) + ':' + linux(compiled), 'org.jlinalg.cli.NfcoreBindingsControl', argument))

        def check(folder, label, arguments, expected, old=False):
            command = ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/timeout', '--signal=TERM', '--kill-after=10s', '240s',
                '/usr/bin/java', '-Xmx256m', '-XX:ActiveProcessorCount=2']
            command += ['-cp', linux(jar), 'org.jlinalg.cli.NextflowToolsCli'] if old else ['-jar', linux(jar)]
            with (folder / (label + '.stdout')).open('xb') as so, (folder / (label + '.stderr')).open('xb') as se:
                result = subprocess.run(command + arguments, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=260)
            save(folder / (label + '.json'), dict(command=command + arguments, expected_exit=expected, exit_code=result.returncode))
            assert result.returncode == expected, (label, result.returncode)

        def compiler_execution(spec):
            version, suffix = spec
            folder = out / version; folder.mkdir()
            source = folder / 'materialized'
            shutil.copytree(matrix / version / 'success/materialized', source)
            session = remote + '/compiler25-' + version
            (source / 'paths.txt').write_text(session + "/workspace/resources/r0/materialized/inputs/α's read data.txt\n", encoding='utf8', newline='\n')
            recipe = json.loads((matrix / version / 'success/recipe.json').read_bytes())
            recipe['workflow']['path'] = linux(source / "workflow-α's data")
            recipe['resources']['bundle']['path'] = linux(source)
            recipe['resources']['sheet']['path'] = linux(source / 'paths.txt')
            request = folder / 'recipe.json'; save(request, recipe)
            setup = ROOT / 'build/literature-review/nextflow-runtime/managed-setup-development-1'
            options = ['--home', linux(setup / ('home-' + suffix)), '--setup-report', linux(reports / ('report-' + suffix))]
            check(folder, 'run', ['--recipe', linux(request), '--out', session, *options], 0)
            check(folder, 'resume', ['--resume', session, *options], 0)
            check(folder, 'verify', ['--verify', session], 0)
            raw = wsl('/usr/bin/python3', '-c', '''import json,sys
from pathlib import Path
s=Path(sys.argv[1]);a=s/'attempts/000002';o=json.loads((a/'outputs.json').read_bytes());c=json.loads((a/'completion.json').read_bytes());assert c['stats']['cached']==1
print((a/'objects'/o['members']['observed.txt']['pin']['sha256']).read_bytes().hex())
''', session)
            assert bytes.fromhex(raw.decode().strip()) == b'CONTROL-READ-BYTES\n2\n96\n60\n9007199254740993\n'
            if version == '21.04.1':
                baseline = remote + '/unchanged-old-nested-config'
                check(folder, 'old-nested-baseline', ['--recipe', linux(request), '--out', baseline, *options], 2, old=True)
                log = wsl('/usr/bin/cat', baseline + '/attempts/000001/nextflow.log')
                (folder / 'old-nested-baseline.nextflow.log').write_bytes(log)
                assert b'No signature of method: nextflow.script.ScriptBinding.check_max()' in log
            print('PASS javac25 actual engine ' + version, flush=True)
            return dict(engine=version, compiler='javac25', execution=True, cached_resume=True, exact_output=True)

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            executions = list(pool.map(compiler_execution, [('21.04.1', '21-v2'), ('22.10.6', '22'), ('24.04.4', '24')]))
        original = matrix / '21.04.1/success/retained/attempts/000002'
        cases = []
        for mode in ['unchanged', 'corrupt-object', 'resealed-command-order', 'unexpected-file', 'latest-failed', 'stale-latest-success']:
            folder = out / mode
            if mode.startswith('latest-') or mode == 'stale-latest-success':
                shutil.copytree(matrix / '21.04.1/success/retained', folder)
                newest = folder / 'attempts/000003'; newest.mkdir()
                save(newest / 'FAILURE.json', dict(status='FAIL2-no-result-admitted', exit_code=2, error='Explicit validation fixture: newest attempt failed.'))
                save(folder / 'latest.json', dict(attempt='000003' if mode == 'latest-failed' else '000002', status='FAIL2' if mode == 'latest-failed' else 'COMPLETED'))
            else:
                shutil.copytree(original, folder)
                if mode == 'corrupt-object':
                    outputs = json.loads((folder / 'outputs.json').read_bytes())
                    obj = folder / 'objects' / outputs['members']['observed.txt']['pin']['sha256']
                    obj.write_bytes(obj.read_bytes() + b'CORRUPTED_CONTROL\n')
                elif mode == 'unexpected-file': (folder / 'undeclared.txt').write_bytes(b'Unexpected complete-tree member\n')
                elif mode == 'resealed-command-order':
                    invocation = json.loads((folder / 'invocation.json').read_bytes())
                    i = invocation['command'].index('-C') + 1
                    configs = invocation['command'][i].split(','); configs[0], configs[1] = configs[1], configs[0]
                    invocation['command'][i] = ','.join(configs); save(folder / 'invocation.json', invocation)
                    manifest = json.loads((folder / 'manifest.json').read_bytes())
                    manifest['files']['invocation.json'] = pin(folder / 'invocation.json'); save(folder / 'manifest.json', manifest)
                    complete = json.loads((folder / 'COMPLETED.json').read_bytes())
                    complete['manifest'] = pin(folder / 'manifest.json'); save(folder / 'COMPLETED.json', complete)
            expected = 0 if mode == 'unchanged' else 2
            for label, executable in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
                result = subprocess.run([str(executable), '-Xmx256m', '-jar', str(jar), '--verify', str(folder)], capture_output=True, timeout=90)
                (out / (mode + '-' + label + '.stderr')).write_bytes(result.stderr)
                (out / (mode + '-' + label + '.stdout')).write_bytes(result.stdout)
                assert result.returncode == expected, (mode, label, result.stderr[-2000:])
                if mode == 'resealed-command-order': assert b'original command' in result.stderr, result.stderr[-2000:]
                cases.append(dict(mode=mode, platform=label, expected_exit=expected, passed=True))
        assert pin(jar) == artifact and all(pin(ROOT / n) == v for n, v in source_pins.items())
        state.update(status='PASS_NFCORE_CROSS_COMPILER_EXECUTION_AND_SEMANTIC_INTEGRITY_CONTROLS_ONLY', executions=executions,
            old_nested_config_regression_demonstrated=True, saved_verification=cases, source_pins=source_pins,
            biological_analysis=False, whole_paper_reproduced=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc(),
            required_action='No result admitted. Preserve owned roots and reports, inspect the original cause and rerun corrected controls to NEW paths. Never change scientific settings or weaken verification to hide a failure.')
        (out / 'WARNINGS.md').write_text('# FAIL2 nf-core foundation integrity controls failed\n\n' + repr(error) + '\n\nRequired action: ' + state['required_action'] + '\n', encoding='utf8')
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    state['canonical_executable_unchanged'] = canonical
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable actual execution, saved-command or no-fallback controls')
    raise SystemExit(main())
