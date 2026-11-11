# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate separate primary configs, literal captured CSV paths and failure admission with real engines."""
import argparse, concurrent.futures, hashlib, json, shutil, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

BASE = ROOT / 'build/literature-review/nfcore-runtime'
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
JAVA25 = Path('C:/Program Files/Common Files/Oracle/Java/javapath')


def linux(path):
    return '/mnt/' + path.drive[0].lower() + '/' + path.as_posix()[3:]


def wsl(*args, timeout=90):
    result = subprocess.run(['wsl.exe', '-d', DISTRO, '--exec', *map(str, args)],
        stdin=subprocess.DEVNULL, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(('Control helper failed', args, result.returncode, result.stderr[-3000:]))
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--setup-reports', type=Path, required=True,
        help='Explicit completed health-report parent for this host; runtime homes stay unchanged')
    args = parser.parse_args()
    out, jar = args.out.resolve(), args.artifact.resolve()
    reports = args.setup_reports.resolve()
    assert out.is_relative_to(BASE) and jar.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    before = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    artifact = pin(jar)
    sources = [*sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('NfcoreEngine*.java')),
        ROOT / 'src/main/java/org/jlinalg/workflow/NfcoreInputBindings.java', Path(__file__),
        ROOT / 'tools/literature/java/NfcoreBindingsControl.java']
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    state = dict(status='FAIL2_NFCORE_ENGINE_CONTROLS', new_catalog_credit=False,
        full_nfcore_adapter_validated=False, artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact))
    try:
        remote = wsl('/usr/bin/python3', '-c', 'import tempfile; print(tempfile.mkdtemp(prefix="omicsmatic-nfcore-engine-",dir="/var/tmp"))').decode().strip()
        save(out / 'OWNER.json', dict(owned_linux_root=remote, cleanup='Retain all attempts; no deletion.'))
        classes = out / 'classes'; classes.mkdir()
        compiled = subprocess.run([str(JAVA / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror',
            '-cp', str(jar), '-d', str(classes), str(sources[-1])], capture_output=True, timeout=90)
        (out / 'javac.stderr').write_bytes(compiled.stderr)
        assert compiled.returncode == 0, compiled.stderr
        for name, java in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
            result = subprocess.run([str(java), '-cp', str(jar) + ';' + str(classes),
                'org.jlinalg.cli.NfcoreBindingsControl', '--portable'], capture_output=True, timeout=60)
            (out / (name + '-native.stdout')).write_bytes(result.stdout)
            (out / (name + '-native.stderr')).write_bytes(result.stderr)
            assert result.returncode == 0, (name, result.stderr)
        for mode in ['--portable', remote + '/actual-capture']:
            raw = wsl('/usr/bin/java', '-cp', linux(jar) + ':' + linux(classes),
                'org.jlinalg.cli.NfcoreBindingsControl', mode)
            (out / ('linux-native-' + ('portable' if mode == '--portable' else 'capture') + '.stdout')).write_bytes(raw)
        original = BASE / 'source-inspection-1/chipseq-1.2.1/nextflow.config'
        wanted = json.loads((BASE / 'source-intake-1/chipseq/1.2.1-source-inventory.json').read_bytes())
        # Read only the original method body; its original MIT notice is retained by the source lock.
        lock = json.loads((ROOT / 'src/main/resources/org/jlinalg/omics/nfcore/nfcore-source-lock.json').read_bytes())
        raw = original.read_bytes()
        assert pin(original)['sha256'] in json.dumps(lock) and pin(original)['sha256'] in json.dumps(wanted)
        body = raw[raw.index(b'def check_max(obj, type) {'):].decode()
        (out / 'original-check-max.config').write_bytes(body.encode())
        main_nf = '''nextflow.enable.dsl=2
params.sheet=null
params.outdir=null
params.fail=false
process READ {
    publishDir params.outdir, mode: 'copy'
    input:
    path sheet
    output:
    path 'observed.txt'
    script:
    if (params.fail) 'exit 7'
    else """
    cat -- "\\$(cat -- ${sheet})" > observed.txt
    printf '%s\\n' '${task.cpus}' '${task.memory.toMega()}' '${task.time.seconds}' '${params.exact}' >> observed.txt
    """
}
workflow { READ(file(params.sheet, checkIfExists: true)) }
'''
        base_config = "process { cpus = { check_max(6, 'cpus') }; memory = { check_max(128.MB, 'memory') }; time = { check_max(2.m, 'time') }; errorStrategy = 'ignore' }\nexecutor { cpus=2; memory='1 GB' }\n"
        primary = "params.max_cpus=4\nparams.max_memory='128 MB'\nparams.max_time='2m'\nincludeConfig 'conf/base.config'\nprofiles { control { process.maxForks=1 } }\n" + body
        overlay = "params.max_cpus=2\nparams.max_memory='96 MB'\nparams.max_time='1m'\n"
        setup = ROOT / 'build/literature-review/nextflow-runtime/managed-setup-development-1'
        expected = b'CONTROL-READ-BYTES\n2\n96\n60\n9007199254740993\n'

        def family(spec):
            version, suffix = spec
            family_out = out / version; family_out.mkdir()
            records = []
            for mode in ['success', 'ignored-failure', 'missing-output', 'missing-tool', 'comma-config']:
                folder = family_out / mode; folder.mkdir()
                source = folder / ('materialized,comma' if mode == 'comma-config' else 'materialized')
                workflow = source / "workflow-α's data"
                (workflow / 'conf').mkdir(parents=True)
                (workflow / 'settings').mkdir()
                (source / 'inputs').mkdir()
                (source / "inputs/α's read data.txt").write_bytes(b'CONTROL-READ-BYTES\n')
                (workflow / 'main.nf').write_text(main_nf, encoding='utf8', newline='\n')
                (workflow / 'nextflow.config').write_text(primary, encoding='utf8', newline='\n')
                (workflow / 'conf/base.config').write_text(base_config, encoding='utf8', newline='\n')
                (workflow / 'settings/overlay.config').write_text(overlay, encoding='utf8', newline='\n')
                session = remote + '/' + version + '-' + mode
                # This relation is also checked through the actual frozen capture above.
                captured_read = session + '/workspace/resources/r0/' + source.name + "/inputs/α's read data.txt"
                (source / 'paths.txt').write_text(captured_read + '\n', encoding='utf8', newline='\n')
                recipe = dict(schema_version=1, engine_profile='linux-x86_64-nextflow-' + version,
                    workflow=dict(path=linux(workflow), entrypoint='main.nf', configs=['nextflow.config', 'settings/overlay.config'], profiles=['control']),
                    resources=dict(bundle=dict(path=linux(source)), sheet=dict(path=linux(source / 'paths.txt'))),
                    parameters=dict(sheet={'$input': 'sheet'}, outdir={'$output': ''}, fail=mode == 'ignored-failure', exact=9007199254740993),
                    required_tools=[dict(name='cat', path='/usr/bin/cat')], outputs=[dict(path='missing.txt' if mode == 'missing-output' else 'observed.txt', kind='file', allow_empty=False)],
                    require_tasks=True, limits=dict(wall_seconds=150, cpu_seconds=150))
                if mode == 'missing-tool': recipe['required_tools'].append(dict(name='missing_fixture', path='/absent-nfcore-fixture-tool'))
                recipe_path = folder / 'recipe.json'; save(recipe_path, recipe)
                home, report = linux(setup / ('home-' + suffix)), linux(reports / ('report-' + suffix))

                def run(label, arguments, expected_exit, old=False):
                    java = ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/timeout', '--signal=TERM', '--kill-after=10s', '220s',
                        '/usr/bin/java', '-Xmx256m', '-XX:ActiveProcessorCount=2']
                    java += ['-cp', linux(jar), 'org.jlinalg.cli.NextflowToolsCli'] if old else ['-jar', linux(jar)]
                    with (folder / (label + '.stdout')).open('xb') as so, (folder / (label + '.stderr')).open('xb') as se:
                        result = subprocess.run(java + arguments, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=240)
                    save(folder / (label + '.json'), dict(command=java + arguments, exit_code=result.returncode, expected_exit=expected_exit))
                    assert result.returncode == expected_exit, (version, mode, label, result.returncode)

                code = 3 if mode in ['missing-tool', 'comma-config'] else 2 if mode in ['ignored-failure', 'missing-output'] else 0
                run('run', ['--recipe', linux(recipe_path), '--home', home, '--setup-report', report, '--out', session], code)
                if code == 0:
                    run('verify', ['--verify', session], 0)
                    run('resume', ['--resume', session, '--home', home, '--setup-report', report], 0)
                    run('verify-resume', ['--verify', session], 0)
                wsl('/usr/bin/python3', '-c', '''import shutil,sys
from pathlib import Path
s,d=map(Path,sys.argv[1:]);d.mkdir()
for p in s.iterdir():
 if p.is_file():shutil.copyfile(p,d/p.name)
for n in ('attempts','objects'):
 if (s/n).exists():shutil.copytree(s/n,d/n)
''', session, linux(folder / 'retained'))
                retained = folder / 'retained'
                if code:
                    assert not list(retained.rglob('COMPLETED.json'))
                    warnings = list(retained.rglob('WARNINGS.md')); assert warnings
                    assert all('Required action:' in p.read_text(encoding='utf8') for p in warnings)
                else:
                    attempt = retained / 'attempts/000002'
                    outputs = json.loads((attempt / 'outputs.json').read_bytes())
                    identity = outputs['members']['observed.txt']['pin']
                    assert (attempt / 'objects' / identity['sha256']).read_bytes() == expected
                    completion = json.loads((attempt / 'completion.json').read_bytes())
                    assert completion['stats']['cached'] == 1 and completion['stats']['succeeded'] == 0
                    command = json.loads((attempt / 'invocation.json').read_bytes())['command']
                    configs = command[command.index('-C') + 1].split(',')
                    assert len(configs) == 3 and configs[0].endswith('/nextflow.config') and configs[1].endswith('/settings/overlay.config')
                    for name, executable in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
                        checked = subprocess.run([str(executable), '-Xmx256m', '-jar', str(jar), '--verify', str(attempt)], capture_output=True, timeout=90)
                        (folder / (name + '-verify.stdout')).write_bytes(checked.stdout)
                        (folder / (name + '-verify.stderr')).write_bytes(checked.stderr)
                        assert checked.returncode == 0, (name, checked.stderr[-2000:])
                records.append(dict(version=version, mode=mode, expected_exit=code, passed=True))
                print(json.dumps(records[-1]), flush=True)
            return records

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(family, [('21.04.1', '21-v2'), ('22.10.6', '22'), ('24.04.4', '24')]))
        assert pin(jar) == artifact and all(pin(ROOT / n) == v for n, v in pins.items())
        state.update(status='PASS_NFCORE_PRIMARY_CONFIG_ENGINE_AND_LITERAL_CAPTURE_CONTROLS_ONLY', controls=sum(results, []),
            source_pins=pins, three_original_engines=True, native_planner_checked_against_frozen_capture=True,
            windows_java21_java25_standalone_verification=True, scientific_analysis=False, paper_reproduction=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
        state['required_action'] = 'No result admitted. Preserve every owned root and report, inspect the named original logs, correct the cause and rerun to NEW output paths. Do not change original software, scientific defaults or admission rules to turn a failed control into a success.'
        (out / 'WARNINGS.md').write_text('# FAIL2: nf-core foundation validation failed\n\n' + repr(error) + '\n\nRequired action: ' + state['required_action'] + '\n', encoding='utf8')
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable source, capture, output or failure-admission checks')
    raise SystemExit(main())
