#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Provision exact private original MAFFT releases; setup never admits user analysis.

Requires Linux x86_64 Python3.11+, the declared existing shell/coreutils/Perl
helpers, prlimit/setsid and fresh ASCII-only private home/report paths. Explicit
installation/download flags are independent. Complete failures/partials remain.
The bounded owned-process primitive is copied from install_iqtree.py without
changing that independently validated installer; MAFFT_TMPDIR is explicitly set.
"""
import argparse,datetime,hashlib,json,os,platform,re,signal,stat,struct,subprocess,sys,tarfile,time
from pathlib import Path,PurePosixPath
import urllib.error,urllib.request
HERE=Path(__file__).resolve().parent
LOCK_PATH=HERE/'mafft-runtime-lock.json'
LOCK_SHA256='e66f242f6f3ee4dfe34bc971869bd301ac4d4eec1ef629d786062472764cffcb'
MARKER='.omicsmatic-mafft.json'
VERSIONS=('7.313','7.475','7.487','7.490','7.505','7.526')
ACTION=('Skip dependent MAFFT analysis while the exact permitted runtime or required analysis admission is unavailable. '
 'Use Linux x86_64 Python3.11+ (Linux Python in WSL on Windows), existing permitted shell/coreutils/Perl/prlimit/setsid helpers, '
 'and NEW canonical ASCII-only home/report paths. Run python3 install_mafft.py --profile linux-x86_64-mafft-7.526 '
 '--home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT_DIRECTORY --allow-install; select the exact requested edition. '
 'Add --allow-download only where permitted, or supply --archive-dir OFFLINE_ARCHIVE_DIRECTORY. '
 'Existing complete homes can be rechecked with a NEW report without install/download flags. '
 'Require report/COMPLETED.json and matching complete hashes. Preserve corrupt caches, changed/partial homes and diagnostics; '
 'correct the named cause and use new paths. Never substitute a release for P052 major-only v7. '
 'Setup runs only three complete tiny synthetic alignment controls and admits no user analysis, alignment accuracy or whole-paper result. '
 'Full host/library binding and typed public workflows remain required. Cell Ranger remains excluded.')

class Unavailable(Exception):
    """Unavailable/forbidden prerequisites require skipped dependent analysis."""

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


def fingerprint(path, maximum=67108864, allow_system_hardlinks=False):
    path = private(path, True)
    before = path.stat()
    if not stat.S_ISREG(before.st_mode) or (before.st_nlink < 1 if allow_system_hardlinks else before.st_nlink != 1) or not 0 <= before.st_size <= maximum:
        raise ValueError('Linked, nonregular or oversized file: ' + str(path))
    result = dict(bytes=before.st_size, sha256=sha(path))
    after = path.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns, before.st_mode, before.st_nlink) != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns, after.st_mode, after.st_nlink):
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
    env = dict(MAFFT_TMPDIR=str(work), PATH='/usr/bin:/bin', HOME=str(work), TMPDIR=str(work), LANG='C', LC_ALL='C', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')
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


def emit(record):
    raw = (json.dumps(record, ensure_ascii=False) + '\n').encode('utf-8')
    if hasattr(sys.stdout, 'buffer'):
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
    else:
        sys.stdout.write(raw.decode('utf-8'))


def interrupted(signum, _frame):
    raise KeyboardInterrupt('Setup interrupted by signal ' + str(signum))


def load_kit():
    if sha(LOCK_PATH) != LOCK_SHA256:
        raise ValueError('MAFFT runtime lock changed; retain the source and inspect its provenance')
    lock = read_json(LOCK_PATH)
    if lock['schema_version'] != 1 or lock['tool'] != 'MAFFT' or set(lock['profiles']) != {'linux-x86_64-mafft-' + v for v in VERSIONS}:
        raise ValueError('Unsupported complete MAFFT profile set')
    for name, wanted in lock['bundled'].items():
        member_name(name); check_file(HERE / 'mafft-runtime' / name, wanted)
    return lock


def host(lock):
    if sys.version_info < (3, 11) or platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('MAFFT setup requires Linux x86_64 Python3.11+; use Linux Python inside WSL on Windows')
    if not hasattr(os, 'waitid') or not hasattr(os, 'WNOWAIT'):
        raise Unavailable('Owned process cleanup requires waitid/WNOWAIT')
    if Path('/etc/ld.so.preload').exists() or Path('/etc/ld.so.preload').is_symlink():
        raise Unavailable('OS loader preloads are outside this declared helper profile')
    helpers = {}
    for name in lock['host_helpers']:
        path = Path(name)
        if not path.is_file() or not os.access(path, os.X_OK):
            raise Unavailable('Required permitted original-launcher helper unavailable: ' + name)
        real = path.resolve(strict=True)
        helpers[name] = dict(real_path=str(real), **fingerprint(real, allow_system_hardlinks=True), mode=stat.S_IMODE(real.stat().st_mode), links=real.stat().st_nlink)
    return dict(os='Linux', architecture='x86_64', kernel=platform.release(), helpers=helpers,
                scope='Declared launcher/helper bindings only; full dynamic library/Perl host admission remains required before user analysis')


def ascii_owned(path):
    if not re.fullmatch(r'/[A-Za-z0-9/._+-]+', str(path)):
        raise ValueError('Original portable launcher requires canonical ASCII home/report paths without whitespace or shell metacharacters')


def expected_files(pin, lock):
    result = {name: {key: value[key] for key in ['bytes', 'sha256', 'mode']} for name, value in pin['files'].items()}
    for name, value in lock['bundled'].items():
        result['share/' + name] = dict(bytes=value['bytes'], sha256=value['sha256'], mode='0444')
    return result


def make_parents(root, parent):
    current = root
    for part in parent.relative_to(root).parts:
        current = current / part
        if not current.exists(): current.mkdir(mode=0o700)
        info = current.lstat()
        if not stat.S_ISDIR(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o700:
            raise ValueError('Private runtime parent changed or has an unsafe mode: ' + str(current))


def obtain(pin, cache, archives, allow_download):
    for path in [cache / pin['filename'], *[p / pin['filename'] for p in archives]]:
        if path.exists() or path.is_symlink():
            check_file(path, pin); return path
    if not allow_download:
        raise Unavailable('Exact archive absent; explicit --allow-download or a permitted --archive-dir is required')
    url = pin['url']
    if not url.startswith('https://mafft.cbrc.jp/alignment/software/mafft-') or not url.endswith('-linux.tgz'):
        raise ValueError('Pinned archive is outside the official MAFFT portable source')
    target = cache / pin['filename']; partial = target.with_name(target.name + '.partial')
    if partial.exists() or partial.is_symlink():
        raise ValueError('Prior partial download preserved; inspect it and use a fresh cache')
    with urllib.request.urlopen(url, timeout=30) as source, partial.open('xb') as output:
        if not source.geturl().startswith('https://mafft.cbrc.jp/alignment/software/'):
            raise ValueError('Archive redirect left the pinned official MAFFT origin')
        count = 0
        while block := source.read(1048576):
            count += len(block)
            if count > pin['bytes']:
                raise ValueError('Complete original archive byte capacity exceeded; partial retained')
            output.write(block)
        identity = os.fstat(output.fileno())
    check_file(partial, pin)
    os.link(partial, target, follow_symlinks=False)
    now = partial.lstat()
    if (now.st_dev, now.st_ino) != (identity.st_dev, identity.st_ino):
        raise ValueError('Download path replaced; preserve both paths')
    partial.unlink(); check_file(target, pin); return target


def extract(archive_path, pin, home):
    before = fingerprint(archive_path); expected = pin['files']; seen = set(); directories = {pin['archive']['root']}
    for name in expected:
        for parent in PurePosixPath(name).parents:
            if str(parent) != '.': directories.add(pin['archive']['root'] + '/' + str(parent))
    encountered = set(); total = 0
    with tarfile.open(archive_path, 'r:gz') as archive:
        for item in archive:
            member_name(item.name)
            if item.name.rstrip('/') in encountered:
                raise ValueError('Duplicate original archive member')
            encountered.add(item.name.rstrip('/'))
            if len(encountered) > 128:
                raise ValueError('Complete archive member capacity exceeded')
            if item.isdir():
                if item.name.rstrip('/') not in directories:
                    raise ValueError('Unknown original archive directory')
                continue
            prefix = pin['archive']['root'] + '/'
            if not item.isfile() or not item.name.startswith(prefix):
                raise ValueError('Linked, nonregular or out-of-root original archive member')
            name = item.name[len(prefix):]
            if name not in expected or name in seen or item.size != expected[name]['bytes']:
                raise ValueError('Extra, duplicate or incorrectly sized original archive member')
            total += item.size
            if total > 134217728:
                raise ValueError('Complete decoded archive byte capacity exceeded')
            seen.add(name); target = home / member_name(name)
            make_parents(home, target.parent)
            with archive.extractfile(item) as source, target.open('xb') as output:
                while block := source.read(1048576): output.write(block)
            target.chmod(int(expected[name]['mode'], 8)); check_file(target, expected[name])
    if seen != set(expected) or fingerprint(archive_path) != before:
        raise ValueError('Incomplete original archive or changed source bytes')


def verify_files(home, pin, lock, marker_expected):
    expected = expected_files(pin, lock); files = set(expected) | ({MARKER} if marker_expected else set())
    directories = {''}
    for name in files:
        for parent in PurePosixPath(name).parents:
            if str(parent) != '.': directories.add(str(parent))
    actual = set()
    for directory, dirs, names in os.walk(home, followlinks=False):
        relative = Path(directory).relative_to(home).as_posix()
        if relative == '.': relative = ''
        if relative not in directories or stat.S_IMODE(Path(directory).lstat().st_mode) != 0o700:
            raise ValueError('Unknown or changed private MAFFT directory: ' + relative)
        for name in dirs + names:
            path = Path(directory) / name; info = path.lstat()
            if not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode)) or info.st_nlink != 1 and stat.S_ISREG(info.st_mode):
                raise ValueError('Linked or nonregular private MAFFT member')
        actual.update((Path(directory)/n).relative_to(home).as_posix() for n in names)
    if actual != files:
        raise ValueError('Complete private MAFFT file census differs; preserve this home')
    elves = {}
    for name, wanted in expected.items():
        path = home / name; check_file(path, wanted)
        if stat.S_IMODE(path.stat().st_mode) != int(wanted['mode'], 8):
            raise ValueError('Private MAFFT file mode changed: ' + name)
        with path.open('rb') as source: magic = source.read(4)
        if magic == b'\x7fELF': elves[name] = static_elf(path.read_bytes())
    if set(elves) != set(pin['static_elf_programs']):
        raise ValueError('Complete original static ELF program census differs')
    if marker_expected and stat.S_IMODE((home/MARKER).stat().st_mode) != 0o444:
        raise ValueError('Private MAFFT marker mode changed')
    return elves


def verify_completion(report, home, profile):
    report = private(report, True); completed = read_json(report/'COMPLETED.json')
    if completed.get('status') != 'completed-runtime-setup-only' or completed.get('home') != str(home) or completed.get('profile') != profile or completed.get('scientific_analysis_admitted') is not False or completed.get('runtime_marker_sha256') != sha(home/MARKER):
        raise ValueError('MAFFT setup completion binding differs')
    inventory(report, 16777216, 256)
    files = completed.get('files'); directories = completed.get('directories')
    required = {'installation.json','WARNINGS.md','version/process.json',*[p['name']+'/process.json' for p in load_kit()['setup_health']]}
    if not isinstance(files, dict) or not required <= set(files):
        raise ValueError('Incomplete MAFFT setup report')
    actual = {p.relative_to(report).as_posix() for p in report.rglob('*') if p.is_file()}
    dirs = sorted(p.relative_to(report).as_posix() for p in report.rglob('*') if p.is_dir())
    if actual != set(files) | {'COMPLETED.json'} or directories != dirs:
        raise ValueError('Complete MAFFT report file/directory census differs')
    for name, wanted in files.items(): member_name(name); check_file(report/name, wanted)
    setup = read_json(report/'installation.json')
    if setup.get('exit_code') != 0 or setup.get('scientific_analysis_admitted') is not False or setup.get('runtime_marker_sha256') != completed['runtime_marker_sha256']:
        raise ValueError('Setup has no complete successful report')
    return completed


def smoke(home, pin, lock, report):
    version = report/'version'; version.mkdir(mode=0o700)
    receipt = owned_run(limited(['/bin/bash',str(home/pin['program']),'--version']),version)
    text = (version/'process.stdout').read_text() + (version/'process.stderr').read_text()
    if not re.search(r'(?<![A-Za-z0-9.])v'+re.escape(pin['version'])+r'(?![0-9.])',text):
        raise ValueError('Original MAFFT version banner differs')
    result = dict(version_probe=receipt, controls=[])
    references = {r['original_control']: r['reference_output'] for r in pin['health_references']}
    for control in lock['setup_health']:
        work = report/control['name']; work.mkdir(mode=0o700)
        for name, resource in control['inputs'].items():
            raw = (home/'share'/resource).read_bytes()
            with (work/name).open('xb') as target: target.write(raw)
        process = owned_run(limited(['/bin/bash',str(home/pin['program']),*control['arguments']]),work,wall_seconds=45,maximum_bytes=16777216)
        checked = {}
        for name, wanted in references[control['name']].items():
            if name == 'process.stderr': continue  # Timings, temporary paths and progress remain raw observations.
            check_file(work/name,wanted); checked[name]=wanted
        for name, resource in control['inputs'].items(): check_file(work/name,lock['bundled'][resource])
        result['controls'].append(dict(name=control['name'],checked_original_outputs=checked,owned_live_after_cleanup=process['owned_live_after_cleanup']))
    return result


def provision(args, report, record):
    home = private(args.home); cache = private(args.cache)
    record.update(home=str(home),cache=str(cache),profile=args.profile)
    if any(a == b or a.is_relative_to(b) or b.is_relative_to(a) for a,b in [(home,cache),(home,report),(cache,report)]):
        raise ValueError('Home, cache and report must be disjoint trees')
    if not home.exists() and not args.allow_install:
        raise Unavailable('Explicit --allow-install is required before creating a runtime home')
    lock = load_kit(); current_host = host(lock); ascii_owned(home); ascii_owned(report)
    if args.profile not in lock['profiles']:
        raise Unavailable('Requested exact MAFFT edition/profile unavailable; no other edition is substituted')
    pin = lock['profiles'][args.profile]; installer = sha(__file__); reused = home.exists()
    record.update(version=pin['version'],host=current_host,lock_sha256=LOCK_SHA256,installer_sha256=installer,known_warning=pin['known_warning'],scope=lock['runtime_admission_scope'])
    if reused:
        if not home.is_dir() or not (home/MARKER).is_file():
            raise ValueError('Existing unmanaged/provisional home preserved; use a new private home')
        marker = read_json(home/MARKER)
        for key,wanted in dict(profile=args.profile,home=str(home),lock_sha256=LOCK_SHA256,installer_sha256=installer,host=current_host,files=expected_files(pin,lock),scientific_analysis_admitted=False).items():
            if marker.get(key) != wanted: raise ValueError('Existing complete home binding differs: '+key)
        verify_files(home,pin,lock,True); verify_completion(marker['setup_report_directory'],home,args.profile)
    else:
        archives = [private(p,True) for p in args.archive_dir]
        if not cache.exists(): cache.mkdir(mode=0o700)
        if not cache.is_dir(): raise ValueError('Archive cache must be a private directory')
        guard=cache/'.mafft-install.lock'
        try:
            with guard.open('xb') as output:
                output.write((str(os.getpid())+'\n').encode()); identity=os.fstat(output.fileno())
        except FileExistsError as error:
            raise Unavailable('Existing cache lock preserved; inspect its owner and use a fresh cache if needed') from error
        try:
            archive=obtain(pin['archive'],cache,archives,args.allow_download)
            record['archive']=dict(path=str(archive),**fingerprint(archive))
            load_kit(); home.mkdir(mode=0o700); extract(archive,pin,home)
            for name,wanted in lock['bundled'].items():
                source=HERE/'mafft-runtime'/name;check_file(source,wanted);target=home/'share'/member_name(name)
                make_parents(home,target.parent)
                with source.open('rb') as src,target.open('xb') as dst:
                    while block:=src.read(1048576):dst.write(block)
                target.chmod(0o444);check_file(target,wanted)
            verify_files(home,pin,lock,False)
        finally:
            if guard.exists() and not guard.is_symlink():
                now=guard.stat()
                if (now.st_dev,now.st_ino)==(identity.st_dev,identity.st_ino):guard.unlink()
    record['stage']='verify-original-runtime'
    health=smoke(home,pin,lock,report);elf=verify_files(home,pin,lock,reused)
    if host(lock)!=current_host or sha(__file__)!=installer:raise ValueError('Declared helper or installer changed during setup')
    load_kit()
    if not reused:
        marker=dict(schema_version=1,tool='MAFFT',status='installed-smoke-tested-runtime-only',profile=args.profile,version=pin['version'],home=str(home),setup_report_directory=str(report),lock_sha256=LOCK_SHA256,installer_sha256=installer,host=current_host,files=expected_files(pin,lock),elf=elf,health=health,scientific_analysis_admitted=False)
        write_json(home/MARKER,marker);(home/MARKER).chmod(0o444);verify_files(home,pin,lock,True)
    record.update(status='verified-reused-smoke-tested-runtime-only' if reused else 'installed-smoke-tested-runtime-only',runtime_reused=reused,runtime_marker_sha256=sha(home/MARKER),files=expected_files(pin,lock),elf=elf,health=health)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile',required=True);parser.add_argument('--home',type=Path,required=True)
    parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path,action='append',default=[])
    parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true')
    args=parser.parse_args(argv)
    record=dict(recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),download_authorized=args.allow_download,installation_authorized=args.allow_install,scientific_analysis_admitted=False,required_action=ACTION)
    report=None
    try:
        report=private(args.report)
        if report.exists():raise ValueError('Existing report preserved; use a NEW report directory')
        for path in [private(args.home),private(args.cache)]:
            if path==report or path.is_relative_to(report) or report.is_relative_to(path):raise ValueError('Report must be disjoint from home/cache')
        report.mkdir(mode=0o700);record['report']=str(report);provision(args,report,record);code=0
    except (Unavailable,urllib.error.URLError,TimeoutError) as error:
        code=3;record.update(status='SKIP3-runtime-unavailable',error=str(error))
    except PermissionError as error:
        code=2 if record.get('stage')=='verify-original-runtime' else 3
        record.update(status='FAIL2-retained-no-analysis' if code==2 else 'SKIP3-runtime-unavailable',error=str(error))
    except (Exception,KeyboardInterrupt) as error:
        code=2;record.update(status='FAIL2-retained-no-analysis',error=repr(error))
    record['exit_code']=code
    try:
        if report is None or record.get('report')!=str(report):raise ValueError('Fresh writable report directory not created')
        write_json(report/'installation.json',record)
        with (report/'WARNINGS.md').open('x',encoding='utf-8',newline='\n') as out:
            out.write('# MAFFT '+('SETUP ONLY — NO USER ANALYSIS ADMITTED' if code==0 else 'SKIP3 — SKIP DEPENDENT ANALYSIS' if code==3 else 'FAIL2 — NO RESULT ADMITTED')+'\n\n'+record.get('error','Three complete synthetic alignments passed; public analysis admission remains unfinished.')+'\n\n')
            if record.get('known_warning'):out.write('**ORIGINAL RELEASE WARNING:** '+record['known_warning']+'\n\n')
            out.write('Required action: '+ACTION+'\n')
        if code==0:
            home=Path(record['home']);files={p.relative_to(report).as_posix():fingerprint(p) for p in report.rglob('*') if p.is_file()}
            directories=sorted(p.relative_to(report).as_posix() for p in report.rglob('*') if p.is_dir())
            write_json(report/'COMPLETED.json',dict(status='completed-runtime-setup-only',home=str(home),profile=args.profile,runtime_marker_sha256=record['runtime_marker_sha256'],files=files,directories=directories,scientific_analysis_admitted=False))
            verify_completion(report,home,args.profile)
    except (Exception,KeyboardInterrupt) as error:
        code=2;record.update(status='FAIL2-report-write-failed',exit_code=2,report_error=repr(error))
        if report is not None and record.get('report')==str(report):
            try:write_json(report/'FAILURE.json',record)
            except (Exception,KeyboardInterrupt):pass
            try:
                with (report/'FAILURE.md').open('x',encoding='utf-8',newline='\n') as failure:
                    failure.write('# MAFFT FAIL2 — NO COMPLETED SETUP OR ANALYSIS\n\n'+record['report_error']+'\n\nThe earlier installation.json and WARNINGS.md are provisional retained records. A home marker alone is not completion. Preserve this whole attempt, correct the named failure and use NEW home/report paths.\n\nRequired action: '+ACTION+'\n')
            except (Exception,KeyboardInterrupt):pass
    emit(record)
    print(('WARNING: SETUP ONLY; NO USER ANALYSIS ADMITTED. ' if code==0 else 'WARNING: SKIP3; SKIP DEPENDENT ANALYSIS. ' if code==3 else 'ERROR: FAIL2; NO RESULT ADMITTED. ')+record.get('error',record.get('report_error',''))+'\nRequired action: '+ACTION,file=sys.stderr)
    return code


if __name__=='__main__':
    signal.signal(signal.SIGTERM,interrupted)
    raise SystemExit(main())
