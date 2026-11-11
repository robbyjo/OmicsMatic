# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise public portable verification against consistently rehashed damaged bundles and real cancellation."""
import argparse
import concurrent.futures
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin
from validate_stringtie_gtf import save

def reseal(root):
    manifest = json.loads((root / 'manifest.json').read_bytes())
    for n, row in list(manifest['files'].items()):
        path = root / n
        if not path.exists(): del manifest['files'][n]
        elif row['kind'] == 'file': row.update(pin(path))
    for path in root.rglob('*'):
        name = path.relative_to(root).as_posix()
        if name not in ['manifest.json', 'COMPLETED.json'] and name not in manifest['files']:
            manifest['files'][name] = dict(kind='directory', mode=-1) if path.is_dir() else dict(kind='file', mode=-1, **pin(path))
    save(root / 'manifest.json', manifest)
    complete = json.loads((root / 'COMPLETED.json').read_bytes()); complete['manifest'] = pin(root / 'manifest.json'); save(root / 'COMPLETED.json', complete)

def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--jar', type=Path, required=True); parser.add_argument('--baseline', type=Path, required=True); parser.add_argument('--out', type=Path, required=True); parser.add_argument('--linux-root', required=True)
    args = parser.parse_args(); jar, baseline, out = args.jar.resolve(), args.baseline.resolve(), args.out.resolve(); base = ROOT / 'build/literature-review/stringtie-runtime'
    assert all(p.is_relative_to(base.resolve()) for p in [jar, baseline, out]) and jar.is_file() and (baseline / 'COMPLETED.json').is_file() and not out.exists()
    assert args.linux_root.startswith('/var/tmp/omicsmatic-stringtie-recovery-') and len(args.linux_root.split('/')) == 4
    out.mkdir(); save(out / 'INCOMPLETE.json', dict(status='STRINGTIE_RECOVERY_VALIDATION_IN_PROGRESS')); artifact = pin(jar); original = pin(baseline / 'manifest.json')
    sources = {n: pin(ROOT / n) for n in ['tools/literature/validate_stringtie_workflow_recovery.py', 'src/test/java/org/jlinalg/cli/StringTieWorkflowCancellationCheck.java']}
    try:
        cases = {}
        for label in ['table', 'audit', 'process', 'runtime-input', 'setup-request', 'extra', 'guard', 'missing-table']:
            target = out / 'cases' / label; shutil.copytree(baseline, target); cases[label] = target
            if label == 'table':
                table = target / 'fit/t_data.ctab'; lines = table.read_text().splitlines(); row = lines[1].split('\t'); row[-1] = '-1.000000'; lines[1] = '\t'.join(row); table.write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
            elif label == 'audit':
                file = target / 'metadata/OUTPUT-AUDIT.json'; d = json.loads(file.read_bytes()); d['transcripts'] += 1; save(file, d)
            elif label == 'process':
                file = target / 'fit/process.json'; d = json.loads(file.read_bytes()); d['command'][6] = '--core=1'; save(file, d)
            elif label == 'runtime-input':
                file = target / 'runtime/inputs/reads.bam'; raw = bytearray(file.read_bytes()); raw[-1] ^= 1; file.write_bytes(raw); reseal(target / 'runtime')
            elif label == 'setup-request':
                file = target / 'setup-report/request.json'; d = json.loads(file.read_bytes()); d['home'] = '/definitely-not-opened/nonexistent-managed-home'; save(file, d)
                file = target / 'setup-report/manifest.json'; d = json.loads(file.read_bytes()); d['files']['request.json'] = pin(target / 'setup-report/request.json'); save(file, d)
                file = target / 'setup-report/COMPLETED.json'; d = json.loads(file.read_bytes()); d['manifest'] = pin(target / 'setup-report/manifest.json'); save(file, d)
            elif label == 'extra': (target / 'unexpected.txt').write_text('extra complete file\n')
            elif label == 'guard': save(target / 'INCOMPLETE.json', dict(status='partial'))
            elif label == 'missing-table': (target / 'fit/e2t.ctab').unlink()
            reseal(target)
        records = []
        def command(label, argv, expected=0, timeout=180):
            argv = list(map(str, argv))
            with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
                process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=timeout, check=False)
            row = dict(label=label, command=argv, exit_code=process.returncode, expected_exit=expected); save(out / (label + '.process.json'), row); assert process.returncode == expected, row; return row
        platforms = [('windows-java21', [JAVA21 / 'java.exe'], str), ('windows-java25', [Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')], str), ('linux-java21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'], linux)]
        def test(platform):
            name, java, convert = platform; results = []
            for label, bundle in cases.items():
                key = name + '-' + label; row = command(key, [*java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', convert(jar), 'org.jlinalg.cli.StringTieToolsCli', '--verify', convert(bundle)], 2)
                message = (out / (key + '.stderr')).read_text(encoding='utf-8'); assert 'FAIL2' in message and 'NEW output paths' in message; results.append(row)
            return results
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            for group in pool.map(test, platforms): records.extend(group)
        classes = out / 'cancellation-classes'; classes.mkdir()
        command('compile-cancellation-driver', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-Xlint:all', '-Werror', '-cp', jar, '-d', classes, ROOT / 'src/test/java/org/jlinalg/cli/StringTieWorkflowCancellationCheck.java'])
        script = out / 'native-cancel.py'; script.write_text(CANCEL, encoding='utf-8', newline='\n')
        command('actual-user-cancellation', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', linux(script), args.linux_root, linux(jar), linux(classes), linux(baseline / 'metadata/recipe.json'), linux(out)])
        assert pin(jar) == artifact and pin(baseline / 'manifest.json') == original and all(pin(ROOT / n) == p for n,p in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-24-public-rehashed-invalid-bundle-rejections-and-actual-owned-user-fit-cancellation', artifact=artifact, baseline_manifest=original,
            sources=sources, invalid_cases=8, actual_cli_rejections=records, actual_user_fit_cancellation=True, cancelled_process_cleanup_complete=True, integration_credit=False)); (out / 'INCOMPLETE.json').unlink()
        print(json.dumps(dict(status='passed-StringTie-recovery-controls', consistently_rehashed_rejections=24, actual_cancelled_original_fit=True)))
    except BaseException as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_RECOVERY_CONTROLS_FAILED', error=str(error), required_action='Preserve this attempt and native partials; inspect original process logs, correct the cause and use NEW paths.')); raise

CANCEL = r'''
import json,shutil,subprocess,sys
from pathlib import Path
base,jar,classes,recipe,out=map(Path,sys.argv[1:]);assert not base.exists();base.mkdir(mode=0o700)
version=json.loads(recipe.read_bytes())['profile'].removeprefix('linux-x86_64-stringtie-');old=Path('/var/tmp/omicsmatic-stringtie-setup-20261103-1');work=base/'cancelled-user-fit'
command=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-cp',str(classes)+':'+str(jar),'org.jlinalg.cli.StringTieWorkflowCancellationCheck',str(recipe),str(old/('home-'+version)),str(old/('install-'+version)),str(work)]
with (out/'native-cancellation.stdout').open('xb') as stdout,(out/'native-cancellation.stderr').open('xb') as stderr:
 process=subprocess.run(command,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C'},stdout=stdout,stderr=stderr,timeout=160,check=False)
(out/'native-cancellation.process.json').write_text(json.dumps(dict(command=command,exit_code=process.returncode),indent=2)+'\n');assert process.returncode==0
shutil.copytree(work,out/'retained-cancelled-fit')
'''

if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete recovery validation')
    main()
