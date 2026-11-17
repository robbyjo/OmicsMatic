#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare the pinned original LDAK 5.2 Linux runtime; no data analysis or implicit installation."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile

PROFILE = Path(__file__).with_name('source-profile.json')
ACTION = ('SKIP3: skip dependent LDAK analysis when the permitted Linux x86_64 runtime, '
          'execution permission or exact archive is unavailable. Use a permitted Linux/WSL '
          'environment and supply --archive PATH, or explicitly allow downloading. '
          'FAIL2: no prepared runtime; retain partials, resolve the named integrity/recording '
          'error and retry NEW output. Runtime preparation does not reproduce a paper analysis.')


class Prerequisite(Exception):
    pass


def pin(path):
    with Path(path).open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(bytes=Path(path).stat().st_size, sha256=digest)


def save(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def obtain(url, target, expected, allow_download, archive=None):
    """Bound every transfer; preserve mismatched bytes for diagnosis."""
    if archive is not None:
        if not Path(archive).is_file():
            raise Prerequisite('Supplied original archive is missing: ' + str(archive))
        source = Path(archive).open('rb')
    else:
        if not allow_download:
            raise Prerequisite('Downloading is forbidden; supply the exact archive with --archive')
        try:
            source = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-LDAK52-setup'}), timeout=60)
        except (urllib.error.URLError, OSError) as error:
            raise Prerequisite('Original download unavailable; supply a verified local archive: ' + str(error)) from error
    with source, Path(target).open('xb') as output:
        remaining = expected['bytes'] + 1
        while remaining:
            raw = source.read(min(1048576, remaining))
            if not raw:
                break
            output.write(raw)
            remaining -= len(raw)
    if pin(target) != expected:
        raise ValueError('Original archive size/SHA-256 mismatch; do not execute or refresh pins')


def unpack(archive, target, profile):
    with zipfile.ZipFile(archive) as saved:
        names = saved.namelist()
        if len(names) != 2 or set(names) != {'ldak5.2.linux_/', 'ldak5.2.linux_/ldak5.2.linux'}:
            raise ValueError('Unexpected original archive membership; no generic extraction is allowed')
        info = saved.getinfo('ldak5.2.linux_/ldak5.2.linux')
        if info.file_size != profile['binary']['bytes']:
            raise ValueError('Original executable size mismatch')
        with saved.open(info) as source, target.open('xb') as output:
            shutil.copyfileobj(source, output, 1048576)
    if pin(target) != profile['binary']:
        raise ValueError('Original executable hash mismatch; retain it without execution')


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = None
    try:
        profile = json.loads(PROFILE.read_bytes())
        destination = args.out.absolute()
        if destination.exists() or destination.is_symlink():
            raise ValueError('Output exists; preserve it and choose a NEW directory')
        if args.archive is not None and args.archive.resolve().is_relative_to(destination.resolve()):
            raise ValueError('Output must not contain the supplied archive')
        destination.mkdir(parents=True, exist_ok=False)
        out = destination.resolve()
        (out/'WARNINGS.md').write_text('# Required actions\n\n'+ACTION+'\n', encoding='utf-8')
        if platform.system() != 'Linux' or platform.machine().lower() not in ['x86_64', 'amd64']:
            raise Prerequisite('This exact original executable requires Linux x86_64; use Linux or a permitted WSL distribution')
        license_file = Path(__file__).with_name('LICENSE-UPSTREAM.txt')
        if pin(license_file) != profile['license']:
            raise ValueError('Bundled upstream GPL notice changed; retain the discrepancy')
        shutil.copyfile(license_file, out/'LICENSE-UPSTREAM.txt')
        shutil.copyfile(PROFILE, out/'source-profile.json')
        archive = out/'ldak5.2.linux.zip'
        obtain(profile['archive_url'], archive, profile['archive'], args.allow_download, args.archive)
        executable = out/'ldak5.2.linux'
        unpack(archive, executable, profile)
        try:
            executable.chmod(0o700)
            environment = {k: v for k, v in os.environ.items() if not k.startswith(('OMP_', 'MKL_')) and k not in ['LD_PRELOAD', 'LD_LIBRARY_PATH']}
            environment.update(OMP_NUM_THREADS='1', MKL_NUM_THREADS='1', LC_ALL='C')
            with (out/'probe.stdout').open('xb') as so, (out/'probe.stderr').open('xb') as se:
                probe = subprocess.run([str(executable)], cwd=out, env=environment, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=30)
        except (PermissionError, FileNotFoundError) as error:
            raise Prerequisite('Original execution is unavailable or forbidden: '+str(error)) from error
        save(out/'probe.PROCESS.json', dict(command=[str(executable)], exit_code=probe.returncode, expected_exit_code=1, intentional_usage_probe=True, scientific_analysis=False))
        text = (out/'probe.stdout').read_text(encoding='utf-8', errors='strict')
        if probe.returncode != 1 or 'Version 5.2 - Help pages' not in text or 'Error, each command requires a main argument' not in text:
            raise ValueError('Original 5.2 usage probe differs from the selected profile')
        files = {path.name: pin(path) for path in out.iterdir() if path.is_file()}
        save(out/'COMPLETED.json', dict(status='READY_PINNED_ORIGINAL_LDAK52_LINUX_RUNTIME_ONLY', files=files, original_binary=profile['binary'], usage_probe_exit_one_is_expected=True, author_binary_identity_established=False, analysis_executed=False))
        print('Prepared original LDAK 5.2 runtime: '+str(out)+'\n'+ACTION)
        return 0
    except Exception as error:
        skipped = isinstance(error, Prerequisite)
        label = 'SKIP3' if skipped else 'FAIL2'
        if out is not None:
            save(out/('SKIPPED.json' if skipped else 'FAILURE.json'), dict(status=label, detail=str(error), required_action=ACTION))
        print(label+': '+str(error)+'\n'+ACTION, file=sys.stderr)
        return 3 if skipped else 2


if __name__ == '__main__':
    raise SystemExit(main())
