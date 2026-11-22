#!/usr/bin/env python3
"""Admit pinned private original Minimac runtimes after real synthetic imputation.

Installation and downloads need separate explicit flags. Analysis never installs.
No shell installer, system package installation, kernel change or server upload
is performed. Existing homes, partial downloads and failed reports are retained.
"""
import argparse
import datetime as dt
import gzip
import hashlib
import io
import json
import math
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import time
import urllib.request
import urllib.error
from pathlib import Path, PurePosixPath

HERE = Path(__file__).resolve().parent
LOCK_RAW = (HERE / 'minimac-runtime-lock.json').read_bytes()
LOCK = json.loads(LOCK_RAW)
LOCK_SHA = hashlib.sha256(LOCK_RAW).hexdigest()
MARKER = '.jlinalg-minimac.json'
HEALTH_STATUS = 'passed-original-imputation-and-reference-health'
RECOVERY = (
    'Required action: use Linux x86_64 Python3.11 or later for setup and Linux Java for analysis; '
    'on Windows run them inside WSL. Where permitted, run python3 tools/literature/install_minimac.py '
    '--profile linux-x86_64-minimac4-4.1.6 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE '
    '--report NEW_REPORT --allow-install. Add --allow-download only where downloads are permitted, '
    'or provide repeated --archive-dir OFFLINE_ARCHIVE_DIRECTORY arguments. Select an explicit '
    'historical profile when required; versions are never silently substituted. Debian-package '
    'extraction needs the existing dpkg-deb utility; no system package is installed by this script. '
    'If installation or execution is forbidden, unavailable, unsupported or changed, SKIP the '
    'dependent analysis and preserve its named cause. Preserve existing homes, corrupt caches, '
    'partial downloads and failed reports; correct the cause and choose new paths. '
    'Runtime admission is separate from the still-unfinished project imputation adapter. '
    'Cell Ranger remains excluded.'
)


class Unavailable(Exception):
    """An unavailable dependency requires an explicit analysis skip."""


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


def private(path):
    path = Path(path).absolute()
    if any(part.is_symlink() for part in [path, *path.parents]):
        raise ValueError('Linked path or ancestor is unsupported: ' + str(path))
    return path.resolve()


def host():
    if platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('Original Minimac runtimes need Linux x86_64; on Windows use Linux Python/Java inside WSL')
    return dict(os='Linux', architecture='x86_64', kernel=platform.release())


def environment(home, pin, work):
    env = dict(PATH='/usr/bin:/bin', HOME=str(work), TMPDIR=str(work), LANG='C', LC_ALL='C',
               OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
    if pin['private_gomp']:
        env['LD_LIBRARY_PATH'] = str(home / 'lib')
    return env


def inventory_bound(root, maximum=268435456, file_limit=512):
    total = count = 0
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            try:
                if path.is_symlink():
                    raise ValueError('Linked process output is unsupported: ' + str(path))
                count += 1
                if path.is_file():
                    total += path.stat().st_size
            except FileNotFoundError:
                continue  # An owned process may rename its normal temporary file.
            if count > file_limit or total > maximum:
                raise ValueError('Owned process output file-count or byte bound exceeded')
    return total


def run(command, work, label, env, timeout=60, byte_bound=268435456):
    stdout = work / (label + '.stdout')
    stderr = work / (label + '.stderr')
    with stdout.open('xb') as out, stderr.open('xb') as err:
        child = subprocess.Popen(command, cwd=work, env=env, stdout=out, stderr=err, start_new_session=True)
        start = time.monotonic()
        record = dict(status='running', command=command, owned_pid=child.pid, owned_process_group=child.pid,
                      timeout_seconds=timeout, byte_bound=byte_bound)
        process_file = work / (label + '.process.json')
        write(process_file, record)
        try:
            while child.poll() is None:
                if time.monotonic() - start > timeout:
                    record['timed_out'] = True
                    raise Unavailable('Original process exceeded timeout: ' + label + '; owned PID/process group ' + str(child.pid))
                inventory_bound(work, byte_bound)
                time.sleep(.05)
            inventory_bound(work, byte_bound)
            record.update(status='completed', exit_code=child.returncode,
                          stdout_sha256=sha(stdout), stderr_sha256=sha(stderr))
            return record
        except BaseException as failure:
            record.update(status='failed-retained', error=repr(failure))
            raise
        finally:
            try:
                os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            try:
                child.wait(timeout=10)
                record['owned_parent_alive_after_cleanup'] = child.poll() is None
                if record['owned_parent_alive_after_cleanup']:
                    raise RuntimeError('Owned setup process remains alive: PID ' + str(child.pid))
            finally:
                record['seconds'] = time.monotonic() - start
                write(process_file, record)


def obtain(name, cache, references, allow_download):
    pin = LOCK['archives'][name]
    target = cache / name

    def check(path):
        if path.is_symlink() or not path.is_file() or path.stat().st_size != pin['bytes'] or sha(path) != pin['sha256']:
            raise ValueError('Corrupt, linked or incomplete pinned archive; preserve it and use another source/cache: ' + str(path))

    if target.exists() or target.is_symlink():
        check(target)
        return target
    for reference in references:
        candidate = reference / name
        if candidate.exists() or candidate.is_symlink():
            check(candidate)
            with candidate.open('rb') as source, target.open('xb') as output:
                shutil.copyfileobj(source, output)
            check(target)
            return target
    if not allow_download:
        raise Unavailable('Pinned archive is missing and --allow-download is absent: ' + name)
    partial = cache / (name + '.part')
    if partial.exists() or partial.is_symlink():
        raise ValueError('Retained partial download exists; choose a new cache: ' + str(partial))
    request = urllib.request.Request(pin['url'], headers={'User-Agent': 'OmicsMatic-private-Minimac-runtime/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response, partial.open('xb') as output:
        count = 0
        while chunk := response.read(1048576):
            count += len(chunk)
            if count > pin['bytes']:
                raise ValueError('Pinned download byte bound exceeded: ' + name)
            output.write(chunk)
    check(partial)
    partial.rename(target)
    return target


def extract_selected(archive, entries, home, report):
    pin = LOCK['archives'][archive.name]
    if pin['kind'] == 'plain':
        if len(entries) != 1:
            raise ValueError('Plain notice must have exactly one locked destination')
        destination, expected = next(iter(entries.items()))
        data = archive.read_bytes()
        target = home / destination
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as output:
            output.write(data)
        if len(data) != expected['bytes'] or sha(target) != expected['sha256']:
            raise ValueError('Pinned original notice differs')
        target.chmod(0o644)
        return
    source = archive
    if pin['kind'] == 'deb':
        if not Path('/usr/bin/dpkg-deb').is_file():
            raise Unavailable('Existing /usr/bin/dpkg-deb is needed for this private profile; install it where permitted or skip')
        label = 'extract-' + archive.name
        result = run(['/usr/bin/dpkg-deb', '--fsys-tarfile', str(archive)], report, label,
                     dict(PATH='/usr/bin:/bin', LANG='C', LC_ALL='C'), byte_bound=536870912)
        if result['exit_code']:
            raise Unavailable('Original Debian-package data extraction failed: ' + archive.name)
        source = report / (label + '.stdout')
    fileobj = None
    if pin['kind'] == 'cpack':
        raw = archive.read_bytes()
        offset = pin['payload_offset']
        prefix = raw[:offset]
        numbers = re.findall(rb'tail \$use_new_tail_syntax \+(\d+) ', prefix)
        if not numbers or prefix.count(b'\n') != int(numbers[-1]) - 1 or raw[offset:offset+2] != b'\x1f\x8b':
            raise ValueError('Pinned CPack payload boundary differs; shell installer is never executed')
        fileobj = io.BytesIO(raw[offset:])
    selected = {entry['member']: (destination, entry) for destination, entry in entries.items()}
    seen = set()
    with tarfile.open(source if fileobj is None else None, mode='r:*', fileobj=fileobj) as tar:
        total = members = 0
        for item in tar:
            members += 1
            total += max(0, item.size)
            if members > 10000 or total > 536870912:
                raise ValueError('Expanded original archive bounds exceeded')
            if item.name not in selected:
                continue  # Unselected links/scripts are never extracted or executed.
            name, expected = selected[item.name]
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or name in seen or not item.isfile() or item.size != expected['bytes']:
                raise ValueError('Pinned selected member type, size or multiplicity differs: ' + name)
            seen.add(name)
            target = home / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with tar.extractfile(item) as source_file, target.open('xb') as output:
                shutil.copyfileobj(source_file, output)
            if sha(target) != expected['sha256']:
                raise ValueError('Extracted member checksum differs: ' + name)
            target.chmod(0o755 if name.startswith('bin/') else 0o644)
    if seen != set(entries):
        raise ValueError('Pinned original member family is incomplete')


def closure(home, pin, report):
    if not Path('/usr/bin/ldd').is_file():
        raise Unavailable('Existing /usr/bin/ldd is needed to admit this host runtime closure')
    external = {}
    binaries = {}
    for name in sorted(key for key in pin['members'] if key.startswith('bin/')):
        binary = home / name
        header = binary.read_bytes()[:20]
        if len(header) != 20 or header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 62:
            raise ValueError('Pinned executable is not Linux little-endian ELF64 x86_64: ' + name)
        label = 'ldd-' + name.replace('/', '-')
        result = run(['/usr/bin/ldd', str(binary)], report, label, environment(home, pin, report), byte_bound=536870912)
        output = (report / (label + '.stdout')).read_text(errors='replace') + (report / (label + '.stderr')).read_text(errors='replace')
        if 'not found' in output:
            raise Unavailable('An original executable requires an unavailable shared library: ' + name)
        static = 'not a dynamic executable' in output or 'statically linked' in output
        if result['exit_code'] and not static:
            raise Unavailable('Cannot establish original ELF library closure: ' + name)
        libraries = []
        for line in output.splitlines():
            match = re.search(r'(?:=>\s*)?(/[^\s]+)\s+\(0x[0-9a-fA-F]+\)', line)
            if not match:
                continue
            original = Path(match.group(1))
            real = original.resolve(strict=True)
            if home in real.parents:
                if original.relative_to(home).as_posix() not in pin['members']:
                    raise ValueError('Unmanaged private shared-library path')
                continue
            if not real.is_file() or real.stat().st_size > 100000000:
                raise Unavailable('Unsupported external runtime library: ' + str(original))
            external[str(original)] = dict(real_path=str(real), bytes=real.stat().st_size, sha256=sha(real))
            libraries.append(str(original))
        binaries[name] = dict(statically_linked=static, external_libraries=libraries)
    tool = Path('/usr/bin/ldd').resolve()
    external['/usr/bin/ldd'] = dict(real_path=str(tool), bytes=tool.stat().st_size, sha256=sha(tool))
    if len(external) > 32:
        raise Unavailable('External runtime closure exceeds supported bound')
    return dict(binaries=binaries, external_files=external)


def required_external(pin):
    required = {'ldd'}
    if pin['style'] in {'legacy100', 'min3'}:
        required |= {'libz.so.1', 'libstdc++.so.6', 'libm.so.6', 'libgcc_s.so.1', 'libc.so.6', 'ld-linux-x86-64.so.2'}
    if pin['style'] == 'legacy100':
        required.add('libpthread.so.0')
    return required


def verify_closure(value, pin):
    files = value.get('external_files', {})
    if not files or len(files) > 32 or '/usr/bin/ldd' not in files:
        raise Unavailable('Missing or oversized external runtime closure')
    names = {Path(name).name for name in files}
    required = required_external(pin)
    if names != required or len(names) != len(files):
        raise Unavailable('External runtime closure is incomplete or differs from the pinned binary family')
    binaries = value.get('binaries', {})
    if set(binaries) != {name for name in pin['members'] if name.startswith('bin/')}:
        raise Unavailable('Runtime binary/library inventory is incomplete')
    static = pin['style'] in {'modern', 'legacy102'}
    for binary in binaries.values():
        paths = binary.get('external_libraries', [])
        if binary.get('statically_linked') != static or {Path(path).name for path in paths} != required - {'ldd'} or set(paths) != set(files) - {'/usr/bin/ldd'} or len(paths) != len(required) - 1:
            raise Unavailable('Original binary static/dynamic library bindings differ')
    for name, expected in files.items():
        path = Path(name)
        if not path.is_absolute() or not path.is_file():
            raise Unavailable('Missing external runtime file: ' + name)
        real = path.resolve()
        if str(real) != expected.get('real_path') or real.stat().st_size != expected.get('bytes') or sha(real) != expected.get('sha256'):
            raise Unavailable('Changed external runtime library/tool; use a new admission: ' + name)


def read_vcf(path):
    opener = gzip.open if path.name.endswith('.gz') else open
    samples = None
    records = {}
    body = []
    with opener(path, 'rt', encoding='utf-8', newline='') as stream:
        for line in stream:
            text = line.rstrip('\r\n')
            if text.startswith('##'):
                continue
            fields = text.split('\t')
            body.append(text)
            if text.startswith('#CHROM\t'):
                samples = fields[9:]
                continue
            if samples is None or len(fields) != len(samples) + 9:
                raise ValueError('Malformed original health VCF sample/row width')
            key = (fields[0], int(fields[1]), fields[3], fields[4])
            if key in records:
                raise ValueError('Duplicate health variant key')
            records[key] = fields
    if not samples or len(set(samples)) != len(samples):
        raise ValueError('Missing or duplicate original health samples')
    return samples, records, hashlib.sha256(('\n'.join(body) + '\n').encode()).hexdigest()


def audit_imputation(path, truth, expected):
    samples, records, body_hash = read_vcf(path)
    if samples != truth[0] or len(records) != expected['variants'] or len(samples) != expected['samples'] or set(records) != set(truth[1]):
        raise ValueError('Actual imputation identity/inventory differs from fixed synthetic truth')
    if body_hash != expected['output_rows_sha256']:
        raise Unavailable('Original imputation records differ from the frozen same-version reference; preserve outputs for independent review, do not weaken admission')
    cells = 0
    for key, fields in records.items():
        formats = fields[8].split(':')
        for i, value in enumerate(fields[9:]):
            output = dict(zip(formats, value.split(':')))
            if len(output) != len(formats) or output.get('GT') != truth[1][key][i+9]:
                raise ValueError('Original imputation does not match known phased health calls')
            dosage = float(output['DS'])
            probabilities = list(map(float, output['GP'].split(',')))
            if len(probabilities) != 3 or any(not math.isfinite(x) or not 0 <= x <= 1 for x in probabilities) or abs(sum(probabilities)-1) > .0011 or not math.isfinite(dosage) or not 0 <= dosage <= 2:
                raise ValueError('Original imputation health posterior is incomplete or invalid')
            cells += 1
    return dict(output_rows_sha256=body_hash, variants=len(records), samples=len(samples), genotype_cells=cells,
                heldout_genotype_cells=expected['heldout_genotype_cells'], known_phased_calls_matched=True)


def audit_export(path, reference):
    samples = None
    mapping = None
    remaining = 0
    seen = set()
    blocks = copies = 0
    with path.open(encoding='utf-8') as stream:
        for line in stream:
            if line.startswith('##'):
                continue
            fields = line.rstrip('\n').split('\t')
            if line.startswith('#CHROM'):
                samples = fields[9:]
                if samples != reference[0]:
                    raise ValueError('Exported reference samples differ')
                continue
            info = dict(value.split('=', 1) for value in fields[7].split(';'))
            if fields[4] == '<BLOCK>':
                if remaining:
                    raise ValueError('Truncated exported block')
                mapping = [list(map(int, value.split(','))) for value in fields[9:]]
                remaining = int(info['VARIANTS'])
                if len(mapping) != len(samples) or any(len(value) != 2 for value in mapping):
                    raise ValueError('Health reference must preserve its explicit diploid mapping')
                blocks += 1
                continue
            if not mapping or remaining <= 0:
                raise ValueError('Unexpected exported reference variant')
            remaining -= 1
            key = (fields[0], int(fields[1]), fields[3], fields[4])
            if key in seen or key not in reference[1]:
                raise ValueError('Exported reference identity differs')
            seen.add(key)
            alleles = list(map(int, info['UHA'].split(',')))
            calls = ['|'.join(str(alleles[index]) for index in sample) for sample in mapping]
            count = sum(map(len, mapping))
            if calls != reference[1][key][9:] or int(info['AN']) != count or int(info['AC']) != sum(int(x) for call in calls for x in call.split('|')):
                raise ValueError('Exported compressed haplotypes or AC/AN differ from original reference')
            copies += count
    if remaining or seen != set(reference[1]):
        raise ValueError('Incomplete exported reference health')
    return dict(blocks=blocks, variants=len(seen), haplotype_copies=copies, original_phased_calls_matched=True)


def health(home, pin, report):
    work = report / 'health'
    work.mkdir()
    fixture = HERE / 'fixtures/minimac'
    for name, expected in LOCK['health_fixtures'].items():
        source = private(fixture / name)
        if not source.is_file() or source.stat().st_size != expected['bytes'] or sha(source) != expected['sha256']:
            raise ValueError('Scientific health fixture changed: ' + name)
        shutil.copy2(source, work / name)
    truth = read_vcf(work / 'truth.vcf')
    reference = read_vcf(work / 'reference.vcf')
    prefix = []
    if pin['emulated']:
        prefix = [str(home / 'bin/qemu-x86_64-static'), '-cpu', 'qemu64']
    binary = home / ('bin/Minimac3' if pin['family'] == 'Minimac3' else 'bin/minimac4')
    command = prefix + [str(binary)]
    env = environment(home, pin, work)
    calls = []
    reference_audit = None
    if pin['style'] == 'modern':
        result = run(command + ['--compress-reference', str(work / 'reference.vcf')], work, 'compress', env)
        calls.append(result)
        if result['exit_code']:
            raise Unavailable('Actual original reference compression failed; inspect retained health diagnostics')
        msav = work / 'reference.msav'
        shutil.copy2(work / 'compress.stdout', msav)
        result = run([str(home / 'bin/sav'), 'export', '--update-info', 'never', '-O', 'vcf', str(msav)], work, 'export', env)
        calls.append(result)
        if result['exit_code']:
            raise Unavailable('Actual original Savvy export failed')
        reference_audit = audit_export(work / 'export.stdout', reference)
        output = work / 'imputed.vcf.gz'
        args = [str(msav), str(work / 'target.vcf.gz'), '--output', str(output), '--format', 'GT,HDS,DS,GP,SD',
                '--threads', '1', '--temp-buffer', '2', '--temp-prefix', str(work / 'temporary_')]
    else:
        output = work / 'imputed.dose.vcf.gz'
        args = ['--noPhoneHome', '--refHaps', str(work / 'reference.m3vcf.gz'), '--haps', str(work / 'target.vcf.gz'),
                '--prefix', str(work / 'imputed'), '--format', 'GT,DS,GP', '--cpus', '1']
    result = run(command + args, work, 'impute', env)
    calls.append(result)
    if result['exit_code']:
        raise Unavailable('Actual original imputation health failed; help/version success is insufficient')
    audit = audit_imputation(output, truth, pin['health'])
    return dict(status=HEALTH_STATUS, family=pin['family'], version=pin['version'], style=pin['style'],
                original_output=audit, reference_export=reference_audit, calls=calls,
                files={path.name: sha(path) for path in work.iterdir() if path.is_file()},
                scope='Pinned diploid synthetic reference/imputation health; not real-data accuracy, mixed-ploidy support or historical/server/paper reproduction.')


def verify_home(home, profile, expected_host):
    if not home.is_dir() or home.is_symlink() or not (home / MARKER).is_file() or (home / MARKER).is_symlink():
        raise Unavailable('Missing, linked or unmanaged existing home; preserve it and use a new private home')
    marker = home / MARKER
    if marker.stat().st_size > 1048576:
        raise Unavailable('Oversized Minimac admission marker')
    value = json.loads(marker.read_text(encoding='utf-8'))
    pin = LOCK['profiles'][profile]
    health_value = value.get('health', {})
    output = health_value.get('original_output', {})
    if value.get('schema_version') != 1 or value.get('profile') != profile or value.get('version') != pin['version'] or value.get('family') != pin['family'] or value.get('lock_sha256') != LOCK_SHA or value.get('host') != expected_host or health_value.get('status') != HEALTH_STATUS or health_value.get('family') != pin['family'] or health_value.get('version') != pin['version'] or health_value.get('style') != pin['style'] or output.get('output_rows_sha256') != pin['health']['output_rows_sha256'] or output.get('variants') != 120 or output.get('samples') != 6 or output.get('genotype_cells') != 720 or output.get('heldout_genotype_cells') != 534 or output.get('known_phased_calls_matched') is not True:
        raise Unavailable('Original runtime profile/version/host/kernel/lock or scientific health admission differs')
    if pin['inspector']:
        audit = health_value.get('reference_export', {})
        if audit.get('variants') != 120 or audit.get('haplotype_copies') != 5760 or audit.get('original_phased_calls_matched') is not True:
            raise Unavailable('Actual compressed-reference export health is missing or inconsistent')
    found = set()
    count = 0
    for path in home.rglob('*'):
        count += 1
        if count > 64 or path.is_symlink() or not (path.is_dir() or path.is_file()):
            raise Unavailable('Unexpected, linked or nonregular runtime member')
        if path.is_file():
            found.add(path.relative_to(home).as_posix())
    if found != set(pin['members']) | {MARKER} or set(value.get('files', {})) != set(pin['members']):
        raise Unavailable('Runtime contains extra, missing or unmanaged files')
    for name, expected in pin['members'].items():
        path = home / name
        if path.stat().st_size != expected['bytes'] or sha(path) != expected['sha256'] or value['files'][name] != expected['sha256'] or name.startswith('bin/') and not os.access(path, os.X_OK):
            raise Unavailable('Changed runtime member or executable permission: ' + name)
    verify_closure(value.get('closure', {}), pin)
    return value


def main(argv=None):
    if sys.version_info < (3, 11):
        print(json.dumps(dict(status='skipped-unavailable-dependency', exit_code=3,
                              error='Python3.11 or later is required for pinned runtime setup; no paths were created')))
        print(RECOVERY, file=sys.stderr)
        return 3
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=sorted(LOCK['profiles']), required=True)
    for name in ['home', 'cache', 'report']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--archive-dir', type=Path, action='append', default=[])
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args(argv)
    report = None
    owns_report = False
    record = dict(status='starting', profile=args.profile, lock_sha256=LOCK_SHA,
                  permissions=dict(install=args.allow_install, download=args.allow_download),
                  started_utc=dt.datetime.now(dt.timezone.utc).isoformat(), required_action=RECOVERY)
    try:
        home_path, cache, report = [private(path) for path in [args.home, args.cache, args.report]]
        references = [private(path) for path in args.archive_dir]
        paths = [home_path, cache, report]
        if any(a == b or a in b.parents or b in a.parents for i, a in enumerate(paths) for b in paths[i+1:]):
            raise ValueError('Home, cache and report must be distinct, non-nested directories')
        if report.exists():
            raise ValueError('Report already exists; preserve it and choose a new report')
        report.mkdir(parents=True)
        owns_report = True
        actual_host = host()
        record.update(host=actual_host, home=str(home_path))
        if home_path.exists():
            admitted = verify_home(home_path, args.profile, actual_host)
            record.update(status='reused-verified-admitted-runtime', health=admitted['health'],
                          runtime_marker_sha256=sha(home_path / MARKER))
        else:
            if not args.allow_install:
                raise Unavailable('--allow-install is absent; no runtime home or cache was created')
            cache.mkdir(parents=True, exist_ok=True)
            if not cache.is_dir():
                raise ValueError('Cache must be a directory')
            pin = LOCK['profiles'][args.profile]
            archives = {name: obtain(name, cache, references, args.allow_download)
                        for name in sorted({entry['archive'] for entry in pin['members'].values()})}
            home_path.mkdir(parents=True, mode=0o700)
            for name, archive in archives.items():
                extract_selected(archive, {key: entry for key, entry in pin['members'].items() if entry['archive'] == name}, home_path, report)
            base = closure(home_path, pin, report)
            verify_closure(base, pin)
            passed = health(home_path, pin, report)
            verify_closure(base, pin)
            admitted = dict(schema_version=1, tool='Minimac', profile=args.profile, family=pin['family'],
                            version=pin['version'], lock_sha256=LOCK_SHA, host=actual_host,
                            files={name: entry['sha256'] for name, entry in pin['members'].items()},
                            closure=base, health=passed, notice_scope=pin.get('notice_scope', ''),
                            upstream_binaries_redistributed=False, global_host_configuration_changed=False)
            write(home_path / MARKER, admitted)
            verify_home(home_path, args.profile, actual_host)
            record.update(status='installed-and-admitted-original-runtime', health=passed,
                          runtime_marker_sha256=sha(home_path / MARKER), notice_scope=pin.get('notice_scope', ''))
        code = 0
    except (Unavailable, PermissionError, urllib.error.URLError, TimeoutError) as failure:
        record.update(status='skipped-unavailable-dependency', error=str(failure))
        code = 3
    except Exception as failure:
        record.update(status='failed-setup-retained', error=repr(failure))
        code = 2
    record['exit_code'] = code
    if owns_report and report is not None and report.is_dir() and not (report / 'installation.json').exists():
        write(report / 'installation.json', record)
        heading = 'SETUP WARNINGS REQUIRE REVIEW' if code == 0 else 'SETUP SKIPPED' if code == 3 else 'SETUP FAILED'
        message = record.get('error', 'Pinned original runtime passed actual scientific health; this does not complete the project imputation adapter.')
        (report / 'WARNINGS.md').write_text('# Minimac ' + heading + '\n\n' + message + '\n\n'
                                          + record.get('notice_scope', '') + '\n\n' + RECOVERY + '\n', encoding='utf-8')
    print(json.dumps({key: record[key] for key in ['status', 'exit_code', 'profile', 'lock_sha256']})
          + (' ' + record['error'] if 'error' in record else ''))
    if code:
        print(RECOVERY, file=sys.stderr)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
