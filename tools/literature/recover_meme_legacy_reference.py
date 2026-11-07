# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recover completed legacy builds after explicitly permitted C-shell preparation; no analysis admission."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--failed-build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) or out.exists():
        raise ValueError('Use a NEW owned recovery directory')
    failed = json.loads(args.failed_build.read_bytes())
    if failed['edition'] not in {'4.9.1-base', '4.9.1-patch2'} or [s['name'] for s in failed['steps']] != ['python-version', 'configure', 'make', 'install']:
        raise ValueError('Require a complete configure/make/install attempt whose only missing step is legacy launcher probing')
    if any(s['exit_code'] or s['timed_out'] for s in failed['steps']):
        raise ValueError('A failed build step cannot be recovered by a runtime dependency')
    home = Path(failed['prefix'])
    source = Path(failed['linux_area']) / 'meme_4.9.1'
    out.mkdir(parents=True)
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-legacy-recovery-', dir='/var/tmp'))
    area.chmod(0o700)
    result = dict(schema_version=1, status='recovering-original-legacy-launcher', version='4.9.1', edition=failed['edition'],
                  prefix=str(home), linux_area=failed['linux_area'], recovery_area=str(area),
                  failed_build=dict(path=str(args.failed_build), **pin(args.failed_build)), recovery_source=pin(Path(__file__)),
                  preparation_steps=[], runtime_admitted=False, analysis_admitted=False, implementation_credit=False)
    (out / 'RECOVERY-SOURCE.py').write_bytes(Path(__file__).read_bytes())
    write(out / 'OWNER.json', result)

    def unchanged():
        files = {p.relative_to(home).as_posix(): pin(p) for p in home.rglob('*') if p.is_file() and not p.is_symlink()}
        links = {p.relative_to(home).as_posix(): os.readlink(p) for p in home.rglob('*') if p.is_symlink()}
        if files != failed['installed_files'] or links != failed['installed_symlinks']:
            raise RuntimeError('Complete installed original legacy payload changed')
        if any(pin(source / name) != value for name, value in failed['original_C_and_H'].items()):
            raise RuntimeError('Original legacy C/H scientific source changed')

    def run(name, command, timeout=120):
        env = dict(PATH='/usr/bin:/bin', HOME=str(area), TMPDIR=str(area), LC_ALL='C', DEBIAN_FRONTEND='noninteractive', PYTHONDONTWRITEBYTECODE='1')
        with (out / (name + '.log')).open('xb') as log:
            process = subprocess.Popen(command, cwd=area, env=env, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            timed_out = False
            try:
                code = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
                code = process.returncode
        row = dict(name=name, command=list(map(str, command)), exit_code=code, timed_out=timed_out, log_pin=pin(out / (name + '.log')))
        result['preparation_steps'].append(row)
        write(out / 'PROGRESS.json', result)
        print(failed['edition'] + ' ' + name + ': exit ' + str(code), flush=True)
        if code or timed_out:
            raise RuntimeError('Inspect failed original launcher preparation: ' + str(out / (name + '.log')))
        return (out / (name + '.log')).read_text()

    try:
        unchanged()
        if not Path('/bin/csh').exists():
            if not (args.allow_install and args.allow_download):
                raise PermissionError('SKIP3: original launcher requires /bin/csh; explicit permitted preparation is absent. Skip dependent analysis')
            plan = run('tcsh-plan', ['/usr/bin/apt-get', '--simulate', '--no-install-recommends', 'install', 'tcsh'])
            if not re.search(r'\b0 upgraded, 1 newly installed, 0 to remove\b', plan):
                raise PermissionError('SKIP3: dependency preparation would exceed one new package/no upgrades/no removals; use another permitted host')
            run('tcsh-install', ['/usr/bin/apt-get', '-y', '--no-install-recommends', 'install', 'tcsh'], 600)
        run('csh-health', ['/bin/csh', '-f', '-c', 'exit 0'])
        result['csh'] = dict(path=str(Path('/bin/csh').resolve()), **pin(Path('/bin/csh').resolve()))
        run('tcsh-package', ['/usr/bin/dpkg-query', '-W', '-f=${Package}\t${Version}\t${Status}\n', 'tcsh'])
        for name in ['meme', 'tomtom', 'centrimo']:
            if run(name + '-version', [str(home / 'bin' / name), '-version']).strip() != '4.9.1':
                raise RuntimeError('Original legacy launcher version differs: ' + name)
        unchanged()
        result.update(status='built-original-historical-MEME-reference-not-scientifically-admitted',
                      installed_files=failed['installed_files'], installed_symlinks=failed['installed_symlinks'],
                      original_C_and_H=failed['original_C_and_H'], unchanged_original_C_and_H=len(failed['original_C_and_H']),
                      original_build_source=failed['source'], original_build_steps=failed['steps'], archive=failed['archive'],
                      source_url=failed['source_url'], intended_use=failed['intended_use'],
                      required_action='Complete original build recovered with its unchanged C-shell launcher. This is reference preparation, not scientific admission. P155 patch level and P013 suite edition remain unstated; do not infer the author runtime identity.')
        write(out / 'COMPLETED.json', result)
    except Exception as failure:
        result.update(status='legacy-reference-unavailable-dependent-analysis-skipped', error=str(failure))
        write(out / 'FAILURE.json', result)
        (out / 'WARNINGS.md').write_text('SKIP3 — DEPENDENT ANALYSIS SKIPPED\n\n' + str(failure) +
            '\n\nRequired action: inspect the named build/preparation logs. Supply C shell and the exact original build on a permitted Linux host; if installation is forbidden, skip this analysis. Preserve this attempt and retry into a NEW area.\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    main()
