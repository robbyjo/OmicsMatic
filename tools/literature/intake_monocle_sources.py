# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Download immutable official Monocle3 source commits with exact-byte inventories; never install."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import tarfile
import urllib.request
from setup_deseq2_reference import ROOT, pin, save

PROFILES = {
    '1.0.0': '004c096f3a3bdaf1e6709a65fb36f40ba775f1ab',
    '1.3.1': 'b545460966874948eb11a57a225594a107f1694d',
    '1.4.27': '536f1033d6de7c957f26a1f403f81efbd825e0db'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    records = []
    for version, commit in PROFILES.items():
        folder = out/version
        folder.mkdir()
        url = 'https://codeload.github.com/cole-trapnell-lab/monocle3/tar.gz/'+commit
        req = urllib.request.Request(url, headers={'User-Agent': 'OmicsMatic-source-audit'})
        with urllib.request.urlopen(req, timeout=120) as response:
            raw = response.read(134217729)
            assert len(raw) <= 134217728
            resolved, headers = response.url, dict(response.headers)
        archive = folder/'monocle3-source.tar.gz'
        archive.write_bytes(raw)
        members = {}
        with tarfile.open(fileobj=io.BytesIO(raw), mode='r:gz') as tar:
            for item in tar.getmembers():
                parts = PurePosixPath(item.name).parts
                assert len(parts) >= 1 and parts[0] == 'monocle3-'+commit
                assert not PurePosixPath(item.name).is_absolute() and '..' not in parts and '\\' not in item.name
                if item.isdir():
                    continue
                assert item.isfile() and not item.issym() and not item.islnk() and item.size <= 33554432
                relative = PurePosixPath(*parts[1:]).as_posix()
                target = folder/'source'/relative
                assert target.resolve().is_relative_to((folder/'source').resolve()) and relative not in members
                target.parent.mkdir(parents=True, exist_ok=True)
                content = tar.extractfile(item).read()
                assert len(content) == item.size
                target.write_bytes(content)
                members[relative] = dict(bytes=len(content), sha256=hashlib.sha256(content).hexdigest())
        description = (folder/'source/DESCRIPTION').read_text(encoding='utf8')
        assert '\nVersion: '+version+'\n' in description
        record = dict(version=version, official_repository='https://github.com/cole-trapnell-lab/monocle3',
            commit=commit, source_url=url, resolved_url=resolved, response_headers=headers, archive=pin(archive), source_members=members)
        save(folder/'SOURCE.json', record)
        records.append(dict(version=version, commit=commit, archive=record['archive'], source_files=len(members)))
        print(json.dumps(records[-1]), flush=True)
    save(out/'COMPLETED.json', dict(status='downloaded-three-exact-official-Monocle3-source-profiles',
        profiles=records, source_pins={'tools/literature/intake_monocle_sources.py': pin(Path(__file__))},
        installed_software=False, original_execution=False, implementation_credit=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable archive/source checks')
    main()
