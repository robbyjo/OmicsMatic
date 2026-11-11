# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install only exact official StringTie payloads privately; setup never admits a scientific runtime or analysis."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import stat
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request

LOCK_SHA = 'b2697399b8cd7e716131a54a6e75ed2b1641eb68e137c373bca52736d0200e3d'
MARKER = '.omicsmatic-stringtie.json'


class Unavailable(Exception):
    pass


def pin(path):
    before = path.stat()
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    after = path.stat()
    if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
        raise ValueError('File changed during complete setup hashing: ' + str(path))
    return dict(bytes=after.st_size, sha256=digest)


def write(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2); stream.write('\n')


def canonical(path, existing=False):
    if not path.is_absolute() or path != Path(os.path.normpath(path)) or any(ord(c) < 33 or ord(c) > 126 for c in str(path)):
        raise ValueError('Use canonical absolute ASCII setup paths without whitespace: ' + str(path))
    if path.parent.resolve(strict=True) != path.parent or existing and path.resolve(strict=True) != path:
        raise ValueError('Linked/noncanonical setup path: ' + str(path))
    return path


def regular(path, expected=None):
    canonical(path, True); info = path.lstat()
    if not stat.S_ISREG(info.st_mode): raise ValueError('Nonregular setup input: ' + str(path))
    if (expected is not None and info.st_size != expected['bytes']) or (expected is None and info.st_size > 1048576):
        raise ValueError('Complete setup input size exceeds its exact/metadata capacity: ' + str(path))
    actual = pin(path)
    if expected is not None and actual != expected: raise ValueError('Complete exact file hash/size differs: ' + str(path))
    return actual


def modes(path, expected):
    if stat.S_IMODE(path.stat().st_mode) != expected:
        raise Unavailable('Filesystem did not preserve required private/source modes at ' + str(path) + '; use a permitted native Linux filesystem')


def validate_home(home, profile, spec):
    canonical(home, True)
    if not home.is_dir() or set(p.name for p in home.iterdir()) != {'bin', 'LICENSE-StringTie.txt', MARKER}:
        raise ValueError('Managed home has unexpected/missing members; preserve it and choose NEW home: ' + str(home))
    modes(home, 0o700); canonical(home / 'bin', True); modes(home / 'bin', 0o700)
    if set(p.name for p in (home / 'bin').iterdir()) != {'stringtie'}: raise ValueError('Managed binary namespace differs')
    marker = home / MARKER; regular(marker); modes(marker, 0o600)
    value = json.loads(marker.read_bytes())
    expected = dict(schema_version=1, status='EXACT_ORIGINAL_PAYLOAD_ONLY', profile=profile, version=spec['version'],
                    lock_sha256=LOCK_SHA, files=spec['installed_files'], scientific_runtime_admitted=False, user_analysis_admitted=False)
    if value != expected: raise ValueError('Managed home marker differs from exact profile/status')
    for relative, fingerprint in spec['installed_files'].items():
        path = home / relative; regular(path, fingerprint)
        if path.stat().st_nlink != 1: raise ValueError('Hard-linked managed payload member: ' + str(path))
        modes(path, 0o755 if relative == 'bin/stringtie' else 0o644)
    return dict(files={name: regular(home / name, expected) for name, expected in spec['installed_files'].items()}, marker=pin(marker))


def selected_member(archive, name, expected):
    if expected['bytes'] > 20000000: raise ValueError('Consumed exact member exceeds bounded setup capacity')
    names, total, found = set(), 0, None
    with tarfile.open(archive, 'r:gz') as source:
        for member in source:
            path = PurePosixPath(member.name)
            if member.name in names or path.is_absolute() or '..' in path.parts or '\\' in member.name or not (member.isdir() or member.isfile()):
                raise ValueError('Unsafe/duplicate original archive member: ' + member.name)
            names.add(member.name); total += member.size
            if len(names) > 50000 or total > 300000000: raise ValueError('Complete original archive census exceeds capacity')
            if member.name == name:
                if not member.isfile() or member.size != expected['bytes']: raise ValueError('Consumed original member shape/size differs')
                with source.extractfile(member) as stream: found = stream.read(expected['bytes'] + 1)
                if dict(bytes=len(found), sha256=hashlib.sha256(found).hexdigest()) != expected: raise ValueError('Consumed exact member hash differs')
    if found is None: raise ValueError('Exact consumed original archive member is absent: ' + name)
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kit', type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--archive-dir', type=Path)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args(); report = None; report_created = False; stage = 'request'; source_pin = pin(Path(__file__).resolve()); downloaded = []
    action = ('Read the named stage/error and every retained original archive/report. Skip dependent analysis while any required permitted dependency is unavailable. '
              'Use a compatible permitted Linux x86_64 Python environment and private native Linux filesystem. Select the exact profile; do not substitute editions. '
              'Supply the two exact official archives via --archive-dir for offline setup, or use --allow-install and --allow-download only where permitted. '
              'Preserve failed/partial paths, correct the cause and retry with NEW --home/--report paths. Existing valid managed homes may be rechecked into a NEW report without installation permission. '
              'Require setup COMPLETED.json and no INCOMPLETE.json, then separate native runtime health/admission and typed fit validation. Setup alone admits no runtime or user analysis.')
    try:
        report = canonical(args.report)
        if report.exists(): raise ValueError('Report must be NEW; preserve historical reports')
        report.mkdir(mode=0o700); report_created = True; write(report / 'INCOMPLETE.json', dict(status='SETUP_IN_PROGRESS_NOT_ADMISSION'))
        if platform.system() != 'Linux' or platform.machine() != 'x86_64': raise Unavailable('Exact StringTie payload setup requires permitted Linux x86_64 Python; on Windows use Linux Python inside WSL')
        modes(report, 0o700)
        kit, home, cache = canonical(args.kit, True), canonical(args.home), canonical(args.cache)
        if not kit.is_dir(): raise ValueError('Setup kit must be a canonical complete directory')
        for a, b in [(home, report), (home, cache), (home, kit), (report, cache), (report, kit)]:
            if a.is_relative_to(b) or b.is_relative_to(a): raise ValueError('Setup home/report/cache/kit must have the declared disjoint scopes')
        stage = 'pinned-kit'
        lock_path = kit / 'stringtie-runtime-lock.json'; regular(lock_path)
        if pin(lock_path)['sha256'] != LOCK_SHA: raise ValueError('Pinned StringTie runtime lock differs')
        lock = json.loads(lock_path.read_bytes())
        if lock['schema_version'] != 1 or args.profile not in lock['profiles']: raise ValueError('Unknown exact StringTie profile')
        spec = lock['profiles'][args.profile]
        for name, expected in lock['resources'].items():
            member = PurePosixPath(name)
            if member.is_absolute() or '..' in member.parts or '\\' in name: raise ValueError('Unsafe pinned kit member')
            regular(kit / name, expected)
        request = dict(profile=args.profile, version=spec['version'], home=str(home), report=str(report), cache=str(cache), kit=str(kit),
                       allow_install=args.allow_install, allow_download=args.allow_download, installer=source_pin, lock=pin(lock_path),
                       scientific_runtime_admitted=False, user_analysis_admitted=False)
        write(report / 'request.json', request)
        if home.exists():
            stage = 'existing-managed-payload-recheck'; payload = validate_home(home, args.profile, spec); operation = 'RECHECKED_EXACT_PAYLOAD_ONLY'
        else:
            if not args.allow_install: raise Unavailable('Installation is not permitted by this invocation; dependent analysis is skipped')
            stage = 'archive-acquisition'
            if cache.exists():
                canonical(cache, True)
                if not cache.is_dir(): raise ValueError('Cache is not a regular directory')
            else: cache.mkdir(mode=0o700)
            archive_dir = canonical(args.archive_dir, True) if args.archive_dir else None
            archives = {}
            for kind, asset in spec['assets'].items():
                if Path(asset['filename']).name != asset['filename']: raise ValueError('Unsafe exact archive filename')
                expected = {k: asset[k] for k in ['bytes', 'sha256']}
                offline = archive_dir / asset['filename'] if archive_dir else None
                destination = cache / asset['filename']
                if offline is not None and offline.exists(): path = offline
                elif destination.exists(): path = destination
                else:
                    if not args.allow_download: raise Unavailable('Exact ' + kind + ' archive is unavailable and downloading is not permitted: ' + asset['filename'])
                    fd, name = tempfile.mkstemp(prefix='stringtie-download-', suffix='.part', dir=report); temporary = Path(name)
                    with os.fdopen(fd, 'wb') as stream, urllib.request.urlopen(urllib.request.Request(asset['url'], headers={'User-Agent': 'OmicsMatic-exact-StringTie-setup'}), timeout=45) as response:
                        count = 0
                        while block := response.read(1024 * 1024):
                            count += len(block)
                            if count > expected['bytes']: raise ValueError('Exact official download exceeds pinned complete size')
                            stream.write(block)
                    regular(temporary, expected); os.link(temporary, destination); temporary.unlink(); path = destination
                    downloaded.append(dict(kind=kind, url=asset['url'], cache=str(path), **expected))
                regular(path, expected); archives[kind] = path
            stage = 'private-exact-payload-extraction'
            staging = Path(tempfile.mkdtemp(prefix='.stringtie-install-', dir=home.parent)); os.chmod(staging, 0o700); modes(staging, 0o700)
            write(report / 'staging.json', dict(path=str(staging), status='PRESERVE_IF_FAILED'))
            binary = selected_member(archives['binary'], spec['executable_member'], spec['installed_files']['bin/stringtie'])
            license_bytes = selected_member(archives['source'], spec['license_member'], spec['installed_files']['LICENSE-StringTie.txt'])
            (staging / 'bin').mkdir(mode=0o700)
            for relative, raw in [('bin/stringtie', binary), ('LICENSE-StringTie.txt', license_bytes)]:
                path = staging / relative
                with path.open('xb') as stream: stream.write(raw)
                os.chmod(path, 0o755 if relative == 'bin/stringtie' else 0o644)
            marker = dict(schema_version=1, status='EXACT_ORIGINAL_PAYLOAD_ONLY', profile=args.profile, version=spec['version'], lock_sha256=LOCK_SHA,
                          files=spec['installed_files'], scientific_runtime_admitted=False, user_analysis_admitted=False)
            write(staging / MARKER, marker); os.chmod(staging / MARKER, 0o600)
            validate_home(staging, args.profile, spec)
            for kind, asset in spec['assets'].items(): regular(archives[kind], {k: asset[k] for k in ['bytes', 'sha256']})
            # Exclusively reserve the requested home; never rename a directory
            # over an existing empty destination. A partial home has no valid
            # marker and remains retained on failure. Commit its marker last.
            home.mkdir(mode=0o700); modes(home, 0o700)
            for name in ['bin', 'LICENSE-StringTie.txt', MARKER]: os.rename(staging / name, home / name)
            payload = validate_home(home, args.profile, spec); operation = 'INSTALLED_EXACT_PAYLOAD_ONLY'
        stage = 'setup-finalization'; regular(lock_path, request['lock'])
        if pin(Path(__file__).resolve()) != source_pin: raise ValueError('Installer source changed during setup')
        warnings = ('# STRINGTIE SETUP ONLY — NO RUNTIME OR USER ANALYSIS ADMITTED\n\n'
                    'Exact original payload bytes and edition-specific license are present. No native health, user fit, library/host admission or biological analysis was performed. '
                    'Complete separate exact-host runtime admission and requested typed workflow validation. Historical editions differ; preserve the exact profile and source settings.\n\n'
                    'Required action: ' + action + '\n')
        (report / 'WARNINGS.md').write_text(warnings, encoding='utf-8', newline='\n')
        files = {p.name: pin(p) for p in sorted(report.iterdir()) if p.is_file() and p.name != 'INCOMPLETE.json'}
        manifest = dict(schema_version=1, status=operation, profile=args.profile, version=spec['version'], home=str(home), payload=payload,
                        files=files, downloads=downloaded, scientific_runtime_admitted=False, user_analysis_admitted=False)
        write(report / 'manifest.json', manifest); write(report / 'COMPLETED.json', dict(status='COMPLETE_SETUP_ONLY_NOT_ADMISSION', manifest=pin(report / 'manifest.json'), scientific_runtime_admitted=False, user_analysis_admitted=False))
        validate_home(home, args.profile, spec); (report / 'INCOMPLETE.json').unlink()
        print(json.dumps(dict(status=operation, profile=args.profile, home=str(home), report=str(report), scientific_runtime_admitted=False, user_analysis_admitted=False))); return 0
    except (Exception, KeyboardInterrupt) as failure:
        skipped = isinstance(failure, (Unavailable, PermissionError, urllib.error.URLError)); code = 3 if skipped else 2
        message = dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if skipped else 'FAIL2_NO_SETUP_OR_ANALYSIS_ADMITTED', stage=stage,
                       error_type=type(failure).__name__, error=str(failure), required_action=action, scientific_runtime_admitted=False, user_analysis_admitted=False)
        if report_created and report is not None and report.is_dir():
            try:
                for name in ['COMPLETED.json', 'manifest.json', 'WARNINGS.md']:
                    path = report / name
                    if path.exists(): path.rename(report / ('UNADMITTED-' + name))
                write(report / 'FAILURE.json', message)
                (report / 'WARNINGS.md').write_text('# ' + message['status'] + '\n\nStage: ' + stage + '\n\n' + str(failure) + '\n\nRequired action: ' + action + '\n', encoding='utf-8', newline='\n')
            except OSError as diagnostic: message['diagnostic_write_error'] = str(diagnostic)
        print(json.dumps(message), file=sys.stderr); return code


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete exact StringTie setup checks')
    raise SystemExit(main())
