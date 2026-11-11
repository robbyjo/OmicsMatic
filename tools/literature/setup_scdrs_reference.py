# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare an isolated historical-scDRS reference; setup alone admits no analysis."""
import argparse
import json
import os
import re
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from intake_scdrs_reference import VERSIONS

BASE = ROOT / 'build/literature-review/scdrs-runtime'
REQUIREMENTS = '''numpy==1.23.5
scipy==1.10.1
pandas==1.5.3
scanpy==1.9.3
anndata==0.8.0
scikit-misc==0.3.1
statsmodels==0.13.5
numba==0.57.1
llvmlite==0.40.1
h5py==3.9.0
matplotlib==3.7.3
scikit-learn==1.3.2
umap-learn==0.5.4
pynndescent==0.5.10
fire==0.5.0
pytest==7.4.4
'''
ACTION = ('SKIP dependent scDRS analysis until this private Python/source profile is available. '
          'Read the named retained installer log, correct the permission, network or dependency cause '
          'where permitted, and retry using NEW output and Linux private paths. Do not modify shared '
          'packages or silently replace historical scDRS versions with a newer release. '
          'Setup success alone grants no catalog credit or scientific result.')


def linux(path):
    value = path.resolve().as_posix()
    assert value[1:3] == ':/'
    return '/mnt/' + value[0].lower() + value[2:]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--micromamba', required=True, type=Path)
    p.add_argument('--linux-private-root', required=True)
    p.add_argument('--wsl-distribution', default='JLinAlg-Omics-SAIGE-20261026')
    p.add_argument('--allow-download', action='store_true')
    p.add_argument('--allow-install', action='store_true')
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    assert re.fullmatch(r'/var/tmp/omicsmatic-scdrs-[a-z0-9-]+', a.linux_private_root)
    out.mkdir(parents=True)
    (out / 'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='SKIP3_SCDRS_REFERENCE_UNAVAILABLE', analysis_admitted=False,
                 new_catalog_credit=False, required_action=ACTION, shared_environment_modified=False)
    save(out / 'STARTED.json', state)
    prefix = ['wsl.exe', '-d', a.wsl_distribution, '--']

    def run(label, args, timeout=1800):
        save(out / (label + '.command.json'), dict(command=args))
        with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
            result = subprocess.run(args, cwd=ROOT, env=dict(os.environ), stdin=subprocess.DEVNULL,
                                    stdout=stdout, stderr=stderr, timeout=timeout)
        save(out / (label + '.process.json'), dict(exit_code=result.returncode))
        assert result.returncode == 0, label + ' failed; read retained stdout/stderr'

    try:
        assert a.allow_download and a.allow_install, 'Explicit download/install switches required'
        intake = BASE / 'source-intake-1'
        record = json.loads((intake / 'COMPLETED.json').read_bytes())
        for version, commit in VERSIONS.items():
            profile = record['profiles'][version]
            assert profile['commit'] == commit
            for name, wanted in profile['files'].items():
                assert pin(intake / version / 'source' / name) == wanted, (version, name)
        binary = out / 'micromamba'
        binary.write_bytes(a.micromamba.resolve(strict=True).read_bytes())
        run('chmod', prefix + ['/bin/chmod', '700', linux(binary)], 60)
        run('new-native-root', prefix + ['/bin/mkdir', '-m', '700', a.linux_private_root], 60)
        environment = a.linux_private_root + '/environment'
        clean = prefix + ['/usr/bin/env', '-i', 'PATH=/usr/bin:/bin', 'HOME=' + a.linux_private_root,
                          'LC_ALL=C.UTF-8', 'MAMBA_ROOT_PREFIX=' + a.linux_private_root + '/mamba-root',
                          'OPENBLAS_NUM_THREADS=1', 'OMP_NUM_THREADS=1', 'NUMBA_NUM_THREADS=1',
                          'PYTHONNOUSERSITE=1']
        run('python-install', clean + [linux(binary), '--no-rc', 'create', '--yes', '--prefix', environment,
            '--override-channels', '-c', 'conda-forge', '--strict-channel-priority',
            'python=3.10.16', 'pip=24.3.1', '--json'])
        python = environment + '/bin/python'
        requirements = out / 'requirements.txt'
        requirements.write_text(REQUIREMENTS, encoding='utf-8', newline='\n')
        wheels = a.linux_private_root + '/wheelhouse'
        run('dependency-download', clean + [python, '-m', 'pip', 'wheel', '--wheel-dir', wheels,
            '--requirement', linux(requirements)])
        run('dependency-install', clean + [python, '-m', 'pip', 'install', '--no-index', '--find-links',
            wheels, '--requirement', linux(requirements)])
        run('dependency-freeze', clean + [python, '-m', 'pip', 'freeze', '--all'], 60)
        probe = ('import sys,json,numpy,scipy,pandas,scanpy,anndata,skmisc,statsmodels;'
                 'print(json.dumps(dict(python=sys.version,numpy=numpy.__version__,scipy=scipy.__version__,'
                 'pandas=pandas.__version__,scanpy=scanpy.__version__,anndata=anndata.__version__,'
                 'skmisc=skmisc.__version__,statsmodels=statsmodels.__version__)))')
        run('dependencies-import', clean + [python, '-c', probe], 120)
        for version in VERSIONS:
            source = linux(intake / version / 'source')
            run('source-import-' + version, clean + ['PYTHONPATH=' + source, python, '-c',
                'import scdrs; print(scdrs.__version__); print(scdrs.__file__)'], 120)
        versions = json.loads((out / 'dependencies-import.stdout').read_bytes())
        assert versions['python'].startswith('3.10.16')
        state.update(status='PASS_THREE_ORIGINAL_SCDRS_SOURCE_IMPORTS_SETUP_ONLY',
            python=python, distribution=a.wsl_distribution, linux_private_root=a.linux_private_root,
            versions=versions, source_inventory=record['profiles'],
            dependency_freeze=pin(out / 'dependency-freeze.stdout'), helper_pin=pin(binary),
            installer_pin=pin(Path(__file__)), clean_environment=clean)
    except Exception as error:
        state['error'] = repr(error)
    save(out / 'COMPLETED.json', state)
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('error', 'Setup only; no analysis run.')
        + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf-8', newline='\n')
    print(state['status'] + (' ' + state['error'] if 'error' in state else ''))
    return 0 if state['status'].startswith('PASS_') else 3


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable runtime/source integrity assertions')
    raise SystemExit(main())
