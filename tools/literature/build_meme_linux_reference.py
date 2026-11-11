# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a separate original MEME 5.5.9 Linux reference; this does not admit analysis."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE_SHA = '0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12'


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-build', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    if not args.allow_build or platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise SystemExit('SKIP3: explicit --allow-build and a permitted Linux x86_64 build host are required; skip dependent analysis')
    if not out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) or out.exists():
        raise ValueError('Use a NEW owned meme-runtime output directory')
    if pin(args.archive)['sha256'] != ARCHIVE_SHA:
        raise ValueError('Original official MEME 5.5.9 archive differs; preserve it and skip unverified software')
    out.mkdir(parents=True)
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-build-', dir='/var/tmp'))
    area.chmod(0o700)
    home = area / 'home-5.5.9'
    record = dict(schema_version=1, status='building-private-original-MEME-reference', version='5.5.9',
                  original_archive=pin(args.archive), source_url='https://meme-suite.org/meme/meme-software/5.5.9/meme-5.5.9.tar.gz',
                  linux_area=str(area), prefix=str(home), steps=[], started=datetime.now(timezone.utc).isoformat(),
                  scientific_analysis_run=False, runtime_admitted=False, project_integration_credited=False,
                  intended_use='educational-research-nonprofit; directly confirmed by user in this goal',
                  script_pin=pin(Path(__file__)))
    write(out / 'OWNER.json', record)

    def run(name, command, cwd, timeout=1800):
        env = {key: value for key, value in os.environ.items() if key not in {'LD_PRELOAD', 'LD_LIBRARY_PATH', 'LIBRARY_PATH', 'CFLAGS', 'CPPFLAGS', 'LDFLAGS', 'MEME_DIR', 'MEME_BIN_DIR', 'MEME_ETC_DIR'}}
        env.update(PATH='/usr/bin:/bin', LC_ALL='C', HOME=str(area))
        with (out / (name + '.log')).open('wb') as log:
            process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
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
        row = dict(name=name, command=command, cwd=str(cwd), exit_code=code, timed_out=timed_out, log_pin=pin(out / (name + '.log')))
        record['steps'].append(row)
        write(out / 'PROGRESS.json', record)
        print(name + ': exit ' + str(code), flush=True)
        if code or timed_out:
            raise RuntimeError(name + ' failed; inspect ' + str(out / (name + '.log')))

    try:
        if not all(shutil.which(name) for name in ['gcc', 'g++', 'make', 'perl']):
            raise RuntimeError('Required compiler/make/Perl unavailable; prepare only where permitted and skip this dependent build/analysis')
        with tarfile.open(args.archive, 'r:gz') as archive:
            members = archive.getmembers()
            if len(members) > 20000 or sum(member.size for member in members) > 800_000_000:
                raise RuntimeError('Source archive exceeds declared development capacities')
            archive.extractall(area, filter='data')
        source = area / 'meme-5.5.9'
        before = {path.relative_to(source).as_posix(): pin(path) for path in source.rglob('*') if path.is_file() and path.suffix in {'.c', '.h'}}
        record['original_C_and_H'] = before
        write(out / 'ORIGINAL-SOURCE-PINS.json', before)
        run('configure', ['/bin/sh', './configure', '--prefix=' + str(home), '--enable-serial', '--enable-build-libxml2', '--enable-build-libxslt', '--with-python=/usr/bin/python3'], source, 600)
        run('make', ['/usr/bin/make', '-j2'], source)
        run('install', ['/usr/bin/make', 'install'], source, 600)
        changed = {name: pin(source / name) for name, value in before.items() if pin(source / name) != value}
        if changed:
            record['changed_original_C_and_H'] = changed
            raise RuntimeError('Original C/H source changed during build; do not admit this runtime')
        record['unchanged_original_C_and_H'] = len(before)
        record['installed_files'] = {path.relative_to(home).as_posix(): pin(path) for path in sorted(home.rglob('*')) if path.is_file() and not path.is_symlink()}
        record['installed_symlinks'] = {path.relative_to(home).as_posix(): os.readlink(path) for path in sorted(home.rglob('*')) if path.is_symlink()}
        for name, flag in [('meme', '-version'), ('tomtom', '-version'), ('streme', '--version'), ('centrimo', '-version')]:
            run(name + '-version', [str(home / 'bin' / name), flag], area, 30)
        if pin(args.archive) != record['original_archive']:
            raise RuntimeError('Original source archive changed')
        record.update(status='built-original-Linux-MEME-Suite-reference-not-scientifically-admitted', finished=datetime.now(timezone.utc).isoformat(),
                      scope='Complete unchanged original C/H sources and local serial installation, version probes only. No Java adapter, scientific output validation, historical edition equivalence, full-paper reproduction or current-host runtime admission is claimed. Earlier FIMO source trees and installed profiles are unchanged.')
        write(out / 'COMPLETED.json', record)
        print(record['status'], flush=True)
    except Exception as failure:
        record.update(status='failed-original-MEME-reference-build-analysis-not-run', error=str(failure))
        write(out / 'FAILURE.json', record)
        (out / 'WARNINGS.md').write_text('# FAIL2 — ORIGINAL MEME BUILD NOT ADMITTED\n\n' + str(failure) + '\n\nRequired action: inspect the named original build log and every dependency. Install/download only where permitted; otherwise SKIP the dependent build and analysis. Preserve this complete attempt and use a NEW area after correction. No failed build or version probe establishes scientific validity.\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    main()
