#!/usr/bin/env python3
"""Install checksum-pinned, pristine RepeatMasker payloads in a NEW private home.

Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later.
This step constructs payloads. Scientific runtime admission is a separate gate.
"""
from pathlib import Path, PurePosixPath
import argparse
import ctypes
import ctypes.util
import hashlib
import io
import json
import os
import platform
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile

RECOVERY = (
    'Required action: read installation.json, WARNINGS.md and retained partials. '
    'Correct the named input, integrity or capacity failure and retry with NEW home/report paths. '
    'Missing/forbidden software or permission means SKIP3; leave dependent analysis skipped. '
    'Use --allow-install only on a permitted host; provide verified --offline-archives directories '
    'or separately authorize --allow-download. Linux x86_64 Python 3.11+ and existing libzstd '
    'are needed for setup. Historical FamDB additionally needs the exact interpreter named '
    'in the lock. This helper never installs OS packages, changes a global Python environment '
    'or runs a scientific search. Payload installation alone does not admit SEARCH. '
    'Keep all existing runtimes and failed attempts; Cell Ranger remains excluded.'
)


class Unavailable(Exception):
    """Dependency or permission unavailable: skip dependent analysis."""


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def fingerprint(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def canonical(path, new=False):
    path = Path(path).absolute()
    if str(path) != os.path.normpath(path) or not re.fullmatch(r'/[A-Za-z0-9_./+-]+', str(path)):
        raise Unavailable('Choose a canonical ASCII setup path without whitespace/metacharacters: ' + str(path))
    if new:
        if path.exists() or path.is_symlink():
            raise ValueError('Require a NEW path; existing paths are preserved: ' + str(path))
        if path.parent.resolve(strict=True) != path.parent:
            raise ValueError('Linked/noncanonical setup parent: ' + str(path))
    elif path.resolve(strict=True) != path:
        raise ValueError('Linked/noncanonical setup path: ' + str(path))
    return path


def safe_name(name):
    path = PurePosixPath(name)
    if not name or not path.parts or path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name or '\x00' in name:
        raise ValueError('Unsafe archive/member path: ' + repr(name))
    return str(path)


def check_file(path, pin):
    path = Path(path)
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or path.resolve(strict=True) != path:
        raise ValueError('Expected canonical regular pinned file: ' + str(path))
    if before.st_size != pin['bytes'] or sha(path) != pin['sha256']:
        raise ValueError('Pinned size/SHA256 differs: ' + str(path))
    after = path.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
            after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns):
        raise ValueError('Pinned file changed during verification: ' + str(path))


def copy_stream(stream, target, pin, executable=False):
    target.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    digest = hashlib.sha256()
    with target.open('xb') as output:
        while chunk := stream.read(min(1048576, pin['bytes'] - count + 1)):
            count += len(chunk)
            if count > pin['bytes']:
                raise ValueError('Complete selected file exceeds pinned byte capacity: ' + str(target))
            digest.update(chunk)
            output.write(chunk)
    if count != pin['bytes'] or digest.hexdigest() != pin['sha256']:
        raise ValueError('Complete selected file differs; partial retained: ' + str(target))
    target.chmod(0o755 if executable else 0o644)


def write_new(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as output:
        output.write(data)


def obtain(name, lock, cache, references, allow_download):
    pin = lock['archives'][name]
    target = cache / name
    if target.exists() or target.is_symlink():
        check_file(target, pin)
        return target
    for directory in references:
        source = directory / name
        if source.exists() or source.is_symlink():
            check_file(source, pin)
            with source.open('rb') as stream:
                copy_stream(stream, target, pin)
            check_file(source, pin)
            return target
    if not allow_download:
        raise Unavailable('Pinned archive absent and download not authorized: ' + name)
    if urllib.parse.urlparse(pin['url']).scheme != 'https':
        raise ValueError('Locked download requires HTTPS')
    part = cache / (name + '.part')
    request = urllib.request.Request(pin['url'], headers={'User-Agent': 'OmicsMatic-private-RepeatMasker-payload/1.0'})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            if urllib.parse.urlparse(response.geturl()).scheme != 'https':
                raise ValueError('Download redirected away from HTTPS')
            copy_stream(response, part, pin)
    except (urllib.error.URLError, TimeoutError) as unavailable:
        raise Unavailable('Pinned HTTPS archive is unavailable; retain any partial and use a permitted connection or verified offline archive: ' + name) from unavailable
    # Link a fully verified regular file without ever replacing an existing cache member.
    os.link(part, target)
    part.unlink()
    return target


def zstd_tar(data, maximum):
    library = ctypes.util.find_library('zstd')
    if not library:
        raise Unavailable('Existing libzstd is unavailable; no system installation attempted')
    zstd = ctypes.CDLL(library)
    zstd.ZSTD_decompress.argtypes = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_size_t]
    zstd.ZSTD_decompress.restype = ctypes.c_size_t
    zstd.ZSTD_isError.argtypes = [ctypes.c_size_t]
    zstd.ZSTD_isError.restype = ctypes.c_uint
    output = ctypes.create_string_buffer(maximum)
    size = zstd.ZSTD_decompress(output, maximum, data, len(data))
    if zstd.ZSTD_isError(size):
        raise ValueError('Invalid zstd payload or expanded byte capacity exceeded')
    return io.BytesIO(output.raw[:size])


def extract_tar(archive, selected, home, maximum_bytes, maximum_members, conda=None):
    if conda:
        with zipfile.ZipFile(archive) as zipped:
            wanted = 'pkg-' + archive.stem + '.tar.zst'
            if len(zipped.namelist()) != 3 or len(set(zipped.namelist())) != 3 or wanted not in zipped.namelist():
                raise ValueError('Pinned conda container inventory differs')
            info = zipped.getinfo(wanted)
            if info.file_size > conda['bytes'] or info.compress_size > conda['bytes']:
                raise ValueError('Conda compressed payload exceeds capacity')
            payload = zstd_tar(zipped.read(wanted), conda['expanded_limit'])
        source = tarfile.open(fileobj=payload, mode='r:')
    else:
        source = tarfile.open(archive, mode='r|gz')
    seen, found = set(), set()
    expanded = 0
    with source:
        for member in source:
            name = safe_name(member.name)
            if name in seen:
                raise ValueError('Duplicate archive member: ' + name)
            seen.add(name)
            expanded += member.size
            if member.size < 0 or expanded > maximum_bytes or len(seen) > maximum_members:
                raise ValueError('Complete archive expanded byte/member capacity exceeded')
            if name not in selected:
                continue  # No unselected member, link, device or executable is materialized.
            target, pin = selected[name]
            if not member.isfile() or member.size != pin['bytes']:
                raise ValueError('Selected tar member is not the pinned regular file: ' + name)
            stream = source.extractfile(member)
            with stream:
                copy_stream(stream, home / target, pin, bool(member.mode & 0o111))
            found.add(name)
    if found != set(selected):
        raise ValueError('Archive is missing complete selected payload members')


def extract_wheel(archive, selected, home, maximum_bytes, maximum_members):
    with zipfile.ZipFile(archive) as zipped:
        seen, found = set(), set()
        expanded = 0
        for member in zipped.infolist():
            name = safe_name(member.filename)
            if name in seen:
                raise ValueError('Duplicate wheel member')
            seen.add(name)
            expanded += member.file_size
            if expanded > maximum_bytes or len(seen) > maximum_members:
                raise ValueError('Wheel expanded byte/member capacity exceeded')
            if member.is_dir():
                continue
            if name not in selected or stat.S_IFMT(member.external_attr >> 16) not in (0, stat.S_IFREG):
                raise ValueError('Unexpected/nonregular selected wheel member: ' + name)
            target, pin = selected[name]
            if member.file_size != pin['bytes']:
                raise ValueError('Pinned wheel member size differs')
            with zipped.open(member) as stream:
                copy_stream(stream, home / target, pin, bool((member.external_attr >> 16) & 0o111))
            found.add(name)
        if found != set(selected):
            raise ValueError('Wheel is missing complete selected payload members')


def inventory(home, expected, maximum_bytes, maximum_members):
    files, directories = set(), {home}
    for name in expected:
        parent = (home / safe_name(name)).parent
        while parent != home:
            directories.add(parent)
            parent = parent.parent
    actual_directories = set()
    count = total = 0
    for parent, children, names in os.walk(home, followlinks=False):
        parent = Path(parent)
        actual_directories.add(parent)
        count += 1 + len(names)
        if count > maximum_members or parent.resolve(strict=True) != parent:
            raise ValueError('Payload directory/member capacity or canonical path differs')
        for child in children:
            if (parent / child).is_symlink():
                raise ValueError('Linked payload directory')
        for name in names:
            path = parent / name
            relative = path.relative_to(home).as_posix()
            if relative not in expected:
                raise ValueError('Unexpected payload member: ' + relative)
            check_file(path, expected[relative])
            total += path.stat().st_size
            if total > maximum_bytes:
                raise ValueError('Complete payload byte capacity exceeded')
            files.add(relative)
    if files != set(expected) or actual_directories != directories:
        raise ValueError('Complete payload file/directory inventory differs')
    return dict(files=len(files), members=count, bytes=total)


def install(args, setup, lock_raw, lock, home, report, cache, references):
    pin = lock['profiles'].get(args.profile)
    if pin is None:
        raise Unavailable('Unsupported profile; no edition is substituted: ' + args.profile)
    historical = pin['python_required']
    python = None
    if historical:
        if not args.python:
            raise Unavailable('Historical payload requires an explicitly declared exact CPython interpreter')
        if not Path(args.python).exists() and not Path(args.python).is_symlink():
            raise Unavailable('Pinned historical CPython interpreter is missing: ' + args.python)
        python = canonical(args.python)
        check_file(python, lock['python']['executable'])
        if not os.access(python, os.X_OK):
            raise Unavailable('Pinned CPython interpreter execution is forbidden')
    elif args.python:
        raise ValueError('Current custom-library payload does not consume --python')
    expected = {'repeatmasker/' + name: value for name, value in pin['source_members'].items()}
    expected.update(lock['common_members'])
    expected['runtime-lock.json'] = fingerprint(lock_raw)
    profile_raw = (args.profile + '\n').encode('ascii')
    expected['profile.txt'] = fingerprint(profile_raw)
    bootstrap = (setup / 'repeat_python_bootstrap.py').read_bytes()
    wrapper = None
    if historical:
        expected.update(lock['python']['scientific_members'])
        expected['python/repeat_python_bootstrap.py'] = fingerprint(bootstrap)
        wrapper = ('#!/bin/sh\nexec ' + str(python) + ' "-I" "-S" "-B" "' +
                   str(home / 'python/repeat_python_bootstrap.py') + '" "$@"\n').encode('utf-8')
        expected['bin/python3'] = fingerprint(wrapper)
    archives = {pin['source_archive']}
    archives.update(value['archive'] for value in expected.values() if 'archive' in value)
    planned = sum(value['bytes'] for value in expected.values()) + sum(lock['archives'][name]['bytes'] for name in archives)
    dirs = {home}
    for name in expected:
        parent = (home / safe_name(name)).parent
        while parent != home:
            dirs.add(parent)
            parent = parent.parent
    if planned + 4194304 > args.workspace_bytes or len(expected) + len(dirs) + len(archives) + 16 > args.file_count:
        raise ValueError('Complete payload/cache plus 4MiB/16-member diagnostics reserve exceeds declared capacities')
    if shutil.disk_usage(home.parent).free < planned + 4194304:
        raise ValueError('Insufficient free storage for the complete payload/cache and retained diagnostics')
    if any(lock['archives'][name].get('kind') == 'conda-zstd' for name in archives) and not ctypes.util.find_library('zstd'):
        raise Unavailable('Existing libzstd is unavailable; no system installation attempted')
    obtained = {name: obtain(name, lock, cache, references, args.allow_download) for name in sorted(archives)}
    home.mkdir()
    write_new(home / 'runtime-lock.json', lock_raw)
    write_new(home / 'profile.txt', profile_raw)
    # Original archive prefixes are pinned by the archive SHA and release/commit identity.
    prefix = 'RepeatMasker/' if historical else 'RepeatMasker-74d2b41c9f2378773e7d42c7f7aac88f7f8df840/'
    selected = {prefix + name: ('repeatmasker/' + name, value) for name, value in pin['source_members'].items()}
    extract_tar(obtained[pin['source_archive']], selected, home, args.workspace_bytes, args.file_count)
    for name, value in lock['common_members'].items():
        if 'bundled_notice' in value:
            with (setup / 'notices' / value['bundled_notice']).open('rb') as stream:
                copy_stream(stream, home / name, value)
    for archive in sorted(archives - {pin['source_archive']}):
        selected = {value['member']: (name, value) for name, value in expected.items() if value.get('archive') == archive}
        archive_pin = lock['archives'][archive]
        if archive_pin.get('kind') == 'wheel':
            extract_wheel(obtained[archive], selected, home, args.workspace_bytes, args.file_count)
        elif archive_pin.get('kind') == 'conda-zstd':
            extract_tar(obtained[archive], selected, home, args.workspace_bytes, args.file_count, archive_pin)
        elif set(selected) == {None}:
            name, value = selected[None]
            with obtained[archive].open('rb') as stream:
                copy_stream(stream, home / name, value, True)
        else:
            extract_tar(obtained[archive], selected, home, args.workspace_bytes, args.file_count)
    if historical:
        write_new(home / 'python/repeat_python_bootstrap.py', bootstrap)
        write_new(home / 'bin/python3', wrapper)
        (home / 'bin/python3').chmod(0o755)
    summary = inventory(home, expected, args.workspace_bytes, args.file_count)
    health = None
    if historical:
        environment = dict(PATH='/usr/bin:/bin', HOME=str(report), LANG='C.UTF-8', LC_ALL='C.UTF-8',
                           PYTHONDONTWRITEBYTECODE='1', OPENBLAS_NUM_THREADS='1', OMP_NUM_THREADS='1')
        with (report / 'python-health.stdout').open('xb') as out, (report / 'python-health.stderr').open('xb') as err:
            process = subprocess.run([str(home / 'bin/python3'), '--omics-runtime-health'], stdout=out, stderr=err,
                                     env=environment, timeout=60, start_new_session=True)
        if process.returncode:
            raise ValueError('Isolated pinned Python provider health failed; inspect retained stdout/stderr')
        health = json.loads((report / 'python-health.stdout').read_text(encoding='utf-8'))
        if [health['python'], health['numpy'], health['h5py']] != ['3.12.3', '2.5.4', '3.16.0'] or not all(
                health[name] for name in ['isolated', 'no_site', 'no_bytecode_writes']):
            raise ValueError('Isolated pinned Python provider health contract differs')
        summary = inventory(home, expected, args.workspace_bytes, args.file_count)
    for name, path in obtained.items():
        check_file(path, lock['archives'][name])
    return dict(status='PAYLOAD_INSTALLED_SEARCH_NOT_ADMITTED', exit_code=0, home=str(home), profile=args.profile,
                payload=summary, archive_names=sorted(archives), lock_sha256=hashlib.sha256(lock_raw).hexdigest(),
                pristine_source_and_configuration=True, global_environment_modified=False,
                scientific_runtime_admission_complete=False, public_search_available=False, python_health=health)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, epilog=RECOVERY)
    parser.add_argument('--home', required=True)
    parser.add_argument('--report', required=True)
    parser.add_argument('--cache', required=True)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--python')
    parser.add_argument('--offline-archives', action='append', default=[])
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--workspace-bytes', type=int, default=17179869184)
    parser.add_argument('--file-count', type=int, default=8192)
    args = parser.parse_args(argv)
    report = None
    result = None
    code = 2
    try:
        if not args.allow_install:
            raise Unavailable('Installation is not authorized: --allow-install absent')
        if platform.system() != 'Linux' or platform.machine().lower() not in ('x86_64', 'amd64') or sys.version_info < (3, 11):
            raise Unavailable('Payload setup requires Linux x86_64 Python 3.11+')
        if not (1048576 <= args.workspace_bytes <= 1000000000000 and 16 <= args.file_count <= 1000000):
            raise ValueError('Invalid explicit workspace byte/member capacities')
        home = canonical(args.home, new=True)
        report_candidate = canonical(args.report, new=True)
        cache = canonical(args.cache, new=not Path(args.cache).exists())
        references = [canonical(value) for value in args.offline_archives]
        locations = [home, report_candidate, cache]
        if any(a.is_relative_to(b) or b.is_relative_to(a) for i, a in enumerate(locations) for b in locations[i + 1:]):
            raise ValueError('Require disjoint home, report and cache paths')
        for directory in references:
            if not directory.is_dir():
                raise ValueError('Offline archive reference is not a real directory')
        if cache.exists() and not cache.is_dir():
            raise ValueError('Private archive cache is not a real directory')
        report_candidate.mkdir()
        report = report_candidate
        cache.mkdir(exist_ok=True)
        setup = Path(__file__).resolve(strict=True).parent
        lock_raw = (setup.parent / 'repeat-runtime-lock.json').read_bytes()
        lock = json.loads(lock_raw)
        if lock['schema_version'] != 1 or lock['tool'] != 'RepeatMasker':
            raise ValueError('Invalid bundled RepeatMasker lock identity')
        result = install(args, setup, lock_raw, lock, home, report, cache, references)
        code = 0
    except (Unavailable, PermissionError) as unavailable:
        code = 3
        result = dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED', exit_code=3, error=str(unavailable))
    except Exception as failure:
        result = dict(status='FAIL2_NO_PAYLOAD_ADMITTED', exit_code=2, error=repr(failure))
    if report is not None:
        try:
            write_new(report / 'installation.json', (json.dumps(result, indent=2, sort_keys=True) + '\n').encode('utf-8'))
            title = 'PAYLOAD ONLY: SEARCH NOT ADMITTED' if code == 0 else 'SKIP3 ANALYSIS SKIPPED' if code == 3 else 'FAIL2 INSTALLATION FAILED'
            write_new(report / 'WARNINGS.md', ('# ' + title + '\n\n' + result.get('error', title) + '\n\n' + RECOVERY + '\n').encode('utf-8'))
        except OSError as diagnostic:
            print('FAIL2 DIAGNOSTIC WRITE FAILED: ' + str(diagnostic) + '. Retain ' + str(report) + '\n' + RECOVERY, file=sys.stderr)
            return 2
    print(('PAYLOAD ONLY; SEARCH NOT ADMITTED' if code == 0 else 'SKIP3 ANALYSIS SKIPPED' if code == 3 else 'FAIL2 INSTALLATION FAILED') +
          ': ' + (str(report) if report is not None else result['error']) + '\n' + RECOVERY, file=sys.stderr)
    return code


if __name__ == '__main__':
    sys.dont_write_bytecode = True
    sys.exit(main())
