#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install an exact private IQ-TREE release and run a bounded synthetic smoke test.

Linux x86_64, Python 3.11+, permitted prlimit/setsid and a fresh report directory
are required. Downloads and new installation need separate explicit flags. No
archive installer, shell, package manager or system configuration is executed.
Failed files and original outputs are retained. Setup admits no user analysis.
"""
import argparse
import datetime
import decimal
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import platform
import re
import signal
import stat
import struct
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
LOCK_PATH = HERE / 'iqtree-runtime-lock.json'
LOCK_SHA256 = '2d80b924a35c9ad6dad675ca9905c4595dc1ccf8e2166116f8a001dc36a0a4f7'
MARKER = '.omicsmatic-iqtree.json'
VERSIONS = ('1.6.12', '2.1.2', '2.1.4-beta', '2.2.0.3', '2.3.1', '2.3.5', '2.3.6', '2.4.0')
ACTION = (
    'Skip the dependent analysis while the exact permitted runtime is unavailable. '
    'Use Linux x86_64 Python 3.11+ (Linux Python inside WSL on Windows), permitted '
    '/usr/bin/prlimit and /usr/bin/setsid, and fresh private home/report paths. '
    'Run python3 tools/literature/install_iqtree.py --profile linux-x86_64-iqtree-2.3.6 '
    '--home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT_DIRECTORY '
    '--allow-install; select the exact requested admitted edition. Add --allow-download '
    'only where downloads are permitted, or use --archive-dir OFFLINE_ARCHIVE_DIRECTORY. '
    'Never substitute another edition for unresolved 1.6.619, untested dating or another '
    'unsupported capability. Preserve corrupt caches, partial downloads, changed homes '
    'and failure logs; require report/COMPLETED.json with matching report and runtime '
    'hashes, correct the named cause and use fresh paths. If installation or '
    'execution is forbidden, keep the analysis skipped. Setup and its four-taxon JC69 '
    'smoke test admit no user analysis or whole-paper result. Cell Ranger remains excluded.'
)


class Unavailable(Exception):
    """Unavailable or forbidden prerequisites require a skipped dependent analysis."""


def sha(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def write_json(path, value):
    raw = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    with Path(path).open('xb') as target:
        target.write(raw)


def read_json(path):
    with Path(path).open('rb') as source:
        raw = source.read(262145)
    if len(raw) > 262144:
        raise ValueError('Complete JSON metadata byte capacity exceeded')
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(raw.decode('utf-8'), object_pairs_hook=pairs)


def private(path, exists=False):
    path = Path(os.path.abspath(path))
    if any(ord(c) < 32 or ord(c) == 127 for c in str(path)):
        raise ValueError('Control character in private path')
    for parent in [path, *path.parents]:
        if parent.is_symlink():
            raise ValueError('Linked private path or ancestor: ' + str(path))
    if exists:
        if path.resolve(strict=True) != path:
            raise ValueError('Noncanonical private path: ' + str(path))
    else:
        private(path.parent, True)
    return path


def fingerprint(path, maximum=67108864):
    path = private(path, True)
    before = path.stat()
    if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or not 0 <= before.st_size <= maximum:
        raise ValueError('Linked, nonregular or oversized file: ' + str(path))
    result = dict(bytes=before.st_size, sha256=sha(path))
    after = path.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_mode) != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_mode):
        raise ValueError('File changed while hashing: ' + str(path))
    return result


def check_file(path, expected):
    actual = fingerprint(path)
    if actual != {key: expected[key] for key in ['bytes', 'sha256']}:
        raise ValueError('Missing or changed pinned file: ' + str(path))
    return actual


def member_name(name):
    path = PurePosixPath(name)
    if not name or path.is_absolute() or '\\' in name or any(p in {'', '.', '..'} for p in name.rstrip('/').split('/')) or any(ord(c) < 32 for c in name):
        raise ValueError('Unsafe archive/runtime member name')
    return path


def load_kit():
    if sha(LOCK_PATH) != LOCK_SHA256:
        raise ValueError('Runtime lock changed; preserve the source and inspect its provenance')
    lock = read_json(LOCK_PATH)
    if lock['schema_version'] != 1 or lock['tool'] != 'IQ-TREE' or set(lock['profiles']) != {'linux-x86_64-iqtree-' + v for v in VERSIONS}:
        raise ValueError('Unsupported runtime lock schema/profile set')
    for name, expected in lock['bundled'].items():
        member_name(name)
        check_file(HERE / 'iqtree-runtime' / name, expected)
    return lock


def host():
    if sys.version_info < (3, 11) or platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('This exact runtime requires Linux x86_64 and Python 3.11+; use Linux Python inside WSL on Windows')
    if not hasattr(os, 'waitid') or not hasattr(os, 'WNOWAIT'):
        raise Unavailable('Owned-process admission requires waitid/WNOWAIT')
    if Path('/etc/ld.so.preload').exists() or Path('/etc/ld.so.preload').is_symlink():
        raise Unavailable('Operating-system loader preloads are outside this profile')
    helpers = {}
    for name in ['/usr/bin/prlimit', '/usr/bin/setsid']:
        path = Path(name)
        if not path.is_file() or not os.access(path, os.X_OK):
            raise Unavailable('Required permitted OS helper unavailable: ' + name)
        real = path.resolve(strict=True)
        helpers[name] = dict(real_path=str(real), **fingerprint(real))
    return dict(os='Linux', architecture='x86_64', kernel=platform.release(), helpers=helpers)


def static_elf(raw):
    if len(raw) < 64 or raw[:7] != b'\x7fELF\x02\x01\x01' or struct.unpack_from('<HHI', raw, 16) != (2, 62, 1):
        raise ValueError('Program must be the pinned ELF64 little-endian x86_64 ET_EXEC release')
    offset = struct.unpack_from('<Q', raw, 32)[0]
    size, count = struct.unpack_from('<HH', raw, 54)
    if struct.unpack_from('<H', raw, 52)[0] != 64 or offset < 64 or size != 56 or not 0 < count <= 64 or offset + size * count > len(raw):
        raise ValueError('Invalid complete ELF program-header dimensions')
    headers = []
    for index in range(count):
        kind, flags, start, virtual, physical, file_size, memory, alignment = struct.unpack_from('<IIQQQQQQ', raw, offset + size * index)
        if kind in {2, 3}:
            raise ValueError('PT_DYNAMIC/PT_INTERP is forbidden in this static executable profile')
        if start + file_size > len(raw) or kind == 1 and memory < file_size:
            raise ValueError('ELF segment exceeds the complete file/memory extent')
        if kind == 0x6474e551 and flags & 1:
            raise ValueError('Executable ELF stack is outside this profile')
        headers.append(dict(type=kind, flags=flags, offset=start, file_bytes=file_size, memory_bytes=memory, alignment=alignment))
    if not any(item['type'] == 1 for item in headers):
        raise ValueError('ELF contains no loadable segment')
    return dict(PT_INTERP_absent=True, PT_DYNAMIC_absent=True, headers=headers)


def obtain(pin, cache, archives, allow_download):
    candidates = [cache / pin['name'], *[directory / pin['name'] for directory in archives]]
    for path in candidates:
        if path.exists() or path.is_symlink():
            check_file(path, pin)
            return path
    if not allow_download:
        raise Unavailable('Exact archive absent; explicit --allow-download or a permitted --archive-dir is required')
    target = cache / pin['name']
    partial = cache / (pin['name'] + '.partial')
    if partial.exists() or partial.is_symlink():
        raise ValueError('Prior partial download preserved; inspect it and use a fresh cache')
    url = pin['url']
    if not url.startswith(('https://github.com/iqtree/iqtree2/releases/download/', 'https://github.com/Cibiv/IQ-TREE/releases/download/')):
        raise ValueError('Download URL is outside the pinned official release sources')
    with urllib.request.urlopen(url, timeout=30) as source, partial.open('xb') as output:
        count = 0
        while block := source.read(1048576):
            count += len(block)
            if count > pin['bytes']:
                raise ValueError('Complete archive byte capacity exceeded; partial download retained')
            output.write(block)
        download_identity = os.fstat(output.fileno())
    check_file(partial, pin)
    # Exclusive hardlink publication never replaces a concurrently created cache.
    os.link(partial, target, follow_symlinks=False)
    now = partial.lstat()
    if (now.st_dev, now.st_ino) != (download_identity.st_dev, download_identity.st_ino):
        raise ValueError('Download path was replaced; preserve both cache paths')
    partial.unlink()
    check_file(target, pin)
    return target


def extract(archive_path, pin, home):
    before = fingerprint(archive_path)
    expected = {value['archive_member']: (name, value) for name, value in pin['files'].items() if 'archive_member' in value}
    directory_name = PurePosixPath(next(iter(expected))).parts[0] + '/bin'
    seen = set()
    with tarfile.open(archive_path, 'r:gz') as archive:
        members = archive.getmembers()
        if len(members) != 6:
            raise ValueError('Complete original archive member count differs')
        for item in members:
            path = member_name(item.name)
            if item.isdir():
                if item.name.rstrip('/') != directory_name:
                    raise ValueError('Unexpected original archive directory')
                continue
            if not item.isfile() or item.name not in expected or item.name in seen:
                raise ValueError('Extra, linked, duplicate or unknown archive member')
            seen.add(item.name)
            name, wanted = expected[item.name]
            if item.size != wanted['bytes']:
                raise ValueError('Original archive member byte count differs')
            target = home / member_name(name)
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            with archive.extractfile(item) as source, target.open('xb') as output:
                while block := source.read(1048576):
                    output.write(block)
            target.chmod(0o555 if name == pin['program'] else 0o444)
            check_file(target, wanted)
    if seen != set(expected) or fingerprint(archive_path) != before:
        raise ValueError('Incomplete archive extraction or source archive changed')


def inventory(root, maximum=8388608, file_limit=128):
    total = count = 0
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            try:
                info = path.lstat()
            except FileNotFoundError:
                continue  # An original checkpoint rename may race a live census.
            if stat.S_ISLNK(info.st_mode) or not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode)) or stat.S_ISREG(info.st_mode) and info.st_nlink != 1:
                raise ValueError('Linked or nonregular owned output: ' + str(path))
            count += 1
            if stat.S_ISREG(info.st_mode):
                total += info.st_size
            if count > file_limit or total > maximum:
                raise ValueError('Complete owned output file/byte capacity exceeded')


def group_members(group):
    result = []
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():
            continue
        try:
            text = (path / 'stat').read_text()
            fields = text[text.rfind(')') + 2:].split()
            if int(fields[2]) == group and fields[0] != 'Z':
                result.append(int(path.name))
        except (OSError, ValueError):
            continue
    return sorted(result)


def owned_run(command, work, wall_seconds=30, maximum_bytes=8388608):
    """Reserve the leader PID with WNOWAIT until cleanup of its own group finishes."""
    child = None
    failure = None
    code = None
    terminal = False
    start = time.monotonic()
    env = dict(PATH='/usr/bin:/bin', HOME=str(work), TMPDIR=str(work), LANG='C', LC_ALL='C', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
    try:
        with (work / 'process.stdout').open('xb') as stdout, (work / 'process.stderr').open('xb') as stderr:
            try:
                child = subprocess.Popen(command, cwd=work, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
            except (PermissionError, FileNotFoundError) as error:
                raise Unavailable('Required process launch unavailable or forbidden: ' + str(error)) from error
            write_json(work / 'running.json', dict(pid=child.pid, process_group=child.pid, command=command))
            while True:
                if os.waitid(os.P_PID, child.pid, os.WEXITED | os.WNOHANG | os.WNOWAIT) is not None:
                    terminal = True
                    break
                inventory(work, maximum_bytes)
                if time.monotonic() - start > wall_seconds:
                    raise RuntimeError('Owned process wall-time capacity exceeded')
                time.sleep(.02)
    except (Exception, KeyboardInterrupt) as error:
        failure = error
    finally:
        live = []
        if child is not None:
            live = group_members(child.pid)
            if live:
                if terminal and failure is None:
                    failure = RuntimeError('Terminal process left live owned descendants; completion refused')
                try:
                    os.killpg(child.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                until = time.monotonic() + .3
                while group_members(child.pid) and time.monotonic() < until:
                    time.sleep(.02)
                if group_members(child.pid):
                    try:
                        os.killpg(child.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                until = time.monotonic() + 3
                while group_members(child.pid) and time.monotonic() < until:
                    time.sleep(.02)
            live = group_members(child.pid)
            if live:
                failure = RuntimeError('Live owned processes remain after cleanup: ' + str(live))
            try:
                code = child.wait(timeout=3)
            except subprocess.TimeoutExpired:
                failure = RuntimeError('Owned leader did not terminate; retain PID/group diagnostics')
        try:
            inventory(work, maximum_bytes)
        except (Exception, KeyboardInterrupt) as error:
            if failure is None:
                failure = error
        record = dict(status='completed-process-only' if failure is None and code == 0 else 'failed-process-only', command=command, exit_code=code, owned_live_after_cleanup=live, leader_pid_reserved_until_cleanup=True, seconds=time.monotonic() - start, error=None if failure is None else repr(failure))
        write_json(work / 'process.json', record)
    if failure is not None:
        raise failure
    if code == -signal.SIGILL:
        raise Unavailable('Pinned executable cannot run on this CPU; retain diagnostics and skip dependent analysis')
    if code != 0:
        raise RuntimeError('Original process failed with exit code ' + str(code))
    return record


def limited(arguments):
    return ['/usr/bin/prlimit', '--as=1073741824', '--cpu=15', '--fsize=1048576', '--nofile=128', '--core=0', '--', *arguments]


def expected_files(pin):
    return {name: dict(bytes=value['bytes'], sha256=value['sha256'], mode='0555' if name == pin['program'] else '0444') for name, value in pin['files'].items()}


def verify_files(home, pin, marker_expected):
    expected = set(pin['files']) | ({MARKER} if marker_expected else set())
    actual = set()
    expected_dirs = set()
    for name in expected:
        parent = PurePosixPath(name).parent
        while parent.as_posix() != '.':
            expected_dirs.add(parent.as_posix())
            parent = parent.parent
    for directory, dirs, files in os.walk(home, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            info = path.lstat()
            relative = path.relative_to(home).as_posix()
            if stat.S_ISDIR(info.st_mode):
                if relative not in expected_dirs or stat.S_IMODE(info.st_mode) != 0o700:
                    raise ValueError('Unexpected or changed private runtime directory: ' + relative)
            elif stat.S_ISREG(info.st_mode) and info.st_nlink == 1:
                actual.add(relative)
            else:
                raise ValueError('Linked or nonregular private runtime member')
    if actual != expected:
        raise ValueError('Complete private runtime inventory differs; preserve this home')
    for name, wanted in expected_files(pin).items():
        check_file(home / name, wanted)
        if stat.S_IMODE((home / name).stat().st_mode) != int(wanted['mode'], 8):
            raise ValueError('Private runtime file mode changed: ' + name)
    if marker_expected and stat.S_IMODE((home / MARKER).stat().st_mode) != 0o444:
        raise ValueError('Private runtime marker mode changed')
    return static_elf((home / pin['program']).read_bytes())


def verify_completion(report, home, profile):
    report = private(report, True)
    completed = read_json(report / 'COMPLETED.json')
    if completed.get('status') != 'completed-runtime-setup-only' or completed.get('home') != str(home) or completed.get('profile') != profile or completed.get('scientific_analysis_admitted') is not False or completed.get('runtime_marker_sha256') != sha(home / MARKER):
        raise ValueError('Runtime setup completion binding differs')
    inventory(report)
    files = completed.get('files')
    if not isinstance(files, dict) or not {'installation.json', 'WARNINGS.md', 'health/process.json', 'version/process.json'} <= set(files):
        raise ValueError('Incomplete setup report file census')
    actual = {path.relative_to(report).as_posix() for path in report.rglob('*') if path.is_file()}
    if actual != set(files) | {'COMPLETED.json'}:
        raise ValueError('Complete setup report inventory differs')
    for name, expected in files.items():
        member_name(name)
        check_file(report / name, expected)
    setup = read_json(report / 'installation.json')
    if setup.get('exit_code') != 0 or setup.get('scientific_analysis_admitted') is not False or setup.get('runtime_marker_sha256') != completed['runtime_marker_sha256']:
        raise ValueError('Runtime setup has no completed successful report')
    return completed


def rounded(actual, text):
    if not math.isfinite(actual) or not re.fullmatch(r'[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?', text):
        raise ValueError('Invalid complete original likelihood value')
    quantum = float(decimal.Decimal(1).scaleb(decimal.Decimal(text).as_tuple().exponent))
    if abs(actual - float(text)) > .5 * quantum + 2e-12:
        raise ValueError('Original fixed-tree likelihood differs from independent enumeration')


def smoke(home, pin, lock, report):
    version = report / 'version'
    version.mkdir(mode=0o700)
    owned_run(limited([str(home / pin['program']), '--version']), version)
    if (version / 'process.stdout').read_text(encoding='utf-8').splitlines()[0] != pin['version_first_line']:
        raise ValueError('Original executable version banner differs')
    work = report / 'health'
    work.mkdir(mode=0o700)
    for name in ['alignment.fasta', 'fixed.nwk']:
        raw = (home / 'share/health' / name).read_bytes()
        with (work / name).open('xb') as target:
            target.write(raw)
    arguments = [str(home / pin['program']), '-s', str(work / 'alignment.fasta'), '-st', 'DNA', '-m', 'JC', '-te', str(work / 'fixed.nwk'), '-blfix', '-wsl', '-seed', '1729', '-nt', '1', '-pre', str(work / 'result')]
    process = owned_run(limited(arguments), work)
    check_file(work / 'result.treefile', lock['health']['treefile'])
    values = (work / 'result.sitelh').read_text(encoding='utf-8').split()
    if values[:3] != ['1', '12', 'Site_Lh'] or len(values) != 15:
        raise ValueError('Complete original site-likelihood output differs')
    for value, printed in zip(lock['health']['site_log_likelihoods'], values[3:]):
        rounded(value, printed)
    text = (work / 'result.iqtree').read_text(encoding='utf-8')
    matches = re.findall(r'^Log-likelihood of the tree: ([^ ]+)', text, re.M)
    if len(matches) != 1 or 'Number of free parameters (#branches + #model parameters): 0' not in text or 'Model of substitution: JC' not in text:
        raise ValueError('Original fixed-model/tree report contract differs')
    rounded(lock['health']['total_log_likelihood'], matches[0])
    for name in ['alignment.fasta', 'fixed.nwk']:
        check_file(work / name, lock['bundled']['health/' + name])
    files = {p.relative_to(work).as_posix(): fingerprint(p) for p in work.rglob('*') if p.is_file()}
    return dict(status=lock['health']['status'], sequences=4, sites=12, total_log_likelihood_printed=matches[0], independent_total_log_likelihood=lock['health']['total_log_likelihood'], all_site_likelihoods_checked=12, files=files, owned_live_after_cleanup=process['owned_live_after_cleanup'])


def provision(args, report, record):
    home = private(args.home)
    cache = private(args.cache)
    if any(a == b or a.is_relative_to(b) or b.is_relative_to(a) for a, b in [(home, cache), (home, report), (cache, report)]):
        raise ValueError('Home, cache and report must be separate nonoverlapping trees')
    record.update(home=str(home), cache=str(cache), profile=args.profile)
    if not home.exists() and not args.allow_install:
        raise Unavailable('Explicit --allow-install is required before new runtime installation')
    current_host = host()
    lock = load_kit()
    if args.profile not in lock['profiles']:
        raise Unavailable('Requested exact edition/profile is unavailable; no other edition is substituted')
    pin = lock['profiles'][args.profile]
    installer_sha = sha(__file__)
    record.update(version=pin['version'], host=current_host, lock_sha256=LOCK_SHA256, installer_sha256=installer_sha, notice_scope=lock['license']['scope'], scope=lock['scope'])
    reused = home.exists()
    if reused:
        if not home.is_dir() or not (home / MARKER).is_file():
            raise ValueError('Existing unmanaged/provisional home preserved; choose a fresh private home')
        marker = read_json(home / MARKER)
        if marker.get('profile') != args.profile or marker.get('home') != str(home) or marker.get('lock_sha256') != LOCK_SHA256 or marker.get('installer_sha256') != installer_sha or marker.get('host') != current_host or marker.get('files') != expected_files(pin) or marker.get('scientific_analysis_admitted') is not False:
            raise ValueError('Existing home admission differs; preserve it and choose a fresh private home')
        verify_files(home, pin, True)
        verify_completion(marker['setup_report_directory'], home, args.profile)
    else:
        archives = [private(path, True) for path in args.archive_dir]
        if not cache.exists():
            cache.mkdir(mode=0o700)
        if not cache.is_dir():
            raise ValueError('Archive cache must be a private directory')
        lockfile = cache / '.iqtree-install.lock'
        try:
            with lockfile.open('xb') as output:
                output.write((str(os.getpid()) + '\n').encode())
                lock_identity = os.fstat(output.fileno())
        except FileExistsError as error:
            raise Unavailable('Existing cache lock preserved; inspect its owner/attempt and use a fresh cache if needed') from error
        try:
            archive = obtain(pin['archive'], cache, archives, args.allow_download)
            record['archive'] = dict(path=str(archive), **fingerprint(archive))
            # Every input and host prerequisite is checked before creating a home.
            load_kit()
            home.mkdir(mode=0o700)
            extract(archive, pin, home)
            for name, expected in pin['files'].items():
                if 'bundled' not in expected:
                    continue
                source = HERE / 'iqtree-runtime' / expected['bundled']
                check_file(source, expected)
                target = home / member_name(name)
                target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                with source.open('rb') as stream, target.open('xb') as output:
                    while block := stream.read(1048576):
                        output.write(block)
                target.chmod(0o444)
                check_file(target, expected)
            verify_files(home, pin, False)
        finally:
            if lockfile.exists() and not lockfile.is_symlink():
                now = lockfile.stat()
                if (now.st_dev, now.st_ino) == (lock_identity.st_dev, lock_identity.st_ino):
                    lockfile.unlink()
    record['stage'] = 'verify-original-runtime'
    health = smoke(home, pin, lock, report)
    elf = verify_files(home, pin, reused)
    if host() != current_host or sha(__file__) != installer_sha:
        raise ValueError('Operating-system prerequisites or installer source changed during setup')
    load_kit()
    if not reused:
        marker = dict(schema_version=1, tool='IQ-TREE', status='installed-smoke-tested-runtime-only', profile=args.profile, version=pin['version'], home=str(home), setup_report_directory=str(report), lock_sha256=LOCK_SHA256, installer_sha256=installer_sha, host=current_host, files=expected_files(pin), elf=elf, health=health, scientific_analysis_admitted=False)
        write_json(home / MARKER, marker)
        (home / MARKER).chmod(0o444)
        verify_files(home, pin, True)
    record.update(status='verified-reused-smoke-tested-runtime-only' if reused else 'installed-smoke-tested-runtime-only', runtime_reused=reused, runtime_marker_sha256=sha(home / MARKER), files=expected_files(pin), elf=elf, health=health)


def emit(record):
    raw = (json.dumps(record, ensure_ascii=False) + '\n').encode('utf-8')
    if hasattr(sys.stdout, 'buffer'):
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
    else:
        sys.stdout.write(raw.decode('utf-8'))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', required=True, help='Exact linux-x86_64-iqtree-VERSION profile; unavailable editions are skipped')
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True, help='Fresh report directory whose parent already exists')
    parser.add_argument('--archive-dir', type=Path, action='append', default=[])
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args(argv)
    record = dict(recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), download_authorized=args.allow_download, installation_authorized=args.allow_install, scientific_analysis_admitted=False, required_action=ACTION)
    report = None
    try:
        report = private(args.report)
        if report.exists():
            raise ValueError('Existing report preserved; select a new report directory')
        # Refuse overlap before creating even a diagnostic directory.
        for path in [private(args.home), private(args.cache)]:
            if path == report or path.is_relative_to(report) or report.is_relative_to(path):
                raise ValueError('Report must be outside the home and cache trees')
        report.mkdir(mode=0o700)
        record['report'] = str(report)
        provision(args, report, record)
        code = 0
    except (Unavailable, urllib.error.URLError, TimeoutError) as error:
        record.update(status='SKIP3-runtime-unavailable', error=str(error))
        code = 3
    except PermissionError as error:
        code = 2 if record.get('stage') == 'verify-original-runtime' else 3
        record.update(status='FAIL2-retained-no-analysis' if code == 2 else 'SKIP3-runtime-unavailable', error=str(error))
    except (Exception, KeyboardInterrupt) as error:
        record.update(status='FAIL2-retained-no-analysis', error=repr(error))
        code = 2
    record['exit_code'] = code
    try:
        if report is None or record.get('report') != str(report):
            raise ValueError('A fresh writable report directory was not created')
        write_json(report / 'installation.json', record)
        with (report / 'WARNINGS.md').open('x', encoding='utf-8', newline='\n') as output:
            output.write('# IQ-TREE ' + ('SETUP ONLY — ANALYSIS NOT ADMITTED' if code == 0 else 'SKIP3 — ANALYSIS MUST BE SKIPPED' if code == 3 else 'FAIL2 — NO RESULT ADMITTED') + '\n\n')
            output.write(record.get('error', 'The exact runtime passed one complete fixed-JC69 synthetic smoke test. A typed public analysis adapter remains required.') + '\n\n' + ACTION + '\n')
        if code == 0:
            home = Path(record['home'])
            files = {path.relative_to(report).as_posix(): fingerprint(path) for path in report.rglob('*') if path.is_file()}
            write_json(report / 'COMPLETED.json', dict(status='completed-runtime-setup-only', home=str(home), profile=args.profile, runtime_marker_sha256=record['runtime_marker_sha256'], files=files, scientific_analysis_admitted=False))
            verify_completion(report, home, args.profile)
    except (Exception, KeyboardInterrupt) as error:
        record.update(status='FAIL2-report-write-failed', exit_code=2, report_error=repr(error))
        code = 2
        # Preserve the earlier provisional files, but leave an explicit failure
        # record when possible. An extra failure file also invalidates any
        # completion manifest whose post-write verification failed.
        if report is not None and record.get('report') == str(report):
            try:
                write_json(report / 'FAILURE.json', record)
            except (Exception, KeyboardInterrupt):
                pass  # The UTF-8 console/exit status still report the failure.
    emit(record)
    print(('WARNING: SETUP ONLY; NO USER ANALYSIS ADMITTED. ' if code == 0 else 'WARNING: SKIP3; SKIP DEPENDENT ANALYSIS. ' if code == 3 else 'ERROR: FAIL2; NO ANALYSIS RESULT ADMITTED. ') + record.get('error', record.get('report_error', '')) + '\nRequired action: ' + ACTION, file=sys.stderr)
    return code


def interrupted(signum, _frame):
    raise KeyboardInterrupt('Setup interrupted by signal ' + str(signum))


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
