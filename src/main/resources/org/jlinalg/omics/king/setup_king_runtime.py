# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a PRIVATE exact KING2.3.2 runtime profile. Never run an analysis or install packages."""
import argparse
import hashlib
import io
import json
import os
import platform
import re
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

PROFILES = {
    'windows': ('Windows-king.zip', 'king.exe', 604322,
                'd0021a81325dd7a045c10000225c22e699e2376510b8f76c1bd84f89053eb7ee',
                1263104, 'ae4ce40a753504ba6199bf9b46ba64b3df8492191a79aa16518892ab997cc616'),
    'linux': ('Linux-king.tar.gz', 'king', 1430594,
              'cbe78fc5a3b35e05ded2e51a4491cf709026391ff4aa2db6186ca255f64e33a9',
              3284088, 'dc2ed86a82471754bbc9ed53e44764d24a593eb3e4b14fde9d30eb6608f74718'),
}
NAMES = {'libz.so.1', 'libquadmath.so.0', 'libm.so.6', 'libgomp.so.1',
         'libpthread.so.0', 'libc.so.6', 'ld-linux-x86-64.so.2'}
ACTION = ('SKIP dependent standalone KING analysis if these prerequisites are unavailable or installation is forbidden. '
          'Use KING only within its permitted research-use terms. Keep original programs/archives private. '
          'Supply the exact original2.3.2 executable with --executable, or use --allow-download where permitted. '
          'On Linux, ask the environment administrator to supply zlib1g, libquadmath0, libgomp1 and libc6 if missing; '
          'this helper installs no packages. Retry in a NEW directory after resolving the named cause. '
          'Windows lacks coordinate-level IBD output in this tested profile: use an explicitly selected Linux runtime for ibdseg.')


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--research-use', action='store_true', help='Explicitly select the permitted research-use profile')
    p.add_argument('--allow-download', action='store_true')
    p.add_argument('--executable', type=Path)
    a = p.parse_args()
    out = a.out.absolute()
    created = False
    state = dict(status='SKIP3_KING_RUNTIME_UNAVAILABLE', analysis_performed=False, required_action=ACTION)
    try:
        if out.exists() or not a.research_use:
            raise RuntimeError('Require a NEW private directory and explicit --research-use')
        out.mkdir(parents=True)
        created = True
        host = platform.system().lower()
        if host not in PROFILES or platform.machine().lower() not in {'amd64', 'x86_64'}:
            raise RuntimeError('Only tested Windows/Linux x86_64 host profiles are supported')
        name, member, size, digest, exe_size, exe_digest = PROFILES[host]
        if a.executable:
            executable = a.executable.resolve(strict=True)
        else:
            if not a.allow_download:
                raise RuntimeError('No executable supplied and download was not allowed')
            url = 'https://www.kingrelatedness.com/'+name
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-research-runtime'}), timeout=90) as response:
                raw = response.read(size+1)
                receipt = dict(url=url, final_url=response.geturl(), status=response.status)
            if len(raw) != size or hashlib.sha256(raw).hexdigest() != digest:
                raise RuntimeError('Official original archive changed; do not substitute a newer runtime')
            (out/name).write_bytes(raw)
            (out/'download.json').write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf8')
            if host == 'windows':
                with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                    if archive.namelist() != [member]:
                        raise RuntimeError('Original archive member population changed')
                    payload = archive.read(member)
            else:
                with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
                    members = archive.getmembers()
                    if len(members) != 1 or members[0].name != member or not members[0].isfile():
                        raise RuntimeError('Original archive member population changed')
                    payload = archive.extractfile(members[0]).read(exe_size+1)
            executable = out/member
            executable.write_bytes(payload)
            if host == 'linux':
                executable.chmod(0o700)
            executable = executable.resolve(strict=True)
        expected = dict(bytes=exe_size, sha256=exe_digest)
        if pin(executable) != expected:
            raise RuntimeError('Executable is not the exact tested original2.3.2 profile')
        dependencies, loader, names, paths = {}, {}, {}, {}
        if host == 'linux':
            if Path('/etc/ld.so.preload').exists():
                raise RuntimeError('Loader preload configuration present; this profile cannot admit it')
            ldd = Path('/usr/bin/ldd').resolve(strict=True)
            r = subprocess.run([str(ldd), str(executable)], env={'PATH': '/usr/bin:/bin', 'LANG': 'C', 'LC_ALL': 'C'},
                               cwd=out, stdin=subprocess.DEVNULL, capture_output=True, timeout=30)
            (out/'ldd.stdout').write_bytes(r.stdout)
            (out/'ldd.stderr').write_bytes(r.stderr)
            if r.returncode != 0:
                raise RuntimeError('Linux dependency inspection failed')
            for line in r.stdout.decode('utf8').splitlines():
                line = line.strip()
                if line.startswith('linux-vdso.so.1 '):
                    continue
                match = re.fullmatch(r'([^ ]+) => (/[^ ]+) \(0x[0-9a-f]+\)', line)
                if match:
                    soname, path = match.groups()
                else:
                    match = re.fullmatch(r'(/[^ ]+) \(0x[0-9a-f]+\)', line)
                    if not match:
                        raise RuntimeError('Missing/unknown Linux dependency: '+line)
                    path = match.group(1)
                    soname = Path(path).name
                if soname in names:
                    raise RuntimeError('Repeated dynamic dependency')
                real = Path(path).resolve(strict=True)
                names[soname], paths[soname] = str(real), path
                dependencies[str(real)] = pin(real)
            if set(names) != NAMES:
                raise RuntimeError('Linux library population differs from tested profile: '+repr(set(names)))
            dependencies[str(ldd)] = pin(ldd)
            for path in [Path('/etc/ld.so.cache'), Path('/etc/ld.so.conf'), *sorted(Path('/etc/ld.so.conf.d').iterdir())]:
                real = path.resolve(strict=True)
                if not path.is_file() or real != path:
                    raise RuntimeError('Unsupported linked/nonregular loader metadata: '+str(path))
                loader[str(real)] = pin(real)
        profile = dict(schema_version=1, tool='KING', version='2.3.2', intended_use='research', platform=host,
                       executable=str(executable), executable_pin=expected, dependencies=dependencies,
                       dependency_names=names, ldd_paths=paths, loader_files=loader,
                       windows_system_DLLs_independently_pinned=False)
        (out/'runtime-profile.json').write_text(json.dumps(profile, indent=2)+'\n', encoding='utf8')
        state.update(status='PASS_EXACT_PRIVATE_KING232_RUNTIME_PROFILE_ONLY', runtime_profile='runtime-profile.json')
    except Exception as error:
        state['cause'] = str(error)
    message = state['status']+'\n'+state.get('cause', '')+'\nREQUIRED ACTION: '+ACTION+'\n'
    if created:
        (out/'COMPLETED.json').write_text(json.dumps(state, indent=2)+'\n', encoding='utf8')
        (out/'WARNINGS.md').write_text('# PRIVATE ORIGINAL RUNTIME - DO NOT REDISTRIBUTE\n\n'+message, encoding='utf8')
    print(message, file=sys.stdout if state['status'].startswith('PASS_') else sys.stderr)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__ == '__main__':
    raise SystemExit(main())
