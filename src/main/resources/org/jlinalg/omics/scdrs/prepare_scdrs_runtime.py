# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a NEW private historical scDRS Linux runtime; no scientific analysis."""
import argparse
import hashlib
import json
import os
import platform
import stat
import subprocess
import sys
import traceback
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

ACTION = ('SKIP dependent scDRS analysis until the exact permitted private Linux x86_64 Python3.10 runtime '
    'and requested original scDRS version are available. Read WARNINGS.md, setup.json and process logs. '
    'Supply permitted prerequisites, or retry with explicit --allow-install/--allow-download into NEW paths. '
    'Offline archives are supported. Never modify shared libraries, silently encode user covariates, '
    'replace historical versions or repair scientific results. Setup grants no analysis admission or producer authentication.')


class Unavailable(RuntimeError):
    pass


def save(path, value):
    path.write_text(json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + '\n', encoding='utf-8', newline='\n')


def pin(path):
    size = 0
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            h.update(block)
            size += len(block)
    return dict(bytes=size, sha256=h.hexdigest())


def canonical(value, exists=True):
    path = Path(value).absolute()
    if path != path.resolve() or (exists and not path.exists()):
        raise Unavailable('Canonical unlinked prerequisite path required: ' + str(path))
    if any(c in str(path) for c in '\r\n\0'):
        raise ValueError('Control character in runtime path')
    return path


def closure(root):
    files = {}
    total = 0
    for parent, directories, names in os.walk(root, followlinks=False):
        for name in sorted([*directories, *names]):
            path = Path(parent) / name
            mode = path.lstat().st_mode
            relative = path.relative_to(root).as_posix()
            if stat.S_ISLNK(mode):
                assert path.resolve(strict=True).is_relative_to(root), 'Runtime link escapes root: ' + relative
                files[relative] = dict(kind='symlink', target=os.readlink(path))
            elif stat.S_ISREG(mode):
                value = pin(path)
                total += value['bytes']
                files[relative] = dict(kind='file', mode=stat.S_IMODE(mode), **value)
            elif not stat.S_ISDIR(mode):
                raise ValueError('Nonregular runtime entry: ' + relative)
            assert len(files) <= 100000 and total <= 4_000_000_000, 'Runtime closure capacity exceeded'
    return dict(files=files, regular_bytes=total)


def fetch(url, target, wanted, allowed):
    if not allowed:
        raise Unavailable('Archive absent and downloads not authorized: ' + target.name)
    request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-private-scDRS-runtime/1.0'})
    try:
        with urllib.request.urlopen(request, timeout=120) as response, target.open('xb') as stream:
            size = 0
            while block := response.read(1048576):
                size += len(block)
                assert size <= wanted['bytes'], 'Download exceeds pinned length: ' + target.name
                stream.write(block)
    except (urllib.error.URLError, OSError) as error:
        raise Unavailable('Required archive download unavailable: ' + repr(error)) from error
    assert pin(target) == {k: wanted[k] for k in ['bytes', 'sha256']}, 'Downloaded archive integrity differs: ' + target.name


PROBE = r'''import sys,json,importlib.metadata as m
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import scdrs,numpy,scipy,pandas,scanpy,anndata,skmisc,statsmodels,numba,umap,h5py,fire,pkg_resources
modules=[scdrs,numpy,scipy,pandas,scanpy,anndata,skmisc,statsmodels,numba,umap,h5py,fire,pkg_resources]
print(json.dumps(dict(python=sys.version.split()[0],prefix=sys.prefix,scdrs=scdrs.__version__,
    distributions={d.metadata['Name']:d.version for d in m.distributions()},
    modules={x.__name__:str(Path(x.__file__).resolve()) for x in modules},search_path=sys.path),sort_keys=True))
'''


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--version', choices=['1.0.0', '1.0.1', '1.0.2'], required=True)
    p.add_argument('--lock', type=Path, default=Path(__file__).with_name('scdrs-workflow-lock.json'))
    group = p.add_mutually_exclusive_group(required=True)
    group.add_argument('--existing-environment', type=Path)
    group.add_argument('--environment-home', type=Path)
    p.add_argument('--micromamba', type=Path)
    p.add_argument('--archive-dir', type=Path)
    p.add_argument('--allow-install', action='store_true')
    p.add_argument('--allow-download', action='store_true')
    a = p.parse_args()
    try:
        out = canonical(a.out, False)
        environment = canonical(a.existing_environment or a.environment_home, bool(a.existing_environment))
        if out.is_relative_to(environment) or environment.is_relative_to(out):
            raise ValueError('Environment and receipt/source roots must be separate without nesting')
        if os.path.lexists(out):
            raise ValueError('Output already exists; preserve it and choose a NEW path')
        if platform.system() != 'Linux' or platform.machine() not in ['amd64', 'x86_64']:
            raise Unavailable('Historical execution requires a permitted Linux x86_64 host, including WSL')
        out.parent.mkdir(parents=True, exist_ok=True)
        out.mkdir(mode=0o700)
    except (Unavailable, OSError) as error:
        print('SKIP3_SCDRS_RUNTIME_UNAVAILABLE: ' + repr(error) + '\nREQUIRED ACTION: ' + ACTION, file=sys.stderr)
        return 3
    except Exception as error:
        print('FAIL2_SCDRS_RUNTIME_PREPARATION: ' + repr(error) + '\nREQUIRED ACTION: ' + ACTION, file=sys.stderr)
        return 2
    state = dict(status='SKIP3_SCDRS_RUNTIME_UNAVAILABLE', analysis_executed=False,
                 result_admitted=False, required_action=ACTION)
    (out / 'helper-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    save(out / 'STARTED.json', state)
    package_cache = out / 'mamba-root'

    def run(label, command, cwd=None):
        save(out / (label + '.command.json'), dict(command=command))
        env = dict(PATH='/usr/bin:/bin', HOME=str(out), LC_ALL='C.UTF-8', LANG='C.UTF-8',
            OPENBLAS_NUM_THREADS='1', OMP_NUM_THREADS='1', MKL_NUM_THREADS='1', NUMBA_NUM_THREADS='1',
            NUMBA_CACHE_DIR=str(out / 'numba-cache'), MPLCONFIGDIR=str(out / 'matplotlib-cache'),
            PYTHONDONTWRITEBYTECODE='1', PYTHONNOUSERSITE='1', PYTHONHASHSEED='0',
            MAMBA_ROOT_PREFIX=str(package_cache), MAMBA_NO_BANNER='1')
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.run(command, cwd=cwd or out, env=env, stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, timeout=1800)
        save(out / (label + '.process.json'), dict(exit_code=process.returncode))
        if process.returncode:
            raise Unavailable(label + ' failed; read retained stdout/stderr')

    try:
        if not a.lock.is_file():
            raise Unavailable('The exported scDRS source/dependency lock is absent')
        lock_path = canonical(a.lock)
        lock = json.loads(lock_path.read_bytes())
        assert lock['schema_version'] == 1 and lock['tool'] == 'scDRS' and lock['platform'] == 'linux-x86_64'
        assert lock['python_version'] == '3.10.16' and len(lock['packages']) == 25 and len(lock['wheels']) == 46
        (out / 'scdrs-workflow-lock.json').write_bytes(lock_path.read_bytes())
        cache = out / 'archives'
        cache.mkdir()

        def archive(name, wanted):
            assert Path(name).name == name and name not in ['', '.', '..']
            choices = []
            if a.archive_dir:
                if not a.archive_dir.is_dir():
                    raise Unavailable('Offline archive directory is absent')
                choices.append(a.archive_dir.resolve() / name)
            if wanted.get('delivery') == 'bundled':
                choices.append(lock_path.parent / name)
            for local in choices:
                if local.is_file():
                    assert pin(local) == {k: wanted[k] for k in ['bytes', 'sha256']}, 'Local archive integrity differs: ' + name
                    return local
            if wanted.get('delivery') == 'bundled':
                raise Unavailable('Exported source-audited bundled wheel is absent: ' + name)
            target = cache / name
            fetch(wanted['url'], target, wanted, a.allow_download)
            return target

        source_info = lock['sources'][a.version]
        original = archive(source_info['filename'], source_info)
        source = out / 'scdrs-source'
        source.mkdir()
        with zipfile.ZipFile(original) as z:
            prefix = 'scDRS-' + source_info['commit'] + '/'
            seen = set()
            for member in z.infolist():
                assert member.filename.startswith(prefix)
                relative = member.filename[len(prefix):]
                if member.is_dir():
                    continue
                assert relative in source_info['files'] and relative not in seen
                assert (member.external_attr >> 16) & 0o170000 != 0o120000, 'Source archive symlink refused'
                seen.add(relative)
                target = source / relative
                assert target.resolve().is_relative_to(source)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(member))
                assert pin(target) == source_info['files'][relative]
            assert seen == set(source_info['files'])
        if not a.existing_environment:
            if not a.allow_install:
                raise Unavailable('Installation not authorized; dependent analysis skipped')
            if os.path.lexists(environment):
                raise ValueError('Environment already exists; choose a NEW private environment')
            if str(environment).startswith('/mnt/'):
                raise Unavailable('Install packages on the native Linux filesystem, outside /mnt')
            environment.parent.mkdir(parents=True, exist_ok=True)
            package_cache = environment.parent / ('.' + environment.name + '-scdrs-package-cache')
            if os.path.lexists(package_cache):
                raise ValueError('Private package cache already exists; use NEW paths')
            package_cache.mkdir(mode=0o700)
            bootstrap = canonical(a.micromamba) if a.micromamba else archive('micromamba-linux-64', lock['bootstrap'])
            assert pin(bootstrap) == {k: lock['bootstrap'][k] for k in ['bytes', 'sha256']}, 'Bootstrap integrity differs'
            private = out / 'micromamba'
            private.write_bytes(bootstrap.read_bytes())
            private.chmod(0o700)
            explicit = ['@EXPLICIT']
            for package in lock['packages']:
                local = archive(package['filename'], package)
                explicit.append(local.as_uri() + '#' + package['md5'])
            spec = out / 'explicit-packages.txt'
            spec.write_text('\n'.join(explicit) + '\n', encoding='ascii')
            run('base-install', [str(private), '--no-rc', 'create', '--yes', '--offline', '--prefix', str(environment), '--file', str(spec), '--json'])
            wheel_dir = out / 'wheelhouse'
            wheel_dir.mkdir()
            for wheel in lock['wheels']:
                local = archive(wheel['filename'], wheel)
                (wheel_dir / wheel['filename']).write_bytes(local.read_bytes())
            requirements = out / 'requirements.txt'
            requirements.write_text(''.join(w['name'] + '==' + w['version'] + '\n' for w in lock['wheels']), encoding='ascii')
            run('wheels-install', [str(environment / 'bin/python'), '-s', '-B', '-m', 'pip', 'install', '--no-index',
                '--no-deps', '--find-links', str(wheel_dir), '--requirement', str(requirements)])
        executable = environment / 'bin/python'
        if not executable.is_file() or not os.access(executable, os.X_OK):
            raise Unavailable('Private Python executable is unavailable')
        conda = {v['name']: v for v in (json.loads(p.read_bytes()) for p in (environment / 'conda-meta').glob('*.json'))}
        if len(conda) != len(lock['packages']):
            raise Unavailable('Private base package count differs from the tested profile')
        for wanted in lock['packages']:
            actual = conda.get(wanted['name'], {})
            if any(actual.get(k) != wanted[k] for k in ['version', 'build', 'sha256']):
                raise Unavailable('Private base package identity differs: ' + wanted['name'])
        before = dict(environment=closure(environment), source=closure(source))
        run('versions', [str(executable), '-s', '-B', '-c', PROBE, str(source)], source)
        versions = json.loads((out / 'versions.stdout').read_bytes())
        if versions['python'] != lock['python_version'] or versions['scdrs'] != a.version or versions['distributions'] != lock['installed_distributions']:
            raise Unavailable('Imported Python, scDRS or installed dependency versions differ')
        assert Path(versions['prefix']).resolve() == environment
        for name, value in versions['modules'].items():
            assert Path(value).is_relative_to(source if name == 'scdrs' else environment), 'Imported module escapes pinned roots: ' + name
        for value in versions['search_path']:
            path = Path(value or source).resolve()
            assert path.is_relative_to(source) or path.is_relative_to(environment), 'Python search path escapes pinned roots'
        after = dict(environment=closure(environment), source=closure(source))
        assert before == after, 'Runtime changed during setup-only imports'
        profile = dict(schema_version=1, tool='scDRS', version=a.version, source_commit=source_info['commit'],
            platform='linux-x86_64', python=str(executable), environment=str(environment), source=str(source),
            versions=versions, closure=after, source_lock=pin(lock_path), preparer=pin(Path(__file__)), setup_only=True,
            system_loader_and_libraries_independently_pinned=False, hashes_are_authenticity=False)
        save(out / 'runtime.json', profile)
        state.update(status='PASS_PRIVATE_HISTORICAL_SCDRS_RUNTIME_PROFILE_ONLY', profile=pin(out / 'runtime.json'),
            environment=str(environment), source=str(source), version=a.version,
            total_regular_bytes=sum(v['regular_bytes'] for v in after.values()),
            regular_and_symlink_entries=sum(len(v['files']) for v in after.values()))
    except Unavailable as error:
        state['cause'] = repr(error)
    except Exception as error:
        state.update(status='FAIL2_SCDRS_RUNTIME_PREPARATION', cause=repr(error))
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf-8')
    save(out / 'setup.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Setup only; no scientific analysis.')
        + '\n\n**REQUIRED ACTION:** ' + ACTION + '\n\nTemporary directories may be cleared; preflight the complete runtime before each analysis. '
        'System loader and libraries are not independently pinned.\n', encoding='utf-8', newline='\n')
    print(state['status'] + (' ' + state['cause'] if 'cause' in state else ''))
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable source, archive or runtime integrity checks')
    raise SystemExit(main())
