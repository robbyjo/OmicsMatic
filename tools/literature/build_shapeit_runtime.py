#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rebuild and adopt the exact controlled SHAPEIT5 common-stage runtime privately.

Pinned downloads and unchanged upstream sources are retained in a fresh workspace.
No scientific analysis is launched. Existing homes/workspaces are never replaced.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
KIT = HERE / 'shapeit-source-build'
CONFIG_SHA256 = 'b414a4b48bbd311f57230090aa7e8ae79b108e2607c9a88cf5a42d0e22b899b7'
ACTION = ('Skip dependent SHAPEIT analyses while setup is unavailable or fails. On permitted Linux x86_64 '
          'with Python >=3.11, AVX2/FMA on every processor, /usr/bin/dpkg-deb, setsid and prlimit, run '
          'python3 tools/literature/build_shapeit_runtime.py --work NEW_BUILD_DIRECTORY '
          '--home NEW_RUNTIME_HOME --cache DOWNLOAD_CACHE --report NEW_REPORT.json '
          '--allow-download --allow-source-build --allow-install. Reserve at least 8 GiB free disk '
          'and sufficient memory for two compiler jobs. Downloads include a 435 MB source archive and '
          'a separately locked compiler environment. Inspect the report and retained process logs; '
          'Use checkout and setup paths containing only ASCII letters, digits, slash, dot, underscore, plus or hyphen. '
          'correct the named cause and use fresh work/home/report paths. Never substitute another '
          'binary, dependency, source revision or hash. Setup alone is not scientific admission; '
          'use the typed common-stage recipe and its complete native input/output audits.')


class Unavailable(Exception):
    """A required permission, host capability or dependency is unavailable."""


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(path):
    if Path(path).stat().st_size > 1048576:
        raise ValueError('Setup metadata exceeds its complete byte capacity')
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate setup metadata key: ' + key)
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=pairs)


def verify_file(path, expected):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise ValueError('Missing, linked or nonregular setup input: ' + str(path))
    before = path.stat()
    if before.st_size != expected['bytes'] or digest(path) != expected['sha256']:
        raise ValueError('Complete pinned setup input differs: ' + str(path))
    after = path.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns):
        raise ValueError('Setup input changed while hashing: ' + str(path))


def canonical(path, fresh=False):
    path = Path(os.path.abspath(path))
    if re.fullmatch(r'/[A-Za-z0-9_./+\-]*', str(path)) is None:
        raise ValueError('Build paths and checkout must use shell-safe ASCII letters/digits, slash, dot, underscore, plus or hyphen')
    parent = path.parent if fresh else path
    if parent.resolve(strict=True) != parent:
        raise ValueError('Setup paths must have canonical existing parents: ' + str(path))
    if fresh and (path.exists() or path.is_symlink()):
        raise ValueError('Preserve existing paths and choose a fresh destination: ' + str(path))
    return path


def distinct_paths(work, home, cache):
    for left, right in [(work, home), (work, cache), (home, cache)]:
        if left == right or left in right.parents or right in left.parents:
            raise ValueError('Build workspace, runtime home and download cache must be separate trees')


def load_config():
    if digest(KIT / 'build-lock.json') != CONFIG_SHA256:
        raise ValueError('Pinned source-build configuration changed')
    config = read_json(KIT / 'build-lock.json')
    if config['schema_version'] != 1:
        raise ValueError('Unsupported setup configuration schema')
    for name, expected in config['files'].items():
        verify_file(KIT / name, expected)
    for name, expected in config['project_files'].items():
        verify_file(ROOT / name, expected)
    return config


def host_capabilities():
    if sys.version_info < (3, 11) or platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('Source setup requires Linux x86_64 and Python 3.11 or later')
    if Path('/etc/ld.so.preload').exists() or Path('/etc/ld.so.preload').is_symlink():
        raise Unavailable('System loader preloads are outside this controlled profile')
    flags = [set(line.split(':', 1)[1].split()) for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('flags') and ':' in line]
    if not flags or any(not {'avx2', 'fma'} <= value for value in flags):
        raise Unavailable('AVX2 and FMA are required on every processor')
    helpers = {}
    for name in ['/usr/bin/dpkg-deb', '/usr/bin/setsid', '/usr/bin/prlimit']:
        path = Path(name).resolve(strict=True)
        if not path.is_file() or not os.access(path, os.X_OK):
            raise Unavailable('Required permitted helper is unavailable: ' + name)
        helpers[name] = dict(real_path=str(path), bytes=path.stat().st_size, sha256=digest(path))
    return helpers


def download(cache, name, expected):
    target = cache / name
    if target.exists() or target.is_symlink():
        verify_file(target, expected)
        return dict(name=name, source='verified-cache', **expected)
    partial = cache / (name + '.partial')
    if partial.exists() or partial.is_symlink():
        raise ValueError('Retain the partial download and select a fresh cache: ' + str(partial))
    request = urllib.request.Request(expected['url'], headers={'User-Agent': 'OmicsMatic-SHAPEIT-source-setup'})
    count = 0
    start = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=120) as response, partial.open('xb') as output:
            if response.geturl().split(':', 1)[0] != 'https':
                raise ValueError('A pinned download redirected outside HTTPS')
            while block := response.read(1048576):
                count += len(block)
                if count > expected['bytes'] or time.monotonic() - start > 600:
                    raise ValueError('Download exceeded complete byte or wall-clock capacity; partial retained')
                output.write(block)
    except (urllib.error.URLError, TimeoutError) as error:
        raise Unavailable('Pinned download unavailable: ' + name + ': ' + str(error)) from error
    verify_file(partial, expected)
    # Hard-linking is an exclusive publication: it never replaces a competing cache entry.
    os.link(partial, target)
    partial.unlink()
    return dict(name=name, source='downloaded-and-hash-verified', **expected)


def safe_name(name):
    path = PurePosixPath(name)
    if not name or '\\' in name or path.is_absolute() or '..' in path.parts or any(ord(c) < 32 for c in name) or len(name) > 4096:
        raise ValueError('Unsafe source archive member: ' + name)
    return path


def unpack_pixi(archive, destination, expected):
    with tarfile.open(archive, 'r:gz') as source:
        members = source.getmembers()
        if len(members) != 1 or members[0].name != 'pixi' or not members[0].isfile() or members[0].size != expected['bytes']:
            raise ValueError('Bootstrap archive differs from its single pinned executable layout')
        with source.extractfile(members[0]) as data, destination.open('xb') as output:
            shutil.copyfileobj(data, output, 1048576)
    verify_file(destination, expected)
    destination.chmod(0o700)


def unpack_source(archive, destination, manifest, commit):
    expected = {}
    for item in manifest['files']:
        name = item['path']; safe_name(name)
        if name in expected or item['mode'] not in {'100644', '100755', '120000'}:
            raise ValueError('Duplicate or unsupported selected source entry: ' + name)
        expected[name] = item
    prefix = 'shapeit-' + commit
    seen, links = set(), []
    destination.mkdir(mode=0o700)
    count = total = 0
    with tarfile.open(archive, 'r|gz') as source:
        for member in source:
            count += 1; total += member.size
            if count > 100000 or total > 4 * 1024**3:
                raise ValueError('Complete source archive exceeds entry or decoded-byte capacity')
            path = safe_name(member.name)
            if path.parts[0] != prefix:
                raise ValueError('Unexpected source archive root')
            name = PurePosixPath(*path.parts[1:]).as_posix()
            if name not in expected:
                continue
            if name in seen:
                raise ValueError('Duplicate selected archive member: ' + name)
            seen.add(name); item = expected[name]
            if item['mode'] == '120000':
                if not member.issym() or member.linkname != item['link']:
                    raise ValueError('Selected source link changed: ' + name)
                links.append(item)
                continue
            if not member.isfile() or member.size != item['bytes']:
                raise ValueError('Selected source type or size changed: ' + name)
            target = destination / name; target.parent.mkdir(parents=True, exist_ok=True)
            with source.extractfile(member) as data, target.open('xb') as output:
                shutil.copyfileobj(data, output, 1048576)
            target.chmod(0o755 if item['mode'] == '100755' else 0o644)
            verify_file(target, item)
    if seen != set(expected):
        raise ValueError('Incomplete selected upstream source inventory')
    for item in links:
        path = destination / item['path']; path.parent.mkdir(parents=True, exist_ok=True)
        # Relative upstream links may use '..'; their resolved targets must stay in this source tree.
        if Path(item['link']).is_absolute() or (path.parent / item['link']).resolve() == destination or destination not in (path.parent / item['link']).resolve().parents:
            raise ValueError('Selected source link escapes the private source tree')
        path.symlink_to(item['link'])
    verify_source(destination, manifest)


def verify_source(source, manifest):
    for item in manifest['files']:
        path = source / item['path']
        if item['mode'] == '120000':
            try: resolved = path.resolve(strict=True)
            except (FileNotFoundError, RuntimeError) as error:
                raise ValueError('Pinned source link target is missing or cyclic: ' + item['path']) from error
            if not path.is_symlink() or path.readlink().as_posix() != item['link'] or source not in resolved.parents:
                raise ValueError('Pinned upstream source link changed: ' + item['path'])
            raw = item['link'].encode()
            if hashlib.sha256(raw).hexdigest() != item['sha256']:
                raise ValueError('Pinned source-link bytes changed')
        else:
            verify_file(path, item)


def live_group(group):
    """Observe nonterminal members of the owned session while its leader is unreaped."""
    result = []
    for directory in Path('/proc').iterdir():
        if not directory.name.isdecimal(): continue
        try:
            raw = (directory/'stat').read_text(); fields = raw[raw.rindex(')')+2:].split()
        except (FileNotFoundError, ProcessLookupError): continue
        if int(fields[2]) == group and int(fields[3]) == group and fields[0] not in {'Z', 'X', 'x'}:
            result.append(int(directory.name))
    return sorted(result)


def run_process(work, name, command, env, cwd, timeout=1800, output_limit=67108864):
    stdout_path, stderr_path = work/(name+'.stdout'), work/(name+'.stderr')
    command = ['/usr/bin/prlimit', '--as=4294967296', '--cpu=1800', '--fsize=2147483648',
               '--nofile=1024', '--core=0', '--', *command]
    child = None; started = time.monotonic(); record = dict(command=command, timeout_seconds=timeout)
    try:
        with stdout_path.open('xb') as stdout, stderr_path.open('xb') as stderr:
            child = subprocess.Popen(command, cwd=cwd, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
            record['pid'] = child.pid
            (work/(name+'.running.json')).write_text(json.dumps(record, indent=2)+'\n')
            while True:
                # WNOWAIT reserves the leader PID through group cleanup and prevents PID reuse.
                status = os.waitid(os.P_PID, child.pid, os.WEXITED | os.WNOHANG | os.WNOWAIT)
                if status is not None:
                    record['exit_code'] = status.si_status if status.si_code == os.CLD_EXITED else -status.si_status
                    break
                if time.monotonic()-started > timeout or stdout_path.stat().st_size > output_limit or stderr_path.stat().st_size > output_limit:
                    raise ValueError('Owned setup process exceeded wall-clock or log capacity: ' + name)
                time.sleep(.1)
            if stdout_path.stat().st_size > output_limit or stderr_path.stat().st_size > output_limit or time.monotonic()-started > timeout:
                raise ValueError('Owned setup process exceeded complete log or wall-clock capacity: ' + name)
            if record['exit_code']:
                raise ValueError('Owned setup process failed: '+name+' (exit '+str(record['exit_code'])+'); inspect retained stdout/stderr')
            if live_group(child.pid):
                raise ValueError('Owned setup process exited with live workers: ' + name)
    finally:
        if child is not None:
            # The leader is still unreaped. Its new session/group cannot be recycled here.
            for sig, grace in [(signal.SIGTERM, 2), (signal.SIGKILL, 2)]:
                if not live_group(child.pid): break
                try: os.killpg(child.pid, sig)
                except ProcessLookupError: break
                deadline = time.monotonic()+grace
                while live_group(child.pid) and time.monotonic() < deadline: time.sleep(.05)
            record['owned_live_after_cleanup'] = live_group(child.pid)
            child.wait(timeout=5)
        record['elapsed_seconds'] = time.monotonic()-started
        record['stdout_sha256'] = digest(stdout_path) if stdout_path.exists() else None
        record['stderr_sha256'] = digest(stderr_path) if stderr_path.exists() else None
        (work/(name+'.process.json')).write_text(json.dumps(record, indent=2)+'\n')
        if record.get('owned_live_after_cleanup'):
            raise ValueError('Owned setup workers remain live; no runtime admission')
    return record


def unpack_libc(tar_path, destination, members, runtime):
    by_path = {PurePosixPath(value).as_posix(): name for name, value in members.items()}
    found = set(); destination.mkdir(mode=0o700)
    with tarfile.open(tar_path, 'r:') as source:
        for member in source:
            path = PurePosixPath(member.name).as_posix()
            if path not in by_path:
                continue
            name = by_path[path]
            if name in found or not member.isfile() or member.size != runtime['files'][name]['bytes']:
                raise ValueError('Pinned libc member type, size or uniqueness changed: ' + name)
            found.add(name); target = destination / Path(name).name
            with source.extractfile(member) as data, target.open('xb') as output:
                shutil.copyfileobj(data, output, 1048576)
            verify_file(target, runtime['files'][name])
    if found != set(members):
        raise ValueError('Pinned libc package lacks required private runtime members')


def canonical_binary_prefix(raw, placeholder, replacement):
    """Apply Conda's fixed-size C-string relocation; never shift ELF addresses."""
    old, new = placeholder.encode('utf-8'), replacement.encode('utf-8')
    if not old or b'\0' in old or b'\0' in new or len(new) > len(old):
        raise ValueError('Canonical prefix must fit the pinned package placeholder')
    parts = []
    for part in raw.split(b'\0'):
        value = part.replace(old, new)
        parts.append(value + b'\0' * (len(part) - len(value)))
    result = b'\0'.join(parts)
    if len(result) != len(raw):
        raise ValueError('Library relocation changed complete file size')
    return result


def reconstruct_libraries(package_cache, destination, sources, runtime, canonical_prefix):
    destination.mkdir(mode=0o700)
    records = []
    for name, item in sources.items():
        safe_name(item['package']); safe_name(item['path'])
        path = package_cache/item['package']/item['path']
        verify_file(path, dict(bytes=item['raw_bytes'], sha256=item['raw_sha256']))
        raw = path.read_bytes()
        output = canonical_binary_prefix(raw, item['prefix_placeholder'], canonical_prefix) if item['prefix_placeholder'] else raw
        target = destination/Path(name).name
        with target.open('xb') as stream: stream.write(output)
        verify_file(target, runtime['files'][name])
        records.append(dict(member=name, package=item['package'], raw_sha256=item['raw_sha256'],
                            canonical_sha256=runtime['files'][name]['sha256'], prefix_normalized=bool(item['prefix_placeholder'])))
    return records


def build(args):
    if not (args.allow_download and args.allow_source_build and args.allow_install):
        raise Unavailable('Explicit --allow-download, --allow-source-build and --allow-install are required')
    helpers = host_capabilities()
    canonical(ROOT)
    builder_identity = dict(bytes=Path(__file__).stat().st_size, sha256=digest(__file__))
    config = load_config()
    work, home = canonical(args.work, True), canonical(args.home, True)
    cache = canonical(args.cache) if args.cache.exists() else canonical(args.cache, True)
    distinct_paths(work, home, cache)
    report = canonical(args.report, True)
    if any(parent == report or parent in report.parents for parent in [work, home, cache]):
        raise ValueError('The fresh report must be outside the work, runtime and download-cache trees')
    if shutil.disk_usage(work.parent).free < 8*1024**3:
        raise Unavailable('Reserve at least 8 GiB free disk before dependency provisioning and compilation')
    work.mkdir(mode=0o700)
    if not cache.exists(): cache.mkdir(mode=0o700)
    downloads = [download(cache, name, expected) for name, expected in config['downloads'].items()]
    unpack_pixi(cache/'pixi.tar.gz', work/'pixi', config['pixi_binary'])
    for name in ['pixi.toml', 'pixi.lock']:
        (work/name).write_bytes((KIT/name).read_bytes())
    (work/'user-home').mkdir(mode=0o700)
    env = dict(PATH='/usr/bin:/bin', LC_ALL='C', LANG='C', HOME=str(work/'user-home'),
               PIXI_CACHE_DIR=str(work/'pixi-cache'), PIXI_NO_CONFIG='true', PIXI_NO_PROGRESS='true', PIXI_COLOR='never',
               RAYON_NUM_THREADS='2', TOKIO_WORKER_THREADS='2', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1')
    for name in ['HTTPS_PROXY', 'HTTP_PROXY', 'ALL_PROXY', 'NO_PROXY', 'https_proxy', 'http_proxy', 'all_proxy', 'no_proxy']:
        if name in os.environ: env[name] = os.environ[name]
    runs = [run_process(work, 'dependency-install', [str(work/'pixi'), 'install', '--locked', '--no-config', '--concurrent-downloads', '4', '--concurrent-solves', '1', '--manifest-path', str(work/'pixi.toml')], env, work)]
    for name in ['pixi.toml', 'pixi.lock']: verify_file(work/name, config['files'][name])
    prefix, source = work/'.pixi/envs/default', work/'source'
    specs = (prefix/'lib/gcc/x86_64-conda-linux-gnu/14.4.0/specs').read_text()
    specs = specs.replace(str(prefix), config['canonical_toolchain'])
    if hashlib.sha256(specs.encode()).hexdigest() != config['canonical_compiler_specs_sha256']:
        raise ValueError('Canonical compiler specs differ from the controlled compiler')
    (work/'canonical-compiler.specs').write_text(specs)
    manifest = read_json(KIT/'source-files.json')
    unpack_source(cache/'shapeit-source.tar.gz', source, manifest, config['source_commit'])
    env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin', LD_LIBRARY_PATH=str(prefix/'lib'), CONDA_PREFIX=str(prefix))
    maps = ['-specs='+str(work/'canonical-compiler.specs'),
            '-ffile-prefix-map='+str(prefix)+'='+config['canonical_toolchain'],
            '-ffile-prefix-map='+str(source)+'='+config['canonical_source'],
            '-ffile-prefix-map='+str(ROOT/'tools/literature/shapeit-reference/JoinedWireProtocol.cpp')+'='+config['canonical_protocol']]
    command = [str(prefix/'bin/make'), '-j2', 'CXX='+str(prefix/'bin/x86_64-conda-linux-gnu-g++')+' -std=c++20 '+' '.join(maps),
               'HTSLIB_INC='+str(prefix/'include'), 'HTSLIB_LIB='+str(prefix/'lib/libhts.so'), 'BOOST_INC='+str(prefix/'include'),
               'BOOST_LIB_IO='+str(prefix/'lib/libboost_iostreams.so'), 'BOOST_LIB_PO='+str(prefix/'lib/libboost_program_options.so'),
               'BOOST_LIB_SE='+str(prefix/'lib/libboost_serialization.so'),
               'LDFLAG=-O3 -L'+str(prefix/'lib')+' -Wl,-rpath,'+config['canonical_toolchain']+'/lib -Wl,-rpath-link,'+str(prefix/'lib'),
               'COMMIT_VERS=c34d4db', 'COMMIT_DATE=2026-05-19', 'OFILE='+' '.join(config['common_object_order'])]
    runs.append(run_process(work, 'compile-common', command, env, source/'phase_common'))
    command = [str(prefix/'bin/x86_64-conda-linux-gnu-g++'), '-std=c++20', '-O2', *maps,
               '-I'+str(source/'phase_common/src'), '-I'+str(prefix/'include'), '-L'+str(prefix/'lib'),
               '-Wl,-rpath,'+config['canonical_toolchain']+'/lib', '-Wl,-rpath-link,'+str(prefix/'lib'), str(ROOT/'tools/literature/shapeit-reference/JoinedWireProtocol.cpp'),
               *[str(source/'phase_common/obj'/name) for name in config['join_object_order']],
               '-lhts', '-lboost_iostreams', '-lboost_program_options', '-lz', '-lbz2', '-lm', '-lpthread', '-llzma', '-lcurl', '-lcrypto', '-ldeflate',
               '-o', str(work/'joined-wire')]
    runs.append(run_process(work, 'compile-join', command, env, work))
    verify_source(source, manifest)
    # dpkg-deb emits a plain tar to a bounded log file; no package is installed into the OS.
    runs.append(run_process(work, 'unpack-libc', ['/usr/bin/dpkg-deb', '--fsys-tarfile', str(cache/'libc6.deb')], env, work))
    runtime = read_json(HERE/'shapeit-runtime-lock.json')
    unpack_libc(work/'unpack-libc.stdout', work/'libc', config['libc_members'], runtime)
    library_sources = read_json(KIT/'runtime-library-sources.json')
    library_records = reconstruct_libraries(work/'pixi-cache/pkgs', work/'runtime-libraries', library_sources, runtime, config['canonical_toolchain'])
    locators = {}
    for name, expected in runtime['files'].items():
        if name == 'bin/phase_common': path = source/'phase_common/bin/phase_common'
        elif name == 'bin/joined-wire': path = work/'joined-wire'
        elif name in config['libc_members']: path = work/'libc'/Path(name).name
        elif name.startswith('lib/'): path = work/'runtime-libraries'/Path(name).name
        else: path = ROOT/'LICENSES'/Path(name).name
        verify_file(path, expected); locators[name] = str(path)
    (work/'exact-member-locators.json').write_text(json.dumps(locators, indent=2)+'\n')
    spec = importlib.util.spec_from_file_location('omicsmatic_shapeit_exact_adopter', HERE/'install_shapeit.py')
    adopter = importlib.util.module_from_spec(spec); spec.loader.exec_module(adopter)
    installed = adopter.install(home, work/'exact-member-locators.json', True)
    for name, expected in config['project_files'].items(): verify_file(ROOT/name, expected)
    for name, expected in config['files'].items(): verify_file(KIT/name, expected)
    if digest(KIT/'build-lock.json') != CONFIG_SHA256: raise ValueError('Source-build configuration changed during setup')
    verify_file(Path(__file__), builder_identity)
    warning_lines = []
    for name in ['compile-common', 'compile-join']:
        with (work/(name+'.stderr')).open(encoding='utf-8', errors='replace') as stream:
            warning_lines.extend(line.rstrip() for line in stream if 'warning' in line.lower() or 'not found' in line.lower())
    return dict(status='installed-exact-source-built-runtime-only', scientific_analysis_admitted=False,
                profile=runtime['profile'], work=str(work), home=str(home), config_sha256=CONFIG_SHA256,
                builder_sha256=builder_identity['sha256'], compiler_warning_lines=warning_lines,
                source_commit=config['source_commit'], selected_source_files=len(manifest['files']),
                source_bytes_unchanged=True, complete_runtime_files=len(locators),
                downloads=downloads, helpers=helpers, processes=runs, library_reconstruction=library_records,
                adopter_result=installed, required_action=ACTION)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['work', 'home', 'cache', 'report']: parser.add_argument('--'+name, type=Path, required=True)
    for name in ['download', 'source-build', 'install']: parser.add_argument('--allow-'+name, action='store_true')
    args = parser.parse_args()
    if args.report.exists() or args.report.is_symlink(): parser.error('Preserve the existing report and choose a new path')
    def terminate(signum, frame):
        raise KeyboardInterrupt('Source setup cancelled by signal ' + str(signum))
    previous = signal.signal(signal.SIGTERM, terminate)
    try:
        try:
            result = build(args); code = 0
        except (Unavailable, PermissionError, FileNotFoundError) as error:
            result = dict(status='SKIP3-source-runtime-unavailable', error=str(error), scientific_analysis_admitted=False, required_action=ACTION); code = 3
        except (ValueError, OSError, tarfile.TarError, KeyboardInterrupt) as error:
            result = dict(status='FAIL2-retained-no-analysis', error=str(error) or type(error).__name__, scientific_analysis_admitted=False, required_action=ACTION); code = 2
    finally:
        signal.signal(signal.SIGTERM, previous)
    try:
        with args.report.open('x', encoding='utf-8', newline='\n') as output: output.write(json.dumps(result, indent=2)+'\n')
    except OSError as error:
        print(json.dumps(dict(status='FAIL2-report-write-failed', error=str(error), scientific_analysis_admitted=False, required_action=ACTION)), file=sys.stderr)
        return 2
    print(json.dumps({key:value for key,value in result.items() if key not in {'downloads','helpers','processes','adopter_result','library_reconstruction'}}))
    if code:
        print('WARNING: '+result['status']+': '+result['error']+'\nRequired action: '+ACTION, file=sys.stderr)
    elif result['compiler_warning_lines']:
        print('WARNING: Upstream compiler/make diagnostics are retained in the setup report and process logs. '
              'The controlled shared-library profile is admitted only after all 32 member hashes match; '
              'setup itself runs no scientific analysis.', file=sys.stderr)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
