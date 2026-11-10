# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a private original MTAG1.0.8 Python2 reference; no GWAS analysis or integration credit."""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

BASE = ROOT / 'build/literature-review/mtag-runtime'
COMMIT = '9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc'
ACTION = ('SKIP dependent MTAG analysis until the original Python2.7 runtime and compatible dependencies are available. '
          'Preserve installer logs, correct the named permission/network/package cause where permitted, and retry into a NEW private path. '
          'Do not change shared Python packages, translate the original source to Python3, or silently replace MTAG by another method. '
          'Setup success alone admits no scientific result.')


def linux(path):
    value = path.resolve().as_posix()
    assert value[1:3] == ':/'
    return '/mnt/' + value[0].lower() + value[2:]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--allow-install', action='store_true')
    p.add_argument('--allow-download', action='store_true')
    p.add_argument('--wsl-distribution', default='JLinAlg-Omics-SAIGE-20261026')
    p.add_argument('--micromamba', type=Path, required=True)
    p.add_argument('--dependency-profile', choices=['current-channel-attempt', 'archived-cf202003'], default='archived-cf202003')
    p.add_argument('--linux-private-root', help='NEW /var/tmp/omicsmatic-mtag-... path on the native Linux filesystem; /tmp is ephemeral in this environment')
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='SKIP3_MTAG_REFERENCE_UNAVAILABLE', analysis_admitted=False, new_catalog_credit=False,
                 source_commit=COMMIT, required_action=ACTION, shared_environment_modified=False)
    save(out / 'STARTED.json', state)
    prefix = ['wsl.exe', '-d', a.wsl_distribution, '--']

    def run(label, args, timeout=1800):
        save(out / (label + '.command.json'), dict(command=args))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            process = subprocess.run(args, cwd=ROOT, env=dict(os.environ), stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, timeout=timeout)
        save(out / (label + '.process.json'), dict(exit_code=process.returncode))
        if process.returncode:
            raise RuntimeError(label + ' failed: exit ' + str(process.returncode) + '; read retained stdout/stderr')

    try:
        if not a.allow_install or not a.allow_download:
            raise RuntimeError('Explicit --allow-install and --allow-download are required for this dependency setup')
        source = BASE / 'source-intake-1'
        record = json.loads((source / 'COMPLETED.json').read_bytes())
        assert record['commit'] == COMMIT
        assert pin(source / 'upstream.zip') == record['archive']
        for name, wanted in record['files'].items():
            assert pin(source / 'source' / name) == wanted, name
        binary = out / 'micromamba'
        binary.write_bytes(a.micromamba.resolve(strict=True).read_bytes())
        run('chmod', prefix + ['/bin/chmod', '700', linux(binary)], 60)
        run('host', prefix + ['/usr/bin/uname', '-sm'], 60)
        assert (out / 'host.stdout').read_text().strip() == 'Linux x86_64'
        environment = linux(out / 'environment')
        home, cache = linux(out), linux(out / 'mamba-root')
        if a.linux_private_root:
            assert re.fullmatch(r'/(?:var/)?tmp/omicsmatic-mtag-[a-z0-9-]+', a.linux_private_root)
            # mkdir without -p must fail if the private root already exists.
            run('new-native-linux-root', prefix + ['/bin/mkdir', '-m', '700', a.linux_private_root], 60)
            environment = a.linux_private_root + '/environment'
            home, cache = a.linux_private_root, a.linux_private_root + '/mamba-root'
        clean = prefix + ['/usr/bin/env', '-i', 'PATH=/usr/bin:/bin', 'HOME=' + home, 'LC_ALL=C',
                          'MAMBA_ROOT_PREFIX=' + cache, 'MAMBA_NO_BANNER=1']
        archived = a.dependency_profile == 'archived-cf202003'
        channel = 'conda-forge/label/cf202003' if archived else 'conda-forge'
        python_version, numpy_version = ('2.7.15', '1.16.4') if archived else ('2.7.18', '1.16.6')
        command = clean + [linux(binary), '--no-rc', 'create', '--yes', '--prefix', environment,
                           '--override-channels', '-c', channel, '--strict-channel-priority',
                           'python=' + python_version, 'numpy=' + numpy_version, 'pandas=0.24.2', 'scipy=1.2.1', 'bitarray=0.8.3', 'joblib=0.14.1']
        run('environment-plan', command + ['--dry-run', '--json'])
        run('environment-install', command + ['--json'])
        run('installed-packages', clean + [linux(binary), '--no-rc', 'list', '--prefix', environment, '--json'], 60)
        run('original-help', clean + [environment + '/bin/python', linux(source / 'source/mtag.py'), '--help'], 120)
        probe = "import sys,numpy,pandas,scipy,bitarray,joblib,json;print(json.dumps(dict(python=sys.version,numpy=numpy.__version__,pandas=pandas.__version__,scipy=scipy.__version__,bitarray=bitarray.__version__,joblib=joblib.__version__)))"
        run('versions', clean + [environment + '/bin/python', '-c', probe], 60)
        versions = json.loads((out / 'versions.stdout').read_bytes())
        assert versions['python'].startswith(python_version)
        for name, wanted in [('numpy', numpy_version), ('pandas', '0.24.2'), ('scipy', '1.2.1'), ('bitarray', '0.8.3'), ('joblib', '0.14.1')]:
            assert versions[name] == wanted
        state.update(status='PASS_ORIGINAL_MTAG108_REFERENCE_PREPARED_ONLY', versions=versions, channel=channel, dependency_profile=a.dependency_profile, python=environment + '/bin/python', native_linux_private_root=a.linux_private_root,
                     source_directory=linux(source / 'source'), source_inventory=record['files'], distribution=a.wsl_distribution,
                     installed_packages=json.loads((out / 'installed-packages.stdout').read_bytes()),
                     helper_pin=pin(binary), installer_pin=pin(Path(__file__)))
        print(state['status'])
    except Exception as error:
        state['error'] = repr(error)
        print(state['status'] + ': ' + repr(error) + '\nRequired action: ' + ACTION, file=sys.stderr)
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('error', 'Setup only; no analysis has run.')
        + '\n\nREQUIRED ACTION: ' + ACTION + ('\n\nPrivate reference runtime is on the Linux filesystem. Temporary directories may be cleared by the operating system; preflight the recorded path and recreate it explicitly if unavailable.' if a.linux_private_root else '') + '\n', encoding='utf-8', newline='\n')
    return 0 if state['status'].startswith('PASS_') else 3


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/runtime integrity checks')
    raise SystemExit(main())
