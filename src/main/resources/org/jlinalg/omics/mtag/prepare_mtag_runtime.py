# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a NEW private, byte-inventoried original MTAG1.0.8 Linux runtime; no GWAS analysis."""
import argparse
import hashlib
import json
import os
import platform
import stat
import subprocess
import sys
import traceback
import urllib.request
import urllib.error
import zipfile
from pathlib import Path

ACTION = ('SKIP dependent MTAG analysis until the exact permitted Linux x86_64 Python2 runtime, original sources and reference files are available. '
          'Read WARNINGS.md, setup.json and process logs. Where permitted, supply the named prerequisite or retry installation with explicit '
          '--allow-install/--allow-download into NEW private paths. Complete offline archives use --archive-dir. '
          'Never update shared Python libraries, translate the original source, infer paper settings or repair source results. '
          'A prepared runtime and hashes establish neither analysis admission nor producer authenticity.')


class Unavailable(RuntimeError):
    pass


def save(path, value):
    path.write_text(json.dumps(value, sort_keys=True, indent=2) + '\n', encoding='utf8', newline='\n')


def pin(path):
    h = hashlib.sha256(); size = 0
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            h.update(block); size += len(block)
    return dict(bytes=size, sha256=h.hexdigest())


def canonical(value, exists=True):
    path = Path(value).absolute()
    if path != path.resolve() or (exists and not path.exists()):
        raise Unavailable('A canonical, unlinked absolute prerequisite path is required: ' + str(path))
    if any(c in str(path) for c in '\r\n\0'):
        raise ValueError('Control character in runtime path')
    return path


def closure(root):
    result = {}; total = 0
    for parent, directories, names in os.walk(root, followlinks=False):
        for name in sorted([*directories, *names]):
            path = Path(parent) / name
            mode = path.lstat().st_mode
            relative = path.relative_to(root).as_posix()
            if stat.S_ISLNK(mode):
                target = os.readlink(path)
                resolved = path.resolve(strict=True)
                if not resolved.is_relative_to(root):
                    raise Unavailable('Runtime symbolic link escapes its declared root: ' + relative)
                result[relative] = dict(kind='symlink', target=target)
            elif stat.S_ISREG(mode):
                value = pin(path); total += value['bytes']
                result[relative] = dict(kind='file', mode=stat.S_IMODE(mode), **value)
            elif not stat.S_ISDIR(mode):
                raise Unavailable('Nonregular runtime member: ' + relative)
            if len(result) > 100000 or total > 4_000_000_000:
                raise Unavailable('Private runtime closure exceeds this profile capacity')
    return dict(files=result, regular_bytes=total)


def fetch(url, target, wanted, allowed):
    if not allowed:
        raise Unavailable('Required archive is absent and downloads were not authorized: ' + target.name)
    request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-private-MTAG-runtime/1.0'})
    try:
        with urllib.request.urlopen(request, timeout=120) as response, target.open('xb') as stream:
            size = 0
            while block := response.read(1048576):
                size += len(block)
                if size > wanted['bytes']:
                    raise ValueError('Download exceeds its pinned byte length: ' + target.name)
                stream.write(block)
    except (urllib.error.URLError, OSError) as error:
        raise Unavailable('Required download is unavailable; dependent analysis skipped: ' + repr(error)) from error
    if pin(target) != {k: wanted[k] for k in ['bytes', 'sha256']}:
        raise ValueError('Archive integrity differs: ' + target.name)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True, help='NEW receipts/source/profile directory')
    p.add_argument('--lock', type=Path, default=Path(__file__).with_name('mtag-workflow-lock.json'))
    group = p.add_mutually_exclusive_group(required=True)
    group.add_argument('--existing-environment', type=Path, help='Read-only exact private reference environment')
    group.add_argument('--environment-home', type=Path, help='NEW native Linux environment directory for installation')
    p.add_argument('--micromamba', type=Path, help='Optional existing exact bootstrap binary; otherwise download pinned official binary where allowed')
    p.add_argument('--archive-dir', type=Path, help='Optional complete offline archives, named as in the lock; source archive is mtag-source.zip')
    p.add_argument('--allow-install', action='store_true')
    p.add_argument('--allow-download', action='store_true')
    a = p.parse_args()
    try:
        out = canonical(a.out, False)
    except (Unavailable, OSError) as error:
        print('SKIP3_MTAG_RUNTIME_UNAVAILABLE: ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
        return 3
    if os.path.lexists(out):
        print('FAIL2: runtime output exists; preserve it and choose NEW paths. ' + ACTION, file=sys.stderr)
        return 2
    declared_environment = a.existing_environment or a.environment_home
    try:
        declared_environment = canonical(declared_environment, bool(a.existing_environment))
        if out.is_relative_to(declared_environment) or declared_environment.is_relative_to(out):
            raise ValueError('Keep environment and receipts/source directories separate, without nesting; no existing runtime may be modified')
    except (Unavailable, OSError) as error:
        print('SKIP3_MTAG_RUNTIME_UNAVAILABLE: ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
        return 3
    except ValueError as error:
        print('FAIL2: unsafe/unavailable runtime output/environment path: ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
        return 2
    try:
        out.parent.mkdir(parents=True, exist_ok=True); out.mkdir(mode=0o700)
    except OSError as error:
        print('SKIP3_MTAG_RUNTIME_UNAVAILABLE: ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
        return 3
    state = dict(status='SKIP3_MTAG_RUNTIME_UNAVAILABLE', analysis_executed=False, result_admitted=False, required_action=ACTION)
    (out / 'helper-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    save(out / 'STARTED.json', state)
    native_cache = out / 'mamba-root'

    def run(label, command, cwd=None):
        save(out / (label + '.command.json'), dict(command=command))
        env = dict(PATH='/usr/bin:/bin', HOME=str(out), LC_ALL='C', LANG='C', OPENBLAS_NUM_THREADS='1', OMP_NUM_THREADS='1', MKL_NUM_THREADS='1',
                   PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1', PYTHONHASHSEED='0', MAMBA_ROOT_PREFIX=str(native_cache), MAMBA_NO_BANNER='1')
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(command, cwd=cwd or out, env=env, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=1800)
        save(out / (label + '.process.json'), dict(exit_code=result.returncode))
        if result.returncode:
            raise Unavailable(label + ' failed: exit' + str(result.returncode) + '; read retained stdout/stderr')

    try:
        if platform.system() != 'Linux' or platform.machine() not in ['x86_64', 'amd64']:
            raise Unavailable('Original MTAG runtime preparation requires Linux x86_64, including an existing permitted WSL distribution on Windows')
        if not a.lock.is_file():
            raise Unavailable('The exported original MTAG source/dependency lock is missing')
        if a.archive_dir and not a.archive_dir.is_dir():
            raise Unavailable('The supplied offline archive directory is unavailable')
        lock_path = a.lock.resolve(strict=True); lock = json.loads(lock_path.read_bytes())
        assert lock['tool'] == 'MTAG' and lock['version'] == '1.0.8' and lock['source_commit'] == '9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc'
        assert len(lock['packages']) == 33
        (out / 'mtag-workflow-lock.json').write_bytes(lock_path.read_bytes())
        cache = out / 'archives'; cache.mkdir()

        def archive(name, wanted):
            local = a.archive_dir.resolve(strict=True) / name if a.archive_dir else None
            if local and local.is_file():
                if pin(local) != {k: wanted[k] for k in ['bytes', 'sha256']}:
                    raise ValueError('Offline archive integrity differs: ' + name)
                return local
            target = cache / name
            fetch(wanted['url'], target, wanted, a.allow_download)
            return target

        original = archive('mtag-source.zip', lock['source'])
        source = out / 'mtag-source'; source.mkdir()
        with zipfile.ZipFile(original) as z:
            prefix = 'mtag-' + lock['source_commit'] + '/'; actual = set()
            for member in z.infolist():
                assert member.filename.startswith(prefix)
                relative = member.filename[len(prefix):]
                if member.is_dir():
                    continue
                assert relative in lock['source']['files'] and relative not in actual
                actual.add(relative)
                target = source / relative
                assert target.resolve().is_relative_to(source)
                target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(z.read(member))
                assert pin(target) == lock['source']['files'][relative]
            assert actual == set(lock['source']['files'])
        if a.existing_environment:
            environment = canonical(a.existing_environment)
        else:
            if not a.allow_install:
                raise Unavailable('Installation was not authorized; no environment created')
            environment = canonical(a.environment_home, False)
            if os.path.lexists(environment):
                raise Unavailable('Installation environment already exists; choose a NEW private path')
            if str(environment).startswith('/mnt/'):
                raise Unavailable('Use the native Linux filesystem for package installation; NTFS package symlink installation previously failed')
            environment.parent.mkdir(parents=True, exist_ok=True)
            native_cache = environment.parent / ('.' + environment.name + '-mtag-package-cache')
            if os.path.lexists(native_cache):
                raise Unavailable('The new native Linux package cache already exists; choose a NEW environment path')
            native_cache.mkdir(mode=0o700)
            binary = canonical(a.micromamba) if a.micromamba else archive('micromamba-linux-64', lock['bootstrap'])
            if pin(binary) != {k: lock['bootstrap'][k] for k in ['bytes', 'sha256']}:
                raise Unavailable('The exact tested micromamba2.10.0 bootstrap is unavailable')
            # Preserve caller-provided/offline binaries and their modes.
            private_binary = out / 'micromamba'
            private_binary.write_bytes(binary.read_bytes())
            assert pin(private_binary) == {k: lock['bootstrap'][k] for k in ['bytes', 'sha256']}
            private_binary.chmod(0o700)
            explicit = ['@EXPLICIT']
            for package in lock['packages']:
                local = archive(package['filename'], package)
                explicit.append(local.as_uri() + '#' + package['md5'])
            spec = out / 'explicit-packages.txt'; spec.write_text('\n'.join(explicit) + '\n', encoding='ascii')
            run('install', [str(private_binary), '--no-rc', 'create', '--yes', '--offline', '--prefix', str(environment), '--file', str(spec), '--json'])
        if out.is_relative_to(environment) or environment.is_relative_to(out):
            raise ValueError('Keep environment and receipts/source directories separate, without nesting')
        executable = environment / 'bin/python'
        if not executable.is_file() or not os.access(executable, os.X_OK):
            raise Unavailable('Private Python2 executable is unavailable')
        packages = list((environment / 'conda-meta').glob('*.json'))
        if len(packages) != 33:
            raise Unavailable('The private environment differs from the exact33-package profile')
        observed = {r['name']: r for r in (json.loads(path.read_bytes()) for path in packages)}
        for wanted in lock['packages']:
            r = observed[wanted['name']]
            if r['version'] != wanted['version'] or r['build'] != wanted['build'] or r.get('sha256') != wanted['sha256']:
                raise Unavailable('Installed package identity differs: ' + wanted['name'])
        before = dict(environment=closure(environment), source=closure(source))
        probe = ('import sys,json;sys.path.insert(0,sys.argv[1]);import mtag,numpy,pandas,scipy,bitarray,joblib;'
                 'print(json.dumps(dict(python=sys.version.split()[0],prefix=sys.prefix,mtag=mtag.__version__,'
                 'versions=dict(numpy=numpy.__version__,pandas=pandas.__version__,scipy=scipy.__version__,bitarray=bitarray.__version__,joblib=joblib.__version__),'
                 'modules=[numpy.__file__,pandas.__file__,scipy.__file__,bitarray.__file__,joblib.__file__],search_path=sys.path)))')
        run('versions', [str(executable), '-s', '-B', '-c', probe, str(source)], source)
        versions = json.loads((out / 'versions.stdout').read_bytes())
        if versions['python'] != lock['python_version'] or versions['mtag'] != lock['version'] or versions['versions'] != lock['versions']:
            raise Unavailable('The original tool/dependency versions differ from the tested profile')
        if Path(versions['prefix']).resolve() != environment:
            raise Unavailable('Private interpreter prefix differs')
        for name in versions['modules']:
            if not Path(name).resolve().is_relative_to(environment):
                raise Unavailable('An imported dependency escaped the private environment: ' + name)
        for name in versions['search_path']:
            resolved = Path(name or source).resolve()
            if not (resolved.is_relative_to(environment) or resolved.is_relative_to(source)):
                raise Unavailable('Interpreter search path escapes the private roots: ' + str(resolved))
        after = dict(environment=closure(environment), source=closure(source))
        assert before == after, 'Runtime changed during no-analysis import; preserve attempt without profile admission'
        profile = dict(schema_version=1, tool='MTAG', version='1.0.8', source_commit=lock['source_commit'], platform='linux-x86_64',
            python=str(executable), environment=str(environment), source=str(source), versions=versions, closure=after,
            source_lock=pin(lock_path), preparer=pin(Path(__file__)), setup_only=True,
            system_loader_and_libraries_independently_pinned=False, hashes_are_authenticity=False)
        save(out / 'runtime.json', profile)
        state.update(status='PASS_PRIVATE_MTAG108_RUNTIME_PROFILE_ONLY', profile=pin(out / 'runtime.json'), environment=str(environment),
                     source=str(source), total_regular_bytes=sum(v['regular_bytes'] for v in after.values()), regular_and_symlink_entries=sum(len(v['files']) for v in after.values()))
        print(state['status'])
    except Unavailable as error:
        state['cause'] = repr(error); print(state['status'] + ': ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
    except Exception as error:
        state.update(status='FAIL2_MTAG_RUNTIME_PREPARATION', cause=repr(error))
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
        print(state['status'] + ': ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
    save(out / 'setup.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Runtime profile only; no GWAS fit or result admission.')
        + '\n\nREQUIRED ACTION: ' + ACTION + '\n\nTemporary directories may be cleared. Preflight the complete runtime before every analysis. '
        + 'System loader/libraries are not independently pinned by this profile.\n', encoding='utf8', newline='\n')
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable package, source, path or complete runtime integrity checks')
    raise SystemExit(main())
