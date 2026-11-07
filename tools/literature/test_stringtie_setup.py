# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual private offline/download/recheck/skip/corruption setup controls; no scientific runtime admission."""
import argparse
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_stringtie_controls import invoke, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--linux-base', type=Path, required=True)
    args = parser.parse_args(); out, area = args.out.resolve(), args.linux_base
    base = ROOT / 'build/literature-review/stringtie-runtime'
    assert platform.system() == 'Linux' and platform.machine() == 'x86_64'
    assert out.is_relative_to(base.resolve()) and not out.exists()
    assert area.is_absolute() and area.parent == Path('/var/tmp') and area.name.startswith('omicsmatic-stringtie-setup-') and not area.exists()
    area.mkdir(mode=0o700); out.mkdir(); installer = ROOT / 'tools/literature/install_stringtie.py'
    kit = ROOT / 'src/main/resources/org/jlinalg/omics/stringtie-runtime-setup'
    assert (kit / 'install_stringtie.py').read_bytes() == installer.read_bytes()
    lock = json.loads((kit / 'stringtie-runtime-lock.json').read_bytes()); records = []; homes = []

    def run(name, argv, expected, timeout=90):
        process = invoke(['/usr/bin/python3', '-I', '-B', str(installer), *map(str, argv)], out, name, timeout)
        assert process['exit_code'] == expected and not process['timed_out'], (name, process)
        records.append(dict(name=name, expected_exit=expected, process=process)); save(out / 'PROGRESS.json', records)
        return process

    def common(profile, home, report, cache, chosen_kit=kit):
        return ['--kit', chosen_kit, '--profile', profile, '--home', home, '--report', report, '--cache', cache]

    def validate(report, home, profile):
        completed = json.loads((report / 'COMPLETED.json').read_bytes()); manifest = json.loads((report / 'manifest.json').read_bytes())
        assert completed['status'] == 'COMPLETE_SETUP_ONLY_NOT_ADMISSION' and completed['manifest'] == pin(report / 'manifest.json')
        assert not completed['scientific_runtime_admitted'] and not completed['user_analysis_admitted'] and not (report / 'INCOMPLETE.json').exists()
        assert set(p.name for p in report.iterdir()) == set(manifest['files']) | {'manifest.json', 'COMPLETED.json'}
        assert all(pin(report / name) == value for name, value in manifest['files'].items())
        assert set(p.name for p in home.iterdir()) == {'bin', 'LICENSE-StringTie.txt', '.omicsmatic-stringtie.json'}
        assert all(pin(home / name) == value for name, value in lock['profiles'][profile]['installed_files'].items())
        assert 'NO RUNTIME OR USER ANALYSIS ADMITTED' in (report / 'WARNINGS.md').read_text()
        return manifest

    for profile, spec in lock['profiles'].items():
        version = spec['version']; home = area / ('home-' + version); report = area / ('install-' + version); cache = area / ('cache-' + version)
        archives = base / 'source-intake-1' / version / 'archives'
        run('offline-' + version, [*common(profile, home, report, cache), '--archive-dir', archives, '--allow-install'], 0)
        first = validate(report, home, profile); assert first['downloads'] == []
        original_payload = {p.relative_to(home).as_posix(): pin(p) for p in home.rglob('*') if p.is_file()}
        recheck = area / ('recheck-' + version)
        run('recheck-' + version, common(profile, home, recheck, cache), 0); second = validate(recheck, home, profile)
        assert second['status'] == 'RECHECKED_EXACT_PAYLOAD_ONLY'
        assert original_payload == {p.relative_to(home).as_posix(): pin(p) for p in home.rglob('*') if p.is_file()}
        homes.append(dict(profile=profile, home=str(home), setup_report=str(report), recheck_report=str(recheck), files=original_payload))
    profile = 'linux-x86_64-stringtie-3.0.3'; home = area / 'forbidden-install-home'; report = area / 'forbidden-install-report'; cache = area / 'forbidden-install-cache'
    run('install-not-permitted', [*common(profile, home, report, cache), '--allow-download'], 3)
    assert not home.exists() and not cache.exists() and 'Installation is not permitted' in (report / 'WARNINGS.md').read_text()
    home = area / 'forbidden-download-home'; report = area / 'forbidden-download-report'; cache = area / 'forbidden-download-cache'
    run('download-not-permitted', [*common(profile, home, report, cache), '--allow-install'], 3)
    assert not home.exists() and 'downloading is not permitted' in (report / 'WARNINGS.md').read_text()
    unmanaged = area / 'unmanaged-home'; unmanaged.mkdir(mode=0o700)
    run('unmanaged-home-preserved', common(profile, unmanaged, area / 'unmanaged-report', area / 'unmanaged-cache'), 2)
    assert list(unmanaged.iterdir()) == []
    existing_report = area / 'existing-report'; existing_report.mkdir(mode=0o700); (existing_report / 'sentinel.txt').write_text('preserve existing report\n')
    sentinel = pin(existing_report / 'sentinel.txt')
    run('existing-report-preserved', common(profile, area / 'existing-report-home', existing_report, area / 'existing-report-cache'), 2)
    assert set(p.name for p in existing_report.iterdir()) == {'sentinel.txt'} and pin(existing_report / 'sentinel.txt') == sentinel
    bad_archives = area / 'corrupt-archives'; bad_archives.mkdir(mode=0o700)
    for asset in lock['profiles'][profile]['assets'].values():
        shutil.copyfile(base / 'source-intake-1/3.0.3/archives' / asset['filename'], bad_archives / asset['filename'])
    binary_archive = bad_archives / lock['profiles'][profile]['assets']['binary']['filename']; raw = bytearray(binary_archive.read_bytes()); raw[0] ^= 1; binary_archive.write_bytes(raw)
    corrupt_home = area / 'corrupt-home'; corrupt_report = area / 'corrupt-report'
    run('corrupt-archive', [*common(profile, corrupt_home, corrupt_report, area / 'corrupt-cache'), '--archive-dir', bad_archives, '--allow-install'], 2)
    assert not corrupt_home.exists() and 'hash/size differs' in (corrupt_report / 'WARNINGS.md').read_text()
    bad_kit = area / 'bad-kit'; shutil.copytree(kit, bad_kit)
    bad_input = bad_kit / 'health-inputs/reads.bam'; raw = bytearray(bad_input.read_bytes()); raw[0] ^= 1; bad_input.write_bytes(raw)
    run('corrupt-kit', common(profile, area / 'bad-kit-home', area / 'bad-kit-report', area / 'bad-kit-cache', bad_kit), 2)
    clone = area / 'corrupt-managed-home'; shutil.copytree(area / 'home-3.0.3', clone)
    binary = clone / 'bin/stringtie'; binary.write_bytes(binary.read_bytes() + b'x'); corrupted = pin(binary)
    run('corrupt-managed-payload-preserved', common(profile, clone, area / 'corrupt-managed-report', area / 'corrupt-managed-cache'), 2)
    assert pin(binary) == corrupted
    denied_command = ['/usr/bin/setpriv', '--reuid', '65534', '--regid', '65534', '--clear-groups', '/usr/bin/python3', '-I', '-B', str(installer),
                      *map(str, common(profile, area / 'uid-denied-home', area / 'uid-denied-report', area / 'uid-denied-cache'))]
    denied = invoke(denied_command, out, 'actual-uid-permission-skip', 30)
    assert denied['exit_code'] == 3 and not denied['timed_out'] and 'PermissionError' in (out / 'actual-uid-permission-skip.stderr').read_text()
    records.append(dict(name='actual-uid-permission-skip', expected_exit=3, process=denied, uid=65534))
    download_home = area / 'download-home'; download_report = area / 'download-report'; download_cache = area / 'download-cache'
    downloaded = invoke(['/usr/bin/python3', '-I', '-B', str(installer), *map(str, common(profile, download_home, download_report, download_cache)), '--allow-install', '--allow-download'], out, 'actual-download', 180)
    assert not downloaded['timed_out'] and downloaded['exit_code'] in (0, 3)
    if downloaded['exit_code'] == 0:
        manifest = validate(download_report, download_home, profile); assert len(manifest['downloads']) == 2; download_status = 'passed-two-exact-official-downloads-and-private-install'
    else:
        failure = json.loads((download_report / 'FAILURE.json').read_bytes()); assert failure['status'] == 'SKIP3_DEPENDENT_ANALYSIS_SKIPPED'
        download_status = 'skipped-named-download-dependency-unavailable-offline-install-remains-validated'
    records.append(dict(name='actual-download', process=downloaded, status=download_status))
    for item in homes:
        home = Path(item['home']); assert item['files'] == {p.relative_to(home).as_posix(): pin(p) for p in home.rglob('*') if p.is_file()}
    receipts = out / 'reports'; receipts.mkdir()
    for folder in sorted(area.iterdir()):
        if folder.is_dir() and ('report' in folder.name or folder.name.startswith(('install-', 'recheck-'))):
            destination = receipts / folder.name; destination.mkdir()
            retained = {}
            for path in sorted(folder.iterdir()):
                if path.is_file():
                    if path.suffix == '.part': retained[path.name] = dict(path=str(path), **pin(path))
                    else: shutil.copyfile(path, destination / path.name)
            if retained: save(destination / 'RETAINED-NONCOPIED-DOWNLOADS.json', dict(status='retained-at-original-Linux-path-not-bundled', files=retained))
    result = dict(schema_version=1, status='passed-original-StringTie-private-setup-and-diagnostics-controls',
                  sources={str(installer.relative_to(ROOT)): pin(installer), str(Path(__file__).relative_to(ROOT)): pin(Path(__file__))},
                  lock=pin(kit / 'stringtie-runtime-lock.json'), linux_base=str(area), homes=homes, cases=records,
                  offline_installs=7, unchanged_payload_rechecks=7, actual_permission_skip_uid=65534, download_status=download_status,
                  scientific_runtime_admitted=False, user_analysis_admitted=False, implementation_credit=False,
                  action='Retain the named private Linux homes and reports for subsequent separate native-host admission; do not overwrite or start replacement installers. Setup alone is not analysis validation.')
    save(out / 'COMPLETED.json', result); print(json.dumps({k: result[k] for k in ['status', 'offline_installs', 'unchanged_payload_rechecks', 'actual_permission_skip_uid', 'download_status', 'linux_base']}))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable actual exact StringTie setup controls')
    main()
