#!/usr/bin/env python3
"""Install/admit a pinned private Eagle runtime using real synthetic phasing.

No archive installer or shell is executed. Downloads and installation require
separate flags; analysis never installs. Reports and failed homes are retained.
"""
import argparse
import ctypes
import ctypes.util
import datetime as dt
import gzip
import hashlib
import io
import json
import os
import platform
import re
import shutil
import signal
import struct
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

HERE = Path(__file__).resolve().parent
LOCK_RAW = (HERE / 'eagle-runtime-lock.json').read_bytes()
LOCK = json.loads(LOCK_RAW)
LOCK_SHA = hashlib.sha256(LOCK_RAW).hexdigest()
MARKER = '.omicsmatic-eagle.json'
HEALTH_STATUS = 'passed-original-cohort-reference-keep-impute-health'
RECOVERY = (
    'Required action: use Linux x86_64 Python 3.11+ for setup and Linux Java for analysis; '
    'on Windows use WSL. Where installation is permitted, run python3 tools/literature/install_eagle.py '
    '--profile linux-x86_64-eagle-2.4.1 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE '
    '--report NEW_REPORT --allow-install. Add --allow-download only where downloads are permitted, '
    'or supply repeated --archive-dir OFFLINE_ARCHIVE_DIRECTORY arguments. Existing /usr/bin/ldd, '
    '/usr/bin/prlimit, /usr/bin/setsid and libzstd are required; this helper does not install system '
    'packages or change OS settings. If dependencies, installation or execution are forbidden or '
    'unavailable, SKIP the dependent analysis (exit 3). Invalid input, integrity, execution and '
    'capacity failures are FAIL (exit 2). Preserve corrupt caches, partial downloads, failed homes '
    'and diagnostics; correct the named cause and choose new paths. Never substitute Eagle 2.4.1 '
    'for a requested 2.3/2.3.5 edition. Runtime setup alone admits no analysis result; use eagle-tools with a typed recipe and complete output audit. '
    'Cell Ranger remains excluded.'
)


class Unavailable(Exception):
    """A missing/forbidden dependency requires an explicit analysis skip."""


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write(path, value):
    data = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    with Path(path).open('xb') as stream:
        stream.write(data)


def private(path):
    path = Path(os.path.abspath(path))
    if any(part.is_symlink() for part in [path, *path.parents]):
        raise ValueError('Linked path or ancestor is unsupported: ' + str(path))
    return path


def host():
    if sys.version_info < (3, 11):
        raise Unavailable('Setup requires Python 3.11 or later')
    if platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('Original Eagle runtime requires Linux x86_64; on Windows use Linux Python/Java inside WSL')
    return dict(os='Linux', architecture='x86_64', kernel=platform.release())


def environment(home, work):
    return dict(PATH='/usr/bin:/bin', HOME=str(work), TMPDIR=str(work), LANG='C', LC_ALL='C',
                OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1',
                LD_LIBRARY_PATH=str(home / 'lib'))


def check_file(path, expected):
    path = private(path)
    if not path.is_file() or path.stat().st_size != expected['bytes'] or sha(path) != expected['sha256']:
        raise ValueError('Missing or changed pinned member: ' + str(path))


def inventory(root, maximum, file_limit):
    total = count = 0
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            try:
                if path.is_symlink() or not (path.is_file() or path.is_dir()):
                    raise ValueError('Linked or nonregular owned process output: ' + str(path))
                count += 1
                if path.is_file():
                    total += path.stat().st_size
            except FileNotFoundError:
                continue  # Owned processes can rename normal temporary files.
            if count > file_limit or total > maximum:
                raise ValueError('Owned process file-count/workspace byte bound exceeded')
    return total


def proc_identity(pid):
    try:
        text = Path('/proc', str(pid), 'stat').read_text(encoding='ascii')
        fields = text[text.rindex(')') + 2:].split()
        return dict(pid=pid, state=fields[0], group=int(fields[2]), session=int(fields[3]), start=int(fields[19]))
    except (FileNotFoundError, ProcessLookupError):
        return None


def group_members(group):
    members = {}
    for path in Path('/proc').iterdir():
        if path.name.isdecimal():
            value = proc_identity(int(path.name))
            if value and value['group'] == group and value['session'] == group:
                members[value['pid']] = value
    return members


def kill_owned(group, known):
    current = group_members(group)
    matching = any(pid in known and value['start'] == known[pid]['start'] for pid, value in current.items())
    if matching:
        try:
            os.killpg(group, signal.SIGKILL)
        except ProcessLookupError:
            pass
    return current


def run(command, work, label, env, limits, require_zero=True):
    """Hard per-process AS/CPU/file/nofile limits plus wall/workspace checks.

    No RLIMIT_NPROC: it would also count unrelated processes sharing the UID.
    Session ownership contains ordinary descendants; no arbitrary shell input.
    """
    invocation = ['/usr/bin/prlimit', '--as=' + str(limits['address_space_bytes']),
                  '--cpu=' + str(limits['cpu_seconds']), '--fsize=' + str(limits['file_bytes']),
                  '--nofile=' + str(limits['open_files']), '--', *map(str, command)]
    stdout, stderr = work / (label + '.stdout'), work / (label + '.stderr')
    record = dict(command=invocation, status='launching', limits=limits)
    child = None
    known = {}
    start = time.monotonic()
    try:
        with stdout.open('xb') as out, stderr.open('xb') as err:
            # Defer handled termination until the child and its identity are
            # assigned. Blocking the signals would also block them in the
            # child; a handler queue avoids that inherited-mask problem.
            pending = []
            previous = {}
            try:
                for number in (signal.SIGINT, signal.SIGTERM):
                    previous[number] = signal.getsignal(number)
                    signal.signal(number, lambda number, _frame: pending.append(number))
                child = subprocess.Popen(invocation, cwd=work, env=env, stdout=out, stderr=err, start_new_session=True)
                record.update(status='running', owned_pid=child.pid, owned_process_group=child.pid)
                # start_new_session completes before Popen returns.
                first = proc_identity(child.pid)
                if first:
                    if first['group'] != child.pid or first['session'] != child.pid:
                        raise ValueError('Owned process did not enter its private session')
                    known[child.pid] = first
                    record['owned_start_ticks'] = first['start']
            finally:
                for number, handler in previous.items():
                    signal.signal(number, handler)
            if pending:
                raise KeyboardInterrupt('Setup interrupted during owned launch by signal ' + str(pending[0]))
            while True:
                # Capture descendants before reaping an exited leader: the
                # unreaped PID still anchors this session's identity.
                known.update(group_members(child.pid))
                if child.poll() is not None:
                    break
                if time.monotonic() - start > limits['wall_seconds']:
                    raise ValueError('Original process exceeded wall-time bound: ' + label)
                inventory(work, limits['workspace_bytes'], limits['file_count'])
                time.sleep(.025)
            inventory(work, limits['workspace_bytes'], limits['file_count'])
            record.update(status='completed', exit_code=child.returncode,
                          stdout_sha256=sha(stdout), stderr_sha256=sha(stderr))
            if child.returncode and require_zero:
                raise ValueError('Original process failed, exit ' + str(child.returncode) + ': ' + label)
    except BaseException as failure:
        record.update(status='failed-retained', error=repr(failure))
        raise
    finally:
        try:
            if child is not None:
                live = kill_owned(child.pid, known)
                record['cleanup_observed_pids'] = sorted(live)
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    record['cleanup_failed'] = True
                    raise RuntimeError('Owned process remains alive: ' + str(child.pid))
                # SIGKILL delivery can race the final /proc snapshot.
                deadline = time.monotonic() + 2
                while True:
                    remaining = group_members(child.pid)
                    record['owned_live_after_cleanup'] = sorted(pid for pid, value in remaining.items() if value['state'] != 'Z')
                    if not record['owned_live_after_cleanup'] or time.monotonic() >= deadline:
                        break
                    time.sleep(.01)
                if record['owned_live_after_cleanup']:
                    record['cleanup_failed'] = True
                    raise RuntimeError('Owned descendants remain alive: ' + str(record['owned_live_after_cleanup']))
        finally:
            record['seconds'] = time.monotonic() - start
            write(work / (label + '.process.json'), record)
    return record


def obtain(name, cache, references, allow_download):
    pin = LOCK['archives'][name]
    target = cache / name
    if target.exists() or target.is_symlink():
        check_file(target, pin)
        return target
    for directory in references:
        source = directory / name
        if source.exists() or source.is_symlink():
            check_file(source, pin)
            with source.open('rb') as stream, target.open('xb') as output:
                shutil.copyfileobj(stream, output)
            check_file(target, pin)
            return target
    if not allow_download:
        raise Unavailable('Pinned archive absent and --allow-download not supplied: ' + name)
    part = cache / (name + '.part')
    if part.exists() or part.is_symlink():
        raise ValueError('Retained partial download exists; choose a new cache: ' + str(part))
    request = urllib.request.Request(pin['url'], headers={'User-Agent': 'OmicsMatic-private-Eagle-runtime/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response, part.open('xb') as output:
        count = 0
        while chunk := response.read(1048576):
            count += len(chunk)
            if count > pin['bytes']:
                raise ValueError('Pinned download byte bound exceeded: ' + name)
            output.write(chunk)
    check_file(part, pin)
    part.rename(target)
    return target


def tar_path(name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name or not path.parts:
        raise ValueError('Unsafe archive path: ' + name)
    return str(path)


def zstd_tar(data, maximum):
    library = ctypes.util.find_library('zstd')
    if not library:
        raise Unavailable('Existing libzstd is needed to extract the pinned private OpenMP dependency')
    zstd = ctypes.CDLL(library)
    zstd.ZSTD_decompress.argtypes = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_size_t]
    zstd.ZSTD_decompress.restype = ctypes.c_size_t
    zstd.ZSTD_isError.argtypes = [ctypes.c_size_t]
    zstd.ZSTD_isError.restype = ctypes.c_uint
    output = ctypes.create_string_buffer(maximum)
    size = zstd.ZSTD_decompress(output, len(output), data, len(data))
    if zstd.ZSTD_isError(size):
        raise ValueError('Pinned conda payload is invalid or exceeds its expanded byte bound')
    return io.BytesIO(output.raw[:size])


def extract_selected(archive, entries, home):
    pin = LOCK['archives'][archive.name]
    check_file(archive, pin)
    selected = {}
    for destination, value in entries.items():
        tar_path(destination)
        selected.setdefault(value['member'], []).append((destination, value))
    payload = None
    if pin['kind'] == 'conda-zstd':
        with zipfile.ZipFile(archive) as zipped:
            wanted = 'pkg-' + archive.stem + '.tar.zst'
            names = zipped.namelist()
            if len(names) != 3 or len(set(names)) != 3 or wanted not in names:
                raise ValueError('Unexpected pinned conda zip inventory')
            info = zipped.getinfo(wanted)
            if info.file_size > pin['bytes'] or info.compress_size > pin['bytes']:
                raise ValueError('Pinned conda compressed payload exceeds bound')
            payload = zstd_tar(zipped.read(wanted), pin['expanded_limit'])
        stream = tarfile.open(fileobj=payload, mode='r:')
    elif pin['kind'] == 'tar-gzip':
        stream = tarfile.open(archive, mode='r|gz')
    else:
        raise ValueError('Unsupported locked archive kind')
    seen = set()
    expanded = count = 0
    found = set()
    with stream:
        for member in stream:
            name = tar_path(member.name)
            if name in seen:
                raise ValueError('Duplicate pinned archive member: ' + name)
            seen.add(name)
            count += 1
            expanded += member.size
            if count > pin.get('member_count', 512) or expanded > pin.get('expanded_bytes', pin.get('expanded_limit', 0)):
                raise ValueError('Pinned archive count/expanded byte bound exceeded')
            if name not in selected:
                continue  # Never materialize or execute other members or links.
            if not member.isfile():
                raise ValueError('Selected archive member must be a regular file: ' + name)
            destinations = selected[name]
            expected = destinations[0][1]
            if member.size != expected['bytes'] or any(value['sha256'] != expected['sha256'] or value['bytes'] != member.size for _, value in destinations):
                raise ValueError('Pinned archive member size/duplicate destination differs')
            source = stream.extractfile(member)
            data = source.read(member.size + 1)
            if len(data) != member.size or hashlib.sha256(data).hexdigest() != expected['sha256']:
                raise ValueError('Pinned archive member bytes differ: ' + name)
            for destination, value in destinations:
                target = home / destination
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as output:
                    output.write(data)
                target.chmod(0o755 if value.get('executable') else 0o644)
                found.add(destination)
    if found != set(entries):
        raise ValueError('Pinned archive is missing selected members')
    if pin['kind'] == 'tar-gzip' and (count != pin['member_count'] or expanded != pin['expanded_bytes']):
        raise ValueError('Pinned original archive inventory differs')


def external_file(path):
    original = Path(path)
    real = original.resolve(strict=True)
    if not real.is_file() or real.stat().st_size > 100000000:
        raise Unavailable('Missing or oversized runtime dependency: ' + str(path))
    return dict(real_path=str(real), bytes=real.stat().st_size, sha256=sha(real))


def closure(home, report):
    external = {}
    binaries = {}
    for helper in LOCK['closure']['helpers']:
        if not Path(helper).is_file() or not os.access(helper, os.X_OK):
            raise Unavailable('Required existing executable is unavailable or forbidden: ' + helper)
        external[helper] = external_file(helper)
    for i, (name, required) in enumerate(LOCK['closure']['binaries'].items()):
        binary = home / name if not name.startswith('/') else Path(name)
        work = report / ('closure-' + str(i))
        work.mkdir()
        process = run(['/usr/bin/ldd', str(binary)], work, 'ldd', environment(home, work), LOCK['health_limits'], require_zero=False)
        text = (work / 'ldd.stdout').read_text(encoding='utf-8') + (work / 'ldd.stderr').read_text(encoding='utf-8')
        if 'not found' in text:
            raise Unavailable('Original runtime shared library unavailable: ' + name)
        if process['exit_code']:
            raise Unavailable('Cannot establish runtime shared-library closure: ' + name)
        found = {}
        for line in text.splitlines():
            match = re.search(r'(?:=>\s*)?(/[^\s]+)\s+\(0x[0-9a-fA-F]+\)', line)
            if not match:
                continue
            original = Path(match.group(1))
            base = original.name
            if base in found:
                raise ValueError('Duplicate runtime library name')
            found[base] = str(original)
            real = original.resolve(strict=True)
            if home in real.parents:
                if original != home / 'lib/libgomp.so.1' or real != original:
                    raise ValueError('Unmanaged private runtime library binding')
            else:
                external[str(original)] = external_file(original)
        if set(found) != set(required):
            raise Unavailable('Runtime dependency bindings differ from this explicit profile: ' + name)
        binaries[name] = found
    result = dict(binaries=binaries, external_files=external)
    verify_closure(home, result)
    return result


def verify_closure(home, value):
    binaries, files = value.get('binaries', {}), value.get('external_files', {})
    if set(binaries) != set(LOCK['closure']['binaries']) or not 3 <= len(files) <= 32:
        raise ValueError('Missing or oversized external runtime closure')
    expected_files = set(LOCK['closure']['helpers'])
    for name, required in LOCK['closure']['binaries'].items():
        bindings = binaries[name]
        if set(bindings) != set(required):
            raise ValueError('Runtime library binding inventory differs: ' + name)
        for base, text in bindings.items():
            path = Path(text)
            if not path.is_absolute() or path.name != base:
                raise ValueError('Invalid runtime library binding path')
            if base == 'libgomp.so.1':
                if path != home / 'lib/libgomp.so.1':
                    raise ValueError('Private OpenMP binding differs')
            else:
                expected_files.add(str(path))
    if set(files) != expected_files:
        raise ValueError('External runtime file inventory differs')
    for name, expected in files.items():
        if external_file(name) != expected:
            raise ValueError('Changed host runtime library/tool; admit a new private home: ' + name)


def read_vcf(path):
    opener = gzip.open if path.name.endswith('.gz') else open
    samples = None
    rows = []
    with opener(path, 'rt', encoding='utf-8', newline='') as stream:
        count = 0
        while line := stream.readline(16385):
            count += 1
            if count > 512 or len(line) > 16384:
                raise ValueError('Scientific health VCF text bound exceeded')
            text = line.rstrip('\r\n')
            if text.startswith('##') and samples is None:
                continue
            fields = text.split('\t')
            if text.startswith('#CHROM\t') and samples is None and len(fields) >= 10:
                samples = fields[9:]
                if len(set(samples)) != len(samples) or any(not x for x in samples):
                    raise ValueError('Missing or duplicate health VCF samples')
            elif samples is None or len(fields) != len(samples) + 9 or not fields[1].isdecimal() or int(fields[1]) < 1:
                raise ValueError('Malformed scientific health VCF header/row width/position')
            else:
                formats = fields[8].split(':')
                if formats.count('GT') != 1 or len(set(formats)) != len(formats):
                    raise ValueError('Scientific health VCF requires one unique GT format')
                calls = []
                for cell in fields[9:]:
                    values = cell.split(':')
                    if len(values) != len(formats):
                        raise ValueError('Scientific health FORMAT cell width differs')
                    calls.append(values[formats.index('GT')])
                identity = [fields[0], str(int(fields[1])), fields[2], fields[3], fields[4]]
                rows.append((identity, calls))
    if not samples or not rows:
        raise ValueError('Scientific health VCF is empty or incomplete')
    return samples, rows


def audit_health(source, output, contract):
    samples, rows = read_vcf(source)
    actual_samples, actual = read_vcf(output)
    if samples != actual_samples or len(rows) != len(actual) or len(rows) != contract['variants'] or len(samples) != contract['samples']:
        raise ValueError('Original scientific health output population differs')
    digest = hashlib.sha256()
    def frame(text):
        raw = text.encode('utf-8')
        digest.update(struct.pack('>I', len(raw)))
        digest.update(raw)
    frame('Eagle-health-contract-v1')
    for name in samples:
        frame(name)
    missing_count = cells = 0
    for (identity, calls), (identity_after, after) in zip(rows, actual):
        if identity != identity_after:
            raise ValueError('Original scientific health output variant identity/order differs')
        for text in identity:
            frame(text)
        for before, observed in zip(calls, after):
            if not re.fullmatch(r'[01.](?:[/|][01.])?', before):
                raise ValueError('Unexpected source health genotype')
            missing = '.' in before
            if missing:
                missing_count += 1
            if missing and contract['missing'] == 'keep':
                expected = before if contract['mode'] == 'cohort' else './.'
                if observed != expected:
                    raise ValueError('Original health keep-missing contract failed')
                token = 'M:' + observed
            else:
                if not re.fullmatch(r'[01]\|[01]', observed):
                    raise ValueError('Original health output lacks a complete phased diploid call')
                dosage = int(observed[0]) + int(observed[2])
                if not missing and dosage != sum(map(int, before.replace('|', '/').split('/'))):
                    raise ValueError('Original health changed an observed source dosage')
                token = 'IMPUTED:2' if missing else '2:' + str(dosage)
            frame(token)
            cells += 1
    value = dict(mode=contract['mode'], missing=contract['missing'], variants=len(rows), samples=len(samples),
                 genotype_cells=cells, source_missing_cells=missing_count, normalized_sha256=digest.hexdigest())
    if value != contract:
        raise ValueError('Original health complete contract/digest differs')
    return value


def health(home, report):
    outputs = {}
    (home / 'health/output').mkdir()
    for label, contract in LOCK['health_contracts'].items():
        work = report / label
        work.mkdir()
        prefix = work / 'phased'
        args = [str(home / 'bin/eagle'), '--geneticMapFile=' + str(home / 'health/input/genetic-map.txt'),
                '--chrom=22', '--chromX=23', '--numThreads=1', '--Kpbwt=10000', '--pbwtIters=1',
                '--seed=0', '--vcfOutFormat=v', '--outPrefix=' + str(prefix)]
        if contract['mode'] == 'cohort':
            source = home / 'health/input/cohort-target.vcf'
            args += ['--vcf=' + str(source), '--pbwtOnly']
        else:
            source = home / 'health/input/target.vcf.gz'
            args += ['--vcfTarget=' + str(source), '--vcfRef=' + str(home / 'health/input/reference.vcf.gz')]
        if contract['missing'] == 'keep':
            args += ['--noImpMissing']
        process = run(args, work, 'eagle', environment(home, work), LOCK['health_limits'])
        output = prefix.with_suffix('.vcf')
        if not output.is_file() or output.is_symlink() or output.stat().st_size > LOCK['health_limits']['file_bytes']:
            raise ValueError('Actual original phasing did not produce a bounded regular VCF')
        audit = audit_health(source, output, contract)
        target = home / 'health/output' / (label + '.vcf')
        with output.open('rb') as stream, target.open('xb') as retained:
            shutil.copyfileobj(stream, retained)
        outputs[label] = dict(audit=audit, output=dict(bytes=target.stat().st_size, sha256=sha(target)), process=process)
    return dict(status=HEALTH_STATUS, contracts=outputs, scope=LOCK['scope'])


def fixed_members(profile):
    result = {name: dict(bytes=value['bytes'], sha256=value['sha256']) for name, value in LOCK['profiles'][profile]['members'].items()}
    result.update({'health/input/' + name: value for name, value in LOCK['health_fixtures'].items()})
    result.update({'share/' + name: value for name, value in LOCK['bundled_notices'].items()})
    return result


def verify_home(home, profile, expected_host):
    home = private(home)
    if not home.is_dir() or not (home / MARKER).is_file():
        raise Unavailable('Missing or unmanaged private runtime home; preserve it and choose a new home')
    marker = private(home / MARKER)
    if marker.stat().st_size > 1048576:
        raise ValueError('Oversized original runtime admission marker')
    value = json.loads(marker.read_text(encoding='utf-8'))
    if value.get('schema_version') != 1 or value.get('tool') != 'Eagle' or value.get('profile') != profile or value.get('version') != LOCK['profiles'][profile]['version'] or value.get('lock_sha256') != LOCK_SHA or value.get('installer_sha256') != sha(Path(__file__)):
        raise ValueError('Original runtime profile/version/lock/installer admission differs')
    if value.get('host') != expected_host:
        raise Unavailable('Admitted runtime host/kernel differs; admit a new home on this host')
    expected = fixed_members(profile)
    passed = value.get('health', {})
    contracts = passed.get('contracts', {})
    if passed.get('status') != HEALTH_STATUS or passed.get('scope') != LOCK['scope'] or set(contracts) != set(LOCK['health_contracts']):
        raise ValueError('Actual original four-run scientific health admission is missing')
    for label, contract in LOCK['health_contracts'].items():
        record = contracts[label]
        output = record.get('output', {})
        if not isinstance(output.get('bytes'), int) or not 1 <= output['bytes'] <= LOCK['health_limits']['file_bytes'] or not re.fullmatch('[0-9a-f]{64}', str(output.get('sha256'))):
            raise ValueError('Malformed original health output fingerprint')
        expected['health/output/' + label + '.vcf'] = output
        check_file(home / ('health/output/' + label + '.vcf'), output)
        process = record.get('process', {})
        if process.get('status') != 'completed' or process.get('exit_code') != 0 or process.get('owned_live_after_cleanup') != [] or process.get('limits') != LOCK['health_limits']:
            raise ValueError('Actual original scientific health process did not complete cleanly')
        source = home / ('health/input/cohort-target.vcf' if contract['mode'] == 'cohort' else 'health/input/target.vcf.gz')
        check_file(source, expected[source.relative_to(home).as_posix()])
        if record.get('audit') != audit_health(source, home / ('health/output/' + label + '.vcf'), contract):
            raise ValueError('Original scientific health audit changed')
    if value.get('files') != expected:
        raise ValueError('Managed runtime file fingerprint inventory differs')
    actual = set()
    allowed_dirs = set()
    for name, entry in expected.items():
        check_file(home / name, entry)
        for parent in PurePosixPath(name).parents:
            if str(parent) != '.':
                allowed_dirs.add(str(parent))
    for directory, dirs, files in os.walk(home, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            relative = path.relative_to(home).as_posix()
            private(path)
            if path.is_dir():
                if relative not in allowed_dirs:
                    raise ValueError('Unmanaged runtime directory: ' + relative)
            elif not path.is_file():
                raise ValueError('Nonregular runtime member: ' + relative)
            else:
                actual.add(relative)
    if actual != set(expected) | {MARKER} or not os.access(home / 'bin/eagle', os.X_OK):
        raise ValueError('Managed runtime inventory/executable permission differs')
    verify_closure(home, value.get('closure', {}))
    return value


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv=None):
    parser = Parser(description=__doc__)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--home', required=True)
    parser.add_argument('--cache', required=True)
    parser.add_argument('--report', required=True)
    parser.add_argument('--archive-dir', action='append', default=[])
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    record = dict(tool='Eagle', recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                  lock_sha256=LOCK_SHA, installer_sha256=sha(Path(__file__)), scope=LOCK['scope'])
    owns_report = False
    report = None
    try:
        args = parser.parse_args(argv)
        record.update(profile=args.profile, install_allowed=args.allow_install, download_allowed=args.allow_download)
        home, cache, report = [private(value) for value in [args.home, args.cache, args.report]]
        references = [private(value) for value in args.archive_dir]
        for first, second in [(home, report), (home, cache), (report, cache)]:
            if first == second or first in second.parents or second in first.parents:
                raise ValueError('Home, cache and report must be separate nonoverlapping paths')
        report.mkdir(parents=True, mode=0o700)
        owns_report = True
        record.update(home=str(home), report=str(report), cache=str(cache))
        if args.profile not in LOCK['profiles']:
            raise Unavailable('Requested Eagle edition/profile is unsupported; no edition is substituted: ' + args.profile)
        actual_host = host()
        record['host'] = actual_host
        if home.exists():
            verify_home(home, args.profile, actual_host)
            record.update(status='reused-admitted-original-runtime', runtime_marker_sha256=sha(home / MARKER))
        else:
            if not args.allow_install:
                raise Unavailable('Private runtime absent and --allow-install not supplied')
            for helper in LOCK['closure']['helpers']:
                if not Path(helper).is_file() or not os.access(helper, os.X_OK):
                    raise Unavailable('Required existing executable unavailable or forbidden: ' + helper)
            cache.mkdir(parents=True, exist_ok=True, mode=0o700)
            pin = LOCK['profiles'][args.profile]
            archives = {name: obtain(name, cache, references, args.allow_download) for name in sorted({v['archive'] for v in pin['members'].values()})}
            for group, directory in [('health_fixtures', 'health'), ('bundled_notices', 'notices')]:
                for name, expected in LOCK[group].items():
                    check_file(HERE / 'eagle-runtime' / directory / name, expected)
            home.mkdir(parents=True, mode=0o700)
            for name, archive in archives.items():
                extract_selected(archive, {k: v for k, v in pin['members'].items() if v['archive'] == name}, home)
            for group, directory, destination in [('health_fixtures', 'health', 'health/input'), ('bundled_notices', 'notices', 'share')]:
                target = home / destination
                target.mkdir(parents=True, exist_ok=True)
                for name in LOCK[group]:
                    with (HERE / 'eagle-runtime' / directory / name).open('rb') as source, (target / name).open('xb') as output:
                        shutil.copyfileobj(source, output)
            base = closure(home, report)
            passed = health(home, report)
            verify_closure(home, base)
            files = fixed_members(args.profile)
            files.update({'health/output/' + name + '.vcf': result['output'] for name, result in passed['contracts'].items()})
            marker = dict(schema_version=1, tool='Eagle', profile=args.profile, version=pin['version'],
                          lock_sha256=LOCK_SHA, installer_sha256=record['installer_sha256'], host=actual_host,
                          files=files, closure=base, health=passed, notice_scope=LOCK['notice_scope'])
            write(home / MARKER, marker)
            verify_home(home, args.profile, actual_host)
            record.update(status='installed-and-admitted-original-runtime', runtime_marker_sha256=sha(home / MARKER), health=passed)
        record['notice_scope'] = LOCK['notice_scope']
        code = 0
    except (Unavailable, PermissionError, urllib.error.URLError, TimeoutError) as failure:
        record.update(status='SKIP3-unavailable-dependency', error=str(failure))
        code = 3
    except (Exception, KeyboardInterrupt) as failure:
        record.update(status='FAIL2-setup-retained', error=repr(failure))
        code = 2
    record['exit_code'] = code
    if owns_report:
        write(report / 'installation.json', record)
        heading = 'SETUP WARNINGS REQUIRE REVIEW' if code == 0 else 'SKIP3 SETUP SKIPPED' if code == 3 else 'FAIL2 SETUP FAILED'
        message = record.get('error', 'Pinned original runtime passed four scientific health checks; phasing requires eagle-tools, a typed recipe and a complete output audit.')
        with (report / 'WARNINGS.md').open('x', encoding='utf-8', newline='\n') as stream:
            stream.write('# Eagle ' + heading + '\n\n' + message + '\n\n' + LOCK['notice_scope'] + '\n\n' + LOCK['scope'] + '\n\n' + RECOVERY + '\n')
    # Structured console bytes must preserve paths even under a Windows code
    # page or an ASCII Linux locale. Test capture streams may have no buffer.
    console = json.dumps(record, ensure_ascii=False) + '\n'
    if hasattr(sys.stdout, 'buffer'):
        sys.stdout.buffer.write(console.encode('utf-8'))
        sys.stdout.buffer.flush()
    else:
        sys.stdout.write(console)
    if code:
        print(RECOVERY, file=sys.stderr)
    return code


def interrupted(signum, _frame):
    raise KeyboardInterrupt('Setup interrupted by signal ' + str(signum))


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, interrupted)
    sys.exit(main())
