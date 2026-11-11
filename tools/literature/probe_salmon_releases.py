# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect/execute official historical Salmon releases in an owned Linux reference area."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0')


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
def write(path, value): path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--capture', type=Path, required=True); parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); capture = args.capture.resolve(); out = args.out.resolve()
    base = (ROOT / 'build/literature-review/salmon-runtime').resolve()
    assert os.uname().sysname == 'Linux' and capture.is_relative_to(base) and out.is_relative_to(base) and not out.exists()
    assert json.loads((capture / 'COMPLETED.json').read_bytes())['versions'] == list(VERSIONS)
    out.mkdir(parents=True); owned = Path(tempfile.mkdtemp(prefix='omicsmatic-salmon-reference-', dir='/var/tmp'))
    assert re.fullmatch(r'/var/tmp/omicsmatic-salmon-reference-[A-Za-z0-9_-]+', str(owned))
    write(out / 'OWNER.json', dict(owned_linux_root=str(owned), purpose='Unmodified original-release reference probing; no public runtime admission'))
    results = []
    for version in VERSIONS:
        folder = out / version; folder.mkdir(); source = capture / version
        metadata = json.loads((source / 'CAPTURED.json').read_bytes())
        for item in metadata['files']:
            path = source / ('source.tar.gz' if 'codeload.github.com' in item['url'] else 'release.json' if 'api.github.com' in item['url'] else item['url'].rsplit('/', 1)[1])
            assert pin(path) == {k:item[k] for k in ('bytes', 'sha256')}
        runtime_tar = next(source.glob('*linux*.tar.gz')); area = owned / version; area.mkdir()
        with tarfile.open(runtime_tar) as archive:
            members = archive.getmembers(); assert len(members) < 1000 and sum(m.size for m in members) < 1024*1024*1024
            roots = set()
            for m in members:
                name = PurePosixPath(m.name); assert not name.is_absolute() and '..' not in name.parts and '\\' not in m.name
                assert m.isdir() or m.isfile() or m.issym() or m.islnk()
                assert not m.mode & 0o6000
                roots.add(name.parts[0])
            assert len(roots) == 1
            archive.extractall(area, filter='data')
        home = area / next(iter(roots)); payload = {}
        for path in sorted(home.rglob('*')):
            info = path.lstat(); name = path.relative_to(home).as_posix()
            if path.is_symlink():
                assert path.resolve(strict=True).is_relative_to(home)
                payload[name] = dict(kind='symlink', target=os.readlink(path))
            elif path.is_dir(): payload[name] = dict(kind='directory', mode=stat.S_IMODE(info.st_mode))
            else:
                assert stat.S_ISREG(info.st_mode)
                payload[name] = dict(kind='file', mode=stat.S_IMODE(info.st_mode), **pin(path))
        src_out = folder / 'source'; src_out.mkdir()
        selected = ['LICENSE', 'src/GZipWriter.cpp', 'src/Salmon.cpp', 'src/SalmonQuantify.cpp',
                    'src/SalmonQuantifyAlignments.cpp', 'include/BootstrapWriter.hpp', 'include/GZipWriter.hpp',
                    'scripts/ConvertBootstrapsToTSV.py', 'doc/source/salmon.rst', 'doc/source/file_formats.rst']
        sources = {}
        with tarfile.open(source / 'source.tar.gz') as archive:
            for name in selected:
                member_name = 'salmon-' + version + '/' + name
                try: member = archive.getmember(member_name)
                except KeyError: continue
                assert member.isfile() and member.size < 8*1024*1024
                target = src_out / name; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.extractfile(member).read()); sources[name] = pin(target)
        assert 'LICENSE' in sources and b'GNU GENERAL PUBLIC LICENSE' in (src_out / 'LICENSE').read_bytes()
        env = dict(PATH='/usr/bin:/bin', HOME=str(area), TMPDIR=str(area), LANG='C', LC_ALL='C',
                   LD_LIBRARY_PATH=str(home / 'lib'), SALMON_NO_VERSION_CHECK='1')
        calls = []
        for label, arguments in [('version', ['--version']), ('index-help', ['--no-version-check', 'index', '--help']),
                                 ('quant-help', ['--no-version-check', 'quant', '--help'])]:
            command = [str(home / 'bin/salmon'), *arguments]
            with (folder / (label+'.stdout')).open('xb') as stdout, (folder / (label+'.stderr')).open('xb') as stderr:
                completed = subprocess.run(command, cwd=area, env=env, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=45)
            record = dict(command=command, exit_code=completed.returncode, environment=env); write(folder / (label+'.json'), record); calls.append(record)
            assert completed.returncode == 0, (version, label, completed.returncode)
        assert (folder / 'version.stdout').read_text().strip() == 'salmon '+version
        result = dict(version=version, home=str(home), payload=payload, sources=sources, calls=calls,
            status='passed-original-release-version-and-help-not-runtime-admitted')
        write(folder / 'PROBED.json', result); results.append(result)
        print(json.dumps(dict(version=version, startup_passed=True, payload_members=len(payload))), flush=True)
    write(out / 'COMPLETED.json', dict(status='passed-five-unmodified-Salmon-release-startup-probes', releases=results,
        owned_linux_root=str(owned), script=pin(Path(__file__)), public_runtime_admitted=False, user_analysis_admitted=False))


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('SALMON REFERENCE PROBE FAIL2: '+str(error)+'\nRequired action: retain every captured archive and owned reference area, inspect the named platform/dependency/exit failure. No public runtime or analysis is admitted; skip dependent analysis until permitted setup succeeds.', file=sys.stderr)
        raise
