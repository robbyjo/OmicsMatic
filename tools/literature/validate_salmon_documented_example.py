# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented export/offline-setup/index/paired/single example through the actual root JAR."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import linux, pin
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable complete documented-example checks')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.artifact.resolve(), args.out.resolve()
    assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists()
    # Opening WSL while a native admission is live changes its resolver evidence.
    # This example must follow completion of the current four-edition batch.
    coverage = json.loads((BASE / 'quant-workflow-development-2/control-3/COMPLETED.json').read_bytes())
    assert set(coverage['selected_editions']) == {'1.0.0', '1.1.0', '1.5.2', '1.8.0'}
    public = json.loads((BASE / 'public-workflow-development-1/windows-matrix-1/COMPLETED.json').read_bytes())
    artifact, canonical = pin(jar), pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    assert public['artifact'] == dict(path=jar.relative_to(ROOT).as_posix(), **artifact)
    sources = dict(public['source_pins'])
    for path in [*ROOT.glob('examples/salmon-bulk/*'), Path(__file__)]:
        if path.is_file():
            sources[path.relative_to(ROOT).as_posix()] = pin(path)
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    archive = BASE / 'upstream-capture-1/1.9.0/salmon-1.9.0_linux_x86_64.tar.gz'
    lock = json.loads((ROOT / 'tools/literature/salmon-runtime-lock.json').read_bytes())['profiles']['linux-x86_64-salmon-1.9.0']['archive']
    assert pin(archive) == {k: lock[k] for k in ['bytes', 'sha256']}
    out.mkdir()
    write(out / 'SOURCE-PINS.json', sources)
    for name in sources:
        target = out / 'source-snapshot' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((ROOT / name).read_bytes())
    remote = subprocess.check_output(['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', 'import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-salmon-documented-",dir="/var/tmp"))']).decode().strip()
    assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-documented-[A-Za-z0-9_-]+', remote)
    write(out / 'OWNER.json', dict(owned_linux_root=remote))
    calls = []

    def command(name, argv, required=True):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=2400)
        receipt = dict(name=name, command=argv, exit_code=result.returncode)
        write(out / (name + '.json'), receipt)
        calls.append(receipt)
        if required:
            assert result.returncode == 0, (name, result.returncode)
        print(name + ':exit ' + str(result.returncode), flush=True)
        return result.returncode

    java = ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx512m', '-XX:ActiveProcessorCount=2', '-jar', linux(jar), 'salmon-tools']

    def substitute(value):
        if isinstance(value, str):
            return value.replace('/var/tmp/omicsmatic-salmon-demo-', remote + '/')
        if isinstance(value, list):
            return [substitute(v) for v in value]
        if isinstance(value, dict):
            return {k: substitute(v) for k, v in value.items()}
        return value

    converted = {}
    for name in ['index', 'paired', 'single']:
        source = ROOT / 'examples/salmon-bulk' / (name + '.json')
        declared = json.loads(source.read_bytes())
        prepared = substitute(declared)
        file = out / (name + '.json')
        write(file, prepared)
        converted[name] = dict(template=source.relative_to(ROOT).as_posix(), template_pin=pin(source), submitted=pin(file), recipe=file)
    success = False
    try:
        command('export', [*java, '--export-runtime-setup', remote + '/kit'])
        command('offline-install', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', remote + '/kit/install_salmon.py', '--profile', 'linux-x86_64-salmon-1.9.0',
                                   '--home', remote + '/home', '--report', remote + '/setup', '--cache', remote + '/cache', '--archive-dir', linux(archive.parent), '--allow-install'])
        for name in ['index', 'paired', 'single']:
            command(name, [*java, '--index-recipe' if name == 'index' else '--recipe', linux(converted[name]['recipe']), '--home', remote + '/home', '--setup-report', remote + '/setup', '--out', remote + '/' + name])
            command('verify-' + name, [*java, '--verify-index' if name == 'index' else '--verify', remote + '/' + name])
        success = True
    finally:
        # Runtime/cache bytes stay in the owned Linux area. Capture every node
        # of the kit, setup evidence and analysis attempts, including failures.
        capture = '''from pathlib import Path
import hashlib,json,shutil,stat,sys
root=Path(sys.argv[1]);target=Path(sys.argv[2]);assert root.is_dir() and not target.exists();target.mkdir()
def inventory():
 result={}
 for area in ['kit','setup','index','paired','single']:
  base=root/area
  if not base.exists():continue
  for p in [base,*sorted(base.rglob('*'))]:
   mode=p.lstat().st_mode;name=p.relative_to(root).as_posix();assert not p.is_symlink()
   row=dict(kind='directory' if stat.S_ISDIR(mode) else 'file',mode=stat.S_IMODE(mode))
   if not stat.S_ISDIR(mode):
    assert stat.S_ISREG(mode)
    with p.open('rb') as f:row.update(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
   result[name]=row
 return result
before=inventory()
for area in ['kit','setup','index','paired','single']:
 if (root/area).exists():shutil.copytree(root/area,target/area,copy_function=shutil.copyfile)
assert before==inventory()
print(json.dumps(dict(complete_native_captured_files=before,runtime_home_retained_linux=str(root/'home'),cache_retained_linux=str(root/'cache'))))
'''
        command('capture', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', '-I', '-B', '-c', capture, remote, linux(out / ('captured' if success else 'FAILED-captured'))])
        receipt = json.loads((out / 'capture.stdout').read_bytes())
        captured = out / ('captured' if success else 'FAILED-captured')
        assert set(receipt['complete_native_captured_files']) == {p.relative_to(captured).as_posix() for p in captured.rglob('*')}
        for name, row in receipt['complete_native_captured_files'].items():
            assert (captured / name).is_dir() == (row['kind'] == 'directory')
            if row['kind'] == 'file':
                assert pin(captured / name) == {k: row[k] for k in ['bytes', 'sha256']}
        write(out / ('CAPTURED.json' if success else 'FAILURE-CAPTURE.json'), receipt)
    assert success
    fits = []
    for name, bootstraps in [('paired', 100), ('single', 5)]:
        audit = json.loads((out / 'captured' / name / 'OUTPUT-AUDIT.json').read_bytes())
        assert audit['complete_targets'] == 40 and audit['complete_bootstrap_values'] == 40 * bootstraps and audit['processed_fragments'] == 10020
        fits.append(dict(name=name, audit=audit, completed=pin(out / 'captured' / name / 'COMPLETED.json')))
    assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical and pin(archive) == {k: lock[k] for k in ['bytes', 'sha256']}
    assert all(pin(ROOT / name) == value for name, value in sources.items())
    write(out / 'COMPLETED.json', dict(status='passed-documentation-templates-export-offline-install-index-paired-single-root-example', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact),
          source_pins=sources, original_runtime_archive=pin(archive), submitted_recipes={k: {a: b for a, b in row.items() if a != 'recipe'} for k, row in converted.items()},
          calls=calls, fits=fits, complete_capture=pin(out / 'CAPTURED.json'), canonical_executable_unchanged=canonical,
          scope='Only the documented absolute path prefix is substituted with a NEW owned Linux area. Scientific choices/capacities match the supplied templates. '
          'The explicitly permitted offline installation uses an already exact official archive; no download is needed. Every captured kit/setup/index/analysis member is retained; '
          'managed runtime/cache bytes remain in the separately named owned Linux home. Two controlled fits cover80 rows and4200 bootstrap values over the reused synthetic population. '
          'Installer and native runtime health fits are separate from those totals. This is not biological validation or whole-paper reproduction.', released=False, deployed=False, Salmon_catalog_status='missing'))


if __name__ == '__main__':
    main()
