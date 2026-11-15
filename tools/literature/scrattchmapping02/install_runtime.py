#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install two locked environments and unchanged original packages in a NEW private directory."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, os, shutil, signal, subprocess, sys, tarfile, time, traceback, urllib.request
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, Unavailable, pin, load, save, source_tree, sources, inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument('--manager', required=True, type=Path, help='Permitted executable Pixi manager')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--source-cache', type=Path, help='Read-only directory with exact filenames from install-source-profile.json')
    parser.add_argument('--cache-dir', type=Path, help='Permitted read/write Pixi package cache; required for offline setup')
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--wall-seconds', type=int, default=7200)
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    child = None
    calls = []
    start = time.monotonic()

    def stop_child():
        if child is not None and child.poll() is None:
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()

    def run(label, command, env, cwd):
        nonlocal child
        if time.monotonic() - start > args.wall_seconds:
            raise Unavailable('Declared installation wall limit exceeded before ' + label)
        command = list(map(str, command))
        began = time.monotonic()
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            child = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, start_new_session=True)
            save(out / (label + '-LIVE.json'), dict(pid=child.pid, command=command))
            while True:
                try:
                    code = child.wait(timeout=10)
                    break
                except subprocess.TimeoutExpired:
                    print('SAME_PRIVATE_INSTALL_CHILD_RUNNING', label, child.pid, flush=True)
                    if time.monotonic() - start > args.wall_seconds:
                        raise Unavailable('Declared installation wall limit exceeded; retain partial files and retry NEW paths')
        record = dict(label=label, command=command, exit_code=code, elapsed_seconds=time.monotonic() - began)
        calls.append(record)
        save(out / (label + '-PROCESS.json'), record)
        print('ORIGINAL_INSTALL_STEP', label, code, flush=True)
        if code:
            raise Unavailable('Original installation/audit failed: ' + label + '; inspect its full logs; dependent analysis skipped')

    def archive(entry):
        target = out / 'downloads' / entry['filename']
        local = args.source_cache.resolve() / entry['filename'] if args.source_cache else None
        if local is not None and local.is_file():
            shutil.copyfile(local, target)
            if pin(target) != entry['archive']:
                raise ValueError('Supplied original archive differs: ' + str(local))
            save(out / (entry['name'] + '-DOWNLOAD.json'), dict(source='local-copy', path=str(local), archive=pin(target)))
            return target
        if not args.allow_download:
            raise Unavailable('Downloads forbidden; supply exact original source-cache file ' + entry['filename'])
        attempts = []
        for ordinal, url in enumerate(entry['urls'], 1):
            partial = out / 'downloads' / (entry['filename'] + '.attempt-' + str(ordinal))
            try:
                request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-original-scrattchmapping02-setup/1'})
                with urllib.request.urlopen(request, timeout=60) as response, partial.open('xb') as stream:
                    total = 0
                    while True:
                        block = response.read(min(1048576, entry['archive']['bytes'] + 1 - total))
                        if not block:
                            break
                        stream.write(block)
                        total += len(block)
                        if total > entry['archive']['bytes']:
                            raise ValueError('Downloaded original archive exceeds its pinned size: ' + url)
                    stream.flush()
                    metadata = dict(url=url, final_url=response.url, status=response.status,
                                    content_type=response.headers.get('Content-Type'), identity=pin(partial))
                attempts.append(metadata)
                if pin(partial) != entry['archive']:
                    raise ValueError('Downloaded original archive differs from the declared source: ' + url)
                # Retain the download attempt as well as the installation input.
                shutil.copyfile(partial, target)
                save(out / (entry['name'] + '-DOWNLOAD.json'), dict(attempts=attempts, archive_retained=True))
                return target
            except (OSError, TimeoutError) as error:
                attempts.append(dict(url=url, error=repr(error)))
        save(out / (entry['name'] + '-DOWNLOAD.json'), dict(attempts=attempts, archive_retained=True))
        raise Unavailable('Original archive unavailable: ' + entry['filename'] + '; inspect download attempts or supply the exact local archive')

    def pristine(entry, archive_path):
        destination = out / 'runtime/source-original' / entry['name']
        destination.mkdir()
        actual = {}
        seen = set()
        with tarfile.open(archive_path, 'r:gz') as stream:
            for member in stream.getmembers():
                path = PurePosixPath(member.name)
                if (path.is_absolute() or any(p in ['.', '..'] for p in path.parts) or '\\' in member.name
                        or not path.parts or path.parts[0] != entry['archive_top_level']):
                    raise ValueError('Unsafe or unexpected original archive member: ' + member.name)
                if len(path.parts) == 1:
                    if not member.isdir():
                        raise ValueError('Original archive root is not a directory')
                    continue
                relative = PurePosixPath(*path.parts[1:]).as_posix()
                if relative in seen:
                    raise ValueError('Duplicate original archive member: ' + relative)
                seen.add(relative)
                target = destination.joinpath(*path.parts[1:])
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                elif member.isfile():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with stream.extractfile(member) as content, target.open('xb') as output:
                        shutil.copyfileobj(content, output)
                    target.chmod(member.mode & 0o777)
                    actual[relative] = pin(target)
                else:
                    raise ValueError('Unsupported original source archive member: ' + member.name)
        if actual != entry['original_members']:
            raise ValueError('Complete original source member identities differ: ' + entry['name'])
        build = out / 'runtime/build-original' / entry['name']
        shutil.copytree(destination, build)
        save(out / (entry['name'] + '-PRISTINE-INPUTS.json'), dict(repository=entry['repository'], commit=entry['commit'],
            archive=pin(archive_path), source=str(destination), build=str(build), all_original_members=actual))
        return build

    try:
        protected = [HERE, args.manager.resolve()]
        protected += [p.resolve() for p in [args.source_cache, args.cache_dir] if p is not None]
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in protected):
            raise ValueError('Use a NEW canonical installation output separate from source, manager and caches')
        out.mkdir(exist_ok=False)
        owned = True
        if not args.allow_install:
            raise Unavailable('Installation forbidden without --allow-install; no dependent analysis started')
        if sys.platform != 'linux':
            raise Unavailable('The selected original runtime requires Linux/WSL')
        if args.wall_seconds < 1:
            raise ValueError('Positive installation wall limit required')
        manager = args.manager.resolve()
        if not manager.is_file() or not os.access(manager, os.X_OK):
            raise Unavailable('Supply a permitted executable Pixi manager with --manager')
        if not args.allow_download and (args.source_cache is None or args.cache_dir is None):
            raise Unavailable('Offline installation requires complete --source-cache and --cache-dir, or use --allow-download when permitted')
        profile = load(HERE / 'install-source-profile.json')
        runner_before = sources()
        installer_before = {name: pin(HERE / name) for name in ['install_runtime.py', 'install-source-profile.json']}
        for name in ['runtime', 'conda-manager', 'downloads', 'home', 'tmp', 'pixi-home']:
            (out / name).mkdir()
        for name in ['library', 'source-original', 'build-original']:
            (out / 'runtime' / name).mkdir()
        cache = args.cache_dir.resolve() if args.cache_dir else out / 'pixi-cache'
        cache.mkdir(exist_ok=True)
        for directory, stem in [(out / 'runtime', 'reference-'), (out / 'conda-manager', 'conda-manager-')]:
            for filename in ['pixi.toml', 'pixi.lock']:
                shutil.copyfile(HERE / (stem + filename), directory / filename)
        env = dict(os.environ, HOME=str(out / 'home'), TMPDIR=str(out / 'tmp'), PIXI_HOME=str(out / 'pixi-home'),
                   PIXI_CACHE_DIR=str(cache), PIXI_NO_PATH_UPDATE='1', PYTHONDONTWRITEBYTECODE='1',
                   R_ENVIRON_USER='/dev/null', R_PROFILE_USER='/dev/null', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1')
        for key in ['R_HOME', 'R_LIBS', 'PYTHONPATH', 'PYTHONHOME']:
            env.pop(key, None)
        save(out / 'INSTALLATION-INPUTS.json', dict(manager=pin(manager), runner_sources=runner_before,
             installer_sources=installer_before, installation_allowed=args.allow_install, download_allowed=args.allow_download,
             cache=str(cache), private_runtime_only=True, analysis_executed=False))
        archives = {entry['name']: archive(entry) for entry in profile['source_packages'] + profile['annotation_packages']}
        for label, directory in [('locked-mapping-dependencies', out / 'runtime'), ('locked-conda-manager', out / 'conda-manager')]:
            command = [manager, 'install', '--locked', '--manifest-path', directory / 'pixi.toml']
            if not args.allow_download:
                command.append('--offline')
            run(label, command, env, out)
        prefix = out / 'runtime/.pixi/envs/default'
        conda_prefix = out / 'conda-manager/.pixi/envs/default'
        (prefix / 'condabin').mkdir(exist_ok=True)
        if (prefix / 'bin/conda').exists() or (prefix / 'bin/conda').is_symlink():
            raise ValueError('NEW original R/Python prefix unexpectedly already contains bin/conda')
        (prefix / 'bin/conda').symlink_to(conda_prefix / 'bin/conda')
        env.update(PATH=str(prefix / 'bin') + os.pathsep + env.get('PATH', ''),
                   R_LIBS_USER=str(out / 'runtime/library'), R_LIBS_SITE=str(prefix / 'lib/R/library'),
                   RETICULATE_PYTHON=str(prefix / 'bin/python'), RETICULATE_CONDA=str(conda_prefix / 'bin/conda'),
                   RETICULATE_AUTOCONFIGURE='FALSE', RETICULATE_USE_MANAGED_VENV='no',
                   CONDA_NO_PLUGINS='true', CONDA_SOLVER='classic', MAKEFLAGS='-j2')
        metadata_path = prefix / 'share/bioconductor-data-packages/dataURLs.json'
        declared = load(metadata_path)
        shutil.copyfile(metadata_path, out / 'bioconductor-dataURLs.json')
        for entry in profile['annotation_packages']:
            metadata = declared[entry['name']]
            if metadata != dict(urls=entry['urls'], md5=entry['publisher_md5'], fn=entry['filename']):
                raise ValueError('Locked annotation package publisher metadata differs: ' + entry['name'])
            if hashlib.md5(archives[entry['name']].read_bytes()).hexdigest() != entry['publisher_md5']:
                raise ValueError('Original annotation archive publisher MD5 differs')
            run(entry['name'], [prefix / 'bin/R', 'CMD', 'INSTALL', '--no-test-load',
                '--library=' + str(prefix / 'lib/R/library'), archives[entry['name']]], env, out)
        for entry in profile['source_packages']:
            build = pristine(entry, archives[entry['name']])
            run(entry['name'], [prefix / 'bin/R', 'CMD', 'INSTALL', '--no-test-load',
                '--library=' + str(out / 'runtime/library'), build], env, out)
        source_tree(out / 'runtime/source-original/scrattch.mapping')
        for entry in profile['source_packages']:
            found = inventory(out / 'runtime/source-original' / entry['name'], links=False)
            original = {name: {key: value[key] for key in ['bytes', 'sha256']} for name, value in found.items()}
            if original != entry['original_members']:
                raise ValueError('Pristine original source changed during compilation: ' + entry['name'])
        for entry in profile['source_packages'] + profile['annotation_packages']:
            if pin(archives[entry['name']]) != entry['archive']:
                raise ValueError('Retained original archive changed during installation: ' + entry['name'])
        if sources() != runner_before or any(pin(HERE / name) != identity for name, identity in installer_before.items()):
            raise ValueError('Installer/adapter sources changed during installation; qualify NEW paths')
        run('audit-original-runtime', [sys.executable, '-B', HERE / 'adopt_runtime.py', '--prefix', out / 'runtime',
            '--manager-workspace', out / 'conda-manager', '--source', out / 'runtime/source-original/scrattch.mapping',
            '--out', out / 'provider'], env, out)
        (out / 'WARNINGS.md').write_text('# Original private runtime installed; workflow qualification remains separate\n\n'
            + ACTION + '\n', encoding='utf-8')
        save(out / 'COMPLETED.json', dict(status='PASS_INSTALLED_AND_AUDITED_ORIGINAL_PRIVATE_RUNTIME_NOT_ANALYSIS',
             provider=str(out / 'provider'), provider_identity=pin(out / 'provider/READY.json'), calls=calls,
             installer_sources=installer_before, runner_sources=runner_before, analysis_executed=False,
             private_runtime_payloads_redistributed=False, new_family_credit=0, whole_goal_complete=False))
        print('PASS_INSTALLED_AND_AUDITED_ORIGINAL_PRIVATE_RUNTIME_NOT_ANALYSIS', flush=True)
        return 0
    except BaseException as error:
        code = 3 if isinstance(error, OSError) and not isinstance(error, FileExistsError) else 2
        label = ('SKIP3_ORIGINAL_INSTALLATION_UNAVAILABLE' if code == 3 else 'FAIL2_ORIGINAL_INSTALLATION_UNADMITTED') + ': ' + repr(error)
        print(label + '\n' + ACTION, flush=True)
        if owned:
            try:
                save(out / 'FAILURE.json', dict(status=label, cause=repr(error), traceback=traceback.format_exc(),
                     calls=calls, analysis_executed=False, result_admitted=False, required_action=ACTION))
                (out / 'WARNINGS.md').write_text('# ' + label + '\n\n' + ACTION + '\n', encoding='utf-8')
            except BaseException as recording:
                code = 2
                print('FAIL2_INSTALLATION_RECORDING_UNAVAILABLE', repr(recording), ACTION, flush=True)
        return code
    finally:
        stop_child()


if __name__ == '__main__':
    raise SystemExit(main())
