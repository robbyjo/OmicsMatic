# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare five original Sniffles editions in a NEW private Linux workspace.

Requires explicit download/install authorization. No system package manager,
shared Python library, registry, or existing source tree is modified. Runtime
preparation and --version probes do not admit an SV analysis or catalog credit.
"""
import argparse
import hashlib
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import traceback
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTION = ('Dependent Sniffles analysis is SKIPPED until its exact edition and dependencies are available. '
          'Where downloads and installation are permitted, fix the named error in the retained logs and '
          'rerun this setup with --allow-download --allow-install and a NEW --out directory. '
          'Otherwise provide a permitted runtime; do not run dependent analysis. '
          'Preserve this attempt, original archives and all input data.')
PACKAGES = ['setuptools==80.9.0', 'wheel==0.45.1', 'cmake==3.31.6',
            'pysam==0.23.3', 'edlib==1.3.9.post1', 'psutil==7.1.0']


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8', newline='\n')


def inventory(root):
    result = {}
    for path in sorted(root.rglob('*')):
        if '__pycache__' in path.parts:
            continue
        name = path.relative_to(root).as_posix()
        if path.is_symlink():
            target = path.resolve(strict=True)
            if target.is_dir():
                # Python venv creates lib64 -> lib. Record this opaque alias;
                # never recurse through it, and require its target inside the
                # inventoried root whose complete regular population is read.
                assert target.is_relative_to(root.resolve()), 'External directory link: ' + str(path)
                result[name] = dict(kind='contained-directory-link', link=str(path.readlink()), resolved=str(target))
            else:
                assert target.is_file(), 'Nonregular link target: ' + str(path)
                result[name] = dict(link=str(path.readlink()), resolved=str(target), **pin(target))
        elif path.is_file():
            result[name] = pin(path)
        else:
            assert path.is_dir(), 'Nonregular runtime node: ' + str(path)
    return result


def members(group):
    result = []
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():
            continue
        try:
            raw = (path / 'stat').read_text()
            fields = raw[raw.rindex(')') + 2:].split()
            if int(fields[2]) == group:
                result.append(dict(pid=int(path.name), state=fields[0], start_ticks=int(fields[19])))
        except (FileNotFoundError, ProcessLookupError):
            pass
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--intake', type=Path, required=True)
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--allow-install', action='store_true')
    args = parser.parse_args()
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    (out / 'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='SKIP3_SNIFFLES_REFERENCE_UNAVAILABLE', required_action=ACTION,
                 analysis_admitted=False, implementation_credit=False, whole_goal_complete=False,
                 processes=[], source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))})
    shared = None
    temporary = out / 'private-temp'
    temporary.mkdir()
    env = {key: value for key, value in os.environ.items()
           if not key.startswith(('PYTHON', 'PIP_', 'LD_', 'CMAKE_')) and key not in
           ['VIRTUAL_ENV', 'CC', 'CXX', 'CFLAGS', 'CXXFLAGS', 'LDFLAGS', 'CPATH', 'LIBRARY_PATH']}
    env.update(LANG='C.UTF-8', LC_ALL='C.UTF-8', TMPDIR=str(temporary),
               PYTHONDONTWRITEBYTECODE='1', PIP_CONFIG_FILE='/dev/null',
               PIP_CACHE_DIR=str(out / 'private-cache'), PIP_DISABLE_PIP_VERSION_CHECK='1',
               PIP_NO_INPUT='1', OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', MKL_NUM_THREADS='1')

    def execute(name, command, timeout=300):
        command = list(map(str, command))
        record = dict(command=command, status='RUNNING_PROCESS_ONLY', started_at=time.time())
        save(out / (name + '.STARTED.json'), record)
        child, error, code = None, None, None
        started = time.monotonic()
        try:
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                child = subprocess.Popen(command, cwd=out, env=env, stdin=subprocess.DEVNULL,
                                         stdout=stdout, stderr=stderr, start_new_session=True)
                record['owned_process_group'] = child.pid
                while child.poll() is None:
                    if time.monotonic() - started > timeout:
                        raise TimeoutError(name + ' exceeded setup timeout')
                    time.sleep(.1)
                code = child.returncode
                assert code == 0, name + ': inspect retained stdout/stderr (exit ' + str(code) + ')'
            record['status'] = 'completed-process-only'
        except Exception as failure:
            error = failure
            record.update(status='SKIP3_REFERENCE_PROCESS_UNAVAILABLE', error=repr(failure))
        finally:
            if child is not None:
                record['owned_before_cleanup'] = members(child.pid)
                for sig, seconds in [(signal.SIGTERM, 2), (signal.SIGKILL, 3)]:
                    if not any(r['state'] != 'Z' for r in members(child.pid)):
                        break
                    try:
                        os.killpg(child.pid, sig)
                    except ProcessLookupError:
                        pass
                    deadline = time.monotonic() + seconds
                    while any(r['state'] != 'Z' for r in members(child.pid)) and time.monotonic() < deadline:
                        time.sleep(.05)
                child.wait(timeout=10)
                record['owned_after_cleanup'] = members(child.pid)
                live = [r for r in record['owned_after_cleanup'] if r['state'] != 'Z']
                record['owned_live_after_cleanup'] = live
                if live:
                    error = RuntimeError('Owned setup workers remain alive; inspect cleanup receipt')
                    record.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE', error=repr(error))
            record.update(exit_code=code, elapsed_seconds=time.monotonic() - started)
            save(out / (name + '.process.json'), record)
            state['processes'].append(record)
        if error:
            raise error

    try:
        assert args.allow_download and args.allow_install, 'Download/install permission absent; no runtime preparation performed'
        assert sys.platform == 'linux' and sys.version_info[:2] == (3, 12)
        intake = args.intake.resolve()
        source = json.loads((intake / 'COMPLETED.json').read_bytes())
        assert source['status'] == 'passed-five-exact-Sniffles-source-editions-only'
        assert len(source['editions']) == 5
        for tag, record in source['editions'].items():
            root = intake / tag / 'source'
            assert inventory(root) == record['members'], 'Exact source population changed: ' + tag
            assert pin(ROOT / record['archive']['path']) == {k: record['archive'][k] for k in ['bytes', 'sha256']}
        shared = dict(python=pin(Path(sys.executable).resolve()), standard_library=inventory(Path('/usr/lib/python3.12')))
        save(out / 'shared-before.json', shared)
        prefix = out / 'venv'
        execute('venv', [sys.executable, '-I', '-B', '-m', 'venv', '--without-pip', '--copies', prefix], 120)
        python = prefix / 'bin/python'
        bootstrap = ROOT / 'build/literature-review/hail-runtime/pip-bootstrap-intake-1'
        receipt = json.loads((bootstrap / 'COMPLETED.json').read_bytes())
        wheel = bootstrap / 'pip-24.3.1-py3-none-any.whl'
        assert receipt['status'] == 'PASS_EXACT_OFFICIAL_PIP_BOOTSTRAP_WHEEL_CAPTURE_ONLY'
        assert pin(wheel) == receipt['archive']
        bridge = out / 'bootstrap-pip.py'
        bridge.write_text('import runpy,sys\nwheel=sys.argv.pop(1)\nsys.path.insert(0,wheel)\nrunpy.run_module("pip",run_name="__main__")\n', encoding='utf8', newline='\n')
        execute('pip-bootstrap', [python, '-I', '-B', bridge, wheel, 'install', '--no-index', '--no-compile', wheel])
        archives = out / 'archives'
        archives.mkdir()
        base = [python, '-I', '-B', '-m', 'pip', '--isolated']
        execute('download', [*base, 'download', '--index-url', 'https://pypi.org/simple', '--only-binary', ':all:',
                             '--dest', archives, *PACKAGES], 600)
        # Check every downloaded artifact against its official PyPI release digest.
        downloads = {}
        for specification in PACKAGES:
            name, version = specification.split('==')
            url = 'https://pypi.org/pypi/' + name + '/' + version + '/json'
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-audit'}), timeout=60) as response:
                raw = response.read(16777217)
                assert len(raw) <= 16777216
            (out / (name + '-PyPI.json')).write_bytes(raw)
            metadata = json.loads(raw)
            assert metadata['info']['version'] == version
            matches = [r for r in metadata['urls'] if (archives / r['filename']).is_file()]
            assert len(matches) == 1
            row = matches[0]
            actual = pin(archives / row['filename'])
            assert actual == dict(bytes=row['size'], sha256=row['digests']['sha256'])
            downloads[row['filename']] = dict(package=name, version=version, url=row['url'], **actual)
        assert {p.name for p in archives.iterdir()} == set(downloads)
        save(out / 'downloaded-archives.json', downloads)
        execute('install', [*base, 'install', '--no-index', '--find-links', archives, '--no-compile',
                            '--report', out / 'pip-report.json', *PACKAGES])
        execute('pip-check', [*base, 'check'])
        execute('freeze', [*base, 'freeze', '--all', '--local'])
        source_copies = out / 'sources'
        source_copies.mkdir()
        for tag, record in source['editions'].items():
            shutil.copytree(intake / tag / 'source', source_copies / tag)
            assert inventory(source_copies / tag) == record['members']
        # CMake writes generated headers inside its source tree: only a private copy is configured.
        legacy = source_copies / 'v1.0.7'
        env['PATH'] = str(prefix / 'bin') + ':/usr/bin:/bin'
        # BamTools 2.3.0 predates the C++17 const-comparator requirement. Select
        # the C++11 language mode explicitly; do not patch its scientific source.
        execute('configure-v107', [prefix / 'bin/cmake', '-S', legacy, '-B', out / 'legacy-build',
                                  '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_CXX_STANDARD=11', '-DCMAKE_CXX_STANDARD_REQUIRED=ON'])
        execute('compile-v107', [prefix / 'bin/cmake', '--build', out / 'legacy-build', '--target', 'sniffles', '--parallel', '2'], 900)
        binary = legacy / 'bin/sniffles-core-1.0.7/sniffles'
        assert binary.is_file()
        execute('version-v107', [binary, '--version'])
        launcher = out / 'launch-source.py'
        launcher.write_text('import runpy,sys\nfrom pathlib import Path\nsource=Path(sys.argv.pop(1)).resolve()\nsys.path.insert(0,str(source/"src"))\nrunpy.run_path(str(source/"src/sniffles/sniffles"),run_name="__main__")\n', encoding='utf8', newline='\n')
        probes = {}
        for tag in source['editions']:
            root = source_copies / tag
            if tag == 'v1.0.7':
                # Original inputs stay byte-identical; CMake adds generated/build files.
                assert all(pin(root / n) == value for n, value in source['editions'][tag]['members'].items())
                command = [binary]
                label = 'version-v107'
            else:
                assert inventory(root) == source['editions'][tag]['members']
                command = [python, '-I', '-B', launcher, root]
                label = 'version-' + tag.replace('.', '')
                execute(label, [*command, '--version'])
            text = (out / (label + '.stdout')).read_text() + (out / (label + '.stderr')).read_text()
            assert tag[1:] in text, (tag, text)
            probes[tag] = dict(command=list(map(str, command)), version_stdout=pin(out / (label + '.stdout')),
                               version_stderr=pin(out / (label + '.stderr')),
                               commit=source['editions'][tag]['commit'])
        runtime = inventory(prefix)
        save(out / 'RUNTIME-FILES.json', runtime)
        save(out / 'SOURCE-COPY-FILES.json', inventory(source_copies))
        state.update(status='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY',
                     python=str(python), python_version=sys.version, packages=PACKAGES, probes=probes,
                     source_intake=str(intake), runtime_files=pin(out / 'RUNTIME-FILES.json'),
                     source_copy_files=pin(out / 'SOURCE-COPY-FILES.json'), legacy_binary=pin(binary),
                     source_patched=False, legacy_cpp_language_standard=11, system_packages_installed=False,
                     original_SV_calling_controls_pending=True)
    except Exception as failure:
        state['error'] = repr(failure)
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
        print('SKIP3: DEPENDENT SNIFFLES ANALYSIS SKIPPED: ' + repr(failure), flush=True)
    finally:
        if shared is not None:
            after = dict(python=pin(Path(sys.executable).resolve()), standard_library=inventory(Path('/usr/lib/python3.12')))
            save(out / 'shared-after.json', after)
            state['preexisting_python_and_standard_library_unchanged'] = after == shared
            if after != shared:
                state.update(status='FAIL2_SHARED_RUNTIME_CHANGED', error='Inspect complete before/after identities')
        save(out / 'COMPLETED.json', state)
        (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' +
            state.get('error', 'Version probes passed. SV calling and the Java adapter remain unvalidated.') +
            '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
    print(state['status'], flush=True)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact-source and shared-runtime checks')
    raise SystemExit(main())
