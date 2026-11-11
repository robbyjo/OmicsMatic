#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy an existing permitted, exact controlled SHAPEIT build into a fresh private home.

This adopter does not download or build software. It accepts only the byte-pinned
profile and installs private copies of its complete loader/library closure.
Installation alone cannot admit a scientific analysis. Failed homes are retained.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import sys

HERE = Path(__file__).resolve().parent
LOCK_PATH = HERE / 'shapeit-runtime-lock.json'
if not LOCK_PATH.exists():
    LOCK_PATH = HERE.parent / 'shapeit-runtime-lock.json'
MARKER = '.omicsmatic-shapeit.json'
ACTION = ('Skip dependent phasing when this exact runtime is unavailable. Where installation is permitted, '
          'run python tools/literature/install_shapeit.py --home NEW_PRIVATE_HOME '
          '--source-manifest EXACT_MEMBER_LOCATORS.json --allow-install using Linux Python. '
          'The source manifest must map every locked bin/lib/share member to an existing permitted file '
          'with the exact locked hash. This adopter does not download, compile or substitute another build. '
          'If the exact build or prerequisites are unavailable, preserve the report/failed home and skip '
          'the analysis; provision the permitted profile before retrying in a fresh home. '
          'Installation is runtime integrity only; native input admission and complete output audits remain required.')


class Unavailable(Exception):
    pass


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(path):
    if Path(path).stat().st_size > 1048576:
        raise ValueError('Metadata exceeds complete byte capacity')
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=pairs)


def canonical(path):
    path = Path(os.path.abspath(path))
    if path.resolve(strict=True) != path:
        raise ValueError('Linked or noncanonical private path: ' + str(path))
    return path


def fingerprint(path):
    original = Path(path)
    real = original.resolve(strict=True)
    before = real.stat()
    if not real.is_file() or not 0 < before.st_size <= 100000000:
        raise ValueError('Missing, nonregular or oversized runtime member: ' + str(path))
    result = dict(bytes=before.st_size, sha256=sha(real))
    after = real.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns):
        raise ValueError('Runtime member changed while hashing: ' + str(path))
    return real, result


def install(home, sources, allow_install):
    if not allow_install:
        raise Unavailable('Explicit --allow-install is required')
    if sys.version_info < (3, 11) or platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('This exact profile requires Linux x86_64 and Python 3.11 or later')
    if Path('/etc/ld.so.preload').exists() or Path('/etc/ld.so.preload').is_symlink():
        raise Unavailable('System loader preloads are outside this admitted profile')
    flags = [set(line.split(':', 1)[1].split()) for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('flags') and ':' in line]
    if not flags or any(not {'avx2', 'fma'} <= value for value in flags):
        raise Unavailable('Controlled source build requires AVX2 and FMA on every processor')
    lock = read_json(LOCK_PATH)
    locators = read_json(sources)
    if not isinstance(locators, dict) or set(locators) != set(lock['files']):
        raise ValueError('Source locator inventory differs from the complete locked profile')
    if any(not isinstance(value, str) or not value.strip() or '\0' in value for value in locators.values()):
        raise ValueError('Every source locator must be a nonempty path string without NUL')
    home = Path(os.path.abspath(home))
    canonical(home.parent)
    if any(c.isspace() or ord(c) < 32 or c == ':' for c in str(home)):
        raise ValueError('Private home contains loader-list whitespace, control characters or colons')
    if home.exists() or home.is_symlink():
        raise ValueError('Preserve existing home and choose a new private directory')
    inputs = {}
    for name, expected in lock['files'].items():
        real, actual = fingerprint(locators[name])
        if actual != expected:
            raise ValueError('Exact controlled member is unavailable or changed: ' + name)
        inputs[name] = real
    helpers = {}
    for name in ['/usr/bin/setsid', '/usr/bin/prlimit']:
        real, record = fingerprint(name)
        if not os.access(real, os.X_OK):
            raise Unavailable('Owned-process helper execution is forbidden: ' + name)
        helpers[name] = dict(real_path=str(real), **record)
    home.mkdir(mode=0o700)
    for directory in ['bin', 'lib', 'share']:
        (home / directory).mkdir(mode=0o700)
    for name, expected in lock['files'].items():
        target = home / name
        with inputs[name].open('rb') as source, target.open('xb') as dest:
            while block := source.read(1048576):
                dest.write(block)
        target.chmod(0o755 if name.startswith('bin/') or name == lock['loader'] else 0o644)
        if fingerprint(target)[1] != expected or fingerprint(inputs[name])[1] != expected:
            raise ValueError('Complete runtime copy changed: ' + name)
    result = dict(status='installed-exact-runtime-only', tool='SHAPEIT', profile=lock['profile'], version=lock['version'],
                  home=str(home), kernel=platform.release(), lock_sha256=sha(LOCK_PATH), installer_sha256=sha(__file__),
                  files=lock['files'], helpers=helpers, scientific_analysis_admitted=False, required_action=ACTION)
    with (home / MARKER).open('x', encoding='utf-8', newline='\n') as output:
        output.write(json.dumps(result, indent=2) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--source-manifest', type=Path, required=True)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if args.report.exists():
        parser.error('Preserve existing report and select a new report path')
    try:
        result = install(args.home, args.source_manifest, args.allow_install)
        code = 0
    except (Unavailable, PermissionError, FileNotFoundError) as error:
        result = dict(status='SKIP3-runtime-unavailable', error=str(error), required_action=ACTION, scientific_analysis_admitted=False)
        code = 3
    except (ValueError, OSError) as error:
        result = dict(status='FAIL2-retained-no-analysis', error=str(error), required_action=ACTION, scientific_analysis_admitted=False)
        code = 2
    try:
        with args.report.open('x', encoding='utf-8', newline='\n') as output:
            output.write(json.dumps(result, indent=2) + '\n')
    except OSError as error:
        print(json.dumps(dict(status='FAIL2-report-write-failed', error=str(error),
                              required_action='Preserve any created runtime home. Select a fresh writable report path. ' + ACTION,
                              scientific_analysis_admitted=False)), file=sys.stderr)
        return 2
    print(json.dumps({key: value for key, value in result.items() if key not in {'files', 'helpers'}}))
    return code


if __name__ == '__main__':
    raise SystemExit(main())
