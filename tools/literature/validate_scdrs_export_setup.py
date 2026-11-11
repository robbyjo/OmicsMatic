# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check exported setup assets, offline runtime admission and denied/missing archive behavior."""
import argparse
import json
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--build', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_SCDRS_EXPORTED_SETUP_CONTROLS', new_catalog_credit=False)

    def run(label, command, expected=0):
        result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=600)
        (out / (label + '.stdout')).write_bytes(result.stdout)
        (out / (label + '.stderr')).write_bytes(result.stderr)
        save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, result.stderr[-2000:])

    try:
        build = json.loads((a.build / 'COMPLETED.json').read_bytes())
        artifact = ROOT / build['artifact']['path']
        assert pin(artifact) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        export = out / 'exported-helper'
        run('export', ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe', '-jar', str(artifact), '--export-runtime-helper', str(export)])
        resource = ROOT / 'src/main/resources/org/jlinalg/omics/scdrs'
        lock = json.loads((resource / 'scdrs-workflow-lock.json').read_bytes())
        expected = ['prepare_scdrs_runtime.py', 'scdrs-workflow-lock.json', 'scdrs-1.0.2-LICENSE.txt',
                    'numpy-1.23.5-LICENSE.txt', 'statsmodels-0.13.5-LICENSE.txt']
        expected += [w['filename'] for w in lock['wheels'] if w['delivery'] == 'bundled']
        assert set(p.name for p in export.iterdir()) == set(expected) | {'WARNINGS.md'}
        for name in expected: assert pin(export / name) == pin(resource / name), name
        profile = out / 'offline-existing-profile'
        common = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/python3',
            linux(export / 'prepare_scdrs_runtime.py'), '--version', '1.0.2', '--existing-environment',
            '/var/tmp/omicsmatic-scdrs-reference-2/environment']
        run('offline-exported-setup', common + ['--out', linux(profile), '--archive-dir', linux(BASE / 'workflow-lock-1/archives')])
        receipt = json.loads((profile / 'setup.json').read_bytes())
        assert receipt['status'] == 'PASS_PRIVATE_HISTORICAL_SCDRS_RUNTIME_PROFILE_ONLY' and not receipt['analysis_executed']
        empty = out / 'empty-archive-directory'
        empty.mkdir()
        missing = out / 'download-not-permitted'
        run('missing-archive-no-download', common + ['--out', linux(missing), '--archive-dir', linux(empty)], 3)
        failure = json.loads((missing / 'setup.json').read_bytes())
        assert failure['status'] == 'SKIP3_SCDRS_RUNTIME_UNAVAILABLE' and 'downloads not authorized' in failure['cause'] and not (missing / 'runtime.json').exists()
        corrupt_archives = out / 'corrupt-archive-directory'
        corrupt_archives.mkdir()
        (corrupt_archives / lock['sources']['1.0.2']['filename']).write_bytes(b'not the pinned original source archive\n')
        corrupt = out / 'corrupt-archive-refusal'
        run('corrupt-archive', common + ['--out', linux(corrupt), '--archive-dir', linux(corrupt_archives)], 2)
        failure = json.loads((corrupt / 'setup.json').read_bytes())
        assert failure['status'] == 'FAIL2_SCDRS_RUNTIME_PREPARATION' and 'Local archive integrity differs' in failure['cause'] and not (corrupt / 'runtime.json').exists()
        profile_object = json.loads((profile / 'runtime.json').read_bytes())
        assert profile_object['source_lock'] == pin(resource / 'scdrs-workflow-lock.json') and profile_object['preparer'] == pin(resource / 'prepare_scdrs_runtime.py')
        state.update(status='PASS_EXPORTED_SCDRS_ASSETS_OFFLINE_PROFILE_AND_PREREQUISITE_INTEGRITY_REFUSALS_ONLY',
            exported_assets={name: pin(export / name) for name in expected}, offline_profile=pin(profile / 'runtime.json'),
            no_implicit_download=True, corrupted_archive_refused=True, scientific_analysis_executed=False,
            existing_runtime_packages_modified=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable exported setup/source identity checks')
    raise SystemExit(main())
