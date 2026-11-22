"""Install an exact private official PLINK1/2 profile; never changes the OS/PATH."""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import json
import os
import platform
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LOCK_PATH = Path(__file__).with_name('plink-runtime-lock.json')
LOCK = json.loads(LOCK_PATH.read_text(encoding='utf-8'))
MARKER = '.jlinalg-plink.json'


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def identity():
    return dict(profile=LOCK['profile'], runtime_lock_sha256=digest(LOCK_PATH), files=LOCK['files'])


def reparse(path):
    return path.is_symlink() or bool(getattr(path.lstat(), 'st_file_attributes', 0) & 0x400)


def available(home):
    try:
        marker = home / MARKER
        if reparse(home) or reparse(marker) or marker.stat().st_size > 65536:
            return False
        data = json.loads(marker.read_text(encoding='utf-8'))
        if any(data.get(key) != value for key, value in identity().items()):
            return False
        files = set()
        directories = set()
        entries = 0
        for path in home.rglob('*'):
            entries += 1
            if entries > 6:
                return False
            if reparse(path):
                return False
            if path.is_dir():
                directories.add(path.relative_to(home).as_posix())
            if path.is_file() and path != marker:
                files.add(path.relative_to(home).as_posix())
        return directories == {'plink1', 'plink2'} and files == set(LOCK['files']) and all(digest(home / name) == value for name, value in LOCK['files'].items())
    except (OSError, ValueError):
        return False


def extract(archive, engine, destination):
    pinned = LOCK['archives'][engine]
    if digest(archive) != pinned['sha256']:
        raise ValueError('Archive hash differs: ' + engine)
    with zipfile.ZipFile(archive) as stream:
        members = [entry for entry in stream.infolist() if not entry.is_dir()]
        names = [entry.filename for entry in members]
        if len(names) != len(pinned['members']) or set(names) != set(pinned['members']):
            raise ValueError('Unexpected or duplicate archive members: ' + engine)
        # Authenticate the entire pinned archive, including deliberately unused utilities.
        for entry in members:
            if entry.file_size > 64 * 1024 * 1024 or stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError('Oversized or linked archive member')
            if '\\' in entry.filename or '/' in entry.filename or ':' in entry.filename or entry.filename in {'.', '..'}:
                raise ValueError('Archive member is not a flat safe filename')
            body = stream.read(entry)
            if hashlib.sha256(body).hexdigest() != pinned['members'][entry.filename]:
                raise ValueError('Archive member hash differs: ' + entry.filename)
        for name, relative in pinned['extract'].items():
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(stream.read(name))


def download(url, path):
    request = urllib.request.Request(url, headers={'User-Agent': 'JLinAlg-private-PLINK-installer'})
    with urllib.request.urlopen(request, timeout=180) as response, path.open('wb') as output:
        if not response.geturl().startswith('https://'):
            raise ValueError('Refuse non-HTTPS redirect')
        size = 0
        while block := response.read(1024 * 1024):
            size += len(block)
            if size > 64 * 1024 * 1024:
                raise ValueError('Archive download exceeds 64 MiB')
            output.write(block)


def probe(home, attempt):
    environment = os.environ.copy()
    environment['PATH'] = str(Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32')
    results = {}
    for engine, values in LOCK['engines'].items():
        command = [str(home / values['executable']), '--version']
        result = subprocess.run(command, cwd=attempt, env=environment, capture_output=True, timeout=60)
        (attempt / (engine + '.stdout')).write_bytes(result.stdout)
        (attempt / (engine + '.stderr')).write_bytes(result.stderr)
        if result.returncode or result.stdout.decode('utf-8').strip() != values['version_stdout'] or result.stderr:
            raise RuntimeError('Original version probe differs: ' + engine)
        results[engine] = dict(command=command, exit_code=result.returncode,
                               stdout_sha256=digest(attempt / (engine + '.stdout')))
    return results


def install(args):
    requested_home = args.home.absolute()
    home = requested_home.resolve()
    archives = args.archives.resolve()
    report = args.report.resolve()
    attempt = report / 'attempts' / (dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ') + '-' + uuid.uuid4().hex[:12])
    attempt.mkdir(parents=True, exist_ok=False)
    record = dict(**identity(), environment=str(home), source_archives=LOCK['archives'],
                  allow_install=args.allow_install, allow_download=args.allow_download,
                  recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(), attempt=str(attempt))
    try:
        if sys.platform != 'win32' or platform.machine().lower() not in {'amd64', 'x86_64'}:
            record.update(status='skipped-platform-unavailable', error='This admitted profile requires Windows x86_64')
        elif os.path.lexists(requested_home) and reparse(requested_home):
            raise RuntimeError('Linked runtime home preserved; select a new --home without a link')
        elif available(home):
            record.update(status='available-admission-only;scientific-validation-separate',
                          runtime_reused=True, version_probes=probe(home, attempt))
        elif home.exists():
            raise RuntimeError('Existing unmanaged or changed home preserved; use a new --home')
        elif not args.allow_install:
            record.update(status='skipped-install-not-permitted', error='New private installation requires --allow-install')
        else:
            for engine, values in LOCK['archives'].items():
                archive = archives / values['filename']
                if not archive.exists():
                    if not args.allow_download:
                        raise RuntimeError('Missing archive ' + str(archive) + '; downloads require --allow-download')
                    archives.mkdir(parents=True, exist_ok=True)
                    with tempfile.TemporaryDirectory(dir=archives) as temporary:
                        candidate = Path(temporary) / 'download.zip'
                        download(values['url'], candidate)
                        if digest(candidate) != values['sha256']:
                            raise ValueError('Downloaded archive differs; nothing installed')
                        shutil.copy2(candidate, archive)
                if digest(archive) != values['sha256']:
                    raise ValueError('Changed cached archive preserved without execution: ' + str(archive))
            home.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=home.parent) as temporary:
                stage = Path(temporary)
                for engine, values in LOCK['archives'].items():
                    extract(archives / values['filename'], engine, stage)
                record['version_probes'] = probe(stage, attempt)
                (stage / MARKER).write_text(json.dumps(identity(), indent=2) + '\n', encoding='utf-8')
                if not available(stage):
                    raise ValueError('Staged private profile failed complete admission')
                stage.rename(home)
            record['status'] = 'installed-admission-only;scientific-validation-separate'
    except Exception as error:
        record.update(status='skipped-dependency-unavailable', error=str(error))
    record['required_action'] = ('Where permitted run python tools/literature/install_plink.py --home NEW_PRIVATE_HOME '
        '--archives ARCHIVE_DIRECTORY --allow-install; add --allow-download only when missing official downloads are allowed. '
        'Supply that exact sealed home to plink-tools --home or JLINALG_PLINK_HOME. If installation or the host is unavailable, '
        'skip the dependent original analysis (exit 3). Preserve changed/incomplete homes and diagnostics; never re-seal changed '
        'binaries or overwrite an existing home. Version/admission probes alone do not validate scientific workflows.')
    record['scope'] = ('Private original binaries and unchanged GPL text only. All official archive members are authenticated; '
        'unused bonus utilities/toy data are not installed. No OS/global installation, persistent PATH change or program patch. '
        'Windows system DLLs remain a host boundary. Analysis commands do not install software.')
    target = report / 'dependency-status.json'
    if target.exists():
        prior = json.loads(target.read_text(encoding='utf-8'))
        record['history'] = prior.get('history', []) + [{k: v for k, v in prior.items() if k != 'history'}]
    for path in [target, attempt / 'dependency-status.json']:
        path.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    skipped = record['status'].startswith('skipped')
    (report / 'WARNINGS.md').write_text(('# ORIGINAL PLINK ANALYSIS SKIPPED\n\n' + record.get('error', '') if skipped
        else '# PRIVATE PLINK RUNTIME ADMISSION ONLY') + '\n\nRequired action: ' + record['required_action'] + '\n\n' + record['scope'] + '\n', encoding='utf-8')
    print(json.dumps({k: record[k] for k in ['status', 'environment', 'required_action']}))
    return 3 if skipped else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, default=ROOT / 'build/omics-plink-official')
    parser.add_argument('--archives', type=Path, default=ROOT / 'build/literature-review/plink-runtime/downloaded-reference-1')
    parser.add_argument('--report', type=Path, default=ROOT / 'build/literature-review/plink-dependency')
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    return install(parser.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
