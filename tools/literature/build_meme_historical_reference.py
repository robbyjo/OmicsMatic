# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build separately pinned original MEME editions; no runtime or analysis admission."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import resource
import signal
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
EDITIONS = {
    '5.5.4': ('meme-5.5.4.tar.gz', 'meme-5.5.4', 'cda6011c2b855bf2563c4e7a2c255e11e99b5b6e5e73736ff008942507580153'),
    '4.9.1-base': ('meme_4.9.1.tar.gz', 'meme_4.9.1', '838f7e8862365e22d2585d65be78637ccbb59045b0fb1f60ce1165b770dff3c9'),
    '4.9.1-patch2': ('meme_4.9.1_2.tar.gz', 'meme_4.9.1', '4ab35c31be4434683d04fd4cb7015f83bc0c2ff007cf90220f304ffff7a71cb8'),
}


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def limits():
    resource.setrlimit(resource.RLIMIT_AS, (4 * 1024**3, 4 * 1024**3))
    resource.setrlimit(resource.RLIMIT_FSIZE, (512 * 1024**2, 512 * 1024**2))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--edition', choices=EDITIONS, required=True)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--python', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-build', action='store_true')
    parser.add_argument('--legacy-common-symbols', action='store_true',
                        help='Explicit GCC -fcommon for legacy C tentative definitions; original sources remain unchanged')
    parser.add_argument('--legacy-inline', action='store_true',
                        help='Explicit GCC -fgnu89-inline for the original GNU inline linkage semantics')
    args = parser.parse_args()
    if not args.allow_build or platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise SystemExit('SKIP3: a permitted Linux x86_64 compiler host and --allow-build are required; skip dependent analysis')
    filename, dirname, sha = EDITIONS[args.edition]
    out = args.out.resolve()
    if not out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) or out.exists():
        raise ValueError('Use a NEW owned meme-runtime output directory')
    archive_pin = pin(args.archive)
    if args.archive.name != filename or archive_pin['sha256'] != sha:
        raise ValueError('Original official archive differs; preserve it and skip unverified software')
    interpreter = args.python.resolve(strict=True)
    out.mkdir(parents=True)
    area = Path(tempfile.mkdtemp(prefix='omicsmatic-meme-historical-', dir='/var/tmp'))
    area.chmod(0o700)
    home = area / ('home-' + args.edition)
    record = dict(schema_version=1, status='building-private-original-historical-MEME-reference',
                  edition=args.edition, version='5.5.4' if args.edition == '5.5.4' else '4.9.1',
                  archive=archive_pin, source_url='https://meme-suite.org/meme/meme-software/' +
                  ('5.5.4' if args.edition == '5.5.4' else '4.9.1') + '/' + filename,
                  linux_area=str(area), prefix=str(home), interpreter=str(interpreter),
                  interpreter_pin=pin(interpreter), legacy_common_symbols=args.legacy_common_symbols,
                  legacy_inline=args.legacy_inline,
                  started=datetime.now(timezone.utc).isoformat(), steps=[], source=pin(Path(__file__)),
                  intended_use='educational-research-nonprofit; directly confirmed by user in this goal',
                  runtime_admitted=False, analysis_admitted=False, implementation_credit=False)
    write(out / 'OWNER.json', record)
    (out / 'BUILDER-SOURCE.py').write_bytes(Path(__file__).read_bytes())

    def run(name, command, cwd=area, timeout=1800):
        env = dict(PATH='/usr/bin:/bin', HOME=str(area), TMPDIR=str(area), LC_ALL='C',
                   PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1')
        with (out / (name + '.log')).open('xb') as log:
            process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True, preexec_fn=limits)
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
        row = dict(name=name, command=list(map(str, command)), cwd=str(cwd), environment=env,
                   exit_code=code, timed_out=timed_out, log_pin=pin(out / (name + '.log')))
        record['steps'].append(row)
        write(out / 'PROGRESS.json', record)
        print(args.edition + ' ' + name + ': exit ' + str(code), flush=True)
        if code or timed_out:
            raise RuntimeError(name + ' failed; inspect ' + str(out / (name + '.log')))

    try:
        run('python-version', [str(interpreter), '-V'], timeout=30)
        python_version = (out / 'python-version.log').read_text().strip()
        required = 'Python 3.' if args.edition == '5.5.4' else 'Python 2.7.'
        if not python_version.startswith(required):
            raise RuntimeError('Original edition requires ' + required + '; no Python source translation is allowed')
        with tarfile.open(args.archive, 'r:gz') as archive:
            members = archive.getmembers()
            if len(members) > 20000 or sum(m.size for m in members) > 800_000_000:
                raise RuntimeError('Original source exceeds declared development capacity')
            if any(not (m.name == dirname or m.name.startswith(dirname + '/')) or
                   m.issym() or m.islnk() or not (m.isfile() or m.isdir()) for m in members):
                raise RuntimeError('Unsupported source archive namespace/node')
            archive.extractall(area, filter='data')
        source = area / dirname
        before = {p.relative_to(source).as_posix(): pin(p) for p in source.rglob('*')
                  if p.is_file() and p.suffix in {'.c', '.h'}}
        write(out / 'ORIGINAL-C-H-PINS.json', before)
        record['original_C_and_H'] = before
        run('configure', ['/bin/sh', './configure', '--prefix=' + str(home), '--enable-serial',
                          '--enable-build-libxml2', '--enable-build-libxslt', '--with-python=' + str(interpreter)],
            source, 600)
        # The old configure script unconditionally replaces environment CFLAGS.
        # A command-line make variable is needed to select legacy common symbols.
        compatibility = (['-fcommon'] if args.legacy_common_symbols else []) + (['-fgnu89-inline'] if args.legacy_inline else [])
        make_flags = ['CFLAGS=-O3 ' + ' '.join(compatibility)] if compatibility else []
        run('make', ['/usr/bin/make', '-j2', *make_flags], source)
        run('install', ['/usr/bin/make', 'install', *make_flags], source, 600)
        if any(pin(source / name) != value for name, value in before.items()):
            raise RuntimeError('Original scientific C/H source changed during build')
        record['unchanged_original_C_and_H'] = len(before)
        record['installed_files'] = {p.relative_to(home).as_posix(): pin(p) for p in sorted(home.rglob('*'))
                                     if p.is_file() and not p.is_symlink()}
        record['installed_symlinks'] = {p.relative_to(home).as_posix(): os.readlink(p) for p in sorted(home.rglob('*')) if p.is_symlink()}
        for name, flag in [('meme', '-version'), ('tomtom', '-version'), ('centrimo', '-version')]:
            run(name + '-version', [str(home / 'bin' / name), flag], timeout=30)
            if (out / (name + '-version.log')).read_text().strip() != record['version']:
                raise RuntimeError('Original program version differs: ' + name)
        if args.edition == '5.5.4':
            run('streme-version', [str(home / 'bin/streme'), '--version'], timeout=30)
            if (out / 'streme-version.log').read_text().strip() != '5.5.4':
                raise RuntimeError('Original STREME version differs')
        if pin(args.archive) != archive_pin or pin(interpreter) != record['interpreter_pin']:
            raise RuntimeError('Source/interpreter changed')
        record.update(status='built-original-historical-MEME-reference-not-scientifically-admitted',
                      finished=datetime.now(timezone.utc).isoformat(),
                      paper_boundary='P155 patch level unstated; base/patch2 are separate profiles. P013 suite edition unstated; legacy command support does not identify the author edition. No paper environment, scientific outputs or historical equivalence are admitted by a successful build.')
        write(out / 'COMPLETED.json', record)
    except Exception as failure:
        record.update(status='failed-historical-MEME-build-dependent-analysis-skipped', error=str(failure))
        write(out / 'FAILURE.json', record)
        (out / 'WARNINGS.md').write_text('FAIL2: original build unavailable; dependent analysis SKIPPED.\n\n' + str(failure) +
            '\n\nRequired action: inspect the named log; prepare missing dependencies only where permitted, or use another permitted host. Preserve this attempt; retry in a NEW output and private build area. Never admit a partial runtime.\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    main()
