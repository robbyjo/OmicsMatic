# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Assemble private official MSYS build helpers without modifying an existing runtime or system."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile
import urllib.request
from setup_deseq2_reference import ROOT, pin, save


def fetch(url, target, maximum):
    req = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-private-reference-build'})
    with urllib.request.urlopen(req, timeout=120) as response:
        raw = response.read(maximum+1)
        assert len(raw) <= maximum
        resolved = response.url
    target.write_bytes(raw)
    return raw, resolved


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--download-cache', type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    cache = args.download_cache.resolve(strict=True) if args.download_cache else None
    if cache:
        assert cache.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and cache.is_dir()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    sys.path.insert(0,str(ROOT/'build/literature-review/monocle-runtime/rtools-intake-1/python-tools'))
    import zstandard
    raw, resolved = fetch('https://repo.msys2.org/msys/x86_64/msys.db',out/'msys.db',16777216)
    packages = {}
    decoded_db = zstandard.ZstdDecompressor().decompress(raw,max_output_size=67108864) if raw.startswith(b'\x28\xb5\x2f\xfd') else raw
    with tarfile.open(fileobj=io.BytesIO(decoded_db),mode='r:*') as archive:
        for member in archive:
            if not member.isfile() or not member.name.endswith('/desc'):
                continue
            text = archive.extractfile(member).read().decode('utf8')
            fields = {}
            for block in text.strip().split('\n\n'):
                lines = block.splitlines()
                assert lines[0].startswith('%') and lines[0].endswith('%')
                fields[lines[0].strip('%')] = lines[1:]
            name = fields['NAME'][0]
            assert name not in packages
            packages[name] = fields
    selected = set()
    provider_resolutions = {}
    def require(name):
        if name not in packages:
            candidates = [n for n,row in packages.items() if name in
                [re.split(r'[<>=]',value,1)[0] for value in row.get('PROVIDES',[])]]
            active = [n for n in candidates if n in selected]
            choices = active if len(active)==1 else candidates
            assert len(choices)==1, 'Missing or ambiguous declared build-helper provider: '+name
            provider_resolutions[name] = choices[0]
            name = choices[0]
        if name in selected:
            return
        assert name in packages, 'Missing explicit build-helper dependency: '+name
        selected.add(name)
        for dependency in packages[name].get('DEPENDS',[]):
            require(re.split(r'[<>=]',dependency,1)[0])
    for name in ['msys2-runtime','bash','make','sed','gawk','grep','diffutils','coreutils','findutils','tar','gzip','xz','patch','which','file']:
        require(name)
    save(out/'PLAN.json',dict(database=pin(out/'msys.db'),database_resolved_url=resolved,
        selected_packages={n:packages[n] for n in sorted(selected)},provider_resolutions=provider_resolutions,
        system_install=False,post_install_scripts_executed=False))
    downloads=out/'downloads';downloads.mkdir()
    host=out/'host';host.mkdir()
    (host/'tmp').mkdir();(host/'var/tmp').mkdir(parents=True)
    members = {};pending = {};omitted = [];receipts=[]
    for name in sorted(selected):
        row = packages[name]
        filename = row['FILENAME'][0]
        assert '/' not in filename and '\\' not in filename and filename.endswith('.pkg.tar.zst')
        url = 'https://repo.msys2.org/msys/x86_64/'+filename
        cached = cache/filename if cache else None
        if cached and cached.is_file() and pin(cached)['sha256']==row['SHA256SUM'][0]:
            source = cached.read_bytes()
            assert len(source)<=134217728
            (downloads/filename).write_bytes(source)
        else:
            source, url = fetch(url,downloads/filename,134217728)
        assert hashlib.sha256(source).hexdigest() == row['SHA256SUM'][0]
        receipts.append(dict(package=name,version=row['VERSION'][0],url=url,archive=pin(downloads/filename)))
        with zstandard.ZstdDecompressor().stream_reader(io.BytesIO(source)) as decoded,tarfile.open(fileobj=decoded,mode='r|') as archive:
            for member in archive:
                parts=PurePosixPath(member.name).parts
                assert parts and not PurePosixPath(member.name).is_absolute() and '..' not in parts and '\\' not in member.name
                if member.isdir() or parts[0].startswith('.'):
                    continue
                if '/share/terminfo/' in member.name or member.name.startswith('usr/share/info/'):
                    omitted.append(dict(package=name,path=member.name));continue
                target=host.joinpath(*parts)
                assert target.resolve().is_relative_to(host)
                key=PurePosixPath(*parts).as_posix()
                if member.issym() or member.islnk():
                    link=(target.parent/member.linkname).resolve() if member.issym() else (host/member.linkname).resolve()
                    assert link.is_relative_to(host)
                    relative=link.relative_to(host).as_posix()
                    assert key not in pending
                    pending[key]=relative;continue
                assert member.isfile() and member.size<=134217728
                content=archive.extractfile(member).read()
                value=dict(bytes=len(content),sha256=hashlib.sha256(content).hexdigest())
                assert len(content)==member.size
                if key in members:
                    assert members[key]==value and target.read_bytes()==content,key
                    continue
                assert not target.exists(), 'Case-colliding helper path: '+key
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content);members[key]=value
        print('Downloaded and unpacked private build helper '+name,flush=True)
    while pending:
        progressed=False
        for name,link in list(pending.items()):
            if link not in members:
                continue
            raw=(host/link).read_bytes();target=host/name
            assert not target.exists()
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
            members[name]=pin(target);del pending[name];progressed=True
        assert progressed, 'Missing or cyclic private helper link targets'
    save(out/'COMPLETED.json',dict(status='assembled-private-official-MSYS-build-helper-files',packages=receipts,
        host=host.relative_to(ROOT).as_posix(),members=members,omitted_terminal_and_info_resources=omitted,
        source_pins={'tools/literature/prepare_monocle_build_host.py':pin(Path(__file__))},
        system_install=False,post_install_scripts_executed=False,compiler_health_certified=False,analysis_admitted=False))
    print(json.dumps(dict(status='assembled-private-official-MSYS-build-helper-files',packages=len(receipts),files=len(members))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable package/path/hash checks')
    main()
