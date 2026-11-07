# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Permitted Linux-only private MACS2 builds; never edit existing runtimes or source."""
import argparse
import hashlib
import json
import os
import platform
import signal
import subprocess
import tarfile
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

ACTION = ('Read WARNINGS.md, setup logs, DOWNLOAD-PROGRESS.json and package identities. '
          'This is private synthetic reference setup, not user-analysis admission. '
          'If installation/download/execution is forbidden or a required compatible runtime is unavailable, '
          'retain SKIP3 and skip dependent analysis. Never modify existing homes/libraries or upstream source. '
          'Preserve partial attempts and retry into NEW output paths after correcting the named cause.')


class Unavailable(Exception):
    pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--python-home', type=Path)
    parser.add_argument('--archive-dir', type=Path)
    out_args = parser.parse_args()
    out = out_args.out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    downloads = []

    def fetch(url, name, maximum, expected=None):
        cached = out_args.archive_dir / name if out_args.archive_dir else None
        if cached is not None and expected is not None and cached.is_file():
            raw = cached.read_bytes()
            assert len(raw) <= maximum
            receipt = dict(url=url, cached_from=str(cached.resolve()), bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
        else:
            request = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-validation/1.0'})
            with urllib.request.urlopen(request, timeout=90) as response:
                raw = response.read(maximum + 1)
                assert len(raw) <= maximum
                receipt = dict(url=url, final_url=response.geturl(), bytes=len(raw),
                               sha256=hashlib.sha256(raw).hexdigest())
        assert expected is None or receipt['sha256'] == expected, 'Published source/wheel hash differs'
        target = out / 'downloads' / name
        target.write_bytes(raw)
        downloads.append(dict(path=target.relative_to(out).as_posix(), **receipt,
                              published_SHA256_checked=expected is not None))
        save(out / 'DOWNLOAD-PROGRESS.json', downloads)
        return target

    env = {k: v for k, v in os.environ.items() if not k.startswith(('PYTHON', 'PIP_', 'CONDA_', 'VIRTUAL_ENV', 'LC_'))}
    env.update(PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1', PIP_CONFIG_FILE='/dev/null',
               PIP_NO_INDEX='1', PIP_DISABLE_PIP_VERSION_CHECK='1', PIP_NO_CACHE_DIR='1',
               OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', LANG='C', LC_ALL='C',
               CC='/usr/bin/gcc', CXX='/usr/bin/g++', LDSHARED='/usr/bin/gcc -shared')

    def command(name, argv, cwd=None, cap=900):
        argv = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            result = subprocess.Popen(argv, cwd=cwd or ROOT, env=env, stdin=subprocess.DEVNULL,
                                      stdout=stdout, stderr=stderr, start_new_session=True)
            timed_out = False
            try:
                result.wait(timeout=cap)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(result.pid, signal.SIGTERM)
                try:
                    result.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(result.pid, signal.SIGKILL)
                    result.wait(timeout=10)
        save(out / (name + '.process.json'), dict(command=argv, exit_code=result.returncode))
        if timed_out:
            raise Unavailable('Original setup command timed out; owned process-group cleanup requested: ' + name)
        if result.returncode:
            raise Unavailable('Original setup command failed: ' + name + '; inspect retained stdout/stderr')

    try:
        if not out_args.allow_install or not out_args.allow_download:
            raise Unavailable('Explicit --allow-install and --allow-download are required; analysis skipped')
        if platform.system() != 'Linux' or platform.machine() != 'x86_64':
            raise Unavailable('This reference build requires permitted Linux x86_64 execution')
        (out / 'downloads').mkdir()
        intake = base / 'peaks-source-intake-1'
        original = json.loads((intake / 'COMPLETED.json').read_bytes())
        for edition in original['MACS2']:
            archive = intake / ('MACS2-' + edition['version'] + '.tar.gz')
            assert pin(archive) == edition['archive']
        release = json.loads((intake / 'python-release.json').read_bytes())
        assets = [a for a in release['assets'] if a['name'].startswith('cpython-3.9.') and
                  a['name'].endswith('-x86_64-unknown-linux-gnu-install_only.tar.gz')]
        assert len(assets) == 1
        asset = assets[0]
        checksum = fetch(asset['browser_download_url'] + '.sha256', asset['name'] + '.sha256', 10000)
        digest = checksum.read_text().split()[0]
        assert len(digest) == 64
        archive = fetch(asset['browser_download_url'], asset['name'], 100_000_000, digest)
        with tarfile.open(archive, 'r:gz') as package:
            members = package.getmembers()
            assert len(members) < 50000 and sum(m.size for m in members) < 500_000_000
            for member in members:
                assert member.name.startswith('python/') and not member.isdev() and not member.isfifo()
            # The Windows-backed workspace is case-insensitive. CPython's
            # terminal database has case-only aliases that become symlink loops
            # there. MACS never uses it; retain its complete source archive and
            # explicitly omit only this terminal database from the private home.
            omitted = [m.name for m in members if m.name == 'python/share/terminfo' or m.name.startswith('python/share/terminfo/')]
            selected = [m for m in members if m.name not in set(omitted)]
            save(out / 'PYTHON-EXTRACTION.json', dict(archive=pin(archive), omitted_members=omitted,
                 reason='Case-only terminfo aliases form symlink loops on Windows-backed storage; noninteractive MACS does not use the terminal database.', Python_code_or_native_libraries_modified=False))
            if out_args.python_home is None:
                package.extractall(out, members=selected, filter='data')
        if out_args.python_home is not None:
            home = out_args.python_home.resolve()
            assert home.is_relative_to(base) and home.is_dir()
            previous = json.loads((home.parent / 'PYTHON-EXTRACTION.json').read_bytes())
            assert previous['archive'] == pin(archive) and previous['omitted_members'] == omitted
            save(out / 'REUSED-PYTHON.json', dict(path=str(home), prior_extraction=pin(home.parent / 'PYTHON-EXTRACTION.json'), archive=pin(archive), modified=False))
            python = home / 'bin/python3'
        else:
            python = out / 'python/bin/python3'
        command('private-python-version', [python, '--version'])
        command('compiler-version', ['/usr/bin/gcc', '--version'])
        packages = [('pip', '21.3.1'), ('setuptools', '57.5.0'), ('wheel', '0.37.1'),
                    ('Cython', '0.29.37'), ('numpy', '1.24.4')]
        wheels = []
        for name, version in packages:
            metadata = fetch('https://pypi.org/pypi/' + name + '/' + version + '/json',
                             name + '-' + version + '-pypi.json', 4_000_000)
            value = json.loads(metadata.read_bytes())
            compatible = [u for u in value['urls'] if 'cp39-cp39-manylinux_2_17_x86_64' in u['filename'] and u['filename'].endswith('.whl')]
            if not compatible:
                compatible = [u for u in value['urls'] if u['filename'].endswith('py3-none-any.whl')]
            assert len(compatible) == 1, (name, [u['filename'] for u in compatible])
            wheel = compatible[0]
            wheels.append(fetch(wheel['url'], wheel['filename'], 50_000_000, wheel['digests']['sha256']))
        env['PIP_FIND_LINKS'] = str(out / 'downloads')
        built = []
        for edition in original['MACS2']:
            version = edition['version']
            home = out / version
            command(version + '-venv', [python, '-m', 'venv', '--symlinks', home])
            executable = home / 'bin/python'
            command(version + '-dependencies', [executable, '-m', 'pip', 'install', '--no-index',
                    '--no-deps', *wheels])
            command(version + '-build', [executable, '-m', 'pip', 'install', '--no-index',
                    '--no-build-isolation', '--no-deps', intake / ('MACS2-' + version + '.tar.gz')])
            command(version + '-version', [home / 'bin/macs2', '--version'])
            assert version in (out / (version + '-version.stdout')).read_text()
            command(version + '-help', [home / 'bin/macs2', 'callpeak', '--help'])
            command(version + '-package-identities', [executable, '-c',
                    'import importlib.metadata as m,json,sys;print(json.dumps(dict(python=sys.version,packages={p:m.version(p) for p in ["MACS2","numpy","Cython","pip","setuptools","wheel"]}),indent=2))'])
            identities = json.loads((out / (version + '-package-identities.stdout')).read_bytes())
            assert identities['packages']['MACS2'] == version
            assert all(identities['packages'][name] == v for name, v in packages)
            built.append(dict(version=version, home=home.relative_to(ROOT).as_posix(),
                              source=edition['archive'], identities=identities,
                              executable=pin(home / 'bin/macs2')))
            save(out / 'BUILD-PROGRESS.json', built)
        save(out / 'COMPLETED.json', dict(status='installed-three-original-MACS2-private-reference-runtimes-not-analysis',
                                         runtimes=built, downloads=downloads, upstream_source_modified=False,
                                         Python_39_reason='Original2.2.7.1 setup float(sys.version[:3]) misreads3.11 as3.1; Python3.9 executes unchanged setup.',
                                         historical_whole_environment_reproduced=False, user_analysis_admitted=False,
                                         source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))}))
        (out / 'WARNINGS.md').write_text('# Private reference runtimes installed; no user analysis admitted\n\n' + ACTION + '\n', encoding='utf8')
        print('Built3 unchanged original MACS2 editions in separate private Python3.9 environments', flush=True)
        return 0
    except Unavailable as error:
        save(out / 'SKIP3.json', dict(status='SKIP3_MACS2_REFERENCE_UNAVAILABLE', error=str(error), required_action=ACTION))
        (out / 'WARNINGS.md').write_text('# SKIP3: DEPENDENT PEAK ANALYSIS SKIPPED\n\n' + str(error) + '\n\n' + ACTION + '\n', encoding='utf8')
        print('SKIP3: ' + str(error) + '\n' + ACTION, flush=True)
        return 3
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_MACS2_SETUP_INTEGRITY', error=str(error), required_action=ACTION))
        (out / 'WARNINGS.md').write_text('# FAIL2: NO RUNTIME OR ANALYSIS ADMITTED\n\n' + str(error) + '\n\n' + ACTION + '\n', encoding='utf8')
        raise


if __name__ == '__main__':
    raise SystemExit(main())
