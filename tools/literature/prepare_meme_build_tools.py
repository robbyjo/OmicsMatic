# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit development-only Linux compiler preparation; never called by analysis."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--with-zlib-development', action='store_true', help='Explicitly prepare the original build\'s zlib development dependency')
    parser.add_argument('--with-report-helpers', action='store_true', help='Explicitly prepare original XML/PNG report dependencies')
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) or out.exists():
        raise ValueError('Use a NEW owned meme-runtime output directory')
    out.mkdir(parents=True)
    record = dict(schema_version=1, status='running-development-build-tool-preparation', steps=[],
                  platform=platform.platform(), started=datetime.now(timezone.utc).isoformat(),
                  allow_install=args.allow_install, allow_download=args.allow_download,
                  scientific_analysis_run=False, runtime_admission=False,
                  script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    write(out / 'STARTED.json', record)

    def run(name, command, timeout=600):
        env = dict(os.environ, DEBIAN_FRONTEND='noninteractive', LC_ALL='C')
        with (out / (name + '.log')).open('wb') as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
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
        raw = (out / (name + '.log')).read_bytes()
        record['steps'].append(dict(name=name, command=command, exit_code=code, timed_out=timed_out,
                                    log_bytes=len(raw), log_sha256=hashlib.sha256(raw).hexdigest()))
        write(out / 'PROGRESS.json', record)
        print(name + ': exit ' + str(code), flush=True)
        if code or timed_out:
            raise RuntimeError(name + ' failed; read ' + str(out / (name + '.log')))
        return raw

    code = 0
    try:
        if platform.system() != 'Linux' or platform.machine() != 'x86_64' or not shutil.which('apt-get'):
            code = 3
            raise RuntimeError('This explicit development helper requires Linux x86_64 with apt-get; provide a permitted supported compiler host or skip dependent build/analysis')
        names = ['gcc', 'g++', 'make']
        packages = names + (['zlib1g-dev'] if args.with_zlib_development else []) + (['libxml-parser-perl', 'ghostscript'] if args.with_report_helpers else [])
        record['before_tools'] = {name: shutil.which(name) for name in names}
        record['packages_before'] = run('packages-before', ['dpkg-query', '-W', '-f=${Package}\t${Version}\t${Status}\n']).decode()
        installed = {row.split('\t')[0] for row in record['packages_before'].splitlines() if row.endswith('\tinstall ok installed')}
        record['requested_packages'] = packages
        if not (all(record['before_tools'].values()) and set(packages) <= installed):
            if not (args.allow_install and args.allow_download):
                code = 3
                raise RuntimeError('Required compiler/make absent and both explicit preparation permissions were not supplied; skip dependent build/analysis')
            request = ['apt-get', '-y', '--no-install-recommends', 'install', *packages]
            plan = run('simulate', ['apt-get', '--simulate', '--no-install-recommends', 'install', *packages]).decode()
            if not re.search(r'\b0 upgraded, \d+ newly installed, 0 to remove\b', plan):
                code = 3
                raise RuntimeError('Preparation refuses upgrades/removals; review a separate host preparation or use another permitted host and skip this dependent build')
            run('download', [*request[:1], '--download-only', *request[1:]])
            archive_cache = out / 'downloaded-debs'
            archive_cache.mkdir()
            packages = []
            for path in sorted(Path('/var/cache/apt/archives').glob('*.deb')):
                target = archive_cache / path.name
                shutil.copyfile(path, target)
                packages.append(dict(path=target.name, bytes=target.stat().st_size,
                                     sha256=hashlib.file_digest(target.open('rb'), 'sha256').hexdigest()))
            record['downloaded_packages'] = packages
            if not packages:
                raise RuntimeError('APT download produced no retained packages; do not install an unrecorded preparation')
            run('install', [*request[:1], '--no-download', *request[1:]])
        record['after_tools'] = {name: shutil.which(name) for name in names}
        if not all(record['after_tools'].values()):
            raise RuntimeError('Compiler/make still unavailable after preparation')
        for name in names:
            run(name.replace('+', 'plus') + '-version', [record['after_tools'][name], '--version'], 30)
        record['packages_after'] = run('packages-after', ['dpkg-query', '-W', '-f=${Package}\t${Version}\t${Status}\n']).decode()
        record['status'] = 'prepared-development-compiler-and-make-not-scientific-runtime'
        write(out / 'COMPLETED.json', record)
    except Exception as failure:
        code = code or 2
        record.update(status='skipped-development-build-tools' if code == 3 else 'failed-development-build-tools', error=str(failure), exit_code=code)
        write(out / 'FAILURE.json', record)
        (out / 'WARNINGS.md').write_text('# ' + ('SKIP3' if code == 3 else 'FAIL2') + ' — BUILD TOOLS NOT PREPARED\n\n' + str(failure) + '\n\nRequired action: inspect every preparation log and named dependency. Install/download only where permitted; otherwise skip the dependent MEME build and analysis. Retain this attempt and use a NEW preparation output. No analysis or runtime admission is claimed.\n', encoding='utf-8')
        print(record['error'], flush=True)
    raise SystemExit(code)


if __name__ == '__main__':
    main()
